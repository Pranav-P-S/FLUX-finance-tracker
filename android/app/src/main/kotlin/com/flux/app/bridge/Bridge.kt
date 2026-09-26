package com.flux.app.bridge

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.PowerManager
import android.provider.MediaStore
import android.provider.Settings
import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * MethodChannel surface between the Android engine and the Flutter UI. Every
 * handler resolves on a background dispatcher and transaction lists are
 * paginated (50 per page) so a growing history never blocks the channel.
 */
class Bridge(private val graph: AppGraph) {

    suspend fun dispatch(method: String, arguments: Any?): Any? {
        val args = arguments as? Map<*, *> ?: emptyMap<Any?, Any?>()
        val db = graph.db
        return when (method) {
            "getPulseSummary" -> mapOf(
                "netBalance" to db.transactions().net(),
                "totalCredit" to db.transactions().totalCredit(),
                "totalDebit" to db.transactions().totalDebit(),
                "txCount" to db.transactions().count(),
                "pendingReview" to db.transactions().pendingReview(),
            )

            "getTransactionsPage" -> {
                val page = (args["page"] as? Number)?.toInt() ?: 0
                val pageSize = (args["pageSize"] as? Number)?.toInt() ?: PAGE_SIZE
                val items = db.transactions().page(page, pageSize)
                mapOf(
                    "items" to items.map { it.toMap() },
                    "hasMore" to (items.size == pageSize),
                    "totalCount" to db.transactions().count(),
                )
            }

            "getInbox" -> {
                val limit = (args["limit"] as? Number)?.toInt() ?: 100
                db.transactions().inbox(limit).map { it.toMap() }
            }

            "categorizeTransaction" -> {
                val id = (args["id"] as? Number)?.toLong() ?: error("id required")
                val categoryId = args["categoryId"] as? String ?: error("categoryId required")
                val tx = db.transactions().all().firstOrNull { it.id == id }
                    ?: error("transaction $id not found")
                db.transactions().update(
                    tx.copy(
                        category = categoryId,
                        categoryConfidence = 1.0,
                        needsReview = false,
                        parseMethod = "manual",
                    ),
                )
                graph.engine.learn(tx.merchant, tx.rawText, categoryId)
                true
            }

            "deleteTransaction" -> {
                db.transactions().delete((args["id"] as? Number)?.toLong() ?: error("id required"))
                true
            }

            "getSpendingByCategory" -> {
                val start = (args["startMs"] as? Number)?.toLong() ?: 0L
                val end = (args["endMs"] as? Number)?.toLong() ?: Long.MAX_VALUE
                db.transactions().range(start, end)
                    .filter { it.amount < 0 }
                    .groupBy { it.category }
                    .map { (category, txs) ->
                        mapOf(
                            "categoryId" to category,
                            "total" to txs.sumOf { -it.amount },
                            "count" to txs.size,
                        )
                    }
                    .sortedByDescending { it["total"] as Double }
            }

            "getDailySpend" -> {
                val start = (args["startMs"] as? Number)?.toLong() ?: 0L
                val end = (args["endMs"] as? Number)?.toLong() ?: Long.MAX_VALUE
                val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                db.transactions().range(start, end)
                    .filter { it.amount < 0 }
                    .groupBy { fmt.format(Date(it.timestamp)) }
                    .map { (day, txs) -> mapOf("day" to day, "total" to txs.sumOf { -it.amount }) }
                    .sortedBy { it["day"] as String }
            }

            "getCategories" -> db.categories().all().map { it.toMap() }

            "addCategory" -> {
                val label = args["label"] as? String ?: error("label required")
                val id = (args["id"] as? String) ?: label.lowercase(Locale.US)
                    .replace(Regex("[^a-z0-9]+"), "_").trim('_')
                db.categories().upsert(
                    com.flux.app.data.CategoryEntity(
                        id = id,
                        label = label,
                        color = (args["color"] as? Number)?.toLong() ?: 0xFF64748BL,
                        icon = args["icon"] as? String ?: "category",
                        keywords = (args["keywords"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                        isDefault = false,
                    ),
                )
                graph.engine.retrain()
                id
            }

            "updateCategory" -> {
                val id = args["id"] as? String ?: error("id required")
                val existing = db.categories().all().firstOrNull { it.id == id }
                    ?: error("category $id not found")
                db.categories().upsert(
                    existing.copy(
                        label = args["label"] as? String ?: existing.label,
                        color = (args["color"] as? Number)?.toLong() ?: existing.color,
                        icon = args["icon"] as? String ?: existing.icon,
                        keywords = (args["keywords"] as? List<*>)?.filterIsInstance<String>()
                            ?: existing.keywords,
                    ),
                )
                graph.engine.retrain()
                true
            }

            "deleteCategory" -> {
                val id = args["id"] as? String ?: error("id required")
                val existing = db.categories().all().firstOrNull { it.id == id }
                    ?: error("category $id not found")
                if (existing.isDefault) error("default categories cannot be deleted")
                db.categories().reassignTransactionsOnDelete(id)
                db.categories().delete(existing)
                graph.engine.retrain()
                true
            }

            "exportState" -> exportState()

            "importState" -> {
                val json = args["json"] as? String ?: error("json required")
                importState(json)
            }

            "isNotificationAccessGranted" -> mapOf(
                "granted" to isNotificationAccessGranted(graphContext()),
            )

            "openNotificationAccessSettings" -> {
                val context = graphContext()
                context.startActivity(
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                true
            }

            "isIgnoringBatteryOptimizations" -> mapOf(
                "ignoring" to isIgnoringBatteryOptimizations(graphContext()),
            )

            "requestIgnoreBatteryOptimizations" -> {
                val context = graphContext()
                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                val alreadyIgnoring = pm.isIgnoringBatteryOptimizations(context.packageName)
                if (!alreadyIgnoring) {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            .setData(Uri.parse("package:${context.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
                mapOf("ignoring" to alreadyIgnoring)
            }

            "simulateNotification" -> {
                val text = args["text"] as? String ?: error("text required")
                val pkg = args["packageName"] as? String ?: "flux.simulator"
                val result = graph.engine.ingest(text, pkg, System.currentTimeMillis())
                when (result) {
                    is com.flux.app.engine.TransactionEngine.IngestResult.Stored ->
                        mapOf("status" to "stored", "transaction" to result.transaction.toMap())
                    is com.flux.app.engine.TransactionEngine.IngestResult.Duplicate ->
                        mapOf("status" to "duplicate")
                    is com.flux.app.engine.TransactionEngine.IngestResult.Filtered ->
                        mapOf("status" to "filtered")
                    is com.flux.app.engine.TransactionEngine.IngestResult.Unparsed ->
                        mapOf("status" to "unparsed")
                }
            }

            "setBiometricEnabled" -> {
                db.settings().put(
                    com.flux.app.data.SettingEntry(
                        "biometric_enabled",
                        (args["enabled"] as? Boolean)?.toString() ?: "false",
                    ),
                )
                true
            }

            "getBiometricEnabled" -> db.settings().get("biometric_enabled") == "true"

            else -> error("Unknown method: $method")
        }
    }

    private fun graphContext(): Context = graph.appContext

    private fun isNotificationAccessGranted(context: Context): Boolean {
        val listeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ) ?: return false
        return listeners.split(":").any { it.contains(context.packageName) }
    }

    private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    private suspend fun exportState(): Map<String, Any?> {
        val db = graph.db
        val root = JSONObject()
        root.put("flux_export_version", 1)
        root.put("exported_at", System.currentTimeMillis())

        val baseState = JSONObject()
        val categories = JSONArray()
        db.categories().all().forEach { c ->
            categories.put(
                JSONObject()
                    .put("id", c.id)
                    .put("label", c.label)
                    .put("color", c.color)
                    .put("icon", c.icon)
                    .put("keywords", JSONArray(c.keywords))
                    .put("isDefault", c.isDefault),
            )
        }
        baseState.put("categories", categories)
        val settings = JSONObject()
        db.settings().all().forEach { settings.put(it.key, it.value) }
        baseState.put("settings", settings)
        root.put("base_state", baseState)

        val transactions = JSONArray()
        db.transactions().all().forEach { t ->
            transactions.put(
                JSONObject()
                    .put("id", t.id)
                    .put("hash", t.hash)
                    .put("amount", t.amount)
                    .put("currency", t.currency)
                    .put("merchant", t.merchant)
                    .put("accountHint", t.accountHint ?: JSONObject.NULL)
                    .put("timestamp", t.timestamp)
                    .put("sourcePackage", t.sourcePackage)
                    .put("rawText", t.rawText)
                    .put("category", t.category)
                    .put("categoryConfidence", t.categoryConfidence)
                    .put("needsReview", t.needsReview)
                    .put("parseMethod", t.parseMethod)
                    .put("createdAt", t.createdAt),
            )
        }
        root.put("transactions", transactions)

        val name = "flux_export_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.json"
        val context = graphContext()
        val path = if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED &&
            android.os.Build.VERSION.SDK_INT >= 29
        ) {
            val values = android.content.ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "application/json")
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("could not create download entry")
            context.contentResolver.openOutputStream(uri)!!.use { out ->
                out.write(root.toString().toByteArray(Charsets.UTF_8))
            }
            uri.toString()
        } else {
            val dir = context.getExternalFilesDir(null) ?: context.filesDir
            val file = File(dir, name)
            file.writeText(root.toString())
            file.absolutePath
        }
        return mapOf("path" to path)
    }

    private suspend fun importState(json: String): Map<String, Any?> {
        val root = JSONObject(json)
        val version = root.optInt("flux_export_version", -1)
        require(version in 1..1) { "unsupported flux_export_version $version" }

        val db = graph.db
        val importedCount = db.withTransaction {
            db.transactions().clear()
            db.categories().all().forEach { db.categories().delete(it) }
            db.training().clear()

            val baseState = root.getJSONObject("base_state")
            val categories = baseState.getJSONArray("categories")
            for (i in 0 until categories.length()) {
                val c = categories.getJSONObject(i)
                db.categories().upsert(
                    com.flux.app.data.CategoryEntity(
                        id = c.getString("id"),
                        label = c.getString("label"),
                        color = c.getLong("color"),
                        icon = c.optString("icon", "category"),
                        keywords = c.optJSONArray("keywords")?.let { arr ->
                            (0 until arr.length()).map { arr.getString(it) }
                        } ?: emptyList(),
                        isDefault = c.optBoolean("isDefault", false),
                    ),
                )
            }
            val settings = baseState.optJSONObject("settings")
            if (settings != null) {
                for (key in settings.keys()) {
                    db.settings().put(com.flux.app.data.SettingEntry(key, settings.getString(key)))
                }
            }

            val transactions = root.getJSONArray("transactions")
            for (i in 0 until transactions.length()) {
                val t = transactions.getJSONObject(i)
                db.transactions().insertAll(
                    listOf(
                        com.flux.app.data.TransactionEntity(
                            id = t.getLong("id"),
                            hash = t.getString("hash"),
                            amount = t.getDouble("amount"),
                            currency = t.optString("currency", "INR"),
                            merchant = t.getString("merchant"),
                            accountHint = t.optString("accountHint").takeIf { it.isNotEmpty() && it != "null" },
                            timestamp = t.getLong("timestamp"),
                            sourcePackage = t.optString("sourcePackage", "import"),
                            rawText = t.optString("rawText", ""),
                            category = t.optString("category", "uncategorized"),
                            categoryConfidence = t.optDouble("categoryConfidence", 0.0),
                            needsReview = t.optBoolean("needsReview", false),
                            parseMethod = t.optString("parseMethod", "import"),
                            createdAt = t.optLong("createdAt", System.currentTimeMillis()),
                        ),
                    ),
                )
            }
            transactions.length()
        }
        graph.engine.retrain()
        return mapOf("imported" to importedCount)
    }

    companion object {
        const val PAGE_SIZE = 50
    }
}

/** Serializable form of a transaction for the channel. */
fun com.flux.app.data.TransactionEntity.toMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "amount" to amount,
    "currency" to currency,
    "merchant" to merchant,
    "accountHint" to accountHint,
    "timestamp" to timestamp,
    "category" to category,
    "categoryConfidence" to categoryConfidence,
    "needsReview" to needsReview,
    "parseMethod" to parseMethod,
    "sourcePackage" to sourcePackage,
    "rawText" to rawText,
)

fun com.flux.app.data.CategoryEntity.toMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "label" to label,
    "color" to color,
    "icon" to icon,
    "keywords" to keywords,
    "isDefault" to isDefault,
)

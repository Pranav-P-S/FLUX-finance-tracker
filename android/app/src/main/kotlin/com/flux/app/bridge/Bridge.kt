package com.flux.app.bridge

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.PowerManager
import android.provider.MediaStore
import android.provider.Settings
import androidx.room.withTransaction
import com.flux.app.data.AppDatabase
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
                // Purchases add to a category's spend; refunds for that payee's
                // category subtract from it. Holds never reach the ledger.
                val spend = mutableMapOf<String, Double>()
                val counts = mutableMapOf<String, Int>()
                db.transactions().range(start, end).filter { it.kind != "pending" }.forEach { tx ->
                    if (tx.amount < 0) {
                        spend.merge(tx.category, -tx.amount, Double::plus)
                        counts.merge(tx.category, 1, Int::plus)
                    } else if (tx.kind == "refund") {
                        spend.merge(tx.category, -tx.amount, Double::plus)
                        counts.merge(tx.category, 1, Int::plus)
                    }
                }
                spend.map { (category, total) ->
                    mapOf(
                        "categoryId" to category,
                        "total" to total.coerceAtLeast(0.0),
                        "count" to (counts[category] ?: 0),
                    )
                }
                    .sortedByDescending { it["total"] as Double }
            }

            "getDailySpend" -> {
                val start = (args["startMs"] as? Number)?.toLong() ?: 0L
                val end = (args["endMs"] as? Number)?.toLong() ?: Long.MAX_VALUE
                val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                db.transactions().range(start, end)
                    .filter { it.amount < 0 && it.kind != "pending" }
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
                val path = args["path"] as? String ?: error("path required")
                importState(path)
            }

            "getBaseCurrency" -> mapOf(
                "currency" to (db.settings().get("base_currency") ?: "INR"),
            )

            "setBaseCurrency" -> {
                val currency = args["currency"] as? String ?: error("currency required")
                require(currency in SUPPORTED_CURRENCIES) { "unsupported currency $currency" }
                db.settings().put(com.flux.app.data.SettingEntry("base_currency", currency))
                true
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
        val name = "flux_export_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.json"
        val context = graphContext()

        // Streamed serialization: rows are pulled from the database in pages and
        // written straight to disk, so memory stays flat regardless of archive size.
        suspend fun writeArchive(writer: android.util.JsonWriter) {
            writer.beginObject()
            writer.name("flux_export_version").value(EXPORT_VERSION)
            writer.name("exported_at").value(System.currentTimeMillis())

            writer.name("base_state")
            writer.beginObject()
            writer.name("categories")
            writer.beginArray()
            for (c in db.categories().all()) {
                writer.beginObject()
                writer.name("id").value(c.id)
                writer.name("label").value(c.label)
                writer.name("color").value(c.color)
                writer.name("icon").value(c.icon)
                writer.name("keywords")
                writer.beginArray()
                for (keyword in c.keywords) writer.value(keyword)
                writer.endArray()
                writer.name("isDefault").value(c.isDefault)
                writer.endObject()
            }
            writer.endArray()
            writer.name("settings")
            writer.beginObject()
            for (entry in db.settings().all()) writer.name(entry.key).value(entry.value)
            writer.endObject()
            writer.endObject()

            writer.name("transactions")
            writer.beginArray()
            var page = 0
            while (true) {
                val chunk = db.transactions().page(page, EXPORT_PAGE_SIZE)
                for (t in chunk) {
                    writer.beginObject()
                    writer.name("id").value(t.id)
                    writer.name("hash").value(t.hash)
                    writer.name("amount").value(t.amount)
                    writer.name("currency").value(t.currency)
                    writer.name("merchant").value(t.merchant)
                    writer.name("accountHint").value(t.accountHint ?: "null")
                    writer.name("timestamp").value(t.timestamp)
                    writer.name("sourcePackage").value(t.sourcePackage)
                    writer.name("rawText").value(t.rawText)
                    writer.name("category").value(t.category)
                    writer.name("categoryConfidence").value(t.categoryConfidence)
                    writer.name("needsReview").value(t.needsReview)
                    writer.name("parseMethod").value(t.parseMethod)
                    writer.name("kind").value(t.kind)
                    writer.name("createdAt").value(t.createdAt)
                    writer.endObject()
                }
                if (chunk.size < EXPORT_PAGE_SIZE) break
                page++
            }
            writer.endArray()
            writer.endObject()
        }

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
                java.io.BufferedWriter(java.io.OutputStreamWriter(out, Charsets.UTF_8)).use { buffered ->
                    android.util.JsonWriter(buffered).use { writer -> writeArchive(writer) }
                }
            }
            uri.toString()
        } else {
            val dir = context.getExternalFilesDir(null) ?: context.filesDir
            val file = File(dir, name)
            file.outputStream().use { out ->
                java.io.BufferedWriter(java.io.OutputStreamWriter(out, Charsets.UTF_8)).use { buffered ->
                    android.util.JsonWriter(buffered).use { writer -> writeArchive(writer) }
                }
            }
            file.absolutePath
        }
        return mapOf("path" to path)
    }

    /**
     * Streams an archive back in. Accepts a file path so neither side ever
     * holds the whole document in memory. v1 archives (no `kind` field) import
     * as plain purchases.
     */
    private suspend fun importState(path: String): Map<String, Any?> {
        val db = graph.db
        val importedCount = java.io.FileInputStream(path).use { fin ->
            java.io.InputStreamReader(fin, Charsets.UTF_8).use { streamReader ->
                android.util.JsonReader(streamReader).use { reader ->
                    db.withTransaction {
                        db.transactions().clear()
                        db.categories().all().forEach { db.categories().delete(it) }
                        db.training().clear()

                        var imported = 0
                        reader.beginObject()
                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                "flux_export_version" -> {
                                    val version = reader.nextInt()
                                    require(version in 1..EXPORT_VERSION) {
                                        "unsupported flux_export_version $version"
                                    }
                                }
                                "base_state" -> importBaseState(reader, db)
                                "transactions" -> {
                                    reader.beginArray()
                                    while (reader.hasNext()) {
                                        val entity = importTransaction(reader)
                                        db.transactions().insertAll(listOf(entity))
                                        imported++
                                    }
                                    reader.endArray()
                                }
                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                        imported
                    }
                }
            }
        }
        graph.engine.retrain()
        return mapOf("imported" to importedCount)
    }

    private suspend fun importBaseState(reader: android.util.JsonReader, db: AppDatabase) {
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "categories" -> {
                    reader.beginArray()
                    while (reader.hasNext()) {
                        var id = ""
                        var label = ""
                        var color = 0xFF64748BL
                        var icon = "category"
                        var keywords: List<String> = emptyList()
                        var isDefault = false
                        reader.beginObject()
                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                "id" -> id = reader.nextString()
                                "label" -> label = reader.nextString()
                                "color" -> color = reader.nextLong()
                                "icon" -> icon = reader.nextString()
                                "keywords" -> {
                                    keywords = mutableListOf<String>().also { list ->
                                        reader.beginArray()
                                        while (reader.hasNext()) list.add(reader.nextString())
                                        reader.endArray()
                                    }
                                }
                                "isDefault" -> isDefault = reader.nextBoolean()
                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                        db.categories().upsert(
                            com.flux.app.data.CategoryEntity(
                                id = id,
                                label = label,
                                color = color,
                                icon = icon,
                                keywords = keywords,
                                isDefault = isDefault,
                            ),
                        )
                    }
                    reader.endArray()
                }
                "settings" -> {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        val key = reader.nextName()
                        db.settings().put(com.flux.app.data.SettingEntry(key, reader.nextString()))
                    }
                    reader.endObject()
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
    }

    private fun importTransaction(reader: android.util.JsonReader): com.flux.app.data.TransactionEntity {
        var id = 0L
        var hash = ""
        var amount = 0.0
        var currency = "INR"
        var merchant = ""
        var accountHint: String? = null
        var timestamp = 0L
        var sourcePackage = "import"
        var rawText = ""
        var category = "uncategorized"
        var categoryConfidence = 0.0
        var needsReview = false
        var parseMethod = "import"
        var kind = com.flux.app.data.TransactionKind.PURCHASE
        var createdAt = System.currentTimeMillis()

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "id" -> id = reader.nextLong()
                "hash" -> hash = reader.nextString()
                "amount" -> amount = reader.nextDouble()
                "currency" -> currency = reader.nextString()
                "merchant" -> merchant = reader.nextString()
                "accountHint" -> {
                    if (reader.peek() == android.util.JsonToken.NULL) reader.nextNull() else {
                        val value = reader.nextString()
                        accountHint = value.takeIf { it != "null" }
                    }
                }
                "timestamp" -> timestamp = reader.nextLong()
                "sourcePackage" -> sourcePackage = reader.nextString()
                "rawText" -> rawText = reader.nextString()
                "category" -> category = reader.nextString()
                "categoryConfidence" -> categoryConfidence = reader.nextDouble()
                "needsReview" -> needsReview = reader.nextBoolean()
                "parseMethod" -> parseMethod = reader.nextString()
                "kind" -> kind = reader.nextString()
                "createdAt" -> createdAt = reader.nextLong()
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        return com.flux.app.data.TransactionEntity(
            id = id,
            hash = hash,
            amount = amount,
            currency = currency,
            merchant = merchant,
            accountHint = accountHint,
            timestamp = timestamp,
            sourcePackage = sourcePackage,
            rawText = rawText,
            category = category,
            categoryConfidence = categoryConfidence,
            needsReview = needsReview,
            parseMethod = parseMethod,
            kind = kind,
            createdAt = createdAt,
        )
    }

    companion object {
        const val PAGE_SIZE = 50
        private const val EXPORT_VERSION = 1
        private const val EXPORT_PAGE_SIZE = 500
        private val SUPPORTED_CURRENCIES = setOf("INR", "USD", "EUR", "GBP")
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
    "kind" to kind,
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

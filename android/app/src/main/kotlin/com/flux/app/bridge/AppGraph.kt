package com.flux.app.bridge

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import com.flux.app.data.AppDatabase
import com.flux.app.data.CategoryEntity
import com.flux.app.data.TrainingSample
import com.flux.app.engine.TransactionEngine
import com.flux.app.ml.SeedCorpus
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Process-wide dependency container. A single constructor graph and coroutine
 * scope, created lazily by whichever component reaches the process first —
 * the activity or the notification listener.
 */
class AppGraph private constructor(val appContext: Context) {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val db: AppDatabase = Room.databaseBuilder(appContext, AppDatabase::class.java, "flux.db")
        .addMigrations(*AppDatabase.ALL_MIGRATIONS)
        .build()

    /** Completes once the seed data is committed; the engine's first model
     *  build waits on it so it never trains from an empty database. */
    private val seedReady: Job = scope.launch { seed(appContext) }

    val engine: TransactionEngine = TransactionEngine(db, seedReady)

    val bridge by lazy { Bridge(this) }

    @Volatile private var messenger: io.flutter.plugin.common.BinaryMessenger? = null
    @Volatile private var changeChannel: MethodChannel? = null

    init {
        scope.launch { engine.sweep() }
        scope.launch(Dispatchers.Main) {
            engine.changes.collect {
                // Must be a codec-encoded MethodCall: a raw buffer is rejected
                // by StandardMethodCodec on the Dart side, silently dropping
                // every change notification.
                val channel = changeChannel ?: return@collect
                runCatching { channel.invokeMethod("onTransactionsChanged", null) }
            }
        }
    }

    fun attachFlutterEngine(messenger: io.flutter.plugin.common.BinaryMessenger) {
        this.messenger = messenger
        this.changeChannel = MethodChannel(messenger, CHANNEL)
    }

    fun detachFlutterEngine() {
        messenger = null
        changeChannel = null
    }

    private suspend fun seed(context: Context) {
        db.withTransaction {
            if (db.categories().count() == 0) {
                db.categories().insertAll(
                    SeedCorpus.categories.map { (id, label, color) ->
                        CategoryEntity(
                            id = id,
                            label = label,
                            color = color,
                            icon = defaultIcon(id),
                            keywords = SeedCorpus.keywords[id].orEmpty(),
                            isDefault = true,
                        )
                    },
                )
            }
            if (db.training().count() == 0) {
                db.training().addAll(
                    SeedCorpus.samples.map { (text, categoryId) ->
                        TrainingSample(text = text, categoryId = categoryId)
                    },
                )
            }
        }
        engine.retrain()
    }

    private fun defaultIcon(id: String): String = when (id) {
        "food_drink" -> "restaurant"
        "transport" -> "directions_car"
        "shopping" -> "shopping_bag"
        "bills_utilities" -> "receipt_long"
        "entertainment" -> "movie"
        "health" -> "favorite"
        "groceries" -> "shopping_cart"
        "travel" -> "flight"
        "income" -> "trending_up"
        "transfers" -> "swap_horiz"
        else -> "category"
    }

    companion object {
        const val CHANNEL = "flux.native_bridge"

        @Volatile private var instance: AppGraph? = null

        fun get(context: Context): AppGraph =
            instance ?: synchronized(this) {
                instance ?: AppGraph(context.applicationContext).also { instance = it }
            }
    }
}

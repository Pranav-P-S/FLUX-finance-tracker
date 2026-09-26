package com.flux.app.engine

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.flux.app.bridge.AppGraph
import kotlinx.coroutines.launch

/**
 * Captures transactions from the notification stream. No SMS permissions are
 * required: alerts are read as they are posted, handed to the engine, and the
 * notification is withdrawn once it has been converted into a transaction.
 */
class FluxNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        Log.i(TAG, "listener connected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification?.extras ?: return
        val text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val body = (if (title.isBlank()) text else "$title\n$text").trim()
        if (body.length < 8) return

        val graph = AppGraph.get(applicationContext)
        graph.scope.launch {
            val result = graph.engine.ingest(body, sbn.packageName, sbn.postTime)
            Log.i(TAG, "ingest(${sbn.packageName}) -> $result")
            if (result is TransactionEngine.IngestResult.Stored) {
                runCatching { cancelNotification(sbn.key) }
            }
        }
    }

    companion object {
        private const val TAG = "FluxEngine"
    }
}

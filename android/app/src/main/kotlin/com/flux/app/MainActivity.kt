package com.flux.app

import com.flux.app.bridge.AppGraph
import io.flutter.embedding.android.FlutterFragmentActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// FlutterFragmentActivity (not FlutterActivity) so the local_auth plugin can drive
// the BiometricPrompt from the Vault screen.
class MainActivity : FlutterFragmentActivity() {
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        val graph = AppGraph.get(applicationContext)
        graph.attachFlutterEngine(flutterEngine.dartExecutor.binaryMessenger)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, AppGraph.CHANNEL)
            .setMethodCallHandler { call, result ->
                // DB and file work belongs on the IO scope; only the reply hops
                // back to the platform main thread.
                graph.scope.launch {
                    val reply = runCatching { graph.bridge.dispatch(call.method, call.arguments) }
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        // The engine may detach while a call is in flight; a reply
                        // to a dead channel throws and must never crash the app.
                        runCatching {
                            reply.fold(
                                onSuccess = { result.success(it) },
                                onFailure = { e -> result.error("BRIDGE_ERROR", e.message, null) },
                            )
                        }
                    }
                }
            }
    }

    override fun onDestroy() {
        AppGraph.get(applicationContext).detachFlutterEngine()
        super.onDestroy()
    }
}

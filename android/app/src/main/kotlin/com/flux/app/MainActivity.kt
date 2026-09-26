package com.flux.app

import com.flux.app.bridge.AppGraph
import io.flutter.embedding.android.FlutterFragmentActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// FlutterFragmentActivity (not FlutterActivity) so the local_auth plugin can drive
// the BiometricPrompt from the Vault screen.
class MainActivity : FlutterFragmentActivity() {
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        val graph = AppGraph.get(applicationContext)
        graph.attachFlutterEngine(flutterEngine.dartExecutor.binaryMessenger)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, AppGraph.CHANNEL)
            .setMethodCallHandler { call, result ->
                graph.scope.launch(Dispatchers.Main) {
                    try {
                        result.success(graph.bridge.dispatch(call.method, call.arguments))
                    } catch (e: Exception) {
                        result.error("BRIDGE_ERROR", e.message, null)
                    }
                }
            }
    }

    override fun onDestroy() {
        AppGraph.get(applicationContext).detachFlutterEngine()
        super.onDestroy()
    }
}

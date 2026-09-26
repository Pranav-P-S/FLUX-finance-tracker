import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:local_auth/local_auth.dart';

import 'app.dart';
import 'core/theme/flux_theme.dart';
import 'providers/providers.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  SystemChrome.setSystemUIOverlayStyle(
    const SystemUiOverlayStyle(
      statusBarColor: Colors.transparent,
      systemNavigationBarColor: FluxTheme.bgDeep,
    ),
  );
  runApp(const ProviderScope(child: _FluxGate()));
}

/// Gate widget: if the Vault has biometric unlock enabled, require a
/// successful LocalAuthentication prompt before mounting the app shell.
class _FluxGate extends ConsumerStatefulWidget {
  const _FluxGate();

  @override
  ConsumerState<_FluxGate> createState() => _FluxGateState();
}

class _FluxGateState extends ConsumerState<_FluxGate> {
  bool _unlocked = false;
  bool _checked = false;

  @override
  void initState() {
    super.initState();
    _checkLock();
  }

  Future<void> _checkLock() async {
    try {
      final enabled = await ref.read(bridgeProvider).biometricEnabled();
      if (!enabled) {
        setState(() {
          _unlocked = true;
          _checked = true;
        });
        return;
      }
      final auth = LocalAuthentication();
      final canCheck = await auth.canCheckBiometrics;
      if (!canCheck) {
        setState(() {
          _unlocked = true;
          _checked = true;
        });
        return;
      }
      final ok = await auth.authenticate(
        localizedReason: 'Unlock Flux to view your Pulse',
        biometricOnly: true,
        persistAcrossBackgrounding: true,
      );
      if (mounted) {
        setState(() {
          _unlocked = ok;
          _checked = true;
        });
      }
    } catch (e) {
      debugPrint('Flux gate: $e');
      // Convenience lock must never lock the user out over a plugin/bridge error.
      if (mounted) {
        setState(() {
          _unlocked = true;
          _checked = true;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    if (!_checked) {
      return const MaterialApp(
        debugShowCheckedModeBanner: false,
        home: Scaffold(
          backgroundColor: FluxTheme.bg,
          body: Center(
            child: CircularProgressIndicator(color: FluxTheme.accent),
          ),
        ),
      );
    }
    if (!_unlocked) {
      return MaterialApp(
        debugShowCheckedModeBanner: false,
        theme: FluxTheme.dark(),
        home: Scaffold(
          backgroundColor: FluxTheme.bg,
          body: Center(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Icon(
                  Icons.lock_rounded,
                  color: FluxTheme.inkDim,
                  size: 48,
                ),
                const SizedBox(height: 12),
                const Text(
                  'Flux is locked',
                  style: TextStyle(color: FluxTheme.ink, fontSize: 18),
                ),
                const SizedBox(height: 20),
                NeuButton(
                  onTap: _checkLock,
                  child: const Text(
                    'Unlock',
                    style: TextStyle(
                      color: FluxTheme.accent,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      );
    }
    return const FluxApp();
  }
}

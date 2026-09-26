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
/// successful LocalAuthentication prompt before mounting the app shell, and
/// re-lock whenever the app leaves the foreground. The gate fails closed:
/// plugin or hardware errors land on the lock screen (with a retry), never
/// on the data.
class _FluxGate extends ConsumerStatefulWidget {
  const _FluxGate();

  @override
  ConsumerState<_FluxGate> createState() => _FluxGateState();
}

class _FluxGateState extends ConsumerState<_FluxGate>
    with WidgetsBindingObserver {
  bool _unlocked = false;
  bool _checked = false;
  bool _authInFlight = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _checkLock();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    // Backgrounded with the lock enabled → require biometrics again.
    if (state == AppLifecycleState.paused && _unlocked && _checked) {
      _checkLock(requireNow: false);
    }
  }

  Future<void> _checkLock({bool requireNow = true}) async {
    if (_authInFlight) return;
    _authInFlight = true;
    try {
      final enabled = await ref.read(bridgeProvider).biometricEnabled();
      if (!enabled) {
        _setLocked(locked: false, checked: true);
        return;
      }
      if (!requireNow && mounted) {
        // Coming back from the background: show the lock screen immediately,
        // then start the prompt.
        _setLocked(locked: true, checked: true);
      }
      final ok = await LocalAuthentication().authenticate(
        localizedReason: 'Unlock Flux to view your Pulse',
        // Device-credential fallback (PIN/pattern) so users without enrolled
        // biometrics are never hard-locked out of their own data.
        biometricOnly: false,
        persistAcrossBackgrounding: true,
      );
      _setLocked(locked: !ok, checked: true);
    } catch (e) {
      debugPrint('Flux gate: $e');
      // Fail closed — including when the setting itself cannot be read. The
      // lock screen offers an explicit retry; convenience never defeats it.
      if (mounted) _setLocked(locked: true, checked: true);
    } finally {
      _authInFlight = false;
    }
  }

  void _setLocked({required bool locked, required bool checked}) {
    if (!mounted) return;
    setState(() {
      _unlocked = !locked;
      _checked = checked;
    });
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
                  onTap: () => _checkLock(),
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

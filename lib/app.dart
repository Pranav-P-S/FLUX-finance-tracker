import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'core/theme/flux_theme.dart';
import 'features/inbox/inbox_screen.dart';
import 'features/lens/lens_screen.dart';
import 'features/pulse/pulse_screen.dart';
import 'features/vault/vault_screen.dart';
import 'providers/providers.dart';

class FluxApp extends ConsumerStatefulWidget {
  const FluxApp({super.key});

  @override
  ConsumerState<FluxApp> createState() => _FluxAppState();
}

class _FluxAppState extends ConsumerState<FluxApp> with WidgetsBindingObserver {
  int _tab = 0;
  StreamSubscription<void>? _changesSub;
  Timer? _refreshDebounce;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    // Native store mutated (notification ingested, import, triage on the
    // engine side) → refresh every feature that reads from the bridge.
    // Bursts of ingest events are coalesced into one refresh pass.
    _changesSub = ref.read(bridgeProvider).onTransactionsChanged.listen((_) {
      if (!mounted) return;
      _refreshDebounce?.cancel();
      _refreshDebounce = Timer(const Duration(milliseconds: 300), () {
        if (!mounted) return;
        ref.read(pulseProvider.notifier).refresh();
        ref.read(inboxProvider.notifier).refresh();
        ref.read(recentTransactionsProvider.notifier).refresh();
        ref.read(categoriesProvider.notifier).refresh();
        ref.read(captureStatusProvider.notifier).refresh();
      });
    });
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    // Returning from system settings (notification access, battery) or from
    // the background: capture status may have changed while we were away.
    if (state == AppLifecycleState.resumed) {
      ref.read(captureStatusProvider.notifier).refresh();
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _changesSub?.cancel();
    _refreshDebounce?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    // select: the shell only rebuilds when the badge count changes, not on
    // every equal-length inbox refresh.
    final inboxCount = ref.watch(
      inboxProvider.select((state) => state.value?.length ?? 0),
    );

    return MaterialApp(
      title: 'Flux',
      debugShowCheckedModeBanner: false,
      theme: FluxTheme.dark(),
      home: Scaffold(
        extendBody: true,
        body: Container(
          decoration: const BoxDecoration(
            gradient: RadialGradient(
              center: Alignment(-0.8, -1.0),
              radius: 1.4,
              colors: [Color(0xFF17203A), FluxTheme.bgDeep],
              stops: [0.0, 0.75],
            ),
          ),
          child: SafeArea(
            bottom: false,
            child: AnimatedSwitcher(
              duration: const Duration(milliseconds: 240),
              child: switch (_tab) {
                0 => const PulseScreen(key: ValueKey(0)),
                1 => const InboxScreen(key: ValueKey(1)),
                2 => const LensScreen(key: ValueKey(2)),
                _ => const VaultScreen(key: ValueKey(3)),
              },
            ),
          ),
        ),
        bottomNavigationBar: _GlassNavBar(
          index: _tab,
          inboxBadge: inboxCount,
          onChanged: (i) => setState(() => _tab = i),
        ),
      ),
    );
  }
}

class _GlassNavBar extends StatelessWidget {
  final int index;
  final int inboxBadge;
  final ValueChanged<int> onChanged;

  const _GlassNavBar({
    required this.index,
    required this.inboxBadge,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    final items = [
      (Icons.bolt_rounded, 'Pulse'),
      (Icons.inbox_rounded, 'Inbox'),
      (Icons.donut_large_rounded, 'Lens'),
      (Icons.lock_rounded, 'Vault'),
    ];

    return SafeArea(
      top: false,
      child: Container(
        margin: const EdgeInsets.fromLTRB(16, 0, 16, 14),
        decoration: BoxDecoration(
          color: FluxTheme.surface.withValues(alpha: 0.75),
          borderRadius: BorderRadius.circular(24),
          border: Border.all(color: Colors.white.withValues(alpha: 0.10)),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.45),
              blurRadius: 22,
              offset: const Offset(0, 8),
            ),
          ],
        ),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 8),
          child: Row(
            children: [
              for (final (i, (icon, label)) in items.indexed)
                Expanded(
                  child: GestureDetector(
                    behavior: HitTestBehavior.opaque,
                    onTap: () => onChanged(i),
                    child: AnimatedContainer(
                      duration: const Duration(milliseconds: 200),
                      curve: Curves.easeOut,
                      decoration: BoxDecoration(
                        borderRadius: BorderRadius.circular(18),
                        gradient: index == i ? FluxTheme.accentGradient : null,
                        boxShadow: index == i
                            ? [
                                BoxShadow(
                                  color: FluxTheme.accent.withValues(
                                    alpha: 0.35,
                                  ),
                                  blurRadius: 14,
                                ),
                              ]
                            : null,
                      ),
                      padding: const EdgeInsets.symmetric(vertical: 10),
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Badge(
                            isLabelVisible: i == 1 && inboxBadge > 0,
                            label: Text('$inboxBadge'),
                            backgroundColor: FluxTheme.debt,
                            child: Icon(
                              icon,
                              size: 22,
                              color: index == i
                                  ? Colors.black
                                  : FluxTheme.inkDim,
                            ),
                          ),
                          const SizedBox(height: 2),
                          Text(
                            label,
                            style: TextStyle(
                              fontSize: 10,
                              fontWeight: index == i
                                  ? FontWeight.w700
                                  : FontWeight.w500,
                              color: index == i
                                  ? Colors.black
                                  : FluxTheme.inkDim,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}

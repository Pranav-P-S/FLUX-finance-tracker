import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/theme/flux_theme.dart';
import '../../models/flux_models.dart';
import '../../providers/providers.dart';
import '../../widgets/tx_tile.dart';

/// Net balance hero, credit/debit split, capture onboarding and recent
/// activity.
class PulseScreen extends ConsumerWidget {
  const PulseScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final pulse = ref.watch(pulseProvider);
    final txs = ref.watch(recentTransactionsProvider);
    final capture = ref.watch(captureStatusProvider);

    return RefreshIndicator(
      color: FluxTheme.accent,
      onRefresh: () async {
        await ref.read(pulseProvider.notifier).refresh();
        await ref.read(recentTransactionsProvider.notifier).refresh();
        await ref.read(captureStatusProvider.notifier).refresh();
      },
      child: ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.fromLTRB(20, 12, 20, 110),
        children: [
          const _Header(),
          const SizedBox(height: 18),
          capture.when(
            loading: () => const SizedBox.shrink(),
            error: (e, _) => const SizedBox.shrink(),
            data: (status) => _OnboardingCard(
              status: status,
              onChanged: () async {
                await ref.read(captureStatusProvider.notifier).refresh();
              },
            ),
          ),
          pulse.when(
            loading: () => const Padding(
              padding: EdgeInsets.all(60),
              child: Center(
                child: CircularProgressIndicator(color: FluxTheme.accent),
              ),
            ),
            error: (e, _) => GlassCard(
              child: Text(
                'Pulse unavailable: $e',
                style: const TextStyle(color: FluxTheme.debt),
              ),
            ),
            data: (p) => _PulseHero(summary: p),
          ),
          const SizedBox(height: 18),
          _SectionTitle('Recent activity'),
          const SizedBox(height: 10),
          txs.when(
            loading: () => const Padding(
              padding: EdgeInsets.all(32),
              child: Center(
                child: CircularProgressIndicator(color: FluxTheme.accent),
              ),
            ),
            error: (e, _) => Text(
              'Could not load transactions: $e',
              style: const TextStyle(color: FluxTheme.debt),
            ),
            data: (list) {
              final categories =
                  ref.watch(categoriesProvider).value ?? const <FluxCategory>[];
              if (list.isEmpty) {
                return GlassCard(
                  padding: const EdgeInsets.all(26),
                  child: Column(
                    children: const [
                      Icon(
                        Icons.bolt_rounded,
                        color: FluxTheme.inkDim,
                        size: 34,
                      ),
                      SizedBox(height: 8),
                      Text(
                        'No transactions yet.\nBank alerts you allow will land here automatically.',
                        textAlign: TextAlign.center,
                        style: TextStyle(color: FluxTheme.inkDim, height: 1.4),
                      ),
                    ],
                  ),
                );
              }
              return Column(
                children: [
                  for (final tx in list.take(6))
                    TxTile(
                      tx: tx,
                      categoryLabel: _labelFor(categories, tx.category),
                      categoryColor: _colorFor(categories, tx.category),
                    ),
                ],
              );
            },
          ),
        ],
      ),
    );
  }

  static String _labelFor(List<FluxCategory> categories, String id) =>
      categories.where((c) => c.id == id).firstOrNull?.label ?? id;

  static Color _colorFor(List<FluxCategory> categories, String id) =>
      categories.where((c) => c.id == id).firstOrNull?.color ??
      const Color(0xFF64748B);
}

class _Header extends StatelessWidget {
  const _Header();

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        ShaderMask(
          shaderCallback: (r) => FluxTheme.accentGradient.createShader(r),
          child: const Text(
            'FLUX',
            style: TextStyle(
              fontSize: 26,
              fontWeight: FontWeight.w900,
              letterSpacing: 3,
            ),
          ),
        ),
        const Spacer(),
        const CircleAvatar(
          radius: 18,
          backgroundColor: FluxTheme.surface,
          child: Icon(
            Icons.graphic_eq_rounded,
            color: FluxTheme.accent,
            size: 20,
          ),
        ),
      ],
    );
  }
}

class _PulseHero extends StatelessWidget {
  final PulseSummary summary;
  const _PulseHero({required this.summary});

  @override
  Widget build(BuildContext context) {
    final currency = 'INR';
    return GlassCard(
      margin: const EdgeInsets.only(top: 14),
      padding: const EdgeInsets.all(24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Text(
                'Net balance',
                style: TextStyle(color: FluxTheme.inkDim, fontSize: 13),
              ),
              const Spacer(),
              Container(
                padding: const EdgeInsets.symmetric(
                  horizontal: 10,
                  vertical: 5,
                ),
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(999),
                  color: FluxTheme.accent.withValues(alpha: 0.12),
                  border: Border.all(
                    color: FluxTheme.accent.withValues(alpha: 0.35),
                  ),
                ),
                child: Text(
                  '${summary.txCount} tracked',
                  style: const TextStyle(color: FluxTheme.accent, fontSize: 11),
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          ShaderMask(
            shaderCallback: (r) => FluxTheme.accentGradient.createShader(r),
            child: Text(
              formatMoney(summary.netBalance, currency: currency),
              style: const TextStyle(fontSize: 40, fontWeight: FontWeight.w800),
            ),
          ),
          const SizedBox(height: 18),
          Row(
            children: [
              Expanded(
                child: _FlowBadge(
                  icon: Icons.south_west_rounded,
                  label: 'Credit',
                  value: formatMoney(summary.totalCredit, currency: currency),
                  color: FluxTheme.credit,
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: _FlowBadge(
                  icon: Icons.north_east_rounded,
                  label: 'Debit',
                  value: formatMoney(summary.totalDebit, currency: currency),
                  color: FluxTheme.debt,
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _FlowBadge extends StatelessWidget {
  final IconData icon;
  final String label;
  final String value;
  final Color color;

  const _FlowBadge({
    required this.icon,
    required this.label,
    required this.value,
    required this.color,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.08),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: color.withValues(alpha: 0.25)),
      ),
      child: Row(
        children: [
          Icon(icon, color: color, size: 20),
          const SizedBox(width: 8),
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                label,
                style: const TextStyle(color: FluxTheme.inkDim, fontSize: 11),
              ),
              Text(
                value,
                style: TextStyle(
                  color: color,
                  fontWeight: FontWeight.w700,
                  fontSize: 14,
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _OnboardingCard extends ConsumerWidget {
  final CaptureStatus status;
  final VoidCallback onChanged;

  const _OnboardingCard({required this.status, required this.onChanged});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    if (status.notificationAccess && status.batteryOptimized) {
      return const SizedBox.shrink();
    }
    return GlassCard(
      margin: const EdgeInsets.only(top: 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (!status.notificationAccess) ...[
            const Row(
              children: [
                Icon(
                  Icons.notifications_active_rounded,
                  color: FluxTheme.accent,
                ),
                SizedBox(width: 10),
                Expanded(
                  child: Text(
                    'Grant notification access to start capture',
                    style: TextStyle(fontWeight: FontWeight.w600),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 6),
            const Text(
              'Flux reads bank/UPI alerts from your notifications and extracts '
              'transactions on-device. Nothing leaves your phone.',
              style: TextStyle(
                color: FluxTheme.inkDim,
                fontSize: 12,
                height: 1.4,
              ),
            ),
            const SizedBox(height: 12),
            NeuButton(
              onTap: () async {
                await ref.read(bridgeProvider).openNotificationAccessSettings();
              },
              child: const Text(
                'Enable capture',
                style: TextStyle(
                  color: FluxTheme.accent,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ),
          ],
          if (!status.notificationAccess && !status.batteryOptimized)
            const SizedBox(height: 14),
          if (!status.batteryOptimized) ...[
            const Row(
              children: [
                Icon(Icons.battery_saver_rounded, color: FluxTheme.accent2),
                SizedBox(width: 10),
                Expanded(
                  child: Text(
                    'Exempt Flux from battery optimization',
                    style: TextStyle(fontWeight: FontWeight.w600),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 6),
            const Text(
              'Keeps the capture engine alive in the background.',
              style: TextStyle(
                color: FluxTheme.inkDim,
                fontSize: 12,
                height: 1.4,
              ),
            ),
            const SizedBox(height: 12),
            NeuButton(
              onTap: () async {
                await ref
                    .read(bridgeProvider)
                    .requestIgnoreBatteryOptimizations();
                onChanged();
              },
              child: const Text(
                'Optimize reliability',
                style: TextStyle(
                  color: FluxTheme.accent2,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }
}

class _SectionTitle extends StatelessWidget {
  final String text;
  const _SectionTitle(this.text);

  @override
  Widget build(BuildContext context) {
    return Text(
      text,
      style: const TextStyle(
        fontSize: 16,
        fontWeight: FontWeight.w700,
        letterSpacing: 0.3,
      ),
    );
  }
}

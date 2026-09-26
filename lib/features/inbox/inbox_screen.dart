import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/theme/flux_theme.dart';
import '../../models/flux_models.dart';
import '../../providers/providers.dart';

/// Review queue for transactions the categorizer was not confident about.
/// Swipe right to approve (pick category), left to delete.
class InboxScreen extends ConsumerWidget {
  const InboxScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final inbox = ref.watch(inboxProvider);

    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 12, 20, 110),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            'Inbox',
            style: TextStyle(fontSize: 24, fontWeight: FontWeight.w800),
          ),
          const SizedBox(height: 4),
          Text(
            'Swipe right to approve • left to discard',
            style: const TextStyle(color: FluxTheme.inkDim, fontSize: 12),
          ),
          const SizedBox(height: 16),
          Expanded(
            child: inbox.when(
              loading: () => const Center(
                child: CircularProgressIndicator(color: FluxTheme.accent),
              ),
              error: (e, _) => Center(
                child: Text(
                  'Inbox unavailable: $e',
                  style: const TextStyle(color: FluxTheme.debt),
                ),
              ),
              data: (items) {
                if (items.isEmpty) {
                  return GlassCard(
                    padding: const EdgeInsets.all(30),
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: const [
                        Icon(
                          Icons.task_alt_rounded,
                          color: FluxTheme.credit,
                          size: 44,
                        ),
                        SizedBox(height: 12),
                        Text(
                          'Inbox zero',
                          style: TextStyle(
                            fontSize: 18,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                        SizedBox(height: 6),
                        Text(
                          'Transactions the categorizer is unsure about will wait here for your call.',
                          textAlign: TextAlign.center,
                          style: TextStyle(
                            color: FluxTheme.inkDim,
                            height: 1.4,
                          ),
                        ),
                      ],
                    ),
                  );
                }
                return _TriageStack(items: items.reversed.toList());
              },
            ),
          ),
        ],
      ),
    );
  }
}

class _TriageStack extends ConsumerStatefulWidget {
  final List<Transaction> items;
  const _TriageStack({required this.items});

  @override
  ConsumerState<_TriageStack> createState() => _TriageStackState();
}

class _TriageStackState extends ConsumerState<_TriageStack> {
  static const _showDepth = 3;

  @override
  Widget build(BuildContext context) {
    final visible = widget.items.take(_showDepth).toList();
    return Stack(
      alignment: Alignment.center,
      fit: StackFit.expand,
      children: [
        // Back cards render as empty glass shells so their content never
        // bleeds through the translucent top card.
        for (final (depth, _) in visible.indexed.skip(1))
          Transform.scale(
            scale: 1.0 - depth * 0.05,
            child: Opacity(
              opacity: 1.0 - depth * 0.3,
              child: const GlassCard(blur: 0, child: SizedBox.expand()),
            ),
          ),
        if (visible.isNotEmpty)
          _TriageCard(key: ValueKey(visible.first.id), tx: visible.first),
      ],
    );
  }
}

class _TriageCard extends ConsumerStatefulWidget {
  final Transaction tx;

  const _TriageCard({super.key, required this.tx});

  @override
  ConsumerState<_TriageCard> createState() => _TriageCardState();
}

class _TriageCardState extends ConsumerState<_TriageCard> {
  String? _picked;

  Future<void> _approve() async {
    final categories = ref.read(categoriesProvider).value ?? [];
    final categoryId = _picked ?? _suggest(categories);
    await ref.read(inboxProvider.notifier).approve(widget.tx, categoryId);
    _toast('Categorized as ${_labelOf(categories, categoryId)}');
  }

  Future<void> _discard() async {
    await ref.read(inboxProvider.notifier).discard(widget.tx);
    _toast('Discarded');
  }

  String _suggest(List<FluxCategory> categories) {
    // Preselect the model's own guess when it made one.
    for (final c in categories) {
      if (c.id == widget.tx.category && widget.tx.categoryConfidence > 0) {
        return c.id;
      }
    }
    return categories
        .firstWhere(
          (c) => c.id == 'uncategorized',
          orElse: () => categories.first,
        )
        .id;
  }

  String _labelOf(List<FluxCategory> categories, String id) => categories
      .firstWhere((c) => c.id == id, orElse: () => categories.first)
      .label;

  void _toast(String message) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(
        SnackBar(
          content: Text(message),
          duration: const Duration(milliseconds: 900),
        ),
      );
  }

  @override
  Widget build(BuildContext context) {
    final categories =
        ref.watch(categoriesProvider).value ?? const <FluxCategory>[];
    final selected = _picked ?? _suggest(categories);
    final tx = widget.tx;

    return Dismissible(
      key: ObjectKey(tx),
      direction: DismissDirection.horizontal,
      background: _SwipeUnder(
        gradient: FluxTheme.credit.withValues(alpha: 0.25),
        icon: Icons.check_circle_rounded,
        color: FluxTheme.credit,
        label: 'Approve',
        alignment: Alignment.centerLeft,
      ),
      secondaryBackground: _SwipeUnder(
        gradient: FluxTheme.debt.withValues(alpha: 0.25),
        icon: Icons.delete_rounded,
        color: FluxTheme.debt,
        label: 'Discard',
        alignment: Alignment.centerRight,
      ),
      onDismissed: (_) {}, // state refresh is driven by the provider
      confirmDismiss: (direction) async {
        if (direction == DismissDirection.startToEnd) {
          await _approve();
        } else {
          await _discard();
        }
        return true;
      },
      child: GlassCard(
        padding: const EdgeInsets.all(22),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Text(
                  'REVIEW',
                  style: TextStyle(
                    letterSpacing: 2,
                    fontSize: 10,
                    color: FluxTheme.inkDim,
                  ),
                ),
                const Spacer(),
                Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 8,
                    vertical: 4,
                  ),
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(999),
                    color: FluxTheme.accent2.withValues(alpha: 0.12),
                  ),
                  child: Text(
                    '${(tx.categoryConfidence * 100).toStringAsFixed(0)}% confidence',
                    style: const TextStyle(
                      color: FluxTheme.accent2,
                      fontSize: 10,
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),
            Text(
              tx.merchant,
              style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w800),
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
            ),
            const SizedBox(height: 6),
            Text(
              '${tx.isCredit ? "+" : "-"}${formatMoney(tx.amount.abs(), currency: tx.currency)}'
              '  •  ${DateTime.fromMillisecondsSinceEpoch(tx.timestamp).toString().split(" ").first}',
              style: TextStyle(
                fontSize: 15,
                fontWeight: FontWeight.w600,
                color: tx.isCredit ? FluxTheme.credit : FluxTheme.inkDim,
              ),
            ),
            const SizedBox(height: 14),
            Container(
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: Colors.white.withValues(alpha: 0.04),
                borderRadius: BorderRadius.circular(12),
              ),
              child: Text(
                tx.rawText,
                maxLines: 3,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                  color: FluxTheme.inkDim,
                  fontSize: 11,
                  height: 1.35,
                ),
              ),
            ),
            const Spacer(),
            const Text(
              'Category',
              style: TextStyle(color: FluxTheme.inkDim, fontSize: 11),
            ),
            const SizedBox(height: 8),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                for (final c in categories)
                  GestureDetector(
                    onTap: () => setState(() => _picked = c.id),
                    child: AnimatedContainer(
                      duration: const Duration(milliseconds: 140),
                      padding: const EdgeInsets.symmetric(
                        horizontal: 12,
                        vertical: 7,
                      ),
                      decoration: BoxDecoration(
                        borderRadius: BorderRadius.circular(999),
                        color: selected == c.id
                            ? c.color.withValues(alpha: 0.22)
                            : Colors.white.withValues(alpha: 0.04),
                        border: Border.all(
                          color: selected == c.id
                              ? c.color
                              : Colors.white.withValues(alpha: 0.08),
                        ),
                      ),
                      child: Text(
                        c.label,
                        style: TextStyle(
                          fontSize: 11,
                          fontWeight: selected == c.id
                              ? FontWeight.w700
                              : FontWeight.w400,
                          color: selected == c.id ? c.color : FluxTheme.inkDim,
                        ),
                      ),
                    ),
                  ),
              ],
            ),
            const SizedBox(height: 18),
            Row(
              children: [
                Expanded(
                  child: NeuButton(
                    onTap: _discard,
                    pressed: true,
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: const [
                        Icon(
                          Icons.close_rounded,
                          color: FluxTheme.debt,
                          size: 18,
                        ),
                        SizedBox(width: 6),
                        Text(
                          'Discard',
                          style: TextStyle(
                            color: FluxTheme.debt,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: NeuButton(
                    onTap: _approve,
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: const [
                        Icon(
                          Icons.check_rounded,
                          color: FluxTheme.credit,
                          size: 18,
                        ),
                        SizedBox(width: 6),
                        Text(
                          'Approve',
                          style: TextStyle(
                            color: FluxTheme.credit,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _SwipeUnder extends StatelessWidget {
  final Color gradient;
  final IconData icon;
  final Color color;
  final String label;
  final Alignment alignment;

  const _SwipeUnder({
    required this.gradient,
    required this.icon,
    required this.color,
    required this.label,
    required this.alignment,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: 2),
      alignment: alignment,
      padding: const EdgeInsets.symmetric(horizontal: 28),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(22),
        gradient: LinearGradient(colors: [gradient, Colors.transparent]),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (alignment == Alignment.centerLeft) Icon(icon, color: color),
          const SizedBox(width: 8),
          Text(
            label,
            style: TextStyle(color: color, fontWeight: FontWeight.w800),
          ),
          if (alignment == Alignment.centerRight) ...[
            const SizedBox(width: 8),
            Icon(icon, color: color),
          ],
        ],
      ),
    );
  }
}

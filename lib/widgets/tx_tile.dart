import 'package:flutter/material.dart';

import '../core/theme/flux_theme.dart';
import '../models/flux_models.dart';

/// Shared transaction row: sign-aware amount pill, category dot, merchant.
class TxTile extends StatelessWidget {
  final Transaction tx;
  final String categoryLabel;
  final Color? categoryColor;
  final VoidCallback? onTap;

  const TxTile({
    super.key,
    required this.tx,
    required this.categoryLabel,
    this.categoryColor,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final color = categoryColor ?? const Color(0xFF64748B);
    final time = DateTime.fromMillisecondsSinceEpoch(tx.timestamp);
    final mm = time.month.toString().padLeft(2, '0');
    final dd = time.day.toString().padLeft(2, '0');
    final hh = time.hour.toString().padLeft(2, '0');
    final mi = time.minute.toString().padLeft(2, '0');

    // Merge the row's fragments into one screen-reader announcement:
    // merchant, category, date and amount in a single utterance.
    return MergeSemantics(
      child: Padding(
        padding: const EdgeInsets.only(bottom: 10),
        child: GlassCard(
          blur: 0,
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
          child: Row(
            children: [
              Container(
                width: 38,
                height: 38,
                decoration: BoxDecoration(
                  color: color.withValues(alpha: 0.14),
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: color.withValues(alpha: 0.35)),
                ),
                child: tx.isCredit
                    ? const Icon(
                        Icons.south_west_rounded,
                        color: FluxTheme.credit,
                        size: 18,
                      )
                    : const Icon(
                        Icons.north_east_rounded,
                        color: FluxTheme.debt,
                        size: 18,
                      ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      tx.merchant,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        fontWeight: FontWeight.w600,
                        fontSize: 14,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Row(
                      children: [
                        Container(
                          width: 7,
                          height: 7,
                          decoration: BoxDecoration(
                            color: color,
                            shape: BoxShape.circle,
                          ),
                        ),
                        const SizedBox(width: 5),
                        Text(
                          categoryLabel,
                          style: const TextStyle(
                            color: FluxTheme.inkDim,
                            fontSize: 11,
                          ),
                        ),
                        if (tx.isRefund) ...[
                          const SizedBox(width: 6),
                          _TagChip(label: 'REFUND', color: FluxTheme.credit),
                        ],
                        if (tx.isPending) ...[
                          const SizedBox(width: 6),
                          const _TagChip(
                            label: 'PENDING',
                            color: FluxTheme.inkDim,
                          ),
                        ],
                        Text(
                          '  •  $dd/$mm $hh:$mi',
                          style: const TextStyle(
                            color: FluxTheme.inkDim,
                            fontSize: 11,
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 8),
              Text(
                formatMoney(tx.amount, currency: tx.currency),
                style: TextStyle(
                  fontWeight: FontWeight.w800,
                  fontSize: 14,
                  color: tx.isCredit ? FluxTheme.credit : FluxTheme.ink,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// Small lifecycle tag for rows that are refunds or unsettled holds.
class _TagChip extends StatelessWidget {
  final String label;
  final Color color;

  const _TagChip({required this.label, required this.color});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 1.5),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(5),
        color: color.withValues(alpha: 0.14),
        border: Border.all(color: color.withValues(alpha: 0.4)),
      ),
      child: Text(
        label,
        style: TextStyle(
          color: color,
          fontSize: 8.5,
          fontWeight: FontWeight.w700,
          letterSpacing: 0.5,
        ),
      ),
    );
  }
}

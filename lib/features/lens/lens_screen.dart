import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/theme/flux_theme.dart';
import '../../models/flux_models.dart';
import '../../providers/providers.dart';
import '../../widgets/charts.dart';

/// Spending by category and by day for a chosen month.
class LensScreen extends ConsumerStatefulWidget {
  const LensScreen({super.key});

  @override
  ConsumerState<LensScreen> createState() => _LensScreenState();
}

class _LensScreenState extends ConsumerState<LensScreen> {
  late DateTime _month = DateTime.now();

  @override
  Widget build(BuildContext context) {
    final lens = ref.watch(lensProvider(_month));
    final categories = ref.watch(categoriesProvider).value ?? const [];

    return ListView(
      padding: const EdgeInsets.fromLTRB(20, 12, 20, 110),
      children: [
        Row(
          children: [
            const Text(
              'Lens',
              style: TextStyle(fontSize: 24, fontWeight: FontWeight.w800),
            ),
            const Spacer(),
            NeuButton(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
              radius: 14,
              onTap: () => setState(() {
                _month = DateTime(_month.year, _month.month - 1);
              }),
              child: const Icon(Icons.chevron_left_rounded, size: 20),
            ),
            const SizedBox(width: 8),
            Text(
              '${_monthName(_month.month)} ${_month.year}',
              style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 14),
            ),
            const SizedBox(width: 8),
            NeuButton(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
              radius: 14,
              onTap: () => setState(() {
                _month = DateTime(_month.year, _month.month + 1);
              }),
              child: const Icon(Icons.chevron_right_rounded, size: 20),
            ),
          ],
        ),
        const SizedBox(height: 18),
        lens.when(
          loading: () => const Padding(
            padding: EdgeInsets.all(60),
            child: Center(
              child: CircularProgressIndicator(color: FluxTheme.accent),
            ),
          ),
          error: (e, _) => GlassCard(
            child: Text(
              'Lens unavailable: $e',
              style: const TextStyle(color: FluxTheme.debt),
            ),
          ),
          data: (data) {
            final slices = <PieSlice>[];
            for (final spend in data.byCategory) {
              final cat = categories
                  .where((c) => c.id == spend.categoryId)
                  .firstOrNull;
              slices.add(
                PieSlice(
                  categoryId: spend.categoryId,
                  label: cat?.label ?? spend.categoryId,
                  color: cat?.color ?? const Color(0xFF64748B),
                  value: spend.total,
                ),
              );
            }
            final total = slices.fold<double>(0, (s, x) => s + x.value);

            if (total <= 0) {
              return GlassCard(
                padding: const EdgeInsets.all(30),
                child: Column(
                  children: const [
                    Icon(
                      Icons.pie_chart_outline_rounded,
                      color: FluxTheme.inkDim,
                      size: 40,
                    ),
                    SizedBox(height: 10),
                    Text(
                      'No spending recorded this month',
                      style: TextStyle(color: FluxTheme.inkDim, height: 1.4),
                      textAlign: TextAlign.center,
                    ),
                  ],
                ),
              );
            }

            return Column(
              children: [
                GlassCard(
                  child: Column(
                    children: [
                      Center(
                        child: NeonPieChart(
                          slices: slices,
                          size: 230,
                          centerLabel: 'spent this month',
                          centerValue: formatMoney(total),
                        ),
                      ),
                      const SizedBox(height: 18),
                      for (final slice in slices.take(6))
                        Padding(
                          padding: const EdgeInsets.symmetric(vertical: 5),
                          child: Row(
                            children: [
                              Container(
                                width: 10,
                                height: 10,
                                decoration: BoxDecoration(
                                  color: slice.color,
                                  borderRadius: BorderRadius.circular(3),
                                  boxShadow: [
                                    BoxShadow(
                                      color: slice.color.withValues(alpha: 0.6),
                                      blurRadius: 6,
                                    ),
                                  ],
                                ),
                              ),
                              const SizedBox(width: 10),
                              Expanded(
                                child: Text(
                                  slice.label,
                                  style: const TextStyle(fontSize: 13),
                                ),
                              ),
                              Text(
                                formatMoney(slice.value),
                                style: const TextStyle(
                                  fontWeight: FontWeight.w700,
                                  fontSize: 13,
                                ),
                              ),
                              const SizedBox(width: 10),
                              SizedBox(
                                width: 42,
                                child: Text(
                                  '${(slice.value / total * 100).toStringAsFixed(0)}%',
                                  textAlign: TextAlign.right,
                                  style: const TextStyle(
                                    color: FluxTheme.inkDim,
                                    fontSize: 11,
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ),
                    ],
                  ),
                ),
                const SizedBox(height: 18),
                GlassCard(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text(
                        'Daily spend',
                        style: TextStyle(
                          fontWeight: FontWeight.w700,
                          fontSize: 14,
                        ),
                      ),
                      const SizedBox(height: 14),
                      HistogramChart(
                        bars: [
                          for (final d in data.byDay)
                            DaySpendBar(day: d.day, value: d.total),
                        ],
                      ),
                      if (data.byDay.length >= 2) ...[
                        const SizedBox(height: 8),
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Text(
                              '${data.byDay.first.day.day}/${data.byDay.first.day.month}',
                              style: const TextStyle(
                                color: FluxTheme.inkDim,
                                fontSize: 10,
                              ),
                            ),
                            Text(
                              '${data.byDay.last.day.day}/${data.byDay.last.day.month}',
                              style: const TextStyle(
                                color: FluxTheme.inkDim,
                                fontSize: 10,
                              ),
                            ),
                          ],
                        ),
                      ],
                    ],
                  ),
                ),
              ],
            );
          },
        ),
      ],
    );
  }

  String _monthName(int m) => const [
    'Jan',
    'Feb',
    'Mar',
    'Apr',
    'May',
    'Jun',
    'Jul',
    'Aug',
    'Sep',
    'Oct',
    'Nov',
    'Dec',
  ][m - 1];
}

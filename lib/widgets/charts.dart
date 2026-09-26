import 'dart:math' as math;

import 'package:flutter/foundation.dart' show listEquals;
import 'package:flutter/material.dart';

import '../core/theme/flux_theme.dart';

class PieSlice {
  final String categoryId;
  final String label;
  final Color color;
  final double value;

  const PieSlice({
    required this.categoryId,
    required this.label,
    required this.color,
    required this.value,
  });

  // Value equality so chart animations restart only when the data changes,
  // not on every parent rebuild that happens to allocate a new list.
  @override
  bool operator ==(Object other) =>
      other is PieSlice &&
      other.categoryId == categoryId &&
      other.label == label &&
      other.color == color &&
      other.value == value;

  @override
  int get hashCode => Object.hash(categoryId, label, color, value);
}

/// Spring-animated neon donut with glow strokes and a center total.
class NeonPieChart extends StatefulWidget {
  final List<PieSlice> slices;
  final double size;
  final String centerLabel;
  final String centerValue;

  const NeonPieChart({
    super.key,
    required this.slices,
    required this.size,
    required this.centerLabel,
    required this.centerValue,
  });

  @override
  State<NeonPieChart> createState() => _NeonPieChartState();
}

class _NeonPieChartState extends State<NeonPieChart>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 900),
  );
  late final Animation<double> _anim = CurvedAnimation(
    parent: _controller,
    curve: Curves.easeOutBack,
  );

  @override
  void initState() {
    super.initState();
    _controller.forward();
  }

  @override
  void didUpdateWidget(NeonPieChart oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (!listEquals(oldWidget.slices, widget.slices)) {
      _controller.forward(from: 0);
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Semantics(
      label: 'Spending by category, total ${widget.centerValue}',
      child: AnimatedBuilder(
        animation: _anim,
        builder: (context, _) => CustomPaint(
          size: Size.square(widget.size),
          painter: _NeonPiePainter(
            slices: widget.slices,
            progress: _anim.value.clamp(0.0, 1.0),
            centerLabel: widget.centerLabel,
            centerValue: widget.centerValue,
          ),
        ),
      ),
    );
  }
}

class _NeonPiePainter extends CustomPainter {
  final List<PieSlice> slices;
  final double progress;
  final String centerLabel;
  final String centerValue;

  _NeonPiePainter({
    required this.slices,
    required this.progress,
    required this.centerLabel,
    required this.centerValue,
  });

  @override
  void paint(Canvas canvas, Size size) {
    final center = size.center(Offset.zero);
    final radius = size.shortestSide / 2 - 8;
    final total = slices.fold<double>(0, (s, x) => s + x.value);

    final trackPaint = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = 18
      ..color = Colors.white.withValues(alpha: 0.05);
    canvas.drawCircle(center, radius, trackPaint);

    if (total <= 0 || slices.isEmpty) return;

    final eased = Curves.easeOutCubic.transform(progress);
    var startAngle = -math.pi / 2;
    final sweepScale = 2 * math.pi * eased;

    for (final slice in slices) {
      final sweep = (slice.value / total) * 2 * math.pi * eased;
      if (sweep <= 0.002) continue;
      final rect = Rect.fromCircle(center: center, radius: radius);

      final glow = Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = 18
        ..strokeCap = StrokeCap.butt
        ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 10)
        ..color = slice.color.withValues(alpha: 0.55);
      canvas.drawArc(rect, startAngle, sweep, false, glow);

      final stroke = Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = 14
        ..strokeCap = StrokeCap.butt
        ..color = slice.color;
      canvas.drawArc(rect, startAngle, sweep, false, stroke);

      startAngle += sweep;
      if (startAngle - (-math.pi / 2) > sweepScale) break;
    }

    final labelPainter = TextPainter(
      text: TextSpan(
        text: centerValue,
        style: const TextStyle(
          color: FluxTheme.ink,
          fontSize: 22,
          fontWeight: FontWeight.w700,
        ),
      ),
      textAlign: TextAlign.center,
      textDirection: TextDirection.ltr,
    )..layout(maxWidth: radius * 1.6);
    labelPainter.paint(
      canvas,
      center - Offset(labelPainter.width / 2, labelPainter.height + 2),
    );

    final subPainter = TextPainter(
      text: TextSpan(
        text: centerLabel,
        style: const TextStyle(color: FluxTheme.inkDim, fontSize: 11),
      ),
      textAlign: TextAlign.center,
      textDirection: TextDirection.ltr,
    )..layout(maxWidth: radius * 1.6);
    subPainter.paint(canvas, center - Offset(subPainter.width / 2, -4));
  }

  @override
  bool shouldRepaint(_NeonPiePainter oldDelegate) =>
      oldDelegate.progress != progress ||
      !listEquals(oldDelegate.slices, slices) ||
      oldDelegate.centerValue != centerValue;
}

/// Rounded histogram bars with gradient fill and a soft glow top cap.
class HistogramChart extends StatefulWidget {
  final List<DaySpendBar> bars;
  final double height;

  const HistogramChart({super.key, required this.bars, this.height = 160});

  @override
  State<HistogramChart> createState() => _HistogramChartState();
}

class _HistogramChartState extends State<HistogramChart>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 800),
  );
  late final Animation<double> _anim = CurvedAnimation(
    parent: _controller,
    curve: Curves.easeOutCubic,
  );

  @override
  void initState() {
    super.initState();
    _controller.forward();
  }

  @override
  void didUpdateWidget(HistogramChart oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (!listEquals(oldWidget.bars, widget.bars)) _controller.forward(from: 0);
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Semantics(
      label: 'Daily spend histogram, ${widget.bars.length} days',
      child: SizedBox(
        height: widget.height,
        child: AnimatedBuilder(
          animation: _anim,
          builder: (context, _) => CustomPaint(
            size: Size.infinite,
            painter: _HistogramPainter(
              bars: widget.bars,
              progress: _anim.value,
            ),
          ),
        ),
      ),
    );
  }
}

class DaySpendBar {
  final DateTime day;
  final double value;

  const DaySpendBar({required this.day, required this.value});

  @override
  bool operator ==(Object other) =>
      other is DaySpendBar && other.day == day && other.value == value;

  @override
  int get hashCode => Object.hash(day, value);
}

class _HistogramPainter extends CustomPainter {
  final List<DaySpendBar> bars;
  final double progress;

  _HistogramPainter({required this.bars, required this.progress});

  @override
  void paint(Canvas canvas, Size size) {
    if (bars.isEmpty) return;
    final maxVal = bars.map((b) => b.value).fold(0.0, math.max);
    if (maxVal <= 0) return;

    final slot = size.width / bars.length;
    final barWidth = math.min(slot * 0.62, 18.0);

    final paint = Paint()..style = PaintingStyle.fill;
    final glowPaint = Paint()
      ..style = PaintingStyle.fill
      ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 6);

    for (var i = 0; i < bars.length; i++) {
      final h =
          (bars[i].value / maxVal) *
          (size.height - 22) *
          Curves.easeOutCubic.transform(progress);
      final x = i * slot + (slot - barWidth) / 2;
      final rect = RRect.fromRectAndCorners(
        Rect.fromLTWH(x, size.height - 18 - h, barWidth, h),
        topLeft: const Radius.circular(6),
        topRight: const Radius.circular(6),
      );
      paint.shader = LinearGradient(
        begin: Alignment.topCenter,
        end: Alignment.bottomCenter,
        colors: [
          FluxTheme.accent.withValues(alpha: 0.95),
          FluxTheme.accent2.withValues(alpha: 0.35),
        ],
      ).createShader(rect.outerRect);
      glowPaint.color = FluxTheme.accent.withValues(alpha: 0.25);
      canvas.drawRRect(rect, glowPaint);
      canvas.drawRRect(rect, paint);
    }
  }

  @override
  bool shouldRepaint(_HistogramPainter oldDelegate) =>
      oldDelegate.progress != progress || !listEquals(oldDelegate.bars, bars);
}

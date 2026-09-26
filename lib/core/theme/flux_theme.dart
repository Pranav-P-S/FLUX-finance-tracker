import 'dart:ui' show ImageFilter;

import 'package:flutter/material.dart';

/// Flux visual language: dark glassmorphic base with neumorphic controls,
/// cyan→violet accents, constant across all four screens.
abstract final class FluxTheme {
  static const bg = Color(0xFF0B0F1A);
  static const bgDeep = Color(0xFF070A12);
  static const surface = Color(0xFF121828);
  static const accent = Color(0xFF22D3EE);
  static const accent2 = Color(0xFFA78BFA);
  static const credit = Color(0xFF34D399);
  static const debt = Color(0xFFF87171);
  static const ink = Color(0xFFE7ECF5);
  static const inkDim = Color(0xFF8A94AB);

  static const accentGradient = LinearGradient(
    colors: [accent, accent2],
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
  );

  static ThemeData dark() {
    final base = ThemeData.dark(useMaterial3: true);
    return base.copyWith(
      scaffoldBackgroundColor: bg,
      colorScheme: base.colorScheme.copyWith(
        primary: accent,
        secondary: accent2,
        surface: surface,
        error: debt,
      ),
      textTheme: base.textTheme.apply(
        bodyColor: ink,
        displayColor: ink,
        fontFamily: 'sans-serif',
      ),
      snackBarTheme: SnackBarThemeData(
        backgroundColor: surface,
        contentTextStyle: const TextStyle(color: ink),
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
      ),
      navigationBarTheme: NavigationBarThemeData(
        backgroundColor: Colors.transparent,
        indicatorColor: Colors.white.withValues(alpha: 0.08),
        labelTextStyle: const WidgetStatePropertyAll(
          TextStyle(fontSize: 11, color: inkDim),
        ),
      ),
    );
  }
}

/// Frosted-glass container — the backbone of the glassmorphic look.
class GlassCard extends StatelessWidget {
  final Widget child;
  final EdgeInsetsGeometry padding;
  final EdgeInsetsGeometry? margin;
  final double radius;
  final double blur;

  const GlassCard({
    super.key,
    required this.child,
    this.padding = const EdgeInsets.all(18),
    this.margin,
    this.radius = 22,
    this.blur = 16,
  });

  @override
  Widget build(BuildContext context) {
    final content = Container(
      padding: padding,
      decoration: BoxDecoration(
        gradient: LinearGradient(
          colors: [
            Colors.white.withValues(alpha: 0.10),
            Colors.white.withValues(alpha: 0.04),
          ],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        borderRadius: BorderRadius.circular(radius),
        border: Border.all(color: Colors.white.withValues(alpha: 0.14)),
      ),
      child: child,
    );
    return Container(
      margin: margin,
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(radius),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.35),
            blurRadius: 24,
            offset: const Offset(0, 10),
          ),
        ],
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(radius),
        child: blur == 0
            ? content
            : BackdropFilter(
                filter: ImageFilter.blur(sigmaX: blur, sigmaY: blur),
                child: content,
              ),
      ),
    );
  }
}

/// Soft-extruded (neumorphic) pressable control on the dark base.
class NeuButton extends StatelessWidget {
  final Widget child;
  final VoidCallback? onTap;
  final bool pressed;
  final double radius;
  final EdgeInsetsGeometry padding;

  const NeuButton({
    super.key,
    required this.child,
    this.onTap,
    this.pressed = false,
    this.radius = 18,
    this.padding = const EdgeInsets.symmetric(horizontal: 18, vertical: 12),
  });

  @override
  Widget build(BuildContext context) {
    return AnimatedContainer(
      duration: const Duration(milliseconds: 160),
      curve: Curves.easeOut,
      decoration: BoxDecoration(
        color: FluxTheme.surface,
        borderRadius: BorderRadius.circular(radius),
        gradient: pressed
            ? null
            : const LinearGradient(
                colors: [Color(0xFF182034), Color(0xFF101626)],
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
              ),
        boxShadow: pressed
            ? [
                BoxShadow(
                  color: Colors.black.withValues(alpha: 0.5),
                  blurRadius: 8,
                  offset: const Offset(2, 2),
                ),
              ]
            : [
                BoxShadow(
                  color: Colors.white.withValues(alpha: 0.05),
                  blurRadius: 10,
                  offset: const Offset(-3, -3),
                ),
                BoxShadow(
                  color: Colors.black.withValues(alpha: 0.55),
                  blurRadius: 12,
                  offset: const Offset(4, 4),
                ),
              ],
      ),
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(radius),
          child: Padding(padding: padding, child: child),
        ),
      ),
    );
  }
}

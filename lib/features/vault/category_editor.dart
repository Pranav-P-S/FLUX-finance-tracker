import 'package:flutter/material.dart';

import '../../core/theme/flux_theme.dart';

/// Bottom-sheet editor for creating a category: label + accent color.
class CategoryEditor extends StatefulWidget {
  final Future<void> Function(String label, int color) onSubmit;

  const CategoryEditor({super.key, required this.onSubmit});

  @override
  State<CategoryEditor> createState() => _CategoryEditorState();
}

class _CategoryEditorState extends State<CategoryEditor> {
  final _controller = TextEditingController();
  int _color = 0xFF22D3EE;

  static const _palette = [
    0xFF22D3EE,
    0xFFA78BFA,
    0xFFF97316,
    0xFF34D399,
    0xFFF472B6,
    0xFFFACC15,
    0xFF60A5FA,
    0xFF94A3B8,
  ];

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.only(
        left: 22,
        right: 22,
        top: 22,
        bottom: MediaQuery.of(context).viewInsets.bottom + 22,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            'New category',
            style: TextStyle(fontSize: 17, fontWeight: FontWeight.w800),
          ),
          const SizedBox(height: 16),
          TextField(
            controller: _controller,
            autofocus: true,
            style: const TextStyle(color: FluxTheme.ink),
            decoration: InputDecoration(
              hintText: 'e.g. Subscriptions',
              hintStyle: const TextStyle(color: FluxTheme.inkDim),
              filled: true,
              fillColor: Colors.white.withValues(alpha: 0.05),
              border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(14),
                borderSide: BorderSide.none,
              ),
            ),
          ),
          const SizedBox(height: 14),
          Wrap(
            spacing: 10,
            children: [
              for (final c in _palette)
                GestureDetector(
                  onTap: () => setState(() => _color = c),
                  child: Container(
                    width: 30,
                    height: 30,
                    decoration: BoxDecoration(
                      color: Color(c),
                      shape: BoxShape.circle,
                      border: Border.all(
                        color: _color == c ? Colors.white : Colors.transparent,
                        width: 2,
                      ),
                      boxShadow: [
                        BoxShadow(
                          color: Color(c).withValues(alpha: 0.5),
                          blurRadius: 8,
                        ),
                      ],
                    ),
                  ),
                ),
            ],
          ),
          const SizedBox(height: 20),
          SizedBox(
            width: double.infinity,
            child: NeuButton(
              onTap: () async {
                final label = _controller.text.trim();
                if (label.isEmpty) return;
                Navigator.of(context).pop();
                await widget.onSubmit(label, _color);
              },
              child: const Text(
                'Create',
                textAlign: TextAlign.center,
                style: TextStyle(
                  color: FluxTheme.accent,
                  fontWeight: FontWeight.w800,
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

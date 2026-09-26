import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/theme/flux_theme.dart';
import '../../models/flux_models.dart';
import '../../providers/providers.dart';
import 'category_editor.dart';

/// Capture-engine status, category management, biometric lock and the JSON
/// Data Hub.
class VaultScreen extends ConsumerWidget {
  const VaultScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final categories = ref.watch(categoriesProvider);
    final biometric = ref.watch(biometricProvider);
    final baseCurrency = ref.watch(baseCurrencyProvider);
    final capture = ref.watch(captureStatusProvider);

    return ListView(
      padding: const EdgeInsets.fromLTRB(20, 12, 20, 170),
      children: [
        const Text(
          'Vault',
          style: TextStyle(fontSize: 24, fontWeight: FontWeight.w800),
        ),
        const SizedBox(height: 18),
        _Section('Capture engine'),
        GlassCard(
          child: Column(
            children: [
              _StatusRow(
                icon: Icons.notifications_active_rounded,
                title: 'Notification access',
                subtitle: 'Reads bank alerts to extract transactions',
                ok: capture.value?.notificationAccess ?? false,
                actionLabel: capture.value?.notificationAccess == true
                    ? null
                    : 'Enable',
                onAction: () =>
                    ref.read(bridgeProvider).openNotificationAccessSettings(),
              ),
              const Divider(height: 24, color: Colors.white12),
              _StatusRow(
                icon: Icons.battery_saver_rounded,
                title: 'Battery optimization exempt',
                subtitle: 'Keeps background capture alive',
                ok: capture.value?.batteryOptimized ?? false,
                actionLabel: capture.value?.batteryOptimized == true
                    ? null
                    : 'Request',
                onAction: () async {
                  await ref
                      .read(bridgeProvider)
                      .requestIgnoreBatteryOptimizations();
                  ref.read(captureStatusProvider.notifier).refresh();
                },
              ),
            ],
          ),
        ),
        const SizedBox(height: 18),
        _Section('Categories'),
        categories.when(
          loading: () => const Center(
            child: CircularProgressIndicator(color: FluxTheme.accent),
          ),
          error: (e, _) => Text(
            'Unavailable: $e',
            style: const TextStyle(color: FluxTheme.debt),
          ),
          data: (list) => GlassCard(
            child: Column(
              children: [
                for (final c in list)
                  _CategoryRow(
                    category: c,
                    onDelete: c.isDefault
                        ? null
                        : () async {
                            await ref
                                .read(categoriesProvider.notifier)
                                .remove(c.id);
                          },
                  ),
                const SizedBox(height: 8),
                NeuButton(
                  onTap: () => _openEditor(context, ref),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: const [
                      Icon(
                        Icons.add_rounded,
                        color: FluxTheme.accent,
                        size: 18,
                      ),
                      SizedBox(width: 6),
                      Text(
                        'New category',
                        style: TextStyle(
                          color: FluxTheme.accent,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 18),
        _Section('Preferences'),
        GlassCard(
          child: ListTile(
            contentPadding: EdgeInsets.zero,
            title: const Text(
              'Base currency',
              style: TextStyle(fontWeight: FontWeight.w600),
            ),
            subtitle: Text(
              'Alerts in other currencies wait in the Inbox',
              style: const TextStyle(color: FluxTheme.inkDim, fontSize: 12),
            ),
            trailing: baseCurrency.when(
              loading: () => const SizedBox(
                width: 18,
                height: 18,
                child: CircularProgressIndicator(strokeWidth: 2),
              ),
              error: (e, _) =>
                  const Text('INR', style: TextStyle(color: FluxTheme.inkDim)),
              data: (currency) => Text(
                currency,
                style: const TextStyle(
                  color: FluxTheme.accent,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ),
            onTap: () => _pickBaseCurrency(context, ref),
          ),
        ),
        const SizedBox(height: 18),
        _Section('Security'),
        GlassCard(
          child: SwitchListTile(
            contentPadding: EdgeInsets.zero,
            value: biometric.value ?? false,
            activeThumbColor: FluxTheme.accent,
            title: const Text(
              'Biometric unlock',
              style: TextStyle(fontWeight: FontWeight.w600),
            ),
            subtitle: const Text(
              'Require fingerprint/face on launch',
              style: TextStyle(color: FluxTheme.inkDim, fontSize: 12),
            ),
            onChanged: (v) =>
                ref.read(biometricProvider.notifier).setEnabled(v),
          ),
        ),
        const SizedBox(height: 18),
        _Section('JSON Data Hub'),
        GlassCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text(
                'Export or restore the entire Flux state — categories, settings and '
                'every transaction — as a portable JSON archive.',
                style: TextStyle(
                  color: FluxTheme.inkDim,
                  fontSize: 12,
                  height: 1.4,
                ),
              ),
              const SizedBox(height: 14),
              Row(
                children: [
                  Expanded(
                    child: NeuButton(
                      onTap: () => _export(context, ref),
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: const [
                          Icon(
                            Icons.ios_share_rounded,
                            size: 16,
                            color: FluxTheme.accent,
                          ),
                          SizedBox(width: 8),
                          Text(
                            'Export',
                            style: TextStyle(
                              color: FluxTheme.accent,
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
                      onTap: () => _import(context, ref),
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: const [
                          Icon(
                            Icons.download_rounded,
                            size: 16,
                            color: FluxTheme.accent2,
                          ),
                          SizedBox(width: 8),
                          Text(
                            'Import',
                            style: TextStyle(
                              color: FluxTheme.accent2,
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
      ],
    );
  }

  Future<void> _pickBaseCurrency(BuildContext context, WidgetRef ref) async {
    final selected = await showDialog<String>(
      context: context,
      builder: (dialogContext) => SimpleDialog(
        backgroundColor: FluxTheme.surface,
        title: const Text('Base currency', style: TextStyle(fontSize: 17)),
        children: [
          for (final currency in const ['INR', 'USD', 'EUR', 'GBP'])
            SimpleDialogOption(
              onPressed: () => Navigator.of(dialogContext).pop(currency),
              child: Text(currency),
            ),
        ],
      ),
    );
    if (selected != null) {
      await ref.read(baseCurrencyProvider.notifier).set(selected);
    }
  }

  void _openEditor(BuildContext context, WidgetRef ref) {
    showModalBottomSheet<void>(
      context: context,
      backgroundColor: FluxTheme.surface,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      builder: (_) => CategoryEditor(
        onSubmit: (label, color) async {
          await ref.read(categoriesProvider.notifier).add(label, color);
        },
      ),
    );
  }

  Future<void> _export(BuildContext context, WidgetRef ref) async {
    final messenger = ScaffoldMessenger.of(context);
    try {
      final path = await ref.read(bridgeProvider).exportState();
      messenger.showSnackBar(SnackBar(content: Text('Exported to $path')));
    } catch (e) {
      messenger.showSnackBar(SnackBar(content: Text('Export failed: $e')));
    }
  }

  Future<void> _import(BuildContext context, WidgetRef ref) async {
    final messenger = ScaffoldMessenger.of(context);
    try {
      final picked = await FilePicker.pickFiles(type: FileType.any);
      final path = picked.single.path;
      if (path == null) return;
      // The archive is streamed straight from disk on the native side; the
      // file content never passes through Dart memory.
      final imported = await ref.read(bridgeProvider).importState(path);
      messenger.showSnackBar(
        SnackBar(content: Text('Imported $imported transactions')),
      );
      await ref.read(categoriesProvider.notifier).refresh();
    } catch (e) {
      messenger.showSnackBar(SnackBar(content: Text('Import failed: $e')));
    }
  }
}

class _Section extends StatelessWidget {
  final String text;
  const _Section(this.text);

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(bottom: 10),
    child: Text(
      text,
      style: const TextStyle(
        fontSize: 13,
        fontWeight: FontWeight.w700,
        letterSpacing: 0.4,
      ),
    ),
  );
}

class _StatusRow extends StatelessWidget {
  final IconData icon;
  final String title;
  final String subtitle;
  final bool ok;
  final String? actionLabel;
  final Future<void> Function() onAction;

  const _StatusRow({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.ok,
    required this.actionLabel,
    required this.onAction,
  });

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Icon(icon, color: ok ? FluxTheme.credit : FluxTheme.accent, size: 22),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                title,
                style: const TextStyle(
                  fontWeight: FontWeight.w600,
                  fontSize: 14,
                ),
              ),
              Text(
                subtitle,
                style: const TextStyle(color: FluxTheme.inkDim, fontSize: 11),
              ),
            ],
          ),
        ),
        if (ok)
          const Icon(Icons.check_circle_rounded, color: FluxTheme.credit)
        else
          NeuButton(
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
            onTap: onAction,
            child: Text(
              actionLabel ?? 'Enable',
              style: const TextStyle(
                color: FluxTheme.accent,
                fontWeight: FontWeight.w700,
                fontSize: 12,
              ),
            ),
          ),
      ],
    );
  }
}

class _CategoryRow extends StatelessWidget {
  final FluxCategory category;
  final VoidCallback? onDelete;

  const _CategoryRow({required this.category, this.onDelete});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        children: [
          Container(
            width: 12,
            height: 12,
            decoration: BoxDecoration(
              color: category.color,
              borderRadius: BorderRadius.circular(4),
              boxShadow: [
                BoxShadow(
                  color: category.color.withValues(alpha: 0.55),
                  blurRadius: 6,
                ),
              ],
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Text(category.label, style: const TextStyle(fontSize: 14)),
          ),
          if (category.isDefault)
            const Text(
              'default',
              style: TextStyle(color: FluxTheme.inkDim, fontSize: 10),
            )
          else
            GestureDetector(
              onTap: onDelete,
              child: const Icon(
                Icons.delete_outline_rounded,
                color: FluxTheme.debt,
                size: 20,
              ),
            ),
        ],
      ),
    );
  }
}

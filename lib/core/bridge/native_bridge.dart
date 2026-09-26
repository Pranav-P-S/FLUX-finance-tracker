import 'dart:async';

import 'package:flutter/foundation.dart' show kDebugMode;
import 'package:flutter/services.dart';

import '../../models/flux_models.dart';

/// Contract used by every provider. The Android build binds [NativeBridge]
/// (MethodChannel); web/demo builds bind a [DemoBridge] with static data.
abstract class FluxBridge {
  Stream<void> get onTransactionsChanged;

  Future<PulseSummary> pulseSummary();
  Future<List<Transaction>> transactionsPage({int page, int pageSize});
  Future<List<Transaction>> inbox({int limit});
  Future<void> categorize(int id, String categoryId);
  Future<void> deleteTransaction(int id);
  Future<List<CategorySpend>> spendingByCategory(int startMs, int endMs);
  Future<List<DaySpend>> dailySpend(int startMs, int endMs);
  Future<List<FluxCategory>> categories();
  Future<void> addCategory({
    required String label,
    required int color,
    String icon,
  });
  Future<void> deleteCategory(String id);
  Future<bool> notificationAccessGranted();
  Future<void> openNotificationAccessSettings();
  Future<bool> ignoringBatteryOptimizations();
  Future<void> requestIgnoreBatteryOptimizations();
  Future<String> exportState();
  Future<int> importState(String path);

  /// Display currency for dashboards; foreign-currency alerts queue in the Inbox.
  Future<String> baseCurrency();
  Future<void> setBaseCurrency(String currency);
  Future<bool> biometricEnabled();
  Future<void> setBiometricEnabled(bool enabled);
  Future<String> simulateNotification(String text, {String packageName});
}

/// Typed client for the `flux.native_bridge` MethodChannel. Lists are pulled
/// in pages of 50 so a growing history never blocks the platform channel.
class NativeBridge implements FluxBridge {
  static const _channel = MethodChannel('flux.native_bridge');

  final _changesController = StreamController<void>.broadcast();

  NativeBridge() {
    _channel.setMethodCallHandler((call) async {
      if (call.method == 'onTransactionsChanged') {
        _changesController.add(null);
      }
      return null;
    });
  }

  @override
  Stream<void> get onTransactionsChanged => _changesController.stream;

  Future<T> _invoke<T>(
    String method, [
    Map<String, Object?> args = const {},
  ]) async {
    return (await _channel.invokeMethod<T>(method, args)) as T;
  }

  Future<Map<Object?, Object?>> _invokeMap(
    String method, [
    Map<String, Object?> args = const {},
  ]) async {
    return (await _channel.invokeMethod<Object?>(method, args)
            as Map<Object?, Object?>?) ??
        {};
  }

  Future<List<Object?>> _invokeList(
    String method, [
    Map<String, Object?> args = const {},
  ]) async {
    return (await _channel.invokeMethod<Object?>(method, args)
            as List<Object?>?) ??
        const [];
  }

  /// Per-row defensive decoding: one malformed element must reject only that
  /// row, never the entire payload (and never the calling screen).
  static List<Transaction> _parseTransactions(List<Object?> items) => [
    for (final e in items)
      if (e is Map) Transaction.fromMap(Map<Object?, Object?>.from(e)),
  ];

  static List<FluxCategory> _parseCategories(List<Object?> items) => [
    for (final e in items)
      if (e is Map) FluxCategory.fromMap(Map<Object?, Object?>.from(e)),
  ];

  static List<CategorySpend> _parseCategorySpend(List<Object?> items) => [
    for (final e in items)
      if (e is Map) CategorySpend.fromMap(Map<Object?, Object?>.from(e)),
  ];

  static List<DaySpend> _parseDaySpend(List<Object?> items) => [
    for (final e in items)
      if (e is Map) DaySpend.fromMap(Map<Object?, Object?>.from(e)),
  ];

  @override
  Future<PulseSummary> pulseSummary() async =>
      PulseSummary.fromMap(await _invokeMap('getPulseSummary'));

  @override
  Future<List<Transaction>> transactionsPage({
    int page = 0,
    int pageSize = 50,
  }) async {
    final res = await _invokeMap('getTransactionsPage', {
      'page': page,
      'pageSize': pageSize,
    });
    return _parseTransactions((res['items'] as List<Object?>?) ?? const []);
  }

  @override
  Future<List<Transaction>> inbox({int limit = 100}) async =>
      _parseTransactions(await _invokeList('getInbox', {'limit': limit}));

  @override
  Future<void> categorize(int id, String categoryId) => _invoke<dynamic>(
    'categorizeTransaction',
    {'id': id, 'categoryId': categoryId},
  );

  @override
  Future<void> deleteTransaction(int id) =>
      _invoke<dynamic>('deleteTransaction', {'id': id});

  @override
  Future<List<CategorySpend>> spendingByCategory(
    int startMs,
    int endMs,
  ) async => _parseCategorySpend(
    await _invokeList('getSpendingByCategory', {
      'startMs': startMs,
      'endMs': endMs,
    }),
  );

  @override
  Future<List<DaySpend>> dailySpend(int startMs, int endMs) async =>
      _parseDaySpend(
        await _invokeList('getDailySpend', {
          'startMs': startMs,
          'endMs': endMs,
        }),
      );

  @override
  Future<List<FluxCategory>> categories() async =>
      _parseCategories(await _invokeList('getCategories'));

  @override
  Future<void> addCategory({
    required String label,
    required int color,
    String icon = 'category',
  }) => _invoke<dynamic>('addCategory', {
    'label': label,
    'color': color,
    'icon': icon,
  });

  @override
  Future<void> deleteCategory(String id) =>
      _invoke<dynamic>('deleteCategory', {'id': id});

  @override
  Future<bool> notificationAccessGranted() async =>
      (await _invokeMap('isNotificationAccessGranted'))['granted'] as bool? ??
      false;

  @override
  Future<void> openNotificationAccessSettings() =>
      _invoke<dynamic>('openNotificationAccessSettings');

  @override
  Future<bool> ignoringBatteryOptimizations() async =>
      (await _invokeMap('isIgnoringBatteryOptimizations'))['ignoring']
          as bool? ??
      false;

  @override
  Future<void> requestIgnoreBatteryOptimizations() =>
      _invoke<dynamic>('requestIgnoreBatteryOptimizations');

  @override
  Future<String> exportState() async =>
      (await _invokeMap('exportState'))['path'] as String? ?? '';

  @override
  Future<int> importState(String path) async =>
      (await _invokeMap('importState', {'path': path}))['imported'] as int? ??
      0;

  @override
  Future<String> baseCurrency() async =>
      (await _invokeMap('getBaseCurrency'))['currency'] as String? ?? 'INR';

  @override
  Future<void> setBaseCurrency(String currency) =>
      _invoke<dynamic>('setBaseCurrency', {'currency': currency});

  @override
  Future<bool> biometricEnabled() => _invoke<bool>('getBiometricEnabled');

  @override
  Future<void> setBiometricEnabled(bool enabled) =>
      _invoke<dynamic>('setBiometricEnabled', {'enabled': enabled});

  /// Debug/demo injector: feeds a synthetic notification through the real
  /// pipeline. Returns the pipeline verdict ('stored'/'duplicate'/...).
  /// Release builds reject it on both sides of the channel.
  @override
  Future<String> simulateNotification(
    String text, {
    String packageName = 'flux.simulator',
  }) async {
    if (!kDebugMode) {
      throw UnsupportedError('simulateNotification is debug-only');
    }
    final res = await _invokeMap('simulateNotification', {
      'text': text,
      'packageName': packageName,
    });
    return res['status'] as String? ?? 'unparsed';
  }
}

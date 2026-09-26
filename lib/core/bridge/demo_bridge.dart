import 'dart:async';

import 'demo_data.dart';
import 'native_bridge.dart';
import '../../models/flux_models.dart';

/// In-memory [FluxBridge] that powers demo mode on web: no platform channel,
/// a fixed dataset from [DemoData], and best-effort no-op writes.
class DemoBridge implements FluxBridge {
  final _never = const Stream<void>.empty();

  @override
  Stream<void> get onTransactionsChanged => _never;

  @override
  Future<PulseSummary> pulseSummary() async => DemoData.summary;

  @override
  Future<List<Transaction>> transactionsPage({
    int page = 0,
    int pageSize = 50,
  }) async {
    // Mirror the native contract: newest first.
    final sorted = [...DemoData.transactions]
      ..sort((a, b) => b.timestamp.compareTo(a.timestamp));
    return sorted.skip(page * pageSize).take(pageSize).toList();
  }

  @override
  Future<List<Transaction>> inbox({int limit = 100}) async =>
      DemoData.transactions.where((t) => t.needsReview).take(limit).toList();

  @override
  Future<void> categorize(int id, String categoryId) async {}

  @override
  Future<void> deleteTransaction(int id) async {}

  @override
  Future<List<CategorySpend>> spendingByCategory(
    int startMs,
    int endMs,
  ) async => DemoData.byCategory;

  @override
  Future<List<DaySpend>> dailySpend(int startMs, int endMs) async =>
      DemoData.byDay;

  @override
  Future<List<FluxCategory>> categories() async => DemoData.categories;

  @override
  Future<void> addCategory({
    required String label,
    required int color,
    String icon = 'category',
  }) async {}

  @override
  Future<void> deleteCategory(String id) async {}

  @override
  Future<bool> notificationAccessGranted() async => true;

  @override
  Future<void> openNotificationAccessSettings() async {}

  @override
  Future<bool> ignoringBatteryOptimizations() async => true;

  @override
  Future<void> requestIgnoreBatteryOptimizations() async {}

  @override
  Future<String> exportState() async => '';

  @override
  Future<int> importState(String path) async => 0;

  @override
  Future<String> baseCurrency() async => 'INR';

  @override
  Future<void> setBaseCurrency(String currency) async {}

  @override
  Future<bool> biometricEnabled() async => false;

  @override
  Future<void> setBiometricEnabled(bool enabled) async {}

  @override
  Future<String> simulateNotification(
    String text, {
    String packageName = 'flux.simulator',
  }) async => 'stored';
}

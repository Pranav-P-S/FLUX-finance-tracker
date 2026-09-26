import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/bridge/demo_bridge.dart';
import '../core/bridge/native_bridge.dart';
import '../models/flux_models.dart';

final bridgeProvider = Provider<FluxBridge>(
  (ref) => kIsWeb ? DemoBridge() : NativeBridge(),
);

/// Emits whenever the native store mutates (listener ingested something,
/// manual triage, import). Features refresh off this.
final dataChangesProvider = StreamProvider<void>(
  (ref) => ref.watch(bridgeProvider).onTransactionsChanged,
);

class PulseController extends AsyncNotifier<PulseSummary> {
  @override
  Future<PulseSummary> build() => ref.watch(bridgeProvider).pulseSummary();

  /// Re-fetches without dropping the current data, so background refreshes
  /// never flash a loading state over populated screens.
  Future<void> refresh() async {
    state = await AsyncValue.guard(
      () => ref.read(bridgeProvider).pulseSummary(),
    );
  }
}

final pulseProvider = AsyncNotifierProvider<PulseController, PulseSummary>(
  PulseController.new,
);

class RecentTransactionsController extends AsyncNotifier<List<Transaction>> {
  /// The Pulse only surfaces the newest tiles; dashboards read the SQL
  /// aggregates instead, so pulling a single page keeps this cheap.
  @override
  Future<List<Transaction>> build() =>
      ref.watch(bridgeProvider).transactionsPage(page: 0, pageSize: 50);

  Future<void> refresh() async {
    state = await AsyncValue.guard(
      () => ref.read(bridgeProvider).transactionsPage(page: 0, pageSize: 50),
    );
  }
}

final recentTransactionsProvider =
    AsyncNotifierProvider<RecentTransactionsController, List<Transaction>>(
      RecentTransactionsController.new,
    );

class InboxController extends AsyncNotifier<List<Transaction>> {
  @override
  Future<List<Transaction>> build() => ref.watch(bridgeProvider).inbox();

  Future<void> approve(Transaction tx, String categoryId) async {
    await ref.read(bridgeProvider).categorize(tx.id, categoryId);
    await refresh();
  }

  Future<void> discard(Transaction tx) async {
    await ref.read(bridgeProvider).deleteTransaction(tx.id);
    await refresh();
  }

  Future<void> refresh() async {
    state = await AsyncValue.guard(() => ref.read(bridgeProvider).inbox());
  }
}

final inboxProvider = AsyncNotifierProvider<InboxController, List<Transaction>>(
  InboxController.new,
);

class CategoriesController extends AsyncNotifier<List<FluxCategory>> {
  @override
  Future<List<FluxCategory>> build() => ref.watch(bridgeProvider).categories();

  Future<void> add(String label, int color) async {
    await ref.read(bridgeProvider).addCategory(label: label, color: color);
    await refresh();
  }

  Future<void> remove(String id) async {
    await ref.read(bridgeProvider).deleteCategory(id);
    await refresh();
  }

  Future<void> refresh() async {
    state = await AsyncValue.guard(() => ref.read(bridgeProvider).categories());
  }
}

final categoriesProvider =
    AsyncNotifierProvider<CategoriesController, List<FluxCategory>>(
      CategoriesController.new,
    );

class LensData {
  final List<CategorySpend> byCategory;
  final List<DaySpend> byDay;
  const LensData({required this.byCategory, required this.byDay});
}

class LensController extends AsyncNotifier<LensData> {
  final DateTime month;
  LensController(this.month);

  @override
  Future<LensData> build() async {
    final start = DateTime(month.year, month.month, 1);
    final end = DateTime(month.year, month.month + 1, 1);
    final bridge = ref.watch(bridgeProvider);
    final byCategory = await bridge.spendingByCategory(
      start.millisecondsSinceEpoch,
      end.millisecondsSinceEpoch,
    );
    final byDay = await bridge.dailySpend(
      start.millisecondsSinceEpoch,
      end.millisecondsSinceEpoch,
    );
    return LensData(byCategory: byCategory, byDay: byDay);
  }
}

final lensProvider =
    AsyncNotifierProvider.family<LensController, LensData, DateTime>(
      LensController.new,
    );

class CaptureStatus {
  final bool notificationAccess;
  final bool batteryOptimized;
  const CaptureStatus({
    required this.notificationAccess,
    required this.batteryOptimized,
  });
}

class CaptureStatusController extends AsyncNotifier<CaptureStatus> {
  @override
  Future<CaptureStatus> build() async {
    final bridge = ref.watch(bridgeProvider);
    return CaptureStatus(
      notificationAccess: await bridge.notificationAccessGranted(),
      batteryOptimized: await bridge.ignoringBatteryOptimizations(),
    );
  }

  Future<void> refresh() async {
    state = await AsyncValue.guard(() async {
      final bridge = ref.read(bridgeProvider);
      return CaptureStatus(
        notificationAccess: await bridge.notificationAccessGranted(),
        batteryOptimized: await bridge.ignoringBatteryOptimizations(),
      );
    });
  }
}

final captureStatusProvider =
    AsyncNotifierProvider<CaptureStatusController, CaptureStatus>(
      CaptureStatusController.new,
    );

class VaultController extends AsyncNotifier<bool> {
  @override
  Future<bool> build() => ref.watch(bridgeProvider).biometricEnabled();

  Future<void> setEnabled(bool enabled) async {
    await ref.read(bridgeProvider).setBiometricEnabled(enabled);
    state = AsyncData(enabled);
  }
}

final biometricProvider = AsyncNotifierProvider<VaultController, bool>(
  VaultController.new,
);

class BaseCurrencyController extends AsyncNotifier<String> {
  @override
  Future<String> build() => ref.watch(bridgeProvider).baseCurrency();

  Future<void> set(String currency) async {
    await ref.read(bridgeProvider).setBaseCurrency(currency);
    state = AsyncData(currency);
  }
}

final baseCurrencyProvider =
    AsyncNotifierProvider<BaseCurrencyController, String>(
      BaseCurrencyController.new,
    );

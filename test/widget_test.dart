import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:flux/features/pulse/pulse_screen.dart';
import 'package:flux/models/flux_models.dart';
import 'package:flux/providers/providers.dart';
import 'package:flux/widgets/tx_tile.dart';

Transaction _tx({
  int id = 1,
  double amount = -450,
  String merchant = 'SWIGGY',
  String category = 'food_drink',
  bool needsReview = false,
}) => Transaction(
  id: id,
  amount: amount,
  currency: 'INR',
  merchant: merchant,
  accountHint: 'XX1234',
  timestamp: DateTime(2026, 9, 20, 13, 45).millisecondsSinceEpoch,
  category: category,
  categoryConfidence: 0.55,
  needsReview: needsReview,
  parseMethod: 'generic_debit',
  sourcePackage: 'com.bank.app',
  rawText: 'Rs 450 debited to SWIGGY',
);

void main() {
  test('formatMoney groups thousands and abbreviates INR lakhs', () {
    expect(formatMoney(-450), '-₹450');
    expect(formatMoney(125000), '₹1.25L');
    expect(formatMoney(1500000), '₹15.0L');
    expect(formatMoney(65000), '₹65,000');
    expect(formatMoney(12.5, currency: 'USD'), r'$12.50');
  });

  testWidgets('TxTile renders merchant, sign-aware amount and category', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: TxTile(tx: _tx(), categoryLabel: 'Food & Drink'),
        ),
      ),
    );
    expect(find.text('SWIGGY'), findsOneWidget);
    expect(find.text('-₹450'), findsOneWidget);
    expect(find.text('Food & Drink'), findsOneWidget);
    // Debit rows use the red sign convention, not green.
    final amount = tester.widget<Text>(find.text('-₹450'));
    expect(amount.style?.color, isNot(const Color(0xFF34D399)));
  });

  testWidgets('Credit transactions render with positive amount', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: TxTile(
            tx: _tx(amount: 65000, merchant: 'SALARY'),
            categoryLabel: 'Income',
          ),
        ),
      ),
    );
    expect(find.text('₹65,000'), findsOneWidget);
  });

  testWidgets('Pulse shows summary and recent activity from the bridge', (
    tester,
  ) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          pulseProvider.overrideWith(_FakePulse.new),
          transactionsProvider.overrideWith(_FakeTransactions.new),
          categoriesProvider.overrideWith(_FakeCategories.new),
          captureStatusProvider.overrideWith(_FakeCapture.new),
        ],
        child: const MaterialApp(home: Scaffold(body: PulseScreen())),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Net balance'), findsOneWidget);
    expect(find.text('SWIGGY'), findsOneWidget);
    expect(find.text('Food & Drink'), findsWidgets);
  });
}

class _FakePulse extends PulseController {
  @override
  Future<PulseSummary> build() async => const PulseSummary(
    netBalance: 60550,
    totalCredit: 65000,
    totalDebit: 4450,
    txCount: 1,
    pendingReview: 0,
  );
}

class _FakeTransactions extends TransactionsController {
  @override
  Future<List<Transaction>> build() async => [_tx()];
}

class _FakeCategories extends CategoriesController {
  @override
  Future<List<FluxCategory>> build() async => const [
    FluxCategory(
      id: 'food_drink',
      label: 'Food & Drink',
      color: Color(0xFFF97316),
      icon: 'restaurant',
      keywords: ['swiggy'],
      isDefault: true,
    ),
    FluxCategory(
      id: 'uncategorized',
      label: 'Uncategorized',
      color: Color(0xFF64748B),
      icon: 'category',
      keywords: [],
      isDefault: true,
    ),
  ];
}

class _FakeCapture extends CaptureStatusController {
  @override
  Future<CaptureStatus> build() async =>
      const CaptureStatus(notificationAccess: true, batteryOptimized: true);
}

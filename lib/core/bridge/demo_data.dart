import 'package:flutter/material.dart';

import '../../models/flux_models.dart';

/// Static dataset shown in demo mode.
abstract final class DemoData {
  static const categoryColors = {
    'food_drink': Color(0xFFF97316),
    'transport': Color(0xFF38BDF8),
    'shopping': Color(0xFFA78BFA),
    'bills_utilities': Color(0xFFFACC15),
    'entertainment': Color(0xFFF472B6),
    'travel': Color(0xFF60A5FA),
    'income': Color(0xFF2DD4BF),
  };

  static const categoryLabels = {
    'food_drink': 'Food & Drink',
    'transport': 'Transport',
    'shopping': 'Shopping',
    'bills_utilities': 'Bills & Utilities',
    'entertainment': 'Entertainment',
    'travel': 'Travel',
    'income': 'Income',
  };

  static final List<FluxCategory> categories = [
    for (final e in categoryLabels.entries)
      FluxCategory(
        id: e.key,
        label: e.value,
        color: categoryColors[e.key]!,
        icon: 'category',
        keywords: const [],
        isDefault: true,
      ),
  ];

  static Transaction _tx({
    required int id,
    required double amount,
    required String merchant,
    required String category,
    required int day,
    required int hour,
    bool needsReview = false,
    String raw = '',
  }) => Transaction(
    id: id,
    amount: amount,
    currency: 'INR',
    merchant: merchant,
    accountHint: 'XX8842',
    timestamp: DateTime(2026, 9, day, hour).millisecondsSinceEpoch,
    category: category,
    categoryConfidence: needsReview ? 0.51 : 0.95,
    needsReview: needsReview,
    parseMethod: needsReview ? 'heuristic' : 'generic_debit',
    sourcePackage: 'com.bank.app',
    rawText: raw,
  );

  static final List<Transaction> transactions = [
    _tx(
      id: 1,
      amount: -2450,
      merchant: 'SWIGGY',
      category: 'food_drink',
      day: 3,
      hour: 21,
      raw: 'HDFC BANK\nRs 2,450.00 debited from A/c XX8842 towards SWIGGY',
    ),
    _tx(
      id: 2,
      amount: 65000,
      merchant: 'SALARY',
      category: 'income',
      day: 1,
      hour: 9,
      raw: 'ICICI\nINR 65,000.00 credited towards SALARY from ACME CORP',
    ),
    _tx(
      id: 3,
      amount: -899,
      merchant: 'NETFLIX.COM',
      category: 'entertainment',
      day: 5,
      hour: 20,
      raw: 'SBI\nRs 899.75 spent on card XX1234 at NETFLIX.COM',
    ),
    _tx(
      id: 4,
      amount: -1299,
      merchant: 'MAKEMYTRIP',
      category: 'travel',
      day: 8,
      hour: 14,
      raw: 'Paytm\nRs 1,299 paid to MAKEMYTRIP for flight booking via UPI',
    ),
    _tx(
      id: 5,
      amount: -999,
      merchant: 'RANDOM TRADERS PVT LTD',
      category: 'food_drink',
      day: 10,
      hour: 11,
      needsReview: true,
      raw: 'Axis\nTxn of Rs 999.00 at RANDOM TRADERS PVT LTD',
    ),
    _tx(
      id: 6,
      amount: -1450,
      merchant: 'INDIAN OIL',
      category: 'transport',
      day: 12,
      hour: 18,
      raw: 'HDFC BANK\nRs 1,450.00 debited towards INDIAN OIL PETROL PUMP',
    ),
    Transaction(
      id: 7,
      amount: 3499,
      currency: 'INR',
      merchant: 'DECATHLON',
      accountHint: 'XX8842',
      timestamp: DateTime(2026, 9, 16, 10).millisecondsSinceEpoch,
      category: 'shopping',
      categoryConfidence: 0.95,
      needsReview: false,
      parseMethod: 'generic_credit',
      kind: TransactionKind.refund,
      sourcePackage: 'com.bank.app',
      rawText: 'ICICI\nRefund of Rs 3,499 received from DECATHLON SPORTS',
    ),
    Transaction(
      id: 8,
      amount: -500,
      currency: 'INR',
      merchant: 'TAJ HOTELS',
      accountHint: 'XX8842',
      timestamp: DateTime(2026, 9, 18, 19).millisecondsSinceEpoch,
      category: 'travel',
      categoryConfidence: 0.9,
      needsReview: false,
      parseMethod: 'generic_debit',
      kind: TransactionKind.pending,
      sourcePackage: 'com.bank.app',
      rawText: 'HDFC BANK\nRs 500 held as pre-authorization by TAJ HOTELS',
    ),
    _tx(
      id: 9,
      amount: -2199,
      merchant: 'FLIPKART',
      category: 'shopping',
      day: 15,
      hour: 22,
      raw: 'ICICI\nRs 2,199 spent at FLIPKART INTERNET PVT LTD',
    ),
  ];

  /// Mirrors the native accounting rules: holds are invisible to every
  /// aggregate, refunds offset their category instead of counting as income.
  static PulseSummary get summary {
    final settled = transactions.where((t) => !t.isPending);
    final net = settled.fold<double>(0, (s, t) => s + t.amount);
    final credit = settled
        .where((t) => t.amount > 0 && !t.isRefund)
        .fold<double>(0, (s, t) => s + t.amount);
    final debit = settled
        .where((t) => t.amount < 0)
        .fold<double>(0, (s, t) => s + -t.amount);
    return PulseSummary(
      netBalance: net,
      totalCredit: credit,
      totalDebit: debit,
      txCount: transactions.length,
      pendingReview: transactions.where((t) => t.needsReview).length,
    );
  }

  static List<CategorySpend> get byCategory {
    final totals = <String, double>{};
    final counts = <String, int>{};
    for (final t in transactions.where((t) => !t.isPending)) {
      if (t.amount < 0) {
        totals[t.category] = (totals[t.category] ?? 0) + -t.amount;
        counts[t.category] = (counts[t.category] ?? 0) + 1;
      } else if (t.isRefund) {
        totals[t.category] = (totals[t.category] ?? 0) - t.amount;
        counts[t.category] = (counts[t.category] ?? 0) + 1;
      }
    }
    return [
      for (final e in totals.entries)
        CategorySpend(
          categoryId: e.key,
          total: e.value < 0 ? 0 : e.value,
          count: counts[e.key] ?? 1,
        ),
    ]..sort((a, b) => b.total.compareTo(a.total));
  }

  static List<DaySpend> get byDay {
    // Grouped by calendar date, not day-of-month: months must not merge.
    final totals = <DateTime, double>{};
    for (final t in transactions.where((t) => t.amount < 0 && !t.isPending)) {
      final d = DateTime.fromMillisecondsSinceEpoch(t.timestamp);
      final day = DateTime(d.year, d.month, d.day);
      totals[day] = (totals[day] ?? 0) + -t.amount;
    }
    return [
      for (final e in totals.entries) DaySpend(day: e.key, total: e.value),
    ]..sort((a, b) => a.day.compareTo(b.day));
  }
}

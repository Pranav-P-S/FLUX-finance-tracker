import 'package:flutter/material.dart';

/// Dart mirrors of the entities exchanged with the Android engine over the
/// platform bridge.

class Transaction {
  final int id;
  final double amount; // signed: debit negative, credit positive
  final String currency;
  final String merchant;
  final String? accountHint;
  final int timestamp; // epoch ms
  final String category;
  final double categoryConfidence;
  final bool needsReview;
  final String parseMethod;
  final String sourcePackage;
  final String rawText;

  const Transaction({
    required this.id,
    required this.amount,
    required this.currency,
    required this.merchant,
    required this.accountHint,
    required this.timestamp,
    required this.category,
    required this.categoryConfidence,
    required this.needsReview,
    required this.parseMethod,
    required this.sourcePackage,
    required this.rawText,
  });

  bool get isCredit => amount > 0;

  factory Transaction.fromMap(Map<Object?, Object?> map) {
    return Transaction(
      id: (map['id'] as num).toInt(),
      amount: (map['amount'] as num).toDouble(),
      currency: map['currency'] as String? ?? 'INR',
      merchant: map['merchant'] as String? ?? 'Unknown',
      accountHint: map['accountHint'] as String?,
      timestamp: (map['timestamp'] as num).toInt(),
      category: map['category'] as String? ?? 'uncategorized',
      categoryConfidence: (map['categoryConfidence'] as num?)?.toDouble() ?? 0,
      needsReview: map['needsReview'] as bool? ?? false,
      parseMethod: map['parseMethod'] as String? ?? '',
      sourcePackage: map['sourcePackage'] as String? ?? '',
      rawText: map['rawText'] as String? ?? '',
    );
  }
}

class FluxCategory {
  final String id;
  final String label;
  final Color color;
  final String icon;
  final List<String> keywords;
  final bool isDefault;

  const FluxCategory({
    required this.id,
    required this.label,
    required this.color,
    required this.icon,
    required this.keywords,
    required this.isDefault,
  });

  factory FluxCategory.fromMap(Map<Object?, Object?> map) {
    return FluxCategory(
      id: map['id'] as String,
      label: map['label'] as String? ?? map['id'] as String,
      color: Color((map['color'] as num?)?.toInt() ?? 0xFF64748B),
      icon: map['icon'] as String? ?? 'category',
      keywords:
          (map['keywords'] as List<Object?>?)?.whereType<String>().toList() ??
          const [],
      isDefault: map['isDefault'] as bool? ?? false,
    );
  }
}

class PulseSummary {
  final double netBalance;
  final double totalCredit;
  final double totalDebit;
  final int txCount;
  final int pendingReview;

  const PulseSummary({
    required this.netBalance,
    required this.totalCredit,
    required this.totalDebit,
    required this.txCount,
    required this.pendingReview,
  });

  factory PulseSummary.fromMap(Map<Object?, Object?> map) => PulseSummary(
    netBalance: (map['netBalance'] as num).toDouble(),
    totalCredit: (map['totalCredit'] as num).toDouble(),
    totalDebit: (map['totalDebit'] as num).toDouble(),
    txCount: (map['txCount'] as num).toInt(),
    pendingReview: (map['pendingReview'] as num).toInt(),
  );
}

class CategorySpend {
  final String categoryId;
  final double total;
  final int count;

  const CategorySpend({
    required this.categoryId,
    required this.total,
    required this.count,
  });

  factory CategorySpend.fromMap(Map<Object?, Object?> map) => CategorySpend(
    categoryId: map['categoryId'] as String,
    total: (map['total'] as num).toDouble(),
    count: (map['count'] as num).toInt(),
  );
}

class DaySpend {
  final DateTime day;
  final double total;

  const DaySpend({required this.day, required this.total});

  factory DaySpend.fromMap(Map<Object?, Object?> map) => DaySpend(
    day: DateTime.parse(map['day'] as String),
    total: (map['total'] as num).toDouble(),
  );
}

/// Compact, sign-aware money formatting. INR abbreviates crores of rupees and
/// lakhs as is conventional for Indian banking; other currencies stay numeric.
String formatMoney(double value, {String currency = 'INR'}) {
  const symbols = {'INR': '₹', 'USD': r'$', 'EUR': '€', 'GBP': '£'};
  final symbol = symbols[currency] ?? '₹';
  final sign = value < 0 ? '-' : '';
  final abs = value.abs();

  if (currency == 'INR' && abs >= 100000) {
    final lakhs = abs / 100000;
    final text = lakhs.toStringAsFixed(lakhs >= 10 ? 1 : 2);
    return '$sign$symbol${text}L';
  }

  final digits = abs.round() == abs
      ? abs.toStringAsFixed(0)
      : abs.toStringAsFixed(2);
  final grouped = digits.replaceAllMapped(
    RegExp(r'(\d)(?=(\d{3})+(?!\d))'),
    (m) => '${m[1]},',
  );
  return '$sign$symbol$grouped';
}

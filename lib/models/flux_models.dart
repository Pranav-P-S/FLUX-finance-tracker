import 'package:flutter/material.dart';

/// Dart mirrors of the entities exchanged with the Android engine over the
/// platform bridge.

/// Tolerant decoders for platform-channel maps: the native side evolves
/// independently, and one malformed field must degrade to a default instead
/// of throwing a cast error that rejects an entire payload.
int? _asInt(Object? v) => v is num ? v.toInt() : int.tryParse('$v');
double? _asDouble(Object? v) => v is num ? v.toDouble() : double.tryParse('$v');
bool _asBool(Object? v) => v == true || v == 'true';
String? _asString(Object? v) => v is String? ? v : null;

/// Lifecycle of a money event, mirroring the Kotlin TransactionKind.
enum TransactionKind {
  purchase('purchase'),
  refund('refund'),
  pending('pending');

  final String id;

  const TransactionKind(this.id);

  static TransactionKind from(String? id) => TransactionKind.values.firstWhere(
    (k) => k.id == id,
    orElse: () => TransactionKind.purchase,
  );
}

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
  final TransactionKind kind;
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
    this.kind = TransactionKind.purchase,
    required this.sourcePackage,
    required this.rawText,
  });

  bool get isCredit => amount > 0;
  bool get isPending => kind == TransactionKind.pending;
  bool get isRefund => kind == TransactionKind.refund;

  factory Transaction.fromMap(Map<Object?, Object?> map) {
    return Transaction(
      id: _asInt(map['id']) ?? 0,
      amount: _asDouble(map['amount']) ?? 0,
      currency: _asString(map['currency']) ?? 'INR',
      merchant: _asString(map['merchant']) ?? 'Unknown',
      accountHint: _asString(map['accountHint']),
      timestamp: _asInt(map['timestamp']) ?? 0,
      category: _asString(map['category']) ?? 'uncategorized',
      categoryConfidence: _asDouble(map['categoryConfidence']) ?? 0,
      needsReview: _asBool(map['needsReview']),
      parseMethod: _asString(map['parseMethod']) ?? '',
      kind: TransactionKind.from(_asString(map['kind'])),
      sourcePackage: _asString(map['sourcePackage']) ?? '',
      rawText: _asString(map['rawText']) ?? '',
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
    final id = _asString(map['id']) ?? '';
    return FluxCategory(
      id: id,
      label: _asString(map['label']) ?? (id.isEmpty ? 'Unknown' : id),
      color: Color(_asInt(map['color']) ?? 0xFF64748B),
      icon: _asString(map['icon']) ?? 'category',
      keywords:
          (map['keywords'] as List<Object?>?)?.whereType<String>().toList() ??
          const [],
      isDefault: _asBool(map['isDefault']),
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
    netBalance: _asDouble(map['netBalance']) ?? 0,
    totalCredit: _asDouble(map['totalCredit']) ?? 0,
    totalDebit: _asDouble(map['totalDebit']) ?? 0,
    txCount: _asInt(map['txCount']) ?? 0,
    pendingReview: _asInt(map['pendingReview']) ?? 0,
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
    categoryId: _asString(map['categoryId']) ?? 'uncategorized',
    total: _asDouble(map['total']) ?? 0,
    count: _asInt(map['count']) ?? 0,
  );
}

class DaySpend {
  final DateTime day;
  final double total;

  const DaySpend({required this.day, required this.total});

  factory DaySpend.fromMap(Map<Object?, Object?> map) => DaySpend(
    day: DateTime.tryParse(_asString(map['day']) ?? '') ?? DateTime.now(),
    total: _asDouble(map['total']) ?? 0,
  );
}

/// Compact, sign-aware money formatting. INR abbreviates crores/lakhs as is
/// conventional for Indian banking; other currencies stay numeric.
String formatMoney(double value, {String currency = 'INR'}) {
  const symbols = {'INR': '₹', 'USD': r'$', 'EUR': '€', 'GBP': '£'};
  final symbol = symbols[currency] ?? '₹';
  if (value.isNaN || value.isInfinite) return '${symbol}0';
  final sign = value < 0 ? '-' : '';
  final abs = value.abs();

  if (currency == 'INR' && abs >= 10000000) {
    final crores = abs / 10000000;
    final text = crores.toStringAsFixed(crores >= 10 ? 1 : 2);
    return '$sign$symbol${text}Cr';
  }

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

// Pure Dart calculation code: no Flutter widgets or platform APIs are needed here.
enum TicketClass {
  economy(20), // Economy includes 20 kilograms.
  business(40); // Business includes 40 kilograms.

  const TicketClass(this.allowanceKg);
  final int allowanceKg;
}

// The receipt holds integer sen, so displayed lines always add up exactly.
class BaggageResult {
  const BaggageResult({
    required this.excessGrams,
    required this.chargeSen,
    required this.taxSen,
  });
  final int excessGrams;
  final int chargeSen;
  final int taxSen;
  int get totalSen => chargeSen + taxSen;
}

class BaggageCalculator {
  // Return an error message for invalid input, or null when the field is valid.
  static String? validate(
    String? raw, {
    required int decimals,
    required String name,
  }) {
    if (raw == null || raw.trim().isEmpty) return 'Enter $name.';
    final rule = RegExp(
      r'^\d{1,6}(?:\.\d{1,'
      '$decimals'
      r'})?$',
    );
    if (!rule.hasMatch(raw.trim())) {
      return 'Use 0–999999, with up to $decimals decimal places.';
    }
    return null;
  }

  // Convert a decimal string to a scaled integer without using floating point.
  // For example: scaled('42.5', 3) is 42500 grams; scaled('12', 2) is 1200 sen.
  static int scaled(String raw, int places) {
    final parts = raw.trim().split('.');
    final fraction = parts.length == 2 ? parts[1] : '';
    return int.parse('${parts[0]}${fraction.padRight(places, '0')}');
  }

  static BaggageResult calculate({
    required String weight,
    required TicketClass ticket,
    required String rate,
  }) {
    if (validate(weight, decimals: 3, name: 'a weight') != null ||
        validate(rate, decimals: 2, name: 'a rate') != null) {
      throw ArgumentError(
        'Weight and rate must be valid nonnegative decimals.',
      );
    }
    final grams = scaled(weight, 3);
    final rateSen = scaled(rate, 2);
    final excess = (grams - ticket.allowanceKg * 1000).clamp(0, grams);
    // Adding half the divisor before integer division gives HALF_UP rounding.
    final charge = (excess * rateSen + 500) ~/ 1000;
    final tax =
        (charge * 8 + 50) ~/ 100; // Assumed 8% service tax, rounded to sen.
    return BaggageResult(excessGrams: excess, chargeSen: charge, taxSen: tax);
  }
}

// Format integer sen as RM with two decimal places and grouped thousands.
String money(int sen) {
  final whole = (sen ~/ 100).toString().replaceAllMapped(
    RegExp(r'\B(?=(\d{3})+(?!\d))'),
    (_) => ',',
  );
  return 'RM $whole.${(sen % 100).toString().padLeft(2, '0')}';
}

// Format grams as compact kilograms (42500 becomes 42.5; 20000 becomes 20).
String kilograms(int grams) {
  final fraction = (grams % 1000)
      .toString()
      .padLeft(3, '0')
      .replaceFirst(RegExp(r'0+$'), '');
  return '${grams ~/ 1000}${fraction.isEmpty ? '' : '.$fraction'}';
}

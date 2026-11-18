import 'package:flutter/material.dart';

// The same semantic colours used by the Java version, expressed as Flutter themes.
abstract final class AppTheme {
  static const navy = Color(0xFF102E3A);
  static const teal = Color(0xFF087F78);
  static const mint = Color(0xFF8AE1D1);

  static ThemeData create(bool dark) {
    final foreground = dark ? const Color(0xFFEAF4F6) : navy;
    final secondary = dark ? const Color(0xFFB1C4CC) : const Color(0xFF526973);
    final surface = dark ? const Color(0xFF142730) : Colors.white;
    final page = dark ? const Color(0xFF0B171E) : const Color(0xFFF4F7F8);
    final outline = dark ? const Color(0xFF34505D) : const Color(0xFFDCE5E7);
    final scheme =
        ColorScheme.fromSeed(
          seedColor: teal,
          brightness: dark ? Brightness.dark : Brightness.light,
        ).copyWith(
          primary: dark ? mint : teal,
          onPrimary: dark ? navy : Colors.white,
          surface: surface,
          onSurface: foreground,
          onSurfaceVariant: secondary,
          outline: outline,
        );
    return ThemeData(
      useMaterial3: true,
      colorScheme: scheme,
      scaffoldBackgroundColor: page,
      fontFamily: 'Roboto',
      textTheme: ThemeData(brightness: scheme.brightness).textTheme
          .apply(bodyColor: foreground, displayColor: foreground),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: page,
        hintStyle: TextStyle(color: secondary),
        contentPadding: const EdgeInsets.symmetric(
          horizontal: 16,
          vertical: 16,
        ),
        border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: BorderSide(color: outline),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: BorderSide(color: scheme.primary, width: 2),
        ),
      ),
    );
  }
}

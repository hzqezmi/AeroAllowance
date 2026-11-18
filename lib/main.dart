import 'package:flutter/material.dart'; // Flutter UI widgets and animation classes.
import 'package:flutter/services.dart'; // Keyboard and system-bar appearance.
import 'package:shared_preferences/shared_preferences.dart'; // Save the theme locally.
import 'package:shared_preferences_android/shared_preferences_android.dart'; // Read the Java app's existing preference file.
import 'package:url_launcher/url_launcher.dart'; // Open the repository in a browser.

import 'app_theme.dart'; // Light and dark colour palettes.
import 'author_config.dart'; // Author identity and repository URL.
import 'baggage_calculator.dart'; // Independently tested decimal-safe calculation.

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized(); // Initialise the engine before plugins.
  final preferences = SharedPreferencesAsync(
    options: const SharedPreferencesAsyncAndroidOptions(
      backend: SharedPreferencesAndroidBackendLibrary.SharedPreferences,
      originalSharedPreferencesOptions: AndroidSharedPreferencesStoreOptions(
        fileName: 'appearance',
      ),
    ),
  ); // Same file and key as Java, so the user's existing theme survives conversion.
  bool? savedDark; // null means follow the device's current appearance.
  try {
    savedDark = await preferences.getBool('dark_mode');
  } catch (_) {
    // A failed preference read should not stop the offline calculator opening.
  }
  runApp(
    AeroAllowanceApp(
      initialDark: savedDark,
      onThemeSaved: (dark) => preferences.setBool('dark_mode', dark),
    ),
  );
}

class AeroAllowanceApp extends StatefulWidget {
  const AeroAllowanceApp({super.key, this.initialDark, this.onThemeSaved});
  final bool? initialDark;
  final Future<void> Function(bool)?
  onThemeSaved; // Injectable for widget tests.
  @override
  State<AeroAllowanceApp> createState() => _AeroAllowanceAppState();
}

class _AeroAllowanceAppState extends State<AeroAllowanceApp> {
  late bool? dark = widget.initialDark;
  final messenger = GlobalKey<ScaffoldMessengerState>();
  Future<void> toggleTheme(bool value) async {
    setState(
      () => dark = value,
    ); // Update colours without destroying Home's state.
    try {
      await widget.onThemeSaved?.call(value);
    } catch (_) {
      messenger.currentState?.showSnackBar(
        const SnackBar(
          content: Text(
            'Appearance changed, but could not be saved for next time.',
          ),
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) => MaterialApp(
    title: 'AeroAllowance',
    debugShowCheckedModeBanner: false,
    restorationScopeId: 'aero_allowance',
    scaffoldMessengerKey: messenger,
    theme: AppTheme.create(false),
    darkTheme: AppTheme.create(true),
    themeMode: dark == null
        ? ThemeMode.system
        : dark!
        ? ThemeMode.dark
        : ThemeMode.light,
    themeAnimationDuration:
        WidgetsBinding
            .instance
            .platformDispatcher
            .accessibilityFeatures
            .disableAnimations
        ? Duration.zero
        : const Duration(milliseconds: 180),
    home: _HomeShell(onToggleTheme: toggleTheme),
  );
}

class _HomeShell extends StatefulWidget {
  const _HomeShell({required this.onToggleTheme});
  final ValueChanged<bool> onToggleTheme;
  @override
  State<_HomeShell> createState() => _HomeShellState();
}

class _HomeShellState extends State<_HomeShell>
    with RestorationMixin, SingleTickerProviderStateMixin {
  // These controllers live above Home/About, keeping drafts during navigation.
  final weight = RestorableTextEditingController();
  final rate = RestorableTextEditingController();
  final business = RestorableBool(false);
  final page = RestorableInt(0);
  final resultVisible = RestorableBool(false);
  final form = GlobalKey<FormState>();
  final receiptKey = GlobalKey();
  final homeScroll = ScrollController();
  final aboutScroll = ScrollController();
  final weightFocus = FocusNode();
  late final AnimationController pageMotion;
  bool listening = false, restoring = false;
  String lastWeight = '',
      lastRate = ''; // Cursor movement alone is not an edit.
  int receiptRevision = 0;

  @override
  String? get restorationId => 'calculator_screen';
  TicketClass get ticket =>
      business.value ? TicketClass.business : TicketClass.economy;
  bool get dark => Theme.of(context).brightness == Brightness.dark;
  bool get reduceMotion => MediaQuery.disableAnimationsOf(context);
  ColorScheme get colours => Theme.of(context).colorScheme;
  Duration get motion =>
      reduceMotion ? Duration.zero : const Duration(milliseconds: 180);

  @override
  void initState() {
    super.initState();
    pageMotion = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 160),
      value: 1,
    );
  }

  @override
  void restoreState(RestorationBucket? oldBucket, bool initialRestore) {
    restoring =
        true; // Restoring text is not an edit; preserve the saved estimate.
    if (listening) {
      weight.value.removeListener(invalidate);
      rate.value.removeListener(invalidate);
    }
    registerForRestoration(weight, 'weight');
    registerForRestoration(rate, 'rate');
    registerForRestoration(business, 'business');
    registerForRestoration(page, 'page');
    registerForRestoration(resultVisible, 'result_visible');
    weight.value.addListener(invalidate);
    rate.value.addListener(invalidate);
    listening = true;
    lastWeight = weight.value.text;
    lastRate = rate.value.text;
    restoring = false;
  }

  void invalidate() {
    final textChanged =
        lastWeight != weight.value.text || lastRate != rate.value.text;
    lastWeight = weight.value.text;
    lastRate = rate.value.text;
    if (!restoring && textChanged && resultVisible.value) {
      setState(
        () => resultVisible.value = false,
      ); // Remove a now-stale estimate.
    }
  }

  void selectCabin(bool value) {
    if (business.value == value) {
      return; // Keep the fee when re-tapping the active class.
    }
    setState(() {
      business.value = value;
      resultVisible.value = false;
    });
  }

  void navigate(int value) {
    if (page.value == value) return;
    FocusManager.instance.primaryFocus?.unfocus();
    setState(() => page.value = value);
    if (!reduceMotion) pageMotion.forward(from: 0);
  }

  void calculate() {
    if (!form.currentState!.validate()) {
      setState(() => resultVisible.value = false);
      return; // Show field errors first.
    }
    FocusManager.instance.primaryFocus?.unfocus();
    setState(() {
      resultVisible.value = true;
      receiptRevision++;
    });
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final target =
          receiptKey.currentContext; // Wait for the new receipt to be laid out.
      if (target != null) {
        Scrollable.ensureVisible(
          target,
          duration: motion,
          alignment: 0.05,
          curve: Curves.easeOut,
        );
      }
    });
  }

  void reset() {
    restoring = true;
    form.currentState
        ?.reset(); // Clear errors before explicitly clearing restored drafts.
    weight.value.clear();
    rate.value.clear();
    restoring = false;
    setState(() {
      business.value = false;
      resultVisible.value = false;
    });
    weightFocus.requestFocus();
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(
        content: Text('Details cleared'),
        duration: Duration(seconds: 2),
      ),
    );
  }

  @override
  void dispose() {
    weight.value.removeListener(invalidate);
    rate.value.removeListener(invalidate);
    weight.dispose();
    rate.dispose();
    business.dispose();
    page.dispose();
    resultVisible.dispose();
    homeScroll.dispose();
    aboutScroll.dispose();
    weightFocus.dispose();
    pageMotion.dispose();
    super
        .dispose(); // Release controllers/listeners when the screen is removed.
  }

  @override
  Widget build(BuildContext context) {
    final bars = dark ? SystemUiOverlayStyle.light : SystemUiOverlayStyle.dark;
    return AnnotatedRegion<SystemUiOverlayStyle>(
      value: bars.copyWith(
        statusBarColor: Colors.transparent,
        systemNavigationBarColor: Theme.of(context).scaffoldBackgroundColor,
      ),
      child: Scaffold(
        body: SafeArea(
          child: Column(
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(22, 10, 18, 10),
                child: Row(
                  children: [
                    const AppIcon(size: 38),
                    const SizedBox(width: 10),
                    const Expanded(
                      child: Text(
                        'AeroAllowance',
                        style: TextStyle(
                          fontSize: 20,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ),
                    Text(
                      'Dark',
                      style: TextStyle(
                        fontSize: 12,
                        color: colours.onSurfaceVariant,
                      ),
                    ),
                    Semantics(
                      label: 'Dark mode',
                      child: Switch(
                        key: const Key('darkSwitch'),
                        value: dark,
                        onChanged: widget.onToggleTheme,
                      ),
                    ),
                  ],
                ),
              ),
              Expanded(
                child: FadeTransition(
                  opacity: pageMotion,
                  child: SlideTransition(
                    position: Tween(
                      begin: const Offset(0, .015),
                      end: Offset.zero,
                    ).animate(pageMotion),
                    child: IndexedStack(
                      index: page.value,
                      children: [home(), about()],
                    ),
                  ),
                ),
              ),
              Container(
                color: colours.surface,
                padding: const EdgeInsets.symmetric(
                  horizontal: 16,
                  vertical: 8,
                ),
                child: Row(
                  children: [
                    Expanded(
                      child: ConfirmButton(
                        label: 'Home',
                        selected: page.value == 0,
                        onPressed: () => navigate(0),
                      ),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: ConfirmButton(
                        label: 'About',
                        selected: page.value == 1,
                        onPressed: () => navigate(1),
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget home() => SingleChildScrollView(
    controller: homeScroll,
    padding: const EdgeInsets.fromLTRB(22, 16, 22, 28),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Container(
          padding: const EdgeInsets.all(20),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(24),
            gradient: const LinearGradient(
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
              colors: [AppTheme.navy, Color(0xFF18555A)],
            ),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text(
                'BEFORE YOU BOARD',
                style: TextStyle(
                  fontSize: 11,
                  color: AppTheme.mint,
                  fontWeight: FontWeight.bold,
                ),
              ),
              const SizedBox(height: 10),
              const Text(
                'A little extra bag.\nNo extra surprises.',
                style: TextStyle(
                  fontSize: 28,
                  height: 1.25,
                  fontWeight: FontWeight.bold,
                  color: Colors.white,
                ),
              ),
              const SizedBox(height: 10),
              const Text(
                'Know your baggage fee before you fly.',
                style: TextStyle(fontSize: 14, color: Color(0xFFD4E4E8)),
              ),
              const SizedBox(height: 18),
              badge('20 kg Economy   /   40 kg Business'),
            ],
          ),
        ),
        const SizedBox(height: 20),
        panel(
          child: Form(
            key: form,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                heading('01  Your baggage', 20),
                const SizedBox(height: 4),
                muted('Choose your cabin and enter your details.'),
                const SizedBox(height: 18),
                heading('Ticket class', 14),
                const SizedBox(height: 8),
                Row(
                  children: [
                    Expanded(
                      child: ConfirmButton(
                        key: const Key('economy'),
                        label:
                            '${business.value ? '' : '✓ '}Economy\n20 kg included',
                        selected: !business.value,
                        onPressed: () => selectCabin(false),
                      ),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: ConfirmButton(
                        key: const Key('business'),
                        label:
                            '${business.value ? '✓ ' : ''}Business\n40 kg included',
                        selected: business.value,
                        onPressed: () => selectCabin(true),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 20),
                field(
                  'Checked baggage weight (kg)',
                  'e.g. 25 or 42.5',
                  weight.value,
                  const Key('weight'),
                  3,
                  focus: weightFocus,
                ),
                const SizedBox(height: 16),
                field(
                  'Excess baggage rate (RM / kg)',
                  'e.g. 15.00',
                  rate.value,
                  const Key('rate'),
                  2,
                ),
                const SizedBox(height: 12),
                muted(
                  'Use the rate provided for your flight. Zero is allowed.',
                  12,
                ),
                const SizedBox(height: 12),
                ConfirmButton(
                  key: const Key('calculate'),
                  label: 'Calculate my fee  →',
                  selected: true,
                  onPressed: calculate,
                ),
                const SizedBox(height: 10),
                ConfirmButton(
                  key: const Key('reset'),
                  label: 'Reset details',
                  onPressed: reset,
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 20),
        // Changing the receipt key triggers a fresh confirmation transition on Calculate.
        Container(
          key: receiptKey,
          child: AnimatedSwitcher(
            duration: motion,
            transitionBuilder: (child, animation) => FadeTransition(
              opacity: animation,
              child: SlideTransition(
                position: Tween(
                  begin: const Offset(0, .02),
                  end: Offset.zero,
                ).animate(animation),
                child: child,
              ),
            ),
            child: resultVisible.value ? receipt() : emptyReceipt(),
          ),
        ),
        const SizedBox(height: 20),
        muted(
          'For assignment estimates • 8% assumed service tax.\nActual airline fees and rules may differ.',
        ),
      ],
    ),
  );

  Widget field(
    String label,
    String hint,
    TextEditingController controller,
    Key key,
    int decimals, {
    FocusNode? focus,
  }) => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      heading(label, 14),
      const SizedBox(height: 8),
      TextFormField(
        key: key,
        controller: controller,
        focusNode: focus,
        keyboardType: const TextInputType.numberWithOptions(decimal: true),
        textInputAction: decimals == 3
            ? TextInputAction.next
            : TextInputAction.done,
        onFieldSubmitted: (_) {
          if (decimals == 2) calculate();
        },
        style: const TextStyle(fontSize: 18),
        decoration: InputDecoration(hintText: hint, errorMaxLines: 3),
        validator: (value) => BaggageCalculator.validate(
          value,
          decimals: decimals,
          name: decimals == 3 ? 'a weight' : 'a rate',
        ),
      ),
    ],
  );

  Widget emptyReceipt() => panel(
    key: const ValueKey('empty_receipt'),
    navy: true,
    child: const Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(
          '02  Your estimate',
          style: TextStyle(
            fontSize: 20,
            fontWeight: FontWeight.bold,
            color: Colors.white,
          ),
        ),
        SizedBox(height: 14),
        Text(
          'Ready when you are.',
          style: TextStyle(
            fontSize: 24,
            fontWeight: FontWeight.bold,
            color: AppTheme.mint,
          ),
        ),
        SizedBox(height: 8),
        Text(
          'Enter your baggage details to see a full fee breakdown here.',
          style: TextStyle(fontSize: 15, color: Color(0xFFD4E4E8)),
        ),
      ],
    ),
  );

  Widget receipt() {
    final result = BaggageCalculator.calculate(
      weight: weight.value.text,
      ticket: ticket,
      rate: rate.value.text,
    ); // Mathematics stays outside the UI.
    final weightGrams = BaggageCalculator.scaled(weight.value.text, 3);
    final rateSen = BaggageCalculator.scaled(rate.value.text, 2);
    return panel(
      key: ValueKey('receipt_$receiptRevision'),
      navy: true,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const Text(
            '02  Your estimate',
            style: TextStyle(
              fontSize: 20,
              fontWeight: FontWeight.bold,
              color: Colors.white,
            ),
          ),
          const SizedBox(height: 12),
          badge(
            result.excessGrams == 0 ? 'WITHIN ALLOWANCE' : 'EXCESS BAGGAGE',
          ),
          const SizedBox(height: 14),
          const Text(
            'TOTAL PAYABLE',
            style: TextStyle(
              fontSize: 12,
              fontWeight: FontWeight.bold,
              color: AppTheme.mint,
            ),
          ),
          Semantics(
            liveRegion: true,
            child: Text(
              money(result.totalSen),
              key: const Key('total'),
              style: const TextStyle(
                fontSize: 38,
                fontWeight: FontWeight.bold,
                color: Colors.white,
              ),
            ),
          ),
          Text(
            result.excessGrams == 0
                ? 'Within your allowance. No extra fee.'
                : 'Includes the assumed 8% service tax',
            style: const TextStyle(fontSize: 14, color: Color(0xFFD4E4E8)),
          ),
          const SizedBox(height: 18),
          const TicketDivider(),
          const SizedBox(height: 10),
          receiptRow('Baggage weight', '${kilograms(weightGrams)} kg'),
          receiptRow('Included allowance', '${ticket.allowanceKg} kg'),
          receiptRow('Excess weight', '${kilograms(result.excessGrams)} kg'),
          receiptRow('Rate per kg', money(rateSen)),
          receiptRow('Excess baggage charge', money(result.chargeSen)),
          receiptRow('Service tax (8%)', money(result.taxSen)),
          const SizedBox(height: 12),
          const TicketDivider(),
          const SizedBox(height: 16),
          const Text(
            'HOW IT WORKS',
            style: TextStyle(
              fontSize: 11,
              fontWeight: FontWeight.bold,
              color: AppTheme.mint,
            ),
          ),
          const SizedBox(height: 4),
          Text(
            'Excess = max(0, ${kilograms(weightGrams)} − ${ticket.allowanceKg})\n'
            'Charge = ${kilograms(result.excessGrams)} × ${money(rateSen)}\n'
            'Total = charge + (charge × 0.08)',
            style: const TextStyle(
              fontSize: 13,
              height: 1.5,
              color: Color(0xFFD4E4E8),
            ),
          ),
        ],
      ),
    );
  }

  Widget receiptRow(String label, String value) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 7),
    child: Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Expanded(
          child: Text(
            label,
            style: const TextStyle(fontSize: 14, color: Color(0xFFD4E4E8)),
          ),
        ),
        const SizedBox(width: 12),
        Flexible(
          fit: FlexFit.tight, // Fill the value column so amounts align at the right edge.
          child: Text(
            value,
            textAlign: TextAlign.right,
            style: const TextStyle(
              fontSize: 14,
              color: Colors.white,
              fontWeight: FontWeight.bold,
            ),
          ),
        ),
      ],
    ),
  );

  Future<void> openRepository() async {
    try {
      final opened = await launchUrl(
        Uri.parse(AuthorConfig.repository),
        mode: LaunchMode.externalApplication,
      );
      if (!opened) throw StateError('No browser available');
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text(
              'Unable to open the repository. Please check your browser.',
            ),
          ),
        );
      }
    }
  }

  Widget about() => SingleChildScrollView(
    controller: aboutScroll,
    padding: const EdgeInsets.fromLTRB(22, 16, 22, 28),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(
          'BEHIND THE APP',
          style: TextStyle(
            fontSize: 11,
            color: colours.primary,
            fontWeight: FontWeight.bold,
          ),
        ),
        const SizedBox(height: 8),
        heading('Built for a smoother\nstart to your journey.', 30),
        const SizedBox(height: 24),
        panel(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const AppIcon(size: 80),
              const SizedBox(height: 14),
              heading('AeroAllowance', 26),
              const SizedBox(height: 4),
              muted('Version 1.0 • Excess baggage estimator'),
              const SizedBox(height: 20),
              Row(
                children: [
                  ClipOval(
                    child: Image.asset(
                      'assets/author_portrait.png',
                      width: 96,
                      height: 96,
                      fit: BoxFit.cover,
                      semanticLabel: 'Portrait of ${AuthorConfig.name}',
                    ),
                  ),
                  const SizedBox(width: 16),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        aboutLabel('AUTHOR'),
                        const SizedBox(height: 6),
                        heading(AuthorConfig.name, 17),
                      ],
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              aboutLabel('MATRIC NUMBER'),
              const SizedBox(height: 4),
              const Text(AuthorConfig.matric, style: TextStyle(fontSize: 17)),
              const SizedBox(height: 12),
              aboutLabel('COURSE'),
              const SizedBox(height: 4),
              const Text(AuthorConfig.course, style: TextStyle(fontSize: 17)),
            ],
          ),
        ),
        const SizedBox(height: 20),
        panel(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              heading('Open the source', 20),
              const SizedBox(height: 6),
              muted(
                'Explore the calculation logic, interface, and learning notes on GitHub.',
              ),
              const SizedBox(height: 12),
              ConfirmButton(
                key: const Key('github'),
                label: 'View GitHub repository  ↗',
                selected: true,
                onPressed: openRepository,
              ),
              const SizedBox(height: 8),
              SelectableText(
                AuthorConfig.repository,
                style: TextStyle(fontSize: 13, color: colours.onSurfaceVariant),
              ),
            ],
          ),
        ),
        const SizedBox(height: 22),
        muted(
          'Economy allowance: 20 kg • Business allowance: 40 kg. '
          'Calculations use the assignment’s assumed 8% service tax.',
        ),
        const SizedBox(height: 12),
        muted(
          '© ${DateTime.now().year} ${AuthorConfig.name}. All rights reserved.\n'
          'Created for the ICT602 individual assignment.',
        ),
      ],
    ),
  );

  Widget aboutLabel(String value) => Text(
    value,
    style: TextStyle(
      fontSize: 11,
      color: colours.primary,
      fontWeight: FontWeight.bold,
    ),
  );
  Widget heading(String text, double size) => Text(
    text,
    style: TextStyle(fontSize: size, height: 1.25, fontWeight: FontWeight.bold),
  );
  Widget muted(String text, [double size = 14]) => Text(
    text,
    style: TextStyle(
      fontSize: size,
      height: 1.4,
      color: colours.onSurfaceVariant,
    ),
  );
  Widget badge(String text) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
    decoration: BoxDecoration(
      color: const Color(0xFF234F57),
      borderRadius: BorderRadius.circular(10),
    ),
    child: Text(
      text,
      style: const TextStyle(
        fontSize: 10,
        fontWeight: FontWeight.bold,
        color: Color(0xFFD8F8F0),
      ),
    ),
  );
  Widget panel({Key? key, required Widget child, bool navy = false}) =>
      Container(
        key: key,
        width: double.infinity,
        padding: const EdgeInsets.all(20),
        decoration: BoxDecoration(
          color: navy ? AppTheme.navy : colours.surface,
          borderRadius: BorderRadius.circular(24),
          border: navy ? null : Border.all(color: colours.outline),
        ),
        child: child,
      );
}

// Shared button: same flat styling, native Material ripple, and confirmation scale.
class ConfirmButton extends StatefulWidget {
  const ConfirmButton({
    super.key,
    required this.label,
    this.selected = false,
    required this.onPressed,
  });
  final String label;
  final bool selected;
  final VoidCallback onPressed;
  @override
  State<ConfirmButton> createState() => _ConfirmButtonState();
}

class _ConfirmButtonState extends State<ConfirmButton>
    with SingleTickerProviderStateMixin {
  late final AnimationController animation = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 180),
    value: 1,
  );
  @override
  void dispose() {
    animation.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final dark = Theme.of(context).brightness == Brightness.dark;
    final reduce = MediaQuery.disableAnimationsOf(context);
    final scheme = Theme.of(context).colorScheme;
    return ScaleTransition(
      scale: Tween(
        begin: .96,
        end: 1.0,
      ).animate(CurvedAnimation(parent: animation, curve: Curves.easeOut)),
      child: Semantics(
        button: true,
        selected: widget.selected,
        child: AnimatedContainer(
          duration: reduce ? Duration.zero : const Duration(milliseconds: 180),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(14),
            color: widget.selected
                ? dark
                      ? const Color(0xFF116F6A)
                      : AppTheme.teal
                : dark
                ? const Color(0xFF243C47)
                : const Color(0xFFE8EFF0),
          ),
          child: Material(
            color: Colors.transparent,
            borderRadius: BorderRadius.circular(14),
            clipBehavior: Clip.antiAlias,
            child: InkWell(
              borderRadius: BorderRadius.circular(14),
              onTap: () {
                if (!reduce) animation.forward(from: 0);
                widget.onPressed();
              },
              child: ConstrainedBox(
                constraints: const BoxConstraints(minHeight: 52),
                child: Padding(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 10,
                    vertical: 12,
                  ),
                  child: Center(
                    child: Text(
                      widget.label,
                      textAlign: TextAlign.center,
                      style: TextStyle(
                        fontSize: 14,
                        fontWeight: FontWeight.bold,
                        color: widget.selected
                            ? Colors.white
                            : scheme.onSurface,
                      ),
                    ),
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class TicketDivider extends StatelessWidget {
  const TicketDivider({super.key});
  @override
  Widget build(BuildContext context) => LayoutBuilder(
    builder: (context, bounds) => Row(
      children: List.generate(
        (bounds.maxWidth / 9).floor(),
        (_) => Expanded(
          child: Container(
            height: 1,
            margin: const EdgeInsets.only(right: 4),
            color: const Color(0xFF47626C),
          ),
        ),
      ),
    ),
  );
}

// Draw the original suitcase/aircraft mark using Flutter's Canvas instead of a new bitmap.
class AppIcon extends StatelessWidget {
  const AppIcon({super.key, this.size = 38});
  final double size;
  @override
  Widget build(BuildContext context) => Semantics(
    label: 'AeroAllowance suitcase and aircraft icon',
    image: true,
    child: CustomPaint(size: Size.square(size), painter: _IconPainter()),
  );
}

class _IconPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    canvas.scale(size.width / 108, size.height / 108);
    final navy = Paint()..color = AppTheme.navy;
    final mint = Paint()..color = const Color(0xFF67D6C4);
    canvas.drawRect(const Rect.fromLTWH(0, 0, 108, 108), navy);
    canvas.drawRRect(
      RRect.fromRectAndRadius(
        const Rect.fromLTWH(25, 34, 58, 51),
        const Radius.circular(6),
      ),
      mint,
    );
    canvas.drawPath(
      Path()
        ..moveTo(43, 23)
        ..lineTo(65, 23)
        ..lineTo(65, 29)
        ..lineTo(49, 29)
        ..lineTo(49, 34)
        ..lineTo(43, 34)
        ..close(),
      mint,
    );
    canvas.drawPath(
      Path()
        ..moveTo(36, 57)
        ..lineTo(48, 60)
        ..lineTo(66, 46)
        ..lineTo(71, 49)
        ..lineTo(59, 65)
        ..lineTo(71, 69)
        ..lineTo(68, 73)
        ..lineTo(52, 70)
        ..lineTo(43, 77)
        ..lineTo(39, 75)
        ..lineTo(44, 66)
        ..lineTo(34, 61)
        ..close(),
      navy,
    );
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}

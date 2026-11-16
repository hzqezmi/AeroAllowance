/*
 * LEARNING NOTES - MAIN ACTIVITY
 * Read from onCreate() to showPage(), then follow the button callbacks.
 * The numeric formulas live in BaggageCalculator.java; this file handles the UI.
 *
 * Java reading keys:
 *   private = used inside this class; public = accessible to Android/other callers.
 *   static final = a shared constant; void = the method returns no value.
 *   new = create an object; this = the current activity; super = the Activity base class.
 *   condition ? first : second = choose one value; ! = not; && = and; || = or.
 *   -> = a callback/lambda run later in response to an event.
 *   dp = density-independent spacing; sp = text size respecting the user's font setting.
 *   LayoutParams -1 = MATCH_PARENT; -2 = WRAP_CONTENT; weight 1 = share spare space.
 *   0xAARRGGBB = alpha/red/green/blue colour; FF alpha is fully opaque.
 *   Comments explain each meaningful code line; closing braces simply end a block.
 */
package com.ict602.aeroallowance; // Places this class in the app's Java package; this must agree with the Android namespace.

import android.app.Activity; // Base class that lets this Java class act as an Android screen.
import android.animation.ValueAnimator; // Checks whether Android animations are enabled before playing custom motion.
import android.content.Intent; // Used to ask another app, such as the browser, to open the GitHub URL.
import android.graphics.Color; // Provides built-in colour values such as WHITE and TRANSPARENT.
import android.graphics.Typeface; // Controls font family and bold/normal text styles.
import android.graphics.drawable.GradientDrawable; // Draws rounded surfaces, gradients, borders, and dashed separators.
import android.graphics.drawable.RippleDrawable; // Adds Android's touch ripple effect to custom button backgrounds.
import android.graphics.drawable.StateListDrawable; // Chooses a different input background when the field gains keyboard focus.
import android.content.res.ColorStateList; // Wraps a colour for APIs that expect colours associated with widget states.
import android.content.res.Configuration; // Reads the device's current light/dark configuration.
import android.net.Uri; // Converts the repository address into a URI an Intent can open.
import android.os.Bundle; // Stores temporary screen state when Android recreates the activity.
import android.text.Editable; // The editable text type required by the TextWatcher callback.
import android.text.InputType; // Requests numeric input with a decimal separator for the two fields.
import android.text.TextWatcher; // Listens for input edits so an old estimate can be cleared.
import android.view.Gravity; // Provides alignment constants, such as vertical centring.
import android.view.View; // Base type shared by labels, buttons, layouts, and other UI elements.
import android.view.inputmethod.InputMethodManager; // Accesses the software keyboard so it can be dismissed after calculating.
import android.widget.*; // Imports Android widgets: LinearLayout, EditText, Button, TextView, Switch, and others.
import java.math.BigDecimal; // Represents decimal quantities without binary floating-point money errors.
import java.util.Locale; // Keeps RM formatting consistent, with a dot for decimals and comma grouping.

/**
 * UI map: showPage() = navigation; buildHome() = inputs; calculate() = validation;
 * renderResult() = receipt; buildAbout() = assignment identity requirements.
 * Views are built in Java so each screen and its behaviour can be learned together.
 */
public class MainActivity extends Activity { // The launcher activity declared in AndroidManifest.xml; it owns both app pages.
    private static final int INK = 0xFF102E3A, TEAL = 0xFF087F78, // Shared navy and teal constants. Hex colours use 0xAARRGGBB; FF means fully opaque.
            MUTED = 0xFF526973, PAPER = 0xFFF4F7F8, LINE = 0xFFDCE5E7; // Other light-theme base colours: secondary text, page background, and borders.
    // Semantic colours: all ordinary surfaces and text share the selected palette.
    // The navy hero and receipt keep their intentional white-on-navy branding in both modes.
    private boolean darkMode; // True means the app currently uses the dark appearance.
    private int foreground, secondary, page, surface, outline, accent, selectedFill, quietFill; // Colour roles assigned by applyPalette(); widgets use roles instead of hard-coded light colours.
    private LinearLayout content, receipt; // References to the scrolling page container and the estimate card.
    private EditText weightInput, rateInput; // References to the two numeric inputs, reused by validation and state saving.
    private Button economyButton, businessButton; // References to the cabin buttons so selection styling can change after a tap.
    private BaggageCalculator.TicketClass ticket = BaggageCalculator.TicketClass.ECONOMY; // The selected enum value; the initial cabin is economy with a 20 kg allowance.
    // Draft values survive Home/About navigation and Android activity recreation.
    private String weightDraft = "", rateDraft = ""; // Copies of field text, initially empty; strings also preserve incomplete user input.
    private boolean about = false, hasResult = false, restoring = false; // Flags: current page, valid displayed estimate, and programmatic restoration in progress.

    @Override public void onCreate(Bundle state) { // Android calls this when creating the screen; state is null on a fresh launch.
        boolean systemDark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) // Mask off unrelated configuration bits so only the night-mode setting is examined.
                == Configuration.UI_MODE_NIGHT_YES; // The comparison is true when the device is currently using a dark system appearance.
        darkMode = getSharedPreferences("appearance", MODE_PRIVATE).getBoolean("dark_mode", systemDark); // Read the locally saved choice; use systemDark only if no preference has been saved.
        // Apply a native dark theme before creating widgets, so cursor/error UI also matches.
        if (darkMode) setTheme(android.R.style.Theme_Material_NoActionBar); // Choose Android's native dark theme before creating widgets; the activity supplies its own header.
        super.onCreate(state); // Allow the Activity superclass to perform essential Android initialisation.
        applyPalette(); // Calculate this appearance's colours and apply matching system-bar styling.
        if (state != null) { // Restore an existing screen only when Android supplied a saved Bundle.
            weightDraft = state.getString("weight", ""); rateDraft = state.getString("rate", ""); // Recover both draft strings; empty defaults prevent null text when a key is missing.
            about = state.getBoolean("about"); hasResult = state.getBoolean("result"); // Recover the page and whether a valid estimate should be displayed.
            ticket = state.getBoolean("business") ? BaggageCalculator.TicketClass.BUSINESS : BaggageCalculator.TicketClass.ECONOMY; // The ternary operator chooses business if its saved flag is true; otherwise choose economy.
        }
        showPage(); // Construct the header, selected page, and bottom navigation.
    }

    private void applyPalette() { // Assign all theme colours in one place so the two appearances stay consistent.
        foreground = darkMode ? 0xFFEAF4F6 : INK; // Main text is nearly white in dark mode and navy in light mode.
        secondary = darkMode ? 0xFFB1C4CC : MUTED; // Secondary labels use a softer colour that stays readable on the active surface.
        page = darkMode ? 0xFF0B171E : PAPER; // Choose the overall page background: deep navy-black or pale grey.
        surface = darkMode ? 0xFF142730 : Color.WHITE; // Choose the normal card background: dark blue-grey or white.
        outline = darkMode ? 0xFF34505D : LINE; // Set the border colour used by cards and inactive input fields.
        accent = darkMode ? 0xFF8AE1D1 : TEAL; // Use bright mint accents on dark backgrounds and darker teal on light backgrounds.
        selectedFill = darkMode ? 0xFF116F6A : TEAL; // Choose a selected-button fill that supports white text in either appearance.
        quietFill = darkMode ? 0xFF243C47 : 0xFFE8EFF0; // Choose the subdued background for unselected cabin and navigation buttons.
        getWindow().setStatusBarColor(page); // Request a status-bar background matching the page; newer Android versions may enforce edge-to-edge.
        getWindow().setNavigationBarColor(page); // Request a navigation-bar background matching the page.
        int icons = darkMode ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR; // Despite the flag's name, LIGHT_STATUS_BAR means dark icons for a light background.
        if (!darkMode && android.os.Build.VERSION.SDK_INT >= 27) icons |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR; // Add dark navigation icons only on supported Android versions; |= combines flag bits.
        getWindow().getDecorView().setSystemUiVisibility(icons); // Apply the combined icon-brightness flags to the activity's outermost view.
    }

    @Override protected void onPostResume() { // Android calls this after the activity has resumed and restored its window state.
        super.onPostResume(); // Preserve the superclass lifecycle behaviour before applying our styling.
        // Restored window state can bring back old icon flags; reapply after restoration.
        applyPalette(); // Reapply colours and icon contrast because restored window flags may be from the old theme.
    }

    @Override protected void onSaveInstanceState(Bundle out) { // Android supplies out so this method can save temporary state before recreation.
        saveDraft(); // Read the latest input text before placing the draft strings in the Bundle.
        out.putString("weight", weightDraft); out.putString("rate", rateDraft); // Store weight and rate under matching keys later read in onCreate().
        out.putBoolean("about", about); out.putBoolean("result", hasResult); // Store navigation state and whether the receipt contains a valid result.
        out.putBoolean("business", ticket == BaggageCalculator.TicketClass.BUSINESS); // Store the selected cabin as a boolean; true represents business.
        super.onSaveInstanceState(out); // Let Android also save its standard view hierarchy and focus state.
    }

    @Override protected void onRestoreInstanceState(Bundle state) { // Runs after onCreate() when Android restores the previous view hierarchy.
        // Android restores field text after onCreate. Treat those updates as restoration,
        // otherwise TextWatcher would mistake them for edits and clear the saved receipt.
        restoring = true; // Tell our TextWatcher to ignore programmatic text restoration.
        super.onRestoreInstanceState(state); // Restore the standard Android view state while edit detection is suppressed.
        if (!about) { // Only Home has input fields that need to be restored and recalculated.
            weightInput.setText(weightDraft); rateInput.setText(rateDraft); // Restore our saved draft strings explicitly into the rebuilt fields.
            hasResult = state.getBoolean("result"); // Recover whether the previous screen had an actual calculated receipt.
            if (hasResult) calculate(); else emptyResult(); // Recalculate a saved valid result; otherwise display the empty estimate message.
        }
        restoring = false; // Allow real user edits to invalidate estimates again after restoration finishes.
    }

    private void saveDraft() { // Copy field text into memory before page changes or activity recreation.
        if (!about && weightInput != null) { // Read inputs only while on Home and after the fields have actually been created.
            weightDraft = weightInput.getText().toString(); rateDraft = rateInput.getText().toString(); // Convert each Editable into a plain String for draft storage.
        }
    }

    // Root uses system-bar insets, keeping controls clear of camera cutouts and gesture bars.
    private void showPage() { // Build the shared screen frame and insert either Home or About.
        LinearLayout root = column(); root.setBackgroundColor(page); // Create a vertical root layout and paint the current page background.
        root.setOnApplyWindowInsetsListener((view, insets) -> { // A lambda runs whenever Android reports occupied system-bar or cutout space.
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), // Reserve the reported left and top inset so the app avoids system overlays.
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom()); return insets; // Also reserve right/bottom insets; return the original insets for normal propagation.
        });
        setContentView(root); // Make this layout the activity's visible content.
        LinearLayout header = row(); header.setPadding(dp(22), dp(12), dp(22), dp(12)); // Create the horizontal header; padding values are left, top, right, and bottom in dp.
        ImageView logo = new ImageView(this); logo.setImageResource(com.ict602.aeroallowance.R.drawable.ic_app); // Create an image widget and load the app's vector icon from Android resources.
        header.addView(logo, new LinearLayout.LayoutParams(dp(38), dp(38))); // Place the logo in the header at 38 by 38 density-independent pixels.
        TextView brand = text("  AeroAllowance", 20, foreground, true); // Create the brand label: 20 sp, themed foreground colour, and bold text.
        header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1)); // Width 0 plus weight 1 gives the title remaining row space; -2 means wrap content height.
        // A labelled native switch exposes its checked state to screen readers.
        Switch appearance = new Switch(this); // Create Android's native on/off switch using this activity as its Context.
        appearance.setText("Dark  "); appearance.setTextSize(12); appearance.setTextColor(secondary); // Give the switch a readable label, 12 sp text, and the secondary text colour.
        appearance.setMinHeight(dp(48)); appearance.setChecked(darkMode); // Provide a 48 dp minimum target and reflect the current theme before adding the listener.
        appearance.setContentDescription("Dark mode"); // Give screen readers a clear description of the switch's purpose.
        appearance.setThumbTintList(ColorStateList.valueOf(darkMode ? accent : TEAL)); // Tint the movable switch thumb to fit the current appearance.
        appearance.setOnCheckedChangeListener((control, checked) -> { // Run this callback after the user changes the switch; checked is the new boolean value.
            saveDraft(); // Preserve current input text before recreating the activity for a new theme.
            // apply() remembers the setting locally; recreate() preserves our Bundle state.
            getSharedPreferences("appearance", MODE_PRIVATE).edit().putBoolean("dark_mode", checked).apply(); // Save only the appearance choice in app-private preferences; apply() schedules the disk write.
            recreate(); // Recreate the screen with the chosen native theme; Bundle callbacks preserve the current draft.
        });
        header.addView(appearance); root.addView(header); // Add the switch to the header, then the completed header to the root layout.

        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); // Create a scroll container that fills its viewport even when its page content is short.
        content = column(); content.setPadding(dp(22), dp(16), dp(22), dp(28)); // Create the page's vertical content stack with consistent outer spacing.
        scroll.addView(content); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1)); // Put content inside the scroller; height 0 plus weight 1 uses space between header and navigation.
        if (about) buildAbout(); else buildHome(); // Render About when about is true; otherwise render the calculator Home page.
        // Page changes get a small entrance; Android's global animation setting is respected.
        if (ValueAnimator.areAnimatorsEnabled()) { // Skip custom motion if Android's animation setting disables it.
            content.setAlpha(0f); content.setTranslationY(dp(6)); // Start page content transparent and 6 dp below its final position.
            content.animate().alpha(1f).translationY(0).setDuration(160).start(); // Fade to full opacity and move to the normal position over 160 milliseconds.
        }

        LinearLayout navigation = row(); navigation.setPadding(dp(16), dp(8), dp(16), dp(8)); // Create the persistent bottom navigation row with surrounding padding.
        navigation.setBackgroundColor(surface); // Use the normal surface colour so navigation matches the selected appearance.
        Button home = button("Home", !about), info = button("About", about); // Create both navigation buttons; !about selects Home when About is not active.
        navigation.addView(home, weighted()); navigation.addView(info, weighted()); root.addView(navigation); // Give the two buttons equal horizontal space, then attach the navigation row.
        home.setOnClickListener(v -> { if (about) { about = false; showPage(); } }); // A Home tap switches pages only if About is currently active.
        info.setOnClickListener(v -> { if (!about) { saveDraft(); about = true; showPage(); } }); // An About tap saves the Home draft, changes the page flag, and rebuilds the screen.
    }

    private void buildHome() { // Build the calculator's hero, form, receipt, and explanatory footer.
        // A compact gradient hero gives the calculator an airline-inspired visual identity.
        LinearLayout hero = card(content, INK); // Create and attach the navy hero card to the scrolling content.
        hero.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, // Replace its solid fill with a gradient running from top-left to bottom-right.
                new int[]{INK, 0xFF18555A})); // These are the gradient's starting navy and ending teal colours.
        ((GradientDrawable)hero.getBackground()).setCornerRadius(dp(24)); // Round the replacement gradient; replacing a background does not keep the old corners automatically.
        hero.addView(text("BEFORE YOU BOARD", 11, 0xFF8AE1D1, true)); // Display the small mint eyebrow label inside the hero.
        gap(hero, 10); // Leave 10 dp of vertical space in hero before the next element.
        hero.addView(text("A little extra bag.\nNo extra surprises.", 28, Color.WHITE, true)); // Display the two-line headline; the escaped newline starts the second line.
        gap(hero, 10); // Leave 10 dp of vertical space in hero before the next element.
        hero.addView(text("Know your baggage fee before you fly.", 14, 0xFFD4E4E8, false)); // Add a quieter supporting sentence below the headline.
        gap(hero, 18); // Leave 18 dp of vertical space in hero before the next element.
        hero.addView(chip("20 kg Economy   /   40 kg Business", 0xFF234F57, 0xFFD8F8F0)); // Show both included allowances in a padded badge for quick comparison.
        gap(content, 20); // Leave 20 dp of vertical space in content before the next element.
        LinearLayout form = card(content, surface); // Create the themed card that will contain the form inputs and buttons.
        form.addView(text("01  Your baggage", 20, foreground, true)); // Add a bold section heading to identify the first step.
        form.addView(text("Choose your cabin and enter your details.", 14, secondary, false)); // Explain what information the user should enter in this card.
        gap(form, 18); // Leave 18 dp of vertical space in form before the next element.
        form.addView(text("Ticket class", 14, foreground, true)); // Label the cabin selection controls.
        LinearLayout choices = row(); // Create a horizontal row so the two cabin options appear side by side.
        economyButton = button("Economy\n20 kg included", ticket == BaggageCalculator.TicketClass.ECONOMY); // Create the economy button, initially styled according to the selected enum.
        businessButton = button("Business\n40 kg included", ticket == BaggageCalculator.TicketClass.BUSINESS); // Create the business button with its 40 kg allowance and initial selection style.
        choices.addView(economyButton, weighted()); choices.addView(businessButton, weighted()); form.addView(choices); // Add equal-width cabin buttons to their row, then add the row to the form.
        updateCabinButtons(); // Apply the selected cabin's checkmark and consistent colours.
        economyButton.setOnClickListener(v -> selectClass(BaggageCalculator.TicketClass.ECONOMY)); // An economy tap passes the ECONOMY enum to the shared selection handler.
        businessButton.setOnClickListener(v -> selectClass(BaggageCalculator.TicketClass.BUSINESS)); // A business tap passes the BUSINESS enum to the same handler.
        gap(form, 12); // Leave 12 dp of vertical space in form before the next element.
        weightInput = input(form, "Checked baggage weight (kg)", "e.g. 25 or 42.5", weightDraft); // Create a labelled weight field, show an example hint, and prefill its saved draft.
        rateInput = input(form, "Excess baggage rate (RM / kg)", "e.g. 15.00", rateDraft); // Create the labelled RM/kg rate field using the same reusable input helper.
        form.addView(text("Use the rate provided for your flight. Zero is allowed.", 12, secondary, false)); // Explain that the rate comes from the user's flight and may legally be zero.
        Button calculate = button("Calculate my fee  →", true); form.addView(calculate, full()); // Create the main action button and place it across the form's available width.
        calculate.setOnClickListener(v -> calculate()); // Connect the button tap to calculate(); no mathematics runs until this handler is called.
        Button reset = button("Reset details", false); form.addView(reset, full()); // Create a quieter full-width Reset button beneath Calculate.
        reset.setOnClickListener(v -> { // Start the callback that clears the form after a Reset tap.
            weightInput.setText(""); rateInput.setText(""); weightInput.setError(null); rateInput.setError(null); // Clear both input strings and remove any previous validation error messages.
            selectClass(BaggageCalculator.TicketClass.ECONOMY); weightInput.requestFocus(); // Restore economy selection and move keyboard focus back to the weight field.
            animateConfirmation(reset); // Play the short button-settle animation to confirm the reset tap.
            Toast.makeText(this, "Details cleared", Toast.LENGTH_SHORT).show(); // Display a brief Android toast so the user knows the form was cleared.
        });
        gap(content, 20); // Leave 20 dp of vertical space in content before the next element.
        receipt = card(content, INK); // Create the navy receipt card and retain its reference for later result updates.
        if (hasResult) { // When returning to a saved valid estimate, rebuild its receipt instead of showing an empty state.
            restoring = true; calculate(); restoring = false; // Recalculate silently while restoring, then re-enable ordinary user-action feedback.
        } else emptyResult(); // For a new or invalidated form, display the empty receipt instructions.
        gap(content, 16); // Leave 16 dp of vertical space in content before the next element.
        paragraph("For assignment estimates • 8% assumed service tax.\nActual airline fees and rules may differ."); // Explain the assignment's assumed tax and that actual airline rules may differ.
        // Editing any input invalidates the old receipt; a stale estimate is never presented as current.
        TextWatcher watcher = new TextWatcher() { // Create one listener object shared by both input fields.
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { } // Required callback before text changes; no work is needed at this stage.
            public void onTextChanged(CharSequence s, int start, int before, int count) { // This callback receives each edit, including typing, pasting, and programmatic setText calls.
                if (!restoring) { hasResult = false; emptyResult(); } // Only real edits clear the old estimate; state restoration must preserve it.
            }
            public void afterTextChanged(Editable e) { } // Required callback after text changes; validation is intentionally deferred until Calculate.
        };
        weightInput.addTextChangedListener(watcher); rateInput.addTextChangedListener(watcher); // Attach the same listener to weight and rate, so editing either invalidates the receipt.
    }

    private void selectClass(BaggageCalculator.TicketClass selected) { // Handle a cabin selection using the strongly typed TicketClass enum.
        // Re-tapping the active cabin confirms it without clearing an already valid estimate.
        if (ticket == selected) { // Detect a tap on the cabin that is already selected.
            animateConfirmation(selected == BaggageCalculator.TicketClass.ECONOMY ? economyButton : businessButton); // Animate the matching button while leaving the existing selection and estimate unchanged.
            return; // Exit early because the allowance has not changed.
        }
        ticket = selected; // Store the newly chosen cabin; its enum value carries the corresponding allowance.
        updateCabinButtons(); // Refresh both buttons' colours, selected state, and checkmark.
        hasResult = false; emptyResult(); // Discard the previous estimate because a different allowance changes the calculation.
        Button active = selected == BaggageCalculator.TicketClass.ECONOMY ? economyButton : businessButton; // Keep a reference to the newly selected button for animation and spoken feedback.
        animateConfirmation(active); // Play a brief settle animation on the chosen option.
        active.announceForAccessibility(selected == BaggageCalculator.TicketClass.ECONOMY // Ask Android accessibility to announce the selected cabin.
                ? "Economy selected. 20 kilograms included." : "Business selected. 40 kilograms included."); // Choose a spoken message that includes the correct included weight.
    }

    private void updateCabinButtons() { // Synchronise both cabin buttons with the selected ticket enum.
        boolean economy = ticket == BaggageCalculator.TicketClass.ECONOMY; // Use a short boolean name for the repeated economy-selection condition.
        styleButton(economyButton, economy); styleButton(businessButton, !economy); // Style economy using the boolean and business using its opposite.
        economyButton.setText((economy ? "✓ " : "") + "Economy\n20 kg included"); // Add the checkmark only when economy is selected; keep its allowance on the second line.
        businessButton.setText((economy ? "" : "✓ ") + "Business\n40 kg included"); // Add the checkmark only when business is selected.
    }

    /** A short settle animation confirms taps; it never delays the actual state change. */
    private void animateConfirmation(View target) { // Animate any supplied View to confirm a tap without delaying its action.
        if (!ValueAnimator.areAnimatorsEnabled()) return; // Return immediately when the device has disabled animations.
        target.animate().cancel(); // Cancel an earlier animation so rapid taps do not queue competing animations.
        target.setScaleX(0.96f); target.setScaleY(0.96f); // Begin at 96 percent of normal width and height; f marks float literals.
        target.animate().scaleX(1f).scaleY(1f).setDuration(180) // Animate both scale values back to 1.0 over 180 milliseconds.
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start(); // Slow down near the end for a gentle settling effect, then start the animation.
    }

    private BigDecimal readNumber(EditText field, String name, int decimals) { // Validate one field and return its BigDecimal, or null if invalid; name customises its error.
        String raw = field.getText().toString().trim(); // Read the field and remove whitespace at its beginning and end.
        // Deliberate length and precision limits prevent unreasonable values and ambiguous rounding.
        if (!raw.matches("[0-9]{1,6}(\\.[0-9]{1," + decimals + "})?")) { // Require 1-6 digits, optionally a dot and 1-decimals digits; reject signs, letters, and extra dots.
            field.setError("Enter " + name + " from 0 to 999999 with up to " + decimals + " decimal places."); // Show a field-specific message explaining the accepted input format.
            return null; // Signal validation failure to calculate(); do not try to parse invalid text.
        }
        field.setError(null); return new BigDecimal(raw); // Clear the old error and construct a decimal directly from the valid string.
    }

    private void calculate() { // Connect the form to the separate calculator class, then present the returned result.
        BigDecimal weight = readNumber(weightInput, "a weight", 3); // Validate weight and permit up to three digits after the decimal point.
        BigDecimal rate = readNumber(rateInput, "a rate", 2); // Validate the RM rate and permit up to two digits after the decimal point.
        if (weight == null || rate == null) { // Stop if either validation returned null; || means either condition can be true.
            hasResult = false; emptyResult(); (weight == null ? weightInput : rateInput).requestFocus(); return; // Clear the receipt, focus the first invalid input, and leave without calculating.
        }
        BaggageCalculator.Result result = BaggageCalculator.calculate(weight, ticket, rate); // Pass the two valid decimals and selected cabin to the actual assignment formula implementation.
        hasResult = true; renderResult(result, weight, rate); // Mark the estimate as valid and fill the receipt with its result.
        if (!restoring) { // Run keyboard, speech, and scrolling feedback only for a user calculation, not restoration.
            ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(weightInput.getWindowToken(), 0); // Ask Android's input service to hide the software keyboard associated with this window.
            receipt.announceForAccessibility("Total payable " + money(result.total)); // Read the formatted total aloud when an accessibility service is listening.
            receipt.post(() -> receipt.requestRectangleOnScreen(new android.graphics.Rect(0, 0, receipt.getWidth(), receipt.getHeight()), false)); // Postpone the scroll request until layout is ready, then ask the scroller to reveal the receipt.
        }
    }

    private void emptyResult() { // Display the initial/invalidated receipt state without monetary results.
        if (receipt == null) return; // Guard against calls before the receipt card exists.
        receipt.removeAllViews(); receipt.addView(text("02  Your estimate", 20, Color.WHITE, true)); // Remove previous rows and add the receipt section heading again.
        gap(receipt, 14); receipt.addView(text("Ready when you are.", 24, 0xFF8AE1D1, true)); // Leave space, then show the mint empty-state heading.
        gap(receipt, 8); receipt.addView(text("Enter your baggage details to see a full fee breakdown here.", 15, 0xFFD4E4E8, false)); // Leave space, then explain how to obtain a fee breakdown.
    }

    private void renderResult(BaggageCalculator.Result r, BigDecimal weight, BigDecimal rate) { // Build the receipt from a Result plus the original weight and rate for explanation.
        receipt.removeAllViews(); receipt.addView(text("02  Your estimate", 20, Color.WHITE, true)); // Replace all old receipt contents before showing the new result.
        gap(receipt, 12); // Leave 12 dp of vertical space in receipt before the next element.
        receipt.addView(chip(r.excess.signum() == 0 ? "WITHIN ALLOWANCE" : "EXCESS BAGGAGE", 0xFF234F57, 0xFFB7F2E3)); // signum()==0 detects zero excess; select the appropriate status badge.
        gap(receipt, 14); receipt.addView(text("TOTAL PAYABLE", 12, 0xFF8AE1D1, true)); // Add a small caption above the main monetary amount.
        receipt.addView(text(money(r.total), 38, Color.WHITE, true)); // Display the final formatted total prominently in 38 sp bold white text.
        receipt.addView(text(r.excess.signum() == 0 ? "Within your allowance. No extra fee." : "Includes the assumed 8% service tax", 14, 0xFFD4E4E8, false)); // Explain either that no fee applies or that the assumed service tax is included.
        gap(receipt, 18); receiptDivider(); gap(receipt, 10); // Separate the total from the itemised breakdown using spacing and a dashed rule.
        receiptLine("Baggage weight", number(weight) + " kg"); // Show the entered weight in kilograms using compact decimal formatting.
        receiptLine("Included allowance", number(ticket.allowance) + " kg"); // Show the allowance belonging to the selected cabin, not a second independent value.
        receiptLine("Excess weight", number(r.excess) + " kg"); // Show the calculator's clamped, nonnegative excess in kilograms.
        receiptLine("Rate per kg", money(rate)); // Show the entered charge per kilogram as a currency amount.
        receiptLine("Excess baggage charge", money(r.charge)); // Show the excess baggage charge before tax.
        receiptLine("Service tax (8%)", money(r.tax)); // Show the calculated service tax as a separate currency line.
        gap(receipt, 12); receiptDivider(); gap(receipt, 16); // Visually separate the receipt rows from the learning explanation below.
        receipt.addView(text("HOW IT WORKS", 11, 0xFF8AE1D1, true)); // Add the heading for the visible formula explanation.
        receipt.addView(text("Excess = max(0, " + number(weight) + " − " + number(ticket.allowance) + ")\nCharge = " + number(r.excess) + " × " + money(rate) + "\nTotal = charge + (charge × 0.08)", 13, 0xFFD4E4E8, false)); // Show the actual weight/allowance subtraction and multiplication; this displays formulas without recalculating them.
        // A short fade confirms the update without distracting from the numbers.
        if (!restoring && ValueAnimator.areAnimatorsEnabled()) { // Only animate a fresh user result when system animations are enabled.
            receipt.animate().cancel(); receipt.setAlpha(0.65f); receipt.setTranslationY(dp(8)); // Cancel any previous receipt animation and set its initial opacity and vertical offset.
            receipt.animate().alpha(1f).translationY(0).setDuration(180).start(); // Fade and slide the receipt into place in 180 milliseconds.
        }
    }

    private void receiptLine(String name, String value) { // Append one label/value pair to the receipt; name is left-aligned and value follows on the right.
        LinearLayout line = row(); line.setPadding(0, dp(7), 0, dp(7)); // Create a horizontal row with 7 dp of space above and below its contents.
        line.addView(text(name, 14, 0xFFD4E4E8, false), new LinearLayout.LayoutParams(0, -2, 1)); // Give the label the remaining width using weight 1, leaving space for the value.
        line.addView(text(value, 14, Color.WHITE, true)); receipt.addView(line); // Add the bold white value, then append the completed row to the receipt.
    }

    private void buildAbout() { // Build the assignment's About information, portrait, source link, and copyright.
        label("BEHIND THE APP"); content.addView(text("Built for a smoother\nstart to your journey.", 30, foreground, true)); // Add the About eyebrow label and its two-line introductory heading.
        gap(content, 24); // Leave 24 dp of vertical space in content before the next element.
        LinearLayout identity = card(content, surface); // Create the identity card using the current theme's normal surface colour.
        ImageView icon = new ImageView(this); icon.setImageResource(R.drawable.ic_app); // Load the app icon, which remains separate from the author's portrait.
        icon.setContentDescription("AeroAllowance suitcase and aircraft application icon"); // Describe the icon for screen-reader users.
        identity.addView(icon, new LinearLayout.LayoutParams(dp(80), dp(80))); // Display the application icon at 80 by 80 dp as required by the assignment.
        gap(identity, 14); identity.addView(text("AeroAllowance", 26, foreground, true)); // Add space below the icon, then display the app name.
        identity.addView(text("Version 1.0 • Excess baggage estimator", 14, secondary, false)); // Display the app version and its purpose in secondary text.
        gap(identity, 20); // Leave 20 dp of vertical space in identity before the next element.
        // Display the supplied photo directly; Android crops only its view into a circle.
        LinearLayout author = row(); // Create a row to hold the portrait beside the author's name.
        ImageView portrait = new ImageView(this); // Create the image widget that displays the provided portrait resource.
        portrait.setImageResource(R.drawable.author_portrait); // Load the unchanged bundled image from drawable-nodpi/author_portrait.png.
        portrait.setScaleType(ImageView.ScaleType.CENTER_CROP); // Scale proportionally until the square is filled; crop extra edges without stretching the face.
        portrait.setContentDescription("Portrait of " + AuthorConfig.NAME); // Identify whose portrait this is to screen readers.
        portrait.setBackground(background(surface, 52)); // Give the portrait a themed rounded background beneath the image.
        portrait.setOutlineProvider(new android.view.ViewOutlineProvider() { // Provide the clipping outline that makes the square image appear circular.
            @Override public void getOutline(View view, android.graphics.Outline shape) { // Android calls this when it needs the view's current outline geometry.
                shape.setOval(0, 0, view.getWidth(), view.getHeight()); // An oval covering an equal-width/equal-height view becomes a circle.
            }
        });
        portrait.setClipToOutline(true); // Clip the visible image to the oval outline; the original photo file is not edited.
        author.addView(portrait, new LinearLayout.LayoutParams(dp(96), dp(96))); // Give the portrait equal 96 dp width and height in the author row.
        LinearLayout authorDetails = column(); // Stack the AUTHOR label above the author's full name.
        authorDetails.addView(text("AUTHOR", 11, accent, true)); // Show the small themed author label.
        gap(authorDetails, 6); // Leave 6 dp of vertical space in authorDetails before the next element.
        authorDetails.addView(text(AuthorConfig.NAME, 17, foreground, true)); // Read the name from AuthorConfig rather than duplicating personal details here.
        LinearLayout.LayoutParams authorLayout = new LinearLayout.LayoutParams(0, -2, 1); // Let the text column take remaining row width and wrap to its required height.
        authorLayout.leftMargin = dp(16); // Leave 16 dp between the circular photo and the text column.
        author.addView(authorDetails, authorLayout); identity.addView(author); // Attach the name column beside the portrait, then attach the completed author row to the card.
        gap(identity, 12); // Leave 12 dp of vertical space in identity before the next element.
        identity.addView(text("MATRIC NUMBER", 11, accent, true)); identity.addView(text(AuthorConfig.MATRIC, 17, foreground, false)); // Display the matric label and configured matric number together.
        gap(identity, 12); // Leave 12 dp of vertical space in identity before the next element.
        identity.addView(text("COURSE", 11, accent, true)); identity.addView(text(AuthorConfig.COURSE, 17, foreground, false)); // Display the course label and configured course/class information.
        gap(content, 20); // Leave 20 dp of vertical space in content before the next element.
        LinearLayout project = card(content, surface); // Create a second card for the source-code link.
        project.addView(text("Open the source", 20, foreground, true)); // Add the repository card's title.
        project.addView(text("Explore the calculation logic, interface, and learning notes on GitHub.", 15, secondary, false)); // Explain what the user will find in the source repository.
        Button github = button("View GitHub repository  ↗", true); project.addView(github, full()); // Create the external-link button and make it fill the card's available width.
        boolean configured = AuthorConfig.GITHUB_URL.startsWith("https://github.com/"); // Treat the link as configured only when its text begins with the expected GitHub HTTPS prefix.
        github.setEnabled(configured); github.setAlpha(configured ? 1f : 0.5f); // Disable and dim an unconfigured link; enable a configured one at full opacity.
        project.addView(text(configured ? AuthorConfig.GITHUB_URL : "Repository link coming soon.", 13, secondary, false)); // Show the actual URL when available; otherwise show a readable pending-link message.
        github.setOnClickListener(v -> { // Start the callback for opening the configured repository.
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(AuthorConfig.GITHUB_URL))); } // Parse the URL and ask Android to launch an app that can view it, normally a browser.
            catch (android.content.ActivityNotFoundException ex) { Toast.makeText(this, "No browser is installed.", Toast.LENGTH_LONG).show(); } // If no app can handle that request, show an explanation instead of crashing.
        });
        gap(content, 22); // Leave 22 dp of vertical space in content before the next element.
        paragraph("Economy allowance: 20 kg • Business allowance: 40 kg. Calculations use the assignment’s assumed 8% service tax."); // Restate the assignment's two allowances and assumed tax for reference.
        paragraph("© " + java.time.Year.now().getValue() + " " + AuthorConfig.NAME + ". All rights reserved.\nCreated for the ICT602 individual assignment."); // Build the copyright from the current year and configured author name.
    }

    // Small reusable UI helpers keep spacing, typography, and colours consistent across screens.
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); } // Convert a dp design measurement to physical pixels using device density, rounding to an integer.
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; } // Create and return a LinearLayout whose children stack vertically.
    private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setGravity(Gravity.CENTER_VERTICAL); return l; } // Create a horizontal LinearLayout (its default orientation), vertically centre its children, and return it.
    private TextView text(String value, int size, int colour, boolean bold) { // Create a reusable label: value is text, size is sp, colour is ARGB, and bold selects its weight.
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(colour); // Create the TextView and assign its content, scaled text size, and colour.
        t.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL)); // Choose the sans-serif family and either bold or normal weight.
        t.setLineSpacing(dp(3), 1f); return t; // Add 3 dp between lines without multiplying their height, then return the label.
    }
    private void label(String value) { content.addView(text(value, 11, accent, true)); gap(content, 8); } // Append a small bold accent label to content, followed by 8 dp of vertical space.
    // Small badges use generous padding and readable contrast, rather than tiny decorative text.
    private TextView chip(String value, int fill, int colour) { // Create and return a small padded badge using the supplied fill and text colours.
        TextView badge = text(value, 10, colour, true); // Start with the shared text helper so badge typography stays consistent.
        badge.setPadding(dp(10), dp(6), dp(10), dp(6)); // Add horizontal and vertical breathing room inside the badge.
        badge.setBackground(background(fill, 10)); return badge; // Apply a rounded fill with 10 dp corners, then return the completed badge.
    }
    private void receiptDivider() { // Append a subtle dashed separator to the dark receipt.
        View divider = new View(this); // Create a plain View used only as a decorative separator.
        GradientDrawable dash = background(Color.TRANSPARENT, 0); // Start with a transparent drawable and square corners.
        dash.setStroke(dp(1), 0xFF47626C, dp(5), dp(4)); // Use a 1 dp muted stroke with 5 dp dashes separated by 4 dp gaps.
        divider.setBackground(dash); receipt.addView(divider, new LinearLayout.LayoutParams(-1, dp(1))); // Apply the drawable and insert the divider at full width with a 1 dp height.
    }
    private void paragraph(String value) { gap(content, 9); content.addView(text(value, 14, secondary, false)); } // Append 9 dp of space and a 14 sp secondary-colour paragraph to the page.
    private void gap(LinearLayout parent, int height) { View v = new View(this); parent.addView(v, new LinearLayout.LayoutParams(1, dp(height))); } // Insert an empty 1-pixel-wide View with the requested dp height to space vertical children.
    private GradientDrawable background(int colour, int radius) { // Create a rounded rectangle drawable from an ARGB fill colour and a corner radius in dp.
        GradientDrawable d = new GradientDrawable(); d.setColor(colour); d.setCornerRadius(dp(radius)); return d; // Set the drawable's fill and converted corner radius, then return it for use as a background.
    }
    private LinearLayout card(LinearLayout parent, int colour) { // Create, attach, and return a padded vertical card inside the supplied parent layout.
        LinearLayout box = column(); box.setPadding(dp(20), dp(22), dp(20), dp(22)); // Create the card's content column with 20 dp horizontal and 22 dp vertical padding.
        GradientDrawable surface = background(colour, 24); // Build its rounded background; this local surface drawable is different from the theme's int field.
        if (colour == this.surface) surface.setStroke(dp(1), outline); // this.surface explicitly references the field; add a border only to normal themed cards.
        box.setBackground(surface); parent.addView(box, new LinearLayout.LayoutParams(-1, -2)); return box; // Apply the background, attach the full-width/wrap-height card, and return it to the caller.
    }
    private LinearLayout.LayoutParams full() { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(10); return p; } // Return full-width, wrap-height layout parameters (-1/-2) with a 10 dp top margin.
    private LinearLayout.LayoutParams weighted() { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1); p.setMargins(dp(3), dp(6), dp(3), dp(6)); return p; } // Return equal-share row parameters (width 0, weight 1) and small surrounding margins.
    private Button button(String value, boolean selected) { // Create a standard reusable button; selected determines its initial colours.
        Button b = new Button(this); b.setText(value); b.setAllCaps(false); b.setTextSize(14); // Create the button, set its label, preserve mixed-case text, and use a 14 sp size.
        b.setMinHeight(dp(52)); b.setPadding(dp(10), dp(10), dp(10), dp(10)); styleButton(b, selected); return b; // Set a 52 dp minimum height and padding, apply selection styling, and return the button.
    }
    private void styleButton(Button b, boolean selected) { // Apply shared colours, touch feedback, and typography to an existing button.
        b.setTextColor(selected ? Color.WHITE : foreground); // Selected buttons use white text; unselected buttons use the theme's foreground colour.
        // Native ripple preserves visible touch feedback on our custom rounded buttons.
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x227FFFFF), // Wrap the rounded fill in an Android ripple with a translucent touch-highlight colour.
                background(selected ? selectedFill : quietFill, 14), null)); // Choose active or quiet fill with 14 dp corners; null means no separate ripple mask is supplied.
        b.setStateListAnimator(null); b.setElevation(0); // Remove the default state/elevation animation and raised shadow to keep the flat design.
        b.setSelected(selected); b.setTypeface(null, Typeface.BOLD); // Expose selected state to Android and make the label bold.
    }
    private EditText input(LinearLayout parent, String title, String hint, String draft) { // Create a labelled numeric field with a hint and optional saved draft, then return the field.
        TextView label = text(title, 14, foreground, true); parent.addView(label); gap(parent, 7); // Create and attach the field label, then leave 7 dp before its input.
        EditText e = new EditText(this); e.setId(View.generateViewId()); label.setLabelFor(e.getId()); // Generate a unique view ID and link the label to this field for accessibility.
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL); // Request a numeric keyboard that permits decimals; readNumber() still performs actual validation.
        e.setSingleLine(true); e.setTextSize(18); e.setTextColor(foreground); e.setHintTextColor(secondary); // Use one line, 18 sp text, and themed colours for entered text and hints.
        e.setHint(hint); e.setText(draft); e.setPadding(dp(14), dp(12), dp(14), dp(12)); // Set the placeholder, restore any draft, and pad the inside of the field.
        // A stronger teal border makes keyboard focus clear without changing the field size.
        GradientDrawable border = background(page, 12); border.setStroke(dp(1), outline); // Create the inactive field background with a 1 dp outline.
        GradientDrawable focused = background(surface, 12); focused.setStroke(dp(2), accent); // Create a themed focused background with a stronger 2 dp accent outline.
        StateListDrawable states = new StateListDrawable(); // Create a drawable selector that can switch background according to view state.
        states.addState(new int[]{android.R.attr.state_focused}, focused); // Register the focused rule first so it takes priority when focus is present.
        states.addState(new int[]{}, border); e.setBackground(states); // Use the normal border as the fallback rule and assign the selector to the field.
        parent.addView(e, new LinearLayout.LayoutParams(-1, -2)); gap(parent, 16); return e; // Attach the full-width field, leave 16 dp after it, and return its reference.
    }
    private String number(BigDecimal value) { return value.stripTrailingZeros().toPlainString(); } // Format a decimal without unnecessary trailing zeros or scientific notation (for example, 2.500 becomes 2.5).
    private String money(BigDecimal value) { return String.format(Locale.US, "RM %,.2f", value); } // Format RM with grouping and exactly two decimal places; calculation rounding happens in BaggageCalculator.
}

package com.example.richhealth.Activities;
import Utils.Utilities;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import com.example.richhealth.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.razorpay.PaymentData;
import com.razorpay.PaymentResultWithDataListener;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import Utils.BiometricHelper;
import Utils.PaymentManager;

public class MainActivity extends AppCompatActivity implements PaymentResultWithDataListener {
    private static final String TAG = "MainActivity";
    private PaymentManager paymentManager;
    private TokenManager tokenManager;
    private SharedPreferences sharedPreferences;
    private BottomNavigationView bottomNav;

    // Double-back-to-exit state
    private static final long BACK_PRESS_EXIT_INTERVAL = 2000; // ms between the two presses
    private long lastBackPressTime = 0;
    private Toast backPressToast;

    // Biometric lock state
    private boolean biometricVerified = false;
    private boolean waitingForBiometric = false;
    private View biometricOverlay;
    private android.widget.TextView biometricReasonView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // Edge-to-edge: draw under the system bars; our own inset listener (setupKeyboardInsets,
        // applied in onResume) handles all padding. See setupKeyboardInsets for the keyboard fix.
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        bottomNav = findViewById(R.id.bottom_navigation);
        // NOTE: system-bar inset handling (edge-to-edge, targetSdk 35+) is applied globally
        // for every Activity in MyApplication via ActivityLifecycleCallbacks — no per-screen code.

        // Initialize TokenManager safely
        Context appContext = getApplicationContext();
        if (appContext != null) {
            tokenManager = TokenManager.getInstance(appContext);
        } else {
            Log.e("MainActivity", "Application context is null. TokenManager not initialized.");
            return;
        }

        // Log login status
        Log.d("MainActivity", "Checking Login Status");
        boolean isLoggedIn = tokenManager.isLoggedIn();

        Log.d("MainActivity", "Is Logged In: " + isLoggedIn);

        // This used to only log. SplashActivity is not the only way in — a medication
        // notification starts MainActivity directly (MedicationReminderReceiver), and any
        // future entry point would land here too — so the destination gates itself.
        if (!isLoggedIn) {
            Log.e("MainActivity", "Reached MainActivity without a session — returning to login");
            startActivity(new Intent(this, LoginActivity.class)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            finish();
            return;
        }

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences(TokenManager.PREF_NAME, Context.MODE_PRIVATE);

        // Optional: Attempt to refresh token if it's close to expiration
        if (isTokenCloseToExpiration()) {
            tokenManager.refreshToken(this);
        }

        // Remove top action bar
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        if (bottomNav == null) {
            Utilities.toastLong(this, "bottomNav is null - layout issue");
            return; // prevent crash
        }
        setupBottomNavigation();
        bottomNav.setOnItemSelectedListener(item -> {
            // Spring-bounce the tapped icon
            animateNavItemSelection(item.getItemId());

            Fragment selectedFragment = null;
            boolean preserveMediaPlayback = true;
            // if/else instead of switch: since AGP 8 the R.id fields are non-final
            // (android.nonFinalResIds defaults to true), so they can't be switch/case labels.
            int itemId = item.getItemId();
            if (itemId == R.id.navigation_home) {
                selectedFragment = new ServicesFragment();
            } else if (itemId == R.id.navigation_tools) {
                selectedFragment = new HealthDataFragment();
            } else if (itemId == R.id.navigation_ai) {
                selectedFragment = new AIFragment();
            } else if (itemId == R.id.navigation_profile) {
                selectedFragment = new ProfileFragment();
            }

            if (selectedFragment != null) {
                getSupportFragmentManager().beginTransaction()
                        .setReorderingAllowed(true)
                        .replace(R.id.fragment_container, selectedFragment)
                        .commit();
            }
            return true;
        });



        if (savedInstanceState == null) {
            try {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new AIFragment())
                        .commit();
                bottomNav.setSelectedItemId(R.id.navigation_ai);
            } catch (Exception e) {
                Utilities.toastLong(this, "Crash in fragment: " + e.getMessage());
                Log.e("MainActivity", "Fragment crash", e);
            }
        }

        handleNavigateIntent(getIntent());

        // If launched from LoginActivity after fresh authentication, skip biometric
        // for this session — user just proved identity with password.
        if (getIntent() != null && getIntent().getBooleanExtra("skip_biometric", false)) {
            biometricVerified = true;
            getIntent().removeExtra("skip_biometric"); // one-time use
        }
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleNavigateIntent(intent);
    }

    private void handleNavigateIntent(android.content.Intent intent) {
        if (intent == null) return;
        String navigateTo = intent.getStringExtra("navigate_to");
        if ("profile".equals(navigateTo)) {
            intent.removeExtra("navigate_to");
            bottomNav.setSelectedItemId(R.id.navigation_profile);
        } else if ("medications".equals(navigateTo)) {
            // Switch to Health Hub; HealthDataFragment reads this same extra on resume, opens the
            // Medications panel, and clears it. (Extra left in place so the fragment can consume it.)
            bottomNav.setSelectedItemId(R.id.navigation_tools);
        } else if (Utils.FamilyNotificationHelper.NAV_FAMILY_REQUESTS.equals(navigateTo)) {
            // A tapped family-request notification. Profile is where the requests sheet lives
            // (the only place the user can accept or decline), so land there and leave the
            // extra in place for ProfileFragment.onResume() to consume and open the sheet.
            bottomNav.setSelectedItemId(R.id.navigation_profile);
        } else if (Utils.FamilyNotificationHelper.NAV_FAMILY.equals(navigateTo)) {
            // Accepted / declined / dependency-removed: nothing to act on, so open the Family
            // panel in Health Hub where the connection itself is shown. Same hand-off as
            // "medications" — HealthDataFragment.onResume() consumes the extra.
            bottomNav.setSelectedItemId(R.id.navigation_tools);
        }
    }
    @Override
    protected void onResume() {
        super.onResume();
        // Re-assert our keyboard/inset handling. MyApplication attaches a *global* inset
        // listener in onActivityCreated (which runs after this Activity's onCreate), so we
        // override it here, once that global setup is guaranteed to be in place.
        setupKeyboardInsets();
        // Lock whenever the user asked for a lock. This used to also require
        // canAuthenticate(), which was the hole: with the sensor unavailable — broken,
        // busy, or biometrics removed since setup — the lock simply never engaged and the
        // health record opened straight up. Availability is now handled INSIDE the lock,
        // where the answer is "explain and offer a way out", never "show the data".
        if (BiometricHelper.isBiometricEnabled(this)
                && !biometricVerified && !waitingForBiometric) {
            showBiometricLock();
        }
        // Refresh Pro status on foreground so a family-Pro coverage change made while the app was
        // backgrounded fires its local notification on return (ProStatusManager detects the flip).
        Utils.ProStatusManager.syncProStatusOnLogin(this);
    }

    /**
     * Keyboard / edge-to-edge inset handling for this bottom-nav host.
     *
     * The bug this fixes: MyApplication globally sets SOFT_INPUT_ADJUST_RESIZE (framework
     * shrinks the window for the keyboard) AND also pads the content by the IME height. On
     * targetSdk 35+ both take effect, so the keyboard height is subtracted twice — the fragment
     * collapses and the chat input disappears behind a black gap, with the bottom nav stranded
     * mid-screen.
     *
     * Fix: under edge-to-edge (targetSdk 35+) the framework does NOT resize the window for the
     * keyboard, so WE lift the content by padding the bottom by the IME height (or the nav bar,
     * whichever is taller). To avoid the original double-count we also hide the bottom nav + its
     * divider while the keyboard is up (a visible BottomNavigationView re-applies the IME inset as
     * its own bottom padding, which was ballooning it into the black gap). Scoped to MainActivity
     * only — no other screen changes.
     */
    private void setupKeyboardInsets() {
        if (getWindow() != null) {
            getWindow().setSoftInputMode(
                    android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        final View content = findViewById(android.R.id.content);
        if (content == null) return;
        final View border = findViewById(R.id.border_view);
        ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            boolean imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
            int navVis = imeVisible ? View.GONE : View.VISIBLE;
            if (bottomNav != null) bottomNav.setVisibility(navVis);
            if (border != null) border.setVisibility(navVis);
            return insets;
        });
        ViewCompat.requestApplyInsets(content);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // When app goes to background, require re-verification next time.
        // BUT: don't reset while the biometric prompt is showing — the system
        // prompt itself can trigger onPause/onStop on some devices, which would
        // create an infinite re-prompt loop.
        if (BiometricHelper.isBiometricEnabled(this) && !waitingForBiometric) {
            biometricVerified = false;
        }
    }

    private void showBiometricLock() {
        // The overlay carries its own controls. It used to be a bare View whose only
        // affordance was an undiscoverable tap-to-retry, which is why a failed prompt had
        // to fail open — there was no other way out. With Unlock and Sign out on the lock
        // itself, failing closed costs the user nothing.
        if (biometricOverlay == null) {
            final float d = getResources().getDisplayMetrics().density;
            android.widget.LinearLayout lock = new android.widget.LinearLayout(this);
            lock.setOrientation(android.widget.LinearLayout.VERTICAL);
            lock.setGravity(android.view.Gravity.CENTER);
            lock.setBackgroundColor(Color.parseColor("#F0121212"));
            lock.setClickable(true);   // block touches to the content behind it
            lock.setFocusable(true);
            // CRITICAL: raise the overlay above the elevated input bar / bottom nav.
            // Without this, elevation-0 overlay covered only the (elevation-0) message
            // list while the raised input bar poked through — so the chat area looked
            // dead but the input still worked. A huge elevation makes it a true full lock.
            lock.setElevation(1_000_000f);
            lock.setPadding((int) (32 * d), (int) (32 * d), (int) (32 * d), (int) (32 * d));

            android.widget.TextView title = new android.widget.TextView(this);
            title.setText("RichHealth is locked");
            title.setTextColor(Color.WHITE);
            title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 18f);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            title.setGravity(android.view.Gravity.CENTER);
            lock.addView(title);

            biometricReasonView = new android.widget.TextView(this);
            biometricReasonView.setTextColor(Color.parseColor("#B8C4C4"));
            biometricReasonView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f);
            biometricReasonView.setGravity(android.view.Gravity.CENTER);
            biometricReasonView.setLineSpacing(0f, 1.15f);
            android.widget.LinearLayout.LayoutParams rlp = new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
            rlp.topMargin = (int) (10 * d);
            rlp.bottomMargin = (int) (24 * d);
            lock.addView(biometricReasonView, rlp);

            com.google.android.material.button.MaterialButton unlockBtn =
                    new com.google.android.material.button.MaterialButton(this);
            unlockBtn.setText("Unlock");
            unlockBtn.setAllCaps(false);
            unlockBtn.setTextSize(15f);
            unlockBtn.setCornerRadius((int) (10 * d));
            unlockBtn.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(Color.parseColor("#37C9A6")));
            unlockBtn.setTextColor(Color.parseColor("#08201C"));
            unlockBtn.setOnClickListener(v -> promptBiometric());
            lock.addView(unlockBtn, new android.widget.LinearLayout.LayoutParams(
                    (int) (200 * d), (int) (46 * d)));

            // The escape hatch, and a SAFE one: it clears the session and sends the user to
            // Login, where their password proves who they are — the same proof this lock
            // stands in for. Nobody is bricked out of their account; they just cannot step
            // over the lock into the data.
            com.google.android.material.button.MaterialButton signOutBtn =
                    new com.google.android.material.button.MaterialButton(this);
            signOutBtn.setText("Sign out");
            signOutBtn.setAllCaps(false);
            signOutBtn.setTextSize(14f);
            signOutBtn.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(Color.TRANSPARENT));
            signOutBtn.setTextColor(Color.parseColor("#9BB0B0"));
            signOutBtn.setElevation(0f);
            signOutBtn.setStateListAnimator(null);
            signOutBtn.setOnClickListener(v -> signOutFromLock());
            android.widget.LinearLayout.LayoutParams slp = new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, (int) (44 * d));
            slp.topMargin = (int) (6 * d);
            lock.addView(signOutBtn, slp);

            biometricOverlay = lock;
            android.widget.FrameLayout rootLayout = findViewById(android.R.id.content);
            rootLayout.addView(biometricOverlay,
                    new android.widget.FrameLayout.LayoutParams(
                            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                            android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
        }
        biometricOverlay.setVisibility(View.VISIBLE);
        biometricOverlay.bringToFront();

        // The overlay goes up FIRST, then we work out whether a prompt can even run. The
        // old order unlocked on an unusable sensor to avoid "freezing" the app — but a lock
        // that opens itself when the lock is broken is not a lock, and this screen is the
        // user's health record. Unusable now means: keep it covered, say why, and offer the
        // two ways out that do not involve showing the data.
        if (!BiometricHelper.canAuthenticate(this)) {
            waitingForBiometric = false;
            setLockReason(BiometricHelper.getUnavailableReason(this));
            return;
        }

        setLockReason("Verify your identity to continue.");
        promptBiometric();
    }

    /** The line under the title on the lock. Safe to call before the overlay exists. */
    private void setLockReason(String reason) {
        if (biometricReasonView == null) return;
        biometricReasonView.setText(reason == null || reason.isEmpty()
                ? "We could not verify it is you."
                : reason);
    }

    /**
     * Device credential (PIN/pattern) would be the nicer fallback than signing out, but this
     * module is androidx.biometric 1.1.0 on minSdk 29, where DEVICE_CREDENTIAL cannot be
     * combined with a biometric authenticator reliably below API 30. Adding it untested
     * would risk an IllegalArgumentException at the exact moment the user is locked out.
     *
     * Clears the session and returns to Login. Same teardown ProfileFragment's logout uses. */
    private void signOutFromLock() {
        TokenManager.getInstance(this).logout();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    /** Shows the system biometric prompt and resolves the lock overlay. Extracted so
     *  the overlay's tap-to-retry can re-invoke it. */
    private void promptBiometric() {
        waitingForBiometric = true;
        BiometricHelper.authenticate(this,
                () -> {
                    biometricVerified = true;
                    waitingForBiometric = false;
                    if (biometricOverlay != null) biometricOverlay.setVisibility(View.GONE);
                },
                (errorCode, errString) -> {
                    waitingForBiometric = false;
                    if (BiometricHelper.isUserCancel(errorCode)) {
                        // Deliberate cancel / lockout — don't reveal content.
                        finishAffinity();
                        return;
                    }
                    // Everything else used to FAIL OPEN — reveal the content on the grounds
                    // that the user had already signed in this session. That reasoning does
                    // not hold: the lock exists precisely for the window AFTER sign-in, when
                    // the phone may be in someone else's hands, and "the sensor errored" is
                    // trivially reachable (cover the reader, or just wait for a busy one).
                    // So the overlay stays up. The screen is never revealed by a failure.
                    if (biometricOverlay != null) {
                        biometricOverlay.setVisibility(View.VISIBLE);
                        biometricOverlay.bringToFront();
                    }
                    // A dialog is only right when we are actually on screen. ERROR_CANCELED
                    // fires as the app goes to the background, and onResume re-prompts when
                    // it comes back — tap-to-retry on the overlay covers the rest.
                    setLockReason(errString == null ? null : errString.toString());
                });
    }

    private void setupBottomNavigation() {
        // Icon tinting is disabled so the Richie launcher logo keeps its real
        // colours. Selected (teal) vs unselected (white) is baked directly into
        // each vector's filled/outline variant via fillColor.
        bottomNav.setItemIconTintList(null);

        // ── Text label colours to match ───────────────────────────────────────
        ColorStateList labelTintList = new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_checked},
                        new int[]{}
                },
                new int[]{
                        Color.parseColor("#008b8b"),
                        Color.parseColor("#666666")
                }
        );
        bottomNav.setItemTextColor(labelTintList);
    }

    /**
     * Plays a subtle spring-bounce on the tapped nav item icon.
     * Call this inside the item-selected listener after navigation.
     * Scale goes 1.0 → 1.18 → 1.0 with an overshoot interpolator so it
     * "pops" satisfyingly without feeling excessive.
     */
    private void animateNavItemSelection(int itemId) {
        View itemView = bottomNav.findViewById(itemId);
        if (itemView == null) return;

        itemView.animate()
                .scaleX(1.18f)
                .scaleY(1.18f)
                .setDuration(90)
                .withEndAction(() -> itemView.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(220)
                        .setInterpolator(new OvershootInterpolator(3.5f))
                        .start())
                .start();
    }
    // Check if token is close to expiration (e.g., within 5 minutes)
    private boolean isTokenCloseToExpiration() {
        // Safety check
        if (sharedPreferences == null) {
            return true; // Force refresh if preferences are null
        }

        long currentTime = System.currentTimeMillis() / 1000;
        long expirationTime = sharedPreferences.getLong(TokenManager.KEY_TOKEN_EXPIRATION, 0);
        long fiveMinutesInSeconds = 5 * 60;

        return (expirationTime - currentTime) <= fiveMinutesInSeconds;
    }

    @Override
    public void onBackPressed() {
        androidx.fragment.app.Fragment currentFragment =
                getSupportFragmentManager().findFragmentById(R.id.fragment_container);

        // ── Step 1 ─────────────────────────────────────────────────────────────────
        // Give the active fragment first refusal — lets it close an open side-panel
        // (HealthDataFragment has 6 panels; AIFragment has 2) before we do anything.
        if (currentFragment instanceof BackPressHandler) {
            if (((BackPressHandler) currentFragment).handleBackPress()) {
                return; // panel was open and is now dismissed — done
            }
        }

        // ── Step 2 ─────────────────────────────────────────────────────────────────
        // If a sub-fragment was pushed onto the back stack (e.g. WorkoutsFragment or
        // ExercisesFragment via HomeFragment.navigateToFragment), pop it FIRST so back
        // returns the user to where they came from instead of throwing them to a tab.
        if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            getSupportFragmentManager().popBackStack();
            return;
        }

        // ── Step 3 ─────────────────────────────────────────────────────────────────
        // Nothing left on the back stack. If we are NOT on the landing tab (Richie/AI),
        // navigate there before falling back to the double-press-to-exit logic.
        if (!(currentFragment instanceof AIFragment)) {
            bottomNav.setSelectedItemId(R.id.navigation_ai);
            return;
        }

        // ── Step 4 ─────────────────────────────────────────────────────────────────
        // Landing tab, nothing in the back stack — require a second press within 2 s to exit.
        long now = System.currentTimeMillis();
        if (now - lastBackPressTime < BACK_PRESS_EXIT_INTERVAL) {
            // Second press — cancel the toast and exit
            if (backPressToast != null) backPressToast.cancel();
            super.onBackPressed();
        } else {
            // First press — show hint toast, record timestamp
            lastBackPressTime = now;
            if (backPressToast != null) backPressToast.cancel();
            backPressToast = Toast.makeText(this, "Press back again to exit", Toast.LENGTH_SHORT);
            backPressToast.show();
        }
    }

    /**
     * Set the PaymentManager instance for RazorPay callbacks
     * Called by ProUpgradeDialog or other payment initiators
     */
    public void setPaymentManager(PaymentManager paymentManager) {
        this.paymentManager = paymentManager;
    }

    // PaymentResultWithDataListener implementation for RazorPay
    @Override
    public void onPaymentSuccess(String razorpayPaymentId, PaymentData paymentData) {
        Log.d(TAG, "RazorPay payment success: " + razorpayPaymentId);
        if (paymentManager != null) {
            paymentManager.onRazorpayPaymentSuccess(
                    paymentData.getPaymentId(),
                    paymentData.getOrderId(),
                    paymentData.getSignature()
            );
        }
    }

    @Override
    public void onPaymentError(int code, String response, PaymentData paymentData) {
        Log.e(TAG, "RazorPay payment error: " + code + " - " + response);
        if (paymentManager != null) {
            paymentManager.onRazorpayPaymentError(code, response);
        }
    }
}
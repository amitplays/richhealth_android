package com.example.richhealth.Activities;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

import com.example.richhealth.R;

import Database.DatabaseHelper;
import Utils.ProStatusManager;

public class SplashActivity extends AppCompatActivity {
    private static final int SPLASH_DURATION = 3500; // Premium timing
    private DatabaseHelper dbHelper;

    // Animation views
    private ImageView logoImage;
    private ImageView logoGlow;
    private TextView appName;
    private TextView tagline;
    private LinearLayout footerLayout;

    // Premium interpolators
    private final PathInterpolator premiumCurve = new PathInterpolator(0.25f, 0.1f, 0.25f, 1f);
    private final PathInterpolator easeOutQuart = new PathInterpolator(0.25f, 1f, 0.5f, 1f);
    private final PathInterpolator easeInOutCubic = new PathInterpolator(0.645f, 0.045f, 0.355f, 1f);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Hide action bar and set premium flags
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        initializeViews();
        dbHelper = new DatabaseHelper(this);
        setupInitialState();

        // Start premium animation sequence
        startPremiumAnimations();

        // Navigate with perfect timing
        new Handler().postDelayed(this::navigateToNextScreen, SPLASH_DURATION);
    }

    private void initializeViews() {
        logoImage = findViewById(R.id.splash_logo);
        logoGlow = findViewById(R.id.logo_glow);
        appName = findViewById(R.id.app_name);
        tagline = findViewById(R.id.tagline);
        footerLayout = findViewById(R.id.footer_layout);
    }

    private void setupInitialState() {
        // Perfect initial state for premium animations
        logoImage.setAlpha(0f);
        logoImage.setScaleX(0.6f);
        logoImage.setScaleY(0.6f);
        logoImage.setRotation(-10f);

        logoGlow.setAlpha(0f);
        logoGlow.setScaleX(0.8f);
        logoGlow.setScaleY(0.8f);

        appName.setAlpha(0f);
        appName.setTranslationY(40f);
        appName.setScaleX(0.95f);
        appName.setScaleY(0.95f);

        tagline.setAlpha(0f);
        tagline.setTranslationY(20f);
        tagline.setScaleX(0.9f);
        tagline.setScaleY(0.9f);

        footerLayout.setAlpha(0f);
        footerLayout.setTranslationY(25f);
        footerLayout.setScaleX(0.95f);
        footerLayout.setScaleY(0.95f);
    }

    private void startPremiumAnimations() {
        // Orchestrated premium sequence
        animateLogoEntrance();

        new Handler().postDelayed(this::animateAppNameEntrance, 600);
        new Handler().postDelayed(this::animateTaglineEntrance, 1000);
        new Handler().postDelayed(this::animateFooterEntrance, 1300);
        new Handler().postDelayed(this::startBreathingEffect, 2000);
    }

    private void animateLogoEntrance() {
        // Logo glow pre-animation
        ObjectAnimator glowPreFade = ObjectAnimator.ofFloat(logoGlow, "alpha", 0f, 0.15f);
        ObjectAnimator glowPreScale = ObjectAnimator.ofFloat(logoGlow, "scaleX", 0.8f, 1.3f);
        ObjectAnimator glowPreScaleY = ObjectAnimator.ofFloat(logoGlow, "scaleY", 0.8f, 1.3f);

        glowPreFade.setDuration(300);
        glowPreScale.setDuration(400);
        glowPreScaleY.setDuration(400);

        glowPreFade.setInterpolator(new LinearInterpolator());
        glowPreScale.setInterpolator(premiumCurve);
        glowPreScaleY.setInterpolator(premiumCurve);

        AnimatorSet glowPreSet = new AnimatorSet();
        glowPreSet.playTogether(glowPreFade, glowPreScale, glowPreScaleY);

        // Main logo entrance - premium feel
        ObjectAnimator logoFade = ObjectAnimator.ofFloat(logoImage, "alpha", 0f, 1f);
        ObjectAnimator logoScaleX = ObjectAnimator.ofFloat(logoImage, "scaleX", 0.6f, 1.08f, 1.0f);
        ObjectAnimator logoScaleY = ObjectAnimator.ofFloat(logoImage, "scaleY", 0.6f, 1.08f, 1.0f);
        ObjectAnimator logoRotation = ObjectAnimator.ofFloat(logoImage, "rotation", -10f, 0f);

        logoFade.setDuration(800);
        logoScaleX.setDuration(1200);
        logoScaleY.setDuration(1200);
        logoRotation.setDuration(1000);

        logoFade.setInterpolator(easeOutQuart);
        logoScaleX.setInterpolator(easeInOutCubic);
        logoScaleY.setInterpolator(easeInOutCubic);
        logoRotation.setInterpolator(premiumCurve);

        // Glow main animation
        ObjectAnimator glowMainFade = ObjectAnimator.ofFloat(logoGlow, "alpha", 0.15f, 0.4f, 0.12f);
        ObjectAnimator glowMainScale = ObjectAnimator.ofFloat(logoGlow, "scaleX", 1.3f, 1.15f);
        ObjectAnimator glowMainScaleY = ObjectAnimator.ofFloat(logoGlow, "scaleY", 1.3f, 1.15f);

        glowMainFade.setDuration(1200);
        glowMainScale.setDuration(1000);
        glowMainScaleY.setDuration(1000);

        glowMainFade.setInterpolator(easeInOutCubic);
        glowMainScale.setInterpolator(premiumCurve);
        glowMainScaleY.setInterpolator(premiumCurve);

        // Execute sequence
        glowPreSet.start();

        new Handler().postDelayed(() -> {
            AnimatorSet logoMainSet = new AnimatorSet();
            logoMainSet.playTogether(logoFade, logoScaleX, logoScaleY, logoRotation,
                    glowMainFade, glowMainScale, glowMainScaleY);
            logoMainSet.start();
        }, 150);
    }

    private void animateAppNameEntrance() {
        ObjectAnimator nameFade = ObjectAnimator.ofFloat(appName, "alpha", 0f, 1f);
        ObjectAnimator nameTranslationY = ObjectAnimator.ofFloat(appName, "translationY", 40f, 0f);
        ObjectAnimator nameScaleX = ObjectAnimator.ofFloat(appName, "scaleX", 0.95f, 1.02f, 1.0f);
        ObjectAnimator nameScaleY = ObjectAnimator.ofFloat(appName, "scaleY", 0.95f, 1.02f, 1.0f);

        nameFade.setDuration(900);
        nameTranslationY.setDuration(1100);
        nameScaleX.setDuration(1200);
        nameScaleY.setDuration(1200);

        nameFade.setInterpolator(easeOutQuart);
        nameTranslationY.setInterpolator(premiumCurve);
        nameScaleX.setInterpolator(easeInOutCubic);
        nameScaleY.setInterpolator(easeInOutCubic);

        AnimatorSet nameSet = new AnimatorSet();
        nameSet.playTogether(nameFade, nameTranslationY, nameScaleX, nameScaleY);
        nameSet.start();
    }

    private void animateTaglineEntrance() {
        ObjectAnimator taglineFade = ObjectAnimator.ofFloat(tagline, "alpha", 0f, 1f);
        ObjectAnimator taglineTranslationY = ObjectAnimator.ofFloat(tagline, "translationY", 20f, 0f);
        ObjectAnimator taglineScaleX = ObjectAnimator.ofFloat(tagline, "scaleX", 0.9f, 1.0f);
        ObjectAnimator taglineScaleY = ObjectAnimator.ofFloat(tagline, "scaleY", 0.9f, 1.0f);

        taglineFade.setDuration(1000);
        taglineTranslationY.setDuration(1100);
        taglineScaleX.setDuration(1000);
        taglineScaleY.setDuration(1000);

        taglineFade.setInterpolator(easeOutQuart);
        taglineTranslationY.setInterpolator(premiumCurve);
        taglineScaleX.setInterpolator(new FastOutSlowInInterpolator());
        taglineScaleY.setInterpolator(new FastOutSlowInInterpolator());

        AnimatorSet taglineSet = new AnimatorSet();
        taglineSet.playTogether(taglineFade, taglineTranslationY, taglineScaleX, taglineScaleY);
        taglineSet.start();
    }

    private void animateFooterEntrance() {
        ObjectAnimator footerFade = ObjectAnimator.ofFloat(footerLayout, "alpha", 0f, 1f);
        ObjectAnimator footerTranslationY = ObjectAnimator.ofFloat(footerLayout, "translationY", 25f, 0f);
        ObjectAnimator footerScaleX = ObjectAnimator.ofFloat(footerLayout, "scaleX", 0.95f, 1.0f);
        ObjectAnimator footerScaleY = ObjectAnimator.ofFloat(footerLayout, "scaleY", 0.95f, 1.0f);

        footerFade.setDuration(800);
        footerTranslationY.setDuration(900);
        footerScaleX.setDuration(800);
        footerScaleY.setDuration(800);

        footerFade.setInterpolator(easeOutQuart);
        footerTranslationY.setInterpolator(premiumCurve);
        footerScaleX.setInterpolator(new FastOutSlowInInterpolator());
        footerScaleY.setInterpolator(new FastOutSlowInInterpolator());

        AnimatorSet footerSet = new AnimatorSet();
        footerSet.playTogether(footerFade, footerTranslationY, footerScaleX, footerScaleY);
        footerSet.start();
    }

    private void startBreathingEffect() {
        // Ultra-subtle breathing effect - premium apps signature
        ObjectAnimator breatheGlow = ObjectAnimator.ofFloat(logoGlow, "alpha", 0.12f, 0.25f, 0.12f);
        ObjectAnimator breatheLogoX = ObjectAnimator.ofFloat(logoImage, "scaleX", 1.0f, 1.015f, 1.0f);
        ObjectAnimator breatheLogoY = ObjectAnimator.ofFloat(logoImage, "scaleY", 1.0f, 1.015f, 1.0f);

        breatheGlow.setDuration(3000);
        breatheLogoX.setDuration(3000);
        breatheLogoY.setDuration(3000);

        breatheGlow.setRepeatCount(ValueAnimator.INFINITE);
        breatheLogoX.setRepeatCount(ValueAnimator.INFINITE);
        breatheLogoY.setRepeatCount(ValueAnimator.INFINITE);

        // Perfect breathing curve
        PathInterpolator breathingCurve = new PathInterpolator(0.4f, 0f, 0.6f, 1f);
        breatheGlow.setInterpolator(breathingCurve);
        breatheLogoX.setInterpolator(breathingCurve);
        breatheLogoY.setInterpolator(breathingCurve);

        breatheGlow.start();
        breatheLogoX.start();
        breatheLogoY.start();
    }

    private void navigateToNextScreen() {
        // Premium exit animation
        AnimatorSet exitSet = new AnimatorSet();

        ObjectAnimator logoFadeOut = ObjectAnimator.ofFloat(logoImage, "alpha", 1f, 0f);
        ObjectAnimator glowFadeOut = ObjectAnimator.ofFloat(logoGlow, "alpha", 0.12f, 0f);
        ObjectAnimator nameFadeOut = ObjectAnimator.ofFloat(appName, "alpha", 1f, 0f);
        ObjectAnimator taglineFadeOut = ObjectAnimator.ofFloat(tagline, "alpha", 1f, 0f);
        ObjectAnimator footerFadeOut = ObjectAnimator.ofFloat(footerLayout, "alpha", 1f, 0f);

        ObjectAnimator logoScaleOut = ObjectAnimator.ofFloat(logoImage, "scaleX", 1f, 0.9f);
        ObjectAnimator logoScaleOutY = ObjectAnimator.ofFloat(logoImage, "scaleY", 1f, 0.9f);

        logoFadeOut.setDuration(400);
        glowFadeOut.setDuration(400);
        nameFadeOut.setDuration(300);
        taglineFadeOut.setDuration(300);
        footerFadeOut.setDuration(300);
        logoScaleOut.setDuration(400);
        logoScaleOutY.setDuration(400);

        FastOutSlowInInterpolator exitInterpolator = new FastOutSlowInInterpolator();
        logoFadeOut.setInterpolator(exitInterpolator);
        glowFadeOut.setInterpolator(exitInterpolator);
        nameFadeOut.setInterpolator(exitInterpolator);
        taglineFadeOut.setInterpolator(exitInterpolator);
        footerFadeOut.setInterpolator(exitInterpolator);
        logoScaleOut.setInterpolator(exitInterpolator);
        logoScaleOutY.setInterpolator(exitInterpolator);

        exitSet.playTogether(logoFadeOut, glowFadeOut, nameFadeOut,
                taglineFadeOut, footerFadeOut, logoScaleOut, logoScaleOutY);

        exitSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                launchNextActivity();
            }
        });

        exitSet.start();
    }

    private void launchNextActivity() {
        boolean isLoggedIn = checkLoginStatus();
        if (isLoggedIn) {
            // Check Pro access from server on startup (backend auto-expires Pro)
            ProStatusManager.checkProAccessOnStartup(this, null);
            // Always check terms for logged-in users
            checkTermsAndProceed();
        } else {
            // Not logged in, go to login
            Intent intent = new Intent(SplashActivity.this, LoginActivity.class);
            startActivity(intent);
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }
    }

    // Add this simple method:
    private void checkTermsAndProceed() {
        boolean termsAccepted = TermsAndConditionsDialog.areTermsAccepted(this);

        // Debug logging
        android.util.Log.d("SplashActivity", "checkTermsAndProceed() called");
        android.util.Log.d("SplashActivity", "termsAccepted: " + termsAccepted);

        if (termsAccepted) {
            // Terms accepted, go to MainActivity
            android.util.Log.d("SplashActivity", "Going to MainActivity");
            Intent intent = new Intent(SplashActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        } else {
            // Terms not accepted, show dialog
            android.util.Log.d("SplashActivity", "Showing terms dialog");
            showTermsDialog();
        }
    }

    // Update your showTermsDialog method to be simpler:
    private void showTermsDialog() {
        android.util.Log.d("SplashActivity", "showTermsDialog() called");

        TermsAndConditionsDialog termsDialog = new TermsAndConditionsDialog(this, new TermsAndConditionsDialog.OnTermsActionListener() {
            @Override
            public void onTermsAccepted() {
                android.util.Log.d("SplashActivity", "Terms accepted - going to MainActivity");
                // Go to MainActivity
                Intent intent = new Intent(SplashActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            }

            @Override
            public void onTermsDeclined() {
                android.util.Log.d("SplashActivity", "Terms declined - logging out");
                // Clear terms and logout
                TermsAndConditionsDialog.clearTermsAcceptance(SplashActivity.this);
                TokenManager.getInstance(SplashActivity.this).logout();

                Intent intent = new Intent(SplashActivity.this, LoginActivity.class);
                startActivity(intent);
                finish();
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            }
        });

        termsDialog.show();
    }

    private boolean checkLoginStatus() {
        TokenManager tokenManager = TokenManager.getInstance(this);
        return tokenManager.isLoggedIn();
    }
}
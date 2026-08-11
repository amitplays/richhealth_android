package com.example.richhealth.Activities;
import Utils.Utilities;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NoConnectionError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.TimeoutError;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.chip.ChipGroup;
import com.example.richhealth.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import Adapters.MemoryAdapter;
import Database.DatabaseHelper;
import Utils.ApiConfig;
import Models.AiMemory;
import Models.UserProfile;
import Utils.ContactUtils;
import Utils.DialogUtils;
import Utils.PaymentManager;
import Utils.PaymentService;
import Utils.ProStatusManager;
import Utils.ProStatusResult;
import Utils.ProUpgradeDialog;
import Utils.BiometricHelper;
import Utils.SimpleProgress;
import Utils.Skeleton;

public class ProfileFragment extends Fragment {

    private static final String TAG = "ProfileFragment";

    // Header section
    private TextView profileName;
    private TextView profileInitials;
    private TextView profileVerifiedPill;
    private View profileOptionsButton;
    private TextView profileOptionsBadge;
    private int lastPendingRequestCount = 0;

    // Health Metrics section
    private TextView aqiValue;
    private TextView weightValue;
    private TextView sleepValue;
    private TextView waterValue;
    private Api.AQIAPIService aqiApiService;
    // Guards against repeated IQAir fetch/store cycles within a session (see refreshAqiFromLocation).
    private boolean aqiRefreshAttempted = false;

    // Fitness Goals section
    private TextView primaryGoal;
    private TextView weeklyGoal;

    // Health & Lifestyle section
    private TextView activityLevel;
    private TextView sleepHours;
    private TextView dietType;

    // Personal Info section
    private TextView emailValue;
    private TextView ageValue;
    private TextView genderValue;
    private TextView phoneValue;
    private TextView locationValue;
    private TextView occupationValue;

    // Lifestyle section
    private TextView stressLevelValue;
    private TextView waterIntakeValue;
    private TextView mealsPerDayValue;
    private TextView screenTimeValue;
    private TextView sunExposureValue;

    // Habits section
    private TextView smokingValueText;
    private TextView alcoholValueText;
    private TextView caffeineValueText;

    // Family History section
    private ChipGroup familyHistoryGroup;
    private TextView familyHistoryEmpty;

    // Inline empty-state values for chip rows
    private TextView medicalConditionsEmpty;
    private TextView allergiesEmpty;

    // Settings section
    private SwitchMaterial metricSwitch;
    private SwitchMaterial biometricSwitch;
    private MaterialButton shareProgressButton;
    private LinearLayout notificationItem;

    // Header + tabs (overhaul)
    private Utils.UsageRing completenessRing;
    private TextView completenessPercent;
    private View completenessCta;
    private View completenessDivider;
    private View tabBtnProfile, tabBtnSettings, tabBtnPlan;
    private TextView tabLabelProfile, tabLabelSettings, tabLabelPlan;
    private android.widget.ImageView tabIconProfile, tabIconSettings, tabIconPlan;
    private View tabProfile, tabSettings, tabPlan;
    private View viewFullPlanButton;

    // Membership section (state-aware: upgrade / granted note / family management)
    private View membershipRow;
    private TextView membershipSubtitle;
    private com.google.android.material.button.MaterialButton membershipUpgradeButton;
    private TextView membershipGrantedNote;
    private View membershipFamilySection;
    private TextView familyCoveredChip;
    private View familyLoading, familyEmpty;
    private LinearLayout familyMembersContainer;
    private PaymentService paymentService;
    private final List<JSONObject> familyMembersList = new ArrayList<>();
    private int familyMemberMax = 5;
    private boolean familyLoadInFlight = false;

    // Plan tab (live usage)
    private TextView planCurrentName, planCurrentDesc, planStatusChip;

    // AI & Chat section
    private LinearLayout aiToneItem;
    private LinearLayout aiLengthItem;
    private TextView aiToneValue, aiLengthValue;
    private LinearLayout aiCustomItem;
    private TextView aiCustomValue;
    private LinearLayout aiMemoryItem;
    private TextView aiMemorySubtitle;
    private SwitchMaterial aiSaveMemoriesSwitch;
    private SwitchMaterial aiImproveModelSwitch;
    private SwitchMaterial aiAutofillCardsSwitch;
    private SwitchMaterial aiShowThinkingSwitch;

    // Logout button (now an icon button in the header)

    private DatabaseHelper dbHelper;
    private UserProfile userProfile;

    private ProStatusManager proStatusManager;
    // Find new views in initViews method
    private TextView bloodTypeValue;
    private ChipGroup medicalConditionsGroup;
    // medicationsGroup removed - medications section not in layout
    private ChipGroup allergiesGroup;
    private View rootView;

    // Menstrual section
    private LinearLayout menstrualCard;
    private TextView menstrualStatusValue;
    private TextView cycleLengthValue;
    private TextView periodLengthValue;
    private TextView pregnancyStatusValue;
    private TextView contraceptionValue;
    private ChipGroup menstrualSymptomsGroup;

    // Additional signup fields now surfaced on the profile
    private TextView ancestryValue;
    private TextView heightValue;
    private TextView waistValue;
    private TextView recentWeightChangeValue;
    private TextView medicationCategoriesEmpty;
    private ChipGroup medicationCategoriesGroup;
    private TextView familyRelativesEmpty;
    private ChipGroup familyRelativesGroup;

    // Conditional habit / condition follow-up rows (wrapper + value)
    private View rowSmokingDuration, rowCigarettesPerDay, rowLastSmoked,
            rowDrinksPerWeek, rowConditionsDiagnosed, rowConditionsMedicated;
    private TextView smokingDurationValue, cigarettesPerDayValue, lastSmokedValue,
            drinksPerWeekValue, conditionsDiagnosedValue, conditionsMedicatedValue;

    private ProUpgradeDialog proUpgradeDialog;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_profile, container, false);
        initViews(rootView);
        setupTabs();
        setupListeners();
        setupLogoutButton();
        loadAndDisplayProfile();
        setupContactImport();
        Utils.IconAnimator.animateSectionIcons(rootView);
        // Cards no longer slide up from the bottom on entry (removed for a calmer,
        // consistent feel across the three profile tabs).
        return rootView;
    }

    /**
     * Stagger-fade the direct MaterialCardView children of the profile container.
     */
    private void animateCardsEntry(View root) {
        android.view.ViewGroup container = root.findViewById(R.id.profile_content_container);
        if (container == null) return;
        long delay = 0;
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (!(child instanceof com.google.android.material.card.MaterialCardView)) continue;
            if (child.getVisibility() == View.GONE) continue;
            child.setAlpha(0f);
            child.setTranslationY(20f);
            child.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(delay)
                    .setDuration(340)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
            delay += 45;
        }
    }

    private void setupListeners() {
        // Header "options" button → single dropdown menu (Edit, Requests, Log out).
        if (profileOptionsButton != null) {
            profileOptionsButton.setOnClickListener(this::showProfileOptionsMenu);
        }

        // Completion CTA in the header → opens the edit sheet (mirrors iOS "Add missing info").
        if (completenessCta != null) {
            completenessCta.setOnClickListener(v -> {
                if (userProfile != null) {
                    showEditProfileDialog();
                } else {
                    Utilities.toast(requireContext(), "Please wait while loading profile data");
                    loadAndDisplayProfile();
                }
            });
        }

        // Initialize the ProUpgradeDialog (add this after initializing other UI components)
        proUpgradeDialog = new ProUpgradeDialog(requireActivity());

        // Upgrade button → opens the existing purchase/upgrade dialog directly.
        if (membershipUpgradeButton != null) {
            membershipUpgradeButton.setOnClickListener(v -> proUpgradeDialog.showUpgrade(
                    new ProUpgradeDialog.ProUpgradeCallback() {
                        @Override public void onProStatusChanged(boolean isPro) { updateProUI(); }
                    }));
        }


        metricSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (userProfile != null) {
                userProfile.setMetric(isChecked);
                dbHelper.updateUserProfile(userProfile);
                updateDisplayedMetrics();
                displayBodyMetrics();
            }
        });

        // Biometric toggle — verify hardware availability and prompt to confirm
        setupBiometricToggle();

        // Add these new click listeners:
        shareProgressButton.setOnClickListener(v -> {
            shareUserProgress();
        });

        notificationItem.setOnClickListener(v -> {
            Utilities.toast(requireContext(), "Notification functionality will be implemented later");
        });

        // ── AI & Chat preferences ──
        setupAiPreferenceListeners();

        // Change Password (Security & Privacy card)
        View changePasswordItem = rootView.findViewById(R.id.change_password_item);
        if (changePasswordItem != null) {
            changePasswordItem.setOnClickListener(v -> showChangePasswordDialog());
        }

    }

    private void setupBiometricToggle() {
        if (biometricSwitch == null) return;

        // Set initial state without triggering listener
        biometricSwitch.setOnCheckedChangeListener(null);
        boolean enabled = BiometricHelper.isBiometricEnabled(requireContext());
        boolean canAuth = BiometricHelper.canAuthenticate(requireContext());
        biometricSwitch.setChecked(enabled);
        biometricSwitch.setEnabled(canAuth);

        // Update subtitle if biometric isn't available
        TextView subtitle = rootView.findViewById(R.id.biometric_subtitle);
        if (!canAuth && subtitle != null) {
            subtitle.setText(BiometricHelper.getUnavailableReason(requireContext()));
            subtitle.setTextColor(ContextCompat.getColor(requireContext(), R.color.rh_danger));
        }

        biometricSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                // User wants to enable — verify biometric works first
                BiometricHelper.authenticate(requireActivity(),
                        () -> {
                            // Guard: fragment may have detached while prompt was showing
                            if (!isAdded()) return;
                            BiometricHelper.setBiometricEnabled(requireContext(), true);
                            BiometricHelper.persistBiometricToServer(requireContext(), true);
                            Utilities.toast(requireContext(), "Biometric lock enabled");
                        },
                        (errorCode, errString) -> {
                            if (!isAdded()) return;
                            // Failed or cancelled: revert the toggle
                            biometricSwitch.setOnCheckedChangeListener(null);
                            biometricSwitch.setChecked(false);
                            setupBiometricToggle(); // re-attach listener
                            if (!BiometricHelper.isUserCancel(errorCode)) {
                                Utilities.toast(requireContext(), "Biometric verification failed");
                            }
                        });
            } else {
                // Turning off — no confirmation needed
                BiometricHelper.setBiometricEnabled(requireContext(), false);
                BiometricHelper.persistBiometricToServer(requireContext(), false);
                Utilities.toast(requireContext(), "Biometric lock disabled");
            }
        });
    }

    private void confirmLogout() {
        // App-styled confirmation (consistent with the rest of the app) instead of a native AlertDialog.
        DialogUtils.showConfirmDialog(requireContext(),
                "Log out?",
                "You'll need to sign in again to access your health data.",
                "Log out", "Cancel", true,
                () -> {
                    // Clear login status in UserProfile
                    if (userProfile != null) {
                        userProfile.setLoggedIn(false);
                        userProfile.setAuthToken(null);
                        dbHelper.updateUserProfile(userProfile);
                    }

                    // Clear all auth tokens, caches, and local DB via TokenManager.logout()
                    Context context = getContext();
                    if (context != null) {
                        TokenManager tokenManager = TokenManager.getInstance(context);
                        tokenManager.logout();

                        // Navigate to LoginActivity
                        Intent intent = new Intent(context, LoginActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        requireActivity().finish();
                    }
                });
    }

    // Add this method to handle share functionality
    private void shareUserProgress() {
        // Create a summary of the user's health profile
        StringBuilder shareContent = new StringBuilder();
        shareContent.append("My Health Progress with RichHealth:\n\n");

        // Add basic profile info
        shareContent.append("🏋️ Fitness Goals:\n");
        shareContent.append("Primary Goal: ").append(primaryGoal.getText()).append("\n");
        shareContent.append("Weekly Goal: ").append(weeklyGoal.getText()).append("\n");

        // Add health metrics
        shareContent.append("📊 Health Metrics:\n");
        shareContent.append("Air Quality: ").append(aqiValue.getText()).append("\n");
        shareContent.append("Weight: ").append(weightValue.getText()).append("\n");
        shareContent.append("Sleep: ").append(sleepValue.getText()).append("\n");
        shareContent.append("Water: ").append(waterValue.getText()).append("\n\n");

        shareContent.append("Download RichHealth to track your own health journey!");

        // Create share intent
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareContent.toString());

        // Launch Android's share dialog
        startActivity(Intent.createChooser(shareIntent, "Share via"));
    }

    /**
     * Update the UserProfile object with the values from the edit dialog
     */
    private void updateUserProfileWithValues(Map<String, String> values) {
        // Update basic info
        userProfile.setName(values.get("name"));
        userProfile.setEmail(values.get("email"));

        // Update health metrics
        try {
            if (values.get("weight") != null && !values.get("weight").isEmpty()) {
                double weight = Double.parseDouble(values.get("weight"));
                userProfile.setWeight(weight);
            }
        } catch (NumberFormatException e) {
            Log.e(TAG, "Invalid weight value", e);
        }

        // Heart rate and blood pressure are captured as measurements in the
        // Health Data collection screen — not edited from the profile — so they
        // are intentionally not parsed or saved here.

        // Personal info
        if (values.containsKey("phoneNumber")) {
            userProfile.setPhoneNumber(values.get("phoneNumber"));
        }
        if (values.containsKey("location")) {
            userProfile.setLocation(values.get("location"));
        }
        if (values.containsKey("gender") && values.get("gender") != null && !values.get("gender").isEmpty()) {
            userProfile.setGender(values.get("gender"));
        }
        if (values.containsKey("dateOfBirth") && values.get("dateOfBirth") != null && !values.get("dateOfBirth").isEmpty()) {
            try {
                java.util.Date dob = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(values.get("dateOfBirth"));
                userProfile.setDateOfBirth(dob);
            } catch (java.text.ParseException e) {
                Log.e(TAG, "Invalid dateOfBirth format (expected YYYY-MM-DD)", e);
            }
        }
        if (values.containsKey("height") && values.get("height") != null && !values.get("height").isEmpty()) {
            try {
                userProfile.setHeight(Double.parseDouble(values.get("height")));
            } catch (NumberFormatException e) {
                Log.e(TAG, "Invalid height value", e);
            }
        }
        if (values.containsKey("waistCircumference") && values.get("waistCircumference") != null && !values.get("waistCircumference").isEmpty()) {
            try {
                userProfile.setWaistCircumference(Double.parseDouble(values.get("waistCircumference")));
            } catch (NumberFormatException e) {
                Log.e(TAG, "Invalid waist value", e);
            }
        }
        if (values.containsKey("occupationType") && OCCUPATION_OPTIONS.containsKey(values.get("occupationType"))) {
            userProfile.setOccupationType(OCCUPATION_OPTIONS.get(values.get("occupationType")));
        }

        // Update fitness goals
        if (values.get("primaryGoal") != null && !values.get("primaryGoal").isEmpty()) {
            String goalLabel = values.get("primaryGoal");
            String resolved = GOAL_OPTIONS.containsKey(goalLabel) ? GOAL_OPTIONS.get(goalLabel) : goalLabel;
            userProfile.setPrimaryGoal(resolved);
        }

        try {
            if (values.get("weeklyGoal") != null && !values.get("weeklyGoal").isEmpty()) {
                double weeklyGoal = Double.parseDouble(values.get("weeklyGoal"));
                userProfile.setWeeklyGoal(weeklyGoal);
            }
        } catch (NumberFormatException e) {
            Log.e(TAG, "Invalid weekly goal value", e);
        }

        // Update lifestyle
        if (values.get("activityLevel") != null && !values.get("activityLevel").isEmpty()) {
            String actLabel = values.get("activityLevel");
            int activityLevelValue = ACTIVITY_OPTIONS.containsKey(actLabel)
                    ? ACTIVITY_OPTIONS.get(actLabel)
                    : getActivityLevelValue(actLabel);
            userProfile.setActivityLevel(activityLevelValue);
        }

        try {
            if (values.get("sleepHours") != null && !values.get("sleepHours").isEmpty()) {
                int sleepHours = Integer.parseInt(values.get("sleepHours"));
                userProfile.setSleepHours(sleepHours);
            }
        } catch (NumberFormatException e) {
            Log.e(TAG, "Invalid sleep hours value", e);
        }

        if (values.get("dietType") != null && !values.get("dietType").isEmpty()) {
            String dietLabel = values.get("dietType");
            String resolvedDiet = DIET_OPTIONS.containsKey(dietLabel) ? DIET_OPTIONS.get(dietLabel) : dietLabel;
            userProfile.setDietType(resolvedDiet);
        }

        if (values.containsKey("mealsPerDay") && MEALS_OPTIONS.containsKey(values.get("mealsPerDay"))) {
            userProfile.setMealsPerDay(MEALS_OPTIONS.get(values.get("mealsPerDay")));
        }
        if (values.containsKey("waterIntake") && WATER_OPTIONS.containsKey(values.get("waterIntake"))) {
            userProfile.setWaterIntake(WATER_OPTIONS.get(values.get("waterIntake")));
        }
        if (values.containsKey("stressLevel") && STRESS_OPTIONS.containsKey(values.get("stressLevel"))) {
            userProfile.setStressLevel(STRESS_OPTIONS.get(values.get("stressLevel")));
        }
        if (values.containsKey("screenTimeBeforeBed") && SCREEN_TIME_OPTIONS.containsKey(values.get("screenTimeBeforeBed"))) {
            userProfile.setScreenTimeBeforeBed(SCREEN_TIME_OPTIONS.get(values.get("screenTimeBeforeBed")));
        }
        if (values.containsKey("sunExposure") && SUN_OPTIONS.containsKey(values.get("sunExposure"))) {
            userProfile.setSunExposure(SUN_OPTIONS.get(values.get("sunExposure")));
        }

        // Habits — smoking is encoded as a tag from SMOKING_OPTIONS, then expanded to smoker/level/frequency
        if (values.containsKey("smokingChoice") && SMOKING_OPTIONS.containsKey(values.get("smokingChoice"))) {
            String tag = SMOKING_OPTIONS.get(values.get("smokingChoice"));
            switch (tag) {
                case "never":
                case "ex":
                    userProfile.setSmoker(false);
                    userProfile.setSmokingLevel(0);
                    userProfile.setSmokingFrequency("Non-smoker");
                    break;
                case "social":
                    userProfile.setSmoker(false);
                    userProfile.setSmokingLevel(1);
                    userProfile.setSmokingFrequency("Social");
                    break;
                case "occasional":
                    userProfile.setSmoker(true);
                    userProfile.setSmokingLevel(2);
                    userProfile.setSmokingFrequency("Occasional");
                    break;
                case "regular":
                    userProfile.setSmoker(true);
                    userProfile.setSmokingLevel(3);
                    userProfile.setSmokingFrequency("Regular");
                    break;
            }
        }

        if (values.containsKey("alcoholConsumption") && ALCOHOL_OPTIONS.containsKey(values.get("alcoholConsumption"))) {
            String alc = ALCOHOL_OPTIONS.get(values.get("alcoholConsumption"));
            userProfile.setAlcoholConsumption(alc);
            switch (alc) {
                case "None":              userProfile.setAlcoholLevel(0); break;
                case "Special Occasions": userProfile.setAlcoholLevel(1); break;
                case "Socially":          userProfile.setAlcoholLevel(2); break;
                case "Regularly":         userProfile.setAlcoholLevel(3); break;
                case "Frequently":        userProfile.setAlcoholLevel(4); break;
            }
        }

        if (values.containsKey("caffeineHabit") && CAFFEINE_OPTIONS.containsKey(values.get("caffeineHabit"))) {
            userProfile.setCaffeineHabit(CAFFEINE_OPTIONS.get(values.get("caffeineHabit")));
        }

        if (values.containsKey("familyHistory")) {
            userProfile.setFamilyHistory(parseCommaSeparatedList(values.get("familyHistory")));
        }

        // Conditional follow-ups (dropdowns store the value string directly)
        if (values.containsKey("smokingDuration")) userProfile.setSmokingDuration(values.get("smokingDuration"));
        if (values.containsKey("cigarettesPerDay")) userProfile.setCigarettesPerDay(values.get("cigarettesPerDay"));
        if (values.containsKey("lastSmoked")) userProfile.setLastSmoked(values.get("lastSmoked"));
        if (values.containsKey("drinksPerWeek")) userProfile.setDrinksPerWeek(values.get("drinksPerWeek"));
        if (values.containsKey("conditionsDiagnosed")) userProfile.setConditionsDiagnosed(values.get("conditionsDiagnosed"));
        if (values.containsKey("conditionsMedicated")) userProfile.setConditionsMedicated(values.get("conditionsMedicated"));
        if (values.containsKey("familyRelatives")) {
            userProfile.setFamilyHistoryRelatives(parseCommaSeparatedList(values.get("familyRelatives")));
        }
        if (values.containsKey("ethnicity")) userProfile.setEthnicity(values.get("ethnicity"));
        if (values.containsKey("recentWeightChange")) userProfile.setRecentWeightChange(values.get("recentWeightChange"));
        if (values.containsKey("medicationCategories")) {
            userProfile.setMedicationCategories(parseCommaSeparatedList(values.get("medicationCategories")));
        }

        // Add new fields for additional details
        if (values.containsKey("bloodType")) {
            userProfile.setBloodType(values.get("bloodType"));
        }

        // Parse comma-separated lists
        if (values.containsKey("medicalConditions")) {
            List<String> medicalConditions = parseCommaSeparatedList(values.get("medicalConditions"));
            userProfile.setMedicalConditions(medicalConditions);
        }

        if (values.containsKey("medications")) {
            List<String> medications = parseCommaSeparatedList(values.get("medications"));
            userProfile.setMedications(medications);
        }

        if (values.containsKey("allergies")) {
            List<String> allergies = parseCommaSeparatedList(values.get("allergies"));
            userProfile.setAllergies(allergies);
        }

        // Menstrual fields
        if (values.containsKey("menstrualStatus")) {
            userProfile.setMenstrualStatus(values.get("menstrualStatus"));
        }
        try {
            if (values.containsKey("averageCycleLength") && !values.get("averageCycleLength").isEmpty()) {
                userProfile.setAverageCycleLength(Integer.parseInt(values.get("averageCycleLength")));
            }
        } catch (NumberFormatException e) {
            Log.e(TAG, "Invalid cycle length value", e);
        }
        try {
            if (values.containsKey("averagePeriodLength") && !values.get("averagePeriodLength").isEmpty()) {
                userProfile.setAveragePeriodLength(Integer.parseInt(values.get("averagePeriodLength")));
            }
        } catch (NumberFormatException e) {
            Log.e(TAG, "Invalid period length value", e);
        }
        if (values.containsKey("menstrualSymptoms")) {
            userProfile.setMenstrualSymptoms(parseCommaSeparatedList(values.get("menstrualSymptoms")));
        }
        if (values.containsKey("pregnancyStatus")) {
            userProfile.setPregnancyStatus(values.get("pregnancyStatus"));
        }
        if (values.containsKey("contraceptionMethod") && CONTRACEPTION_OPTIONS.containsKey(values.get("contraceptionMethod"))) {
            userProfile.setContraceptionMethod(CONTRACEPTION_OPTIONS.get(values.get("contraceptionMethod")));
        }

    }

    private List<String> parseCommaSeparatedList(String input) {
        if (input == null || input.trim().isEmpty()) {
            return new ArrayList<>();
        }
        return Arrays.stream(input.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private void showChangePasswordDialog() {
        DialogUtils.DialogField[] fields = new DialogUtils.DialogField[] {
                new DialogUtils.DialogField("currentPassword", "Current Password", android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD, ""),
                new DialogUtils.DialogField("newPassword", "New Password", android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD, ""),
                new DialogUtils.DialogField("confirmPassword", "Confirm Password", android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD, "")
        };

        DialogUtils.showEditDialog(
                requireContext(),
                "Change Password",
                fields,
                values -> {
                    String newPass = values.get("newPassword");
                    String confirmPass = values.get("confirmPassword");

                    if (!newPass.equals(confirmPass)) {
                        throw new IllegalArgumentException("Passwords don't match");
                    }

                    // Add password update logic here
                    // userProfile.setPassword(newPass);
                    // dbHelper.updateUserProfile(userProfile);
                },
                null,
                null
        );
    }

    private void initViews(View view) {
        // Header section
        profileName = view.findViewById(R.id.profile_name);
        profileInitials = view.findViewById(R.id.profile_initials);
        profileVerifiedPill = view.findViewById(R.id.profile_verified_pill);
        profileOptionsButton = view.findViewById(R.id.profile_options_button);
        profileOptionsBadge = view.findViewById(R.id.profile_options_badge);
        completenessRing = view.findViewById(R.id.completeness_ring);
        completenessPercent = view.findViewById(R.id.completeness_percent);
        completenessCta = view.findViewById(R.id.completeness_cta);
        completenessDivider = view.findViewById(R.id.completeness_divider);

        // Health Metrics section
        aqiValue = view.findViewById(R.id.aqi_value);
        weightValue = view.findViewById(R.id.weight_value);
        sleepValue = view.findViewById(R.id.sleep_value);
        waterValue = view.findViewById(R.id.water_value);

        // Fitness Goals section
        primaryGoal = view.findViewById(R.id.primary_goal);
        weeklyGoal = view.findViewById(R.id.weekly_goal);

        // Health & Lifestyle section
        activityLevel = view.findViewById(R.id.activity_level);
        sleepHours = view.findViewById(R.id.sleep_hours);
        dietType = view.findViewById(R.id.diet_type);

        // Settings section
        notificationItem = view.findViewById(R.id.notification_item);
        shareProgressButton = view.findViewById(R.id.share_progress_button);

// Add this method to set up click listeners
        metricSwitch = view.findViewById(R.id.metric_switch);
        biometricSwitch = view.findViewById(R.id.biometric_switch);

        membershipRow = view.findViewById(R.id.membership_row);
        membershipSubtitle = view.findViewById(R.id.membership_subtitle);
        membershipUpgradeButton = view.findViewById(R.id.membership_upgrade_button);
        membershipGrantedNote = view.findViewById(R.id.membership_granted_note);
        membershipFamilySection = view.findViewById(R.id.membership_family_section);
        familyCoveredChip = view.findViewById(R.id.family_covered_chip);
        familyLoading = view.findViewById(R.id.family_loading);
        familyEmpty = view.findViewById(R.id.family_empty);
        familyMembersContainer = view.findViewById(R.id.family_members_container);
        paymentService = new PaymentService(requireContext());
        proStatusManager = ProStatusManager.getInstance(requireContext());

        // Header + tabs
        // [PLAN-PILL-REVIEW] disabled (backend-driven, keep after review): profilePlanBadge = view.findViewById(R.id.profile_plan_badge);
        tabBtnProfile = view.findViewById(R.id.tab_btn_profile);
        tabBtnSettings = view.findViewById(R.id.tab_btn_settings);
        tabBtnPlan = view.findViewById(R.id.tab_btn_plan);
        tabLabelProfile = view.findViewById(R.id.tab_label_profile);
        tabLabelSettings = view.findViewById(R.id.tab_label_settings);
        tabLabelPlan = view.findViewById(R.id.tab_label_plan);
        tabIconProfile = view.findViewById(R.id.tab_icon_profile);
        tabIconSettings = view.findViewById(R.id.tab_icon_settings);
        tabIconPlan = view.findViewById(R.id.tab_icon_plan);
        tabProfile = view.findViewById(R.id.tab_profile);
        tabSettings = view.findViewById(R.id.tab_settings);
        tabPlan = view.findViewById(R.id.tab_plan);
        viewFullPlanButton = view.findViewById(R.id.btn_view_full_plan);
        planCurrentName = view.findViewById(R.id.plan_current_name);
        planCurrentDesc = view.findViewById(R.id.plan_current_desc);
        planStatusChip = view.findViewById(R.id.plan_status_chip);

        // AI & Chat section
        aiToneItem = view.findViewById(R.id.ai_tone_item);
        aiLengthItem = view.findViewById(R.id.ai_length_item);
        aiToneValue = view.findViewById(R.id.ai_tone_value);
        aiLengthValue = view.findViewById(R.id.ai_length_value);
        aiCustomItem = view.findViewById(R.id.ai_custom_item);
        aiCustomValue = view.findViewById(R.id.ai_custom_value);
        aiMemoryItem = view.findViewById(R.id.ai_memory_item);
        aiMemorySubtitle = view.findViewById(R.id.ai_memory_subtitle);
        aiSaveMemoriesSwitch = view.findViewById(R.id.ai_save_memories_switch);
        aiImproveModelSwitch = view.findViewById(R.id.ai_improve_model_switch);
        aiAutofillCardsSwitch = view.findViewById(R.id.ai_autofill_cards_switch);
        aiShowThinkingSwitch = view.findViewById(R.id.ai_show_thinking_switch);

        // Find logout button if it exists

        bloodTypeValue = view.findViewById(R.id.blood_type);
        medicalConditionsGroup = view.findViewById(R.id.medical_conditions_group);
        // medicationsGroup removed - medications section not in layout
        allergiesGroup = view.findViewById(R.id.allergies_group);

        // Menstrual
        menstrualCard = view.findViewById(R.id.menstrual_card);
        menstrualStatusValue = view.findViewById(R.id.menstrual_status_value);
        cycleLengthValue = view.findViewById(R.id.cycle_length_value);
        periodLengthValue = view.findViewById(R.id.period_length_value);
        pregnancyStatusValue = view.findViewById(R.id.pregnancy_status_value);
        contraceptionValue = view.findViewById(R.id.contraception_value);
        menstrualSymptomsGroup = view.findViewById(R.id.menstrual_symptoms_group);

        // Additional signup fields
        ancestryValue = view.findViewById(R.id.ancestry_value);
        heightValue = view.findViewById(R.id.height_value);
        waistValue = view.findViewById(R.id.waist_value);
        recentWeightChangeValue = view.findViewById(R.id.recent_weight_change_value);
        medicationCategoriesEmpty = view.findViewById(R.id.medication_categories_empty);
        medicationCategoriesGroup = view.findViewById(R.id.medication_categories_group);
        familyRelativesEmpty = view.findViewById(R.id.family_relatives_empty);
        familyRelativesGroup = view.findViewById(R.id.family_relatives_group);

        // Conditional habit follow-up rows
        rowSmokingDuration = view.findViewById(R.id.row_smoking_duration);
        rowCigarettesPerDay = view.findViewById(R.id.row_cigarettes_per_day);
        rowLastSmoked = view.findViewById(R.id.row_last_smoked);
        rowDrinksPerWeek = view.findViewById(R.id.row_drinks_per_week);
        rowConditionsDiagnosed = view.findViewById(R.id.row_conditions_diagnosed);
        rowConditionsMedicated = view.findViewById(R.id.row_conditions_medicated);
        smokingDurationValue = view.findViewById(R.id.smoking_duration_value);
        cigarettesPerDayValue = view.findViewById(R.id.cigarettes_per_day_value);
        lastSmokedValue = view.findViewById(R.id.last_smoked_value);
        drinksPerWeekValue = view.findViewById(R.id.drinks_per_week_value);
        conditionsDiagnosedValue = view.findViewById(R.id.conditions_diagnosed_value);
        conditionsMedicatedValue = view.findViewById(R.id.conditions_medicated_value);

        // Personal Info
        emailValue = view.findViewById(R.id.email_value);
        ageValue = view.findViewById(R.id.age_value);
        genderValue = view.findViewById(R.id.gender_value);
        phoneValue = view.findViewById(R.id.phone_value);
        locationValue = view.findViewById(R.id.location_value);
        occupationValue = view.findViewById(R.id.occupation_value);

        // Lifestyle
        stressLevelValue = view.findViewById(R.id.stress_level_value);
        waterIntakeValue = view.findViewById(R.id.water_intake_value);
        mealsPerDayValue = view.findViewById(R.id.meals_per_day_value);
        screenTimeValue = view.findViewById(R.id.screen_time_value);
        sunExposureValue = view.findViewById(R.id.sun_exposure_value);

        // Habits
        smokingValueText = view.findViewById(R.id.smoking_value);
        alcoholValueText = view.findViewById(R.id.alcohol_value);
        caffeineValueText = view.findViewById(R.id.caffeine_value);

        // Family History
        familyHistoryGroup = view.findViewById(R.id.family_history_group);
        familyHistoryEmpty = view.findViewById(R.id.family_history_empty);

        medicalConditionsEmpty = view.findViewById(R.id.medical_conditions_empty);
        allergiesEmpty = view.findViewById(R.id.allergies_empty);

        dbHelper = new DatabaseHelper(requireContext());
    }

    private void displayAdditionalDetails() {
        // Blood Type
        bloodTypeValue.setText(userProfile.getBloodType() != null && !userProfile.getBloodType().isEmpty()
                ? userProfile.getBloodType()
                : "Not Set");

        // Medical Conditions — inline "Not set" when empty, chips below when populated
        populateInlineChipGroup(medicalConditionsGroup, medicalConditionsEmpty, userProfile.getMedicalConditions());

        // Allergies — same pattern
        populateInlineChipGroup(allergiesGroup, allergiesEmpty, userProfile.getAllergies());

        // Medication types (predictive categories) — same inline chip pattern
        populateInlineChipGroup(medicationCategoriesGroup, medicationCategoriesEmpty, userProfile.getMedicationCategories());

        // Height, Waist, Recent Weight Change
        displayBodyMetrics();

        // Menstrual / Reproductive Health
        displayMenstrualSection();
    }

    private boolean isFemaleUser() {
        String gender = userProfile.getGender();
        return gender != null && (gender.equalsIgnoreCase("female") || gender.equalsIgnoreCase("f"));
    }

    /** Maps stored contraception value → display label; falls back to the raw text for "Other". */
    private String contraceptionDisplay(String value) {
        if (value == null || value.isEmpty()) return getString(R.string.empty_value);
        switch (value.toLowerCase()) {
            case "none":    return "None";
            case "pill":    return "Pill";
            case "iud":     return "IUD";
            case "condom":  return "Condom";
            case "implant": return "Implant/Injection";
            default:        return capitalize(value); // custom "Other" text
        }
    }

    private void displayMenstrualSection() {
        String status = userProfile.getMenstrualStatus();
        boolean isFemale = isFemaleUser();

        // Show for female users even if menstrual status is not yet set
        if (!isFemale && (status == null || "not_applicable".equals(status))) {
            if (menstrualCard != null) menstrualCard.setVisibility(View.GONE);
            return;
        }
        if (menstrualCard != null) menstrualCard.setVisibility(View.VISIBLE);

        // Contraception (shown regardless of cycle-status completeness)
        if (contraceptionValue != null) {
            contraceptionValue.setText(contraceptionDisplay(userProfile.getContraceptionMethod()));
        }

        // If female but status not set yet, show defaults
        if (status == null || "not_applicable".equals(status)) {
            if (menstrualStatusValue != null) menstrualStatusValue.setText("Not set");
            if (cycleLengthValue != null) cycleLengthValue.setText("Not set");
            if (periodLengthValue != null) periodLengthValue.setText("Not set");
            if (pregnancyStatusValue != null) pregnancyStatusValue.setText("N/A");
            return;
        }

        // Format status for display
        String displayStatus = status.substring(0, 1).toUpperCase() + status.substring(1).replace("_", " ");
        if (menstrualStatusValue != null) menstrualStatusValue.setText(displayStatus);

        int cycleLen = userProfile.getAverageCycleLength();
        if (cycleLengthValue != null) cycleLengthValue.setText(cycleLen > 0 ? cycleLen + " days" : "Not set");

        int periodLen = userProfile.getAveragePeriodLength();
        if (periodLengthValue != null) periodLengthValue.setText(periodLen > 0 ? periodLen + " days" : "Not set");

        String pregnancy = userProfile.getPregnancyStatus();
        if (pregnancyStatusValue != null) {
            if (pregnancy != null && !"not_applicable".equals(pregnancy)) {
                String pDisplay = pregnancy.substring(0, 1).toUpperCase() + pregnancy.substring(1).replace("_", " ");
                pregnancyStatusValue.setText(pDisplay);
            } else {
                pregnancyStatusValue.setText("N/A");
            }
        }

        populateChipGroup(menstrualSymptomsGroup, userProfile.getMenstrualSymptoms());
    }

    /**
     * For chip rows that should stay as a single-line row when empty:
     * shows the inline "Not set" value next to the label and hides the ChipGroup;
     * when populated, hides the inline value and reveals chips beneath.
     */
    private void populateInlineChipGroup(ChipGroup chipGroup, TextView emptyValue, List<String> items) {
        if (chipGroup == null || emptyValue == null) return;
        if (items == null || items.isEmpty()) {
            chipGroup.setVisibility(View.GONE);
            chipGroup.removeAllViews();
            emptyValue.setVisibility(View.VISIBLE);
            return;
        }
        emptyValue.setVisibility(View.GONE);
        chipGroup.setVisibility(View.VISIBLE);
        chipGroup.removeAllViews();
        int accentDim = ContextCompat.getColor(requireContext(), R.color.rh_accent_dim);
        for (String item : items) {
            TextView pill = new TextView(requireContext());
            pill.setText(item);
            pill.setTextColor(Color.WHITE);
            pill.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12);
            pill.setBackgroundResource(R.drawable.rounded_category_bg);
            pill.getBackground().setTint(accentDim);
            int hPad = (int) (10 * getResources().getDisplayMetrics().density);
            int vPad = (int) (4 * getResources().getDisplayMetrics().density);
            pill.setPadding(hPad, vPad, hPad, vPad);
            ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            int margin = (int) (6 * getResources().getDisplayMetrics().density);
            lp.setMargins(0, margin, margin, margin);
            pill.setLayoutParams(lp);
            chipGroup.addView(pill);
        }
    }

    private void populateChipGroup(ChipGroup chipGroup, List<String> items) {
        if (chipGroup == null) {
            Log.w(TAG, "ChipGroup is null, skipping population");
            return;
        }

        chipGroup.removeAllViews();
        if (items == null || items.isEmpty()) {
            TextView emptyText = new TextView(requireContext());
            emptyText.setText(R.string.empty_value);
            emptyText.setTextColor(ContextCompat.getColor(requireContext(), R.color.rh_text_tertiary));
            emptyText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13);
            chipGroup.addView(emptyText);
            return;
        }

        int accentDim = ContextCompat.getColor(requireContext(), R.color.rh_accent_dim);
        for (String item : items) {
            TextView pill = new TextView(requireContext());
            pill.setText(item);
            pill.setTextColor(Color.WHITE);
            pill.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12);
            pill.setBackgroundResource(R.drawable.rounded_category_bg);
            pill.getBackground().setTint(accentDim);
            int hPad = (int) (10 * getResources().getDisplayMetrics().density);
            int vPad = (int) (4 * getResources().getDisplayMetrics().density);
            pill.setPadding(hPad, vPad, hPad, vPad);
            ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            int margin = (int) (6 * getResources().getDisplayMetrics().density);
            lp.setMargins(0, margin, margin, margin);
            pill.setLayoutParams(lp);
            chipGroup.addView(pill);
        }
    }

    // ── Display helpers for the new sections ──────────────────────────────

    private String orEmpty(String s) {
        return (s == null || s.isEmpty()) ? getString(R.string.empty_value) : s;
    }

    /** Like orEmpty but returns "" (used for editable dropdown defaults). */
    private String orEmptyStr(String s) {
        return s == null ? "" : s;
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        String cleaned = s.replace("_", " ").replace("-", " ").trim();
        if (cleaned.isEmpty()) return cleaned;
        StringBuilder out = new StringBuilder();
        for (String w : cleaned.split("\\s+")) {
            if (w.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(w.charAt(0)));
            if (w.length() > 1) out.append(w.substring(1).toLowerCase());
        }
        return out.toString();
    }

    private String levelLabel(int level) {
        switch (level) {
            case 0: return "None";
            case 1: return "Light";
            case 2: return "Moderate";
            case 3: return "Heavy";
            case 4: return "Very Heavy";
            default: return getString(R.string.empty_value);
        }
    }

    private String stressLabel(int level) {
        switch (level) {
            case 1: return "Very Low";
            case 2: return "Low";
            case 3: return "Moderate";
            case 4: return "High";
            case 5: return "Very High";
            default: return getString(R.string.empty_value);
        }
    }

    private void displayPersonalInfo() {
        if (emailValue != null) {
            emailValue.setText(orEmpty(userProfile.getEmail()));
        }
        int age = userProfile.calculateAge();
        ageValue.setText(age > 0 ? age + " yrs" : getString(R.string.empty_value));
        genderValue.setText(orEmpty(capitalize(userProfile.getGender())));
        phoneValue.setText(orEmpty(userProfile.getPhoneNumber()));
        locationValue.setText(orEmpty(userProfile.getLocation()));
        occupationValue.setText(orEmpty(capitalize(userProfile.getOccupationType())));
        // Ancestry is stored in a display-ready form (e.g. "White/European"); don't
        // re-case it (capitalize would mangle slashes), just show as-is.
        if (ancestryValue != null) ancestryValue.setText(orEmpty(userProfile.getEthnicity()));
    }

    /** Height, Waist and Recent Weight Change (unit-aware, mirrors weight display). */
    private void displayBodyMetrics() {
        boolean metric = userProfile.isMetric();

        if (heightValue != null) {
            double h = userProfile.getHeight();
            if (h > 0) {
                if (metric) {
                    heightValue.setText(String.format(java.util.Locale.US, "%.0f cm", h));
                } else {
                    double totalIn = h / 2.54;
                    int ft = (int) (totalIn / 12);
                    int in = (int) Math.round(totalIn - ft * 12);
                    heightValue.setText(ft + "' " + in + "\"");
                }
            } else {
                heightValue.setText(getString(R.string.empty_value));
            }
        }

        if (waistValue != null) {
            double w = userProfile.getWaistCircumference();
            if (w > 0) {
                waistValue.setText(metric
                        ? String.format(java.util.Locale.US, "%.0f cm", w)
                        : String.format(java.util.Locale.US, "%.0f in", w / 2.54));
            } else {
                waistValue.setText(getString(R.string.empty_value));
            }
        }

        if (recentWeightChangeValue != null) {
            recentWeightChangeValue.setText(orEmpty(capitalize(userProfile.getRecentWeightChange())));
        }
    }

    /** Shows a conditional follow-up row only when it has a value (keeps cards minimal). */
    private void bindFollowupRow(View row, TextView value, String raw) {
        String v = raw == null ? "" : raw.trim();
        if (v.isEmpty()) {
            if (row != null) row.setVisibility(View.GONE);
            return;
        }
        if (row != null) row.setVisibility(View.VISIBLE);
        if (value != null) value.setText(v); // values like "1-5 years" are already display-ready
    }

    private void displayLifestyle() {
        stressLevelValue.setText(stressLabel(userProfile.getStressLevel()));

        int water = userProfile.getWaterIntake();
        waterIntakeValue.setText(water > 0 ? water + " glasses" : getString(R.string.empty_value));

        int meals = userProfile.getMealsPerDay();
        mealsPerDayValue.setText(meals > 0 ? String.valueOf(meals) : getString(R.string.empty_value));

        screenTimeValue.setText(orEmpty(capitalize(userProfile.getScreenTimeBeforeBed())));
        sunExposureValue.setText(orEmpty(capitalize(userProfile.getSunExposure())));
    }

    private void displayHabits() {
        // Smoking: combine status + level + frequency
        StringBuilder smoking = new StringBuilder();
        if (userProfile.isSmoker()) {
            smoking.append(levelLabel(userProfile.getSmokingLevel()));
            String freq = userProfile.getSmokingFrequency();
            if (freq != null && !freq.isEmpty()) {
                smoking.append(" · ").append(capitalize(freq));
            }
        } else {
            smoking.append("Non-smoker");
        }
        smokingValueText.setText(smoking.toString());

        // Alcohol
        StringBuilder alcohol = new StringBuilder();
        String ac = userProfile.getAlcoholConsumption();
        int al = userProfile.getAlcoholLevel();
        if (al > 0 || (ac != null && !ac.isEmpty() && !"none".equalsIgnoreCase(ac))) {
            alcohol.append(levelLabel(al));
            if (ac != null && !ac.isEmpty()) {
                alcohol.append(" · ").append(capitalize(ac));
            }
        } else {
            alcohol.append("None");
        }
        alcoholValueText.setText(alcohol.toString());

        caffeineValueText.setText(orEmpty(capitalize(userProfile.getCaffeineHabit())));

        // Conditional follow-ups — each row hides itself when empty.
        bindFollowupRow(rowSmokingDuration, smokingDurationValue, userProfile.getSmokingDuration());
        bindFollowupRow(rowCigarettesPerDay, cigarettesPerDayValue, userProfile.getCigarettesPerDay());
        bindFollowupRow(rowLastSmoked, lastSmokedValue, userProfile.getLastSmoked());
        bindFollowupRow(rowDrinksPerWeek, drinksPerWeekValue, userProfile.getDrinksPerWeek());
        bindFollowupRow(rowConditionsDiagnosed, conditionsDiagnosedValue, userProfile.getConditionsDiagnosed());
        bindFollowupRow(rowConditionsMedicated, conditionsMedicatedValue, userProfile.getConditionsMedicated());
    }

    private void displayFamilyHistory() {
        // Use the same small filled-pill style as conditions/allergies/medications so
        // every chip group on the profile screen is one consistent size and shape
        // (previously this drew larger outlined Material Chips).
        List<String> items = userProfile.getFamilyHistory();
        List<String> capitalized = null;
        if (items != null) {
            capitalized = new ArrayList<>();
            for (String item : items) capitalized.add(capitalize(item));
        }
        populateInlineChipGroup(familyHistoryGroup, familyHistoryEmpty, capitalized);

        // Affected relatives — inline chip row (same pattern as conditions/allergies)
        populateInlineChipGroup(familyRelativesGroup, familyRelativesEmpty, userProfile.getFamilyHistoryRelatives());
    }

    private void loadAndDisplayProfile() {
        userProfile = dbHelper.getUserProfile();
        if (userProfile != null) {
            Log.d(TAG, "Profile loaded from local DB");
            displayProfile();
        } else {
            Log.w(TAG, "Local profile missing — fetching from server");
        }
        // Always refresh from the server too, so fields the local row is missing
        // (e.g. date of birth → age, waist) get backfilled. Honors this method's
        // documented "always-on refresh" behaviour.
        refreshProfileFromServer();
    }

    /**
     * Always-on refresh: GETs /api/user/profile and merges into the local SQLite row.
     * Updates the existing row in place (preserving id) when one exists, otherwise inserts.
     * Re-renders without re-running the entry animation when refreshing existing data.
     */
    private void refreshProfileFromServer() {
        Context context = getContext();
        if (context == null) return;
        TokenManager tokenManager = TokenManager.getInstance(context);
        String token = tokenManager.getToken();
        if (token == null) {
            if (userProfile == null) {
                Utilities.toast(context, "Please log in again");
            }
            return;
        }

        String url = ApiConfig.BASE_URL + "/api/user/profile";
        StringRequest request = new StringRequest(Request.Method.GET, url,
                response -> {
                    if (!isAdded()) return;
                    try {
                        JSONObject body = new JSONObject(response);
                        JSONObject user = body.optJSONObject("user");
                        if (user == null) {
                            Log.e(TAG, "Profile response has no 'user' field");
                            return;
                        }
                        UserProfile existing = dbHelper.getUserProfile();
                        boolean hadExisting = existing != null;
                        UserProfile profile = hadExisting ? existing : new UserProfile();
                        applyServerJsonToProfile(profile, user, token);
                        if (hadExisting) {
                            dbHelper.updateUserProfile(profile);
                            Log.d(TAG, "Updated profile from server, id=" + profile.getId());
                        } else {
                            long id = dbHelper.insertUserProfile(profile);
                            Log.d(TAG, "Inserted profile from server, id=" + id);
                        }
                        userProfile = dbHelper.getUserProfile();
                        // AI preferences are NOT stored in SQLite, so the round-trip
                        // above drops them. They're server-authoritative — carry the
                        // freshly-parsed server values onto the display object so the
                        // toggles/segments reflect (and persist) what's in Mongo.
                        if (userProfile != null) {
                            userProfile.setAiTone(profile.getAiTone());
                            userProfile.setAiReplyLength(profile.getAiReplyLength());
                            userProfile.setAiCustomInstructions(profile.getAiCustomInstructions());
                            userProfile.setAiSaveMemories(profile.isAiSaveMemories());
                            userProfile.setAiImproveModel(profile.isAiImproveModel());
                            userProfile.setAiAutofillCards(profile.isAiAutofillCards());
                            userProfile.setAiShowThinking(profile.isAiShowThinking());
                        }
                        if (userProfile != null && isAdded()) {
                            // Suppress entry animation on refresh of an already-painted screen
                            displayProfile(!hadExisting);
                        } else if (userProfile == null && isAdded()) {
                            Utilities.toast(requireContext(), "Could not load profile data");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to parse /api/user/profile response", e);
                        if (userProfile == null && isAdded()) {
                            Utilities.toast(requireContext(), "Could not load profile data");
                        }
                    }
                },
                error -> {
                    Log.e(TAG, "Failed to fetch profile", error);
                    if (userProfile == null && isAdded()) {
                        Utilities.toast(requireContext(), "Could not load profile data");
                    }
                }
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> h = new HashMap<>();
                h.put("Authorization", "Bearer " + token);
                return h;
            }
        };
        Volley.newRequestQueue(context).add(request);
    }

    private void applyServerJsonToProfile(UserProfile profile, JSONObject u, String token) throws JSONException {
        profile.setLoggedIn(true);
        profile.setAuthToken(token);
        profile.setLastLogin(new java.util.Date());

        if (u.has("name")) profile.setName(u.optString("name"));
        if (u.has("email")) profile.setEmail(u.optString("email"));
        profile.setEmailVerified(u.optBoolean("emailVerified", false));
        if (u.has("gender") && !u.isNull("gender")) profile.setGender(u.optString("gender"));
        if (u.has("phoneNumber") && !u.isNull("phoneNumber")) profile.setPhoneNumber(u.optString("phoneNumber"));
        if (u.has("location") && !u.isNull("location")) profile.setLocation(u.optString("location"));
        if (u.has("occupationType") && !u.isNull("occupationType")) profile.setOccupationType(u.optString("occupationType"));
        if (u.has("bloodType") && !u.isNull("bloodType")) profile.setBloodType(u.optString("bloodType"));
        if (u.has("dietType") && !u.isNull("dietType")) profile.setDietType(u.optString("dietType"));
        if (u.has("primaryGoal") && !u.isNull("primaryGoal")) profile.setPrimaryGoal(u.optString("primaryGoal"));

        if (u.has("dateOfBirth") && !u.isNull("dateOfBirth")) {
            try {
                String dobStr = u.getString("dateOfBirth");
                String clean = dobStr.contains("T") ? dobStr.substring(0, dobStr.indexOf("T")) : dobStr;
                profile.setDateOfBirth(new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(clean));
            } catch (Exception ignored) {}
        }

        profile.setHeight(u.optDouble("height", 0));
        profile.setWeight(u.optDouble("weight", 0));
        profile.setTargetWeight(u.optDouble("targetWeight", 0));
        profile.setWaistCircumference(u.optDouble("waistCircumference", 0));
        profile.setWeeklyGoal(u.optDouble("weeklyGoal", 0));
        profile.setRestingHeartRate(u.optInt("restingHeartRate", 0));
        profile.setSystolicBP(u.optInt("systolicBP", 0));
        profile.setDiastolicBP(u.optInt("diastolicBP", 0));
        profile.setActivityLevel(u.optInt("activityLevel", 0));
        profile.setSleepHours(u.optInt("sleepHours", 0));
        profile.setStressLevel(u.optInt("stressLevel", 0));
        profile.setMealsPerDay(u.optInt("mealsPerDay", 0));
        profile.setWaterIntake(u.optInt("waterIntake", 0));
        profile.setSmoker(u.optBoolean("smoker", false));
        profile.setSmokingLevel(u.optInt("smokingLevel", 0));
        profile.setAlcoholLevel(u.optInt("alcoholLevel", 0));
        profile.setMetric(u.optBoolean("isMetric", true));

        if (u.has("smokingFrequency") && !u.isNull("smokingFrequency")) profile.setSmokingFrequency(u.optString("smokingFrequency"));
        if (u.has("alcoholConsumption") && !u.isNull("alcoholConsumption")) profile.setAlcoholConsumption(u.optString("alcoholConsumption"));
        if (u.has("caffeineHabit") && !u.isNull("caffeineHabit")) profile.setCaffeineHabit(u.optString("caffeineHabit"));
        if (u.has("screenTimeBeforeBed") && !u.isNull("screenTimeBeforeBed")) profile.setScreenTimeBeforeBed(u.optString("screenTimeBeforeBed"));
        if (u.has("sunExposure") && !u.isNull("sunExposure")) profile.setSunExposure(u.optString("sunExposure"));
        if (u.has("contraceptionMethod") && !u.isNull("contraceptionMethod")) profile.setContraceptionMethod(u.optString("contraceptionMethod"));

        if (u.has("menstrualStatus") && !u.isNull("menstrualStatus")) profile.setMenstrualStatus(u.optString("menstrualStatus"));
        if (u.has("pregnancyStatus") && !u.isNull("pregnancyStatus")) profile.setPregnancyStatus(u.optString("pregnancyStatus"));
        profile.setAverageCycleLength(u.optInt("averageCycleLength", 0));
        profile.setAveragePeriodLength(u.optInt("averagePeriodLength", 0));

        profile.setMedicalConditions(jsonArrToList(u.optJSONArray("medicalConditions")));
        profile.setMedications(jsonArrToList(u.optJSONArray("medications")));
        profile.setAllergies(jsonArrToList(u.optJSONArray("allergies")));
        profile.setMenstrualSymptoms(jsonArrToList(u.optJSONArray("menstrualSymptoms")));
        profile.setFamilyHistory(jsonArrToList(u.optJSONArray("familyHistory")));
        profile.setFamilyHistoryRelatives(jsonArrToList(u.optJSONArray("familyHistoryRelatives")));

        // Conditional follow-ups
        if (u.has("smokingStatus") && !u.isNull("smokingStatus")) profile.setSmokingStatus(u.optString("smokingStatus"));
        if (u.has("smokingDuration") && !u.isNull("smokingDuration")) profile.setSmokingDuration(u.optString("smokingDuration"));
        if (u.has("cigarettesPerDay") && !u.isNull("cigarettesPerDay")) profile.setCigarettesPerDay(u.optString("cigarettesPerDay"));
        if (u.has("lastSmoked") && !u.isNull("lastSmoked")) profile.setLastSmoked(u.optString("lastSmoked"));
        if (u.has("drinksPerWeek") && !u.isNull("drinksPerWeek")) profile.setDrinksPerWeek(u.optString("drinksPerWeek"));
        if (u.has("conditionsDiagnosed") && !u.isNull("conditionsDiagnosed")) profile.setConditionsDiagnosed(u.optString("conditionsDiagnosed"));
        if (u.has("conditionsMedicated") && !u.isNull("conditionsMedicated")) profile.setConditionsMedicated(u.optString("conditionsMedicated"));
        if (u.has("ethnicity") && !u.isNull("ethnicity")) profile.setEthnicity(u.optString("ethnicity"));
        if (u.has("recentWeightChange") && !u.isNull("recentWeightChange")) profile.setRecentWeightChange(u.optString("recentWeightChange"));
        profile.setMedicationCategories(jsonArrToList(u.optJSONArray("medicationCategories")));

        // AI / Chat preferences (nested object). Missing → keep model defaults.
        JSONObject aiPrefs = u.optJSONObject("aiPreferences");
        if (aiPrefs != null) {
            if (aiPrefs.has("tone") && !aiPrefs.isNull("tone")) profile.setAiTone(aiPrefs.optString("tone", "balanced"));
            if (aiPrefs.has("replyLength") && !aiPrefs.isNull("replyLength")) profile.setAiReplyLength(aiPrefs.optString("replyLength", "balanced"));
            if (aiPrefs.has("customInstructions") && !aiPrefs.isNull("customInstructions")) profile.setAiCustomInstructions(aiPrefs.optString("customInstructions", ""));
            profile.setAiSaveMemories(aiPrefs.optBoolean("saveMemories", true));
            profile.setAiImproveModel(aiPrefs.optBoolean("improveModel", true));
            profile.setAiAutofillCards(aiPrefs.optBoolean("autofillCards", false));
            profile.setAiShowThinking(aiPrefs.optBoolean("showThinking", false));
        }
    }

    private List<String> jsonArrToList(JSONArray arr) {
        List<String> out = new ArrayList<>();
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            String s = arr.optString(i, null);
            if (s != null) out.add(s);
        }
        return out;
    }

    private void updateDisplayedMetrics() {
        if (userProfile == null) {
            Log.e(TAG, "Cannot update metrics - user profile is null");
            return;
        }

        if (userProfile.getWeight() <= 0) {
            weightValue.setText("Not set");
            return;
        }

        if (userProfile.isMetric()) {
            weightValue.setText(String.format("%.1f kg", userProfile.getWeight()));
        } else {
            double lbs = userProfile.getWeight() * 2.20462;
            weightValue.setText(String.format("%.1f lbs", lbs));
        }
    }

    private void displayProfile() {
        displayProfile(true);
    }

    private void displayProfile(boolean animate) {
        try {
            // Basic Info
            profileName.setText(userProfile.getName() != null && !userProfile.getName().isEmpty() ?
                    userProfile.getName() : "User");
            if (profileInitials != null) profileInitials.setText(initialsFor(userProfile.getName()));
            updateVerifiedPill();
            refreshFamilyRequestsBadge();

            // At a Glance: Weight (via updateDisplayedMetrics) + Sleep + Water from the
            // profile, plus live Air Quality fetched from the user's location data.
            updateDisplayedMetrics();

            sleepValue.setText(userProfile.getSleepHours() > 0
                    ? userProfile.getSleepHours() + " h" : "—");
            waterValue.setText(userProfile.getWaterIntake() > 0
                    ? userProfile.getWaterIntake() + " gl" : "—");

            fetchAndShowAqi();

            // Fitness Goals
            primaryGoal.setText(userProfile.getPrimaryGoal() != null && !userProfile.getPrimaryGoal().isEmpty() ?
                    userProfile.getPrimaryGoal() : "Not set");

            if (userProfile.getWeeklyGoal() > 0) {
                weeklyGoal.setText(String.format("Target: %.1f kg/week", userProfile.getWeeklyGoal()));
            } else {
                weeklyGoal.setText("No target set");
            }

            // Health & Lifestyle
            String[] activityLevels = {"Sedentary", "Light", "Moderate", "Active", "Very Active"};
            int activityLevelIndex = userProfile.getActivityLevel();

            // Ensure activity level is within valid range
            if (activityLevelIndex < 1 || activityLevelIndex > 5) {
                activityLevel.setText("Not set");
                Log.d(TAG, "Activity level out of range: " + activityLevelIndex);
            } else {
                activityLevel.setText(activityLevels[activityLevelIndex - 1]);
                Log.d(TAG, "Activity level set to: " + activityLevels[activityLevelIndex - 1]);
            }

            // Sleep Hours
            sleepHours.setText(userProfile.getSleepHours() > 0 ?
                    userProfile.getSleepHours() + " hours" : "Not set");

            // Diet Type
            dietType.setText(userProfile.getDietType() != null && !userProfile.getDietType().isEmpty() ?
                    userProfile.getDietType() : "Not specified");

            // notificationSwitch was removed - notifications are now handled via notification_item click
            metricSwitch.setChecked(userProfile.isMetric());
            // shareProgressSwitch was removed - share progress is now handled via share_progress_button

            // Display Additional Details - medical info, etc.
            displayAdditionalDetails();

            // Personal info, Lifestyle, Habits, Family History
            displayPersonalInfo();
            displayLifestyle();
            displayHabits();
            displayFamilyHistory();

            // AI & Chat preferences
            displayAiPreferences();

            // Header extras (overhaul)
            updatePlanBadge();
            updateCompleteness();

            // Card slide-in animation removed (was distracting on every refresh/tab switch).

        } catch (Exception e) {
            Log.e(TAG, "Error displaying profile: " + e.getMessage(), e);
            Utilities.toast(requireContext(), "Error displaying profile");
        }
    }

    private void animateCards() {
        try {
            if (rootView == null) return;
            ViewGroup container = rootView.findViewById(R.id.profile_content_container);
            if (container == null) return;
            LayoutAnimationController animation = AnimationUtils.loadLayoutAnimation(
                    requireContext(), R.anim.layout_animation_slide_bottom);
            container.setLayoutAnimation(animation);
            container.scheduleLayoutAnimation();
        } catch (Exception e) {
            Log.e(TAG, "Error animating cards", e);
        }
    }

    private int getActivityLevelValue(String level) {
        if (level == null) {
            return 2; // Default to Lightly Active
        }

        switch (level.toLowerCase()) {
            case "sedentary": return 1;
            case "lightly active": return 2;
            case "moderately active": return 3;
            case "very active": return 4;
            case "extremely active": return 5;
            default: return 2; // Default to Lightly Active if not recognized
        }
    }

    private void setupContactImport() {
        // Add this to your button click listener or wherever you want to trigger it
        if (ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, 100);
        } else {
//            importContacts();
        }
    }

    private void importContacts() {
        if (userProfile == null) {
            Utilities.toast(requireContext(), "Profile not loaded");
            return;
        }

        List<ContactUtils.Contact> contacts = ContactUtils.getContacts(requireContext());
        dbHelper.saveContacts(userProfile.getId(), contacts);
        Utilities.toast(requireContext(), "Imported " + contacts.size() + " contacts");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                importContacts();
            } else {

            }
        }
    }

    private void updateProUI() {
        bindMembershipSection();
        updatePlanBadge();
        updatePlanStatusCard();
    }

    /**
     * State-aware Membership section. Mirrors the branching the old
     * "Your Subscription" popup used, but inline in the Plan tab:
     *   · covered family member → a "shared via family plan" note
     *   · family-plan owner      → inline family management (covered count + rows)
     *   · pro (upgradeable)      → Upgrade button (opens the purchase dialog)
     *   · free                   → Upgrade button
     */
    private void bindMembershipSection() {
        if (membershipSubtitle == null || proStatusManager == null) return;

        boolean isPro     = proStatusManager.isProUser();
        boolean isOwner   = proStatusManager.isFamilyPlanOwner();
        boolean isGranted = proStatusManager.isGrantedPro();
        String tier       = proStatusManager.getUserTier();

        // Subtitle
        if (isPro) {
            String name = planDisplayName(tier);
            String expiry = null;
            try { expiry = proStatusManager.getFormattedExpiryDate(); } catch (Exception ignored) {}
            membershipSubtitle.setText(expiry != null ? name + " · Active until " + expiry : name + " · Active");
        } else {
            membershipSubtitle.setText("Free plan · Upgrade for more");
        }

        // Default: hide all state views
        if (membershipUpgradeButton != null) membershipUpgradeButton.setVisibility(View.GONE);
        if (membershipGrantedNote != null)   membershipGrantedNote.setVisibility(View.GONE);
        if (membershipFamilySection != null)  membershipFamilySection.setVisibility(View.GONE);

        if (isGranted) {
            // Covered member — show who shared Pro, no management.
            if (membershipGrantedNote != null) {
                String by = proStatusManager.getProGrantedBy();
                membershipGrantedNote.setText(by != null && !by.isEmpty()
                        ? "Pro shared via family plan by " + by
                        : "Pro shared via family plan");
                membershipGrantedNote.setVisibility(View.VISIBLE);
            }
        } else if (isOwner) {
            // Family owner — inline management.
            if (membershipFamilySection != null) membershipFamilySection.setVisibility(View.VISIBLE);
            loadFamilyMembers();
        } else {
            // Free or individual pro — offer Upgrade (purchase dialog handles tier logic).
            if (membershipUpgradeButton != null) {
                membershipUpgradeButton.setText(isPro ? "Upgrade plan" : "Upgrade");
                membershipUpgradeButton.setVisibility(View.VISIBLE);
            }
        }
    }

    private String planDisplayName(String tier) {
        switch (tier != null ? tier : "pro") {
            case "ultra":         return "RichHealth Ultra";
            case "family":        return "RichHealth Family";
            case "family_member": return "Family Member";
            case "plus":          return "RichHealth Plus";
            default:              return "RichHealth Pro";
        }
    }

    // ── Inline family management (reuses /api/user/relationships + PaymentService) ──

    /** Fetches accepted relatives and renders them into the Membership card. */
    private void loadFamilyMembers() {
        if (familyLoadInFlight) return;
        Context context = getContext();
        if (context == null || familyMembersContainer == null) return;
        String token = TokenManager.getInstance(context).getToken();
        if (token == null) return;

        familyLoadInFlight = true;
        if (familyLoading != null) familyLoading.setVisibility(View.VISIBLE);
        if (familyEmpty != null)   familyEmpty.setVisibility(View.GONE);
        familyMembersContainer.removeAllViews();

        String url = ApiConfig.BASE_URL + "/api/user/relationships";
        StringRequest request = new StringRequest(Request.Method.GET, url,
                response -> {
                    familyLoadInFlight = false;
                    if (!isAdded()) return;
                    if (familyLoading != null) familyLoading.setVisibility(View.GONE);
                    try {
                        JSONObject root = new JSONObject(response);
                        JSONArray arr = root.optJSONArray("relationships");
                        familyMemberMax = root.optInt("maxFamilyMembers", familyMemberMax);
                        familyMembersList.clear();
                        int covered = 0;
                        if (arr != null) {
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject rel = arr.getJSONObject(i);
                                if (!"accepted".equals(rel.optString("status"))) continue;
                                if (rel.optString("userId", "").isEmpty()) continue;
                                familyMembersList.add(rel);
                                if (rel.optBoolean("isCoveredByMyPlan", false)) covered++;
                            }
                        }
                        if (familyCoveredChip != null) {
                            familyCoveredChip.setText(covered + "/" + familyMemberMax + " covered");
                        }
                        renderFamilyMembers();
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing relationships", e);
                        if (familyEmpty != null) familyEmpty.setVisibility(View.VISIBLE);
                    }
                },
                error -> {
                    familyLoadInFlight = false;
                    if (!isAdded()) return;
                    if (familyLoading != null) familyLoading.setVisibility(View.GONE);
                    if (familyEmpty != null) familyEmpty.setVisibility(View.VISIBLE);
                }) {
            @Override public Map<String, String> getHeaders() {
                Map<String, String> h = new HashMap<>();
                h.put("Authorization", "Bearer " + token);
                return h;
            }
        };
        Volley.newRequestQueue(context).add(request);
    }

    private int countCoveredLocal() {
        int n = 0;
        for (JSONObject r : familyMembersList) {
            if (r.optBoolean("isCoveredByMyPlan", false)) n++;
        }
        return n;
    }

    /** Inflates one row per member into the container (new design system look). */
    private void renderFamilyMembers() {
        if (familyMembersContainer == null) return;
        familyMembersContainer.removeAllViews();
        if (familyMembersList.isEmpty()) {
            if (familyEmpty != null) familyEmpty.setVisibility(View.VISIBLE);
            return;
        }
        if (familyEmpty != null) familyEmpty.setVisibility(View.GONE);

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        int currentlyCovered = countCoveredLocal();
        boolean atLimit = currentlyCovered >= familyMemberMax;

        for (JSONObject rel : familyMembersList) {
            View row = inflater.inflate(R.layout.item_plan_family_member, familyMembersContainer, false);

            String n = rel.optString("name", "");
            if (n.isEmpty()) n = rel.optString("email", "Unknown");
            final String name = n;
            String relationship = rel.optString("relationship", "");
            String email = rel.optString("email", "");
            boolean coveredByMe = rel.optBoolean("isCoveredByMyPlan", false);
            boolean isPro = rel.optBoolean("isPro", false);
            final String userId = rel.optString("userId", "");

            ((TextView) row.findViewById(R.id.fm_name)).setText(name);
            String meta = relationship.isEmpty() ? email
                    : (email.isEmpty() ? capitalize(relationship) : capitalize(relationship) + " · " + email);
            ((TextView) row.findViewById(R.id.fm_meta)).setText(meta);

            row.findViewById(R.id.fm_pro_badge).setVisibility(isPro ? View.VISIBLE : View.GONE);
            row.findViewById(R.id.fm_covered_badge).setVisibility(coveredByMe ? View.VISIBLE : View.GONE);

            com.google.android.material.button.MaterialButton action = row.findViewById(R.id.fm_action_button);
            if (coveredByMe) {
                action.setText("Remove");
                action.setTextColor(ContextCompat.getColor(requireContext(), R.color.rh_danger));
                action.setStrokeColor(android.content.res.ColorStateList.valueOf(
                        ContextCompat.getColor(requireContext(), R.color.rh_danger)));
                action.setEnabled(true);
                action.setAlpha(1f);
                action.setOnClickListener(v -> confirmRemoveFromPro(userId, name));
            } else {
                boolean canAdd = !atLimit;
                action.setText(canAdd ? "Add to Pro" : "Plan full");
                action.setTextColor(ContextCompat.getColor(requireContext(), R.color.rh_accent));
                action.setStrokeColor(android.content.res.ColorStateList.valueOf(
                        ContextCompat.getColor(requireContext(), R.color.rh_divider)));
                action.setEnabled(canAdd);
                action.setAlpha(canAdd ? 1f : 0.4f);
                action.setOnClickListener(v -> {
                    if (atLimit) {
                        Utilities.toast(requireContext(), "Plan full — remove someone first to add " + name);
                        return;
                    }
                    confirmAddToPro(userId, name);
                });
            }
            familyMembersContainer.addView(row);
        }
    }

    private void confirmAddToPro(String memberId, String name) {
        DialogUtils.showConfirmDialog(requireContext(),
                "Add to Pro Plan",
                "Add " + name + " to your plan? They'll get all premium features included.",
                "Add", "Cancel", false,
                () -> {
                    SimpleProgress p = SimpleProgress.show(requireActivity(), "Adding " + name + "…");
                    paymentService.addFamilyMemberDirect(memberId, new PaymentService.PaymentCallback() {
                        @Override public void onSuccess(ProStatusResult r) {
                            p.hide();
                            if (!isAdded()) return;
                            Utilities.toast(requireContext(), name + " added");
                            loadFamilyMembers();
                        }
                        @Override public void onError(String e) {
                            p.hide();
                            if (!isAdded()) return;
                            Utilities.toast(requireContext(), "Failed: " + e);
                        }
                    });
                });
    }

    private void confirmRemoveFromPro(String memberId, String name) {
        DialogUtils.showConfirmDialog(requireContext(),
                "Remove from Pro",
                "Remove " + name + " from your family plan?",
                "Remove", "Cancel", true,
                () -> {
                    SimpleProgress p = SimpleProgress.show(requireActivity(), "Removing " + name + "…");
                    paymentService.removeFamilyMember(memberId, new PaymentService.PaymentCallback() {
                        @Override public void onSuccess(ProStatusResult r) {
                            p.hide();
                            if (!isAdded()) return;
                            Utilities.toast(requireContext(), name + " removed");
                            loadFamilyMembers();
                        }
                        @Override public void onError(String e) {
                            p.hide();
                            if (!isAdded()) return;
                            Utilities.toast(requireContext(), "Failed: " + e);
                        }
                    });
                });
    }

    /**
     * Save profile to server, then update local database with server response
     */
    // Modify existing method to support additional fields
    private void saveProfileToServer(UserProfile profile) {
        Context context = getContext();
        if (context == null) return; // Fragment detached, skip operation safely

        try {
            // Get token for authentication
            TokenManager tokenManager = TokenManager.getInstance(context);
            String token = tokenManager.getToken();
            String userId = tokenManager.getUserId();

            if (token == null || userId == null) {
                Utilities.toast(context, "Authentication error. Please log in again.");
                return;
            }

            // Create JSON object for request
            JSONObject profileData = new JSONObject();

            // Existing fields
            profileData.put("name", profile.getName());
            profileData.put("email", profile.getEmail());
            profileData.put("height", profile.getHeight());
            profileData.put("weight", profile.getWeight());
            // Heart rate & blood pressure are owned by the Health Data screen; the
            // profile no longer sends them (avoids overwriting real measurements with 0).
            profileData.put("primaryGoal", profile.getPrimaryGoal());
            profileData.put("weeklyGoal", profile.getWeeklyGoal());
            profileData.put("activityLevel", profile.getActivityLevel());
            profileData.put("sleepHours", profile.getSleepHours());
            profileData.put("dietType", profile.getDietType());

            // New additional fields
            profileData.put("bloodType", profile.getBloodType());
            profileData.put("targetWeight", profile.getTargetWeight());
            profileData.put("waistCircumference", profile.getWaistCircumference());
            profileData.put("isMetric", profile.isMetric());

            // Personal / lifestyle / habits — kept aligned with onboarding payload
            profileData.put("gender", profile.getGender());
            profileData.put("phoneNumber", profile.getPhoneNumber());
            if (profile.getDateOfBirth() != null) {
                profileData.put("dateOfBirth",
                        new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(profile.getDateOfBirth()));
            }
            profileData.put("height", profile.getHeight());
            profileData.put("occupationType", profile.getOccupationType());
            profileData.put("stressLevel", profile.getStressLevel());
            profileData.put("waterIntake", profile.getWaterIntake());
            profileData.put("mealsPerDay", profile.getMealsPerDay());
            profileData.put("screenTimeBeforeBed", profile.getScreenTimeBeforeBed());
            profileData.put("sunExposure", profile.getSunExposure());
            profileData.put("smoker", profile.isSmoker());
            profileData.put("smokingLevel", profile.getSmokingLevel());
            profileData.put("smokingFrequency", profile.getSmokingFrequency());
            profileData.put("alcoholConsumption", profile.getAlcoholConsumption());
            profileData.put("alcoholLevel", profile.getAlcoholLevel());
            profileData.put("caffeineHabit", profile.getCaffeineHabit());
            JSONArray familyHistoryArray = new JSONArray(
                    profile.getFamilyHistory() != null ? profile.getFamilyHistory() : new ArrayList<>());
            profileData.put("familyHistory", familyHistoryArray);

            // Convert lists to JSON arrays
            JSONArray medicalConditionsArray = new JSONArray(profile.getMedicalConditions());
            JSONArray medicationsArray = new JSONArray(profile.getMedications());
            JSONArray allergiesArray = new JSONArray(profile.getAllergies());

            profileData.put("medicalConditions", medicalConditionsArray);
            profileData.put("medications", medicationsArray);
            profileData.put("allergies", allergiesArray);

            // Menstrual health fields
            profileData.put("menstrualStatus", profile.getMenstrualStatus());
            profileData.put("averageCycleLength", profile.getAverageCycleLength());
            profileData.put("averagePeriodLength", profile.getAveragePeriodLength());
            profileData.put("contraceptionMethod", profile.getContraceptionMethod());
            profileData.put("pregnancyStatus", profile.getPregnancyStatus());
            JSONArray menstrualSymptomsArray = new JSONArray(profile.getMenstrualSymptoms());
            profileData.put("menstrualSymptoms", menstrualSymptomsArray);

            // Conditional follow-ups
            profileData.put("smokingStatus", profile.getSmokingStatus());
            profileData.put("smokingDuration", profile.getSmokingDuration());
            profileData.put("cigarettesPerDay", profile.getCigarettesPerDay());
            profileData.put("lastSmoked", profile.getLastSmoked());
            profileData.put("drinksPerWeek", profile.getDrinksPerWeek());
            profileData.put("conditionsDiagnosed", profile.getConditionsDiagnosed());
            profileData.put("conditionsMedicated", profile.getConditionsMedicated());
            profileData.put("familyHistoryRelatives", new JSONArray(
                    profile.getFamilyHistoryRelatives() != null ? profile.getFamilyHistoryRelatives() : new ArrayList<>()));
            profileData.put("ethnicity", profile.getEthnicity());
            profileData.put("recentWeightChange", profile.getRecentWeightChange());
            profileData.put("medicationCategories", new JSONArray(
                    profile.getMedicationCategories() != null ? profile.getMedicationCategories() : new ArrayList<>()));

            // AI / Chat preferences — sent as a nested object matching the schema
            JSONObject aiPrefs = new JSONObject();
            aiPrefs.put("tone", profile.getAiTone());
            aiPrefs.put("replyLength", profile.getAiReplyLength());
            aiPrefs.put("customInstructions", profile.getAiCustomInstructions() != null ? profile.getAiCustomInstructions() : "");
            aiPrefs.put("saveMemories", profile.isAiSaveMemories());
            aiPrefs.put("improveModel", profile.isAiImproveModel());
            aiPrefs.put("autofillCards", profile.isAiAutofillCards());
            aiPrefs.put("showThinking", profile.isAiShowThinking());
            profileData.put("aiPreferences", aiPrefs);

            SimpleProgress progress = SimpleProgress.show(requireActivity(), "Saving profile...");
            String url = ApiConfig.BASE_URL + "/api/user/profile";
            StringRequest request = new StringRequest(Request.Method.PUT, url,
                    response -> {
                        ApiConfig.logRestCall(url, true, "Profile updated");
                        progress.hide();
                        Utilities.toast(requireContext(), "Profile updated successfully");
                        loadAndDisplayProfile();
                    },
                    error -> {
                        ApiConfig.logRestCall(url, false, error.toString());
                        progress.hide();
                        Log.e(TAG, "Error updating profile", error);
                        Utilities.toast(requireContext(), "Failed to update profile");
                    }
            ) {
                @Override
                public byte[] getBody() {
                    return profileData.toString().getBytes(StandardCharsets.UTF_8);
                }

                @Override
                public String getBodyContentType() {
                    return "application/json; charset=utf-8";
                }

                @Override
                public Map<String, String> getHeaders() throws AuthFailureError {
                    Map<String, String> headers = new HashMap<>();
                    headers.put("Authorization", "Bearer " + token);
                    return headers;
                }
            };

            // Add to request queue
            RequestQueue queue = Volley.newRequestQueue(context);
            queue.add(request);
        } catch (JSONException e) {
            Log.e(TAG, "Error creating JSON for profile update", e);
            Utilities.toast(requireContext(), "Error preparing profile data");
        }
    }

    /**
     * Save profile to local SQLite database
     */
    private void saveProfileToLocalDatabase() {
        try {
            Log.d(TAG, "Saving profile to local database. ID: " + userProfile.getId() +
                    ", Name: " + userProfile.getName() +
                    ", Activity Level: " + userProfile.getActivityLevel());

            long result = dbHelper.updateUserProfile(userProfile);
            Log.d(TAG, "Database update result: " + result);

            // Refresh UI with updated data
            loadAndDisplayProfile();
        } catch (Exception e) {
            Log.e(TAG, "Error saving to local database", e);
            Utilities.toast(requireContext(), "Error saving profile locally: " + e.getMessage());
        }
    }

    /**
     * Update the showEditProfileDialog method to use this flow
     */

    // ── Display-label → backend-value maps (kept aligned with onboarding/signup) ──
    // Major hereditary / chronic conditions offered as searchable suggestions for
    // Family History. Users can still type a custom entry not on this list.
    private static final String[] FAMILY_HISTORY_OPTIONS = new String[]{
            "Diabetes (Type 2)", "Diabetes (Type 1)", "High Blood Pressure", "High Cholesterol",
            "Heart Disease", "Heart Attack", "Stroke", "Obesity",
            "Breast Cancer", "Ovarian Cancer", "Prostate Cancer", "Colorectal Cancer",
            "Lung Cancer", "Stomach Cancer", "Cancer (Other)",
            "Asthma", "COPD", "Tuberculosis", "Thyroid Disorder", "Hypothyroidism", "Hyperthyroidism",
            "Kidney Disease", "Liver Disease", "Hepatitis",
            "Alzheimer's / Dementia", "Parkinson's Disease", "Epilepsy", "Migraine",
            "Depression", "Anxiety", "Bipolar Disorder", "Schizophrenia",
            "Arthritis", "Rheumatoid Arthritis", "Osteoporosis", "Gout",
            "Anemia", "Sickle Cell Disease", "Thalassemia", "Hemophilia",
            "PCOS", "Endometriosis", "Celiac Disease", "Crohn's Disease", "Ulcerative Colitis",
            "Psoriasis", "Eczema", "Allergies", "Glaucoma", "Cataract",
            "Autoimmune Disorder", "Congenital Heart Defect"
    };

    private static final java.util.LinkedHashMap<String, String> GOAL_OPTIONS = new java.util.LinkedHashMap<String, String>() {{
        put("Lose Weight", "Weight Loss");
        put("Build Muscle", "Muscle Gain");
        put("Stay Fit", "Improve Fitness");
        put("Manage Condition", "Manage a Health Condition");
        put("Boost Energy", "Boost Energy");
        put("Sleep Better", "Improve Sleep");
        put("Eat Healthier", "Eat Healthier");
        put("Mental Health", "Improve Mental Health");
    }};
    private static final java.util.LinkedHashMap<String, Integer> ACTIVITY_OPTIONS = new java.util.LinkedHashMap<String, Integer>() {{
        put("Mostly Sitting", 1);
        put("Light Activity", 2);
        put("Moderately Active", 3);
        put("Very Active", 4);
        put("Athlete", 5);
    }};
    private static final java.util.LinkedHashMap<String, String> DIET_OPTIONS = new java.util.LinkedHashMap<String, String>() {{
        put("Everything", "Regular");
        put("Vegetarian", "Vegetarian");
        put("Vegan", "Vegan");
        put("Keto", "Keto");
        put("Mediterranean", "Mediterranean");
        put("Gluten-Free", "Gluten-Free");
        put("Other", "Other");
    }};
    private static final java.util.LinkedHashMap<String, String> OCCUPATION_OPTIONS = new java.util.LinkedHashMap<String, String>() {{
        put("Desk / Office", "desk");
        put("Physical Labour", "physical");
        put("Healthcare", "healthcare");
        put("Student", "student");
        put("Work from Home", "remote");
        put("Retired / Home", "retired");
    }};
    private static final java.util.LinkedHashMap<String, Integer> STRESS_OPTIONS = new java.util.LinkedHashMap<String, Integer>() {{
        put("Rarely", 1);
        put("Sometimes", 2);
        put("Often", 3);
        put("Almost Always", 4);
    }};
    private static final java.util.LinkedHashMap<String, Integer> WATER_OPTIONS = new java.util.LinkedHashMap<String, Integer>() {{
        put("I forget to drink", 2);
        put("4–6 glasses", 5);
        put("7–9 glasses", 8);
        put("10+ (hydration champ!)", 10);
    }};
    private static final java.util.LinkedHashMap<String, Integer> MEALS_OPTIONS = new java.util.LinkedHashMap<String, Integer>() {{
        put("1–2 meals", 2);
        put("3 meals", 3);
        put("4–5 meals", 4);
        put("6+ meals", 6);
    }};
    private static final java.util.LinkedHashMap<String, String> SCREEN_TIME_OPTIONS = new java.util.LinkedHashMap<String, String>() {{
        put("I stop 1hr+ before", "low");
        put("About 30 mins", "moderate");
        put("Right until I try to sleep", "high");
        put("I fall asleep with it", "very_high");
    }};
    private static final java.util.LinkedHashMap<String, String> SUN_OPTIONS = new java.util.LinkedHashMap<String, String>() {{
        put("Mostly indoors", "low");
        put("Some outdoor time", "moderate");
        put("Outdoors most of the day", "high");
    }};
    // Smoking display → tag (then mapped to smoker/level/frequency tuple at save time)
    private static final java.util.LinkedHashMap<String, String> SMOKING_OPTIONS = new java.util.LinkedHashMap<String, String>() {{
        put("Never, not even once", "never");
        put("I quit — proud of it", "ex");
        put("Only socially", "social");
        put("Sometimes", "occasional");
        put("Daily habit", "regular");
    }};
    private static final java.util.LinkedHashMap<String, String> ALCOHOL_OPTIONS = new java.util.LinkedHashMap<String, String>() {{
        put("I don't drink", "None");
        put("Special occasions", "Special Occasions");
        put("Socially / weekends", "Socially");
        put("Few times a week", "Regularly");
        put("Almost daily", "Frequently");
    }};
    private static final java.util.LinkedHashMap<String, String> CAFFEINE_OPTIONS = new java.util.LinkedHashMap<String, String>() {{
        put("No caffeine", "none");
        put("Tea person", "tea");
        put("Coffee lover", "coffee");
        put("Energy drinks", "energy_drinks");
    }};
    // Contraception — labels/values aligned with onboarding's contraception step.
    private static final java.util.LinkedHashMap<String, String> CONTRACEPTION_OPTIONS = new java.util.LinkedHashMap<String, String>() {{
        put("None", "none");
        put("Pill", "pill");
        put("IUD", "iud");
        put("Condom", "condom");
        put("Implant/Injection", "implant");
    }};

    /** Find the display label whose value equals the given backend value (case-insensitive for strings). */
    private static <V> String labelForValue(java.util.LinkedHashMap<String, V> map, V value) {
        if (value == null) return "";
        for (Map.Entry<String, V> e : map.entrySet()) {
            V v = e.getValue();
            if (v == null) continue;
            if (v instanceof String && value instanceof String) {
                if (((String) v).equalsIgnoreCase((String) value)) return e.getKey();
            } else if (v.equals(value)) {
                return e.getKey();
            }
        }
        return "";
    }

    private static String[] keysArray(java.util.LinkedHashMap<String, ?> map) {
        return map.keySet().toArray(new String[0]);
    }

    private void showEditProfileDialog() {
        // Create all fields for the edit dialog
        String[] activityLevels = keysArray(ACTIVITY_OPTIONS);
        String[] dietTypes = keysArray(DIET_OPTIONS);
        String[] goals = keysArray(GOAL_OPTIONS);
        String[] bloodTypes = {"A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"};
        String[] genders = {"Male", "Female", "Other"};
        String[] occupations = keysArray(OCCUPATION_OPTIONS);
        String[] stressLevels = keysArray(STRESS_OPTIONS);
        String[] waterOptions = keysArray(WATER_OPTIONS);
        String[] mealsOptions = keysArray(MEALS_OPTIONS);
        String[] screenTimes = keysArray(SCREEN_TIME_OPTIONS);
        String[] sunExposures = keysArray(SUN_OPTIONS);
        String[] smokingChoices = keysArray(SMOKING_OPTIONS);
        String[] alcoholChoices = keysArray(ALCOHOL_OPTIONS);
        String[] caffeineChoices = keysArray(CAFFEINE_OPTIONS);

        // Get current values with safe defaults
        String currentName = userProfile.getName() != null ? userProfile.getName() : "";
        String currentEmail = userProfile.getEmail() != null ? userProfile.getEmail() : "";
        String currentWeight = userProfile.getWeight() > 0 ? String.valueOf(userProfile.getWeight()) : "";
        String currentPrimaryGoalLabel = labelForValue(GOAL_OPTIONS, userProfile.getPrimaryGoal());
        if (currentPrimaryGoalLabel.isEmpty()) currentPrimaryGoalLabel = goals[2];
        String currentWeeklyGoal = userProfile.getWeeklyGoal() > 0 ? String.valueOf(userProfile.getWeeklyGoal()) : "0.5";
        String currentActivityLabel = labelForValue(ACTIVITY_OPTIONS, userProfile.getActivityLevel());
        if (currentActivityLabel.isEmpty()) currentActivityLabel = activityLevels[1];
        String currentSleepHours = userProfile.getSleepHours() > 0 ? String.valueOf(userProfile.getSleepHours()) : "8";
        String currentDietLabel = labelForValue(DIET_OPTIONS, userProfile.getDietType());
        if (currentDietLabel.isEmpty()) currentDietLabel = dietTypes[0];
        String currentBloodType = userProfile.getBloodType() != null ? userProfile.getBloodType() : "";

        // Personal info
        String currentGender = userProfile.getGender() != null ? capitalize(userProfile.getGender()) : "";
        String currentPhone = userProfile.getPhoneNumber() != null ? userProfile.getPhoneNumber() : "";
        String currentLocation = userProfile.getLocation() != null ? userProfile.getLocation() : "";
        String currentDob = "";
        if (userProfile.getDateOfBirth() != null) {
            currentDob = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(userProfile.getDateOfBirth());
        }
        String currentHeight = userProfile.getHeight() > 0 ? String.valueOf(userProfile.getHeight()) : "";
        String currentWaist = userProfile.getWaistCircumference() > 0 ? String.valueOf(userProfile.getWaistCircumference()) : "";
        String currentOccupationLabel = labelForValue(OCCUPATION_OPTIONS, userProfile.getOccupationType());

        // Lifestyle
        String currentStressLabel = labelForValue(STRESS_OPTIONS, userProfile.getStressLevel());
        String currentWaterLabel = labelForValue(WATER_OPTIONS, userProfile.getWaterIntake());
        String currentMealsLabel = labelForValue(MEALS_OPTIONS, userProfile.getMealsPerDay());
        String currentScreenLabel = labelForValue(SCREEN_TIME_OPTIONS, userProfile.getScreenTimeBeforeBed());
        String currentSunLabel = labelForValue(SUN_OPTIONS, userProfile.getSunExposure());

        // Habits — derive smoking choice from smoker + level
        String currentSmokingLabel;
        if (!userProfile.isSmoker() && userProfile.getSmokingLevel() == 0) {
            // Never vs ex — best-guess via smokingFrequency text; default to "Never, not even once"
            String freq = userProfile.getSmokingFrequency();
            currentSmokingLabel = (freq != null && freq.toLowerCase().contains("ex"))
                    ? "I quit — proud of it" : "Never, not even once";
        } else if (!userProfile.isSmoker() && userProfile.getSmokingLevel() == 1) {
            currentSmokingLabel = "Only socially";
        } else if (userProfile.isSmoker() && userProfile.getSmokingLevel() <= 2) {
            currentSmokingLabel = "Sometimes";
        } else {
            currentSmokingLabel = "Daily habit";
        }
        String currentAlcoholLabel = labelForValue(ALCOHOL_OPTIONS, userProfile.getAlcoholConsumption());
        String currentCaffeineLabel = labelForValue(CAFFEINE_OPTIONS, userProfile.getCaffeineHabit());

        // Family History (comma-separated)
        String currentFamilyHistory = userProfile.getFamilyHistory() != null
                ? String.join(", ", userProfile.getFamilyHistory()) : "";

        // Prepare list inputs
        String currentMedicalConditions = userProfile.getMedicalConditions() != null
                ? String.join(", ", userProfile.getMedicalConditions())
                : "";
        String currentMedications = userProfile.getMedications() != null
                ? String.join(", ", userProfile.getMedications())
                : "";
        String currentAllergies = userProfile.getAllergies() != null
                ? String.join(", ", userProfile.getAllergies())
                : "";

        // Menstrual fields (conditional)
        String[] menstrualStatuses = {"regular", "irregular", "perimenopause", "menopause", "prefer_not_to_say", "not_applicable"};
        String[] pregnancyStatuses = {"not_pregnant", "pregnant", "postpartum", "trying_to_conceive", "not_applicable"};
        String currentMenstrualStatus = userProfile.getMenstrualStatus() != null ? userProfile.getMenstrualStatus() : "not_applicable";
        String currentCycleLength = userProfile.getAverageCycleLength() > 0 ? String.valueOf(userProfile.getAverageCycleLength()) : "28";
        String currentPeriodLength = userProfile.getAveragePeriodLength() > 0 ? String.valueOf(userProfile.getAveragePeriodLength()) : "5";
        String currentMenstrualSymptoms = userProfile.getMenstrualSymptoms() != null
                ? String.join(", ", userProfile.getMenstrualSymptoms()) : "";
        String currentPregnancyStatus = userProfile.getPregnancyStatus() != null ? userProfile.getPregnancyStatus() : "not_applicable";

        boolean showMenstrual = isFemaleUser() || !"not_applicable".equals(currentMenstrualStatus);

        // Build field list dynamically
        List<DialogUtils.DialogField> fieldList = new ArrayList<>();

        // Basic Info
        fieldList.add(DialogUtils.DialogField.section("Basic Info"));
        fieldList.add(new DialogUtils.DialogField("name", "Name",
                android.text.InputType.TYPE_TEXT_VARIATION_PERSON_NAME, currentName));
        fieldList.add(new DialogUtils.DialogField("email", "Email",
                android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, currentEmail));
        fieldList.add(new DialogUtils.DialogField("phoneNumber", "Phone Number",
                android.text.InputType.TYPE_CLASS_PHONE, currentPhone));
        fieldList.add(new DialogUtils.DialogField("location", "Location",
                android.text.InputType.TYPE_CLASS_TEXT, currentLocation));
        fieldList.add(DialogUtils.DialogField.date("dateOfBirth", "Date of Birth", currentDob));
        fieldList.add(new DialogUtils.DialogField("gender", "Gender", genders, currentGender));

        // Health Metrics — heart rate & blood pressure live in the Health Data
        // collection screen (as measurements), so they are not edited here.
        fieldList.add(DialogUtils.DialogField.section("Health Metrics"));
        fieldList.add(new DialogUtils.DialogField("height", "Height (cm)",
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL, currentHeight));
        fieldList.add(new DialogUtils.DialogField("weight", "Weight (kg)",
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL, currentWeight));
        fieldList.add(new DialogUtils.DialogField("waistCircumference", "Waist (cm)",
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL, currentWaist));

        // Fitness Goals
        fieldList.add(DialogUtils.DialogField.section("Fitness Goals"));
        fieldList.add(new DialogUtils.DialogField("primaryGoal", "Primary Goal", goals, currentPrimaryGoalLabel));
        fieldList.add(new DialogUtils.DialogField("weeklyGoal", "Weekly Goal (kg)",
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL, currentWeeklyGoal));

        // Health & Lifestyle
        fieldList.add(DialogUtils.DialogField.section("Health & Lifestyle"));
        fieldList.add(new DialogUtils.DialogField("activityLevel", "Activity Level", activityLevels, currentActivityLabel));
        fieldList.add(new DialogUtils.DialogField("occupationType", "Occupation", occupations, currentOccupationLabel));
        fieldList.add(new DialogUtils.DialogField("sleepHours", "Sleep Hours",
                android.text.InputType.TYPE_CLASS_NUMBER, currentSleepHours));
        fieldList.add(new DialogUtils.DialogField("dietType", "Diet Type", dietTypes, currentDietLabel));
        fieldList.add(new DialogUtils.DialogField("mealsPerDay", "Meals Per Day", mealsOptions, currentMealsLabel));
        fieldList.add(new DialogUtils.DialogField("waterIntake", "Water Intake", waterOptions, currentWaterLabel));
        fieldList.add(new DialogUtils.DialogField("stressLevel", "Stress Level", stressLevels, currentStressLabel));
        fieldList.add(new DialogUtils.DialogField("screenTimeBeforeBed", "Screen Time Before Bed", screenTimes, currentScreenLabel));
        fieldList.add(new DialogUtils.DialogField("sunExposure", "Sun Exposure", sunExposures, currentSunLabel));

        // Habits
        fieldList.add(DialogUtils.DialogField.section("Habits"));
        fieldList.add(new DialogUtils.DialogField("smokingChoice", "Smoking", smokingChoices, currentSmokingLabel));
        fieldList.add(new DialogUtils.DialogField("alcoholConsumption", "Alcohol", alcoholChoices, currentAlcoholLabel));
        fieldList.add(new DialogUtils.DialogField("caffeineHabit", "Caffeine", caffeineChoices, currentCaffeineLabel));

        // Habit / condition follow-ups
        fieldList.add(new DialogUtils.DialogField("smokingDuration", "Years Smoked",
                new String[]{"", "<1 year", "1-5 years", "5-10 years", "10+ years"},
                orEmptyStr(userProfile.getSmokingDuration())));
        fieldList.add(new DialogUtils.DialogField("cigarettesPerDay", "Cigarettes / day",
                new String[]{"", "<5", "5-10", "10-20", "20+"},
                orEmptyStr(userProfile.getCigarettesPerDay())));
        fieldList.add(new DialogUtils.DialogField("lastSmoked", "Last Smoked",
                new String[]{"", "This week", "This month", "This year", "Over a year ago"},
                orEmptyStr(userProfile.getLastSmoked())));
        fieldList.add(new DialogUtils.DialogField("drinksPerWeek", "Drinks / week",
                new String[]{"", "1-2", "3-5", "6-10", "10+"},
                orEmptyStr(userProfile.getDrinksPerWeek())));
        fieldList.add(new DialogUtils.DialogField("conditionsDiagnosed", "Condition Diagnosed",
                new String[]{"", "<1 year", "1-5 years", "5-10 years", "10+ years"},
                orEmptyStr(userProfile.getConditionsDiagnosed())));
        fieldList.add(new DialogUtils.DialogField("conditionsMedicated", "On Medication For It",
                new String[]{"", "Yes", "Some", "No"},
                orEmptyStr(userProfile.getConditionsMedicated())));

        // Family History
        fieldList.add(DialogUtils.DialogField.section("Family & Predictive"));
        fieldList.add(DialogUtils.DialogField.multiSelect("familyHistory", "Family History",
                FAMILY_HISTORY_OPTIONS, currentFamilyHistory));
        fieldList.add(new DialogUtils.DialogField("familyRelatives", "Affected Relatives (comma-separated)",
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE,
                userProfile.getFamilyHistoryRelatives() != null ? String.join(", ", userProfile.getFamilyHistoryRelatives()) : ""));

        // Predictive extras
        fieldList.add(new DialogUtils.DialogField("ethnicity", "Ancestry",
                new String[]{"", "South Asian", "East Asian", "Southeast Asian", "Middle Eastern",
                        "White/European", "Black/African", "Hispanic/Latino", "Mixed/Other", "Prefer not to say"},
                orEmptyStr(userProfile.getEthnicity())));
        fieldList.add(new DialogUtils.DialogField("recentWeightChange", "Recent Weight Change",
                new String[]{"", "Gained", "Lost", "Stable", "Not sure"},
                orEmptyStr(userProfile.getRecentWeightChange())));
        fieldList.add(new DialogUtils.DialogField("medicationCategories", "Medication Types (comma-separated)",
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE,
                userProfile.getMedicationCategories() != null ? String.join(", ", userProfile.getMedicationCategories()) : ""));

        // Additional Details
        fieldList.add(DialogUtils.DialogField.section("Medical Details"));
        fieldList.add(new DialogUtils.DialogField("bloodType", "Blood Type", bloodTypes, currentBloodType));
        fieldList.add(new DialogUtils.DialogField("medicalConditions", "Medical Conditions (comma-separated)",
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE, currentMedicalConditions));
        fieldList.add(new DialogUtils.DialogField("medications", "Medications (comma-separated)",
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE, currentMedications));
        fieldList.add(new DialogUtils.DialogField("allergies", "Allergies (comma-separated)",
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE, currentAllergies));

        // Menstrual fields (only if currently applicable)
        if (showMenstrual) {
            fieldList.add(DialogUtils.DialogField.section("Reproductive Health"));
            fieldList.add(new DialogUtils.DialogField("menstrualStatus", "Cycle Status", menstrualStatuses, currentMenstrualStatus));
            fieldList.add(new DialogUtils.DialogField("averageCycleLength", "Avg Cycle Length (days)",
                    android.text.InputType.TYPE_CLASS_NUMBER, currentCycleLength));
            fieldList.add(new DialogUtils.DialogField("averagePeriodLength", "Avg Period Length (days)",
                    android.text.InputType.TYPE_CLASS_NUMBER, currentPeriodLength));
            fieldList.add(new DialogUtils.DialogField("menstrualSymptoms", "Cycle Symptoms (comma-separated)",
                    android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE, currentMenstrualSymptoms));
            fieldList.add(new DialogUtils.DialogField("pregnancyStatus", "Pregnancy Status", pregnancyStatuses, currentPregnancyStatus));
            fieldList.add(new DialogUtils.DialogField("contraceptionMethod", "Contraception",
                    keysArray(CONTRACEPTION_OPTIONS), labelForValue(CONTRACEPTION_OPTIONS, userProfile.getContraceptionMethod())));
        }

        DialogUtils.DialogField[] fields = fieldList.toArray(new DialogUtils.DialogField[0]);

        DialogUtils.showEditDialog(
                requireContext(),
                "Edit Profile",
                fields,
                values -> {
                    try {
                        Log.d(TAG, "User submitted profile edit form");

                        // Update local UserProfile object with new values
                        updateUserProfileWithValues(values);

                        saveProfileToServer(userProfile);
                        saveProfileToLocalDatabase();
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Number format error while updating profile", e);
                        Utilities.toast(requireContext(), "Invalid number format");
                    } catch (Exception e) {
                        Log.e(TAG, "Error updating profile", e);
                        Utilities.toast(requireContext(), "Error: " + e.getMessage());
                    }
                },
                "Change Password",
                () -> showChangePasswordDialog()
        );
    }


    // ══════════════════════ TABS + HEADER ══════════════════════

    private void setupTabs() {
        if (tabBtnProfile != null) tabBtnProfile.setOnClickListener(v -> selectTab(0));
        if (tabBtnSettings != null) tabBtnSettings.setOnClickListener(v -> selectTab(1));
        if (tabBtnPlan != null) tabBtnPlan.setOnClickListener(v -> selectTab(2));
        if (viewFullPlanButton != null) {
            viewFullPlanButton.setOnClickListener(v ->
                    Utils.UsageBottomSheet.show(requireActivity()));
        }
        selectTab(0);
    }

    /** 0 = Profile, 1 = Settings, 2 = Plan */
    private void selectTab(int index) {
        if (tabProfile != null) tabProfile.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        if (tabSettings != null) tabSettings.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        if (tabPlan != null) tabPlan.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
        styleTab(tabBtnProfile, tabLabelProfile, tabIconProfile, index == 0);
        styleTab(tabBtnSettings, tabLabelSettings, tabIconSettings, index == 1);
        styleTab(tabBtnPlan, tabLabelPlan, tabIconPlan, index == 2);

        if (index == 2) {
            updatePlanStatusCard();
        }
    }

    /** Header of the Plan tab — current tier name + a short status line. */
    private void updatePlanStatusCard() {
        if (planCurrentName == null || proStatusManager == null) return;
        String tier = proStatusManager.getUserTier();
        boolean isPro = proStatusManager.isProUser();
        String name;
        switch (tier != null ? tier : "free") {
            case "ultra":  name = "RichHealth Ultra";  break;
            case "pro":    name = "RichHealth Pro";    break;
            case "plus":   name = "RichHealth Plus";   break;
            case "family": name = "RichHealth Family"; break;
            case "family_member": name = "Family Member"; break;
            default:       name = "Free Plan";         break;
        }
        planCurrentName.setText(name);
        if (planStatusChip != null) {
            planStatusChip.setText(isPro ? "ACTIVE" : "FREE");
            planStatusChip.setTextColor(isPro ? Color.parseColor("#4CAF50") : Color.WHITE);
            planStatusChip.getBackground().setTint(isPro ? Color.parseColor("#1A4CAF50") : Color.parseColor("#1A1A1A"));
        }
        if (planCurrentDesc != null) {
            String expiry = null;
            try { expiry = proStatusManager.getFormattedExpiryDate(); } catch (Exception ignored) {}
            if (isPro && expiry != null) {
                planCurrentDesc.setText("Active until " + expiry + " · all features unlocked.");
            } else if (isPro) {
                planCurrentDesc.setText("All features unlocked.");
            } else {
                planCurrentDesc.setText("Track your limits across chat, reports and analysis. Upgrade for more.");
            }
        }
        // Keep the Membership section in sync whenever the Plan tab is shown.
        bindMembershipSection();
    }

    private void styleTab(View container, TextView label, android.widget.ImageView icon, boolean selected) {
        if (container == null) return;
        container.setBackgroundResource(selected ? R.drawable.pill_tab_selected : R.drawable.pill_tab_unselected);
        int color = selected ? Color.WHITE : Color.parseColor("#AAAAAA");
        if (label != null) label.setTextColor(color);
        // Icon sits beside the label (siblings in a centered row), so it hugs the text
        // instead of being pinned to the pill's left edge.
        if (icon != null) icon.setColorFilter(color);
    }

    /** No-op: the header plan pill was removed. The plan is shown only on the Plan tab card
     *  (plan_current_name + plan_status_chip). Kept as a no-op so callers stay unchanged. */
    private void updatePlanBadge() {
        // intentionally empty
    }

    /** "Verified" pill (mirrors iOS) — shown only when the account's email is verified. */
    private void updateVerifiedPill() {
        if (profileVerifiedPill == null) return;
        boolean verified = userProfile != null && userProfile.isEmailVerified();
        if (verified) {
            Utils.StatusPill.apply(profileVerifiedPill, Utils.StatusPill.Intent.SUCCESS, "Verified");
            profileVerifiedPill.setVisibility(View.VISIBLE);
        } else {
            profileVerifiedPill.setVisibility(View.GONE);
        }
    }

    /** Up to two initials from the user's name for the avatar (mirrors iOS). */
    private String initialsFor(String name) {
        if (name == null || name.trim().isEmpty()) return "?";
        StringBuilder sb = new StringBuilder();
        for (String part : name.trim().split("\\s+")) {
            if (!part.isEmpty()) sb.append(Character.toUpperCase(part.charAt(0)));
            if (sb.length() == 2) break;
        }
        return sb.length() == 0 ? "?" : sb.toString();
    }

    /** Header requests icon: shown with a count badge only when incoming family requests exist. */
    /** Options-button badge: shows the pending incoming family-request count (dot when > 0). */
    private void refreshFamilyRequestsBadge() {
        if (getActivity() == null || profileOptionsBadge == null) return;
        Utils.FamilyRequestsSheet.fetchPendingCount(getActivity(), count -> {
            if (!isAdded() || profileOptionsBadge == null) return;
            lastPendingRequestCount = count;
            if (count > 0) {
                profileOptionsBadge.setVisibility(View.VISIBLE);
                profileOptionsBadge.setText(count > 9 ? "9+" : String.valueOf(count));
            } else {
                profileOptionsBadge.setVisibility(View.GONE);
            }
        });
    }

    /** Single header dropdown (native PopupMenu): Edit profile · Requests · Log out. */
    private void showProfileOptionsMenu(View anchor) {
        androidx.appcompat.widget.PopupMenu popup =
                new androidx.appcompat.widget.PopupMenu(requireContext(), anchor);
        popup.getMenuInflater().inflate(R.menu.profile_add_menu, popup.getMenu());
        // Show item icons where supported (appcompat 1.4+). Reflection avoids a hard
        // compile dependency on the method being present in this module's appcompat.
        try {
            java.lang.reflect.Method m = popup.getClass().getMethod("setForceShowIcon", boolean.class);
            m.invoke(popup, true);
        } catch (Throwable ignored) {}
        // Reflect the pending count in the Requests item title.
        android.view.MenuItem reqItem = popup.getMenu().findItem(R.id.menu_requests);
        if (reqItem != null && lastPendingRequestCount > 0) {
            String cnt = lastPendingRequestCount > 9 ? "9+" : String.valueOf(lastPendingRequestCount);
            reqItem.setTitle("Requests (" + cnt + ")");
        }
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.menu_edit_profile) {
                if (userProfile != null) {
                    showEditProfileDialog();
                } else {
                    Utilities.toast(requireContext(), "Please wait while loading profile data");
                    loadAndDisplayProfile();
                }
                return true;
            } else if (id == R.id.menu_requests) {
                Utils.FamilyRequestsSheet.show(requireActivity(), () -> {
                    refreshFamilyRequestsBadge();
                    if (proStatusManager != null && proStatusManager.isFamilyPlanOwner()) {
                        loadFamilyMembers();
                    }
                });
                return true;
            } else if (id == R.id.menu_logout) {
                confirmLogout();
                return true;
            }
            return false;
        });
        popup.show();
    }

    /**
     * Profile completeness — a simple local ratio over the fields that matter
     * for good AI output. Surfaced in the header ring (a real gap: the value
     * existed on the backend but was shown nowhere).
     */
    private void updateCompleteness() {
        if (userProfile == null || completenessRing == null) return;
        int total = 0, filled = 0;
        // Each check is one "slot" toward completeness.
        total++; if (notBlank(userProfile.getName())) filled++;
        total++; if (userProfile.getDateOfBirth() != null) filled++;
        total++; if (notBlank(userProfile.getGender())) filled++;
        total++; if (userProfile.getHeight() > 0) filled++;
        total++; if (userProfile.getWeight() > 0) filled++;
        total++; if (notBlank(userProfile.getPhoneNumber())) filled++;
        total++; if (notBlank(userProfile.getLocation())) filled++;
        total++; if (notBlank(userProfile.getPrimaryGoal())) filled++;
        total++; if (userProfile.getActivityLevel() >= 1) filled++;
        total++; if (userProfile.getSleepHours() > 0) filled++;
        total++; if (notBlank(userProfile.getDietType())) filled++;
        total++; if (notBlank(userProfile.getBloodType())) filled++;
        total++; if (userProfile.getWaistCircumference() > 0) filled++;
        total++; if (notBlank(userProfile.getEthnicity())) filled++;
        total++; if (notBlank(userProfile.getOccupationType())) filled++;
        total++; if (userProfile.getMedicalConditions() != null && !userProfile.getMedicalConditions().isEmpty()) filled++;

        int percent = total == 0 ? 0 : Math.round(100f * filled / total);
        completenessRing.setPercent(percent);
        if (completenessPercent != null) completenessPercent.setText(percent + "% complete");
        // Hide the "Add missing info" CTA (and its divider) once the profile is complete.
        int ctaVis = percent >= 100 ? View.GONE : View.VISIBLE;
        if (completenessCta != null) completenessCta.setVisibility(ctaVis);
        if (completenessDivider != null) completenessDivider.setVisibility(ctaVis);
    }

    private boolean notBlank(String s) { return s != null && !s.trim().isEmpty(); }

    // ── At a Glance: live Air Quality (from the user's location AQI data) ──
    private void fetchAndShowAqi() {
        if (aqiValue == null) return;
        Context ctx = getContext();
        if (ctx == null) return;
        if (aqiApiService == null) aqiApiService = new Api.AQIAPIService(ctx);
        // Read a wider window (7 days) so the most recent stored reading still shows even if
        // none was recorded today. If nothing is stored yet, populate it from the device
        // location (the fetch+store used to live only in HomeFragment, which is why AQI was
        // empty in Profile when Home hadn't run).
        aqiApiService.getUserAQIHistory(7, new Api.AQIAPIService.OnAQIHistoryListener() {
            @Override public void onSuccess(java.util.List<Models.AQIData> history) {
                if (!isAdded() || aqiValue == null) return;
                Models.AQIData latest = null;
                if (history != null) {
                    // history is newest-first (backend sorts by timestamp desc)
                    for (int i = 0; i < history.size(); i++) {
                        Models.AQIData d = history.get(i);
                        if (d != null && d.getAqiValue() > 0) { latest = d; break; }
                    }
                }
                if (latest != null) {
                    aqiValue.setText(String.valueOf(latest.getAqiValue()));
                    aqiValue.setTextColor(aqiColor(latest.getAqiValue()));
                } else {
                    refreshAqiFromLocation();
                }
            }
            @Override public void onError(String errorMessage) { refreshAqiFromLocation(); }
        });
    }

    /**
     * Fetches live AQI from the device's last known location via IQAir and stores it to the
     * backend, then re-reads so the "At a Glance" value populates. Uses location only if the
     * permission is already granted (does not prompt from Profile); otherwise leaves the
     * placeholder — Home still populates AQI when it runs.
     */
    private void refreshAqiFromLocation() {
        Context ctx = getContext();
        if (ctx == null || aqiValue == null) return;
        if (aqiRefreshAttempted) return; // only fetch+store once per session — avoids any re-read loop
        aqiRefreshAttempted = true;

        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return; // no permission — leave placeholder, Home will populate when visited
        }

        android.location.Location loc = null;
        try {
            android.location.LocationManager lm =
                    (android.location.LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
            if (lm != null) {
                loc = lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER);
                if (loc == null) {
                    loc = lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER);
                }
            }
        } catch (SecurityException e) {
            return;
        }
        if (loc == null) return; // no fix available right now

        String url = "https://api.airvisual.com/v2/nearest_city?lat=" + loc.getLatitude()
                + "&lon=" + loc.getLongitude() + "&key=49b9397d-7ef6-479f-8426-d65b32cc3e7f";

        StringRequest request = new StringRequest(Request.Method.GET, url,
                response -> {
                    ApiConfig.logRestCall(url, true, "IQAir data fetched (profile)");
                    try {
                        JSONObject data = new JSONObject(response).getJSONObject("data");
                        storeAqiToBackend(data);
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing IQAir response (profile)", e);
                    }
                },
                error -> ApiConfig.logRestCall(url, false, error.toString()));
        request.setRetryPolicy(new DefaultRetryPolicy(10000, 1, 1.0f));
        Volley.newRequestQueue(ctx).add(request);
    }

    /** Stores an IQAir "data" object to the backend, then re-reads to display the fresh value. */
    private void storeAqiToBackend(JSONObject data) {
        Context ctx = getContext();
        if (ctx == null) return;
        try {
            JSONObject location = data.getJSONObject("location");
            JSONArray coordinates = location.getJSONArray("coordinates");
            JSONObject current = data.getJSONObject("current");
            JSONObject pollution = current.getJSONObject("pollution");
            JSONObject weather = current.getJSONObject("weather");

            JSONObject body = new JSONObject();
            body.put("city", data.getString("city"));
            body.put("state", data.optString("state", ""));
            body.put("country", data.getString("country"));
            body.put("longitude", coordinates.getDouble(0));
            body.put("latitude", coordinates.getDouble(1));
            body.put("aqius", pollution.getInt("aqius"));
            body.put("aqicn", pollution.optInt("aqicn", 0));
            body.put("mainus", pollution.optString("mainus", ""));
            body.put("maincn", pollution.optString("maincn", ""));
            body.put("temperature", weather.optDouble("tp", 0));
            body.put("humidity", weather.optInt("hu", 0));
            body.put("pressure", weather.optInt("pr", 0));

            String url = ApiConfig.BASE_URL + "/api/aqi/store";
            TokenManager tokenManager = TokenManager.getInstance(ctx);
            final byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);

            StringRequest request = new StringRequest(Request.Method.POST, url,
                    response -> {
                        ApiConfig.logRestCall(url, true, "AQI stored (profile)");
                        if (isAdded()) fetchAndShowAqi(); // re-read now that data exists
                    },
                    error -> ApiConfig.logRestCall(url, false, error.toString())) {
                @Override public byte[] getBody() { return payload; }
                @Override public String getBodyContentType() { return "application/json; charset=utf-8"; }
                @Override public Map<String, String> getHeaders() {
                    Map<String, String> headers = new HashMap<>();
                    headers.put("Authorization", "Bearer " + tokenManager.getToken());
                    return headers;
                }
            };
            Volley.newRequestQueue(ctx).add(request);
        } catch (JSONException e) {
            Log.e(TAG, "Error building AQI store body (profile)", e);
        }
    }

    private int aqiColor(int aqi) {
        Context ctx = getContext();
        if (aqi <= 50)  return ctx != null ? ContextCompat.getColor(ctx, R.color.rh_success) : Color.WHITE;
        if (aqi <= 100) return Color.parseColor("#E0B000");
        if (aqi <= 150) return Color.parseColor("#E67E22");
        return ctx != null ? ContextCompat.getColor(ctx, R.color.rh_danger) : Color.parseColor("#FF5252");
    }

    // ══════════════════════ AI & CHAT PREFERENCES ══════════════════════

    // Display label ↔ backend value maps for the AI reply preferences.
    private static final java.util.LinkedHashMap<String, String> AI_TONE_OPTIONS = new java.util.LinkedHashMap<String, String>() {{
        put("Balanced", "balanced");
        put("Warm & encouraging", "warm");
        put("Direct & clinical", "direct");
    }};
    private static final java.util.LinkedHashMap<String, String> AI_LENGTH_OPTIONS = new java.util.LinkedHashMap<String, String>() {{
        put("Concise", "concise");
        put("Balanced", "balanced");
        put("Detailed", "detailed");
    }};

    private static String aiLabelForValue(java.util.LinkedHashMap<String, String> map, String value) {
        if (value != null) {
            for (Map.Entry<String, String> e : map.entrySet()) {
                if (e.getValue().equalsIgnoreCase(value)) return e.getKey();
            }
        }
        // Fall back to the first entry (the default)
        return map.keySet().iterator().next();
    }

    // Segment value order must match the layout left→right.
    private void displayAiPreferences() {
        if (userProfile == null) return;

        if (aiToneValue != null) aiToneValue.setText(capitalizeWord(orDefault(userProfile.getAiTone(), "balanced")));
        if (aiLengthValue != null) aiLengthValue.setText(capitalizeWord(orDefault(userProfile.getAiReplyLength(), "balanced")));

        if (aiCustomValue != null) {
            String custom = userProfile.getAiCustomInstructions();
            if (custom != null && !custom.trim().isEmpty()) {
                aiCustomValue.setText(custom.trim());
                aiCustomValue.setTextColor(ContextCompat.getColor(requireContext(), R.color.rh_text_secondary));
            } else {
                aiCustomValue.setText("Anything Richie should always keep in mind");
                aiCustomValue.setTextColor(ContextCompat.getColor(requireContext(), R.color.rh_text_tertiary));
            }
        }

        // Switches — set state without firing listeners
        if (aiSaveMemoriesSwitch != null) {
            aiSaveMemoriesSwitch.setOnCheckedChangeListener(null);
            aiSaveMemoriesSwitch.setChecked(userProfile.isAiSaveMemories());
            aiSaveMemoriesSwitch.setOnCheckedChangeListener((b, checked) -> {
                userProfile.setAiSaveMemories(checked);
                persistAiPreferences();
            });
        }
        if (aiImproveModelSwitch != null) {
            aiImproveModelSwitch.setOnCheckedChangeListener(null);
            aiImproveModelSwitch.setChecked(userProfile.isAiImproveModel());
            aiImproveModelSwitch.setOnCheckedChangeListener((b, checked) -> {
                userProfile.setAiImproveModel(checked);
                persistAiPreferences();
            });
        }
        if (aiAutofillCardsSwitch != null) {
            aiAutofillCardsSwitch.setOnCheckedChangeListener(null);
            aiAutofillCardsSwitch.setChecked(userProfile.isAiAutofillCards());
            aiAutofillCardsSwitch.setOnCheckedChangeListener((b, checked) -> {
                userProfile.setAiAutofillCards(checked);
                persistAiPreferences();
            });
        }
        if (aiShowThinkingSwitch != null) {
            aiShowThinkingSwitch.setOnCheckedChangeListener(null);
            aiShowThinkingSwitch.setChecked(userProfile.isAiShowThinking());
            aiShowThinkingSwitch.setOnCheckedChangeListener((b, checked) -> {
                userProfile.setAiShowThinking(checked);
                persistAiPreferences();
            });
        }
    }

    private void setupAiPreferenceListeners() {
        if (aiToneItem != null) aiToneItem.setOnClickListener(v -> showToneChooser());
        if (aiLengthItem != null) aiLengthItem.setOnClickListener(v -> showLengthChooser());
        if (aiCustomItem != null) {
            aiCustomItem.setOnClickListener(v -> showCustomInstructionsDialog());
        }
        if (aiMemoryItem != null) {
            aiMemoryItem.setOnClickListener(v -> showAiMemoryDialog());
        }
    }

    private interface AiChoiceCallback { void onChosen(String value); }

    // Custom dark picker matching the app's dialog aesthetic (Dialog + R.style.DialogTheme,
    // teal accent, rounded card) — not a stock AlertDialog radio list.
    private void showAiChoiceDialog(String title, java.util.LinkedHashMap<String, String> options,
                                    String currentValue, AiChoiceCallback callback) {
        Context ctx = getContext();
        if (ctx == null) return;
        final float d = getResources().getDisplayMetrics().density;

        final Dialog dialog = new Dialog(ctx, R.style.DialogTheme);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding((int) (22 * d), (int) (20 * d), (int) (22 * d), (int) (14 * d));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setCornerRadius(18 * d);
        bg.setColor(Color.parseColor("#141C1C"));
        bg.setStroke((int) d, Color.parseColor("#243A38"));
        root.setBackground(bg);

        TextView titleView = new TextView(ctx);
        titleView.setText(title);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 17f);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tlp.bottomMargin = (int) (12 * d);
        root.addView(titleView, tlp);

        final String[] labels = options.keySet().toArray(new String[0]);
        final String[] values = options.values().toArray(new String[0]);
        for (int i = 0; i < labels.length; i++) {
            final String value = values[i];
            boolean selected = value.equalsIgnoreCase(currentValue);

            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding((int) (6 * d), (int) (13 * d), (int) (6 * d), (int) (13 * d));
            row.setClickable(true);
            row.setFocusable(true);
            android.util.TypedValue tv = new android.util.TypedValue();
            ctx.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
            row.setBackgroundResource(tv.resourceId);

            TextView lbl = new TextView(ctx);
            lbl.setText(labels[i]);
            lbl.setTextColor(selected ? Color.parseColor("#37C9A6") : Color.parseColor("#E4EEEE"));
            lbl.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15f);
            row.addView(lbl, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

            if (selected) {
                android.widget.ImageView chk = new android.widget.ImageView(ctx);
                chk.setImageResource(R.drawable.ic_check);
                chk.setColorFilter(Color.parseColor("#37C9A6"));
                row.addView(chk, new LinearLayout.LayoutParams((int) (18 * d), (int) (18 * d)));
            }

            row.setOnClickListener(v -> {
                callback.onChosen(value);
                dialog.dismiss();
            });
            root.addView(row, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }

        dialog.setContentView(root);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
            lp.copyFrom(dialog.getWindow().getAttributes());
            lp.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.86);
            dialog.getWindow().setAttributes(lp);
        }
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
    }

    /** Row-based choosers for Tone / Reply Length — consistent with the other
     *  settings rows (icon + label + value + chevron), replacing the tab-like pills. */
    private void showToneChooser() {
        if (userProfile == null) return;
        java.util.LinkedHashMap<String, String> opts = new java.util.LinkedHashMap<>();
        opts.put("Balanced", "balanced");
        opts.put("Warm", "warm");
        opts.put("Direct", "direct");
        showAiChoiceDialog("Response Tone", opts, userProfile.getAiTone(), value -> {
            userProfile.setAiTone(value);
            displayAiPreferences();
            persistAiPreferences();
        });
    }

    private void showLengthChooser() {
        if (userProfile == null) return;
        java.util.LinkedHashMap<String, String> opts = new java.util.LinkedHashMap<>();
        opts.put("Concise", "concise");
        opts.put("Balanced", "balanced");
        opts.put("Detailed", "detailed");
        showAiChoiceDialog("Reply Length", opts, userProfile.getAiReplyLength(), value -> {
            userProfile.setAiReplyLength(value);
            displayAiPreferences();
            persistAiPreferences();
        });
    }

    private String capitalizeWord(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private String orDefault(String s, String d) { return (s == null || s.isEmpty()) ? d : s; }

    private void showCustomInstructionsDialog() {
        if (userProfile == null) return;
        DialogUtils.DialogField[] fields = new DialogUtils.DialogField[] {
                new DialogUtils.DialogField("customInstructions",
                        "e.g. Explain simply, avoid jargon, remind me about my knee",
                        android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE,
                        userProfile.getAiCustomInstructions() != null ? userProfile.getAiCustomInstructions() : "")
        };
        DialogUtils.showEditDialog(
                requireContext(),
                "Custom Instructions",
                fields,
                values -> {
                    String instr = values.get("customInstructions");
                    if (instr != null && instr.length() > 500) instr = instr.substring(0, 500);
                    userProfile.setAiCustomInstructions(instr);
                    displayAiPreferences();
                    persistAiPreferences();
                },
                null,
                null
        );
    }

    /**
     * Lightweight, silent PUT of just the aiPreferences object. Reuses the same
     * /api/user/profile endpoint (its whitelist accepts partial updates) so no
     * new backend surface is needed.
     */
    private void persistAiPreferences() {
        Context context = getContext();
        if (context == null || userProfile == null) return;

        // Persist locally too so the value survives until the next server refresh.
        try { dbHelper.updateUserProfile(userProfile); } catch (Exception ignored) {}

        TokenManager tokenManager = TokenManager.getInstance(context);
        String token = tokenManager.getToken();
        if (token == null) return;

        JSONObject body = new JSONObject();
        try {
            JSONObject aiPrefs = new JSONObject();
            aiPrefs.put("tone", userProfile.getAiTone());
            aiPrefs.put("replyLength", userProfile.getAiReplyLength());
            aiPrefs.put("customInstructions", userProfile.getAiCustomInstructions() != null ? userProfile.getAiCustomInstructions() : "");
            aiPrefs.put("saveMemories", userProfile.isAiSaveMemories());
            aiPrefs.put("improveModel", userProfile.isAiImproveModel());
            aiPrefs.put("autofillCards", userProfile.isAiAutofillCards());
            aiPrefs.put("showThinking", userProfile.isAiShowThinking());
            body.put("aiPreferences", aiPrefs);
        } catch (JSONException e) {
            Log.e(TAG, "Failed to build aiPreferences body", e);
            return;
        }

        // Blocking progress so the user sees that a real backend call is in flight while
        // their AI setting is being saved (uses the app-wide SimpleProgress logo overlay).
        SimpleProgress progress = SimpleProgress.show(requireActivity(), "Tuning Richie to your preferences…");

        String url = ApiConfig.BASE_URL + "/api/user/profile";
        StringRequest request = new StringRequest(Request.Method.PUT, url,
                response -> {
                    progress.hide();
                    Log.d(TAG, "AI preferences saved");
                },
                error -> {
                    progress.hide();
                    Log.e(TAG, "Failed to save AI preferences", error);
                    Utilities.toast(getContext(), "Couldn't update your AI settings. Please try again.");
                }
        ) {
            @Override
            public byte[] getBody() { return body.toString().getBytes(StandardCharsets.UTF_8); }
            @Override
            public String getBodyContentType() { return "application/json; charset=utf-8"; }
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> h = new HashMap<>();
                h.put("Authorization", "Bearer " + token);
                return h;
            }
        };
        Volley.newRequestQueue(context).add(request);
    }

    // ── AI Memory manager dialog ──
    private void showAiMemoryDialog() {
        Context context = getContext();
        if (context == null) return;

        Dialog dialog = new Dialog(context, R.style.DialogTheme);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_ai_memory);
        if (dialog.getWindow() != null) {
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
            lp.copyFrom(dialog.getWindow().getAttributes());
            lp.width = WindowManager.LayoutParams.MATCH_PARENT;
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
            dialog.getWindow().setAttributes(lp);
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        RecyclerView recycler = dialog.findViewById(R.id.memory_recycler);
        TextView emptyView = dialog.findViewById(R.id.memory_empty);
        ImageView closeBtn = dialog.findViewById(R.id.memory_close);

        MemoryAdapter adapter = new MemoryAdapter(context);
        recycler.setLayoutManager(new LinearLayoutManager(context));
        recycler.setAdapter(adapter);

        adapter.setDeleteListener((memory, position) -> confirmDeleteMemory(memory, position, adapter, emptyView));

        if (closeBtn != null) closeBtn.setOnClickListener(v -> dialog.dismiss());

        fetchMemories(adapter, emptyView);
        dialog.show();
    }

    private void fetchMemories(MemoryAdapter adapter, TextView emptyView) {
        Context context = getContext();
        if (context == null) return;
        TokenManager tokenManager = TokenManager.getInstance(context);
        String token = tokenManager.getToken();
        if (token == null) return;

        String url = ApiConfig.BASE_URL + "/api/user/memories";
        StringRequest request = new StringRequest(Request.Method.GET, url,
                response -> {
                    if (!isAdded()) return;
                    try {
                        JSONObject body = new JSONObject(response);
                        JSONArray arr = body.optJSONArray("memories");
                        List<AiMemory> memories = new ArrayList<>();
                        if (arr != null) {
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject m = arr.optJSONObject(i);
                                if (m == null) continue;
                                String id = m.optString("_id", m.optString("id", ""));
                                String fact = m.optString("fact", "");
                                String category = m.optString("category", "");
                                if (!fact.isEmpty()) memories.add(new AiMemory(id, fact, category));
                            }
                        }
                        adapter.setData(memories);
                        updateMemoryEmptyState(adapter, emptyView);
                        updateMemorySubtitle(memories.size());
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to parse memories", e);
                    }
                },
                error -> Log.e(TAG, "Failed to fetch memories", error)
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> h = new HashMap<>();
                h.put("Authorization", "Bearer " + token);
                return h;
            }
        };
        Volley.newRequestQueue(context).add(request);
    }

    private void confirmDeleteMemory(AiMemory memory, int position, MemoryAdapter adapter, TextView emptyView) {
        DialogUtils.showConfirmDialog(requireContext(),
                "Forget this?",
                memory.getFact(),
                "Forget", "Cancel", true,
                () -> deleteMemory(memory, position, adapter, emptyView));
    }

    private void deleteMemory(AiMemory memory, int position, MemoryAdapter adapter, TextView emptyView) {
        Context context = getContext();
        if (context == null || memory.getId() == null || memory.getId().isEmpty()) return;
        TokenManager tokenManager = TokenManager.getInstance(context);
        String token = tokenManager.getToken();
        if (token == null) return;

        String url = ApiConfig.BASE_URL + "/api/user/memories/" + memory.getId();
        StringRequest request = new StringRequest(Request.Method.DELETE, url,
                response -> {
                    if (!isAdded()) return;
                    adapter.removeItem(position);
                    updateMemoryEmptyState(adapter, emptyView);
                    updateMemorySubtitle(adapter.getItemCountSafe());
                    Utilities.toast(requireContext(), "Removed from memory");
                },
                error -> {
                    if (isAdded()) Utilities.toast(requireContext(), "Could not remove memory");
                }
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> h = new HashMap<>();
                h.put("Authorization", "Bearer " + token);
                return h;
            }
        };
        Volley.newRequestQueue(context).add(request);
    }

    private void updateMemoryEmptyState(MemoryAdapter adapter, TextView emptyView) {
        if (emptyView == null) return;
        emptyView.setVisibility(adapter.getItemCountSafe() == 0 ? View.VISIBLE : View.GONE);
    }

    private void updateMemorySubtitle(int count) {
        if (aiMemorySubtitle == null) return;
        if (count <= 0) {
            aiMemorySubtitle.setText("What Richie remembers about you");
        } else {
            aiMemorySubtitle.setText(count + (count == 1 ? " thing remembered" : " things remembered"));
        }
    }

    /**
     * Handles user logout when the logout button is clicked
     */
    private void setupLogoutButton() {
        // Logout now lives in the header options dropdown (see showProfileOptionsMenu()).
    }

    @Override
    public void onResume() {
        super.onResume();
        loadAndDisplayProfile();
        // Refresh from server to pick up edits made elsewhere (web / other devices) and
        // self-heal devices whose local cache was written with a partial login payload.
        refreshProfileFromServer();
        updateProUI();
    }

}


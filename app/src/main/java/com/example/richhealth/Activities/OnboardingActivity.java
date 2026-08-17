package com.example.richhealth.Activities;
import Utils.Utilities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentTransaction;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.example.richhealth.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import Database.DatabaseHelper;
import Models.OnboardingData;
import Models.SelectableOption;
import Models.StepConfig;
import Models.UserProfile;
import Utils.ApiConfig;
import Utils.SimpleProgress;

public class OnboardingActivity extends AppCompatActivity implements CardStepHost {

    private static final String TAG = "OnboardingActivity";
    private static final int ACCOUNT_FRAGMENT_INDEX = 0;
    private static final int MENSTRUAL_FRAGMENT_INDEX = 2;
    // Conditional follow-up steps (see rebuildActiveSteps / isStepActive).
    private static final int SMOKING_DETAIL_FRAGMENT_INDEX = 13;
    private static final int ALCOHOL_DETAIL_FRAGMENT_INDEX = 14;
    private static final int CONDITIONS_DETAIL_FRAGMENT_INDEX = 19;
    // Steps after which the active-step list must be recomputed (an answer here
    // decides whether a following conditional step appears).
    private static final int HABITS_FRAGMENT_INDEX = 12;
    private static final int CONDITIONS_FRAGMENT_INDEX = 18;

    private final List<BaseOnboardingFragment> allFragments = new ArrayList<>();
    private final List<Integer> activeSteps = new ArrayList<>();
    private int currentStep = 0;

    private LinearProgressIndicator progressBar;
    private TextView tvStepLabel;
    private ImageButton btnBack;
    private MaterialButton btnContinue;
    private View loadingOverlay;

    private final OnboardingData onboardingData = new OnboardingData();

    // Email verification (post-signup) state. The account already exists once we
    // reach here, so this must survive the user leaving the app for their inbox
    // and any activity recreation — the code box is re-shown, and Back can't
    // fall back into the onboarding steps.
    private boolean awaitingOtpVerification = false;
    private String otpEmail = null;
    private String otpName = null;
    private android.app.Dialog otpDialog = null;

    /** Keyed by the step index used in CardStepFragment.newInstance(). */
    private final Map<Integer, StepConfig> cardStepConfigs = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        if (getSupportActionBar() != null) getSupportActionBar().hide();

        progressBar    = findViewById(R.id.progress_bar);
        tvStepLabel    = findViewById(R.id.tv_step_label);
        btnBack        = findViewById(R.id.btn_back);
        btnContinue    = findViewById(R.id.btn_continue);
        loadingOverlay = findViewById(R.id.loading_overlay);

        initCardStepConfigs();

        // All fragments — index 2 (menstrual) is conditional on gender.
        // One-question-per-step where it improves focus; related questions
        // stay bundled (Habits, Medical, Personal DOB+gender+location).
        allFragments.add(new OnboardingAccountFragment());          // 0  – account + phone
        allFragments.add(new OnboardingPersonalFragment());         // 1  – DOB + gender + location
        allFragments.add(CardStepFragment.newInstance(2));          // 2  – menstrual health (conditional)
        allFragments.add(new OnboardingBodyFragment());             // 3  – height & weight (sliders)
        allFragments.add(CardStepFragment.newInstance(3));          // 4  – health goal
        allFragments.add(CardStepFragment.newInstance(4));          // 5  – activity level
        allFragments.add(CardStepFragment.newInstance(5));          // 6  – occupation
        allFragments.add(CardStepFragment.newInstance(6));          // 7  – diet type
        allFragments.add(CardStepFragment.newInstance(7));          // 8  – meals + water
        allFragments.add(CardStepFragment.newInstance(8));          // 9  – sleep
        allFragments.add(CardStepFragment.newInstance(9));          // 10 – stress
        allFragments.add(CardStepFragment.newInstance(10));         // 11 – screen time
        allFragments.add(CardStepFragment.newInstance(11));         // 12 – smoking + alcohol + caffeine
        allFragments.add(CardStepFragment.newInstance(16));         // 13 – smoking detail (conditional)
        allFragments.add(CardStepFragment.newInstance(17));         // 14 – alcohol detail (conditional)
        allFragments.add(CardStepFragment.newInstance(12));         // 15 – family history
        allFragments.add(CardStepFragment.newInstance(13));         // 16 – allergies
        allFragments.add(CardStepFragment.newInstance(14));         // 17 – sun exposure
        allFragments.add(CardStepFragment.newInstance(15));         // 18 – blood type + conditions
        allFragments.add(CardStepFragment.newInstance(18));         // 19 – conditions detail (conditional)
        allFragments.add(CardStepFragment.newInstance(19));         // 20 – ancestry

        rebuildActiveSteps();

        btnBack.setOnClickListener(v -> handleBack());
        btnContinue.setOnClickListener(v -> handleContinue());

        showStep(0, true);

        // If the activity was recreated while awaiting email verification (the
        // account already exists), restore that state and re-show the code box.
        if (savedInstanceState != null
                && savedInstanceState.getBoolean("awaitingOtpVerification", false)) {
            awaitingOtpVerification = true;
            otpEmail = savedInstanceState.getString("otpEmail");
            otpName = savedInstanceState.getString("otpName");
            if (otpEmail != null) {
                btnContinue.post(() -> {
                    if (awaitingOtpVerification) showOtpDialog(otpEmail, otpName);
                });
            }
        }
    }

    // ── Data-driven card configs ──────────────────────────────────────────────

    /**
     * All card-selection steps defined as pure data.
     * To add a question: add a SectionConfig here. Zero new Java files needed.
     */
    private void initCardStepConfigs() {

        // ── Step 2: Menstrual Health (conditional — Female/Other only) ────────
        final String MENSTRUAL_NONE = "__menstrual_none__";
        List<SelectableOption> menstrualSymptomOptions = Arrays.asList(
                new SelectableOption("Cramps",             R.drawable.ic_signup_cramps,      "Cramps"),
                new SelectableOption("Bloating",           R.drawable.ic_signup_bloating,    "Bloating"),
                new SelectableOption("Mood Changes",       R.drawable.ic_signup_mood_swings, "Mood Changes"),
                new SelectableOption("Headaches",          R.drawable.ic_signup_headache,    "Headaches"),
                new SelectableOption("Fatigue",            R.drawable.ic_signup_low_energy,  "Fatigue"),
                new SelectableOption("Breast Tenderness",  R.drawable.ic_signup_heart,       "Breast Tenderness"),
                new SelectableOption("Acne",               R.drawable.ic_signup_skin,        "Acne"),
                new SelectableOption("Heavy Flow",         R.drawable.ic_signup_heavy_flow,  "Heavy Flow"),
                new SelectableOption("None",               R.drawable.ic_signup_none,        MENSTRUAL_NONE, true)
        );

        cardStepConfigs.put(2, new StepConfig(
                R.drawable.ic_signup_menstrual_hero,
                "Menstrual Health",
                "This helps us personalise nutrition, symptom tracking, and health insights around your cycle",
                Arrays.asList(
                        new StepConfig.SectionConfig(
                                "What's your cycle like?",
                                "Hormones swing differently across regular, irregular, and menopausal cycles — this changes your nutrition, energy, and sleep advice.",
                                Arrays.asList(
                                        new SelectableOption("Regular",          R.drawable.ic_signup_cycle_regular,    "regular"),
                                        new SelectableOption("Irregular",        R.drawable.ic_signup_cycle_irregular,  "irregular"),
                                        new SelectableOption("Perimenopause",    R.drawable.ic_signup_perimenopause,    "perimenopause"),
                                        new SelectableOption("Menopause",        R.drawable.ic_signup_menopause,        "menopause"),
                                        new SelectableOption("Prefer Not to Say",R.drawable.ic_signup_block,            "prefer_not_to_say")
                                ),
                                false, true, 2,
                                (data, value) -> data.menstrualStatus = (String) value
                        ),
                        new StepConfig.SectionConfig(
                                "Any common symptoms?",
                                "Tracking these helps us spot PMS, endometriosis, or hormonal imbalance patterns early.",
                                menstrualSymptomOptions,
                                true, false, 2,
                                (data, value) -> {
                                    @SuppressWarnings("unchecked")
                                    List<Object> selected = (List<Object>) value;
                                    List<String> symptoms = new ArrayList<>();
                                    for (Object o : selected) {
                                        String s = (String) o;
                                        if (!s.equals(MENSTRUAL_NONE)) symptoms.add(s);
                                    }
                                    data.menstrualSymptoms = symptoms;
                                },
                                menstrualSymptomOptions.size() - 1
                        ),
                        new StepConfig.SectionConfig(
                                "Pregnancy status",
                                "Safety-critical — changes our supplement, food, and medication recommendations.",
                                Arrays.asList(
                                        new SelectableOption("Not Pregnant",       R.drawable.ic_signup_none,      "not_pregnant"),
                                        new SelectableOption("Pregnant",           R.drawable.ic_signup_pregnant,  "pregnant"),
                                        new SelectableOption("Postpartum",         R.drawable.ic_signup_postpartum,"postpartum"),
                                        new SelectableOption("Trying to Conceive", R.drawable.ic_signup_seedling,  "trying_to_conceive")
                                ),
                                false, false, 2,
                                (data, value) -> data.pregnancyStatus = (String) value
                        ),
                        new StepConfig.SectionConfig(
                                "Average cycle length?",
                                "Cycle length helps us time nutrition and symptom predictions.",
                                Arrays.asList(
                                        new SelectableOption("21–25 days", R.drawable.ic_signup_cycle_regular,   23),
                                        new SelectableOption("26–30 days", R.drawable.ic_signup_cycle_regular,   28),
                                        new SelectableOption("31–35 days", R.drawable.ic_signup_cycle_irregular, 33),
                                        new SelectableOption("Irregular",  R.drawable.ic_signup_cycle_irregular, 0)
                                ),
                                false, false, 2,
                                (data, value) -> data.averageCycleLength = (Integer) value
                        ),
                        new StepConfig.SectionConfig(
                                "Typical period length?",
                                "How many days your period usually lasts.",
                                Arrays.asList(
                                        new SelectableOption("1–3 days", R.drawable.ic_signup_none,           2),
                                        new SelectableOption("4–5 days", R.drawable.ic_signup_menstrual_hero, 5),
                                        new SelectableOption("6–7 days", R.drawable.ic_signup_heavy_flow,     6),
                                        new SelectableOption("8+ days",  R.drawable.ic_signup_heavy_flow,     8)
                                ),
                                false, false, 2,
                                (data, value) -> data.averagePeriodLength = (Integer) value
                        ),
                        new StepConfig.SectionConfig(
                                "Contraception method?",
                                "Some methods affect your cycle, mood, and nutrition needs.",
                                Arrays.asList(
                                        new SelectableOption("None",             R.drawable.ic_signup_none,       "none"),
                                        new SelectableOption("Pill",             R.drawable.ic_signup_healthcare, "pill"),
                                        new SelectableOption("IUD",              R.drawable.ic_signup_healthcare, "iud"),
                                        new SelectableOption("Condom",           R.drawable.ic_signup_block,      "condom"),
                                        new SelectableOption("Implant/Injection",R.drawable.ic_signup_healthcare, "implant"),
                                        SelectableOption.other("Other — you tell us", R.drawable.ic_edit, true)
                                ),
                                false, false, 2,
                                (data, value) -> data.contraceptionMethod = (String) value
                        )
                )
        ));

        // ── Step 3: Health Goal (+ Other) ─────────────────────────────────────
        cardStepConfigs.put(3, new StepConfig(
                R.drawable.ic_onboarding_goal,
                "Your Health Goal",
                "Pick the one you want to focus on most",
                Arrays.asList(
                        new StepConfig.SectionConfig(
                                null,
                                Arrays.asList(
                                        new SelectableOption("Lose Weight",      R.drawable.ic_signup_weight_loss,   "Weight Loss"),
                                        new SelectableOption("Build Muscle",     R.drawable.ic_signup_muscle,        "Muscle Gain"),
                                        new SelectableOption("Stay Fit",         R.drawable.ic_signup_walk,          "Improve Fitness"),
                                        new SelectableOption("Manage Condition", R.drawable.ic_signup_heart,         "Manage a Health Condition"),
                                        new SelectableOption("Boost Energy",     R.drawable.ic_signup_energy,        "Boost Energy"),
                                        new SelectableOption("Sleep Better",     R.drawable.ic_signup_sleep_goal,    "Improve Sleep"),
                                        new SelectableOption("Eat Healthier",    R.drawable.ic_signup_healthy_food,  "Eat Healthier"),
                                        new SelectableOption("Mental Health",    R.drawable.ic_signup_mental_health, "Improve Mental Health"),
                                        SelectableOption.other("Other — you tell us", R.drawable.ic_edit)
                                ),
                                true, true, 2,
                                (data, value) -> {
                                    @SuppressWarnings("unchecked")
                                    List<Object> selected = (List<Object>) value;
                                    List<String> goals = new ArrayList<>();
                                    for (Object o : selected) goals.add((String) o);
                                    data.specificGoals = goals;
                                    data.primaryGoal = goals.isEmpty() ? "" : goals.get(0);
                                }
                        ),
                        new StepConfig.SectionConfig(
                                "Has your weight changed recently?",
                                "Unexplained changes can flag thyroid, metabolic, or blood-sugar issues early.",
                                Arrays.asList(
                                        new SelectableOption("Gained",   R.drawable.ic_signup_fastfood,    "Gained"),
                                        new SelectableOption("Lost",     R.drawable.ic_signup_weight_loss, "Lost"),
                                        new SelectableOption("Stable",   R.drawable.ic_signup_walk,        "Stable"),
                                        new SelectableOption("Not sure", R.drawable.ic_signup_none,        "Not sure")
                                ),
                                false, false, 2,
                                (data, value) -> data.recentWeightChange = (String) value
                        )
                )
        ));

        // ── Step 4: Activity Level (split from occupation) ────────────────────
        cardStepConfigs.put(4, new StepConfig(
                R.drawable.ic_signup_activity_hero,
                "Your Activity Level",
                "This helps us calculate your daily calorie needs",
                Arrays.asList(
                        new StepConfig.SectionConfig(
                                null,
                                Arrays.asList(
                                        new SelectableOption("Mostly Sitting",    R.drawable.ic_signup_sitting,  1),
                                        new SelectableOption("Light Activity",    R.drawable.ic_signup_walk,     2),
                                        new SelectableOption("Moderately Active", R.drawable.ic_signup_bike,     3),
                                        new SelectableOption("Very Active",       R.drawable.ic_signup_muscle,   4),
                                        new SelectableOption("Athlete",           R.drawable.ic_signup_athlete,  5)
                                ),
                                false, true, 2,
                                (data, value) -> data.activityLevel = (Integer) value
                        )
                )
        ));

        // ── Step 5: Occupation (+ Other) ──────────────────────────────────────
        cardStepConfigs.put(5, new StepConfig(
                R.drawable.ic_signup_desk,
                "What do you do?",
                "Your work rhythm shapes your energy, posture, and recovery",
                Arrays.asList(
                        new StepConfig.SectionConfig(
                                null,
                                Arrays.asList(
                                        new SelectableOption("Desk / Office",    R.drawable.ic_signup_desk,            "desk"),
                                        new SelectableOption("Physical Labour",  R.drawable.ic_signup_physical_labor,  "physical"),
                                        new SelectableOption("Healthcare",       R.drawable.ic_signup_healthcare,      "healthcare"),
                                        new SelectableOption("Student",          R.drawable.ic_signup_student,         "student"),
                                        new SelectableOption("Work from Home",   R.drawable.ic_signup_wfh,             "remote"),
                                        new SelectableOption("Retired / Home",   R.drawable.ic_signup_retired,         "retired"),
                                        SelectableOption.other("Other — you tell us", R.drawable.ic_edit, true)
                                ),
                                false, true, 2,
                                (data, value) -> data.occupationType = (String) value
                        )
                )
        ));

        // ── Step 6: Diet Type (+ Other) ───────────────────────────────────────
        cardStepConfigs.put(6, new StepConfig(
                R.drawable.ic_signup_healthy_food,
                "Your Diet",
                "We'll personalise your nutrition insights around how you eat",
                Arrays.asList(
                        new StepConfig.SectionConfig(
                                null,
                                Arrays.asList(
                                        new SelectableOption("Everything",    R.drawable.ic_signup_fastfood,     "Regular"),
                                        new SelectableOption("Vegetarian",    R.drawable.ic_signup_vegetarian,   "Vegetarian"),
                                        new SelectableOption("Vegan",         R.drawable.ic_signup_seedling,     "Vegan"),
                                        new SelectableOption("Keto",          R.drawable.ic_signup_seedling,     "Keto"),
                                        new SelectableOption("Mediterranean", R.drawable.ic_signup_healthy_food, "Mediterranean"),
                                        new SelectableOption("Gluten-Free",   R.drawable.ic_signup_gluten_free,  "Gluten-Free"),
                                        SelectableOption.other("Other — you tell us", R.drawable.ic_signup_other_diet, true)
                                ),
                                false, true, 2,
                                (data, value) -> data.dietType = (String) value
                        )
                )
        ));

        // ── Step 7: Meals + Water (numeric habits, stay bundled) ──────────────
        cardStepConfigs.put(7, new StepConfig(
                R.drawable.ic_signup_water_glass,
                "How you eat & drink",
                "Two quick ones — both shape your daily energy",
                Arrays.asList(
                        new StepConfig.SectionConfig(
                                "How many meals a day?",
                                "Meal frequency shapes blood sugar stability and energy dips through the day.",
                                Arrays.asList(
                                        new SelectableOption("1–2 meals", R.drawable.ic_signup_meals_1, 2),
                                        new SelectableOption("3 meals",   R.drawable.ic_signup_meals_3, 3),
                                        new SelectableOption("4–5 meals", R.drawable.ic_signup_meals_4, 4),
                                        new SelectableOption("6+ meals",  R.drawable.ic_signup_meals_6, 6)
                                ),
                                false, true, 2,
                                (data, value) -> data.mealsPerDay = (Integer) value
                        ),
                        new StepConfig.SectionConfig(
                                "How much water do you drink?",
                                "Hydration is the most overlooked driver of energy, focus, and skin health.",
                                Arrays.asList(
                                        new SelectableOption("I forget to drink",      R.drawable.ic_signup_low_energy,    2),
                                        new SelectableOption("4–6 glasses",            R.drawable.ic_signup_water_glass,   5),
                                        new SelectableOption("7–9 glasses",            R.drawable.ic_signup_water_bottle,  8),
                                        new SelectableOption("10+ (hydration champ!)", R.drawable.ic_signup_water_drops,   10)
                                ),
                                false, true, 2,
                                (data, value) -> data.waterIntake = (Integer) value
                        )
                )
        ));

        // ── Step 8: Sleep (split) ─────────────────────────────────────────────
        cardStepConfigs.put(8, new StepConfig(
                R.drawable.ic_signup_sleep_goal,
                "How much do you sleep?",
                "Sleep is where your body actually does the healing work",
                Arrays.asList(
                        new StepConfig.SectionConfig(
                                null,
                                Arrays.asList(
                                        new SelectableOption("Under 5 hrs", R.drawable.ic_signup_sleep_under5, 4),
                                        new SelectableOption("5–6 hours",   R.drawable.ic_signup_sleep_5to6,   6),
                                        new SelectableOption("7–8 hours",   R.drawable.ic_signup_sleep_7to8,   8),
                                        new SelectableOption("9+ hours",    R.drawable.ic_signup_sleep_9plus,  9)
                                ),
                                false, true, 2,
                                (data, value) -> data.sleepHours = (Integer) value
                        )
                )
        ));

        // ── Step 9: Stress (split) ────────────────────────────────────────────
        cardStepConfigs.put(9, new StepConfig(
                R.drawable.ic_signup_stress_often,
                "How often do you feel stressed?",
                "No judgement — this helps us spot patterns that matter",
                Arrays.asList(
                        new StepConfig.SectionConfig(
                                null,
                                Arrays.asList(
                                        new SelectableOption("Rarely",        R.drawable.ic_signup_stress_rare,      1),
                                        new SelectableOption("Sometimes",     R.drawable.ic_signup_stress_sometimes, 2),
                                        new SelectableOption("Often",         R.drawable.ic_signup_stress_often,     3),
                                        new SelectableOption("Almost Always", R.drawable.ic_signup_stress_always,    4)
                                ),
                                false, true, 2,
                                (data, value) -> data.stressLevel = (Integer) value
                        )
                )
        ));

        // ── Step 10: Screen time before bed (split) ───────────────────────────
        cardStepConfigs.put(10, new StepConfig(
                R.drawable.ic_signup_screen_high,
                "Screens before bed?",
                "Blue light late at night is one of the top sleep disruptors",
                Arrays.asList(
                        new StepConfig.SectionConfig(
                                null,
                                Arrays.asList(
                                        new SelectableOption("I stop 1hr+ before",         R.drawable.ic_signup_screen_none,   "low"),
                                        new SelectableOption("About 30 mins",              R.drawable.ic_signup_screen_30min,  "moderate"),
                                        new SelectableOption("Right until I try to sleep", R.drawable.ic_signup_screen_high,   "high"),
                                        new SelectableOption("I fall asleep with it",      R.drawable.ic_signup_screen_always, "very_high")
                                ),
                                false, true, 2,
                                (data, value) -> data.screenTimeBeforeBed = (String) value
                        )
                )
        ));

        // ── Step 11: Lifestyle Habits (smoking + alcohol + caffeine bundled) ──
        // Smoking and alcohol each have 5 options with the most serious one full-width
        // so it visually stands out and prompts honest self-reflection.
        cardStepConfigs.put(11, new StepConfig(
                R.drawable.ic_onboarding_habits,
                "Lifestyle Habits",
                "Honest answers help us give you genuinely better health advice",
                Arrays.asList(
                        new StepConfig.SectionConfig(
                                "Do you smoke?",
                                "Smoking changes our cardiovascular, lung capacity, and recovery recommendations.",
                                Arrays.asList(
                                        new SelectableOption("Never, not even once", R.drawable.ic_signup_smoke_never,     "never"),
                                        new SelectableOption("I quit — proud of it", R.drawable.ic_signup_none,            "ex"),
                                        new SelectableOption("Only socially",         R.drawable.ic_signup_smoke_social,     "social"),
                                        new SelectableOption("Sometimes",             R.drawable.ic_signup_smoke_sometimes,  "occasional"),
                                        new SelectableOption("Daily habit",           R.drawable.ic_signup_smoke_daily,      "regular", true)
                                ),
                                false, true, 2,
                                (data, value) -> {
                                    data.smokingStatus = (String) value;
                                    switch ((String) value) {
                                        case "never":
                                        case "ex":
                                            data.smoker = false;
                                            data.smokingLevel = 0;
                                            data.smokingFrequency = "Non-smoker";
                                            break;
                                        case "social":
                                            data.smoker = false;
                                            data.smokingLevel = 1;
                                            data.smokingFrequency = "Social";
                                            break;
                                        case "occasional":
                                            data.smoker = true;
                                            data.smokingLevel = 2;
                                            data.smokingFrequency = "Occasional";
                                            break;
                                        case "regular":
                                            data.smoker = true;
                                            data.smokingLevel = 3;
                                            data.smokingFrequency = "Regular";
                                            break;
                                    }
                                }
                        ),
                        new StepConfig.SectionConfig(
                                "How often do you drink alcohol?",
                                "Alcohol quietly wrecks sleep quality, liver function, and hydration — even in small amounts.",
                                Arrays.asList(
                                        new SelectableOption("I don't drink",       R.drawable.ic_signup_alcohol_none,       "None"),
                                        new SelectableOption("Special occasions",    R.drawable.ic_signup_alcohol_special,    "Special Occasions"),
                                        new SelectableOption("Socially / weekends", R.drawable.ic_signup_alcohol_weekends,   "Socially"),
                                        new SelectableOption("Few times a week",    R.drawable.ic_signup_alcohol_regularly,  "Regularly"),
                                        new SelectableOption("Almost daily",        R.drawable.ic_signup_alcohol_daily,      "Frequently", true)
                                ),
                                false, true, 2,
                                (data, value) -> {
                                    data.alcoholConsumption = (String) value;
                                    switch ((String) value) {
                                        case "None":              data.alcoholLevel = 0; break;
                                        case "Special Occasions": data.alcoholLevel = 1; break;
                                        case "Socially":          data.alcoholLevel = 2; break;
                                        case "Regularly":         data.alcoholLevel = 3; break;
                                        case "Frequently":        data.alcoholLevel = 4; break;
                                    }
                                }
                        ),
                        new StepConfig.SectionConfig(
                                "What's your daily fuel?",
                                "Caffeine timing matters more than quantity — it's one of the biggest sleep disruptors we see.",
                                Arrays.asList(
                                        new SelectableOption("No caffeine",   R.drawable.ic_signup_caffeine_none, "none"),
                                        new SelectableOption("Tea person",    R.drawable.ic_signup_tea,           "tea"),
                                        new SelectableOption("Coffee lover",  R.drawable.ic_signup_coffee,        "coffee"),
                                        new SelectableOption("Energy drinks", R.drawable.ic_signup_energy_drink,  "energy_drinks"),
                                        SelectableOption.other("Other — you tell us", R.drawable.ic_edit, true)
                                ),
                                false, true, 2,
                                (data, value) -> data.caffeineHabit = (String) value
                        )
                )
        ));

        // ── Step 12: Family Health Story (+ Other) ────────────────────────────
        // Powers the genetics analysis engine — hereditary risk patterns in your bloodline.
        final String FAM_NONE = "__fam_none__";
        List<SelectableOption> familyOptions = Arrays.asList(
                new SelectableOption("Diabetes",        R.drawable.ic_signup_diabetes,     "Diabetes"),
                new SelectableOption("Heart Disease",   R.drawable.ic_signup_heart,        "Heart Disease"),
                new SelectableOption("Hypertension",    R.drawable.ic_signup_hypertension, "Hypertension"),
                new SelectableOption("Cancer",          R.drawable.ic_problem,             "Cancer"),
                new SelectableOption("Stroke",          R.drawable.ic_signup_brain2,       "Stroke"),
                new SelectableOption("Thyroid Issues",  R.drawable.ic_signup_thyroid,      "Thyroid Issues"),
                new SelectableOption("Kidney Disease",  R.drawable.ic_signup_kidney,       "Kidney Disease"),
                new SelectableOption("Mental Health",   R.drawable.ic_signup_mental_health,"Mental Health Issues"),
                SelectableOption.other("Other — you tell us", R.drawable.ic_edit),
                new SelectableOption("None / Not Sure", R.drawable.ic_signup_none,         FAM_NONE, true)
        );

        cardStepConfigs.put(12, new StepConfig(
                R.drawable.ic_signup_family,
                "Your Family's Health Story",
                "Hereditary patterns are one of the strongest health predictors — even if you feel perfectly fine now",
                Arrays.asList(
                        new StepConfig.SectionConfig(
                                "Has anyone in your family had...",
                                "Select from parents or grandparents' generation — hereditary risk is one of the strongest health predictors.",
                                familyOptions,
                                true, false, 2,
                                (data, value) -> {
                                    @SuppressWarnings("unchecked")
                                    List<Object> selected = (List<Object>) value;
                                    List<String> history = new ArrayList<>();
                                    for (Object o : selected) {
                                        String s = (String) o;
                                        if (!s.equals(FAM_NONE)) history.add(s);
                                    }
                                    data.familyHistory = history;
                                },
                                familyOptions.size() - 1
                        ),
                        new StepConfig.SectionConfig(
                                "Who in your family?",
                                "Pick the relatives who had the condition(s) you selected above — closer relatives (parents, siblings) carry more weight than distant ones.",
                                Arrays.asList(
                                        new SelectableOption("Parent(s)",      R.drawable.ic_signup_family, "Parent"),
                                        new SelectableOption("Grandparent(s)", R.drawable.ic_signup_family, "Grandparent"),
                                        new SelectableOption("Sibling(s)",     R.drawable.ic_signup_family, "Sibling"),
                                        new SelectableOption("Not sure",       R.drawable.ic_signup_none,   "__rel_none__", true)
                                ),
                                true, false, 2,
                                (data, value) -> {
                                    @SuppressWarnings("unchecked")
                                    List<Object> selected = (List<Object>) value;
                                    List<String> rels = new ArrayList<>();
                                    for (Object o : selected) {
                                        String s = (String) o;
                                        if (!s.equals("__rel_none__")) rels.add(s);
                                    }
                                    data.familyHistoryRelatives = rels;
                                },
                                3
                        )
                )
        ));

        // ── Step 13: Allergies (replaces chronic complaints — daily check-in captures those) ──
        // Allergies are safety-critical for the AI council, NutriCheck food checks,
        // and medication cross-references — and weren't collected anywhere else before.
        final String ALLERGY_NONE = "__allergy_none__";
        // NOTE: several icons below are using placeholder fallbacks to existing drawables.
        // Swap in dedicated icons (ic_signup_peanuts, ic_signup_dairy, ic_signup_eggs, etc.)
        // once they're added to res/drawable/.
        List<SelectableOption> allergyOptions = Arrays.asList(
                new SelectableOption("Peanuts & Nuts", R.drawable.ic_signup_healthy_food,  "Peanuts/Tree Nuts"),
                new SelectableOption("Dairy",          R.drawable.ic_signup_healthy_food,  "Dairy"),
                new SelectableOption("Eggs",           R.drawable.ic_egg,                  "Eggs"),
                new SelectableOption("Gluten / Wheat", R.drawable.ic_signup_gluten_free,   "Gluten"),
                new SelectableOption("Seafood",        R.drawable.ic_signup_fastfood,      "Seafood"),
                new SelectableOption("Soy",            R.drawable.ic_signup_seedling,      "Soy"),
                new SelectableOption("Pollen",         R.drawable.ic_signup_seedling,      "Pollen"),
                new SelectableOption("Dust",           R.drawable.ic_signup_block,         "Dust"),
                new SelectableOption("Pet Dander",     R.drawable.ic_signup_block,         "Pet Dander"),
                new SelectableOption("Penicillin",     R.drawable.ic_signup_healthcare,    "Penicillin"),
                new SelectableOption("NSAIDs / Aspirin", R.drawable.ic_signup_healthcare,  "NSAIDs"),
                new SelectableOption("Latex",          R.drawable.ic_signup_block,         "Latex"),
                SelectableOption.other("Other — you tell us", R.drawable.ic_edit),
                new SelectableOption("None",           R.drawable.ic_signup_none,          ALLERGY_NONE, true)
        );

        cardStepConfigs.put(13, new StepConfig(
                R.drawable.ic_signup_medical_hero,
                "Any allergies?",
                "Safety-critical — your food checks, meal suggestions, and medication advice will flag these",
                Arrays.asList(
                        new StepConfig.SectionConfig(
                                "Select all that apply",
                                "We'll cross-reference these with every food check, meal plan, and medication suggestion.",
                                allergyOptions,
                                true, false, 2,
                                (data, value) -> {
                                    @SuppressWarnings("unchecked")
                                    List<Object> selected = (List<Object>) value;
                                    List<String> list = new ArrayList<>();
                                    for (Object o : selected) {
                                        String s = (String) o;
                                        if (!s.equals(ALLERGY_NONE)) list.add(s);
                                    }
                                    data.allergies = list;
                                },
                                allergyOptions.size() - 1
                        )
                )
        ));

        // ── Step 14: Sun exposure (split from body complaints) ────────────────
        cardStepConfigs.put(14, new StepConfig(
                R.drawable.ic_signup_energy,
                "How much sun do you get?",
                "Daily sun drives Vitamin D, sleep rhythms, and mood more than people realise",
                Arrays.asList(
                        new StepConfig.SectionConfig(
                                null,
                                Arrays.asList(
                                        new SelectableOption("Mostly indoors",           R.drawable.ic_signup_wfh,      "low"),
                                        new SelectableOption("Some outdoor time",        R.drawable.ic_signup_walk,     "moderate"),
                                        new SelectableOption("Outdoors most of the day", R.drawable.ic_signup_retired,  "high")
                                ),
                                false, true, 3,
                                (data, value) -> data.sunExposure = (String) value
                        )
                )
        ));

        // ── Step 15: Blood Type + Medical Conditions (+ Other) ────────────────
        final String MED_NONE = "__none__";
        List<SelectableOption> conditionOptions = Arrays.asList(
                new SelectableOption("Diabetes",           R.drawable.ic_signup_diabetes,      "Diabetes"),
                new SelectableOption("Hypertension",       R.drawable.ic_signup_hypertension,  "Hypertension"),
                new SelectableOption("Heart Disease",      R.drawable.ic_signup_heart,         "Heart Disease"),
                new SelectableOption("Asthma",             R.drawable.ic_pulmonology,          "Asthma"),
                new SelectableOption("Thyroid",            R.drawable.ic_signup_thyroid,       "Thyroid Issues"),
                new SelectableOption("Arthritis",          R.drawable.ic_signup_joint_pain,    "Arthritis"),
                new SelectableOption("High Cholesterol",   R.drawable.ic_signup_heart,         "High Cholesterol"),
                new SelectableOption("PCOS/Hormonal",      R.drawable.ic_signup_menstrual_hero,"PCOS/Hormonal Issues"),
                new SelectableOption("Anxiety/Depression", R.drawable.ic_signup_psychiatry,    "Anxiety/Depression"),
                new SelectableOption("Digestive Issues",   R.drawable.ic_signup_bloating,      "Digestive Issues"),
                new SelectableOption("Kidney Issues",      R.drawable.ic_signup_kidney,        "Kidney Issues"),
                SelectableOption.other("Other — you tell us", R.drawable.ic_edit),
                new SelectableOption("None of the above",  R.drawable.ic_signup_none,          MED_NONE, true)
        );

        cardStepConfigs.put(15, new StepConfig(
                R.drawable.ic_signup_medical_hero,
                "Medical Info",
                "Optional — skip any section you prefer not to share",
                Arrays.asList(
                        new StepConfig.SectionConfig(
                                "What's your blood type?",
                                "Emergency contacts rely on this — and some dietary science varies by type.",
                                Arrays.asList(
                                        new SelectableOption("A+",         R.drawable.ic_bloodtype, "A+"),
                                        new SelectableOption("A-",         R.drawable.ic_bloodtype, "A-"),
                                        new SelectableOption("B+",         R.drawable.ic_bloodtype, "B+"),
                                        new SelectableOption("B-",         R.drawable.ic_bloodtype, "B-"),
                                        new SelectableOption("AB+",        R.drawable.ic_bloodtype, "AB+"),
                                        new SelectableOption("AB-",        R.drawable.ic_bloodtype, "AB-"),
                                        new SelectableOption("O+",         R.drawable.ic_bloodtype, "O+"),
                                        new SelectableOption("O-",         R.drawable.ic_bloodtype, "O-"),
                                        new SelectableOption("Don't Know", R.drawable.ic_help_clinic, "")
                                ),
                                false, false, 3,
                                (data, value) -> data.bloodType = (String) value
                        ),
                        new StepConfig.SectionConfig(
                                "Any medical conditions?",
                                "Flagged across nutrition, activity, and medication advice. Select all that apply.",
                                conditionOptions,
                                true, false, 2,
                                (data, value) -> {
                                    @SuppressWarnings("unchecked")
                                    List<Object> selected = (List<Object>) value;
                                    List<String> conditions = new ArrayList<>();
                                    for (Object o : selected) {
                                        String s = (String) o;
                                        if (!s.equals(MED_NONE)) conditions.add(s);
                                    }
                                    data.medicalConditions = conditions;
                                },
                                conditionOptions.size() - 1
                        ),
                        new StepConfig.SectionConfig(
                                "Do you take any regular medications?",
                                "Helps our food checks and AI avoid conflicts. Pick the types you take.",
                                Arrays.asList(
                                        new SelectableOption("Blood pressure", R.drawable.ic_signup_hypertension,  "Blood pressure"),
                                        new SelectableOption("Diabetes",       R.drawable.ic_signup_diabetes,      "Diabetes"),
                                        new SelectableOption("Cholesterol",    R.drawable.ic_signup_heart,         "Cholesterol"),
                                        new SelectableOption("Thyroid",        R.drawable.ic_signup_thyroid,       "Thyroid"),
                                        new SelectableOption("Heart",          R.drawable.ic_signup_heart,         "Heart"),
                                        new SelectableOption("Mental health",  R.drawable.ic_signup_mental_health, "Mental health"),
                                        SelectableOption.other("Other — you tell us", R.drawable.ic_edit),
                                        new SelectableOption("None",           R.drawable.ic_signup_none,          "__meds_none__", true)
                                ),
                                true, false, 2,
                                (data, value) -> {
                                    @SuppressWarnings("unchecked")
                                    List<Object> selected = (List<Object>) value;
                                    List<String> meds = new ArrayList<>();
                                    for (Object o : selected) {
                                        String s = (String) o;
                                        if (!s.equals("__meds_none__")) meds.add(s);
                                    }
                                    data.medicationCategories = meds;
                                },
                                7
                        )
                )
        ));

        // ── Step 16: Smoking detail (conditional — smokers & ex-smokers) ──────
        cardStepConfigs.put(16, new StepConfig(
                R.drawable.ic_signup_smoke_daily,
                "About your smoking",
                "A few details so our lung, heart, and recovery advice is accurate",
                Arrays.asList(
                        new StepConfig.SectionConfig(
                                "How long have you smoked?",
                                "Duration matters as much as amount for long-term risk.",
                                Arrays.asList(
                                        new SelectableOption("Under a year", R.drawable.ic_signup_smoke_social,    "<1 year"),
                                        new SelectableOption("1–5 years",    R.drawable.ic_signup_smoke_sometimes, "1-5 years"),
                                        new SelectableOption("5–10 years",   R.drawable.ic_signup_smoke_daily,     "5-10 years"),
                                        new SelectableOption("10+ years",    R.drawable.ic_signup_smoke_daily,     "10+ years")
                                ),
                                false, false, 2,
                                (data, value) -> data.smokingDuration = (String) value
                        ),
                        new StepConfig.SectionConfig(
                                "How many a day?",
                                "Typical daily amount.",
                                Arrays.asList(
                                        new SelectableOption("Under 5", R.drawable.ic_signup_smoke_social,    "<5"),
                                        new SelectableOption("5–10",    R.drawable.ic_signup_smoke_sometimes, "5-10"),
                                        new SelectableOption("10–20",   R.drawable.ic_signup_smoke_daily,     "10-20"),
                                        new SelectableOption("20+",     R.drawable.ic_signup_smoke_daily,     "20+")
                                ),
                                false, false, 2,
                                (data, value) -> data.cigarettesPerDay = (String) value
                        ),
                        new StepConfig.SectionConfig(
                                "When did you last smoke?",
                                "So we account for ex-smokers too.",
                                Arrays.asList(
                                        new SelectableOption("This week",       R.drawable.ic_signup_smoke_daily,  "This week"),
                                        new SelectableOption("This month",      R.drawable.ic_signup_smoke_social, "This month"),
                                        new SelectableOption("This year",       R.drawable.ic_signup_none,         "This year"),
                                        new SelectableOption("Over a year ago", R.drawable.ic_signup_none,         "Over a year ago")
                                ),
                                false, false, 2,
                                (data, value) -> data.lastSmoked = (String) value
                        )
                )
        ));

        // ── Step 17: Alcohol detail (conditional — drinkers) ─────────────────
        cardStepConfigs.put(17, new StepConfig(
                R.drawable.ic_signup_alcohol_regularly,
                "About your drinking",
                "One quick detail so our liver, sleep, and hydration advice fits you",
                Arrays.asList(
                        new StepConfig.SectionConfig(
                                "Drinks per week?",
                                "Roughly how many alcoholic drinks in a typical week.",
                                Arrays.asList(
                                        new SelectableOption("1–2",  R.drawable.ic_signup_alcohol_special,   "1-2"),
                                        new SelectableOption("3–5",  R.drawable.ic_signup_alcohol_weekends,  "3-5"),
                                        new SelectableOption("6–10", R.drawable.ic_signup_alcohol_regularly, "6-10"),
                                        new SelectableOption("10+",  R.drawable.ic_signup_alcohol_daily,     "10+")
                                ),
                                false, false, 2,
                                (data, value) -> data.drinksPerWeek = (String) value
                        )
                )
        ));

        // ── Step 18: Condition detail (conditional — if a condition was picked) ──
        cardStepConfigs.put(18, new StepConfig(
                R.drawable.ic_signup_medical_hero,
                "About your condition(s)",
                "Two quick details so our advice accounts for what you're managing",
                Arrays.asList(
                        new StepConfig.SectionConfig(
                                "When were you first diagnosed?",
                                "Roughly how long you've been managing it.",
                                Arrays.asList(
                                        new SelectableOption("Under a year", R.drawable.ic_signup_none,       "<1 year"),
                                        new SelectableOption("1–5 years",    R.drawable.ic_signup_healthcare, "1-5 years"),
                                        new SelectableOption("5–10 years",   R.drawable.ic_signup_healthcare, "5-10 years"),
                                        new SelectableOption("10+ years",    R.drawable.ic_signup_heart,      "10+ years")
                                ),
                                false, false, 2,
                                (data, value) -> data.conditionsDiagnosed = (String) value
                        ),
                        new StepConfig.SectionConfig(
                                "On medication for it?",
                                "Helps us avoid conflicting food and supplement advice.",
                                Arrays.asList(
                                        new SelectableOption("Yes",  R.drawable.ic_signup_healthcare, "Yes"),
                                        new SelectableOption("Some", R.drawable.ic_signup_healthcare, "Some"),
                                        new SelectableOption("No",   R.drawable.ic_signup_none,       "No")
                                ),
                                false, false, 3,
                                (data, value) -> data.conditionsMedicated = (String) value
                        )
                )
        ));

        // ── Step 19: Ancestry / ethnicity (predictive risk stratifier) ───────
        cardStepConfigs.put(19, new StepConfig(
                R.drawable.ic_signup_family,
                "Your ancestry",
                "Risk for some conditions varies by ancestry — this sharpens our predictions",
                Arrays.asList(
                        new StepConfig.SectionConfig(
                                null,
                                Arrays.asList(
                                        new SelectableOption("South Asian (India, Pakistan, Bangladesh, Sri Lanka, Nepal)", R.drawable.ic_signup_family, "South Asian"),
                                        new SelectableOption("East Asian (China, Japan, Korea)",        R.drawable.ic_signup_family, "East Asian"),
                                        new SelectableOption("Southeast Asian (Indonesia, Philippines, Vietnam, Thailand)",   R.drawable.ic_signup_family, "Southeast Asian"),
                                        new SelectableOption("Middle Eastern (Arab, Persian, Turkish)",    R.drawable.ic_signup_family, "Middle Eastern"),
                                        new SelectableOption("White/European",    R.drawable.ic_signup_family, "White/European"),
                                        new SelectableOption("Black/African",     R.drawable.ic_signup_family, "Black/African"),
                                        new SelectableOption("Hispanic/Latino",   R.drawable.ic_signup_family, "Hispanic/Latino"),
                                        new SelectableOption("Mixed/Other",       R.drawable.ic_signup_family, "Mixed/Other"),
                                        new SelectableOption("Prefer not to say", R.drawable.ic_signup_block,   "Prefer not to say", true)
                                ),
                                false, false, 2,
                                (data, value) -> data.ethnicity = (String) value
                        )
                )
        ));
    }

    /** Called by CardStepFragment to fetch its configuration. */
    public StepConfig getCardStepConfig(int stepIndex) {
        return cardStepConfigs.get(stepIndex);
    }

    // ── Dynamic step management ────────────────────────────────────────────

    /**
     * Rebuild the list of active step indices based on current onboarding data.
     * Called at init and after the personal step (gender selection) to
     * conditionally include/exclude the menstrual health step.
     */
    private void rebuildActiveSteps() {
        activeSteps.clear();
        for (int i = 0; i < allFragments.size(); i++) {
            if (isStepActive(i)) activeSteps.add(i);
        }
    }

    /** Whether a (possibly conditional) step should appear, given current answers. */
    private boolean isStepActive(int fragmentIndex) {
        switch (fragmentIndex) {
            case MENSTRUAL_FRAGMENT_INDEX: {
                String g = onboardingData.gender;
                return "Female".equals(g) || "Other".equals(g);
            }
            case SMOKING_DETAIL_FRAGMENT_INDEX:
                // Anyone who smokes now or used to (not a lifelong non-smoker).
                return onboardingData.smokingStatus != null
                        && !onboardingData.smokingStatus.isEmpty()
                        && !"never".equals(onboardingData.smokingStatus);
            case ALCOHOL_DETAIL_FRAGMENT_INDEX:
                return onboardingData.alcoholConsumption != null
                        && !onboardingData.alcoholConsumption.isEmpty()
                        && !"None".equals(onboardingData.alcoholConsumption);
            case CONDITIONS_DETAIL_FRAGMENT_INDEX:
                return onboardingData.medicalConditions != null
                        && !onboardingData.medicalConditions.isEmpty();
            default:
                return true;
        }
    }

    private int getTotalSteps() {
        return activeSteps.size();
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private void showStep(int step, boolean forward) {
        currentStep = step;
        int fragmentIndex = activeSteps.get(step);

        FragmentTransaction tx = getSupportFragmentManager().beginTransaction();
        if (forward) {
            tx.setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left);
        } else {
            tx.setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_right);
        }
        tx.replace(R.id.fragment_container, allFragments.get(fragmentIndex));
        tx.commit();

        updateTopBar(step);
    }

    private void updateTopBar(int step) {
        int total = getTotalSteps();
        tvStepLabel.setText("Step " + (step + 1) + " of " + total);
        btnBack.setVisibility(step == 0 ? View.INVISIBLE : View.VISIBLE);

        int progress = (int) Math.round((step + 1) * 100.0 / total);
        progressBar.setProgressCompat(progress, true);

        btnContinue.setText(step == total - 1 ? "Let's Go  →" : "Continue");
    }

    private void handleContinue() {
        int fragmentIndex = activeSteps.get(currentStep);
        BaseOnboardingFragment current = allFragments.get(fragmentIndex);
        if (!current.validate()) return;

        current.collectData(onboardingData);

        // On the account step, verify the email isn't already registered before
        // advancing. This is async, so advance only in its callback.
        if (fragmentIndex == ACCOUNT_FRAGMENT_INDEX && current instanceof OnboardingAccountFragment) {
            checkEmailThenAdvance((OnboardingAccountFragment) current, fragmentIndex);
            return;
        }

        advanceAfterStep(fragmentIndex);
    }

    /** Shared post-step navigation (rebuild conditionals, then advance or submit). */
    private void advanceAfterStep(int fragmentIndex) {
        // Rebuild active steps whenever an answer decides a later conditional
        // step: personal (gender→menstrual), habits (smoking/alcohol→details),
        // and blood+conditions (conditions→condition detail).
        if (fragmentIndex == 1
                || fragmentIndex == HABITS_FRAGMENT_INDEX
                || fragmentIndex == CONDITIONS_FRAGMENT_INDEX) {
            rebuildActiveSteps();
        }

        if (currentStep < getTotalSteps() - 1) {
            showStep(currentStep + 1, true);
        } else {
            // Final step → create the account, then verify the email.
            submitSignup();
        }
    }

    /**
     * Duplicate-email guard for onboarding step 1. Calls POST /api/auth/check-email
     * (mirrors the submitSignup networking) and only advances on {available:true}.
     * On {available:false} it flags the email field and stays put. On any network
     * or parse error it fails OPEN (advances) so a backend hiccup can't block
     * onboarding — the final signup still rejects duplicates.
     */
    private boolean checkingEmail = false;

    private void checkEmailThenAdvance(OnboardingAccountFragment accountFragment, int fragmentIndex) {
        if (checkingEmail) return; // guard against double-fire
        checkingEmail = true;

        final JSONObject body = new JSONObject();
        try {
            body.put("email", onboardingData.email);
        } catch (JSONException e) {
            checkingEmail = false;
            advanceAfterStep(fragmentIndex); // fail open
            return;
        }

        showLoading(true);
        StringRequest request = new StringRequest(
                Request.Method.POST,
                ApiConfig.BASE_URL + "/api/auth/check-email",
                response -> {
                    checkingEmail = false;
                    showLoading(false);
                    boolean available;
                    try {
                        available = new JSONObject(response).optBoolean("available", true);
                    } catch (JSONException e) {
                        available = true; // fail open on parse error
                    }
                    ApiConfig.logRestCall("/api/auth/check-email", true, "available=" + available);
                    if (available) {
                        advanceAfterStep(fragmentIndex);
                    } else {
                        accountFragment.setEmailError("Email already registered");
                    }
                },
                error -> {
                    // Fail open — a backend hiccup shouldn't block onboarding.
                    checkingEmail = false;
                    showLoading(false);
                    ApiConfig.logRestCall("/api/auth/check-email", false, error.toString());
                    advanceAfterStep(fragmentIndex);
                }
        ) {
            @Override
            public byte[] getBody() {
                return body.toString().getBytes(StandardCharsets.UTF_8);
            }

            @Override
            public String getBodyContentType() {
                return "application/json; charset=utf-8";
            }
        };

        request.setRetryPolicy(new DefaultRetryPolicy(15000, 0, 1f));
        Volley.newRequestQueue(this).add(request);
    }

    private void handleBack() {
        if (currentStep == 0) {
            showExitDialog();
        } else {
            showStep(currentStep - 1, false);
        }
    }

    private void showExitDialog() {
        Utils.DialogUtils.showConfirmDialog(this,
                "Exit Setup",
                "Are you sure you want to exit? Your progress will be lost.",
                "Exit", "Stay", true,
                this::finish);
    }

    public OnboardingData getOnboardingData() {
        return onboardingData;
    }

    // ── Email OTP verification ────────────────────────────────────────────────

    private interface OtpOk { void run(); }
    private interface OtpErr { void run(String message); }

    /**
     * Account is already created — now confirm the email. We show the code box
     * immediately (so it always appears, even if email delivery is down) and
     * fire off the code in the background.
     */
    private void showEmailVerification(String email, String name) {
        awaitingOtpVerification = true;
        otpEmail = email;
        otpName = name;
        showOtpDialog(email, name);
        sendOtp(email,
                () -> Utilities.toast(this, "Verification code sent to " + email),
                msg -> Utilities.toastLong(this, msg));
    }

    private void sendOtp(String email, OtpOk onOk, OtpErr onErr) {
        JSONObject body = new JSONObject();
        try {
            body.put("email", email);
        } catch (JSONException e) {
            onErr.run("Something went wrong. Please try again.");
            return;
        }
        StringRequest request = new StringRequest(
                Request.Method.POST,
                ApiConfig.BASE_URL + "/api/auth/send-otp",
                response -> { ApiConfig.logRestCall("/api/auth/send-otp", true, "otp sent"); onOk.run(); },
                error -> {
                    ApiConfig.logRestCall("/api/auth/send-otp", false, error.toString());
                    onErr.run(parseError(error, "Couldn't send the code. Please try again."));
                }
        ) {
            @Override public byte[] getBody() { return body.toString().getBytes(StandardCharsets.UTF_8); }
            @Override public String getBodyContentType() { return "application/json; charset=utf-8"; }
        };
        request.setRetryPolicy(new DefaultRetryPolicy(30000, 1, 1f));
        Volley.newRequestQueue(this).add(request);
    }

    /**
     * OTP entry using the app's standard edit-dialog component (dialog_edit_profile
     * + DialogTheme) so it matches every other dialog. Non-dismissible — the user
     * must verify (real code or resend) to continue.
     */
    private void showOtpDialog(String email, String name) {
        // Never stack two verification dialogs (e.g. onResume + savedState both fire).
        if (otpDialog != null && otpDialog.isShowing()) return;
        if (isFinishing() || isDestroyed()) return;

        android.view.LayoutInflater inflater = android.view.LayoutInflater.from(this);
        View dialogView = inflater.inflate(R.layout.dialog_edit_profile, null);

        ((TextView) dialogView.findViewById(R.id.dialog_title)).setText("Verify your email");

        LinearLayout fieldsContainer = dialogView.findViewById(R.id.fields_container);
        fieldsContainer.removeAllViews();

        int gap = (int) (12 * getResources().getDisplayMetrics().density);
        TextView info = new TextView(this);
        info.setText("Enter the code we emailed to " + email + " to verify your account.");
        info.setTextColor(0xFFB0B0B0);
        info.setTextSize(14);
        info.setPadding(0, 0, 0, gap);
        fieldsContainer.addView(info);

        View fieldLayout = inflater.inflate(R.layout.dialog_profile_field_item, fieldsContainer, false);
        final com.google.android.material.textfield.TextInputLayout codeLayout =
                (com.google.android.material.textfield.TextInputLayout) fieldLayout;
        codeLayout.setHint("Verification code");
        final com.google.android.material.textfield.TextInputEditText codeInput =
                fieldLayout.findViewById(R.id.field_input);
        codeInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        codeInput.setFilters(new android.text.InputFilter[]{ new android.text.InputFilter.LengthFilter(6) });
        fieldsContainer.addView(fieldLayout);

        final android.app.Dialog dialog = new android.app.Dialog(this, R.style.DialogTheme);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(dialogView);
        dialog.setCancelable(false);                 // non-dismissible
        dialog.setCanceledOnTouchOutside(false);
        if (dialog.getWindow() != null) {
            android.view.WindowManager.LayoutParams wlp = new android.view.WindowManager.LayoutParams();
            wlp.copyFrom(dialog.getWindow().getAttributes());
            wlp.width = android.view.WindowManager.LayoutParams.WRAP_CONTENT;
            wlp.height = android.view.WindowManager.LayoutParams.WRAP_CONTENT;
            dialog.getWindow().setAttributes(wlp);
            dialog.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        android.widget.Button verifyButton = dialogView.findViewById(R.id.save_button);
        android.widget.Button resendButton = dialogView.findViewById(R.id.cancel_button);
        verifyButton.setText("Verify");
        resendButton.setText("Resend");

        verifyButton.setOnClickListener(v -> {
            String code = codeInput.getText() != null ? codeInput.getText().toString().trim() : "";
            if (code.length() < 4) {
                codeLayout.setError("Enter the code from your email");
                return;
            }
            codeLayout.setError(null);
            showLoading(true, "Verifying...");
            verifyOtp(email, code,
                    () -> {
                        showLoading(false);
                        awaitingOtpVerification = false;
                        otpDialog = null;
                        dialog.dismiss();
                        goToMainAfterVerification(name);
                    },
                    msg -> { showLoading(false); codeLayout.setError(msg); });
        });

        resendButton.setOnClickListener(v -> {
            showLoading(true, "Sending verification code...");
            sendOtp(email,
                    () -> { showLoading(false); Utilities.toast(this, "New code sent."); },
                    msg -> { showLoading(false); Utilities.toastLong(this, msg); });
        });

        otpDialog = dialog;
        dialog.show();
    }

    /** Verified — enter the app. */
    private void goToMainAfterVerification(String name) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.putExtra("new_user_name", name);
        startActivity(intent);
    }

    private void verifyOtp(String email, String code, OtpOk onOk, OtpErr onErr) {
        JSONObject body = new JSONObject();
        try {
            body.put("email", email);
            body.put("otp", code);
        } catch (JSONException e) {
            onErr.run("Something went wrong. Please try again.");
            return;
        }
        StringRequest request = new StringRequest(
                Request.Method.POST,
                ApiConfig.BASE_URL + "/api/auth/verify-otp",
                response -> { ApiConfig.logRestCall("/api/auth/verify-otp", true, "verified"); onOk.run(); },
                error -> {
                    ApiConfig.logRestCall("/api/auth/verify-otp", false, error.toString());
                    onErr.run(parseError(error, "Incorrect or expired code."));
                }
        ) {
            @Override public byte[] getBody() { return body.toString().getBytes(StandardCharsets.UTF_8); }
            @Override public String getBodyContentType() { return "application/json; charset=utf-8"; }
        };
        request.setRetryPolicy(new DefaultRetryPolicy(30000, 1, 1f));
        Volley.newRequestQueue(this).add(request);
    }

    /** Best-effort extraction of a server error message from a Volley error. */
    private String parseError(com.android.volley.VolleyError error, String fallback) {
        if (error != null && error.networkResponse != null) {
            try {
                String bodyStr = new String(error.networkResponse.data, StandardCharsets.UTF_8);
                JSONObject json = new JSONObject(bodyStr);
                if (json.has("message")) return json.getString("message");
                if (json.has("errors")) {
                    JSONObject errs = json.getJSONObject("errors");
                    if (errs.names() != null && errs.names().length() > 0) {
                        return errs.getString(errs.names().getString(0));
                    }
                }
            } catch (Exception ignored) {}
        } else if (error instanceof com.android.volley.NoConnectionError) {
            return "No internet connection. Please check your network.";
        } else if (error instanceof com.android.volley.TimeoutError) {
            return "Connection timed out. Please try again.";
        }
        return fallback;
    }

    // ── Signup API call ───────────────────────────────────────────────────────

    private void submitSignup() {
        showLoading(true);
        try {
            JSONObject payload = buildPayload();
            Log.d(TAG, "Signup payload: " + payload);

            StringRequest request = new StringRequest(
                    Request.Method.POST,
                    ApiConfig.BASE_URL + "/api/auth/signup",
                    response -> {
                        ApiConfig.logRestCall("/api/auth/signup", true, "Onboarding signup success");
                        handleSignupSuccess(response);
                    },
                    error -> {
                        ApiConfig.logRestCall("/api/auth/signup", false, error.toString());
                        showLoading(false);
                        handleSignupError(error);
                    }
            ) {
                @Override
                public byte[] getBody() {
                    return payload.toString().getBytes(StandardCharsets.UTF_8);
                }

                @Override
                public String getBodyContentType() {
                    return "application/json; charset=utf-8";
                }
            };

            request.setRetryPolicy(new DefaultRetryPolicy(30000, 1, 1f));
            Volley.newRequestQueue(this).add(request);

        } catch (JSONException e) {
            showLoading(false);
            Log.e(TAG, "Error building payload", e);
            Utilities.toast(this, "Error preparing data. Please try again.");
        }
    }

    private JSONObject buildPayload() throws JSONException {
        OnboardingData d = onboardingData;
        JSONObject p = new JSONObject();

        // Account
        p.put("email",           d.email);
        p.put("password",        d.password);
        p.put("confirmPassword", d.confirmPassword);
        p.put("name",            d.name);
        p.put("phoneNumber",     d.phoneNumber);

        // Personal
        p.put("gender",   d.gender);
        p.put("location", d.location);
        p.put("ethnicity", d.ethnicity);
        if (d.dateOfBirth != null) {
            p.put("dateOfBirth",
                    new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(d.dateOfBirth));
        }

        // Body
        p.put("height", d.heightCm);
        p.put("weight", d.weightKg);
        if (d.waistCircumferenceCm > 0) {
            p.put("waistCircumference", d.waistCircumferenceCm);
        }

        // Goal + Activity
        p.put("primaryGoal",    d.primaryGoal);
        JSONArray specificGoalsArr = new JSONArray();
        for (String g : d.specificGoals) specificGoalsArr.put(g);
        p.put("specificGoals",  specificGoalsArr);
        p.put("activityLevel",  d.activityLevel);
        p.put("occupationType", d.occupationType);

        // Diet
        p.put("dietType",   d.dietType);
        p.put("mealsPerDay", d.mealsPerDay);
        p.put("waterIntake", d.waterIntake);

        // Sleep + Stress
        p.put("sleepHours",          d.sleepHours);
        p.put("stressLevel",         d.stressLevel);
        p.put("screenTimeBeforeBed", d.screenTimeBeforeBed);

        // Habits
        p.put("smoker",             d.smoker);
        p.put("smokingLevel",       d.smokingLevel);
        p.put("smokingFrequency",   d.smokingFrequency);
        p.put("smokingStatus",      d.smokingStatus);
        p.put("alcoholConsumption", d.alcoholConsumption);
        p.put("alcoholLevel",       d.alcoholLevel);
        p.put("caffeineHabit",      d.caffeineHabit);

        // Habit / condition follow-ups (conditional)
        p.put("smokingDuration",     d.smokingDuration);
        p.put("cigarettesPerDay",    d.cigarettesPerDay);
        p.put("lastSmoked",          d.lastSmoked);
        p.put("drinksPerWeek",       d.drinksPerWeek);
        p.put("conditionsDiagnosed", d.conditionsDiagnosed);
        p.put("conditionsMedicated", d.conditionsMedicated);

        // Family history
        JSONArray familyArr = new JSONArray();
        for (String f : d.familyHistory) familyArr.put(f);
        p.put("familyHistory", familyArr);

        JSONArray relativesArr = new JSONArray();
        for (String r : d.familyHistoryRelatives) relativesArr.put(r);
        p.put("familyHistoryRelatives", relativesArr);

        // Allergies (replaces chronic complaints — daily check-in captures those)
        JSONArray allergiesArr = new JSONArray();
        for (String a : d.allergies) allergiesArr.put(a);
        p.put("allergies", allergiesArr);

        // Sun exposure
        p.put("sunExposure", d.sunExposure);

        // Menstrual health (only send if applicable)
        if (!"not_applicable".equals(d.menstrualStatus)) {
            p.put("menstrualStatus", d.menstrualStatus);
            p.put("averageCycleLength", d.averageCycleLength);
            p.put("averagePeriodLength", d.averagePeriodLength);
            JSONArray menstrualSymptomsArr = new JSONArray();
            for (String s : d.menstrualSymptoms) menstrualSymptomsArr.put(s);
            p.put("menstrualSymptoms", menstrualSymptomsArr);
            p.put("contraceptionMethod", d.contraceptionMethod);
            p.put("pregnancyStatus", d.pregnancyStatus);
        }

        // Medical
        if (d.bloodType != null && !d.bloodType.isEmpty()) {
            p.put("bloodType", d.bloodType);
        }
        JSONArray conditions = new JSONArray();
        for (String c : d.medicalConditions) conditions.put(c);
        p.put("medicalConditions", conditions);

        // Predictive extras
        p.put("recentWeightChange", d.recentWeightChange);
        JSONArray medCats = new JSONArray();
        for (String m : d.medicationCategories) medCats.put(m);
        p.put("medicationCategories", medCats);

        p.put("weeklyGoal", 0.5);

        return p;
    }

    private void handleSignupSuccess(String response) {
        showLoading(false);
        try {
            JSONObject json = new JSONObject(response);
            String token  = json.getString("token");
            String userId = json.getString("userId");

            TokenManager.getInstance(this).saveLoginInfo(token, userId);

            OnboardingData d = onboardingData;
            UserProfile profile = new UserProfile();
            profile.setName(d.name);
            profile.setEmail(d.email);
            profile.setPhoneNumber(d.phoneNumber);
            profile.setGender(d.gender);
            profile.setLocation(d.location);
            profile.setDateOfBirth(d.dateOfBirth);
            profile.setHeight(d.heightCm);
            profile.setWeight(d.weightKg);
            profile.setWaistCircumference(d.waistCircumferenceCm);
            profile.setPrimaryGoal(d.primaryGoal);
            profile.setSpecificGoals(d.specificGoals != null ? d.specificGoals : new ArrayList<>());
            profile.setActivityLevel(d.activityLevel);
            profile.setDietType(d.dietType);
            profile.setSleepHours(d.sleepHours);
            profile.setSmoker(d.smoker);
            profile.setSmokingLevel(d.smokingLevel);
            profile.setSmokingFrequency(d.smokingFrequency);
            profile.setSmokingStatus(d.smokingStatus);
            profile.setAlcoholConsumption(d.alcoholConsumption);
            profile.setAlcoholLevel(d.alcoholLevel);
            profile.setCaffeineHabit(d.caffeineHabit);
            profile.setSmokingDuration(d.smokingDuration);
            profile.setCigarettesPerDay(d.cigarettesPerDay);
            profile.setLastSmoked(d.lastSmoked);
            profile.setDrinksPerWeek(d.drinksPerWeek);
            profile.setConditionsDiagnosed(d.conditionsDiagnosed);
            profile.setConditionsMedicated(d.conditionsMedicated);
            profile.setFamilyHistoryRelatives(d.familyHistoryRelatives != null ? d.familyHistoryRelatives : new ArrayList<>());
            profile.setEthnicity(d.ethnicity);
            profile.setRecentWeightChange(d.recentWeightChange);
            profile.setMedicationCategories(d.medicationCategories != null ? d.medicationCategories : new ArrayList<>());
            profile.setScreenTimeBeforeBed(d.screenTimeBeforeBed);
            profile.setSunExposure(d.sunExposure);
            profile.setOccupationType(d.occupationType);
            profile.setStressLevel(d.stressLevel);
            profile.setMealsPerDay(d.mealsPerDay);
            profile.setWaterIntake(d.waterIntake);
            profile.setFamilyHistory(d.familyHistory != null ? d.familyHistory : new ArrayList<>());
            profile.setBloodType(d.bloodType != null ? d.bloodType : "");
            profile.setMedicalConditions(d.medicalConditions != null ? d.medicalConditions : new ArrayList<>());
            profile.setMedications(new ArrayList<>());
            profile.setAllergies(d.allergies != null ? d.allergies : new ArrayList<>());
            profile.setPreferredExerciseTypes(new ArrayList<>());
            profile.setWeeklyGoal(0.5);

            // Menstrual health data
            if (d.menstrualStatus != null && !"not_applicable".equals(d.menstrualStatus)) {
                profile.setMenstrualStatus(d.menstrualStatus);
                profile.setAverageCycleLength(d.averageCycleLength);
                profile.setAveragePeriodLength(d.averagePeriodLength);
                profile.setPregnancyStatus(d.pregnancyStatus);
                profile.setContraceptionMethod(d.contraceptionMethod);
                profile.setMenstrualSymptoms(d.menstrualSymptoms != null ? d.menstrualSymptoms : new ArrayList<>());
            }

            profile.setAuthToken(token);
            profile.setLoggedIn(true);
            profile.setLastLogin(new Date());
            profile.setMetric(true);

            new DatabaseHelper(this).insertUserProfile(profile);

            Log.d(TAG, "Signup success — verifying email before entering app");

            // Account created. Now email the code and show the verification box;
            // we only enter the app once the email is verified.
            showEmailVerification(d.email, d.name);

        } catch (JSONException e) {
            Log.e(TAG, "Error parsing signup response", e);
            startActivity(new Intent(this, LoginActivity.class)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        }
    }

    private void handleSignupError(com.android.volley.VolleyError error) {
        String message = "Signup failed. Please try again.";
        if (error.networkResponse != null) {
            try {
                String body = new String(error.networkResponse.data, StandardCharsets.UTF_8);
                JSONObject json = new JSONObject(body);
                if (json.has("errors")) {
                    JSONObject errors = json.getJSONObject("errors");
                    if (errors.names() != null && errors.names().length() > 0) {
                        message = errors.getString(errors.names().getString(0));
                    }
                } else if (json.has("message")) {
                    message = json.getString("message");
                }
            } catch (Exception ignored) {}
        } else if (error instanceof com.android.volley.TimeoutError) {
            message = "Connection timed out. Please check your internet and try again.";
        } else if (error instanceof com.android.volley.NoConnectionError) {
            message = "No internet connection. Please check your network.";
        }
        Utilities.toastLong(this, message);
    }

    private void showLoading(boolean show) {
        showLoading(show, "Creating your account...");
    }

    private void showLoading(boolean show, String message) {
        if (show) {
            SimpleProgress.show(this, message);
        } else {
            SimpleProgress.hide();
        }
        btnContinue.setEnabled(!show);
        btnBack.setEnabled(!show);
    }

    @Override
    public void onBackPressed() {
        // Once the account is created and we're awaiting email verification,
        // Back must NOT walk back into the onboarding steps. Keep the user on
        // the (non-dismissible) code box instead.
        if (awaitingOtpVerification) {
            if (otpEmail != null && (otpDialog == null || !otpDialog.isShowing())) {
                showOtpDialog(otpEmail, otpName);
            }
            return;
        }
        handleBack();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Returning from the inbox app (or any recreation): make sure the code
        // box is back on screen if we're still waiting on verification.
        if (awaitingOtpVerification && otpEmail != null
                && (otpDialog == null || !otpDialog.isShowing())) {
            showOtpDialog(otpEmail, otpName);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("awaitingOtpVerification", awaitingOtpVerification);
        outState.putString("otpEmail", otpEmail);
        outState.putString("otpName", otpName);
    }

    @Override
    protected void onDestroy() {
        // Avoid leaking the dialog window on recreation; state is restored in onCreate.
        if (otpDialog != null && otpDialog.isShowing()) otpDialog.dismiss();
        otpDialog = null;
        super.onDestroy();
    }
}

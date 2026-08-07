package com.example.richhealth.Activities;
import Utils.Utilities;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import Models.OnboardingData;
import Models.SelectableOption;
import Models.StepConfig;
import Utils.ApiConfig;
import Utils.ProUpgradeDialog;
import Utils.SimpleProgress;

/**
 * Step-based flow for adding a living dependent (child or elder).
 * Reuses CardStepFragment and the same StepConfig pattern as OnboardingActivity.
 *
 * Steps:
 *   0 — Dependent type (child / elder)          [CardStepFragment key=100]
 *   1 — Basic info (name, password, DOB, gender) [DependentInfoFragment]
 *   2 — Menstrual health (conditional)           [CardStepFragment key=2]
 *   3 — Health info (blood type + conditions)    [CardStepFragment key=10]
 */
public class AddDependentActivity extends AppCompatActivity implements CardStepHost {

    private static final String TAG = "AddDependentActivity";
    private static final int MENSTRUAL_FRAGMENT_INDEX = 2;

    private final List<BaseOnboardingFragment> allFragments = new ArrayList<>();
    private final List<Integer> activeSteps = new ArrayList<>();
    private int currentStep = 0;

    private LinearProgressIndicator progressBar;
    private TextView tvStepLabel;
    private ImageButton btnBack;
    private MaterialButton btnContinue;
    private View loadingOverlay;

    private final OnboardingData data = new OnboardingData();
    private String dependentType = "";

    private final Map<Integer, StepConfig> cardStepConfigs = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_dependent);

        if (getSupportActionBar() != null) getSupportActionBar().hide();

        progressBar    = findViewById(R.id.progress_bar);
        tvStepLabel    = findViewById(R.id.tv_step_label);
        btnBack        = findViewById(R.id.btn_back);
        btnContinue    = findViewById(R.id.btn_continue);
        loadingOverlay = findViewById(R.id.loading_overlay);

        initCardStepConfigs();

        // Build all fragments (index 2 = menstrual is conditional)
        allFragments.add(CardStepFragment.newInstance(100));       // 0 – dependent type
        allFragments.add(new DependentInfoFragment());             // 1 – name, password, DOB, gender
        allFragments.add(CardStepFragment.newInstance(2));         // 2 – menstrual health (conditional)
        allFragments.add(CardStepFragment.newInstance(10));        // 3 – blood type + conditions

        rebuildActiveSteps();

        btnBack.setOnClickListener(v -> handleBack());
        btnContinue.setOnClickListener(v -> handleContinue());

        showStep(0, true);
    }

    private void initCardStepConfigs() {
        // ── Dependent Type ───────────────────────────────────────────────────
        cardStepConfigs.put(100, new StepConfig(
                R.drawable.ic_family_group,
                "Who are you adding?",
                "We'll create a health profile under your account",
                Arrays.asList(
                        new StepConfig.SectionConfig(
                                null,
                                Arrays.asList(
                                        new SelectableOption("Child",  R.drawable.ic_child_care,    "child"),
                                        new SelectableOption("Elder",  R.drawable.ic_assist_walker, "elder")
                                ),
                                false, true, 2,
                                (d, value) -> dependentType = (String) value
                        )
                )
        ));

        // ── Menstrual Health (reuse same config key 2 as OnboardingActivity) ─
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
                "Helps personalise health insights around their cycle",
                Arrays.asList(
                        new StepConfig.SectionConfig(
                                "Cycle status",
                                Arrays.asList(
                                        new SelectableOption("Regular",           R.drawable.ic_signup_cycle_regular,   "regular"),
                                        new SelectableOption("Irregular",         R.drawable.ic_signup_cycle_irregular, "irregular"),
                                        new SelectableOption("Perimenopause",     R.drawable.ic_signup_perimenopause,   "perimenopause"),
                                        new SelectableOption("Menopause",         R.drawable.ic_signup_menopause,       "menopause"),
                                        new SelectableOption("Prefer Not to Say", R.drawable.ic_signup_block,           "prefer_not_to_say")
                                ),
                                false, true, 2,
                                (d, value) -> d.menstrualStatus = (String) value
                        ),
                        new StepConfig.SectionConfig(
                                "Common cycle symptoms",
                                menstrualSymptomOptions,
                                true, false, 2,
                                (d, value) -> {
                                    @SuppressWarnings("unchecked")
                                    List<Object> selected = (List<Object>) value;
                                    List<String> symptoms = new ArrayList<>();
                                    for (Object o : selected) {
                                        String s = (String) o;
                                        if (!s.equals(MENSTRUAL_NONE)) symptoms.add(s);
                                    }
                                    d.menstrualSymptoms = symptoms;
                                },
                                menstrualSymptomOptions.size() - 1
                        ),
                        new StepConfig.SectionConfig(
                                "Pregnancy status",
                                Arrays.asList(
                                        new SelectableOption("Not Pregnant",       R.drawable.ic_signup_none,      "not_pregnant"),
                                        new SelectableOption("Pregnant",           R.drawable.ic_signup_pregnant,  "pregnant"),
                                        new SelectableOption("Postpartum",         R.drawable.ic_signup_postpartum,"postpartum"),
                                        new SelectableOption("Trying to Conceive", R.drawable.ic_signup_seedling,  "trying_to_conceive")
                                ),
                                false, false, 2,
                                (d, value) -> d.pregnancyStatus = (String) value
                        )
                )
        ));

        // ── Blood Type + Medical Conditions (reuse same config key 10) ──────
        final String MED_NONE = "__none__";
        List<SelectableOption> conditionOptions = Arrays.asList(
                new SelectableOption("Diabetes",           R.drawable.ic_signup_diabetes,       "Diabetes"),
                new SelectableOption("Hypertension",       R.drawable.ic_signup_hypertension,   "Hypertension"),
                new SelectableOption("Heart Disease",      R.drawable.ic_signup_heart,          "Heart Disease"),
                new SelectableOption("Asthma",             R.drawable.ic_pulmonology,           "Asthma"),
                new SelectableOption("Thyroid",            R.drawable.ic_signup_thyroid,        "Thyroid Issues"),
                new SelectableOption("Arthritis",          R.drawable.ic_signup_joint_pain,     "Arthritis"),
                new SelectableOption("High Cholesterol",   R.drawable.ic_signup_heart,          "High Cholesterol"),
                new SelectableOption("PCOS/Hormonal",      R.drawable.ic_signup_menstrual_hero, "PCOS/Hormonal Issues"),
                new SelectableOption("Anxiety/Depression", R.drawable.ic_signup_psychiatry,     "Anxiety/Depression"),
                new SelectableOption("Digestive Issues",   R.drawable.ic_signup_bloating,       "Digestive Issues"),
                new SelectableOption("Kidney Issues",      R.drawable.ic_signup_kidney,         "Kidney Issues"),
                new SelectableOption("None of the above",  R.drawable.ic_signup_none,           MED_NONE, true)
        );

        cardStepConfigs.put(10, new StepConfig(
                R.drawable.ic_signup_medical_hero,
                "Medical Info",
                "Optional — skip any section you prefer not to share",
                Arrays.asList(
                        new StepConfig.SectionConfig(
                                "Blood type",
                                Arrays.asList(
                                        new SelectableOption("A+",         R.drawable.ic_bloodtype,   "A+"),
                                        new SelectableOption("A-",         R.drawable.ic_bloodtype,   "A-"),
                                        new SelectableOption("B+",         R.drawable.ic_bloodtype,   "B+"),
                                        new SelectableOption("B-",         R.drawable.ic_bloodtype,   "B-"),
                                        new SelectableOption("AB+",        R.drawable.ic_bloodtype,   "AB+"),
                                        new SelectableOption("AB-",        R.drawable.ic_bloodtype,   "AB-"),
                                        new SelectableOption("O+",         R.drawable.ic_bloodtype,   "O+"),
                                        new SelectableOption("O-",         R.drawable.ic_bloodtype,   "O-"),
                                        new SelectableOption("Don't Know", R.drawable.ic_help_clinic, "")
                                ),
                                false, false, 3,
                                (d, value) -> d.bloodType = (String) value
                        ),
                        new StepConfig.SectionConfig(
                                "Medical conditions",
                                conditionOptions,
                                true, false, 2,
                                (d, value) -> {
                                    @SuppressWarnings("unchecked")
                                    List<Object> selected = (List<Object>) value;
                                    List<String> conditions = new ArrayList<>();
                                    for (Object o : selected) {
                                        String s = (String) o;
                                        if (!s.equals(MED_NONE)) conditions.add(s);
                                    }
                                    d.medicalConditions = conditions;
                                },
                                conditionOptions.size() - 1
                        )
                )
        ));
    }

    // ── CardStepHost interface ────────────────────────────────────────────────

    @Override
    public StepConfig getCardStepConfig(int stepIndex) {
        return cardStepConfigs.get(stepIndex);
    }

    @Override
    public OnboardingData getOnboardingData() {
        return data;
    }

    // ── Dynamic step management ──────────────────────────────────────────────

    private void rebuildActiveSteps() {
        activeSteps.clear();
        for (int i = 0; i < allFragments.size(); i++) {
            if (i == MENSTRUAL_FRAGMENT_INDEX) {
                String gender = data.gender;
                if ("Female".equals(gender) || "Other".equals(gender)) {
                    activeSteps.add(i);
                }
            } else {
                activeSteps.add(i);
            }
        }
    }

    private int getTotalSteps() {
        return activeSteps.size();
    }

    // ── Navigation ───────────────────────────────────────────────────────────

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

        btnContinue.setText(step == total - 1 ? "Add Dependent" : "Continue");
    }

    private void handleContinue() {
        int fragmentIndex = activeSteps.get(currentStep);
        BaseOnboardingFragment current = allFragments.get(fragmentIndex);
        if (!current.validate()) return;

        current.collectData(data);

        // After info step (gender selection), rebuild active steps for menstrual
        if (fragmentIndex == 1) {
            rebuildActiveSteps();
        }

        if (currentStep < getTotalSteps() - 1) {
            showStep(currentStep + 1, true);
        } else {
            submitDependent();
        }
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
                "Cancel",
                "Are you sure you want to cancel? Progress will be lost.",
                "Cancel", "Stay", true,
                this::finish);
    }

    // ── API call ─────────────────────────────────────────────────────────────

    private void submitDependent() {
        showLoading(true);
        try {
            JSONObject payload = buildPayload();
            Log.d(TAG, "Dependent payload: " + payload);

            String token = TokenManager.getInstance(this).getToken();

            StringRequest request = new StringRequest(
                    Request.Method.POST,
                    ApiConfig.BASE_URL + "/api/dependents/user",
                    response -> {
                        ApiConfig.logRestCall("/api/dependents/user", true, "Dependent created");
                        showLoading(false);
                        Utilities.toast(this, "Dependent added successfully!");
                        setResult(RESULT_OK);
                        finish();
                    },
                    error -> {
                        ApiConfig.logRestCall("/api/dependents/user", false, error.toString());
                        showLoading(false);
                        String msg = "Failed to add dependent";
                        boolean requiresUpgrade = false;
                        if (error.networkResponse != null) {
                            try {
                                String body = new String(error.networkResponse.data, StandardCharsets.UTF_8);
                                JSONObject errJson = new JSONObject(body);
                                msg = errJson.optString("message", msg);
                                requiresUpgrade = errJson.optBoolean("requiresUpgrade", false);
                            } catch (Exception ignored) {}
                        }
                        if (requiresUpgrade) {
                            ProUpgradeDialog proDialog = new ProUpgradeDialog(this);
                            proDialog.setLimitContext(msg);
                            proDialog.show(isPro -> {
                                if (isPro) submitDependent();
                            });
                        } else {
                            Utilities.toastLong(this, msg);
                        }
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

                @Override
                public Map<String, String> getHeaders() {
                    Map<String, String> headers = new HashMap<>();
                    headers.put("Authorization", "Bearer " + token);
                    return headers;
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
        JSONObject p = new JSONObject();

        p.put("dependentType", dependentType);
        p.put("name", data.name);
        p.put("password", data.password);
        p.put("gender", data.gender);

        if (data.dateOfBirth != null) {
            p.put("dateOfBirth",
                    new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(data.dateOfBirth));
        }

        if (data.bloodType != null && !data.bloodType.isEmpty()) {
            p.put("bloodType", data.bloodType);
        }

        JSONArray conditions = new JSONArray();
        for (String c : data.medicalConditions) conditions.put(c);
        p.put("medicalConditions", conditions);

        // Menstrual fields (only if applicable)
        if (!"not_applicable".equals(data.menstrualStatus)) {
            p.put("menstrualStatus", data.menstrualStatus);
            p.put("averageCycleLength", data.averageCycleLength);
            p.put("averagePeriodLength", data.averagePeriodLength);
            JSONArray menstrualArr = new JSONArray();
            for (String s : data.menstrualSymptoms) menstrualArr.put(s);
            p.put("menstrualSymptoms", menstrualArr);
            p.put("contraceptionMethod", data.contraceptionMethod);
            p.put("pregnancyStatus", data.pregnancyStatus);
        }

        return p;
    }

    private void showLoading(boolean show) {
        if (show) {
            SimpleProgress.show(this, "Adding dependent...");
        } else {
            SimpleProgress.hide();
        }
        btnContinue.setEnabled(!show);
    }
}

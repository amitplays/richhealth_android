package com.example.richhealth.Activities;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import android.app.AlertDialog;
import com.example.richhealth.R;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.formatter.ValueFormatter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import Database.DatabaseHelper;
import Models.UserProfile;
import Utils.ApiConfig;
import Utils.ErrorHandler;
import Utils.ProStatusManager;
import Utils.ProUpgradeDialog;
import Utils.SimpleProgress;

public class HealthAnalysisActivity extends AppCompatActivity {

    private static final String TAG = "HealthAnalysisActivity";

    private ProStatusManager proStatusManager;
    private TokenManager tokenManager;

    // Cached data
    private JSONObject lastHealthAnalysisJson;
    private JSONObject cachedTypeAnalyses;
    private String currentAnalysisTab = "reports";

    // Prevents GET re-fetch from overwriting freshly-generated tab content
    private boolean skipTabRender = false;

    // AQI dialog
    private Api.AQIAPIService aqiApiService;

    // Data overview chart
    private PieChart dataOverviewChart;

    // Views
    private TextView usageBadge;
    private TextView statusLine;
    private TextView dialogHeadline;
    private TextView dialogReason;
    private com.google.android.material.button.MaterialButton refreshButton;
    private TextView dialogLastUpdated;
    private ProgressBar refreshProgress;
    private com.google.android.material.button.MaterialButton bottomRefreshButton;

    // Profile stat cells
    private View cellAge, cellGender, cellBlood, cellWeight, cellBmi, cellSleep;
    private TextView pillAge, pillGender, pillBlood, pillWeight, pillBmi, pillBmiLabel, pillSleep;
    private TextView completeProfileCta;

    // Data on file summary
    private TextView dataOnFileSummary;

    // Hero health score (inside AI Insight card)
    private View heroHealthScoreSection;
    private TextView heroHealthScoreValue;
    private ProgressBar heroHealthScoreProgress;

    // Action items
    private View actionItemsCard;
    private LinearLayout actionItemsContainer;

    // Profile section
    private LinearLayout profileSection;
    private TextView profilePercentText;
    private ProgressBar profileProgress;
    private TextView missingFieldsText;

    // AQI
    private View aqiCard;
    private TextView aqiLocation, aqiValue, aqiQuality, aqiRecords, aqiAnalysis, aqiTapDetails;

    // Change banner
    private LinearLayout changeBanner;
    private TextView changeBannerText, changeBannerRefresh;

    // Analysis tabs & content
    private TextView tabReports, tabSymptoms, tabMedications, tabMeasurements, tabGenetics, tabDiagnostics;
    private LinearLayout analysisShimmer;
    private TextView analysisSummary;
    private LinearLayout analysisDetailsContainer;
    private TextView analysisNoData;
    private TextView analysisGeneratedAt;

    private TextView[] allTabs;
    private final String[] tabKeys = {"reports", "symptoms", "medications", "measurements", "genetics", "diagnostics"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_health_analysis);

        tokenManager = TokenManager.getInstance(this);
        proStatusManager = ProStatusManager.getInstance(this);

        initViews();
        loadHealthAnalysisData();
        Utils.IconAnimator.animateSectionIcons(getWindow().getDecorView());
    }

    private void initViews() {
        ImageButton backButton = findViewById(R.id.back_button);
        backButton.setOnClickListener(v -> finish());

        // [PLAN-PILL-REVIEW] disabled (backend-driven, keep after review): usageBadge = findViewById(R.id.dialog_usage_badge);
        statusLine = findViewById(R.id.dialog_status_line);
        dialogHeadline = findViewById(R.id.dialog_headline_text);
        dialogReason = findViewById(R.id.dialog_status_reason);
        refreshButton = findViewById(R.id.dialog_refresh_button);
        dialogLastUpdated = findViewById(R.id.dialog_last_updated_text);
        refreshProgress = findViewById(R.id.dialog_refresh_progress);
        bottomRefreshButton = findViewById(R.id.dialog_bottom_refresh_button);

        // Profile stat cells
        cellAge = findViewById(R.id.dialog_cell_age);
        pillAge = findViewById(R.id.dialog_pill_age);
        cellGender = findViewById(R.id.dialog_cell_gender);
        pillGender = findViewById(R.id.dialog_pill_gender);
        cellBlood = findViewById(R.id.dialog_cell_blood);
        pillBlood = findViewById(R.id.dialog_pill_blood);
        cellWeight = findViewById(R.id.dialog_cell_weight);
        pillWeight = findViewById(R.id.dialog_pill_weight);
        cellBmi = findViewById(R.id.dialog_cell_bmi);
        pillBmi = findViewById(R.id.dialog_pill_bmi);
        pillBmiLabel = findViewById(R.id.dialog_pill_bmi_label);
        cellSleep = findViewById(R.id.dialog_cell_sleep);
        pillSleep = findViewById(R.id.dialog_pill_sleep);

        // Complete profile CTA
        completeProfileCta = findViewById(R.id.dialog_complete_profile_cta);
        if (completeProfileCta != null) {
            completeProfileCta.setOnClickListener(v -> {
                android.content.Intent intent = new android.content.Intent(HealthAnalysisActivity.this, MainActivity.class);
                intent.putExtra("navigate_to", "profile");
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
            });
        }

        // Data on file summary
        dataOnFileSummary = findViewById(R.id.dialog_data_on_file_summary);

        // Hero health score
        heroHealthScoreSection = findViewById(R.id.hero_health_score_section);
        heroHealthScoreValue = findViewById(R.id.hero_health_score_value);
        heroHealthScoreProgress = findViewById(R.id.hero_health_score_progress);

        // Action items
        actionItemsCard = findViewById(R.id.dialog_action_items_card);
        actionItemsContainer = findViewById(R.id.dialog_action_items_container);

        profileSection = findViewById(R.id.dialog_profile_section);
        profilePercentText = findViewById(R.id.dialog_profile_percent_text);
        profileProgress = findViewById(R.id.dialog_profile_progress);
        missingFieldsText = findViewById(R.id.dialog_missing_fields_text);

        // AQI
        aqiCard = findViewById(R.id.dialog_aqi_card);
        aqiLocation = findViewById(R.id.dialog_aqi_location);
        aqiValue = findViewById(R.id.dialog_aqi_value);
        aqiQuality = findViewById(R.id.dialog_aqi_quality);
        aqiRecords = findViewById(R.id.dialog_aqi_records);
        aqiAnalysis = findViewById(R.id.dialog_aqi_analysis);
        aqiTapDetails = findViewById(R.id.dialog_aqi_tap_details);

        // Change banner
        changeBanner = findViewById(R.id.dialog_change_banner);
        changeBannerText = findViewById(R.id.dialog_change_banner_text);
        changeBannerRefresh = findViewById(R.id.dialog_change_banner_refresh);

        // Analysis tabs
        tabReports = findViewById(R.id.tab_reports);
        tabSymptoms = findViewById(R.id.tab_symptoms);
        tabMedications = findViewById(R.id.tab_medications);
        tabMeasurements = findViewById(R.id.tab_measurements);
        tabGenetics = findViewById(R.id.tab_genetics);
        tabDiagnostics = findViewById(R.id.tab_diagnostics);
        analysisShimmer = findViewById(R.id.dialog_analysis_shimmer);
        analysisSummary = findViewById(R.id.dialog_analysis_summary);
        analysisDetailsContainer = findViewById(R.id.dialog_analysis_details_container);
        analysisNoData = findViewById(R.id.dialog_analysis_no_data);
        analysisGeneratedAt = findViewById(R.id.dialog_analysis_generated_at);

        allTabs = new TextView[]{tabReports, tabSymptoms, tabMedications, tabMeasurements, tabGenetics, tabDiagnostics};

        // Tab click listeners
        for (int i = 0; i < allTabs.length; i++) {
            final int idx = i;
            allTabs[i].setOnClickListener(v -> {
                currentAnalysisTab = tabKeys[idx];
                for (int j = 0; j < allTabs.length; j++) {
                    if (j == idx) {
                        allTabs[j].setBackgroundResource(R.drawable.pill_tab_selected);
                        allTabs[j].setTextColor(Color.WHITE);
                    } else {
                        allTabs[j].setBackgroundResource(R.drawable.pill_tab_unselected);
                        allTabs[j].setTextColor(Color.parseColor("#AAAAAA"));
                    }
                }
                displayAnalysisTabContent(currentAnalysisTab);
            });
        }

        // Refresh button
        refreshButton.setOnClickListener(v -> {
            // Gate free users who already used their monthly analysis
            if (!proStatusManager.isProUser() && isAnalysisUsedThisMonth()) {
                showLimitReachedDialog("You've used your 1 free health analysis this month.");
                return;
            }
            refreshButton.setVisibility(View.INVISIBLE);
            refreshProgress.setVisibility(View.VISIBLE);
            dialogLastUpdated.setText("Generating analysis...");
            if (changeBanner != null) changeBanner.setVisibility(View.GONE);
            refreshAllAnalyses();
        });

        // Bottom refresh button
        if (bottomRefreshButton != null) {
            bottomRefreshButton.setOnClickListener(v -> {
                // Gate free users who already used their monthly analysis
                if (!proStatusManager.isProUser() && isAnalysisUsedThisMonth()) {
                    showLimitReachedDialog("You've used your 1 free health analysis this month.");
                    return;
                }
                refreshButton.setVisibility(View.INVISIBLE);
                refreshProgress.setVisibility(View.VISIBLE);
                dialogLastUpdated.setText("Generating analysis...");
                bottomRefreshButton.setEnabled(false);
                bottomRefreshButton.setText("Generating...");
                if (changeBanner != null) changeBanner.setVisibility(View.GONE);
                refreshAllAnalyses();
            });
        }

        // Banner refresh tap
        changeBannerRefresh.setOnClickListener(v -> {
            if (!proStatusManager.isProUser() && isAnalysisUsedThisMonth()) {
                showLimitReachedDialog("You've used your 1 free health analysis this month.");
                return;
            }
            changeBanner.setVisibility(View.GONE);
            refreshAllAnalyses();
        });

        // AQI card tap — fetch and show AQI chart dialog
        aqiApiService = new Api.AQIAPIService(this);
        aqiCard.setOnClickListener(v -> fetchAndShowAQIHistory());

        // Data overview pie chart
        setupDataOverviewChart();
    }

    private void loadHealthAnalysisData() {
        loadHealthAnalysisData(true);
    }

    private void loadHealthAnalysisData(boolean showProgress) {
        String token = tokenManager.getToken();
        if (token == null) {
            dialogHeadline.setText("Login required");
            return;
        }

        if (showProgress) {
            SimpleProgress.show(this, "Loading health analysis...");
        }

        String url = ApiConfig.BASE_URL + "/api/health/analysis";

        StringRequest request = new StringRequest(Request.Method.GET, url,
            response -> {
                if (showProgress) SimpleProgress.hide();
                try {
                    JSONObject jsonResponse = new JSONObject(response);
                    JSONObject analysis = jsonResponse.optJSONObject("analysis");

                    if (analysis == null) {
                        statusLine.setText("● Not yet generated");
                        statusLine.setTextColor(Color.parseColor("#808080"));
                        dialogHeadline.setText("Tap Refresh to generate your first health analysis");
                        dialogReason.setText("We need to analyze your health data to provide insights");
                        dialogLastUpdated.setText("");
                        showFallbackState();
                        return;
                    }

                    lastHealthAnalysisJson = analysis;

                    // Only overwrite tab data from GET if we didn't just generate fresh data via POST
                    if (!skipTabRender) {
                        if (analysis.has("healthAnalysisCache") && !analysis.isNull("healthAnalysisCache")) {
                            cachedTypeAnalyses = analysis.getJSONObject("healthAnalysisCache");
                        }
                    }

                    populateViews();
                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing health analysis response", e);
                    dialogHeadline.setText("Unable to load analysis");
                }
            },
            error -> {
                if (showProgress) SimpleProgress.hide();
                ApiConfig.logRestCall(url, false, error.toString());
                ErrorHandler.ParsedError parsed = ErrorHandler.parse(error);
                if (parsed.type == ErrorHandler.ErrorType.AUTH_EXPIRED) {
                    ErrorHandler.handleAuthExpired(HealthAnalysisActivity.this);
                    return;
                }
                String hint = parsed.type == ErrorHandler.ErrorType.NETWORK_ERROR
                        ? "No connection" : parsed.message;
                dialogHeadline.setText("Unable to load analysis");
                if (statusLine != null) {
                    statusLine.setText("● " + hint);
                    statusLine.setTextColor(Color.parseColor("#808080"));
                }
            }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + token);
                headers.put("Content-Type", "application/json");
                return headers;
            }
        };

        request.setRetryPolicy(new DefaultRetryPolicy(30000, 1, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        Volley.newRequestQueue(this).add(request);
    }

    private void populateViews() {
        currentAnalysisTab = "reports";

        // Sync tab visual highlight
        for (int t = 0; t < tabKeys.length; t++) {
            if (tabKeys[t].equals(currentAnalysisTab)) {
                allTabs[t].setBackgroundResource(R.drawable.pill_tab_selected);
                allTabs[t].setTextColor(Color.WHITE);
            } else {
                allTabs[t].setBackgroundResource(R.drawable.pill_tab_unselected);
                allTabs[t].setTextColor(Color.parseColor("#AAAAAA"));
            }
        }
        // Update tab labels with data counts from cache
        String[] tabNames = {"Reports", "Symptoms", "Medications", "Measurements", "Family Health", "Diagnostics"};
        if (cachedTypeAnalyses != null) {
            for (int i = 0; i < tabKeys.length; i++) {
                JSONObject tabData = cachedTypeAnalyses.optJSONObject(tabKeys[i]);
                int dataCount = tabData != null ? tabData.optInt("dataCount", -1) : -1;
                if (dataCount > 0) {
                    allTabs[i].setText(tabNames[i] + " (" + dataCount + ")");
                } else {
                    allTabs[i].setText(tabNames[i]);
                }
            }
        }

        // Only re-render tabs from GET if we didn't just generate fresh data via POST
        if (!skipTabRender) {
            displayAnalysisTabContent(currentAnalysisTab);
        }
        skipTabRender = false; // reset for next load

        // Change banner
        if (lastHealthAnalysisJson != null) {
            boolean needsUpdate = lastHealthAnalysisJson.optBoolean("healthDataNeedsUpdate", false);
            JSONObject changes = lastHealthAnalysisJson.optJSONObject("dataChangesSinceAnalysis");
            if (needsUpdate && changes != null) {
                int rc = changes.optInt("reports", 0);
                int sc = changes.optInt("symptoms", 0);
                int mc = changes.optInt("medications", 0);
                int msc = changes.optInt("measurements", 0);
                int gc = changes.optInt("genetics", 0);
                int total = rc + sc + mc + msc + gc;
                if (total > 0) {
                    List<String> parts = new ArrayList<>();
                    if (rc > 0) parts.add(rc + " report" + (rc > 1 ? "s" : ""));
                    if (sc > 0) parts.add(sc + " symptom" + (sc > 1 ? "s" : ""));
                    if (mc > 0) parts.add(mc + " medication" + (mc > 1 ? "s" : ""));
                    if (msc > 0) parts.add(msc + " measurement" + (msc > 1 ? "s" : ""));
                    if (gc > 0) parts.add(gc + " family health update" + (gc > 1 ? "s" : ""));
                    changeBannerText.setText("Changed since last analysis: " + String.join(", ", parts));
                    changeBanner.setVisibility(View.VISIBLE);
                }
            }
        }

        // Plan badge — read usageStatus from backend response if available, else fallback to tier
        // [PLAN-PILL-REVIEW] disabled (backend-driven, keep after review): if (proStatusManager != null) {
            // usageBadge.setVisibility(View.VISIBLE);
            // String tier = proStatusManager.getUserTier();

            // // Try to read usageStatus from backend response (top-level or inside analysis)
            // JSONObject usageStatus = null;
            // if (lastHealthAnalysisJson != null) {
                // usageStatus = lastHealthAnalysisJson.optJSONObject("usageStatus");
            // }

            // if (usageStatus != null) {
                // int count = usageStatus.optInt("count", 0);
                // Object limitObj = usageStatus.opt("limit");
                // boolean isUnlimited = (limitObj == null || limitObj.toString().equals("null"));

                // if (isUnlimited) {
                    // String tierLabel = tier.substring(0, 1).toUpperCase() + tier.substring(1);
                    // if (tier.equals("family_member")) tierLabel = "Family";
                    // usageBadge.setText(tierLabel + " · Unlimited");
                    // usageBadge.setTextColor(tier.equals("ultra") ? Color.parseColor("#E040FB") : Color.parseColor("#008b8b"));
                // } else {
                    // int limit = usageStatus.optInt("limit", 1);
                    // usageBadge.setText(count + "/" + limit + " used");
                    // usageBadge.setTextColor(count >= limit ? Color.parseColor("#FF9800") : Color.parseColor("#808080"));
                // }
            // } else {
                // // Fallback: tier-based badge (before API response arrives). Uses PlanBadge so the
                // // plan name + color match every other screen (was a divergent purple "Ultra").
                // if (tier != null && !tier.isEmpty() && !"free".equals(tier)) {
                    // // [PLAN-PILL-REVIEW] disabled (backend-driven, keep after review): Utils.PlanBadge.apply(usageBadge, tier, Utils.PlanBadge.Style.COMPACT, " · Unlimited");
                // } else {
                    // boolean used = isAnalysisUsedThisMonth();
                    // usageBadge.setText("Free · " + (used ? "1" : "0") + "/1 used");
                    // usageBadge.setTextColor(Color.parseColor(used ? "#FF9800" : "#808080"));
                // }
            // }
        // } else {
            // usageBadge.setVisibility(View.GONE);
        // }

        // Load cached user analysis for lifestyle data
        JSONObject userAnalysis = getCachedUserAnalysis();

        if (lastHealthAnalysisJson != null) {
            try {
                String lastUpdated = lastHealthAnalysisJson.optString("lastUpdated", null);
                boolean neverGenerated = (lastUpdated == null || lastUpdated.isEmpty());

                if (neverGenerated) {
                    statusLine.setText("● Not yet generated");
                    statusLine.setTextColor(Color.parseColor("#808080"));
                    dialogHeadline.setText("Tap Refresh to generate your first health analysis");
                    dialogReason.setText("We need to analyze your health data to provide insights");
                    dialogLastUpdated.setText("");
                } else {
                    String headline = lastHealthAnalysisJson.optString("headline", "Complete your profile for personalized analysis");
                    dialogHeadline.setText(headline);

                    JSONObject healthStatus = lastHealthAnalysisJson.optJSONObject("healthAnalysisStatus");
                    String statusLevel = "";
                    String reason = "";
                    if (healthStatus != null) {
                        statusLevel = healthStatus.optString("level", "");
                        reason = healthStatus.optString("reason", "");
                    }

                    statusLine.setText(getStatusText(statusLevel));
                    statusLine.setTextColor(getStatusColor(statusLevel));
                    dialogReason.setText(reason);

                    String timeAgo = formatTimeAgo(lastUpdated);
                    dialogLastUpdated.setText("Generated " + timeAgo);
                }

                // Profile snapshot
                JSONObject metrics = lastHealthAnalysisJson.optJSONObject("metrics");
                JSONObject dataPoints = lastHealthAnalysisJson.optJSONObject("dataPoints");

                // Cell: Age
                if (metrics != null && !metrics.isNull("age")) {
                    int age = metrics.optInt("age", 0);
                    if (age > 0) {
                        pillAge.setText(String.valueOf(age));
                        pillAge.setTextColor(Color.parseColor("#80DEEA"));
                    } else {
                        pillAge.setText("Not set");
                        pillAge.setTextColor(Color.parseColor("#808080"));
                        pillAge.setTextSize(13);
                    }
                }

                // Cell: Gender — primary: metrics from health analysis API, fallback: cached user analysis
                String gender = "";
                if (metrics != null) gender = metrics.optString("gender", "");
                if ((gender.isEmpty() || gender.equals("null")) && userAnalysis != null) {
                    JSONObject profile = userAnalysis.optJSONObject("profile");
                    if (profile != null) gender = profile.optString("gender", "");
                }
                if (!gender.isEmpty() && !gender.equals("null")) {
                    pillGender.setText(gender.substring(0, 1).toUpperCase() + gender.substring(1));
                    pillGender.setTextColor(Color.parseColor("#CE93D8"));
                } else {
                    pillGender.setText("Not set");
                    pillGender.setTextColor(Color.parseColor("#808080"));
                    pillGender.setTextSize(13);
                }

                // Cell: Blood Group — primary: metrics, fallback: cached user analysis
                String bloodType = "";
                if (metrics != null) bloodType = metrics.optString("bloodType", "");
                if ((bloodType.isEmpty() || bloodType.equals("null") || bloodType.equals("Unknown")) && userAnalysis != null) {
                    JSONObject medData = userAnalysis.optJSONObject("medicalData");
                    if (medData != null) bloodType = medData.optString("bloodType", "");
                }
                if (!bloodType.isEmpty() && !bloodType.equals("null") && !bloodType.equals("Unknown")) {
                    pillBlood.setText(bloodType);
                    pillBlood.setTextColor(Color.parseColor("#EF9A9A"));
                } else {
                    pillBlood.setText("Not set");
                    pillBlood.setTextColor(Color.parseColor("#808080"));
                    pillBlood.setTextSize(13);
                }

                // Cell: Weight — primary: metrics, fallback: cached user analysis
                double weight = 0;
                if (metrics != null) weight = metrics.optDouble("weight", 0);
                if (weight <= 0 && userAnalysis != null) {
                    JSONObject profile = userAnalysis.optJSONObject("profile");
                    JSONObject healthMetrics = profile != null ? profile.optJSONObject("healthMetrics") : null;
                    if (healthMetrics != null) weight = healthMetrics.optDouble("weight", 0);
                }
                if (weight > 0) {
                    pillWeight.setText(Math.round(weight) + " kg");
                    pillWeight.setTextColor(Color.parseColor("#A5D6A7"));
                } else {
                    pillWeight.setText("Not set");
                    pillWeight.setTextColor(Color.parseColor("#808080"));
                    pillWeight.setTextSize(13);
                }

                // Cell: BMI
                if (metrics != null && !metrics.isNull("bmi")) {
                    double bmi = metrics.optDouble("bmi", 0);
                    if (bmi > 0) {
                        pillBmi.setText(String.format("%.1f", bmi));
                        pillBmi.setTextColor(Color.parseColor("#FFF59D"));
                        pillBmiLabel.setText("BMI \u00B7 " + getBmiCategory(bmi));
                    } else {
                        pillBmi.setText("–");
                        pillBmi.setTextColor(Color.parseColor("#808080"));
                        pillBmiLabel.setText("BMI");
                    }
                }

                // Cell: Sleep — primary: metrics, fallback: cached user analysis
                double sleepHours = 0;
                if (metrics != null) sleepHours = metrics.optDouble("sleepHours", 0);
                if (sleepHours <= 0 && userAnalysis != null) {
                    JSONObject profile = userAnalysis.optJSONObject("profile");
                    JSONObject lifestyleData = profile != null ? profile.optJSONObject("lifestyle") : null;
                    if (lifestyleData != null) sleepHours = lifestyleData.optDouble("sleepHours", 0);
                }
                if (sleepHours > 0) {
                    pillSleep.setText(sleepHours + "h");
                    pillSleep.setTextColor(Color.parseColor("#90CAF9"));
                } else {
                    pillSleep.setText("Not set");
                    pillSleep.setTextColor(Color.parseColor("#808080"));
                    pillSleep.setTextSize(13);
                }

                // Show "Complete your profile" CTA if any key pills show "Not set"
                if (completeProfileCta != null) {
                    boolean hasIncomplete = "Not set".equals(pillAge.getText().toString())
                            || "Not set".equals(pillGender.getText().toString())
                            || "Not set".equals(pillBlood.getText().toString())
                            || "Not set".equals(pillWeight.getText().toString());
                    completeProfileCta.setVisibility(hasIncomplete ? View.VISIBLE : View.GONE);
                }

                // Profile Score from server-computed healthAnalysisStatus
                JSONObject healthStatusForScore = lastHealthAnalysisJson.optJSONObject("healthAnalysisStatus");
                if (healthStatusForScore != null) {
                    int score = healthStatusForScore.optInt("score", -1);
                    if (score >= 0) {
                        heroHealthScoreSection.setVisibility(View.VISIBLE);
                        heroHealthScoreValue.setText(String.valueOf(score));
                        heroHealthScoreProgress.setProgress(score);
                        int scoreColor;
                        if (score >= 80) scoreColor = Color.parseColor("#4CAF50");
                        else if (score >= 60) scoreColor = Color.parseColor("#008b8b");
                        else if (score >= 40) scoreColor = Color.parseColor("#FFC107");
                        else scoreColor = Color.parseColor("#F44336");
                        heroHealthScoreValue.setTextColor(scoreColor);
                        heroHealthScoreProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(scoreColor));
                    }
                }

                // Action Items from backend
                JSONArray actionItemsArr = lastHealthAnalysisJson.optJSONArray("actionItems");
                actionItemsContainer.removeAllViews();
                if (actionItemsArr != null && actionItemsArr.length() > 0) {
                    actionItemsCard.setVisibility(View.VISIBLE);
                    int maxItems = Math.min(actionItemsArr.length(), 5);
                    for (int ai = 0; ai < maxItems; ai++) {
                        JSONObject item = actionItemsArr.optJSONObject(ai);
                        if (item != null) {
                            addActionItem(actionItemsContainer,
                                item.optString("message", ""),
                                item.optString("priority", "MEDIUM"),
                                item.optString("category", ""));
                        }
                    }
                } else {
                    // Distinguish "never generated" from "all good"
                    boolean hasBeenGenerated = !neverGenerated(lastHealthAnalysisJson)
                            && cachedTypeAnalyses != null && cachedTypeAnalyses.length() > 0;
                    if (hasBeenGenerated) {
                        // Truly all good — analysis ran and found no action items
                        actionItemsCard.setVisibility(View.VISIBLE);
                        TextView allGood = new TextView(HealthAnalysisActivity.this);
                        allGood.setText("All health data categories are covered. Keep your profile updated for the best insights.");
                        allGood.setTextColor(Color.parseColor("#4CAF50"));
                        allGood.setTextSize(13);
                        allGood.setLineSpacing(1.3f, 1);
                        actionItemsContainer.addView(allGood);
                    } else {
                        // Never generated — hide the card entirely, don't mislead
                        actionItemsCard.setVisibility(View.GONE);
                    }
                }

                // Data on File summary (compact text instead of 4 large cells)
                int medCount = dataPoints != null ? dataPoints.optInt("medications", 0) : 0;
                int reportCount = dataPoints != null ? dataPoints.optInt("reports", 0) : 0;
                int familyCount = dataPoints != null ? dataPoints.optInt("familyMembers", 0) : 0;
                int symptomCount = dataPoints != null ? dataPoints.optInt("symptoms", 0) : 0;
                int measurementCount = dataPoints != null ? dataPoints.optInt("measurements", 0) : 0;

                int conditionCount = 0;
                int allergyCount = 0;
                if (userAnalysis != null) {
                    JSONObject medData = userAnalysis.optJSONObject("medicalData");
                    if (medData != null) {
                        conditionCount = medData.optInt("conditionCount", 0);
                        allergyCount = medData.optInt("allergyCount", 0);
                    }
                }

                List<String> dataParts = new ArrayList<>();
                int totalConditions = conditionCount + allergyCount;
                if (totalConditions > 0) dataParts.add(totalConditions + " Condition" + (totalConditions > 1 ? "s" : ""));
                if (medCount > 0) dataParts.add(medCount + " Medication" + (medCount > 1 ? "s" : ""));
                if (reportCount > 0) dataParts.add(reportCount + " Report" + (reportCount > 1 ? "s" : ""));
                if (symptomCount > 0) dataParts.add(symptomCount + " Symptom" + (symptomCount > 1 ? "s" : ""));
                if (measurementCount > 0) dataParts.add(measurementCount + " Measurement" + (measurementCount > 1 ? "s" : ""));
                if (familyCount > 0) dataParts.add(familyCount + " Family Member" + (familyCount > 1 ? "s" : ""));
                if (allergyCount > 0 && conditionCount > 0) {
                    // Already included in totalConditions above
                } else if (allergyCount > 0 && conditionCount == 0) {
                    dataParts.add(allergyCount + " Allerg" + (allergyCount > 1 ? "ies" : "y"));
                }

                if (dataParts.isEmpty()) {
                    dataOnFileSummary.setText("No health records on file yet. Add data for better insights.");
                    dataOnFileSummary.setTextColor(Color.parseColor("#808080"));
                } else {
                    StringBuilder sb = new StringBuilder();
                    for (int dp = 0; dp < dataParts.size(); dp++) {
                        if (dp > 0) sb.append("  ·  ");
                        sb.append(dataParts.get(dp));
                    }
                    dataOnFileSummary.setText(sb.toString());
                    dataOnFileSummary.setTextColor(Color.parseColor("#AAAAAA"));
                }

                // Populate the data overview pie chart
                populateDataOverviewChart(dataPoints, totalConditions);

                // Profile completion (single source: backend profileCompletion)
                JSONObject profileComp = lastHealthAnalysisJson.optJSONObject("profileCompletion");
                if (profileComp != null) {
                    int pct = profileComp.optInt("percent", -1);
                    if (pct >= 0 && pct < 100) {
                        profileSection.setVisibility(View.VISIBLE);
                        profilePercentText.setText(pct + "% complete");
                        profileProgress.setProgress(pct);

                        JSONArray missingArr = profileComp.optJSONArray("missing");
                        if (missingArr != null && missingArr.length() > 0) {
                            StringBuilder missingStr = new StringBuilder("Missing: ");
                            for (int mi = 0; mi < missingArr.length(); mi++) {
                                if (mi > 0) missingStr.append(", ");
                                missingStr.append(missingArr.optString(mi, ""));
                            }
                            missingFieldsText.setVisibility(View.VISIBLE);
                            missingFieldsText.setText(missingStr.toString());
                        } else {
                            missingFieldsText.setVisibility(View.GONE);
                        }
                    } else {
                        profileSection.setVisibility(View.GONE);
                    }
                } else {
                    profileSection.setVisibility(View.GONE);
                }

            } catch (Exception e) {
                Log.e(TAG, "Error populating health analysis", e);
                dialogHeadline.setText("Unable to load analysis");
                dialogReason.setText("Please try again later");
                statusLine.setText("● Data unavailable");
                statusLine.setTextColor(Color.parseColor("#808080"));
            }
        } else {
            showFallbackState();
        }

        // AQI from SharedPreferences cache
        populateAqi();

        // Gate refresh for free users who used their monthly analysis
        gateRefreshForFreeUser();
    }

    private void showFallbackState() {
        dialogHeadline.setText("Tap refresh to generate your analysis");
        dialogLastUpdated.setText("Not yet generated");

        // Reset stat cells to placeholder (keep visible)
        pillAge.setText("–");
        pillAge.setTextColor(Color.parseColor("#808080"));
        pillGender.setText("–");
        pillGender.setTextColor(Color.parseColor("#808080"));
        pillBlood.setText("–");
        pillBlood.setTextColor(Color.parseColor("#808080"));
        pillWeight.setText("–");
        pillWeight.setTextColor(Color.parseColor("#808080"));
        pillBmi.setText("–");
        pillBmi.setTextColor(Color.parseColor("#808080"));
        pillBmiLabel.setText("BMI");
        pillSleep.setText("–");
        pillSleep.setTextColor(Color.parseColor("#808080"));

        // Reset data on file summary
        if (dataOnFileSummary != null) {
            dataOnFileSummary.setText("–");
            dataOnFileSummary.setTextColor(Color.parseColor("#808080"));
        }

        // Reset tab labels
        String[] tabNames = {"Reports", "Symptoms", "Medications", "Measurements", "Family Health", "Diagnostics"};
        for (int i = 0; i < allTabs.length; i++) {
            allTabs[i].setText(tabNames[i]);
        }

        profileSection.setVisibility(View.GONE);
        heroHealthScoreSection.setVisibility(View.GONE);
        actionItemsCard.setVisibility(View.GONE);
        if (completeProfileCta != null) completeProfileCta.setVisibility(View.GONE);

        // Reset pie chart to empty state
        populateDataOverviewChart(null, 0);
    }

    private void populateAqi() {
        try {
            SharedPreferences aqiPrefs = getSharedPreferences("aqi_prefs", Context.MODE_PRIVATE);
            int cachedAqi = aqiPrefs.getInt("cached_aqi", 0);
            String cachedCity = aqiPrefs.getString("cached_city", "");
            int aqiRecordCount = aqiPrefs.getInt("cached_aqi_record_count", 0);
            String cachedAqiAnalysis = aqiPrefs.getString("cached_aqi_analysis", "");

            if (cachedAqi > 0) {
                aqiCard.setVisibility(View.VISIBLE);
                if (aqiTapDetails != null) aqiTapDetails.setVisibility(View.VISIBLE);
                aqiValue.setText("AQI " + cachedAqi);
                String quality = getAqiQualityLabel(cachedAqi);
                aqiQuality.setText(quality);
                aqiQuality.setTextColor(getAqiColor(cachedAqi));
                if (cachedCity != null && !cachedCity.isEmpty()) {
                    aqiLocation.setText(cachedCity);
                } else {
                    aqiLocation.setText("Air Quality");
                }
                if (aqiRecordCount > 0) {
                    aqiRecords.setText(aqiRecordCount + " record" + (aqiRecordCount > 1 ? "s" : ""));
                    aqiRecords.setVisibility(View.VISIBLE);
                }
                if (cachedAqiAnalysis.isEmpty()) {
                    if (cachedAqi <= 50) cachedAqiAnalysis = "Air quality is good. Safe for outdoor activities.";
                    else if (cachedAqi <= 100) cachedAqiAnalysis = "Moderate air quality. Sensitive groups should limit outdoor exertion.";
                    else if (cachedAqi <= 150) cachedAqiAnalysis = "Unhealthy for sensitive groups. Consider reducing outdoor activities.";
                    else cachedAqiAnalysis = "Unhealthy air quality. Avoid prolonged outdoor activities.";
                }
                if (!cachedAqiAnalysis.isEmpty()) {
                    aqiAnalysis.setText(cachedAqiAnalysis);
                    aqiAnalysis.setVisibility(View.VISIBLE);
                }
            } else {
                // No locally cached AQI — try server-side value from health analysis metrics as fallback
                int serverAqi = 0;
                String serverLocation = "";
                if (lastHealthAnalysisJson != null) {
                    JSONObject metrics = lastHealthAnalysisJson.optJSONObject("metrics");
                    if (metrics != null) {
                        serverAqi = metrics.optInt("aqi", 0);
                        serverLocation = metrics.optString("location", "");
                    }
                }
                aqiCard.setVisibility(View.VISIBLE);
                if (aqiTapDetails != null) aqiTapDetails.setVisibility(View.GONE);
                aqiRecords.setVisibility(View.GONE);
                if (serverAqi > 0) {
                    aqiValue.setText("AQI " + serverAqi);
                    String quality = getAqiQualityLabel(serverAqi);
                    aqiQuality.setText(quality);
                    aqiQuality.setTextColor(getAqiColor(serverAqi));
                    aqiLocation.setText(!serverLocation.isEmpty() ? serverLocation : "Air Quality");
                    aqiAnalysis.setText("From last analysis · Enable location on Home for live readings");
                    aqiAnalysis.setVisibility(View.VISIBLE);
                } else {
                    aqiValue.setText("–");
                    aqiQuality.setText("Air Quality");
                    aqiQuality.setTextColor(Color.parseColor("#808080"));
                    aqiLocation.setText("Not available");
                    aqiAnalysis.setText("Enable location on the Home tab to monitor air quality in your area");
                    aqiAnalysis.setVisibility(View.VISIBLE);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading AQI cache", e);
            aqiCard.setVisibility(View.VISIBLE);
            if (aqiTapDetails != null) aqiTapDetails.setVisibility(View.GONE);
            aqiValue.setText("–");
            aqiQuality.setText("Air Quality");
            aqiQuality.setTextColor(Color.parseColor("#808080"));
            aqiLocation.setText("Not available");
            aqiRecords.setVisibility(View.GONE);
            aqiAnalysis.setText("Enable location on the Home tab to monitor air quality");
            aqiAnalysis.setVisibility(View.VISIBLE);
        }
    }

    // ═══════ PER-TYPE ANALYSIS ═══════
    private void displayAnalysisTabContent(String tabKey) {
        analysisSummary.setVisibility(View.GONE);
        analysisDetailsContainer.setVisibility(View.GONE);
        analysisDetailsContainer.removeAllViews();
        analysisNoData.setVisibility(View.GONE);
        analysisGeneratedAt.setVisibility(View.GONE);
        stopShimmerAnimation(analysisShimmer);

        // Show per-tab staleness notice when data in this category changed since last analysis
        if (lastHealthAnalysisJson != null && lastHealthAnalysisJson.optBoolean("healthDataNeedsUpdate", false)) {
            JSONObject changes = lastHealthAnalysisJson.optJSONObject("dataChangesSinceAnalysis");
            if (changes != null) {
                // Map tab keys to change counter keys
                String changeKey = null;
                switch (tabKey) {
                    case "reports": changeKey = "reports"; break;
                    case "symptoms": changeKey = "symptoms"; break;
                    case "medications": changeKey = "medications"; break;
                    case "measurements": changeKey = "measurements"; break;
                    case "genetics": changeKey = "genetics"; break;
                }
                int changeCount = changeKey != null ? changes.optInt(changeKey, 0) : 0;
                if (changeCount > 0) {
                    addStaleDataNotice(analysisDetailsContainer, tabKey, changeCount);
                    analysisDetailsContainer.setVisibility(View.VISIBLE);
                }
            }
        }

        if (cachedTypeAnalyses == null || !cachedTypeAnalyses.has(tabKey)) {
            switch (tabKey) {
                case "reports":
                    analysisNoData.setText("Upload medical reports to get AI-powered analysis");
                    break;
                case "symptoms":
                    analysisNoData.setText("Log your symptoms to track patterns over time");
                    break;
                case "medications":
                    analysisNoData.setText("Add medications to check for interactions and adherence");
                    break;
                case "measurements":
                    analysisNoData.setText("Record health measurements to monitor trends");
                    break;
                case "genetics":
                    analysisNoData.setText("Link family members to unlock genetic risk insights");
                    break;
                case "diagnostics":
                    analysisNoData.setText("Tap Refresh Analysis to generate diagnostic recommendations");
                    break;
                default:
                    analysisNoData.setText("Tap Refresh Analysis to generate insights");
                    break;
            }
            analysisNoData.setVisibility(View.VISIBLE);
            return;
        }

        try {
            JSONObject typeData = cachedTypeAnalyses.getJSONObject(tabKey);
            String textStr = typeData.optString("text", "{}");
            String generatedAt = typeData.optString("generatedAt", "");

            JSONObject parsed = new JSONObject(textStr);

            if (parsed.optBoolean("noData", false)) {
                analysisNoData.setText(parsed.optString("message", "No data available for analysis"));
                analysisNoData.setVisibility(View.VISIBLE);
            } else if (parsed.has("error")) {
                analysisNoData.setText(parsed.optString("error", "Analysis temporarily unavailable"));
                analysisNoData.setVisibility(View.VISIBLE);
            } else {
                String summary = parsed.optString("summary", "");
                if (!summary.isEmpty()) {
                    analysisSummary.setText(summary);
                    analysisSummary.setVisibility(View.VISIBLE);
                }

                analysisDetailsContainer.setVisibility(View.VISIBLE);

                String[][] sectionKeys = {
                    {"patterns", "Patterns"},
                    {"concerns", "Concerns"},
                    {"interactions", "Interactions"},
                    {"trends", "Trends"},
                    {"outOfRange", "Out of Range"},
                    {"keyFindings", "Key Findings"},
                    {"recommendations", "Recommendations"}
                };

                for (String[] section : sectionKeys) {
                    JSONArray arr = parsed.optJSONArray(section[0]);
                    if (arr != null && arr.length() > 0) {
                        addAnalysisSectionHeader(analysisDetailsContainer, section[1]);
                        for (int i = 0; i < arr.length(); i++) {
                            addAnalysisBullet(analysisDetailsContainer, arr.optString(i, ""));
                        }
                    }
                }

                String adherence = parsed.optString("adherenceInsights", "");
                if (!adherence.isEmpty()) {
                    addAnalysisSectionHeader(analysisDetailsContainer, "Adherence");
                    addAnalysisBullet(analysisDetailsContainer, adherence);
                }

                // Diagnostics tab: render recommended[] as structured cards
                if ("diagnostics".equals(tabKey)) {
                    JSONArray recommended = parsed.optJSONArray("recommended");
                    if (recommended != null && recommended.length() > 0) {
                        addAnalysisSectionHeader(analysisDetailsContainer, "Recommended Tests");
                        for (int i = 0; i < recommended.length(); i++) {
                            JSONObject item = recommended.optJSONObject(i);
                            if (item == null) continue;
                            String testName = item.optString("test", "");
                            String type = item.optString("type", "");
                            String priority = item.optString("priority", "routine");
                            String reason = item.optString("reason", "");
                            String frequency = item.optString("frequency", "");
                            if (!testName.isEmpty()) {
                                String priorityColor = "urgent".equals(priority) ? "#F44336"
                                        : "recommended".equals(priority) ? "#FF9800" : "#4CAF50";
                                addDiagnosticItem(analysisDetailsContainer, testName, type, priority, priorityColor, reason, frequency);
                            }
                        }
                    }
                    String disclaimer = parsed.optString("disclaimer", "");
                    if (!disclaimer.isEmpty()) {
                        addAnalysisSectionHeader(analysisDetailsContainer, "Disclaimer");
                        addAnalysisBullet(analysisDetailsContainer, disclaimer);
                    }
                }
            }

            if (!generatedAt.isEmpty()) {
                boolean isStale = lastHealthAnalysisJson != null
                        && lastHealthAnalysisJson.optBoolean("healthDataNeedsUpdate", false);
                String dateStr = formatAbsoluteDate(generatedAt);
                String relativeStr = formatTimeAgo(generatedAt);
                String displayText;
                if (!dateStr.isEmpty()) {
                    displayText = dateStr + " · " + relativeStr;
                } else {
                    displayText = "Generated " + relativeStr;
                }
                if (isStale) {
                    analysisGeneratedAt.setText(displayText + " · outdated");
                    analysisGeneratedAt.setTextColor(Color.parseColor("#FFC107"));
                } else {
                    analysisGeneratedAt.setText(displayText);
                    analysisGeneratedAt.setTextColor(Color.parseColor("#666666"));
                }
                analysisGeneratedAt.setVisibility(View.VISIBLE);
            }

        } catch (JSONException e) {
            Log.e(TAG, "Error displaying analysis for tab: " + tabKey, e);
            analysisNoData.setText("Error displaying analysis");
            analysisNoData.setVisibility(View.VISIBLE);
        }
    }

    private void addAnalysisSectionHeader(LinearLayout container, String title) {
        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextColor(Color.parseColor("#008b8b"));
        tv.setTextSize(12);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        int dp8 = (int) (8 * getResources().getDisplayMetrics().density);
        tv.setPadding(0, dp8 * 2, 0, dp8 / 2);
        container.addView(tv);
    }

    private void addAnalysisBullet(LinearLayout container, String text) {
        if (text == null || text.isEmpty()) return;
        TextView tv = new TextView(this);
        tv.setText("• " + text);
        tv.setTextColor(Color.parseColor("#CCCCCC"));
        tv.setTextSize(12);
        tv.setLineSpacing(1.3f, 1);
        int dp4 = (int) (4 * getResources().getDisplayMetrics().density);
        tv.setPadding(dp4 * 2, dp4 / 2, 0, dp4 / 2);
        container.addView(tv);
    }

    private void addStaleDataNotice(LinearLayout container, String tabKey, int changeCount) {
        float density = getResources().getDisplayMetrics().density;
        int dp8 = (int) (8 * density);
        int dp12 = (int) (12 * density);

        LinearLayout notice = new LinearLayout(this);
        notice.setOrientation(LinearLayout.HORIZONTAL);
        notice.setGravity(android.view.Gravity.CENTER_VERTICAL);
        notice.setBackgroundColor(Color.parseColor("#1A1A0A")); // subtle dark amber tint
        notice.setPadding(dp12, dp8, dp12, dp8);
        LinearLayout.LayoutParams noticeLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        noticeLp.setMargins(0, 0, 0, dp8);
        notice.setLayoutParams(noticeLp);

        TextView icon = new TextView(this);
        icon.setText("↻");
        icon.setTextColor(Color.parseColor("#FFC107"));
        icon.setTextSize(14);
        icon.setPadding(0, 0, dp8, 0);
        notice.addView(icon);

        // Build human-readable category name
        String category;
        switch (tabKey) {
            case "reports": category = "report"; break;
            case "symptoms": category = "symptom"; break;
            case "medications": category = "medication"; break;
            case "measurements": category = "measurement"; break;
            case "genetics": category = "family health update"; break;
            default: category = "data point"; break;
        }
        if (changeCount > 1 && !category.endsWith("s")) {
            category += "s";
        }

        TextView text = new TextView(this);
        text.setText(changeCount + " new " + category + " added since this analysis. Tap Refresh to update.");
        text.setTextColor(Color.parseColor("#FFC107"));
        text.setTextSize(11);
        text.setLineSpacing(1.2f, 1);
        notice.addView(text);

        container.addView(notice);
    }

    private void addDiagnosticItem(LinearLayout container, String testName, String type,
                                    String priority, String priorityColor, String reason, String frequency) {
        float density = getResources().getDisplayMetrics().density;
        int dp6 = (int) (6 * density);
        int dp8 = (int) (8 * density);
        int dp12 = (int) (12 * density);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(Color.parseColor("#1E1E1E"));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(0, dp6, 0, dp6);
        card.setLayoutParams(cardLp);
        card.setPadding(dp12, dp8, dp12, dp8);

        // Header row: test name + priority badge
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView nameTv = new TextView(this);
        nameTv.setText(testName);
        nameTv.setTextColor(Color.WHITE);
        nameTv.setTextSize(13);
        nameTv.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        nameTv.setLayoutParams(nameLp);
        headerRow.addView(nameTv);

        TextView priorityBadge = new TextView(this);
        priorityBadge.setText(priority.substring(0, 1).toUpperCase() + priority.substring(1));
        priorityBadge.setTextColor(Color.parseColor(priorityColor));
        priorityBadge.setTextSize(10);
        priorityBadge.setTypeface(null, android.graphics.Typeface.BOLD);
        headerRow.addView(priorityBadge);
        card.addView(headerRow);

        // Type label
        if (!type.isEmpty()) {
            TextView typeTv = new TextView(this);
            typeTv.setText(type.replace("_", " ").toUpperCase());
            typeTv.setTextColor(Color.parseColor("#808080"));
            typeTv.setTextSize(10);
            typeTv.setPadding(0, dp6 / 2, 0, dp6 / 2);
            card.addView(typeTv);
        }

        // Reason
        if (!reason.isEmpty()) {
            TextView reasonTv = new TextView(this);
            reasonTv.setText(reason);
            reasonTv.setTextColor(Color.parseColor("#CCCCCC"));
            reasonTv.setTextSize(12);
            reasonTv.setLineSpacing(1.3f, 1);
            reasonTv.setPadding(0, dp6 / 2, 0, 0);
            card.addView(reasonTv);
        }

        // Frequency
        if (!frequency.isEmpty()) {
            TextView freqTv = new TextView(this);
            freqTv.setText("Frequency: " + frequency);
            freqTv.setTextColor(Color.parseColor("#606060"));
            freqTv.setTextSize(11);
            freqTv.setPadding(0, dp6 / 2, 0, 0);
            card.addView(freqTv);
        }

        container.addView(card);
    }

    private void addActionItem(LinearLayout container, String description, String priority, String category) {
        if (description == null || description.isEmpty()) return;

        float density = getResources().getDisplayMetrics().density;
        int dp6 = (int) (6 * density);
        int dp8 = (int) (8 * density);

        LinearLayout itemLayout = new LinearLayout(this);
        itemLayout.setOrientation(LinearLayout.HORIZONTAL);
        itemLayout.setGravity(android.view.Gravity.TOP);
        itemLayout.setPadding(0, dp6, 0, dp6);

        // Priority dot
        TextView dot = new TextView(this);
        dot.setText("●");
        dot.setTextSize(8);
        int dotColor;
        switch (priority) {
            case "HIGH": dotColor = Color.parseColor("#F44336"); break;
            case "MEDIUM": dotColor = Color.parseColor("#FF9800"); break;
            default: dotColor = Color.parseColor("#4CAF50"); break;
        }
        dot.setTextColor(dotColor);
        dot.setPadding(0, (int)(5 * density), dp8, 0);
        itemLayout.addView(dot);

        // Description
        TextView desc = new TextView(this);
        desc.setText(description);
        desc.setTextColor(Color.parseColor("#E0E0E0"));
        desc.setTextSize(13);
        desc.setLineSpacing(1.3f, 1);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        desc.setLayoutParams(lp);
        itemLayout.addView(desc);

        container.addView(itemLayout);
    }

    // ═══════ SHIMMER ═══════
    private void startShimmerAnimation(View shimmerView) {
        shimmerView.setVisibility(View.VISIBLE);
        shimmerView.setAlpha(1f);
        ObjectAnimator anim = ObjectAnimator.ofFloat(shimmerView, "alpha", 1f, 0.3f);
        anim.setDuration(800);
        anim.setRepeatCount(ObjectAnimator.INFINITE);
        anim.setRepeatMode(ObjectAnimator.REVERSE);
        anim.setInterpolator(new AccelerateDecelerateInterpolator());
        anim.start();
        shimmerView.setTag(anim);
    }

    private void stopShimmerAnimation(View shimmerView) {
        Object tag = shimmerView.getTag();
        if (tag instanceof ObjectAnimator) {
            ((ObjectAnimator) tag).cancel();
        }
        shimmerView.setAlpha(1f);
        shimmerView.setVisibility(View.GONE);
    }

    // ═══════ REFRESH ALL ANALYSES ═══════
    private void refreshAllAnalyses() {
        String token = tokenManager.getToken();
        if (token == null) return;

        SimpleProgress.show(this, "Generating health analysis...");

        String url = ApiConfig.BASE_URL + "/api/health/analysis/generate";

        startShimmerAnimation(analysisShimmer);
        analysisSummary.setVisibility(View.GONE);
        analysisDetailsContainer.setVisibility(View.GONE);
        analysisNoData.setVisibility(View.GONE);
        analysisGeneratedAt.setVisibility(View.GONE);

        StringRequest request = new StringRequest(Request.Method.POST, url,
            response -> {
                try {
                    JSONObject jsonResponse = new JSONObject(response);
                    if (jsonResponse.optBoolean("success", false)) {
                        cachedTypeAnalyses = jsonResponse.optJSONObject("analyses");
                        displayAnalysisTabContent(currentAnalysisTab);
                        dialogLastUpdated.setText("Generated just now");

                        if (cachedTypeAnalyses != null && cachedTypeAnalyses.has("overall")) {
                            try {
                                JSONObject overall = cachedTypeAnalyses.getJSONObject("overall");
                                String overallText = overall.optString("text", "{}");
                                JSONObject overallParsed = new JSONObject(overallText);
                                String newHeadline = overallParsed.optString("headline", "");
                                if (!newHeadline.isEmpty()) {
                                    dialogHeadline.setText(newHeadline);
                                }
                            } catch (JSONException ignored) {}
                        }

                        // Re-fetch to refresh headline/score/metrics — but don't overwrite fresh tab data
                        skipTabRender = true;
                        loadHealthAnalysisData(false);
                    } else {
                        analysisNoData.setText("Analysis generation failed. Try again.");
                        analysisNoData.setVisibility(View.VISIBLE);
                        stopShimmerAnimation(analysisShimmer);
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing generate response", e);
                    analysisNoData.setText("Error processing response");
                    analysisNoData.setVisibility(View.VISIBLE);
                    stopShimmerAnimation(analysisShimmer);
                }
                SimpleProgress.hide();
                refreshButton.setVisibility(View.VISIBLE);
                refreshProgress.setVisibility(View.GONE);
                if (bottomRefreshButton != null) {
                    bottomRefreshButton.setEnabled(true);
                    bottomRefreshButton.setText("Refresh Analysis");
                }
            },
            error -> {
                SimpleProgress.hide();
                Log.e(TAG, "Error generating analysis", error);
                stopShimmerAnimation(analysisShimmer);
                refreshButton.setVisibility(View.VISIBLE);
                refreshProgress.setVisibility(View.GONE);

                // Check for 429 (limit reached)
                NetworkResponse networkResponse = error.networkResponse;
                if (networkResponse != null && networkResponse.statusCode == 429) {
                    String serverMsg = "Health analysis limit reached for this period.";
                    try {
                        String body = new String(networkResponse.data, "UTF-8");
                        JSONObject errJson = new JSONObject(body);
                        String msg = errJson.optString("message", "");
                        if (!msg.isEmpty()) serverMsg = msg;
                    } catch (Exception ignored) {}

                    analysisNoData.setText("Monthly analysis limit reached");
                    analysisNoData.setVisibility(View.VISIBLE);
                    dialogLastUpdated.setText("Limit reached");

                    // Disable bottom refresh for free users
                    if (bottomRefreshButton != null) {
                        bottomRefreshButton.setEnabled(false);
                        bottomRefreshButton.setText("Limit Reached");
                        bottomRefreshButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#2A2A2A")));
                        bottomRefreshButton.setTextColor(Color.parseColor("#808080"));
                    }

                    showLimitReachedDialog(serverMsg);
                } else {
                    analysisNoData.setText("Failed to generate. Check connection.");
                    analysisNoData.setVisibility(View.VISIBLE);
                    dialogLastUpdated.setText("Generation failed");
                    if (bottomRefreshButton != null) {
                        bottomRefreshButton.setEnabled(true);
                        bottomRefreshButton.setText("Refresh Analysis");
                    }
                }
            }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + token);
                headers.put("Content-Type", "application/json");
                return headers;
            }
        };

        request.setRetryPolicy(new DefaultRetryPolicy(120000, 0, 1f));
        Volley.newRequestQueue(this).add(request);
    }

    // ═══════ DATA OVERVIEW CHART ═══════

    private void setupDataOverviewChart() {
        dataOverviewChart = findViewById(R.id.data_overview_pie_chart);
        if (dataOverviewChart == null) return;

        dataOverviewChart.setUsePercentValues(false);
        dataOverviewChart.getDescription().setEnabled(false);
        dataOverviewChart.setDrawHoleEnabled(true);
        dataOverviewChart.setHoleColor(Color.TRANSPARENT);
        dataOverviewChart.setHoleRadius(58f);
        dataOverviewChart.setTransparentCircleRadius(58f); // same as hole = no 3D halo
        dataOverviewChart.setTransparentCircleColor(Color.TRANSPARENT);
        dataOverviewChart.setDrawSlicesUnderHole(false);
        dataOverviewChart.setDrawCenterText(true);
        dataOverviewChart.setCenterTextColor(Color.WHITE);
        dataOverviewChart.setCenterTextSize(14f);
        dataOverviewChart.setRotationEnabled(false);
        dataOverviewChart.setHighlightPerTapEnabled(false);
        dataOverviewChart.setDrawEntryLabels(false); // no labels on slices
        dataOverviewChart.setBackgroundColor(Color.TRANSPARENT);
        dataOverviewChart.setExtraOffsets(8f, 4f, 8f, 4f);

        // Legend on the right side
        Legend legend = dataOverviewChart.getLegend();
        legend.setEnabled(true);
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.CENTER);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.RIGHT);
        legend.setOrientation(Legend.LegendOrientation.VERTICAL);
        legend.setDrawInside(false);
        legend.setTextColor(Color.WHITE);
        legend.setTextSize(12f);
        legend.setFormSize(10f);
        legend.setForm(Legend.LegendForm.CIRCLE);
        legend.setXEntrySpace(8f);
        legend.setYEntrySpace(6f);
        legend.setXOffset(12f);

        // Empty state
        dataOverviewChart.setCenterText("Loading...");
        dataOverviewChart.invalidate();
    }

    private void populateDataOverviewChart(JSONObject dataPoints, int conditionCount) {
        if (dataOverviewChart == null) return;

        List<PieEntry> entries = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();

        int reports = dataPoints != null ? dataPoints.optInt("reports", 0) : 0;
        int symptoms = dataPoints != null ? dataPoints.optInt("symptoms", 0) : 0;
        int medications = dataPoints != null ? dataPoints.optInt("medications", 0) : 0;
        int measurements = dataPoints != null ? dataPoints.optInt("measurements", 0) : 0;
        int family = dataPoints != null ? dataPoints.optInt("familyMembers", 0) : 0;

        // Distinct teal shades for each data type
        if (reports > 0) { entries.add(new PieEntry(reports, "Reports (" + reports + ")")); colors.add(Color.parseColor("#004D40")); }
        if (symptoms > 0) { entries.add(new PieEntry(symptoms, "Symptoms (" + symptoms + ")")); colors.add(Color.parseColor("#00BFA5")); }
        if (medications > 0) { entries.add(new PieEntry(medications, "Meds (" + medications + ")")); colors.add(Color.parseColor("#008B8B")); }
        if (measurements > 0) { entries.add(new PieEntry(measurements, "Vitals (" + measurements + ")")); colors.add(Color.parseColor("#26A69A")); }
        if (conditionCount > 0) { entries.add(new PieEntry(conditionCount, "Conditions (" + conditionCount + ")")); colors.add(Color.parseColor("#80CBC4")); }
        if (family > 0) { entries.add(new PieEntry(family, "Family (" + family + ")")); colors.add(Color.parseColor("#B2DFDB")); }

        int total = reports + symptoms + medications + measurements + conditionCount + family;

        if (entries.isEmpty()) {
            // Show placeholder with single gray slice
            entries.add(new PieEntry(1, "No data yet"));
            colors.add(Color.parseColor("#455a64"));
            dataOverviewChart.setCenterText("Add\nhealth data");
            dataOverviewChart.getLegend().setEnabled(false);
        } else {
            dataOverviewChart.setCenterText(String.valueOf(total));
            dataOverviewChart.getLegend().setEnabled(true);
        }

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(colors);
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setValueTextSize(13f);
        dataSet.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.valueOf((int) value);
            }
        });
        dataSet.setSliceSpace(2f);
        dataSet.setSelectionShift(0f); // flat 2D, no selection pop-out

        PieData data = new PieData(dataSet);
        dataOverviewChart.setData(data);
        dataOverviewChart.animateY(600);
        dataOverviewChart.invalidate();
    }

    // ═══════ AQI DIALOG ═══════

    private void fetchAndShowAQIHistory() {
        SimpleProgress.show(this, "Loading AQI history...");

        final int[] completedCalls = {0};
        final java.util.List<Models.AQIData>[] historyResult = new java.util.List[]{null};
        final int[] analysisResult = {-1, -1, -1}; // averageAQI, maxAQI, highExposureDays

        aqiApiService.getUserAQIHistory(30, new Api.AQIAPIService.OnAQIHistoryListener() {
            @Override
            public void onSuccess(java.util.List<Models.AQIData> aqiHistory) {
                historyResult[0] = aqiHistory;
                completedCalls[0]++;
                if (completedCalls[0] >= 2) {
                    showAQIDialogWithResults(historyResult[0], analysisResult);
                }
            }

            @Override
            public void onError(String errorMessage) {
                // If API fails, try to show cached data from local DB
                DatabaseHelper dbHelper = new DatabaseHelper(HealthAnalysisActivity.this);
                UserProfile userProfile = dbHelper.getUserProfile();
                if (userProfile != null) {
                    historyResult[0] = dbHelper.getAQIHistoryForUser(userProfile.getId());
                }
                completedCalls[0]++;
                if (completedCalls[0] >= 2) {
                    showAQIDialogWithResults(historyResult[0], analysisResult);
                }
            }
        });

        aqiApiService.getUserAQIAnalysis(new Api.AQIAPIService.OnAQIAnalysisListener() {
            @Override
            public void onSuccess(int averageAQI, int maxAQI, int highExposureDays) {
                analysisResult[0] = averageAQI;
                analysisResult[1] = maxAQI;
                analysisResult[2] = highExposureDays;
                completedCalls[0]++;
                if (completedCalls[0] >= 2) {
                    showAQIDialogWithResults(historyResult[0], analysisResult);
                }
            }

            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "Error fetching AQI analysis: " + errorMessage);
                completedCalls[0]++;
                if (completedCalls[0] >= 2) {
                    showAQIDialogWithResults(historyResult[0], analysisResult);
                }
            }
        });
    }

    private void showAQIDialogWithResults(java.util.List<Models.AQIData> aqiHistory, int[] analysisResult) {
        SimpleProgress.hide();
        if (aqiHistory == null || aqiHistory.isEmpty()) {
            android.widget.Toast.makeText(this, "No AQI history data available", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        if (analysisResult[2] >= 0) {
            Utils.DialogUtils.showAQIChartDialog(this, aqiHistory, analysisResult[2]);
        } else {
            Utils.DialogUtils.showAQIChartDialog(this, aqiHistory);
        }
    }

    // ═══════ HELPERS ═══════

    private boolean neverGenerated(JSONObject analysis) {
        String lastUpdated = analysis.optString("lastUpdated", null);
        return (lastUpdated == null || lastUpdated.isEmpty());
    }

    private JSONObject getCachedUserAnalysis() {
        try {
            SharedPreferences prefs = getSharedPreferences("user_analysis_cache", Context.MODE_PRIVATE);
            String cachedData = prefs.getString("analysis_data", null);
            long cacheTime = prefs.getLong("cache_time", 0);
            if (cachedData == null) return null;
            // Reject stale cache (>24h) so profile pills don't show old data
            long age = System.currentTimeMillis() - cacheTime;
            if (cacheTime > 0 && age > 24 * 60 * 60 * 1000L) {
                Log.d(TAG, "user_analysis_cache is stale (" + (age / 3600000) + "h old), skipping");
                return null;
            }
            JSONObject response = new JSONObject(cachedData);
            return response.optJSONObject("analysis");
        } catch (Exception e) {
            Log.e(TAG, "Error reading cached user analysis", e);
        }
        return null;
    }

    private boolean isAnalysisUsedThisMonth() {
        if (lastHealthAnalysisJson == null) return false;
        String lastUpdated = lastHealthAnalysisJson.optString("lastUpdated", null);
        if (lastUpdated == null || lastUpdated.isEmpty()) return false;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            Date date = sdf.parse(lastUpdated);
            if (date == null) return false;
            Calendar now = Calendar.getInstance();
            Calendar analysisDate = Calendar.getInstance();
            analysisDate.setTime(date);
            return now.get(Calendar.MONTH) == analysisDate.get(Calendar.MONTH)
                && now.get(Calendar.YEAR) == analysisDate.get(Calendar.YEAR);
        } catch (Exception e) {
            return false;
        }
    }

    private void showLimitReachedDialog(String message) {
        Utils.DialogUtils.showConfirmDialog(this,
            "Analysis Limit Reached",
            message + "\n\nUpgrade your plan to get unlimited health analyses every month.",
            "Upgrade", "OK", false,
            () -> {
                ProUpgradeDialog upgDlg = new ProUpgradeDialog(this);
                upgDlg.setLimitContext(message);
                upgDlg.show(isPro -> {
                    if (isPro) {
                        proStatusManager.syncProStatusOnLogin(HealthAnalysisActivity.this);
                    }
                });
            });
    }

    private void gateRefreshForFreeUser() {
        if (proStatusManager.isProUser()) return;
        if (!isAnalysisUsedThisMonth()) return;

        // Free user has used their 1 analysis this month — disable refresh
        if (bottomRefreshButton != null) {
            bottomRefreshButton.setEnabled(false);
            bottomRefreshButton.setText("Limit Reached (1/month)");
            bottomRefreshButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#2A2A2A")));
            bottomRefreshButton.setTextColor(Color.parseColor("#808080"));
        }
    }

    private String getStatusText(String level) {
        switch (level) {
            case "EXCELLENT": return "● Excellent health profile";
            case "NORMAL": return "● Health looks stable";
            case "NEEDS_ATTENTION": return "● Needs some attention";
            case "BAD": return "● Action recommended";
            case "CRITICAL": return "● Requires attention";
            default: return "● Health status";
        }
    }

    private int getStatusColor(String level) {
        switch (level) {
            case "CRITICAL": return Color.parseColor("#F44336");
            case "BAD": return Color.parseColor("#FF9800");
            case "NEEDS_ATTENTION": return Color.parseColor("#FFC107");
            case "NORMAL": return Color.parseColor("#4CAF50");
            case "EXCELLENT": return Color.parseColor("#2196F3");
            default: return Color.parseColor("#808080");
        }
    }

    private String getBmiCategory(double bmi) {
        if (bmi < 18.5) return "Underweight";
        if (bmi < 25) return "Normal";
        if (bmi < 30) return "Overweight";
        return "Obese";
    }

    private String getAqiQualityLabel(int aqi) {
        if (aqi <= 50) return "Good";
        if (aqi <= 100) return "Moderate";
        if (aqi <= 150) return "Unhealthy";
        return "Very Unhealthy";
    }

    private int getAqiColor(int aqi) {
        if (aqi <= 50) return Color.parseColor("#4CAF50");
        if (aqi <= 100) return Color.parseColor("#FFC107");
        if (aqi <= 150) return Color.parseColor("#FF9800");
        return Color.parseColor("#F44336");
    }

    private String formatAbsoluteDate(String isoTimestamp) {
        try {
            String cleaned = isoTimestamp.replaceAll("\\.[0-9]+", "");
            if (cleaned.endsWith("Z")) {
                cleaned = cleaned.substring(0, cleaned.length() - 1);
            } else {
                cleaned = cleaned.replaceAll("[+-]\\d{2}:\\d{2}$", "");
            }
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            Date date = sdf.parse(cleaned);
            SimpleDateFormat displayFmt = new SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.US);
            displayFmt.setTimeZone(java.util.TimeZone.getDefault());
            return displayFmt.format(date);
        } catch (Exception e) {
            return "";
        }
    }

    private String formatTimeAgo(String isoTimestamp) {
        try {
            String cleaned = isoTimestamp.replaceAll("\\.[0-9]+", "");
            if (cleaned.endsWith("Z")) {
                cleaned = cleaned.substring(0, cleaned.length() - 1);
            } else {
                cleaned = cleaned.replaceAll("[+-]\\d{2}:\\d{2}$", "");
            }
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            Date date = sdf.parse(cleaned);
            long diffMs = System.currentTimeMillis() - date.getTime();
            long diffMins = diffMs / 60000;
            long diffHours = diffMins / 60;
            long diffDays = diffHours / 24;

            if (diffMins < 1) return "Just now";
            if (diffMins < 60) return diffMins + " min ago";
            if (diffHours < 24) return diffHours + " hour" + (diffHours > 1 ? "s" : "") + " ago";
            if (diffDays < 7) return diffDays + " day" + (diffDays > 1 ? "s" : "") + " ago";
            SimpleDateFormat displayFmt = new SimpleDateFormat("MMM d, yyyy", Locale.US);
            return displayFmt.format(date);
        } catch (Exception e) {
            return "Last updated";
        }
    }
}

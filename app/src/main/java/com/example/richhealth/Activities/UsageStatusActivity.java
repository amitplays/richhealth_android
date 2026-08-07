package com.example.richhealth.Activities;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.example.richhealth.R;
import com.google.android.material.button.MaterialButton;

import Utils.ApiConfig;
import Utils.ProUpgradeDialog;
import Utils.Skeleton;

import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.ValueFormatter;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import Utils.ProStatusManager;

public class UsageStatusActivity extends AppCompatActivity {

    private static final String TAG = "UsageStatusActivity";

    private ProStatusManager proStatusManager;
    private PieChart usageOverviewChart;

    // Plan tabs
    private TextView[] allPlanTabs;
    private final String[] planTabKeys = {"free", "plus", "pro", "ultra"};
    private String currentPlanTab;

    // Feature table value cells
    private TextView featValChat, featValSessions, featValAnalysis, featValReports;
    private TextView featValNutri, featValDietary, featValReportAnalysis, featValModel;
    private TextView featValSharing, featValDependents;
    private TextView featureValueHeader;

    // Latest usage payload (null until /api/user/usage returns)
    private JSONObject latestUsage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_usage_status);

        proStatusManager = ProStatusManager.getInstance(this);
        Utils.IconAnimator.animateSectionIcons(getWindow().getDecorView());

        // Back button
        ImageButton backButton = findViewById(R.id.back_button);
        backButton.setOnClickListener(v -> finish());

        setupUsageChart();
        setupPlanTabs();

        // Skeleton-load everything that depends on /api/user/usage.
        Skeleton.show(
                findViewById(R.id.dialog_plan_description),
                findViewById(R.id.dialog_usage_analysis_count),
                findViewById(R.id.dialog_usage_analysis_period),
                findViewById(R.id.dialog_usage_reports_count),
                findViewById(R.id.dialog_usage_reports_period),
                findViewById(R.id.dialog_usage_chat_count),
                findViewById(R.id.dialog_usage_chat_period),
                findViewById(R.id.dialog_usage_nutri_count),
                findViewById(R.id.dialog_usage_dietary_count),
                findViewById(R.id.chart_subtitle)
        );

        // Show a sensible default immediately using cached tier, then fetch real usage.
        renderFromUsage(null);
        fetchUsageSummary();
    }

    // ═══════ PLAN TABS ═══════

    private void setupPlanTabs() {
        TextView tabFree = findViewById(R.id.tab_plan_free);
        TextView tabPlus = findViewById(R.id.tab_plan_plus);
        TextView tabPro = findViewById(R.id.tab_plan_pro);
        TextView tabUltra = findViewById(R.id.tab_plan_ultra);
        allPlanTabs = new TextView[]{tabFree, tabPlus, tabPro, tabUltra};

        featValChat = findViewById(R.id.feat_val_chat);
        featValSessions = findViewById(R.id.feat_val_sessions);
        featValAnalysis = findViewById(R.id.feat_val_analysis);
        featValReports = findViewById(R.id.feat_val_reports);
        featValNutri = findViewById(R.id.feat_val_nutri);
        featValDietary = findViewById(R.id.feat_val_dietary);
        featValReportAnalysis = findViewById(R.id.feat_val_report_analysis);
        featValModel = findViewById(R.id.feat_val_model);
        featValSharing = findViewById(R.id.feat_val_sharing);
        featValDependents = findViewById(R.id.feat_val_dependents);
        featureValueHeader = findViewById(R.id.dialog_feature_value_header);

        for (int i = 0; i < allPlanTabs.length; i++) {
            final int idx = i;
            allPlanTabs[i].setOnClickListener(v -> {
                currentPlanTab = planTabKeys[idx];
                updateTabStyles(idx);
                updateFeatureTable(currentPlanTab);
            });
        }
    }

    private void updateTabStyles(int selectedIdx) {
        for (int j = 0; j < allPlanTabs.length; j++) {
            if (j == selectedIdx) {
                allPlanTabs[j].setBackgroundResource(R.drawable.pill_tab_selected);
                allPlanTabs[j].setTextColor(Color.WHITE);
            } else {
                allPlanTabs[j].setBackgroundResource(R.drawable.pill_tab_unselected);
                allPlanTabs[j].setTextColor(Color.parseColor("#AAAAAA"));
            }
        }
    }

    private void selectInitialTab(String userTier) {
        String tabToSelect;
        switch (userTier) {
            case "ultra": tabToSelect = "ultra"; break;
            case "pro":
            case "family":
            case "family_member": tabToSelect = "pro"; break;
            case "plus": tabToSelect = "plus"; break;
            default: tabToSelect = "free"; break;
        }
        currentPlanTab = tabToSelect;

        for (int i = 0; i < planTabKeys.length; i++) {
            if (planTabKeys[i].equals(tabToSelect)) {
                updateTabStyles(i);
                break;
            }
        }
        updateFeatureTable(currentPlanTab);
    }

    // ═══════ FEATURE COMPARISON TABLE (hardcoded to mirror backend config/tiers.js) ═══════
    // These are compile-time constants for the comparison UI. They must match tiers.js.
    // The summary cards above use LIVE usage from /api/user/usage, not this table.

    private void updateFeatureTable(String plan) {
        int green = Color.parseColor("#4CAF50");
        int orange = Color.parseColor("#FF9800");

        String[] planLabels = {"FREE", "PLUS", "PRO", "ULTRA"};
        for (int i = 0; i < planTabKeys.length; i++) {
            if (planTabKeys[i].equals(plan)) {
                featureValueHeader.setText(planLabels[i]);
                break;
            }
        }

        int chat, sessions, reports, dependents, nutriLimit, dietaryLimit, reportAnalysisLimit, analysisLimit;
        boolean sharingAvailable;

        switch (plan) {
            case "ultra":
                chat = 100; sessions = 100; reports = 0; dependents = 5;
                analysisLimit = 0; sharingAvailable = true;
                nutriLimit = 0; dietaryLimit = 0; reportAnalysisLimit = 0;
                break;
            case "pro":
                chat = 50; sessions = 50; reports = 10; dependents = 2;
                analysisLimit = 10; sharingAvailable = true;
                nutriLimit = 0; dietaryLimit = 20; reportAnalysisLimit = 10;
                break;
            case "plus":
                chat = 25; sessions = 25; reports = 5; dependents = 1;
                analysisLimit = 5; sharingAvailable = false;
                nutriLimit = 15; dietaryLimit = 10; reportAnalysisLimit = 5;
                break;
            default: // free
                chat = 5; sessions = 5; reports = 2; dependents = 0;
                analysisLimit = 1; sharingAvailable = false;
                nutriLimit = 5; dietaryLimit = 2; reportAnalysisLimit = 0;
                break;
        }

        featValChat.setText(String.valueOf(chat));
        featValChat.setTextColor(green);
        featValSessions.setText(String.valueOf(sessions));
        featValSessions.setTextColor(green);

        if (analysisLimit == 0) {
            featValAnalysis.setText("Unlimited"); featValAnalysis.setTextColor(green);
        } else {
            featValAnalysis.setText(analysisLimit + "/month");
            featValAnalysis.setTextColor(plan.equals("free") ? orange : green);
        }

        if (reports == 0) {
            featValReports.setText("Unlimited"); featValReports.setTextColor(green);
        } else {
            featValReports.setText(reports + "/month");
            featValReports.setTextColor(plan.equals("free") ? orange : green);
        }

        if (nutriLimit == 0 && !plan.equals("free")) {
            featValNutri.setText("Unlimited"); featValNutri.setTextColor(green);
        } else {
            featValNutri.setText(nutriLimit + "/month"); featValNutri.setTextColor(orange);
        }

        if (featValDietary != null) {
            if (dietaryLimit == 0 && !plan.equals("free")) {
                featValDietary.setText("Unlimited"); featValDietary.setTextColor(green);
            } else {
                featValDietary.setText(dietaryLimit + "/month");
                featValDietary.setTextColor(plan.equals("free") ? orange : green);
            }
        }

        if (plan.equals("free")) {
            featValReportAnalysis.setText("Locked"); featValReportAnalysis.setTextColor(orange);
        } else if (reportAnalysisLimit == 0) {
            featValReportAnalysis.setText("Unlimited"); featValReportAnalysis.setTextColor(green);
        } else {
            featValReportAnalysis.setText(reportAnalysisLimit + "/month"); featValReportAnalysis.setTextColor(green);
        }

        boolean allModels = plan.equals("pro") || plan.equals("ultra");
        featValModel.setText(allModels ? "All" : "5 Models");
        featValModel.setTextColor(allModels ? green : orange);

        featValSharing.setText(sharingAvailable ? "Yes" : "--");
        featValSharing.setTextColor(sharingAvailable ? green : orange);

        featValDependents.setText(String.valueOf(dependents));
        featValDependents.setTextColor(dependents > 0 ? green : orange);
    }

    // ═══════ FETCH USAGE FROM BACKEND ═══════

    private void fetchUsageSummary() {
        TokenManager tokenManager = TokenManager.getInstance(this);
        String token = tokenManager.getToken();
        if (token == null) {
            Log.w(TAG, "No auth token, skipping usage fetch");
            return;
        }

        String url = ApiConfig.BASE_URL + "/api/user/usage";
        StringRequest request = new StringRequest(Request.Method.GET, url,
                response -> {
                    ApiConfig.logRestCall(url, true, "Usage fetched");
                    try {
                        latestUsage = new JSONObject(response);
                        renderFromUsage(latestUsage);
                    } catch (Exception e) {
                        Log.e(TAG, "Parse error on /api/user/usage", e);
                    }
                },
                error -> {
                    ApiConfig.logRestCall(url, false, error.toString());
                    Log.e(TAG, "Error fetching usage: " + error);
                    // Show one compact error pill on the description line; hide
                    // the rest so the page doesn't show stale/misleading values.
                    String reason = error.networkResponse == null ? "No connection" : "Unavailable";
                    TextView desc = findViewById(R.id.dialog_plan_description);
                    Skeleton.hideAndGone(
                            findViewById(R.id.dialog_usage_analysis_count),
                            findViewById(R.id.dialog_usage_analysis_period),
                            findViewById(R.id.dialog_usage_reports_count),
                            findViewById(R.id.dialog_usage_reports_period),
                            findViewById(R.id.dialog_usage_chat_count),
                            findViewById(R.id.dialog_usage_chat_period),
                            findViewById(R.id.dialog_usage_nutri_count),
                            findViewById(R.id.dialog_usage_dietary_count),
                            findViewById(R.id.chart_subtitle)
                    );
                    if (desc != null) Skeleton.error(desc, reason);
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + token);
                return headers;
            }
        };
        Volley.newRequestQueue(this).add(request);
    }

    // ═══════ RENDER ═══════

    private void renderFromUsage(JSONObject data) {
        // [PLAN-PILL-REVIEW] disabled (backend-driven, keep after review): TextView planBadge = findViewById(R.id.plan_badge);
        TextView planDescription = findViewById(R.id.dialog_plan_description);
        TextView resetLabel = findViewById(R.id.usage_reset_label);
        TextView chartSubtitle = findViewById(R.id.chart_subtitle);
        MaterialButton upgradeButton = findViewById(R.id.dialog_usage_upgrade_button);

        // Resolve tier: prefer server tier, fall back to local cache
        String tier = proStatusManager.getUserTier();
        boolean isPro = proStatusManager.isProUser();
        if (data != null) {
            tier = data.optString("tier", tier);
            isPro = data.optBoolean("isPro", isPro);
        }

        // Real backend data is in — drop skeletons on the fields we're about to fill.
        if (data != null) {
            Skeleton.hide(planDescription, chartSubtitle); // [PLAN-PILL-REVIEW] plan_badge skeleton removed
        }

        // [PLAN-PILL-REVIEW] disabled (backend-driven, keep after review): setupPlanBadge(planBadge, tier);
        selectInitialTab(tier);

        // Plan description
        planDescription.setText(buildPlanDescription(tier, isPro));

        // Reset date label (only when server data is loaded)
        if (data != null && data.has("periodEnd")) {
            long periodEnd = data.optLong("periodEnd", 0);
            if (periodEnd > 0) {
                long daysLeft = Math.max(0, (periodEnd - System.currentTimeMillis()) / (1000L * 60 * 60 * 24));
                resetLabel.setText(daysLeft <= 1
                        ? "RESETS TOMORROW"
                        : "RESETS IN " + daysLeft + " DAYS");
                resetLabel.setVisibility(android.view.View.VISIBLE);
            }
        }

        // Summary cards
        JSONObject usage = data != null ? data.optJSONObject("usage") : null;
        JSONObject tierConfig = data != null ? data.optJSONObject("tierConfig") : null;

        bindUsageCard(R.id.dialog_usage_analysis_count, R.id.dialog_usage_analysis_period,
                R.id.pb_usage_analysis, usage, "healthAnalysis", "monthly");
        bindUsageCard(R.id.dialog_usage_reports_count, R.id.dialog_usage_reports_period,
                R.id.pb_usage_reports, usage, "medicalReports", "monthly");
        bindUsageCard(R.id.dialog_usage_chat_count, R.id.dialog_usage_chat_period,
                R.id.pb_usage_chat, usage, "chatSessions", "sessions");
        bindUsageCard(R.id.dialog_usage_nutri_count, -1,
                R.id.pb_usage_nutri, usage, "nutricheck", null);
        bindUsageCard(R.id.dialog_usage_dietary_count, -1,
                R.id.pb_usage_dietary, usage, "dietaryInsights", null);

        // Pie chart — actual consumption if any, otherwise month-progress ring
        long periodStart = data != null ? data.optLong("periodStart", 0) : 0;
        long periodEnd = data != null ? data.optLong("periodEnd", 0) : 0;
        populateUsageChart(tier, usage, periodStart, periodEnd);

        // Upgrade button state
        if (isPro) {
            upgradeButton.setText("Manage Account");
            upgradeButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#0D1F1F")));
            upgradeButton.setStrokeColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#008B8B")));
            upgradeButton.setStrokeWidth(2);
            upgradeButton.setTextColor(Color.parseColor("#008B8B"));
        } else {
            upgradeButton.setText("Upgrade to Pro");
            upgradeButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#008B8B")));
            upgradeButton.setStrokeColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#00BFA5")));
            upgradeButton.setStrokeWidth(0);
            upgradeButton.setTextColor(Color.WHITE);
        }
        final boolean proFinal = isPro;
        upgradeButton.setOnClickListener(v -> {
            if (!proFinal) {
                new ProUpgradeDialog(this).show(newIsPro -> {
                    if (newIsPro) ProStatusManager.syncProStatusOnLogin(this);
                });
            } else {
                finish();
            }
        });

        if (chartSubtitle != null && data != null) {
            String sub = buildChartSubtitle(isPro, tier, periodEnd);
            chartSubtitle.setText(sub);
        }
    }

    private String buildChartSubtitle(boolean isPro, String tier, long periodEnd) {
        long expiry = proStatusManager.getExpiryDate();
        if (isPro && expiry > 0) {
            long daysToExpiry = Math.max(0, (expiry - System.currentTimeMillis()) / (1000L * 60 * 60 * 24));
            String planLbl = planLabelForTier(tier);
            if (daysToExpiry <= 0) return planLbl + " plan — expires today";
            if (daysToExpiry <= 30) return planLbl + " plan — " + daysToExpiry + " days left";
            return planLbl + " plan — " + daysToExpiry + " days remaining";
        }
        if (periodEnd > 0) {
            long d = Math.max(0, (periodEnd - System.currentTimeMillis()) / (1000L * 60 * 60 * 24));
            return d <= 1 ? "Free limits reset tomorrow" : "Free limits reset in " + d + " days";
        }
        return "Your monthly limits";
    }

    /**
     * Populate one summary card from the usage payload.
     * Shows "used/limit" with a matching progress bar; colors shift to orange
     * when the user is close to / past their cap.
     *
     * @param periodViewId -1 if the card has no period TextView (row 2 cards)
     */
    private void bindUsageCard(int countViewId, int periodViewId, int progressViewId,
                               JSONObject usage, String feature, String periodText) {
        TextView countView = findViewById(countViewId);
        TextView periodView = periodViewId == -1 ? null : findViewById(periodViewId);
        ProgressBar pb = findViewById(progressViewId);

        int green = Color.parseColor("#4CAF50");
        int white = Color.WHITE;
        int orange = Color.parseColor("#FF9800");
        int dim = Color.parseColor("#808080");

        if (usage == null) {
            // Data hasn't arrived yet — leave the skeleton pulsing on the count/period
            // views and the progress bar idle. Real values will replace them once
            // /api/user/usage returns.
            if (pb != null) {
                pb.setMax(100);
                pb.setProgress(0);
                pb.setVisibility(android.view.View.VISIBLE);
            }
            return;
        }

        // Real data arrived — remove skeletons from this card's text views.
        Skeleton.hide(countView, periodView);

        JSONObject f = usage.optJSONObject(feature);
        if (f == null) {
            if (pb != null) pb.setVisibility(android.view.View.INVISIBLE);
            return;
        }

        int count = f.optInt("count", 0);
        boolean hasLimit = !f.isNull("limit");
        int limit = f.optInt("limit", 0);
        boolean reached = f.optBoolean("limitReached", false);

        if (!hasLimit) {
            // Unlimited
            countView.setText(count + " used");
            countView.setTextColor(green);
            if (periodView != null) periodView.setText("unlimited");
            if (pb != null) {
                pb.setMax(100); pb.setProgress(100);
                pb.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#4CAF50")));
                pb.setVisibility(android.view.View.VISIBLE);
            }
            return;
        }

        if (limit == 0) {
            // Feature locked for this tier
            countView.setText("Locked");
            countView.setTextColor(orange);
            if (periodView != null) periodView.setText("upgrade");
            if (pb != null) {
                pb.setMax(100); pb.setProgress(0);
                pb.setVisibility(android.view.View.VISIBLE);
            }
            return;
        }

        int displayCount = Math.min(count, limit);
        int pct = Math.min(100, Math.round(100f * count / limit));

        countView.setText(displayCount + "/" + limit);
        countView.setTextColor(reached ? orange : white);
        if (periodView != null && periodText != null) {
            periodView.setText(periodText);
        }

        if (pb != null) {
            pb.setMax(100);
            pb.setProgress(pct);
            int tint = reached ? orange : (pct >= 80 ? Color.parseColor("#FFB300") : Color.parseColor("#008B8B"));
            pb.setProgressTintList(android.content.res.ColorStateList.valueOf(tint));
            pb.setVisibility(android.view.View.VISIBLE);
        }
    }

    private String buildPlanDescription(String tier, boolean isPro) {
        if (!isPro) {
            return "Track your limits across chat, reports, and analysis. Upgrade for more.";
        }
        String expiry = proStatusManager.getFormattedExpiryDate();
        switch (tier) {
            case "ultra":
                return expiry != null
                        ? "Ultra plan active until " + expiry + ". All features unlocked."
                        : "All features unlocked with unlimited access across the platform.";
            case "family":
                int memberCount = proStatusManager.getFamilyMemberCount();
                int maxMembers = proStatusManager.getMaxFamilyMembers();
                return "Family plan with " + memberCount + "/" + maxMembers + " members. Pro features for everyone.";
            case "family_member":
                return "Pro access granted by " + proStatusManager.getProGrantedBy() + ". Enjoy full pro features.";
            case "plus":
                return expiry != null
                        ? "Plus plan active until " + expiry + ". Essential features unlocked."
                        : "Essential features unlocked with expanded limits.";
            default:
                return expiry != null
                        ? "Pro plan active until " + expiry + ". All features unlocked."
                        : "All features unlocked with full access across the platform.";
        }
    }

    // ═══════ HELPERS ═══════

    private void setupPlanBadge(TextView badge, String tier) {
        if (badge == null) return;
        switch (tier) {
            case "ultra":
                badge.setText("RichHealth Ultra");
                badge.setTextColor(Color.parseColor("#E040FB"));
                break;
            case "pro":
                badge.setText("RichHealth Pro");
                badge.setTextColor(Color.parseColor("#4CAF50"));
                break;
            case "plus":
                badge.setText("RichHealth Plus");
                badge.setTextColor(Color.parseColor("#008b8b"));
                break;
            case "family":
                badge.setText("RichHealth Family");
                badge.setTextColor(Color.parseColor("#4CAF50"));
                break;
            case "family_member":
                badge.setText("Family Member");
                badge.setTextColor(Color.parseColor("#4CAF50"));
                break;
            default:
                badge.setText("Free Plan");
                badge.setTextColor(Color.WHITE);
                break;
        }
    }

    // ═══════ USAGE OVERVIEW CHART ═══════

    private void setupUsageChart() {
        usageOverviewChart = findViewById(R.id.usage_overview_pie_chart);
        if (usageOverviewChart == null) return;

        usageOverviewChart.setUsePercentValues(false);
        usageOverviewChart.getDescription().setEnabled(false);
        usageOverviewChart.setDrawHoleEnabled(true);
        usageOverviewChart.setHoleColor(Color.TRANSPARENT);
        usageOverviewChart.setHoleRadius(58f);
        usageOverviewChart.setTransparentCircleRadius(58f);
        usageOverviewChart.setTransparentCircleColor(Color.TRANSPARENT);
        usageOverviewChart.setDrawSlicesUnderHole(false);
        usageOverviewChart.setDrawCenterText(true);
        usageOverviewChart.setCenterTextColor(Color.WHITE);
        usageOverviewChart.setCenterTextSize(14f);
        usageOverviewChart.setRotationEnabled(false);
        usageOverviewChart.setHighlightPerTapEnabled(false);
        usageOverviewChart.setDrawEntryLabels(false);
        usageOverviewChart.setBackgroundColor(Color.TRANSPARENT);
        usageOverviewChart.setExtraOffsets(8f, 4f, 8f, 4f);

        Legend legend = usageOverviewChart.getLegend();
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

        usageOverviewChart.setCenterText("Loading...");
        usageOverviewChart.invalidate();
    }

    /**
     * Two modes:
     *  - If the user has used anything this period, show a feature-breakdown donut.
     *  - Otherwise, show a month-progress ring (elapsed vs remaining days) with
     *    the days-to-reset count as the center text. Useful info either way.
     */
    private void populateUsageChart(String tier, JSONObject usage, long periodStart, long periodEnd) {
        if (usageOverviewChart == null) return;

        List<PieEntry> entries = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();

        int totalUsed = 0;
        Legend legend = usageOverviewChart.getLegend();

        if (usage != null) {
            int[] featColors = {
                    Color.parseColor("#008B8B"),
                    Color.parseColor("#00BFA5"),
                    Color.parseColor("#26A69A"),
                    Color.parseColor("#4DB6AC"),
                    Color.parseColor("#80CBC4"),
                    Color.parseColor("#004D40"),
            };
            String[] features = {"healthAnalysis", "medicalReports", "reportAnalysis", "nutricheck", "dietaryInsights", "chatSessions"};
            String[] labels   = {"Analysis",       "Reports",        "Report Analysis","NutriCheck", "Diet Guide",      "Sessions"};

            for (int i = 0; i < features.length; i++) {
                JSONObject f = usage.optJSONObject(features[i]);
                if (f == null) continue;
                int count = f.optInt("count", 0);
                if (count <= 0) continue;
                entries.add(new PieEntry(count, labels[i] + " (" + count + ")"));
                colors.add(featColors[i]);
                totalUsed += count;
            }
        }

        String planLabel = planLabelForTier(tier);

        if (totalUsed == 0 && periodStart > 0 && periodEnd > 0) {
            // Month-progress ring: elapsed time vs remaining time in billing period.
            long now = System.currentTimeMillis();
            long total = Math.max(1, periodEnd - periodStart);
            long elapsed = Math.max(0, Math.min(total, now - periodStart));
            long remaining = Math.max(0, total - elapsed);

            long daysLeft = Math.max(0, remaining / (1000L * 60 * 60 * 24));

            entries.clear();
            colors.clear();
            entries.add(new PieEntry(elapsed, "Elapsed"));
            colors.add(Color.parseColor("#2A2A2A"));
            entries.add(new PieEntry(remaining, "Resets in " + daysLeft + "d"));
            colors.add(Color.parseColor("#008B8B"));

            legend.setEnabled(true);
            String centerLine1 = daysLeft <= 1 ? "resets\ntomorrow" : daysLeft + " days\nto reset";
            usageOverviewChart.setCenterText(centerLine1 + "\n" + planLabel);
        } else if (totalUsed == 0) {
            // No period info (pre-fetch): keep minimal, center shows plan name.
            legend.setEnabled(false);
            entries.clear();
            colors.clear();
            entries.add(new PieEntry(1, ""));
            colors.add(Color.parseColor("#2A2A2A"));
            usageOverviewChart.setCenterText(planLabel);
        } else {
            legend.setEnabled(true);
            usageOverviewChart.setCenterText(planLabel + "\n" + totalUsed + " actions");
        }

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(colors);
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setValueTextSize(13f);
        dataSet.setDrawValues(totalUsed > 0);
        dataSet.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.valueOf((int) value);
            }
        });
        dataSet.setSliceSpace(2f);
        dataSet.setSelectionShift(0f);

        PieData data = new PieData(dataSet);
        usageOverviewChart.setData(data);
        usageOverviewChart.animateY(600);
        usageOverviewChart.invalidate();
    }

    private String planLabelForTier(String tier) {
        switch (tier) {
            case "ultra": return "Ultra";
            case "pro": return "Pro";
            case "plus": return "Plus";
            case "family":
            case "family_member": return "Family";
            default: return "Free";
        }
    }

}

package com.example.richhealth.Activities;
import Utils.Utilities;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.example.richhealth.R;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import Models.SelectableOption;
import Utils.ApiConfig;
import Utils.SimpleProgress;

public class DailyCheckInActivity extends AppCompatActivity {

    private static final String TAG = "DailyCheckInActivity";

    private TokenManager tokenManager;

    private TextView tierBadge;
    private LinearLayout listLoadingState;
    private LinearLayout noAccessState;
    private LinearLayout emptyState;
    private TextView emptySubtitle;
    private RecyclerView checkinRecycler;
    private LinearLayout startBanner;
    private MaterialButton btnStartCheckin;
    private android.widget.ImageView listLoadingLogo;
    private android.animation.ObjectAnimator listLoadingSpinner;

    private boolean isDue = false;
    private String nextDueDate = null;

    private BarChart checkinChart;
    private TextView checkinChartSubtitle;

    private final List<SessionItem> sessionItems = new ArrayList<>();
    private SessionListAdapter listAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_daily_checkin);

        tokenManager = TokenManager.getInstance(this);
        Utils.IconAnimator.animateSectionIcons(getWindow().getDecorView());

        ImageButton backButton = findViewById(R.id.back_button);
        backButton.setOnClickListener(v -> finish());

        tierBadge        = findViewById(R.id.tier_badge);
        listLoadingState = findViewById(R.id.list_loading_state);
        noAccessState    = findViewById(R.id.no_access_state);
        emptyState       = findViewById(R.id.empty_state);
        emptySubtitle    = findViewById(R.id.empty_subtitle);
        checkinRecycler  = findViewById(R.id.checkin_recycler);
        startBanner      = findViewById(R.id.start_banner);
        btnStartCheckin  = findViewById(R.id.btn_start_checkin);
        listLoadingLogo  = findViewById(R.id.list_loading_logo);

        listLoadingSpinner = android.animation.ObjectAnimator
                .ofFloat(listLoadingLogo, android.view.View.ROTATION, 0f, 360f);
        listLoadingSpinner.setDuration(1200);
        listLoadingSpinner.setRepeatCount(android.animation.ObjectAnimator.INFINITE);
        listLoadingSpinner.setInterpolator(new android.view.animation.LinearInterpolator());

        btnStartCheckin.setOnClickListener(v -> startNewCheckIn());

        listAdapter = new SessionListAdapter(sessionItems);
        checkinRecycler.setLayoutManager(new LinearLayoutManager(this));
        checkinRecycler.setNestedScrollingEnabled(false);
        checkinRecycler.setAdapter(listAdapter);

        checkinChart = findViewById(R.id.checkin_chart);
        checkinChartSubtitle = findViewById(R.id.checkin_chart_subtitle);
        setupCheckInChart();

        fetchCheckInList();
    }

    // ─── Network ──────────────────────────────────────────────────────────────

    private void fetchCheckInList() {
        String token = tokenManager != null ? tokenManager.getToken() : null;
        if (token == null) { showNoAccess(); return; }

        showLoadingList();
        String url = ApiConfig.BASE_URL + "/api/checkin/list";

        StringRequest request = new StringRequest(Request.Method.GET, url,
                response -> {
                    try {
                        JSONObject json = new JSONObject(response);
                        boolean canAccess = json.optBoolean("canAccess", false);
                        if (!canAccess) { showNoAccess(); return; }

                        String tier = json.optString("tier", "free");
                        if (tierBadge != null) {
                            String label = tier.substring(0, 1).toUpperCase() + tier.substring(1);
                            tierBadge.setText(label);
                            tierBadge.setVisibility(View.VISIBLE);
                        }

                        isDue = json.optBoolean("isDue", false);
                        nextDueDate = json.optString("nextDueDate", null);
                        if ("null".equals(nextDueDate) || nextDueDate != null && nextDueDate.isEmpty()) {
                            nextDueDate = null;
                        }

                        JSONArray arr = json.optJSONArray("sessions");
                        sessionItems.clear();
                        if (arr != null) {
                            for (int i = 0; i < arr.length(); i++) {
                                sessionItems.add(SessionItem.from(arr.getJSONObject(i)));
                            }
                        }

                        if (sessionItems.isEmpty()) showEmpty();
                        else showList();

                    } catch (JSONException e) {
                        Log.e(TAG, "Parse error in checkin list", e);
                        showEmpty();
                    }
                },
                error -> {
                    Log.e(TAG, "Error fetching check-in list", error);
                    showEmpty();
                }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> h = new HashMap<>();
                h.put("Authorization", "Bearer " + token);
                return h;
            }
        };
        request.setRetryPolicy(new DefaultRetryPolicy(15000, 1, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        Volley.newRequestQueue(this).add(request);
    }

    void openSession(String sessionId) {
        SimpleProgress.show(this, "Loading your check-in...");
        String token = tokenManager != null ? tokenManager.getToken() : null;
        if (token == null) { SimpleProgress.hide(); return; }

        String url = ApiConfig.BASE_URL + "/api/checkin/sessions/" + sessionId + "/questions";

        StringRequest request = new StringRequest(Request.Method.GET, url,
                response -> {
                    SimpleProgress.hide();
                    try {
                        JSONObject json = new JSONObject(response);
                        JSONArray questions = json.optJSONArray("questions");
                        if (questions == null || questions.length() == 0) {
                            Utilities.toast(this, "No questions available right now.");
                            return;
                        }
                        showQuestionDialog(sessionId, questions);
                    } catch (JSONException e) {
                        Log.e(TAG, "Parse error in session questions", e);
                        Utilities.toast(this, "Failed to load questions.");
                    }
                },
                error -> {
                    SimpleProgress.hide();
                    Log.e(TAG, "Error loading session questions", error);
                    Utilities.toast(this, "Failed to load questions. Please try again.");
                }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> h = new HashMap<>();
                h.put("Authorization", "Bearer " + token);
                return h;
            }
        };
        request.setRetryPolicy(new DefaultRetryPolicy(20000, 1, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        Volley.newRequestQueue(this).add(request);
    }

    // ─── Question dialog ──────────────────────────────────────────────────────

    private void showQuestionDialog(String sessionId, JSONArray questionsArray) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_checkin_questions);
        dialog.setCancelable(false);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            WindowManager.LayoutParams params = window.getAttributes();
            params.dimAmount = 0.7f;
            window.setAttributes(params);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }

        TextView tvProgress        = dialog.findViewById(R.id.question_progress);
        ProgressBar pbProgress     = dialog.findViewById(R.id.question_progress_bar);
        TextView tvQuestion        = dialog.findViewById(R.id.question_text);
        RecyclerView optionsRv     = dialog.findViewById(R.id.options_recycler);
        LinearLayout submitLoading = dialog.findViewById(R.id.submit_loading);
        android.widget.ImageView submitLogo = dialog.findViewById(R.id.submit_loading_logo);
        MaterialButton btnNext     = dialog.findViewById(R.id.btn_next);

        // Spin animator for the saving logo
        android.animation.ObjectAnimator submitSpinner = android.animation.ObjectAnimator
                .ofFloat(submitLogo, android.view.View.ROTATION, 0f, 360f);
        submitSpinner.setDuration(1200);
        submitSpinner.setRepeatCount(android.animation.ObjectAnimator.INFINITE);
        submitSpinner.setInterpolator(new android.view.animation.LinearInterpolator());

        optionsRv.setLayoutManager(new GridLayoutManager(this, 2));

        final int total = questionsArray.length();
        final int[] currentIndex = {0};
        final SelectableCardAdapter[] currentAdapter = {null};
        final String[] currentQuestionId = {null};
        // Keep reference to current options so we can look up label/emoji by value on submit
        @SuppressWarnings("unchecked")
        final List<SelectableOption>[] currentOptions = new List[]{null};

        Runnable showQuestion = new Runnable() {
            @Override
            public void run() {
                if (currentIndex[0] >= total) return;
                try {
                    JSONObject q = questionsArray.getJSONObject(currentIndex[0]);
                    currentQuestionId[0] = q.optString("_id", null);

                    tvProgress.setText("Question " + (currentIndex[0] + 1) + " of " + total);
                    pbProgress.setMax(total);
                    pbProgress.setProgress(currentIndex[0] + 1);
                    tvQuestion.setText(q.optString("text", ""));
                    btnNext.setText(currentIndex[0] == total - 1 ? "Done" : "Next");
                    btnNext.setEnabled(false);

                    JSONArray opts = q.optJSONArray("options");
                    List<SelectableOption> options = new ArrayList<>();
                    if (opts != null) {
                        for (int i = 0; i < opts.length(); i++) {
                            JSONObject o = opts.getJSONObject(i);
                            // SelectableOption constructor: (label, emoji, value)
                            options.add(new SelectableOption(
                                    o.optString("label", ""),
                                    o.optString("emoji", ""),
                                    o.optString("value", "")));
                        }
                    }
                    currentOptions[0] = options;
                    currentAdapter[0] = new SelectableCardAdapter(options, false);
                    currentAdapter[0].setOnSelectionChangedListener(
                            () -> btnNext.setEnabled(currentAdapter[0].hasSelection()));
                    optionsRv.setAdapter(currentAdapter[0]);
                } catch (JSONException e) {
                    Log.e(TAG, "Error showing question", e);
                }
            }
        };
        showQuestion.run();

        btnNext.setOnClickListener(v -> {
            if (currentAdapter[0] == null || !currentAdapter[0].hasSelection()) {
                Utilities.toast(this, "Please select an answer.");
                return;
            }

            // Find the selected option by matching value
            Object selectedValue = currentAdapter[0].getSelectedValue();
            SelectableOption selectedOpt = null;
            if (currentOptions[0] != null) {
                for (SelectableOption opt : currentOptions[0]) {
                    if (selectedValue != null && selectedValue.equals(opt.value)) {
                        selectedOpt = opt;
                        break;
                    }
                }
            }
            if (selectedOpt == null) {
                Utilities.toast(this, "Please select an answer.");
                return;
            }
            final SelectableOption finalOpt = selectedOpt;

            btnNext.setEnabled(false);
            submitLoading.setVisibility(View.VISIBLE);
            submitSpinner.start();

            String token = tokenManager != null ? tokenManager.getToken() : null;
            if (token == null) { dialog.dismiss(); return; }

            String url = ApiConfig.BASE_URL + "/api/checkin/sessions/" + sessionId + "/respond";
            try {
                JSONObject body = new JSONObject();
                body.put("questionId", currentQuestionId[0]);
                body.put("selectedLabel", finalOpt.label);
                body.put("selectedEmoji", finalOpt.emoji);
                body.put("selectedValue", String.valueOf(finalOpt.value));
                final byte[] bodyBytes = body.toString().getBytes(StandardCharsets.UTF_8);

                StringRequest req = new StringRequest(Request.Method.POST, url,
                        resp -> {
                            submitSpinner.cancel();
                            submitLoading.setVisibility(View.GONE);
                            currentIndex[0]++;
                            if (currentIndex[0] >= total) {
                                dialog.dismiss();
                                Utilities.toast(this, "Check-in complete! \uD83D\uDC99");
                                fetchCheckInList();
                            } else {
                                showQuestion.run();
                            }
                        },
                        error -> {
                            submitSpinner.cancel();
                            submitLoading.setVisibility(View.GONE);
                            btnNext.setEnabled(true);
                            Log.e(TAG, "Submit response error", error);
                            Utilities.toast(this, "Failed to save answer. Try again.");
                        }) {
                    @Override
                    public Map<String, String> getHeaders() throws AuthFailureError {
                        Map<String, String> h = new HashMap<>();
                        h.put("Authorization", "Bearer " + token);
                        h.put("Content-Type", "application/json");
                        return h;
                    }
                    @Override public byte[] getBody() { return bodyBytes; }
                    @Override public String getBodyContentType() { return "application/json; charset=utf-8"; }
                };
                req.setRetryPolicy(new DefaultRetryPolicy(15000, 0, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
                Volley.newRequestQueue(this).add(req);
            } catch (JSONException e) {
                Log.e(TAG, "Error building response body", e);
                submitLoading.setVisibility(View.GONE);
                btnNext.setEnabled(true);
            }
        });

        dialog.show();
    }

    // ─── On-demand check-in start ─────────────────────────────────────────────

    private void startNewCheckIn() {
        btnStartCheckin.setEnabled(false); // prevent double-tap race condition
        SimpleProgress.show(this, "Preparing your health check-in...");
        String token = tokenManager != null ? tokenManager.getToken() : null;
        if (token == null) { SimpleProgress.hide(); btnStartCheckin.setEnabled(true); return; }

        String url = ApiConfig.BASE_URL + "/api/checkin/start";

        StringRequest request = new StringRequest(Request.Method.POST, url,
                response -> {
                    SimpleProgress.hide();
                    try {
                        JSONObject json = new JSONObject(response);
                        String sessionId = json.optString("sessionId", "");
                        JSONArray questions = json.optJSONArray("questions");
                        if (questions == null || questions.length() == 0) {
                            Utilities.toast(this, "No questions available right now.");
                            return;
                        }
                        showQuestionDialog(sessionId, questions);
                    } catch (JSONException e) {
                        Log.e(TAG, "Parse error starting check-in", e);
                        Utilities.toast(this, "Failed to start check-in.");
                    }
                },
                error -> {
                    SimpleProgress.hide();
                    btnStartCheckin.setEnabled(true); // re-enable on failure
                    Log.e(TAG, "Error starting check-in", error);
                    Utilities.toast(this, "Failed to start check-in. Please try again.");
                }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> h = new HashMap<>();
                h.put("Authorization", "Bearer " + token);
                h.put("Content-Type", "application/json");
                return h;
            }
            @Override public byte[] getBody() { return "{}".getBytes(StandardCharsets.UTF_8); }
            @Override public String getBodyContentType() { return "application/json; charset=utf-8"; }
        };
        // LLM generation can take a few seconds — give it 30s
        request.setRetryPolicy(new DefaultRetryPolicy(30000, 0, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        Volley.newRequestQueue(this).add(request);
    }

    // ─── State helpers ────────────────────────────────────────────────────────

    private void showLoadingList() {
        listLoadingSpinner.start();
        startBanner.setVisibility(View.GONE);
        listLoadingState.setVisibility(View.VISIBLE);
        noAccessState.setVisibility(View.GONE);
        emptyState.setVisibility(View.GONE);
        checkinRecycler.setVisibility(View.GONE);
    }

    private void showNoAccess() {
        listLoadingSpinner.cancel();
        startBanner.setVisibility(View.GONE);
        listLoadingState.setVisibility(View.GONE);
        noAccessState.setVisibility(View.VISIBLE);
        emptyState.setVisibility(View.GONE);
        checkinRecycler.setVisibility(View.GONE);
    }

    private void showEmpty() {
        listLoadingSpinner.cancel();
        // isDue=true + no history → show start banner, hide empty text
        // isDue=false + no history → hide start banner, show "all caught up"
        startBanner.setVisibility(isDue ? View.VISIBLE : View.GONE);
        listLoadingState.setVisibility(View.GONE);
        noAccessState.setVisibility(View.GONE);
        emptyState.setVisibility(isDue ? View.GONE : View.VISIBLE);
        checkinRecycler.setVisibility(View.GONE);

        if (!isDue && emptySubtitle != null && nextDueDate != null) {
            emptySubtitle.setText("Your next check-in will be ready on " + formatDate(nextDueDate, ""));
        }
        // Chart shows empty state — no sessions yet
        populateCheckInChart(sessionItems);
    }

    private void showList() {
        listLoadingSpinner.cancel();
        // Always show start banner at top when due, even alongside the history list
        startBanner.setVisibility(isDue ? View.VISIBLE : View.GONE);
        listLoadingState.setVisibility(View.GONE);
        noAccessState.setVisibility(View.GONE);
        emptyState.setVisibility(View.GONE);
        checkinRecycler.setVisibility(View.VISIBLE);
        listAdapter.notifyDataSetChanged();
        populateCheckInChart(sessionItems);
    }

    // ─── Check-in chart ───────────────────────────────────────────────────────

    private void setupCheckInChart() {
        if (checkinChart == null) return;
        checkinChart.getDescription().setEnabled(false);
        checkinChart.getLegend().setEnabled(false);
        checkinChart.setDrawGridBackground(false);
        checkinChart.setDrawBorders(false);
        checkinChart.setBackgroundColor(Color.TRANSPARENT);
        checkinChart.setTouchEnabled(false);
        checkinChart.setHighlightPerTapEnabled(false);
        checkinChart.setNoDataText("Complete your first check-in to see history");
        checkinChart.setNoDataTextColor(Color.parseColor("#808080"));
        checkinChart.getXAxis().setEnabled(false);
        checkinChart.getAxisLeft().setEnabled(false);
        checkinChart.getAxisRight().setEnabled(false);
        checkinChart.setExtraOffsets(0f, 8f, 0f, 4f);
        checkinChart.invalidate();
    }

    private void populateCheckInChart(List<SessionItem> items) {
        if (checkinChart == null) return;

        if (items == null || items.isEmpty()) {
            checkinChart.clear();
            checkinChart.invalidate();
            if (checkinChartSubtitle != null) {
                checkinChartSubtitle.setText("Complete your first check-in to see history");
            }
            return;
        }

        // Count statuses across all sessions
        int completed = 0, missed = 0, inProgress = 0;
        for (SessionItem s : items) {
            switch (s.status) {
                case "completed": completed++; break;
                case "missed":    missed++;    break;
                case "in_progress":
                case "pending":   inProgress++; break;
            }
        }
        int total = items.size();

        // Build bars: one bar per status category (completed, missed, in-progress)
        List<BarEntry> entries = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();

        if (completed > 0) {
            entries.add(new BarEntry(0, completed));
            colors.add(Color.parseColor("#008b8b")); // teal
        }
        if (missed > 0) {
            entries.add(new BarEntry(entries.size(), missed));
            colors.add(Color.parseColor("#E53935")); // red
        }
        if (inProgress > 0) {
            entries.add(new BarEntry(entries.size(), inProgress));
            colors.add(Color.parseColor("#FF9800")); // orange
        }

        if (entries.isEmpty()) {
            checkinChart.clear();
            checkinChart.invalidate();
            return;
        }

        BarDataSet dataSet = new BarDataSet(entries, "Check-ins");
        dataSet.setColors(colors);
        dataSet.setDrawValues(true);
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setValueTextSize(13f);
        dataSet.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.valueOf((int) value);
            }
        });

        // Custom x-axis labels
        final List<String> labels = new ArrayList<>();
        if (completed > 0) labels.add("Completed");
        if (missed > 0)    labels.add("Missed");
        if (inProgress > 0) labels.add("In Progress");

        checkinChart.getXAxis().setEnabled(true);
        checkinChart.getXAxis().setPosition(com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM);
        checkinChart.getXAxis().setGranularity(1f);
        checkinChart.getXAxis().setTextColor(Color.parseColor("#AAAAAA"));
        checkinChart.getXAxis().setTextSize(11f);
        checkinChart.getXAxis().setDrawGridLines(false);
        checkinChart.getXAxis().setDrawAxisLine(false);
        checkinChart.getXAxis().setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int idx = (int) value;
                return idx >= 0 && idx < labels.size() ? labels.get(idx) : "";
            }
        });

        BarData barData = new BarData(dataSet);
        barData.setBarWidth(0.45f);
        checkinChart.setData(barData);
        checkinChart.animateY(500);
        checkinChart.invalidate();

        // Update subtitle with total summary
        if (checkinChartSubtitle != null) {
            checkinChartSubtitle.setText(
                total + " total check-in" + (total == 1 ? "" : "s")
                + "  •  " + completed + " completed  •  " + missed + " missed"
            );
        }
    }

    // ─── Data model ───────────────────────────────────────────────────────────

    static class SessionItem {
        String sessionId;
        String period;
        String status;
        String scheduledFor;
        int totalQuestions;
        int answeredCount;
        String completedAt;
        List<JSONObject> responses = new ArrayList<>();

        static SessionItem from(JSONObject o) {
            SessionItem s = new SessionItem();
            s.sessionId      = o.optString("_id", "");
            s.period         = o.optString("period", "");
            s.status         = o.optString("status", "pending");
            s.scheduledFor   = o.optString("scheduledFor",
                                    o.optString("periodDate", ""));
            s.totalQuestions = o.optInt("totalQuestions", 0);
            s.answeredCount  = o.optInt("answeredCount", 0);
            s.completedAt    = o.optString("completedAt", "");
            JSONArray arr = o.optJSONArray("responses");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    try { s.responses.add(arr.getJSONObject(i)); }
                    catch (JSONException ignored) {}
                }
            }
            return s;
        }
    }

    // ─── List adapter ─────────────────────────────────────────────────────────

    class SessionListAdapter extends RecyclerView.Adapter<SessionListAdapter.VH> {

        private final List<SessionItem> items;
        SessionListAdapter(List<SessionItem> items) { this.items = items; }

        @Override
        public VH onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            View v = getLayoutInflater().inflate(R.layout.item_checkin_session, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(VH h, int position) { h.bind(items.get(position)); }

        @Override
        public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            final TextView periodBadge;
            final TextView statusBadge;
            final TextView sessionDate;
            final TextView sessionSubInfo;
            final MaterialButton btnAction;
            final LinearLayout accordionHeader;
            final TextView accordionChevron;
            final LinearLayout responsesContainer;
            boolean expanded = false;

            VH(View v) {
                super(v);
                periodBadge        = v.findViewById(R.id.period_badge);
                statusBadge        = v.findViewById(R.id.status_badge);
                sessionDate        = v.findViewById(R.id.session_date);
                sessionSubInfo     = v.findViewById(R.id.session_sub_info);
                btnAction          = v.findViewById(R.id.btn_action);
                accordionHeader    = v.findViewById(R.id.accordion_header);
                accordionChevron   = v.findViewById(R.id.accordion_chevron);
                responsesContainer = v.findViewById(R.id.responses_container);
            }

            void bind(SessionItem item) {
                expanded = false;
                responsesContainer.setVisibility(View.GONE);
                responsesContainer.removeAllViews();
                if (accordionChevron != null) accordionChevron.setRotation(90f);
                itemView.setAlpha(1f);

                periodBadge.setText(periodLabel(item.period));
                sessionDate.setText(formatDate(item.scheduledFor, item.period));

                switch (item.status) {
                    case "pending":
                        statusBadge.setText("Pending");
                        statusBadge.setTextColor(0xFF808080);
                        sessionSubInfo.setText(item.totalQuestions + " question"
                                + (item.totalQuestions != 1 ? "s" : ""));
                        sessionSubInfo.setTextColor(0xFF808080);
                        btnAction.setVisibility(View.VISIBLE);
                        btnAction.setText("Start");
                        accordionHeader.setVisibility(View.GONE);
                        btnAction.setOnClickListener(v -> openSession(item.sessionId));
                        break;

                    case "in_progress":
                        statusBadge.setText("In Progress");
                        statusBadge.setTextColor(0xFFFFA500);
                        sessionSubInfo.setText((item.totalQuestions - item.answeredCount)
                                + " of " + item.totalQuestions + " remaining");
                        sessionSubInfo.setTextColor(0xFF808080);
                        btnAction.setVisibility(View.VISIBLE);
                        btnAction.setText("Continue");
                        accordionHeader.setVisibility(View.GONE);
                        btnAction.setOnClickListener(v -> openSession(item.sessionId));
                        break;

                    case "completed":
                        statusBadge.setText("\u2713 Completed");
                        statusBadge.setTextColor(0xFF4CAF50);
                        sessionSubInfo.setText(item.totalQuestions + " question"
                                + (item.totalQuestions != 1 ? "s" : "") + " answered");
                        sessionSubInfo.setTextColor(0xFF606060);
                        btnAction.setVisibility(View.GONE);
                        accordionHeader.setVisibility(View.VISIBLE);
                        buildResponseRows(item);
                        accordionHeader.setOnClickListener(v -> {
                            expanded = !expanded;
                            responsesContainer.setVisibility(expanded ? View.VISIBLE : View.GONE);
                            if (accordionChevron != null)
                                accordionChevron.setRotation(expanded ? 270f : 90f);
                        });
                        break;

                    case "missed":
                        statusBadge.setText("\u2717 Missed");
                        statusBadge.setTextColor(0xFFE53935);
                        sessionSubInfo.setText("This check-in was not completed in time");
                        sessionSubInfo.setTextColor(0xFF606060);
                        btnAction.setVisibility(View.GONE);
                        accordionHeader.setVisibility(View.GONE);
                        itemView.setAlpha(0.6f);
                        break;

                    default:
                        statusBadge.setText(item.status);
                        statusBadge.setTextColor(0xFF808080);
                        sessionSubInfo.setText("");
                        btnAction.setVisibility(View.GONE);
                        accordionHeader.setVisibility(View.GONE);
                        break;
                }
            }

            private void buildResponseRows(SessionItem item) {
                responsesContainer.removeAllViews();
                if (item.responses.isEmpty()) return;

                View divider = new View(DailyCheckInActivity.this);
                LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1));
                dp.topMargin = dpToPx(8);
                dp.bottomMargin = dpToPx(10);
                divider.setLayoutParams(dp);
                divider.setBackgroundColor(0xFF2A2A2A);
                responsesContainer.addView(divider);

                for (JSONObject r : item.responses) {
                    LinearLayout row = new LinearLayout(DailyCheckInActivity.this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT);
                    rp.bottomMargin = dpToPx(8);
                    row.setLayoutParams(rp);

                    TextView tvQ = new TextView(DailyCheckInActivity.this);
                    tvQ.setLayoutParams(new LinearLayout.LayoutParams(
                            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                    tvQ.setTextColor(0xFFCCCCCC);
                    tvQ.setTextSize(13f);
                    tvQ.setText(r.optString("questionText", ""));
                    row.addView(tvQ);

                    TextView tvA = new TextView(DailyCheckInActivity.this);
                    tvA.setTextColor(0xFF008b8b);
                    tvA.setTextSize(13f);
                    tvA.setPadding(dpToPx(8), 0, 0, 0);
                    String emoji = r.optString("selectedEmoji", "");
                    String label = r.optString("selectedLabel", "");
                    tvA.setText(emoji.isEmpty() ? label : emoji + " " + label);
                    row.addView(tvA);

                    responsesContainer.addView(row);
                }
            }
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private String periodLabel(String period) {
        if (period == null) return "";
        switch (period) {
            case "weekly":      return "Weekly";
            case "monthly":     return "Monthly";
            case "semi_weekly": return "Bi-Weekly";
            default:
                return period.length() > 0
                        ? period.substring(0, 1).toUpperCase() + period.substring(1)
                        : period;
        }
    }

    private String formatDate(String isoDate, String period) {
        if (isoDate == null || isoDate.isEmpty()) return "";
        try {
            SimpleDateFormat parser = new SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault());
            parser.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = parser.parse(isoDate);
            if (date == null) return isoDate;

            switch (period == null ? "" : period) {
                case "monthly": {
                    return new SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(date);
                }
                case "semi_weekly":
                case "weekly": {
                    return new SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(date);
                }
                default: {
                    return new SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(date);
                }
            }
        } catch (Exception e) {
            return isoDate;
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}

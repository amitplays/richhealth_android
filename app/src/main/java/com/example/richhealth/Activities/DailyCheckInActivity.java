package com.example.richhealth.Activities;
import Utils.Utilities;

import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import com.android.volley.RequestQueue;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.example.richhealth.R;
import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import Models.SelectableOption;
import Utils.ApiConfig;
import Utils.CheckInNotificationHelper;
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

    // (2) Focus this week — single teal-tinted highlighted row.
    private LinearLayout focusSection;
    private TextView focusTitle;
    private TextView focusWhy;

    // (3) What's going well — win rows.
    private LinearLayout winsSection;
    private LinearLayout winsContainer;

    // (5) Sharpen your care ("Worth logging") — log-suggestion rows.
    private LinearLayout sharpenSection;
    private LinearLayout sharpenContainer;

    // (6) Rhythm — slim streak/completion row + sparkline.
    private LinearLayout rhythmSection;
    private TextView rhythmText;
    private LinearLayout rhythmSpark;

    // (7) Past reads — timeline list.
    private LinearLayout pastReadsSection;

    // (1) HERO — "Richie's read on you"
    private LinearLayout richieSection;
    private LinearLayout richieSafetyBanner;
    private TextView richieSafetyText;
    private TextView richieHeadline;
    private TextView richieOverallChip;
    private LinearLayout richieProcessing;
    private android.widget.ImageView richieProcessingLogo;
    private android.animation.ObjectAnimator richieProcessingSpinner;
    private LinearLayout richieReady;
    private LinearLayout richieFailed;
    private TextView richieUpdated;
    private TextView richieCouncilBadge;
    private TextView richieAnalysisText;
    private LinearLayout richieCouncilSection;
    private LinearLayout richieCouncilHeader;
    private LinearLayout richieCouncilContainer;
    private TextView richieCouncilChevron;
    private LinearLayout richieReasoningSection;
    private LinearLayout richieReasoningHeader;
    private TextView richieReasoningText;
    private TextView richieReasoningChevron;
    private MaterialButton richieRetryBtn;

    // (2) What Richie's watching — watchlist container.
    private LinearLayout watchlistSection;
    private LinearLayout watchlistContainer;

    /** Date shown in the hero header when generatedAt is absent. */
    private String lastCompletedDate = null;

    // Analysis polling
    private final Handler analysisHandler = new Handler(Looper.getMainLooper());
    private Runnable analysisPollRunnable;
    private long analysisPollStart;
    private String analysisSessionId;
    private RequestQueue analysisQueue;
    private boolean isForeground = true;
    private boolean analysisNotified = false;
    private static final long ANALYSIS_POLL_INTERVAL_MS = 4000L;
    private static final long ANALYSIS_POLL_TIMEOUT_MS  = 90000L;
    private static final String TAG_ANALYSIS = "checkin_analysis";

    // Status colors — paired with legend labels in the layout
    private static final int COLOR_COMPLETED   = 0xFF008B8B; // teal
    private static final int COLOR_MISSED      = 0xFFE53935; // red
    private static final int COLOR_IN_PROGRESS = 0xFFFFA500; // orange
    private static final int MAX_CELLS = 10;

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

        // (6) Rhythm, (7) Past reads
        rhythmSection      = findViewById(R.id.rhythm_section);
        rhythmText         = findViewById(R.id.rhythm_text);
        rhythmSpark        = findViewById(R.id.rhythm_spark);
        pastReadsSection   = findViewById(R.id.past_reads_section);

        // (2) Focus, (3) Wins, (4) Watchlist, (5) Sharpen your care
        focusSection       = findViewById(R.id.focus_section);
        focusTitle         = findViewById(R.id.focus_title);
        focusWhy           = findViewById(R.id.focus_why);
        winsSection        = findViewById(R.id.wins_section);
        winsContainer      = findViewById(R.id.wins_container);
        watchlistSection   = findViewById(R.id.watchlist_section);
        watchlistContainer = findViewById(R.id.watchlist_container);
        sharpenSection     = findViewById(R.id.sharpen_section);
        sharpenContainer   = findViewById(R.id.sharpen_container);

        // (1) HERO — "Richie's read on you"
        richieSection         = findViewById(R.id.richie_section);
        richieSafetyBanner    = findViewById(R.id.richie_safety_banner);
        richieSafetyText      = findViewById(R.id.richie_safety_text);
        richieHeadline        = findViewById(R.id.richie_headline);
        richieOverallChip     = findViewById(R.id.richie_overall_chip);
        richieProcessing      = findViewById(R.id.richie_processing);
        richieProcessingLogo  = findViewById(R.id.richie_processing_logo);

        // Spinning app logo for the "Richie is reviewing" state — same drawable +
        // rotation pattern as the list-loading spinner.
        richieProcessingSpinner = android.animation.ObjectAnimator
                .ofFloat(richieProcessingLogo, android.view.View.ROTATION, 0f, 360f);
        richieProcessingSpinner.setDuration(1200);
        richieProcessingSpinner.setRepeatCount(android.animation.ObjectAnimator.INFINITE);
        richieProcessingSpinner.setInterpolator(new android.view.animation.LinearInterpolator());
        richieReady           = findViewById(R.id.richie_ready);
        richieFailed          = findViewById(R.id.richie_failed);
        richieUpdated         = findViewById(R.id.richie_updated);
        richieCouncilBadge    = findViewById(R.id.richie_council_badge);
        richieAnalysisText    = findViewById(R.id.richie_analysis_text);
        richieCouncilSection  = findViewById(R.id.richie_council_section);
        richieCouncilHeader   = findViewById(R.id.richie_council_header);
        richieCouncilContainer= findViewById(R.id.richie_council_container);
        richieCouncilChevron  = findViewById(R.id.richie_council_chevron);
        richieReasoningSection= findViewById(R.id.richie_reasoning_section);
        richieReasoningHeader = findViewById(R.id.richie_reasoning_header);
        richieReasoningText   = findViewById(R.id.richie_reasoning_text);
        richieReasoningChevron= findViewById(R.id.richie_reasoning_chevron);
        richieRetryBtn        = findViewById(R.id.richie_retry_btn);

        richieCouncilHeader.setOnClickListener(v -> {
            boolean show = richieCouncilContainer.getVisibility() != View.VISIBLE;
            richieCouncilContainer.setVisibility(show ? View.VISIBLE : View.GONE);
            richieCouncilChevron.setRotation(show ? 270f : 90f);
        });
        richieReasoningHeader.setOnClickListener(v -> {
            boolean show = richieReasoningText.getVisibility() != View.VISIBLE;
            richieReasoningText.setVisibility(show ? View.VISIBLE : View.GONE);
            richieReasoningChevron.setRotation(show ? 270f : 90f);
        });
        richieRetryBtn.setOnClickListener(v -> {
            if (analysisSessionId != null) startAnalysisFor(analysisSessionId);
        });

        fetchCheckInList();
    }

    @Override
    protected void onResume() {
        super.onResume();
        isForeground = true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        isForeground = false;
    }

    @Override
    protected void onDestroy() {
        stopAnalysisPolling();
        stopProcessingSpinner();
        super.onDestroy();
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
        listLoadingState.setVisibility(View.VISIBLE);
        noAccessState.setVisibility(View.GONE);
        startBanner.setVisibility(View.GONE);
        emptyState.setVisibility(View.GONE);
        // Hide all content sections while the first fetch is in flight.
        richieSection.setVisibility(View.GONE);
        focusSection.setVisibility(View.GONE);
        winsSection.setVisibility(View.GONE);
        watchlistSection.setVisibility(View.GONE);
        sharpenSection.setVisibility(View.GONE);
        rhythmSection.setVisibility(View.GONE);
        pastReadsSection.setVisibility(View.GONE);
    }

    private void showNoAccess() {
        listLoadingSpinner.cancel();
        listLoadingState.setVisibility(View.GONE);
        noAccessState.setVisibility(View.VISIBLE);
        startBanner.setVisibility(View.GONE);
        emptyState.setVisibility(View.GONE);
        richieSection.setVisibility(View.GONE);
        focusSection.setVisibility(View.GONE);
        winsSection.setVisibility(View.GONE);
        watchlistSection.setVisibility(View.GONE);
        sharpenSection.setVisibility(View.GONE);
        rhythmSection.setVisibility(View.GONE);
        pastReadsSection.setVisibility(View.GONE);
    }

    private void showEmpty() {
        listLoadingSpinner.cancel();
        listLoadingState.setVisibility(View.GONE);
        noAccessState.setVisibility(View.GONE);
        // (0) Action zone: start banner when due; otherwise a quiet "no reads yet".
        startBanner.setVisibility(isDue ? View.VISIBLE : View.GONE);
        emptyState.setVisibility(isDue ? View.GONE : View.VISIBLE);
        if (!isDue && emptySubtitle != null) {
            emptySubtitle.setText(nextDueDate != null
                    ? "Your next check-in will be ready on " + formatDate(nextDueDate, "")
                    : "Your first check-in will appear here when it's ready.");
        }
        // No sessions → no hero / focus / wins / watchlist / sharpen / rhythm / past reads.
        pastReadsSection.setVisibility(View.GONE);
        rhythmSection.setVisibility(View.GONE);
        populateSummary(sessionItems);
    }

    private void showList() {
        listLoadingSpinner.cancel();
        listLoadingState.setVisibility(View.GONE);
        noAccessState.setVisibility(View.GONE);
        emptyState.setVisibility(View.GONE);
        // (0) Action zone: start banner at top when due, even alongside history.
        startBanner.setVisibility(isDue ? View.VISIBLE : View.GONE);
        // (7) Past reads — newest-first timeline.
        pastReadsSection.setVisibility(View.VISIBLE);
        sortSessionsDescending();
        listAdapter.notifyDataSetChanged();
        // Builds hero (1), watchlist (2), rhythm (4) from real data.
        populateSummary(sessionItems);
    }

    /** Orders the adapter-backing list newest → oldest for the timeline. */
    private void sortSessionsDescending() {
        Collections.sort(sessionItems, (a, b) -> Long.compare(sessionTime(b), sessionTime(a)));
    }

    // ─── Check-in summary (streak + consistency grid + last check-in) ───────────

    /**
     * Rebuilds the rhythm row (4) and drives the hero analysis (1) from the
     * already-parsed session list. Fully client-side, deterministic.
     * Watchlist (2) is populated later from the analysis response.
     */
    private void populateSummary(List<SessionItem> items) {
        if (items == null || items.isEmpty()) {
            if (rhythmSection != null) rhythmSection.setVisibility(View.GONE);
            hideRichie();
            stopAnalysisPolling();
            analysisSessionId = null;
            lastCompletedDate = null;
            return;
        }

        // Chronological order (oldest → newest); stable, deterministic.
        List<SessionItem> sorted = new ArrayList<>(items);
        Collections.sort(sorted, (a, b) -> Long.compare(sessionTime(a), sessionTime(b)));

        // (4) RHYTHM — slim streak + completion + sparkline.
        buildRhythm(sorted);

        // (1) HERO — only when there's a completed session Richie can review.
        SessionItem lastCompleted = latestCompleted(sorted);
        if (lastCompleted == null) {
            hideRichie();
            stopAnalysisPolling();
            analysisSessionId = null;
            lastCompletedDate = null;
        } else {
            lastCompletedDate = (lastCompleted.completedAt != null && !lastCompleted.completedAt.isEmpty())
                    ? lastCompleted.completedAt : lastCompleted.scheduledFor;
            syncAnalysis(lastCompleted.sessionId);
        }
    }

    /** Rhythm row: "{streak} in a row · {pct}% kept" + a capped status sparkline. */
    private void buildRhythm(List<SessionItem> sortedAsc) {
        if (rhythmSection == null) return;
        rhythmSection.setVisibility(View.VISIBLE);

        int streak = computeStreak(sortedAsc);
        int completed = 0, missed = 0;
        for (SessionItem s : sortedAsc) {
            if ("completed".equals(s.status)) completed++;
            else if ("missed".equals(s.status)) missed++;
        }
        int denom = completed + missed;
        int pct = denom == 0 ? 0 : Math.round((float) completed / denom * 100f);
        if (rhythmText != null) {
            rhythmText.setText(streak + " in a row · " + pct + "% kept");
        }
        buildSparkline(sortedAsc);
    }

    /** Small colored bars from the most recent statuses, capped at MAX_CELLS. */
    private void buildSparkline(List<SessionItem> sortedAsc) {
        if (rhythmSpark == null) return;
        rhythmSpark.removeAllViews();

        int start = Math.max(0, sortedAsc.size() - MAX_CELLS);
        int barW = dpToPx(8);
        int barH = dpToPx(18);
        int gap = dpToPx(4);

        for (int i = start; i < sortedAsc.size(); i++) {
            SessionItem s = sortedAsc.get(i);
            View bar = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(barW, barH);
            if (i > start) lp.setMarginStart(gap);
            bar.setLayoutParams(lp);
            bar.setBackgroundResource(R.drawable.checkin_cell_bg);
            bar.setBackgroundTintList(ColorStateList.valueOf(sparkColor(s.status)));
            bar.setContentDescription(sessionStatusLabel(s.status));
            rhythmSpark.addView(bar);
        }
    }

    private int sparkColor(String status) {
        if ("completed".equals(status)) return COLOR_COMPLETED; // teal
        if ("missed".equals(status))    return COLOR_MISSED;    // red
        return 0xFFFFC107;                                      // amber (pending/in_progress)
    }

    /**
     * Number of consecutive completed cycles counting back from the most recent.
     * A single leading still-open (pending/in_progress) cycle is skipped without
     * breaking the streak; the first "missed" (or a gap after counting starts)
     * stops it. Input must be sorted oldest → newest.
     */
    private int computeStreak(List<SessionItem> sortedAsc) {
        int streak = 0;
        for (int i = sortedAsc.size() - 1; i >= 0; i--) {
            String status = sortedAsc.get(i).status;
            if ("completed".equals(status)) {
                streak++;
            } else if ("pending".equals(status) || "in_progress".equals(status)) {
                if (streak == 0) continue; // leading open cycle — skip, don't break
                break;                      // open cycle after a run — stop counting
            } else { // missed / unknown
                break;
            }
        }
        return streak;
    }

    /** Most recent completed session; null if none. */
    private SessionItem latestCompleted(List<SessionItem> sortedAsc) {
        for (int i = sortedAsc.size() - 1; i >= 0; i--) {
            SessionItem s = sortedAsc.get(i);
            if ("completed".equals(s.status) && s.sessionId != null && !s.sessionId.isEmpty()) {
                return s;
            }
        }
        return null;
    }

    // ─── What Richie thinks: analysis fetch + poll ──────────────────────────────

    private RequestQueue getAnalysisQueue() {
        if (analysisQueue == null) analysisQueue = Volley.newRequestQueue(getApplicationContext());
        return analysisQueue;
    }

    /** Kick off analysis tracking for a session only if not already tracking it. */
    private void syncAnalysis(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) { hideRichie(); return; }
        if (sessionId.equals(analysisSessionId)) return; // already tracking/handled
        startAnalysisFor(sessionId);
    }

    /** (Re)start polling for the given session's analysis. */
    private void startAnalysisFor(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) { hideRichie(); return; }
        stopAnalysisPolling();
        analysisSessionId = sessionId;
        analysisNotified = false;
        analysisPollStart = System.currentTimeMillis();
        showRichieProcessing();
        fetchAnalysis(sessionId);
    }

    private void fetchAnalysis(String sessionId) {
        String token = tokenManager != null ? tokenManager.getToken() : null;
        if (token == null) { hideRichie(); return; }

        String url = ApiConfig.BASE_URL + "/api/checkin/sessions/" + sessionId + "/analysis";
        StringRequest req = new StringRequest(Request.Method.GET, url,
                response -> handleAnalysisResponse(sessionId, response),
                error -> handleAnalysisError(sessionId, error)) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> h = new HashMap<>();
                h.put("Authorization", "Bearer " + token);
                return h;
            }
        };
        req.setTag(TAG_ANALYSIS);
        req.setRetryPolicy(new DefaultRetryPolicy(15000, 1, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        getAnalysisQueue().add(req);
    }

    private void handleAnalysisResponse(String sessionId, String response) {
        if (!sessionId.equals(analysisSessionId)) return; // stale response
        try {
            JSONObject json = new JSONObject(response);
            String status = json.optString("status", "none");
            switch (status) {
                case "ready":
                    renderAnalysisReady(json);
                    stopAnalysisPolling();
                    break;
                case "failed":
                    showRichieFailed();
                    stopAnalysisPolling();
                    break;
                case "processing":
                case "none":
                default:
                    showRichieProcessing();
                    scheduleNextPoll(sessionId, status);
                    break;
            }
        } catch (JSONException e) {
            Log.e(TAG, "Analysis parse error", e);
            scheduleNextPoll(sessionId, "processing");
        }
    }

    private void handleAnalysisError(String sessionId, VolleyError error) {
        if (!sessionId.equals(analysisSessionId)) return;
        Log.e(TAG, "Analysis fetch error", error);
        scheduleNextPoll(sessionId, "processing"); // transient — keep trying until timeout
    }

    private void scheduleNextPoll(String sessionId, String lastStatus) {
        if (System.currentTimeMillis() - analysisPollStart >= ANALYSIS_POLL_TIMEOUT_MS) {
            // Bounded: give up quietly on "none", surface a retry otherwise.
            if ("none".equals(lastStatus)) hideRichie();
            else showRichieFailed();
            stopAnalysisPolling();
            return;
        }
        analysisPollRunnable = () -> fetchAnalysis(sessionId);
        analysisHandler.postDelayed(analysisPollRunnable, ANALYSIS_POLL_INTERVAL_MS);
    }

    private void stopAnalysisPolling() {
        if (analysisPollRunnable != null) {
            analysisHandler.removeCallbacks(analysisPollRunnable);
            analysisPollRunnable = null;
        }
        if (analysisQueue != null) analysisQueue.cancelAll(TAG_ANALYSIS);
    }

    private void renderAnalysisReady(JSONObject json) {
        showRichieReady();

        // Urgent safety banner at the top of the hero (only when safety.urgent).
        applySafety(json.optJSONObject("safety"));

        // Overall health chip (distinct from lifecycle status).
        applyOverallChip(json.optString("overall", ""));

        // Prominent headline above the analysis body.
        String headline = json.optString("headline", "");
        if ("null".equals(headline)) headline = "";
        if (richieHeadline != null) {
            if (!headline.trim().isEmpty()) {
                richieHeadline.setText(headline.trim());
                richieHeadline.setVisibility(View.VISIBLE);
            } else {
                richieHeadline.setVisibility(View.GONE);
            }
        }

        String analysis = json.optString("analysis", "");
        if (richieAnalysisText != null) richieAnalysisText.setText(analysis);

        // Header subline: "Updated · {date}" from generatedAt, else the completion date.
        if (richieUpdated != null) {
            String generatedAt = json.optString("generatedAt", "");
            if ("null".equals(generatedAt)) generatedAt = "";
            String src = !generatedAt.isEmpty() ? generatedAt : lastCompletedDate;
            String when = formatDate(src, "");
            String line = (when != null && !when.isEmpty()) ? "Updated · " + when : "Updated";
            // When not due, fold the next-due date into the hero subline.
            if (!isDue && nextDueDate != null) {
                String next = formatDate(nextDueDate, "");
                if (next != null && !next.isEmpty()) line += "  ·  Next " + next;
            }
            richieUpdated.setText(line);
        }

        boolean isCouncil = json.optBoolean("isCouncil", false);
        JSONArray council = json.optJSONArray("council");

        // Council badge: "◆ N perspectives" when a council was used.
        if (richieCouncilBadge != null) {
            if (isCouncil && council != null && council.length() > 0) {
                richieCouncilBadge.setText("◆ " + council.length() + " perspectives");
                richieCouncilBadge.setVisibility(View.VISIBLE);
            } else {
                richieCouncilBadge.setVisibility(View.GONE);
            }
        }

        if (isCouncil && council != null && council.length() > 0) {
            richieReasoningSection.setVisibility(View.GONE);
            richieCouncilSection.setVisibility(View.VISIBLE);
            buildCouncil(council);
        } else {
            richieCouncilSection.setVisibility(View.GONE);
            String thinking = json.optString("thinking", "");
            if (thinking != null && !thinking.trim().isEmpty()) {
                richieReasoningSection.setVisibility(View.VISIBLE);
                richieReasoningText.setText(thinking);
                richieReasoningText.setVisibility(View.GONE); // collapsed by default
                richieReasoningChevron.setRotation(90f);
            } else {
                richieReasoningSection.setVisibility(View.GONE);
            }
        }

        // (2) FOCUS — single highlighted "focus this week" row.
        buildFocus(json.optJSONObject("focus"));

        // (3) WINS — "what's going well" rows.
        buildWins(json.optJSONArray("wins"));

        // (4) WHAT RICHIE'S WATCHING — built from the analysis watchlist array.
        buildWatchlist(json.optJSONArray("watchlist"));

        // (5) SHARPEN YOUR CARE — "worth logging" rows (display-only).
        buildLogSuggestions(json.optJSONArray("logSuggestions"));

        // Notify only if the review finished while the screen wasn't in the foreground.
        if (!isForeground && !analysisNotified) {
            analysisNotified = true;
            CheckInNotificationHelper.fireAnalysisReady(this);
        }
    }

    /**
     * (2) Builds the watchlist rows in code. Hidden entirely when empty/null so
     * the section never renders as a hollow box.
     */
    private void buildWatchlist(JSONArray watchlist) {
        if (watchlistSection == null || watchlistContainer == null) return;
        watchlistContainer.removeAllViews();

        if (watchlist == null || watchlist.length() == 0) {
            watchlistSection.setVisibility(View.GONE);
            return;
        }

        int rows = 0;
        for (int i = 0; i < watchlist.length(); i++) {
            JSONObject w = watchlist.optJSONObject(i);
            if (w == null) continue;
            String signal = w.optString("signal", "").trim();
            String note   = w.optString("note", "").trim();
            String status = w.optString("status", "watch").trim().toLowerCase(Locale.US);
            if (signal.isEmpty()) continue;

            int color;
            String tag;
            if ("ok".equals(status))            { color = 0xFF4CAF50; tag = "On track"; }
            else if ("attention".equals(status)){ color = 0xFFE53935; tag = "Attention"; }
            else                                { color = 0xFFFFC107; tag = "Watch"; }

            if (rows > 0) {
                View divider = new View(this);
                LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1));
                divider.setLayoutParams(dl);
                divider.setBackgroundColor(0xFF2A2A2A);
                watchlistContainer.addView(divider);
            }

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.TOP);
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            row.setLayoutParams(rp);
            row.setPadding(dpToPx(14), dpToPx(14), dpToPx(14), dpToPx(14));

            // status dot (paired with the tag label below for accessibility)
            View dot = new View(this);
            LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(dpToPx(9), dpToPx(9));
            dp.topMargin = dpToPx(5);
            dp.setMarginEnd(dpToPx(12));
            dot.setLayoutParams(dp);
            dot.setBackgroundResource(R.drawable.checkin_dot_bg);
            dot.setBackgroundTintList(ColorStateList.valueOf(color));
            dot.setContentDescription(tag);
            row.addView(dot);

            // body: signal + note
            LinearLayout body = new LinearLayout(this);
            body.setOrientation(LinearLayout.VERTICAL);
            body.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView sig = new TextView(this);
            sig.setText(signal);
            sig.setTextColor(0xFFFFFFFF);
            sig.setTextSize(14.5f);
            sig.setTypeface(null, Typeface.BOLD);
            body.addView(sig);

            if (!note.isEmpty()) {
                TextView nt = new TextView(this);
                nt.setText(note);
                nt.setTextColor(0xFF9E9E9E);
                nt.setTextSize(13f);
                nt.setLineSpacing(dpToPx(1), 1f);
                nt.setPadding(0, dpToPx(2), 0, 0);
                body.addView(nt);
            }
            row.addView(body);

            // status tag chip
            TextView chip = makeStatusChip(tag, color);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            cp.setMarginStart(dpToPx(10));
            chip.setLayoutParams(cp);
            row.addView(chip);

            watchlistContainer.addView(row);
            rows++;
        }

        watchlistSection.setVisibility(rows > 0 ? View.VISIBLE : View.GONE);
    }

    /** A small rounded status chip: color-tinted low-alpha fill + colored bold text. */
    private TextView makeStatusChip(String text, int color) {
        TextView chip = new TextView(this);
        chip.setText(text);
        chip.setTextColor(color);
        chip.setTextSize(11f);
        chip.setTypeface(null, Typeface.BOLD);
        chip.setAllCaps(true);
        chip.setPadding(dpToPx(8), dpToPx(3), dpToPx(8), dpToPx(3));

        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dpToPx(999));
        bg.setColor((color & 0x00FFFFFF) | 0x24000000); // ~14% alpha of the status color
        chip.setBackground(bg);
        return chip;
    }

    /**
     * Urgent safety banner at the top of the hero. Shown only when the safety
     * object exists, is flagged urgent, and carries a non-empty message.
     */
    private void applySafety(JSONObject safety) {
        if (richieSafetyBanner == null) return;
        boolean urgent = safety != null && safety.optBoolean("urgent", false);
        String message = safety != null ? safety.optString("message", "") : "";
        if ("null".equals(message)) message = "";
        if (urgent && !message.trim().isEmpty()) {
            if (richieSafetyText != null) richieSafetyText.setText(message.trim());
            richieSafetyBanner.setVisibility(View.VISIBLE);
        } else {
            richieSafetyBanner.setVisibility(View.GONE);
        }
    }

    /**
     * Overall health chip: good→green "On track", steady→teal "Steady",
     * watch→amber "Watch", attention→red "Attention". Hidden when unknown/empty.
     */
    private void applyOverallChip(String overall) {
        if (richieOverallChip == null) return;
        String o = overall == null ? "" : overall.trim().toLowerCase(Locale.US);
        String label;
        int color;
        switch (o) {
            case "good":      label = "On track";  color = 0xFF4CAF50; break; // green
            case "steady":    label = "Steady";    color = 0xFF008B8B; break; // teal
            case "watch":     label = "Watch";     color = 0xFFFFC107; break; // amber
            case "attention": label = "Attention"; color = 0xFFE53935; break; // red
            default:
                richieOverallChip.setVisibility(View.GONE);
                return;
        }
        applyPill(richieOverallChip, label, color);
        richieOverallChip.setVisibility(View.VISIBLE);
    }

    /**
     * (2) FOCUS — single teal-tinted highlighted row. Hidden entirely when the
     * focus object is missing or its title is empty.
     */
    private void buildFocus(JSONObject focus) {
        if (focusSection == null) return;
        String title = focus != null ? focus.optString("title", "").trim() : "";
        String why   = focus != null ? focus.optString("why", "").trim() : "";
        if (focus == null || title.isEmpty()) {
            focusSection.setVisibility(View.GONE);
            return;
        }
        if (focusTitle != null) focusTitle.setText(title);
        if (focusWhy != null) {
            if (!why.isEmpty()) {
                focusWhy.setText(why);
                focusWhy.setVisibility(View.VISIBLE);
            } else {
                focusWhy.setVisibility(View.GONE);
            }
        }
        focusSection.setVisibility(View.VISIBLE);
    }

    /**
     * (3) WINS — "what's going well" rows (green check + text). Hidden entirely
     * when the array is null/empty or holds no usable strings.
     */
    private void buildWins(JSONArray wins) {
        if (winsSection == null || winsContainer == null) return;
        winsContainer.removeAllViews();
        if (wins == null || wins.length() == 0) {
            winsSection.setVisibility(View.GONE);
            return;
        }

        int rows = 0;
        for (int i = 0; i < wins.length(); i++) {
            String win = wins.optString(i, "").trim();
            if (win.isEmpty()) continue;

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.TOP);
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            if (rows > 0) rp.topMargin = dpToPx(6);
            row.setLayoutParams(rp);

            TextView check = new TextView(this);
            check.setText("✓"); // ✓
            check.setTextColor(0xFF4CAF50); // green
            check.setTextSize(14f);
            check.setTypeface(null, Typeface.BOLD);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            cp.setMarginEnd(dpToPx(10));
            check.setLayoutParams(cp);
            row.addView(check);

            TextView txt = new TextView(this);
            txt.setText(win);
            txt.setTextColor(0xFFC7C9CE);
            txt.setTextSize(14f);
            txt.setLineSpacing(dpToPx(2), 1f);
            txt.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(txt);

            winsContainer.addView(row);
            rows++;
        }
        winsSection.setVisibility(rows > 0 ? View.VISIBLE : View.GONE);
    }

    /**
     * (5) SHARPEN YOUR CARE — "worth logging" rows built from logSuggestions.
     * Display-only (no tap navigation). Hidden entirely when empty.
     */
    private void buildLogSuggestions(JSONArray logSuggestions) {
        if (sharpenSection == null || sharpenContainer == null) return;
        sharpenContainer.removeAllViews();
        if (logSuggestions == null || logSuggestions.length() == 0) {
            sharpenSection.setVisibility(View.GONE);
            return;
        }

        int rows = 0;
        for (int i = 0; i < logSuggestions.length(); i++) {
            JSONObject s = logSuggestions.optJSONObject(i);
            if (s == null) continue;
            String label = s.optString("label", "").trim();
            String why   = s.optString("why", "").trim();
            if (label.isEmpty()) continue;

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            if (rows > 0) rp.topMargin = dpToPx(9);
            row.setLayoutParams(rp);
            row.setPadding(dpToPx(13), dpToPx(11), dpToPx(13), dpToPx(11));

            // Neutral rounded surface with a hairline border (matches the mock).
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            bg.setCornerRadius(dpToPx(12));
            bg.setColor(0xFF0E0E0F);
            bg.setStroke(dpToPx(1), 0xFF2A2A2A);
            row.setBackground(bg);

            TextView icon = new TextView(this);
            icon.setText("＋"); // ＋
            icon.setTextColor(0xFF008B8B); // teal
            icon.setTextSize(14f);
            icon.setTypeface(null, Typeface.BOLD);
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            ip.setMarginEnd(dpToPx(10));
            icon.setLayoutParams(ip);
            row.addView(icon);

            LinearLayout body = new LinearLayout(this);
            body.setOrientation(LinearLayout.VERTICAL);
            body.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView lbl = new TextView(this);
            lbl.setText(label);
            lbl.setTextColor(0xFFFFFFFF);
            lbl.setTextSize(14f);
            body.addView(lbl);

            if (!why.isEmpty()) {
                TextView nt = new TextView(this);
                nt.setText(why);
                nt.setTextColor(0xFF9E9E9E);
                nt.setTextSize(12f);
                nt.setLineSpacing(dpToPx(1), 1f);
                nt.setPadding(0, dpToPx(1), 0, 0);
                body.addView(nt);
            }
            row.addView(body);

            sharpenContainer.addView(row);
            rows++;
        }
        sharpenSection.setVisibility(rows > 0 ? View.VISIBLE : View.GONE);
    }

    private void buildCouncil(JSONArray council) {
        if (richieCouncilContainer == null) return;
        richieCouncilContainer.removeAllViews();
        // Reset to collapsed state each time it's rebuilt.
        richieCouncilContainer.setVisibility(View.GONE);
        if (richieCouncilChevron != null) richieCouncilChevron.setRotation(90f);

        for (int i = 0; i < council.length(); i++) {
            JSONObject m = council.optJSONObject(i);
            if (m == null) continue;
            String label = m.optString("label", m.optString("model", ""));
            String text  = m.optString("text", "");
            if (label.trim().isEmpty() && text.trim().isEmpty()) continue;

            LinearLayout block = new LinearLayout(this);
            block.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            bp.bottomMargin = dpToPx(12);
            block.setLayoutParams(bp);

            TextView lbl = new TextView(this);
            lbl.setText(label.toUpperCase(Locale.getDefault()));
            lbl.setTextColor(COLOR_COMPLETED); // teal
            lbl.setTextSize(12f);
            lbl.setTypeface(null, Typeface.BOLD);
            block.addView(lbl);

            TextView txt = new TextView(this);
            txt.setText(text);
            txt.setTextColor(0xFFBBBBBB);
            txt.setTextSize(13f);
            txt.setLineSpacing(dpToPx(2), 1f);
            txt.setPadding(0, dpToPx(2), 0, 0);
            block.addView(txt);

            richieCouncilContainer.addView(block);
        }
    }

    private void showRichieProcessing() {
        if (richieSection == null) return;
        richieSection.setVisibility(View.VISIBLE);
        richieProcessing.setVisibility(View.VISIBLE);
        richieReady.setVisibility(View.GONE);
        richieFailed.setVisibility(View.GONE);
        // Focus / wins / watchlist / sharpen arrive with the ready analysis — nothing yet.
        hideAnalysisSections();
        startProcessingSpinner();
    }

    private void showRichieReady() {
        if (richieSection == null) return;
        richieSection.setVisibility(View.VISIBLE);
        richieProcessing.setVisibility(View.GONE);
        richieReady.setVisibility(View.VISIBLE);
        richieFailed.setVisibility(View.GONE);
        stopProcessingSpinner();
    }

    private void showRichieFailed() {
        if (richieSection == null) return;
        richieSection.setVisibility(View.VISIBLE);
        richieProcessing.setVisibility(View.GONE);
        richieReady.setVisibility(View.GONE);
        richieFailed.setVisibility(View.VISIBLE);
        hideAnalysisSections();
        stopProcessingSpinner();
    }

    private void hideRichie() {
        if (richieSection != null) richieSection.setVisibility(View.GONE);
        hideAnalysisSections();
        stopProcessingSpinner();
    }

    /** Hides every section fed by the ready analysis (focus/wins/watchlist/sharpen). */
    private void hideAnalysisSections() {
        if (focusSection != null) focusSection.setVisibility(View.GONE);
        if (winsSection != null) winsSection.setVisibility(View.GONE);
        if (watchlistSection != null) watchlistSection.setVisibility(View.GONE);
        if (sharpenSection != null) sharpenSection.setVisibility(View.GONE);
    }

    private void startProcessingSpinner() {
        if (richieProcessingSpinner != null && !richieProcessingSpinner.isStarted()) {
            richieProcessingSpinner.start();
        }
    }

    private void stopProcessingSpinner() {
        if (richieProcessingSpinner != null && richieProcessingSpinner.isStarted()) {
            richieProcessingSpinner.cancel();
        }
        if (richieProcessingLogo != null) richieProcessingLogo.setRotation(0f);
    }

    private String sessionStatusLabel(String status) {
        if ("completed".equals(status)) return "Completed";
        if ("missed".equals(status)) return "Missed";
        if ("in_progress".equals(status)) return "In Progress";
        return "Pending";
    }

    /** Parses a session's date to epoch millis for ordering; 0 when unparseable. */
    private long sessionTime(SessionItem s) {
        String iso = s.scheduledFor;
        if (iso == null || iso.isEmpty()) return 0L;
        try {
            SimpleDateFormat parser = new SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault());
            parser.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date d = parser.parse(iso);
            return d == null ? 0L : d.getTime();
        } catch (Exception e) {
            return 0L;
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
            final View timelineDot;
            final TextView sessionWhen;
            final TextView sessionSummary;
            final TextView statusPill;
            final MaterialButton btnAction;
            final LinearLayout accordionHeader;
            final TextView accordionChevron;
            final LinearLayout responsesContainer;
            boolean expanded = false;

            VH(View v) {
                super(v);
                timelineDot        = v.findViewById(R.id.timeline_dot);
                sessionWhen        = v.findViewById(R.id.session_when);
                sessionSummary     = v.findViewById(R.id.session_summary);
                statusPill         = v.findViewById(R.id.status_pill);
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
                btnAction.setVisibility(View.GONE);
                accordionHeader.setVisibility(View.GONE);

                // Timeline dot color: teal completed / red missed / amber otherwise.
                timelineDot.setBackgroundTintList(ColorStateList.valueOf(sparkColor(item.status)));

                // "period \u00b7 date"
                String period = periodLabel(item.period);
                String date = formatDate(item.scheduledFor, item.period);
                sessionWhen.setText(date.isEmpty() ? period : period + " \u00b7 " + date);

                switch (item.status) {
                    case "pending":
                        sessionSummary.setText(item.totalQuestions > 0
                                ? item.totalQuestions + " question"
                                        + (item.totalQuestions != 1 ? "s" : "") + " waiting"
                                : "Not started");
                        applyPill(statusPill, "Pending", 0xFF9E9E9E);
                        btnAction.setVisibility(View.VISIBLE);
                        btnAction.setText("Start");
                        btnAction.setOnClickListener(v -> openSession(item.sessionId));
                        break;

                    case "in_progress":
                        sessionSummary.setText((item.totalQuestions - item.answeredCount)
                                + " of " + item.totalQuestions + " remaining");
                        applyPill(statusPill, "In Progress", 0xFFFFC107);
                        btnAction.setVisibility(View.VISIBLE);
                        btnAction.setText("Continue");
                        btnAction.setOnClickListener(v -> openSession(item.sessionId));
                        break;

                    case "completed":
                        sessionSummary.setText(summaryLine(item));
                        applyPill(statusPill, "Completed", 0xFF4CAF50);
                        if (!item.responses.isEmpty()) {
                            accordionHeader.setVisibility(View.VISIBLE);
                            buildResponseRows(item);
                            accordionHeader.setOnClickListener(v -> {
                                expanded = !expanded;
                                responsesContainer.setVisibility(expanded ? View.VISIBLE : View.GONE);
                                if (accordionChevron != null)
                                    accordionChevron.setRotation(expanded ? 270f : 90f);
                            });
                        }
                        break;

                    case "missed":
                        sessionSummary.setText("No read \u2014 cycle missed");
                        applyPill(statusPill, "Missed", 0xFFE53935);
                        itemView.setAlpha(0.6f);
                        break;

                    default:
                        sessionSummary.setText(sessionStatusLabel(item.status));
                        applyPill(statusPill, sessionStatusLabel(item.status), 0xFF9E9E9E);
                        break;
                }
            }

            private void buildResponseRows(SessionItem item) {
                responsesContainer.removeAllViews();
                if (item.responses.isEmpty()) return;

                int n = 0;
                for (JSONObject r : item.responses) {
                    LinearLayout block = new LinearLayout(DailyCheckInActivity.this);
                    block.setOrientation(LinearLayout.VERTICAL);
                    LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT);
                    if (n > 0) bp.topMargin = dpToPx(11);
                    block.setLayoutParams(bp);

                    TextView tvQ = new TextView(DailyCheckInActivity.this);
                    tvQ.setTextColor(0xFF9E9E9E);
                    tvQ.setTextSize(12f);
                    tvQ.setText(r.optString("questionText", ""));
                    block.addView(tvQ);

                    TextView tvA = new TextView(DailyCheckInActivity.this);
                    tvA.setTextColor(0xFFFFFFFF);
                    tvA.setTextSize(14f);
                    tvA.setPadding(0, dpToPx(2), 0, 0);
                    String emoji = r.optString("selectedEmoji", "");
                    String label = r.optString("selectedLabel", "");
                    if (label.isEmpty()) label = r.optString("selectedValue", "");
                    tvA.setText(emoji.isEmpty() ? label : emoji + " " + label);
                    block.addView(tvA);

                    responsesContainer.addView(block);
                    n++;
                }
            }
        }
    }

    /**
     * Past-reads summary line for a completed session: up to 3 distinct response
     * categories, title-cased, joined with " · ". Falls back to a count when no
     * categories are present. Purely from real response data.
     */
    private String summaryLine(SessionItem item) {
        List<String> cats = new ArrayList<>();
        for (JSONObject r : item.responses) {
            String c = r.optString("category", "").trim();
            if (c.isEmpty()) continue;
            String tc = titleCase(c);
            boolean dup = false;
            for (String existing : cats) if (existing.equalsIgnoreCase(tc)) { dup = true; break; }
            if (!dup) cats.add(tc);
            if (cats.size() >= 3) break;
        }
        if (!cats.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < cats.size(); i++) {
                if (i > 0) sb.append(" · ");
                sb.append(cats.get(i));
            }
            return sb.toString();
        }
        int n = item.answeredCount > 0 ? item.answeredCount : item.totalQuestions;
        return n + " answered";
    }

    /** Title-cases a category token: "medication_adherence" → "Medication Adherence". */
    private String titleCase(String s) {
        String cleaned = s.replace('_', ' ').replace('-', ' ').trim();
        if (cleaned.isEmpty()) return cleaned;
        String[] words = cleaned.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0)));
            if (w.length() > 1) sb.append(w.substring(1).toLowerCase(Locale.US));
        }
        return sb.toString();
    }

    /** Styles an existing pill TextView: colored bold text on a low-alpha rounded fill. */
    private void applyPill(TextView pill, String text, int color) {
        pill.setText(text);
        pill.setTextColor(color);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dpToPx(999));
        bg.setColor((color & 0x00FFFFFF) | 0x24000000); // ~14% alpha
        pill.setBackground(bg);
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

package com.example.richhealth.Activities;
import Utils.Utilities;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
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

    // Summary views (replaces old bar chart)
    private LinearLayout streakSection;
    private TextView streakNumber;
    private TextView streakLabel;
    private LinearLayout consistencyGrid;
    private FrameLayout ringContainer;
    private CompletionRingView completionRing;
    private TextView completionSummary;
    private ImageButton checkinInfoBtn;

    // "What Richie thinks" section
    private LinearLayout richieSection;
    private LinearLayout richieProcessing;
    private LinearLayout richieReady;
    private LinearLayout richieFailed;
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

        streakSection      = findViewById(R.id.streak_section);
        streakNumber       = findViewById(R.id.streak_number);
        streakLabel        = findViewById(R.id.streak_label);
        consistencyGrid    = findViewById(R.id.consistency_grid);
        ringContainer      = findViewById(R.id.ring_container);
        completionSummary  = findViewById(R.id.completion_summary);
        checkinInfoBtn     = findViewById(R.id.checkin_info_btn);

        // Completion ring is drawn programmatically into its container.
        completionRing = new CompletionRingView(this);
        ringContainer.addView(completionRing,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));

        if (checkinInfoBtn != null) checkinInfoBtn.setOnClickListener(v -> showCheckinInfoDialog());

        // "What Richie thinks" views
        richieSection         = findViewById(R.id.richie_section);
        richieProcessing      = findViewById(R.id.richie_processing);
        richieReady           = findViewById(R.id.richie_ready);
        richieFailed          = findViewById(R.id.richie_failed);
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
        super.onDestroy();
    }

    private void showCheckinInfoDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Why check in?")
                .setMessage("Your check-in answers tune Richie — they sharpen your "
                        + "chat replies, health analysis, and the questions you get next.")
                .setPositiveButton("Got it", null)
                .show();
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
        // No sessions yet — hide the summary
        populateSummary(sessionItems);
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
        populateSummary(sessionItems);
    }

    // ─── Check-in summary (streak + consistency grid + last check-in) ───────────

    /**
     * Rebuilds the streak/consistency section and the "last check-in" chips
     * from the already-parsed session list. Fully client-side, deterministic.
     */
    private void populateSummary(List<SessionItem> items) {
        if (items == null || items.isEmpty()) {
            if (streakSection != null) streakSection.setVisibility(View.GONE);
            hideRichie();
            stopAnalysisPolling();
            analysisSessionId = null;
            return;
        }

        // Chronological order (oldest → newest); stable, deterministic.
        List<SessionItem> sorted = new ArrayList<>(items);
        Collections.sort(sorted, (a, b) -> Long.compare(sessionTime(a), sessionTime(b)));

        // (A) Completion ring + streak + consistency grid
        if (streakSection != null) {
            streakSection.setVisibility(View.VISIBLE);
            int streak = computeStreak(sorted);
            if (streakNumber != null) streakNumber.setText(String.valueOf(streak));
            if (streakLabel != null) {
                streakLabel.setText(streak == 0 ? "Start your streak" : "check-in streak");
            }
            updateCompletionRing(sorted);
            buildConsistencyGrid(sorted);
        }

        // (B) What Richie thinks — only when there's a completed session.
        SessionItem lastCompleted = latestCompleted(sorted);
        if (lastCompleted == null) {
            hideRichie();
            stopAnalysisPolling();
            analysisSessionId = null;
        } else {
            syncAnalysis(lastCompleted.sessionId);
        }
    }

    /** Completed vs missed ratio → tinted arc + centered percent. */
    private void updateCompletionRing(List<SessionItem> sortedAsc) {
        int completed = 0, missed = 0;
        for (SessionItem s : sortedAsc) {
            if ("completed".equals(s.status)) completed++;
            else if ("missed".equals(s.status)) missed++;
        }
        int denom = Math.max(1, completed + missed);
        float rate = (float) completed / denom;
        int pct = Math.round(rate * 100f);

        int color;
        if (rate >= 0.85f)      color = 0xFF4CAF50; // green
        else if (rate >= 0.70f) color = 0xFFFFC107; // amber
        else if (rate >= 0.50f) color = 0xFFFF9800; // orange
        else                    color = 0xFFE53935; // red

        if (completionRing != null) completionRing.setRing(rate, pct, color);
        if (completionSummary != null) {
            completionSummary.setText(completed + " answered · " + missed + " missed");
        }
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

    /** Populates the consistency grid: last up to MAX_CELLS cycles, oldest → newest. */
    private void buildConsistencyGrid(List<SessionItem> sortedAsc) {
        if (consistencyGrid == null) return;
        consistencyGrid.removeAllViews();

        int start = Math.max(0, sortedAsc.size() - MAX_CELLS);
        int cellSize = dpToPx(17);
        int gap = dpToPx(4);

        for (int i = start; i < sortedAsc.size(); i++) {
            SessionItem s = sortedAsc.get(i);
            int color = colorForStatus(s.status);

            View cell = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(cellSize, cellSize);
            if (i > start) lp.setMarginStart(gap);
            cell.setLayoutParams(lp);
            cell.setBackgroundResource(R.drawable.checkin_cell_bg);
            cell.setBackgroundTintList(ColorStateList.valueOf(color));
            cell.setContentDescription(statusLabel(s.status));
            consistencyGrid.addView(cell);
        }
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

        String analysis = json.optString("analysis", "");
        if (richieAnalysisText != null) richieAnalysisText.setText(analysis);

        boolean isCouncil = json.optBoolean("isCouncil", false);
        JSONArray council = json.optJSONArray("council");

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

        // Notify only if the review finished while the screen wasn't in the foreground.
        if (!isForeground && !analysisNotified) {
            analysisNotified = true;
            CheckInNotificationHelper.fireAnalysisReady(this);
        }
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
    }

    private void showRichieReady() {
        if (richieSection == null) return;
        richieSection.setVisibility(View.VISIBLE);
        richieProcessing.setVisibility(View.GONE);
        richieReady.setVisibility(View.VISIBLE);
        richieFailed.setVisibility(View.GONE);
    }

    private void showRichieFailed() {
        if (richieSection == null) return;
        richieSection.setVisibility(View.VISIBLE);
        richieProcessing.setVisibility(View.GONE);
        richieReady.setVisibility(View.GONE);
        richieFailed.setVisibility(View.VISIBLE);
    }

    private void hideRichie() {
        if (richieSection != null) richieSection.setVisibility(View.GONE);
    }

    private int colorForStatus(String status) {
        if ("completed".equals(status)) return COLOR_COMPLETED;
        if ("missed".equals(status)) return COLOR_MISSED;
        return COLOR_IN_PROGRESS; // pending / in_progress / unknown
    }

    private String statusLabel(String status) {
        if ("completed".equals(status)) return "Completed";
        if ("missed".equals(status)) return "Missed";
        return "In progress";
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

    // ─── Completion ring (thin arc + centered percent) ──────────────────────────

    static class CompletionRingView extends View {
        private final Paint bgPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint arcPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF oval = new RectF();
        private float sweep = 0f;          // 0..1
        private String centerText = "0%";
        private final float strokeW;

        CompletionRingView(Context c) {
            super(c);
            float d = c.getResources().getDisplayMetrics().density;
            strokeW = 6f * d;

            bgPaint.setStyle(Paint.Style.STROKE);
            bgPaint.setStrokeWidth(strokeW);
            bgPaint.setColor(0xFF2A2A2A);

            arcPaint.setStyle(Paint.Style.STROKE);
            arcPaint.setStrokeWidth(strokeW);
            arcPaint.setStrokeCap(Paint.Cap.ROUND);
            arcPaint.setColor(0xFF008B8B);

            textPaint.setColor(0xFFFFFFFF);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTextSize(16f * d);
            textPaint.setFakeBoldText(true);
        }

        void setRing(float rate, int percent, int color) {
            this.sweep = Math.max(0f, Math.min(1f, rate));
            this.centerText = percent + "%";
            arcPaint.setColor(color);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float pad = strokeW / 2f + dpToPx(1);
            oval.set(pad, pad, getWidth() - pad, getHeight() - pad);
            canvas.drawArc(oval, 0f, 360f, false, bgPaint);
            canvas.drawArc(oval, -90f, 360f * sweep, false, arcPaint);

            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float ty = cy - (textPaint.descent() + textPaint.ascent()) / 2f;
            canvas.drawText(centerText, cx, ty, textPaint);
        }

        private float dpToPx(int dp) {
            return dp * getResources().getDisplayMetrics().density;
        }
    }
}

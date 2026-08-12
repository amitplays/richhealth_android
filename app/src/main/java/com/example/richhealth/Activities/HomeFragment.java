package com.example.richhealth.Activities;
import Utils.Utilities;

import Utils.ApiConfig;
import Utils.Skeleton;
import android.Manifest;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.example.richhealth.R;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import android.media.MediaPlayer;
import android.widget.ImageButton;
import android.widget.SeekBar;

import androidx.viewpager2.widget.ViewPager2;
import Adapters.BriefingAdapter;
import Adapters.PodcastAdapter;
import Models.BriefingCard;
import Api.AQIAPIService;
import Database.DatabaseHelper;
import Models.AQIData;
import Models.Podcast;
import Models.UserProfile;
import Utils.DialogUtils;
import Utils.ErrorHandler;
import Utils.FoodDialogUtils;
import Utils.ProStatusManager;
import Utils.SimpleProgress;

public class HomeFragment extends Fragment {
    private static final String TAG = "HomeFragment";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    private TextView welcomeText;
    private TextView healthScoreText;
    private TextView weeklyProgressText; // Now used for AQI
    private TextView weightProgressText; // Now used for profile completion percentage
    private TextView profileCompletionStatus;

    private RecyclerView recommendedPodcastsRecycler;
    private TextView mentalHealthChatButton;

    private DatabaseHelper dbHelper;
    private AQIAPIService aqiApiService;
    private UserProfile userProfile;
    private ProStatusManager proStatusManager;

    private LinearLayout planPill;
    private TextView planPillText;
    private ImageView planPillIcon;
    private List<UserProfile.RelationshipRequest> incomingRequests = new ArrayList<>();
    // Store incoming doctor requests (name/email/status)
    private final List<Map<String, String>> doctorIncomingRequests = new ArrayList<>();

    private TextView locationNameText;
    private Chip aqiStatusChip;
    private TextView linkDoctorButton;
    Location lastKnownLocation;

    private TextView dietaryInsightsText;
    private TextView nutriCheckText;
    private ImageView dietaryStaleIndicator;
    private ImageView nutriStaleIndicator;
    private TextView dietarySecondaryInfo;
    private TextView nutriSecondaryInfo;
    private TextView nutriStatusPill;        // semantic status pill (see TOOLS CARD STANDARD)
    private TextView healthAnalysisUpdatedTime;
    private TextView chatSecondaryInfo;

    // Per-type LLM analysis cache from backend healthAnalysisCache
    private JSONObject cachedTypeAnalyses;
    // Track which tab is selected in the analysis dialog
    private String currentAnalysisTab = "reports";

    // ========== CHECK-IN CARD ==========
    private Utils.ServiceCardView checkInHomeCard;
    private TextView checkInStatusText;
    private TextView checkInStartButton;
    private TextView checkInStatusPill;
    // ========== END CHECK-IN CARD ==========

    // ========== REUSABLE SERVICE CARDS (Tools screen) ==========
    // Every primary tool card is now one Utils.ServiceCardView. These hold the
    // card refs so the fragment can drive pill / meta / status-coloured chevron.
    private Utils.ServiceCardView nutriCheckCard;
    private Utils.ServiceCardView watchConnectCard;
    private Utils.ServiceCardView advisoryCard;
    private Utils.ServiceCardView aqiCard;
    private Utils.ServiceCardView dietaryCard;
    private Utils.ServiceCardView feedCard;
    private String advisoryFullContent = "";   // cached digest text for the tap-to-expand dialog

    // ========== NEW VARIABLES FOR HEALTH ANALYSIS & USAGE ==========
    private Utils.ServiceCardView healthAnalysisCard;
    private LinearLayout usageStatusCard;
    private TextView healthAnalysisLocation;
    private TextView healthAnalysisAQI;
    private TextView healthAnalysisProfilePercent;
    private Button analyzeHealthButton;

    private ImageView healthStatusChip;      // retired badge (0dp); kept for legacy toggles
    private TextView healthStatusPill;       // semantic status pill (see TOOLS CARD STANDARD)
    private ImageView healthAnalysisLastUpdated;
    // Status level/reason cached for dialogs (replaces reading from chip text/tag)
    private String currentStatusLevel = "";
    private String currentStatusReason = "";

    // Cached full API response for dialog
    private JSONObject lastHealthAnalysisJson;

    // Cached local profile completion (used by both home card and dialog)
    private int cachedProfilePercent = 0;
    // cachedProfileMissing removed — profile completion now uses backend data only

    private TextView usageStatusTitle;
    // [PLAN-PILL-REVIEW] removed (hardcoded/dead plan pill; will review later)
    private TextView usageContext;
    private Button viewUsageButton;
    private ImageView usageStatusIcon;
    // ========== END NEW VARIABLES ==========

    // Podcast player components
    private MediaPlayer mediaPlayer;
    private PodcastAdapter podcastAdapter;
    private List<Podcast> recommendedPodcasts;
    private Podcast currentPodcast;

    // Health Advisory (Daily Digest)
    private MaterialCardView dailyDigestCard;
    private TextView digestContent;
    private TextView digestStatusText;   // loading / no-content placeholder
    private TextView digestDateLabel;
    private TextView digestLocationChip;
    private LinearLayout digestAqiRow;
    private View digestAqiDot;
    private TextView digestAqiText;
    private LinearLayout digestErrorRow;

    // Daily Briefing carousel
    private ViewPager2 briefingPager;
    private LinearLayout briefingDots;
    private TextView briefingUpdated;
    private BriefingAdapter briefingAdapter;
    private final Handler briefingAutoScroll = new Handler(android.os.Looper.getMainLooper());
    private Runnable briefingTick;
    private int briefingCount = 0;
    private static final long BRIEFING_INTERVAL_MS = 5000L;
    private TextView digestRetry;

    // Mini player components
    private View miniPlayerBar;
    private View miniPlayerSpacer;
    private TextView miniPlayerTitle;
    private ImageButton miniPlayerPlayPause;
    private ImageButton miniPlayerClose;
    private SeekBar miniPlayerSeekbar;
    private TextView miniPlayerTime;
    private ScrollView homeScrollView;
    private final Handler seekbarHandler = new Handler(Looper.getMainLooper());
    private boolean userIsSeeking = false;

    // ── Wearable / fitness (Google Fit, pure Java — no Kotlin) ──
    private static final int REQ_GOOGLE_FIT = 8801;
    private com.google.android.gms.fitness.FitnessOptions fitnessOptions;
    private TextView watchPillRef;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        dbHelper = new DatabaseHelper(requireContext());
        aqiApiService = new AQIAPIService(requireContext());
        proStatusManager = ProStatusManager.getInstance(requireContext());

        initViews(view);
        setupClickListeners(view);
        loadUserData();
        fetchUserAnalysis();
        setupRecommendedPodcasts();
        // ========== LOAD HEALTH ANALYSIS & USAGE ==========
        loadAndDisplayHealthAnalysis();
        loadAndDisplayUsageStatus();
        loadCheckInCard();
        // ========== END LOAD CALLS ==========
        ProStatusManager.syncProStatusOnLogin(requireContext());
        fetchBriefing();
        fetchDailyDigest();      // Daily Advisory card (digest text, split from the Briefing carousel)
        fetchDietaryInsights();  // Dietary Insights card (eat / avoid)
        updateAqiCard();         // Air Quality card — seed from cached AQI immediately
        // Request location permission
        fetchLocation();
        Utils.IconAnimator.animateSectionIcons(view);

        return view;
    }

    private void initViews(View view) {
        welcomeText = view.findViewById(R.id.welcome_text);
        healthScoreText = view.findViewById(R.id.health_score);
        weeklyProgressText = view.findViewById(R.id.weekly_progress);
        // nutriCheckText is bound below from the NutriCheck ServiceCardView subtitle.
        weightProgressText = view.findViewById(R.id.weight_progress);
        profileCompletionStatus = view.findViewById(R.id.profile_completion_status);

        recommendedPodcastsRecycler = view.findViewById(R.id.recommended_podcasts_recycler);

        // Health Feed "See all" link
        View healthFeedSeeAll = view.findViewById(R.id.health_feed_see_all);
        if (healthFeedSeeAll != null) {
            healthFeedSeeAll.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), HealthFeedActivity.class);
                startActivity(intent);
            });
        }

        // ── Reusable ServiceCardView refs (one component per tool card) ──
        healthAnalysisCard = view.findViewById(R.id.health_analysis_card);
        checkInHomeCard = view.findViewById(R.id.checkin_home_card);
        nutriCheckCard = view.findViewById(R.id.nutri_check_card);
        watchConnectCard = view.findViewById(R.id.watch_connect_card);
        advisoryCard = view.findViewById(R.id.daily_advisory_card);
        aqiCard = view.findViewById(R.id.aqi_card);
        dietaryCard = view.findViewById(R.id.dietary_insights_card);
        feedCard = view.findViewById(R.id.feed_card);

        // Legacy per-card view fields now point at the ServiceCardView's inner views
        // (pill / subtitle / meta) so ALL existing wiring keeps working unchanged.
        // Retired 0dp badges (…_chip, …_last_updated, …_stale_indicator, …_button)
        // still live in the hidden compat block and are looked up normally below.
        healthStatusPill = healthAnalysisCard.getPillView();
        healthAnalysisUpdatedTime = healthAnalysisCard.getMetaView();

        nutriStatusPill = nutriCheckCard.getPillView();
        nutriSecondaryInfo = nutriCheckCard.getMetaView();
        nutriCheckText = nutriCheckCard.getSubtitleView();

        dietaryInsightsText = dietaryCard.getSubtitleView();
        dietarySecondaryInfo = dietaryCard.getMetaView();

        dietaryStaleIndicator = view.findViewById(R.id.dietary_stale_indicator);
        nutriStaleIndicator = view.findViewById(R.id.nutri_stale_indicator);
        chatSecondaryInfo = view.findViewById(R.id.chat_secondary_info);
        locationNameText = view.findViewById(R.id.location_name);
        aqiStatusChip = view.findViewById(R.id.aqi_status_chip);
        linkDoctorButton = view.findViewById(R.id.link_doctor_button);

        // Data insights initialized as null (populated from API)
        // lastDataInsights removed

        // ========== NEW HEALTH ANALYSIS CARD VIEWS ==========
        // healthAnalysisCard / healthStatusPill / healthAnalysisUpdatedTime bound above.
        healthAnalysisLocation = view.findViewById(R.id.health_analysis_location);
        healthAnalysisAQI = view.findViewById(R.id.health_analysis_aqi);
        healthAnalysisProfilePercent = view.findViewById(R.id.health_analysis_profile_percent);
        analyzeHealthButton = view.findViewById(R.id.analyze_health_button);

        // Retired 0dp compat badges (kept for legacy toggles; carry no visible state now)
        healthStatusChip = view.findViewById(R.id.health_status_chip);
        healthAnalysisLastUpdated = view.findViewById(R.id.health_analysis_last_updated);

        // ========== NEW USAGE STATUS CARD VIEWS ==========
        usageStatusCard = view.findViewById(R.id.usage_status_card);
        usageStatusTitle = view.findViewById(R.id.usage_status_title);
        // [PLAN-PILL-REVIEW] removed (hardcoded/dead plan pill; will review later)
        usageContext = view.findViewById(R.id.usage_context);
        usageStatusIcon = view.findViewById(R.id.usage_status_icon);
        viewUsageButton = view.findViewById(R.id.view_usage_button);
        // ========== END NEW VIEWS ==========

        // Check-In home card (checkInHomeCard bound above). Pill/meta from the card;
        // start button is a retired 0dp compat view.
        checkInStatusText = checkInHomeCard.getMetaView();
        checkInStatusPill = checkInHomeCard.getPillView();
        checkInStartButton = view.findViewById(R.id.checkin_start_button);

        // Health Advisory card
        dailyDigestCard = view.findViewById(R.id.daily_digest_card);
        digestContent = view.findViewById(R.id.digest_content);
        digestDateLabel = view.findViewById(R.id.digest_date_label);
        digestLocationChip = view.findViewById(R.id.digest_location_chip);
        digestAqiRow = view.findViewById(R.id.digest_aqi_row);
        digestAqiDot = view.findViewById(R.id.digest_aqi_dot);
        digestAqiText = view.findViewById(R.id.digest_aqi_text);
        digestErrorRow = view.findViewById(R.id.digest_error_row);
        digestRetry = view.findViewById(R.id.digest_retry);
        digestStatusText = view.findViewById(R.id.digest_status_text);
        briefingPager = view.findViewById(R.id.briefing_pager);
        briefingDots = view.findViewById(R.id.briefing_dots);
        briefingUpdated = view.findViewById(R.id.briefing_updated);
        setupBriefingPager();
        View briefingRetry = view.findViewById(R.id.digest_retry);
        if (briefingRetry != null) briefingRetry.setOnClickListener(v -> fetchBriefing());

        // Mini player
        homeScrollView = view.findViewById(R.id.home_scroll_view);
        miniPlayerBar = view.findViewById(R.id.mini_player_bar);
        miniPlayerSpacer = view.findViewById(R.id.mini_player_spacer);
        miniPlayerTitle = view.findViewById(R.id.mini_player_title);
        miniPlayerPlayPause = view.findViewById(R.id.mini_player_play_pause);
        miniPlayerClose = view.findViewById(R.id.mini_player_close);
        miniPlayerSeekbar = view.findViewById(R.id.mini_player_seekbar);
        miniPlayerTime = view.findViewById(R.id.mini_player_time);
        setupMiniPlayerListeners();


        // Quick Action Cards
        view.findViewById(R.id.start_workout_card).setOnClickListener(v ->
                navigateToFragment(new WorkoutsFragment())
        );

        view.findViewById(R.id.browse_exercises_card).setOnClickListener(v ->
                navigateToFragment(new ExercisesFragment())
        );

        // Mental Health Chat Button
        mentalHealthChatButton = view.findViewById(R.id.mental_health_chat_button);

        // Plan pill (replaces notification button) — opens upgrade dialog / shows tier
        // [PLAN-PILL-REVIEW] disabled (backend-driven, keep after review): planPill = view.findViewById(R.id.plan_pill);
        // planPillText = view.findViewById(R.id.plan_pill_text);
        // planPillIcon = view.findViewById(R.id.plan_pill_icon);
        setupPlanPill();
        animateCardsEntry(view);

        // Skeleton loading for backend-driven fields — replaces "Loading..." placeholder text.
        // Cards that start hidden are revealed so the skeleton can show in their place.
        if (checkInHomeCard != null) checkInHomeCard.setVisibility(View.VISIBLE);
        if (checkInStatusText != null) checkInStatusText.setVisibility(View.VISIBLE);
        if (healthAnalysisUpdatedTime != null) healthAnalysisUpdatedTime.setVisibility(View.VISIBLE);
        if (dietarySecondaryInfo != null) dietarySecondaryInfo.setVisibility(View.VISIBLE);
        if (nutriSecondaryInfo != null) nutriSecondaryInfo.setVisibility(View.VISIBLE);
        if (chatSecondaryInfo != null) chatSecondaryInfo.setVisibility(View.VISIBLE);

        Skeleton.show(
                healthScoreText,
                healthAnalysisUpdatedTime,
                usageContext,
                digestStatusText,
                digestLocationChip,
                checkInStatusText,
                dietarySecondaryInfo,
                nutriSecondaryInfo,
                chatSecondaryInfo
        );
    }

    private void setupClickListeners(View view) {
        // With these lines:
        MaterialCardView dietaryInsightsCard = view.findViewById(R.id.dietary_insights_card);
        dietaryInsightsCard.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), DietaryInsightsActivity.class);
            startActivity(intent);
        });

        if (nutriCheckCard != null) {
            nutriCheckCard.setOnClickListener(v -> {
                Intent nutriIntent = new Intent(requireContext(), NutriCheckActivity.class);
                startActivity(nutriIntent);
            });
        }

        // Daily Advisory → expand full digest text in a CardInfoDialog.
        if (advisoryCard != null) {
            advisoryCard.setOnClickListener(v -> showAdvisoryDialog());
        }
        // Air Quality → open the AQI history sheet (same as the old AQI chip).
        if (aqiCard != null) {
            aqiCard.setOnClickListener(v -> fetchAndShowAQIHistory());
        }
        // Health Feed → switch the Services host to the Feed tab (fallback: activity).
        if (feedCard != null) {
            feedCard.setOnClickListener(v -> openFeedTab());
        }

        // AQI views may be null if Health Metrics card was replaced with new design
        View aqiChip = view.findViewById(R.id.aqi_status_chip);
        if (aqiChip != null) {
            aqiChip.setOnClickListener(v -> fetchAndShowAQIHistory());
        }
        View aqiLink = view.findViewById(R.id.aqi_standard_link);
        if (aqiLink != null) {
            aqiLink.setOnClickListener(v -> fetchAndShowAQIHistory());
        }

        // ========== NEW CARD CLICK LISTENERS ==========
        if (healthAnalysisCard != null) {
            healthAnalysisCard.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), HealthAnalysisActivity.class);
                startActivity(intent);
            });
        }
        if (analyzeHealthButton != null) {
            analyzeHealthButton.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), HealthAnalysisActivity.class);
                startActivity(intent);
            });
        }
        if (usageStatusCard != null) {
            usageStatusCard.setOnClickListener(v -> Utils.UsageBottomSheet.show(requireActivity()));
        }
        if (viewUsageButton != null) {
            viewUsageButton.setOnClickListener(v -> Utils.UsageBottomSheet.show(requireActivity()));
        }
        // ========== END NEW LISTENERS ==========

        // Check-In card
        if (checkInHomeCard != null) {
            checkInHomeCard.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), DailyCheckInActivity.class);
                startActivity(intent);
            });
        }
        if (checkInStartButton != null) {
            checkInStartButton.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), DailyCheckInActivity.class);
                startActivity(intent);
            });
        }

        linkDoctorButton.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), DoctorSearchActivity.class);
            startActivity(intent);
        });

        // Mental Health Chat Button Click
        mentalHealthChatButton.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), HealthAssistantActivity.class);
            startActivity(intent);
        });

        // Whole-card clicks for the two service tiles (CTAs are now hidden; the
        // entire card is tappable). Same destinations as their old buttons.
        View doctorCard = view.findViewById(R.id.doctor_connection_card);
        if (doctorCard != null) {
            doctorCard.setOnClickListener(v ->
                    startActivity(new Intent(requireContext(), DoctorSearchActivity.class)));
        }
        View wellnessCard = view.findViewById(R.id.mental_health_card);
        if (wellnessCard != null) {
            wellnessCard.setOnClickListener(v ->
                    startActivity(new Intent(requireContext(), HealthAssistantActivity.class)));
        }

        // ── Connect watch / health data (Google Health / Health Connect) ──
        TextView watchConnectButton = view.findViewById(R.id.watch_connect_button);
        TextView watchConnectPill = watchConnectCard != null ? watchConnectCard.getPillView() : null;
        refreshWatchConnectState(watchConnectPill);
        View.OnClickListener watchClick = v -> openHealthConnect(watchConnectPill);
        if (watchConnectCard != null) watchConnectCard.setOnClickListener(watchClick);
        if (watchConnectButton != null) watchConnectButton.setOnClickListener(watchClick);

        // Feed / AQI cards are informational → NORMAL chevron.
        if (feedCard != null) feedCard.setChevronStatus(Utils.ServiceCardView.ChevronStatus.NORMAL);
        if (aqiCard != null) aqiCard.setChevronStatus(Utils.ServiceCardView.ChevronStatus.NORMAL);
    }

    /** Switch the Services host to the Feed tab; fall back to the standalone activity. */
    private void openFeedTab() {
        Fragment parent = getParentFragment();
        if (parent instanceof ServicesFragment) {
            ((ServicesFragment) parent).showFeedTab();
        } else {
            startActivity(new Intent(requireContext(), HealthFeedActivity.class));
        }
    }

    /** Tap-to-expand for the Daily Advisory card: full digest text in a dialog. */
    private void showAdvisoryDialog() {
        String content = advisoryFullContent != null ? advisoryFullContent.trim() : "";
        if (content.isEmpty()) {
            startActivity(new Intent(requireContext(), HealthAnalysisActivity.class));
            return;
        }
        new Utils.CardInfoDialog.Builder(requireContext())
                .title("Daily Advisory")
                .subtitle("Personalized for today")
                .icon(R.drawable.ic_heart_smile)
                .body(content)
                .build()
                .show();
    }

    // ── Wearable / fitness linking (Google Fit, pure Java) ───────────────────
    // Reads step/heart-rate data that Wear OS / Fitbit / other watches sync into
    // Google Fit, then caches + shows it on the card (no "install another app").
    private static final String HOME_PREFS = "rh_home_prefs";

    private com.google.android.gms.fitness.FitnessOptions getFitnessOptions() {
        if (fitnessOptions == null) {
            fitnessOptions = com.google.android.gms.fitness.FitnessOptions.builder()
                    .addDataType(com.google.android.gms.fitness.data.DataType.TYPE_STEP_COUNT_DELTA,
                            com.google.android.gms.fitness.FitnessOptions.ACCESS_READ)
                    .addDataType(com.google.android.gms.fitness.data.DataType.TYPE_HEART_RATE_BPM,
                            com.google.android.gms.fitness.FitnessOptions.ACCESS_READ)
                    .build();
        }
        return fitnessOptions;
    }

    private void openHealthConnect(TextView pill) {
        Context ctx = getContext();
        if (ctx == null) return;
        watchPillRef = pill;

        // Google Fit (pure Java): request the fitness scope if needed, then read + show the data
        // in-app. Google Fit aggregates data synced from Wear OS / Fitbit / other watches, so
        // there's no "install another app" step.
        com.google.android.gms.fitness.FitnessOptions opts = getFitnessOptions();
        com.google.android.gms.auth.api.signin.GoogleSignInAccount account =
                com.google.android.gms.auth.api.signin.GoogleSignIn.getAccountForExtension(ctx, opts);
        if (!com.google.android.gms.auth.api.signin.GoogleSignIn.hasPermissions(account, opts)) {
            com.google.android.gms.auth.api.signin.GoogleSignIn.requestPermissions(
                    this, REQ_GOOGLE_FIT, account, opts);
        } else {
            readAndShowHealthData();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_GOOGLE_FIT) {
            if (resultCode == android.app.Activity.RESULT_OK) {
                readAndShowHealthData();
            } else {
                Context ctx = getContext();
                if (ctx != null) {
                    Utilities.toastLong(ctx, "Google Fit permission is needed to sync your watch data");
                }
            }
        }
    }

    /** Reads today's step total from Google Fit and reflects it on the watch card. */
    private void readAndShowHealthData() {
        final Context ctx = getContext();
        if (ctx == null) return;
        com.google.android.gms.fitness.FitnessOptions opts = getFitnessOptions();
        com.google.android.gms.auth.api.signin.GoogleSignInAccount account =
                com.google.android.gms.auth.api.signin.GoogleSignIn.getAccountForExtension(ctx, opts);

        com.google.android.gms.fitness.Fitness.getHistoryClient(ctx, account)
                .readDailyTotal(com.google.android.gms.fitness.data.DataType.TYPE_STEP_COUNT_DELTA)
                .addOnSuccessListener(dataSet -> {
                    if (!isAdded()) return;
                    long steps = 0;
                    if (dataSet != null && !dataSet.isEmpty()) {
                        steps = dataSet.getDataPoints().get(0)
                                .getValue(com.google.android.gms.fitness.data.Field.FIELD_STEPS).asInt();
                    }
                    setWatchConnected(true);
                    if (watchPillRef != null) refreshWatchConnectState(watchPillRef);
                    ctx.getSharedPreferences(HOME_PREFS, Context.MODE_PRIVATE).edit()
                            .putLong("fit_steps", steps).apply();
                    Utilities.toast(ctx, "Synced " + steps + " steps today");
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) {
                        Utilities.toast(ctx, "Couldn't read fitness data: " + e.getMessage());
                    }
                });
    }

    private void setWatchConnected(boolean connected) {
        Context ctx = getContext();
        if (ctx == null) return;
        ctx.getSharedPreferences(HOME_PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean("watch_connected", connected).apply();
    }

    private void refreshWatchConnectState(TextView pill) {
        if (pill == null) return;
        Context ctx = getContext();
        boolean connected = ctx != null && ctx.getSharedPreferences(HOME_PREFS, Context.MODE_PRIVATE)
                .getBoolean("watch_connected", false);
        // Standard semantic pill: SUCCESS when connected, NEUTRAL when off.
        Utils.StatusPill.apply(pill,
                connected ? Utils.StatusPill.Intent.SUCCESS : Utils.StatusPill.Intent.NEUTRAL,
                connected ? "Connected" : "Not connected");
    }


    // Fetch incoming doctor connection requests and merge with existing list
    private void checkForIncomingDoctorRequests() {
        Context context = getContext();
        if (context == null) return; // Fragment detached, skip operation safely

        TokenManager tokenManager = TokenManager.getInstance(context);
        String token = tokenManager.getToken();

        if (token == null) {
            return;
        }

        String url = ApiConfig.BASE_URL + "/api/users/doctor/doctor/requests";

        StringRequest request = new StringRequest(Request.Method.GET, url,
                response -> {
                    ApiConfig.logRestCall(url, true, "Doctor requests fetched");
                    try {
                        JSONObject jsonResponse = new JSONObject(response);
                        JSONArray doctorRequestsArray = jsonResponse.optJSONArray("incomingDoctorRequests");

                        if (doctorRequestsArray == null) {
                            return;
                        }

                        doctorIncomingRequests.clear();
                        for (int i = 0; i < doctorRequestsArray.length(); i++) {
                            JSONObject reqObj = doctorRequestsArray.getJSONObject(i);
                            Map<String, String> item = new HashMap<>();
                            item.put("email", reqObj.optString("email", ""));
                            item.put("name", reqObj.optString("name", ""));
                            item.put("status", reqObj.optString("status", "pending"));
                            doctorIncomingRequests.add(item);
                        }

                        updateNotificationBadge();
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing doctor requests response", e);
                    }
                },
                error -> {
                    ApiConfig.logRestCall(url, false, error.toString());
                    Log.e(TAG, "Error fetching doctor connection requests", error);
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + token);
                return headers;
            }
        };

        RequestQueue queue = Volley.newRequestQueue(context);
        queue.add(request);
    }

    // 5. Add new method that uses your existing FoodDialogUtils with tabs
    private void showDietaryInsightsDialog() {
        // Use your existing FoodDialogUtils but with tabs
        FoodDialogUtils.showDietaryInsightsDialog(
                requireContext(),
                "Diet Guide"
        );
    }

    private void loadUserData() {
        userProfile = dbHelper.getUserProfile();

        if (userProfile != null) {
            // Set personalized greeting
            setPersonalizedGreeting();

            // Profile completion — show placeholder until backend value arrives via loadAndDisplayHealthAnalysis()
            if (weightProgressText != null) weightProgressText.setText("–");
            if (profileCompletionStatus != null) profileCompletionStatus.setText("");

            // Set personalized food recommendations
            setPersonalizedFoodRecommendations();
        } else {
            // Default values if no user profile
            welcomeText.setText("Welcome to RichHealth");
            Skeleton.hide(healthScoreText, healthStatusChip);
            healthScoreText.setText("Health Score: N/A");
            if (weightProgressText != null) weightProgressText.setText("0%");
            if (profileCompletionStatus != null) profileCompletionStatus.setText("Profile not set up");

           }
    }

    private void setPersonalizedGreeting() {
        Calendar calendar = Calendar.getInstance();
        int hourOfDay = calendar.get(Calendar.HOUR_OF_DAY);
        String greeting;

        if (hourOfDay >= 5 && hourOfDay < 12) {
            greeting = "Good Morning";
        } else if (hourOfDay >= 12 && hourOfDay < 17) {
            greeting = "Good Afternoon";
        } else {
            greeting = "Good Evening";
        }

        String name = userProfile.getName() != null
                ? ", " + userProfile.getName().split(" ")[0]
                : "";

        welcomeText.setText(greeting + name);
    }

    // Profile completion is now sourced exclusively from the backend via loadAndDisplayHealthAnalysis().
    // The old local calculateProfileCompletion() method was removed to eliminate the dual-score discrepancy.

    private void fetchUserAnalysis() {
        Context context = getContext();
        if (context == null) return;

        // Check cache first
        SharedPreferences prefs = context.getSharedPreferences("user_analysis_cache", Context.MODE_PRIVATE);
        String cachedData = prefs.getString("analysis_data", null);
        long cacheTime = prefs.getLong("cache_time", 0);
        long cacheExpiry = 24 * 60 * 60 * 1000; // 24 hours

        if (cachedData != null && (System.currentTimeMillis() - cacheTime) < cacheExpiry) {
            return; // Use cached data — pills read from getCachedUserAnalysis()
        }

        String url = ApiConfig.BASE_URL + "/api/users/analysis";
        TokenManager tokenManager = TokenManager.getInstance(context);

        StringRequest request = new StringRequest(Request.Method.GET, url,
                response -> {
                    ApiConfig.logRestCall(url, true, "User analysis fetched");
                    try {
                        JSONObject jsonResponse = new JSONObject(response);

                        // Cache the response
                        prefs.edit()
                                .putString("analysis_data", response)
                                .putLong("cache_time", System.currentTimeMillis())
                                .apply();

                        // Cache saved — pills read from getCachedUserAnalysis() at dialog open
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing user analysis", e);
                    }
                },
                error -> {
                    ApiConfig.logRestCall(url, false, error.toString());
                    Log.e(TAG, "Error fetching user analysis", error);
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + tokenManager.getToken());
                return headers;
            }
        };

        Volley.newRequestQueue(context).add(request);
    }

    private void setPersonalizedFoodRecommendations() {
        if (userProfile != null) {
            dietaryInsightsText.setText("What foods to eat and avoid based on your health data.");
        } else {
            dietaryInsightsText.setText("Complete your profile to get personalized nutrition recommendations.");
        }
    }

    private void setupRecommendedPodcasts() {
        // Create sample podcasts
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int rightPadding = (int)(screenWidth * 0.15);

        // Apply padding only on the right side
        recommendedPodcastsRecycler.setPadding(0, 0, rightPadding, 0);
        recommendedPodcastsRecycler.setClipToPadding(false);

        // For smoother scrolling experience
        PagerSnapHelper snapHelper = new PagerSnapHelper();
        snapHelper.attachToRecyclerView(recommendedPodcastsRecycler);

        recommendedPodcasts = createRecommendedPodcasts();

        podcastAdapter = new PodcastAdapter(
                requireContext(),
                recommendedPodcasts,
                new PodcastAdapter.OnPodcastClickListener() {
                    @Override
                    public void onPlayClick(Podcast podcast) {
                        playPodcast(podcast);
                    }

                    @Override
                    public void onPauseClick(Podcast podcast) {
                        pausePodcast();
                    }
                }
        );

        recommendedPodcastsRecycler.setLayoutManager(
                new LinearLayoutManager(requireContext(),
                        LinearLayoutManager.HORIZONTAL, false)
        );
        recommendedPodcastsRecycler.setAdapter(podcastAdapter);
    }

    private void setupMiniPlayerListeners() {
        miniPlayerPlayPause.setOnClickListener(v -> {
            if (mediaPlayer == null || currentPodcast == null) return;
            if (mediaPlayer.isPlaying()) {
                pausePodcast();
            } else {
                resumePodcast();
            }
        });

        miniPlayerClose.setOnClickListener(v -> {
            stopPodcast();
        });

        miniPlayerSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && mediaPlayer != null) {
                    int seekPos = (int) ((progress / 100.0) * mediaPlayer.getDuration());
                    mediaPlayer.seekTo(seekPos);
                    updateMiniPlayerTimeText();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                userIsSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                userIsSeeking = false;
            }
        });
    }

    private final Runnable seekbarUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            if (mediaPlayer != null && mediaPlayer.isPlaying() && !userIsSeeking) {
                int current = mediaPlayer.getCurrentPosition();
                int total = mediaPlayer.getDuration();
                if (total > 0) {
                    miniPlayerSeekbar.setProgress((int) ((current / (float) total) * 100));
                }
                updateMiniPlayerTimeText();
            }
            seekbarHandler.postDelayed(this, 500);
        }
    };

    private void showMiniPlayer(Podcast podcast) {
        miniPlayerTitle.setText(podcast.getTitle());
        miniPlayerPlayPause.setImageResource(R.drawable.ic_pause);
        miniPlayerBar.setVisibility(View.VISIBLE);
        miniPlayerSpacer.setVisibility(View.VISIBLE);
        seekbarHandler.removeCallbacks(seekbarUpdateRunnable);
        seekbarHandler.post(seekbarUpdateRunnable);
    }

    private void hideMiniPlayer() {
        miniPlayerBar.setVisibility(View.GONE);
        miniPlayerSpacer.setVisibility(View.GONE);
        seekbarHandler.removeCallbacks(seekbarUpdateRunnable);
        miniPlayerSeekbar.setProgress(0);
        miniPlayerTime.setText("0:00 / 0:00");
    }

    private void updateMiniPlayerTimeText() {
        if (mediaPlayer == null) return;
        String current = formatTime(mediaPlayer.getCurrentPosition());
        String total = formatTime(mediaPlayer.getDuration());
        miniPlayerTime.setText(current + " / " + total);
    }

    private String formatTime(int millis) {
        int seconds = (millis / 1000) % 60;
        int minutes = (millis / 1000) / 60;
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
    }

    private void playPodcast(Podcast podcast) {
        if (currentPodcast != null && currentPodcast.getId() == podcast.getId() && mediaPlayer != null) {
            resumePodcast();
            return;
        }

        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }

        int resourceId = getResources().getIdentifier(
                podcast.getAudioResourceName(),
                "raw",
                requireContext().getPackageName()
        );

        if (resourceId == 0) {
            Utilities.toast(requireContext(), "Audio file not found");
            return;
        }

        try {
            mediaPlayer = MediaPlayer.create(requireContext(), resourceId);

            if (mediaPlayer != null) {
                currentPodcast = podcast;
                mediaPlayer.start();

                podcastAdapter.setNowPlayingId(podcast.getId());
                podcastAdapter.setPaused(false);
                podcastAdapter.notifyDataSetChanged();

                showMiniPlayer(podcast);

                mediaPlayer.setOnCompletionListener(mp -> {
                    podcastAdapter.setPaused(true);
                    podcastAdapter.notifyDataSetChanged();
                    miniPlayerPlayPause.setImageResource(R.drawable.ic_play);
                    miniPlayerSeekbar.setProgress(100);
                    updateMiniPlayerTimeText();
                });
            }
        } catch (Exception e) {
            Utilities.toast(requireContext(), "Error playing podcast");
            Log.e(TAG, "Error playing podcast: " + e.getMessage());
        }
    }

    private void pausePodcast() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            podcastAdapter.setPaused(true);
            podcastAdapter.notifyDataSetChanged();
            miniPlayerPlayPause.setImageResource(R.drawable.ic_play);
        }
    }

    private void resumePodcast() {
        if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
            mediaPlayer.start();
            podcastAdapter.setPaused(false);
            podcastAdapter.notifyDataSetChanged();
            miniPlayerPlayPause.setImageResource(R.drawable.ic_pause);
        }
    }

    private void stopPodcast() {
        seekbarHandler.removeCallbacks(seekbarUpdateRunnable);
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        currentPodcast = null;
        podcastAdapter.setNowPlayingId(-1);
        podcastAdapter.setPaused(true);
        podcastAdapter.notifyDataSetChanged();
        hideMiniPlayer();
    }

    private List<Podcast> createRecommendedPodcasts() {
        List<Podcast> podcasts = new ArrayList<>();

        podcasts.add(createPodcast(
                1,
                "Health & Wellness Basics \uD83C\uDF3F",
                "A beginner-friendly overview of foundational health habits including nutrition, exercise, sleep hygiene, and stress management techniques for everyday life.",
                "sample_podcast",
                180,
                "Wellness",
                13
        ));
        podcasts.add(createPodcast(
                2,
                "Sunlight and Vitamin D Synthesis \uD83C\uDF1E",
                "Detailed exploration of how sunlight triggers vitamin D synthesis in the human body, discussing its crucial role in bone health, immune function, and overall well-being.",
                "vit_d",
                180,
                "Fitness",
                13
        ));
        podcasts.add(createPodcast(
                3,
                "Cold Showers and Immune Function \uD83E\uDD76",
                "Explore the potential for regular cold exposure to enhance immune resilience, improve stress management through cross-adaptation, and offer practical benefits for health and recovery.",
                "cold",
                180,
                "Health",
                13
        ));
        podcasts.add(createPodcast(
                4,
                "The Science of Aging \uD83E\uDDEC",
                "Deep dive into the biological mechanisms of aging, from telomere shortening to cellular senescence, and evidence-based strategies to promote longevity and healthy aging.",
                "aging",
                180,
                "Science",
                13
        ));
        return podcasts;
    }

    private Podcast createPodcast(long id, String title, String description,
                                  String audioResourceName, long duration,
                                  String category, int iconResourceId) {
        Podcast podcast = new Podcast(id, title, description, audioResourceName,
                duration, category, iconResourceId);

        switch(category) {
            case "Wellness":
                podcast.setTags(Arrays.asList("Basics", "Lifestyle", "Self-Care"));
                break;
            case "Fitness":
                podcast.setTags(Arrays.asList("Wellness", "Nutrition", "Vitamin D"));
                break;
            case "Health":
                podcast.setTags(Arrays.asList("Immune System", "Recovery", "Stress Management"));
                break;
            case "Science":
                podcast.setTags(Arrays.asList("Longevity", "Biology", "Anti-Aging"));
                break;
        }

        podcast.setSourceLinks(Arrays.asList(
                "https://www.ncbi.nlm.nih.gov/research-paper-1",
                "https://scholar.google.com/study-link"
        ));

        return podcast;
    }

    private final ActivityResultLauncher<String> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                Log.d("LocationPermission", "Launcher callback. isGranted = " + isGranted);

                if (isGranted) {
                    Log.d("LocationPermission", "Permission granted via launcher");
                    getLocation();
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        getLocation();
                        if (lastKnownLocation != null) {
                            fetchAQIData(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude());
                        } else {
                            Log.e("LocationPermission", "lastKnownLocation is null in launcher");
                        }
                    }, 200); // slight delay to ensure location is ready

                } else {
                    Log.d("LocationPermission", "Permission denied via launcher");
                    if (weeklyProgressText != null) weeklyProgressText.setText("N/A");
                }
            });


    private void fetchLocation() {
        // First, try to show cached AQI data regardless of location status
        Context context = getContext();
        if (context == null) {
            Log.e(TAG, "Context is null in fetchLocation");
            return;
        }
        
        SharedPreferences prefs = context.getSharedPreferences("aqi_prefs", Context.MODE_PRIVATE);
        int cachedAQI = prefs.getInt("cached_aqi", -1);
        String cachedCity = prefs.getString("cached_city", "");

        if (cachedAQI != -1) {
            // We have cached data, show it immediately
            updateIQAirDisplay(cachedAQI, cachedCity);
        }

        // Check for permission
        if (ActivityCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            // Don't change UI if we already have cached data (old AQI views may be null)
            if (cachedAQI == -1) {
                if (weeklyProgressText != null) weeklyProgressText.setText("N/A");
                if (locationNameText != null) locationNameText.setText("Location Permission Required");
                if (aqiStatusChip != null) {
                    aqiStatusChip.setText("Grant Permission");
                    aqiStatusChip.setChipBackgroundColor(ColorStateList.valueOf(Color.parseColor("#808080")));
                }
            }

            // Request permission
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }

        // Use the LocationManager directly - simpler approach
        LocationManager locationManager =
                (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);

        // Check if location is enabled
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) &&
                !locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {

            // Don't change UI if we already have cached data (old AQI views may be null)
            if (cachedAQI == -1) {
                if (weeklyProgressText != null) weeklyProgressText.setText("AQI");
                if (locationNameText != null) locationNameText.setText("Location Off");
                if (aqiStatusChip != null) {
                    aqiStatusChip.setText("Enable location");
                    aqiStatusChip.setChipBackgroundColor(ColorStateList.valueOf(Color.parseColor("#808080")));
                }
            }

            Utilities.toast(context, "Please enable location services for latest AQI data");
            return;
        }

        // Try to get location - first try GPS
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    0,  // minimum time interval between updates (milliseconds)
                    0,  // minimum distance between updates (meters)
                    new LocationListener() {
                        @Override
                        public void onLocationChanged(Location location) {
                            lastKnownLocation = location;
                            fetchAQIData(location.getLatitude(), location.getLongitude());
                            locationManager.removeUpdates(this);
                        }

                        @Override
                        public void onStatusChanged(String provider, int status, Bundle extras) {}

                        @Override
                        public void onProviderEnabled(String provider) {}

                        @Override
                        public void onProviderDisabled(String provider) {}
                    },
                    Looper.getMainLooper());
        }
        // Also try NETWORK provider as a backup
        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    0,
                    0,
                    new LocationListener() {
                        @Override
                        public void onLocationChanged(Location location) {
                            lastKnownLocation = location;
                            fetchAQIData(location.getLatitude(), location.getLongitude());
                            locationManager.removeUpdates(this);
                        }

                        @Override
                        public void onStatusChanged(String provider, int status, Bundle extras) {}

                        @Override
                        public void onProviderEnabled(String provider) {}

                        @Override
                        public void onProviderDisabled(String provider) {}
                    },
                    Looper.getMainLooper());
        }

        // Also try last known location as immediate fallback
        Location lastLocationGPS = null;
        Location lastLocationNetwork = null;

        try {
            lastLocationGPS = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            lastLocationNetwork = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        } catch (Exception e) {
            Log.e(TAG, "Error getting last known location: " + e.getMessage());
        }

        // Use the most recent one
        if (lastLocationGPS != null && lastLocationNetwork != null) {
            if (lastLocationGPS.getTime() > lastLocationNetwork.getTime()) {
                lastKnownLocation = lastLocationGPS;
            } else {
                lastKnownLocation = lastLocationNetwork;
            }
        } else if (lastLocationGPS != null) {
            lastKnownLocation = lastLocationGPS;
        } else if (lastLocationNetwork != null) {
            lastKnownLocation = lastLocationNetwork;
        }

        // If we have a last known location, use it
        if (lastKnownLocation != null) {
            fetchAQIData(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude());
        } else if (cachedAQI == -1) {
            // No last known location and no cached data (old AQI views may be null if replaced by Health Analysis card)
            if (weeklyProgressText != null) weeklyProgressText.setText("No Data");
            if (locationNameText != null) locationNameText.setText("Waiting for location");
            if (aqiStatusChip != null) {
                aqiStatusChip.setText("Pending");
                aqiStatusChip.setChipBackgroundColor(ColorStateList.valueOf(Color.parseColor("#808080")));
            }
        }
    }

    private void fetchAQIData(double latitude, double longitude) {
        Log.d(TAG, "fetchAQIData() called with lat/lon: " + latitude + "/" + longitude);

        if (getContext() == null) {
            Log.e(TAG, "Context is null in fetchAQIData - Returning ");
            return;
        }

        // Get shared preferences
        SharedPreferences prefs = getContext().getSharedPreferences("aqi_prefs", Context.MODE_PRIVATE);

        // Always store current location for future comparison, regardless of API call
        SharedPreferences.Editor editor = prefs.edit();
        editor.putFloat("current_lat", (float)latitude);
        editor.putFloat("current_lon", (float)longitude);
        editor.apply();

        // Get cached data
        long lastTime = prefs.getLong("last_update_time", 0);
        float lastLat = prefs.getFloat("last_lat", 0);
        float lastLon = prefs.getFloat("last_lon", 0);
        int cachedAQI = prefs.getInt("cached_aqi", -1);
        String cachedCity = prefs.getString("cached_city", "");

        long hourAgo = System.currentTimeMillis() - (60 * 60 * 1000);
        boolean timeExpired = lastTime < hourAgo;
        boolean locationChanged = Math.abs(latitude - lastLat) > 0.01 || Math.abs(longitude - lastLon) > 0.01;
        boolean firstTime = lastTime == 0;


        // Use cached data if available and still valid
        if (!timeExpired && !locationChanged && !firstTime && cachedAQI != -1) {
            Log.d(TAG, "Using cached AQI data: " + cachedAQI);
            updateIQAirDisplay(cachedAQI, cachedCity);
            return;
        }

        // If we have cached data, keep showing it while fetching new data
        if (cachedAQI != -1) {
            Log.d(TAG, "Showing cached data while fetching new data");
            updateIQAirDisplay(cachedAQI, cachedCity);
        } else {
            Log.d(TAG, "No cached data available, showing loading state");
            if (weeklyProgressText != null) weeklyProgressText.setText("Loading...");
        }

        Context context = getContext();
        if (context == null) {
            Log.e(TAG, "Context is null in fetchAQIData");
            return;
        }

        Log.d(TAG, "Making API request for location: " + latitude + ", " + longitude);

        String iqairApiUrl = "https://api.airvisual.com/v2/nearest_city?lat=" +
                latitude + "&lon=" + longitude + "&key=49b9397d-7ef6-479f-8426-d65b32cc3e7f";

        RequestQueue queue = Volley.newRequestQueue(context);
        StringRequest stringRequest = new StringRequest(Request.Method.GET, iqairApiUrl,
                response -> {
                    ApiConfig.logRestCall(iqairApiUrl, true, "IQAir data fetched");
                    Log.d(TAG, "API response received"+response);
                    try {
                        JSONObject jsonResponse = new JSONObject(response);
                        JSONObject data = jsonResponse.getJSONObject("data");

                        storeAqiDataToBackend(data);

                        String city = data.getString("city");
                        JSONObject current = data.getJSONObject("current");
                        JSONObject pollution = current.getJSONObject("pollution");
                        int aqi = pollution.getInt("aqius");

                        Log.d(TAG, "AQI parsed successfully: " + aqi + " for " + city);

                        // Save the successful API data and location to SharedPreferences
                        SharedPreferences.Editor apiEditor = prefs.edit();
                        apiEditor.putLong("last_update_time", System.currentTimeMillis());
                        apiEditor.putFloat("last_lat", (float)latitude);
                        apiEditor.putFloat("last_lon", (float)longitude);
                        apiEditor.putInt("cached_aqi", aqi);
                        apiEditor.putString("cached_city", city);
                        apiEditor.apply();

                        Log.d(TAG, "Saved AQI data to SharedPreferences");

                        // Update display with new data
                        updateIQAirDisplay(aqi, city);

                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing AQI response: " + e.getMessage());

                        // If we have cached data, keep showing it on error
                        if (cachedAQI != -1) {
                            updateIQAirDisplay(cachedAQI, cachedCity);
                        } else {
                            if (weeklyProgressText != null) weeklyProgressText.setText("Error");
                            if (locationNameText != null) locationNameText.setText("Data Error");
                            if (aqiStatusChip != null) {
                                aqiStatusChip.setText("Try Again");
                                aqiStatusChip.setChipBackgroundColor(ColorStateList.valueOf(Color.parseColor("#FF0000")));
                            }
                        }
                    }
                },
                error -> {
                    ApiConfig.logRestCall(iqairApiUrl, false, error.toString());
                    Log.e(TAG, "Error fetching AQI data: " + error.toString());

                    // If we have cached data, keep showing it on error
                    if (cachedAQI != -1) {
                        Log.d(TAG, "Network error, using cached data: " + cachedAQI);
                        updateIQAirDisplay(cachedAQI, cachedCity);
                    } else {
                        if (weeklyProgressText != null) weeklyProgressText.setText("Error");
                        if (locationNameText != null) locationNameText.setText("Network Error");
                        if (aqiStatusChip != null) {
                            aqiStatusChip.setText("Try Again");
                            aqiStatusChip.setChipBackgroundColor(ColorStateList.valueOf(Color.parseColor("#FF0000")));
                        }
                    }
                });

        // Set a timeout to avoid hanging requests
        stringRequest.setRetryPolicy(new DefaultRetryPolicy(
                10000, // 10 second timeout
                3,     // Max 1 retry
                1.0f   // No backoff multiplier
        ));

        queue.add(stringRequest);
    }

    private void storeAqiDataToBackend(JSONObject data) {
        Context context = getContext();
        if (context == null) return; // Fragment detached, skip operation safely

        try {
            String city = data.getString("city");
            String state = data.optString("state", "");
            String country = data.getString("country");

            JSONObject location = data.getJSONObject("location");
            JSONArray coordinates = location.getJSONArray("coordinates");
            double longitude = coordinates.getDouble(0);
            double latitude = coordinates.getDouble(1);

            JSONObject current = data.getJSONObject("current");
            JSONObject pollution = current.getJSONObject("pollution");
            int aqius = pollution.getInt("aqius");
            int aqicn = pollution.optInt("aqicn", 0);
            String mainus = pollution.optString("mainus", "");
            String maincn = pollution.optString("maincn", "");

            JSONObject weather = current.getJSONObject("weather");
            double temperature = weather.optDouble("tp", 0);
            int humidity = weather.optInt("hu", 0);
            int pressure = weather.optInt("pr", 0);

            // Create request body
            JSONObject requestBody = new JSONObject();
            requestBody.put("city", city);
            requestBody.put("state", state);
            requestBody.put("country", country);
            requestBody.put("latitude", latitude);
            requestBody.put("longitude", longitude);
            requestBody.put("aqius", aqius);
            requestBody.put("aqicn", aqicn);
            requestBody.put("mainus", mainus);
            requestBody.put("maincn", maincn);
            requestBody.put("temperature", temperature);
            requestBody.put("humidity", humidity);
            requestBody.put("pressure", pressure);

            String url = ApiConfig.BASE_URL + "/api/aqi/store";
            TokenManager tokenManager = TokenManager.getInstance(context);

            StringRequest request = new StringRequest(Request.Method.POST, url,
                    response -> {
                        ApiConfig.logRestCall(url, true, "AQI data stored");
                        Log.d(TAG, "AQI data stored successfully");
                        // Cache record count and analysis summary from backend response
                        try {
                            JSONObject resp = new JSONObject(response);
                            int recordCount = resp.optInt("recordCount", 0);
                            String analysisSummary = resp.optString("analysisSummary", "");
                            if (recordCount > 0 || !analysisSummary.isEmpty()) {
                                SharedPreferences aqiPrefs = context.getSharedPreferences("aqi_prefs", Context.MODE_PRIVATE);
                                SharedPreferences.Editor editor = aqiPrefs.edit();
                                if (recordCount > 0) editor.putInt("cached_aqi_record_count", recordCount);
                                if (!analysisSummary.isEmpty()) editor.putString("cached_aqi_analysis", analysisSummary);
                                editor.apply();
                            }
                        } catch (Exception ignored) {}
                    },
                    error -> {
                        ApiConfig.logRestCall(url, false, error.toString());
                        Log.e(TAG, "Failed to store AQI data: " + error.toString());
                        // Don't show error to user - this is background operation
                    }
            ) {
                @Override
                public byte[] getBody() {
                    return requestBody.toString().getBytes(StandardCharsets.UTF_8);
                }

                @Override
                public String getBodyContentType() {
                    return "application/json; charset=utf-8";
                }

                @Override
                public Map<String, String> getHeaders() throws AuthFailureError {
                    Map<String, String> headers = new HashMap<>();
                    headers.put("Authorization", "Bearer " + tokenManager.getToken());
                    return headers;
                }
            };

            RequestQueue queue = Volley.newRequestQueue(context);
            queue.add(request);

        } catch (JSONException e) {
            Log.e(TAG, "Error creating AQI request: " + e.getMessage());
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        Log.d("LocationPermission", "onRequestPermissionsResult called");

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            Log.d("LocationPermission", "Matched request code");

            if (grantResults.length > 0) {
                Log.d("LocationPermission", "Permission result: " + grantResults[0]);

                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d("LocationPermission", "Permission granted");
                    getLocation();
                    if (lastKnownLocation != null) {
                        fetchAQIData(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude());
                    } else {
                        Log.e("LocationPermission", "lastKnownLocation is null");
                    }
                } else {
                    Log.d("LocationPermission", "Permission denied");
                    if (weeklyProgressText != null) weeklyProgressText.setText("N/A");
                    Context context = getContext();
                    if (context != null) {
                        Utilities.toast(context, "Location permission is needed for AQI data");
                    }
                }
            } else {
                Log.d("LocationPermission", "grantResults is empty");
            }
        } else {
            Log.d("LocationPermission", "Unknown request code: " + requestCode);
        }
    }

    private void getLocation() {
        Log.d(TAG, "getLocation() called from: " + Thread.currentThread().getStackTrace()[3]);

        Context context = getContext();
        if (context == null) {
            Log.e(TAG, "Context is null in getLocation");
            return;
        }

        // Check permission
        if (ActivityCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }

        // Get location manager
        LocationManager locationManager =
                (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);

        // Create a simple location listener
        LocationListener locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                // Got a location - use it!
                lastKnownLocation = location;
                fetchAQIData(location.getLatitude(), location.getLongitude());

                // Stop listening for updates
                locationManager.removeUpdates(this);
            }

            @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
            @Override public void onProviderEnabled(String provider) {}
            @Override public void onProviderDisabled(String provider) {}
        };

        // Request location updates
        locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,  // or NETWORK_PROVIDER for faster, less accurate locations
                0,  // minimum time interval
                0,  // minimum distance
                locationListener);
    }


    private void updateIQAirDisplay(int aqiValue, String cityName) {
        Log.d(TAG, "updateIQAirDisplay() called with value: " + aqiValue);

        String aqiText = String.valueOf(aqiValue);
        String aqiStatus;
        int aqiColor;

        if (aqiValue <= 50) {
            aqiStatus = "Good";
            aqiColor = Color.parseColor("#00E400");
        } else if (aqiValue <= 100) {
            aqiStatus = "Moderate";
            aqiColor = Color.parseColor("#FFFF00");
        } else if (aqiValue <= 150) {
            aqiStatus = "Unhealthy";
            aqiColor = Color.parseColor("#FF7E00");
        } else if (aqiValue <= 200) {
            aqiStatus = "Unhealthy";
            aqiColor = Color.parseColor("#FF0000");
        } else if (aqiValue <= 300) {
            aqiStatus = "Very Unhealthy";
            aqiColor = Color.parseColor("#8F3F97");
        } else {
            aqiStatus = "Hazardous";
            aqiColor = Color.parseColor("#7E0023");
        }

        if (weeklyProgressText != null) weeklyProgressText.setText(aqiText);
        if (locationNameText != null) locationNameText.setText(cityName);
        if (aqiStatusChip != null) {
            aqiStatusChip.setText(aqiStatus);
            aqiStatusChip.setTextColor(Color.parseColor("#000000"));
            aqiStatusChip.setChipBackgroundColor(ColorStateList.valueOf(aqiColor));
        }

        // Refresh the reusable Air Quality ServiceCard from the just-updated cache.
        updateAqiCard();

        Log.d(TAG, "AQI display updated successfully");
    }


    private void showMentalHealthChatDialog() {
        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_mental_health_chat);

        // Set dialog width to most of the screen
        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
        layoutParams.copyFrom(dialog.getWindow().getAttributes());
        layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
        layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
        dialog.getWindow().setAttributes(layoutParams);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        // Find views in dialog
        TextView dialogTitle = dialog.findViewById(R.id.dialog_title);
        dialogTitle.setText("Wellness Chat");

        TextView dialogMessage = dialog.findViewById(R.id.dialog_message);
        dialogMessage.setText("Chat anonymously with our AI assistant. Your conversation is private and will not be saved. How are you feeling today?");

        EditText messageInput = dialog.findViewById(R.id.message_input);
        Button sendButton = dialog.findViewById(R.id.send_button);
        Button closeButton = dialog.findViewById(R.id.close_button);

        RecyclerView chatRecycler = dialog.findViewById(R.id.chat_recycler);
        chatRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));

        // Create chat adapter for mental health chat
        List<Map<String, String>> chatMessages = new ArrayList<>();
        MentalHealthChatAdapter chatAdapter = new MentalHealthChatAdapter(chatMessages);
        chatRecycler.setAdapter(chatAdapter);

        // Add welcome message
        Map<String, String> welcomeMessage = new HashMap<>();
        welcomeMessage.put("text", "Hello! I'm your mental health assistant. How are you feeling today?");
        welcomeMessage.put("sender", "assistant");
        chatMessages.add(welcomeMessage);
        chatAdapter.notifyItemInserted(chatMessages.size() - 1);

        // Set up send button
        sendButton.setOnClickListener(v -> {
            String message = messageInput.getText().toString().trim();
            if (!message.isEmpty()) {
                // Add user message to chat
                Map<String, String> userMessage = new HashMap<>();
                userMessage.put("text", message);
                userMessage.put("sender", "user");
                chatMessages.add(userMessage);
                chatAdapter.notifyItemInserted(chatMessages.size() - 1);

                // Clear input
                messageInput.setText("");

                // Scroll to bottom
                chatRecycler.smoothScrollToPosition(chatMessages.size() - 1);

                // Simulate AI response (with slight delay)
                new android.os.Handler().postDelayed(() -> {
                    // Generate a response based on user message
                    String response = generateMentalHealthResponse(message);

                    // Add AI response to chat
                    Map<String, String> aiResponse = new HashMap<>();
                    aiResponse.put("text", response);
                    aiResponse.put("sender", "assistant");
                    chatMessages.add(aiResponse);
                    chatAdapter.notifyItemInserted(chatMessages.size() - 1);

                    // Scroll to bottom
                    chatRecycler.smoothScrollToPosition(chatMessages.size() - 1);
                }, 1000);
            }
        });

        // Set up close button
        closeButton.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private String generateMentalHealthResponse(String message) {
        // This is a simple placeholder implementation
        // In a real app, you'd connect to an AI service or use a more sophisticated approach

        String messageLower = message.toLowerCase();

        if (messageLower.contains("stress") || messageLower.contains("stressed") ||
                messageLower.contains("anxious") || messageLower.contains("anxiety")) {
            return "I'm sorry to hear you're feeling stressed. Consider trying deep breathing exercises, " +
                    "progressive muscle relaxation, or a short mindfulness meditation. " +
                    "What specifically is causing this feeling?";
        }
        else if (messageLower.contains("sad") || messageLower.contains("depression") ||
                messageLower.contains("depressed") || messageLower.contains("unhappy")) {
            return "I understand that feeling sad can be difficult. Consider connecting with a friend, " +
                    "engaging in a physical activity, or practicing self-care. Would you like to talk more about what's causing these feelings?";
        }
        else if (messageLower.contains("tired") || messageLower.contains("exhausted") ||
                messageLower.contains("fatigue") || messageLower.contains("sleep")) {
            return "Fatigue can have many causes. Are you getting 7-9 hours of quality sleep? " +
                    "Consider reviewing your sleep habits, physical activity, and stress levels. " +
                    "Would you like some tips for better sleep?";
        }
        else if (messageLower.contains("good") || messageLower.contains("great") ||
                messageLower.contains("fine") || messageLower.contains("well")) {
            return "I'm glad to hear you're doing well! Maintaining positive mental health is important. " +
                    "Is there anything specific you'd like to discuss or any area of your well-being you'd like to focus on?";
        }
        else {
            return "Thank you for sharing. Remember that your feelings are valid. " +
                    "Would you like to talk more about this, or would you prefer some resources on maintaining mental well-being?";
        }
    }

    private void navigateToFragment(Fragment fragment) {
        requireActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit();
    }

    private void showIncomingRequestsDialog() {
        // Implementation from original code - showing dialog with incoming relationship requests
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Family Relationship Requests");

        // Inflate custom view for the dialog
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_relationship_requests, null);
        RecyclerView requestsRecycler = dialogView.findViewById(R.id.requests_recycler);

        // Set up adapter
        IncomingRequestAdapter adapter = new IncomingRequestAdapter(incomingRequests);
        requestsRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        requestsRecycler.setAdapter(adapter);

        builder.setView(dialogView);
        builder.setPositiveButton("Close", null);

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    // Show dialog with RecyclerView for incoming doctor requests (similar to family requests)
    private void showDoctorRequestsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Incoming Doctor Requests");

        // Inflate custom view for the dialog
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_relationship_requests, null);
        RecyclerView requestsRecycler = dialogView.findViewById(R.id.requests_recycler);

        // Set up adapter for doctor requests with dialog reference for dismissal
        DoctorRequestAdapter adapter = new DoctorRequestAdapter(doctorIncomingRequests);
        requestsRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        requestsRecycler.setAdapter(adapter);

        builder.setView(dialogView);
        builder.setPositiveButton("Close", null);

        AlertDialog dialog = builder.create();

        // Pass dialog reference to adapter after creation
        adapter.setDialog(dialog);

        dialog.show();
    }

    // Inner class for Mental Health Chat adapter
    private class MentalHealthChatAdapter extends RecyclerView.Adapter<MentalHealthChatAdapter.ChatViewHolder> {
        private List<Map<String, String>> messages;

        public MentalHealthChatAdapter(List<Map<String, String>> messages) {
            this.messages = messages;
        }

        @NonNull
        @Override
        public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_chat_message, parent, false);
            return new ChatViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
            Map<String, String> message = messages.get(position);
            holder.messageText.setText(message.get("text"));

            if ("user".equals(message.get("sender"))) {
                // User message (right-aligned)
                holder.messageText.setBackgroundResource(R.drawable.chat_bubble_user);
                holder.itemView.setPadding(80, 8, 8, 8); // Add padding on the left
            } else {
                // Assistant message (left-aligned)
                holder.messageText.setBackgroundResource(R.drawable.chat_bubble_assistant);
                holder.itemView.setPadding(8, 8, 80, 8); // Add padding on the right
            }
        }

        @Override
        public int getItemCount() {
            return messages.size();
        }

        class ChatViewHolder extends RecyclerView.ViewHolder {
            TextView messageText;

            public ChatViewHolder(@NonNull View itemView) {
                super(itemView);
                messageText = itemView.findViewById(R.id.message_text);
            }
        }
    }

    // Inner class for Incoming Request adapter (from original code)
    private class IncomingRequestAdapter extends RecyclerView.Adapter<IncomingRequestAdapter.RequestViewHolder> {
        private List<UserProfile.RelationshipRequest> requests;

        public IncomingRequestAdapter(List<UserProfile.RelationshipRequest> requests) {
            this.requests = requests;
        }

        @NonNull
        @Override
        public RequestViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_incoming_request, parent, false);
            return new RequestViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RequestViewHolder holder, int position) {
            UserProfile.RelationshipRequest request = requests.get(position);
            holder.emailText.setText(request.getEmail());
            holder.relationshipText.setText("Wants to connect as your " + request.getRelationship());

            // Set button click listeners
            holder.acceptButton.setOnClickListener(v -> {
                respondToRequest(request.getEmail(), true, position);
            });

            holder.rejectButton.setOnClickListener(v -> {
                respondToRequest(request.getEmail(), false, position);
            });
        }

        @Override
        public int getItemCount() {
            return requests.size();
        }

        class RequestViewHolder extends RecyclerView.ViewHolder {
            TextView emailText;
            TextView relationshipText;
            Button acceptButton;
            Button rejectButton;

            public RequestViewHolder(@NonNull View itemView) {
                super(itemView);
                emailText = itemView.findViewById(R.id.email_text);
                relationshipText = itemView.findViewById(R.id.relationship_text);
                acceptButton = itemView.findViewById(R.id.accept_button);
                rejectButton = itemView.findViewById(R.id.reject_button);
            }
        }
    }

    // Inner class for Doctor Request adapter
    private class DoctorRequestAdapter extends RecyclerView.Adapter<DoctorRequestAdapter.DoctorRequestViewHolder> {
        private List<Map<String, String>> doctorRequests;
        private AlertDialog dialog;

        public DoctorRequestAdapter(List<Map<String, String>> doctorRequests) {
            this.doctorRequests = doctorRequests;
        }

        public void setDialog(AlertDialog dialog) {
            this.dialog = dialog;
        }

        @NonNull
        @Override
        public DoctorRequestViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_incoming_request, parent, false);
            return new DoctorRequestViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull DoctorRequestViewHolder holder, int position) {
            Map<String, String> request = doctorRequests.get(position);
            String name = request.get("name");
            String email = request.get("email");

            // Set display name and email
            if (name != null && !name.isEmpty()) {
                holder.emailText.setText(name);
                if (email != null && !email.isEmpty()) {
                    holder.relationshipText.setText(email);
                } else {
                    holder.relationshipText.setText("Doctor connection request");
                }
            } else {
                holder.emailText.setText(email != null ? email : "");
                holder.relationshipText.setText("Doctor connection request");
            }

            // Set button click listeners
            holder.acceptButton.setOnClickListener(v -> {
                respondToDoctorRequest(email, true, position, dialog);
            });

            holder.rejectButton.setOnClickListener(v -> {
                respondToDoctorRequest(email, false, position, dialog);
            });
        }

        @Override
        public int getItemCount() {
            return doctorRequests.size();
        }

        class DoctorRequestViewHolder extends RecyclerView.ViewHolder {
            TextView emailText;
            TextView relationshipText;
            Button acceptButton;
            Button rejectButton;

            public DoctorRequestViewHolder(@NonNull View itemView) {
                super(itemView);
                emailText = itemView.findViewById(R.id.email_text);
                relationshipText = itemView.findViewById(R.id.relationship_text);
                acceptButton = itemView.findViewById(R.id.accept_button);
                rejectButton = itemView.findViewById(R.id.reject_button);
            }
        }
    }

    // Method to respond to doctor connection request
    private void respondToDoctorRequest(String email, boolean accept, int position, AlertDialog dialog) {
        Context context = getContext();
        if (context == null) return; // Fragment detached, skip operation safely

        TokenManager tokenManager = TokenManager.getInstance(context);
        String token = tokenManager.getToken();

        if (token == null) {
            Utilities.toast(context, "Authentication error");
            return;
        }

        // Show progress indicator
        SimpleProgress progress = SimpleProgress.show(requireActivity(), "Processing doctor request...");

        String url = ApiConfig.BASE_URL + "/api/users/doctor/respond";

        JSONObject requestBody = new JSONObject();
        try {
            requestBody.put("email", email);
            requestBody.put("accept", accept);
        } catch (JSONException e) {
            Log.e(TAG, "Error creating request body", e);
            progress.hide();
            return;
        }

        StringRequest request = new StringRequest(Request.Method.POST, url,
                response -> {
                    ApiConfig.logRestCall(url, true, "Doctor request response sent");
                    progress.hide();
                    // Request successful
                    String action = accept ? "accepted" : "rejected";
                    Utilities.toast(context, "Doctor request " + action);

                    // Remove the request from the list
                    if (position < doctorIncomingRequests.size()) {
                        doctorIncomingRequests.remove(position);
                    }
                    updateNotificationBadge();

                    // Dismiss the dialog for clean UX
                    if (dialog != null && dialog.isShowing()) {
                        dialog.dismiss();
                    }
                },
                error -> {
                    ApiConfig.logRestCall(url, false, error.toString());
                    progress.hide();
                    String errorMessage = "Failed to respond to doctor request";

                    // Try to parse error message from response
                    if (error.networkResponse != null && error.networkResponse.data != null) {
                        try {
                            String errorData = new String(error.networkResponse.data, StandardCharsets.UTF_8);
                            try {
                                JSONObject errorJson = new JSONObject(errorData);
                                if (errorJson.has("message")) {
                                    errorMessage = errorJson.getString("message");
                                }
                            } catch (JSONException e) {
                                // If not JSON, use the raw string response
                                if (!errorData.isEmpty()) {
                                    errorMessage = errorData;
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing error response", e);
                        }
                    }

                    Utilities.toast(context, errorMessage);
                    Log.e(TAG, "Error responding to doctor request: " + errorMessage, error);
                }
        ) {
            @Override
            public String getBodyContentType() {
                return "application/json; charset=utf-8";
            }

            @Override
            public byte[] getBody() {
                return requestBody.toString().getBytes(StandardCharsets.UTF_8);
            }

            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + token);
                return headers;
            }
        };

        // Add retry policy
        request.setRetryPolicy(new DefaultRetryPolicy(
                30000,
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

        RequestQueue queue = Volley.newRequestQueue(context);
        queue.add(request);
    }

    // Method to respond to relationship request (from original code)
    // Improved method to respond to relationship request
    private void respondToRequest(String email, boolean accept, int position) {
        Context context = getContext();
        if (context == null) return; // Fragment detached, skip operation safely

        TokenManager tokenManager = TokenManager.getInstance(context);
        String token = tokenManager.getToken();

        if (token == null) {
            Utilities.toast(context, "Authentication error");
            return;
        }

        // Show progress indicator
        SimpleProgress progress = SimpleProgress.show(requireActivity(), "Processing request...");

        String url;
        UserProfile.RelationshipRequest req = incomingRequests.get(position);
        if (req != null && "Doctor".equalsIgnoreCase(req.getRelationship())) {
            url = ApiConfig.BASE_URL + "/api/users/doctor/respond";
        } else {
            url = ApiConfig.BASE_URL + "/api/users/relationship/respond";
        }

        JSONObject requestBody = new JSONObject();
        try {
            requestBody.put("email", email);
            requestBody.put("accept", accept);
        } catch (JSONException e) {
            Log.e(TAG, "Error creating request body", e);
            progress.hide();
            return;
        }

        StringRequest request = new StringRequest(Request.Method.POST, url,
                response -> {
                    ApiConfig.logRestCall(url, true, "Relationship request response sent");
                    progress.hide();
                    // Request successful
                    String action = accept ? "accepted" : "rejected";
                    Utilities.toast(context, "Request " + action);

                    // Remove the request from the list
                    incomingRequests.remove(position);
                    updateNotificationBadge();
                },
                error -> {
                    ApiConfig.logRestCall(url, false, error.toString());
                    progress.hide();
                    String errorMessage = "Failed to respond to request";

                    // Try to parse error message from response
                    if (error.networkResponse != null && error.networkResponse.data != null) {
                        try {
                            String errorData = new String(error.networkResponse.data, StandardCharsets.UTF_8);
                            try {
                                JSONObject errorJson = new JSONObject(errorData);
                                if (errorJson.has("message")) {
                                    errorMessage = errorJson.getString("message");
                                }
                            } catch (JSONException e) {
                                // If not JSON, use the raw string response
                                if (!errorData.isEmpty()) {
                                    errorMessage = errorData;
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing error response", e);
                        }
                    }

                    Utilities.toast(context, errorMessage);
                    Log.e(TAG, "Error responding to request: " + errorMessage, error);
                }
        ) {
            @Override
            public String getBodyContentType() {
                return "application/json; charset=utf-8";
            }

            @Override
            public byte[] getBody() {
                return requestBody.toString().getBytes(StandardCharsets.UTF_8);
            }

            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + token);
                return headers;
            }
        };

        // Add retry policy
        request.setRetryPolicy(new DefaultRetryPolicy(
                30000,
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

        RequestQueue queue = Volley.newRequestQueue(context);
        queue.add(request);
    }

    // Notification badge removed with notification button — keep stub for callers.
    private void updateNotificationBadge() {
        // no-op: notification surface removed in favor of plan pill
    }

    @Override
    public void onResume() {
        super.onResume();
        // Check for incoming relationship requests
        checkForIncomingRequests();
        // Check for incoming doctor connection requests
        checkForIncomingDoctorRequests();
        // Refresh check-in card so status pill reflects any action taken in DailyCheckInActivity.
        loadCheckInCard();
        // Resume briefing auto-scroll if a carousel is showing.
        if (briefingPager != null && briefingPager.getVisibility() == View.VISIBLE) scheduleBriefingAutoScroll();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            if (podcastAdapter != null) {
                podcastAdapter.setPaused(true);
                podcastAdapter.notifyDataSetChanged();
            }
            miniPlayerPlayPause.setImageResource(R.drawable.ic_play);
        }
        if (briefingTick != null) briefingAutoScroll.removeCallbacks(briefingTick);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        seekbarHandler.removeCallbacks(seekbarUpdateRunnable);
        if (briefingTick != null) briefingAutoScroll.removeCallbacks(briefingTick);
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    // Method to check for incoming relationship requests (from original code)
    private void checkForIncomingRequests() {
        Context context = getContext();
        if (context == null) return; // Fragment detached, skip operation safely

        TokenManager tokenManager = TokenManager.getInstance(context);
        String token = tokenManager.getToken();

        if (token == null) {
            return;
        }

        String url = ApiConfig.BASE_URL + "/api/users/relationship/requests";

        StringRequest request = new StringRequest(Request.Method.GET, url,
                response -> {
                    ApiConfig.logRestCall(url, true, "Relationship requests fetched");
                    try {
                        JSONObject jsonResponse = new JSONObject(response);
                        JSONArray incomingRequestsArray = jsonResponse.getJSONArray("incomingRequests");

                        // Filter only pending requests
                        incomingRequests.clear();
                        for (int i = 0; i < incomingRequestsArray.length(); i++) {
                            JSONObject requestObj = incomingRequestsArray.getJSONObject(i);
                            if ("pending".equals(requestObj.getString("status"))) {
                                UserProfile.RelationshipRequest relationshipRequest = new UserProfile.RelationshipRequest(
                                        requestObj.getString("email"),
                                        requestObj.getString("relationship"),
                                        requestObj.getString("status")
                                );
                                incomingRequests.add(relationshipRequest);
                            }
                        }

                        // Update notification badge visibility
                        updateNotificationBadge();

                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing relationship requests response", e);
                    }
                },
                error -> {
                    ApiConfig.logRestCall(url, false, error.toString());
                    Log.e(TAG, "Error fetching relationship requests", error);
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + token);
                return headers;
            }
        };

        RequestQueue queue = Volley.newRequestQueue(context);
        queue.add(request);
    }

    private void fetchAndShowAQIHistory() {
        // Show progress
        SimpleProgress.show(requireActivity(), "Loading AQI history...");

        // Fetch both history and analysis in parallel
        final int[] completedCalls = {0};
        final List<AQIData>[] historyResult = new List[]{null};
        final int[] analysisResult = {-1, -1, -1}; // averageAQI, maxAQI, highExposureDays

        // Fetch history
        aqiApiService.getUserAQIHistory(30, new AQIAPIService.OnAQIHistoryListener() {
            @Override
            public void onSuccess(List<AQIData> aqiHistory) {
                historyResult[0] = aqiHistory;

                // Store the data in database
                if (userProfile != null) {
                    for (AQIData aqiData : aqiHistory) {
                        aqiData.setUserId(userProfile.getId());
                        dbHelper.insertAQIData(aqiData);
                    }
                }

                completedCalls[0]++;
                if (completedCalls[0] >= 2) {
                    showAQIDialogWithResults(historyResult[0], analysisResult);
                }
            }

            @Override
            public void onError(String errorMessage) {
                // If API fails, try to show cached data
                if (userProfile != null) {
                    historyResult[0] = dbHelper.getAQIHistoryForUser(userProfile.getId());
                }

                completedCalls[0]++;
                if (completedCalls[0] >= 2) {
                    showAQIDialogWithResults(historyResult[0], analysisResult);
                }
            }
        });

        // Fetch analysis
        aqiApiService.getUserAQIAnalysis(new AQIAPIService.OnAQIAnalysisListener() {
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

    private void showAQIDialogWithResults(List<AQIData> aqiHistory, int[] analysisResult) {
        SimpleProgress.hide();

        Context context = getContext();
        if (context == null) return;

        if (aqiHistory == null || aqiHistory.isEmpty()) {
            Utilities.toast(context, "No AQI history data available");
            return;
        }

        // Show chart dialog with analysis if available
        if (analysisResult[2] >= 0) {
            DialogUtils.showAQIChartDialog(context, aqiHistory, analysisResult[2]);
        } else {
            DialogUtils.showAQIChartDialog(context, aqiHistory);
        }
    }

    // ========== DAILY BRIEFING (smart carousel) ==========

    private void setupBriefingPager() {
        if (briefingPager == null) return;
        briefingAdapter = new BriefingAdapter(requireContext());
        briefingPager.setAdapter(briefingAdapter);
        briefingPager.setOffscreenPageLimit(1);
        briefingPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override public void onPageSelected(int position) { updateBriefingDots(position); }
            @Override public void onPageScrollStateChanged(int state) {
                // Pause auto-scroll while the user is dragging; resume when idle.
                if (state == ViewPager2.SCROLL_STATE_DRAGGING) {
                    if (briefingTick != null) briefingAutoScroll.removeCallbacks(briefingTick);
                } else if (state == ViewPager2.SCROLL_STATE_IDLE) {
                    scheduleBriefingAutoScroll();
                }
            }
        });
    }

    private void fetchBriefing() {
        if (briefingPager == null) return;
        Context context = getContext();
        if (context == null) return;
        TokenManager tokenManager = TokenManager.getInstance(context);
        final String token = tokenManager != null ? tokenManager.getToken() : null;
        if (token == null) return;

        final String url = ApiConfig.BASE_URL + "/api/home/briefing";
        StringRequest request = new StringRequest(Request.Method.GET, url,
                response -> {
                    if (!isAdded() || getActivity() == null) return;
                    requireActivity().runOnUiThread(() -> {
                        try {
                            JSONObject json = new JSONObject(response);
                            org.json.JSONArray arr = json.optJSONArray("cards");
                            List<BriefingCard> cards = new ArrayList<>();
                            if (arr != null) {
                                for (int i = 0; i < arr.length(); i++) {
                                    JSONObject c = arr.optJSONObject(i);
                                    if (c == null) continue;
                                    String title = c.optString("title", "").trim();
                                    String priority = c.optString("priority", "medium").trim();
                                    org.json.JSONArray pts = c.optJSONArray("points");
                                    List<String> points = new ArrayList<>();
                                    if (pts != null) {
                                        for (int j = 0; j < pts.length(); j++) {
                                            String p = pts.optString(j, "").trim();
                                            if (!p.isEmpty()) points.add(p);
                                        }
                                    }
                                    if (!title.isEmpty() && !points.isEmpty()) cards.add(new BriefingCard(title, points, priority));
                                }
                            }
                            showBriefing(cards, json.optString("generatedAt", ""));
                        } catch (JSONException e) {
                            Log.e(TAG, "Briefing parse error", e);
                            showBriefingError();
                        }
                    });
                },
                error -> { if (isAdded()) showBriefingError(); }) {
            @Override public java.util.Map<String, String> getHeaders() {
                java.util.Map<String, String> h = new java.util.HashMap<>();
                h.put("Authorization", "Bearer " + token);
                return h;
            }
        };
        Volley.newRequestQueue(context).add(request);
    }

    private void showBriefing(List<BriefingCard> cards, String generatedAtIso) {
        if (briefingPager == null || briefingAdapter == null) return;
        if (digestStatusText != null) digestStatusText.setVisibility(View.GONE);
        if (digestErrorRow != null) digestErrorRow.setVisibility(View.GONE);
        if (digestContent != null) digestContent.setVisibility(View.GONE);

        if (cards.isEmpty()) { showBriefingEmpty(); return; }

        briefingCount = cards.size();
        briefingAdapter.setCards(cards);
        briefingPager.setVisibility(View.VISIBLE);
        buildBriefingDots(briefingCount);
        updateBriefingDots(0);
        setBriefingUpdated(generatedAtIso);
        scheduleBriefingAutoScroll();
    }

    private void showBriefingEmpty() {
        briefingCount = 0;
        if (briefingPager != null) briefingPager.setVisibility(View.GONE);
        if (briefingDots != null) briefingDots.removeAllViews();
        if (briefingUpdated != null) briefingUpdated.setVisibility(View.GONE);
        if (digestStatusText != null) {
            digestStatusText.setText("Add a vital or complete your profile and I'll build your briefing.");
            digestStatusText.setVisibility(View.VISIBLE);
        }
    }

    private void showBriefingError() {
        if (briefingPager != null && briefingPager.getVisibility() == View.VISIBLE) return; // keep what we have
        if (digestStatusText != null) {
            digestStatusText.setText("Couldn't load your briefing. Pull to refresh.");
            digestStatusText.setVisibility(View.VISIBLE);
        }
    }

    private void setBriefingUpdated(String iso) {
        if (briefingUpdated == null) return;
        String label = formatBriefingUpdated(iso);
        if (label == null) { briefingUpdated.setVisibility(View.GONE); return; }
        briefingUpdated.setText("Updated " + label);
        briefingUpdated.setVisibility(View.VISIBLE);
    }

    private String formatBriefingUpdated(String iso) {
        if (iso == null || iso.isEmpty()) return null;
        try {
            java.text.SimpleDateFormat in = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US);
            in.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            java.util.Date d = in.parse(iso);
            if (d == null) return null;
            long h = (System.currentTimeMillis() - d.getTime()) / (60L * 60L * 1000L);
            if (h < 1) return "just now";
            if (h < 24) return h + "h ago";
            long days = h / 24;
            if (days < 7) return days + "d ago";
            return new java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault()).format(d);
        } catch (Exception e) { return null; }
    }

    private void buildBriefingDots(int count) {
        if (briefingDots == null) return;
        briefingDots.removeAllViews();
        if (count <= 1) return;
        float dp = getResources().getDisplayMetrics().density;
        for (int i = 0; i < count; i++) {
            View dot = new View(requireContext());
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            bg.setColor(android.graphics.Color.parseColor("#2A3A3A"));
            dot.setBackground(bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) (6 * dp), (int) (6 * dp));
            lp.rightMargin = (int) (6 * dp);
            dot.setLayoutParams(lp);
            briefingDots.addView(dot);
        }
    }

    private void updateBriefingDots(int active) {
        if (briefingDots == null) return;
        float dp = getResources().getDisplayMetrics().density;
        for (int i = 0; i < briefingDots.getChildCount(); i++) {
            View dot = briefingDots.getChildAt(i);
            boolean on = i == active;
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setShape(on ? android.graphics.drawable.GradientDrawable.RECTANGLE
                           : android.graphics.drawable.GradientDrawable.OVAL);
            if (on) bg.setCornerRadius(4 * dp);
            bg.setColor(android.graphics.Color.parseColor(on ? "#008b8b" : "#2A3A3A"));
            dot.setBackground(bg);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) dot.getLayoutParams();
            lp.width = on ? (int) (15 * dp) : (int) (6 * dp);
            lp.height = (int) (6 * dp);
            dot.setLayoutParams(lp);
        }
    }

    private void scheduleBriefingAutoScroll() {
        if (briefingTick != null) briefingAutoScroll.removeCallbacks(briefingTick);
        if (briefingCount <= 1 || briefingPager == null) return;
        briefingTick = () -> {
            if (briefingPager == null || briefingCount <= 1) return;
            int next = (briefingPager.getCurrentItem() + 1) % briefingCount;
            briefingPager.setCurrentItem(next, true);
            briefingAutoScroll.postDelayed(briefingTick, BRIEFING_INTERVAL_MS);
        };
        briefingAutoScroll.postDelayed(briefingTick, BRIEFING_INTERVAL_MS);
    }

    // ========== DAILY DIGEST ==========

    private static final long ADVISORY_CACHE_TTL_MS = 36 * 60 * 60 * 1000L; // 36 hours

    private void fetchDailyDigest() {
        if (dailyDigestCard == null) return;
        Context context = getContext();
        if (context == null) return;

        TokenManager tokenManager = TokenManager.getInstance(context);
        String token = tokenManager != null ? tokenManager.getToken() : null;
        if (token == null) return;

        // Read location and AQI context from SharedPreferences (populated by AQI module)
        SharedPreferences aqiPrefs = context.getSharedPreferences("aqi_prefs", Context.MODE_PRIVATE);
        float lat = aqiPrefs.getFloat("current_lat", 0f);
        float lon = aqiPrefs.getFloat("current_lon", 0f);
        int cachedAqi = aqiPrefs.getInt("cached_aqi", -1);
        String cachedCity = aqiPrefs.getString("cached_city", "");

        // Set location chip immediately from cache — don't wait for API response
        if (digestLocationChip != null) {
            if (!cachedCity.isEmpty()) {
                updateLocationChip(cachedCity, true);
            } else {
                updateLocationChip("No location", false);
            }
        }

        // Client-side cache check — avoid network call if advisory is recent
        SharedPreferences advisoryPrefs = context.getSharedPreferences("advisory_cache", Context.MODE_PRIVATE);
        long cachedAt = advisoryPrefs.getLong("cached_at", 0);
        String cachedContent = advisoryPrefs.getString("cached_response", "");
        if (!cachedContent.isEmpty() && (System.currentTimeMillis() - cachedAt) < ADVISORY_CACHE_TTL_MS) {
            try {
                JSONObject cachedJson = new JSONObject(cachedContent);
                String content = cachedJson.optString("content", "");
                if (!content.isEmpty()) {
                    showDigestCard(cachedJson, content);
                    return;
                }
            } catch (JSONException e) {
                Log.e(TAG, "Advisory cache parse error, fetching fresh", e);
            }
        }

        // Build URL with location params if available
        StringBuilder urlBuilder = new StringBuilder(ApiConfig.BASE_URL + "/api/home/daily-digest");
        if (lat != 0f && lon != 0f) {
            urlBuilder.append("?lat=").append(lat).append("&lon=").append(lon);
            if (!cachedCity.isEmpty()) urlBuilder.append("&city=").append(cachedCity);
            if (cachedAqi >= 0) urlBuilder.append("&aqi=").append(cachedAqi);
        }
        final String url = urlBuilder.toString();

        StringRequest request = new StringRequest(Request.Method.GET, url,
                response -> {
                    ApiConfig.logRestCall(url, true, "Health advisory fetched");
                    if (getActivity() == null || !isAdded()) return;
                    requireActivity().runOnUiThread(() -> {
                        try {
                            JSONObject json = new JSONObject(response);
                            String content = json.optString("content", "");
                            if (content.isEmpty()) {
                                // No advisory generated — show a helpful placeholder on the card.
                                advisoryFullContent = "";
                                if (advisoryCard != null) {
                                    advisoryCard.setSubtitle("No advisory today. Complete your health profile or enable location for personalized tips.");
                                    advisoryCard.hidePill();
                                    advisoryCard.hideDate();
                                    advisoryCard.setChevronStatus(Utils.ServiceCardView.ChevronStatus.NORMAL);
                                }
                                return;
                            }

                            // Cache the advisory response locally
                            if (getContext() != null) {
                                getContext().getSharedPreferences("advisory_cache", Context.MODE_PRIVATE)
                                        .edit()
                                        .putString("cached_response", response)
                                        .putLong("cached_at", System.currentTimeMillis())
                                        .apply();
                            }

                            showDigestCard(json, content);
                        } catch (JSONException e) {
                            Log.e(TAG, "Daily digest parse error", e);
                            // Don't show card if parsing fails
                        }
                    });
                },
                error -> {
                    ApiConfig.logRestCall(url, false, error.toString());
                    if (getActivity() == null || !isAdded()) return;
                    ErrorHandler.ParsedError parsed = ErrorHandler.parse(error);
                    final String reason = parsed.type == ErrorHandler.ErrorType.NETWORK_ERROR
                            ? "No connection" : "Unavailable";
                    requireActivity().runOnUiThread(() -> showDigestErrorState(reason));
                }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + token);
                // Bypass Volley HTTP cache — content changes with location/AQI params
                headers.put("Cache-Control", "no-cache");
                return headers;
            }
        };

        request.setRetryPolicy(new DefaultRetryPolicy(15000, 0, 1.0f));
        Volley.newRequestQueue(context).add(request);
    }

    /** Advisory attention chip: warning icon when the card needs the user's attention. */
    private void setDigestAttention(boolean attention) {
        View root = getView();
        if (root == null) return;
        ImageView chip = root.findViewById(R.id.digest_status_chip);
        if (chip == null) return;
        if (attention) {
            chip.setImageResource(R.drawable.ic_warning_yellow);
            chip.setVisibility(View.VISIBLE);
        } else {
            chip.setVisibility(View.GONE);
        }
    }

    /**
     * Populates the Daily Advisory ServiceCard (the digest text, split from the
     * Briefing carousel). Subtitle = advisory content (≤3 lines, tap to expand),
     * meta = "Updated X ago" from generatedAt, chevron = ATTENTION when stale.
     */
    private void showDigestCard(JSONObject json, String content) {
        advisoryFullContent = content;
        if (advisoryCard == null) return;

        advisoryCard.setSubtitle(content);

        boolean stale = json.optBoolean("stale", false);
        String generatedAt = json.optString("generatedAt", "");
        if (generatedAt != null && !generatedAt.isEmpty()) {
            advisoryCard.setDate(formatTimeAgo(generatedAt));
        } else {
            advisoryCard.hideDate();
        }

        if (stale) {
            advisoryCard.setPill(Utils.StatusPill.Intent.WARNING, "Update");
            advisoryCard.setChevronStatus(Utils.ServiceCardView.ChevronStatus.ATTENTION);
        } else {
            advisoryCard.hidePill();
            advisoryCard.setChevronStatus(Utils.ServiceCardView.ChevronStatus.NORMAL);
        }
    }

    private void showDigestErrorState() {
        showDigestErrorState("Unavailable");
    }

    /** Error state for the Daily Advisory card. Subtitle carries the reason and a
     *  tap retries the fetch (falls back to the advisory dialog once content loads). */
    private void showDigestErrorState(String reason) {
        if (advisoryCard == null) return;
        advisoryFullContent = "";
        advisoryCard.setSubtitle((reason != null ? reason : "Unavailable") + " — tap to retry");
        advisoryCard.hidePill();
        advisoryCard.hideDate();
        advisoryCard.setChevronStatus(Utils.ServiceCardView.ChevronStatus.ATTENTION);
        advisoryCard.setOnClickListener(v -> {
            // Restore the normal expand-on-tap behaviour after a retry is triggered.
            advisoryCard.setOnClickListener(vv -> showAdvisoryDialog());
            fetchDailyDigest();
        });
    }

    /**
     * Updates the location chip text, color, and drawable icon tint + size.
     */
    private void updateLocationChip(String text, boolean hasLocation) {
        if (digestLocationChip == null) return;
        Skeleton.hide(digestLocationChip);
        digestLocationChip.setText(text);
        int color = Color.parseColor(hasLocation ? "#008b8b" : "#555555");
        digestLocationChip.setTextColor(color);

        // Set a properly sized and tinted location icon
        android.graphics.drawable.Drawable icon = androidx.core.content.ContextCompat.getDrawable(
                digestLocationChip.getContext(), com.example.richhealth.R.drawable.ic_location);
        if (icon != null) {
            icon = icon.mutate();
            int sizePx = (int) (12 * digestLocationChip.getResources().getDisplayMetrics().density);
            icon.setBounds(0, 0, sizePx, sizePx);
            icon.setTint(color);
            digestLocationChip.setCompoundDrawables(icon, null, null, null);
            digestLocationChip.setCompoundDrawablePadding(
                    (int) (3 * digestLocationChip.getResources().getDisplayMetrics().density));
        }
    }

    // ========== NEW METHODS: HEALTH ANALYSIS ==========

    private void loadAndDisplayHealthAnalysis() {
        if (healthScoreText == null) return;
        String url = ApiConfig.BASE_URL + "/api/health/analysis";
        Context context = requireContext();
        TokenManager tokenManager = TokenManager.getInstance(context);
        String token = tokenManager.getToken();

        if (token == null) {
            Skeleton.hide(healthScoreText);
            if (healthStatusChip != null) healthStatusChip.setVisibility(View.GONE);
            healthScoreText.setText("Login required");
            return;
        }

        StringRequest request = new StringRequest(Request.Method.GET, url,
            response -> {
                try {
                    ApiConfig.logRestCall(url, true, "Health analysis fetched");
                    Log.d(TAG, "Health analysis response: " + response);
                    Skeleton.hide(healthScoreText, healthAnalysisUpdatedTime,
                            dietarySecondaryInfo, nutriSecondaryInfo);
                    JSONObject jsonResponse = new JSONObject(response);

                    boolean success = jsonResponse.optBoolean("success", false);
                    JSONObject analysis = jsonResponse.optJSONObject("analysis");

                    if (analysis == null) {
                        // No analysis yet — hide status icon, show inviting CTA for new users
                        if (healthStatusChip != null) healthStatusChip.setVisibility(View.GONE);
                        currentStatusLevel = "";
                        currentStatusReason = "";
                        if (healthScoreText != null) {
                            healthScoreText.setText("Generate your first analysis");
                        }
                        if (healthAnalysisLastUpdated != null) healthAnalysisLastUpdated.setVisibility(View.GONE);
                        if (healthAnalysisUpdatedTime != null) {
                            healthAnalysisUpdatedTime.setText("No analysis yet");
                            healthAnalysisUpdatedTime.setVisibility(View.VISIBLE);
                        }
                        return;
                    }

                    // Cache full response for dialog use
                    lastHealthAnalysisJson = analysis;

                    // Sync profile completion from backend (single source of truth)
                    JSONObject profileComp = analysis.optJSONObject("profileCompletion");
                    if (profileComp != null) {
                        int backendPct = profileComp.optInt("percent", -1);
                        if (backendPct >= 0) {
                            cachedProfilePercent = backendPct;
                            if (weightProgressText != null) weightProgressText.setText(backendPct + "%");
                        }
                        // Update status text with backend missing fields
                        if (profileCompletionStatus != null) {
                            JSONArray missingArr = profileComp.optJSONArray("missing");
                            if (backendPct >= 100) {
                                profileCompletionStatus.setText("Profile complete");
                            } else if (missingArr != null && missingArr.length() > 0) {
                                String first = missingArr.optString(0, "");
                                profileCompletionStatus.setText("Missing: " + first
                                    + (missingArr.length() > 1 ? " & more" : ""));
                            }
                        }
                    }

                    // Check if analysis was ever actually generated
                    String lastUpdatedStr = analysis.optString("lastUpdated", null);
                    boolean neverGenerated = (lastUpdatedStr == null || lastUpdatedStr.isEmpty());

                    if (neverGenerated) {
                        // Analysis endpoint returned data but no headline was ever generated
                        if (healthStatusChip != null) healthStatusChip.setVisibility(View.GONE);
                        currentStatusLevel = "";
                        currentStatusReason = "";
                        if (healthScoreText != null) {
                            healthScoreText.setText("Generate your first analysis");
                        }
                        if (healthAnalysisLastUpdated != null) healthAnalysisLastUpdated.setVisibility(View.GONE);
                        if (healthAnalysisUpdatedTime != null) {
                            healthAnalysisUpdatedTime.setText("No analysis yet");
                            healthAnalysisUpdatedTime.setVisibility(View.VISIBLE);
                        }
                    } else {
                        // Headline
                        String headline = analysis.optString("headline", "Your health is looking good");
                        if (healthScoreText != null) {
                            healthScoreText.setText(headline);
                        }

                        // Health Status (with defaults)
                        JSONObject healthStatus = analysis.optJSONObject("healthAnalysisStatus");
                        if (healthStatus != null) {
                            String statusLevel = healthStatus.optString("level", "");
                            String reason = healthStatus.optString("reason", "");
                            updateStatusChipColor(statusLevel, reason);
                        } else {
                            updateStatusChipColor("", "");
                        }

                        // Meta line: when the last insight was generated.
                        if (healthAnalysisUpdatedTime != null) {
                            healthAnalysisUpdatedTime.setText(formatTimeAgo(lastUpdatedStr));
                            healthAnalysisUpdatedTime.setVisibility(View.VISIBLE);
                        }
                        // Hide stale pill
                        if (healthAnalysisLastUpdated != null) {
                            healthAnalysisLastUpdated.setVisibility(View.GONE);
                        }
                    }

                    // Metrics (with safe access)
                    JSONObject metrics = analysis.optJSONObject("metrics");
                    if (metrics != null) {
                        if (healthAnalysisLocation != null) {
                            String location = metrics.optString("location", "Location unavailable");
                            healthAnalysisLocation.setText(location);
                        }
                        if (healthAnalysisAQI != null) {
                            int aqi = metrics.optInt("aqi", 0);
                            healthAnalysisAQI.setText("AQI: " + aqi);
                        }
                    }

                    // Profile completion — prefer backend value, fall back to local calculation
                    if (healthAnalysisProfilePercent != null) {
                        int backendPct = (profileComp != null) ? profileComp.optInt("percent", -1) : -1;
                        int displayPct = (backendPct >= 0) ? backendPct : cachedProfilePercent;
                        healthAnalysisProfilePercent.setText("Profile: " + displayPct + "%");
                    }

                    // Parse per-type analysis cache (new backend fields)
                    if (analysis.has("healthAnalysisCache") && !analysis.isNull("healthAnalysisCache")) {
                        cachedTypeAnalyses = analysis.getJSONObject("healthAnalysisCache");
                    }

                    // When data changed since the last analysis, surface a WARNING "Update"
                    // pill (standard) instead of the old tiny warning-triangle badge.
                    boolean needsUpdate = analysis.optBoolean("healthDataNeedsUpdate", false);
                    if (needsUpdate && healthStatusPill != null) {
                        Utils.StatusPill.apply(healthStatusPill, Utils.StatusPill.Intent.WARNING, "Update");
                        healthStatusPill.setOnClickListener(v -> showStaleDataInfoDialog("Health Analysis"));
                        // Keep the bare "X ago" meta visible; the pill conveys staleness.
                    }
                    // Legacy 0dp badge kept in sync (invisible) so nothing else breaks.
                    if (healthAnalysisLastUpdated != null) {
                        healthAnalysisLastUpdated.setVisibility(needsUpdate ? View.VISIBLE : View.GONE);
                        healthAnalysisLastUpdated.setOnClickListener(needsUpdate
                                ? v -> showStaleDataInfoDialog("Health Analysis") : null);
                    }
                    // Status-coloured chevron (shared iOS contract: URGENT > ATTENTION > NORMAL).
                    if (healthAnalysisCard != null) {
                        // Chevron encodes urgency/staleness, NOT the health state (the pill does that),
                        // so BAD/NEEDS_ATTENTION do NOT trigger the yellow chevron — only a critical
                        // result (red) or new data since the last analysis (yellow). Matches iOS.
                        Utils.ServiceCardView.ChevronStatus cs;
                        if ("CRITICAL".equals(currentStatusLevel)) {
                            cs = Utils.ServiceCardView.ChevronStatus.URGENT;
                        } else if (needsUpdate) {
                            cs = Utils.ServiceCardView.ChevronStatus.ATTENTION;
                        } else {
                            cs = Utils.ServiceCardView.ChevronStatus.NORMAL;
                        }
                        healthAnalysisCard.setChevronStatus(cs);
                    }

                    // Per-feature stale indicators: compare lastHealthDataChange vs each feature's generatedAt
                    String lastDataChangeStr = analysis.optString("lastHealthDataChange", null);
                    long lastDataChange = lastDataChangeStr != null ? parseIsoToMillis(lastDataChangeStr) : 0;

                    // Dietary insights — stale if data changed after last generation
                    String dietaryUpdatedStr = analysis.optString("dietaryInsightsLastUpdated", null);
                    long dietaryUpdated = dietaryUpdatedStr != null ? parseIsoToMillis(dietaryUpdatedStr) : 0;
                    boolean dietaryIsStale = lastDataChange > 0 && lastDataChange > dietaryUpdated;

                    // NOTE: the dietary CHEVRON is driven solely by the backend `stale` flag in
                    // fetchDietaryInsights() — single source of truth, matching iOS. We do NOT set it
                    // here (avoids a race between the two async responses). dietaryIsStale below only
                    // drives the legacy 0dp "!" indicator.
                    // Show "!" icon ONLY when data has changed after the last Diet Guide run.
                    if (dietaryStaleIndicator != null) {
                        if (dietaryIsStale) {
                            dietaryStaleIndicator.setVisibility(View.VISIBLE);
                            dietaryStaleIndicator.setOnClickListener(v ->
                                    showStaleDataInfoDialog("Diet Guide"));
                        } else {
                            dietaryStaleIndicator.setVisibility(View.GONE);
                            dietaryStaleIndicator.setOnClickListener(null);
                        }
                    }
                    // Secondary info: "Updated X ago" — hidden when stale (mutual exclusivity)
                    if (dietarySecondaryInfo != null) {
                        if (dietaryIsStale) {
                            dietarySecondaryInfo.setVisibility(View.GONE);
                        } else if (dietaryUpdatedStr != null && !dietaryUpdatedStr.isEmpty()) {
                            dietarySecondaryInfo.setText(formatTimeAgo(dietaryUpdatedStr));
                            dietarySecondaryInfo.setVisibility(View.VISIBLE);
                        } else {
                            dietarySecondaryInfo.setVisibility(View.GONE);
                        }
                    }

                    // NutriCheck — stale if data changed after last check
                    String nutriUpdatedStr = analysis.optString("lastNutriCheckAt", null);
                    long nutriUpdated = nutriUpdatedStr != null ? parseIsoToMillis(nutriUpdatedStr) : 0;
                    // Require a prior check before flagging stale — a never-checked user is NORMAL,
                    // not attention (matches iOS nutriCheckIsStale, which needs both timestamps).
                    boolean nutriIsStale = nutriUpdated > 0 && lastDataChange > 0 && lastDataChange > nutriUpdated;

                    // Stale → WARNING "Update" pill (standard); otherwise the pill hides and
                    // the meta line shows "Last check: X ago".
                    if (nutriStatusPill != null) {
                        if (nutriIsStale) {
                            Utils.StatusPill.apply(nutriStatusPill, Utils.StatusPill.Intent.WARNING, "Update");
                            nutriStatusPill.setOnClickListener(v -> showStaleDataInfoDialog("NutriCheck"));
                        } else {
                            nutriStatusPill.setVisibility(View.GONE);
                            nutriStatusPill.setOnClickListener(null);
                        }
                    }
                    // Legacy 0dp badge kept in sync (invisible).
                    if (nutriStaleIndicator != null) {
                        nutriStaleIndicator.setVisibility(nutriIsStale ? View.VISIBLE : View.GONE);
                        nutriStaleIndicator.setOnClickListener(nutriIsStale
                                ? v -> showStaleDataInfoDialog("NutriCheck") : null);
                    }
                    // NutriCheck chevron: ATTENTION when stale, else NORMAL.
                    if (nutriCheckCard != null) {
                        nutriCheckCard.setChevronStatus(nutriIsStale
                                ? Utils.ServiceCardView.ChevronStatus.ATTENTION
                                : Utils.ServiceCardView.ChevronStatus.NORMAL);
                    }
                    // Meta line: always show when NutriCheck was last used (the pill
                    // conveys staleness), or a friendly fallback if never used.
                    if (nutriSecondaryInfo != null) {
                        if (nutriUpdatedStr != null && !nutriUpdatedStr.isEmpty()) {
                            nutriSecondaryInfo.setText(formatTimeAgo(nutriUpdatedStr));
                        } else {
                            nutriSecondaryInfo.setText("No checks yet");
                        }
                        nutriSecondaryInfo.setVisibility(View.VISIBLE);
                    }

                    // Refresh usage display now that we have real data
                    loadAndDisplayUsageStatus();
                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing health analysis response", e);
                    showHealthAnalysisErrorState("Server error");
                }
            },
            error -> {
                ApiConfig.logRestCall(url, false, error.toString());
                ErrorHandler.ParsedError parsed = ErrorHandler.parse(error);
                if (parsed.type == ErrorHandler.ErrorType.AUTH_EXPIRED) {
                    ErrorHandler.handleAuthExpired(requireContext());
                    return;
                }
                String hint = parsed.type == ErrorHandler.ErrorType.NETWORK_ERROR
                        ? "No connection" : "Server unavailable";
                showHealthAnalysisErrorState(hint);
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
        RequestQueue queue = Volley.newRequestQueue(context);
        queue.add(request);
    }

    /** Shows a compact error pill on the status chip and hides the rest of the
     *  analysis-card skeletons. The parent card remains clickable to retry. */
    private void showHealthAnalysisErrorState(String reason) {
        Skeleton.hideAndGone(healthScoreText, healthAnalysisUpdatedTime,
                dietarySecondaryInfo, nutriSecondaryInfo);
        if (healthStatusChip != null) {
            healthStatusChip.setVisibility(View.GONE);
        }
        if (healthScoreText != null) {
            healthScoreText.setText(reason);
        }
        currentStatusLevel = "";
        currentStatusReason = reason != null ? reason : "";
        if (healthAnalysisLastUpdated != null) healthAnalysisLastUpdated.setVisibility(View.GONE);
    }

    private void showHealthAnalysisDialog() {
        currentAnalysisTab = "reports";
        Dialog dialog = new Dialog(requireContext(), R.style.DialogTheme);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_health_analysis);

        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
        layoutParams.copyFrom(dialog.getWindow().getAttributes());
        layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
        layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
        dialog.getWindow().setAttributes(layoutParams);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        // ── Get all views ──
        // [PLAN-PILL-REVIEW] disabled (backend-driven, keep after review): TextView usageBadge = dialog.findViewById(R.id.dialog_usage_badge);
        TextView statusLine = dialog.findViewById(R.id.dialog_status_line);
        TextView dialogHeadline = dialog.findViewById(R.id.dialog_headline_text);
        TextView dialogReason = dialog.findViewById(R.id.dialog_status_reason);
        com.google.android.material.button.MaterialButton refreshButton = dialog.findViewById(R.id.dialog_refresh_button);
        TextView dialogLastUpdated = dialog.findViewById(R.id.dialog_last_updated_text);

        // Profile stat cells (container + value)
        View cellAge = dialog.findViewById(R.id.dialog_cell_age);
        TextView pillAge = dialog.findViewById(R.id.dialog_pill_age);
        View cellGender = dialog.findViewById(R.id.dialog_cell_gender);
        TextView pillGender = dialog.findViewById(R.id.dialog_pill_gender);
        View cellBlood = dialog.findViewById(R.id.dialog_cell_blood);
        TextView pillBlood = dialog.findViewById(R.id.dialog_pill_blood);
        View cellWeight = dialog.findViewById(R.id.dialog_cell_weight);
        TextView pillWeight = dialog.findViewById(R.id.dialog_pill_weight);
        View cellBmi = dialog.findViewById(R.id.dialog_cell_bmi);
        TextView pillBmi = dialog.findViewById(R.id.dialog_pill_bmi);
        TextView pillBmiLabel = dialog.findViewById(R.id.dialog_pill_bmi_label);
        View cellSleep = dialog.findViewById(R.id.dialog_cell_sleep);
        TextView pillSleep = dialog.findViewById(R.id.dialog_pill_sleep);

        // Medical data grid cells
        TextView pillConditions = dialog.findViewById(R.id.dialog_pill_conditions);
        TextView pillConditionsLabel = dialog.findViewById(R.id.dialog_pill_conditions_label);
        TextView pillMedications = dialog.findViewById(R.id.dialog_pill_medications);
        TextView pillMedicationsLabel = dialog.findViewById(R.id.dialog_pill_medications_label);
        TextView pillReports = dialog.findViewById(R.id.dialog_pill_reports);
        TextView pillReportsLabel = dialog.findViewById(R.id.dialog_pill_reports_label);
        TextView pillFamily = dialog.findViewById(R.id.dialog_pill_family);
        TextView pillFamilyLabel = dialog.findViewById(R.id.dialog_pill_family_label);

        // Health score
        LinearLayout healthScoreSection = dialog.findViewById(R.id.dialog_health_score_section);
        TextView healthScoreLabel = dialog.findViewById(R.id.dialog_health_score_text);
        ProgressBar healthScoreProgress = dialog.findViewById(R.id.dialog_health_score_progress);

        LinearLayout profileSection = dialog.findViewById(R.id.dialog_profile_section);
        TextView profilePercentText = dialog.findViewById(R.id.dialog_profile_percent_text);
        ProgressBar profileProgress = dialog.findViewById(R.id.dialog_profile_progress);
        TextView missingFieldsText = dialog.findViewById(R.id.dialog_missing_fields_text);
        ProgressBar refreshProgress = dialog.findViewById(R.id.dialog_refresh_progress);

        View aqiCard = dialog.findViewById(R.id.dialog_aqi_card);
        TextView aqiLocation = dialog.findViewById(R.id.dialog_aqi_location);
        TextView aqiValue = dialog.findViewById(R.id.dialog_aqi_value);
        TextView aqiQuality = dialog.findViewById(R.id.dialog_aqi_quality);
        TextView aqiRecords = dialog.findViewById(R.id.dialog_aqi_records);
        TextView aqiAnalysis = dialog.findViewById(R.id.dialog_aqi_analysis);
        com.google.android.material.button.MaterialButton closeButton = dialog.findViewById(R.id.dialog_close_button);
        com.google.android.material.button.MaterialButton bottomRefreshButton = dialog.findViewById(R.id.dialog_bottom_refresh_button);

        // ── Per-type analysis views ──
        LinearLayout changeBanner = dialog.findViewById(R.id.dialog_change_banner);
        TextView changeBannerText = dialog.findViewById(R.id.dialog_change_banner_text);
        TextView changeBannerRefresh = dialog.findViewById(R.id.dialog_change_banner_refresh);
        TextView tabReports = dialog.findViewById(R.id.tab_reports);
        TextView tabSymptoms = dialog.findViewById(R.id.tab_symptoms);
        TextView tabMedications = dialog.findViewById(R.id.tab_medications);
        TextView tabMeasurements = dialog.findViewById(R.id.tab_measurements);
        TextView tabGenetics = dialog.findViewById(R.id.tab_genetics);
        View analysisContentCard = dialog.findViewById(R.id.dialog_analysis_content_card);
        LinearLayout analysisShimmer = dialog.findViewById(R.id.dialog_analysis_shimmer);
        TextView analysisSummary = dialog.findViewById(R.id.dialog_analysis_summary);
        LinearLayout analysisDetailsContainer = dialog.findViewById(R.id.dialog_analysis_details_container);
        TextView analysisNoData = dialog.findViewById(R.id.dialog_analysis_no_data);
        TextView analysisGeneratedAt = dialog.findViewById(R.id.dialog_analysis_generated_at);

        TextView[] allTabs = { tabReports, tabSymptoms, tabMedications, tabMeasurements, tabGenetics };
        String[] tabKeys = { "reports", "symptoms", "medications", "measurements", "genetics" };

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
                displayAnalysisTabContent(currentAnalysisTab, analysisSummary, analysisDetailsContainer, analysisNoData, analysisGeneratedAt, analysisShimmer);
            });
        }

        // Populate initial tab + sync visual highlight
        for (int t = 0; t < tabKeys.length; t++) {
            if (tabKeys[t].equals(currentAnalysisTab)) {
                allTabs[t].setBackgroundResource(R.drawable.pill_tab_selected);
                allTabs[t].setTextColor(Color.WHITE);
            } else {
                allTabs[t].setBackgroundResource(R.drawable.pill_tab_unselected);
                allTabs[t].setTextColor(Color.parseColor("#AAAAAA"));
            }
        }
        displayAnalysisTabContent(currentAnalysisTab, analysisSummary, analysisDetailsContainer, analysisNoData, analysisGeneratedAt, analysisShimmer);

        // ── Change banner from backend dataChangesSinceAnalysis ──
        if (lastHealthAnalysisJson != null) {
            boolean needsUpdate = lastHealthAnalysisJson.optBoolean("healthDataNeedsUpdate", false);
            JSONObject changes = lastHealthAnalysisJson.optJSONObject("dataChangesSinceAnalysis");
            if (needsUpdate && changes != null) {
                int rc = changes.optInt("reports", 0);
                int sc = changes.optInt("symptoms", 0);
                int mc = changes.optInt("medications", 0);
                int msc = changes.optInt("measurements", 0);
                int total = rc + sc + mc + msc;
                if (total > 0) {
                    List<String> parts = new ArrayList<>();
                    if (rc > 0) parts.add(rc + " report" + (rc > 1 ? "s" : ""));
                    if (sc > 0) parts.add(sc + " symptom" + (sc > 1 ? "s" : ""));
                    if (mc > 0) parts.add(mc + " medication" + (mc > 1 ? "s" : ""));
                    if (msc > 0) parts.add(msc + " measurement" + (msc > 1 ? "s" : ""));
                    changeBannerText.setText("Changed since last analysis: " + String.join(", ", parts));
                    changeBanner.setVisibility(View.VISIBLE);
                }
            }
        }

        // Banner refresh tap
        changeBannerRefresh.setOnClickListener(v -> {
            changeBanner.setVisibility(View.GONE);
            refreshAllAnalyses(dialogHeadline, dialogLastUpdated, refreshButton, refreshProgress,
                    bottomRefreshButton, analysisSummary, analysisDetailsContainer, analysisNoData, analysisGeneratedAt, analysisShimmer);
        });

        // ── Load cached user analysis for lifestyle data ──
        JSONObject userAnalysis = getCachedUserAnalysis();

        // ── PLAN BADGE (inline in header) ──
        if (proStatusManager != null) {
            // [PLAN-PILL-REVIEW] disabled (backend-driven, keep after review): usageBadge.setVisibility(View.VISIBLE);
            String tier = proStatusManager.getUserTier();

            // Try to read usageStatus from backend response
            JSONObject usageStatus = (lastHealthAnalysisJson != null) ? lastHealthAnalysisJson.optJSONObject("usageStatus") : null;

            if (usageStatus != null) {
                int count = usageStatus.optInt("count", 0);
                Object limitObj = usageStatus.opt("limit");
                boolean isUnlimited = (limitObj == null || limitObj.toString().equals("null"));

                if (isUnlimited) {
                    // [PLAN-PILL-REVIEW] disabled (backend-driven, keep after review): Utils.PlanBadge.apply(usageBadge, tier, Utils.PlanBadge.Style.COMPACT, " · Unlimited");
                } else {
                    int limit = usageStatus.optInt("limit", 1);
                    // [PLAN-PILL-REVIEW] disabled (backend-driven, keep after review): Utils.PlanBadge.apply(usageBadge, tier, Utils.PlanBadge.Style.COMPACT,
                            // " · " + count + "/" + limit);
                }
            } else {
                // Fallback: tier-based badge
                switch (tier) {
                    case "ultra":
                    case "pro":
                    case "family":
                    case "family_member":
                    case "plus":
                        // [PLAN-PILL-REVIEW] disabled (backend-driven, keep after review): Utils.PlanBadge.apply(usageBadge, tier, Utils.PlanBadge.Style.COMPACT, " · Unlimited");
                        break;
                    default:
                        boolean used = isAnalysisUsedThisMonth();
                        // [PLAN-PILL-REVIEW] disabled (backend-driven, keep after review): Utils.PlanBadge.apply(usageBadge, "free", Utils.PlanBadge.Style.COMPACT,
                                // " · " + (used ? "1" : "0") + "/1");
                        break;
                }
            }
        } else {
            // [PLAN-PILL-REVIEW] disabled (backend-driven, keep after review): usageBadge.setVisibility(View.GONE);
        }

        // ── Populate from cached health analysis API ──
        if (lastHealthAnalysisJson != null) {
            try {
                // Check if analysis was ever actually generated
                String lastUpdated = lastHealthAnalysisJson.optString("lastUpdated", null);
                boolean neverGenerated = (lastUpdated == null || lastUpdated.isEmpty());

                if (neverGenerated) {
                    // === NEVER GENERATED STATE ===
                    statusLine.setText("● Not yet generated");
                    statusLine.setTextColor(Color.parseColor("#808080"));
                    dialogHeadline.setText("Tap Refresh to generate your first health analysis");
                    dialogReason.setText("We need to analyze your health data to provide insights");
                    dialogLastUpdated.setText("");
                } else {
                    // === AI INSIGHT CARD (generated state) ===
                    String headline = lastHealthAnalysisJson.optString("headline", "Complete your profile for personalized analysis");
                    dialogHeadline.setText(headline);

                    JSONObject healthStatus = lastHealthAnalysisJson.optJSONObject("healthAnalysisStatus");
                    String statusLevel = "";
                    String reason = "";
                    if (healthStatus != null) {
                        statusLevel = healthStatus.optString("level", "");
                        reason = healthStatus.optString("reason", "");
                    }

                    // Map status to meaningful text (not raw chip)
                    statusLine.setText(getStatusText(statusLevel));
                    statusLine.setTextColor(getStatusColor(statusLevel));
                    dialogReason.setText(reason);

                    String timeAgo = formatTimeAgo(lastUpdated);
                    dialogLastUpdated.setText("Generated " + timeAgo);
                }

                // === PROFILE SNAPSHOT (pill-based) ===
                JSONObject metrics = lastHealthAnalysisJson.optJSONObject("metrics");
                JSONObject dataPoints = lastHealthAnalysisJson.optJSONObject("dataPoints");

                // ── Cell: Age ──
                if (!neverGenerated && metrics != null && !metrics.isNull("age")) {
                    int age = metrics.optInt("age", 0);
                    if (age > 0) {
                        pillAge.setText(String.valueOf(age));
                        cellAge.setVisibility(View.VISIBLE);
                    }
                }

                // ── Cell: Gender ──
                if (userAnalysis != null) {
                    JSONObject profile = userAnalysis.optJSONObject("profile");
                    if (profile != null) {
                        String gender = profile.optString("gender", "");
                        if (!gender.isEmpty() && !gender.equals("null")) {
                            pillGender.setText(gender.substring(0, 1).toUpperCase() + gender.substring(1));
                            cellGender.setVisibility(View.VISIBLE);
                        }
                    }
                }

                // ── Cell: Blood Group ──
                if (userAnalysis != null) {
                    JSONObject medData = userAnalysis.optJSONObject("medicalData");
                    if (medData != null) {
                        String bloodType = medData.optString("bloodType", "");
                        if (!bloodType.isEmpty() && !bloodType.equals("null")) {
                            pillBlood.setText(bloodType);
                            cellBlood.setVisibility(View.VISIBLE);
                        }
                    }
                }

                // ── Cell: Weight ──
                if (userAnalysis != null) {
                    JSONObject profile = userAnalysis.optJSONObject("profile");
                    JSONObject healthMetrics = profile != null ? profile.optJSONObject("healthMetrics") : null;
                    if (healthMetrics != null) {
                        double weight = healthMetrics.optDouble("weight", 0);
                        if (weight > 0) {
                            pillWeight.setText(Math.round(weight) + " kg");
                            cellWeight.setVisibility(View.VISIBLE);
                        }
                    }
                }

                // ── Cell: BMI ──
                if (!neverGenerated && metrics != null && !metrics.isNull("bmi")) {
                    double bmi = metrics.optDouble("bmi", 0);
                    if (bmi > 0) {
                        pillBmi.setText(String.format("%.1f", bmi));
                        pillBmiLabel.setText("BMI \u00B7 " + getBmiCategory(bmi));
                        cellBmi.setVisibility(View.VISIBLE);
                    }
                }

                // ── Cell: Sleep ──
                if (userAnalysis != null) {
                    JSONObject profile = userAnalysis.optJSONObject("profile");
                    JSONObject lifestyleData = profile != null ? profile.optJSONObject("lifestyle") : null;
                    if (lifestyleData != null) {
                        double sleepHours = lifestyleData.optDouble("sleepHours", 0);
                        if (sleepHours > 0) {
                            pillSleep.setText(sleepHours + "h");
                            cellSleep.setVisibility(View.VISIBLE);
                        }
                    }
                }

                // ── Health Score from LLM overall analysis ──
                if (cachedTypeAnalyses != null && cachedTypeAnalyses.has("overall")) {
                    try {
                        JSONObject overall = cachedTypeAnalyses.getJSONObject("overall");
                        String overallText = overall.optString("text", "{}");
                        JSONObject overallParsed = new JSONObject(overallText);
                        int score = overallParsed.optInt("healthScore", -1);
                        if (score >= 0) {
                            healthScoreSection.setVisibility(View.VISIBLE);
                            healthScoreLabel.setText(score + "/100");
                            healthScoreProgress.setProgress(score);
                            // Color based on score
                            int scoreColor;
                            if (score >= 80) scoreColor = Color.parseColor("#4CAF50");
                            else if (score >= 60) scoreColor = Color.parseColor("#008b8b");
                            else if (score >= 40) scoreColor = Color.parseColor("#FFC107");
                            else scoreColor = Color.parseColor("#F44336");
                            healthScoreLabel.setTextColor(scoreColor);
                            healthScoreProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(scoreColor));
                        }
                    } catch (JSONException ignored) {}
                }

                // ── Medical Data Grid (stat cells) ──
                int medCount = dataPoints != null ? dataPoints.optInt("medications", 0) : 0;
                int reportCount = dataPoints != null ? dataPoints.optInt("reports", 0) : 0;
                int familyCount = dataPoints != null ? dataPoints.optInt("familyMembers", 0) : 0;

                int conditionCount = 0;
                int allergyCount = 0;
                if (userAnalysis != null) {
                    JSONObject medData = userAnalysis.optJSONObject("medicalData");
                    if (medData != null) {
                        conditionCount = medData.optInt("conditionCount", 0);
                        allergyCount = medData.optInt("allergyCount", 0);
                    }
                }

                // Conditions cell
                int totalConditions = conditionCount + allergyCount;
                pillConditions.setText(String.valueOf(totalConditions));
                if (totalConditions > 0) {
                    pillConditions.setTextColor(Color.parseColor("#FFC107"));
                    String label = conditionCount > 0 && allergyCount > 0
                            ? conditionCount + " condition" + (conditionCount > 1 ? "s" : "") + " · " + allergyCount + " allerg" + (allergyCount > 1 ? "ies" : "y")
                            : conditionCount > 0
                                ? "Condition" + (conditionCount > 1 ? "s" : "")
                                : "Allerg" + (allergyCount > 1 ? "ies" : "y");
                    pillConditionsLabel.setText(label);
                } else {
                    pillConditions.setTextColor(Color.parseColor("#4CAF50"));
                    pillConditionsLabel.setText("No Conditions");
                }

                // Medications cell
                pillMedications.setText(String.valueOf(medCount));
                if (medCount > 0) {
                    pillMedications.setTextColor(Color.parseColor("#2196F3"));
                    pillMedicationsLabel.setText("Active Med" + (medCount > 1 ? "s" : ""));
                } else {
                    pillMedications.setTextColor(Color.parseColor("#808080"));
                    pillMedicationsLabel.setText("No Medications");
                }

                // Reports cell
                pillReports.setText(String.valueOf(reportCount));
                if (reportCount > 0) {
                    pillReports.setTextColor(Color.parseColor("#FF9800"));
                    pillReportsLabel.setText("Report" + (reportCount > 1 ? "s" : "") + " Uploaded");
                } else {
                    pillReports.setTextColor(Color.parseColor("#808080"));
                    pillReportsLabel.setText("No Reports");
                }

                // Family cell
                pillFamily.setText(String.valueOf(familyCount));
                if (familyCount > 0) {
                    pillFamily.setTextColor(Color.parseColor("#4CAF50"));
                    pillFamilyLabel.setText("Family Linked");
                } else {
                    pillFamily.setTextColor(Color.parseColor("#808080"));
                    pillFamilyLabel.setText("No Family");
                }

                // ── Profile completion + missing fields (single source: backend) ──
                JSONObject dialogProfileComp = lastHealthAnalysisJson != null
                        ? lastHealthAnalysisJson.optJSONObject("profileCompletion") : null;
                int dialogPct = dialogProfileComp != null ? dialogProfileComp.optInt("percent", cachedProfilePercent) : cachedProfilePercent;
                if (dialogPct >= 0 && dialogPct < 100) {
                    profileSection.setVisibility(View.VISIBLE);
                    profilePercentText.setText(dialogPct + "% complete");
                    profileProgress.setProgress(dialogPct);

                    JSONArray dialogMissing = dialogProfileComp != null ? dialogProfileComp.optJSONArray("missing") : null;
                    if (dialogMissing != null && dialogMissing.length() > 0) {
                        StringBuilder missingStr = new StringBuilder("Missing: ");
                        for (int i = 0; i < dialogMissing.length(); i++) {
                            if (i > 0) missingStr.append(", ");
                            missingStr.append(dialogMissing.optString(i, ""));
                        }
                        missingFieldsText.setVisibility(View.VISIBLE);
                        missingFieldsText.setText(missingStr.toString());
                    } else {
                        missingFieldsText.setVisibility(View.GONE);
                    }
                } else {
                    profileSection.setVisibility(View.GONE);
                }

            } catch (Exception e) {
                Log.e(TAG, "Error populating health analysis dialog", e);
                dialogHeadline.setText("Unable to load analysis");
                dialogReason.setText("Please try again later");
                statusLine.setText("● Data unavailable");
                statusLine.setTextColor(Color.parseColor("#808080"));
            }
        } else {
            // No cached data — fallback
            if (healthScoreText != null && healthScoreText.getText() != null
                    && !healthScoreText.getText().toString().equals("Loading health analysis...")) {
                dialogHeadline.setText(healthScoreText.getText());
            } else {
                dialogHeadline.setText("Tap refresh to generate your analysis");
            }
            if (currentStatusReason != null && !currentStatusReason.isEmpty()) {
                dialogReason.setText(currentStatusReason);
            }
            if (currentStatusLevel != null && !currentStatusLevel.isEmpty()) {
                statusLine.setText(getStatusText(currentStatusLevel));
                statusLine.setTextColor(getStatusColor(currentStatusLevel));
            }
            dialogLastUpdated.setText("Not yet generated");
            // Hide all stat cells in fallback
            cellAge.setVisibility(View.GONE);
            cellGender.setVisibility(View.GONE);
            cellBlood.setVisibility(View.GONE);
            cellWeight.setVisibility(View.GONE);
            cellBmi.setVisibility(View.GONE);
            cellSleep.setVisibility(View.GONE);
            pillConditions.setText("–");
            pillConditions.setTextColor(Color.parseColor("#808080"));
            pillConditionsLabel.setText("Conditions");
            pillMedications.setText("–");
            pillMedications.setTextColor(Color.parseColor("#808080"));
            pillMedicationsLabel.setText("Medications");
            pillReports.setText("–");
            pillReports.setTextColor(Color.parseColor("#808080"));
            pillReportsLabel.setText("Reports");
            pillFamily.setText("–");
            pillFamily.setTextColor(Color.parseColor("#808080"));
            pillFamilyLabel.setText("Family");
            profileSection.setVisibility(View.GONE);
            healthScoreSection.setVisibility(View.GONE);
        }

        // ── AQI from SharedPreferences cache (independent of health analysis) ──
        try {
            android.content.SharedPreferences aqiPrefs = requireContext().getSharedPreferences("aqi_prefs", Context.MODE_PRIVATE);
            int cachedAqi = aqiPrefs.getInt("cached_aqi", 0);
            String cachedCity = aqiPrefs.getString("cached_city", "");
            int aqiRecordCount = aqiPrefs.getInt("cached_aqi_record_count", 0);
            String cachedAqiAnalysis = aqiPrefs.getString("cached_aqi_analysis", "");

            if (cachedAqi > 0) {
                aqiValue.setText("AQI " + cachedAqi);
                String quality = getAqiQualityLabel(cachedAqi);
                aqiQuality.setText(quality);
                aqiQuality.setTextColor(getAqiColor(cachedAqi));
                if (cachedCity != null && !cachedCity.isEmpty()) {
                    aqiLocation.setText(cachedCity);
                } else {
                    aqiLocation.setText("Air Quality");
                }
                // Records count
                if (aqiRecordCount > 0) {
                    aqiRecords.setText(aqiRecordCount + " record" + (aqiRecordCount > 1 ? "s" : ""));
                    aqiRecords.setVisibility(View.VISIBLE);
                }
                // Local fallback if backend hasn't provided analysis yet
                if (cachedAqiAnalysis.isEmpty()) {
                    if (cachedAqi <= 50) cachedAqiAnalysis = "Air quality is good. Safe for outdoor activities.";
                    else if (cachedAqi <= 100) cachedAqiAnalysis = "Moderate air quality. Sensitive groups should limit outdoor exertion.";
                    else if (cachedAqi <= 150) cachedAqiAnalysis = "Unhealthy for sensitive groups. Consider reducing outdoor activities.";
                    else cachedAqiAnalysis = "Unhealthy air quality. Avoid prolonged outdoor activities.";
                }
                // Analysis summary
                if (!cachedAqiAnalysis.isEmpty()) {
                    aqiAnalysis.setText(cachedAqiAnalysis);
                    aqiAnalysis.setVisibility(View.VISIBLE);
                }
            } else {
                aqiValue.setText("--");
                aqiLocation.setText("Air Quality");
                aqiQuality.setText("No data yet");
                aqiQuality.setTextColor(Color.parseColor("#808080"));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading AQI cache for dialog", e);
            aqiValue.setText("--");
            aqiLocation.setText("Air Quality");
            aqiQuality.setText("No data yet");
        }

        // ── Refresh button: triggers full LLM analysis refresh ──
        refreshButton.setOnClickListener(v -> {
            refreshButton.setVisibility(View.INVISIBLE);
            refreshProgress.setVisibility(View.VISIBLE);
            dialogLastUpdated.setText("Generating analysis...");
            if (changeBanner != null) changeBanner.setVisibility(View.GONE);
            refreshAllAnalyses(dialogHeadline, dialogLastUpdated, refreshButton, refreshProgress,
                    bottomRefreshButton, analysisSummary, analysisDetailsContainer, analysisNoData, analysisGeneratedAt, analysisShimmer);
        });

        // ── AQI card tap: opens existing AQI history dialog ──
        aqiCard.setOnClickListener(v -> {
            dialog.dismiss();
            fetchAndShowAQIHistory();
        });

        // ── Close button ──
        closeButton.setOnClickListener(v -> dialog.dismiss());

        // ── Bottom refresh button (same action as inline refresh) ──
        if (bottomRefreshButton != null) {
            bottomRefreshButton.setOnClickListener(v -> {
                refreshButton.setVisibility(View.INVISIBLE);
                refreshProgress.setVisibility(View.VISIBLE);
                dialogLastUpdated.setText("Generating analysis...");
                bottomRefreshButton.setEnabled(false);
                bottomRefreshButton.setText("Generating...");
                if (changeBanner != null) changeBanner.setVisibility(View.GONE);
                refreshAllAnalyses(dialogHeadline, dialogLastUpdated, refreshButton, refreshProgress,
                        bottomRefreshButton, analysisSummary, analysisDetailsContainer, analysisNoData, analysisGeneratedAt, analysisShimmer);
            });
        }

        dialog.show();
    }

    // Read cached user analysis from SharedPreferences
    private JSONObject getCachedUserAnalysis() {
        try {
            Context context = getContext();
            if (context == null) return null;
            SharedPreferences prefs = context.getSharedPreferences("user_analysis_cache", Context.MODE_PRIVATE);
            String cachedData = prefs.getString("analysis_data", null);
            if (cachedData != null) {
                JSONObject response = new JSONObject(cachedData);
                return response.optJSONObject("analysis");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading cached user analysis", e);
        }
        return null;
    }

    // ═══════ PER-TYPE ANALYSIS: display content for selected tab ═══════
    private void displayAnalysisTabContent(String tabKey, TextView summaryView,
            LinearLayout detailsContainer, TextView noDataView, TextView generatedAtView, View shimmerView) {
        // Reset all views
        summaryView.setVisibility(View.GONE);
        detailsContainer.setVisibility(View.GONE);
        detailsContainer.removeAllViews();
        noDataView.setVisibility(View.GONE);
        generatedAtView.setVisibility(View.GONE);
        stopShimmerAnimation(shimmerView);

        if (cachedTypeAnalyses == null || !cachedTypeAnalyses.has(tabKey)) {
            if ("genetics".equals(tabKey)) {
                noDataView.setText("Link family members and refresh to get genetics analysis");
            } else {
                noDataView.setText("Tap Refresh Analysis to generate insights");
            }
            noDataView.setVisibility(View.VISIBLE);
            return;
        }

        try {
            JSONObject typeData = cachedTypeAnalyses.getJSONObject(tabKey);
            String textStr = typeData.optString("text", "{}");
            String generatedAt = typeData.optString("generatedAt", "");

            JSONObject parsed = new JSONObject(textStr);

            if (parsed.optBoolean("noData", false)) {
                // No data for this type
                noDataView.setText(parsed.optString("message", "No data available for analysis"));
                noDataView.setVisibility(View.VISIBLE);
            } else if (parsed.has("error")) {
                // LLM error
                noDataView.setText(parsed.optString("error", "Analysis temporarily unavailable"));
                noDataView.setVisibility(View.VISIBLE);
            } else {
                // Success — show summary + details
                String summary = parsed.optString("summary", "");
                if (!summary.isEmpty()) {
                    summaryView.setText(summary);
                    summaryView.setVisibility(View.VISIBLE);
                }

                detailsContainer.setVisibility(View.VISIBLE);

                // Type-specific arrays to display
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
                        addAnalysisSectionHeader(detailsContainer, section[1]);
                        for (int i = 0; i < arr.length(); i++) {
                            addAnalysisBullet(detailsContainer, arr.optString(i, ""));
                        }
                    }
                }

                // adherenceInsights is a string, not array
                String adherence = parsed.optString("adherenceInsights", "");
                if (!adherence.isEmpty()) {
                    addAnalysisSectionHeader(detailsContainer, "Adherence");
                    addAnalysisBullet(detailsContainer, adherence);
                }
            }

            // Generated timestamp
            if (!generatedAt.isEmpty()) {
                generatedAtView.setText("Generated " + formatTimeAgo(generatedAt));
                generatedAtView.setVisibility(View.VISIBLE);
            }

        } catch (JSONException e) {
            Log.e(TAG, "Error displaying analysis for tab: " + tabKey, e);
            noDataView.setText("Error displaying analysis");
            noDataView.setVisibility(View.VISIBLE);
        }
    }

    private void addAnalysisSectionHeader(LinearLayout container, String title) {
        TextView tv = new TextView(requireContext());
        tv.setText(title);
        tv.setTextColor(Color.parseColor("#008b8b"));
        tv.setTextSize(12);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        int dp8 = (int)(8 * getResources().getDisplayMetrics().density);
        tv.setPadding(0, dp8 * 2, 0, dp8 / 2);
        container.addView(tv);
    }

    private void addAnalysisBullet(LinearLayout container, String text) {
        if (text == null || text.isEmpty()) return;
        TextView tv = new TextView(requireContext());
        tv.setText("• " + text);
        tv.setTextColor(Color.parseColor("#CCCCCC"));
        tv.setTextSize(12);
        tv.setLineSpacing(1.3f,1);
        int dp4 = (int)(4 * getResources().getDisplayMetrics().density);
        tv.setPadding(dp4 * 2, dp4 / 2, 0, dp4 / 2);
        container.addView(tv);
    }

    // ═══════ SHIMMER ANIMATION HELPERS ═══════
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

    // ═══════ REFRESH ALL ANALYSES: call POST /api/health/analysis/generate ═══════
    private void refreshAllAnalyses(TextView headlineView, TextView updatedView,
            com.google.android.material.button.MaterialButton refreshBtn, ProgressBar progress,
            com.google.android.material.button.MaterialButton bottomBtn,
            TextView analysisSummary, LinearLayout analysisDetailsContainer,
            TextView analysisNoData, TextView analysisGeneratedAt, View analysisShimmer) {

        Context context = getContext();
        if (context == null) return;
        TokenManager tokenManager = TokenManager.getInstance(context);
        String token = tokenManager.getToken();
        if (token == null) return;

        String url = ApiConfig.BASE_URL + "/api/health/analysis/generate";

        // Show shimmer loading in analysis card
        startShimmerAnimation(analysisShimmer);
        analysisSummary.setVisibility(View.GONE);
        analysisDetailsContainer.setVisibility(View.GONE);
        analysisNoData.setVisibility(View.GONE);
        analysisGeneratedAt.setVisibility(View.GONE);

        StringRequest request = new StringRequest(Request.Method.POST, url,
            response -> {
                try {
                    ApiConfig.logRestCall(url, true, "Full analysis generated");
                    JSONObject jsonResponse = new JSONObject(response);
                    if (jsonResponse.optBoolean("success", false)) {
                        cachedTypeAnalyses = jsonResponse.optJSONObject("analyses");

                        // Re-display current tab
                        displayAnalysisTabContent(currentAnalysisTab, analysisSummary,
                                analysisDetailsContainer, analysisNoData, analysisGeneratedAt, analysisShimmer);

                        updatedView.setText("Generated just now");

                        // Update overall headline if present
                        if (cachedTypeAnalyses != null && cachedTypeAnalyses.has("overall")) {
                            try {
                                JSONObject overall = cachedTypeAnalyses.getJSONObject("overall");
                                String overallText = overall.optString("text", "{}");
                                JSONObject overallParsed = new JSONObject(overallText);
                                String newHeadline = overallParsed.optString("headline", "");
                                if (!newHeadline.isEmpty()) {
                                    headlineView.setText(newHeadline);
                                    if (healthScoreText != null) healthScoreText.setText(newHeadline);
                                }
                            } catch (JSONException ignored) {}
                        }

                        // Re-fetch main health analysis to update cached data (statusLevel, etc.)
                        loadAndDisplayHealthAnalysis();
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
                refreshBtn.setVisibility(View.VISIBLE);
                progress.setVisibility(View.GONE);
                if (bottomBtn != null) {
                    bottomBtn.setEnabled(true);
                    bottomBtn.setText("Refresh Analysis");
                }
            },
            error -> {
                ApiConfig.logRestCall(url, false, error.toString());
                Log.e(TAG, "Error generating analysis", error);
                analysisNoData.setText("Failed to generate. Check connection.");
                analysisNoData.setVisibility(View.VISIBLE);
                stopShimmerAnimation(analysisShimmer);
                refreshBtn.setVisibility(View.VISIBLE);
                progress.setVisibility(View.GONE);
                updatedView.setText("Generation failed");
                if (bottomBtn != null) {
                    bottomBtn.setEnabled(true);
                    bottomBtn.setText("Refresh Analysis");
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

        // 2 minute timeout — LLM calls take time (4 parallel calls)
        request.setRetryPolicy(new DefaultRetryPolicy(120000, 0, 1f));
        Volley.newRequestQueue(context).add(request);
    }

    // Map status level to human-readable text
    private String getStatusText(String level) {
        switch (level) {
            case "EXCELLENT": return "● Excellent";
            case "NORMAL": return "● Stable";
            case "NEEDS_ATTENTION": return "● Attention";
            case "BAD": return "● Action needed";
            case "CRITICAL": return "● Critical";
            default: return "● Analyzed";
        }
    }

    // Pill-friendly text (no bullet — the colored pill bg conveys the state).
    private String getStatusPillText(String level) {
        switch (level) {
            case "EXCELLENT": return "Excellent";
            case "NORMAL": return "Stable";
            case "NEEDS_ATTENTION": return "Attention";
            case "BAD": return "Action needed";
            case "CRITICAL": return "Critical";
            default: return "Analyzed";
        }
    }

    private Utils.StatusPill.Intent getStatusIntent(String level) {
        if (level == null) return Utils.StatusPill.Intent.NEUTRAL;
        switch (level) {
            case "EXCELLENT": return Utils.StatusPill.Intent.INFO;
            case "NORMAL":    return Utils.StatusPill.Intent.SUCCESS;
            case "NEEDS_ATTENTION":
            case "BAD":       return Utils.StatusPill.Intent.WARNING;
            case "CRITICAL":  return Utils.StatusPill.Intent.DANGER;
            default:          return Utils.StatusPill.Intent.NEUTRAL;
        }
    }

    // Helper: get color for health status level
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

    // Helper: BMI category
    private String getBmiCategory(double bmi) {
        if (bmi < 18.5) return "Underweight";
        if (bmi < 25) return "Normal";
        if (bmi < 30) return "Overweight";
        return "Obese";
    }

    // Helper: AQI quality label
    private String getAqiQualityLabel(int aqi) {
        if (aqi <= 50) return "Good";
        if (aqi <= 100) return "Moderate";
        if (aqi <= 150) return "Unhealthy";
        return "Very Unhealthy";
    }

    // Helper: AQI color
    private int getAqiColor(int aqi) {
        if (aqi <= 50) return Color.parseColor("#4CAF50");
        if (aqi <= 100) return Color.parseColor("#FFC107");
        if (aqi <= 150) return Color.parseColor("#FF9800");
        return Color.parseColor("#F44336");
    }

    // ========== AIR QUALITY CARD (reuses cached AQI from aqi_prefs) ==========

    /** Pill intent for an AQI value (SUCCESS good → DANGER unhealthy+). */
    private Utils.StatusPill.Intent aqiIntent(int aqi) {
        if (aqi <= 50) return Utils.StatusPill.Intent.SUCCESS;
        if (aqi <= 100) return Utils.StatusPill.Intent.WARNING;
        return Utils.StatusPill.Intent.DANGER;
    }

    /** Short "X ago" for an epoch-millis timestamp (AQI reading time). */
    private String relativeFromMillis(long whenMs) {
        if (whenMs <= 0) return "";
        long mins = (System.currentTimeMillis() - whenMs) / 60000L;
        if (mins < 1) return "just now";
        if (mins < 60) return mins + " min ago";
        long hours = mins / 60;
        if (hours < 24) return hours + " hour" + (hours > 1 ? "s" : "") + " ago";
        long days = hours / 24;
        return days + " day" + (days > 1 ? "s" : "") + " ago";
    }

    /**
     * Populates the Air Quality ServiceCard from the same cached source the daily
     * digest already uses (aqi_prefs: cached_aqi / cached_city / last_update_time).
     * Informational → chevron always NORMAL. Called on load and whenever AQI refreshes.
     */
    private void updateAqiCard() {
        if (aqiCard == null) return;
        Context ctx = getContext();
        if (ctx == null) return;
        SharedPreferences prefs = ctx.getSharedPreferences("aqi_prefs", Context.MODE_PRIVATE);
        int aqi = prefs.getInt("cached_aqi", -1);
        String city = prefs.getString("cached_city", "");
        long updated = prefs.getLong("last_update_time", 0);

        aqiCard.setChevronStatus(Utils.ServiceCardView.ChevronStatus.NORMAL);
        if (aqi < 0) {
            aqiCard.setSubtitle("Waiting for location…");
            aqiCard.hidePill();
            aqiCard.hideDate();
            return;
        }
        aqiCard.setSubtitle("AQI " + aqi + (city.isEmpty() ? "" : " · " + city));
        aqiCard.setPill(aqiIntent(aqi), getAqiQualityLabel(aqi));
        if (updated > 0) {
            aqiCard.setDate(relativeFromMillis(updated));
        } else {
            aqiCard.hideDate();
        }
    }

    // ========== DIETARY INSIGHTS CARD (GET /api/home/dietary-insights) ==========

    /**
     * Fills the Dietary Insights ServiceCard with "Eat more: … · Limit: …" from
     * foodsToEat / foodsToAvoid, meta from lastUpdated, chevron ATTENTION when stale.
     * Mirrors the existing Volley + Bearer-token pattern used across this fragment.
     */
    private void fetchDietaryInsights() {
        if (dietaryCard == null) return;
        Context context = getContext();
        if (context == null) return;
        TokenManager tokenManager = TokenManager.getInstance(context);
        final String token = tokenManager != null ? tokenManager.getToken() : null;
        if (token == null) return;

        final String url = ApiConfig.BASE_URL + "/api/home/dietary-insights";
        StringRequest request = new StringRequest(Request.Method.GET, url,
                response -> {
                    if (!isAdded() || getActivity() == null) return;
                    requireActivity().runOnUiThread(() -> {
                        try {
                            JSONObject json = new JSONObject(response);
                            String eat = joinFoods(json.optJSONArray("foodsToEat"), 3);
                            String avoid = joinFoods(json.optJSONArray("foodsToAvoid"), 3);
                            boolean stale = json.optBoolean("stale", false);
                            String lastUpdated = json.optString("lastUpdated", "");

                            StringBuilder sub = new StringBuilder();
                            if (!eat.isEmpty()) sub.append("Eat more: ").append(eat);
                            if (!avoid.isEmpty()) {
                                if (sub.length() > 0) sub.append("  ·  ");
                                sub.append("Limit: ").append(avoid);
                            }
                            if (sub.length() == 0) sub.append("What foods to eat and avoid");
                            dietaryCard.setSubtitle(sub.toString());

                            if (lastUpdated != null && !lastUpdated.isEmpty()) {
                                dietaryCard.setDate(formatTimeAgo(lastUpdated));
                            } else {
                                dietaryCard.hideDate();
                            }
                            dietaryCard.setChevronStatus(stale
                                    ? Utils.ServiceCardView.ChevronStatus.ATTENTION
                                    : Utils.ServiceCardView.ChevronStatus.NORMAL);
                        } catch (JSONException e) {
                            Log.e(TAG, "Dietary insights parse error", e);
                        }
                    });
                },
                error -> Log.w(TAG, "Could not load dietary insights: " + error)) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> h = new HashMap<>();
                h.put("Authorization", "Bearer " + token);
                return h;
            }
        };
        request.setRetryPolicy(new DefaultRetryPolicy(15000, 0, 1.0f));
        Volley.newRequestQueue(context).add(request);
    }

    /** Join up to {@code max} strings from a JSON array into a comma list. */
    private String joinFoods(org.json.JSONArray arr, int max) {
        if (arr == null) return "";
        StringBuilder sb = new StringBuilder();
        int n = Math.min(arr.length(), max);
        for (int i = 0; i < n; i++) {
            String s = arr.optString(i, "").trim();
            if (s.isEmpty()) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(s);
        }
        return sb.toString();
    }

    // ========== NEW METHODS: USAGE STATUS ==========

    private void loadAndDisplayUsageStatus() {
        if (usageStatusTitle == null) return;
        boolean isPro = proStatusManager.isProUser();

        if (isPro) {
            displayProStatus();
        } else {
            displayFreeStatus();
        }

        // Wellness Chat — show tier-based message limit
        if (chatSecondaryInfo != null) {
            Skeleton.hide(chatSecondaryInfo);
            String tier = proStatusManager.getUserTier();
            int limit;
            switch (tier != null ? tier : "") {
                case "ultra": limit = 100; break;
                case "pro":
                case "family":
                case "family_member": limit = 50; break;
                case "plus": limit = 25; break;
                default: limit = 5;
            }
            chatSecondaryInfo.setText(limit + " messages / session");
            chatSecondaryInfo.setVisibility(View.VISIBLE);
        }

        // Fill the context line with a universal countdown (pro: expiry; free: reset).
        // Uses cached data immediately, then refreshes from /api/user/usage.
        renderUsageCountdown(0L);
        fetchUsageCountdown();
    }

    /**
     * Render the context line with a label:value pair — "EXPIRES" / "RESETS"
     * in brand teal, the day count in grey. Matches the home page's general
     * label:value style (brand-tinted labels, muted values).
     *
     * @param periodEndMs epoch ms for free-tier monthly reset (0 if unknown)
     */
    private void renderUsageCountdown(long periodEndMs) {
        if (usageContext == null) return;
        Skeleton.hide(usageContext);
        boolean isPro = proStatusManager.isProUser();
        String tier = proStatusManager.getUserTier();

        String label, value;
        if (isPro && "family_member".equals(tier)) {
            String grantedBy = proStatusManager.getProGrantedBy();
            label = "GRANTED BY";
            value = grantedBy != null && !grantedBy.isEmpty() ? grantedBy : "family owner";
        } else if (isPro) {
            long expiry = proStatusManager.getExpiryDate();
            if (expiry <= 0) {
                label = "PLAN";
                value = "Active";
            } else {
                long days = Math.max(0, (expiry - System.currentTimeMillis()) / (1000L * 60 * 60 * 24));
                label = "EXPIRES";
                value = days <= 0 ? "today" : (days == 1 ? "tomorrow" : "in " + days + " days");
            }
        } else {
            label = "RESETS";
            if (periodEndMs > 0) {
                long days = Math.max(0, (periodEndMs - System.currentTimeMillis()) / (1000L * 60 * 60 * 24));
                value = days <= 0 ? "today" : (days == 1 ? "tomorrow" : "in " + days + " days");
            } else {
                // First of next month, UTC — matches backend billing period start.
                java.util.Calendar cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
                cal.add(java.util.Calendar.MONTH, 1);
                cal.set(java.util.Calendar.DAY_OF_MONTH, 1);
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
                cal.set(java.util.Calendar.MINUTE, 0);
                cal.set(java.util.Calendar.SECOND, 0);
                cal.set(java.util.Calendar.MILLISECOND, 0);
                long days = Math.max(0, (cal.getTimeInMillis() - System.currentTimeMillis()) / (1000L * 60 * 60 * 24));
                value = days <= 0 ? "today" : (days == 1 ? "tomorrow" : "in " + days + " days");
            }
        }

        usageContext.setText(styledLabelValue(label, value));
    }

    /**
     * Build a "LABEL  value" Spannable: label in teal bold micro-caps, value in muted grey.
     * Use for label:value pairs on the home page so styling stays consistent.
     */
    private CharSequence styledLabelValue(String label, String value) {
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder();
        sb.append(label);
        sb.setSpan(new android.text.style.ForegroundColorSpan(android.graphics.Color.parseColor("#008b8b")),
                0, label.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                0, label.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new android.text.style.RelativeSizeSpan(0.85f),
                0, label.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        int sep = sb.length();
        sb.append("  ").append(value);
        sb.setSpan(new android.text.style.ForegroundColorSpan(android.graphics.Color.parseColor("#CCCCCC")),
                sep, sb.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sb;
    }

    /** Pull periodEnd from /api/user/usage and refresh the countdown. */
    private void fetchUsageCountdown() {
        Context context = getContext();
        if (context == null || usageContext == null) return;

        TokenManager tokenManager = TokenManager.getInstance(context);
        String token = tokenManager.getToken();
        if (token == null) return;

        String url = ApiConfig.BASE_URL + "/api/user/usage";
        StringRequest req = new StringRequest(Request.Method.GET, url,
                response -> {
                    try {
                        JSONObject body = new JSONObject(response);
                        long periodEnd = body.optLong("periodEnd", 0);
                        renderUsageCountdown(periodEnd);
                    } catch (JSONException ignored) {}
                },
                error -> { /* keep the locally-computed countdown on failure */ }
        ) {
            @Override
            public java.util.Map<String, String> getHeaders() throws AuthFailureError {
                java.util.Map<String, String> h = new java.util.HashMap<>();
                h.put("Authorization", "Bearer " + token);
                return h;
            }
        };
        Volley.newRequestQueue(context).add(req);
    }

    private void displayFreeStatus() {
        Skeleton.hide(usageContext);
        // Plan tier shown by the global header plan_pill; the inner Active/Free pill was
        // removed for layout consistency. usageContext carries the only sub-line now.
        if (usageContext != null) usageContext.setText("Limited features");
    }

    private void displayProStatus() {
        Skeleton.hide(usageContext);
        String tier = proStatusManager.getUserTier();
        if (usageContext != null) {
            switch (tier) {
                case "family":
                    int memberCount = proStatusManager.getFamilyMemberCount();
                    int maxMembers = proStatusManager.getMaxFamilyMembers();
                    usageContext.setText(memberCount + "/" + maxMembers + " members");
                    break;
                case "family_member":
                    String grantedBy = proStatusManager.getProGrantedBy();
                    usageContext.setText("Granted by " + (grantedBy != null ? grantedBy : "owner"));
                    break;
                default:
                    int used = proStatusManager.getReportsUsed();
                    int total = proStatusManager.getTotalReports();
                    if (total > 0) {
                        usageContext.setText(used + "/" + total + " reports used");
                    } else {
                        usageContext.setText("All features unlocked");
                    }
                    break;
            }
        }
    }

    /** Load check-in home card data from /api/checkin/home-card */
    private void loadCheckInCard() {
        if (checkInHomeCard == null) return;

        Context context = getContext();
        if (context == null) return;

        TokenManager tokenManager = TokenManager.getInstance(context);
        String token = tokenManager.getToken();
        if (token == null) return;

        String url = ApiConfig.BASE_URL + "/api/checkin/home-card";

        StringRequest request = new StringRequest(Request.Method.GET, url,
                response -> {
                    if (!isAdded()) return;
                    Skeleton.hide(checkInStatusText);
                    try {
                        org.json.JSONObject json = new org.json.JSONObject(response);
                        boolean canAccess = json.optBoolean("canAccess", false);
                        String tier = json.optString("tier", "free");
                        int pendingCount = json.optInt("pendingCount", 0);
                        int inProgressCount = json.optInt("inProgressCount", 0);
                        int pendingOnlyCount = json.optInt("pendingOnlyCount",
                                Math.max(0, pendingCount - inProgressCount));
                        boolean isDue = json.optBoolean("isDue", false);

                        checkInHomeCard.setVisibility(View.VISIBLE);

                        // Schedule local check-in reminders (tier cadence) — no Firebase needed.
                        // Android 13+ (API 33) requires POST_NOTIFICATIONS at runtime, else these
                        // silently never show — ask once, and only schedule when it's allowed.
                        Utils.NotificationPermissionHelper.requestIfNeeded(HomeFragment.this);
                        if (Utils.NotificationPermissionHelper.hasPermission(requireContext())) {
                            Utils.CheckInNotificationHelper.scheduleForTier(requireContext(), tier);
                        }

                        // Reset pill; set below per state.
                        if (checkInStatusPill != null) {
                            checkInStatusPill.setVisibility(View.GONE);
                        }

                        if (!canAccess) {
                            if (checkInStatusText != null) {
                                checkInStatusText.setText("Scheduled automatically");
                            }
                            if (checkInStartButton != null) {
                                checkInStartButton.setText("View \u203a");
                            }
                        } else if (inProgressCount > 0) {
                            setCheckInPill(Utils.StatusPill.Intent.WARNING, "In Progress");
                            if (checkInStatusText != null) {
                                checkInStatusText.setText(inProgressCount > 1
                                        ? inProgressCount + " check-ins started"
                                        : "Check-in started");
                            }
                            if (checkInStartButton != null) {
                                checkInStartButton.setText("Continue \u203a");
                            }
                        } else if (pendingOnlyCount > 0) {
                            setCheckInPill(Utils.StatusPill.Intent.NEUTRAL, "Pending");
                            if (checkInStatusText != null) {
                                checkInStatusText.setText(pendingOnlyCount + " check-in"
                                        + (pendingOnlyCount > 1 ? "s" : "") + " ready");
                            }
                            if (checkInStartButton != null) {
                                checkInStartButton.setText("Start \u203a");
                            }
                        } else if (isDue) {
                            setCheckInPill(Utils.StatusPill.Intent.WARNING, "Due now");
                            if (checkInStatusText != null) {
                                checkInStatusText.setText("Your check-in is ready");
                            }
                            if (checkInStartButton != null) {
                                checkInStartButton.setText("Start \u203a");
                            }
                        } else {
                            setCheckInPill(Utils.StatusPill.Intent.SUCCESS, "All caught up");
                            if (checkInStartButton != null) {
                                checkInStartButton.setText("History \u203a");
                            }
                        }

                        // Chevron: ATTENTION when a check-in is due/pending/in-progress, else NORMAL.
                        if (checkInHomeCard != null) {
                            boolean attention = canAccess
                                    && (inProgressCount > 0 || pendingOnlyCount > 0 || isDue);
                            checkInHomeCard.setChevronStatus(attention
                                    ? Utils.ServiceCardView.ChevronStatus.ATTENTION
                                    : Utils.ServiceCardView.ChevronStatus.NORMAL);
                        }

                        // Meta line: real "last check-in" date (pill already carries the status).
                        if (checkInStatusText != null) {
                            String lastAt = json.optString("lastCompletedAt", "");
                            if (lastAt != null && !lastAt.isEmpty() && !lastAt.equals("null")) {
                                checkInStatusText.setText(formatTimeAgo(lastAt));
                            } else {
                                checkInStatusText.setText("No check-ins yet");
                            }
                            checkInStatusText.setVisibility(View.VISIBLE);
                        }
                    } catch (org.json.JSONException e) {
                        Log.e(TAG, "Error parsing checkin home card", e);
                    }
                },
                error -> {
                    if (!isAdded()) return;
                    if (checkInStatusText != null) {
                        Utils.ErrorHandler.ParsedError parsed = Utils.ErrorHandler.parse(error);
                        String msg = parsed.type == Utils.ErrorHandler.ErrorType.NETWORK_ERROR
                                ? "No connection" : "Unavailable";
                        Skeleton.error(checkInStatusText, msg);
                    }
                    Log.w(TAG, "Could not load check-in card: " + error.getMessage());
                }) {
            @Override
            public java.util.Map<String, String> getHeaders() throws com.android.volley.AuthFailureError {
                java.util.Map<String, String> headers = new java.util.HashMap<>();
                headers.put("Authorization", "Bearer " + token);
                return headers;
            }
        };

        request.setRetryPolicy(new com.android.volley.DefaultRetryPolicy(
                10000, 1, com.android.volley.DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        com.android.volley.toolbox.Volley.newRequestQueue(context).add(request);
    }

    private void setCheckInPill(Utils.StatusPill.Intent intent, String text) {
        if (checkInStatusPill == null) return;
        // Clear any inline tint left over from older code paths so the drawable
        // shows its own color (StatusPill.apply sets the background drawable).
        checkInStatusPill.setBackgroundTintList(null);
        Utils.StatusPill.apply(checkInStatusPill, intent, text);
    }

    // Get chat messages per session limit based on tier (from TIERS config)
    private int getChatLimitForTier(String tier) {
        switch (tier) {
            case "ultra": return 100;
            case "pro":
            case "family":
            case "family_member": return 50;
            case "plus": return 25;
            default: return 5; // free
        }
    }

    // Check if the health analysis was used within the current month
    private boolean isAnalysisUsedThisMonth() {
        if (lastHealthAnalysisJson == null) return false;
        String lastUpdated = lastHealthAnalysisJson.optString("lastUpdated", null);
        if (lastUpdated == null || lastUpdated.isEmpty()) return false;
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US);
            java.util.Date date = sdf.parse(lastUpdated);
            if (date == null) return false;
            java.util.Calendar now = java.util.Calendar.getInstance();
            java.util.Calendar analysisDate = java.util.Calendar.getInstance();
            analysisDate.setTime(date);
            return now.get(java.util.Calendar.MONTH) == analysisDate.get(java.util.Calendar.MONTH)
                && now.get(java.util.Calendar.YEAR) == analysisDate.get(java.util.Calendar.YEAR);
        } catch (Exception e) {
            return false;
        }
    }

    private void showUsageDialog() {
        Dialog dialog = new Dialog(requireContext(), R.style.DialogTheme);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_usage_status);

        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
        layoutParams.copyFrom(dialog.getWindow().getAttributes());
        layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
        layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
        dialog.getWindow().setAttributes(layoutParams);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        // ── Get all views ──
        TextView dialogTitle = dialog.findViewById(R.id.dialog_usage_title);
        // [PLAN-PILL-REVIEW] disabled (backend-driven, keep after review): TextView planName = dialog.findViewById(R.id.dialog_plan_name);
        TextView planDescription = dialog.findViewById(R.id.dialog_plan_description);

        // Usage summary columns
        TextView analysisCount = dialog.findViewById(R.id.dialog_usage_analysis_count);
        TextView analysisPeriod = dialog.findViewById(R.id.dialog_usage_analysis_period);
        TextView reportsCount = dialog.findViewById(R.id.dialog_usage_reports_count);
        TextView reportsPeriod = dialog.findViewById(R.id.dialog_usage_reports_period);
        TextView chatCount = dialog.findViewById(R.id.dialog_usage_chat_count);
        TextView chatPeriod = dialog.findViewById(R.id.dialog_usage_chat_period);

        // New summary columns
        TextView nutriCount = dialog.findViewById(R.id.dialog_usage_nutri_count);
        TextView reportAnalysisCount = dialog.findViewById(R.id.dialog_usage_report_analysis_count);

        // Feature comparison section
        LinearLayout featureSection = dialog.findViewById(R.id.dialog_feature_comparison_section);
        TextView featureSectionTitle = dialog.findViewById(R.id.dialog_feature_section_title);
        View featureHeaderRow = dialog.findViewById(R.id.dialog_feature_header_row);
        TextView col1Header = dialog.findViewById(R.id.dialog_feature_col1_header);
        TextView col2Header = dialog.findViewById(R.id.dialog_feature_col2_header);

        // Feature comparison rows
        TextView featChatFree = dialog.findViewById(R.id.dialog_feat_chat_free);
        TextView featChatPro = dialog.findViewById(R.id.dialog_feat_chat_pro);
        TextView featAnalysisFree = dialog.findViewById(R.id.dialog_feat_analysis_free);
        TextView featAnalysisPro = dialog.findViewById(R.id.dialog_feat_analysis_pro);
        TextView featReportsFree = dialog.findViewById(R.id.dialog_feat_reports_free);
        TextView featReportsPro = dialog.findViewById(R.id.dialog_feat_reports_pro);
        TextView featNutriFree = dialog.findViewById(R.id.dialog_feat_nutri_free);
        TextView featNutriPro = dialog.findViewById(R.id.dialog_feat_nutri_pro);
        TextView featReportAnalysisFree = dialog.findViewById(R.id.dialog_feat_report_analysis_free);
        TextView featReportAnalysisPro = dialog.findViewById(R.id.dialog_feat_report_analysis_pro);
        TextView featModelFree = dialog.findViewById(R.id.dialog_feat_model_free);
        TextView featModelPro = dialog.findViewById(R.id.dialog_feat_model_pro);
        TextView featSharingFree = dialog.findViewById(R.id.dialog_feat_sharing_free);
        TextView featSharingPro = dialog.findViewById(R.id.dialog_feat_sharing_pro);
        TextView featDependentsFree = dialog.findViewById(R.id.dialog_feat_dependents_free);
        TextView featDependentsPro = dialog.findViewById(R.id.dialog_feat_dependents_pro);

        // Buttons
        com.google.android.material.button.MaterialButton cancelButton = dialog.findViewById(R.id.dialog_usage_cancel_button);
        com.google.android.material.button.MaterialButton upgradeButton = dialog.findViewById(R.id.dialog_usage_upgrade_button);

        boolean isPro = proStatusManager.isProUser();
        String tier = proStatusManager.getUserTier();
        int chatLimit = getChatLimitForTier(tier);

        if (isPro) {
            // ── Pro/Ultra/Family user view ──
            dialogTitle.setText("Your Account");
            // [PLAN-PILL-REVIEW] disabled (backend-driven, keep after review): planName.setText(Utils.PlanBadge.fullLabelFor(tier));
            // planName.setTextColor(Utils.PlanBadge.colorFor(requireContext(), tier));
            switch (tier) {
                case "family":
                    int memberCount = proStatusManager.getFamilyMemberCount();
                    int maxMembers = proStatusManager.getMaxFamilyMembers();
                    planDescription.setText("Family plan · " + memberCount + "/" + maxMembers + " members");
                    break;
                case "family_member":
                    planDescription.setText("Pro access granted by " + proStatusManager.getProGrantedBy());
                    break;
                default:
                    String expiry = proStatusManager.getFormattedExpiryDate();
                    planDescription.setText(expiry != null ? "Active until " + expiry : "All features unlocked");
                    break;
            }

            // Usage summary: show actual limits for this tier
            // tiers.js: ultra=null(unlimited), family/family_member=10, plus=5, pro=10
            if (tier.equals("ultra")) {
                analysisCount.setText("∞");
                analysisPeriod.setText("unlimited");
            } else if (tier.equals("plus")) {
                analysisCount.setText("5");
                analysisPeriod.setText("per month");
            } else {
                // pro
                analysisCount.setText("10");
                analysisPeriod.setText("per month");
            }
            analysisCount.setTextColor(Color.parseColor("#4CAF50"));
            analysisPeriod.setTextColor(Color.parseColor("#4CAF50"));

            if (tier.equals("ultra")) {
                reportsCount.setText("∞");
                reportsPeriod.setText("unlimited");
            } else {
                int usedReports = proStatusManager.getReportsUsed();
                int totalReports = proStatusManager.getTotalReports();
                int defaultReports = tier.equals("plus") ? 5 : 10;
                reportsCount.setText(usedReports + "/" + (totalReports > 0 ? totalReports : defaultReports));
                reportsPeriod.setText("per month");
            }
            reportsCount.setTextColor(Color.parseColor("#4CAF50"));
            reportsPeriod.setTextColor(Color.parseColor("#4CAF50"));

            chatCount.setText(String.valueOf(chatLimit));
            chatCount.setTextColor(Color.parseColor("#4CAF50"));
            chatPeriod.setText("per session");
            chatPeriod.setTextColor(Color.parseColor("#4CAF50"));

            // NutriCheck & Report Analysis summary
            if (nutriCount != null) {
                nutriCount.setText(tier.equals("plus") ? "15/month" : "Unlimited");
                nutriCount.setTextColor(Color.parseColor("#4CAF50"));
            }
            if (reportAnalysisCount != null) {
                reportAnalysisCount.setText("Available");
                reportAnalysisCount.setTextColor(Color.parseColor("#4CAF50"));
            }

            // Feature section: show "Your Features" as a single-column list
            featureSectionTitle.setText("Your Features");
            featureHeaderRow.setVisibility(View.GONE);

            // Show tier-specific values in col1 (feature name stays), hide col2
            int green = Color.parseColor("#4CAF50");
            featChatFree.setText(chatLimit + "/session");
            featChatFree.setTextColor(green);
            featChatPro.setVisibility(View.GONE);

            // tiers.js: ultra=null(unlimited), family/family_member=10, plus=5, pro=10
            String analysisText;
            if (tier.equals("ultra")) analysisText = "Unlimited";
            else if (tier.equals("plus")) analysisText = "5/month";
            else analysisText = "10/month"; // pro, family, family_member
            featAnalysisFree.setText(analysisText);
            featAnalysisFree.setTextColor(green);
            featAnalysisPro.setVisibility(View.GONE);

            String reportsText;
            if (tier.equals("ultra")) reportsText = "Unlimited";
            else if (tier.equals("plus")) reportsText = "5/month";
            else reportsText = "10/month"; // pro, family
            featReportsFree.setText(reportsText);
            featReportsFree.setTextColor(green);
            featReportsPro.setVisibility(View.GONE);

            featNutriFree.setText(tier.equals("plus") ? "15/month" : "Unlimited");
            featNutriFree.setTextColor(green);
            featNutriPro.setVisibility(View.GONE);

            featReportAnalysisFree.setText("Available");
            featReportAnalysisFree.setTextColor(green);
            featReportAnalysisPro.setVisibility(View.GONE);

            // Plus can only use standard models; Pro/Ultra/Family get all models (including GPT-5.3, Claude 4.5)
            featModelFree.setText(tier.equals("plus") ? "Standard models" : "All models");
            featModelFree.setTextColor(green);
            featModelPro.setVisibility(View.GONE);

            featSharingFree.setText(tier.equals("plus") ? "--" : "Available");
            featSharingFree.setTextColor(tier.equals("plus") ? Color.parseColor("#FF9800") : green);
            featSharingPro.setVisibility(View.GONE);

            int maxDeps;
            if (tier.equals("ultra")) maxDeps = 5;
            else if (tier.equals("pro") || tier.equals("family")) maxDeps = 2;
            else if (tier.equals("plus")) maxDeps = 1;
            else maxDeps = 0;
            featDependentsFree.setText(String.valueOf(maxDeps));
            featDependentsFree.setTextColor(green);
            featDependentsPro.setVisibility(View.GONE);

            upgradeButton.setText("Manage Account");
        } else {
            // ── Free user view ──
            dialogTitle.setText("Free vs Pro");
            // [PLAN-PILL-REVIEW] disabled (backend-driven, keep after review): planName.setText(Utils.PlanBadge.fullLabelFor("free"));
            // planName.setTextColor(Utils.PlanBadge.colorFor(requireContext(), "free"));
            planDescription.setText("Limited access to features");

            // Usage summary: actual usage counts
            boolean analysisUsed = isAnalysisUsedThisMonth();
            analysisCount.setText(analysisUsed ? "Used" : "1 left");
            analysisCount.setTextColor(Color.parseColor(analysisUsed ? "#FF9800" : "#4CAF50"));
            analysisPeriod.setText("monthly");

            int reports = proStatusManager.getReportsUsed();
            reportsCount.setText(Math.min(reports, 2) + "/2");
            reportsCount.setTextColor(Color.parseColor(reports >= 2 ? "#FF9800" : "#FFFFFF"));
            reportsPeriod.setText("monthly");

            chatCount.setText("5");
            chatCount.setTextColor(Color.parseColor("#FFFFFF"));
            chatPeriod.setText("per session");

            // NutriCheck: free tier gets 5/month (usageTracker nutricheck.free=5)
            if (nutriCount != null) {
                nutriCount.setText("5/month");
                nutriCount.setTextColor(Color.parseColor("#FF9800"));
            }
            if (reportAnalysisCount != null) {
                reportAnalysisCount.setText("Locked");
                reportAnalysisCount.setTextColor(Color.parseColor("#FF9800"));
            }

            // Feature comparison: Free vs Pro columns
            // Set values explicitly to match backend tiers.js (don't rely on XML defaults)
            featureSectionTitle.setText("Free vs Pro Comparison");
            featureHeaderRow.setVisibility(View.VISIBLE);
            col1Header.setText("Free");
            col2Header.setText("Pro");

            featChatFree.setText("5");
            featChatPro.setText("50");
            featChatPro.setVisibility(View.VISIBLE);
            featAnalysisFree.setText("1/month");
            featAnalysisPro.setText("10/month");
            featAnalysisPro.setVisibility(View.VISIBLE);
            featReportsFree.setText("2/month");
            featReportsPro.setText("10/month");
            featReportsPro.setVisibility(View.VISIBLE);
            featNutriFree.setText("5/month");
            featNutriPro.setText("Unlimited");
            featNutriPro.setVisibility(View.VISIBLE);
            featReportAnalysisFree.setText("--");
            featReportAnalysisPro.setText("Available");
            featReportAnalysisPro.setVisibility(View.VISIBLE);
            featModelFree.setText("5 Models");
            featModelPro.setText("All");
            featModelPro.setVisibility(View.VISIBLE);
            featSharingFree.setText("--");
            featSharingPro.setText("Yes");
            featSharingPro.setVisibility(View.VISIBLE);
            featDependentsFree.setText("0");
            featDependentsPro.setText("2");
            featDependentsPro.setVisibility(View.VISIBLE);

            upgradeButton.setText("Upgrade to Pro");
        }

        // Cancel button
        cancelButton.setOnClickListener(v -> dialog.dismiss());

        // Upgrade button
        upgradeButton.setOnClickListener(v -> {
            dialog.dismiss();
            if (!isPro) {
                new Utils.ProUpgradeDialog(requireActivity()).show(newIsPro -> {
                    if (newIsPro) Utils.ProStatusManager.syncProStatusOnLogin(requireContext());
                });
            }
        });

        dialog.show();
    }

    // ========== END NEW METHODS ==========

    // Drive the semantic status pill (see TOOLS CARD STANDARD) + bind explainer dialog.
    private void updateStatusChipColor(String statusLevel, String reason) {
        currentStatusLevel = statusLevel != null ? statusLevel : "";
        currentStatusReason = reason != null ? reason : "";
        if (healthStatusPill == null) return;

        String level = currentStatusLevel;
        if (level.isEmpty() || getStatusIconRes(level) == 0) {
            healthStatusPill.setVisibility(View.GONE);
            return;
        }
        Utils.StatusPill.apply(healthStatusPill, getStatusIntent(level), getStatusPillText(level));
        healthStatusPill.setOnClickListener(v -> showHealthStatusInfoDialog());
    }

    // Map status level → semantic icon
    private int getStatusIconRes(String level) {
        if (level == null) return 0;
        switch (level) {
            case "EXCELLENT":
            case "NORMAL":
                return R.drawable.ic_check_green;
            case "NEEDS_ATTENTION":
            case "BAD":
                return R.drawable.ic_warning_yellow;
            case "CRITICAL":
                return R.drawable.ic_exclamation_red;
            default:
                return 0;
        }
    }

    /** Dialog explaining the current health status icon (stable / warning / critical). */
    private void showHealthStatusInfoDialog() {
        String level = currentStatusLevel != null ? currentStatusLevel : "";
        String label = getStatusPillText(level);
        int iconRes = getStatusIconRes(level);
        if (iconRes == 0) iconRes = R.drawable.ic_info_outline;

        String body;
        String[] bullets;
        switch (level) {
            case "EXCELLENT":
                body = "Your latest analysis indicates excellent overall health based on your profile, vitals and recent entries.";
                bullets = new String[] {
                        "Vitals and metrics are within healthy ranges",
                        "No high-priority concerns flagged",
                        "Keep your data fresh to maintain accuracy"
                };
                break;
            case "NORMAL":
                body = "Things look stable. Your recent measurements and reports are within expected ranges for your profile.";
                bullets = new String[] {
                        "No urgent issues flagged",
                        "Continue logging routinely for better trends",
                        "Tap the card to view the full breakdown"
                };
                break;
            case "NEEDS_ATTENTION":
            case "BAD":
                body = currentStatusReason != null && !currentStatusReason.isEmpty()
                        ? currentStatusReason
                        : "Some readings or reports are outside the typical range and could use a closer look.";
                bullets = new String[] {
                        "Review the flagged metrics in the analysis",
                        "Consider logging missing context (symptoms, meds)",
                        "Discuss persistent issues with your doctor"
                };
                break;
            case "CRITICAL":
                body = currentStatusReason != null && !currentStatusReason.isEmpty()
                        ? currentStatusReason
                        : "Something in your latest analysis is flagged as critical and warrants immediate attention.";
                bullets = new String[] {
                        "Review the critical finding in the full analysis",
                        "Reach out to a doctor without delay",
                        "Keep your medications and history up to date"
                };
                break;
            default:
                body = "Tap your Health Analysis card to view the full breakdown.";
                bullets = new String[] { "Open Health Analysis for details" };
        }

        new Utils.CardInfoDialog.Builder(requireContext())
                .title(label)
                .subtitle("Health Status")
                .icon(iconRes)
                .body(body)
                .bullets(bullets)
                .build()
                .show();
    }

    /**
     * Smart dialog explaining that the data backing this card has changed
     * since the last result was generated. Suggests refreshing and surfacing
     * any obvious gaps (medications, period history for women).
     */
    /**
     * Only ever shown when the underlying feature is stale — i.e. the user added or
     * updated data AFTER the last time this feature ran. So the dialog speaks plainly:
     * the result is outdated, refresh to recompute.
     */
    private void showStaleDataInfoDialog(String featureName) {
        java.util.List<String> bullets = new java.util.ArrayList<>();
        bullets.add("Your information has changed since this " + featureName + " was last generated");
        bullets.add("Tap the card to refresh — recommendations may be inaccurate until you do");

        // Smart hints based on what's missing in the user profile
        try {
            if (userProfile != null) {
                java.util.List<?> meds = userProfile.getMedications();
                if (meds == null || meds.isEmpty()) {
                    bullets.add("You haven't added any medications yet — adding them improves accuracy");
                }
                String gender = userProfile.getGender();
                if (gender != null && gender.toLowerCase(Locale.US).startsWith("f")) {
                    bullets.add("Make sure your period history is logged — cycle context improves accuracy");
                }
            }
        } catch (Throwable ignored) {
            // Defensive — UserProfile shape may evolve; never block the dialog
        }

        bullets.add("Keep your profile, vitals and reports up to date for the best results");

        new Utils.CardInfoDialog.Builder(requireContext())
                .title(featureName + " is outdated")
                .subtitle("Why this icon is here")
                .icon(R.drawable.ic_warning_yellow)
                .body("Your latest data was added or updated after this " + featureName.toLowerCase(Locale.US)
                        + " was generated, so the result you see may not reflect everything you've logged. "
                        + "Refresh to recompute with your current information.")
                .bullets(bullets.toArray(new String[0]))
                .build()
                .show();
    }

    /** Parse an ISO timestamp string to epoch millis. Returns 0 on failure. */
    private long parseIsoToMillis(String iso) {
        if (iso == null || iso.isEmpty()) return 0;
        try {
            String cleaned = iso.replaceAll("\\.[0-9]+", "");
            if (cleaned.endsWith("Z")) cleaned = cleaned.substring(0, cleaned.length() - 1);
            else cleaned = cleaned.replaceAll("[+-]\\d{2}:\\d{2}$", "");
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            Date d = sdf.parse(cleaned);
            return d != null ? d.getTime() : 0;
        } catch (Exception e) { return 0; }
    }

    // Format timestamp as "X hours ago" or "Just now"
    private String formatTimeAgo(String isoTimestamp) {
        try {
            // Strip millis (.000) and timezone (Z / +00:00) then parse as UTC
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

    /**
     * Plan pill — replaces the notification button. Reflects user tier and opens the upgrade dialog.
     */
    private void setupPlanPill() {
        if (planPill == null) return;
        refreshPlanPill();
        planPill.setOnClickListener(v -> {
            v.animate()
                    .scaleX(0.92f).scaleY(0.92f)
                    .setDuration(90)
                    .withEndAction(() -> v.animate()
                            .scaleX(1f).scaleY(1f)
                            .setDuration(140)
                            .setInterpolator(new android.view.animation.OvershootInterpolator(2.5f))
                            .start())
                    .start();
            new Utils.ProUpgradeDialog(requireActivity()).show(newIsPro -> refreshPlanPill());
        });
    }

    private void refreshPlanPill() {
        if (planPill == null || planPillText == null || planPillIcon == null) return;
        boolean isPro = proStatusManager != null && proStatusManager.isProUser();
        if (isPro) {
            String tier = proStatusManager.getUserTier();
            planPillText.setText(tier != null ? tier.toUpperCase(Locale.US) : "PRO");
            planPillText.setTextColor(0xFFFFB300);
            planPillIcon.setColorFilter(0xFFFFB300);
            planPill.setBackgroundResource(R.drawable.bg_pill_plan_pro);
        } else {
            planPillText.setText("FREE");
            planPillText.setTextColor(0xFF008b8b);
            planPillIcon.setColorFilter(0xFF008b8b);
            planPill.setBackgroundResource(R.drawable.bg_pill_plan_free);
        }
    }

    /**
     * Stagger-fade key cards in on first display.
     */
    private void animateCardsEntry(View view) {
        int[] cardIds = new int[] {
                R.id.daily_digest_card,
                R.id.checkin_home_card,
                R.id.dietary_insights_card,
                R.id.nutri_check_card,
                R.id.doctor_connection_card,
                R.id.mental_health_card,
                R.id.start_workout_card,
                R.id.browse_exercises_card
        };
        long delay = 0;
        for (int id : cardIds) {
            View card = view.findViewById(id);
            if (card == null || card.getVisibility() == View.GONE) continue;
            card.setAlpha(0f);
            card.setTranslationY(24f);
            card.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(delay)
                    .setDuration(360)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
            delay += 55;
        }
    }

}


package com.example.richhealth.Activities;

import android.animation.ObjectAnimator;
import android.app.AlertDialog;
import android.os.Handler;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.HorizontalScrollView;
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.example.richhealth.R;
import com.google.android.material.button.MaterialButton;
import android.graphics.drawable.GradientDrawable;
import android.widget.ProgressBar;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import android.content.res.ColorStateList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import Adapters.ChatAdapter;
import Adapters.SavedChatAdapter;
import Api.MedicalDataApiService;
import Database.DatabaseHelper;
import Models.ChatMessage;
import Models.ChatSession;
import Models.HealthCard;
import Models.MedicalData;
import Models.Suggestion;
import Models.UserProfile;
import Utils.ApiConfig;
import Utils.HealthLogParser;
import Utils.DialogUtils;
import Utils.ErrorHandler;
import Utils.ProStatusManager;
import Utils.SimpleProgress;
import Utils.Utilities;

public class AIFragment extends Fragment implements BackPressHandler {

    private static final String TAG = "AIFragment";

    // UI components
    private TextView headerTitle;
    private TextView headerSubtitle;
    private RecyclerView chatRecycler;
    private ChipGroup suggestionChips;
    private EditText messageInput;
    private ImageButton sendButton;
    private ChatAdapter chatAdapter;
    private ImageButton chatHistoryButton;
    private ImageButton newChatHeaderButton;
    private ImageButton keepHistoryButton;
    private TextView savedCountText;

    // Welcome / empty state
    private LinearLayout welcomeContainer;
    // ScrollView wrapper around the welcome/empty state. Must be hidden together with
    // welcomeContainer — otherwise it overlays the chat RecyclerView and blocks touches.
    private View welcomeScroll;
    private ImageView welcomeLogo;
    private ObjectAnimator welcomeLogoAnimator;
    private boolean welcomeTransitioning = false;
    private TextView welcomeGreeting;
    private TextView healthDataContext;
    private LinearLayout hintBox;
    private TextView hintText;
    private ImageView hintIcon;
    private String nudgeAction = "";
    private boolean nudgeHasContent = false;
    private boolean nudgeRevealed = false;
    private Runnable nudgeRevealRunnable;
    private boolean animateNextSuggestions = false;
    private com.google.android.material.card.MaterialCardView expandedSuggestionCard = null;
    private LinearLayout expandedSuggestionExpand = null;
    private ImageView expandedSuggestionChevron = null;
    private static final int SUGGESTION_DISPLAY_COUNT = 3;
    private final java.util.List<AiSuggestion> suggestionBackup = new java.util.ArrayList<>();
    private final java.util.Set<String> knownQuestions = new java.util.HashSet<>();
    private boolean refillInProgress = false;

    /** A starter suggestion plus the AI's reason for offering it. */
    private static class AiSuggestion {
        final String q;
        final String why;
        AiSuggestion(String q, String why) { this.q = q; this.why = why; }
    }
    private LinearLayout suggestionPillsRow1;
    private LinearLayout suggestionPillsRow2;
    private HorizontalScrollView suggestionScroll1;
    private HorizontalScrollView suggestionScroll2;
    private ValueAnimator pillAnimator1;
    private ValueAnimator pillAnimator2;

    // Side panel for chat history
    private Dialog chatHistoryPanel;
    private RecyclerView chatSessionsRecycler;
    private ChatHistoryAdapter chatSessionsAdapter;
    private View emptyStateView;
    private MaterialButton newChatButton;
    private MaterialButton deleteAllChatsButton;
    private ImageButton closePanelButton;
    private EditText chatSearchInput;
    private View historyLoading;
    // Full, unfiltered session list — the search box filters a copy of this.
    private final List<ChatSession> allChatSessions = new ArrayList<>();

    private String currentModel = "auto";

    // Other components
    private DatabaseHelper dbHelper;
    private ProStatusManager proStatusManager;
    private String sessionId = null;

    // The chat the user is currently in, held at process scope so it survives
    // switching bottom-nav tabs (MainActivity recreates AIFragment on each tab).
    // It is naturally cleared when the app process is killed (app closed), so a
    // cold start still begins on a fresh chat. Set when a session is opened or
    // created; cleared on an explicit "New chat" / delete.
    private static ChatSession activeSession = null;
    private static void setActiveSession(ChatSession s) { activeSession = s; }
    private static void clearActiveSession() { activeSession = null; }

    // Typed-but-unsent input text, held at process scope so it survives switching
    // bottom-nav tabs (MainActivity recreates AIFragment on each tab, so instance
    // fields are lost — same reason activeSession is static). Kept in sync by a
    // TextWatcher, cleared when a message is sent, and naturally gone on app process
    // death (a cold start still opens an empty input box).
    private static String pendingInputText = "";
    private UserProfile userProfile;
    private boolean isHistoryKeepingEnabled = true;
    private SimpleProgress initialProgress;
    private Dialog savedChatsPanel;
    private SavedChatAdapter savedChatAdapter;
    private int thinkingPosition = -1;
    private ChatMessage thinkingMsg = null;
    private ObjectAnimator iconAnimator;
    private Handler thinkingCycleHandler = null;
    // dot animation state — drives both the . .. ... .... ..... cycle AND message advancement
    private int thinkingDotStep = 0;
    private int thinkingMsgIdx  = 0;
    private int thinkingTick    = 0;
    private List<String> thinkingBaseMessages = null;
    private static final String[] DOT_STATES = {".", "..", "...", "....", "....."};
    private static final int DOT_INTERVAL_MS = 400;  // each dot step
    private static final int DOTS_PER_MSG    = 5;    // dot steps before advancing to next message
    private static final int TICKS_PER_MSG   = 25;   // ~10s per message (25 × 400ms) before escalating text

    // Dependent selection
    private View inputProfileChip;
    private TextView inputProfileText;
    private ImageView inputProfileIcon;
    private ImageView inputProfileChevron;
    private LinearLayout suggestionList;
    private String selectedDependentId = null;   // null = self
    private String selectedDependentName = "Myself";
    private List<JSONObject> dependentsList = new ArrayList<>();

    // Model selection — pill above the input (was a header button; now a clickable LinearLayout)
    private View modelDropdownButton;
    private TextView modelPillText;
    private ImageView modelPillIcon;
    private View maxModePill;
    private TextView maxModePillText;
    private String selectedModel = "Auto";

    // Max mode = "council" of the three flagship models. Same wire format as any other
    // model id so it flows through createSessionThenSend / sendMessageToBackend with no
    // client-side special-casing. Backend is expected to fan out to gpt5.3 + claude4.5 + gemini.
    private static final String MAX_MODEL_ID = "max";
    private static final String MAX_MODEL_DISPLAY = "Max";

    // Fork-chat staging — set when user taps fork on an AI message; consumed when the
    // BottomSheet model selector confirms; produces the fork-context bubble in the new chat.
    private List<ChatMessage> pendingForkContext = null;
    private String pendingForkSourceModel = null;
    private String pendingForkSourceSessionId = null;
    private String pendingForkSourceTitle = null;
    private String pendingForkSourceModelId = null;
    private String pendingForkSourceDependentId = null;
    private static final String[][] AI_MODELS = {
            {"Auto", "auto", "false"},
            {"Gemini", "gemini", "false"},
            {"Mistral", "mistral", "false"},
            {"DeepSeek R1", "deepseek", "false"},
            {"Llama 3.3", "llama", "false"},
            {"GPT 5.3", "gpt5.3", "true"},
            {"Claude 4.5", "claude4.5", "true"}
    };

    // Usage tracking
    private ProgressBar usageProgressBar;
    private TextView usageText;
    // [PLAN-PILL-REVIEW] removed (hardcoded/dead plan pill; will review later)
    private Utils.UsageRing usageRing;
    private android.widget.ImageButton textSizeButton;
    private boolean isNewChatMode = true;
    private int messageLimit = 5;
    private int messagesUsed = 0;
    private boolean isMonthlySessionLimitReached = false;

    private boolean isSessionLimitReached = false;

    // Application context captured while attached. In-flight chat requests use this
    // (not getContext(), which returns null once the fragment detaches) so that when
    // the user switches tabs mid-send, the request still reaches the backend and the
    // reply is generated + saved server-side — then recovered when they return.
    private Context appContext;

    // Bounded, silent poll that recovers a reply which finished generating AFTER the
    // user left the chat and came back before it was persisted. See
    // startReplyRecoveryPoll(); cancelled on new sends, new chats, and destroy.
    private Handler replyPollHandler;
    private int replyPollAttempts;
    private static final int REPLY_POLL_MAX_ATTEMPTS = 12;   // ~48s of coverage
    private static final int REPLY_POLL_INTERVAL_MS = 4000;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        appContext = context.getApplicationContext();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_ai, container, false);
        initViews(view);
        setupSavedChatsPanel();
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Keep the input border in sync with the keyboard: when the keyboard is
        // dismissed, drop focus so the border returns to grey (it used to stay teal).
        setupKeyboardFocusSync(view);

        // Initialize UI components
        Utilities.checkAndShowInternetStatus(requireContext(), view);
        loadSuggestions();
        animateHeader();
        setupRecyclerView();
        loadUserProfile();
        // If the user was mid-chat and only switched tabs (fragment recreated), reopen
        // that same session instead of starting fresh. activeSession is null on a cold
        // app start, so a freshly launched app still begins on a new chat.
        if (activeSession != null) {
            loadSession(activeSession);
        } else {
            initializeChatSession();
        }
        setupHistoryToggle();
        fetchSavedMessagesCount();

        // Initialize side panels
        setupPanels();

        // Fetch dependents for the dependent selector
        fetchDependents();

        // Restore any text the user had typed before switching tabs (none of the
        // setup calls above write to the input box, so this is safe to do last).
        if (messageInput != null && pendingInputText != null && !pendingInputText.isEmpty()) {
            messageInput.setText(pendingInputText);
            messageInput.setSelection(messageInput.getText().length());
        }
    }

    /**
     * Drops input focus when the keyboard is dismissed so the chat border reverts
     * to its grey resting state (previously it stayed teal because the EditText kept
     * focus even after the keyboard closed).
     */
    private void setupKeyboardFocusSync(View root) {
        if (root == null) return;
        root.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            if (!isAdded() || messageInput == null) return;
            android.graphics.Rect r = new android.graphics.Rect();
            root.getWindowVisibleDisplayFrame(r);
            int screenH = root.getRootView().getHeight();
            boolean keyboardOpen = (screenH - r.bottom) > screenH * 0.15;
            if (!keyboardOpen && messageInput.hasFocus()) {
                messageInput.clearFocus();
            }
        });
    }

    private void initViews(View view) {
        headerTitle = view.findViewById(R.id.header_title);
        headerSubtitle = view.findViewById(R.id.header_subtitle);
        chatRecycler = view.findViewById(R.id.chat_recycler);
        suggestionChips = view.findViewById(R.id.suggestion_chips);
        messageInput = view.findViewById(R.id.message_input);
        sendButton = view.findViewById(R.id.send_button);
        keepHistoryButton = view.findViewById(R.id.keep_history_button);
        savedCountText = view.findViewById(R.id.saved_count);
        chatHistoryButton = view.findViewById(R.id.chat_history_button);
        newChatHeaderButton = view.findViewById(R.id.new_chat_header_button);

        // Text-size cycler — Small (12) → Medium (13) → Large (15) → repeat.
        // The icon itself scales to mirror the chosen size: subtle, immediate,
        // no toast queueing.
        textSizeButton = view.findViewById(R.id.text_size_button);
        if (textSizeButton != null) {
            // Restore icon scale to match saved size, no animation on first paint.
            applyTextSizeIconScale(getSavedChatTextSizeSp(), false);
            textSizeButton.setOnClickListener(this::cycleChatTextSize);
        }

        // Dependent selector
        inputProfileChip = view.findViewById(R.id.input_profile_chip);
        inputProfileText = view.findViewById(R.id.input_profile_text);
        inputProfileIcon = view.findViewById(R.id.input_profile_icon);
        inputProfileChevron = view.findViewById(R.id.input_profile_chevron);
        if (inputProfileChip != null) {
            inputProfileChip.setOnClickListener(v -> showDependentSelectionDropdown());
        }
        updateDependentChip();

        // Model dropdown and usage tracking
        modelDropdownButton = view.findViewById(R.id.model_dropdown_button);
        modelPillText = view.findViewById(R.id.model_pill_text);
        modelPillIcon = view.findViewById(R.id.model_pill_icon);
        maxModePill = view.findViewById(R.id.max_mode_pill);
        maxModePillText = view.findViewById(R.id.max_mode_pill_text);
        if (maxModePill != null) {
            maxModePill.setOnClickListener(v -> attemptSelectMaxMode(true));
        }
        usageProgressBar = view.findViewById(R.id.usage_progress_bar);
        usageText = view.findViewById(R.id.usage_text);   // hidden, kept for compat
        // [PLAN-PILL-REVIEW] removed (hardcoded/dead plan pill; will review later)

        // Usage ring (tap → bottom toast with the actual numbers).
        usageRing = view.findViewById(R.id.usage_ring);
        View usageRingContainer = view.findViewById(R.id.usage_ring_container);
        if (usageRingContainer != null) {
            usageRingContainer.setOnClickListener(v -> showUsageToast());
        }

        // Welcome / empty state
        // The empty-state Max hint just opens the model picker; tier gating
        // is handled inside the picker / selection flow.
        View maxHintPill = view.findViewById(R.id.max_mode_pill);
        if (maxHintPill != null && modelDropdownButton != null) {
            maxHintPill.setOnClickListener(v -> modelDropdownButton.performClick());
        }
        welcomeContainer = view.findViewById(R.id.empty_state_container);
        welcomeScroll = view.findViewById(R.id.welcome_scroll);
        welcomeLogo = view.findViewById(R.id.welcome_logo);
        welcomeGreeting = view.findViewById(R.id.welcome_greeting);
        healthDataContext = view.findViewById(R.id.health_data_context);
        hintBox = view.findViewById(R.id.hint_box);
        hintText = view.findViewById(R.id.hint_text);
        hintIcon = view.findViewById(R.id.hint_icon);
        if (hintBox != null) {
            hintBox.setOnClickListener(v -> openAddHealthData());
        }
        suggestionList = view.findViewById(R.id.suggestion_list);
        setSuggestions(cachedOrDefaultSuggestions());

        sendButton.setOnClickListener(v -> {
            String message = messageInput.getText().toString().trim();
            if (!message.isEmpty()) {
                sendMessage(message);
            }
        });

        // Input border follows focus: greyed out at rest, teal while typing
        // (mirrors the side-panel search box). Family/Max modes keep their own colours.
        messageInput.setOnFocusChangeListener((v, hasFocus) -> updateInputBorder());

        // Persist the typed-but-unsent text at process scope so it survives switching
        // bottom-nav tabs (the fragment is recreated on each tab). Restored in
        // onViewCreated; cleared automatically when a message is sent (setText("")).
        messageInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                pendingInputText = s == null ? "" : s.toString();
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });

        dbHelper = new DatabaseHelper(requireContext());
        proStatusManager = ProStatusManager.getInstance(requireContext());

        setupModelDropdown();
        updatePlanInfo();
        updateUsageBar(0, getMessageLimit());
    }

    private void setupModelDropdown() {
        modelDropdownButton.setOnClickListener(v -> showModelSelectionDropdown());
    }

    /** Logo drawable for a model id. */
    private int modelIconRes(String id) {
        switch (id) {
            case "gemini":    return R.drawable.ic_model_gemini;
            case "mistral":   return R.drawable.ic_model_mistral;
            case "deepseek":  return R.drawable.ic_model_deepseek;
            case "llama":     return R.drawable.ic_model_llama;
            case "gpt5.3":    return R.drawable.ic_model_gpt;
            case "claude4.5": return R.drawable.ic_model_claude;
            case "max":       return R.drawable.ic_model_max;
            default:          return R.drawable.ic_model_auto;
        }
    }

    /** Signature brand colour for a model id. */
    private int modelIconColor(String id) {
        switch (id) {
            case "gemini":    return Color.parseColor("#4285F4");
            case "mistral":   return Color.parseColor("#FA520F");
            case "deepseek":  return Color.parseColor("#4D6BFE");
            case "llama":     return Color.parseColor("#0866FF");
            case "gpt5.3":    return Color.parseColor("#10A37F");
            case "claude4.5": return Color.parseColor("#D97757");
            case "max":       return Color.parseColor("#FFD700");
            default:          return Color.parseColor("#008b8b");
        }
    }

    /** Short, plain-English description of what each model is good for. */
    private String modelDescription(String id) {
        switch (id) {
            case "gemini":    return "Fast, great for everyday questions";
            case "mistral":   return "Quick and lightweight";
            case "deepseek":  return "Deep, step-by-step reasoning";
            case "llama":     return "Balanced open model";
            case "gpt5.3":    return "Advanced reasoning and nuance";
            case "claude4.5": return "Careful, thorough answers";
            default:          return "Automatically picks the best model";
        }
    }

    /** Round brand-tinted chip holding a model's logo, for the picker rows. */
    private View buildModelIconChip(String id, float dp) {
        int color = modelIconColor(id);
        FrameLayout chip = new FrameLayout(requireContext());
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams((int) (34 * dp), (int) (34 * dp));
        cp.setMarginEnd((int) (14 * dp));
        chip.setLayoutParams(cp);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.argb(30, Color.red(color), Color.green(color), Color.blue(color)));
        chip.setBackground(bg);
        ImageView iv = new ImageView(requireContext());
        FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams((int) (19 * dp), (int) (19 * dp));
        ip.gravity = Gravity.CENTER;
        iv.setLayoutParams(ip);
        iv.setImageResource(modelIconRes(id));
        iv.setColorFilter(color);
        chip.addView(iv);
        return chip;
    }

    /** Teal drag handle shared by every bottom-sheet drawer in the chat UI. */
    private View buildDrawerHandle(float dp) {
        View handle = new View(requireContext());
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams((int)(34 * dp), (int)(4 * dp));
        hp.gravity = Gravity.CENTER_HORIZONTAL;
        hp.topMargin = (int)(10 * dp);
        hp.bottomMargin = (int)(14 * dp);
        handle.setLayoutParams(hp);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(2 * dp);
        bg.setColor(Color.parseColor("#008b8b"));
        handle.setBackground(bg);
        return handle;
    }

    /**
     * One clean, thin, single-line picker row — icon · name · (PRO) · teal check.
     * Shared by the model and dependent drawers so both read as the same list.
     */
    private View buildPickerRow(int iconRes, int iconColor, String name, boolean selected,
                                boolean showPro, float dp, View.OnClickListener onClick) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding((int)(22 * dp), (int)(13 * dp), (int)(22 * dp), (int)(13 * dp));
        TypedValue tv = new TypedValue();
        requireContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        row.setBackgroundResource(tv.resourceId);

        ImageView icon = new ImageView(requireContext());
        icon.setImageResource(iconRes);
        icon.setColorFilter(iconColor);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams((int)(20 * dp), (int)(20 * dp));
        ip.setMarginEnd((int)(14 * dp));
        icon.setLayoutParams(ip);
        row.addView(icon);

        TextView nameTv = new TextView(requireContext());
        nameTv.setText(name);
        nameTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        nameTv.setTextColor(selected ? Color.parseColor("#008b8b") : Color.parseColor("#E4EEEE"));
        nameTv.setSingleLine(true);
        nameTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        nameTv.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        row.addView(nameTv);

        if (showPro) {
            TextView pro = new TextView(requireContext());
            pro.setText("PRO");
            pro.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
            pro.setTextColor(Color.parseColor("#FFD700"));
            pro.setPadding((int)(7 * dp), (int)(2 * dp), (int)(7 * dp), (int)(2 * dp));
            GradientDrawable pbg = new GradientDrawable();
            pbg.setCornerRadius(8 * dp);
            pbg.setColor(Color.parseColor("#1A1500"));
            pbg.setStroke(1, Color.parseColor("#332D00"));
            pro.setBackground(pbg);
            LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            pp.setMarginStart((int)(10 * dp));
            pro.setLayoutParams(pp);
            row.addView(pro);
        }

        if (selected) {
            ImageView check = new ImageView(requireContext());
            check.setImageResource(R.drawable.ic_check);
            check.setColorFilter(Color.parseColor("#008b8b"));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams((int)(18 * dp), (int)(18 * dp));
            cp.setMarginStart((int)(12 * dp));
            check.setLayoutParams(cp);
            row.addView(check);
        }

        row.setOnClickListener(onClick);
        return row;
    }

    /** Hairline divider between picker rows, inset to align under the row text. */
    private void addSheetDivider(LinearLayout container, float dp) {
        View divider = new View(requireContext());
        divider.setBackgroundColor(Color.parseColor("#1A1A1A"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        lp.setMarginStart((int)(56 * dp));
        divider.setLayoutParams(lp);
        container.addView(divider);
    }

    /** Slim drawer title (one line, no card). */
    private TextView buildDrawerTitle(String text, float dp) {
        TextView title = new TextView(requireContext());
        title.setText(text);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setTextColor(Color.parseColor("#E4EEEE"));
        title.setPadding((int)(22 * dp), (int)(2 * dp), (int)(22 * dp), (int)(10 * dp));
        return title;
    }

    /** Handles a model row tap (pro-gating, fork finalisation, model switch). */
    private void onModelRowPicked(String id, String name, boolean requiresPro, boolean isPro,
                                  com.google.android.material.bottomsheet.BottomSheetDialog dialog) {
        if (requiresPro && !isPro) {
            dialog.dismiss();
            DialogUtils.showConfirmDialog(requireContext(),
                    "Pro Feature",
                    name + " is available on Pro and above. Upgrade to unlock all AI models including the full council mode.",
                    "Upgrade", "Not Now", false,
                    () -> new Utils.ProUpgradeDialog(requireActivity()).show(isProNow -> {}));
            return;
        }
        currentModel = id;
        selectedModel = name;
        applyModelToUi(name);

        // Snapshot fork staging BEFORE dismiss (dismiss listener clears it).
        List<ChatMessage> stagedFork = pendingForkContext;
        String stagedFromModel = pendingForkSourceModel;
        String stagedSessionId = pendingForkSourceSessionId;
        String stagedTitle = pendingForkSourceTitle;
        String stagedModelId = pendingForkSourceModelId;
        String stagedDependentId = pendingForkSourceDependentId;
        pendingForkContext = null;
        pendingForkSourceModel = null;
        pendingForkSourceSessionId = null;
        pendingForkSourceTitle = null;
        pendingForkSourceModelId = null;
        pendingForkSourceDependentId = null;

        dialog.dismiss();

        if (!"auto".equals(id)) checkModelStatus(id, name);

        if (stagedFork != null) {
            finalizeForkAfterModelPick(stagedFork, stagedFromModel,
                    stagedSessionId, stagedTitle, stagedModelId, stagedDependentId);
        } else if (!isNewChatMode) {
            startNewChatWithCurrentModel();
        }
    }

    private void showModelSelectionDropdown() {
        boolean isPro = proStatusManager.isProUser();
        float dp = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, getResources().getDisplayMetrics());

        com.google.android.material.bottomsheet.BottomSheetDialog dialog =
                new com.google.android.material.bottomsheet.BottomSheetDialog(requireContext());

        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(0, (int)(8 * dp), 0, (int)(20 * dp));
        // Background is set on the BottomSheet's own surface (in setOnShowListener) so
        // we don't end up with two stacked rounded layers (BottomSheet + container)
        // creating a visible split at the corners.

        // Teal drag handle (shared across all drawers).
        container.addView(buildDrawerHandle(dp));

        // Title — plus a fork context note only when forking.
        if (pendingForkContext != null) {
            container.addView(buildDrawerTitle("Pick AI for forked chat", dp));
            TextView sub = new TextView(requireContext());
            sub.setText(pendingForkContext.size() + " messages from "
                    + (pendingForkSourceModel == null ? "current chat" : pendingForkSourceModel)
                    + " will appear as a tappable bubble in the new chat.");
            sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            sub.setTextColor(Color.parseColor("#7A9999"));
            sub.setPadding((int)(22 * dp), 0, (int)(22 * dp), (int)(10 * dp));
            container.addView(sub);
        } else {
            container.addView(buildDrawerTitle("Choose a model", dp));
        }

        // Max — first row, headline feature, same one-line style.
        container.addView(buildPickerRow(R.drawable.ic_model_max, Color.parseColor("#FFD700"),
                "Max", isMaxMode(), true, dp, v -> { dialog.dismiss(); attemptSelectMaxMode(false); }));
        addSheetDivider(container, dp);

        // Single models — clean one-line rows.
        for (int i = 0; i < AI_MODELS.length; i++) {
            String[] model = AI_MODELS[i];
            final String name = model[0];
            final String id = model[1];
            final boolean requiresPro = model[2].equals("true");
            boolean isSelected = id.equals(currentModel);
            final boolean proUser = isPro;
            container.addView(buildPickerRow(modelIconRes(id), modelIconColor(id), name, isSelected,
                    requiresPro, dp, v -> onModelRowPicked(id, name, requiresPro, proUser, dialog)));
            if (i < AI_MODELS.length - 1) addSheetDivider(container, dp);
        }

        dialog.setContentView(container);

        // Round the BottomSheet's own surface (otherwise its square edges peek out
        // behind our content). One layer, one set of rounded corners — no split.
        dialog.setOnShowListener(d -> {
            View sheet = dialog.findViewById(
                    com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) {
                GradientDrawable sheetBg = new GradientDrawable();
                sheetBg.setColor(Color.parseColor("#111111"));
                sheetBg.setCornerRadii(new float[]{
                        20 * dp, 20 * dp,   // top-left
                        20 * dp, 20 * dp,   // top-right
                        0, 0,               // bottom-right
                        0, 0                // bottom-left
                });
                sheet.setBackground(sheetBg);
            }
        });

        // If the user dismisses without picking, drop any staged fork — they cancelled.
        dialog.setOnDismissListener(d -> {
            pendingForkContext = null;
            pendingForkSourceModel = null;
            pendingForkSourceSessionId = null;
            pendingForkSourceTitle = null;
            pendingForkSourceModelId = null;
            pendingForkSourceDependentId = null;
        });

        dialog.show();
    }

    /** Populate two rows of suggestion pills (fallback — will come from backend later) */
    private void populateSuggestionPills() {
        if (suggestionPillsRow1 == null || suggestionPillsRow2 == null) return;
        suggestionPillsRow1.removeAllViews();
        suggestionPillsRow2.removeAllViews();

        // Row 1: health data queries
        String[][] row1 = {
                {"Review my health reports", "Review my health reports and highlight any areas of concern"},
                {"Analyze my symptoms", "Analyze my recent symptoms and suggest possible causes"},
                {"Blood test insights", "Explain my latest blood test results in simple terms"},
                {"Check medication interactions", "Check my current medications for any interactions or side effects"},
                {"Heart health check", "Assess my heart health based on my data and suggest improvements"},
        };

        // Row 2: lifestyle & wellness
        String[][] row2 = {
                {"Personalized diet plan", "Suggest a diet plan based on my health profile and goals"},
                {"Sleep improvement tips", "Give me tips to improve my sleep quality based on my data"},
                {"Fitness recommendations", "Recommend exercises suited to my fitness level and health conditions"},
                {"Stress management advice", "Suggest stress management techniques based on my health profile"},
                {"Vitamin deficiency check", "Analyze my data for possible vitamin or mineral deficiencies"},
        };

        fillPillRow(suggestionPillsRow1, row1);
        fillPillRow(suggestionPillsRow2, row2);
    }

    private void fillPillRow(LinearLayout container, String[][] suggestions) {
        float dp = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 1, getResources().getDisplayMetrics());

        for (String[] suggestion : suggestions) {
            TextView pill = new TextView(requireContext());
            pill.setText(suggestion[0]);
            pill.setTextColor(Color.WHITE);
            pill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            pill.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.suggestion_pill_bg));
            pill.setPadding((int)(16 * dp), (int)(10 * dp), (int)(16 * dp), (int)(10 * dp));
            pill.setSingleLine(true);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMarginEnd((int)(8 * dp));
            pill.setLayoutParams(params);

            String fullPrompt = suggestion[1];
            pill.setOnClickListener(v -> sendQuickSuggestion(fullPrompt));

            container.addView(pill);
        }
    }

    /** Start auto-glide on both pill rows (row1 → right, row2 → left) */
    private void startPillAutoScroll() {
        stopPillAutoScroll();

        if (suggestionScroll1 != null) {
            suggestionScroll1.post(() -> {
                if (!isAdded() || suggestionScroll1 == null) return;
                View child = suggestionScroll1.getChildAt(0);
                if (child == null) return;
                int max = child.getWidth() - suggestionScroll1.getWidth();
                if (max <= 0) return;

                pillAnimator1 = ValueAnimator.ofInt(0, max);
                pillAnimator1.addUpdateListener(a ->
                        suggestionScroll1.scrollTo((int) a.getAnimatedValue(), 0));
                pillAnimator1.setDuration(max * 28L);
                pillAnimator1.setRepeatCount(ValueAnimator.INFINITE);
                pillAnimator1.setRepeatMode(ValueAnimator.REVERSE);
                pillAnimator1.setInterpolator(new LinearInterpolator());
                pillAnimator1.start();
            });
        }

        if (suggestionScroll2 != null) {
            suggestionScroll2.post(() -> {
                if (!isAdded() || suggestionScroll2 == null) return;
                View child = suggestionScroll2.getChildAt(0);
                if (child == null) return;
                int max = child.getWidth() - suggestionScroll2.getWidth();
                if (max <= 0) return;

                // Row 2 starts from right, glides left
                suggestionScroll2.scrollTo(max, 0);
                pillAnimator2 = ValueAnimator.ofInt(max, 0);
                pillAnimator2.addUpdateListener(a ->
                        suggestionScroll2.scrollTo((int) a.getAnimatedValue(), 0));
                pillAnimator2.setDuration(max * 32L);
                pillAnimator2.setRepeatCount(ValueAnimator.INFINITE);
                pillAnimator2.setRepeatMode(ValueAnimator.REVERSE);
                pillAnimator2.setInterpolator(new LinearInterpolator());
                pillAnimator2.start();
            });
        }
    }

    private void stopPillAutoScroll() {
        if (pillAnimator1 != null) { pillAnimator1.cancel(); pillAnimator1 = null; }
        if (pillAnimator2 != null) { pillAnimator2.cancel(); pillAnimator2 = null; }
    }

    private String getModelDisplayName(String modelId) {
        if (MAX_MODEL_ID.equals(modelId)) return MAX_MODEL_DISPLAY;
        for (String[] model : AI_MODELS) {
            if (model[1].equals(modelId)) return model[0];
        }
        return "Auto";
    }

    /** True if Max is currently the selected mode for this chat. */
    private boolean isMaxMode() {
        return MAX_MODEL_ID.equals(currentModel);
    }

    /**
     * Tap-handler for the Max pill (and the Max hero card in the BottomSheet).
     * Pro-gates and asks for confirmation, since Max changes the cost profile of the
     * chat (3 frontier models per turn). Mid-conversation it starts a new chat —
     * same rule the regular model picker uses.
     */
    private void attemptSelectMaxMode(boolean fromPill) {
        boolean isPro = proStatusManager.isProUser();
        if (!isPro) {
            Utils.ProUpgradeDialog dlg = new Utils.ProUpgradeDialog(requireActivity());
            dlg.show(isProNow -> {
                if (isProNow) ProStatusManager.syncProStatusOnLogin(requireContext());
            });
            return;
        }
        if (isMaxMode()) {
            // Already on Max — toggle off back to Auto so the pill is a real on/off control.
            currentModel = "auto";
            selectedModel = "Auto";
            applyModelToUi(selectedModel);
            return;
        }
        currentModel = MAX_MODEL_ID;
        selectedModel = MAX_MODEL_DISPLAY;
        applyModelToUi(selectedModel);

        // If the user is in the middle of a conversation, switching modes opens a fresh
        // chat — same behaviour as picking a different model from the BottomSheet.
        if (!isNewChatMode) {
            startNewChatWithCurrentModel();
        }
    }

    /**
     * Programmatically build the Max-mode hero card shown at the top of the model
     * BottomSheet. Same visual language as the gold-accented Max pill above the input,
     * so the user reads them as the same feature.
     */
    private View buildMaxHeroCard(float dp, com.google.android.material.bottomsheet.BottomSheetDialog dialog) {
        boolean active = isMaxMode();

        // Compact two-row card.
        // Row 1:  ✦ Max mode  · PRO ······ [ Use Max ]
        // Row 2:  GPT-5.3 · Claude 4.5 · Gemini Pro · ~3× quota
        com.google.android.material.card.MaterialCardView card =
                new com.google.android.material.card.MaterialCardView(requireContext());
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins((int)(16*dp), (int)(6*dp), (int)(16*dp), (int)(4*dp));
        card.setLayoutParams(cardLp);
        card.setRadius(14 * dp);
        card.setCardElevation(0);
        card.setStrokeWidth((int)(1 * dp));
        card.setStrokeColor(Color.parseColor(active ? "#FFD700" : "#3A3000"));
        card.setCardBackgroundColor(Color.parseColor(active ? "#1A1500" : "#10100A"));

        LinearLayout col = new LinearLayout(requireContext());
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding((int)(14*dp), (int)(11*dp), (int)(10*dp), (int)(11*dp));

        // ── Row 1: bolt · "Max mode" · PRO badge · spacer · CTA pill ───────
        LinearLayout headerRow = new LinearLayout(requireContext());
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        ImageView bolt = new ImageView(requireContext());
        bolt.setImageResource(R.drawable.ic_bolt);
        bolt.setColorFilter(Color.parseColor("#FFD700"));
        LinearLayout.LayoutParams boltLp = new LinearLayout.LayoutParams((int)(14*dp), (int)(14*dp));
        boltLp.setMarginEnd((int)(6*dp));
        bolt.setLayoutParams(boltLp);
        headerRow.addView(bolt);

        TextView titleTv = new TextView(requireContext());
        titleTv.setText("Max mode");
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        titleTv.setTypeface(null, android.graphics.Typeface.BOLD);
        titleTv.setTextColor(Color.parseColor("#FFD700"));
        headerRow.addView(titleTv);

        TextView proBadge = new TextView(requireContext());
        proBadge.setText("PRO");
        proBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8);
        proBadge.setTextColor(Color.parseColor("#FFD700"));
        proBadge.setPadding((int)(6*dp), (int)(2*dp), (int)(6*dp), (int)(2*dp));
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setCornerRadius(8 * dp);
        badgeBg.setColor(Color.parseColor("#1A1500"));
        badgeBg.setStroke(1, Color.parseColor("#332D00"));
        proBadge.setBackground(badgeBg);
        LinearLayout.LayoutParams proLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        proLp.setMarginStart((int)(8*dp));
        proBadge.setLayoutParams(proLp);
        headerRow.addView(proBadge);

        View spacer = new View(requireContext());
        LinearLayout.LayoutParams spacerLp =
                new LinearLayout.LayoutParams(0, 1, 1);
        spacer.setLayoutParams(spacerLp);
        headerRow.addView(spacer);

        // CTA pill — inline with the title so the card is one row tall here.
        TextView cta = new TextView(requireContext());
        cta.setText(active ? "Switch off" : "Use Max");
        cta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        cta.setTypeface(null, android.graphics.Typeface.BOLD);
        cta.setAllCaps(false);
        cta.setTextColor(Color.parseColor(active ? "#0E0E0E" : "#FFD700"));
        cta.setPadding((int)(14*dp), (int)(6*dp), (int)(14*dp), (int)(6*dp));

        GradientDrawable ctaBg = new GradientDrawable();
        ctaBg.setCornerRadius(999 * dp);
        if (active) {
            ctaBg.setColor(Color.parseColor("#FFD700"));
            ctaBg.setStroke((int)(1*dp), Color.parseColor("#FFD700"));
        } else {
            ctaBg.setColor(Color.parseColor("#1A1500"));
            ctaBg.setStroke((int)(1*dp), Color.parseColor("#5C4A00"));
        }
        cta.setBackground(ctaBg);
        cta.setClickable(true);
        cta.setFocusable(true);
        cta.setOnClickListener(v -> {
            dialog.dismiss();
            attemptSelectMaxMode(false);
        });
        headerRow.addView(cta);

        col.addView(headerRow);

        // ── Row 2: models · ~5× quota — same compact line, just calmer ───
        TextView sub = new TextView(requireContext());
        sub.setText("GPT-5.3 · Claude 4.5 · Gemini Pro  ·  ~5× quota");
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        sub.setTextColor(Color.parseColor("#9A8B5A"));
        sub.setSingleLine(true);
        sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = (int)(6*dp);
        sub.setLayoutParams(subLp);
        col.addView(sub);

        card.addView(col);
        return card;
    }

    /** Single point that updates every UI surface tied to the current model selection. */
    private void applyModelToUi(String displayName) {
        if (modelPillText != null) modelPillText.setText(displayName);
        if (headerSubtitle != null) headerSubtitle.setText(displayName);
        boolean max = isMaxMode();
        // Leading model logo on the pill, tinted its brand colour.
        if (modelPillIcon != null) {
            String iconId = max ? MAX_MODEL_ID : currentModel;
            modelPillIcon.setImageResource(modelIconRes(iconId));
            modelPillIcon.setColorFilter(modelIconColor(iconId));
        }
        if (maxModePill != null) {
            maxModePill.setBackgroundResource(
                    max ? R.drawable.max_pill_active_bg : R.drawable.max_pill_bg);
        }
        if (maxModePillText != null) {
            maxModePillText.setTextColor(
                    max ? Color.parseColor("#0E0E0E") : Color.parseColor("#FFD700"));
            maxModePillText.setText(max ? "Max ✦" : "Max");
        }

        // Input card border is owned by updateInputBorder() so family selection
        // (white) and Max mode (bright teal) never fight over it.
        updateInputBorder();
    }

    private int getMessageLimit() {
        String tier = proStatusManager.getUserTier();
        switch (tier) {
            case "ultra": return 100;
            case "pro":
            case "family":
            case "family_member": return 50;
            case "plus": return 25;
            default: return 5; // free
        }
    }

    private void updatePlanInfo() {
        String tier = proStatusManager.getUserTier();
        // [PLAN-PILL-REVIEW] removed (hardcoded/dead plan pill; will review later)
        boolean isCouncilTier = "ultra".equals(tier) || "pro".equals(tier)
                || "family".equals(tier) || "family_member".equals(tier);
        // Show council branding in subtitle when Pro/Ultra user is on Auto mode
        if (isCouncilTier && "auto".equals(currentModel) && headerSubtitle != null) {
            headerSubtitle.setText("3-Model Council ✦");
        }
        messageLimit = getMessageLimit();
    }

    /** Update plan label from backend-authoritative tier string */
    private void updatePlanLabelFromTier(String tier) {
        // [PLAN-PILL-REVIEW] removed (hardcoded/dead plan pill; will review later)
    }

    /**
     * Async check: asks backend if the selected model is currently reachable.
     * If unavailable, reverts to Auto and shows a Toast. Non-blocking.
     */
    private void checkModelStatus(String modelId, String modelName) {
        Context context = getContext();
        if (context == null) return;
        TokenManager tokenManager = TokenManager.getInstance(context);
        String token = tokenManager != null ? tokenManager.getToken() : null;
        if (token == null) return;

        String url = ApiConfig.BASE_URL + "/api/chat/model-status?model=" + modelId;

        StringRequest request = new StringRequest(Request.Method.GET, url,
                response -> {
                    ApiConfig.logRestCall(url, true, "Model status checked: " + modelId);
                    if (!isAdded()) return;
                    try {
                        JSONObject json = new JSONObject(response);
                        boolean available = json.optBoolean("available", true);
                        if (!available) {
                            String fallback = json.optString("fallback", "auto");
                            String fallbackName = getModelDisplayName(fallback);
                            requireActivity().runOnUiThread(() -> {
                                currentModel = fallback;
                                selectedModel = fallbackName;
                                applyModelToUi(fallbackName);
                                Utilities.toastLong(requireContext(), modelName + " is currently unavailable. Switched to " + fallbackName + ".");
                            });
                        }
                    } catch (JSONException e) {
                        Log.w(TAG, "Model status parse error — assuming available");
                    }
                },
                error -> {
                    ApiConfig.logRestCall(url, false, error.toString());
                    // Endpoint missing or server error — silently assume model is available
                    Log.d(TAG, "Model status check failed for " + modelId + " — assuming available");
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + token);
                return headers;
            }
        };

        request.setRetryPolicy(new com.android.volley.DefaultRetryPolicy(5000, 0, 1f));
        Volley.newRequestQueue(context).add(request);
    }

    private void updateUsageBar(int used, int limit) {
        messagesUsed = used;
        messageLimit = limit;
        int progress = (limit > 0) ? (int) ((float) used / limit * 100) : 0;

        // Hidden progress bar kept for compatibility.
        if (usageProgressBar != null) {
            usageProgressBar.setProgress(Math.min(progress, 100));
        }

        // Drive the visible top-bar usage ring.
        if (usageRing != null) {
            usageRing.setUsage(used, limit);
        }

        // Hidden TextView kept for any legacy reads.
        if (usageText != null) {
            usageText.setText(used + "/" + limit);
        }
    }

    /**
     * Bottom toast with the human-readable usage. Shown when the user taps the
     * tiny ring in the top bar — keeps the bar itself clean.
     */
    private void showUsageToast() {
        int used = messagesUsed;
        int limit = messageLimit;
        String msg;
        if (limit <= 0) {
            msg = "Loading your usage…";
        } else {
            int remaining = Math.max(0, limit - used);
            msg = used + " of " + limit + " messages used · " + remaining + " left this session";
        }
        Toast t = Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT);
        t.setGravity(android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL,
                0, (int) (96 * getResources().getDisplayMetrics().density));
        t.show();
    }

    private void startNewChatWithCurrentModel() {
        isNewChatMode = true;
        sessionId = null;
        cancelReplyRecoveryPoll(); // leaving this chat — stop watching for its reply
        clearActiveSession(); // explicit new chat — don't reopen the old one on tab switch
        isSessionLimitReached = false;
        // Note: isMonthlySessionLimitReached is NOT reset here — it persists until month rolls over

        // Reset dependent selection to self
        selectedDependentId = null;
        selectedDependentName = "Myself";
        updateDependentChip();

        chatAdapter.clear();
        messageInput.setEnabled(true);
        sendButton.setEnabled(true);
        messageInput.setHint("Ask anything about your health...");
        messageInput.setBackground(null);

        updateUsageBar(0, getMessageLimit());

        headerTitle.setText("");  // new chat: header shows only the two icons
        applyModelToUi(selectedModel);

        // Empty state shows automatically via adapter observer (chat cleared → count 0)
    }

    /**
     * Step 1 of fork: snapshot the conversation up to and including the tapped AI
     * message, stash it, then open the model BottomSheet so the user picks an AI
     * for the new chat. The actual fork is finalised in {@link #finalizeForkAfterModelPick}
     * once the user confirms a model.
     */
    private void beginForkFromMessage(int position) {
        if (chatAdapter == null) return;
        List<ChatMessage> live = chatAdapter.getMessages();
        if (position < 0 || position >= live.size()) return;

        // Deep-ish copy so later edits/clears in the live list don't mutate our snapshot.
        // ChatMessage holds primitives + strings, so a shallow copy of references is fine —
        // the bubble only ever reads getMessage() / isFromAI() / getTimestamp().
        List<ChatMessage> snapshot = new ArrayList<>();
        for (int i = 0; i <= position; i++) {
            ChatMessage src = live.get(i);
            if (src.isForkContext()) continue; // never nest fork bubbles
            snapshot.add(src);
        }
        if (snapshot.isEmpty()) return;

        pendingForkContext = snapshot;
        pendingForkSourceModel = selectedModel;
        pendingForkSourceSessionId = sessionId;
        pendingForkSourceTitle = headerTitle != null ? headerTitle.getText().toString() : null;
        pendingForkSourceModelId = currentModel;
        pendingForkSourceDependentId = selectedDependentId;

        // Subtle hint that this isn't a plain model swap.
        Utilities.toast(requireContext(), "Pick an AI to fork this chat into");

        showModelSelectionDropdown();
    }

    /**
     * Step 2 of fork: open a fresh chat with the just-picked model and drop a single
     * fork-context bubble at the top. The bubble is purely client-side: it isn't sent
     * to the backend, so the new session is born clean and the new model picks up
     * from whatever the user types next.
     */
    private void finalizeForkAfterModelPick(List<ChatMessage> snapshot, String fromModel,
                                             String fromSessionId, String fromTitle,
                                             String fromModelId, String fromDependentId) {
        if (snapshot == null || snapshot.isEmpty()) return;

        // Reset to a brand-new session under the newly picked model.
        startNewChatWithCurrentModel();

        // Make sure the welcome state isn't blocking the chat list — adding a bubble
        // would normally trigger that, but we want it visible immediately.
        setWelcomeVisibility(View.GONE);
        chatRecycler.setVisibility(View.VISIBLE);

        ChatMessage forkBubble = new ChatMessage("", true);
        forkBubble.setForkContext(true);
        forkBubble.setForkContextMessages(snapshot);
        forkBubble.setForkSourceModelName(fromModel);
        forkBubble.setForkSourceSessionId(fromSessionId);
        forkBubble.setForkSourceTitle(fromTitle);
        forkBubble.setForkSourceModelId(fromModelId);
        forkBubble.setForkSourceDependentId(fromDependentId);
        chatAdapter.addMessage(forkBubble);
        scrollToBottom();
    }

    /**
     * Show the full forked-chat history in the same dialog style used elsewhere in the
     * app (Dialog + R.style.DialogTheme). Reuses {@link ChatAdapter} in read-only mode
     * so we don't reinvent message rendering.
     */
    private void showForkedChatPreviewDialog(ChatMessage forkBubble) {
        if (forkBubble == null || forkBubble.getForkContextMessages() == null) return;

        Dialog dialog = new Dialog(requireContext(), R.style.DialogTheme);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_forked_chat);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        List<ChatMessage> msgs = forkBubble.getForkContextMessages();
        TextView subtitle = dialog.findViewById(R.id.forked_chat_subtitle);
        String src = forkBubble.getForkSourceModelName();
        subtitle.setText(msgs.size() + " message" + (msgs.size() == 1 ? "" : "s")
                + (src != null && !src.isEmpty() ? "  ·  from " + src : ""));

        RecyclerView recycler = dialog.findViewById(R.id.forked_chat_recycler);
        LinearLayoutManager lm = new LinearLayoutManager(requireContext());
        recycler.setLayoutManager(lm);
        ChatAdapter previewAdapter = new ChatAdapter(requireContext());
        previewAdapter.setReadOnly(true);
        recycler.setAdapter(previewAdapter);
        for (ChatMessage m : msgs) previewAdapter.addMessage(m);

        // Cap dialog height so very long chats don't overflow the screen.
        recycler.post(() -> {
            int maxH = (int) (getResources().getDisplayMetrics().heightPixels * 0.75f);
            if (recycler.getHeight() > maxH) {
                ViewGroup.LayoutParams lp = recycler.getLayoutParams();
                lp.height = maxH;
                recycler.setLayoutParams(lp);
            }
        });

        ImageButton close = dialog.findViewById(R.id.forked_chat_close);
        if (close != null) close.setOnClickListener(v -> dialog.dismiss());

        // "Open original chat" — load the original session if we still have its id.
        // Hidden when the source session is unknown (defensive — shouldn't happen,
        // since you can only fork off an AI message which lives inside a session).
        com.google.android.material.button.MaterialButton openOriginal =
                dialog.findViewById(R.id.forked_chat_open_original);
        if (openOriginal != null) {
            String origId = forkBubble.getForkSourceSessionId();
            if (origId == null || origId.isEmpty()) {
                openOriginal.setVisibility(View.GONE);
            } else {
                openOriginal.setOnClickListener(v -> {
                    dialog.dismiss();
                    openOriginalSessionFromFork(forkBubble);
                });
            }
        }

        dialog.show();
    }

    /** Reuse the existing loadSession() flow with a minimal ChatSession built from
     *  whatever we captured at fork time. The session's real metadata will be
     *  refreshed the next time chat history is fetched. */
    private void openOriginalSessionFromFork(ChatMessage forkBubble) {
        String origId = forkBubble.getForkSourceSessionId();
        if (origId == null || origId.isEmpty()) return;

        String title = forkBubble.getForkSourceTitle();
        if (title == null || title.isEmpty()) title = "Original Chat";

        ChatSession session = new ChatSession(
                origId, title, "", 0, System.currentTimeMillis(),
                userProfile != null ? userProfile.getId() : 0);
        if (forkBubble.getForkSourceModelId() != null) {
            session.setModelType(forkBubble.getForkSourceModelId());
        }
        if (forkBubble.getForkSourceDependentId() != null) {
            session.setDependentId(forkBubble.getForkSourceDependentId());
        }
        loadSession(session);
    }

    private void createSessionThenSend(String firstMessage) {
        String url = ApiConfig.BASE_URL + "/api/chat/sessions";
        Context context = getContext();
        if (context == null) return;
        TokenManager tokenManager = TokenManager.getInstance(context);

        String title = firstMessage.length() > 40
                ? firstMessage.substring(0, 40) + "..."
                : firstMessage;

        JSONObject requestBody = new JSONObject();
        try {
            requestBody.put("title", title);
            requestBody.put("modelType", currentModel);
            if (selectedDependentId != null) {
                requestBody.put("dependentId", selectedDependentId);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error creating session request body", e);
            hideThinkingAnimation();
            sendButton.setEnabled(true);
            messageInput.setEnabled(true);
            return;
        }

        StringRequest request = new StringRequest(Request.Method.POST, url,
                response -> {
                    ApiConfig.logRestCall(url, true, "Chat session created");
                    try {
                        JSONObject responseObj = new JSONObject(response);
                        sessionId = responseObj.getString("sessionId");
                        isNewChatMode = false;
                        isSessionLimitReached = false;

                        String sessionTitle = responseObj.getString("title");

                        // Remember this freshly created session so tab switches reopen it.
                        ChatSession created = new ChatSession(sessionId, sessionTitle, "", 0,
                                System.currentTimeMillis(), 0);
                        created.setModelType(currentModel);
                        created.setDependentId(selectedDependentId);
                        setActiveSession(created);
                        if (selectedDependentId != null) {
                            headerTitle.setText(selectedDependentName + " · " + sessionTitle);
                        } else {
                            headerTitle.setText(sessionTitle);
                        }

                        // Profile chip stays available in the input bar mid-chat.

                        sendMessageToBackend(firstMessage);

                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing create session response", e);
                        hideThinkingAnimation();
                        sendButton.setEnabled(true);
                        messageInput.setEnabled(true);
                        showErrorMessage("Failed to start chat");
                    }
                },
                error -> {
                    ApiConfig.logRestCall(url, false, error.toString());
                    hideThinkingAnimation();
                    sendButton.setEnabled(true);
                    messageInput.setEnabled(true);
                    ErrorHandler.ParsedError parsed = ErrorHandler.parse(error);
                    if (parsed.type == ErrorHandler.ErrorType.AUTH_EXPIRED && isAdded()) {
                        ErrorHandler.handleAuthExpired(requireContext());
                    } else {
                        showErrorMessage(parsed.type == ErrorHandler.ErrorType.NETWORK_ERROR
                                ? "No internet connection." : "Failed to start chat. Please try again.");
                    }
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
                headers.put("Content-Type", "application/json");
                return headers;
            }
        };

        RequestQueue queue = Volley.newRequestQueue(context);
        queue.add(request);
    }

    private void setupPanels() {
        setupChatHistoryPanel();
    }

    private void setupChatHistoryPanel() {
        // Material 3 SideSheetDialog — the standardized Android side panel.
        // Gives us: edge-aware insets, swipe-to-dismiss, predictive back, and
        // proper accessibility — all for free, replacing our hand-rolled Dialog.
        com.google.android.material.sidesheet.SideSheetDialog sideSheet =
                new com.google.android.material.sidesheet.SideSheetDialog(requireContext());
        sideSheet.setContentView(R.layout.layout_chat_side_panel);
        // Open from the LEFT edge. START resolves to left in LTR layouts. Must be
        // called AFTER setContentView (the sheet view is created there) and before
        // show() — runtime edge changes aren't supported.
        sideSheet.setSheetEdge(Gravity.START);

        sideSheet.setOnShowListener(d -> {
            // 1. Make the dialog window fill the screen so the scrim covers
            //    everything behind the sheet (otherwise the dim only sits next
            //    to the sheet and the rest of the app stays visible).
            Window w = sideSheet.getWindow();
            if (w != null) {
                w.setLayout(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT);
                w.setDimAmount(0.55f);
            }
            // 2. M3 side sheets default to ~400dp max width. Stretch the sheet
            //    container to 88% of screen width so it feels like a real panel.
            View sheet = sideSheet.findViewById(
                    com.google.android.material.R.id.m3_side_sheet);
            if (sheet != null) {
                int target = (int) (getResources().getDisplayMetrics().widthPixels * 0.88);
                ViewGroup.LayoutParams lp = sheet.getLayoutParams();
                if (lp != null) {
                    lp.width = target;
                    sheet.setLayoutParams(lp);
                }
            }
        });

        chatHistoryPanel = sideSheet;

        // Find views in the panel
        chatSessionsRecycler = chatHistoryPanel.findViewById(R.id.chat_sessions_recycler);
        emptyStateView = chatHistoryPanel.findViewById(R.id.empty_state);
        newChatButton = chatHistoryPanel.findViewById(R.id.new_chat_button);
        deleteAllChatsButton = chatHistoryPanel.findViewById(R.id.delete_all_chats_button);
        closePanelButton = chatHistoryPanel.findViewById(R.id.close_panel_button);
        historyLoading = chatHistoryPanel.findViewById(R.id.history_loading);

        // Search box — filters the loaded sessions live (focus visuals handled by
        // the field's own background selector: grey at rest, white on focus).
        chatSearchInput = chatHistoryPanel.findViewById(R.id.search_input);
        if (chatSearchInput != null) {
            chatSearchInput.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                    filterChatSessions(s == null ? "" : s.toString());
                }
                @Override public void afterTextChanged(android.text.Editable s) {}
            });
        }

        // Setup RecyclerView
        chatSessionsRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        chatSessionsAdapter = new ChatHistoryAdapter();
        chatSessionsRecycler.setAdapter(chatSessionsAdapter);

        // Setup buttons
        newChatButton.setOnClickListener(v -> {
            createNewChatSession();
            chatHistoryPanel.dismiss();
        });

        deleteAllChatsButton.setOnClickListener(v -> {
            showDeleteAllChatsConfirmDialog();
        });

        closePanelButton.setOnClickListener(v -> {
            chatHistoryPanel.dismiss();
        });

        // Setup chat history button (3-dots in pill) in the main view
        chatHistoryButton.setOnClickListener(v -> {
            showChatHistoryPanel();
        });

        // Setup new chat button (left icon in pill) in the main view
        newChatHeaderButton.setOnClickListener(v -> {
            createNewChatSession();
        });
    }

    /** Fetch the user's dependents to populate the chat-for selector */
    private void fetchDependents() {
        Context context = getContext();
        if (context == null) return;
        TokenManager tokenManager = TokenManager.getInstance(context);
        String token = tokenManager != null ? tokenManager.getToken() : null;
        if (token == null) return;

        // Try dependent-users endpoint first (living dependents stored as User docs)
        String url = ApiConfig.BASE_URL + "/api/dependents/users";

        StringRequest request = new StringRequest(Request.Method.GET, url,
                response -> {
                    ApiConfig.logRestCall(url, true, "Dependents fetched");
                    if (!isAdded()) return;
                    try {
                        JSONObject responseObj = new JSONObject(response);
                        JSONArray deps = responseObj.optJSONArray("dependents");
                        dependentsList.clear();
                        if (deps != null && deps.length() > 0) {
                            for (int i = 0; i < deps.length(); i++) {
                                dependentsList.add(deps.getJSONObject(i));
                            }
                        }

                        // Also fetch non-user dependents (child/deceased from Dependent model)
                        fetchNonUserDependents(token);
                    } catch (JSONException e) {
                        Log.w(TAG, "Error parsing dependents response", e);
                        updateDependentSelectorVisibility();
                    }
                },
                error -> {
                    ApiConfig.logRestCall(url, false, error.toString());
                    Log.d(TAG, "Dependents fetch failed — may not have any");
                    updateDependentSelectorVisibility();
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + token);
                return headers;
            }
        };

        request.setRetryPolicy(new com.android.volley.DefaultRetryPolicy(10000, 0, 1f));
        Volley.newRequestQueue(context).add(request);
    }

    private void fetchNonUserDependents(String token) {
        Context context = getContext();
        if (context == null) return;

        String url = ApiConfig.BASE_URL + "/api/dependents";

        StringRequest request = new StringRequest(Request.Method.GET, url,
                response -> {
                    ApiConfig.logRestCall(url, true, "Non-user dependents fetched");
                    if (!isAdded()) return;
                    try {
                        JSONObject responseObj = new JSONObject(response);
                        JSONArray deps = responseObj.optJSONArray("dependents");
                        if (deps != null) {
                            for (int i = 0; i < deps.length(); i++) {
                                JSONObject dep = deps.getJSONObject(i);
                                // Only include active, non-deceased dependents
                                String type = dep.optString("type", "");
                                String status = dep.optString("status", "active");
                                if (!"deceased".equals(type) && "active".equals(status)) {
                                    // Avoid duplicates — check by _id
                                    String depId = dep.getString("_id");
                                    boolean alreadyExists = false;
                                    for (JSONObject existing : dependentsList) {
                                        if (depId.equals(existing.optString("_id"))) {
                                            alreadyExists = true;
                                            break;
                                        }
                                    }
                                    if (!alreadyExists) {
                                        dependentsList.add(dep);
                                    }
                                }
                            }
                        }
                    } catch (JSONException e) {
                        Log.w(TAG, "Error parsing non-user dependents", e);
                    }
                    updateDependentSelectorVisibility();
                },
                error -> {
                    ApiConfig.logRestCall(url, false, error.toString());
                    updateDependentSelectorVisibility();
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + token);
                return headers;
            }
        };

        request.setRetryPolicy(new com.android.volley.DefaultRetryPolicy(10000, 0, 1f));
        Volley.newRequestQueue(context).add(request);
    }

    /**
     * Refresh the in-input health-profile chip. When a family member is active
     * the chip shows their name in white and the input border turns white too,
     * so it is always obvious whose health the chat is about.
     */
    private void updateDependentChip() {
        boolean family = selectedDependentId != null;
        int tint = family ? Color.parseColor("#FFFFFF") : Color.parseColor("#008b8b");
        if (inputProfileText != null) {
            inputProfileText.setText(family ? selectedDependentName : "You");
            inputProfileText.setTextColor(tint);
        }
        if (inputProfileIcon != null) inputProfileIcon.setColorFilter(tint);
        if (inputProfileChevron != null) inputProfileChevron.setColorFilter(tint);
        updateInputBorder();
    }

    /**
     * Central owner of the input card's border. Priority: a selected family
     * member (white) wins over Max mode (bright teal) wins over the resting
     * teal. Keeps the two features from fighting over the same border.
     */
    private void updateInputBorder() {
        View root = getView();
        if (root == null) return;
        com.google.android.material.card.MaterialCardView inputCard = root.findViewById(R.id.input_card);
        if (inputCard == null) return;
        float dp = getResources().getDisplayMetrics().density;
        if (selectedDependentId != null) {
            inputCard.setStrokeWidth((int) (1.5f * dp));
            inputCard.setStrokeColor(Color.parseColor("#FFFFFF"));
            inputCard.setCardBackgroundColor(Color.parseColor("#0B0B0B"));
        } else if (isMaxMode()) {
            inputCard.setStrokeWidth((int) (2 * dp));
            inputCard.setStrokeColor(Color.parseColor("#1FB9B9"));
            inputCard.setCardBackgroundColor(Color.parseColor("#0F1A1A"));
        } else {
            // Resting default: grey border when not focused, teal while typing.
            boolean focused = messageInput != null && messageInput.hasFocus();
            inputCard.setStrokeWidth((int) (1 * dp));
            inputCard.setStrokeColor(Color.parseColor(focused ? "#008b8b" : "#333333"));
            inputCard.setCardBackgroundColor(Color.parseColor("#111111"));
        }
    }

    /** Cached suggestions for instant paint, or a sensible default set. */
    private java.util.List<AiSuggestion> cachedOrDefaultSuggestions() {
        try {
            String raw = requireContext()
                    .getSharedPreferences("chat_suggestions", android.content.Context.MODE_PRIVATE)
                    .getString("items", null);
            if (raw != null) {
                java.util.List<AiSuggestion> list = parseSuggestions(new JSONArray(raw));
                if (list.size() >= 3) return list;
            }
        } catch (Exception ignored) {}
        java.util.List<AiSuggestion> def = new java.util.ArrayList<>();
        def.add(new AiSuggestion("Explain my latest report", "Turns your lab numbers into plain language you can act on."));
        def.add(new AiSuggestion("Any risks in my medications?", "Checks what you take for interactions and side effects."));
        def.add(new AiSuggestion("What should I eat for more energy?", "Nutrition tips shaped by your profile and goals."));
        return def;
    }

    private java.util.List<AiSuggestion> parseSuggestions(JSONArray arr) {
        java.util.List<AiSuggestion> list = new java.util.ArrayList<>();
        if (arr == null) return list;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            String q = o.optString("q", "").trim();
            String why = o.optString("why", "").trim();
            if (!q.isEmpty()) list.add(new AiSuggestion(q, why));
        }
        return list;
    }

    /**
     * Take the full pool from the backend: show the first few, keep the rest as
     * backup for when the user marks one "Not helpful".
     */
    private void setSuggestions(java.util.List<AiSuggestion> pool) {
        suggestionBackup.clear();
        knownQuestions.clear();
        java.util.List<AiSuggestion> display = new java.util.ArrayList<>();
        if (pool != null) {
            for (AiSuggestion s : pool) {
                if (s == null || s.q == null || s.q.isEmpty()) continue;
                String key = s.q.toLowerCase();
                if (knownQuestions.contains(key)) continue;
                knownQuestions.add(key);
                if (display.size() < SUGGESTION_DISPLAY_COUNT) display.add(s);
                else suggestionBackup.add(s);
            }
        }
        buildSuggestionRows(display);
    }

    /**
     * "Suggested for you" as Material cards. Each card is an accordion: tap to
     * reveal why Richie suggested it, then Ask or mark it Not helpful. Only one
     * card is open at a time.
     */
    private void buildSuggestionRows(java.util.List<AiSuggestion> items) {
        if (suggestionList == null) return;
        suggestionList.removeAllViews();
        expandedSuggestionCard = null;
        expandedSuggestionExpand = null;
        expandedSuggestionChevron = null;
        if (items == null || items.isEmpty()) return;
        float dp = getResources().getDisplayMetrics().density;

        LinearLayout header = new LinearLayout(requireContext());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, (int) (12 * dp));
        ImageView spark = new ImageView(requireContext());
        spark.setImageResource(R.drawable.ic_model_auto);
        spark.setColorFilter(Color.parseColor("#008b8b"));
        LinearLayout.LayoutParams sparkLp = new LinearLayout.LayoutParams((int) (13 * dp), (int) (13 * dp));
        sparkLp.setMarginEnd((int) (7 * dp));
        header.addView(spark, sparkLp);
        TextView headerLabel = new TextView(requireContext());
        headerLabel.setText("Suggested for you");
        headerLabel.setTextColor(Color.parseColor("#5F8A8A"));
        headerLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        headerLabel.setLetterSpacing(0.03f);
        header.addView(headerLabel);
        suggestionList.addView(header);

        for (AiSuggestion item : items) {
            suggestionList.addView(buildSuggestionCard(item, dp));
        }

        if (animateNextSuggestions) {
            animateNextSuggestions = false;
            float startY = 14 * dp;
            for (int i = 0; i < suggestionList.getChildCount(); i++) {
                View v = suggestionList.getChildAt(i);
                v.animate().cancel();
                v.setAlpha(0f);
                v.setTranslationY(startY);
                v.animate().alpha(1f).translationY(0f)
                        .setStartDelay(i * 65L)
                        .setDuration(300)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f))
                        .start();
            }
        }
    }

    private View buildSuggestionCard(final AiSuggestion item, float dp) {
        final com.google.android.material.card.MaterialCardView card =
                new com.google.android.material.card.MaterialCardView(requireContext());
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = (int) (10 * dp);
        card.setLayoutParams(cardLp);
        card.setRadius(14 * dp);
        card.setCardElevation(0f);
        card.setStrokeWidth((int) (1 * dp));
        card.setStrokeColor(Color.parseColor("#1A2A2A"));
        card.setCardBackgroundColor(Color.parseColor("#0C1414"));

        LinearLayout col = new LinearLayout(requireContext());
        col.setOrientation(LinearLayout.VERTICAL);

        // Header row — always visible, tap to toggle.
        LinearLayout headerRow = new LinearLayout(requireContext());
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setPadding((int) (15 * dp), (int) (14 * dp), (int) (13 * dp), (int) (14 * dp));
        headerRow.setClickable(true);
        headerRow.setFocusable(true);
        TypedValue tv = new TypedValue();
        requireContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        headerRow.setBackgroundResource(tv.resourceId);

        ImageView lead = new ImageView(requireContext());
        lead.setImageResource(R.drawable.ic_model_auto);
        lead.setColorFilter(Color.parseColor("#008b8b"));
        LinearLayout.LayoutParams leadLp = new LinearLayout.LayoutParams((int) (15 * dp), (int) (15 * dp));
        leadLp.setMarginEnd((int) (12 * dp));
        headerRow.addView(lead, leadLp);

        TextView qView = new TextView(requireContext());
        qView.setText(item.q);
        qView.setTextColor(Color.parseColor("#DCE6E6"));
        qView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        headerRow.addView(qView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        final ImageView chevron = new ImageView(requireContext());
        chevron.setImageResource(R.drawable.ic_chevron_down);
        chevron.setColorFilter(Color.parseColor("#4A6A6A"));
        headerRow.addView(chevron, new LinearLayout.LayoutParams((int) (16 * dp), (int) (16 * dp)));
        col.addView(headerRow);

        // Expandable "why + actions" — hidden until tapped.
        final LinearLayout expand = new LinearLayout(requireContext());
        expand.setOrientation(LinearLayout.VERTICAL);
        expand.setPadding((int) (15 * dp), 0, (int) (15 * dp), (int) (14 * dp));
        expand.setVisibility(View.GONE);

        View divider = new View(requireContext());
        divider.setBackgroundColor(Color.parseColor("#152525"));
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, (int) dp));
        divLp.bottomMargin = (int) (12 * dp);
        expand.addView(divider, divLp);

        LinearLayout whyLabelRow = new LinearLayout(requireContext());
        whyLabelRow.setOrientation(LinearLayout.HORIZONTAL);
        whyLabelRow.setGravity(Gravity.CENTER_VERTICAL);
        ImageView whyMark = new ImageView(requireContext());
        whyMark.setImageResource(R.drawable.ic_model_auto);
        whyMark.setColorFilter(Color.parseColor("#008b8b"));
        LinearLayout.LayoutParams whyMarkLp = new LinearLayout.LayoutParams((int) (11 * dp), (int) (11 * dp));
        whyMarkLp.setMarginEnd((int) (6 * dp));
        whyLabelRow.addView(whyMark, whyMarkLp);
        TextView whyLabel = new TextView(requireContext());
        whyLabel.setText("WHY RICHIE SUGGESTED THIS");
        whyLabel.setTextColor(Color.parseColor("#5F8A8A"));
        whyLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f);
        whyLabel.setLetterSpacing(0.08f);
        whyLabelRow.addView(whyLabel);
        expand.addView(whyLabelRow);

        TextView whyView = new TextView(requireContext());
        whyView.setText(item.why == null || item.why.isEmpty()
                ? "Tailored to your profile and health data." : item.why);
        whyView.setTextColor(Color.parseColor("#AEC0C0"));
        whyView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        whyView.setLineSpacing(dp * 2, 1f);
        LinearLayout.LayoutParams whyLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        whyLp.topMargin = (int) (6 * dp);
        whyLp.bottomMargin = (int) (14 * dp);
        whyView.setLayoutParams(whyLp);
        expand.addView(whyView);

        LinearLayout btnRow = new LinearLayout(requireContext());
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView ask = new TextView(requireContext());
        ask.setText("Ask");
        ask.setTextColor(Color.WHITE);
        ask.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        ask.setTypeface(null, android.graphics.Typeface.BOLD);
        ask.setGravity(Gravity.CENTER);
        ask.setPadding((int) (22 * dp), (int) (8 * dp), (int) (22 * dp), (int) (8 * dp));
        GradientDrawable askBg = new GradientDrawable();
        askBg.setCornerRadius(16 * dp);
        askBg.setColor(Color.parseColor("#008b8b"));
        ask.setBackground(askBg);
        ask.setClickable(true);
        ask.setOnClickListener(v -> sendQuickSuggestion(item.q));
        btnRow.addView(ask);

        TextView notHelpful = new TextView(requireContext());
        notHelpful.setText("Not helpful");
        notHelpful.setTextColor(Color.parseColor("#7A8A8A"));
        notHelpful.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        notHelpful.setPadding((int) (14 * dp), (int) (8 * dp), (int) (14 * dp), (int) (8 * dp));
        LinearLayout.LayoutParams nhLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        nhLp.setMarginStart((int) (6 * dp));
        notHelpful.setLayoutParams(nhLp);
        notHelpful.setClickable(true);
        notHelpful.setOnClickListener(v -> dismissSuggestionCard(card));
        btnRow.addView(notHelpful);

        expand.addView(btnRow);
        col.addView(expand);
        card.addView(col);

        headerRow.setOnClickListener(v -> toggleSuggestionCard(card, expand, chevron));
        return card;
    }

    /** Accordion toggle — smooth height/fade via AutoTransition, one open at a time. */
    private void toggleSuggestionCard(com.google.android.material.card.MaterialCardView card,
                                      LinearLayout expand, ImageView chevron) {
        boolean opening = expand.getVisibility() != View.VISIBLE;

        android.transition.AutoTransition t = new android.transition.AutoTransition();
        t.setDuration(260);
        t.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        android.transition.TransitionManager.beginDelayedTransition(suggestionList, t);

        if (opening && expandedSuggestionCard != null && expandedSuggestionCard != card) {
            if (expandedSuggestionExpand != null) expandedSuggestionExpand.setVisibility(View.GONE);
            if (expandedSuggestionChevron != null) expandedSuggestionChevron.animate().rotation(0f).setDuration(200).start();
            expandedSuggestionCard.setStrokeColor(Color.parseColor("#1A2A2A"));
        }

        expand.setVisibility(opening ? View.VISIBLE : View.GONE);
        chevron.animate().rotation(opening ? 180f : 0f).setDuration(220).start();
        card.setStrokeColor(Color.parseColor(opening ? "#274545" : "#1A2A2A"));

        if (opening) {
            expandedSuggestionCard = card;
            expandedSuggestionExpand = expand;
            expandedSuggestionChevron = chevron;
        } else {
            expandedSuggestionCard = null;
            expandedSuggestionExpand = null;
            expandedSuggestionChevron = null;
        }
    }

    /**
     * "Not helpful" → remove the card and slot in a backup suggestion in its
     * place (smoothly). When the backup pool runs low, quietly ask the backend
     * for more, generated around everything we've already shown.
     */
    private void dismissSuggestionCard(com.google.android.material.card.MaterialCardView card) {
        if (suggestionList == null) return;
        if (expandedSuggestionCard == card) {
            expandedSuggestionCard = null;
            expandedSuggestionExpand = null;
            expandedSuggestionChevron = null;
        }
        float dp = getResources().getDisplayMetrics().density;
        int idx = suggestionList.indexOfChild(card);
        AiSuggestion next = suggestionBackup.isEmpty() ? null : suggestionBackup.remove(0);

        android.transition.AutoTransition t = new android.transition.AutoTransition();
        t.setDuration(260);
        t.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        android.transition.TransitionManager.beginDelayedTransition(suggestionList, t);

        suggestionList.removeView(card);
        if (next != null && idx >= 0) {
            suggestionList.addView(buildSuggestionCard(next, dp), idx);
        }

        Utilities.toast(requireContext(), next != null ? "Swapped in a fresh one." : "Got it — I'll suggest better ones.");

        if (suggestionBackup.size() <= 1) refillSuggestions();
    }

    /** Background top-up of the backup pool, excluding everything already shown. */
    private void refillSuggestions() {
        if (refillInProgress) return;
        Context ctx = getContext();
        if (ctx == null) return;
        String token = TokenManager.getInstance(ctx).getToken();
        if (token == null) return;
        refillInProgress = true;

        String exclude = android.text.TextUtils.join("||", knownQuestions);
        String url = ApiConfig.BASE_URL + "/api/chat/suggestions?refresh=true&exclude="
                + android.net.Uri.encode(exclude);

        StringRequest request = new StringRequest(Request.Method.GET, url,
                response -> {
                    refillInProgress = false;
                    if (!isAdded()) return;
                    try {
                        JSONArray arr = new JSONObject(response).optJSONArray("suggestions");
                        java.util.List<AiSuggestion> fresh = parseSuggestions(arr);
                        for (AiSuggestion s : fresh) {
                            String key = s.q.toLowerCase();
                            if (!knownQuestions.contains(key)) {
                                knownQuestions.add(key);
                                suggestionBackup.add(s);
                            }
                        }
                        if (arr != null) {
                            requireContext().getSharedPreferences("chat_suggestions", Context.MODE_PRIVATE)
                                    .edit().putString("items", arr.toString()).apply();
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "refillSuggestions parse: " + e.getMessage());
                    }
                },
                error -> {
                    refillInProgress = false;
                    Log.w(TAG, "refillSuggestions: " + error.toString());
                }
        ) {
            @Override
            public java.util.Map<String, String> getHeaders() {
                java.util.Map<String, String> h = new java.util.HashMap<>();
                h.put("Authorization", "Bearer " + token);
                return h;
            }
        };
        request.setRetryPolicy(new com.android.volley.DefaultRetryPolicy(15000, 1, 1f));
        Volley.newRequestQueue(ctx).add(request);
    }

    /**
     * Calm entrance for the non-suggestion blocks (greeting, subtitle, data
     * line). Suggestion cards animate in buildSuggestionRows; the nudge reveals
     * on its own delay.
     */
    private void animateEmptyStateIn() {
        if (welcomeContainer == null) return;
        float startY = 14 * getResources().getDisplayMetrics().density;
        int shown = 0;
        for (int i = 0; i < welcomeContainer.getChildCount(); i++) {
            View child = welcomeContainer.getChildAt(i);
            if (child == suggestionList || child == hintBox) continue;
            if (child.getVisibility() != View.VISIBLE) continue;
            child.animate().cancel();
            child.setAlpha(0f);
            child.setTranslationY(startY);
            child.animate().alpha(1f).translationY(0f)
                    .setStartDelay(shown * 55L)
                    .setDuration(320)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f))
                    .start();
            shown++;
        }
    }

    /**
     * Fetch AI suggestions + the progressive data nudge from the backend.
     * Paints cached/defaults first so the screen is never empty, then updates.
     */
    private void fetchChatSuggestions() {
        setSuggestions(cachedOrDefaultSuggestions());

        Context ctx = getContext();
        if (ctx == null) return;
        String token = TokenManager.getInstance(ctx).getToken();
        if (token == null) return;

        String url = ApiConfig.BASE_URL + "/api/chat/suggestions";
        StringRequest request = new StringRequest(Request.Method.GET, url,
                response -> {
                    if (!isAdded()) return;
                    try {
                        JSONObject json = new JSONObject(response);
                        JSONArray arr = json.optJSONArray("suggestions");
                        java.util.List<AiSuggestion> items = parseSuggestions(arr);
                        if (items.size() >= 3) {
                            setSuggestions(items);
                            requireContext().getSharedPreferences("chat_suggestions", Context.MODE_PRIVATE)
                                    .edit().putString("items", arr.toString()).apply();
                        }
                        JSONObject nudge = json.optJSONObject("nudge");
                        if (nudge != null) {
                            applyNudge(nudge.optString("text", ""), nudge.optString("type", ""), nudge.optString("action", ""));
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "fetchChatSuggestions parse: " + e.getMessage());
                    }
                },
                error -> Log.w(TAG, "fetchChatSuggestions: " + error.toString())
        ) {
            @Override
            public java.util.Map<String, String> getHeaders() {
                java.util.Map<String, String> h = new java.util.HashMap<>();
                h.put("Authorization", "Bearer " + token);
                return h;
            }
        };
        request.setRetryPolicy(new com.android.volley.DefaultRetryPolicy(15000, 1, 1f));
        Volley.newRequestQueue(ctx).add(request);
    }

    /**
     * Set the nudge content, but only reveal it after the 3s delay has passed
     * (so the suggestions land first). If the delay already elapsed, reveal now.
     */
    private void applyNudge(String text, String type, String action) {
        nudgeAction = action != null ? action : "";
        if (text == null || text.trim().isEmpty()) {
            nudgeHasContent = false;
            if (hintBox != null) hintBox.setVisibility(View.GONE);
            populateHealthDataContext();   // show "Working from your …" if there is data
            return;
        }
        nudgeHasContent = true;
        if (healthDataContext != null) healthDataContext.setVisibility(View.GONE);
        if (hintText != null) hintText.setText(text);
        if (hintIcon != null) hintIcon.setImageResource(nudgeIcon(type));
        if (nudgeRevealed) revealNudge();   // delay already passed → show now
    }

    /** Hide the nudge and schedule it to appear ~3s after the suggestions. */
    private void scheduleNudgeReveal() {
        nudgeRevealed = false;
        if (hintBox != null) hintBox.setVisibility(View.GONE);
        if (welcomeContainer != null && nudgeRevealRunnable != null) {
            welcomeContainer.removeCallbacks(nudgeRevealRunnable);
        }
        nudgeRevealRunnable = () -> {
            if (!isAdded() || hintBox == null) return;
            nudgeRevealed = true;
            if (nudgeHasContent) revealNudge();
        };
        if (welcomeContainer != null) welcomeContainer.postDelayed(nudgeRevealRunnable, 3000L);
    }

    /** Fade the nudge card up into view. */
    private void revealNudge() {
        if (hintBox == null || hintBox.getVisibility() == View.VISIBLE) return;
        float dp = getResources().getDisplayMetrics().density;
        hintBox.setAlpha(0f);
        hintBox.setTranslationY(12 * dp);
        hintBox.setVisibility(View.VISIBLE);
        hintBox.animate().alpha(1f).translationY(0f)
                .setDuration(340)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f))
                .start();
    }

    private int nudgeIcon(String type) {
        if (type == null) return R.drawable.ic_lab_profile;
        switch (type) {
            case "add_measurements": return R.drawable.ic_add_chart;
            case "add_symptoms":     return R.drawable.ic_info_outline;
            case "add_family":       return R.drawable.ic_person;
            case "freshness":        return R.drawable.ic_info_outline;
            default:                 return R.drawable.ic_lab_profile; // add_reports / add_meds
        }
    }

    /**
     * Show the in-input profile chip only for accounts that actually have
     * family members. It lives in the input bar, so it stays available even
     * mid-conversation (that is the point — switch who you are asking about).
     */
    private void updateDependentSelectorVisibility() {
        if (inputProfileChip == null) return;
        // Only offer the switch for family accounts, and only while starting a
        // new chat. Once a chat is under way it is locked to that profile, so
        // the pill disappears.
        boolean show = !dependentsList.isEmpty() && isNewChatMode;
        inputProfileChip.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    /** Show a polished bottom-sheet to pick self or a dependent */
    private void showDependentSelectionDropdown() {
        float dp = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, getResources().getDisplayMetrics());

        // Same Material BottomSheet pattern as the model picker, so both pickers in
        // the chat UI feel identical (drag-to-dismiss, system-rounded surface, scrim).
        com.google.android.material.bottomsheet.BottomSheetDialog dialog =
                new com.google.android.material.bottomsheet.BottomSheetDialog(requireContext());

        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(0, (int)(8 * dp), 0, (int)(24 * dp));
        // Background lives on the BottomSheet's own surface (set in onShow) so we
        // don't end up with two stacked rounded layers fighting at the corners.

        container.addView(buildDrawerHandle(dp));
        container.addView(buildDrawerTitle("Who is this chat for?", dp));

        // "Myself" — one clean row (same style as the model picker).
        container.addView(buildPickerRow(R.drawable.ic_person, Color.parseColor("#008b8b"),
                "Myself", selectedDependentId == null, false, dp, v -> {
                    selectedDependentId = null;
                    selectedDependentName = "Myself";
                    updateDependentChip();
                    dialog.dismiss();
                }));

        // Dependents
        for (int i = 0; i < dependentsList.size(); i++) {
            JSONObject dep = dependentsList.get(i);
            final String depId = dep.optString("_id", "");
            final String depName = dep.optString("name", "Dependent");
            boolean isSelected = depId.equals(selectedDependentId);
            addSheetDivider(container, dp);
            container.addView(buildPickerRow(R.drawable.ic_person, Color.parseColor("#008b8b"),
                    depName, isSelected, false, dp, v -> {
                        selectedDependentId = depId;
                        selectedDependentName = depName;
                        updateDependentChip();
                        dialog.dismiss();
                    }));
        }

        dialog.setContentView(container);

        // Round the BottomSheet's own surface — single layer, single set of corners,
        // so there's no visible split where our content meets the sheet edge.
        dialog.setOnShowListener(d -> {
            View sheet = dialog.findViewById(
                    com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) {
                GradientDrawable sheetBg = new GradientDrawable();
                sheetBg.setColor(Color.parseColor("#111111"));
                sheetBg.setCornerRadii(new float[]{
                        24 * dp, 24 * dp,   // top-left
                        24 * dp, 24 * dp,   // top-right
                        0, 0,               // bottom-right
                        0, 0                // bottom-left
                });
                sheet.setBackground(sheetBg);
            }
        });

        dialog.show();
    }

    private void showChatHistoryPanel() {
        // Reset the search each time the panel opens.
        if (chatSearchInput != null) chatSearchInput.setText("");
        // Open the panel first so the standard loading overlay sits on top of it
        // (top z-index within the panel window), then fetch sessions.
        chatHistoryPanel.show();
        fetchChatSessions();
    }

    /** Filters the loaded sessions by title / last message against the search query. */
    private void filterChatSessions(String query) {
        if (chatSessionsAdapter == null) return;
        String q = query == null ? "" : query.trim().toLowerCase();
        if (q.isEmpty()) {
            chatSessionsAdapter.updateSessions(allChatSessions);
            updateEmptyState(allChatSessions.isEmpty());
            return;
        }
        List<ChatSession> filtered = new ArrayList<>();
        for (ChatSession s : allChatSessions) {
            String title = s.getTitle() == null ? "" : s.getTitle().toLowerCase();
            String last = s.getLastMessage() == null ? "" : s.getLastMessage().toLowerCase();
            if (title.contains(q) || last.contains(q)) filtered.add(s);
        }
        chatSessionsAdapter.updateSessions(filtered);
        updateEmptyState(filtered.isEmpty());
    }

    private void fetchChatSessions() {
        Context context = getContext();
        if (context == null) return; // Fragment detached, skip operation safely

        String url = ApiConfig.BASE_URL + "/api/chat/sessions";
        TokenManager tokenManager = TokenManager.getInstance(context);

        // Consistent app-wide loading dialog (SimpleProgress) on the topmost z-index.
        // When the panel is open it renders on the panel window; otherwise on the activity.
        SimpleProgress historyProgress = (chatHistoryPanel != null && chatHistoryPanel.isShowing())
                ? SimpleProgress.show(chatHistoryPanel, "Loading chat history...")
                : SimpleProgress.show(requireActivity(), "Loading chat history...");

        StringRequest request = new StringRequest(Request.Method.GET, url,
                response -> {
                    ApiConfig.logRestCall(url, true, "Chat sessions fetched");
                    historyProgress.hide();
                    try {
                        JSONArray sessionsArray = new JSONArray(response);
                        List<ChatSession> sessions = new ArrayList<>();

                        for (int i = 0; i < sessionsArray.length(); i++) {
                            JSONObject sessionObj = sessionsArray.getJSONObject(i);

                            ChatSession session = new ChatSession(
                                    sessionObj.getString("sessionId"),
                                    sessionObj.getString("title"),
                                    sessionObj.optString("lastMessage", ""),
                                    sessionObj.optInt("messageCount", 0),
                                    parseTimestamp(sessionObj.optString("timestamp", "")),
                                    userProfile != null ? userProfile.getId() : 0
                            );
                            session.setModelType(sessionObj.optString("modelType", "auto"));
                            String depId = sessionObj.optString("dependentId", null);
                            if (depId != null && !depId.equals("null") && !depId.isEmpty()) {
                                session.setDependentId(depId);
                            }
                            sessions.add(session);
                        }

                        // Keep the full list; the search box filters a copy of it.
                        allChatSessions.clear();
                        allChatSessions.addAll(sessions);
                        filterChatSessions(chatSearchInput != null
                                ? chatSearchInput.getText().toString() : "");

                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing sessions response", e);
                        Utilities.toast(requireContext(), "Failed to load chat history");
                        updateEmptyState(true);
                    }
                },
                error -> {
                    ApiConfig.logRestCall(url, false, error.toString());
                    historyProgress.hide();
                    Log.e(TAG, "Error fetching sessions: " + error.toString());
                    Utilities.toast(requireContext(), "Failed to load chat history");
                    updateEmptyState(true);
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + tokenManager.getToken());
                return headers;
            }
        };

        RequestQueue queue = Volley.newRequestQueue(context);
        queue.add(request);
    }

    // Update empty state visibility
    private void updateEmptyState(boolean isEmpty) {
        if (emptyStateView != null) {
            emptyStateView.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        }
        if (chatSessionsRecycler != null) {
            chatSessionsRecycler.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        }
    }

    private void showDeleteSessionConfirmDialog(ChatSession session, int position) {
        DialogUtils.showConfirmDialog(requireContext(),
                "Delete Chat Session",
                "Are you sure you want to delete this chat session? This action cannot be undone.",
                "Delete", "Cancel", true,
                () -> deleteSession(session.getSessionId(), position));
    }
    // Show thinking animation — rotating context-aware messages so the user sees Richie working
    private void showThinkingAnimation() {
        if (thinkingPosition >= 0) return;

        // Build context-aware message pool, shuffle for variety
        thinkingBaseMessages = buildThinkingMessages(); // generic, ordered — do NOT shuffle
        thinkingDotStep = 0;
        thinkingMsgIdx  = 0;
        thinkingTick    = 0;

        // Show first message immediately with a single dot
        thinkingMsg = new ChatMessage(thinkingBaseMessages.get(0) + DOT_STATES[0], true);
        thinkingMsg.setSessionId(sessionId);
        thinkingMsg.setThinking(true);   // adapter hides the action row for this bubble
        chatAdapter.addMessage(thinkingMsg);
        thinkingPosition = chatAdapter.getItemCount() - 1;
        // Hard snap to the bottom. A smooth scroll here fights stackFromEnd and
        // parks the placeholder at the top of the viewport (see scrollToBottom).
        final int pinPos = thinkingPosition;
        chatRecycler.scrollToPosition(pinPos);
        chatRecycler.post(() -> chatRecycler.scrollToPosition(pinPos));

        // Spin the logo icon in the thinking bubble
        new Handler().postDelayed(() -> {
            if (!isAdded() || thinkingPosition < 0) return;
            RecyclerView.ViewHolder vh = chatRecycler.findViewHolderForAdapterPosition(thinkingPosition);
            if (vh != null) {
                ImageView aiIcon = vh.itemView.findViewById(R.id.ai_icon);
                if (aiIcon != null) {
                    iconAnimator = ObjectAnimator.ofFloat(aiIcon, View.ROTATION, 0f, 360f);
                    iconAnimator.setDuration(2000);
                    iconAnimator.setRepeatCount(ObjectAnimator.INFINITE);
                    iconAnimator.setInterpolator(new LinearInterpolator());
                    iconAnimator.start();
                }
            }
        }, 100);

        // Single handler drives BOTH dot animation AND message cycling.
        // Every 400 ms: advance dot (. → .. → ... → .... → .....)
        // Every 5 dot steps (2000 ms): move to the next message in pool.
        //
        // We DO NOT call notifyItemChanged on every tick — that re-binds the
        // whole row, runs the change-animator, fights smooth-scroll and makes
        // the bubble flicker / "stick" at the top. Instead we update the text
        // directly on the bound TextView. The model is still updated so any
        // re-bind from RecyclerView recycling shows the latest state.
        thinkingCycleHandler = new Handler();
        Runnable ticker = new Runnable() {
            @Override public void run() {
                if (!isAdded() || thinkingPosition < 0 || thinkingMsg == null
                        || thinkingBaseMessages == null) return;

                // Dots cycle every step; the message escalates to the next (more
                // patient) line every ~10s, then holds on the last one.
                thinkingDotStep = (thinkingDotStep + 1) % DOT_STATES.length;
                thinkingTick++;
                thinkingMsgIdx = Math.min(thinkingTick / TICKS_PER_MSG, thinkingBaseMessages.size() - 1);

                String txt = thinkingBaseMessages.get(thinkingMsgIdx) + DOT_STATES[thinkingDotStep];
                thinkingMsg.setMessage(txt);

                // Direct view update — no re-bind, no flicker, no scroll fight.
                RecyclerView.ViewHolder vh =
                        chatRecycler.findViewHolderForAdapterPosition(thinkingPosition);
                if (vh != null) {
                    TextView msgView = vh.itemView.findViewById(R.id.message_text);
                    if (msgView != null) msgView.setText(txt);
                }
                // The placeholder text changes length each tick and can re-wrap
                // on narrow screens, nudging the list. Re-pin to the bottom, but
                // ONLY if the user is already there — never hijack a manual scroll-up.
                // (Previously the "bubble off-screen" branch snapped unconditionally,
                // which yanked a user who had scrolled up to read history.)
                if (isChatNearBottom()) chatRecycler.scrollToPosition(thinkingPosition);

                if (thinkingCycleHandler != null) {
                    thinkingCycleHandler.postDelayed(this, DOT_INTERVAL_MS);
                }
            }
        };
        thinkingCycleHandler.postDelayed(ticker, DOT_INTERVAL_MS);
    }

    /**
     * Generic, time-ordered "thinking" lines. Index 0 shows first; the tick loop
     * advances one step roughly every 10s and then holds on the last line. The
     * model's real reasoning isn't available before the reply (no streaming), so
     * these are intentionally generic — they just need to feel calm and smart.
     */
    private List<String> buildThinkingMessages() {
        List<String> pool = new ArrayList<>();
        pool.add("Thinking");
        pool.add("Still thinking");
        pool.add("Working through your data");
        pool.add("Almost there");
        pool.add("Just a moment more");
        return pool;
    }

    /**
     * BackPressHandler — called by MainActivity before its own back-press logic.
     * Priority order: chat-history panel → saved-chats panel.
     * Returns true (consumed) if a panel was open and has been dismissed.
     */
    @Override
    public boolean handleBackPress() {
        if (chatHistoryPanel != null && chatHistoryPanel.isShowing()) {
            chatHistoryPanel.dismiss();
            return true;
        }
        if (savedChatsPanel != null && savedChatsPanel.isShowing()) {
            savedChatsPanel.dismiss();
            return true;
        }
        return false;
    }

    // Stop the dot/message cycle and remove the thinking bubble
    private void hideThinkingAnimation() {
        if (thinkingCycleHandler != null) {
            thinkingCycleHandler.removeCallbacksAndMessages(null);
            thinkingCycleHandler = null;
        }
        thinkingBaseMessages = null;
        thinkingMsg          = null;
        thinkingDotStep      = 0;
        thinkingMsgIdx       = 0;
        thinkingTick         = 0;

        if (iconAnimator != null) {
            iconAnimator.cancel();
            iconAnimator = null;
        }
        if (thinkingPosition >= 0) {
            ((ChatAdapter) chatRecycler.getAdapter()).removeMessageAt(thinkingPosition);
            thinkingPosition = -1;
        }
    }

    private void deleteSession(String sessionId, int position) {
        String url = ApiConfig.BASE_URL + "/api/chat/sessions/" + sessionId;
        Context context = getContext();
        if (context == null) return; // Fragment detached, skip operation safely
        TokenManager tokenManager = TokenManager.getInstance(context);

        SimpleProgress progress = (chatHistoryPanel != null && chatHistoryPanel.isShowing())
                ? SimpleProgress.show(chatHistoryPanel, "Deleting chat...")
                : SimpleProgress.show(requireActivity(), "Deleting chat...");

        StringRequest request = new StringRequest(Request.Method.DELETE, url,
                response -> {
                    ApiConfig.logRestCall(url, true, "Chat session deleted");
                    progress.hide();

                    // Remove from adapter
                    chatSessionsAdapter.removeSession(position);

                    // If the deleted chat was the one we remember for tab switches,
                    // forget it so we don't try to reopen a deleted session.
                    if (activeSession != null && sessionId != null
                            && sessionId.equals(activeSession.getSessionId())) {
                        clearActiveSession();
                    }

                    // Remove from local database if exists

                    // Show success message
                    Utilities.toast(requireContext(), "Chat session deleted");

                    // Check if list is now empty
                    if (chatSessionsAdapter.getItemCount() == 0) {
                        updateEmptyState(true);
                    }
                },
                error -> {
                    ApiConfig.logRestCall(url, false, error.toString());
                    progress.hide();
                    Log.e(TAG, "Error deleting session: " + error.toString());
                    Utilities.toast(requireContext(), "Failed to delete chat session");
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + tokenManager.getToken());
                return headers;
            }
        };

        RequestQueue queue = Volley.newRequestQueue(context);
        queue.add(request);
    }

    private void showDeleteAllChatsConfirmDialog() {
        DialogUtils.showConfirmDialog(requireContext(),
                "Delete All Chats",
                "Are you sure you want to delete all chat sessions? Saved messages will be preserved. This action cannot be undone.",
                "Delete All", "Cancel", true,
                this::deleteAllChats);
    }

    private void deleteAllChats() {
        String url = ApiConfig.BASE_URL + "/api/chat/sessions/all";
        Context context = getContext();
        if (context == null) return;
        TokenManager tokenManager = TokenManager.getInstance(context);

        SimpleProgress progress = (chatHistoryPanel != null && chatHistoryPanel.isShowing())
                ? SimpleProgress.show(chatHistoryPanel, "Deleting all chats...")
                : SimpleProgress.show(requireActivity(), "Deleting all chats...");

        StringRequest request = new StringRequest(Request.Method.DELETE, url,
                response -> {
                    ApiConfig.logRestCall(url, true, "All chats deleted");
                    progress.hide();

                    // Clear the adapter
                    chatSessionsAdapter.clearAllSessions();

                    // Reset current session
                    sessionId = null;
                    clearActiveSession(); // all chats gone — nothing to reopen on tab switch
                    isNewChatMode = true;
                    chatAdapter.clear();

                    // Update UI
                    updateEmptyState(true);
                    headerTitle.setText("");  // new chat: header shows only the two icons
                    updateUsageBar(0, getMessageLimit());

                    Utilities.toast(requireContext(), "All chats deleted");
                },
                error -> {
                    ApiConfig.logRestCall(url, false, error.toString());
                    progress.hide();
                    Log.e(TAG, "Error deleting all chats: " + error.toString());
                    Utilities.toast(requireContext(), "Failed to delete all chats");
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + tokenManager.getToken());
                return headers;
            }
        };

        RequestQueue queue = Volley.newRequestQueue(context);
        queue.add(request);
    }

    private void loadSession(ChatSession session) {
        sessionId = session.getSessionId();
        setActiveSession(session); // remember it so tab switches reopen this chat
        isNewChatMode = false;
        isSessionLimitReached = false;

        messageInput.setEnabled(true);
        sendButton.setEnabled(true);
        messageInput.setHint("Ask anything about your health...");
        messageInput.setBackground(null);

        // Hide welcome state immediately before loading messages (also hides its
        // ScrollView wrapper so it can't overlay + block the chat list).
        setWelcomeVisibility(View.GONE);
        chatRecycler.setVisibility(View.VISIBLE);

        // Show dependent name in header if this is a dependent chat
        if (session.getDependentId() != null) {
            // Find the dependent name from our cached list
            String depName = null;
            for (JSONObject dep : dependentsList) {
                if (session.getDependentId().equals(dep.optString("_id"))) {
                    depName = dep.optString("name", null);
                    break;
                }
            }
            if (depName != null) {
                headerTitle.setText(depName + " — " + session.getTitle());
            } else {
                headerTitle.setText(session.getTitle());
            }
            selectedDependentId = session.getDependentId();
        } else {
            headerTitle.setText(session.getTitle());
            selectedDependentId = null;
        }

        // Update usage bar with this session's message count
        updateUsageBar(session.getMessageCount(), getMessageLimit());

        // Update pill button with this session's model
        if (session.getModelType() != null) {
            currentModel = session.getModelType();
            selectedModel = getModelDisplayName(session.getModelType());
            applyModelToUi(selectedModel);
        }

        chatAdapter.clear();
        fetchSessionMessages(sessionId);
    }

    private void createNewChatSession() {
        startNewChatWithCurrentModel();
    }

    private void fetchSessionMessages(String sessionId) {
        String url = ApiConfig.BASE_URL + "/api/chat/sessions/" + sessionId + "/messages";
        Context context = getContext();
        if (context == null) return;
        TokenManager tokenManager = TokenManager.getInstance(context);

        SimpleProgress progress = SimpleProgress.show(requireActivity(), "Loading chat...");

        StringRequest request = new StringRequest(Request.Method.GET, url,
                response -> {
                    ApiConfig.logRestCall(url, true, "Chat messages fetched");
                    progress.hide();
                    try {
                        JSONArray messagesArray = new JSONArray(response);
                        renderSessionMessages(messagesArray, sessionId);
                        scrollToBottom();
                        // If the newest message is still the user's (no reply yet), the
                        // reply may be finishing on the server after we left and came back.
                        // Poll briefly to pull it in once it's saved.
                        startReplyRecoveryPollIfNeeded(sessionId);
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing messages: " + e.getMessage());
                        Utilities.toast(requireContext(), "Error loading chat messages");
                    }
                },
                error -> {
                    ApiConfig.logRestCall(url, false, error.toString());
                    progress.hide();
                    Log.e(TAG, "Error fetching messages: " + error.toString());
                    Utilities.toast(requireContext(), "Failed to load chat messages");
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + tokenManager.getToken());
                return headers;
            }
        };

        RequestQueue queue = Volley.newRequestQueue(context);
        queue.add(request);
    }

    /**
     * Rebuild the chat list from a server message array. Extracted from
     * fetchSessionMessages so the reply-recovery poll renders messages identically.
     */
    private void renderSessionMessages(JSONArray messagesArray, String forSessionId) throws JSONException {
        chatAdapter.clear();
        for (int i = 0; i < messagesArray.length(); i++) {
            JSONObject messageObj = messagesArray.getJSONObject(i);

            ChatMessage message = new ChatMessage(
                    messageObj.getString("message"),
                    messageObj.getBoolean("isFromAI"));

            message.setMessageId(messageObj.getString("_id"));
            message.setSessionId(forSessionId);

            // Persisted "logged" confirmation boxes render distinctly.
            if ("log".equals(messageObj.optString("type"))) {
                message.setLogEntry(true);
            }
            if (messageObj.has("timestamp")) {
                message.setTimestamp(parseTimestamp(messageObj.getString("timestamp")));
            }
            if (messageObj.has("isSaved")) {
                message.setSaved(messageObj.getBoolean("isSaved"));
            }
            // Restore the persisted "memory saved" icon (server keeps it true only
            // while a memory linked to this turn still exists).
            if (messageObj.optBoolean("memorySaved", false)) {
                message.setMemoryAdded(true);
            }
            // Restore the persisted "Thinking" trace so the collapsible reasoning row
            // survives reopening a past session.
            String savedReasoning = messageObj.optString("reasoning", "");
            if (!savedReasoning.trim().isEmpty()) {
                message.setReasoning(savedReasoning);
            }

            chatAdapter.addMessage(message);
        }
    }

    /**
     * If the last loaded message is the user's (no reply yet), a reply may still be
     * finishing on the server — most often because the user sent it, switched tabs,
     * and returned before it was saved. Poll a few times to pull it in, then stop.
     * No-op if the newest message is already an AI reply.
     */
    private void startReplyRecoveryPollIfNeeded(String pollSessionId) {
        cancelReplyRecoveryPoll();
        if (!isAdded() || chatAdapter == null || pollSessionId == null) return;
        if (!lastMessageAwaitingReply()) return;
        replyPollAttempts = 0;
        replyPollHandler = new Handler();
        scheduleReplyPoll(pollSessionId);
    }

    /** True when the newest message in the list is a plain user message (no reply yet). */
    private boolean lastMessageAwaitingReply() {
        if (chatAdapter == null) return false;
        List<ChatMessage> msgs = chatAdapter.getMessages();
        if (msgs == null || msgs.isEmpty()) return false;
        ChatMessage last = msgs.get(msgs.size() - 1);
        return !last.isFromAI() && !last.isThinking();
    }

    private void scheduleReplyPoll(final String pollSessionId) {
        if (replyPollHandler == null) return;
        replyPollHandler.postDelayed(() -> {
            if (!isAdded() || replyPollHandler == null) return;
            // Session changed underneath us (new chat / opened another) — stop quietly.
            if (sessionId == null || !sessionId.equals(pollSessionId)) { cancelReplyRecoveryPoll(); return; }
            if (!lastMessageAwaitingReply()) { cancelReplyRecoveryPoll(); return; }
            if (++replyPollAttempts > REPLY_POLL_MAX_ATTEMPTS) { cancelReplyRecoveryPoll(); return; }
            pollReplyOnce(pollSessionId);
        }, REPLY_POLL_INTERVAL_MS);
    }

    /** One silent fetch of the session's messages; re-renders only once a reply arrives. */
    private void pollReplyOnce(final String pollSessionId) {
        Context context = (appContext != null) ? appContext : getContext();
        if (context == null) return;
        final String url = ApiConfig.BASE_URL + "/api/chat/sessions/" + pollSessionId + "/messages";
        final TokenManager tokenManager = TokenManager.getInstance(context);
        StringRequest request = new StringRequest(Request.Method.GET, url,
                response -> {
                    if (!isAdded()) return;
                    if (sessionId == null || !sessionId.equals(pollSessionId)) { cancelReplyRecoveryPoll(); return; }
                    try {
                        JSONArray arr = new JSONArray(response);
                        // Only disturb the view once the reply is actually present, so we
                        // never clobber the user's scroll (or a reveal) for nothing.
                        boolean replyArrived = arr.length() > 0
                                && arr.getJSONObject(arr.length() - 1).optBoolean("isFromAI", false);
                        if (replyArrived) {
                            renderSessionMessages(arr, pollSessionId);
                            scrollToBottom();
                            cancelReplyRecoveryPoll();
                        } else {
                            scheduleReplyPoll(pollSessionId); // not ready yet — try again
                        }
                    } catch (JSONException e) {
                        scheduleReplyPoll(pollSessionId);
                    }
                },
                error -> {
                    if (isAdded()) scheduleReplyPoll(pollSessionId); // transient — retry within budget
                }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + tokenManager.getToken());
                return headers;
            }
        };
        Volley.newRequestQueue(context).add(request);
    }

    private void cancelReplyRecoveryPoll() {
        if (replyPollHandler != null) {
            replyPollHandler.removeCallbacksAndMessages(null);
            replyPollHandler = null;
        }
    }

    private void setupSavedChatsPanel() {
        savedChatsPanel = new Dialog(requireContext());
        savedChatsPanel.requestWindowFeature(Window.FEATURE_NO_TITLE);
        savedChatsPanel.setContentView(R.layout.layout_saved_chats_panel);

        savedChatsPanel.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        WindowManager.LayoutParams params = savedChatsPanel.getWindow().getAttributes();
        params.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.85);
        params.height = WindowManager.LayoutParams.MATCH_PARENT;
        params.gravity = Gravity.END;

        savedChatsPanel.getWindow().setAttributes(params);
        savedChatsPanel.getWindow().getAttributes().windowAnimations = R.style.DialogAnimationSlideRight;

        // Close button
        savedChatsPanel.findViewById(R.id.close_panel_button).setOnClickListener(v -> savedChatsPanel.dismiss());

        RecyclerView savedChatsRecycler = savedChatsPanel.findViewById(R.id.saved_chats_recycler);
        savedChatAdapter = new SavedChatAdapter(requireContext());

        // Set up action listener for delete
        savedChatAdapter.setActionListener(message -> {
            // Unsave the message
            toggleMessageSaved(message);

            // Remove from adapter
            savedChatAdapter.removeMessage(message.getMessageId());

            // Update UI counter
            fetchSavedMessagesCount();

            // Update empty state visibility
            updateSavedChatsEmptyState();
        });

        savedChatsRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        savedChatsRecycler.setAdapter(savedChatAdapter);
    }

    private void updateSavedChatsEmptyState() {
        if (savedChatsPanel != null) {
            View emptyState = savedChatsPanel.findViewById(R.id.empty_state);
            if (savedChatAdapter.getItemCount() == 0) {
                emptyState.setVisibility(View.VISIBLE);
            } else {
                emptyState.setVisibility(View.GONE);
            }
        }
    }

    private void showInitialLoading() {
        // Works like native ProgressBar - overlay on top without disturbing the view
        initialProgress = SimpleProgress.show(getView(), "AI Assistant is getting ready...");
    }

    private void hideInitialLoadingAfterDelay() {
        new android.os.Handler().postDelayed(() -> {
            updateLoadingMessage("Loading your health profile...");
        }, 800);

        new android.os.Handler().postDelayed(() -> {
            updateLoadingMessage("Connecting to Richie...");
        }, 1800);

        new android.os.Handler().postDelayed(() -> {
            if (initialProgress != null) {
                initialProgress.hide();
                initialProgress = null;
            }
        }, 2800);
    }

    private void updateLoadingMessage(String message) {
        if (initialProgress != null) {
            initialProgress.setMessage(message);
        }
    }

    private void setupHistoryToggle() {
        updateHistoryButtonState();

        keepHistoryButton.setOnClickListener(v -> {
            isHistoryKeepingEnabled = !isHistoryKeepingEnabled;
            updateHistoryButtonState();

            String message = isHistoryKeepingEnabled
                    ? "Chat history will be saved"
                    : "Chat history is disabled";
            Utilities.toast(requireContext(), message);
        });
    }

    private void updateHistoryButtonState() {
        int activeColor = ContextCompat.getColor(requireContext(), R.color.teal_200);
        int inactiveColor = ContextCompat.getColor(requireContext(), R.color.gray_inactive);

        keepHistoryButton.setColorFilter(
                isHistoryKeepingEnabled ? activeColor : inactiveColor,
                android.graphics.PorterDuff.Mode.SRC_IN
        );
    }

    private void setupRecyclerView() {
        chatAdapter = new ChatAdapter(requireContext());
        // Restore the user's saved chat-size preference, if any.
        chatAdapter.setMessageTextSizeSp(getSavedChatTextSizeSp());
        chatAdapter.setSavedListener(message -> {
            if (message.getMessageId() != null) {
                toggleMessageSaved(message);
            } else {
                Log.e(TAG, "Cannot save message: messageId is null");
                Utilities.toast(requireContext(), "Cannot save this message");
            }
        });
        chatAdapter.setForkListener((message, position) -> beginForkFromMessage(position));
        chatAdapter.setForkContextClickListener(this::showForkedChatPreviewDialog);
        chatAdapter.setHealthCardListener(this::saveHealthCard);
        chatAdapter.setMemoryClickListener(this::showMemorySavedNote);

        // Toggle welcome screen based on adapter content
        chatAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override public void onChanged() { toggleWelcomeState(); }
            @Override public void onItemRangeInserted(int p, int c) { toggleWelcomeState(); }
            @Override public void onItemRangeRemoved(int p, int c) { toggleWelcomeState(); }
        });

        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        // stackFromEnd=false: the list is top-anchored, so a new reply (and its
        // typewriter reveal) is appended BELOW the current view without pushing
        // earlier content up. The view only moves when the user scrolls, or when we
        // explicitly scrollToBottom() on their own send. This is the fix for the
        // "chat scrolls up when the reply arrives" complaint.
        layoutManager.setStackFromEnd(false);
        chatRecycler.setLayoutManager(layoutManager);
        chatRecycler.setAdapter(chatAdapter);

        // The typewriter reveal calls setText() every ~22ms, and each setText
        // relayouts the list — under stackFromEnd that fights a manual scroll and
        // makes it "jump" while the AI is typing. The moment the user grabs the
        // list, finish the reveal (show full text at once) so their scroll is free.
        chatRecycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView rv, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING && chatAdapter != null) {
                    chatAdapter.completeActiveTypewriter();
                }
            }
        });

        // Kill change-animations on the chat recycler. The thinking-bubble
        // ticker mutates its TextView ~every 400 ms; the default animator
        // would fade-cross those updates and fight scroll-to-bottom.
        if (chatRecycler.getItemAnimator() instanceof androidx.recyclerview.widget.SimpleItemAnimator) {
            ((androidx.recyclerview.widget.SimpleItemAnimator) chatRecycler.getItemAnimator())
                    .setSupportsChangeAnimations(false);
        }
    }

    /** Show/hide the welcome/empty state AND its ScrollView wrapper together. The
     *  wrapper is full-size and sits over the chat list, so if it's left visible
     *  while messages are shown it swallows every touch/scroll on the bubbles. Always
     *  toggle both through here. */
    private void setWelcomeVisibility(int visibility) {
        if (welcomeContainer != null) welcomeContainer.setVisibility(visibility);
        if (welcomeScroll != null) welcomeScroll.setVisibility(visibility);
    }

    /** Show welcome cards when chat is empty, hide when messages exist */
    private void toggleWelcomeState() {
        if (welcomeContainer == null) return;
        boolean isEmpty = chatAdapter.getItemCount() == 0;
        boolean welcomeShown = welcomeContainer.getVisibility() == View.VISIBLE;

        if (isEmpty && !welcomeShown) {
            // Back to a fresh chat — show the welcome instantly, logo spinning.
            welcomeContainer.animate().cancel();
            welcomeTransitioning = false;
            welcomeContainer.setAlpha(1f);
            setWelcomeVisibility(View.VISIBLE);
            chatRecycler.setVisibility(View.GONE);
            startWelcomeLogoSpin();
        } else if (!isEmpty && welcomeShown) {
            // First message just landed — cross-fade the (spinning) salutation
            // logo out as the chat fades in. The first AI bubble spins the same
            // ic_launcher, so the logo reads as "becoming" the chat's icon.
            if (!welcomeTransitioning) crossfadeWelcomeToChat();
        } else {
            // No boundary change — keep the two views in sync without animating.
            setWelcomeVisibility(isEmpty ? View.VISIBLE : View.GONE);
            chatRecycler.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        }

        // Profile chip lives in the input bar and stays visible in both states
        // (for family accounts), so the "who" context persists into the chat.
        updateDependentSelectorVisibility();
    }

    /** Fade the welcome screen (with its spinning logo) out while the chat list
     *  fades in — the easy, low-risk version of the "logo becomes the first
     *  chat icon" transition (both are the same spinning ic_launcher). */
    private void crossfadeWelcomeToChat() {
        welcomeTransitioning = true;
        chatRecycler.setAlpha(0f);
        chatRecycler.setVisibility(View.VISIBLE);
        chatRecycler.animate().alpha(1f).setDuration(260).start();

        welcomeContainer.animate()
                .alpha(0f)
                .setDuration(220)
                .withEndAction(() -> {
                    setWelcomeVisibility(View.GONE);
                    welcomeContainer.setAlpha(1f); // reset for the next new chat
                    stopWelcomeLogoSpin();
                    welcomeTransitioning = false;
                })
                .start();
    }

    /** Infinite spin on the salutation logo — mirrors the login page, running
     *  only while the first message call is in flight (and the welcome is on
     *  screen). */
    private void startWelcomeLogoSpin() {
        if (welcomeLogo == null || welcomeContainer == null
                || welcomeContainer.getVisibility() != View.VISIBLE) return;
        stopWelcomeLogoSpin();
        welcomeLogoAnimator = ObjectAnimator.ofFloat(welcomeLogo, View.ROTATION, 0f, 360f);
        welcomeLogoAnimator.setDuration(2000);
        welcomeLogoAnimator.setRepeatCount(ObjectAnimator.INFINITE);
        welcomeLogoAnimator.setInterpolator(new LinearInterpolator());
        welcomeLogoAnimator.start();
    }

    private void stopWelcomeLogoSpin() {
        if (welcomeLogoAnimator != null) {
            welcomeLogoAnimator.cancel();
            welcomeLogoAnimator = null;
        }
        if (welcomeLogo != null) welcomeLogo.setRotation(0f);
    }

    /** Send a message from one of the quick-suggestion cards */
    private void sendQuickSuggestion(String text) {
        messageInput.setText(text);
        sendMessage(text);
    }

    private void initializeChatSession() {
        Log.d(TAG, "initializeChatSession: Starting fresh new chat mode");

        isNewChatMode = true;
        sessionId = null;
        isSessionLimitReached = false;

        updatePlanInfo();
        updateUsageBar(0, getMessageLimit());

        headerTitle.setText("");  // new chat: header shows only the two icons
        applyModelToUi(selectedModel);

        showWelcomeMessage();
    }

    private void showWelcomeMessage() {
        Log.d(TAG, "Showing welcome state");
        if (welcomeContainer == null) return;

        // Time-aware, name-led greeting.
        if (welcomeGreeting != null) {
            int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
            String part = hour < 12 ? "Good morning" : (hour < 17 ? "Good afternoon" : "Good evening");
            String firstName = "";
            if (userProfile != null) {
                String name = userProfile.getName();
                if (name != null && !name.isEmpty() && !name.equals("User")) {
                    firstName = name.trim().split(" ")[0];
                }
            }
            welcomeGreeting.setText(firstName.isEmpty() ? part : (part + ", " + firstName));
        }

        setWelcomeVisibility(View.VISIBLE);
        chatRecycler.setVisibility(View.GONE);
        startWelcomeLogoSpin();  // logo stays alive the whole time the welcome is up

        // Suggestions animate in first; the nudge is hidden now and revealed ~3s
        // later so the two moments don't compete.
        animateNextSuggestions = true;
        scheduleNudgeReveal();
        populateHealthDataContext();
        applyLocalNudge();

        // Animate once the container is laid out and visible.
        welcomeContainer.post(() -> {
            animateEmptyStateIn();
            fetchChatSuggestions();
        });
    }

    /** Local data counts from the analysis cache: {reports, meds, symptoms, measurements, have}. */
    private int[] readDataCounts() {
        int reports = 0, meds = 0, symptoms = 0, measurements = 0, have = 0;
        try {
            String raw = requireContext()
                    .getSharedPreferences("user_analysis_cache", android.content.Context.MODE_PRIVATE)
                    .getString("analysis_data", null);
            if (raw != null) {
                JSONObject analysis = new JSONObject(raw).optJSONObject("analysis");
                JSONObject dp = analysis != null ? analysis.optJSONObject("dataPoints") : null;
                if (dp != null) {
                    reports = dp.optInt("reports", 0);
                    meds = dp.optInt("medications", 0);
                    symptoms = dp.optInt("symptoms", 0);
                    measurements = dp.optInt("measurements", 0);
                    have = 1;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "readDataCounts: " + e.getMessage());
        }
        return new int[]{reports, meds, symptoms, measurements, have};
    }

    /**
     * Quiet "Working from your …" line when the user has data. The no-data case
     * is owned by the nudge (applyNudge / applyLocalNudge), so this only handles
     * the positive, grounding line.
     */
    private void populateHealthDataContext() {
        if (healthDataContext == null) return;
        int[] c = readDataCounts();
        int reports = c[0], meds = c[1], symptoms = c[2], measurements = c[3];
        if (reports + meds + symptoms + measurements > 0) {
            java.util.List<String> parts = new java.util.ArrayList<>();
            if (reports > 0) parts.add(reports + " report" + (reports > 1 ? "s" : ""));
            if (meds > 0) parts.add(meds + " med" + (meds > 1 ? "s" : ""));
            if (symptoms > 0) parts.add(symptoms + " symptom" + (symptoms > 1 ? "s" : ""));
            if (measurements > 0) parts.add(measurements + " reading" + (measurements > 1 ? "s" : ""));
            healthDataContext.setText("Working from your " + android.text.TextUtils.join(", ", parts));
            healthDataContext.setVisibility(View.VISIBLE);
        } else {
            healthDataContext.setVisibility(View.GONE);
        }
    }

    /** Immediate nudge from local counts, shown until the backend nudge lands. */
    private void applyLocalNudge() {
        int[] c = readDataCounts();
        int reports = c[0], meds = c[1], symptoms = c[2], measurements = c[3];
        boolean have = c[4] == 1;
        if (!have) {
            applyNudge("Add your reports and vitals so answers are made for you.", "add_reports", "health_data");
        } else if (reports == 0) {
            applyNudge("No lab reports yet. Add one so I can read your bloodwork.", "add_reports", "health_data");
        } else if (measurements == 0) {
            applyNudge("Add a vital like BP or weight for sharper insight.", "add_measurements", "health_data");
        } else if (symptoms == 0) {
            applyNudge("Log how you've been feeling and I'll help spot patterns.", "add_symptoms", "health_data");
        } else if (meds == 0) {
            applyNudge("On any medication? Add it so I can check interactions.", "add_meds", "health_data");
        } else {
            applyNudge("", "complete", "");
        }
    }

    /** Jump to the right screen for the current nudge (health data, or family). */
    private void openAddHealthData() {
        try {
            int target = "family".equals(nudgeAction) ? R.id.navigation_profile : R.id.navigation_tools;
            View nav = requireActivity().findViewById(R.id.bottom_navigation);
            if (nav instanceof com.google.android.material.bottomnavigation.BottomNavigationView) {
                ((com.google.android.material.bottomnavigation.BottomNavigationView) nav).setSelectedItemId(target);
            }
        } catch (Exception e) {
            Log.w(TAG, "openAddHealthData: " + e.getMessage());
        }
    }

    /** True when the chat is scrolled to (or near) the newest message. */
    private boolean isChatNearBottom() {
        RecyclerView.LayoutManager lm = chatRecycler.getLayoutManager();
        if (lm instanceof LinearLayoutManager) {
            int last = ((LinearLayoutManager) lm).findLastVisibleItemPosition();
            return last < 0 || last >= chatAdapter.getItemCount() - 2;
        }
        return true;
    }

    private void scrollToBottom() {
        chatRecycler.post(() -> {
            int itemCount = chatAdapter.getItemCount();
            if (itemCount <= 0) return;
            // Instant snap, not smoothScrollToPosition. With stackFromEnd=true a
            // smooth scroll to a just-inserted (not-yet-measured) row lands the
            // bubble at the TOP of the viewport instead of the bottom — that's the
            // "jumps to top while waiting" glitch. scrollToPosition bottom-aligns
            // reliably because the layout manager stacks from the end.
            chatRecycler.scrollToPosition(itemCount - 1);
        });
    }

    private void animateHeader() {
        headerTitle.setTranslationY(-50);
        headerTitle.setAlpha(0f);
        headerTitle.animate()
                .translationY(0)
                .alpha(1.0f)
                .setDuration(500)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        headerSubtitle.setTranslationY(-50);
        headerSubtitle.setAlpha(0f);
        headerSubtitle.animate()
                .translationY(0)
                .alpha(1.0f)
                .setStartDelay(100)
                .setDuration(500)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private void loadUserProfile() {
        userProfile = dbHelper.getUserProfile();

        // If no profile exists, use empty profile
        if (userProfile == null) {
            userProfile = new UserProfile();
            userProfile.setName("User");
        }
    }

    private void loadSuggestions() {
        List<Suggestion> suggestions = dbHelper.getFrequentSuggestions();
        suggestionChips.removeAllViews();

        for (Suggestion suggestion : suggestions) {
            Chip chip = new Chip(requireContext());
            chip.setText(suggestion.getText());
            chip.setClickable(true);
            chip.setCheckable(false);
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            chip.setGravity(Gravity.CENTER_VERTICAL);
            // Style the chip for better visibility
            chip.setChipBackgroundColorResource(R.color.teal_200);
            chip.setTextColor(getResources().getColor(android.R.color.black));

            chip.setOnClickListener(v -> {
                messageInput.setText(suggestion.getText());
                messageInput.setSelection(messageInput.getText().length());

                // Increment usage count
                dbHelper.incrementSuggestionUseCount(suggestion.getId());
            });

            suggestionChips.addView(chip);
        }

        // Add default suggestions if none exist
        if (suggestions.isEmpty()) {
            addDefaultSuggestions();
        }
    }

    private void addDefaultSuggestions() {
        String[] defaultSuggestions = {
                "How can I improve my workout routine?",
                "What's a good diet for weight loss?",
                "How many calories should I eat daily?",
                "Tips for better sleep",
                "How to stay motivated?"
        };

        for (String text : defaultSuggestions) {
            Suggestion suggestion = new Suggestion(0, text, "fitness", 0, false);
            long id = dbHelper.saveSuggestion(suggestion);

            Chip chip = new Chip(requireContext());
            chip.setText(text);
            chip.setClickable(true);
            chip.setCheckable(false);
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            chip.setGravity(Gravity.CENTER_VERTICAL);
            chip.setChipBackgroundColorResource(R.color.teal_200);
            chip.setTextColor(getResources().getColor(android.R.color.black));

            final long suggestionId = id;
            chip.setOnClickListener(v -> {
                messageInput.setText(text);
                messageInput.setSelection(messageInput.getText().length());

                // This properly sets the text size on the EditText
                messageInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);

                // Increment usage count
                dbHelper.incrementSuggestionUseCount(suggestionId);
            });

            suggestionChips.addView(chip);
        }
    }

    // Add this helper method for parsing timestamps
    private long parseTimestamp(String timestampStr) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault());
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = sdf.parse(timestampStr);
            return date != null ? date.getTime() : System.currentTimeMillis();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    private void sendMessage(String messageText) {
        if (isMonthlySessionLimitReached) {
            showSessionLimitReachedDialog(0, 0);
            return;
        }
        if (isSessionLimitReached) {
            showLimitReachedDialog();
            return;
        }

        // Any pending reply-recovery poll is now moot — this send drives the reply.
        cancelReplyRecoveryPoll();

        ChatMessage userMessage = new ChatMessage(messageText, false);
        if (sessionId != null) {
            userMessage.setSessionId(sessionId);
        }

        chatAdapter.addMessage(userMessage);
        messageInput.setText("");
        scrollToBottom();

        if (isNewChatMode) {
            sendButton.setEnabled(false);
            messageInput.setEnabled(false);
            showThinkingAnimation();
            createSessionThenSend(messageText);
        } else {
            sendMessageToBackend(messageText);
        }
    }

    // REPLACE YOUR sendMessageToBackend METHOD WITH THIS:
    private void sendMessageToBackend(String messageText) {
        // Check if session limit is reached before sending
        if (isSessionLimitReached) {
            showLimitReachedDialog();
            return;
        }

        // Disable input while waiting for response
        sendButton.setEnabled(false);
        messageInput.setEnabled(false);

        // Show thinking animation
        showThinkingAnimation();

        String url = ApiConfig.BASE_URL + "/api/chat/sessions/" + sessionId + "/messages";
        // Use the application context so this request completes even if the user leaves
        // the tab mid-send (getContext() would be null once detached, which previously
        // dropped the send entirely and the reply was never generated).
        Context context = (appContext != null) ? appContext : getContext();
        if (context == null) return; // truly detached with no app context — nothing safe to do
        TokenManager tokenManager = TokenManager.getInstance(context);

        JSONObject requestBody = new JSONObject();
        try {
            requestBody.put("message", messageText);
            requestBody.put("withAIResponse", true);
            // Note: modelType is per-session (set when session was created), not per-message.
            // Backend reads model from session.modelType, not from message body.
            // userContext is NOT sent — backend reads full health profile from MongoDB directly.
        } catch (JSONException e) {
            Log.e(TAG, "Error creating request body", e);
            sendButton.setEnabled(true);
            messageInput.setEnabled(true);
            hideThinkingAnimation();
            return;
        }

        StringRequest request = new StringRequest(Request.Method.POST, url,
                response -> {
                    ApiConfig.logRestCall(url, true, "Message sent");
                    // If the user navigated away, the reply is already saved server-side;
                    // it's recovered by the reply-recovery poll when they return. Touching
                    // detached views here would be pointless (and dialogs would crash).
                    if (!isAdded()) return;
                    hideThinkingAnimation();
                    Log.d(TAG, "Sending message : " + messageText);

                    try {
                        JSONObject responseObj = new JSONObject(response);

                        // Check if limit is reached
                        if (responseObj.has("isLimitReached") && responseObj.getBoolean("isLimitReached")) {
                            isSessionLimitReached = true;
                            updateUsageBar(messageLimit, messageLimit);

                            if (responseObj.has("aiMessage")) {
                                JSONObject aiMessageObj = responseObj.getJSONObject("aiMessage");
                                String aiResponseText = aiMessageObj.getString("message");

                                appendAiResponse(aiResponseText, aiMessageObj.getString("_id"), null, null, false);
                            }

                            showLimitReachedDialog();
                            return;
                        }

                        // Update usage from response - try root-level fields first, then session fallback
                        int msgCount = messagesUsed;
                        int limit = messageLimit;
                        if (responseObj.has("messagesUsed")) {
                            msgCount = responseObj.getInt("messagesUsed");
                        } else if (responseObj.has("session")) {
                            JSONObject sessionObj = responseObj.getJSONObject("session");
                            msgCount = sessionObj.optInt("messageCount", messagesUsed);
                        }
                        if (responseObj.has("messageLimit")) {
                            limit = responseObj.getInt("messageLimit");
                        }
                        updateUsageBar(msgCount, limit);

                        if (responseObj.has("aiMessage")) {
                            JSONObject aiMessageObj = responseObj.getJSONObject("aiMessage");
                            String aiResponseText = aiMessageObj.getString("message");
                            if (responseObj.has("aiModel")) {
                                String modelUsed = responseObj.getString("aiModel");
                                headerSubtitle.setText(modelUsed);
                            }
                            org.json.JSONArray memArr = responseObj.optJSONArray("memoriesAdded");
                            boolean memorySaved = memArr != null && memArr.length() > 0;
                            appendAiResponse(aiResponseText, aiMessageObj.getString("_id"), responseObj.optJSONArray("dataCards"), responseObj.optString("thinking", ""), memorySaved);
                        }

                        // Sync plan label with backend's authoritative tier
                        if (responseObj.has("userTier")) {
                            String userTier = responseObj.getString("userTier");
                            updatePlanLabelFromTier(userTier);
                        }

                        sendButton.setEnabled(true);
                        messageInput.setEnabled(true);

                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing message response", e);
                        showErrorMessage("Error processing AI response");
                        sendButton.setEnabled(true);
                        messageInput.setEnabled(true);
                    }
                },
                error -> {
                    ApiConfig.logRestCall(url, false, error.toString());
                    if (!isAdded()) return; // detached — no views/dialogs to update safely
                    hideThinkingAnimation();
                    sendButton.setEnabled(true);
                    messageInput.setEnabled(true);

                    ErrorHandler.ParsedError parsed = ErrorHandler.parse(error);

                    switch (parsed.type) {
                        case AUTH_EXPIRED:
                            if (isAdded()) ErrorHandler.handleAuthExpired(requireContext());
                            return;

                        case RATE_LIMIT:
                            // 429 — usage limit: go straight to ProUpgradeDialog
                            if (isAdded()) {
                                Utils.ProUpgradeDialog rateLimitDlg = new Utils.ProUpgradeDialog(requireActivity());
                                rateLimitDlg.setLimitContext("You've reached your monthly chat limit.");
                                rateLimitDlg.show(isPro -> {
                                    if (isPro) ProStatusManager.syncProStatusOnLogin(requireContext());
                                });
                            }
                            return;

                        case BAD_REQUEST:
                            // Check for app-level limit flags in 400 body
                            if (error.networkResponse != null) {
                                try {
                                    String body = new String(error.networkResponse.data, "UTF-8");
                                    JSONObject errorObj = new JSONObject(body);
                                    if (errorObj.optBoolean("sessionLimitReached", false)) {
                                        isMonthlySessionLimitReached = true;
                                        showSessionLimitReachedDialog(
                                                errorObj.optInt("sessionsUsed", 0),
                                                errorObj.optInt("sessionLimit", 0)
                                        );
                                        return;
                                    }
                                    if (errorObj.optBoolean("isLimitReached", false)) {
                                        isSessionLimitReached = true;
                                        showLimitReachedDialog();
                                        return;
                                    }
                                } catch (Exception ignored) {}
                            }
                            showErrorMessage(parsed.message);
                            return;

                        case SERVER_ERROR:
                            showErrorMessage("Server is temporarily unavailable. Please try again.");
                            return;

                        case NETWORK_ERROR:
                            showErrorMessage("No internet connection. Please check your network.");
                            return;

                        default:
                            Log.e(TAG, "Error sending message: " + error.toString());
                            showErrorMessage("Failed to send message. Please try again.");
                    }
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

        request.setRetryPolicy(new com.android.volley.DefaultRetryPolicy(
                60000, 0, 1f
        ));

        RequestQueue queue = Volley.newRequestQueue(context);
        queue.add(request);
    }


    /**
     * Per-session message limit hit — uses layout_chat_limit_dialog.xml (logo card style,
     * same visual language as SimpleProgress). Shows what happened, benefits, reset date.
     */
    private void showLimitReachedDialog() {
        if (!isAdded()) return;
        updateUsageBar(messageLimit, messageLimit);
        showChatLimitDialog(
                "Chat Session Full",
                buildPlanBadge() + " · " + messageLimit + " messages used",
                "You've reached the message limit for this chat session.",
                true  // isSessionLimit (not monthly)
        );
    }

    /**
     * Monthly sessions limit hit — same card style, different copy.
     * sessionsUsed / sessionLimit come from backend response — if both 0 we still show useful info.
     */
    private void showSessionLimitReachedDialog(int sessionsUsed, int sessionLimit) {
        if (!isAdded()) return;
        disableMessageInput();
        String countLine = (sessionLimit > 0)
                ? buildPlanBadge() + " · " + sessionsUsed + " of " + sessionLimit + " sessions used"
                : buildPlanBadge();
        showChatLimitDialog(
                "Monthly Limit Reached",
                countLine,
                "You've used all your chat sessions for this month.",
                false // not session limit
        );
    }

    /**
     * Shared dialog builder — inflates layout_chat_limit_dialog.xml, fills all fields,
     * wires buttons. Follows the same Dialog(context, R.style.DialogTheme) + XML pattern
     * used by DialogUtils throughout the app.
     */
    private void showChatLimitDialog(String title, String badge, String whatHappened, boolean isSessionLimit) {
        if (!isAdded()) return;

        android.app.Dialog dialog = new android.app.Dialog(requireContext(), R.style.DialogTheme);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.layout_chat_limit_dialog);
        dialog.setCancelable(false);

        android.view.Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(android.view.WindowManager.LayoutParams.MATCH_PARENT,
                    android.view.WindowManager.LayoutParams.WRAP_CONTENT);
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        String tier = proStatusManager.getUserTier();
        boolean canUpgrade = "free".equals(tier) || "plus".equals(tier);

        // Fill title + badge
        ((android.widget.TextView) dialog.findViewById(R.id.limit_title)).setText(title);
        ((android.widget.TextView) dialog.findViewById(R.id.limit_plan_badge)).setText(badge);
        ((android.widget.TextView) dialog.findViewById(R.id.limit_what_happened)).setText(whatHappened);

        // Fill tier-aware upgrade benefits
        String[] benefits = buildUpgradeBenefits(tier);
        ((android.widget.TextView) dialog.findViewById(R.id.limit_benefit_1)).setText(benefits[0]);
        ((android.widget.TextView) dialog.findViewById(R.id.limit_benefit_2)).setText(benefits[1]);
        ((android.widget.TextView) dialog.findViewById(R.id.limit_benefit_3)).setText(benefits[2]);

        // If no upgrade available (already ultra/family) hide the benefits block
        if (!canUpgrade) {
            dialog.findViewById(R.id.limit_upgrade_block).setVisibility(android.view.View.GONE);
        }

        // Reset date — first day of next month, calculated locally, no API needed
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.DAY_OF_MONTH, 1);
        cal.add(java.util.Calendar.MONTH, 1);
        String resetDate = new java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.US).format(cal.getTime());
        ((android.widget.TextView) dialog.findViewById(R.id.limit_reset_date))
                .setText("Limit resets on: " + resetDate);

        // Dismiss button — label depends on context
        com.google.android.material.button.MaterialButton dismissBtn =
                dialog.findViewById(R.id.limit_dismiss_button);
        dismissBtn.setText(isSessionLimit ? "New Chat" : "OK");
        dismissBtn.setOnClickListener(v -> {
            dialog.dismiss();
            if (isSessionLimit && !isMonthlySessionLimitReached) {
                startNewChatWithCurrentModel();
            }
        });

        // Upgrade button — only for free/plus; hidden for higher tiers
        com.google.android.material.button.MaterialButton upgradeBtn =
                dialog.findViewById(R.id.limit_upgrade_button);
        if (canUpgrade) {
            upgradeBtn.setOnClickListener(v -> {
                dialog.dismiss();
                Utils.ProUpgradeDialog chatLimitDlg = new Utils.ProUpgradeDialog(requireActivity());
                chatLimitDlg.setLimitContext(whatHappened);
                chatLimitDlg.show(isPro -> {
                    if (isPro) ProStatusManager.syncProStatusOnLogin(requireContext());
                });
            });
        } else {
            upgradeBtn.setVisibility(android.view.View.GONE);
            // Make dismiss button full-width when upgrade is hidden
            dismissBtn.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
        }

        dialog.show();
    }

    /** Human-readable plan name for the badge */
    private String buildPlanBadge() {
        String tier = proStatusManager.getUserTier();
        switch (tier) {
            case "ultra":         return "Ultra Plan";
            case "family":        return "Family Plan";
            case "family_member": return "Family Pro Plan";
            case "pro":           return "Pro Plan";
            case "plus":          return "Plus Plan";
            default:              return "Free Plan";
        }
    }

    /** Tier-aware upgrade benefits — 3 bullet strings */
    private String[] buildUpgradeBenefits(String tier) {
        if ("plus".equals(tier)) {
            // Plus → Pro
            return new String[]{
                "↑  50 messages per session (was 25)",
                "↑  20 chat sessions per month (was 10)",
                "↑  GPT-5.3 & Claude 4.5 models"
            };
        }
        // Free → Pro (default)
        return new String[]{
            "↑  50 messages per session (Pro) · 100 (Ultra)",
            "↑  20 chat sessions per month (Pro)",
            "↑  Advanced AI models — GPT-5.3, Claude 4.5"
        };
    }

    private void disableMessageInput() {
        messageInput.setEnabled(false);
        sendButton.setEnabled(false);
        messageInput.setHint("Session limit reached — start a new chat");

        // Show a persistent message at the bottom
        ChatMessage limitMessage = new ChatMessage(
                "Chat limit reached. Start a new chat to continue.",
                true
        );
        limitMessage.setSessionId(sessionId);
        chatAdapter.addMessage(limitMessage);
        scrollToBottom();
    }

    // ─── Autofill health cards ───────────────────────────────────────────────

    /** Adds an AI reply bubble, then any prefilled "log this" cards parsed from
     *  its healthlog block, as separate card bubbles beneath it. */
    private void appendAiResponse(String aiResponseText, String messageId, org.json.JSONArray dataCards, String reasoning, boolean memorySaved) {
        // The reply lands in place; we never auto-scroll on it. Only the user's own
        // scrolling moves the chat, so the view never jumps when a reply arrives.
        ChatMessage aiMessage = new ChatMessage(aiResponseText, true);
        aiMessage.setMessageId(messageId);
        aiMessage.setSessionId(sessionId);
        aiMessage.setAnimateReveal(true); // typewriter reveal for fresh replies only
        aiMessage.setMemoryAdded(memorySaved); // shows the memory icon only on this turn

        // Don't surface the reasoning trace on the FIRST reply of a session — early-turn
        // chain-of-thought is the most likely to restate/leak the system prompt.
        boolean hasPriorAiReply = false;
        for (ChatMessage m : chatAdapter.getMessages()) {
            if (m.isFromAI() && !m.isHealthCard() && !m.isLogEntry() && !m.isThinking()) {
                hasPriorAiReply = true;
                break;
            }
        }
        if (hasPriorAiReply && reasoning != null && !reasoning.trim().isEmpty()) {
            aiMessage.setReasoning(reasoning);
        }
        chatAdapter.addMessage(aiMessage);

        // Prefilled quick-log cards from the backend extraction pass, rendered as
        // separate collapsible card bubbles beneath the reply.
        List<HealthCard> cards = HealthLogParser.cardsFromArray(dataCards);
        for (HealthCard card : cards) {
            ChatMessage cardMsg = new ChatMessage("", true);
            cardMsg.setSessionId(sessionId);
            cardMsg.setHealthCard(card);
            chatAdapter.addMessage(cardMsg);
        }
        // Intentionally no scrollToBottom() here — replies must not move the view.
    }

    /** Tapping the memory icon on a reply just tells the user a memory was saved
     *  in this chat and points them to Settings to view/manage all memories. It does
     *  NOT list memories here — the icon only appears on turns that saved one. */
    private void showMemorySavedNote() {
        safeToast("Memory saved from this chat. View or manage it in Settings → AI Memories.");
    }

    private MedicalDataApiService medicalDataApiService;
    private MedicalDataApiService medicalDataApi() {
        if (medicalDataApiService == null) {
            medicalDataApiService = new MedicalDataApiService(requireContext());
        }
        return medicalDataApiService;
    }

    /** Toast that is safe to call from async (network) callbacks — no-ops if the
     *  fragment has detached (e.g. the user navigated away while offline). */
    private void safeToast(String msg) {
        if (!isAdded()) return;
        Context c = getContext();
        if (c != null) Utilities.toast(c, msg);
    }

    /** Routes an "Add" tap from a health card to the matching existing endpoint.
     *  On success it also drops a persisted "logged" box into the chat so the user
     *  can see, on returning, that data was recorded. */
    private void saveHealthCard(HealthCard card, ChatAdapter.HealthCardCallback cb) {
        ChatAdapter.HealthCardCallback wrapped = success -> {
            if (success) logCardToChat(card);
            cb.onResult(success);
        };
        try {
            switch (card.getKind()) {
                case HealthCard.KIND_MEASUREMENT: saveMeasurementCard(card, wrapped); break;
                case HealthCard.KIND_MEDICATION:  saveMedicationCard(card, wrapped); break;
                case HealthCard.KIND_PERIOD:      savePeriodCard(card, wrapped); break;
                case HealthCard.KIND_SYMPTOM:
                default:                          saveSymptomCard(card, wrapped); break;
            }
        } catch (Exception e) {
            Log.e(TAG, "saveHealthCard failed", e);
            Utilities.toast(requireContext(), "Couldn't save. Please try again.");
            cb.onResult(false);
        }
    }

    /** Human summary shown in the persisted "logged" box. */
    private String confirmationText(HealthCard card) {
        String[] sev = {"Very Mild", "Mild", "Moderate", "Severe", "Very Severe"};
        switch (card.getKind()) {
            case HealthCard.KIND_MEASUREMENT: {
                String u = card.getUnit();
                return "Logged measurement · " + card.getTitle() + " " + card.getValue()
                        + (u == null || u.isEmpty() ? "" : " " + u);
            }
            case HealthCard.KIND_MEDICATION: {
                String d = card.getDosage();
                return "Added medication · " + card.getName()
                        + (d == null || d.isEmpty() ? "" : " " + d);
            }
            case HealthCard.KIND_PERIOD: {
                int p = Math.max(1, Math.min(5, card.getPainLevel()));
                return "Logged period · " + capitalizeFirst(card.getFlowIntensity()) + " flow · pain " + sev[p - 1];
            }
            case HealthCard.KIND_SYMPTOM:
            default: {
                int s = Math.max(1, Math.min(5, card.getSeverity()));
                String dur = card.getDuration();
                return "Logged symptom · " + card.getTitle() + " (" + sev[s - 1]
                        + (dur == null || dur.isEmpty() ? "" : " · " + dur) + ")";
            }
        }
    }

    private String capitalizeFirst(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Adds the confirmation box locally and persists it to the session so it
     *  survives reload. */
    private void logCardToChat(HealthCard card) {
        String text = confirmationText(card);

        ChatMessage box = new ChatMessage(text, true);
        box.setSessionId(sessionId);
        box.setLogEntry(true);
        chatAdapter.addMessage(box);
        scrollToBottom();

        persistLogEntry(text);
    }

    private void persistLogEntry(String text) {
        if (sessionId == null) return;
        Context context = getContext();
        if (context == null) return;
        final TokenManager tokenManager = TokenManager.getInstance(context);
        String url = ApiConfig.BASE_URL + "/api/chat/sessions/" + sessionId + "/log";
        JSONObject body = new JSONObject();
        try { body.put("text", text); } catch (JSONException e) { return; }
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, body,
                response -> ApiConfig.logRestCall(url, true, "log entry saved"),
                error -> ApiConfig.logRestCall(url, false, error.toString())) {
            @Override public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + tokenManager.getToken());
                return headers;
            }
        };
        Volley.newRequestQueue(context).add(request);
    }

    private void saveSymptomCard(HealthCard card, ChatAdapter.HealthCardCallback cb) {
        String name = card.getTitle() == null ? "" : card.getTitle().trim();
        String duration = card.getDuration() == null ? "" : card.getDuration().trim();
        if (name.isEmpty()) { Utilities.toast(requireContext(), "Add a symptom name"); cb.onResult(false); return; }
        if (duration.isEmpty()) { Utilities.toast(requireContext(), "Add a duration"); cb.onResult(false); return; }

        MedicalData.Symptom symptom = new MedicalData.Symptom();
        symptom.setName(name);
        symptom.setSeverity(card.getSeverity());
        symptom.setDuration(duration);
        symptom.setDescription(card.getDescription());
        symptom.setRecordedAt(new Date());
        symptom.setShareWithFamily(false);
        if (userProfile != null) symptom.setUserId(userProfile.getId());

        medicalDataApi().addSymptom(symptom, new MedicalDataApiService.OnMedicalDataListener() {
            @Override public void onSuccess(JSONObject response) {
                safeToast("Symptom logged");
                cb.onResult(true);
            }
            @Override public void onError(String errorMessage) {
                safeToast("Couldn't save symptom");
                cb.onResult(false);
            }
        });
    }

    private void savePeriodCard(HealthCard card, ChatAdapter.HealthCardCallback cb) {
        MedicalData.PeriodLog log = new MedicalData.PeriodLog();
        Date start = new Date();
        String sd = card.getStartDate() == null ? "" : card.getStartDate().trim();
        if (!sd.isEmpty()) {
            try {
                SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                Date p = f.parse(sd);
                if (p != null) start = p;
            } catch (ParseException ignored) {}
        }
        log.setStartDate(start);
        log.setFlowIntensity(card.getFlowIntensity());
        log.setPainLevel(card.getPainLevel());
        log.setNotes(card.getNotes());
        log.setShareWithFamily(false);

        medicalDataApi().addPeriodLog(log, new MedicalDataApiService.OnMedicalDataListener() {
            @Override public void onSuccess(JSONObject response) {
                safeToast("Period logged");
                cb.onResult(true);
            }
            @Override public void onError(String errorMessage) {
                safeToast("Couldn't save period log");
                cb.onResult(false);
            }
        });
    }

    private void saveMeasurementCard(HealthCard card, ChatAdapter.HealthCardCallback cb) {
        String title = card.getTitle() == null ? "" : card.getTitle().trim();
        String value = card.getValue() == null ? "" : card.getValue().trim();
        String unit = card.getUnit() == null ? "" : card.getUnit().trim();
        if (title.isEmpty()) { Utilities.toast(requireContext(), "Add a measurement name"); cb.onResult(false); return; }
        if (value.isEmpty()) { Utilities.toast(requireContext(), "Add a value"); cb.onResult(false); return; }
        if (unit.isEmpty()) { Utilities.toast(requireContext(), "Add a unit"); cb.onResult(false); return; }

        // Measurement value can be non-numeric (e.g. "120/80"), so POST directly to
        // /api/medical-data with value as a string rather than the numeric-only
        // addMeasurement() path.
        String url = ApiConfig.BASE_URL + "/api/medical-data";
        JSONObject body = new JSONObject();
        try {
            body.put("type", "measurement");
            body.put("title", title);
            body.put("value", value);
            body.put("unit", unit);
            body.put("description", card.getDescription());
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            body.put("dateTime", sdf.format(new Date()));
            body.put("shareWithFamily", false);
        } catch (JSONException e) { cb.onResult(false); return; }

        postJson(url, body, "Measurement logged", "Couldn't save measurement", cb);
    }

    private void saveMedicationCard(HealthCard card, ChatAdapter.HealthCardCallback cb) {
        String name = card.getName() == null ? "" : card.getName().trim();
        String dosage = card.getDosage() == null ? "" : card.getDosage().trim();
        if (name.isEmpty()) { Utilities.toast(requireContext(), "Add a medication name"); cb.onResult(false); return; }
        if (dosage.isEmpty()) { Utilities.toast(requireContext(), "Add a dosage"); cb.onResult(false); return; }

        String url = ApiConfig.BASE_URL + "/api/medications";
        JSONObject body = new JSONObject();
        try {
            body.put("name", name);
            body.put("dosage", dosage);
            body.put("frequency", card.getFrequency());
            body.put("isOngoing", true);
            if (card.getPurpose() != null && !card.getPurpose().isEmpty()) body.put("purpose", card.getPurpose());
            body.put("shareWithFamily", false);
        } catch (JSONException e) { cb.onResult(false); return; }

        postJson(url, body, "Medication added", "Couldn't save medication", cb);
    }

    /** Shared authenticated JSON POST for card saves. */
    private void postJson(String url, JSONObject body, String okMsg, String failMsg, ChatAdapter.HealthCardCallback cb) {
        Context context = getContext();
        if (context == null) { cb.onResult(false); return; }
        final TokenManager tokenManager = TokenManager.getInstance(context);
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, body,
                response -> {
                    ApiConfig.logRestCall(url, true, okMsg);
                    safeToast(okMsg);
                    cb.onResult(true);
                },
                error -> {
                    ApiConfig.logRestCall(url, false, error.toString());
                    safeToast(failMsg);
                    cb.onResult(false);
                }) {
            @Override public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + tokenManager.getToken());
                return headers;
            }
        };
        Volley.newRequestQueue(context).add(request);
    }

    private void showErrorMessage(String errorMessage) {
        ChatMessage errorMsg = new ChatMessage("Sorry, I encountered an error: " + errorMessage, true);
        chatAdapter.addMessage(errorMsg);
        scrollToBottom();
    }

    // Toggle saved status of a message
    private void toggleMessageSaved(ChatMessage message) {
        String url = ApiConfig.BASE_URL + "/api/chat/messages/" + message.getMessageId() + "/saved";
        Context context = getContext();
        if (context == null) return;
        TokenManager tokenManager = TokenManager.getInstance(context);

        SimpleProgress progress = SimpleProgress.show(requireActivity(), "Saving...");

        StringRequest request = new StringRequest(Request.Method.PUT, url,
                response -> {
                    ApiConfig.logRestCall(url, true, "Message save toggled");
                    progress.hide();
                    try {
                        JSONObject responseObj = new JSONObject(response);
                        boolean isSaved = responseObj.getBoolean("isSaved");

                        message.setSaved(isSaved);
                        chatAdapter.updateMessageSavedStatus(message.getMessageId(), isSaved);

                        fetchSavedMessagesCount();

                        String toastMessage = isSaved ? "Chat saved" : "Chat unsaved";
                        Utilities.toast(requireContext(), toastMessage);

                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing save response", e);
                    }
                },
                error -> {
                    ApiConfig.logRestCall(url, false, error.toString());
                    progress.hide();
                    Log.e(TAG, "Error toggling saved status: " + error.toString());
                    Utilities.toast(requireContext(), "Failed to save chat");
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + tokenManager.getToken());
                return headers;
            }
        };

        RequestQueue queue = Volley.newRequestQueue(context);
        queue.add(request);
    }

    private void fetchSavedMessagesCount() {
        String url = ApiConfig.BASE_URL + "/api/chat/saved-messages";
        Context context = getContext();
        if (context == null) return; // Fragment detached, skip operation safely
        TokenManager tokenManager = TokenManager.getInstance(context);

        StringRequest request = new StringRequest(Request.Method.GET, url,
                response -> {
                    ApiConfig.logRestCall(url, true, "Saved messages count fetched");
                    try {
                        JSONArray messagesArray = new JSONArray(response);
                        if (savedCountText != null) {
                            savedCountText.setText(String.valueOf(messagesArray.length()));
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing saved messages response", e);
                    }
                },
                error -> {
                    ApiConfig.logRestCall(url, false, error.toString());
                    Log.e(TAG, "Error fetching saved messages count: " + error.toString());
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + tokenManager.getToken());
                return headers;
            }
        };

        RequestQueue queue = Volley.newRequestQueue(context);
        queue.add(request);
    }


    @Override
    public void onResume() {
        super.onResume();
        animateHeader();
        loadUserProfile();

        // Resume pill glide + logo spin if welcome screen is visible
        if (welcomeContainer != null && welcomeContainer.getVisibility() == View.VISIBLE) {
            startPillAutoScroll();
            startWelcomeLogoSpin();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        stopPillAutoScroll();
        stopWelcomeLogoSpin();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopPillAutoScroll();
        stopWelcomeLogoSpin();
        cancelReplyRecoveryPoll();
        if (iconAnimator != null) { iconAnimator.cancel(); iconAnimator = null; }
        if (thinkingCycleHandler != null) { thinkingCycleHandler.removeCallbacksAndMessages(null); thinkingCycleHandler = null; }
    }

    /**
     * Adapter for displaying chat history sessions in the side panel
     */
    private class ChatHistoryAdapter extends RecyclerView.Adapter<ChatHistoryAdapter.ViewHolder> {
        private List<ChatSession> sessions;

        public ChatHistoryAdapter() {
            this.sessions = new ArrayList<>();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(requireContext()).inflate(R.layout.item_chat_session, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ChatSession session = sessions.get(position);
            holder.bind(session, position);
        }

        @Override
        public int getItemCount() {
            return sessions.size();
        }

        public void updateSessions(List<ChatSession> newSessions) {
            this.sessions.clear();
            this.sessions.addAll(newSessions);
            notifyDataSetChanged();
        }

        public void removeSession(int position) {
            if (position >= 0 && position < sessions.size()) {
                sessions.remove(position);
                notifyItemRemoved(position);
            }
        }

        public void clearAllSessions() {
            sessions.clear();
            notifyDataSetChanged();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            private final TextView sessionTitle;
            private final TextView sessionTime;
            private final TextView sessionInfo;
            private final MaterialButton openButton;
            private final MaterialButton deleteButton;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                sessionTitle = itemView.findViewById(R.id.session_title);
                sessionTime = itemView.findViewById(R.id.session_time);
                sessionInfo = itemView.findViewById(R.id.session_info);
                openButton = itemView.findViewById(R.id.open_button);
                deleteButton = itemView.findViewById(R.id.delete_button);
            }

            void bind(ChatSession session, int position) {
                sessionTitle.setText(session.getTitle());
                sessionTime.setText(session.getTimeAgo());

                String messageInfo = formatPreview(session.getLastMessage()) +
                        " • " + session.getMessageCount() + " messages";
                sessionInfo.setText(messageInfo);

                // Set click listeners
                openButton.setOnClickListener(v -> {
                    loadSession(session);
                    chatHistoryPanel.dismiss();
                });

                deleteButton.setOnClickListener(v -> {
                    showDeleteSessionConfirmDialog(session, position);
                });

                // Also make the whole item clickable
                itemView.setOnClickListener(v -> {
                    loadSession(session);
                    chatHistoryPanel.dismiss();
                });
            }

            private String formatPreview(String message) {
                if (message == null || message.isEmpty()) {
                    return "No messages yet";
                }

                // Limit to 50 characters for preview
                if (message.length() > 50) {
                    return message.substring(0, 47) + "...";
                }

                return message;
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Chat text-size cycler
    //
    // Three sizes: Small (12sp) · Medium (13sp · default) · Large (15sp).
    // Tapping the "Aa" button cycles through them, persists the choice in
    // SharedPreferences, and propagates it to the ChatAdapter so existing
    // and future messages re-render at the new size.
    // ─────────────────────────────────────────────────────────────────────────
    private static final String CHAT_PREFS = "rh_chat_prefs";
    private static final String KEY_CHAT_TEXT_SIZE = "chat_text_size_sp";
    private static final float[] CHAT_SIZE_STEPS = { 12f, 13f, 15f };
    private static final String[] CHAT_SIZE_LABELS = { "Small", "Medium", "Large" };

    private float getSavedChatTextSizeSp() {
        try {
            android.content.SharedPreferences prefs =
                    requireContext().getSharedPreferences(CHAT_PREFS,
                            android.content.Context.MODE_PRIVATE);
            return prefs.getFloat(KEY_CHAT_TEXT_SIZE, 13f);
        } catch (Exception e) {
            return 13f;
        }
    }

    private void saveChatTextSizeSp(float sp) {
        try {
            requireContext().getSharedPreferences(CHAT_PREFS,
                    android.content.Context.MODE_PRIVATE)
                    .edit().putFloat(KEY_CHAT_TEXT_SIZE, sp).apply();
        } catch (Exception ignored) {}
    }

    private void cycleChatTextSize(View anchor) {
        float current = getSavedChatTextSizeSp();
        int idx = 1; // default to Medium
        for (int i = 0; i < CHAT_SIZE_STEPS.length; i++) {
            if (Math.abs(CHAT_SIZE_STEPS[i] - current) < 0.01f) { idx = i; break; }
        }
        int next = (idx + 1) % CHAT_SIZE_STEPS.length;
        float nextSp = CHAT_SIZE_STEPS[next];
        saveChatTextSizeSp(nextSp);
        if (chatAdapter != null) chatAdapter.setMessageTextSizeSp(nextSp);

        // Visual feedback: scale the Aa icon itself to match the chosen size.
        applyTextSizeIconScale(nextSp, true);

        // Light haptic so the change feels confirmed without a toast.
        if (anchor != null) {
            anchor.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
        }
    }

    /**
     * Scales the Aa text-size icon so its visual size mirrors the active chat
     * text size — Small → 0.85, Medium → 1.0, Large → 1.18.
     * Cheap, animated by default, no toast.
     */
    private void applyTextSizeIconScale(float sp, boolean animate) {
        if (textSizeButton == null) return;
        float scale;
        if      (sp <= 12.5f) scale = 0.85f;
        else if (sp <= 13.5f) scale = 1.00f;
        else                  scale = 1.18f;
        if (animate) {
            textSizeButton.animate()
                    .scaleX(scale).scaleY(scale)
                    .setDuration(180)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(1.4f))
                    .start();
        } else {
            textSizeButton.setScaleX(scale);
            textSizeButton.setScaleY(scale);
        }
    }
}
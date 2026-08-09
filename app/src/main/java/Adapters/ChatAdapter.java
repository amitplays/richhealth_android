package Adapters;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.richhealth.R;
import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.List;
import java.util.Locale;

import Models.ChatMessage;
import Models.HealthCard;
import Utils.TextFormatter;
import Utils.Utilities;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_USER = 1;
    private static final int VIEW_TYPE_AI = 2;
    private static final int VIEW_TYPE_FORK_CONTEXT = 3;
    private static final int VIEW_TYPE_HEALTH_CARD = 4;
    private static final int VIEW_TYPE_LOG = 5;

    private List<ChatMessage> messages;
    private Context context;
    private OnMessageSavedListener savedListener;
    private OnMessageForkListener forkListener;
    private OnForkContextClickListener forkContextClickListener;
    private OnHealthCardActionListener healthCardListener;
    private OnMemoryClickListener memoryClickListener;
    private boolean readOnly;
    /** Message body text size in sp. Default 13 (matches layout default). */
    private float messageTextSizeSp = 13f;
    /** Holder whose typewriter reveal is currently running (null when none). */
    private AIMessageViewHolder revealingHolder;

    public void removeMessageAt(int position) {
        if (position >= 0 && position < messages.size()) {
            messages.remove(position);
            notifyItemRemoved(position);
        }
    }
    public interface OnMessageSavedListener {
        void onMessageSaved(ChatMessage message);
    }
    public interface OnMessageForkListener {
        void onMessageFork(ChatMessage message, int position);
    }
    public interface OnForkContextClickListener {
        void onForkContextClick(ChatMessage message);
    }
    /** Result callback for a save initiated from a health card. */
    public interface HealthCardCallback {
        void onResult(boolean success);
    }
    /** Fragment handles the actual persistence (token + existing endpoints). */
    public interface OnHealthCardActionListener {
        void onAddHealthCard(HealthCard card, HealthCardCallback callback);
    }
    /** Tapping the memory icon on a reply opens "What Richie remembers" (from DB). */
    public interface OnMemoryClickListener {
        void onMemoryClick();
    }

    public ChatAdapter(Context context) {
        this.context = context;
        this.messages = new ArrayList<>();
    }

    public void setSavedListener(OnMessageSavedListener listener) {
        this.savedListener = listener;
    }

    public void setForkListener(OnMessageForkListener listener) {
        this.forkListener = listener;
    }

    public void setForkContextClickListener(OnForkContextClickListener listener) {
        this.forkContextClickListener = listener;
    }

    public void setHealthCardListener(OnHealthCardActionListener listener) {
        this.healthCardListener = listener;
    }

    public void setMemoryClickListener(OnMemoryClickListener listener) {
        this.memoryClickListener = listener;
    }

    /** Read-only mode hides save/fork action buttons (used inside the forked-chat preview dialog). */
    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    /** Sets the text size (sp) used for both user and AI message bodies, then refreshes the list. */
    public void setMessageTextSizeSp(float sp) {
        if (this.messageTextSizeSp == sp) return;
        this.messageTextSizeSp = sp;
        notifyDataSetChanged();
    }

    public float getMessageTextSizeSp() {
        return messageTextSizeSp;
    }

    /**
     * Claude-style compact relative time: "now", "2m ago", "1h ago", "3d ago",
     * "2w ago", "Mar 14". Falls back to a date for anything older than a year.
     */
    private static String formatRelativeTime(long timestampMs) {
        long now = System.currentTimeMillis();
        long diff = Math.max(0, now - timestampMs);
        long sec = diff / 1000;
        if (sec < 45)        return "now";
        long min = sec / 60;
        if (min < 60)        return min + "m ago";
        long hr = min / 60;
        if (hr < 24)         return hr + "h ago";
        long day = hr / 24;
        if (day < 7)         return day + "d ago";
        long week = day / 7;
        if (week < 5)        return week + "w ago";
        long month = day / 30;
        if (month < 12)      return month + "mo ago";
        return new SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                .format(new Date(timestampMs));
    }

    @Override
    public int getItemViewType(int position) {
        ChatMessage message = messages.get(position);
        if (message.isHealthCard()) return VIEW_TYPE_HEALTH_CARD;
        if (message.isLogEntry()) return VIEW_TYPE_LOG;
        if (message.isForkContext()) return VIEW_TYPE_FORK_CONTEXT;
        return message.isFromAI() ? VIEW_TYPE_AI : VIEW_TYPE_USER;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        if (viewType == VIEW_TYPE_AI) {
            view = LayoutInflater.from(context).inflate(R.layout.item_chat_ai, parent, false);
            return new AIMessageViewHolder(view);
        } else if (viewType == VIEW_TYPE_HEALTH_CARD) {
            view = LayoutInflater.from(context).inflate(R.layout.item_chat_health_card, parent, false);
            return new HealthCardViewHolder(view);
        } else if (viewType == VIEW_TYPE_LOG) {
            view = LayoutInflater.from(context).inflate(R.layout.item_chat_log, parent, false);
            return new LogViewHolder(view);
        } else if (viewType == VIEW_TYPE_FORK_CONTEXT) {
            view = LayoutInflater.from(context).inflate(R.layout.item_chat_fork_context, parent, false);
            return new ForkContextViewHolder(view);
        } else {
            view = LayoutInflater.from(context).inflate(R.layout.item_chat_user, parent, false);
            return new UserMessageViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage message = messages.get(position);

        if (holder instanceof AIMessageViewHolder) {
            ((AIMessageViewHolder) holder).bind(message, position);
        } else if (holder instanceof HealthCardViewHolder) {
            ((HealthCardViewHolder) holder).bind(message);
        } else if (holder instanceof LogViewHolder) {
            ((LogViewHolder) holder).bind(message);
        } else if (holder instanceof UserMessageViewHolder) {
            ((UserMessageViewHolder) holder).bind(message);
        } else if (holder instanceof ForkContextViewHolder) {
            ((ForkContextViewHolder) holder).bind(message);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    public void addMessage(ChatMessage message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    /**
     * Expand/collapse content inside a row without the list jumping. With
     * stackFromEnd=true a size change re-anchors the layout (jumps to top/bottom);
     * we capture the row's top, apply the change, then re-pin the same row to the
     * same offset so it stays visually put.
     */
    private void toggleKeepingScroll(RecyclerView.ViewHolder holder, Runnable change) {
        View item = holder.itemView;
        android.view.ViewParent parent = item.getParent();
        if (!(parent instanceof RecyclerView)) { change.run(); return; }
        final RecyclerView rv = (RecyclerView) parent;
        final RecyclerView.LayoutManager lm = rv.getLayoutManager();
        final int pos = holder.getAdapterPosition();
        final int top = item.getTop();
        change.run();
        if (lm instanceof LinearLayoutManager && pos != RecyclerView.NO_POSITION) {
            rv.post(() -> ((LinearLayoutManager) lm).scrollToPositionWithOffset(pos, top));
        }
    }

    public void addMessageAt(int index, ChatMessage message) {
        if (index < 0) index = 0;
        if (index > messages.size()) index = messages.size();
        messages.add(index, message);
        notifyItemInserted(index);
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }

    public void clear() {
        messages.clear();
        notifyDataSetChanged();
    }

    public void updateMessageSavedStatus(String messageId, boolean isSaved) {
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            if (message.getMessageId() != null && message.getMessageId().equals(messageId)) {
                message.setSaved(isSaved);
                notifyItemChanged(i);
                break;
            }
        }
    }

    /**
     * Long-press any text bubble to copy its contents to the clipboard, with a toast
     * confirmation. Shared by the user, AI, and log bubbles so the gesture is
     * identical everywhere. Returns true so the long-press is consumed.
     */
    private boolean copyMessageToClipboard(String text) {
        if (text == null || text.trim().isEmpty()) return false;
        try {
            android.content.ClipboardManager cm = (android.content.ClipboardManager)
                    context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null) return false;
            cm.setPrimaryClip(android.content.ClipData.newPlainText("RichHealth message", text));
            Utilities.toast(context, "Copied to clipboard");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    class UserMessageViewHolder extends RecyclerView.ViewHolder {
        private TextView messageTextView;
        private TextView timeTextView;

        public UserMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            messageTextView = itemView.findViewById(R.id.message_text);
            timeTextView = itemView.findViewById(R.id.time_text);
        }

        public void bind(ChatMessage message) {
            messageTextView.setText(message.getMessage());
            messageTextView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, messageTextSizeSp);
            SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.getDefault());
            timeTextView.setText(sdf.format(new Date(message.getTimestamp())));
            // Long-press to copy (consistent across all bubbles).
            itemView.setOnLongClickListener(v -> copyMessageToClipboard(message.getMessage()));
        }
    }

    class AIMessageViewHolder extends RecyclerView.ViewHolder {
        private TextView messageTextView;
        private TextView timeTextView;
        private TextView timeAgoTextView;
        private View actionRow;
        private com.google.android.material.card.MaterialCardView bubbleCard;
        private ImageButton saveButton;
        private ImageButton forkButton;
        private ImageButton memoryButton;
        private Runnable revealRunnable;
        private CharSequence revealFull; // full text for an in-progress typewriter
        private View thinkingHeader;
        private TextView thinkingText;
        private ImageView thinkingChevron;

        public AIMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            messageTextView = itemView.findViewById(R.id.message_text);
            timeTextView = itemView.findViewById(R.id.time_text);          // legacy, hidden
            timeAgoTextView = itemView.findViewById(R.id.time_ago_text);   // new in-bubble
            actionRow = itemView.findViewById(R.id.action_row);
            bubbleCard = itemView.findViewById(R.id.bubble_card);
            saveButton = itemView.findViewById(R.id.save_button);
            forkButton = itemView.findViewById(R.id.fork_button);
            memoryButton = itemView.findViewById(R.id.memory_button);
            thinkingHeader = itemView.findViewById(R.id.thinking_header);
            thinkingText = itemView.findViewById(R.id.thinking_text);
            thinkingChevron = itemView.findViewById(R.id.thinking_chevron);
        }

        public void bind(ChatMessage message, int position) {
            // Apply formatting to AI messages
            SpannableStringBuilder formattedText = TextFormatter.formatResponse(message.getMessage());
            messageTextView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, messageTextSizeSp);

            // Cancel any typewriter still running on this (possibly recycled) holder.
            if (revealRunnable != null) {
                messageTextView.removeCallbacks(revealRunnable);
                revealRunnable = null;
                revealFull = null;
                if (revealingHolder == this) revealingHolder = null;
            }
            if (message.isAnimateReveal() && !message.isThinking()) {
                message.setAnimateReveal(false); // play once; re-binds show full text
                startTypewriter(formattedText);
            } else {
                messageTextView.setText(formattedText);
            }

            // ─── Reasoning ("Thinking") collapsible ──────────────────────
            // Shown only for real replies from a thinking-capable model. Collapsed
            // by default; tap the header to expand. Reset every bind (recycle-safe).
            if (thinkingHeader != null && thinkingText != null) {
                if (message.hasReasoning() && !message.isThinking()) {
                    thinkingText.setText(message.getReasoning().trim());
                    thinkingHeader.setVisibility(View.VISIBLE);
                    thinkingText.setVisibility(View.GONE);
                    if (thinkingChevron != null) thinkingChevron.setRotation(0f);
                    thinkingHeader.setOnClickListener(v -> {
                        final boolean show = thinkingText.getVisibility() != View.VISIBLE;
                        if (thinkingChevron != null) {
                            thinkingChevron.animate().rotation(show ? 180f : 0f).setDuration(180).start();
                        }
                        // Keep the row pinned so the chat doesn't jump on expand/collapse.
                        toggleKeepingScroll(AIMessageViewHolder.this,
                                () -> thinkingText.setVisibility(show ? View.VISIBLE : View.GONE));
                    });
                } else {
                    thinkingHeader.setVisibility(View.GONE);
                    thinkingText.setVisibility(View.GONE);
                }
            }

            // ─── Thinking placeholder ────────────────────────────────────
            // No card background, no action row, no bookmark/fork/time.
            // Just teal text + the animated dots already baked into the
            // message string ("Reading your reports . . .").
            if (message.isThinking()) {
                // Transient placeholder — not copyable (clear any listener left on a
                // recycled holder so a long-press can't copy a previous reply).
                itemView.setOnLongClickListener(null);
                if (bubbleCard != null) {
                    bubbleCard.setCardBackgroundColor(android.graphics.Color.TRANSPARENT);
                    bubbleCard.setStrokeWidth(0);
                }
                messageTextView.setTextColor(android.graphics.Color.parseColor("#00B8B8")); // app teal, brighter for legibility
                messageTextView.setAlpha(0.92f);
                if (actionRow != null) actionRow.setVisibility(View.GONE);
                // Tighten internal padding so the line floats next to the avatar.
                View container = itemView.findViewById(R.id.message_container);
                if (container != null) {
                    container.setPadding(0, container.getPaddingTop(), 0, 0);
                }
                return;
            }
            // Restore normal bubble look for non-thinking AI messages.
            if (bubbleCard != null) {
                bubbleCard.setCardBackgroundColor(android.graphics.Color.parseColor("#AD3E3C3C"));
                bubbleCard.setStrokeWidth(0);
            }
            messageTextView.setTextColor(android.graphics.Color.WHITE);
            messageTextView.setAlpha(1f);
            View container = itemView.findViewById(R.id.message_container);
            if (container != null) {
                int dp = (int) android.util.TypedValue.applyDimension(
                        android.util.TypedValue.COMPLEX_UNIT_DIP, 1,
                        container.getResources().getDisplayMetrics());
                container.setPadding(12 * dp, 10 * dp, 12 * dp, 4 * dp);
            }
            if (actionRow != null) actionRow.setVisibility(View.VISIBLE);

            // Legacy absolute time (now hidden by layout) — kept set for any reads.
            SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.getDefault());
            timeTextView.setText(sdf.format(new Date(message.getTimestamp())));

            // New: Claude-style relative time inside the bubble action row.
            if (timeAgoTextView != null) {
                timeAgoTextView.setText(formatRelativeTime(message.getTimestamp()));
            }

            // Update bookmark icon based on saved status
            if (message.isSaved()) {
                saveButton.setImageResource(R.drawable.ic_bookmark);
                saveButton.setColorFilter(ContextCompat.getColor(context, R.color.gold));
            } else {
                saveButton.setImageResource(R.drawable.ic_bookmark);
                saveButton.setColorFilter(ContextCompat.getColor(context, android.R.color.darker_gray));
            }

            // Hide action buttons in read-only mode (forked-chat preview dialog).
            // Fork button additionally requires a real saved messageId to fork from.
            boolean hasRealId = message.getMessageId() != null && !message.getMessageId().isEmpty();
            saveButton.setVisibility(readOnly ? View.GONE : View.VISIBLE);
            forkButton.setVisibility((readOnly || !hasRealId) ? View.GONE : View.VISIBLE);
            if (memoryButton != null) {
                // Only shown on the turn where Richie actually saved a memory.
                boolean showMemory = !readOnly && message.isMemoryAdded();
                memoryButton.setVisibility(showMemory ? View.VISIBLE : View.GONE);
                memoryButton.setOnClickListener(v -> {
                    if (memoryClickListener != null) memoryClickListener.onMemoryClick();
                });
            }

            saveButton.setOnClickListener(v -> {
                if (savedListener != null) {
                    savedListener.onMessageSaved(message);
                }
            });

            forkButton.setOnClickListener(v -> {
                if (forkListener != null) {
                    forkListener.onMessageFork(message, getAdapterPosition());
                }
            });

            // Long-press anywhere on the reply to copy its text (the "thinking"
            // placeholder returned earlier, so this only applies to real replies).
            itemView.setOnLongClickListener(v -> copyMessageToClipboard(message.getMessage()));
        }

        /** Length-aware typewriter reveal: more chars per step for longer replies
         *  (feels faster), ~1 char per step for short ones (feels typed). Preserves
         *  markdown spans via subSequence and is safe against view recycling. */
        private void startTypewriter(final CharSequence full) {
            revealFull = full;
            revealingHolder = this; // track for completeActiveTypewriter()
            final int total = full.length();
            if (total == 0) { messageTextView.setText(full); finishReveal(); return; }
            final int charsPerStep = Math.max(1, Math.min(12, Math.round(total / 45f)));
            final int[] shown = {0};
            messageTextView.setText("");
            // Follow the typing to the bottom ONLY if the user is already at the bottom
            // when it starts — so a reader watching the reply sees it stream in, while
            // someone who scrolled up is left undisturbed. A manual drag still ends the
            // reveal (see the SCROLL_STATE_DRAGGING listener), which frees their scroll.
            final RecyclerView followRv = (itemView.getParent() instanceof RecyclerView)
                    ? (RecyclerView) itemView.getParent() : null;
            final boolean follow = isNearBottom(followRv);
            revealRunnable = new Runnable() {
                @Override public void run() {
                    shown[0] = Math.min(total, shown[0] + charsPerStep);
                    messageTextView.setText(full.subSequence(0, shown[0]));
                    if (follow && followRv != null && !followRv.isComputingLayout()) {
                        followRv.scrollBy(0, 1_000_000); // clamps to content bottom (stick to newest text)
                    }
                    if (shown[0] < total) {
                        messageTextView.postDelayed(this, 22);
                    } else {
                        finishReveal();
                    }
                }
            };
            messageTextView.postDelayed(revealRunnable, 22);
        }

        /** Stop the reveal and show the full text at once. Idempotent. Called both
         *  on natural completion and when the user grabs the list to scroll — each
         *  reveal tick calls setText() which relayouts the RecyclerView, and under
         *  stackFromEnd that fights a manual scroll. Completing immediately ends
         *  the fight and leaves the user wherever they scrolled to. */
        void finishReveal() {
            if (revealRunnable != null) {
                messageTextView.removeCallbacks(revealRunnable);
                revealRunnable = null;
            }
            if (revealFull != null) {
                messageTextView.setText(revealFull);
                revealFull = null;
            }
            if (revealingHolder == this) revealingHolder = null;
        }
    }

    /** Force-finish any typewriter reveal currently in progress (see finishReveal). */
    public void completeActiveTypewriter() {
        if (revealingHolder != null) revealingHolder.finishReveal();
    }

    /** True when the list is scrolled to (or within one row of) the newest message. */
    private boolean isNearBottom(RecyclerView rv) {
        if (rv == null) return false;
        RecyclerView.LayoutManager lm = rv.getLayoutManager();
        if (lm instanceof LinearLayoutManager) {
            int last = ((LinearLayoutManager) lm).findLastVisibleItemPosition();
            return last < 0 || last >= getItemCount() - 2;
        }
        return false;
    }

    // ─── Autofill "log this" card ────────────────────────────────────────────
    private interface StrSetter { void set(String s); }

    class HealthCardViewHolder extends RecyclerView.ViewHolder {
        private final LinearLayout root;

        HealthCardViewHolder(@NonNull View itemView) {
            super(itemView);
            root = itemView.findViewById(R.id.health_card_root);
        }

        void bind(ChatMessage message) {
            build(message.getHealthCard());
        }

        private float density() {
            return root.getResources().getDisplayMetrics().density;
        }

        private void build(final HealthCard card) {
            root.removeAllViews();
            if (card == null) return;
            final float dp = density();

            if (card.isDismissed()) {
                TextView t = new TextView(context);
                t.setText("Dismissed");
                t.setTextColor(Color.parseColor("#4A6A6A"));
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
                t.setPadding((int) (6 * dp), (int) (2 * dp), 0, 0);
                root.addView(t);
                return;
            }

            MaterialCardView cardView = new MaterialCardView(context);
            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            cardView.setLayoutParams(cardLp);
            cardView.setRadius(14 * dp);
            cardView.setCardElevation(0f);
            cardView.setStrokeWidth((int) (1 * dp));
            boolean expanded = card.isExpanded() && !card.isAdded();
            cardView.setStrokeColor(Color.parseColor(expanded ? "#274545" : "#1A2A2A"));
            cardView.setCardBackgroundColor(Color.parseColor("#0C1414"));

            LinearLayout col = new LinearLayout(context);
            col.setOrientation(LinearLayout.VERTICAL);

            // ── Header ──
            LinearLayout header = new LinearLayout(context);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);
            header.setPadding((int) (15 * dp), (int) (13 * dp), (int) (13 * dp), (int) (13 * dp));
            header.setClickable(true);
            TypedValue tv = new TypedValue();
            context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
            header.setBackgroundResource(tv.resourceId);

            ImageView lead = new ImageView(context);
            lead.setImageResource(card.isAdded() ? R.drawable.ic_check : R.drawable.ic_model_auto);
            lead.setColorFilter(Color.parseColor(card.isAdded() ? "#37C9A6" : "#008b8b"));
            LinearLayout.LayoutParams leadLp = new LinearLayout.LayoutParams((int) (15 * dp), (int) (15 * dp));
            leadLp.setMarginEnd((int) (12 * dp));
            header.addView(lead, leadLp);

            TextView titleView = new TextView(context);
            titleView.setText(card.isAdded() ? ("Added · " + strip(card.getHeaderLabel())) : card.getHeaderLabel());
            titleView.setTextColor(Color.parseColor("#DCE6E6"));
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
            header.addView(titleView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

            if (!card.isAdded()) {
                final ImageView chevron = new ImageView(context);
                chevron.setImageResource(R.drawable.ic_chevron_down);
                chevron.setColorFilter(Color.parseColor("#4A6A6A"));
                chevron.setRotation(expanded ? 180f : 0f);
                header.addView(chevron, new LinearLayout.LayoutParams((int) (16 * dp), (int) (16 * dp)));
                header.setOnClickListener(v -> toggleKeepingScroll(HealthCardViewHolder.this, () -> {
                    card.setExpanded(!card.isExpanded());
                    build(card);
                }));
            }
            col.addView(header);

            // ── Expanded editor ──
            if (expanded) {
                LinearLayout body = new LinearLayout(context);
                body.setOrientation(LinearLayout.VERTICAL);
                body.setPadding((int) (15 * dp), 0, (int) (15 * dp), (int) (13 * dp));

                View divider = new View(context);
                divider.setBackgroundColor(Color.parseColor("#152525"));
                LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, (int) dp));
                divLp.bottomMargin = (int) (12 * dp);
                body.addView(divider, divLp);

                buildFields(body, card, dp);
                buildButtons(body, card, dp);

                col.addView(body);
            }

            cardView.addView(col);
            LinearLayout.LayoutParams outer = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            outer.bottomMargin = (int) (4 * dp);
            root.addView(cardView, outer);
        }

        private String strip(String header) {
            int i = header.indexOf("· ");
            return i >= 0 ? header.substring(i + 2) : header;
        }

        private void buildFields(LinearLayout body, HealthCard card, float dp) {
            switch (card.getKind()) {
                case HealthCard.KIND_MEASUREMENT:
                    body.addView(input("What", card.getTitle(), "e.g. Blood Pressure",
                            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, dp, card::setTitle));
                    body.addView(input("Value", card.getValue(), "e.g. 120/80",
                            InputType.TYPE_CLASS_TEXT, dp, card::setValue));
                    body.addView(input("Unit", card.getUnit(), "e.g. mmHg",
                            InputType.TYPE_CLASS_TEXT, dp, card::setUnit));
                    body.addView(dateRow("When", card, card.getDateTime(), true, card::setDateTime, dp));
                    break;
                case HealthCard.KIND_MEDICATION:
                    body.addView(input("Medication", card.getName(), "e.g. Paracetamol",
                            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, dp, card::setName));
                    body.addView(input("Dosage", card.getDosage(), "e.g. 500mg",
                            InputType.TYPE_CLASS_TEXT, dp, card::setDosage));
                    body.addView(readonly("Frequency", card.getFrequency(), dp));
                    body.addView(input("For (purpose)", card.getPurpose(), "e.g. headache",
                            InputType.TYPE_CLASS_TEXT, dp, card::setPurpose));
                    body.addView(dateRow("When", card, card.getDateTime(), true, card::setDateTime, dp));
                    break;
                case HealthCard.KIND_PERIOD:
                    body.addView(flowChips(card, dp));
                    body.addView(seekRow("Pain", card.getPainLevel(), dp, level -> card.setPainLevel(level)));
                    body.addView(dateRow("Start", card, card.getStartDate(), false, card::setStartDate, dp));
                    body.addView(input("Notes", card.getNotes(), "optional",
                            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, dp, card::setNotes));
                    break;
                case HealthCard.KIND_SYMPTOM:
                default:
                    body.addView(input("Symptom", card.getTitle(), "e.g. Itching",
                            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, dp, card::setTitle));
                    body.addView(seekRow("Severity", card.getSeverity(), dp, level -> card.setSeverity(level)));
                    body.addView(input("Duration", card.getDuration(), "e.g. 2 days",
                            InputType.TYPE_CLASS_TEXT, dp, card::setDuration));
                    body.addView(input("Notes", card.getDescription(), "optional",
                            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, dp, card::setDescription));
                    body.addView(dateRow("When", card, card.getDateTime(), true, card::setDateTime, dp));
                    break;
            }
        }

        private void buildButtons(LinearLayout body, final HealthCard card, final float dp) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rowLp.topMargin = (int) (10 * dp);
            row.setLayoutParams(rowLp);

            final TextView add = new TextView(context);
            add.setText("Add");
            add.setTextColor(Color.WHITE);
            add.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
            add.setTypeface(null, android.graphics.Typeface.BOLD);
            add.setGravity(Gravity.CENTER);
            add.setPadding((int) (24 * dp), (int) (8 * dp), (int) (24 * dp), (int) (8 * dp));
            GradientDrawable addBg = new GradientDrawable();
            addBg.setCornerRadius(16 * dp);
            addBg.setColor(Color.parseColor("#008b8b"));
            add.setBackground(addBg);
            add.setClickable(true);
            add.setOnClickListener(v -> {
                if (healthCardListener == null) return;
                add.setEnabled(false);
                add.setText("Saving…");
                healthCardListener.onAddHealthCard(card, success -> {
                    if (success) {
                        card.setAdded(true);
                        card.setExpanded(false);
                    } else {
                        add.setEnabled(true);
                        add.setText("Add");
                    }
                    build(card);
                });
            });
            row.addView(add);

            TextView dismiss = new TextView(context);
            dismiss.setText("Dismiss");
            dismiss.setTextColor(Color.parseColor("#7A8A8A"));
            dismiss.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
            dismiss.setPadding((int) (14 * dp), (int) (8 * dp), (int) (14 * dp), (int) (8 * dp));
            LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            dLp.setMarginStart((int) (6 * dp));
            dismiss.setLayoutParams(dLp);
            dismiss.setClickable(true);
            dismiss.setOnClickListener(v -> {
                card.setDismissed(true);
                android.transition.AutoTransition t = new android.transition.AutoTransition();
                t.setDuration(200);
                android.transition.TransitionManager.beginDelayedTransition(root, t);
                build(card);
            });
            row.addView(dismiss);

            body.addView(row);
        }

        // ── small field builders ──
        private LinearLayout input(String label, String value, String hint, int inputType,
                                   float dp, final StrSetter setter) {
            LinearLayout wrap = new LinearLayout(context);
            wrap.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams wLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            wLp.bottomMargin = (int) (9 * dp);
            wrap.setLayoutParams(wLp);
            wrap.addView(fieldLabel(label, dp));

            final EditText et = new EditText(context);
            et.setText(value == null ? "" : value);
            et.setHint(hint);
            et.setInputType(inputType);
            et.setSingleLine(true);
            // These inline fields are rebuilt as the card expands/collapses; opting them
            // out of autofill avoids the OS cancelling/restarting fill sessions each time
            // (harmless, but it spams RemoteFillService in logcat). Not credential fields.
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                et.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
            }
            et.setTextColor(Color.parseColor("#E4EEEE"));
            et.setHintTextColor(Color.parseColor("#4A6A6A"));
            et.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            et.setPadding((int) (11 * dp), (int) (9 * dp), (int) (11 * dp), (int) (9 * dp));
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(10 * dp);
            bg.setColor(Color.parseColor("#0A1212"));
            bg.setStroke((int) dp, Color.parseColor("#1A2A2A"));
            et.setBackground(bg);
            et.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                public void onTextChanged(CharSequence s, int a, int b, int c) {}
                public void afterTextChanged(Editable s) { setter.set(s.toString()); }
            });
            LinearLayout.LayoutParams etLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            etLp.topMargin = (int) (4 * dp);
            wrap.addView(et, etLp);
            return wrap;
        }

        private LinearLayout readonly(String label, String value, float dp) {
            LinearLayout wrap = new LinearLayout(context);
            wrap.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams wLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            wLp.bottomMargin = (int) (9 * dp);
            wrap.setLayoutParams(wLp);
            wrap.addView(fieldLabel(label, dp));
            TextView t = new TextView(context);
            t.setText(value == null || value.isEmpty() ? "—" : value);
            t.setTextColor(Color.parseColor("#AEC0C0"));
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            tLp.topMargin = (int) (3 * dp);
            wrap.addView(t, tLp);
            return wrap;
        }

        private TextView fieldLabel(String label, float dp) {
            TextView l = new TextView(context);
            l.setText(label.toUpperCase(Locale.getDefault()));
            l.setTextColor(Color.parseColor("#5F8A8A"));
            l.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f);
            l.setLetterSpacing(0.06f);
            return l;
        }

        private LinearLayout seekRow(String label, int value1to5, final float dp, final IntSetter setter) {
            LinearLayout wrap = new LinearLayout(context);
            wrap.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams wLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            wLp.bottomMargin = (int) (9 * dp);
            wrap.setLayoutParams(wLp);

            LinearLayout labelRow = new LinearLayout(context);
            labelRow.setOrientation(LinearLayout.HORIZONTAL);
            labelRow.addView(fieldLabel(label, dp), new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            final TextView val = new TextView(context);
            final String[] labels = {"Very Mild", "Mild", "Moderate", "Severe", "Very Severe"};
            int init = Math.max(1, Math.min(5, value1to5));
            val.setText(labels[init - 1]);
            val.setTextColor(Color.parseColor("#37C9A6"));
            val.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
            labelRow.addView(val);
            wrap.addView(labelRow);

            SeekBar seek = new SeekBar(context);
            seek.setMax(4);
            seek.setProgress(init - 1);
            seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                    val.setText(labels[progress]);
                    setter.set(progress + 1);
                }
                public void onStartTrackingTouch(SeekBar sb) {}
                public void onStopTrackingTouch(SeekBar sb) {}
            });
            LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            sLp.topMargin = (int) (2 * dp);
            wrap.addView(seek, sLp);
            return wrap;
        }

        private LinearLayout flowChips(final HealthCard card, final float dp) {
            LinearLayout wrap = new LinearLayout(context);
            wrap.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams wLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            wLp.bottomMargin = (int) (9 * dp);
            wrap.setLayoutParams(wLp);
            wrap.addView(fieldLabel("Flow", dp));

            LinearLayout chipRow = new LinearLayout(context);
            chipRow.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams crLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            crLp.topMargin = (int) (5 * dp);
            chipRow.setLayoutParams(crLp);

            final String[] keys = {"light", "medium", "heavy"};
            final String[] labels = {"Light", "Medium", "Heavy"};
            for (int i = 0; i < keys.length; i++) {
                final String key = keys[i];
                boolean sel = key.equals(card.getFlowIntensity());
                TextView chip = new TextView(context);
                chip.setText(labels[i]);
                chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
                chip.setTextColor(Color.parseColor(sel ? "#0C1414" : "#AEC0C0"));
                chip.setGravity(Gravity.CENTER);
                chip.setPadding((int) (16 * dp), (int) (7 * dp), (int) (16 * dp), (int) (7 * dp));
                GradientDrawable chipBg = new GradientDrawable();
                chipBg.setCornerRadius(14 * dp);
                chipBg.setColor(Color.parseColor(sel ? "#37C9A6" : "#0A1212"));
                chipBg.setStroke((int) dp, Color.parseColor(sel ? "#37C9A6" : "#1A2A2A"));
                chip.setBackground(chipBg);
                chip.setClickable(true);
                LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                cLp.setMarginEnd((int) (8 * dp));
                chip.setLayoutParams(cLp);
                chip.setOnClickListener(v -> {
                    card.setFlowIntensity(key);
                    build(card);
                });
                chipRow.addView(chip);
            }
            wrap.addView(chipRow);
            return wrap;
        }

        // ── date / time picker row (quick-log cards can carry an editable time) ──
        private LinearLayout dateRow(final String label, final HealthCard card, final String currentIso,
                                     final boolean withTime, final StrSetter setter, final float dp) {
            LinearLayout wrap = new LinearLayout(context);
            wrap.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams wLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            wLp.bottomMargin = (int) (9 * dp);
            wrap.setLayoutParams(wLp);
            wrap.addView(fieldLabel(label, dp));

            final TextView t = new TextView(context);
            t.setText(displayDate(currentIso, withTime));
            t.setTextColor(Color.parseColor("#37C9A6"));
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            t.setClickable(true);
            t.setOnClickListener(v -> pickDate(currentIso, withTime, iso -> {
                setter.set(iso);
                build(card);
            }));
            LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            tLp.topMargin = (int) (3 * dp);
            wrap.addView(t, tLp);
            return wrap;
        }

        private String displayDate(String iso, boolean withTime) {
            Date d = parseIso(iso);
            if (d == null) return withTime ? "Now  (tap to change)" : "Today  (tap to change)";
            SimpleDateFormat out = new SimpleDateFormat(withTime ? "d MMM, h:mm a" : "d MMM yyyy", Locale.getDefault());
            return out.format(d);
        }

        private Date parseIso(String iso) {
            if (iso == null || iso.trim().isEmpty()) return null;
            String s = iso.trim();
            String[] fmts = { "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm", "yyyy-MM-dd" };
            for (String fmt : fmts) {
                try {
                    SimpleDateFormat f = new SimpleDateFormat(fmt, Locale.US);
                    if (fmt.endsWith("'Z'")) f.setTimeZone(TimeZone.getTimeZone("UTC"));
                    return f.parse(s);
                } catch (Exception ignored) {}
            }
            return null;
        }

        private void pickDate(String currentIso, final boolean withTime, final StrSetter onPicked) {
            final Calendar cal = Calendar.getInstance();
            Date cur = parseIso(currentIso);
            if (cur != null) cal.setTime(cur);
            final android.content.Context ctx = root.getContext();
            DatePickerDialog dpd = new DatePickerDialog(ctx, (view, y, m, d) -> {
                cal.set(Calendar.YEAR, y);
                cal.set(Calendar.MONTH, m);
                cal.set(Calendar.DAY_OF_MONTH, d);
                if (withTime) {
                    new TimePickerDialog(ctx, (tv2, h, min) -> {
                        cal.set(Calendar.HOUR_OF_DAY, h);
                        cal.set(Calendar.MINUTE, min);
                        cal.set(Calendar.SECOND, 0);
                        cal.set(Calendar.MILLISECOND, 0);
                        SimpleDateFormat out = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
                        out.setTimeZone(TimeZone.getTimeZone("UTC"));
                        onPicked.set(out.format(cal.getTime()));
                    }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false).show();
                } else {
                    SimpleDateFormat out = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                    onPicked.set(out.format(cal.getTime()));
                }
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
            dpd.getDatePicker().setMaxDate(System.currentTimeMillis());
            dpd.show();
        }
    }

    private interface IntSetter { void set(int v); }

    class LogViewHolder extends RecyclerView.ViewHolder {
        private final TextView logText;

        LogViewHolder(@NonNull View itemView) {
            super(itemView);
            logText = itemView.findViewById(R.id.log_text);
        }

        void bind(ChatMessage message) {
            String m = message.getMessage();
            // Server sends the text already prefixed; add the check only if missing.
            if (m != null && !m.startsWith("✓")) m = "✓  " + m;
            logText.setText(m);
            // Long-press to copy, consistent with the other bubbles.
            itemView.setOnLongClickListener(v -> copyMessageToClipboard(message.getMessage()));
        }
    }

    class ForkContextViewHolder extends RecyclerView.ViewHolder {
        private final View card;
        private final TextView subtitle;

        public ForkContextViewHolder(@NonNull View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.fork_context_card);
            subtitle = itemView.findViewById(R.id.fork_subtitle);
        }

        void bind(ChatMessage message) {
            int count = message.getForkContextMessages() != null
                    ? message.getForkContextMessages().size() : 0;
            String src = message.getForkSourceModelName();
            String sub = count + " message" + (count == 1 ? "" : "s")
                    + (src != null && !src.isEmpty() ? "  ·  from " + src : "")
                    + "  ·  tap to view";
            subtitle.setText(sub);

            View.OnClickListener click = v -> {
                if (forkContextClickListener != null) {
                    forkContextClickListener.onForkContextClick(message);
                }
            };
            card.setOnClickListener(click);
            itemView.setOnClickListener(click);
        }
    }
}

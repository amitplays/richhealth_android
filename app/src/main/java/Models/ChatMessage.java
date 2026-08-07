package Models;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatMessage {
    private String messageId; // MongoDB ID as String
    private long id; // Local database ID
    private String message;
    private boolean isFromAI;
    private long timestamp;
    private String sessionId;
    private boolean isSaved;
    private Date savedAt;

    // Fork-context bubble (transient, client-only, never persisted)
    private boolean isForkContext;
    private List<ChatMessage> forkContextMessages;
    private String forkSourceModelName;
    private String forkSourceSessionId;
    private String forkSourceTitle;
    private String forkSourceModelId;
    private String forkSourceDependentId;

    /** Transient flag — true while this bubble is the placeholder shown
     *  while waiting for the AI reply. Never persisted. */
    private boolean isThinking;
    public boolean isThinking() { return isThinking; }
    public void setThinking(boolean v) { isThinking = v; }

    /** Autofill "log this" card bubble (transient, client-only, never persisted).
     *  When set, this message renders as an editable HealthCard instead of text. */
    private HealthCard healthCard;
    public boolean isHealthCard() { return healthCard != null; }
    public HealthCard getHealthCard() { return healthCard; }
    public void setHealthCard(HealthCard c) { this.healthCard = c; }

    /** Persisted "logged" confirmation (server type == "log"). Rendered as a small
     *  centered info box, e.g. "✓ Logged symptom · Itching". */
    private boolean logEntry;
    public boolean isLogEntry() { return logEntry; }
    public void setLogEntry(boolean v) { this.logEntry = v; }

    /** Transient — true for a just-arrived AI reply so the adapter plays a
     *  typewriter reveal once. Cleared after the animation starts; never persisted. */
    private boolean animateReveal;
    public boolean isAnimateReveal() { return animateReveal; }
    public void setAnimateReveal(boolean v) { this.animateReveal = v; }

    /** Reasoning trace from a thinking-capable model (display-only, per-turn).
     *  Shown as a collapsible "Thinking" row above the reply when present. */
    private String reasoning;
    public String getReasoning() { return reasoning; }
    public void setReasoning(String r) { this.reasoning = r; }
    public boolean hasReasoning() { return reasoning != null && !reasoning.trim().isEmpty(); }

    /** True when Richie saved a new memory from this turn — drives the small
     *  memory icon in the action row (tap shows a short "saved in this chat" note). */
    private boolean memoryAdded;
    public boolean isMemoryAdded() { return memoryAdded; }
    public void setMemoryAdded(boolean v) { this.memoryAdded = v; }

    public boolean isForkContext() { return isForkContext; }
    public void setForkContext(boolean v) { isForkContext = v; }
    public List<ChatMessage> getForkContextMessages() { return forkContextMessages; }
    public void setForkContextMessages(List<ChatMessage> m) { forkContextMessages = m; }
    public String getForkSourceModelName() { return forkSourceModelName; }
    public void setForkSourceModelName(String n) { forkSourceModelName = n; }
    public String getForkSourceSessionId() { return forkSourceSessionId; }
    public void setForkSourceSessionId(String s) { forkSourceSessionId = s; }
    public String getForkSourceTitle() { return forkSourceTitle; }
    public void setForkSourceTitle(String t) { forkSourceTitle = t; }
    public String getForkSourceModelId() { return forkSourceModelId; }
    public void setForkSourceModelId(String m) { forkSourceModelId = m; }
    public String getForkSourceDependentId() { return forkSourceDependentId; }
    public void setForkSourceDependentId(String d) { forkSourceDependentId = d; }

    // Constructor for new messages
    public ChatMessage(String message, boolean isFromAI) {
        this.message = message;
        this.isFromAI = isFromAI;
        this.timestamp = System.currentTimeMillis();
        this.isSaved = false;
    }

    // Constructor for messages from database
    public ChatMessage(long id, String message, boolean isFromAI, long timestamp) {
        this.id = id;
        this.message = message;
        this.isFromAI = isFromAI;
        this.timestamp = timestamp;
        this.isSaved = false;
    }

    // Constructor with saved status (from MongoDB)
    public ChatMessage(String messageId, String message, boolean isFromAI, long timestamp, boolean isSaved, Date savedAt) {
        this.messageId = messageId;
        this.message = message;
        this.isFromAI = isFromAI;
        this.timestamp = timestamp;
        this.isSaved = isSaved;
        this.savedAt = savedAt;
    }

    // Getters and setters
    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isFromAI() {
        return isFromAI;
    }

    public void setFromAI(boolean fromAI) {
        isFromAI = fromAI;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public boolean isSaved() {
        return isSaved;
    }

    public void setSaved(boolean saved) {
        isSaved = saved;
    }

    public Date getSavedAt() {
        return savedAt;
    }

    public void setSavedAt(Date savedAt) {
        this.savedAt = savedAt;
    }

    // Helper method to format time
    public String getFormattedTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }
}
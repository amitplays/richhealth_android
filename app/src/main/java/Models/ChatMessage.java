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

    /** Agentic tool trace + citations for an AI reply (mirrors iOS steps/sources).
     *  Populated from the message's agentSteps + sources arrays; empty for normal
     *  replies. Shown as a collapsible "what Richie checked" row + tappable sources. */
    private final java.util.List<String> agentToolLines = new java.util.ArrayList<>();
    private final java.util.List<String[]> agentSources = new java.util.ArrayList<>(); // [displayTitle, url]

    public void setAgentTrace(org.json.JSONArray steps, org.json.JSONArray sources) {
        agentToolLines.clear();
        agentSources.clear();
        if (steps != null) {
            for (int i = 0; i < steps.length(); i++) {
                org.json.JSONObject s = steps.optJSONObject(i);
                if (s == null || !"tool_start".equals(s.optString("type"))) continue;
                String tool = s.optString("tool", "");
                String q = s.optString("query", "").trim();
                String line;
                switch (tool) {
                    case "search_publications":  line = "Searched research" + (q.isEmpty() ? "" : ": " + q); break;
                    case "web_search":           line = "Searched the web" + (q.isEmpty() ? "" : ": " + q); break;
                    case "fetch_health_records": line = "Checked your " + (q.isEmpty() ? "records" : q) + " log"; break;
                    default:                     line = "Used " + (tool.isEmpty() ? "a tool" : tool);
                }
                agentToolLines.add(line);
            }
        }
        if (sources != null) {
            for (int i = 0; i < sources.length(); i++) {
                org.json.JSONObject src = sources.optJSONObject(i);
                if (src == null) continue;
                String url = src.optString("url", "").trim();
                if (url.isEmpty()) continue;
                String title = src.optString("title", "").trim();
                if (title.isEmpty()) title = url;
                int year = src.optInt("year", 0);
                agentSources.add(new String[]{ year > 0 ? title + " (" + year + ")" : title, url });
            }
        }
    }
    public boolean hasAgentTrace() { return !agentToolLines.isEmpty() || !agentSources.isEmpty(); }
    public java.util.List<String> getAgentToolLines() { return agentToolLines; }
    public java.util.List<String[]> getAgentSources() { return agentSources; }
    public String getAgentTraceLabel() {
        int n = agentSources.size();
        return n == 0 ? "What Richie checked" : "Checked " + n + " source" + (n == 1 ? "" : "s");
    }

    /** True when Richie saved a new memory from this turn — drives the small
     *  memory icon in the action row (tap shows a short "saved in this chat" note). */
    private boolean memoryAdded;
    public boolean isMemoryAdded() { return memoryAdded; }
    public void setMemoryAdded(boolean v) { this.memoryAdded = v; }

    /** Image attached to a USER message (FileStore id from the backend). null for text-only.
     *  Rendered as a placeholder for now; the id is mapped end-to-end. */
    private String imageFileId;
    public String getImageFileId() { return imageFileId; }
    public void setImageFileId(String id) { this.imageFileId = id; }
    public boolean hasImage() { return imageFileId != null && !imageFileId.trim().isEmpty(); }

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
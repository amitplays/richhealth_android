package Models;

public class ChatSession {
    private String sessionId;
    private String title;
    private String lastMessage;
    private int messageCount;
    private long timestamp;
    private long userId;
    private String modelType;
    private String dependentId;

    public ChatSession(String sessionId, String title, String lastMessage, int messageCount, long timestamp, long userId) {
        this.sessionId = sessionId;
        this.title = title;
        this.lastMessage = lastMessage;
        this.messageCount = messageCount;
        this.timestamp = timestamp;
        this.userId = userId;
        this.modelType = "auto";
        this.dependentId = null;
    }

    // Getters and setters
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getLastMessage() { return lastMessage; }
    public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }

    public int getMessageCount() { return messageCount; }
    public void setMessageCount(int messageCount) { this.messageCount = messageCount; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public long getUserId() { return userId; }
    public void setUserId(long userId) { this.userId = userId; }

    public String getModelType() { return modelType; }
    public void setModelType(String modelType) { this.modelType = modelType; }

    public String getDependentId() { return dependentId; }
    public void setDependentId(String dependentId) { this.dependentId = dependentId; }

    // Helper method to format time ago
    public String getTimeAgo() {
        long now = System.currentTimeMillis();
        long seconds = (now - timestamp) / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return days + " days ago";
        } else if (hours > 0) {
            return hours + " hours ago";
        } else if (minutes > 0) {
            return minutes + " minutes ago";
        } else {
            return "Just now";
        }
    }
}
package Models;

import java.util.Date;

public class MedicalData {
    public static final String TYPE_SYMPTOM = "symptom";
    public static final String TYPE_MEASUREMENT = "measurement";
    public static final String TYPE_PERIOD_LOG = "period_log";

    private long id;
    private long userId;
    private String serverId; // MongoDB document ID
    private String type; // "symptom" or "metric"
    private Date recordedAt;
    private Date createdAt;
    private boolean isDeleted;
    private Date deletedAt;
    private boolean shareWithFamily;
    private boolean includeInChat = true;


    // Common constructor
    public MedicalData(String type) {
        this.type = type;
        this.recordedAt = new Date();
        this.createdAt = new Date();
        this.isDeleted = false;
    }

    // Getters and Setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getUserId() { return userId; }
    public void setUserId(long userId) { this.userId = userId; }

    public String getServerId() { return serverId; }
    public void setServerId(String serverId) { this.serverId = serverId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public Date getRecordedAt() { return recordedAt; }
    public void setRecordedAt(Date recordedAt) { this.recordedAt = recordedAt; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public boolean isDeleted() { return isDeleted; }
    public void setDeleted(boolean deleted) { isDeleted = deleted; }

    public Date getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Date deletedAt) { this.deletedAt = deletedAt; }


    public boolean isShareWithFamily() {
        return shareWithFamily;
    }

    public void setShareWithFamily(boolean shareWithFamily) {
        this.shareWithFamily = shareWithFamily;
    }

    public boolean isIncludeInChat() {
        return includeInChat;
    }

    public void setIncludeInChat(boolean includeInChat) {
        this.includeInChat = includeInChat;
    }

    // Symptom specific class
    public static class Symptom extends MedicalData {
        private String name;
        private int severity; // 1-5 scale
        private String duration;
        private String description;

        public Symptom() {
            super(TYPE_SYMPTOM);
        }

        // Getters and Setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public int getSeverity() { return severity; }
        public void setSeverity(int severity) { this.severity = severity; }

        public String getSeverityText() {
            switch (severity) {
                case 1: return "Very Mild";
                case 2: return "Mild";
                case 3: return "Moderate";
                case 4: return "Severe";
                case 5: return "Very Severe";
                default: return "Unknown";
            }
        }

        public String getDuration() { return duration; }
        public void setDuration(String duration) { this.duration = duration; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    // Health Metric specific class
    public static class HealthMetric extends MedicalData {
        private String metricType; // Blood Pressure, Blood Sugar, etc.
        private double value;
        private String unit;
        private String status; // normal, high, low
        private String notes;

        public HealthMetric() {
            super(TYPE_MEASUREMENT);
        }

        // Getters and Setters
        public String getMetricType() { return metricType; }
        public void setMetricType(String metricType) { this.metricType = metricType; }

        public double getValue() { return value; }
        public void setValue(double value) { this.value = value; }

        public String getUnit() { return unit; }
        public void setUnit(String unit) { this.unit = unit; }

        public String getFormattedValue() {
            return value + " " + unit;
        }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        // Auto-calculate status based on common reference ranges
        // This is a simplified example - real implementation would have more comprehensive ranges
        public void calculateStatus() {
            if (metricType.equalsIgnoreCase("Blood Glucose")) {
                if (value < 70) status = "low";
                else if (value > 140) status = "high";
                else status = "normal";
            } else if (metricType.equalsIgnoreCase("Blood Pressure")) {
                // For blood pressure, value might be systolic
                // and we'd need separate fields for diastolic
                if (value < 90) status = "low";
                else if (value > 140) status = "high";
                else status = "normal";
            } else {
                status = "unknown";
            }
        }

        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
    }

    // Period Log specific class
    public static class PeriodLog extends MedicalData {
        private Date startDate;
        private Date endDate;
        private String flowIntensity; // "light", "medium", "heavy"
        private int painLevel; // 1-5 scale
        private String notes;

        public PeriodLog() {
            super(TYPE_PERIOD_LOG);
        }

        public Date getStartDate() { return startDate; }
        public void setStartDate(Date startDate) { this.startDate = startDate; }

        public Date getEndDate() { return endDate; }
        public void setEndDate(Date endDate) { this.endDate = endDate; }

        public String getFlowIntensity() { return flowIntensity; }
        public void setFlowIntensity(String flowIntensity) { this.flowIntensity = flowIntensity; }

        public String getFlowIntensityLabel() {
            if (flowIntensity == null) return "Unknown";
            switch (flowIntensity) {
                case "light": return "Light";
                case "medium": return "Medium";
                case "heavy": return "Heavy";
                default: return "Unknown";
            }
        }

        public int getPainLevel() { return painLevel; }
        public void setPainLevel(int painLevel) { this.painLevel = painLevel; }

        public String getPainLevelText() {
            switch (painLevel) {
                case 1: return "Very Mild";
                case 2: return "Mild";
                case 3: return "Moderate";
                case 4: return "Severe";
                case 5: return "Very Severe";
                default: return "Unknown";
            }
        }

        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
    }
}
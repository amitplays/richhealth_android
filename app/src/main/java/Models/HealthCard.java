package Models;

/**
 * A single AI-proposed "log this" card, parsed from the ```healthlog``` block that
 * Richie appends to a chat reply when the autofill-cards setting is on. Rendered as
 * a collapsible, editable card in the chat and, on "Add", saved through the existing
 * medical-data / medication endpoints. Purely client-side; never persisted locally.
 */
public class HealthCard {

    public static final String KIND_SYMPTOM = "symptom";
    public static final String KIND_MEASUREMENT = "measurement";
    public static final String KIND_MEDICATION = "medication";
    public static final String KIND_PERIOD = "period";

    private String kind = KIND_SYMPTOM;

    // Symptom / measurement share a title.
    private String title = "";

    // Symptom
    private int severity = 3;          // 1-5
    private String duration = "";
    private String description = "";

    // Measurement
    private String value = "";
    private String unit = "";

    // Medication
    private String name = "";
    private String dosage = "";
    private String frequency = "As needed";
    private String purpose = "";

    // Period
    private String startDate = "";     // "YYYY-MM-DD" or "" for today
    private String flowIntensity = "medium";  // light | medium | heavy
    private int painLevel = 3;         // 1-5
    private String notes = "";

    // Transient UI state
    private boolean added = false;
    private boolean dismissed = false;
    private boolean expanded = false;

    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public int getSeverity() { return severity; }
    public void setSeverity(int severity) { this.severity = severity; }

    public String getDuration() { return duration; }
    public void setDuration(String duration) { this.duration = duration; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDosage() { return dosage; }
    public void setDosage(String dosage) { this.dosage = dosage; }

    public String getFrequency() { return frequency; }
    public void setFrequency(String frequency) { this.frequency = frequency; }

    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }

    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }

    public String getFlowIntensity() { return flowIntensity; }
    public void setFlowIntensity(String flowIntensity) { this.flowIntensity = flowIntensity; }

    public int getPainLevel() { return painLevel; }
    public void setPainLevel(int painLevel) { this.painLevel = painLevel; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public boolean isAdded() { return added; }
    public void setAdded(boolean added) { this.added = added; }

    public boolean isDismissed() { return dismissed; }
    public void setDismissed(boolean dismissed) { this.dismissed = dismissed; }

    public boolean isExpanded() { return expanded; }
    public void setExpanded(boolean expanded) { this.expanded = expanded; }

    /** Short label for the collapsed header, e.g. "Log symptom · Itching". */
    public String getHeaderLabel() {
        switch (kind) {
            case KIND_MEASUREMENT:
                return "Log measurement" + (isBlank(title) ? "" : " · " + title);
            case KIND_MEDICATION:
                return "Add medication" + (isBlank(name) ? "" : " · " + name);
            case KIND_PERIOD:
                return "Log period";
            case KIND_SYMPTOM:
            default:
                return "Log symptom" + (isBlank(title) ? "" : " · " + title);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}

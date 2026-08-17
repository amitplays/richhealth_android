package Models;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MedicationModel {
    private int id;
    private String name;
    private String dosage;
    private String frequency;
    private Date startDate;
    private Date endDate;
    private String notes;
    private boolean isActive;
    private String serverId;
    private boolean shareWithFamily;
    private boolean includeInChat = true;

    // New backend fields
    private String purpose;
    private String prescribedBy;
    private String medicationType;
    private String administrationMethod;
    private List<String> sideEffects = new ArrayList<>();
    private double adherenceRate = -1;

    // Reminders (mirror backend remindersEnabled / reminderDays / reminderTimes)
    private boolean remindersEnabled = false;
    private List<Integer> reminderDays = new ArrayList<>();   // 0=Sun..6=Sat, empty = daily
    private List<int[]> reminderTimes = new ArrayList<>();    // each int[]{hour,minute}

    public MedicationModel() {
        // Default constructor
    }

    public MedicationModel(int id, String name, String dosage, String frequency,
                           Date startDate, Date endDate, String notes, boolean isActive) {
        this.id = id;
        this.name = name;
        this.dosage = dosage;
        this.frequency = frequency;
        this.startDate = startDate;
        this.endDate = endDate;
        this.notes = notes;
        this.isActive = isActive;
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDosage() {
        return dosage;
    }

    public void setDosage(String dosage) {
        this.dosage = dosage;
    }

    public String getFrequency() {
        return frequency;
    }

    public void setFrequency(String frequency) {
        this.frequency = frequency;
    }

    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    public Date getEndDate() {
        return endDate;
    }

    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public String getServerId() {
        return serverId;
    }

    public void setServerId(String serverId) {
        this.serverId = serverId;
    }

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

    // New backend field getters and setters
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }

    public String getPrescribedBy() { return prescribedBy; }
    public void setPrescribedBy(String prescribedBy) { this.prescribedBy = prescribedBy; }

    public String getMedicationType() { return medicationType; }
    public void setMedicationType(String medicationType) { this.medicationType = medicationType; }

    public String getAdministrationMethod() { return administrationMethod; }
    public void setAdministrationMethod(String administrationMethod) { this.administrationMethod = administrationMethod; }

    public List<String> getSideEffects() { return sideEffects; }
    public void setSideEffects(List<String> sideEffects) { this.sideEffects = sideEffects; }
    public void addSideEffect(String sideEffect) { this.sideEffects.add(sideEffect); }

    public double getAdherenceRate() { return adherenceRate; }
    public void setAdherenceRate(double adherenceRate) { this.adherenceRate = adherenceRate; }

    // Reminder fields (mirrors backend remindersEnabled / reminderDays / reminderTimes).
    // reminderDays: 0=Sun..6=Sat, empty = every day. reminderTimes: each int[]{hour,minute}.
    public boolean isRemindersEnabled() { return remindersEnabled; }
    public void setRemindersEnabled(boolean remindersEnabled) { this.remindersEnabled = remindersEnabled; }

    public List<Integer> getReminderDays() { return reminderDays; }
    public void setReminderDays(List<Integer> reminderDays) {
        this.reminderDays = reminderDays != null ? reminderDays : new ArrayList<>();
    }

    public List<int[]> getReminderTimes() { return reminderTimes; }
    public void setReminderTimes(List<int[]> reminderTimes) {
        this.reminderTimes = reminderTimes != null ? reminderTimes : new ArrayList<>();
    }

    // Helper method to determine if medication is current
    public boolean isCurrent() {
        if (!isActive) {
            return false;
        }

        Date now = new Date();
        return startDate != null &&
                (endDate == null || now.before(endDate) || now.equals(endDate));
    }

    // Helper method to determine medication duration
    public String getDurationText() {
        if (startDate == null) {
            return "No start date";
        }

        if (endDate == null) {
            return "Since " + android.text.format.DateFormat.format("MMM dd, yyyy", startDate);
        }

        return android.text.format.DateFormat.format("MMM dd, yyyy", startDate) +
                " to " +
                android.text.format.DateFormat.format("MMM dd, yyyy", endDate);
    }
}
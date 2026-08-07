package Models;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
public class UserProfile {
    // Basic Information
    private long id;
    private String name;
    private String email;
    private Date dateOfBirth;
    private String gender;
    private boolean isMetric;

    // Physical Measurements
    private double height;
    private double weight;
    private double targetWeight;
    private double neckCircumference;
    private double waistCircumference;
    private double hipCircumference;

    // Health Metrics
    private int restingHeartRate;
    private String bloodType;
    private int systolicBP;
    private int diastolicBP;
    private double bodyFatPercentage;

    // Fitness Level
    private int activityLevel;  // 1-5 scale
    private int fitnessLevel;   // 1-5 scale
    private String exerciseFrequency;
    private List<String> preferredExerciseTypes = new ArrayList<>();
    private int typicalWorkoutDuration;

    // Health Goals
    private String primaryGoal;
    private List<String> specificGoals = new ArrayList<>();
    private double weeklyGoal;

    // Medical Information
    private List<String> medicalConditions = new ArrayList<>();
    private List<String> medications = new ArrayList<>();
    private List<String> allergies = new ArrayList<>();
    private String emergencyContact;

    // Lifestyle Factors
    private String occupationType;
    private int sleepHours;
    private int stressLevel;    // 1-5 scale
    private boolean smoker;
    private int smokingLevel;   // 0-4 scale (added field)
    private String smokingFrequency;
    private String alcoholConsumption;
    private int alcoholLevel;
    private String caffeineHabit;
    private String screenTimeBeforeBed;
    private String sunExposure;
    private List<String> familyHistory = new ArrayList<>();
    private List<String> familyHistoryRelatives = new ArrayList<>();

    // Conditional follow-ups
    private String smokingStatus = "";       // never / ex / social / occasional / regular
    private String smokingDuration = "";
    private String cigarettesPerDay = "";
    private String lastSmoked = "";
    private String drinksPerWeek = "";
    private String conditionsDiagnosed = "";
    private String conditionsMedicated = "";

    // Predictive extras
    private String ethnicity = "";
    private String recentWeightChange = "";
    private List<String> medicationCategories = new ArrayList<>();

    // Dietary Information
    private List<String> dietaryRestrictions = new ArrayList<>();
    private String dietType;
    private int mealsPerDay;
    private int waterIntake;
    private List<String> supplements = new ArrayList<>();

    // Menstrual / Reproductive Health
    private String menstrualStatus = "not_applicable";
    private int averageCycleLength = 0;
    private int averagePeriodLength = 0;
    private List<String> menstrualSymptoms = new ArrayList<>();
    private String contraceptionMethod = "";
    private String pregnancyStatus = "not_applicable";

    // App Preferences
    private boolean receiveNotifications = true;
    private String preferredWorkoutTime;
    private int workoutReminders;
    private boolean shareProgress;

    // AI / Chat preferences — synced with the server's user.aiPreferences object.
    // Not stored in local SQLite; always refreshed from the server on load, so
    // defaults here match the backend schema defaults.
    private String aiTone = "balanced";            // balanced | warm | direct
    private String aiReplyLength = "balanced";     // concise | balanced | detailed
    private String aiCustomInstructions = "";      // standing instructions for Richie
    private boolean aiSaveMemories = true;          // allow Richie to remember chat facts
    private boolean aiImproveModel = true;          // consent to improve RichHealth
    private boolean aiAutofillCards = false;        // let Richie offer prefilled "log this" cards
    private boolean aiShowThinking = false;         // run chat on the thinking model + show its reasoning

    // Timestamps
    private Date createdAt;
    private Date lastUpdated;
    private Date lastWorkout;
    private Date lastWeightIn;

    // Authentication
    private String password;
    private String confirmPassword; // Transient field for registration
    private boolean isLoggedIn;
    private String authToken;
    private Date lastLogin;
    private String phoneNumber;

    // Location
    private String location;

    private List<RelationshipRequest> incomingRequests = new ArrayList<>();
    private List<RelationshipRequest> sentRequests = new ArrayList<>();

    // Default Constructor
    public UserProfile() {
        this.createdAt = new Date();
        this.lastUpdated = new Date();
    }

    public List<String> getMedicalConditions() {
        return medicalConditions;
    }

    public void setMedicalConditions(List<String> medicalConditions) {
        this.medicalConditions = medicalConditions;
    }

    public List<String> getMedications() {
        return medications;
    }

    public void setMedications(List<String> medications) {
        this.medications = medications;
    }

    public List<String> getAllergies() {
        return allergies;
    }

    public void setAllergies(List<String> allergies) {
        this.allergies = allergies;
    }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    // Getters and Setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public Date getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(Date dateOfBirth) { this.dateOfBirth = dateOfBirth; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public boolean isMetric() { return isMetric; }
    public void setMetric(boolean metric) { isMetric = metric; }

    public double getHeight() { return height; }
    public void setHeight(double height) { this.height = height; }

    public double getWeight() { return weight; }
    public void setWeight(double weight) {
        this.weight = weight;
        this.lastWeightIn = new Date();
    }

    public double getTargetWeight() { return targetWeight; }
    public void setTargetWeight(double targetWeight) { this.targetWeight = targetWeight; }

    public double getWaistCircumference() { return waistCircumference; }
    public void setWaistCircumference(double waistCircumference) { this.waistCircumference = waistCircumference; }

    public int getRestingHeartRate() { return restingHeartRate; }
    public void setRestingHeartRate(int restingHeartRate) { this.restingHeartRate = restingHeartRate; }

    public String getBloodType() { return bloodType; }
    public void setBloodType(String bloodType) { this.bloodType = bloodType; }

    public String getMenstrualStatus() { return menstrualStatus; }
    public void setMenstrualStatus(String menstrualStatus) { this.menstrualStatus = menstrualStatus; }
    public int getAverageCycleLength() { return averageCycleLength; }
    public void setAverageCycleLength(int averageCycleLength) { this.averageCycleLength = averageCycleLength; }
    public int getAveragePeriodLength() { return averagePeriodLength; }
    public void setAveragePeriodLength(int averagePeriodLength) { this.averagePeriodLength = averagePeriodLength; }
    public List<String> getMenstrualSymptoms() { return menstrualSymptoms; }
    public void setMenstrualSymptoms(List<String> menstrualSymptoms) { this.menstrualSymptoms = menstrualSymptoms; }
    public String getContraceptionMethod() { return contraceptionMethod; }
    public void setContraceptionMethod(String contraceptionMethod) { this.contraceptionMethod = contraceptionMethod; }
    public String getPregnancyStatus() { return pregnancyStatus; }
    public void setPregnancyStatus(String pregnancyStatus) { this.pregnancyStatus = pregnancyStatus; }

    public int getSystolicBP() { return systolicBP; }
    public void setSystolicBP(int systolicBP) { this.systolicBP = systolicBP; }

    public int getDiastolicBP() { return diastolicBP; }
    public void setDiastolicBP(int diastolicBP) { this.diastolicBP = diastolicBP; }

    public int getActivityLevel() { return activityLevel; }
    public void setActivityLevel(int activityLevel) { this.activityLevel = activityLevel; }

    public String getPrimaryGoal() { return primaryGoal; }
    public void setPrimaryGoal(String primaryGoal) { this.primaryGoal = primaryGoal; }

    public double getWeeklyGoal() { return weeklyGoal; }
    public void setWeeklyGoal(double weeklyGoal) { this.weeklyGoal = weeklyGoal; }

    public int getSleepHours() { return sleepHours; }
    public void setSleepHours(int sleepHours) { this.sleepHours = sleepHours; }

    public String getDietType() { return dietType; }
    public void setDietType(String dietType) { this.dietType = dietType; }

    public boolean isReceiveNotifications() { return receiveNotifications; }
    public void setReceiveNotifications(boolean receiveNotifications) {
        this.receiveNotifications = receiveNotifications;
    }

    public boolean isShareProgress() { return shareProgress; }
    public void setShareProgress(boolean shareProgress) { this.shareProgress = shareProgress; }

    // AI / Chat preference accessors
    public String getAiTone() { return aiTone; }
    public void setAiTone(String aiTone) { this.aiTone = aiTone; }

    public String getAiReplyLength() { return aiReplyLength; }
    public void setAiReplyLength(String aiReplyLength) { this.aiReplyLength = aiReplyLength; }

    public String getAiCustomInstructions() { return aiCustomInstructions; }
    public void setAiCustomInstructions(String aiCustomInstructions) { this.aiCustomInstructions = aiCustomInstructions; }

    public boolean isAiSaveMemories() { return aiSaveMemories; }
    public void setAiSaveMemories(boolean aiSaveMemories) { this.aiSaveMemories = aiSaveMemories; }

    public boolean isAiImproveModel() { return aiImproveModel; }
    public void setAiImproveModel(boolean aiImproveModel) { this.aiImproveModel = aiImproveModel; }

    public boolean isAiAutofillCards() { return aiAutofillCards; }
    public void setAiAutofillCards(boolean aiAutofillCards) { this.aiAutofillCards = aiAutofillCards; }

    public boolean isAiShowThinking() { return aiShowThinking; }
    public void setAiShowThinking(boolean aiShowThinking) { this.aiShowThinking = aiShowThinking; }

    public Date getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(Date lastUpdated) { this.lastUpdated = lastUpdated; }

    // List Getters and Setters
    public List<String> getPreferredExerciseTypes() { return preferredExerciseTypes; }
    public void setPreferredExerciseTypes(List<String> types) { this.preferredExerciseTypes = types; }

    public List<String> getSpecificGoals() { return specificGoals; }
    public void setSpecificGoals(List<String> goals) { this.specificGoals = goals; }

    // New getter and setter for smokingLevel
    public int getSmokingLevel() { return smokingLevel; }
    public void setSmokingLevel(int smokingLevel) { this.smokingLevel = smokingLevel; }

    public int getStressLevel() { return stressLevel; }
    public void setStressLevel(int stressLevel) { this.stressLevel = stressLevel; }

    public String getOccupationType() { return occupationType; }
    public void setOccupationType(String occupationType) { this.occupationType = occupationType; }

    public int getMealsPerDay() { return mealsPerDay; }
    public void setMealsPerDay(int mealsPerDay) { this.mealsPerDay = mealsPerDay; }

    // Getters and setters for smoker and alcoholConsumption (already existed)
    public boolean isSmoker() { return smoker; }
    public void setSmoker(boolean smoker) { this.smoker = smoker; }

    public String getAlcoholConsumption() { return alcoholConsumption; }
    public void setAlcoholConsumption(String alcoholConsumption) { this.alcoholConsumption = alcoholConsumption; }

    public int getAlcoholLevel() { return alcoholLevel; }
    public void setAlcoholLevel(int alcoholLevel) { this.alcoholLevel = alcoholLevel; }

    public String getSmokingFrequency() { return smokingFrequency; }
    public void setSmokingFrequency(String smokingFrequency) { this.smokingFrequency = smokingFrequency; }

    public String getCaffeineHabit() { return caffeineHabit; }
    public void setCaffeineHabit(String caffeineHabit) { this.caffeineHabit = caffeineHabit; }

    public String getScreenTimeBeforeBed() { return screenTimeBeforeBed; }
    public void setScreenTimeBeforeBed(String screenTimeBeforeBed) { this.screenTimeBeforeBed = screenTimeBeforeBed; }

    public String getSunExposure() { return sunExposure; }
    public void setSunExposure(String sunExposure) { this.sunExposure = sunExposure; }

    public List<String> getFamilyHistory() { return familyHistory; }
    public void setFamilyHistory(List<String> familyHistory) { this.familyHistory = familyHistory; }

    public List<String> getFamilyHistoryRelatives() { return familyHistoryRelatives; }
    public void setFamilyHistoryRelatives(List<String> familyHistoryRelatives) { this.familyHistoryRelatives = familyHistoryRelatives; }

    public String getSmokingStatus() { return smokingStatus; }
    public void setSmokingStatus(String smokingStatus) { this.smokingStatus = smokingStatus; }

    public String getSmokingDuration() { return smokingDuration; }
    public void setSmokingDuration(String smokingDuration) { this.smokingDuration = smokingDuration; }

    public String getCigarettesPerDay() { return cigarettesPerDay; }
    public void setCigarettesPerDay(String cigarettesPerDay) { this.cigarettesPerDay = cigarettesPerDay; }

    public String getLastSmoked() { return lastSmoked; }
    public void setLastSmoked(String lastSmoked) { this.lastSmoked = lastSmoked; }

    public String getDrinksPerWeek() { return drinksPerWeek; }
    public void setDrinksPerWeek(String drinksPerWeek) { this.drinksPerWeek = drinksPerWeek; }

    public String getConditionsDiagnosed() { return conditionsDiagnosed; }
    public void setConditionsDiagnosed(String conditionsDiagnosed) { this.conditionsDiagnosed = conditionsDiagnosed; }

    public String getConditionsMedicated() { return conditionsMedicated; }
    public void setConditionsMedicated(String conditionsMedicated) { this.conditionsMedicated = conditionsMedicated; }

    public String getEthnicity() { return ethnicity; }
    public void setEthnicity(String ethnicity) { this.ethnicity = ethnicity; }

    public String getRecentWeightChange() { return recentWeightChange; }
    public void setRecentWeightChange(String recentWeightChange) { this.recentWeightChange = recentWeightChange; }

    public List<String> getMedicationCategories() { return medicationCategories; }
    public void setMedicationCategories(List<String> medicationCategories) { this.medicationCategories = medicationCategories; }

    public int getWaterIntake() { return waterIntake; }
    public void setWaterIntake(int waterIntake) { this.waterIntake = waterIntake; }

    public int calculateAge() {
        return calculateAge(dateOfBirth);
    }

    // Authentication getters/setters
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }

    public boolean isLoggedIn() { return isLoggedIn; }
    public void setLoggedIn(boolean loggedIn) { isLoggedIn = loggedIn; }

    public String getAuthToken() { return authToken; }
    public void setAuthToken(String authToken) { this.authToken = authToken; }

    public Date getLastLogin() { return lastLogin; }
    public void setLastLogin(Date lastLogin) { this.lastLogin = lastLogin; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    // Helper Methods
    public double getBMI() {
        if (height <= 0) return 0;
        double heightInMeters = isMetric ? height / 100 : height / 39.37;
        double weightInKg = isMetric ? weight : weight / 2.20462;
        return weightInKg / (heightInMeters * heightInMeters);
    }

    public String getBMICategory() {
        double bmi = getBMI();
        if (bmi < 18.5) return "Underweight";
        if (bmi < 24.9) return "Normal";
        if (bmi < 29.9) return "Overweight";
        return "Obese";
    }

    public double getBMR() {
        double weightInKg = isMetric ? weight : weight / 2.20462;
        double heightInCm = isMetric ? height : height * 2.54;
        int age = calculateAge(dateOfBirth);

        if ("male".equalsIgnoreCase(gender)) {
            return (10 * weightInKg) + (6.25 * heightInCm) - (5 * age) + 5;
        } else {
            return (10 * weightInKg) + (6.25 * heightInCm) - (5 * age) - 161;
        }
    }

    public String getFormattedBP() {
        return systolicBP + "/" + diastolicBP;
    }

    private int calculateAge(Date birthDate) {
        if (birthDate == null) return 0;
        Date now = new Date();
        long diffInMillis = now.getTime() - birthDate.getTime();
        return (int) (diffInMillis / (1000L * 60 * 60 * 24 * 365.25));
    }

    public String getFormattedWeight() {
        if (isMetric) {
            return String.format("%.1f kg", weight);
        }
        return String.format("%.1f lbs", weight * 2.20462);
    }

    public String getFormattedHeight() {
        if (isMetric) {
            return String.format("%.1f cm", height);
        }
        int feet = (int)(height / 30.48);
        int inches = (int)((height % 30.48) / 2.54);
        return String.format("%d'%d\"", feet, inches);
    }

    // Validation methods
    public boolean isValidEmail() {
        String emailRegex = "^[A-Za-z0-9+_.-]+@(.+)$";
        return email != null && email.matches(emailRegex);
    }

    public boolean isValidPassword() {
        // At least 8 chars, 1 number, 1 uppercase, 1 lowercase, 1 special char
        String passwordRegex = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!])(?=\\S+$).{8,}$";
        return password != null && password.matches(passwordRegex);
    }

    public boolean isValidPhoneNumber() {
        String phoneRegex = "^\\+?[1-9]\\d{1,14}$";
        return phoneNumber != null && phoneNumber.matches(phoneRegex);
    }

    public boolean passwordsMatch() {
        return password != null && password.equals(confirmPassword);
    }

    public String getActivityLevelText() {
        switch (activityLevel) {
            case 1: return "Sedentary";
            case 2: return "Lightly Active";
            case 3: return "Moderately Active";
            case 4: return "Very Active";
            case 5: return "Extremely Active";
            default: return "Not Set";
        }
    }

    public static class MedicalReport {
        private String id;
        private String serverReportId;
        private String fileName;
        private String fileType;
        private Date uploadDate;
        private String reportType;
        private String filePath; // Local file path or URI
        private String aiAnalysis;
        private Map<String, Object> extractedData;
        private String status;

        // Constructors
        public MedicalReport() {
            this.uploadDate = new Date();
            this.extractedData = new HashMap<>();
            this.status = "uploaded";
        }

        public MedicalReport(String fileName, String fileType, String reportType, String filePath) {
            this.fileName = fileName;
            this.fileType = fileType;
            this.reportType = reportType;
            this.filePath = filePath;
            this.uploadDate = new Date();
            this.extractedData = new HashMap<>();
            this.status = "uploaded";
        }

        // Getters and Setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getServerReportId() { return serverReportId; }
        public void setServerReportId(String serverReportId) { this.serverReportId = serverReportId; }

        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }

        public String getFileType() { return fileType; }
        public void setFileType(String fileType) { this.fileType = fileType; }

        public Date getUploadDate() { return uploadDate; }
        public void setUploadDate(Date uploadDate) { this.uploadDate = uploadDate; }

        public String getReportType() { return reportType; }
        public void setReportType(String reportType) { this.reportType = reportType; }

        public String getFilePath() { return filePath; }
        public void setFilePath(String filePath) { this.filePath = filePath; }

        public String getAiAnalysis() { return aiAnalysis; }
        public void setAiAnalysis(String aiAnalysis) { this.aiAnalysis = aiAnalysis; }

        public Map<String, Object> getExtractedData() { return extractedData; }
        public void setExtractedData(Map<String, Object> extractedData) { this.extractedData = extractedData; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        // Method to add extracted data
        public void addExtractedData(String key, Object value) {
            if (extractedData == null) {
                extractedData = new HashMap<>();
            }
            extractedData.put(key, value);
        }
    }


    public static class RelationshipRequest {
        private String email;
        private String relationship;
        private String status;
        private String name;
        private String userId;
        private boolean isPro;
        private String proSource; // "self", "family_member", "none"
        private boolean isCoveredByMyPlan;

        public RelationshipRequest() {
        }

        public RelationshipRequest(String email, String relationship, String status) {
            this.email = email;
            this.relationship = relationship;
            this.status = status;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getRelationship() { return relationship; }
        public void setRelationship(String relationship) { this.relationship = relationship; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }

        public boolean isPro() { return isPro; }
        public void setPro(boolean pro) { isPro = pro; }

        public String getProSource() { return proSource; }
        public void setProSource(String proSource) { this.proSource = proSource; }

        public boolean isCoveredByMyPlan() { return isCoveredByMyPlan; }
        public void setCoveredByMyPlan(boolean coveredByMyPlan) { isCoveredByMyPlan = coveredByMyPlan; }
    }

    // Add getters and setters for the new lists
    public List<RelationshipRequest> getIncomingRequests() {
        return incomingRequests;
    }

    public void setIncomingRequests(List<RelationshipRequest> incomingRequests) {
        this.incomingRequests = incomingRequests;
    }

    public List<RelationshipRequest> getSentRequests() {
        return sentRequests;
    }

    public void setSentRequests(List<RelationshipRequest> sentRequests) {
        this.sentRequests = sentRequests;
    }
}
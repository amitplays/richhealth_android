package Models;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class OnboardingData {
    // Step: Account
    public String name = "";
    public String email = "";
    public String password = "";
    public String confirmPassword = "";
    public String phoneNumber = "";

    // Step: Personal
    public Date dateOfBirth = null;
    public String gender = "";
    public String location = "";
    public String ethnicity = "";            // ancestry — strong risk stratifier

    // Predictive extras
    public String recentWeightChange = "";   // Gained / Lost / Stable / Not sure
    public List<String> medicationCategories = new ArrayList<>(); // regular meds by type

    // Step: Menstrual Health (conditional — Female/Other only)
    public String menstrualStatus = "not_applicable";
    public int averageCycleLength = 28;
    public int averagePeriodLength = 5;
    public List<String> menstrualSymptoms = new ArrayList<>();
    public String contraceptionMethod = "";
    public String pregnancyStatus = "not_applicable";

    // Step: Body
    public double heightCm = 170;
    public double weightKg = 70;
    // Optional — waist circumference in cm. 0 = not provided (skipped).
    // Strong predictor of metabolic/diabetes risk (used in predictive health).
    public double waistCircumferenceCm = 0;

    // Step: Goal
    public String primaryGoal = "Maintain Health";
    // Multi-select goals (primaryGoal kept as the first selection for back-compat).
    public List<String> specificGoals = new ArrayList<>();

    // Step: Activity Level (split from Occupation)
    public int activityLevel = 2;
    // Step: Occupation
    public String occupationType = "";

    // Step: Diet Type
    public String dietType = "Regular";
    // Step: Meals + Water
    public int mealsPerDay = 3;
    public int waterIntake = 6;              // glasses per day

    // Step: Sleep
    public int sleepHours = 8;
    // Step: Stress
    public int stressLevel = 2;
    // Step: Screen time before bed
    public String screenTimeBeforeBed = "moderate"; // low / moderate / high / very_high

    // Step: Habits (smoking + alcohol + caffeine bundled)
    public boolean smoker = false;
    public int smokingLevel = 0;
    public String smokingFrequency = "Non-smoker";
    // never / ex / social / occasional / regular — kept so we can tell an
    // ex-smoker from a never-smoker and show the right follow-ups.
    public String smokingStatus = "never";
    public String alcoholConsumption = "None";
    public int alcoholLevel = 0;
    public String caffeineHabit = "none";    // none / tea / coffee / both / energy_drinks

    // Conditional follow-ups (only asked when relevant)
    public String smokingDuration = "";      // how long they've smoked
    public String cigarettesPerDay = "";     // typical amount per day
    public String lastSmoked = "";           // when they last smoked (covers ex-smokers)
    public String drinksPerWeek = "";        // typical alcoholic drinks per week
    public String conditionsDiagnosed = "";  // how long since first diagnosed
    public String conditionsMedicated = "";  // on medication for condition(s)?

    // Step: Family Health Story
    public List<String> familyHistory = new ArrayList<>();
    public List<String> familyHistoryRelatives = new ArrayList<>(); // parent / grandparent / sibling

    // Step: Allergies (replaces chronicComplaints — that data is now continuously
    // captured by the periodic Health Check-In, making it redundant at signup)
    public List<String> allergies = new ArrayList<>();

    // Step: Sun exposure
    public String sunExposure = "moderate";  // low / moderate / high

    // Step: Medical (blood type + conditions)
    public String bloodType = "";
    public List<String> medicalConditions = new ArrayList<>();
}

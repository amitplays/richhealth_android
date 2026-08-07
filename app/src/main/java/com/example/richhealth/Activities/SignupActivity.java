package com.example.richhealth.Activities;
import Utils.Utilities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.example.richhealth.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import Database.DatabaseHelper;
import Models.UserProfile;
import Utils.ApiConfig;

public class SignupActivity extends AppCompatActivity {

    private static final String TAG = "SignupActivity";
    private static final String API_URL = ApiConfig.BASE_URL + "/api/auth/signup"; // For emulator testing

    // UI Components
    private MaterialButton nextButton;
    private MaterialButton backButton;
    private MaterialButton submitButton;
    private View[] stepIndicators;
    private CircularProgressIndicator progressIndicator;

    // Form Fields
    private TextInputLayout emailLayout, passwordLayout, confirmPasswordLayout, nameLayout;
    private TextInputEditText emailInput, passwordInput, confirmPasswordInput, nameInput;
    private TextInputEditText dobInput;
    private AutoCompleteTextView genderInput;
    private TextInputLayout heightLayout, weightLayout;
    private TextInputEditText heightInput, weightInput;
    private AutoCompleteTextView activityLevelInput, dietTypeInput;

    // Add these field declarations to the class variables section
    private TextInputLayout phoneNumberLayout, locationLayout, bloodTypeLayout;
    private TextInputEditText phoneNumberInput, locationInput;
    private AutoCompleteTextView bloodTypeInput;
    private TextInputLayout sleepHoursLayout, primaryGoalLayout;
    private TextInputEditText sleepHoursInput;
    private AutoCompleteTextView primaryGoalInput;
    private TextInputLayout systolicBPLayout, diastolicBPLayout, restingHeartRateLayout;
    private TextInputEditText systolicBPInput, diastolicBPInput, restingHeartRateInput;

    private SeekBar smokingSeekBar, alcoholSeekBar;
    private TextView smokingText, alcoholText;

    private static final int MAX_STEPS = 4;
    private int currentStep = 0;
    private boolean isSubmitting = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        initializeViews();
        setupStepIndicators();
        setupValidation();
        setupDropdowns();
        setupDatePicker();
        updateStepVisibility();
    }

    private void initializeViews() {

        // Add these to initializeViews() method
        phoneNumberLayout = findViewById(R.id.phone_number_layout);
        phoneNumberInput = findViewById(R.id.phone_number_input);
        locationLayout = findViewById(R.id.location_layout);
        locationInput = findViewById(R.id.location_input);
        bloodTypeLayout = findViewById(R.id.blood_type_layout);
        bloodTypeInput = findViewById(R.id.blood_type_input);
        sleepHoursLayout = findViewById(R.id.sleep_hours_layout);
        sleepHoursInput = findViewById(R.id.sleep_hours_input);
        primaryGoalLayout = findViewById(R.id.primary_goal_layout);
        primaryGoalInput = findViewById(R.id.primary_goal_input);
        systolicBPLayout = findViewById(R.id.systolic_bp_layout);
        systolicBPInput = findViewById(R.id.systolic_bp_input);
        diastolicBPLayout = findViewById(R.id.diastolic_bp_layout);
        diastolicBPInput = findViewById(R.id.diastolic_bp_input);
        restingHeartRateLayout = findViewById(R.id.resting_heart_rate_layout);
        restingHeartRateInput = findViewById(R.id.resting_heart_rate_input);
        // Add these to initializeViews() method
        smokingSeekBar = findViewById(R.id.smoking_seekbar);
        smokingText = findViewById(R.id.smoking_text);
        alcoholSeekBar = findViewById(R.id.alcohol_seekbar);
        alcoholText = findViewById(R.id.alcohol_text);


        // Buttons
        nextButton = findViewById(R.id.next_button);
        backButton = findViewById(R.id.back_button);
        submitButton = findViewById(R.id.submit_button);
        progressIndicator = findViewById(R.id.progress_indicator);

        // "Already have an account? Log In" — go to the login screen.
        View loginLink = findViewById(R.id.login_link);
        if (loginLink != null) {
            loginLink.setOnClickListener(v -> {
                startActivity(new Intent(SignupActivity.this, LoginActivity.class));
                finish();
            });
        }

        // Step 1: Account Info
        emailLayout = findViewById(R.id.email_layout);
        emailInput = findViewById(R.id.email_input);
        passwordLayout = findViewById(R.id.password_layout);
        passwordInput = findViewById(R.id.password_input);
        confirmPasswordLayout = findViewById(R.id.confirm_password_layout);
        confirmPasswordInput = findViewById(R.id.confirm_password_input);

        // Step 2: Personal Info
        nameLayout = findViewById(R.id.name_layout);
        nameInput = findViewById(R.id.name_input);
        dobInput = findViewById(R.id.dob_input);
        genderInput = findViewById(R.id.gender_input);

        // Step 3: Physical Measurements
        heightLayout = findViewById(R.id.height_layout);
        heightInput = findViewById(R.id.height_input);
        weightLayout = findViewById(R.id.weight_layout);
        weightInput = findViewById(R.id.weight_input);

        // Step 4: Lifestyle & Goals
        activityLevelInput = findViewById(R.id.activity_level_input);
        dietTypeInput = findViewById(R.id.diet_type_input);

        // Set up seekbar listeners
        smokingSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateSmokingText(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Not needed
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Not needed
            }
        });

        alcoholSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateAlcoholText(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Not needed
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Not needed
            }
        });

        // Set up button listeners
        nextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                moveToNextStep();
            }
        });

        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                moveToPreviousStep();
            }
        });

        submitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (validateCurrentStep()) {
                    isSubmitting = true;
                    submitSignupForm();
                }
            }
        });
    }

    private void setupDropdowns() {
        // Gender dropdown
        String[] genders = new String[]{"Male", "Female", "Other"};
        ArrayAdapter<String> genderAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, genders);
        genderInput.setAdapter(genderAdapter);

        // Activity level dropdown
        String[] activityLevels = new String[]{
                "Sedentary", "Lightly Active", "Moderately Active",
                "Very Active", "Extremely Active"
        };
        ArrayAdapter<String> activityAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, activityLevels);
        activityLevelInput.setAdapter(activityAdapter);

        // Set default selection for activity level to prevent null values
        activityLevelInput.setText("Lightly Active", false);

        // Diet type dropdown
        String[] dietTypes = new String[]{
                "Regular", "Vegetarian", "Vegan", "Keto",
                "Paleo", "Mediterranean", "Other"
        };
        ArrayAdapter<String> dietAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, dietTypes);
        dietTypeInput.setAdapter(dietAdapter);

        // Set default selection for diet type to prevent null values
        dietTypeInput.setText("Regular", false);

        String[] bloodTypes = new String[]{"A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"};
        ArrayAdapter<String> bloodTypeAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, bloodTypes);
        bloodTypeInput.setAdapter(bloodTypeAdapter);

// Primary goal dropdown
        String[] primaryGoals = new String[]{
                "Weight Loss", "Muscle Gain", "Improve Fitness", "Maintain Health",
                "Increase Strength", "Improve Flexibility", "Sports Performance"
        };
        ArrayAdapter<String> primaryGoalAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, primaryGoals);
        primaryGoalInput.setAdapter(primaryGoalAdapter);

// Set default selection
        primaryGoalInput.setText("Maintain Health", false);

    }

    private void setupValidation() {
        // Email validation
        emailInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                validateEmail();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Password validation
        TextWatcher passwordWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                validatePassword();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        };
        passwordInput.addTextChangedListener(passwordWatcher);
        confirmPasswordInput.addTextChangedListener(passwordWatcher);
    }

    private void setupDatePicker() {
        MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select date of birth")
                .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                .build();

        dobInput.setOnClickListener(v -> datePicker.show(getSupportFragmentManager(), "DATE_PICKER"));

        datePicker.addOnPositiveButtonClickListener(selection -> {
            SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy", Locale.getDefault());
            dobInput.setText(sdf.format(new Date(selection)));
        });
    }

    private void setupStepIndicators() {
        stepIndicators = new View[]{
                findViewById(R.id.step_1),
                findViewById(R.id.step_2),
                findViewById(R.id.step_3),
                findViewById(R.id.step_4)
        };
        updateStepIndicators(0);
    }

    private void updateStepIndicators(int currentStep) {
        for (int i = 0; i < stepIndicators.length; i++) {
            stepIndicators[i].setBackgroundColor(
                    getResources().getColor(i <= currentStep ?
                            android.R.color.holo_orange_dark :
                            android.R.color.darker_gray)
            );
        }
    }

    private void updateStepVisibility() {
        // Get references to all step layouts
        LinearLayout step1Layout = findViewById(R.id.step1_layout);
        LinearLayout step2Layout = findViewById(R.id.step2_layout);
        LinearLayout step3Layout = findViewById(R.id.step3_layout);
        LinearLayout step4Layout = findViewById(R.id.step4_layout);

        // Hide all steps first
        step1Layout.setVisibility(View.GONE);
        step2Layout.setVisibility(View.GONE);
        step3Layout.setVisibility(View.GONE);
        step4Layout.setVisibility(View.GONE);

        // Show only current step
        switch (currentStep) {
            case 0:
                step1Layout.setVisibility(View.VISIBLE);
                break;
            case 1:
                step2Layout.setVisibility(View.VISIBLE);
                break;
            case 2:
                step3Layout.setVisibility(View.VISIBLE);
                break;
            case 3:
                step4Layout.setVisibility(View.VISIBLE);
                break;
        }

        // Update button states
        if (currentStep == MAX_STEPS - 1) {
            nextButton.setVisibility(View.GONE);
            submitButton.setVisibility(View.VISIBLE);
        } else {
            nextButton.setVisibility(View.VISIBLE);
            submitButton.setVisibility(View.GONE);
        }

        if (currentStep > 0) {
            backButton.setVisibility(View.VISIBLE);
        } else {
            backButton.setVisibility(View.GONE);
        }

        updateStepIndicators(currentStep);

        // Debug logging to verify button state
        Log.d(TAG, "Current step: " + currentStep + ", Back button visibility: " +
                (backButton.getVisibility() == View.VISIBLE ? "VISIBLE" : "GONE"));
    }

    private void moveToNextStep() {
        if (isSubmitting) {
            return;
        }

        if (!validateCurrentStep()) {
            Utilities.toast(this, "Please fill all required fields correctly");
            return;
        }

        if (currentStep < MAX_STEPS - 1) {
            currentStep++;
            updateStepVisibility();
        }
    }

    private void moveToPreviousStep() {
        Log.d(TAG, "moveToPreviousStep called");

        if (isSubmitting) {
            return;
        }

        if (currentStep > 0) {
            currentStep--;
            updateStepVisibility();
        } else {
            Utils.DialogUtils.showConfirmDialog(this,
                    "Exit Signup",
                    "Are you sure you want to exit? All progress will be lost.",
                    "Yes", "No", true,
                    this::finish);
        }
    }

    private boolean validateCurrentStep() {
        switch (currentStep) {
            case 0:
                return validateAccountInfo();
            case 1:
                return validatePersonalInfo();
            case 2:
                return validatePhysicalMeasurements();
            case 3:
                return validateLifestyleAndGoals();
            default:
                return true;
        }
    }

    private boolean validateAccountInfo() {
        boolean isValid = validateEmail();
        isValid = validatePassword() && isValid;
        return isValid;
    }

    private boolean validateEmail() {
        String email = emailInput.getText().toString().trim();
        if (email.isEmpty()) {
            emailLayout.setError("Email is required");
            return false;
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailLayout.setError("Invalid email format");
            return false;
        }

        emailLayout.setError(null);
        return true;
    }

    private boolean validatePassword() {
        String password = passwordInput.getText().toString();
        String confirmPassword = confirmPasswordInput.getText().toString();

        if (password.isEmpty()) {
            passwordLayout.setError("Password is required");
            return false;
        }

        if (password.length() < 6) {
            passwordLayout.setError("Password must be at least 6 characters");
            return false;
        }

        if (!password.equals(confirmPassword)) {
            confirmPasswordLayout.setError("Passwords do not match");
            return false;
        }

        passwordLayout.setError(null);
        confirmPasswordLayout.setError(null);
        return true;
    }

    private boolean validatePersonalInfo() {
        boolean isValid = true;

        String name = nameInput.getText().toString().trim();
        if (name.isEmpty()) {
            nameLayout.setError("Name is required");
            isValid = false;
        } else {
            nameLayout.setError(null);
        }

        String dob = dobInput.getText().toString().trim();
        if (dob.isEmpty()) {
            dobInput.setError("Date of Birth is required");
            isValid = false;
        } else {
            dobInput.setError(null);
        }

        String gender = genderInput.getText().toString().trim();
        if (gender.isEmpty()) {
            genderInput.setError("Gender is required");
            isValid = false;
        } else {
            genderInput.setError(null);
        }

        return isValid;
    }

    private boolean validatePhysicalMeasurements() {
        boolean isValid = true;

        String height = heightInput.getText().toString().trim();
        if (height.isEmpty()) {
            heightLayout.setError("Height is required");
            isValid = false;
        } else {
            try {
                double heightVal = Double.parseDouble(height);
                if (heightVal <= 0 || heightVal > 300) {
                    heightLayout.setError("Enter valid height");
                    isValid = false;
                } else {
                    heightLayout.setError(null);
                }
            } catch (NumberFormatException e) {
                heightLayout.setError("Invalid number");
                isValid = false;
            }
        }

        String weight = weightInput.getText().toString().trim();
        if (weight.isEmpty()) {
            weightLayout.setError("Weight is required");
            isValid = false;
        } else {
            try {
                double weightVal = Double.parseDouble(weight);
                if (weightVal <= 0 || weightVal > 500) {
                    weightLayout.setError("Enter valid weight");
                    isValid = false;
                } else {
                    weightLayout.setError(null);
                }
            } catch (NumberFormatException e) {
                weightLayout.setError("Invalid number");
                isValid = false;
            }
        }

        return isValid;
    }

    // Add validation for new fields to the validateLifestyleAndGoals() method
    private boolean validateLifestyleAndGoals() {
        boolean isValid = true;

        String activityLevel = activityLevelInput.getText().toString();
        if (activityLevel.isEmpty()) {
            activityLevelInput.setError("Please select activity level");
            isValid = false;
        } else {
            activityLevelInput.setError(null);
        }

        String dietType = dietTypeInput.getText().toString();
        if (dietType.isEmpty()) {
            dietTypeInput.setError("Please select diet type");
            isValid = false;
        } else {
            dietTypeInput.setError(null);
        }

        // Validate sleep hours
        String sleepHours = sleepHoursInput.getText().toString().trim();
        if (!sleepHours.isEmpty()) {
            try {
                int hours = Integer.parseInt(sleepHours);
                if (hours < 1 || hours > 24) {
                    sleepHoursLayout.setError("Enter valid hours (1-24)");
                    isValid = false;
                } else {
                    sleepHoursLayout.setError(null);
                }
            } catch (NumberFormatException e) {
                sleepHoursLayout.setError("Invalid number");
                isValid = false;
            }
        }

        // Optional validation for blood pressure
        String systolicBP = systolicBPInput.getText().toString().trim();
        String diastolicBP = diastolicBPInput.getText().toString().trim();

        if (!systolicBP.isEmpty()) {
            try {
                int systolic = Integer.parseInt(systolicBP);
                if (systolic < 50 || systolic > 250) {
                    systolicBPLayout.setError("Enter valid value (50-250)");
                    isValid = false;
                } else {
                    systolicBPLayout.setError(null);
                }
            } catch (NumberFormatException e) {
                systolicBPLayout.setError("Invalid number");
                isValid = false;
            }
        }

        if (!diastolicBP.isEmpty()) {
            try {
                int diastolic = Integer.parseInt(diastolicBP);
                if (diastolic < 30 || diastolic > 150) {
                    diastolicBPLayout.setError("Enter valid value (30-150)");
                    isValid = false;
                } else {
                    diastolicBPLayout.setError(null);
                }
            } catch (NumberFormatException e) {
                diastolicBPLayout.setError("Invalid number");
                isValid = false;
            }
        }

        // Optional validation for resting heart rate
        String heartRate = restingHeartRateInput.getText().toString().trim();
        if (!heartRate.isEmpty()) {
            try {
                int rate = Integer.parseInt(heartRate);
                if (rate < 30 || rate > 200) {
                    restingHeartRateLayout.setError("Enter valid value (30-200)");
                    isValid = false;
                } else {
                    restingHeartRateLayout.setError(null);
                }
            } catch (NumberFormatException e) {
                restingHeartRateLayout.setError("Invalid number");
                isValid = false;
            }
        }

        return isValid;
    }

    private void submitSignupForm() {
        showProgress(true);

        try {
            JSONObject signupData = new JSONObject();

            int smokingLevel = smokingSeekBar.getProgress();
            signupData.put("smoker", smokingLevel > 0); // Set smoker flag if level > 0
            signupData.put("smokingLevel", smokingLevel);
            signupData.put("smokingFrequency", getSmokingText(smokingLevel));

            int alcoholLevel = alcoholSeekBar.getProgress();
            signupData.put("alcoholConsumption", getAlcoholText(alcoholLevel));
            signupData.put("alcoholLevel", alcoholLevel);


            // Required fields from our form
            signupData.put("email", emailInput.getText().toString().trim());
            signupData.put("password", passwordInput.getText().toString());
            signupData.put("confirmPassword", confirmPasswordInput.getText().toString());
            signupData.put("name", nameInput.getText().toString().trim());
            signupData.put("gender", genderInput.getText().toString().trim());

            // Date format conversion for API
            String dobStr = dobInput.getText().toString().trim();
            SimpleDateFormat inputFormat = new SimpleDateFormat("MM/dd/yyyy", Locale.getDefault());
            SimpleDateFormat outputFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            try {
                Date dob = inputFormat.parse(dobStr);
                signupData.put("dateOfBirth", outputFormat.format(dob));
            } catch (ParseException e) {
                Log.e(TAG, "Date parsing error", e);
                signupData.put("dateOfBirth", dobStr);
            }

            signupData.put("height", Double.parseDouble(heightInput.getText().toString().trim()));
            signupData.put("weight", Double.parseDouble(weightInput.getText().toString().trim()));

            // Get the activity level value and convert it to the correct integer
            String activityLevelText = activityLevelInput.getText().toString().trim();
            int activityLevelValue = getActivityLevelValue(activityLevelText);
            signupData.put("activityLevel", activityLevelValue);

            signupData.put("dietType", dietTypeInput.getText().toString().trim());

            // Add new optional fields if provided
            // Phone number
            String phoneNumber = phoneNumberInput.getText().toString().trim();
            if (!phoneNumber.isEmpty()) {
                signupData.put("phoneNumber", phoneNumber);
            }

            // Location
            String location = locationInput.getText().toString().trim();
            if (!location.isEmpty()) {
                signupData.put("location", location);
            }

            // Blood type
            String bloodType = bloodTypeInput.getText().toString().trim();
            if (!bloodType.isEmpty()) {
                signupData.put("bloodType", bloodType);
            }

            // Parse and add sleep hours if provided
            String sleepHoursStr = sleepHoursInput.getText().toString().trim();
            if (!sleepHoursStr.isEmpty()) {
                try {
                    int sleepHours = Integer.parseInt(sleepHoursStr);
                    signupData.put("sleepHours", sleepHours);
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Error parsing sleep hours", e);
                }
            }

            // Add heart rate if provided
            String heartRateStr = restingHeartRateInput.getText().toString().trim();
            if (!heartRateStr.isEmpty()) {
                try {
                    int heartRate = Integer.parseInt(heartRateStr);
                    signupData.put("restingHeartRate", heartRate);
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Error parsing heart rate", e);
                }
            }

            // Add blood pressure if provided
            String systolicStr = systolicBPInput.getText().toString().trim();
            if (!systolicStr.isEmpty()) {
                try {
                    int systolic = Integer.parseInt(systolicStr);
                    signupData.put("systolicBP", systolic);
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Error parsing systolic BP", e);
                }
            }

            String diastolicStr = diastolicBPInput.getText().toString().trim();
            if (!diastolicStr.isEmpty()) {
                try {
                    int diastolic = Integer.parseInt(diastolicStr);
                    signupData.put("diastolicBP", diastolic);
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Error parsing diastolic BP", e);
                }
            }

            // Add primary goal
            String primaryGoal = primaryGoalInput.getText().toString().trim();
            if (!primaryGoal.isEmpty()) {
                signupData.put("primaryGoal", primaryGoal);
            }

            // Default weekly goal value
            signupData.put("weeklyGoal", 0.5);

            Log.d(TAG, "Signup payload: " + signupData.toString());

            StringRequest request = new StringRequest(Request.Method.POST, API_URL,
                    response -> {
                        ApiConfig.logRestCall(API_URL, true, "Signup successful");
                        Log.d(TAG, "Signup successful, response: " + response);
                        showProgress(false);
                        try {
                            // Parse response
                            JSONObject responseJson = new JSONObject(response);
                            String token = responseJson.getString("token");
                            String userId = responseJson.getString("userId");

                            // Create a UserProfile to save to local database
                            UserProfile newUser = new UserProfile();
                            newUser.setEmail(emailInput.getText().toString().trim());
                            newUser.setName(nameInput.getText().toString().trim());
                            newUser.setGender(genderInput.getText().toString().trim());

                            // Parse and set date of birth
                            try {
                                Date dob = inputFormat.parse(dobStr);
                                newUser.setDateOfBirth(dob);
                            } catch (ParseException e) {
                                Log.e(TAG, "Error parsing date", e);
                            }

                            // Set physical measurements
                            newUser.setHeight(Double.parseDouble(heightInput.getText().toString().trim()));
                            newUser.setWeight(Double.parseDouble(weightInput.getText().toString().trim()));

                            newUser.setSmoker(smokingLevel > 0);
                            newUser.setSmokingLevel(smokingLevel);
                            newUser.setAlcoholConsumption(getAlcoholText(alcoholLevel));
                            // Set activity level
                            newUser.setActivityLevel(activityLevelValue);

                            // Set diet type
                            newUser.setDietType(dietTypeInput.getText().toString().trim());

                            // Set additional fields
                            if (!phoneNumber.isEmpty()) {
                                newUser.setPhoneNumber(phoneNumber);
                            }

                            if (!location.isEmpty()) {
                                newUser.setLocation(location);
                            }

                            if (!bloodType.isEmpty()) {
                                newUser.setBloodType(bloodType);
                            }

                            // Set sleep hours
                            if (!sleepHoursStr.isEmpty()) {
                                try {
                                    newUser.setSleepHours(Integer.parseInt(sleepHoursStr));
                                } catch (NumberFormatException e) {
                                    newUser.setSleepHours(8); // Default value
                                }
                            } else {
                                newUser.setSleepHours(8); // Default value
                            }

                            // Set heart rate
                            if (!heartRateStr.isEmpty()) {
                                try {
                                    newUser.setRestingHeartRate(Integer.parseInt(heartRateStr));
                                } catch (NumberFormatException e) {
                                    // Skip if invalid
                                }
                            }

                            // Set blood pressure
                            if (!systolicStr.isEmpty()) {
                                try {
                                    newUser.setSystolicBP(Integer.parseInt(systolicStr));
                                } catch (NumberFormatException e) {
                                    // Skip if invalid
                                }
                            }

                            if (!diastolicStr.isEmpty()) {
                                try {
                                    newUser.setDiastolicBP(Integer.parseInt(diastolicStr));
                                } catch (NumberFormatException e) {
                                    // Skip if invalid
                                }
                            }

                            // Set primary goal
                            if (!primaryGoal.isEmpty()) {
                                newUser.setPrimaryGoal(primaryGoal);
                            }

                            newUser.setWeeklyGoal(0.5); // Default weekly goal

                            // Initialize empty lists for collection fields
                            newUser.setMedicalConditions(new ArrayList<>());
                            newUser.setMedications(new ArrayList<>());
                            newUser.setAllergies(new ArrayList<>());
                            newUser.setPreferredExerciseTypes(new ArrayList<>());

                            // Set authentication info
                            newUser.setAuthToken(token);
                            newUser.setLoggedIn(true);
                            newUser.setLastLogin(new Date());

                            // Save user to local database
                            DatabaseHelper dbHelper = new DatabaseHelper(this);
                            long result = dbHelper.insertUserProfile(newUser);
                            Log.d(TAG, "User saved to database with result: " + result);

                            // Save auth token
                            TokenManager tokenManager = TokenManager.getInstance(this);
                            tokenManager.saveLoginInfo(token, userId);

                            handleSignupSuccess();

                        } catch (JSONException e) {
                            Log.e(TAG, "Error parsing signup response", e);
                            showProgress(false);
                        }
                    },
                    error -> {
                        ApiConfig.logRestCall(API_URL, false, error.toString());
                        showProgress(false);
                        handleSignupError(error);
                    }
            ) {
                @Override
                public byte[] getBody() {
                    return signupData.toString().getBytes(StandardCharsets.UTF_8);
                }

                @Override
                public String getBodyContentType() {
                    return "application/json; charset=utf-8";
                }
            };

            // Set retry policy
            request.setRetryPolicy(new DefaultRetryPolicy(
                    30000,  // 30 seconds timeout
                    1,      // Max 1 retry
                    1f      // No backoff multiplier
            ));

            // Add to request queue
            RequestQueue queue = Volley.newRequestQueue(this);
            queue.add(request);

        } catch (JSONException | NumberFormatException e) {
            showProgress(false);
            Log.e(TAG, "Error creating JSON payload", e);
            Utilities.toast(this, "Error preparing signup data: " + e.getMessage());
        }
    }

    private void handleSignupSuccess() {
        new AlertDialog.Builder(this)
                .setTitle("Signup Successful")
                .setMessage("Your account has been created successfully! Please login to continue.")
                .setPositiveButton("Go to Login", (dialog, which) -> {
                    // Navigate to login screen
                    Intent intent = new Intent(SignupActivity.this, LoginActivity.class);
                    startActivity(intent);
                    finish();
                })
                .setCancelable(false)
                .show();
    }

    private void handleSignupError(VolleyError error) {
        String errorMessage = "Signup failed. Please try again.";

        if (error.networkResponse != null) {
            try {
                String errorData = new String(error.networkResponse.data, StandardCharsets.UTF_8);
                JSONObject errorJson = new JSONObject(errorData);

                if (errorJson.has("errors")) {
                    JSONObject errors = errorJson.getJSONObject("errors");
                    // Display the first error we find
                    for (int i = 0; i < errors.names().length(); i++) {
                        String field = errors.names().getString(i);
                        errorMessage = errors.getString(field);
                        break;
                    }
                } else if (errorJson.has("message")) {
                    errorMessage = errorJson.getString("message");
                }
            } catch (JSONException e) {
                Log.e(TAG, "Error parsing error response", e);
            }
        }

        Utilities.toastLong(this, errorMessage);
    }

    private int getActivityLevelValue(String level) {
        switch (level.toLowerCase()) {
            case "sedentary": return 1;
            case "lightly active": return 2;
            case "moderately active": return 3;
            case "very active": return 4;
            case "extremely active": return 5;
            default: return 2; // Default to "Lightly Active" if not recognized
        }
    }

    private void showProgress(boolean show) {
        progressIndicator.setVisibility(show ? View.VISIBLE : View.GONE);
        nextButton.setEnabled(!show);
        backButton.setEnabled(!show);
        submitButton.setEnabled(!show);
    }

    private void updateSmokingText(int progress) {
        String[] smokingLevels = {"Non-smoker", "Occasional", "Light", "Moderate", "Heavy"};
        smokingText.setText(smokingLevels[progress]);
    }

    private void updateAlcoholText(int progress) {
        String[] alcoholLevels = {"None", "Rarely", "Socially", "Regularly", "Frequently"};
        alcoholText.setText(alcoholLevels[progress]);
    }

    // Get the text representation of smoking level
    private String getSmokingText(int level) {
        String[] smokingLevels = {"Non-smoker", "Occasional", "Light", "Moderate", "Heavy"};
        return smokingLevels[level];
    }

    // Get the text representation of alcohol consumption level
    private String getAlcoholText(int level) {
        String[] alcoholLevels = {"None", "Rarely", "Socially", "Regularly", "Frequently"};
        return alcoholLevels[level];
    }

    @Override
    public void onBackPressed() {
        if (currentStep > 0) {
            moveToPreviousStep();
        } else {
            Utils.DialogUtils.showConfirmDialog(this,
                    "Exit Signup",
                    "Are you sure you want to exit? All progress will be lost.",
                    "Yes", "No", true,
                    () -> super.onBackPressed());
        }
    }
}
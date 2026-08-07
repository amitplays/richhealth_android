package com.example.richhealth.Activities;
import Utils.Utilities;

import Utils.ApiConfig;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.example.richhealth.R;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import Database.DatabaseHelper;
import Models.UserProfile;
import Utils.BiometricHelper;
import Utils.ProStatusManager;

public class LoginActivity extends AppCompatActivity {
    private static final String TAG = "LoginActivity";
    private static final String API_URL = ApiConfig.BASE_URL + "/api/auth/login";

    private TextInputLayout emailLayout, passwordLayout;
    private TextInputEditText emailInput, passwordInput;
    private Button loginButton;
    private TextView signupLink;
    private TokenManager tokenManager;
    private DatabaseHelper dbHelper;
    private ImageView logo;
    private ObjectAnimator spinningAnimator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Initialize TokenManager
        tokenManager = TokenManager.getInstance(this);
        dbHelper = new DatabaseHelper(this);
        logo = findViewById(R.id.logo);

        // Hide action bar
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        initViews();
        setupListeners();
    }

    private void startLogoSpin() {
        spinningAnimator = ObjectAnimator.ofFloat(logo, View.ROTATION, 0f, 180f);
        spinningAnimator.setDuration(3000);
        spinningAnimator.setRepeatCount(ObjectAnimator.INFINITE);
        spinningAnimator.setInterpolator(new DecelerateInterpolator());
        spinningAnimator.start();
    }

    private void stopLogoSpin() {
        if (spinningAnimator != null && spinningAnimator.isRunning()) {
            spinningAnimator.cancel();
            logo.setRotation(0); // Reset rotation
        }
    }
    private void initViews() {
        emailLayout = findViewById(R.id.email_layout);
        passwordLayout = findViewById(R.id.password_layout);
        emailInput = findViewById(R.id.email_input);
        passwordInput = findViewById(R.id.password_input);
        loginButton = findViewById(R.id.login_button);
        signupLink = findViewById(R.id.signup_link);
    }

    private void setupListeners() {
        // Email validation
        emailInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Clear error state when user starts typing
                emailLayout.setError(null);
                validateEmail();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Password validation
        passwordInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Clear error state when user starts typing
                passwordLayout.setError(null);
                validatePassword();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Login button
        loginButton.setOnClickListener(v -> attemptLogin());

        // Signup link — leads to new card-based onboarding flow
        signupLink.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, OnboardingActivity.class));
            finish();
        });
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
        if (password.isEmpty()) {
            passwordLayout.setError("Password is required");
            return false;
        }

        passwordLayout.setError(null);
        return true;
    }

    private void attemptLogin() {
        // Validate input
        if (!validateEmail() || !validatePassword()) {
            return;
        }

        // Get input values
        final String email = emailInput.getText().toString().trim();
        final String password = passwordInput.getText().toString();

        Log.d(TAG, "Attempting login for email: " + email);

        // Show loading state
        loginButton.setEnabled(false);
        loginButton.setText("Logging in...");
        // Start 5-second spin animation on login button
        startLogoSpin();
        // Try all three common approaches to handle the server requirements
        tryJsonLogin(email, password);
    }

    private void tryJsonLogin(final String email, final String password) {
        try {
            // Create JSON object
            final JSONObject jsonBody = new JSONObject();
            jsonBody.put("email", email);
            jsonBody.put("password", password);

            Log.d(TAG, "Attempting JSON login with payload: " + jsonBody.toString());
            Log.d(TAG, "Attempting JSON login with url: " + API_URL);

            StringRequest request = new StringRequest(Request.Method.POST, API_URL,
                    response -> {
                        ApiConfig.logRestCall(API_URL, true, "JSON login successful");
                        Log.d(TAG, "Login successful, response: " + response);
                        handleLoginSuccess(response);
                    },
                    error -> {
                        ApiConfig.logRestCall(API_URL, false, "JSON login failed: " + error.toString());
                        Log.e(TAG, "JSON login failed, Error : "+error+". Trying form URL encoded approach");
                        // Only try next approach if it's likely a format issue, not a timeout or server error
                        if (error.networkResponse != null && (error.networkResponse.statusCode == 400 || error.networkResponse.statusCode == 415)) {
                            tryFormUrlEncodedLogin(email, password);
                        } else if (error instanceof com.android.volley.TimeoutError || error instanceof com.android.volley.NoConnectionError) {
                            stopLogoSpin();
                            handleLoginError("Connection timeout. Please check your internet or try again later.", error);
                        } else {
                            // For other errors, try the fallback approach anyway just in case
                            tryFormUrlEncodedLogin(email, password);
                        }
                    }
            ) {
                @Override
                public byte[] getBody() {
                    return jsonBody.toString().getBytes(StandardCharsets.UTF_8);
                }

                @Override
                public String getBodyContentType() {
                    return "application/json; charset=utf-8";
                }

                @Override
                protected Response<String> parseNetworkResponse(NetworkResponse response) {
                    // Check for 2xx response regardless of specific content type
                    if (response.statusCode >= 200 && response.statusCode < 300) {
                        try {
                            String responseData = new String(response.data,
                                    HttpHeaderParser.parseCharset(response.headers));
                            return Response.success(responseData,
                                    HttpHeaderParser.parseCacheHeaders(response));
                        } catch (UnsupportedEncodingException e) {
                            return Response.error(new VolleyError("Encoding error"));
                        }
                    }
                    return super.parseNetworkResponse(response);
                }
            };

            // Set retry policy
            request.setRetryPolicy(new DefaultRetryPolicy(
                    10000,  // 10 seconds timeout
                    1,      // No retries for this attempt
                    1f      // No backoff multiplier
            ));

            // Add to request queue
            RequestQueue queue = Volley.newRequestQueue(this);
            queue.add(request);

        } catch (JSONException e) {
            Log.e(TAG, "Error creating JSON for login", e);
            tryFormUrlEncodedLogin(email, password);
        }
    }

    private void tryFormUrlEncodedLogin(final String email, final String password) {
        Log.d(TAG, "Attempting form URL encoded login. URL : " +API_URL );

        StringRequest request = new StringRequest(Request.Method.POST, API_URL,
                response -> {
                    ApiConfig.logRestCall(API_URL, true, "Form login successful");
                    Log.d(TAG, "Login successful, response: " + response);
                    handleLoginSuccess(response);
                },
                error -> {
                    ApiConfig.logRestCall(API_URL, false, "Form login failed: " + error.toString());
                    Log.e(TAG, "Form URL encoded login failed, trying multipart approach" + error);
                    stopLogoSpin();
                    handleLoginError("Login failed. Please check your credentials and try again.", error);
                }
        ) {
            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<>();
                params.put("email", email);
                params.put("password", password);
                return params;
            }

            @Override
            public String getBodyContentType() {
                return "application/x-www-form-urlencoded; charset=UTF-8";
            }

            @Override
            protected Response<String> parseNetworkResponse(NetworkResponse response) {
                // Check for 2xx response regardless of specific content type
                if (response.statusCode >= 200 && response.statusCode < 300) {
                    try {
                        String responseData = new String(response.data,
                                HttpHeaderParser.parseCharset(response.headers));
                        return Response.success(responseData,
                                HttpHeaderParser.parseCacheHeaders(response));
                    } catch (UnsupportedEncodingException e) {
                        return Response.error(new VolleyError("Encoding error"));
                    }
                }
                return super.parseNetworkResponse(response);
            }
        };

        // Set retry policy
        request.setRetryPolicy(new DefaultRetryPolicy(
                10000,  // 10 seconds timeout
                0,      // No retries for this approach
                1f      // No backoff multiplier
        ));

        // Add to request queue
        RequestQueue queue = Volley.newRequestQueue(this);
        queue.add(request);
    }

    private void handleLoginSuccess(String responseData) {
        try {
            // Parse login response
            JSONObject responseJson = new JSONObject(responseData);
            Log.d(TAG, "Full response: " + responseData);
            ProStatusManager.syncProStatusOnLogin(this);
            // Extract token and user ID
            String token = responseJson.getString("token");
            String userId = responseJson.getString("userId");
            Log.d(TAG, "Extracted token: " + token + " AND Extracted userId: " + userId);

            // Get user details if available
            String name = "";
            String email = emailInput.getText().toString().trim();
            int activityLevel = 2; // Default to "Lightly Active" if not in response
            String dietType = "Regular"; // Default if not in response

            // Additional fields with default values
            double height = 0;
            double weight = 0;
            String bloodType = "";
            List<String> medicalConditions = new ArrayList<>();
            List<String> medications = new ArrayList<>();
            List<String> allergies = new ArrayList<>();
            int sleepHours = 0;
            String primaryGoal = "";
            double weeklyGoal = 0;

            JSONObject userDetails = null;
            if (responseJson.has("user") && !responseJson.isNull("user")) {
                userDetails = responseJson.getJSONObject("user");
                Log.d(TAG, "Full user details: " + userDetails.toString());

                // Process each field with explicit logging
                if (userDetails.has("name") && !userDetails.isNull("name")) {
                    name = userDetails.getString("name");
                    Log.d(TAG, "Name from response: " + name);
                }

                if (userDetails.has("email") && !userDetails.isNull("email")) {
                    email = userDetails.getString("email");
                    Log.d(TAG, "Email from response: " + email);
                }

                if (userDetails.has("height") && !userDetails.isNull("height")) {
                    height = userDetails.getDouble("height");
                    Log.d(TAG, "Height from response: " + height);
                }

                if (userDetails.has("weight") && !userDetails.isNull("weight")) {
                    weight = userDetails.getDouble("weight");
                    Log.d(TAG, "Weight from response: " + weight);
                }

                if (userDetails.has("bloodType") && !userDetails.isNull("bloodType")) {
                    bloodType = userDetails.getString("bloodType");
                    Log.d(TAG, "Blood Type from response: " + bloodType);
                }

                if (userDetails.has("medicalConditions") && !userDetails.isNull("medicalConditions")) {
                    JSONArray conditionsArray = userDetails.getJSONArray("medicalConditions");
                    for (int i = 0; i < conditionsArray.length(); i++) {
                        medicalConditions.add(conditionsArray.getString(i));
                    }
                    Log.d(TAG, "Medical Conditions from response: " + medicalConditions);
                }

                if (userDetails.has("medications") && !userDetails.isNull("medications")) {
                    JSONArray medicationsArray = userDetails.getJSONArray("medications");
                    for (int i = 0; i < medicationsArray.length(); i++) {
                        medications.add(medicationsArray.getString(i));
                    }
                    Log.d(TAG, "Medications from response: " + medications);
                }

                if (userDetails.has("allergies") && !userDetails.isNull("allergies")) {
                    JSONArray allergiesArray = userDetails.getJSONArray("allergies");
                    for (int i = 0; i < allergiesArray.length(); i++) {
                        allergies.add(allergiesArray.getString(i));
                    }
                    Log.d(TAG, "Allergies from response: " + allergies);
                }

                if (userDetails.has("activityLevel") && !userDetails.isNull("activityLevel")) {
                    activityLevel = userDetails.getInt("activityLevel");
                    Log.d(TAG, "Activity level from response: " + activityLevel);
                }

                if (userDetails.has("dietType") && !userDetails.isNull("dietType")) {
                    dietType = userDetails.getString("dietType");
                    Log.d(TAG, "Diet type from response: " + dietType);
                }

                if (userDetails.has("sleepHours") && !userDetails.isNull("sleepHours")) {
                    sleepHours = userDetails.getInt("sleepHours");
                    Log.d(TAG, "Sleep Hours from response: " + sleepHours);
                }

                if (userDetails.has("primaryGoal") && !userDetails.isNull("primaryGoal")) {
                    primaryGoal = userDetails.getString("primaryGoal");
                    Log.d(TAG, "Primary Goal from response: " + primaryGoal);
                }

                if (userDetails.has("weeklyGoal") && !userDetails.isNull("weeklyGoal")) {
                    weeklyGoal = userDetails.getDouble("weeklyGoal");
                    Log.d(TAG, "Weekly Goal from response: " + weeklyGoal);
                }
            } else {
                Log.d(TAG, "No user details found in response");
            }

            Log.d(TAG, "Login successful for userId: " + userId);

            // Save to TokenManager
            tokenManager.saveLoginInfo(token, userId);

            // Sync account-level T&C acceptance to this device so an already-accepted
            // user isn't re-prompted after reinstalling or switching devices.
            if (userDetails != null && userDetails.optBoolean("termsAccepted", false)) {
                TermsAndConditionsDialog.markAcceptedLocally(LoginActivity.this, userId);
            }

            // Sync only the "already offered" flag so the one-time setup prompt isn't
            // re-shown after reinstall / on a new device. We intentionally do NOT
            // auto-enable the biometric app-lock from the account preference: enabling
            // the lock is a per-device decision (biometric enrolment is device-specific),
            // so a new device stays unlocked until the user turns it on in Settings.
            if (userDetails != null && userDetails.optBoolean("biometricPromptShown", false)) {
                BiometricHelper.setPromptedBiometricSetup(LoginActivity.this, true);
            }

            // Save user data to database
            saveUserToDatabase(userId, name, email, token, userDetails,
                    activityLevel, dietType, height, weight,
                    bloodType, medicalConditions, medications,
                    allergies, sleepHours, primaryGoal, weeklyGoal);

            // Stop logo spinning
            stopLogoSpin();

            // Show Terms and Conditions dialog before proceeding to MainActivity
            showTermsAndConditionsDialog();

        } catch (JSONException e) {
            Log.e(TAG, "Error parsing login response", e);
            handleLoginError("Invalid server response", null);
            stopLogoSpin();
        }
    }

    private void showTermsAndConditionsDialog() {
        // Already accepted on this account (synced from the cloud) — skip straight
        // ahead instead of re-prompting on every login.
        if (TermsAndConditionsDialog.areTermsAccepted(this)) {
            offerBiometricSetup();
            return;
        }
        TermsAndConditionsDialog termsDialog = new TermsAndConditionsDialog(this, new TermsAndConditionsDialog.OnTermsActionListener() {
            @Override
            public void onTermsAccepted() {
                Utilities.toast(LoginActivity.this, "Login successful!");
                // After terms accepted, offer biometric setup if device supports it
                offerBiometricSetup();
            }

            @Override
            public void onTermsDeclined() {
                Utilities.toastLong(LoginActivity.this, "Terms must be accepted to use the app");
                tokenManager.logout();
                finishAffinity();
            }
        });

        termsDialog.show();
    }

    /**
     * Show biometric setup prompt after login + T&C acceptance.
     * If device doesn't support biometric, skip straight to MainActivity.
     */
    private void offerBiometricSetup() {
        // Skip if device has no biometric hardware or none enrolled
        if (!BiometricHelper.canAuthenticate(this)) {
            navigateToMain();
            return;
        }

        // Skip if biometric is already enabled (e.g., returning user re-accepting terms)
        if (BiometricHelper.isBiometricEnabled(this)) {
            navigateToMain();
            return;
        }

        // Only OFFER setup once per device — don't nag on every login. The user can
        // still enable it later from Settings.
        if (BiometricHelper.hasPromptedBiometricSetup(this)) {
            navigateToMain();
            return;
        }
        BiometricHelper.setPromptedBiometricSetup(this, true);

        android.app.Dialog dialog = new android.app.Dialog(this, R.style.DialogTheme);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_biometric_setup);
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);

        android.view.WindowManager.LayoutParams layoutParams = new android.view.WindowManager.LayoutParams();
        layoutParams.copyFrom(dialog.getWindow().getAttributes());
        layoutParams.width = android.view.WindowManager.LayoutParams.MATCH_PARENT;
        layoutParams.height = android.view.WindowManager.LayoutParams.WRAP_CONTENT;
        dialog.getWindow().setAttributes(layoutParams);
        dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));

        dialog.findViewById(R.id.enable_biometric_button).setOnClickListener(v -> {
            // Verify biometric works before enabling
            BiometricHelper.authenticate(this,
                    () -> {
                        // Success — save preference (locally + account) and proceed
                        BiometricHelper.setBiometricEnabled(this, true);
                        BiometricHelper.persistBiometricToServer(this, true);
                        Utilities.toast(this, "Biometric lock enabled!");
                        dialog.dismiss();
                        navigateToMain();
                    },
                    (errorCode, errString) -> {
                        // Failed / cancelled — still proceed, user can enable later in Settings.
                        // Record that we've asked so we don't re-prompt on the next login.
                        BiometricHelper.persistBiometricToServer(this, false);
                        if (!isFinishing()) Utilities.toast(this, "Biometric setup skipped. You can enable it in Settings.");
                        if (dialog.isShowing()) dialog.dismiss();
                        navigateToMain();
                    });
        });

        dialog.findViewById(R.id.skip_biometric_button).setOnClickListener(v -> {
            // Record the one-time offer as shown on the account so it isn't re-prompted.
            BiometricHelper.persistBiometricToServer(this, false);
            dialog.dismiss();
            navigateToMain();
        });

        dialog.show();
    }

    private void navigateToMain() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        // Tell MainActivity to skip biometric this session — user just authenticated with password
        intent.putExtra("skip_biometric", true);
        startActivity(intent);
        finish();
    }


    private void saveUserToDatabase(String userId, String name, String email, String token,
                                    JSONObject userDetails, int activityLevel, String dietType,
                                    double height, double weight, String bloodType,
                                    List<String> medicalConditions, List<String> medications,
                                    List<String> allergies, int sleepHours,
                                    String primaryGoal, double weeklyGoal) {
        try {
            Log.d(TAG, "saveUserToDatabase - Start");
            Log.d(TAG, "Parameters:");
            Log.d(TAG, "userId: " + userId);
            Log.d(TAG, "name: " + name);
            Log.d(TAG, "email: " + email);
            Log.d(TAG, "token: " + token);
            Log.d(TAG, "userDetails: " + (userDetails != null ? userDetails.toString() : "null"));
            Log.d(TAG, "activityLevel: " + activityLevel);
            Log.d(TAG, "dietType: " + dietType);

            // Get existing profile or create a new one
            UserProfile profile = dbHelper.getUserProfile();
            Log.d(TAG, "Existing profile: " + (profile != null ? "Found" : "Not found"));

            if (profile == null) {
                profile = new UserProfile();
                Log.d(TAG, "Creating new user profile");
            } else {
                Log.d(TAG, "Updating existing user profile");
            }

            // If full user details are available from the server
            if (userDetails != null) {
                Log.d(TAG, "Processing user details");

                // Basic info
                name = userDetails.has("name") ? userDetails.getString("name") : name;
                email = userDetails.has("email") ? userDetails.getString("email") : email;

                Log.d(TAG, "Processed name: " + name);
                Log.d(TAG, "Processed email: " + email);

                profile.setName(name);
                profile.setEmail(email);
                profile.setLoggedIn(true);
                profile.setLastLogin(new Date());
                profile.setAuthToken(token);

                // Process all additional fields from userDetails
                if (userDetails.has("height") && !userDetails.isNull("height")) {
                    height = userDetails.getDouble("height");
                    Log.d(TAG, "Height from response: " + height);
                }

                if (userDetails.has("weight") && !userDetails.isNull("weight")) {
                    weight = userDetails.getDouble("weight");
                    Log.d(TAG, "Weight from response: " + weight);
                }

                if (userDetails.has("bloodType") && !userDetails.isNull("bloodType")) {
                    bloodType = userDetails.getString("bloodType");
                    Log.d(TAG, "Blood Type from response: " + bloodType);
                }

                if (userDetails.has("medicalConditions") && !userDetails.isNull("medicalConditions")) {
                    JSONArray conditionsArray = userDetails.getJSONArray("medicalConditions");
                    medicalConditions.clear();
                    for (int i = 0; i < conditionsArray.length(); i++) {
                        medicalConditions.add(conditionsArray.getString(i));
                    }
                    Log.d(TAG, "Medical Conditions from response: " + medicalConditions);
                }

                if (userDetails.has("medications") && !userDetails.isNull("medications")) {
                    JSONArray medicationsArray = userDetails.getJSONArray("medications");
                    medications.clear();
                    for (int i = 0; i < medicationsArray.length(); i++) {
                        medications.add(medicationsArray.getString(i));
                    }
                    Log.d(TAG, "Medications from response: " + medications);
                }

                if (userDetails.has("allergies") && !userDetails.isNull("allergies")) {
                    JSONArray allergiesArray = userDetails.getJSONArray("allergies");
                    allergies.clear();
                    for (int i = 0; i < allergiesArray.length(); i++) {
                        allergies.add(allergiesArray.getString(i));
                    }
                    Log.d(TAG, "Allergies from response: " + allergies);
                }

                if (userDetails.has("activityLevel") && !userDetails.isNull("activityLevel")) {
                    activityLevel = userDetails.getInt("activityLevel");
                    Log.d(TAG, "Activity level from response: " + activityLevel);
                }

                if (userDetails.has("dietType") && !userDetails.isNull("dietType")) {
                    dietType = userDetails.getString("dietType");
                    Log.d(TAG, "Diet type from response: " + dietType);
                }

                if (userDetails.has("sleepHours") && !userDetails.isNull("sleepHours")) {
                    sleepHours = userDetails.getInt("sleepHours");
                    Log.d(TAG, "Sleep Hours from response: " + sleepHours);
                }

                if (userDetails.has("primaryGoal") && !userDetails.isNull("primaryGoal")) {
                    primaryGoal = userDetails.getString("primaryGoal");
                    Log.d(TAG, "Primary Goal from response: " + primaryGoal);
                }

                if (userDetails.has("weeklyGoal") && !userDetails.isNull("weeklyGoal")) {
                    weeklyGoal = userDetails.getDouble("weeklyGoal");
                    Log.d(TAG, "Weekly Goal from response: " + weeklyGoal);
                }

                // Additional fields from the response
                // Gender
                if (userDetails.has("gender") && !userDetails.isNull("gender")) {
                    profile.setGender(userDetails.getString("gender"));
                    Log.d(TAG, "Gender from response: " + userDetails.getString("gender"));
                }

                // Date of Birth
                if (userDetails.has("dateOfBirth") && !userDetails.isNull("dateOfBirth")) {
                    try {
                        String dobStr = userDetails.getString("dateOfBirth");
                        // Handle ISO format from backend (e.g. "2000-04-11T00:00:00.000Z")
                        String cleanDob = dobStr.contains("T") ? dobStr.substring(0, dobStr.indexOf("T")) : dobStr;
                        java.util.Date dob = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(cleanDob);
                        profile.setDateOfBirth(dob);
                        Log.d(TAG, "DOB from response: " + cleanDob);
                    } catch (Exception e) {
                        Log.w(TAG, "Could not parse DOB: " + e.getMessage());
                    }
                }

                // Menstrual health
                if (userDetails.has("menstrualStatus") && !userDetails.isNull("menstrualStatus")) {
                    profile.setMenstrualStatus(userDetails.getString("menstrualStatus"));
                    Log.d(TAG, "Menstrual status from response: " + userDetails.getString("menstrualStatus"));
                }
                if (userDetails.has("averageCycleLength") && !userDetails.isNull("averageCycleLength")) {
                    profile.setAverageCycleLength(userDetails.getInt("averageCycleLength"));
                }
                if (userDetails.has("averagePeriodLength") && !userDetails.isNull("averagePeriodLength")) {
                    profile.setAveragePeriodLength(userDetails.getInt("averagePeriodLength"));
                }
                if (userDetails.has("pregnancyStatus") && !userDetails.isNull("pregnancyStatus")) {
                    profile.setPregnancyStatus(userDetails.getString("pregnancyStatus"));
                }
                if (userDetails.has("menstrualSymptoms") && !userDetails.isNull("menstrualSymptoms")) {
                    JSONArray symptomsArr = userDetails.getJSONArray("menstrualSymptoms");
                    java.util.List<String> symptoms = new java.util.ArrayList<>();
                    for (int i = 0; i < symptomsArr.length(); i++) {
                        symptoms.add(symptomsArr.getString(i));
                    }
                    profile.setMenstrualSymptoms(symptoms);
                    Log.d(TAG, "Menstrual symptoms from response: " + symptoms);
                }

                // Smoker fields
                if (userDetails.has("smoker") && !userDetails.isNull("smoker")) {
                    profile.setSmoker(userDetails.getBoolean("smoker"));
                }
                if (userDetails.has("smokingLevel") && !userDetails.isNull("smokingLevel")) {
                    profile.setSmokingLevel(userDetails.getInt("smokingLevel"));
                }
                if (userDetails.has("smokingFrequency") && !userDetails.isNull("smokingFrequency")) {
                    profile.setSmokingFrequency(userDetails.getString("smokingFrequency"));
                }
                if (userDetails.has("alcoholConsumption") && !userDetails.isNull("alcoholConsumption")) {
                    profile.setAlcoholConsumption(userDetails.getString("alcoholConsumption"));
                }
                if (userDetails.has("alcoholLevel") && !userDetails.isNull("alcoholLevel")) {
                    profile.setAlcoholLevel(userDetails.getInt("alcoholLevel"));
                }
                if (userDetails.has("caffeineHabit") && !userDetails.isNull("caffeineHabit")) {
                    profile.setCaffeineHabit(userDetails.getString("caffeineHabit"));
                }
                if (userDetails.has("screenTimeBeforeBed") && !userDetails.isNull("screenTimeBeforeBed")) {
                    profile.setScreenTimeBeforeBed(userDetails.getString("screenTimeBeforeBed"));
                }
                if (userDetails.has("sunExposure") && !userDetails.isNull("sunExposure")) {
                    profile.setSunExposure(userDetails.getString("sunExposure"));
                }

                // Personal / contact
                if (userDetails.has("phoneNumber") && !userDetails.isNull("phoneNumber")) {
                    profile.setPhoneNumber(userDetails.getString("phoneNumber"));
                }
                if (userDetails.has("location") && !userDetails.isNull("location")) {
                    profile.setLocation(userDetails.getString("location"));
                }

                // Lifestyle
                if (userDetails.has("occupationType") && !userDetails.isNull("occupationType")) {
                    profile.setOccupationType(userDetails.getString("occupationType"));
                }
                if (userDetails.has("stressLevel") && !userDetails.isNull("stressLevel")) {
                    profile.setStressLevel(userDetails.getInt("stressLevel"));
                }
                if (userDetails.has("mealsPerDay") && !userDetails.isNull("mealsPerDay")) {
                    profile.setMealsPerDay(userDetails.getInt("mealsPerDay"));
                }
                if (userDetails.has("waterIntake") && !userDetails.isNull("waterIntake")) {
                    profile.setWaterIntake(userDetails.getInt("waterIntake"));
                }

                // Reproductive (extras)
                if (userDetails.has("contraceptionMethod") && !userDetails.isNull("contraceptionMethod")) {
                    profile.setContraceptionMethod(userDetails.getString("contraceptionMethod"));
                }

                // Family history
                if (userDetails.has("familyHistory") && !userDetails.isNull("familyHistory")) {
                    JSONArray arr = userDetails.getJSONArray("familyHistory");
                    java.util.List<String> list = new java.util.ArrayList<>();
                    for (int i = 0; i < arr.length(); i++) list.add(arr.getString(i));
                    profile.setFamilyHistory(list);
                }

                if (userDetails.has("restingHeartRate") && !userDetails.isNull("restingHeartRate")) {
                    int restingHeartRate = userDetails.getInt("restingHeartRate");
                    profile.setRestingHeartRate(restingHeartRate);
                    Log.d(TAG, "Resting Heart Rate from response: " + restingHeartRate);
                }

                if (userDetails.has("systolicBP") && !userDetails.isNull("systolicBP")) {
                    int systolicBP = userDetails.getInt("systolicBP");
                    profile.setSystolicBP(systolicBP);
                    Log.d(TAG, "Systolic BP from response: " + systolicBP);
                }

                if (userDetails.has("diastolicBP") && !userDetails.isNull("diastolicBP")) {
                    int diastolicBP = userDetails.getInt("diastolicBP");
                    profile.setDiastolicBP(diastolicBP);
                    Log.d(TAG, "Diastolic BP from response: " + diastolicBP);
                }

                // Make sure activity level is set to a valid value (1-5)
                if (activityLevel < 1 || activityLevel > 5) {
                    activityLevel = 2; // Default to "Lightly Active"
                }
                profile.setActivityLevel(activityLevel);
                Log.d(TAG, "Set activity level: " + activityLevel);

                // Set diet type
                if (dietType != null && !dietType.isEmpty()) {
                    profile.setDietType(dietType);
                    Log.d(TAG, "Set diet type: " + dietType);
                }

                // Set all the fields on the profile
                profile.setHeight(height);
                profile.setWeight(weight);
                profile.setBloodType(bloodType);
                profile.setMedicalConditions(medicalConditions);
                profile.setMedications(medications);
                profile.setAllergies(allergies);
                profile.setSleepHours(sleepHours);
                profile.setPrimaryGoal(primaryGoal);
                profile.setWeeklyGoal(weeklyGoal);

            } else {
                Log.d(TAG, "No user details provided, using default values");

                // Existing manual profile creation logic
                profile.setName(name);
                profile.setEmail(email);
                profile.setLoggedIn(true);
                profile.setLastLogin(new Date());
                profile.setAuthToken(token);

                // Make sure activity level is set to a valid value (1-5)
                if (activityLevel < 1 || activityLevel > 5) {
                    activityLevel = 2; // Default to "Lightly Active"
                }
                profile.setActivityLevel(activityLevel);

                // Set diet type
                if (dietType != null && !dietType.isEmpty()) {
                    profile.setDietType(dietType);
                }
            }

            // Save or update profile
            long result;
            if (profile.getId() > 0) {
                Log.d(TAG, "Updating existing profile");
                result = dbHelper.updateUserProfile(profile);
            } else {
                Log.d(TAG, "Inserting new profile");
                result = dbHelper.insertUserProfile(profile);
            }

            Log.d(TAG, "Database operation result: " + result);
            Log.d(TAG, "saveUserToDatabase - End");

        } catch (Exception e) {
            Log.e(TAG, "Error saving user to database", e);
        }
    }

    // Error handling method
    private void handleLoginError(String message, VolleyError error) {
        // Restore login button state
        loginButton.setEnabled(true);
        loginButton.setText("Login");

        String errorMessage = message;
        boolean isCredentialError = false;

        if (error != null) {
            if (error instanceof com.android.volley.TimeoutError) {
                errorMessage = "Connection timeout. Please check your internet or try again later.";
            } else if (error instanceof com.android.volley.NoConnectionError) {
                errorMessage = "No internet connection. Please check your network settings.";
            } else if (error.networkResponse != null) {
                int statusCode = error.networkResponse.statusCode;
                if (statusCode == 401) {
                    errorMessage = "Invalid email or password.";
                    isCredentialError = true;
                } else if (statusCode == 400) {
                    errorMessage = "Missing email or password.";
                    isCredentialError = true;
                } else if (statusCode == 500) {
                    errorMessage = "Server error. Please try again later.";
                }

                // Try to parse server message if available
                try {
                    String responseBody = new String(error.networkResponse.data, StandardCharsets.UTF_8);
                    JSONObject data = new JSONObject(responseBody);
                    if (data.has("message")) {
                        errorMessage = data.getString("message");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing error response", e);
                }
            }
        }

        // Add visual error feedback to input fields
        if (isCredentialError || errorMessage.toLowerCase().contains("credential") || errorMessage.toLowerCase().contains("invalid email")) {
            emailLayout.setError(errorMessage);
            passwordLayout.setError(errorMessage);
        } else {
            // For network/server errors, maybe just show a toast or a general error
            Utilities.toastLong(this, errorMessage);
        }

        // Show error message
        Log.e(TAG, "Login error: " + errorMessage);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Re-run animations when returning to fragment
        animateViews();
    }

    private void animateViews() {
        float translationY = -50f;

        TextView appTitle = findViewById(R.id.title);
        MaterialCardView loginCard = findViewById(R.id.login_card);
        LinearLayout signupLayout = findViewById(R.id.signup_layout);

        // Set initial states
        logo.setAlpha(0f);
        logo.setTranslationY(translationY);

        appTitle.setAlpha(0f);
        appTitle.setTranslationY(translationY);

        loginCard.setAlpha(0f);
        loginCard.setTranslationY(translationY);

        signupLayout.setAlpha(0f);
        signupLayout.setTranslationY(translationY);

        // Animate logo
        logo.animate()
                .alpha(1f)
                .translationY(0)
                .setDuration(500)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        // Heartbeat effect for logo (scale up-down repeatedly)
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(logo, View.SCALE_X,
                1f, 1.3f, 1.15f, 1.3f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(logo, View.SCALE_Y,
                1f, 1.3f, 1.15f, 1.3f, 1f);
        scaleX.setDuration(700);
        scaleY.setDuration(700);
        scaleX.setInterpolator(new DecelerateInterpolator());
        scaleY.setInterpolator(new DecelerateInterpolator());

        AnimatorSet heartbeat = new AnimatorSet();
        heartbeat.playTogether(scaleX, scaleY);

        final int maxBeats = 3;
        final int[] beatCount = {0};

        heartbeat.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                beatCount[0]++;
                if (beatCount[0] < maxBeats) {
                    heartbeat.start();
                }
            }
        });

        heartbeat.setStartDelay(500);
//        heartbeat.start();



        // Animate app title
        appTitle.animate()
                .alpha(1f)
                .translationY(0)
                .setStartDelay(100)
                .setDuration(500)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        // Animate login card
        loginCard.animate()
                .alpha(1f)
                .translationY(0)
                .setStartDelay(200)
                .setDuration(500)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        // Animate signup layout
        signupLayout.animate()
                .alpha(1f)
                .translationY(0)
                .setStartDelay(300)
                .setDuration(500)
                .setInterpolator(new DecelerateInterpolator())
                .start();




    }
}
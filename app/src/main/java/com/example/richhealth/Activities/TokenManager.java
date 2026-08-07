package com.example.richhealth.Activities;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.auth0.android.jwt.JWT;

import org.json.JSONException;
import org.json.JSONObject;
import Database.DatabaseHelper;
import Utils.ApiConfig;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class TokenManager {
    public static final String PREF_NAME = "RichHealthPrefs";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";
    public static final String KEY_TOKEN_EXPIRATION = "token_expiration";
    private static final String TAG = "TokenManager";


    private SharedPreferences sharedPreferences;
    private SharedPreferences.Editor editor;
    private Context context;

    // Singleton instance
    private static TokenManager instance;

    private TokenManager(Context context) {
        this.context = context.getApplicationContext();
        sharedPreferences = this.context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = sharedPreferences.edit();
    }

    public static synchronized TokenManager getInstance(Context context) {
        if (instance == null) {
            instance = new TokenManager(context);
        }
        return instance;
    }

    // Save login information with expiration
    public void saveLoginInfo(String token, String userId) {
        // Decode JWT to get expiration
        long expirationTime = getTokenExpirationTime(token);
        Log.d(TAG, "Saving Login Info");
        Log.d(TAG, "Token: " + token);
        Log.d(TAG, "User ID: " + userId);
        Log.d(TAG, "Expiration Time: " + expirationTime);

        editor.putString(KEY_ACCESS_TOKEN, token);
        editor.putString(KEY_USER_ID, userId);
        editor.putBoolean(KEY_IS_LOGGED_IN, true);
        editor.putLong(KEY_TOKEN_EXPIRATION, expirationTime);
        editor.apply();
    }

    // Check if user is logged in and token is not expired
    public boolean isLoggedIn() {
        boolean isLoggedIn = sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false);
        String token = sharedPreferences.getString(KEY_ACCESS_TOKEN, null);         // Check if token exists
        long expirationTime = sharedPreferences.getLong(KEY_TOKEN_EXPIRATION, 0);

        Log.d(TAG, "isLoggedIn: " + isLoggedIn);
        Log.d(TAG, "Token: " + (token != null ? "Present" : "Null"));
        Log.d(TAG, "Expiration Time: " + expirationTime);
        Log.d(TAG, "Current Time: " + (System.currentTimeMillis() / 1000));
        if (!isLoggedIn) return false;

        if (token == null) return false;

        return !isTokenExpired();
    }

    // Check if token is expired
    public boolean isTokenExpired() {
        long currentTime = System.currentTimeMillis() / 1000;
        long expirationTime = sharedPreferences.getLong(KEY_TOKEN_EXPIRATION, 0);

        // If expiration time is 0 or in the past, consider token expired
        return expirationTime <= currentTime;
    }

    // Extract expiration time from JWT token
    private long getTokenExpirationTime(String token) {
        try {
            // Decode JWT without verification (for expiration)
            JWT jwt = new JWT(token);
            Date expiresAt = jwt.getExpiresAt();
            return expiresAt != null ? expiresAt.getTime() / 1000 : 0;
        } catch (Exception e) {
            Log.e("TokenManager", "Error decoding token", e);
            return 0;
        }
    }

    // Get access token
    public String getToken() {
        return sharedPreferences.getString(KEY_ACCESS_TOKEN, null);
    }

    // Get user ID
    public String getUserId() {
        return sharedPreferences.getString(KEY_USER_ID, null);
    }

    // Logout — clear all user-specific caches
    public void logout() {
        editor.clear();
        editor.apply();

        // Clear all cached health data so next user doesn't see stale data
        context.getSharedPreferences("user_analysis_cache", Context.MODE_PRIVATE).edit().clear().apply();
        context.getSharedPreferences("aqi_prefs", Context.MODE_PRIVATE).edit().clear().apply();
        context.getSharedPreferences("dietary_insights_prefs", Context.MODE_PRIVATE).edit().clear().apply();
        context.getSharedPreferences("pro_status_prefs", Context.MODE_PRIVATE).edit().clear().apply();

        // Clear local SQLite database (symptoms, measurements, medications, chat, reports, profile)
        new DatabaseHelper(context).clearUserData();
    }

    // Refresh token (optional)
    public void refreshToken(Context context) {
        // Only attempt refresh if logged in
        if (!isLoggedIn()) {
            logout();
            return;
        }

        String url = ApiConfig.BASE_URL + "/api/auth/refresh-token";
        StringRequest refreshRequest = new StringRequest(Request.Method.POST, url,
                response -> {
                    ApiConfig.logRestCall(url, true, "Token refreshed");
                    try {
                        JSONObject jsonResponse = new JSONObject(response);
                        String newToken = jsonResponse.getString("token");
                        String userId = jsonResponse.getString("userId");

                        // Save new token
                        saveLoginInfo(newToken, userId);
                    } catch (JSONException e) {
                        Log.e(TAG, "Token refresh parse error", e);
                        logout();
                    }
                },
                error -> {
                    ApiConfig.logRestCall(url, false, error.toString());
                    Log.e(TAG, "Token refresh failed", error);
                    logout();
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + getToken());
                return headers;
            }
        };

        // Add to request queue
        Volley.newRequestQueue(context).add(refreshRequest);
    }
}
package Utils;

import android.content.Context;
import android.content.SharedPreferences;

public class SessionManager {
    // Shared Preferences
    private SharedPreferences pref;
    private SharedPreferences.Editor editor;

    // Shared preferences file name
    private static final String PREF_NAME = "RichHealthSession";

    // All Shared Preferences Keys
    private static final String IS_LOGGED_IN = "IsLoggedIn";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_NAME = "name";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_TOKEN = "token";

    // Constructor
    public SessionManager(Context context) {
        int PRIVATE_MODE = 0;
        pref = context.getSharedPreferences(PREF_NAME, PRIVATE_MODE);
        editor = pref.edit();
    }

    // Login session management
    public void createLoginSession(String userId, String name, String email, String token) {
        // Storing login value as TRUE
        editor.putBoolean(IS_LOGGED_IN, true);

        // Store user data
        editor.putString(KEY_USER_ID, userId);
        editor.putString(KEY_NAME, name);
        editor.putString(KEY_EMAIL, email);
        editor.putString(KEY_TOKEN, token);

        // Commit changes
        editor.apply();
    }

    // Check login status
    public boolean isLoggedIn() {
        return pref.getBoolean(IS_LOGGED_IN, false);
    }

    // Get stored session data
    public String getUserIdString() {
        return pref.getString(KEY_USER_ID, "0");
    }

    public long getUserId() {
        try {
            return Long.parseLong(pref.getString(KEY_USER_ID, "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public String getName() {
        return pref.getString(KEY_NAME, "");
    }

    public String getEmail() {
        return pref.getString(KEY_EMAIL, "");
    }

    public String getToken() {
        return pref.getString(KEY_TOKEN, "");
    }

    // Clear session details
    public void logoutUser() {
        // Clear all data from Shared Preferences
        editor.clear();
        editor.apply();
    }
}
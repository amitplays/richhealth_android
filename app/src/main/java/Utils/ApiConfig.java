package Utils;

import android.util.Log;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ApiConfig {
    // Base URL for the API
    // Local development server
//    public static final String BASE_URL = "http://192.168.2.114:5000";
    // Production server — uncomment below and comment the local one when deploying
//     public static final String BASE_URL = "https://richhealthbackend.onrender.com";
     public static final String BASE_URL = "https://richhealthbackend.vercel.app";

    // API endpoints
    public static final String LOGIN_URL = BASE_URL + "/api/auth/login";
    public static final String REGISTER_URL = BASE_URL + "/api/auth/signup";
    public static final String PROFILE_URL = BASE_URL + "/api/user/profile";

    public static String getBaseUrl() {
        return BASE_URL;
    }

    /**
     * Logs a REST call in a single line.
     * Format: [Timestamp] Endpoint | Status | Message
     */
    public static void logRestCall(String url, boolean success, String message) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        String status = success ? "SUCCESS" : "FAILED";
        Log.d("REST_CALL", String.format("[%s] %s | %s | %s", timestamp, url, status, message));
    }
}
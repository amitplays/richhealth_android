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

    // ── AirVisual (IQAir) — air quality provider ──────────────────────────────
    //
    // THE ONE PLACE to change this key on Android. It used to be pasted inline into the
    // URL in TWO fragments (HomeFragment.fetchAqiFromIQAir and ProfileFragment), so
    // rotating it meant finding both and hoping there was not a third. iOS's equivalent
    // is APIConfig.airVisualKey.
    //
    // Moving it here does NOT make it private: it is still compiled into the APK and
    // anyone can read it out, so rotating it buys nothing on its own. The only fix that
    // works is proxying the call through our own aqiController, so the key lives in a
    // server env var and neither app ever carries it.
    public static final String AIRVISUAL_KEY = "14e2baae-46bf-441a-8ec8-642da0410050";
    public static final String AIRVISUAL_NEAREST_CITY_URL =
            "https://api.airvisual.com/v2/nearest_city";

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
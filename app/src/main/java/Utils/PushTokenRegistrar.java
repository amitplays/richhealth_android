package Utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.example.richhealth.Activities.TokenManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Registers this device's FCM token with the backend so it can receive push notifications.
 *
 * Backend contract (../richhealthbackend/controllers/userController.js):
 *   POST   /api/user/device-token  { token, platform:"android", appId }
 *   DELETE /api/user/device-token  { token }
 * Registration is idempotent — the backend upserts on the token, so calling this on every
 * launch is correct and cheap (FCM rotates tokens).
 *
 * NOTE: this class deliberately has NO Firebase dependency. It takes whatever token string
 * it is handed, so it compiles and works today. Obtaining that token still needs Firebase
 * Cloud Messaging wired into the project (google-services.json + the google-services Gradle
 * plugin + firebase-messaging + a FirebaseMessagingService that calls
 * {@link #register(Context, String)} from onNewToken). Until that lands nothing calls this,
 * and the app behaves exactly as before.
 */
public class PushTokenRegistrar {

    private static final String TAG = "PushTokenRegistrar";
    private static final String PREFS = "push_prefs";
    private static final String KEY_LAST_TOKEN = "last_registered_token";

    private PushTokenRegistrar() {}

    /** Remembers the last token we successfully registered, so logout can un-register it. */
    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Call from FirebaseMessagingService.onNewToken and once per launch after login. */
    public static void register(Context context, String fcmToken) {
        if (context == null || fcmToken == null || fcmToken.isEmpty()) return;
        final Context appContext = context.getApplicationContext();
        final String authToken = TokenManager.getInstance(appContext).getToken();
        if (authToken == null || authToken.isEmpty()) return;   // not signed in — nothing to attach it to

        JSONObject body = new JSONObject();
        try {
            body.put("token", fcmToken);
            body.put("platform", "android");
            body.put("appId", appContext.getPackageName());
        } catch (JSONException e) {
            Log.e(TAG, "Failed to build register body", e);
            return;
        }

        send(appContext, Request.Method.POST, body, authToken, () ->
                prefs(appContext).edit().putString(KEY_LAST_TOKEN, fcmToken).apply());
    }

    /**
     * Removes this device from the signed-in account. MUST be called BEFORE the auth token is
     * cleared, which is why the caller passes it in explicitly rather than letting us read it
     * back from TokenManager mid-logout.
     */
    public static void unregister(Context context, String authToken) {
        if (context == null || authToken == null || authToken.isEmpty()) return;
        final Context appContext = context.getApplicationContext();
        final String fcmToken = prefs(appContext).getString(KEY_LAST_TOKEN, null);
        if (fcmToken == null || fcmToken.isEmpty()) return;

        JSONObject body = new JSONObject();
        try {
            body.put("token", fcmToken);
        } catch (JSONException e) {
            return;
        }

        send(appContext, Request.Method.DELETE, body, authToken, () ->
                prefs(appContext).edit().remove(KEY_LAST_TOKEN).apply());
    }

    private interface OnOk { void run(); }

    private static void send(Context appContext, int method, JSONObject body,
                             String authToken, OnOk onOk) {
        final String path = "/api/user/device-token";
        final String url = ApiConfig.BASE_URL + path;
        StringRequest request = new StringRequest(method, url,
                response -> {
                    ApiConfig.logRestCall(path, true, "device token synced");
                    if (onOk != null) onOk.run();
                },
                error -> ApiConfig.logRestCall(path, false, error.toString())
        ) {
            @Override public byte[] getBody() { return body.toString().getBytes(StandardCharsets.UTF_8); }
            @Override public String getBodyContentType() { return "application/json; charset=utf-8"; }
            @Override public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + authToken);
                return headers;
            }
        };
        request.setRetryPolicy(new DefaultRetryPolicy(15000, 1, 1f));
        Volley.newRequestQueue(appContext).add(request);
    }
}

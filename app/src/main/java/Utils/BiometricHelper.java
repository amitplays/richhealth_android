package Utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

/**
 * Manages biometric authentication preference and prompt display.
 * Biometric data never leaves the device — only a boolean preference is stored.
 */
public class BiometricHelper {
    private static final String TAG = "BiometricHelper";
    private static final String PREFS_NAME = "RichHealthPrefs";
    private static final String KEY_BIOMETRIC_ENABLED = "biometric_enabled";
    // Whether we've already offered biometric setup on this device — so the offer
    // isn't shown on every login. (Enablement itself is device-specific and stays
    // local; biometric enrollment can't be shared across devices.)
    private static final String KEY_BIOMETRIC_PROMPTED = "biometric_setup_prompted";

    /** True once the biometric setup offer has been shown on this device. */
    public static boolean hasPromptedBiometricSetup(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_BIOMETRIC_PROMPTED, false);
    }

    public static void setPromptedBiometricSetup(Context context, boolean prompted) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_BIOMETRIC_PROMPTED, prompted).apply();
    }

    /**
     * Returns true if the user has opted in to biometric lock.
     */
    public static boolean isBiometricEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false);
    }

    /**
     * Persist the user's biometric preference.
     */
    public static void setBiometricEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_BIOMETRIC_ENABLED, enabled)
                .apply();
        Log.d(TAG, "Biometric enabled set to: " + enabled);
    }

    /**
     * Persist the biometric preference to the account (Mongo) so the one-time setup
     * offer isn't shown again after a reinstall or on a new device, and so the choice
     * follows the user. Best-effort/fire-and-forget — device-local prefs stay the
     * source of truth for actually locking this device. Mirrors the T&C sync pattern.
     *
     * @param enabled the user's biometric-lock choice; promptShown is always set true.
     */
    public static void persistBiometricToServer(Context context, boolean enabled) {
        try {
            final String token = com.example.richhealth.Activities.TokenManager
                    .getInstance(context).getToken();
            if (token == null) return;
            String url = ApiConfig.BASE_URL + "/api/user/profile";
            org.json.JSONObject body = new org.json.JSONObject();
            body.put("biometricEnabled", enabled);
            body.put("biometricPromptShown", true);
            final byte[] payload = body.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            com.android.volley.toolbox.StringRequest req = new com.android.volley.toolbox.StringRequest(
                    com.android.volley.Request.Method.PUT, url,
                    r -> {}, e -> Log.w(TAG, "biometric pref sync failed: " + e)) {
                @Override public byte[] getBody() { return payload; }
                @Override public String getBodyContentType() { return "application/json; charset=utf-8"; }
                @Override public java.util.Map<String, String> getHeaders() {
                    java.util.Map<String, String> h = new java.util.HashMap<>();
                    h.put("Authorization", "Bearer " + token);
                    return h;
                }
            };
            com.android.volley.toolbox.Volley.newRequestQueue(context).add(req);
        } catch (Exception ignored) {}
    }

    /**
     * Returns true if the device has biometric hardware and at least one
     * fingerprint/face enrolled.
     */
    public static boolean canAuthenticate(Context context) {
        BiometricManager mgr = BiometricManager.from(context);
        int result = mgr.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK);
        return result == BiometricManager.BIOMETRIC_SUCCESS;
    }

    /**
     * Returns a human-readable reason when biometric is unavailable.
     */
    public static String getUnavailableReason(Context context) {
        BiometricManager mgr = BiometricManager.from(context);
        int result = mgr.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK);
        switch (result) {
            case BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE:
                return "This device does not have biometric hardware.";
            case BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE:
                return "Biometric hardware is currently unavailable.";
            case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED:
                return "No biometrics enrolled. Please set up fingerprint or face unlock in your device settings.";
            default:
                return "Biometric authentication is not available.";
        }
    }

    /** Error callback that carries the biometric error code so callers can react
     *  differently to a real user-cancel vs a transient/system cancellation. */
    public interface OnBiometricError {
        void onError(int errorCode, CharSequence errString);
    }

    /** True when the error is a deliberate user decision to cancel (vs a transient
     *  system cancellation like the app briefly pausing). Callers should only take
     *  disruptive action (e.g. closing the app) for these. */
    public static boolean isUserCancel(int errorCode) {
        return errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                || errorCode == BiometricPrompt.ERROR_USER_CANCELED
                || errorCode == BiometricPrompt.ERROR_LOCKOUT
                || errorCode == BiometricPrompt.ERROR_LOCKOUT_PERMANENT;
    }

    /**
     * Show the system biometric prompt.
     *
     * @param activity the hosting FragmentActivity
     * @param onSuccess called when authentication succeeds
     * @param onError   called with the error code when auth fails / is cancelled
     */
    public static void authenticate(FragmentActivity activity,
                                    Runnable onSuccess,
                                    OnBiometricError onError) {
        // Guard: never launch a prompt against a dead activity — that is exactly what
        // leaves the biometric receiver dangling and produces DeadObjectException.
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            if (onError != null) onError.onError(BiometricPrompt.ERROR_CANCELED, "Activity unavailable");
            return;
        }

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("RichHealth")
                .setSubtitle("Verify your identity to continue")
                .setNegativeButtonText("Cancel")
                .build();

        BiometricPrompt biometricPrompt = new BiometricPrompt(activity,
                ContextCompat.getMainExecutor(activity),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        Log.d(TAG, "Biometric authentication succeeded");
                        if (onSuccess != null && !activity.isFinishing() && !activity.isDestroyed()) onSuccess.run();
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        Log.d(TAG, "Biometric auth error(" + errorCode + "): " + errString);
                        if (onError != null) onError.onError(errorCode, errString);
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        super.onAuthenticationFailed();
                        // Don't call onError here — the system shows "Not recognized" and
                        // lets the user retry. onAuthenticationError fires on final failure.
                        Log.d(TAG, "Biometric attempt failed, user can retry");
                    }
                });

        try {
            biometricPrompt.authenticate(promptInfo);
        } catch (Exception e) {
            Log.e(TAG, "authenticate() threw", e);
            if (onError != null) onError.onError(BiometricPrompt.ERROR_HW_UNAVAILABLE, "Prompt failed");
        }
    }
}

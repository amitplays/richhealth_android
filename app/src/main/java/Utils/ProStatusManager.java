package Utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.example.richhealth.Activities.TokenManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Enhanced singleton class to manage the Pro status of a user
 */
public class ProStatusManager {
    private static final String TAG = "ProStatusManager";
    private static final String PREF_NAME = "pro_status_prefs";
    private static final String KEY_IS_PRO = "is_pro_user";
    private static final String KEY_EXPIRY_DATE = "pro_expiry_date";
    private static final String KEY_SUBSCRIPTION_PLAN = "pro_subscription_plan";
    private static final String KEY_TRANSACTION_ID = "last_transaction_id";
    private static final String KEY_UPGRADE_DATE = "pro_upgrade_date";
    private static final String KEY_LAST_SYNC_TIME = "last_sync_time";
    private static final String KEY_IS_FAMILY_PLAN_OWNER = "is_family_plan_owner";
    private static final String KEY_IS_GRANTED_PRO = "is_granted_pro";
    private static final String KEY_PRO_GRANTED_BY = "pro_granted_by";
    private static final String KEY_FAMILY_MEMBER_COUNT = "family_member_count";
    private static final String KEY_MAX_FAMILY_MEMBERS = "max_family_members";
    private static final String KEY_REPORTS_USED = "reports_used";
    private static final String KEY_REPORTS_REMAINING = "reports_remaining";
    private static final String KEY_TOTAL_REPORTS = "total_reports";

    private static ProStatusManager instance;
    private final SharedPreferences prefs;
    private final Context context;

    private ProStatusManager(Context context) {
        this.context = context.getApplicationContext();
        prefs = this.context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Get the singleton instance of ProStatusManager
     * @param context Application context
     * @return ProStatusManager instance
     */
    public static synchronized ProStatusManager getInstance(Context context) {
        if (instance == null) {
            instance = new ProStatusManager(context);
        }
        return instance;
    }

    /**
     * Check if the user has Pro status
     * @return true if user has Pro status
     */
    public boolean isProUser() {
        // Check if pro status has expired
        long expiryDate = prefs.getLong(KEY_EXPIRY_DATE, 0);
        long currentTime = System.currentTimeMillis();

        Log.d(TAG, "isProUser: ."+ (expiryDate > 0 && expiryDate < currentTime));
        Log.d(TAG, "KEY_IS_PRO: ."+ prefs.getBoolean(KEY_IS_PRO, false));
        Log.d(TAG, "check :"+ (expiryDate > 0 && expiryDate < currentTime && prefs.getBoolean(KEY_IS_PRO, false)));

        if ((expiryDate > 0) && (expiryDate < currentTime) && (prefs.getBoolean(KEY_IS_PRO, false))) {
            setProStatus(false);
            Log.d(TAG, "Pro has expired, clearing status");
            return false;
        }

        return prefs.getBoolean(KEY_IS_PRO, false);
    }

    /**
     * Set the Pro status of the user locally
     * @param isProUser true to set as Pro, false otherwise
     */
    public void setProStatus(boolean isProUser) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(KEY_IS_PRO, isProUser);

        // If turning off Pro, clear expiry date and plan
        if (!isProUser) {
            editor.remove(KEY_EXPIRY_DATE);
            editor.remove(KEY_SUBSCRIPTION_PLAN);
            editor.remove(KEY_TRANSACTION_ID);
            editor.remove(KEY_UPGRADE_DATE);
        }

        editor.apply();
    }

    public void setProStatusComplete(boolean isProUser, long expiryDate, String plan, String transactionId) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(KEY_IS_PRO, isProUser);
        editor.putLong(KEY_EXPIRY_DATE, expiryDate);
        editor.putString(KEY_SUBSCRIPTION_PLAN, plan);
        editor.putString(KEY_TRANSACTION_ID, transactionId);
        editor.putLong(KEY_UPGRADE_DATE, System.currentTimeMillis());
        editor.putLong(KEY_LAST_SYNC_TIME, System.currentTimeMillis());
        editor.apply();

        Log.d(TAG, "Pro status updated: isPro=" + isProUser + ", plan=" + plan + ", expires=" + new Date(expiryDate).toString());
    }

    public void setFamilyPlanInfo(boolean isFamilyPlanOwner, boolean isGrantedPro,
                                   String proGrantedBy, int familyMemberCount, int maxFamilyMembers) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(KEY_IS_FAMILY_PLAN_OWNER, isFamilyPlanOwner);
        editor.putBoolean(KEY_IS_GRANTED_PRO, isGrantedPro);
        editor.putString(KEY_PRO_GRANTED_BY, proGrantedBy);
        editor.putInt(KEY_FAMILY_MEMBER_COUNT, familyMemberCount);
        editor.putInt(KEY_MAX_FAMILY_MEMBERS, maxFamilyMembers);
        editor.apply();
    }

    public boolean isFamilyPlanOwner() {
        return prefs.getBoolean(KEY_IS_FAMILY_PLAN_OWNER, false);
    }

    public boolean isGrantedPro() {
        return prefs.getBoolean(KEY_IS_GRANTED_PRO, false);
    }

    public String getProGrantedBy() {
        return prefs.getString(KEY_PRO_GRANTED_BY, null);
    }

    public int getFamilyMemberCount() {
        return prefs.getInt(KEY_FAMILY_MEMBER_COUNT, 0);
    }

    public int getMaxFamilyMembers() {
        return prefs.getInt(KEY_MAX_FAMILY_MEMBERS, 5);
    }

    public void setSubscriptionUsage(int reportsUsed, int reportsRemaining, int totalReports) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(KEY_REPORTS_USED, reportsUsed);
        editor.putInt(KEY_REPORTS_REMAINING, reportsRemaining);
        editor.putInt(KEY_TOTAL_REPORTS, totalReports);
        editor.apply();
    }

    public int getReportsUsed() {
        return prefs.getInt(KEY_REPORTS_USED, 0);
    }

    public int getReportsRemaining() {
        return prefs.getInt(KEY_REPORTS_REMAINING, 0);
    }

    public int getTotalReports() {
        return prefs.getInt(KEY_TOTAL_REPORTS, 0);
    }

    public String getUserTier() {
        if (!isProUser()) return "free";
        String plan = getSubscriptionPlan();
        if ("ultra".equals(plan)) return "ultra";
        if ("family".equals(plan)) return "family";
        if ("family_member".equals(plan)) return "family_member";
        if ("plus".equals(plan)) return "plus";
        return "pro";
    }

    /**
     * Get the expiry date of the Pro subscription
     * @return Expiry date in milliseconds, or 0 if not Pro
     */
    public long getExpiryDate() {
        return prefs.getLong(KEY_EXPIRY_DATE, 0);
    }

    /**
     * Get the subscription plan
     * @return The plan name (monthly/yearly) or empty if not Pro
     */
    public String getSubscriptionPlan() {
        return prefs.getString(KEY_SUBSCRIPTION_PLAN, "");
    }

    /**
     * Get the last transaction ID
     * @return The transaction ID or empty if none
     */
    public String getTransactionId() {
        return prefs.getString(KEY_TRANSACTION_ID, "");
    }

    /**
     * Get the upgrade date
     * @return The upgrade date in milliseconds, or 0 if not Pro
     */
    public long getUpgradeDate() {
        return prefs.getLong(KEY_UPGRADE_DATE, 0);
    }

    /**
     * Format expiry date as a readable string
     * @return Formatted date string or "Not subscribed" if not Pro
     */
    public String getFormattedExpiryDate() {
        long expiryDate = getExpiryDate();
        if (expiryDate > 0) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
            return dateFormat.format(new Date(expiryDate));
        }
        return "Not subscribed";
    }

    /**
     * Format upgrade date as a readable string
     * @return Formatted date string or "Not available" if not Pro
     */
    public String getFormattedUpgradeDate() {
        long upgradeDate = getUpgradeDate();
        if (upgradeDate > 0) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
            return dateFormat.format(new Date(upgradeDate));
        }
        return "Not available";
    }

    public static void syncProStatusOnLogin(Context context) {
        Log.d(TAG, "Syncing Pro status on login/home screen");
        ProStatusManager proStatusManager = getInstance(context);
        boolean currentStatus = proStatusManager.isProUser();
        Log.d(TAG, "Current local Pro status: " + currentStatus);

        PaymentService paymentService = new PaymentService(context);
        paymentService.getProStatus(new PaymentService.PaymentCallback() {
            @Override
            public void onSuccess(ProStatusResult result) {
                proStatusManager.setProStatusComplete(
                        result.isPro(),
                        result.getExpiryDate(),
                        result.getPlan(),
                        result.getTransactionId()
                );
                proStatusManager.setFamilyPlanInfo(
                        result.isFamilyPlanOwner(),
                        result.isGrantedPro(),
                        result.getProGrantedBy(),
                        result.getFamilyProMemberCount(),
                        result.getMaxFamilyMembers()
                );
                proStatusManager.setSubscriptionUsage(
                        result.getReportsUsed(),
                        result.getReportsRemaining(),
                        result.getTotalReports()
                );
                Log.d(TAG, "Pro status synced from server: isPro=" + result.isPro() +
                        ", plan=" + result.getPlan());
            }

            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "Failed to sync pro status from server: " + errorMessage);
            }
        });
    }

    public static void checkProStatusOnSplash(Context context) {
        Log.d(TAG, "Checking Pro status on splash screen");
        ProStatusManager proStatusManager = getInstance(context);
        boolean isProUser = proStatusManager.isProUser();
        Log.d(TAG, "Pro status on splash: " + isProUser);
    }

    /**
     * Check Pro access from server on app startup.
     * Backend auto-expires Pro, so this ensures local status is in sync.
     * @param context Application context
     * @param callback Optional callback for result
     */
    public static void checkProAccessOnStartup(Context context, ProStatusCallback callback) {
        Log.d(TAG, "Checking Pro access from server on startup");
        
        TokenManager tokenManager = TokenManager.getInstance(context);
        String token = tokenManager.getToken();
        
        if (token == null) {
            Log.d(TAG, "No token available, skipping pro access check");
            if (callback != null) callback.onSyncError("Not authenticated");
            return;
        }

        String url = ApiConfig.BASE_URL + "/api/user/pro-access";

        StringRequest request = new StringRequest(Request.Method.GET, url,
                response -> {
                    ApiConfig.logRestCall(url, true, "Pro access checked");
                    try {
                        JSONObject jsonResponse = new JSONObject(response);
                        boolean isPro = jsonResponse.optBoolean("isPro", false);
                        long expiryDate = jsonResponse.optLong("expiryDate", 0);
                        String tier = jsonResponse.optString("tier", "");

                        ProStatusManager proStatusManager = getInstance(context);

                        if (isPro) {
                            // Use tier from server if valid, otherwise keep locally stored plan
                            String planToUse = (!tier.isEmpty() && !"free".equals(tier))
                                    ? tier : proStatusManager.getSubscriptionPlan();
                            // Use server expiry if provided; otherwise keep the locally stored expiry
                            long expiryToUse = expiryDate > 0 ? expiryDate : proStatusManager.getExpiryDate();
                            proStatusManager.setProStatusComplete(true, expiryToUse,
                                    planToUse, proStatusManager.getTransactionId());
                            Log.d(TAG, "Pro access confirmed from server, tier: " + planToUse + ", expires: " + new Date(expiryToUse));
                        } else {
                            proStatusManager.setProStatus(false);
                            Log.d(TAG, "Pro access expired or not active according to server");
                        }

                        if (callback != null) callback.onStatusSynced(isPro);
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing pro access response", e);
                        if (callback != null) callback.onSyncError("Parse error");
                    }
                },
                error -> {
                    ApiConfig.logRestCall(url, false, error.toString());
                    Log.e(TAG, "Error checking pro access: " + error.toString());
                    if (callback != null) callback.onSyncError("Network error");
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + token);
                return headers;
            }
        };

        Volley.newRequestQueue(context).add(request);
    }

    /**
     * Callback interface for Pro status operations
     */
    public interface ProStatusCallback {
        void onStatusSynced(boolean isPro);
        void onSyncError(String error);
    }
}
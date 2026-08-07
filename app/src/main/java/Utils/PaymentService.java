package Utils;

import android.content.Context;
import android.util.Log;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.example.richhealth.Activities.TokenManager;
import Utils.ApiConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import Models.PlanOption;

/**
 * Service for handling payment API calls
 */
public class PaymentService {
    private static final String TAG = "PaymentService";
    private static final String BASE_URL = ApiConfig.BASE_URL + "/api/payment";

    private final Context context;
    private final TokenManager tokenManager;
    private final RequestQueue requestQueue;

    /**
     * Callback interface for payment API responses
     */
    public interface PaymentCallback {
        void onSuccess(ProStatusResult result);
        void onError(String errorMessage);
    }

    public PaymentService(Context context) {
        this.context = context;
        this.tokenManager = TokenManager.getInstance(context);
        this.requestQueue = Volley.newRequestQueue(context);
    }

    /**
     * Initiate payment with a known PlanOption (preferred — planId comes from backend catalog).
     */
    public void initiatePayment(PlanOption plan, PaymentCallback callback) {
        initiatePaymentById(plan.getPlanId(), plan.getPrice(), null, callback);
    }

    public void initiatePayment(PlanOption plan, String couponCode, PaymentCallback callback) {
        initiatePaymentById(plan.getPlanId(), plan.getPrice(), couponCode, callback);
    }

    private void initiatePaymentById(int planId, double amount, String couponCode, PaymentCallback callback) {
        String url = BASE_URL + "/initiate";

        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("planId", planId);
            requestBody.put("amount", amount);
            requestBody.put("paymentMethod", "RAZORPAY");
            if (couponCode != null && !couponCode.trim().isEmpty()) {
                requestBody.put("couponCode", couponCode.trim());
            }

            JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, requestBody,
                    response -> {
                        ApiConfig.logRestCall(url, true, "Payment initiated");
                        try {
                            ProStatusResult result = new ProStatusResult();
                            result.setTransactionId(response.getString("transactionId"));
                            
                            // RazorPay returns orderId and keyId instead of paymentUrl
                            result.setRazorpayOrderId(response.getString("orderId"));
                            result.setRazorpayKeyId(response.getString("keyId"));
                            result.setAmount(response.getDouble("amount"));

                            if (response.has("planId")) {
                                result.setPlan(response.getString("planId"));
                            }

                            callback.onSuccess(result);
                        } catch (JSONException e) {
                            Log.e(TAG, "Error parsing payment initiation response", e);
                            callback.onError("Failed to parse server response");
                        }
                    },
                    error -> {
                        ApiConfig.logRestCall(url, false, error.toString());
                        String errorMessage = parseErrorResponse(error);
                        Log.e(TAG, "Payment initiation error: " + errorMessage, error);
                        callback.onError(errorMessage);
                    }) {
                @Override
                public Map<String, String> getHeaders() throws AuthFailureError {
                    Map<String, String> headers = new HashMap<>();
                    String token = tokenManager.getToken();
                    if (token != null) {
                        headers.put("Authorization", "Bearer " + token);
                    }
                    return headers;
                }
            };

            requestQueue.add(request);
        } catch (JSONException e) {
            Log.e(TAG, "Error creating payment initiation request", e);
            callback.onError("Failed to create payment request");
        }
    }

    public void verifyPayment(String transactionId, String razorpayPaymentId,
                              String razorpayOrderId, String razorpaySignature,
                              PaymentCallback callback) {
        verifyPayment(transactionId, razorpayPaymentId, razorpayOrderId, razorpaySignature, null, callback);
    }

    public void verifyPayment(String transactionId, String razorpayPaymentId,
                              String razorpayOrderId, String razorpaySignature,
                              java.util.List<String> familyMemberIds,
                              PaymentCallback callback) {
        String url = BASE_URL + "/verify";

        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("transactionId", transactionId);
            requestBody.put("razorpayPaymentId", razorpayPaymentId);
            requestBody.put("razorpayOrderId", razorpayOrderId);
            requestBody.put("razorpaySignature", razorpaySignature);

            if (familyMemberIds != null && !familyMemberIds.isEmpty()) {
                org.json.JSONArray memberIds = new org.json.JSONArray(familyMemberIds);
                requestBody.put("familyMemberIds", memberIds);
            }

            JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, requestBody,
                    new Response.Listener<JSONObject>() {
                        @Override
                        public void onResponse(JSONObject response) {
                            ApiConfig.logRestCall(url, true, "Payment verified");
                            try {
                                boolean success = response.getBoolean("success");
                                if (success) {
                                    ProStatusResult result = new ProStatusResult();
                                    result.setPro(true);
                                    result.setExpiryDate(response.optLong("expiryDate", 0));
                                    result.setPlan(response.optString("plan", ""));
                                    result.setMaxFamilyMembers(response.optInt("maxFamilyMembers", 5));

                                    JSONArray activated = response.optJSONArray("activatedMembers");
                                    if (activated != null) {
                                        java.util.List<String> members = new java.util.ArrayList<>();
                                        for (int i = 0; i < activated.length(); i++) {
                                            members.add(activated.optString(i, ""));
                                        }
                                        result.setFamilyProMembers(members);
                                    }

                                    callback.onSuccess(result);
                                } else {
                                    String status = response.optString("status", "unknown");
                                    String message = response.optString("message", "Payment verification failed");
                                    callback.onError(message + " (Status: " + status + ")");
                                }
                            } catch (JSONException e) {
                                Log.e(TAG, "Error parsing payment verification response", e);
                                callback.onError("Failed to parse server response");
                            }
                        }
                    },
                    new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            ApiConfig.logRestCall(url, false, error.toString());
                            String errorMessage = parseErrorResponse(error);
                            Log.e(TAG, "Payment verification error: " + errorMessage, error);
                            callback.onError(errorMessage);
                        }
                    }) {
                @Override
                public Map<String, String> getHeaders() throws AuthFailureError {
                    Map<String, String> headers = new HashMap<>();
                    String token = tokenManager.getToken();
                    if (token != null) {
                        headers.put("Authorization", "Bearer " + token);
                    }
                    return headers;
                }
            };

            requestQueue.add(request);
        } catch (JSONException e) {
            Log.e(TAG, "Error creating payment verification request", e);
            callback.onError("Failed to create verification request");
        }
    }

    /**
     * Get Pro status from the server
     * @param callback Callback to handle the response
     */
    public void getProStatus(PaymentCallback callback) {
        String url = BASE_URL + "/pro-status";

        // Don't call the server without a session — it would fire an unauthenticated
        // request that always 401s ("No token provided"). Happens briefly around
        // login/logout; just report not-signed-in instead of spamming the network.
        String authToken = tokenManager != null ? tokenManager.getToken() : null;
        if (authToken == null || authToken.isEmpty()) {
            if (callback != null) callback.onError("Not signed in");
            return;
        }

        StringRequest request = new StringRequest(Request.Method.GET, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        ApiConfig.logRestCall(url, true, "Pro status fetched");
                        try {
                            JSONObject jsonResponse = new JSONObject(response);
                            ProStatusResult result = new ProStatusResult();
                            result.setPro(jsonResponse.getBoolean("isPro"));
                            result.setExpiryDate(jsonResponse.optLong("expiryDate", 0));
                            result.setPlan(jsonResponse.optString("plan", ""));
                            result.setTransactionId(jsonResponse.optString("transactionId", ""));
                            result.setUpgradeDate(jsonResponse.optLong("upgradeDate", 0));

                            result.setFamilyPlanOwner(jsonResponse.optBoolean("isFamilyPlanOwner", false));
                            result.setGrantedPro(jsonResponse.optBoolean("isGrantedPro", false));
                            result.setProGrantedBy(jsonResponse.optString("proGrantedBy", null));
                            result.setMaxFamilyMembers(jsonResponse.optInt("maxFamilyMembers", 5));
                            result.setFamilyMembersCount(jsonResponse.optInt("familyProMemberCount", 0));

                            if (jsonResponse.has("familyProMembers")) {
                                JSONArray membersArray = jsonResponse.getJSONArray("familyProMembers");
                                java.util.List<String> members = new java.util.ArrayList<>();
                                for (int i = 0; i < membersArray.length(); i++) {
                                    members.add(membersArray.getString(i));
                                }
                                result.setFamilyProMembers(members);
                            }

                            result.setSubscriptionActive(jsonResponse.optBoolean("isSubscriptionActive", false));

                            JSONObject subscription = jsonResponse.optJSONObject("subscription");
                            if (subscription != null) {
                                result.setPlanType(subscription.optInt("planType", 0));
                                result.setStartDate(subscription.optLong("startDate", 0));
                                result.setEndDate(subscription.optLong("endDate", 0));
                                result.setTotalReports(subscription.optInt("totalReports", 0));
                                result.setReportsUsed(subscription.optInt("reportsUsed", 0));
                                result.setReportsRemaining(subscription.optInt("reportsRemaining", 0));
                                result.setFamilyMembersCount(subscription.optInt("familyMembersCount", 0));
                                result.setMaxFamilyMembers(subscription.optInt("maxFamilyMembers", 0));
                            }

                            JSONObject familySubscription = jsonResponse.optJSONObject("familySubscription");
                            if (familySubscription != null) {
                                result.setOwnerId(familySubscription.optString("ownerId", ""));
                                result.setOwnerName(familySubscription.optString("ownerName", ""));
                                result.setOwnerEmail(familySubscription.optString("ownerEmail", ""));
                                result.setExpiryDate(familySubscription.optLong("expiryDate", 0));
                                result.setPlanType(familySubscription.optInt("planType", 0));
                                result.setTotalReports(familySubscription.optInt("totalReports", 0));
                                result.setReportsUsed(familySubscription.optInt("reportsUsed", 0));
                                result.setPersonalReportsUsed(familySubscription.optInt("personalReportsUsed", 0));
                            }

                            callback.onSuccess(result);
                        } catch (JSONException e) {
                            Log.e(TAG, "Error parsing Pro status response", e);
                            callback.onError("Failed to parse server response");
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        ApiConfig.logRestCall(url, false, error.toString());
                        String errorMessage = parseErrorResponse(error);
                        Log.e(TAG, "Pro status error: " + errorMessage, error);
                        callback.onError(errorMessage);
                    }
                }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                String token = tokenManager.getToken();
                if (token != null) {
                    headers.put("Authorization", "Bearer " + token);
                }
                return headers;
            }
        };

        requestQueue.add(request);
    }

    /**
     * @deprecated This method called a /track-report endpoint that never existed on the backend.
     *             Report counts are derived from MedicalReport documents and returned by
     *             GET /api/payment/pro-status. Use {@link #getProStatus(PaymentCallback)} instead
     *             and read reportsUsed / reportsRemaining from the result.
     */
    @Deprecated
    public void trackReportUsage(PaymentCallback callback) {
        Log.e(TAG, "trackReportUsage() is deprecated — no backend route exists. " +
                    "Use getProStatus() to fetch current report counts.");
        // Fetch accurate report counts from pro-status instead of hitting a nonexistent endpoint
        getProStatus(callback);
    }

    /**
     * Add a family member to the plan without additional payment.
     * Works for Ultra/Family plan holders — family members are included.
     */
    public void addFamilyMemberDirect(String memberId, PaymentCallback callback) {
        String url = BASE_URL + "/family-member/add";
        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("memberId", memberId);

            JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, requestBody,
                    response -> {
                        ApiConfig.logRestCall(url, true, "Family member added");
                        try {
                            boolean success = response.getBoolean("success");
                            if (success) {
                                ProStatusResult result = new ProStatusResult();
                                result.setPro(true);
                                if (response.has("familyProMembers")) {
                                    JSONArray arr = response.getJSONArray("familyProMembers");
                                    java.util.List<String> members = new java.util.ArrayList<>();
                                    for (int i = 0; i < arr.length(); i++) {
                                        members.add(arr.getString(i));
                                    }
                                    result.setFamilyProMembers(members);
                                }
                                callback.onSuccess(result);
                            } else {
                                callback.onError(response.optString("message", "Failed to add member"));
                            }
                        } catch (JSONException e) {
                            Log.e(TAG, "Error parsing add family member response", e);
                            callback.onError("Failed to parse response");
                        }
                    },
                    error -> {
                        ApiConfig.logRestCall(url, false, error.toString());
                        Log.e(TAG, "Add family member error: " + parseErrorResponse(error), error);
                        callback.onError(parseErrorResponse(error));
                    }) {
                @Override
                public Map<String, String> getHeaders() throws AuthFailureError {
                    Map<String, String> headers = new HashMap<>();
                    String token = tokenManager.getToken();
                    if (token != null) headers.put("Authorization", "Bearer " + token);
                    return headers;
                }
            };
            requestQueue.add(request);
        } catch (JSONException e) {
            Log.e(TAG, "Error creating add family member request", e);
            callback.onError("Failed to create request");
        }
    }

    public void removeFamilyMember(String memberId, PaymentCallback callback) {
        String url = BASE_URL + "/family-member/remove";

        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("memberId", memberId);

            JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, requestBody,
                    response -> {
                        ApiConfig.logRestCall(url, true, "Family member removed");
                        try {
                            boolean success = response.getBoolean("success");
                            if (success) {
                                ProStatusResult result = new ProStatusResult();
                                result.setPro(true);
                                if (response.has("familyProMembers")) {
                                    JSONArray arr = response.getJSONArray("familyProMembers");
                                    java.util.List<String> members = new java.util.ArrayList<>();
                                    for (int i = 0; i < arr.length(); i++) {
                                        members.add(arr.getString(i));
                                    }
                                    result.setFamilyProMembers(members);
                                }
                                callback.onSuccess(result);
                            } else {
                                callback.onError(response.optString("message", "Failed to remove member"));
                            }
                        } catch (JSONException e) {
                            Log.e(TAG, "Error parsing remove family member response", e);
                            callback.onError("Failed to parse response");
                        }
                    },
                    error -> {
                        ApiConfig.logRestCall(url, false, error.toString());
                        Log.e(TAG, "Remove family member error: " + parseErrorResponse(error), error);
                        callback.onError(parseErrorResponse(error));
                    }
            ) {
                @Override
                public Map<String, String> getHeaders() throws AuthFailureError {
                    Map<String, String> headers = new HashMap<>();
                    String token = tokenManager.getToken();
                    if (token != null) headers.put("Authorization", "Bearer " + token);
                    return headers;
                }
            };

            requestQueue.add(request);
        } catch (JSONException e) {
            Log.e(TAG, "Error creating remove family member request", e);
            callback.onError("Failed to create request");
        }
    }

    // ── Coupon validation ────────────────────────────────────────────────────

    public static class CouponResult {
        public boolean valid;
        public String message;
        public String code;
        public double originalAmount;
        public double discount;
        public double finalAmount;
    }

    public interface CouponCallback {
        void onResult(CouponResult result);
        void onError(String errorMessage);
    }

    /**
     * Preview a coupon for a plan. Backend returns valid=false with a message if invalid.
     */
    public void validateCoupon(String code, int planId, CouponCallback callback) {
        String url = BASE_URL + "/coupon/validate";
        try {
            JSONObject body = new JSONObject();
            body.put("code", code);
            body.put("planId", planId);
            JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, body,
                    response -> {
                        ApiConfig.logRestCall(url, true, "Coupon validated");
                        CouponResult r = new CouponResult();
                        r.valid = response.optBoolean("valid", false);
                        r.message = response.optString("message", "");
                        r.code = response.optString("code", "");
                        r.originalAmount = response.optDouble("originalAmount", 0);
                        r.discount = response.optDouble("discount", 0);
                        r.finalAmount = response.optDouble("finalAmount", 0);
                        callback.onResult(r);
                    },
                    error -> {
                        ApiConfig.logRestCall(url, false, error.toString());
                        callback.onError(parseErrorResponse(error));
                    }) {
                @Override public Map<String, String> getHeaders() throws AuthFailureError {
                    Map<String, String> headers = new HashMap<>();
                    String token = tokenManager.getToken();
                    if (token != null) headers.put("Authorization", "Bearer " + token);
                    return headers;
                }
            };
            requestQueue.add(request);
        } catch (JSONException e) {
            callback.onError("Failed to create coupon request");
        }
    }

    // ── Plan catalog ─────────────────────────────────────────────────────────

    /**
     * Callback for plan catalog fetch.
     */
    public interface PlansCallback {
        void onSuccess(List<PlanOption> plans, String currentTier);
        void onError(String errorMessage);
    }

    /**
     * Fetch purchasable plan catalog from GET /api/payment/plans.
     * Falls back to PlanOption.getFallbackPlans() on error — caller receives onError
     * but can still use the fallback list returned in a separate onFallback path if needed.
     */
    public void getPlans(PlansCallback callback) {
        String url = BASE_URL + "/plans";

        StringRequest request = new StringRequest(Request.Method.GET, url,
                response -> {
                    ApiConfig.logRestCall(url, true, "Plans fetched");
                    try {
                        JSONObject json = new JSONObject(response);
                        JSONArray plansArray = json.getJSONArray("plans");
                        String currentTier = json.optString("currentTier", "free");

                        List<PlanOption> plans = new ArrayList<>();
                        for (int i = 0; i < plansArray.length(); i++) {
                            plans.add(PlanOption.fromJson(plansArray.getJSONObject(i)));
                        }

                        callback.onSuccess(plans, currentTier);
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing plans response", e);
                        callback.onError("Failed to parse plans");
                    }
                },
                error -> {
                    ApiConfig.logRestCall(url, false, error.toString());
                    String msg = parseErrorResponse(error);
                    Log.e(TAG, "Plans fetch error: " + msg, error);
                    callback.onError(msg);
                }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                String token = tokenManager.getToken();
                if (token != null) headers.put("Authorization", "Bearer " + token);
                return headers;
            }
        };

        requestQueue.add(request);
    }

    private String parseErrorResponse(VolleyError error) {
        String errorMessage = "Network error";

        if (error.networkResponse != null) {
            int statusCode = error.networkResponse.statusCode;
            errorMessage = "Server error: " + statusCode;

            if (error.networkResponse.data != null) {
                try {
                    String responseBody = new String(error.networkResponse.data, StandardCharsets.UTF_8);
                    JSONObject errorJson = new JSONObject(responseBody);
                    if (errorJson.has("message")) {
                        errorMessage = errorJson.getString("message");
                    }
                } catch (JSONException e) {
                    // If not a JSON response, use the raw response
                    errorMessage = new String(error.networkResponse.data, StandardCharsets.UTF_8);
                    if (errorMessage.length() > 100) {
                        errorMessage = errorMessage.substring(0, 100) + "...";
                    }
                }
            }
        } else if (error.getMessage() != null) {
            errorMessage = error.getMessage();
        }

        return errorMessage;
    }
}
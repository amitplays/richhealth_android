package Utils;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import Models.PlanOption;

import com.razorpay.Checkout;
import com.razorpay.PaymentResultListener;

import org.json.JSONObject;

/**
 * Manages the payment flow for Pro upgrades using RazorPay
 */
public class PaymentManager {
    private static final String TAG = "PaymentManager";

    private final Context context;
    private final PaymentService paymentService;
    private final ProStatusManager proStatusManager;

    private SimpleProgress progressDialog;
    private String currentTransactionId;
    private String currentRazorpayOrderId;
    private PaymentCallback callback;
    private Activity currentActivity;
    private int selectedPlanType = 2;

    public interface PaymentCallback {
        void onPaymentInitiated();
        void onPaymentSuccess(String plan);
        void onPaymentFailed(String reason);
        void onPaymentCancelled();
    }

    public interface FamilyMemberSelectionListener {
        void onSelectFamilyMembers(String razorpayPaymentId, String razorpayOrderId,
                                   String razorpaySignature, FamilyMemberSelectionCallback selectionCallback);
    }

    public interface FamilyMemberSelectionCallback {
        void onMembersSelected(java.util.List<String> memberIds);
        void onSkipped();
    }

    private FamilyMemberSelectionListener familyMemberSelectionListener;
    // pendingAddMemberId removed — family members are now added via addFamilyMemberDirect() (no payment)

    public PaymentManager(Context context) {
        this.context = context;
        this.paymentService = new PaymentService(context);
        this.proStatusManager = ProStatusManager.getInstance(context);
        Checkout.preload(context);
    }

    public void setFamilyMemberSelectionListener(FamilyMemberSelectionListener listener) {
        this.familyMemberSelectionListener = listener;
    }

    public void setSelectedPlanType(int planType) {
        this.selectedPlanType = planType;
    }

    public int getSelectedPlanType() {
        return selectedPlanType;
    }

    /**
     * Add a family member to the current Ultra/Family plan at no extra charge.
     * Family members are included in the Ultra plan price — no separate payment required.
     * Uses the direct backend endpoint POST /api/payment/family-member/add.
     */
    public void addFamilyMemberDirect(Activity activity, String memberId, PaymentCallback callback) {
        Log.d(TAG, "=== ADD FAMILY MEMBER (DIRECT) ===");
        Log.d(TAG, "MemberId: " + memberId);

        this.callback = callback;
        this.currentActivity = activity;

        progressDialog = SimpleProgress.show(activity, "Adding family member...");

        paymentService.addFamilyMemberDirect(memberId, new PaymentService.PaymentCallback() {
            @Override
            public void onSuccess(ProStatusResult result) {
                progressDialog.hide();

                if (result.getFamilyProMembers() != null) {
                    proStatusManager.setFamilyPlanInfo(
                            true, false, null,
                            result.getFamilyProMembers().size(),
                            proStatusManager.getMaxFamilyMembers()
                    );
                }

                if (callback != null) callback.onPaymentSuccess("family_member_add");
            }

            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "Add family member failed: " + errorMessage);
                progressDialog.hide();
                if (callback != null) callback.onPaymentFailed(errorMessage);
            }
        });
    }

    /**
     * Preferred entry point — starts payment for a PlanOption fetched from the backend.
     * Amount and planId come directly from the catalog, preventing client-side manipulation.
     */
    public void startPaymentFlow(Activity activity, PlanOption plan, PaymentCallback callback) {
        startPaymentFlow(activity, plan, null, callback);
    }

    public void startPaymentFlow(Activity activity, PlanOption plan, String couponCode, PaymentCallback callback) {
        Log.d(TAG, "=== PAYMENT FLOW STARTED (PlanOption) ===");
        Log.d(TAG, "Plan: " + plan.getName() + " planId=" + plan.getPlanId() + " amount=" + plan.getPrice()
                + " coupon=" + (couponCode == null ? "" : couponCode));

        this.callback = callback;
        this.currentActivity = activity;
        this.selectedPlanType = plan.getPlanId();

        progressDialog = SimpleProgress.show(activity, "Initializing payment…");

        paymentService.initiatePayment(plan, couponCode, new PaymentService.PaymentCallback() {
            @Override
            public void onSuccess(ProStatusResult result) {
                currentTransactionId = result.getTransactionId();
                currentRazorpayOrderId = result.getRazorpayOrderId();
                progressDialog.hide();

                startRazorpayCheckout(activity, result.getRazorpayKeyId(),
                        result.getRazorpayOrderId(), result.getAmount(), plan.getTierKey());

                if (callback != null) callback.onPaymentInitiated();
            }

            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "Payment initiation failed: " + errorMessage);
                progressDialog.hide();
                if (callback != null) callback.onPaymentFailed(errorMessage);
            }
        });
    }

    /**
     * Start RazorPay Checkout
     * @param activity The activity that initiated the payment
     * @param keyId RazorPay Key ID
     * @param orderId RazorPay Order ID
     * @param amount Amount in INR
     * @param plan The subscription plan
     */
    private void startRazorpayCheckout(Activity activity, String keyId, String orderId, 
                                        double amount, String plan) {
        Log.d(TAG, "=== STARTING RAZORPAY CHECKOUT ===");
        Log.d(TAG, "Order ID: " + orderId);
        Log.d(TAG, "Amount: " + amount);

        try {
            Checkout checkout = new Checkout();
            checkout.setKeyID(keyId);
            
            // Set RazorPay logo (optional)
            checkout.setImage(com.example.richhealth.R.drawable.ic_launcher);

            JSONObject options = new JSONObject();
            options.put("name", "RichHealth");
            options.put("description", "Pro Subscription - " + plan);
            options.put("order_id", orderId);
            options.put("currency", "INR");
            // Amount should be in paise (multiply by 100)
            options.put("amount", (int)(amount * 100));
            
            // Prefill customer info if available
            JSONObject prefill = new JSONObject();
            prefill.put("email", "");
            prefill.put("contact", "");
            options.put("prefill", prefill);
            
            // Theme options
            JSONObject theme = new JSONObject();
            theme.put("color", "#008b8b"); // RichHealth teal
            options.put("theme", theme);

            Log.d(TAG, "Opening RazorPay checkout with options: " + options.toString());
            checkout.open(activity, options);

        } catch (Exception e) {
            Log.e(TAG, "Error starting RazorPay checkout: " + e.getMessage(), e);
            if (callback != null) {
                callback.onPaymentFailed("Failed to open payment gateway: " + e.getMessage());
            }
        }
    }

    /**
     * Handle successful payment from RazorPay
     * Called by PaymentResultListener.onPaymentSuccess
     * @param razorpayPaymentId The RazorPay payment ID
     */
    public void onRazorpayPaymentSuccess(String razorpayPaymentId, String razorpayOrderId, 
                                          String razorpaySignature) {
        Log.d(TAG, "=== RAZORPAY PAYMENT SUCCESS ===");
        Log.d(TAG, "Payment ID: " + razorpayPaymentId);
        Log.d(TAG, "Order ID: " + razorpayOrderId);
        Log.d(TAG, "Signature: " + razorpaySignature);

        if (selectedPlanType == 3 && familyMemberSelectionListener != null) {
            familyMemberSelectionListener.onSelectFamilyMembers(
                    razorpayPaymentId, razorpayOrderId, razorpaySignature,
                    new FamilyMemberSelectionCallback() {
                        @Override
                        public void onMembersSelected(java.util.List<String> memberIds) {
                            if (currentActivity != null) {
                                progressDialog = SimpleProgress.show(currentActivity, "Verifying payment...");
                            }
                            verifyPaymentWithMembers(razorpayPaymentId, razorpayOrderId,
                                    razorpaySignature, memberIds);
                        }

                        @Override
                        public void onSkipped() {
                            if (currentActivity != null) {
                                progressDialog = SimpleProgress.show(currentActivity, "Verifying payment...");
                            }
                            verifyPayment(razorpayPaymentId, razorpayOrderId, razorpaySignature);
                        }
                    });
        } else {
            if (currentActivity != null) {
                progressDialog = SimpleProgress.show(currentActivity, "Verifying payment...");
            }
            verifyPayment(razorpayPaymentId, razorpayOrderId, razorpaySignature);
        }
    }

    /**
     * Handle failed payment from RazorPay
     * Called by PaymentResultListener.onPaymentError
     * @param code Error code
     * @param response Error response
     */
    public void onRazorpayPaymentError(int code, String response) {
        Log.e(TAG, "=== RAZORPAY PAYMENT ERROR ===");
        Log.e(TAG, "Code: " + code + ", Response: " + response);

        if (callback != null) {
            if (code == Checkout.PAYMENT_CANCELED) {
                callback.onPaymentCancelled();
            } else {
                callback.onPaymentFailed("Payment failed: " + response);
            }
        }
    }

    /**
     * Verify the payment status with the server
     * @param razorpayPaymentId RazorPay payment ID
     * @param razorpayOrderId RazorPay order ID
     * @param razorpaySignature RazorPay signature
     */
    private void verifyPayment(String razorpayPaymentId, String razorpayOrderId, 
                               String razorpaySignature) {
        verifyPaymentWithMembers(razorpayPaymentId, razorpayOrderId, razorpaySignature, null);
    }

    private void verifyPaymentWithMembers(String razorpayPaymentId, String razorpayOrderId,
                                           String razorpaySignature, java.util.List<String> familyMemberIds) {
        if (currentTransactionId == null) {
            if (progressDialog != null) progressDialog.hide();
            if (callback != null) {
                callback.onPaymentFailed("Transaction ID is missing");
            }
            return;
        }

        paymentService.verifyPayment(currentTransactionId, razorpayPaymentId,
                razorpayOrderId, razorpaySignature, familyMemberIds, new PaymentService.PaymentCallback() {
            @Override
            public void onSuccess(ProStatusResult result) {
                if (progressDialog != null) progressDialog.hide();

                proStatusManager.setProStatusComplete(
                        true,
                        result.getExpiryDate(),
                        result.getPlan(),
                        result.getTransactionId()
                );
                proStatusManager.setFamilyPlanInfo(
                        "family".equals(result.getPlan()),
                        false,
                        null,
                        result.getFamilyProMemberCount(),
                        result.getMaxFamilyMembers()
                );

                if (callback != null) {
                    callback.onPaymentSuccess(result.getPlan());
                }
            }

            @Override
            public void onError(String errorMessage) {
                if (progressDialog != null) progressDialog.hide();
                if (callback != null) {
                    callback.onPaymentFailed(errorMessage);
                }
            }
        });
    }

    public String getCurrentRazorpayOrderId() {
        return currentRazorpayOrderId;
    }

    /**
     * Get the current transaction ID
     * @return The current transaction ID
     */
    public String getCurrentTransactionId() {
        return currentTransactionId;
    }
}

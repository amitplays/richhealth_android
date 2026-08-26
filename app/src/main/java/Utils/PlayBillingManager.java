package Utils;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.AcknowledgePurchaseResponseListener;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.ProductDetailsResponseListener;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;

import java.util.Collections;
import java.util.List;

import Models.PlanOption;

/**
 * Google Play Billing (subscriptions) — the in-app payment rail. Play natively offers UPI /
 * UPI Autopay / cards, so this covers India and the rest of the world through one sheet.
 *
 * Reuses {@link PaymentManager.PaymentCallback} so ProUpgradeDialog needs no new interface.
 * The backend (POST /api/payment/google/verify) re-checks the token with Google and is the
 * source of truth for Pro state; this class only drives the client flow + acknowledgement.
 */
public class PlayBillingManager implements PurchasesUpdatedListener {
    private static final String TAG = "PlayBillingManager";

    private final Context context;
    private final PaymentService paymentService;
    private final ProStatusManager proStatusManager;

    private BillingClient billingClient;
    private Activity activity;
    private PlanOption plan;
    private String productId;
    private PaymentManager.PaymentCallback callback;

    public PlayBillingManager(Context context) {
        this.context = context;
        this.paymentService = new PaymentService(context);
        this.proStatusManager = ProStatusManager.getInstance(context);
    }

    /** Product id convention shared with backend + Play Console: "richhealth.&lt;tierKey&gt;". */
    private static String productIdFor(PlanOption plan) {
        return "richhealth." + plan.getTierKey();
    }

    /** Entry point mirroring PaymentManager.startPaymentFlow. */
    public void startPurchase(Activity activity, PlanOption plan, PaymentManager.PaymentCallback callback) {
        this.activity = activity;
        this.plan = plan;
        this.productId = productIdFor(plan);
        this.callback = callback;

        billingClient = BillingClient.newBuilder(context)
                .setListener(this)
                .enablePendingPurchases(
                        PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .build();

        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(@NonNull BillingResult result) {
                if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    queryAndLaunch();
                } else {
                    fail("Google Play billing unavailable: " + result.getDebugMessage());
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                // Transient; the user can retry the purchase.
            }
        });
    }

    private void queryAndLaunch() {
        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                .setProductList(Collections.singletonList(
                        QueryProductDetailsParams.Product.newBuilder()
                                .setProductId(productId)
                                .setProductType(BillingClient.ProductType.SUBS)
                                .build()))
                .build();

        billingClient.queryProductDetailsAsync(params, new ProductDetailsResponseListener() {
            @Override
            public void onProductDetailsResponse(@NonNull BillingResult result,
                                                 @NonNull List<ProductDetails> productDetailsList) {
                if (result.getResponseCode() != BillingClient.BillingResponseCode.OK
                        || productDetailsList.isEmpty()) {
                    fail("This plan isn't available on Google Play yet.");
                    return;
                }
                ProductDetails details = productDetailsList.get(0);
                List<ProductDetails.SubscriptionOfferDetails> offers = details.getSubscriptionOfferDetails();
                if (offers == null || offers.isEmpty()) {
                    fail("No subscription offer found for this plan.");
                    return;
                }
                String offerToken = offers.get(0).getOfferToken();
                BillingFlowParams flowParams = BillingFlowParams.newBuilder()
                        .setProductDetailsParamsList(Collections.singletonList(
                                BillingFlowParams.ProductDetailsParams.newBuilder()
                                        .setProductDetails(details)
                                        .setOfferToken(offerToken)
                                        .build()))
                        .build();
                BillingResult launch = billingClient.launchBillingFlow(activity, flowParams);
                if (launch.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                    fail("Couldn't open Google Play: " + launch.getDebugMessage());
                }
            }
        });
    }

    @Override
    public void onPurchasesUpdated(@NonNull BillingResult result, List<Purchase> purchases) {
        int code = result.getResponseCode();
        if (code == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (Purchase p : purchases) {
                handlePurchase(p);
            }
        } else if (code == BillingClient.BillingResponseCode.USER_CANCELED) {
            if (callback != null) callback.onPaymentCancelled();
            endConnection();
        } else {
            fail("Purchase failed: " + result.getDebugMessage());
        }
    }

    private void handlePurchase(final Purchase purchase) {
        if (purchase.getPurchaseState() != Purchase.PurchaseState.PURCHASED) {
            // PENDING (e.g. a UPI mandate awaiting confirmation) — not a failure.
            if (callback != null) callback.onPaymentInitiated();
            return;
        }
        // Server verifies the token with Google, activates Pro, and returns the plan/expiry.
        paymentService.verifyGooglePurchase(purchase.getPurchaseToken(), productId,
                new PaymentService.PaymentCallback() {
                    @Override
                    public void onSuccess(ProStatusResult resultData) {
                        if (!purchase.isAcknowledged()) {
                            AcknowledgePurchaseParams ackParams = AcknowledgePurchaseParams.newBuilder()
                                    .setPurchaseToken(purchase.getPurchaseToken())
                                    .build();
                            billingClient.acknowledgePurchase(ackParams,
                                    new AcknowledgePurchaseResponseListener() {
                                        @Override
                                        public void onAcknowledgePurchaseResponse(@NonNull BillingResult r) {
                                            // Ack failures are non-fatal; server can re-ack via API.
                                        }
                                    });
                        }
                        proStatusManager.setProStatusComplete(true, resultData.getExpiryDate(),
                                resultData.getPlan(), resultData.getTransactionId());
                        if (callback != null) callback.onPaymentSuccess(resultData.getPlan());
                        endConnection();
                    }

                    @Override
                    public void onError(String errorMessage) {
                        fail(errorMessage);
                    }
                });
    }

    private void fail(String reason) {
        Log.e(TAG, reason);
        if (callback != null) callback.onPaymentFailed(reason);
        endConnection();
    }

    private void endConnection() {
        try {
            if (billingClient != null) billingClient.endConnection();
        } catch (Exception ignored) {
        }
    }
}

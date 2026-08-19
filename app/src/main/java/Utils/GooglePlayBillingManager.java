package Utils;

import android.app.Activity;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;

import java.util.Collections;
import java.util.List;

/**
 * Thin wrapper over Google Play Billing for subscription purchases. Minimal on purpose:
 * connect -> query the one product -> launch checkout -> hand back the purchase token.
 * The backend ({@code POST /api/payment/google/verify}) validates the token against the
 * Play Developer API AND acknowledges it, so we do not acknowledge client-side.
 */
public class GooglePlayBillingManager {

    private static final String TAG = "GPBilling";

    public interface PurchaseCallback {
        void onPurchased(String purchaseToken, String productId);
        void onError(String message);
        void onCancelled();
    }

    private final Activity activity;
    private BillingClient billingClient;
    private PurchaseCallback callback;
    private String pendingProductId;

    public GooglePlayBillingManager(Activity activity) { this.activity = activity; }

    public void purchaseSubscription(String productId, PurchaseCallback cb) {
        this.callback = cb;
        this.pendingProductId = productId;
        billingClient = BillingClient.newBuilder(activity)
                .enablePendingPurchases()
                .setListener(purchasesUpdatedListener)
                .build();
        billingClient.startConnection(new BillingClientStateListener() {
            @Override public void onBillingSetupFinished(@NonNull BillingResult r) {
                if (r.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    queryAndLaunch(productId);
                } else {
                    fail("Google Play billing unavailable (" + r.getResponseCode() + ")");
                }
            }
            @Override public void onBillingServiceDisconnected() { /* user can retry */ }
        });
    }

    private void queryAndLaunch(String productId) {
        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                .setProductList(Collections.singletonList(
                        QueryProductDetailsParams.Product.newBuilder()
                                .setProductId(productId)
                                .setProductType(BillingClient.ProductType.SUBS)
                                .build()))
                .build();
        billingClient.queryProductDetailsAsync(params, (result, productDetailsList) -> {
            if (result.getResponseCode() != BillingClient.BillingResponseCode.OK
                    || productDetailsList == null || productDetailsList.isEmpty()) {
                fail("This plan isn't available on Google Play yet (" + productId + ")");
                return;
            }
            ProductDetails pd = productDetailsList.get(0);
            List<ProductDetails.SubscriptionOfferDetails> offers = pd.getSubscriptionOfferDetails();
            if (offers == null || offers.isEmpty()) {
                fail("No subscription offer configured for " + productId);
                return;
            }
            String offerToken = offers.get(0).getOfferToken();
            BillingFlowParams flowParams = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(Collections.singletonList(
                            BillingFlowParams.ProductDetailsParams.newBuilder()
                                    .setProductDetails(pd)
                                    .setOfferToken(offerToken)
                                    .build()))
                    .build();
            BillingResult launch = billingClient.launchBillingFlow(activity, flowParams);
            if (launch.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                fail("Couldn't open the Google Play checkout.");
            }
        });
    }

    private final PurchasesUpdatedListener purchasesUpdatedListener = (result, purchases) -> {
        int code = result.getResponseCode();
        if (code == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (Purchase p : purchases) {
                if (p.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                    if (callback != null) callback.onPurchased(p.getPurchaseToken(), pendingProductId);
                }
            }
        } else if (code == BillingClient.BillingResponseCode.USER_CANCELED) {
            if (callback != null) callback.onCancelled();
        } else {
            fail("Purchase failed (" + code + ")");
        }
    };

    private void fail(String msg) {
        Log.e(TAG, msg);
        if (callback != null) callback.onError(msg);
    }
}

package com.example.richhealth.Activities;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import com.example.richhealth.R;
import com.google.android.material.checkbox.MaterialCheckBox;

public class TermsAndConditionsDialog {
    private static final String PREFS_NAME = "AppPreferences";
    private static final String KEY_TERMS_DIALOG_SHOWN = "terms_dialog_shown";
    private static final String KEY_TERMS_ACCEPTED = "terms_accepted";

    private Context context;
    private OnTermsActionListener listener;
    private Dialog dialog;

    public interface OnTermsActionListener {
        void onTermsAccepted();
        void onTermsDeclined();
    }

    public TermsAndConditionsDialog(Context context, OnTermsActionListener listener) {
        this.context = context;
        this.listener = listener;
    }

    /**
     * Simple check - terms are accepted ONLY if explicitly accepted by current user
     */
    public static boolean areTermsAccepted(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean termsAccepted = prefs.getBoolean(KEY_TERMS_ACCEPTED, false);

        TokenManager tokenManager = TokenManager.getInstance(context);
        String currentUserId = tokenManager.getUserId();
        String acceptedByUserId = prefs.getString("terms_accepted_by_user", "");

        android.util.Log.d("TermsDialog", "=== Terms Check ===");
        android.util.Log.d("TermsDialog", "termsAccepted: " + termsAccepted);
        android.util.Log.d("TermsDialog", "currentUserId: " + currentUserId);
        android.util.Log.d("TermsDialog", "acceptedByUserId: " + acceptedByUserId);

        boolean result = termsAccepted &&
                currentUserId != null &&
                currentUserId.equals(acceptedByUserId);

        android.util.Log.d("TermsDialog", "Final result: " + result);
        return result;
    }

    /**
     * Show the terms and conditions dialog
     */
    public void show() {
        dialog = new Dialog(context, R.style.DialogTheme);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_terms_and_conditions);
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);

        // Back button = decline (only on ACTION_UP to prevent double-fire)
        dialog.setOnKeyListener((dialogInterface, keyCode, keyEvent) -> {
            if (keyCode == android.view.KeyEvent.KEYCODE_BACK
                    && keyEvent.getAction() == android.view.KeyEvent.ACTION_UP) {
                dialog.dismiss();
                if (listener != null) {
                    listener.onTermsDeclined();
                }
                return true;
            }
            return keyCode == android.view.KeyEvent.KEYCODE_BACK; // consume ACTION_DOWN too
        });

        // Configure dialog window — take up most of the screen so content is scrollable
        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
        layoutParams.copyFrom(dialog.getWindow().getAttributes());
        layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
        // Use 80% of screen height so the ScrollView (weight=1) has room to expand
        android.util.DisplayMetrics dm = context.getResources().getDisplayMetrics();
        layoutParams.height = (int) (dm.heightPixels * 0.80);
        dialog.getWindow().setAttributes(layoutParams);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        // Initialize UI elements
        TextView titleText = dialog.findViewById(R.id.terms_title);
        TextView termsContent = dialog.findViewById(R.id.terms_content);
        Button acceptButton = dialog.findViewById(R.id.accept_button);
        Button declineButton = dialog.findViewById(R.id.decline_button);
        MaterialCheckBox termsCheckbox = dialog.findViewById(R.id.terms_checkbox);

        // Set up terms content with HTML formatting
        String termsHtml = getTermsAndConditionsHtml();
        termsContent.setText(Html.fromHtml(termsHtml, Html.FROM_HTML_MODE_LEGACY));
        termsContent.setMovementMethod(LinkMovementMethod.getInstance());

        // Accept button starts disabled; enabled only when checkbox is checked
        acceptButton.setEnabled(false);
        acceptButton.setAlpha(0.4f);

        termsCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            acceptButton.setEnabled(isChecked);
            acceptButton.setAlpha(isChecked ? 1.0f : 0.4f);
        });

        // Accept button
        acceptButton.setOnClickListener(v -> {
            String currentUserId = TokenManager.getInstance(context).getUserId();
            markAcceptedLocally(context, currentUserId);   // fast local cache
            persistTermsToServer(context);                 // account-level, syncs across devices

            dialog.dismiss();
            if (listener != null) {
                listener.onTermsAccepted();
            }
        });

        // Decline button
        declineButton.setOnClickListener(v -> {
            dialog.dismiss();
            if (listener != null) {
                listener.onTermsDeclined();
            }
        });

        dialog.show();
    }

    /**
     * Get the terms and conditions content as HTML
     */
    private String getTermsAndConditionsHtml() {
        return "<p>" +
                "Welcome to <b>RichHealth</b>. By using our application, you agree to be bound by these Terms &amp; Conditions. Please read them carefully." +
                "</p><br>" +

                "<p style='color: #008b8b;'><b>1. Acceptance of Terms</b></p>" +
                "<p>" +
                "By creating an account or using RichHealth, you confirm that you are at least 18 years old and agree to comply with these terms. If you do not agree, you must not use the app." +
                "</p><br>" +

                "<p style='color: #008b8b;'><b>2. Data Collection &amp; Privacy</b></p>" +
                "<p>" +
                "&#8226; We collect health metrics, activity data, and personal information to deliver personalised insights and recommendations.<br><br>" +
                "&#8226; All data is encrypted in transit (TLS 1.3) and at rest (AES-256).<br><br>" +
                "&#8226; We <b>never</b> sell, rent, or share your personal health information with third-party advertisers.<br><br>" +
                "&#8226; You may request a full export or permanent deletion of your data at any time by contacting support@richhealth.com.<br><br>" +
                "&#8226; Anonymous, aggregated data may be used to improve our algorithms and health recommendations." +
                "</p><br>" +

                "<p style='color: #008b8b;'><b>3. Health &amp; Medical Disclaimers</b></p>" +
                "<p>" +
                "&#8226; RichHealth provides <b>general wellness information only</b> and is not a substitute for professional medical advice, diagnosis, or treatment.<br><br>" +
                "&#8226; Always consult a qualified healthcare professional before making decisions based on information from this app.<br><br>" +
                "&#8226; AI-generated insights are based on the data you provide and general health guidelines; they may not account for your complete medical history.<br><br>" +
                "&#8226; In case of a medical emergency, call your local emergency number immediately. Do not rely on this app for emergency guidance." +
                "</p><br>" +

                "<p style='color: #008b8b;'><b>4. Your Responsibilities</b></p>" +
                "<p>" +
                "&#8226; You agree to provide accurate and truthful health information.<br><br>" +
                "&#8226; You are responsible for maintaining the confidentiality of your account credentials.<br><br>" +
                "&#8226; You must not attempt to reverse-engineer, hack, or exploit any part of the application.<br><br>" +
                "&#8226; You must not use the app for any unlawful or harmful purpose." +
                "</p><br>" +

                "<p style='color: #008b8b;'><b>5. Subscriptions &amp; Payments</b></p>" +
                "<p>" +
                "&#8226; Certain premium features require an active subscription (RichHealth Pro).<br><br>" +
                "&#8226; Subscriptions renew automatically unless cancelled at least 24 hours before the renewal date.<br><br>" +
                "&#8226; Subscriptions are billed through the app store (Google Play or Apple); manage or cancel anytime in your store account. Cancellation stops future renewals but does not refund the current period.<br><br>" +
                "&#8226; All purchases are final and non-refundable except where required by law. Refunds for store purchases are handled solely by Google Play or Apple under their terms; RichHealth cannot issue them.<br><br>" +
                "&#8226; We reserve the right to adjust pricing with 30 days prior notice." +
                "</p><br>" +

                "<p style='color: #008b8b;'><b>6. Biometric Authentication</b></p>" +
                "<p>" +
                "&#8226; RichHealth supports optional biometric authentication (fingerprint / face unlock) for app security.<br><br>" +
                "&#8226; Biometric data is processed locally on your device and is <b>never</b> transmitted to our servers.<br><br>" +
                "&#8226; You can enable or disable biometric lock at any time from Settings." +
                "</p><br>" +

                "<p style='color: #008b8b;'><b>7. Limitation of Liability</b></p>" +
                "<p>" +
                "&#8226; RichHealth and its creators shall not be liable for any direct, indirect, or consequential damages arising from the use of this app.<br><br>" +
                "&#8226; We do not guarantee the completeness, accuracy, or reliability of all health information provided.<br><br>" +
                "&#8226; Use of the app is entirely at your own discretion and risk." +
                "</p><br>" +

                "<p style='color: #008b8b;'><b>8. Termination</b></p>" +
                "<p>" +
                "&#8226; We reserve the right to suspend or terminate accounts that violate these terms.<br><br>" +
                "&#8226; You may delete your account at any time. Upon deletion, your data will be permanently removed within 30 days." +
                "</p><br>" +

                "<p style='color: #808080; font-size: 12px;'>" +
                "For questions or concerns, contact us at support@richhealth.com" +
                "</p>";
    }

    /** Cache acceptance locally for the current user (fast path / offline). */
    public static void markAcceptedLocally(Context context, String userId) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_TERMS_ACCEPTED, true)
                .putString("terms_accepted_by_user", userId != null ? userId : "")
                .apply();
    }

    /** Persist acceptance to the account so a reinstall / new device won't re-prompt. */
    private static void persistTermsToServer(Context context) {
        try {
            final String token = TokenManager.getInstance(context).getToken();
            if (token == null) return;
            String url = Utils.ApiConfig.BASE_URL + "/api/user/profile";
            org.json.JSONObject body = new org.json.JSONObject();
            body.put("termsAccepted", true);
            java.text.SimpleDateFormat sdf =
                    new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US);
            sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            body.put("termsAcceptedAt", sdf.format(new java.util.Date()));
            final byte[] payload = body.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            com.android.volley.toolbox.StringRequest req = new com.android.volley.toolbox.StringRequest(
                    com.android.volley.Request.Method.PUT, url,
                    r -> {}, e -> {}) {
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
     * Clear terms acceptance when user logs out
     */
    public static void clearTermsAcceptance(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putBoolean(KEY_TERMS_ACCEPTED, false)
                .putString("terms_accepted_by_user", "")
                .apply();
        android.util.Log.d("TermsDialog", "Terms acceptance cleared");
    }

    /**
     * Reset terms acceptance (for testing purposes)
     */
    public static void resetTermsAcceptance(Context context) {
        clearTermsAcceptance(context);
    }
}

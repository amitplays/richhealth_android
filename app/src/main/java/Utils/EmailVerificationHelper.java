package Utils;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.InputFilter;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NoConnectionError;
import com.android.volley.Request;
import com.android.volley.TimeoutError;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.example.richhealth.R;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

/**
 * Shared email-verification (OTP) helper — the ONE place the app talks to
 * POST /api/auth/send-otp and POST /api/auth/verify-otp.
 *
 * Signup verifies inline inside OnboardingActivity (which owns its own non-dismissible
 * dialog but calls the network helpers here). After login, LoginActivity uses
 * {@link #show} to offer verification to an account whose profile came back with
 * emailVerified=false. Verifying flips User.emailVerified server-side, which is what
 * stops the prompt returning.
 */
public class EmailVerificationHelper {

    public interface Ok { void run(); }
    public interface Err { void run(String message); }
    /** Verify success, carrying the server's body — verify-otp now returns a session. */
    public interface OkData { void run(JSONObject response); }

    private EmailVerificationHelper() {}

    /** Addresses always leave the app trimmed AND lowercased. They used to be trimmed
     *  only, so an account created as "Alice@x.com" had its code filed under a different
     *  key than a later "alice@x.com" login, and the code never matched. */
    public static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    /** POST /api/auth/send-otp — emails a fresh 6-digit code. No auth token needed. */
    public static void sendOtp(Context context, String email, Ok onOk, Err onErr) {
        JSONObject body = new JSONObject();
        try {
            body.put("email", normalize(email));
        } catch (JSONException e) {
            if (onErr != null) onErr.run("Something went wrong. Please try again.");
            return;
        }
        post(context, "/api/auth/send-otp", body, "otp sent",
                "Couldn't send the code. Please try again.",
                json -> { if (onOk != null) onOk.run(); }, onErr);
    }

    /** POST /api/auth/verify-otp — confirms the code and marks the email verified. */
    public static void verifyOtp(Context context, String email, String code, Ok onOk, Err onErr) {
        verifyOtp(context, email, code, json -> { if (onOk != null) onOk.run(); }, onErr);
    }

    /**
     * Same call, handing back the parsed body. A successful verify now returns
     * {verified, token, userId, user} because signup deliberately issues no session —
     * this is the moment the account becomes usable, so the caller needs the token.
     */
    public static void verifyOtp(Context context, String email, String code, OkData onOk, Err onErr) {
        JSONObject body = new JSONObject();
        try {
            body.put("email", normalize(email));
            body.put("otp", code);
        } catch (JSONException e) {
            if (onErr != null) onErr.run("Something went wrong. Please try again.");
            return;
        }
        post(context, "/api/auth/verify-otp", body, "verified",
                "Incorrect or expired code.", onOk, onErr);
    }

    private static void post(Context context, String path, JSONObject body, String okLog,
                             String fallbackError, OkData onOk, Err onErr) {
        StringRequest request = new StringRequest(
                Request.Method.POST,
                ApiConfig.BASE_URL + path,
                response -> {
                    ApiConfig.logRestCall(path, true, okLog);
                    JSONObject parsed;
                    try { parsed = new JSONObject(response); } catch (JSONException e) { parsed = new JSONObject(); }
                    if (onOk != null) onOk.run(parsed);
                },
                error -> {
                    ApiConfig.logRestCall(path, false, error.toString());
                    if (onErr != null) onErr.run(parseError(error, fallbackError));
                }
        ) {
            @Override public byte[] getBody() { return body.toString().getBytes(StandardCharsets.UTF_8); }
            @Override public String getBodyContentType() { return "application/json; charset=utf-8"; }
        };
        request.setRetryPolicy(new DefaultRetryPolicy(30000, 1, 1f));
        Volley.newRequestQueue(context.getApplicationContext()).add(request);
    }

    /** Best-effort extraction of the server's message from a Volley error. */
    public static String parseError(VolleyError error, String fallback) {
        if (error != null && error.networkResponse != null) {
            try {
                String bodyStr = new String(error.networkResponse.data, StandardCharsets.UTF_8);
                JSONObject json = new JSONObject(bodyStr);
                if (json.has("message")) return json.getString("message");
                if (json.has("errors")) {
                    JSONObject errs = json.getJSONObject("errors");
                    if (errs.names() != null && errs.names().length() > 0) {
                        return errs.getString(errs.names().getString(0));
                    }
                }
            } catch (Exception ignored) {}
        } else if (error instanceof NoConnectionError) {
            return "No internet connection. Please check your network.";
        } else if (error instanceof TimeoutError) {
            return "Connection timed out. Please try again.";
        }
        return fallback;
    }

    /**
     * Email verification gate, shown when login is refused with requiresEmailVerification.
     *
     * This used to be a post-login "prompt": the user was already signed in, back or a
     * tap outside ran {@code onDone}, and {@code onDone} walked straight into the app. It
     * verified nothing. The server now refuses to issue a session at all until the address
     * is confirmed, so there is no token behind this dialog — {@code onVerified} runs ONLY
     * on success and carries the session the server hands back. Dismissing returns the user
     * to the login form, which is where they already are.
     */
    public static android.app.Dialog show(Activity activity, String email, OkData onVerified) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return null;
        final String target = normalize(email);

        LayoutInflater inflater = LayoutInflater.from(activity);
        View dialogView = inflater.inflate(R.layout.dialog_edit_profile, null);
        ((TextView) dialogView.findViewById(R.id.dialog_title)).setText("Verify your email");

        LinearLayout fieldsContainer = dialogView.findViewById(R.id.fields_container);
        fieldsContainer.removeAllViews();

        int gap = (int) (12 * activity.getResources().getDisplayMetrics().density);
        TextView info = new TextView(activity);
        info.setText("We emailed " + target + ". Tap \u201cVerify my email\u201d in that message and this screen continues by itself \u2014 "
                + "on this phone or any other device.\n\nThe link is good for 24 hours. If it has expired, send a new one.");
        info.setTextColor(0xFFB0B0B0);
        info.setTextSize(14);
        info.setPadding(0, 0, 0, gap);
        fieldsContainer.addView(info);

        // The code field was removed: the email now carries a link and nothing else, so
        // there is nothing to type. watchForLinkVerification() below already finishes this
        // by itself when the link is opened, on this phone or any other device. The
        // sendOtp/verifyOtp helpers are kept intact — the endpoints still exist and the
        // password-reset flow still uses codes.

        final Dialog dialog = new Dialog(activity, R.style.DialogTheme);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(dialogView);
        // Dismissible on purpose: there is no session behind this dialog, so closing it
        // lands back on the login form rather than inside the app. Someone who mistyped
        // their address needs that way out.
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(false);
        if (dialog.getWindow() != null) {
            WindowManager.LayoutParams wlp = new WindowManager.LayoutParams();
            wlp.copyFrom(dialog.getWindow().getAttributes());
            wlp.width = WindowManager.LayoutParams.WRAP_CONTENT;
            wlp.height = WindowManager.LayoutParams.WRAP_CONTENT;
            dialog.getWindow().setAttributes(wlp);
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        Button verifyButton = dialogView.findViewById(R.id.save_button);
        Button resendButton = dialogView.findViewById(R.id.cancel_button);
        // One action. The primary button resends the email; there is nothing to confirm
        // here because confirming happens in the mail app.
        verifyButton.setText("Resend email");
        resendButton.setVisibility(View.GONE);

        verifyButton.setOnClickListener(v -> {
            verifyButton.setEnabled(false);
            sendOtp(activity, target,
                    () -> {
                        verifyButton.setEnabled(true);
                        Utilities.toast(activity, "New email sent to " + target);
                    },
                    msg -> {
                        verifyButton.setEnabled(true);
                        Utilities.toastLong(activity, msg);
                    });
        });

        dialog.show();
        // Send the first email as the dialog opens, so the user is never waiting on a tap.
        sendOtp(activity, target,
                () -> Utilities.toast(activity, "Verification email sent to " + target),
                msg -> Utilities.toastLong(activity, msg));
        return dialog;
    }

    /**
     * Watches for the emailed LINK being tapped, so the user can verify on a laptop and
     * have the phone let them straight in.
     *
     * The check is just a silent re-login: the server answers 403 while the address is
     * unconfirmed and hands back a session the moment it is not. That means no extra
     * endpoint and, in particular, no public "is this address verified?" oracle. Stops on
     * success, when the activity goes away, or when {@code stop} flips.
     */
    public static void watchForLinkVerification(Activity activity, String email, String password,
                                                boolean[] stop, OkData onVerified) {
        final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        final String target = normalize(email);
        final Runnable[] tick = new Runnable[1];
        tick[0] = () -> {
            if (stop[0] || activity.isFinishing() || activity.isDestroyed()) return;
            JSONObject body = new JSONObject();
            try {
                body.put("email", target);
                body.put("password", password);
            } catch (JSONException e) {
                return;   // nothing recoverable; the code path still works
            }
            StringRequest req = new StringRequest(
                    Request.Method.POST,
                    ApiConfig.BASE_URL + "/api/auth/login",
                    response -> {
                        if (stop[0]) return;
                        try {
                            JSONObject json = new JSONObject(response);
                            if (json.has("token")) {
                                stop[0] = true;
                                if (onVerified != null) onVerified.run(json);
                                return;
                            }
                        } catch (JSONException ignored) {}
                        handler.postDelayed(tick[0], POLL_INTERVAL_MS);
                    },
                    error -> {                       // still unverified, or offline
                        if (!stop[0]) handler.postDelayed(tick[0], POLL_INTERVAL_MS);
                    }
            ) {
                @Override public byte[] getBody() { return body.toString().getBytes(StandardCharsets.UTF_8); }
                @Override public String getBodyContentType() { return "application/json; charset=utf-8"; }
            };
            req.setRetryPolicy(new DefaultRetryPolicy(15000, 0, 1f));
            Volley.newRequestQueue(activity.getApplicationContext()).add(req);
        };
        handler.postDelayed(tick[0], POLL_INTERVAL_MS);
    }

    private static final long POLL_INTERVAL_MS = 4000L;
}

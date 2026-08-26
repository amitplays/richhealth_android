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

    private EmailVerificationHelper() {}

    /** POST /api/auth/send-otp — emails a fresh 6-digit code. No auth token needed. */
    public static void sendOtp(Context context, String email, Ok onOk, Err onErr) {
        JSONObject body = new JSONObject();
        try {
            body.put("email", email);
        } catch (JSONException e) {
            if (onErr != null) onErr.run("Something went wrong. Please try again.");
            return;
        }
        post(context, "/api/auth/send-otp", body, "otp sent",
                "Couldn't send the code. Please try again.", onOk, onErr);
    }

    /** POST /api/auth/verify-otp — confirms the code and marks the email verified. */
    public static void verifyOtp(Context context, String email, String code, Ok onOk, Err onErr) {
        JSONObject body = new JSONObject();
        try {
            body.put("email", email);
            body.put("otp", code);
        } catch (JSONException e) {
            if (onErr != null) onErr.run("Something went wrong. Please try again.");
            return;
        }
        post(context, "/api/auth/verify-otp", body, "verified",
                "Incorrect or expired code.", onOk, onErr);
    }

    private static void post(Context context, String path, JSONObject body, String okLog,
                             String fallbackError, Ok onOk, Err onErr) {
        StringRequest request = new StringRequest(
                Request.Method.POST,
                ApiConfig.BASE_URL + path,
                response -> {
                    ApiConfig.logRestCall(path, true, okLog);
                    if (onOk != null) onOk.run();
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
     * Post-login verification prompt. Unlike the signup dialog this one is DISMISSIBLE —
     * the user is already inside the app, so back / tap-outside means "later" and still
     * runs {@code onDone}. {@code onDone} always runs exactly once so the caller can
     * continue its login chain either way.
     */
    public static void show(Activity activity, String email, Ok onDone) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            if (onDone != null) onDone.run();
            return;
        }
        final boolean[] finished = {false};
        final Ok done = () -> {
            if (finished[0]) return;
            finished[0] = true;
            if (onDone != null) onDone.run();
        };

        LayoutInflater inflater = LayoutInflater.from(activity);
        View dialogView = inflater.inflate(R.layout.dialog_edit_profile, null);
        ((TextView) dialogView.findViewById(R.id.dialog_title)).setText("Verify your email");

        LinearLayout fieldsContainer = dialogView.findViewById(R.id.fields_container);
        fieldsContainer.removeAllViews();

        int gap = (int) (12 * activity.getResources().getDisplayMetrics().density);
        TextView info = new TextView(activity);
        info.setText("Enter the code we emailed to " + email + " to verify your account. "
                + "You can also resend it, or skip for now.");
        info.setTextColor(0xFFB0B0B0);
        info.setTextSize(14);
        info.setPadding(0, 0, 0, gap);
        fieldsContainer.addView(info);

        View fieldLayout = inflater.inflate(R.layout.dialog_profile_field_item, fieldsContainer, false);
        final TextInputLayout codeLayout = (TextInputLayout) fieldLayout;
        codeLayout.setHint("Verification code");
        final TextInputEditText codeInput = fieldLayout.findViewById(R.id.field_input);
        codeInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        codeInput.setFilters(new InputFilter[]{ new InputFilter.LengthFilter(6) });
        fieldsContainer.addView(fieldLayout);

        final Dialog dialog = new Dialog(activity, R.style.DialogTheme);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(dialogView);
        dialog.setCancelable(true);                  // "later" = back or tap outside
        dialog.setCanceledOnTouchOutside(true);
        dialog.setOnDismissListener(d -> done.run());
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
        verifyButton.setText("Verify");
        resendButton.setText("Resend");

        verifyButton.setOnClickListener(v -> {
            String code = codeInput.getText() != null ? codeInput.getText().toString().trim() : "";
            if (code.length() < 4) {
                codeLayout.setError("Enter the code from your email");
                return;
            }
            codeLayout.setError(null);
            verifyButton.setEnabled(false);
            verifyOtp(activity, email, code,
                    () -> {
                        Utilities.toast(activity, "Email verified");
                        if (dialog.isShowing()) dialog.dismiss();   // dismiss listener runs done
                        else done.run();
                    },
                    msg -> {
                        verifyButton.setEnabled(true);
                        codeLayout.setError(msg);
                    });
        });

        resendButton.setOnClickListener(v -> {
            resendButton.setEnabled(false);
            sendOtp(activity, email,
                    () -> {
                        resendButton.setEnabled(true);
                        Utilities.toast(activity, "New code sent.");
                    },
                    msg -> {
                        resendButton.setEnabled(true);
                        Utilities.toastLong(activity, msg);
                    });
        });

        dialog.show();
        // Fire the first code as the dialog opens, so the box is never empty-handed.
        sendOtp(activity, email, null, msg -> Utilities.toastLong(activity, msg));
    }
}

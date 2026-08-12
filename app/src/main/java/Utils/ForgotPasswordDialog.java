package Utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.util.Log;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Forgot-password flow (email -> emailed code -> new password), backed by
 * POST /api/auth/forgot-password and /api/auth/reset-password. Self-contained,
 * no auth token. Mirrors the app's existing Volley usage (see BiometricHelper).
 */
public class ForgotPasswordDialog {

    private static final String TAG = "ForgotPasswordDialog";

    public static void show(Activity activity) {
        if (activity == null) return;
        int pad = (int) (16 * activity.getResources().getDisplayMetrics().density);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, 0);

        final EditText emailEt = new EditText(activity);
        emailEt.setHint("Email");
        emailEt.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        root.addView(emailEt);

        final EditText codeEt = new EditText(activity);
        codeEt.setHint("6-digit code (emailed to you)");
        codeEt.setInputType(InputType.TYPE_CLASS_NUMBER);
        root.addView(codeEt);

        final EditText passEt = new EditText(activity);
        passEt.setHint("New password (min. 8 characters)");
        passEt.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(passEt);

        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Reset password")
                .setView(root)
                .setPositiveButton("Reset", null)
                .setNeutralButton("Send code", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                String email = emailEt.getText().toString().trim();
                if (email.isEmpty()) { emailEt.setError("Enter your email"); return; }
                sendCode(activity, email);
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String email = emailEt.getText().toString().trim();
                String code = codeEt.getText().toString().trim();
                String pass = passEt.getText().toString();
                if (email.isEmpty()) { emailEt.setError("Enter your email"); return; }
                if (code.isEmpty()) { codeEt.setError("Enter the code"); return; }
                if (pass.length() < 8) { passEt.setError("Min. 8 characters"); return; }
                resetPassword(activity, email, code, pass, dialog);
            });
        });
        dialog.show();
    }

    private static void sendCode(Context context, String email) {
        try {
            JSONObject body = new JSONObject();
            body.put("email", email);
            postJson(context, "/api/auth/forgot-password", body,
                    r -> Toast.makeText(context, "If an account exists, a code was emailed.", Toast.LENGTH_LONG).show(),
                    e -> Toast.makeText(context, "Couldn't send the code. Try again.", Toast.LENGTH_SHORT).show());
        } catch (Exception e) { Log.w(TAG, "sendCode", e); }
    }

    private static void resetPassword(Context context, String email, String code, String pass, AlertDialog dialog) {
        try {
            JSONObject body = new JSONObject();
            body.put("email", email);
            body.put("otp", code);
            body.put("newPassword", pass);
            postJson(context, "/api/auth/reset-password", body,
                    r -> { Toast.makeText(context, "Password updated. You can log in now.", Toast.LENGTH_LONG).show(); dialog.dismiss(); },
                    e -> Toast.makeText(context, "Incorrect or expired code.", Toast.LENGTH_SHORT).show());
        } catch (Exception e) { Log.w(TAG, "resetPassword", e); }
    }

    private static void postJson(Context context, String path, JSONObject body,
                                 Response.Listener<String> onOk, Response.ErrorListener onErr) {
        final byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
        String url = ApiConfig.BASE_URL + path;
        StringRequest req = new StringRequest(Request.Method.POST, url, onOk, onErr) {
            @Override public byte[] getBody() { return payload; }
            @Override public String getBodyContentType() { return "application/json; charset=utf-8"; }
            @Override public Map<String, String> getHeaders() {
                Map<String, String> h = new HashMap<>();
                h.put("Content-Type", "application/json");
                return h;
            }
        };
        Volley.newRequestQueue(context).add(req);
    }
}

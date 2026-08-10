package Utils;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.example.richhealth.R;
import com.example.richhealth.Activities.TokenManager;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Incoming family-request sheet — a half-screen glass bottom sheet (matches the
 * app's {@link UsageBottomSheet} styling) that lets a user Accept/Reject relatives
 * who invited them to connect. Backend was fully built; there was no reachable UI.
 *
 * Data:  GET  /api/user/relationship/requests  → { incomingRequests: [{email,name,relationship,status}] }
 * Action: POST /api/user/relationship/respond   { email, accept }
 */
public final class FamilyRequestsSheet {

    private FamilyRequestsSheet() {}

    public interface OnChanged { void onChanged(); }
    public interface CountCallback { void onCount(int pending); }

    // ── Badge count (used by the Profile header icon) ─────────────────────────

    /** Fetches the number of pending incoming family requests (0 on any failure). */
    public static void fetchPendingCount(final Activity activity, final CountCallback cb) {
        if (activity == null || cb == null) return;
        fetchRequests(activity, arr -> cb.onCount(arr == null ? 0 : arr.length()));
    }

    // ── Sheet ─────────────────────────────────────────────────────────────────

    public static void show(final Activity activity, final OnChanged onChanged) {
        if (activity == null || activity.isFinishing()) return;

        final BottomSheetDialog dialog = new BottomSheetDialog(activity, R.style.RH_Theme_BottomSheetDialog);
        View sheet = LayoutInflater.from(activity).inflate(R.layout.sheet_family_requests, null);
        dialog.setContentView(sheet);

        View container = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (container != null) container.setBackgroundColor(android.graphics.Color.TRANSPARENT);

        final LinearLayout rows = sheet.findViewById(R.id.family_requests_rows);
        final TextView empty = sheet.findViewById(R.id.family_requests_empty);
        final ProgressBar loading = sheet.findViewById(R.id.family_requests_loading);

        loading.setVisibility(View.VISIBLE);
        empty.setVisibility(View.GONE);

        fetchRequests(activity, requests -> {
            loading.setVisibility(View.GONE);
            renderRows(activity, dialog, rows, empty, requests, onChanged);
        });

        dialog.show();
    }

    private static void renderRows(final Activity activity, final BottomSheetDialog dialog,
                                   final LinearLayout rows, final TextView empty,
                                   final JSONArray requests, final OnChanged onChanged) {
        rows.removeAllViews();
        if (requests == null || requests.length() == 0) {
            empty.setVisibility(View.VISIBLE);
            return;
        }
        empty.setVisibility(View.GONE);
        LayoutInflater inflater = LayoutInflater.from(activity);
        for (int i = 0; i < requests.length(); i++) {
            JSONObject req = requests.optJSONObject(i);
            if (req == null) continue;
            rows.addView(buildRow(activity, dialog, inflater, rows, empty, req, onChanged));
        }
    }

    private static View buildRow(final Activity activity, final BottomSheetDialog dialog,
                                 LayoutInflater inflater, final LinearLayout rows, final TextView empty,
                                 final JSONObject req, final OnChanged onChanged) {
        View card = inflater.inflate(R.layout.item_request_card, rows, false);

        final String email = req.optString("email", "");
        String name = req.optString("name", "");
        String relationship = req.optString("relationship", "");

        TextView nameView = card.findViewById(R.id.request_name);
        TextView descView = card.findViewById(R.id.request_description);
        android.widget.ImageView icon = card.findViewById(R.id.request_icon);
        final MaterialButton accept = card.findViewById(R.id.accept_button);
        final MaterialButton reject = card.findViewById(R.id.reject_button);

        nameView.setText(!name.isEmpty() ? name : (!email.isEmpty() ? email : "Someone"));
        descView.setText(relationship.isEmpty()
                ? "Wants to connect with you as family"
                : "Wants to connect as " + relationship);
        if (icon != null) icon.setImageResource(R.drawable.ic_family_group);

        accept.setOnClickListener(v -> respond(activity, dialog, rows, empty, card,
                email, true, accept, reject, onChanged));
        reject.setOnClickListener(v -> respond(activity, dialog, rows, empty, card,
                email, false, accept, reject, onChanged));
        return card;
    }

    private static void respond(final Activity activity, final BottomSheetDialog dialog,
                                final LinearLayout rows, final TextView empty, final View card,
                                final String email, final boolean accept,
                                final MaterialButton acceptBtn, final MaterialButton rejectBtn,
                                final OnChanged onChanged) {
        if (email == null || email.isEmpty()) return;
        // Prevent double-taps while the request is in flight.
        acceptBtn.setEnabled(false);
        rejectBtn.setEnabled(false);

        TokenManager tm = TokenManager.getInstance(activity);
        final String token = tm != null ? tm.getToken() : null;
        if (token == null) { acceptBtn.setEnabled(true); rejectBtn.setEnabled(true); return; }

        final String url = ApiConfig.BASE_URL + "/api/user/relationship/respond";
        final String body = "{\"email\":\"" + email.replace("\"", "\\\"") + "\",\"accept\":" + accept + "}";

        StringRequest req = new StringRequest(Request.Method.POST, url,
                response -> {
                    ApiConfig.logRestCall(url, true, accept ? "accepted" : "rejected");
                    rows.removeView(card);
                    if (rows.getChildCount() == 0) empty.setVisibility(View.VISIBLE);
                    Toast.makeText(activity, accept ? "Request accepted" : "Request declined",
                            Toast.LENGTH_SHORT).show();
                    if (onChanged != null) onChanged.onChanged();
                },
                error -> {
                    ApiConfig.logRestCall(url, false, error.toString());
                    acceptBtn.setEnabled(true);
                    rejectBtn.setEnabled(true);
                    Toast.makeText(activity, "Couldn't update the request. Please try again.",
                            Toast.LENGTH_SHORT).show();
                }) {
            @Override public byte[] getBody() { return body.getBytes(StandardCharsets.UTF_8); }
            @Override public String getBodyContentType() { return "application/json; charset=utf-8"; }
            @Override public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> h = new HashMap<>();
                h.put("Authorization", "Bearer " + token);
                return h;
            }
        };
        Volley.newRequestQueue(activity).add(req);
    }

    // ── Networking (shared) ───────────────────────────────────────────────────

    private interface RequestsCallback { void onResult(JSONArray pendingIncoming); }

    private static void fetchRequests(final Activity activity, final RequestsCallback cb) {
        TokenManager tm = TokenManager.getInstance(activity);
        final String token = tm != null ? tm.getToken() : null;
        if (token == null) { cb.onResult(null); return; }

        final String url = ApiConfig.BASE_URL + "/api/user/relationship/requests";
        StringRequest req = new StringRequest(Request.Method.GET, url,
                response -> {
                    try {
                        JSONObject json = new JSONObject(response);
                        JSONArray incoming = json.optJSONArray("incomingRequests");
                        JSONArray pending = new JSONArray();
                        if (incoming != null) {
                            for (int i = 0; i < incoming.length(); i++) {
                                JSONObject r = incoming.optJSONObject(i);
                                if (r == null) continue;
                                // status defaults to "pending"; treat missing as pending too.
                                String status = r.optString("status", "pending");
                                if ("pending".equalsIgnoreCase(status)) pending.put(r);
                            }
                        }
                        cb.onResult(pending);
                    } catch (Exception e) {
                        cb.onResult(null);
                    }
                },
                error -> {
                    ApiConfig.logRestCall(url, false, error.toString());
                    cb.onResult(null);
                }) {
            @Override public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> h = new HashMap<>();
                h.put("Authorization", "Bearer " + token);
                return h;
            }
        };
        Volley.newRequestQueue(activity).add(req);
    }
}

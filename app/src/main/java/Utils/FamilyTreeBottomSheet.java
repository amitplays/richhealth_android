package Utils;

import android.app.Activity;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.example.richhealth.R;
import com.example.richhealth.Activities.TokenManager;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * "Your Family" sheet — a generation-layered graph of everyone connected to the
 * signed-in user, drawn by {@link FamilyTreeView}.
 *
 * Pulls the three sources the backend exposes in parallel and renders whatever
 * arrives; a single failed call degrades the graph rather than emptying it.
 * Mirrors the iOS FamilyTreeSheet.
 */
public final class FamilyTreeBottomSheet {

    private FamilyTreeBottomSheet() {}

    /**
     * @param selfName the signed-in user's name, used for the centre node. Pass
     *                 null and it falls back to "You".
     */
    public static void show(final Activity activity, final String selfName) {
        if (activity == null || activity.isFinishing()) return;

        final BottomSheetDialog dialog =
                new BottomSheetDialog(activity, R.style.RH_Theme_BottomSheetDialog);
        View sheet = LayoutInflater.from(activity).inflate(R.layout.sheet_family_tree, null);
        dialog.setContentView(sheet);

        // Same treatment as UsageBottomSheet: clear the container so only our
        // rounded-top surface shows, with no second elevated layer behind it.
        View container = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (container != null) container.setBackgroundColor(Color.TRANSPARENT);

        final TextView subtitle = sheet.findViewById(R.id.family_sheet_subtitle);
        final TextView empty = sheet.findViewById(R.id.family_sheet_empty);
        final View loading = sheet.findViewById(R.id.family_sheet_loading);
        final FamilyTreeView tree = sheet.findViewById(R.id.family_tree_view);

        tree.setOnNodeTapListener(node -> {
            if (node.isSelf) return;
            String line = node.displayName() + " · "
                    + (node.isPending ? "invite pending" : node.relationship);
            Toast.makeText(activity, line, Toast.LENGTH_SHORT).show();
        });

        loading.setVisibility(View.VISIBLE);
        tree.setVisibility(View.INVISIBLE);
        empty.setVisibility(View.GONE);

        fetchAll(activity, (relationships, dependentUsers, dependents) -> {
            if (activity.isFinishing()) return;

            List<FamilyGraph.Node> nodes =
                    FamilyGraph.build(selfName, relationships, dependentUsers, dependents);
            int members = FamilyGraph.memberCount(nodes);

            loading.setVisibility(View.GONE);

            if (members == 0) {
                tree.setVisibility(View.GONE);
                empty.setVisibility(View.VISIBLE);
                subtitle.setText("No family connected yet");
                return;
            }

            tree.setNodes(nodes);
            tree.setVisibility(View.VISIBLE);
            empty.setVisibility(View.GONE);
            subtitle.setText(members == 1 ? "1 family member" : members + " family members");
        });

        dialog.show();
    }

    // ── Networking ──────────────────────────────────────────────────────────

    private interface FamilyCallback {
        void onResult(JSONObject relationships, JSONObject dependentUsers, JSONObject dependents);
    }

    /**
     * Fires the three GETs together and calls back once all have settled. Each
     * slot is null if that call failed, so a partial result still draws.
     */
    private static void fetchAll(final Activity activity, final FamilyCallback cb) {
        TokenManager tm = TokenManager.getInstance(activity);
        final String token = tm != null ? tm.getToken() : null;
        if (token == null) { cb.onResult(null, null, null); return; }

        final JSONObject[] slots = new JSONObject[3];
        final int[] remaining = {3};
        final RequestQueue queue = Volley.newRequestQueue(activity);

        String[] urls = {
                // Plural "users" — matches HealthDataFragment and both iOS call sites.
                ApiConfig.BASE_URL + "/api/users/relationships",
                ApiConfig.BASE_URL + "/api/dependents/users",
                ApiConfig.BASE_URL + "/api/dependents",
        };

        for (int i = 0; i < urls.length; i++) {
            final int slot = i;
            final String url = urls[i];
            StringRequest req = new StringRequest(Request.Method.GET, url,
                    response -> {
                        ApiConfig.logRestCall(url, true, "Family source fetched");
                        try { slots[slot] = new JSONObject(response); }
                        catch (Exception ignored) { slots[slot] = null; }
                        if (--remaining[0] == 0) cb.onResult(slots[0], slots[1], slots[2]);
                    },
                    error -> {
                        ApiConfig.logRestCall(url, false, error.toString());
                        slots[slot] = null;
                        if (--remaining[0] == 0) cb.onResult(slots[0], slots[1], slots[2]);
                    }) {
                @Override
                public Map<String, String> getHeaders() throws AuthFailureError {
                    Map<String, String> h = new HashMap<>();
                    h.put("Authorization", "Bearer " + token);
                    return h;
                }
            };
            queue.add(req);
        }
    }
}

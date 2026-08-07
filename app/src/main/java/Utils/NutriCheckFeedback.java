package Utils;

import android.content.Context;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * POST thumbs-up/down feedback on a NutriCheck history entry. The server persists the
 * reaction on that entry and feeds liked/disliked verdicts back into future NutriCheck
 * prompts so the AI learns from prior agreement/disagreement.
 */
public class NutriCheckFeedback {

    public interface ResultCallback {
        void onResult(boolean success);
    }

    /** reaction = "up", "down", or null (clear). */
    public static void send(Context context, String token, String historyId, String reaction,
                             ResultCallback cb) {
        if (token == null || historyId == null || historyId.isEmpty()) {
            if (cb != null) cb.onResult(false);
            return;
        }
        final Context appCtx = context.getApplicationContext();
        String url = ApiConfig.BASE_URL + "/api/home/nutri-check/feedback";

        JSONObject body = new JSONObject();
        try {
            body.put("id", historyId);
            if (reaction == null) body.put("reaction", JSONObject.NULL);
            else body.put("reaction", reaction);
        } catch (JSONException e) {
            if (cb != null) cb.onResult(false);
            return;
        }

        JsonObjectRequest req = new JsonObjectRequest(Request.Method.POST, url, body,
                response -> {
                    ApiConfig.logRestCall(url, true, "NutriCheck feedback saved");
                    if (cb != null) cb.onResult(true);
                },
                error -> {
                    ApiConfig.logRestCall(url, false, error.toString());
                    if (cb != null) cb.onResult(false);
                }) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + token);
                headers.put("Content-Type", "application/json");
                return headers;
            }
        };
        req.setRetryPolicy(new DefaultRetryPolicy(8000, 1, 1.0f));
        Volley.newRequestQueue(appCtx).add(req);
    }
}

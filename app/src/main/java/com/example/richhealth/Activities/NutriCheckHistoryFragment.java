package com.example.richhealth.Activities;
import Utils.Utilities;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.example.richhealth.R;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import Utils.ApiConfig;
import Utils.NutriCheckFeedback;

public class NutriCheckHistoryFragment extends Fragment {

    private RecyclerView historyRecycler;
    private LinearLayout historyLoading;
    private LinearLayout historyEmpty;

    public static NutriCheckHistoryFragment newInstance() {
        return new NutriCheckHistoryFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_nutri_check_history, container, false);
        historyRecycler = view.findViewById(R.id.history_recycler);
        historyLoading = view.findViewById(R.id.history_loading);
        historyEmpty = view.findViewById(R.id.history_empty);

        historyRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        refreshData();
        return view;
    }

    public void refreshData() {
        if (getActivity() instanceof NutriCheckActivity) {
            NutriCheckActivity activity = (NutriCheckActivity) getActivity();
            List<JSONObject> history = activity.getHistory();
            if (history == null) {
                showLoading();
            } else if (history.isEmpty()) {
                showEmpty();
            } else {
                showHistory(history);
            }
        }
    }

    private void showLoading() {
        if (historyLoading == null) return;
        historyLoading.setVisibility(View.VISIBLE);
        historyEmpty.setVisibility(View.GONE);
        historyRecycler.setVisibility(View.GONE);
    }

    private void showEmpty() {
        if (historyEmpty == null) return;
        historyLoading.setVisibility(View.GONE);
        historyEmpty.setVisibility(View.VISIBLE);
        historyRecycler.setVisibility(View.GONE);
    }

    private void showHistory(List<JSONObject> history) {
        if (historyRecycler == null) return;
        historyLoading.setVisibility(View.GONE);
        historyEmpty.setVisibility(View.GONE);
        historyRecycler.setVisibility(View.VISIBLE);
        historyRecycler.setAdapter(new HistoryAdapter(history));
    }

    private void deleteHistoryItem(String itemId, int position) {
        NutriCheckActivity activity = (NutriCheckActivity) getActivity();
        if (activity == null) return;

        Utils.DialogUtils.showConfirmDialog(requireContext(),
                "Delete Check",
                "Remove this NutriCheck result from your history?",
                "Delete", "Cancel", true,
                () -> {
                    String token = activity.getToken();
                    if (token == null) return;

                    String url = ApiConfig.BASE_URL + "/api/home/nutri-check/history/" + itemId;

                    StringRequest request = new StringRequest(Request.Method.DELETE, url,
                            response -> activity.reloadHistory(),
                            error -> Utilities.toast(requireContext(), "Failed to delete")
                    ) {
                        @Override
                        public Map<String, String> getHeaders() throws AuthFailureError {
                            Map<String, String> headers = new HashMap<>();
                            headers.put("Authorization", "Bearer " + token);
                            return headers;
                        }
                    };

                    Volley.newRequestQueue(requireContext()).add(request);
                });
    }

    private String formatTimeAgo(String isoTimestamp) {
        if (isoTimestamp == null || isoTimestamp.isEmpty()) return "";
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            Date date = sdf.parse(isoTimestamp.substring(0, Math.min(19, isoTimestamp.length())));
            if (date == null) return isoTimestamp;

            long now = System.currentTimeMillis();
            long diff = now - date.getTime();
            long minutes = diff / 60000;
            long hours = minutes / 60;
            long days = hours / 24;

            if (minutes < 1) return "Just now";
            if (minutes < 60) return minutes + " min ago";
            if (hours < 24) return hours + "h ago";
            if (days < 7) return days + "d ago";

            return new SimpleDateFormat("MMM d, yyyy", Locale.US).format(date);
        } catch (Exception e) {
            return isoTimestamp;
        }
    }

    // ── Adapter ──

    private class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

        private final List<JSONObject> items;
        // Guards against rapid double-taps: each tap while a POST is pending is ignored,
        // not queued, so the server never sees out-of-order writes for the same entry.
        private final java.util.Set<String> inflight = new java.util.HashSet<>();

        private final int TINT_NEUTRAL = Color.parseColor("#808080");
        private final int TINT_UP = Color.parseColor("#4CAF50");
        private final int TINT_DOWN = Color.parseColor("#FF5252");

        HistoryAdapter(List<JSONObject> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_nutri_check_history, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            JSONObject item = items.get(position);

            String foodItem = item.optString("foodItem", "");
            String recommendation = item.optString("recommendation", "");
            String reason = item.optString("reason", "");
            String checkedAt = item.optString("checkedAt", "");
            String dataChangesSince = item.optString("dataChangesSince", "");
            String itemId = item.optString("_id", "");
            String reaction = item.isNull("userReaction") ? null : item.optString("userReaction", null);

            holder.foodName.setText(foodItem);
            holder.reasonText.setText(reason);
            holder.timestampText.setText(formatTimeAgo(checkedAt));

            // Recommendation badge
            String rec = recommendation.toLowerCase();
            switch (rec) {
                case "strong_yes":
                    holder.recommendationBadge.setText("Definitely good");
                    holder.recommendationBadge.setTextColor(Color.parseColor("#4CAF50"));
                    break;
                case "yes":
                    holder.recommendationBadge.setText("Generally okay");
                    holder.recommendationBadge.setTextColor(Color.parseColor("#8BC34A"));
                    break;
                case "moderate":
                    holder.recommendationBadge.setText("In moderation");
                    holder.recommendationBadge.setTextColor(Color.parseColor("#FFC107"));
                    break;
                case "no":
                    holder.recommendationBadge.setText("Not recommended");
                    holder.recommendationBadge.setTextColor(Color.parseColor("#FF5722"));
                    break;
                case "strong_no":
                    holder.recommendationBadge.setText("Avoid");
                    holder.recommendationBadge.setTextColor(Color.parseColor("#F44336"));
                    break;
                default:
                    holder.recommendationBadge.setText("Unknown");
                    holder.recommendationBadge.setTextColor(Color.GRAY);
                    break;
            }

            // Data changes warning
            if (dataChangesSince != null && !dataChangesSince.isEmpty()) {
                holder.dataChangesText.setText(dataChangesSince);
                holder.dataChangesText.setVisibility(View.VISIBLE);
            } else {
                holder.dataChangesText.setVisibility(View.GONE);
            }

            holder.deleteButton.setOnClickListener(v ->
                    deleteHistoryItem(itemId, holder.getAdapterPosition()));

            // Feedback thumbs — visible only when the entry has a server id.
            boolean hasId = !itemId.isEmpty();
            holder.feedbackRow.setVisibility(hasId ? View.VISIBLE : View.GONE);
            applyReactionTint(holder, reaction);
            boolean isInflight = inflight.contains(itemId);
            holder.thumbUp.setEnabled(!isInflight);
            holder.thumbDown.setEnabled(!isInflight);
            holder.thumbUp.setOnClickListener(v -> handleThumbTap(item, itemId, "up"));
            holder.thumbDown.setOnClickListener(v -> handleThumbTap(item, itemId, "down"));
        }

        private void handleThumbTap(JSONObject item, String itemId, String tapped) {
            if (itemId.isEmpty() || inflight.contains(itemId)) return;

            NutriCheckActivity activity = (NutriCheckActivity) getActivity();
            if (activity == null) return;
            String token = activity.getToken();
            if (token == null) return;

            final String prev = item.isNull("userReaction") ? null : item.optString("userReaction", null);
            final String next = tapped.equals(prev) ? null : tapped;  // toggle off if same

            try {
                if (next == null) item.put("userReaction", JSONObject.NULL);
                else item.put("userReaction", next);
            } catch (org.json.JSONException ignored) {}

            inflight.add(itemId);
            int idx = items.indexOf(item);
            if (idx != -1) notifyItemChanged(idx);

            NutriCheckFeedback.send(activity, token, itemId, next, success -> {
                inflight.remove(itemId);
                if (!success) {
                    try {
                        if (prev == null) item.put("userReaction", JSONObject.NULL);
                        else item.put("userReaction", prev);
                    } catch (org.json.JSONException ignored) {}
                    Utilities.toast(requireContext(), "Couldn't save feedback. Try again.");
                }
                int i = items.indexOf(item);
                if (i != -1) notifyItemChanged(i);
            });
        }

        private void applyReactionTint(ViewHolder h, String reaction) {
            int upTint = "up".equals(reaction) ? TINT_UP : TINT_NEUTRAL;
            int downTint = "down".equals(reaction) ? TINT_DOWN : TINT_NEUTRAL;
            h.thumbUp.setIconTint(ColorStateList.valueOf(upTint));
            h.thumbDown.setIconTint(ColorStateList.valueOf(downTint));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView foodName, recommendationBadge, reasonText, dataChangesText, timestampText;
            ImageButton deleteButton;
            LinearLayout feedbackRow;
            MaterialButton thumbUp, thumbDown;

            ViewHolder(View v) {
                super(v);
                foodName = v.findViewById(R.id.food_name);
                recommendationBadge = v.findViewById(R.id.recommendation_badge);
                reasonText = v.findViewById(R.id.reason_text);
                dataChangesText = v.findViewById(R.id.data_changes_text);
                timestampText = v.findViewById(R.id.timestamp_text);
                deleteButton = v.findViewById(R.id.delete_button);
                feedbackRow = v.findViewById(R.id.feedback_row);
                thumbUp = v.findViewById(R.id.thumb_up);
                thumbDown = v.findViewById(R.id.thumb_down);
            }
        }
    }
}

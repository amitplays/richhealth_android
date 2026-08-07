package com.example.richhealth.Activities;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.richhealth.R;
import com.google.android.material.card.MaterialCardView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class DietaryHistoryFragment extends Fragment {

    private RecyclerView historyRecycler;
    private LinearLayout historyLoading;
    private LinearLayout historyEmpty;

    public static DietaryHistoryFragment newInstance() {
        return new DietaryHistoryFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_dietary_history, container, false);
        historyRecycler = view.findViewById(R.id.history_recycler);
        historyLoading = view.findViewById(R.id.history_loading);
        historyEmpty = view.findViewById(R.id.history_empty);

        historyRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        refreshData();
        return view;
    }

    public void refreshData() {
        if (getActivity() instanceof DietaryInsightsActivity) {
            DietaryInsightsActivity activity = (DietaryInsightsActivity) getActivity();
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

    // ── Adapter ──────────────────────────────────────────────────────────────

    private class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

        private final List<JSONObject> items;

        HistoryAdapter(List<JSONObject> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_dietary_history, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            JSONObject item = items.get(position);

            // Date
            String rawDate = item.optString("generatedAt", "");
            holder.date.setText(formatDate(rawDate));

            // Trigger badge
            String trigger = item.optString("trigger", "");
            holder.trigger.setText(triggerLabel(trigger));

            // Food counts
            JSONArray eatArr = item.optJSONArray("foodsToEat");
            JSONArray avoidArr = item.optJSONArray("foodsToAvoid");
            int eatCount = eatArr != null ? eatArr.length() : 0;
            int avoidCount = avoidArr != null ? avoidArr.length() : 0;
            holder.eatCount.setText(eatCount + " food" + (eatCount != 1 ? "s" : "") + " to eat");
            holder.avoidCount.setText(avoidCount + " food" + (avoidCount != 1 ? "s" : "") + " to avoid");

            // Eat preview (first 3 names)
            holder.eatPreview.setText(buildPreview(eatArr));

            // Avoid preview
            holder.avoidPreview.setText(buildPreview(avoidArr));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        private String formatDate(String iso) {
            if (iso == null || iso.isEmpty()) return "Unknown date";
            try {
                SimpleDateFormat input = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault());
                input.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date date = input.parse(iso);
                if (date == null) return iso;
                return new SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(date);
            } catch (ParseException e) {
                // Try without millis
                try {
                    SimpleDateFormat input2 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault());
                    input2.setTimeZone(TimeZone.getTimeZone("UTC"));
                    Date date = input2.parse(iso);
                    if (date == null) return iso;
                    return new SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(date);
                } catch (ParseException ex) {
                    return iso;
                }
            }
        }

        private String triggerLabel(String trigger) {
            switch (trigger) {
                case "data_change": return "Data Updated";
                case "manual_refresh": return "Refreshed";
                case "first_load": return "First Load";
                default: return "Generated";
            }
        }

        private String buildPreview(JSONArray arr) {
            if (arr == null || arr.length() == 0) return "—";
            List<String> names = new ArrayList<>();
            for (int i = 0; i < Math.min(arr.length(), 3); i++) {
                JSONObject food = arr.optJSONObject(i);
                if (food != null) {
                    names.add(food.optString("name", ""));
                }
            }
            if (arr.length() > 3) names.add("+" + (arr.length() - 3) + " more");
            return String.join(", ", names);
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView date, trigger, eatCount, avoidCount, eatPreview, avoidPreview;

            ViewHolder(@NonNull View v) {
                super(v);
                date = v.findViewById(R.id.history_date);
                trigger = v.findViewById(R.id.history_trigger);
                eatCount = v.findViewById(R.id.history_eat_count);
                avoidCount = v.findViewById(R.id.history_avoid_count);
                eatPreview = v.findViewById(R.id.history_eat_preview);
                avoidPreview = v.findViewById(R.id.history_avoid_preview);
            }
        }
    }
}

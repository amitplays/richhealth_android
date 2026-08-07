package com.example.richhealth.Activities;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.example.richhealth.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import Utils.ApiConfig;

public class ActivityTimelineFragment extends Fragment {

    private static final String TAG = "ActivityTimelineFragment";

    private RecyclerView timelineRecycler;
    private View emptyState;
    private View loadingState;
    private List<TimelineEvent> events = new ArrayList<>();
    private TimelineAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_activity_timeline, container, false);

        timelineRecycler = view.findViewById(R.id.timeline_recycler);
        emptyState = view.findViewById(R.id.empty_state);
        loadingState = view.findViewById(R.id.loading_state);


        timelineRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new TimelineAdapter();
        timelineRecycler.setAdapter(adapter);

        fetchActivityLog();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        fetchActivityLog();
    }

    private void fetchActivityLog() {
        TokenManager tokenManager = TokenManager.getInstance(requireContext());
        String token = tokenManager != null ? tokenManager.getToken() : null;

        if (token == null) {
            showEmpty();
            return;
        }

        showLoading();

        String url = ApiConfig.BASE_URL + "/api/user/activity-log";

        StringRequest request = new StringRequest(Request.Method.GET, url,
                response -> {
                    try {
                        JSONObject json = new JSONObject(response);
                        JSONArray activities = json.optJSONArray("activities");

                        events.clear();
                        if (activities != null) {
                            for (int i = 0; i < activities.length(); i++) {
                                JSONObject item = activities.getJSONObject(i);
                                String type = item.optString("type", "");
                                String description = item.optString("description", "");
                                String timestamp = item.optString("timestamp", "");
                                long ts = parseIsoTimestamp(timestamp);
                                int icon = getIconForType(type);
                                String title = getTitleForType(type);
                                events.add(new TimelineEvent(title, description, ts, icon));
                            }
                        }

                        if (events.isEmpty()) {
                            showEmpty();
                        } else {
                            showTimeline();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing activity log", e);
                        showEmpty();
                    }
                },
                error -> {
                    Log.e(TAG, "Error fetching activity log", error);
                    showEmpty();
                }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + token);
                return headers;
            }
        };

        request.setRetryPolicy(new DefaultRetryPolicy(15000, 1, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        Volley.newRequestQueue(requireContext()).add(request);
    }

    private void showLoading() {
        if (loadingState != null) loadingState.setVisibility(View.VISIBLE);
        emptyState.setVisibility(View.GONE);
        timelineRecycler.setVisibility(View.GONE);
    }

    private void showEmpty() {
        if (loadingState != null) loadingState.setVisibility(View.GONE);
        emptyState.setVisibility(View.VISIBLE);
        timelineRecycler.setVisibility(View.GONE);
    }

    private void showTimeline() {
        if (loadingState != null) loadingState.setVisibility(View.GONE);
        emptyState.setVisibility(View.GONE);
        timelineRecycler.setVisibility(View.VISIBLE);
        adapter.notifyDataSetChanged();
    }

    private String getTitleForType(String type) {
        switch (type) {
            case "health_analysis": return "Health Analysis";
            case "dietary_insights": return "Diet Guide";
            case "nutri_check": return "NutriCheck";
            case "report_analysis": return "Report Analysis";
            case "aqi_check": return "Air Quality";
            case "health_assistant": return "Health Assistant";
            case "symptom": return "Symptom";
            case "medication": return "Medication";
            case "measurement": return "Measurement";
            case "family": return "Family";
            default: return "Activity";
        }
    }

    private int getIconForType(String type) {
        switch (type) {
            case "health_analysis": return R.drawable.ic_analysis;
            case "dietary_insights": return R.drawable.ic_food;
            case "nutri_check": return R.drawable.ic_food;
            case "report_analysis": return R.drawable.ic_medical_reports;
            case "aqi_check": return R.drawable.ic_analysis;
            case "symptom": return R.drawable.ic_symptoms;
            case "medication": return R.drawable.ic_medications;
            case "measurement": return R.drawable.ic_blood_pressure;
            case "family": return R.drawable.ic_family;
            default: return R.drawable.ic_analysis;
        }
    }

    private long parseIsoTimestamp(String iso) {
        if (iso == null || iso.isEmpty()) return 0;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
            Date date = sdf.parse(iso);
            if (date != null) return date.getTime();
        } catch (Exception ignored) {}
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            Date date = sdf.parse(iso);
            if (date != null) return date.getTime();
        } catch (Exception ignored) {}
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            Date date = sdf.parse(iso.substring(0, Math.min(19, iso.length())));
            if (date != null) return date.getTime();
        } catch (Exception ignored) {}
        return 0;
    }

    private String formatTimeAgo(long timestamp) {
        long now = System.currentTimeMillis();
        long diff = now - timestamp;

        long seconds = diff / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (seconds < 60) return "Just now";
        if (minutes < 60) return minutes + " min ago";
        if (hours < 24) return hours + "h ago";
        if (days < 7) return days + "d ago";

        SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy", Locale.US);
        return sdf.format(new Date(timestamp));
    }

    // ── Data model ──

    static class TimelineEvent {
        String title;
        String description;
        long timestamp;
        int iconRes;

        TimelineEvent(String title, String description, long timestamp, int iconRes) {
            this.title = title;
            this.description = description;
            this.timestamp = timestamp;
            this.iconRes = iconRes;
        }
    }

    // ── Adapter ──

    class TimelineAdapter extends RecyclerView.Adapter<TimelineAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_timeline_event, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            TimelineEvent event = events.get(position);
            holder.title.setText(event.title);
            holder.description.setText(event.description);
            holder.time.setText(formatTimeAgo(event.timestamp));
            holder.icon.setImageResource(event.iconRes);

            // Hide timeline line for last item
            holder.timelineLine.setVisibility(position == events.size() - 1 ? View.INVISIBLE : View.VISIBLE);
        }

        @Override
        public int getItemCount() {
            return events.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView icon;
            TextView title, description, time;
            View timelineDot, timelineLine;

            ViewHolder(View v) {
                super(v);
                icon = v.findViewById(R.id.event_icon);
                title = v.findViewById(R.id.event_title);
                description = v.findViewById(R.id.event_description);
                time = v.findViewById(R.id.event_time);
                timelineDot = v.findViewById(R.id.timeline_dot);
                timelineLine = v.findViewById(R.id.timeline_line);
            }
        }
    }
}

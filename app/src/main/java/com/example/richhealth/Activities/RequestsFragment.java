package com.example.richhealth.Activities;
import Utils.Utilities;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

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
import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import Utils.ApiConfig;
import Utils.SimpleProgress;

public class RequestsFragment extends Fragment {

    private static final String TAG = "RequestsFragment";

    private RecyclerView requestsRecycler;
    private View emptyState;

    private List<ListItem> listItems = new ArrayList<>();
    private RequestListAdapter adapter;

    // Raw data
    private List<Map<String, String>> doctorRequests = new ArrayList<>();
    private List<Map<String, String>> familyRequests = new ArrayList<>();

    private int pendingApiCalls = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_requests, container, false);

        requestsRecycler = view.findViewById(R.id.requests_recycler);
        emptyState = view.findViewById(R.id.empty_state);

        requestsRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new RequestListAdapter();
        requestsRecycler.setAdapter(adapter);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadRequests();
    }

    private void loadRequests() {
        if (getView() != null) {
            SimpleProgress.show(getView(), "Loading requests...");
        }
        emptyState.setVisibility(View.GONE);
        requestsRecycler.setVisibility(View.GONE);

        doctorRequests.clear();
        familyRequests.clear();
        pendingApiCalls = 2;

        fetchDoctorRequests();
        fetchFamilyRequests();
    }

    private void onApiCallComplete() {
        pendingApiCalls--;
        if (pendingApiCalls <= 0) {
            SimpleProgress.hide();
            buildListItems();

            if (listItems.isEmpty()) {
                emptyState.setVisibility(View.VISIBLE);
                requestsRecycler.setVisibility(View.GONE);
            } else {
                emptyState.setVisibility(View.GONE);
                requestsRecycler.setVisibility(View.VISIBLE);
            }
            adapter.notifyDataSetChanged();
        }
    }

    private void fetchDoctorRequests() {
        String token = TokenManager.getInstance(requireContext()).getToken();
        if (token == null) { onApiCallComplete(); return; }

        String url = ApiConfig.BASE_URL + "/api/users/doctor/doctor/requests";

        StringRequest request = new StringRequest(Request.Method.GET, url,
                response -> {
                    ApiConfig.logRestCall(url, true, "Doctor requests fetched");
                    try {
                        JSONObject json = new JSONObject(response);
                        JSONArray requestsArray = json.optJSONArray("incomingDoctorRequests");
                        if (requestsArray != null) {
                            for (int i = 0; i < requestsArray.length(); i++) {
                                JSONObject req = requestsArray.getJSONObject(i);
                                Map<String, String> item = new HashMap<>();
                                item.put("email", req.optString("email", ""));
                                item.put("name", req.optString("name", req.optString("email", "")));
                                item.put("status", req.optString("status", "pending"));
                                doctorRequests.add(item);
                            }
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing doctor requests", e);
                    }
                    onApiCallComplete();
                },
                error -> {
                    ApiConfig.logRestCall(url, false, error.toString());
                    Log.e(TAG, "Error fetching doctor requests", error);
                    onApiCallComplete();
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + token);
                return headers;
            }
        };

        Volley.newRequestQueue(requireContext()).add(request);
    }

    private void fetchFamilyRequests() {
        String token = TokenManager.getInstance(requireContext()).getToken();
        if (token == null) { onApiCallComplete(); return; }

        String url = ApiConfig.BASE_URL + "/api/users/relationship/requests";

        StringRequest request = new StringRequest(Request.Method.GET, url,
                response -> {
                    ApiConfig.logRestCall(url, true, "Family requests fetched");
                    try {
                        JSONObject json = new JSONObject(response);
                        JSONArray requestsArray = json.optJSONArray("incomingRequests");
                        if (requestsArray != null) {
                            for (int i = 0; i < requestsArray.length(); i++) {
                                JSONObject req = requestsArray.getJSONObject(i);
                                String status = req.optString("status", "pending");
                                if (!"pending".equals(status)) continue;
                                Map<String, String> item = new HashMap<>();
                                item.put("email", req.optString("email", ""));
                                item.put("name", req.optString("name", req.optString("email", "")));
                                item.put("relationship", req.optString("relationship", ""));
                                item.put("status", status);
                                familyRequests.add(item);
                            }
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing family requests", e);
                    }
                    onApiCallComplete();
                },
                error -> {
                    ApiConfig.logRestCall(url, false, error.toString());
                    Log.e(TAG, "Error fetching family requests", error);
                    onApiCallComplete();
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + token);
                return headers;
            }
        };

        Volley.newRequestQueue(requireContext()).add(request);
    }

    private void buildListItems() {
        listItems.clear();

        // Doctor requests section
        listItems.add(new ListItem(ListItem.TYPE_HEADER, "Doctor Requests", doctorRequests.size(), true));
        if (doctorRequests.isEmpty()) {
            listItems.add(new ListItem(ListItem.TYPE_EMPTY, "No doctor requests", 0, true));
        } else {
            for (Map<String, String> req : doctorRequests) {
                listItems.add(new ListItem(ListItem.TYPE_REQUEST, req, true));
            }
        }

        // Family requests section
        listItems.add(new ListItem(ListItem.TYPE_HEADER, "Family Requests", familyRequests.size(), false));
        if (familyRequests.isEmpty()) {
            listItems.add(new ListItem(ListItem.TYPE_EMPTY, "No family requests", 0, false));
        } else {
            for (Map<String, String> req : familyRequests) {
                listItems.add(new ListItem(ListItem.TYPE_REQUEST, req, false));
            }
        }
    }

    private void respondToRequest(String email, boolean accept, boolean isDoctor, int adapterPosition) {
        String token = TokenManager.getInstance(requireContext()).getToken();
        if (token == null) return;

        String url;
        if (isDoctor) {
            url = ApiConfig.BASE_URL + "/api/users/doctor/respond";
        } else {
            // Check if it's a doctor relationship type
            ListItem item = listItems.get(adapterPosition);
            String relationship = item.data != null ? item.data.get("relationship") : "";
            if ("Doctor".equalsIgnoreCase(relationship)) {
                url = ApiConfig.BASE_URL + "/api/users/doctor/respond";
            } else {
                url = ApiConfig.BASE_URL + "/api/users/relationship/respond";
            }
        }

        JSONObject body = new JSONObject();
        try {
            body.put("email", email);
            body.put("accept", accept);
        } catch (JSONException e) {
            return;
        }

        // Show loading for respond action
        String actionLabel = accept ? "Accepting" : "Declining";
        if (getView() != null) {
            SimpleProgress.show(getView(), actionLabel + " request...");
        }

        String finalUrl = url;
        StringRequest request = new StringRequest(Request.Method.POST, url,
                response -> {
                    SimpleProgress.hide();
                    ApiConfig.logRestCall(finalUrl, true, "Request responded");
                    String action = accept ? "accepted" : "declined";
                    Utilities.toast(requireContext(), "Request " + action);

                    // Remove the item from data and rebuild
                    if (isDoctor) {
                        doctorRequests.removeIf(r -> email.equals(r.get("email")));
                    } else {
                        familyRequests.removeIf(r -> email.equals(r.get("email")));
                    }
                    buildListItems();
                    adapter.notifyDataSetChanged();

                    // Check if all empty
                    if (doctorRequests.isEmpty() && familyRequests.isEmpty()) {
                        emptyState.setVisibility(View.VISIBLE);
                        requestsRecycler.setVisibility(View.GONE);
                    }
                },
                error -> {
                    SimpleProgress.hide();
                    ApiConfig.logRestCall(finalUrl, false, error.toString());
                    String errorMessage = "Failed to respond";
                    if (error.networkResponse != null && error.networkResponse.data != null) {
                        try {
                            String errorData = new String(error.networkResponse.data, StandardCharsets.UTF_8);
                            try {
                                JSONObject errorJson = new JSONObject(errorData);
                                if (errorJson.has("message")) {
                                    errorMessage = errorJson.getString("message");
                                }
                            } catch (JSONException ignored) {
                                if (!errorData.isEmpty()) errorMessage = errorData;
                            }
                        } catch (Exception ignored) {}
                    }
                    Utilities.toast(requireContext(), errorMessage);
                }
        ) {
            @Override
            public String getBodyContentType() {
                return "application/json; charset=utf-8";
            }

            @Override
            public byte[] getBody() {
                return body.toString().getBytes(StandardCharsets.UTF_8);
            }

            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + token);
                return headers;
            }
        };

        Volley.newRequestQueue(requireContext()).add(request);
    }

    // ── Data model for multi-view-type list ──

    static class ListItem {
        static final int TYPE_HEADER = 0;
        static final int TYPE_REQUEST = 1;
        static final int TYPE_EMPTY = 2;

        int type;
        String text;
        int count;
        boolean isDoctor;
        Map<String, String> data;

        // Header / Empty constructor
        ListItem(int type, String text, int count, boolean isDoctor) {
            this.type = type;
            this.text = text;
            this.count = count;
            this.isDoctor = isDoctor;
        }

        // Request constructor
        ListItem(int type, Map<String, String> data, boolean isDoctor) {
            this.type = type;
            this.data = data;
            this.isDoctor = isDoctor;
        }
    }

    // ── Multi-view-type adapter ──

    class RequestListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        @Override
        public int getItemViewType(int position) {
            return listItems.get(position).type;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            switch (viewType) {
                case ListItem.TYPE_HEADER:
                    return new HeaderViewHolder(inflater.inflate(R.layout.item_section_header, parent, false));
                case ListItem.TYPE_REQUEST:
                    return new RequestViewHolder(inflater.inflate(R.layout.item_request_card, parent, false));
                case ListItem.TYPE_EMPTY:
                default:
                    return new EmptyViewHolder(inflater.inflate(android.R.layout.simple_list_item_1, parent, false));
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            ListItem item = listItems.get(position);

            if (holder instanceof HeaderViewHolder) {
                HeaderViewHolder h = (HeaderViewHolder) holder;
                h.title.setText(item.text);
                h.count.setText(String.valueOf(item.count));
                h.icon.setImageResource(item.isDoctor ? R.drawable.ic_medical : R.drawable.ic_person);
            } else if (holder instanceof RequestViewHolder) {
                RequestViewHolder h = (RequestViewHolder) holder;
                Map<String, String> data = item.data;

                String name = data.get("name");
                if (name == null || name.isEmpty()) name = data.get("email");
                h.name.setText(name);

                if (item.isDoctor) {
                    h.description.setText("Wants to connect as your Doctor");
                    h.icon.setImageResource(R.drawable.ic_medical);
                } else {
                    String rel = data.get("relationship");
                    h.description.setText(rel != null && !rel.isEmpty()
                            ? "Wants to connect as your " + rel
                            : "Wants to connect with you");
                    h.icon.setImageResource(R.drawable.ic_person);
                }

                String email = data.get("email");
                boolean isDoctor = item.isDoctor;
                int pos = position;

                h.acceptButton.setOnClickListener(v -> respondToRequest(email, true, isDoctor, pos));
                h.rejectButton.setOnClickListener(v -> respondToRequest(email, false, isDoctor, pos));

            } else if (holder instanceof EmptyViewHolder) {
                EmptyViewHolder h = (EmptyViewHolder) holder;
                h.text.setText(item.text);
                h.text.setTextColor(0xFF666666);
                h.text.setTextSize(13);
                h.text.setPadding(56, 8, 16, 16);
                h.itemView.setBackgroundColor(0x00000000);
            }
        }

        @Override
        public int getItemCount() {
            return listItems.size();
        }
    }

    // ── ViewHolders ──

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        ImageView icon;
        TextView title, count;

        HeaderViewHolder(View v) {
            super(v);
            icon = v.findViewById(R.id.section_icon);
            title = v.findViewById(R.id.section_title);
            count = v.findViewById(R.id.section_count);
        }
    }

    static class RequestViewHolder extends RecyclerView.ViewHolder {
        ImageView icon;
        TextView name, description;
        MaterialButton acceptButton, rejectButton;

        RequestViewHolder(View v) {
            super(v);
            icon = v.findViewById(R.id.request_icon);
            name = v.findViewById(R.id.request_name);
            description = v.findViewById(R.id.request_description);
            acceptButton = v.findViewById(R.id.accept_button);
            rejectButton = v.findViewById(R.id.reject_button);
        }
    }

    static class EmptyViewHolder extends RecyclerView.ViewHolder {
        TextView text;

        EmptyViewHolder(View v) {
            super(v);
            text = v.findViewById(android.R.id.text1);
        }
    }
}

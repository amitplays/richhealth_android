package com.example.richhealth.Activities;
import Utils.Utilities;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.example.richhealth.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import Models.Doctor;
import Utils.ApiConfig;
import Utils.SimpleProgress;

public class PendingRequestsFragment extends Fragment {

    private static final String TAG = "PendingRequestsFragment";

    private RecyclerView pendingRequestsRecycler;
    private LinearLayout emptyState;
    private Button findDoctorButton;

    private List<Doctor> pendingRequests = new ArrayList<>();
    private PendingRequestAdapter requestAdapter;
    private TokenManager tokenManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_pending_requests, container, false);

        tokenManager = TokenManager.getInstance(requireContext());

        // Initialize views
        pendingRequestsRecycler = view.findViewById(R.id.pending_requests_recycler);
        emptyState = view.findViewById(R.id.empty_state);
        findDoctorButton = view.findViewById(R.id.find_doctor_button);

        // Setup RecyclerView
        setupRecyclerView();

        // Setup find doctor button
        findDoctorButton.setOnClickListener(v -> {
            if (getActivity() instanceof DoctorSearchActivity) {
                ((DoctorSearchActivity) getActivity()).getViewPager().setCurrentItem(0);
            }
        });

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        // Load pending requests whenever the fragment becomes visible
        loadPendingRequests();
    }

    // Pending Request adapter for RecyclerView
    private static class PendingRequestAdapter extends RecyclerView.Adapter<PendingRequestAdapter.RequestViewHolder> {

        private List<Doctor> doctors;
        private OnCancelRequestListener listener;

        public interface OnCancelRequestListener {
            void onCancelRequest(Doctor doctor);
        }

        public PendingRequestAdapter(List<Doctor> doctors, OnCancelRequestListener listener) {
            this.doctors = doctors;
            this.listener = listener;
        }

        @NonNull
        @Override
        public RequestViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_doctor, parent, false);
            return new RequestViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RequestViewHolder holder, int position) {
            Doctor doctor = doctors.get(position);

            holder.doctorName.setText(doctor.getName());
            holder.doctorSpecialty.setText(doctor.getSpecialty());
            holder.doctorEmail.setText(doctor.getEmail());

            // For pending requests, show cancel button
            holder.actionButton.setText("Cancel Request");
            holder.actionButton.setOnClickListener(v -> listener.onCancelRequest(doctor));
        }

        @Override
        public int getItemCount() {
            return doctors.size();
        }

        static class RequestViewHolder extends RecyclerView.ViewHolder {
            TextView doctorName;
            TextView doctorSpecialty;
            TextView doctorEmail;
            Button actionButton;

            public RequestViewHolder(@NonNull View itemView) {
                super(itemView);
                doctorName = itemView.findViewById(R.id.doctor_name);
                doctorSpecialty = itemView.findViewById(R.id.doctor_specialty);
                doctorEmail = itemView.findViewById(R.id.doctor_email);
                actionButton = itemView.findViewById(R.id.action_button);
            }
        }
    }

    // Function to setup RecyclerView
    private void setupRecyclerView() {
        requestAdapter = new PendingRequestAdapter(pendingRequests, this::cancelRequest);
        pendingRequestsRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        pendingRequestsRecycler.setAdapter(requestAdapter);
    }

    // Function to load pending requests
    private void loadPendingRequests() {
        SimpleProgress progress = SimpleProgress.show(requireActivity(), "Loading...");

        String token = tokenManager.getToken();

        if (token == null) {
            progress.hide();
            Utilities.toast(requireContext(), "Authentication error");
            return;
        }

        String url = ApiConfig.BASE_URL + "/api/user/doctor/pending";

        StringRequest request = new StringRequest(Request.Method.GET, url,
                response -> {
                    ApiConfig.logRestCall(url, true, "Pending requests fetched");
                    progress.hide();
                    try {
                        JSONArray requestsArray = new JSONArray(response);

                        // Clear previous results
                        pendingRequests.clear();

                        // Parse pending requests from response
                        for (int i = 0; i < requestsArray.length(); i++) {
                            JSONObject requestObj = requestsArray.getJSONObject(i);
                            JSONObject doctorObj = requestObj.getJSONObject("doctor");

                            Doctor doctor = new Doctor();
                            doctor.setId(doctorObj.getString("_id"));
                            doctor.setName(doctorObj.getString("name"));
                            doctor.setEmail(doctorObj.getString("email"));
                            doctor.setSpecialty(doctorObj.optString("specialty", "General Practitioner"));
                            doctor.setConnectionStatus("pending");

                            pendingRequests.add(doctor);
                        }

                        // Update UI
                        updateUI();

                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing pending requests response", e);
                        Utilities.toast(requireContext(), "Error loading pending requests");
                    }
                },
                error -> {
                    ApiConfig.logRestCall(url, false, error.toString());
                    progress.hide();
                    Utilities.toast(requireContext(), "Failed to load pending requests");
                    Log.e(TAG, "Error loading pending requests: " + error.toString());
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + token);
                return headers;
            }
        };

        RequestQueue queue = Volley.newRequestQueue(requireContext());
        queue.add(request);
    }

    // Function to update UI based on pending requests
    private void updateUI() {
        if (pendingRequests.isEmpty()) {
            pendingRequestsRecycler.setVisibility(View.GONE);
            emptyState.setVisibility(View.VISIBLE);
        } else {
            pendingRequestsRecycler.setVisibility(View.VISIBLE);
            emptyState.setVisibility(View.GONE);
            requestAdapter.notifyDataSetChanged();
        }
    }

    // Function to cancel a connection request
    private void cancelRequest(Doctor doctor) {
        SimpleProgress progress = SimpleProgress.show(requireActivity(), "Canceling request...");

        String token = tokenManager.getToken();

        if (token == null) {
            progress.hide();
            Utilities.toast(requireContext(), "Authentication error");
            return;
        }

        String url = ApiConfig.BASE_URL + "/api/user/doctor/cancel";

        JSONObject requestBody = new JSONObject();
        try {
            requestBody.put("doctorId", doctor.getId());
        } catch (JSONException e) {
            progress.hide();
            Log.e(TAG, "Error creating request body", e);
            return;
        }

        StringRequest request = new StringRequest(Request.Method.POST, url,
                response -> {
                    ApiConfig.logRestCall(url, true, "Request canceled");
                    progress.hide();
                    Utilities.toast(requireContext(), "Request canceled successfully");

                    // Remove the canceled request from the list
                    pendingRequests.remove(doctor);
                    updateUI();
                },
                error -> {
                    ApiConfig.logRestCall(url, false, error.toString());
                    progress.hide();
                    String errorMessage = "Failed to cancel request";

                    // Handle different types of error responses
                    if (error.networkResponse != null && error.networkResponse.data != null) {
                        try {
                            String errorData = new String(error.networkResponse.data, StandardCharsets.UTF_8);

                            // Try to parse as JSON first
                            try {
                                JSONObject errorJson = new JSONObject(errorData);
                                if (errorJson.has("message")) {
                                    errorMessage = errorJson.getString("message");
                                }
                            } catch (JSONException e) {
                                // If not JSON, use the raw string response
                                if (!errorData.isEmpty()) {
                                    errorMessage = errorData;
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing error response", e);
                        }
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
                return requestBody.toString().getBytes(StandardCharsets.UTF_8);
            }

            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + token);
                return headers;
            }
        };

        RequestQueue queue = Volley.newRequestQueue(requireContext());
        queue.add(request);
    }
}
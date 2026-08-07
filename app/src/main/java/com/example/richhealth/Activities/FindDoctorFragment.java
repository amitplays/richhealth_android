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
import com.google.android.material.textfield.TextInputEditText;

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

public class FindDoctorFragment extends Fragment {

    private static final String TAG = "FindDoctorFragment";

    private TextInputEditText searchInput;
    private Button searchButton;
    private RecyclerView searchResultsRecycler;
    private TextView searchResultsTitle;
    private LinearLayout emptyState;

    private List<Doctor> searchResults = new ArrayList<>();
    private DoctorAdapter doctorAdapter;
    private TokenManager tokenManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_find_doctor, container, false);

        tokenManager = TokenManager.getInstance(requireContext());

        // Initialize views
        searchInput = view.findViewById(R.id.doctor_search_input);
        searchButton = view.findViewById(R.id.search_button);
        searchResultsRecycler = view.findViewById(R.id.search_results_recycler);
        searchResultsTitle = view.findViewById(R.id.search_results_title);
        emptyState = view.findViewById(R.id.empty_state);

        // Setup RecyclerView
        setupRecyclerView();

        // Setup search button
        searchButton.setOnClickListener(v -> {
            String query = searchInput.getText().toString().trim();
            if (!query.isEmpty()) {
                SimpleProgress progress = SimpleProgress.show(requireActivity(), "Searching...");
                searchDoctors(query, progress);
            } else {
                Utilities.toast(requireContext(), "Please enter a search term");
            }
        });

        return view;
    }

    // Doctor adapter for RecyclerView
    private static class DoctorAdapter extends RecyclerView.Adapter<DoctorAdapter.DoctorViewHolder> {

        private List<Doctor> doctors;
        private OnDoctorClickListener listener;

        public interface OnDoctorClickListener {
            void onDoctorClick(Doctor doctor);
        }

        public DoctorAdapter(List<Doctor> doctors, OnDoctorClickListener listener) {
            this.doctors = doctors;
            this.listener = listener;
        }

        @NonNull
        @Override
        public DoctorViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_doctor, parent, false);
            return new DoctorViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull DoctorViewHolder holder, int position) {
            Doctor doctor = doctors.get(position);

            holder.doctorName.setText(doctor.getName());
            holder.doctorSpecialty.setText(doctor.getSpecialty());
            holder.doctorEmail.setText(doctor.getEmail());

            // Set button text based on connection status
            Button actionButton = holder.actionButton;
            switch (doctor.getConnectionStatus()) {
                case "connected":
                    actionButton.setText("Connected");
                    actionButton.setEnabled(false);
                    break;
                case "pending":
                    actionButton.setText("Request Pending");
                    actionButton.setEnabled(false);
                    break;
                default:
                    actionButton.setText("Send Request");
                    actionButton.setEnabled(true);
                    actionButton.setOnClickListener(v -> listener.onDoctorClick(doctor));
                    break;
            }
        }

        @Override
        public int getItemCount() {
            return doctors.size();
        }

        static class DoctorViewHolder extends RecyclerView.ViewHolder {
            TextView doctorName;
            TextView doctorSpecialty;
            TextView doctorEmail;
            Button actionButton;

            public DoctorViewHolder(@NonNull View itemView) {
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
        doctorAdapter = new DoctorAdapter(searchResults, doctor -> {
            // Show doctor request dialog when clicked
            if (getActivity() instanceof DoctorSearchActivity) {
                ((DoctorSearchActivity) getActivity()).showDoctorRequestDialog(doctor);
            }
        });

        searchResultsRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        searchResultsRecycler.setAdapter(doctorAdapter);
    }

    // Function to search for doctors
    private void searchDoctors(String query, SimpleProgress progress) {
        String token = tokenManager.getToken();

        if (token == null) {
            progress.hide();
            Utilities.toast(requireContext(), "Authentication error");
            return;
        }

        String url = ApiConfig.BASE_URL + "/api/user/doctor/search?query=" + query;

        StringRequest request = new StringRequest(Request.Method.GET, url,
                response -> {
                    ApiConfig.logRestCall(url, true, "Doctor search completed");
                    progress.hide();
                    try {
                        JSONArray doctorsArray = new JSONArray(response);

                        // Clear previous results
                        searchResults.clear();

                        // Parse doctors from response
                        for (int i = 0; i < doctorsArray.length(); i++) {
                            JSONObject doctorObj = doctorsArray.getJSONObject(i);

                            Doctor doctor = new Doctor();
                            doctor.setId(doctorObj.getString("_id"));
                            doctor.setName(doctorObj.getString("name"));
                            doctor.setEmail(doctorObj.getString("email"));
                            doctor.setSpecialty(doctorObj.optString("specialty", "General Practitioner"));
                            doctor.setConnectionStatus(doctorObj.optString("connectionStatus", "none"));

                            searchResults.add(doctor);
                        }

                        // Update UI
                        updateUI();

                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing doctor search response", e);
                        Utilities.toast(requireContext(), "Error loading doctors");
                    }
                },
                error -> {
                    ApiConfig.logRestCall(url, false, error.toString());
                    progress.hide();
                    String errorMessage = "Failed to search doctors";

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
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + token);
                return headers;
            }
        };

        RequestQueue queue = Volley.newRequestQueue(requireContext());
        queue.add(request);
    }

    // Function to update UI based on search results
    private void updateUI() {
        if (searchResults.isEmpty()) {
            searchResultsTitle.setVisibility(View.GONE);
            searchResultsRecycler.setVisibility(View.GONE);
            emptyState.setVisibility(View.VISIBLE);
        } else {
            searchResultsTitle.setVisibility(View.VISIBLE);
            searchResultsRecycler.setVisibility(View.VISIBLE);
            emptyState.setVisibility(View.GONE);
            doctorAdapter.notifyDataSetChanged();
        }
    }
}
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import Models.Doctor;
import Utils.ApiConfig;
import Utils.SimpleProgress;

public class ConnectedDoctorsFragment extends Fragment {

    private static final String TAG = "ConnectedDoctorsFragment";

    private RecyclerView connectedDoctorsRecycler;
    private LinearLayout emptyState;
    private Button findDoctorButton;

    private List<Doctor> connectedDoctors = new ArrayList<>();
    private ConnectedDoctorAdapter doctorAdapter;
    private TokenManager tokenManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_connected_doctors, container, false);

        tokenManager = TokenManager.getInstance(requireContext());

        // Initialize views
        connectedDoctorsRecycler = view.findViewById(R.id.connected_doctors_recycler);
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

        // Load connected doctors whenever the fragment becomes visible
        loadConnectedDoctors();
    }

    // Connected Doctor adapter for RecyclerView
    private static class ConnectedDoctorAdapter extends RecyclerView.Adapter<ConnectedDoctorAdapter.DoctorViewHolder> {

        private List<Doctor> doctors;

        public ConnectedDoctorAdapter(List<Doctor> doctors) {
            this.doctors = doctors;
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

            // For connected doctors, show different button
            holder.actionButton.setText("Message");
            holder.actionButton.setOnClickListener(v -> {
                // In a real app, this would open messaging with this doctor
                Utilities.toast(v.getContext(), "Messaging will be implemented in a future update");
            });
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
        doctorAdapter = new ConnectedDoctorAdapter(connectedDoctors);
        connectedDoctorsRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        connectedDoctorsRecycler.setAdapter(doctorAdapter);
    }

    // Function to load connected doctors
    private void loadConnectedDoctors() {
        SimpleProgress progress = SimpleProgress.show(requireActivity(), "Loading...");

        String token = tokenManager.getToken();

        if (token == null) {
            progress.hide();
            Utilities.toast(requireContext(), "Authentication error");
            return;
        }

        String url = ApiConfig.BASE_URL + "/api/user/doctor/connected";

        StringRequest request = new StringRequest(Request.Method.GET, url,
                response -> {
                    ApiConfig.logRestCall(url, true, "Connected doctors fetched");
                    progress.hide();
                    try {
                        JSONArray doctorsArray = new JSONArray(response);

                        // Clear previous results
                        connectedDoctors.clear();

                        // Parse doctors from response
                        for (int i = 0; i < doctorsArray.length(); i++) {
                            JSONObject doctorObj = doctorsArray.getJSONObject(i);

                            Doctor doctor = new Doctor();
                            doctor.setId(doctorObj.getString("_id"));
                            doctor.setName(doctorObj.getString("name"));
                            doctor.setEmail(doctorObj.getString("email"));
                            doctor.setSpecialty(doctorObj.optString("specialty", "General Practitioner"));
                            doctor.setConnectionStatus("connected");

                            connectedDoctors.add(doctor);
                        }

                        // Update UI
                        updateUI();

                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing connected doctors response", e);
                        Utilities.toast(requireContext(), "Error loading doctors");
                    }
                },
                error -> {
                    ApiConfig.logRestCall(url, false, error.toString());
                    progress.hide();
                    Utilities.toast(requireContext(), "Failed to load connected doctors");
                    Log.e(TAG, "Error loading connected doctors: " + error.toString());
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

    // Function to update UI based on connected doctors
    private void updateUI() {
        if (connectedDoctors.isEmpty()) {
            connectedDoctorsRecycler.setVisibility(View.GONE);
            emptyState.setVisibility(View.VISIBLE);
        } else {
            connectedDoctorsRecycler.setVisibility(View.VISIBLE);
            emptyState.setVisibility(View.GONE);
            doctorAdapter.notifyDataSetChanged();
        }
    }
}
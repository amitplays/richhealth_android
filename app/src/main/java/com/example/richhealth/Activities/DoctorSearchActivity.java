package com.example.richhealth.Activities;
import Utils.Utilities;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.example.richhealth.R;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import Models.Doctor;
import Utils.ApiConfig;
import Utils.SimpleProgress;

public class DoctorSearchActivity extends AppCompatActivity {

    private static final String TAG = "DoctorSearchActivity";
    private TabLayout tabLayout;
    private ViewPager2 viewPager;
    private String[] tabTitles = {"Find Doctor", "Connected Doctors", "Pending Requests"};
    private TokenManager tokenManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_doctor_search);

        tokenManager = TokenManager.getInstance(this);

        // Initialize views
        tabLayout = findViewById(R.id.doctor_tabs);
        viewPager = findViewById(R.id.doctor_viewpager);
        ImageButton backButton = findViewById(R.id.back_button);

        // Setup back button
        backButton.setOnClickListener(v -> finish());

        // Setup ViewPager with fragments
        setupViewPager();

        // Connect TabLayout with ViewPager
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) ->
                tab.setText(tabTitles[position])
        ).attach();
    }

    // Add getter for viewPager - this resolves the error
    public ViewPager2 getViewPager() {
        return viewPager;
    }

    private void setupViewPager() {
        DoctorPagerAdapter adapter = new DoctorPagerAdapter(this);
        viewPager.setAdapter(adapter);
    }

    // ViewPager adapter for doctor fragments
    private class DoctorPagerAdapter extends FragmentStateAdapter {

        public DoctorPagerAdapter(FragmentActivity fragmentActivity) {
            super(fragmentActivity);
        }

        @Override
        public int getItemCount() {
            return tabTitles.length;
        }

        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0:
                    return new FindDoctorFragment();
                case 1:
                    return new ConnectedDoctorsFragment();
                case 2:
                    return new PendingRequestsFragment();
                default:
                    return new FindDoctorFragment();
            }
        }
    }

    public void showDoctorRequestDialog(Doctor doctor) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_doctor_request);

        // Set dialog width to match most of the screen width
        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
        layoutParams.copyFrom(dialog.getWindow().getAttributes());
        layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
        layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
        dialog.getWindow().setAttributes(layoutParams);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        // Initialize dialog views
        TextView doctorNameText = dialog.findViewById(R.id.doctor_name_text);
        TextView doctorSpecialtyText = dialog.findViewById(R.id.doctor_specialty_text);
        TextInputEditText messageInput = dialog.findViewById(R.id.message_input);
        Button cancelButton = dialog.findViewById(R.id.cancel_button);
        Button sendButton = dialog.findViewById(R.id.send_button);

        // Set doctor information
        doctorNameText.setText(doctor.getName());
        doctorSpecialtyText.setText(doctor.getSpecialty());

        // Setup buttons
        cancelButton.setOnClickListener(v -> dialog.dismiss());

        sendButton.setOnClickListener(v -> {
            String message = messageInput.getText().toString().trim();
            SimpleProgress progress = SimpleProgress.show(this, "Sending request...");
            sendDoctorConnectionRequest(doctor, message, progress, dialog);
        });

        dialog.show();
    }

    // Function to send doctor connection request
    private void sendDoctorConnectionRequest(Doctor doctor, String message, SimpleProgress progress, Dialog dialog) {
        String token = tokenManager.getToken();

        if (token == null) {
            progress.hide();
            Utilities.toast(this, "Authentication error");
            return;
        }

        String url = ApiConfig.BASE_URL + "/api/user/doctor/request";

        JSONObject requestBody = new JSONObject();
        try {
            requestBody.put("doctorId", doctor.getId());
            requestBody.put("message", message);
        } catch (JSONException e) {
            Log.e(TAG, "Error creating request body", e);
            progress.hide();
            return;
        }

        StringRequest request = new StringRequest(Request.Method.POST, url,
                response -> {
                    ApiConfig.logRestCall(url, true, "Doctor connection request sent");
                    progress.hide();
                    dialog.dismiss();
                    Utilities.toast(this, "Connection request sent successfully");

                    // Refresh the pending requests tab
                    viewPager.setCurrentItem(2); // Switch to pending requests tab
                },
                error -> {
                    ApiConfig.logRestCall(url, false, error.toString());
                    progress.hide();
                    String errorMessage = "Failed to send request";

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

                    Utilities.toast(DoctorSearchActivity.this, errorMessage);
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

        RequestQueue queue = Volley.newRequestQueue(this);
        queue.add(request);
    }
}
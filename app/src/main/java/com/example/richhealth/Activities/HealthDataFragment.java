package com.example.richhealth.Activities;
import Utils.Utilities;

import Utils.ApiConfig;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.widget.CompoundButton;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.ViewTreeObserver;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.example.richhealth.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import Models.MedicationModel;

import Utils.PaymentManager;
import Utils.PaymentService;
import Utils.ProStatusManager;
import Utils.ProStatusResult;
import Utils.ProUpgradeDialog;
import Utils.SimpleProgress;
import Utils.UploadedFile;
import Models.MedicalData;
import Models.UserProfile;
import Adapters.MedicalDataAdapter;
import Adapters.UploadedFilesAdapter;
import Api.MedicalDataApiService;
import Api.MedicalReportApiService;
import Database.DatabaseHelper;

public class HealthDataFragment extends Fragment implements BackPressHandler {

    private static final String TAG = "HealthDataFragment";
    private static final int REPORT_FILE_PICKER_REQUEST_CODE = 1001;

    private RecyclerView sentRequestsRecyclerView;

    // UI components
    // Whole cards are the tap targets now (Material clickable card) — not "+" buttons.
    private View medicalReportsButton;
    private View medicationsButton;
    private View addRelativeButton;
    // Plan pill (replaces notification button)
    private LinearLayout planPill;
    private TextView planPillText;
    private ImageView planPillIcon;
    private HealthDataFragment.RelationshipAdapter relationshipAdapter;

    // Side Panels
    private Dialog medicalDataPanel;
    private Dialog medicalReportsPanel;
    private Dialog medicationsPanel;
    private Dialog familyMembersPanel;

    // Medical reports components
    private List<UploadedFile> reportUploadedFiles;
    private UploadedFilesAdapter reportFilesAdapter;
    private Map<String, JSONObject> remoteReports = new HashMap<>();
    private boolean userCanAnalyze = false;
    private MedicalReportApiService apiService;

    // Modern Activity Result API for the report file picker. The legacy
    // startActivityForResult / onActivityResult path is deprecated and silently
    // drops results when the parent Activity doesn't forward them — which is
    // exactly what was happening here (logcat showed nothing after picking a
    // PDF). registerForActivityResult is lifecycle-aware and works regardless
    // of fragment nesting / parent activity overrides.
    private androidx.activity.result.ActivityResultLauncher<Intent> reportFilePickerLauncher;

    // Medical data components
    private MedicalDataAdapter medicalDataAdapter;
    private List<UserProfile.RelationshipRequest> familyRelationships = new ArrayList<>();


    // Database helper
    private DatabaseHelper dbHelper;
    private ProStatusManager proStatusManager;
    private MedicalDataApiService medicalDataApiService;
    private ProUpgradeDialog proUpgradeDialog;

    // User profile
    private UserProfile userProfile;

    // Variables for date and time selection
    private Calendar selectedDateTime;
    private SimpleDateFormat dateFormatter;
    private SimpleDateFormat timeFormatter;
    private View rootView;


    // Update these variables in the class
    private Dialog symptomsPanel;
    private Dialog measurementsPanel;
    private Dialog periodLogsPanel;
    private MedicalDataAdapter symptomsAdapter;
    private MedicalDataAdapter measurementsAdapter;

    // Panel search fields (chat-history-style search header) + reports master list for filtering.
    private android.widget.EditText symptomsSearchInput;
    private android.widget.EditText measurementsSearchInput;
    private android.widget.EditText reportsSearchInput;
    private android.widget.EditText medicationsSearchInput;
    private android.widget.EditText periodLogsSearchInput;
    private final List<UploadedFile> allReports = new ArrayList<>();
    // Master (unfiltered) medications; `medications` below is the filtered view the adapter shows.
    private final List<MedicationModel> allMedications = new ArrayList<>();
    private MedicalDataAdapter periodLogsAdapter;
    private MedicationsAdapter medicationsAdapter;

    private View symptomsButton;
    private View measurementsButton;
    private View periodHistoryButton;
    private MaterialCardView periodHistoryCard;

    private List<MedicationModel> medications = new ArrayList<>();
    private RecyclerView medicationsRecycler;


    /**
     * BackPressHandler — called by MainActivity before its own back-press logic.
     * Checks every side panel in priority order and dismisses the first one found open.
     * Returns true (consumed) so MainActivity does NOT also navigate away.
     */
    @Override
    public boolean handleBackPress() {
        if (symptomsPanel != null && symptomsPanel.isShowing()) {
            symptomsPanel.dismiss();
            return true;
        }
        if (measurementsPanel != null && measurementsPanel.isShowing()) {
            measurementsPanel.dismiss();
            return true;
        }
        if (periodLogsPanel != null && periodLogsPanel.isShowing()) {
            periodLogsPanel.dismiss();
            return true;
        }
        if (medicationsPanel != null && medicationsPanel.isShowing()) {
            medicationsPanel.dismiss();
            return true;
        }
        if (medicalReportsPanel != null && medicalReportsPanel.isShowing()) {
            medicalReportsPanel.dismiss();
            return true;
        }
        if (medicalDataPanel != null && medicalDataPanel.isShowing()) {
            medicalDataPanel.dismiss();
            return true;
        }
        if (familyMembersPanel != null && familyMembersPanel.isShowing()) {
            familyMembersPanel.dismiss();
            return true;
        }
        return false;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Register the file picker launcher BEFORE the fragment reaches STARTED.
        // This must happen in onCreate or earlier — that's a hard requirement
        // of the Activity Result API.
        reportFilePickerLauncher = registerForActivityResult(
                new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
                result -> {
                    Log.d(TAG, "Report picker result: code=" + result.getResultCode());
                    if (result.getResultCode() != android.app.Activity.RESULT_OK) {
                        return;
                    }
                    Intent data = result.getData();
                    if (data == null || data.getData() == null) {
                        Log.w(TAG, "Report picker returned OK but no data");
                        Utilities.toast(requireContext(), "No file selected.");
                        return;
                    }
                    Uri uri = data.getData();
                    Log.d(TAG, "Report picker uri=" + uri);
                    // Persist read permission so we can still read the URI after
                    // the picker process is gone.
                    try {
                        requireContext().getContentResolver().takePersistableUriPermission(
                                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (SecurityException ignored) {
                        // Some pickers (Intent.ACTION_GET_CONTENT) don't grant
                        // persistable permission — that's fine, we read it now.
                    }
                    processReportFile(uri);
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_health_data, container, false);
        rootView = inflater.inflate(R.layout.layout_family_members_panel, container, false);

        // Initialize date and time formatters and selectedDateTime
        dateFormatter = new SimpleDateFormat("MM/dd/yyyy", Locale.US);
        timeFormatter = new SimpleDateFormat("h:mm a", Locale.US);
        selectedDateTime = Calendar.getInstance();

        initViews(view);
        setupListeners();
        setupPlanPill();
        setupCardInfoDialogs(view);
        loadUserProfile();
        setupFamilyMedicalRecordsSection(rootView);
        setupPanels();
        fetchMedicalDataStats(view);
        animateCardsEntry(view);
        return view;
    }

    private void fetchMedicalDataStats(View view) {
        Context context = getContext();
        if (context == null) return;

        TokenManager tokenManager = TokenManager.getInstance(context);
        String token = tokenManager.getToken();
        if (token == null) return;

        String url = ApiConfig.BASE_URL + "/api/medical-data/stats";

        StringRequest request = new StringRequest(Request.Method.GET, url,
                response -> {
                    ApiConfig.logRestCall(url, true, "Medical data stats fetched");
                    try {
                        JSONObject jsonResponse = new JSONObject(response);
                        int symptomsCount = 0;
                        int measurementsCount = 0;

                        if (jsonResponse.has("symptoms")) {
                            JSONObject symptoms = jsonResponse.getJSONObject("symptoms");
                            symptomsCount = symptoms.optInt("count", 0);
                        }
                        if (jsonResponse.has("measurements")) {
                            JSONObject measurements = jsonResponse.getJSONObject("measurements");
                            measurementsCount = measurements.optInt("count", 0);
                        }

                        TextView statsText = view.findViewById(R.id.medical_data_stats_text);
                        if (statsText != null && (symptomsCount > 0 || measurementsCount > 0)) {
                            statsText.setText(symptomsCount + " symptoms · " + measurementsCount + " measurements tracked");
                            statsText.setVisibility(View.VISIBLE);
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing medical data stats", e);
                    }
                },
                error -> {
                    ApiConfig.logRestCall(url, false, error.toString());
                    Log.e(TAG, "Error fetching medical data stats: " + error.toString());
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + token);
                return headers;
            }
        };

        Volley.newRequestQueue(context).add(request);
    }

    private void loadUserProfile() {
        userProfile = dbHelper.getUserProfile();

        // If no profile exists, use empty profile
        if (userProfile == null) {
            userProfile = new UserProfile();
            userProfile.setName("User");
        }

        // Show period history card only for female users (or users with active menstrual status)
        if (periodHistoryCard != null && userProfile != null) {
            String gender = userProfile.getGender();
            String menstrualStatus = userProfile.getMenstrualStatus();
            boolean showPeriod = "Female".equalsIgnoreCase(gender)
                    || (menstrualStatus != null
                        && !"not_applicable".equals(menstrualStatus)
                        && !"prefer_not_to_say".equals(menstrualStatus));
            periodHistoryCard.setVisibility(showPeriod ? View.VISIBLE : View.GONE);
        }
    }

    private void setupFamilyMedicalRecordsSection(View rootView) {
        // Find the views
        sentRequestsRecyclerView = rootView.findViewById(R.id.family_relationships_recycler);

        // Setup RecyclerView for family relationships
        relationshipAdapter = new HealthDataFragment.RelationshipAdapter(familyRelationships);
        sentRequestsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        sentRequestsRecyclerView.setAdapter(relationshipAdapter);

        // Setup add dependent button
        MaterialButton addDependentButton = rootView.findViewById(R.id.add_dependent_button);
        if (addDependentButton != null) {
            addDependentButton.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), AddDependentActivity.class);
                startActivity(intent);
            });
        }

        // Setup connect family member button
        MaterialButton addFamilyMemberButton = rootView.findViewById(R.id.add_family_member_button);
        addFamilyMemberButton.setOnClickListener(v -> {
            showAddFamilyMemberDialog();
        });
    }

    private void initViews(View view) {
        // Whole cards are the tap targets (Material clickable card).
        symptomsButton = view.findViewById(R.id.symptoms_card);
        measurementsButton = view.findViewById(R.id.measurements_card);
        medicalReportsButton = view.findViewById(R.id.medical_reports_card);
        medicationsButton = view.findViewById(R.id.medications_card);
        addRelativeButton = view.findViewById(R.id.add_relative_card);
        // [PLAN-PILL-REVIEW] disabled (backend-driven, keep after review): planPill = view.findViewById(R.id.plan_pill);
        // planPillText = view.findViewById(R.id.plan_pill_text);
        // planPillIcon = view.findViewById(R.id.plan_pill_icon);

        periodHistoryButton = view.findViewById(R.id.period_history_card);
        periodHistoryCard = view.findViewById(R.id.period_history_card);

        // Initialize services and helpers
        dbHelper = new DatabaseHelper(requireContext());
        proStatusManager = ProStatusManager.getInstance(requireContext());
        medicalDataApiService = new MedicalDataApiService(requireContext());
        apiService = new MedicalReportApiService(requireContext());
    }

    private void setupListeners() {
        // Set up click listeners for symptoms button
        symptomsButton.setOnClickListener(v -> {
            SimpleProgress progress = SimpleProgress.show(requireActivity(), "Fetching your symptoms data...");

            // Load local symptoms data first
            loadSymptoms();

            // Then fetch from server
            if (userProfile != null) {
                medicalDataApiService.getAllMedicalData(new MedicalDataApiService.OnFetchDataListener() {
                    @Override
                    public void onSuccess(List<JSONObject> dataList) {
                        syncMedicalDataFromServer(dataList);
                        loadSymptoms(); // Refresh with synced data

                        progress.hide();
                        symptomsPanel.show(); // Show dialog only after everything loads
                    }

                    @Override
                    public void onError(String errorMessage) {
                        progress.hide();
                        Utilities.toast(requireContext(), "Couldn't fetch symptoms. Please check your connection.");
                    }
                });
            } else {
                progress.hide();
                symptomsPanel.show();
            }
        });

        // Set up click listeners for measurements button
        measurementsButton.setOnClickListener(v -> {
            SimpleProgress progress = SimpleProgress.show(requireActivity(), "Fetching your measurements data...");

            // Load local measurements data first
            loadMeasurements();

            // Then fetch from server
            if (userProfile != null) {
                medicalDataApiService.getAllMedicalData(new MedicalDataApiService.OnFetchDataListener() {
                    @Override
                    public void onSuccess(List<JSONObject> dataList) {
                        syncMedicalDataFromServer(dataList);
                        loadMeasurements(); // Refresh with synced data

                        progress.hide();
                        measurementsPanel.show(); // Show dialog only after everything loads
                    }

                    @Override
                    public void onError(String errorMessage) {
                        progress.hide();
                        Utilities.toast(requireContext(), "Couldn't fetch measurements. Please check your connection.");
                    }
                });
            } else {
                progress.hide();
                measurementsPanel.show();
            }
        });

        // Period history button
        periodHistoryButton.setOnClickListener(v -> {
            SimpleProgress progress = SimpleProgress.show(requireActivity(), "Fetching your period history...");

            loadPeriodLogs();

            if (userProfile != null) {
                medicalDataApiService.getPeriodLogs(new MedicalDataApiService.OnFetchDataListener() {
                    @Override
                    public void onSuccess(List<JSONObject> dataList) {
                        syncPeriodLogsFromServer(dataList);
                        loadPeriodLogs();
                        progress.hide();
                        periodLogsPanel.show();
                    }

                    @Override
                    public void onError(String errorMessage) {
                        progress.hide();
                        periodLogsPanel.show();
                    }
                });
            } else {
                progress.hide();
                periodLogsPanel.show();
            }
        });

        // Replace these with the new panel methods
        medicalReportsButton.setOnClickListener(v -> showMedicalReportsPanel());
        medicationsButton.setOnClickListener(v -> showMedicationsPanel());
        addRelativeButton.setOnClickListener(v -> showFamilyMembersPanel());

        // Add subtle press-pulse animations to all + buttons.
        attachAddButtonPulse(symptomsButton);
        attachAddButtonPulse(measurementsButton);
        attachAddButtonPulse(medicalReportsButton);
        attachAddButtonPulse(medicationsButton);
        attachAddButtonPulse(addRelativeButton);
        attachAddButtonPulse(periodHistoryButton);
    }

    private void syncMedicalDataFromServer(List<JSONObject> dataList) {
        for (JSONObject dataObj : dataList) {
            try {
                String type = dataObj.getString("type");
                String serverId = dataObj.getString("_id");

                // Check if this data already exists locally
                boolean exists = false;
                List<MedicalData> localData = dbHelper.getMedicalDataForUser(userProfile.getId());
                for (MedicalData localItem : localData) {
                    if (serverId.equals(localItem.getServerId())) {
                        exists = true;
                        break;
                    }
                }

                // If not exists locally, add it
                if (!exists) {
                    if (MedicalData.TYPE_SYMPTOM.equals(type)) {
                        MedicalData.Symptom symptom = parseSymptomFromJson(dataObj);
                        if (symptom != null) {
                            dbHelper.insertSymptom(symptom);
                        }
                    } else if (MedicalData.TYPE_MEASUREMENT.equals(type)) {
                        MedicalData.HealthMetric metric = parseMetricFromJson(dataObj);
                        if (metric != null) {
                            dbHelper.insertHealthMetric(metric);
                        }
                    }
                }
            } catch (JSONException e) {
                Log.e(TAG, "Error parsing medical data: " + e.getMessage());
            }
        }
    }

    // Replace the setupMedicalDataPanel method with these two methods
    private void setupSymptomsPanel() {
        symptomsPanel = new Dialog(requireContext());
        symptomsPanel.requestWindowFeature(Window.FEATURE_NO_TITLE);
        symptomsPanel.setContentView(R.layout.layout_symptoms_panel);

        symptomsPanel.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        WindowManager.LayoutParams params = symptomsPanel.getWindow().getAttributes();
        params.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.85);
        params.height = WindowManager.LayoutParams.MATCH_PARENT;
        params.gravity = Gravity.END;

        symptomsPanel.getWindow().setAttributes(params);
        symptomsPanel.getWindow().getAttributes().windowAnimations = R.style.DialogAnimationSlideRight;

        // Close button
        symptomsPanel.findViewById(R.id.close_panel_button).setOnClickListener(v -> symptomsPanel.dismiss());

        // Setup RecyclerView
        RecyclerView symptomsRecycler = symptomsPanel.findViewById(R.id.symptoms_recycler);
        symptomsAdapter = new MedicalDataAdapter(requireContext());

        symptomsAdapter.setActionListener(new MedicalDataAdapter.OnMedicalDataActionListener() {
            @Override
            public void onEditItem(MedicalData data) {
                if (data instanceof MedicalData.Symptom) {
                    showEditSymptomDialog((MedicalData.Symptom) data);
                }
            }

            @Override
            public void onDeleteItem(MedicalData data, int position) {
                showDeleteConfirmDialog(data, position);
            }
        });

        symptomsRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        symptomsRecycler.setAdapter(symptomsAdapter);

        // Search (chat-history-style header)
        symptomsSearchInput = symptomsPanel.findViewById(R.id.search_input);
        if (symptomsSearchInput != null) {
            symptomsSearchInput.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) { applySymptomFilter(); }
                @Override public void afterTextChanged(android.text.Editable s) {}
            });
        }

        // Setup Add Symptom Button
        MaterialButton addSymptomButton = symptomsPanel.findViewById(R.id.add_symptom_button);
        addSymptomButton.setOnClickListener(v -> {
            showAddSymptomDialog();
        });

        // Load symptoms initially
        loadSymptoms();
    }


    private void setupMeasurementsPanel() {
        measurementsPanel = new Dialog(requireContext());
        measurementsPanel.requestWindowFeature(Window.FEATURE_NO_TITLE);
        measurementsPanel.setContentView(R.layout.layout_measurements_panel);

        measurementsPanel.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        WindowManager.LayoutParams params = measurementsPanel.getWindow().getAttributes();
        params.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.85);
        params.height = WindowManager.LayoutParams.MATCH_PARENT;
        params.gravity = Gravity.END;

        measurementsPanel.getWindow().setAttributes(params);
        measurementsPanel.getWindow().getAttributes().windowAnimations = R.style.DialogAnimationSlideRight;

        // Close button
        measurementsPanel.findViewById(R.id.close_panel_button).setOnClickListener(v -> measurementsPanel.dismiss());

        // Setup RecyclerView
        RecyclerView measurementsRecycler = measurementsPanel.findViewById(R.id.measurements_recycler);
        measurementsAdapter = new MedicalDataAdapter(requireContext());

        measurementsAdapter.setActionListener(new MedicalDataAdapter.OnMedicalDataActionListener() {
            @Override
            public void onEditItem(MedicalData data) {
                if (data instanceof MedicalData.HealthMetric) {
                    showEditMeasurementDialog((MedicalData.HealthMetric) data);
                }
            }

            @Override
            public void onDeleteItem(MedicalData data, int position) {
                showDeleteConfirmDialog(data, position);
            }
        });


        measurementsRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        measurementsRecycler.setAdapter(measurementsAdapter);

        // Search (chat-history-style header)
        measurementsSearchInput = measurementsPanel.findViewById(R.id.search_input);
        if (measurementsSearchInput != null) {
            measurementsSearchInput.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) { applyMeasurementFilter(); }
                @Override public void afterTextChanged(android.text.Editable s) {}
            });
        }

        // Setup Add Measurement Button
        MaterialButton addMeasurementButton = measurementsPanel.findViewById(R.id.add_measurement_button);
        addMeasurementButton.setOnClickListener(v -> {
            showAddMeasurementDialog();
        });

        // Load measurements initially
        loadMeasurements();
    }


    private MedicalData.Symptom parseSymptomFromJson(JSONObject json) {
        try {
            MedicalData.Symptom symptom = new MedicalData.Symptom();
            symptom.setServerId(json.getString("_id"));
            symptom.setName(json.getString("title"));
            symptom.setSeverity(json.getInt("severity"));
            symptom.setDuration(json.getString("duration"));

            if (json.has("description")) {
                symptom.setDescription(json.getString("description"));
            }

            if (json.has("shareWithFamily")) {
                symptom.setShareWithFamily(json.getBoolean("shareWithFamily"));
            }
            if (json.has("includeInChat")) {
                symptom.setIncludeInChat(json.getBoolean("includeInChat"));
            }

            if (json.has("dateTime")) {
                String dateTimeStr = json.getString("dateTime");
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault());
                sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                symptom.setRecordedAt(sdf.parse(dateTimeStr));
            } else {
                symptom.setRecordedAt(new Date());
            }

            if (userProfile != null) {
                symptom.setUserId(userProfile.getId());
            }

            return symptom;
        } catch (Exception e) {
            Log.e(TAG, "Error parsing symptom JSON: " + e.getMessage());
            return null;
        }
    }


    private void setupPanels() {
        setupSymptomsPanel();
        setupMeasurementsPanel();
        setupPeriodLogsPanel();
        setupMedicalReportsPanel();
        setupMedicationsPanel();
        setupFamilyMembersPanel();
    }

    private void setupPeriodLogsPanel() {
        periodLogsPanel = new Dialog(requireContext());
        periodLogsPanel.requestWindowFeature(Window.FEATURE_NO_TITLE);
        periodLogsPanel.setContentView(R.layout.layout_period_logs_panel);

        periodLogsPanel.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        WindowManager.LayoutParams params = periodLogsPanel.getWindow().getAttributes();
        params.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.85);
        params.height = WindowManager.LayoutParams.MATCH_PARENT;
        params.gravity = Gravity.END;

        periodLogsPanel.getWindow().setAttributes(params);
        periodLogsPanel.getWindow().getAttributes().windowAnimations = R.style.DialogAnimationSlideRight;

        // Close button
        periodLogsPanel.findViewById(R.id.close_panel_button).setOnClickListener(v -> periodLogsPanel.dismiss());

        // Setup RecyclerView
        RecyclerView periodLogsRecycler = periodLogsPanel.findViewById(R.id.period_logs_recycler);
        periodLogsAdapter = new MedicalDataAdapter(requireContext());

        periodLogsAdapter.setActionListener(new MedicalDataAdapter.OnMedicalDataActionListener() {
            @Override
            public void onEditItem(MedicalData data) {
                if (data instanceof MedicalData.PeriodLog) {
                    showEditPeriodLogDialog((MedicalData.PeriodLog) data);
                }
            }

            @Override
            public void onDeleteItem(MedicalData data, int position) {
                showDeletePeriodLogConfirmDialog(data, position);
            }
        });

        periodLogsRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        periodLogsRecycler.setAdapter(periodLogsAdapter);

        // Search (chat-history-style header) — same pattern as symptoms/measurements.
        periodLogsSearchInput = periodLogsPanel.findViewById(R.id.search_input);
        if (periodLogsSearchInput != null) {
            periodLogsSearchInput.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) { applyPeriodLogFilter(); }
                @Override public void afterTextChanged(android.text.Editable s) {}
            });
        }

        // Setup Add Period Log Button
        MaterialButton addPeriodLogButton = periodLogsPanel.findViewById(R.id.add_period_log_button);
        addPeriodLogButton.setOnClickListener(v -> {
            showAddPeriodLogDialog();
        });

        // Load period logs initially
        loadPeriodLogs();
    }

    // Medical Reports Panel - Using AIFragment implementation exactly
    private void setupMedicalReportsPanel() {
        // Create the dialog
        medicalReportsPanel = new Dialog(requireContext());
        medicalReportsPanel.requestWindowFeature(Window.FEATURE_NO_TITLE);
        medicalReportsPanel.setContentView(R.layout.layout_medical_reports_panel);

        medicalReportsPanel.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        WindowManager.LayoutParams params = medicalReportsPanel.getWindow().getAttributes();
        params.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.85);
        params.height = WindowManager.LayoutParams.MATCH_PARENT;
        params.gravity = Gravity.END;

        medicalReportsPanel.getWindow().setAttributes(params);
        medicalReportsPanel.getWindow().getAttributes().windowAnimations = R.style.DialogAnimationSlideRight;

        // Close button
        medicalReportsPanel.findViewById(R.id.close_panel_button).setOnClickListener(v -> medicalReportsPanel.dismiss());

        // Initialize views (header is now a search field — no title/subtitle)
        com.google.android.material.button.MaterialButton addReportButton = medicalReportsPanel.findViewById(R.id.add_medical_report_button);
        RecyclerView reportsRecyclerView = medicalReportsPanel.findViewById(R.id.reports_recycler);

        // Initialize uploaded files list
        reportUploadedFiles = new ArrayList<>();
        reportFilesAdapter = new UploadedFilesAdapter(reportUploadedFiles, this::deleteReport);

        // Setup analyzer listener
        reportFilesAdapter.setAnalyzeClickListener(this::handleAnalyzeClick);

        // Setup RecyclerView
        reportsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        reportsRecyclerView.setAdapter(reportFilesAdapter);

        // Search (chat-history-style header)
        reportsSearchInput = medicalReportsPanel.findViewById(R.id.search_input);
        if (reportsSearchInput != null) {
            reportsSearchInput.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) { applyReportFilter(); }
                @Override public void afterTextChanged(android.text.Editable s) {}
            });
        }

        // Trends: plot a single test's numeric values across all uploaded reports.
        com.google.android.material.button.MaterialButton viewTrendsButton =
                medicalReportsPanel.findViewById(R.id.view_trends_button);
        if (viewTrendsButton != null) {
            viewTrendsButton.setOnClickListener(v ->
                    Utils.DialogUtils.showReportTrendChartDialog(requireContext(), reportUploadedFiles));
        }

        // Initialize API service if it doesn't exist
        if (apiService == null) {
            apiService = new MedicalReportApiService(requireContext());
        }

        // Show empty state initially
        updateReportsEmptyState();

        // All users can upload reports — analysis is gated by tier
        addReportButton.setOnClickListener(v -> openReportFilePicker());
    }

    // Medications Panel (this is new functionality)
    // 2. Replace your setupMedicationsPanel method with this
    private void setupMedicationsPanel() {
        // Create panel dialog
        medicationsPanel = new Dialog(requireContext());
        medicationsPanel.requestWindowFeature(Window.FEATURE_NO_TITLE);
        medicationsPanel.setContentView(R.layout.layout_medications_panel);

        // Set transparent background
        medicationsPanel.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        // Set panel dimensions
        WindowManager.LayoutParams params = medicationsPanel.getWindow().getAttributes();
        params.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.85);
        params.height = WindowManager.LayoutParams.MATCH_PARENT;
        params.gravity = Gravity.END;

        // Apply params and animation
        medicationsPanel.getWindow().setAttributes(params);
        medicationsPanel.getWindow().getAttributes().windowAnimations = R.style.DialogAnimationSlideRight;

        // Close button
        medicationsPanel.findViewById(R.id.close_panel_button).setOnClickListener(v -> medicationsPanel.dismiss());

        // Setup Add Medication button
        MaterialButton addMedicationButton = medicationsPanel.findViewById(R.id.add_medication_button);
        addMedicationButton.setOnClickListener(v -> {
            showAddMedicationDialog();
        });

        // Setup RecyclerView for medications
        medicationsRecycler = medicationsPanel.findViewById(R.id.medications_recycler);
        medicationsRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));

        // Search (chat-history-style header) — same pattern as symptoms/measurements.
        medicationsSearchInput = medicationsPanel.findViewById(R.id.search_input);
        if (medicationsSearchInput != null) {
            medicationsSearchInput.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) { applyMedicationFilter(); }
                @Override public void afterTextChanged(android.text.Editable s) {}
            });
        }
    }

    // (Removed fetchMedicationStats: the stats line was replaced by the search
    //  header's "Search in N medications" count when the panel was redesigned.)

    // Family Members Panel
    private void setupFamilyMembersPanel() {
        // Create panel dialog
        familyMembersPanel = new Dialog(requireContext());
        familyMembersPanel.requestWindowFeature(Window.FEATURE_NO_TITLE);
        familyMembersPanel.setContentView(R.layout.layout_family_members_panel);

        // Set transparent background
        familyMembersPanel.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        // Set panel dimensions
        WindowManager.LayoutParams params = familyMembersPanel.getWindow().getAttributes();
        params.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.85);
        params.height = WindowManager.LayoutParams.MATCH_PARENT;
        params.gravity = Gravity.END;

        // Apply params and animation
        familyMembersPanel.getWindow().setAttributes(params);
        familyMembersPanel.getWindow().getAttributes().windowAnimations = R.style.DialogAnimationSlideRight;

        // Close button
        familyMembersPanel.findViewById(R.id.close_panel_button).setOnClickListener(v -> familyMembersPanel.dismiss());

        // Setup RecyclerView for family relationships
        sentRequestsRecyclerView = familyMembersPanel.findViewById(R.id.family_relationships_recycler);
        relationshipAdapter = new RelationshipAdapter(familyRelationships);
        sentRequestsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        sentRequestsRecyclerView.setAdapter(relationshipAdapter);

        // Setup Add Dependent button
        MaterialButton addDependentButton = familyMembersPanel.findViewById(R.id.add_dependent_button);
        addDependentButton.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), AddDependentActivity.class);
            startActivity(intent);
        });

        // Setup Connect Family Member button
        MaterialButton addFamilyMemberButton = familyMembersPanel.findViewById(R.id.add_family_member_button);
        addFamilyMemberButton.setOnClickListener(v -> {
            showAddFamilyMemberDialog();
        });


        // Show empty state initially, will be updated when data loads
        updateFamilyMembersEmptyState();
    }

    private void updateFamilyMembersEmptyState() {
        View emptyState = familyMembersPanel.findViewById(R.id.empty_state);
        if (emptyState != null) {
            boolean isEmpty = familyRelationships == null || familyRelationships.isEmpty();
            emptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            Log.d(TAG, "Family relationships empty state: " + (isEmpty ? "VISIBLE" : "GONE"));
        }
    }

    private void updateFamilyProCounter(int count, int max, boolean isFamilyPlanOwner) {
        TextView counter = familyMembersPanel.findViewById(R.id.family_pro_counter);
        if (counter != null) {
            if (isFamilyPlanOwner) {
                counter.setVisibility(View.VISIBLE);
                counter.setText(count + "/" + max + " covered");
            } else {
                counter.setVisibility(View.GONE);
            }
        }
    }

    private void fetchDependentUsers(String token, Runnable onComplete) {
        Context context = getContext();
        if (context == null) {
            onComplete.run();
            return;
        }
        String url = ApiConfig.BASE_URL + "/api/dependents/users";
        StringRequest request = new StringRequest(Request.Method.GET, url,
                response -> {
                    try {
                        JSONObject json = new JSONObject(response);
                        JSONArray dependents = json.getJSONArray("dependents");
                        int maxDependents = json.optInt("maxDependents", 0);
                        int depCount = json.optInt("count", dependents.length());

                        for (int i = 0; i < dependents.length(); i++) {
                            JSONObject dep = dependents.getJSONObject(i);
                            UserProfile.RelationshipRequest depItem = new UserProfile.RelationshipRequest(
                                    "", // no email for dependents
                                    dep.optString("dependentType", "dependent"),
                                    "dependent"
                            );
                            depItem.setName(dep.optString("name", "Dependent"));
                            depItem.setUserId(dep.optString("_id", ""));
                            familyRelationships.add(depItem);
                        }

                        // Update dependent counter in panel header
                        updateDependentCounter(depCount, maxDependents);
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing dependent users", e);
                    }
                    onComplete.run();
                },
                error -> {
                    Log.e(TAG, "Error fetching dependent users", error);
                    onComplete.run();
                }
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + token);
                return headers;
            }
        };
        Volley.newRequestQueue(context).add(request);
    }

    private void updateDependentCounter(int count, int max) {
        if (familyMembersPanel == null) return;
        TextView counter = familyMembersPanel.findViewById(R.id.dependent_counter);
        if (counter != null) {
            counter.setVisibility(View.VISIBLE);
            counter.setText(count + "/" + max + " dependents");
        }
    }

    private void showMedicalReportsPanel() {
        SimpleProgress progress = SimpleProgress.show(requireActivity(), "Fetching your medical reports securely...");

        // Fetch reports first, then show the panel when data is loaded
        apiService.getUserReports(new MedicalReportApiService.OnReportsFetchListener() {
            @Override
            public void onSuccess(List<JSONObject> reports) {
                remoteReports.clear();
                allReports.clear();

                for (JSONObject report : reports) {
                    try {
                        String reportId = report.getString("_id");
                        String fileName = report.getString("fileName");
                        String reportType = report.getString("reportType");
                        String status = report.getString("status");
                        String fileUrl = report.getString("fileUrl");

                        // Track user-level analysis capability from API response
                        userCanAnalyze = report.optBoolean("_canAnalyze", false);

                        remoteReports.put(reportId, report);

                        UploadedFile uploadedFile = new UploadedFile(requireContext(), null, fileName);
                        uploadedFile.setReportType(reportType);
                        uploadedFile.setReportId(reportId);
                        uploadedFile.setStatus(status);
                        uploadedFile.setFileUrl(fileUrl);

                        if (report.has("shareWithFamily")) {
                            uploadedFile.setShareWithFamily(report.getBoolean("shareWithFamily"));
                        }
                        if (report.has("includeInChat")) {
                            uploadedFile.setIncludeInChat(report.getBoolean("includeInChat"));
                        }

                        // Mark whether this specific report can be analyzed
                        boolean canAnalyzeThis = report.optBoolean("canAnalyzeThisReport", false);
                        uploadedFile.setCanAnalyze(canAnalyzeThis);

                        // Populate analysis fields (riskLevel, opinion, key findings, etc.)
                        applyAnalysisToFile(uploadedFile, report);

                        allReports.add(uploadedFile);
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing report", e);
                    }
                }

                applyReportFilter();

                // Hide progress and show panel only after data is loaded
                progress.hide();
                medicalReportsPanel.show();
            }

            @Override
            public void onError(String error) {
                progress.hide();
                Log.e(TAG,"Error : "+error);
                Utilities.toast(requireContext(), "Failed to load reports: " + error);

                updateReportsEmptyState();
                // Still show the panel with whatever data we have locally
                medicalReportsPanel.show();
            }
        });
    }

    private void showMedicationsPanel() {
        if (userProfile != null) {
            Context context = getContext();
            if (context == null) return; // Fragment detached, skip operation safely

            SimpleProgress progress = SimpleProgress.show(requireActivity(), "Fetching your medications securely...");

            // Fetch medications first, then show the panel when data is loaded
            TokenManager tokenManager = TokenManager.getInstance(context);
            String token = tokenManager.getToken();

            if (token == null) {
                progress.hide();
                Utilities.toast(context, "Authentication error");
                return;
            }

            String url = ApiConfig.BASE_URL + "/api/medications";

            JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
                    response -> {
                        try {
                            JSONArray medicationsArray = response.getJSONArray("medications");
                            allMedications.clear();

                            for (int i = 0; i < medicationsArray.length(); i++) {
                                JSONObject medicationObj = medicationsArray.getJSONObject(i);
                                MedicationModel medication = parseMedicationFromJson(medicationObj);
                                if (medication != null) {
                                    allMedications.add(medication);
                                }
                            }

                            applyMedicationFilter();
                            syncRemindersFromMedications(context);

                            // Hide progress and show panel only after data is loaded
                            progress.hide();
                            medicationsPanel.show();

                        } catch (JSONException e) {
                            progress.hide();
                            Log.e(TAG, "Error parsing medications response", e);
                            Utilities.toast(context, "Couldn't fetch medications. Please check your connection.");
                        }
                    },
                    error -> {
                        progress.hide();
                        Log.e(TAG, "Error fetching medications", error);
                        Utilities.toast(context, "Couldn't fetch medications. Please check your connection.");
                    }
            ) {
                @Override
                public Map<String, String> getHeaders() throws AuthFailureError {
                    Map<String, String> headers = new HashMap<>();
                    headers.put("Authorization", "Bearer " + token);
                    return headers;
                }
            };

            RequestQueue queue = Volley.newRequestQueue(context);
            queue.add(request);
        } else {
            // If no user profile, just show the panel with empty state
            medicationsPanel.show();
        }
    }

    private void loadMedications() {
        if (userProfile != null) {
            fetchMedicationsFromAPI();
        }
    }

    private void fetchMedicationsFromAPI() {
        Context context = getContext();
        if (context == null) return; // Fragment detached, skip operation safely

        TokenManager tokenManager = TokenManager.getInstance(context);
        String token = tokenManager.getToken();

        if (token == null) {
            Utilities.toast(context, "Authentication error");
            return;
        }

        SimpleProgress progress = medicationsPanel != null && medicationsPanel.isShowing()
                ? SimpleProgress.show(medicationsPanel, "Fetching your medications securely...")
                : SimpleProgress.show(requireActivity(), "Fetching your medications securely...");

        String url = ApiConfig.BASE_URL + "/api/medications";

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {
                    ApiConfig.logRestCall(url, true, "Medications fetched");
                    progress.hide();
                    try {
                        JSONArray medicationsArray = response.getJSONArray("medications");
                        allMedications.clear();

                        for (int i = 0; i < medicationsArray.length(); i++) {
                            JSONObject medicationObj = medicationsArray.getJSONObject(i);
                            MedicationModel medication = parseMedicationFromJson(medicationObj);
                            if (medication != null) {
                                allMedications.add(medication);
                            }
                        }

                        applyMedicationFilter();
                        syncRemindersFromMedications(context);

                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing medications response", e);
                        Utilities.toast(requireContext(), "Error loading medications");
                    }
                },
                error -> {
                    ApiConfig.logRestCall(url, false, error.toString());
                    progress.hide();
                    Log.e(TAG, "Error fetching medications", error);
                    Utilities.toast(requireContext(), "Failed to load medications");
                    updateMedicationsEmptyState();
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + token);
                return headers;
            }
        };

        RequestQueue queue = Volley.newRequestQueue(context);
        queue.add(request);
    }

    /**
     * Mirror the freshly-fetched medications into the local reminder store so reminders
     * survive reinstall / reboot and stay in sync with the server. Only called after a
     * successful fetch. {@link Utils.MedicationReminderHelper#setForMedication} upserts
     * enabled ones and drops disabled / discontinued ones.
     */
    private void syncRemindersFromMedications(Context context) {
        if (context == null) return;
        for (MedicationModel m : allMedications) {
            if (m == null || m.getServerId() == null) continue;
            boolean effectiveEnabled = m.isRemindersEnabled() && m.isActive();

            List<Integer> dayList = m.getReminderDays();
            int[] days = new int[dayList != null ? dayList.size() : 0];
            for (int i = 0; dayList != null && i < dayList.size(); i++) days[i] = dayList.get(i);

            List<int[]> timeList = m.getReminderTimes();
            int[][] times = new int[timeList != null ? timeList.size() : 0][];
            for (int i = 0; timeList != null && i < timeList.size(); i++) times[i] = timeList.get(i);

            Utils.MedicationReminderHelper.setForMedication(context, m.getServerId(),
                    m.getName(), m.getDosage(), effectiveEnabled, days, times);
        }
    }

    private MedicationModel parseMedicationFromJson(JSONObject json) {
        try {
            MedicationModel medication = new MedicationModel();

            // Set the MongoDB ID from the server
            if (json.has("_id") && !json.isNull("_id")) {
                medication.setServerId(json.getString("_id"));
            }

            medication.setName(json.getString("name"));
            medication.setDosage(json.getString("dosage"));
            medication.setFrequency(json.getString("frequency"));
            medication.setActive(json.optBoolean("isOngoing", true)); // Use isOngoing from backend

            if (json.has("notes") && !json.isNull("notes")) {
                medication.setNotes(json.getString("notes"));
            }

            // Parse dates
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault());
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

            if (json.has("startDate") && !json.isNull("startDate")) {
                try {
                    Date startDate = sdf.parse(json.getString("startDate"));
                    medication.setStartDate(startDate);
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing start date", e);
                }
            }

            if (json.has("endDate") && !json.isNull("endDate")) {
                try {
                    Date endDate = sdf.parse(json.getString("endDate"));
                    medication.setEndDate(endDate);
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing end date", e);
                }
            }

            if (json.has("shareWithFamily")) {
                medication.setShareWithFamily(json.getBoolean("shareWithFamily"));
            }
            if (json.has("includeInChat")) {
                medication.setIncludeInChat(json.getBoolean("includeInChat"));
            }

            // Parse new fields
            if (json.has("purpose") && !json.isNull("purpose")) {
                medication.setPurpose(json.getString("purpose"));
            }
            if (json.has("prescribedBy") && !json.isNull("prescribedBy")) {
                medication.setPrescribedBy(json.getString("prescribedBy"));
            }
            if (json.has("medicationType") && !json.isNull("medicationType")) {
                medication.setMedicationType(json.getString("medicationType"));
            }
            if (json.has("administrationMethod") && !json.isNull("administrationMethod")) {
                medication.setAdministrationMethod(json.getString("administrationMethod"));
            }
            if (json.has("adherenceRate") && !json.isNull("adherenceRate")) {
                medication.setAdherenceRate(json.getDouble("adherenceRate"));
            }

            // Reminder fields (mirror backend remindersEnabled / reminderDays / reminderTimes)
            medication.setRemindersEnabled(json.optBoolean("remindersEnabled", false));

            java.util.List<Integer> reminderDays = new ArrayList<>();
            JSONArray daysArr = json.optJSONArray("reminderDays");
            if (daysArr != null) {
                for (int d = 0; d < daysArr.length(); d++) {
                    reminderDays.add(daysArr.optInt(d, -1));
                }
            }
            medication.setReminderDays(reminderDays);

            java.util.List<int[]> reminderTimes = new ArrayList<>();
            JSONArray timesArr = json.optJSONArray("reminderTimes");
            if (timesArr != null) {
                for (int t = 0; t < timesArr.length(); t++) {
                    JSONObject to = timesArr.optJSONObject(t);
                    if (to == null) continue;
                    reminderTimes.add(new int[]{to.optInt("hour", 8), to.optInt("minute", 0)});
                }
            }
            medication.setReminderTimes(reminderTimes);

            return medication;
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing medication JSON", e);
            return null;
        }
    }

    // 3. Replace your updateMedicationsUI method with this
    private void updateMedicationsUI() {
        // Create a simple adapter similar to your other patterns
        medicationsAdapter = new MedicationsAdapter(medications);
        medicationsAdapter.setActionListener(new OnMedicationActionListener() {
            @Override
            public void onEditItem(MedicationModel medication) {
                showEditMedicationDialog(medication);
            }

            @Override
            public void onDeleteItem(MedicationModel medication, int position) {
                showDeleteMedicationConfirmDialog(medication, position);
            }
        });
        medicationsRecycler.setAdapter(medicationsAdapter);

        updateMedicationsEmptyState();
    }

    /**
     * Filters the medications master list by the search query into the displayed
     * `medications` list, then refreshes the adapter, empty state and search hint.
     * Mirrors applySymptomFilter / applyMeasurementFilter so all panels behave the same.
     */
    private void applyMedicationFilter() {
        String q = (medicationsSearchInput != null && medicationsSearchInput.getText() != null)
                ? medicationsSearchInput.getText().toString().trim().toLowerCase() : "";
        medications.clear();
        for (MedicationModel m : allMedications) {
            if (m == null) continue;
            if (q.isEmpty() || medicationMatches(m, q)) medications.add(m);
        }
        // Rebuild the adapter against the filtered list (keeps positions consistent
        // for edit/delete, which index into `medications`).
        updateMedicationsUI();
        if (medicationsSearchInput != null) {
            int total = allMedications.size();
            medicationsSearchInput.setHint("Search in " + total + " medication" + (total == 1 ? "" : "s"));
        }
    }

    /** Match a medication against the query across name, dosage, purpose and type. */
    private boolean medicationMatches(MedicationModel m, String q) {
        if (contains(m.getName(), q)) return true;
        if (contains(m.getDosage(), q)) return true;
        if (contains(m.getPurpose(), q)) return true;
        if (contains(m.getMedicationType(), q)) return true;
        return contains(m.getFrequency(), q);
    }

    private boolean contains(String s, String q) {
        return s != null && s.toLowerCase().contains(q);
    }

    private void updateMedicationsEmptyState() {
        View emptyState = medicationsPanel.findViewById(R.id.empty_state);
        if (emptyState != null) {
            emptyState.setVisibility(medications.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private void showFamilyMembersPanel() {
        if (userProfile != null) {
            Context context = getContext();
            if (context == null) return; // Fragment detached, skip operation safely

            SimpleProgress progress = SimpleProgress.show(requireActivity(), "Fetching your family connections securely...");

            // Fetch relationships first, then show the panel when data is loaded
            TokenManager tokenManager = TokenManager.getInstance(context);
            String token = tokenManager.getToken();

            if (token == null) {
                progress.hide();
                Utilities.toast(context, "Authentication error. Please sign in again.");
                return;
            }

            String url = ApiConfig.BASE_URL + "/api/users/relationships";

            StringRequest request = new StringRequest(Request.Method.GET, url,
                    response -> {
                        ApiConfig.logRestCall(url, true, "Family relationships fetched (panel)");
                        try {
                            JSONObject jsonResponse = new JSONObject(response);
                            JSONArray relationshipsArray = jsonResponse.getJSONArray("relationships");
                            Log.d(TAG, "fetchFamilyRelationships() -> Data fetch success with " + relationshipsArray.length() + " relationships");

                            familyRelationships.clear();

                            boolean isFamilyPlanOwner = jsonResponse.optBoolean("isFamilyPlanOwner", false);
                            int familyProMemberCount = jsonResponse.optInt("familyProMemberCount", 0);
                            int maxFamilyMembers = jsonResponse.optInt("maxFamilyMembers", 5);

                            for (int i = 0; i < relationshipsArray.length(); i++) {
                                JSONObject relationshipObj = relationshipsArray.getJSONObject(i);

                                UserProfile.RelationshipRequest relationship = new UserProfile.RelationshipRequest(
                                        relationshipObj.getString("email"),
                                        relationshipObj.getString("relationship"),
                                        relationshipObj.getString("status")
                                );

                                if (relationshipObj.has("name") && !relationshipObj.isNull("name")) {
                                    relationship.setName(relationshipObj.getString("name"));
                                }
                                if (relationshipObj.has("userId") && !relationshipObj.isNull("userId")) {
                                    relationship.setUserId(relationshipObj.getString("userId"));
                                }
                                relationship.setPro(relationshipObj.optBoolean("isPro", false));
                                relationship.setProSource(relationshipObj.optString("proSource", "none"));
                                relationship.setCoveredByMyPlan(relationshipObj.optBoolean("isCoveredByMyPlan", false));

                                Log.d(TAG, "Adding relationship: " + relationship.getEmail());
                                familyRelationships.add(relationship);
                            }

                            relationshipAdapter.setFamilyPlanOwner(isFamilyPlanOwner);

                            // Also fetch dependent-as-user records and add to list
                            fetchDependentUsers(token, () -> {
                                relationshipAdapter.notifyDataSetChanged();
                                updateFamilyMembersEmptyState();
                                updateFamilyProCounter(familyProMemberCount, maxFamilyMembers, isFamilyPlanOwner);
                                progress.hide();
                                familyMembersPanel.show();
                            });

                        } catch (JSONException e) {
                            progress.hide();
                            Log.e(TAG, "Error parsing family relationships response", e);
                            Utilities.toast(context, "Couldn't fetch family connections. Please check your connection.");
                        }
                    },
                    error -> {
                        ApiConfig.logRestCall(url, false, error.toString());
                        progress.hide();
                        Log.e(TAG, "Error fetching family relationships", error);
                        Utilities.toast(context, "Couldn't fetch family connections. Please check your connection.");
                    }
            ) {
                @Override
                public Map<String, String> getHeaders() throws AuthFailureError {
                    Map<String, String> headers = new HashMap<>();
                    headers.put("Authorization", "Bearer " + token);
                    return headers;
                }
            };

            RequestQueue queue = Volley.newRequestQueue(context);
            queue.add(request);
        } else {
            // If no user profile, just show the panel with empty state
            familyMembersPanel.show();
        }
    }

    // Update the loadSymptoms method
    private void loadSymptoms() {
        applySymptomFilter();
    }

    /** Filters symptoms (from the local DB) by the search query and refreshes the list + hint. */
    private void applySymptomFilter() {
        if (userProfile == null || symptomsAdapter == null) return;
        String q = (symptomsSearchInput != null && symptomsSearchInput.getText() != null)
                ? symptomsSearchInput.getText().toString().trim().toLowerCase() : "";
        List<MedicalData> all = dbHelper.getMedicalDataForUser(userProfile.getId());
        List<MedicalData> out = new ArrayList<>();
        int total = 0;
        for (MedicalData data : all) {
            if (data instanceof MedicalData.Symptom) {
                total++;
                if (q.isEmpty() || medicalDataMatches(data, q)) out.add(data);
            }
        }
        symptomsAdapter.setData(out);
        updateSymptomsEmptyState(out.isEmpty());
        if (symptomsSearchInput != null) {
            symptomsSearchInput.setHint("Search in " + total + " symptom" + (total == 1 ? "" : "s"));
        }
    }


    private void loadMeasurements() {
        applyMeasurementFilter();
    }

    /** Filters measurements (from the local DB) by the search query and refreshes the list + hint. */
    private void applyMeasurementFilter() {
        if (userProfile == null || measurementsAdapter == null) return;
        String q = (measurementsSearchInput != null && measurementsSearchInput.getText() != null)
                ? measurementsSearchInput.getText().toString().trim().toLowerCase() : "";
        List<MedicalData> all = dbHelper.getMedicalDataForUser(userProfile.getId());
        List<MedicalData> out = new ArrayList<>();
        int total = 0;
        for (MedicalData data : all) {
            if (data instanceof MedicalData.HealthMetric) {
                total++;
                if (q.isEmpty() || medicalDataMatches(data, q)) out.add(data);
            }
        }
        measurementsAdapter.setData(out);
        updateMeasurementsEmptyState(out.isEmpty());
        if (measurementsSearchInput != null) {
            measurementsSearchInput.setHint("Search in " + total + " measurement" + (total == 1 ? "" : "s"));
        }
    }

    /** Filters the already-fetched medical reports by the search query and refreshes list + hint. */
    private void applyReportFilter() {
        if (reportFilesAdapter == null) return;
        String q = (reportsSearchInput != null && reportsSearchInput.getText() != null)
                ? reportsSearchInput.getText().toString().trim().toLowerCase() : "";
        reportUploadedFiles.clear();
        for (UploadedFile f : allReports) {
            if (q.isEmpty() || reportMatches(f, q)) reportUploadedFiles.add(f);
        }
        reportFilesAdapter.notifyDataSetChanged();
        updateReportsEmptyState();
        if (reportsSearchInput != null) {
            int total = allReports.size();
            reportsSearchInput.setHint("Search in " + total + " report" + (total == 1 ? "" : "s"));
        }
    }

    private boolean medicalDataMatches(MedicalData d, String q) {
        StringBuilder sb = new StringBuilder();
        if (d instanceof MedicalData.Symptom) {
            MedicalData.Symptom s = (MedicalData.Symptom) d;
            sb.append(safeStr(s.getName())).append(' ')
              .append(safeStr(s.getDescription())).append(' ')
              .append(safeStr(s.getDuration()));
        } else if (d instanceof MedicalData.HealthMetric) {
            MedicalData.HealthMetric m = (MedicalData.HealthMetric) d;
            sb.append(safeStr(m.getMetricType())).append(' ')
              .append(safeStr(m.getFormattedValue())).append(' ')
              .append(safeStr(m.getNotes()));
        } else if (d instanceof MedicalData.PeriodLog) {
            MedicalData.PeriodLog p = (MedicalData.PeriodLog) d;
            sb.append(safeStr(p.getFlowIntensityLabel())).append(' ')
              .append(safeStr(p.getPainLevelText())).append(' ')
              .append(safeStr(p.getNotes()));
        }
        return sb.toString().toLowerCase().contains(q);
    }

    private boolean reportMatches(UploadedFile f, String q) {
        return (safeStr(f.getName()) + " " + safeStr(f.getReportType()))
                .toLowerCase().contains(q);
    }

    private String safeStr(String s) { return s == null ? "" : s; }

    // Add these two methods for empty state handling
    private void updateSymptomsEmptyState(boolean isEmpty) {
        View emptyState = symptomsPanel.findViewById(R.id.empty_state);
        RecyclerView recyclerView = symptomsPanel.findViewById(R.id.symptoms_recycler);

        if (isEmpty) {
            emptyState.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void updateMeasurementsEmptyState(boolean isEmpty) {
        View emptyState = measurementsPanel.findViewById(R.id.empty_state);
        RecyclerView recyclerView = measurementsPanel.findViewById(R.id.measurements_recycler);

        if (isEmpty) {
            emptyState.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void updateReportsEmptyState() {
        View emptyState = medicalReportsPanel.findViewById(R.id.empty_state);
        RecyclerView recyclerView = medicalReportsPanel.findViewById(R.id.reports_recycler);
        boolean isEmpty = reportUploadedFiles == null || reportUploadedFiles.isEmpty();

        if (isEmpty) {
            emptyState.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    // ─── Period Log Methods ────────────────────────────────────────────────────

    private void loadPeriodLogs() {
        applyPeriodLogFilter();
    }

    /** Filters period logs (from the local DB) by the search query and refreshes the list + hint. */
    private void applyPeriodLogFilter() {
        if (userProfile == null || periodLogsAdapter == null) return;
        String q = (periodLogsSearchInput != null && periodLogsSearchInput.getText() != null)
                ? periodLogsSearchInput.getText().toString().trim().toLowerCase() : "";
        List<MedicalData> allData = dbHelper.getMedicalDataForUser(userProfile.getId());
        List<MedicalData> out = new ArrayList<>();
        int total = 0;
        for (MedicalData data : allData) {
            if (data instanceof MedicalData.PeriodLog) {
                total++;
                if (q.isEmpty() || medicalDataMatches(data, q)) out.add(data);
            }
        }
        periodLogsAdapter.setData(out);
        updatePeriodLogsEmptyState(out.isEmpty());
        if (periodLogsSearchInput != null) {
            periodLogsSearchInput.setHint("Search in " + total + " period log" + (total == 1 ? "" : "s"));
        }
    }

    private void updatePeriodLogsEmptyState(boolean isEmpty) {
        View emptyState = periodLogsPanel.findViewById(R.id.empty_state);
        RecyclerView recyclerView = periodLogsPanel.findViewById(R.id.period_logs_recycler);

        if (isEmpty) {
            emptyState.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void syncPeriodLogsFromServer(List<JSONObject> dataList) {
        for (JSONObject dataObj : dataList) {
            try {
                String serverId = dataObj.getString("_id");

                // Check if this data already exists locally
                boolean exists = false;
                List<MedicalData> localData = dbHelper.getMedicalDataForUser(userProfile.getId());
                for (MedicalData localItem : localData) {
                    if (serverId.equals(localItem.getServerId())) {
                        exists = true;
                        break;
                    }
                }

                if (!exists) {
                    MedicalData.PeriodLog periodLog = parsePeriodLogFromJson(dataObj);
                    if (periodLog != null) {
                        dbHelper.insertPeriodLog(periodLog);
                    }
                }
            } catch (JSONException e) {
                Log.e(TAG, "Error parsing period log: " + e.getMessage());
            }
        }
    }

    private MedicalData.PeriodLog parsePeriodLogFromJson(JSONObject json) {
        try {
            MedicalData.PeriodLog periodLog = new MedicalData.PeriodLog();
            periodLog.setServerId(json.getString("_id"));
            periodLog.setFlowIntensity(json.getString("flowIntensity"));
            periodLog.setPainLevel(json.getInt("painLevel"));
            periodLog.setNotes(json.optString("notes", ""));

            if (json.has("shareWithFamily")) {
                periodLog.setShareWithFamily(json.getBoolean("shareWithFamily"));
            }
            if (json.has("includeInChat")) {
                periodLog.setIncludeInChat(json.getBoolean("includeInChat"));
            }

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault());
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

            if (json.has("startDate")) {
                periodLog.setStartDate(sdf.parse(json.getString("startDate")));
                periodLog.setRecordedAt(periodLog.getStartDate());
            } else {
                periodLog.setStartDate(new Date());
                periodLog.setRecordedAt(new Date());
            }

            if (json.has("endDate") && !json.isNull("endDate")) {
                periodLog.setEndDate(sdf.parse(json.getString("endDate")));
            }

            if (userProfile != null) {
                periodLog.setUserId(userProfile.getId());
            }

            return periodLog;
        } catch (Exception e) {
            Log.e(TAG, "Error parsing period log JSON: " + e.getMessage());
            return null;
        }
    }

    private void showAddPeriodLogDialog() {
        Dialog dialog = new Dialog(requireContext(), R.style.DialogTheme);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_add_period_log);

        // App-standard dialog window (92% width, wrap height, bounded scroll).
        Utils.DialogUtils.applyStandardEditDialogWindow(dialog);

        MaterialButton startDateButton = dialog.findViewById(R.id.start_date_button);
        MaterialButton endDateButton = dialog.findViewById(R.id.end_date_button);
        AutoCompleteTextView flowDropdown = dialog.findViewById(R.id.flow_intensity_dropdown);
        SeekBar painSeekBar = dialog.findViewById(R.id.pain_level_seekbar);
        TextView painText = dialog.findViewById(R.id.pain_level_text);
        EditText notesInput = dialog.findViewById(R.id.notes_input);
        SwitchMaterial shareSwitch = dialog.findViewById(R.id.share_with_family_switch);
        Button cancelButton = dialog.findViewById(R.id.cancel_button);
        Button saveButton = dialog.findViewById(R.id.save_button);

        // Date holders
        final Calendar startCal = Calendar.getInstance();
        final Calendar endCal = Calendar.getInstance();
        final boolean[] startDateSet = {false};
        final boolean[] endDateSet = {false};
        SimpleDateFormat displayFormat = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());

        // Start date picker
        startDateButton.setOnClickListener(v -> {
            DatePickerDialog picker = new DatePickerDialog(requireContext(),
                    (view, year, month, dayOfMonth) -> {
                        startCal.set(year, month, dayOfMonth);
                        startDateButton.setText(displayFormat.format(startCal.getTime()));
                        startDateButton.setTextColor(Color.WHITE);
                        startDateSet[0] = true;
                    },
                    startCal.get(Calendar.YEAR),
                    startCal.get(Calendar.MONTH),
                    startCal.get(Calendar.DAY_OF_MONTH));
            picker.getDatePicker().setMaxDate(System.currentTimeMillis());
            picker.show();
        });

        // End date picker
        endDateButton.setOnClickListener(v -> {
            DatePickerDialog picker = new DatePickerDialog(requireContext(),
                    (view, year, month, dayOfMonth) -> {
                        endCal.set(year, month, dayOfMonth);
                        endDateButton.setText(displayFormat.format(endCal.getTime()));
                        endDateButton.setTextColor(Color.WHITE);
                        endDateSet[0] = true;
                    },
                    endCal.get(Calendar.YEAR),
                    endCal.get(Calendar.MONTH),
                    endCal.get(Calendar.DAY_OF_MONTH));
            picker.getDatePicker().setMaxDate(System.currentTimeMillis());
            picker.show();
        });

        // Flow intensity dropdown
        String[] flowOptions = {"Light", "Medium", "Heavy"};
        ArrayAdapter<String> flowAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, flowOptions);
        flowDropdown.setAdapter(flowAdapter);

        // Pain level seekbar
        String[] painLabels = {"Very Mild", "Mild", "Moderate", "Severe", "Very Severe"};
        painSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                painText.setText(painLabels[progress]);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        cancelButton.setOnClickListener(v -> dialog.dismiss());

        saveButton.setOnClickListener(v -> {
            if (!startDateSet[0]) {
                Utilities.toast(requireContext(), "Please select a start date");
                return;
            }

            String flowText = flowDropdown.getText().toString().trim();
            if (flowText.isEmpty()) {
                Utilities.toast(requireContext(), "Please select flow intensity");
                return;
            }

            // Map display label to API value
            String flowIntensity = flowText.toLowerCase(Locale.US);

            int painLevel = painSeekBar.getProgress() + 1;
            String notes = notesInput.getText().toString().trim();

            MedicalData.PeriodLog periodLog = new MedicalData.PeriodLog();
            periodLog.setStartDate(startCal.getTime());
            if (endDateSet[0]) {
                periodLog.setEndDate(endCal.getTime());
            }
            periodLog.setFlowIntensity(flowIntensity);
            periodLog.setPainLevel(painLevel);
            periodLog.setNotes(notes);
            periodLog.setRecordedAt(startCal.getTime());
            periodLog.setShareWithFamily(shareSwitch.isChecked());

            if (userProfile != null) {
                periodLog.setUserId(userProfile.getId());
            }

            savePeriodLog(periodLog);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void savePeriodLog(MedicalData.PeriodLog periodLog) {
        long id = dbHelper.insertPeriodLog(periodLog);
        if (id > 0) {
            periodLog.setId(id);
            periodLogsAdapter.addItem(periodLog);

            // Close the side panel after entry
            if (periodLogsPanel != null && periodLogsPanel.isShowing()) {
                periodLogsPanel.dismiss();
            }

            Utilities.toast(requireContext(), "Period log added successfully");

            medicalDataApiService.addPeriodLog(periodLog, new MedicalDataApiService.OnMedicalDataListener() {
                @Override
                public void onSuccess(JSONObject response) {
                    try {
                        JSONObject data = response.getJSONObject("data");
                        String serverId = data.getString("_id");
                        periodLog.setServerId(serverId);
                        dbHelper.updateMedicalData(periodLog);
                        Log.d(TAG, "Period log synced with server: " + serverId);
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing period log response", e);
                    }
                }

                @Override
                public void onError(String errorMessage) {
                    Log.e(TAG, "Error saving period log to server: " + errorMessage);
                    Utilities.toast(requireContext(), "Saved locally but couldn't sync with server");
                }
            });
        } else {
            Utilities.toast(requireContext(), "Failed to save period log");
        }
    }

    private void showEditPeriodLogDialog(MedicalData.PeriodLog periodLog) {
        Dialog dialog = new Dialog(requireContext(), R.style.DialogTheme);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_add_period_log);

        // App-standard dialog window (92% width, wrap height, bounded scroll).
        Utils.DialogUtils.applyStandardEditDialogWindow(dialog);

        TextView title = dialog.findViewById(R.id.dialog_title);
        title.setText("Edit Period Log");

        MaterialButton startDateButton = dialog.findViewById(R.id.start_date_button);
        MaterialButton endDateButton = dialog.findViewById(R.id.end_date_button);
        AutoCompleteTextView flowDropdown = dialog.findViewById(R.id.flow_intensity_dropdown);
        SeekBar painSeekBar = dialog.findViewById(R.id.pain_level_seekbar);
        TextView painText = dialog.findViewById(R.id.pain_level_text);
        EditText notesInput = dialog.findViewById(R.id.notes_input);
        SwitchMaterial shareSwitch = dialog.findViewById(R.id.share_with_family_switch);
        Button cancelButton = dialog.findViewById(R.id.cancel_button);
        Button saveButton = dialog.findViewById(R.id.save_button);

        SimpleDateFormat displayFormat = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
        final Calendar startCal = Calendar.getInstance();
        final Calendar endCal = Calendar.getInstance();
        final boolean[] startDateSet = {true};
        final boolean[] endDateSet = {periodLog.getEndDate() != null};

        // Pre-fill existing values
        startCal.setTime(periodLog.getStartDate());
        startDateButton.setText(displayFormat.format(periodLog.getStartDate()));
        startDateButton.setTextColor(Color.WHITE);

        if (periodLog.getEndDate() != null) {
            endCal.setTime(periodLog.getEndDate());
            endDateButton.setText(displayFormat.format(periodLog.getEndDate()));
            endDateButton.setTextColor(Color.WHITE);
        }

        // Flow dropdown
        String[] flowOptions = {"Light", "Medium", "Heavy"};
        ArrayAdapter<String> flowAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, flowOptions);
        flowDropdown.setAdapter(flowAdapter);
        flowDropdown.setText(periodLog.getFlowIntensityLabel(), false);

        // Pain seekbar
        painSeekBar.setProgress(periodLog.getPainLevel() - 1);
        String[] painLabels = {"Very Mild", "Mild", "Moderate", "Severe", "Very Severe"};
        painText.setText(painLabels[periodLog.getPainLevel() - 1]);
        painSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                painText.setText(painLabels[progress]);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        if (periodLog.getNotes() != null) {
            notesInput.setText(periodLog.getNotes());
        }
        shareSwitch.setChecked(periodLog.isShareWithFamily());

        startDateButton.setOnClickListener(v -> {
            DatePickerDialog picker = new DatePickerDialog(requireContext(),
                    (view, year, month, dayOfMonth) -> {
                        startCal.set(year, month, dayOfMonth);
                        startDateButton.setText(displayFormat.format(startCal.getTime()));
                        startDateButton.setTextColor(Color.WHITE);
                        startDateSet[0] = true;
                    },
                    startCal.get(Calendar.YEAR), startCal.get(Calendar.MONTH), startCal.get(Calendar.DAY_OF_MONTH));
            picker.getDatePicker().setMaxDate(System.currentTimeMillis());
            picker.show();
        });

        endDateButton.setOnClickListener(v -> {
            DatePickerDialog picker = new DatePickerDialog(requireContext(),
                    (view, year, month, dayOfMonth) -> {
                        endCal.set(year, month, dayOfMonth);
                        endDateButton.setText(displayFormat.format(endCal.getTime()));
                        endDateButton.setTextColor(Color.WHITE);
                        endDateSet[0] = true;
                    },
                    endCal.get(Calendar.YEAR), endCal.get(Calendar.MONTH), endCal.get(Calendar.DAY_OF_MONTH));
            picker.getDatePicker().setMaxDate(System.currentTimeMillis());
            picker.show();
        });

        cancelButton.setOnClickListener(v -> dialog.dismiss());

        saveButton.setOnClickListener(v -> {
            String flowText = flowDropdown.getText().toString().trim();
            if (flowText.isEmpty()) {
                Utilities.toast(requireContext(), "Please select flow intensity");
                return;
            }

            periodLog.setStartDate(startCal.getTime());
            if (endDateSet[0]) {
                periodLog.setEndDate(endCal.getTime());
            }
            periodLog.setFlowIntensity(flowText.toLowerCase(Locale.US));
            periodLog.setPainLevel(painSeekBar.getProgress() + 1);
            periodLog.setNotes(notesInput.getText().toString().trim());
            periodLog.setShareWithFamily(shareSwitch.isChecked());
            periodLog.setRecordedAt(startCal.getTime());

            // Update local DB
            dbHelper.updateMedicalData(periodLog);
            periodLogsAdapter.updateItem(periodLog);
            Utilities.toast(requireContext(), "Period log updated");

            // Update on server
            if (periodLog.getServerId() != null) {
                medicalDataApiService.updatePeriodLog(periodLog.getServerId(), periodLog, new MedicalDataApiService.OnMedicalDataListener() {
                    @Override
                    public void onSuccess(JSONObject response) {
                        Log.d(TAG, "Period log updated on server");
                    }

                    @Override
                    public void onError(String errorMessage) {
                        Log.e(TAG, "Error updating period log on server: " + errorMessage);
                    }
                });
            }

            dialog.dismiss();
        });

        dialog.show();
    }

    private void showDeletePeriodLogConfirmDialog(MedicalData data, int position) {
        Utils.DialogUtils.showConfirmDialog(requireContext(),
                "Delete Period Log",
                "Are you sure you want to delete this period log?",
                "Delete", "Cancel", true,
                () -> {
                    // Remove from adapter
                    periodLogsAdapter.removeItem(position);

                    // Soft delete locally
                    dbHelper.softDeleteMedicalData(data.getId());

                    // Delete from server
                    if (data.getServerId() != null) {
                        medicalDataApiService.deletePeriodLog(data.getServerId(), new MedicalDataApiService.OnDeleteListener() {
                            @Override
                            public void onSuccess() {
                                Log.d(TAG, "Period log deleted from server");
                            }

                            @Override
                            public void onError(String errorMessage) {
                                Log.e(TAG, "Error deleting period log from server: " + errorMessage);
                            }
                        });
                    }

                    Utilities.toast(requireContext(), "Period log deleted");
                    loadPeriodLogs();
                });
    }

    private void showEditMedicationDialog(MedicationModel medication) {
        Dialog dialog = new Dialog(requireContext(), R.style.DialogTheme);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_add_medication);

        // App-standard dialog window (92% width, wrap height, bounded scroll).
        Utils.DialogUtils.applyStandardEditDialogWindow(dialog);

        TextView dialogTitle = dialog.findViewById(R.id.dialog_title);
        dialogTitle.setText("Edit Medication");

        TextInputEditText medicationNameInput = dialog.findViewById(R.id.medication_name_input);
        TextInputEditText dosageInput = dialog.findViewById(R.id.dosage_input);
        AutoCompleteTextView frequencyDropdown = dialog.findViewById(R.id.frequency_dropdown);
        Button startDateInput = dialog.findViewById(R.id.start_date_input);
        Button endDateInput = dialog.findViewById(R.id.end_date_input);
        TextInputEditText notesInput = dialog.findViewById(R.id.notes_input);
        SwitchMaterial stillTakingSwitch = dialog.findViewById(R.id.still_taking_switch);
        TextView disclaimerText = dialog.findViewById(R.id.disclaimer_text);

        // New fields
        TextInputEditText purposeInput = dialog.findViewById(R.id.purpose_input);
        TextInputEditText prescribedByInput = dialog.findViewById(R.id.prescribed_by_input);
        AutoCompleteTextView medicationTypeDropdown = dialog.findViewById(R.id.medication_type_dropdown);
        AutoCompleteTextView administrationMethodDropdown = dialog.findViewById(R.id.administration_method_dropdown);
        SwitchMaterial shareSwitch = dialog.findViewById(R.id.share_with_family_switch);

        Button cancelButton = dialog.findViewById(R.id.cancel_button);
        Button saveButton = dialog.findViewById(R.id.save_button);

        final ReminderUiState reminderState = setupReminderSection(dialog, medication);

        final boolean[] isStillTaking = {medication.isActive()};
        endDateInput.setVisibility(isStillTaking[0] ? View.GONE : View.VISIBLE);
        disclaimerText.setVisibility(isStillTaking[0] ? View.VISIBLE : View.GONE);
        shareSwitch.setChecked(medication.isShareWithFamily());

        String[] frequencies = {
                "Once daily", "Twice daily", "Three times daily", "Four times daily",
                "Every 6 hours", "Every 8 hours", "Every 12 hours", "As needed",
                "Weekly", "Monthly", "Custom"
        };
        ArrayAdapter<String> frequencyAdapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_dropdown_item_1line, frequencies
        );
        frequencyDropdown.setAdapter(frequencyAdapter);

        // Setup medication type dropdown
        String[] medicationTypes = {"Prescription", "Over-the-counter", "Supplement", "Herbal", "Vitamin", "Other"};
        ArrayAdapter<String> medicationTypeAdapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_dropdown_item_1line, medicationTypes
        );
        medicationTypeDropdown.setAdapter(medicationTypeAdapter);

        // Setup administration method dropdown
        String[] administrationMethods = {"Oral", "Injection", "Topical", "Inhaled", "Eye drops", "Nasal", "Other"};
        ArrayAdapter<String> administrationMethodAdapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_dropdown_item_1line, administrationMethods
        );
        administrationMethodDropdown.setAdapter(administrationMethodAdapter);

        stillTakingSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isStillTaking[0] = isChecked;
            if (isChecked) {
                // Still taking - hide end date, show disclaimer
                endDateInput.setVisibility(View.GONE);
                endDateInput.setText(""); // Clear end date
                disclaimerText.setVisibility(View.VISIBLE);
            } else {
                // Not taking - show end date, hide disclaimer
                endDateInput.setVisibility(View.VISIBLE);
                disclaimerText.setVisibility(View.GONE);
            }
        });

        // Setup date pickers
        startDateInput.setOnClickListener(v -> showDatePickerDialog(startDateInput));
        endDateInput.setOnClickListener(v -> showDatePickerDialog(endDateInput));

        // Set current medication values
        medicationNameInput.setText(medication.getName());
        dosageInput.setText(medication.getDosage());
        frequencyDropdown.setText(medication.getFrequency());
        stillTakingSwitch.setChecked(medication.isActive());

        // Set dates if available
        SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy", Locale.US);
        if (medication.getStartDate() != null) {
            startDateInput.setText(dateFormat.format(medication.getStartDate()));
        }
        if (medication.getEndDate() != null && !medication.isActive()) {
            endDateInput.setText(dateFormat.format(medication.getEndDate()));
        }

        if (medication.getNotes() != null) {
            notesInput.setText(medication.getNotes());
        }

        // Populate new fields
        if (medication.getPurpose() != null) {
            purposeInput.setText(medication.getPurpose());
        }
        if (medication.getPrescribedBy() != null) {
            prescribedByInput.setText(medication.getPrescribedBy());
        }
        if (medication.getMedicationType() != null) {
            medicationTypeDropdown.setText(medication.getMedicationType(), false);
        }
        if (medication.getAdministrationMethod() != null) {
            administrationMethodDropdown.setText(medication.getAdministrationMethod(), false);
        }
        cancelButton.setOnClickListener(v -> dialog.dismiss());
        saveButton.setOnClickListener(v -> {
            String name = medicationNameInput.getText().toString().trim();
            if (name.isEmpty()) {
                medicationNameInput.setError("Medication name is required");
                return;
            }

            String dosage = dosageInput.getText().toString().trim();
            String frequency = frequencyDropdown.getText().toString().trim();
            String startDateString = startDateInput.getText().toString().trim();
            String endDateString = endDateInput.getText().toString().trim();
            String notes = notesInput.getText().toString().trim();

            if (frequency.isEmpty()) {
                Utilities.toast(requireContext(), "Please select a frequency");
                return;
            }

            // Check if dates are actual dates (not default button text)
            SimpleDateFormat validationDateFormat = new SimpleDateFormat("MM/dd/yyyy", Locale.US);
            validationDateFormat.setLenient(false);

            Date parsedStart = null;
            Date parsedEnd = null;
            try { parsedStart = validationDateFormat.parse(startDateString); } catch (Exception ignored) {}
            try { parsedEnd = validationDateFormat.parse(endDateString); } catch (Exception ignored) {}

            if (parsedStart != null && parsedEnd != null) {
                if (parsedEnd.before(parsedStart) || parsedEnd.equals(parsedStart)) {
                    Utilities.toast(requireContext(), "End date must be after start date");
                    return;
                }
            }

            if (!isStillTaking[0] && parsedEnd == null) {
                Utilities.toast(requireContext(), "End date is required when not taking medication");
                return;
            }

            String finalStartDate = parsedStart != null ? startDateString : "";
            String finalEndDate = parsedEnd != null ? endDateString : "";

            // Get new fields
            String purpose = purposeInput.getText().toString().trim();
            String prescribedBy = prescribedByInput.getText().toString().trim();
            String medicationType = medicationTypeDropdown.getText().toString().trim();
            String administrationMethod = administrationMethodDropdown.getText().toString().trim();

            // Reminder state
            boolean remindersEnabled = reminderState.isReady() && reminderState.switchReminders.isChecked();
            int[] reminderDays = new int[0];
            int[][] reminderTimes = new int[0][];
            if (remindersEnabled) {
                String cadence = reminderState.cadenceDropdown.getText().toString().trim();
                reminderTimes = readTimes(reminderState);
                if (CAD_TWICE_WEEK.equals(cadence)) {
                    reminderDays = readSelectedDays(reminderState);
                    if (reminderDays.length != 2) {
                        Utilities.toast(requireContext(), "Please pick two days for a twice-a-week reminder");
                        return;
                    }
                }
            }

            saveMedicationToAPI(medication, name, dosage, frequency, finalStartDate, finalEndDate, notes, isStillTaking[0],
                    purpose, prescribedBy, medicationType, administrationMethod, shareSwitch.isChecked(),
                    remindersEnabled, reminderDays, reminderTimes);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void showDeleteMedicationConfirmDialog(MedicationModel medication, int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Delete Medication");
        builder.setMessage("Are you sure you want to delete this medication?");
        builder.setPositiveButton("Delete", (dialog, which) -> {
            deleteMedication(medication, position);
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
    private void deleteMedication(MedicationModel medication, int position) {
        Context context = getContext();
        if (context == null) return;

        Utilities.toast(context, "Deleting...");

        String url = ApiConfig.BASE_URL + "/api/medications/" + medication.getServerId();
        TokenManager tokenManager = TokenManager.getInstance(context);

        StringRequest request = new StringRequest(Request.Method.DELETE, url,
                response -> {
                    ApiConfig.logRestCall(url, true, "Medication deleted");
                    // Cancel any local reminders for the deleted medication.
                    if (medication != null) {
                        Utils.MedicationReminderHelper.removeForMedication(context, medication.getServerId());
                    }
                    // Remove from the master list by id, then re-apply the current filter
                    // so both the master and the displayed list stay in sync.
                    String delId = medication != null ? medication.getServerId() : null;
                    if (delId != null) {
                        for (int i = allMedications.size() - 1; i >= 0; i--) {
                            if (delId.equals(allMedications.get(i).getServerId())) {
                                allMedications.remove(i);
                                break;
                            }
                        }
                    }
                    applyMedicationFilter();
                    Utilities.toast(context, "Medication deleted");
                },
                error -> {
                    ApiConfig.logRestCall(url, false, error.toString());
                    Log.e(TAG, "Error deleting medication", error);
                    Utilities.toast(context, "Failed to delete medication");
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + tokenManager.getToken());
                return headers;
            }
        };

        RequestQueue queue = Volley.newRequestQueue(context);
        queue.add(request);
    }

    private void showDiscontinueMedicationDialog(MedicationModel medication, int position) {
        Context context = getContext();
        if (context == null) return;

        Utils.DialogUtils.showConfirmDialog(context,
                "Discontinue " + medication.getName() + "?",
                "This will mark the medication as discontinued and set today as the end date. You can re-add it later if needed.",
                "Discontinue", "Cancel", true,
                () -> discontinueMedication(medication, position));
    }

    private void discontinueMedication(MedicationModel medication, int position) {
        Context context = getContext();
        if (context == null || medication.getServerId() == null) return;

        String url = ApiConfig.BASE_URL + "/api/medications/" + medication.getServerId() + "/discontinue";
        TokenManager tokenManager = TokenManager.getInstance(context);

        Date today = new Date();
        JSONObject requestBody = new JSONObject();
        try {
            SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
            isoFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            requestBody.put("discontinueDate", isoFormat.format(today));
        } catch (Exception e) {
            Log.e(TAG, "Error creating discontinue request", e);
        }

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.PATCH, url, requestBody,
                response -> {
                    ApiConfig.logRestCall(url, true, "Medication discontinued");
                    // A discontinued medication should stop reminding.
                    Utils.MedicationReminderHelper.removeForMedication(context, medication.getServerId());
                    medication.setEndDate(today);
                    medication.setActive(false);
                    if (medicationsAdapter != null) {
                        medicationsAdapter.notifyItemChanged(position);
                    }
                    Utilities.toast(context, "Medication discontinued");
                },
                error -> {
                    ApiConfig.logRestCall(url, false, error.toString());
                    Log.e(TAG, "Error discontinuing medication", error);
                    Utilities.toast(context, "Failed to discontinue medication");
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + tokenManager.getToken());
                return headers;
            }
        };

        Volley.newRequestQueue(context).add(request);
    }

    private void showEditSymptomDialog(MedicalData.Symptom symptom) {
        Dialog dialog = new Dialog(requireContext(), R.style.DialogTheme);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_add_symptom);

        // App-standard dialog window (92% width, wrap height, bounded scroll).
        Utils.DialogUtils.applyStandardEditDialogWindow(dialog);

        // Change dialog title to "Edit Symptom"
        TextView dialogTitle = dialog.findViewById(R.id.dialog_title);
        dialogTitle.setText("Edit Symptom");

        EditText nameInput = dialog.findViewById(R.id.name_input);
        SeekBar severitySeekBar = dialog.findViewById(R.id.severity_seekbar);
        TextView severityText = dialog.findViewById(R.id.severity_text);
        EditText durationInput = dialog.findViewById(R.id.duration_input);
        EditText descriptionInput = dialog.findViewById(R.id.description_input);
        Button dateButton = dialog.findViewById(R.id.date_button);
        Button timeButton = dialog.findViewById(R.id.time_button);
        SwitchMaterial shareSwitch = dialog.findViewById(R.id.share_with_family_switch);
        Button cancelButton = dialog.findViewById(R.id.cancel_button);
        Button saveButton = dialog.findViewById(R.id.save_button);

        // Set current values
        nameInput.setText(symptom.getName());
        severitySeekBar.setProgress(symptom.getSeverity() - 1);
        durationInput.setText(symptom.getDuration());
        descriptionInput.setText(symptom.getDescription());
        shareSwitch.setChecked(symptom.isShareWithFamily());

        // Seed date & time from the saved record so it can be corrected/back-dated.
        selectedDateTime = Calendar.getInstance();
        if (symptom.getRecordedAt() != null) selectedDateTime.setTime(symptom.getRecordedAt());
        updateDateTimeButtons(dateButton, timeButton);
        wireDateTimeButtons(dateButton, timeButton);

        String[] severityLabels = {"Very Mild", "Mild", "Moderate", "Severe", "Very Severe"};
        severityText.setText(severityLabels[symptom.getSeverity() - 1]);

        severitySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                severityText.setText(severityLabels[progress]);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        cancelButton.setOnClickListener(v -> dialog.dismiss());

        saveButton.setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            int severity = severitySeekBar.getProgress() + 1;
            String duration = durationInput.getText().toString().trim();
            String description = descriptionInput.getText().toString().trim();

            if (name.isEmpty()) {
                Utilities.toast(requireContext(), "Please enter a symptom name");
                return;
            }

            if (duration.isEmpty()) {
                Utilities.toast(requireContext(), "Please enter a duration");
                return;
            }

            symptom.setName(name);
            symptom.setSeverity(severity);
            symptom.setDuration(duration);
            symptom.setDescription(description);
            symptom.setRecordedAt(selectedDateTime.getTime());
            symptom.setShareWithFamily(shareSwitch.isChecked());

            updateSymptom(symptom);
            dialog.dismiss();
        });

        dialog.show();
    }
    private void updateSymptom(MedicalData.Symptom symptom) {
        int result = dbHelper.updateMedicalData(symptom);
        if (result > 0) {
            if (symptomsAdapter != null) {
                symptomsAdapter.updateItem(symptom);
            } else {
                loadSymptoms();
            }
            Utilities.toast(requireContext(), "Symptom updated");

            if (symptom.getServerId() != null) {
                medicalDataApiService.updateSymptom(symptom.getServerId(), symptom, new MedicalDataApiService.OnMedicalDataListener() {
                    @Override
                    public void onSuccess(JSONObject response) { }

                    @Override
                    public void onError(String errorMessage) {
                        Log.e(TAG, "Error syncing symptom: " + errorMessage);
                    }
                });
            }
        } else {
            Utilities.toast(requireContext(), "Failed to update symptom");
        }
    }

    private void showEditMeasurementDialog(MedicalData.HealthMetric metric) {
        Dialog dialog = new Dialog(requireContext(), R.style.DialogTheme);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_add_measurement);

        // App-standard dialog window (92% width, wrap height, bounded scroll).
        Utils.DialogUtils.applyStandardEditDialogWindow(dialog);

        TextView dialogTitle = dialog.findViewById(R.id.dialog_title);
        dialogTitle.setText("Edit Measurement");

        AutoCompleteTextView typeDropdown = dialog.findViewById(R.id.type_dropdown);
        EditText valueInput = dialog.findViewById(R.id.value_input);
        AutoCompleteTextView unitDropdown = dialog.findViewById(R.id.unit_dropdown);
        EditText notesInput = dialog.findViewById(R.id.notes_input);
        Button dateButton = dialog.findViewById(R.id.date_button);
        Button timeButton = dialog.findViewById(R.id.time_button);
        SwitchMaterial shareSwitch = dialog.findViewById(R.id.share_with_family_switch);
        Button cancelButton = dialog.findViewById(R.id.cancel_button);
        Button saveButton = dialog.findViewById(R.id.save_button);

        // Setup type dropdown
        String[] measurementTypes = {
                "Blood Pressure", "Blood Glucose", "Heart Rate", "Weight",
                "Temperature", "Oxygen Saturation", "Cholesterol", "Thyroid (TSH)"
        };

        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_dropdown_item_1line, measurementTypes
        );
        typeDropdown.setAdapter(typeAdapter);

        // Setup unit dropdown based on selected type
        Map<String, String[]> unitMap = new HashMap<>();
        unitMap.put("Blood Pressure", new String[]{"mmHg"});
        unitMap.put("Blood Glucose", new String[]{"mg/dL", "mmol/L"});
        unitMap.put("Heart Rate", new String[]{"bpm"});
        unitMap.put("Weight", new String[]{"kg", "lbs"});
        unitMap.put("Temperature", new String[]{"°C", "°F"});
        unitMap.put("Oxygen Saturation", new String[]{"%"});
        unitMap.put("Cholesterol", new String[]{"mg/dL", "mmol/L"});
        unitMap.put("Thyroid (TSH)", new String[]{"mIU/L"});

        typeDropdown.setOnItemClickListener((parent, view, position, id) -> {
            String selectedType = typeDropdown.getText().toString();
            String[] units = unitMap.get(selectedType);

            if (units != null) {
                ArrayAdapter<String> unitAdapter = new ArrayAdapter<>(
                        requireContext(), android.R.layout.simple_dropdown_item_1line, units
                );
                unitDropdown.setAdapter(unitAdapter);
                unitDropdown.setText(units[0], false);
            }
        });

        // Set current values
        typeDropdown.setText(metric.getMetricType(), false);
        valueInput.setText(String.valueOf(metric.getValue()));
        unitDropdown.setText(metric.getUnit(), false);
        notesInput.setText(metric.getNotes());
        shareSwitch.setChecked(metric.isShareWithFamily());

        // Setup unit adapter for current type
        String[] units = unitMap.get(metric.getMetricType());
        if (units != null) {
            ArrayAdapter<String> unitAdapter = new ArrayAdapter<>(
                    requireContext(), android.R.layout.simple_dropdown_item_1line, units
            );
            unitDropdown.setAdapter(unitAdapter);
        }

        // Initialize date and time
        selectedDateTime.setTime(metric.getRecordedAt());
        updateDateTimeButtons(dateButton, timeButton);

        // Setup date button
        dateButton.setOnClickListener(v -> {
            DatePickerDialog datePickerDialog = new DatePickerDialog(
                    requireContext(),
                    (view, year, month, dayOfMonth) -> {
                        selectedDateTime.set(Calendar.YEAR, year);
                        selectedDateTime.set(Calendar.MONTH, month);
                        selectedDateTime.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                        updateDateTimeButtons(dateButton, timeButton);
                    },
                    selectedDateTime.get(Calendar.YEAR),
                    selectedDateTime.get(Calendar.MONTH),
                    selectedDateTime.get(Calendar.DAY_OF_MONTH)
            );
            datePickerDialog.show();
        });

        // Setup time button
        timeButton.setOnClickListener(v -> {
            TimePickerDialog timePickerDialog = new TimePickerDialog(
                    requireContext(),
                    (view, hourOfDay, minute) -> {
                        selectedDateTime.set(Calendar.HOUR_OF_DAY, hourOfDay);
                        selectedDateTime.set(Calendar.MINUTE, minute);
                        updateDateTimeButtons(dateButton, timeButton);
                    },
                    selectedDateTime.get(Calendar.HOUR_OF_DAY),
                    selectedDateTime.get(Calendar.MINUTE),
                    false
            );
            timePickerDialog.show();
        });

        // Setup buttons
        cancelButton.setOnClickListener(v -> dialog.dismiss());

        saveButton.setOnClickListener(v -> {
            String type = typeDropdown.getText().toString().trim();
            String valueStr = valueInput.getText().toString().trim();
            String unit = unitDropdown.getText().toString().trim();
            String notes = notesInput.getText().toString().trim();

            if (type.isEmpty()) {
                Utilities.toast(requireContext(), "Please select a measurement type");
                return;
            }

            if (valueStr.isEmpty()) {
                Utilities.toast(requireContext(), "Please enter a value");
                return;
            }

            if (unit.isEmpty()) {
                Utilities.toast(requireContext(), "Please select a unit");
                return;
            }

            double value;
            try {
                value = Double.parseDouble(valueStr);
            } catch (NumberFormatException e) {
                Utilities.toast(requireContext(), "Please enter a valid number");
                return;
            }

            metric.setMetricType(type);
            metric.setValue(value);
            metric.setUnit(unit);
            metric.setNotes(notes);
            metric.setRecordedAt(selectedDateTime.getTime());
            metric.setShareWithFamily(shareSwitch.isChecked());

            metric.calculateStatus();

            updateMeasurement(metric);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void updateDateTimeButtons(Button dateButton, Button timeButton) {
        dateButton.setText(dateFormatter.format(selectedDateTime.getTime()));
        timeButton.setText(timeFormatter.format(selectedDateTime.getTime()));
    }

    /**
     * Wires a date + time button pair to the shared {@link #selectedDateTime}.
     * Same picker behaviour used by the measurement dialog — extracted so the
     * symptom dialog (and any other dated entry) can log past date/times too.
     */
    private void wireDateTimeButtons(Button dateButton, Button timeButton) {
        dateButton.setOnClickListener(v -> {
            DatePickerDialog datePickerDialog = new DatePickerDialog(
                    requireContext(),
                    (view, year, month, dayOfMonth) -> {
                        selectedDateTime.set(Calendar.YEAR, year);
                        selectedDateTime.set(Calendar.MONTH, month);
                        selectedDateTime.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                        updateDateTimeButtons(dateButton, timeButton);
                    },
                    selectedDateTime.get(Calendar.YEAR),
                    selectedDateTime.get(Calendar.MONTH),
                    selectedDateTime.get(Calendar.DAY_OF_MONTH)
            );
            datePickerDialog.getDatePicker().setMaxDate(System.currentTimeMillis());
            datePickerDialog.show();
        });

        timeButton.setOnClickListener(v -> {
            TimePickerDialog timePickerDialog = new TimePickerDialog(
                    requireContext(),
                    (view, hourOfDay, minute) -> {
                        selectedDateTime.set(Calendar.HOUR_OF_DAY, hourOfDay);
                        selectedDateTime.set(Calendar.MINUTE, minute);
                        updateDateTimeButtons(dateButton, timeButton);
                    },
                    selectedDateTime.get(Calendar.HOUR_OF_DAY),
                    selectedDateTime.get(Calendar.MINUTE),
                    false
            );
            timePickerDialog.show();
        });
    }

    private void updateMeasurement(MedicalData.HealthMetric metric) {
        int result = dbHelper.updateMedicalData(metric);
        if (result > 0) {
            measurementsAdapter.updateItem(metric);
            Utilities.toast(requireContext(), "Measurement updated");

            if (metric.getServerId() != null) {
                medicalDataApiService.updateMeasurement(metric.getServerId(), metric, new MedicalDataApiService.OnMedicalDataListener() {
                    @Override
                    public void onSuccess(JSONObject response) { }

                    @Override
                    public void onError(String errorMessage) {
                        Log.e(TAG, "Error syncing measurement: " + errorMessage);
                    }
                });
            }
        } else {
            Utilities.toast(requireContext(), "Failed to update measurement");
        }
    }

    private void updateMedication(MedicationModel medication) {
        // Keep the master list in sync so the search filter reflects the edit.
        if (medication != null && medication.getServerId() != null) {
            for (int i = 0; i < allMedications.size(); i++) {
                if (medication.getServerId().equals(allMedications.get(i).getServerId())) {
                    allMedications.set(i, medication);
                    break;
                }
            }
        }

        // Update in adapter (medications are server-only, no local DB)
        if (medicationsAdapter != null) {
            medicationsAdapter.updateItem(medication);
        } else {
            Log.e(TAG, "medicationsAdapter is null, cannot update UI");
            // Fallback: refresh all medications
            loadMedications();
        }

        // Show success message
        Utilities.toast(requireContext(), "Medication updated successfully");
    }

    private void showDeleteConfirmDialog(MedicalData data, int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Delete Item");
        builder.setMessage("Are you sure you want to delete this item?");
        builder.setPositiveButton("Delete", (dialog, which) -> {
            deleteMedicalData(data, position);
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void deleteMedicalData(MedicalData data, int position) {
        if (data.getServerId() != null) {
            Utilities.toast(requireContext(), "Deleting...");
            medicalDataApiService.deleteMedicalData(data.getServerId(), new MedicalDataApiService.OnDeleteListener() {
                @Override
                public void onSuccess() {
                    dbHelper.softDeleteMedicalData(data.getId());
                    // Refresh visible list so the deletion reflects without reopening the panel
                    if (data instanceof MedicalData.Symptom) {
                        loadSymptoms();
                    } else if (data instanceof MedicalData.HealthMetric) {
                        loadMeasurements();
                    } else {
                        loadSymptoms();
                        loadMeasurements();
                    }
                    Utilities.toast(requireContext(), "Deleted successfully");
                }

                @Override
                public void onError(String errorMessage) {
                    Utilities.toast(requireContext(), "Failed to delete: " + errorMessage);
                }
            });
        } else {
            // No server ID, just delete locally
            int result = dbHelper.softDeleteMedicalData(data.getId());
            if (result > 0) {
                // Remove from adapter
                loadMeasurements();
                loadSymptoms();

                // Show success message
                Utilities.toast(requireContext(), "Item deleted successfully");

            } else {
                Utilities.toast(requireContext(), "Failed to delete item");
            }
        }
    }

    // The exact deleteReport method from AIFragment
    private void deleteReport(UploadedFile file, int position) {
        Utils.DialogUtils.showConfirmDialog(requireContext(),
                "Delete Report",
                "This will remove the report from your view but keep it archived for your records.",
                "Delete", "Cancel", true,
                () -> {
                    if (file.getReportId() != null) {
                        Utilities.toast(requireContext(), "Deleting...");
                        apiService.deleteReport(file.getReportId(), new MedicalReportApiService.OnDeleteListener() {
                            @Override
                            public void onSuccess() {
                                allReports.remove(file);
                                reportUploadedFiles.remove(file);
                                reportFilesAdapter.notifyDataSetChanged();
                                updateReportsEmptyState();
                                Utilities.toast(requireContext(), "Report archived successfully");
                            }

                            @Override
                            public void onError(String error) {
                                Utilities.toast(requireContext(), "Failed to delete report: " + error);
                            }
                        });
                    } else {
                        allReports.remove(file);
                        reportUploadedFiles.remove(file);
                        reportFilesAdapter.notifyDataSetChanged();
                        updateReportsEmptyState();
                    }
                });
    }

    private void handleAnalyzeClick(UploadedFile file) {
        if (file.getReportId() == null) {
            Utilities.toast(requireContext(), "Report not uploaded yet");
            return;
        }

        String status = file.getStatus();

        if ("processing".equals(status) || "queued".equals(status)) {
            Utilities.toast(requireContext(), "Analysis is already in progress");
            return;
        }

        // If report has analysis, show it
        if (file.hasAnalysis()) {
            showAnalysisDialog(file, true);
            return;
        }

        // For unanalyzed reports — check if user can analyze
        if (!userCanAnalyze) {
            // Non-pro user: show upgrade dialog
            showProUpgradeDialog();
            return;
        }

        if ("failed".equals(status)) {
            Utils.DialogUtils.showConfirmDialog(requireContext(),
                    "Retry Analysis",
                    "Previous analysis failed. Would you like to try again?",
                    "Yes", "Cancel", false,
                    () -> requestAnalysis(file));
            return;
        }

        // Pro user with unanalyzed report — request analysis
        requestAnalysis(file);
    }

    private void requestAnalysis(UploadedFile file) {
        SimpleProgress progress = medicalReportsPanel != null && medicalReportsPanel.isShowing()
                ? SimpleProgress.show(medicalReportsPanel, "Analyzing your report with AI...")
                : SimpleProgress.show(requireActivity(), "Analyzing your report with AI...");

        // Update local status immediately
        file.setStatus("processing");
        reportFilesAdapter.notifyDataSetChanged();

        apiService.analyzeReport(file.getReportId(), new MedicalReportApiService.OnAnalysisListener() {
            @Override
            public void onSuccess(JSONObject analysis) {
                try {
                    JSONObject report = analysis.getJSONObject("report");
                    String newStatus = report.optString("status", "queued");

                    remoteReports.put(file.getReportId(), report);
                    file.setStatus(newStatus);
                    applyAnalysisToFile(file, report);
                    reportFilesAdapter.notifyDataSetChanged();

                    if ("processed".equals(newStatus)) {
                        progress.hide();
                        showAnalysisDialog(file, false);
                    } else if ("failed".equals(newStatus)) {
                        progress.hide();
                        Utilities.toast(requireContext(), "Analysis failed. Please try again.");
                    } else {
                        // Queued or processing — poll until done (or timeout).
                        pollAnalysisStatus(file, progress, 0);
                    }
                } catch (JSONException e) {
                    progress.hide();
                    Log.e(TAG, "Error parsing analysis response", e);
                    file.setStatus("failed");
                    reportFilesAdapter.notifyDataSetChanged();
                    Utilities.toast(requireContext(), "Error processing analysis response");
                }
            }

            @Override
            public void onError(String error) {
                progress.hide();
                file.setStatus("failed");
                reportFilesAdapter.notifyDataSetChanged();
                Utilities.toast(requireContext(), "Analysis failed: " + error);
            }

            @Override
            public void onNotAllowed(String message) {
                progress.hide();
                file.setStatus("uploaded");
                reportFilesAdapter.notifyDataSetChanged();
                Utils.DialogUtils.showConfirmDialog(requireContext(),
                        "Upgrade Required",
                        message,
                        "Upgrade", "Not Now", false,
                        () -> showProUpgradeDialog());
            }
        });
    }

    // Polls the report endpoint until status becomes processed/failed, or until
    // the attempt budget is exhausted. Backend processor runs at most every
    // 30s, but the analyze endpoint also fires an immediate batch — so most
    // reports finish within 5-20s. Budget: ~25 attempts × 4s = ~100s.
    private static final int ANALYSIS_POLL_MAX_ATTEMPTS = 25;
    private static final long ANALYSIS_POLL_INTERVAL_MS = 4000L;

    private void pollAnalysisStatus(UploadedFile file, SimpleProgress progress, int attempt) {
        if (!isAdded() || file.getReportId() == null) {
            progress.hide();
            return;
        }
        if (attempt >= ANALYSIS_POLL_MAX_ATTEMPTS) {
            progress.hide();
            Utilities.toastLong(requireContext(), "Analysis is taking longer than expected. We'll notify you when it's ready.");
            return;
        }

        View root = getView();
        Runnable poll = () -> apiService.getReportById(file.getReportId(),
                new MedicalReportApiService.OnAnalysisListener() {
                    @Override
                    public void onSuccess(JSONObject report) {
                        if (!isAdded()) {
                            progress.hide();
                            return;
                        }
                        String status = report.optString("status", "queued");
                        remoteReports.put(file.getReportId(), report);
                        file.setStatus(status);
                        applyAnalysisToFile(file, report);
                        reportFilesAdapter.notifyDataSetChanged();

                        if ("processed".equals(status)) {
                            progress.hide();
                            showAnalysisDialog(file, false);
                        } else if ("failed".equals(status)) {
                            progress.hide();
                            Utilities.toast(requireContext(), "Analysis failed. Please try again.");
                        } else {
                            pollAnalysisStatus(file, progress, attempt + 1);
                        }
                    }

                    @Override
                    public void onError(String error) {
                        // Transient errors during polling shouldn't kill the loop —
                        // the backend job may still complete. Keep polling.
                        Log.w(TAG, "Poll error (attempt " + attempt + "): " + error);
                        pollAnalysisStatus(file, progress, attempt + 1);
                    }
                });

        if (root != null) {
            root.postDelayed(poll, ANALYSIS_POLL_INTERVAL_MS);
        } else {
            new android.os.Handler(android.os.Looper.getMainLooper())
                    .postDelayed(poll, ANALYSIS_POLL_INTERVAL_MS);
        }
    }

    /**
     * Map a server-side report JSON onto a local UploadedFile, populating
     * every analysis field (summaries, opinion, risk, key findings, etc.).
     * Safe to call repeatedly — overwrites previous analysis data.
     */
    /** Effective report date in epoch millis: metadata.reportDate → uploadDate → createdAt. */
    private long resolveReportDateMillis(JSONObject report) {
        JSONObject metadata = report.optJSONObject("metadata");
        String[] candidates = {
                metadata != null ? metadata.optString("reportDate", null) : null,
                report.optString("uploadDate", null),
                report.optString("createdAt", null)
        };
        for (String s : candidates) {
            long ms = parseIsoMillis(s);
            if (ms > 0) return ms;
        }
        return System.currentTimeMillis();
    }

    /** Parse common Mongoose/ISO date strings; returns 0 when unparseable. */
    private long parseIsoMillis(String s) {
        if (s == null || s.isEmpty() || "null".equals(s)) return 0L;
        String[] patterns = {
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd"
        };
        for (String p : patterns) {
            try {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(p, java.util.Locale.US);
                sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                java.util.Date d = sdf.parse(s);
                if (d != null) return d.getTime();
            } catch (Exception ignored) { }
        }
        return 0L;
    }

    private void applyAnalysisToFile(UploadedFile file, JSONObject report) {
        try {
            String status = report.optString("status", file.getStatus());

            String summary = report.optString("aiAnalysisSummary", "");
            String detailed = report.optString("aiAnalysisDetailed", "");
            boolean hasAnalysis = "processed".equals(status) && summary != null && !summary.isEmpty();

            file.setHasAnalysis(hasAnalysis);
            file.setAiAnalysisSummary(summary);
            file.setAiAnalysisDetailed(detailed);
            file.setDetailedSummary(report.optString("detailedSummary", ""));
            file.setAiOpinion(report.optString("aiOpinion", ""));
            file.setRiskLevel(report.optString("riskLevel", ""));
            file.setUrgency(report.optString("urgency", ""));
            file.setReportTypeDetected(report.optString("reportTypeDetected", ""));
            file.setAnalysisStatus(report.optString("analysisStatus", ""));
            file.setStatusMessage(report.optString("statusMessage", ""));

            // Effective report date for trend plotting: prefer the actual test date
            // from metadata, fall back to the upload date.
            file.setReportDateMillis(resolveReportDateMillis(report));

            file.clearKeyFindings();
            JSONArray keyFindingsArray = report.optJSONArray("keyFindings");
            if (keyFindingsArray != null) {
                for (int i = 0; i < keyFindingsArray.length(); i++) {
                    JSONObject finding = keyFindingsArray.optJSONObject(i);
                    if (finding == null) continue;
                    // valueNumeric may be absent/null (non-numeric result) → NaN.
                    double valueNumeric = finding.has("valueNumeric") && !finding.isNull("valueNumeric")
                            ? finding.optDouble("valueNumeric", Double.NaN)
                            : Double.NaN;
                    file.addKeyFinding(new UploadedFile.KeyFinding(
                            finding.optString("parameter", ""),
                            finding.optString("value", ""),
                            finding.optString("unit", ""),
                            finding.optString("normalRange", ""),
                            finding.optString("status", "normal"),
                            finding.optString("canonicalKey", ""),
                            valueNumeric
                    ));
                }
            }

            file.setRecommendations(jsonArrayToStringList(report.optJSONArray("recommendations")));
            file.setFollowUpTests(jsonArrayToStringList(report.optJSONArray("followUpTests")));
            file.setLifestyleAdvice(jsonArrayToStringList(report.optJSONArray("lifestyleAdvice")));

            List<UploadedFile.PossibleCondition> conditions = new ArrayList<>();
            JSONArray conditionsArray = report.optJSONArray("possibleConditions");
            if (conditionsArray != null) {
                for (int i = 0; i < conditionsArray.length(); i++) {
                    JSONObject c = conditionsArray.optJSONObject(i);
                    if (c == null) continue;
                    conditions.add(new UploadedFile.PossibleCondition(
                            c.optString("name", ""),
                            c.optString("confidence", ""),
                            c.optString("rationale", "")
                    ));
                }
            }
            file.setPossibleConditions(conditions);
        } catch (Exception e) {
            Log.w(TAG, "applyAnalysisToFile: " + e.getMessage());
        }
    }

    private static List<String> jsonArrayToStringList(JSONArray array) {
        List<String> out = new ArrayList<>();
        if (array == null) return out;
        for (int i = 0; i < array.length(); i++) {
            String s = array.optString(i, "");
            if (s != null && !s.isEmpty()) out.add(s);
        }
        return out;
    }

    private void showAnalysisDialog(UploadedFile file, boolean showReanalyzeOption) {
        View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_report_analysis, null);

        // Header
        TextView title = view.findViewById(R.id.report_dialog_title);
        TextView subtitle = view.findViewById(R.id.report_dialog_subtitle);
        String detected = file.getReportTypeDetected();
        if (detected != null && !detected.isEmpty()) {
            title.setText(detected);
            if (file.getReportType() != null && !file.getReportType().isEmpty()
                    && !file.getReportType().equalsIgnoreCase(detected)) {
                subtitle.setText(file.getReportType());
                subtitle.setVisibility(View.VISIBLE);
            }
        } else if (file.getReportType() != null && !file.getReportType().isEmpty()) {
            title.setText(file.getReportType());
        } else {
            title.setText("Report Analysis");
        }

        // Status banner — show whenever analysis is not trustworthy or there's a status message
        View statusBanner = view.findViewById(R.id.report_status_banner);
        TextView statusMessageView = view.findViewById(R.id.report_status_message);
        ImageView statusIcon = view.findViewById(R.id.report_status_icon);
        boolean trustworthy = file.isAnalysisTrustworthy();
        String statusMsg = file.getStatusMessage();
        if (!trustworthy || (statusMsg != null && !statusMsg.isEmpty()
                && !"ok".equalsIgnoreCase(file.getAnalysisStatus()))) {
            statusBanner.setVisibility(View.VISIBLE);
            int color = trustworthy ? 0xFFFF9800 : 0xFFFF5252;
            statusMessageView.setTextColor(color);
            statusIcon.setColorFilter(color);
            String fallback;
            String s = file.getAnalysisStatus();
            if ("extraction_failed".equalsIgnoreCase(s)) {
                fallback = "We couldn't read this file clearly. Try uploading a sharper scan.";
            } else if ("not_a_medical_report".equalsIgnoreCase(s)) {
                fallback = "This doesn't look like a medical report.";
            } else if ("unreadable".equalsIgnoreCase(s)) {
                fallback = "The report was unreadable. Please upload a higher-quality file.";
            } else if ("partial".equalsIgnoreCase(s)) {
                fallback = "Only part of the report could be analyzed.";
            } else {
                fallback = "Analysis may be incomplete.";
            }
            statusMessageView.setText(
                    statusMsg != null && !statusMsg.isEmpty() ? statusMsg : fallback);
        }

        // Risk + urgency chips (only when trustworthy)
        View chipsRow = view.findViewById(R.id.report_chips_row);
        TextView chipRisk = view.findViewById(R.id.report_chip_risk);
        TextView chipUrgency = view.findViewById(R.id.report_chip_urgency);
        boolean anyChip = false;
        if (trustworthy) {
            String risk = file.getRiskLevel();
            if (risk != null && !risk.isEmpty()) {
                chipRisk.setText("Risk: " + capitalize(risk));
                chipRisk.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(getRiskColor(risk)));
                chipRisk.setVisibility(View.VISIBLE);
                anyChip = true;
            }
            String urgency = file.getUrgency();
            if (urgency != null && !urgency.isEmpty()) {
                chipUrgency.setText(capitalize(urgency));
                chipUrgency.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(getUrgencyColor(urgency)));
                chipUrgency.setVisibility(View.VISIBLE);
                anyChip = true;
            }
        }
        if (anyChip) chipsRow.setVisibility(View.VISIBLE);

        // Plain summary
        View summaryCard = view.findViewById(R.id.report_summary_card);
        TextView summaryText = view.findViewById(R.id.report_summary_text);
        if (file.getAiAnalysisSummary() != null && !file.getAiAnalysisSummary().isEmpty()) {
            summaryText.setText(file.getAiAnalysisSummary());
            summaryCard.setVisibility(View.VISIBLE);
        }

        // Key findings (only when trustworthy)
        View keyFindingsCard = view.findViewById(R.id.report_key_findings_card);
        LinearLayout keyFindingsContainer = view.findViewById(R.id.report_key_findings_container);
        if (trustworthy && file.getKeyFindings() != null && !file.getKeyFindings().isEmpty()) {
            for (UploadedFile.KeyFinding f : file.getKeyFindings()) {
                keyFindingsContainer.addView(buildKeyFindingRow(f));
            }
            keyFindingsCard.setVisibility(View.VISIBLE);
        }

        // AI Opinion
        View opinionCard = view.findViewById(R.id.report_opinion_card);
        TextView opinionText = view.findViewById(R.id.report_opinion_text);
        if (trustworthy && file.getAiOpinion() != null && !file.getAiOpinion().isEmpty()) {
            opinionText.setText(file.getAiOpinion());
            opinionCard.setVisibility(View.VISIBLE);
        }

        // Detailed
        View detailedCard = view.findViewById(R.id.report_detailed_card);
        TextView detailedText = view.findViewById(R.id.report_detailed_text);
        if (trustworthy && file.getDetailedSummary() != null && !file.getDetailedSummary().isEmpty()) {
            detailedText.setText(file.getDetailedSummary());
            detailedCard.setVisibility(View.VISIBLE);
        }

        // Possible conditions
        View conditionsCard = view.findViewById(R.id.report_conditions_card);
        LinearLayout conditionsContainer = view.findViewById(R.id.report_conditions_container);
        if (trustworthy && file.getPossibleConditions() != null && !file.getPossibleConditions().isEmpty()) {
            for (UploadedFile.PossibleCondition c : file.getPossibleConditions()) {
                conditionsContainer.addView(buildConditionRow(c));
            }
            conditionsCard.setVisibility(View.VISIBLE);
        }

        // Recommendations / follow-up / lifestyle
        attachBulletList(view, R.id.report_recommendations_card, R.id.report_recommendations_container,
                trustworthy ? file.getRecommendations() : null);
        attachBulletList(view, R.id.report_followup_card, R.id.report_followup_container,
                trustworthy ? file.getFollowUpTests() : null);
        attachBulletList(view, R.id.report_lifestyle_card, R.id.report_lifestyle_container,
                trustworthy ? file.getLifestyleAdvice() : null);

        // Empty state — only when trustworthy AND nothing to show AND no banner
        boolean anyContent = summaryCard.getVisibility() == View.VISIBLE
                || keyFindingsCard.getVisibility() == View.VISIBLE
                || opinionCard.getVisibility() == View.VISIBLE
                || detailedCard.getVisibility() == View.VISIBLE
                || conditionsCard.getVisibility() == View.VISIBLE
                || statusBanner.getVisibility() == View.VISIBLE;
        if (!anyContent) {
            view.findViewById(R.id.report_empty_text).setVisibility(View.VISIBLE);
        }

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(view)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        view.findViewById(R.id.report_close_button).setOnClickListener(v -> dialog.dismiss());

        View reanalyze = view.findViewById(R.id.report_reanalyze_button);
        if (showReanalyzeOption) {
            reanalyze.setVisibility(View.VISIBLE);
            reanalyze.setOnClickListener(v -> {
                dialog.dismiss();
                Utils.DialogUtils.showConfirmDialog(requireContext(),
                        "Re-analyze Report",
                        "Are you sure you want to re-analyze this report? This will override the existing analysis.",
                        "Yes", "No", false,
                        () -> requestAnalysis(file));
            });
        }

        dialog.show();
    }

    private View buildKeyFindingRow(UploadedFile.KeyFinding f) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(6), 0, dp(6));

        View dot = new View(requireContext());
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(8), dp(8));
        dotLp.topMargin = dp(6);
        dotLp.rightMargin = dp(10);
        dotLp.gravity = android.view.Gravity.TOP;
        dot.setLayoutParams(dotLp);
        android.graphics.drawable.GradientDrawable dotBg = new android.graphics.drawable.GradientDrawable();
        dotBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        dotBg.setColor(f.getStatusColor());
        dot.setBackground(dotBg);
        row.addView(dot);

        LinearLayout textCol = new LinearLayout(requireContext());
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textCol.setLayoutParams(textLp);

        TextView paramLine = new TextView(requireContext());
        StringBuilder s = new StringBuilder();
        s.append(f.getParameter() != null ? f.getParameter() : "");
        s.append(": ").append(f.getValue() != null ? f.getValue() : "");
        if (f.getUnit() != null && !f.getUnit().isEmpty()) s.append(" ").append(f.getUnit());
        paramLine.setText(s.toString());
        paramLine.setTextColor(0xFFE0E0E0);
        paramLine.setTextSize(13);
        textCol.addView(paramLine);

        if (f.getNormalRange() != null && !f.getNormalRange().isEmpty()) {
            TextView rangeLine = new TextView(requireContext());
            rangeLine.setText("Normal: " + f.getNormalRange());
            rangeLine.setTextColor(0xFF888888);
            rangeLine.setTextSize(11);
            textCol.addView(rangeLine);
        }
        row.addView(textCol);

        TextView statusChip = new TextView(requireContext());
        statusChip.setText(capitalize(f.getStatus()));
        statusChip.setTextColor(0xFFFFFFFF);
        statusChip.setTextSize(10);
        statusChip.setTypeface(statusChip.getTypeface(), android.graphics.Typeface.BOLD);
        statusChip.setPadding(dp(8), dp(3), dp(8), dp(3));
        android.graphics.drawable.GradientDrawable chipBg = new android.graphics.drawable.GradientDrawable();
        chipBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        chipBg.setCornerRadius(dp(10));
        chipBg.setColor(f.getStatusColor());
        statusChip.setBackground(chipBg);
        LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        chipLp.gravity = android.view.Gravity.CENTER_VERTICAL;
        statusChip.setLayoutParams(chipLp);
        row.addView(statusChip);

        return row;
    }

    private View buildConditionRow(UploadedFile.PossibleCondition c) {
        LinearLayout col = new LinearLayout(requireContext());
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, dp(6), 0, dp(6));

        LinearLayout firstLine = new LinearLayout(requireContext());
        firstLine.setOrientation(LinearLayout.HORIZONTAL);
        firstLine.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView name = new TextView(requireContext());
        name.setText(c.getName() != null ? c.getName() : "");
        name.setTextColor(0xFFFFFFFF);
        name.setTextSize(13);
        name.setTypeface(name.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        name.setLayoutParams(nameLp);
        firstLine.addView(name);

        if (c.getConfidence() != null && !c.getConfidence().isEmpty()) {
            TextView conf = new TextView(requireContext());
            conf.setText(c.getConfidence());
            conf.setTextColor(0xFF008b8b);
            conf.setTextSize(10);
            conf.setPadding(dp(8), dp(2), dp(8), dp(2));
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            bg.setCornerRadius(dp(10));
            bg.setStroke(dp(1), 0xFF008b8b);
            conf.setBackground(bg);
            firstLine.addView(conf);
        }
        col.addView(firstLine);

        if (c.getRationale() != null && !c.getRationale().isEmpty()) {
            TextView rationale = new TextView(requireContext());
            rationale.setText(c.getRationale());
            rationale.setTextColor(0xFFAAAAAA);
            rationale.setTextSize(12);
            rationale.setLineSpacing(0, 1.3f);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rlp.topMargin = dp(2);
            rationale.setLayoutParams(rlp);
            col.addView(rationale);
        }
        return col;
    }

    private void attachBulletList(View root, int cardId, int containerId, List<String> items) {
        View card = root.findViewById(cardId);
        LinearLayout container = root.findViewById(containerId);
        if (items == null || items.isEmpty()) return;
        for (String item : items) {
            if (item == null || item.isEmpty()) continue;
            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, dp(4), 0, dp(4));

            TextView bullet = new TextView(requireContext());
            bullet.setText("•");
            bullet.setTextColor(0xFF008b8b);
            bullet.setTextSize(14);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            blp.rightMargin = dp(8);
            bullet.setLayoutParams(blp);
            row.addView(bullet);

            TextView text = new TextView(requireContext());
            text.setText(item);
            text.setTextColor(0xFFE0E0E0);
            text.setTextSize(13);
            text.setLineSpacing(0, 1.4f);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            text.setLayoutParams(tlp);
            row.addView(text);

            container.addView(row);
        }
        card.setVisibility(View.VISIBLE);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private int getRiskColor(String risk) {
        if (risk == null) return 0xFF757575;
        switch (risk.toLowerCase()) {
            case "low": return 0xFF4CAF50;
            case "moderate": return 0xFFFF9800;
            case "high": return 0xFFFF5722;
            case "critical": return 0xFFF44336;
            default: return 0xFF757575;
        }
    }

    private int getUrgencyColor(String urgency) {
        if (urgency == null) return 0xFF757575;
        switch (urgency.toLowerCase()) {
            case "routine": return 0xFF2196F3;
            case "soon": return 0xFFFF9800;
            case "urgent": return 0xFFFF5722;
            case "emergency": return 0xFFF44336;
            default: return 0xFF757575;
        }
    }

    private String getRiskEmoji(String risk) {
        if (risk == null) return "";
        switch (risk.toLowerCase()) {
            case "low": return "🟢";
            case "moderate": return "🟡";
            case "high": return "🟠";
            case "critical": return "🔴";
            default: return "⚪";
        }
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return "";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private String getStatusEmoji(String status) {
        if (status == null) return "⚪";
        switch (status.toLowerCase()) {
            case "normal": return "🟢";
            case "high": return "🟠";
            case "low": return "🔵";
            case "critical": return "🔴";
            default: return "⚪";
        }
    }

    private void openReportFilePicker() {
        if (reportFilePickerLauncher == null) {
            Log.e(TAG, "reportFilePickerLauncher is null — fragment not in CREATED state");
            Utilities.toast(requireContext(), "Cannot open file picker right now.");
            return;
        }
        // ACTION_OPEN_DOCUMENT works better than ACTION_GET_CONTENT for PDFs
        // (and grants a stable URI we can read on the upload thread).
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/pdf",
                "image/*",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        });
        try {
            reportFilePickerLauncher.launch(intent);
        } catch (android.content.ActivityNotFoundException e) {
            // Some devices lack a SAF picker — fall back to GET_CONTENT.
            Log.w(TAG, "OPEN_DOCUMENT unavailable, falling back to GET_CONTENT", e);
            Intent fallback = new Intent(Intent.ACTION_GET_CONTENT);
            fallback.setType("*/*");
            fallback.addCategory(Intent.CATEGORY_OPENABLE);
            reportFilePickerLauncher.launch(Intent.createChooser(fallback, "Select report"));
        }
    }

    private void processReportFile(Uri uri) {
        if (uri != null && !isReportUriAlreadyAdded(uri)) {
            String fileName = getFileNameFromUri(uri);
            // Pass context to UploadedFile constructor
            UploadedFile newFile = new UploadedFile(requireContext(), uri, fileName);

            // Show report type dialog before adding
            showReportTypeDialog(newFile);
        }
    }

    private void showReportTypeDialog(UploadedFile file) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_report_type, null);
        AutoCompleteTextView reportTypeSpinner = dialogView.findViewById(R.id.report_type_spinner);
        String[] reportTypes = {"Blood Test", "X-Ray", "MRI", "CT Scan", "Ultrasound", "ECG", "Medical Checkup", "Lab Report", "Prescription", "Other"};

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                reportTypes
        );
        reportTypeSpinner.setAdapter(adapter);

        new AlertDialog.Builder(requireContext())
                .setTitle("Select Report Type")
                .setView(dialogView)
                .setPositiveButton("Save", (dialog, which) -> {
                    String selectedType = reportTypeSpinner.getText().toString();
                    // Save file with selected type
                    file.setReportType(selectedType);
                    saveReportToDatabase(file);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveReportToDatabase(UploadedFile file) {
        Log.d(TAG, "saveReportToDatabase: name=" + file.getName()
                + " type=" + file.getReportType() + " mime=" + file.getFileType());
        SimpleProgress progress = medicalReportsPanel != null && medicalReportsPanel.isShowing()
                ? SimpleProgress.show(medicalReportsPanel, "Uploading report...")
                : SimpleProgress.show(requireActivity(), "Uploading report...");

        try {
            File actualFile = getFileFromUri(file.getUri());

            if (actualFile == null || !actualFile.exists() || actualFile.length() == 0) {
                progress.hide();
                Log.e(TAG, "saveReportToDatabase: failed to materialize file from uri="
                        + file.getUri());
                Utilities.toastLong(requireContext(), "Couldn't read the selected file. Please try a different one.");
                return;
            }
            Log.d(TAG, "saveReportToDatabase: copied to " + actualFile.getAbsolutePath()
                    + " size=" + actualFile.length());

            uploadWithRawText(file, actualFile, "", progress);

        } catch (Exception e) {
            progress.hide();
            Log.e(TAG, "saveReportToDatabase failed", e);
            Utilities.toastLong(requireContext(), "Error preparing file: " + e.getMessage());
        }
    }

    private void uploadWithRawText(UploadedFile file, File actualFile, String rawText, SimpleProgress progress) {
        Log.d(TAG, "uploadWithRawText: POSTing " + actualFile.getName()
                + " (" + actualFile.length() + " bytes) as " + file.getReportType());
        try {
            JSONObject metadata = new JSONObject();
            metadata.put("originalFileName", file.getName());

            apiService.uploadReport(actualFile, file.getReportType(), metadata,
                    new MedicalReportApiService.OnReportUploadListener() {
                        @Override
                        public void onSuccess(JSONObject response) {
                            progress.hide();
                            try {
                                JSONObject report = response.getJSONObject("report");
                                String reportId = report.getString("_id");
                                String fileUrl = report.getString("fileUrl");

                                remoteReports.put(reportId, report);

                                file.setReportId(reportId);
                                file.setStatus(report.getString("status"));
                                file.setFileUrl(fileUrl);

                                allReports.add(file);
                                applyReportFilter();

                                Utilities.toast(requireContext(), "Report uploaded successfully");
                            } catch (JSONException e) {
                                Log.e(TAG, "Error parsing upload response", e);
                                Utilities.toast(requireContext(), "Error processing upload response");
                            }
                        }

                        @Override
                        public void onError(String error) {
                            progress.hide();
                            Utilities.toast(requireContext(), "Upload failed: " + error);
                        }

                        @Override
                        public void onLimitReached(String message) {
                            progress.hide();
                            Utils.DialogUtils.showConfirmDialog(requireContext(),
                                    "Report Limit Reached",
                                    message,
                                    "Upgrade", "Not Now", false,
                                    () -> showProUpgradeDialog());
                        }

                        @Override
                        public void onProgress(int progressValue) {
                            // Update progress dialog if needed
                        }
                    }, rawText);
        } catch (Exception e) {
            progress.hide();
            Utilities.toast(requireContext(), "Error preparing file: " + e.getMessage());
        }
    }

    private void saveReportToLocalDatabase(UploadedFile file, JSONObject serverReport) {
        try {
            // Get and set file type explicitly
            String fileType = null;
            if (file.getUri() != null) {
                fileType = requireContext().getContentResolver().getType(file.getUri());
            }
            if (fileType == null) {
                fileType = "application/octet-stream";
            }
            file.setFileType(fileType);

            // Create MedicalReport object for local database
            UserProfile.MedicalReport localReport = new UserProfile.MedicalReport(
                    file.getName(),
                    file.getFileType(),
                    file.getReportType(),
                    file.getUri() != null ? file.getUri().toString() : ""
            );

            // Set server ID
            String serverId = serverReport.getString("_id");
            localReport.setServerReportId(serverId);  // Store server ID separately
            localReport.setStatus(serverReport.getString("status"));

            // Set analysis if available
            if (serverReport.has("aiAnalysisSummary")) {
                localReport.setAiAnalysis(serverReport.getString("aiAnalysisSummary"));
            }

        } catch (JSONException e) {
            Log.e(TAG, "Error saving to local database", e);
        }
    }

    private File getFileFromUri(Uri uri) {
        try {
            // Create a temporary file
            String fileName = getFileNameFromUri(uri);
            File tempFile = new File(requireContext().getCacheDir(), fileName);

            try (InputStream inputStream = requireContext().getContentResolver().openInputStream(uri);
                 FileOutputStream outputStream = new FileOutputStream(tempFile)) {

                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }

            return tempFile;
        } catch (IOException e) {
            Log.e(TAG, "Error creating file from URI", e);
            return null;
        }
    }

    private boolean isReportUriAlreadyAdded(Uri uri) {
        String fileName = getFileNameFromUri(uri);
        for (UploadedFile file : reportUploadedFiles) {
            if (file.getName().equals(fileName)) {
                return true;
            }
        }
        return false;
    }

    private String getFileNameFromUri(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = requireContext().getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        result = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting file name", e);
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    private void showProUpgradeDialog() {
        if (proUpgradeDialog == null) {
            proUpgradeDialog = new ProUpgradeDialog(requireActivity());
        }
        proUpgradeDialog.show(new ProUpgradeDialog.ProUpgradeCallback() {
            @Override
            public void onProStatusChanged(boolean isPro) {
                if (isPro) {
                    Utilities.toast(requireContext(), "Pro features unlocked!");
                }
            }
        });
    }

    private void showAddMedicationDialog() {
        Dialog dialog = new Dialog(requireContext(), R.style.DialogTheme);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_add_medication);

        // App-standard dialog window (92% width, wrap height, bounded scroll).
        Utils.DialogUtils.applyStandardEditDialogWindow(dialog);

        TextInputEditText medicationNameInput = dialog.findViewById(R.id.medication_name_input);
        TextInputEditText dosageInput = dialog.findViewById(R.id.dosage_input);
        AutoCompleteTextView frequencyDropdown = dialog.findViewById(R.id.frequency_dropdown);
        Button startDateInput = dialog.findViewById(R.id.start_date_input);
        Button endDateInput = dialog.findViewById(R.id.end_date_input);
        TextInputEditText notesInput = dialog.findViewById(R.id.notes_input);
        SwitchMaterial stillTakingSwitch = dialog.findViewById(R.id.still_taking_switch);
        TextView disclaimerText = dialog.findViewById(R.id.disclaimer_text);

        // New fields
        TextInputEditText purposeInput = dialog.findViewById(R.id.purpose_input);
        TextInputEditText prescribedByInput = dialog.findViewById(R.id.prescribed_by_input);
        AutoCompleteTextView medicationTypeDropdown = dialog.findViewById(R.id.medication_type_dropdown);
        AutoCompleteTextView administrationMethodDropdown = dialog.findViewById(R.id.administration_method_dropdown);
        SwitchMaterial shareSwitch = dialog.findViewById(R.id.share_with_family_switch);

        Button cancelButton = dialog.findViewById(R.id.cancel_button);
        Button saveButton = dialog.findViewById(R.id.save_button);

        final ReminderUiState reminderState = setupReminderSection(dialog, null);

        final boolean[] isStillTaking = {true};
        endDateInput.setVisibility(View.GONE);
        disclaimerText.setVisibility(View.VISIBLE);

        String[] frequencies = {
                "Once daily", "Twice daily", "Three times daily", "Four times daily",
                "Every 6 hours", "Every 8 hours", "Every 12 hours", "As needed",
                "Weekly", "Monthly", "Custom"
        };
        ArrayAdapter<String> frequencyAdapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_dropdown_item_1line, frequencies
        );
        frequencyDropdown.setAdapter(frequencyAdapter);

        String[] medicationTypes = {"Prescription", "Over-the-counter", "Supplement", "Herbal", "Vitamin", "Other"};
        ArrayAdapter<String> medicationTypeAdapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_dropdown_item_1line, medicationTypes
        );
        medicationTypeDropdown.setAdapter(medicationTypeAdapter);

        String[] administrationMethods = {"Oral", "Injection", "Topical", "Inhaled", "Eye drops", "Nasal", "Other"};
        ArrayAdapter<String> administrationMethodAdapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_dropdown_item_1line, administrationMethods
        );
        administrationMethodDropdown.setAdapter(administrationMethodAdapter);

        stillTakingSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    isStillTaking[0] = isChecked;
                    if (isChecked) {
                        // Still taking - hide end date, show disclaimer
                        endDateInput.setVisibility(View.GONE);
                        endDateInput.setText(""); // Clear end date
                        disclaimerText.setVisibility(View.VISIBLE);
                    } else {
                        // Not taking - show end date, hide disclaimer
                        endDateInput.setVisibility(View.VISIBLE);
                        disclaimerText.setVisibility(View.GONE);
                    }
                });

        // Setup date pickers
        startDateInput.setOnClickListener(v -> showDatePickerDialog(startDateInput));
        endDateInput.setOnClickListener(v -> showDatePickerDialog(endDateInput));

        // Set default start date to today
        SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy", Locale.US);
        startDateInput.setText(dateFormat.format(new Date()));

        cancelButton.setOnClickListener(v -> dialog.dismiss());
        saveButton.setOnClickListener(v -> {
            String name = medicationNameInput.getText().toString().trim();
            if (name.isEmpty()) {
                medicationNameInput.setError("Medication name is required");
                return;
            }

            String dosage = dosageInput.getText().toString().trim();
            String frequency = frequencyDropdown.getText().toString().trim();
            String startDateString = startDateInput.getText().toString().trim();
            String endDateString = endDateInput.getText().toString().trim();
            String notes = notesInput.getText().toString().trim();

            if (frequency.isEmpty()) {
                Utilities.toast(requireContext(), "Please select a frequency");
                return;
            }

            // Check if dates are actual dates (not default button text)
            SimpleDateFormat validationDateFormat = new SimpleDateFormat("MM/dd/yyyy", Locale.US);
            validationDateFormat.setLenient(false);

            Date parsedStart = null;
            Date parsedEnd = null;
            try { parsedStart = validationDateFormat.parse(startDateString); } catch (Exception ignored) {}
            try { parsedEnd = validationDateFormat.parse(endDateString); } catch (Exception ignored) {}

            // Validate dates if both are provided
            if (parsedStart != null && parsedEnd != null) {
                if (parsedEnd.before(parsedStart) || parsedEnd.equals(parsedStart)) {
                    Utilities.toast(requireContext(), "End date must be after start date");
                    return;
                }
            }

            // Validate end date if not still taking
            if (!isStillTaking[0] && parsedEnd == null) {
                Utilities.toast(requireContext(), "End date is required when not taking medication");
                return;
            }

            // Use parsed date strings or empty
            String finalStartDate = parsedStart != null ? startDateString : "";
            String finalEndDate = parsedEnd != null ? endDateString : "";

            // Get new fields
            String purpose = purposeInput.getText().toString().trim();
            String prescribedBy = prescribedByInput.getText().toString().trim();
            String medicationType = medicationTypeDropdown.getText().toString().trim();
            String administrationMethod = administrationMethodDropdown.getText().toString().trim();

            // Reminder state
            boolean remindersEnabled = reminderState.isReady() && reminderState.switchReminders.isChecked();
            int[] reminderDays = new int[0];
            int[][] reminderTimes = new int[0][];
            if (remindersEnabled) {
                String cadence = reminderState.cadenceDropdown.getText().toString().trim();
                reminderTimes = readTimes(reminderState);
                if (CAD_TWICE_WEEK.equals(cadence)) {
                    reminderDays = readSelectedDays(reminderState);
                    if (reminderDays.length != 2) {
                        Utilities.toast(requireContext(), "Please pick two days for a twice-a-week reminder");
                        return;
                    }
                }
            }

            saveMedicationToAPI(null, name, dosage, frequency, finalStartDate, finalEndDate, notes, isStillTaking[0],
                    purpose, prescribedBy, medicationType, administrationMethod, shareSwitch.isChecked(),
                    remindersEnabled, reminderDays, reminderTimes);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void saveMedicationToAPI(String name, String dosage, String frequency,
                                     String startDateString, String endDateString,
                                     String notes, boolean isStillTaking) {
        saveMedicationToAPI(null, name, dosage, frequency, startDateString, endDateString, notes, isStillTaking,
                null, null, null, null, false, false, new int[0], new int[0][]);
    }

    private void saveMedicationToAPI(MedicationModel existingMedication, String name, String dosage, String frequency,
                                     String startDateString, String endDateString,
                                     String notes, boolean isStillTaking) {
        saveMedicationToAPI(existingMedication, name, dosage, frequency, startDateString, endDateString, notes, isStillTaking,
                null, null, null, null, false, false, new int[0], new int[0][]);
    }

    private void saveMedicationToAPI(MedicationModel existingMedication, String name, String dosage, String frequency,
                                     String startDateString, String endDateString,
                                     String notes, boolean isStillTaking,
                                     String purpose, String prescribedBy, String medicationType,
                                     String administrationMethod, boolean shareWithFamily) {
        saveMedicationToAPI(existingMedication, name, dosage, frequency, startDateString, endDateString, notes, isStillTaking,
                purpose, prescribedBy, medicationType, administrationMethod, shareWithFamily,
                false, new int[0], new int[0][]);
    }

    private void saveMedicationToAPI(MedicationModel existingMedication, String name, String dosage, String frequency,
                                     String startDateString, String endDateString,
                                     String notes, boolean isStillTaking,
                                     String purpose, String prescribedBy, String medicationType,
                                     String administrationMethod, boolean shareWithFamily,
                                     boolean remindersEnabled, int[] reminderDays, int[][] reminderTimes) {

        Context context = getContext();
        if (context == null) return; // Fragment detached, skip operation safely

        TokenManager tokenManager = TokenManager.getInstance(context);
        String token = tokenManager.getToken();

        if (token == null) {
            Utilities.toast(requireContext(), "Authentication error");
            return;
        }

        // Determine if this is an update or create operation
        boolean isUpdate = existingMedication != null && existingMedication.getServerId() != null;
        String progressMessage = isUpdate ? "Updating medication..." : "Saving medication...";
        SimpleProgress progress = medicationsPanel != null && medicationsPanel.isShowing()
                ? SimpleProgress.show(medicationsPanel, progressMessage)
                : SimpleProgress.show(requireActivity(), progressMessage);

        String url = ApiConfig.BASE_URL + "/api/medications";
        int httpMethod = Request.Method.POST;

        if (isUpdate) {
            url += "/" + existingMedication.getServerId();
            httpMethod = Request.Method.PUT;
        }

        JSONObject requestBody = new JSONObject();
        try {
            requestBody.put("name", name);
            requestBody.put("dosage", dosage);
            requestBody.put("frequency", frequency);
            requestBody.put("isOngoing", isStillTaking);

            if (notes != null && !notes.isEmpty()) {
                requestBody.put("notes", notes);
            }

            // Parse and format dates
            SimpleDateFormat inputFormat = new SimpleDateFormat("MM/dd/yyyy", Locale.US);
            SimpleDateFormat outputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
            outputFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

            if (!startDateString.isEmpty()) {
                try {
                    Date startDate = inputFormat.parse(startDateString);
                    requestBody.put("startDate", outputFormat.format(startDate));
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing start date", e);
                }
            }

            if (!isStillTaking && !endDateString.isEmpty()) {
                try {
                    Date endDate = inputFormat.parse(endDateString);
                    requestBody.put("endDate", outputFormat.format(endDate));
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing end date", e);
                }
            } else if (isStillTaking) {
                // Resuming a previously discontinued medication: clear the server-side endDate
                // so the pre-save hook doesn't flip isOngoing back to false on next read.
                requestBody.put("endDate", JSONObject.NULL);
            }

            // Add new optional fields
            if (purpose != null && !purpose.isEmpty()) {
                requestBody.put("purpose", purpose);
            }
            if (prescribedBy != null && !prescribedBy.isEmpty()) {
                requestBody.put("prescribedBy", prescribedBy);
            }
            if (medicationType != null && !medicationType.isEmpty()) {
                requestBody.put("medicationType", medicationType);
            }
            if (administrationMethod != null && !administrationMethod.isEmpty()) {
                requestBody.put("administrationMethod", administrationMethod);
            }
            requestBody.put("shareWithFamily", shareWithFamily);

            // Reminder fields (backend: remindersEnabled / reminderDays / reminderTimes)
            requestBody.put("remindersEnabled", remindersEnabled);

            JSONArray reminderDaysJson = new JSONArray();
            if (reminderDays != null) {
                for (int d : reminderDays) reminderDaysJson.put(d);
            }
            requestBody.put("reminderDays", reminderDaysJson);

            JSONArray reminderTimesJson = new JSONArray();
            if (reminderTimes != null) {
                for (int[] hm : reminderTimes) {
                    if (hm == null || hm.length < 2) continue;
                    JSONObject t = new JSONObject();
                    t.put("hour", hm[0]);
                    t.put("minute", hm[1]);
                    reminderTimesJson.put(t);
                }
            }
            requestBody.put("reminderTimes", reminderTimesJson);

        } catch (JSONException e) {
            progress.hide();
            Log.e(TAG, "Error creating request body", e);
            Utilities.toast(requireContext(), "Error preparing medication data");
            return;
        }

        String finalUrl = url;
        String finalUrl1 = url;
        JsonObjectRequest request = new JsonObjectRequest(httpMethod, url, requestBody,
                response -> {
                    ApiConfig.logRestCall(finalUrl, true, isUpdate ? "Medication updated" : "Medication saved");
                    progress.hide();
                    String successMessage = isUpdate ? "Medication updated successfully" : "Medication saved successfully";
                    Utilities.toast(requireContext(), successMessage);

                    // Schedule (or clear) local reminders from the saved state. Reminders
                    // only make sense while the medication is ongoing.
                    boolean effectiveEnabled = remindersEnabled && isStillTaking;
                    String reminderServerId;
                    if (isUpdate && existingMedication != null) {
                        reminderServerId = existingMedication.getServerId();
                    } else {
                        JSONObject medObj = response.optJSONObject("medication");
                        String parsedId = medObj != null ? medObj.optString("_id", null) : null;
                        if (parsedId == null || parsedId.isEmpty()) {
                            parsedId = response.optString("_id", null);
                        }
                        reminderServerId = parsedId;
                    }
                    if (reminderServerId != null && !reminderServerId.isEmpty()) {
                        Utils.MedicationReminderHelper.setForMedication(context, reminderServerId,
                                name, dosage, effectiveEnabled, reminderDays, reminderTimes);
                    }

                    if (isUpdate && existingMedication != null) {
                        // Update the existing medication object with new values
                        existingMedication.setName(name);
                        existingMedication.setDosage(dosage);
                        existingMedication.setFrequency(frequency);
                        existingMedication.setActive(isStillTaking);
                        existingMedication.setNotes(notes);
                        existingMedication.setShareWithFamily(shareWithFamily);
                        existingMedication.setRemindersEnabled(remindersEnabled);
                        java.util.List<Integer> updDays = new ArrayList<>();
                        if (reminderDays != null) for (int d : reminderDays) updDays.add(d);
                        existingMedication.setReminderDays(updDays);
                        java.util.List<int[]> updTimes = new ArrayList<>();
                        if (reminderTimes != null) for (int[] hm : reminderTimes) {
                            if (hm != null && hm.length >= 2) updTimes.add(new int[]{hm[0], hm[1]});
                        }
                        existingMedication.setReminderTimes(updTimes);

                        // Parse and set dates
                        SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy", Locale.US);
                        try {
                            if (!startDateString.isEmpty()) {
                                existingMedication.setStartDate(dateFormat.parse(startDateString));
                            }
                            if (!isStillTaking && !endDateString.isEmpty()) {
                                existingMedication.setEndDate(dateFormat.parse(endDateString));
                            } else if (isStillTaking) {
                                existingMedication.setEndDate(null);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing dates after update", e);
                        }

                        updateMedication(existingMedication);
                    } else {
                        // For new medications, refresh the list
                        loadMedications();
                    }
                },
                error -> {
                    ApiConfig.logRestCall(finalUrl1, false, error.toString());
                    progress.hide();
                    String errorMessage = "Failed to save medication";

                    if (error.networkResponse != null) {
                        int statusCode = error.networkResponse.statusCode;

                        if (statusCode == 404) {
                            errorMessage = "Medication service not available. Please try again later.";
                        } else if (statusCode == 401) {
                            errorMessage = "Authentication failed. Please login again.";
                        } else if (statusCode >= 500) {
                            errorMessage = "Server error. Please try again later.";
                        } else if (error.networkResponse.data != null) {
                            try {
                                String errorData = new String(error.networkResponse.data, StandardCharsets.UTF_8);

                                if (errorData.trim().startsWith("{")) {
                                    JSONObject errorJson = new JSONObject(errorData);

                                    if (errorJson.has("message")) {
                                        errorMessage = errorJson.getString("message");

                                        if (errorMessage.contains("validation failed") && errorJson.has("error")) {
                                            String detailedError = errorJson.getString("error");
                                            if (detailedError.contains("frequency") && detailedError.contains("not a valid enum")) {
                                                errorMessage = "Please select a valid frequency from the dropdown";
                                            } else if (detailedError.contains("End date must be after start date")) {
                                                errorMessage = "End date must be after start date";
                                            } else if (detailedError.contains("validation failed")) {
                                                errorMessage = "Please check your input fields";
                                            }
                                        }
                                    }
                                } else {
                                    errorMessage = "Service temporarily unavailable. Please try again.";
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error parsing error response", e);
                                errorMessage = "Network error. Please check your connection.";
                            }
                        }
                    } else {
                        errorMessage = "Network error. Please check your connection.";
                    }

                    Utilities.toastLong(requireContext(), errorMessage);
                }
        ) {
            @Override
            public String getBodyContentType() {
                return "application/json; charset=utf-8";
            }

            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + token);
                return headers;
            }
        };

        RequestQueue queue = Volley.newRequestQueue(context);
        queue.add(request);
    }


    // ─── Reminder UI (medication reminders) ─────────────────────────────────────

    private static final String CAD_DAILY      = "Every day";
    private static final String CAD_TWICE_DAY  = "Twice a day";
    private static final String CAD_THREE_DAY  = "Three times a day";
    private static final String CAD_TWICE_WEEK = "Twice a week";
    private static final String[] REMINDER_CADENCES =
            { CAD_DAILY, CAD_TWICE_DAY, CAD_THREE_DAY, CAD_TWICE_WEEK };
    private static final String[] WEEKDAY_LABELS = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};

    /** Holds the reminder section views + editable state for one open dialog. */
    private static class ReminderUiState {
        SwitchMaterial switchReminders;
        View section;
        AutoCompleteTextView cadenceDropdown;
        TextView daysLabel;
        ChipGroup chipGroupDays;
        LinearLayout timesContainer;
        final List<int[]> times = new ArrayList<>();   // each {hour,minute}

        boolean isReady() {
            return switchReminders != null && section != null && cadenceDropdown != null
                    && chipGroupDays != null && timesContainer != null;
        }
    }

    private ReminderUiState setupReminderSection(Dialog dialog, MedicationModel existing) {
        ReminderUiState st = new ReminderUiState();
        st.switchReminders = dialog.findViewById(R.id.switch_reminders);
        st.section         = dialog.findViewById(R.id.reminders_section);
        st.cadenceDropdown = dialog.findViewById(R.id.spinner_reminder_cadence);
        st.daysLabel       = dialog.findViewById(R.id.reminder_days_label);
        st.chipGroupDays   = dialog.findViewById(R.id.chip_group_days);
        st.timesContainer  = dialog.findViewById(R.id.reminder_times_container);

        if (!st.isReady()) return st; // layout missing — degrade gracefully

        ArrayAdapter<String> cadenceAdapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_dropdown_item_1line, REMINDER_CADENCES);
        st.cadenceDropdown.setAdapter(cadenceAdapter);

        buildWeekdayChips(st);

        st.switchReminders.setOnCheckedChangeListener((b, checked) ->
                st.section.setVisibility(checked ? View.VISIBLE : View.GONE));

        st.cadenceDropdown.setOnItemClickListener((parent, view, position, id) ->
                applyCadence(st, REMINDER_CADENCES[position], true, null));

        boolean enabled = existing != null && existing.isRemindersEnabled();
        st.switchReminders.setChecked(enabled);
        st.section.setVisibility(enabled ? View.VISIBLE : View.GONE);

        if (enabled) {
            String cadence = inferCadence(existing);
            st.cadenceDropdown.setText(cadence, false);
            applyCadence(st, cadence, false, existing);
        } else {
            st.cadenceDropdown.setText(CAD_DAILY, false);
            applyCadence(st, CAD_DAILY, true, null);
        }
        return st;
    }

    private void buildWeekdayChips(ReminderUiState st) {
        st.chipGroupDays.removeAllViews();
        float density = getResources().getDisplayMetrics().density;
        for (int i = 0; i < 7; i++) {
            Chip chip = new Chip(requireContext());
            chip.setText(WEEKDAY_LABELS[i]);
            chip.setTag(i);
            chip.setCheckable(true);
            chip.setTextColor(Color.WHITE);
            chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.rh_accent_dim)));
            chip.setChipStrokeColor(android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.rh_accent)));
            chip.setChipStrokeWidth(density);
            st.chipGroupDays.addView(chip);
        }
    }

    /**
     * Apply a cadence: toggle the weekday selector and, when {@code recompute}, auto-space
     * the times evenly across 24h from the first time. When !recompute the times/days are
     * taken from the existing medication (prefill).
     */
    private void applyCadence(ReminderUiState st, String cadence, boolean recompute, MedicationModel existing) {
        boolean weekly = CAD_TWICE_WEEK.equals(cadence);
        st.daysLabel.setVisibility(weekly ? View.VISIBLE : View.GONE);
        st.chipGroupDays.setVisibility(weekly ? View.VISIBLE : View.GONE);

        int count = timesCountFor(cadence);

        if (!recompute && existing != null && existing.getReminderTimes() != null
                && !existing.getReminderTimes().isEmpty()) {
            st.times.clear();
            for (int[] hm : existing.getReminderTimes()) {
                if (hm != null && hm.length >= 2) st.times.add(new int[]{hm[0], hm[1]});
            }
            while (st.times.size() < count) st.times.add(new int[]{8, 0});
            while (st.times.size() > count) st.times.remove(st.times.size() - 1);
            if (weekly) applyExistingDays(st, existing);
        } else {
            int[] first = st.times.isEmpty() ? new int[]{8, 0} : st.times.get(0);
            st.times.clear();
            int step = 24 * 60 / count;
            int firstMinutes = first[0] * 60 + first[1];
            for (int k = 0; k < count; k++) {
                int m = (firstMinutes + k * step) % (24 * 60);
                st.times.add(new int[]{m / 60, m % 60});
            }
        }

        renderTimeRows(st);
    }

    private void applyExistingDays(ReminderUiState st, MedicationModel existing) {
        List<Integer> days = existing.getReminderDays();
        for (int i = 0; i < st.chipGroupDays.getChildCount(); i++) {
            View v = st.chipGroupDays.getChildAt(i);
            if (v instanceof Chip) {
                Integer tag = (Integer) v.getTag();
                ((Chip) v).setChecked(days != null && tag != null && days.contains(tag));
            }
        }
    }

    private int timesCountFor(String cadence) {
        if (CAD_TWICE_DAY.equals(cadence)) return 2;
        if (CAD_THREE_DAY.equals(cadence)) return 3;
        return 1; // Every day, Twice a week
    }

    private String inferCadence(MedicationModel med) {
        List<Integer> days = med.getReminderDays();
        if (days != null && !days.isEmpty()) return CAD_TWICE_WEEK;
        int n = med.getReminderTimes() != null ? med.getReminderTimes().size() : 1;
        if (n >= 3) return CAD_THREE_DAY;
        if (n == 2) return CAD_TWICE_DAY;
        return CAD_DAILY;
    }

    private void renderTimeRows(ReminderUiState st) {
        st.timesContainer.removeAllViews();
        for (int idx = 0; idx < st.times.size(); idx++) {
            final int rowIndex = idx;
            MaterialButton row = new MaterialButton(requireContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = (int) (8 * getResources().getDisplayMetrics().density);
            row.setLayoutParams(lp);
            row.setAllCaps(false);
            row.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            row.setTextColor(Color.WHITE);
            row.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    Color.parseColor("#1E1E1E")));
            row.setText(formatTime(st.times.get(rowIndex)));
            row.setIconResource(R.drawable.ic_date);
            row.setIconTint(android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.rh_accent)));
            row.setOnClickListener(v -> {
                int[] hm = st.times.get(rowIndex);
                TimePickerDialog tp = new TimePickerDialog(requireContext(),
                        (view, hourOfDay, minute) -> {
                            st.times.set(rowIndex, new int[]{hourOfDay, minute});
                            row.setText(formatTime(st.times.get(rowIndex)));
                        }, hm[0], hm[1], false);
                tp.show();
            });
            st.timesContainer.addView(row);
        }
    }

    private String formatTime(int[] hm) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, hm[0]);
        c.set(Calendar.MINUTE, hm[1]);
        return new SimpleDateFormat("h:mm a", Locale.US).format(c.getTime());
    }

    /** Selected weekdays (0=Sun..6=Sat) from the chip group. */
    private int[] readSelectedDays(ReminderUiState st) {
        List<Integer> sel = new ArrayList<>();
        for (int i = 0; i < st.chipGroupDays.getChildCount(); i++) {
            View v = st.chipGroupDays.getChildAt(i);
            if (v instanceof Chip && ((Chip) v).isChecked()) {
                Integer tag = (Integer) v.getTag();
                if (tag != null) sel.add(tag);
            }
        }
        int[] out = new int[sel.size()];
        for (int i = 0; i < sel.size(); i++) out[i] = sel.get(i);
        return out;
    }

    private int[][] readTimes(ReminderUiState st) {
        int[][] out = new int[st.times.size()][];
        for (int i = 0; i < st.times.size(); i++) out[i] = st.times.get(i);
        return out;
    }

    private void showDatePickerDialog(final TextInputEditText dateInput) {
        showDatePickerDialog((TextView) dateInput);
    }

    private void showDatePickerDialog(final TextView dateInput) {
        // Get current date as default
        final java.util.Calendar calendar = java.util.Calendar.getInstance();
        int year = calendar.get(java.util.Calendar.YEAR);
        int month = calendar.get(java.util.Calendar.MONTH);
        int day = calendar.get(java.util.Calendar.DAY_OF_MONTH);

        // Create date picker dialog
        android.app.DatePickerDialog datePickerDialog = new android.app.DatePickerDialog(
                requireContext(),
                (view, selectedYear, selectedMonth, selectedDay) -> {
                    // Format the date and set it to the input field
                    String formattedDate = String.format(java.util.Locale.US, "%02d/%02d/%04d",
                            selectedMonth + 1, selectedDay, selectedYear);
                    dateInput.setText(formattedDate);
                },
                year, month, day);

        datePickerDialog.show();
    }

    private void showAddFamilyMemberDialog() {
        Dialog dialog = new Dialog(requireContext(), R.style.DialogTheme);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_add_family_member);

        // App-standard dialog window (92% width, wrap height, bounded scroll).
        Utils.DialogUtils.applyStandardEditDialogWindow(dialog);

        EditText emailInput = dialog.findViewById(R.id.email_input);
        AutoCompleteTextView relationshipDropdown = dialog.findViewById(R.id.relationship_dropdown);
        Button cancelButton = dialog.findViewById(R.id.cancel_button);
        Button sendButton = dialog.findViewById(R.id.send_button);

        // Setup relationship dropdown
        String[] relationships = new String[] {
                "Father", "Mother", "Brother", "Sister",
                "Grandfather", "Grandmother",
                "Paternal Uncle", "Paternal Aunt",
                "Maternal Uncle", "Maternal Aunt",
                "Son", "Daughter", "Grandson", "Granddaughter",
                "Nephew", "Niece"
        };

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                relationships
        );
        relationshipDropdown.setAdapter(adapter);

        // Setup buttons
        cancelButton.setOnClickListener(v -> dialog.dismiss());

        sendButton.setOnClickListener(v -> {
            String email = emailInput.getText().toString().trim();
            String relationship = relationshipDropdown.getText().toString().trim();

            if (email.isEmpty()) {
                Utilities.toast(requireContext(), "Please enter an email");
                return;
            }

            if (relationship.isEmpty()) {
                Utilities.toast(requireContext(), "Please select a relationship");
                return;
            }

            Utilities.toast(requireContext(), "Sending request...");
            sendFamilyRelationshipRequest(email, relationship);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void sendFamilyRelationshipRequest(String email, String relationship) {
        Context context = getContext();
        if (context == null) return;

        TokenManager tokenManager = TokenManager.getInstance(context);
        String token = tokenManager.getToken();

        if (token == null) {
            Utilities.toast(context, "Authentication error");
            return;
        }

        String url = ApiConfig.BASE_URL + "/api/user/relationship/request";

        JSONObject requestBody = new JSONObject();
        try {
            requestBody.put("email", email);
            requestBody.put("relationship", relationship);
        } catch (JSONException e) {
            Log.e(TAG, "Error creating request body", e);
            return;
        }

        StringRequest request = new StringRequest(Request.Method.POST, url,
                response -> {
                    ApiConfig.logRestCall(url, true, "Family relationship request sent");
                    Utilities.toast(requireContext(), "Request sent successfully");
                    fetchFamilyRelationships();
                },
                error -> {
                    ApiConfig.logRestCall(url, false, error.toString());
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

        RequestQueue queue = Volley.newRequestQueue(context);
        queue.add(request);
    }

    private void fetchFamilyRelationships() {
        Context context = getContext();
        if (context == null) return;

        TokenManager tokenManager = TokenManager.getInstance(context);
        String token = tokenManager.getToken();

        if (token == null) {
            return;
        }

        String url = ApiConfig.BASE_URL + "/api/users/relationships";

        StringRequest request = new StringRequest(Request.Method.GET, url,
                response -> {
                    ApiConfig.logRestCall(url, true, "Family relationships list fetched");
                    try {
                        JSONObject jsonResponse = new JSONObject(response);
                        JSONArray relationshipsArray = jsonResponse.getJSONArray("relationships");
                        Log.d(TAG, "fetchFamilyRelationships() -> Data fetch success with " + relationshipsArray.length() + " relationships");

                        familyRelationships.clear();

                        for (int i = 0; i < relationshipsArray.length(); i++) {
                            JSONObject relationshipObj = relationshipsArray.getJSONObject(i);

                            UserProfile.RelationshipRequest relationship = new UserProfile.RelationshipRequest(
                                    relationshipObj.getString("email"),
                                    relationshipObj.getString("relationship"),
                                    relationshipObj.getString("status")
                            );

                            if (relationshipObj.has("name") && !relationshipObj.isNull("name")) {
                                relationship.setName(relationshipObj.getString("name"));
                            }
                            if (relationshipObj.has("userId") && !relationshipObj.isNull("userId")) {
                                relationship.setUserId(relationshipObj.getString("userId"));
                            }
                            relationship.setPro(relationshipObj.optBoolean("isPro", false));
                            relationship.setProSource(relationshipObj.optString("proSource", "none"));
                            relationship.setCoveredByMyPlan(relationshipObj.optBoolean("isCoveredByMyPlan", false));

                            familyRelationships.add(relationship);
                        }

                        relationshipAdapter.notifyDataSetChanged();
                        updateFamilyMembersEmptyState();

                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing family relationships response", e);
                    }
                },
                error -> {
                    ApiConfig.logRestCall(url, false, error.toString());
                    Log.e(TAG, "Error fetching family relationships", error);
                    Utilities.toast(requireContext(), "Failed to load family connections");
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + token);
                return headers;
            }
        };

        RequestQueue queue = Volley.newRequestQueue(context);
        queue.add(request);
    }

    private void handleRemoveFamilyMember(String memberId, int position) {
        Utils.DialogUtils.showConfirmDialog(requireContext(),
                "Remove from Pro",
                "Remove this member from your family pro plan?",
                "Remove", "Cancel", true,
                () -> {
                    Utilities.toast(requireContext(), "Removing member...");
                    PaymentService paymentService = new PaymentService(requireContext());
                    paymentService.removeFamilyMember(memberId, new PaymentService.PaymentCallback() {
                        @Override
                        public void onSuccess(ProStatusResult result) {
                            Utilities.toast(requireContext(), "Member removed from pro plan");
                            showFamilyMembersPanel();
                        }

                        @Override
                        public void onError(String errorMessage) {
                            Utilities.toast(requireContext(), "Failed: " + errorMessage);
                        }
                    });
                });
    }

    private void handleAddFamilyMemberToPro(String memberId, int position) {
        Utils.DialogUtils.showConfirmDialog(requireContext(),
                "Add to Pro Plan",
                "Add this family member to your pro plan? Family members are included in your Ultra plan.",
                "Add Member", "Cancel", false,
                () -> {
                    PaymentManager paymentManager = new PaymentManager(requireContext());
                    if (requireActivity() instanceof MainActivity) {
                        ((MainActivity) requireActivity()).setPaymentManager(paymentManager);
                    }
                    paymentManager.addFamilyMemberDirect(requireActivity(), memberId,
                            new PaymentManager.PaymentCallback() {
                                @Override
                                public void onPaymentInitiated() { }

                                @Override
                                public void onPaymentSuccess(String plan) {
                                    Utilities.toast(requireContext(), "Family member added to pro plan!");
                                    showFamilyMembersPanel();
                                }

                                @Override
                                public void onPaymentFailed(String reason) {
                                    Utilities.toast(requireContext(), "Failed: " + reason);
                                }

                                @Override
                                public void onPaymentCancelled() {
                                    Utilities.toast(requireContext(), "Cancelled");
                                }
                            });
                });
    }

    private void showEditRelationshipDialog(UserProfile.RelationshipRequest relationship, int position) {
        Dialog dialog = new Dialog(requireContext(), R.style.DialogTheme);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_add_family_member);

        // App-standard dialog window (92% width, wrap height, bounded scroll).
        Utils.DialogUtils.applyStandardEditDialogWindow(dialog);

        EditText emailInput = dialog.findViewById(R.id.email_input);
        AutoCompleteTextView relationshipDropdown = dialog.findViewById(R.id.relationship_dropdown);
        Button cancelButton = dialog.findViewById(R.id.cancel_button);
        Button sendButton = dialog.findViewById(R.id.send_button);

        emailInput.setText(relationship.getEmail());
        emailInput.setEnabled(false);
        emailInput.setAlpha(0.6f);

        String[] relationships = new String[] {
                "Father", "Mother", "Brother", "Sister",
                "Grandfather", "Grandmother",
                "Paternal Uncle", "Paternal Aunt",
                "Maternal Uncle", "Maternal Aunt",
                "Son", "Daughter", "Grandson", "Granddaughter",
                "Nephew", "Niece", "Cousin", "Spouse"
        };

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                relationships
        );
        relationshipDropdown.setAdapter(adapter);
        relationshipDropdown.setText(relationship.getRelationship(), false);

        TextView titleView = dialog.findViewById(R.id.dialog_title);
        if (titleView != null) {
            titleView.setText("Edit Relationship");
        }
        sendButton.setText("Save");

        cancelButton.setOnClickListener(v -> dialog.dismiss());

        sendButton.setOnClickListener(v -> {
            String newRelationship = relationshipDropdown.getText().toString().trim();
            if (newRelationship.isEmpty()) {
                Utilities.toast(requireContext(), "Please select a relationship");
                return;
            }
            if (newRelationship.equals(relationship.getRelationship())) {
                dialog.dismiss();
                return;
            }
            dialog.dismiss();
            editRelationship(relationship.getUserId(), newRelationship, position);
        });

        dialog.show();
    }

    private void editRelationship(String relativeUserId, String newRelationship, int position) {
        Context context = getContext();
        if (context == null || relativeUserId == null) return;

        TokenManager tokenManager = TokenManager.getInstance(context);
        String token = tokenManager.getToken();
        if (token == null) {
            Utilities.toast(context, "Authentication error");
            return;
        }

        Utilities.toast(context, "Updating relationship...");

        String url = ApiConfig.BASE_URL + "/api/user/relationship/edit";

        JSONObject requestBody = new JSONObject();
        try {
            requestBody.put("relativeUserId", relativeUserId);
            requestBody.put("relationship", newRelationship);
        } catch (JSONException e) {
            Log.e(TAG, "Error creating edit request body", e);
            return;
        }

        StringRequest request = new StringRequest(Request.Method.PUT, url,
                response -> {
                    Utilities.toast(requireContext(), "Relationship updated");
                    if (position >= 0 && position < familyRelationships.size()) {
                        familyRelationships.get(position).setRelationship(newRelationship);
                        relationshipAdapter.notifyItemChanged(position);
                    }
                },
                error -> {
                    Log.e(TAG, "Error editing relationship", error);
                    String errorMsg = "Failed to update relationship";
                    if (error.networkResponse != null && error.networkResponse.data != null) {
                        try {
                            JSONObject errJson = new JSONObject(new String(error.networkResponse.data, StandardCharsets.UTF_8));
                            if (errJson.has("message")) errorMsg = errJson.getString("message");
                        } catch (Exception ignored) {}
                    }
                    Utilities.toast(requireContext(), errorMsg);
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

        Volley.newRequestQueue(context).add(request);
    }

    private void confirmDeleteRelationship(UserProfile.RelationshipRequest relationship, int position) {
        String displayName = relationship.getName();
        if (displayName == null || displayName.isEmpty()) displayName = relationship.getEmail();

        Utils.DialogUtils.showConfirmDialog(requireContext(),
                "Remove Family Member",
                "Remove " + displayName + " from your family connections?\n\nIf they are covered by your pro plan, their access will be revoked.",
                "Remove", "Cancel", true,
                () -> deleteRelationship(relationship.getUserId(), position));
    }

    private void deleteRelationship(String relativeUserId, int position) {
        Context context = getContext();
        if (context == null || relativeUserId == null) return;

        TokenManager tokenManager = TokenManager.getInstance(context);
        String token = tokenManager.getToken();
        if (token == null) {
            Utilities.toast(context, "Authentication error");
            return;
        }

        Utilities.toast(context, "Removing member...");

        String url = ApiConfig.BASE_URL + "/api/user/relationship/delete";

        JSONObject requestBody = new JSONObject();
        try {
            requestBody.put("relativeUserId", relativeUserId);
        } catch (JSONException e) {
            Log.e(TAG, "Error creating delete request body", e);
            return;
        }

        StringRequest request = new StringRequest(Request.Method.POST, url,
                response -> {
                    ApiConfig.logRestCall(url, true, "Relationship deleted");
                    Utilities.toast(requireContext(), "Member removed");
                    if (position >= 0 && position < familyRelationships.size()) {
                        familyRelationships.remove(position);
                        relationshipAdapter.notifyItemRemoved(position);
                        relationshipAdapter.notifyItemRangeChanged(position, familyRelationships.size());
                        updateFamilyMembersEmptyState();
                    }
                },
                error -> {
                    ApiConfig.logRestCall(url, false, error.toString());
                    Log.e(TAG, "Error deleting relationship", error);
                    String errorMsg = "Failed to remove member";
                    if (error.networkResponse != null && error.networkResponse.data != null) {
                        try {
                            JSONObject errJson = new JSONObject(new String(error.networkResponse.data, StandardCharsets.UTF_8));
                            if (errJson.has("message")) errorMsg = errJson.getString("message");
                        } catch (Exception ignored) {}
                    }
                    Utilities.toast(requireContext(), errorMsg);
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

        Volley.newRequestQueue(context).add(request);
    }

    private void confirmCancelRequest(UserProfile.RelationshipRequest relationship, int position) {
        Utils.DialogUtils.showConfirmDialog(requireContext(),
                "Cancel Request",
                "Cancel the pending request to " + relationship.getEmail() + "?",
                "Yes, Cancel", "No", true,
                () -> cancelRelationshipRequest(relationship.getEmail(), position));
    }

    private void cancelRelationshipRequest(String email, int position) {
        Context context = getContext();
        if (context == null) return;

        TokenManager tokenManager = TokenManager.getInstance(context);
        String token = tokenManager.getToken();
        if (token == null) {
            Utilities.toast(context, "Authentication error");
            return;
        }

        Utilities.toast(context, "Cancelling request...");

        String url = ApiConfig.BASE_URL + "/api/user/relationship/cancel";

        JSONObject requestBody = new JSONObject();
        try {
            requestBody.put("email", email);
        } catch (JSONException e) {
            Log.e(TAG, "Error creating cancel request body", e);
            return;
        }

        StringRequest request = new StringRequest(Request.Method.POST, url,
                response -> {
                    ApiConfig.logRestCall(url, true, "Relationship request cancelled");
                    Utilities.toast(requireContext(), "Request cancelled");
                    if (position >= 0 && position < familyRelationships.size()) {
                        familyRelationships.remove(position);
                        relationshipAdapter.notifyItemRemoved(position);
                        relationshipAdapter.notifyItemRangeChanged(position, familyRelationships.size());
                        updateFamilyMembersEmptyState();
                    }
                },
                error -> {
                    ApiConfig.logRestCall(url, false, error.toString());
                    Log.e(TAG, "Error cancelling request", error);
                    String errorMsg = "Failed to cancel request";
                    if (error.networkResponse != null && error.networkResponse.data != null) {
                        try {
                            JSONObject errJson = new JSONObject(new String(error.networkResponse.data, StandardCharsets.UTF_8));
                            if (errJson.has("message")) errorMsg = errJson.getString("message");
                        } catch (Exception ignored) {}
                    }
                    Utilities.toast(requireContext(), errorMsg);
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

        Volley.newRequestQueue(context).add(request);
    }

    /**
     * Plan pill — replaces the notification button. Reflects the user's tier
     * (FREE / PRO / PLUS / ULTRA) and opens the upgrade/management dialog on tap.
     */
    private void setupPlanPill() {
        if (planPill == null) return;
        refreshPlanPill();
        planPill.setOnClickListener(v -> {
            // Subtle press feedback before opening the dialog.
            v.animate()
                    .scaleX(0.92f).scaleY(0.92f)
                    .setDuration(90)
                    .withEndAction(() -> v.animate()
                            .scaleX(1f).scaleY(1f)
                            .setDuration(140)
                            .setInterpolator(new android.view.animation.OvershootInterpolator(2.5f))
                            .start())
                    .start();
            showProUpgradeDialog();
        });
    }

    private void refreshPlanPill() {
        if (planPill == null || planPillText == null || planPillIcon == null) return;
        boolean isPro = proStatusManager != null && proStatusManager.isProUser();
        if (isPro) {
            String tier = proStatusManager.getUserTier();
            // Consistent plan name (e.g. "Family Pro", not "FAMILY_MEMBER") via PlanBadge.
            planPillText.setText(tier != null && !tier.isEmpty()
                    ? Utils.PlanBadge.compactLabelFor(tier) : "Pro");
            planPillText.setTextColor(0xFFFFB300);
            planPillIcon.setColorFilter(0xFFFFB300);
            planPill.setBackgroundResource(R.drawable.bg_pill_plan_pro);
        } else {
            planPillText.setText("FREE");
            planPillText.setTextColor(0xFF008b8b);
            planPillIcon.setColorFilter(0xFF008b8b);
            planPill.setBackgroundResource(R.drawable.bg_pill_plan_free);
        }
    }

    /**
     * Wires the small (i) info icon on each card to a CardInfoDialog,
     * with a 360° rotation on tap as a tactile cue.
     */
    private void setupCardInfoDialogs(View view) {
        bindInfoIcon(view, R.id.symptoms_info,
                "Symptoms", "Daily Tracking", R.drawable.ic_sick,
                "Capture how you feel — when, where and how intense — so patterns emerge over time.",
                new String[] {
                        "Log discomfort with severity, body area and notes",
                        "Spot recurring patterns and triggers",
                        "Share a clean timeline with your doctor in seconds"
                });

        bindInfoIcon(view, R.id.measurements_info,
                "Measurements", "Vitals & Metrics", R.drawable.ic_ruler,
                "Track the numbers that matter — blood pressure, weight, glucose, oxygen and more — in one trusted place.",
                new String[] {
                        "Log readings manually or sync from devices",
                        "See trends over weeks and months",
                        "Get gentle alerts when something looks off"
                });

        bindInfoIcon(view, R.id.medical_reports_info,
                "Medical Reports", "Records Vault", R.drawable.ic_doc,
                "A private vault for everything from lab results to imaging — accessible from anywhere, secure by default.",
                new String[] {
                        "Upload PDFs, photos and scans",
                        "AI-assisted summary of key findings",
                        "Carry your full medical record on your phone"
                });

        bindInfoIcon(view, R.id.medications_info,
                "Medications", "Prescriptions", R.drawable.ic_pill,
                "Stay on top of every pill, dose and refill — and keep a clear record of what you've taken and when.",
                new String[] {
                        "Track active prescriptions and dosage",
                        "Never miss a refill",
                        "Hand a clean medication list to any doctor"
                });

        bindInfoIcon(view, R.id.family_info,
                "Family Health", "Genetics & Shared Care", R.drawable.ic_family_group,
                "Some health risks run in the family. Add relatives to capture genetic context and coordinate care.",
                new String[] {
                        "Record family medical history",
                        "Manage care for dependents",
                        "Share access with trusted family members"
                });

        bindInfoIcon(view, R.id.period_info,
                "Period History", "Cycle Tracking", R.drawable.ic_gynecology,
                "A discreet, accurate cycle log — predictions, symptoms and notes, all kept private to you.",
                new String[] {
                        "Log periods, flow and symptoms",
                        "See predictions and cycle insights",
                        "Tie cycle data to other health metrics"
                });
    }

    private void bindInfoIcon(View root, int id, String title, String subtitle,
                              int iconRes, String body, String[] bullets) {
        ImageView icon = root.findViewById(id);
        if (icon == null) return;
        icon.setOnClickListener(v -> {
            // Rotate the (i) as a tactile cue.
            v.animate()
                    .rotationBy(360f)
                    .setDuration(420)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
            new Utils.CardInfoDialog.Builder(requireContext())
                    .title(title)
                    .subtitle(subtitle)
                    .icon(iconRes)
                    .body(body)
                    .bullets(bullets)
                    .build()
                    .show();
        });
    }

    /**
     * Stagger-fade cards in on first display. Cheap, runs once per fragment instance.
     */
    private void animateCardsEntry(View view) {
        int[] cardIds = new int[] {
                R.id.symptoms_card,
                R.id.measurements_card,
                R.id.period_history_card,
                R.id.medical_reports_card,
                R.id.medications_card,
                R.id.add_relative_card
        };
        long delay = 0;
        for (int id : cardIds) {
            View card = view.findViewById(id);
            if (card == null || card.getVisibility() == View.GONE) continue;
            card.setAlpha(0f);
            card.setTranslationY(24f);
            card.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(delay)
                    .setDuration(360)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
            delay += 55;
        }
    }

    private void attachAddButtonPulse(View btn) {
        if (btn == null) return;
        btn.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    // Subtle press-scale for the whole card (the Material ripple does the rest).
                    v.animate().scaleX(0.98f).scaleY(0.98f).setDuration(90).start();
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1f).scaleY(1f)
                            .setDuration(180)
                            .setInterpolator(new android.view.animation.OvershootInterpolator(3f))
                            .start();
                    break;
            }
            return false; // let click fire normally
        });
    }

    private void showAddSymptomDialog() {
        Dialog dialog = new Dialog(requireContext(), R.style.DialogTheme);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_add_symptom);

        // App-standard dialog window (92% width, wrap height, bounded scroll).
        Utils.DialogUtils.applyStandardEditDialogWindow(dialog);

        EditText nameInput = dialog.findViewById(R.id.name_input);
        SeekBar severitySeekBar = dialog.findViewById(R.id.severity_seekbar);
        TextView severityText = dialog.findViewById(R.id.severity_text);
        EditText durationInput = dialog.findViewById(R.id.duration_input);
        EditText descriptionInput = dialog.findViewById(R.id.description_input);
        Button dateButton = dialog.findViewById(R.id.date_button);
        Button timeButton = dialog.findViewById(R.id.time_button);
        SwitchMaterial shareSwitch = dialog.findViewById(R.id.share_with_family_switch);
        Button cancelButton = dialog.findViewById(R.id.cancel_button);
        Button saveButton = dialog.findViewById(R.id.save_button);

        // Date & time default to now, but can be changed so past symptoms can be logged.
        selectedDateTime = Calendar.getInstance();
        updateDateTimeButtons(dateButton, timeButton);
        wireDateTimeButtons(dateButton, timeButton);

        // Setup severity seekbar
        String[] severityLabels = {"Very Mild", "Mild", "Moderate", "Severe", "Very Severe"};
        severitySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                severityText.setText(severityLabels[progress]);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        cancelButton.setOnClickListener(v -> dialog.dismiss());

        saveButton.setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            int severity = severitySeekBar.getProgress() + 1;
            String duration = durationInput.getText().toString().trim();
            String description = descriptionInput.getText().toString().trim();

            if (name.isEmpty()) {
                Utilities.toast(requireContext(), "Please enter a symptom name");
                return;
            }

            if (duration.isEmpty()) {
                Utilities.toast(requireContext(), "Please enter a duration");
                return;
            }

            MedicalData.Symptom symptom = new MedicalData.Symptom();
            symptom.setName(name);
            symptom.setSeverity(severity);
            symptom.setDuration(duration);
            symptom.setDescription(description);
            symptom.setRecordedAt(selectedDateTime.getTime());
            symptom.setShareWithFamily(shareSwitch.isChecked());

            if (userProfile != null) {
                symptom.setUserId(userProfile.getId());
            }

            saveSymptom(symptom);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void saveSymptom(MedicalData.Symptom symptom) {
        // First save to local database
        long id = dbHelper.insertSymptom(symptom);
        if (id > 0) {
            symptom.setId(id);

            // Add to adapter and update empty state
            symptomsAdapter.addItem(symptom);
            updateSymptomsEmptyState(false);

            // Show success message (sidebar stays open to show new symptom)
            Utilities.toast(requireContext(), "Symptom added successfully");

            // Save to API
            medicalDataApiService.addSymptom(symptom, new MedicalDataApiService.OnMedicalDataListener() {
                @Override
                public void onSuccess(JSONObject response) {
                    try {
                        // Get the server ID and update local record
                        JSONObject data = response.getJSONObject("data");
                        String serverId = data.getString("_id");
                        symptom.setServerId(serverId);

                        // Update in database
                        dbHelper.updateMedicalData(symptom);

                        // Update medical data count
//                        fetchMedicalDataCount();

                        Log.d(TAG, "Symptom synced with server: " + serverId);
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing symptom response", e);
                    }
                }

                @Override
                public void onError(String errorMessage) {
                    Log.e(TAG, "Error saving symptom to server: " + errorMessage);
                    Utilities.toast(requireContext(), "Saved locally but couldn't sync with server");
                }
            });
        } else {
            Utilities.toast(requireContext(), "Failed to save symptom");
        }
    }

    private void showAddMeasurementDialog() {
        Dialog dialog = new Dialog(requireContext(), R.style.DialogTheme);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_add_measurement);

        // App-standard dialog window (92% width, wrap height, bounded scroll).
        Utils.DialogUtils.applyStandardEditDialogWindow(dialog);

        AutoCompleteTextView typeDropdown = dialog.findViewById(R.id.type_dropdown);
        EditText valueInput = dialog.findViewById(R.id.value_input);
        AutoCompleteTextView unitDropdown = dialog.findViewById(R.id.unit_dropdown);
        EditText notesInput = dialog.findViewById(R.id.notes_input);
        Button dateButton = dialog.findViewById(R.id.date_button);
        Button timeButton = dialog.findViewById(R.id.time_button);
        SwitchMaterial shareSwitch = dialog.findViewById(R.id.share_with_family_switch);
        Button cancelButton = dialog.findViewById(R.id.cancel_button);
        Button saveButton = dialog.findViewById(R.id.save_button);

        // Setup type dropdown
        String[] measurementTypes = {
                "Blood Pressure", "Blood Glucose", "Heart Rate", "Weight",
                "Temperature", "Oxygen Saturation", "Cholesterol", "Thyroid (TSH)"
        };

        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_dropdown_item_1line, measurementTypes
        );
        typeDropdown.setAdapter(typeAdapter);

        // Setup unit dropdown based on selected type
        Map<String, String[]> unitMap = new HashMap<>();
        unitMap.put("Blood Pressure", new String[]{"mmHg"});
        unitMap.put("Blood Glucose", new String[]{"mg/dL", "mmol/L"});
        unitMap.put("Heart Rate", new String[]{"bpm"});
        unitMap.put("Weight", new String[]{"kg", "lbs"});
        unitMap.put("Temperature", new String[]{"°C", "°F"});
        unitMap.put("Oxygen Saturation", new String[]{"%"});
        unitMap.put("Cholesterol", new String[]{"mg/dL", "mmol/L"});
        unitMap.put("Thyroid (TSH)", new String[]{"mIU/L"});

        typeDropdown.setOnItemClickListener((parent, view, position, id) -> {
            String selectedType = typeDropdown.getText().toString();
            String[] units = unitMap.get(selectedType);

            if (units != null) {
                ArrayAdapter<String> unitAdapter = new ArrayAdapter<>(
                        requireContext(), android.R.layout.simple_dropdown_item_1line, units
                );
                unitDropdown.setAdapter(unitAdapter);
                unitDropdown.setText(units[0], false);
            }
        });

        // Initialize date and time
        updateDateTimeButtons(dateButton, timeButton);

        // Setup date button
        dateButton.setOnClickListener(v -> {
            DatePickerDialog datePickerDialog = new DatePickerDialog(
                    requireContext(),
                    (view, year, month, dayOfMonth) -> {
                        selectedDateTime.set(Calendar.YEAR, year);
                        selectedDateTime.set(Calendar.MONTH, month);
                        selectedDateTime.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                        updateDateTimeButtons(dateButton, timeButton);
                    },
                    selectedDateTime.get(Calendar.YEAR),
                    selectedDateTime.get(Calendar.MONTH),
                    selectedDateTime.get(Calendar.DAY_OF_MONTH)
            );
            datePickerDialog.show();
        });

        // Setup time button
        timeButton.setOnClickListener(v -> {
            TimePickerDialog timePickerDialog = new TimePickerDialog(
                    requireContext(),
                    (view, hourOfDay, minute) -> {
                        selectedDateTime.set(Calendar.HOUR_OF_DAY, hourOfDay);
                        selectedDateTime.set(Calendar.MINUTE, minute);
                        updateDateTimeButtons(dateButton, timeButton);
                    },
                    selectedDateTime.get(Calendar.HOUR_OF_DAY),
                    selectedDateTime.get(Calendar.MINUTE),
                    false
            );
            timePickerDialog.show();
        });

        // Setup buttons
        cancelButton.setOnClickListener(v -> dialog.dismiss());

        saveButton.setOnClickListener(v -> {
            String type = typeDropdown.getText().toString().trim();
            String valueStr = valueInput.getText().toString().trim();
            String unit = unitDropdown.getText().toString().trim();
            String notes = notesInput.getText().toString().trim();

            if (type.isEmpty()) {
                Utilities.toast(requireContext(), "Please select a measurement type");
                return;
            }

            if (valueStr.isEmpty()) {
                Utilities.toast(requireContext(), "Please enter a value");
                return;
            }

            if (unit.isEmpty()) {
                Utilities.toast(requireContext(), "Please select a unit");
                return;
            }

            double value;
            try {
                value = Double.parseDouble(valueStr);
            } catch (NumberFormatException e) {
                Utilities.toast(requireContext(), "Please enter a valid number");
                return;
            }

            MedicalData.HealthMetric metric = new MedicalData.HealthMetric();
            metric.setMetricType(type);
            metric.setValue(value);
            metric.setUnit(unit);
            metric.setNotes(notes);
            metric.setRecordedAt(selectedDateTime.getTime());
            metric.setShareWithFamily(shareSwitch.isChecked());

            if (userProfile != null) {
                metric.setUserId(userProfile.getId());
            }

            metric.calculateStatus();

            saveMeasurement(metric);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void saveMeasurement(MedicalData.HealthMetric metric) {
        // First save to local database
        long id = dbHelper.insertHealthMetric(metric);
        if (id > 0) {
            metric.setId(id);

            // Add to adapter
            measurementsAdapter.addItem(metric);

            // Show success message
            Utilities.toast(requireContext(), "Measurement added successfully");

            // Save to API
            medicalDataApiService.addMeasurement(metric, new MedicalDataApiService.OnMedicalDataListener() {
                @Override
                public void onSuccess(JSONObject response) {
                    try {
                        // Get the server ID and update local record
                        JSONObject data = response.getJSONObject("data");
                        String serverId = data.getString("_id");
                        metric.setServerId(serverId);

                        // Update in database
                        dbHelper.updateMedicalData(metric);


                        Log.d(TAG, "Measurement synced with server: " + serverId);
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing measurement response", e);
                    }
                }

                @Override
                public void onError(String errorMessage) {
                    Log.e(TAG, "Error saving measurement to server: " + errorMessage);
                    Utilities.toast(requireContext(), "Saved locally but couldn't sync with server");
                }
            });
        } else {
            Utilities.toast(requireContext(), "Failed to save measurement");
        }
    }


    private MedicalData.HealthMetric parseMetricFromJson(JSONObject json) {
        try {
            MedicalData.HealthMetric metric = new MedicalData.HealthMetric();
            metric.setServerId(json.getString("_id"));
            metric.setMetricType(json.getString("title"));
            metric.setValue(json.getDouble("value"));
            metric.setUnit(json.getString("unit"));

            if (json.has("description")) {
                metric.setNotes(json.getString("description"));
            }

            if (json.has("status")) {
                metric.setStatus(json.getString("status"));
            }

            if (json.has("shareWithFamily")) {
                metric.setShareWithFamily(json.getBoolean("shareWithFamily"));
            }
            if (json.has("includeInChat")) {
                metric.setIncludeInChat(json.getBoolean("includeInChat"));
            }

            if (json.has("dateTime")) {
                String dateTimeStr = json.getString("dateTime");
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault());
                sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                metric.setRecordedAt(sdf.parse(dateTimeStr));
            } else {
                metric.setRecordedAt(new Date());
            }

            if (userProfile != null) {
                metric.setUserId(userProfile.getId());
            }

            return metric;
        } catch (Exception e) {
            Log.e(TAG, "Error parsing metric JSON: " + e.getMessage());
            return null;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // Refresh data if needed
        refreshPlanPill();
    }

    private class RelationshipAdapter extends RecyclerView.Adapter<HealthDataFragment.RelationshipAdapter.RelationshipViewHolder> {
        private List<UserProfile.RelationshipRequest> relationships;
        private boolean isFamilyPlanOwner = false;

        public RelationshipAdapter(List<UserProfile.RelationshipRequest> relationships) {
            this.relationships = relationships;
        }

        public void setFamilyPlanOwner(boolean owner) {
            this.isFamilyPlanOwner = owner;
        }

        @NonNull
        @Override
        public HealthDataFragment.RelationshipAdapter.RelationshipViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_family_relationship, parent, false);
            return new HealthDataFragment.RelationshipAdapter.RelationshipViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull HealthDataFragment.RelationshipAdapter.RelationshipViewHolder holder, int position) {
            UserProfile.RelationshipRequest relationship = relationships.get(position);

            String displayName = relationship.getName();
            if (displayName == null || displayName.isEmpty()) {
                displayName = relationship.getEmail();
            }

            holder.nameText.setText(displayName);
            holder.emailText.setText(relationship.getEmail());
            holder.relationshipText.setText(relationship.getRelationship());

            String status = relationship.getStatus();
            holder.statusText.setText(status);

            String relationshipType = relationship.getRelationship().toLowerCase();
            boolean isFemaleRelationship = relationshipType.contains("mother") ||
                    relationshipType.contains("sister") ||
                    relationshipType.contains("aunt") ||
                    relationshipType.contains("grandmother") ||
                    relationshipType.contains("daughter") ||
                    relationshipType.contains("niece");

            boolean isDependent = "dependent".equals(status);

            // Status icon
            if (isDependent) {
                holder.statusIcon.setImageResource(R.drawable.ic_person);
                holder.statusIcon.setColorFilter(0xFF008b8b, PorterDuff.Mode.SRC_IN);
            } else if ("accepted".equals(status)) {
                holder.statusIcon.setImageResource(isFemaleRelationship ? R.drawable.ic_personf : R.drawable.ic_person);
                holder.statusIcon.setColorFilter(
                        ContextCompat.getColor(requireContext(), android.R.color.holo_green_light),
                        PorterDuff.Mode.SRC_IN);
            } else if ("pending".equals(status)) {
                holder.statusIcon.setImageResource(R.drawable.ic_pending);
                holder.statusIcon.setColorFilter(
                        ContextCompat.getColor(requireContext(), android.R.color.holo_orange_light),
                        PorterDuff.Mode.SRC_IN);
            } else {
                holder.statusIcon.setImageResource(isFemaleRelationship ? R.drawable.ic_personf : R.drawable.ic_person);
                holder.statusIcon.setColorFilter(
                        ContextCompat.getColor(requireContext(), android.R.color.darker_gray),
                        PorterDuff.Mode.SRC_IN);
            }

            // Status chip
            if (isDependent) {
                String depType = relationship.getRelationship();
                holder.statusChip.setText(depType != null ? depType.toUpperCase() : "DEPENDENT");
                holder.statusChip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(0xFF008b8b));
            } else if ("accepted".equals(status)) {
                holder.statusChip.setText("CONNECTED");
                holder.statusChip.setChipBackgroundColorResource(android.R.color.holo_green_dark);
            } else if ("pending".equals(status)) {
                holder.statusChip.setText("PENDING");
                holder.statusChip.setChipBackgroundColorResource(android.R.color.holo_orange_dark);
            } else {
                holder.statusChip.setText(status != null ? status.toUpperCase() : "UNKNOWN");
                holder.statusChip.setChipBackgroundColorResource(android.R.color.darker_gray);
            }

            // Badges
            holder.proBadge.setVisibility(View.GONE);
            holder.coveredBadge.setVisibility(View.GONE);

            if ("accepted".equals(status)) {
                if (relationship.isPro()) {
                    holder.proBadge.setVisibility(View.VISIBLE);
                    String source = relationship.getProSource();
                    holder.proBadge.setText("self".equals(source) ? "PRO" : "FAMILY PRO");
                }
                if (isFamilyPlanOwner && relationship.isCoveredByMyPlan()) {
                    holder.coveredBadge.setVisibility(View.VISIBLE);
                }
            }

            // Pro plan action buttons (only for family plan owners on accepted members, not dependents)
            if (isFamilyPlanOwner && "accepted".equals(status) && !isDependent) {
                holder.actionButtonsContainer.setVisibility(View.VISIBLE);
                if (relationship.isCoveredByMyPlan()) {
                    holder.removeFromProButton.setVisibility(View.VISIBLE);
                    holder.addToProButton.setVisibility(View.GONE);
                    holder.removeFromProButton.setOnClickListener(v -> {
                        if (relationship.getUserId() != null) {
                            handleRemoveFamilyMember(relationship.getUserId(), position);
                        }
                    });
                } else {
                    holder.addToProButton.setVisibility(View.VISIBLE);
                    holder.removeFromProButton.setVisibility(View.GONE);
                    holder.addToProButton.setOnClickListener(v -> {
                        if (relationship.getUserId() != null) {
                            handleAddFamilyMemberToPro(relationship.getUserId(), position);
                        }
                    });
                }
            } else {
                holder.actionButtonsContainer.setVisibility(View.GONE);
            }

            // Main action buttons (consistent with other item cards)
            if (isDependent) {
                holder.editButton.setVisibility(View.GONE);
                holder.removeButton.setVisibility(View.GONE);
                holder.cancelRequestButton.setVisibility(View.GONE);
                holder.emailText.setVisibility(View.GONE);
            } else if ("accepted".equals(status)) {
                holder.emailText.setVisibility(View.VISIBLE);
                holder.editButton.setVisibility(View.VISIBLE);
                holder.removeButton.setVisibility(View.VISIBLE);
                holder.cancelRequestButton.setVisibility(View.GONE);

                holder.editButton.setOnClickListener(v ->
                        showEditRelationshipDialog(relationship, position));
                holder.removeButton.setOnClickListener(v ->
                        confirmDeleteRelationship(relationship, position));
            } else if ("pending".equals(status)) {
                holder.emailText.setVisibility(View.VISIBLE);
                holder.editButton.setVisibility(View.GONE);
                holder.removeButton.setVisibility(View.GONE);
                holder.cancelRequestButton.setVisibility(View.VISIBLE);

                holder.cancelRequestButton.setOnClickListener(v ->
                        confirmCancelRequest(relationship, position));
            } else {
                holder.emailText.setVisibility(View.VISIBLE);
                holder.editButton.setVisibility(View.GONE);
                holder.removeButton.setVisibility(View.GONE);
                holder.cancelRequestButton.setVisibility(View.GONE);
            }
        }

        @Override
        public int getItemCount() {
            return relationships.size();
        }

        class RelationshipViewHolder extends RecyclerView.ViewHolder {
            TextView nameText, emailText, relationshipText, statusText;
            TextView proBadge, coveredBadge;
            Chip statusChip;
            ImageView statusIcon;
            LinearLayout actionButtonsContainer, mainActionsRow;
            MaterialButton addToProButton, removeFromProButton;
            MaterialButton editButton, removeButton, cancelRequestButton;

            public RelationshipViewHolder(@NonNull View itemView) {
                super(itemView);
                nameText = itemView.findViewById(R.id.name_text);
                emailText = itemView.findViewById(R.id.email_text);
                relationshipText = itemView.findViewById(R.id.relationship_text);
                statusText = itemView.findViewById(R.id.status_text);
                statusIcon = itemView.findViewById(R.id.status_icon);
                statusChip = itemView.findViewById(R.id.status_chip);
                proBadge = itemView.findViewById(R.id.pro_badge);
                coveredBadge = itemView.findViewById(R.id.covered_badge);
                actionButtonsContainer = itemView.findViewById(R.id.action_buttons_container);
                addToProButton = itemView.findViewById(R.id.add_to_pro_button);
                removeFromProButton = itemView.findViewById(R.id.remove_from_pro_button);
                mainActionsRow = itemView.findViewById(R.id.main_actions_row);
                editButton = itemView.findViewById(R.id.edit_button);
                removeButton = itemView.findViewById(R.id.remove_button);
                cancelRequestButton = itemView.findViewById(R.id.cancel_request_button);
            }
        }
    }
    public interface OnMedicationActionListener {
        void onEditItem(MedicationModel medication);
        void onDeleteItem(MedicationModel medication, int position);
    }

    private class MedicationsAdapter extends RecyclerView.Adapter<MedicationsAdapter.MedicationViewHolder> {
        private List<MedicationModel> medicationList;
        private OnMedicationActionListener actionListener;


        public MedicationsAdapter(List<MedicationModel> medicationList) {
            this.medicationList = medicationList;
        }

        public void setActionListener(OnMedicationActionListener listener) {
            this.actionListener = listener;
        }

        public void updateItem(MedicationModel medication) {
            for (int i = 0; i < medicationList.size(); i++) {
                // Compare by serverId since medications are server-only
                String existingServerId = medicationList.get(i).getServerId();
                String updatedServerId = medication.getServerId();
                if (existingServerId != null && updatedServerId != null &&
                    existingServerId.equals(updatedServerId)) {
                    medicationList.set(i, medication);
                    notifyItemChanged(i);
                    break;
                }
            }
        }

        public void removeItem(int position) {
            if (position >= 0 && position < medicationList.size()) {
                medicationList.remove(position);
                notifyItemRemoved(position);
            }
        }

        @NonNull
        @Override
        public MedicationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_medication, parent, false);
            return new MedicationViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull MedicationViewHolder holder, int position) {
            MedicationModel medication = medicationList.get(position);
            if (holder.nameText == null) return;

            holder.nameText.setText(medication.getName());
            holder.dosageText.setText(medication.getDosage());
            holder.frequencyText.setText(medication.getFrequency());

            // Set duration text using the helper method from MedicationModel
            holder.durationText.setText(medication.getDurationText());

            // Set status using chip
            if (holder.statusChip != null) {
                if (medication.isCurrent()) {
                    holder.statusChip.setText("ACTIVE");
                    holder.statusChip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#4CAF50"))); // Green
                } else {
                    holder.statusChip.setText("COMPLETED");
                    holder.statusChip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#757575"))); // Gray
                }
            }

            if (medication.getNotes() != null && !medication.getNotes().isEmpty()) {
                holder.notesText.setText(medication.getNotes());
                holder.notesText.setVisibility(View.VISIBLE);
            } else {
                holder.notesText.setVisibility(View.GONE);
            }

            // Sharing icon
            if (holder.sharingIcon != null) {
                if (medication.isShareWithFamily()) {
                    holder.sharingIcon.setImageResource(R.drawable.ic_visibility);
                    holder.sharingIcon.setImageTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#008b8b")));
                } else {
                    holder.sharingIcon.setImageResource(R.drawable.ic_visibility_off);
                    holder.sharingIcon.setImageTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#666666")));
                }
                holder.sharingIcon.setOnClickListener(v -> {
                    String msg = medication.isShareWithFamily() ? "Shared with family" : "Not shared with family";
                    Utilities.toast(v.getContext(), msg);
                });
            }

            // Show discontinue button only for current medications
            if (holder.discontinueButton != null) {
                if (medication.isCurrent()) {
                    holder.discontinueButton.setVisibility(View.VISIBLE);
                    holder.discontinueButton.setOnClickListener(v -> {
                        showDiscontinueMedicationDialog(medication, position);
                    });
                } else {
                    holder.discontinueButton.setVisibility(View.GONE);
                }
            }
        }

        @Override
        public int getItemCount() {
            return medicationList.size();
        }

        class MedicationViewHolder extends RecyclerView.ViewHolder {
            TextView nameText;
            TextView dosageText;
            TextView frequencyText;
            TextView durationText;
            TextView notesText;
            Chip statusChip;
            MaterialButton editButton;
            MaterialButton discontinueButton;
            MaterialButton deleteButton;
            ImageView sharingIcon;

            public MedicationViewHolder(@NonNull View itemView) {
                super(itemView);
                nameText = itemView.findViewById(R.id.medication_name);
                dosageText = itemView.findViewById(R.id.medication_dosage);
                frequencyText = itemView.findViewById(R.id.medication_frequency);
                durationText = itemView.findViewById(R.id.medication_duration);
                notesText = itemView.findViewById(R.id.medication_notes);
                statusChip = itemView.findViewById(R.id.status_chip);
                editButton = itemView.findViewById(R.id.edit_button);
                discontinueButton = itemView.findViewById(R.id.discontinue_button);
                deleteButton = itemView.findViewById(R.id.delete_button);
                sharingIcon = itemView.findViewById(R.id.sharing_icon);

                // Set click listeners
                editButton.setOnClickListener(v -> {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION && actionListener != null) {
                        actionListener.onEditItem(medicationList.get(position));
                    }
                });

                deleteButton.setOnClickListener(v -> {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION && actionListener != null) {
                        actionListener.onDeleteItem(medicationList.get(position), position);
                    }
                });
            }
        }
    }}
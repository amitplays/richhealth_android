package com.example.richhealth.Activities;
import Utils.Utilities;

import static android.content.ContentValues.TAG;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.richhealth.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import Adapters.UploadedFilesAdapter;
import Api.MedicalReportApiService;
import Database.DatabaseHelper;
import Models.UserProfile;
import Utils.AnimatedActionButton;
import Utils.ProStatusManager;
import Utils.ProUpgradeDialog;
import Utils.SimpleProgress;
import Utils.UploadedFile;

public class MedicalReportsActivity extends Activity {
    private static final int FILE_PICKER_REQUEST_CODE = 1001;

    private RecyclerView reportsRecyclerView;
    private UploadedFilesAdapter filesAdapter;
    private List<UploadedFile> uploadedFiles;
    private DatabaseHelper dbHelper;
    private ProStatusManager proStatusManager;
    private AnimatedActionButton addReportButton;
    private TextView headerTitle;
    private UserProfile currentUserProfile;
    private MedicalReportApiService apiService;
    private Map<String, JSONObject> remoteReports = new HashMap<>();
    private boolean userCanAnalyze = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_medical_reports);

        // Initialize list FIRST to avoid NPE
        uploadedFiles = new ArrayList<>();

        initializeDatabase();
        initializeViews();
        setupClickListeners();
    }

    private void initializeDatabase() {
        dbHelper = new DatabaseHelper(this);
        proStatusManager = ProStatusManager.getInstance(this);
        apiService = new MedicalReportApiService(this);

        // Fetch current user profile
        currentUserProfile = dbHelper.getUserProfile();
        if (currentUserProfile == null) {
            Utilities.toast(this, "No user profile found");
            finish();
            return;
        }

        // Load remote reports once
        loadRemoteReports();
    }

    private void loadRemoteReports() {
        SimpleProgress progress = SimpleProgress.show(MedicalReportsActivity.this, "Loading reports...");
        apiService.getUserReports(new MedicalReportApiService.OnReportsFetchListener() {
            @Override
            public void onSuccess(List<JSONObject> reports) {
                progress.hide();
                remoteReports.clear();
                uploadedFiles.clear();

                for (JSONObject report : reports) {
                    try {
                        String reportId = report.getString("_id");
                        String fileName = report.getString("fileName");
                        String reportType = report.getString("reportType");
                        String status = report.getString("status");
                        String fileUrl = report.getString("fileUrl");

                        // Track user-level analysis capability
                        userCanAnalyze = report.optBoolean("_canAnalyze", false);

                        remoteReports.put(reportId, report);

                        UploadedFile uploadedFile = new UploadedFile(MedicalReportsActivity.this, null, fileName);
                        uploadedFile.setReportType(reportType);
                        uploadedFile.setReportId(reportId);
                        uploadedFile.setStatus(status);
                        uploadedFile.setFileUrl(fileUrl);

                        boolean hasAnalysis = "processed".equals(status) &&
                                report.has("aiAnalysisSummary") &&
                                !report.isNull("aiAnalysisSummary") &&
                                !report.getString("aiAnalysisSummary").isEmpty();

                        uploadedFile.setHasAnalysis(hasAnalysis);

                        // Mark whether this specific report can be analyzed
                        boolean canAnalyzeThis = report.optBoolean("canAnalyzeThisReport", false);
                        uploadedFile.setCanAnalyze(canAnalyzeThis);

                        if (hasAnalysis) {
                            uploadedFile.setAiAnalysisSummary(report.getString("aiAnalysisSummary"));
                            if (report.has("aiAnalysisDetailed") && !report.isNull("aiAnalysisDetailed")) {
                                uploadedFile.setAiAnalysisDetailed(report.getString("aiAnalysisDetailed"));
                            }
                        }

                        uploadedFiles.add(uploadedFile);
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing report", e);
                    }
                }

                if (filesAdapter != null) {
                    filesAdapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onError(String error) {
                progress.hide();
                Utilities.toast(MedicalReportsActivity.this, "Failed to load reports: " + error);
            }
        });
    }

    private void initializeViews() {
        headerTitle = findViewById(R.id.header_title);
        addReportButton = findViewById(R.id.add_medical_report_button);
        reportsRecyclerView = findViewById(R.id.reports_recycler);

        // Back button
        ImageButton backButton = findViewById(R.id.back_button);
        backButton.setOnClickListener(v -> finish());

        // uploadedFiles already initialized in onCreate
        filesAdapter = new UploadedFilesAdapter(uploadedFiles, this::deleteReport);

        // Setup analyzer listener
        filesAdapter.setAnalyzeClickListener(this::handleAnalyzeClick);

        // Setup RecyclerView
        reportsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        reportsRecyclerView.setAdapter(filesAdapter);

        // Collapse button initially
        addReportButton.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                addReportButton.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                addReportButton.collapseToIcon();
            }
        });
    }

    private void setupClickListeners() {
        // Add Medical Report Button — all users can upload reports
        addReportButton.setOnClickListener(v -> openFilePicker());
    }

    private void handleAnalyzeClick(UploadedFile file) {
        if (file.getReportId() == null) {
            Utilities.toast(this, "Report not uploaded yet");
            return;
        }

        String status = file.getStatus();

        if ("processing".equals(status) || "queued".equals(status)) {
            Utilities.toast(this, "Analysis is already in progress");
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
            Utils.DialogUtils.showConfirmDialog(this,
                    "Retry Analysis",
                    "Previous analysis failed. Would you like to try again?",
                    "Yes", "Cancel", false,
                    () -> requestAnalysis(file));
            return;
        }

        // Pro user with unanalyzed report — request analysis
        requestAnalysis(file);
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "Select files"), FILE_PICKER_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == FILE_PICKER_REQUEST_CODE && resultCode == RESULT_OK) {
            if (data != null) {
                // Check for multiple file selection
                if (data.getClipData() != null) {
                    int count = data.getClipData().getItemCount();
                    for (int i = 0; i < count; i++) {
                        Uri uri = data.getClipData().getItemAt(i).getUri();
                        processSelectedFile(uri);
                    }
                } else if (data.getData() != null) {
                    // Single file selection
                    Uri uri = data.getData();
                    processSelectedFile(uri);
                }
            }
        }
    }

    private void processSelectedFile(Uri uri) {
        if (uri != null && !isUriAlreadyAdded(uri)) {
            String fileName = getFileNameFromUri(uri);
            // Pass context to UploadedFile constructor
            UploadedFile newFile = new UploadedFile(this, uri, fileName);

            // Show report type dialog before adding
            showReportTypeDialog(newFile);
        }
    }

    private boolean isUriAlreadyAdded(Uri uri) {
        String fileName = getFileNameFromUri(uri);
        for (UploadedFile file : uploadedFiles) {
            if (file.getName().equals(fileName)) {
                return true;
            }
        }
        return false;
    }

    private String getFileNameFromUri(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    result = cursor.getString(nameIndex);
                }
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

    private void showReportTypeDialog(UploadedFile file) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_report_type, null);
        AutoCompleteTextView reportTypeSpinner = dialogView.findViewById(R.id.report_type_spinner);
        String[] reportTypes = {"Blood Test", "X-Ray", "MRI", "CT Scan", "Ultrasound", "ECG", "Medical Checkup", "Lab Report", "Prescription", "Other"};

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                reportTypes
        );
        reportTypeSpinner.setAdapter(adapter);

        new AlertDialog.Builder(this)
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
        SimpleProgress progress = SimpleProgress.show(MedicalReportsActivity.this, "Uploading report...");

        try {
            File actualFile = getFileFromUri(file.getUri());

            if (actualFile == null) {
                progress.hide();
                Utilities.toast(this, "Failed to access file");
                return;
            }

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

                                uploadedFiles.add(file);
                                filesAdapter.notifyItemInserted(uploadedFiles.size() - 1);

                                Utilities.toast(MedicalReportsActivity.this, "Report uploaded successfully");
                            } catch (JSONException e) {
                                Log.e(TAG, "Error parsing upload response", e);
                                Utilities.toast(MedicalReportsActivity.this, "Error processing upload response");
                            }
                        }

                        @Override
                        public void onError(String error) {
                            progress.hide();
                            Utilities.toast(MedicalReportsActivity.this, "Upload failed: " + error);
                        }

                        @Override
                        public void onLimitReached(String message) {
                            progress.hide();
                            Utils.DialogUtils.showConfirmDialog(MedicalReportsActivity.this,
                                    "Report Limit Reached",
                                    message,
                                    "Upgrade", "Not Now", false,
                                    () -> {
                                        ProUpgradeDialog upgDlg = new ProUpgradeDialog(MedicalReportsActivity.this);
                                        upgDlg.setLimitContext(message);
                                        upgDlg.show(isPro -> {
                                            if (isPro) proStatusManager.syncProStatusOnLogin(MedicalReportsActivity.this);
                                        });
                                    });
                        }

                        @Override
                        public void onProgress(int p) {
                        }
                    });
        } catch (Exception e) {
            progress.hide();
            Utilities.toast(this, "Error preparing file: " + e.getMessage());
        }
    }


    private void deleteReport(UploadedFile file, int position) {
        Utils.DialogUtils.showConfirmDialog(this,
                "Delete Report",
                "This will remove the report from your view but keep it archived for your records.",
                "Delete", "Cancel", true,
                () -> {
                    if (file.getReportId() != null) {
                        SimpleProgress progress = SimpleProgress.show(MedicalReportsActivity.this, "Deleting report...");
                        apiService.deleteReport(file.getReportId(), new MedicalReportApiService.OnDeleteListener() {
                            @Override
                            public void onSuccess() {
                                progress.hide();
                                uploadedFiles.remove(position);
                                filesAdapter.notifyItemRemoved(position);
                                Utilities.toast(MedicalReportsActivity.this, "Report archived successfully");
                            }

                            @Override
                            public void onError(String error) {
                                progress.hide();
                                Utilities.toast(MedicalReportsActivity.this, "Failed to delete report: " + error);
                            }
                        });
                    } else {
                        uploadedFiles.remove(position);
                        filesAdapter.notifyItemRemoved(position);
                    }
                });
    }


    private void requestAnalysis(UploadedFile file) {
        SimpleProgress progress = SimpleProgress.show(MedicalReportsActivity.this, "Queuing analysis...");

        file.setStatus("processing");
        filesAdapter.notifyDataSetChanged();

        apiService.analyzeReport(file.getReportId(), new MedicalReportApiService.OnAnalysisListener() {
            @Override
            public void onSuccess(JSONObject analysis) {
                progress.hide();
                try {
                    JSONObject report = analysis.getJSONObject("report");
                    String newStatus = report.getString("status");

                    remoteReports.put(file.getReportId(), report);
                    file.setStatus(newStatus);

                    if ("processed".equals(newStatus) && report.has("aiAnalysisSummary")) {
                        file.setAiAnalysisSummary(report.getString("aiAnalysisSummary"));
                        file.setHasAnalysis(true);

                        if (report.has("aiAnalysisDetailed") && !report.isNull("aiAnalysisDetailed")) {
                            file.setAiAnalysisDetailed(report.getString("aiAnalysisDetailed"));
                        }

                        filesAdapter.notifyDataSetChanged();
                        showAnalysisDialog(file, false);
                    } else if ("failed".equals(newStatus)) {
                        file.setHasAnalysis(false);
                        filesAdapter.notifyDataSetChanged();
                        Utilities.toast(MedicalReportsActivity.this, "Analysis failed. Please try again.");
                    } else {
                        // Analysis queued — will be processed asynchronously
                        file.setCanAnalyze(false);
                        filesAdapter.notifyDataSetChanged();
                        Utilities.toast(MedicalReportsActivity.this, "Analysis queued. It will be ready shortly.");
                    }

                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing analysis response", e);
                    file.setStatus("failed");
                    filesAdapter.notifyDataSetChanged();
                    Utilities.toast(MedicalReportsActivity.this, "Error processing analysis response");
                }
            }

            @Override
            public void onError(String error) {
                progress.hide();
                file.setStatus("failed");
                filesAdapter.notifyDataSetChanged();
                Utilities.toast(MedicalReportsActivity.this, "Analysis failed: " + error);
            }

            @Override
            public void onNotAllowed(String message) {
                progress.hide();
                file.setStatus(file.getStatus() != null && !"failed".equals(file.getStatus()) ? file.getStatus() : "pending");
                filesAdapter.notifyDataSetChanged();
                Utils.DialogUtils.showConfirmDialog(MedicalReportsActivity.this,
                        "Upgrade Required",
                        message,
                        "Upgrade", "Not Now", false,
                        () -> {
                            ProUpgradeDialog upgDlg = new ProUpgradeDialog(MedicalReportsActivity.this);
                            upgDlg.setLimitContext(message);
                            upgDlg.show(isPro -> {
                                if (isPro) proStatusManager.syncProStatusOnLogin(MedicalReportsActivity.this);
                            });
                        });
            }
        });
    }

    private void showProUpgradeDialog() {
        new ProUpgradeDialog(this).show(isPro -> {
            if (isPro) {
                proStatusManager.syncProStatusOnLogin(MedicalReportsActivity.this);
            }
        });
    }

    private void showAnalysisDialog(UploadedFile file, boolean showReanalyzeOption) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Medical Report Analysis");

        String message = "";
        if (file.getAiAnalysisSummary() != null && !file.getAiAnalysisSummary().isEmpty()) {
            message = file.getAiAnalysisSummary();
            if (file.getAiAnalysisDetailed() != null && !file.getAiAnalysisDetailed().isEmpty()) {
                message += "\n\n" + file.getAiAnalysisDetailed();
            }
        } else {
            message = "No analysis available";
        }

        builder.setMessage(message);
        builder.setPositiveButton("OK", null);

        if (showReanalyzeOption) {
            builder.setNeutralButton("Re-analyze", (dialog, which) -> {
                Utils.DialogUtils.showConfirmDialog(this,
                        "Re-analyze Report",
                        "Are you sure you want to re-analyze this report? This will override the existing analysis.",
                        "Yes", "No", false,
                        () -> requestAnalysis(file));
            });
        }

        builder.show();
    }

//    private void saveToLocalDatabase(UploadedFile file, JSONObject serverReport) {
//        try {
//
//            // Get and set file type explicitly
//            String fileType = null;
//            if (file.getUri() != null) {
//                fileType = getContentResolver().getType(file.getUri());
//            }
//            if (fileType == null) {
//                fileType = "application/octet-stream";
//            }
//            file.setFileType(fileType);
//
//            // Create MedicalReport object for local database
//            UserProfile.MedicalReport localReport = new UserProfile.MedicalReport(
//                    file.getName(),
//                    file.getFileType(), // This will now work
//                    file.getReportType(),
//                    file.getUri() != null ? file.getUri().toString() : ""
//            );
//
//            // Set server ID
//            String serverId = serverReport.getString("_id");
//            localReport.setServerReportId(serverId);  // Store server ID separately
//            localReport.setStatus(serverReport.getString("status"));
//
//            // Set analysis if available
//            if (serverReport.has("aiAnalysisSummary")) {
//                localReport.setAiAnalysis(serverReport.getString("aiAnalysisSummary"));
//            }
//
//            // Insert into local database
//            long reportId = dbHelper.insertMedicalReport(currentUserProfile.getId(), localReport);
//
//            if (reportId != -1) {
//                Log.d(TAG, "Report saved to local database with ID: " + reportId);
//            }
//        } catch (JSONException e) {
//            Log.e(TAG, "Error saving to local database", e);
//        }
//    }
//
//    private void updateLocalDatabaseAnalysis(String reportId, JSONObject serverReport) {
//        try {
//            // Find the report in local database
//            List<UserProfile.MedicalReport> localReports =
//                    dbHelper.getMedicalReportsForUser(currentUserProfile.getId());
//
//            for (UserProfile.MedicalReport report : localReports) {
//                if (report.getId().equals(reportId)) {
//                    // Update analysis
//                    if (serverReport.has("aiAnalysisSummary")) {
//                        String analysis = serverReport.getString("aiAnalysisSummary");
//                        dbHelper.updateMedicalReportAnalysis(
//                                Long.parseLong(report.getId()), analysis);
//                    }
//                    break;
//                }
//            }
//        } catch (JSONException e) {
//            Log.e(TAG, "Error updating local database analysis", e);
//        }
//    }
//
//    private void markAsDeletedInLocalDatabase(String reportId) {
//        // Since your current implementation physically deletes,
//        // you might want to add a flag for soft delete
//        // For now, we'll keep the physical delete behavior
//        List<UserProfile.MedicalReport> localReports =
//                dbHelper.getMedicalReportsForUser(currentUserProfile.getId());
//
//        for (UserProfile.MedicalReport report : localReports) {
//            if (report.getId().equals(reportId)) {
//                // Delete from local database
//                dbHelper.deleteMedicalReport(Long.parseLong(report.getId()));
//                break;
//            }
//        }
//    }

    // File utility method - implement based on your needs
    private File getFileFromUri(Uri uri) {
        try {
            // Create a temporary file
            String fileName = getFileNameFromUri(uri);
            File tempFile = new File(getCacheDir(), fileName);

            try (InputStream inputStream = getContentResolver().openInputStream(uri);
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
}
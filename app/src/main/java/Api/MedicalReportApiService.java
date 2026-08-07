package Api;

import android.content.Context;
import android.util.Log;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.example.richhealth.Activities.TokenManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import Models.UserProfile;
import Utils.ApiConfig;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;

public class MedicalReportApiService {
    private static final String TAG = "MedicalReportApi";
    private static final String BASE_URL = ApiConfig.getBaseUrl() + "/api/medical-reports";

    private final Context context;
    private final RequestQueue requestQueue;
    private final TokenManager tokenManager;
    private final OkHttpClient httpClient;

    public interface OnReportUploadListener {
        void onSuccess(JSONObject response);
        void onError(String error);
        void onProgress(int progress);
        default void onLimitReached(String message) { onError(message); }
    }

    public interface OnReportsFetchListener {
        void onSuccess(List<JSONObject> reports);
        void onError(String error);
    }

    public interface OnAnalysisListener {
        void onSuccess(JSONObject analysis);
        void onError(String error);
        default void onNotAllowed(String message) { onError(message); }
    }

    public MedicalReportApiService(Context context) {
        this.context = context;
        this.requestQueue = Volley.newRequestQueue(context);
        this.tokenManager = TokenManager.getInstance(context);
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(160, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(160, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(160, java.util.concurrent.TimeUnit.SECONDS)
                .build();
    }

    public void uploadReport(File file, String reportType, JSONObject metadata,
                             OnReportUploadListener listener) {
        uploadReport(file, reportType, metadata, listener, "");
    }

    public void uploadReport(File file, String reportType, JSONObject metadata,
                             OnReportUploadListener listener, String rawText) {
        String url = BASE_URL;

        // Send the REAL content type (from the file extension) instead of a
        // generic octet-stream — otherwise the backend can't tell it's an image/
        // PDF and skips extraction ("not extractable"). Backend also re-checks.
        MediaType fileMediaType = MediaType.parse(guessMimeType(file.getName()));

        // Use OkHttp for multipart file upload
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.getName(),
                        RequestBody.create(fileMediaType, file))
                .addFormDataPart("reportType", reportType)
                .addFormDataPart("metadata", metadata.toString())
                .addFormDataPart("rawText", rawText != null ? rawText : "")
                .build();

        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + tokenManager.getToken())
                .post(requestBody)
                .build();

        sendUpload(url, request, listener);
    }

    /** Map a file name to a content type; falls back to octet-stream (backend re-checks). */
    private String guessMimeType(String name) {
        String lower = name == null ? "" : name.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".heic")) return "image/heic";
        if (lower.endsWith(".heif")) return "image/heif";
        if (lower.endsWith(".pdf")) return "application/pdf";
        return "application/octet-stream";
    }

    private void sendUpload(String url, okhttp3.Request request, OnReportUploadListener listener) {
        httpClient.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, IOException e) {
                ApiConfig.logRestCall(url, false, e.getMessage());
                Log.e(TAG, "Report upload failed", e);
                ((android.app.Activity) context).runOnUiThread(() ->
                        listener.onError("Upload failed: " + e.getMessage()));
            }

            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws IOException {
                final String responseBody = response.body().string();
                ApiConfig.logRestCall(url, response.isSuccessful(), response.message());
                ((android.app.Activity) context).runOnUiThread(() -> {
                    try {
                        if (response.isSuccessful()) {
                            JSONObject jsonResponse = new JSONObject(responseBody);
                            listener.onSuccess(jsonResponse);
                        } else if (response.code() == 429) {
                            String message = "You've reached your report upload limit for this period.";
                            try {
                                JSONObject errJson = new JSONObject(responseBody);
                                String msg = errJson.optString("message", "");
                                if (!msg.isEmpty()) message = msg;
                            } catch (JSONException ignored) {}
                            ApiConfig.logRestCall(url, false, "Report limit reached (429)");
                            Log.w(TAG, "Report upload limit reached");
                            listener.onLimitReached(message);
                        } else {
                            listener.onError("Upload failed: " + response.message());
                        }
                    } catch (JSONException e) {
                        listener.onError("Parse error: " + e.getMessage());
                    }
                });
            }
        });
    }

    public void getUserReports(OnReportsFetchListener listener) {
        String url = BASE_URL;

        StringRequest request = new StringRequest(Request.Method.GET, url,
                response -> {
                    ApiConfig.logRestCall(url, true, "Reports fetched");
                    try {
                        JSONObject jsonResponse = new JSONObject(response);
                        JSONArray jsonArray = jsonResponse.getJSONArray("reports");
                        boolean canAnalyze = jsonResponse.optBoolean("canAnalyze", false);

                        List<JSONObject> reports = new ArrayList<>();
                        for (int i = 0; i < jsonArray.length(); i++) {
                            JSONObject report = jsonArray.getJSONObject(i);
                            report.put("_canAnalyze", canAnalyze);
                            reports.add(report);
                        }

                        listener.onSuccess(reports);
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing report", e);
                        listener.onError("Parse error: " + e.getMessage());
                    }
                },
                error -> {
                    ApiConfig.logRestCall(url, false, error.toString());
                    Log.e(TAG, "Error fetching reports", error);
                    listener.onError(getErrorMessage(error));
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + tokenManager.getToken());
                return headers;
            }
        };

        request.setRetryPolicy(new DefaultRetryPolicy(
                30000,
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        ));

        requestQueue.add(request);
    }

    public void analyzeReport(String reportId, OnAnalysisListener listener) {
        String url = BASE_URL + "/" + reportId + "/analyze";

        StringRequest request = new StringRequest(Request.Method.POST, url,
                response -> {
                    ApiConfig.logRestCall(url, true, "Report analyzed");
                    try {
                        JSONObject jsonResponse = new JSONObject(response);
                        listener.onSuccess(jsonResponse);
                    } catch (JSONException e) {
                        listener.onError("Parse error: " + e.getMessage());
                    }
                },
                error -> {
                    ApiConfig.logRestCall(url, false, error.toString());
                    if (error.networkResponse != null && error.networkResponse.statusCode == 403) {
                        String message = "Report analysis requires a Plus plan or higher.";
                        try {
                            String body = new String(error.networkResponse.data, "utf-8");
                            JSONObject errJson = new JSONObject(body);
                            String msg = errJson.optString("message", "");
                            if (!msg.isEmpty()) message = msg;
                        } catch (Exception ignored) {}
                        Log.w(TAG, "Report analysis not allowed for tier (403)");
                        listener.onNotAllowed(message);
                    } else {
                        Log.e(TAG, "Error analyzing report " + reportId, error);
                        listener.onError(getErrorMessage(error));
                    }
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + tokenManager.getToken());
                return headers;
            }
        };

        request.setRetryPolicy(new DefaultRetryPolicy(
                60000,
                0,      // No retries
                1.0f
        ));

        requestQueue.add(request);
    }

    /**
     * Fetch a single report by ID — used to poll for analysis completion
     * after queueing. Returns the raw report JSON via OnAnalysisListener.onSuccess.
     */
    public void getReportById(String reportId, OnAnalysisListener listener) {
        String url = BASE_URL + "/" + reportId;

        StringRequest request = new StringRequest(Request.Method.GET, url,
                response -> {
                    ApiConfig.logRestCall(url, true, "Report fetched");
                    try {
                        JSONObject jsonResponse = new JSONObject(response);
                        listener.onSuccess(jsonResponse);
                    } catch (JSONException e) {
                        listener.onError("Parse error: " + e.getMessage());
                    }
                },
                error -> {
                    ApiConfig.logRestCall(url, false, error.toString());
                    Log.e(TAG, "Error fetching report " + reportId, error);
                    listener.onError(getErrorMessage(error));
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + tokenManager.getToken());
                return headers;
            }
        };

        request.setRetryPolicy(new DefaultRetryPolicy(
                30000,
                0,
                1.0f
        ));

        requestQueue.add(request);
    }

    public void getOverallHealthAnalysis(OnAnalysisListener listener) {
        String url = BASE_URL + "/analysis/overall";

        StringRequest request = new StringRequest(Request.Method.GET, url,
                response -> {
                    ApiConfig.logRestCall(url, true, "Overall health analysis fetched");
                    try {
                        JSONObject jsonResponse = new JSONObject(response);
                        listener.onSuccess(jsonResponse);
                    } catch (JSONException e) {
                        listener.onError("Parse error: " + e.getMessage());
                    }
                },
                error -> {
                    ApiConfig.logRestCall(url, false, error.toString());
                    Log.e(TAG, "Error fetching overall health analysis", error);
                    listener.onError(getErrorMessage(error));
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + tokenManager.getToken());
                return headers;
            }
        };

        request.setRetryPolicy(new DefaultRetryPolicy(
                30000,
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        ));

        requestQueue.add(request);
    }

    private String getErrorMessage(VolleyError error) {
        if (error.networkResponse != null) {
            try {
                String responseBody = new String(error.networkResponse.data, "utf-8");
                JSONObject data = new JSONObject(responseBody);
                return data.optString("message", "Unknown error occurred");
            } catch (Exception e) {
                return "Error: " + error.getMessage();
            }
        }
        return "Network error occurred";
    }

    // Add this interface to MedicalReportApiService.java
    public interface OnDeleteListener {
        void onSuccess();
        void onError(String error);
    }

    // Add this method to MedicalReportApiService.java
    public void deleteReport(String reportId, OnDeleteListener listener) {
        String url = BASE_URL + "/" + reportId;

        StringRequest request = new StringRequest(Request.Method.DELETE, url,
                response -> {
                    ApiConfig.logRestCall(url, true, "Report deleted");
                    listener.onSuccess();
                },
                error -> {
                    ApiConfig.logRestCall(url, false, error.toString());
                    Log.e(TAG, "Error deleting report " + reportId, error);
                    listener.onError(getErrorMessage(error));
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + tokenManager.getToken());
                return headers;
            }
        };

        request.setRetryPolicy(new DefaultRetryPolicy(
                30000,
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        ));

        requestQueue.add(request);
    }
}
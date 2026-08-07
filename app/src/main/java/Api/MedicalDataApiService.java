package Api;

import Utils.ApiConfig;
import android.content.Context;
import android.util.Log;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.example.richhealth.Activities.TokenManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.TimeZone;

import Models.MedicalData;

public class MedicalDataApiService {
    private static final String TAG = "MedicalDataApiService";
    private static final String BASE_URL = ApiConfig.BASE_URL + "/api/medical-data";

    private final Context context;
    private final RequestQueue requestQueue;
    private final TokenManager tokenManager;

    // Interface for handling API responses
    public interface OnMedicalDataListener {
        void onSuccess(JSONObject response);
        void onError(String errorMessage);
    }

    // Interface for handling data fetch responses
    public interface OnFetchDataListener {
        void onSuccess(List<JSONObject> dataList);
        void onError(String errorMessage);
    }

    // Interface for handling delete responses
    public interface OnDeleteListener {
        void onSuccess();
        void onError(String errorMessage);
    }

    // Constructor
    public MedicalDataApiService(Context context) {
        this.context = context;
        this.requestQueue = Volley.newRequestQueue(context);
        this.tokenManager = TokenManager.getInstance(context);
    }

    // Add a symptom
    public void addSymptom(MedicalData.Symptom symptom, OnMedicalDataListener listener) {
        JSONObject requestBody = new JSONObject();
        try {
            requestBody.put("type", MedicalData.TYPE_SYMPTOM);
            requestBody.put("title", symptom.getName());
            requestBody.put("severity", symptom.getSeverity());
            requestBody.put("duration", symptom.getDuration());
            requestBody.put("description", symptom.getDescription());

            // Format date for API
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            requestBody.put("dateTime", sdf.format(symptom.getRecordedAt()));
            requestBody.put("shareWithFamily", symptom.isShareWithFamily());

        } catch (JSONException e) {
            Log.e(TAG, "Error creating request body", e);
            listener.onError("Error preparing request: " + e.getMessage());
            return;
        }

        makeRequest(Request.Method.POST, BASE_URL, requestBody, listener);
    }

    // Add a measurement
    public void addMeasurement(MedicalData.HealthMetric measurement, OnMedicalDataListener listener) {
        JSONObject requestBody = new JSONObject();
        try {
            requestBody.put("type", MedicalData.TYPE_MEASUREMENT);
            requestBody.put("title", measurement.getMetricType());
            requestBody.put("value", measurement.getValue());
            requestBody.put("unit", measurement.getUnit());
            requestBody.put("description", measurement.getNotes());

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            requestBody.put("dateTime", sdf.format(measurement.getRecordedAt()));
            requestBody.put("shareWithFamily", measurement.isShareWithFamily());

        } catch (JSONException e) {
            Log.e(TAG, "Error creating request body", e);
            listener.onError("Error preparing request: " + e.getMessage());
            return;
        }

        makeRequest(Request.Method.POST, BASE_URL, requestBody, listener);
    }

    // Get all medical data
    public void getAllMedicalData(OnFetchDataListener listener) {
        StringRequest request = new StringRequest(Request.Method.GET, BASE_URL,
                response -> {
                    ApiConfig.logRestCall(BASE_URL, true, "Data fetched successfully");
                    try {
                        JSONObject jsonResponse = new JSONObject(response);
                        JSONArray dataArray = jsonResponse.getJSONArray("data");

                        List<JSONObject> dataList = new ArrayList<>();
                        for (int i = 0; i < dataArray.length(); i++) {
                            dataList.add(dataArray.getJSONObject(i));
                        }

                        listener.onSuccess(dataList);

                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing response", e);
                        listener.onError("Error parsing server response");
                    }
                },
                error -> {
                    ApiConfig.logRestCall(BASE_URL, false, error.toString());
                    Log.e(TAG, "Error fetching medical data", error);
                    listener.onError(handleVolleyError(error));
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + tokenManager.getToken());
                return headers;
            }
        };

        requestQueue.add(request);
    }

    // Get medical data by type
    public void getMedicalDataByType(String type, OnFetchDataListener listener) {
        String url = BASE_URL + "?type=" + type;

        StringRequest request = new StringRequest(Request.Method.GET, url,
                response -> {
                    ApiConfig.logRestCall(url, true, "Data fetched successfully for type: " + type);
                    try {
                        JSONObject jsonResponse = new JSONObject(response);
                        JSONArray dataArray = jsonResponse.getJSONArray("data");

                        List<JSONObject> dataList = new ArrayList<>();
                        for (int i = 0; i < dataArray.length(); i++) {
                            dataList.add(dataArray.getJSONObject(i));
                        }

                        listener.onSuccess(dataList);

                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing response", e);
                        listener.onError("Error parsing server response");
                    }
                },
                error -> {
                    ApiConfig.logRestCall(url, false, error.toString());
                    Log.e(TAG, "Error fetching medical data", error);
                    listener.onError(handleVolleyError(error));
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + tokenManager.getToken());
                return headers;
            }
        };

        requestQueue.add(request);
    }

    // Get medical data statistics
    public void getMedicalDataStats(OnMedicalDataListener listener) {
        String url = BASE_URL + "/stats";

        StringRequest request = new StringRequest(Request.Method.GET, url,
                response -> {
                    ApiConfig.logRestCall(url, true, "Stats fetched successfully");
                    try {
                        JSONObject jsonResponse = new JSONObject(response);
                        listener.onSuccess(jsonResponse);
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing response", e);
                        listener.onError("Error parsing server response");
                    }
                },
                error -> {
                    ApiConfig.logRestCall(url, false, error.toString());
                    Log.e(TAG, "Error fetching medical data stats", error);
                    listener.onError(handleVolleyError(error));
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + tokenManager.getToken());
                return headers;
            }
        };

        requestQueue.add(request);
    }

    // Update a symptom
    public void updateSymptom(String serverId, MedicalData.Symptom symptom, OnMedicalDataListener listener) {
        String url = BASE_URL + "/" + serverId;

        JSONObject requestBody = new JSONObject();
        try {
            requestBody.put("type", MedicalData.TYPE_SYMPTOM);
            requestBody.put("title", symptom.getName());
            requestBody.put("severity", symptom.getSeverity());
            requestBody.put("duration", symptom.getDuration());
            requestBody.put("description", symptom.getDescription());
            requestBody.put("shareWithFamily", symptom.isShareWithFamily());

            if (symptom.getRecordedAt() != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
                sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                requestBody.put("dateTime", sdf.format(symptom.getRecordedAt()));
            }

        } catch (JSONException e) {
            Log.e(TAG, "Error creating request body", e);
            listener.onError("Error preparing request: " + e.getMessage());
            return;
        }

        makeRequest(Request.Method.PUT, url, requestBody, listener);
    }

    // Update a measurement
    public void updateMeasurement(String serverId, MedicalData.HealthMetric measurement, OnMedicalDataListener listener) {
        String url = BASE_URL + "/" + serverId;

        JSONObject requestBody = new JSONObject();
        try {
            requestBody.put("type", MedicalData.TYPE_MEASUREMENT);
            requestBody.put("title", measurement.getMetricType());
            requestBody.put("value", measurement.getValue());
            requestBody.put("unit", measurement.getUnit());
            requestBody.put("description", measurement.getNotes());
            requestBody.put("shareWithFamily", measurement.isShareWithFamily());

            if (measurement.getRecordedAt() != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
                sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                requestBody.put("dateTime", sdf.format(measurement.getRecordedAt()));
            }

        } catch (JSONException e) {
            Log.e(TAG, "Error creating request body", e);
            listener.onError("Error preparing request: " + e.getMessage());
            return;
        }

        makeRequest(Request.Method.PUT, url, requestBody, listener);
    }

    // Delete medical data
    public void deleteMedicalData(String serverId, OnDeleteListener listener) {
        String url = BASE_URL + "/" + serverId;

        StringRequest request = new StringRequest(Request.Method.DELETE, url,
                response -> {
                    ApiConfig.logRestCall(url, true, "Data deleted successfully");
                    listener.onSuccess();
                },
                error -> {
                    ApiConfig.logRestCall(url, false, error.toString());
                    Log.e(TAG, "Error deleting medical data", error);
                    listener.onError(handleVolleyError(error));
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + tokenManager.getToken());
                return headers;
            }
        };

        requestQueue.add(request);
    }

    // Helper method to make API requests
    private void makeRequest(int method, String url, JSONObject requestBody, OnMedicalDataListener listener) {
        StringRequest request = new StringRequest(method, url,
                response -> {
                    ApiConfig.logRestCall(url, true, "Request successful");
                    try {
                        JSONObject jsonResponse = new JSONObject(response);
                        listener.onSuccess(jsonResponse);
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing response", e);
                        listener.onError("Error parsing server response");
                    }
                },
                error -> {
                    ApiConfig.logRestCall(url, false, error.toString());
                    Log.e(TAG, "API error", error);
                    listener.onError(handleVolleyError(error));
                }
        ) {
            @Override
            public byte[] getBody() {
                return requestBody.toString().getBytes(StandardCharsets.UTF_8);
            }

            @Override
            public String getBodyContentType() {
                return "application/json; charset=utf-8";
            }

            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + tokenManager.getToken());
                return headers;
            }
        };

        // Set timeout
        request.setRetryPolicy(new DefaultRetryPolicy(
                30000,  // 30 seconds timeout
                0,      // no retries
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        ));

        requestQueue.add(request);
    }

    // Helper method to handle Volley errors
    private String handleVolleyError(VolleyError error) {
        if (error.networkResponse != null) {
            String responseBody = new String(error.networkResponse.data, StandardCharsets.UTF_8);
            try {
                JSONObject errorJson = new JSONObject(responseBody);
                if (errorJson.has("message")) {
                    return errorJson.getString("message");
                }
            } catch (JSONException e) {
                Log.e(TAG, "Error parsing error response", e);
            }
            return "Server error: " + error.networkResponse.statusCode;
        } else if (error instanceof com.android.volley.NoConnectionError) {
            return "No internet connection";
        } else if (error instanceof com.android.volley.TimeoutError) {
            return "Request timed out";
        } else {
            return "An unknown error occurred";
        }
    }

    public void toggleMedicalDataSharing(String dataId, boolean shareWithFamily, OnSharingToggleListener listener) {
        String url = BASE_URL + "/" + dataId + "/sharing";
        patchBooleanField(url, "shareWithFamily", shareWithFamily, listener);
    }

    public void toggleSymptomSharing(String symptomId, boolean shareWithFamily, OnSharingToggleListener listener) {
        // Symptoms are stored as MedicalData with type="symptom" — use unified endpoint
        String url = BASE_URL + "/" + symptomId + "/sharing";
        patchBooleanField(url, "shareWithFamily", shareWithFamily, listener);
    }

    public void toggleMedicalDataChatContext(String dataId, boolean includeInChat, OnSharingToggleListener listener) {
        String url = BASE_URL + "/" + dataId + "/chat-context";
        patchBooleanField(url, "includeInChat", includeInChat, listener);
    }

    public void toggleSymptomChatContext(String symptomId, boolean includeInChat, OnSharingToggleListener listener) {
        String url = BASE_URL + "/" + symptomId + "/chat-context";
        patchBooleanField(url, "includeInChat", includeInChat, listener);
    }

    public void toggleMedicationSharing(String medicationId, boolean shareWithFamily, OnSharingToggleListener listener) {
        String url = ApiConfig.BASE_URL + "/api/medications/" + medicationId + "/sharing";
        patchBooleanField(url, "shareWithFamily", shareWithFamily, listener);
    }

    public void toggleMedicationChatContext(String medicationId, boolean includeInChat, OnSharingToggleListener listener) {
        String url = ApiConfig.BASE_URL + "/api/medications/" + medicationId + "/chat-context";
        patchBooleanField(url, "includeInChat", includeInChat, listener);
    }

    public void toggleReportSharing(String reportId, boolean shareWithFamily, OnSharingToggleListener listener) {
        String url = ApiConfig.BASE_URL + "/api/medical-reports/" + reportId + "/sharing";
        patchBooleanField(url, "shareWithFamily", shareWithFamily, listener);
    }

    public void toggleReportChatContext(String reportId, boolean includeInChat, OnSharingToggleListener listener) {
        String url = ApiConfig.BASE_URL + "/api/medical-reports/" + reportId + "/chat-context";
        patchBooleanField(url, "includeInChat", includeInChat, listener);
    }

    // ─── Period Log Methods ────────────────────────────────────────────────────
    private static final String PERIOD_LOG_URL = ApiConfig.BASE_URL + "/api/period-logs";

    public void addPeriodLog(MedicalData.PeriodLog periodLog, OnMedicalDataListener listener) {
        JSONObject requestBody = new JSONObject();
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

            requestBody.put("startDate", sdf.format(periodLog.getStartDate()));
            if (periodLog.getEndDate() != null) {
                requestBody.put("endDate", sdf.format(periodLog.getEndDate()));
            }
            requestBody.put("flowIntensity", periodLog.getFlowIntensity());
            requestBody.put("painLevel", periodLog.getPainLevel());
            requestBody.put("notes", periodLog.getNotes());
            requestBody.put("shareWithFamily", periodLog.isShareWithFamily());
        } catch (JSONException e) {
            Log.e(TAG, "Error creating request body", e);
            listener.onError("Error preparing request: " + e.getMessage());
            return;
        }

        makeRequest(Request.Method.POST, PERIOD_LOG_URL, requestBody, listener);
    }

    public void getPeriodLogs(OnFetchDataListener listener) {
        StringRequest request = new StringRequest(Request.Method.GET, PERIOD_LOG_URL,
                response -> {
                    ApiConfig.logRestCall(PERIOD_LOG_URL, true, "Period logs fetched successfully");
                    try {
                        JSONObject jsonResponse = new JSONObject(response);
                        JSONArray dataArray = jsonResponse.getJSONArray("periodLogs");

                        List<JSONObject> dataList = new ArrayList<>();
                        for (int i = 0; i < dataArray.length(); i++) {
                            dataList.add(dataArray.getJSONObject(i));
                        }

                        listener.onSuccess(dataList);
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing response", e);
                        listener.onError("Error parsing server response");
                    }
                },
                error -> {
                    ApiConfig.logRestCall(PERIOD_LOG_URL, false, error.toString());
                    Log.e(TAG, "Error fetching period logs", error);
                    listener.onError(handleVolleyError(error));
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + tokenManager.getToken());
                return headers;
            }
        };

        requestQueue.add(request);
    }

    public void updatePeriodLog(String serverId, MedicalData.PeriodLog periodLog, OnMedicalDataListener listener) {
        String url = PERIOD_LOG_URL + "/" + serverId;

        JSONObject requestBody = new JSONObject();
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

            requestBody.put("startDate", sdf.format(periodLog.getStartDate()));
            if (periodLog.getEndDate() != null) {
                requestBody.put("endDate", sdf.format(periodLog.getEndDate()));
            }
            requestBody.put("flowIntensity", periodLog.getFlowIntensity());
            requestBody.put("painLevel", periodLog.getPainLevel());
            requestBody.put("notes", periodLog.getNotes());
            requestBody.put("shareWithFamily", periodLog.isShareWithFamily());
        } catch (JSONException e) {
            Log.e(TAG, "Error creating request body", e);
            listener.onError("Error preparing request: " + e.getMessage());
            return;
        }

        makeRequest(Request.Method.PUT, url, requestBody, listener);
    }

    public void deletePeriodLog(String serverId, OnDeleteListener listener) {
        String url = PERIOD_LOG_URL + "/" + serverId;

        StringRequest request = new StringRequest(Request.Method.DELETE, url,
                response -> {
                    ApiConfig.logRestCall(url, true, "Period log deleted successfully");
                    listener.onSuccess();
                },
                error -> {
                    ApiConfig.logRestCall(url, false, error.toString());
                    Log.e(TAG, "Error deleting period log", error);
                    listener.onError(handleVolleyError(error));
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + tokenManager.getToken());
                return headers;
            }
        };

        requestQueue.add(request);
    }

    public void togglePeriodLogSharing(String periodLogId, boolean shareWithFamily, OnSharingToggleListener listener) {
        String url = PERIOD_LOG_URL + "/" + periodLogId + "/sharing";
        patchBooleanField(url, "shareWithFamily", shareWithFamily, listener);
    }

    public void togglePeriodLogChatContext(String periodLogId, boolean includeInChat, OnSharingToggleListener listener) {
        String url = PERIOD_LOG_URL + "/" + periodLogId + "/chat-context";
        patchBooleanField(url, "includeInChat", includeInChat, listener);
    }

    private void patchBooleanField(String url, String fieldName, boolean value, OnSharingToggleListener listener) {
        JSONObject requestBody = new JSONObject();
        try {
            requestBody.put(fieldName, value);
        } catch (JSONException e) {
            listener.onError("Error creating request: " + e.getMessage());
            return;
        }

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.PATCH, url, requestBody,
                response -> {
                    ApiConfig.logRestCall(url, true, "PATCH successful");
                    try {
                        String message = response.optString("message", "Updated successfully");
                        listener.onSuccess(message, value);
                    } catch (Exception e) {
                        listener.onError("Error parsing response: " + e.getMessage());
                    }
                },
                error -> {
                    ApiConfig.logRestCall(url, false, error.toString());
                    Log.e(TAG, "PATCH " + url + " failed", error);
                    listener.onError(handleVolleyError(error));
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                String token = tokenManager.getToken();
                if (token != null) {
                    headers.put("Authorization", "Bearer " + token);
                }
                headers.put("Content-Type", "application/json");
                return headers;
            }
        };

        requestQueue.add(request);
    }

    public interface OnSharingToggleListener {
        void onSuccess(String message, boolean newState);
        void onError(String errorMessage);
    }
}
package Api;

import android.content.Context;
import android.util.Log;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.example.richhealth.Activities.TokenManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import Models.AQIData;
import Utils.ApiConfig;

public class AQIAPIService {
    private static final String TAG = "AQIAPIService";
    private static final String BASE_URL = ApiConfig.BASE_URL + "/api/aqi";

    private final Context context;
    private final RequestQueue requestQueue;
    private final TokenManager tokenManager;

    // Interface for handling AQI history responses
    public interface OnAQIHistoryListener {
        void onSuccess(List<AQIData> aqiHistory);
        void onError(String errorMessage);
    }

    // Interface for handling single AQI response
    public interface OnAQILatestListener {
        void onSuccess(AQIData aqiData);
        void onError(String errorMessage);
    }

    // Interface for handling AQI analysis response
    public interface OnAQIAnalysisListener {
        void onSuccess(int averageAQI, int maxAQI, int highExposureDays);
        void onError(String errorMessage);
    }

    // Constructor
    public AQIAPIService(Context context) {
        this.context = context;
        this.requestQueue = Volley.newRequestQueue(context);
        this.tokenManager = TokenManager.getInstance(context);
    }

    // Get user AQI history
    public void getUserAQIHistory(int days, OnAQIHistoryListener listener) {
        String url = BASE_URL + "/user/history?days=" + days;

        StringRequest request = new StringRequest(Request.Method.GET, url,
                response -> {
                    ApiConfig.logRestCall(url, true, "User AQI history fetched");
                    try {
                        JSONObject jsonResponse = new JSONObject(response);
                        JSONArray historyArray = jsonResponse.getJSONArray("history");

                        List<AQIData> aqiHistory = new ArrayList<>();
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
                        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

                        for (int i = 0; i < historyArray.length(); i++) {
                            JSONObject aqiObject = historyArray.getJSONObject(i);

                            // Backend returns 'aqius' (US AQI standard)
                            int aqiValue = aqiObject.getInt("aqius");
                            
                            // Determine status based on AQI value
                            String status = getAQIStatus(aqiValue);
                            
                            String recordedAtStr = aqiObject.getString("timestamp");

                            Date recordedAt = sdf.parse(recordedAtStr);

                            // Create AQI data object (userId will be set when storing)
                            AQIData aqiData = new AQIData(0, aqiValue, status, recordedAt);
                            if (aqiObject.has("_id")) {
                                aqiData.setServerId(aqiObject.getString("_id"));
                            }
                            
                            // Set location data
                            if (aqiObject.has("city")) {
                                aqiData.setCity(aqiObject.getString("city"));
                            }
                            if (aqiObject.has("state")) {
                                aqiData.setState(aqiObject.getString("state"));
                            }
                            if (aqiObject.has("country")) {
                                aqiData.setCountry(aqiObject.getString("country"));
                            }

                            aqiHistory.add(aqiData);
                        }

                        listener.onSuccess(aqiHistory);

                    } catch (JSONException | ParseException e) {
                        Log.e(TAG, "Error parsing AQI history response", e);
                        listener.onError("Error parsing server response");
                    }
                },
                error -> {
                    ApiConfig.logRestCall(url, false, error.toString());
                    Log.e(TAG, "Error fetching AQI history", error);
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

        request.setRetryPolicy(new DefaultRetryPolicy(
                30000,  // 30 seconds timeout
                0,      // no retries
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        ));

        requestQueue.add(request);
    }

    // Get latest AQI for a location from server
    public void getLatestAQI(String city, String state, String country, OnAQILatestListener listener) {
        String url = BASE_URL + "/latest?city=" + city + "&state=" + state + "&country=" + country;

        StringRequest request = new StringRequest(Request.Method.GET, url,
                response -> {
                    ApiConfig.logRestCall(url, true, "Latest AQI fetched");
                    try {
                        JSONObject jsonResponse = new JSONObject(response);

                        int aqiValue = jsonResponse.getInt("aqius");
                        String status = getAQIStatus(aqiValue);

                        AQIData aqiData = new AQIData(0, aqiValue, status, new Date());
                        aqiData.setCity(city);
                        aqiData.setState(state);
                        aqiData.setCountry(country);

                        listener.onSuccess(aqiData);

                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing latest AQI response", e);
                        listener.onError("Error parsing server response");
                    }
                },
                error -> {
                    ApiConfig.logRestCall(url, false, error.toString());
                    Log.e(TAG, "Error fetching latest AQI", error);
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

        request.setRetryPolicy(new DefaultRetryPolicy(
                15000, 0, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        ));

        requestQueue.add(request);
    }

    // Get AQI history for a location from server
    public void getLocationAQIHistory(String city, String state, String country, int days, OnAQIHistoryListener listener) {
        String url = BASE_URL + "/history?city=" + city + "&state=" + state + "&country=" + country + "&days=" + days;

        StringRequest request = new StringRequest(Request.Method.GET, url,
                response -> {
                    ApiConfig.logRestCall(url, true, "Location AQI history fetched");
                    try {
                        JSONObject jsonResponse = new JSONObject(response);
                        JSONArray historyArray = jsonResponse.getJSONArray("history");

                        List<AQIData> aqiHistory = new ArrayList<>();
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
                        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

                        for (int i = 0; i < historyArray.length(); i++) {
                            JSONObject aqiObject = historyArray.getJSONObject(i);

                            int aqiValue = aqiObject.getInt("aqius");
                            String status = getAQIStatus(aqiValue);
                            String recordedAtStr = aqiObject.getString("timestamp");

                            Date recordedAt = sdf.parse(recordedAtStr);

                            AQIData aqiData = new AQIData(0, aqiValue, status, recordedAt);
                            aqiData.setCity(city);
                            aqiData.setState(state);
                            aqiData.setCountry(country);

                            aqiHistory.add(aqiData);
                        }

                        listener.onSuccess(aqiHistory);

                    } catch (JSONException | ParseException e) {
                        Log.e(TAG, "Error parsing location AQI history response", e);
                        listener.onError("Error parsing server response");
                    }
                },
                error -> {
                    ApiConfig.logRestCall(url, false, error.toString());
                    Log.e(TAG, "Error fetching location AQI history", error);
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

        request.setRetryPolicy(new DefaultRetryPolicy(
                30000, 0, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        ));

        requestQueue.add(request);
    }

    // Get user's AQI exposure analysis
    public void getUserAQIAnalysis(OnAQIAnalysisListener listener) {
        String url = BASE_URL + "/user/analysis";

        StringRequest request = new StringRequest(Request.Method.GET, url,
                response -> {
                    ApiConfig.logRestCall(url, true, "User AQI analysis fetched");
                    try {
                        JSONObject jsonResponse = new JSONObject(response);

                        int averageAQI = jsonResponse.optInt("averageAQI", 0);
                        int maxAQI = jsonResponse.optInt("maxAQI", 0);
                        int highExposureDays = jsonResponse.optInt("highExposureDays", 0);

                        listener.onSuccess(averageAQI, maxAQI, highExposureDays);

                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing AQI analysis response", e);
                        listener.onError("Error parsing server response");
                    }
                },
                error -> {
                    ApiConfig.logRestCall(url, false, error.toString());
                    Log.e(TAG, "Error fetching AQI analysis", error);
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

        request.setRetryPolicy(new DefaultRetryPolicy(
                15000, 0, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        ));

        requestQueue.add(request);
    }

    // Helper method to determine AQI status based on value
    private static String getAQIStatus(int aqi) {
        if (aqi <= 50) return "Good";
        else if (aqi <= 100) return "Moderate";
        else if (aqi <= 150) return "Unhealthy for Sensitive Groups";
        else if (aqi <= 200) return "Unhealthy";
        else if (aqi <= 300) return "Very Unhealthy";
        else return "Hazardous";
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
}

package com.example.richhealth.Activities;
import Utils.Utilities;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.example.richhealth.R;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import Utils.ApiConfig;
import Utils.FoodDialogUtils;
import Utils.ProUpgradeDialog;
import Utils.SimpleProgress;

public class DietaryInsightsActivity extends AppCompatActivity {

    private static final String TAG = "DietaryInsightsActivity";
    private static final String DIETARY_INSIGHTS_CACHE_KEY = "dietary_insights_cache";
    private static final String DIETARY_INSIGHTS_CACHE_TIME_KEY = "dietary_insights_cache_time";

    private TabLayout tabLayout;
    private ViewPager2 viewPager;
    private TextView usageBadge;
    private String[] tabTitles = {"Foods to Eat", "Foods to Avoid", "History"};

    public enum DataState { LOADING, LOADED, ERROR }
    private DataState dataState = DataState.LOADING;

    private List<FoodDialogUtils.FoodItem> foodsToEat = new ArrayList<>();
    private List<FoodDialogUtils.FoodItem> foodsToAvoid = new ArrayList<>();

    // null = not yet loaded, empty list = loaded but no history
    private List<JSONObject> historyItems = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dietary_insights);

        // Back button
        ImageButton backButton = findViewById(R.id.back_button);
        backButton.setOnClickListener(v -> finish());

        tabLayout = findViewById(R.id.dietary_tabs);
        viewPager = findViewById(R.id.dietary_viewpager);
        usageBadge = findViewById(R.id.usage_badge);

        // Show loading
        SimpleProgress.show(this, "Loading dietary insights...");

        // Setup ViewPager with fragments
        setupViewPager();

        // Connect TabLayout with ViewPager
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) ->
                tab.setText(tabTitles[position])
        ).attach();

        // Fetch dietary data
        TokenManager tokenManager = TokenManager.getInstance(this);
        String token = tokenManager != null ? tokenManager.getToken() : null;
        checkHealthDataStatusAndFetch(token);

        // Fetch history in parallel
        if (token != null) {
            fetchHistory(token);
        } else {
            historyItems = new ArrayList<>(); // empty, no auth
            updateHistoryFragment();
        }
    }

    private void setupViewPager() {
        DietaryPagerAdapter adapter = new DietaryPagerAdapter(this);
        viewPager.setAdapter(adapter);
    }

    private void hideLoading() {
        SimpleProgress.hide();
    }

    public List<FoodDialogUtils.FoodItem> getFoodsToEat() {
        return foodsToEat;
    }

    public List<FoodDialogUtils.FoodItem> getFoodsToAvoid() {
        return foodsToAvoid;
    }

    public DataState getDataState() {
        return dataState;
    }

    /** Called by DietaryHistoryFragment to get history data (null = still loading) */
    public List<JSONObject> getHistory() {
        return historyItems;
    }

    private void updateFragments() {
        // Notify list fragments that food data is ready
        for (Fragment fragment : getSupportFragmentManager().getFragments()) {
            if (fragment instanceof DietaryListFragment) {
                ((DietaryListFragment) fragment).refreshData();
            }
        }
    }

    private void updateHistoryFragment() {
        for (Fragment fragment : getSupportFragmentManager().getFragments()) {
            if (fragment instanceof DietaryHistoryFragment) {
                ((DietaryHistoryFragment) fragment).refreshData();
            }
        }
    }

    private void fetchHistory(String token) {
        String url = ApiConfig.BASE_URL + "/api/home/dietary-insights/history";
        StringRequest request = new StringRequest(Request.Method.GET, url,
                response -> {
                    ApiConfig.logRestCall(url, true, "Dietary history fetched");
                    try {
                        JSONObject json = new JSONObject(response);
                        JSONArray arr = json.optJSONArray("history");
                        historyItems = new ArrayList<>();
                        if (arr != null) {
                            for (int i = 0; i < arr.length(); i++) {
                                historyItems.add(arr.getJSONObject(i));
                            }
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing history", e);
                        historyItems = new ArrayList<>();
                    }
                    updateHistoryFragment();
                },
                error -> {
                    ApiConfig.logRestCall(url, false, error.toString());
                    historyItems = new ArrayList<>();
                    updateHistoryFragment();
                }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + token);
                return headers;
            }
        };
        Volley.newRequestQueue(this).add(request);
    }

    private void checkHealthDataStatusAndFetch(String token) {
        SharedPreferences prefs = getSharedPreferences("dietary_insights_prefs", MODE_PRIVATE);
        String cachedData = prefs.getString(DIETARY_INSIGHTS_CACHE_KEY, null);
        long cacheTime = prefs.getLong(DIETARY_INSIGHTS_CACHE_TIME_KEY, 0);
        boolean cacheIsRecent = cachedData != null && (System.currentTimeMillis() - cacheTime) < 24 * 60 * 60 * 1000;

        if (token == null) {
            if (cachedData != null) {
                try {
                    processResponse(new JSONObject(cachedData));
                } catch (JSONException e) {
                    loadDefaultData();
                }
            } else {
                loadDefaultData();
            }
            return;
        }

        if (cacheIsRecent) {
            Log.d(TAG, "Using cached dietary insights");
            try {
                processResponse(new JSONObject(cachedData));
                return;
            } catch (JSONException e) {
                Log.e(TAG, "Error parsing cached data", e);
            }
        }

        // Check health data status
        String statusUrl = ApiConfig.BASE_URL + "/api/user/health-data-status";

        StringRequest statusRequest = new StringRequest(Request.Method.GET, statusUrl,
                response -> {
                    ApiConfig.logRestCall(statusUrl, true, "Health data status checked");
                    try {
                        JSONObject statusJson = new JSONObject(response);
                        boolean healthDataNeedsUpdate = statusJson.optBoolean("healthDataNeedsUpdate", true);

                        if (!healthDataNeedsUpdate && cachedData != null) {
                            Log.d(TAG, "Using cached dietary insights - health data unchanged");
                            processResponse(new JSONObject(cachedData));
                        } else {
                            Log.d(TAG, "Fetching fresh dietary insights");
                            fetchDietaryInsights(token, prefs);
                        }
                    } catch (JSONException e) {
                        fetchDietaryInsights(token, prefs);
                    }
                },
                error -> {
                    ApiConfig.logRestCall(statusUrl, false, error.toString());
                    fetchDietaryInsights(token, prefs);
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + token);
                return headers;
            }
        };

        Volley.newRequestQueue(this).add(statusRequest);
    }

    private void fetchDietaryInsights(String token, SharedPreferences prefs) {
        String dataUrl = ApiConfig.BASE_URL + "/api/home/dietary-insights";

        JsonObjectRequest dataRequest = new JsonObjectRequest(Request.Method.GET, dataUrl, null,
                response -> {
                    ApiConfig.logRestCall(dataUrl, true, "Dietary insights fetched");
                    prefs.edit()
                            .putString(DIETARY_INSIGHTS_CACHE_KEY, response.toString())
                            .putLong(DIETARY_INSIGHTS_CACHE_TIME_KEY, System.currentTimeMillis())
                            .apply();
                    processResponse(response);
                },
                error -> {
                    ApiConfig.logRestCall(dataUrl, false, error.toString());
                    hideLoading();

                    // Check for 429 (limit reached)
                    if (error.networkResponse != null && error.networkResponse.statusCode == 429) {
                        try {
                            String body = new String(error.networkResponse.data, "UTF-8");
                            JSONObject errJson = new JSONObject(body);
                            String msg = errJson.optString("message", "Diet Guide limit reached for this period.");

                            // Update usage badge to show limit reached
                            updateUsageBadge(errJson.optJSONObject("usageStatus"));

                            Utils.DialogUtils.showConfirmDialog(DietaryInsightsActivity.this,
                                "Limit Reached",
                                msg + "\n\nUpgrade your plan for more dietary insights.",
                                "Upgrade", "OK", false,
                                () -> new ProUpgradeDialog(DietaryInsightsActivity.this).show(isPro -> {}));
                        } catch (Exception ignored) {}

                        // Still try to show cached data if available
                        if (prefs.getString(DIETARY_INSIGHTS_CACHE_KEY, null) != null) {
                            try {
                                processResponse(new JSONObject(prefs.getString(DIETARY_INSIGHTS_CACHE_KEY, null)));
                                return;
                            } catch (JSONException ignored) {}
                        }
                    } else if (error.networkResponse != null && error.networkResponse.statusCode == 503) {
                        // AI temporarily unavailable — show message, use cache if possible
                        String msg = "AI analysis is temporarily unavailable. Please try again in a few minutes.";
                        try {
                            String body = new String(error.networkResponse.data, "UTF-8");
                            JSONObject errJson = new JSONObject(body);
                            String serverMsg = errJson.optString("message", "");
                            if (!serverMsg.isEmpty()) msg = serverMsg;
                        } catch (Exception ignored) {}
                        Utilities.toastLong(DietaryInsightsActivity.this, msg);

                        // Fall back to cached data if available
                        String cached = prefs.getString(DIETARY_INSIGHTS_CACHE_KEY, null);
                        if (cached != null) {
                            try {
                                processResponse(new JSONObject(cached));
                                return;
                            } catch (JSONException ignored) {}
                        }
                    }

                    loadDefaultData();
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + token);
                return headers;
            }
        };

        dataRequest.setRetryPolicy(new DefaultRetryPolicy(10000, 1, 1.0f));
        Volley.newRequestQueue(this).add(dataRequest);
    }

    private void updateUsageBadge(JSONObject usageStatus) {
        if (usageBadge == null) return;
        if (usageStatus == null) {
            usageBadge.setVisibility(View.GONE);
            return;
        }
        int count = usageStatus.optInt("count", 0);
        Object limitObj = usageStatus.opt("limit");
        boolean isUnlimited = (limitObj == null || limitObj.toString().equals("null"));

        usageBadge.setVisibility(View.VISIBLE);
        if (isUnlimited) {
            usageBadge.setText("Unlimited");
            usageBadge.setTextColor(Color.parseColor("#008b8b"));
        } else {
            int limit = usageStatus.optInt("limit", 0);
            usageBadge.setText(count + "/" + limit + " used");
            usageBadge.setTextColor(count >= limit ? Color.parseColor("#FF9800") : Color.parseColor("#808080"));
        }
    }

    private void processResponse(JSONObject response) {
        hideLoading();
        foodsToEat.clear();
        foodsToAvoid.clear();

        // Update usage badge from backend response
        updateUsageBadge(response.optJSONObject("usageStatus"));

        try {
            if (response.has("foodsToEat")) {
                JSONArray arr = response.getJSONArray("foodsToEat");
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    foodsToEat.add(new FoodDialogUtils.FoodItem(
                            obj.getString("name"),
                            obj.optString("components", ""),
                            obj.optString("reason", "")
                    ));
                }
            }
            if (response.has("foodsToAvoid")) {
                JSONArray arr = response.getJSONArray("foodsToAvoid");
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    foodsToAvoid.add(new FoodDialogUtils.FoodItem(
                            obj.getString("name"),
                            obj.optString("components", ""),
                            obj.optString("reason", "")
                    ));
                }
            }
            dataState = DataState.LOADED;
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing dietary response", e);
            dataState = DataState.ERROR;
        }

        updateFragments();
    }

    private void loadDefaultData() {
        // API failed — show error state, no dummy data
        hideLoading();
        foodsToEat.clear();
        foodsToAvoid.clear();
        dataState = DataState.ERROR;
        updateFragments();
    }

    // ViewPager adapter
    private class DietaryPagerAdapter extends FragmentStateAdapter {

        public DietaryPagerAdapter(FragmentActivity fragmentActivity) {
            super(fragmentActivity);
        }

        @Override
        public int getItemCount() {
            return tabTitles.length;
        }

        @Override
        public Fragment createFragment(int position) {
            if (position == 2) {
                return DietaryHistoryFragment.newInstance();
            }
            return DietaryListFragment.newInstance(position);
        }
    }
}

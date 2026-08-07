package Utils;

import android.animation.ObjectAnimator;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.SharedPreferences;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.example.richhealth.Activities.TokenManager;
import Utils.ApiConfig;
import com.example.richhealth.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class for displaying food information dialogs
 */
public class FoodDialogUtils {

    /**
     * Data class to hold food information
     */
    public static class FoodItem {
        private String foodName;
        private String components;
        private String reasonToAvoid;

        public FoodItem(String foodName, String components, String reasonToAvoid) {
            this.foodName = foodName;
            this.components = components;
            this.reasonToAvoid = reasonToAvoid;
        }

        public String getFoodName() { return foodName; }
        public String getComponents() { return components; }
        public String getReasonToAvoid() { return reasonToAvoid; }
    }


    /**
     * Get default foods to avoid
     */
    private static List<FoodItem> getDefaultFoodsToAvoid(Context context) {
        List<FoodItem> items = new ArrayList<>();
        try {
            String jsonSample = "{"
                    + "\"foodsToAvoid\": ["
                    + "  {\"name\": \"Processed Meats\", \"components\": \"Sodium, Nitrates, Saturated Fat\", \"reason\": \"Increases risk of hypertension and heart disease\"},"
                    + "  {\"name\": \"Refined Carbohydrates\", \"components\": \"White flour, Added sugars\", \"reason\": \"Spikes blood sugar, linked to diabetes and obesity\"},"
                    + "  {\"name\": \"Sugary Beverages\", \"components\": \"High fructose corn syrup, Added sugars\", \"reason\": \"Linked to obesity, diabetes, and heart disease\"},"
                    + "  {\"name\": \"Trans Fats\", \"components\": \"Partially hydrogenated oils\", \"reason\": \"Increases bad cholesterol and inflammation\"},"
                    + "  {\"name\": \"Fast Food\", \"components\": \"Sodium, Trans fats, Refined carbs\", \"reason\": \"High in calories, salt, and unhealthy fats\"},"
                    + "  {\"name\": \"Canned Soups\", \"components\": \"Sodium, MSG, BPA\", \"reason\": \"Extremely high sodium content increases blood pressure\"},"
                    + "  {\"name\": \"Artificial Sweeteners\", \"components\": \"Aspartame, Saccharin, Sucralose\", \"reason\": \"May increase cravings and affect gut bacteria\"},"
                    + "  {\"name\": \"Vegetable Oils\", \"components\": \"Omega-6 fatty acids\", \"reason\": \"High consumption linked to inflammation\"}"
                    + "]}";

            JSONObject jsonObject = new JSONObject(jsonSample);
            JSONArray foodsArray = jsonObject.getJSONArray("foodsToAvoid");

            for (int i = 0; i < foodsArray.length(); i++) {
                JSONObject foodObject = foodsArray.getJSONObject(i);
                items.add(new FoodItem(
                        foodObject.getString("name"),
                        foodObject.getString("components"),
                        foodObject.getString("reason")
                ));
            }
        } catch (JSONException e) {
            Utilities.toast(context, "Error loading food data");
            e.printStackTrace();
        }
        return items;
    }

    /**
     * Get default foods to eat
     */
    private static List<FoodItem> getDefaultFoodsToEat(Context context) {
        List<FoodItem> items = new ArrayList<>();
        try {
            String jsonSample = "{"
                    + "\"foodsToEat\": ["
                    + "  {\"name\": \"Leafy Greens\", \"components\": \"Vitamins K, A, C, Folate, Iron\", \"reason\": \"Reduces inflammation, supports immune system\"},"
                    + "  {\"name\": \"Fatty Fish\", \"components\": \"Omega-3 fatty acids, Protein, Vitamin D\", \"reason\": \"Reduces inflammation, supports heart health\"},"
                    + "  {\"name\": \"Berries\", \"components\": \"Antioxidants, Fiber, Vitamin C\", \"reason\": \"Fights oxidative stress, improves blood sugar\"},"
                    + "  {\"name\": \"Nuts and Seeds\", \"components\": \"Healthy fats, Protein, Fiber\", \"reason\": \"Supports heart health, provides sustainable energy\"},"
                    + "  {\"name\": \"Legumes\", \"components\": \"Protein, Fiber, B vitamins\", \"reason\": \"Stabilizes blood sugar, supports gut health\"},"
                    + "  {\"name\": \"Whole Grains\", \"components\": \"Fiber, B vitamins, Minerals\", \"reason\": \"Provides sustainable energy, supports digestion\"},"
                    + "  {\"name\": \"Fermented Foods\", \"components\": \"Probiotics, Various nutrients\", \"reason\": \"Supports gut health and immune function\"},"
                    + "  {\"name\": \"Avocados\", \"components\": \"Monounsaturated fats, Potassium\", \"reason\": \"Supports heart health and nutrient absorption\"}"
                    + "]}";

            JSONObject jsonObject = new JSONObject(jsonSample);
            JSONArray foodsArray = jsonObject.getJSONArray("foodsToEat");

            for (int i = 0; i < foodsArray.length(); i++) {
                JSONObject foodObject = foodsArray.getJSONObject(i);
                items.add(new FoodItem(
                        foodObject.getString("name"),
                        foodObject.getString("components"),
                        foodObject.getString("reason")
                ));
            }
        } catch (JSONException e) {
            Utilities.toast(context, "Error loading food data");
            e.printStackTrace();
        }
        return items;
    }
    private static final String DIETARY_INSIGHTS_CACHE_KEY = "dietary_insights_cache";
    private static final String DIETARY_INSIGHTS_CACHE_TIME_KEY = "dietary_insights_cache_time";

    public static void showDietaryInsightsDialog(Context context, String title) {
        // First create and show the dialog with loading state
        Dialog dialog = new Dialog(context, R.style.DialogTheme);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_food_info);

        // Set dialog width to match most of the screen width
        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
        layoutParams.copyFrom(dialog.getWindow().getAttributes());
        layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
        layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
        dialog.getWindow().setAttributes(layoutParams);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        // Find views
        TextView titleTextView = dialog.findViewById(R.id.dialog_title);
        TextView disclaimerTextView = dialog.findViewById(R.id.disclaimer_text);
        RecyclerView foodRecyclerView = dialog.findViewById(R.id.food_recycler_view);
        TabLayout tabLayout = dialog.findViewById(R.id.dietary_tabs);
        MaterialButton okButton = dialog.findViewById(R.id.ok_button);
        final ImageView loadingIcon = dialog.findViewById(R.id.loading_icon);

        // Start the loading icon animation
        final ObjectAnimator spinningAnimator;
        if (loadingIcon != null) {
            spinningAnimator = ObjectAnimator.ofFloat(loadingIcon, View.ROTATION, 0f, 360f);
            spinningAnimator.setDuration(1500);
            spinningAnimator.setRepeatCount(ObjectAnimator.INFINITE);
            spinningAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
            spinningAnimator.start();
            loadingIcon.setVisibility(View.VISIBLE);
        } else {
            spinningAnimator = null;
        }

        titleTextView.setText(title);

        // Setup RecyclerView with empty adapter initially
        foodRecyclerView.setLayoutManager(new LinearLayoutManager(context));
        final FoodInfoAdapter adapter = new FoodInfoAdapter(new ArrayList<>());
        foodRecyclerView.setAdapter(adapter);

        // Set initial disclaimer
        disclaimerTextView.setText("This information is general guidance. Always consult with a healthcare professional for personalized nutrition advice.");

        TokenManager tokenManager = TokenManager.getInstance(context);
        String token = tokenManager != null ? tokenManager.getToken() : null;

        // Check health data status first to decide if we need to fetch fresh data
        checkHealthDataStatusAndFetch(context, token, dialog, adapter, tabLayout, loadingIcon, spinningAnimator, disclaimerTextView);

        okButton.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private static void checkHealthDataStatusAndFetch(Context context, String token, Dialog dialog,
                                                       FoodInfoAdapter adapter, TabLayout tabLayout,
                                                       ImageView loadingIcon, ObjectAnimator spinningAnimator,
                                                       TextView disclaimerTextView) {
        SharedPreferences prefs = context.getSharedPreferences("dietary_insights_prefs", Context.MODE_PRIVATE);
        String cachedData = prefs.getString(DIETARY_INSIGHTS_CACHE_KEY, null);
        long cacheTime = prefs.getLong(DIETARY_INSIGHTS_CACHE_TIME_KEY, 0);
        boolean cacheIsRecent = cachedData != null && (System.currentTimeMillis() - cacheTime) < 24 * 60 * 60 * 1000;

        if (token == null) {
            // No token, use cached or default data
            if (cachedData != null) {
                try {
                    processDietaryInsightsResponse(context, new JSONObject(cachedData), adapter, tabLayout, loadingIcon, spinningAnimator, disclaimerTextView);
                } catch (JSONException e) {
                    loadDefaultDietaryData(context, adapter, tabLayout, loadingIcon, spinningAnimator);
                }
            } else {
                loadDefaultDietaryData(context, adapter, tabLayout, loadingIcon, spinningAnimator);
            }
            return;
        }

        // If cache is less than 24 hours old, use it directly without any API call
        if (cacheIsRecent) {
            Log.d("FoodDialogUtils", "Using cached dietary insights - cache is less than 24 hours old");
            try {
                processDietaryInsightsResponse(context, new JSONObject(cachedData), adapter, tabLayout, loadingIcon, spinningAnimator, disclaimerTextView);
                return;
            } catch (JSONException e) {
                Log.e("FoodDialogUtils", "Error parsing cached data, falling through to API call", e);
                // Fall through to API call
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
                            // Health data hasn't changed, use cached insights
                            Log.d("FoodDialogUtils", "Using cached dietary insights - health data unchanged");
                            processDietaryInsightsResponse(context, new JSONObject(cachedData), adapter, tabLayout, loadingIcon, spinningAnimator, disclaimerTextView);
                        } else {
                            // Health data needs update, fetch fresh insights
                            Log.d("FoodDialogUtils", "Fetching fresh dietary insights - health data updated");
                            fetchDietaryInsights(context, token, adapter, tabLayout, loadingIcon, spinningAnimator, disclaimerTextView, prefs);
                        }
                    } catch (JSONException e) {
                        Log.e("FoodDialogUtils", "Error parsing health data status", e);
                        fetchDietaryInsights(context, token, adapter, tabLayout, loadingIcon, spinningAnimator, disclaimerTextView, prefs);
                    }
                },
                error -> {
                    ApiConfig.logRestCall(statusUrl, false, error.toString());
                    Log.e("FoodDialogUtils", "Error checking health data status", error);
                    // On error, fetch fresh data anyway
                    fetchDietaryInsights(context, token, adapter, tabLayout, loadingIcon, spinningAnimator, disclaimerTextView, prefs);
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + token);
                return headers;
            }
        };

        Volley.newRequestQueue(context).add(statusRequest);
    }

    private static void fetchDietaryInsights(Context context, String token, FoodInfoAdapter adapter,
                                              TabLayout tabLayout, ImageView loadingIcon,
                                              ObjectAnimator spinningAnimator, TextView disclaimerTextView,
                                              SharedPreferences prefs) {
        String dataUrl = ApiConfig.BASE_URL + "/api/home/dietary-insights";

        // Make the API call for dietary insights
        JsonObjectRequest dataRequest = new JsonObjectRequest(Request.Method.GET, dataUrl, null,
                response -> {
                    ApiConfig.logRestCall(dataUrl, true, "Dietary insights fetched");
                    // Cache the response
                    prefs.edit()
                            .putString(DIETARY_INSIGHTS_CACHE_KEY, response.toString())
                            .putLong(DIETARY_INSIGHTS_CACHE_TIME_KEY, System.currentTimeMillis())
                            .apply();

                    processDietaryInsightsResponse(context, response, adapter, tabLayout, loadingIcon, spinningAnimator, disclaimerTextView);
                },
                error -> {
                    ApiConfig.logRestCall(dataUrl, false, error.toString());
                    // Hide loading icon
                    if (loadingIcon != null && spinningAnimator != null) {
                        spinningAnimator.cancel();
                        loadingIcon.setVisibility(View.GONE);
                    }

                    Log.e("FoodDialogUtils", "Error fetching dietary data: " + error.getMessage());
                    loadDefaultDietaryData(context, adapter, tabLayout, loadingIcon, spinningAnimator);
                    setupDefaultTabListener(context, tabLayout, adapter, disclaimerTextView);
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + token);
                return headers;
            }
        };

        // Set timeout
        dataRequest.setRetryPolicy(new DefaultRetryPolicy(
                10000,  // 10 seconds timeout
                1,      // Max 1 retry
                1.0f    // No backoff multiplier
        ));

        // Add to request queue
        RequestQueue dataQueue = Volley.newRequestQueue(context);
        dataQueue.add(dataRequest);
    }

    private static void processDietaryInsightsResponse(Context context, JSONObject response,
                                                        FoodInfoAdapter adapter, TabLayout tabLayout,
                                                        ImageView loadingIcon, ObjectAnimator spinningAnimator,
                                                        TextView disclaimerTextView) {
        // Hide loading icon
        if (loadingIcon != null && spinningAnimator != null) {
            spinningAnimator.cancel();
            loadingIcon.setVisibility(View.GONE);
        }

        try {
            // Parse response and update data
            final List<FoodItem> foodsToEat = new ArrayList<>();
            final List<FoodItem> foodsToAvoid = new ArrayList<>();

            if (response.has("foodsToEat")) {
                JSONArray foodsArray = response.getJSONArray("foodsToEat");
                for (int i = 0; i < foodsArray.length(); i++) {
                    JSONObject foodObject = foodsArray.getJSONObject(i);
                    foodsToEat.add(new FoodItem(
                            foodObject.getString("name"),
                            foodObject.optString("components", ""),
                            foodObject.optString("reason", "")
                    ));
                }
            }

            if (response.has("foodsToAvoid")) {
                JSONArray foodsArray = response.getJSONArray("foodsToAvoid");
                for (int i = 0; i < foodsArray.length(); i++) {
                    JSONObject foodObject = foodsArray.getJSONObject(i);
                    foodsToAvoid.add(new FoodItem(
                            foodObject.getString("name"),
                            foodObject.optString("components", ""),
                            foodObject.optString("reason", "")
                    ));
                }
            }

            // Use default data only if API returned empty lists
            List<FoodItem> finalFoodsToEat = foodsToEat;
            List<FoodItem> finalFoodsToAvoid = foodsToAvoid;

            if (foodsToEat.isEmpty()) {
                finalFoodsToEat = getDefaultFoodsToEat(context);
            }

            if (foodsToAvoid.isEmpty()) {
                finalFoodsToAvoid = getDefaultFoodsToAvoid(context);
            }

            // Update the adapter with the appropriate list based on selected tab
            if (tabLayout.getSelectedTabPosition() == 0) {
                adapter.updateItems(finalFoodsToEat);
            } else {
                adapter.updateItems(finalFoodsToAvoid);
            }

            // Setup tab selection listener
            final List<FoodItem> finalEat = finalFoodsToEat;
            final List<FoodItem> finalAvoid = finalFoodsToAvoid;

            tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
                @Override
                public void onTabSelected(TabLayout.Tab tab) {
                    if (tab.getPosition() == 0) {
                        adapter.updateItems(finalEat);
                        disclaimerTextView.setText("This information is general guidance. Always consult with a healthcare professional for personalized nutrition advice.");
                    } else {
                        adapter.updateItems(finalAvoid);
                        disclaimerTextView.setText("This information is general guidance. Always consult with a healthcare professional before making dietary changes.");
                    }
                }

                @Override
                public void onTabUnselected(TabLayout.Tab tab) {}

                @Override
                public void onTabReselected(TabLayout.Tab tab) {}
            });

        } catch (JSONException e) {
            Log.e("FoodDialogUtils", "Error parsing API response", e);
            loadDefaultDietaryData(context, adapter, tabLayout, loadingIcon, spinningAnimator);
            setupDefaultTabListener(context, tabLayout, adapter, disclaimerTextView);
        }
    }

    private static void loadDefaultDietaryData(Context context, FoodInfoAdapter adapter,
                                                TabLayout tabLayout, ImageView loadingIcon,
                                                ObjectAnimator spinningAnimator) {
        // Hide loading icon
        if (loadingIcon != null && spinningAnimator != null) {
            spinningAnimator.cancel();
            loadingIcon.setVisibility(View.GONE);
        }

        List<FoodItem> foodsToEat = getDefaultFoodsToEat(context);
        List<FoodItem> foodsToAvoid = getDefaultFoodsToAvoid(context);

        if (tabLayout.getSelectedTabPosition() == 0) {
            adapter.updateItems(foodsToEat);
        } else {
            adapter.updateItems(foodsToAvoid);
        }
    }
    /**
     * Setup the tab listener with default data
     */
    private static void setupDefaultTabListener(Context context, TabLayout tabLayout,
                                                FoodInfoAdapter adapter, TextView disclaimerTextView) {
        List<FoodItem> foodsToEat = getDefaultFoodsToEat(context);
        List<FoodItem> foodsToAvoid = getDefaultFoodsToAvoid(context);

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                String disclaimer;

                if (tab.getPosition() == 0) {
                    // Foods to Eat tab
                    adapter.updateItems(foodsToEat);
                    disclaimer = "This information is general guidance. Always consult with a healthcare professional for personalized nutrition advice.";
                } else {
                    // Foods to Avoid tab
                    adapter.updateItems(foodsToAvoid);
                    disclaimer = "This information is general guidance. Always consult with a healthcare professional before making dietary changes.";
                }

                disclaimerTextView.setText(disclaimer);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    /**
     * Adapter for the food information RecyclerView
     */
    public static class FoodInfoAdapter extends RecyclerView.Adapter<FoodInfoAdapter.ViewHolder> {
        private List<FoodItem> foodItems;

        public FoodInfoAdapter(List<FoodItem> foodItems) {
            this.foodItems = foodItems;
        }

        // Add this method to update the data
        public void updateItems(List<FoodItem> newItems) {
            this.foodItems = newItems;
            notifyDataSetChanged();
        }

        @Override
        public ViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_food_info, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            FoodItem item = foodItems.get(position);
            holder.foodNameTextView.setText(item.getFoodName());
            holder.componentsTextView.setText(item.getComponents());
            holder.reasonTextView.setText(item.getReasonToAvoid());
        }

        @Override
        public int getItemCount() {
            return foodItems.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView foodNameTextView;
            TextView componentsTextView;
            TextView reasonTextView;

            public ViewHolder(View itemView) {
                super(itemView);
                foodNameTextView = itemView.findViewById(R.id.food_name);
                componentsTextView = itemView.findViewById(R.id.food_components);
                reasonTextView = itemView.findViewById(R.id.food_reason);
            }
        }
    }

}

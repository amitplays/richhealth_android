package com.example.richhealth.Activities;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
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

public class NutriCheckActivity extends AppCompatActivity {

    private static final String TAG = "NutriCheckActivity";
    private final String[] tabTitles = {"Check", "History"};

    private TokenManager tokenManager;
    private TextView usageBadge;

    // null = not yet loaded, empty list = loaded but no history
    private List<JSONObject> historyItems = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nutri_check);

        tokenManager = TokenManager.getInstance(this);

        ImageButton backButton = findViewById(R.id.back_button);
        backButton.setOnClickListener(v -> finish());

        usageBadge = findViewById(R.id.usage_badge);

        TabLayout tabLayout = findViewById(R.id.nutri_check_tabs);
        ViewPager2 viewPager = findViewById(R.id.nutri_check_viewpager);

        viewPager.setAdapter(new NutriCheckPagerAdapter(this));

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) ->
                tab.setText(tabTitles[position])
        ).attach();

        // Fetch history
        fetchHistory();
    }

    public String getToken() {
        return tokenManager != null ? tokenManager.getToken() : null;
    }

    /** Called by NutriCheckHistoryFragment to get history data (null = still loading) */
    public List<JSONObject> getHistory() {
        return historyItems;
    }

    /** Called by NutriCheckCheckFragment after a successful check */
    public void reloadHistory() {
        fetchHistory();
    }

    private void updateHistoryFragment() {
        for (Fragment fragment : getSupportFragmentManager().getFragments()) {
            if (fragment instanceof NutriCheckHistoryFragment) {
                ((NutriCheckHistoryFragment) fragment).refreshData();
            }
        }
    }

    private void fetchHistory() {
        String token = getToken();
        if (token == null) {
            historyItems = new ArrayList<>();
            updateHistoryFragment();
            return;
        }

        String url = ApiConfig.BASE_URL + "/api/home/nutri-check/history";

        StringRequest request = new StringRequest(Request.Method.GET, url,
                response -> {
                    try {
                        JSONObject json = new JSONObject(response);
                        JSONArray history = json.optJSONArray("history");

                        historyItems = new ArrayList<>();
                        if (history != null) {
                            for (int i = 0; i < history.length(); i++) {
                                historyItems.add(history.getJSONObject(i));
                            }
                        }

                        // Usage badge
                        JSONObject usageStatus = json.optJSONObject("usageStatus");
                        if (usageStatus != null) {
                            int count = usageStatus.optInt("count", 0);
                            Object limitObj = usageStatus.opt("limit");
                            if (limitObj == null || limitObj == JSONObject.NULL) {
                                usageBadge.setText("Unlimited");
                            } else {
                                usageBadge.setText(count + "/" + usageStatus.optInt("limit", 0) + " used");
                            }
                            usageBadge.setVisibility(View.VISIBLE);
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing history", e);
                        historyItems = new ArrayList<>();
                    }
                    updateHistoryFragment();
                },
                error -> {
                    Log.e(TAG, "Error loading history", error);
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

        request.setRetryPolicy(new DefaultRetryPolicy(15000, 1, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        Volley.newRequestQueue(this).add(request);
    }

    // ViewPager adapter
    private class NutriCheckPagerAdapter extends FragmentStateAdapter {

        public NutriCheckPagerAdapter(FragmentActivity fragmentActivity) {
            super(fragmentActivity);
        }

        @Override
        public int getItemCount() {
            return tabTitles.length;
        }

        @Override
        public Fragment createFragment(int position) {
            if (position == 1) {
                return NutriCheckHistoryFragment.newInstance();
            }
            return NutriCheckCheckFragment.newInstance();
        }
    }
}

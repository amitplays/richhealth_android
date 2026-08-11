package Utils;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.example.richhealth.R;
import com.example.richhealth.Activities.TokenManager;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Minimalist "Your Plan" usage sheet — replaces the old full-screen
 * UsageStatusActivity. Shows the current tier, this month's usage per feature as
 * clean rows with thin progress bars, and a single upgrade CTA (reuses the
 * existing {@link ProUpgradeDialog}). Pulls live data from /api/user/usage.
 */
public final class UsageBottomSheet {

    private UsageBottomSheet() {}

    // Features shown, in order: {usage key, display label}.
    private static final String[][] FEATURES = {
            {"chatSessions",    "Chat Sessions"},
            {"healthAnalysis",  "Health Analysis"},
            {"medicalReports",  "Medical Reports"},
            {"nutricheck",      "NutriCheck"},
            // Dietary Insights hidden for now (product decision).
    };

    public static void show(final Activity activity) {
        if (activity == null || activity.isFinishing()) return;

        final BottomSheetDialog dialog = new BottomSheetDialog(activity, R.style.RH_Theme_BottomSheetDialog);
        View sheet = LayoutInflater.from(activity).inflate(R.layout.sheet_usage_status, null);
        dialog.setContentView(sheet);

        // Belt-and-suspenders: also clear the container fill so only our rounded-top
        // surface is visible (no second rounded/elevated layer peeking behind it).
        View container = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (container != null) container.setBackgroundColor(Color.TRANSPARENT);

        final ProStatusManager pro = ProStatusManager.getInstance(activity);
        final TextView badge = sheet.findViewById(R.id.sheet_plan_badge);
        final TextView subtitle = sheet.findViewById(R.id.sheet_plan_subtitle);
        final LinearLayout rows = sheet.findViewById(R.id.sheet_usage_rows);
        final MaterialButton upgrade = sheet.findViewById(R.id.sheet_upgrade_button);

        // Immediate state from cache, then refined by the network payload.
        bindBadge(badge, pro.getUserTier());
        bindUpgradeButton(activity, dialog, upgrade, pro.isProUser());

        // Loading placeholders.
        renderRows(activity, rows, null);

        fetchUsage(activity, data -> {
            String tier = data != null ? data.optString("tier", pro.getUserTier()) : pro.getUserTier();
            boolean isPro = data != null ? data.optBoolean("isPro", pro.isProUser()) : pro.isProUser();
            bindBadge(badge, tier);
            bindUpgradeButton(activity, dialog, upgrade, isPro);
            if (subtitle != null && data != null) subtitle.setText(buildSubtitle(data));
            JSONObject usage = data != null ? data.optJSONObject("usage") : null;
            renderRows(activity, rows, usage);
        });

        dialog.show();
    }

    // ── Networking ──────────────────────────────────────────────────────────

    private interface UsageCallback { void onResult(JSONObject data); }

    private static void fetchUsage(final Activity activity, final UsageCallback cb) {
        TokenManager tm = TokenManager.getInstance(activity);
        final String token = tm != null ? tm.getToken() : null;
        if (token == null) { cb.onResult(null); return; }

        String url = ApiConfig.BASE_URL + "/api/user/usage";
        StringRequest req = new StringRequest(Request.Method.GET, url,
                response -> {
                    ApiConfig.logRestCall(url, true, "Usage fetched");
                    try { cb.onResult(new JSONObject(response)); }
                    catch (Exception e) { cb.onResult(null); }
                },
                error -> {
                    ApiConfig.logRestCall(url, false, error.toString());
                    cb.onResult(null);
                }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> h = new HashMap<>();
                h.put("Authorization", "Bearer " + token);
                return h;
            }
        };
        Volley.newRequestQueue(activity).add(req);
    }

    // ── Rendering ───────────────────────────────────────────────────────────

    private static void renderRows(Context ctx, LinearLayout container, JSONObject usage) {
        container.removeAllViews();
        float d = ctx.getResources().getDisplayMetrics().density;
        for (String[] feature : FEATURES) {
            container.addView(buildRow(ctx, feature[1],
                    usage != null ? usage.optJSONObject(feature[0]) : null, d));
        }
    }

    private static View buildRow(Context ctx, String label, JSONObject f, float d) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.bottomMargin = (int) (16 * d);
        row.setLayoutParams(rowLp);

        LinearLayout top = new LinearLayout(ctx);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView name = new TextView(ctx);
        name.setText(label);
        name.setTextColor(Color.parseColor("#CFD6D6"));
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        name.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        top.addView(name);

        TextView value = new TextView(ctx);
        value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        value.setTypeface(null, android.graphics.Typeface.BOLD);
        top.addView(value);
        row.addView(top);

        ProgressBar pb = new ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal);
        LinearLayout.LayoutParams pbLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int) (5 * d));
        pbLp.topMargin = (int) (8 * d);
        pb.setLayoutParams(pbLp);
        pb.setMax(100);
        row.addView(pb);

        int white = Color.WHITE;
        int green = Color.parseColor("#4CAF50");
        int orange = Color.parseColor("#FF9800");
        int teal = Color.parseColor("#008B8B");
        int amber = Color.parseColor("#FFB300");

        if (f == null) {
            value.setText("…");
            value.setTextColor(Color.parseColor("#808080"));
            pb.setProgress(0);
            return row;
        }

        int count = f.optInt("count", 0);
        boolean hasLimit = !f.isNull("limit");
        int limit = f.optInt("limit", 0);
        boolean reached = f.optBoolean("limitReached", false);

        if (!hasLimit) {
            value.setText(count + " used · unlimited");
            value.setTextColor(green);
            pb.setProgress(100);
            pb.setProgressTintList(ColorStateList.valueOf(green));
        } else if (limit == 0) {
            value.setText("Locked");
            value.setTextColor(orange);
            pb.setProgress(0);
            pb.setProgressTintList(ColorStateList.valueOf(orange));
        } else {
            int shown = Math.min(count, limit);
            int pct = Math.min(100, Math.round(100f * count / limit));
            value.setText(shown + " / " + limit);
            value.setTextColor(reached ? orange : white);
            pb.setProgress(pct);
            pb.setProgressTintList(ColorStateList.valueOf(reached ? orange : (pct >= 80 ? amber : teal)));
        }
        return row;
    }

    // ── Badge / button / subtitle ───────────────────────────────────────────

    private static void bindBadge(TextView badge, String tier) {
        if (badge == null) return;
        String t = tier == null ? "free" : tier.toLowerCase();
        String label; int bg;
        switch (t) {
            case "ultra": label = "Ultra"; bg = Color.parseColor("#F2C14E"); break;
            case "pro":   label = "Pro";   bg = Color.parseColor("#008B8B"); break;
            case "plus":  label = "Plus";  bg = Color.parseColor("#4FB0A6"); break;
            default:      label = "Free";  bg = Color.parseColor("#8A8A8A"); break;
        }
        badge.setText(label);
        float d = badge.getResources().getDisplayMetrics().density;
        GradientDrawable bgd = new GradientDrawable();
        bgd.setCornerRadius(20 * d);
        bgd.setColor(bg);
        badge.setBackground(bgd);
    }

    private static void bindUpgradeButton(final Activity activity, final BottomSheetDialog dialog,
                                          MaterialButton upgrade, boolean isPro) {
        if (upgrade == null) return;
        if (isPro) {
            upgrade.setText("Manage Account");
            upgrade.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#0D1F1F")));
            upgrade.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#008B8B")));
            upgrade.setStrokeWidth((int) activity.getResources().getDisplayMetrics().density * 2);
            upgrade.setTextColor(Color.parseColor("#008B8B"));
            upgrade.setOnClickListener(v -> dialog.dismiss());
        } else {
            upgrade.setText("Upgrade to Pro");
            upgrade.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#008B8B")));
            upgrade.setStrokeWidth(0);
            upgrade.setTextColor(Color.WHITE);
            upgrade.setOnClickListener(v -> {
                dialog.dismiss();
                new ProUpgradeDialog(activity).show(newIsPro -> {
                    if (newIsPro) ProStatusManager.syncProStatusOnLogin(activity);
                });
            });
        }
    }

    private static String buildSubtitle(JSONObject data) {
        long periodEnd = data.optLong("periodEnd", 0);
        if (periodEnd > 0) {
            long days = Math.max(0, (periodEnd - System.currentTimeMillis()) / (1000L * 60 * 60 * 24));
            return days <= 1 ? "Limits reset tomorrow" : "Limits reset in " + days + " days";
        }
        return "Your monthly limits";
    }
}

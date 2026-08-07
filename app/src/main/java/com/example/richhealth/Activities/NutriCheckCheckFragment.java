package com.example.richhealth.Activities;
import Utils.Utilities;

import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.example.richhealth.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import Utils.ApiConfig;
import Utils.NutriCheckFeedback;
import Utils.ProUpgradeDialog;

public class NutriCheckCheckFragment extends Fragment {

    private TextInputEditText foodItemInput;
    private MaterialButton checkButton;
    private ProgressBar checkProgress;
    private View resultContainer;
    private TextView recommendationText, reasonText;
    private View feedbackRow;
    private MaterialButton thumbUp, thumbDown;

    // Feedback state for the currently-displayed result card.
    private String currentHistoryId = null;   // id of the history entry the result corresponds to
    private String currentReaction = null;    // "up" | "down" | null
    private boolean feedbackInflight = false; // one POST at a time

    private static final int TINT_NEUTRAL = Color.parseColor("#808080");
    private static final int TINT_UP = Color.parseColor("#4CAF50");
    private static final int TINT_DOWN = Color.parseColor("#FF5252");

    // Session-level check history for consistency context (resets when activity is destroyed)
    private static class PreviousCheck {
        final String foodItem;
        final String recommendation;
        final String reason;
        PreviousCheck(String foodItem, String recommendation, String reason) {
            this.foodItem = foodItem;
            this.recommendation = recommendation;
            this.reason = reason;
        }
    }
    private final java.util.List<PreviousCheck> sessionChecks = new java.util.ArrayList<>();
    private boolean isChecking = false; // guard against double-tap / Volley retry dupes

    public static NutriCheckCheckFragment newInstance() {
        return new NutriCheckCheckFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_nutri_check_check, container, false);

        foodItemInput = view.findViewById(R.id.food_item_input);
        checkButton = view.findViewById(R.id.check_button);
        checkProgress = view.findViewById(R.id.check_progress);
        resultContainer = view.findViewById(R.id.result_container);
        recommendationText = view.findViewById(R.id.recommendation_text);
        reasonText = view.findViewById(R.id.reason_text);
        feedbackRow = view.findViewById(R.id.feedback_row);
        thumbUp = view.findViewById(R.id.thumb_up);
        thumbDown = view.findViewById(R.id.thumb_down);

        checkButton.setOnClickListener(v -> performCheck());
        thumbUp.setOnClickListener(v -> handleThumbTap("up"));
        thumbDown.setOnClickListener(v -> handleThumbTap("down"));

        return view;
    }

    private void handleThumbTap(String tapped) {
        if (currentHistoryId == null || feedbackInflight) return;
        NutriCheckActivity activity = getNutriCheckActivity();
        if (activity == null) return;
        String token = activity.getToken();
        if (token == null) return;

        final String prev = currentReaction;
        final String next = tapped.equals(prev) ? null : tapped;
        currentReaction = next;
        feedbackInflight = true;
        applyReactionTint();
        thumbUp.setEnabled(false);
        thumbDown.setEnabled(false);

        NutriCheckFeedback.send(requireContext(), token, currentHistoryId, next, success -> {
            feedbackInflight = false;
            thumbUp.setEnabled(true);
            thumbDown.setEnabled(true);
            if (!success) {
                currentReaction = prev;
                applyReactionTint();
                Utilities.toast(requireContext(), "Couldn't save feedback. Try again.");
            } else if (getNutriCheckActivity() != null) {
                // Reload so the History tab reflects the new reaction.
                getNutriCheckActivity().reloadHistory();
            }
        });
    }

    private void applyReactionTint() {
        if (thumbUp == null || thumbDown == null) return;
        int upTint = "up".equals(currentReaction) ? TINT_UP : TINT_NEUTRAL;
        int downTint = "down".equals(currentReaction) ? TINT_DOWN : TINT_NEUTRAL;
        thumbUp.setIconTint(ColorStateList.valueOf(upTint));
        thumbDown.setIconTint(ColorStateList.valueOf(downTint));
    }

    private void performCheck() {
        if (isChecking) return; // prevent double submission

        String foodItem = foodItemInput.getText() != null ? foodItemInput.getText().toString().trim() : "";
        if (foodItem.isEmpty()) {
            Utilities.toast(requireContext(), "Please enter a food item");
            return;
        }

        NutriCheckActivity activity = getNutriCheckActivity();
        if (activity == null) return;

        String token = activity.getToken();
        if (token == null) return;

        isChecking = true;
        checkButton.setEnabled(false);
        checkButton.setText("Checking...");
        checkProgress.setVisibility(View.VISIBLE);
        resultContainer.setVisibility(View.GONE);
        // Reset feedback state — old result is being discarded.
        currentHistoryId = null;
        currentReaction = null;
        feedbackInflight = false;

        String url = ApiConfig.BASE_URL + "/api/home/nutri-check";

        JSONObject body = new JSONObject();
        try {
            body.put("foodItem", foodItem);
            // Include last 3 checks from this session for AI consistency context
            if (!sessionChecks.isEmpty()) {
                org.json.JSONArray prevArray = new org.json.JSONArray();
                int start = Math.max(0, sessionChecks.size() - 3);
                for (int i = start; i < sessionChecks.size(); i++) {
                    JSONObject prev = new JSONObject();
                    prev.put("foodItem", sessionChecks.get(i).foodItem);
                    prev.put("recommendation", sessionChecks.get(i).recommendation);
                    prev.put("reason", sessionChecks.get(i).reason);
                    prevArray.put(prev);
                }
                body.put("previousChecks", prevArray);
            }
        } catch (JSONException ignored) {}

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, body,
                response -> {
                    isChecking = false;
                    checkButton.setEnabled(true);
                    checkButton.setText("Check");
                    checkProgress.setVisibility(View.GONE);

                    String recommendation = response.optString("recommendation", "moderate");
                    String reason = response.optString("reason", "");
                    currentHistoryId = response.optString("historyId", null);
                    currentReaction = null;

                    // Save to session history for consistency context in subsequent checks
                    if (sessionChecks.size() >= 5) sessionChecks.remove(0);
                    sessionChecks.add(new PreviousCheck(foodItem, recommendation, reason));

                    showResult(recommendation, reason);

                    // Tell activity to reload history
                    if (getNutriCheckActivity() != null) {
                        getNutriCheckActivity().reloadHistory();
                    }
                },
                error -> {
                    isChecking = false;
                    checkButton.setEnabled(true);
                    checkButton.setText("Check");
                    checkProgress.setVisibility(View.GONE);

                    NetworkResponse networkResponse = error.networkResponse;
                    if (networkResponse != null && networkResponse.statusCode == 429) {
                        String msg = "You've used all your NutriCheck scans this month.";
                        int usedCount = 0;
                        int limitCount = 0;
                        try {
                            String errBody = new String(networkResponse.data, "UTF-8");
                            JSONObject errJson = new JSONObject(errBody);
                            String serverMsg = errJson.optString("message", "");
                            if (!serverMsg.isEmpty()) msg = serverMsg;
                            JSONObject usage = errJson.optJSONObject("usageStatus");
                            if (usage != null) {
                                usedCount = usage.optInt("count", 0);
                                limitCount = usage.optInt("limit", 0);
                            }
                        } catch (Exception ignored) {}

                        showLimitReachedDialog(msg, usedCount, limitCount);
                    } else if (networkResponse != null && networkResponse.statusCode == 503) {
                        // AI temporarily unavailable
                        String msg = "AI analysis is temporarily unavailable. Please try again in a few minutes.";
                        try {
                            String errBody = new String(networkResponse.data, "UTF-8");
                            JSONObject errJson = new JSONObject(errBody);
                            String serverMsg = errJson.optString("message", "");
                            if (!serverMsg.isEmpty()) msg = serverMsg;
                        } catch (Exception ignored) {}
                        Utilities.toastLong(requireContext(), msg);
                    } else {
                        Utilities.toast(requireContext(), "Error checking food item. Please try again.");
                    }
                }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + token);
                headers.put("Content-Type", "application/json");
                return headers;
            }
        };

        request.setRetryPolicy(new DefaultRetryPolicy(30000, 0, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        Volley.newRequestQueue(requireContext()).add(request);
    }

    private void showResult(String recommendation, String reason) {
        resultContainer.setVisibility(View.VISIBLE);

        switch (recommendation.toLowerCase()) {
            case "strong_yes":
                recommendationText.setText("Definitely good for you");
                recommendationText.setTextColor(Color.parseColor("#4CAF50"));
                break;
            case "yes":
                recommendationText.setText("Generally okay");
                recommendationText.setTextColor(Color.parseColor("#8BC34A"));
                break;
            case "moderate":
                recommendationText.setText("Okay in moderation");
                recommendationText.setTextColor(Color.parseColor("#FFC107"));
                break;
            case "no":
                recommendationText.setText("Not recommended");
                recommendationText.setTextColor(Color.parseColor("#FF5722"));
                break;
            case "strong_no":
                recommendationText.setText("Absolutely avoid");
                recommendationText.setTextColor(Color.parseColor("#F44336"));
                break;
            default:
                recommendationText.setText("Unknown");
                recommendationText.setTextColor(Color.GRAY);
                break;
        }

        reasonText.setText(reason);

        // Reset thumbs UI for the new verdict. Only show feedback row if we have a historyId
        // to attach the reaction to — otherwise the POST would be meaningless.
        if (feedbackRow != null) {
            if (currentHistoryId != null && !currentHistoryId.isEmpty()) {
                feedbackRow.setVisibility(View.VISIBLE);
                thumbUp.setEnabled(true);
                thumbDown.setEnabled(true);
                applyReactionTint();
            } else {
                feedbackRow.setVisibility(View.GONE);
            }
        }
    }

    private NutriCheckActivity getNutriCheckActivity() {
        if (getActivity() instanceof NutriCheckActivity) {
            return (NutriCheckActivity) getActivity();
        }
        return null;
    }

    private void showLimitReachedDialog(String message, int usedCount, int limitCount) {
        if (getContext() == null) return;

        Utils.ProStatusManager proManager = Utils.ProStatusManager.getInstance(requireContext());
        boolean isPro = proManager.isProUser();
        String tier = proManager.getUserTier();

        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);

        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#1A1A1A"));
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        // Icon / header
        int iconSizePx = (int) (40 * getResources().getDisplayMetrics().density);
        ImageView icon = new ImageView(requireContext());
        icon.setImageResource(R.drawable.ic_warning);
        icon.setImageTintList(ColorStateList.valueOf(Color.parseColor("#FFC107")));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSizePx, iconSizePx);
        iconParams.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(icon, iconParams);

        // Title
        TextView title = new TextView(requireContext());
        title.setText("Monthly Limit Reached");
        title.setTextColor(Color.parseColor("#FFC107"));
        title.setTextSize(18);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = (int) (12 * getResources().getDisplayMetrics().density);
        root.addView(title, titleParams);

        // Usage bar
        if (limitCount > 0) {
            TextView usageText = new TextView(requireContext());
            usageText.setText(usedCount + " / " + limitCount + " scans used");
            usageText.setTextColor(Color.parseColor("#B0B0B0"));
            usageText.setTextSize(14);
            usageText.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams usageParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            usageParams.topMargin = (int) (12 * getResources().getDisplayMetrics().density);
            root.addView(usageText, usageParams);

            // Progress bar
            ProgressBar usageBar = new ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal);
            usageBar.setMax(limitCount);
            usageBar.setProgress(usedCount);
            usageBar.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#FFC107")));
            usageBar.setProgressBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#333333")));
            LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, (int) (8 * getResources().getDisplayMetrics().density));
            barParams.topMargin = (int) (8 * getResources().getDisplayMetrics().density);
            root.addView(usageBar, barParams);
        }

        // Message
        TextView msg = new TextView(requireContext());
        String tierLabel = isPro ? tier.substring(0, 1).toUpperCase() + tier.substring(1) : "Free";
        String body = limitCount > 0
                ? "Your " + tierLabel + " plan includes " + limitCount + " NutriCheck scans per month. Resets at the start of next month."
                : "You've used all your NutriCheck scans for this month. Resets at the start of next month.";
        msg.setText(body);
        msg.setTextColor(Color.parseColor("#999999"));
        msg.setTextSize(13);
        msg.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams msgParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        msgParams.topMargin = (int) (16 * getResources().getDisplayMetrics().density);
        root.addView(msg, msgParams);

        // Action button
        com.google.android.material.button.MaterialButton actionBtn =
                new com.google.android.material.button.MaterialButton(requireContext());
        actionBtn.setCornerRadius((int) (8 * getResources().getDisplayMetrics().density));
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnParams.topMargin = (int) (20 * getResources().getDisplayMetrics().density);

        if (!isPro || "free".equals(tier)) {
            // Free user — show upgrade
            actionBtn.setText("Upgrade Plan");
            actionBtn.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#008B8B")));
            actionBtn.setTextColor(Color.WHITE);
            actionBtn.setOnClickListener(v -> {
                dialog.dismiss();
                new ProUpgradeDialog(requireActivity()).show(pro -> {});
            });
        } else {
            // Already paid — show "Got it"
            actionBtn.setText("Got It");
            actionBtn.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#2A2A2A")));
            actionBtn.setTextColor(Color.parseColor("#008B8B"));
            actionBtn.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#008B8B")));
            actionBtn.setStrokeWidth((int) (1 * getResources().getDisplayMetrics().density));
            actionBtn.setOnClickListener(v -> dialog.dismiss());
        }
        root.addView(actionBtn, btnParams);

        // Dismiss text button
        if (!isPro || "free".equals(tier)) {
            TextView dismiss = new TextView(requireContext());
            dismiss.setText("Not Now");
            dismiss.setTextColor(Color.parseColor("#666666"));
            dismiss.setTextSize(14);
            dismiss.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams dismissParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            dismissParams.topMargin = (int) (12 * getResources().getDisplayMetrics().density);
            dismiss.setOnClickListener(v -> dialog.dismiss());
            root.addView(dismiss, dismissParams);
        }

        dialog.setContentView(root);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.85),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.show();
    }
}

package Utils;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.animation.ObjectAnimator;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.animation.DecelerateInterpolator;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.Request;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import org.json.JSONArray;
import org.json.JSONObject;

import com.example.richhealth.Activities.TokenManager;
import com.example.richhealth.R;
import com.example.richhealth.Activities.MainActivity;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import Models.PlanOption;

/**
 * Backend-driven Pro upgrade dialog with a swipeable plan card pager.
 *
 * Plans are fetched from GET /api/payment/plans, with fallback to PlanOption.getFallbackPlans().
 * The dialog shows one plan card at a time (full content: icon, price, stats, features).
 * Users swipe left/right to compare plans, or tap the segmented chip tabs to jump directly.
 * A 24dp peek on each side signals adjacent cards are available.
 *
 * Usage:
 *   ProUpgradeDialog dlg = new ProUpgradeDialog(activity);
 *   dlg.setLimitContext("You've reached your monthly limit.");  // optional
 *   dlg.show(isPro -> { ... });
 */
public class ProUpgradeDialog implements PaymentManager.PaymentCallback,
        PaymentManager.FamilyMemberSelectionListener {

    private static final String TAG = "ProUpgradeDialog";

    // Per-tier accent colours
    private static final int COLOR_PLUS  = 0xFF4FC3F7; // sky-blue  — Basic
    private static final int COLOR_PRO   = 0xFF008b8b; // brand teal — Pro
    private static final int COLOR_ULTRA = 0xFFFFB300; // amber/gold — Ultra

    private final Context context;
    private final Activity activity;
    private final ProStatusManager proStatusManager;
    private final PaymentManager paymentManager;
    private final PaymentService paymentService;

    private ProUpgradeCallback callback;
    private ProStatusResult currentStatus;

    private List<PlanOption> plans = new ArrayList<>();
    private PlanOption selectedPlan = null;
    private String limitContext = null;

    // Coupon state — applied is non-null only after backend confirms validity
    private String appliedCouponCode = null;
    private double appliedCouponFinalAmount = 0;
    private double appliedCouponDiscount = 0;
    private int appliedCouponPlanId = 0;

    // Dialog views
    private Dialog upgradeDialog;
    private MaterialButton upgradeButton;
    private RecyclerView plansPager;
    private LinearLayout chipsRow;
    private LinearLayoutManager pagerLayoutManager;
    private PagerSnapHelper snapHelper;

    // Chip views for selection state updates (parallel to upgradeable list)
    private final List<View> chipViews = new ArrayList<>();
    private ObjectAnimator logoAnimator;

    // ── Stat tile model ───────────────────────────────────────────────────────
    private static class Stat {
        final String value, label;
        final int iconRes;
        Stat(String v, String l, int icon) { value = v; label = l; iconRes = icon; }
    }

    // ── Public interface ──────────────────────────────────────────────────────
    public interface ProUpgradeCallback {
        void onProStatusChanged(boolean isPro);
    }

    // ── Constructor ───────────────────────────────────────────────────────────
    public ProUpgradeDialog(Activity activity) {
        this.context = activity;
        this.activity = activity;
        this.proStatusManager = ProStatusManager.getInstance(context);
        this.paymentManager = new PaymentManager(context);
        this.paymentService = new PaymentService(context);
        this.paymentManager.setFamilyMemberSelectionListener(this);
    }

    public void setLimitContext(String message) {
        this.limitContext = message;
    }

    // ── Entry point ───────────────────────────────────────────────────────────
    public void show(ProUpgradeCallback callback) {
        this.callback = callback;
        SimpleProgress progress = SimpleProgress.show(activity, "Loading subscription info…");

        paymentService.getProStatus(new PaymentService.PaymentCallback() {
            @Override public void onSuccess(ProStatusResult result) {
                progress.hide();
                currentStatus = result;
                proStatusManager.setProStatusComplete(
                        result.isPro(), result.getExpiryDate(),
                        result.getPlan(), result.getTransactionId());
                proStatusManager.setFamilyPlanInfo(
                        result.isFamilyPlanOwner(), result.isGrantedPro(),
                        result.getProGrantedBy(), result.getFamilyProMemberCount(),
                        result.getMaxFamilyMembers());
                if (result.isPro()) showProManagementDialog(result);
                else openUpgradeDialog();
            }

            @Override public void onError(String errorMessage) {
                progress.hide();
                Log.e(TAG, "Pro status error: " + errorMessage);
                if (proStatusManager.isProUser()) showProManagementDialog(null);
                else openUpgradeDialog();
            }
        });
    }

    /**
     * Opens the upgrade/purchase dialog directly, bypassing the pro-status
     * routing in show() (which would open the management screen for Pro users).
     * Used by the Plan tab's Membership "Upgrade" button now that subscription
     * management lives inline.
     */
    public void showUpgrade(ProUpgradeCallback callback) {
        this.callback = callback;
        openUpgradeDialog();
    }

    // ── Upgrade dialog ────────────────────────────────────────────────────────
    private void openUpgradeDialog() {
        upgradeDialog = new Dialog(context, R.style.DialogTheme);
        upgradeDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        upgradeDialog.setContentView(R.layout.dialog_pro_upgrade);

        Window w = upgradeDialog.getWindow();
        if (w != null) {
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
            lp.copyFrom(w.getAttributes());
            lp.width = WindowManager.LayoutParams.MATCH_PARENT;
            lp.height = WindowManager.LayoutParams.MATCH_PARENT;
            w.setAttributes(lp);
            w.setBackgroundDrawable(new ColorDrawable(Color.parseColor("#1A1A1A")));
            w.setWindowAnimations(R.style.DialogAnimationSlideRight);
        }

        upgradeButton = upgradeDialog.findViewById(R.id.upgrade_button);
        plansPager    = upgradeDialog.findViewById(R.id.plans_pager);
        chipsRow      = upgradeDialog.findViewById(R.id.plans_chip_row);

        // Limit context banner
        TextView limitBanner = upgradeDialog.findViewById(R.id.limit_context_banner);
        if (limitContext != null && !limitContext.isEmpty()) {
            limitBanner.setText(limitContext);
            limitBanner.setVisibility(View.VISIBLE);
        }

        upgradeDialog.findViewById(R.id.cancel_button)
                .setOnClickListener(v -> upgradeDialog.dismiss());

        upgradeButton.setEnabled(false);
        upgradeButton.setOnClickListener(v -> onUpgradeClicked());

        setupCouponRow();

        upgradeDialog.show();

        // Perpetually spin the logo (same pattern as SimpleProgress / LoginActivity)
        ImageView logo = upgradeDialog.findViewById(R.id.dialog_logo);
        if (logo != null) {
            logoAnimator = ObjectAnimator.ofFloat(logo, View.ROTATION, 0f, 360f);
            logoAnimator.setDuration(3000);
            logoAnimator.setRepeatCount(ObjectAnimator.INFINITE);
            logoAnimator.setInterpolator(new DecelerateInterpolator());
            logoAnimator.start();
        }

        // Teal shadow tint on API 28+
        MaterialCardView dialogCard = upgradeDialog.findViewById(R.id.dialog_card);
        if (android.os.Build.VERSION.SDK_INT >= 28 && dialogCard != null) {
            dialogCard.setOutlineAmbientShadowColor(Color.parseColor("#40008b8b"));
            dialogCard.setOutlineSpotShadowColor(Color.parseColor("#60008b8b"));
        }

        upgradeDialog.setOnDismissListener(d -> {
            if (logoAnimator != null) logoAnimator.cancel();
        });

        // Fetch plans while dialog shows the loading spinner
        paymentService.getPlans(new PaymentService.PlansCallback() {
            @Override public void onSuccess(List<PlanOption> fetchedPlans, String currentTier) {
                plans = fetchedPlans;
                showPlansError(false);
                buildPlanUI(currentTier);
            }

            @Override public void onError(String errorMessage) {
                Log.w(TAG, "Plans fetch failed, using fallback: " + errorMessage);
                plans = PlanOption.getFallbackPlans();
                showPlansError(true);
                buildPlanUI(proStatusManager.getUserTier());
            }
        });
    }

    // ── Build the pager UI once plans are known ───────────────────────────────
    private void buildPlanUI(String currentTier) {
        if (upgradeDialog == null || !upgradeDialog.isShowing()) return;

        // Hide spinner, show pager container
        upgradeDialog.findViewById(R.id.plans_loading_container).setVisibility(View.GONE);
        upgradeDialog.findViewById(R.id.plan_selector_container).setVisibility(View.VISIBLE);

        // "Currently on: Free" label — styled as a small chip
        TextView currentLabel = upgradeDialog.findViewById(R.id.current_tier_label);
        if (currentLabel != null && currentTier != null && !currentTier.isEmpty()) {
            String display = currentTier.substring(0, 1).toUpperCase() + currentTier.substring(1);
            currentLabel.setText("On: " + display);
            currentLabel.setTextColor(Color.WHITE);
            currentLabel.setTypeface(null, Typeface.BOLD);
            GradientDrawable tierChip = new GradientDrawable();
            tierChip.setShape(GradientDrawable.RECTANGLE);
            tierChip.setCornerRadius(dpToPx(100));
            tierChip.setColor(Color.parseColor("#2A2A2A"));
            tierChip.setStroke(dpToPx(1), Color.parseColor("#008b8b"));
            currentLabel.setBackground(tierChip);
            currentLabel.setPadding(dpToPx(10), dpToPx(4), dpToPx(10), dpToPx(4));
            currentLabel.setVisibility(View.VISIBLE);
        }

        List<PlanOption> upgradeable = getUpgradeablePlans(plans, currentTier);
        if (upgradeable.isEmpty()) upgradeable = plans;

        // Build segmented chip tabs
        buildChips(upgradeable);

        // Set up RecyclerView pager
        pagerLayoutManager = new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false);
        plansPager.setLayoutManager(pagerLayoutManager);
        plansPager.setClipToPadding(false);
        plansPager.setClipChildren(false);
        int peekPx = dpToPx(24);
        plansPager.setPadding(peekPx, 0, peekPx, 0);

        // Gap between cards
        plansPager.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override public void getItemOffsets(@NonNull android.graphics.Rect out,
                    @NonNull View view, @NonNull RecyclerView parent,
                    @NonNull RecyclerView.State state) {
                out.right = dpToPx(10);
            }
        });

        snapHelper = new PagerSnapHelper();
        snapHelper.attachToRecyclerView(plansPager);

        final List<PlanOption> finalUpgradeable = upgradeable;
        plansPager.setAdapter(new PlanPagerAdapter(upgradeable));

        // Update chip + button when swipe settles
        plansPager.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrollStateChanged(@NonNull RecyclerView rv, int state) {
                if (state == RecyclerView.SCROLL_STATE_IDLE) {
                    View snap = snapHelper.findSnapView(pagerLayoutManager);
                    if (snap != null) {
                        int pos = pagerLayoutManager.getPosition(snap);
                        onPageChanged(pos, finalUpgradeable);
                    }
                }
            }
        });

        // Default page: most popular, or index 0
        int defaultIdx = 0;
        for (int i = 0; i < upgradeable.size(); i++) {
            if (upgradeable.get(i).isMostPopular()) { defaultIdx = i; break; }
        }
        final int startIdx = defaultIdx;
        plansPager.post(() -> {
            plansPager.scrollToPosition(startIdx);
            onPageChanged(startIdx, finalUpgradeable);
        });
    }

    // ── Segmented chip tabs ───────────────────────────────────────────────────
    private void buildChips(List<PlanOption> upgradeable) {
        chipsRow.removeAllViews();
        chipViews.clear();

        // Outer pill container style
        GradientDrawable containerBg = new GradientDrawable();
        containerBg.setShape(GradientDrawable.RECTANGLE);
        containerBg.setCornerRadius(dpToPx(100));
        containerBg.setColor(Color.parseColor("#1C1C1C"));
        containerBg.setStroke(dpToPx(1), Color.parseColor("#282828"));
        chipsRow.setBackground(containerBg);
        chipsRow.setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));

        for (int i = 0; i < upgradeable.size(); i++) {
            final int idx = i;
            PlanOption plan = upgradeable.get(i);

            TextView chip = new TextView(context);
            String shortName = plan.getName().replace("RichHealth ", "");
            chip.setText(shortName);
            chip.setTextSize(13f);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dpToPx(18), dpToPx(8), dpToPx(18), dpToPx(8));
            chip.setTypeface(null, Typeface.BOLD);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            chip.setLayoutParams(lp);

            chip.setOnClickListener(v -> {
                plansPager.smoothScrollToPosition(idx);
                // Also fire immediately for chip feel
                onPageChanged(idx, upgradeable);
            });

            chipsRow.addView(chip);
            chipViews.add(chip);
        }

        updateChipSelection(0, upgradeable);
    }

    private void updateChipSelection(int activePos, List<PlanOption> upgradeable) {
        for (int i = 0; i < chipViews.size(); i++) {
            TextView chip = (TextView) chipViews.get(i);
            if (i == activePos && i < upgradeable.size()) {
                int accentColor = getPlanColor(upgradeable.get(i).getTierKey());
                GradientDrawable activeBg = new GradientDrawable();
                activeBg.setShape(GradientDrawable.RECTANGLE);
                activeBg.setCornerRadius(dpToPx(100));
                activeBg.setColor(accentColor);
                chip.setBackground(activeBg);
                chip.setTextColor(Color.WHITE);
            } else {
                chip.setBackground(null);
                chip.setTextColor(Color.parseColor("#555555"));
            }
        }
    }

    private void onPageChanged(int pos, List<PlanOption> upgradeable) {
        if (pos < 0 || pos >= upgradeable.size()) return;
        selectedPlan = upgradeable.get(pos);
        // If a coupon was applied for a different plan, clear it — must re-validate per plan
        if (appliedCouponCode != null && appliedCouponPlanId != selectedPlan.getPlanId()) {
            appliedCouponCode = null;
            appliedCouponDiscount = 0;
            appliedCouponFinalAmount = 0;
            appliedCouponPlanId = 0;
            if (upgradeDialog != null) {
                View statusRow = upgradeDialog.findViewById(R.id.coupon_status_row);
                View ticketRow = upgradeDialog.findViewById(R.id.coupon_row);
                MaterialButton ap = upgradeDialog.findViewById(R.id.coupon_apply_button);
                if (statusRow != null) statusRow.setVisibility(View.GONE);
                if (ap != null) {
                    ap.setText("APPLY");
                    ap.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                            Color.parseColor("#008b8b")));
                }
                if (ticketRow != null) ticketRow.setBackgroundResource(R.drawable.bg_coupon_ticket);
            }
        }
        updateChipSelection(pos, upgradeable);
        updateUpgradeButton(selectedPlan);
        upgradeButton.setEnabled(true);
        // Subtle plan-name entrance animation on the visible card
        animateVisiblePlanName();
    }

    private void animateVisiblePlanName() {
        if (plansPager == null || pagerLayoutManager == null) return;
        View snap = snapHelper != null ? snapHelper.findSnapView(pagerLayoutManager) : null;
        if (snap == null) {
            // Fallback: first visible
            snap = pagerLayoutManager.findViewByPosition(pagerLayoutManager.findFirstVisibleItemPosition());
        }
        if (snap == null) return;
        TextView name = snap.findViewById(R.id.plan_name);
        if (name == null) return;
        name.setAlpha(0f);
        name.setTranslationY(dpToPx(6));
        name.animate().alpha(1f).translationY(0f).setDuration(220)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    // ── RecyclerView pager adapter ────────────────────────────────────────────
    private class PlanPagerAdapter extends RecyclerView.Adapter<PlanPagerAdapter.VH> {
        private final List<PlanOption> data;
        PlanPagerAdapter(List<PlanOption> data) { this.data = data; }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(context).inflate(R.layout.item_plan_card, parent, false);
            // Card width = pager width minus the two 24dp side paddings
            // pager width = dialog card width (screen - 32dp dialog margin)
            int screenPx = context.getResources().getDisplayMetrics().widthPixels;
            int cardWidthPx = screenPx - dpToPx(32) - dpToPx(48); // 32=dialog margins, 48=peek padding
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                    cardWidthPx, RecyclerView.LayoutParams.MATCH_PARENT);
            v.setLayoutParams(lp);
            return new VH(v);
        }

        @Override public void onBindViewHolder(@NonNull VH holder, int pos) {
            bindPlanCard(holder.itemView, data.get(pos));
        }

        @Override public int getItemCount() { return data.size(); }

        class VH extends RecyclerView.ViewHolder {
            VH(View v) { super(v); }
        }
    }

    // ── Bind a plan card (called by adapter for each page) ────────────────────
    private void bindPlanCard(View card, PlanOption plan) {
        int accentColor = getPlanColor(plan.getTierKey());

        // ── Icon circle ───────────────────────────────────────────────────────
        FrameLayout iconBg  = card.findViewById(R.id.plan_icon_bg);
        ImageView iconView  = card.findViewById(R.id.plan_icon);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(Color.argb(35, Color.red(accentColor),
                Color.green(accentColor), Color.blue(accentColor)));
        iconBg.setBackground(circle);
        iconView.setImageResource(getPlanIconRes(plan.getTierKey()));
        iconView.setColorFilter(accentColor);

        // ── Card stroke (plan identity color) ─────────────────────────────────
        if (card instanceof MaterialCardView) {
            ((MaterialCardView) card).setStrokeColor(
                    Color.argb(70, Color.red(accentColor),
                            Color.green(accentColor), Color.blue(accentColor)));
            ((MaterialCardView) card).setStrokeWidth(dpToPx(1));
        }

        // ── Name + badge + tagline ────────────────────────────────────────────
        ((TextView) card.findViewById(R.id.plan_name)).setText(plan.getName());
        if (plan.isMostPopular()) {
            card.findViewById(R.id.plan_popular_badge).setVisibility(View.VISIBLE);
        }
        ((TextView) card.findViewById(R.id.plan_tagline)).setText(getPlanTagline(plan.getTierKey()));

        // ── Price ─────────────────────────────────────────────────────────────
        TextView priceView = card.findViewById(R.id.plan_price);
        priceView.setText(formatPrice(plan.getPrice()));
        priceView.setTextColor(accentColor);

        // ── Discount msg pill (inline with price) ────────────────────────────
        TextView discountMsg = card.findViewById(R.id.plan_discount_msg);
        if (plan.hasDiscount() && !plan.getDiscountMessage().isEmpty()) {
            discountMsg.setText(plan.getDiscountMessage());
            discountMsg.setVisibility(View.VISIBLE);
        } else {
            discountMsg.setVisibility(View.GONE);
        }

        // ── Original price (strikethrough) ────────────────────────────────────
        if (plan.hasDiscount()) {
            TextView origView = card.findViewById(R.id.plan_original_price);
            origView.setText(formatPrice(plan.getOriginalPrice()));
            origView.setPaintFlags(origView.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            origView.setVisibility(View.VISIBLE);
        }

        // ── Duration ──────────────────────────────────────────────────────────
        TextView durationView = card.findViewById(R.id.plan_duration);
        durationView.setText(plan.getDurationMonths() == 12 ? "12 months" : plan.getDurationMonths() + " months");
        durationView.setTextColor(Color.parseColor("#BBBBBB"));
        durationView.setTypeface(null, Typeface.BOLD);

        // ── Stats row ─────────────────────────────────────────────────────────
        LinearLayout statsRow = card.findViewById(R.id.plan_stats_row);
        statsRow.removeAllViews();
        for (Stat s : getKeyStats(plan.getTierKey())) {
            statsRow.addView(buildStatTile(s.value, s.label, s.iconRes, accentColor));
        }

        // ── Feature list ──────────────────────────────────────────────────────
        LinearLayout featuresList = card.findViewById(R.id.plan_features_list);
        featuresList.removeAllViews();
        for (String feature : plan.getFeatures()) {
            featuresList.addView(buildFeatureRow(feature, accentColor));
        }
    }

    // ── Upgrade button ────────────────────────────────────────────────────────
    private void updateUpgradeButton(PlanOption plan) {
        String shortName = plan.getName().replace("RichHealth ", "");
        double price = effectivePriceFor(plan);
        upgradeButton.setText("Get " + shortName + "  ·  " + formatPrice(price));
        int accent = getPlanColor(plan.getTierKey());
        upgradeButton.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(accent));
    }

    private double effectivePriceFor(PlanOption plan) {
        if (appliedCouponCode != null && appliedCouponPlanId == plan.getPlanId()) {
            return appliedCouponFinalAmount;
        }
        return plan.getPrice();
    }

    // ── Coupon row ────────────────────────────────────────────────────────────
    private void setupCouponRow() {
        if (upgradeDialog == null) return;
        final EditText input = upgradeDialog.findViewById(R.id.coupon_input);
        final MaterialButton apply = upgradeDialog.findViewById(R.id.coupon_apply_button);
        final View statusRow = upgradeDialog.findViewById(R.id.coupon_status_row);
        final ImageView statusIcon = upgradeDialog.findViewById(R.id.coupon_status_icon);
        final TextView status = upgradeDialog.findViewById(R.id.coupon_status);
        final View ticketRow = upgradeDialog.findViewById(R.id.coupon_row);
        if (input == null || apply == null || status == null || statusRow == null) return;

        Runnable resetCoupon = () -> {
            appliedCouponCode = null;
            appliedCouponDiscount = 0;
            appliedCouponFinalAmount = 0;
            appliedCouponPlanId = 0;
            statusRow.setVisibility(View.GONE);
            apply.setText("APPLY");
            apply.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    Color.parseColor("#008b8b")));
            ticketRow.setBackgroundResource(R.drawable.bg_coupon_ticket);
            if (selectedPlan != null) updateUpgradeButton(selectedPlan);
        };

        // Editing the field after applying clears the applied state
        input.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                if (appliedCouponCode != null) resetCoupon.run();
            }
        });

        apply.setOnClickListener(v -> {
            // If already applied, the button removes the coupon
            if (appliedCouponCode != null) {
                input.setText("");
                resetCoupon.run();
                return;
            }
            String code = input.getText() == null ? "" : input.getText().toString().trim();
            if (code.isEmpty()) {
                Utilities.toast(context, "Enter a coupon code");
                return;
            }
            if (selectedPlan == null) {
                Utilities.toast(context, "Select a plan first");
                return;
            }
            apply.setEnabled(false);
            statusRow.setVisibility(View.VISIBLE);
            statusIcon.setImageResource(R.drawable.ic_pending);
            statusIcon.setColorFilter(Color.parseColor("#999999"));
            status.setTextColor(Color.parseColor("#999999"));
            status.setText("Checking " + code + "…");

            final PlanOption planAtRequest = selectedPlan;
            paymentService.validateCoupon(code, planAtRequest.getPlanId(),
                    new PaymentService.CouponCallback() {
                        @Override public void onResult(PaymentService.CouponResult r) {
                            apply.setEnabled(true);
                            if (!r.valid) {
                                appliedCouponCode = null;
                                statusIcon.setImageResource(R.drawable.ic_warning);
                                statusIcon.setColorFilter(Color.parseColor("#E57373"));
                                status.setTextColor(Color.parseColor("#E57373"));
                                status.setText(r.message != null && !r.message.isEmpty()
                                        ? r.message : "Invalid coupon");
                                return;
                            }
                            appliedCouponCode = r.code;
                            appliedCouponDiscount = r.discount;
                            appliedCouponFinalAmount = r.finalAmount;
                            appliedCouponPlanId = planAtRequest.getPlanId();
                            statusIcon.setImageResource(R.drawable.ic_success);
                            statusIcon.setColorFilter(Color.parseColor("#4CAF50"));
                            status.setTextColor(Color.parseColor("#4CAF50"));
                            status.setText(r.code + " applied — you save " + formatPrice(r.discount));
                            apply.setText("REMOVE");
                            apply.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                                    Color.parseColor("#4A4A4A")));
                            ticketRow.setBackgroundResource(R.drawable.bg_coupon_ticket_applied);
                            if (selectedPlan != null) updateUpgradeButton(selectedPlan);
                        }
                        @Override public void onError(String errorMessage) {
                            apply.setEnabled(true);
                            statusIcon.setImageResource(R.drawable.ic_warning);
                            statusIcon.setColorFilter(Color.parseColor("#E57373"));
                            status.setTextColor(Color.parseColor("#E57373"));
                            status.setText(errorMessage != null ? errorMessage : "Failed to validate");
                        }
                    });
        });
    }

    // ── Stat tile ─────────────────────────────────────────────────────────────
    private View buildStatTile(String value, String label, int iconRes, int accentColor) {
        LinearLayout tile = new LinearLayout(context);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        p.setMargins(dpToPx(3), 0, dpToPx(3), 0);
        tile.setLayoutParams(p);
        tile.setPadding(dpToPx(4), dpToPx(8), dpToPx(4), dpToPx(8));

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dpToPx(10));
        bg.setColor(Color.parseColor("#141414"));
        tile.setBackground(bg);

        // Icon
        ImageView icon = new ImageView(context);
        LinearLayout.LayoutParams iconP = new LinearLayout.LayoutParams(dpToPx(20), dpToPx(20));
        iconP.setMargins(0, 0, 0, dpToPx(3));
        iconP.gravity = Gravity.CENTER_HORIZONTAL;
        icon.setLayoutParams(iconP);
        icon.setImageResource(iconRes);
        icon.setColorFilter(accentColor);
        tile.addView(icon);

        // Big value
        TextView valTv = new TextView(context);
        valTv.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        valTv.setText(value);
        valTv.setTextColor(accentColor);
        valTv.setTextSize(24f);
        valTv.setTypeface(null, Typeface.BOLD);
        valTv.setGravity(Gravity.CENTER);
        tile.addView(valTv);

        // Label — first line bold white, second line smaller grey
        TextView lblTv = new TextView(context);
        lblTv.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        String[] parts = label.split("\n", 2);
        if (parts.length == 2) {
            SpannableString ss = new SpannableString(parts[0] + "\n" + parts[1]);
            ss.setSpan(new StyleSpan(Typeface.BOLD), 0, parts[0].length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ss.setSpan(new ForegroundColorSpan(Color.parseColor("#CCCCCC")), 0, parts[0].length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ss.setSpan(new ForegroundColorSpan(Color.parseColor("#888888")), parts[0].length() + 1, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ss.setSpan(new RelativeSizeSpan(0.85f), parts[0].length() + 1, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            lblTv.setText(ss);
        } else {
            lblTv.setText(label);
            lblTv.setTypeface(null, Typeface.BOLD);
            lblTv.setTextColor(Color.parseColor("#CCCCCC"));
        }
        lblTv.setTextSize(9.5f);
        lblTv.setGravity(Gravity.CENTER);
        lblTv.setLineSpacing(0f, 1.15f);
        tile.addView(lblTv);

        return tile;
    }

    // ── Feature row ───────────────────────────────────────────────────────────
    private View buildFeatureRow(String feature, int accentColor) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowP.setMargins(0, 0, 0, dpToPx(6));
        row.setLayoutParams(rowP);
        row.setGravity(Gravity.CENTER_VERTICAL);

        // Icon in a small tinted rounded-square container
        FrameLayout iconContainer = new FrameLayout(context);
        LinearLayout.LayoutParams icContP = new LinearLayout.LayoutParams(dpToPx(26), dpToPx(26));
        icContP.setMargins(0, 0, dpToPx(10), 0);
        iconContainer.setLayoutParams(icContP);
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setShape(GradientDrawable.RECTANGLE);
        iconBg.setCornerRadius(dpToPx(6));
        iconBg.setColor(Color.argb(25, Color.red(accentColor),
                Color.green(accentColor), Color.blue(accentColor)));
        iconContainer.setBackground(iconBg);

        ImageView icon = new ImageView(context);
        FrameLayout.LayoutParams icP = new FrameLayout.LayoutParams(dpToPx(14), dpToPx(14));
        icP.gravity = Gravity.CENTER;
        icon.setLayoutParams(icP);
        icon.setImageResource(getFeatureIconRes(feature));
        icon.setColorFilter(accentColor);
        iconContainer.addView(icon);

        // Feature text — bold main part, grey qualifier
        TextView tv = new TextView(context);
        LinearLayout.LayoutParams tvP = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tv.setLayoutParams(tvP);
        tv.setText(buildFeatureSpan(feature));
        tv.setTextSize(12.5f);
        tv.setLineSpacing(0f, 1.15f);

        row.addView(iconContainer);
        row.addView(tv);
        return row;
    }

    /**
     * Splits a feature string into a bold main part and a grey qualifier.
     * Split point: " per ", " (", " - ", or the start of a parenthesized clause.
     * e.g. "20 AI report analyses per month" → bold "20 AI report analyses" + grey " per month"
     */
    private SpannableString buildFeatureSpan(String feature) {
        int splitAt = -1;
        String[] qualifiers = {" per ", " (", " - ", " up to "};
        for (String q : qualifiers) {
            int idx = feature.toLowerCase().indexOf(q);
            if (idx > 0) { splitAt = idx; break; }
        }
        SpannableString ss = new SpannableString(feature);
        int boldEnd = splitAt > 0 ? splitAt : feature.length();
        ss.setSpan(new StyleSpan(Typeface.BOLD), 0, boldEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new ForegroundColorSpan(Color.parseColor("#FFFFFF")), 0, boldEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (splitAt > 0) {
            ss.setSpan(new ForegroundColorSpan(Color.parseColor("#999999")), splitAt, feature.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return ss;
    }

    // ── Plan metadata helpers ─────────────────────────────────────────────────
    private int getPlanColor(String tierKey) {
        switch (tierKey) {
            case "pro":   return COLOR_PRO;
            case "ultra": return COLOR_ULTRA;
            default:      return COLOR_PLUS;
        }
    }

    private int getPlanIconRes(String tierKey) {
        switch (tierKey) {
            case "pro":   return R.drawable.ic_premium;
            case "ultra": return R.drawable.ic_ai;
            default:      return R.drawable.ic_analytics;
        }
    }

    private String getPlanTagline(String tierKey) {
        switch (tierKey) {
            case "pro":   return "Full AI power + family health";
            case "ultra": return "Everything, unlimited, for a year";
            default:      return "Essential AI-powered health tools";
        }
    }

    private Stat[] getKeyStats(String tierKey) {
        switch (tierKey) {
            case "pro":
                return new Stat[]{
                    new Stat("10",  "AI Reports\nper month",  R.drawable.ic_medical_reports),
                    new Stat("50",  "Chats per\nsession",     R.drawable.ic_chat),
                    new Stat("2",   "Family\nMembers",        R.drawable.ic_family)
                };
            case "ultra":
                return new Stat[]{
                    new Stat("∞",   "AI Reports\nUnlimited",  R.drawable.ic_medical_reports),
                    new Stat("100", "Chats per\nsession",     R.drawable.ic_chat),
                    new Stat("5",   "Family\nMembers",        R.drawable.ic_family)
                };
            default: // plus
                return new Stat[]{
                    new Stat("5",   "AI Reports\nper month",  R.drawable.ic_medical_reports),
                    new Stat("25",  "Chats per\nsession",     R.drawable.ic_chat),
                    new Stat("All", "Health\nFeatures",       R.drawable.ic_analytics)
                };
        }
    }

    private int getFeatureIconRes(String feature) {
        String l = feature.toLowerCase();
        // Order matters: check the most specific keywords first so generic ones
        // ("analys", "track") don't poach more specific feature lines.
        if (l.contains("nutri")     || l.contains("meal"))      return R.drawable.ic_food;
        if (l.contains("medication"))                            return R.drawable.ic_medication;
        if (l.contains("aqi")       || l.contains("air"))       return R.drawable.ic_air;
        if (l.contains("doctor"))                                return R.drawable.ic_stethoscope;
        if (l.contains("podcast"))                               return R.drawable.ic_podcast;
        if (l.contains("family")    || l.contains("dependent")) return R.drawable.ic_family;
        if (l.contains("model")     || l.contains("ai "))       return R.drawable.ic_ai;
        if (l.contains("chat")      || l.contains("message"))   return R.drawable.ic_chat;
        if (l.contains("report")    || l.contains("analys"))    return R.drawable.ic_medical_reports;
        if (l.contains("support")   || l.contains("priority")
                                    || l.contains("onboarding")
                                    || l.contains("white-glove")) return R.drawable.ic_premium;
        if (l.contains("symptom"))                               return R.drawable.ic_heart_check;
        if (l.contains("check-in")  || l.contains("checkin"))   return R.drawable.ic_calendar_check;
        if (l.contains("share")     || l.contains("data"))      return R.drawable.ic_share;
        if (l.contains("health")    || l.contains("insight")
                                    || l.contains("track"))     return R.drawable.ic_analytics;
        return R.drawable.ic_success;
    }

    // ── Tier filtering ────────────────────────────────────────────────────────
    private List<PlanOption> getUpgradeablePlans(List<PlanOption> all, String currentTier) {
        int current = tierOrdinal(currentTier);
        List<PlanOption> result = new ArrayList<>();
        for (PlanOption p : all) {
            if (tierOrdinal(p.getTierKey()) > current) result.add(p);
        }
        return result;
    }

    private int tierOrdinal(String tier) {
        switch (tier) {
            case "plus":          return 1;
            case "pro":           return 2;
            case "ultra":         return 3;
            case "family":        return 3;
            case "family_member": return 2;
            default:              return 0;
        }
    }

    private void showPlansError(boolean show) {
        if (upgradeDialog == null || !upgradeDialog.isShowing()) return;
        View err = upgradeDialog.findViewById(R.id.plans_error_container);
        if (err != null) err.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    // ── Payment flow ──────────────────────────────────────────────────────────
    private void onUpgradeClicked() {
        if (selectedPlan == null) return;
        // Only forward the coupon if it was validated for the same plan the user is buying
        String couponToSend = (appliedCouponCode != null
                && appliedCouponPlanId == selectedPlan.getPlanId())
                ? appliedCouponCode : null;
        upgradeDialog.dismiss();
        paymentManager.setSelectedPlanType(selectedPlan.getPlanId());
        if (activity instanceof MainActivity) {
            ((MainActivity) activity).setPaymentManager(paymentManager);
        }
        paymentManager.startPaymentFlow(activity, selectedPlan, couponToSend, this);
    }

    // ── PaymentManager.PaymentCallback ────────────────────────────────────────
    @Override public void onPaymentInitiated() {
        Utilities.toast(context, "Complete payment in Razorpay");
    }

    @Override public void onPaymentSuccess(String plan) {
        SimpleProgress progress = SimpleProgress.show(activity, "Activating premium features…");
        paymentService.getProStatus(new PaymentService.PaymentCallback() {
            @Override public void onSuccess(ProStatusResult result) {
                progress.hide();
                currentStatus = result;
                proStatusManager.setProStatusComplete(
                        result.isPro(), result.getExpiryDate(),
                        result.getPlan(), result.getTransactionId());
                proStatusManager.setFamilyPlanInfo(
                        result.isFamilyPlanOwner(), result.isGrantedPro(),
                        result.getProGrantedBy(), result.getFamilyProMemberCount(),
                        result.getMaxFamilyMembers());
                showSuccessDialog(result.getPlanType());
                if (callback != null) callback.onProStatusChanged(true);
            }

            @Override public void onError(String errorMessage) {
                progress.hide();
                Log.e(TAG, "Error refreshing status after payment: " + errorMessage);
                showSuccessDialog(selectedPlan != null ? selectedPlan.getPlanId() : 2);
                if (callback != null) callback.onProStatusChanged(true);
            }
        });
    }

    @Override public void onPaymentFailed(String reason) {
        new androidx.appcompat.app.AlertDialog.Builder(context, R.style.DialogTheme)
                .setTitle("Payment Failed")
                .setMessage(reason)
                .setPositiveButton("Try Again", (d, w) -> openUpgradeDialog())
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override public void onPaymentCancelled() {
        Utilities.toast(context, "Payment cancelled");
    }

    // ── Success dialog ────────────────────────────────────────────────────────
    private void showSuccessDialog(int planType) {
        Dialog dialog = new Dialog(context, R.style.DialogTheme);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_payment_success);

        Window w = dialog.getWindow();
        if (w != null) {
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
            lp.copyFrom(w.getAttributes());
            lp.width = WindowManager.LayoutParams.MATCH_PARENT;
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
            w.setAttributes(lp);
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        TextView titleView   = dialog.findViewById(R.id.dialog_title);
        TextView messageView = dialog.findViewById(R.id.message_text);
        MaterialButton done  = dialog.findViewById(R.id.done_button);

        PlanOption matchedPlan = null;
        for (PlanOption p : plans) {
            if (p.getPlanId() == planType) { matchedPlan = p; break; }
        }

        String planName = matchedPlan != null ? matchedPlan.getName() : "RichHealth Premium";
        String expiry   = proStatusManager.getFormattedExpiryDate();
        titleView.setText("Welcome to " + planName + "!");
        messageView.setText("Payment successful! Your subscription is active until " + expiry
                + ".\n\nAll features are now unlocked.");

        done.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    // ── Pro management dialog ─────────────────────────────────────────────────
    /**
     * Modern card-based subscription dialog. Family-plan owners see an inline
     * RecyclerView of their connected relatives, reusing item_family_relationship.xml
     * (the same card used in the Health Hub Family Members panel) so add/remove
     * controls match the rest of the app.
     */
    private Dialog managementDialog;
    private List<JSONObject> familyMembersList = new ArrayList<>();
    private FamilyManagementAdapter familyAdapter;
    private int familyMemberMax = 5;

    private void showProManagementDialog(ProStatusResult status) {
        managementDialog = new Dialog(context, R.style.DialogTheme);
        managementDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        managementDialog.setContentView(R.layout.dialog_pro_management);

        Window w = managementDialog.getWindow();
        if (w != null) {
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
            lp.copyFrom(w.getAttributes());
            lp.width = WindowManager.LayoutParams.MATCH_PARENT;
            lp.height = WindowManager.LayoutParams.MATCH_PARENT;
            w.setAttributes(lp);
            w.setBackgroundDrawable(new ColorDrawable(Color.parseColor("#1A1A1A")));
            w.setWindowAnimations(R.style.DialogAnimationSlideRight);
        }

        String plan        = proStatusManager.getSubscriptionPlan();
        String expiryDate  = proStatusManager.getFormattedExpiryDate();
        String upgradeDate = proStatusManager.getFormattedUpgradeDate();

        if (status != null) {
            if (status.getExpiryDate() > 0) {
                expiryDate = new java.text.SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                        .format(new java.util.Date(status.getExpiryDate()));
            }
            if (status.getUpgradeDate() > 0) {
                upgradeDate = new java.text.SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                        .format(new java.util.Date(status.getUpgradeDate()));
            }
            if (status.getPlanType() > 0) {
                switch (status.getPlanType()) {
                    case 1: plan = "RichHealth Plus";   break;
                    case 2: plan = "RichHealth Pro";    break;
                    case 3: plan = "RichHealth Ultra";  break;
                    default: plan = status.getPlan();
                }
            } else {
                plan = status.getPlan();
            }
        }

        TextView planText  = managementDialog.findViewById(R.id.plan_text);
        TextView statusTxt = managementDialog.findViewById(R.id.status_text);
        TextView startTxt  = managementDialog.findViewById(R.id.start_date_text);
        TextView expiryTxt = managementDialog.findViewById(R.id.expiry_date_text);
        TextView grantedBy = managementDialog.findViewById(R.id.granted_by_info_text);
        TextView familyInfo = managementDialog.findViewById(R.id.family_info_text);
        TextView reportsInfo = managementDialog.findViewById(R.id.reports_info_text);
        ProgressBar reportsBar = managementDialog.findViewById(R.id.reports_progress);
        View reportsRow = managementDialog.findViewById(R.id.reports_row);
        View familySection = managementDialog.findViewById(R.id.family_section);
        ImageButton closeBtn = managementDialog.findViewById(R.id.close_button);
        MaterialButton ok = managementDialog.findViewById(R.id.ok_button);
        MaterialButton disable = managementDialog.findViewById(R.id.disable_button);

        // Use the unified PlanBadge naming + tier-tinted color so the management
        // dialog matches every other plan-display surface in the app.
        String tierKey = (status != null && status.getPlan() != null && !status.getPlan().isEmpty())
                ? status.getPlan() : proStatusManager.getUserTier();
        if (tierKey == null || tierKey.isEmpty()) tierKey = "pro";
        planText.setText(PlanBadge.fullLabelFor(tierKey));
        planText.setTextColor(PlanBadge.colorFor(context, tierKey));
        statusTxt.setText("ACTIVE");
        startTxt.setText(upgradeDate != null && !upgradeDate.isEmpty() ? upgradeDate : "—");
        expiryTxt.setText(expiryDate != null && !expiryDate.isEmpty() ? expiryDate : "—");

        if (status != null && status.getTotalReports() > 0) {
            reportsRow.setVisibility(View.VISIBLE);
            reportsInfo.setText(status.getReportsUsed() + "/" + status.getTotalReports());
            int pct = (int) Math.min(100, (status.getReportsUsed() * 100L) / Math.max(1, status.getTotalReports()));
            reportsBar.setProgress(pct);
        } else {
            reportsRow.setVisibility(View.GONE);
        }

        if (status != null && status.isGrantedPro()) {
            grantedBy.setVisibility(View.VISIBLE);
            String by = status.getProGrantedBy();
            grantedBy.setText("Pro shared via family plan"
                    + (by != null && !by.isEmpty() ? " by " + by : ""));
            planText.setText(PlanBadge.fullLabelFor("family_member"));
            planText.setTextColor(PlanBadge.colorFor(context, "family_member"));
        } else {
            grantedBy.setVisibility(View.GONE);
        }

        boolean isFamilyOwner = status != null
                && (status.getPlanType() == 3 || status.isFamilyPlanOwner());
        familyMemberMax = status != null && status.getMaxFamilyMembers() > 0
                ? status.getMaxFamilyMembers() : 5;

        if (isFamilyOwner) {
            familySection.setVisibility(View.VISIBLE);
            familyInfo.setText(status.getFamilyMembersCount() + "/" + familyMemberMax + " covered");

            RecyclerView rv = managementDialog.findViewById(R.id.family_recycler);
            rv.setLayoutManager(new LinearLayoutManager(context));
            familyMembersList = new ArrayList<>();
            familyAdapter = new FamilyManagementAdapter();
            rv.setAdapter(familyAdapter);

            loadFamilyMembersForManagement(familyInfo);
        } else {
            familySection.setVisibility(View.GONE);
        }

        // Upgrade CTA — visible to anyone below the top tier who isn't a covered
        // (granted-pro) family member. Tapping it dismisses Manage and opens the
        // upgrade page; getUpgradeablePlans() then filters to higher tiers only,
        // so a Plus user sees Pro+Ultra and a Pro user sees Ultra. Nothing here
        // is hardcoded — the next-tier set is derived from /api/payment/plans.
        MaterialButton upgradeCta = managementDialog.findViewById(R.id.upgrade_plan_button);
        boolean canUpgrade = tierOrdinal(tierKey) < 3
                && !(status != null && status.isGrantedPro());
        if (upgradeCta != null && canUpgrade) {
            upgradeCta.setVisibility(View.VISIBLE);
            // Tint the upgrade CTA with the next tier's identity color so the
            // call-to-action visually previews where the user is going.
            String nextTier = tierOrdinal(tierKey) <= 1 ? "pro" : "ultra";
            upgradeCta.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    PlanBadge.colorFor(context, nextTier)));
            upgradeCta.setOnClickListener(v -> {
                managementDialog.dismiss();
                openUpgradeDialog();
            });
            // Demote Done to a subtler outlined style so Upgrade reads as primary.
            ok.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    Color.parseColor("#222222")));
            ok.setStrokeColor(android.content.res.ColorStateList.valueOf(
                    Color.parseColor("#3A3A3A")));
            ok.setStrokeWidth(dpToPx(1));
        }

        closeBtn.setOnClickListener(v -> managementDialog.dismiss());
        ok.setOnClickListener(v -> managementDialog.dismiss());
        disable.setOnClickListener(v -> {
            proStatusManager.setProStatus(false);
            Utilities.toast(context, "Pro disabled (test only)");
            if (callback != null) callback.onProStatusChanged(false);
            managementDialog.dismiss();
        });

        managementDialog.show();
    }

    /**
     * Fetches accepted relatives and loads them into the inline RecyclerView.
     */
    private void loadFamilyMembersForManagement(TextView familyInfo) {
        View loading = managementDialog.findViewById(R.id.family_loading);
        View empty   = managementDialog.findViewById(R.id.family_empty);
        RecyclerView rv = managementDialog.findViewById(R.id.family_recycler);

        loading.setVisibility(View.VISIBLE);
        empty.setVisibility(View.GONE);
        rv.setVisibility(View.GONE);

        String token = TokenManager.getInstance(context).getToken();
        if (token == null) {
            loading.setVisibility(View.GONE);
            return;
        }
        String url = ApiConfig.BASE_URL + "/api/user/relationships";
        StringRequest request = new StringRequest(Request.Method.GET, url,
                response -> {
                    ApiConfig.logRestCall(url, true, "Relationships fetched");
                    loading.setVisibility(View.GONE);
                    try {
                        JSONObject root = new JSONObject(response);
                        JSONArray arr = root.optJSONArray("relationships");
                        familyMemberMax = root.optInt("maxFamilyMembers", familyMemberMax);
                        familyMembersList.clear();
                        int covered = 0;
                        if (arr != null) {
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject rel = arr.getJSONObject(i);
                                if (!"accepted".equals(rel.optString("status"))) continue;
                                String uid = rel.optString("userId", "");
                                if (uid.isEmpty()) continue;
                                familyMembersList.add(rel);
                                if (rel.optBoolean("isCoveredByMyPlan", false)) covered++;
                            }
                        }
                        familyInfo.setText(covered + "/" + familyMemberMax + " covered");
                        if (familyMembersList.isEmpty()) {
                            empty.setVisibility(View.VISIBLE);
                            rv.setVisibility(View.GONE);
                        } else {
                            empty.setVisibility(View.GONE);
                            rv.setVisibility(View.VISIBLE);
                            familyAdapter.notifyDataSetChanged();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing relationships", e);
                        empty.setVisibility(View.VISIBLE);
                    }
                },
                error -> {
                    ApiConfig.logRestCall(url, false, error.toString());
                    loading.setVisibility(View.GONE);
                    empty.setVisibility(View.VISIBLE);
                }) {
            @Override public Map<String, String> getHeaders() {
                Map<String, String> h = new HashMap<>();
                h.put("Authorization", "Bearer " + token);
                return h;
            }
        };
        Volley.newRequestQueue(context).add(request);
    }

    /**
     * Adapter reusing item_family_relationship.xml — the same row card the
     * Health Hub family panel uses. Add to Pro / Remove from Pro buttons
     * call paymentService.addFamilyMemberDirect / removeFamilyMember.
     */
    private class FamilyManagementAdapter extends RecyclerView.Adapter<FamilyManagementAdapter.VH> {
        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(context)
                    .inflate(R.layout.item_family_relationship, parent, false);
            return new VH(v);
        }

        @Override public int getItemCount() { return familyMembersList.size(); }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            JSONObject rel = familyMembersList.get(pos);
            String n = rel.optString("name", "");
            if (n.isEmpty()) n = rel.optString("email", "Unknown");
            final String name = n;
            String relationship = rel.optString("relationship", "");
            String email = rel.optString("email", "");
            boolean coveredByMe = rel.optBoolean("isCoveredByMyPlan", false);
            boolean isPro = rel.optBoolean("isPro", false);
            String proSource = rel.optString("proSource", "");
            final String userId = rel.optString("userId", "");

            ((TextView) h.itemView.findViewById(R.id.name_text)).setText(name);
            ((TextView) h.itemView.findViewById(R.id.email_text)).setText(email);
            ((TextView) h.itemView.findViewById(R.id.relationship_text)).setText(relationship);

            // Status chip — connected
            com.google.android.material.chip.Chip chip = h.itemView.findViewById(R.id.status_chip);
            chip.setText("CONNECTED");
            chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(
                    Color.parseColor("#1B5E20")));

            // Pro / Covered badges
            TextView proBadge = h.itemView.findViewById(R.id.pro_badge);
            TextView covBadge = h.itemView.findViewById(R.id.covered_badge);
            if (isPro) {
                proBadge.setVisibility(View.VISIBLE);
                String memberTier = "self".equals(proSource) ? "pro" : "family_member";
                PlanBadge.apply(proBadge, memberTier);
            } else {
                proBadge.setVisibility(View.GONE);
            }
            covBadge.setVisibility(coveredByMe ? View.VISIBLE : View.GONE);

            // Hide edit/remove (those are for the canonical Family Members panel)
            h.itemView.findViewById(R.id.edit_button).setVisibility(View.GONE);
            h.itemView.findViewById(R.id.remove_button).setVisibility(View.GONE);
            h.itemView.findViewById(R.id.cancel_request_button).setVisibility(View.GONE);

            // Pro action row
            View actions = h.itemView.findViewById(R.id.action_buttons_container);
            MaterialButton addBtn = h.itemView.findViewById(R.id.add_to_pro_button);
            MaterialButton removeBtn = h.itemView.findViewById(R.id.remove_from_pro_button);
            actions.setVisibility(View.VISIBLE);

            int currentlyCovered = countCoveredLocal();
            boolean atLimit = currentlyCovered >= familyMemberMax;

            if (coveredByMe) {
                addBtn.setVisibility(View.GONE);
                removeBtn.setVisibility(View.VISIBLE);
                removeBtn.setOnClickListener(v -> confirmRemoveFromPro(userId, name));
            } else {
                addBtn.setVisibility(View.VISIBLE);
                removeBtn.setVisibility(View.GONE);
                addBtn.setEnabled(!atLimit);
                addBtn.setAlpha(atLimit ? 0.4f : 1f);
                addBtn.setText(atLimit ? "Plan full" : "Add to Pro");
                addBtn.setOnClickListener(v -> {
                    if (atLimit) {
                        Utilities.toast(context, "Plan full — remove someone first to add " + name);
                        return;
                    }
                    confirmAddToPro(userId, name);
                });
            }
        }

        class VH extends RecyclerView.ViewHolder {
            VH(View v) { super(v); }
        }
    }

    private int countCoveredLocal() {
        int n = 0;
        for (JSONObject r : familyMembersList) {
            if (r.optBoolean("isCoveredByMyPlan", false)) n++;
        }
        return n;
    }

    private void confirmAddToPro(String memberId, String name) {
        DialogUtils.showConfirmDialog(context,
                "Add to Pro Plan",
                "Add " + name + " to your plan? They'll get all premium features included.",
                "Add", "Cancel", false,
                () -> {
                    SimpleProgress p = SimpleProgress.show(activity, "Adding " + name + "…");
                    paymentService.addFamilyMemberDirect(memberId,
                            new PaymentService.PaymentCallback() {
                                @Override public void onSuccess(ProStatusResult r) {
                                    p.hide();
                                    Utilities.toast(context, name + " added");
                                    refreshFamilyAfterChange();
                                }
                                @Override public void onError(String e) {
                                    p.hide();
                                    Utilities.toast(context, "Failed: " + e);
                                }
                            });
                });
    }

    private void confirmRemoveFromPro(String memberId, String name) {
        DialogUtils.showConfirmDialog(context,
                "Remove from Pro",
                "Remove " + name + " from your family plan?",
                "Remove", "Cancel", true,
                () -> {
                    SimpleProgress p = SimpleProgress.show(activity, "Removing " + name + "…");
                    paymentService.removeFamilyMember(memberId,
                            new PaymentService.PaymentCallback() {
                                @Override public void onSuccess(ProStatusResult r) {
                                    p.hide();
                                    Utilities.toast(context, name + " removed");
                                    refreshFamilyAfterChange();
                                }
                                @Override public void onError(String e) {
                                    p.hide();
                                    Utilities.toast(context, "Failed: " + e);
                                }
                            });
                });
    }

    private void refreshFamilyAfterChange() {
        if (managementDialog == null || !managementDialog.isShowing()) return;
        TextView familyInfo = managementDialog.findViewById(R.id.family_info_text);
        loadFamilyMembersForManagement(familyInfo);
        // Also refresh global pro status manager so Health Hub stays in sync
        paymentService.getProStatus(new PaymentService.PaymentCallback() {
            @Override public void onSuccess(ProStatusResult result) {
                proStatusManager.setFamilyPlanInfo(
                        result.isFamilyPlanOwner(), result.isGrantedPro(),
                        result.getProGrantedBy(), result.getFamilyProMemberCount(),
                        result.getMaxFamilyMembers());
                if (callback != null) callback.onProStatusChanged(true);
            }
            @Override public void onError(String e) { /* non-fatal */ }
        });
    }


    // ── Family member helpers ─────────────────────────────────────────────────
    private void showAddFamilyMemberDialog() {
        SimpleProgress progress = SimpleProgress.show(activity, "Loading family members…");
        String token = TokenManager.getInstance(context).getToken();
        if (token == null) {
            progress.hide();
            Utilities.toast(context, "Authentication required");
            return;
        }

        String url = ApiConfig.BASE_URL + "/api/user/relationships";
        StringRequest request = new StringRequest(Request.Method.GET, url,
                response -> {
                    ApiConfig.logRestCall(url, true, "Relationships fetched");
                    progress.hide();
                    try {
                        JSONArray relationships = new JSONObject(response).getJSONArray("relationships");
                        List<String> ids   = new ArrayList<>();
                        List<String> names = new ArrayList<>();
                        for (int i = 0; i < relationships.length(); i++) {
                            JSONObject rel = relationships.getJSONObject(i);
                            if (!"accepted".equals(rel.optString("status"))) continue;
                            if (rel.optBoolean("isCoveredByMyPlan", false)) continue;
                            String uid = rel.optString("userId", "");
                            if (!uid.isEmpty()) {
                                ids.add(uid);
                                names.add(rel.optString("name",
                                        rel.optString("email", "Unknown"))
                                        + " (" + rel.optString("relationship", "") + ")");
                            }
                        }
                        if (ids.isEmpty()) {
                            Utilities.toastLong(context, "All relatives are already covered or none found.");
                            return;
                        }
                        showAddMemberSelectionDialog(ids, names);
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing relationships", e);
                        Utilities.toast(context, "Failed to load family members");
                    }
                },
                error -> {
                    ApiConfig.logRestCall(url, false, error.toString());
                    progress.hide();
                    Utilities.toast(context, "Failed to load family members");
                }) {
            @Override public Map<String, String> getHeaders() {
                Map<String, String> h = new HashMap<>();
                h.put("Authorization", "Bearer " + token);
                return h;
            }
        };
        Volley.newRequestQueue(context).add(request);
    }

    private void showAddMemberSelectionDialog(List<String> memberIds, List<String> memberNames) {
        new AlertDialog.Builder(context, R.style.DialogTheme)
                .setTitle("Select a Family Member")
                .setItems(memberNames.toArray(new String[0]), (d, which) -> {
                    String id   = memberIds.get(which);
                    String name = memberNames.get(which);
                    new AlertDialog.Builder(context, R.style.DialogTheme)
                            .setTitle("Add to Plan")
                            .setMessage("Add " + name + " to your plan?\nThey'll get access to all your premium features.")
                            .setPositiveButton("Add", (d2, w2) -> {
                                SimpleProgress progress = SimpleProgress.show(activity, "Adding " + name + "…");
                                paymentService.addFamilyMemberDirect(id,
                                        new PaymentService.PaymentCallback() {
                                            @Override
                                            public void onSuccess(ProStatusResult result) {
                                                progress.hide();
                                                if (result.getFamilyProMembers() != null) {
                                                    proStatusManager.setFamilyPlanInfo(
                                                            true, false, null,
                                                            result.getFamilyProMembers().size(),
                                                            proStatusManager.getMaxFamilyMembers());
                                                }
                                                Utilities.toast(context, name + " has been added to your plan!");
                                            }
                                            @Override
                                            public void onError(String reason) {
                                                progress.hide();
                                                Utilities.toast(context, "Failed: " + reason);
                                            }
                                        });
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ── FamilyMemberSelectionListener ─────────────────────────────────────────
    @Override
    public void onSelectFamilyMembers(String razorpayPaymentId, String razorpayOrderId,
                                       String razorpaySignature,
                                       PaymentManager.FamilyMemberSelectionCallback selectionCallback) {
        SimpleProgress progress = SimpleProgress.show(activity, "Loading family members…");
        String url = ApiConfig.BASE_URL + "/api/user/relationships";
        TokenManager tm = TokenManager.getInstance(context);

        StringRequest request = new StringRequest(Request.Method.GET, url,
                response -> {
                    ApiConfig.logRestCall(url, true, "Family members fetched for selection");
                    progress.hide();
                    try {
                        JSONArray arr = new JSONObject(response).optJSONArray("relationships");
                        List<String> ids   = new ArrayList<>();
                        List<String> names = new ArrayList<>();
                        if (arr != null) {
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject rel = arr.getJSONObject(i);
                                if ("accepted".equals(rel.optString("status", ""))) {
                                    String uid = rel.optString("userId", "");
                                    if (!uid.isEmpty()) {
                                        ids.add(uid);
                                        names.add(rel.optString("name",
                                                rel.optString("email", "Unknown"))
                                                + " (" + rel.optString("relationship", "") + ")");
                                    }
                                }
                            }
                        }
                        if (ids.isEmpty()) {
                            Utilities.toastLong(context, "No accepted family members found.");
                            selectionCallback.onSkipped();
                            return;
                        }
                        showFamilySelectionDialog(ids, names, selectionCallback);
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing family members", e);
                        selectionCallback.onSkipped();
                    }
                },
                error -> {
                    ApiConfig.logRestCall(url, false, error.toString());
                    progress.hide();
                    selectionCallback.onSkipped();
                }) {
            @Override public Map<String, String> getHeaders() {
                Map<String, String> h = new HashMap<>();
                String token = tm.getToken();
                if (token != null) h.put("Authorization", "Bearer " + token);
                return h;
            }
        };
        Volley.newRequestQueue(context).add(request);
    }

    private void showFamilySelectionDialog(List<String> ids, List<String> names,
                                            PaymentManager.FamilyMemberSelectionCallback cb) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.DialogTheme);
        builder.setTitle("Select Family Members");

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(48, 32, 48, 16);

        TextView sub = new TextView(context);
        sub.setText("Choose up to 5 members to include:");
        sub.setTextColor(Color.parseColor("#CCCCCC"));
        sub.setTextSize(14);
        sub.setPadding(0, 0, 0, 24);
        container.addView(sub);

        final int maxSel = 5;
        List<CheckBox> boxes = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            CheckBox cbx = new CheckBox(context);
            cbx.setText(names.get(i));
            cbx.setTextColor(Color.WHITE);
            cbx.setButtonTintList(android.content.res.ColorStateList.valueOf(
                    Color.parseColor("#008b8b")));
            cbx.setPadding(8, 12, 8, 12);
            cbx.setTag(ids.get(i));
            container.addView(cbx);
            boxes.add(cbx);
        }
        for (CheckBox b : boxes) {
            b.setOnCheckedChangeListener((bv, checked) -> {
                if (checked) {
                    int sel = 0;
                    for (CheckBox bx : boxes) if (bx.isChecked()) sel++;
                    if (sel > maxSel) {
                        bv.setChecked(false);
                        Utilities.toast(context, "Max " + maxSel + " members allowed");
                    }
                }
            });
        }

        ScrollView sv = new ScrollView(context);
        sv.addView(container);
        builder.setView(sv);

        builder.setPositiveButton("Confirm", (d, w) -> {
            List<String> selected = new ArrayList<>();
            for (CheckBox bx : boxes) {
                if (bx.isChecked()) selected.add((String) bx.getTag());
            }
            if (selected.size() > 5) {
                Utilities.toast(context, "Max 5 members");
                cb.onSkipped();
                return;
            }
            if (selected.isEmpty()) cb.onSkipped();
            else cb.onMembersSelected(selected);
        });
        builder.setNegativeButton("Skip for Now", (d, w) -> cb.onSkipped());
        builder.setCancelable(false);
        builder.show();
    }

    // ── Utils ─────────────────────────────────────────────────────────────────
    private String formatPrice(double price) {
        DecimalFormatSymbols syms = new DecimalFormatSymbols(Locale.US);
        syms.setGroupingSeparator(',');
        DecimalFormat df = new DecimalFormat("#,##0", syms);
        return "₹" + df.format((long) price);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }
}

package com.example.richhealth.Activities;
import Utils.Utilities;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.slider.Slider;

import java.util.ArrayList;
import java.util.List;

import Models.OnboardingData;
import Models.SelectableOption;
import Models.StepConfig;

/**
 * Generic reusable fragment for card-selection onboarding steps.
 *
 * Pass the step index via newInstance(int). The fragment fetches its
 * StepConfig from OnboardingActivity.getCardStepConfig(stepIndex) and
 * builds the entire UI programmatically — no XML layout needed.
 *
 * To add a new question: add a SectionConfig in OnboardingActivity.initCardStepConfigs().
 * No new Fragment class ever needed.
 */
public class CardStepFragment extends BaseOnboardingFragment {

    private static final String ARG_STEP_INDEX = "step_index";

    private StepConfig config;
    // Parallel to config.sections: card sections put their adapter here and null
    // into sliders; slider sections do the reverse. Keeps validate()/collectData()
    // index-aligned.
    private final List<SelectableCardAdapter> adapters = new ArrayList<>();
    private final List<Slider> sliders = new ArrayList<>();
    private final List<View> animTargets = new ArrayList<>();
    // Per-section views, for in-step conditional visibility (visibleWhen).
    private final List<List<View>> sectionViews = new ArrayList<>();
    // Answers, kept across a view rebuild. OnboardingActivity uses
    // FragmentTransaction.replace on persistent fragment instances, so stepping Back and
    // forward reruns onCreateView and every adapter and slider is constructed empty — the
    // user's taps on this step were simply gone.
    //
    // Snapshotted from the views themselves rather than read back out of OnboardingData,
    // for two reasons: handleBack() never calls collectData(), so a Back press never
    // reaches OnboardingData at all; and OnboardingData ships real values as defaults
    // (activityLevel 2 IS "Light Activity", dietType "Regular" IS "Everything"), so
    // restoring from it would light a card on a first visit and satisfy a required
    // section the user never answered.
    private final List<List<Object>> savedSelections = new ArrayList<>();
    private final List<Float> savedSliderValues = new ArrayList<>();
    // Section titles at snapshot time. Steps 16/18/21 are rebuilt per fetch
    // (buildSmokingDetailConfig / buildConditionDetailConfig / buildFamilyRelativesConfig)
    // and emit one identically-shaped section PER selected condition, with the same slider
    // spec and the same Yes/Some/No option values. Matching on section count alone would
    // therefore happily restore the Diabetes answers onto Asthma after the user went back
    // and changed which conditions they picked. The condition name is in the title, so
    // comparing titles is what actually detects that.
    private final List<String> savedSectionTitles = new ArrayList<>();

    public static CardStepFragment newInstance(int stepIndex) {
        CardStepFragment f = new CardStepFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_STEP_INDEX, stepIndex);
        f.setArguments(args);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        int stepIndex = getArguments() != null ? getArguments().getInt(ARG_STEP_INDEX) : 0;
        config = hostActivity.getCardStepConfig(stepIndex);
        // Only restore into a step whose shape still matches the snapshot; otherwise the
        // snapshot is discarded rather than applied to the wrong sections.
        final boolean canRestore = snapshotMatches();
        adapters.clear();
        sliders.clear();
        animTargets.clear();
        sectionViews.clear();

        Context ctx = requireContext();

        // Root: full-screen dark scroll container
        NestedScrollView scroll = new NestedScrollView(ctx);
        scroll.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        scroll.setBackgroundColor(Color.parseColor("#0F0F0F"));

        // Inner vertical container with padding
        LinearLayout content = new LinearLayout(ctx);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(ctx, 20), dp(ctx, 8), dp(ctx, 20), dp(ctx, 24));

        // ── Hero (icon in teal circle, or emoji fallback) ─────────────────────
        if (config.hasHeroIcon()) {
            MaterialCardView heroCircle = new MaterialCardView(ctx);
            LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(
                    dp(ctx, 80), dp(ctx, 80));
            heroParams.gravity = Gravity.CENTER_HORIZONTAL;
            heroParams.topMargin = dp(ctx, 8);
            heroCircle.setLayoutParams(heroParams);
            heroCircle.setCardBackgroundColor(Color.parseColor("#1A2E2E"));
            heroCircle.setRadius(dp(ctx, 40));
            heroCircle.setCardElevation(0);

            ImageView heroIcon = new ImageView(ctx);
            FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(
                    dp(ctx, 48), dp(ctx, 48));
            iconParams.gravity = Gravity.CENTER;
            heroIcon.setLayoutParams(iconParams);
            heroIcon.setImageResource(config.heroIconRes);
            heroIcon.setImageTintList(ColorStateList.valueOf(Color.parseColor("#008b8b")));
            heroCircle.addView(heroIcon);

            content.addView(heroCircle);
            animTargets.add(heroCircle);
        } else {
            TextView tvEmoji = new TextView(ctx);
            LinearLayout.LayoutParams emojiParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            emojiParams.gravity = Gravity.CENTER_HORIZONTAL;
            emojiParams.topMargin = dp(ctx, 8);
            tvEmoji.setLayoutParams(emojiParams);
            tvEmoji.setText(config.heroEmoji);
            tvEmoji.setTextSize(TypedValue.COMPLEX_UNIT_SP, 56);
            content.addView(tvEmoji);
            animTargets.add(tvEmoji);
        }

        // ── Title ─────────────────────────────────────────────────────────────
        TextView tvTitle = new TextView(ctx);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = dp(ctx, 12);
        tvTitle.setLayoutParams(titleParams);
        tvTitle.setText(config.title);
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        tvTitle.setTypeface(null, Typeface.BOLD);
        content.addView(tvTitle);
        animTargets.add(tvTitle);

        // ── Subtitle ──────────────────────────────────────────────────────────
        TextView tvSubtitle = new TextView(ctx);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = dp(ctx, 4);
        subtitleParams.bottomMargin = dp(ctx, 32);
        tvSubtitle.setLayoutParams(subtitleParams);
        tvSubtitle.setText(config.subtitle);
        tvSubtitle.setTextColor(Color.parseColor("#888888"));
        tvSubtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        content.addView(tvSubtitle);
        animTargets.add(tvSubtitle);

        // ── Sections ──────────────────────────────────────────────────────────
        // Each section renders like a proper question block:
        //   • Section title  — 19sp bold white (weighty, reads as a real question)
        //   • Why subtitle   — 13sp #888888 (explains why we're asking)
        //   • Card grid
        for (StepConfig.SectionConfig section : config.sections) {
            List<View> ownViews = new ArrayList<>();
            sectionViews.add(ownViews);

            // Section title — styled like a sub-question
            if (section.sectionTitle != null) {
                TextView tvHeader = new TextView(ctx);
                LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                headerParams.bottomMargin = dp(ctx, 2);
                tvHeader.setLayoutParams(headerParams);
                tvHeader.setText(section.sectionTitle);
                tvHeader.setTextColor(Color.WHITE);
                tvHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
                tvHeader.setTypeface(null, Typeface.BOLD);
                content.addView(tvHeader);
                animTargets.add(tvHeader);
                ownViews.add(tvHeader);
            }

            // Why-we-ask subtitle
            if (section.whySubtitle != null && !section.whySubtitle.isEmpty()) {
                TextView tvWhy = new TextView(ctx);
                LinearLayout.LayoutParams whyParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                whyParams.bottomMargin = dp(ctx, 12);
                tvWhy.setLayoutParams(whyParams);
                tvWhy.setText(section.whySubtitle);
                tvWhy.setTextColor(Color.parseColor("#888888"));
                tvWhy.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                tvWhy.setLineSpacing(dp(ctx, 2), 1f);
                content.addView(tvWhy);
                animTargets.add(tvWhy);
                ownViews.add(tvWhy);
            } else if (section.sectionTitle != null) {
                // pad below the header so the grid doesn't butt up against it
                View spacer = new View(ctx);
                spacer.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, 8)));
                content.addView(spacer);
                ownViews.add(spacer);
            }

            // ── Slider section (2026-08 rework): numeric answer, live readout ──
            if (section.slider != null) {
                final StepConfig.SliderSpec spec = section.slider;

                TextView tvValue = new TextView(ctx);
                LinearLayout.LayoutParams valParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                valParams.bottomMargin = dp(ctx, 4);
                tvValue.setLayoutParams(valParams);
                tvValue.setTextColor(Color.parseColor("#008b8b"));
                tvValue.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
                tvValue.setTypeface(null, Typeface.BOLD);

                Slider slider = new Slider(ctx);
                LinearLayout.LayoutParams slParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                slParams.bottomMargin = dp(ctx, 32);
                slider.setLayoutParams(slParams);
                slider.setValueFrom(spec.valueFrom);
                slider.setValueTo(spec.valueTo);
                slider.setStepSize(spec.stepSize);
                slider.setValue(spec.defaultValue);

                if (canRestore) {
                    Float prev = savedSliderValues.get(adapters.size());
                    // Range AND step alignment: Slider.setValue throws for either, and a
                    // rebuilt config can legitimately have moved the bounds.
                    if (prev != null && prev >= spec.valueFrom && prev <= spec.valueTo
                            && isOnStep(prev, spec.valueFrom, spec.stepSize)) {
                        slider.setValue(prev);
                    }
                }

                Runnable updateLabel = () -> {
                    float v = slider.getValue();
                    String num = (spec.stepSize < 1f && v != Math.round(v))
                            ? String.format(java.util.Locale.US, "%.1f", v)
                            : String.valueOf(Math.round(v));
                    tvValue.setText(num + (spec.unit.isEmpty() ? "" : " " + spec.unit));
                };
                updateLabel.run();
                slider.addOnChangeListener((s, value, fromUser) -> updateLabel.run());

                content.addView(tvValue);
                content.addView(slider);
                animTargets.add(tvValue);
                animTargets.add(slider);
                ownViews.add(tvValue);
                ownViews.add(slider);

                adapters.add(null);
                sliders.add(slider);
                continue;
            }

            // Card grid RecyclerView
            RecyclerView rv = new RecyclerView(ctx);
            LinearLayout.LayoutParams rvParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rvParams.bottomMargin = dp(ctx, 32);
            rv.setLayoutParams(rvParams);
            rv.setNestedScrollingEnabled(false);

            final List<SelectableOption> options = section.options;
            final int spanCount = section.spanCount;

            GridLayoutManager glm = new GridLayoutManager(ctx, spanCount);
            if (hasFullWidthItem(options)) {
                glm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
                    @Override
                    public int getSpanSize(int position) {
                        return options.get(position).fullWidth ? spanCount : 1;
                    }
                });
            }
            rv.setLayoutManager(glm);

            SelectableCardAdapter adapter = new SelectableCardAdapter(options, section.multiSelect);
            if (section.clearOthersPosition >= 0) {
                adapter.setClearOthersPosition(section.clearOthersPosition);
            }
            adapters.add(adapter);
            sliders.add(null);
            if (canRestore) {
                List<Object> prev = savedSelections.get(adapters.size() - 1);
                if (prev != null && !prev.isEmpty()) adapter.setSelectedValues(prev);
            }
            rv.setAdapter(adapter);

            content.addView(rv);
            animTargets.add(rv);
            ownViews.add(rv);
        }

        // ── In-step conditional sections (2026-08): show/hide dependents when the
        // answer they depend on changes, and re-apply once up front. ──
        boolean hasDependents = false;
        for (StepConfig.SectionConfig s : config.sections) {
            if (s.dependsOnSection >= 0) { hasDependents = true; break; }
        }
        if (hasDependents) {
            for (int i = 0; i < config.sections.size(); i++) {
                SelectableCardAdapter a = adapters.get(i);
                if (a != null) a.setOnSelectionChangedListener(this::applyDependencies);
            }
            applyDependencies();
        }

        scroll.addView(content);

        // ── Subtle staggered entrance animation ──────────────────────────────
        // Each major view fades in from slightly below its final position,
        // ~60ms apart, so the screen feels assembled rather than slammed in.
        final float translateStartPx = dp(ctx, 12);
        for (int i = 0; i < animTargets.size(); i++) {
            View v = animTargets.get(i);
            v.setAlpha(0f);
            v.setTranslationY(translateStartPx);
        }
        scroll.post(() -> {
            for (int i = 0; i < animTargets.size(); i++) {
                View v = animTargets.get(i);
                v.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setStartDelay(i * 60L)
                        .setDuration(340)
                        .setInterpolator(new DecelerateInterpolator(1.6f))
                        .start();
            }
        });

        return scroll;
    }

    @Override
    public void onDestroyView() {
        // Take the snapshot before the views go away; onCreateView reads it back.
        savedSelections.clear();
        savedSliderValues.clear();
        savedSectionTitles.clear();
        for (int i = 0; i < adapters.size(); i++) {
            SelectableCardAdapter a = adapters.get(i);
            savedSelections.add(a != null ? a.getSelectedValues() : null);
            savedSliderValues.add(sliders.get(i) != null ? sliders.get(i).getValue() : null);
            savedSectionTitles.add(config.sections.get(i).sectionTitle);
        }
        super.onDestroyView();
    }

    /** Does the snapshot describe the step we are about to build, section for section? */
    private boolean snapshotMatches() {
        int n = config.sections.size();
        if (savedSelections.size() != n || savedSliderValues.size() != n
                || savedSectionTitles.size() != n) {
            return false;
        }
        for (int i = 0; i < n; i++) {
            String was = savedSectionTitles.get(i);
            String now = config.sections.get(i).sectionTitle;
            if (was == null ? now != null : !was.equals(now)) return false;
        }
        return true;
    }

    /** True when v sits exactly on one of the slider's steps. */
    private static boolean isOnStep(float v, float from, float step) {
        if (step <= 0f) return true;
        float steps = (v - from) / step;
        return Math.abs(steps - Math.round(steps)) < 1e-4f;
    }

    /** Is section i currently shown (its gating answer, if any, matches)? */
    private boolean isSectionVisible(int i) {
        StepConfig.SectionConfig s = config.sections.get(i);
        if (s.dependsOnSection < 0 || s.visibleForValues == null) return true;
        SelectableCardAdapter dep = s.dependsOnSection < adapters.size()
                ? adapters.get(s.dependsOnSection) : null;
        if (dep == null || !dep.hasSelection()) return false;
        Object v = dep.getSelectedValue();
        return v != null && s.visibleForValues.contains(String.valueOf(v));
    }

    /** Re-evaluate every dependent section's visibility (called on any selection change). */
    private void applyDependencies() {
        for (int i = 0; i < config.sections.size(); i++) {
            if (config.sections.get(i).dependsOnSection < 0) continue;
            int vis = isSectionVisible(i) ? View.VISIBLE : View.GONE;
            for (View v : sectionViews.get(i)) v.setVisibility(vis);
        }
    }

    @Override
    public boolean validate() {
        for (int i = 0; i < config.sections.size(); i++) {
            StepConfig.SectionConfig section = config.sections.get(i);
            if (!isSectionVisible(i)) continue;   // hidden dependents don't gate Continue
            if (section.slider != null) continue; // sliders always have a value
            if (section.required && !adapters.get(i).hasSelection()) {
                String label = section.sectionTitle != null ? section.sectionTitle : config.title;
                Utilities.toast(getContext(), "Please make a selection for: " + label);
                return false;
            }
        }
        return true;
    }

    @Override
    public void collectData(OnboardingData data) {
        for (int i = 0; i < config.sections.size(); i++) {
            StepConfig.SectionConfig section = config.sections.get(i);
            if (!isSectionVisible(i)) continue; // hidden answers must not be recorded
            if (section.slider != null) {
                Slider slider = sliders.get(i);
                if (slider != null) section.dataWriter.write(data, slider.getValue());
                continue;
            }
            SelectableCardAdapter adapter = adapters.get(i);
            if (!adapter.hasSelection()) continue;

            Object value = section.multiSelect
                    ? adapter.getSelectedValues()
                    : adapter.getSelectedValue();
            section.dataWriter.write(data, value);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean hasFullWidthItem(List<SelectableOption> options) {
        for (SelectableOption o : options) {
            if (o.fullWidth) return true;
        }
        return false;
    }

    private int dp(Context ctx, int dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, ctx.getResources().getDisplayMetrics()));
    }
}

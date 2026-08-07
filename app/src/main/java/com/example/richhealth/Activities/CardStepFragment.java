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
    private final List<SelectableCardAdapter> adapters = new ArrayList<>();
    private final List<View> animTargets = new ArrayList<>();

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
        adapters.clear();
        animTargets.clear();

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
            } else if (section.sectionTitle != null) {
                // pad below the header so the grid doesn't butt up against it
                View spacer = new View(ctx);
                spacer.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, 8)));
                content.addView(spacer);
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
            rv.setAdapter(adapter);

            content.addView(rv);
            animTargets.add(rv);
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
    public boolean validate() {
        for (int i = 0; i < config.sections.size(); i++) {
            StepConfig.SectionConfig section = config.sections.get(i);
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

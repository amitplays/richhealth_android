package com.example.richhealth.Activities;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.richhealth.R;
import com.google.android.material.slider.Slider;

import Models.OnboardingData;

public class OnboardingBodyFragment extends BaseOnboardingFragment {

    private Slider sliderHeight, sliderWeight, sliderWaist;
    private TextView tvHeightDisplay, tvWeightDisplay, tvWaistDisplay;
    private CheckBox cbWaistUnknown;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_onboarding_body, container, false);

        sliderHeight = root.findViewById(R.id.slider_height);
        sliderWeight = root.findViewById(R.id.slider_weight);
        sliderWaist = root.findViewById(R.id.slider_waist);
        tvHeightDisplay = root.findViewById(R.id.tv_height_display);
        tvWeightDisplay = root.findViewById(R.id.tv_weight_display);
        tvWaistDisplay = root.findViewById(R.id.tv_waist_display);
        cbWaistUnknown = root.findViewById(R.id.cb_waist_unknown);

        // Restore previous values
        if (hostActivity != null) {
            OnboardingData d = hostActivity.getOnboardingData();
            sliderHeight.setValue((float) d.heightCm);
            sliderWeight.setValue((float) d.weightKg);
            if (d.waistCircumferenceCm > 0) {
                sliderWaist.setValue((float) d.waistCircumferenceCm);
                cbWaistUnknown.setChecked(false);
            }
        }

        updateHeightDisplay((int) sliderHeight.getValue());
        updateWeightDisplay((int) sliderWeight.getValue());
        updateWaistDisplay((int) sliderWaist.getValue());

        sliderHeight.addOnChangeListener((slider, value, fromUser) -> updateHeightDisplay((int) value));
        sliderWeight.addOnChangeListener((slider, value, fromUser) -> updateWeightDisplay((int) value));
        sliderWaist.addOnChangeListener((slider, value, fromUser) -> {
            updateWaistDisplay((int) value);
            // Interacting with the slider means the user does know their waist.
            if (fromUser && cbWaistUnknown.isChecked()) cbWaistUnknown.setChecked(false);
        });

        return root;
    }

    private void updateHeightDisplay(int cm) {
        int totalInches = (int) Math.round(cm / 2.54);
        int feet = totalInches / 12;
        int inches = totalInches % 12;
        tvHeightDisplay.setText(cm + " cm  ·  " + feet + "'" + inches + "\"");
    }

    private void updateWeightDisplay(int kg) {
        int lbs = (int) Math.round(kg * 2.20462);
        tvWeightDisplay.setText(kg + " kg  ·  " + lbs + " lbs");
    }

    private void updateWaistDisplay(int cm) {
        int inches = (int) Math.round(cm / 2.54);
        tvWaistDisplay.setText(cm + " cm  ·  " + inches + "\"");
    }

    @Override
    public boolean validate() {
        // Sliders always have a value, nothing to validate
        return true;
    }

    @Override
    public void collectData(OnboardingData data) {
        data.heightCm = sliderHeight.getValue();
        data.weightKg = sliderWeight.getValue();
        // 0 = user skipped / doesn't know their waist.
        data.waistCircumferenceCm = cbWaistUnknown.isChecked() ? 0 : sliderWaist.getValue();
    }
}

package com.example.richhealth.Activities;
import Utils.Utilities;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.richhealth.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import Models.OnboardingData;
import Models.SelectableOption;

public class OnboardingHabitsFragment extends BaseOnboardingFragment {

    private SelectableCardAdapter smokingAdapter, alcoholAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_onboarding_habits, container, false);

        // Smoking options — value is int[]{level, smokerFlag} encoded as a simple tag
        List<SelectableOption> smokingOptions = new ArrayList<>(Arrays.asList(
                new SelectableOption("Never", "🚭", "never"),
                new SelectableOption("Ex-Smoker", "✅", "ex"),
                new SelectableOption("Occasionally", "💨", "occasional"),
                new SelectableOption("Regularly", "🚬", "regular")
        ));

        RecyclerView rvSmoking = root.findViewById(R.id.rv_smoking);
        rvSmoking.setLayoutManager(new GridLayoutManager(getContext(), 2));
        smokingAdapter = new SelectableCardAdapter(smokingOptions, false);
        rvSmoking.setAdapter(smokingAdapter);

        List<SelectableOption> alcoholOptions = new ArrayList<>(Arrays.asList(
                new SelectableOption("Never", "🙅", "None"),
                new SelectableOption("Socially", "🥂", "Socially"),
                new SelectableOption("Regularly", "🍺", "Regularly"),
                new SelectableOption("Daily", "🍾", "Frequently")
        ));

        RecyclerView rvAlcohol = root.findViewById(R.id.rv_alcohol);
        rvAlcohol.setLayoutManager(new GridLayoutManager(getContext(), 2));
        alcoholAdapter = new SelectableCardAdapter(alcoholOptions, false);
        rvAlcohol.setAdapter(alcoholAdapter);

        return root;
    }

    @Override
    public boolean validate() {
        if (!smokingAdapter.hasSelection()) {
            Utilities.toast(getContext(), "Please answer the smoking question");
            return false;
        }
        if (!alcoholAdapter.hasSelection()) {
            Utilities.toast(getContext(), "Please answer the alcohol question");
            return false;
        }
        return true;
    }

    @Override
    public void collectData(OnboardingData data) {
        String smokingTag = (String) smokingAdapter.getSelectedValue();
        switch (smokingTag) {
            case "never":
                data.smoker = false; data.smokingLevel = 0; data.smokingFrequency = "Non-smoker"; break;
            case "ex":
                data.smoker = false; data.smokingLevel = 0; data.smokingFrequency = "Non-smoker"; break;
            case "occasional":
                data.smoker = true; data.smokingLevel = 1; data.smokingFrequency = "Occasional"; break;
            case "regular":
                data.smoker = true; data.smokingLevel = 3; data.smokingFrequency = "Heavy"; break;
        }

        String alcohol = (String) alcoholAdapter.getSelectedValue();
        data.alcoholConsumption = alcohol;
        switch (alcohol) {
            case "None":       data.alcoholLevel = 0; break;
            case "Socially":   data.alcoholLevel = 2; break;
            case "Regularly":  data.alcoholLevel = 3; break;
            case "Frequently": data.alcoholLevel = 4; break;
        }
    }
}

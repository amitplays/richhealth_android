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

public class OnboardingSleepStressFragment extends BaseOnboardingFragment {

    private SelectableCardAdapter sleepAdapter, stressAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_onboarding_sleep_stress, container, false);

        List<SelectableOption> sleepOptions = new ArrayList<>(Arrays.asList(
                new SelectableOption("Under 5 hrs", "😵", 4),
                new SelectableOption("5–6 hours", "😐", 6),
                new SelectableOption("7–8 hours", "😴", 8),
                new SelectableOption("9+ hours", "🛌", 9)
        ));

        RecyclerView rvSleep = root.findViewById(R.id.rv_sleep);
        rvSleep.setLayoutManager(new GridLayoutManager(getContext(), 2));
        sleepAdapter = new SelectableCardAdapter(sleepOptions, false);
        rvSleep.setAdapter(sleepAdapter);

        List<SelectableOption> stressOptions = new ArrayList<>(Arrays.asList(
                new SelectableOption("Rarely", "😌", 1),
                new SelectableOption("Sometimes", "🙂", 2),
                new SelectableOption("Often", "😤", 3),
                new SelectableOption("Almost Always", "😰", 4)
        ));

        RecyclerView rvStress = root.findViewById(R.id.rv_stress);
        rvStress.setLayoutManager(new GridLayoutManager(getContext(), 2));
        stressAdapter = new SelectableCardAdapter(stressOptions, false);
        rvStress.setAdapter(stressAdapter);

        return root;
    }

    @Override
    public boolean validate() {
        if (!sleepAdapter.hasSelection()) {
            Utilities.toast(getContext(), "Please select your typical sleep duration");
            return false;
        }
        if (!stressAdapter.hasSelection()) {
            Utilities.toast(getContext(), "Please select your stress level");
            return false;
        }
        return true;
    }

    @Override
    public void collectData(OnboardingData data) {
        data.sleepHours = (Integer) sleepAdapter.getSelectedValue();
        data.stressLevel = (Integer) stressAdapter.getSelectedValue();
    }
}

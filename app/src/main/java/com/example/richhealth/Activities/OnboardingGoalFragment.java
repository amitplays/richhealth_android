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

public class OnboardingGoalFragment extends BaseOnboardingFragment {

    private SelectableCardAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_onboarding_goal, container, false);

        List<SelectableOption> options = new ArrayList<>(Arrays.asList(
                new SelectableOption("Lose Weight", "⚖️", "Weight Loss"),
                new SelectableOption("Build Muscle", "💪", "Muscle Gain"),
                new SelectableOption("Stay Fit", "🏃", "Improve Fitness"),
                new SelectableOption("Manage Condition", "❤️", "Manage a Health Condition"),
                new SelectableOption("Boost Energy", "⚡", "Boost Energy"),
                new SelectableOption("Sleep Better", "😴", "Improve Sleep"),
                new SelectableOption("Eat Healthier", "🥗", "Eat Healthier"),
                new SelectableOption("Mental Health", "🧠", "Improve Mental Health")
        ));

        RecyclerView rv = root.findViewById(R.id.rv_goals);
        rv.setLayoutManager(new GridLayoutManager(getContext(), 2));
        adapter = new SelectableCardAdapter(options, false);
        rv.setAdapter(adapter);

        return root;
    }

    @Override
    public boolean validate() {
        if (!adapter.hasSelection()) {
            Utilities.toast(getContext(), "Please select your main health goal");
            return false;
        }
        return true;
    }

    @Override
    public void collectData(OnboardingData data) {
        data.primaryGoal = (String) adapter.getSelectedValue();
    }
}

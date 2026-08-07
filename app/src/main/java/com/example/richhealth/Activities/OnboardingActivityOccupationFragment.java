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

public class OnboardingActivityOccupationFragment extends BaseOnboardingFragment {

    private SelectableCardAdapter activityAdapter, occupationAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_onboarding_activity, container, false);

        // Activity level options
        List<SelectableOption> activityOptions = new ArrayList<>(Arrays.asList(
                new SelectableOption("Mostly Sitting", "🪑", 1),
                new SelectableOption("Light Activity", "🚶", 2),
                new SelectableOption("Moderately Active", "🚴", 3),
                new SelectableOption("Very Active", "🏋️", 4),
                new SelectableOption("Athlete", "🏅", 5)
        ));

        RecyclerView rvActivity = root.findViewById(R.id.rv_activity);
        rvActivity.setLayoutManager(new GridLayoutManager(getContext(), 3));
        activityAdapter = new SelectableCardAdapter(activityOptions, false);
        rvActivity.setAdapter(activityAdapter);

        // Occupation options
        List<SelectableOption> occupationOptions = new ArrayList<>(Arrays.asList(
                new SelectableOption("Desk / Office", "💻", "desk"),
                new SelectableOption("Physical Labour", "🔨", "physical"),
                new SelectableOption("Healthcare", "🩺", "healthcare"),
                new SelectableOption("Student", "📚", "student"),
                new SelectableOption("Work from Home", "🏠", "remote"),
                new SelectableOption("Retired / Home", "🌿", "retired")
        ));

        RecyclerView rvOccupation = root.findViewById(R.id.rv_occupation);
        rvOccupation.setLayoutManager(new GridLayoutManager(getContext(), 2));
        occupationAdapter = new SelectableCardAdapter(occupationOptions, false);
        rvOccupation.setAdapter(occupationAdapter);

        return root;
    }

    @Override
    public boolean validate() {
        if (!activityAdapter.hasSelection()) {
            Utilities.toast(getContext(), "Please select your activity level");
            return false;
        }
        if (!occupationAdapter.hasSelection()) {
            Utilities.toast(getContext(), "Please select your type of work");
            return false;
        }
        return true;
    }

    @Override
    public void collectData(OnboardingData data) {
        data.activityLevel = (Integer) activityAdapter.getSelectedValue();
        data.occupationType = (String) occupationAdapter.getSelectedValue();
    }
}

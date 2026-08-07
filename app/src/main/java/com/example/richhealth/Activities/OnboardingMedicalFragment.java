package com.example.richhealth.Activities;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

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

public class OnboardingMedicalFragment extends BaseOnboardingFragment {

    private SelectableCardAdapter bloodTypeAdapter, conditionsAdapter;

    private static final String NONE_VALUE = "__none__";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_onboarding_medical, container, false);

        // Blood type
        List<SelectableOption> bloodOptions = new ArrayList<>(Arrays.asList(
                new SelectableOption("A+", "🅰", "A+"),
                new SelectableOption("A-", "🅰", "A-"),
                new SelectableOption("B+", "🅱", "B+"),
                new SelectableOption("B-", "🅱", "B-"),
                new SelectableOption("AB+", "🆎", "AB+"),
                new SelectableOption("AB-", "🆎", "AB-"),
                new SelectableOption("O+", "🅾", "O+"),
                new SelectableOption("O-", "🅾", "O-"),
                new SelectableOption("Don't Know", "❓", "")
        ));

        RecyclerView rvBlood = root.findViewById(R.id.rv_blood_type);
        rvBlood.setLayoutManager(new GridLayoutManager(getContext(), 3));
        bloodTypeAdapter = new SelectableCardAdapter(bloodOptions, false);
        rvBlood.setAdapter(bloodTypeAdapter);

        // Medical conditions (multi-select)
        List<SelectableOption> conditionOptions = new ArrayList<>(Arrays.asList(
                new SelectableOption("Diabetes", "🩸", "Diabetes"),
                new SelectableOption("Hypertension", "💊", "Hypertension"),
                new SelectableOption("Heart Disease", "❤️", "Heart Disease"),
                new SelectableOption("Asthma", "💨", "Asthma"),
                new SelectableOption("Thyroid", "🦋", "Thyroid Issues"),
                new SelectableOption("Arthritis", "🦴", "Arthritis"),
                new SelectableOption("High Cholesterol", "🫀", "High Cholesterol"),
                new SelectableOption("PCOS/Hormonal", "⚖️", "PCOS/Hormonal Issues"),
                new SelectableOption("Anxiety/Depression", "🧠", "Anxiety/Depression"),
                new SelectableOption("Digestive Issues", "🫁", "Digestive Issues"),
                new SelectableOption("Kidney Issues", "💧", "Kidney Issues"),
                new SelectableOption("None of the above", "✅", NONE_VALUE, true)
        ));

        RecyclerView rvConditions = root.findViewById(R.id.rv_conditions);
        GridLayoutManager conditionsLm = new GridLayoutManager(getContext(), 2);
        conditionsLm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                // Last item ("None of the above") spans full width
                return conditionOptions.get(position).fullWidth ? 2 : 1;
            }
        });
        rvConditions.setLayoutManager(conditionsLm);
        conditionsAdapter = new SelectableCardAdapter(conditionOptions, true);
        // "None of the above" is the last item
        conditionsAdapter.setClearOthersPosition(conditionOptions.size() - 1);
        rvConditions.setAdapter(conditionsAdapter);

        return root;
    }

    @Override
    public boolean validate() {
        // This step is optional — always passes
        return true;
    }

    @Override
    public void collectData(OnboardingData data) {
        // Blood type
        if (bloodTypeAdapter.hasSelection()) {
            data.bloodType = (String) bloodTypeAdapter.getSelectedValue();
        }

        // Medical conditions
        data.medicalConditions = new ArrayList<>();
        List<Object> selected = conditionsAdapter.getSelectedValues();
        for (Object val : selected) {
            String s = (String) val;
            if (!s.equals(NONE_VALUE)) {
                data.medicalConditions.add(s);
            }
        }
    }
}

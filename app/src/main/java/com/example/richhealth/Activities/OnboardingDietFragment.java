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

public class OnboardingDietFragment extends BaseOnboardingFragment {

    private SelectableCardAdapter dietAdapter, mealsAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_onboarding_diet, container, false);

        List<SelectableOption> dietOptions = new ArrayList<>(Arrays.asList(
                new SelectableOption("Everything", "🍔", "Regular"),
                new SelectableOption("Vegetarian", "🥦", "Vegetarian"),
                new SelectableOption("Vegan", "🌱", "Vegan"),
                new SelectableOption("Keto", "🥑", "Keto"),
                new SelectableOption("Mediterranean", "🫒", "Mediterranean"),
                new SelectableOption("Gluten-Free", "🌾", "Gluten-Free"),
                new SelectableOption("Other", "🍽️", "Other")
        ));

        RecyclerView rvDiet = root.findViewById(R.id.rv_diet);
        rvDiet.setLayoutManager(new GridLayoutManager(getContext(), 2));
        dietAdapter = new SelectableCardAdapter(dietOptions, false);
        rvDiet.setAdapter(dietAdapter);

        List<SelectableOption> mealsOptions = new ArrayList<>(Arrays.asList(
                new SelectableOption("1–2 meals", "1️⃣", 2),
                new SelectableOption("3 meals", "3️⃣", 3),
                new SelectableOption("4–5 meals", "5️⃣", 4),
                new SelectableOption("6+ meals", "🔄", 6)
        ));

        RecyclerView rvMeals = root.findViewById(R.id.rv_meals);
        rvMeals.setLayoutManager(new GridLayoutManager(getContext(), 2));
        mealsAdapter = new SelectableCardAdapter(mealsOptions, false);
        rvMeals.setAdapter(mealsAdapter);

        return root;
    }

    @Override
    public boolean validate() {
        if (!dietAdapter.hasSelection()) {
            Utilities.toast(getContext(), "Please select your diet type");
            return false;
        }
        return true;
    }

    @Override
    public void collectData(OnboardingData data) {
        data.dietType = (String) dietAdapter.getSelectedValue();
        if (mealsAdapter.hasSelection()) {
            data.mealsPerDay = (Integer) mealsAdapter.getSelectedValue();
        }
    }
}

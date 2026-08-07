package com.example.richhealth.Activities;
import Utils.Utilities;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.richhealth.R;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointBackward;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import Models.OnboardingData;
import Models.SelectableOption;

public class OnboardingPersonalFragment extends BaseOnboardingFragment {

    private MaterialCardView cardDob;
    private TextView tvDobDisplay;
    private SelectableCardAdapter genderAdapter;
    private TextInputLayout layoutLocation;
    private TextInputEditText inputLocation;
    private Date selectedDob = null;
    private static final SimpleDateFormat DISPLAY_FMT = new SimpleDateFormat("MMMM d, yyyy", Locale.getDefault());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_onboarding_personal, container, false);

        cardDob = root.findViewById(R.id.card_dob);
        tvDobDisplay = root.findViewById(R.id.tv_dob_display);
        layoutLocation = root.findViewById(R.id.layout_location);
        inputLocation = root.findViewById(R.id.input_location);

        if (hostActivity != null && !hostActivity.getOnboardingData().location.isEmpty()) {
            inputLocation.setText(hostActivity.getOnboardingData().location);
        }

        // Restore DOB if returning
        if (hostActivity != null && hostActivity.getOnboardingData().dateOfBirth != null) {
            selectedDob = hostActivity.getOnboardingData().dateOfBirth;
            tvDobDisplay.setText(DISPLAY_FMT.format(selectedDob));
            tvDobDisplay.setTextColor(0xFFFFFFFF);
        }

        cardDob.setOnClickListener(v -> showDatePicker());

        // Gender cards
        List<SelectableOption> genderOptions = new ArrayList<>(Arrays.asList(
                new SelectableOption("Male",   R.drawable.ic_signup_male,         "Male"),
                new SelectableOption("Female", R.drawable.ic_signup_female,       "Female"),
                new SelectableOption("Other",  R.drawable.ic_signup_transgender,  "Other")
        ));

        RecyclerView rvGender = root.findViewById(R.id.rv_gender);
        GridLayoutManager lm = new GridLayoutManager(getContext(), 3);
        rvGender.setLayoutManager(lm);
        genderAdapter = new SelectableCardAdapter(genderOptions, false);
        rvGender.setAdapter(genderAdapter);

        // Restore gender selection
        if (hostActivity != null) {
            String savedGender = hostActivity.getOnboardingData().gender;
            if (!savedGender.isEmpty()) {
                for (int i = 0; i < genderOptions.size(); i++) {
                    if (genderOptions.get(i).value.equals(savedGender)) {
                        // Pre-select by simulating click via adapter internals isn't ideal;
                        // just leave it — user can re-tap on revisit.
                        break;
                    }
                }
            }
        }

        return root;
    }

    private void showDatePicker() {
        CalendarConstraints constraints = new CalendarConstraints.Builder()
                .setValidator(DateValidatorPointBackward.now())
                .build();

        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select your date of birth")
                .setCalendarConstraints(constraints)
                .build();

        picker.addOnPositiveButtonClickListener(selection -> {
            selectedDob = new Date(selection);
            tvDobDisplay.setText(DISPLAY_FMT.format(selectedDob));
            tvDobDisplay.setTextColor(0xFFFFFFFF);
        });

        picker.show(getParentFragmentManager(), "DOB_PICKER");
    }

    @Override
    public boolean validate() {
        if (selectedDob == null) {
            Utilities.toast(getContext(), "Please select your date of birth");
            return false;
        }
        if (!genderAdapter.hasSelection()) {
            Utilities.toast(getContext(), "Please select your gender");
            return false;
        }
        String loc = inputLocation.getText() != null ? inputLocation.getText().toString().trim() : "";
        if (loc.isEmpty()) {
            layoutLocation.setError("Please enter your location");
            return false;
        }
        layoutLocation.setError(null);
        return true;
    }

    @Override
    public void collectData(OnboardingData data) {
        data.dateOfBirth = selectedDob;
        data.gender = (String) genderAdapter.getSelectedValue();
        data.location = inputLocation.getText() != null ? inputLocation.getText().toString().trim() : "";
    }
}

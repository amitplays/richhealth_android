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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import Models.OnboardingData;
import Models.SelectableOption;

/**
 * Collects dependent basic info: name, password, DOB, and gender.
 * Used in AddDependentActivity step flow.
 */
public class DependentInfoFragment extends BaseOnboardingFragment {

    private TextInputEditText etName;
    private TextInputEditText etPassword;
    private MaterialCardView cardDob;
    private TextView tvDobDisplay;
    private SelectableCardAdapter genderAdapter;
    private Date selectedDob = null;

    private static final SimpleDateFormat DISPLAY_FMT =
            new SimpleDateFormat("MMMM d, yyyy", Locale.getDefault());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_dependent_info, container, false);

        etName = root.findViewById(R.id.et_name);
        etPassword = root.findViewById(R.id.et_password);
        cardDob = root.findViewById(R.id.card_dob);
        tvDobDisplay = root.findViewById(R.id.tv_dob_display);

        cardDob.setOnClickListener(v -> showDatePicker());

        // Gender cards
        List<SelectableOption> genderOptions = new ArrayList<>(Arrays.asList(
                new SelectableOption("Male", "\uD83D\uDC68", "Male"),
                new SelectableOption("Female", "\uD83D\uDC69", "Female"),
                new SelectableOption("Other", "\uD83E\uDDD1", "Other")
        ));

        RecyclerView rvGender = root.findViewById(R.id.rv_gender);
        rvGender.setLayoutManager(new GridLayoutManager(getContext(), 3));
        genderAdapter = new SelectableCardAdapter(genderOptions, false);
        rvGender.setAdapter(genderAdapter);

        return root;
    }

    private void showDatePicker() {
        CalendarConstraints constraints = new CalendarConstraints.Builder()
                .setValidator(DateValidatorPointBackward.now())
                .build();

        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select date of birth")
                .setCalendarConstraints(constraints)
                .build();

        picker.addOnPositiveButtonClickListener(selection -> {
            selectedDob = new Date(selection);
            tvDobDisplay.setText(DISPLAY_FMT.format(selectedDob));
            tvDobDisplay.setTextColor(0xFFFFFFFF);
        });

        picker.show(getParentFragmentManager(), "DEP_DOB_PICKER");
    }

    @Override
    public boolean validate() {
        String name = etName.getText() != null ? etName.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";

        if (name.isEmpty()) {
            Utilities.toast(getContext(), "Please enter a name");
            return false;
        }
        if (password.length() < 6) {
            Utilities.toast(getContext(), "Password must be at least 6 characters");
            return false;
        }
        if (!genderAdapter.hasSelection()) {
            Utilities.toast(getContext(), "Please select a gender");
            return false;
        }
        return true;
    }

    @Override
    public void collectData(OnboardingData data) {
        data.name = etName.getText() != null ? etName.getText().toString().trim() : "";
        data.password = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";
        data.gender = (String) genderAdapter.getSelectedValue();
        data.dateOfBirth = selectedDob;
    }
}

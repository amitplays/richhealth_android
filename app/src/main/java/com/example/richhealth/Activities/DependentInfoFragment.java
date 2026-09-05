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

    /**
     * MaterialDatePicker hands back UTC midnight of the chosen day, but DISPLAY_FMT here
     * and every downstream reader of UserProfile.dateOfBirth work in the device timezone.
     * West of UTC that showed and submitted the day BEFORE the one the user tapped.
     * Re-anchoring to local midnight of the same calendar day keeps all of them correct.
     */
    private static Date localDayFrom(long utcMillis) {
        java.util.Calendar utc = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
        utc.setTimeInMillis(utcMillis);
        java.util.Calendar local = java.util.Calendar.getInstance();
        local.clear();
        local.set(utc.get(java.util.Calendar.YEAR),
                  utc.get(java.util.Calendar.MONTH),
                  utc.get(java.util.Calendar.DAY_OF_MONTH));
        return local.getTime();
    }

    /** Picker-space (UTC midnight) equivalent of a stored local-midnight DOB. */
    private static long utcDayFrom(Date localDay) {
        java.util.Calendar local = java.util.Calendar.getInstance();
        local.setTime(localDay);
        java.util.Calendar utc = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
        utc.clear();
        utc.set(local.get(java.util.Calendar.YEAR),
                local.get(java.util.Calendar.MONTH),
                local.get(java.util.Calendar.DAY_OF_MONTH));
        return utc.getTimeInMillis();
    }

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

        // Restore on back-navigation. AddDependentActivity uses FragmentTransaction.replace
        // on a persistent instance, so onCreateView reruns and this whole step came back
        // blank — the user had to retype the name and password and re-pick DOB and gender.
        // handleContinue() calls collectData() before advancing, so the answers are already
        // in the shared OnboardingData; nothing here is a default that could be mistaken
        // for a real answer.
        if (hostActivity != null) {
            OnboardingData saved = hostActivity.getOnboardingData();
            if (saved.name != null && !saved.name.isEmpty()) etName.setText(saved.name);
            if (saved.password != null && !saved.password.isEmpty()) etPassword.setText(saved.password);
            if (saved.dateOfBirth != null) {
                selectedDob = saved.dateOfBirth;
                tvDobDisplay.setText(DISPLAY_FMT.format(selectedDob));
                tvDobDisplay.setTextColor(0xFFFFFFFF);
            }
            if (saved.gender != null && !saved.gender.isEmpty()) {
                genderAdapter.setSelectedValue(saved.gender);
            }
        }

        return root;
    }

    private void showDatePicker() {
        CalendarConstraints constraints = new CalendarConstraints.Builder()
                .setValidator(DateValidatorPointBackward.now())
                .build();

        MaterialDatePicker.Builder<Long> builder = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select date of birth");
        // Only when one is already stored: with no selection the confirm button stays
        // disabled, which is what we want for a first visit.
        if (selectedDob != null) {
            builder.setSelection(utcDayFrom(selectedDob));
        }

        MaterialDatePicker<Long> picker = builder
                .setCalendarConstraints(constraints)
                .build();

        picker.addOnPositiveButtonClickListener(selection -> {
            selectedDob = localDayFrom(selection);
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
        // 8 to match signup, the backend validator and both apps' copy.
        if (password.length() < 8) {
            Utilities.toast(getContext(), "Password must be at least 8 characters");
            return false;
        }
        // Was never checked, so collectData() could write a null date of birth — age drives
        // every risk model downstream. Checked before gender because it sits above it on
        // screen, so the message points at the first empty field.
        if (selectedDob == null) {
            Utilities.toast(getContext(), "Please select a date of birth");
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

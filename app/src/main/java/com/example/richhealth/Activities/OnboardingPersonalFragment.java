package com.example.richhealth.Activities;
import Utils.Utilities;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;

import androidx.core.content.ContextCompat;
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
    private TextInputEditText inputLocation;   // LEGACY (hidden) — structured fields below replaced it
    private TextInputLayout layoutCountry;
    private AutoCompleteTextView inputCountry;
    private TextInputEditText inputCity;
    private static final int REQ_LOCATION = 7301;
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

        // ── Structured location (2026-08): country dropdown (pre-filled from the
        // device region) + city field + GPS autofill. Stored as "City, Country". ──
        layoutCountry = root.findViewById(R.id.layout_country);
        inputCountry = root.findViewById(R.id.input_country);
        inputCity = root.findViewById(R.id.input_city);

        List<String> countries = new ArrayList<>();
        for (String iso : Locale.getISOCountries()) {
            String name = new Locale("", iso).getDisplayCountry();
            if (!name.isEmpty() && !countries.contains(name)) countries.add(name);
        }
        java.util.Collections.sort(countries);
        inputCountry.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_list_item_1, countries));
        String deviceCountry = Locale.getDefault().getDisplayCountry();
        if (deviceCountry != null && !deviceCountry.isEmpty()) {
            inputCountry.setText(deviceCountry, false);
        }

        // Restore a previously entered "City, Country" split back into the fields.
        if (hostActivity != null && !hostActivity.getOnboardingData().location.isEmpty()) {
            String saved = hostActivity.getOnboardingData().location;
            int comma = saved.lastIndexOf(',');
            if (comma > 0) {
                inputCity.setText(saved.substring(0, comma).trim());
                inputCountry.setText(saved.substring(comma + 1).trim(), false);
            } else {
                inputCity.setText(saved);
            }
        }

        root.findViewById(R.id.btn_use_location).setOnClickListener(v -> fillFromGps());

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

        // Restore gender selection. This used to find the index and then deliberately do
        // nothing, so stepping Back cleared the answer and validate() failed until the
        // user re-tapped. DOB and country were already restored above.
        if (hostActivity != null) {
            String savedGender = hostActivity.getOnboardingData().gender;
            if (savedGender != null && !savedGender.isEmpty()) {
                genderAdapter.setSelectedValue(savedGender);
            }
        }

        return root;
    }

    private void showDatePicker() {
        // Minimum age 10, same cap the iOS picker uses. DateValidatorPointBackward.now()
        // allowed today's date, i.e. an age of 0.
        // UTC + zeroed time: the grid's cells are UTC midnights, so a local-zone cap with
        // a time-of-day made the "exactly 10 years ago" cell unselectable for part of the
        // day in zones ahead of UTC.
        java.util.Calendar maxDob = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
        maxDob.add(java.util.Calendar.YEAR, -10);
        maxDob.set(java.util.Calendar.HOUR_OF_DAY, 0);
        maxDob.set(java.util.Calendar.MINUTE, 0);
        maxDob.set(java.util.Calendar.SECOND, 0);
        maxDob.set(java.util.Calendar.MILLISECOND, 0);
        final long maxDobMillis = maxDob.getTimeInMillis();

        CalendarConstraints constraints = new CalendarConstraints.Builder()
                .setEnd(maxDobMillis)
                .setValidator(DateValidatorPointBackward.before(maxDobMillis))
                .build();

        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select your date of birth")
                .setCalendarConstraints(constraints)
                // Explicit, and clamped: the default selection is today (now out of range),
                // and a DOB saved before this cap existed would open the calendar at
                // January 1900 and could be re-confirmed while still under age.
                .setSelection(selectedDob != null && selectedDob.getTime() <= maxDobMillis
                        ? selectedDob.getTime()
                        : maxDobMillis)
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
        String country = inputCountry.getText() != null ? inputCountry.getText().toString().trim() : "";
        if (country.isEmpty()) {
            layoutCountry.setError("Please select your country");
            return false;
        }
        layoutCountry.setError(null);
        return true;
    }

    /** GPS autofill: last known fix → Geocoder → fill city + country. Best-effort. */
    @SuppressLint("MissingPermission")
    private void fillFromGps() {
        if (getContext() == null) return;
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION}, REQ_LOCATION);
            return;
        }
        try {
            LocationManager lm = (LocationManager) requireContext().getSystemService(android.content.Context.LOCATION_SERVICE);
            Location loc = null;
            for (String provider : lm.getProviders(true)) {
                Location l = lm.getLastKnownLocation(provider);
                if (l != null && (loc == null || l.getTime() > loc.getTime())) loc = l;
            }
            if (loc == null) {
                Utilities.toast(getContext(), "Couldn't get a location fix — type your city instead.");
                return;
            }
            List<Address> addrs = new Geocoder(requireContext(), Locale.getDefault())
                    .getFromLocation(loc.getLatitude(), loc.getLongitude(), 1);
            if (addrs != null && !addrs.isEmpty()) {
                Address a = addrs.get(0);
                if (a.getLocality() != null && !a.getLocality().isEmpty()) {
                    inputCity.setText(a.getLocality());
                }
                if (a.getCountryName() != null && !a.getCountryName().isEmpty()) {
                    inputCountry.setText(a.getCountryName(), false);
                }
            } else {
                Utilities.toast(getContext(), "Couldn't resolve your city — type it instead.");
            }
        } catch (Exception e) {
            Utilities.toast(getContext(), "Couldn't get your location — type it instead.");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            fillFromGps();
        }
    }

    @Override
    public void collectData(OnboardingData data) {
        data.dateOfBirth = selectedDob;
        data.gender = (String) genderAdapter.getSelectedValue();
        // Compose "City, Country" from the structured fields (legacy field unused).
        String city = inputCity.getText() != null ? inputCity.getText().toString().trim() : "";
        String country = inputCountry.getText() != null ? inputCountry.getText().toString().trim() : "";
        data.location = city.isEmpty() ? country : (country.isEmpty() ? city : city + ", " + country);
    }
}

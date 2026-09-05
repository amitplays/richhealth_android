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
    // NO LONGER APPLIED. The minimum-age floor was removed (2026-09): an account may now
    // be created for someone of any age — a newborn included — because a guardian can sign
    // up on their behalf from the account step. Kept, together with maxDobMillis(), so the
    // reasoning behind the UTC/local handling below stays readable; nothing calls them.
    private static final int MIN_AGE_YEARS = 10;

    /**
     * Newest date of birth that still clears the minimum age, expressed as the picker's
     * UTC midnight so it can be compared against a selection directly. Used both to
     * constrain the picker and to re-check a stored value in validate() — the picker
     * constraint alone only governs what the calendar OPENS on.
     *
     * The birthday is worked out from the user's LOCAL today, then converted. Deriving it
     * from UTC "today" instead just moves the off-by-one: for the hours when UTC is still
     * on yesterday's date (any zone ahead of UTC), a user turning exactly MIN_AGE_YEARS
     * today was told they were too young and could not even select their real birthday.
     *
     * Superseded by {@link #latestDobMillis()} — see the note on MIN_AGE_YEARS.
     */
    private static long maxDobMillis() {
        java.util.Calendar cutoff = java.util.Calendar.getInstance();
        cutoff.add(java.util.Calendar.YEAR, -MIN_AGE_YEARS);
        return utcDayFrom(cutoff.getTime());
    }

    /**
     * Newest selectable date of birth now that there is no age floor: LOCAL today, in the
     * picker's UTC-midnight space. Only the future is excluded.
     *
     * Derived from local today for the same reason maxDobMillis() was, and deliberately
     * used with DateValidatorPointBackward.before() (which is inclusive) rather than
     * .now(): east of UTC, local today's UTC midnight is still in the future against the
     * wall clock, and .now() would refuse a baby born today.
     */
    private static long latestDobMillis() {
        return utcDayFrom(new Date());
    }

    /**
     * MaterialDatePicker hands back UTC midnight of the chosen day, but everything
     * downstream — DISPLAY_FMT here, the "yyyy-MM-dd" signup payload in
     * OnboardingActivity, and ProfileFragment's formatters — reads it in the device
     * timezone. West of UTC that printed and SENT the day before the one the user tapped.
     * Re-anchoring to local midnight of the same calendar day at the source keeps every
     * one of those readers correct without changing any of them.
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

    /**
     * Inverse of {@link #localDayFrom(long)} — the picker's UTC midnight for the same
     * calendar day. Needed whenever a stored DOB is handed BACK to the picker or compared
     * against {@link #maxDobMillis()}: those live in UTC-midnight space, and comparing a
     * local midnight against them is off by the UTC offset, which would reject a
     * legitimate "exactly MIN_AGE_YEARS ago" birthday west of UTC.
     */
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
        // No age floor any more — the only bound left is "not in the future", so the cap is
        // today rather than "MIN_AGE_YEARS ago". Everything else about this method is
        // unchanged on purpose: both bugs fixed here previously still have to stay fixed.
        final long maxDobMillis = latestDobMillis();

        CalendarConstraints.Builder constraints = new CalendarConstraints.Builder()
                .setEnd(maxDobMillis)
                .setValidator(DateValidatorPointBackward.before(maxDobMillis));

        MaterialDatePicker.Builder<Long> builder = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select your date of birth");

        if (selectedDob != null) {
            // Returning to this step: re-select their answer, clamped — a DOB saved before
            // this cap existed would otherwise open the calendar at January 1900.
            builder.setSelection(Math.min(utcDayFrom(selectedDob), maxDobMillis));
        } else {
            // First time here: open on the newest allowed month but create NO selection, so
            // the confirm button stays DISABLED until the user actually picks a day.
            // Setting a selection unconditionally (as this did) pre-picked the newest
            // allowed date and enabled OK, so one stray tap recorded that as a real
            // date of birth and walked straight past the "Please select..." guard below.
            constraints.setOpenAt(maxDobMillis);
        }

        MaterialDatePicker<Long> picker = builder
                .setCalendarConstraints(constraints.build())
                .build();

        picker.addOnPositiveButtonClickListener(selection -> {
            selectedDob = localDayFrom(selection);
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
        // The minimum-age re-check that used to sit here is gone with the floor itself
        // (2026-09) — any age is allowed now. Only the "required" guard above remains;
        // a future date is already impossible through the picker's constraint.
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

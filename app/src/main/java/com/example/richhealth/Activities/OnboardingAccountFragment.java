package com.example.richhealth.Activities;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.richhealth.R;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import Models.OnboardingData;

public class OnboardingAccountFragment extends BaseOnboardingFragment {

    private TextInputLayout layoutName, layoutEmail, layoutPhone, layoutPassword, layoutConfirm;
    private TextInputEditText inputName, inputEmail, inputPhone, inputPassword, inputConfirm;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_onboarding_account, container, false);

        layoutName = root.findViewById(R.id.layout_name);
        layoutEmail = root.findViewById(R.id.layout_email);
        layoutPhone = root.findViewById(R.id.layout_phone);
        layoutPassword = root.findViewById(R.id.layout_password);
        layoutConfirm = root.findViewById(R.id.layout_confirm_password);

        inputName = root.findViewById(R.id.input_name);
        inputEmail = root.findViewById(R.id.input_email);
        inputPhone = root.findViewById(R.id.input_phone);
        inputPassword = root.findViewById(R.id.input_password);
        inputConfirm = root.findViewById(R.id.input_confirm_password);

        // Restore previously entered data if user comes back
        if (hostActivity != null) {
            OnboardingData d = hostActivity.getOnboardingData();
            if (!d.name.isEmpty()) inputName.setText(d.name);
            if (!d.email.isEmpty()) inputEmail.setText(d.email);
            if (!d.phoneNumber.isEmpty()) inputPhone.setText(d.phoneNumber);
        }

        TextWatcher clearError = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                layoutName.setError(null);
                layoutEmail.setError(null);
                layoutPhone.setError(null);
                layoutPassword.setError(null);
                layoutConfirm.setError(null);
            }
        };
        inputName.addTextChangedListener(clearError);
        inputEmail.addTextChangedListener(clearError);
        inputPhone.addTextChangedListener(clearError);
        inputPassword.addTextChangedListener(clearError);
        inputConfirm.addTextChangedListener(clearError);

        return root;
    }

    @Override
    public boolean validate() {
        boolean valid = true;

        String name = inputName.getText() != null ? inputName.getText().toString().trim() : "";
        if (name.isEmpty()) {
            layoutName.setError("Name is required");
            valid = false;
        } else {
            layoutName.setError(null);
        }

        String email = inputEmail.getText() != null ? inputEmail.getText().toString().trim() : "";
        if (email.isEmpty()) {
            layoutEmail.setError("Email is required");
            valid = false;
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            layoutEmail.setError("Enter a valid email address");
            valid = false;
        } else {
            layoutEmail.setError(null);
        }

        String phone = inputPhone.getText() != null ? inputPhone.getText().toString().trim() : "";
        if (phone.isEmpty()) {
            layoutPhone.setError("Phone number is required");
            valid = false;
        } else if (!android.util.Patterns.PHONE.matcher(phone).matches() || phone.replaceAll("[^0-9]", "").length() < 7) {
            layoutPhone.setError("Enter a valid phone number");
            valid = false;
        } else {
            layoutPhone.setError(null);
        }

        String password = inputPassword.getText() != null ? inputPassword.getText().toString() : "";
        if (password.isEmpty()) {
            layoutPassword.setError("Password is required");
            valid = false;
        } else if (password.length() < 6) {
            layoutPassword.setError("Password must be at least 6 characters");
            valid = false;
        } else {
            layoutPassword.setError(null);
        }

        String confirm = inputConfirm.getText() != null ? inputConfirm.getText().toString() : "";
        if (confirm.isEmpty()) {
            layoutConfirm.setError("Please confirm your password");
            valid = false;
        } else if (!confirm.equals(password)) {
            layoutConfirm.setError("Passwords do not match");
            valid = false;
        } else {
            layoutConfirm.setError(null);
        }

        return valid;
    }

    @Override
    public void collectData(OnboardingData data) {
        data.name = inputName.getText() != null ? inputName.getText().toString().trim() : "";
        data.email = inputEmail.getText() != null ? inputEmail.getText().toString().trim() : "";
        data.phoneNumber = inputPhone.getText() != null ? inputPhone.getText().toString().trim() : "";
        data.password = inputPassword.getText() != null ? inputPassword.getText().toString() : "";
        data.confirmPassword = inputConfirm.getText() != null ? inputConfirm.getText().toString() : "";
    }
}

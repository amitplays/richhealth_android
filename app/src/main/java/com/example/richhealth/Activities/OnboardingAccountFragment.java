package com.example.richhealth.Activities;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.richhealth.R;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Arrays;
import java.util.List;

import Models.OnboardingData;

public class OnboardingAccountFragment extends BaseOnboardingFragment {

    private TextInputLayout layoutName, layoutEmail, layoutPhone, layoutPassword, layoutConfirm;
    private TextInputEditText inputName, inputEmail, inputPhone, inputPassword, inputConfirm;

    // Guardian/dependent signup (2026-09). Everything below is inert while the switch is off.
    private SwitchMaterial switchGuardian;
    private LinearLayout guardianFields;
    private TextInputLayout layoutParentEmail, layoutParentRelationship;
    private TextInputEditText inputParentEmail;
    private AutoCompleteTextView inputParentRelationship;

    /**
     * The only labels the server accepts for parentRelationship (utils/relationships.js
     * gates on this set). Kept as the NEW ACCOUNT's self-description, which is what the
     * hint "You are signing up for your…" reads out.
     */
    private static final List<String> PARENT_RELATIONSHIPS =
            Arrays.asList("Son", "Daughter", "Grandson", "Granddaughter");

    // Field errors that arrive from the SIGNUP response, i.e. long after this step was
    // left behind. showStep() re-inflates the fragment, so they are stashed here and
    // applied in onCreateView once the new views exist — setting them on the old,
    // detached views would silently do nothing.
    private String pendingEmailError, pendingParentEmailError, pendingParentRelationshipError;

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

        switchGuardian = root.findViewById(R.id.switch_guardian_signup);
        guardianFields = root.findViewById(R.id.guardian_fields_container);
        layoutParentEmail = root.findViewById(R.id.layout_parent_email);
        layoutParentRelationship = root.findViewById(R.id.layout_parent_relationship);
        inputParentEmail = root.findViewById(R.id.input_parent_email);
        inputParentRelationship = root.findViewById(R.id.input_parent_relationship);

        // simple_list_item_1 to match the country dropdown in OnboardingPersonalFragment.
        inputParentRelationship.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_list_item_1, PARENT_RELATIONSHIPS));
        // The dropdown is not a text field, so the shared TextWatcher never sees it.
        inputParentRelationship.setOnItemClickListener(
                (parent, view, pos, id) -> layoutParentRelationship.setError(null));
        switchGuardian.setOnCheckedChangeListener((b, checked) -> {
            guardianFields.setVisibility(checked ? View.VISIBLE : View.GONE);
            if (!checked) {
                // Clear on the way out so a toggle switched on and off again cannot leave
                // a stale address behind for collectData/buildPayload to send.
                inputParentEmail.setText("");
                inputParentRelationship.setText("", false);
                layoutParentEmail.setError(null);
                layoutParentRelationship.setError(null);
            }
        });

        // Restore previously entered data if user comes back
        if (hostActivity != null) {
            OnboardingData d = hostActivity.getOnboardingData();
            if (!d.name.isEmpty()) inputName.setText(d.name);
            if (!d.email.isEmpty()) inputEmail.setText(d.email);
            if (!d.phoneNumber.isEmpty()) inputPhone.setText(d.phoneNumber);
            // Restore the guardian answers too — stepping Back and forward must not
            // silently drop the link the user asked for.
            if (!d.parentEmail.isEmpty() || !d.parentRelationship.isEmpty()) {
                switchGuardian.setChecked(true);
                guardianFields.setVisibility(View.VISIBLE);
                if (!d.parentEmail.isEmpty()) inputParentEmail.setText(d.parentEmail);
                if (!d.parentRelationship.isEmpty()) {
                    inputParentRelationship.setText(d.parentRelationship, false);
                }
            }
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
                // Same all-fields sweep for the guardian pair, so an error raised by the
                // guardian lookup or by signup clears as soon as the user edits.
                layoutParentEmail.setError(null);
                layoutParentRelationship.setError(null);
            }
        };
        inputName.addTextChangedListener(clearError);
        inputEmail.addTextChangedListener(clearError);
        inputPhone.addTextChangedListener(clearError);
        inputPassword.addTextChangedListener(clearError);
        inputConfirm.addTextChangedListener(clearError);
        inputParentEmail.addTextChangedListener(clearError);

        // Apply any error carried over from a rejected signup (see setSignupFieldErrors).
        if (pendingEmailError != null) { layoutEmail.setError(pendingEmailError); pendingEmailError = null; }
        if (pendingParentEmailError != null) {
            layoutParentEmail.setError(pendingParentEmailError);
            pendingParentEmailError = null;
        }
        if (pendingParentRelationshipError != null) {
            layoutParentRelationship.setError(pendingParentRelationshipError);
            pendingParentRelationshipError = null;
        }

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
            // 8, not 6: ForgotPasswordDialog has always required 8, so an account made
            // at 6 could never be reset to a password of its own class.
        } else if (password.length() < 8) {
            layoutPassword.setError("Password must be at least 8 characters");
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

        // Guardian pair — only checked while the toggle is on, so an ordinary signup is
        // validated exactly as before. Both fields are required together: the server
        // rejects a parentEmail without a usable parentRelationship and vice versa.
        if (isGuardianSignup()) {
            String parentEmail = getEnteredParentEmail();
            if (parentEmail.isEmpty()) {
                layoutParentEmail.setError("Enter the parent or guardian's email");
                valid = false;
            } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(parentEmail).matches()) {
                layoutParentEmail.setError("Enter a valid email address");
                valid = false;
            } else if (parentEmail.equalsIgnoreCase(email)) {
                // Same rule the server enforces — catch it here so the person is not walked
                // through 20 more steps only to be bounced at signup.
                layoutParentEmail.setError("That is this account's own email address");
                valid = false;
            } else {
                layoutParentEmail.setError(null);
            }

            if (getSelectedParentRelationship().isEmpty()) {
                layoutParentRelationship.setError("Choose who you are signing up for");
                valid = false;
            } else {
                layoutParentRelationship.setError(null);
            }
        }

        return valid;
    }

    /** True when the "creating this account for someone else" toggle is on. */
    public boolean isGuardianSignup() {
        return switchGuardian != null && switchGuardian.isChecked();
    }

    /** Trimmed guardian address, or "" when the toggle is off / nothing typed. */
    public String getEnteredParentEmail() {
        if (!isGuardianSignup() || inputParentEmail == null || inputParentEmail.getText() == null) return "";
        return inputParentEmail.getText().toString().trim();
    }

    /** The picked "Son/Daughter/Grandson/Granddaughter", or "" when nothing is picked. */
    public String getSelectedParentRelationship() {
        if (!isGuardianSignup() || inputParentRelationship == null
                || inputParentRelationship.getText() == null) return "";
        return inputParentRelationship.getText().toString().trim();
    }

    /** Shows an error on the guardian-email field (registered-address check / signup 400). */
    public void setParentEmailError(String message) {
        if (layoutParentEmail != null) layoutParentEmail.setError(message);
        else pendingParentEmailError = message;
    }

    /**
     * Routes a rejected signup's field errors back onto this step. Signup only fails at the
     * LAST step, so this fragment is detached at that point and its view references still
     * point at the torn-down views — setting an error on them would silently do nothing.
     * The messages are therefore only stashed here and painted by onCreateView once the
     * host has navigated back and the views have been re-inflated.
     */
    public void setSignupFieldErrors(String emailError, String parentEmailError,
                                     String parentRelationshipError) {
        if (emailError != null) pendingEmailError = emailError;
        if (parentEmailError != null) pendingParentEmailError = parentEmailError;
        if (parentRelationshipError != null) pendingParentRelationshipError = parentRelationshipError;
    }

    /** Returns the trimmed email currently entered in the field. */
    public String getEnteredEmail() {
        return inputEmail.getText() != null ? inputEmail.getText().toString().trim() : "";
    }

    /** Shows an error on the email field (e.g. duplicate-email check). */
    public void setEmailError(String message) {
        if (layoutEmail != null) layoutEmail.setError(message);
    }

    @Override
    public void collectData(OnboardingData data) {
        data.name = inputName.getText() != null ? inputName.getText().toString().trim() : "";
        data.email = inputEmail.getText() != null ? inputEmail.getText().toString().trim() : "";
        data.phoneNumber = inputPhone.getText() != null ? inputPhone.getText().toString().trim() : "";
        data.password = inputPassword.getText() != null ? inputPassword.getText().toString() : "";
        data.confirmPassword = inputConfirm.getText() != null ? inputConfirm.getText().toString() : "";
        // Empty unless the toggle is on — buildPayload keys off exactly that.
        data.parentEmail = getEnteredParentEmail();
        data.parentRelationship = getSelectedParentRelationship();
    }
}

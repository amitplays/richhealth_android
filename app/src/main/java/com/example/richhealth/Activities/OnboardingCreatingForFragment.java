package com.example.richhealth.Activities;

import Utils.Utilities;

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
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.richhealth.R;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import Models.OnboardingData;
import Models.SelectableOption;

/**
 * Step 1 of onboarding: "Who are you creating this account for?"
 *
 * Replaces the guardian toggle that used to sit at the bottom of the ACCOUNT step
 * (2026-09). Two reasons it moved:
 *
 *   1. Shape. A yes/no switch under five text fields reads as a footnote. This is not
 *      a footnote — it decides who every following answer is about — so it is its own
 *      step, with the same selectable cards the rest of onboarding uses.
 *   2. Timing. The guardian address is verified BEFORE the step can be left, and that
 *      check fails CLOSED (see OnboardingActivity.checkGuardianEmailThenAdvance). At
 *      the old placement, blocking meant throwing away a filled-in account form on a
 *      network blip, so it had to fail open — which made it not really a check. Here
 *      nothing is invested yet, so "try again" costs the user one tap.
 *
 * Picking "Myself" leaves parentEmail/parentRelationship empty, which is exactly what
 * buildPayload keys off: an ordinary signup's JSON is byte-for-byte what it was.
 */
public class OnboardingCreatingForFragment extends BaseOnboardingFragment {

    /** Card values. Local to this step — nothing outside it ever sees them. */
    public static final String FOR_MYSELF = "self";
    public static final String FOR_SOMEONE_ELSE = "other";

    /**
     * The only labels the server accepts for parentRelationship. Mirrors
     * DEPENDENCY_RELATIONSHIPS in the backend's utils/relationships.js — the direct
     * family line in BOTH directions, since caring for a parent or grandparent is as
     * real as creating an account for a child.
     *
     * READ AS: "this person is your ___". The value describes the NEW ACCOUNT, not the
     * person filling the form, because that is the direction the server stores and the
     * direction its reciprocal map expects. Creating an account for your father → the
     * new account IS your father → "Father". There is no inversion anywhere on the
     * client. Ordered oldest generation first, matching the family picker.
     */
    private static final List<String> PARENT_RELATIONSHIPS = Arrays.asList(
            "Paternal Grandfather", "Paternal Grandmother",
            "Maternal Grandfather", "Maternal Grandmother",
            "Father", "Mother",
            "Son", "Daughter",
            "Grandson", "Granddaughter"
    );

    private SelectableCardAdapter creatingForAdapter;
    private LinearLayout otherContainer;
    private TextInputLayout layoutParentEmail, layoutParentRelationship;
    private TextInputEditText inputParentEmail;
    private AutoCompleteTextView inputParentRelationship;

    /**
     * Answers held on the FRAGMENT, not in the views. OnboardingActivity keeps one
     * instance of every step and navigates with FragmentTransaction.replace, so
     * onCreateView reruns on every visit and rebuilds all views empty — the same reason
     * OnboardingPersonalFragment keeps selectedDob. Restoring from OnboardingData
     * instead would not work for the choice itself: "Myself" writes two empty strings,
     * which is indistinguishable from "not answered yet".
     */
    private String choice = "";
    private String enteredParentEmail = "";
    private String pickedRelationship = "";

    // Field errors that arrive from the SIGNUP response — i.e. ~20 steps after this one
    // was left. showStep() re-inflates the fragment, so they are stashed and painted in
    // onCreateView once the new views exist; setting them on the old, detached views
    // would silently do nothing.
    private String pendingParentEmailError, pendingParentRelationshipError;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_onboarding_creating_for, container, false);

        otherContainer = root.findViewById(R.id.creating_for_other_container);
        layoutParentEmail = root.findViewById(R.id.layout_parent_email);
        layoutParentRelationship = root.findViewById(R.id.layout_parent_relationship);
        inputParentEmail = root.findViewById(R.id.input_parent_email);
        inputParentRelationship = root.findViewById(R.id.input_parent_relationship);

        List<SelectableOption> options = new ArrayList<>(Arrays.asList(
                new SelectableOption("Myself",       R.drawable.ic_person,   FOR_MYSELF),
                new SelectableOption("Someone else", R.drawable.ic_guardian, FOR_SOMEONE_ELSE)
        ));

        RecyclerView rv = root.findViewById(R.id.rv_creating_for);
        rv.setLayoutManager(new GridLayoutManager(getContext(), 2));
        creatingForAdapter = new SelectableCardAdapter(options, false);
        rv.setAdapter(creatingForAdapter);
        creatingForAdapter.setOnSelectionChangedListener(this::applyChoiceVisibility);

        // simple_list_item_1 to match the country dropdown in OnboardingPersonalFragment.
        inputParentRelationship.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_list_item_1, PARENT_RELATIONSHIPS));
        // The dropdown is not a text field, so the TextWatcher below never sees it.
        inputParentRelationship.setOnItemClickListener((parent, view, pos, id) -> {
            pickedRelationship = PARENT_RELATIONSHIPS.get(pos);
            layoutParentRelationship.setError(null);
        });

        TextWatcher clearError = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                // An error raised by the registered-address lookup (or by a rejected
                // signup) clears the moment the user edits the address.
                layoutParentEmail.setError(null);
            }
        };
        inputParentEmail.addTextChangedListener(clearError);

        // Restore this step's own answers — stepping Back and forward must not silently
        // drop the link the user asked for, and must not silently drop "Myself" either.
        if (!choice.isEmpty()) {
            creatingForAdapter.setSelectedValue(choice);
        }
        if (!enteredParentEmail.isEmpty()) inputParentEmail.setText(enteredParentEmail);
        if (!pickedRelationship.isEmpty()) {
            inputParentRelationship.setText(pickedRelationship, false);
        }
        applyChoiceVisibility();

        // Apply any error carried over from a rejected signup (see setSignupFieldErrors).
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

    /**
     * Snapshot the view state before the views are torn down, so onCreateView can put it
     * back. collectData() only runs on Continue, and handleBack() never calls it, so
     * without this a Back press would lose a half-typed address.
     */
    @Override
    public void onDestroyView() {
        if (creatingForAdapter != null && creatingForAdapter.hasSelection()) {
            choice = String.valueOf(creatingForAdapter.getSelectedValue());
        }
        enteredParentEmail = readParentEmail();
        pickedRelationship = readRelationship();
        super.onDestroyView();
    }

    /** Show the creator's own email + the relationship picker only for "Someone else". */
    private void applyChoiceVisibility() {
        boolean someoneElse = FOR_SOMEONE_ELSE.equals(currentChoice());
        otherContainer.setVisibility(someoneElse ? View.VISIBLE : View.GONE);
        if (!someoneElse) {
            // Clear on the way out so a choice made and then undone cannot leave a stale
            // address behind for collectData/buildPayload to send.
            inputParentEmail.setText("");
            inputParentRelationship.setText("", false);
            pickedRelationship = "";
            layoutParentEmail.setError(null);
            layoutParentRelationship.setError(null);
        }
    }

    /** The value of the selected card, or "" while nothing is picked. */
    private String currentChoice() {
        if (creatingForAdapter == null || !creatingForAdapter.hasSelection()) return choice;
        Object v = creatingForAdapter.getSelectedValue();
        return v == null ? "" : String.valueOf(v);
    }

    private String readParentEmail() {
        if (inputParentEmail == null || inputParentEmail.getText() == null) return enteredParentEmail;
        return inputParentEmail.getText().toString().trim();
    }

    private String readRelationship() {
        if (inputParentRelationship == null || inputParentRelationship.getText() == null) {
            return pickedRelationship;
        }
        return inputParentRelationship.getText().toString().trim();
    }

    /** True when this account is being created for someone other than the person here. */
    public boolean isCreatingForSomeoneElse() {
        return FOR_SOMEONE_ELSE.equals(currentChoice());
    }

    /** Trimmed creator address, or "" for a "Myself" signup. */
    public String getEnteredParentEmail() {
        return isCreatingForSomeoneElse() ? readParentEmail() : "";
    }

    /** The picked relationship label, or "" for a "Myself" signup. */
    public String getSelectedParentRelationship() {
        return isCreatingForSomeoneElse() ? readRelationship() : "";
    }

    /** Shows an error on the creator's email field (registered-address check / signup 400). */
    public void setParentEmailError(String message) {
        if (layoutParentEmail != null) layoutParentEmail.setError(message);
        else pendingParentEmailError = message;
    }

    /**
     * Routes a rejected signup's field errors back onto this step. Signup only fails at
     * the LAST step, so this fragment is detached by then and its view references still
     * point at torn-down views — setting an error on them would silently do nothing. The
     * messages are stashed here and painted by onCreateView once the host has navigated
     * back and the views have been re-inflated.
     */
    public void setSignupFieldErrors(String parentEmailError, String parentRelationshipError) {
        if (parentEmailError != null) pendingParentEmailError = parentEmailError;
        if (parentRelationshipError != null) pendingParentRelationshipError = parentRelationshipError;
    }

    @Override
    public boolean validate() {
        String picked = currentChoice();
        if (picked.isEmpty()) {
            Utilities.toast(getContext(), "Please choose who this account is for");
            return false;
        }
        // "Myself" is the whole answer — nothing else on this step applies, and the rest
        // of onboarding proceeds exactly as it always has.
        if (!FOR_SOMEONE_ELSE.equals(picked)) return true;

        boolean valid = true;

        String parentEmail = readParentEmail();
        if (parentEmail.isEmpty()) {
            layoutParentEmail.setError("Enter the email of your own account");
            valid = false;
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(parentEmail).matches()) {
            layoutParentEmail.setError("Enter a valid email address");
            valid = false;
        } else {
            layoutParentEmail.setError(null);
        }

        if (readRelationship().isEmpty()) {
            layoutParentRelationship.setError("Choose how they are related to you");
            valid = false;
        } else {
            layoutParentRelationship.setError(null);
        }

        return valid;
    }

    @Override
    public void collectData(OnboardingData data) {
        // Both are "" for a "Myself" signup, and buildPayload omits the keys entirely in
        // that case — so an ordinary signup's payload is unchanged.
        data.parentEmail = getEnteredParentEmail();
        data.parentRelationship = getSelectedParentRelationship();
    }
}

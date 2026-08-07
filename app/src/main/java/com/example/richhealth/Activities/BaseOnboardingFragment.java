package com.example.richhealth.Activities;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import Models.OnboardingData;

public abstract class BaseOnboardingFragment extends Fragment {

    protected CardStepHost hostActivity;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof CardStepHost) {
            hostActivity = (CardStepHost) context;
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        hostActivity = null;
    }

    /**
     * Validate the current step's inputs. Show appropriate error messages.
     * @return true if all required fields are filled correctly
     */
    public abstract boolean validate();

    /**
     * Write this step's selections/inputs into the shared OnboardingData object.
     * Called only after validate() returns true.
     */
    public abstract void collectData(OnboardingData data);
}

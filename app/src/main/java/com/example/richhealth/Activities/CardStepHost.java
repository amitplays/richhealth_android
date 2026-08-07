package com.example.richhealth.Activities;

import Models.OnboardingData;
import Models.StepConfig;

/**
 * Interface for activities that host CardStepFragment and other BaseOnboardingFragments.
 * Implemented by OnboardingActivity and AddDependentActivity.
 */
public interface CardStepHost {
    StepConfig getCardStepConfig(int stepIndex);
    OnboardingData getOnboardingData();
}

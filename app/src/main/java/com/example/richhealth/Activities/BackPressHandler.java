package com.example.richhealth.Activities;

/**
 * Implement this on any Fragment hosted inside MainActivity that needs to intercept
 * the back button before MainActivity's own logic runs.
 *
 * Use-cases:
 *  — Fragment has an open side-panel Dialog (HealthDataFragment, AIFragment)
 *  — Fragment has other dismissible overlay state
 *
 * Contract: return true if the fragment consumed the press, false to let
 * MainActivity continue with its normal back-press sequence.
 */
public interface BackPressHandler {
    /**
     * @return true  → event consumed (do NOT let MainActivity continue)
     *         false → event not consumed (MainActivity will handle it)
     */
    boolean handleBackPress();
}

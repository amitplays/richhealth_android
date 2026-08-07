package com.example.richhealth.Activities;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

/**
 * Replaced by DailyCheckInActivity's built-in list — kept as a stub to avoid
 * breaking any stale references during the transition.
 */
public class CheckInHistoryFragment extends Fragment {

    public static CheckInHistoryFragment newInstance() {
        return new CheckInHistoryFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return new View(requireContext());
    }

    public void refreshData() { /* no-op */ }
}

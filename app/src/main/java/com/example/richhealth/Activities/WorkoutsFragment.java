package com.example.richhealth.Activities;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LayoutAnimationController;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;
import com.example.richhealth.R;
import com.google.android.material.button.MaterialButton;

import java.util.Arrays;
import java.util.List;
import Adapters.WorkoutAdapter;
import Database.DatabaseHelper;
import Models.Workout;

public class WorkoutsFragment extends Fragment {
    private RecyclerView workoutRecyclerView;
    private WorkoutAdapter workoutAdapter;
    private DatabaseHelper dbHelper;
    private TextView workoutsCount;
    private LinearLayout emptyState;
    private List<Workout> workouts;
    private RecyclerView workoutTypesRecycler;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_workouts, container, false);
        initViews(view);
        setupClickListeners();
        loadWorkouts();
        return view;
    }

    private void initViews(View view) {
        // Initialize workout types recycler
        workoutTypesRecycler = view.findViewById(R.id.workout_types_recycler);
        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext(),
                LinearLayoutManager.HORIZONTAL, false);
        workoutTypesRecycler.setLayoutManager(layoutManager);

        // Add snap helper for carousel effect
        PagerSnapHelper snapHelper = new PagerSnapHelper();
        snapHelper.attachToRecyclerView(workoutTypesRecycler);

        // Initialize workout list recycler
        workoutRecyclerView = view.findViewById(R.id.selected_exercises_recycler);
        workoutRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        // Setup animation for workout list
        LayoutAnimationController animation = AnimationUtils.loadLayoutAnimation(
                requireContext(), R.anim.layout_animation_slide_bottom);
        workoutRecyclerView.setLayoutAnimation(animation);
        workoutTypesRecycler.setLayoutAnimation(animation);

        // Get view references
        workoutsCount = view.findViewById(R.id.workouts_count);
        emptyState = view.findViewById(R.id.empty_state);

        TextView headerTitle = view.findViewById(R.id.header_title);
        ImageButton addWorkoutButton = view.findViewById(R.id.add_workout_button);
        addWorkoutButton.setOnClickListener(v -> startActivity(
                new Intent(getActivity(), AddWorkout.class)));

        // Animate header elements
        animateHeaderElements(headerTitle, workoutsCount, addWorkoutButton);

        dbHelper = new DatabaseHelper(requireContext());
    }

    private void animateHeaderElements(TextView title, TextView subtitle, View button) {
        // Fade in and slide down animation for title
        title.setTranslationY(-50);
        title.animate()
                .translationY(0)
                .alpha(1.0f)
                .setDuration(500)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        // Fade in and slide down animation for subtitle with delay
        subtitle.setTranslationY(-50);
        subtitle.animate()
                .translationY(0)
                .alpha(1.0f)
                .setStartDelay(100)
                .setDuration(500)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        // Fade in and slide down animation for button with delay
        button.setTranslationY(-50);
        button.animate()
                .translationY(0)
                .alpha(1.0f)
                .setStartDelay(200)
                .setDuration(500)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadWorkouts();

        // Re-run animations
        View view = getView();
        if (view != null) {
            TextView headerTitle = view.findViewById(R.id.header_title);
            ImageButton addWorkoutButton = view.findViewById(R.id.add_workout_button);
            animateHeaderElements(headerTitle, workoutsCount, addWorkoutButton);

            workoutRecyclerView.scheduleLayoutAnimation();
            workoutTypesRecycler.scheduleLayoutAnimation();
        }
    }


    private void setupClickListeners() {
        // Add any additional click listeners here
    }
    private void updateWorkoutsCount() {
        int totalWorkouts = workouts.size();
        workoutsCount.setText(totalWorkouts + " workouts");
    }

    private void checkEmptyState() {
        if (workouts.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            workoutRecyclerView.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            workoutRecyclerView.setVisibility(View.VISIBLE);
        }
    }


    private void loadWorkouts() {
        workouts = dbHelper.getAllWorkouts();
        workoutAdapter = new WorkoutAdapter(workouts, requireContext(),
                new WorkoutAdapter.WorkoutActionListener() {
                    @Override
                    public void onWorkoutClick(int position) {
                        // Handle workout click
                    }

                    @Override
                    public void onWorkoutEdit(int position) {
                        // Handle edit
                    }

                    @Override
                    public void onWorkoutDelete(int position) {
                        if (position >= 0 && position < workouts.size()) {
                            new AlertDialog.Builder(requireContext())
                                    .setTitle("Delete Workout")
                                    .setMessage("Are you sure you want to delete this workout?")
                                    .setPositiveButton("Delete", (dialog, which) -> {
                                        Workout workout = workouts.get(position);
                                        dbHelper.deleteWorkout(workout.getId());
                                        workouts.remove(position);
                                        workoutAdapter.notifyDataSetChanged();
                                        // Run animation again
                                        workoutRecyclerView.scheduleLayoutAnimation();
                                        updateWorkoutsCount();
                                        checkEmptyState();
                                    })
                                    .setNegativeButton("Cancel", null)
                                    .show();
                        }
                    }
                });
        workoutRecyclerView.setAdapter(workoutAdapter);
        updateWorkoutsCount();
        checkEmptyState();

        // Run initial animation
        workoutRecyclerView.scheduleLayoutAnimation();
    }
}
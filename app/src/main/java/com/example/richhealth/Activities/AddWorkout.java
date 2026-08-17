package com.example.richhealth.Activities;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LayoutAnimationController;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.richhealth.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import Adapters.ExerciseAdapter;
import Adapters.ExerciseInWorkoutAdapter;
import Database.DatabaseHelper;
import Models.Exercise;
import Models.Workout;
import Models.WorkoutExercise;
import Utils.Utilities;

public class AddWorkout extends Activity {
    private TextView selectedExerciseText;
    private TextInputEditText setsInput, repsInput, weightInput;
    // Change these variables
    private MaterialButton saveButton;
    private MaterialCardView addExerciseBtn;
    private MaterialCardView selectExerciseCard;
    private ImageView selectExerciseAddIcon;
    private Exercise selectedExercise;
    private DatabaseHelper dbHelper;
    private RecyclerView exercisesRecycler;
    private List<WorkoutExercise> exerciseList;
    private ExerciseInWorkoutAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.add_workout);

        dbHelper = new DatabaseHelper(this);
        exerciseList = new ArrayList<>();
        initViews();
        setupRecyclerView();
        setupClickListeners();
    }

    private void initViews() {
        selectedExerciseText = findViewById(R.id.selected_exercise_text);
        selectExerciseCard = findViewById(R.id.select_exercise_card);
        selectExerciseAddIcon = findViewById(R.id.select_exercise_add_icon);
        setsInput = findViewById(R.id.sets_input);
        repsInput = findViewById(R.id.reps_input);
        weightInput = findViewById(R.id.weight_input);
        saveButton = findViewById(R.id.save_button);      // Now correctly casts to MaterialCardView
        addExerciseBtn = findViewById(R.id.add_exercise); // Now correctly casts to MaterialCardView
        exercisesRecycler = findViewById(R.id.exercises_recycler);

        // Setup animations for header elements
        TextView headerTitle = findViewById(R.id.header_title);
        TextView workoutInfo = findViewById(R.id.workout_info);
        animateHeaderElements(headerTitle, workoutInfo);

        // Setup recycler view animation
        LayoutAnimationController animation = AnimationUtils.loadLayoutAnimation(
                this, R.anim.layout_animation_slide_bottom);
        exercisesRecycler.setLayoutAnimation(animation);
    }

    private void animateHeaderElements(TextView title, TextView subtitle) {
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
    }
    private void setupRecyclerView() {
        exercisesRecycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ExerciseInWorkoutAdapter(exerciseList, this);
        exercisesRecycler.setAdapter(adapter);
    }

    private void setupClickListeners() {
        // A tap anywhere on the select row (text, the "+" icon, or the surrounding card)
        // opens the exercise picker — previously only the text itself was tappable.
        View.OnClickListener openPicker = v -> showExerciseSelectionDialog();
        selectedExerciseText.setOnClickListener(openPicker);
        if (selectExerciseCard != null) selectExerciseCard.setOnClickListener(openPicker);
        if (selectExerciseAddIcon != null) selectExerciseAddIcon.setOnClickListener(openPicker);

        addExerciseBtn.setOnClickListener(view -> {
            if (selectedExercise != null && !isEmpty(setsInput) && !isEmpty(repsInput) && !isEmpty(weightInput)) {
                if (validateAndAddCurrentExercise()) {
                    clearInputs();
                }
            }
        });

        saveButton.setOnClickListener(v -> {
            if (selectedExercise != null && !isEmpty(setsInput) && !isEmpty(repsInput) && !isEmpty(weightInput)) {
                if (validateAndAddCurrentExercise()) {
                    saveWorkout();
                }
            } else {
                saveWorkout();
            }
        });
    }

    private boolean isEmpty(TextInputEditText input) {
        return input.getText() == null || input.getText().toString().trim().isEmpty();
    }

    private boolean validateAndAddCurrentExercise() {
        if (selectedExercise == null) {
            Utilities.toast(this, "Please select an exercise");
            return false;
        }

        String setsStr = setsInput.getText().toString();
        String repsStr = repsInput.getText().toString();
        String weightStr = weightInput.getText().toString();

        if (setsStr.isEmpty() || repsStr.isEmpty() || weightStr.isEmpty()) {
            Utilities.toast(this, "Please fill in all fields");
            return false;
        }

        try {
            int sets = Integer.parseInt(setsStr);
            int reps = Integer.parseInt(repsStr);
            double weight = Double.parseDouble(weightStr);

            // Validate ranges
            if (sets <= 0 || reps <= 0 || weight <= 0) {
                Utilities.toast(this, "Please enter valid numbers greater than 0");
                return false;
            }

            WorkoutExercise workoutExercise = new WorkoutExercise(selectedExercise, sets, reps, weight);
            exerciseList.add(workoutExercise);
            adapter.notifyItemInserted(exerciseList.size() - 1);

            // Scroll to the newly added item
            exercisesRecycler.smoothScrollToPosition(exerciseList.size() - 1);

            clearInputs();
            return true;
        } catch (NumberFormatException e) {
            Utilities.toast(this, "Please enter valid numbers");
            return false;
        }
    }

    private void clearInputs() {
        selectedExercise = null;
        selectedExerciseText.setText("Select Exercise");
        setsInput.setText("");
        repsInput.setText("");
        weightInput.setText("");
    }

    private void showExerciseSelectionDialog() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.fragment_exercises);

        RecyclerView exerciseRecyclerView = dialog.findViewById(R.id.exercises_recycler_view);
        EditText searchInput = dialog.findViewById(R.id.exercise_search_bar);
        AutoCompleteTextView categoryFilter = dialog.findViewById(R.id.category_filter);

        // Cached, no-DB-seed load so re-opening the picker is instant (RH-13 lag fix).
        List<Exercise> exercises = Utilities.loadExercisesFromJsonCached(this);

        // Setup category filter
        Set<String> categories = new HashSet<>();
        categories.add("All");
        for(Exercise exercise : exercises) {
            categories.add(exercise.getCategory());
        }
        List<String> categoryList = new ArrayList<>(categories);
        Collections.sort(categoryList);

        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(
                this,
                R.layout.dropdown_item,
                categoryList
        );

        categoryFilter.setAdapter(categoryAdapter);
        categoryFilter.setText("All", false);

        // Setup RecyclerView
        exerciseRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        ExerciseAdapter adapter = new ExerciseAdapter(exercises, this, exercise -> {
            selectedExercise = exercise;
            selectedExerciseText.setText(exercise.getName());
            dialog.dismiss();
            setsInput.requestFocus();
        });
        exerciseRecyclerView.setAdapter(adapter);

        // Setup search and filter
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String category = categoryFilter.getText().toString();
                adapter.getFilter().filter(s.toString() + "\n" + category);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        categoryFilter.setOnItemClickListener((parent, view, position, id) -> {
            String searchText = searchInput.getText().toString();
            String selectedCategory = categoryList.get(position);
            adapter.getFilter().filter(searchText + "\n" + selectedCategory);
        });

        dialog.show();
    }

    private void saveWorkout() {
        if (exerciseList.isEmpty()) {
            Utilities.toast(this, "Please add at least one exercise");
            return;
        }

        try {
            // Create workout first
            Workout workout = new Workout();
            workout.setName("Workout " + System.currentTimeMillis());
            long workoutId = dbHelper.insertWorkout(workout);

            // Add all exercises to the workout
            for (WorkoutExercise workoutExercise : exerciseList) {
                dbHelper.insertWorkoutExerciseMapping(
                        workoutId,
                        workoutExercise.getExercise().getId(),
                        workoutExercise.getSets(),
                        workoutExercise.getReps(),
                        workoutExercise.getWeight()
                );
            }

            Utilities.toast(this, "Workout saved successfully");
            finish();
        } catch (Exception e) {
            Utilities.toast(this, "Error saving workout: " + e.getMessage());
        }
    }
}
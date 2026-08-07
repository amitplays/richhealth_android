package Adapters;

import android.app.Dialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.richhealth.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;

import Database.DatabaseHelper;
import Models.Exercise;
import Models.Workout;
import Models.WorkoutExercise;
import Utils.Utilities;
public class WorkoutAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_HEADER = 0;
    private static final int TYPE_WORKOUT = 1;

    private List<Object> items;
    private List<Workout> workouts;
    private Context context;
    private WorkoutActionListener listener;
    private SimpleDateFormat dateFormat;
    private SimpleDateFormat monthFormat;

    public interface WorkoutActionListener {
        void onWorkoutClick(int position);
        void onWorkoutEdit(int position);
        void onWorkoutDelete(int position);
    }

    public WorkoutAdapter(List<Workout> workouts, Context context, WorkoutActionListener listener) {
        this.workouts = workouts;
        this.context = context;
        this.listener = listener;
        this.dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        this.monthFormat = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        processWorkouts(workouts);
    }

    private void processWorkouts(List<Workout> workouts) {
        TreeMap<String, List<Workout>> groupedWorkouts = new TreeMap<>(Collections.reverseOrder());

        // Group workouts by month
        for (Workout workout : workouts) {
            Calendar cal = Calendar.getInstance();
            cal.setTime(workout.getDate());
            String monthYear = monthFormat.format(cal.getTime());

            if (!groupedWorkouts.containsKey(monthYear)) {
                groupedWorkouts.put(monthYear, new ArrayList<>());
            }
            groupedWorkouts.get(monthYear).add(workout);
        }

        // Create flat list with headers
        items = new ArrayList<>();
        for (String monthYear : groupedWorkouts.keySet()) {
            items.add(monthYear); // Header
            items.addAll(groupedWorkouts.get(monthYear));
        }
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position) instanceof String ? TYPE_HEADER : TYPE_WORKOUT;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_HEADER) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_workout_header, parent, false);
            return new HeaderViewHolder(view);
        } else {
            View view = LayoutInflater.from(context).inflate(R.layout.item_workout, parent, false);
            return new WorkoutViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HeaderViewHolder) {
            HeaderViewHolder headerHolder = (HeaderViewHolder) holder;
            String monthYear = (String) items.get(position);
            headerHolder.headerTitle.setText(monthYear);
        } else {
            WorkoutViewHolder workoutHolder = (WorkoutViewHolder) holder;
            Workout workout = (Workout) items.get(position);

            workoutHolder.workoutName.setText("Workout on ");
            workoutHolder.workoutDate.setText(dateFormat.format(workout.getDate()));

            ExerciseInWorkoutAdapter exerciseAdapter = new ExerciseInWorkoutAdapter(workout.getExercises(), context);
            workoutHolder.exercisesRecycler.setLayoutManager(new LinearLayoutManager(context));
            workoutHolder.exercisesRecycler.setAdapter(exerciseAdapter);

            workoutHolder.itemView.setOnClickListener(v -> {
                if(listener != null) {
                    listener.onWorkoutClick(workouts.indexOf(workout));
                }
            });

            workoutHolder.editButton.setOnClickListener(v -> {
                showEditDialog(workout, workouts.indexOf(workout));
            });

            workoutHolder.deleteButton.setOnClickListener(v -> {
                if(listener != null) {
                    listener.onWorkoutDelete(workouts.indexOf(workout));
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    public void updateWorkouts(List<Workout> newWorkouts) {
        this.workouts = newWorkouts;
        processWorkouts(newWorkouts);
        notifyDataSetChanged();
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView headerTitle;

        HeaderViewHolder(View itemView) {
            super(itemView);
            headerTitle = itemView.findViewById(R.id.header_title);
        }
    }

    static class WorkoutViewHolder extends RecyclerView.ViewHolder {
        TextView workoutName, workoutDate;
        RecyclerView exercisesRecycler;
        ImageButton editButton, deleteButton;

        WorkoutViewHolder(View itemView) {
            super(itemView);
            workoutName = itemView.findViewById(R.id.workout_name);
            workoutDate = itemView.findViewById(R.id.workout_date);
            exercisesRecycler = itemView.findViewById(R.id.exercises_recycler);
            editButton = itemView.findViewById(R.id.edit_workout);
            deleteButton = itemView.findViewById(R.id.delete_workout);
        }
    }

    private void showEditDialog(Workout workout, int position) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context, R.style.AlertDialogTheme);
        builder.setTitle("Edit Workout");

        View view = LayoutInflater.from(context).inflate(R.layout.dialog_edit_workout, null);
        RecyclerView exercisesRecycler = view.findViewById(R.id.exercises_recycler);
        exercisesRecycler.setLayoutManager(new LinearLayoutManager(context));

        List<WorkoutExercise> workoutExercises = new ArrayList<>(workout.getExercises());
        WorkoutExerciseAdapter adapter = new WorkoutExerciseAdapter(workoutExercises, exercises -> {
            workout.setExercises(exercises);
        });
        exercisesRecycler.setAdapter(adapter);

        builder.setView(view);
        builder.setPositiveButton("Save", (dialog, which) -> {
            DatabaseHelper dbHelper = new DatabaseHelper(context);
            // Delete existing exercise mappings and re-insert with updated sets/reps/weight
            dbHelper.deleteWorkoutExercises(workout.getId());
            for (Models.WorkoutExercise ex : workout.getExercises()) {
                if (ex.getExercise() != null) {
                    dbHelper.insertWorkoutExerciseMapping(
                            workout.getId(),
                            ex.getExercise().getId(),
                            ex.getSets(),
                            ex.getReps(),
                            ex.getWeight());
                }
            }
            notifyDataSetChanged();
        });

        builder.setNeutralButton("Add Exercise", null);

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(dialogInterface -> {
            Button addButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
            addButton.setOnClickListener(v -> showExerciseSelectionDialog(workout, adapter));
        });

        dialog.show();
    }

    private void showExerciseEditDialog(WorkoutExercise exercise) {
        Dialog dialog = new Dialog(context);
        dialog.setContentView(R.layout.dialog_edit_workout);

        TextView exerciseName = dialog.findViewById(R.id.exercise_name);
        EditText setsInput = dialog.findViewById(R.id.sets_input);
        EditText repsInput = dialog.findViewById(R.id.reps_input);
        EditText weightInput = dialog.findViewById(R.id.weight_input);
        Button saveButton = dialog.findViewById(R.id.save_button);

        exerciseName.setText(exercise.getExercise().getName());
        setsInput.setText(String.valueOf(exercise.getSets()));
        repsInput.setText(String.valueOf(exercise.getReps()));
        weightInput.setText(String.format("%.1f", exercise.getWeight()));

        saveButton.setOnClickListener(v -> {
            try {
                exercise.setSets(Integer.parseInt(setsInput.getText().toString()));
                exercise.setReps(Integer.parseInt(repsInput.getText().toString()));
                exercise.setWeight(Double.parseDouble(weightInput.getText().toString()));

                notifyDataSetChanged();
                dialog.dismiss();
                Utilities.toast(context, "Exercise updated");
            } catch (NumberFormatException e) {
                Utilities.toast(context, "Please enter valid numbers");
            }
        });

        dialog.show();
    }

    private void showExerciseSelectionDialog(Workout workout, WorkoutExerciseAdapter adapter) {
        Dialog dialog = new Dialog(context);
        dialog.setContentView(R.layout.fragment_exercises);

        RecyclerView exerciseRecycler = dialog.findViewById(R.id.exercises_recycler_view);
        exerciseRecycler.setLayoutManager(new LinearLayoutManager(context));

        List<Exercise> exercises = Utilities.loadExercisesFromJson(context);
        ExerciseAdapter exerciseAdapter = new ExerciseAdapter(exercises, context, position -> {
            Exercise exercise = exercises.get(position);
            workout.addExercise(new WorkoutExercise(exercise, 3, 12, 0));
            adapter.notifyDataSetChanged();
            dialog.dismiss();
        });

        exerciseRecycler.setAdapter(exerciseAdapter);
        dialog.show();
    }
}
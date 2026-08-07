package Adapters;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.richhealth.R;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import Models.WorkoutExercise;

public class ExerciseInWorkoutAdapter extends RecyclerView.Adapter<ExerciseInWorkoutAdapter.ViewHolder> {
    private List<WorkoutExercise> workoutExercises;
    private Context context;
    private static final Map<String, Integer> categoryColors = new HashMap<>();

    static {
        categoryColors.put("Chest", Color.parseColor("#008b8b"));      // Orange
        categoryColors.put("Back", Color.parseColor("#2196F3"));       // Blue
        categoryColors.put("Shoulders", Color.parseColor("#4CAF50"));  // Green
        categoryColors.put("Biceps", Color.parseColor("#9C27B0"));    // Purple
        categoryColors.put("Triceps", Color.parseColor("#E91E63"));   // Pink
        categoryColors.put("Legs", Color.parseColor("#FFC107"));      // Yellow
        categoryColors.put("Core", Color.parseColor("#00BCD4"));      // Cyan
        // Add more categories as needed
    }

    public ExerciseInWorkoutAdapter(List<WorkoutExercise> workoutExercises, Context context) {
        this.workoutExercises = workoutExercises;
        this.context = context;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_exercise_in_workout, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        WorkoutExercise workoutExercise = workoutExercises.get(position);
        if (workoutExercise != null && workoutExercise.getExercise() != null) {
            String category = workoutExercise.getExercise().getCategory();

            // Set category with the appropriate color
            holder.exerciseCategory.setText(category);
            holder.exerciseCategory.setTextColor(categoryColors.getOrDefault(category, Color.parseColor("#008b8b")));

            // Set exercise name
            holder.exerciseName.setText(workoutExercise.getExercise().getName());

            // Format and set exercise details
            String details = String.format("%d sets × %d reps • %.1f kg",
                    workoutExercise.getSets(),
                    workoutExercise.getReps(),
                    workoutExercise.getWeight());
            holder.exerciseDetails.setText(details);
        }
    }

    @Override
    public int getItemCount() {
        return workoutExercises != null ? workoutExercises.size() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView exerciseName, exerciseCategory, exerciseDetails;

        ViewHolder(View itemView) {
            super(itemView);
            exerciseName = itemView.findViewById(R.id.exercise_name);
            exerciseCategory = itemView.findViewById(R.id.exercise_category);
            exerciseDetails = itemView.findViewById(R.id.exercise_details);
        }
    }
}
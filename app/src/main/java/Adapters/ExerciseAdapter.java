package Adapters;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;


import com.example.richhealth.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import Models.Exercise;

public class ExerciseAdapter extends RecyclerView.Adapter<ExerciseAdapter.ExerciseViewHolder> implements Filterable {

    private List<Exercise> exerciseList;
    private List<Exercise> filteredList;
    private Context context;
    private OnExerciseClickListener listener;

    // Map for category colors
    private final Map<String, Integer> categoryColors = new HashMap<String, Integer>() {{
        put("Chest", Color.parseColor("#008b8b"));    // Orange
        put("Back", Color.parseColor("#2196F3"));     // Blue
        put("Legs", Color.parseColor("#4CAF50"));     // Green
        put("Shoulders", Color.parseColor("#9C27B0")); // Purple
        put("Arms", Color.parseColor("#F44336"));     // Red
        put("Core", Color.parseColor("#FFEB3B"));     // Yellow
    }};

    private final Map<String, Integer> categoryIcons = new HashMap<String, Integer>() {{
        put("Chest", R.drawable.ic_chest);
        put("Back", R.drawable.ic_back);
        put("Legs", R.drawable.ic_legs);
        put("Shoulders", R.drawable.ic_shoulders);
        put("Arms", R.drawable.ic_arms);
        put("Core", R.drawable.ic_core);
    }};

    public ExerciseAdapter(List<Exercise> exerciseList, Context context, OnExerciseClickListener listener) {
        this.exerciseList = exerciseList;
        this.filteredList = new ArrayList<>(exerciseList);
        this.context = context;
        this.listener = listener;
        organizeExercises();
    }

    @NonNull
    @Override
    public ExerciseViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_exercise, parent, false);
        return new ExerciseViewHolder(view, listener);
    }

    @Override
    public void onBindViewHolder(@NonNull ExerciseViewHolder holder, int position) {
        Exercise exercise = filteredList.get(position);
        holder.exerciseName.setText(exercise.getName());
        holder.exerciseDescription.setText(exercise.getDescription());
        holder.exerciseCategory.setText(exercise.getCategory());
        holder.exerciseCategory.setTextColor(Color.parseColor("#008b8b")); // Main orange

        // Set difficulty with orange shades
        String difficulty = exercise.getDifficulty();
        holder.exerciseDifficulty.setText(difficulty.substring(0, 1).toUpperCase() + difficulty.substring(1).toLowerCase());

        // Different shades of orange for difficulty levels
        switch(difficulty.toLowerCase()) {
            case "beginner":
                holder.exerciseDifficulty.setText("▮▯▯");
                break;
            case "intermediate":
                holder.exerciseDifficulty.setText("▮▮▯");
                break;
            case "advanced":
                holder.exerciseDifficulty.setText("▮▮▮");
                break;
            default:
                holder.exerciseDifficulty.setTextColor(Color.parseColor("#9E9E9E")); // Default gray
        }
    }

    @Override
    public int getItemCount() {
        return filteredList.size();
    }

    private void organizeExercises() {
        // Sort exercises by category
        Collections.sort(exerciseList, (e1, e2) -> e1.getCategory().compareTo(e2.getCategory()));
        filteredList = new ArrayList<>(exerciseList);
    }

    public static class ExerciseViewHolder extends RecyclerView.ViewHolder {
        TextView exerciseName, exerciseDescription, exerciseDifficulty, exerciseCategory;
//        ImageView exerciseCategory;

        public ExerciseViewHolder(@NonNull View itemView, OnExerciseClickListener listener) {
            super(itemView);
            exerciseName = itemView.findViewById(R.id.exercise_name);
            exerciseDescription = itemView.findViewById(R.id.exercise_description);
            exerciseCategory = itemView.findViewById(R.id.exercise_category);
            exerciseDifficulty = itemView.findViewById(R.id.exercise_difficulty);

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onExerciseClick(getAdapterPosition());
                }
            });
        }
    }

    public interface OnExerciseClickListener {
        void onExerciseClick(int position);
    }

    public void updateList(List<Exercise> newExercises) {
        this.exerciseList = newExercises;
        organizeExercises();
        notifyDataSetChanged();
    }

    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                List<Exercise> filtered = new ArrayList<>();

                if (constraint == null || constraint.length() == 0) {
                    filtered.addAll(exerciseList);
                } else {
                    String[] filters = constraint.toString().split("\n");
                    String searchQuery = filters[0].toLowerCase().trim();
                    String category = filters.length > 1 ? filters[1] : "All";

                    for (Exercise exercise : exerciseList) {
                        boolean matchesCategory = category.equals("All") ||
                                exercise.getCategory().equals(category);
                        boolean matchesSearch = exercise.getName().toLowerCase().contains(searchQuery);

                        if (matchesCategory && matchesSearch) {
                            filtered.add(exercise);
                        }
                    }
                }

                FilterResults results = new FilterResults();
                results.values = filtered;
                return results;
            }

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                filteredList = (List<Exercise>) results.values;
                notifyDataSetChanged();
            }
        };
    }
}
package Adapters;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;


import com.example.richhealth.R;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import Models.Exercise;

/**
 * Grouped, multi-view-type adapter for the exercise catalogue.
 *
 * The catalogue is rendered as a single vertical list, split into category sections
 * (Chest, Shoulders, Biceps, Triceps, Legs, Forearm Flexors & Grip) each introduced by
 * a header row. Internally we flatten the (filtered) exercise list into a list of
 * {@link Row}s — either a header row or an exercise row — and expose two RecyclerView
 * view types. Search / category filtering re-flattens the visible rows, so headers keep
 * making sense: filtering to one category shows just that section, and a text search shows
 * matches under their own category headers.
 *
 * The same adapter (and {@code item_exercise} layout) is reused by the Add Workout /
 * edit-workout exercise pickers so the picker matches the library UI.
 */
public class ExerciseAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> implements Filterable {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_EXERCISE = 1;

    // Fixed display order for the category sections; matches res/raw/exercises.json.
    private static final String[] CATEGORY_ORDER = {
            "Chest", "Shoulders", "Biceps", "Triceps", "Legs", "Forearm Flexors & Grip"
    };

    private List<Exercise> exerciseList;
    private List<Row> displayItems = new ArrayList<>();
    private final Context context;
    private final OnExerciseClickListener listener;

    /** A single visible row: either a category header or an exercise. */
    private static class Row {
        final int type;
        final String category;    // set when type == TYPE_HEADER
        final Exercise exercise;  // set when type == TYPE_EXERCISE

        private Row(int type, String category, Exercise exercise) {
            this.type = type;
            this.category = category;
            this.exercise = exercise;
        }

        static Row header(String category) {
            return new Row(TYPE_HEADER, category, null);
        }

        static Row exercise(Exercise exercise) {
            return new Row(TYPE_EXERCISE, null, exercise);
        }
    }

    public ExerciseAdapter(List<Exercise> exerciseList, Context context, OnExerciseClickListener listener) {
        this.exerciseList = exerciseList;
        this.context = context;
        this.listener = listener;
        buildDisplayItems(exerciseList);
    }

    /** Flattens the given exercises into grouped header + exercise rows. */
    private void buildDisplayItems(List<Exercise> exercises) {
        displayItems = new ArrayList<>();
        if (exercises == null || exercises.isEmpty()) {
            return;
        }

        // Preserve the fixed category order, then append any unknown categories at the end.
        LinkedHashMap<String, List<Exercise>> groups = new LinkedHashMap<>();
        for (String category : CATEGORY_ORDER) {
            groups.put(category, new ArrayList<>());
        }
        for (Exercise exercise : exercises) {
            String category = exercise.getCategory();
            List<Exercise> bucket = groups.get(category);
            if (bucket == null) {
                bucket = new ArrayList<>();
                groups.put(category, bucket);
            }
            bucket.add(exercise);
        }

        for (Map.Entry<String, List<Exercise>> entry : groups.entrySet()) {
            List<Exercise> bucket = entry.getValue();
            if (bucket.isEmpty()) {
                continue;
            }
            displayItems.add(Row.header(entry.getKey()));
            for (Exercise exercise : bucket) {
                displayItems.add(Row.exercise(exercise));
            }
        }
    }

    @Override
    public int getItemViewType(int position) {
        return displayItems.get(position).type;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(context);
        if (viewType == TYPE_HEADER) {
            View view = inflater.inflate(R.layout.item_exercise_header, parent, false);
            return new HeaderViewHolder(view);
        }
        View view = inflater.inflate(R.layout.item_exercise, parent, false);
        return new ExerciseViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = displayItems.get(position);
        if (row.type == TYPE_HEADER) {
            ((HeaderViewHolder) holder).headerTitle.setText(row.category);
            return;
        }

        final Exercise exercise = row.exercise;
        ExerciseViewHolder h = (ExerciseViewHolder) holder;
        h.exerciseName.setText(exercise.getName());
        h.exerciseDescription.setText(exercise.getDescription());
        h.exerciseCategory.setText(exercise.getCategory());
        h.exerciseCategory.setTextColor(Color.parseColor("#008b8b"));

        // Difficulty shown as a compact 3-bar indicator.
        String difficulty = exercise.getDifficulty();
        switch (difficulty.toLowerCase()) {
            case "beginner":
                h.exerciseDifficulty.setText("▮▯▯");
                break;
            case "intermediate":
                h.exerciseDifficulty.setText("▮▮▯");
                break;
            case "advanced":
                h.exerciseDifficulty.setText("▮▮▮");
                break;
            default:
                h.exerciseDifficulty.setText(difficulty);
                h.exerciseDifficulty.setTextColor(Color.parseColor("#9E9E9E"));
        }

        h.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onExerciseClick(exercise);
            }
        });
    }

    @Override
    public int getItemCount() {
        return displayItems.size();
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView headerTitle;

        HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            headerTitle = itemView.findViewById(R.id.exercise_header_title);
        }
    }

    static class ExerciseViewHolder extends RecyclerView.ViewHolder {
        TextView exerciseName, exerciseDescription, exerciseDifficulty, exerciseCategory;

        ExerciseViewHolder(@NonNull View itemView) {
            super(itemView);
            exerciseName = itemView.findViewById(R.id.exercise_name);
            exerciseDescription = itemView.findViewById(R.id.exercise_description);
            exerciseCategory = itemView.findViewById(R.id.exercise_category);
            exerciseDifficulty = itemView.findViewById(R.id.exercise_difficulty);
        }
    }

    public interface OnExerciseClickListener {
        void onExerciseClick(Exercise exercise);
    }

    public void updateList(List<Exercise> newExercises) {
        this.exerciseList = newExercises;
        buildDisplayItems(newExercises);
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
            @SuppressWarnings("unchecked")
            protected void publishResults(CharSequence constraint, FilterResults results) {
                buildDisplayItems((List<Exercise>) results.values);
                notifyDataSetChanged();
            }
        };
    }
}

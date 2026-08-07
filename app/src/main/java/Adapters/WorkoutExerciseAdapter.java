package Adapters;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.richhealth.R;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;
import java.util.Locale;

import Models.Exercise;
import Models.WorkoutExercise;
public class WorkoutExerciseAdapter extends RecyclerView.Adapter<WorkoutExerciseAdapter.ViewHolder> {
    private List<WorkoutExercise> exercises;
    private OnExerciseChangeListener listener;

    public interface OnExerciseChangeListener {
        void onExerciseChanged(List<WorkoutExercise> exercises);
    }

    public WorkoutExerciseAdapter(List<WorkoutExercise> exercises, OnExerciseChangeListener listener) {
        this.exercises = exercises;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_edit_exercise, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(exercises.get(position));
    }

    @Override
    public int getItemCount() {
        return exercises.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        TextView exerciseName;
        EditText setsInput, repsInput, weightInput;
        ImageButton duplicateBtn, deleteBtn;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            exerciseName = itemView.findViewById(R.id.exercise_name);
            setsInput = itemView.findViewById(R.id.sets_input);
            repsInput = itemView.findViewById(R.id.reps_input);
            weightInput = itemView.findViewById(R.id.weight_input);
            duplicateBtn = itemView.findViewById(R.id.duplicate_btn);
            deleteBtn = itemView.findViewById(R.id.delete_btn);

            TextWatcher watcher = new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override
                public void afterTextChanged(Editable s) {
                    int pos = getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        WorkoutExercise exercise = exercises.get(pos);
                        try {
                            if (!setsInput.getText().toString().isEmpty()) {
                                exercise.setSets(Integer.parseInt(setsInput.getText().toString()));
                            }
                            if (!repsInput.getText().toString().isEmpty()) {
                                exercise.setReps(Integer.parseInt(repsInput.getText().toString()));
                            }
                            if (!weightInput.getText().toString().isEmpty()) {
                                exercise.setWeight(Double.parseDouble(weightInput.getText().toString()));
                            }
                            if (listener != null) listener.onExerciseChanged(exercises);
                        } catch (NumberFormatException ignored) {}
                    }
                }
            };

            setsInput.addTextChangedListener(watcher);
            repsInput.addTextChangedListener(watcher);
            weightInput.addTextChangedListener(watcher);

            duplicateBtn.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    exercises.add(pos + 1, exercises.get(pos).clone());
                    notifyItemInserted(pos + 1);
                    if (listener != null) listener.onExerciseChanged(exercises);
                }
            });

            deleteBtn.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    exercises.remove(pos);
                    notifyItemRemoved(pos);
                    if (listener != null) listener.onExerciseChanged(exercises);
                }
            });
        }

        void bind(WorkoutExercise exercise) {
            exerciseName.setText(exercise.getExercise().getName());
            setsInput.setText(String.valueOf(exercise.getSets()));
            repsInput.setText(String.valueOf(exercise.getReps()));
            weightInput.setText(String.format(Locale.getDefault(), "%.1f", exercise.getWeight()));
        }
    }
}
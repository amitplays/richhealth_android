package com.example.richhealth.Activities;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LayoutAnimationController;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.richhealth.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import Adapters.ExerciseAdapter;
import Models.Exercise;
import Utils.Utilities;

//        // Anywhere in your app where you want to show the notification
//        if (getContext() != null) {
//            Utilities.showProgressCheckNotification(getContext());
//        }
public class ExercisesFragment extends Fragment {

    private RecyclerView exercisesRecyclerView;
    private EditText exerciseSearchBar;
    private TextView exercisesCount;
    private ExerciseAdapter exerciseAdapter;
    private List<Exercise> exerciseList = new ArrayList<>();
    private AutoCompleteTextView categoryFilter;
    private String currentCategory = "All";


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_exercises, container, false);
        initializeViews(view);
        loadExercises();
        setupRecyclerView();
        setupCategoryFilter();
        setupSearchBar();


        return view;

    }

    private void initializeViews(View view) {
        exercisesRecyclerView = view.findViewById(R.id.exercises_recycler_view);
        exerciseSearchBar = view.findViewById(R.id.exercise_search_bar);
        exercisesCount = view.findViewById(R.id.exercises_count);
        categoryFilter = view.findViewById(R.id.category_filter);

        TextView headerTitle = view.findViewById(R.id.header_title);

        // Animate header elements
        animateHeaderElements(headerTitle, exercisesCount);
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

    @Override
    public void onResume() {
        super.onResume();
        // Re-run animations
        View view = getView();
        if (view != null) {
            TextView headerTitle = view.findViewById(R.id.header_title);
            animateHeaderElements(headerTitle, exercisesCount);
            exercisesRecyclerView.scheduleLayoutAnimation();
        }
    }

    private void setupRecyclerView() {
        exercisesRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        // Add this: Setup layout animation
        LayoutAnimationController animation = AnimationUtils.loadLayoutAnimation(
                requireContext(), R.anim.layout_animation_slide_bottom);
        exercisesRecyclerView.setLayoutAnimation(animation);

        exerciseAdapter = new ExerciseAdapter(exerciseList, requireContext(), exercise ->
                showExerciseDetailsDialog(exercise));
        exercisesRecyclerView.setAdapter(exerciseAdapter);
        exercisesCount.setText(exerciseList.size() + " exercises");

        // Add this: Run initial animation
        exercisesRecyclerView.scheduleLayoutAnimation();
    }

    private void setupCategoryFilter() {
        Set<String> categories = new HashSet<>();
        categories.add("All");

        for(Exercise exercise : exerciseList) {
            categories.add(exercise.getCategory());
        }

        List<String> categoryList = new ArrayList<>(categories);
        Collections.sort(categoryList);

        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(
                requireContext(),
                R.layout.dropdown_item,
                categoryList
        );

        categoryFilter.setAdapter(categoryAdapter);
        categoryFilter.setText("All", false);
        categoryFilter.setOnItemClickListener((parent, view, position, id) -> {
            currentCategory = categoryList.get(position);
            filterExercises();
        });
    }

    private void setupSearchBar() {
        exerciseSearchBar.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterExercises();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void filterExercises() {
        String searchQuery = exerciseSearchBar.getText().toString().toLowerCase().trim();
        exerciseAdapter.getFilter().filter(searchQuery + "\n" + currentCategory);
        // Add this: Run animation when filter updates
        exercisesRecyclerView.scheduleLayoutAnimation();
    }

    // Modify ExerciseAdapter's Filter implementation:
    private Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                List<Exercise> filtered = new ArrayList<>();
                String searchStr = constraint == null ? "" : constraint.toString().toLowerCase();

                for (Exercise exercise : exerciseList) {
                    boolean matchesCategory = currentCategory.equals("All") ||
                            exercise.getCategory().equals(currentCategory);
                    boolean matchesSearch = exercise.getName().toLowerCase().contains(searchStr);

                    if (matchesCategory && matchesSearch) {
                        filtered.add(exercise);
                    }
                }

                FilterResults results = new FilterResults();
                results.values = filtered;
                return results;
            }

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
//                filteredList = (List<Exercise>) results.values;
//                notifyDataSetChanged();
            }
        };
    }


    private void loadExercises() {
        try {
            String json = loadJSONFromAssets();
            JSONObject jsonObject = new JSONObject(json);

            Iterator<String> keys = jsonObject.keys();
            while (keys.hasNext()) {
                String category = keys.next();
                JSONArray exercisesArray = jsonObject.getJSONArray(category);
                for (int i = 0; i < exercisesArray.length(); i++) {
                    JSONObject exerciseObject = exercisesArray.getJSONObject(i);
                    Exercise exercise = new Exercise(
                            exerciseObject.getInt("id"),
                            exerciseObject.getString("name"),
                            category,
                            exerciseObject.getDouble("met"),
                            exerciseObject.getString("equipment"),
                            exerciseObject.getString("difficulty"),
                            exerciseObject.getString("description")
                    );
                    exerciseList.add(exercise);
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void showExerciseDetailsDialog(Exercise exercise) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme);

        TextView customTitle = new TextView(requireContext());
        customTitle.setText(exercise.getName());
        customTitle.setTextSize(30); // Increase the text size
        customTitle.setPadding(45, 25, 16, 25); // Add padding
        customTitle.setGravity(Gravity.LEFT); // Center the title
        customTitle.setTextColor(ContextCompat.getColor(requireContext(), R.color.teal_200)); // Set text color

        builder.setCustomTitle(customTitle)
                .setMessage(exercise.getName())
                .setMessage("Category: " + exercise.getCategory() + "\n\n" +
                        "Difficulty: " + exercise.getDifficulty() + "\n\n" +
                        "Equipment: " + exercise.getEquipment() + "\n\n" +
                        "Description: " + exercise.getDescription() + "\n\n" +
                        String.format("MET Value: %.1f", exercise.getMet()))
                .setPositiveButton("Close", null)
                .show();
    }

    private String loadJSONFromAssets() {
        try {
            InputStream is = requireContext().getResources().openRawResource(R.raw.exercises);
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            is.close();
            return new String(buffer, StandardCharsets.UTF_8);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
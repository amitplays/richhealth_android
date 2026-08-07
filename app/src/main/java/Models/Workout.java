package Models;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Workout {
    private long id;
    private String name;
    private Date date;
    private List<WorkoutExercise> exercises;
    private long userId; // Added userId field

    public Workout() {
        this.exercises = new ArrayList<>();
        this.date = new Date(); // Current date/time
    }

    public Workout(long id, String name) {
        this.id = id;
        this.name = name;
        this.date = new Date();
        this.exercises = new ArrayList<>();
    }

    public Workout(long id, String name, Date date) {
        this.id = id;
        this.name = name;
        this.date = date;
        this.exercises = new ArrayList<>();
    }

    // Constructor with userId
    public Workout(long id, String name, Date date, long userId) {
        this.id = id;
        this.name = name;
        this.date = date;
        this.userId = userId;
        this.exercises = new ArrayList<>();
    }

    // Getters
    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Date getDate() {
        return date;
    }

    public List<WorkoutExercise> getExercises() {
        return exercises;
    }

    public long getUserId() {
        return userId;
    }

    // Setters
    public void setId(long id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public void setExercises(List<WorkoutExercise> exercises) {
        this.exercises = exercises;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    // Helper methods
    public void addExercise(WorkoutExercise exercise) {
        if (this.exercises == null) {
            this.exercises = new ArrayList<>();
        }
        this.exercises.add(exercise);
    }

    public void removeExercise(int position) {
        if (exercises != null && position >= 0 && position < exercises.size()) {
            exercises.remove(position);
        }
    }

    public int getExerciseCount() {
        return exercises != null ? exercises.size() : 0;
    }
}
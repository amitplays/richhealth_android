package Models;

public class WorkoutExercise {
    private long id;
    private Exercise exercise;
    private int sets;
    private int reps;
    private double weight;

    // Constructor with id
    public WorkoutExercise(long id, Exercise exercise, int sets, int reps, double weight) {
        this.id = id;
        this.exercise = exercise;
        this.sets = sets;
        this.reps = reps;
        this.weight = weight;
    }

    // Constructor without id (for new entries)
    public WorkoutExercise(Exercise exercise, int sets, int reps, double weight) {
        this.exercise = exercise;
        this.sets = sets;
        this.reps = reps;
        this.weight = weight;
    }

    // Getters
    public long getId() {
        return id;
    }

    public Exercise getExercise() {
        return exercise;
    }

    public int getSets() {
        return sets;
    }

    public int getReps() {
        return reps;
    }

    public double getWeight() {
        return weight;
    }

    // Setters
    public void setId(long id) {
        this.id = id;
    }

    public void setExercise(Exercise exercise) {
        this.exercise = exercise;
    }

    public void setSets(int sets) {
        this.sets = sets;
    }

    public void setReps(int reps) {
        this.reps = reps;
    }

    public void setWeight(double weight) {
        this.weight = weight;
    }

    // Clone method for duplicating exercises
    public WorkoutExercise clone() {
        return new WorkoutExercise(this.exercise, this.sets, this.reps, this.weight);
    }
}
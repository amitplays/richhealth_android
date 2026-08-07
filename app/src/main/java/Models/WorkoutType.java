package Models;

public class WorkoutType {
    private String title;
    private String description;
    private String exerciseCount;

    public WorkoutType(String title, String description, String exerciseCount) {
        this.title = title;
        this.description = description;
        this.exerciseCount = exerciseCount;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getExerciseCount() {
        return exerciseCount;
    }

    public void setExerciseCount(String exerciseCount) {
        this.exerciseCount = exerciseCount;
    }
}
package Models;

public class Exercise {
    private int id;
    private String name;
    private String category;
    private double met;
    private String equipment;
    private String difficulty;
    private String description;

    // Constructor
    public Exercise(int id, String name, String category, double met, String equipment, String difficulty, String description) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.met = met;
        this.equipment = equipment;
        this.difficulty = difficulty;
        this.description = description;
    }

    // Getters
    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public double getMet() {
        return met;
    }

    public String getEquipment() {
        return equipment;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public String getDescription() {
        return description;
    }




}

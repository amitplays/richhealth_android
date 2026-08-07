package Models;

public class Doctor {
    private String id;
    private String name;
    private String email;
    private String specialty;
    private String connectionStatus; // "connected", "pending", "none"

    // Default constructor
    public Doctor() {
    }

    // Constructor with parameters
    public Doctor(String id, String name, String email, String specialty, String connectionStatus) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.specialty = specialty;
        this.connectionStatus = connectionStatus;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getSpecialty() {
        return specialty;
    }

    public void setSpecialty(String specialty) {
        this.specialty = specialty;
    }

    public String getConnectionStatus() {
        return connectionStatus;
    }

    public void setConnectionStatus(String connectionStatus) {
        this.connectionStatus = connectionStatus;
    }
}
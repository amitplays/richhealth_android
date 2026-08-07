package Models;

/**
 * A single durable fact Richie has remembered about the user.
 * Mirrors the backend UserMemory document (id, fact, category).
 */
public class AiMemory {
    private String id;
    private String fact;
    private String category;

    public AiMemory(String id, String fact, String category) {
        this.id = id;
        this.fact = fact;
        this.category = category;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getFact() { return fact; }
    public void setFact(String fact) { this.fact = fact; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
}

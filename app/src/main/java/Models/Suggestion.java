package Models;
public class Suggestion {
    private long id;
    private String text;
    private String category;
    private int useCount;
    private boolean isFrequentlyUsed;

    public Suggestion(long id, String text, String category) {
        this.id = id;
        this.text = text;
        this.category = category;
        this.useCount = 0;
        this.isFrequentlyUsed = false;
    }

    public Suggestion(long id, String text, String category, int useCount, boolean isFrequentlyUsed) {
        this.id = id;
        this.text = text;
        this.category = category;
        this.useCount = useCount;
        this.isFrequentlyUsed = isFrequentlyUsed;
    }

    // Getters and setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public int getUseCount() { return useCount; }
    public void setUseCount(int useCount) {
        this.useCount = useCount;
        this.isFrequentlyUsed = useCount > 5; // Mark as frequent after 5 uses
    }

    public boolean isFrequentlyUsed() { return isFrequentlyUsed; }
    public void setFrequentlyUsed(boolean frequentlyUsed) { isFrequentlyUsed = frequentlyUsed; }

    public void incrementUseCount() {
        useCount++;
        isFrequentlyUsed = useCount > 5;
    }
}
package Models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Podcast implements Serializable {
    private long id;
    private String title;
    private String description;
    private String audioResourceName;
    private long duration; // in milliseconds
    private String category;
    private Date addedDate;
    private List<String> tags;  // Changed back to List<String>
    private int iconResourceId; // New field

    // New field for source links
    private List<String> sourceLinks;

    public Podcast() {
        this.addedDate = new Date();
        this.sourceLinks = new ArrayList<>();
    }

    public Podcast(long id, String title, String description, String audioResourceName,
                   long duration, String category) {
        this(id, title, description, audioResourceName, duration, category, 0);
    }

    // New constructor to include iconResourceId
    public Podcast(long id, String title, String description, String audioResourceName,
                   long duration, String category, int iconResourceId) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.audioResourceName = audioResourceName;
        this.duration = duration;
        this.category = category;
        this.addedDate = new Date();
        this.sourceLinks = new ArrayList<>();
        this.iconResourceId = iconResourceId;
    }

    // Existing getters and setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getAudioResourceName() { return audioResourceName; }
    public void setAudioResourceName(String audioResourceName) { this.audioResourceName = audioResourceName; }

    public long getDuration() { return duration; }
    public void setDuration(long duration) { this.duration = duration; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public Date getAddedDate() { return addedDate != null ? addedDate : new Date(); }
    public void setAddedDate(Date addedDate) { this.addedDate = addedDate; }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public int getIconResourceId() {
        return iconResourceId;
    }

    public void setIconResourceId(int iconResourceId) {
        this.iconResourceId = iconResourceId;
    }

    public List<String> getSourceLinks() {
        return sourceLinks;
    }

    public void setSourceLinks(List<String> sourceLinks) {
        this.sourceLinks = sourceLinks;
    }
}
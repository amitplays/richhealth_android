package Models;

import java.util.List;

/**
 * One card of the Daily Briefing carousel: a single prioritised action the AI is
 * asking the user to take. {@code priority} is one of urgent|high|medium|low and
 * {@code points} holds the one action sentence (kept as a list for render compat).
 */
public class BriefingCard {
    private final String title;
    private final List<String> points;
    private final String priority;

    public BriefingCard(String title, List<String> points) {
        this(title, points, "medium");
    }

    public BriefingCard(String title, List<String> points, String priority) {
        this.title = title;
        this.points = points;
        this.priority = (priority == null || priority.trim().isEmpty())
                ? "medium" : priority.trim().toLowerCase();
    }

    public String getTitle() { return title; }
    public List<String> getPoints() { return points; }
    public String getPriority() { return priority; }
}

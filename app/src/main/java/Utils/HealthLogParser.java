package Utils;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import Models.HealthCard;

/**
 * Builds {@link HealthCard}s from the {@code dataCards} array the backend returns
 * with a chat reply (the quick-log autofill feature). The backend already validates
 * and normalizes each card via its dedicated extraction pass; this is a thin,
 * defensive client-side mapper.
 */
public final class HealthLogParser {

    private static final String TAG = "HealthLogParser";

    private HealthLogParser() {}

    /** Map a backend {@code dataCards} JSON array into HealthCards. Null-safe. */
    public static List<HealthCard> cardsFromArray(JSONArray arr) {
        List<HealthCard> cards = new ArrayList<>();
        if (arr == null) return cards;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject c = arr.optJSONObject(i);
            if (c == null) continue;
            try {
                HealthCard card = fromJson(c);
                if (card != null) cards.add(card);
            } catch (Exception e) {
                Log.w(TAG, "Skipping malformed card: " + e.getMessage());
            }
        }
        return cards;
    }

    private static HealthCard fromJson(JSONObject c) {
        String kind = c.optString("kind", "").trim().toLowerCase();
        if (kind.isEmpty()) return null;

        HealthCard card = new HealthCard();
        switch (kind) {
            case HealthCard.KIND_MEASUREMENT:
                card.setKind(HealthCard.KIND_MEASUREMENT);
                card.setTitle(c.optString("title", ""));
                card.setValue(c.optString("value", ""));
                card.setUnit(c.optString("unit", ""));
                card.setDescription(c.optString("description", ""));
                card.setDateTime(c.optString("dateTime", ""));
                break;
            case HealthCard.KIND_MEDICATION:
                card.setKind(HealthCard.KIND_MEDICATION);
                card.setName(c.optString("name", ""));
                card.setDosage(c.optString("dosage", ""));
                card.setFrequency(normalizeFrequency(c.optString("frequency", "As needed")));
                card.setPurpose(c.optString("purpose", ""));
                card.setDateTime(c.optString("dateTime", ""));
                break;
            case HealthCard.KIND_PERIOD:
                card.setKind(HealthCard.KIND_PERIOD);
                card.setStartDate(c.optString("startDate", ""));
                card.setFlowIntensity(normalizeFlow(c.optString("flowIntensity", "medium")));
                card.setPainLevel(clamp(c.optInt("painLevel", 3)));
                card.setNotes(c.optString("notes", ""));
                break;
            case HealthCard.KIND_SYMPTOM:
                card.setKind(HealthCard.KIND_SYMPTOM);
                card.setTitle(c.optString("title", ""));
                card.setSeverity(clamp(c.optInt("severity", 3)));
                card.setDuration(c.optString("duration", ""));
                card.setDescription(c.optString("description", ""));
                card.setDateTime(c.optString("dateTime", ""));
                break;
            default:
                return null;
        }
        return card;
    }

    private static int clamp(int v) {
        if (v < 1) return 1;
        if (v > 5) return 5;
        return v;
    }

    /** Coerce whatever the model wrote to the PeriodLog enum: light | medium | heavy. */
    private static String normalizeFlow(String f) {
        if (f == null) return "medium";
        String s = f.trim().toLowerCase();
        if (s.contains("light") || s.contains("spot")) return "light";
        if (s.contains("heavy")) return "heavy";
        return "medium"; // medium / moderate / normal / anything else
    }

    /** Coerce the model's frequency to one of the Medication schema enum values. */
    private static String normalizeFrequency(String f) {
        if (f == null) return "As needed";
        String s = f.trim().toLowerCase();
        if (s.isEmpty()) return "As needed";
        if (s.contains("as needed") || s.contains("prn") || s.contains("when")) return "As needed";
        if (s.contains("four") || s.contains("4 times") || s.contains("qid")) return "Four times daily";
        if (s.contains("three") || s.contains("3 times") || s.contains("tid") || s.contains("thrice")) return "Three times daily";
        if (s.contains("twice") || s.contains("2 times") || s.contains("bid") || s.contains("two times")) return "Twice daily";
        if (s.contains("every 6")) return "Every 6 hours";
        if (s.contains("every 8")) return "Every 8 hours";
        if (s.contains("every 12")) return "Every 12 hours";
        if (s.contains("week")) return "Weekly";
        if (s.contains("month")) return "Monthly";
        if (s.contains("once") || s.contains("daily") || s.contains("every day") || s.contains("od") || s.contains("per day")) return "Once daily";
        return "As needed";
    }
}

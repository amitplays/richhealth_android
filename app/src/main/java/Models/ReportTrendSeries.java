package Models;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * One test = one chart. Parsed from GET /api/health/reports/trends.
 *
 * The server already did the grouping, unit conversion and de-duplication
 * (services/reportTrends.js), so nothing here computes anything — it only reads.
 * The version this replaces grouped keyFindings on the device and plotted mixed
 * units (mg/dL beside mmol/L) on one axis.
 *
 * Field names follow FHIR/HL7 so a real lab result has somewhere to go: a value
 * that is a word ("Non-reactive"), a censored value ("&lt;0.5"), a per-lab
 * reference band, and a two-component test (blood pressure).
 */
public class ReportTrendSeries {

    /** A single measurement. */
    public static class Point {
        public long t;                 // epoch millis — when the sample was taken
        public Double v;               // canonical value; null when the result is a word
        public String text = "";       // "Non-reactive", "O+"
        public String raw = "";        // exactly what the lab printed — the record
        public String status = "";     // normal|low|high|critical_low|critical_high|abnormal
        public Double refLow;          // the band THIS lab printed
        public Double refHigh;
        public String bound = "";      // "lt" / "gt" — outside what the analyser can measure
        public String reportId = "";

        public boolean isBounded() { return "lt".equals(bound) || "gt".equals(bound); }
        public String boundPrefix() { return "lt".equals(bound) ? "<" : "gt".equals(bound) ? ">" : ""; }
    }

    /** Almost always one. Blood pressure has two. */
    public static class Line {
        public String name = "";
        public final List<Point> points = new ArrayList<>();
    }

    public String key = "";
    public String label = "";
    public String unit = "";
    public String category = "Other";
    public String kind = "numeric";     // "numeric" | "categorical"
    public Double refLow;
    public Double refHigh;
    public Boolean higherIsBetter;      // null when neither direction is "better"
    public String note = "";
    public final List<Line> lines = new ArrayList<>();

    public boolean isCategorical() { return "categorical".equals(kind); }

    public int pointCount() {
        int n = 0;
        for (Line l : lines) n += l.points.size();
        return n;
    }

    /** Newest point across every line — what the card headlines. */
    public Point latestPoint() {
        Point best = null;
        for (Line l : lines) {
            if (l.points.isEmpty()) continue;
            Point p = l.points.get(l.points.size() - 1);   // server sends oldest first
            if (best == null || p.t > best.t) best = p;
        }
        return best;
    }

    public String latestText() {
        Point p = latestPoint();
        if (p == null) return "--";
        if (isCategorical()) return p.text == null || p.text.isEmpty() ? "--" : p.text;
        if (p.v == null) return "--";
        return p.boundPrefix() + format(p.v);
    }

    /**
     * Latest vs the one before it, on the first line that has two. Null when there
     * is nothing to compare against — one report is not a trend.
     */
    public String changeText() {
        if (isCategorical()) return null;
        for (Line l : lines) {
            if (l.points.size() < 2) continue;
            Double last = l.points.get(l.points.size() - 1).v;
            Double prev = l.points.get(l.points.size() - 2).v;
            if (last == null || prev == null || prev == 0) continue;
            double pct = (last - prev) / Math.abs(prev) * 100;
            if (Math.abs(pct) < 1) return "Steady";
            return (pct > 0 ? "+" : "") + Math.round(pct) + "%";
        }
        return null;
    }

    /**
     * Is the latest move a good one? Needs to know which direction is better —
     * higher HDL is good, higher LDL is not — so it returns null when we do not
     * know rather than guessing.
     */
    public Boolean changeIsGood() {
        if (higherIsBetter == null || isCategorical()) return null;
        for (Line l : lines) {
            if (l.points.size() < 2) continue;
            Double last = l.points.get(l.points.size() - 1).v;
            Double prev = l.points.get(l.points.size() - 2).v;
            if (last == null || prev == null || last.equals(prev)) continue;
            return (last > prev) == higherIsBetter;
        }
        return null;
    }

    public static String format(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(java.util.Locale.US, "%.1f", v);
    }

    // ── Parsing ──────────────────────────────────────────────────────────────

    public static List<ReportTrendSeries> fromJson(JSONArray arr) {
        List<ReportTrendSeries> out = new ArrayList<>();
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            ReportTrendSeries s = new ReportTrendSeries();
            s.key = o.optString("key", "");
            if (s.key.isEmpty()) continue;
            s.label = o.optString("label", s.key);
            s.unit = o.optString("unit", "");
            s.category = o.optString("category", "Other");
            s.kind = o.optString("kind", "numeric");
            s.refLow = optDouble(o, "refLow");
            s.refHigh = optDouble(o, "refHigh");
            // Boolean.valueOf, not the raw optBoolean: `cond ? null : primitiveBoolean`
            // makes Java unbox the null branch and throw at runtime.
            s.higherIsBetter = o.isNull("higherIsBetter")
                    ? null : Boolean.valueOf(o.optBoolean("higherIsBetter"));
            s.note = o.optString("note", "");

            JSONArray lines = o.optJSONArray("lines");
            for (int j = 0; lines != null && j < lines.length(); j++) {
                JSONObject lo = lines.optJSONObject(j);
                if (lo == null) continue;
                Line line = new Line();
                line.name = lo.optString("name", s.label);
                JSONArray pts = lo.optJSONArray("points");
                for (int k = 0; pts != null && k < pts.length(); k++) {
                    JSONObject po = pts.optJSONObject(k);
                    if (po == null) continue;
                    long t = parseDate(po.optString("t", ""));
                    if (t <= 0) continue;                       // undated → unplottable
                    Point p = new Point();
                    p.t = t;
                    p.v = optDouble(po, "v");
                    p.text = po.optString("text", "");
                    p.raw = po.optString("raw", "");
                    p.status = po.optString("status", "");
                    p.refLow = optDouble(po, "refLow");
                    p.refHigh = optDouble(po, "refHigh");
                    p.bound = po.optString("bound", "");
                    p.reportId = po.optString("reportId", "");
                    if (!s.isCategorical() && p.v == null) continue;
                    if (s.isCategorical() && p.text.isEmpty()) continue;
                    line.points.add(p);
                }
                if (!line.points.isEmpty()) s.lines.add(line);
            }
            if (!s.lines.isEmpty()) out.add(s);
        }
        return out;
    }

    private static Double optDouble(JSONObject o, String field) {
        if (o == null || !o.has(field) || o.isNull(field)) return null;
        double d = o.optDouble(field, Double.NaN);
        return Double.isNaN(d) ? null : d;
    }

    /**
     * The server sends ISO 8601 from Mongo, which may or may not carry
     * milliseconds. Both forms are tried, then a bare date, before giving up —
     * a point we cannot date is dropped rather than plotted at the epoch.
     */
    private static long parseDate(String iso) {
        if (iso == null || iso.isEmpty()) return 0;
        String[] patterns = {
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd",
        };
        for (String pattern : patterns) {
            try {
                java.text.SimpleDateFormat f = new java.text.SimpleDateFormat(pattern, java.util.Locale.US);
                if (pattern.endsWith("'Z'")) f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                return f.parse(iso).getTime();
            } catch (Exception ignored) { }
        }
        return 0;
    }
}

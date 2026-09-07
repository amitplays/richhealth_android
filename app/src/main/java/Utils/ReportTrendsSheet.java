package Utils;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.example.richhealth.R;
import com.example.richhealth.Activities.TokenManager;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import Models.ReportTrendSeries;

/**
 * Report trends — one chart per TEST, across every report the user has uploaded.
 *
 * A Lipid Panel and a Full Body Checkup can both contain LDL, and that is ONE
 * chart with points from both; the report type is irrelevant. All of the
 * grouping, unit conversion and de-duplication happened server-side
 * (services/reportTrends.js) and this only draws the answer.
 *
 * Replaces DialogUtils.showReportTrendChartDialog, which grouped keyFindings on
 * the device — so it plotted mg/dL beside mmol/L on one axis, and hid any test
 * until it had two values.
 */
public final class ReportTrendsSheet {

    private ReportTrendsSheet() {}

    private static final int ACCENT = Color.parseColor("#008B8B");
    private static final int ACCENT_2 = Color.parseColor("#E0A030");   // second line (diastolic)
    private static final int BAND = Color.parseColor("#33008B8B");
    private static final int MUTED = Color.parseColor("#8A8A8A");

    public static void show(final Activity activity) {
        if (activity == null || activity.isFinishing()) return;

        final BottomSheetDialog dialog = new BottomSheetDialog(activity, R.style.RH_Theme_BottomSheetDialog);
        View sheet = LayoutInflater.from(activity).inflate(R.layout.sheet_report_trends, null);
        dialog.setContentView(sheet);
        View container = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (container != null) container.setBackgroundColor(Color.TRANSPARENT);

        final ProgressBar progress = sheet.findViewById(R.id.trends_progress);
        final TextView empty = sheet.findViewById(R.id.trends_empty);
        final TextView subtitle = sheet.findViewById(R.id.trends_subtitle);
        final RecyclerView list = sheet.findViewById(R.id.trends_list);

        list.setLayoutManager(new LinearLayoutManager(activity, LinearLayoutManager.VERTICAL, false));


        fetch(activity, (series, reportCount, failed) -> {
            // Swiping the sheet away mid-request used to leave the callback driving
            // a dismissed dialog's behavior.
            if (activity.isFinishing() || !dialog.isShowing()) return;
            progress.setVisibility(View.GONE);
            if (series == null || series.isEmpty()) {
                empty.setText(failed
                        ? "Couldn't load trends. Check your connection and try again."
                        : "Upload a report with test results — a blood test, a checkup — and its values appear here.");
                empty.setVisibility(View.VISIBLE);
                subtitle.setVisibility(View.GONE);
                return;
            }
            subtitle.setText(series.size() + (series.size() == 1 ? " test from " : " tests from ")
                    + reportCount + (reportCount == 1 ? " report" : " reports"));
            list.setAdapter(new TrendAdapter(activity, series));
            list.setVisibility(View.VISIBLE);
            expandToFullHeight(dialog);
        });

        dialog.show();
    }

    /**
     * Raise the sheet to full height, once there is a list worth the room.
     *
     * Deliberately NOT called for the spinner, the error or the empty state — those
     * are a few words, and a full-screen sheet holding them reads as a broken
     * screen. Deliberately NOT called before show() either: BottomSheetDialog.onStart
     * sets STATE_COLLAPSED as the dialog is shown, so anything set earlier is
     * overwritten. The container's own height has to be raised too, or the expanded
     * state still stops at wrap_content.
     *
     * The existing LayoutParams object is MUTATED rather than replaced: it is a
     * CoordinatorLayout.LayoutParams carrying the bottom-sheet behavior, and handing
     * the view a plain ViewGroup.LayoutParams would make BottomSheetBehavior.from()
     * throw.
     */
    private static void expandToFullHeight(BottomSheetDialog dialog) {
        View c = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (c == null) return;
        ViewGroup.LayoutParams lp = c.getLayoutParams();
        if (lp != null) {
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            c.setLayoutParams(lp);
        }
        BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(c);
        behavior.setSkipCollapsed(true);
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    // ── Network ──────────────────────────────────────────────────────────────

    private interface Callback {
        void onResult(List<ReportTrendSeries> series, int reportCount, boolean failed);
    }

    private static void fetch(final Activity activity, final Callback cb) {
        final String token = TokenManager.getInstance(activity).getToken();
        if (token == null) { cb.onResult(null, 0, true); return; }

        final String url = ApiConfig.BASE_URL + "/api/health/reports/trends";
        StringRequest request = new StringRequest(Request.Method.GET, url,
                response -> {
                    ApiConfig.logRestCall(url, true, "Report trends fetched");
                    try {
                        JSONObject json = new JSONObject(response);
                        cb.onResult(ReportTrendSeries.fromJson(json.optJSONArray("series")),
                                json.optInt("reportCount", 0), false);
                    } catch (Exception e) {
                        cb.onResult(null, 0, true);
                    }
                },
                error -> {
                    ApiConfig.logRestCall(url, false, String.valueOf(error));
                    cb.onResult(null, 0, true);
                }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + token);
                return headers;
            }
        };
        Volley.newRequestQueue(activity).add(request);
    }

    // ── List ─────────────────────────────────────────────────────────────────

    private static class TrendAdapter extends RecyclerView.Adapter<TrendAdapter.Holder> {
        private final Activity activity;
        private final List<ReportTrendSeries> items;

        TrendAdapter(Activity activity, List<ReportTrendSeries> items) {
            this.activity = activity;
            this.items = items;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_report_trend_card, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            h.bind(activity, items.get(position));
        }

        @Override
        public int getItemCount() { return items.size(); }

        static class Holder extends RecyclerView.ViewHolder {
            private final TextView label, category, latest, change, footer;
            private final LineChart chart;
            private final LinearLayout textResults;

            Holder(@NonNull View v) {
                super(v);
                label = v.findViewById(R.id.card_label);
                category = v.findViewById(R.id.card_category);
                latest = v.findViewById(R.id.card_latest);
                change = v.findViewById(R.id.card_change);
                footer = v.findViewById(R.id.card_footer);
                chart = v.findViewById(R.id.card_chart);
                textResults = v.findViewById(R.id.card_text_results);
            }

            void bind(final Activity activity, final ReportTrendSeries s) {
                label.setText(s.label);
                // The server's catch-all bucket says nothing the label doesn't; it
                // printed a literal "Other" under most tests.
                if (isNoiseCategory(s.category)) {
                    category.setVisibility(View.GONE);
                } else {
                    category.setText(s.category);
                    category.setVisibility(View.VISIBLE);
                }

                String unitSuffix = s.unit.isEmpty() || s.isCategorical() ? "" : " " + s.unit;
                latest.setText(s.latestText() + unitSuffix);
                latest.setTextColor(statusColor(s.latestPoint()));

                String delta = s.changeText();
                if (delta == null) {
                    change.setVisibility(View.GONE);
                } else {
                    change.setText(delta);
                    Boolean good = s.changeIsGood();
                    change.setTextColor(good == null ? MUTED
                            : good ? Color.parseColor("#4CAF50") : Color.parseColor("#E0A030"));
                    change.setVisibility(View.VISIBLE);
                }

                StringBuilder f = new StringBuilder();
                f.append(s.pointCount()).append(s.pointCount() == 1 ? " result" : " results");
                if (s.refLow != null && s.refHigh != null) {
                    f.append(" · normal ").append(ReportTrendSeries.format(s.refLow))
                            .append("–").append(ReportTrendSeries.format(s.refHigh));
                }
                footer.setText(f.toString());

                // Words, not numbers: a line through "Non-reactive" means nothing.
                if (s.isCategorical()) {
                    chart.setVisibility(View.GONE);
                    textResults.setVisibility(View.VISIBLE);
                    fillTextResults(textResults, s, 3);
                } else {
                    textResults.setVisibility(View.GONE);
                    chart.setVisibility(View.VISIBLE);
                    renderChart(chart, s, true, false);
                }

                itemView.setOnClickListener(v -> showDetail(activity, s));
            }
        }
    }

    /** Blank, or the server's catch-all bucket, which says nothing the label doesn't. */
    private static boolean isNoiseCategory(String category) {
        if (category == null) return true;
        String c = category.trim();
        return c.isEmpty() || "other".equalsIgnoreCase(c);
    }

    // ── Chart ────────────────────────────────────────────────────────────────

    /**
     * Draws one series. The card and the enlarged dialog call this with the same
     * data, so tapping magnifies the same picture rather than showing a different
     * one.
     *
     * @param axes   draw the date and value axes. On now for cards too: an
     *               unlabelled chart is a decoration, and dates along the bottom
     *               are what let someone read a result off the card.
     * @param detail the enlarged dialog. Enables touch and names the reference
     *               lines — `axes` used to stand in for this, which stopped being
     *               true once cards gained axes.
     */
    private static void renderChart(LineChart chart, ReportTrendSeries s, boolean axes, boolean detail) {
        List<com.github.mikephil.charting.interfaces.datasets.ILineDataSet> sets = new ArrayList<>();
        // A single result is a dot, not a line — but still worth drawing: one value
        // against its reference band already answers "is this normal?".
        boolean singlePoint = s.pointCount() <= 1;

        for (int i = 0; i < s.lines.size(); i++) {
            ReportTrendSeries.Line line = s.lines.get(i);
            List<Entry> entries = new ArrayList<>();
            for (ReportTrendSeries.Point p : line.points) {
                if (p.v == null) continue;
                entries.add(new Entry((float) p.t, p.v.floatValue()));
            }
            if (entries.isEmpty()) continue;
            int color = i == 0 ? ACCENT : ACCENT_2;
            LineDataSet set = new LineDataSet(entries, line.name);
            set.setColor(color);
            set.setLineWidth(singlePoint ? 0f : 2.2f);
            set.setCircleColor(color);
            set.setCircleRadius(singlePoint ? 6f : 3.5f);
            set.setDrawCircleHole(false);
            set.setDrawValues(false);
            set.setMode(LineDataSet.Mode.LINEAR);
            set.setDrawFilled(false);
            sets.add(set);
        }

        chart.setData(new LineData(sets));

        chart.getDescription().setEnabled(false);
        chart.setNoDataText("");
        chart.setTouchEnabled(detail);
        chart.setScaleEnabled(false);
        chart.setDrawGridBackground(false);
        // Only blood pressure has two lines; a legend on a single-line chart is noise.
        chart.getLegend().setEnabled(s.lines.size() > 1);
        chart.getLegend().setTextColor(MUTED);

        XAxis x = chart.getXAxis();
        x.setPosition(XAxis.XAxisPosition.BOTTOM);
        x.setDrawGridLines(false);
        x.setTextColor(MUTED);
        x.setEnabled(axes);
        x.setLabelCount(3, false);
        x.setValueFormatter(new ValueFormatter() {
            private final java.text.SimpleDateFormat fmt =
                    new java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault());
            @Override public String getFormattedValue(float value) {
                return fmt.format(new java.util.Date((long) value));
            }
        });

        YAxis left = chart.getAxisLeft();
        left.setDrawGridLines(false);
        left.setTextColor(MUTED);
        left.setEnabled(axes);
        left.removeAllLimitLines();
        // The reference band as two lines. MPAndroidChart has no shaded band, and
        // two limit lines read more clearly on a small card than a filled area.
        if (s.refLow != null) left.addLimitLine(band(s.refLow.floatValue(), detail ? "Low" : ""));
        if (s.refHigh != null) left.addLimitLine(band(s.refHigh.floatValue(), detail ? "High" : ""));
        chart.getAxisRight().setEnabled(false);

        chart.invalidate();
    }

    private static LimitLine band(float value, String label) {
        LimitLine l = new LimitLine(value, label);
        l.setLineColor(BAND);
        l.setLineWidth(1f);
        l.enableDashedLine(6f, 4f, 0f);
        l.setTextColor(MUTED);
        l.setTextSize(9f);
        return l;
    }

    private static int statusColor(ReportTrendSeries.Point p) {
        if (p == null) return ACCENT;
        switch (p.status) {
            case "critical_low":
            case "critical_high": return Color.parseColor("#FF5252");
            case "low":
            case "high":
            case "abnormal":      return Color.parseColor("#E0A030");
            default:              return ACCENT;
        }
    }

    /** Newest first — a qualitative result has no trend, only a history. */
    private static void fillTextResults(LinearLayout container, ReportTrendSeries s, int limit) {
        container.removeAllViews();
        List<ReportTrendSeries.Point> all = new ArrayList<>();
        for (ReportTrendSeries.Line l : s.lines) all.addAll(l.points);
        all.sort((a, b) -> Long.compare(b.t, a.t));
        java.text.SimpleDateFormat fmt =
                new java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault());
        int shown = 0;
        for (ReportTrendSeries.Point p : all) {
            if (shown++ >= limit) break;
            LinearLayout row = new LinearLayout(container.getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            TextView value = new TextView(container.getContext());
            value.setText(p.text);
            value.setTextSize(13f);
            value.setTextColor(statusColor(p));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            value.setLayoutParams(lp);
            TextView date = new TextView(container.getContext());
            date.setText(fmt.format(new java.util.Date(p.t)));
            date.setTextSize(11f);
            date.setTextColor(MUTED);
            row.addView(value);
            row.addView(date);
            container.addView(row);
        }
    }

    // ── Enlarged ─────────────────────────────────────────────────────────────

    private static void showDetail(Activity activity, ReportTrendSeries s) {
        if (activity == null || activity.isFinishing()) return;
        final Dialog dialog = new Dialog(activity, android.R.style.Theme_Material_Dialog_NoActionBar);
        View v = LayoutInflater.from(activity).inflate(R.layout.dialog_report_trend_detail, null);
        dialog.setContentView(v);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            dialog.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(Color.parseColor("#141414")));
        }

        ((TextView) v.findViewById(R.id.detail_label)).setText(s.label);

        // bind() treats a null category as real, so new StringBuilder(s.category)
        // would NPE here the moment a category-less test was tapped. Same
        // "Other is noise" rule as the card, or the two views disagree.
        StringBuilder sub = new StringBuilder();
        if (!isNoiseCategory(s.category)) sub.append(s.category).append(" · ");
        sub.append(s.pointCount()).append(s.pointCount() == 1 ? " result" : " results");
        if (s.refLow != null && s.refHigh != null) {
            sub.append(" · normal ").append(ReportTrendSeries.format(s.refLow))
                    .append("–").append(ReportTrendSeries.format(s.refHigh));
            if (!s.unit.isEmpty()) sub.append(" ").append(s.unit);
        }
        ((TextView) v.findViewById(R.id.detail_subtitle)).setText(sub.toString());

        TextView note = v.findViewById(R.id.detail_note);
        if (s.note != null && !s.note.isEmpty()) {
            note.setText(s.note);
            note.setVisibility(View.VISIBLE);
        }

        LineChart chart = v.findViewById(R.id.detail_chart);
        if (s.isCategorical()) {
            chart.setVisibility(View.GONE);
        } else {
            renderChart(chart, s, true, true);
        }

        // Every value, exactly as the lab printed it. `raw` is the record — the
        // plotted number may have been unit-converted.
        LinearLayout rows = v.findViewById(R.id.detail_rows);
        rows.removeAllViews();
        List<ReportTrendSeries.Point> all = new ArrayList<>();
        for (ReportTrendSeries.Line l : s.lines) all.addAll(l.points);
        all.sort((a, b) -> Long.compare(b.t, a.t));
        java.text.SimpleDateFormat fmt =
                new java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault());
        for (ReportTrendSeries.Point p : all) {
            LinearLayout row = (LinearLayout) LayoutInflater.from(activity)
                    .inflate(R.layout.item_report_trend_value, rows, false);
            TextView value = row.findViewById(R.id.value_text);
            TextView range = row.findViewById(R.id.value_range);
            TextView date = row.findViewById(R.id.value_date);
            value.setText(displayValue(s, p));
            value.setTextColor(statusColor(p));
            if (p.refLow != null && p.refHigh != null) {
                range.setText("Normal " + ReportTrendSeries.format(p.refLow)
                        + "–" + ReportTrendSeries.format(p.refHigh));
                range.setVisibility(View.VISIBLE);
            } else {
                range.setVisibility(View.GONE);
            }
            date.setText(fmt.format(new java.util.Date(p.t)));
            rows.addView(row);
        }

        MaterialButton close = v.findViewById(R.id.detail_close);
        close.setOnClickListener(x -> dialog.dismiss());
        dialog.show();
    }

    /**
     * Prefer what the lab printed. Falls back to the plotted number with its bound
     * prefix, so "&lt;0.5" never reads as an exact 0.5.
     */
    private static String displayValue(ReportTrendSeries s, ReportTrendSeries.Point p) {
        if (p.raw != null && !p.raw.isEmpty()) {
            // `raw` is what the LAB printed, so it carries the LAB's unit — `unitRaw` whenever
            // the backend converted the value into the series' canonical unit. Pairing it with
            // s.unit printed a converted 5.40 mmol/L result as "5.40 mg/dL". latestText() and
            // the reference range above are canonical values, so they keep s.unit.
            String printed = (p.unitRaw != null && !p.unitRaw.isEmpty()) ? p.unitRaw : s.unit;
            return printed.isEmpty() ? p.raw : p.raw + " " + printed;
        }
        if (p.text != null && !p.text.isEmpty()) return p.text;
        if (p.v == null) return "--";
        String n = p.boundPrefix() + ReportTrendSeries.format(p.v);
        return s.unit.isEmpty() ? n : n + " " + s.unit;
    }
}

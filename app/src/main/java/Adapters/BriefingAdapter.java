package Adapters;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import Models.BriefingCard;
import com.example.richhealth.R;

/** Renders Daily-Briefing pages (title + up to 3 reasoned insights) for a ViewPager2. */
public class BriefingAdapter extends RecyclerView.Adapter<BriefingAdapter.VH> {

    private final Context context;
    private final List<BriefingCard> cards = new ArrayList<>();

    public BriefingAdapter(Context context) {
        this.context = context;
    }

    public void setCards(List<BriefingCard> newCards) {
        cards.clear();
        if (newCards != null) cards.addAll(newCards);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_briefing_card, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        BriefingCard card = cards.get(position);
        h.title.setText(card.getTitle());

        float d = context.getResources().getDisplayMetrics().density;
        bindPriority(h.priority, card.getPriority(), d);

        h.points.removeAllViews();
        List<String> pts = card.getPoints();
        if (pts != null) {
            for (String p : pts) {
                if (p == null || p.trim().isEmpty()) continue;
                h.points.addView(buildPointRow(p.trim(), d));
            }
        }
    }

    /**
     * Colour + label the priority pill using the app-wide semantic pill palette
     * (Utils.StatusPill) so it matches the Tools card pills exactly:
     * urgent→DANGER, high→WARNING, medium→INFO, low→NEUTRAL.
     */
    private void bindPriority(TextView pill, String priority, float d) {
        if (pill == null) return;
        String p = priority == null ? "medium" : priority.trim().toLowerCase();
        String label;
        Utils.StatusPill.Intent intent;
        switch (p) {
            case "urgent": label = "Urgent"; intent = Utils.StatusPill.Intent.DANGER;  break;
            case "high":   label = "High";   intent = Utils.StatusPill.Intent.WARNING; break;
            case "low":    label = "Low";    intent = Utils.StatusPill.Intent.NEUTRAL; break;
            case "medium":
            default:       label = "Medium"; intent = Utils.StatusPill.Intent.INFO;    break;
        }
        pill.setText(label);
        pill.setTextColor(Utils.StatusPill.foregroundColor(intent));
        pill.setBackgroundResource(Utils.StatusPill.drawableFor(intent));
    }

    private View buildPointRow(String text, float d) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.bottomMargin = (int) (7 * d);
        row.setLayoutParams(rowLp);

        View dot = new View(context);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(Color.parseColor("#008b8b"));
        dot.setBackground(dotBg);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams((int) (5 * d), (int) (5 * d));
        dotLp.topMargin = (int) (6 * d);
        dotLp.rightMargin = (int) (9 * d);
        dot.setLayoutParams(dotLp);
        row.addView(dot);

        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#CFD6D6"));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        tv.setLineSpacing(2 * d, 1f);
        tv.setMaxLines(4);
        tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tv.setGravity(Gravity.START);
        tv.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        row.addView(tv);
        return row;
    }

    @Override
    public int getItemCount() { return cards.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView priority;
        final LinearLayout points;
        VH(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.briefing_title);
            priority = itemView.findViewById(R.id.briefing_priority);
            points = itemView.findViewById(R.id.briefing_points);
        }
    }
}

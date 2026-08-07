package Utils;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.richhealth.R;

/**
 * Single source of truth for rendering a user's subscription plan as a badge.
 *
 * Tier values accepted: "free", "plus", "pro", "ultra", "family", "family_member".
 * Anything else (null, empty) is treated as "free".
 *
 * Two display styles:
 *   COMPACT — short tier name only ("Plus", "Pro", "Ultra", "Family", "Free"). Use in headers/inline.
 *   FULL    — full marketing name ("RichHealth Pro", "Family Member", etc.). Use in detail screens/dialogs.
 *
 * The badge always renders as a rounded pill with tier-tinted background, stroke, and text.
 */
public final class PlanBadge {

    public enum Style { COMPACT, FULL }

    private PlanBadge() {}

    /** Apply a tier-tinted pill style to {@code view} using {@link Style#COMPACT}. */
    public static void apply(@NonNull TextView view, @Nullable String tier) {
        apply(view, tier, Style.COMPACT, null);
    }

    /** Apply a tier-tinted pill style to {@code view}. */
    public static void apply(@NonNull TextView view, @Nullable String tier, @NonNull Style style) {
        apply(view, tier, style, null);
    }

    /**
     * Apply a tier-tinted pill style to {@code view}.
     *
     * @param suffix optional text appended after the plan label, e.g. " · Unlimited" or " · 5/10 used".
     */
    public static void apply(@NonNull TextView view, @Nullable String tier,
                             @NonNull Style style, @Nullable String suffix) {
        Context ctx = view.getContext();
        String key = normalize(tier);
        int color = colorFor(ctx, key);
        String label = labelFor(key, style);
        if (suffix != null && !suffix.isEmpty()) label = label + suffix;

        view.setText(label);
        view.setTextColor(color);
        view.setAllCaps(false);
        view.setTypeface(view.getTypeface(), Typeface.BOLD);

        // Pill background — tier-tinted fill + stroke, tinted programmatically.
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(ctx, 999));
        bg.setColor(withAlpha(color, 30));         // ~12% fill
        bg.setStroke(dp(ctx, 1), withAlpha(color, 70)); // ~27% stroke
        view.setBackground(bg);

        int padH = dp(ctx, 10);
        int padV = dp(ctx, 4);
        view.setPadding(padH, padV, padH, padV);
        view.setCompoundDrawablePadding(dp(ctx, 4));
        // Default text size if caller hasn't set one explicitly via XML
        if (view.getTextSize() <= 0) {
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        }
    }

    /** Tier accent color (full alpha), e.g. for icons or text outside the pill. */
    @ColorInt
    public static int colorFor(@NonNull Context ctx, @Nullable String tier) {
        switch (normalize(tier)) {
            case "plus":          return ContextCompat.getColor(ctx, R.color.plan_plus);
            case "pro":           return ContextCompat.getColor(ctx, R.color.plan_pro);
            case "ultra":         return ContextCompat.getColor(ctx, R.color.plan_ultra);
            case "family":        // owner — same gold as covered members
            case "family_member": return ContextCompat.getColor(ctx, R.color.plan_family);
            default:              return ContextCompat.getColor(ctx, R.color.plan_free);
        }
    }

    /** ColorStateList helper for icon tinting. */
    @NonNull
    public static ColorStateList tintFor(@NonNull Context ctx, @Nullable String tier) {
        return ColorStateList.valueOf(colorFor(ctx, tier));
    }

    /** Short tier label — used in COMPACT style and as a fallback. */
    @NonNull
    public static String compactLabelFor(@Nullable String tier) {
        return labelFor(normalize(tier), Style.COMPACT);
    }

    /** Full marketing label — used in FULL style. */
    @NonNull
    public static String fullLabelFor(@Nullable String tier) {
        return labelFor(normalize(tier), Style.FULL);
    }

    // ── internals ─────────────────────────────────────────────────────────

    private static String normalize(@Nullable String tier) {
        if (tier == null) return "free";
        String t = tier.trim().toLowerCase();
        return t.isEmpty() ? "free" : t;
    }

    private static String labelFor(String key, Style style) {
        if (style == Style.FULL) {
            switch (key) {
                case "plus":          return "RichHealth Plus";
                case "pro":           return "RichHealth Pro";
                case "ultra":         return "RichHealth Ultra";
                case "family":        return "RichHealth Family";
                case "family_member": return "Family Member";
                default:              return "Free Plan";
            }
        }
        switch (key) {
            case "plus":          return "Plus";
            case "pro":           return "Pro";
            case "ultra":         return "Ultra";
            case "family":        return "Family";
            case "family_member": return "Family Pro";
            default:              return "Free";
        }
    }

    @ColorInt
    private static int withAlpha(@ColorInt int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private static int dp(Context ctx, int v) {
        float d = ctx.getResources().getDisplayMetrics().density;
        return Math.round(v * d);
    }
}

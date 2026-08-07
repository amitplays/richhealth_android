package Utils;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

/**
 * Tiny circular usage indicator — Claude-style.
 *
 * Behaviour:
 *   • Background ring (subtle).
 *   • Foreground arc fills clockwise, starting at 12 o'clock.
 *   • Colour stages by usage:
 *       0–25 %  green
 *       25–50 % blue
 *       50–75 % orange
 *       75 % +  red
 *
 * Public API:
 *   setUsage(used, limit)      – sets values, repaints.
 *   getUsed() / getLimit()     – current values for click handlers / toasts.
 */
public class UsageRing extends View {

    // ---- colour palette -------------------------------------------------
    private static final int COLOR_GREEN  = Color.parseColor("#22C55E"); // 0–25%
    private static final int COLOR_BLUE   = Color.parseColor("#008b8b"); // 25–50%   (app teal — reads as "blue" in dark UI)
    private static final int COLOR_ORANGE = Color.parseColor("#F59E0B"); // 50–75%
    private static final int COLOR_RED    = Color.parseColor("#EF4444"); // 75–100%
    private static final int COLOR_TRACK  = Color.parseColor("#1F1F1F"); // background ring

    // ---- state ----------------------------------------------------------
    private int used = 0;
    private int limit = 0;
    // When true, the arc is drawn in a single accent color regardless of
    // fraction. Used for "good when full" meters like profile completeness,
    // where the consumption color-stages (green→red) would be misleading.
    private boolean singleColorMode = false;
    private int singleColor = COLOR_BLUE;

    // ---- paint ----------------------------------------------------------
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcRect    = new RectF();

    public UsageRing(Context context) { super(context); init(); }
    public UsageRing(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public UsageRing(Context context, AttributeSet attrs, int def) { super(context, attrs, def); init(); }

    private void init() {
        float strokePx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 2.5f, getResources().getDisplayMetrics());

        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        trackPaint.setStrokeWidth(strokePx);
        trackPaint.setColor(COLOR_TRACK);

        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);
        arcPaint.setStrokeWidth(strokePx);
    }

    public void setUsage(int used, int limit) {
        this.used = Math.max(0, used);
        this.limit = Math.max(0, limit);
        invalidate();
    }

    public int getUsed()  { return used; }
    public int getLimit() { return limit; }

    /**
     * Draw as a "good when full" progress ring (e.g. profile completeness):
     * fills to {@code percent} in a single accent color, no green→red staging.
     */
    public void setPercent(int percent) {
        this.singleColorMode = true;
        this.used = Math.max(0, Math.min(100, percent));
        this.limit = 100;
        invalidate();
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        // Default to 18dp if unconstrained — keeps it tiny like Claude's.
        int defaultSize = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 18f, getResources().getDisplayMetrics());
        int w = resolveSize(defaultSize, wSpec);
        int h = resolveSize(defaultSize, hSpec);
        int side = Math.min(w, h);
        setMeasuredDimension(side, side);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float stroke = arcPaint.getStrokeWidth();
        float pad = stroke / 2f + 1f;
        arcRect.set(pad, pad, getWidth() - pad, getHeight() - pad);

        // Background ring.
        canvas.drawArc(arcRect, 0f, 360f, false, trackPaint);

        if (limit <= 0) return;

        float fraction = Math.min(1f, (float) used / (float) limit);
        if (fraction <= 0f) return;

        // Color stages (consumption) — or a flat accent for "good when full".
        int color;
        if (singleColorMode) {
            color = singleColor;
        } else if (fraction < 0.25f) color = COLOR_GREEN;
        else if (fraction < 0.50f) color = COLOR_BLUE;
        else if (fraction < 0.75f) color = COLOR_ORANGE;
        else                       color = COLOR_RED;
        arcPaint.setColor(color);

        // Sweep clockwise from 12 o'clock.
        float sweep = fraction * 360f;
        canvas.drawArc(arcRect, -90f, sweep, false, arcPaint);
    }
}

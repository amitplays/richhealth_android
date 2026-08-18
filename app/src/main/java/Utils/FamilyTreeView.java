package Utils;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.richhealth.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Canvas-drawn family graph: one row per generation, the signed-in user in the
 * middle, org-chart style connectors between adjacent rows. Everything is drawn
 * in a single onDraw pass over precomputed rectangles, so it stays smooth while
 * panning and pinching.
 *
 * Layout is computed once per data/size change into {@link Placed} rectangles in
 * CONTENT space; pan and zoom are applied as a canvas transform at draw time, so
 * a gesture never triggers a re-layout.
 *
 * See {@link FamilyGraph} for why this is a generation graph and not a true tree
 * (the backend has no relative-to-relative links).
 */
public class FamilyTreeView extends View {

    /** Tapped-node callback. */
    public interface OnNodeTapListener {
        void onNodeTapped(FamilyGraph.Node node);
    }

    private static final float MIN_SCALE = 0.45f;
    private static final float MAX_SCALE = 2.4f;

    // ── Geometry (px, resolved in init) ────────────────────────────────────
    private float cardW, cardH, cardGapX, rowGap, rowLabelH, avatarR, corner, edgePad, density;

    // ── Paints ─────────────────────────────────────────────────────────────
    private final Paint cardFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cardStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selfStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint avatarFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint connector = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dashed = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint proDot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint initialsPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint namePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint relationPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint rowLabelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    private int accent, accentDim, surface, divider, textPrimary, textTertiary, warning;

    // ── State ──────────────────────────────────────────────────────────────
    private final List<FamilyGraph.Node> nodes = new ArrayList<FamilyGraph.Node>();
    private final List<Placed> placed = new ArrayList<Placed>();
    private final List<RowBand> bands = new ArrayList<RowBand>();
    private final Path connectorPath = new Path();

    private float contentW, contentH;
    private float scale = 1f, panX = 0f, panY = 0f;
    private boolean needsFit = true;

    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;
    private OnNodeTapListener tapListener;

    /** A node with its resolved rectangle in content space. */
    private static final class Placed {
        final FamilyGraph.Node node;
        final RectF rect = new RectF();
        Placed(FamilyGraph.Node n) { node = n; }
        float cx() { return rect.centerX(); }
    }

    /** One generation row: its label, its y band, and the nodes on it. */
    private static final class RowBand {
        int generation;
        String label;
        float labelBaseline;
        float top, bottom;
        final List<Placed> items = new ArrayList<Placed>();
    }

    public FamilyTreeView(Context context) { super(context); init(context); }
    public FamilyTreeView(Context context, @Nullable AttributeSet attrs) { super(context, attrs); init(context); }
    public FamilyTreeView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr); init(context);
    }

    private void init(Context ctx) {
        float d = ctx.getResources().getDisplayMetrics().density;
        density   = d;
        cardW     = 100 * d;
        cardH     = 104 * d;
        cardGapX  = 12 * d;
        rowGap    = 60 * d;
        rowLabelH = 24 * d;
        avatarR   = 21 * d;
        corner    = 16 * d;
        edgePad   = 16 * d;

        accent       = ContextCompat.getColor(ctx, R.color.rh_accent);
        accentDim    = ContextCompat.getColor(ctx, R.color.rh_accent_dim);
        surface      = ContextCompat.getColor(ctx, R.color.rh_surface_elevated);
        divider      = ContextCompat.getColor(ctx, R.color.rh_divider);
        textPrimary  = ContextCompat.getColor(ctx, R.color.rh_text_primary);
        textTertiary = ContextCompat.getColor(ctx, R.color.rh_text_tertiary);
        warning      = ContextCompat.getColor(ctx, R.color.rh_warning);

        cardFill.setStyle(Paint.Style.FILL);
        cardFill.setColor(surface);

        cardStroke.setStyle(Paint.Style.STROKE);
        cardStroke.setStrokeWidth(1 * d);
        cardStroke.setColor(divider);

        selfStroke.setStyle(Paint.Style.STROKE);
        selfStroke.setStrokeWidth(1.6f * d);
        selfStroke.setColor(accent);

        avatarFill.setStyle(Paint.Style.FILL);

        connector.setStyle(Paint.Style.STROKE);
        connector.setStrokeWidth(1.4f * d);
        connector.setStrokeCap(Paint.Cap.ROUND);
        connector.setStrokeJoin(Paint.Join.ROUND);
        connector.setColor(withAlpha(accent, 0x8A));

        dashed.setStyle(Paint.Style.STROKE);
        dashed.setStrokeWidth(1 * d);
        dashed.setColor(divider);
        dashed.setPathEffect(new android.graphics.DashPathEffect(new float[]{4 * d, 4 * d}, 0f));

        proDot.setStyle(Paint.Style.FILL);
        proDot.setColor(accent);

        Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);

        initialsPaint.setColor(Color.WHITE);
        initialsPaint.setTextSize(14 * d);
        initialsPaint.setTypeface(medium);
        initialsPaint.setTextAlign(Paint.Align.CENTER);

        namePaint.setColor(textPrimary);
        namePaint.setTextSize(12.5f * d);
        namePaint.setTypeface(medium);
        namePaint.setTextAlign(Paint.Align.CENTER);

        relationPaint.setColor(textTertiary);
        relationPaint.setTextSize(10.5f * d);
        relationPaint.setTextAlign(Paint.Align.CENTER);

        rowLabelPaint.setColor(textTertiary);
        rowLabelPaint.setTextSize(10.5f * d);
        rowLabelPaint.setTypeface(medium);
        rowLabelPaint.setLetterSpacing(0.08f);
        rowLabelPaint.setTextAlign(Paint.Align.CENTER);

        scaleDetector = new ScaleGestureDetector(ctx, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(@NonNull ScaleGestureDetector det) {
                float factor = det.getScaleFactor();
                float next = clamp(scale * factor, MIN_SCALE, MAX_SCALE);
                // Zoom about the pinch focus so the content under the fingers stays put.
                float fx = det.getFocusX(), fy = det.getFocusY();
                panX = fx - (fx - panX) * (next / scale);
                panY = fy - (fy - panY) * (next / scale);
                scale = next;
                clampPan();
                invalidate();
                return true;
            }
        });

        gestureDetector = new GestureDetector(ctx, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(@NonNull MotionEvent e) { return true; }

            @Override
            public boolean onScroll(MotionEvent e1, @NonNull MotionEvent e2, float dx, float dy) {
                panX -= dx;
                panY -= dy;
                clampPan();
                invalidate();
                return true;
            }

            @Override public boolean onSingleTapUp(@NonNull MotionEvent e) {
                Placed hit = hitTest(e.getX(), e.getY());
                if (hit != null) {
                    performClick();
                    if (tapListener != null) tapListener.onNodeTapped(hit.node);
                    return true;
                }
                return false;
            }

            @Override public boolean onDoubleTap(@NonNull MotionEvent e) {
                needsFit = true;      // double tap re-frames the whole family
                invalidate();
                return true;
            }
        });

        // Let the sheet's scroll container yield to our pan gestures.
        setClickable(true);
    }

    public void setOnNodeTapListener(OnNodeTapListener l) { tapListener = l; }

    /** Replace the graph and re-frame it. */
    public void setNodes(List<FamilyGraph.Node> newNodes) {
        nodes.clear();
        if (newNodes != null) nodes.addAll(newNodes);
        needsFit = true;
        layoutGraph();
        invalidate();
    }

    public boolean isEmpty() { return FamilyGraph.memberCount(nodes) == 0; }

    // ── Layout ─────────────────────────────────────────────────────────────

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        needsFit = true;
        layoutGraph();
    }

    /**
     * Places every node into content-space rectangles. Rows are centred on a
     * common axis so the signed-in user sits on the vertical centre line.
     */
    private void layoutGraph() {
        placed.clear();
        bands.clear();
        contentW = 0;
        contentH = 0;
        if (nodes.isEmpty()) return;

        // Bucket by generation, preserving the sort FamilyGraph already applied.
        List<List<FamilyGraph.Node>> buckets = new ArrayList<List<FamilyGraph.Node>>();
        for (int i = 0; i < FamilyGraph.ROW_ORDER.length; i++) {
            buckets.add(new ArrayList<FamilyGraph.Node>());
        }
        for (int i = 0; i < nodes.size(); i++) {
            FamilyGraph.Node n = nodes.get(i);
            int idx = rowIndex(n.generation);
            if (idx >= 0) buckets.get(idx).add(n);
        }

        // FamilyGraph sorts the user first in their row (order -1); move them to the
        // middle so they actually sit on the centre line the connectors converge on.
        for (int i = 0; i < buckets.size(); i++) {
            List<FamilyGraph.Node> row = buckets.get(i);
            int selfIdx = -1;
            for (int j = 0; j < row.size(); j++) {
                if (row.get(j).isSelf) { selfIdx = j; break; }
            }
            if (selfIdx >= 0) {
                int mid = row.size() / 2;
                FamilyGraph.Node self = row.remove(selfIdx);
                row.add(Math.min(mid, row.size()), self);
            }
        }

        // Widest row decides the content width; every row is centred inside it.
        float widest = 0;
        for (int i = 0; i < buckets.size(); i++) {
            int count = buckets.get(i).size();
            if (count == 0) continue;
            widest = Math.max(widest, count * cardW + (count - 1) * cardGapX);
        }
        contentW = widest;

        float y = 0;
        for (int i = 0; i < buckets.size(); i++) {
            List<FamilyGraph.Node> row = buckets.get(i);
            if (row.isEmpty()) continue;

            RowBand band = new RowBand();
            band.generation = FamilyGraph.ROW_ORDER[i];
            band.label = FamilyGraph.rowLabel(band.generation);
            band.labelBaseline = y + rowLabelH * 0.7f;

            float rowW = row.size() * cardW + (row.size() - 1) * cardGapX;
            float x = (contentW - rowW) / 2f;
            float top = y + rowLabelH;

            for (int j = 0; j < row.size(); j++) {
                Placed p = new Placed(row.get(j));
                p.rect.set(x, top, x + cardW, top + cardH);
                placed.add(p);
                band.items.add(p);
                x += cardW + cardGapX;
            }

            band.top = top;
            band.bottom = top + cardH;
            bands.add(band);

            y = band.bottom + rowGap;
        }

        contentH = bands.isEmpty() ? 0 : bands.get(bands.size() - 1).bottom;
        buildConnectors();

        if (needsFit) fitToView();
    }

    private static int rowIndex(int generation) {
        for (int i = 0; i < FamilyGraph.ROW_ORDER.length; i++) {
            if (FamilyGraph.ROW_ORDER[i] == generation) return i;
        }
        return -1;
    }

    /**
     * Org-chart connectors: a horizontal bus between each pair of adjacent
     * generation rows, with a short stub from every card to that bus. The
     * unplaced "Other family" row is deliberately NOT connected — those people
     * have no known generation, and a line would assert one.
     */
    private void buildConnectors() {
        connectorPath.reset();
        for (int i = 0; i + 1 < bands.size(); i++) {
            RowBand upper = bands.get(i);
            RowBand lower = bands.get(i + 1);
            if (upper.generation == FamilyGraph.GEN_UNPLACED
                    || lower.generation == FamilyGraph.GEN_UNPLACED) continue;

            float busY = (upper.bottom + lower.top) / 2f;
            float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;

            for (int j = 0; j < upper.items.size(); j++) {
                float cx = upper.items.get(j).cx();
                connectorPath.moveTo(cx, upper.bottom);
                connectorPath.lineTo(cx, busY);
                minX = Math.min(minX, cx); maxX = Math.max(maxX, cx);
            }
            for (int j = 0; j < lower.items.size(); j++) {
                float cx = lower.items.get(j).cx();
                connectorPath.moveTo(cx, busY);
                connectorPath.lineTo(cx, lower.top);
                minX = Math.min(minX, cx); maxX = Math.max(maxX, cx);
            }
            if (maxX > minX) {
                connectorPath.moveTo(minX, busY);
                connectorPath.lineTo(maxX, busY);
            }
        }
    }

    /** Scale and centre so the whole graph is visible on first show. */
    private void fitToView() {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0 || contentW <= 0 || contentH <= 0) return;

        float sx = (w - edgePad * 2) / contentW;
        float sy = (h - edgePad * 2) / contentH;
        scale = clamp(Math.min(sx, sy), MIN_SCALE, 1f);   // never zoom past 1:1 to fit
        panX = (w - contentW * scale) / 2f;
        panY = (h - contentH * scale) / 2f;
        if (panY < edgePad) panY = edgePad;
        needsFit = false;
    }

    /** Keep at least part of the graph on screen no matter how far the user drags. */
    private void clampPan() {
        int w = getWidth(), h = getHeight();
        float sw = contentW * scale, sh = contentH * scale;

        if (sw <= w) panX = (w - sw) / 2f;
        else panX = clamp(panX, w - sw - edgePad, edgePad);

        if (sh <= h) panY = (h - sh) / 2f;
        else panY = clamp(panY, h - sh - edgePad, edgePad);
    }

    // ── Drawing ────────────────────────────────────────────────────────────

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (placed.isEmpty()) return;
        if (needsFit) fitToView();

        canvas.save();
        canvas.translate(panX, panY);
        canvas.scale(scale, scale);

        canvas.drawPath(connectorPath, connector);

        for (int i = 0; i < bands.size(); i++) {
            RowBand band = bands.get(i);

            // The unplaced strip is fenced off with a dashed rule — it is a list,
            // not a generation, and should not read as one.
            if (band.generation == FamilyGraph.GEN_UNPLACED) {
                float ruleY = band.top - rowLabelH - rowGap / 2f;
                canvas.drawLine(0, ruleY, contentW, ruleY, dashed);
            }

            canvas.drawText(band.label.toUpperCase(java.util.Locale.US),
                    contentW / 2f, band.labelBaseline, rowLabelPaint);

            for (int j = 0; j < band.items.size(); j++) {
                drawCard(canvas, band.items.get(j));
            }
        }

        canvas.restore();
    }

    private void drawCard(Canvas canvas, Placed p) {
        FamilyGraph.Node n = p.node;
        RectF r = p.rect;

        canvas.drawRoundRect(r, corner, corner, cardFill);
        if (n.isSelf) {
            canvas.drawRoundRect(r, corner, corner, selfStroke);
        } else {
            canvas.drawRoundRect(r, corner, corner, cardStroke);
        }

        float d = density;
        float cx = r.centerX();
        float avatarCy = r.top + avatarR + 12 * d;

        // Accent gradient for the user, flat dim tint for everyone else. Pending
        // invites are washed out so they read as "not confirmed yet".
        if (n.isSelf) {
            avatarFill.setShader(new LinearGradient(
                    cx - avatarR, avatarCy - avatarR, cx + avatarR, avatarCy + avatarR,
                    accent, withAlpha(accent, 0xB0), Shader.TileMode.CLAMP));
        } else {
            avatarFill.setShader(null);
            avatarFill.setColor(n.isPending ? withAlpha(accentDim, 0x99) : accentDim);
        }
        canvas.drawCircle(cx, avatarCy, avatarR, avatarFill);
        avatarFill.setShader(null);

        initialsPaint.setColor(n.isSelf ? Color.WHITE : withAlpha(accent, 0xFF));
        initialsPaint.setAlpha(n.isPending ? 0x99 : 0xFF);
        float initialsBaseline = avatarCy - (initialsPaint.descent() + initialsPaint.ascent()) / 2f;
        canvas.drawText(n.initials(), cx, initialsBaseline, initialsPaint);

        // A small dot marks a Pro member; deceased entries get a muted marker.
        if (n.isPro || n.isDeceased) {
            proDot.setColor(n.isDeceased ? textTertiary : accent);
            canvas.drawCircle(cx + avatarR - 2 * d, avatarCy - avatarR + 4 * d, 3.5f * d, proDot);
        }

        float textW = r.width() - 12 * d;

        namePaint.setAlpha(n.isPending ? 0xAA : 0xFF);
        String name = TextUtils.ellipsize(n.displayName(), namePaint, textW,
                TextUtils.TruncateAt.END).toString();
        canvas.drawText(name, cx, avatarCy + avatarR + 20 * d, namePaint);

        String sub = n.isPending ? "Invite pending"
                : (n.relationship == null || n.relationship.isEmpty() ? "Family" : n.relationship);
        relationPaint.setColor(n.isPending ? warning : textTertiary);
        String rel = TextUtils.ellipsize(sub, relationPaint, textW,
                TextUtils.TruncateAt.END).toString();
        canvas.drawText(rel, cx, avatarCy + avatarR + 36 * d, relationPaint);
    }

    // ── Input ──────────────────────────────────────────────────────────────

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Stop the sheet's scroll view from stealing the drag mid-pan.
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN && getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }
        boolean handled = scaleDetector.onTouchEvent(event);
        handled = gestureDetector.onTouchEvent(event) || handled;
        return handled || super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() { return super.performClick(); }

    /** Screen point → node, or null when the tap missed every card. */
    @Nullable
    private Placed hitTest(float screenX, float screenY) {
        float x = (screenX - panX) / scale;
        float y = (screenY - panY) / scale;
        for (int i = 0; i < placed.size(); i++) {
            if (placed.get(i).rect.contains(x, y)) return placed.get(i);
        }
        return null;
    }

    // ── Small helpers ──────────────────────────────────────────────────────

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }
}

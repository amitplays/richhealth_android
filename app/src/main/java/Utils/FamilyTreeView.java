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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Canvas-drawn family graph — an egocentric "constellation". YOU sit at the centre and every
 * relative is on a spoke to you (the only relationship the backend stores). Distance from the
 * centre encodes degree of blood (1st-degree inner … 3rd outer); angle encodes generation
 * (up = older, down = younger) and side (paternal left, maternal right). Node styling: a single
 * teal hue stepped by degree, paternal teal-fill vs maternal inverted, double outline for your
 * direct line, single for siblings, married ring + dashed link for a spouse, muted for deceased,
 * dashed for pending, a star for Pro.
 *
 * Layout is computed once per data/size change into content-space circles; pan/zoom are a canvas
 * transform at draw time, so a gesture never re-lays-out. Twin of iOS FamilyTreeSheet.
 * See {@link FamilyGraph} for why this is a hub graph, not a genealogical tree.
 */
public class FamilyTreeView extends View {

    public interface OnNodeTapListener { void onNodeTapped(FamilyGraph.Node node); }

    private static final float MIN_SCALE = 0.45f;
    private static final float MAX_SCALE = 2.4f;

    // relationship "kind"
    private static final int K_DIRECT = 0, K_SIBLING = 1, K_COLLATERAL = 2, K_SPOUSE = 3, K_OTHER = 4;

    // ── Geometry ─────────────────────────────────────────────────────────────
    private float density, edgePad;

    // ── Paints ───────────────────────────────────────────────────────────────
    private final Paint nodeFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadeStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint spoke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pendingStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint marriedRing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selfStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint proDot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint proRing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint initialsPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint namePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint relationPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint sideCapPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    private int accent, surface, textPrimary, textTertiary, warning;

    // teal ramp stepped by degree (single hue — the only accent)
    private static final int DEG1 = 0xFF17B6B6;
    private static final int DEG2 = 0xFF0E8A8A;
    private static final int DEG3 = 0xFF0A5F5F;
    private static final int MAT_FILL = 0xFFE6F6F6;   // maternal inverted fill (light)
    private static final int DEC_FILL = 0x2A9AB0B0;   // deceased muted fill

    // ── State ────────────────────────────────────────────────────────────────
    private final List<FamilyGraph.Node> nodes = new ArrayList<FamilyGraph.Node>();
    private final List<Placed> placed = new ArrayList<Placed>();
    private float contentW, contentH, selfCx, selfCy;
    private float scale = 1f, panX = 0f, panY = 0f;
    private boolean needsFit = true;

    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;
    private OnNodeTapListener tapListener;

    /** A node with its resolved centre + radius (content space) and derived placement. */
    private static final class Placed {
        final FamilyGraph.Node node;
        float cx, cy, radius;
        int degree, side, kind;
        Placed(FamilyGraph.Node n) { node = n; }
    }

    public FamilyTreeView(Context c) { super(c); init(c); }
    public FamilyTreeView(Context c, @Nullable AttributeSet a) { super(c, a); init(c); }
    public FamilyTreeView(Context c, @Nullable AttributeSet a, int s) { super(c, a, s); init(c); }

    private void init(Context ctx) {
        density = ctx.getResources().getDisplayMetrics().density;
        edgePad = 16 * density;

        accent       = ContextCompat.getColor(ctx, R.color.rh_accent);
        surface      = ContextCompat.getColor(ctx, R.color.rh_surface_elevated);
        textPrimary  = ContextCompat.getColor(ctx, R.color.rh_text_primary);
        textTertiary = ContextCompat.getColor(ctx, R.color.rh_text_tertiary);
        warning      = ContextCompat.getColor(ctx, R.color.rh_warning);

        nodeFill.setStyle(Paint.Style.FILL);

        edgePaint.setStyle(Paint.Style.STROKE);        // "white in dark / dark in light" high-contrast
        edgePaint.setColor(textPrimary);

        shadeStroke.setStyle(Paint.Style.STROKE);
        shadeStroke.setStrokeWidth(1.3f * density);

        spoke.setStyle(Paint.Style.STROKE);
        spoke.setStrokeCap(Paint.Cap.ROUND);

        pendingStroke.setStyle(Paint.Style.STROKE);
        pendingStroke.setStrokeWidth(1.4f * density);
        pendingStroke.setColor(accent);
        pendingStroke.setPathEffect(new android.graphics.DashPathEffect(new float[]{4 * density, 4 * density}, 0f));

        marriedRing.setStyle(Paint.Style.STROKE);
        marriedRing.setStrokeWidth(1f * density);
        marriedRing.setColor(withAlpha(accent, 0x99));

        selfStroke.setStyle(Paint.Style.STROKE);
        selfStroke.setStrokeWidth(1.6f * density);
        selfStroke.setColor(withAlpha(Color.WHITE, 0xD9));

        proDot.setStyle(Paint.Style.FILL);
        proDot.setColor(accent);
        proRing.setStyle(Paint.Style.STROKE);
        proRing.setStrokeWidth(1f * density);
        proRing.setColor(withAlpha(Color.WHITE, 0xE6));

        Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        Typeface bold = Typeface.create("sans-serif", Typeface.BOLD);

        initialsPaint.setTextSize(13 * density);
        initialsPaint.setTypeface(bold);
        initialsPaint.setTextAlign(Paint.Align.CENTER);

        namePaint.setColor(textPrimary);
        namePaint.setTextSize(12.5f * density);
        namePaint.setTypeface(medium);
        namePaint.setTextAlign(Paint.Align.CENTER);

        relationPaint.setColor(textTertiary);
        relationPaint.setTextSize(10.5f * density);
        relationPaint.setTextAlign(Paint.Align.CENTER);

        sideCapPaint.setColor(textTertiary);
        sideCapPaint.setTextSize(10 * density);
        sideCapPaint.setTypeface(medium);
        sideCapPaint.setLetterSpacing(0.18f);
        sideCapPaint.setTextAlign(Paint.Align.CENTER);

        scaleDetector = new ScaleGestureDetector(ctx, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(@NonNull ScaleGestureDetector det) {
                float next = clamp(scale * det.getScaleFactor(), MIN_SCALE, MAX_SCALE);
                float fx = det.getFocusX(), fy = det.getFocusY();
                panX = fx - (fx - panX) * (next / scale);
                panY = fy - (fy - panY) * (next / scale);
                scale = next;
                clampPan(); invalidate();
                return true;
            }
        });
        gestureDetector = new GestureDetector(ctx, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(@NonNull MotionEvent e) { return true; }
            @Override public boolean onScroll(MotionEvent e1, @NonNull MotionEvent e2, float dx, float dy) {
                panX -= dx; panY -= dy; clampPan(); invalidate(); return true;
            }
            @Override public boolean onSingleTapUp(@NonNull MotionEvent e) {
                Placed hit = hitTest(e.getX(), e.getY());
                if (hit != null) { performClick(); if (tapListener != null) tapListener.onNodeTapped(hit.node); return true; }
                return false;
            }
            @Override public boolean onDoubleTap(@NonNull MotionEvent e) { needsFit = true; invalidate(); return true; }
        });
        setClickable(true);
    }

    public void setOnNodeTapListener(OnNodeTapListener l) { tapListener = l; }

    public void setNodes(List<FamilyGraph.Node> newNodes) {
        nodes.clear();
        if (newNodes != null) nodes.addAll(newNodes);
        needsFit = true;
        layoutGraph();
        invalidate();
    }

    public boolean isEmpty() { return FamilyGraph.memberCount(nodes) == 0; }

    // ── Classification (mirror of iOS classifyRelationship) ────────────────────

    /** {degree, gen, side, kind}. degree 0=spouse, 1..3, 99=other. side -1 pat / +1 mat / 0. */
    private static int[] classify(FamilyGraph.Node n) {
        if (n.isDeceased) return new int[]{99, 99, 0, K_OTHER};
        String k = n.relationship == null ? "" : n.relationship.trim().toLowerCase(Locale.US);
        if (k.contains("grandfather") || k.contains("grandmother") || (k.contains("grand") && k.contains("parent")))
            return new int[]{2, -2, 0, K_DIRECT};
        if (k.contains("grandson") || k.contains("granddaughter") || (k.contains("grand") && k.contains("child")))
            return new int[]{2, 2, 0, K_DIRECT};
        if (k.contains("father")) return new int[]{1, -1, -1, K_DIRECT};
        if (k.contains("mother")) return new int[]{1, -1, 1, K_DIRECT};
        if (k.equals("parent")) return new int[]{1, -1, 0, K_DIRECT};
        if (k.contains("uncle") || k.contains("aunt")) {
            int side = k.contains("paternal") ? -1 : (k.contains("maternal") ? 1 : 0);
            return new int[]{2, -1, side, K_COLLATERAL};
        }
        if (k.contains("son") || k.contains("daughter") || k.equals("child")) return new int[]{1, 1, 0, K_DIRECT};
        if (k.contains("brother") || k.contains("sister") || k.contains("sibling")) return new int[]{1, 0, 0, K_SIBLING};
        if (k.contains("spouse") || k.contains("husband") || k.contains("wife")) return new int[]{0, 0, 0, K_SPOUSE};
        if (k.contains("cousin")) return new int[]{3, 0, 0, K_COLLATERAL};
        if (k.contains("nephew") || k.contains("niece")) return new int[]{2, 1, 0, K_COLLATERAL};
        return new int[]{99, 99, 0, K_OTHER};
    }

    private static String bucketOf(int[] pl) {
        int gen = pl[1], side = pl[2], kind = pl[3];
        if (kind == K_SPOUSE) return "spouse";
        if (kind == K_SIBLING) return "sib";
        if (kind == K_OTHER) return "other";
        if (gen <= -2) return "gp";
        if (gen == -1) {
            if (kind == K_COLLATERAL) return side < 0 ? "auntPat" : (side > 0 ? "auntMat" : "auntMid");
            return side < 0 ? "parPat" : (side > 0 ? "parMat" : "parMid");
        }
        if (gen == 0) return "cousin";
        if (gen == 1) return kind == K_COLLATERAL ? "niece" : "child";
        return "gc";
    }

    private static float[] rangeOf(String key) {
        switch (key) {
            case "gp":      return new float[]{55, 125};
            case "parPat":  return new float[]{104, 134};
            case "parMat":  return new float[]{46, 76};
            case "parMid":  return new float[]{80, 100};
            case "auntPat": return new float[]{150, 172};
            case "auntMat": return new float[]{8, 30};
            case "auntMid": return new float[]{126, 150};
            case "sib":     return new float[]{163, 210};
            case "spouse":  return new float[]{352, 372};
            case "cousin":  return new float[]{300, 340};
            case "child":   return new float[]{246, 294};
            case "niece":   return new float[]{210, 236};
            case "gc":      return new float[]{256, 284};
            default:        return new float[]{188, 352};
        }
    }

    private float radiusForDegree(int degree) {
        float dp;
        switch (degree) { case 0: dp = 112; break; case 1: dp = 176; break;
            case 2: dp = 290; break; case 3: dp = 366; break; default: dp = 430; }
        return dp * density;
    }

    private static int shadeForDegree(int degree) {
        if (degree <= 1) return DEG1;
        if (degree == 2) return DEG2;
        return DEG3;
    }

    // ── Layout ─────────────────────────────────────────────────────────────────

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        needsFit = true;
        layoutGraph();
    }

    private void layoutGraph() {
        placed.clear();
        contentW = 0; contentH = 0;
        if (nodes.isEmpty()) return;

        FamilyGraph.Node self = null;
        Map<String, List<FamilyGraph.Node>> buckets = new HashMap<String, List<FamilyGraph.Node>>();
        for (int i = 0; i < nodes.size(); i++) {
            FamilyGraph.Node n = nodes.get(i);
            if (n.isSelf) { self = n; continue; }
            String b = bucketOf(classify(n));
            List<FamilyGraph.Node> list = buckets.get(b);
            if (list == null) { list = new ArrayList<FamilyGraph.Node>(); buckets.put(b, list); }
            list.add(n);
        }

        // temp positions in a centre-origin space
        List<Object[]> raw = new ArrayList<Object[]>();   // {node, px, py, pl}
        for (Map.Entry<String, List<FamilyGraph.Node>> e : buckets.entrySet()) {
            List<FamilyGraph.Node> list = e.getValue();
            Collections.sort(list, new Comparator<FamilyGraph.Node>() {
                @Override public int compare(FamilyGraph.Node a, FamilyGraph.Node b) {
                    if (a.isPending != b.isPending) return a.isPending ? 1 : -1;
                    return a.displayName().compareToIgnoreCase(b.displayName());
                }
            });
            float[] rng = rangeOf(e.getKey());
            int count = list.size();
            for (int i = 0; i < count; i++) {
                FamilyGraph.Node n = list.get(i);
                int[] pl = classify(n);
                double t = count == 1 ? 0.5 : (i + 0.5) / (double) count;
                double ang = (rng[0] + (rng[1] - rng[0]) * t) * Math.PI / 180.0;
                float r = radiusForDegree(pl[0]);
                float px = (float) (r * Math.cos(ang));
                float py = (float) (-r * Math.sin(ang));
                raw.add(new Object[]{n, px, py, pl});
            }
        }

        float nodeR = 24 * density, labelPad = 46 * density, sidePad = 62 * density;
        float minX = -nodeR - sidePad, maxX = nodeR + sidePad;
        float minY = -nodeR, maxY = nodeR + labelPad;
        for (int i = 0; i < raw.size(); i++) {
            float px = (Float) raw.get(i)[1], py = (Float) raw.get(i)[2];
            minX = Math.min(minX, px - nodeR - sidePad); maxX = Math.max(maxX, px + nodeR + sidePad);
            minY = Math.min(minY, py - nodeR);           maxY = Math.max(maxY, py + nodeR + labelPad);
        }
        float pad = 10 * density;
        selfCx = -minX + pad; selfCy = -minY + pad;
        contentW = (maxX - minX) + 2 * pad;
        contentH = (maxY - minY) + 2 * pad;

        if (self != null) {
            Placed ps = new Placed(self);
            ps.cx = selfCx; ps.cy = selfCy; ps.radius = 27 * density;
            ps.degree = 0; ps.side = 0; ps.kind = K_OTHER;
            placed.add(ps);
        }
        for (int i = 0; i < raw.size(); i++) {
            Object[] row = raw.get(i);
            FamilyGraph.Node n = (FamilyGraph.Node) row[0];
            int[] pl = (int[]) row[3];
            Placed p = new Placed(n);
            p.cx = selfCx + (Float) row[1]; p.cy = selfCy + (Float) row[2]; p.radius = 22 * density;
            p.degree = pl[0]; p.side = pl[2]; p.kind = pl[3];
            placed.add(p);
        }

        if (needsFit) fitToView();
    }

    private void fitToView() {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0 || contentW <= 0 || contentH <= 0) return;
        float sx = (w - edgePad * 2) / contentW;
        float sy = (h - edgePad * 2) / contentH;
        scale = clamp(Math.min(sx, sy), MIN_SCALE, 1f);
        panX = (w - contentW * scale) / 2f;
        panY = (h - contentH * scale) / 2f;
        needsFit = false;
    }

    private void clampPan() {
        int w = getWidth(), h = getHeight();
        float sw = contentW * scale, sh = contentH * scale;
        if (sw <= w) panX = (w - sw) / 2f; else panX = clamp(panX, w - sw - edgePad, edgePad);
        if (sh <= h) panY = (h - sh) / 2f; else panY = clamp(panY, h - sh - edgePad, edgePad);
    }

    // ── Drawing ─────────────────────────────────────────────────────────────────

    @Override protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (placed.isEmpty()) return;
        if (needsFit) fitToView();

        canvas.save();
        canvas.translate(panX, panY);
        canvas.scale(scale, scale);

        // spokes — each is a link to YOU
        for (int i = 0; i < placed.size(); i++) {
            Placed p = placed.get(i);
            if (p.node.isSelf) continue;
            drawSpoke(canvas, p);
        }

        // paternal / maternal captions
        canvas.drawText("PATERNAL", 62 * density, 16 * density, sideCapPaint);
        canvas.drawText("MATERNAL", contentW - 62 * density, 16 * density, sideCapPaint);

        for (int i = 0; i < placed.size(); i++) drawNode(canvas, placed.get(i));

        canvas.restore();
    }

    private void drawSpoke(Canvas canvas, Placed p) {
        float fromX = selfCx, fromY = selfCy, toX = p.cx, toY = p.cy;
        float midX = (fromX + toX) / 2f, midY = (fromY + toY) / 2f;
        float dx = toX - fromX, dy = toY - fromY;
        float len = Math.max(1f, (float) Math.hypot(dx, dy));
        float off = len * 0.10f;
        float ctrlX = midX + (-dy / len) * off, ctrlY = midY + (dx / len) * off;
        Path path = new Path();
        path.moveTo(fromX, fromY);
        path.quadTo(ctrlX, ctrlY, toX, toY);

        int base = p.node.isDeceased ? textTertiary : shadeForDegree(p.degree);
        int alpha = p.degree >= 3 ? 0x66 : (p.degree == 2 ? 0x8C : 0xB3);
        if (p.node.isDeceased) alpha = 0x80;
        spoke.setColor(withAlpha(base, alpha));
        spoke.setStrokeWidth((p.degree >= 3 ? 1.0f : (p.degree == 2 ? 1.4f : 1.8f)) * density);
        if (p.kind == K_SPOUSE) spoke.setPathEffect(new android.graphics.DashPathEffect(new float[]{1 * density, 5 * density}, 0f));
        else if (p.node.isPending) spoke.setPathEffect(new android.graphics.DashPathEffect(new float[]{3 * density, 5 * density}, 0f));
        else spoke.setPathEffect(null);
        canvas.drawPath(path, spoke);
    }

    private void drawNode(Canvas canvas, Placed p) {
        FamilyGraph.Node n = p.node;
        float cx = p.cx, cy = p.cy, R = p.radius, d = density;
        int shade = shadeForDegree(p.degree);
        boolean maternal = p.side > 0;

        // fill
        if (n.isSelf) {
            nodeFill.setShader(new LinearGradient(cx - R, cy - R, cx + R, cy + R,
                    0xFF4FE4E4, accent, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, R, nodeFill);
            nodeFill.setShader(null);
        } else if (n.isDeceased) {
            nodeFill.setColor(DEC_FILL); canvas.drawCircle(cx, cy, R, nodeFill);
        } else if (n.isPending) {
            // no fill — hollow dashed
        } else if (maternal) {
            nodeFill.setColor(MAT_FILL); canvas.drawCircle(cx, cy, R, nodeFill);
        } else {
            nodeFill.setColor(shade); canvas.drawCircle(cx, cy, R, nodeFill);
        }

        // outline by role
        if (n.isSelf) {
            canvas.drawCircle(cx, cy, R, selfStroke);
        } else if (n.isPending) {
            canvas.drawCircle(cx, cy, R, pendingStroke);
        } else if (p.kind == K_SPOUSE) {
            canvas.drawCircle(cx, cy, R + 3.5f * d, marriedRing);
        } else if (p.kind == K_DIRECT) {
            edgePaint.setStrokeWidth(1.8f * d); canvas.drawCircle(cx, cy, R, edgePaint);
            edgePaint.setStrokeWidth(1.4f * d); canvas.drawCircle(cx, cy, R + 3.5f * d, edgePaint);
        } else if (p.kind == K_SIBLING) {
            edgePaint.setStrokeWidth(2f * d); canvas.drawCircle(cx, cy, R, edgePaint);
        } else {
            shadeStroke.setColor(shade); canvas.drawCircle(cx, cy, R, shadeStroke);
        }

        // initials
        int ink = n.isSelf ? Color.WHITE
                : n.isDeceased ? textTertiary
                : n.isPending ? accent
                : maternal ? shade : Color.WHITE;
        initialsPaint.setColor(ink);
        String label = n.isSelf ? "YOU" : n.initials();
        initialsPaint.setTextSize((n.isSelf ? 11 : 13) * d);
        float ib = cy - (initialsPaint.descent() + initialsPaint.ascent()) / 2f;
        canvas.drawText(label, cx, ib, initialsPaint);

        // pro star / deceased mark
        if (n.isPro) {
            float bx = cx + R - 3 * d, by = cy - R + 3 * d;
            canvas.drawCircle(bx, by, 4.5f * d, proDot);
            canvas.drawCircle(bx, by, 4.5f * d, proRing);
        } else if (n.isDeceased) {
            proDot.setColor(textTertiary);
            canvas.drawCircle(cx + R - 3 * d, cy - R + 3 * d, 3f * d, proDot);
            proDot.setColor(accent);
        }

        if (n.isSelf) return;

        // name + relationship below
        float textW = 118 * d;
        namePaint.setAlpha(n.isPending ? 0xAA : 0xFF);
        String name = TextUtils.ellipsize(n.displayName(), namePaint, textW, TextUtils.TruncateAt.END).toString();
        canvas.drawText(name, cx, cy + R + 18 * d, namePaint);

        String sub = n.isPending ? "Invite pending"
                : (n.relationship == null || n.relationship.isEmpty() ? "Family" : n.relationship);
        relationPaint.setColor(n.isPending ? warning : textTertiary);
        String rel = TextUtils.ellipsize(sub, relationPaint, textW, TextUtils.TruncateAt.END).toString();
        canvas.drawText(rel, cx, cy + R + 33 * d, relationPaint);
    }

    // ── Input ────────────────────────────────────────────────────────────────

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN && getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }
        boolean handled = scaleDetector.onTouchEvent(event);
        handled = gestureDetector.onTouchEvent(event) || handled;
        return handled || super.onTouchEvent(event);
    }

    @Override public boolean performClick() { return super.performClick(); }

    @Nullable private Placed hitTest(float screenX, float screenY) {
        float x = (screenX - panX) / scale, y = (screenY - panY) / scale;
        for (int i = 0; i < placed.size(); i++) {
            Placed p = placed.get(i);
            float dx = x - p.cx, dy = y - p.cy;
            if (dx * dx + dy * dy <= (p.radius + 6 * density) * (p.radius + 6 * density)) return p;
        }
        return null;
    }

    private static float clamp(float v, float lo, float hi) { return v < lo ? lo : (v > hi ? hi : v); }
    private static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }
}

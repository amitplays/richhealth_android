package Utils;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.TextView;

import java.util.WeakHashMap;

/**
 * Lightweight skeleton-loading helper. No extra dependency.
 *
 * Three states a skeletonized view moves through:
 *   1. show(view)        — pill-shaped bar with a horizontal shimmer sweep.
 *   2. hide(view)        — data arrived; restore the view's original look.
 *   3. error(view, msg)  — request failed; show a compact red pill instead.
 *
 * Design rationale:
 *  - The shimmer uses a LinearGradient whose local matrix translates left→right
 *    once every 1200ms. One animator per view is cheap; they drift in/out of sync
 *    naturally, which reads as alive rather than mechanical.
 *  - Bar shape is a full pill (corner radius = height/2) so it never looks boxy
 *    regardless of text size.
 *  - Error pill is intentionally small and terse (no "tap to retry" verbiage);
 *    callers that want retry should make the parent card clickable.
 *
 * Usage:
 *   Skeleton.show(v1, v2, v3);
 *   // ...on success:
 *   Skeleton.hide(v1, v2, v3);
 *   v1.setText(result);
 *   // ...on failure:
 *   Skeleton.error(v1, "No connection");
 *   Skeleton.hideAndGone(v2, v3);
 */
public final class Skeleton {

    private static final String PLACEHOLDER = "\u2007\u2007\u2007\u2007\u2007\u2007\u2007\u2007";

    private static final int BASE_COLOR      = 0xFF2E2E2E;
    private static final int HIGHLIGHT_COLOR = 0xFF4F4F4F;

    // Error pill palette — dark red tint that reads on #1A1A1A cards.
    private static final int ERROR_BG_COLOR     = 0xFF3A1015;
    private static final int ERROR_STROKE_COLOR = 0xFF7A2A30;
    private static final int ERROR_TEXT_COLOR   = 0xFFFF6B6B;

    private static final long SHIMMER_DURATION_MS = 1200L;

    private static final WeakHashMap<View, State> saved = new WeakHashMap<>();

    private static class State {
        Drawable background;
        CharSequence originalText;
        int textColor;
        int paddingLeft, paddingTop, paddingRight, paddingBottom;
        boolean isTextView;
        boolean placeholderInjected;
        ShimmerDrawable shimmer;     // non-null while in skeleton state
    }

    private Skeleton() {}

    public static void show(View... views) {
        if (views == null) return;
        for (View v : views) apply(v);
    }

    public static void hide(View... views) {
        if (views == null) return;
        for (View v : views) restore(v, /*andGone=*/false);
    }

    /** Hide skeleton and make the view GONE — useful for error paths where
     *  supplementary fields should disappear instead of showing stale content. */
    public static void hideAndGone(View... views) {
        if (views == null) return;
        for (View v : views) restore(v, /*andGone=*/true);
    }

    /** Replace the skeleton with a compact red error pill. Use on the most
     *  prominent status view of a failed section; call hideAndGone() on the rest. */
    public static void error(TextView view, String message) {
        if (view == null) return;

        State existing = saved.remove(view);
        if (existing != null && existing.shimmer != null) existing.shimmer.stop();

        // Snapshot current state (pre-error) so a later show()/hide() can restore cleanly.
        State s = existing != null ? existing : captureState(view);
        s.shimmer = null;
        saved.put(view, s);

        float density = view.getResources().getDisplayMetrics().density;

        GradientDrawable pill = new GradientDrawable();
        pill.setColor(ERROR_BG_COLOR);
        pill.setCornerRadius(999f);
        pill.setStroke((int) (1 * density), ERROR_STROKE_COLOR);

        int hPad = (int) (10 * density);
        int vPad = (int) (3 * density);
        view.setPadding(hPad, vPad, hPad, vPad);
        view.setBackground(pill);
        view.setTextColor(ERROR_TEXT_COLOR);
        view.setText(message);
        view.setAlpha(1f);
        view.setVisibility(View.VISIBLE);
    }

    public static boolean isActive(View v) {
        return v != null && saved.containsKey(v);
    }

    // ─────────────────────────── internals ───────────────────────────

    private static void apply(View v) {
        if (v == null || saved.containsKey(v)) return;
        State s = captureState(v);

        if (s.isTextView) {
            TextView tv = (TextView) v;
            if (s.originalText == null || s.originalText.length() == 0) {
                tv.setText(PLACEHOLDER);
                s.placeholderInjected = true;
            }
            tv.setTextColor(0);  // fully transparent
        }

        ShimmerDrawable shimmer = new ShimmerDrawable();
        s.shimmer = shimmer;
        v.setBackground(shimmer);
        v.setAlpha(1f);
        v.setVisibility(View.VISIBLE);

        saved.put(v, s);
    }

    private static void restore(View v, boolean andGone) {
        if (v == null) return;
        State s = saved.remove(v);
        if (s == null) {
            if (andGone) v.setVisibility(View.GONE);
            return;
        }
        if (s.shimmer != null) s.shimmer.stop();
        v.setAlpha(1f);
        v.setBackground(s.background);
        v.setPadding(s.paddingLeft, s.paddingTop, s.paddingRight, s.paddingBottom);
        if (s.isTextView) {
            TextView tv = (TextView) v;
            tv.setTextColor(s.textColor);
            if (s.placeholderInjected && PLACEHOLDER.contentEquals(tv.getText())) {
                tv.setText(s.originalText == null ? "" : s.originalText);
            }
        }
        if (andGone) v.setVisibility(View.GONE);
    }

    private static State captureState(View v) {
        State s = new State();
        s.background      = v.getBackground();
        s.paddingLeft     = v.getPaddingLeft();
        s.paddingTop      = v.getPaddingTop();
        s.paddingRight    = v.getPaddingRight();
        s.paddingBottom   = v.getPaddingBottom();
        s.isTextView      = v instanceof TextView;
        if (s.isTextView) {
            TextView tv = (TextView) v;
            s.originalText = tv.getText();
            s.textColor    = tv.getCurrentTextColor();
        }
        return s;
    }

    // ───────────────────── ShimmerDrawable ─────────────────────
    //
    // Paints a pill-shaped base plus a lighter "highlight" band that sweeps
    // left→right by animating the LinearGradient's local matrix. The animator
    // is driven by invalidateSelf() every frame — cheap and self-contained.

    private static final class ShimmerDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Matrix matrix = new Matrix();
        private final RectF rect = new RectF();
        private LinearGradient gradient;
        private float offset = 0f;
        private float gradientWidth = 0f;
        private ValueAnimator animator;

        ShimmerDrawable() {
            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(SHIMMER_DURATION_MS);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setInterpolator(new LinearInterpolator());
            animator.addUpdateListener(a -> {
                offset = (float) a.getAnimatedValue();
                invalidateSelf();
            });
            animator.start();
        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            super.onBoundsChange(bounds);
            int w = bounds.width();
            if (w <= 0) return;
            gradientWidth = w * 0.4f;
            gradient = new LinearGradient(
                    0f, 0f, gradientWidth, 0f,
                    new int[]{BASE_COLOR, HIGHLIGHT_COLOR, BASE_COLOR},
                    new float[]{0f, 0.5f, 1f},
                    Shader.TileMode.CLAMP);
            paint.setShader(gradient);
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            if (b.width() <= 0 || b.height() <= 0) return;
            if (gradient == null) onBoundsChange(b);

            // Translate the gradient so its highlight band sweeps across the bar.
            matrix.setTranslate(offset * (b.width() + gradientWidth) - gradientWidth, 0f);
            gradient.setLocalMatrix(matrix);

            rect.set(b);
            float cr = b.height() * 0.5f;  // full pill
            canvas.drawRoundRect(rect, cr, cr, paint);
        }

        void stop() {
            if (animator != null) {
                animator.cancel();
                animator = null;
            }
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}

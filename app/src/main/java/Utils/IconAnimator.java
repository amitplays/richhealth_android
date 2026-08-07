package Utils;

import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import com.example.richhealth.R;

/**
 * Finds every View tagged "section_icon" inside a root and plays
 * a subtle scale + fade pulse with a staggered delay per icon.
 */
public final class IconAnimator {

    private static final long STAGGER_MS = 60;

    private IconAnimator() {}

    public static void animateSectionIcons(View root) {
        if (root == null) return;
        // Wait until the view tree is laid out so alpha/animation changes are visible
        root.post(() -> animateRecursive(root, new int[]{0}));
    }

    private static void animateRecursive(View view, int[] index) {
        if ("section_icon".equals(view.getTag())) {
            long delay = index[0] * STAGGER_MS;
            view.setAlpha(0f);
            view.postDelayed(() -> {
                view.setAlpha(1f);
                view.startAnimation(
                        AnimationUtils.loadAnimation(view.getContext(), R.anim.icon_subtle_pulse));
            }, delay);
            index[0]++;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                animateRecursive(group.getChildAt(i), index);
            }
        }
    }
}

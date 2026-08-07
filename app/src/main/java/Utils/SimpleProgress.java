package Utils;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import com.example.richhealth.R;

public class SimpleProgress {

    private static SimpleProgress instance;

    private View progressView;
    private TextView progressText;
    private ImageView progressLogo;
    private ViewGroup rootView;
    private ObjectAnimator spinningAnimator;

    private static SimpleProgress inflate(ViewGroup root, android.content.Context context, String message) {
        // Hide any existing progress first to avoid orphaned views
        hide();

        SimpleProgress progress = new SimpleProgress();
        instance = progress;
        progress.rootView = root;

        LayoutInflater inflater = LayoutInflater.from(context);
        progress.progressView = inflater.inflate(R.layout.layout_simple_progress, progress.rootView, false);
        progress.progressText = progress.progressView.findViewById(R.id.progress_text);
        progress.progressLogo = progress.progressView.findViewById(R.id.progress_logo);

        progress.progressText.setText(message);
        progress.rootView.addView(progress.progressView);
        progress.progressView.setVisibility(View.VISIBLE);
        progress.progressView.bringToFront();
        progress.startLogoSpin();
        return progress;
    }

    public static SimpleProgress show(Activity activity, String message) {
        ViewGroup root = (ViewGroup) activity.findViewById(android.R.id.content);
        return inflate(root, activity, message);
    }

    public static SimpleProgress show(Dialog dialog, String message) {
        ViewGroup root = (ViewGroup) dialog.getWindow().getDecorView();
        return inflate(root, dialog.getContext(), message);
    }

    public static SimpleProgress show(View fragmentView, String message) {
        View rootContainer = fragmentView.getRootView();
        if (rootContainer instanceof ViewGroup) {
            ViewGroup root = (ViewGroup) rootContainer.findViewById(android.R.id.content);
            return inflate(root, fragmentView.getContext(), message);
        }
        return new SimpleProgress();
    }

    // Start the beautiful spinning animation (same as LoginActivity)
    private void startLogoSpin() {
        if (progressLogo != null) {
            spinningAnimator = ObjectAnimator.ofFloat(progressLogo, View.ROTATION, 0f, 360f);
            spinningAnimator.setDuration(2000); // Slightly faster than login (3000ms)
            spinningAnimator.setRepeatCount(ObjectAnimator.INFINITE);
            spinningAnimator.setInterpolator(new DecelerateInterpolator());
            spinningAnimator.start();
        }
    }

    // Stop the spinning animation
    private void stopLogoSpin() {
        if (spinningAnimator != null && spinningAnimator.isRunning()) {
            spinningAnimator.cancel();
            if (progressLogo != null) {
                progressLogo.setRotation(0); // Reset rotation
            }
        }
    }

    // Update message
    public void setMessage(String message) {
        if (progressText != null) {
            progressText.setText(message);
        }
    }

    // Hide and remove (like native ProgressBar.dismiss())
    public static void hide() {
        if (instance != null) {
            instance.stopLogoSpin(); // Stop animation before hiding
            if (instance.progressView != null && instance.rootView != null) {
                instance.rootView.removeView(instance.progressView);
                instance.progressView = null;
            }
            instance = null;
        }
    }
}

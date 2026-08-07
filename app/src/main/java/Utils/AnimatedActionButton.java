package Utils;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.richhealth.R;
import com.google.android.material.button.MaterialButton;

public class AnimatedActionButton extends LinearLayout {
    private static final String TAG = "AnimatedActionButton";
    private MaterialButton textButton;
    private ImageView iconView;
    private boolean isExpanded = true;
    private int iconResId;
    private String buttonText;
    private OnClickListener clickListener;

    public AnimatedActionButton(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initView(context, attrs);
    }

    private void initView(Context context, AttributeSet attrs) {
        // Set up layout
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER);  // Change from CENTER_VERTICAL to CENTER
        setBackground(ContextCompat.getDrawable(context, R.drawable.button_outline_background));
        setPadding(10, 10, 10, 10);

        // Extract custom attributes
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.AnimatedActionButton);
        iconResId = a.getResourceId(R.styleable.AnimatedActionButton_actionIcon, R.drawable.ic_attach_file);
        buttonText = a.getString(R.styleable.AnimatedActionButton_buttonText);
        a.recycle();

        // Create text button
        textButton = new MaterialButton(context);
        textButton.setText(buttonText);
        textButton.setTextColor(ContextCompat.getColor(context, R.color.teal_200));
        textButton.setBackgroundColor(Color.TRANSPARENT);
        textButton.setTextSize(12);
        textButton.setPadding(5,5,5,5);

        // Create icon
        iconView = new ImageView(context);
        iconView.setImageResource(iconResId);
        iconView.setColorFilter(ContextCompat.getColor(context, R.color.teal_200));

        // Use LayoutParams to control positioning
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        iconParams.gravity = Gravity.CENTER;  // Center the icon vertically and horizontally
        iconView.setLayoutParams(iconParams);
        iconView.setPadding(0, 0, 0, 0);
        iconView.setVisibility(View.GONE);

        // Add to layout
        addView(textButton);
        addView(iconView);

        // Log creation
        Log.d(TAG, "Button initialized: " + buttonText);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        Log.d(TAG, "Touch event detected: " + event.getAction());

        if (event.getAction() == MotionEvent.ACTION_UP) {
            // Ensure we're not in a scrolling context
            performClick();
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        Log.d(TAG, "Perform click triggered");

        // Toggle state
        if (isExpanded) {
            collapseToIcon();
        } else {
            expandToButton();
        }
        isExpanded = !isExpanded;

        // Trigger custom click listener if set
        if (clickListener != null) {
            clickListener.onClick(this);
        }

        return super.performClick();
    }

    public void collapseToIcon() {
        Log.d(TAG, "Collapsing to icon");

        // Animate width
        final int startWidth = getWidth();
        ValueAnimator widthAnimator = ValueAnimator.ofInt(startWidth, 150);
        widthAnimator.addUpdateListener(valueAnimator -> {
            ViewGroup.LayoutParams params = getLayoutParams();
            params.width = (int) valueAnimator.getAnimatedValue();
            setLayoutParams(params);
        });

        // Fade out text, fade in icon
        textButton.animate()
                .alpha(0f)
                .setDuration(1300)  // Longer duration for text fade
                .withEndAction(() -> {
                    textButton.setVisibility(View.GONE);

                    // Prepare icon
                    iconView.setVisibility(View.VISIBLE);
                    iconView.setAlpha(0f);

                    // Fade in icon
                    iconView.animate()
                            .alpha(1f)
                            .setDuration(900)
                            .start();
                });

        widthAnimator.setInterpolator(new DecelerateInterpolator());
        widthAnimator.setDuration(1300);
        widthAnimator.start();
    }

    private void expandToButton() {
        Log.d(TAG, "Expanding to button");

        // Animate width back to wrap content
        final int startWidth = getWidth();
        ValueAnimator widthAnimator = ValueAnimator.ofInt(startWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        widthAnimator.addUpdateListener(valueAnimator -> {
            ViewGroup.LayoutParams params = getLayoutParams();
            params.width = (int) valueAnimator.getAnimatedValue();
            setLayoutParams(params);
        });

        // Fade out icon, fade in text
        iconView.animate().alpha(0f).setDuration(300).withEndAction(() -> {
            iconView.setVisibility(View.GONE);
            textButton.setVisibility(View.VISIBLE);
            textButton.setAlpha(0f);
            textButton.animate().alpha(1f).setDuration(300).start();
        });

        widthAnimator.setInterpolator(new DecelerateInterpolator());
        widthAnimator.setDuration(300);
        widthAnimator.start();
    }

    public void collapseImmediately() {
        // Directly set to icon state without animation
        textButton.setVisibility(View.GONE);
        iconView.setVisibility(View.VISIBLE);

        // Optional: Set layout params to icon width
        ViewGroup.LayoutParams params = getLayoutParams();
        params.width = 100; // or whatever icon width you want
        setLayoutParams(params);

        isExpanded = false;
    }

    public void expandImmediately() {
        // Directly set to text state without animation
        iconView.setVisibility(View.GONE);
        textButton.setVisibility(View.VISIBLE);

        // Reset layout params
        ViewGroup.LayoutParams params = getLayoutParams();
        params.width = ViewGroup.LayoutParams.WRAP_CONTENT;
        setLayoutParams(params);

        isExpanded = true;
    }
}
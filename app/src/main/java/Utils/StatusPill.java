package Utils;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.example.richhealth.R;
import com.google.android.material.chip.Chip;

/**
 * Single source of truth for status-pill semantics.
 *
 * Used by code that mutates a pill at runtime (e.g. health analysis state, AQI level,
 * check-in status, connection state). Keeps text/colors consistent so callers don't
 * each invent their own setBackgroundTintList / setTextColor combination.
 *
 * For static pills declared in XML, use the styles directly:
 *   - TextView pills: style="@style/Widget.RH.StatusPill" + android:background="@drawable/pill_status_*"
 *   - Material Chip pills: style="@style/Widget.RH.StatusChip" + app:chipBackgroundColor=...
 */
public final class StatusPill {

    public enum Intent { SUCCESS, WARNING, DANGER, INFO, NEUTRAL, PREMIUM }

    private StatusPill() {}

    public static int backgroundColor(@NonNull Context ctx, @NonNull Intent intent) {
        switch (intent) {
            case SUCCESS:  return Color.parseColor("#4CAF50");
            case WARNING:  return Color.parseColor("#FF9800");
            case DANGER:   return Color.parseColor("#FF5252");
            case INFO:     return Color.parseColor("#008B8B");
            case PREMIUM:  return Color.parseColor("#FFD700");
            case NEUTRAL:
            default:       return Color.parseColor("#3A3A3A");
        }
    }

    public static int foregroundColor(@NonNull Intent intent) {
        // Premium uses gold bg → dark text for contrast. Everything else gets white.
        return intent == Intent.PREMIUM ? Color.BLACK : Color.WHITE;
    }

    /** Apply to a TextView pill (Widget.RH.StatusPill style assumed). */
    public static void apply(@NonNull TextView pill, @NonNull Intent intent, CharSequence text) {
        Context ctx = pill.getContext();
        pill.setText(text);
        pill.setTextColor(foregroundColor(intent));
        pill.setBackgroundResource(drawableFor(intent));
        pill.setVisibility(View.VISIBLE);
    }

    /** Apply to a Material Chip pill (Widget.RH.StatusChip style assumed). */
    public static void apply(@NonNull Chip pill, @NonNull Intent intent, CharSequence text) {
        pill.setText(text);
        pill.setTextColor(foregroundColor(intent));
        pill.setChipBackgroundColor(ColorStateList.valueOf(backgroundColor(pill.getContext(), intent)));
        pill.setVisibility(View.VISIBLE);
    }

    public static int drawableFor(@NonNull Intent intent) {
        switch (intent) {
            case SUCCESS:  return R.drawable.pill_status_success;
            case WARNING:  return R.drawable.pill_status_warning;
            case DANGER:   return R.drawable.pill_status_danger;
            case INFO:     return R.drawable.pill_status_info;
            case PREMIUM:  return R.drawable.pill_status_premium;
            case NEUTRAL:
            default:       return R.drawable.pill_status_neutral;
        }
    }
}

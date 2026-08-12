package Utils;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

/**
 * POST_NOTIFICATIONS runtime permission (Android 13 / API 33+).
 *
 * Below API 33 notifications are allowed by default. From API 33 the app MUST request
 * POST_NOTIFICATIONS at runtime — otherwise local reminders (check-ins) are silently
 * dropped. The manifest already declares the permission; this adds the runtime ask.
 *
 * Ask contextually and ONCE (respect the user's choice); scheduling re-checks
 * hasPermission() on the next home load, so a later grant takes effect automatically.
 */
public class NotificationPermissionHelper {

    public static final int RC_POST_NOTIFICATIONS = 3001;
    private static final String PREFS = "notif_perm_prefs";
    private static final String KEY_ASKED = "post_notif_asked";

    /** True if the app may post notifications (always true below API 33). */
    public static boolean hasPermission(Context context) {
        if (context == null) return false;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true;
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Ask for POST_NOTIFICATIONS once on API 33+. No-op if not needed, already granted,
     * already asked, or the fragment is detached.
     */
    public static void requestIfNeeded(Fragment fragment) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        if (fragment == null || !fragment.isAdded()) return;
        Context ctx = fragment.getContext();
        if (ctx == null || hasPermission(ctx)) return;

        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (prefs.getBoolean(KEY_ASKED, false)) return; // only prompt once
        prefs.edit().putBoolean(KEY_ASKED, true).apply();

        fragment.requestPermissions(
                new String[]{Manifest.permission.POST_NOTIFICATIONS}, RC_POST_NOTIFICATIONS);
    }
}

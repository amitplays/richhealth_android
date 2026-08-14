package Utils;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

/**
 * POST_NOTIFICATIONS runtime permission (Android 13 / API 33+).
 *
 * Below API 33 notifications are allowed by default. From API 33 the app MUST request
 * POST_NOTIFICATIONS at runtime — otherwise local reminders (check-ins) are silently
 * dropped. The manifest already declares the permission; this adds the runtime ask.
 *
 * Asking policy. This used to write an "asked" flag and never ask again, which meant a
 * single accidental dismissal silenced every reminder permanently with nothing in the
 * UI to explain it or undo it. Now:
 *
 *   · we ask at most {@link #MAX_ASKS} times, on separate Home loads;
 *   · after the first ask, shouldShowRequestPermissionRationale() tells us whether the
 *     system will still show the dialog. false + already-asked is the only reliable
 *     "permanently denied" signal, and at that point the app-notification settings
 *     screen is the only route — see {@link #openNotificationSettings(Context)}.
 */
public class NotificationPermissionHelper {

    public static final int RC_POST_NOTIFICATIONS = 3001;
    private static final String PREFS = "notif_perm_prefs";
    private static final String KEY_ASK_COUNT = "post_notif_ask_count";

    /** The OS stops showing the dialog after two dismissals; there is no point asking more. */
    private static final int MAX_ASKS = 2;

    /** True if the app may post notifications (always true below API 33). */
    public static boolean hasPermission(Context context) {
        if (context == null) return false;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true;
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Ask for POST_NOTIFICATIONS on API 33+. No-op if not needed, already granted,
     * permanently denied, out of attempts, or the fragment is detached.
     */
    public static void requestIfNeeded(Fragment fragment) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        if (fragment == null || !fragment.isAdded()) return;
        Context ctx = fragment.getContext();
        if (ctx == null) return;

        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        // Clear the counter while the permission is held. Without this, a user who
        // granted on the second ask and later switched notifications off in system
        // Settings would be stuck: the count stays at MAX_ASKS, so we would never ask
        // again and would report "permanently denied" even though the OS would happily
        // show the dialog.
        if (hasPermission(ctx)) {
            if (prefs.contains(KEY_ASK_COUNT)) prefs.edit().remove(KEY_ASK_COUNT).apply();
            return;
        }

        int asked = prefs.getInt(KEY_ASK_COUNT, 0);
        if (asked >= MAX_ASKS) return;

        // Asked before and the system will no longer show a rationale → the dialog is
        // dead. Stop pestering; the Profile row routes to Settings instead.
        if (asked > 0
                && !fragment.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
            return;
        }

        // Recorded before the dialog resolves because we cannot observe a dismissal any
        // other way; MAX_ASKS bounds it, and a grant makes the count irrelevant.
        prefs.edit().putInt(KEY_ASK_COUNT, asked + 1).apply();

        fragment.requestPermissions(
                new String[]{Manifest.permission.POST_NOTIFICATIONS}, RC_POST_NOTIFICATIONS);
    }

    /**
     * True when we have used up the dialog and only the system settings screen can
     * turn notifications back on. Drives the explanatory copy in Profile.
     */
    public static boolean isPermanentlyDenied(Fragment fragment) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false;
        if (fragment == null || !fragment.isAdded()) return false;
        Context ctx = fragment.getContext();
        if (ctx == null || hasPermission(ctx)) return false;

        int asked = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_ASK_COUNT, 0);
        if (asked == 0) return false;
        return asked >= MAX_ASKS
                || !fragment.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS);
    }

    /**
     * Open this app's notification settings — the only way back once the runtime dialog
     * is spent, and also where the check-in channel itself can be un-muted. Falls back
     * to the app detail page on anything that does not support the direct action.
     */
    public static void openNotificationSettings(Context context) {
        if (context == null) return;
        try {
            Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            try {
                Intent fallback = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.fromParts("package", context.getPackageName(), null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(fallback);
            } catch (Exception ignored) {
                // Nothing sensible left to do — the caller shows a toast.
            }
        }
    }
}

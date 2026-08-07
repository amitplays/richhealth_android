package Utils;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import com.example.richhealth.Activities.CheckInNotificationReceiver;

import java.util.Calendar;

/**
 * Schedules local check-in reminder notifications using AlarmManager.
 * Schedule is determined entirely by the user's tier — no push/Firebase needed.
 *
 *   free / plus / family / family_member → 1st of each month at 9 AM
 *   pro                                  → every Monday at 9 AM
 *   ultra                                → every Monday AND Thursday at 9 AM
 *
 * Call scheduleForTier(context, tier) whenever tier is known (login / home screen load).
 * On BOOT_COMPLETED the receiver reads the stored tier and reschedules automatically.
 */
public class CheckInNotificationHelper {

    private static final String TAG = "CheckInNotifHelper";

    // Separate request codes so Monday and Thursday alarms don't overwrite each other
    public static final int RC_MONTHLY     = 2001;
    public static final int RC_WEEKLY_MON  = 2002;
    public static final int RC_WEEKLY_THU  = 2003;

    // Intent extra carrying which slot this alarm is for
    public static final String EXTRA_ALARM_TYPE = "alarm_type";
    public static final String TYPE_MONTHLY    = "monthly";
    public static final String TYPE_WEEKLY_MON = "weekly_mon";
    public static final String TYPE_WEEKLY_THU = "weekly_thu";

    private static final String PREFS_NAME = "checkin_notif_prefs";
    private static final String KEY_TIER   = "stored_tier";

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Cancel all existing alarms and schedule new ones for the given tier.
     * Safe to call multiple times — always replaces previous alarms.
     */
    public static void scheduleForTier(Context context, String tier) {
        storeTier(context, tier);
        cancelAllReminders(context);

        switch (tier == null ? "free" : tier) {
            case "ultra":
                scheduleAt(context, RC_WEEKLY_MON, TYPE_WEEKLY_MON, nextWeekday(Calendar.MONDAY));
                scheduleAt(context, RC_WEEKLY_THU, TYPE_WEEKLY_THU, nextWeekday(Calendar.THURSDAY));
                break;
            case "pro":
                scheduleAt(context, RC_WEEKLY_MON, TYPE_WEEKLY_MON, nextWeekday(Calendar.MONDAY));
                break;
            default:
                // free, plus, family, family_member → 1st of month
                scheduleAt(context, RC_MONTHLY, TYPE_MONTHLY, nextFirstOfMonth());
                break;
        }
    }

    /**
     * Reschedule the next alarm of the same type after one has fired.
     * Called by CheckInNotificationReceiver after showing a notification.
     */
    public static void rescheduleAfterFire(Context context, String alarmType) {
        switch (alarmType == null ? "" : alarmType) {
            case TYPE_WEEKLY_MON:
                // Schedule the NEXT Monday (add 7 days from today)
                scheduleAt(context, RC_WEEKLY_MON, TYPE_WEEKLY_MON, nextWeekdayFromNow(Calendar.MONDAY, true));
                break;
            case TYPE_WEEKLY_THU:
                scheduleAt(context, RC_WEEKLY_THU, TYPE_WEEKLY_THU, nextWeekdayFromNow(Calendar.THURSDAY, true));
                break;
            case TYPE_MONTHLY:
                scheduleAt(context, RC_MONTHLY, TYPE_MONTHLY, nextFirstOfMonth());
                break;
        }
    }

    /** Called on BOOT_COMPLETED — reads stored tier and reschedules everything. */
    public static void rescheduleOnBoot(Context context) {
        String tier = loadTier(context);
        Log.d(TAG, "Rescheduling on boot, tier=" + tier);
        scheduleForTier(context, tier);
    }

    public static void cancelAllReminders(Context context) {
        cancel(context, RC_MONTHLY,    TYPE_MONTHLY);
        cancel(context, RC_WEEKLY_MON, TYPE_WEEKLY_MON);
        cancel(context, RC_WEEKLY_THU, TYPE_WEEKLY_THU);
    }

    // ─── Internal scheduling ──────────────────────────────────────────────────

    private static void scheduleAt(Context context, int requestCode, String alarmType, Calendar when) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        PendingIntent pi = buildPendingIntent(context, requestCode, alarmType);
        try {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when.getTimeInMillis(), pi);
            Log.d(TAG, "Scheduled " + alarmType + " for " + when.getTime());
        } catch (Exception e) {
            Log.w(TAG, "Could not schedule " + alarmType + ": " + e.getMessage());
        }
    }

    private static void cancel(Context context, int requestCode, String alarmType) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.cancel(buildPendingIntent(context, requestCode, alarmType));
    }

    private static PendingIntent buildPendingIntent(Context context, int requestCode, String alarmType) {
        Intent intent = new Intent(context, CheckInNotificationReceiver.class);
        intent.putExtra(EXTRA_ALARM_TYPE, alarmType);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(context, requestCode, intent, flags);
    }

    // ─── Date math ────────────────────────────────────────────────────────────

    /**
     * Returns the next occurrence of the given weekday at 9:00 AM local time.
     * If today IS that weekday and it's before 9 AM, returns today at 9 AM.
     * Otherwise returns the next future occurrence.
     */
    static Calendar nextWeekday(int weekday) {
        return nextWeekdayFromNow(weekday, false);
    }

    static Calendar nextWeekdayFromNow(int weekday, boolean forceNextWeek) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 9);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);

        int todayDow = c.get(Calendar.DAY_OF_WEEK);
        int daysUntil = (weekday - todayDow + 7) % 7;

        if (forceNextWeek) {
            // After firing we always want NEXT week's slot
            daysUntil = daysUntil == 0 ? 7 : daysUntil + 7;
        } else if (daysUntil == 0 && c.getTimeInMillis() <= System.currentTimeMillis()) {
            // Same weekday but 9 AM already passed — go to next week
            daysUntil = 7;
        }

        if (daysUntil > 0) c.add(Calendar.DAY_OF_YEAR, daysUntil);
        return c;
    }

    /**
     * Returns the 1st of the next month at 9:00 AM local time.
     * If today IS the 1st and it's before 9 AM, returns today at 9 AM.
     */
    static Calendar nextFirstOfMonth() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 9);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);

        if (c.get(Calendar.DAY_OF_MONTH) == 1 && c.getTimeInMillis() > System.currentTimeMillis()) {
            return c; // today is the 1st and 9 AM hasn't passed yet
        }

        // Advance to the 1st of next month
        c.add(Calendar.MONTH, 1);
        c.set(Calendar.DAY_OF_MONTH, 1);
        return c;
    }

    // ─── Tier persistence ─────────────────────────────────────────────────────

    private static void storeTier(Context context, String tier) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_TIER, tier).apply();
    }

    static String loadTier(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_TIER, "free");
    }
}

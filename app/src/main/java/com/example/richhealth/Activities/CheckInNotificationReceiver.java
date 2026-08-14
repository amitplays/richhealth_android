package com.example.richhealth.Activities;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.TaskStackBuilder;

import com.example.richhealth.R;

import Utils.CheckInNotificationHelper;

public class CheckInNotificationReceiver extends BroadcastReceiver {

    /** Some OEM skins send this instead of BOOT_COMPLETED. */
    private static final String ACTION_QUICKBOOT = "android.intent.action.QUICKBOOT_POWERON";

    // One id per slot: with a single shared id the Monday reminder replaced the
    // Thursday one on ultra (and vice versa) instead of both being visible.
    private static final int NOTIF_ID_MONTHLY    = 1001;
    private static final int NOTIF_ID_WEEKLY_MON = 1002;
    private static final int NOTIF_ID_WEEKLY_THU = 1003;

    // Distinct request codes so the tap intents don't overwrite each other either.
    private static final int RC_CONTENT_MONTHLY    = 4001;
    private static final int RC_CONTENT_WEEKLY_MON = 4002;
    private static final int RC_CONTENT_WEEKLY_THU = 4003;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;

        String action = intent.getAction();

        // Reboot, OEM quick-boot, or an app update — the OS drops pending alarms in all
        // three cases, so rebuild them from the stored tier.
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || ACTION_QUICKBOOT.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            CheckInNotificationHelper.rescheduleOnBoot(context);
            return;
        }

        // Determine which slot fired so we can show the right text and reschedule
        String alarmType = intent.getStringExtra(CheckInNotificationHelper.EXTRA_ALARM_TYPE);

        // This receiver has to be exported for the boot filter, which means any app can
        // fire an explicit broadcast at it. Only act on a slot we actually scheduled —
        // otherwise a bare Intent would post a convincing fake check-in reminder and
        // shift the real schedule.
        if (!CheckInNotificationHelper.TYPE_MONTHLY.equals(alarmType)
                && !CheckInNotificationHelper.TYPE_WEEKLY_MON.equals(alarmType)
                && !CheckInNotificationHelper.TYPE_WEEKLY_THU.equals(alarmType)) {
            return;
        }

        showNotification(context, alarmType);

        // Reschedule the NEXT occurrence of this specific alarm type
        CheckInNotificationHelper.rescheduleAfterFire(context, alarmType);
    }

    // ─── Notification ─────────────────────────────────────────────────────────

    private void showNotification(Context context, String alarmType) {
        CheckInNotificationHelper.ensureChannel(context);

        String title = "Health Check-In Ready \uD83D\uDC99";
        String body  = notificationBody(alarmType);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;

        // Build the tap target on top of a synthesized Home task (DailyCheckInActivity
        // declares MainActivity as its parent). Launching it bare with NEW_TASK meant
        // Back from a notification-opened check-in dropped the user out of the app.
        // No flags set here on purpose: TaskStackBuilder puts NEW_TASK | CLEAR_TASK on
        // the ROOT intent it builds. Setting NEW_TASK on this child would make the
        // launcher route it into an existing task instead of the synthesized stack —
        // which is exactly the "Back exits the app" behaviour being fixed.
        Intent openIntent = new Intent(context, DailyCheckInActivity.class);

        PendingIntent pendingIntent = TaskStackBuilder.create(context)
                .addNextIntentWithParentStack(openIntent)
                .getPendingIntent(contentRequestCode(alarmType), flags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                context, CheckInNotificationHelper.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        try {
            manager.notify(notificationId(alarmType), builder.build());
        } catch (SecurityException e) {
            // POST_NOTIFICATIONS not granted on API 33+. The alarm still fired and was
            // rescheduled, so reminders resume the moment the user grants it.
            android.util.Log.w("CheckInNotifReceiver", "Cannot post notification: " + e.getMessage());
        }
    }

    private int notificationId(String alarmType) {
        if (CheckInNotificationHelper.TYPE_WEEKLY_MON.equals(alarmType)) return NOTIF_ID_WEEKLY_MON;
        if (CheckInNotificationHelper.TYPE_WEEKLY_THU.equals(alarmType)) return NOTIF_ID_WEEKLY_THU;
        return NOTIF_ID_MONTHLY;
    }

    private int contentRequestCode(String alarmType) {
        if (CheckInNotificationHelper.TYPE_WEEKLY_MON.equals(alarmType)) return RC_CONTENT_WEEKLY_MON;
        if (CheckInNotificationHelper.TYPE_WEEKLY_THU.equals(alarmType)) return RC_CONTENT_WEEKLY_THU;
        return RC_CONTENT_MONTHLY;
    }

    private String notificationBody(String alarmType) {
        if (alarmType == null) return "Your health check-in is ready. Tap to answer your personalized questions.";
        switch (alarmType) {
            case CheckInNotificationHelper.TYPE_MONTHLY:
                return "Your monthly health check-in is ready. Take a moment to reflect on your health this month.";
            case CheckInNotificationHelper.TYPE_WEEKLY_MON:
            case CheckInNotificationHelper.TYPE_WEEKLY_THU:
                return "Your health check-in is ready. Answer a few personalized questions to stay on top of your wellness.";
            default:
                return "Your health check-in is ready. Tap to answer your personalized questions.";
        }
    }
}

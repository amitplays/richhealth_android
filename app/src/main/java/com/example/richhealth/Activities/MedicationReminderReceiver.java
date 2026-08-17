package com.example.richhealth.Activities;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.example.richhealth.R;

import Utils.MedicationReminderHelper;

/**
 * Receives medication reminder alarms, boot broadcasts and notification-action taps,
 * mirroring {@link CheckInNotificationReceiver}.
 *
 * <ul>
 *   <li>BOOT_COMPLETED / QUICKBOOT_POWERON / MY_PACKAGE_REPLACED → reschedule everything
 *       from the local store (the OS drops pending alarms in all three cases).</li>
 *   <li>ACTION_FIRE → post the reminder notification (Taken / Missed / Snooze) and
 *       reschedule this slot's next occurrence.</li>
 *   <li>ACTION_TAKEN / ACTION_MISSED → cancel the notification and log the dose.</li>
 *   <li>ACTION_SNOOZE → cancel the notification and re-fire this slot in 10 minutes.</li>
 * </ul>
 */
public class MedicationReminderReceiver extends BroadcastReceiver {

    /** Some OEM skins send this instead of BOOT_COMPLETED. */
    private static final String ACTION_QUICKBOOT = "android.intent.action.QUICKBOOT_POWERON";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;

        String sysAction = intent.getAction();

        // Reboot, OEM quick-boot, or an app update — rebuild alarms from the local store.
        if (Intent.ACTION_BOOT_COMPLETED.equals(sysAction)
                || ACTION_QUICKBOOT.equals(sysAction)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(sysAction)) {
            MedicationReminderHelper.rescheduleAll(context);
            return;
        }

        // This receiver is exported for the boot filter, so any app could fire an explicit
        // broadcast at it. Only act on our own known action strings (mirrors CheckIn).
        String action = intent.getStringExtra(MedicationReminderHelper.EXTRA_ACTION);
        if (action == null) return;

        String serverId = intent.getStringExtra(MedicationReminderHelper.EXTRA_SERVER_ID);
        if (serverId == null) return;

        String name = intent.getStringExtra(MedicationReminderHelper.EXTRA_NAME);
        String dose = intent.getStringExtra(MedicationReminderHelper.EXTRA_DOSE);
        int day = intent.getIntExtra(MedicationReminderHelper.EXTRA_DAY, -1);
        int timeIndex = intent.getIntExtra(MedicationReminderHelper.EXTRA_TIME_INDEX, 0);
        int hour = intent.getIntExtra(MedicationReminderHelper.EXTRA_HOUR, 8);
        int minute = intent.getIntExtra(MedicationReminderHelper.EXTRA_MINUTE, 0);
        boolean isSnooze = intent.getBooleanExtra(MedicationReminderHelper.EXTRA_IS_SNOOZE, false);

        int notifId = MedicationReminderHelper.stableId(serverId, day, timeIndex);

        switch (action) {
            case MedicationReminderHelper.ACTION_FIRE:
                showNotification(context, serverId, name, dose, day, timeIndex, hour, minute, notifId);
                // A snooze re-fire must NOT roll the recurring slot forward again — that
                // slot's next occurrence is already scheduled and untouched.
                if (!isSnooze) {
                    MedicationReminderHelper.rescheduleSlotAfterFire(context, serverId, day, timeIndex);
                }
                break;

            case MedicationReminderHelper.ACTION_TAKEN:
                cancelNotification(context, notifId);
                MedicationReminderHelper.postDose(context, serverId,
                        MedicationReminderHelper.DOSE_TAKEN,
                        MedicationReminderHelper.todayAtIso(hour, minute));
                break;

            case MedicationReminderHelper.ACTION_MISSED:
                cancelNotification(context, notifId);
                MedicationReminderHelper.postDose(context, serverId,
                        MedicationReminderHelper.DOSE_MISSED,
                        MedicationReminderHelper.todayAtIso(hour, minute));
                break;

            case MedicationReminderHelper.ACTION_SNOOZE:
                cancelNotification(context, notifId);
                MedicationReminderHelper.scheduleSnooze(context, serverId, name, dose,
                        day, timeIndex, hour, minute);
                break;

            default:
                // Unknown / spoofed action — ignore.
                break;
        }
    }

    // ─── Notification ─────────────────────────────────────────────────────────

    private void showNotification(Context context, String serverId, String name, String dose,
                                  int day, int timeIndex, int hour, int minute, int notifId) {
        MedicationReminderHelper.ensureChannel(context);

        String medName = (name != null && !name.isEmpty()) ? name : "your medication";
        String medDose = (dose != null && !dose.isEmpty()) ? dose : "It's time";
        String title = "Time for " + medName;
        String body = medDose + " — tap Taken once you've had it.";

        PendingIntent takenPi  = actionPendingIntent(context, MedicationReminderHelper.ACTION_TAKEN,
                serverId, name, dose, day, timeIndex, hour, minute);
        PendingIntent missedPi = actionPendingIntent(context, MedicationReminderHelper.ACTION_MISSED,
                serverId, name, dose, day, timeIndex, hour, minute);
        PendingIntent snoozePi = actionPendingIntent(context, MedicationReminderHelper.ACTION_SNOOZE,
                serverId, name, dose, day, timeIndex, hour, minute);

        // Tapping the body opens the app.
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentPi = PendingIntent.getActivity(
                context, MedicationReminderHelper.stableId(serverId, day, timeIndex), openIntent, flags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                context, MedicationReminderHelper.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(contentPi)
                .setAutoCancel(true)
                .addAction(R.drawable.ic_notification, "Taken", takenPi)
                .addAction(R.drawable.ic_notification, "Missed", missedPi)
                .addAction(R.drawable.ic_notification, "Snooze 10 min", snoozePi);

        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        try {
            manager.notify(notifId, builder.build());
        } catch (SecurityException e) {
            // POST_NOTIFICATIONS not granted on API 33+. The alarm still fired and the
            // slot was rescheduled, so reminders resume once the user grants it.
            android.util.Log.w("MedReminderReceiver", "Cannot post notification: " + e.getMessage());
        }
    }

    private PendingIntent actionPendingIntent(Context context, String action, String serverId,
                                              String name, String dose, int day, int timeIndex,
                                              int hour, int minute) {
        Intent intent = new Intent(context, MedicationReminderReceiver.class);
        intent.putExtra(MedicationReminderHelper.EXTRA_ACTION, action);
        intent.putExtra(MedicationReminderHelper.EXTRA_SERVER_ID, serverId);
        intent.putExtra(MedicationReminderHelper.EXTRA_NAME, name);
        intent.putExtra(MedicationReminderHelper.EXTRA_DOSE, dose);
        intent.putExtra(MedicationReminderHelper.EXTRA_DAY, day);
        intent.putExtra(MedicationReminderHelper.EXTRA_TIME_INDEX, timeIndex);
        intent.putExtra(MedicationReminderHelper.EXTRA_HOUR, hour);
        intent.putExtra(MedicationReminderHelper.EXTRA_MINUTE, minute);

        int rc = (serverId + ":" + day + ":" + timeIndex + ":" + action).hashCode() & 0x7fffffff;
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(context, rc, intent, flags);
    }

    private void cancelNotification(Context context, int notifId) {
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.cancel(notifId);
    }
}

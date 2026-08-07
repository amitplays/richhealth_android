package com.example.richhealth.Activities;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.example.richhealth.R;

import Utils.CheckInNotificationHelper;

public class CheckInNotificationReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID   = "checkin_channel";
    private static final int    NOTIFICATION_ID = 1001;

    @Override
    public void onReceive(Context context, Intent intent) {
        // On device reboot — reschedule all alarms using the stored tier
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            CheckInNotificationHelper.rescheduleOnBoot(context);
            return;
        }

        // Determine which slot fired so we can show the right text and reschedule
        String alarmType = intent != null
                ? intent.getStringExtra(CheckInNotificationHelper.EXTRA_ALARM_TYPE)
                : null;

        showNotification(context, alarmType);

        // Reschedule the NEXT occurrence of this specific alarm type
        CheckInNotificationHelper.rescheduleAfterFire(context, alarmType);
    }

    // ─── Notification ─────────────────────────────────────────────────────────

    private void showNotification(Context context, String alarmType) {
        createNotificationChannel(context);

        String title = "Health Check-In Ready \uD83D\uDC99";
        String body  = notificationBody(alarmType);

        Intent openIntent = new Intent(context, DailyCheckInActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;

        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, openIntent, flags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, builder.build());
        }
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

    // ─── Channel ──────────────────────────────────────────────────────────────

    private void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Health Check-In",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Reminders for your scheduled health check-ins");
            NotificationManager manager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }
}

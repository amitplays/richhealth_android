package Utils;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.example.richhealth.Activities.MedicationReminderReceiver;
import com.example.richhealth.Activities.TokenManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * Schedules per-medication reminder notifications using AlarmManager, mirroring
 * {@link CheckInNotificationHelper}. The full schedule is persisted locally in
 * SharedPreferences as JSON so BOOT and offline reschedules need no network — the
 * store is only refreshed when a medication fetch/save actually succeeds.
 *
 * AlarmManager is not a reliable repeating scheduler, so we always schedule only the
 * NEXT occurrence of each (day, time) slot and let the receiver reschedule that slot's
 * next occurrence after it fires (see {@link #rescheduleSlotAfterFire}).
 */
public class MedicationReminderHelper {

    private static final String TAG = "MedReminderHelper";

    private static final String PREFS_NAME = "med_reminder_prefs";
    private static final String KEY_PLANS  = "plans";
    private static final String KEY_QUEUE  = "dose_queue";

    /** Shared with the receiver so channel id can never drift. */
    public static final String CHANNEL_ID = "med_reminders";

    // Intent-extra keys carrying the slot this alarm/action belongs to.
    public static final String EXTRA_ACTION     = "med_action";
    public static final String EXTRA_SERVER_ID  = "med_server_id";
    public static final String EXTRA_NAME       = "med_name";
    public static final String EXTRA_DOSE       = "med_dose";
    public static final String EXTRA_DAY        = "med_day";        // 0=Sun..6=Sat, -1 = daily
    public static final String EXTRA_TIME_INDEX = "med_time_index";
    public static final String EXTRA_HOUR       = "med_hour";
    public static final String EXTRA_MINUTE     = "med_minute";
    public static final String EXTRA_IS_SNOOZE  = "med_is_snooze";

    // Action strings distinguishing what a delivered broadcast means.
    public static final String ACTION_FIRE   = "FIRE";
    public static final String ACTION_TAKEN  = "ACTION_TAKEN";
    public static final String ACTION_MISSED = "ACTION_MISSED";
    public static final String ACTION_SNOOZE = "ACTION_SNOOZE";

    // Dose-log endpoint suffixes.
    public static final String DOSE_TAKEN  = "dose-taken";
    public static final String DOSE_MISSED = "dose-missed";

    public static final int SNOOZE_MINUTES = 10;

    // ─── Notification channel ───────────────────────────────────────────────────

    public static void ensureChannel(Context context) {
        if (context == null) return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        android.app.NotificationManager mgr =
                (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (mgr == null) return;
        android.app.NotificationChannel channel = new android.app.NotificationChannel(
                CHANNEL_ID, "Medication reminders",
                android.app.NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Reminders to take your medications");
        mgr.createNotificationChannel(channel);
    }

    // ─── Local plan store ───────────────────────────────────────────────────────

    /** Overwrite the whole local plan store. */
    public static void saveAllPlans(Context context, JSONArray plans) {
        if (context == null || plans == null) return;
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_PLANS, plans.toString()).apply();
    }

    /** Load the local plan store (never null). */
    public static JSONArray loadPlans(Context context) {
        if (context == null) return new JSONArray();
        String raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_PLANS, "[]");
        try {
            return new JSONArray(raw);
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    private static int indexOfPlan(JSONArray plans, String serverId) {
        if (plans == null || serverId == null) return -1;
        for (int i = 0; i < plans.length(); i++) {
            JSONObject p = plans.optJSONObject(i);
            if (p != null && serverId.equals(p.optString("serverId", null))) return i;
        }
        return -1;
    }

    // ─── Public API ─────────────────────────────────────────────────────────────

    /**
     * Upsert one medication into the local store and (re)schedule its alarms.
     * Cancels the medication's existing alarms first. If it is not enabled the
     * medication is removed from the store entirely (discontinued / reminders off).
     */
    public static void setForMedication(Context context, String serverId, String name, String dose,
                                        boolean enabled, int[] days, int[][] times) {
        if (context == null || serverId == null) return;
        ensureChannel(context);

        JSONArray plans = loadPlans(context);

        // Cancel any alarms from the previously-stored version of this plan.
        int existingIdx = indexOfPlan(plans, serverId);
        if (existingIdx >= 0) {
            cancelAlarmsForPlan(context, plans.optJSONObject(existingIdx));
        }

        // Not enabled (reminders off / discontinued) → drop from store.
        if (!enabled || times == null || times.length == 0) {
            if (existingIdx >= 0) {
                plans = removeAt(plans, existingIdx);
                saveAllPlans(context, plans);
            }
            return;
        }

        JSONObject plan = buildPlanJson(serverId, name, dose, true, days, times);
        try {
            if (existingIdx >= 0) {
                plans.put(existingIdx, plan);
            } else {
                plans.put(plan);
            }
        } catch (JSONException e) {
            Log.w(TAG, "Could not upsert plan: " + e.getMessage());
        }
        saveAllPlans(context, plans);

        scheduleAlarmsForPlan(context, plan);
    }

    /** Remove a medication from the store and cancel its alarms. */
    public static void removeForMedication(Context context, String serverId) {
        if (context == null || serverId == null) return;
        JSONArray plans = loadPlans(context);
        int idx = indexOfPlan(plans, serverId);
        if (idx < 0) return;
        cancelAlarmsForPlan(context, plans.optJSONObject(idx));
        plans = removeAt(plans, idx);
        saveAllPlans(context, plans);
    }

    /** Cancel + reschedule everything from the store (used on boot / app start). */
    public static void rescheduleAll(Context context) {
        if (context == null) return;
        ensureChannel(context);
        JSONArray plans = loadPlans(context);
        for (int i = 0; i < plans.length(); i++) {
            JSONObject plan = plans.optJSONObject(i);
            if (plan == null) continue;
            cancelAlarmsForPlan(context, plan);
            if (plan.optBoolean("enabled", false)) {
                scheduleAlarmsForPlan(context, plan);
            }
        }
    }

    /**
     * Schedule the NEXT occurrence of a single slot after it has fired. Called by the
     * receiver right after showing a notification. Daily slots roll to tomorrow, weekly
     * slots to the next matching weekday.
     */
    public static void rescheduleSlotAfterFire(Context context, String serverId, int dayOrMinus1, int timeIndex) {
        if (context == null || serverId == null) return;
        JSONArray plans = loadPlans(context);
        int idx = indexOfPlan(plans, serverId);
        if (idx < 0) return;
        JSONObject plan = plans.optJSONObject(idx);
        if (plan == null || !plan.optBoolean("enabled", false)) return;

        JSONArray times = plan.optJSONArray("times");
        if (times == null || timeIndex < 0 || timeIndex >= times.length()) return;
        JSONObject t = times.optJSONObject(timeIndex);
        if (t == null) return;
        int hour = t.optInt("hour", 8);
        int minute = t.optInt("minute", 0);

        String name = plan.optString("name", "");
        String dose = plan.optString("dose", "");

        Calendar when = computeNext(dayOrMinus1, hour, minute);
        int rc = stableId(serverId, dayOrMinus1, timeIndex);
        Intent i = buildFireIntent(context, serverId, name, dose, dayOrMinus1, timeIndex, hour, minute, false);
        scheduleExact(context, buildPendingIntent(context, rc, i), when.getTimeInMillis());
    }

    /** Re-fire a slot in SNOOZE_MINUTES via a distinct one-shot alarm (does not disturb the recurring slot). */
    public static void scheduleSnooze(Context context, String serverId, String name, String dose,
                                      int dayOrMinus1, int timeIndex, int hour, int minute) {
        if (context == null || serverId == null) return;
        long when = System.currentTimeMillis() + SNOOZE_MINUTES * 60L * 1000L;
        int rc = snoozeId(serverId, dayOrMinus1, timeIndex);
        Intent i = buildFireIntent(context, serverId, name, dose, dayOrMinus1, timeIndex, hour, minute, true);
        scheduleExact(context, buildPendingIntent(context, rc, i), when);
    }

    // ─── Scheduling internals ───────────────────────────────────────────────────

    private static JSONObject buildPlanJson(String serverId, String name, String dose,
                                            boolean enabled, int[] days, int[][] times) {
        JSONObject plan = new JSONObject();
        try {
            plan.put("serverId", serverId);
            plan.put("name", name != null ? name : "");
            plan.put("dose", dose != null ? dose : "");
            plan.put("enabled", enabled);

            JSONArray daysArr = new JSONArray();
            if (days != null) {
                for (int d : days) daysArr.put(d);
            }
            plan.put("days", daysArr);

            JSONArray timesArr = new JSONArray();
            if (times != null) {
                for (int[] hm : times) {
                    if (hm == null || hm.length < 2) continue;
                    JSONObject t = new JSONObject();
                    t.put("hour", hm[0]);
                    t.put("minute", hm[1]);
                    timesArr.put(t);
                }
            }
            plan.put("times", timesArr);
        } catch (JSONException e) {
            Log.w(TAG, "Could not build plan json: " + e.getMessage());
        }
        return plan;
    }

    /** For each (day, time) slot schedule the next occurrence. Daily = day list of {-1}. */
    private static void scheduleAlarmsForPlan(Context context, JSONObject plan) {
        if (plan == null) return;
        String serverId = plan.optString("serverId", null);
        if (serverId == null) return;
        String name = plan.optString("name", "");
        String dose = plan.optString("dose", "");

        int[] dayList = dayListFor(plan);
        JSONArray times = plan.optJSONArray("times");
        if (times == null) return;

        for (int ti = 0; ti < times.length(); ti++) {
            JSONObject t = times.optJSONObject(ti);
            if (t == null) continue;
            int hour = t.optInt("hour", 8);
            int minute = t.optInt("minute", 0);
            for (int d : dayList) {
                Calendar when = computeNext(d, hour, minute);
                int rc = stableId(serverId, d, ti);
                Intent i = buildFireIntent(context, serverId, name, dose, d, ti, hour, minute, false);
                scheduleExact(context, buildPendingIntent(context, rc, i), when.getTimeInMillis());
            }
        }
    }

    private static void cancelAlarmsForPlan(Context context, JSONObject plan) {
        if (plan == null) return;
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        String serverId = plan.optString("serverId", null);
        if (serverId == null) return;

        int[] dayList = dayListFor(plan);
        JSONArray times = plan.optJSONArray("times");
        int count = times != null ? times.length() : 0;
        for (int ti = 0; ti < count; ti++) {
            for (int d : dayList) {
                Intent i = buildFireIntent(context, serverId, "", "", d, ti, 0, 0, false);
                am.cancel(buildPendingIntent(context, stableId(serverId, d, ti), i));
                // Also drop any pending snooze for this slot.
                am.cancel(buildPendingIntent(context, snoozeId(serverId, d, ti), i));
            }
        }
    }

    private static int[] dayListFor(JSONObject plan) {
        JSONArray days = plan.optJSONArray("days");
        if (days == null || days.length() == 0) return new int[]{-1};
        int[] out = new int[days.length()];
        for (int i = 0; i < days.length(); i++) out[i] = days.optInt(i, -1);
        return out;
    }

    private static Intent buildFireIntent(Context context, String serverId, String name, String dose,
                                          int day, int timeIndex, int hour, int minute, boolean isSnooze) {
        Intent intent = new Intent(context, MedicationReminderReceiver.class);
        intent.putExtra(EXTRA_ACTION, ACTION_FIRE);
        intent.putExtra(EXTRA_SERVER_ID, serverId);
        intent.putExtra(EXTRA_NAME, name);
        intent.putExtra(EXTRA_DOSE, dose);
        intent.putExtra(EXTRA_DAY, day);
        intent.putExtra(EXTRA_TIME_INDEX, timeIndex);
        intent.putExtra(EXTRA_HOUR, hour);
        intent.putExtra(EXTRA_MINUTE, minute);
        intent.putExtra(EXTRA_IS_SNOOZE, isSnooze);
        return intent;
    }

    private static PendingIntent buildPendingIntent(Context context, int requestCode, Intent intent) {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(context, requestCode, intent, flags);
    }

    /**
     * Exact when permitted, otherwise best-effort — never crash. On API 31+ exact alarms
     * require user permission, so we fall back to setAndAllowWhileIdle when not granted.
     */
    private static void scheduleExact(Context context, PendingIntent pi, long whenMillis) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null || pi == null) return;
        try {
            boolean canExact = true;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                canExact = am.canScheduleExactAlarms();
            }
            if (canExact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMillis, pi);
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMillis, pi);
            }
        } catch (SecurityException se) {
            try {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMillis, pi);
            } catch (Exception ignored) {}
        } catch (Exception e) {
            Log.w(TAG, "Could not schedule alarm: " + e.getMessage());
        }
    }

    // ─── Date math ──────────────────────────────────────────────────────────────

    /**
     * Next occurrence of a slot. day == -1 → daily (today at time if still future, else
     * tomorrow). Otherwise 0=Sun..6=Sat mapped onto Calendar.DAY_OF_WEEK.
     */
    static Calendar computeNext(int day, int hour, int minute) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);

        if (day < 0) {
            if (c.getTimeInMillis() <= System.currentTimeMillis()) {
                c.add(Calendar.DAY_OF_YEAR, 1);
            }
            return c;
        }

        int targetDow = day + 1; // Calendar: Sunday=1 .. Saturday=7
        int todayDow = c.get(Calendar.DAY_OF_WEEK);
        int daysUntil = (targetDow - todayDow + 7) % 7;
        if (daysUntil == 0 && c.getTimeInMillis() <= System.currentTimeMillis()) {
            daysUntil = 7;
        }
        if (daysUntil > 0) c.add(Calendar.DAY_OF_YEAR, daysUntil);
        return c;
    }

    /** ISO-8601 (UTC) for today at the given local hour/minute — the dose's scheduledTime. */
    public static String todayAtIso(int hour, int minute) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(c.getTime());
    }

    // ─── Stable ids ─────────────────────────────────────────────────────────────

    /** Stable request code + notification id per (serverId, day, timeIndex). */
    public static int stableId(String serverId, int day, int timeIndex) {
        return (serverId + ":" + day + ":" + timeIndex).hashCode() & 0x7fffffff;
    }

    private static int snoozeId(String serverId, int day, int timeIndex) {
        return (serverId + ":" + day + ":" + timeIndex + ":snooze").hashCode() & 0x7fffffff;
    }

    // ─── Dose logging + offline queue ───────────────────────────────────────────

    /**
     * POST a dose event to the backend; on any network failure enqueue it locally so a
     * later {@link #flushDoseQueue} retries it. doseAction = DOSE_TAKEN / DOSE_MISSED.
     */
    public static void postDose(Context context, String serverId, String doseAction, String scheduledTimeIso) {
        if (context == null || serverId == null || doseAction == null) return;
        final Context app = context.getApplicationContext();

        String token = TokenManager.getInstance(app).getToken();
        if (token == null) {
            enqueueDose(app, serverId, doseAction, scheduledTimeIso);
            return;
        }

        String url = ApiConfig.BASE_URL + "/api/health/medications/" + serverId + "/" + doseAction;

        JSONObject body = new JSONObject();
        try {
            body.put("scheduledTime", scheduledTimeIso);
        } catch (JSONException e) {
            Log.w(TAG, "Could not build dose body: " + e.getMessage());
        }

        final String finalToken = token;
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, body,
                response -> ApiConfig.logRestCall(url, true, "Dose " + doseAction + " logged"),
                error -> {
                    ApiConfig.logRestCall(url, false, error.toString());
                    enqueueDose(app, serverId, doseAction, scheduledTimeIso);
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + finalToken);
                headers.put("Content-Type", "application/json");
                return headers;
            }
        };

        try {
            RequestQueue queue = Volley.newRequestQueue(app);
            queue.add(request);
        } catch (Exception e) {
            enqueueDose(app, serverId, doseAction, scheduledTimeIso);
        }
    }

    private static void enqueueDose(Context context, String serverId, String doseAction, String scheduledTimeIso) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        JSONArray queue;
        try {
            queue = new JSONArray(prefs.getString(KEY_QUEUE, "[]"));
        } catch (JSONException e) {
            queue = new JSONArray();
        }
        try {
            JSONObject item = new JSONObject();
            item.put("serverId", serverId);
            item.put("action", doseAction);
            item.put("scheduledTime", scheduledTimeIso);
            queue.put(item);
            prefs.edit().putString(KEY_QUEUE, queue.toString()).apply();
        } catch (JSONException e) {
            Log.w(TAG, "Could not enqueue dose: " + e.getMessage());
        }
    }

    /** Attempt to send every queued dose event. Failures re-enqueue themselves. */
    public static void flushDoseQueue(Context context) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        JSONArray queue;
        try {
            queue = new JSONArray(prefs.getString(KEY_QUEUE, "[]"));
        } catch (JSONException e) {
            return;
        }
        if (queue.length() == 0) return;

        // Clear now; each entry is re-sent (and re-enqueued on failure).
        prefs.edit().remove(KEY_QUEUE).apply();

        for (int i = 0; i < queue.length(); i++) {
            JSONObject item = queue.optJSONObject(i);
            if (item == null) continue;
            postDose(context,
                    item.optString("serverId", null),
                    item.optString("action", DOSE_TAKEN),
                    item.optString("scheduledTime", null));
        }
    }

    // ─── helpers ────────────────────────────────────────────────────────────────

    private static JSONArray removeAt(JSONArray arr, int idx) {
        JSONArray out = new JSONArray();
        for (int i = 0; i < arr.length(); i++) {
            if (i == idx) continue;
            out.put(arr.opt(i));
        }
        return out;
    }
}

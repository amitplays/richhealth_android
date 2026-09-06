package Utils;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.example.richhealth.R;
import com.example.richhealth.Activities.MainActivity;
import com.example.richhealth.Activities.TokenManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Local notifications for the four family / dependency events.
 *
 *   1. a new incoming connection request            → the recipient
 *   2. a request I sent was accepted                → me
 *   3. a request I sent was declined                → me
 *   4. a dependency was removed                     → the other side
 *
 * There is deliberately no server push here: APNS/FCM is not configured for this app, so
 * the backend's half of these events is the email it already sends. A local notification
 * can only be raised by code that is actually running, so this is a DIFF against a
 * snapshot in SharedPreferences, recomputed whenever the app already fetches family data
 * (HomeFragment.checkForIncomingRequests runs on every Home onResume).
 *
 * Snapshot, per account (see {@link #accountKey}):
 *   · the emails of pending INCOMING requests
 *   · the emails of pending SENT requests
 *   · the accepted relatives that currently carry a `dependency`
 *
 * Diff rules:
 *   · an incoming email that is new since last time              → "new request"
 *   · a sent email that DISAPPEARED and is now an accepted        → "accepted"
 *     relative
 *   · a sent email that DISAPPEARED and is NOT an accepted        → "declined"
 *     relative
 *   · a relative that had a dependency and no longer does         → "dependency removed"
 *
 * Two invariants this class exists to protect:
 *   · FIRST RUN IS SILENT. With no stored snapshot every existing request looks new, so
 *     the first refresh for an account only seeds and notifies nothing.
 *   · A USER IS NEVER TOLD ABOUT THEIR OWN ACTION. Accepting, declining, cancelling or
 *     removing a dependency locally changes exactly the sets this class watches, so those
 *     call sites re-seed through {@link #refreshAfterLocalAction} instead.
 *
 * Data sources (both already used elsewhere in the app):
 *   GET /api/users/relationship/requests → { sentRequests:[{email,relationship,status,isDependency}],
 *                                            incomingRequests:[{email,name,relationship,status,isDependency}] }
 *   GET /api/users/relationships         → { relationships:[ …accepted rows with
 *                                            {userId,email,name,status:"accepted",dependency}, …pending rows ] }
 * The accepted side has to come from the second call: /relationship/requests cannot tell
 * an accepted request from a declined one — both simply stop being pending.
 */
public final class FamilyNotificationHelper {

    private FamilyNotificationHelper() {}

    private static final String TAG = "FamilyNotifHelper";

    /** Its own channel so a user can mute family alerts without losing check-in reminders. */
    public static final String CHANNEL_ID = "family_events";

    private static final String PREFS = "family_notif_prefs";

    // Every key carries the account id. Keying per account is what makes signing into a
    // second account on the same device impossible to cross-notify with the first
    // account's requests — the second account simply has no snapshot and seeds silently.
    // (The whole file is also listed in TokenManager.ACCOUNT_SCOPED_PREFS so it is wiped
    // on logout; the per-account keys are the belt to that pair of braces.)
    private static final String KEY_SEEDED   = "seeded:";
    private static final String KEY_INCOMING = "incoming:";
    private static final String KEY_SENT     = "sent:";
    private static final String KEY_DEPS     = "deps:";

    /** navigate_to values consumed by MainActivity / ProfileFragment / HealthDataFragment. */
    public static final String NAV_FAMILY_REQUESTS = "family_requests";
    public static final String NAV_FAMILY          = "family";

    // One refresh at a time. Both GETs come back on the main thread, so these plain
    // statics need no synchronisation.
    private static boolean inFlight = false;
    /** Set by a local action so whichever refresh lands next stays quiet. */
    private static boolean suppressNotify = false;

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Re-read family state and raise a notification for anything that changed since the
     * last snapshot. Safe to call on every Home resume; cheap when nothing changed.
     */
    public static void refreshAndNotify(Context context) {
        start(context);
    }

    /**
     * Re-seed the snapshot WITHOUT notifying. Call after a local accept / decline /
     * cancel / remove-dependency succeeds: those all mutate the watched sets, and
     * without this the next diff would report the user's own action back to them.
     */
    public static void refreshAfterLocalAction(Context context) {
        // Set before the in-flight check on purpose: if a notifying refresh is already
        // running it is downgraded to silent, because its data predates this action and
        // the user must not be told about it either way.
        suppressNotify = true;
        start(context);
    }

    /** Set when a refresh is asked for while one is already running — see start(). */
    private static volatile boolean rerunRequested = false;

    /**
     * Create the channel. Idempotent. Called early (alongside the other helpers' channel
     * setup) for the same reason CheckInNotificationHelper does it: the channel then
     * exists in system settings before the first notification is ever posted.
     */
    public static void ensureChannel(Context context) {
        if (context == null) return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager mgr =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (mgr == null) return;
        android.app.NotificationChannel channel = new android.app.NotificationChannel(
                CHANNEL_ID, "Family & dependants",
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("Connection requests, accepted or declined invites, and dependency changes");
        mgr.createNotificationChannel(channel);
    }

    // ─── Fetch ────────────────────────────────────────────────────────────────

    private static void start(final Context anyContext) {
        if (anyContext == null) return;
        // Application context: this outlives the fragment that kicked it off.
        final Context context = anyContext.getApplicationContext();

        TokenManager tm = TokenManager.getInstance(context);
        final String token = tm != null ? tm.getToken() : null;
        final String account = accountKey(tm);
        // No token or no account id means we cannot key a snapshot, and an unkeyed one
        // is exactly the cross-account leak this class is built to avoid.
        if (token == null || account == null) {
            // Nothing will run to consume a pending suppression, so clear it rather than
            // leave it latched and silence the next real refresh. Only safe when no other
            // refresh is in flight — that one still has to honour it.
            if (!inFlight) suppressNotify = false;
            return;
        }

        if (inFlight) {
            // A refresh is already running, and its HTTP responses predate whatever just
            // happened — so it cannot see this action and would store a pre-action
            // snapshot. Ask it to run again when it finishes rather than dropping this.
            rerunRequested = true;
            return;
        }
        inFlight = true;

        final String requestsUrl = ApiConfig.BASE_URL + "/api/users/relationship/requests";
        final String relsUrl     = ApiConfig.BASE_URL + "/api/users/relationships";

        // [0] = /relationship/requests body, [1] = /relationships body; null = failed.
        final JSONObject[] bodies = new JSONObject[2];

        // Both halves must land before anything is compared or stored. Storing a
        // half-picture would look like "every sent request vanished" on the next run and
        // fire a wave of false "declined" notifications.
        //
        // They are fetched SEQUENTIALLY, requests first, and that order matters. The
        // server removes the sender's pending row and inserts the new relative in one
        // save, so reading relationships FIRST can land in the gap where the request is
        // already gone but the relative has not appeared — which reads exactly like a
        // decline. Requests-then-relationships can only ever miss an event, which the
        // next refresh picks up. (Running them in parallel has the same inversion bug.)
        com.android.volley.RequestQueue queue = Volley.newRequestQueue(context);
        queue.add(get(requestsUrl, token, requestsJson -> {
            bodies[0] = requestsJson;
            queue.add(get(relsUrl, token, relsJson -> {
                bodies[1] = relsJson;
                inFlight = false;
                boolean complete = bodies[0] != null && bodies[1] != null;
                boolean notify = !suppressNotify;
                // Only clear the suppression once it has actually been honoured by a
                // completed store. Clearing it on a failed silent re-seed would leave the
                // snapshot holding the pre-action state, and the next refresh would
                // report the user's own action back to them.
                if (complete) suppressNotify = false;
                if (complete) {
                    try {
                        computeAndStore(context, account, bodies[0], bodies[1], notify);
                    } catch (Exception e) {
                        Log.w(TAG, "Family diff failed: " + e.getMessage());
                    }
                }
                if (rerunRequested) {
                    rerunRequested = false;
                    start(context);
                }
            }));
        }));
    }

    private interface JsonCallback { void onResult(JSONObject json); }

    private static StringRequest get(final String url, final String token, final JsonCallback cb) {
        return new StringRequest(Request.Method.GET, url,
                response -> {
                    JSONObject parsed = null;
                    try {
                        parsed = new JSONObject(response);
                    } catch (Exception e) {
                        Log.w(TAG, "Bad JSON from " + url);
                    }
                    cb.onResult(parsed);
                },
                error -> {
                    ApiConfig.logRestCall(url, false, error.toString());
                    cb.onResult(null);
                }) {
            @Override public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> h = new HashMap<>();
                h.put("Authorization", "Bearer " + token);
                return h;
            }
        };
    }

    // ─── Diff ─────────────────────────────────────────────────────────────────

    private static void computeAndStore(Context context, String account,
                                        JSONObject requestsBody, JSONObject relsBody,
                                        boolean notify) {

        // ── current incoming requests ──
        Set<String> curIncoming = new HashSet<>();
        Map<String, JSONObject> incomingByEmail = new HashMap<>();
        JSONArray incoming = requestsBody.optJSONArray("incomingRequests");
        if (incoming != null) {
            for (int i = 0; i < incoming.length(); i++) {
                JSONObject r = incoming.optJSONObject(i);
                if (r == null) continue;
                // status defaults to "pending" server-side; treat a missing one as pending
                // too, exactly as FamilyRequestsSheet.fetchRequests does.
                if (!"pending".equalsIgnoreCase(r.optString("status", "pending"))) continue;
                String email = norm(r.optString("email", ""));
                if (email.isEmpty()) continue;
                curIncoming.add(email);
                incomingByEmail.put(email, r);
            }
        }

        // ── current sent requests ──
        Set<String> curSent = new HashSet<>();
        JSONArray sent = requestsBody.optJSONArray("sentRequests");
        if (sent != null) {
            for (int i = 0; i < sent.length(); i++) {
                JSONObject r = sent.optJSONObject(i);
                if (r == null) continue;
                if (!"pending".equalsIgnoreCase(r.optString("status", "pending"))) continue;
                String email = norm(r.optString("email", ""));
                if (!email.isEmpty()) curSent.add(email);
            }
        }

        // ── current accepted relatives ──
        // /api/users/relationships returns accepted relatives AND the pending rows; only
        // the accepted ones carry userId and dependency, so filter on status.
        Set<String> acceptedEmails = new HashSet<>();
        Map<String, String> nameByEmail = new HashMap<>();
        Set<String> acceptedIds = new HashSet<>();
        Set<String> curDeps = new HashSet<>();     // encoded, see depEntry()
        JSONArray rels = relsBody.optJSONArray("relationships");
        if (rels != null) {
            for (int i = 0; i < rels.length(); i++) {
                JSONObject r = rels.optJSONObject(i);
                if (r == null) continue;
                if (!"accepted".equalsIgnoreCase(r.optString("status", ""))) continue;
                String email = norm(r.optString("email", ""));
                String name  = r.optString("name", "");
                if (!email.isEmpty()) {
                    acceptedEmails.add(email);
                    nameByEmail.put(email, name);
                }
                String userId = r.optString("userId", "");
                if (userId.isEmpty()) continue;
                acceptedIds.add(userId);
                // `dependency` is null on an ordinary relative and "dependent"/"guardian"
                // when the connection also carries the guardian layer.
                String dependency = r.isNull("dependency") ? "" : r.optString("dependency", "");
                if (!dependency.isEmpty()) curDeps.add(depEntry(userId, dependency, name));
            }
        }

        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        boolean seeded = prefs.getBoolean(KEY_SEEDED + account, false);

        // First run for this account: seed and say nothing. Without this every request
        // that already existed would fire at once.
        if (notify && seeded) {
            Set<String> oldIncoming = prefs.getStringSet(KEY_INCOMING + account, new HashSet<>());
            Set<String> oldSent     = prefs.getStringSet(KEY_SENT + account, new HashSet<>());
            Set<String> oldDeps     = prefs.getStringSet(KEY_DEPS + account, new HashSet<>());

            // 1. a new incoming request
            for (String email : curIncoming) {
                if (oldIncoming.contains(email)) continue;
                notifyIncoming(context, email, incomingByEmail.get(email));
            }

            // 2 & 3. a sent request that stopped being pending — accepted if the person
            // is now an accepted relative, declined if they are not.
            for (String email : oldSent) {
                if (curSent.contains(email)) continue;
                if (acceptedEmails.contains(email)) {
                    String who = display(nameByEmail.get(email), email);
                    post(context, "accepted:" + email, NAV_FAMILY,
                            "Family request accepted",
                            who + " accepted your family request.");
                } else {
                    post(context, "declined:" + email, NAV_FAMILY,
                            "Family request declined",
                            email + " declined your family request.");
                }
            }

            // 4. a relative that carried a dependency and no longer does
            Set<String> curDepIds = new HashSet<>();
            for (String entry : curDeps) curDepIds.add(depId(entry));
            for (String entry : oldDeps) {
                String userId = depId(entry);
                if (userId.isEmpty() || curDepIds.contains(userId)) continue;
                notifyDependencyRemoved(context, entry, acceptedIds.contains(userId));
            }
        }

        // Always store — including on the silent paths — so the next diff is measured
        // against what the server says right now.
        prefs.edit()
                .putStringSet(KEY_INCOMING + account, curIncoming)
                .putStringSet(KEY_SENT + account, curSent)
                .putStringSet(KEY_DEPS + account, curDeps)
                .putBoolean(KEY_SEEDED + account, true)
                .apply();
    }

    // ─── Notification text ────────────────────────────────────────────────────

    private static void notifyIncoming(Context context, String email, JSONObject req) {
        String name = req != null ? req.optString("name", "") : "";
        String relationship = req != null ? req.optString("relationship", "") : "";
        // Raised either by a signup ("a parent is creating this account for me") or by an
        // ordinary request flagged as a dependency. Accepting makes the SENDER this user's
        // dependent — i.e. this user their guardian — which is a great deal more than
        // "we are related", so it must not read like an ordinary request.
        boolean isDependency = req != null && req.optBoolean("isDependency", false);

        String who = display(name, email);
        String title, body;
        if (isDependency) {
            title = "New guardian request";
            body = who + " wants you as their guardian — you would look after their account.";
        } else {
            title = "New family request";
            body = relationship == null || relationship.isEmpty()
                    ? who + " wants to connect with you as family."
                    : who + " wants to connect with you as " + relationship + ".";
        }
        // Tap goes to the Profile requests sheet: the only place the user can act on it.
        post(context, "incoming:" + email, NAV_FAMILY_REQUESTS, title, body);
    }

    private static void notifyDependencyRemoved(Context context, String oldEntry, boolean stillRelated) {
        String[] parts = oldEntry.split("\\|", 3);
        String userId = parts.length > 0 ? parts[0] : "";
        String role   = parts.length > 1 ? parts[1] : "";
        String name   = parts.length > 2 ? parts[2] : "";
        String who = display(name, "A family member");

        // The role has to come out of the OLD snapshot: by the time we notice, the server
        // has already cleared `dependency`, so the current row cannot say which way round
        // the link ran.
        String body = "guardian".equalsIgnoreCase(role)
                ? who + " is no longer your guardian."
                : who + " is no longer your dependent.";
        // Removing a dependency keeps the family connection; deleting the connection
        // outright also ends the dependency. Only claim the connection survived when the
        // relative is still in the accepted list.
        if (stillRelated) body += " You are still connected as family.";

        post(context, "depremoved:" + userId, NAV_FAMILY, "Dependency removed", body);
    }

    // ─── Posting ──────────────────────────────────────────────────────────────

    private static void post(Context context, String key, String navigateTo,
                             String title, String body) {
        NotificationManager mgr =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (mgr == null) return;
        ensureChannel(context);

        int id = notifId(key);

        // Same deep-link mechanism the medication reminder already uses: reuse the single
        // MainActivity task and hand it a navigate_to extra, rather than adding a second
        // linking scheme. MainActivity switches tab; the fragment consumes the extra.
        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        openIntent.putExtra("navigate_to", navigateTo);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        // Request code == notification id so two family notifications can never overwrite
        // each other's extras under FLAG_UPDATE_CURRENT.
        PendingIntent contentPi = PendingIntent.getActivity(context, id, openIntent, flags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(contentPi)
                .setAutoCancel(true);

        try {
            mgr.notify(id, builder.build());
        } catch (SecurityException e) {
            // POST_NOTIFICATIONS not granted on API 33+. HomeFragment already asks for it
            // (NotificationPermissionHelper.requestIfNeeded); same treatment as the
            // check-in and medication receivers — log and carry on.
            Log.w(TAG, "Cannot post family notification: " + e.getMessage());
        }
    }

    // ─── Small helpers ────────────────────────────────────────────────────────

    /**
     * Stable per-event notification id, so re-posting the same event replaces it instead
     * of stacking. Same hashing convention as MedicationReminderHelper.stableId; the
     * "family:" prefix keeps it in its own space, and the floor keeps it clear of the
     * check-in receiver's fixed 1001–1003 ids.
     */
    private static int notifId(String key) {
        int id = ("family:" + key).hashCode() & 0x7fffffff;
        return id < 10000 ? id + 10000 : id;
    }

    /** userId|role|name — split with limit 3 so a name containing '|' survives intact. */
    private static String depEntry(String userId, String role, String name) {
        return userId + "|" + (role == null ? "" : role) + "|" + (name == null ? "" : name);
    }

    private static String depId(String entry) {
        if (entry == null) return "";
        int i = entry.indexOf('|');
        return i < 0 ? entry : entry.substring(0, i);
    }

    /** Emails are compared as set members, so normalise before they ever go in a set. */
    private static String norm(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static String display(String name, String fallback) {
        return (name != null && !name.trim().isEmpty()) ? name : fallback;
    }

    /**
     * The account this snapshot belongs to. The user id is what LoginActivity stores
     * alongside the token, so it changes the moment a different account signs in.
     */
    private static String accountKey(TokenManager tm) {
        if (tm == null) return null;
        String userId = tm.getUserId();
        return (userId == null || userId.trim().isEmpty()) ? null : userId.trim();
    }
}

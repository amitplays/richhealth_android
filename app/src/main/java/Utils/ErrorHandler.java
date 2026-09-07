package Utils;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.android.volley.VolleyError;

import org.json.JSONObject;

import com.example.richhealth.Activities.LoginActivity;
import com.example.richhealth.Activities.TokenManager;

/**
 * Centralised Volley error parser for consistent server error handling.
 * Usage:
 *   ErrorHandler.ParsedError e = ErrorHandler.parse(error);
 *   if (e.type == ErrorHandler.ErrorType.AUTH_EXPIRED) ErrorHandler.redirectToLogin(ctx);
 */
public class ErrorHandler {

    private static final String TAG = "ErrorHandler";

    public enum ErrorType {
        AUTH_EXPIRED,   // 401 — session expired
        RATE_LIMIT,     // 429 — usage/feature limit reached
        SERVER_ERROR,   // 500, 503 — backend issue
        NETWORK_ERROR,  // no response — connectivity
        BAD_REQUEST,    // 400 — malformed request
        UNKNOWN
    }

    public static class ParsedError {
        public final ErrorType type;
        public final int statusCode;
        public final String message;       // user-facing message
        public final String serverMessage; // raw server message if any
        public final String errorCode;     // server's machine-readable reason, when it sends one

        /**
         * True only for the 429 that actually means "your plan is used up".
         *
         * Two unrelated things answer 429 and they were indistinguishable here. The
         * monthly quota checks name themselves with an errorCode (LIMIT_REACHED) and
         * usually attach a usageStatus block; the shared 10-requests-per-minute AI
         * limiter answers with a bare {message} and nothing else. Both arrived as
         * RATE_LIMIT, so every screen sold an upgrade for both — and a paying user who
         * simply tapped an eleventh time in a minute was told to buy the plan she is
         * already on.
         *
         * RATE_LIMIT still means exactly what it always meant (any 429), so callers that
         * only read the type are unaffected. This flag is what says WHICH 429 it was, and
         * it lives here rather than at each call site because the same pair of 429s comes
         * back from every aiLimiter route (chat send, nutri-check, dietary insights,
         * digest, briefing) — a fourth screen must not have to rediscover the rule.
         */
        public final boolean planQuota;

        ParsedError(ErrorType type, int statusCode, String message, String serverMessage) {
            this(type, statusCode, message, serverMessage, null, false);
        }

        ParsedError(ErrorType type, int statusCode, String message, String serverMessage,
                    String errorCode, boolean planQuota) {
            this.type = type;
            this.statusCode = statusCode;
            this.message = message;
            this.serverMessage = serverMessage;
            this.errorCode = errorCode;
            this.planQuota = planQuota;
        }
    }

    /**
     * Does this error body describe the plan quota, or the per-minute AI limiter?
     *
     * The quota answers name themselves ("LIMIT_REACHED", "ReportLimitReached") and the
     * home-screen ones also carry the usageStatus block the upgrade dialog renders; the
     * limiter sends neither. Matching on "LIMIT" rather than one exact string keeps a
     * future quota code from silently falling through to the wait-a-moment branch, while
     * an unrelated 429 code (should one appear) still does not read as a paywall.
     * Overloaded for the screens that have already parsed the body themselves.
     */
    public static boolean isPlanQuota(JSONObject body) {
        if (body == null) return false;
        String code = body.optString("errorCode", "");
        if (code.toUpperCase(java.util.Locale.US).contains("LIMIT")) return true;
        return body.optJSONObject("usageStatus") != null;
    }

    /** Same question, straight from the VolleyError. Never throws. */
    public static boolean isPlanQuota(VolleyError error) {
        if (error == null || error.networkResponse == null) return false;
        try {
            return isPlanQuota(new JSONObject(new String(error.networkResponse.data, "UTF-8")));
        } catch (Exception ignored) {
            return false;
        }
    }

    /** Parse a VolleyError into a structured ParsedError. Never throws. */
    public static ParsedError parse(VolleyError error) {
        if (error == null) {
            return new ParsedError(ErrorType.UNKNOWN, 0, "An unexpected error occurred.", null);
        }

        if (error.networkResponse == null) {
            return new ParsedError(ErrorType.NETWORK_ERROR, 0,
                    "No connection. Please check your internet.", null);
        }

        int status = error.networkResponse.statusCode;
        String serverMessage = null;
        // Held beyond the try so the 429 case can inspect more than the message —
        // errorCode/usageStatus are what tell the plan quota apart from the AI limiter.
        JSONObject json = null;
        String errorCode = null;

        try {
            String body = new String(error.networkResponse.data, "UTF-8");
            json = new JSONObject(body);
            serverMessage = json.optString("message", null);
            if (serverMessage != null && serverMessage.isEmpty()) serverMessage = null;
            errorCode = json.optString("errorCode", null);
            if (errorCode != null && errorCode.isEmpty()) errorCode = null;
        } catch (Exception ignored) {}

        Log.d(TAG, "HTTP " + status + " — " + serverMessage);

        switch (status) {
            case 401:
                return new ParsedError(ErrorType.AUTH_EXPIRED, status,
                        "Your session has expired. Please log in again.", serverMessage);
            case 429:
                // Message and type are deliberately unchanged — a caller that only reads
                // those behaves exactly as before. planQuota is the new information: false
                // here means "wait a moment", not "buy something".
                String limitMsg = serverMessage != null ? serverMessage
                        : "You've reached your usage limit. Upgrade your plan for more.";
                return new ParsedError(ErrorType.RATE_LIMIT, status, limitMsg, serverMessage,
                        errorCode, isPlanQuota(json));
            case 500:
            case 503:
                return new ParsedError(ErrorType.SERVER_ERROR, status,
                        "Server is temporarily unavailable. Please try again shortly.", serverMessage);
            case 400:
                String badMsg = serverMessage != null ? serverMessage : "Invalid request.";
                return new ParsedError(ErrorType.BAD_REQUEST, status, badMsg, serverMessage);
            default:
                String unknownMsg = serverMessage != null ? serverMessage : "Something went wrong (" + status + ").";
                return new ParsedError(ErrorType.UNKNOWN, status, unknownMsg, serverMessage);
        }
    }

    /**
     * Call when a 401 is received. Clears token and redirects to LoginActivity.
     * Safe to call from any context (Fragment or Activity).
     */
    public static void handleAuthExpired(Context context) {
        try {
            TokenManager tm = TokenManager.getInstance(context);
            if (tm != null) tm.logout();
        } catch (Exception ignored) {}

        Intent intent = new Intent(context, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(intent);
    }
}

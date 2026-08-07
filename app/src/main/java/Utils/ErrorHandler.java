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

        ParsedError(ErrorType type, int statusCode, String message, String serverMessage) {
            this.type = type;
            this.statusCode = statusCode;
            this.message = message;
            this.serverMessage = serverMessage;
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

        try {
            String body = new String(error.networkResponse.data, "UTF-8");
            JSONObject json = new JSONObject(body);
            serverMessage = json.optString("message", null);
            if (serverMessage != null && serverMessage.isEmpty()) serverMessage = null;
        } catch (Exception ignored) {}

        Log.d(TAG, "HTTP " + status + " — " + serverMessage);

        switch (status) {
            case 401:
                return new ParsedError(ErrorType.AUTH_EXPIRED, status,
                        "Your session has expired. Please log in again.", serverMessage);
            case 429:
                String limitMsg = serverMessage != null ? serverMessage
                        : "You've reached your usage limit. Upgrade your plan for more.";
                return new ParsedError(ErrorType.RATE_LIMIT, status, limitMsg, serverMessage);
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

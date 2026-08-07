package Utils;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.provider.Settings;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.example.richhealth.R;
import com.google.android.material.snackbar.Snackbar;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import Database.DatabaseHelper;
import Models.Exercise;

public class Utilities {

    /**
     * Standard short toast for the app. Centralizes toast creation so length and behaviour
     * stay consistent everywhere — prefer this over scattered Toast.makeText(...) calls.
     * No-op if context is null (safe to call from detached fragments).
     */
    public static void toast(Context context, String message) {
        if (context == null || message == null) return;
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    /** Standard long toast (use sparingly, for messages the user needs a moment to read). */
    public static void toastLong(Context context, String message) {
        if (context == null || message == null) return;
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
    }

    public static List<Exercise> loadExercisesFromJson(Context context) {
        List<Exercise> exerciseList = new ArrayList<>();
        DatabaseHelper dbHelper = new DatabaseHelper(context);

        try {
            String json = loadJSONFromAssets(context);
            JSONObject jsonObject = new JSONObject(json);

            Iterator<String> keys = jsonObject.keys();
            while (keys.hasNext()) {
                String category = keys.next();
                JSONArray exercisesArray = jsonObject.getJSONArray(category);
                for (int i = 0; i < exercisesArray.length(); i++) {
                    JSONObject exerciseObject = exercisesArray.getJSONObject(i);
                    Exercise exercise = new Exercise(
                            exerciseObject.getInt("id"),
                            exerciseObject.getString("name"),
                            category,
                            exerciseObject.getDouble("met"),
                            exerciseObject.getString("equipment"),
                            exerciseObject.getString("difficulty"),
                            exerciseObject.getString("description")
                    );
                    exerciseList.add(exercise);

                    // Save each exercise to database
                    dbHelper.insertExercise(exercise);
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return exerciseList;
    }

    private static String loadJSONFromAssets(Context context) {
        try {
            InputStream is = context.getResources().openRawResource(R.raw.exercises);
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            is.close();
            return new String(buffer, StandardCharsets.UTF_8);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


    public static boolean isInternetAvailable(Context context) {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectivityManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network network = connectivityManager.getActiveNetwork();
                if (network == null) return false;

                NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
                return capabilities != null &&
                        (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
            } else {
                NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
                return networkInfo != null && networkInfo.isConnected();
            }
        }
        return false;
    }

    public static String getConnectionType(Context context) {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectivityManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network network = connectivityManager.getActiveNetwork();
                if (network == null) return "No Connection";

                NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
                if (capabilities != null) {
                    if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        // Check WiFi speed
                        int linkSpeed = capabilities.getLinkDownstreamBandwidthKbps();
                        if (linkSpeed > 10000) return "WiFi (Fast)";
                        else if (linkSpeed > 1000) return "WiFi (Good)";
                        else return "WiFi (Slow)";
                    } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                        return "Mobile Data";
                    } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                        return "Ethernet";
                    }
                }
            } else {
                NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
                if (networkInfo != null && networkInfo.isConnected()) {
                    int type = networkInfo.getType();
                    if (type == ConnectivityManager.TYPE_WIFI) {
                        return "WiFi";
                    } else if (type == ConnectivityManager.TYPE_MOBILE) {
                        return "Mobile Data";
                    }
                }
            }
        }
        return "No Connection";
    }

    // Quick check with automatic snackbar
    public static void checkAndShowInternetStatus(Context context, View view) {
        try {
            boolean hasInternet = isInternetAvailable(context);
            String connectionType = getConnectionType(context);

            if (!hasInternet) {
                showNoInternetSnackbar(context, view, connectionType);
            } else if (connectionType.contains("Slow")) {
                showSlowConnectionSnackbar(context, view);
            }
            // If good connection, show nothing
        } catch (SecurityException e) {
            // Permission missing - show generic message
            Snackbar.make(view, "Unable to check internet connection", Snackbar.LENGTH_SHORT).show();
        }
    }

    private static void showNoInternetSnackbar(Context context, View view, String connectionType) {
        String message;
        String actionText;

        if (connectionType.contains("WiFi")) {
            message = "No internet. Turn on WiFi or switch to mobile data";
            actionText = "WiFi Settings";
        } else if (connectionType.contains("Mobile")) {
            message = "No internet. Turn on mobile data or switch to WiFi";
            actionText = "Settings";
        } else {
            message = "Internet not available. Turn on WiFi or mobile data";
            actionText = "Settings";
        }

        Snackbar snackbar = Snackbar.make(view, message, Snackbar.LENGTH_LONG)
                .setAction(actionText, v -> {
                    Intent intent = new Intent(Settings.ACTION_WIRELESS_SETTINGS);
                    context.startActivity(intent);
                });

        // Position above bottom navigation if it exists
        View bottomNav = ((android.app.Activity) context).findViewById(R.id.bottom_navigation);
        if (bottomNav != null) {
            snackbar.setAnchorView(bottomNav);
        }

        snackbar.show();
    }

    private static void showSlowConnectionSnackbar(Context context, View view) {
        Snackbar snackbar = Snackbar.make(view, "Connection is slow. Consider switching networks", Snackbar.LENGTH_LONG)
                .setAction("Settings", v -> {
                    Intent intent = new Intent(Settings.ACTION_WIRELESS_SETTINGS);
                    context.startActivity(intent);
                });

        // Position above bottom navigation if it exists
        View bottomNav = ((android.app.Activity) context).findViewById(R.id.bottom_navigation);
        if (bottomNav != null) {
            snackbar.setAnchorView(bottomNav);
        }

        snackbar.show();
    }

}
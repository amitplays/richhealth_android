package com.example.richhealth;

import android.app.Activity;
import android.app.Application;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bugsee.library.Bugsee;

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Bugsee.launch(this, "8cca0475-6801-4b15-b530-2730597b83b0");

        // ── Edge-to-edge inset handling (core fix, applied once for the whole app) ──
        // At targetSdk 35+ edge-to-edge is enforced: android:statusBarColor /
        // navigationBarColor are ignored and every screen draws under the system bars
        // (header slides under the status bar, the bottom nav is swallowed by the gesture
        // bar). Instead of padding each fragment/activity, we consume the system-bar insets
        // as padding on every Activity's content view here — one place, covers all screens.
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
                // Guarantee resize-on-keyboard for every screen, so inputs are never hidden
                // behind the IME. Under edge-to-edge (targetSdk 35+) the framework no longer
                // auto-resizes; we still request it and consume the IME inset below.
                if (activity.getWindow() != null) {
                    activity.getWindow().setSoftInputMode(
                            android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
                }
                final View content = activity.findViewById(android.R.id.content);
                if (content == null) return;
                // Keep the system-bar areas black (matches the app's old #000000 bars).
                content.setBackgroundColor(Color.BLACK);
                ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
                    Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
                    // Bottom padding = whichever is taller, the nav bar or the keyboard. When
                    // the IME opens this grows to the keyboard height, lifting the content
                    // (and any input box) above it. Edge-to-edge won't do this for us.
                    int bottom = Math.max(bars.bottom, ime.bottom);
                    v.setPadding(bars.left, bars.top, bars.right, bottom);
                    return insets;
                });
                ViewCompat.requestApplyInsets(content);
            }

            @Override public void onActivityStarted(@NonNull Activity activity) {}
            @Override public void onActivityResumed(@NonNull Activity activity) {}
            @Override public void onActivityPaused(@NonNull Activity activity) {}
            @Override public void onActivityStopped(@NonNull Activity activity) {}
            @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}
            @Override public void onActivityDestroyed(@NonNull Activity activity) {}
        });
    }
}

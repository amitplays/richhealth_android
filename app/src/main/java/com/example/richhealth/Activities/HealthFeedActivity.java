package com.example.richhealth.Activities;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.richhealth.R;

/**
 * Standalone "Health Intel" screen, opened from Home → "See all".
 *
 * It now simply HOSTS {@link HealthFeedFragment} instead of duplicating the whole
 * feed (adapter, local data, mini-player). This guarantees the Home entry point and
 * the Services → Feed tab show the exact same thing: the backend-driven, shared,
 * personalized pool with the redesigned source-led cards. One implementation, no drift.
 */
public class HealthFeedActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_health_feed);

        View back = findViewById(R.id.back_button);
        if (back != null) back.setOnClickListener(v -> finish());

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.feed_fragment_container, new HealthFeedFragment())
                    .commit();
        }
    }
}

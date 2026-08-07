package com.example.richhealth.Activities;

import android.os.Bundle;
import android.widget.ImageButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.example.richhealth.R;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class NotificationsActivity extends AppCompatActivity {

    private final String[] tabTitles = {"Requests", "Activity"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        ImageButton backButton = findViewById(R.id.back_button);
        backButton.setOnClickListener(v -> finish());

        TabLayout tabLayout = findViewById(R.id.notification_tabs);
        ViewPager2 viewPager = findViewById(R.id.notification_viewpager);

        viewPager.setAdapter(new NotificationPagerAdapter(this));

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) ->
                tab.setText(tabTitles[position])
        ).attach();
    }

    private class NotificationPagerAdapter extends FragmentStateAdapter {

        public NotificationPagerAdapter(FragmentActivity fragmentActivity) {
            super(fragmentActivity);
        }

        @Override
        public int getItemCount() {
            return tabTitles.length;
        }

        @Override
        public Fragment createFragment(int position) {
            if (position == 0) {
                return new RequestsFragment();
            } else {
                return new ActivityTimelineFragment();
            }
        }
    }
}

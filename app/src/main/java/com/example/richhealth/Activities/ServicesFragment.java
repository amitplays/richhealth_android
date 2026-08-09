package com.example.richhealth.Activities;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.example.richhealth.R;

/**
 * Host for the "Services" tab. Shows a two-tab pill switcher:
 *   Feed  → HealthFeedFragment (Health Intel), shown first / by default
 *   Tools → HomeFragment (all the health tools / service cards)
 *
 * Both children are added once and toggled with show/hide so each keeps its
 * state when switching. Implements BackPressHandler so back on this tab first
 * lets the active child consume it, then returns Tools → Feed before the
 * activity falls back to the landing (Richie) tab.
 */
public class ServicesFragment extends Fragment implements BackPressHandler {

    private View tabFeed, tabTools;
    private TextView tabLabelFeed, tabLabelTools;
    private android.widget.ImageView tabIconFeed, tabIconTools;
    private HealthFeedFragment feedFragment;
    private HomeFragment toolsFragment;
    private int currentTab = 0; // 0 = Feed, 1 = Tools

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_services_host, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tabFeed = view.findViewById(R.id.tab_feed);
        tabTools = view.findViewById(R.id.tab_tools);
        tabLabelFeed = view.findViewById(R.id.tab_label_feed);
        tabLabelTools = view.findViewById(R.id.tab_label_tools);
        tabIconFeed = view.findViewById(R.id.tab_icon_feed);
        tabIconTools = view.findViewById(R.id.tab_icon_tools);
        tabFeed.setOnClickListener(v -> selectTab(0));
        tabTools.setOnClickListener(v -> selectTab(1));

        if (savedInstanceState == null) {
            feedFragment = new HealthFeedFragment();
            toolsFragment = new HomeFragment();
            getChildFragmentManager().beginTransaction()
                    .add(R.id.services_container, feedFragment, "feed")
                    .add(R.id.services_container, toolsFragment, "tools")
                    .hide(toolsFragment)
                    .commit();
            currentTab = 0;
        } else {
            feedFragment = (HealthFeedFragment) getChildFragmentManager().findFragmentByTag("feed");
            toolsFragment = (HomeFragment) getChildFragmentManager().findFragmentByTag("tools");
            currentTab = savedInstanceState.getInt("current_tab", 0);
        }

        selectTab(currentTab);
    }

    /** Public entry so child tools (e.g. the Health Feed card) can jump to the Feed tab. */
    public void showFeedTab() {
        selectTab(0);
    }

    private void selectTab(int index) {
        currentTab = index;
        if (feedFragment != null && toolsFragment != null && isAdded()) {
            FragmentTransaction t = getChildFragmentManager().beginTransaction();
            if (index == 0) {
                t.show(feedFragment).hide(toolsFragment);
            } else {
                t.show(toolsFragment).hide(feedFragment);
            }
            t.commitAllowingStateLoss();
        }
        styleTab(tabFeed, tabLabelFeed, tabIconFeed, index == 0);
        styleTab(tabTools, tabLabelTools, tabIconTools, index == 1);
    }

    private void styleTab(View container, TextView label, android.widget.ImageView icon, boolean selected) {
        if (container == null) return;
        container.setBackgroundResource(selected ? R.drawable.pill_tab_selected : R.drawable.pill_tab_unselected);
        int color = selected ? Color.WHITE : Color.parseColor("#AAAAAA");
        if (label != null) label.setTextColor(color);
        if (icon != null) icon.setColorFilter(color);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("current_tab", currentTab);
    }

    @Override
    public boolean handleBackPress() {
        // 1) Let the active child (chat panels, etc.) consume the back press.
        Fragment active = currentTab == 1 ? toolsFragment : feedFragment;
        if (active instanceof BackPressHandler && ((BackPressHandler) active).handleBackPress()) {
            return true;
        }
        // 2) If we're on Tools, returning to Feed is the natural "up" step.
        if (currentTab != 0) {
            selectTab(0);
            return true;
        }
        // 3) On Feed with nothing to close — let the activity handle exit.
        return false;
    }
}

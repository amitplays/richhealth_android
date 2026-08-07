package com.example.richhealth.Activities;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.richhealth.R;

import java.util.ArrayList;
import java.util.List;

import Utils.FoodDialogUtils;

public class DietaryListFragment extends Fragment {

    private static final String ARG_TAB_POSITION = "tab_position";
    private int tabPosition;
    private RecyclerView foodRecyclerView;
    private TextView disclaimerText;
    private LinearLayout emptyState;
    private TextView emptyTitle;
    private TextView emptySubtitle;
    private LinearLayout tableHeader;
    private FoodDialogUtils.FoodInfoAdapter adapter;

    public static DietaryListFragment newInstance(int position) {
        DietaryListFragment fragment = new DietaryListFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_TAB_POSITION, position);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            tabPosition = getArguments().getInt(ARG_TAB_POSITION, 0);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_dietary_list, container, false);

        foodRecyclerView = view.findViewById(R.id.food_recycler_view);
        disclaimerText = view.findViewById(R.id.disclaimer_text);
        emptyState = view.findViewById(R.id.dietary_empty_state);
        emptyTitle = view.findViewById(R.id.dietary_empty_title);
        emptySubtitle = view.findViewById(R.id.dietary_empty_subtitle);
        tableHeader = view.findViewById(R.id.dietary_table_header);

        foodRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new FoodDialogUtils.FoodInfoAdapter(new ArrayList<>());
        foodRecyclerView.setAdapter(adapter);

        // Set disclaimer based on tab
        if (tabPosition == 0) {
            disclaimerText.setText("Disclaimer: This information is general guidance. Always consult with a healthcare professional for personalized nutrition advice.");
        } else {
            disclaimerText.setText("Disclaimer: This information is general guidance. Always consult with a healthcare professional before making dietary changes.");
        }

        // Load data if activity already has it
        refreshData();

        return view;
    }

    public void refreshData() {
        if (!(getActivity() instanceof DietaryInsightsActivity) || adapter == null) return;

        DietaryInsightsActivity activity = (DietaryInsightsActivity) getActivity();
        DietaryInsightsActivity.DataState state = activity.getDataState();

        if (state == DietaryInsightsActivity.DataState.LOADING) {
            // Still waiting — show nothing
            return;
        }

        List<FoodDialogUtils.FoodItem> items = tabPosition == 0
                ? activity.getFoodsToEat()
                : activity.getFoodsToAvoid();

        if (state == DietaryInsightsActivity.DataState.LOADED && items != null && !items.isEmpty()) {
            showList(items);
        } else if (state == DietaryInsightsActivity.DataState.ERROR) {
            showEmptyState(
                "Unable to load recommendations",
                "Check your connection and try again"
            );
        } else {
            // LOADED but empty list
            showEmptyState(
                "No recommendations yet",
                "Complete your health profile to get personalized dietary guidance"
            );
        }
    }

    private void showList(List<FoodDialogUtils.FoodItem> items) {
        if (emptyState != null) emptyState.setVisibility(View.GONE);
        if (tableHeader != null) tableHeader.setVisibility(View.VISIBLE);
        foodRecyclerView.setVisibility(View.VISIBLE);
        if (disclaimerText != null) disclaimerText.setVisibility(View.VISIBLE);
        adapter.updateItems(items);
    }

    private void showEmptyState(String title, String subtitle) {
        if (tableHeader != null) tableHeader.setVisibility(View.GONE);
        foodRecyclerView.setVisibility(View.GONE);
        if (disclaimerText != null) disclaimerText.setVisibility(View.GONE);
        if (emptyTitle != null) emptyTitle.setText(title);
        if (emptySubtitle != null) emptySubtitle.setText(subtitle);
        if (emptyState != null) emptyState.setVisibility(View.VISIBLE);
    }
}

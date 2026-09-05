package com.example.richhealth.Activities;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.richhealth.R;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import Models.SelectableOption;

public class SelectableCardAdapter extends RecyclerView.Adapter<SelectableCardAdapter.CardViewHolder> {

    public interface OnSelectionChangedListener {
        void onSelectionChanged();
    }

    private final List<SelectableOption> options;
    private final boolean multiSelect;
    private final Set<Integer> selectedPositions = new HashSet<>();
    private OnSelectionChangedListener listener;
    // Position of a "clear all others when selected" item (e.g. "None of the above")
    private int clearOthersPosition = -1;

    public SelectableCardAdapter(List<SelectableOption> options, boolean multiSelect) {
        this.options = options;
        this.multiSelect = multiSelect;
    }

    public void setClearOthersPosition(int position) {
        this.clearOthersPosition = position;
    }

    public void setOnSelectionChangedListener(OnSelectionChangedListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public CardViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_onboarding_card, parent, false);
        return new CardViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CardViewHolder holder, int position) {
        SelectableOption option = options.get(position);
        holder.tvLabel.setText(option.displayLabel);

        if (option.hasIcon()) {
            holder.ivIcon.setImageResource(option.iconRes);
            holder.ivIcon.setVisibility(View.VISIBLE);
            holder.tvEmoji.setVisibility(View.GONE);
        } else {
            holder.tvEmoji.setText(option.emoji);
            holder.tvEmoji.setVisibility(View.VISIBLE);
            holder.ivIcon.setVisibility(View.GONE);
        }

        boolean isSelected = selectedPositions.contains(position);
        applyCardState(holder.card, isSelected);
        if (option.hasIcon()) {
            holder.ivIcon.setImageTintList(
                    ColorStateList.valueOf(Color.parseColor("#008b8b")));
        }

        holder.card.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos == RecyclerView.NO_ID) return;

            SelectableOption opt = options.get(pos);

            // Subtle tap pulse — matches app animation language (200ms decelerate)
            v.animate()
                    .scaleX(0.96f).scaleY(0.96f)
                    .setDuration(90)
                    .withEndAction(() -> v.animate()
                            .scaleX(1f).scaleY(1f)
                            .setDuration(110)
                            .start())
                    .start();

            if (opt.isOther) {
                String title = "Tell us — " + opt.label;
                OtherInputDialog.show(v.getContext(), title, "Type here", opt.otherText, text -> {
                    opt.otherText = text;
                    opt.displayLabel = text;
                    applyTapSelection(pos);
                });
                return;
            }

            applyTapSelection(pos);
        });
    }

    private void applyTapSelection(int pos) {
        if (!multiSelect) {
            selectedPositions.clear();
            selectedPositions.add(pos);
        } else {
            if (pos == clearOthersPosition) {
                selectedPositions.clear();
                selectedPositions.add(pos);
            } else {
                selectedPositions.remove(clearOthersPosition);
                if (selectedPositions.contains(pos)) {
                    selectedPositions.remove(pos);
                } else {
                    selectedPositions.add(pos);
                }
            }
        }

        notifyDataSetChanged();
        if (listener != null) listener.onSelectionChanged();
    }

    private void applyCardState(MaterialCardView card, boolean selected) {
        if (selected) {
            card.setCardBackgroundColor(Color.parseColor("#0A2828"));
            card.setStrokeColor(Color.parseColor("#008b8b"));
            card.setStrokeWidth(3);
        } else {
            card.setCardBackgroundColor(Color.parseColor("#1E1E1E"));
            card.setStrokeColor(Color.parseColor("#2A2A2A"));
            card.setStrokeWidth(1);
        }
    }

    /**
     * Pre-select the option whose value matches, for restoring a saved answer when the
     * user steps back. Does not fire the selection listener — restoring is not a user
     * action, and the host reads getSelectedValue() when it saves.
     */
    public void setSelectedValue(Object value) {
        if (value == null) return;
        setSelectedValues(java.util.Collections.singletonList(value));
    }

    /**
     * Restore a whole selection, for multi-select sections. Counterpart to
     * {@link #getSelectedValues()}. Replaces the current selection rather than adding to
     * it, so a restore can never leave a stale card lit; values with no matching option
     * (an options list rebuilt since the snapshot) are simply skipped. Like
     * setSelectedValue this does NOT fire the selection listener — restoring is not a
     * user action.
     */
    public void setSelectedValues(List<?> values) {
        if (values == null) return;
        selectedPositions.clear();
        for (Object value : values) {
            if (value == null) continue;
            for (int i = 0; i < options.size(); i++) {
                if (value.equals(valueFor(options.get(i)))) {
                    selectedPositions.add(i);
                    // Single-select sections hold at most one, so stop at the first match.
                    if (!multiSelect) { notifyDataSetChanged(); return; }
                    break;
                }
            }
        }
        notifyDataSetChanged();
    }

    public boolean hasSelection() {
        return !selectedPositions.isEmpty();
    }

    /** Returns the value of the first (or only) selected option, or null. */
    public Object getSelectedValue() {
        if (selectedPositions.isEmpty()) return null;
        return valueFor(options.get(selectedPositions.iterator().next()));
    }

    /** Returns all selected values for multi-select adapters. */
    public List<Object> getSelectedValues() {
        List<Object> values = new ArrayList<>();
        for (int pos : selectedPositions) {
            values.add(valueFor(options.get(pos)));
        }
        return values;
    }

    /** For Other options, the submitted text overrides the placeholder value. */
    private Object valueFor(SelectableOption opt) {
        if (opt.isOther && opt.otherText != null && !opt.otherText.isEmpty()) {
            return opt.otherText;
        }
        return opt.value;
    }

    @Override
    public int getItemCount() {
        return options.size();
    }

    static class CardViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView card;
        ImageView ivIcon;
        TextView tvEmoji;
        TextView tvLabel;

        CardViewHolder(@NonNull View itemView) {
            super(itemView);
            card = (MaterialCardView) itemView;
            ivIcon = itemView.findViewById(R.id.iv_card_icon);
            tvEmoji = itemView.findViewById(R.id.tv_card_emoji);
            tvLabel = itemView.findViewById(R.id.tv_card_label);
        }
    }
}

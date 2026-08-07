package Models;

import androidx.annotation.DrawableRes;

public class SelectableOption {
    public final String label;
    public final String emoji;
    @DrawableRes
    public final int iconRes;
    public final Object value;
    public final boolean fullWidth;

    /**
     * "Other — you tell us" option. When true, tapping the card opens a branded
     * input dialog; the typed text replaces {@link #displayLabel} and becomes
     * the option's {@link #value} (single-select) or is added to the selection
     * set (multi-select).
     */
    public final boolean isOther;

    /** Mutable override for the card label — used by the Other input flow. */
    public String displayLabel;
    /** Mutable free-text captured from the Other dialog. */
    public String otherText = "";

    // ── Emoji-based (legacy) ─────────────────────────────────────────────────
    public SelectableOption(String label, String emoji, Object value) {
        this(label, emoji, 0, value, false, false);
    }

    public SelectableOption(String label, String emoji, Object value, boolean fullWidth) {
        this(label, emoji, 0, value, fullWidth, false);
    }

    // ── Icon-based ───────────────────────────────────────────────────────────
    public SelectableOption(String label, @DrawableRes int iconRes, Object value) {
        this(label, "", iconRes, value, false, false);
    }

    public SelectableOption(String label, @DrawableRes int iconRes, Object value, boolean fullWidth) {
        this(label, "", iconRes, value, fullWidth, false);
    }

    // ── Full ─────────────────────────────────────────────────────────────────
    public SelectableOption(String label, String emoji, @DrawableRes int iconRes,
                            Object value, boolean fullWidth) {
        this(label, emoji, iconRes, value, fullWidth, false);
    }

    public SelectableOption(String label, String emoji, @DrawableRes int iconRes,
                            Object value, boolean fullWidth, boolean isOther) {
        this.label = label;
        this.emoji = emoji;
        this.iconRes = iconRes;
        this.value = value;
        this.fullWidth = fullWidth;
        this.isOther = isOther;
        this.displayLabel = label;
    }

    // ── Factory for "Other — you tell us" cards ──────────────────────────────
    public static SelectableOption other(String label, @DrawableRes int iconRes) {
        return new SelectableOption(label, "", iconRes, /* value */ "", false, true);
    }

    public static SelectableOption other(String label, @DrawableRes int iconRes, boolean fullWidth) {
        return new SelectableOption(label, "", iconRes, /* value */ "", fullWidth, true);
    }

    public boolean hasIcon() {
        return iconRes != 0;
    }
}

package Utils;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;

import com.example.richhealth.R;
import com.google.android.material.card.MaterialCardView;

/**
 * Single reusable "Services / Tools" card.
 *
 * Implements the one TOOLS CARD STANDARD used across the Tools screen so every
 * card is laid out identically:
 *   [ icon 30dp ] [ teal title + top-right status pill / subtitle / meta date ] [ chevron ]
 *
 * The whole card surface is the click target (it IS a MaterialCardView), so
 * callers just use {@link #setOnClickListener}. Runtime setters are the primary
 * API; optional XML attrs (serviceIcon / serviceTitle / serviceSubtitle) let the
 * static cards declare their look inline.
 *
 * Colours come from @color/rh_* resources — the chevron is tinted per
 * {@link ChevronStatus} to mirror the iOS status-coloured chevron contract.
 */
public class ServiceCardView extends MaterialCardView {

    /** Chevron colour semantics (precedence handled by the caller): URGENT > ATTENTION > NORMAL. */
    public enum ChevronStatus { NORMAL, ATTENTION, URGENT }

    private ImageView iconView;
    private TextView titleView;
    private TextView subtitleView;
    private TextView pillView;
    private TextView metaView;
    private ImageView chevronView;
    /** Photo variant (Services redesign): photo bg + scrim + glass stat pill. */
    private boolean photoMode = false;

    public ServiceCardView(@NonNull Context context) {
        super(context);
        init(context, null);
    }

    public ServiceCardView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public ServiceCardView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(@NonNull Context context, @Nullable AttributeSet attrs) {
        // TOOLS CARD STANDARD chrome: corner 18dp, no elevation, translucent surface.
        setRadius(dp(18));
        setCardElevation(0f);
        setCardBackgroundColor(ContextCompat.getColor(context, R.color.rh_surface));
        setClickable(true);
        setFocusable(true);
        setForeground(resolveSelectableForeground());

        // Photo variant? Peek at the attrs BEFORE inflating so the right layout loads.
        int photoRes = 0; boolean hero = false; CharSequence statLabel = null;
        if (attrs != null) {
            TypedArray pa = context.obtainStyledAttributes(attrs, R.styleable.ServiceCardView);
            photoRes = pa.getResourceId(R.styleable.ServiceCardView_servicePhoto, 0);
            hero = pa.getBoolean(R.styleable.ServiceCardView_serviceHero, false);
            statLabel = pa.getText(R.styleable.ServiceCardView_serviceStatLabel);
            pa.recycle();
        }
        photoMode = photoRes != 0;

        LayoutInflater.from(context).inflate(
                photoMode ? R.layout.view_service_card_photo : R.layout.view_service_card,
                this, true);

        if (photoMode) {
            ImageView photoView = findViewById(R.id.service_card_photo);
            if (photoView != null) photoView.setImageResource(photoRes);
            View root = findViewById(R.id.service_card_photo_root);
            if (root != null) root.setMinimumHeight((int) dp(hero ? 400 : 132));
            TextView labelView = findViewById(R.id.service_card_stat_label);
            if (labelView != null) {
                if (statLabel != null && statLabel.length() > 0) labelView.setText(statLabel);
                else labelView.setVisibility(View.GONE);
            }
            // Card ground behind the photo edges stays black (photo + scrim carry the look).
            setCardBackgroundColor(0xFF000000);
        }

        iconView = findViewById(R.id.service_card_icon);
        titleView = findViewById(R.id.service_card_title);
        subtitleView = findViewById(R.id.service_card_subtitle);
        pillView = findViewById(R.id.service_card_pill);
        metaView = findViewById(R.id.service_card_meta);
        chevronView = findViewById(R.id.service_card_chevron);

        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ServiceCardView);
            int icon = a.getResourceId(R.styleable.ServiceCardView_serviceIcon, 0);
            if (icon != 0) setIcon(icon);
            CharSequence title = a.getText(R.styleable.ServiceCardView_serviceTitle);
            if (title != null) setTitle(title);
            CharSequence subtitle = a.getText(R.styleable.ServiceCardView_serviceSubtitle);
            if (subtitle != null) setSubtitle(subtitle);
            boolean hero2 = a.getBoolean(R.styleable.ServiceCardView_serviceHero, false);
            if (photoMode && !hero2 && titleView != null) {
                titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f);
            }
            a.recycle();
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void setIcon(@DrawableRes int res) {
        if (iconView != null) iconView.setImageResource(res);
    }

    public void setTitle(CharSequence title) {
        if (titleView != null) titleView.setText(title);
    }

    public void setSubtitle(CharSequence subtitle) {
        if (subtitleView != null) subtitleView.setText(subtitle);
    }

    /** Set + show the top-right status pill via the shared StatusPill semantics. */
    public void setPill(@NonNull StatusPill.Intent intent, CharSequence text) {
        if (pillView == null) return;
        pillView.setBackgroundTintList(null);
        StatusPill.apply(pillView, intent, text);
        if (photoMode) {
            // Glass stat pill: the container carries the background — the value line is
            // plain coloured text (iOS parity), so drop the pill chip chrome.
            pillView.setBackground(null);
            pillView.setPadding(0, 0, 0, 0);
            int c = StatusPill.backgroundColor(getContext(), intent);
            // NEUTRAL's grey chip colour is illegible as text on the scrim → white.
            pillView.setTextColor(intent == StatusPill.Intent.NEUTRAL ? 0xFFFFFFFF : c);
        }
    }

    public void hidePill() {
        if (pillView != null) {
            pillView.setVisibility(View.GONE);
            pillView.setOnClickListener(null);
        }
    }

    /** Set + show the meta "Updated/Checked X ago" line. */
    public void setDate(CharSequence date) {
        if (metaView == null) return;
        metaView.setText(date);
        metaView.setVisibility(View.VISIBLE);
    }

    public void hideDate() {
        if (metaView != null) metaView.setVisibility(View.GONE);
    }

    /** Tint the trailing chevron per status: NORMAL=tertiary, ATTENTION=warning, URGENT=danger. */
    public void setChevronStatus(@NonNull ChevronStatus status) {
        if (chevronView == null) return;
        int colorRes;
        switch (status) {
            case URGENT:    colorRes = R.color.rh_danger; break;
            case ATTENTION: colorRes = R.color.rh_warning; break;
            case NORMAL:
            default:        colorRes = R.color.rh_text_tertiary; break;
        }
        ImageViewCompat.setImageTintList(chevronView,
                ColorStateList.valueOf(ContextCompat.getColor(getContext(), colorRes)));
    }

    // ── Escape hatches: let callers attach behaviour to inner views (e.g. the
    //    pill's stale-info dialog) while keeping all styling centralised here. ──

    public TextView getPillView() { return pillView; }
    public TextView getMetaView() { return metaView; }
    public TextView getSubtitleView() { return subtitleView; }
    public TextView getTitleView() { return titleView; }
    public ImageView getIconView() { return iconView; }
    public ImageView getChevronView() { return chevronView; }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private float dp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }

    private android.graphics.drawable.Drawable resolveSelectableForeground() {
        TypedValue outValue = new TypedValue();
        getContext().getTheme().resolveAttribute(
                android.R.attr.selectableItemBackground, outValue, true);
        return ContextCompat.getDrawable(getContext(), outValue.resourceId);
    }
}

package Utils;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;

import com.example.richhealth.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

/**
 * Reusable info dialog for explaining what a Health Hub card does.
 * Mirrors the visual language of ProUpgradeDialog: rotating launcher logo, teal stroke,
 * encrypted footer chip. Use as a tap target on the (i) icon of any card.
 */
public class CardInfoDialog {

    private final Context context;
    private final String title;
    private final String subtitle;
    private final String body;
    private final String[] bullets;
    private final int iconRes;

    private Dialog dialog;

    private CardInfoDialog(Builder b) {
        this.context = b.context;
        this.title = b.title;
        this.subtitle = b.subtitle;
        this.body = b.body;
        this.bullets = b.bullets;
        this.iconRes = b.iconRes;
    }

    public void show() {
        dialog = new Dialog(context, R.style.DialogTheme);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_card_info);

        Window w = dialog.getWindow();
        if (w != null) {
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
            lp.copyFrom(w.getAttributes());
            lp.width = WindowManager.LayoutParams.MATCH_PARENT;
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
            w.setAttributes(lp);
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        TextView titleView    = dialog.findViewById(R.id.info_dialog_title);
        TextView subtitleView = dialog.findViewById(R.id.info_dialog_subtitle);
        TextView bodyView     = dialog.findViewById(R.id.info_dialog_body);
        LinearLayout bulletsContainer = dialog.findViewById(R.id.info_dialog_bullets);
        MaterialButton button = dialog.findViewById(R.id.info_dialog_button);

        titleView.setText(title);
        subtitleView.setText(subtitle != null ? subtitle : "Feature overview");
        bodyView.setText(body);

        if (bullets != null) {
            for (String b : bullets) {
                bulletsContainer.addView(buildBullet(b));
            }
        }

        button.setOnClickListener(v -> dialog.dismiss());

        // The card's own icon — static (no rotation; rotation read as glitchy on a small icon).
        ImageView logo = dialog.findViewById(R.id.info_dialog_logo);
        if (logo != null) {
            logo.setImageResource(iconRes);
            logo.setRotation(0f);
        }

        // Teal-tinted shadow (matches upgrade dialog).
        MaterialCardView card = dialog.findViewById(R.id.info_dialog_card);
        if (android.os.Build.VERSION.SDK_INT >= 28 && card != null) {
            card.setOutlineAmbientShadowColor(Color.parseColor("#40008b8b"));
            card.setOutlineSpotShadowColor(Color.parseColor("#60008b8b"));
        }

        dialog.show();
    }

    private View buildBullet(String text) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.bottomMargin = dp(6);
        row.setLayoutParams(rowLp);

        TextView dot = new TextView(context);
        dot.setText("•");
        dot.setTextColor(Color.parseColor("#008b8b"));
        dot.setTextSize(14f);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        dotLp.rightMargin = dp(10);
        dot.setLayoutParams(dotLp);

        TextView t = new TextView(context);
        t.setText(text);
        t.setTextColor(Color.parseColor("#A8A8A8"));
        t.setTextSize(13f);
        t.setLineSpacing(dp(2), 1f);

        row.addView(dot);
        row.addView(t);
        return row;
    }

    private int dp(int v) {
        return (int) (v * context.getResources().getDisplayMetrics().density);
    }

    public static class Builder {
        private final Context context;
        private String title;
        private String subtitle;
        private String body;
        private String[] bullets;
        @DrawableRes private int iconRes;

        public Builder(Context context) { this.context = context; }
        public Builder title(String v)    { this.title = v; return this; }
        public Builder subtitle(String v) { this.subtitle = v; return this; }
        public Builder body(String v)     { this.body = v; return this; }
        public Builder bullets(String... v) { this.bullets = v; return this; }
        public Builder icon(@DrawableRes int v) { this.iconRes = v; return this; }
        public CardInfoDialog build() { return new CardInfoDialog(this); }
    }
}

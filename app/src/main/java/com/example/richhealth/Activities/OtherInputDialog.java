package com.example.richhealth.Activities;
import Utils.Utilities;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import com.example.richhealth.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

/**
 * Branded single free-text input dialog used by "Other — you tell us" cards
 * across signup. Uses the same layout/styling as dialog_add_symptom and
 * dialog_add_medication — MaterialCardView wrapper, teal title, outlined
 * text input with dark box, Cancel/Save MaterialButtons.
 */
public final class OtherInputDialog {

    public interface Callback {
        void onSubmit(String text);
    }

    private OtherInputDialog() {}

    public static void show(Context ctx, String title, String subtitle,
                            String hint, String prefillText, Callback cb) {

        Dialog dialog = new Dialog(ctx, R.style.DialogTheme);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_other_input);

        // App-standard dialog window (92% width, wrap height, bounded scroll).
        Utils.DialogUtils.applyStandardEditDialogWindow(dialog);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }

        TextView tvTitle    = dialog.findViewById(R.id.dialog_title);
        TextView tvSubtitle = dialog.findViewById(R.id.dialog_subtitle);
        TextInputEditText input = dialog.findViewById(R.id.input_text);
        MaterialButton btnCancel = dialog.findViewById(R.id.btn_cancel);
        MaterialButton btnSave   = dialog.findViewById(R.id.btn_save);

        tvTitle.setText(title);
        if (subtitle != null && !subtitle.isEmpty()) {
            tvSubtitle.setText(subtitle);
        }
        if (hint != null) input.setHint(hint);
        if (prefillText != null && !prefillText.isEmpty()) {
            input.setText(prefillText);
            input.setSelection(prefillText.length());
        }
        input.requestFocus();

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            String text = input.getText() != null ? input.getText().toString().trim() : "";
            if (text.isEmpty()) {
                Utilities.toast(ctx, "Please type something");
                return;
            }
            cb.onSubmit(text);
            dialog.dismiss();
        });

        dialog.show();
    }

    /** Convenience overload without an explicit subtitle. */
    public static void show(Context ctx, String title, String hint,
                            String prefillText, Callback cb) {
        show(ctx, title, "Type your answer below", hint, prefillText, cb);
    }
}

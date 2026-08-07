package Utils;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.util.Log;
import android.util.TypedValue;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Calendar;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.example.richhealth.Activities.TokenManager;
import com.example.richhealth.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import Models.AQIData;
public class DialogUtils {

    public static class DialogField {
        private String key;
        private String label;
        private int inputType;
        private String[] options;
        private String defaultValue;
        private boolean isDropdown;
        private boolean isSection;
        private boolean isDate;
        private boolean isMultiSelect;

        // Constructor for text fields
        public DialogField(String key, String label, int inputType, String defaultValue) {
            this.key = key;
            this.label = label;
            this.inputType = inputType;
            this.defaultValue = defaultValue;
            this.isDropdown = false;
        }

        // Constructor for dropdown fields
        public DialogField(String key, String label, String[] options, String defaultValue) {
            this.key = key;
            this.label = label;
            this.options = options;
            this.defaultValue = defaultValue;
            this.isDropdown = true;
        }

        // Private constructor for section headers
        private DialogField(String sectionTitle) {
            this.label = sectionTitle;
            this.isSection = true;
        }

        /**
         * Creates a non-input section header row. Groups the long edit form into
         * scannable sections. Ignored by the save loop (no key / no value).
         */
        public static DialogField section(String title) {
            return new DialogField(title);
        }

        /**
         * Creates a tap-to-open native date-picker field. Stores/returns the value
         * as an ISO {@code yyyy-MM-dd} string (matching the profile payload format).
         */
        public static DialogField date(String key, String label, String defaultValue) {
            DialogField f = new DialogField(key, label, InputType.TYPE_NULL, defaultValue);
            f.isDropdown = false;
            f.isDate = true;
            return f;
        }

        /**
         * Searchable multi-select field: type to filter {@code options}, tap or press
         * Done to add a removable chip. Custom (typed) values are allowed too. Stores
         * and returns the selection as a comma-separated string, so it round-trips
         * through the same save loop as a plain text field.
         */
        public static DialogField multiSelect(String key, String label, String[] options, String defaultValue) {
            DialogField f = new DialogField(key, label, InputType.TYPE_NULL, defaultValue);
            f.isDropdown = false;
            f.options = options;
            f.isMultiSelect = true;
            return f;
        }

        public String getKey() { return key; }
        public String getLabel() { return label; }
        public int getInputType() { return inputType; }
        public String[] getOptions() { return options; }
        public String getDefaultValue() { return defaultValue; }
        public boolean isDropdown() { return isDropdown; }
        public boolean isSection() { return isSection; }
        public boolean isDate() { return isDate; }
        public boolean isMultiSelect() { return isMultiSelect; }
    }

    /** Callback for the app-standard single-choice picker. */
    public interface OnChoiceListener { void onChoice(String value); }

    public interface OnDialogSubmitListener {
        void onSubmit(Map<String, String> values) throws Exception;
    }

    public interface OnDialogActionListener {
        void onAction();
    }

    // App accent used for section headers and to flag changed-but-unsaved values.
    private static final int DIALOG_ACCENT_COLOR = Color.parseColor("#008b8b");

    /** Colour a field value in the accent when it differs from its saved original. */
    private static void applyChangedTint(TextView view, String original, int normalColor) {
        String cur = view.getText() == null ? "" : view.getText().toString();
        boolean changed = !cur.equals(original == null ? "" : original);
        view.setTextColor(changed ? DIALOG_ACCENT_COLOR : normalColor);
    }

    // ─── APP DIALOG DESIGN STANDARD ────────────────────────────────────────────
    // The profile "edit" dialog (R.layout.dialog_edit_profile) is the canonical
    // look for every edit/add dialog in the app. Reference points:
    //   • Surface  : @drawable/bg_dialog_surface (#141414, 22dp radius, #2A2A2A stroke)
    //   • Title    : BLUE accent (@color/rh_accent), bold, 18sp, left-aligned
    //   • Structure: title → 1dp #2A2A2A divider → bounded ScrollView(id=fields_scroll)
    //                → 1dp #2A2A2A divider → footer (Cancel text btn + accent Save)
    //   • Field row: label takes ~35-40% width, input fills the rest
    //                (@drawable/bg_dialog_input). See dialog_field_input.xml.
    //   • Section/category headers use the blue accent — but ONLY the profile form
    //     is grouped into categories; most dialogs have none, so don't add them.
    // Custom controls (SeekBar, Switch, dropdowns) keep their component but sit
    // inside this shell. Call applyStandardEditDialogWindow(dialog) after
    // setContentView so every dialog gets the same 92%-width, wrap-height,
    // bounded-scroll window as the profile dialog.
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Applies the app-standard edit-dialog window: 92% screen width, wrap height,
     * transparent background, and the field scroll bounded to ~62% of the screen
     * so tall forms scroll instead of overflowing (same behaviour as the profile
     * edit dialog). The inflated layout must expose a ScrollView with the id
     * {@code fields_scroll} for the scroll bound to take effect.
     */
    public static void applyStandardEditDialogWindow(Dialog dialog) {
        if (dialog == null) return;
        final Context context = dialog.getContext();
        if (dialog.getWindow() != null) {
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
            lp.copyFrom(dialog.getWindow().getAttributes());
            lp.width = (int) (context.getResources().getDisplayMetrics().widthPixels * 0.92);
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
            dialog.getWindow().setAttributes(lp);
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        final View scroll = dialog.findViewById(R.id.fields_scroll);
        if (scroll != null) {
            final int maxH = (int) (context.getResources().getDisplayMetrics().heightPixels * 0.62);
            scroll.getViewTreeObserver().addOnPreDrawListener(
                    new android.view.ViewTreeObserver.OnPreDrawListener() {
                        @Override public boolean onPreDraw() {
                            scroll.getViewTreeObserver().removeOnPreDrawListener(this);
                            if (scroll.getHeight() > maxH) {
                                android.view.ViewGroup.LayoutParams p = scroll.getLayoutParams();
                                p.height = maxH;
                                scroll.setLayoutParams(p);
                            }
                            return true;
                        }
                    });
        }
    }

    public static void showEditDialog(
            Context context,
            String title,
            DialogField[] fields,
            OnDialogSubmitListener onSubmitListener,
            String actionButtonText,
            OnDialogActionListener actionListener) {

        // Inflate the main dialog layout
        LayoutInflater inflater = LayoutInflater.from(context);
        View dialogView = inflater.inflate(R.layout.dialog_edit_profile, null);
        LinearLayout fieldsContainer = dialogView.findViewById(R.id.fields_container);

        // Clear any existing views
        fieldsContainer.removeAllViews();

        // Title
        TextView titleView = dialogView.findViewById(R.id.dialog_title);
        if (titleView != null && title != null) titleView.setText(title);

        // Value-holder per field key. EditText extends TextView, so both input and
        // picker rows can be read uniformly via getText() at save time.
        Map<String, TextView> inputViews = new HashMap<>();

        final float density = context.getResources().getDisplayMetrics().density;
        boolean firstSection = true;
        for (DialogField field : fields) {
            // Section header — quiet small-caps label grouping the form.
            if (field.isSection()) {
                TextView header = new TextView(context);
                header.setText(field.getLabel() != null ? field.getLabel().toUpperCase() : "");
                // Section/category headers in the app accent (was quiet grey #7A7A7A).
                header.setTextColor(DIALOG_ACCENT_COLOR);
                header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
                header.setAllCaps(true);
                header.setLetterSpacing(0.12f);
                header.setTypeface(header.getTypeface(), Typeface.BOLD);
                LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                hlp.leftMargin = (int) (4 * density);
                hlp.topMargin = (int) (density * (firstSection ? 2 : 16));
                hlp.bottomMargin = (int) (4 * density);
                header.setLayoutParams(hlp);
                fieldsContainer.addView(header);
                firstSection = false;
                continue;
            }

            if (field.isMultiSelect()) {
                // Searchable multi-select: autocomplete search that adds removable chips.
                View row = inflater.inflate(R.layout.dialog_field_multiselect, fieldsContainer, false);
                TextView label = row.findViewById(R.id.input_label);
                AutoCompleteTextView search = row.findViewById(R.id.field_search);
                final com.google.android.material.chip.ChipGroup chips = row.findViewById(R.id.field_chips);
                final TextView holder = row.findViewById(R.id.field_value);
                label.setText(field.getLabel());
                search.setHint("Search and add…");

                String[] opts = field.getOptions() != null ? field.getOptions() : new String[0];
                ArrayAdapter<String> adapter = new ArrayAdapter<>(
                        context, android.R.layout.simple_list_item_1, new java.util.ArrayList<>(java.util.Arrays.asList(opts)));
                search.setAdapter(adapter);

                // Seed chips from the existing comma-separated value.
                final java.util.LinkedHashSet<String> selected = new java.util.LinkedHashSet<>();
                if (field.getDefaultValue() != null) {
                    for (String s : field.getDefaultValue().split(",")) {
                        String t = s.trim();
                        if (!t.isEmpty()) selected.add(t);
                    }
                }
                // Turn the label accent-blue while the selection differs from the saved value.
                final String msOriginal = field.getDefaultValue() != null ? field.getDefaultValue() : "";
                final int msLabelNormal = label.getCurrentTextColor();
                final Runnable msTintLabel = () -> label.setTextColor(
                        holder.getText().toString().equals(msOriginal) ? msLabelNormal : DIALOG_ACCENT_COLOR);
                syncMultiSelect(context, chips, holder, selected, density);

                search.setOnItemClickListener((parent, v, position, id) -> {
                    String val = (String) parent.getItemAtPosition(position);
                    if (val != null && !val.trim().isEmpty()) selected.add(val.trim());
                    search.setText("");
                    syncMultiSelect(context, chips, holder, selected, density);
                    msTintLabel.run();
                });
                search.setOnEditorActionListener((v, actionId, event) -> {
                    String val = search.getText().toString().trim();
                    if (!val.isEmpty()) {
                        selected.add(val);
                        search.setText("");
                        syncMultiSelect(context, chips, holder, selected, density);
                        msTintLabel.run();
                    }
                    return true;
                });

                inputViews.put(field.getKey(), holder);
                fieldsContainer.addView(row);
                continue;
            }

            if (field.isDropdown() || field.isDate()) {
                // Compact tap-to-open row (label + value + chevron).
                View row = inflater.inflate(R.layout.dialog_field_picker, fieldsContainer, false);
                TextView label = row.findViewById(R.id.picker_label);
                final TextView valueView = row.findViewById(R.id.picker_value);
                label.setText(field.getLabel());
                if (field.getDefaultValue() != null) valueView.setText(field.getDefaultValue());

                final String pickerOriginal = field.getDefaultValue() != null ? field.getDefaultValue() : "";
                final int pickerNormal = valueView.getCurrentTextColor();
                if (field.isDropdown()) {
                    row.setOnClickListener(v -> showChoiceDialog(
                            context, field.getLabel(), field.getOptions(),
                            valueView.getText().toString(),
                            value -> { valueView.setText(value); applyChangedTint(valueView, pickerOriginal, pickerNormal); }));
                } else {
                    row.setOnClickListener(v -> showDatePicker(
                            context, valueView.getText().toString(),
                            value -> { valueView.setText(value); applyChangedTint(valueView, pickerOriginal, pickerNormal); }));
                }
                inputViews.put(field.getKey(), valueView);
                fieldsContainer.addView(row);
                continue;
            }

            // Text input. Multi-line inputs get a roomier label-on-top layout.
            boolean multiline = (field.getInputType() & InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0;
            View row = inflater.inflate(
                    multiline ? R.layout.dialog_field_multiline : R.layout.dialog_field_input,
                    fieldsContainer, false);
            TextView label = row.findViewById(R.id.input_label);
            EditText inputView = row.findViewById(R.id.field_input);
            label.setText(field.getLabel());
            inputView.setHint(field.getLabel());
            inputView.setInputType(field.getInputType());
            if (field.getDefaultValue() != null) inputView.setText(field.getDefaultValue());
            // Flag unsaved edits: value turns accent-blue while it differs from the
            // original, back to normal when it matches again.
            final String inputOriginal = field.getDefaultValue() != null ? field.getDefaultValue() : "";
            final int inputNormal = inputView.getCurrentTextColor();
            inputView.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
                @Override public void afterTextChanged(android.text.Editable s) {
                    applyChangedTint(inputView, inputOriginal, inputNormal);
                }
            });
            inputViews.put(field.getKey(), inputView);
            fieldsContainer.addView(row);
        }

        Dialog dialog = new Dialog(context, R.style.DialogTheme);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(dialogView);

        if (dialog.getWindow() != null) {
            WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
            layoutParams.copyFrom(dialog.getWindow().getAttributes());
            layoutParams.width = (int) (context.getResources().getDisplayMetrics().widthPixels * 0.92);
            layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
            dialog.getWindow().setAttributes(layoutParams);
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        // Bound the scroll area so the dialog never grows past ~76% of the screen
        // (the real cause of the old "giant dialog"); the footer stays pinned.
        final View scroll = dialogView.findViewById(R.id.fields_scroll);
        if (scroll != null) {
            final int maxScrollH = (int) (context.getResources().getDisplayMetrics().heightPixels * 0.62);
            scroll.getViewTreeObserver().addOnPreDrawListener(
                    new android.view.ViewTreeObserver.OnPreDrawListener() {
                        @Override public boolean onPreDraw() {
                            scroll.getViewTreeObserver().removeOnPreDrawListener(this);
                            if (scroll.getHeight() > maxScrollH) {
                                android.view.ViewGroup.LayoutParams lp = scroll.getLayoutParams();
                                lp.height = maxScrollH;
                                scroll.setLayoutParams(lp);
                            }
                            return true;
                        }
                    });
        }

        // Optional secondary action (e.g. Change Password).
        MaterialButton actionButton = dialog.findViewById(R.id.action_button);
        if (actionButton != null) {
            if (actionButtonText != null && actionListener != null) {
                actionButton.setText(actionButtonText);
                actionButton.setVisibility(View.VISIBLE);
                actionButton.setOnClickListener(v -> {
                    dialog.dismiss();
                    actionListener.onAction();
                });
            } else {
                actionButton.setVisibility(View.GONE);
            }
        }

        Button saveButton = dialog.findViewById(R.id.save_button);
        Button cancelButton = dialog.findViewById(R.id.cancel_button);
        cancelButton.setOnClickListener(v -> dialog.dismiss());
        saveButton.setOnClickListener(v -> {
            try {
                Map<String, String> values = new HashMap<>();
                for (DialogField field : fields) {
                    if (field.isSection()) continue;
                    TextView holder = inputViews.get(field.getKey());
                    if (holder != null) {
                        values.put(field.getKey(), holder.getText().toString().trim());
                    }
                }
                onSubmitListener.onSubmit(values);
                dialog.dismiss();
            } catch (Exception e) {
                Utilities.toast(context, "Error: " + e.getMessage());
            }
        });

        dialog.show();
    }

    /**
     * Rebuilds the chip row for a multi-select field and mirrors the current
     * selection into the hidden holder as a comma-separated string. Chips carry a
     * close icon that removes them. Styling matches the app's accent chips.
     */
    private static void syncMultiSelect(Context context,
                                        com.google.android.material.chip.ChipGroup chips,
                                        TextView holder,
                                        final java.util.LinkedHashSet<String> selected,
                                        float density) {
        chips.removeAllViews();
        chips.setVisibility(selected.isEmpty() ? View.GONE : View.VISIBLE);
        for (final String item : selected) {
            com.google.android.material.chip.Chip chip = new com.google.android.material.chip.Chip(context);
            chip.setText(item);
            chip.setTextColor(Color.WHITE);
            chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(context, R.color.rh_accent_dim)));
            chip.setChipStrokeColor(android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(context, R.color.rh_accent)));
            chip.setChipStrokeWidth(density);
            chip.setCloseIconVisible(true);
            chip.setCloseIconTint(android.content.res.ColorStateList.valueOf(Color.WHITE));
            chip.setClickable(false);
            chip.setCheckable(false);
            chip.setOnCloseIconClickListener(v -> {
                selected.remove(item);
                syncMultiSelect(context, chips, holder, selected, density);
            });
            chips.addView(chip);
        }
        holder.setText(android.text.TextUtils.join(", ", selected));
    }

    /**
     * App-standard single-choice picker. Dark rounded card, teal accent, a check
     * on the selected row — identical in look to the "Response Tone" chooser so
     * every picker in the app is consistent. Long lists scroll.
     */
    public static void showChoiceDialog(Context ctx, String title, String[] options,
                                        String currentValue, OnChoiceListener callback) {
        if (options == null) return;
        final float d = ctx.getResources().getDisplayMetrics().density;

        final Dialog dialog = new Dialog(ctx, R.style.DialogTheme);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding((int) (22 * d), (int) (20 * d), (int) (22 * d), (int) (14 * d));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(18 * d);
        bg.setColor(Color.parseColor("#141C1C"));
        bg.setStroke((int) d, Color.parseColor("#243A38"));
        root.setBackground(bg);

        if (title != null && !title.isEmpty()) {
            TextView titleView = new TextView(ctx);
            titleView.setText(title);
            titleView.setTextColor(Color.WHITE);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f);
            titleView.setTypeface(null, Typeface.BOLD);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            tlp.bottomMargin = (int) (12 * d);
            root.addView(titleView, tlp);
        }

        LinearLayout list = new LinearLayout(ctx);
        list.setOrientation(LinearLayout.VERTICAL);

        for (final String option : options) {
            final String value = option == null ? "" : option;
            boolean selected = value.equalsIgnoreCase(currentValue == null ? "" : currentValue);

            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding((int) (6 * d), (int) (13 * d), (int) (6 * d), (int) (13 * d));
            row.setClickable(true);
            row.setFocusable(true);
            TypedValue tv = new TypedValue();
            ctx.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
            row.setBackgroundResource(tv.resourceId);

            TextView lbl = new TextView(ctx);
            lbl.setText(value.isEmpty() ? "Not set" : value);
            lbl.setTextColor(selected ? Color.parseColor("#37C9A6") : Color.parseColor("#E4EEEE"));
            lbl.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
            row.addView(lbl, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

            if (selected) {
                ImageView chk = new ImageView(ctx);
                chk.setImageResource(R.drawable.ic_check);
                chk.setColorFilter(Color.parseColor("#37C9A6"));
                row.addView(chk, new LinearLayout.LayoutParams((int) (18 * d), (int) (18 * d)));
            }

            row.setOnClickListener(v -> {
                callback.onChoice(value);
                dialog.dismiss();
            });
            list.addView(row, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }

        // Cap height so long lists (blood type, ancestry) scroll instead of overflowing.
        final ScrollView scroller = new ScrollView(ctx);
        scroller.setVerticalScrollBarEnabled(false);
        scroller.addView(list);
        root.addView(scroller, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        final int maxH = (int) (ctx.getResources().getDisplayMetrics().heightPixels * 0.6);
        scroller.getViewTreeObserver().addOnPreDrawListener(
                new android.view.ViewTreeObserver.OnPreDrawListener() {
                    @Override public boolean onPreDraw() {
                        scroller.getViewTreeObserver().removeOnPreDrawListener(this);
                        if (scroller.getHeight() > maxH) {
                            android.view.ViewGroup.LayoutParams lp = scroller.getLayoutParams();
                            lp.height = maxH;
                            scroller.setLayoutParams(lp);
                        }
                        return true;
                    }
                });

        dialog.setContentView(root);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
            lp.copyFrom(dialog.getWindow().getAttributes());
            lp.width = (int) (ctx.getResources().getDisplayMetrics().widthPixels * 0.86);
            dialog.getWindow().setAttributes(lp);
        }
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
    }

    /** Callback for {@link #showConfirmDialog}. */
    public interface OnConfirmListener {
        void onConfirm();
    }

    /**
     * App-styled confirmation dialog (same visual language as showChoiceDialog:
     * rounded #141C1C card, teal accent). Use this instead of a native
     * AlertDialog.Builder for confirmations (logout, delete, discard, etc.) so
     * every confirmation across the app looks consistent.
     *
     * @param destructive when true the confirm button is red (for delete/logout);
     *                    otherwise it is the teal accent.
     */
    public static void showConfirmDialog(Context ctx, String title, String message,
                                         String positiveText, String negativeText,
                                         boolean destructive, OnConfirmListener onConfirm) {
        if (ctx == null) return;
        final float d = ctx.getResources().getDisplayMetrics().density;

        final Dialog dialog = new Dialog(ctx, R.style.DialogTheme);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding((int) (22 * d), (int) (20 * d), (int) (22 * d), (int) (16 * d));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(18 * d);
        bg.setColor(Color.parseColor("#141C1C"));
        bg.setStroke((int) d, Color.parseColor("#243A38"));
        root.setBackground(bg);

        if (title != null && !title.isEmpty()) {
            TextView titleView = new TextView(ctx);
            titleView.setText(title);
            titleView.setTextColor(Color.WHITE);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f);
            titleView.setTypeface(null, Typeface.BOLD);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            tlp.bottomMargin = (int) (10 * d);
            root.addView(titleView, tlp);
        }

        if (message != null && !message.isEmpty()) {
            TextView msgView = new TextView(ctx);
            msgView.setText(message);
            msgView.setTextColor(Color.parseColor("#B8C4C4"));
            msgView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
            msgView.setLineSpacing(0f, 1.15f);
            LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            mlp.bottomMargin = (int) (18 * d);
            root.addView(msgView, mlp);
        }

        LinearLayout buttonRow = new LinearLayout(ctx);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(Gravity.END);

        String accent = destructive ? "#FF5252" : "#37C9A6";

        // Negative (cancel) — quiet text button. Omitted for single-button info dialogs
        // (pass negativeText = null).
        if (negativeText != null) {
            MaterialButton negative = new MaterialButton(ctx);
            negative.setText(negativeText);
            negative.setTextSize(14f);
            negative.setAllCaps(false);
            negative.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(Color.TRANSPARENT));
            negative.setTextColor(Color.parseColor("#9BB0B0"));
            negative.setCornerRadius((int) (10 * d));
            negative.setInsetTop(0);
            negative.setInsetBottom(0);
            // Flat: kill the default MaterialButton elevation/shadow that showed as a
            // ghost "box" around the transparent Cancel button.
            negative.setElevation(0f);
            negative.setStateListAnimator(null);
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, (int) (44 * d));
            negative.setLayoutParams(nlp);
            negative.setOnClickListener(v -> dialog.dismiss());
            buttonRow.addView(negative);
        }

        // Positive (confirm) — filled accent (teal, or red when destructive).
        MaterialButton positive = new MaterialButton(ctx);
        positive.setText(positiveText != null ? positiveText : "Confirm");
        positive.setTextSize(14f);
        positive.setAllCaps(false);
        positive.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(Color.parseColor(accent)));
        positive.setTextColor(Color.WHITE);
        positive.setCornerRadius((int) (10 * d));
        positive.setInsetTop(0);
        positive.setInsetBottom(0);
        positive.setElevation(0f);
        positive.setStateListAnimator(null);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, (int) (44 * d));
        plp.leftMargin = (int) (8 * d);
        positive.setLayoutParams(plp);
        positive.setOnClickListener(v -> {
            dialog.dismiss();
            if (onConfirm != null) onConfirm.onConfirm();
        });
        buttonRow.addView(positive);

        root.addView(buttonRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        dialog.setContentView(root);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
            lp.copyFrom(dialog.getWindow().getAttributes());
            lp.width = (int) (ctx.getResources().getDisplayMetrics().widthPixels * 0.86);
            dialog.getWindow().setAttributes(lp);
        }
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
    }

    /** Native date picker; seeds from an ISO yyyy-MM-dd string and returns the same format. */
    public static void showDatePicker(Context ctx, String currentIso, OnChoiceListener callback) {
        Calendar cal = Calendar.getInstance();
        if (currentIso != null && currentIso.matches("\\d{4}-\\d{2}-\\d{2}")) {
            try {
                cal.setTime(new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(currentIso));
            } catch (Exception ignored) {}
        }
        DatePickerDialog dp = new DatePickerDialog(ctx, (view, year, month, day) -> {
            String iso = String.format(java.util.Locale.US, "%04d-%02d-%02d", year, month + 1, day);
            callback.onChoice(iso);
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
        // Can't be born in the future.
        dp.getDatePicker().setMaxDate(System.currentTimeMillis());
        dp.show();
    }

    // ADD AQI Chart Dialog
    public static void showAQIChartDialog(Context context, List<AQIData> aqiHistory) {
        showAQIChartDialog(context, aqiHistory, -1);
    }

    // Overloaded method with analysis data
    public static void showAQIChartDialog(Context context, List<AQIData> aqiHistory, int highExposureDays) {
        Dialog dialog = new Dialog(context, R.style.FullScreenDialog);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_aqi_chart);

        // Set dialog width with proper margins
        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
        layoutParams.copyFrom(dialog.getWindow().getAttributes());
        layoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
        layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
        dialog.getWindow().setAttributes(layoutParams);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        if (aqiHistory == null || aqiHistory.isEmpty()) {
            Utilities.toast(context, "No AQI history data available");
            return;
        }

        // Group data by location
        Map<String, List<AQIData>> locationGroups = new HashMap<>();
        for (AQIData data : aqiHistory) {
            String locationKey = data.getLocationKey();
            if (!locationGroups.containsKey(locationKey)) {
                locationGroups.put(locationKey, new ArrayList<>());
            }
            locationGroups.get(locationKey).add(data);
        }

        // Find location with most records
        String defaultLocation = null;
        int maxCount = 0;
        for (Map.Entry<String, List<AQIData>> entry : locationGroups.entrySet()) {
            if (entry.getValue().size() > maxCount) {
                maxCount = entry.getValue().size();
                defaultLocation = entry.getKey();
            }
        }

        // Setup UI elements
        AutoCompleteTextView locationSpinner = dialog.findViewById(R.id.location_spinner);
        com.github.mikephil.charting.charts.LineChart chart = dialog.findViewById(R.id.aqi_chart);
        TextView avgAqiValue = dialog.findViewById(R.id.avg_aqi_value);
        TextView maxAqiValue = dialog.findViewById(R.id.max_aqi_value);
        TextView dataPointsValue = dialog.findViewById(R.id.data_points_value);

        // Setup location spinner
        List<String> locations = new ArrayList<>(locationGroups.keySet());
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_dropdown_item_1line, locations);
        locationSpinner.setAdapter(adapter);
        locationSpinner.setText(defaultLocation, false);

        // Display chart for default location
        updateChartForLocation(chart, avgAqiValue, maxAqiValue, dataPointsValue, locationGroups.get(defaultLocation));

        // Handle location selection
        locationSpinner.setOnItemClickListener((parent, view, position, id) -> {
            String selectedLocation = locations.get(position);
            updateChartForLocation(chart, avgAqiValue, maxAqiValue, dataPointsValue, locationGroups.get(selectedLocation));
        });

        // Show analysis card if high exposure days is provided
        View analysisCard = dialog.findViewById(R.id.analysis_card);
        TextView highExposureDaysText = dialog.findViewById(R.id.high_exposure_days_text);
        if (highExposureDays >= 0 && analysisCard != null && highExposureDaysText != null) {
            analysisCard.setVisibility(View.VISIBLE);
            highExposureDaysText.setText("High exposure days (AQI > 100): " + highExposureDays);
        }

        // Setup OK button
        MaterialButton okButton = dialog.findViewById(R.id.ok_button);
        okButton.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private static void updateChartForLocation(com.github.mikephil.charting.charts.LineChart chart,
                                               TextView avgAqiValue, TextView maxAqiValue, TextView dataPointsValue,
                                               List<AQIData> locationData) {
        // Calculate stats
        int sum = 0, max = 0;
        for (AQIData data : locationData) {
            sum += data.getAqiValue();
            if (data.getAqiValue() > max) max = data.getAqiValue();
        }
        int avg = sum / locationData.size();

        // Update stats
        avgAqiValue.setText(String.valueOf(avg));
        maxAqiValue.setText(String.valueOf(max));
        dataPointsValue.setText(String.valueOf(locationData.size()));

        // Update chart
        setupAQIChart(chart, locationData);
    }

    private static void setupAQIChart(com.github.mikephil.charting.charts.LineChart chart, List<AQIData> aqiHistory) {
        // Prepare data entries
        java.util.List<com.github.mikephil.charting.data.Entry> entries = new java.util.ArrayList<>();
        java.util.List<String> labels = new java.util.ArrayList<>();

        // Sort data by date (oldest first for chart)
        java.util.Collections.sort(aqiHistory, (a, b) -> a.getRecordedAt().compareTo(b.getRecordedAt()));

        for (int i = 0; i < aqiHistory.size(); i++) {
            AQIData data = aqiHistory.get(i);
            entries.add(new com.github.mikephil.charting.data.Entry(i, data.getAqiValue()));

            // Format date for label
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MM/dd", java.util.Locale.getDefault());
            labels.add(sdf.format(data.getRecordedAt()));
        }

        // Create dataset with gradient colors
        com.github.mikephil.charting.data.LineDataSet dataSet = new com.github.mikephil.charting.data.LineDataSet(entries, "AQI Levels");
        dataSet.setColor(Color.parseColor("#1976D2"));
        dataSet.setLineWidth(3f);
        dataSet.setCircleColor(Color.parseColor("#1976D2"));
        dataSet.setCircleRadius(5f);
        dataSet.setCircleHoleRadius(2.5f);
        dataSet.setDrawValues(false);
        dataSet.setDrawFilled(true);
        dataSet.setFillColor(Color.parseColor("#1976D2"));
        dataSet.setFillAlpha(50);
        dataSet.setMode(com.github.mikephil.charting.data.LineDataSet.Mode.CUBIC_BEZIER);
        dataSet.setCubicIntensity(0.2f);

        // Create line data
        com.github.mikephil.charting.data.LineData lineData = new com.github.mikephil.charting.data.LineData(dataSet);

        // Configure chart
        chart.setData(lineData);
        chart.getDescription().setEnabled(false);
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        chart.setPinchZoom(true);

        // Configure X axis
        com.github.mikephil.charting.components.XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setLabelCount(Math.min(labels.size(), 7)); // Show max 7 labels
        xAxis.setTextColor(Color.parseColor("#008b8b"));
        xAxis.setValueFormatter(new com.github.mikephil.charting.formatter.ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int index = (int) value;
                if (index >= 0 && index < labels.size()) {
                    return labels.get(index);
                }
                return "";
            }
        });

        // Configure Y axis
        com.github.mikephil.charting.components.YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setAxisMinimum(0f);
        leftAxis.setTextColor(Color.parseColor("#008b8b"));

        com.github.mikephil.charting.components.YAxis rightAxis = chart.getAxisRight();
        rightAxis.setEnabled(false);

        // Configure legend
        com.github.mikephil.charting.components.Legend legend = chart.getLegend();
        legend.setEnabled(true);
        legend.setTextColor(Color.parseColor("#ffffff"));
        legend.setXOffset(0f);
        legend.setYOffset(10f);

        // Refresh chart
        chart.invalidate();
    }

    // ─── Report trend chart ──────────────────────────────────────────────────
    // Mirrors showAQIChartDialog: a dropdown picks a test (by canonicalKey) and
    // the line chart plots that test's numeric values across all reports over time.

    private static class TrendPoint {
        final long date;
        final double value;
        TrendPoint(long date, double value) { this.date = date; this.value = value; }
    }

    /**
     * Plot a single test's values across a patient's uploaded reports.
     * Groups keyFindings by canonicalKey; only numeric findings are charted.
     */
    public static void showReportTrendChartDialog(Context context, List<UploadedFile> reports) {
        if (reports == null || reports.isEmpty()) {
            Utilities.toast(context, "No reports to chart yet");
            return;
        }

        // Build per-test series keyed by canonicalKey.
        java.util.LinkedHashMap<String, List<TrendPoint>> seriesByKey = new java.util.LinkedHashMap<>();
        Map<String, String> labelByKey = new HashMap<>();
        Map<String, String> unitByKey = new HashMap<>();
        Map<String, String> rangeByKey = new HashMap<>();

        for (UploadedFile r : reports) {
            if (r == null || r.getKeyFindings() == null) continue;
            long date = r.getReportDateMillis();
            for (UploadedFile.KeyFinding f : r.getKeyFindings()) {
                if (f == null || !f.hasNumericValue()) continue;
                String key = f.getCanonicalKey();
                if (key == null || key.isEmpty()) {
                    key = f.getParameter() == null ? "" : f.getParameter().trim().toLowerCase();
                }
                if (key.isEmpty()) continue;
                List<TrendPoint> pts = seriesByKey.get(key);
                if (pts == null) { pts = new ArrayList<>(); seriesByKey.put(key, pts); }
                pts.add(new TrendPoint(date, f.getValueNumeric()));
                if (!labelByKey.containsKey(key)) {
                    String param = (f.getParameter() != null && !f.getParameter().isEmpty()) ? f.getParameter() : key;
                    labelByKey.put(key, param);
                    unitByKey.put(key, f.getUnit() != null ? f.getUnit() : "");
                    rangeByKey.put(key, f.getNormalRange() != null ? f.getNormalRange() : "");
                }
            }
        }

        final List<String> keys = new ArrayList<>();
        for (Map.Entry<String, List<TrendPoint>> e : seriesByKey.entrySet()) {
            if (!e.getValue().isEmpty()) keys.add(e.getKey());
        }
        if (keys.isEmpty()) {
            Utilities.toast(context, "No numeric test values to chart yet");
            return;
        }
        // Tests with more data points first (more useful trends).
        java.util.Collections.sort(keys, (a, b) -> seriesByKey.get(b).size() - seriesByKey.get(a).size());

        Dialog dialog = new Dialog(context, R.style.FullScreenDialog);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_report_trend_chart);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        lp.copyFrom(dialog.getWindow().getAttributes());
        lp.width = WindowManager.LayoutParams.WRAP_CONTENT;
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
        dialog.getWindow().setAttributes(lp);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        AutoCompleteTextView testSpinner = dialog.findViewById(R.id.test_spinner);
        com.github.mikephil.charting.charts.LineChart chart = dialog.findViewById(R.id.trend_chart);
        TextView latestValue = dialog.findViewById(R.id.latest_value);
        TextView rangeValue = dialog.findViewById(R.id.range_value);
        TextView pointsValue = dialog.findViewById(R.id.points_value);
        TextView referenceRangeText = dialog.findViewById(R.id.reference_range_text);

        // Dropdown shows friendly labels; position maps back to canonicalKey.
        List<String> displayNames = new ArrayList<>();
        for (String k : keys) {
            String unit = unitByKey.get(k);
            displayNames.add(labelByKey.get(k) + (unit != null && !unit.isEmpty() ? " (" + unit + ")" : ""));
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_dropdown_item_1line, displayNames);
        testSpinner.setAdapter(adapter);
        testSpinner.setText(displayNames.get(0), false);

        updateTrendChart(chart, latestValue, rangeValue, pointsValue, referenceRangeText,
                seriesByKey.get(keys.get(0)), unitByKey.get(keys.get(0)), rangeByKey.get(keys.get(0)), labelByKey.get(keys.get(0)));

        testSpinner.setOnItemClickListener((parent, view, position, id) -> {
            String key = keys.get(position);
            updateTrendChart(chart, latestValue, rangeValue, pointsValue, referenceRangeText,
                    seriesByKey.get(key), unitByKey.get(key), rangeByKey.get(key), labelByKey.get(key));
        });

        MaterialButton okButton = dialog.findViewById(R.id.trend_ok_button);
        okButton.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private static void updateTrendChart(com.github.mikephil.charting.charts.LineChart chart,
                                         TextView latest, TextView range, TextView points, TextView refRange,
                                         List<TrendPoint> series, String unit, String normalRange, String label) {
        java.util.Collections.sort(series, (a, b) -> Long.compare(a.date, b.date));

        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE, last = 0;
        for (TrendPoint p : series) {
            if (p.value < min) min = p.value;
            if (p.value > max) max = p.value;
            last = p.value;
        }
        String u = (unit != null && !unit.isEmpty()) ? " " + unit : "";
        latest.setText(fmtTrend(last) + u);
        range.setText(series.size() > 1 ? fmtTrend(min) + "–" + fmtTrend(max) : fmtTrend(last));
        points.setText(String.valueOf(series.size()));
        refRange.setText((normalRange != null && !normalRange.isEmpty()) ? "Reference range: " + normalRange : "");

        setupTrendChart(chart, series, label);
    }

    private static String fmtTrend(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v)) return String.valueOf((long) v);
        return String.format(java.util.Locale.US, "%.2f", v);
    }

    private static void setupTrendChart(com.github.mikephil.charting.charts.LineChart chart,
                                        List<TrendPoint> series, String label) {
        java.util.List<com.github.mikephil.charting.data.Entry> entries = new java.util.ArrayList<>();
        final java.util.List<String> labels = new java.util.ArrayList<>();

        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MM/dd/yy", java.util.Locale.getDefault());
        for (int i = 0; i < series.size(); i++) {
            TrendPoint p = series.get(i);
            entries.add(new com.github.mikephil.charting.data.Entry(i, (float) p.value));
            labels.add(p.date > 0 ? sdf.format(new java.util.Date(p.date)) : "—");
        }

        com.github.mikephil.charting.data.LineDataSet dataSet =
                new com.github.mikephil.charting.data.LineDataSet(entries, label != null ? label : "Value");
        dataSet.setColor(Color.parseColor("#008b8b"));
        dataSet.setLineWidth(3f);
        dataSet.setCircleColor(Color.parseColor("#008b8b"));
        dataSet.setCircleRadius(5f);
        dataSet.setCircleHoleRadius(2.5f);
        dataSet.setDrawValues(true);
        dataSet.setValueTextColor(Color.parseColor("#DCE6E6"));
        dataSet.setValueTextSize(10f);
        dataSet.setDrawFilled(true);
        dataSet.setFillColor(Color.parseColor("#008b8b"));
        dataSet.setFillAlpha(50);
        dataSet.setMode(com.github.mikephil.charting.data.LineDataSet.Mode.CUBIC_BEZIER);
        dataSet.setCubicIntensity(0.2f);

        com.github.mikephil.charting.data.LineData lineData = new com.github.mikephil.charting.data.LineData(dataSet);

        chart.setData(lineData);
        chart.getDescription().setEnabled(false);
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        chart.setPinchZoom(true);

        com.github.mikephil.charting.components.XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setLabelCount(Math.min(labels.size(), 7));
        xAxis.setGranularity(1f);
        xAxis.setTextColor(Color.parseColor("#008b8b"));
        xAxis.setValueFormatter(new com.github.mikephil.charting.formatter.ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int index = (int) value;
                if (index >= 0 && index < labels.size()) {
                    return labels.get(index);
                }
                return "";
            }
        });

        com.github.mikephil.charting.components.YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setTextColor(Color.parseColor("#008b8b"));

        com.github.mikephil.charting.components.YAxis rightAxis = chart.getAxisRight();
        rightAxis.setEnabled(false);

        com.github.mikephil.charting.components.Legend legend = chart.getLegend();
        legend.setEnabled(true);
        legend.setTextColor(Color.parseColor("#ffffff"));
        legend.setXOffset(0f);
        legend.setYOffset(10f);

        chart.invalidate();
    }
}
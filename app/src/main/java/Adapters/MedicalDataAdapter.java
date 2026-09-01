package Adapters;
import Utils.Utilities;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.richhealth.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import Models.MedicalData;

public class MedicalDataAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_ITEM = 0;
    private static final int VIEW_TYPE_HEADER = 1;

    /** Provenance sentinel iOS writes into `description` for every HealthKit import
     *  (HealthKitManager -> MedicalDataRecord.appleWatchSourceTag). It is the only way to
     *  tell an imported reading from a hand-entered one; the backend does not tag them. */
    public static final String APPLE_SOURCE_TAG = "Imported from Apple Health";

    private List<MedicalData> medicalDataList;
    private Context context;
    private OnMedicalDataActionListener actionListener;

    /**
     * Section mode — measurements only. Off by default, so the symptoms, period and
     * medical-data panels keep the flat list they have always had, with adapter positions
     * still equal to data indices.
     */
    private boolean grouped = false;
    private boolean appleExpanded = false;
    /** Flattened display rows: a Header, or a MedicalData. Equals medicalDataList when
     *  grouping is off. */
    private final List<Object> rows = new ArrayList<>();

    private static class Header {
        final String label; final int iconRes; final int count; final boolean collapsible;
        Header(String label, int iconRes, int count, boolean collapsible) {
            this.label = label; this.iconRes = iconRes; this.count = count; this.collapsible = collapsible;
        }
    }

    /** True when this reading came from an Apple Health import rather than the user. */
    public static boolean isFromAppleWatch(MedicalData data) {
        if (!(data instanceof MedicalData.HealthMetric)) return false;
        String notes = ((MedicalData.HealthMetric) data).getNotes();
        return APPLE_SOURCE_TAG.equals(notes);
    }

    /** Turns on the "Manually Added" / "Apple Watch" grouping (measurements panel). */
    public void setGrouped(boolean grouped) {
        this.grouped = grouped;
        rebuildRows();
        notifyDataSetChanged();
    }

    private void rebuildRows() {
        rows.clear();
        if (medicalDataList == null) return;
        if (!grouped) { rows.addAll(medicalDataList); return; }

        List<MedicalData> manual = new ArrayList<>();
        List<MedicalData> apple = new ArrayList<>();
        for (MedicalData d : medicalDataList) {
            (isFromAppleWatch(d) ? apple : manual).add(d);
        }
        if (!manual.isEmpty()) {
            rows.add(new Header("Manually Added", R.drawable.ic_edit, manual.size(), false));
            rows.addAll(manual);
        }
        if (!apple.isEmpty()) {
            rows.add(new Header("Apple Watch", R.drawable.ic_directory_sync, apple.size(), true));
            if (appleExpanded) rows.addAll(apple);
        }
    }

    /** notifyItemXxx positions are only valid when rows track the data list 1:1. */
    private boolean canNotifyByPosition() { return !grouped; }

    public interface OnMedicalDataActionListener {
        void onEditItem(MedicalData data);
        void onDeleteItem(MedicalData data, int position);
    }

    public MedicalDataAdapter(Context context) {
        this.context = context;
        this.medicalDataList = new ArrayList<>();
    }

    public void setActionListener(OnMedicalDataActionListener listener) {
        this.actionListener = listener;
    }

    public void setData(List<MedicalData> data) {
        this.medicalDataList = data;
        rebuildRows();
        notifyDataSetChanged();
    }

    public void addItem(MedicalData data) {
        this.medicalDataList.add(0, data);
        rebuildRows();
        if (canNotifyByPosition()) notifyItemInserted(0); else notifyDataSetChanged();
    }

    public void removeItem(int position) {
        if (position >= 0 && position < medicalDataList.size()) {
            this.medicalDataList.remove(position);
            rebuildRows();
            if (canNotifyByPosition()) notifyItemRemoved(position); else notifyDataSetChanged();
        }
    }

    public void updateItem(MedicalData data) {
        for (int i = 0; i < medicalDataList.size(); i++) {
            if (medicalDataList.get(i).getId() == data.getId()) {
                medicalDataList.set(i, data);
                rebuildRows();
                if (canNotifyByPosition()) notifyItemChanged(i); else notifyDataSetChanged();
                break;
            }
        }
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position) instanceof Header ? VIEW_TYPE_HEADER : VIEW_TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(context);
        if (viewType == VIEW_TYPE_HEADER) {
            return new SectionHeaderViewHolder(
                    inflater.inflate(R.layout.item_medical_section_header, parent, false));
        }
        return new MedicalDataViewHolder(inflater.inflate(R.layout.item_medical_data, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object row = rows.get(position);
        if (holder instanceof SectionHeaderViewHolder) {
            ((SectionHeaderViewHolder) holder).bind((Header) row);
        } else {
            MedicalData data = (MedicalData) row;
            // The listener's `position` has always been the adapter position; callers use
            // it only for the delete confirm, which reloads the whole list, so the data
            // index is what is meaningful here.
            ((MedicalDataViewHolder) holder).bind(data, medicalDataList.indexOf(data));
        }
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    class SectionHeaderViewHolder extends RecyclerView.ViewHolder {
        private final ImageView icon;
        private final TextView label;
        private final TextView count;
        private final ImageView chevron;

        SectionHeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.section_icon);
            label = itemView.findViewById(R.id.section_label);
            count = itemView.findViewById(R.id.section_count);
            chevron = itemView.findViewById(R.id.section_chevron);
        }

        void bind(Header h) {
            icon.setImageResource(h.iconRes);
            label.setText(h.label);
            if (h.collapsible) {
                count.setText(String.valueOf(h.count));
                count.setVisibility(View.VISIBLE);
                chevron.setVisibility(View.VISIBLE);
                chevron.setRotation(appleExpanded ? 180f : 0f);
                itemView.setOnClickListener(v -> {
                    appleExpanded = !appleExpanded;
                    rebuildRows();
                    notifyDataSetChanged();
                });
                itemView.setClickable(true);
            } else {
                count.setVisibility(View.GONE);
                chevron.setVisibility(View.GONE);
                itemView.setOnClickListener(null);
                itemView.setClickable(false);
            }
        }
    }

    class MedicalDataViewHolder extends RecyclerView.ViewHolder {
        private TextView titleText;
        private TextView valueText;
        private TextView dateText;
        private MaterialButton editButton;
        private MaterialButton deleteButton;
        private ImageView sharingIcon;

        public MedicalDataViewHolder(@NonNull View itemView) {
            super(itemView);
            titleText = itemView.findViewById(R.id.title_text);
            valueText = itemView.findViewById(R.id.value_text);
            dateText = itemView.findViewById(R.id.date_text);
            editButton = itemView.findViewById(R.id.edit_button);
            deleteButton = itemView.findViewById(R.id.delete_button);
            sharingIcon = itemView.findViewById(R.id.sharing_icon);
        }

        public void bind(MedicalData data, int position) {
            // Set common data
            if (data instanceof MedicalData.Symptom) {
                titleText.setText(((MedicalData.Symptom) data).getName());
            } else if (data instanceof MedicalData.PeriodLog) {
                titleText.setText("Period");
            } else {
                titleText.setText(((MedicalData.HealthMetric) data).getMetricType());
            }

            SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault());
            dateText.setText(sdf.format(data.getRecordedAt()));

            // Sharing icon
            if (data.isShareWithFamily()) {
                sharingIcon.setImageResource(R.drawable.ic_visibility);
                sharingIcon.setImageTintList(ColorStateList.valueOf(Color.parseColor("#008b8b")));
            } else {
                sharingIcon.setImageResource(R.drawable.ic_visibility_off);
                sharingIcon.setImageTintList(ColorStateList.valueOf(Color.parseColor("#666666")));
            }
            sharingIcon.setOnClickListener(v -> {
                String msg = data.isShareWithFamily() ? "Shared with family" : "Not shared with family";
                Utilities.toast(context, msg);
            });

            // Find status chip
            Chip statusChip = itemView.findViewById(R.id.status_chip);

            // Set type-specific content
            if (data instanceof MedicalData.Symptom) {
                MedicalData.Symptom symptom = (MedicalData.Symptom) data;

                if (symptom.getDuration() != null && !symptom.getDuration().isEmpty()) {
                    valueText.setText("Duration: " + symptom.getDuration());
                    valueText.setVisibility(View.VISIBLE);
                } else {
                    valueText.setVisibility(View.GONE);
                }

                if (statusChip != null) {
                    statusChip.setText(symptom.getSeverityText());
                    int severity = symptom.getSeverity();
                    int chipColor;
                    if (severity <= 2) {
                        chipColor = Color.parseColor("#4CAF50");
                    } else if (severity <= 4) {
                        chipColor = Color.parseColor("#FF9800");
                    } else {
                        chipColor = Color.parseColor("#F44336");
                    }
                    statusChip.setChipBackgroundColor(ColorStateList.valueOf(chipColor));
                }

            } else if (data instanceof MedicalData.PeriodLog) {
                MedicalData.PeriodLog periodLog = (MedicalData.PeriodLog) data;
                valueText.setText("Flow: " + periodLog.getFlowIntensityLabel() + "  •  Pain: " + periodLog.getPainLevelText());
                valueText.setVisibility(View.VISIBLE);

                SimpleDateFormat dateFmt = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
                dateText.setText(dateFmt.format(periodLog.getStartDate()));

                if (statusChip != null) {
                    statusChip.setText(periodLog.getPainLevelText());
                    int painLevel = periodLog.getPainLevel();
                    int chipColor;
                    if (painLevel <= 2) {
                        chipColor = Color.parseColor("#4CAF50");
                    } else if (painLevel <= 4) {
                        chipColor = Color.parseColor("#FF9800");
                    } else {
                        chipColor = Color.parseColor("#F44336");
                    }
                    statusChip.setChipBackgroundColor(ColorStateList.valueOf(chipColor));
                }

            } else if (data instanceof MedicalData.HealthMetric) {
                MedicalData.HealthMetric metric = (MedicalData.HealthMetric) data;
                valueText.setText(metric.getFormattedValue());
                valueText.setVisibility(View.VISIBLE);

                String status = metric.getStatus();
                if (statusChip != null && status != null) {
                    statusChip.setText(status.toUpperCase());
                    int chipColor;
                    if ("normal".equalsIgnoreCase(status)) {
                        chipColor = Color.parseColor("#4CAF50");
                    } else if ("low".equalsIgnoreCase(status)) {
                        chipColor = Color.parseColor("#2196F3");
                    } else if ("high".equalsIgnoreCase(status)) {
                        chipColor = Color.parseColor("#F44336");
                    } else {
                        chipColor = Color.parseColor("#757575");
                    }
                    statusChip.setChipBackgroundColor(ColorStateList.valueOf(chipColor));
                }
            }

            // Description
            TextView descriptionText = itemView.findViewById(R.id.description_text);
            if (descriptionText != null) {
                if (data instanceof MedicalData.Symptom) {
                    String description = ((MedicalData.Symptom) data).getDescription();
                    if (description != null && !description.isEmpty()) {
                        descriptionText.setText(description);
                        descriptionText.setVisibility(View.VISIBLE);
                    } else {
                        descriptionText.setVisibility(View.GONE);
                    }
                } else if (data instanceof MedicalData.PeriodLog) {
                    String notes = ((MedicalData.PeriodLog) data).getNotes();
                    if (notes != null && !notes.isEmpty()) {
                        descriptionText.setText(notes);
                        descriptionText.setVisibility(View.VISIBLE);
                    } else {
                        descriptionText.setVisibility(View.GONE);
                    }
                } else if (data instanceof MedicalData.HealthMetric) {
                    // An Apple import's only "note" is the provenance sentinel, which the
                    // section header already says — printing it on every row is noise.
                    // Same call iOS makes in MedicalDataRecord.displaySubtitle.
                    String notes = isFromAppleWatch(data)
                            ? null : ((MedicalData.HealthMetric) data).getNotes();
                    if (notes != null && !notes.isEmpty()) {
                        descriptionText.setText(notes);
                        descriptionText.setVisibility(View.VISIBLE);
                    } else {
                        descriptionText.setVisibility(View.GONE);
                    }
                }
            }

            // Action buttons
            editButton.setOnClickListener(v -> {
                if (actionListener != null) {
                    actionListener.onEditItem(data);
                }
            });

            deleteButton.setOnClickListener(v -> {
                if (actionListener != null) {
                    actionListener.onDeleteItem(data, position);
                }
            });
        }
    }
}
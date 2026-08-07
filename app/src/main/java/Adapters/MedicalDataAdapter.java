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

public class MedicalDataAdapter extends RecyclerView.Adapter<MedicalDataAdapter.MedicalDataViewHolder> {

    private List<MedicalData> medicalDataList;
    private Context context;
    private OnMedicalDataActionListener actionListener;

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
        notifyDataSetChanged();
    }

    public void addItem(MedicalData data) {
        this.medicalDataList.add(0, data);
        notifyItemInserted(0);
    }

    public void removeItem(int position) {
        if (position >= 0 && position < medicalDataList.size()) {
            this.medicalDataList.remove(position);
            notifyItemRemoved(position);
        }
    }

    public void updateItem(MedicalData data) {
        for (int i = 0; i < medicalDataList.size(); i++) {
            if (medicalDataList.get(i).getId() == data.getId()) {
                medicalDataList.set(i, data);
                notifyItemChanged(i);
                break;
            }
        }
    }

    @NonNull
    @Override
    public MedicalDataViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_medical_data, parent, false);
        return new MedicalDataViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MedicalDataViewHolder holder, int position) {
        MedicalData data = medicalDataList.get(position);
        holder.bind(data, position);
    }

    @Override
    public int getItemCount() {
        return medicalDataList.size();
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
                    String notes = ((MedicalData.HealthMetric) data).getNotes();
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
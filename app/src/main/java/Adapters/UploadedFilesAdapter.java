package Adapters;
import Utils.Utilities;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
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

import java.util.List;

import Utils.ApiConfig;
import Utils.UploadedFile;

public class UploadedFilesAdapter extends RecyclerView.Adapter<UploadedFilesAdapter.ViewHolder> {
    private List<UploadedFile> uploadedFiles;
    private OnFileActionListener actionListener;
    private OnAnalyzeClickListener analyzeListener;

    public interface OnFileActionListener {
        void onDeleteClick(UploadedFile file, int position);
    }

    public interface OnAnalyzeClickListener {
        void onAnalyzeClick(UploadedFile file);
    }

    public UploadedFilesAdapter(List<UploadedFile> uploadedFiles, OnFileActionListener listener) {
        this.uploadedFiles = uploadedFiles;
        this.actionListener = listener;
    }

    public void setAnalyzeClickListener(OnAnalyzeClickListener listener) {
        this.analyzeListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_uploaded_file, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        UploadedFile file = uploadedFiles.get(position);
        holder.bind(file, position);
    }

    @Override
    public int getItemCount() {
        return uploadedFiles.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private TextView fileNameText;
        private TextView reportTypeText;
        private TextView statusText;
        private MaterialButton deleteButton;
        private MaterialButton viewButton;
        private MaterialButton analyzeButton;
        private ImageView sharingIcon;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            fileNameText = itemView.findViewById(R.id.file_name);
            reportTypeText = itemView.findViewById(R.id.report_type);
            statusText = itemView.findViewById(R.id.report_status);
            deleteButton = itemView.findViewById(R.id.delete_button);
            viewButton = itemView.findViewById(R.id.view_button);
            analyzeButton = itemView.findViewById(R.id.analyze_button);
            sharingIcon = itemView.findViewById(R.id.sharing_icon);
        }

        public void bind(UploadedFile file, int position) {
            fileNameText.setText(file.getName());
            reportTypeText.setText(file.getReportType());

            // Status text
            if (file.getStatus() != null) {
                statusText.setText(getStatusText(file.getStatus()));
                statusText.setVisibility(View.VISIBLE);
            } else {
                statusText.setVisibility(View.GONE);
            }

            // Sharing icon
            if (file.isShareWithFamily()) {
                sharingIcon.setImageResource(R.drawable.ic_visibility);
                sharingIcon.setImageTintList(ColorStateList.valueOf(Color.parseColor("#008b8b")));
            } else {
                sharingIcon.setImageResource(R.drawable.ic_visibility_off);
                sharingIcon.setImageTintList(ColorStateList.valueOf(Color.parseColor("#666666")));
            }
            sharingIcon.setOnClickListener(v -> {
                String msg = file.isShareWithFamily() ? "Shared with family" : "Not shared with family";
                Utilities.toast(v.getContext(), msg);
            });

            // Analyze button — show appropriate text based on state
            if (file.hasAnalysis()) {
                analyzeButton.setText("View Analysis");
                analyzeButton.setContentDescription("Show AI Analysis");
            } else if ("processing".equals(file.getStatus()) || "queued".equals(file.getStatus())) {
                analyzeButton.setText("Analyzing...");
                analyzeButton.setContentDescription("Analysis in progress");
            } else {
                analyzeButton.setText("Analyze Now");
                analyzeButton.setContentDescription("Analyze Report");
            }

            // View button
            viewButton.setOnClickListener(v -> {
                if (file.getFileUrl() != null) {
                    openFileViewer(v.getContext(), file);
                } else {
                    Utilities.toast(v.getContext(), "File not available");
                }
            });

            // Analyze button
            analyzeButton.setOnClickListener(v -> {
                if (analyzeListener != null) {
                    analyzeListener.onAnalyzeClick(file);
                }
            });

            // Delete button
            deleteButton.setOnClickListener(v -> {
                if (actionListener != null) {
                    actionListener.onDeleteClick(file, position);
                }
            });
        }

        private String getStatusText(String status) {
            switch (status) {
                case "uploading":
                    return "Uploading...";
                case "uploaded":
                    return "Uploaded";
                case "queued":
                    return "Queued for Analysis";
                case "processing":
                    return "Analyzing...";
                case "processed":
                    return "Analysis Complete";
                case "failed":
                    return "Analysis Failed";
                default:
                    return status;
            }
        }

        private void openFileViewer(Context context, UploadedFile file) {
            String fileUrl = file.getFileUrl();

            if (fileUrl.startsWith("/")) {
                fileUrl = ApiConfig.BASE_URL + fileUrl;
            }

            Intent intent = new Intent(Intent.ACTION_VIEW);

            String mimeType = file.getFileType();
            if (mimeType == null || mimeType.equals("application/octet-stream")) {
                String fileName = file.getName().toLowerCase();
                if (fileName.endsWith(".pdf")) {
                    mimeType = "application/pdf";
                } else if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
                    mimeType = "image/jpeg";
                } else if (fileName.endsWith(".png")) {
                    mimeType = "image/png";
                }
            }

            intent.setDataAndType(Uri.parse(fileUrl), mimeType);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);

            try {
                context.startActivity(intent);
            } catch (Exception e) {
                Utilities.toast(context, "No app found to open this file");
            }
        }
    }
}
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;

import android.os.Handler;
import android.os.Looper;

import androidx.core.content.FileProvider;

import com.example.richhealth.Activities.TokenManager;

import Utils.ApiConfig;
import Utils.UploadedFile;

public class UploadedFilesAdapter extends RecyclerView.Adapter<UploadedFilesAdapter.ViewHolder> {
    /** One client for the whole adapter: OkHttp owns its connection and thread pools, and
     *  building one per tap on View throws all of that away each time. */
    private static final okhttp3.OkHttpClient HTTP = new okhttp3.OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    /**
     * Where a downloaded report is parked before being handed to a viewer app.
     *
     * It sits UNDER the camera folder for one reason: FileProvider can only grant a
     * content:// URI for a path that res/xml/file_paths.xml declares, and that file declares
     * exactly one — `<cache-path name="camera" path="camera/" />`. Adding
     * `<cache-path name="reports" path="reports/" />` there is all it would take to move
     * these out on their own, and this constant is the only line that would change.
     */
    private static final String CACHE_SUBDIR = "camera/reports";

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
        private TextView reportDateText;
        private TextView statusText;
        private MaterialButton deleteButton;
        private MaterialButton viewButton;
        private MaterialButton analyzeButton;
        private ImageView sharingIcon;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            fileNameText = itemView.findViewById(R.id.file_name);
            reportTypeText = itemView.findViewById(R.id.report_type);
            reportDateText = itemView.findViewById(R.id.report_date);
            statusText = itemView.findViewById(R.id.report_status);
            deleteButton = itemView.findViewById(R.id.delete_button);
            viewButton = itemView.findViewById(R.id.view_button);
            analyzeButton = itemView.findViewById(R.id.analyze_button);
            sharingIcon = itemView.findViewById(R.id.sharing_icon);
        }

        public void bind(UploadedFile file, int position) {
            fileNameText.setText(file.getName());
            reportTypeText.setText(file.getReportType());

            // Test date where the lab printed one, upload date otherwise — the same
            // value the analysis dialog and the trend charts use.
            String when = file.getReportDateText();
            if (when != null) {
                reportDateText.setText(when);
                reportDateText.setVisibility(View.VISIBLE);
            } else {
                reportDateText.setVisibility(View.GONE);
            }

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

        /**
         * Open a stored report.
         *
         * The report's `fileUrl` is `/api/health/reports/file/<id>` (services/fileStore.js),
         * and that route sits behind requireUser — serveFile even re-checks that the file
         * belongs to the caller. Handing that URL to ACTION_VIEW hands it to a DIFFERENT app,
         * which has none of this app's credentials: the viewer fetched it with no bearer
         * token, got a 401 JSON body back, and every attempt to look at a stored report
         * failed. There is no way to attach a token to an intent, so the fetch has to happen
         * here, where the token is, and the viewer gets the local copy instead.
         *
         * Anything that is NOT one of our own URLs is left exactly as it was — an absolute
         * third-party link carries no auth of ours and the viewer can fetch it itself.
         */
        private void openFileViewer(Context context, UploadedFile file) {
            final String fileUrl = file.getFileUrl();
            final boolean isOurApi = fileUrl.startsWith("/") || fileUrl.startsWith(ApiConfig.BASE_URL);
            final String absoluteUrl = fileUrl.startsWith("/") ? ApiConfig.BASE_URL + fileUrl : fileUrl;

            if (!isOurApi) {
                launchViewer(context, Uri.parse(absoluteUrl), resolveMimeType(file, null));
                return;
            }

            TokenManager tokenManager = TokenManager.getInstance(context);
            final String token = tokenManager != null ? tokenManager.getToken() : null;
            if (token == null || token.isEmpty()) {
                // The same thing the request would have said, said before the round trip.
                Utilities.toast(context, "Please sign in again to open this report");
                return;
            }

            // A report can be several megabytes over a phone connection, so say something
            // before the wait rather than leaving the tap looking ignored.
            Utilities.toast(context, "Opening report\u2026");

            // Application context and an explicit main-thread handler: the callback comes back
            // on an OkHttp thread, and this row's Activity may be gone by then.
            final Context appContext = context.getApplicationContext();
            final Handler main = new Handler(Looper.getMainLooper());

            okhttp3.Request request = new okhttp3.Request.Builder()
                    .url(absoluteUrl)
                    .header("Authorization", "Bearer " + token)
                    .get()
                    .build();

            HTTP.newCall(request).enqueue(new okhttp3.Callback() {
                @Override
                public void onFailure(okhttp3.Call call, IOException e) {
                    ApiConfig.logRestCall(absoluteUrl, false, e.getMessage());
                    main.post(() -> Utilities.toast(appContext,
                            "Couldn't open this report. Check your connection."));
                }

                @Override
                public void onResponse(okhttp3.Call call, okhttp3.Response response) {
                    final int code = response.code();
                    try (okhttp3.Response closeable = response) {
                        okhttp3.ResponseBody body = closeable.body();
                        if (!closeable.isSuccessful() || body == null) {
                            ApiConfig.logRestCall(absoluteUrl, false, "HTTP " + code);
                            main.post(() -> Utilities.toast(appContext,
                                    (code == 401 || code == 403)
                                            ? "You don't have access to this report"
                                            : "Couldn't open this report"));
                            return;
                        }
                        // What the server says it stored, used only where the file name and
                        // the record could not say (serveFile sets it from the FileStore doc).
                        final String mimeType = resolveMimeType(file, closeable.header("Content-Type"));
                        final File local = writeToCache(appContext, file, body.byteStream());
                        ApiConfig.logRestCall(absoluteUrl, true, "report downloaded for viewing");
                        main.post(() -> openLocalCopy(appContext, local, mimeType));
                    } catch (IOException e) {
                        ApiConfig.logRestCall(absoluteUrl, false, e.getMessage());
                        main.post(() -> Utilities.toast(appContext, "Couldn't save this report to open it"));
                    }
                }
            });
        }

        /** Hand the downloaded copy to a viewer as a content:// URI. */
        private void openLocalCopy(Context context, File local, String mimeType) {
            Uri uri;
            try {
                uri = FileProvider.getUriForFile(context,
                        context.getPackageName() + ".fileprovider", local);
            } catch (IllegalArgumentException e) {
                // Only reachable if CACHE_SUBDIR stops matching res/xml/file_paths.xml —
                // named plainly rather than crashing the row.
                Utilities.toast(context, "Couldn't open this report");
                return;
            }
            launchViewer(context, uri, mimeType);
        }

        /** The original intent, unchanged — it just receives a URI a viewer can actually read. */
        private void launchViewer(Context context, Uri uri, String mimeType) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mimeType);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);

            try {
                context.startActivity(intent);
            } catch (Exception e) {
                Utilities.toast(context, "No app found to open this file");
            }
        }

        /** The file-name rules this screen always used, plus the server's own answer last. */
        private String resolveMimeType(UploadedFile file, String serverContentType) {
            String mimeType = file.getFileType();
            if (mimeType == null || mimeType.isEmpty() || mimeType.equals("application/octet-stream")) {
                String fileName = file.getName() == null ? "" : file.getName().toLowerCase();
                if (fileName.endsWith(".pdf")) {
                    mimeType = "application/pdf";
                } else if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
                    mimeType = "image/jpeg";
                } else if (fileName.endsWith(".png")) {
                    mimeType = "image/png";
                }
            }
            if ((mimeType == null || mimeType.isEmpty() || mimeType.equals("application/octet-stream"))
                    && serverContentType != null && !serverContentType.isEmpty()) {
                int semicolon = serverContentType.indexOf(';');
                mimeType = (semicolon > 0 ? serverContentType.substring(0, semicolon) : serverContentType).trim();
            }
            return mimeType;
        }

        /**
         * Write the response body into the cache. Re-downloaded on every tap rather than
         * reused: a half-written copy from a connection that dropped is indistinguishable
         * from a complete one, and opening a truncated PDF is a worse failure than waiting.
         * The copy is left behind for the viewer to read and the OS reclaims the cache.
         */
        private File writeToCache(Context context, UploadedFile file, InputStream in) throws IOException {
            File dir = new File(context.getCacheDir(), CACHE_SUBDIR);
            if (!dir.exists() && !dir.mkdirs()) throw new IOException("Couldn't create the cache folder");
            File out = new File(dir, cacheFileName(file));
            try (FileOutputStream fos = new FileOutputStream(out)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    fos.write(buffer, 0, read);
                }
            }
            return out;
        }

        /** Report id + the stored name, stripped to characters a file system will take. The
         *  name is kept because viewers show it, and its extension helps them choose. */
        private String cacheFileName(UploadedFile file) {
            String id = sanitize(file.getReportId(), "report");
            String name = sanitize(file.getName(), "file");
            return id + "_" + name;
        }

        private String sanitize(String value, String fallback) {
            String cleaned = value == null ? "" : value.replaceAll("[^A-Za-z0-9._-]", "_");
            return cleaned.isEmpty() ? fallback : cleaned;
        }
    }
}
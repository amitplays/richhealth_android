package Adapters;
import Utils.Utilities;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.example.richhealth.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import Models.Podcast;
import Utils.ProStatusManager;
import Utils.ProUpgradeDialog;

public class PodcastAdapter extends RecyclerView.Adapter<PodcastAdapter.PodcastViewHolder> {
    private List<Podcast> podcasts;
    private Context context;
    private OnPodcastClickListener listener;

    // Track which podcast is currently playing
    private long nowPlayingId = -1;
    private boolean isPaused = true;

    // Track expanded position - only one can be expanded at a time
    private int expandedPosition = -1;

    private ProStatusManager proStatusManager;
    private List<Long> proOnlyPodcastIds = new ArrayList<>(java.util.Arrays.asList(2L, 3L));

    public interface OnPodcastClickListener {
        void onPlayClick(Podcast podcast);
        void onPauseClick(Podcast podcast);
    }

    public PodcastAdapter(Context context, List<Podcast> podcasts, OnPodcastClickListener listener) {
        this.context = context;
        this.podcasts = podcasts;
        this.listener = listener;
        this.proStatusManager = ProStatusManager.getInstance(context);
    }

    public void setNowPlayingId(long id) {
        this.nowPlayingId = id;
        this.isPaused = false;
    }

    public void setPaused(boolean paused) {
        this.isPaused = paused;
    }

    @NonNull
    @Override
    public PodcastViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_podcast, parent, false);
        return new PodcastViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PodcastViewHolder holder, int position) {
        Podcast podcast = podcasts.get(position);
        boolean isProOnly = proOnlyPodcastIds.contains(podcast.getId());

        holder.titleText.setText(podcast.getTitle());
        holder.descriptionText.setText(podcast.getDescription());

        // Source links setup
        if (!podcast.getSourceLinks().isEmpty()) {
            holder.sourcesText.setText("Sources: " + podcast.getSourceLinks().size());
            holder.sourceLinksButton.setVisibility(View.VISIBLE);
            holder.sourceLinksButton.setOnClickListener(v -> showSourceLinksDialog(podcast.getSourceLinks()));
        } else {
            holder.sourcesText.setText("No Sources");
            holder.sourceLinksButton.setVisibility(View.GONE);
        }

        // Set date
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        holder.dateText.setText("Added: " + dateFormat.format(podcast.getAddedDate()));

        // [PLAN-PILL-REVIEW] removed (hardcoded/dead plan pill; will review later)

        // Play/Pause button state
        boolean isThisPlaying = podcast.getId() == nowPlayingId && !isPaused;
        holder.playPauseButton.setImageResource(isThisPlaying ? R.drawable.ic_pause : R.drawable.ic_play);

        // Play/Pause click listener
        holder.playPauseButton.setOnClickListener(v -> {
            if (isProOnly && !proStatusManager.isProUser()) {
                showProUpgradeDialog();
            } else {
                if (podcast.getId() == nowPlayingId && !isPaused) {
                    listener.onPauseClick(podcast);
                } else {
                    listener.onPlayClick(podcast);
                }
            }
        });

        // Expandable content logic (unchanged from your original code)
        final boolean isExpanded = position == expandedPosition;
//        holder.expandableContent.setVisibility(isExpanded ? View.VISIBLE : View.GONE);

        holder.expandableContent.setVisibility(View.VISIBLE);
        holder.itemView.setOnClickListener(v -> {
            int previousExpandedPosition = expandedPosition;
            expandedPosition = isExpanded ? -1 : position;

            if (previousExpandedPosition >= 0 && previousExpandedPosition != position) {
                notifyItemChanged(previousExpandedPosition);
            }

            notifyItemChanged(position);
        });
    }

    private void showSourceLinksDialog(List<String> sourceLinks) {
        if (sourceLinks == null || sourceLinks.isEmpty()) {
            Utilities.toast(context, "No source links available");
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.AlertDialogTheme);
        builder.setTitle("Source Links");

        String[] links = sourceLinks.toArray(new String[0]);
        builder.setItems(links, (dialog, which) -> {
            try {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(links[which]));
                context.startActivity(browserIntent);
            } catch (Exception e) {
                Utilities.toast(context, "Unable to open link");
            }
        });

        builder.setNegativeButton("Close", null);
        builder.show();
    }

    private void showProUpgradeDialog() {
        new ProUpgradeDialog((android.app.Activity) context).show(isPro -> {
            if (isPro) notifyDataSetChanged();
        });
    }

    @Override
    public int getItemCount() {
        return podcasts.size();
    }

    static class PodcastViewHolder extends RecyclerView.ViewHolder {
        TextView titleText;
        TextView descriptionText;
        TextView dateText;
        TextView sourcesText;
        // [PLAN-PILL-REVIEW] removed (hardcoded/dead plan pill; will review later)
        ImageButton playPauseButton;
        ImageButton sourceLinksButton;
        LinearLayout expandableContent;

        PodcastViewHolder(@NonNull View itemView) {
            super(itemView);
            titleText = itemView.findViewById(R.id.podcast_title);
            descriptionText = itemView.findViewById(R.id.podcast_description);
            dateText = itemView.findViewById(R.id.podcast_date);
            sourcesText = itemView.findViewById(R.id.podcast_sources);
            // [PLAN-PILL-REVIEW] removed (hardcoded/dead plan pill; will review later)
            playPauseButton = itemView.findViewById(R.id.play_pause_button);
            sourceLinksButton = itemView.findViewById(R.id.source_links_button);
            expandableContent = itemView.findViewById(R.id.expandable_content);
        }
    }
}
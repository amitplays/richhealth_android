package Adapters;

import android.graphics.Bitmap;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.ImageRequest;
import com.android.volley.toolbox.Volley;
import com.example.richhealth.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Horizontal media rail (Podcasts / News) on the redesigned Services page —
 * Android twin of iOS FeedMediaCard: 232dp card, 128dp image (teal→blue
 * placeholder + type icon when there is no image), 2-line title, 1-line
 * per-user reason, "Pro" pill. Tapping any card opens the full Feed
 * (the rail is a preview, exactly like iOS onSeeAll behaviour).
 */
public class MediaRailAdapter extends RecyclerView.Adapter<MediaRailAdapter.ViewHolder> {

    /** Minimal rail item — deliberately tiny; the full Feed screen has the rich model. */
    public static class Item {
        public String title = "";
        public String reason = "";
        public String imageUrl = "";
        public boolean isProOnly = false;
        public boolean isPodcast = false;
    }

    public interface OnItemClick { void onClick(Item item); }

    private final List<Item> items = new ArrayList<>();
    private final OnItemClick onClick;
    private RequestQueue queue;

    // Small shared bitmap cache so rail scrolling doesn't re-download covers.
    private static final LruCache<String, Bitmap> IMAGE_CACHE = new LruCache<>(24);

    public MediaRailAdapter(OnItemClick onClick) {
        this.onClick = onClick;
    }

    public void setItems(List<Item> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_media_card, parent, false);
        if (queue == null) queue = Volley.newRequestQueue(parent.getContext().getApplicationContext());
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        Item item = items.get(position);
        h.title.setText(item.title);
        h.reason.setText(item.reason);
        h.reason.setVisibility(item.reason == null || item.reason.isEmpty() ? View.GONE : View.VISIBLE);
        h.proPill.setVisibility(item.isProOnly ? View.VISIBLE : View.GONE);
        h.typeIcon.setImageResource(item.isPodcast ? R.drawable.ic_podcast : R.drawable.ic_feed);
        h.itemView.setOnClickListener(v -> { if (onClick != null) onClick.onClick(item); });

        // Image: placeholder gradient + icon until (and unless) the cover loads.
        h.image.setVisibility(View.GONE);
        h.image.setTag(item.imageUrl);
        if (item.imageUrl != null && !item.imageUrl.isEmpty()) {
            Bitmap cached = IMAGE_CACHE.get(item.imageUrl);
            if (cached != null) {
                h.image.setImageBitmap(cached);
                h.image.setVisibility(View.VISIBLE);
            } else {
                final String url = item.imageUrl;
                ImageRequest req = new ImageRequest(url,
                        bmp -> {
                            IMAGE_CACHE.put(url, bmp);
                            // Only apply if this holder still shows the same item.
                            if (url.equals(h.image.getTag())) {
                                h.image.setImageBitmap(bmp);
                                h.image.setVisibility(View.VISIBLE);
                            }
                        },
                        800, 600, ImageView.ScaleType.CENTER_CROP, Bitmap.Config.RGB_565,
                        error -> { /* keep placeholder */ });
                queue.add(req);
            }
        }
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView image, typeIcon;
        final TextView title, reason, proPill;
        ViewHolder(@NonNull View v) {
            super(v);
            image = v.findViewById(R.id.media_card_image);
            typeIcon = v.findViewById(R.id.media_card_type_icon);
            title = v.findViewById(R.id.media_card_title);
            reason = v.findViewById(R.id.media_card_reason);
            proPill = v.findViewById(R.id.media_card_pro_pill);
        }
    }
}

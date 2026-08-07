package com.example.richhealth.Activities;
import Utils.Utilities;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.MediaPlayer;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.richhealth.R;
import com.google.android.material.snackbar.Snackbar;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import org.json.JSONArray;
import org.json.JSONObject;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.ImageRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import android.graphics.Bitmap;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import Models.Podcast;
import Utils.ApiConfig;
import Utils.ProStatusManager;
import Utils.ProUpgradeDialog;

/**
 * Health Intel feed — the "Feed" tab of the AI screen. Ported from the old
 * HealthFeedActivity so the logic (local podcasts, filters, mini-player, pro
 * gating) is reused, with a social-style card that colours each media type.
 */
public class HealthFeedFragment extends Fragment {

    private RecyclerView feedRecycler;
    private View emptyState;
    private FeedAdapter adapter;

    // Search field (same thin field as the chat-history side panel); filters the
    // loaded feed live against title / description / source / category.
    private EditText searchInput;

    private final List<FeedItem> allItems = new ArrayList<>();
    private final List<FeedItem> filteredItems = new ArrayList<>();
    // Local podcasts are kept separately so they can always sit at the END of the
    // feed (news/articles are fresher; podcasts are hardcoded and would otherwise
    // dominate the top).
    private final List<FeedItem> podcastItems = new ArrayList<>();
    private String currentQuery = "";

    // Mini player
    private View miniPlayerBar;
    private TextView miniPlayerTitle, miniPlayerTime;
    private ImageButton miniPlayPause, miniClose;
    private SeekBar miniSeekbar;
    private MediaPlayer mediaPlayer;
    private Podcast currentPodcast;
    private final Handler seekbarHandler = new Handler(Looper.getMainLooper());

    // Networking for the feed + source favicons (single queue, small in-memory cache).
    private RequestQueue requestQueue;
    private final Map<String, Bitmap> faviconCache = new HashMap<>();

    // Richie logo that spins on the News pill while the feed is fetching.
    private ImageView newsSpinner;
    private ObjectAnimator newsSpinAnimator;

    // Swipe-to-dismiss: hidden backend item ids are kept device-locally so a
    // removed news/article stays gone for this user across reloads and app restarts.
    private static final String FEED_PREFS = "feed_prefs";
    private static final String KEY_HIDDEN_IDS = "hidden_feed_ids";
    private static final String KEY_SWIPE_TAUGHT = "feed_swipe_taught";
    private final Set<String> hiddenIds = new HashSet<>();
    private boolean swipeHintShown = false;

    private RequestQueue queue() {
        if (requestQueue == null) requestQueue = Volley.newRequestQueue(requireContext().getApplicationContext());
        return requestQueue;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_health_feed, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Search box — filters the loaded feed live (focus visuals handled by the
        // field's own background selector, same as the chat-history search).
        searchInput = view.findViewById(R.id.feed_search_input);
        searchInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                currentQuery = s == null ? "" : s.toString();
                applyFilter();
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });

        loadHiddenIds();

        feedRecycler = view.findViewById(R.id.feed_recycler);
        emptyState = view.findViewById(R.id.empty_state);
        newsSpinner = view.findViewById(R.id.feed_news_spinner);
        feedRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new FeedAdapter();
        feedRecycler.setAdapter(adapter);
        attachSwipeToDismiss();

        miniPlayerBar = view.findViewById(R.id.feed_mini_player);
        miniPlayerTitle = view.findViewById(R.id.feed_mini_player_title);
        miniPlayerTime = view.findViewById(R.id.feed_mini_player_time);
        miniPlayPause = view.findViewById(R.id.feed_mini_play_pause);
        miniClose = view.findViewById(R.id.feed_mini_close);
        miniSeekbar = view.findViewById(R.id.feed_mini_seekbar);

        miniPlayPause.setOnClickListener(v -> {
            if (mediaPlayer == null) return;
            if (mediaPlayer.isPlaying()) pausePodcast(); else resumePodcast();
        });
        miniClose.setOnClickListener(v -> stopPodcast());
        miniSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser && mediaPlayer != null) mediaPlayer.seekTo(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        loadFeedItems();
    }

    private void loadFeedItems() {
        podcastItems.clear();
        for (Podcast p : createLocalPodcasts()) {
            FeedItem item = new FeedItem();
            item.type = FeedItem.TYPE_PODCAST;
            item.title = p.getTitle();
            item.description = p.getDescription();
            item.category = p.getCategory();
            item.source = "RichHealth Audio";
            item.date = p.getAddedDate();
            item.podcast = p;
            item.sourceLinks = p.getSourceLinks();
            item.isProOnly = (p.getId() == 2 || p.getId() == 3);
            podcastItems.add(item);
        }

        // First paint shows podcasts; once the backend pool loads, news/articles go
        // on top and podcasts drop to the end (see rebuild in fetchFeedFromBackend).
        allItems.clear();
        allItems.addAll(podcastItems);
        applyFilter();
        fetchFeedFromBackend();
    }

    /**
     * Loads news + articles from the backend shared pool (GET /api/feed). The server
     * returns items newest-first with a per-user "reason" ("why we suggested this").
     * Local podcasts stay as-is; we append the fetched items and refresh.
     */
    private void fetchFeedFromBackend() {
        if (!isAdded()) return;
        String url = ApiConfig.BASE_URL + "/api/feed?limit=50";
        final String token = TokenManager.getInstance(requireContext()).getToken();

        startNewsSpin();
        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET, url, null,
                response -> {
                    stopNewsSpin();
                    if (!isAdded()) return;
                    try {
                        JSONArray items = response.optJSONArray("items");
                        if (items == null) return;
                        // Rebuild: backend news/articles first (newest-first from the
                        // server), then local podcasts appended at the end.
                        List<FeedItem> fetched = new ArrayList<>();
                        for (int i = 0; i < items.length(); i++) {
                            JSONObject o = items.optJSONObject(i);
                            if (o == null) continue;
                            FeedItem item = new FeedItem();
                            item.id = o.optString("_id", "");
                            // Skip anything this user has swiped away (kept device-local).
                            if (!item.id.isEmpty() && hiddenIds.contains(item.id)) continue;
                            item.type = o.optString("type", FeedItem.TYPE_NEWS);
                            item.title = o.optString("title", "");
                            item.description = o.optString("description", "");
                            item.category = o.optString("category", "");
                            item.url = o.optString("url", "");
                            item.reason = o.optString("reason", "");
                            item.isProOnly = o.optBoolean("isProOnly", false);
                            item.date = parseFeedDate(o.optString("publishedAt",
                                    o.optString("createdAt", "")));
                            // Articles use sourceLinks for the "read" action; fall back to url.
                            // The first sourceLink's label is the publisher name shown in the header.
                            JSONArray sl = o.optJSONArray("sourceLinks");
                            if (sl != null && sl.length() > 0) {
                                List<String> links = new ArrayList<>();
                                for (int j = 0; j < sl.length(); j++) {
                                    JSONObject s = sl.optJSONObject(j);
                                    String link = s != null ? s.optString("url", "") : sl.optString(j, "");
                                    if (link != null && !link.isEmpty()) links.add(link);
                                    if (j == 0 && s != null) {
                                        String lbl = s.optString("label", "");
                                        if (!lbl.isEmpty()) item.source = lbl;
                                    }
                                }
                                if (!links.isEmpty()) item.sourceLinks = links;
                            }
                            if (item.source == null || item.source.isEmpty()) {
                                item.source = o.optString("fetchedFrom", "");
                            }
                            if (item.sourceLinks == null && !item.url.isEmpty()) {
                                item.sourceLinks = Arrays.asList(item.url);
                            }
                            if (!item.title.isEmpty()) fetched.add(item);
                        }
                        allItems.clear();
                        allItems.addAll(fetched);      // news/articles first (newest)
                        allItems.addAll(podcastItems); // podcasts last
                        applyFilter();
                        // Teach the swipe gesture once, now that real (swipeable)
                        // items are on screen.
                        maybeShowSwipeHint();
                    } catch (Exception e) {
                        Utilities.toast(requireContext(), "Feed parse error");
                    }
                },
                error -> {
                    // Silent: podcasts still show; feed just wasn't reachable this time.
                    stopNewsSpin();
                }) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> h = new HashMap<>();
                if (token != null) h.put("Authorization", "Bearer " + token);
                return h;
            }
        };
        queue().add(request);
    }

    /** Spin the Richie logo on the News pill while a fetch is in flight. */
    private void startNewsSpin() {
        if (newsSpinner == null) return;
        newsSpinner.setVisibility(View.VISIBLE);
        if (newsSpinAnimator == null) {
            newsSpinAnimator = ObjectAnimator.ofFloat(newsSpinner, View.ROTATION, 0f, 360f);
            newsSpinAnimator.setDuration(1900);
            newsSpinAnimator.setRepeatCount(ObjectAnimator.INFINITE);
            newsSpinAnimator.setInterpolator(new LinearInterpolator());
        }
        if (!newsSpinAnimator.isRunning()) newsSpinAnimator.start();
    }

    private void stopNewsSpin() {
        if (newsSpinAnimator != null && newsSpinAnimator.isRunning()) newsSpinAnimator.cancel();
        if (newsSpinner != null) {
            newsSpinner.setRotation(0f);
            newsSpinner.setVisibility(View.GONE);
        }
    }

    /** Strip HTML tags / decode a few entities / collapse whitespace from feed text.
     *  Also removes a trailing UNCLOSED tag (e.g. an `<a href="…very long url` that a
     *  server-side truncation cut before its closing `>`), which a plain tag strip
     *  would otherwise leave on screen. */
    private String cleanText(String s) {
        if (s == null) return "";
        String out = s.replaceAll("(?s)<[^>]*>", " ")
                .replaceAll("<[^>]*$", " ") // dangling unclosed tag from truncation
                .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return out;
    }

    /** Host of a URL, without leading www. — used for the favicon lookup. */
    private String domainOf(String url) {
        try {
            String host = Uri.parse(url).getHost();
            if (host == null) return "";
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Loads the source's favicon into the header icon (Google's favicon service),
     * with a globe fallback. Recycle-safe via a tag on the ImageView; cached in
     * memory so scrolling doesn't refetch.
     */
    private void loadSourceIcon(ImageView iv, String pageUrl) {
        iv.setImageResource(R.drawable.ic_public);
        iv.setImageTintList(ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.rh_text_tertiary)));
        String domain = domainOf(pageUrl);
        if (domain.isEmpty()) return;
        final String favUrl = "https://www.google.com/s2/favicons?sz=64&domain=" + domain;
        iv.setTag(favUrl);
        Bitmap cached = faviconCache.get(favUrl);
        if (cached != null) {
            iv.setImageTintList(null);
            iv.setImageBitmap(cached);
            return;
        }
        ImageRequest req = new ImageRequest(favUrl,
                bmp -> {
                    faviconCache.put(favUrl, bmp);
                    if (favUrl.equals(iv.getTag())) {
                        iv.setImageTintList(null);
                        iv.setImageBitmap(bmp);
                    }
                },
                64, 64, ImageView.ScaleType.FIT_CENTER, Bitmap.Config.ARGB_8888,
                err -> { /* keep the globe fallback */ });
        queue().add(req);
    }

    /**
     * "Why you're seeing this" — opens the app-standard dialog (same design as the
     * profile dialogs) with a Richie intro + the AI/personalized one-liner. Uses the
     * per-user reason from the list immediately, and quietly upgrades it to the
     * backend's AI sentence (GET /api/feed/:id → aiReason) when that arrives.
     */
    private void showWhyDialog(FeedItem item) {
        if (!isAdded()) return;
        final Dialog dialog = new Dialog(requireContext(), R.style.DialogTheme);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        View content = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_feed_why, null);
        dialog.setContentView(content);

        final TextView reasonView = content.findViewById(R.id.why_reason);
        String line = (item.reason != null && !item.reason.isEmpty())
                ? item.reason : "Handpicked for your health feed.";
        reasonView.setText(line);
        content.findViewById(R.id.why_got_it).setOnClickListener(v -> dialog.dismiss());

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
            lp.copyFrom(dialog.getWindow().getAttributes());
            lp.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.88);
            dialog.getWindow().setAttributes(lp);
        }
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();

        // Upgrade the line to Richie's freshly-written AI sentence when it arrives.
        if (item.id != null && !item.id.isEmpty()) {
            final String token = TokenManager.getInstance(requireContext()).getToken();
            String url = ApiConfig.BASE_URL + "/api/feed/" + item.id;
            JsonObjectRequest req = new JsonObjectRequest(Request.Method.GET, url, null,
                    resp -> {
                        String ai = resp.optString("aiReason", "");
                        if (!ai.isEmpty()) {
                            item.reason = ai;
                            if (dialog.isShowing()) reasonView.setText(ai);
                        }
                    },
                    err -> {}) {
                @Override public Map<String, String> getHeaders() {
                    Map<String, String> h = new HashMap<>();
                    if (token != null) h.put("Authorization", "Bearer " + token);
                    return h;
                }
            };
            queue().add(req);
        }
    }

    /** Parse an ISO date string from the backend; null-safe, falls back to now. */
    private Date parseFeedDate(String iso) {
        if (iso == null || iso.isEmpty()) return new Date();
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            // Trim milliseconds/zone suffix (…T01:03:00.000Z → …T01:03:00).
            String trimmed = iso.length() >= 19 ? iso.substring(0, 19) : iso;
            return f.parse(trimmed);
        } catch (Exception e) {
            return new Date();
        }
    }

    private List<Podcast> createLocalPodcasts() {
        List<Podcast> podcasts = new ArrayList<>();

        Podcast p1 = new Podcast(1, "Health & Wellness Basics 🌿",
                "A beginner-friendly overview of foundational health habits including nutrition, exercise, sleep hygiene, and stress management techniques for everyday life.",
                "sample_podcast", 180, "Wellness", 13);
        p1.setTags(Arrays.asList("Basics", "Lifestyle", "Self-Care"));
        p1.setSourceLinks(Arrays.asList("https://www.ncbi.nlm.nih.gov/research-paper-1", "https://scholar.google.com/study-link"));

        Podcast p2 = new Podcast(2, "Sunlight and Vitamin D Synthesis 🌞",
                "Detailed exploration of how sunlight triggers vitamin D synthesis in the human body, discussing its crucial role in bone health, immune function, and overall well-being.",
                "vit_d", 180, "Fitness", 13);
        p2.setTags(Arrays.asList("Wellness", "Nutrition", "Vitamin D"));
        p2.setSourceLinks(Arrays.asList("https://www.ncbi.nlm.nih.gov/research-paper-1", "https://scholar.google.com/study-link"));

        Podcast p3 = new Podcast(3, "Cold Showers and Immune Function 🥶",
                "Explore the potential for regular cold exposure to enhance immune resilience, improve stress management through cross-adaptation, and offer practical benefits for health and recovery.",
                "cold", 180, "Health", 13);
        p3.setTags(Arrays.asList("Immune System", "Recovery", "Stress Management"));
        p3.setSourceLinks(Arrays.asList("https://www.ncbi.nlm.nih.gov/research-paper-1", "https://scholar.google.com/study-link"));

        Podcast p4 = new Podcast(4, "The Science of Aging 🧬",
                "Deep dive into the biological mechanisms of aging, from telomere shortening to cellular senescence, and evidence-based strategies to promote longevity and healthy aging.",
                "aging", 180, "Science", 13);
        p4.setTags(Arrays.asList("Longevity", "Biology", "Anti-Aging"));
        p4.setSourceLinks(Arrays.asList("https://www.ncbi.nlm.nih.gov/research-paper-1", "https://scholar.google.com/study-link"));

        podcasts.add(p1);
        podcasts.add(p2);
        podcasts.add(p3);
        podcasts.add(p4);
        return podcasts;
    }

    /** Filters the loaded feed by the search query against title / description /
     *  source / category (mirrors the chat-history search). Empty query shows all. */
    private void applyFilter() {
        filteredItems.clear();
        String q = currentQuery == null ? "" : currentQuery.trim().toLowerCase(Locale.US);

        for (FeedItem item : allItems) {
            if (q.isEmpty() || matchesQuery(item, q)) {
                filteredItems.add(item);
            }
        }

        if (filteredItems.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            feedRecycler.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            feedRecycler.setVisibility(View.VISIBLE);
        }
        adapter.notifyDataSetChanged();
    }

    private boolean matchesQuery(FeedItem item, String q) {
        String title = item.title == null ? "" : item.title.toLowerCase(Locale.US);
        String desc = item.description == null ? "" : item.description.toLowerCase(Locale.US);
        String source = item.source == null ? "" : item.source.toLowerCase(Locale.US);
        String category = item.category == null ? "" : item.category.toLowerCase(Locale.US);
        return title.contains(q) || desc.contains(q) || source.contains(q) || category.contains(q);
    }

    // ── Swipe to dismiss (news / articles only) ──

    private SharedPreferences prefs() {
        return requireContext().getSharedPreferences(FEED_PREFS, Context.MODE_PRIVATE);
    }

    /** Load the device-local set of hidden item ids into memory. */
    private void loadHiddenIds() {
        hiddenIds.clear();
        hiddenIds.addAll(prefs().getStringSet(KEY_HIDDEN_IDS, new HashSet<>()));
    }

    /** Persist an id as hidden (store a fresh copy — the returned set must not be mutated). */
    private void hideFeedId(String id) {
        if (id == null || id.isEmpty()) return;
        hiddenIds.add(id);
        prefs().edit().putStringSet(KEY_HIDDEN_IDS, new HashSet<>(hiddenIds)).apply();
    }

    private void unhideFeedId(String id) {
        if (id == null || id.isEmpty()) return;
        hiddenIds.remove(id);
        prefs().edit().putStringSet(KEY_HIDDEN_IDS, new HashSet<>(hiddenIds)).apply();
    }

    /**
     * Notification-style swipe: only the foreground card moves (via getDefaultUIUtil),
     * revealing the "Slide to remove" hint underneath. Podcasts (no backend id) aren't
     * swipeable. On swipe we remove the row and offer a 5s Undo before it's persisted.
     */
    private void attachSwipeToDismiss() {
        ItemTouchHelper.SimpleCallback cb = new ItemTouchHelper.SimpleCallback(
                0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {

            @Override
            public int getSwipeDirs(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
                int pos = vh.getAdapterPosition();
                if (pos < 0 || pos >= filteredItems.size()) return 0;
                FeedItem item = filteredItems.get(pos);
                // Backend news/articles have an id and can be hidden for this user.
                if (item.id == null || item.id.isEmpty()) return 0;
                return ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT;
            }

            @Override
            public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh,
                                  @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSelectedChanged(RecyclerView.ViewHolder vh, int actionState) {
                if (vh instanceof FeedAdapter.ViewHolder) {
                    FeedAdapter.ViewHolder h = (FeedAdapter.ViewHolder) vh;
                    // Reveal the hint only once the user actually starts swiping.
                    if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                        h.swipeHint.setVisibility(View.VISIBLE);
                    }
                    getDefaultUIUtil().onSelected(h.foreground);
                }
            }

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView rv,
                                    @NonNull RecyclerView.ViewHolder vh, float dX, float dY,
                                    int actionState, boolean isCurrentlyActive) {
                if (vh instanceof FeedAdapter.ViewHolder) {
                    getDefaultUIUtil().onDraw(c, rv, ((FeedAdapter.ViewHolder) vh).foreground,
                            dX, dY, actionState, isCurrentlyActive);
                }
            }

            @Override
            public void onChildDrawOver(@NonNull Canvas c, @NonNull RecyclerView rv,
                                        RecyclerView.ViewHolder vh, float dX, float dY,
                                        int actionState, boolean isCurrentlyActive) {
                if (vh instanceof FeedAdapter.ViewHolder) {
                    getDefaultUIUtil().onDrawOver(c, rv, ((FeedAdapter.ViewHolder) vh).foreground,
                            dX, dY, actionState, isCurrentlyActive);
                }
            }

            @Override
            public void clearView(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
                if (vh instanceof FeedAdapter.ViewHolder) {
                    FeedAdapter.ViewHolder h = (FeedAdapter.ViewHolder) vh;
                    getDefaultUIUtil().clearView(h.foreground);
                    h.swipeHint.setVisibility(View.GONE); // back to clean at rest
                }
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) {
                dismissItemAt(vh.getAdapterPosition());
            }
        };
        new ItemTouchHelper(cb).attachToRecyclerView(feedRecycler);
    }

    /** Remove a row with a 5s Undo; only persist the dismissal if Undo wasn't used. */
    private void dismissItemAt(int pos) {
        if (pos < 0 || pos >= filteredItems.size()) return;
        final FeedItem item = filteredItems.get(pos);
        final int filteredPos = pos;
        final boolean wasInAll = allItems.contains(item);

        filteredItems.remove(pos);
        adapter.notifyItemRemoved(pos);
        refreshEmptyState();

        Snackbar sb = Snackbar.make(feedRecycler, "Removed from your feed", 5000);
        sb.setAction("Undo", v -> {
            int insertAt = Math.min(filteredPos, filteredItems.size());
            filteredItems.add(insertAt, item);
            adapter.notifyItemInserted(insertAt);
            refreshEmptyState();
            feedRecycler.scrollToPosition(insertAt);
        });
        sb.addCallback(new Snackbar.Callback() {
            @Override
            public void onDismissed(Snackbar s, int event) {
                if (event == DISMISS_EVENT_ACTION) return; // undone — keep it
                hideFeedId(item.id);
                if (wasInAll) allItems.remove(item); // don't let search re-surface it
            }
        });
        sb.show();
    }

    private void refreshEmptyState() {
        boolean empty = filteredItems.isEmpty();
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        feedRecycler.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    /** One-time teaching nudge: slide the first card ~30% to reveal the hint, then back. */
    private void maybeShowSwipeHint() {
        if (swipeHintShown || !isAdded()) return;
        if (prefs().getBoolean(KEY_SWIPE_TAUGHT, false)) { swipeHintShown = true; return; }
        if (filteredItems.isEmpty()) return;
        swipeHintShown = true;
        prefs().edit().putBoolean(KEY_SWIPE_TAUGHT, true).apply();
        feedRecycler.postDelayed(() -> {
            if (!isAdded()) return;
            RecyclerView.ViewHolder vh = feedRecycler.findViewHolderForAdapterPosition(0);
            if (vh instanceof FeedAdapter.ViewHolder) {
                animateSwipeHint((FeedAdapter.ViewHolder) vh);
            }
        }, 600);
    }

    private void animateSwipeHint(FeedAdapter.ViewHolder h) {
        final View fg = h.foreground;
        final View hint = h.swipeHint;
        if (fg == null || fg.getWidth() == 0) return;
        hint.setVisibility(View.VISIBLE); // reveal only for the nudge
        float peek = fg.getWidth() * 0.30f;
        ObjectAnimator out = ObjectAnimator.ofFloat(fg, View.TRANSLATION_X, 0f, peek);
        out.setDuration(420);
        out.setInterpolator(new DecelerateInterpolator());
        ObjectAnimator back = ObjectAnimator.ofFloat(fg, View.TRANSLATION_X, peek, 0f);
        back.setStartDelay(700);
        back.setDuration(420);
        back.setInterpolator(new DecelerateInterpolator());
        AnimatorSet set = new AnimatorSet();
        set.playSequentially(out, back);
        set.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) {
                fg.setTranslationX(0f);
                hint.setVisibility(View.GONE); // clean again once the nudge finishes
            }
        });
        set.start();
    }

    // ── Podcast playback ──

    private void playPodcast(Podcast podcast) {
        stopPodcast();
        currentPodcast = podcast;
        try {
            int resId = getResources().getIdentifier(podcast.getAudioResourceName(), "raw", requireContext().getPackageName());
            if (resId == 0) {
                Utilities.toast(requireContext(), "Audio not found");
                return;
            }
            mediaPlayer = MediaPlayer.create(requireContext(), resId);
            if (mediaPlayer == null) {
                Utilities.toast(requireContext(), "Failed to load audio");
                return;
            }
            mediaPlayer.setOnCompletionListener(mp -> stopPodcast());
            mediaPlayer.start();
            showMiniPlayer(podcast);
            adapter.notifyDataSetChanged();
        } catch (Exception e) {
            Utilities.toast(requireContext(), "Playback error");
        }
    }

    private void pausePodcast() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            miniPlayPause.setImageResource(R.drawable.ic_play);
            adapter.notifyDataSetChanged();
        }
    }

    private void resumePodcast() {
        if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
            mediaPlayer.start();
            miniPlayPause.setImageResource(R.drawable.ic_pause);
            startSeekbarUpdate();
            adapter.notifyDataSetChanged();
        }
    }

    private void stopPodcast() {
        seekbarHandler.removeCallbacksAndMessages(null);
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        currentPodcast = null;
        if (miniPlayerBar != null) miniPlayerBar.setVisibility(View.GONE);
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void showMiniPlayer(Podcast podcast) {
        miniPlayerBar.setVisibility(View.VISIBLE);
        miniPlayerTitle.setText(podcast.getTitle());
        miniPlayPause.setImageResource(R.drawable.ic_pause);
        if (mediaPlayer != null) miniSeekbar.setMax(mediaPlayer.getDuration());
        startSeekbarUpdate();
    }

    private final Runnable seekbarUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                int current = mediaPlayer.getCurrentPosition();
                int total = mediaPlayer.getDuration();
                miniSeekbar.setProgress(current);
                int secCurrent = current / 1000;
                int secTotal = total / 1000;
                miniPlayerTime.setText(String.format(Locale.US, "%d:%02d / %d:%02d",
                        secCurrent / 60, secCurrent % 60, secTotal / 60, secTotal % 60));
            }
            seekbarHandler.postDelayed(this, 500);
        }
    };

    private void startSeekbarUpdate() {
        seekbarHandler.removeCallbacksAndMessages(null);
        seekbarHandler.post(seekbarUpdateRunnable);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopPodcast();
        stopNewsSpin();
    }

    // ── Data model ──
    static class FeedItem {
        static final String TYPE_PODCAST = "podcast";
        static final String TYPE_ARTICLE = "article";
        static final String TYPE_NEWS = "news";

        String id;       // backend _id (used to fetch the AI "why" on demand)
        String type;
        String title;
        String description;
        String category;
        String source;   // publisher / feed name shown in the header
        Date date;
        String url;
        String reason;   // per-user "why we suggested this" (from backend)
        boolean isProOnly;
        Podcast podcast;
        List<String> sourceLinks;
    }

    // ── Adapter ──
    class FeedAdapter extends RecyclerView.Adapter<FeedAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_feed_card, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            FeedItem item = filteredItems.get(position);

            // Reset any leftover swipe/teach state on a recycled row.
            holder.foreground.setTranslationX(0f);
            holder.swipeHint.setVisibility(View.GONE);

            holder.title.setText(item.title);

            // Description: hide when empty or low-value (a bare link / leftover markup).
            String desc = cleanText(item.description);
            if (desc.isEmpty() || desc.startsWith("http")) {
                holder.description.setVisibility(View.GONE);
            } else {
                holder.description.setVisibility(View.VISIBLE);
                holder.description.setText(desc);
            }

            // Source (publisher) + favicon in place of the old generic bell.
            String source = item.source != null && !item.source.isEmpty()
                    ? item.source
                    : (FeedItem.TYPE_PODCAST.equals(item.type) ? "RichHealth Audio" : "Health news");
            holder.sourceName.setText(source);
            if (FeedItem.TYPE_PODCAST.equals(item.type)) {
                holder.sourceIcon.setImageResource(R.drawable.ic_podcast);
                holder.sourceIcon.setImageTintList(ColorStateList.valueOf(
                        ContextCompat.getColor(requireContext(), R.color.rh_accent)));
            } else {
                loadSourceIcon(holder.sourceIcon, item.url);
            }

            if (item.category != null && !item.category.isEmpty()) {
                holder.category.setVisibility(View.VISIBLE);
                holder.category.setText(item.category);
            } else {
                holder.category.setVisibility(View.GONE);
            }

            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd", Locale.US);
            holder.date.setText(item.date != null ? sdf.format(item.date) : "");

            // Why-we-suggested: an icon that opens the app-standard reason dialog.
            if (item.reason != null && !item.reason.isEmpty()) {
                holder.whyButton.setVisibility(View.VISIBLE);
                holder.whyButton.setOnClickListener(v -> showWhyDialog(item));
            } else {
                holder.whyButton.setVisibility(View.GONE);
            }

            ProStatusManager proManager = ProStatusManager.getInstance(requireContext());
            // [PLAN-PILL-REVIEW] removed (hardcoded/dead plan pill; will review later)

            holder.playButton.setVisibility(View.GONE);
            holder.sourceButton.setVisibility(View.GONE);
            holder.readButton.setVisibility(View.GONE); // whole card is tappable now — no open icon

            final String primaryLink = primaryLinkOf(item);

            // The whole card is the tap target: open the link (news / article) or toggle
            // playback (podcast). Replaces the old open-in-new icon button in the header.
            holder.foreground.setOnClickListener(v -> {
                if (item.isProOnly && !proManager.isProUser()) { showProUpgradeDialog(); return; }
                if (FeedItem.TYPE_PODCAST.equals(item.type)) {
                    if (item.podcast == null) return;
                    boolean playingThis = currentPodcast != null
                            && currentPodcast.getId() == item.podcast.getId()
                            && mediaPlayer != null && mediaPlayer.isPlaying();
                    if (playingThis) pausePodcast(); else playPodcast(item.podcast);
                } else if (primaryLink != null && !primaryLink.isEmpty()) {
                    openUrl(primaryLink);
                }
            });

            // Long-press copies the link, with a toast confirmation.
            holder.foreground.setOnLongClickListener(v -> {
                if (primaryLink == null || primaryLink.isEmpty()) return false;
                copyLinkToClipboard(primaryLink);
                return true;
            });

            // Podcasts keep their dedicated play + research-links controls.
            if (FeedItem.TYPE_PODCAST.equals(item.type)) {
                holder.playButton.setVisibility(View.VISIBLE);
                boolean isPlaying = currentPodcast != null && item.podcast != null
                        && currentPodcast.getId() == item.podcast.getId()
                        && mediaPlayer != null && mediaPlayer.isPlaying();
                holder.playButton.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
                holder.playButton.setOnClickListener(v -> {
                    if (item.isProOnly && !proManager.isProUser()) { showProUpgradeDialog(); return; }
                    if (currentPodcast != null && item.podcast != null
                            && currentPodcast.getId() == item.podcast.getId()
                            && mediaPlayer != null && mediaPlayer.isPlaying()) {
                        pausePodcast();
                    } else {
                        playPodcast(item.podcast);
                    }
                });
                if (item.sourceLinks != null && !item.sourceLinks.isEmpty()) {
                    holder.sourceButton.setVisibility(View.VISIBLE);
                    holder.sourceButton.setOnClickListener(v -> showSourceLinksDialog(item.sourceLinks));
                }
            }
        }

        @Override
        public int getItemCount() {
            return filteredItems.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView sourceName, title, description, category, date;
            ImageView sourceIcon;
            ImageButton playButton, sourceButton, readButton, whyButton;
            View foreground; // the card layer that slides on swipe (hint sits behind it)
            View swipeHint;  // "Slide to remove" panel; shown only during swipe/teach

            ViewHolder(View v) {
                super(v);
                foreground = v.findViewById(R.id.feed_foreground);
                swipeHint = v.findViewById(R.id.feed_swipe_hint);
                sourceIcon = v.findViewById(R.id.feed_source_icon);
                sourceName = v.findViewById(R.id.feed_source_name);
                title = v.findViewById(R.id.feed_title);
                description = v.findViewById(R.id.feed_description);
                category = v.findViewById(R.id.feed_category);
                date = v.findViewById(R.id.feed_date);
                // [PLAN-PILL-REVIEW] removed (hardcoded/dead plan pill; will review later)
                whyButton = v.findViewById(R.id.feed_why_button);
                playButton = v.findViewById(R.id.feed_play_button);
                sourceButton = v.findViewById(R.id.feed_source_button);
                readButton = v.findViewById(R.id.feed_read_button);
            }
        }
    }

    // ── Helpers ──

    /** The single "open" link for a card: article → first source link (else url);
     *  news / podcast → url if present, else first source link. Null when none. */
    private String primaryLinkOf(FeedItem item) {
        if (item == null) return null;
        if (FeedItem.TYPE_ARTICLE.equals(item.type)) {
            if (item.sourceLinks != null && !item.sourceLinks.isEmpty()) return item.sourceLinks.get(0);
            return item.url;
        }
        if (item.url != null && !item.url.isEmpty()) return item.url;
        if (item.sourceLinks != null && !item.sourceLinks.isEmpty()) return item.sourceLinks.get(0);
        return null;
    }

    /** Copy a feed link to the clipboard with a toast confirmation (long-press action). */
    private void copyLinkToClipboard(String link) {
        if (link == null || link.isEmpty() || !isAdded()) return;
        try {
            android.content.ClipboardManager cm = (android.content.ClipboardManager)
                    requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null) return;
            cm.setPrimaryClip(android.content.ClipData.newPlainText("Feed link", link));
            Utilities.toast(requireContext(), "Link copied to clipboard");
        } catch (Exception e) {
            Utilities.toast(requireContext(), "Couldn't copy link");
        }
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Utilities.toast(requireContext(), "Unable to open link");
        }
    }

    private void showSourceLinksDialog(List<String> sourceLinks) {
        if (sourceLinks == null || sourceLinks.isEmpty()) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme);
        builder.setTitle("Source Links");
        String[] links = sourceLinks.toArray(new String[0]);
        builder.setItems(links, (dialog, which) -> openUrl(links[which]));
        builder.setNegativeButton("Close", null);
        builder.show();
    }

    private void showProUpgradeDialog() {
        new ProUpgradeDialog(requireActivity()).show(isPro -> {
            if (isPro && adapter != null) adapter.notifyDataSetChanged();
        });
    }
}

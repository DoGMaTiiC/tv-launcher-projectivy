package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Displays a looping local MP4 live wallpaper (Projectivy style) or rotates local wallpaper images while Home is visible. */
final class WallpaperPlayer {
    static final String LIVE_VIDEO_TAG = "tvhome_live_video";

    interface FrameListener {
        void onVideoRendering(boolean active);
    }

    private final FrameLayout root;
    private final ImageView image;
    private final LiveVideoView liveVideo;
    private final int backgroundColor;
    private final List<File> files;
    private final long interval;
    private final String order;
    private final SharedPreferences preferences;
    private final ExecutorService decoder = Executors.newSingleThreadExecutor();
    private final Random random = new Random();
    private final int width;
    /** config wallpaper_true_color: ARGB_8888 instead of RGB_565. */
    private final Bitmap.Config bitmapConfig;
    private final int height;
    /** wallpaper_dim as an ARGB black, painted into each picture once instead of a full-screen shade view. */
    private final int dimColor;
    /** The picture on screen, released once the next one has been drawn. */
    private Bitmap current;
    private boolean active;
    private boolean closed;
    private boolean loading;
    private String displayed = "";
    /** wallpaper_captions: the shown picture's name from wallpapers/captions.txt; null when off or there are none. */
    private final TextView caption;
    private final Map<String, String> names;
    /** wallpaper_moods: the collection's groups by time of day; empty rotates through all of it. */
    private final Map<String, Set<String>> moods;
    /** config.xml latitude and longitude for the sun's times, and today's {sunrise, sunset} once worked out. */
    private final String latitude, longitude;
    private LocalDate sunDay;
    private int[] sun;

    /**
     * Resolves {@code wallpaper.mp4} in priority order with zero storage permissions on Android 14 (targetSdk 34):
     * app external files, {@code /sdcard/tvhome/wallpaper.mp4}, then app internal files.
     */
    static File findVideoFile(Context context) {
        if (context == null) return null;
        File ext = context.getExternalFilesDir(null);
        File[] candidates = {
                ext != null ? new File(ext, "wallpaper.mp4") : null,
                new File("/sdcard/tvhome/wallpaper.mp4"),
                new File(context.getFilesDir(), "wallpaper.mp4")
        };
        for (File f : candidates) {
            if (f != null && f.isFile() && f.canRead() && f.length() > 0) return f;
        }
        return null;
    }

    /** Hooks an existing or new live-video TextureView in {@code root} to {@code stage}'s frame callback. */
    static LiveVideoView attachToStage(Context context, FrameLayout root, View stage, FrameListener listener) {
        if (context == null || root == null) return null;
        View existing = root.findViewWithTag(LIVE_VIDEO_TAG);
        if (existing instanceof LiveVideoView) {
            LiveVideoView lv = (LiveVideoView) existing;
            lv.setFrameListener(listener);
            return lv;
        }
        File video = findVideoFile(context);
        if (video == null) return null;
        LiveVideoView lv = new LiveVideoView(context, video);
        lv.setFrameListener(listener);
        int idx = stage != null ? Math.max(0, root.indexOfChild(stage)) : 0;
        root.addView(lv, idx, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.setBackground(null);
        return lv;
    }

    WallpaperPlayer(Context context, FrameLayout root, Config config) {
        this.root = root;
        backgroundColor = config.backgroundColor;
        files = new ArrayList<>(config.wallpapers);
        interval = config.wallpaperRotationMinutes * 60_000L;
        bitmapConfig = config.wallpaperTrueColor ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565;
        // The dim is painted into the picture (decode), so no full-screen shade layer is drawn on every frame.
        dimColor = config.wallpaperDim > 0 ? (config.wallpaperDim * 255 / 100) << 24 : 0;
        order = config.wallpaperOrder;
        preferences = context.getSharedPreferences("wallpaper_rotation", Context.MODE_PRIVATE);
        moods = config.wallpaperMoods;
        latitude = moods.isEmpty() ? "" : context.getString(R.string.latitude);
        longitude = moods.isEmpty() ? "" : context.getString(R.string.longitude);
        // Playback state belongs to one collection. Changing the collection starts from its first
        // picture instead of resuming a filename that belonged to the previous one.
        String collection = collectionKey(files);
        if (!collection.equals(preferences.getString("collection", ""))) {
            preferences.edit().putString("collection", collection)
                    .remove("image").remove("changed").apply();
        }
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        width = metrics.widthPixels;
        height = metrics.heightPixels;
        File video = findVideoFile(context);
        if (video != null) {
            View existing = root.findViewWithTag(LIVE_VIDEO_TAG);
            if (existing instanceof LiveVideoView) {
                liveVideo = (LiveVideoView) existing;
            } else {
                liveVideo = new LiveVideoView(context, video);
                root.addView(liveVideo, 0, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            }
            root.setBackground(null);
            image = null;
            names = Collections.emptyMap();
            caption = null;
            return;
        }
        liveVideo = null;
        image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        names = config.wallpaperCaptions && config.wallpaperEnabled && !files.isEmpty()
                ? WallpaperCaptions.read(files.get(0).getParentFile()) : Collections.emptyMap();
        caption = names.isEmpty() ? null : makeCaption(context, root);
        if (config.wallpaperEnabled && !files.isEmpty()) {
            root.addView(image, 0, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            showFirst();
        }
    }

    /**
     * The picture Home opens with is decoded right here, on the main thread, as the single wallpaper always was:
     * Home's first frame already has it instead of drawing the plain colour and the picture popping in later.
     * Only the later changes of a rotation decode in the background.
     */
    private void showFirst() {
        String previous = preferences.getString("image", "");
        List<File> playable = playable();
        File first = WallpaperRotation.find(playable, previous);
        if (first == null) first = WallpaperRotation.pick(playable, previous, order, random);
        while (first != null) {
            Bitmap bitmap = decode(first);
            if (bitmap != null) {
                show(bitmap);
                // The picture covers the whole screen: the root's plain colour under it is overdraw.
                root.setBackground(null);
                displayed = first.getName();
                showCaption(false);
                if (!displayed.equals(previous)) {
                    preferences.edit().putString("image", displayed)
                            .putLong("changed", System.currentTimeMillis()).apply();
                }
                return;
            }
            files.remove(first);   // unreadable or over 4K: try another, or keep the plain colour
            playable = playable();
            first = WallpaperRotation.pick(playable, previous, order, random);
        }
    }

    void start() {
        active = true;
        if (liveVideo != null) {
            liveVideo.setPlaybackActive(true);
            return;
        }
        update();
    }

    void stop() {
        active = false;
        if (liveVideo != null) liveVideo.setPlaybackActive(false);
    }

    /** Drops the wallpaper texture when Home is hidden and Android asks the process to free memory. */
    void releaseForMemoryPressure() {
        if (liveVideo != null) {
            liveVideo.setPlaybackActive(false);
            return;
        }
        if (current == null || image == null) return;
        image.setImageDrawable(null);
        // The renderer may still have a queued frame that references this hardware bitmap. Drop our references and
        // let Android reclaim its native buffer after rendering releases it instead of recycling it mid-frame.
        current = null;
        displayed = "";
        root.setBackgroundColor(backgroundColor);
    }

    /** The pictures the rotation picks from now: wallpaper_moods' group for the time of day, or the whole collection. */
    private List<File> playable() {
        if (moods.isEmpty()) return files;
        LocalDateTime now = LocalDateTime.now();
        if (!now.toLocalDate().equals(sunDay)) {
            sunDay = now.toLocalDate();
            sun = WallpaperMood.sun(sunDay, latitude, longitude, ZoneId.systemDefault());
        }
        return WallpaperMood.select(files, moods, WallpaperMood.timeOfDay(now.getHour() * 60 + now.getMinute(), sun[0], sun[1]));
    }

    void close() {
        active = false;
        closed = true;
        if (liveVideo != null) liveVideo.release();
        decoder.shutdownNow();
    }

    void update() {
        if (!active || closed || loading || liveVideo != null || files.isEmpty()) return;
        String previous = preferences.getString("image", "");
        // A picture outside the time of day's group (the group changed) is replaced at once, not after the interval.
        List<File> playable = playable();
        File selected = WallpaperRotation.find(playable, previous);
        long changed = preferences.getLong("changed", 0);
        if (selected == null || WallpaperRotation.due(changed, System.currentTimeMillis(), interval)) {
            selected = WallpaperRotation.pick(playable, previous, order, random);
        }
        if (selected == null || selected.getName().equals(displayed)) return;
        File next = selected;
        loading = true;
        decoder.execute(() -> {
            Bitmap bitmap = decode(next);
            root.post(() -> {
                loading = false;
                if (closed || !active) {
                    if (bitmap != null) bitmap.recycle();
                    return;
                }
                if (bitmap == null) {
                    files.remove(next);
                    Log.w("TvHome.Wallpaper", "Skipping unreadable image: " + next.getName());
                    update();
                    return;
                }
                show(bitmap);
                displayed = next.getName();
                showCaption(true);
                if (!displayed.equals(previous) || changed == 0) {
                    preferences.edit().putString("image", displayed)
                            .putLong("changed", System.currentTimeMillis()).apply();
                }
            });
        });
    }

    /**
     * Small light text under the top-right buttons, above everything else and never focusable. It sits where the
     * top bar ends in every layout, so it does not cover the clock, the stats line or the tiles.
     */
    private static TextView makeCaption(Context context, FrameLayout root) {
        float dp = context.getResources().getDisplayMetrics().density;
        TextView text = new TextView(context);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        text.setTextColor(0xD9FFFFFF);
        text.setShadowLayer(6 * dp, 0, dp, 0xCC000000);
        text.setSingleLine(true);
        text.setEllipsize(TextUtils.TruncateAt.END);
        text.setMaxWidth(Math.round(520 * dp));
        text.setFocusable(false);
        text.setAlpha(0f);
        try {
            text.setTypeface(UiFont.get(context, R.string.font_regular));
        } catch (Exception ignored) {
            // the system font is fine
        }
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        params.topMargin = Math.round(110 * dp);
        params.setMarginEnd(Math.round(62 * dp));
        root.addView(text, params);
        return text;
    }

    /** The displayed picture's name, faded across a change; a picture without a name hides the caption. */
    private void showCaption(boolean fade) {
        if (caption == null) return;
        String name = names.get(displayed);
        caption.animate().cancel();
        if (name == null) {
            caption.setAlpha(0f);
            return;
        }
        caption.setContentDescription(name);
        if (!fade || caption.getAlpha() == 0f) {
            caption.setText(name);
            caption.setAlpha(1f);
            return;
        }
        caption.animate().alpha(0f).setDuration(250).withEndAction(() -> {
            caption.setText(name);
            caption.animate().alpha(1f).setDuration(350).start();
        }).start();
    }

    /** Names plus sizes: enough to notice a different collection without hashing every image. */
    private static String collectionKey(List<File> images) {
        StringBuilder key = new StringBuilder();
        for (File file : images) key.append(file.getName()).append(':').append(file.length()).append(';');
        return key.toString();
    }

    /** Puts a picture on screen and releases the previous one after a frame has drawn the new one. */
    private void show(Bitmap bitmap) {
        Bitmap old = current;
        current = bitmap;
        // The wallpaper covers the whole root; avoid drawing the fallback colour under it.
        root.setBackground(null);
        image.setImageBitmap(bitmap);
        if (old != null && old != bitmap) image.postDelayed(old::recycle, 500);
    }

    private Bitmap decode(File file) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), options);
            int width = options.outWidth, height = options.outHeight;
            if (width <= 0 || height <= 0) return null;
            int sample = WallpaperSize.sampleSize(width, height, this.width, this.height);
            if (!WallpaperSize.accepted(width / sample, height / sample)) return null;
            options.inJustDecodeBounds = false;
            options.inSampleSize = sample;
            int[] scale = WallpaperSize.coverScale(width / sample, height / sample, this.width, this.height);
            if (scale != null) {
                options.inScaled = true;
                options.inDensity = scale[0];
                options.inTargetDensity = scale[1];
            }
            options.inPreferredConfig = bitmapConfig;
            options.inMutable = dimColor != 0;
            Bitmap decoded = BitmapFactory.decodeFile(file.getPath(), options);
            if (decoded == null) return null;
            if (dimColor != 0) new Canvas(decoded).drawColor(dimColor);
            // One copy in GPU memory instead of a software bitmap plus its texture; the decode stays off the UI
            // thread for rotations, and Home's return no longer uploads 4-8 MB to the GPU.
            Bitmap gpu = decoded.copy(Bitmap.Config.HARDWARE, false);
            if (gpu == null) return decoded;
            decoded.recycle();
            return gpu;
        } catch (Exception | OutOfMemoryError failure) {
            return null;
        }
    }

    /** Seamless muted center-cropped looping MP4 backdrop rendered on a TextureView inside the View hierarchy. */
    static final class LiveVideoView extends TextureView implements TextureView.SurfaceTextureListener {
        private final File videoFile;
        private final Matrix cropMatrix = new Matrix();
        private MediaPlayer player;
        private Surface surface;
        private FrameListener frameListener;
        private boolean shouldPlay = true;
        private boolean prepared;
        private boolean rendering;
        private boolean released;
        private int videoW, videoH;

        LiveVideoView(Context context, File videoFile) {
            super(context);
            this.videoFile = videoFile;
            setTag(LIVE_VIDEO_TAG);
            setOpaque(true);
            setFocusable(false);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            setSurfaceTextureListener(this);
        }

        void setFrameListener(FrameListener listener) {
            this.frameListener = listener;
            if (listener != null) listener.onVideoRendering(rendering);
        }

        void setPlaybackActive(boolean active) {
            shouldPlay = active;
            if (player == null || !prepared || released) return;
            try {
                if (active && !player.isPlaying()) player.start();
                else if (!active && player.isPlaying()) player.pause();
            } catch (IllegalStateException ignored) {
            }
        }

        void release() {
            released = true;
            shouldPlay = false;
            notifyRendering(false);
            releasePlayer();
        }

        private void notifyRendering(boolean active) {
            if (rendering == active) return;
            rendering = active;
            if (frameListener != null) frameListener.onVideoRendering(active);
        }

        private void startPlayer(SurfaceTexture st) {
            releasePlayer();
            if (released || videoFile == null || !videoFile.isFile()) return;
            try {
                surface = new Surface(st);
                player = new MediaPlayer();
                player.setDataSource(videoFile.getAbsolutePath());
                player.setSurface(surface);
                player.setLooping(true);
                player.setVolume(0f, 0f);
                player.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
                player.setOnVideoSizeChangedListener((mp, w, h) -> {
                    videoW = w;
                    videoH = h;
                    applyCrop();
                });
                player.setOnPreparedListener(mp -> {
                    if (released) return;
                    prepared = true;
                    videoW = mp.getVideoWidth();
                    videoH = mp.getVideoHeight();
                    applyCrop();
                    if (shouldPlay) mp.start();
                });
                player.setOnInfoListener((mp, what, extra) -> {
                    if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) notifyRendering(true);
                    return false;
                });
                player.setOnErrorListener((mp, what, extra) -> {
                    Log.w("TvHome.Wallpaper", "Video playback error " + what + "/" + extra);
                    notifyRendering(false);
                    releasePlayer();
                    return true;
                });
                player.prepareAsync();
            } catch (Exception e) {
                Log.w("TvHome.Wallpaper", "Failed to start video wallpaper", e);
                notifyRendering(false);
                releasePlayer();
            }
        }

        private void releasePlayer() {
            prepared = false;
            if (player != null) {
                try {
                    player.reset();
                } catch (Exception ignored) {
                }
                try {
                    player.release();
                } catch (Exception ignored) {
                }
                player = null;
            }
            if (surface != null) {
                surface.release();
                surface = null;
            }
        }

        private void applyCrop() {
            int tw = getWidth(), th = getHeight();
            if (tw <= 0 || th <= 0 || videoW <= 0 || videoH <= 0) return;
            float scale = Math.max((float) tw / videoW, (float) th / videoH);
            cropMatrix.setScale((videoW * scale) / tw, (videoH * scale) / th, tw / 2f, th / 2f);
            setTransform(cropMatrix);
        }

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) {
            released = false;
            startPlayer(st);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) {
            applyCrop();
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
            notifyRendering(false);
            releasePlayer();
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture st) {
            if (!released && prepared) notifyRendering(true);
        }
    }
}

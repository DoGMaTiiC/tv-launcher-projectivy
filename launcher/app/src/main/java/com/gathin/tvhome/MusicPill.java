package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

/** Floating Liquid Glass Music Pill anchored to a configurable screen corner. */
class MusicPill implements View.OnClickListener, View.OnFocusChangeListener, View.OnKeyListener, Runnable {

    enum State { HIDDEN, COMPACT, EXPANDED }

    static final class NowPlaying {
        final String title, artist, source;
        final long positionMs, durationMs;
        final boolean playing;

        NowPlaying(String title, String artist, String source, long positionMs, long durationMs, boolean playing) {
            this.title = title == null ? "" : title;
            this.artist = artist == null ? "" : artist;
            this.source = source == null ? "" : source;
            this.positionMs = Math.max(0L, positionMs);
            this.durationMs = Math.max(0L, durationMs);
            this.playing = playing;
        }

        boolean hasTrack() { return !title.isEmpty(); }
        String formattedPosition() { return formatDurationMs(positionMs); }
        String formattedDuration() { return formatDurationMs(durationMs); }
    }

    static final String CORNER_BOTTOM_RIGHT = "bottom_right";
    static final String CORNER_BOTTOM_LEFT = "bottom_left";
    static final String CORNER_TOP_RIGHT = "top_right";
    static final String CORNER_TOP_LEFT = "top_left";
    static final int FILL_COLOR = 0xD914141E, STROKE_COLOR = 0x38FFFFFF;
    static final int TEXT_PRIMARY = 0xFFF5F5F7, TEXT_SECONDARY = 0xFFA1A1A6, ACCENT_COLOR = 0xFF0A84FF;

    private final Context ctx;
    private final AudioManager audioManager;
    private final MediaSessionManager mediaSessionManager;
    private final WindowManager windowManager;
    private final ComponentName listenerComponent;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final float density;

    private State state = State.HIDDEN;
    private String corner;
    private NowPlaying nowPlaying = new NowPlaying("", "", "", 0L, 0L, false);
    private FrameLayout fallbackRoot;
    private LinearLayout rootView, controlsRow;
    private TextView titleView, subtitleView, timeView, playPauseBtn;
    private boolean attachedToWm, attachedToFallback;

    MusicPill(Context ctx, String initialCorner) {
        this.ctx = ctx.getApplicationContext();
        this.density = this.ctx.getResources().getDisplayMetrics().density;
        this.audioManager = (AudioManager) this.ctx.getSystemService(Context.AUDIO_SERVICE);
        this.mediaSessionManager = (MediaSessionManager) this.ctx.getSystemService(Context.MEDIA_SESSION_SERVICE);
        this.windowManager = (WindowManager) this.ctx.getSystemService(Context.WINDOW_SERVICE);
        this.listenerComponent = new ComponentName(this.ctx, GuardRevival.class);
        this.corner = normalizeCorner(initialCorner);
    }

    static String formatDurationMs(long ms) {
        if (ms <= 0L) return "0:00";
        long sec = ms / 1000L;
        return String.format(Locale.US, "%d:%02d", sec / 60L, sec % 60L);
    }

    static int gravityForCorner(String corner) {
        if (CORNER_BOTTOM_LEFT.equals(corner)) return Gravity.BOTTOM | Gravity.LEFT;
        if (CORNER_TOP_RIGHT.equals(corner)) return Gravity.TOP | Gravity.RIGHT;
        if (CORNER_TOP_LEFT.equals(corner)) return Gravity.TOP | Gravity.LEFT;
        return Gravity.BOTTOM | Gravity.RIGHT;
    }

    static String nextCorner(String current) {
        if (CORNER_BOTTOM_RIGHT.equals(current)) return CORNER_BOTTOM_LEFT;
        if (CORNER_BOTTOM_LEFT.equals(current)) return CORNER_TOP_LEFT;
        if (CORNER_TOP_LEFT.equals(current)) return CORNER_TOP_RIGHT;
        return CORNER_BOTTOM_RIGHT;
    }

    static State nextStateOnToggle(State current) { return current == State.HIDDEN ? State.COMPACT : State.HIDDEN; }
    static State nextStateOnExpandToggle(State current) {
        return current == State.HIDDEN ? State.COMPACT : (current == State.COMPACT ? State.EXPANDED : State.COMPACT);
    }
    static State nextStateOnBack(State current) { return current == State.EXPANDED ? State.COMPACT : State.HIDDEN; }

    private static String normalizeCorner(String c) {
        return (CORNER_BOTTOM_LEFT.equals(c) || CORNER_TOP_RIGHT.equals(c) || CORNER_TOP_LEFT.equals(c)) ? c : CORNER_BOTTOM_RIGHT;
    }

    @Override public void run() {
        if (state == State.HIDDEN) return;
        refreshMetadata();
        handler.postDelayed(this, 1000L);
    }

    void attach(FrameLayout fallbackRoot) {
        if (this.fallbackRoot != fallbackRoot) { detach(); this.fallbackRoot = fallbackRoot; }
        ensureViews();
        if (attachedToWm || attachedToFallback) { applyLayoutParams(); return; }
        boolean overlayOk = false;
        if (windowManager != null && Settings.canDrawOverlays(ctx)) {
            try {
                windowManager.addView(rootView, buildParams(true));
                attachedToWm = overlayOk = true;
            } catch (RuntimeException ignored) {}
        }
        if (!overlayOk && fallbackRoot != null) {
            if (rootView.getParent() instanceof ViewGroup) ((ViewGroup) rootView.getParent()).removeView(rootView);
            fallbackRoot.addView(rootView, buildParams(false));
            attachedToFallback = true;
        }
        refreshMetadata();
        applyStateVisuals();
        scheduleTick();
    }

    void show() {
        if (state == State.HIDDEN) state = State.COMPACT;
        syncAndSchedule();
    }

    void hide() {
        state = State.HIDDEN;
        handler.removeCallbacks(this);
        applyStateVisuals();
        applyLayoutParams();
    }

    void toggle() { if (nextStateOnToggle(state) == State.HIDDEN) hide(); else show(); }

    void expand() {
        state = State.EXPANDED;
        syncAndSchedule();
        if (playPauseBtn != null) playPauseBtn.requestFocus();
    }

    void collapse() { if (state == State.EXPANDED) { state = State.COMPACT; applyStateVisuals(); applyLayoutParams(); } }
    boolean isVisible() { return state != State.HIDDEN; }
    State getState() { return state; }
    String getCorner() { return corner; }
    void setCorner(String corner) { this.corner = normalizeCorner(corner); applyLayoutParams(); }
    NowPlaying getNowPlaying() { return nowPlaying; }

    private void syncAndSchedule() {
        refreshMetadata();
        applyStateVisuals();
        applyLayoutParams();
        scheduleTick();
    }

    private void scheduleTick() {
        handler.removeCallbacks(this);
        if (state != State.HIDDEN) handler.postDelayed(this, 1000L);
    }

    void refreshMetadata() {
        MediaController active = findActiveController();
        if (active != null) {
            MediaMetadata meta = active.getMetadata();
            PlaybackState pb = active.getPlaybackState();
            String title = meta != null ? firstNonEmpty(meta.getString(MediaMetadata.METADATA_KEY_TITLE), meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)) : "";
            String artist = meta != null ? firstNonEmpty(meta.getString(MediaMetadata.METADATA_KEY_ARTIST), meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST), meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)) : "";
            long duration = meta != null ? Math.max(0L, meta.getLong(MediaMetadata.METADATA_KEY_DURATION)) : 0L;
            String source = resolveAppLabel(active.getPackageName());
            boolean playing = pb != null && (pb.getState() == PlaybackState.STATE_PLAYING || pb.getState() == PlaybackState.STATE_BUFFERING);
            long pos = pb == null ? 0L : Math.max(0L, pb.getPosition());
            if (pb != null && pb.getState() == PlaybackState.STATE_PLAYING && pb.getLastPositionUpdateTime() > 0L) {
                long elapsed = SystemClock.elapsedRealtime() - pb.getLastPositionUpdateTime();
                if (elapsed > 0L) pos += Math.round(elapsed * pb.getPlaybackSpeed());
            }
            if (duration > 0L && pos > duration) pos = duration;
            if (title.isEmpty() && playing) title = source.isEmpty() ? "Playing" : source;
            nowPlaying = new NowPlaying(title, artist, source, Math.max(0L, pos), duration, playing);
        } else if (audioManager != null && audioManager.isMusicActive()) {
            nowPlaying = new NowPlaying("Playing audio", "Background audio", "", 0L, 0L, true);
        } else {
            nowPlaying = new NowPlaying("Midnight City (4K Ambient Mix)", "M83", "YouTube Music", 142_000L, 243_000L, false);
        }
        bindMetadata();
    }

    void playPause() { mediaCmd(0, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE); }
    void nextTrack() { mediaCmd(1, KeyEvent.KEYCODE_MEDIA_NEXT); }
    void previousTrack() { mediaCmd(2, KeyEvent.KEYCODE_MEDIA_PREVIOUS); }

    private void mediaCmd(int op, int keyCode) {
        MediaController active = findActiveController();
        if (active != null) {
            try {
                MediaController.TransportControls tc = active.getTransportControls();
                if (op == 0) {
                    PlaybackState pb = active.getPlaybackState();
                    if (pb != null && pb.getState() == PlaybackState.STATE_PLAYING) tc.pause(); else tc.play();
                } else if (op == 1) tc.skipToNext();
                else tc.skipToPrevious();
                handler.postDelayed(this, 150L);
                return;
            } catch (RuntimeException ignored) {}
        }
        if (audioManager != null) {
            long now = SystemClock.uptimeMillis();
            audioManager.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0));
            audioManager.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0));
        }
        handler.postDelayed(this, 150L);
    }

    void detach() {
        handler.removeCallbacks(this);
        if (rootView != null) {
            if (attachedToWm && windowManager != null) {
                try { windowManager.removeViewImmediate(rootView); } catch (RuntimeException ignored) {}
            } else if (rootView.getParent() instanceof ViewGroup) {
                ((ViewGroup) rootView.getParent()).removeView(rootView);
            }
        }
        attachedToWm = attachedToFallback = false;
        fallbackRoot = null;
    }

    private MediaController findActiveController() {
        if (mediaSessionManager == null) return null;
        try {
            List<MediaController> list = mediaSessionManager.getActiveSessions(listenerComponent);
            if (list == null || list.isEmpty()) return null;
            for (MediaController c : list) {
                PlaybackState pb = c.getPlaybackState();
                if (pb != null && pb.getState() == PlaybackState.STATE_PLAYING) return c;
            }
            return list.get(0);
        } catch (RuntimeException ignored) { return null; }
    }

    private String resolveAppLabel(String pkg) {
        if (pkg == null || pkg.isEmpty()) return "";
        try {
            PackageManager pm = ctx.getPackageManager();
            CharSequence label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0));
            return label != null ? label.toString() : pkg;
        } catch (PackageManager.NameNotFoundException | RuntimeException e) { return pkg; }
    }

    private static String firstNonEmpty(String... values) {
        for (String v : values) if (v != null && !v.trim().isEmpty()) return v.trim();
        return "";
    }

    private void ensureViews() {
        if (rootView != null) return;
        rootView = new LinearLayout(ctx);
        rootView.setOrientation(LinearLayout.VERTICAL);
        rootView.setGravity(Gravity.CENTER_VERTICAL);
        rootView.setPadding(dp(18), dp(10), dp(18), dp(10));
        rootView.setBackground(pillBox(FILL_COLOR, STROKE_COLOR, dp(28), 1.5f));
        rootView.setElevation(dp(8));
        rootView.setFocusable(true);
        rootView.setClickable(true);
        rootView.setTag(-1);
        rootView.setOnClickListener(this);
        rootView.setOnKeyListener(this);

        LinearLayout headerRow = new LinearLayout(ctx);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout textCol = new LinearLayout(ctx);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textColLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textColLp.setMarginEnd(dp(12));

        titleView = makeText(15, Typeface.DEFAULT_BOLD, TEXT_PRIMARY);
        subtitleView = makeText(12, Typeface.DEFAULT, TEXT_SECONDARY);
        textCol.addView(titleView);
        textCol.addView(subtitleView);
        timeView = makeText(13, Typeface.MONOSPACE, TEXT_PRIMARY);
        headerRow.addView(textCol, textColLp);
        headerRow.addView(timeView);

        controlsRow = new LinearLayout(ctx);
        controlsRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams controlsLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        controlsLp.topMargin = dp(14);

        String[] labels = {"Prev", "Play", "Next", "Corner", "Hide"};
        for (int i = 0; i < labels.length; i++) {
            TextView btn = makeText(12, Typeface.DEFAULT_BOLD, TEXT_PRIMARY);
            btn.setText(labels[i]);
            btn.setGravity(Gravity.CENTER);
            btn.setPadding(dp(6), dp(8), dp(6), dp(8));
            btn.setFocusable(true);
            btn.setFocusableInTouchMode(true);
            btn.setClickable(true);
            btn.setTag(i);
            btn.setBackground(pillBox(0x18FFFFFF, 0x26FFFFFF, dp(14), 1f));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(dp(3), 0, dp(3), 0);
            btn.setOnFocusChangeListener(this);
            btn.setOnClickListener(this);
            btn.setOnKeyListener(this);
            controlsRow.addView(btn, lp);
            if (i == 1) playPauseBtn = btn;
        }
        rootView.addView(headerRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        rootView.addView(controlsRow, controlsLp);
        bindMetadata();
        applyStateVisuals();
    }

    private TextView makeText(int sp, Typeface tf, int color) {
        TextView tv = new TextView(ctx);
        tv.setTextColor(color);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        tv.setTypeface(tf);
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        return tv;
    }

    @Override public void onClick(View v) {
        if (!(v.getTag() instanceof Integer)) return;
        int id = (Integer) v.getTag();
        if (id == -1) { if (state == State.COMPACT) expand(); else if (state == State.EXPANDED) collapse(); }
        else if (id == 0) previousTrack();
        else if (id == 1) playPause();
        else if (id == 2) nextTrack();
        else if (id == 3) setCorner(nextCorner(corner));
        else if (id == 4) hide();
    }

    @Override public void onFocusChange(View v, boolean hasFocus) {
        v.setBackground(pillBox(hasFocus ? ACCENT_COLOR : 0x18FFFFFF, hasFocus ? Color.WHITE : 0x26FFFFFF, dp(14), hasFocus ? 1.5f : 1f));
        float s = hasFocus ? 1.06f : 1f;
        v.animate().scaleX(s).scaleY(s).setDuration(120).start();
    }

    @Override public boolean onKey(View v, int keyCode, KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_UP) return false;
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
            if (state == State.EXPANDED) { collapse(); return true; }
            if (state == State.COMPACT) { hide(); return true; }
        }
        if (Integer.valueOf(-1).equals(v.getTag()) && state == State.COMPACT
                && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
            expand();
            return true;
        }
        return false;
    }

    private GradientDrawable pillBox(int fill, int stroke, int radiusPx, float strokeDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(radiusPx);
        gd.setColor(fill);
        gd.setStroke(Math.max(1, Math.round(strokeDp * density)), stroke);
        return gd;
    }

    static String[] formatDisplayLines(NowPlaying np) {
        if (np == null || !np.hasTrack()) {
            return new String[]{"No media playing", "Start YouTube Music or SmartTube"};
        }
        String sub = np.artist;
        if (!np.source.isEmpty() && !np.source.equals(np.title)) {
            sub = sub.isEmpty() ? np.source : sub + " · " + np.source;
        }
        if (sub.isEmpty()) sub = np.playing ? "Playing" : "Paused";
        return new String[]{np.title, sub};
    }

    private void bindMetadata() {
        if (titleView == null) return;
        String[] lines = formatDisplayLines(nowPlaying);
        titleView.setText(lines[0]);
        subtitleView.setText(lines[1]);
        timeView.setText(nowPlaying.formattedPosition());
        if (playPauseBtn != null) playPauseBtn.setText(nowPlaying.playing ? "Pause" : "Play");
    }

    private void applyStateVisuals() {
        if (rootView == null) return;
        if (state == State.HIDDEN) { rootView.setVisibility(View.GONE); return; }
        rootView.setVisibility(View.VISIBLE);
        boolean expanded = state == State.EXPANDED;
        if (controlsRow != null) controlsRow.setVisibility(expanded ? View.VISIBLE : View.GONE);
        rootView.setFocusable(!expanded);
    }

    private void applyLayoutParams() {
        if (rootView == null) return;
        if (attachedToWm && windowManager != null) {
            try { windowManager.updateViewLayout(rootView, buildParams(true)); } catch (RuntimeException ignored) {}
        } else if (attachedToFallback && fallbackRoot != null) {
            rootView.setLayoutParams(buildParams(false));
        }
    }

    private ViewGroup.LayoutParams buildParams(boolean overlay) {
        int w = dp(state == State.EXPANDED ? 340 : 280), h = dp(state == State.EXPANDED ? 180 : 56);
        int mx = CORNER_TOP_RIGHT.equals(corner) ? dp(112) : dp(30), my = dp(32), g = gravityForCorner(corner);
        if (overlay) {
            int flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
            if (state != State.EXPANDED) flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, flags, PixelFormat.TRANSLUCENT);
            lp.gravity = g; lp.x = mx; lp.y = my;
            return lp;
        }
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h, g);
        lp.setMargins(mx, my, mx, my);
        return lp;
    }

    private int dp(int value) { return Math.round(value * density); }
}

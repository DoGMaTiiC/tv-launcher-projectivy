package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.format.DateFormat;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewParent;
import android.widget.FrameLayout;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Projectivy Launcher stage: live looping MP4 video backdrop (with crisp static fallback),
 * subtle bottom legibility scrim, top-right [Wi-Fi | Gear] pill + live clock, and bottom-left TV Apps header.
 */
final class TopStageView extends View {

    static final class CardData {
        final String pkg;
        final String title;
        final Bitmap art;
        final BitmapShader shader;

        CardData(String pkg, String title, String meta, Bitmap art, int accent) {
            this.pkg = pkg;
            this.title = title;
            this.art = art;
            this.shader = (art != null && !art.isRecycled())
                    ? new BitmapShader(art, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) : null;
        }
    }

    interface Listener {
        void onReturnToDock();
        void onLaunchCard(String pkg);
        void onLiveTv();
        void onInputs();
        void onControlCenter();
    }

    private static final int ZONE_NONE = 0, ZONE_TOP = 2;

    private final Listener listener;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint imgPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF tmp = new RectF();
    private final RectF pill = new RectF();
    private final Matrix mtx = new Matrix();
    private final Typeface outfitMedium;
    private final Bitmap backdrop;

    private WallpaperPlayer.LiveVideoView liveVideo;
    private boolean videoRendering;
    private float scrollProgress = 0f;
    private int zone = ZONE_NONE;

    private long cachedMin = -1L;
    private String cachedTime = "10:41";
    private int cachedW = -1, cachedH = -1;
    private Shader bottomScrim;

    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            invalidate();
            scheduleClockTick();
        }
    };

    TopStageView(Context ctx, DockBlur.LiquidRenderer liquid, Listener listener) {
        super(ctx);
        this.listener = listener;
        setFocusable(false);
        setWillNotDraw(false);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeCap(Paint.Cap.ROUND);

        Typeface font;
        try {
            font = UiFont.get(ctx, R.string.font_medium);
        } catch (Exception ignored) {
            font = Typeface.DEFAULT_BOLD;
        }
        outfitMedium = font;

        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        int bw = dm != null && dm.widthPixels > 0 ? dm.widthPixels : 1920;
        int bh = dm != null && dm.heightPixels > 0 ? dm.heightPixels : 1080;
        backdrop = HeroArt.loadProjectivyBackdrop(ctx, bw, bh);
    }

    void setNightMode(boolean nightMode) {
    }

    void setScrollProgress(float progress) {
        float clamped = Math.max(0f, Math.min(1f, progress));
        if (Math.abs(this.scrollProgress - clamped) > 0.001f) {
            this.scrollProgress = clamped;
            invalidate();
        }
    }

    void setFocusedDock(int slot, String pkg, String label) {
    }

    void setCards(CardData c0, CardData c1, CardData c2) {
    }

    void setNowPlaying(String line, boolean active) {
    }

    void enterFromDock(boolean toButtons) {
        setFocusable(true);
        zone = ZONE_TOP;
        requestFocus();
        invalidate();
    }

    void clearStageFocus() {
        zone = ZONE_NONE;
        setFocusable(false);
        invalidate();
    }

    boolean isStageFocused() {
        return zone != ZONE_NONE && hasFocus();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        ViewParent p = getParent();
        if (p instanceof FrameLayout) {
            liveVideo = WallpaperPlayer.attachToStage(getContext(), (FrameLayout) p, this, active -> {
                if (videoRendering != active) {
                    videoRendering = active;
                    invalidate();
                }
            });
            if (liveVideo != null) {
                liveVideo.setPlaybackActive(getWindowVisibility() == VISIBLE);
            }
        }
        scheduleClockTick();
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(clockTick);
        if (liveVideo != null) {
            liveVideo.setFrameListener(null);
            liveVideo = null;
        }
        videoRendering = false;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        boolean vis = visibility == VISIBLE;
        if (liveVideo != null) {
            liveVideo.setPlaybackActive(vis);
        }
        removeCallbacks(clockTick);
        if (vis) {
            invalidate();
            scheduleClockTick();
        }
    }

    private void scheduleClockTick() {
        removeCallbacks(clockTick);
        if (!isAttachedToWindow() || getWindowVisibility() != VISIBLE) return;
        long delay = 60_000L - (System.currentTimeMillis() % 60_000L) + 50L;
        postDelayed(clockTick, delay);
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, android.graphics.Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        if (!gainFocus && zone != ZONE_NONE) clearStageFocus();
        else invalidate();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (zone == ZONE_NONE) return super.onKeyDown(keyCode, event);
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_BACK) {
            clearStageFocus();
            if (listener != null) listener.onReturnToDock();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (listener != null) listener.onControlCenter();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float k = w / 1920f;

        if (!videoRendering && backdrop != null && !backdrop.isRecycled()) {
            float bw = backdrop.getWidth(), bh = backdrop.getHeight();
            float sc = Math.max(w / bw, h / bh);
            mtx.setScale(sc, sc);
            mtx.postTranslate((w - bw * sc) / 2f, (h - bh * sc) / 2f);
            imgPaint.setAlpha(255);
            c.drawBitmap(backdrop, mtx, imgPaint);
        }

        if (cachedW != w || cachedH != h || bottomScrim == null) {
            cachedW = w;
            cachedH = h;
            bottomScrim = new LinearGradient(0f, 760f * k, 0f, h,
                    0x00000000, 0x78060A0E, Shader.TileMode.CLAMP);
        }
        fill.setShader(bottomScrim);
        c.drawRect(0f, 760f * k, w, h, fill);
        fill.setShader(null);

        drawTopRightCluster(c, k);
        drawBottomLeftCategory(c, k);
    }

    private void drawTopRightCluster(Canvas c, float k) {
        pill.set(1432f * k, 40f * k, 1594f * k, 106f * k);
        fill.setColor(0x469AA8B8);
        c.drawRoundRect(pill, 33f * k, 33f * k, fill);
        if (zone == ZONE_TOP) {
            rim.setStrokeWidth(2.4f * k);
            rim.setColor(0xEBFFFFFF);
            c.drawRoundRect(pill, 33f * k, 33f * k, rim);
        }

        // Solid 90-degree Wi-Fi fan centered around (1474*k, 73*k)
        float wx = 1474f * k, wy = 85f * k, wr = 22f * k;
        tmp.set(wx - wr, wy - wr, wx + wr, wy + wr);
        fill.setColor(0xD2E4ECF4);
        c.drawArc(tmp, -135f, 90f, true, fill);

        // 8-tooth solid Settings gear centered at (1550*k, 73*k)
        float gx = 1550f * k, gy = 73f * k;
        fill.setColor(0xD2E4ECF4);
        c.drawCircle(gx, gy, 14f * k, fill);
        float tw = 5.4f * k, th = 19f * k;
        for (int i = 0; i < 4; i++) {
            c.save();
            c.rotate(i * 45f, gx, gy);
            tmp.set(gx - tw / 2f, gy - th, gx + tw / 2f, gy + th);
            c.drawRoundRect(tmp, 2.2f * k, 2.2f * k, fill);
            c.restore();
        }
        fill.setColor(0x66404B56);
        c.drawCircle(gx, gy, 6.5f * k, fill);

        // Live clock at x = 1612*k, baseline y = 89*k
        long min = System.currentTimeMillis() / 60_000L;
        if (min != cachedMin) {
            cachedMin = min;
            String pattern = DateFormat.is24HourFormat(getContext()) ? "HH:mm" : "h:mm";
            cachedTime = new SimpleDateFormat(pattern, Locale.getDefault()).format(new Date());
        }
        tp.setTypeface(outfitMedium);
        tp.setTextSize(46f * k);
        tp.setColor(0xC8E2EBF2);
        tp.setShadowLayer(4f * k, 0f, 2f * k, 0x55000000);
        c.drawText(cachedTime, 1612f * k, 89f * k, tp);
        tp.clearShadowLayer();
    }

    private void drawBottomLeftCategory(Canvas c, float k) {
        float fade = 1f - scrollProgress;
        if (fade <= 0.02f) return;
        int alpha = Math.round(0xEB * fade);
        int col = (alpha << 24) | 0x00FFFFFF;

        // TV monitor outline at x = 110*k, y = 775*k .. 804*k
        rim.setColor(col);
        rim.setShadowLayer(4f * k, 0f, 2f * k, (Math.round(0x88 * fade) << 24));
        rim.setStrokeWidth(2.8f * k);
        tmp.set(110f * k, 775f * k, 146f * k, 799f * k);
        c.drawRoundRect(tmp, 3f * k, 3f * k, rim);
        rim.setStrokeWidth(2.6f * k);
        c.drawLine(119f * k, 804f * k, 137f * k, 804f * k, rim);
        rim.clearShadowLayer();

        tp.setTypeface(outfitMedium);
        tp.setTextSize(27f * k);
        tp.setColor(col);
        tp.setShadowLayer(5f * k, 0f, 2f * k, (Math.round(0xAA * fade) << 24));
        c.drawText("TV Apps", 158f * k, 797f * k, tp);
        tp.clearShadowLayer();
    }
}

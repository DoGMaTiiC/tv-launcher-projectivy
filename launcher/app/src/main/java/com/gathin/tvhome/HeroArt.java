package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The cinematic shelf's backdrop: an app's artwork reduced to 48×27 pixels, saturated and smoothed,
 * then stretched over the screen by its ImageView. Bilinear filtering of an image that small is a
 * blur that costs one tiny texture; a real blur would mean RenderScript or a shader pass per frame,
 * which a 1 GB TV cannot spare.
 */
final class HeroArt {
    static final int W = 48, H = 27;
    private static final float SATURATION = 1.7f;
    private static final String[] HERO_EXTS = {"jpg", "jpeg", "png", "webp"};

    private HeroArt() {
    }

    /** Looks up {@code heroes/<pkg>.(jpg|jpeg|png|webp)} inside {@code themeDir}, or null if absent. */
    static File findHeroFile(File themeDir, String pkg) {
        if (themeDir == null || pkg == null || pkg.isEmpty()) return null;
        if (pkg.contains("/") || pkg.contains("\\") || pkg.contains("..")) return null;
        File heroesDir = new File(themeDir, ThemeFiles.HEROES);
        if (!heroesDir.isDirectory()) return null;
        for (String ext : HERO_EXTS) {
            File candidate = new File(heroesDir, pkg + "." + ext);
            if (candidate.isFile()) return candidate;
        }
        return null;
    }

    /**
     * Applies a bottom and left-side readability scrim in-place to ARGB pixels so hero titles and
     * dock tiles stay legible over bright artwork. Pure pixel math for off-UI-thread decoding and
     * JVM unit tests.
     */
    static void applyVignette(int[] px, int w, int h) {
        if (px == null || w <= 0 || h <= 0 || px.length < w * h) return;
        float bottomStart = h * 0.55f;
        float bottomSpan = Math.max(1f, h - bottomStart);
        float leftEnd = Math.max(1f, w * 0.35f);

        for (int y = 0; y < h; y++) {
            float bt = y > bottomStart ? (y - bottomStart) / bottomSpan : 0f;
            float bottomDark = 0.78f * bt * bt;
            int row = y * w;
            for (int x = 0; x < w; x++) {
                float lt = x < leftEnd ? (leftEnd - x) / leftEnd : 0f;
                float leftDark = 0.55f * lt * lt;
                float keep = (1f - bottomDark) * (1f - leftDark);
                if (keep >= 0.999f) continue;
                int p = px[row + x];
                int a = p & 0xFF000000;
                int r = Math.round(((p >>> 16) & 0xFF) * keep);
                int g = Math.round(((p >>> 8) & 0xFF) * keep);
                int b = Math.round((p & 0xFF) * keep);
                px[row + x] = a | (r << 16) | (g << 8) | b;
            }
        }
    }

    /**
     * Sampled decoder for editorial hero artwork: scales down to screen size (within 4K bounds)
     * and applies {@link #applyVignette(int[], int, int)}. Intended to run off the UI thread.
     */
    static Bitmap decodeHero(File file, int screenW, int screenH) {
        return decodeHeroFile(file, screenW, screenH, true);
    }

    /** Decodes crisp hero artwork for Bento showcase cards without full-screen edge darkening. */
    static Bitmap decodeRawHero(File file, int reqW, int reqH) {
        return decodeHeroFile(file, reqW, reqH, false);
    }

    /**
     * Loads the full-screen 1920x1080 Projectivy backdrop in priority order with zero storage permissions:
     * app external files {@code wallpaper.jpg}, {@code /sdcard/tvhome/wallpaper.jpg}, then {@code R.drawable.bg_projectivy}.
     */
    static Bitmap loadProjectivyBackdrop(Context context, int reqW, int reqH) {
        if (context == null) return null;
        int w = reqW > 0 ? reqW : 1920;
        int h = reqH > 0 ? reqH : 1080;
        File ext = context.getExternalFilesDir(null);
        File[] candidates = {
                ext != null ? new File(ext, Config.WALLPAPER_NAME) : null,
                new File("/sdcard/tvhome/" + Config.WALLPAPER_NAME)
        };
        for (File f : candidates) {
            if (f != null && f.isFile() && f.canRead()) {
                Bitmap b = decodeRawHero(f, w, h);
                if (b != null) return b;
            }
        }
        try {
            return BitmapFactory.decodeResource(context.getResources(), R.drawable.bg_projectivy);
        } catch (Exception | OutOfMemoryError ignored) {
            return null;
        }
    }

    private static Bitmap decodeHeroFile(File file, int screenW, int screenH, boolean vignette) {
        if (file == null || !file.isFile() || screenW <= 0 || screenH <= 0) return null;
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), options);
            int width = options.outWidth, height = options.outHeight;
            if (width <= 0 || height <= 0) return null;
            int sample = WallpaperSize.sampleSize(width, height, screenW, screenH);
            if (!WallpaperSize.accepted(width / sample, height / sample)) return null;
            options.inJustDecodeBounds = false;
            options.inSampleSize = sample;
            int[] scale = WallpaperSize.coverScale(width / sample, height / sample, screenW, screenH);
            if (scale != null) {
                options.inScaled = true;
                options.inDensity = scale[0];
                options.inTargetDensity = scale[1];
            }
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            options.inMutable = vignette;
            Bitmap bmp = BitmapFactory.decodeFile(file.getPath(), options);
            if (bmp == null) return null;
            if (vignette) {
                if (!bmp.isMutable()) {
                    Bitmap copy = bmp.copy(Bitmap.Config.ARGB_8888, true);
                    bmp.recycle();
                    if (copy == null) return null;
                    bmp = copy;
                }
                int bw = bmp.getWidth(), bh = bmp.getHeight();
                int[] px = new int[bw * bh];
                bmp.getPixels(px, 0, bw, 0, 0, bw, bh);
                applyVignette(px, bw, bh);
                bmp.setPixels(px, 0, bw, 0, 0, bw, bh);
            }
            return bmp;
        } catch (Exception | OutOfMemoryError failure) {
            return null;
        }
    }

    /** Returns sorted {@code .mp4} files from {@code aerial/} and {@code wallpapers/} in {@code themeDir}. */
    static List<File> listAerialVideos(File themeDir) {
        if (themeDir == null || !themeDir.isDirectory()) return Collections.emptyList();
        List<File> videos = new ArrayList<>();
        collectVideos(new File(themeDir, ThemeFiles.AERIAL), videos);
        collectVideos(new File(themeDir, ThemeFiles.WALLPAPERS), videos);
        if (videos.isEmpty()) return Collections.emptyList();
        videos.sort(Comparator.comparing((File f) -> f.getName().toLowerCase(Locale.ROOT))
                .thenComparing(File::getPath));
        return Collections.unmodifiableList(videos);
    }

    private static void collectVideos(File dir, List<File> out) {
        if (!dir.isDirectory()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isFile() && ThemeFiles.isVideo(f.getName())) {
                out.add(f);
            }
        }
    }

    /** The softened artwork, about 5 KB; null when there is nothing to draw. */
    static Bitmap from(Drawable live) {
        if (live == null) return null;
        // Draw a private copy: changing the bounds of the drawable a tile shows would blank the tile.
        Drawable d = live.getConstantState() != null ? live.getConstantState().newDrawable().mutate() : live;
        Rect saved = new Rect(d.getBounds());
        Bitmap big = Bitmap.createBitmap(W * 4, H * 4, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(big);
        c.drawColor(Color.BLACK);
        d.setBounds(0, 0, big.getWidth(), big.getHeight());
        d.draw(c);
        d.setBounds(saved);
        // Halve twice: each filtered halving averages 2×2 pixels, where one 4× step would skip most of them.
        Bitmap half = Bitmap.createScaledBitmap(big, W * 2, H * 2, true);
        big.recycle();
        Bitmap small = Bitmap.createScaledBitmap(half, W, H, true);
        half.recycle();

        int[] px = new int[W * H];
        small.getPixels(px, 0, W, 0, 0, W, H);
        saturate(px);
        // Three box passes approximate a gaussian, so the stretched image shows no seams between pixels.
        int[] tmp = new int[W * H];
        for (int pass = 0; pass < 3; pass++) {
            DockBlur.box(px, tmp, W, H, 2, true);
            DockBlur.box(tmp, px, W, H, 2, false);
        }
        Bitmap out = small.isMutable() ? small : small.copy(Bitmap.Config.ARGB_8888, true);
        if (out != small) small.recycle();
        out.setPixels(px, 0, W, 0, 0, W, H);
        return out;
    }

    private static void saturate(int[] px) {
        for (int i = 0; i < px.length; i++) {
            int p = px[i];
            int r = Color.red(p), g = Color.green(p), b = Color.blue(p);
            float lum = 0.2126f * r + 0.7152f * g + 0.0722f * b;
            px[i] = Color.rgb(clamp(lum + (r - lum) * SATURATION), clamp(lum + (g - lum) * SATURATION),
                    clamp(lum + (b - lum) * SATURATION));
        }
    }

    private static int clamp(float v) {
        return v < 0 ? 0 : v > 255 ? 255 : Math.round(v);
    }
}

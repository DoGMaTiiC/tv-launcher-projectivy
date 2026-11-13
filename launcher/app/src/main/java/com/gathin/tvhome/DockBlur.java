package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;

/**
 * Atmospheric blur utilities and no-op Liquid Glass stubs (Projectivy keeps cards floating
 * directly over the live video wallpaper with no tvOS dock platter).
 */
final class DockBlur {

    static final int FROSTED_TOP_COLOR = 0x18FFFFFF;
    static final int FROSTED_BOTTOM_COLOR = 0x0AFFFFFF;
    static final int SPECULAR_RIM_COLOR = 0x48FFFFFF;
    static final float SPECULAR_RIM_DP = 1.5f;

    static final int FW = 192, FH = 108, GW = 480, GH = 270;
    static final float SHADOW_BLUR = 26f, DEEP_BLUR = 30f;

    static final class Atmosphere {
        final Bitmap backdropBitmap;
        final int[] glassPixels;

        Atmosphere(Bitmap backdropBitmap, int[] glassPixels) {
            this.backdropBitmap = backdropBitmap;
            this.glassPixels = glassPixels;
        }
    }

    private DockBlur() {}

    static int frostedTopColor() { return FROSTED_TOP_COLOR; }

    static int frostedBottomColor() { return FROSTED_BOTTOM_COLOR; }

    static int[] frostedGradientColors(boolean dockBlur, boolean lightGlass) {
        if (dockBlur) return new int[]{FROSTED_TOP_COLOR, FROSTED_BOTTOM_COLOR};
        return lightGlass ? new int[]{0x24FFFFFF, 0x0DFFFFFF} : null;
    }

    static float rimStrokeWidthPx(float density, boolean dockBlur) {
        return density <= 0f ? 0f : (dockBlur ? SPECULAR_RIM_DP : 1f) * density;
    }

    static int rimColor(boolean dockBlur, boolean lightGlass, boolean translucent) {
        return dockBlur ? SPECULAR_RIM_COLOR : (lightGlass ? 0x38FFFFFF : translucent ? 0x26FFFFFF : 0x1FFFFFFF);
    }

    static int[] cropRectPx(int bitmapW, int bitmapH, int screenW, int screenH,
                            float bandLeft, float bandTop, float bandRight, float bandBottom) {
        if (bitmapW <= 0 || bitmapH <= 0 || screenW <= 0 || screenH <= 0
                || bandRight <= bandLeft || bandBottom <= bandTop) return null;
        float sx = bitmapW / (float) screenW, sy = bitmapH / (float) screenH;
        int l = clamp(Math.round(bandLeft * sx), bitmapW);
        int t = clamp(Math.round(bandTop * sy), bitmapH);
        int r = clamp(Math.round(bandRight * sx), bitmapW);
        int b = clamp(Math.round(bandBottom * sy), bitmapH);
        return (r > l && b > t) ? new int[]{l, t, r, b} : null;
    }

    static void softenPixels(int[] pixels, int w, int h, int radius, float saturationBoost) {
        if (pixels == null || w <= 0 || h <= 0 || pixels.length < w * h) return;
        int total = w * h;
        if (radius > 0) {
            int[] scratch = new int[total];
            box(pixels, scratch, w, h, radius, true);
            box(scratch, pixels, w, h, radius, false);
        }
        if (saturationBoost != 1f) {
            for (int i = 0; i < total; i++) {
                int c = pixels[i];
                int r = (c >>> 16) & 0xFF, g = (c >>> 8) & 0xFF, b = c & 0xFF;
                float luma = 0.299f * r + 0.587f * g + 0.114f * b;
                pixels[i] = (c & 0xFF000000)
                        | (clamp(Math.round(luma + (r - luma) * saturationBoost), 255) << 16)
                        | (clamp(Math.round(luma + (g - luma) * saturationBoost), 255) << 8)
                        | clamp(Math.round(luma + (b - luma) * saturationBoost), 255);
            }
        }
    }

    static Bitmap softenBackdropCrop(Bitmap source) {
        if (source == null || source.isRecycled()) return null;
        int srcW = source.getWidth(), srcH = source.getHeight();
        if (srcW <= 0 || srcH <= 0) return null;
        int dstW = Math.min(srcW, 96);
        int dstH = Math.max(1, Math.round(srcH * (dstW / (float) srcW)));
        Bitmap scaled = (dstW == srcW && dstH == srcH) ? source : Bitmap.createScaledBitmap(source, dstW, dstH, true);
        int[] pixels = new int[dstW * dstH];
        scaled.getPixels(pixels, 0, dstW, 0, 0, dstW, dstH);
        if (scaled != source) scaled.recycle();
        softenPixels(pixels, dstW, dstH, 3, 1.15f);
        return Bitmap.createBitmap(pixels, dstW, dstH, Bitmap.Config.ARGB_8888);
    }

    /**
     * Produces the ultra-smooth atmospheric wash (192x108) for the full-screen background and the
     * lightly blurred 480x270 buffer sampled by the Liquid Glass shader, for Night or Day mode.
     */
    static Atmosphere makeAtmosphere(Bitmap src, boolean nightMode) {
        if (src == null || src.isRecycled() || src.getWidth() <= 0 || src.getHeight() <= 0) return null;
        int[] frost = reduce(src, FW, FH, 3, 3);
        int[] glass = reduce(src, GW, GH, 2, 2);
        if (nightMode) {
            tint(frost, 0.76f, 11f, 11f, 13f);
            tint(glass, 0.56f, 11f, 11f, 13f);
        } else {
            tint(frost, 0.82f, 255f, 255f, 255f);
            tint(glass, 0.74f, 255f, 255f, 255f);
        }
        Bitmap bg = Bitmap.createBitmap(frost, FW, FH, Bitmap.Config.ARGB_8888);
        return new Atmosphere(bg, glass);
    }

    private static int[] reduce(Bitmap full, int w, int h, int radius, int passes) {
        Bitmap b = full;
        while (b.getWidth() / 2 >= w * 1.5f) {
            Bitmap half = Bitmap.createScaledBitmap(b, b.getWidth() / 2, b.getHeight() / 2, true);
            if (b != full) b.recycle();
            b = half;
        }
        Bitmap small = (b.getWidth() == w && b.getHeight() == h) ? b : Bitmap.createScaledBitmap(b, w, h, true);
        if (b != full && b != small) b.recycle();
        int[] px = new int[w * h];
        small.getPixels(px, 0, w, 0, 0, w, h);
        if (small != full) small.recycle();
        int[] tmp = new int[px.length];
        for (int pass = 0; pass < passes; pass++) {
            box(px, tmp, w, h, radius, true);
            box(tmp, px, w, h, radius, false);
        }
        return px;
    }

    static void box(int[] in, int[] out, int w, int h, int r, boolean horizontal) {
        int n = horizontal ? w : h, lines = horizontal ? h : w;
        int step = horizontal ? 1 : w, lineStep = horizontal ? w : 1;
        int span = 2 * r + 1;
        for (int line = 0; line < lines; line++) {
            int base = line * lineStep;
            int sa = 0, sr = 0, sg = 0, sb = 0;
            for (int i = -r; i <= r; i++) {
                int p = in[base + clamp(i, n - 1) * step];
                sa += (p >>> 24) & 255;
                sr += (p >> 16) & 255;
                sg += (p >> 8) & 255;
                sb += p & 255;
            }
            for (int i = 0; i < n; i++) {
                out[base + i * step] = ((sa / span) << 24)
                        | ((sr / span) << 16) | ((sg / span) << 8) | (sb / span);
                int add = in[base + Math.min(n - 1, i + r + 1) * step];
                int sub = in[base + Math.max(0, i - r) * step];
                sa += ((add >>> 24) & 255) - ((sub >>> 24) & 255);
                sr += ((add >> 16) & 255) - ((sub >> 16) & 255);
                sg += ((add >> 8) & 255) - ((sub >> 8) & 255);
                sb += (add & 255) - (sub & 255);
            }
        }
    }

    private static void tint(int[] px, float amount, float tr, float tg, float tb) {
        float keep = 1f - amount;
        for (int i = 0; i < px.length; i++) {
            int p = px[i];
            int r = Math.round(((p >> 16) & 255) * keep + tr * amount);
            int g = Math.round(((p >> 8) & 255) * keep + tg * amount);
            int b = Math.round((p & 255) * keep + tb * amount);
            px[i] = 0xFF000000 | (clamp(r, 255) << 16) | (clamp(g, 255) << 8) | clamp(b, 255);
        }
    }

    private static int clamp(int v, int max) {
        return v < 0 ? 0 : Math.min(v, max);
    }

    /** No-op shadow cache stub (Projectivy dock-less mode draws no tvOS platter shadow). */
    static final class ShadowCache {
        void draw(Canvas c, RectF shape, float r, float blur, int argb) {
            return;
        }
    }

    /** No-op Liquid Glass stub (Projectivy draws no tvOS dock bar or frosted platter). */
    static final class LiquidRenderer {
        void setGlassPixels(int[] pixels) {
        }

        void draw(Canvas c, RectF r, float radius, int screenW, int screenH,
                  float offX, float offY, boolean nightMode, float lift, float specular) {
            return;
        }
    }
}

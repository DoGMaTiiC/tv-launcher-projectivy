package com.gathin.tvhome;

/**
 * Pure math and constants for the tvOS Focus Slab (`tile_tilt`):
 * 1.05x focus scale, 2-layer inner banner parallax (1.035x), directional 3D tilt (max 3.5 deg),
 * and specular highlight shift across GlassView.
 */
final class TileTilt {

    /** tvOS Focus Slab scale (1.05x instead of 1.10x/1.12x so neighbours never collide). */
    static final float FOCUS_SCALE = 1.05f;
    /** Inner banner/icon parallax scale on focus. */
    static final float BANNER_PARALLAX_SCALE = 1.035f;
    /** Maximum directional tilt angle in degrees (within the 2-4 deg tvOS spec). */
    static final float MAX_TILT_DEG = 3.5f;
    /** Camera distance in dp so a 320x180dp banner tilts without harsh foreshortening. */
    static final float CAMERA_DISTANCE_DP = 1280f;
    /** Spring stiffness for tilt return-to-rest (critically damped). */
    static final float TILT_STIFFNESS = 600f;
    /** Spring damping ratio for tilt return-to-rest (no bounce). */
    static final float TILT_DAMPING = 0.85f;

    private TileTilt() {
    }

    /**
     * Computes initial Y-axis rotation impulse (degrees) when focus moves horizontally by {@code dxPx}
     * (focused tile X minus previous tile X) for a tile of width {@code tileWidthPx}.
     * Moving right (dxPx > 0) banks the leading right edge toward the viewer (+rotationY).
     */
    static float rotationYForDx(float dxPx, int tileWidthPx) {
        if (tileWidthPx <= 0 || dxPx == 0f) return 0f;
        float norm = clamp(dxPx / (float) tileWidthPx, -1.5f, 1.5f);
        return clamp((norm / 1.5f) * MAX_TILT_DEG, -MAX_TILT_DEG, MAX_TILT_DEG);
    }

    /**
     * Computes initial X-axis rotation impulse (degrees) when focus moves vertically by {@code dyPx}
     * (focused tile Y minus previous tile Y) for a tile of height {@code tileHeightPx}.
     * Moving down (dyPx > 0) banks the bottom edge (-rotationX).
     */
    static float rotationXForDy(float dyPx, int tileHeightPx) {
        if (tileHeightPx <= 0 || dyPx == 0f) return 0f;
        float norm = clamp(dyPx / (float) tileHeightPx, -1.5f, 1.5f);
        return clamp((-norm / 1.5f) * MAX_TILT_DEG, -MAX_TILT_DEG, MAX_TILT_DEG);
    }

    /**
     * Normalized [-1f, 1f] specular highlight shift from current tilt angles.
     */
    static float specularShift(float rotationX, float rotationY) {
        if (MAX_TILT_DEG <= 0f) return 0f;
        return clamp((rotationY - rotationX * 0.5f) / MAX_TILT_DEG, -1f, 1f);
    }

    /** Camera distance in pixels for {@link android.view.View#setCameraDistance(float)}. */
    static float cameraDistancePx(float density) {
        return CAMERA_DISTANCE_DP * Math.max(1f, density) * Math.max(1f, density);
    }

    static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}

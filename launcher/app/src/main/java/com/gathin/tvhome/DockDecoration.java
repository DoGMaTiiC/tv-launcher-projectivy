package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/** Draws the tvOS style lighter band behind the first (dock) row and adds a gap below it. */
public class DockDecoration extends RecyclerView.ItemDecoration {

    private final int dockSize;
    private final float density;
    private final Paint band = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint backdropPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Matrix backdropMatrix = new Matrix();
    private final RectF backdropSrc = new RectF();
    private final RectF rect = new RectF();

    /** Extra band below the tiles for their names (always_show_names), in px. */
    private final float namesRoom;
    /** Light glass like the top bar's pills (config.xml dock_glass): a white tint that fades downwards. */
    private final boolean lightGlass;
    /** Frosted Glass Dock (config.xml dock_blur): softened backdrop crop + 0x18FFFFFF->0x0AFFFFFF fill + 1.5dp rim. */
    private final boolean dockBlur;
    private DockBlur.LiquidRenderer liquidRenderer;
    private DockBlur.ShadowCache shadowCache;
    private boolean nightMode = true;
    private final int[] winLoc = new int[2];
    private final RectF shadowRect = new RectF();
    private final Paint radiosityPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix radiosityMatrix = new Matrix();
    private int radiosityColor = 0;
    private RadialGradient radiosityShader;
    /** Padding all round the tiles equal to the gap between them (config.xml dock_even_padding). */
    private final boolean evenPadding;
    /** The band's top and bottom the glass gradient was made for; it is remade when they move. */
    private float shaderTop = Float.NaN, shaderBottom = Float.NaN;
    private float backdropLeft = Float.NaN, backdropTop = Float.NaN, backdropRight = Float.NaN, backdropBottom = Float.NaN;
    private Bitmap backdropBitmap;
    private BitmapShader backdropShader;
    /** Draw the band and its edge (config.xml dock_band); false leaves the tiles floating on the wallpaper. */
    private final boolean bandVisible;
    /** single_row: the tiles after the dock share its row, so they take the same offsets to stay level with it. */
    private final boolean singleRow;
    /** dock_band_all: in a single row, the band runs behind every app instead of the dock apps only. */
    private final boolean bandAll;
    /** Projectivy mode: no dock band, draws multi-pass coral-red neon focus glow around focused tile. */
    private boolean projectivyMode;
    private final Paint neonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF neonRect = new RectF();

    /**
     * @param translucent the cinematic shelf's band: the artwork and the glow show through it.
     * @param names       the dock tiles show their names, so the band reaches down over them.
     * @param opacity     the plain band's opacity in percent (dock_opacity), so a wallpaper can show through it.
     * @param lightGlass  a light frosted band like the pills, for a sky or wallpaper behind it.
     * @param evenPadding the band's padding is the gap between the tiles on all four sides (dock_even_padding).
     * @param bandVisible false skips the band and its edge, so the tiles float on the wallpaper (dock_band).
     * @param singleRow   every app sits in the dock's row (single_row); the band covers the dock apps only, unless bandAll.
     * @param bandAll     with singleRow, the band runs behind every app in the row (dock_band_all).
     */
    DockDecoration(int dockSize, float density, boolean translucent, boolean names, int opacity, boolean lightGlass,
                   boolean evenPadding, boolean bandVisible, boolean singleRow, boolean bandAll) {
        this(dockSize, density, translucent, names, opacity, lightGlass, evenPadding, bandVisible, singleRow, bandAll, false);
    }

    /**
     * @param dockBlur Glass Dock mode: frosted translucent fill, optional softened backdrop crop, and 1.5dp specular rim.
     */
    DockDecoration(int dockSize, float density, boolean translucent, boolean names, int opacity, boolean lightGlass,
                   boolean evenPadding, boolean bandVisible, boolean singleRow, boolean bandAll, boolean dockBlur) {
        this.dockSize = dockSize;
        this.singleRow = singleRow;
        this.bandAll = singleRow && bandAll;
        this.density = density;
        this.namesRoom = names ? 22 * density : 0;
        this.lightGlass = lightGlass;
        this.dockBlur = dockBlur;
        this.evenPadding = evenPadding;
        this.bandVisible = bandVisible;
        band.setColor(translucent ? 0x8C121014 : (Math.round(opacity * 2.55f) << 24) | 0x141416);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(DockBlur.rimStrokeWidthPx(density, dockBlur));
        edge.setColor(DockBlur.rimColor(dockBlur, lightGlass, translucent));
    }

    /** Attaches shared AGSL Liquid Glass renderer and appearance variant for the shelf platter. */
    void setLiquidGlass(DockBlur.LiquidRenderer liquid, boolean nightMode) {
        this.liquidRenderer = liquid;
        this.nightMode = nightMode;
        if (this.shadowCache == null) this.shadowCache = new DockBlur.ShadowCache();
    }

    void setNightMode(boolean nightMode) {
        this.nightMode = nightMode;
    }

    void setProjectivyMode(boolean projectivyMode) {
        this.projectivyMode = projectivyMode;
        neonPaint.setStyle(Paint.Style.STROKE);
    }

    /** Sets an optional wallpaper crop behind the Glass Dock; softened once off-frame and drawn via BitmapShader. */
    public void setBackdropCrop(Bitmap crop) {
        Bitmap softened = DockBlur.softenBackdropCrop(crop);
        if (this.backdropBitmap != null && this.backdropBitmap != softened) {
            this.backdropBitmap.recycle();
        }
        this.backdropBitmap = softened;
        if (softened != null) {
            this.backdropShader = new BitmapShader(softened, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            int w = softened.getWidth(), h = softened.getHeight();
            this.backdropSrc.set(0f, h * 0.65f, w, h * 0.92f);
            this.backdropPaint.setShader(this.backdropShader);
        } else {
            this.backdropShader = null;
            this.backdropPaint.setShader(null);
        }
        this.backdropLeft = Float.NaN;
    }

    /** Concentric corners: the band's radius is the tiles' radius plus the band's padding around them. */
    static float radiusPx(float tileRadiusPx, float paddingPx) {
        return tileRadiusPx + paddingPx;
    }

    /**
     * The band's padding on every side for dock_even_padding: the gap between two dock tiles (a cell minus its tile, as
     * AppAdapter.tileWidthPx lays them out), capped by the room the grid leaves at its edges.
     */
    static float evenPaddingPx(int innerWidthPx, int columns, int tileWidthPx, float roomPx) {
        float gap = columns > 0 ? innerWidthPx / (float) columns - tileWidthPx : 0f;
        return Math.max(0f, Math.min(gap, roomPx));
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent,
                               @NonNull RecyclerView.State state) {
        int pos = parent.getChildAdapterPosition(view);
        if (projectivyMode) {
            float k = parent.getWidth() > 0 ? parent.getWidth() / 1920f : density / 2f;
            outRect.top = 0;
            outRect.bottom = (!singleRow && pos >= 0 && pos < dockSize)
                    ? Math.round(145f * k)
                    : Math.round(16f * k);
            return;
        }
        if (pos >= 0 && (singleRow || pos < dockSize)) {
            outRect.top = Math.round((dockBlur ? 12f : 6f) * density);
            outRect.bottom = Math.round((dockBlur ? 24f : 12f) * density);
        }
    }

    @Override
    public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        if (projectivyMode) return;
        if (bandVisible) {
            drawDockPlatter(c, parent);
        }
        if (dockBlur) {
            drawFocusedRadiosity(c, parent);
        }
    }

    @Override
    public void onDrawOver(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        if (!projectivyMode) return;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            RecyclerView.ViewHolder vh = parent.getChildViewHolder(child);
            View card = vh instanceof AppAdapter.Holder ? ((AppAdapter.Holder) vh).card : child.findViewById(R.id.card);
            if (card == null || card.getVisibility() != View.VISIBLE) continue;
            if (!card.isFocused() && !child.hasFocus()) continue;
            float cx = child.getLeft() + card.getLeft() + card.getWidth() * 0.5f
                    + child.getTranslationX() + card.getTranslationX();
            float cy = child.getTop() + card.getTop() + card.getHeight() * 0.5f
                    + child.getTranslationY() + card.getTranslationY();
            float halfW = card.getWidth() * card.getScaleX() * 0.5f;
            float halfH = card.getHeight() * card.getScaleY() * 0.5f;
            float k = parent.getWidth() > 0 ? parent.getWidth() / 1920f : density / 2f;
            drawNeonFocusGlow(c, cx - halfW, cy - halfH, cx + halfW, cy + halfH, k);
            return;
        }
    }

    private void drawNeonFocusGlow(Canvas c, float l, float t, float r, float b, float k) {
        float baseRadius = 3f * k;
        // 20 concentric expanded rounded-rect strokes from 30*k down to 1.5*k for soft coral-red neon bloom.
        for (int i = 0; i < 20; i++) {
            float progress = i / 19f;
            float offset = (30f - i * 1.5f) * k;
            float strokeW = (i >= 16 ? 2.8f : 3.6f) * k;
            int alpha = Math.round(3f + 132f * progress * progress * progress);
            int green = Math.round(0x45 + (0x78 - 0x45) * progress);
            int blue = Math.round(0x36 + (0x66 - 0x36) * progress);
            neonPaint.setColor((alpha << 24) | (0xFF << 16) | (green << 8) | blue);
            neonPaint.setStrokeWidth(strokeW);
            neonRect.set(l - offset, t - offset, r + offset, b + offset);
            float rad = baseRadius + offset * 0.45f;
            c.drawRoundRect(neonRect, rad, rad, neonPaint);
        }
        // Soft luminous coral inner core border of 2.6*k
        neonPaint.setColor(0xCCFF8E7C);
        neonPaint.setStrokeWidth(2.6f * k);
        neonRect.set(l - 0.5f * k, t - 0.5f * k, r + 0.5f * k, b + 0.5f * k);
        c.drawRoundRect(neonRect, baseRadius, baseRadius, neonPaint);
    }

    private void drawDockPlatter(Canvas c, RecyclerView parent) {
        boolean any = false;
        int cardWidth = 0;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            int pos = parent.getChildAdapterPosition(child);
            if (pos < 0 || (!bandAll && pos >= dockSize)) continue;
            RecyclerView.ViewHolder vh = parent.getChildViewHolder(child);
            View card = vh instanceof AppAdapter.Holder ? ((AppAdapter.Holder) vh).card : child.findViewById(R.id.card);
            if (card == null || (dockBlur && card.getVisibility() != View.VISIBLE)) continue;
            float l = child.getLeft() + card.getLeft();
            float t = child.getTop() + card.getTop() + child.getTranslationY();
            float r = l + card.getWidth();
            float b = t + card.getHeight();
            cardWidth = card.getWidth();
            if (!any) {
                rect.set(l, t, r, b);
                any = true;
            } else {
                rect.union(l, t, r, b);
            }
        }
        if (!any) return;
        float pad = 14 * density;
        // The band can only draw inside the grid, and the first and last dock tiles sit 12dp in from its
        // edges: a full 14dp at the sides sliced the rounded ends flat (#454). Keep the sides, with the
        // 1dp edge line, inside the grid; the top and bottom have room.
        // 22dp at the sides (the grid's 10dp padding makes room): a focused first or last tile grows about 7dp
        // outwards and still keeps about the band's top margin from its rounded end.
        float sidePad = 22 * density;
        float side = Math.max(0f, Math.min(sidePad, Math.min(rect.left, parent.getWidth() - rect.right) - density));
        float radius = AppAdapter.cornerRadius(cardWidth, density, dockBlur);
        if (evenPadding) {
            // The same padding on all four sides, so the band's corners are concentric with the tiles' everywhere.
            int inner = parent.getWidth() - parent.getPaddingLeft() - parent.getPaddingRight();
            float room = dockBlur
                    ? 20f * density
                    : (singleRow ? rect.top : Math.min(rect.top, Math.min(rect.left, parent.getWidth() - rect.right))) - density;
            float even = evenPaddingPx(inner, dockSize, cardWidth, room);
            if (dockBlur) even = Math.max(14f * density, Math.min(even, 18f * density));
            rect.inset(-even, -even);
            radius = radiusPx(radius, even);
        } else {
            // dock_even_padding off: the band as before, with its fixed 26 dp corners.
            rect.inset(singleRow ? -sidePad : -side, -pad);
            radius = 26 * density;
        }
        if (singleRow) {
            // Tiles past the screen's edge: the band stops at the grid's edge with its rounded end instead of being
            // sliced square there.
            rect.left = Math.max(rect.left, density);
            rect.right = Math.min(rect.right, parent.getWidth() - density);
        }
        rect.bottom += namesRoom;
        if (dockBlur && liquidRenderer != null) {
            View rootView = parent.getRootView();
            int screenW = rootView != null && rootView.getWidth() > 0 ? rootView.getWidth() : parent.getWidth();
            int screenH = rootView != null && rootView.getHeight() > 0 ? rootView.getHeight() : parent.getHeight();
            float k = screenW > 0 ? screenW / 1920f : 1f;
            parent.getLocationInWindow(winLoc);
            if (shadowCache != null) {
                shadowRect.set(rect);
                shadowRect.offset(0f, 16f * k);
                shadowCache.draw(c, shadowRect, radius, DockBlur.SHADOW_BLUR * k,
                        nightMode ? 0x7A000000 : 0x38000000);
            }
            liquidRenderer.draw(c, rect, radius, screenW, screenH,
                    winLoc[0], winLoc[1], nightMode, 0.04f, 0.85f);
            return;
        }
        if (dockBlur) {
            Shader savedShader = band.getShader();
            int savedColor = band.getColor();
            band.setShader(null);
            band.setColor(0x50000000);
            float shadowDy = 4f * density;
            rect.offset(0f, shadowDy);
            c.drawRoundRect(rect, radius, radius, band);
            rect.offset(0f, -shadowDy);
            band.setColor(savedColor);
            band.setShader(savedShader);
        }
        if (dockBlur && backdropShader != null) {
            if (rect.left != backdropLeft || rect.top != backdropTop
                    || rect.right != backdropRight || rect.bottom != backdropBottom) {
                backdropMatrix.setRectToRect(backdropSrc, rect, Matrix.ScaleToFit.FILL);
                backdropShader.setLocalMatrix(backdropMatrix);
                backdropLeft = rect.left;
                backdropTop = rect.top;
                backdropRight = rect.right;
                backdropBottom = rect.bottom;
            }
            c.drawRoundRect(rect, radius, radius, backdropPaint);
        }
        int[] gradientColors = DockBlur.frostedGradientColors(dockBlur, lightGlass);
        if (gradientColors != null && (rect.top != shaderTop || rect.bottom != shaderBottom)) {
            band.setShader(dockBlur
                    ? new LinearGradient(0, rect.top, 0, rect.bottom,
                            new int[]{0x38FFFFFF, 0x18141824, 0x24FFFFFF}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP)
                    : new LinearGradient(0, rect.top, 0, rect.bottom, gradientColors[0], gradientColors[1], Shader.TileMode.CLAMP));
            if (dockBlur) {
                edge.setShader(new LinearGradient(0, rect.top, 0, rect.bottom,
                        new int[]{0x90FFFFFF, 0x24FFFFFF, 0x48FFFFFF}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
            }
            shaderTop = rect.top;
            shaderBottom = rect.bottom;
        }
        c.drawRoundRect(rect, radius, radius, band);
        c.drawRoundRect(rect, radius, radius, edge);
    }

    /**
     * Draws ZZZZRadiosityImage-style ambient colored glow underneath the currently focused app tile
     * (+40pt bleed around the 250x150 icon, matching HeadBoardIconRendering).
     */
    private void drawFocusedRadiosity(Canvas c, RecyclerView parent) {
        RecyclerView.Adapter<?> raw = parent.getAdapter();
        if (!(raw instanceof AppAdapter)) return;
        AppAdapter adapter = (AppAdapter) raw;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            RecyclerView.ViewHolder vh = parent.getChildViewHolder(child);
            View card = vh instanceof AppAdapter.Holder ? ((AppAdapter.Holder) vh).card : child.findViewById(R.id.card);
            if (card == null || card.getVisibility() != View.VISIBLE) continue;
            if (!card.isFocused() && !child.hasFocus()) continue;
            int pos = parent.getChildAdapterPosition(child);
            if (pos < 0) return;
            int color = adapter.ambientColorAt(pos);
            int rgb = color & 0x00FFFFFF;
            if (radiosityShader == null || radiosityColor != rgb) {
                radiosityColor = rgb;
                int coreAlpha = nightMode ? 0x68000000 : 0x44000000;
                int midAlpha = nightMode ? 0x24000000 : 0x18000000;
                radiosityShader = new RadialGradient(0f, 0f, 1f,
                        new int[]{coreAlpha | rgb, midAlpha | rgb, 0x00000000},
                        new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
                radiosityPaint.setShader(radiosityShader);
            }
            float cx = child.getLeft() + card.getLeft() + card.getWidth() * 0.5f
                    + card.getTranslationX() + child.getTranslationX();
            float cy = child.getTop() + card.getTop() + card.getHeight() * 0.5f
                    + card.getTranslationY() + child.getTranslationY() + 8f * density;
            float rx = card.getWidth() * card.getScaleX() * 0.85f;
            float ry = card.getHeight() * card.getScaleY() * 0.98f;
            radiosityMatrix.reset();
            radiosityMatrix.setScale(rx, ry);
            radiosityMatrix.postTranslate(cx, cy);
            radiosityShader.setLocalMatrix(radiosityMatrix);
            c.drawOval(cx - rx, cy - ry, cx + rx, cy + ry, radiosityPaint);
            return;
        }
    }
}

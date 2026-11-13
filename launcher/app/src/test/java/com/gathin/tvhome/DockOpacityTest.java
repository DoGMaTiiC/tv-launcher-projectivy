package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DockOpacityTest {
    @Test public void aChoiceMadeOnTheTvWinsOverTheTheme() {
        // Theme says 80, the owner picked 30 in Launcher settings.
        assertEquals(30, Config.chosenDockOpacity(30, 80));
        assertEquals(100, Config.chosenDockOpacity(100, 80));
    }

    @Test public void noChoiceKeepsTheThemeValue() {
        // -1 is what Config passes when nothing is saved (or a theme push cleared it).
        assertEquals(80, Config.chosenDockOpacity(-1, 80));
    }

    @Test public void anOutOfRangeChoiceIsIgnored() {
        // Below 30 the band would vanish and the dock lose its shape; the theme key has the same floor.
        assertEquals(80, Config.chosenDockOpacity(0, 80));
        assertEquals(80, Config.chosenDockOpacity(29, 80));
        assertEquals(80, Config.chosenDockOpacity(101, 80));
    }

    @Test public void glassDockFrostedFillAndRimColorsMatchSpec() {
        assertEquals(0x18FFFFFF, DockBlur.frostedTopColor());
        assertEquals(0x0AFFFFFF, DockBlur.frostedBottomColor());
        assertArrayEquals(new int[]{0x18FFFFFF, 0x0AFFFFFF}, DockBlur.frostedGradientColors(true, false));
        assertArrayEquals(new int[]{0x24FFFFFF, 0x0DFFFFFF}, DockBlur.frostedGradientColors(false, true));
        assertNull(DockBlur.frostedGradientColors(false, false));

        assertEquals(0x48FFFFFF, DockBlur.rimColor(true, false, false));
        assertEquals(0x38FFFFFF, DockBlur.rimColor(false, true, false));
        assertEquals(0x26FFFFFF, DockBlur.rimColor(false, false, true));
        assertEquals(0x1FFFFFFF, DockBlur.rimColor(false, false, false));
    }

    @Test public void glassDockRimStrokeIsOnePointFiveDp() {
        assertEquals(3.0f, DockBlur.rimStrokeWidthPx(2.0f, true), 0.001f);
        assertEquals(2.0f, DockBlur.rimStrokeWidthPx(2.0f, false), 0.001f);
        assertEquals(0.0f, DockBlur.rimStrokeWidthPx(-1.0f, true), 0.001f);
    }

    @Test public void backdropCropRectMapsAndClampsToBitmapBounds() {
        int[] rect = DockBlur.cropRectPx(1920, 1080, 1920, 1080, 100f, 800f, 1820f, 1020f);
        assertNotNull(rect);
        assertArrayEquals(new int[]{100, 800, 1820, 1020}, rect);

        // Scaled bitmap (half-res) with out-of-bounds band edges gets clamped cleanly.
        int[] scaled = DockBlur.cropRectPx(960, 540, 1920, 1080, -20f, 540f, 2000f, 1100f);
        assertNotNull(scaled);
        assertArrayEquals(new int[]{0, 270, 960, 540}, scaled);

        assertNull(DockBlur.cropRectPx(0, 540, 1920, 1080, 0f, 0f, 100f, 100f));
        assertNull(DockBlur.cropRectPx(960, 540, 1920, 1080, 200f, 200f, 200f, 300f));
    }

    @Test public void softenPixelsBlursSharpEdgesAndPreservesAlpha() {
        int[] px = new int[5 * 5];
        for (int i = 0; i < px.length; i++) px[i] = 0xFF102030;
        // Single bright saturated pixel at center (2, 2).
        px[2 * 5 + 2] = 0xFFFF0000;

        DockBlur.softenPixels(px, 5, 5, 1, 1.15f);

        int center = px[2 * 5 + 2];
        int neighbor = px[2 * 5 + 1];
        assertEquals(0xFF, (center >>> 24) & 0xFF);
        assertTrue(((center >>> 16) & 0xFF) < 0xFF);
        assertTrue(((neighbor >>> 16) & 0xFF) > 0x10);
    }

    @Test public void dockCornersAreConcentricWithTileRadiusAndEvenPadding() {
        // 16dp tile radius at 2x density = 32px; 24px padding -> 56px band corner radius.
        assertEquals(56f, DockDecoration.radiusPx(32f, 24f), 0.001f);

        // 6 columns in 1200px inner width = 200px cell; 176px tile -> 24px gap.
        assertEquals(24f, DockDecoration.evenPaddingPx(1200, 6, 176, 40f), 0.001f);
        // Capped by available room at the grid edge.
        assertEquals(10f, DockDecoration.evenPaddingPx(1200, 6, 176, 10f), 0.001f);
        assertEquals(0f, DockDecoration.evenPaddingPx(1200, 0, 176, 10f), 0.001f);
    }
}

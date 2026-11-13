package com.gathin.tvhome;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class HeroArtTest {
    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    @Test public void findHeroFileMatchesSupportedExtensions() throws IOException {
        File theme = tmp.newFolder("theme");
        File heroes = new File(theme, "heroes");
        assertTrue(heroes.mkdirs());

        File netflix = new File(heroes, "com.netflix.ninja.jpg");
        assertTrue(netflix.createNewFile());
        File plex = new File(heroes, "com.plexapp.android.webp");
        assertTrue(plex.createNewFile());
        File kodi = new File(heroes, "org.xbmc.kodi.png");
        assertTrue(kodi.createNewFile());
        File youtube = new File(heroes, "com.google.android.youtube.tv.jpeg");
        assertTrue(youtube.createNewFile());

        assertEquals(netflix, HeroArt.findHeroFile(theme, "com.netflix.ninja"));
        assertEquals(plex, HeroArt.findHeroFile(theme, "com.plexapp.android"));
        assertEquals(kodi, HeroArt.findHeroFile(theme, "org.xbmc.kodi"));
        assertEquals(youtube, HeroArt.findHeroFile(theme, "com.google.android.youtube.tv"));
    }

    @Test public void findHeroFileReturnsNullWhenMissingOrInvalid() throws IOException {
        File theme = tmp.newFolder("theme");
        assertNull(HeroArt.findHeroFile(theme, "com.netflix.ninja"));
        assertNull(HeroArt.findHeroFile(null, "com.netflix.ninja"));
        assertNull(HeroArt.findHeroFile(theme, null));
        assertNull(HeroArt.findHeroFile(theme, ""));
        assertNull(HeroArt.findHeroFile(theme, "../secret"));

        File heroes = new File(theme, "heroes");
        assertTrue(heroes.mkdirs());
        assertTrue(new File(heroes, "com.netflix.ninja.bmp").createNewFile());
        assertNull(HeroArt.findHeroFile(theme, "com.netflix.ninja"));
    }

    @Test public void applyVignetteDarkensBottomAndLeftEdgePreservingTopRightAndAlpha() {
        int w = 100, h = 100;
        int[] px = new int[w * h];
        Arrays.fill(px, 0xFFFFFFFF);

        HeroArt.applyVignette(px, w, h);

        int topRight = px[5 * w + 95];
        int bottomCenter = px[99 * w + 50];
        int leftMid = px[30 * w];
        int rightMid = px[30 * w + 95];

        assertEquals(0xFF000000, bottomCenter & 0xFF000000);
        assertEquals(0xFFFFFFFF, topRight);
        assertTrue((bottomCenter & 0xFF) < (topRight & 0xFF) / 2);
        assertTrue((leftMid & 0xFF) < (rightMid & 0xFF));

        HeroArt.applyVignette(null, w, h);
        HeroArt.applyVignette(px, 0, h);
        HeroArt.applyVignette(px, w, 0);
    }

    @Test public void decodeHeroReturnsNullForMissingFileOrBadBounds() throws IOException {
        File missing = new File(tmp.getRoot(), "does-not-exist.jpg");
        assertNull(HeroArt.decodeHero(null, 1920, 1080));
        assertNull(HeroArt.decodeHero(missing, 1920, 1080));

        File empty = tmp.newFile("real.jpg");
        assertNotNull(empty);
        assertNull(HeroArt.decodeHero(empty, 0, 1080));
        assertNull(HeroArt.decodeHero(empty, 1920, 0));
    }
}

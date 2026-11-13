package com.gathin.tvhome;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AerialVideoTest {
    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    @Test public void listAerialVideosReturnsEmptyForNullOrMissingDirs() throws IOException {
        assertTrue(HeroArt.listAerialVideos(null).isEmpty());
        assertTrue(HeroArt.listAerialVideos(new File(tmp.getRoot(), "missing")).isEmpty());
        File emptyTheme = tmp.newFolder("empty");
        assertTrue(HeroArt.listAerialVideos(emptyTheme).isEmpty());
    }

    @Test public void listAerialVideosFiltersAndSortsAcrossAerialAndWallpapers() throws IOException {
        File theme = tmp.newFolder("theme");
        File aerial = new File(theme, "aerial");
        File wallpapers = new File(theme, "wallpapers");
        assertTrue(aerial.mkdirs());
        assertTrue(wallpapers.mkdirs());

        assertTrue(new File(aerial, "zeta.mp4").createNewFile());
        assertTrue(new File(aerial, "alpha.MP4").createNewFile());
        assertTrue(new File(wallpapers, "beta.mp4").createNewFile());

        // Non-video, hidden, or directory entries must be ignored.
        assertTrue(new File(aerial, "cover.jpg").createNewFile());
        assertTrue(new File(aerial, ".hidden.mp4").createNewFile());
        assertTrue(new File(aerial, "subdir.mp4").mkdirs());
        assertTrue(new File(wallpapers, "dusk.jpg").createNewFile());
        assertTrue(new File(wallpapers, "captions.txt").createNewFile());

        List<File> found = HeroArt.listAerialVideos(theme);
        assertEquals(3, found.size());
        assertEquals("alpha.MP4", found.get(0).getName());
        assertEquals("beta.mp4", found.get(1).getName());
        assertEquals("zeta.mp4", found.get(2).getName());
    }
}

package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import android.view.Gravity;

import org.junit.Test;

/**
 * Plain JVM unit tests for MusicPill's pure formatting, corner layout, and state machine seams.
 */
public class MusicPillTest {

    @Test
    public void formatDurationMsFormatsZeroAndNegativeAsZeroZero() {
        assertEquals("0:00", MusicPill.formatDurationMs(0L));
        assertEquals("0:00", MusicPill.formatDurationMs(-5000L));
        assertEquals("0:00", MusicPill.formatDurationMs(999L));
    }

    @Test
    public void formatDurationMsFormatsMinutesAndSecondsWithPaddedSeconds() {
        assertEquals("0:05", MusicPill.formatDurationMs(5_000L));
        assertEquals("3:42", MusicPill.formatDurationMs(222_000L));
        assertEquals("10:00", MusicPill.formatDurationMs(600_000L));
        assertEquals("65:03", MusicPill.formatDurationMs(3_903_450L));
    }

    @Test
    public void gravityForCornerMapsAllFourCornersAndDefaultsToBottomRight() {
        assertEquals(Gravity.BOTTOM | Gravity.RIGHT, MusicPill.gravityForCorner("bottom_right"));
        assertEquals(Gravity.BOTTOM | Gravity.LEFT, MusicPill.gravityForCorner("bottom_left"));
        assertEquals(Gravity.TOP | Gravity.RIGHT, MusicPill.gravityForCorner("top_right"));
        assertEquals(Gravity.TOP | Gravity.LEFT, MusicPill.gravityForCorner("top_left"));
        assertEquals(Gravity.BOTTOM | Gravity.RIGHT, MusicPill.gravityForCorner(null));
        assertEquals(Gravity.BOTTOM | Gravity.RIGHT, MusicPill.gravityForCorner("center"));
    }

    @Test
    public void nextCornerCyclesCleanlyThroughAllFourCorners() {
        assertEquals("bottom_left", MusicPill.nextCorner("bottom_right"));
        assertEquals("top_left", MusicPill.nextCorner("bottom_left"));
        assertEquals("top_right", MusicPill.nextCorner("top_left"));
        assertEquals("bottom_right", MusicPill.nextCorner("top_right"));
        assertEquals("bottom_right", MusicPill.nextCorner(null));
        assertEquals("bottom_right", MusicPill.nextCorner("unknown"));
    }

    @Test
    public void stateTransitionsFollowHiddenCompactExpandedLifecycle() {
        assertEquals(MusicPill.State.COMPACT, MusicPill.nextStateOnToggle(MusicPill.State.HIDDEN));
        assertEquals(MusicPill.State.HIDDEN, MusicPill.nextStateOnToggle(MusicPill.State.COMPACT));
        assertEquals(MusicPill.State.HIDDEN, MusicPill.nextStateOnToggle(MusicPill.State.EXPANDED));

        assertEquals(MusicPill.State.COMPACT, MusicPill.nextStateOnExpandToggle(MusicPill.State.HIDDEN));
        assertEquals(MusicPill.State.EXPANDED, MusicPill.nextStateOnExpandToggle(MusicPill.State.COMPACT));
        assertEquals(MusicPill.State.COMPACT, MusicPill.nextStateOnExpandToggle(MusicPill.State.EXPANDED));

        assertEquals(MusicPill.State.COMPACT, MusicPill.nextStateOnBack(MusicPill.State.EXPANDED));
        assertEquals(MusicPill.State.HIDDEN, MusicPill.nextStateOnBack(MusicPill.State.COMPACT));
        assertEquals(MusicPill.State.HIDDEN, MusicPill.nextStateOnBack(MusicPill.State.HIDDEN));
    }

    @Test
    public void formatDisplayLinesHandlesFallbackPlayingPausedAndArtistSource() {
        String[] fallback = MusicPill.formatDisplayLines(null);
        assertEquals("No media playing", fallback[0]);
        assertEquals("Start YouTube Music or SmartTube", fallback[1]);

        String[] empty = MusicPill.formatDisplayLines(new MusicPill.NowPlaying("", "", "", 0L, 0L, false));
        assertEquals("No media playing", empty[0]);
        assertEquals("Start YouTube Music or SmartTube", empty[1]);

        String[] playingNoArtist = MusicPill.formatDisplayLines(new MusicPill.NowPlaying("Track A", "", "", 1000L, 5000L, true));
        assertEquals("Track A", playingNoArtist[0]);
        assertEquals("Playing", playingNoArtist[1]);

        String[] pausedNoArtist = MusicPill.formatDisplayLines(new MusicPill.NowPlaying("Track A", "", "", 1000L, 5000L, false));
        assertEquals("Track A", pausedNoArtist[0]);
        assertEquals("Paused", pausedNoArtist[1]);

        String[] withArtistAndSource = MusicPill.formatDisplayLines(new MusicPill.NowPlaying("Midnight City", "M83", "YouTube Music", 45000L, 240000L, true));
        assertEquals("Midnight City", withArtistAndSource[0]);
        assertEquals("M83 · YouTube Music", withArtistAndSource[1]);
    }
}

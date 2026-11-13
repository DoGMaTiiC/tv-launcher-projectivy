package com.gathin.tvhome;

import org.junit.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Seam tests for {@link ControlCenter} quick-action list building, action IDs, and Now Playing formatting. */
public class ControlCenterTest {

    @Test
    public void actionEnumContainsAllControlCenterAndMediaIds() {
        EnumSet<ControlCenter.Action> all = EnumSet.allOf(ControlCenter.Action.class);
        assertEquals(9, all.size());
        assertTrue(all.contains(ControlCenter.Action.INPUTS));
        assertTrue(all.contains(ControlCenter.Action.WIFI));
        assertTrue(all.contains(ControlCenter.Action.SCREENSAVER));
        assertTrue(all.contains(ControlCenter.Action.LIBRARY));
        assertTrue(all.contains(ControlCenter.Action.REORDER));
        assertTrue(all.contains(ControlCenter.Action.SETTINGS));
        assertTrue(all.contains(ControlCenter.Action.MEDIA_PLAY_PAUSE));
        assertTrue(all.contains(ControlCenter.Action.MEDIA_NEXT));
        assertTrue(all.contains(ControlCenter.Action.TOGGLE_MUSIC_PILL));
    }

    @Test
    public void buildItemsIncludesAllQuickActionsWhenAllEnabled() {
        List<ControlCenter.Item> items = ControlCenter.buildItems(true, true, true);
        List<ControlCenter.Action> actions = actionsOf(items);
        assertEquals(
                java.util.Arrays.asList(
                        ControlCenter.Action.INPUTS,
                        ControlCenter.Action.WIFI,
                        ControlCenter.Action.SCREENSAVER,
                        ControlCenter.Action.LIBRARY,
                        ControlCenter.Action.REORDER,
                        ControlCenter.Action.SETTINGS
                ),
                actions
        );
        for (ControlCenter.Item item : items) {
            assertNotNull(item.action);
            assertNotNull(item.label);
            assertFalse(item.label.trim().isEmpty());
        }
    }

    @Test
    public void buildItemsOmitsOptionalActionsWhenDisabled() {
        List<ControlCenter.Item> items = ControlCenter.buildItems(false, false, false);
        List<ControlCenter.Action> actions = actionsOf(items);
        assertEquals(
                java.util.Arrays.asList(
                        ControlCenter.Action.WIFI,
                        ControlCenter.Action.SCREENSAVER,
                        ControlCenter.Action.SETTINGS
                ),
                actions
        );
    }

    @Test
    public void buildItemsRespectsIndividualOptionalFlags() {
        assertEquals(
                java.util.Arrays.asList(
                        ControlCenter.Action.INPUTS,
                        ControlCenter.Action.WIFI,
                        ControlCenter.Action.SCREENSAVER,
                        ControlCenter.Action.SETTINGS
                ),
                actionsOf(ControlCenter.buildItems(true, false, false))
        );
        assertEquals(
                java.util.Arrays.asList(
                        ControlCenter.Action.WIFI,
                        ControlCenter.Action.SCREENSAVER,
                        ControlCenter.Action.LIBRARY,
                        ControlCenter.Action.SETTINGS
                ),
                actionsOf(ControlCenter.buildItems(false, true, false))
        );
        assertEquals(
                java.util.Arrays.asList(
                        ControlCenter.Action.WIFI,
                        ControlCenter.Action.SCREENSAVER,
                        ControlCenter.Action.REORDER,
                        ControlCenter.Action.SETTINGS
                ),
                actionsOf(ControlCenter.buildItems(false, false, true))
        );
    }

    @Test
    public void formatNowPlayingWhenPlaying() {
        assertEquals("Playing · Midnight City · M83",
                ControlCenter.formatNowPlaying("Midnight City", "M83", true));
        assertEquals("Playing · Midnight City",
                ControlCenter.formatNowPlaying("Midnight City", "", true));
        assertEquals("Playing · Midnight City",
                ControlCenter.formatNowPlaying("Midnight City", null, true));
    }

    @Test
    public void formatNowPlayingWhenPaused() {
        assertEquals("Paused · Midnight City · M83",
                ControlCenter.formatNowPlaying("Midnight City", "M83", false));
        assertEquals("Paused · Midnight City",
                ControlCenter.formatNowPlaying("Midnight City", null, false));
    }

    @Test
    public void formatNowPlayingWhenEmpty() {
        assertEquals("Nothing playing", ControlCenter.formatNowPlaying(null, null, false));
        assertEquals("Nothing playing", ControlCenter.formatNowPlaying("", "", true));
        assertEquals("Nothing playing", ControlCenter.formatNowPlaying("   ", "  ", false));
    }

    @Test
    public void dispatchActionRoutesEveryActionToMatchingHostCallback() {
        List<String> calls = new ArrayList<>();
        ControlCenter.Host host = new ControlCenter.Host() {
            @Override public void onInputs() { calls.add("INPUTS"); }
            @Override public void onWifi() { calls.add("WIFI"); }
            @Override public void onScreensaver() { calls.add("SCREENSAVER"); }
            @Override public void onLibrary() { calls.add("LIBRARY"); }
            @Override public void onReorder() { calls.add("REORDER"); }
            @Override public void onSettings() { calls.add("SETTINGS"); }
            @Override public void onMediaPlayPause() { calls.add("MEDIA_PLAY_PAUSE"); }
            @Override public void onMediaNext() { calls.add("MEDIA_NEXT"); }
            @Override public void onToggleMusicPill() { calls.add("TOGGLE_MUSIC_PILL"); }
            @Override public boolean isMusicPillVisible() { return false; }
            @Override public String nowPlayingTitle() { return null; }
            @Override public String nowPlayingArtist() { return null; }
            @Override public boolean isMediaPlaying() { return false; }
        };

        for (ControlCenter.Action action : ControlCenter.Action.values()) {
            ControlCenter.dispatchAction(host, action);
            ControlCenter.dispatchAction(null, action);
        }
        ControlCenter.dispatchAction(host, null);

        assertEquals(ControlCenter.Action.values().length, calls.size());
        for (int i = 0; i < ControlCenter.Action.values().length; i++) {
            assertEquals(ControlCenter.Action.values()[i].name(), calls.get(i));
        }
    }

    private static List<ControlCenter.Action> actionsOf(List<ControlCenter.Item> items) {
        List<ControlCenter.Action> actions = new ArrayList<>(items.size());
        for (ControlCenter.Item item : items) {
            actions.add(item.action);
        }
        return actions;
    }
}

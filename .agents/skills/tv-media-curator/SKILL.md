---
name: tv-media-curator
description: Curate, validate, stage, and push local backdrops, Hero Artwork, and Aerial Screensaver MP4 loops to the offline TV launcher on TCL 65C6K 4K Google TV using tools/tv-media.py.
---

# TV Media Curator

Manage local visual media for the 100% offline tvOS-inspired TV launcher (`com.gathin.tvhome`) without network calls or `INTERNET` permission (per `docs/adr/0001-personal-offline-tvos-and-hybrid-glass.md`).

## Target Hardware Guidelines (TCL 65C6K 4K Google TV)

- **Backdrops (`wallpaper.jpg`, `wallpapers/*`) & Hero Artwork (`heroes/<package>.*`)**:
  - Max resolution: **3840x2160** (16:9 4K UHD).
  - Format: **JPEG** or **WebP** preferred (`.png` also accepted). Keep files reasonably sized for fast off-UI-thread sampled decoding.
  - Hero Artwork is displayed in the top highlight area with a bottom/side readability scrim when a dock tile receives focus (`hero_art: true`).
- **Aerial Screensaver (`aerial/*.mp4`)**:
  - Codec: **H.264 (AVC)** or **HEVC (H.265)** in an **MP4** container.
  - Resolution: **1080p** or **4K** (3840x2160) at 24/30/60 fps.
  - Audio: **Muted / no audio track** (`ffmpeg -i input.mov -an -c:v libx264 -pix_fmt yuv420p -movflags +faststart aerial/loop.mp4`).

## Naming & Directory Rules

All filenames must use ASCII `[A-Za-z0-9_.-]+`, with no spaces, no `..`, and no leading `.`:

- `config.json`: Theme overlay flags (e.g. `dock_blur`, `hero_art`, `control_center`, `music_pill`).
- `wallpaper.jpg`: Primary static backdrop.
- `wallpapers/<name>.(jpg|jpeg|png|webp|mp4)`: Rotating collection items.
- `heroes/<package>.(jpg|jpeg|png|webp)`: Per-app Hero Artwork keyed by Android package name (e.g., `heroes/com.netflix.ninja.jpg`).
- `aerial/<name>.mp4`: Local looping videos for the Aerial Screensaver.

## Workflow with `tools/tv-media.py`

1. **Stage media** into a local directory (e.g., `themes/tvos-glass` or `work/media`):
   ```bash
   tools/tv-media.py --stage-dir themes/tvos-glass --set-backdrop ~/Pictures/backdrop-4k.jpg
   tools/tv-media.py --stage-dir themes/tvos-glass --set-hero com.netflix.ninja ~/Pictures/netflix-hero.webp
   tools/tv-media.py --stage-dir themes/tvos-glass --add-aerial ~/Videos/ocean-4k-muted.mp4
   ```
2. **Update `config.json` flags** without clobbering existing keys:
   ```bash
   tools/tv-media.py --stage-dir themes/tvos-glass --set-config-flag hero_art=true --set-config-flag dock_blur=true
   ```
3. **Validate** all staged filenames, package names, and `config.json`:
   ```bash
   tools/tv-media.py --stage-dir themes/tvos-glass --validate
   ```
4. **Dry-run or push** to the TV via `adb` + `ThemeProvider` (`content://com.gathin.tvhome.theme/...`):
   ```bash
   tools/tv-media.py --stage-dir themes/tvos-glass --tv-host 192.168.1.50:5555 --push --dry-run
   tools/tv-media.py --stage-dir themes/tvos-glass --tv-host 192.168.1.50:5555 --push
   ```

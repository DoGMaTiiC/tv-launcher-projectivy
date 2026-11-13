import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
TV_MEDIA_PATH = REPO_ROOT / "tools" / "tv-media.py"


def load_tv_media():
    spec = importlib.util.spec_from_file_location("tv_media", TV_MEDIA_PATH)
    module = importlib.util.module_from_spec(spec)
    sys.modules["tv_media"] = module
    spec.loader.exec_module(module)
    return module


class TestValidation(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.mod = load_tv_media()

    def test_validate_filename_accepts_safe_images_and_videos(self):
        self.assertTrue(self.mod.validate_filename("aurora_01.jpg", self.mod.IMAGE_EXTS))
        self.assertTrue(self.mod.validate_filename("backdrop-4k.WEBP", self.mod.IMAGE_EXTS))
        self.assertTrue(self.mod.validate_filename("ocean-loop.mp4", self.mod.VIDEO_EXTS))

    def test_validate_filename_rejects_traversal_spaces_hidden_and_wrong_ext(self):
        for bad in ("../evil.jpg", "a..b.jpg", ".hidden.jpg", "my photo.jpg", "clip.mkv", "hero.exe"):
            self.assertFalse(self.mod.validate_filename(bad, self.mod.IMAGE_EXTS), f"should reject {bad}")
        self.assertFalse(self.mod.validate_filename("loop.mov", self.mod.VIDEO_EXTS))

    def test_validate_package_name(self):
        self.assertTrue(self.mod.validate_package_name("com.netflix.ninja"))
        self.assertTrue(self.mod.validate_package_name("org.xbmc.kodi"))
        self.assertTrue(self.mod.validate_package_name("com.google.android.youtube.tv"))
        for bad in ("netflix", ".com.foo", "com..foo", "com.foo/bar", "123.app", "com.foo-bar"):
            self.assertFalse(self.mod.validate_package_name(bad), f"should reject {bad}")


class TestStagingAndDirValidation(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.mod = load_tv_media()

    def test_stage_backdrop_hero_and_aerial(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            src_dir = root / "src"
            stage_dir = root / "stage"
            src_dir.mkdir()

            backdrop = src_dir / "my_bg.jpg"
            backdrop.write_bytes(b"\xff\xd8\xff\xe0fake-jpeg")
            hero = src_dir / "netflix_banner.webp"
            hero.write_bytes(b"RIFFfakewebp")
            aerial = src_dir / "ocean_4k.mp4"
            aerial.write_bytes(b"\x00\x00\x00\x18ftypmp42")

            out_bg = self.mod.stage_backdrop(stage_dir, backdrop)
            out_hero = self.mod.stage_hero(stage_dir, "com.netflix.ninja", hero)
            out_aerial = self.mod.stage_aerial(stage_dir, aerial)

            self.assertEqual(out_bg, stage_dir / "wallpaper.jpg")
            self.assertEqual(out_bg.read_bytes(), b"\xff\xd8\xff\xe0fake-jpeg")
            self.assertEqual(out_hero, stage_dir / "heroes" / "com.netflix.ninja.webp")
            self.assertEqual(out_hero.read_bytes(), b"RIFFfakewebp")
            self.assertEqual(out_aerial, stage_dir / "aerial" / "ocean_4k.mp4")
            self.assertEqual(out_aerial.read_bytes(), b"\x00\x00\x00\x18ftypmp42")
            self.assertEqual(self.mod.validate_stage_dir(stage_dir), [])

    def test_stage_rejects_invalid_inputs(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            stage_dir = root / "stage"
            bad_img = root / "not_an_image.txt"
            bad_img.write_text("hi")
            bad_vid = root / "my video.mp4"
            bad_vid.write_bytes(b"mp4")

            with self.assertRaises(ValueError):
                self.mod.stage_backdrop(stage_dir, bad_img)
            with self.assertRaises(ValueError):
                self.mod.stage_hero(stage_dir, "invalidpkg", bad_img)
            with self.assertRaises(ValueError):
                self.mod.stage_aerial(stage_dir, bad_vid)

    def test_validate_stage_dir_catches_bad_files_and_invalid_json(self):
        with tempfile.TemporaryDirectory() as tmp:
            stage_dir = Path(tmp)
            (stage_dir / "heroes").mkdir()
            (stage_dir / "heroes" / "badpkg.jpg").write_bytes(b"x")
            (stage_dir / "aerial").mkdir()
            (stage_dir / "aerial" / "bad space.mp4").write_bytes(b"x")
            (stage_dir / "config.json").write_text("{not-json")

            errors = self.mod.validate_stage_dir(stage_dir)
            self.assertEqual(len(errors), 3)


class TestConfigFlags(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.mod = load_tv_media()

    def test_set_config_flag_preserves_existing_keys_and_parses_types(self):
        with tempfile.TemporaryDirectory() as tmp:
            stage_dir = Path(tmp)
            (stage_dir / "config.json").write_text(
                json.dumps({"dock_blur": False, "greeting_name": "Patrick", "dock_columns": 6})
            )

            self.mod.set_config_flag(stage_dir, "hero_art", "true")
            self.mod.set_config_flag(stage_dir, "dock_blur", "true")
            self.mod.set_config_flag(stage_dir, "dock_columns", "7")
            self.mod.set_config_flag(stage_dir, "music_pill_corner", "bottom_right")

            written = json.loads((stage_dir / "config.json").read_text())
            self.assertEqual(
                written,
                {
                    "dock_blur": True,
                    "greeting_name": "Patrick",
                    "dock_columns": 7,
                    "hero_art": True,
                    "music_pill_corner": "bottom_right",
                },
            )

    def test_set_config_flag_creates_config_when_missing(self):
        with tempfile.TemporaryDirectory() as tmp:
            stage_dir = Path(tmp) / "new_theme"
            self.mod.set_config_flag(stage_dir, "control_center", "true")
            written = json.loads((stage_dir / "config.json").read_text())
            self.assertEqual(written, {"control_center": True})


class TestPushDryRun(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.mod = load_tv_media()

    def test_build_push_commands_assembles_adb_and_theme_provider_uris(self):
        with tempfile.TemporaryDirectory() as tmp:
            stage_dir = Path(tmp)
            (stage_dir / "config.json").write_text('{"hero_art": true}\n')
            (stage_dir / "wallpaper.jpg").write_bytes(b"bg")
            (stage_dir / "heroes").mkdir()
            (stage_dir / "heroes" / "com.netflix.ninja.jpg").write_bytes(b"hero")
            (stage_dir / "aerial").mkdir()
            (stage_dir / "aerial" / "clouds.mp4").write_bytes(b"mp4")

            cmds = self.mod.build_push_commands(stage_dir, tv_host="192.168.1.50:5555", revision="rev123")
            joined = [" ".join(c) for c in cmds]

            self.assertTrue(any("-s 192.168.1.50:5555 push" in line and "config.json" in line for line in joined))
            self.assertTrue(any("content://com.gathin.tvhome.theme/config.json" in line for line in joined))
            self.assertTrue(any("content://com.gathin.tvhome.theme/wallpaper.jpg" in line for line in joined))
            self.assertTrue(any("content://com.gathin.tvhome.theme/heroes/com.netflix.ninja.jpg" in line for line in joined))
            self.assertTrue(any("content://com.gathin.tvhome.theme/aerial/clouds.mp4" in line for line in joined))
            self.assertTrue(any("content://com.gathin.tvhome.theme/theme_revision" in line and "rev123" in line for line in joined))
            self.assertTrue(any("am force-stop com.gathin.tvhome" in line for line in joined))

    def test_cli_dry_run_push_exits_zero(self):
        import subprocess

        with tempfile.TemporaryDirectory() as tmp:
            stage_dir = Path(tmp)
            (stage_dir / "config.json").write_text('{"dock_blur": true}\n')
            proc = subprocess.run(
                [sys.executable, str(TV_MEDIA_PATH), "--stage-dir", str(stage_dir), "--push", "--dry-run"],
                capture_output=True,
                text=True,
                check=False,
            )
            self.assertEqual(proc.returncode, 0, proc.stderr)
            self.assertIn("content://com.gathin.tvhome.theme/config.json", proc.stdout)


if __name__ == "__main__":
    unittest.main()

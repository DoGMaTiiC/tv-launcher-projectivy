#!/usr/bin/env python3
"""Stage, validate, and push local media assets (backdrops, hero artwork, aerial loops) to the TV launcher.

Uses Python 3 stdlib only. Pushes files via `adb` and `ThemeProvider`
(`content://com.gathin.tvhome.theme/...`) so Android 11+ scoped storage works without root.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import uuid
from pathlib import Path
from typing import Any

PKG = "com.gathin.tvhome"
THEME_URI = f"content://{PKG}.theme"
REMOTE_DIR = f"/sdcard/Android/data/{PKG}/files"
REMOTE_TMP = "/data/local/tmp/cyt-media"

IMAGE_EXTS = (".jpg", ".jpeg", ".png", ".webp")
VIDEO_EXTS = (".mp4",)

_SAFE_NAME_RE = re.compile(r"^[A-Za-z0-9_.-]+$")
_PACKAGE_RE = re.compile(r"^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z][a-zA-Z0-9_]*)+$")


def validate_filename(name: str, allowed_exts: tuple[str, ...]) -> bool:
    """Return True if `name` is a safe single filename with one of `allowed_exts`."""
    if not name or name.startswith(".") or ".." in name:
        return False
    if "/" in name or "\\" in name:
        return False
    if not _SAFE_NAME_RE.match(name):
        return False
    lower = name.lower()
    return any(lower.endswith(ext) for ext in allowed_exts)


def validate_package_name(pkg: str) -> bool:
    """Return True if `pkg` is a valid Android application package name."""
    if not pkg or ".." in pkg:
        return False
    return bool(_PACKAGE_RE.match(pkg))


def _ensure_source_file(src: Path, allowed_exts: tuple[str, ...], check_name: bool = False) -> str:
    """Validate that `src` exists as a regular file with an allowed extension."""
    if not src.is_file():
        raise ValueError(f"source file not found: {src}")
    ext = src.suffix.lower()
    if ext not in allowed_exts:
        raise ValueError(f"unsupported extension '{ext}' for {src.name}; expected {allowed_exts}")
    if check_name and not validate_filename(src.name, allowed_exts):
        raise ValueError(f"unsafe filename '{src.name}' (use [A-Za-z0-9_.-]+ without '..')")
    return ext


def stage_backdrop(stage_dir: Path, src: Path) -> Path:
    """Copy a backdrop image into `<stage_dir>/wallpaper.jpg`."""
    _ensure_source_file(src, IMAGE_EXTS)
    stage_dir.mkdir(parents=True, exist_ok=True)
    dest = stage_dir / "wallpaper.jpg"
    shutil.copy2(src, dest)
    return dest


def stage_hero(stage_dir: Path, pkg: str, src: Path) -> Path:
    """Copy a hero artwork image into `<stage_dir>/heroes/<pkg><ext>`."""
    if not validate_package_name(pkg):
        raise ValueError(f"invalid Android package name: {pkg}")
    ext = _ensure_source_file(src, IMAGE_EXTS)
    heroes_dir = stage_dir / "heroes"
    heroes_dir.mkdir(parents=True, exist_ok=True)
    dest = heroes_dir / f"{pkg}{ext}"
    shutil.copy2(src, dest)
    return dest


def stage_aerial(stage_dir: Path, src: Path) -> Path:
    """Copy an aerial MP4 loop into `<stage_dir>/aerial/<name>.mp4`."""
    _ensure_source_file(src, VIDEO_EXTS, check_name=True)
    aerial_dir = stage_dir / "aerial"
    aerial_dir.mkdir(parents=True, exist_ok=True)
    dest = aerial_dir / src.name
    shutil.copy2(src, dest)
    return dest


def validate_stage_dir(stage_dir: Path) -> list[str]:
    """Validate all staged media and config.json in `stage_dir`; return error strings."""
    errors: list[str] = []
    if not stage_dir.is_dir():
        return [f"stage directory does not exist: {stage_dir}"]

    cfg_path = stage_dir / "config.json"
    if cfg_path.exists():
        try:
            data = json.loads(cfg_path.read_text(encoding="utf-8-sig"))
            if not isinstance(data, dict):
                errors.append("config.json must be a JSON object")
        except (OSError, ValueError) as exc:
            errors.append(f"config.json is invalid JSON: {exc}")

    heroes_dir = stage_dir / "heroes"
    if heroes_dir.is_dir():
        for entry in sorted(heroes_dir.iterdir()):
            if not entry.is_file():
                continue
            if not validate_filename(entry.name, IMAGE_EXTS):
                errors.append(f"invalid hero filename: heroes/{entry.name}")
                continue
            pkg = entry.stem
            if not validate_package_name(pkg):
                errors.append(f"invalid hero package name: heroes/{entry.name}")

    wallpapers_dir = stage_dir / "wallpapers"
    if wallpapers_dir.is_dir():
        for entry in sorted(wallpapers_dir.iterdir()):
            if not entry.is_file() or entry.name == "captions.txt":
                continue
            if not validate_filename(entry.name, IMAGE_EXTS + VIDEO_EXTS):
                errors.append(f"invalid wallpaper filename: wallpapers/{entry.name}")

    aerial_dir = stage_dir / "aerial"
    if aerial_dir.is_dir():
        for entry in sorted(aerial_dir.iterdir()):
            if not entry.is_file():
                continue
            if not validate_filename(entry.name, VIDEO_EXTS):
                errors.append(f"invalid aerial filename: aerial/{entry.name}")

    return errors


def _parse_flag_value(raw: str) -> Any:
    """Parse a JSON literal (bool, int, float, null) or return the raw string."""
    lowered = raw.strip()
    if lowered in ("true", "false", "null"):
        return json.loads(lowered)
    try:
        if "." in lowered:
            return float(lowered)
        return int(lowered)
    except ValueError:
        return raw


def set_config_flag(stage_dir: Path, key: str, raw_value: str) -> dict[str, Any]:
    """Update `<key>` in `<stage_dir>/config.json` without clobbering existing keys."""
    if not key or "=" in key:
        raise ValueError(f"invalid config key: {key!r}")
    stage_dir.mkdir(parents=True, exist_ok=True)
    cfg_path = stage_dir / "config.json"
    data: dict[str, Any] = {}
    if cfg_path.exists():
        loaded = json.loads(cfg_path.read_text(encoding="utf-8-sig"))
        if not isinstance(loaded, dict):
            raise ValueError("existing config.json is not a JSON object")
        data = loaded
    data[key] = _parse_flag_value(raw_value)
    cfg_path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    return data


def collect_stage_files(stage_dir: Path) -> list[tuple[Path, str]]:
    """Return sorted (local_path, relative_uri_path) pairs from `stage_dir`."""
    files: list[tuple[Path, str]] = []
    for root_name in ("config.json", "wallpaper.jpg"):
        candidate = stage_dir / root_name
        if candidate.is_file():
            files.append((candidate, root_name))
    for subdir in ("heroes", "wallpapers", "aerial"):
        sub_path = stage_dir / subdir
        if sub_path.is_dir():
            for entry in sorted(sub_path.iterdir()):
                if entry.is_file():
                    files.append((entry, f"{subdir}/{entry.name}"))
    return files


def _adb_prefix(adb_bin: str, tv_host: str | None) -> list[str]:
    cmd = [adb_bin]
    if tv_host:
        cmd.extend(["-s", tv_host])
    return cmd


def build_push_commands(
    stage_dir: Path,
    tv_host: str | None = None,
    adb_bin: str = "adb",
    revision: str | None = None,
) -> list[list[str]]:
    """Build the sequence of `adb` commands to push `stage_dir` via ThemeProvider."""
    errors = validate_stage_dir(stage_dir)
    if errors:
        raise ValueError("stage validation failed:\n- " + "\n- ".join(errors))

    files = collect_stage_files(stage_dir)
    if not files:
        raise ValueError(f"no staged media or config.json found in {stage_dir}")

    prefix = _adb_prefix(adb_bin, tv_host)
    rev = revision or uuid.uuid4().hex
    commands: list[list[str]] = []

    for local_path, rel_path in files:
        safe_tmp = f"{REMOTE_TMP}-{rel_path.replace('/', '-')}"
        uri = f"{THEME_URI}/{rel_path}"
        remote_parent = f"{REMOTE_DIR}/{Path(rel_path).parent}".rstrip("/.")
        shell_cmd = (
            f"rm -f {REMOTE_DIR}/{rel_path} 2>/dev/null; "
            f"out=$(content write --uri {uri} < {safe_tmp} 2>&1) && "
            f"case \"$out\" in *'Unsupported operation'*|*'Error while accessing provider'*) false ;; esac || "
            f"{{ mkdir -p {remote_parent} 2>/dev/null && cp {safe_tmp} {REMOTE_DIR}/{rel_path}; }}; "
            f"rm -f {safe_tmp}"
        )
        commands.append([*prefix, "push", str(local_path), safe_tmp])
        commands.append([*prefix, "shell", shell_cmd])

    rev_shell = (
        f"printf '%s' '{rev}' > {REMOTE_TMP}-theme_revision && "
        f"content write --uri {THEME_URI}/theme_revision < {REMOTE_TMP}-theme_revision; "
        f"rm -f {REMOTE_TMP}-theme_revision"
    )
    commands.append([*prefix, "shell", rev_shell])
    commands.append([*prefix, "shell", f"am force-stop {PKG}"])
    return commands


def run_push(
    stage_dir: Path,
    tv_host: str | None = None,
    adb_bin: str = "adb",
    dry_run: bool = False,
) -> int:
    """Execute or print the adb + ThemeProvider push commands."""
    commands = build_push_commands(stage_dir, tv_host=tv_host, adb_bin=adb_bin)
    for cmd in commands:
        if dry_run:
            print(" ".join(cmd))
        else:
            proc = subprocess.run(cmd, check=False)
            if proc.returncode != 0:
                return proc.returncode
    return 0


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Stage, validate, and push local TV launcher media via adb + ThemeProvider."
    )
    parser.add_argument(
        "--stage-dir",
        type=Path,
        default=Path("."),
        help="Local theme/media staging directory (default: current directory)",
    )
    parser.add_argument("--validate", action="store_true", help="Validate staged files and config.json")
    parser.add_argument("--set-backdrop", type=Path, metavar="FILE", help="Stage backdrop as wallpaper.jpg")
    parser.add_argument(
        "--set-hero",
        nargs=2,
        metavar=("PKG", "FILE"),
        help="Stage hero artwork as heroes/<pkg>.<ext>",
    )
    parser.add_argument("--add-aerial", type=Path, metavar="MP4", help="Stage muted MP4 loop under aerial/")
    parser.add_argument(
        "--set-config-flag",
        action="append",
        default=[],
        metavar="KEY=VALUE",
        help="Set or update a key in config.json without clobbering existing keys",
    )
    parser.add_argument("--push", action="store_true", help="Push staged directory to the TV via adb")
    parser.add_argument("--dry-run", action="store_true", help="Print adb commands without executing them")
    parser.add_argument("--tv-host", default=os.environ.get("TV_HOST") or None, help="ADB target IP[:port]")
    parser.add_argument("--adb", default=os.environ.get("TV_ADB_BINARY", "adb"), help="Path to adb binary")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(sys.argv[1:] if argv is None else argv)
    stage_dir: Path = args.stage_dir

    try:
        if args.set_backdrop:
            dest = stage_backdrop(stage_dir, args.set_backdrop)
            print(f"staged backdrop -> {dest}")
        if args.set_hero:
            pkg, hero_file = args.set_hero
            dest = stage_hero(stage_dir, pkg, Path(hero_file))
            print(f"staged hero -> {dest}")
        if args.add_aerial:
            dest = stage_aerial(stage_dir, args.add_aerial)
            print(f"staged aerial -> {dest}")
        for item in args.set_config_flag:
            if "=" not in item:
                raise ValueError(f"expected KEY=VALUE, got {item!r}")
            key, raw_val = item.split("=", 1)
            set_config_flag(stage_dir, key, raw_val)
            print(f"updated config.json: {key}")
        if args.validate:
            errors = validate_stage_dir(stage_dir)
            if errors:
                for err in errors:
                    sys.stderr.write(f"error: {err}\n")
                return 2
            print(f"valid: {stage_dir}")
        if args.push:
            return run_push(stage_dir, tv_host=args.tv_host, adb_bin=args.adb, dry_run=args.dry_run)
    except ValueError as exc:
        sys.stderr.write(f"error: {exc}\n")
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())

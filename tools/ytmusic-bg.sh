#!/usr/bin/env bash
# Grant background audio, Doze whitelist, and overlay permissions over ADB so YouTube Music /
# SmartTube can keep playing behind other apps or HDMI/PS5 inputs while the floating Music Pill
# stays visible on top (ADR 0002).
#
# Usage:
#   tools/ytmusic-bg.sh [--check] [-s <device_serial>] [device_serial] [package ...]
#
# Examples:
#   tools/ytmusic-bg.sh --check
#   tools/ytmusic-bg.sh 192.168.1.50:5555
#   tools/ytmusic-bg.sh 192.168.1.50:5555 com.google.android.apps.youtube.music
#   ANDROID_SERIAL=192.168.1.50:5555 tools/ytmusic-bg.sh com.teamsmart.videomanager.tv

set -euo pipefail

DRY_RUN=0
SERIAL="${ANDROID_SERIAL:-}"
LAUNCHER_PKG="com.gathin.tvhome"
DEFAULT_PACKAGES=(
  "com.google.android.apps.youtube.music"
  "com.teamsmart.videomanager.tv"
)
PACKAGES=()

usage() {
  cat <<'EOF'
Usage: tools/ytmusic-bg.sh [--check] [-s <device_serial>] [device_serial] [package ...]

Options:
  --check, -n        Dry-run: print the adb commands without executing them
  -s, --serial ID    Target ADB device serial (or set ANDROID_SERIAL)
  -h, --help         Show this help message

Defaults:
  Packages: com.google.android.apps.youtube.music com.teamsmart.videomanager.tv
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --check|-n)
      DRY_RUN=1
      shift
      ;;
    -s|--serial)
      if [ "$#" -lt 2 ] || [ -z "${2:-}" ]; then
        echo "error: $1 requires a device serial" >&2
        exit 2
      fi
      SERIAL="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    -*)
      echo "error: unknown flag $1" >&2
      usage >&2
      exit 2
      ;;
    *)
      # Treat the first positional argument as a device serial if no serial is set yet
      # and the argument contains ':' (host:port) or has no '.' (USB serial).
      if [ -z "$SERIAL" ] && [ "${#PACKAGES[@]}" -eq 0 ] && { [[ "$1" == *:* ]] || [[ "$1" != *.* ]]; }; then
        SERIAL="$1"
      else
        PACKAGES+=("$1")
      fi
      shift
      ;;
  esac
done

if [ "${#PACKAGES[@]}" -eq 0 ]; then
  PACKAGES=("${DEFAULT_PACKAGES[@]}")
fi

ADB_CMD=(adb)
if [ -n "$SERIAL" ]; then
  ADB_CMD+=(-s "$SERIAL")
fi

run_adb() {
  if [ "$DRY_RUN" -eq 1 ]; then
    printf '[dry-run]'
    printf ' %q' "${ADB_CMD[@]}" "$@"
    printf '\n'
    return 0
  fi
  "${ADB_CMD[@]}" "$@"
}

if [ "$DRY_RUN" -eq 0 ] && ! command -v adb >/dev/null 2>&1; then
  echo "error: adb not found in PATH (use --check for dry-run)" >&2
  exit 1
fi

# Allow the launcher to draw the floating Music Pill over apps and HDMI/PS5 inputs.
run_adb shell appops set "$LAUNCHER_PKG" SYSTEM_ALERT_WINDOW allow

# Allow each music package to stay alive in the background, play audio, and bypass Doze.
for pkg in "${PACKAGES[@]}"; do
  run_adb shell appops set "$pkg" RUN_IN_BACKGROUND allow
  run_adb shell appops set "$pkg" PLAY_AUDIO allow
  run_adb shell cmd deviceidle whitelist "+$pkg"
done

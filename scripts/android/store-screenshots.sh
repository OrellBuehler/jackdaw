#!/usr/bin/env bash
# Captures the Play Store phone screenshots from a booted emulator, in English and German,
# light and dark. The Debug app is launched once per screen with
# `--es store_screenshot <screen>` (see StoreScreenshots.kt): it then fills an empty
# library with bundled packs and an invented study history (JackdawSdk.seedDemoLibrary)
# and opens that screen, so nothing has to be tapped. A capture is only taken once the
# screen shows a text that proves it is rendered; a blank or half-loaded one is retried.
#
# Usage: store-screenshots.sh <output dir>
# Needs: adb with exactly one emulator (API 33 or newer) running and the Debug APK installed.
set -euo pipefail

OUT_DIR="$1"
PKG="ch.orellbuehler.jackdaw"
ACTIVITY="$PKG/.MainActivity"
WIDTH=1080
HEIGHT=1920
DENSITY=420
SETTLE_SECONDS="${SETTLE_SECONDS:-3}"
FIGURE_SECONDS="${FIGURE_SECONDS:-8}"
ATTEMPTS="${ATTEMPTS:-3}"
# A blank or half-loaded 1080x1920 screen compresses to far less than any real one.
MIN_PNG_BYTES=30000

WORK_DIR="$(mktemp -d)"

log() { echo "[store-screenshots] $*" >&2; }

adb_sh() { adb shell "$@" | tr -d '\r'; }

demo_cmd() {
  local command="$1"
  shift
  adb shell am broadcast -a com.android.systemui.demo -e command "$command" "$@" >/dev/null
}

prepare_device() {
  adb wait-for-device
  until [ "$(adb_sh getprop sys.boot_completed)" = "1" ]; do sleep 2; done
  adb shell input keyevent 82 || true
  adb shell wm dismiss-keyguard || true
  adb shell wm size "${WIDTH}x${HEIGHT}"
  adb shell wm density "$DENSITY"
  # Gesture navigation, like current phones, instead of the three buttons.
  adb shell cmd overlay enable com.android.internal.systemui.navbar.gestural || true
  adb shell settings put global window_animation_scale 0
  adb shell settings put global transition_animation_scale 0
  adb shell settings put global animator_duration_scale 0
  # Offline, so the pack list on the add screen is the bundled one on every run.
  if [ "${OFFLINE:-1}" = "1" ]; then
    adb shell svc wifi disable || true
    adb shell svc data disable || true
  fi
  adb shell cmd uimode night no
  # A new display size or a night mode switch can rebuild System UI, which drops demo
  # mode, so it is switched on last, after things have settled.
  sleep 5
  # Fixed clock, full battery, wifi only, no notification icons.
  adb shell settings put global sysui_demo_allowed 1
  demo_cmd enter
  demo_cmd clock -e hhmm 0941
  demo_cmd battery -e level 100 -e plugged false
  demo_cmd network -e wifi show -e level 4 -e fully true
  demo_cmd network -e mobile hide
  demo_cmd notifications -e visible false
  demo_cmd status -e volume hide -e bluetooth hide
  sleep 2
}

restore_device() {
  demo_cmd exit || true
  adb shell svc wifi enable || true
  adb shell cmd uimode night no || true
  adb shell wm size reset || true
  adb shell wm density reset || true
}
trap 'restore_device; rm -rf "$WORK_DIR"' EXIT

# launch <screen> [pack] [practice filter] [section]
launch() {
  local screen="$1" pack="${2:-}" filter="${3:-}" section="${4:-}"
  local args=(--es store_screenshot "$screen")
  if [ -n "$pack" ]; then args+=(--es store_screenshot_pack "$pack"); fi
  if [ -n "$filter" ]; then args+=(--es store_screenshot_filter "$filter"); fi
  if [ -n "$section" ]; then args+=(--es store_screenshot_section "$section"); fi
  adb shell am force-stop "$PKG"
  adb shell am start -W -n "$ACTIVITY" "${args[@]}" >/dev/null
}

# The accessibility tree of the screen, or nothing when the UI never goes idle.
dump_ui() {
  adb shell uiautomator dump /sdcard/store-screenshot-ui.xml >"$WORK_DIR/dump.log" 2>&1 || return 1
  adb exec-out cat /sdcard/store-screenshot-ui.xml
}

# wait_for_text <timeout> <text>; true once the text is on screen.
wait_for_text() {
  local timeout="$1" text="$2" waited=0 ui
  while [ "$waited" -lt "$timeout" ]; do
    if ui="$(dump_ui)" && grep -qF "$text" <<<"$ui"; then
      return 0
    fi
    sleep 1
    waited=$((waited + 1))
  done
  return 1
}

# Keeps what the screen looked like when an attempt failed, next to the screenshots.
save_debug() {
  local dir="$OUT_DIR/failed"
  mkdir -p "$dir"
  adb exec-out screencap -p >"$dir/$1.png" || true
  dump_ui >"$dir/$1.xml" || true
}

# shoot <locale> <name> <ready text> <screen> [pack] [practice filter] [section]
shoot() {
  local locale="$1" name="$2" ready="$3"
  shift 3
  local file="$OUT_DIR/$locale/$name.png" attempt bytes
  mkdir -p "$OUT_DIR/$locale"
  for attempt in $(seq 1 "$ATTEMPTS"); do
    log "$locale/$name (attempt $attempt)"
    launch "$@"
    if ! wait_for_text 60 "$ready"; then
      log "$locale/$name: screen never showed '$ready'"
      save_debug "$locale-$name-$attempt"
      continue
    fi
    # Figures are drawn by a web view, which is slow to start on an emulator.
    if [ "$1" = "study" ]; then sleep "$FIGURE_SECONDS"; fi
    sleep "$SETTLE_SECONDS"
    adb exec-out screencap -p >"$file"
    bytes="$(stat -c%s "$file")"
    if [ "$bytes" -lt "$MIN_PNG_BYTES" ]; then
      log "$locale/$name looks blank ($bytes bytes), capturing again"
      continue
    fi
    log "$locale/$name ok ($bytes bytes)"
    return 0
  done
  log "$locale/$name FAILED after $ATTEMPTS attempts"
  return 1
}

prepare_device
adb shell am force-stop "$PKG"
adb shell pm clear "$PKG" >/dev/null
failed=0

for locale in en de; do
  if [ "$locale" = "en" ]; then
    close="Close session"
    activity="Activity"
    add="Add pack"
  else
    close="Sitzung schliessen"
    activity="Aktivität"
    add="Pack hinzufügen"
  fi
  # The app follows the per-app language of the system; the library itself is the same.
  adb shell cmd locale set-app-locales "$PKG" --user 0 --locales "$locale-CH"

  adb shell cmd uimode night no
  shoot "$locale" library "Flags of the World" library || failed=1
  shoot "$locale" pack "Flags of the World" pack flags-of-the-world || failed=1
  shoot "$locale" study-flags "$close" study flags-of-the-world quiz europe-west || failed=1
  shoot "$locale" study-map "$close" study history-on-the-map flashcards antiquity || failed=1
  shoot "$locale" study-quiz "$close" study solar-system quiz || failed=1
  shoot "$locale" stats "$activity" stats || failed=1
  shoot "$locale" add "$add" add || failed=1

  adb shell cmd uimode night yes
  shoot "$locale" library-dark "Flags of the World" library || failed=1
  shoot "$locale" study-map-dark "$close" study history-on-the-map flashcards antiquity || failed=1
  shoot "$locale" stats-dark "$activity" stats || failed=1
done

exit "$failed"

#!/usr/bin/env bash
# Captures the App Store screenshots from a booted simulator, in English and German,
# light and dark. The Debug app is launched once per screen with
# `-store_screenshot <screen>` (see Support/StoreScreenshots.swift): it then fills an
# empty library with bundled packs and an invented study history
# (JackdawSdk.seedDemoLibrary) and opens that screen, so nothing has to be tapped.
#
# Usage: store-screenshots.sh <simulator udid> <output dir>
set -euo pipefail

DEVICE_ID="$1"
OUT_DIR="$2"
BUNDLE_ID="ch.orellbuehler.jackdaw"
SETTLE_SECONDS="${SETTLE_SECONDS:-8}"

xcrun simctl status_bar "$DEVICE_ID" override \
  --time "9:41" --dataNetwork wifi --wifiMode active --wifiBars 3 \
  --cellularMode active --cellularBars 4 --batteryState charged --batteryLevel 100

# capture <locale> <name> <appearance> <screen> [pack] [practice filter] [section]
capture() {
  local locale="$1" name="$2" appearance="$3" screen="$4" pack="${5:-}" filter="${6:-}" section="${7:-}"
  xcrun simctl ui "$DEVICE_ID" appearance "$appearance"
  xcrun simctl terminate "$DEVICE_ID" "$BUNDLE_ID" 2>/dev/null || true
  local args=(
    -store_screenshot "$screen"
    -AppleLanguages "($locale)"
    -AppleLocale "${locale}_CH"
  )
  if [ -n "$pack" ]; then args+=(-store_screenshot_pack "$pack"); fi
  if [ -n "$filter" ]; then args+=(-store_screenshot_filter "$filter"); fi
  if [ -n "$section" ]; then args+=(-store_screenshot_section "$section"); fi
  xcrun simctl launch "$DEVICE_ID" "$BUNDLE_ID" "${args[@]}" >/dev/null
  sleep "$SETTLE_SECONDS"
  mkdir -p "$OUT_DIR/$locale"
  local file="$OUT_DIR/$locale/$name.png"
  xcrun simctl io "$DEVICE_ID" screenshot "$file"
  # A blank screen compresses to well under this; a real one never does.
  if [ "$(stat -f%z "$file")" -lt 150000 ]; then
    echo "$locale/$name looks blank, waiting and capturing again" >&2
    sleep "$SETTLE_SECONDS"
    xcrun simctl io "$DEVICE_ID" screenshot "$file"
  fi
}

# The first launch after an install seeds the library and is slow enough to be
# captured blank, so start the app once and throw that launch away.
xcrun simctl launch "$DEVICE_ID" "$BUNDLE_ID" -store_screenshot library >/dev/null
sleep 25

for locale in en de; do
  capture "$locale" library light library
  capture "$locale" pack light pack flags-of-the-world
  capture "$locale" study-flags light study flags-of-the-world quiz europe-west
  capture "$locale" study-map light study history-on-the-map flashcards antiquity
  capture "$locale" study-quiz light study solar-system quiz
  capture "$locale" stats light stats
  capture "$locale" add light add
  capture "$locale" library-dark dark library
  capture "$locale" study-map-dark dark study history-on-the-map flashcards antiquity
  capture "$locale" stats-dark dark stats
done

xcrun simctl ui "$DEVICE_ID" appearance light
xcrun simctl status_bar "$DEVICE_ID" clear

#!/usr/bin/env bash
# Builds the debug APK and copies it to the project root directory.
set -e

echo "Building debug APK..."
gradle :app:assembleDebug

APK_SRC="app/build/outputs/apk/debug/app-debug.apk"
APK_DST="./app-debug.apk"

if [ -f "$APK_SRC" ]; then
  cp -f "$APK_SRC" "$APK_DST"
  echo "Successfully copied debug APK to $APK_DST"
else
  echo "Error: Debug APK not found at $APK_SRC"
  exit 1
fi

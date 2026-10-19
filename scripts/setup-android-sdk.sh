#!/usr/bin/env bash
# Installs the Android SDK pieces this project needs (idempotent). Used by cloud sessions;
# on your own computer Android Studio does this for you.
set -euo pipefail
SDK="${ANDROID_HOME:-$HOME/android-sdk}"
if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  version=$(curl -fsSL https://dl.google.com/android/repository/repository2-3.xml \
    | grep -oE 'commandlinetools-linux-[0-9]+_latest.zip' | sed -E 's/[^0-9]//g' | sort -n | tail -1)
  tmp=$(mktemp -d)
  curl -fsSL -o "$tmp/clt.zip" "https://dl.google.com/android/repository/commandlinetools-linux-${version}_latest.zip"
  mkdir -p "$SDK/cmdline-tools"
  unzip -q -o "$tmp/clt.zip" -d "$tmp"
  rm -rf "$SDK/cmdline-tools/latest"
  mv "$tmp/cmdline-tools" "$SDK/cmdline-tools/latest"
  rm -rf "$tmp"
fi
yes | "$SDK/cmdline-tools/latest/bin/sdkmanager" --licenses >/dev/null 2>&1 || true
"$SDK/cmdline-tools/latest/bin/sdkmanager" "platforms;android-35" "build-tools;35.0.0" "platform-tools" >/dev/null
root="$(cd "$(dirname "$0")/.." && pwd)"
[ -f "$root/local.properties" ] || echo "sdk.dir=$SDK" > "$root/local.properties"
echo "Android SDK ready at $SDK"

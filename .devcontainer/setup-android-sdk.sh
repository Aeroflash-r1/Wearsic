#!/usr/bin/env bash
set -e

echo "Setting up Android SDK..."

export ANDROID_HOME="$HOME/android-sdk"
mkdir -p "$ANDROID_HOME/cmdline-tools"

# Scrape the current command-line tools download URL directly from Google's
# official page instead of hardcoding a version number that will go stale.
TOOLS_URL=$(curl -s https://developer.android.com/studio | grep -o 'https://dl.google.com/android/repository/commandlinetools-linux-[0-9]*_latest.zip' | head -n 1)

if [ -z "$TOOLS_URL" ]; then
  echo "Could not auto-detect the command-line tools URL."
  echo "Go to https://developer.android.com/studio#command-line-tools-only,"
  echo "copy the Linux zip link, and re-run this script with:"
  echo "  TOOLS_URL=<paste-link-here> bash .devcontainer/setup-android-sdk.sh"
  exit 1
fi

echo "Using: $TOOLS_URL"
curl -sL "$TOOLS_URL" -o /tmp/cmdline-tools.zip
unzip -q /tmp/cmdline-tools.zip -d "$ANDROID_HOME/cmdline-tools"
mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
rm /tmp/cmdline-tools.zip

SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"

yes | "$SDKMANAGER" --licenses > /dev/null || true

# android-35 / build-tools 35.0.0 matches Wear OS 6 (One UI 8 Watch).
# If your AI agent picks a different compileSdk in Prompt 1, install the
# matching platform with: sdkmanager "platforms;android-<N>"
"$SDKMANAGER" "platform-tools" "platforms;android-35" "build-tools;35.0.0"

# Persist environment variables for every new shell in this Codespace
{
  echo "export ANDROID_HOME=\"$ANDROID_HOME\""
  echo "export ANDROID_SDK_ROOT=\"$ANDROID_HOME\""
  echo "export PATH=\"\$ANDROID_HOME/cmdline-tools/latest/bin:\$ANDROID_HOME/platform-tools:\$PATH\""
} >> "$HOME/.bashrc"

echo "Android SDK ready at $ANDROID_HOME"
echo "Open a new terminal tab (or run 'source ~/.bashrc') to pick up the environment variables."

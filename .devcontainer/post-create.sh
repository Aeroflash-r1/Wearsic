#!/usr/bin/env bash
set -euo pipefail

echo "==> Installing Freebuff CLI (npm i -g freebuff)"
npm install -g freebuff

echo "==> Toolchain check"
echo "-- Java:"
java -version
echo "-- Android SDK packages installed:"
sdkmanager --list_installed
echo "-- Freebuff:"
freebuff --version || echo "   (installed — run 'freebuff' inside a project folder to start it)"
echo "-- Gradle wrapper (once your project has one):"
echo "   run ./gradlew --version from your app's root folder"

cat <<'EOF'

==> Ready.
- ANDROID_HOME / ANDROID_SDK_ROOT: /opt/android-sdk (API 36, build-tools 36.0.0)
- cd into your project and run: freebuff
- This container builds/lints/tests the app but cannot run the Wear OS
  emulator (no KVM in Codespaces). Pair a physical watch over adb, or
  run the emulator on your own machine and tunnel it in if needed.
EOF

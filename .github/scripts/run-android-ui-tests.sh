#!/usr/bin/env bash
set -Eeuo pipefail

readonly AVD_NAME="ci-api30"
readonly SYSTEM_IMAGE="system-images;android-30;google_apis;x86"
readonly DEVICE_PROFILE="pixel_5"
readonly EMULATOR_ADB_SERIAL="emulator-5554"

export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
if [[ -z "${ANDROID_SDK_ROOT}" ]]; then
  echo "ANDROID_SDK_ROOT or ANDROID_HOME must be set" >&2
  exit 1
fi

SDKMANAGER="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
AVDMANAGER="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/avdmanager"
if [[ ! -x "$SDKMANAGER" ]]; then
  SDKMANAGER="$ANDROID_SDK_ROOT/cmdline-tools/bin/sdkmanager"
fi
if [[ ! -x "$AVDMANAGER" ]]; then
  AVDMANAGER="$ANDROID_SDK_ROOT/cmdline-tools/bin/avdmanager"
fi
EMULATOR="$ANDROID_SDK_ROOT/emulator/emulator"
ADB="$ANDROID_SDK_ROOT/platform-tools/adb"

if [[ ! -x "$SDKMANAGER" || ! -x "$AVDMANAGER" || ! -x "$EMULATOR" || ! -x "$ADB" ]]; then
  echo "Android SDK command-line tools are not fully installed" >&2
  exit 1
fi

# Accept Android SDK licenses quietly.
echo "Accepting Android SDK licenses..."
yes | "$SDKMANAGER" --licenses >/dev/null

# Ensure required SDK components are present.
echo "Ensuring required SDK components are installed..."
"$SDKMANAGER" --install "platform-tools" "platforms;android-30" "$SYSTEM_IMAGE"

# Delete any existing AVD to avoid stale configuration issues.
if "$AVDMANAGER" list avd | grep -q "Name: $AVD_NAME"; then
  "$AVDMANAGER" delete avd --name "$AVD_NAME"
fi

echo "Creating AVD $AVD_NAME..."
printf 'no\n' | "$AVDMANAGER" create avd --force --name "$AVD_NAME" --package "$SYSTEM_IMAGE" --device "$DEVICE_PROFILE"

echo "hw.cpu.ncore=2" >> "$HOME/.android/avd/$AVD_NAME.avd/config.ini"

echo "Starting emulator $AVD_NAME..."
"$EMULATOR" \
  -avd "$AVD_NAME" \
  -no-window \
  -gpu swiftshader_indirect \
  -no-snapshot \
  -no-audio \
  -no-boot-anim \
  -camera-front none \
  -camera-back none \
  -accel off \
  >/tmp/emulator.log 2>&1 &

EMU_PID=$!

cleanup() {
  local exit_code=$?
  echo "Stopping emulator"
  if (( exit_code != 0 )) && [[ -f /tmp/emulator.log ]]; then
    echo '::group::Emulator log'
    cat /tmp/emulator.log || true
    echo '::endgroup::'
  fi
  "$ADB" -s "$EMULATOR_ADB_SERIAL" emu kill >/dev/null 2>&1 || true
  if [[ -n "${EMU_PID:-}" ]]; then
    wait "$EMU_PID" >/dev/null 2>&1 || true
  fi
  "$AVDMANAGER" delete avd --name "$AVD_NAME" >/dev/null 2>&1 || true
  exit "$exit_code"
}
trap cleanup EXIT

# Ensure the ADB server is ready to communicate with the emulator.
"$ADB" start-server >/dev/null
"$ADB" wait-for-device >/dev/null

# Wait for the emulator to boot completely.
BOOT_TIMEOUT_SECONDS=900
SECONDS_WAITED=0
until "$ADB" -s "$EMULATOR_ADB_SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' | grep -q "1"; do
  sleep 5
  SECONDS_WAITED=$((SECONDS_WAITED + 5))
  if (( SECONDS_WAITED >= BOOT_TIMEOUT_SECONDS )); then
    echo "Emulator failed to boot within ${BOOT_TIMEOUT_SECONDS}s" >&2
    exit 1
  fi
  # Ensure ADB connection stays alive.
  "$ADB" wait-for-device >/dev/null 2>&1 || true
  if ! kill -0 "$EMU_PID" >/dev/null 2>&1; then
    echo "Emulator process exited prematurely" >&2
    exit 1
  fi
  echo "Waiting for emulator to boot... (${SECONDS_WAITED}s)"
done

echo "Emulator booted. Configuring device..."

# Keep the device awake and disable animations to reduce flakiness.
"$ADB" -s "$EMULATOR_ADB_SERIAL" shell svc power stayon true || true
"$ADB" -s "$EMULATOR_ADB_SERIAL" shell settings put global window_animation_scale 0 || true
"$ADB" -s "$EMULATOR_ADB_SERIAL" shell settings put global transition_animation_scale 0 || true
"$ADB" -s "$EMULATOR_ADB_SERIAL" shell settings put global animator_duration_scale 0 || true
"$ADB" -s "$EMULATOR_ADB_SERIAL" shell wm dismiss-keyguard || true
"$ADB" -s "$EMULATOR_ADB_SERIAL" shell input keyevent 82 || true

# Run the instrumentation tests.
./gradlew --stacktrace --no-daemon :sample:app:connectedDebugAndroidTest

# Tests finished successfully; cleanup handled by trap.

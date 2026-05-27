#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ANDROID_DIR="${ROOT_DIR}/android"
IMAGE="${ANDROID_SDK_IMAGE:-ghcr.io/cirruslabs/android-sdk:35}"
OUTPUT_DIR="${ROOT_DIR}/dist"

mkdir -p "${OUTPUT_DIR}"

echo "Building debug APK with Docker image: ${IMAGE}"

docker run --rm \
  -v "${ANDROID_DIR}:/project" \
  -v "sms-receiver-gradle-cache:/root/.gradle" \
  -w /project \
  "${IMAGE}" \
  bash -lc '
    set -euo pipefail
    echo "sdk.dir=${ANDROID_HOME}" > local.properties
    chmod +x gradlew
    ./gradlew assembleDebug --no-daemon --stacktrace
  '

APK_PATH="${ANDROID_DIR}/app/build/outputs/apk/debug/app-debug.apk"
if [[ ! -f "${APK_PATH}" ]]; then
  echo "APK not found at ${APK_PATH}"
  exit 1
fi

cp "${APK_PATH}" "${OUTPUT_DIR}/sms-receiver-debug.apk"
echo "APK ready: ${OUTPUT_DIR}/sms-receiver-debug.apk"

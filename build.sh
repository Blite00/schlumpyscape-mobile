#!/bin/bash
# Build the client jar, then the Android APK. Output: ./schlumpyscape-android.apk
#   ANDROID_HOME   Android SDK (platform 33, build-tools, NDK for the native bits if you rebuild them)
#   JAVA8_HOME     a JDK 8, used to compile the game client
#   JAVA11_HOME    a JDK 11 (or 17), used by Gradle for the launcher app
# To sign with your own key too: RS_KEYSTORE=/path/release.jks RS_KEYSTORE_PASSWORD=... RS_KEY_ALIAS=release ./build.sh
set -euo pipefail
cd "$(dirname "$0")"
: "${ANDROID_HOME:?set ANDROID_HOME to your Android SDK}"; export ANDROID_SDK_ROOT=$ANDROID_HOME
JAVA8_HOME=${JAVA8_HOME:-/usr/lib/jvm/java-8-openjdk-amd64}; JAVA11_HOME=${JAVA11_HOME:-/usr/lib/jvm/java-11-openjdk-amd64}
echo "== client (rt4.jar)"
( cd client-mobile && rm -rf client/build/libs && JAVA_HOME=$JAVA8_HOME PATH=$JAVA8_HOME/bin:$PATH ./gradlew --no-daemon :client:jar )
cp client-mobile/client/build/libs/client-1.0.0.jar launcher/app_pojavlauncher/src/main/assets/rt4.jar
echo "== launcher (APK)"
( cd launcher && JAVA_HOME=$JAVA11_HOME PATH=$JAVA11_HOME/bin:$PATH ./gradlew --no-daemon :app_pojavlauncher:assembleRelease )
cp launcher/app_pojavlauncher/build/outputs/apk/release/app_pojavlauncher-release.apk schlumpyscape-android.apk
ls -la schlumpyscape-android.apk

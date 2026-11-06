#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")"
task_root="$PWD"
# Standard SDK/JDK installation; no dependency on another project's checkout.
# DEVICE_PROBE_TOOLS remains an optional compatibility setting for the old layout.
task_legacy="${DEVICE_PROBE_TOOLS:-}"
if [[ -n "$task_legacy" ]]; then
    task_java="${JAVA_HOME:-$task_legacy/jdk-17.0.20.1+1}/bin"
    task_android="${ANDROID_JAR:-$task_legacy/platform/android-35/android.jar}"
    task_build="${BUILD_TOOLS_DIR:-$task_legacy/build-tools/android-15}"
else
    task_sdk="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
    if [[ -z "$task_sdk" && ( -z "${ANDROID_JAR:-}" || -z "${BUILD_TOOLS_DIR:-}" ) ]]; then
        echo 'Set ANDROID_SDK_ROOT (SDK platform android-35 and build-tools 35.0.0 required).' >&2
        exit 1
    fi
    if [[ -z "${JAVA_HOME:-}" ]]; then
        echo 'Set JAVA_HOME to a JDK 17 installation.' >&2
        exit 1
    fi
    task_java="$JAVA_HOME/bin"
    task_android="${ANDROID_JAR:-$task_sdk/platforms/android-35/android.jar}"
    task_build="${BUILD_TOOLS_DIR:-$task_sdk/build-tools/${ANDROID_BUILD_TOOLS_VERSION:-35.0.0}}"
fi
for task_tool in java javac jar keytool; do
    [[ -x "$task_java/$task_tool" ]] || { echo "Missing JDK tool: $task_tool" >&2; exit 1; }
done
for task_tool in aapt aapt2 zipalign; do
    [[ -x "$task_build/$task_tool" ]] || { echo "Missing Android build tool: $task_tool" >&2; exit 1; }
done
for task_file in "$task_android" "$task_build/core-lambda-stubs.jar" "$task_build/lib/d8.jar" "$task_build/lib/apksigner.jar"; do
    [[ -f "$task_file" ]] || { echo "Missing toolchain file: $task_file" >&2; exit 1; }
done
for task_tool in zip unzip grep; do
    command -v "$task_tool" >/dev/null || { echo "Missing command: $task_tool" >&2; exit 1; }
done
task_apk="DeviceProbe-1.4.0.apk"
mkdir -p build/signing
# A fresh output directory prevents removed classes from leaking into later APKs.
task_work="$(mktemp -d "$task_root/build/v1.4.0.XXXXXX")"
mkdir -p "$task_work/classes" "$task_work/generated" "$task_work/dex" "$task_work/test-classes"
"$task_java/javac" -encoding UTF-8 --release 8 -d "$task_work/test-classes" \
    src/cn/litebot/deviceprobe/MetricMath.java src/cn/litebot/deviceprobe/AudioEvidence.java \
    tests/MetricMathTest.java tests/AudioEvidenceTest.java
"$task_java/java" -cp "$task_work/test-classes" cn.litebot.deviceprobe.MetricMathTest
"$task_java/java" -cp "$task_work/test-classes" cn.litebot.deviceprobe.AudioEvidenceTest
"$task_build/aapt2" compile --dir res -o "$task_work/resources.zip"
"$task_build/aapt2" link -o "$task_work/resources.apk" --manifest AndroidManifest.xml \
    -I "$task_android" --java "$task_work/generated" "$task_work/resources.zip"
"$task_java/javac" -encoding UTF-8 -source 8 -target 8 -Xlint:deprecation \
    -bootclasspath "$task_android:$task_build/core-lambda-stubs.jar" -d "$task_work/classes" \
    src/cn/litebot/deviceprobe/*.java "$task_work/generated/cn/litebot/deviceprobe/R.java"
"$task_java/javac" -encoding UTF-8 --release 8 -cp "$task_work/classes:$task_android" \
    -d "$task_work/test-classes" tests/ReportTest.java
"$task_java/java" -cp "$task_work/test-classes:$task_work/classes:$task_android" cn.litebot.deviceprobe.ReportTest
"$task_java/jar" --create --file "$task_work/classes.jar" -C "$task_work/classes" .
"$task_java/java" -cp "$task_build/lib/d8.jar" com.android.tools.r8.D8 \
    --release --min-api 26 --lib "$task_android" --output "$task_work/dex" "$task_work/classes.jar"
cp "$task_work/resources.apk" "$task_work/unsigned.apk"
(cd "$task_work/dex" && zip -q -u ../unsigned.apk classes.dex)
"$task_build/zipalign" -f -P 16 4 "$task_work/unsigned.apk" "$task_work/aligned.apk"
if [[ ! -f build/signing/local.jks ]]; then
    # Disposable local-development credentials, NOT a production release key.
    # Never distribute the generated keystore. New checkouts cannot update old APKs.
    "$task_java/keytool" -genkeypair -noprompt -keystore build/signing/local.jks \
        -storepass android -keypass android -alias device-probe-local -keyalg RSA -keysize 3072 \
        -validity 3650 -dname "CN=Device Probe Local Build,O=Local Development,C=CN"
fi
"$task_java/java" -jar "$task_build/lib/apksigner.jar" sign --ks build/signing/local.jks \
    --ks-key-alias device-probe-local --ks-pass pass:android --key-pass pass:android \
    --out "$task_apk" "$task_work/aligned.apk"
"$task_java/java" -jar "$task_build/lib/apksigner.jar" verify --verbose "$task_apk"
"$task_build/zipalign" -c -P 16 4 "$task_apk"
"$task_build/aapt" dump badging "$task_apk"
"$task_build/aapt" dump permissions "$task_apk"
if "$task_build/aapt" dump permissions "$task_apk" | grep -q 'uses-permission'; then
    echo "ERROR: this offline build must not request permissions" >&2
    exit 1
fi
if unzip -Z1 "$task_apk" | grep -Eiq '(\.jks$|\.java$|\.pem$|password|signing/)'; then
    echo "ERROR: build-only material leaked into APK" >&2
    exit 1
fi
echo "PASS: zero requested permissions; no signing keys or source in APK"
if unzip -Z1 "$task_apk" | grep -q '^lib/'; then
    echo "ERROR: this Java-only build must not contain native libraries" >&2
    exit 1
fi
if unzip -p "$task_apk" classes.dex | LC_ALL=C grep -aiE 'vivid|vulkan'; then
    echo "ERROR: removed module remains in DEX" >&2
    exit 1
fi
if "$task_build/aapt" dump xmltree "$task_apk" AndroidManifest.xml | grep -Ei 'E: service|vivid|vulkan'; then
    echo "ERROR: removed module remains in manifest" >&2
    exit 1
fi
echo "PASS: no native libraries, Vivid/Vulkan code, or services"
if command -v sha256sum >/dev/null; then
    sha256sum "$task_apk"
else
    shasum -a 256 "$task_apk"
fi

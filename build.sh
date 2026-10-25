#!/usr/bin/env bash
# Copyright (C) 2026 Toit contributors.
set -euo pipefail
repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source_dir="$repo_root"
build_dir="$repo_root/build"
sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
build_tools="$sdk_root/build-tools/${ANDROID_BUILD_TOOLS:-36.0.0}"
android_jar="$sdk_root/platforms/${ANDROID_PLATFORM:-android-35}/android.jar"
mkdir -p "$build_dir/classes" "$build_dir/dex"
find "$build_dir/classes" "$build_dir/dex" -type f -delete
cd "$build_dir"
"$build_tools/aapt2" compile --dir "$source_dir/res" -o resources.zip
"$build_tools/aapt2" link -I "$android_jar" --manifest "$source_dir/AndroidManifest.xml" -o base.apk resources.zip
find "$source_dir/src" -name '*.java' > sources.txt
javac --release 8 -classpath "$android_jar" -d classes @sources.txt
jar cf classes.jar -C classes .
"$build_tools/d8" --min-api 26 --lib "$android_jar" --output dex classes.jar
cp base.apk with-dex.apk
(cd dex && zip -q -u ../with-dex.apk classes.dex)
"$build_tools/zipalign" -f 4 with-dex.apk aligned.apk
if [[ ! -f "$repo_root/build/debug.keystore" ]]; then
    keytool -genkeypair -keystore "$repo_root/build/debug.keystore" -storepass android -keypass android \
        -alias androiddebugkey -dname 'CN=Android Debug' -keyalg RSA -keysize 2048 -validity 10000
fi
"$build_tools/apksigner" sign --ks "$repo_root/build/debug.keystore" --ks-pass pass:android \
    --out remote-helper.apk aligned.apk
"$build_tools/apksigner" verify remote-helper.apk
echo "Built $build_dir/remote-helper.apk"

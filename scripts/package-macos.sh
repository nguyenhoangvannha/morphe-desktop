#!/usr/bin/env bash
set -euo pipefail

if [[ $(uname -s) != Darwin || $# != 2 ]]; then
    echo "Usage (on macOS): bash scripts/package-macos.sh <version> <all.jar>" >&2
    exit 1
fi

version="$1"
jar="$2"
if [[ ! "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.-]+)?$ || ! -f "$jar" ]]; then
    echo "Expected a release version and an existing runnable JAR." >&2
    exit 1
fi

case $(uname -m) in
    arm64) arch=arm64 ;;
    x86_64) arch=x64 ;;
    *) echo "Unsupported macOS architecture" >&2; exit 1 ;;
esac

project_dir=$(cd "$(dirname "$0")/.." && pwd)
output_dir="$project_dir/build/macos"
mkdir -p "$output_dir"
staging=$(mktemp -d "$output_dir/staging.XXXXXX")
trap 'rm -rf "$staging"' EXIT
mkdir -p "$staging/input"
cp "$jar" "$staging/input/$(basename "$jar")"

# Keep bin/java: MorpheLauncher starts a second JVM after downloading GUI natives.
# jpackage's default --strip-native-commands would remove that executable.
# CFBundleShortVersionString is numeric; the archive retains the prerelease suffix.
"$JAVA_HOME/bin/jpackage" \
    --type app-image \
    --name Morphe \
    --app-version "${version%%-*}" \
    --vendor MorpheApp \
    --description "Morphe Desktop" \
    --mac-package-identifier app.morphe.desktop \
    --icon "$project_dir/src/main/resources/morphe_logo.icns" \
    --input "$staging/input" \
    --main-jar "$(basename "$jar")" \
    --main-class app.morphe.MorpheLauncherKt \
    --add-modules ALL-MODULE-PATH \
    --jlink-options "--strip-debug --no-man-pages --no-header-files" \
    --java-options "--enable-native-access=ALL-UNNAMED" \
    --dest "$staging/image"

app="$staging/image/Morphe.app"
test -x "$app/Contents/runtime/Contents/Home/bin/java"
# Exercise the actual app launcher and its bundled runtime before publishing.
MORPHE_DATA_DIR="$staging/test-data" "$app/Contents/MacOS/Morphe" --version \
    | grep -F "Morphe Desktop v$version"

archive="$output_dir/morphe-desktop-$version-macos-$arch.app.zip"
ditto -c -k --sequesterRsrc --keepParent "$app" "$archive"
echo "Created $archive"

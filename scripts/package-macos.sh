#!/usr/bin/env bash
set -euo pipefail

project_dir=$(cd "$(dirname "$0")/.." && pwd)
output_dir="$project_dir/build/macos"

if [[ $(uname -s) != Darwin ]]; then
    echo "Error: macOS packaging is only supported on macOS." >&2
    exit 1
fi

check_is_jdk21() {
    local home="$1"
    if [[ -x "$home/bin/java" && -x "$home/bin/jpackage" ]]; then
        local ver
        ver=$("$home/bin/java" -version 2>&1 | sed -E -n 's/.*version "([0-9]+).*/\1/p')
        if [[ -n "$ver" && "$ver" -ge 21 ]] 2>/dev/null; then
            return 0
        fi
    fi
    return 1
}

resolve_jdk21() {
    # 1. Existing JAVA_HOME
    if [[ -n "${JAVA_HOME:-}" ]] && check_is_jdk21 "$JAVA_HOME"; then
        export JAVA_HOME
        return 0
    fi

    # 2. Standard Homebrew locations (arm64 & x86_64)
    for loc in \
        "/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home" \
        "/opt/homebrew/opt/openjdk@21" \
        "/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home" \
        "/usr/local/opt/openjdk@21"; do
        if check_is_jdk21 "$loc"; then
            export JAVA_HOME="$loc"
            return 0
        fi
    done

    # 3. macOS java_home tool
    if /usr/libexec/java_home -v 21 >/dev/null 2>&1; then
        local jh
        jh="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
        if [[ -n "$jh" ]] && check_is_jdk21 "$jh"; then
            export JAVA_HOME="$jh"
            return 0
        fi
    fi

    # 4. Standard JVM installation directory
    for candidate in /Library/Java/JavaVirtualMachines/*/Contents/Home; do
        if check_is_jdk21 "$candidate"; then
            export JAVA_HOME="$candidate"
            return 0
        fi
    done

    # 5. Check PATH java / jpackage
    if command -v jpackage >/dev/null 2>&1; then
        local jp_bin
        jp_bin="$(command -v jpackage)"
        while [[ -L "$jp_bin" ]]; do
            local link
            link="$(readlink "$jp_bin")"
            if [[ "$link" = /* ]]; then
                jp_bin="$link"
            else
                jp_bin="$(dirname "$jp_bin")/$link"
            fi
        done
        local candidate
        candidate="$(cd "$(dirname "$jp_bin")/.." && pwd)"
        if check_is_jdk21 "$candidate"; then
            export JAVA_HOME="$candidate"
            return 0
        fi
    fi

    echo "Error: JDK 21 or higher is required for jpackage. Please install JDK 21 (e.g. brew install openjdk@21) or set JAVA_HOME." >&2
    exit 1
}

resolve_jdk21

# Parse arguments or auto-detect
if [[ $# -eq 0 ]]; then
    version=$(grep -E '^version\s*=' "$project_dir/gradle.properties" | cut -d= -f2 | tr -d '[:space:]')
    jar="$project_dir/build/libs/morphe-desktop-$version-all.jar"
    if [[ ! -f "$jar" ]]; then
        # Try fallback matching pattern
        jar=$(ls -t "$project_dir"/build/libs/morphe-desktop-*-all.jar 2>/dev/null | head -n 1 || true)
    fi
    if [[ -z "$jar" || ! -f "$jar" ]]; then
        echo "==> Runnable JAR not found. Building it now..."
        bash "$project_dir/scripts/build-jar.sh"
        jar="$project_dir/build/libs/morphe-desktop-$version-all.jar"
    fi
elif [[ $# -eq 1 ]]; then
    if [[ "$1" == "-h" || "$1" == "--help" ]]; then
        echo "Usage: $0 [<version> <all.jar>]"
        echo "If no arguments are provided, version is read from gradle.properties"
        echo "and the runnable JAR is located or built automatically."
        exit 0
    fi
    version="$1"
    jar="$project_dir/build/libs/morphe-desktop-$version-all.jar"
elif [[ $# -eq 2 ]]; then
    version="$1"
    jar="$2"
else
    echo "Usage: $0 [<version> <all.jar>]" >&2
    exit 1
fi

if [[ ! "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.-]+)?$ || ! -f "$jar" ]]; then
    echo "Error: Expected a valid version (e.g. 2.3.2) and an existing runnable JAR ($jar)." >&2
    exit 1
fi

case $(uname -m) in
    arm64) arch=arm64 ;;
    x86_64) arch=x64 ;;
    *) echo "Unsupported macOS architecture: $(uname -m)" >&2; exit 1 ;;
esac

echo "=================================================="
echo "  Packaging Morphe Desktop macOS App (v$version - $arch)"
echo "=================================================="
echo "Using JDK: $JAVA_HOME"
echo "Input JAR: $jar"

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
echo "==> Verifying packaged macOS application launcher..."
MORPHE_DATA_DIR="$staging/test-data" "$app/Contents/MacOS/Morphe" --version \
    | grep -F "Morphe Desktop v$version"

# Copy unzipped Morphe.app into output directory for immediate local use
dest_app="$output_dir/Morphe.app"
rm -rf "$dest_app"
cp -R "$app" "$dest_app"

# Create .app.zip distribution archive
archive="$output_dir/morphe-desktop-$version-macos-$arch.app.zip"
ditto -c -k --sequesterRsrc --keepParent "$app" "$archive"

echo ""
echo "==> Packaging successful!"
echo "    App bundle: $dest_app"
echo "    Archive:    $archive"
echo ""
echo "To launch the app:"
echo "    open \"$dest_app\""

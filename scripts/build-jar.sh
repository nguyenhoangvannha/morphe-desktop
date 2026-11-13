#!/usr/bin/env bash
set -euo pipefail

project_dir=$(cd "$(dirname "$0")/.." && pwd)
cd "$project_dir"

fast=false
for arg in "$@"; do
    case "$arg" in
        --fast)
            fast=true
            ;;
        -h|--help)
            echo "Usage: $0 [--fast]"
            echo "  --fast    Skip ProGuard minification (runs shadowJar instead of assemble)"
            exit 0
            ;;
        *)
            echo "Unknown argument: $arg" >&2
            echo "Usage: $0 [--fast]" >&2
            exit 1
            ;;
    esac
done

check_is_jdk21() {
    local home="$1"
    if [[ -x "$home/bin/java" ]]; then
        local ver
        ver=$("$home/bin/java" -version 2>&1 | sed -E -n 's/.*version "([0-9]+).*/\1/p')
        if [[ -n "$ver" && "$ver" -ge 21 ]] 2>/dev/null; then
            return 0
        fi
    fi
    return 1
}

resolve_jdk21() {
    if [[ -n "${JAVA_HOME:-}" ]] && check_is_jdk21 "$JAVA_HOME"; then
        export JAVA_HOME
        return 0
    fi
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
    if /usr/libexec/java_home -v 21 >/dev/null 2>&1; then
        local jh
        jh="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
        if [[ -n "$jh" ]] && check_is_jdk21 "$jh"; then
            export JAVA_HOME="$jh"
            return 0
        fi
    fi
    for candidate in /Library/Java/JavaVirtualMachines/*/Contents/Home; do
        if check_is_jdk21 "$candidate"; then
            export JAVA_HOME="$candidate"
            return 0
        fi
    done
    return 0
}

resolve_jdk21

version=$(grep -E '^version\s*=' "$project_dir/gradle.properties" | cut -d= -f2 | tr -d '[:space:]')
if [[ -z "$version" ]]; then
    echo "Error: Could not determine version from gradle.properties" >&2
    exit 1
fi

echo "=================================================="
echo "  Building Morphe Desktop JAR (v$version)"
echo "=================================================="

if [[ "$fast" == true ]]; then
    echo "==> Running fast build without ProGuard (./gradlew shadowJar)..."
    ./gradlew shadowJar
else
    echo "==> Running production build with ProGuard (./gradlew assemble)..."
    ./gradlew assemble
fi

jar="$project_dir/build/libs/morphe-desktop-$version-all.jar"
if [[ ! -f "$jar" ]]; then
    # Fallback search if exact version file name differs
    jar=$(ls -t "$project_dir"/build/libs/morphe-desktop-*-all.jar 2>/dev/null | head -n 1)
fi

if [[ -z "$jar" || ! -f "$jar" ]]; then
    echo "Error: Output JAR was not found in build/libs/" >&2
    exit 1
fi

jar_size=$(du -h "$jar" | cut -f1 | tr -d '[:space:]')
echo ""
echo "==> Build successful!"
echo "    Output JAR: $jar ($jar_size)"
echo ""
echo "==> Verifying runnable JAR..."
java_cmd="java"
if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
    java_cmd="$JAVA_HOME/bin/java"
fi
"$java_cmd" -jar "$jar" --version
echo "==> JAR verification passed!"

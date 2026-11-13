#!/usr/bin/env bash
set -euo pipefail

project_dir=$(cd "$(dirname "$0")/.." && pwd)
output_dir="$project_dir/build/macos"

if [[ $(uname -s) != Darwin ]]; then
    echo "Error: DMG packaging is only supported on macOS." >&2
    exit 1
fi

app="$output_dir/Morphe.app"
if [[ ! -d "$app" ]]; then
    echo "==> Morphe.app not found. Packaging macOS app first..."
    bash "$project_dir/scripts/package-macos.sh"
fi

version="${1:-$(grep -E '^version\s*=' "$project_dir/gradle.properties" | cut -d= -f2 | tr -d '[:space:]')}"
case $(uname -m) in
    arm64) arch=arm64 ;;
    x86_64) arch=x64 ;;
    *) echo "Unsupported macOS architecture: $(uname -m)" >&2; exit 1 ;;
esac

dmg_path="$output_dir/morphe-desktop-$version-macos-$arch.dmg"
rm -f "$dmg_path"

echo "=================================================="
echo "  Creating Morphe Desktop DMG (v$version - $arch)"
echo "=================================================="

staging=$(mktemp -d "$output_dir/dmg-staging.XXXXXX")
trap 'rm -rf "$staging"' EXIT

cp -R "$app" "$staging/Morphe.app"
ln -s /Applications "$staging/Applications"

# Set volume icon if available
icon="$project_dir/src/main/resources/morphe_logo.icns"
if [[ -f "$icon" ]]; then
    cp "$icon" "$staging/.VolumeIcon.icns"
    if command -v SetFile >/dev/null 2>&1; then
        SetFile -a C "$staging" 2>/dev/null || true
    fi
fi

hdiutil create \
    -volname "Morphe Desktop" \
    -srcfolder "$staging" \
    -ov \
    -format UDZO \
    "$dmg_path"

echo ""
echo "==> DMG successfully created!"
echo "    DMG path: $dmg_path"
echo ""
echo "To mount and inspect:"
echo "    open \"$dmg_path\""

#!/bin/bash
# Build the release artifacts: the prebuilt Android binaries, a checksum list, and a source archive.
#
#   tools/make-release.sh --out DIR [--ndk DIR] [--shim FILE] [--write-lock]
#
#   --out DIR      where the artifacts go (outside the repository)
#   --ndk DIR      Android NDK r27c (default: $NDK, then ~/android-ndk-r27c)
#   --shim FILE    the tested Vulkan routing shim to publish. Without it the NDK build is used, which has the same
#                  interface but has not been run on a device (see gpu/README.md).
#   --write-lock   also write prebuilt.sha256 into the repository, the list of checksums the installer verifies against.
#                  Commit it WITH the release, so every checkout names exactly the assets it expects.
#
# The NDK-built binaries are reproducible: building them again in another directory gives identical bytes, so anyone
# can check a published asset by rebuilding it (installer/gbos-install.py prepare --ndk auto compares automatically).
#
# This script uploads nothing. It ends by printing the commands to do so.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT=""; NDK_DIR="${NDK:-$HOME/android-ndk-r27c}"; SHIM=""; WRITE=0
while [ $# -gt 0 ]; do
    case "$1" in
        --out) OUT="$2"; shift ;;
        --ndk) NDK_DIR="$2"; shift ;;
        --shim) SHIM="$2"; shift ;;
        --write-lock) WRITE=1 ;;
        -h|--help) sed -n '2,19p' "$0"; exit 0 ;;
        *) echo "unknown argument: $1" >&2; exit 2 ;;
    esac
    shift
done
[ -n "$OUT" ] || { echo "--out DIR is required" >&2; exit 2; }
case "$(cd "$ROOT" && realpath -m "$OUT")" in "$ROOT"|"$ROOT"/*) echo "--out must be outside the repository" >&2; exit 2 ;; esac
VERSION="$(cat "$ROOT/VERSION")"
WANT_NDK="27.2.12479018"                       # r27c: the builds are only reproducible with this exact toolchain

echo "== 1. scrub"
"$ROOT/tools/scrub.sh" || { echo "the tree is not clean; refusing to release" >&2; exit 1; }

echo "== 2. toolchain"
HAVE=$(sed -n 's/^Pkg.Revision *= *//p' "$NDK_DIR/source.properties" 2>/dev/null || true)
[ "$HAVE" = "$WANT_NDK" ] || { echo "NDK at $NDK_DIR is '${HAVE:-not found}', need $WANT_NDK (r27c)" >&2; exit 1; }

echo "== 3. build"
mkdir -p "$OUT/prebuilt"
make -s -C "$ROOT/native" OUT="$OUT/.build" NDK="$NDK_DIR" "$OUT/.build/input-bridge" "$OUT/.build/vpd-stub" "$OUT/.build/vulkan.hybrid64.so" 2>&1 | grep -v -E 'warning|extern|\^|^ *[0-9]+ \|' || true
cp "$OUT/.build/input-bridge" "$OUT/.build/vpd-stub" "$OUT/prebuilt/"
if [ -n "$SHIM" ]; then
    [ -f "$SHIM" ] || { echo "no such file: $SHIM" >&2; exit 1; }
    cp "$SHIM" "$OUT/prebuilt/vulkan.hybrid64.so"; SHIMNOTE="the tested binary (linked with host gcc)"
else
    cp "$OUT/.build/vulkan.hybrid64.so" "$OUT/prebuilt/"; SHIMNOTE="NDK build, not run on a device"
fi
rm -r "$OUT/.build"

echo "== 4. checksums"
( cd "$OUT/prebuilt" && sha256sum input-bridge vpd-stub vulkan.hybrid64.so ) > "$OUT/SHA256SUMS"
cat "$OUT/SHA256SUMS"
if [ "$WRITE" = 1 ]; then
    {
        echo "# Expected sha256 of the prebuilt release assets. Written by tools/make-release.sh; the installer checks downloads against it."
        echo "# Format: sha256  size  name  note"
        while read -r sum name; do
            note="NDK r27c build, reproducible"
            [ "$name" = vulkan.hybrid64.so ] && note="$SHIMNOTE"
            printf '%s  %s  %s  %s\n' "$sum" "$(stat -c %s "$OUT/prebuilt/$name")" "$name" "$note"
        done < "$OUT/SHA256SUMS"
    } > "$ROOT/prebuilt.sha256"
    echo "wrote $ROOT/prebuilt.sha256 (commit it with the release)"
fi

echo "== 5. source archive"
TAR="$OUT/gbos-$VERSION-src.tar.gz"
( cd "$ROOT" && tar --sort=name --owner=0 --group=0 --numeric-owner --mtime='2000-01-01 00:00:00' \
      --exclude='__pycache__' --exclude='*.pyc' --exclude='.git' \
      --transform "s,^\./,gbos-$VERSION/," -cf - . ) | gzip -n -9 > "$TAR"
( cd "$OUT" && sha256sum "$(basename "$TAR")" >> SHA256SUMS )
ls -l "$OUT" "$OUT/prebuilt" | awk '{print $5, $9}' | sed '/^ *$/d'

cat <<MSG

Done. To publish (nothing was uploaded):
  1. commit prebuilt.sha256 and tag:    git tag v$VERSION
  2. gh release create v$VERSION $TAR $OUT/prebuilt/* $OUT/SHA256SUMS --title "GBOS $VERSION" --notes-file RELEASE_NOTES.md
  3. users fetch the binaries with:     installer/gbos-install.py fetch-prebuilt --url https://github.com/<owner>/<repo>/releases/download/v$VERSION --dest DIR
MSG

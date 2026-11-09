#!/usr/bin/env bash
# Build a bsdiff delta package that turns OLD_APK into NEW_APK.
#
# usage: make-delta.sh OLD_APK NEW_APK OUT_DIR
#
# On success OUT_DIR contains update.patch and update.json (see HDeltaUpdater.kt).
# If the patch is not worth shipping (>= MAX_PERCENT of the full APK) nothing is
# left in OUT_DIR, so callers just check whether OUT_DIR/update.json exists.
set -euo pipefail

OLD_APK="$1"
NEW_APK="$2"
OUT_DIR="$3"
MAX_PERCENT="${MAX_PERCENT:-80}"

rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"

bsdiff "$OLD_APK" "$NEW_APK" "$OUT_DIR/update.patch"

old_sha=$(sha256sum "$OLD_APK" | cut -d' ' -f1)
new_sha=$(sha256sum "$NEW_APK" | cut -d' ' -f1)
new_size=$(stat -c %s "$NEW_APK")
patch_size=$(stat -c %s "$OUT_DIR/update.patch")

echo "old apk : $(stat -c %s "$OLD_APK") bytes ($old_sha)"
echo "new apk : $new_size bytes ($new_sha)"
echo "patch   : $patch_size bytes"

if [ "$old_sha" = "$new_sha" ]; then
  echo "APKs are identical, no delta needed."
  rm -rf "$OUT_DIR"; mkdir -p "$OUT_DIR"
  exit 0
fi

if [ $((patch_size * 100)) -ge $((new_size * MAX_PERCENT)) ]; then
  echo "Patch is not smaller than ${MAX_PERCENT}% of the full APK, skipping delta."
  rm -rf "$OUT_DIR"; mkdir -p "$OUT_DIR"
  exit 0
fi

jq -n \
  --arg baseSha256 "$old_sha" \
  --arg newSha256 "$new_sha" \
  --argjson newSize "$new_size" \
  --argjson patchSize "$patch_size" \
  '{format: "bsdiff40", baseSha256: $baseSha256, newSha256: $newSha256, newSize: $newSize, patchSize: $patchSize}' \
  > "$OUT_DIR/update.json"

echo "Delta package ready in $OUT_DIR"

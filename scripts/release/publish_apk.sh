#!/usr/bin/env bash
# Publish a signed APK for in-app updates:
#   1. upload it to the public Supabase Storage bucket "app-releases" (never overwrites),
#   2. register it in public.app_releases with its size and SHA-256.
# Needs a service-role/secret key: run only in CI (GitHub secret) or on a trusted machine.
#
# Usage: publish_apk.sh <apk> <versionCode> <versionName>
# Env:   SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY
#        MIN_SUPPORTED_VERSION_CODE (optional, default 1)
#        NOTES_EN_FILE / NOTES_CKB_FILE (optional release-note files)
set -euo pipefail

APK="$1"; CODE="$2"; NAME="$3"
: "${SUPABASE_URL:?}" "${SUPABASE_SERVICE_ROLE_KEY:?}"
URL="${SUPABASE_URL%/}"
MIN="${MIN_SUPPORTED_VERSION_CODE:-1}"
OBJECT="englishmaster-${NAME}-${CODE}.apk"

[[ "$CODE" =~ ^[0-9]+$ ]] || { echo "versionCode must be numeric" >&2; exit 1; }
[[ "$OBJECT" =~ ^[A-Za-z0-9._-]+\.apk$ ]] || { echo "unsafe versionName: $NAME" >&2; exit 1; }

SHA=$(sha256sum "$APK" | cut -d' ' -f1)
SIZE=$(stat -c %s "$APK")

# New-style secret keys (sb_secret_...) go in the apikey header only; legacy JWT keys also as Bearer.
AUTH=(-H "apikey: ${SUPABASE_SERVICE_ROLE_KEY}")
[[ "$SUPABASE_SERVICE_ROLE_KEY" == sb_secret_* ]] || AUTH+=(-H "Authorization: Bearer ${SUPABASE_SERVICE_ROLE_KEY}")

echo "Uploading $OBJECT ($SIZE bytes, sha256 $SHA)"
curl -sS --fail-with-body "${AUTH[@]}" \
  -H "Content-Type: application/vnd.android.package-archive" -H "x-upsert: false" \
  --data-binary "@$APK" "$URL/storage/v1/object/app-releases/$OBJECT" >/dev/null

notes() { [[ -n "${1:-}" && -f "$1" ]] && jq -Rs . < "$1" || echo null; }
BODY=$(jq -n --argjson code "$CODE" --arg name "$NAME" --arg path "$OBJECT" --arg sha "$SHA" \
  --argjson size "$SIZE" --argjson min "$MIN" \
  --argjson en "$(notes "${NOTES_EN_FILE:-}")" --argjson ckb "$(notes "${NOTES_CKB_FILE:-}")" \
  '{version_code:$code, version_name:$name, apk_path:$path, sha256:$sha, size_bytes:$size,
    min_supported_version_code:$min, notes_en:$en, notes_ckb:$ckb, published:true}')

echo "Registering release $NAME ($CODE)"
curl -sS --fail-with-body "${AUTH[@]}" -H "Content-Type: application/json" -H "Prefer: return=minimal" \
  -d "$BODY" "$URL/rest/v1/app_releases" >/dev/null
echo "Published: $URL/storage/v1/object/public/app-releases/$OBJECT"

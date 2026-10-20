#!/usr/bin/env bash
# Uploads content/media/** to the public 'question-media' bucket (same relative paths).
# Idempotent (x-upsert). Needs SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY.
set -euo pipefail
: "${SUPABASE_URL:?}" "${SUPABASE_SERVICE_ROLE_KEY:?}"
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
AUTH=(-H "apikey: $SUPABASE_SERVICE_ROLE_KEY")
[[ "$SUPABASE_SERVICE_ROLE_KEY" == sb_secret_* ]] || AUTH+=(-H "Authorization: Bearer $SUPABASE_SERVICE_ROLE_KEY")
cd "$ROOT/content/media"
count=0
while IFS= read -r -d '' f; do
  path=${f#./}
  case "$path" in
    *.png) type=image/png ;; *.jpg|*.jpeg) type=image/jpeg ;; *.webp) type=image/webp ;; *.mp4) type=video/mp4 ;;
    *) echo "skip $path"; continue ;;
  esac
  code=$(curl -sS -o /tmp/upload.out -w '%{http_code}' -X POST "${AUTH[@]}" -H "Content-Type: $type" -H "x-upsert: true" \
    -H "Cache-Control: max-age=86400" --data-binary "@$f" "${SUPABASE_URL%/}/storage/v1/object/question-media/$path")
  if [[ "$code" != 200 ]]; then echo "::error::upload of $path failed (HTTP $code): $(head -c 300 /tmp/upload.out)"; exit 1; fi
  echo "uploaded $path"; count=$((count + 1))
done < <(find . -type f -print0 | sort -z)
echo "$count files uploaded"

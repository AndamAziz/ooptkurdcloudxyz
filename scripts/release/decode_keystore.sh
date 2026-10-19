#!/usr/bin/env bash
# Writes the release keystore to $1 from CI secrets and checks password and alias.
# Preferred: ANDROID_KEYSTORE_ENCRYPTION_KEY decrypts the committed
#            android/release-signing/englishmaster-release.jks.enc (AES-256-CBC, PBKDF2 600k).
# Fallback:  ANDROID_KEYSTORE_BASE64 (whitespace ignored).
# Also needs ANDROID_KEYSTORE_PASSWORD and ANDROID_KEY_ALIAS. Prints ::error:: lines for CI.
set -uo pipefail
OUT="$1"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
ENC="$ROOT/android/release-signing/englishmaster-release.jks.enc"

if [ -n "${ANDROID_KEYSTORE_ENCRYPTION_KEY:-}" ]; then
  EK=$(printf '%s' "$ANDROID_KEYSTORE_ENCRYPTION_KEY" | tr -d ' \t\r\n')
  EK="$EK" openssl enc -d -aes-256-cbc -pbkdf2 -iter 600000 -md sha256 -in "$ENC" -out "$OUT" -pass env:EK 2>/dev/null \
    || { echo "::error::ANDROID_KEYSTORE_ENCRYPTION_KEY is wrong (could not decrypt the keystore)"; exit 1; }
elif [ -n "${ANDROID_KEYSTORE_BASE64:-}" ]; then
  CLEAN=$(printf '%s' "$ANDROID_KEYSTORE_BASE64" | tr -d ' \t\r\n')
  printf '%s' "$CLEAN" | base64 -d > "$OUT" 2>/dev/null \
    || { echo "::error::ANDROID_KEYSTORE_BASE64 is not valid base64 (${#CLEAN} characters after removing spaces)"; exit 1; }
else
  echo "::error::set ANDROID_KEYSTORE_ENCRYPTION_KEY (preferred) or ANDROID_KEYSTORE_BASE64"; exit 1
fi

PW=$(printf '%s' "${ANDROID_KEYSTORE_PASSWORD:-}" | tr -d '\r\n')
ALIAS=$(printf '%s' "${ANDROID_KEY_ALIAS:-}" | tr -d ' \t\r\n')
keytool -list -keystore "$OUT" -storepass "$PW" > /dev/null 2>&1 \
  || { echo "::error::ANDROID_KEYSTORE_PASSWORD is wrong (keystore could not be opened)"; exit 1; }
keytool -list -keystore "$OUT" -storepass "$PW" -alias "$ALIAS" > /dev/null 2>&1 \
  || { echo "::error::ANDROID_KEY_ALIAS is wrong (expected englishmaster)"; exit 1; }
echo "Release keystore OK"

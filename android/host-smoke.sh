#!/usr/bin/env bash
set -euo pipefail

# Reproducible no-device validation for the two-app Glass3 project. It does
# not need AK/SK or a connected device; those remain field-only checks.
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

if [[ -z "${JAVA_HOME:-}" && -x "/opt/homebrew/opt/openjdk@17/bin/java" ]]; then
  export JAVA_HOME="/opt/homebrew/opt/openjdk@17"
fi

echo "== shell syntax =="
bash -n device-preflight.sh field-install.sh field-diagnostics.sh field-commission.sh field-script-smoke.sh release-check.sh package-release.sh sync-candidate.sh host-smoke.sh
grep -Fq 'abortBroadcast()' glass-app/src/main/java/com/inspection/demo/glass/MainActivity.kt
! rg -q 'Manifest\.permission\.BLUETOOTH_ADVERTISE' phone-app/src/main/java/com/inspection/demo/phone/MainActivity.kt

echo "== environment and artifact preflight =="
./device-preflight.sh

echo "== Gradle tests, lint and APKs =="
./gradlew \
  :protocol:test \
  :phone-app:test \
  :phone-app:lint \
  :glass-app:lint \
  assembleDebug \
  :phone-app:assembleRelease \
  :glass-app:assembleRelease \
  --console=plain

echo "== post-build artifact preflight =="
./device-preflight.sh

echo "== offline field script smoke =="
./field-script-smoke.sh

echo "== Release signature check =="
./release-check.sh

echo "== APK hashes =="
hash_file() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1"
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$1"
  else
    echo "找不到 sha256sum 或 shasum，无法输出 APK 哈希" >&2
    return 1
  fi
}
hash_file phone-app/build/outputs/apk/debug/phone-app-debug.apk
hash_file glass-app/build/outputs/apk/debug/glass-app-debug.apk
release_apk_path() {
  local module="$1"
  local release_dir="$ROOT_DIR/$module/build/outputs/apk/release"
  local signed="$release_dir/$module-release.apk"
  local unsigned="$release_dir/$module-release-unsigned.apk"
  if [[ -s "$signed" ]]; then
    printf '%s\n' "$signed"
  else
    printf '%s\n' "$unsigned"
  fi
}
hash_file "$(release_apk_path phone-app)"
hash_file "$(release_apk_path glass-app)"

echo "== Candidate bundle =="
./sync-candidate.sh
CANDIDATE_DIR="$ROOT_DIR/../artifacts/rokid-rc-20261106"
if command -v sha256sum >/dev/null 2>&1; then
  (cd "$CANDIDATE_DIR" && sha256sum -c SHA256SUMS)
else
  (cd "$CANDIDATE_DIR" && shasum -a 256 -c SHA256SUMS)
fi
grep -Fq "# inspection-demo Rokid Glass3 Enterprise 候选包" "$CANDIDATE_DIR/README.md"
test -s "$CANDIDATE_DIR/docs/FIELD_HANDOFF.md"
test -s "$CANDIDATE_DIR/docs/local.properties.example"
test -s "$CANDIDATE_DIR/docs/LEADERSHIP_STATUS.md"
test -x "$CANDIDATE_DIR/docs/field-commission.sh"
if rg -q '/Users/|/opt/homebrew|/inspection-demo/artifacts' "$CANDIDATE_DIR/README.md" "$CANDIDATE_DIR/docs" --glob '*.md'; then
  echo "候选现场文档不应包含开发机绝对路径" >&2
  exit 1
fi

echo "HOST SMOKE PASSED"

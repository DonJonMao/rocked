#!/usr/bin/env bash
set -euo pipefail

# Offline regression for the field-facing shell scripts. It uses a tiny fake
# adb transport and real APKs, so it verifies serial routing, install safety,
# signature gating and diagnostic evidence without touching a device.
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [[ -d "$ROOT_DIR/phone-app" ]]; then
  PROJECT_ROOT="$ROOT_DIR"
else
  PROJECT_ROOT="$(cd "$ROOT_DIR/.." && pwd)"
fi
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/inspection-field-smoke.XXXXXX")"
cleanup() {
  find "$TMP_DIR" -type f -delete 2>/dev/null || true
  rmdir "$TMP_DIR" 2>/dev/null || true
}
trap cleanup EXIT

PHONE_APK="$PROJECT_ROOT/phone-app/build/outputs/apk/debug/phone-app-debug.apk"
GLASS_APK="$PROJECT_ROOT/glass-app/build/outputs/apk/debug/glass-app-debug.apk"
if [[ -s "$PROJECT_ROOT/apk/phone-app-debug.apk" ]]; then PHONE_APK="$PROJECT_ROOT/apk/phone-app-debug.apk"; fi
if [[ -s "$PROJECT_ROOT/apk/glass-app-debug.apk" ]]; then GLASS_APK="$PROJECT_ROOT/apk/glass-app-debug.apk"; fi
[[ -s "$PHONE_APK" ]] || { echo "缺少手机 Debug APK，请先构建" >&2; exit 1; }
[[ -s "$GLASS_APK" ]] || { echo "缺少眼镜 Debug APK，请先构建" >&2; exit 1; }

# A candidate bundle may be handed off before Android build-tools are
# installed. The full regression runs in the source checkout (and on any
# field laptop with aapt); the candidate reports an explicit skip instead of
# disguising a missing tool as an application failure.
aapt_available=0
if command -v aapt >/dev/null 2>&1; then
  aapt_available=1
else
  sdk_dir="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
  if [[ -z "$sdk_dir" && -f "$PROJECT_ROOT/local.properties" ]]; then
    sdk_dir="$(awk -F= '$1 == "sdk.dir" {sub(/^[[:space:]]+/, "", $2); print $2; exit}' "$PROJECT_ROOT/local.properties")"
  fi
  for candidate in "$sdk_dir"/build-tools/*/aapt; do
    if [[ -x "$candidate" ]]; then
      aapt_available=1
      break
    fi
  done
fi
if [[ "$aapt_available" == 0 ]]; then
  echo "FIELD SCRIPT SMOKE SKIPPED（缺少 aapt/build-tools；安装 Android SDK 后重试）"
  exit 0
fi

ADB_LOG="$TMP_DIR/adb.log"
export ADB_LOG
cat >"$TMP_DIR/fake-adb" <<'EOF'
#!/usr/bin/env bash
set -u
if [[ "$1" == "version" ]]; then
  echo "Android Debug Bridge version 1.0.41-fake"
  exit 0
fi
if [[ "$1" == "start-server" ]]; then exit 0; fi
if [[ "$1" == "devices" ]]; then
  printf 'List of devices attached\nphone-smoke\tdevice\nglass-smoke\tdevice\n'
  exit 0
fi
if [[ "$1" != "-s" || $# -lt 3 ]]; then exit 2; fi
serial="$2"
shift 2
case "$1" in
  get-state)
    echo device
    ;;
  install)
    apk="${!#}"
    printf '%s|install|%s\n' "$serial" "$apk" >>"$ADB_LOG"
    echo Success
    ;;
  shell)
    shift
    if [[ "${1:-}" == "pm" && "${2:-}" == "path" ]]; then
      echo "package:/data/app/fake/base.apk"
    elif [[ "${1:-}" == "getprop" ]]; then
      echo "fake-${serial}"
    else
      echo "fake-${serial}-${*}"
    fi
    ;;
  logcat)
    echo "11-06 12:00:00.000 I inspection: fake field smoke"
    ;;
  *)
    echo "unsupported fake adb call: $*" >&2
    exit 2
    ;;
esac
EOF
chmod +x "$TMP_DIR/fake-adb"
ln -s "$TMP_DIR/fake-adb" "$TMP_DIR/adb"

echo "== explicit device-role preflight =="
PATH="$TMP_DIR:$PATH" "$ROOT_DIR/device-preflight.sh" \
  --phone phone-smoke \
  --glass glass-smoke >/dev/null
set +e
PATH="$TMP_DIR:$PATH" "$ROOT_DIR/device-preflight.sh" --phone phone-smoke >/dev/null 2>&1
role_status=$?
set -e
[[ "$role_status" -eq 2 ]] || { echo "未拒绝只指定一台设备的预检参数" >&2; exit 1; }

echo "== debug serial routing =="
"$ROOT_DIR/field-install.sh" \
  --adb "$TMP_DIR/fake-adb" \
  --phone phone-smoke \
  --glass glass-smoke >/dev/null
grep -F "phone-smoke|install|$PHONE_APK" "$ADB_LOG" >/dev/null
grep -F "glass-smoke|install|$GLASS_APK" "$ADB_LOG" >/dev/null
if grep -F "phone-smoke|install|$GLASS_APK" "$ADB_LOG" >/dev/null || \
   grep -F "glass-smoke|install|$PHONE_APK" "$ADB_LOG" >/dev/null; then
  echo "APK 被路由到错误设备" >&2
  exit 1
fi

echo "== one-command field commissioning =="
commission_dir="$TMP_DIR/commission"
"$ROOT_DIR/field-commission.sh" \
  --adb "$TMP_DIR/fake-adb" \
  --phone phone-smoke \
  --glass glass-smoke \
  --variant debug \
  --out "$commission_dir" >/dev/null
[[ -s "$commission_dir/artifacts.txt" ]] || { echo "一键现场启用缺少诊断证据" >&2; exit 1; }
[[ -s "$commission_dir/README.txt" ]] || { echo "一键现场启用缺少诊断说明" >&2; exit 1; }

echo "== release signature gate =="
install_count_before="$(wc -l <"$ADB_LOG" | tr -d ' ')"
set +e
"$ROOT_DIR/field-install.sh" \
  --adb "$TMP_DIR/fake-adb" \
  --variant release \
  --phone phone-smoke \
  --glass glass-smoke >/dev/null 2>&1
release_status=$?
set -e
[[ "$release_status" -ne 0 ]] || { echo "unsigned Release 未被拦截" >&2; exit 1; }
install_count_after="$(wc -l <"$ADB_LOG" | tr -d ' ')"
[[ "$install_count_before" == "$install_count_after" ]] || {
  echo "Release 签名失败后仍执行了安装" >&2
  exit 1
}

echo "== diagnostics evidence =="
diagnostics_dir="$TMP_DIR/diagnostics"
"$ROOT_DIR/field-diagnostics.sh" \
  --adb "$TMP_DIR/fake-adb" \
  --phone phone-smoke \
  --glass glass-smoke \
  --out "$diagnostics_dir" >/dev/null
[[ -s "$diagnostics_dir/artifacts.txt" ]] || { echo "缺少 artifacts.txt" >&2; exit 1; }
grep -F "Android Debug Bridge version 1.0.41-fake" "$diagnostics_dir/artifacts.txt" >/dev/null
grep -F "${PHONE_APK}" "$diagnostics_dir/artifacts.txt" >/dev/null
grep -F "${GLASS_APK}" "$diagnostics_dir/artifacts.txt" >/dev/null

echo "FIELD SCRIPT SMOKE PASSED"

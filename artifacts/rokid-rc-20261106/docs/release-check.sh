#!/usr/bin/env bash
set -u

# Check whether the already-built Release APKs carry a verifiable signer.
# This script never prints passwords or reads their values to stdout.
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [[ -d "$ROOT_DIR/phone-app" ]]; then
  PROJECT_ROOT="$ROOT_DIR"
else
  PROJECT_ROOT="$(cd "$ROOT_DIR/.." && pwd)"
fi
REQUIRE_SIGNED=0
if [[ "${1:-}" == "--require-signed" ]]; then
  REQUIRE_SIGNED=1
elif [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  cat <<'EOF'
用法：
  ./release-check.sh             检查 Release APK；unsigned 时给出提醒
  ./release-check.sh --require-signed
                                  要求两个 APK 都能通过 apksigner 验证

签名配置来自 local.properties 的四项 android.signing.*；脚本不会输出密码。
EOF
  exit 0
elif [[ $# -gt 0 ]]; then
  echo "未知参数：$1" >&2
  exit 2
fi

pass() { printf 'PASS  %s\n' "$1"; }
warn() { printf 'WARN  %s\n' "$1"; }
fail() { printf 'FAIL  %s\n' "$1"; }

sdk_dir=""
if [[ -f "$PROJECT_ROOT/local.properties" ]]; then
  sdk_dir="$(awk -F= '$1 == "sdk.dir" {sub(/^[[:space:]]+/, "", $2); print $2; exit}' "$PROJECT_ROOT/local.properties")"
fi
sdk_dir="${sdk_dir:-${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}}"
apksigner=""
if command -v apksigner >/dev/null 2>&1; then
  apksigner="$(command -v apksigner)"
elif [[ -n "$sdk_dir" && -d "$sdk_dir/build-tools" ]]; then
  for candidate in "$sdk_dir"/build-tools/*/apksigner; do
    [[ -x "$candidate" ]] && apksigner="$candidate"
  done
fi
if [[ -z "$apksigner" ]]; then
  fail "找不到 apksigner；请安装 Android build-tools"
  exit 1
fi

release_apk_path() {
  local module="$1"
  local release_dir="$PROJECT_ROOT/$module/build/outputs/apk/release"
  local signed="$release_dir/$module-release.apk"
  local unsigned="$release_dir/$module-release-unsigned.apk"
  if [[ -s "$PROJECT_ROOT/apk/$module-release.apk" || -s "$PROJECT_ROOT/apk/$module-release-unsigned.apk" ]]; then
    signed="$PROJECT_ROOT/apk/$module-release.apk"
    unsigned="$PROJECT_ROOT/apk/$module-release-unsigned.apk"
  fi
  # AGP emits the signed variant without the -unsigned suffix. Prefer it
  # whenever enterprise signing is configured and the artifact exists.
  if [[ -s "$signed" ]]; then
    printf '%s\n' "$signed"
  else
    printf '%s\n' "$unsigned"
  fi
}

apk_paths=(
  "$(release_apk_path phone-app)"
  "$(release_apk_path glass-app)"
)
failures=0
for apk in "${apk_paths[@]}"; do
  if [[ ! -s "$apk" ]]; then
    if [[ "$REQUIRE_SIGNED" == 1 ]]; then
      fail "缺少 Release APK：$(basename "$apk")"
      failures=$((failures + 1))
    else
      warn "缺少 Release APK：$(basename "$apk")"
    fi
    continue
  fi
  if "$apksigner" verify --verbose "$apk" >/dev/null 2>&1; then
    pass "签名可验证：$(basename "$apk")"
  elif [[ "$REQUIRE_SIGNED" == 1 ]]; then
    fail "签名不可验证或 APK 为 unsigned：$(basename "$apk")"
    failures=$((failures + 1))
  else
    warn "APK 尚未签名：$(basename "$apk")"
  fi
done

if [[ "$REQUIRE_SIGNED" == 1 && "$failures" -gt 0 ]]; then
  exit 1
fi
if [[ "$REQUIRE_SIGNED" == 1 ]]; then
  echo "RELEASE SIGNATURE CHECK PASSED"
else
  echo "RELEASE SIGNATURE CHECK COMPLETED"
fi

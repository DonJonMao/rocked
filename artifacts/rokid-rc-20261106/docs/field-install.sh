#!/usr/bin/env bash
set -euo pipefail

# Install the two APKs on explicitly selected field devices. This script is
# intentionally non-destructive: it uses adb install -r and never clears app
# data, so a paused task and a pending photo queue remain available.
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [[ -d "$ROOT_DIR/phone-app" ]]; then
  PROJECT_ROOT="$ROOT_DIR"
else
  PROJECT_ROOT="$(cd "$ROOT_DIR/.." && pwd)"
fi
PHONE_SERIAL=""
GLASS_SERIAL=""
ADB_BIN=""
AAPT_BIN=""
APKSIGNER_BIN=""
VARIANT="debug"

usage() {
  cat <<'EOF'
用法：
  ./field-install.sh --phone SERIAL --glass SERIAL [--variant debug|release]

说明：
  - SERIAL 来自 `adb devices`；必须显式指定，避免把眼镜 APK 装到手机或反过来。
  - 默认安装 Debug APK；企业签名后可指定 `--variant release`，脚本会先用 apksigner 校验两个 Release APK。
  - 脚本只执行 `adb install -r`，不会清数据或卸载应用。
  - 源码工程可在 local.properties 配置 sdk.dir；候选交付包可用 ANDROID_SDK_ROOT/PATH 提供 adb、aapt，并准备好所选变体 APK。
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --phone)
      [[ $# -ge 2 ]] || { echo "缺少 --phone 的序列号" >&2; usage >&2; exit 2; }
      PHONE_SERIAL="$2"
      shift 2
      ;;
    --glass)
      [[ $# -ge 2 ]] || { echo "缺少 --glass 的序列号" >&2; usage >&2; exit 2; }
      GLASS_SERIAL="$2"
      shift 2
      ;;
    --adb)
      [[ $# -ge 2 ]] || { echo "缺少 --adb 的路径" >&2; usage >&2; exit 2; }
      ADB_BIN="$2"
      shift 2
      ;;
    --variant)
      [[ $# -ge 2 ]] || { echo "缺少 --variant 的值" >&2; usage >&2; exit 2; }
      case "$2" in
        debug|release) VARIANT="$2" ;;
        *) echo "--variant 只能是 debug 或 release：$2" >&2; usage >&2; exit 2 ;;
      esac
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "未知参数：$1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ -z "$PHONE_SERIAL" || -z "$GLASS_SERIAL" ]]; then
  echo "必须同时指定 --phone 和 --glass；先执行 adb devices 查看序列号。" >&2
  usage >&2
  exit 2
fi
if [[ "$PHONE_SERIAL" == "$GLASS_SERIAL" ]]; then
  echo "--phone 和 --glass 不能使用同一个序列号。" >&2
  exit 2
fi

if [[ -z "$ADB_BIN" ]]; then
  if command -v adb >/dev/null 2>&1; then
    ADB_BIN="$(command -v adb)"
  else
    sdk_dir=""
    if [[ -f "$PROJECT_ROOT/local.properties" ]]; then
      sdk_dir="$(awk -F= '$1 == "sdk.dir" {sub(/^[[:space:]]+/, "", $2); print $2; exit}' "$PROJECT_ROOT/local.properties")"
    fi
    sdk_dir="${sdk_dir:-${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}}"
    if [[ -x "$sdk_dir/platform-tools/adb" ]]; then
      ADB_BIN="$sdk_dir/platform-tools/adb"
    fi
  fi
fi
if [[ -z "$ADB_BIN" || ! -x "$ADB_BIN" ]]; then
  echo "找不到可执行的 adb；请设置 PATH、local.properties 的 sdk.dir 或传入 --adb。" >&2
  exit 1
fi

if command -v aapt >/dev/null 2>&1; then
  AAPT_BIN="$(command -v aapt)"
else
  sdk_dir=""
  if [[ -f "$PROJECT_ROOT/local.properties" ]]; then
    sdk_dir="$(awk -F= '$1 == "sdk.dir" {sub(/^[[:space:]]+/, "", $2); print $2; exit}' "$PROJECT_ROOT/local.properties")"
  fi
  sdk_dir="${sdk_dir:-${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}}"
  for candidate in "$sdk_dir"/build-tools/*/aapt; do
    [[ -x "$candidate" ]] && AAPT_BIN="$candidate"
  done
fi
if [[ -z "$AAPT_BIN" || ! -x "$AAPT_BIN" ]]; then
  echo "找不到 aapt；无法从 APK 读取企业包名，请安装 Android build-tools。" >&2
  exit 1
fi

if [[ "$VARIANT" == "release" ]]; then
  if command -v apksigner >/dev/null 2>&1; then
    APKSIGNER_BIN="$(command -v apksigner)"
  else
    sdk_dir=""
    if [[ -f "$PROJECT_ROOT/local.properties" ]]; then
      sdk_dir="$(awk -F= '$1 == "sdk.dir" {sub(/^[[:space:]]+/, "", $2); print $2; exit}' "$PROJECT_ROOT/local.properties")"
    fi
    if [[ -z "$sdk_dir" ]]; then sdk_dir="$(printenv ANDROID_SDK_ROOT || true)"; fi
    if [[ -z "$sdk_dir" ]]; then sdk_dir="$(printenv ANDROID_HOME || true)"; fi
    for candidate in "$sdk_dir"/build-tools/*/apksigner; do
      [[ -x "$candidate" ]] && APKSIGNER_BIN="$candidate"
    done
  fi
  if [[ -z "$APKSIGNER_BIN" || ! -x "$APKSIGNER_BIN" ]]; then
    echo "找不到 apksigner；Release 安装前必须校验签名。" >&2
    exit 1
  fi
fi

if ! "$ADB_BIN" start-server >/dev/null 2>&1; then
  echo "adb 服务不可用；请确认设备已授权并出现在 adb devices。" >&2
  exit 1
fi

device_state() {
  local serial="$1"
  "$ADB_BIN" -s "$serial" get-state 2>/dev/null || true
}

for serial in "$PHONE_SERIAL" "$GLASS_SERIAL"; do
  if [[ "$(device_state "$serial")" != "device" ]]; then
    echo "设备不可用或未授权：$serial" >&2
    "$ADB_BIN" devices >&2 || true
    exit 1
  fi
done

phone_model="$($ADB_BIN -s "$PHONE_SERIAL" shell getprop ro.product.model | tr -d '\r' || true)"
glass_model="$($ADB_BIN -s "$GLASS_SERIAL" shell getprop ro.product.model | tr -d '\r' || true)"
if [[ "$VARIANT" == "debug" ]]; then
  phone_apk="$PROJECT_ROOT/phone-app/build/outputs/apk/debug/phone-app-debug.apk"
  glass_apk="$PROJECT_ROOT/glass-app/build/outputs/apk/debug/glass-app-debug.apk"
  if [[ -s "$PROJECT_ROOT/apk/phone-app-debug.apk" ]]; then phone_apk="$PROJECT_ROOT/apk/phone-app-debug.apk"; fi
  if [[ -s "$PROJECT_ROOT/apk/glass-app-debug.apk" ]]; then glass_apk="$PROJECT_ROOT/apk/glass-app-debug.apk"; fi
else
  phone_apk="$PROJECT_ROOT/phone-app/build/outputs/apk/release/phone-app-release.apk"
  glass_apk="$PROJECT_ROOT/glass-app/build/outputs/apk/release/glass-app-release.apk"
  if [[ -s "$PROJECT_ROOT/apk/phone-app-release.apk" ]]; then phone_apk="$PROJECT_ROOT/apk/phone-app-release.apk"; fi
  if [[ -s "$PROJECT_ROOT/apk/glass-app-release.apk" ]]; then glass_apk="$PROJECT_ROOT/apk/glass-app-release.apk"; fi
fi
echo "安装变体：$VARIANT"
[[ -s "$phone_apk" ]] || { echo "缺少手机 APK：$phone_apk" >&2; exit 1; }
[[ -s "$glass_apk" ]] || { echo "缺少眼镜 APK：$glass_apk" >&2; exit 1; }
if [[ "$VARIANT" == "release" ]]; then
  if ! "$APKSIGNER_BIN" verify --verbose "$phone_apk" >/dev/null 2>&1; then
    echo "手机 Release APK 未通过签名校验：$phone_apk" >&2
    exit 1
  fi
  if ! "$APKSIGNER_BIN" verify --verbose "$glass_apk" >/dev/null 2>&1; then
    echo "眼镜 Release APK 未通过签名校验：$glass_apk" >&2
    exit 1
  fi
  echo "Release APK 签名校验通过。"
fi
phone_package="$("$AAPT_BIN" dump badging "$phone_apk" | awk -F"'" '/^package: name=/{print $2; exit}')"
glass_package="$("$AAPT_BIN" dump badging "$glass_apk" | awk -F"'" '/^package: name=/{print $2; exit}')"
[[ -n "$phone_package" ]] || { echo "无法从手机 APK 读取包名" >&2; exit 1; }
[[ -n "$glass_package" ]] || { echo "无法从眼镜 APK 读取包名" >&2; exit 1; }
if [[ "$phone_package" == "$glass_package" ]]; then
  echo "手机和眼镜 APK 的包名不能相同：$phone_package" >&2
  exit 1
fi

# In the source checkout local.properties is the authoritative record of the
# enterprise-registered application IDs. A candidate bundle intentionally
# omits that file, so its APK manifest remains the source of truth there.
property_value() {
  local key="$1"
  [[ -f "$PROJECT_ROOT/local.properties" ]] || return 0
  awk -F= -v wanted="$key" '$1 == wanted {sub(/^[[:space:]]+/, "", $2); print $2; exit}' "$PROJECT_ROOT/local.properties"
}
expected_phone="$(property_value android.phoneApplicationId)"
expected_glass="$(property_value android.glassApplicationId)"
expected_phone="${expected_phone:-com.inspection.demo.phone}"
expected_glass="${expected_glass:-com.inspection.demo.glass}"
if [[ -f "$PROJECT_ROOT/local.properties" && "$phone_package" != "$expected_phone" ]]; then
  echo "手机 APK 包名与 local.properties 不一致：期望 $expected_phone，实际 $phone_package" >&2
  exit 1
fi
if [[ -f "$PROJECT_ROOT/local.properties" && "$glass_package" != "$expected_glass" ]]; then
  echo "眼镜 APK 包名与 local.properties 不一致：期望 $expected_glass，实际 $glass_package" >&2
  exit 1
fi

echo "手机：${PHONE_SERIAL}（${phone_model:-未知型号}）"
echo "眼镜：${GLASS_SERIAL}（${glass_model:-未知型号}）"
echo "手机包名：$phone_package"
echo "眼镜包名：$glass_package"
echo "安装手机 APK…"
"$ADB_BIN" -s "$PHONE_SERIAL" install -r "$phone_apk"
echo "安装眼镜 APK…"
"$ADB_BIN" -s "$GLASS_SERIAL" install -r "$glass_apk"

for pair in \
  "$PHONE_SERIAL $phone_package" \
  "$GLASS_SERIAL $glass_package"; do
  serial="${pair%% *}"
  package_name="${pair#* }"
  package_path="$("$ADB_BIN" -s "$serial" shell pm path "$package_name" 2>/dev/null | tr -d '\r' || true)"
  if [[ "$package_path" != package:* ]]; then
    echo "安装核对失败：$package_name @ $serial" >&2
    exit 1
  fi
  echo "已核对：$package_name @ $serial"
done

echo "现场安装完成（${VARIANT}）；未清除应用数据。接下来按 android/SMOKE_TEST.md 执行初始化、配对和最小链路。"

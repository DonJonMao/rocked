#!/usr/bin/env bash
set -euo pipefail

# Collect non-destructive diagnostics from the phone and Glass3. The report
# intentionally never reads local.properties or prints enterprise secrets.
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [[ -d "$ROOT_DIR/phone-app" ]]; then
  PROJECT_ROOT="$ROOT_DIR"
else
  PROJECT_ROOT="$(cd "$ROOT_DIR/.." && pwd)"
fi
PHONE_SERIAL=""
GLASS_SERIAL=""
ADB_BIN=""
OUT_DIR=""

usage() {
  cat <<'EOF'
用法：
  ./field-diagnostics.sh --phone SERIAL --glass SERIAL [--out DIR]

说明：
  - SERIAL 来自 `adb devices`；必须显式指定手机和眼镜。
  - 只读取设备信息、应用安装状态、权限、蓝牙/Wi-Fi/P2P 摘要和最近日志。
  - 不会安装、卸载、清数据、修改设置，也不会读取 local.properties 的凭证值。
  - 默认把报告写入上级 artifacts/field-diagnostics-时间戳/。
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
    --out)
      [[ $# -ge 2 ]] || { echo "缺少 --out 的目录" >&2; usage >&2; exit 2; }
      OUT_DIR="$2"
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
    if [[ -z "$sdk_dir" ]]; then sdk_dir="$(printenv ANDROID_SDK_ROOT || true)"; fi
    if [[ -z "$sdk_dir" ]]; then sdk_dir="$(printenv ANDROID_HOME || true)"; fi
    if [[ -x "$sdk_dir/platform-tools/adb" ]]; then
      ADB_BIN="$sdk_dir/platform-tools/adb"
    fi
  fi
fi
if [[ -z "$ADB_BIN" || ! -x "$ADB_BIN" ]]; then
  echo "找不到可执行的 adb；请设置 PATH、local.properties 的 sdk.dir 或传入 --adb。" >&2
  exit 1
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

if [[ -z "$OUT_DIR" ]]; then
  OUT_DIR="$ROOT_DIR/../artifacts/field-diagnostics-$(date +%Y%m%d-%H%M%S)"
fi
umask 077
mkdir -p "$OUT_DIR"

package_from_apk() {
  local apk="$1"
  local sdk_dir=""
  local aapt=""
  if [[ -f "$PROJECT_ROOT/local.properties" ]]; then
    sdk_dir="$(awk -F= '$1 == "sdk.dir" {sub(/^[[:space:]]+/, "", $2); print $2; exit}' "$PROJECT_ROOT/local.properties")"
  fi
  sdk_dir="${sdk_dir:-${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}}"
  if command -v aapt >/dev/null 2>&1; then
    aapt="$(command -v aapt)"
  else
    for candidate in "$sdk_dir"/build-tools/*/aapt; do
      [[ -x "$candidate" ]] && aapt="$candidate"
    done
  fi
  if [[ -n "$aapt" && -s "$apk" ]]; then
    "$aapt" dump badging "$apk" | awk -F"'" '/^package: name=/{print $2; exit}'
  fi
}

phone_apk="$PROJECT_ROOT/phone-app/build/outputs/apk/debug/phone-app-debug.apk"
glass_apk="$PROJECT_ROOT/glass-app/build/outputs/apk/debug/glass-app-debug.apk"
if [[ -s "$PROJECT_ROOT/apk/phone-app-debug.apk" ]]; then phone_apk="$PROJECT_ROOT/apk/phone-app-debug.apk"
elif [[ -s "$PROJECT_ROOT/apk/phone-app-release.apk" ]]; then phone_apk="$PROJECT_ROOT/apk/phone-app-release.apk"
elif [[ -s "$PROJECT_ROOT/apk/phone-app-release-unsigned.apk" ]]; then phone_apk="$PROJECT_ROOT/apk/phone-app-release-unsigned.apk"
fi
if [[ -s "$PROJECT_ROOT/apk/glass-app-debug.apk" ]]; then glass_apk="$PROJECT_ROOT/apk/glass-app-debug.apk"
elif [[ -s "$PROJECT_ROOT/apk/glass-app-release.apk" ]]; then glass_apk="$PROJECT_ROOT/apk/glass-app-release.apk"
elif [[ -s "$PROJECT_ROOT/apk/glass-app-release-unsigned.apk" ]]; then glass_apk="$PROJECT_ROOT/apk/glass-app-release-unsigned.apk"
fi
phone_package="$(package_from_apk "$phone_apk" || true)"
glass_package="$(package_from_apk "$glass_apk" || true)"
phone_package="${phone_package:-com.inspection.demo.phone}"
glass_package="${glass_package:-com.inspection.demo.glass}"

capture() {
  local title="$1"
  shift
  {
    echo "### $title"
    if ! "$@" 2>&1; then
      echo "[命令失败：$*]"
    fi
    echo
  }
}

collect_device() {
  local label="$1"
  local serial="$2"
  local package_name="$3"
  local prefix="$OUT_DIR/$label"

  {
    echo "设备角色：$label"
    echo "adb 序列号：$serial"
    echo "应用包名：$package_name"
    echo
    capture "基础属性" "$ADB_BIN" -s "$serial" shell getprop ro.product.manufacturer
    capture "型号" "$ADB_BIN" -s "$serial" shell getprop ro.product.model
    capture "产品名" "$ADB_BIN" -s "$serial" shell getprop ro.product.name
    capture "Android 版本" "$ADB_BIN" -s "$serial" shell getprop ro.build.version.release
    capture "API 级别" "$ADB_BIN" -s "$serial" shell getprop ro.build.version.sdk
    capture "固件构建" "$ADB_BIN" -s "$serial" shell getprop ro.build.display.id
    capture "增量版本" "$ADB_BIN" -s "$serial" shell getprop ro.build.version.incremental
    capture "应用安装路径" "$ADB_BIN" -s "$serial" shell pm path "$package_name"
    capture "应用版本与权限摘要" "$ADB_BIN" -s "$serial" shell dumpsys package "$package_name"
    capture "蓝牙摘要" "$ADB_BIN" -s "$serial" shell dumpsys bluetooth_manager
    capture "Wi-Fi 摘要" "$ADB_BIN" -s "$serial" shell dumpsys wifi
    capture "Wi-Fi P2P 摘要" "$ADB_BIN" -s "$serial" shell dumpsys wifi_p2p
  } >"$prefix-summary.txt"

  if ! "$ADB_BIN" -s "$serial" logcat -d -t 300 -v threadtime -s \
      Rokid:* inspection:* GlassPhotoCapture:* '*:S' >"$prefix-logcat.txt" 2>&1; then
    echo "logcat 读取失败" >"$prefix-logcat.txt"
  fi
}

collect_device phone "$PHONE_SERIAL" "$phone_package"
collect_device glass "$GLASS_SERIAL" "$glass_package"

hash_file() {
  local file="$1"
  if [[ ! -s "$file" ]]; then
    printf 'missing\t%s\n' "$file"
  elif command -v sha256sum >/dev/null 2>&1; then
    printf '%s\t%s\n' "$(sha256sum "$file" | awk '{print $1}')" "$file"
  elif command -v shasum >/dev/null 2>&1; then
    printf '%s\t%s\n' "$(shasum -a 256 "$file" | awk '{print $1}')" "$file"
  else
    printf 'hash-tool-missing\t%s\n' "$file"
  fi
}

{
  echo "诊断报告版本：1"
  echo "采集时间：$(date '+%Y-%m-%d %H:%M:%S %z')"
  echo "adb 路径：$ADB_BIN"
  echo "adb 版本："
  "$ADB_BIN" version 2>&1 || true
  echo
  echo "APK SHA-256（本机待安装产物）："
  hash_file "$phone_apk"
  hash_file "$glass_apk"
  echo
  echo "包名："
  printf 'phone\t%s\n' "$phone_package"
  printf 'glass\t%s\n' "$glass_package"
} >"$OUT_DIR/artifacts.txt"

cat >"$OUT_DIR/README.txt" <<EOF
巡检 Android 双设备诊断报告
生成时间：$(date '+%Y-%m-%d %H:%M:%S %z')
手机序列号：$PHONE_SERIAL
眼镜序列号：$GLASS_SERIAL
手机包名：$phone_package
眼镜包名：$glass_package

文件：
  phone-summary.txt / phone-logcat.txt
  glass-summary.txt / glass-logcat.txt
  artifacts.txt（adb 版本、APK SHA-256 和包名）

报告只包含设备诊断信息和最近 300 行过滤日志；脚本没有读取或输出 local.properties 的凭证值。
EOF

echo "诊断报告已写入：$OUT_DIR"
echo "请一并提供 README.txt、phone-summary.txt、glass-summary.txt 以及需要时的 logcat 文件。"

#!/usr/bin/env bash
set -euo pipefail

# One-command field commissioning: verify the exact devices, install the
# selected APKs without clearing data, then save a diagnostic bundle.
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PHONE_SERIAL=""
GLASS_SERIAL=""
ADB_BIN=""
VARIANT="debug"
OUT_DIR=""

usage() {
  cat <<'EOF'
用法：
  ./field-commission.sh --phone PHONE_SERIAL --glass GLASS_SERIAL \
    [--variant debug|release] [--out DIR] [--adb PATH]

说明：
  - 自动执行严格预检、非破坏性安装和现场诊断留档。
  - 默认安装 Debug；Release 会额外要求两个 APK 已通过签名校验。
  - 不会清除应用数据，不会删除任务、草稿或照片队列。
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --phone)
      [[ $# -ge 2 ]] || { echo "缺少 --phone 的序列号" >&2; exit 2; }
      PHONE_SERIAL="$2"; shift 2 ;;
    --glass)
      [[ $# -ge 2 ]] || { echo "缺少 --glass 的序列号" >&2; exit 2; }
      GLASS_SERIAL="$2"; shift 2 ;;
    --variant)
      [[ $# -ge 2 ]] || { echo "缺少 --variant 的值" >&2; exit 2; }
      case "$2" in debug|release) VARIANT="$2" ;; *) echo "--variant 只能是 debug 或 release：$2" >&2; exit 2 ;; esac
      shift 2 ;;
    --out)
      [[ $# -ge 2 ]] || { echo "缺少 --out 的目录" >&2; exit 2; }
      OUT_DIR="$2"; shift 2 ;;
    --adb)
      [[ $# -ge 2 ]] || { echo "缺少 --adb 的路径" >&2; exit 2; }
      ADB_BIN="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "未知参数：$1" >&2; usage >&2; exit 2 ;;
  esac
done

if [[ -z "$PHONE_SERIAL" || -z "$GLASS_SERIAL" ]]; then
  echo "必须同时指定 --phone 和 --glass。" >&2
  usage >&2
  exit 2
fi
if [[ "$PHONE_SERIAL" == "$GLASS_SERIAL" ]]; then
  echo "--phone 和 --glass 不能使用同一个序列号。" >&2
  exit 2
fi

adb_args=()
if [[ -n "$ADB_BIN" ]]; then adb_args+=(--adb "$ADB_BIN"); fi
device_args=(--require-device --phone "$PHONE_SERIAL" --glass "$GLASS_SERIAL")
if [[ "$VARIANT" == "release" ]]; then device_args+=(--require-release); fi

echo "== 现场严格预检 =="
"$ROOT_DIR/device-preflight.sh" "${adb_args[@]}" "${device_args[@]}"
echo "== 安装 $VARIANT APK =="
install_args=(--phone "$PHONE_SERIAL" --glass "$GLASS_SERIAL" --variant "$VARIANT")
if [[ -n "$ADB_BIN" ]]; then install_args+=(--adb "$ADB_BIN"); fi
"$ROOT_DIR/field-install.sh" "${install_args[@]}"
echo "== 生成现场诊断 =="
diagnostic_args=(--phone "$PHONE_SERIAL" --glass "$GLASS_SERIAL")
if [[ -n "$ADB_BIN" ]]; then diagnostic_args+=(--adb "$ADB_BIN"); fi
if [[ -n "$OUT_DIR" ]]; then diagnostic_args+=(--out "$OUT_DIR"); fi
"$ROOT_DIR/field-diagnostics.sh" "${diagnostic_args[@]}"
echo "现场启用完成：手机 ${PHONE_SERIAL}；眼镜 ${GLASS_SERIAL}；变体 ${VARIANT}"

#!/usr/bin/env bash
set -euo pipefail

# Build and assemble a signed, field-installable Release bundle. This script
# refuses unsigned APKs before creating an output directory.
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT_DIR=""
SKIP_BUILD=0

usage() {
  cat <<'EOF'
用法：
  ./package-release.sh [--out DIR] [--skip-build]

说明：
  - 默认先构建两个 Release APK，再要求它们通过 apksigner 校验。
  - --skip-build 只复用已有 Release APK，适合重复打包。
  - 输出目录必须为空或不存在，避免覆盖已有交付证据。
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --out)
      [[ $# -ge 2 ]] || { echo "缺少 --out 的目录" >&2; usage >&2; exit 2; }
      OUT_DIR="$2"
      shift 2
      ;;
    --skip-build)
      SKIP_BUILD=1
      shift
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

if [[ -z "${JAVA_HOME:-}" && -x "/opt/homebrew/opt/openjdk@17/bin/java" ]]; then
  export JAVA_HOME="/opt/homebrew/opt/openjdk@17"
fi

if [[ "$SKIP_BUILD" == 0 ]]; then
  "$ROOT_DIR/gradlew" :phone-app:assembleRelease :glass-app:assembleRelease --console=plain
fi

# This is intentionally before mkdir/copy: an unsigned build must not create
# a directory that looks like a production delivery bundle.
"$ROOT_DIR/release-check.sh" --require-signed

phone_apk="$ROOT_DIR/phone-app/build/outputs/apk/release/phone-app-release.apk"
glass_apk="$ROOT_DIR/glass-app/build/outputs/apk/release/glass-app-release.apk"
[[ -s "$phone_apk" ]] || { echo "缺少已签名手机 APK：$phone_apk" >&2; exit 1; }
[[ -s "$glass_apk" ]] || { echo "缺少已签名眼镜 APK：$glass_apk" >&2; exit 1; }

if [[ -z "$OUT_DIR" ]]; then
  OUT_DIR="$ROOT_DIR/../artifacts/rokid-release-$(date +%Y%m%d-%H%M%S)"
fi
if [[ -e "$OUT_DIR" ]]; then
  if [[ ! -d "$OUT_DIR" || -n "$(find "$OUT_DIR" -mindepth 1 -maxdepth 1 -print -quit)" ]]; then
    echo "输出目录已存在且非空：$OUT_DIR" >&2
    exit 1
  fi
else
  mkdir -p "$OUT_DIR"
fi
OUT_ABS="$(cd "$OUT_DIR" && pwd)"
mkdir -p "$OUT_ABS/apk" "$OUT_ABS/docs"

cp "$phone_apk" "$OUT_ABS/apk/phone-app-release.apk"
cp "$glass_apk" "$OUT_ABS/apk/glass-app-release.apk"
for script in device-preflight.sh field-install.sh field-diagnostics.sh field-commission.sh release-check.sh; do
  cp "$ROOT_DIR/$script" "$OUT_ABS/docs/$script"
  chmod +x "$OUT_ABS/docs/$script"
done
cp "$ROOT_DIR/SMOKE_TEST.md" "$OUT_ABS/docs/SMOKE_TEST.md"
cp "$ROOT_DIR/FIELD_HANDOFF.md" "$OUT_ABS/docs/FIELD_HANDOFF.md"
cp "$ROOT_DIR/local.properties.example" "$OUT_ABS/docs/local.properties.example"
cp "$ROOT_DIR/../protocol/task-complete.example.json" "$OUT_ABS/docs/task-complete.example.json"
cp "$ROOT_DIR/../protocol/device-heartbeat.example.json" "$OUT_ABS/docs/device-heartbeat.example.json"
cp "$ROOT_DIR/../ANDROID_ROKID_OFFICIAL_AUDIT.md" "$OUT_ABS/docs/ANDROID_ROKID_OFFICIAL_AUDIT.md"

generated_at="$(date '+%Y-%m-%d %H:%M:%S %z')"
awk -v out_abs="$OUT_ABS" -v generated_at="$generated_at" '
{
  gsub(/__OUT_ABS__/, out_abs)
  gsub(/__GENERATED_AT__/, generated_at)
  print
}
' >"$OUT_ABS/README.md" <<'EOF'
# inspection-demo Rokid Glass3 Enterprise Release

生成日期：__GENERATED_AT__

这是已通过企业签名校验的 Release 交付包：

- `apk/phone-app-release.apk`：手机端
- `apk/glass-app-release.apk`：眼镜端
- `SHA256SUMS`：APK 校验清单
- `docs/FIELD_HANDOFF.md`：现场资料、安装和验收清单
- `docs/local.properties.example`：不含真实凭证的配置模板
- `docs/`：现场预检、安装、诊断、签名检查和 Smoke Test

安装前执行：

```bash
cd "__OUT_ABS__"
if command -v sha256sum >/dev/null 2>&1; then
  sha256sum -c SHA256SUMS
else
  shasum -a 256 -c SHA256SUMS
fi
docs/device-preflight.sh --require-device --require-release --phone PHONE_SERIAL --glass GLASS_SERIAL
docs/field-commission.sh --variant release --phone PHONE_SERIAL --glass GLASS_SERIAL
```

仍需按 `docs/SMOKE_TEST.md` 完成真实 Glass3、蓝牙/P2P、按键、相机、照片和断线恢复验收。
EOF

hash_files() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$@"
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$@"
  else
    echo "找不到 sha256sum 或 shasum，无法生成 SHA256SUMS" >&2
    return 1
  fi
}

(cd "$OUT_ABS" && hash_files apk/*.apk > SHA256SUMS)
echo "Release 交付包已生成：$OUT_ABS"
echo "请在交付包根目录执行：sha256sum -c SHA256SUMS（没有 sha256sum 时使用 shasum -a 256 -c SHA256SUMS）"

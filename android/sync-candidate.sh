#!/usr/bin/env bash
set -euo pipefail

# Copy the four locally built APKs and the field-facing scripts into a
# reviewable candidate bundle. This does not sign APKs, install anything, or
# modify local.properties; it only refreshes the explicitly selected output.
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT_DIR="${ROOT_DIR}/../artifacts/rokid-rc-20261106"

usage() {
  cat <<'EOF'
用法：
  ./sync-candidate.sh [--out DIR]

说明：
  - 复用 android/phone-app 和 android/glass-app 已构建的 Debug/Release APK；Release 优先选择已签名的 *.apk，否则使用 *-unsigned.apk。
  - 输出目录中的 APK、现场脚本、Smoke Test 和 SHA256SUMS 会被刷新。
  - 不构建、不签名、不安装 APK，也不读取或修改企业凭证。
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
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

phone_debug="$ROOT_DIR/phone-app/build/outputs/apk/debug/phone-app-debug.apk"
glass_debug="$ROOT_DIR/glass-app/build/outputs/apk/debug/glass-app-debug.apk"
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
phone_release="$(release_apk_path phone-app)"
glass_release="$(release_apk_path glass-app)"
for apk in "$phone_debug" "$glass_debug" "$phone_release" "$glass_release"; do
  [[ -s "$apk" ]] || { echo "缺少 APK，请先运行 ./host-smoke.sh：$apk" >&2; exit 1; }
done

mkdir -p "$OUT_DIR/apk" "$OUT_DIR/docs"
cp "$phone_debug" "$OUT_DIR/apk/phone-app-debug.apk"
cp "$glass_debug" "$OUT_DIR/apk/glass-app-debug.apk"
# A signed build uses the same module name without the -unsigned suffix. Remove
# only the two known Release filenames so switching from unsigned to signed
# cannot leave a stale APK in the candidate checksum set.
rm -f "$OUT_DIR/apk/phone-app-release.apk" "$OUT_DIR/apk/phone-app-release-unsigned.apk"
rm -f "$OUT_DIR/apk/glass-app-release.apk" "$OUT_DIR/apk/glass-app-release-unsigned.apk"
cp "$phone_release" "$OUT_DIR/apk/$(basename "$phone_release")"
cp "$glass_release" "$OUT_DIR/apk/$(basename "$glass_release")"

for script in device-preflight.sh field-install.sh field-diagnostics.sh field-commission.sh field-script-smoke.sh release-check.sh; do
  cp "$ROOT_DIR/$script" "$OUT_DIR/docs/$script"
  chmod +x "$OUT_DIR/docs/$script"
done
cp "$ROOT_DIR/SMOKE_TEST.md" "$OUT_DIR/docs/SMOKE_TEST.md"
cp "$ROOT_DIR/FIELD_HANDOFF.md" "$OUT_DIR/docs/FIELD_HANDOFF.md"
cp "$ROOT_DIR/local.properties.example" "$OUT_DIR/docs/local.properties.example"
if [[ -s "$ROOT_DIR/../LEADERSHIP_STATUS.md" ]]; then
  cp "$ROOT_DIR/../LEADERSHIP_STATUS.md" "$OUT_DIR/docs/LEADERSHIP_STATUS.md"
fi
cp "$ROOT_DIR/../protocol/task-complete.example.json" "$OUT_DIR/docs/task-complete.example.json"
cp "$ROOT_DIR/../protocol/device-heartbeat.example.json" "$OUT_DIR/docs/device-heartbeat.example.json"
if [[ -s "$ROOT_DIR/../ANDROID_ROKID_OFFICIAL_AUDIT.md" ]]; then
  cp "$ROOT_DIR/../ANDROID_ROKID_OFFICIAL_AUDIT.md" "$OUT_DIR/docs/ANDROID_ROKID_OFFICIAL_AUDIT.md"
fi

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

(cd "$OUT_DIR" && hash_files apk/*.apk > SHA256SUMS)
OUT_ABS="$(cd "$OUT_DIR" && pwd)"
generated_at="$(date '+%Y-%m-%d %H:%M:%S %z')"
phone_release_name="$(basename "$phone_release")"
glass_release_name="$(basename "$glass_release")"
cat > "$OUT_ABS/README.md" <<EOF
# inspection-demo Rokid Glass3 Enterprise 候选包

生成日期：$generated_at

这是本机最近一次同步的候选 APK 集合。文件名来自实际构建产物，Release 可能是已签名的 \`*-release.apk\`，也可能是未签名的 \`*-release-unsigned.apk\`。

## APK

- \`apk/phone-app-debug.apk\`：手机 Debug APK
- \`apk/glass-app-debug.apk\`：眼镜 Debug APK
- \`apk/$phone_release_name\`：手机 Release APK
- \`apk/$glass_release_name\`：眼镜 Release APK
- \`SHA256SUMS\`：以上四个 APK 的 SHA-256 清单
- \`docs/FIELD_HANDOFF.md\`：现场资料、安装和验收清单
- \`docs/local.properties.example\`：不含真实凭证的配置模板
- \`docs/LEADERSHIP_STATUS.md\`：面向管理人员的当前进度说明

## 交接前检查

\`\`\`bash
# 在候选包根目录执行（Linux 使用 sha256sum，macOS 使用 shasum）
if command -v sha256sum >/dev/null 2>&1; then
  sha256sum -c SHA256SUMS
else
  shasum -a 256 -c SHA256SUMS
fi
docs/device-preflight.sh
\`\`\`

正式安装前必须提供 Android SDK/adb、真实手机和 Glass3；签名 Release 还必须执行：

\`\`\`bash
docs/device-preflight.sh --require-device --require-release --phone PHONE_SERIAL --glass GLASS_SERIAL
docs/field-commission.sh --variant release --phone PHONE_SERIAL --glass GLASS_SERIAL
\`\`\`

当前候选包不代表真实 Glass3 验收完成。蓝牙/P2P、实体按键、相机、照片文件通道、断线恢复和正式后端仍需按 \`docs/SMOKE_TEST.md\` 现场验证。
EOF
echo "候选包已同步：$(cd "$OUT_DIR" && pwd)"
echo "请在候选包根目录执行：sha256sum -c SHA256SUMS（没有 sha256sum 时使用 shasum -a 256 -c SHA256SUMS）"

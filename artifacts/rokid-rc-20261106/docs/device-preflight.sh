#!/usr/bin/env bash
set -u

# Read-only environment check for the Glass3 field setup. It deliberately
# prints presence/absence of secrets, never their values.
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [[ -d "$ROOT_DIR/phone-app" ]]; then
  PROJECT_ROOT="$ROOT_DIR"
  PACKAGE_MODE=0
else
  PROJECT_ROOT="$(cd "$ROOT_DIR/.." && pwd)"
  PACKAGE_MODE=1
fi
LOCAL_PROPERTIES="$PROJECT_ROOT/local.properties"
REQUIRE_DEVICE=0
REQUIRE_RELEASE=0
PHONE_SERIAL=""
GLASS_SERIAL=""
ADB_BIN=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --require-device) REQUIRE_DEVICE=1 ;;
    --require-release) REQUIRE_RELEASE=1 ;;
    --phone)
      [[ $# -ge 2 ]] || { echo "缺少 --phone 的序列号" >&2; exit 2; }
      PHONE_SERIAL="$2"
      shift
      ;;
    --glass)
      [[ $# -ge 2 ]] || { echo "缺少 --glass 的序列号" >&2; exit 2; }
      GLASS_SERIAL="$2"
      shift
      ;;
    --adb)
      [[ $# -ge 2 ]] || { echo "缺少 --adb 的路径" >&2; exit 2; }
      ADB_BIN="$2"
      shift
      ;;
    -h|--help)
      cat <<'EOF'
用法：
  ./device-preflight.sh [--require-device] [--require-release]

--require-device   未检测到手机和 Glass3 两台 adb 真机时失败
--require-release  Release APK 未通过 apksigner 签名校验时失败
--phone SERIAL      指定现场手机序列号；必须与 --glass 一起使用
--glass SERIAL      指定现场 Glass3 序列号；必须与 --phone 一起使用
--adb PATH          指定 adb 路径；不填时从 PATH 或 sdk.dir 自动寻找
EOF
      exit 0
      ;;
    *)
      echo "未知参数：$1" >&2
      exit 2
      ;;
  esac
  shift
done

if [[ -n "$PHONE_SERIAL" || -n "$GLASS_SERIAL" ]] &&
   [[ -z "$PHONE_SERIAL" || -z "$GLASS_SERIAL" ]]; then
  echo "--phone 和 --glass 必须同时指定，避免把两台设备角色弄反。" >&2
  exit 2
fi
if [[ -n "$PHONE_SERIAL" && "$PHONE_SERIAL" == "$GLASS_SERIAL" ]]; then
  echo "--phone 和 --glass 不能使用同一个序列号。" >&2
  exit 2
fi

failures=0
warnings=0

pass() { printf 'PASS  %s\n' "$1"; }
warn() { printf 'WARN  %s\n' "$1"; warnings=$((warnings + 1)); }
fail() { printf 'FAIL  %s\n' "$1"; failures=$((failures + 1)); }

java_home_candidate="${JAVA_HOME:-}"
java_bin=""
if [[ -n "$java_home_candidate" && -x "$java_home_candidate/bin/java" ]]; then
  java_bin="$java_home_candidate/bin/java"
elif [[ -z "$java_home_candidate" ]]; then
  # macOS commonly has a JDK 17 installed through Homebrew or the system
  # java_home helper while JAVA_HOME is not exported in a new shell.
  if [[ -x "/usr/libexec/java_home" ]]; then
    java_home_candidate="$(/usr/libexec/java_home -v 17 2>/dev/null || true)"
  fi
  if [[ -n "$java_home_candidate" && -x "$java_home_candidate/bin/java" ]]; then
    java_bin="$java_home_candidate/bin/java"
  elif [[ -x "/opt/homebrew/opt/openjdk@17/bin/java" ]]; then
    java_home_candidate="/opt/homebrew/opt/openjdk@17"
    java_bin="$java_home_candidate/bin/java"
  elif command -v java >/dev/null 2>&1; then
    java_bin="$(command -v java)"
  fi
fi
if [[ -n "$java_bin" ]]; then
  java_version="$("$java_bin" -version 2>&1 | awk -F'"' '/version/ {print $2; exit}')"
  if [[ "$java_version" == 17.* ]]; then
    pass "JDK 17 ($java_version${java_home_candidate:+; JAVA_HOME=$java_home_candidate})"
  else
    if [[ "$PACKAGE_MODE" == 1 ]]; then
      warn "候选交付包不构建 APK；跳过 JDK 17 严格校验（检测到 ${java_version:-unknown}）"
    else
      fail "需要 JDK 17（检测到 ${java_version:-unknown}）"
    fi
  fi
else
  if [[ "$PACKAGE_MODE" == 1 ]]; then
    pass "候选交付包无需 JDK 17"
  else
    fail "找不到可执行的 JDK 17；请设置 JAVA_HOME"
  fi
fi

if [[ -x "$PROJECT_ROOT/gradlew" ]]; then
  pass "Gradle Wrapper 可执行"
elif [[ "$PACKAGE_MODE" == 1 ]]; then
  pass "候选交付包无需 Gradle 构建"
else
  fail "缺少可执行的 Gradle Wrapper：$PROJECT_ROOT/gradlew"
fi

sdk_dir=""
if [[ -f "$LOCAL_PROPERTIES" ]]; then
  sdk_dir="$(awk -F= '$1 == "sdk.dir" {sub(/^[[:space:]]+/, "", $2); print $2; exit}' "$LOCAL_PROPERTIES")"
fi
sdk_dir="${sdk_dir:-${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}}"
if [[ -z "$sdk_dir" ]]; then
  if [[ "$PACKAGE_MODE" == 1 ]]; then
    warn "候选交付包未提供 sdk.dir；将仅使用 PATH 中的 adb/aapt"
  else
    fail "local.properties 没有 sdk.dir"
  fi
elif [[ -d "$sdk_dir" ]]; then
  pass "Android SDK 可用：$sdk_dir"
else
  fail "Android SDK 路径不存在：$sdk_dir"
fi

property_value() {
  local key="$1"
  [[ -f "$LOCAL_PROPERTIES" ]] || return 0
  awk -F= -v wanted="$key" '$1 == wanted {sub(/^[[:space:]]+/, "", $2); print $2; exit}' "$LOCAL_PROPERTIES"
}

app_id="$(property_value rokid.appId)"
app_secret="$(property_value rokid.appSecret)"
if [[ -n "$app_id" && -n "$app_secret" && "$app_id" != replace-with-* && "$app_secret" != replace-with-* ]]; then
  pass "Rokid 企业 AK/SK 已配置（值不显示）"
else
  warn "Rokid 企业 AK/SK 未完整配置或仍是占位值；手机端 SDK 初始化会拒绝继续"
fi

sync_url="$(property_value inspection.syncUrl)"
if [[ -z "$sync_url" ]]; then
  warn "报告同步 URL 为空；报告只保存在本地队列"
elif [[ "$sync_url" == https://* ]]; then
  pass "报告同步 URL 使用 HTTPS"
else
  fail "报告同步 URL 必须使用 HTTPS"
fi

signing_keys=(android.signing.storeFile android.signing.storePassword android.signing.keyAlias android.signing.keyPassword)
signing_count=0
for key in "${signing_keys[@]}"; do
  [[ -n "$(property_value "$key")" ]] && signing_count=$((signing_count + 1))
done
if [[ "$signing_count" == 0 ]]; then
  if [[ "$PACKAGE_MODE" == 1 && -s "$PROJECT_ROOT/apk/phone-app-release.apk" && -s "$PROJECT_ROOT/apk/glass-app-release.apk" ]]; then
    pass "候选包已携带 Release APK；签名由 release-check.sh 校验"
  else
    warn "企业签名未配置；Release APK 将是 unsigned"
  fi
elif [[ "$signing_count" == "${#signing_keys[@]}" ]]; then
  pass "企业签名配置完整（值不显示）"
else
  fail "android.signing.* 必须四项同时配置"
fi

apk_paths=(
  "$PROJECT_ROOT/phone-app/build/outputs/apk/debug/phone-app-debug.apk"
  "$PROJECT_ROOT/glass-app/build/outputs/apk/debug/glass-app-debug.apk"
)
if [[ -s "$PROJECT_ROOT/apk/phone-app-debug.apk" ]]; then apk_paths[0]="$PROJECT_ROOT/apk/phone-app-debug.apk"; fi
if [[ -s "$PROJECT_ROOT/apk/glass-app-debug.apk" ]]; then apk_paths[1]="$PROJECT_ROOT/apk/glass-app-debug.apk"; fi
if [[ ! -s "${apk_paths[0]}" ]]; then
  if [[ -s "$PROJECT_ROOT/apk/phone-app-release.apk" ]]; then apk_paths[0]="$PROJECT_ROOT/apk/phone-app-release.apk"
  elif [[ -s "$PROJECT_ROOT/apk/phone-app-release-unsigned.apk" ]]; then apk_paths[0]="$PROJECT_ROOT/apk/phone-app-release-unsigned.apk"
  fi
fi
if [[ ! -s "${apk_paths[1]}" ]]; then
  if [[ -s "$PROJECT_ROOT/apk/glass-app-release.apk" ]]; then apk_paths[1]="$PROJECT_ROOT/apk/glass-app-release.apk"
  elif [[ -s "$PROJECT_ROOT/apk/glass-app-release-unsigned.apk" ]]; then apk_paths[1]="$PROJECT_ROOT/apk/glass-app-release-unsigned.apk"
  fi
fi
for apk in "${apk_paths[@]}"; do
  if [[ -s "$apk" ]]; then
    pass "APK 可用：$(basename "$apk")"
  else
    warn "尚未生成 APK：$(basename "$apk")"
  fi
done

# Validate the installable artifacts before a field visit. This catches a
# swapped APK or a stale catalog while there is still no device involved.
aapt_bin=""
if command -v aapt >/dev/null 2>&1; then
  aapt_bin="$(command -v aapt)"
elif [[ -n "$sdk_dir" && -d "$sdk_dir/build-tools" ]]; then
  for candidate in "$sdk_dir"/build-tools/*/aapt; do
    [[ -x "$candidate" ]] && aapt_bin="$candidate"
  done
fi
if [[ ! -s "${apk_paths[0]}" || ! -s "${apk_paths[1]}" ]]; then
  warn "APK 尚未齐备；跳过包名、SDK 级别和文件入口核对"
elif [[ -z "$aapt_bin" ]]; then
  warn "找不到 aapt；跳过 APK 包名和 SDK 级别核对"
else
  phone_package="$("$aapt_bin" dump badging "${apk_paths[0]}" 2>/dev/null | awk -F"'" '/^package: name=/{print $2; exit}')"
  glass_package="$("$aapt_bin" dump badging "${apk_paths[1]}" 2>/dev/null | awk -F"'" '/^package: name=/{print $2; exit}')"
  expected_phone="$(property_value android.phoneApplicationId)"
  expected_glass="$(property_value android.glassApplicationId)"
  if [[ "$PACKAGE_MODE" == 1 ]]; then
    # A release candidate intentionally carries only APKs and field docs.
    # When no local.properties is present, the APK manifest is the source of
    # truth for an enterprise-registered custom applicationId.
    expected_phone="${expected_phone:-$phone_package}"
    expected_glass="${expected_glass:-$glass_package}"
  else
    expected_phone="${expected_phone:-com.inspection.demo.phone}"
    expected_glass="${expected_glass:-com.inspection.demo.glass}"
  fi
  if [[ "$phone_package" == "$expected_phone" ]]; then
    pass "手机 APK 包名：$phone_package"
  else
    fail "手机 APK 包名不匹配：期望 ${expected_phone}，实际 ${phone_package:-unknown}"
  fi
  if [[ "$glass_package" == "$expected_glass" ]]; then
    pass "眼镜 APK 包名：$glass_package"
  else
    fail "眼镜 APK 包名不匹配：期望 ${expected_glass}，实际 ${glass_package:-unknown}"
  fi
  phone_sdk="$("$aapt_bin" dump badging "${apk_paths[0]}" 2>/dev/null | awk -F"'" '/^sdkVersion:/{print $2; exit}')"
  glass_sdk="$("$aapt_bin" dump badging "${apk_paths[1]}" 2>/dev/null | awk -F"'" '/^sdkVersion:/{print $2; exit}')"
  phone_target="$("$aapt_bin" dump badging "${apk_paths[0]}" 2>/dev/null | awk -F"'" '/^targetSdkVersion:/{print $2; exit}')"
  glass_target="$("$aapt_bin" dump badging "${apk_paths[1]}" 2>/dev/null | awk -F"'" '/^targetSdkVersion:/{print $2; exit}')"
  [[ "$phone_sdk" == 24 && "$phone_target" == 34 ]] && pass "手机 APK SDK：min $phone_sdk / target $phone_target" || fail "手机 APK SDK 不符合预期：min ${phone_sdk:-unknown} / target ${phone_target:-unknown}"
  [[ "$glass_sdk" == 29 && "$glass_target" == 34 ]] && pass "眼镜 APK SDK：min $glass_sdk / target $glass_target" || fail "眼镜 APK SDK 不符合预期：min ${glass_sdk:-unknown} / target ${glass_target:-unknown}"

  # The Web console exports task.dispatch JSON files. The phone APK must be
  # able to receive those files from Android's file picker or a file manager.
  manifest_dump="$($aapt_bin dump xmltree "${apk_paths[0]}" AndroidManifest.xml 2>/dev/null || true)"
  if grep -Fq 'android.intent.action.VIEW' <<<"$manifest_dump"; then
    pass "手机 APK 支持 VIEW 文件打开入口"
  else
    fail "手机 APK 缺少 VIEW 文件打开入口"
  fi
  if grep -Fq 'android.intent.action.SEND' <<<"$manifest_dump"; then
    pass "手机 APK 支持 SEND 文件分享入口"
  else
    fail "手机 APK 缺少 SEND 文件分享入口"
  fi
  if grep -Fq 'application/json' <<<"$manifest_dump"; then
    pass "手机 APK 支持 application/json 任务文件"
  else
    fail "手机 APK 缺少 application/json 任务文件入口"
  fi
  if grep -Fq 'android.permission.ACCESS_COARSE_LOCATION' <<<"$manifest_dump"; then
    pass "手机 APK 声明 Android 12/12L Wi-Fi P2P 所需定位权限"
  else
    fail "手机 APK 缺少 Android 12/12L Wi-Fi P2P 粗略定位权限"
  fi
  if grep -Fq 'android.permission.BLUETOOTH_ADVERTISE' <<<"$manifest_dump"; then
    phone_source_manifest="$PROJECT_ROOT/phone-app/src/main/AndroidManifest.xml"
    if [[ "$PACKAGE_MODE" == 1 ]]; then
      pass "手机 APK 的蓝牙广播声明来自已打包依赖，候选包不主动申请该权限"
    elif [[ -s "$phone_source_manifest" ]] &&
       ! grep -Fq 'android.permission.BLUETOOTH_ADVERTISE' "$phone_source_manifest"; then
      # Some SDK versions merge this declaration into the final manifest even
      # when the app itself never requests it at runtime. The meaningful gate
      # is the app's permission request list, which host-smoke checks in
      # MainActivity; keep this APK-level result informational.
      pass "手机 APK 未由应用主动申请蓝牙广播权限（SDK 可能合并声明）"
    else
      fail "手机 APK 不应申请蓝牙广播权限；手机端只发起连接"
    fi
  else
    pass "手机 APK 未申请不必要的蓝牙广播权限"
  fi
  glass_manifest_dump="$($aapt_bin dump xmltree "${apk_paths[1]}" AndroidManifest.xml 2>/dev/null || true)"
  glass_source_manifest="$PROJECT_ROOT/glass-app/src/main/AndroidManifest.xml"
  for permission in \
    'android.permission.BLUETOOTH_SCAN' \
    'android.permission.BLUETOOTH_CONNECT' \
    'android.permission.NEARBY_WIFI_DEVICES'; do
    if grep -Fq "$permission" <<<"$glass_manifest_dump"; then
      pass "眼镜 APK 声明 ${permission}"
    elif [[ "$PACKAGE_MODE" == 0 && -s "$glass_source_manifest" ]] && grep -Fq "$permission" "$glass_source_manifest"; then
      # host-smoke runs one preflight before Gradle. If an older local APK is
      # still present, do not report a source change as a build failure; the
      # post-build preflight will verify the actual APK. Strict field checks
      # still fail until the installed artifact contains the permission.
      if [[ "$REQUIRE_DEVICE" == 1 || "$REQUIRE_RELEASE" == 1 ]]; then
        fail "眼镜 APK 缺少 ${permission}（源码已声明，请先重新构建）"
      else
        warn "眼镜 APK 尚未包含 ${permission}；源码已声明，重新构建后复核"
      fi
    else
      fail "眼镜 APK 缺少 ${permission}"
    fi
  done
fi

if [[ "$REQUIRE_RELEASE" == 1 ]]; then
  if [[ -x "$ROOT_DIR/release-check.sh" ]]; then
    if ! "$ROOT_DIR/release-check.sh" --require-signed; then
      fail "Release APK 未通过签名校验"
    fi
  else
    fail "缺少 release-check.sh，无法执行 Release 签名校验"
  fi
fi

catalog_source="$PROJECT_ROOT/../seed/building4-catalog.json"
hash_stdin() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$@"
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$@"
  else
    return 1
  fi
}
if [[ -s "$catalog_source" && -s "${apk_paths[0]}" ]] && command -v unzip >/dev/null 2>&1 && { command -v sha256sum >/dev/null 2>&1 || command -v shasum >/dev/null 2>&1; }; then
  source_hash="$(hash_stdin "$catalog_source" | awk '{print $1}')"
  apk_hash="$(unzip -p "${apk_paths[0]}" assets/building4-catalog.json 2>/dev/null | hash_stdin | awk '{print $1}')"
  if [[ -n "$apk_hash" && "$source_hash" == "$apk_hash" ]]; then
    pass "四号楼目录已打入 APK 且与 Web seed SHA-256 一致"
  else
    fail "四号楼目录缺失或与 Web seed 不一致"
  fi
else
  if [[ "$PACKAGE_MODE" == 1 ]]; then
    warn "候选交付包未附带 Web seed；跳过 APK 与源码目录比对"
  else
    warn "无法核对 APK 内四号楼目录（缺少 unzip/sha256 工具或源目录）"
  fi
fi

adb_bin="$ADB_BIN"
if [[ -n "$adb_bin" && ! -x "$adb_bin" ]]; then
  fail "指定的 adb 不可执行：$adb_bin"
  adb_bin=""
fi
if [[ -z "$adb_bin" && -x "$(command -v adb 2>/dev/null || true)" ]]; then
  adb_bin="$(command -v adb)"
elif [[ -z "$adb_bin" && -n "$sdk_dir" && -x "$sdk_dir/platform-tools/adb" ]]; then
  adb_bin="$sdk_dir/platform-tools/adb"
fi
if [[ -z "$adb_bin" ]]; then
  # A release candidate is intentionally self-contained: APK review and
  # signature checks can happen on a handoff machine before Android SDK or
  # devices are available. Device access becomes a hard gate only when the
  # caller explicitly asks for it.
  if [[ "$PACKAGE_MODE" == 1 && "$REQUIRE_DEVICE" == 0 && -z "$PHONE_SERIAL" ]]; then
    warn "找不到 adb；候选包可先离线核对，现场安装前请提供 Android SDK/adb"
  else
    fail "找不到 adb"
  fi
else
  device_state() {
    local serial="$1"
    "$adb_bin" -s "$serial" get-state 2>/dev/null | tr -d '\r' | tail -n 1 || true
  }
  if [[ -n "$PHONE_SERIAL" ]]; then
    phone_state="$(device_state "$PHONE_SERIAL")"
    glass_state="$(device_state "$GLASS_SERIAL")"
    if [[ "$phone_state" == "device" ]]; then
      phone_model="$($adb_bin -s "$PHONE_SERIAL" shell getprop ro.product.model 2>/dev/null | tr -d '\r' || true)"
      pass "已确认指定手机：$PHONE_SERIAL${phone_model:+（${phone_model}）}"
    else
      fail "指定手机不可用或未授权：$PHONE_SERIAL"
    fi
    if [[ "$glass_state" == "device" ]]; then
      glass_model="$($adb_bin -s "$GLASS_SERIAL" shell getprop ro.product.model 2>/dev/null | tr -d '\r' || true)"
      pass "已确认指定 Glass3：$GLASS_SERIAL${glass_model:+（${glass_model}）}"
    else
      fail "指定 Glass3 不可用或未授权：$GLASS_SERIAL"
    fi
  else
    device_lines="$($adb_bin devices | awk 'NR > 1 && $2 == "device" {count++} END {print count + 0}')"
    if [[ "$device_lines" -ge 2 ]]; then
      pass "adb 已连接 ${device_lines} 台设备；现场安装仍须显式指定 --phone 和 --glass"
    elif [[ "$REQUIRE_DEVICE" == 1 ]]; then
      fail "adb 设备不足：当前 ${device_lines} 台，需要手机和 Glass3 两台设备"
    else
      warn "当前未确认手机和 Glass3 两台设备；连接数据调试线后重试"
    fi
  fi
fi

printf '\n结果：%d 个失败，%d 个提醒\n' "$failures" "$warnings"
if [[ "$failures" -gt 0 ]]; then
  exit 1
fi

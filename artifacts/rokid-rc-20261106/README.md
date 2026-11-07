# inspection-demo Rokid Glass3 Enterprise 候选包

生成日期：2026-11-07 20:53:22 +0800

这是本机最近一次同步的候选 APK 集合。文件名来自实际构建产物，Release 可能是已签名的 `*-release.apk`，也可能是未签名的 `*-release-unsigned.apk`。

## APK

- `apk/phone-app-debug.apk`：手机 Debug APK
- `apk/glass-app-debug.apk`：眼镜 Debug APK
- `apk/phone-app-release-unsigned.apk`：手机 Release APK
- `apk/glass-app-release-unsigned.apk`：眼镜 Release APK
- `SHA256SUMS`：以上四个 APK 的 SHA-256 清单
- `docs/FIELD_HANDOFF.md`：现场资料、安装和验收清单
- `docs/local.properties.example`：不含真实凭证的配置模板
- `docs/LEADERSHIP_STATUS.md`：面向管理人员的当前进度说明

## 交接前检查

```bash
# 在候选包根目录执行（Linux 使用 sha256sum，macOS 使用 shasum）
if command -v sha256sum >/dev/null 2>&1; then
  sha256sum -c SHA256SUMS
else
  shasum -a 256 -c SHA256SUMS
fi
docs/device-preflight.sh
```

正式安装前必须提供 Android SDK/adb、真实手机和 Glass3；签名 Release 还必须执行：

```bash
docs/device-preflight.sh --require-device --require-release --phone PHONE_SERIAL --glass GLASS_SERIAL
docs/field-commission.sh --variant release --phone PHONE_SERIAL --glass GLASS_SERIAL
```

当前候选包不代表真实 Glass3 验收完成。蓝牙/P2P、实体按键、相机、照片文件通道、断线恢复和正式后端仍需按 `docs/SMOKE_TEST.md` 现场验证。

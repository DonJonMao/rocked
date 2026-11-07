# Android Release 与现场脚本演练记录

记录时间：2026-11-06 05:16 +0800

这份记录只包含本机和一次性测试签名演练，不代表真实 Glass3 已验收。

## 当前源码工程

在 `android/` 目录执行 `./host-smoke.sh` 已通过：

- 协议测试：31/31；
- 手机上传测试：3/3；
- 手机端、眼镜端 lint 无 Error；
- Debug/Release unsigned 构建成功；
- Release 签名检查对 unsigned 产物给出提醒；
- 预检为 0 个失败、4 个提醒；
- 手机/眼镜 APK 内置四号楼目录与 Web seed SHA-256 一致。

## 一次性签名演练

使用临时本地测试 keystore（没有企业凭证）生成签名 APK 后，实际执行：

```bash
./release-check.sh --require-signed
./package-release.sh --skip-build --out <temporary-output>
shasum -a 256 -c SHA256SUMS
```

结果：手机、眼镜签名均可验证；交付包 APK checksum 全部 `OK`。

## 候选包现场脚本

在临时签名候选包自身的 `docs/` 目录执行：

```bash
docs/release-check.sh --require-signed
docs/device-preflight.sh --require-release
docs/field-install.sh --variant release --phone phone-1 --glass glass-1 --adb <fake-adb>
```

结果：签名门禁、候选包预检、Release 安装与包名核对均通过。fake `adb` 只验证脚本路径、APK 选择和安装核对逻辑，不能替代真实设备。

## 清理与当前交付状态

- 临时 keystore、签名 APK 和临时输出目录已删除；`local.properties` 已恢复。
- 当前候选包仍是 unsigned Release，位于 [rokid-rc-20261106](rokid-rc-20261106/)；其四个 APK 的 `SHA256SUMS` 已核对通过。
- 真实 Glass3 安装、蓝牙/P2P、实体按键、相机、照片文件通道、断网恢复、企业 AK/SK、正式签名和后端 HTTPS 仍需现场条件。

# Rokid Glass3 企业版现场交接清单

这份清单用于把巡检应用装到真实手机和 Rokid Glass3 企业版，并完成第一轮现场验收。候选包、源码和真实设备必须使用同一套 APK 校验清单。

## 现场需要提供

- Rokid Glass3 企业版，记录眼镜固件版本。
- 一部 Android 手机，记录手机型号和 Android 版本。
- Glass3 数据调试线；普通充电线不能保证电脑识别眼镜。
- Rokid 企业 `appId` 和 `appSecret`。
- 企业登记的 Rokid 客户端编号（没有特殊登记时使用 `InspectionGlass`）。
- 企业登记的手机包名、眼镜包名和正式签名要求。
- 企业 Release 签名文件、别名和密码（只在现场电脑配置，不回传到项目）。
- 正式巡检报告 HTTPS 地址，以及接口需要的 token（如果本阶段已提供）。

## 拿到资料后先做

1. 从候选包的 `docs/local.properties.example` 复制为交接电脑工程根目录的 `local.properties`，只在本机填写 SDK 路径、企业凭证、包名和签名信息。
2. 运行 `./device-preflight.sh`，先确认 APK、目录、包名和配置没有明显问题。
   脚本会自动从 `local.properties` 的 `sdk.dir/platform-tools/adb` 找工具，即使 `adb` 尚未加入 PATH 也能完成预检。
3. 运行 `./gradlew test :phone-app:lint :glass-app:lint assembleDebug :phone-app:assembleRelease :glass-app:assembleRelease`。
4. 先从 `adb devices` 记录手机和 Glass3 的序列号，再运行 `./device-preflight.sh --require-device --require-release --phone PHONE_SERIAL --glass GLASS_SERIAL`；它会分别确认两台指定设备已经授权。只指定 `--require-device` 时只能确认设备数量，不能确认角色。
5. 推荐直接运行 `./field-commission.sh --variant release --phone PHONE_SERIAL --glass GLASS_SERIAL`；它会自动完成严格预检、安装和诊断留档，安装前后都不会清除应用数据。
6. 如需单独排查，也可以分别运行 `field-install.sh` 和 `field-diagnostics.sh`；诊断报告会保存设备、固件、连接摘要和 APK 哈希。

## 现场必须完成

- 手机和眼镜初始化、蓝牙连接和 P2P 连接。
- 下发一个四号楼任务，眼镜确认设备后开始巡检。
- 填写读数、选择正常/异常/停机/待机/不涉及，并验证缺项会被拦截。
- 拍照并确认照片在手机端校验保存。
- 暂停、退出、重启后恢复当前步骤和草稿。
- 断开连接后恢复，确认未完成步骤、照片和草稿继续处理且不会重复生成记录。
- 验证实体单击、双击和镜腿左右滑动；滑动不能跳过校验。
- 完成任务后导出本地报告；如果已提供正式 HTTPS 地址，再验证上传、失败重试和幂等。

## 交接时要保存

- Glass3 固件版本、手机型号和 Android 版本。
- 使用的 APK 文件名、SHA-256、包名、签名证书指纹和 SDK 版本。
- 任务 ID、步骤 ID、照片 ID，以及失败发生的时间和现场提示。
- `artifacts/field-diagnostics-时间戳/` 下的诊断报告。
- [SMOKE_TEST.md](SMOKE_TEST.md) 每一项的通过、失败或未执行结果。

当前候选包仍不是正式发布包：没有企业签名、真实设备和企业凭证时，只能完成本机检查和离线演练。

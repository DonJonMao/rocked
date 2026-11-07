# Glass3 企业版真机 Smoke Test

这份清单验证 `inspection-demo` 的第一条真实设备链路。它不能用模拟器替代，眼镜端必须使用 **Glass3 数据调试线**，手机端必须配置企业 SDK 凭证。

## 0. 现场准备

- Rokid Glass3 企业版及已记录的固件版本。
- Android 手机，系统已打开蓝牙、Wi-Fi 和附近设备权限。
- Glass3 数据调试线和投屏工具。普通充电线不能让 Android Studio 或 `adb` 识别眼镜。
- Rokid 企业 `appId`、`appSecret`，以及已登记的包名/签名要求。

在 `android/local.properties` 中保留本机 SDK 路径并填写凭证；不要把这个文件提交到源码。现场脚本会自动从该路径找到 `adb`、`aapt` 和签名检查工具，不要求先修改系统 PATH：

```properties
sdk.dir=/path/to/Android/sdk
rokid.appId=企业提供的appId
rokid.appSecret=企业提供的appSecret
# 若企业登记了不同的手机/眼镜客户端编号，再改这一项；默认使用 InspectionGlass。
rokid.clientId=InspectionGlass
# 若企业登记了自定义包名，再填写这两项；不填则使用 demo 默认包名。
# android.phoneApplicationId=com.example.inspection.phone
# android.glassApplicationId=com.example.inspection.glass
```

## 1. 构建和安装

```bash
cd /path/to/inspection-demo/android
export JAVA_HOME=/path/to/jdk-17
./device-preflight.sh
./gradlew test assembleDebug

adb devices
# 用 adb devices 中的真实序列号替换 PHONE_SERIAL / GLASS_SERIAL。
./field-install.sh --phone PHONE_SERIAL --glass GLASS_SERIAL
# 企业签名完成并通过 release-check 后，也可以安装正式 Release：
# ./field-install.sh --variant release --phone PHONE_SERIAL --glass GLASS_SERIAL

# 现场推荐：一次完成严格预检、安装和诊断留档
./field-commission.sh --variant debug --phone PHONE_SERIAL --glass GLASS_SERIAL
```

现场正式安装前使用 `./device-preflight.sh --require-device --require-release --phone PHONE_SERIAL --glass GLASS_SERIAL`；它会按序列号分别确认手机和 Glass3，检查 SDK、APK、企业配置和 Release 签名，不会输出凭证内容。只装 Debug 联调时可省略 `--require-release`。如果暂时不指定序列号，预检只能确认“至少两台设备”，不能确认设备角色。
`field-install.sh` 会分别读取两个设备型号，从 APK 自动读取实际包名，执行非破坏性的 `adb install -r` 并核对包名；显式传序列号可避免把 APK 装错设备。若配置了自定义包名，下面强制停止示例中的包名也要替换为 `aapt dump badging` 输出的实际值。

安装前后如需记录现场环境，执行：

```bash
./field-diagnostics.sh --phone PHONE_SERIAL --glass GLASS_SERIAL
```

脚本只读收集两台设备的型号、Android/固件版本、包安装状态、权限、蓝牙/Wi-Fi/P2P 摘要和最近 300 行 `Rokid`/`inspection`/`GlassPhotoCapture` 日志，报告写入 `../artifacts/field-diagnostics-时间戳/`。
报告中的 `artifacts.txt` 还会记录 `adb` 版本、实际使用的 APK SHA-256 和包名，便于把现场日志与候选包对应起来。

预期：`adb devices` 同时显示手机和 Glass3（或投屏工具能看到眼镜设备）；两个 APK 安装成功。若手机端显示“未配置企业 AK/SK”，先检查 `local.properties` 后重新构建。

## 2. 连接和任务下发

1. 先启动眼镜端，画面显示“等待巡检任务”。
2. 启动手机端，点击“初始化 SDK”。Android 13 及以上授予蓝牙扫描、连接和附近 Wi‑Fi 设备权限；Android 12/12L 还要同时授予精确和粗略定位权限以使用 Wi‑Fi P2P；更旧系统按提示授予经典蓝牙和定位权限。手机端只作为连接发起方，不申请额外的蓝牙广播权限。拒绝任一必要权限时，应用不会继续初始化 SDK；若 SDK 已初始化但消息服务尚未就绪，按提示稍后重新初始化。眼镜端第一次拍照时若系统弹出相机授权，选择允许；拒绝后可在系统设置授权并重新点击拍照。
3. 点击“扫描并连接 Glass3”，等待“蓝牙已连接”。随后应自动尝试 P2P；必要时点击“建立/重试 P2P”。
4. 在手机端选择一个四号楼模板、对象、日期、时段和班次，点击“下发所选 4 号楼任务”。
5. 眼镜显示模板、设备和日程，并回传 `task.ack`。

如果现场先由 Web 管理端编排任务，也可以在 Web 工作台已有任务时点击“导出 Android task.dispatch”，把 JSON 传到手机；手机端点击“导入 Web task.dispatch JSON”。导入成功后应看到模板/设备名称，已连接时立即下发，未连接时在连接恢复后自动重试。也可以在系统文件管理器中直接打开文件，或通过分享面板将 JSON 分享给巡检手机端；两种入口都走相同的协议和任务结构校验。不要选择“导出 JSON”报告文件，它是归档格式，不是可执行任务。

手机端也注册了 JSON 文件打开入口；在系统文件管理器中直接点选 `task.dispatch` 文件会打开巡检手机端并执行同样的校验。错误类型的 JSON 会被拒绝，不会写入任务记录。

判定：手机状态显示眼镜确认任务；眼镜先停留在“收到任务”页面，点击“开始巡检”后发送 `task.start` 并进入设备确认步骤。重复点击下发同一任务不能清零眼镜当前步骤。

## 3. 步骤和照片

1. 在眼镜端点击“开始巡检”，输入目标设备资产码并勾选设备名称/位置确认；错误资产码或未勾选确认不能提交。
2. 在后续步骤填写一个读数和检查结果。
3. 点击“拍照并回传手机”，等待“本步骤照片已校验保存”。
4. 在手机端确认照片已写入应用私有目录。
5. 点击“确认并完成当前步骤”，等待手机 `step.ack` 后再观察眼镜进入下一步。
6. 最后一步勾选“我已核对以上巡检记录”后提交；选择异常、停机、待机或不涉及时，没有说明/原因不能提交。

判定：照片未收到 `photo.ack` 前不能满足拍照要求；无效读数、超限读数、缺说明、跳步提交和把其他步骤照片错绑到当前步骤都会被手机拒绝；重复 `step.complete` 不生成第二条完成记录。

最终复核成功后，手机会发送 `task.complete`。它只表示手机已经将全部步骤落盘，不能与报告导出 JSON 混用；协议样例见 `protocol/task-complete.example.json`。眼镜收到后应显示“手机已确认全部步骤保存”，重复通知保持幂等。

若照片文件损坏、声明格式与实际格式不一致或尺寸/哈希校验失败，手机必须回传 `photo.ack(accepted=false)`，眼镜应清理这张不可重试的照片并显示拒绝原因，不能在每次重启后无限重发同一文件。

## 4. 断开和恢复

1. 在当前步骤填写部分内容，点击“保存草稿并暂停”，确认眼镜显示暂停页面。
2. 重新启动眼镜端，确认仍停留在暂停页面，点击“继续巡检”后恢复原步骤和草稿；手机在线时手机端应显示暂停/继续状态。
3. 拍照后、收到 `photo.ack` 前关闭手机蓝牙或退出手机端，记录眼镜提示。
4. 重新启动手机端并完成 SDK 初始化、蓝牙和 P2P 连接。
5. 观察照片是否重新发送；如果连接在应用仍运行时恢复，照片 outbox 应在约 10 秒内自动重试；若步骤 ACK 在重启前丢失，观察最后一次 `step.complete` 是否重发。
6. 恢复连接后完成当前步骤并继续下一步。

眼镜 SDK client 就绪后会每 30 秒发送一次 `device.heartbeat`；手机独立状态栏应显示眼镜连接状态、电量和 App 版本，连续 90 秒未收到时应标记为心跳超时。若系统暂时无法读取电量，眼镜端应记录“本次心跳已跳过”，不能显示伪造的 `0%`，下一周期继续重试。心跳只证明控制通道可用，不代表任务或报告已经同步成功。协议样例见 `protocol/device-heartbeat.example.json`。

判定：照片文件仍存在，手机重新校验后只保留一个 `photoId`；重复步骤只返回原有确认，不改变已完成数据。网络断开不应让眼镜端已经保存的当前步骤消失。

### 4.1 未完成步骤草稿恢复

1. 在眼镜当前步骤填写部分读数、选择一个结果并输入说明，但不要点击完成。
2. 让眼镜端 Activity 进入后台或执行 `adb shell am force-stop com.inspection.demo.glass`，再重新启动眼镜端。
3. 返回同一个任务和步骤，确认读数、结果和说明仍在；如果手机在线，手机端状态应出现“已保存眼镜端草稿”。

判定：草稿恢复不会推进步骤，也不会生成 `step.ack`；只有资料校验通过并收到手机 `step.ack` 后才推进。`step.draft` 失败时，眼镜端仍以本地草稿为准，恢复连接后可再次发送。

连接恢复后，眼镜会自动重发当前本地草稿；手机端应再次显示“已保存眼镜端草稿”，但步骤进度不会改变。

## 5. 证据记录

记录以下信息，才能判断是代码问题还是现场环境问题：

- Glass3 固件版本、手机型号和 Android 版本。
- APK SHA-256、企业 SDK 版本、包名和签名。
- 蓝牙名称、P2P 是否连接、失败时的手机状态文案。
- `adb logcat` 中 `Rokid`、`inspection` 和 `GlassPhotoCapture` 相关日志。
- 任务 ID、步骤 ID、照片 ID，以及失败发生在发送、校验还是 ACK 阶段。

当前工程已提供 Room 本地任务/同步队列、WorkManager 上传骨架和 P2P/蓝牙文件通道；生产化前还要用真实服务端地址验证 HTTPS、认证、幂等响应和失败重试，并用 Glass3 真机验收文件通道。本清单只覆盖第一条双端现场链路。

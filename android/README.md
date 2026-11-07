# Android 开发入口

这里是 Glass3 企业版的 Android 工程目录，包含 `protocol`、`phone-app` 和 `glass-app` 三个模块。当前本机已安装 JDK 17、Android SDK 34、Gradle Wrapper 和 `adb`；Android Studio 与真实 Glass3 设备仍待接入。协议、边界和启动顺序见上级的 [ANDROID_ROKID_ADAPTATION.md](../ANDROID_ROKID_ADAPTATION.md)。

## 当前状态（2026-11-06）

- Web 四号楼巡检流程已接入；Android 手机端、眼镜端和共享协议已完成本机联调准备。
- 最新主机回归通过：协议 **41/41**、手机端 **10/10**、双端 lint、Debug/Release 构建、现场脚本演练和候选包校验全部通过。
- 候选包在 `../artifacts/rokid-rc-20261106/`；四个 APK 的 SHA-256 以其中的 `SHA256SUMS` 为准。
- 当前仍有四项现场条件未满足：企业 AK/SK、正式 HTTPS 报告地址、企业签名、真实 Android 手机与 Glass3。Release 目前是 unsigned，不能当正式发布包。
- 手机和眼镜之间的 Rokid 客户端编号可在 `local.properties` 用 `rokid.clientId` 配置，默认是 `InspectionGlass`；两端必须使用同一个编号。
- 安装、诊断和预检脚本会优先使用 PATH 中的 `adb`，找不到时自动读取 `local.properties` 的 `sdk.dir/platform-tools/adb`；现场电脑不必先手工修改 PATH。
- `adb devices` 当前没有真实设备；本机通过的是代码、构建和离线演练，不能替代 Glass3 现场验收。

## 推荐起点

先运行官方仓库的双端示例，再把业务替换进去：

<https://github.com/RokidSuuport/glass3_sdk_demo/tree/main/scenario/dual-device-ai-inspection-report>

拿到现场设备前可先运行只读环境检查：

```bash
cd /Users/mao/projects/inspection-demo/android
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
./device-preflight.sh
```

它只报告企业凭证是否存在，不会打印 AK/SK、token 或签名密码；脚本在未设置 `JAVA_HOME` 时会自动探测 macOS/Homebrew 的 JDK 17。连接现场设备后建议使用 `--require-device --phone PHONE_SERIAL --glass GLASS_SERIAL`，按序列号分别确认手机和 Glass3；只加 `--require-device` 时只能核对设备数量。正式安装前再加 `--require-release`，把未签名 Release 包作为失败。

它已经包含：

- 独立的 `glass-app` 和 `phone-app` Gradle 工程；
- Glass3 SDK 初始化、蓝牙扫描/连接、Wi-Fi P2P 配对；
- 按 Rokid 官方快速开始补齐手机端蓝牙扫描/连接、网络状态和 Wi-Fi Direct 声明，以及眼镜端网络/Wi-Fi 状态声明；手机端只申请连接所需权限，蓝牙广播权限保留在眼镜端；
- 眼镜端相机预览、物理按键/触控和资源释放；
- 手机端消息、照片接收、电量状态和本地报告示例。

## 本项目接入时的改造顺序

1. 当前工程已提供 `android/glass-app`、`android/phone-app` 和 `android/protocol`，不再从自由文本 AI 示例复制起步。
2. `protocol` 已将 `protocol/inspection-protocol.v1.json` 映射成 Kotlin 模型；消息解析不复用示例中的 AI 自由文本协议。
3. 眼镜端已实现 `task.dispatch`、`task.ack`、`task.start`、`step.draft`、`step.complete`，先显示“收到任务”再开始巡检；设备确认会校验资产码和名称/位置确认，末步会要求核对全部记录，并接入官方拍照 API、照片元数据和 JPEG 流回传。
4. 手机端已接收 `photo.upload` 元数据和照片文件/字节流，校验 MIME、尺寸、大小和 SHA-256 后写入 app-private 目录，回传 `photo.ack`，并用 Room 持久化活动任务快照、已完成步骤和照片元数据；文件回调即使被 SDK 改名，也会按 `photoId`/原始文件名严格关联，避免跨任务错绑；手机端和眼镜端已有未完成任务时都不会被新的下发覆盖，拒绝消息会保留原任务和原因；旧版 SharedPreferences 快照会在首次读取时迁移。眼镜端只有收到 `photo.ack` 才把照片计入步骤。
5. 眼镜端在收到 `step.ack` 前不会推进步骤；任务、当前步骤草稿和待发送照片都先同步写入本地，再发送 ACK/元数据，SDK/Activity 重启后会自动恢复并重试。草稿以 `step.draft` 尽力同步到手机；支持显式暂停/继续并保留原步骤，不推进步骤；手机端重复照片和重复步骤按幂等规则处理，并保存完整读数与异常说明。
6. 接入 AK/SK、包名和签名后，再连真实 Glass3 设备做蓝牙/P2P、按键、拍照和断网恢复验收；正式服务端接口仍需联调，照片已优先走官方 P2P 文件通道并保留蓝牙文件/字节流回退。

拿到设备后的逐步验收清单见 [SMOKE_TEST.md](SMOKE_TEST.md)；现场资料和交接顺序见 [FIELD_HANDOFF.md](FIELD_HANDOFF.md)。

无设备时可以直接运行一键主机回归：

```bash
./host-smoke.sh
```

它会执行现场脚本语法检查、环境/APK/目录预检、协议与手机测试、双端 lint、Debug/Release 构建、Release 签名门禁并打印四个 APK 的 SHA-256；不会读取或打印企业凭证。
主机回归最后还会刷新 `artifacts/rokid-rc-20261106/`，重新核对候选包四项 `SHA256SUMS`，并检查候选 README 不含本机绝对路径。
其中 `field-script-smoke.sh` 使用 fake adb 做离线双设备演练，验证手机/眼镜序列号路由、Release 未签名安装门禁、现场一键启用和诊断证据生成，不会连接或修改真实设备。
候选包若尚未安装 Android build-tools，运行同一脚本会明确报告 `SKIPPED`；在源码回归或现场电脑补齐 `aapt` 后才执行完整演练。

检查 Release APK 是否已完成可验证签名可运行：

```bash
./release-check.sh
./release-check.sh --require-signed   # 企业签名后作为发布门禁
./package-release.sh                  # 通过签名门禁后生成正式交付包
./sync-candidate.sh                   # 刷新本地候选包 APK、现场脚本和 SHA256SUMS
```

当前本机 Release 产物为 unsigned，默认检查会给出提醒；`--require-signed` 会在签名缺失时失败。
`package-release.sh` 在签名门禁通过前不会创建交付目录。
`sync-candidate.sh` 只同步已经构建的 APK，不会签名、安装或修改凭证配置；Release 优先选择已签名产物，没有签名时使用 unsigned 产物，并清理候选目录中同模块旧的 Release 文件名；适合每次本机回归后刷新 `artifacts/rokid-rc-20261106/`。

候选交付包运行 `docs/device-preflight.sh` 时，即使交接电脑尚未安装 Android SDK 或 `adb`，也会以提醒结束，便于先离线核对 APK；连接现场设备前使用 `--require-device`，此时缺少 `adb` 或手机/Glass3 任一设备都会严格失败。源码工程仍把缺少 `adb` 视为环境失败。

不要把官方示例源码直接覆盖当前 Web Demo；Web 端仍然保留任务编排、模板维护、报告验收和演示基准。

## 本机环境准备

当前工程使用 JDK 17、Gradle Wrapper 8.6、AGP 8.4.2、Kotlin 2.0.21；Kotlin 2.0.21 与该 AGP 的 lint metadata 版本一致，避免 Kotlin 2.2 产物在 lint 中出现 metadata mismatch。眼镜端需要 Glass3 数据调试线，普通充电线只能充电；Android Studio 识别到设备后再运行：

```bash
cd android
export PATH="/opt/homebrew/share/android-commandlinetools/platform-tools:$PATH"
./gradlew :glass-app:assembleDebug :phone-app:assembleDebug
adb devices
# 用 adb devices 中的真实序列号替换 PHONE_SERIAL / GLASS_SERIAL。
./field-install.sh --phone PHONE_SERIAL --glass GLASS_SERIAL
# 企业签名后的正式包：
./field-install.sh --variant release --phone PHONE_SERIAL --glass GLASS_SERIAL

# 现场推荐：一次完成严格预检、安装和诊断留档
./field-commission.sh --variant debug --phone PHONE_SERIAL --glass GLASS_SERIAL
```

`field-install.sh` 会分别读取设备型号，从 APK 自动读取实际包名，执行非破坏性的 `adb install -r` 并核对包名；不会清除任务、草稿或照片队列。企业登记了自定义包名时，在 `local.properties` 填 `android.phoneApplicationId` / `android.glassApplicationId` 即可覆盖默认值。

双设备连上后，可先运行只读诊断收集现场证据：

```bash
./field-diagnostics.sh --phone PHONE_SERIAL --glass GLASS_SERIAL
```

它会把手机和眼镜的型号、Android 版本、应用安装路径、权限、蓝牙/Wi-Fi/P2P 摘要和最近 300 行过滤日志写入 `../artifacts/field-diagnostics-时间戳/`；不读取或输出 AK/SK、token、签名密码，也不修改设备。

手机端初始化需要企业 AK/SK；这类凭证不要提交到 Git，应通过本地 `local.properties` 或未纳入版本控制的配置注入。可复制 [local.properties.example](local.properties.example) 为 `local.properties` 后填写；示例默认不配置同步地址，不会误发网络请求。

若后端已提供报告接收接口，可在 `local.properties` 增加 `inspection.syncUrl=https://...`，可选填写 `inspection.syncToken`。手机端会在有网络时由 WorkManager 上传队列中的报告，携带 `Idempotency-Key: taskId`；URL 留空时只保留本地队列，不会发起网络请求。请求字段、HTTP 状态语义和幂等要求见 [REPORT_SYNC_CONTRACT.md](REPORT_SYNC_CONTRACT.md)。

当前已验证：

```text
./gradlew projects                         # 通过
./gradlew test                              # 协议 32 项、手机上传 3 项通过
./gradlew :phone-app:compileDebugKotlin \
          :glass-app:compileDebugKotlin    # 通过
./gradlew assembleDebug                    # 两个 Debug APK 通过
./gradlew lint                              # 通过；仅有 SDK/依赖、字符串资源化和设备适配警告
./gradlew :phone-app:assembleRelease \
          :glass-app:assembleRelease       # 两个 Release unsigned APK 通过
```

APK 输出在 `phone-app/build/outputs/apk/debug/` 和 `glass-app/build/outputs/apk/debug/`；`build/` 已加入忽略规则，不会进入源码交付。

未配置签名时，Release 构建会输出 `*-release-unsigned.apk`；配置企业签名后，Gradle 会输出 `*-release.apk`。企业包名、签名证书和签名密码仍需按 Rokid/现场要求注入后才能安装到生产设备；本机只验证了 Release 编译、资源合并和 lint，不把 unsigned APK 当作最终交付包。

签名配置模板也已列在 [local.properties.example](local.properties.example) 的 `android.signing.storeFile/storePassword/keyAlias/keyPassword` 四项中；四项必须同时填写，密码不会写入源码或候选包。

2026-11-06 本机产物（照片失败回退与临时文件持久化改动后）：

```text
glass-app/build/outputs/apk/debug/glass-app-debug.apk
SHA-256 1a066ec00ead0ccf952d45329a7006fa13e8ad9a06cedbc72cb073c74cc223b3
phone-app/build/outputs/apk/debug/phone-app-debug.apk
SHA-256 340416ea63d05a4b71355b5c37b6c8fa7bb8cc9e639d9eb162140e1b7a8213b4
```

Release unsigned 产物（2026-11-06）：

```text
glass-app/build/outputs/apk/release/glass-app-release-unsigned.apk
SHA-256 b41241b16f8743bcd150c08dae66eabfd33d4610eaa22efbde884489574f3a2b
phone-app/build/outputs/apk/release/phone-app-release-unsigned.apk
SHA-256 59dfe80eef57a9350587bb59f7e1eba499ab67526c3ab4f20598a6066075c7b3
```

本机使用 Homebrew 的 JDK 17 和 Android SDK 34 构建；`adb` 位于 `/opt/homebrew/share/android-commandlinetools/platform-tools/`，可按上面的命令加入 PATH。`android/local.properties` 只在本机提供 SDK 路径并已忽略，不包含企业 AK/SK。APK 已用 `aapt dump badging` 核对包名、版本和 targetSdk 34；当前 `adb devices` 没有连接设备，尚未安装到真实 Glass3 或手机，真机链路仍需数据调试线、企业凭证和设备固件配合。

手机 APK 内置 `assets/building4-catalog.json`，内容与 Web 端 `seed/building4-catalog.json` 完全一致（12 套模板、30 个对象）。初始化 SDK 后可选择模板、对象、日期、时段和班次再下发；眼镜端会显示日程并按字段要求阻止不完整提交。

Web 与手机端之间现在也支持离线任务文件桥接：Web 工作台在已有任务时点击“导出 Android task.dispatch”，生成协议版本 1 的任务 JSON；手机端点击“导入 Web task.dispatch JSON”选择该文件。手机端会先用共享协议解码、校验并写入 Room，再立即下发或在 Glass3 连接恢复后自动重试。导入不会读取浏览器报告 JSON，也不会覆盖内容不同的同一 `taskId`。

任务下发后，手机端可点击“导出本地巡检报告 JSON”，报告写入 app-private 的 `files/reports/`，包含任务快照、日程、每个步骤的完整读数/结果/说明、故障发现时间、步骤确认时间以及已校验照片元数据。活动任务快照保存在 app-private 的 Room 数据库 `inspection-phone.db` 中，提交 ACK 前同步落盘；后续接入服务器时可直接复用 `InspectionReport` 结构。

任务完成后，报告会以 `taskId` 幂等地写入手机端 Room 离线同步队列；队列保留待发送、发送中、失败、拒绝和已成功状态，并按 30 秒起步、最多 6 小时的退避时间计算下一次重试。WorkManager 上传骨架已经接入网络约束、HTTPS 校验、幂等请求头和认证 token；进程在上传中断时，超过 5 分钟的发送中状态会自动恢复为可重试。具体服务端字段与认证规则确定后即可联调。Room 当前保存活动任务、任务历史和多条同步记录，手机端可查看本地任务记录。

最新本机回归（2026-11-06）：手机端任务 JSON 同时支持 `VIEW/data`、`SEND/EXTRA_STREAM` 和 `ClipData` URI 导入，均复用 `TaskDispatchFileParser`；照片归属校验失败时界面显示未关联状态，不再误报已保存；手机端和眼镜端的入站坏 JSON/错误 payload 不会冒泡到 Rokid SDK 回调线程；手机 Activity 的 UI 入站边界会隔离坏 payload 和回调异常；眼镜端强制校验 `task.dispatch` 的协议版本、`commandId`、`taskId` 以及 `commandId=cmd-dispatch-{taskId}` 身份关系，并校验 `task.ack/start/pause/resume`、步骤/照片 ACK 的 commandId 与结果身份一致；步骤完成和照片上传的 commandId 由共享 `ProtocolCommandIds` 生成，手机端会拒绝不匹配的入站消息；新增一条完整五步巡检流程回归，覆盖设备确认、顺序、读数阈值、照片归属和最终复核；眼镜进程重启后会从持久化照片 outbox 恢复 photoId 到步骤的归属映射，再重试当前任务的照片；无活动任务时不重试旧照片；新增 [local.properties.example](local.properties.example) 作为不含真实凭证的配置模板。`android/host-smoke.sh` 通过，协议测试 **35/35**、手机上传测试 **3/3**、双端 lint 和 Debug/Release 构建通过，预检为 0 个失败、4 个外部条件提醒。当前四个 APK 均已同步到候选包，并以候选包内 `SHA256SUMS` 完成校验。由于 APK 打包时间会影响 ZIP 哈希，具体值以该清单为准。

任务 JSON 边界校验（2026-11-06）：手机导入和眼镜接收现在共同检查首末步骤、内部检查项顺序、模板对象归属、日期/时段/班次和字段范围；协议测试 **32/32**、手机上传 **3/3**，`host-smoke.sh` 通过。

手机端文件解析已独立为 `TaskDispatchFileParser`，在写入 Room 前拒绝错误消息类型、空 `taskId`、非法步骤结构、不支持的协议版本和缺失 `commandId`；对应手机端测试 **6/6**。

手机端还注册了 `application/json` / `text/plain` 的文件打开入口，现场可以从系统文件管理器直接打开 Web 导出的任务文件；文件报告或其他 JSON 会在解析阶段拒绝。

本轮软件回归还确认 Web 生产构建端到端测试 **32/32** 通过；候选交付包已同步最新手机/眼镜 APK。历史段落中的较早测试计数保留为当时实际记录，最终以本段与 `EXECUTION_PLAN.md` 最新条目为准。

照片传输也保留可恢复边界：眼镜端字节流失败（包括 SDK 回调 `onSuccess(false)` 或同步抛异常）会保留 outbox 并自动重试；手机端若文件先于 `photo.upload` 元数据到达，会先复制到 app-private 的 `pending-photo-files/` 再等待元数据，避免依赖 SDK 临时缓存；同一 `photoId` 的重复文件回调会串行处理，避免临时文件互相覆盖。手机完成校验并发送 `photo.ack` 后才清理对应临时状态；负 ACK 或任务归属失败会删除对应照片文件，文件先到的临时目录按 24 小时和 64 MB 上限清理。

连接恢复重试（2026-11-06）：手机端在经典蓝牙或 Wi-Fi P2P 控制通道恢复后，会从 Room 活动任务快照自动重试未完成的 `task.dispatch`；同一 `taskId` 依赖协议幂等，不会清空眼镜端草稿、步骤或照片队列，已完成任务不会重发。最近一次全量验证协议 30/30、手机上传 3/3、双端 lint 无 Error、Debug/Release unsigned 构建通过。

眼镜 SDK 断线恢复（2026-11-06）：`RokidGlassTransport` 在服务断开、绑定失效、消息服务尚未就绪或监听注册异常时自动延迟重绑；SDK 重新就绪后会补发当前任务的 `task.ack` 以及持久化的开始/暂停状态，再继续照片 outbox 和未确认步骤的重试。控制消息使用既有幂等 commandId，不会重复创建任务或推进步骤。

本轮 APK SHA-256：手机 Debug `882a85f6edba3433ceda56bba2643702671a70345bb1c76abde780b77143cf82`，眼镜 Debug `1a066ec00ead0ccf952d45329a7006fa13e8ad9a06cedbc72cb073c74cc223b3`；手机 Release unsigned `738b4b8a2c17fe65533e9c1c02fa72c1d56ada22f437e52adf6b05facff6e95f`，眼镜 Release unsigned `b41241b16f8743bcd150c08dae66eabfd33d4610eaa22efbde884489574f3a2b`。

P2P 状态文案与连接恢复重试复核（2026-11-06）：最新全量构建与设备预检通过，预检结果为 0 个失败、4 个外部条件提醒。最新 APK SHA-256：手机 Debug `a0544f9f81f01c4f59b3a2fa7b9bc5bc89a234e8a2bb682cac5346bfe0160c24`，眼镜 Debug `1a066ec00ead0ccf952d45329a7006fa13e8ad9a06cedbc72cb073c74cc223b3`；手机 Release unsigned `35e1a316a3b4f3c9145bccfb7cf118c70501f5adedf5618ceee31593da0b3166`，眼镜 Release unsigned `b41241b16f8743bcd150c08dae66eabfd33d4610eaa22efbde884489574f3a2b`。

ACK 重试一致性（2026-11-06）：`step.ack` 与 `photo.ack` 的命令身份现在包含 accepted/rejected 结果。手机暂时保存失败先返回负 ACK、重试成功返回正 ACK 时，眼镜端重放账本不会把正 ACK 当成旧负 ACK；重复同一结果仍按幂等规则去重。手机端同一 `taskId` 若收到内容不同的任务也会拒绝，避免相同编号覆盖任务快照。

本轮最新验证：协议测试 **31/31**、手机上传 **3/3**，双端 lint 无 Error，四个 APK 构建成功；`aapt dump badging` 确认为手机 `com.inspection.demo.phone`、眼镜 `com.inspection.demo.glass`，compile/target SDK 34。APK SHA-256：手机 Debug `d6da50d341077dc79393149b7191796fed8d6ccb33ced0fe960fcd572a9d3072`，眼镜 Debug `68c4682a8c7cc6c6a4ce39ae97c98a48a68cad5f36fcc6fdc6997e65ac75efcc`；手机 Release unsigned `8b3268702ed9f6092b072f147cc3c5afffd4d61a01cee3d8579765bd31e1c6e2`，眼镜 Release unsigned `4a239d7acbddf972dbdc8255767636ce2b0a3b31ff1efdf994b3578ef33e2a14`。

最新补丁复核（2026-11-06）：P2P 查询同步异常会清除连接中状态并允许再次建立连接；初始化后会检查已存在的蓝牙/P2P 链路。全量验证仍为协议 **31/31**、手机上传 **3/3**、双端 lint 无 Error、Debug/Release unsigned 构建成功，设备预检 0 个失败、4 个提醒。最新 APK SHA-256：手机 Debug `ab4858431537153adc05201fdedd562d55124d6fd6a4b1b966e70ec86ff469fd`，眼镜 Debug `68c4682a8c7cc6c6a4ce39ae97c98a48a68cad5f36fcc6fdc6997e65ac75efcc`；手机 Release unsigned `c94e4f9c290a0d8a4262ed0b1c4871d38fdfbe071bc3b3827dc9b83554d8a5a4`，眼镜 Release unsigned `4a239d7acbddf972dbdc8255767636ce2b0a3b31ff1efdf994b3578ef33e2a14`。

照片 ACK 崩溃窗口复核（2026-11-06）：眼镜端在记录 ACK 重放前同步删除照片 outbox，避免重启后重复文件被重放账本吞掉而永久残留。全量验证协议 **31/31**、手机上传 **3/3**、双端 lint 无 Error、四个 APK 通过；最新 APK SHA-256：手机 Debug `ab4858431537153adc05201fdedd562d55124d6fd6a4b1b966e70ec86ff469fd`，眼镜 Debug `284cc10e262e6dac5e442a194a588287a161d7f4c95e41b265369d115b6e6bcd`；手机 Release unsigned `c94e4f9c290a0d8a4262ed0b1c4871d38fdfbe071bc3b3827dc9b83554d8a5a4`，眼镜 Release unsigned `60a5ee18d3787cd4df58932088d15861e56ac6593736fcb7ac8760f6c83ec304`。

照片 ACK 持久化失败回退（2026-11-06）：眼镜端只有在 outbox 删除同步提交成功后才把 ACK 交给重放账本；删除失败会保留待发送项并允许下一次 ACK 重试。全量验证协议 **31/31**、手机上传 **3/3**、双端 lint 无 Error、设备预检 0 个失败、4 个提醒。最新 APK SHA-256：手机 Debug `ab4858431537153adc05201fdedd562d55124d6fd6a4b1b966e70ec86ff469fd`，眼镜 Debug `61d0ab161a0ec91020cc14919470200ddf1ac587f71ba3c36f8c43777abd7b88`；手机 Release unsigned `c94e4f9c290a0d8a4262ed0b1c4871d38fdfbe071bc3b3827dc9b83554d8a5a4`，眼镜 Release unsigned `fe0f0949522b8bf639f092aac1188e4045bd4d503e2840f6deeb5263c11ac08f`。

官方 Demo 独立构建与 Glass3 交互补齐（2026-11-06）：已在本机用 JDK 17 独立构建官方 `dual-device-ai-inspection-report` 的 `glass-app` 和 `phone-app`，两者均 `BUILD SUCCESSFUL`。当前工程眼镜端补入 `DPAD_LEFT/RIGHT` 镜腿滑动焦点导航，并在巡检页面启用 `FLAG_KEEP_SCREEN_ON`；滑动不会绕过步骤校验。对照记录见 `../ANDROID_ROKID_OFFICIAL_AUDIT.md`。本轮全量验证协议 **31/31**、手机上传 **3/3**、双端 lint 无 Error、Debug/Release unsigned 均成功；最新 APK SHA-256：手机 Debug `ab4858431537153adc05201fdedd562d55124d6fd6a4b1b966e70ec86ff469fd`，眼镜 Debug `615b5eda730d9c5639f79d7d4b8a48b7b71366e9a6fa2b48c3af0c092875f201`；手机 Release unsigned `c94e4f9c290a0d8a4262ed0b1c4871d38fdfbe071bc3b3827dc9b83554d8a5a4`，眼镜 Release unsigned `38babc0be89ceda6bbad72a05da139184aee0959b4c9acfc9c51e8dfac5a9b98`。

现场预检增强（2026-11-06）：`device-preflight.sh` 现在还会从 APK 读取手机/眼镜包名与 min/target SDK，并用 SHA-256 比对 APK 内的 `building4-catalog.json` 和 Web seed；因此即使没有连接设备，也能先拦截装错 APK 或过期目录。显式提供 `--phone` / `--glass` 时会逐台核对设备状态；当前运行仍为 0 个失败、4 个外部条件提醒。

候选交付包（2026-11-06）已整理到 `../artifacts/rokid-rc-20261106/`，包含四个 APK、`SHA256SUMS`、现场安装/诊断脚本和 Smoke Test。该目录中的四个 APK 已逐一做 checksum 核对；Release 包仍是 unsigned，必须完成企业签名后才能作为正式发布包。

Glass SDK 异步就绪时序复核（2026-11-06）：眼镜端现在以官方 `IClientCallback.onReady()` 为唯一业务就绪信号；`registerClient()` 返回后只注册消息监听，不会提前触发照片重试或任务恢复。SDK 服务断开/绑定失效时会清除就绪标志，重新绑定后再通知一次。

最新 Glass SDK 时序修复产物（2026-11-06）：手机 Debug `ab4858431537153adc05201fdedd562d55124d6fd6a4b1b966e70ec86ff469fd`，眼镜 Debug `8886132d48328fac106acc6f9551a8afc4a97764cac7441a837129a5accf197b`；手机 Release unsigned `c94e4f9c290a0d8a4262ed0b1c4871d38fdfbe071bc3b3827dc9b83554d8a5a4`，眼镜 Release unsigned `b86f0fbf1969d8dea36ca908bcb1df9141ced88ae33c72346bdc75ded19ee06b`。候选交付包已同步这些产物。

候选包交接增强（2026-11-06）：候选模式的 `device-preflight.sh` 在没有 `adb` 时只提示，不阻止离线核对；现场使用 `--require-device` 仍会严格失败。`field-diagnostics.sh` 生成的 `artifacts.txt` 会记录 `adb` 版本、两份 APK SHA-256 和实际包名，便于把设备日志与准确产物对应起来。

干净构建复核（2026-11-06）：发现 AGP 8.4.2 的 lint 会对 Kotlin 2.2 产物打印 metadata mismatch，已将工程 Kotlin 工具链调整为 2.0.21，并用 AndroidX `ContextCompat.registerReceiver(..., RECEIVER_EXPORTED)` 修复 Glass3 实体按键广播在 Android 14 的 lint 错误。清理 Gradle 输出后重新执行 `host-smoke.sh`，无 Kotlin metadata 错误、无 lint 错误，Debug/Release 构建和现场脚本演练均通过。

重复步骤冲突回归（2026-11-06）：手机端对已经落盘的步骤只接受完全相同的重放 payload；相同步骤编号/版本但读数、结果或说明不同的消息会返回负 ACK，不会覆盖原记录。协议测试增至 **37/37**，候选包已同步。

模板字段边界回归（2026-11-06）：步骤完成消息中的 `readings` 只能包含任务模板声明的字段；额外键值会被拒绝，避免手工改写任务 JSON 后把未定义数据写入报告。

草稿字段边界回归（2026-11-06）：`step.draft` 在写入手机 Room 前也执行模板字段校验，非法读数不会留在中间草稿里；协议测试增至 **38/38**。

任务完成通知闭环（2026-11-06）：手机在所有步骤已由 Room 持久化后发送 `task.complete`；眼镜端验证固定 commandId 和任务归属，并把“手机已确认全部步骤保存”同步写入本地快照。连接恢复时手机会对已完成任务幂等重发该通知，眼镜重启后也能恢复终态显示。最新回归为协议 **39/39**、手机 **7/7**，`android/host-smoke.sh` 和候选包四项 SHA-256 校验均通过。当前候选包哈希以 [artifacts/rokid-rc-20261106/SHA256SUMS](../artifacts/rokid-rc-20261106/SHA256SUMS) 为准。

最终完成消息的 payload 也有类型门禁：眼镜端只接受存在且为 JSON 布尔值的 `accepted`，缺失或字符串值不会推进完成状态。最新回归为协议 **40/40**、手机 **7/7**；候选 APK 哈希以候选包内 `SHA256SUMS` 为准。

眼镜 SDK client 就绪后会通过控制通道每 30 秒发送 `device.heartbeat`，手机显示实际连接状态、电量和 App 版本；心跳不等于任务或报告同步成功。协议样例见 `protocol/device-heartbeat.example.json`，候选包和正式包会把样例复制到 `docs/`。

2026-11-06 心跳边界与手机可观测性补丁：眼镜端无法读取系统电量时跳过本次心跳，不把未知状态伪装成 `0%`；手机端独立显示眼镜心跳，连续 90 秒未收到时标记超时。最新候选 APK SHA-256 以 [`artifacts/rokid-rc-20261106/SHA256SUMS`](../artifacts/rokid-rc-20261106/SHA256SUMS) 为准：手机 Debug `28c5104761719bdf7f9e2b3296c9b42dfdcee2d5678f553c33e65f7ac3c5f199`，眼镜 Debug `12e7201a10fa2d48604e2aa77668abee7661c346f9c862ae557acd37bd75d787`；手机 Release unsigned `539ca058a4402b541a600a9553b5b594180cb2af6777d1d67088a56aed098be2`，眼镜 Release unsigned `b37e47146365b22577ac4d3d735221c28c7170df4409b012876d2702313114a4`。

心跳 payload 严格类型校验也已纳入：版本和连接必须为字符串，电量必须为 0–100 的整数；候选包最新哈希以同一 `SHA256SUMS` 为准，Release 仍为 unsigned。

最新交付复核（2026-11-06）：对照官方 `RokidSuuport/glass3_sdk_demo` 后，眼镜端物理按键广播处理补上 `abortBroadcast()`，补齐官方眼镜端的蓝牙、附近 Wi-Fi、定位和常亮权限声明，并在 SDK 监听注册失败时清理半成功监听后再重试；手机端按官方示例移除不必要的蓝牙广播权限申请；主机回归增加源码与 APK 门禁并通过。最新 APK 哈希以候选包 `SHA256SUMS` 为准；本轮 `android/host-smoke.sh` 仍为协议 **41/41**、手机端 **10/10**、双端 lint/构建/现场脚本通过；真实手机、Glass3 和企业发布条件仍需现场完成。

该补丁后的手机端单测为 **10/10**（Debug/Release 各 10 项），协议单测为 **41/41**（Debug/Release 各 41 项）；最新候选 APK：手机 Debug `69a9847f1138375e8e76dd0f0b9a4605bbbe99a83b3388cdab4c5e4d610bd74c`，眼镜 Debug `eeed7dd37fb8cb34631299447c045247b4d15296bf353b7d17692d5431bf557a`，手机 Release unsigned `fdbc3ac7a6e479393f27193d8bb23073daf598ae6fb4d1205106213de3fe3375`，眼镜 Release unsigned `fabf249befb5ee190e478e7c44ae2cba6e4a6350835ff6d1e9d14508ca1f6f2`。

2026-11-06 手机权限收敛后的当前候选 APK：手机 Debug `cb1408f8744ee96a17f24a299ac116f70949c0221de1dcbe1fb5cc34f4a0383d`，眼镜 Debug `29f621915d6c9cb369494388b3d2b0b2dd02a95f6a26231b4c0ec9e05bc92160`，手机 Release unsigned `e408f42947be1c21fec398c7c59d99e5fafdd67f8f2d4ca3d84849b71b0872d7`，眼镜 Release unsigned `7188ac913d1e92efff1cca34f19711bfa053c6a5c9ea79435b298342ff83bb50`；最终以候选包 `SHA256SUMS` 为准。

# inspection-demo 的 Rokid Android 适配方案

本文把现有 Web Demo 与 Rokid Android 端的边界固定下来，作为 Android 双端工程的技术基线。当前项目仍保留 React + TypeScript + Vite 的浏览器 Demo，同时已提供 `android/` 下的 Glass3 企业版手机端、眼镜端和协议模块；源码构建通过不等于已经连接真实眼镜。

## 当前状态（2026-11-06）

本机可交付的 Android 候选包已经形成：协议测试 **41/41**、手机端测试 **10/10**，双端 lint、Debug/Release 构建、现场脚本演练和四个 APK 的 SHA-256 校验均通过。手机清单已覆盖 Android 12/12L 的 Wi-Fi P2P 定位授权要求，候选现场文档也已去除开发机绝对路径。

目前还不能写成“Glass3 真机已验收”：真实手机与眼镜、企业 AK/SK、正式签名、蓝牙/P2P 实连、实体按键、相机回调、照片文件传输、断线恢复和正式报告服务仍需现场完成。

## 先确认设备路线

官方当前资料把设备分成不同路线，不能只按“Rokid 眼镜”选 SDK：

| 实际设备 | 适配路线 | 当前结论 |
| --- | --- | --- |
| Rokid Glass3 企业版 | Sprite Enterprise / Glass3 Enterprise，眼镜端 `glass3.open.sdk`，手机端 `phone.sdk` | 本项目最匹配，先按这条路线设计 |
| 普通 Rokid Glasses / Bolon AI Glasses | YodaOS-Sprite 的 CXR-L/CXR-S/Bare-metal | 需要另一套平台权限和示例，不能套用 Glass3 AAR |
| Rokid AR / AR Lite / Station 2 系列 | YodaOS-Master、UXR/OpenXR/Unity 等 | 需要重新评估，不能直接使用本方案 |

在购买或申请 SDK 前，必须确认型号、是否企业版、眼镜固件、是否有配套 Android 手机，以及是否能取得企业 SDK 的 AK/SK。手机端官方初始化参数包含 `UserAuthInfo(appId, secret)`；没有凭证时可以先做无设备的协议和 UI 开发，但不能宣称已经打通手机 SDK。

## 推荐的三端结构

```text
管理端 Web（保留当前 React Demo）
        │ HTTPS / WebSocket
        ▼
业务后端（任务、账号、报告、上传、审计）
        │ HTTPS
        ▼
手机 Android App（phone.sdk、Room、WorkManager）
        │ Rokid 蓝牙 / Wi‑Fi P2P
        ▼
眼镜 Android App（glass3.open.sdk、任务 UI、相机、按键）
```

手机是任务同步、网络和眼镜连接的中继；眼镜端要能在断网时继续当前任务，把未上传的照片和结果放入本地队列。管理端不直接连接眼镜，也不把 React 页面塞进眼镜 WebView。

当前已补齐一个不依赖后端的联调入口：Web 管理端把活动任务快照映射为协议兼容的 `task.dispatch` JSON，手机端通过系统文件选择器导入。手机端完成类型、任务编号、模板/步骤/字段和任务守卫校验后再持久化；有可用 SDK 链路就立即发送，没有链路则等待恢复。报告导出 JSON 与任务下发 JSON 是两个不同格式，前者用于归档，后者用于执行。

官方 `RokidSuuport/glass3_sdk_demo` 当前已经提供了一个很接近本项目的 `scenario/dual-device-ai-inspection-report`：独立 `glass-app` 和 `phone-app`，眼镜负责现场采集和识别，手机负责蓝牙/P2P、记录和报告。它可以作为 Android 工程的起始参考；本项目的差异是把“自由识图结果”改成固定模板、步骤、读数和异常状态，并让管理端 Web 继续作为任务编排端。

## 现有代码如何映射到 Android

| 当前 Web 代码 | Android 对应物 | 迁移规则 |
| --- | --- | --- |
| `Template`、`StepDefinition`、`ReadingField` | Kotlin `data class`，协议 JSON 的模板快照 | 字段名、步骤 ID、单位和阈值保持不变 |
| `Task`、`StepRecord`、`Draft` | Room 实体 + 领域模型 | 保存模板快照，不重新读取后来修改的模板 |
| `Command` | Kotlin `sealed interface Command` | 保留 `commandId`、`taskId`、`expectedStepId` 和幂等语义 |
| `applyCommand`、`validateStep` | 纯 Kotlin domain 层 | UI 和 Rokid SDK 都不能直接改任务状态 |
| `InspectionService` | `TaskUseCase` + `TaskRepository` | 事务先写本地，再投递同步队列 |
| `IndexedDbRepository` | 手机端 Room；眼镜端 Room/DataStore + app-private 文件 | 照片二进制写文件，数据库只存元数据和路径 |
| `EvidencePhoto` | `PhotoEntity` + 文件缓存 | `photoId` 与 `taskId/stepId` 永久绑定，上传可重试 |
| React 眼镜模拟区 | 眼镜端单 Activity 任务界面 | 大字号、单步聚焦、按键/侧触控操作 |

不要共享 React 组件。共享的是 `protocol/inspection-protocol.v1.json` 以及未来生成的 Kotlin/TypeScript 模型。

## 协议和通道

协议样例在 `protocol/`；`task-complete.example.json` 是手机完成全部步骤后发给眼镜的最终通知样例：

- `task.dispatch`：手机向眼镜下发模板快照、设备、人员和步骤。
- `task.ack`：眼镜确认收到并持久化任务。
- `task.start`：执行者在眼镜确认任务和日程后开始巡检。
- `task.pause` / `task.resume`：保存当前草稿后暂停或从原步骤继续，不能推进或跳过步骤。
- `step.draft`：保存当前读数、状态和说明；可频繁发送，服务端可合并。
- `step.complete`：提交当前步骤，必须携带 `expectedStepId`、`revision` 和 `commandId`。
- `photo.upload`：先发照片元数据，再通过 P2P 文件通道传二进制；以 `photoId` 幂等。
- `step.ack` / `photo.ack`：手机返回保存或失败结果；照片校验失败也必须返回负 `photo.ack`，让眼镜把不可重试文件结束掉。
- `task.complete`：手机确认全部步骤已落盘；`commandId` 固定，重复完成消息可安全重试。
- `device.heartbeat`：眼镜就绪后每 30 秒发送连接、电量和 App 版本，不能把它伪装成业务任务同步成功；如果系统暂时无法读取电量，本次心跳会跳过并等待下一周期，避免把未知值显示成 `0%`；样例见 `protocol/device-heartbeat.example.json`。

控制消息可以用文本通道：官方手机端示例提供 `sendTextMessageByP2P` 和 `sendTextMessageByClassicBT`。图片、录像和 APK 等大文件优先使用 P2P 文件操作；官方示例提供 `getFileOperater().sendFile(...)`。因此本巡检建议：小 JSON 控制消息优先蓝牙或 P2P，照片使用 P2P 文件传输，网络上传由手机端单独处理。

每条命令都要经过以下检查：

1. `messageId` / `commandId` 是否已经处理；重复消息只返回已有结果。
2. `taskId`、`expectedStepId` 和本地 `revision` 是否匹配；过期动作不能推进新步骤。
3. 照片文件是否已落盘并校验 MIME、尺寸、大小和 SHA-256；成功后才允许步骤完成。
4. 状态变更、审计事件和待同步记录是否在同一 Room 事务提交。

## Glass3 Enterprise 的 Android 接入点

官方快速开始当前给出的版本基线为：JDK 17、Gradle Wrapper 8.6、AGP 8.4.2、Kotlin 2.2.0；SDK 为眼镜端 `com.rokid.security:glass3.open.sdk:2.2.2-E`，手机端 `com.rokid.security:phone.sdk:2.2.2-E`。Maven 仓库是：

```gradle
maven { url = uri("https://maven.rokid.com/repository/maven-public/") }
```

依赖写法：

```gradle
implementation("com.rokid.security:glass3.open.sdk:2.2.2-E") {
    exclude(group = "org.slf4j")
}
```

手机端只需把坐标替换为 `com.rokid.security:phone.sdk:2.2.2-E`。若出现 native 冲突，按官方说明对 `libr2aud.so` 配置 `pickFirst`。AAR 声明的最低 API 是 24；官方当前眼镜 Demo 使用 minSdk 29，手机 Demo 使用 minSdk 24，工程应以实际 Demo 和固件验证结果为准。

关键 SDK 调用关系（客户端编号默认是 `InspectionGlass`，企业登记不同编号时通过 `local.properties` 的 `rokid.clientId` 同步覆盖两端）：

```text
眼镜端：GlassSdk.bindSecurityService(context, callback)
       → GlassSdk.registerClient("InspectionGlass", callback)
       → GlassSdk.getGlassMessageService()/getGlassMediaService()

手机端：PSecuritySDK.initSDK(EngineParam(...))
       → getWifiP2PClientService()/getClassicBlueToothClientService()
       → getMessageService()
```

眼镜端注册的 `clientId` 必须与手机端发送消息使用的 clientId 相同。眼镜接收消息参考 `GlassSdk.getGlassMessageService().setMessageListener(...)`，接收文件使用 `glassFileOperater` 的 `FileReceiveListener`。Activity 销毁时必须解除监听、停止媒体并释放 SDK。

## 眼镜交互如何承接现有五步流程

首版眼镜端只保留一个当前步骤，避免把管理端表格完整搬到近眼显示：

1. **收到任务**：显示任务名称、设备名称和“开始巡检”。收到后先写本地，再显示成功。
2. **确认设备**：显示设备码和位置；支持扫码结果或手工输入，确认后完成。
3. **检查项**：显示标题、操作说明、读数输入、正常/异常/关机/待机/不涉及；异常必须输入说明；来自原表的异常步骤可填写故障发现时间。
4. **拍照**：物理按键或侧触控触发拍照；相机回调写入 app-private 文件并生成 `photoId`，失败可重拍。
5. **提交**：显示已完成项、照片数和异常数；确认后提交最后一步，手机端再上传报告。

Glass3 官方示例将两类输入分开处理：物理按键使用系统广播（单击 `com.rokid.glass3.action.button.CLICK`、双击 `...DOUBLE_CLICK` 等），镜腿侧边触控使用当前 Activity 的 `KeyEvent`（单击 `KEYCODE_ENTER`，前后滑动 `KEYCODE_DPAD_RIGHT/LEFT`，返回 `KEYCODE_BACK`）。因此业务层应抽象为 `Confirm`、`Next`、`Previous`、`Back` 四个动作，不能把广播 Action 写进巡检状态机。

相机接入先参考官方“眼镜端 SDK 拍照录像”示例；如果要把相机画面与 UI 混流，再参考 CameraShare 示例。进入后台、离开页面和任务完成时都要释放相机/录像资源。

## 第一条可验收链路

拿到设备后按下面顺序开发，不要先接入 135 个检查项：

1. 用 Glass3 数据调试线连接眼镜；普通充电线不能让 Android Studio 识别设备。用投屏工具观察眼镜画面。
2. 原样运行官方 Demo，完成手机初始化、蓝牙配对和 P2P 连接。
3. 新建 `android/` 工程，先只做手机端与眼镜端两个 module；不要把 Android 工程嵌到 Vite 工程里。
4. 下发 `protocol/task-dispatch.example.json`，眼镜端显示“收到任务”，并回 `task.ack`。
5. 眼镜端单击按键完成一个虚拟步骤，回传 `step.complete`；手机端收到后显示结果。
6. 接入一张照片：拍照 → 文件落盘 → `photo.upload` → 手机端接收并校验 → `photo.ack`。
7. 拔掉网络但保持手机和眼镜连接，完成当前步骤；恢复网络后由 WorkManager 上传，验证重复上传不会生成重复记录。

这条链路通过后，继续接入正式报告接口、认证规则和远程管理；手机端活动任务快照与离线同步队列已经使用 Room，WorkManager 可靠同步骨架也已接入。报告上传的请求体、状态码和幂等规则已固化在 `android/REPORT_SYNC_CONTRACT.md`。读数阈值、异常流程和四号楼 12 套模板已经进入当前 Android 协议与手机 APK。当前手机端已经能把本地任务快照导出为 `InspectionReport` JSON，并在任务完成后幂等写入带退避重试状态的 Room 同步队列，作为后续服务端上传的稳定输入。

官方双端查验示例已经验证了几项可复用实现：手机端 `MainPhoneActivity` 负责权限、`PSecuritySDK` 初始化和连接状态；`DeviceLinkerManager` 负责蓝牙/P2P、消息监听和照片流；眼镜端 `GlassSdkUtils` 负责绑定系统服务和注册 `clientId`；`IdentificationActivity` 负责预览、按键/离线语音触发、资源释放。我们的第一版应沿用这几个边界，把 `AiVisionStore` 换成任务/步骤/照片队列，把 `AiVisionRelayPayload` 换成 `protocol/` 中的消息。

当前仓库已经把这条最小链路写成可构建源码：`android/protocol` 提供 Kotlin 协议模型、四号楼字段/阈值/日程模型、开始巡检与设备/最终复核校验、草稿恢复、暂停/继续状态、照片完整性校验、照片与步骤归属校验、报告模型、离线同步队列状态转换和 JSON 编解码，并有设备无关的完整消息流程测试；`android/phone-app` 提供 SDK 初始化、权限、Glass3 扫描连接、自动/手动 P2P 建连、模板/对象/日期/时段/班次选择、四号楼目录读取、`task.dispatch`、照片 MIME/尺寸解码、大小和 SHA-256 校验、P2P/蓝牙文件接收、落盘和 `photo.ack`，以及 Room 活动任务/任务历史/同步队列、本地报告 JSON 导出、完成后报告入队和 WorkManager 上传骨架；`android/glass-app` 提供 SDK 绑定、任务接收、收到任务后显式开始、按协议动态渲染设备确认/字段/结果/日程、`task.ack` / `task.start` / `task.pause` / `task.resume` / `step.draft` / `step.complete` / `step.ack`、本地任务草稿与照片待发送队列恢复、侧边触控按键、物理按键广播、官方拍照 API、P2P/蓝牙文件优先和字节流回退。手机端和眼镜端都拒绝用新任务覆盖未完成快照；眼镜只有收到手机确认后才推进步骤。正式服务端接口和企业签名仍需下一阶段，P2P 文件通道仍需真机验证。

照片链路还覆盖三个异步到达边界：眼镜端字节流 `onSuccess(false)` 或同步调用异常会保留 outbox 并自动重试；手机端文件先到时会把 SDK 临时文件复制到 app-private 暂存目录，等待元数据后再校验，避免依赖 SDK 缓存寿命；同一 `photoId` 的重复文件回调按照片串行化，避免并发写临时文件。

截至 2026-11-06，本机最新全量 Android 校验已通过：协议/流程测试 **35/35**、手机上传测试 **3/3**，手机端和眼镜端 lint 无 Error，Debug 与 Release unsigned APK 均可生成；四个 APK 已用 `aapt dump badging` 核对包名、minSdk 和 targetSdk。手机端任务文件同时支持系统文件管理器 `VIEW/data` 和分享面板 `SEND/EXTRA_STREAM`，两者都经过同一协议与任务结构校验；眼镜端直收任务强制检查协议版本、`commandId`、`taskId` 以及 `commandId=cmd-dispatch-{taskId}` 身份关系；手机对 `task.ack/start/pause/resume` 也按共享 commandId 校验，入站坏 payload 在 UI 边界被隔离；新增完整五步巡检流程回归，覆盖设备确认、顺序、读数阈值、照片归属和最终复核；眼镜进程重启后会从持久化照片 outbox 恢复 `photoId → stepId` 归属，并只重试当前任务的照片；没有活动任务时不会重试旧照片。`device-preflight.sh` 结果为 0 个失败、4 个外部条件提醒。由于当前 `adb devices` 为空，以下项目仍不能标记为真机通过：Glass3 安装与固件兼容性、蓝牙/P2P 配对、实体按键广播、相机回调、照片文件通道和断网恢复。最新产物哈希以 [android/README.md](android/README.md) 和 [EXECUTION_PLAN.md](EXECUTION_PLAN.md) 为准。

权限已按 Rokid 官方快速开始对齐：手机端声明蓝牙扫描/连接/广播、Wi‑Fi P2P 和 API 32 以下定位权限，并在 Android 12/12L 动态申请精确与模糊定位；Android 13+ 使用 `NEARBY_WIFI_DEVICES`。眼镜端声明网络/Wi‑Fi/P2P 状态和相机能力，照片仍限定写入 app-private 目录。企业登记了不同包名时，可通过 `local.properties` 的 `android.phoneApplicationId` / `android.glassApplicationId` 覆盖默认包名，现场安装脚本会从 APK 自动读取包名。

## 开工前的阻塞项

设备型号已由项目方确认：**Rokid Glass3 企业版**。因此工程按 Glass3 Enterprise / Sprite Enterprise 双端 SDK 路线实现，不再按普通 Rokid Glasses 或 AR/OpenXR 路线适配。

仍需在真机开工前补齐：

- 眼镜固件版本和是否已有企业授权。
- 手机端 SDK 的 AK/SK、包名/签名要求和 `clientId` 约定。
- 是否有 Glass3 数据调试线、Android 手机和投屏工具。
- 现场照片是否允许落盘到手机、单张大小和保留期限。

在这些现场条件到位前，可以继续完成协议、领域状态机和无设备 Android UI；真实 SDK 连接、蓝牙/P2P、按键和相机仍不能视为已验收。

## 官方资料

- [Rokid Open Platform](https://open.rokid.com)
- [Glass3 Enterprise 快速开始](https://x-docs.rokid.com/docs/terminal-sdk/getting-started/%E5%BF%AB%E9%80%9F%E5%BC%80%E5%A7%8B.html)
- [API 总览](https://x-docs.rokid.com/docs/terminal-sdk/api-reference/)
- [官方代码示例](https://x-docs.rokid.com/docs/downloads/samples.html)
- [手机端发送消息与文件](https://x-docs.rokid.com/docs/%E4%BB%A3%E7%A0%81%E7%A4%BA%E4%BE%8B/20-message-transfer/01-%E6%89%8B%E6%9C%BA%E7%AB%AF%E5%8F%91%E9%80%81%E6%B6%88%E6%81%AF%E4%B8%8E%E6%96%87%E4%BB%B6.html)
- [眼镜端接收消息与文件](https://x-docs.rokid.com/docs/%E4%BB%A3%E7%A0%81%E7%A4%BA%E4%BE%8B/20-message-transfer/04-%E7%9C%BC%E9%95%9C%E7%AB%AF%E6%8E%A5%E6%94%B6%E6%B6%88%E6%81%AF%E4%B8%8E%E6%96%87%E4%BB%B6.html)
- [按键与侧边触控](https://x-docs.rokid.com/docs/%E4%BB%A3%E7%A0%81%E7%A4%BA%E4%BE%8B/50-system/01-%E6%8C%89%E9%94%AE%E4%B8%8E%E4%BE%A7%E8%BE%B9%E8%A7%A6%E6%8E%A7.html)
- [官方 Glass3 SDK Demo 源码](https://github.com/RokidSuuport/glass3_sdk_demo)

### 2026-11-06 重试一致性补强

手机和眼镜现在把 ACK 的 accepted/rejected 结果纳入命令身份，修复“先负 ACK、后正 ACK”时眼镜重放账本误吞正 ACK 的边界；同一 `taskId` 的不同任务内容也会被两端拒绝覆盖。手机 SDK 初始化完成后会检查已有蓝牙/P2P 链路，进程重启而连接仍在时可继续重发未完成任务。该改动已通过协议 31/31、手机上传 3/3、双端 lint 和 Debug/Release 构建；真机链路仍待 Glass3 Enterprise 与配套手机现场验证。

### 2026-11-06 最终完成消息边界

`task.complete` 由手机在全部步骤 Room 落盘后发送，眼镜端严格验证固定 `commandId`、任务归属和 `accepted` 布尔类型；只有终态同步成功才写入眼镜快照和重放账本。连接恢复时手机会幂等重发该通知，负结果不会阻断后续成功重试。协议测试 **40/40**、手机端测试 **7/7**，候选包与真实设备现场验收边界保持不变。

### 2026-11-06 设备心跳实现

眼镜 SDK client 就绪后每 30 秒发送 `device.heartbeat`，带实际设备 ID、电量、连接状态和 App 版本；手机端严格校验并在独立状态栏显示心跳，连续 90 秒未收到时标记为心跳超时。系统电量属性不可用时不发送伪造的 `0%`，而是跳过本次心跳并在下一周期重试。它只证明控制通道可用，不等于任务或报告同步成功。协议样例见 `protocol/device-heartbeat.example.json`。

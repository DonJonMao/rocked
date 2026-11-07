# Rokid Glass3 官方 Demo 对照记录

更新时间：2026-11-06

## 构建证据

本机使用 JDK 17 和 Android SDK 构建官方仓库；现场请替换为本机实际路径
`RokidSuuport/glass3_sdk_demo` 的双设备示例：

```bash
cd scenario/dual-device-ai-inspection-report/glass-app
./gradlew assembleDebug --console=plain

cd ../phone-app
./gradlew assembleDebug --console=plain
```

结果：

- 官方 `glass-app`：`BUILD SUCCESSFUL`
- 官方 `phone-app`：`BUILD SUCCESSFUL`
- SDK 坐标均为 `2.2.2-E`
- 首次构建需要各工程自己的 `local.properties`，本机只写入临时 SDK 路径，没有修改官方源码

## 当前工程与官方边界

| 能力 | 官方示例入口 | 当前工程 |
|---|---|---|
| 眼镜 SDK 绑定/注册 | `GlassSdk.bindSecurityService`、`registerClient` | `RokidGlassTransport` 已接入 |
| 手机 SDK 初始化 | `PSecuritySDK.initSDK`、`EngineParam` | `RokidPhoneTransport` 已接入，凭证来自 `local.properties` |
| 经典蓝牙控制消息 | `sendTextMessageByClassicBTWithClient` | 已接入，断线后可重发未完成任务 |
| Wi-Fi P2P 控制/文件 | `sendConnectP2pRequest`、P2P message/file operator | 已接入，控制消息和照片均有蓝牙回退 |
| 照片 | `CameraMediaOptions.takePhoto` | 已接入私有目录、MIME/尺寸/SHA-256 校验和 ACK |
| 物理按键 | 系统广播 `CLICK/DOUBLE_CLICK` | 已接入：单击完成、双击退出 |
| 镜腿滑动 | `KEYCODE_DPAD_LEFT/RIGHT` | 已补入：左右滑动只移动焦点，不绕过步骤校验 |
| 常亮 | 官方 `FLAG_KEEP_SCREEN_ON` | 已补入眼镜巡检 Activity |
| 业务数据 | 官方 AI 识别记录 | 当前改为固定巡检模板、读数、异常、照片和报告队列 |

## 本轮代码变更

眼镜端 `MainActivity`：

- 将 `DPAD_LEFT/RIGHT` 映射为界面焦点前后移动，避免侧滑误提交步骤；
- 在 `onResume` 保持近眼屏幕常亮，在 `onPause` 清除标志；
- 保留实体单击、双击广播及 Enter/返回键语义。

## 本地验证

```text
:protocol:test       通过
:phone-app:test      通过
:phone-app:lint      通过
:glass-app:lint      通过
Debug/Release APK    构建成功
```

当前产物 SHA-256 以候选包内 [SHA256SUMS](artifacts/rokid-rc-20261106/SHA256SUMS) 为准；最新主机回归输出为：手机 Debug `85494ba2cfbf24a29ef4150ea4e6c8c3733ce9656c3336481c4cca3e767f2564`，眼镜 Debug `29f621915d6c9cb369494388b3d2b0b2dd02a95f6a26231b4c0ec9e05bc92160`，手机 Release unsigned `7336dd46c7fbfd207ad9b45e7585cbc204e68f9a43808d4b6fa0e84688b968c9`，眼镜 Release unsigned `7188ac913d1e92efff1cca34f19711bfa053c6a5c9ea79435b298342ff83bb50`。

上述验证仍是无设备验证。Glass3 企业版的安装、固件兼容性、企业 AK/SK、蓝牙/P2P 实连、真实按键广播、相机回调、照片文件通道和断线恢复，必须按
[android/SMOKE_TEST.md](android/SMOKE_TEST.md) 在现场完成，不能由本地构建替代。


## 异步就绪时序复核

官方示例通过 `IClientCallback.onReady()` 表示 client 真正可用。当前工程已调整为：`registerClient()` 返回后只完成消息监听注册，只有 `onReady()` 回调到达后才触发任务/照片恢复；服务断开或绑定失效会清除就绪状态，重连后重新通知。该改动已通过眼镜端 lint、Debug/Release 构建。

## 最终完成通知复核

手机在所有步骤已由 Room 落盘后发送 `task.complete`。眼镜端现在校验固定
`complete-task-{taskId}` 身份，只有把终态写入本地快照后才记入重放账本；手机连接恢复时会对已完成任务幂等重发该通知。该闭环已通过协议 **39/39**、手机端 **7/7**、双端 lint 和 Debug/Release 构建，候选包校验以
[artifacts/rokid-rc-20261106/SHA256SUMS](artifacts/rokid-rc-20261106/SHA256SUMS)
为准。它仍不替代真实 Glass3 现场验收。

### 完成消息 payload 类型门禁

`task.complete` 的 `accepted` 现在必须是存在的 JSON 布尔值；坏 payload 会在协议编解码层拒绝，不会写入眼镜终态或重放账本。最新协议测试 **40/40**、手机端测试 **7/7**，候选包校验清单保持有效。

## 2026-11-06 官方仓库最新示例复核

- 已从官方 `RokidSuuport/glass3_sdk_demo` 最新主分支下载并对照 `scenario/dual-device-ai-inspection-report`；仓库 README 仍明确使用 SDK `2.2.2-E`。
- 官方示例确认手机端使用经典蓝牙承载控制消息、P2P 承载大文件；眼镜端使用 `sendTextMessageByClassicBTWithClient` 和 `sendStreamData`，与当前工程的优先顺序一致。
- 官方示例同时提供 Glass3 物理按键广播 `CLICK/DOUBLE_CLICK`，并在处理后调用 `abortBroadcast()` 消费广播。当前眼镜端已补上同样的消费动作，避免系统默认动作和巡检动作同时发生。
- 官方眼镜清单还声明蓝牙扫描/连接、附近 Wi-Fi、定位和常亮权限；当前眼镜清单已补齐这些声明，并由 `device-preflight.sh` 从 APK 逐项核对。
- 官方手机示例只申请蓝牙扫描/连接和附近 Wi-Fi 相关权限；当前手机端已移除 `BLUETOOTH_ADVERTISE` 的运行时申请，眼镜端仍保留自身广播声明。由于 SDK 可能把该权限合并进最终 APK 清单，预检同时检查应用源码的主动申请列表。
- 眼镜端 SDK 监听注册失败时现在会清理已经挂上的消息监听，再进行延迟重试，避免部分注册成功造成重复回调。
- `android/host-smoke.sh` 增加静态门禁检查，保证眼镜 Activity 保留 `abortBroadcast()`；随后全量 Android 回归通过，候选包已刷新。
- 这次对照仍不能替代真实设备测试；官方示例和当前工程都需要现场确认固件、权限、连接和企业授权的实际表现。

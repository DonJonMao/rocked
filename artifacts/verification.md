# 实施与验证记录

核查时间：2026-10-28（Asia/Shanghai）。源码目录：`/Users/mao/projects/inspection-demo`。任务包原文件保留，新增应用、锁文件、素材、测试与验收产物。

## 最终实际执行结果

| 命令 | 结果 | 证据 |
|---|---|---|
| `npm ci` | 通过；使用系统 npm 10.9.4 从锁文件安装 262 个包，审计 0 漏洞 | 本次终端执行结果；随后所有检查基于该安装 |
| `npm run typecheck` | 通过，退出码 0 | [checks.log](checks.log) |
| `npm run lint` | 通过，无错误、无警告 | [checks.log](checks.log) |
| `npm run test` | 通过，3 个文件、36 项：18 领域 + 8 存储 + 10 组件/输入边界 | [checks.log](checks.log) |
| `npm run build` | 通过；JS 306.59 kB / gzip 93.02 kB，CSS 34.08 kB / gzip 8.28 kB | [checks.log](checks.log) |
| `npm run test:e2e` | 通过，15/15，42.4 秒，Chromium | [e2e-dev.log](e2e-dev.log) |
| `DEMO_BUILD=1 npm run test:e2e` | 通过，15/15，37.1 秒，自动运行 `npm run demo` 的构建与预览 | [e2e-production.log](e2e-production.log) |
| `npm audit` | 通过，0 漏洞 | [checks.log](checks.log) |

开发与演示均固定 `http://127.0.0.1:5173`，strictPort；测试使用独立浏览器上下文，不触碰演示人员的真实存储。

实际版本：Node 22.22.0、npm 10.9.4、React/React DOM 19.3.1、TypeScript 5.9.3、Vite 6.4.5、idb 8.0.4、lucide-react 0.468.0、Vitest 4.1.12、Testing Library React 16.3.3、Playwright 1.64.1、ESLint 9.39.5。具体依赖由 `package-lock.json` 锁定。

## 功能与边界证据

[逐条验收映射](acceptance-results.md) 覆盖 ACCEPTANCE 的 34 条 P0，列明测试名称、具体断言与人工边界。33 条本地可自动验证用例通过；B04 的拍照、Blob 保存和 tracks 停止代码路径通过模拟媒体验证，真实摄像头与系统权限尚未人工实测。

- 下发等待开始；资产不匹配、照片不足、结果缺失、异常说明缺失和最终核对缺失均阻断。完成状态与异常结论分开。
- 完成命令包含 taskId / expectedStepId / commandId；业务层重放、过期请求、同一帧重复点击及原生双击均不重复推进。
- 任务、照片与事件在 IndexedDB 原子保存。真实事务写入错误注入证明照片新增、步骤完成、照片删除及重置均整体回滚，可重试。
- 暂停及刷新恢复原步骤、草稿、图片和历史；照片由 Blob 重新生成预览地址，导出 JSON 无 Blob 或 object URL。
- PNG/JPEG/WebP 上传成功；SVG、伪装格式、实际无法解码及超过 10MB 文件被拒绝。素材只取受控本地 SVG 并转为 PNG。
- 模板增删、重排、版本、首末位置以及各类型步骤的照片和结果要求均实际执行；旧任务保留快照，新任务读取新配置。
- 两个任务的照片、结论、时间线隔离；打印前等待照片，print media 隐藏操作控件；真实下载的 JSON 字段、统计与图片元数据经过解析验证。
- 三轮“下发—执行部分步骤—重置—刷新”无残留；取消重置保留数据。完整五步流程另在三种分辨率分别完成。
- 全链路拦截所有非本地请求，未发生公网资源请求或 pageerror；未以改变电脑网卡状态冒充物理断网测试。

## 视觉核查

最终生产构建生成 15 张原始全页截图，文件在 [screenshots/](screenshots/)，尺寸与视口映射见 [manifest.json](screenshots/manifest.json)。视口为 1366×768、1440×900、1920×1080；每种均有初始、缺照片、执行中、暂停刷新恢复、完成报告。

已目视检查三个尺寸的五种状态，并单独检查小屏缺项、执行输入、暂停和完整报告。检查结果：无横向溢出，三栏明确；眼镜标题、要求与主操作保持可见，表单内部可滚动；具体错误清晰，已保存素材有来源标记，报告照片、异常与时间线完整。自动化额外断言标题与主按钮完整处于视口内、缺项提示与视口相交。全页截图保留必要纵向滚动内容，因此图片高度不等于视口高度。

目视复核索引：[1366](visual-review/1366-contact.png)、[1440](visual-review/1440-contact.png)、[1920](visual-review/1920-contact.png)。原始截图是验收依据，索引图用于快速对照。

## 失败与修复记录

- 初次依赖安装成功但有 3 项旧 Vitest 开发依赖漏洞。npm 10 升级时两次出现 Arborist `edgesOut` 错误；`npx --yes npm@11 install` 完成升级，之后系统 npm 10 的 `npm ci` 复现通过，最终审计 0 漏洞。
- 初期 TypeScript 发现 ES2022 不含 `toReversed`，已改为复制后 reverse；后续新增截图断言缺少 HTMLImageElement 类型，已修正并重新通过最终 typecheck。
- 测试早期选择器、暂停后立即刷新及 UUID getAll 顺序假设引起失败；改为准确可访问名称、等待已暂停持久化状态与按时间/事件顺序核查。
- Tab 焦点可能离开原生弹窗，补充显式可见控件循环。方向键跳过隐藏控件，输入框保留原生编辑行为。
- 非默认设备/人员刷新后的左栏显示错误已修复为活动任务快照；可配置首末步骤缺少照片控件已改为统一配置渲染。
- 原生双击在连续可选检查项上可能生成新步骤请求，已忽略 click.detail 的第二击并补回归测试。
- 初次约束眼镜高度后，当前标题可能滚出可视区；将标题、表单滚动区与主操作拆开，最终三尺寸视口断言及截图复查通过。
- 开发模式一次测试与源代码热更新重叠，导致弹窗节点卸载、14 通过/1 失败；保留 [中间日志](e2e-dev-interrupted.log)，稳定源码后重跑 15/15，通过构建版再次确认。
- 一次独立布局探查在测试服务已停止时收到 ERR_CONNECTION_REFUSED；后续在 Playwright 管理的实际服务上完成全部布局验证。

## 人工补测与未实施范围

真实摄像头硬件、操作系统权限弹窗及原生系统打印窗口未人工实测。自动化相机成功用例使用 Canvas MediaStream，权限拒绝/迟到结果使用 mock；它们证明代码路径，不证明外接硬件。打印用例验证浏览器打印调用与 print CSS，不冒充操作系统打印机实测。人工步骤和真实状态已记录于 [README](../README.md#摄像头与人工补测)。主流程无需摄像头，使用内置素材即可完整演示。

P1 语音、独立放大、模拟图片核验、双区域摄像头以及可选网络队列未实施。真实 Glass3 Android、910B 识别、二维码解码、跨网音视频、企业登录和多用户平台属于约定的后续接入范围。

交付时再次运行 `npm run demo`，同一构建产物成功启动；`curl --silent --fail -I http://127.0.0.1:5173` 返回 HTTP 200。应用保持可打开的本地预览状态。

## 2026-11-06 回归复测

本轮实际执行：

- `npm run typecheck`：通过。
- `npm run lint`：通过。
- `npm run test`：4 个测试文件、44 项测试通过。
- `npm run build`：通过，Vite 产物生成在 `dist/`。
- `PW_REUSE_SERVER=1 npm run test:e2e`：31/31 通过。
- `DEMO_BUILD=1 PW_REUSE_SERVER=1 npm run test:e2e`：31/31 通过。
- `android/host-smoke.sh`：通过；Android 协议 31/31、手机上传 3/3、双端 lint、Debug/Release 构建和 APK 目录校验均通过。

端到端命令复用了已经运行在 `127.0.0.1:5173` 的本项目预览服务；该服务进程由 `npm run demo` 启动，未复用外部项目。真实摄像头、手机和 Rokid Glass3 企业版仍未连接，相关硬件验收保持“待现场实测”。

## 2026-11-06 Web ↔ Android 任务桥接复测

- 新增单元测试后 `npm run test`：5 个测试文件、46/46 通过；`npm run typecheck`、`npm run lint`、`npm run build` 均通过。
- 新增 Playwright 用例“管理端可导出 Android task.dispatch 任务文件”，实际下载并解析 JSON，确认 `type=task.dispatch`、版本、任务编号、模板、资产和 5 个步骤；该用例通过。
- `PW_REUSE_SERVER=1 npm run test:e2e`：32/32 通过，包含四号楼 12 套流程、三种视口、照片/摄像头模拟、暂停恢复、报告、重置和 Android 任务文件导出。
- `android/host-smoke.sh`：通过；协议 31/31、手机上传 3/3、双端 lint、Debug/Release 构建及目录预检均通过。
- 候选包 `artifacts/rokid-rc-20261106/` 已同步本轮 APK 并重新执行四项 SHA-256 校验；Release 仍为 unsigned，真机仍待现场条件。
- `DEMO_BUILD=1 PW_REUSE_SERVER=1 npm run test:e2e`：32/32 通过，确认新任务导出按钮也存在于生产构建产物。

## 2026-11-06 Android 任务边界校验复测

- `android/gradlew :protocol:test`：协议测试 **32/32** 通过。
- `android/host-smoke.sh`：通过，协议 32/32、手机上传 3/3、双端 lint、Debug/Release 构建及 APK 预检通过。
- 候选包四个 APK 已更新并用 `shasum -a 256 -c SHA256SUMS` 全部校验通过；未连接真机，Release 签名门禁仍按 unsigned 预期提醒。

## 2026-11-06 手机任务文件解析复测

- 新增 `TaskDispatchFileParser`，把文件解析和 Activity UI 解耦；手机端单元测试 **5/5**、协议测试 **32/32**。
- 错误消息类型、空 taskId、非法步骤结构在写入 Room 前被拒绝；成功任务仍保留 taskId 和模板/设备快照。
- `android/host-smoke.sh` 通过，候选包四项 APK checksum 通过。

## 2026-11-06 Android 文件关联复测

- 手机端新增 JSON MIME 文件打开入口，直接打开和应用内文件选择器共用 `TaskDispatchFileParser`。
- 中间 lint 曾发现 `AppLinkUrlError`；已仅对 MIME 文件关联 filter 加局部 `tools:ignore`，之后 `android/host-smoke.sh` 完整通过，lint 无 Error。
- 当前候选包 APK 已重新复制并执行 SHA-256 校验，Release unsigned 状态保持明确。

## 2026-11-06 task.dispatch 身份门禁复测

- `TaskDispatchFileParser` 对协议版本和 `commandId` 增加硬校验，手机端测试 **6/6**、协议 **32/32** 通过。
- `android/host-smoke.sh` 通过；候选包四个 APK checksum 全部 `OK`。

## 2026-11-06 Android 文件入口预检补充

- `android/device-preflight.sh` 新增 APK Manifest 检查：手机 APK 必须包含 `android.intent.action.VIEW` 和 `application/json` 文件入口。
- `bash -n android/device-preflight.sh`：通过。
- `android/host-smoke.sh`：通过；预检新增两项均 PASS，整体 0 个失败、4 个提醒；Android 构建与测试保持通过。
- 当前提醒为企业 AK/SK 未配置、报告 HTTPS URL 为空、Release unsigned、未连接手机或 Glass3。

## 2026-11-06 Android 文件分享入口复测

- 手机端新增 `SEND/EXTRA_STREAM` JSON 文件分享入口；`VIEW/data` 和分享面板共用 `TaskDispatchFileParser`。
- `android/local.properties.example` 提供不含真实凭证的配置模板；默认同步地址为空，避免误发网络请求。
- `android/host-smoke.sh`：通过；预检新增 `SEND` Manifest 检查为 PASS，0 个失败、4 个提醒；协议 32/32、手机上传 3/3、双端 lint 和 Debug/Release 构建均通过。
- 候选包四个 APK SHA-256 已重新核对，全部 `OK`。

## 2026-11-06 Android 外部分享 URI 兼容复测

- `MainActivity` 任务入口现在还兼容 `ClipData` URI，覆盖文件管理器、分享面板和部分系统文档提供器的传递方式。
- `android/host-smoke.sh`：通过；预检 0 个失败、4 个提醒，协议 32/32、手机上传 3/3、双端 lint 和 Debug/Release 构建通过。
- 最新 APK 已同步候选包：手机 Debug `fbbb01601ffc61e5d66ec8725771e68333aa6c7b13f489907f332e418a8812e4`，眼镜 Debug `7e66f44610693a6bb9a67e0b10134d82be5328b1e83c256fd45f9de7a081bd14`；手机 Release unsigned `9c5081d8242856c76cdef71e99df922eba79c2201b8a1660fb303ed459cbb0be`，眼镜 Release unsigned `28320ce6317df95f5e5c00fcd7522f78f18850caaf110df869b9d7814b6d3623`；候选包 checksum 全部 `OK`。

## 2026-11-06 Android 照片拒绝状态提示修复

- 手机端照片回调在任务/步骤归属校验失败时不再显示“照片已保存”，改为显示未关联原因并回传负 `photo.ack`；校验成功才显示文件已保存。
- `android/host-smoke.sh`：通过；协议 32/32、手机上传 3/3、双端 lint 和 Debug/Release 构建通过，预检 0 个失败、4 个提醒。
- 候选包已同步最新 APK，四项 SHA-256 全部 `OK`；Release 仍为 unsigned，真机仍待现场条件。

## 2026-11-06 候选包可重复同步

- 新增 `android/sync-candidate.sh`：从已构建 APK 刷新候选包、现场脚本、Smoke Test 和 `SHA256SUMS`；不签名、不安装、不读取或修改凭证。
- `bash -n android/sync-candidate.sh`：通过；实际执行同步后候选包四项 checksum 全部 `OK`。
- `android/host-smoke.sh`：通过，并纳入候选同步脚本的 shell 语法检查；协议 32/32、手机上传 3/3、双端 lint 和 Debug/Release 构建通过。

## 2026-11-06 Android 入站坏包隔离

- 手机端和眼镜端现在把完整入站消息处理包在安全边界内：JSON 解码、payload 解析、任务/ACK 回调异常均不会冒泡到 Rokid SDK 回调线程；错误只显示现场状态并保留已有任务/outbox。
- `android/host-smoke.sh`：通过；协议 32/32、手机上传 3/3、双端 lint 和 Debug/Release 构建通过，预检 0 个失败、4 个提醒。
- 运行 `android/sync-candidate.sh` 同步最新 APK 后，候选包四项 SHA-256 全部 `OK`。

## 2026-11-06 Android 眼镜端任务身份门禁

- 将 `task.dispatch` 的类型、协议版本、`commandId` 和 `taskId` 校验提取为共享 `TaskGuard.validateDispatchEnvelope`；手机文件导入和眼镜 SDK 直收任务共用同一规则。
- 新增协议回归测试，协议测试增至 **33/33**；手机上传保持 **3/3**。
- `android/host-smoke.sh`：通过；双端 lint、Debug/Release 构建和 APK 预检通过，0 个失败、4 个提醒。
- 候选包已用 `sync-candidate.sh` 同步，四项 APK SHA-256 全部 `OK`。

## 2026-11-06 Android ACK 身份一致性

- 眼镜端收到 `step.ack` / `photo.ack` 后，除任务/步骤/照片字段外，还验证 `commandId` 是否对应 accepted/rejected 结果和步骤 revision；不匹配的旧 ACK 不会进入重放账本或推进本地状态。
- `android/host-smoke.sh`：通过；协议 33/33、手机上传 3/3、双端 lint 和 Debug/Release 构建通过，预检 0 个失败、4 个提醒。
- 候选包已重新同步，四个 APK SHA-256 全部 `OK`。

## 2026-11-06 Android 控制消息 commandId 统一

- 新增共享 `ProtocolCommandIds` 生成 dispatch、step.complete、photo.upload、task.complete 身份；手机端拒绝 commandId 不匹配的照片/步骤消息，眼镜端 ACK 校验与同一生成器一致。
- `android/host-smoke.sh`：通过；协议 33/33、手机上传 3/3、双端 lint 和 Debug/Release 构建通过，预检 0 个失败、4 个提醒。
- 候选包用 `sync-candidate.sh` 更新，四项 APK checksum 全部 `OK`。

## 2026-11-06 Android commandId 生成器回归

- 新增 `ProtocolCommandIds` 的稳定性测试，覆盖 dispatch、step.complete、photo.upload、task.complete 四类身份。
- `./gradlew :protocol:test`：通过；协议测试总数 **34/34**（Debug/Release 报告均无失败）。
- 本轮只新增协议回归测试，既有已构建 APK 与候选包 checksum 保持有效。

## 2026-11-06 Android 五步巡检流程回归

- 新增设备确认 → 压力读数/阈值与照片 → 运行模式 → 外观检查 → 最终复核的完整协议流程测试。
- 覆盖跳步拒绝、资产码/确认、照片步骤归属、异常说明、选择字段和最终复核；协议测试增至 **35/35**，Debug/Release 均无失败。
- `android/host-smoke.sh`：通过；手机上传 3/3、双端 lint、Debug/Release 构建和 APK 预检通过，0 个失败、4 个提醒。

## 2026-11-06 Android 凭证模板与预检防误配

- `local.properties.example` 的 Rokid AK/SK 默认改为空值，直接复制不会误触发 SDK 初始化。
- `device-preflight.sh` 会把 `replace-with-*` 占位值视为未配置，并且不输出任何凭证内容；候选包内脚本已同步。
- `android/host-smoke.sh`：通过；预检 0 个失败、4 个提醒，协议 35/35、手机上传 3/3、双端 lint 和 Debug/Release 构建通过。
- APK 未变更，候选包四项 SHA-256 全部 `OK`。

## 2026-11-06 Android 照片 outbox 重启归属恢复

- 修复眼镜进程重启后的照片 ACK 边界：从持久化 outbox 恢复 `photoId → stepId` 映射，重试限定当前活动任务；没有活动任务时不重试旧照片；ACK 仍在任务快照成功落盘后才清理 outbox。
- `android/host-smoke.sh`：通过；协议 35/35、手机上传 3/3、双端 lint 和 Debug/Release 构建通过，预检 0 个失败、4 个提醒。
- 候选包已同步最新 APK，四项 SHA-256 全部 `OK`；Release 仍为 unsigned，真实设备仍待现场条件。

- Android 修复后复核 Web 端：typecheck、lint、46/46 单测、生产构建通过；`PW_REUSE_SERVER=1 npm run test:e2e` **32/32** 通过。

## 2026-11-06 Android task.dispatch 身份一致性门禁

- `task.dispatch` 现在强制 `commandId` 与 `taskId` 按 `ProtocolCommandIds.dispatch(taskId)` 一致；手机文件导入和眼镜 SDK 直收共用同一校验，错配消息在持久化前拒绝。
- 协议与手机解析测试通过；`android/host-smoke.sh` 通过，协议 35/35、手机上传 3/3，预检 0 个失败、4 个提醒；候选包四项 checksum 全部 `OK`。

- 手机 Activity 增加入站消息安全边界，并对 `task.ack/start/pause/resume` 校验共享 commandId；`host-smoke.sh` 构建与 lint 仍通过。

## 2026-11-06 Android SDK 断线自动重绑与控制状态恢复

- 眼镜端 SDK 服务断开、绑定失效、消息服务未就绪或监听注册异常时，统一延迟重绑并恢复监听。
- SDK 重新就绪后补发持久化任务的 `task.ack` 以及 `task.start`/`task.pause`，随后继续照片 outbox 和未确认步骤重试；控制消息沿用幂等 commandId。
- ACK 重放账本改为同步提交，缩小“状态已应用但进程在异步写入前退出”的重复处理窗口。
- `android/host-smoke.sh` 通过：协议 **35/35**、手机上传 **3/3**、双端 lint、Debug/Release 构建；预检 0 个失败、4 个外部条件提醒。
- `android/sync-candidate.sh` 后候选包四项 `SHA256SUMS` 均为 `OK`。

## 2026-11-06 Android 照片索引终态提交顺序

- 手机端在发送照片终态 ACK 前同步提交 pending photo 索引删除；清理失败时保留重试条件，不发送 ACK。
- `android/host-smoke.sh` 通过：协议 **35/35**、手机上传 **3/3**、双端 lint、Debug/Release 构建；候选包四项 checksum 全部 `OK`。

## 2026-11-06 Android 任务文件大小边界

- 手机端任务文件读取和解析均限制为 2 MB；超大 JSON 在解码和 Room 写入前拒绝。
- 新增超大任务文件单测；`android/host-smoke.sh` 通过，候选包四项 checksum 全部 `OK`。

## 2026-11-06 候选包自定义包名预检

- 候选交付包没有 `local.properties` 时，预检按 APK manifest 的实际 applicationId 校验，避免企业自定义包名被 Demo 默认包名误判。
- 当前候选包实际预检：0 个失败、5 个外部条件提醒；四个 APK checksum 全部 `OK`。

## 2026-11-06 Release 安装签名门禁

- `field-install.sh --variant release` 在安装前强制调用 `apksigner verify`。
- 用 unsigned APK 改名为 signed 产物并配合 fake adb 演练，脚本在安装前退出，未执行安装。
- `android/host-smoke.sh` 通过，候选包四项 checksum 全部 `OK`。

## 2026-11-06 Android 照片负 ACK 终态与临时文件清理

- 手机端照片关联失败或负 ACK 时清理对应 `photoId` 的私有文件，避免错任务照片残留；状态文案与 accepted/rejected 结果一致。
- 文件先于元数据到达的临时目录增加 24 小时、64 MB 上限清理。
- `android/host-smoke.sh` 通过：协议 **35/35**、手机上传 **3/3**、双端 lint、Debug/Release 构建；预检 0 个失败、4 个外部条件提醒。
- `android/sync-candidate.sh` 后候选包四项 `SHA256SUMS` 均为 `OK`。

## 2026-11-06 候选包离线预检与现场证据增强

- `device-preflight.sh` 在候选包模式下没有 `adb` 时改为提醒；显式 `--require-device` 仍严格失败，避免交接电脑无法离线核对 APK。
- `field-diagnostics.sh` 新增 `artifacts.txt`，记录 `adb` 版本、手机/眼镜 APK SHA-256 和实际包名，并在报告 README 中列出。
- 用 fake adb 双设备回归诊断报告生成；`android/host-smoke.sh` 通过，协议 **35/35**、手机上传 **3/3**、双端 lint、Debug/Release 构建通过。
- 候选包离线预检 **0 个失败、7 个提醒**；`--require-device` 在无 adb 时按预期失败；四个 APK `SHA256SUMS` 全部 `OK`。

## 2026-11-06 现场脚本离线演练

- 新增 `android/field-script-smoke.sh` 并纳入 `host-smoke.sh`：用 fake adb 验证手机/眼镜序列号路由、unsigned Release 在安装前被拦截、失败后不执行安装，以及诊断报告的 `artifacts.txt` 证据。
- 源码工程完整演练通过；候选包在无 Android build-tools 的交接电脑上明确输出 `SKIPPED`，补齐 `aapt` 后可执行同一演练。
- `android/host-smoke.sh` 通过；候选包四个 APK `SHA256SUMS` 全部 `OK`。

## 2026-11-06 干净构建与 Android 14 广播注册修复

- 清空 Gradle 输出后复核发现两处真实问题：预检在 APK 尚未构建时误把缺失产物当作 manifest 错误；Glass3 按键动态广播缺少 Android 14 receiver export flag。
- `device-preflight.sh` 现在会在 APK 未齐备时跳过依赖 APK 的核对；`host-smoke.sh` 将 fake adb 演练放到构建之后。
- Glass Activity 改用 `ContextCompat.registerReceiver(..., RECEIVER_EXPORTED)`，保留接收 Rokid 系统广播的语义。
- Kotlin 工具链从 2.2.0 调整为 2.0.21，与 AGP 8.4.2 lint metadata 版本一致；干净构建不再出现 metadata mismatch 或 kapt fallback 提示。
- 清理后 `host-smoke.sh` 通过，协议 **37/37**、手机测试 **3/3**、双端 lint、Debug/Release 构建和现场脚本演练均通过；候选包四个 APK checksum 全部 `OK`。
- `host-smoke.sh` 现在在构建前后各执行一次预检；构建后会再次核对 APK 包名、min/target SDK、JSON 文件入口和四号楼目录哈希。

## 2026-11-06 模板读数字段边界

- `StepValidator` 现在拒绝步骤 payload 中未在任务模板声明的读数字段；合法任务的停机/待机/不涉及状态仍可省略声明字段，但不能注入额外键值。
- 新增协议回归，覆盖伪造读数不能进入报告；协议测试增至 **37/37**，`host-smoke.sh` 和候选包 checksum 均通过。

## 2026-11-06 草稿字段边界

- `TaskGuard.validateStepDraft` 与完成消息共用模板字段门禁；手机端在 Room 持久化前拒绝带有未定义读数字段的 `step.draft`。
- 协议测试增至 **38/38**；`host-smoke.sh` 通过，候选包四个 APK checksum 全部 `OK`。

## 2026-11-06 任务完成通知闭环

- 眼镜端处理手机发来的 `task.complete`：验证固定 `commandId` 与任务身份，只有完成状态同步持久化后才记入 ACK 重放账本。
- 眼镜任务快照新增终态字段；重启后能恢复“手机已确认全部步骤保存”的显示状态。
- 手机连接恢复时发现全部步骤已在 Room 落盘，会幂等重发 `task.complete`，覆盖最终通知在断线或进程重启期间丢失的情况。
- `TaskCompletePayload` 新增编解码回归；协议 Debug/Release 共 **39/39**、手机端 Debug/Release 共 **7/7**，双端 lint 和 Debug/Release APK 构建均通过。
- `android/host-smoke.sh` 通过，预检 **0 个失败、4 个外部条件提醒**；候选包已重新同步，`SHA256SUMS` 四项均为 `OK`。真实设备、企业签名、AK/SK 和 HTTPS 后端仍未验收。

## 2026-11-06 完成消息 payload 类型门禁

- `task.complete` 的 `accepted` 必须存在且必须是 JSON 布尔值；缺失或字符串值会被眼镜端拒绝，不会误记终态或重放账本。
- 新增协议回归；协议 Debug/Release **40/40**、手机端 Debug/Release **10/10** 通过。
- 全量 `android/host-smoke.sh` 通过；候选包四项 `SHA256SUMS` 均为 `OK`，真实设备、凭证和正式签名仍待现场。

## 2026-11-06 负完成通知重试语义

- `task.complete` 的负结果不再记入眼镜重放账本；由于完成命令 ID 固定，之后的正结果仍能被处理。
- 全量回归协议 **40/40**、手机 **7/7**，`host-smoke.sh` 通过，候选包四项 checksum 全部 `OK`。

## 2026-11-06 最终完成消息现场样例

- 新增 `protocol/task-complete.example.json`，用于抓包和现场联调时区分 `task.complete` 与报告导出 JSON。
- `sync-candidate.sh`、`package-release.sh` 会把样例放入候选/正式包 `docs/`；脚本语法和候选 APK checksum 均通过。

## 2026-11-06 设备心跳闭环

- 眼镜端 SDK client 就绪后每 30 秒发送真实设备心跳，手机端解析并显示连接状态、电量和 App 版本；心跳不伪装成任务或报告同步成功。
- 新增 `device-heartbeat.example.json`，候选包和正式包均随 `docs/` 交付。
- 协议 Debug/Release **41/41**、手机端 Debug/Release **10/10**；`host-smoke.sh`、候选包 SHA-256 和离线预检通过。当前预检 0 个失败、7 个外部条件提醒，真实设备仍待现场。

## 2026-11-06 心跳电量未知值边界

- 修复 `BatteryManager.BATTERY_PROPERTY_CAPACITY` 不可用时回退成 `0%` 的误报；眼镜端现在跳过本次 `device.heartbeat`，下一周期重试，手机不会把未知电量显示为低电量。
- 主机回归与候选包校验随后完成并记录在“心跳补丁回归结果”；真实 Glass3 仍需现场确认电量属性可读性。

## 2026-11-06 手机端心跳超时可观测性

- 手机端新增独立眼镜状态栏：有效心跳显示连接、电量和版本，连续 90 秒未收到心跳时显示超时；任务消息不会覆盖该状态。
- 主机回归与候选包校验随后完成并记录在“心跳补丁回归结果”；真实 Glass3 仍需现场验证断链时序。

## 2026-11-06 心跳补丁回归结果

- `android/host-smoke.sh` 通过：协议 Debug/Release **41/41**、手机端 Debug/Release **10/10**，双端 lint、Debug/Release 构建和现场脚本演练通过；预检 0 个失败、4 个环境提醒。
- Web 基线通过：typecheck、lint、Vitest **46/46**、production build、Playwright **32/32**。
- 候选包已重新同步，四个 APK 的 `SHA256SUMS` 全部 `OK`。Release 仍为 unsigned，真实 Glass3、企业 AK/SK、签名和正式后端仍待现场条件。

## 2026-11-06 心跳 payload 严格类型校验

- 心跳 payload 现在严格要求版本/连接为 JSON 字符串，电量为整数且处于 0–100；新增小数电量和数字版本号拒绝回归。
- 重新执行 `android/host-smoke.sh`：协议 **41/41**、手机端 **10/10**，双端 lint、Debug/Release 构建和现场脚本演练通过；预检 0 个失败、4 个提醒。
- 候选包四项 APK `SHA256SUMS` 全部 `OK`。最新哈希：手机 Debug `69a9847f1138375e8e76dd0f0b9a4605bbbe99a83b3388cdab4c5e4d610bd74c`，眼镜 Debug `eeed7dd37fb8cb34631299447c045247b4d15296bf353b7d17692d5431bf557a`；手机 Release unsigned `fdbc3ac7a6e479393f27193d8bb23073daf598ae6fb4d1205106213de3fe3375`，眼镜 Release unsigned `fabf249befb5ee190e478e7c44ae2cba6e4a6350835ff6d1e9d14508ca1f6f2e`。

## 2026-11-06 候选包签名产物选择

- `sync-candidate.sh` 现在优先选择已签名 `*-release.apk`，未签名时使用 `*-release-unsigned.apk`；切换时清理旧文件名，避免候选 checksum 混入过期产物。
- 临时签名命名演练通过；当前候选包仍为 unsigned，真实企业签名尚未注入。
- `package-release.sh --skip-build` 在 unsigned APK 上于创建输出目录前失败，未留下伪正式交付目录。
- `local.properties.example` 已补齐企业签名四项空白配置模板，未写入任何真实凭证。

## 2026-11-06 候选包 README 证据刷新

- 候选包 README 改为由 `sync-candidate.sh` 按实际 APK 文件名和生成时间动态生成，清除了旧的 38/38、3/3 等陈旧数字。
- 修复 Markdown heredoc 转义问题后重新同步；README 内容、四项 APK checksum 和候选 `device-preflight.sh` 均已复核，预检 0 个失败、7 个提醒。
- README 检查命令已改为相对候选根目录，复核不含本机绝对路径。
- `host-smoke.sh` 已纳入候选包刷新、四项 checksum 和 README 可移植性断言。
- 重新运行 `android/host-smoke.sh` 通过：协议 **41/41**、手机端 **10/10**，双端 lint/Debug/Release 构建、现场脚本和候选包 checksum 均通过；预检 0 个失败、4 个提醒。

## 2026-11-06 候选包跨平台校验补强

- `sync-candidate.sh`、`package-release.sh`、`host-smoke.sh` 和 `device-preflight.sh` 现在会自动选择 `sha256sum` 或 `shasum`，候选包在 Linux 现场电脑和 macOS 开发机上都能生成、核对和检查 SHA-256。
- 使用仅保留系统 `/usr/bin:/bin` 的环境完成候选包同步和四个 APK 校验，四项均为 `OK`，证明不依赖本机 Homebrew 的哈希命令。
- 修改后重新执行 `android/host-smoke.sh`：预检 0 个失败、4 个环境提醒；协议 **41/41**、手机端 **10/10**、双端 lint、Debug/Release 构建、现场脚本演练和候选包校验全部通过。
- 真实手机、Glass3、企业 AK/SK、正式签名和正式报告服务仍未连接，继续保留为现场验收条件。

## 2026-11-06 交付前全量复测

- Web：`npm run typecheck`、`npm run lint`、`npm run test -- --run`（**46/46**）、`npm run build` 全部通过。
- Web 端到端：`PW_REUSE_SERVER=1 npm run test:e2e` **32/32** 通过，覆盖四号楼 12 套流程、三种屏幕尺寸、照片、暂停恢复、报告和 Android 任务文件导出。
- Android：`android/host-smoke.sh` 通过，协议 **41/41**、手机端 **10/10**，双端 lint、Debug/Release 构建、候选包四项 SHA-256 和现场脚本演练通过；预检 0 个失败、4 个环境提醒。
- 当前四个提醒是外部交付条件：企业 AK/SK、报告 HTTPS 地址、企业签名和真实手机/Glass3。它们没有被本机测试结果掩盖。

## 2026-11-06 双设备现场门禁补强

- 修正 `device-preflight.sh --require-device` 的判定：正式现场安装必须同时检测到手机和 Glass3 两台已授权设备，只有一台设备也会被拦截。
- 当前本机实际执行严格预检，明确返回“当前 0 台，需要手机和 Glass3 两台设备”，并以非零状态退出；这与当前没有真实设备的事实一致。
- 修改后重新执行 `android/host-smoke.sh`：协议 **41/41**、手机端 **10/10**、双端 lint、Debug/Release 构建、候选包四项校验和现场脚本演练全部通过；普通预检仍为 0 个失败、4 个提醒。

## 2026-11-06 断线后草稿重发补强

- 眼镜端现在会在 SDK 重新就绪后，读取本地保存的当前步骤草稿并重发 `step.draft`；手机端可以恢复显示最新读数、结果和说明。
- 这只同步草稿，不推进步骤，也不会绕过手机端的步骤确认；步骤仍必须经过 `step.complete` 和手机 ACK 才能前进。
- `android/host-smoke.sh` 通过：协议 **41/41**、手机端 **10/10**，双端 lint、Debug/Release 构建、候选包四项 SHA-256 和现场脚本演练全部通过；当前候选 APK 已刷新。
- Glass3 实体断线、重启和草稿重发仍需现场设备按 [SMOKE_TEST.md](../android/SMOKE_TEST.md) 实测，本机只验证了编译和逻辑边界。

## 2026-11-06 现场交接清单

- 新增 [android/FIELD_HANDOFF.md](../android/FIELD_HANDOFF.md)，用现场人员能直接执行的方式列出设备、企业资料、签名、安装、诊断和验收步骤。
- 候选包和正式打包流程都会把这份清单复制到 `docs/FIELD_HANDOFF.md`；`host-smoke.sh` 已检查候选包内文件存在。
- 清单明确要求同一批 APK、同一份 SHA-256、手机和 Glass3 两台设备，并保留断线、照片、草稿、实体按键和报告上传的现场证据。
- 真实设备和企业资料仍未到位，清单用于减少现场准备遗漏，不代表现场验收已经完成。

## 2026-11-06 交接入口复核

- `android/README.md` 已在开发入口和真机准备处链接 `FIELD_HANDOFF.md`，并明确 `--require-device` 需要手机与 Glass3 两台设备。
- 候选 README、候选 `docs/FIELD_HANDOFF.md` 和源码说明已互相对应；`android/host-smoke.sh` 重新通过，候选 APK 四项 SHA-256 全部 `OK`。
- 当前 `adb devices` 仍只有标题行，没有真实设备；严格预检继续正确失败，普通候选预检继续为 0 个失败、4 个提醒。

## 2026-11-06 候选现场文档可移植性复核

- 将 `android/SMOKE_TEST.md` 和官方 SDK 对照说明中的开发机绝对路径改为现场通用路径提示；交接人员在自己的电脑上替换为实际 SDK/JDK/工程目录即可。
- `android/host-smoke.sh` 新增候选包 `README.md` 和 `docs/*.md` 的绝对路径检查，避免把开发机目录带入交付包。
- 全量 `android/host-smoke.sh` 通过：协议 **41/41**、手机端 **10/10**、双端 lint、Debug/Release 构建、现场脚本演练和候选包四项 SHA-256 均通过；预检 **0 个失败、4 个外部条件提醒**。
- 最新候选包：`artifacts/rokid-rc-20261106/`；Release 仍为 unsigned，真实手机、Glass3、企业 AK/SK、正式签名和正式报告服务仍待现场条件。

## 2026-11-06 Android 12/12L 定位权限补齐

- 手机端清单补充 `ACCESS_COARSE_LOCATION`（仅 Android 12/12L），与已有的精确定位一起满足 Wi-Fi P2P 发现时的系统授权要求；Android 13 及以上继续使用附近 Wi-Fi 设备权限。
- 全量 `android/host-smoke.sh` 通过：协议 **41/41**、手机端 **10/10**、双端 lint、Debug/Release 构建、现场脚本演练和候选包四项 SHA-256 均通过。
- 预检仍为 **0 个失败、4 个外部条件提醒**；真实手机和 Glass3、企业凭证、签名与正式报告服务仍待现场。

## 2026-11-06 Android 12/12L 权限预检门禁

- `device-preflight.sh` 新增 APK 清单检查：手机包必须包含粗略定位权限；以后若误删该声明，构建前的预检会直接报错。
- 重新运行 `android/host-smoke.sh`：协议 **41/41**、手机端 **10/10**、双端 lint、Debug/Release 构建、现场脚本和候选包校验全部通过；新增权限检查为 PASS。
- 候选包已刷新至 `artifacts/rokid-rc-20261106/`，四项 APK SHA-256 均为 `OK`。

## 2026-11-06 候选包权限说明同步

- Smoke Test 明确 Android 12/12L 需要同时授予精确和粗略定位权限；候选包已重新复制现场文档。
- `sync-candidate.sh` 执行成功，四个 APK 的 `SHA256SUMS` 均为 `OK`；APK 内容未因文档同步改变。

## 2026-11-06 眼镜端连接恢复自动重发当前步骤

- 眼镜端在等待手机 `step.ack` 时，如果 SDK 连接先恢复、尚未到 8 秒超时，不再要求巡检人员重新点击完成；会自动重发同一条 `step.complete`。
- 该消息沿用原有任务/步骤/revision/commandId，手机端按幂等规则只保留一条完成记录。
- 修改后 `android/host-smoke.sh` 全量通过：协议 **41/41**、手机端 **10/10**、双端 lint、Debug/Release 构建、现场脚本演练和候选包四项 SHA-256 均通过；预检 **0 个失败、4 个外部条件提醒**。
- 最新候选 APK 哈希以 `artifacts/rokid-rc-20261106/SHA256SUMS` 为准；Release 仍未签名，真机仍待现场。

## 2026-11-06 手机端 SDK 监听注册失败保护

- 手机端注册蓝牙、P2P、消息和文件监听时增加安全边界；任一 Rokid 服务临时不可用时，应用显示可重试提示，不再把异常直接抛到页面。
- 如果只注册成功一部分，会先移除已注册监听，再允许下一次初始化，避免现场重复收到消息。
- 修改后 `android/host-smoke.sh` 全量通过：协议 **41/41**、手机端 **10/10**、双端 lint、Debug/Release 构建、现场脚本演练和候选包四项 SHA-256 均通过；预检 **0 个失败、4 个外部条件提醒**。

## 2026-11-06 Rokid 客户端编号可配置

- 手机端和眼镜端的 Rokid 客户端编号不再写死；两端从 `local.properties` 的 `rokid.clientId` 读取，默认仍为 `InspectionGlass`。
- `local.properties.example`、现场交接清单和 Smoke Test 已说明：企业如果登记了其他编号，只需两端使用同一个值后重新构建。
- 全量 `android/host-smoke.sh` 通过：协议 **41/41**、手机端 **10/10**、双端 lint、Debug/Release 构建、现场脚本演练和候选包四项 SHA-256 均通过；预检 **0 个失败、4 个外部条件提醒**。
- 当前候选包 Release 仍未签名，真实设备和企业凭证仍待现场。

## 2026-11-06 候选包补齐现场配置模板

- 候选包和正式 Release 打包流程现在都会带上 `docs/local.properties.example`；现场人员可以从同一交接目录复制配置模板，再填写自己的 SDK、企业凭证、客户端编号和签名信息。
- 主机回归新增模板存在性检查；`android/host-smoke.sh` 通过，协议 **41/41**、手机端 **10/10**、双端 lint/构建、现场脚本和候选包四项 SHA-256 均通过。
- 当前仍未配置企业凭证和正式签名，候选 Release 仍明确为 unsigned。

## 2026-11-06 现场工具路径自动发现复核

- 安装、诊断和预检脚本在找不到 PATH 中的 `adb` 时，会读取 `local.properties` 的 `sdk.dir`，自动使用其中的 `platform-tools/adb`；`aapt` 和 `apksigner` 也按同样方式查找。
- 使用只保留 `/usr/bin:/bin` 的 PATH 实测源码预检通过；这证明现场电脑不必先手工修改 PATH。
- 现场说明和候选包已同步该用法；当前仍只有四项外部提醒，没有真实手机/Glass3、企业凭证、正式签名和报告地址。

## 2026-11-06 交付状态复核

- 重新执行 `android/host-smoke.sh`：协议 **41/41**、手机端 **10/10**，双端 lint、Debug/Release 构建、现场脚本演练和候选包四项 SHA-256 校验全部通过；普通预检 **0 个失败、4 个提醒**。
- 重新执行 Web 端 `npm run typecheck`、`npm run lint`、`npm run test` **46/46**、`npm run build` 和 `PW_REUSE_SERVER=1 npm run test:e2e` **32/32**，全部通过。
- 单独执行 `android/device-preflight.sh --require-device` 时正确失败，报告当前 **0 台** Android 设备，需要手机和 Glass3 两台；这证明真机门禁没有被离线环境误放行。
- 新增 [LEADERSHIP_STATUS.md](../LEADERSHIP_STATUS.md)，说明当前已完成内容、软件与现场进度口径及设备到位后的验收顺序。
- 候选同步脚本现在同时带上 `docs/LEADERSHIP_STATUS.md`，并由 `host-smoke.sh` 检查文件存在；候选包仍通过四项 APK 校验。

## 2026-11-06 官方按键行为对照补强

- 对照官方 `RokidSuuport/glass3_sdk_demo` 最新主分支后，确认 Glass3 的 `CLICK/DOUBLE_CLICK` 广播在应用处理后应调用 `abortBroadcast()`，避免系统默认动作继续执行。
- 眼镜端单击/双击广播处理已补上安全消费；`android/host-smoke.sh` 增加源码静态门禁。
- 修改后主机回归仍通过：协议 **41/41**、手机端 **10/10**、双端 lint、Debug/Release 构建、现场脚本演练、候选包同步和四项 SHA-256 校验全部通过。

## 2026-11-06 按键补丁产物复核

- 为确认源码改动确实进入安装包，眼镜端 Debug Kotlin 编译和 APK 打包使用 `--rerun-tasks` 强制执行，随后重新运行 `android/host-smoke.sh`。
- 最新眼镜 Debug SHA-256 为 `1d804c955d7b9119931f17fd63b83d7a09149c70872c9ee9c02d7bbe5b1c5b79`，眼镜 Release unsigned SHA-256 为 `f340c5414ec7c322f110df27dc0c679f76869585faa52b1ebf4ba4b63994e7c0`；手机两项产物保持候选清单中的值。
- 候选包已重新同步，四个 APK `SHA256SUMS` 全部 `OK`，现场说明和官方对照记录也已同步。

## 2026-11-06 眼镜端权限清单对照复核

- 官方 Glass3 眼镜 Demo 的清单包含蓝牙扫描/连接、附近 Wi-Fi、定位和常亮权限；当前眼镜端已补齐同类声明。
- `device-preflight.sh` 现在从眼镜 APK 逐项核对 `BLUETOOTH_SCAN`、`BLUETOOTH_CONNECT` 和 `NEARBY_WIFI_DEVICES`，避免源码声明未进入实际安装包。
- 主机回归最终结果：预检 **0 个失败、4 个提醒**，协议 **41/41**、手机端 **10/10**，双端 lint、Debug/Release 构建、现场脚本演练和候选包四项 SHA-256 均通过。
- 最新眼镜 Debug SHA-256 为 `a0fbc3e475b73585d9947bd5382ac1e2ca69fbb0179450b9c0310bbaae788890`，眼镜 Release unsigned SHA-256 为 `067a81e1a53e85f731ea09eeb2b54fba5da881ba246ac0d3441e612a2e2fca62`。
- 候选包重新同步后，`docs/LEADERSHIP_STATUS.md` 与 `docs/ANDROID_ROKID_OFFICIAL_AUDIT.md` 均存在，四个 APK 校验仍全部 `OK`。
- 单独执行 `android/device-preflight.sh --require-device` 时，权限和 APK 检查均为 PASS，唯一失败项是当前 0 台 `adb` 设备；这确认现场门禁现在只拦截真实设备缺失。

## 2026-11-06 眼镜端监听失败清理复核

- 眼镜端 SDK 注册如果在 `registerClient` 与消息监听之间部分成功，失败回调现在会先移除已挂上的消息监听，再进入延迟重试，避免重连后重复接收一条消息。
- `android/host-smoke.sh` 通过：预检 **0 个失败、4 个提醒**，协议 **41/41**、手机端 **10/10**，双端 lint、Debug/Release 构建、现场脚本和候选包四项 SHA-256 全部通过。
- 最新眼镜 Debug SHA-256 为 `29f621915d6c9cb369494388b3d2b0b2dd02a95f6a26231b4c0ec9e05bc92160`，眼镜 Release unsigned SHA-256 为 `7188ac913d1e92efff1cca34f19711bfa053c6a5c9ea79435b298342ff83bb50`。

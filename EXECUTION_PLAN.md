# 执行计划

状态：本地 Demo 已完成（2026-10-28）。阶段 0–5 的本地开发与自动化验证完成；真实摄像头/系统权限及原生打印窗口未人工实测，按 README 现场补测。原任务包保留。

## 实施顺序

阶段 0 → 阶段 1 → 阶段 2 → 阶段 3 → 阶段 4 → 阶段 5。先通过 P0 验收，再安排 P1。每阶段结束更新进度、实际命令与结果，随后继续下一阶段。

## 阶段 0：工程与运行环境

目标：能够启动本地工程，目录、脚本和依赖可复现。

- [x] 检查目录、Node 与包管理器版本；保留现有文件和说明。
- [x] 初始化或复用 React + TypeScript + Vite 工程，记录版本并保留锁文件。
- [x] 加入图标、本地存储和测试依赖。只安装当前实际使用的组件。
- [x] 配置 dev、build、typecheck、lint、test、test:e2e、demo。
- [x] 固定开发与演示的 host/port，严格端口占用检测。
- [x] 建立 src/domain、repositories、services、features 与本地素材目录。

通过条件：启动页正常，typecheck 和 build 通过；README 写明本地启动方式。

## 阶段 1：最小完整巡检流程

目标：在一个页面完成下发、开始、逐步执行和最终提交，业务状态先正确。

- [x] 读取 seed 的资产、人员和五步模板。
- [x] 建立任务类型、步骤类型、校验函数和状态转换函数。
- [x] 下发保存模板／资产／人员快照，创建 assigned 任务。
- [x] 眼镜模拟区收到任务，点击开始进入设备确认。
- [x] 错误资产码给出提示，正确资产码确认后进入第 2 步。
- [x] 临时使用本地演示图片完成 2–4 步的照片、结果和异常说明。
- [x] 缺少照片、结果、异常说明时，完成动作显示缺项。
- [x] 每步完成后保存记录和时间，自动激活下一步。
- [x] 第 5 步汇总并确认提交，任务显示已完成。
- [x] 右侧进度、照片和异常数来自同一份任务数据。
- [x] 编写关键状态转换与校验单元测试。

通过条件：一次完整操作可达完成状态；异常登记可完成；无跳步；无手填假统计。

## 阶段 2：图片、持久化和恢复

目标：演示数据可靠保存，摄像头失败不影响展示流程。

- [x] 使用 IndexedDB 保存模板、任务、图片 Blob 与事件；加入初始化恢复状态。
- [x] 默认素材仅在空库首次创建，不覆盖恢复数据。
- [x] 通过 idb 事务实现图片写入与步骤关联、步骤提交与事件同步保存。
- [x] 图片来源包含本地演示素材、上传文件、电脑摄像头。
- [x] 显示图像缩略图、大图预览、来源标记和保存状态。
- [x] 上传校验格式／大小／可解码；拒绝错误文件并保留当前步骤。
- [x] 仅当前未完成步骤允许删除与重拍；完成步骤只读。
- [x] 保存失败提示重试，记录未保存时不推进步骤。
- [x] 完成操作防重入，过期 stepId 和重复 commandId 被拒绝。
- [x] 暂停保存草稿；恢复返回原步骤；刷新恢复已保存数据及照片。
- [x] 关闭采集弹层和重置时释放摄像头、object URL 和定时器。

通过条件：完成前两步后刷新，当前步骤与照片不丢；拒绝摄像头可用素材完成；连续点击只前进一步。

## 阶段 3：模板、历史与报告

目标：展示“配置驱动”与“过程留痕”的完整业务价值。

- [x] 模板页支持编辑步骤标题、说明、照片数量、结果要求。
- [x] 内部检查项支持增加、删除、上移、下移；首末步骤固定。
- [x] 模板输入校验和版本号更新；已下发任务使用原快照。
- [x] 在一个未完成任务的约束下，下发结束后可以新建下一任务。
- [x] 记录列表可打开历史任务；已完成任务只读。
- [x] 生成完整报告：基本信息、步骤、结果、照片、异常摘要、时间线。
- [x] 报告打印样式和 JSON 记录导出可用，图片元信息准确。
- [x] 二次确认后重置演示；照片、事件、模板状态一致恢复。

通过条件：修改模板只影响新任务；两次演示记录独立；报告照片可见；重置后无残留。

## 阶段 4：领导演示视觉与操作

目标：领导能够看清“下发—执行—留痕”的联动，演示人员能顺畅操作。

- [x] 完成白／灰／蓝的企业风格，关键步骤和按钮突出。
- [x] 三栏比例协调，模拟屏幕保持竖屏视觉；正文和照片不拥挤。
- [x] 统一状态、错误、空状态和加载提示。
- [x] 标注本地演示、模拟终端及演示素材；移除假在线状态和模拟置信度。
- [x] 鼠标、键盘、弹层焦点与表单输入互不冲突。
- [x] 在 1366×768、1440×900、1920×1080 检查布局并修复溢出。
- [x] 截取初始、缺照片提示、执行中、暂停恢复、报告五种状态。

通过条件：常见笔记本与会议屏分辨率下无横向溢出，无按钮遮挡，无需缩小浏览器才能操作。

## 阶段 5：回归测试与交付

目标：按验收清单重现主线和异常操作，整理领导演示入口。

- [x] 逐条核查 ACCEPTANCE.md 的 P0；33 条本地用例通过，B04 模拟代码路径通过、真实摄像头未人工实测；证据见 artifacts/acceptance-results.md。
- [x] 运行 typecheck、lint、test、build、test:e2e，记录真实结果。
- [x] 核查浏览器控制台、网络错误、重置、多次演示、历史数据隔离。
- [x] 使用 npm run demo 的构建产物重新走一遍主线。
- [x] 更新 README：环境、安装、启动、固定地址、重置、摄像头和打印说明。
- [x] 根据实际按钮更新 DEMO_SCRIPT.md，保证脚本与页面一致。
- [x] 汇总源码目录、测试结果、截图位置、P1 未完成事项。

通过条件：核心用例通过；源码、锁文件、素材、测试、文档齐全；运行命令可复现。

## 后续可选项

- [ ] 当前步骤语音播报。
- [ ] 眼镜视窗独立放大。
- [ ] 明确标识的模拟 AI 核验。
- [ ] 模拟网络中断与待同步队列。

## 实施日志（由 Codex 填写）

### 环境与启动

- `pwd` / `rg --files` / `ls -la`：确认任务包目录，无既有应用。`git status --short` 返回非 Git 仓库；未覆盖文件或擅自创建提交。
- `node -v` = v22.22.0；`npm -v` = 10.9.4。
- `npm install` 完成，新增锁文件。初次依赖审计报告 3 项开发依赖漏洞，后续升级已全部修复（最终 0 漏洞）。
- 固定 origin 为 `http://127.0.0.1:5173`，dev / demo 均 strictPort。

### 关键决策

单浏览器、React + TypeScript、IndexedDB、共享任务状态；核心先于扩展。命令层统一校验与事务，照片采集保留原任务与步骤绑定。完成按钮对检查步骤代表人员确认；设备与最终提交另有明确核对勾选。并行开发业务层、通用功能组件和验收测试。

### 问题与修复

- 初次 `npm install` 报告 3 个 Vitest 旧依赖漏洞。升级 Vitest 至 4.1.12 时 npm 10 出现 Arborist `edgesOut` 错误，两次安装失败；改用 `npx --yes npm@11 install` 成功，审计 0 漏洞。随后系统 npm 10 的 `npm ci` 锁文件复现通过（262 包，0 漏洞）。
- 首次整合 typecheck 提示 ES2022 不含 `toReversed`；替换为复制数组后 `reverse()`，随后 typecheck/lint/build 通过。
- 浏览器回归发现已暂停动作需等待事务完成再刷新，测试已按实际可见状态等待；真实照片和草稿能够恢复。
- 修复刷新后左侧设备/人员默认值与任务快照不一致；统一显示活动任务快照。
- 首末步骤可配置照片与结果要求，执行页统一渲染对应控件，避免配置产生无法完成任务。
- 原生弹窗 Tab 可移至浏览器外，新增可见控件循环；方向键过滤折叠内容中的隐藏按钮。
- 完成按钮忽略原生双击的第二次 click，叠加提交锁与 expectedStepId/commandId 业务校验，避免连续可选检查项被双击推进。
- 眼镜视窗保持竖屏及限定高度，内容可滚动，步骤标题/要求、可滚动表单、底部操作区分离，标题和完成按钮保持可见。报告独立页面显示，进入时回到顶部。
- 一次临时布局探查连接到已停止的 Playwright webServer，返回 ERR_CONNECTION_REFUSED；不作为应用运行失败或测试通过证据。后续以 Playwright 管理的实际服务验证布局。


### 测试结果

最终源码实际通过：
- `npm ci`：通过；`npm audit`：0 漏洞。
- `npm run typecheck`、`npm run lint`：通过，退出码 0。
- `npm run test`：36/36，通过 18 领域、8 存储事务、10 组件/边界测试。
- `npm run build`：通过，Vite 6.4.5；JS 306.59 kB / gzip 93.02 kB。
- `npm run test:e2e`：15/15 通过，42.4 秒。
- `DEMO_BUILD=1 npm run test:e2e`：15/15 通过，37.1 秒；真实运行 npm run demo 的构建产物。
- `npx playwright install chromium`：安装成功，测试使用 Playwright 1.64.1 Chromium。
- 1366×768、1440×900、1920×1080 共 15 张最终生产截图已保存并目视复核；当前标题/主按钮/缺项提示另有实际视口位置断言。
- 最终命令输出：`artifacts/checks.log`、`artifacts/e2e-dev.log`、`artifacts/e2e-production.log`。
- 完整测试边界、失败修复、版本及逐条证据：`artifacts/verification.md`、`artifacts/acceptance-results.md`。
- 中间开发回归有一次热更新导致节点卸载；保存失败日志并在稳定源码和构建产物上全部重新通过。不把中间失败记为通过。

### 交付状态

完整源码、package-lock.json、本地素材、36 项单元/组件测试、15 项端到端测试、15 张截图、运行说明和演示脚本齐全。无本地核心功能遗留问题。真实摄像头与系统权限须现场人工补测；P1 及真实终端/模型/跨网/账号按约定留待后续。

### 交付时运行状态

已实际运行 `npm run demo`，构建成功并在 `http://127.0.0.1:5173` 提供预览；`curl --fail -I` 验证 HTTP 200。服务保留运行，可直接打开浏览器演示。

### 本地预览复启（2026-10-29）

- 按用户请求运行 `npm run demo`：TypeScript 构建与 Vite 生产构建成功，预览服务启动在 `http://127.0.0.1:5173/`。
- `curl -fsS -I --max-time 5 http://127.0.0.1:5173/` 返回 HTTP 200。
- 本次为启动检查，未修改应用源码，也未重新执行全量功能测试。内置浏览器不可用，提供链接由用户在浏览器打开。

### 2026-10-30 重新启动预览

- 按用户要求运行 `npm run demo`，TypeScript 与 Vite 构建成功。
- 预览地址：`http://127.0.0.1:5173/`；HTTP 检查返回 200。
- 本次仅启动及验证服务，未修改业务源码，未重跑完整测试。

## 2026-11-03：接入4号楼电气与暖通巡检表

用户要求将两份巡检PDF流程纳入Demo。

- [x] 逐页阅读10页原表并整理为12套模板、30个对象、135个检查项；保留来源和各表实际时段。
- [x] 多模板选择与独立编辑、设备范围、巡检日期/时段/班次、工程师姓名记录。
- [x] 通用数值/选项/文本字段、严格/包含端点的阈值校验、额外运行状态及说明。
- [x] 报告展示原表来源、日程、状态、读数、单位、标准及故障时间；JSON保留完整快照。
- [x] 在IndexedDB原事务中增量补充目录，保留既有模板、任务和照片。
- [x] 更新演示脚本和来源映射文档BUILDING4_FLOWS.md。
- [x] 完成新旧流程全量测试、三尺寸视觉复核和构建产物验证。

决策：每次任务为一个区域/设备、一个时段；新增模板不凭空强制拍照。保留纸表明确数值及单位；UPS“40%以下”按≤40%解释并显式说明。AHU模式自由填写，未知阈值不自动判断。当前未加入自动派单、全天纸表矩阵、电子签名或真实终端连接。

初步验证：typecheck、lint、build通过；43项单元/组件测试通过。Playwright首次加载JSON遇到Node导入属性要求，改为明确读取JSON后继续运行；不得将首次失败计作通过。最终结果待补充。

最终结果：typecheck、lint、build通过，44/44单元与组件测试通过；开发版31/31端到端通过，最终构建版31/31通过（59.7秒）。三尺寸新增15张截图及原机柜15张回归截图完成。中间失败及修复、截图索引与人工边界见 artifacts/building4-verification.md。

预览已通过npm run demo启动于 http://127.0.0.1:5173/；curl -fsS -I --max-time 5检查返回HTTP 200，服务保留运行。

## 2026-11-05：Rokid Android 适配准备

- [x] 核对官方 Glass3 Enterprise 当前工具链、SDK 坐标、消息/文件、相机、按键能力。
- [x] 固定 Web 与 Android 的版本化消息协议样例和幂等字段（见 `protocol/`）。
- [x] 记录 `Template`、`Task`、`Command`、`EvidencePhoto` 到 Kotlin/Room/Rokid 分层的映射（见 `ANDROID_ROKID_ADAPTATION.md`）。
- [x] 找到官方最接近的双端查验工程 `scenario/dual-device-ai-inspection-report`，记录可复用的手机/眼镜边界（见 `android/README.md`）。
- [x] 创建独立 Android 工程骨架：`:protocol`、`:phone-app`、`:glass-app`，接入 Glass3 SDK 坐标和官方 Gradle Wrapper。
- [x] 实现最小消息链路源码：手机端 `task.dispatch`，眼镜端 `task.ack` / `step.complete`，保留 `commandId`、`taskId`、`expectedStepId`。
- [x] 接入眼镜端本地任务恢复、侧边触控按键和物理按键广播；手机端加入 Glass3 扫描连接入口。
- [x] 接入官方拍照 API、照片元数据消息和 JPEG 流回传；手机端写入 app-private 照片目录。
- [x] 将四号楼目录打包为手机端受控 asset；手机端可直接下发冷水机组模板，眼镜端按字段、阈值、结果、异常说明和照片数校验。
- [x] 下发任务携带巡检日期、时段和班次；手机端按模板联动对象/时段，眼镜端显示日程。
- [x] 手机端记录任务快照、完成步骤和照片，照片落盘后回传 `photo.ack`；眼镜端持久化步骤照片关联，重启后继续校验。
- [x] 确认实际型号为 Rokid Glass3 企业版；仍待补充固件、AK/SK、数据调试线和配套手机信息。
- [x] 使用本机 JDK 17、Android SDK 34 和 Gradle Wrapper 完成 Android 工程构建与 lint；`local.properties` 仅保留本机 SDK 路径。
- [x] 补充 `PhotoUploadPayload`、四号楼字段/日程模型、统一步骤校验和协议默认值解码，13 项协议/流程单测通过；生成并核验手机端、眼镜端 Debug APK。
- [x] 对齐 Web 的非正常状态规则：异常、停机、待机、不涉及均要求填写说明/原因。
- [x] 更新 `protocol/inspection-protocol.v1.json`，纳入日程、步骤字段/阈值，并加入设备无关的下发/照片/步骤/任务完成幂等流程测试。
- [x] 增加共享任务守卫，拒绝未知、过期和跳步完成消息，重复完成保持幂等并回传负确认。
- [x] 增加照片 MIME/尺寸/大小/SHA-256 校验；眼镜照片写入待发送队列，手机重启后仍可接收并按 photoId 幂等确认。
- [x] 增加手机端 P2P 自动/手动建连入口；眼镜端等待 `photo.ack` 和 `step.ack` 后才推进步骤。
- [x] 增加眼镜端待确认步骤持久化，Activity/SDK 重启后重发最后一次 `step.complete`。
- [x] 手机端任务快照持久化完整步骤结果、读数、异常说明和照片元数据，为后续报告与网络同步保留原始记录；当前使用 Room 活动任务槽位。
- [x] 手机端离线报告同步队列使用 Room 表和事务状态转换，兼容迁移旧版 SharedPreferences 队列。
- [x] 未配置企业凭证时手机端明确提示 `android/local.properties`，不再把空 AK/SK 交给 SDK 产生模糊初始化错误。
- [x] 增加 Glass3 企业版真机 Smoke Test 清单，覆盖安装、配对、任务、照片 ACK 和断网恢复判定（见 `android/SMOKE_TEST.md`）。
- [ ] 原样运行官方 Demo，再连真实设备验证独立工程。
- [ ] 完成“任务下发 → 眼镜显示 → 按键完成 → 回传 → 一张照片”的最小真机链路。

### 2026-11-05 Android 构建记录

- `export JAVA_HOME=/opt/homebrew/opt/openjdk@17; ./gradlew :protocol:test assembleDebug lint`：通过；协议/流程测试 12/12、两端 Kotlin 编译、两个 Debug APK 和 lint 均通过。
- `./gradlew test`：协议层 12/12 通过，两个应用暂无 Android 单元测试源，Gradle 正常报告 `NO-SOURCE`。
- `./gradlew lint`：通过；报告仅有依赖版本、字符串资源化、固定横屏/系统 API 适配，以及 Rokid SDK/MinIO 内部 TLS 相关警告，没有 lint error。
- APK 通过 `aapt dump badging` 核验：`com.inspection.demo.glass`（minSdk 29、targetSdk 34）和 `com.inspection.demo.phone`（minSdk 24、targetSdk 34）；手机 APK 内含 `assets/building4-catalog.json`，且与 Web seed SHA-256 一致。哈希与路径见 `android/README.md`。
- 使用 `/opt/homebrew/share/android-commandlinetools/platform-tools/adb devices` 检查，当前设备列表为空；未宣称完成真机安装、蓝牙/P2P、按键、相机或断网恢复验收。
- 首次构建因 shell 未设置 Java 和 SDK 路径分别失败；改用已安装的 JDK 17，并生成被 `.gitignore` 忽略的 `android/local.properties` 后复跑通过。该环境问题不属于源码缺陷。

### 2026-11-05 Android 报告导出补充

- 新增协议层 `InspectionReport`、`InspectionReportBuilder` 和 `InspectionReportCodec`，按任务步骤顺序输出模板/设备/人员/日程、读数、结果、异常说明和照片元数据。
- 手机端 `PhoneTaskStore` 增加单快照报告构建与 app-private JSON 导出；主界面增加“导出本地巡检报告 JSON”入口。
- `export JAVA_HOME=/opt/homebrew/opt/openjdk@17; ./gradlew :protocol:test :phone-app:compileDebugKotlin assembleDebug lint`：通过；13 项协议/流程测试通过，两个 Debug APK 和 lint 通过。Kotlin metadata 提示仍来自当前官方工具链组合，不影响构建。

### 2026-11-05 Android 照片绑定校验补充

- `TaskGuard` 现在使用已落盘照片元数据校验 `photoId`、`stepId` 和重复关联，拒绝把其他步骤照片绑定到当前步骤。
- `PhoneTaskStore` 改为传入完整照片元数据；新增错绑照片和重复照片单测。
- `export JAVA_HOME=/opt/homebrew/opt/openjdk@17; ./gradlew :protocol:test :phone-app:compileDebugKotlin assembleDebug lint`：通过；14 项协议/流程测试通过，两个 Debug APK 和 lint 通过。

### 2026-11-05 Android 离线同步队列补充

- 新增 `InspectionSyncEntry` 和 `InspectionSyncQueue`，实现报告按 `taskId` 幂等入队、发送中/成功/失败状态、退避重试和到期筛选。
- 手机端新增 `PhoneSyncStore`；任务完成后自动把报告 JSON 写入离线队列，界面可查看待同步数量，后续已接入 WorkManager 网络上传和状态转换。
- `export JAVA_HOME=/opt/homebrew/opt/openjdk@17; ./gradlew :protocol:test :phone-app:compileDebugKotlin assembleDebug lint`：通过；15 项协议/流程测试通过，两个 Debug APK 和 lint 通过。Kotlin metadata 提示仍来自当前官方工具链组合，不影响构建。

### 2026-11-05 Android WorkManager 上传骨架补充

- 手机端加入 `androidx.work:work-runtime-ktx`、网络约束和唯一 WorkManager 任务；任务启动时读取到期报告，按批次上传并复用本地成功/失败/退避状态。
- 上传端点通过未提交的 `android/local.properties` 配置 `inspection.syncUrl` 和可选 `inspection.syncToken`；URL 为空时 Worker 安全退出，不产生外部请求。
- 非 2xx/非重试型 HTTP 响应会进入 `rejected` 状态；上传地址格式错误或非 HTTPS 也不会进入无限重试。
- `export JAVA_HOME=/opt/homebrew/opt/openjdk@17; ./gradlew :protocol:test :phone-app:compileDebugKotlin assembleDebug lint`：通过；15 项协议/流程测试、两个 Debug APK 和 lint 通过。

### 2026-11-05 Android P2P 文件通道补充

- 对照官方 `glassFileOperater` / `getFileOperater` API 接入照片文件传输：眼镜端优先 P2P 文件，P2P 不可用时回退蓝牙文件，再回退原有字节流。
- 手机端同时注册 P2P 和蓝牙 `FileReceiveV2Listener`，文件完成后复用现有实际图片解码、尺寸、大小、SHA-256、落盘和 `photo.ack` 逻辑；文件传输失败仍保留眼镜端 outbox 重试。
- 手机端对文件回调与经典蓝牙 `photo.upload` 元数据做乱序容错：文件先到时暂存路径，元数据到达后再校验和确认，避免跨通道竞态丢照片。
- `export JAVA_HOME=/opt/homebrew/opt/openjdk@17; ./gradlew :protocol:test assembleDebug lint`：通过；16 项协议/流程测试、两个 Debug APK 和 lint 通过。文件通道尚未连接真实 Glass3 验收。

### 2026-11-05 Android 交付资源补充

- 两个应用补充可构建的矢量启动图标、备份规则和 Android 12+ 数据提取规则，消除缺少应用图标与备份配置的结构性资源问题。
- 重新运行 `export JAVA_HOME=/opt/homebrew/opt/openjdk@17; ./gradlew :protocol:test assembleDebug lint`：通过；15 项协议/流程测试、两个 Debug APK 和 lint 通过，剩余为依赖版本、字符串资源化、设备适配及第三方 SDK 内部 TLS 警告。
- 新 APK 哈希已同步到 `android/README.md`；真机链路仍需 Glass3、Android 手机、企业凭证和后端地址。

### 2026-11-05 Android Room 持久化补充

- 手机端活动任务快照由 `SharedPreferences` JSON 替换为 Room `inspection-phone.db` 单槽位实体；任务模板快照、完成步骤、照片元数据和步骤提交在同一 JSON 行内原子更新，避免多键偏好写入的部分提交。
- 保留旧版单 JSON 和五键偏好格式的一次性迁移；迁移先提交 Room，再清理旧键，进程中断不会丢失进行中的任务。
- `PhoneTaskStore` 对本地读取/写入加串行锁，并在发送任务前确认 Room 提交成功；任务切换、重复照片和重复步骤仍沿用原有幂等校验。
- `PhoneSyncStore` 同步迁移到 Room `inspection_sync_queue` 表，队列状态转换以事务替换提交；旧版同步偏好 JSON 在首次读取时迁移，避免 Activity 与 WorkManager 互相覆盖整队列。
- Room 2.8.2 编译器与 Kotlin 2.2.0 兼容；后续正式历史任务查询可在当前活动槽位基础上扩展实体，不改变手机/眼镜协议。
- `export JAVA_HOME=/opt/homebrew/opt/openjdk@17; ./gradlew clean :protocol:test assembleDebug lint :phone-app:assembleRelease :glass-app:assembleRelease --console=plain`：clean 构建通过；Room 注解处理、16 项协议/流程测试、双端 Debug/Release 和 lint 均通过。新的手机 Debug APK SHA-256 为 `962dd978a3ffb2549af3890629912b10c5b2817a889f648ed1c7c923f34258a6`，手机 Release unsigned APK SHA-256 为 `938b2b2afa61144d0b37a7ecce8444c8c96c57d8a00cd3a9e6210eeb604c9330`。

### 2026-11-05 Android 生命周期与同步失败保护

- 手机端 SDK 服务为空时明确失败返回；重复点击初始化不会重复注册经典蓝牙、P2P、消息和文件监听器，释放后可以再次初始化。
- WorkManager 上传在服务器成功后会先确认本地 Room 状态已写入；成功、失败、拒绝状态落盘失败均返回重试，避免报告停留在 `sending` 后永远没有下一次执行。
- WorkManager 上传关闭自动 HTTP 重定向，3xx 响应进入拒绝状态，避免 HTTPS 配置被服务端重定向到未知地址。
- `export JAVA_HOME=/opt/homebrew/opt/openjdk@17; ./gradlew :protocol:test :phone-app:compileDebugKotlin :phone-app:compileReleaseKotlin lint` 和随后 `./gradlew :phone-app:assembleDebug :phone-app:assembleRelease`：均通过；16 项协议测试、手机编译、APK 和 lint 均通过。当前手机 Debug APK SHA-256 为 `5a52d4b0783abd6679397d01d2f5f7e7948c1236ada8650e6ddcbb192966227c`，手机 Release unsigned APK SHA-256 为 `b556f99a24494bdd726a29ea192790f1b4a6bc2a5b4b29d45fff465904e6055d`。

### 2026-11-05 Android 现场交付细节

- 手机端管理界面改为 `ScrollView`，四号楼模板、对象、时段、日期、同步状态和下发操作在常见小屏上都可滚动访问，不依赖缩小字体或硬件键盘。
- 新增 [android/REPORT_SYNC_CONTRACT.md](android/REPORT_SYNC_CONTRACT.md)，固定 `InspectionReport` JSON、`Idempotency-Key`、HTTPS、状态码、重试/拒绝和服务端唯一约束，供后端直接联调。
- `export JAVA_HOME=/opt/homebrew/opt/openjdk@17; ./gradlew :protocol:test :phone-app:compileDebugKotlin lint :phone-app:assembleDebug :phone-app:assembleRelease :glass-app:assembleRelease --console=plain`：通过；16 项协议测试、手机编译、双端 APK 和 lint 均通过。当前手机 Debug APK SHA-256 为 `dac2b8dd4891afda92fc2b32d72a534adead42d0ab09b5299710861a5466dd1e`，手机 Release unsigned APK SHA-256 为 `a9cbba2680c0d19bc8040f3c38a9f08ad3a3781ff126996e304b4081be1ed87c`。

### 2026-11-06 Android 任务历史补充

- Room 数据库升级到 v3，新增 `inspection_task_history` 表；每次活动任务快照提交与历史记录在同一 Room 事务写入，保留模板快照、步骤完成数、状态和更新时间。
- 手机端新增“查看本地任务记录”入口，显示最近任务及完成进度；旧版 v1/v2 数据库通过迁移继续可用，活动任务与同步队列不受影响。
- `export JAVA_HOME=/opt/homebrew/opt/openjdk@17; ./gradlew :protocol:test :phone-app:compileDebugKotlin lint :phone-app:assembleDebug :phone-app:assembleRelease --console=plain`：通过；18 项协议测试、手机编译、APK 和 lint 均通过。
- 新增 `ReportSyncPolicy` 端点/状态分类纯逻辑和 2 项单测；协议测试从 16 项增至 18 项，覆盖 HTTPS 主机、用户信息、2xx/3xx/4xx/408/429/5xx 边界。WorkManager 已复用该策略。
- `export JAVA_HOME=/opt/homebrew/opt/openjdk@17; ./gradlew :protocol:test :phone-app:compileDebugKotlin lint :phone-app:assembleDebug :phone-app:assembleRelease --console=plain`：通过；18 项协议测试、手机编译、APK 和 lint 均通过。当前手机 Debug APK SHA-256 为 `c4f2e4dc3d8f038e5289749980800808887ede117560434041e7796299150fbb`，手机 Release unsigned APK SHA-256 为 `2e85808f143d38ff4c341703e7141772c5148af59654f3a87064645be8c76c78`。

### 2026-11-06 Android 最终构建复核

- Room v3 任务历史迁移、旧版 SharedPreferences 首次打开历史页迁移逻辑已重新编译；协议测试 18/18 通过，手机编译、双端 Release、lint 均通过，lint XML 未发现 `severity=Error`。
- 当前手机 Debug APK SHA-256 为 `4f9f3c5b2b329865fd57f3c2bd35e0215affb818beb6c4b757ad0375321088a3`；手机 Release unsigned APK SHA-256 为 `ae579d8d1a5d57f9887fd380376373811e4b6bf5b1a8c15a74c139eba681a79b`；眼镜 Release unsigned APK SHA-256 为 `fd48c533bcae2b4d83235391d77d48e6d886b3c9b1d046c5173252b44a7d2578`。
- `adb devices` 当前没有真实手机或 Glass3 Enterprise 设备；蓝牙/P2P、实体按键、相机、文件传输和现场安装仍待真机与企业签名/凭证联调。

### 2026-11-06 Android 照片通道关联加固

- 新增协议层 `PhotoTransferMatcher`：文件先到或元数据先到时，按 `photoId` 或原始文件名严格匹配，拒绝相近文件名造成的跨任务错绑。
- 手机端不再用 SDK 回调文件名作为照片身份；以 `photo.upload` 的 `photoId` 写入 Room，且按声明 MIME 保留 `.jpg`、`.png`、`.webp` 扩展名。
- 新增照片通道关联边界测试；协议测试现为 19/19，通过手机编译、双端 Release 和 lint，三个 lint XML 均为 0 个 Error。
- 当前手机 Debug APK SHA-256 为 `5322f6fb80bdb8808f5e1d204e48eb9cdf701bfa0f67cd75d66a02e3acc0fb1c`；手机 Release unsigned APK SHA-256 为 `b6f17e6fc2917cc404043134b0f851e7c7318e39308b179268badc0bb2392907`；眼镜 Release unsigned APK SHA-256 为 `c6423322075b90e671bf9cef5bb71f6e461c9bf929ab4de9395254bcb2940e74`。
- 眼镜端拍照入口增加运行时 `CAMERA` 权限检查；未授权时不会调用相机 API，界面会提示授权后重试。新增改动后双端构建仍通过；眼镜 Release unsigned APK SHA-256 更新为 `c6423322075b90e671bf9cef5bb71f6e461c9bf929ab4de9395254bcb2940e74`。

### 2026-11-06 Android 现场环境检查

- 新增可执行的 `android/device-preflight.sh`，只读检查 JDK 17、Gradle Wrapper、Android SDK、企业 AK/SK 是否完整、HTTPS 同步地址、签名四项、Debug APK 和 `adb` 设备；默认不因未连接设备退出，`--require-device` 可用于现场安装前硬校验。
- 在当前无真机、无企业凭证的环境执行通过：0 个失败、4 个提醒；提醒内容仅说明配置缺失，不输出任何凭证或 token。

### 2026-11-06 Android 明文流量边界

- 手机端 Manifest 增加 `android:usesCleartextTraffic="false"`，与 `ReportSyncPolicy` 的 HTTPS 校验和 3xx 拒绝策略一致；报告同步接口文档已同步说明。
- `export JAVA_HOME=/opt/homebrew/opt/openjdk@17; ./gradlew :protocol:test :phone-app:compileDebugKotlin lint :phone-app:assembleDebug :phone-app:assembleRelease --console=plain`：通过；协议测试 19/19，lint 三个模块 0 个 Error。
- 当前手机 Debug APK SHA-256 为 `f6a37492bdda16e4d8d1d533ea37ead10cf6b26288541ed1e2f0ba93d46b5301`；手机 Release unsigned APK SHA-256 为 `e9fe37c79bc6b5fa3a74e70728aab6db3260d24b91278a029f1ef599f3a5e5b2`；眼镜 Release unsigned APK SHA-256 为 `c6423322075b90e671bf9cef5bb71f6e461c9bf929ab4de9395254bcb2940e74`。

### 2026-11-06 Android 眼镜端持久化确认

- 眼镜端任务快照、待确认步骤和照片 outbox 改为同步 `commit()`；只有本地写入成功才发送 `task.ack`、`step.complete` 或照片元数据，避免进程被杀时 ACK 已发而本地状态未落盘。
- 手机已确认步骤但眼镜本地推进保存失败时，保留原完成 payload，允许按原 `taskId/stepId/revision` 幂等重试；照片 ACK 后也先完成本地写入再更新内存状态。
- 照片 ACK 处理改为任务快照成功后才删除眼镜 outbox；写盘失败时保留照片和待关联步骤，下一次 SDK/Activity 恢复仍会重发。
- `export JAVA_HOME=/opt/homebrew/opt/openjdk@17; ./gradlew :protocol:test :phone-app:compileDebugKotlin lint :phone-app:assembleDebug :phone-app:assembleRelease :glass-app:assembleRelease --console=plain`：通过；协议测试 19/19，lint 三个模块 0 个 Error。
- 当前手机 Debug APK SHA-256 为 `f6a37492bdda16e4d8d1d533ea37ead10cf6b26288541ed1e2f0ba93d46b5301`；手机 Release unsigned APK SHA-256 为 `e9fe37c79bc6b5fa3a74e70728aab6db3260d24b91278a029f1ef599f3a5e5b2`；眼镜 Release unsigned APK SHA-256 为 `b218bf8d2ca175f56b6694bb2900ea5a05cdefc5e0803f5a0ef5ddd167dce8b2`。

### 2026-11-06 Android 相机元数据复核

- 眼镜拍照回调先用 `BitmapFactory` 从实际文件头读取宽度、高度和 MIME，再生成 `PhotoUploadPayload`；手机端原有解码、尺寸、大小和 SHA-256 校验保持不变。
- 对照 Rokid 官方 Sprite Enterprise 文件传输示例核对 `glassFileOperater.sendFile`、蓝牙文件回退和 `sendStreamData` 调用边界；本地 AAR 方法签名一致。
- 完整命令 `./gradlew :protocol:test :phone-app:compileDebugKotlin lint :phone-app:assembleDebug :phone-app:assembleRelease :glass-app:assembleRelease --console=plain`：通过；协议测试 19/19，lint 三个模块 0 个 Error。
- 当前眼镜 Release unsigned APK SHA-256 为 `b218bf8d2ca175f56b6694bb2900ea5a05cdefc5e0803f5a0ef5ddd167dce8b2`。
- 眼镜端拍照回调不再盲信固件传来的宽高和格式，先从实际文件头读取尺寸/MIME 再生成元数据；手机端仍会做完整解码、尺寸、大小和 SHA-256 校验。

### 2026-11-06 Android 草稿恢复与现场检查增强

- 眼镜端 `GlassTaskStore` 新增当前步骤草稿快照，读数、结果和说明在 Activity/SDK 重启前同步 `commit()`；重新进入任务时恢复表单值，不推进步骤。
- 新增 `step.draft` 协议编解码和当前步骤顺序校验；眼镜在本地草稿落盘成功后尽力同步到手机，手机以 Room 快照保存草稿，完成步骤后清理对应草稿，晚到的草稿不会覆盖已提交结果。
- 协议测试新增草稿往返和顺序/重复校验，当前测试总数为 21 项；完整命令 `./gradlew :protocol:test :phone-app:lint :glass-app:lint :phone-app:assembleDebug :phone-app:assembleRelease :glass-app:assembleDebug :glass-app:assembleRelease --console=plain` 通过，Lint 三个模块均为 0 个 Error。
- 本轮 APK SHA-256：手机 Debug `142d4855a81540aec610aa6867ec47cdb2aaf708b72887570426c88356ecd9cd`，手机 Release unsigned `26a284e0358591b86b9d3a4187d7b721caca466f7ce8246d213933c67916103a`，眼镜 Debug `842d0124d58d8150f7053d26f67d029717791f711d92c3d20dd9c32f8cbe4c1f`，眼镜 Release unsigned `31b71d743ca2b123ac001a8a2ad40ccf492cdce7c55c0fa10163e27398bdb580`。
- `device-preflight.sh` 现在在未设置 `JAVA_HOME` 时自动探测 macOS JDK 17/Homebrew JDK 17；独立执行结果为 0 个失败、4 个提醒。当前提醒仍是企业 AK/SK、同步 URL、Release 签名和未连接真机。
- 真机蓝牙/P2P、相机、物理按键、文件传输和正式后端仍需 Glass3 Enterprise、Android 手机、企业凭证、签名和服务端条件；本轮没有把这些未执行项目标记为通过。

### 2026-11-06 Android 双端任务覆盖保护

- 手机端原有 Room 活动快照保护抽成协议层 `TaskGuard.activeDispatchRejection`，并增加单测覆盖：相同 `taskId` 幂等，已完成任务可替换，未完成任务拒绝覆盖。
- 眼镜端收到不同 `taskId` 时也检查当前快照；已有未完成任务会保留原任务、回传 `task.ack(accepted=false)` 和原因，不再直接清空步骤、草稿或照片队列。
- 重新执行 `./gradlew :protocol:test :phone-app:compileDebugKotlin :glass-app:compileDebugKotlin :phone-app:lint :glass-app:lint :phone-app:assembleDebug :glass-app:assembleDebug --console=plain`：通过；协议测试 23/23，两个 Debug APK 和 lint 通过，lint 无 Error。

### 2026-11-06 Android 下发消息结构校验与重启恢复

- 协议层增加模板、设备、执行人、步骤编号和字段类型校验；手机端和眼镜端均在保存前拒绝不完整或重复编号的下发消息。
- 手机端启动时读取 Room 活动快照并立即恢复进度和报告导出入口；眼镜端本地清理改为同步提交，SDK 监听注册增加幂等保护。
- 重新执行同一 Gradle 命令：通过；协议测试 24/24，两个 Debug APK 和 lint 通过，lint 无 Error。Kotlin metadata 提示来自当前官方工具链组合，不影响构建结果。

### 2026-11-06 Android 步骤时间与异常留痕

- Android 眼镜端新增故障发现时间输入，仅在有原表来源且选择异常时显示；时间格式按 `HH:mm` 校验，并随草稿、`step.draft`、`step.complete` 和报告快照保存。
- `StepCompletePayload` 新增 `completedAt`，手机导出报告保留每步确认时间；协议 JSON Schema 和编解码单测同步更新。
- 重新执行 Android 构建与 lint：通过；协议测试 25/25，两个 Debug APK 和两个 Release unsigned APK 通过，lint 无 Error。

### 2026-11-06 Android 最终产物复核

- 重新生成手机端和眼镜端 Debug/Release unsigned APK；`assembleRelease`、协议测试和两个应用 lint 均通过。
- 最新 Debug APK：手机 SHA-256 `ae7d2a62b44254f77c2501554b78f363109a2f5fc7c2e76ac7d22627d37a8279`，眼镜 SHA-256 `4bbfa0d07c9cdd82a32edb685b820b3064fb271b3737a479a7e8cab335f35e0b`。
- 最新 Release unsigned APK：手机 SHA-256 `ebd3ac9c0326ccddbe52447db4a81ff90a5bde2cc71981a344c4b7ea24d2af8d`，眼镜 SHA-256 `7c5e6fe22c3b84b8b07e21cd0956a849f1b3e67d7af8a5e18f18af3059128e11`。

### 2026-11-06 Android 开始巡检与设备确认补齐

- 眼镜收到任务后先停留在确认页，点击“开始巡检”才持久化开始状态并发送 `task.start`；旧版本快照没有开始标志时按原可执行状态兼容恢复。
- `asset_check` 现在要求资产码与任务资产严格一致，并要求确认设备名称/位置；`review_submit` 要求确认已核对全部巡检记录。协议增加 `assetCode`、`confirmed` 和 `faultTime` 字段，避免 Android 端跳过 Web Demo 的关键确认步骤。
- 更新 [android/SMOKE_TEST.md](android/SMOKE_TEST.md) 的开始、资产确认和最终提交验收步骤；新增协议边界测试后当前测试总数为 22 项。完整命令 `./gradlew :protocol:test :phone-app:lint :glass-app:lint :phone-app:assembleDebug :phone-app:assembleRelease :glass-app:assembleDebug :glass-app:assembleRelease --console=plain` 通过，Lint 三个模块均为 0 个 Error。
- 本轮 APK SHA-256：手机 Debug `5a3458536449d3ea1596529781ad833e1e49d8cbd51651e30f765a8b7d37e7dd`，手机 Release unsigned `7edb1040ad5562fe528f5441d607e3ad3e14fe40b6152b2204e56453e5321a20`，眼镜 Debug `fc65a0dffee502ea1c577f3e8a4e897498ca1cab16dd7998b85078e8c5348f93`，眼镜 Release unsigned `e44d6b300e0882271b6b75fe2c67d7cbfb1155eab9868916d4a4c61266fa8579`。
- 真机链路仍未执行：当前 `adb devices` 为空，企业 AK/SK、生产签名和同步后端未配置。

### 2026-11-06 Android 暂停与继续

- 眼镜端增加“保存草稿并暂停/继续巡检”操作，暂停状态、当前步骤和草稿同步落盘；重启后仍停留在暂停页，继续后恢复原步骤，不会跳步。
- 增加 `task.pause` / `task.resume` 控制消息和手机端状态反馈，更新现场 Smoke Test。
- 本轮仅涉及眼镜 UI/状态与文档，下一步仍需真实 Glass3 验证广播按键、暂停恢复和连接断开时的消息顺序。
- 暂停改动后 `./gradlew :protocol:test :glass-app:compileDebugKotlin :glass-app:lint :glass-app:assembleDebug :glass-app:assembleRelease --console=plain` 通过，Lint 仍为 0 个 Error；眼镜 Debug SHA-256 `2a8f7ef347cfea572061f03cba00c3e22ffea96483597df339f687994762a675`，Release unsigned SHA-256 `09042a47c4f85e68c39c5611910b39a0bcdba26b4911f03e4e18bbfa2e8aaa72`。

### 2026-11-06 Android 未完成任务覆盖保护

- 手机端下发新任务前检查 Room 活动快照；已有不同任务且未完成时拒绝覆盖并显示原因，完成旧任务后才允许建立新任务。相同 `taskId` 仍保持幂等。

### 2026-11-05 Android 同步队列恢复补充

- 为上传队列增加 `sendingAtEpochMs` 和 5 分钟超时恢复：进程在网络请求中断后不会永久卡在“发送中”，恢复后的任务继续遵循既有退避策略，且沿用 `Idempotency-Key` 保证重试幂等。
- 新增进程重启/发送中断单测；协议与流程测试从 15 项增至 16 项。
- `export JAVA_HOME=/opt/homebrew/opt/openjdk@17; ./gradlew :protocol:test :phone-app:compileDebugKotlin assembleDebug lint`：通过；16 项协议/流程测试、两个 Debug APK 和 lint 通过。

### 2026-11-05 Android 权限兼容补充

- 手机端按 Android 版本分支申请附近设备权限：Android 13+ 加入 `NEARBY_WIFI_DEVICES`，Android 12 只申请蓝牙扫描/连接，旧版本沿用经典蓝牙和定位权限。
- 权限被拒绝时不再继续初始化企业 SDK，界面会明确提示需要的权限，避免把权限失败误报成 SDK 连接失败。
- `export JAVA_HOME=/opt/homebrew/opt/openjdk@17; ./gradlew :protocol:test assembleDebug lint`：通过；16 项协议/流程测试、两个 Debug APK 和 lint 通过。

### 2026-11-05 Android 照片格式校验补充

- 手机端在 JPEG/PNG/WebP 解码成功后，进一步比较 Android 解码器识别出的实际 MIME 与眼镜端声明的 MIME，拒绝扩展名或元数据伪装的图片。
- `export JAVA_HOME=/opt/homebrew/opt/openjdk@17; ./gradlew :protocol:test assembleDebug lint`：通过；16 项协议/流程测试、两个 Debug APK 和 lint 通过。

### 2026-11-05 Android 文件元数据恢复补充

- 手机端将“文件先到、元数据后到”的照片路径写入持久化待匹配记录；进程在两个回调之间退出后，收到元数据仍能找到文件并复用完整校验、落盘和 `photo.ack` 流程。
- 元数据写入改为同步提交，只有照片校验和本地任务保存完成后才清理待匹配记录。
- `export JAVA_HOME=/opt/homebrew/opt/openjdk@17; ./gradlew :protocol:test assembleDebug lint`：通过；16 项协议/流程测试、两个 Debug APK 和 lint 通过。

### 2026-11-05 Android 消息发送失败反馈补充

- 手机端和眼镜端的控制消息发送现在检查消息服务是否存在，并捕获 SDK 同步抛出的发送异常；消息服务不可用或发送失败时不再显示“已发送”，同时保留当前任务/步骤状态等待重试。
- `export JAVA_HOME=/opt/homebrew/opt/openjdk@17; ./gradlew :protocol:test assembleDebug lint`：通过；16 项协议/流程测试、两个 Debug APK 和 lint 通过。

### 2026-11-05 Android 蓝牙通道状态反馈补充

- 手机端发送控制消息前检查经典蓝牙消息通道是否已连接；未连接时明确提示并保留可重试状态，避免把未发送的任务/步骤 ACK 显示成成功。
- `export JAVA_HOME=/opt/homebrew/opt/openjdk@17; ./gradlew :protocol:test assembleDebug lint`：通过；16 项协议/流程测试、两个 Debug APK 和 lint 通过。

### 2026-11-05 Android 控制消息 P2P 回退补充

- 手机端接收经典蓝牙和 P2P 两种文本回调；发送控制消息优先使用已连接的经典蓝牙，经典蓝牙消息通道不可用时检查 P2P 状态并回退发送。
- 眼镜端同样在经典蓝牙不可用时检查 P2P 状态并通过 `sendTextMessageByP2PWithClient` 回退发送，原有消息 ACK 和任务状态机不变。
- `export JAVA_HOME=/opt/homebrew/opt/openjdk@17; ./gradlew :protocol:test assembleDebug lint`：通过；16 项协议/流程测试、两个 Debug APK 和 lint 通过。

### 2026-11-05 Android 照片元数据 P2P 回退补充

- 眼镜端 `photo.upload` 元数据现在与任务/步骤 ACK 使用相同的通道策略：经典蓝牙可用时优先使用，否则检查 P2P 并发送元数据，元数据成功后才启动照片文件传输。
- 避免照片文件已经通过 P2P 发出但手机没有对应元数据，导致文件永久停留在待匹配状态。
- `export JAVA_HOME=/opt/homebrew/opt/openjdk@17; ./gradlew :protocol:test assembleDebug lint`：通过；16 项协议/流程测试、两个 Debug APK 和 lint 通过。

### 2026-11-05 Android 任务 ACK 反馈补充

- 手机端解析 `task.ack` 的 `accepted` 和 `reason` 字段；眼镜拒绝任务时显示拒绝原因，不再误报为任务已确认。
- `export JAVA_HOME=/opt/homebrew/opt/openjdk@17; ./gradlew :protocol:test assembleDebug lint`：通过；16 项协议/流程测试、两个 Debug APK 和 lint 通过。

### 2026-11-05 Android 本地配置转义补充

- 手机端 BuildConfig 生成统一转义 AK/SK、同步 URL 和 token 中的反斜杠、引号与换行，避免企业凭证或服务端 token 的特殊字符破坏 Gradle 生成源码。
- 使用临时包含特殊字符的本地配置验证 `generateDebugBuildConfig` 成功，随后恢复干净配置并重新构建 APK；测试凭证未写入最终产物。
- `export JAVA_HOME=/opt/homebrew/opt/openjdk@17; ./gradlew :protocol:test assembleDebug lint`：通过；16 项协议/流程测试、两个 Debug APK 和 lint 通过。

### 2026-11-05 Android Release 构建补充

- 新增 Release 构建核验：`./gradlew :phone-app:assembleRelease :glass-app:assembleRelease` 通过，生成手机端与眼镜端 `*-release-unsigned.apk`。
- Release APK 已用 `aapt dump badging` 核对包名、targetSdk 34 和应用图标，并检查不包含临时测试凭证；正式安装仍需企业签名证书、包名规则和现场授权。
- Release 构建过程只出现 Kotlin metadata、第三方原生库 strip 和既有 lint 警告，没有构建错误。

### 2026-11-06 Android 交付证据同步

- 重新运行完整 Android 校验命令：`./gradlew :protocol:test :phone-app:compileDebugKotlin :glass-app:compileDebugKotlin :phone-app:lint :glass-app:lint :phone-app:assembleDebug :glass-app:assembleDebug :phone-app:assembleRelease :glass-app:assembleRelease --console=plain`，构建成功；协议测试 **26/26**，两个应用 lint 无 Error，Debug/Release unsigned APK 均生成。
- 通过 `device-preflight.sh`：0 个失败、4 个提醒。提醒为未配置企业 AK/SK、报告同步 URL、Release 签名和未连接真机，均是外部现场条件，不是本地构建失败。
- 用 `aapt dump badging` 核对四个 APK：手机包名 `com.inspection.demo.phone`、眼镜包名 `com.inspection.demo.glass`，`minSdk` 分别为 24/29，`targetSdk` 均为 34。
- 最新 Debug APK：手机 `e6ccfd544ca959cda674e86ca5bb0a22667b341c99fa4a4ce166b3d1decd3c4a`，眼镜 `4bc8f950868531e614a63d5748bac1525188f23e8d18d461cbb6bf02fffd027d`。
- 最新 Release unsigned APK：手机 `a2055d29be1664ac0c27d80638056d10f95c18ee5342d71684f8921b0c32ff4d`，眼镜 `23315cd00b7f4b47238e19123fb7d590b14fb47daf2fd502a7e11dfbdc3f602b`。

### 2026-11-06 Android 照片拒绝回执补齐

- 手机端在照片解码、MIME、尺寸、大小或 SHA-256 校验失败时回传 `photo.ack(accepted=false)` 并清理待匹配元数据；眼镜端收到负回执后清理不可重试的照片 outbox，避免损坏文件在每次重启后无限重发。
- 眼镜端步骤收到 `step.ack(accepted=true)` 后清理已提交步骤草稿；若本地快照写入失败则恢复旧草稿和待确认提交，保持可重试。
- 新增负 `photo.ack` 编解码回归测试；协议测试 **27/27**。
- clean 全量命令 `./gradlew clean :protocol:test :phone-app:compileDebugKotlin :glass-app:compileDebugKotlin :phone-app:lint :glass-app:lint :phone-app:assembleDebug :glass-app:assembleDebug :phone-app:assembleRelease :glass-app:assembleRelease --console=plain`：通过；两个应用 lint 无 Error，四个 APK 生成。
- clean 构建后最新 Debug APK：手机 `7ad85a43b06a3ced1b659989fc7e08de12bde637f7829f017328c6681fb12499`，眼镜 `84ddd59ea674cadf93d340607eb59437716e29aa258320dde4213914dea10452`；Release unsigned：手机 `d134fc1f89ba8fcba54db387ee6ebe387e20c01c14b1f2e93281902769a79e7f`，眼镜 `08c7b1afc8efd7148495c36a503b98d87431bc1b29c641a1451ebbcea631e153`。

### 2026-11-06 Android SDK 延迟注册保护

- 手机端只有在 `getMessageService()` 成功取得后才把监听标记为已注册；消息服务暂时为空时初始化返回失败并允许重新尝试，避免出现“SDK 已连接但收不到任务”。
- 眼镜端增加注册进行中保护，并在 SDK client 回调后重试消息服务注册，避免同步回调造成重复注册或服务启动竞态。
- `:protocol:test`、手机/眼镜 Debug 编译和 lint、手机/眼镜 Release 构建均通过；协议测试仍为 **27/27**，lint 无 Error。
- 本轮最新 APK：手机 Debug `f4704827c337cd469a98559f91b3ee235d22057d3c54b1c94f706399bb6c7ac1`，眼镜 Debug `abaabf6adcdeecd8bdc432df4ec22479308b40a7559779e04ffa542edbb3ffce`；手机 Release unsigned `03cd6650cc80f216fc192487ea9f0c80c1319710014c6579896c3adceeab1fdc`，眼镜 Release unsigned `a322b6f64d7d9a1b0d3061b4d252ff6926a405761cb363453b798e930d56524b`。

### 2026-11-06 Android 照片 outbox 运行中恢复

- 眼镜端照片 outbox 在消息服务不可用、蓝牙/P2P 未连接、文件传输失败或照片 ACK 丢失时，保留本地文件并安排约 10 秒后重试；收到负 `photo.ack` 后仍立即清理终止项。
- Activity 销毁时取消重试 Handler，避免页面退出后继续发送；恢复连接无需重启应用即可继续照片回传。
- 完整 Android 构建命令通过；协议测试 **27/27**，手机/眼镜 lint 无 Error，Debug/Release APK 均生成。
- 本轮最新眼镜 Debug APK：`1e93bbe26f51f8253df56a68c6e50c1a2c23115603f5f1f1e0118275723ea21f`；眼镜 Release unsigned：`09221137f806f9362b6d5d2f16e131a0c1f56b1a054694bf9e976e0efe4aef`。

### 2026-11-06 Android ACK 重放去重

- 协议层新增 `ProtocolReplayLedger`，同时记录 `commandId` 和 `messageId`，限制记录大小并可从本地字符串集合恢复。
- 眼镜端对 `step.ack` / `photo.ack` 做持久化去重；重复 ACK 不再重复推进界面或重复计入照片。`task.dispatch`、`step.complete` 和照片上传仍保留原有幂等重试与 ACK 语义，避免 ACK 丢失时被错误吞掉。
- 新增命令重试、无 commandId 消息重试和有界淘汰测试；协议测试 **30/30** 通过。
- `./gradlew :protocol:test :glass-app:compileDebugKotlin :phone-app:compileDebugKotlin :phone-app:lint :glass-app:lint :phone-app:assembleDebug :glass-app:assembleDebug --console=plain`：通过，lint 无 Error。
- 本轮 Debug APK：手机 `25030523a78188270d1e97fb13a303124cb1324a777b1b9ca18607fcc0baa26e`，眼镜 `bbaa9d513b1e16bcd012d92bdb06f6e5070e4e6852ed993bbd775e21ee6fe875`；Release unsigned：手机 `486f641ed41d8503b63fc85ddbc692789710a4b0708ec218ec127f43b145217c`，眼镜 `3e5dcbda290ad154e9eb0dda4802874c9edb6d2acfc6a8c28f026a6f7726a557`。

### 2026-11-06 Android 报告上传本地 HTTP 联调

- 将 WorkManager 的 `HttpURLConnection` 请求抽到 `ReportUploadHttpClient`，生产路径仍强制 HTTPS、关闭重定向并携带 `Idempotency-Key` / Bearer token。
- 增加本地 loopback HTTP 测试服务器，实际验证 POST 方法、请求头、JSON body、2xx 成功、5xx/429 类重试和 4xx/3xx 永久拒绝；不把 loopback HTTP 放宽当作生产配置。
- `./gradlew test :phone-app:lint :glass-app:lint :phone-app:assembleDebug :glass-app:assembleDebug :phone-app:assembleRelease :glass-app:assembleRelease --console=plain`：通过；协议 **30/30**、手机上传 **3/3**，两个应用 lint 无 Error，Debug/Release APK 均生成。
- 随 ACK 落盘结果修正后重新构建：手机 Debug `fe2ae1b823a560b6067a8158c40021d4346a35244cca55bf43497c66881873a9`，眼镜 Debug `55c3ffe121ba273c16d70471b166fe8f5ff87670ec4b0ce603e2e9cc7efdb8f6`；手机 Release unsigned `b190557fcb9760c76a8fafe8794419970195f56a6f801ddb7381e4aefd63802a`，眼镜 `4c391fdce2f00c27132c230ea65cf6302f449ed39f2e07b5799f5fe28c967ad5`。

### 2026-11-06 Android SDK 生命周期释放保护

- 眼镜端 SDK 注册增加 `released` 标记和可取消的延迟重试；Activity 在服务尚未注册时销毁，也不会继续绑定、注册监听或回调旧页面。
- `release()` 现在会清理 Handler 队列、监听器、注册状态并安全释放已就绪的 SDK；不改变正常连接、ACK 和照片重试流程。
- `:protocol:test`、手机测试、眼镜 Debug 编译/lint、眼镜 Debug/Release 构建均通过；协议 **30/30**、手机上传 **3/3**。
- 本轮眼镜 Debug SHA-256 `0118db8326f7f1d294b66cad5b7f86246f74db0042f3bf1e85b0435f6649f621`，Release unsigned SHA-256 `d5de96ec61d4c89a7a21c4ec385c66622b75ba035f7fe22d4dca36c550914e3e`。

### 2026-11-06 Android 手机 SDK 生命周期释放保护

- 手机端增加初始化进行中和已释放状态；SDK 异步初始化回调、蓝牙/P2P、消息和文件回调在 Activity 销毁后会被忽略，避免重新注册监听或触碰旧页面。
- 手机端 `release()` 现在可安全清理扫描、P2P、消息/文件监听和 SDK；重复点击初始化不会并发启动多个 SDK 初始化流程。
- `./gradlew :protocol:test test :phone-app:lint :glass-app:lint assembleDebug :phone-app:assembleRelease :glass-app:assembleRelease --console=plain`：通过；协议 **30/30**、手机上传 **3/3**，两个应用 lint 无 Error，四个 APK 均生成。
- 本轮 APK：手机 Debug `deaa6c081bc856b285462fbcd76a6c99840295b0bdba7978801ee435578bfe91`，眼镜 Debug `0118db8326f7f1d294b66cad5b7f86246f74db0042f3bf1e85b0435f6649f621`；手机 Release unsigned `e3eb494a16737d867c0ce2af8324a033ee196f9fee9aac191028cf96ad9eecd0`，眼镜 `d5de96ec61d4c89a7a21c4ec385c66622b75ba035f7fe22d4dca36c550914e3e`。

### 2026-11-06 Android 双端异步回调释放保护补齐

- 眼镜端消息监听、经典蓝牙发送、P2P 状态查询和发送回调在 Activity 销毁后全部忽略；手机端控制消息、扫描连接和 P2P 建连回调同样检查 `released`，避免旧页面收到状态、继续发送或重置连接状态。
- `./gradlew :protocol:test test :phone-app:lint :glass-app:lint assembleDebug :phone-app:assembleRelease :glass-app:assembleRelease --console=plain`：通过；协议 **30/30**、手机上传 **3/3**，两个应用 lint 无 Error，四个 APK 均生成。
- 本轮 APK：手机 Debug `8cd47d1b18b9e0c4cd06c65f1ccf32dea1a46ae5a547c74870541f7a339b2d75`，眼镜 Debug `a9ea808c7ecb8831409aebc0fe235d01c4419b0fbc582bcc7aa4297ab2a8f0a7`；手机 Release unsigned `3b3dc9091dc306b5f0cc1a2a8c78137a25b67f697f72a4b791360a71847ba593`，眼镜 `f537b4142dd6dcd71fa2d5faa385ab476e31f151c98085d3ae2e2c9ecd25133b`。

### 2026-11-06 Android 现场安装入口

- 新增 `android/field-install.sh`：必须显式传入手机和眼镜的 adb 序列号，分别读取型号，执行非破坏性的 `adb install -r`，并核对 `com.inspection.demo.phone` / `com.inspection.demo.glass` 包名。
- 已执行 `bash -n android/field-install.sh`、`./field-install.sh --help`，并用双设备 fake-adb 夹具验证序列号、型号读取、双 APK 安装和包名核对；真实安装仍需现场设备授权。

### 2026-11-06 Android 手机照片接收释放竞态

- 手机端照片文件/字节流处理在 Activity 销毁后不再执行旧页面保存、负 ACK 或清理待处理元数据；已落盘但尚未 ACK 的项会保留，下一次 SDK 初始化可继续幂等处理。
- 增量全套命令仍通过：协议 **30/30**、手机上传 **3/3**，手机/眼镜 lint 无 Error，Debug/Release APK 均生成；`device-preflight.sh` 仍为 0 个失败、4 个提醒。
- 本轮 APK：手机 Debug `940f36a00c60739ddf2e08445864bbd6842bebfe55e6170e1b6ed7b8d009ba64`，眼镜 Debug `a9ea808c7ecb8831409aebc0fe235d01c4419b0fbc582bcc7aa4297ab2a8f0a7`；手机 Release unsigned `b740a90a207f9bfaaa024d407bfaddcf7b98cb4cfc161e242b82f5980ec6cbf4`，眼镜 `f537b4142dd6dcd71fa2d5faa385ab476e31f151c98085d3ae2e2c9ecd25133b`。

### 2026-11-06 Android 拍照元数据释放竞态收尾

- 眼镜端经典蓝牙照片元数据发送在发送返回后再次检查 `released`，Activity 销毁竞态下不会调用旧页面的照片发送回调。
- 最终全套命令通过；协议 **30/30**、手机上传 **3/3**，两个应用 lint 无 Error，四个 APK 均生成；设备预检为 0 个失败、4 个提醒。
- 本轮 APK：手机 Debug `940f36a00c60739ddf2e08445864bbd6842bebfe55e6170e1b6ed7b8d009ba64`，眼镜 Debug `9cb2f5bb216d6df1571e808d7bcc5b1c847f6b4c240480860699b4a48e725d09`；手机 Release unsigned `b740a90a207f9bfaaa024d407bfaddcf7b98cb4cfc161e242b82f5980ec6cbf4`，眼镜 `8b028e35f0d3452e5317cbafb55c7449bf21dfbb3041b85bc137ab23786aee98`。

### 2026-11-06 Android 眼镜照片回调释放竞态收尾

- 眼镜端照片元数据的 P2P 成功/失败回调、P2P 状态查询回调及异常回退现在都会检查 `released`；Activity 销毁后不再触发旧页面状态更新、文件发送或重试。
- `./gradlew :protocol:test test :phone-app:lint :glass-app:lint assembleDebug :phone-app:assembleRelease :glass-app:assembleRelease --console=plain`：通过；协议 **30/30**、手机上传 **3/3**，两个应用 lint 无 Error，四个 APK 均生成。
- `device-preflight.sh`：0 个失败、4 个提醒；提醒为企业 AK/SK、报告同步 URL、企业签名和未连接真机。
- 本轮 APK：手机 Debug `deaa6c081bc856b285462fbcd76a6c99840295b0bdba7978801ee435578bfe91`，眼镜 Debug `854bdc4397f2a90e5f2c2003b43f8de9c32b3351430f7aec9e532aa56c39f264`；手机 Release unsigned `e3eb494a16737d867c0ce2af8324a033ee196f9fee9aac191028cf96ad9eecd0`，眼镜 `2fe8a2b355a506da524ee182bf616e371e36e25ae91980eb16b7441d6c06985d`。

### 2026-11-06 Android 当前交付状态

- 当前源码已包含双端异步回调释放保护、照片接收竞态保护和现场双设备安装脚本 `android/field-install.sh`；`SMOKE_TEST.md` 与 `android/README.md` 已同步安装步骤。
- 最终验证：协议 **30/30**、手机上传 **3/3**；全套 lint、Debug/Release 构建、`aapt dump badging` 和脚本语法检查通过；`device-preflight.sh` 为 0 个失败、4 个提醒。
- 当前产物哈希以 `android/README.md` 为准：手机 Debug `f4556317e5c44e6d1ec4349f63a874e3ceae608c92c5d1440523df147ff1f914`，眼镜 Debug `d2a481ccc584d62e818b36207f267c0ea6a8fad54689a85608b869004d9853c7`；手机 Release unsigned `b740a90a207f9bfaaa024d407bfaddcf7b98cb4cfc161e242b82f5980ec6cbf4`，眼镜 `8b028e35f0d3452e5317cbafb55c7449bf21dfbb3041b85bc137ab23786aee98`。

### 2026-11-06 Android clean 全量复核

- 执行 `./gradlew clean :protocol:test test :phone-app:lint :glass-app:lint assembleDebug :phone-app:assembleRelease :glass-app:assembleRelease --console=plain`：**BUILD SUCCESSFUL**；协议 **30/30**、手机上传 **3/3**，双端 lint、Debug/Release 构建通过。
- clean 构建后的 Debug APK 已用 `aapt dump badging` 核对包名、minSdk 和 targetSdk；`device-preflight.sh` 仍为 0 个失败、4 个提醒。
- 当前哈希：手机 Debug `f4556317e5c44e6d1ec4349f63a874e3ceae608c92c5d1440523df147ff1f914`，眼镜 Debug `d2a481ccc584d62e818b36207f267c0ea6a8fad54689a85608b869004d9853c7`；手机 Release unsigned `b740a90a207f9bfaaa024d407bfaddcf7b98cb4cfc161e242b82f5980ec6cbf4`，眼镜 `8b028e35f0d3452e5317cbafb55c7449bf21dfbb3041b85bc137ab23786aee98`。

### 2026-11-06 Android Rokid 官方权限清单对齐

- 对照 Rokid Glass3 Enterprise 快速开始，手机端补齐 `CHANGE_NETWORK_STATE`、`BLUETOOTH_ADVERTISE` 和可选 Wi-Fi Direct 特性；Android 12+ 初始化流程申请蓝牙广播权限。
- 眼镜端补齐网络状态、Wi-Fi 状态、Wi-Fi P2P 相关声明；不申请不需要的外部存储权限，照片仍写入 app-private 目录。
- AAPT 合并产物确认手机/眼镜 APK 的权限和可选特性已进入最终包；构建、lint 和协议/上传测试保持通过。
- 本轮 APK：手机 Debug `ed1fca56b999c5f1cd36a9ad72d2f0e37852094d9627a367ad9227ad8db5d8bd`，眼镜 Debug `223de3f2c806982543ec7f4c87c28ab457f556bcd18240f36435f52637a5b215`；手机 Release unsigned `d2d639ece425d39062edc457c121f3293bfb3a25d3eae899b3cb8bc6747ce4e1`，眼镜 `09820f390fc7293f7ac58bcb379e2307884a701a94698c9e1555d34fcfd28367`。

### 2026-11-06 Android 12/12L Wi-Fi P2P 定位权限

- 手机端将 `ACCESS_FINE_LOCATION` 声明覆盖到 API 32，并只在 Android 12/12L 的动态权限请求中加入定位；Android 13+ 继续使用 `NEARBY_WIFI_DEVICES`，避免把定位权限带到新系统。
- AAPT 最终包确认手机端包含蓝牙扫描/连接/广播、API 32 以下定位和可选 Wi-Fi Direct；构建、lint、协议/上传测试均通过，设备预检仍为 0 个失败、4 个提醒。
- 本轮 APK：手机 Debug `07bc056f6516a60ca82833c12e33274c581c76251a3ffff55feccdd8872208d4`，眼镜 Debug `223de3f2c806982543ec7f4c87c28ab457f556bcd18240f36435f52637a5b215`；手机 Release unsigned `6a98f3f38e52644a6b300312c3963d5a130090848611b8ff29f2c8f07a9d8999`，眼镜 `09820f390fc7293f7ac58bcb379e2307884a701a94698c9e1555d34fcfd28367`。

### 2026-11-06 Android 12 定位权限请求完整性

- Android 12/12L 动态请求同时包含 `ACCESS_FINE_LOCATION` 和 `ACCESS_COARSE_LOCATION`；Android 13+ 仍不请求定位，只使用 `NEARBY_WIFI_DEVICES`。
- 构建、lint、协议/上传测试和设备预检保持通过。
- 本轮 APK：手机 Debug `4932e796344275475ac9c0d7d2d55921fbc0b40ae56837c69b9a01e5e02c9669`，眼镜 Debug `223de3f2c806982543ec7f4c87c28ab457f556bcd18240f36435f52637a5b215`；手机 Release unsigned `2411f1c3e23ca70ee2142d9b9417728f4d065b6bf970477f8c46a1c42e986008`，眼镜 `09820f390fc7293f7ac58bcb379e2307884a701a94698c9e1555d34fcfd28367`。

### 2026-11-06 Android 企业包名可配置

- `local.properties` 新增可选 `android.phoneApplicationId` / `android.glassApplicationId`；不填写时继续使用 demo 默认包名。
- `field-install.sh` 不再写死包名，而是用 `aapt dump badging` 从实际 APK 读取包名后安装和核对；已用双设备 fake-adb 验证默认包名路径。
- 临时配置自定义包名 `com.example.inspection.phone` / `com.example.inspection.glass` 后，两个 APK 的 manifest 包名实测切换成功；配置已恢复，最终产物恢复为默认包名。
- 默认包名最终构建通过；当前 APK：手机 Debug `833404b7558c9759124df57de72ecbd4409a278a61b5fa35e05ac4b47252693f`，眼镜 Debug `a1e2959919d7640f1d86b721918e15aab43a97939a147823813f3bf92914cfd2`；手机 Release unsigned `2411f1c3e23ca70ee2142d9b9417728f4d065b6bf970477f8c46a1c42e986008`，眼镜 `09820f390fc7293f7ac58bcb379e2307884a701a94698c9e1555d34fcfd28367`。

### 2026-11-06 Android 照片传输失败回退与临时文件持久化

- 眼镜端字节流回调返回 `onSuccess(false)` 时现在会保留 outbox 并安排重试；此前该分支没有重试，可能一直等待不存在的 `photo.ack`。
- 手机端在“文件先到、元数据后到”时会立即把 SDK 临时文件复制到 app-private 的 `pending-photo-files/`，并同步提交索引；进程或 SDK 清理原始缓存后仍能按 `photoId`/原文件名匹配。完成接受或负 ACK 后只删除这份临时副本，不删除 SDK 管理的源文件。
- 暂存文件使用 UUID 子目录保留原始 basename，且重启后按实际 SharedPreferences 键清理，避免副本名变化导致元数据无法匹配或残留孤儿索引。
- 增加字节流 API 同步抛异常时的重试回退；原有 P2P 文件、蓝牙文件和字节流顺序不变。
- 手机端按 `photoId` 串行化重复文件回调，避免 P2P/蓝牙重复到达时同时写入同一个临时文件；不同照片仍可并行处理。
- `./gradlew :protocol:test test :phone-app:lint :glass-app:lint assembleDebug :phone-app:assembleRelease :glass-app:assembleRelease --console=plain`：**BUILD SUCCESSFUL**；协议 **30/30**、手机上传 **3/3**，双端 lint 和 Debug/Release 构建通过。
- AAPT 核验默认包名为 `com.inspection.demo.phone` / `com.inspection.demo.glass`，compile/target SDK 34；`device-preflight.sh` 为 0 个失败、4 个外部条件提醒。
- 增量复核（含重复照片回调串行化）后 APK SHA-256：手机 Debug `340416ea63d05a4b71355b5c37b6c8fa7bb8cc9e639d9eb162140e1b7a8213b4`，眼镜 Debug `1a066ec00ead0ccf952d45329a7006fa13e8ad9a06cedbc72cb073c74cc223b3`；手机 Release unsigned `59dfe80eef57a9350587bb59f7e1eba499ab67526c3ab4f20598a6066075c7b3`，眼镜 `b41241b16f8743bcd150c08dae66eabfd33d4610eaa22efbde884489574f3a2b`。

### 2026-11-06 Android 双设备现场诊断入口

- 新增 `android/field-diagnostics.sh`：必须显式传入手机和眼镜序列号，只读收集型号、Android/固件版本、包安装路径、权限、蓝牙/Wi-Fi/P2P 摘要和最近 300 行过滤日志。
- 诊断报告默认写入 `artifacts/field-diagnostics-时间戳/`，不读取或输出 `local.properties` 的 AK/SK、token 或签名密码；脚本不会安装、卸载、清数据或修改设备。
- 已执行 `bash -n android/field-diagnostics.sh`、`--help`，并用双设备 fake-adb 验证报告目录、手机/眼镜摘要和日志文件均能生成。

### 2026-11-06 Android 连接恢复自动重试

- 手机端 `RokidPhoneTransport` 在经典蓝牙连接成功、P2P 已连接或 P2P 建连成功后触发连接就绪回调；`MainActivity` 从 Room 活动快照重试未完成的 `task.dispatch`。
- 重试按 `taskId` 使用现有幂等协议，保留眼镜端草稿、步骤和照片队列；已完成任务和空任务不会重发。P2P 服务“已启用”回调不作为已连接信号，避免误发。
- `./gradlew :protocol:test :phone-app:test :phone-app:lint :glass-app:lint assembleDebug :phone-app:assembleRelease :glass-app:assembleRelease --console=plain`：**BUILD SUCCESSFUL**；协议 **30/30**、手机上传 **3/3**，双端 lint 无 Error，四个 APK 均生成。
- 本轮 APK SHA-256：手机 Debug `882a85f6edba3433ceda56bba2643702671a70345bb1c76abde780b77143cf82`，眼镜 Debug `1a066ec00ead0ccf952d45329a7006fa13e8ad9a06cedbc72cb073c74cc223b3`；手机 Release unsigned `738b4b8a2c17fe65533e9c1c02fa72c1d56ada22f437e52adf6b05facff6e95f`，眼镜 Release unsigned `b41241b16f8743bcd150c08dae66eabfd33d4610eaa22efbde884489574f3a2b`。
- `adb devices` 仍为空；Glass3 真机安装、蓝牙/P2P 配对、实体按键、相机和照片文件通道仍需现场设备、企业凭证与签名。

### 2026-11-06 Android P2P 状态文案复核

- 将 Wi-Fi P2P “服务已启用”与“连接已建立”状态分开显示，连接就绪回调仍只在实际蓝牙/P2P 控制通道可用时触发。
- 全量命令 `./gradlew :protocol:test :phone-app:test :phone-app:lint :glass-app:lint assembleDebug :phone-app:assembleRelease :glass-app:assembleRelease --console=plain`：**BUILD SUCCESSFUL**；协议 **30/30**、手机上传 **3/3**，双端 lint 无 Error，四个 APK 均生成。
- `device-preflight.sh`：0 个失败、4 个提醒（企业 AK/SK、同步 URL、Release 签名和未连接真机）。
- 最新 APK SHA-256：手机 Debug `a0544f9f81f01c4f59b3a2fa7b9bc5bc89a234e8a2bb682cac5346bfe0160c24`，眼镜 Debug `1a066ec00ead0ccf952d45329a7006fa13e8ad9a06cedbc72cb073c74cc223b3`；手机 Release unsigned `35e1a316a3b4f3c9145bccfb7cf118c70501f5adedf5618ceee31593da0b3166`，眼镜 Release unsigned `b41241b16f8743bcd150c08dae66eabfd33d4610eaa22efbde884489574f3a2b`。

### 2026-11-06 Android ACK 重试身份与已有连接恢复

- 修复 ACK 重试边界：`step.ack` / `photo.ack` 的 `commandId` 现在区分 accepted/rejected。手机本地暂时保存失败后先发负 ACK、重试成功发正 ACK 时，眼镜重放账本可正确应用正 ACK；同一结果的重复消息仍幂等去重。
- 同一 `taskId` 的任务下发若 payload 内容变化，手机和眼镜均拒绝覆盖，避免任务编号复用造成两端快照不一致。
- 手机 SDK 初始化完成后主动检查已有蓝牙/P2P 连接；进程重启但物理链路未断时，也能触发活动任务的自动重发。
- 眼镜端提交步骤草稿时保留故障发现时间字段；手机连接恢复重发只在真实步骤未全部完成时执行。
- `./gradlew :protocol:test :phone-app:test :phone-app:lint :glass-app:lint assembleDebug :phone-app:assembleRelease :glass-app:assembleRelease --console=plain`：**BUILD SUCCESSFUL**；协议 **31/31**、手机上传 **3/3**，双端 lint 无 Error，四个 APK 均生成。
- `aapt dump badging` 核对默认包名 `com.inspection.demo.phone` / `com.inspection.demo.glass`、compile/target SDK 34；`device-preflight.sh` 仍为 0 个失败、4 个提醒。
- 本轮 APK SHA-256：手机 Debug `d6da50d341077dc79393149b7191796fed8d6ccb33ced0fe960fcd572a9d3072`，眼镜 Debug `68c4682a8c7cc6c6a4ce39ae97c98a48a68cad5f36fcc6fdc6997e65ac75efcc`；手机 Release unsigned `8b3268702ed9f6092b072f147cc3c5afffd4d61a01cee3d8579765bd31e1c6e2`，眼镜 Release unsigned `4a239d7acbddf972dbdc8255767636ce2b0a3b31ff1efdf994b3578ef33e2a14`。

### 2026-11-06 Android P2P 异常恢复复核

- P2P 状态查询同步抛异常时现在清除 `p2pConnecting`，后续按钮或连接恢复事件可以再次建立连接；P2P 服务启用事件不再误清除真实连接请求状态。
- 手机 SDK 初始化完成后会检查已经存在的蓝牙/P2P 控制链路，覆盖进程重启但物理连接未断的场景。
- 眼镜端完成步骤草稿保留 `faultTime`，同一任务编号的不同 payload 不允许作为幂等重放接受。
- 全量命令通过：协议 **31/31**、手机上传 **3/3**，双端 lint 无 Error，Debug/Release unsigned APK 均生成；设备预检 0 个失败、4 个提醒。
- 最新 APK SHA-256：手机 Debug `ab4858431537153adc05201fdedd562d55124d6fd6a4b1b966e70ec86ff469fd`，眼镜 Debug `68c4682a8c7cc6c6a4ce39ae97c98a48a68cad5f36fcc6fdc6997e65ac75efcc`；手机 Release unsigned `c94e4f9c290a0d8a4262ed0b1c4871d38fdfbe071bc3b3827dc9b83554d8a5a4`，眼镜 Release unsigned `4a239d7acbddf972dbdc8255767636ce2b0a3b31ff1efdf994b3578ef33e2a14`。

### 2026-11-06 Android 照片 ACK 崩溃窗口

- 眼镜端收到正/负 `photo.ack` 后改为同步删除 outbox，再允许消息重放账本持久化；进程在两步之间退出时不会出现“outbox 仍在、ACK 已被去重”的永久残留。
- 全量命令通过：协议 **31/31**、手机上传 **3/3**，双端 lint 无 Error，Debug/Release unsigned APK 均生成。
- 最新 APK SHA-256：手机 Debug `ab4858431537153adc05201fdedd562d55124d6fd6a4b1b966e70ec86ff469fd`，眼镜 Debug `284cc10e262e6dac5e442a194a588287a161d7f4c95e41b265369d115b6e6bcd`；手机 Release unsigned `c94e4f9c290a0d8a4262ed0b1c4871d38fdfbe071bc3b3827dc9b83554d8a5a4`，眼镜 Release unsigned `60a5ee18d3787cd4df58932088d15861e56ac6593736fcb7ac8760f6c83ec304`。

### 2026-11-06 Android 照片 ACK 持久化失败回退

- 眼镜端 `markAcknowledged` 现在返回持久化结果；outbox 删除失败时不记 ACK 重放、不清理待关联照片，后续收到重复 ACK 或重新连接时可以重试清理。
- 全量命令通过：协议 **31/31**、手机上传 **3/3**，双端 lint 无 Error，Debug/Release unsigned APK 均生成；设备预检 0 个失败、4 个提醒。
- 最新 APK SHA-256：手机 Debug `ab4858431537153adc05201fdedd562d55124d6fd6a4b1b966e70ec86ff469fd`，眼镜 Debug `61d0ab161a0ec91020cc14919470200ddf1ac587f71ba3c36f8c43777abd7b88`；手机 Release unsigned `c94e4f9c290a0d8a4262ed0b1c4871d38fdfbe071bc3b3827dc9b83554d8a5a4`，眼镜 Release unsigned `fe0f0949522b8bf639f092aac1188e4045bd4d503e2840f6deeb5263c11ac08f`。

### 2026-11-06 官方 Demo 构建与眼镜交互补齐

- 独立构建官方 `dual-device-ai-inspection-report` 的 `glass-app`、`phone-app` 均通过，确认本机 JDK 17、Android SDK 和 Rokid `2.2.2-E` 依赖可用。
- 当前眼镜端补入 `DPAD_LEFT/RIGHT` 镜腿滑动焦点导航，并在巡检页面启用 `FLAG_KEEP_SCREEN_ON`；滑动只移动焦点，不绕过步骤校验。
- 全量命令通过：协议 **31/31**、手机上传 **3/3**，双端 lint 无 Error，Debug/Release unsigned APK 均生成；设备预检 0 个失败、4 个外部条件提醒。
- 最新 APK SHA-256：手机 Debug `ab4858431537153adc05201fdedd562d55124d6fd6a4b1b966e70ec86ff469fd`，眼镜 Debug `615b5eda730d9c5639f79d7d4b8a48b7b71366e9a6fa2b48c3af0c092875f201`；手机 Release unsigned `c94e4f9c290a0d8a4262ed0b1c4871d38fdfbe071bc3b3827dc9b83554d8a5a4`，眼镜 Release unsigned `38babc0be89ceda6bbad72a05da139184aee0959b4c9acfc9c51e8dfac5a9b98`。

### 2026-11-06 现场预检增强

- `device-preflight.sh` 增加 APK 包名、min/target SDK 和四号楼目录完整性检查；目录通过 SHA-256 与 Web seed 比对，避免现场安装过期 APK。
- 本机验证：三个现场脚本 `bash -n` 通过；预检 0 个失败、4 个提醒，并确认手机 `minSdk 24/target 34`、眼镜 `minSdk 29/target 34`、APK 包名和目录哈希均正确。

### 2026-11-06 无设备主机回归入口

- 新增可执行的 `android/host-smoke.sh`，统一执行脚本语法检查、环境/APK/目录预检、协议/手机测试、双端 lint、Debug/Release 构建和 APK SHA-256 输出。
- 已实际运行并通过：`HOST SMOKE PASSED`；当前仍只提示 AK/SK、同步 URL、企业签名和未连接真机四项外部条件。

### 2026-11-06 Web 与 Android 主机回归复测

- Web `npm run typecheck`：通过。
- Web `npm run lint`：通过。
- Web `npm run test`：4 个测试文件、44 项测试通过。
- Web `npm run build`：Vite 生产构建通过。
- Web `PW_REUSE_SERVER=1 npm run test:e2e`：31/31 通过；`DEMO_BUILD=1 PW_REUSE_SERVER=1 npm run test:e2e`：31/31 通过，覆盖 12 套四号楼流程、三种视口、照片/摄像头模拟、暂停恢复、报告和重置。
- Android `./host-smoke.sh`：通过，协议 31/31、手机上传 3/3、双端 lint 和四个 APK 构建通过；现场设备和企业凭证仍未连接。

### 2026-11-06 Rokid 候选交付包

- 新增 `artifacts/rokid-rc-20261106/`，包含手机/眼镜 Debug APK、两个 Release unsigned APK、SHA-256 清单、现场脚本、Smoke Test 和官方 Demo 对照记录。
- 交付包 `SHA256SUMS` 已实际执行 `shasum -a 256 -c`，四个 APK 均 `OK`；AAPT 核对手机 `minSdk 24/target 34`、眼镜 `minSdk 29/target 34` 和默认包名正确。
- 包内仍明确标注企业签名、AK/SK、后端地址和真机联调未完成，不把候选包当作生产发布包。

### 2026-11-06 Glass SDK 异步就绪时序

- 对照官方 `IClientCallback.onReady()` 后修正眼镜端：`registerClient()` 返回不再直接触发业务就绪；任务恢复和照片 outbox 重试只在 client 回调确认后执行。
- 服务断开/绑定失效会清除 client 就绪和通知状态，重连后只通知一次。
- 眼镜端协议测试、lint、Debug/Release 构建通过；候选交付包已同步更新并重新核对 SHA-256。

### 2026-11-06 Glass SDK 时序修复产物

- 眼镜端异步就绪修复后的最新 SHA-256：Debug `8886132d48328fac106acc6f9551a8afc4a97764cac7441a837129a5accf197b`，Release unsigned `b86f0fbf1969d8dea36ca908bcb1df9141ced88ae33c72346bdc75ded19ee06b`；手机产物保持不变。
- `android/host-smoke.sh` 再次通过，候选交付包 `artifacts/rokid-rc-20261106/` 已重新复制并执行 checksum 校验。

### 2026-11-06 Release 签名门禁

- 新增 `android/release-check.sh` 及候选包内 `docs/release-check.sh`；脚本可验证两个 Release APK 的签名，默认对 unsigned 产物给出提醒，`--require-signed` 用于企业签名后的发布门禁。
- `android/host-smoke.sh` 已纳入该签名检查，并能同时识别 AGP 的 `*-release.apk` 与未签名的 `*-release-unsigned.apk` 输出。
- `field-install.sh` 增加 `--variant debug|release`；Release 模式只接受已签名的 `*-release.apk`，避免把 unsigned 产物当成正式安装包。
- `device-preflight.sh` 增加 `--require-release`，现场正式安装可与 `--require-device` 一起使用，强制通过 `apksigner` 签名门禁。
- 新增 `android/package-release.sh`：构建两个 Release、强制签名校验、复制 APK 与现场文档并生成 `SHA256SUMS`；签名门禁失败时不会创建交付目录。
- 复核修正 `package-release.sh` 的相对输出目录处理，生成的 README 现在使用规范化绝对路径；当前 unsigned 环境回归确认仍会在签名门禁前停止且不创建输出目录。

### 2026-11-06 临时签名交付演练

- 使用一次性本地测试 keystore（不含企业凭证）实际生成手机/眼镜签名 Release APK；`release-check.sh --require-signed` 两项均通过。
- `package-release.sh --skip-build --out <临时目录>` 完整通过，生成交付 README、现场脚本和 `SHA256SUMS`；交付包 checksum 两项均 `OK`。
- 演练结束后已恢复 `local.properties`、删除临时 keystore、删除签名 APK 和临时输出；工作区恢复为 unsigned Release 产物，原候选包 checksum 未变化。

### 2026-11-06 候选包现场脚本路径复核

- `device-preflight.sh`、`field-install.sh`、`release-check.sh` 现在同时支持源码工程目录和 `artifacts/rokid-rc-*/{apk,docs}` 候选包布局；候选包没有 Gradle/seed 时会跳过源码专属检查，不把它误报为工程损坏。
- 已用当前候选包实际运行预检：0 个失败、5 个提醒；Release 门禁能正确识别包内 unsigned APK 并失败。候选包脚本已同步并通过 `bash -n`。
- 用一次性测试签名 APK 生成临时候选包后，从包内 `docs/release-check.sh --require-signed` 和 `docs/device-preflight.sh --require-release` 实际执行均通过；另用 fake `adb` 完成候选包 `field-install.sh --variant debug` 的包名/安装核对，退出码为 0。临时签名与测试文件已清理。
- Release 打包脚本不再把源码专用的 `host-smoke.sh` 放入候选包；候选包只保留可在 `apk/` + `docs/` 布局中独立运行的现场脚本。
- 以临时签名 APK 生成候选包后，再用 fake `adb` 执行 `docs/field-install.sh --variant release`，两端安装与包名核对均通过；当前源码工作区已清理临时签名产物并恢复 unsigned 输出。
- 新增 [artifacts/ANDROID_RELEASE_REHEARSAL.md](artifacts/ANDROID_RELEASE_REHEARSAL.md)，记录签名、候选包预检、安装演练的实际命令和边界；最终源码/候选包复核再次通过。
- 候选包预检不再要求现场安装 JDK/Gradle；源码工程仍严格检查 JDK 17，候选包只检查 APK、Android SDK 工具、签名和设备条件。
- 本机执行脚本和 `bash -n` 均通过；当前两个 Release APK 仍为 unsigned，因此尚未满足正式发布签名条件。
- 候选包四个 APK 的 `SHA256SUMS` 已在包目录重新核对，四项均 `OK`。

### 2026-11-06 Web ↔ Android 任务文件桥接

- Web 新增 `src/services/androidTask.ts`，将任务快照映射为共享协议的 `task.dispatch` envelope，完整保留模板来源、设备、人员、日程、步骤字段和阈值。
- Web 工作台已有任务时可导出 `Android task.dispatch` JSON；它与历史巡检报告 JSON 分开，避免把归档数据误当成可执行任务。
- 手机端新增系统文件选择器导入入口；导入先做 envelope 类型、`taskId`、payload 解码和 `TaskGuard` 校验，再写入 Room。SDK 已连接时立即下发，否则连接恢复后按现有幂等机制重试。
- Web 当前验证：typecheck、lint、build、单元测试 **46/46** 通过；Android `host-smoke.sh` 通过，协议 **31/31**、手机上传 **3/3**、双端 lint 和 Debug/Release 构建通过。
- 真机安装、Glass3 固件、企业签名/AK/SK、蓝牙/P2P 和相机文件通道仍需现场条件验证。

### 2026-11-06 Android 下发内容边界校验补强

- `TaskGuard.validateDispatch` 现在在手机导入和眼镜接收两端共同校验任务结构：设备确认/检查项/提交记录顺序、模板对象归属、日期/时段/班次、字段类型与范围，避免手工修改任务 JSON 绕过 Web 校验。
- 新增协议测试覆盖有效与非法日程、错误设备和错序步骤；协议测试由 31 项增至 **32 项**，全部通过。
- `android/host-smoke.sh` 重新通过：协议 **32/32**、手机上传 **3/3**、双端 lint、Debug/Release 构建和 APK 预检均通过。
- 候选包已同步本轮 APK 并重新生成 `SHA256SUMS`；Release 仍为 unsigned，真机条件仍待现场提供。

### 2026-11-06 Android 任务文件解析单元测试

- 将 Web `task.dispatch` 文件解析从 Activity 内联逻辑抽为 `TaskDispatchFileParser`，统一处理类型、taskId、协议 payload 和任务结构校验，再交给 Room 保存。
- 新增手机端 2 项单元测试：成功解析 Web 兼容任务、拒绝错误 envelope 和非法步骤结构；手机端测试现为 **5/5**，协议测试 **32/32**。
- `android/host-smoke.sh` 再次通过，候选包已同步并重新校验四个 APK。

### 2026-11-06 Android 文件直接打开入口

- 手机端注册 JSON 文件打开入口，系统文件管理器可以直接打开 Web 导出的 `task.dispatch`；`onNewIntent` 复用同一个解析、校验和持久化路径。
- 首次加入 MIME-only `VIEW` intent filter 时 lint 报 `AppLinkUrlError`，已确认这是 lint 对非 Web 文件关联的误报边界，并在该 filter 上局部声明 `tools:ignore`；随后 `host-smoke.sh` 通过，未留下 lint error。
- 手机端测试 **5/5**、协议 **32/32**，候选包 APK checksum 全部通过。

### 2026-11-06 task.dispatch 协议身份门禁

- 文件导入现在强制要求 `version=1` 和非空 `commandId`，与任务重试/幂等语义一致；不支持的版本和缺失身份在 Room 写入前拒绝。
- 手机端解析测试增至 **6/6**，协议测试 **32/32**；`host-smoke.sh` 通过，候选 APK 已同步并重新校验。
### 2026-11-06 Android 文件分享入口与配置模板

- 手机端任务导入现在同时支持系统文件管理器的 `VIEW/data` 和分享面板的 `SEND/EXTRA_STREAM`，两者共用同一解析、协议版本、身份和任务结构校验路径。
- 新增 `android/local.properties.example`，明确列出 SDK、Rokid 企业凭证、HTTPS 同步地址、token 和企业包名覆盖项；示例默认不配置同步地址，避免误发网络请求。
- `android/host-smoke.sh`：通过；协议 **32/32**、手机上传 **3/3**、双端 lint、Debug/Release 构建和 APK 预检通过。
- 候选包四个 APK 已同步并重新执行 SHA-256 校验，全部 `OK`；Release 仍为 unsigned，真机仍待现场条件。


### 2026-11-06 Android 外部分享 URI 兼容

- 任务导入入口补充 `ClipData` URI 回退，兼容部分系统分享面板和文档提供器不填 `data`/`EXTRA_STREAM` 的场景。
- `android/host-smoke.sh`：通过；协议 **32/32**、手机上传 **3/3**、双端 lint、Debug/Release 构建和 APK 预检通过。
- 候选包已同步最新四个 APK 并重新生成/校验 `SHA256SUMS`；Release 仍为 unsigned，真实设备仍待现场条件。

### 2026-11-06 Android 照片拒绝状态提示修复

- 手机端只有在照片元数据通过当前任务/步骤归属校验并成功落盘后才显示“照片已保存”；错误照片显示未关联状态并继续返回负 `photo.ack`。
- `android/host-smoke.sh`：通过；协议 **32/32**、手机上传 **3/3**、双端 lint、Debug/Release 构建和 APK 预检通过。
- 候选包已同步最新 APK 并重新校验 `SHA256SUMS`；Release 仍为 unsigned，真机仍待现场条件。

### 2026-11-06 候选包同步流程固化

- 新增 `android/sync-candidate.sh`，将候选包刷新从手工复制改为可重复命令；它只复制已构建 APK、现场脚本和 Smoke Test，并重新生成 `SHA256SUMS`。
- `android/host-smoke.sh` 纳入该脚本的语法检查；实际同步后四项 APK checksum 全部 `OK`。
- 本机全量回归保持通过：协议 **32/32**、手机上传 **3/3**、双端 lint、Debug/Release 构建和预检通过；Release 仍为 unsigned，真机仍待现场条件。

### 2026-11-06 Android 入站坏包隔离

- 手机端完整包住入站消息的 JSON/payload 解析和回调；眼镜端将入站 envelope 处理移入统一安全边界。坏 JSON、错误 payload 或业务回调异常不会直接冲击 Rokid SDK 回调线程，只报告状态并保留可恢复快照/照片队列。
- `android/host-smoke.sh`：通过；协议 **32/32**、手机上传 **3/3**、双端 lint、Debug/Release 构建和 APK 预检通过。
- 用 `android/sync-candidate.sh` 同步后，候选包四项 APK checksum 全部 `OK`；Release 仍为 unsigned，真实设备仍待现场条件。

### 2026-11-06 Android 眼镜端任务身份门禁

- 将 `task.dispatch` envelope 的类型、协议版本、`commandId` 和 `taskId` 校验提取为共享 `TaskGuard.validateDispatchEnvelope`；手机文件导入与眼镜端 SDK 直收统一执行，拒绝旧版本或无身份消息进入任务快照。
- 新增协议回归测试，协议测试增至 **33/33**；手机上传测试 **3/3**。
- `android/host-smoke.sh`：通过；双端 lint、Debug/Release 构建和 APK 预检通过；候选包已重新同步并校验。

### 2026-11-06 Android ACK 身份一致性

- 眼镜端对 `step.ack` / `photo.ack` 增加 commandId 身份校验，确保 ACK 的 accepted/rejected 结果、步骤 revision 或 photoId 与命令身份一致；不匹配的旧 ACK 不会记入重放账本或推进步骤。
- `android/host-smoke.sh`：通过；协议 **33/33**、手机上传 **3/3**、双端 lint、Debug/Release 构建和 APK 预检通过。
- 候选包已重新同步并校验四项 SHA-256；Release 仍为 unsigned，真机仍待现场条件。

### 2026-11-06 Android 控制消息 commandId 统一

- 将 dispatch、step.complete、photo.upload、task.complete 的 commandId 生成集中到共享 `ProtocolCommandIds`；手机端拒绝身份不匹配的照片/步骤消息，眼镜端 ACK 使用同一规则校验。
- `android/host-smoke.sh`：通过；协议 **33/33**、手机上传 **3/3**、双端 lint、Debug/Release 构建和 APK 预检通过。
- 候选包已同步并校验四项 APK SHA-256；Release 仍为 unsigned，真实设备仍待现场条件。

### 2026-11-06 Android commandId 生成器回归

- 增加共享 `ProtocolCommandIds` 的稳定性单测，覆盖 dispatch、step.complete、photo.upload、task.complete 四类身份。
- `./gradlew :protocol:test`：通过；协议测试 **34/34**，Debug/Release 报告均无失败。
- 本轮为协议测试补充，已构建 APK 与候选包 checksum 保持有效。

### 2026-11-06 Android 五步巡检流程回归

- 新增设备确认 → 压力读数/阈值与照片 → 运行模式 → 外观检查 → 最终复核的完整协议流程测试，覆盖跳步拒绝、资产确认、照片归属、异常说明、选择字段和最终复核。
- 协议测试增至 **35/35**，Debug/Release 均无失败；`android/host-smoke.sh` 保持通过。
- 本轮未改变 APK 二进制；候选包 checksum 继续有效。

### 2026-11-06 Android 凭证模板与预检防误配

- `local.properties.example` 的 AK/SK 默认为空，避免复制模板后把占位值交给 Rokid SDK。
- `device-preflight.sh` 将 `replace-with-*` 识别为未配置并只输出状态，不泄露凭证；候选包脚本已同步。
- `android/host-smoke.sh`：通过；协议 **35/35**、手机上传 **3/3**、双端 lint、Debug/Release 构建和 APK 预检通过；候选 APK 未变更且 checksum 有效。

### 2026-11-06 Android 照片 outbox 重启归属恢复

- 修复眼镜进程重启后的照片 ACK 边界：启动时从持久化 outbox 恢复 `photoId → stepId` 映射，照片重试只针对当前活动任务；没有活动任务时不重试旧照片；手机确认后仍先提交眼镜任务快照，再删除 outbox，避免 ACK 已记账但照片队列残留。
- `android/host-smoke.sh`：通过；协议 **35/35**、手机上传 **3/3**、双端 lint、Debug/Release 构建和 APK 预检通过，预检 0 个失败、4 个外部条件提醒。
- 候选包已重新同步，四个 APK SHA-256 校验全部 `OK`；Release 仍为 unsigned，真机链路仍待现场条件。
- `GlassPhotoCapture` 的 `pendingPhotos` / `retryPending` 现在必须带非空任务 ID，避免未来调用点误触发全量旧照片重试。

- Android 修复后复核 Web 端：`npm run typecheck`、`npm run lint`、`npm run test`（46/46）、`npm run build` 均通过；`PW_REUSE_SERVER=1 npm run test:e2e` 通过 **32/32**。

### 2026-11-06 Android task.dispatch 身份一致性门禁

- `TaskGuard.validateDispatchEnvelope` 现在除了检查 `version`、`commandId` 和 `taskId` 非空，还强制 `commandId` 等于 `ProtocolCommandIds.dispatch(taskId)`；手机文件导入和眼镜 SDK 直收共用该规则。
- 更新 `protocol/task-dispatch.example.json` 和手机解析测试样例；增加错配 commandId 回归断言。
- `:protocol:test`、`:phone-app:test` 通过；`android/host-smoke.sh` 通过，协议 **35/35**、手机上传 **3/3**，预检 0 个失败、4 个提醒；候选包四项 checksum 全部 `OK`。

- 手机 Activity 的入站消息处理现在有 UI 层安全边界；`task.ack/start/pause/resume` 也验证共享 commandId，坏 payload 只更新状态，不使界面线程崩溃。

### 2026-11-06 Android SDK 断线自动重绑与控制状态恢复

- 眼镜端 `RokidGlassTransport` 在 `onServiceDisconnected`、`onBindingDied`、消息服务尚未就绪或监听注册异常时统一安排延迟重绑，避免 SDK 断线后只清状态、不再恢复监听。
- 眼镜端 SDK 重新就绪后，会先补发持久化任务的 `task.ack`，再补发 `task.start` 或 `task.pause`，并继续重试照片 outbox 与未确认的步骤完成；这些控制消息均使用既有幂等 commandId。
- 眼镜端 ACK 重放账本改为同步提交到 SharedPreferences，避免 ACK 已应用但进程在异步写入前退出时留下重复处理窗口。
- `android/host-smoke.sh`：通过；协议 **35/35**、手机上传 **3/3**、双端 lint、Debug/Release 构建和 APK 预检通过，预检仍为 0 个失败、4 个外部条件提醒。
- 候选包已用 `android/sync-candidate.sh` 同步，四个 APK 的 `SHA256SUMS` 全部 `OK`；真实手机、Glass3、企业凭证和签名仍待现场条件。

### 2026-11-06 Android 照片索引终态提交顺序

- 手机端在发送照片终态 ACK 前同步提交 `pending photo` 索引删除；若清理失败则不发送 ACK，保留重试条件，避免进程退出后出现“已确认但索引仍待处理”的状态。
- `android/host-smoke.sh`：通过；协议 **35/35**、手机上传 **3/3**、双端 lint、Debug/Release 构建；候选包四项 checksum 全部 `OK`。

### 2026-11-06 Android 任务文件大小边界

- 手机端文件选择器、VIEW、分享和 ClipData 入口现在在读取阶段限制任务 JSON 为 2 MB，解析器也重复校验字节数；超大文件不会进入 JSON 解码或 Room。
- 新增手机端超大任务文件拒绝单测；`android/host-smoke.sh` 通过，候选包四项 checksum 全部 `OK`。

### 2026-11-06 候选包自定义包名预检

- `device-preflight.sh` 在源码工程继续按 `local.properties` 校验登记包名；在仅含 APK 的候选交付包中，若未提供 `local.properties`，改用 APK manifest 的实际包名作为核对基准，支持企业自定义 applicationId。
- 已用当前候选包在提供 Android SDK 工具的环境中实际运行预检：0 个失败、5 个外部条件提醒；`SHA256SUMS` 四项均为 `OK`。

### 2026-11-06 Release 安装签名门禁

- `field-install.sh --variant release` 现在在任何 `adb install` 前强制执行 `apksigner verify`，即使 unsigned APK 被手工改名为 `*-release.apk` 也会被拦截。
- 用当前 unsigned APK 改名后的临时候选包和 fake adb 做安装演练：脚本在安装前退出，未执行安装；`android/host-smoke.sh` 通过，候选包 checksum 全部 `OK`。

### 2026-11-06 Android 照片负 ACK 终态与临时文件清理

- 手机端照片校验或 Room 关联失败时，负 `photo.ack` 之前会删除该 `photoId` 的私有文件和临时文件，不再把错任务照片留在目录中。
- 照片拒绝、重复照片和正常照片现在分别显示准确状态，不会在负 ACK 后覆盖成“照片已保存”。
- 文件先到、元数据迟到的 `pending-photo-files/` 增加 24 小时和 64 MB 双重清理上限，防止异常断链造成无限堆积。
- `android/host-smoke.sh`：通过；协议 **35/35**、手机上传 **3/3**、双端 lint、Debug/Release 构建和 APK 预检通过；候选包四项 checksum 全部 `OK`。

### 2026-11-06 Android 任务完成通知闭环

- 眼镜端 `RokidGlassTransport` 新增 `task.complete` 入站处理：校验固定 `commandId`、任务归属和 `accepted`，只有 Activity 持久化最终完成状态后才记入重放账本。
- `GlassTaskStore` 增加完成状态字段；眼镜进程重启后能区分“等待手机确认”和“手机已确认全部步骤保存”。
- 手机端连接恢复时如果 Room 活动任务的全部步骤已经落盘，会幂等重发 `task.complete`，避免最终通知丢失后眼镜一直停在等待状态。
- 新增 `TaskCompletePayload` 编解码回归；本机协议测试 **39/39**、手机测试 **7/7**，双端 lint、Debug/Release 构建和 `android/host-smoke.sh` 均通过。
- `android/sync-candidate.sh` 已刷新候选包；`artifacts/rokid-rc-20261106/SHA256SUMS` 四项全部 `OK`。企业凭证、正式签名和真实 Glass3 联调仍需现场条件。

### 2026-11-06 Android 任务终态边界复核

- 眼镜端 `task.complete` 只接受 `currentStepIndex == steps.size` 的终态；越界、提前完成、未落盘状态均不会进入完成状态或重放账本。
- 复跑 `android/host-smoke.sh`：0 个预检失败、4 个外部条件提醒，协议/手机测试和双端 lint、Debug/Release 构建均通过。
- 候选包已再次同步，四个 APK 的 `SHA256SUMS` 全部 `OK`；真实设备和企业发布条件仍未满足。

### 2026-11-06 Android 完成消息 payload 类型门禁

- `ProtocolCodec` 对 `task.complete` 强制要求 `accepted` 存在且为布尔值；缺字段或字符串伪造不会进入眼镜完成状态，也不会记入重放账本。
- 新增缺失/非布尔 `accepted` 回归测试；协议 Debug/Release **40/40**、手机端 Debug/Release **7/7** 通过。
- `android/host-smoke.sh` 通过，预检 0 个失败、4 个外部条件提醒；候选包已刷新，四项 `SHA256SUMS` 均为 `OK`。

### 2026-11-06 Android 负完成通知不吞后续成功重试

- `task.complete` 使用固定 commandId；眼镜收到 `accepted=false` 时只更新现场状态，不写入重放账本，后续同 commandId 的成功重试仍可落盘完成。
- 复跑 `android/host-smoke.sh` 和 `sync-candidate.sh`；协议 **40/40**、手机 **7/7**，双端 lint/构建、预检和候选包 checksum 均通过。

### 2026-11-06 Android 最终完成消息现场样例

- 新增 `protocol/task-complete.example.json`，明确区分手机发给眼镜的最终完成通知与报告归档 JSON。
- Smoke Test 和 Android 适配说明补充该消息的判定；候选包同步脚本与正式打包脚本都会把样例放入 `docs/`。
- `bash -n`、候选包同步和 APK `SHA256SUMS` 校验通过；APK 二进制未因文档样例改变。

### 2026-11-06 Android 设备心跳闭环

- 眼镜 SDK client 就绪后每 30 秒发送 `device.heartbeat`，包含实际设备 ID、连接状态、系统电量和 App 版本；手机端严格验证设备 ID、连接枚举和电量范围后显示在线状态。
- 心跳不推进任务、不生成报告同步成功状态；连接恢复和任务下发仍使用原有幂等协议。
- 新增 `protocol/device-heartbeat.example.json`，候选包/正式包脚本会将其复制到 `docs/`。
- 协议测试 **41/41**、手机测试 **7/7**，双端 lint、Debug/Release 构建、`host-smoke.sh` 和候选包离线预检均通过；预检 0 个失败、7 个外部条件提醒。

### 2026-11-06 心跳电量未知值边界

- 修复眼镜端 `BatteryManager` 不支持或暂时无法返回电量时回退为 `0%` 的误报；现在跳过本次心跳并等待下一周期重试，协议仍只接受 0–100 的真实电量。
- 更新 Android 适配说明和现场 Smoke Test，明确区分“无法读取”与低电量。
- 随后已重新运行主机回归和候选包同步并通过；真实 Glass3 现场仍需验证系统电量属性行为。

### 2026-11-06 手机端心跳超时可观测性

- 手机端新增独立的眼镜状态栏，心跳状态不再被任务、照片或同步提示覆盖。
- 最近一次有效心跳后 90 秒仍未收到下一次心跳时显示“心跳超时”；连接恢复时先显示“等待心跳”，收到有效心跳后恢复电量、版本和连接状态。
- 随后已重新运行双端 lint/构建和主机回归并通过；真实 Glass3 仍需现场验证 30 秒心跳与断链时序。

### 2026-11-06 心跳补丁回归完成

- `android/host-smoke.sh` 通过：协议 **41/41**、手机端 **10/10**，双端 lint、Debug/Release 构建和现场脚本演练通过；预检 0 个失败、4 个环境提醒。
- Web 基线同步复核通过：typecheck、lint、Vitest **46/46**、production build、Playwright **32/32**。
- `artifacts/rokid-rc-20261106/SHA256SUMS` 四项均为 `OK`；Release 仍是 unsigned，真实 Glass3 与企业发布条件仍待现场。

### 2026-11-06 心跳 payload 严格类型校验

- `ProtocolCodec` 现在要求 `device.heartbeat.appVersion` 和 `connection` 是 JSON 字符串，`batteryPercent` 是 0–100 范围内的整数；小数和隐式数字字符串都会拒绝。
- 增加小数电量和数字版本号回归断言；重新执行 `android/host-smoke.sh`，协议 **41/41**、手机端 **10/10**、双端 lint/构建和现场脚本均通过。
- 候选包已刷新，四项 APK `SHA256SUMS` 均为 `OK`；最新哈希以候选包清单为准。

### 2026-11-06 候选包签名产物选择

- `sync-candidate.sh` 的 Release 选择逻辑现在优先使用已签名的 `*-release.apk`，没有签名时回退 `*-release-unsigned.apk`。
- 切换签名状态时会清理候选目录中同模块的旧 Release 文件名，避免 `SHA256SUMS` 同时包含过期 unsigned 产物。
- 已用临时签名命名演练验证：候选包只生成签名命名的 Release APK；当前真实企业签名仍未配置。
- 另外用 unsigned Release 运行 `package-release.sh --skip-build --out <临时目录>`，脚本在创建输出目录前拒绝，生产交付门禁保持有效。
- `local.properties.example` 补齐 `android.signing.storeFile/storePassword/keyAlias/keyPassword` 四项空白模板，企业证书接入时不再需要猜配置键名。

### 2026-11-06 候选包 README 证据刷新

- `sync-candidate.sh` 现在每次同步动态生成候选包顶层 README，使用实际 Release 文件名和生成时间，不再保留旧的测试计数或过期 APK 说明。
- 首次演练发现 Markdown 代码围栏未转义会被 heredoc 当成命令替换，已修复并重新同步；当前 README、四项 checksum 和候选预检均无脚本错误。
- README 的检查命令改为相对候选根目录，不再写入本机绝对路径，候选包可直接拷贝到交接电脑使用。
- `host-smoke.sh` 现在会自动刷新候选包、执行候选 `SHA256SUMS`，并拒绝 README 混入本机绝对路径，避免源码与交付目录脱节。

### 2026-11-06 候选包跨平台校验补强

- 候选同步、正式打包、主机回归和 APK 内目录核对统一支持 `sha256sum` / `shasum` 自动选择，交接到 Linux 现场电脑时不再依赖 macOS 的命令名。
- 在受限 PATH（仅 `/usr/bin:/bin`）下实际同步候选包并完成四个 APK 的 SHA-256 核对，四项均为 `OK`。
- 修改后重新执行 `android/host-smoke.sh`：协议 **41/41**、手机端 **10/10**，双端 lint、Debug/Release 构建、现场脚本演练、候选包同步和校验全部通过；预检 0 个失败、4 个提醒。
- 真机、企业凭证、正式签名和正式报告服务仍待现场条件，未将本机检查标记为真机通过。

### 2026-11-06 双设备现场门禁补强

- `device-preflight.sh --require-device` 现在要求同时有手机和 Glass3 两台 `adb` 设备，不再把“一台设备已连接”误判为可以正式安装。
- 无设备时严格预检实际返回失败，并指出当前设备数和所需的两台设备；普通离线候选包预检继续只给提醒，不影响 APK 离线核对。
- 修改后 `android/host-smoke.sh` 通过：协议 **41/41**、手机端 **10/10**，双端 lint/Debug/Release 构建、候选包校验和现场脚本演练均通过；预检 0 个失败、4 个提醒。

### 2026-11-06 断线后草稿重发补强

- 眼镜 SDK 重新就绪时，眼镜端会从本地快照读取当前步骤草稿并重发 `step.draft`，手机端可以恢复已填写的读数、结果和说明。
- 草稿重发不推进步骤、不生成 `step.ack`；只有原有资料校验、`step.complete` 和手机 ACK 链路可以推进任务。
- `android/host-smoke.sh` 通过：协议 **41/41**、手机端 **10/10**，双端 lint、Debug/Release 构建、现场脚本和候选 APK 校验均通过；候选包已刷新。
- 真机断线、进程重启和 Glass3 草稿恢复仍必须在现场执行，未被本机构建结果替代。

### 2026-11-06 现场交接清单

- 新增 `android/FIELD_HANDOFF.md`，把现场需要提供的设备、凭证、签名和报告地址，以及安装、诊断和验收顺序写成一页清单。
- 候选包和正式 Release 包都会带上 `docs/FIELD_HANDOFF.md`；`host-smoke.sh` 已验证候选包包含该文件。
- 清单要求保存 APK SHA-256、固件、设备版本、任务/照片/步骤编号和诊断报告，便于把现场问题区分为软件、设备或企业配置问题。

### 2026-11-06 交接入口复核

- `android/README.md` 已从开发入口和现场准备处链接 `FIELD_HANDOFF.md`，并明确严格预检需要手机与 Glass3 两台设备。
- 候选 README、候选 `docs/FIELD_HANDOFF.md` 和源码说明已经过一致性检查；`android/host-smoke.sh` 重新通过，四个 APK 校验全部 `OK`。

### 2026-11-06 候选现场文档去除开发机路径

- 将 Smoke Test 和官方 SDK 对照说明中的 `/Users/mao/...`、`/opt/homebrew/...` 改为现场通用路径，交接电脑只需替换成自己的实际位置。
- `android/host-smoke.sh` 现在检查候选包顶层 README 及 `docs/*.md`，发现开发机绝对路径会直接失败。
- 本轮全量回归通过：协议 **41/41**、手机端 **10/10**、双端 lint、Debug/Release 构建、现场脚本演练、候选包同步与四项 SHA-256 校验均通过；预检 **0 个失败、4 个外部条件提醒**。
- 仍未满足现场条件：真实手机和 Glass3、企业 AK/SK、正式签名、正式 HTTPS 报告服务。

### 2026-11-06 Android 12/12L 定位权限补齐

- 手机 APK 清单补充粗略定位权限，并限制到 Android 12/12L；现场申请 Wi-Fi P2P 所需权限时，精确/粗略定位会一起出现，避免系统只给出不完整授权。
- 修改后全量主机回归通过：协议 **41/41**、手机端 **10/10**、双端 lint、Debug/Release 构建、现场脚本演练和候选包四项 SHA-256 均通过。
- 当前仍有四项外部提醒：企业 AK/SK、报告 HTTPS 地址、企业签名、真实手机与 Glass3。

### 2026-11-06 Android 12/12L 权限预检门禁

- 设备预检新增粗略定位权限检查，防止后续改动让 Wi-Fi P2P 在 Android 12/12L 上失去必要授权。
- 修改后 `android/host-smoke.sh` 全量通过，新增权限检查为 PASS，候选包已刷新并完成四项 SHA-256 校验。

### 2026-11-06 候选包现场权限说明同步

- Smoke Test 已明确 Android 12/12L 需要同时授予精确和粗略定位权限。
- 候选包现场文档已重新同步，四项 APK SHA-256 校验通过。

### 2026-11-06 眼镜端连接恢复自动重发当前步骤

- 修复连接在 ACK 超时前恢复时不自动重发的问题；现在会自动重发同一条步骤提交，巡检人员无需重复点击。
- 通过原有幂等编号避免重复记账；主机全量回归、双端构建、现场脚本和候选包校验全部通过。
- 真实 Glass3 的断线/恢复时序仍需现场确认。

### 2026-11-06 手机端 SDK 监听注册失败保护

- Rokid 手机 SDK 的监听注册现在可安全失败并重试；部分注册成功时会清理现场，避免重复监听和重复消息。
- 主机全量回归、双端 lint/构建、现场脚本和候选包校验均通过；真实 SDK 服务异常仍需现场观察实际提示。

### 2026-11-06 Rokid 客户端编号可配置

- 两端的 Rokid clientId 改为由 `local.properties` 的 `rokid.clientId` 注入，默认值保持 `InspectionGlass`，适配企业登记的不同编号时不再改源码。
- 配置模板、现场清单和 Smoke Test 已同步；主机全量回归、构建、脚本演练和候选包校验通过。

### 2026-11-06 候选包补齐现场配置模板

- 候选包/正式包新增 `docs/local.properties.example`，现场配置不再需要另找源码目录。
- 主机回归会检查模板存在，候选包同步、APK 校验和全量构建均通过。

### 2026-11-06 现场工具路径自动发现复核

- 现场脚本的 SDK 工具自动发现已用受限 PATH 实测：即使 `adb` 不在 PATH，仍能从 `sdk.dir/platform-tools/adb` 找到并完成预检。
- README、交接清单和 Smoke Test 已说明这一点，候选包现场文档已同步。

### 2026-11-06 交付状态复核

- Android `./host-smoke.sh` 重新执行通过：预检 **0 个失败、4 个现场条件提醒**；协议 **41/41**、手机端 **10/10**，双端 lint、Debug/Release 构建、现场脚本演练和候选包四项 SHA-256 校验全部通过。
- Web 端按项目实际脚本重新执行：`npm run typecheck`、`npm run lint`、`npm run test` **46/46**、`npm run build` 和 `PW_REUSE_SERVER=1 npm run test:e2e` **32/32** 全部通过。
- 新增 [LEADERSHIP_STATUS.md](LEADERSHIP_STATUS.md)，用非技术语言说明已完成内容、现场剩余条件和进度口径；README 已增加入口链接。
- 候选包同步时一并带上 `docs/LEADERSHIP_STATUS.md`，交接人员在同一目录即可查看面向管理人员的说明；主机回归增加文件存在性检查。
- 对照官方 `RokidSuuport/glass3_sdk_demo` 最新示例后，眼镜端物理按键广播处理补上 `abortBroadcast()`，并在主机回归加入静态门禁，避免系统默认按键动作与巡检动作同时触发。
- 眼镜端 Debug/Release 产物已用 `--rerun-tasks` 强制重编译确认改动进入 APK；候选包已重新同步，四项 SHA-256 全部 `OK`。
- 按官方眼镜清单补齐蓝牙扫描/连接、附近 Wi-Fi、定位和常亮权限；设备预检现在从眼镜 APK 逐项核对，主机回归首次预检允许旧 APK 提醒，构建后预检严格通过。
- 眼镜端 SDK 监听注册如果只完成一部分就失败，现在会先移除已挂上的监听再延迟重试，避免重连后重复收到同一消息；主机全量回归通过。
- 现场设备预检新增 `--phone PHONE_SERIAL --glass GLASS_SERIAL`，会按序列号分别确认手机和 Glass3；未同时提供两项或重复序列号会直接拒绝。未指定序列号时仍保留数量检查，但会明确提示现场安装必须指定设备角色。
- `field-install.sh` 在源码工程中核对 APK 包名与 `local.properties` 的企业登记包名；`field-script-smoke.sh` 新增设备角色参数回归，随后 `android/host-smoke.sh` 全量通过。
- 本次结果仍只证明本机软件和离线演练通过；真实手机、Glass3、企业凭证、正式签名、真实连接及正式报告服务仍需现场完成。

### 2026-11-06 现场角色核对复验

- `device-preflight.sh --phone ... --glass ...` 的显式角色核对已用 fake adb 脚本验证；只传一台或重复序列号会返回参数错误。
- `field-install.sh` 的包名配置核对、`field-script-smoke.sh` 和 `android/host-smoke.sh` 均通过。
- 当前真实 `adb devices` 仍为 0 台；候选包四项 `SHA256SUMS` 均为 `OK`。因此这次补强提升了现场防错能力，但没有把无设备状态写成真机验收。

### 2026-11-06 手机端权限按官方示例收敛

- [x] 对照官方双端示例后，移除手机端对 `BLUETOOTH_ADVERTISE` 的运行时申请；手机只负责扫描、连接和发起 P2P，眼镜端保留官方所需的广播声明。
- [x] Smoke Test、Android README 和预检说明已同步；APK 最终清单若因 SDK 依赖合并该声明，预检会区分“依赖声明”和“应用主动申请”，不把它误判成应用行为。
- [x] `android/host-smoke.sh`：协议 **41/41**、手机端 **10/10**、双端 lint/Debug/Release 构建、现场脚本演练和候选包四项校验全部通过；预检 **0 个失败、4 个现场条件提醒**。
- 当前候选包 APK 哈希以 `artifacts/rokid-rc-20261106/SHA256SUMS` 为准；手机权限改动已进入 APK，候选包已重新同步。

### 2026-11-06 现场一键启用流程

- [x] 新增 `android/field-commission.sh`：输入手机和 Glass3 序列号后，自动完成严格预检、非破坏性安装和诊断报告留档。
- [x] 候选包、正式包脚本和现场交接资料均已带上该命令；离线双设备演练验证了正确路由、未签名 Release 拦截和诊断文件生成。
- [x] `android/host-smoke.sh` 全量通过；候选包四项 `SHA256SUMS` 全部 `OK`，真实设备仍未连接。

### 2026-11-07 交付前回归复验

- 重新执行 `android/host-smoke.sh`：协议 **41/41**、手机端 **10/10**，双端 lint、Debug/Release 构建、现场脚本演练和候选包同步全部通过；候选包四项 APK 的 `SHA256SUMS` 均为 `OK`。
- 重新执行 `android/device-preflight.sh`：**0 个软件失败、4 个外部条件提醒**（企业 AK/SK、报告 HTTPS 地址、企业签名、真实设备）。预检从 `local.properties.sdk.dir` 自动找到 JDK/SDK 工具，即使 `adb` 未加入 PATH 也能完成离线检查。
- 单独执行 `android/device-preflight.sh --require-device` 按预期失败：当前 `adb` 设备为 **0 台**，严格门禁要求同时连接手机和 Glass3；因此没有把离线演练写成真机验收。
- 当前仍未完成的工作只依赖现场条件：真实手机与 Glass3 的蓝牙/P2P、相机和照片传输、实体按键、断线恢复，以及企业凭证、正式签名和正式 HTTPS 报告服务。

### 2026-11-07 Android 终态消息发送顺序修复

- 修复手机端最后一步完成时的异步竞态：`task.complete` 现在通过 `step.ack` 的发送成功回调触发，避免 SDK/P2P 两条异步发送乱序，眼镜先收到过早的任务完成通知。
- 本地任务状态和报告仍在发送 ACK 前提交；如果 ACK 或连接在回调前中断，连接恢复逻辑会按原 taskId 幂等重发终态通知，不会重复记账。
- `:phone-app:testDebugUnitTest`、`:phone-app:lintDebug` 和 `:phone-app:assembleDebug` 通过；随后 `android/host-smoke.sh` 通过，协议 **41/41**、手机端 **10/10**、双端 lint/Debug/Release 构建、现场脚本和候选包四项 SHA-256 全部通过。
- 本次更新后的候选包哈希以 `artifacts/rokid-rc-20261106/SHA256SUMS` 为准；Release 仍为 unsigned，真机链路仍待现场。

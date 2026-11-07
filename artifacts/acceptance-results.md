# P0 验收证据映射

核查日期：2026-10-28。范围为 [ACCEPTANCE.md](../ACCEPTANCE.md) 的全部 34 条 P0 用例。

最终执行结果：单元/组件 36/36，开发模式 E2E 15/15，构建模式 E2E 15/15；全部要求的工程命令通过。以下状态依据实际执行与截图复核更新，见 [verification.md](verification.md)。B04 的真实摄像头及系统权限测试单独列为人工补测，不以模拟媒体结果替代。

## 自动化证据索引

E01—E13 均位于 [tests/e2e/inspection.spec.ts](../tests/e2e/inspection.spec.ts)。其中 E01 以三种分辨率分别运行，端到端测试合计 15 项。

| 索引 | 测试完整名称 |
|---|---|
| E01 | 完整流程、异常、暂停恢复与报告 1366×768 / 1440×900 / 1920×1080（3 项） |
| E02 | 校验资产、结果和异常说明，删除照片重新阻断，键盘不能跳步 |
| E03 | 上传三种图片、拒绝错误格式/损坏/超限文件，刷新后 Blob 图片可见 |
| E04 | 摄像头拒绝权限后保留替代入口，不重复请求权限 |
| E05 | 照片及步骤写入失败不推进，重试后原子保存 |
| E06 | 关闭采集后迟到图片不写入新步骤，迟到相机授权释放 tracks |
| E07 | 模拟摄像头拍照成功，保存后停止所有 tracks |
| E08 | 模板快照、内部顺序与通用新增检查项，新任务执行新版本 |
| E09 | 报告 JSON、打印、历史只读和多任务隔离 |
| E10 | 重置取消保留数据，连续三轮重置不残留任务图片和事件 |
| E11 | 首末固定步骤的照片与结果要求也由配置驱动 |
| E12 | 非默认设备和人员下发后刷新，管理区与眼镜保持同一快照 |
| E13 | 原生双击不会越过连续无必填资料的检查项 |

单元与组件证据：

- U： [tests/unit/domain.test.ts](../tests/unit/domain.test.ts)，18 项纯领域测试。
- S： [tests/unit/domain-storage.test.ts](../tests/unit/domain-storage.test.ts)，8 项 IndexedDB/命令服务测试。
- C： [tests/unit/components.test.tsx](../tests/unit/components.test.tsx)，10 项组件与图片输入边界测试。

## A. 任务下发与步骤控制

| ID | 状态 | 测试证据及可观察断言 |
|---|---|---|
| A01 | 通过 | E01 下发后出现“开始巡检”，最终数据库中只有一个任务；E02 在 assigned 状态刷新后仍等待开始；E12 选择 B01、李工下发并验证管理区、眼镜及持久化快照一致。U `seeds are independent and dispatch saves separate snapshots, awaiting an explicit start` 验证 assigned、无 startedAt、只允许一个未完成任务。 |
| A02 | 通过 | E02 输入错误资产码后显示 ASSET-A03 且保持“确认巡检设备”，模拟扫描后进入柜门检查。U `requires the exact assigned asset and worker confirmation without advancing on error` 验证错误码及缺确认均不推进，正确后保存确认时间。 |
| A03 | 通过 | E01 第 2 步缺照片点击完成，断言具体“至少 1 张照片”提示及当前步骤未改变，并保存缺照片截图。U `reports specific missing photos, result and abnormal note and preserves the original state` 验证失败不修改原任务。 |
| A04 | 通过 | E02 已添加图片但未选择结果时断言“请选择检查结果”，随后仍能在本步填写。U `reports specific missing photos, result and abnormal note and preserves the original state` 验证结果缺失阻断。 |
| A05 | 通过 | E02 选择异常、无说明时断言“请填写异常说明”；补写说明后进入下一步。U 同名缺项测试断言正常推进后的异常数为 1；E01/E09 最终报告断言异常摘要及异常统计。 |
| A06 | 通过 | E02 验证后续步骤按钮禁用，触发点击及方向键后当前步骤仍为柜门；说明框中的 Enter/方向键不触发完成。U `rejects a late completion with an old expectedStepId and any future step` 验证业务层拒绝未来步骤。 |
| A07 | 通过 | E02 同一事件循环连续点击，只产生一个柜门完成事件；E13 使用原生 dblclick、80ms 间隔，在连续零照片且选填结果的检查项上验证只推进一步。U `replaying commandId does not advance or add another completion event`、S `serializes rapid commands and rejects a distinct late completion instead of advancing twice` 验证同 commandId 重放幂等且没有重复事件。 |
| A08 | 通过 | U `rejects a late completion with an old expectedStepId and any future step` 验证旧 stepId 报过期且新步骤不变；S `serializes rapid commands and rejects a distinct late completion instead of advancing twice` 验证不同 commandId 携带旧 stepId 仍被拒绝，数据库仅前进一步。 |
| A09 | 通过 | E01 最终提交未勾选时出现核对提示，勾选后任务 completed、5 步均完成。U `requires review confirmation, reaches 100%, and separates completion from abnormal conclusion` 断言 80%→100%、completedAt；U `never allows final submission if any previous step is incomplete` 阻止未完成前序时提交。 |
| A10 | 通过 | E01 完成含异常的任务，数据库状态 completed，报告可见“发现异常”及实际异常说明。E09 导出统计 abnormal=1、percent=100 且 conclusion=发现异常；C `renders archived step records as read-only and separates completion from abnormal results` 同时显示已完成与发现异常。 |

## B. 图片与持久化

| ID | 状态 | 测试证据及可观察断言 |
|---|---|---|
| B01 | 通过 | E01 使用本地素材、断言 fixture/isDemo/taskId；E06 验证照片仍关联原柜门 stepId，新步骤没有照片。C `creates previews from Blobs, labels fixtures and releases object URLs` 验证预览与“演示素材 · 已保存”标记。 |
| B02 | 通过 | E03 上传 PNG/JPEG/WebP，拒绝错误格式、损坏图片、超过 10MB 文件，刷新后仍有原 3 张图片；数据库来源 upload、isDemo=false。C `rejects SVG and oversized images before decode`、`rejects an SVG disguised with an allowed MIME type` 验证输入边界。 |
| B03 | 通过 | E04 模拟 NotAllowedError，显示无法访问摄像头，仅发起一次权限请求，并通过演示图片完成当前步。C `shows camera refusal without disabling fixture and file fallback` 验证素材按钮、上传入口保持可用。真实系统权限分支见 B04。 |
| B04 | 自动化通过；真实硬件待人工补测 | E07 使用 Canvas MediaStream 模拟摄像头拍照，验证照片 source=camera、isDemo=false，保存关闭后所有 tracks.readyState=ended；E04 验证拒绝权限不重复请求。C `stops active camera tracks when leaving the preview`、`stops a camera stream that resolves after the capture dialog closes` 验证资源释放。以上均为模拟媒体或 mock，**未证明真实摄像头、操作系统授权及硬件画面**；按 README 人工步骤补测并记录设备/浏览器。 |
| B05 | 通过 | E01 在指示灯步骤保存异常草稿与照片，暂停、刷新、继续后断言说明、单选结果、原步骤与可解码图片恢复。S `initializes only once and recovers task, draft, photo bytes and events from a new connection` 验证新连接恢复完整任务、事件、Blob 字节及照片关联。 |
| B06 | 通过 | E02 删除唯一照片后再次出现照片缺项提示；U `rejects wrong photo bindings and late photos, and recalculates validation after deletion` 验证照片与关联均删除、条件重新计算。S `a failed photo deletion preserves the Blob and association; retry deletes them together` 验证失败回滚及重试后重新阻断完成。 |
| B07 | 通过 | E02 已完成步骤弹层显示“已完成 · 只读”，没有 input/textarea/select 或删除按钮；E09 历史报告保留照片与说明。U 完成任务测试验证 draft/addPhoto 命令被拒绝；C 只读报告测试验证无编辑/删除入口。 |
| B08 | 通过 | E05 注入照片及事件 store 的 QuotaExceededError，验证可见保存失败、当前步骤不变、无孤立图片/关联/完成事件；点击重试后只产生一次事件。S 分别覆盖新增照片、完成、删除照片、重置的中途写入失败，验证事务整体回滚，同一命令可安全重试。 |
| B09 | 通过 | E01/E03 刷新后图片 complete 且 naturalWidth>0；S 新连接恢复测试直接读取原 Blob 字节。C `creates previews from Blobs, labels fixtures and releases object URLs` 验证 URL 由 Blob 创建且卸载释放；U JSON 导出测试断言不存在 blob: 路径。 |
| B10 | 通过 | E06 延迟 canvas 结果，关闭采集、上传有效图片并推进后释放旧结果，数据库仍只有原步骤的一张有效图片，新步骤 photoIds 为空；晚到摄像头授权的 track 被停止。U `rejects wrong photo bindings and late photos, and recalculates validation after deletion` 拒绝原绑定不一致及过期步骤，重置测试拒绝任务消失后的迟到照片。 |

## C. 模板、报告和重置

| ID | 状态 | 测试证据及可观察断言 |
|---|---|---|
| C01 | 通过 | E08 改第 3 步标题与照片数为 2，第二个任务显示新名称，只有一张照片时阻断，补齐后继续；数据库新任务快照 version=2。U `saving template creates a higher version and does not alter dispatched task snapshots` 验证新配置进入下一任务。 |
| C02 | 通过 | E08 先下发再改模板，旧任务仍执行原指示灯标题、1 张照片和 5 步流程，数据库旧快照 version=1、新任务 version=2；实际打开旧报告，断言“模板 v1”且不含新版步骤标题。U 同名快照测试验证旧模板对象内容保持不变。 |
| C03 | 通过 | E08 新增、上移、下移、再新增删除，断言首尾名称固定；新检查项通过通用执行页完成。E11 首末步骤自定义照片与结果要求也实际生效。U `validates template names, titles, IDs, minimum items, anchors, photos and versions` 校验 ID 唯一、至少一内部项及照片整数；C 模板编辑测试验证默认模板未被直接修改。 |
| C04 | 通过 | E09 断言报告资产、人员、3 张图片、5 个步骤、实际下发/开始/完成时间，以及时间线条数和每条摘要；调用打印函数，print media 下所有 .no-print 控件均不可见而 report-paper 可见。E01 保存完成报告全页截图并断言异常说明；U/C 验证任务数据与照片结果一致。已目视检查三种报告截图；打印样式由 print media 验证，具体原生系统打印窗口不由 stub 测试证明。 |
| C05 | 通过 | E09 捕获真实浏览器下载并解析 JSON，断言 task.id、完整统计、结论、3 张照片的 MIME/大小/taskId，且不含 blob:；U `exports durable photo metadata with correctly isolated history and no object URLs or Blobs` 验证无 blob 字段、包含尺寸与来源。 |
| C06 | 通过 | E09 完成两个任务后打开第一份报告，断言第一任务 ID、原异常说明及只有 3 张照片；旧报告时间线条数和每条摘要与第一任务原事件逐项相等，数据库两任务照片分别归属。U `exports durable photo metadata with correctly isolated history and no object URLs or Blobs` 验证导出的事件均属于指定任务。 |
| C07 | 通过 | E10 取消重置后保留照片，确认后 tasks/photos/events 全空且模板 version=1、5 步、3 个资产；刷新后仍为空。S `reset atomically restores defaults and removes all task, image and event records` 验证修改后的模板恢复；S reset 失败测试验证不会清一半。摄像头关闭/卸载释放由 E07、C 活跃与迟到 track 测试覆盖，真实硬件见 B04。 |
| C08 | 通过 | E10 实际循环三轮：下发、开始、完成设备确认、保存照片、取消/确认重置、刷新；每轮断言任务/图片/事件为空、configuration 仅 1 条、默认资产 3 个。此项的“执行”覆盖部分执行后重置，不把它写成完成三轮完整五步巡检；完整五步由 E01 三种分辨率独立覆盖。 |

## D. 页面与工程

| ID | 状态 | 测试证据及可观察断言 |
|---|---|---|
| D01 | 通过 | E01 分别以 1366×768、1440×900、1920×1080 完成主线，每个分辨率保存 5 张状态截图，并断言 document.scrollWidth≤innerWidth；按钮由 Playwright 实际点击。并断言当前标题、完成按钮完整位于视口内、缺项提示与视口相交。已目视检查 [screenshots/](screenshots/) 的 15 张图，文字、按钮与纵向滚动布局符合要求。 |
| D02 | 通过 | E02 在说明框按 Enter/方向键不推进；E03 在采集弹层连续 Tab 保持焦点约束，Esc 关闭后回到采集按钮。C `closes native dialog through cancel and restores focus`、`wraps Tab in both directions and skips disabled or nonvisible controls` 验证关闭恢复、Tab/Shift+Tab 双向焦点循环及跳过隐藏控件。 |
| D03 | 通过 | E01 五种状态截图及全链路实际按钮；E02/E05 验证缺项与失败反馈；U/E09 根据实际 task/stepRecords/photoIds 验证进度、照片与异常数。已结合截图及代码核查中文标签、真实空状态，无假同步或硬件在线状态。 |
| D04 | 通过 | E01 拦截并拒绝所有非 http://127.0.0.1:5173/ 请求，整条本地素材主线运行后断言 external=[]、pageerror=[]。这证明核心链路无公网资源请求，未宣称曾修改电脑网卡或物理断网。 |
| D05 | 通过 | 必须以最终实际运行的 npm run typecheck、npm run lint、npm run test、npm run build、npm run test:e2e 退出码和测试汇总为证据，见 [verification.md](verification.md)。最终实际结果为 26 项领域/存储、10 项组件通过，15 项 E2E 在开发及构建模式均通过。 |
| D06 | 通过 | [playwright.config.ts](../playwright.config.ts) 的 DEMO_BUILD=1 启动 npm run demo，已实际执行构建后全部 15 项端到端测试，15/15 通过。开发与演示均固定 http://127.0.0.1:5173，strictPort；本结论来自构建产物实际运行。 |

## 最终证据收口

1. 最终命令、版本、退出结果已写入 verification.md，失败及修复记录保留。
2. 已检查三种分辨率全部 15 张截图，报告的模板版本、五步记录、时间、异常与照片完整。
3. C02/C04/C06 已补充可见报告版本、打印隐藏控件和旧报告时间线隔离断言，补充后的测试在开发与构建模式均通过。
4. B04 真实硬件、系统授权和原生打印对话框属于人工补测；模拟媒体、print media 和 window.print stub 只证明代码路径，不冒充硬件或系统界面实测。

# 开发参考资料

以下用于开发阶段查阅。界面和领导演示报告只展示业务内容，不输出资料列表。

## Codex 的项目指令与分阶段执行

- OpenAI Codex Best practices：
  https://developers.openai.com/codex/learn/best-practices
- OpenAI ExecPlans：
  https://developers.openai.com/cookbook/articles/codex_exec_plans

参考方式：用 AGENTS.md 保留工作规则，用执行计划记录阶段目标、进度、决策和实际测试结果。

## 前端脚手架

- Vite Getting Started：
  https://vite.dev/guide/

参考方式：使用 react-ts 模板；启动前核对当前 Node 兼容要求，不凭印象固定过旧环境。

## 分步交互

- SurveyJS Multi-Step Form Wizard：
  https://surveyjs.io/form-library/examples/multi-step-form-wizard/documentation

参考方式：逐步呈现、进度提示与完成校验。当前项目采用轻量自定义步骤页，不引入整个问卷管理平台。

## 眼镜交互

- Rokid 按键与侧边触控：
  https://x-docs.rokid.com/docs/代码示例/50-system/01-按键与侧边触控.html
- Rokid 产品手册：
  https://x-docs.rokid.com/docs/terminal-sdk/resources/产品手册.html

参考方式：任务和现场采集流程、确认与导航操作。浏览器 Demo 中的步骤锁定、快照和记录保存由本项目实现。

## 图片与存储

- MDN getUserMedia：
  https://developer.mozilla.org/en-US/docs/Web/API/MediaDevices/getUserMedia
- MDN IndexedDB：
  https://developer.mozilla.org/en-US/docs/Web/API/IndexedDB_API

参考方式：摄像头需要用户许可和安全上下文；本机使用 localhost/loopback 进行开发，远程部署使用 HTTPS。IndexedDB 用于结构化数据和 Blob 保存。

## 测试和视觉检查

- Playwright Visual comparisons：
  https://playwright.dev/docs/test-snapshots
- Playwright Test fixtures：
  https://playwright.dev/docs/test-fixtures

参考方式：独立浏览器上下文、用例 fixture、截图记录和相同环境下的视觉回归。

本任务包定义的页面、状态、范围和验收标准是本项目的设计方案，不是上述产品已有功能的照录。

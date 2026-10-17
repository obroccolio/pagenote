# pagenote Android 端任务拆解

> 执行顺序：先契约与骨架，再阅读与笔记，再标注与笔迹，再 OCR 与准备管线，再 Agent 与模型，最后集成与真机。
> 每完成一个任务更新复选框，保持变更可回链到 `requirements.md` 验收。

# Implementation Plan

- [x] 1. 跨端契约与测试夹具
  - 在 `android/docs/spec/contracts/` 落 `project.schema.json`、`annotations.schema.json`、`ai-reviews.schema.json`、`prepared-manifest.schema.json`、分页与 revision 示例。
  - 在 `android/shared-tests/` 放桌面语义回归夹具：分页、空页、assets、revision 冲突、标注、准备缓存。
  - _Requirement: 项目与数据兼容，构建与测试_

- [x] 2. 安卓工程骨架与构建隔离
  - 新建 Gradle 工程与 `app/core-project/core-reader/feature-notes/feature-annotations/feature-pen/feature-ocr/agent/shared-tests` 模块。
  - 接本地 SDK 与 Gradle Wrapper，`assembleDebug` 按 ABI 可出可安装产物。
  - 桌面构建忽略 `android/`，安卓构建只认 `android/`；验证互不干扰。
  - _Requirement: 构建与测试，约束与非功能_

- [x] 3. 项目管理与分页笔记
  - 实现项目新建、打开、保存、assets 导入导出、原子写。
  - 实现 `splitPageNotes` 同语义分页与合并，空页占位。
  - 实现整页 SHA-256 revision、`expectedRevision` 冲突拒绝、按页历史与恢复。
  - _Requirement: 项目与数据兼容，阅读与笔记_

- [ ] 4. 阅读器与双栏自适应（部分：渲染比例/区域映射/准备缓存校验已落地；SAF 项目入口、PdfRenderer 阅读、PDF/笔记窄屏堆叠与宽屏并排已接通，真机验收待续）
  - [ ] 4.1 SAF 打开项目目录、解码并校验 `project.pagenote`、使用 `PdfRenderer` 翻页。
  - [ ] 4.2 原生文字/文字块抽取、整页与区域 PNG、缩放平移及缓存接入（部分完成：API 35 原生文本抽取和 PDF 视口缩放/平移已实现，区域图像与缓存接入待完成）。
  - [ ] 4.3 手机上下页、平板左右对照、Markdown 源文档阅读。
  - PDF 按页渲染、原生文字与文字块抽取、整页 PNG。
  - 手机竖屏上下页，平板横屏左右对照；缩放、平移、适应宽度、重置缩放。
  - Markdown 源文档阅读。
  - _Requirement: 阅读与笔记，布局与适配_

- [ ] 5. 笔记渲染与轻量编辑（部分：Markwon 基础编辑/预览、GFM 表格、分页读写与 revision 冲突校验已落地；公式、代码高亮、Mermaid、图片和历史界面待续）
  - GFM、公式、代码高亮、Mermaid 成功与失败态、表格、图片。
  - 轻量开源 Markdown 编辑适配，不自研编辑器。
  - 超长页截断与按需读取，不一次全量装入。
  - _Requirement: 阅读与笔记_

- [x] 6. 标注与 AI 标注工作台
  - 文字选区标注、区域框选标注、标注列表、删除。
  - AI 标注创建、改写、状态流转，成功改写后才能 `applied`。
  - 交接读写与 stale 判定。
  - _Requirement: 标注_

- [ ] 7. 手写笔迹（部分：原生 View、压感、分页编辑和 SAF 项目保存已实现；真机验收待做）
  - [x] 原生 View 笔/荧光笔/橡皮、stylus 侧键与硬件橡皮输入、撤销、重做、按页清除。
  - [x] 笔迹按页存取并保留 annotations.json 中的其他批注、页面和未知字段。
  - [ ] 主动笔与被动笔、边缘、断触、误触抑制真机验证。
  - _Requirement: 笔迹，布局与适配_

- [x] 8. 页面准备管线
  - 原生文字、文字块、整页与区域 PNG、Markdown 表格抽取。
  - 缓存键、manifest、坐标 1000x1000、区域保留整页图、原文变化失效、刷新重试。
  - _Requirement: 页面准备与 OCR_

- [ ] 9. 离线 OCR（部分：已打包 PP-OCRv6 tiny 中英文模型、端侧 ONNX 引擎和当前页手动识别入口；自动触发、页面准备缓存、区域重识别与真机验收待续）
  - 中英文整页与区域识别、坐标、置信度、警告、原图保留。
  - 触发规则、超时处理、失败明确标记、飞行模式可用。
  - [x] 区域重识别 API 与全页坐标映射，契约见 `features/ocr-region-recognition.md`。
  - [ ] 阅读器区域选择和 SAF 项目缓存，契约见 `features/reader-ocr-selection.md`，实施拆分见 `reader-ocr-selection-plan.md`。
  - _Requirement: 页面准备与 OCR_

- [ ] 10. 端侧 Agent 与 OpenAI 兼容接入（部分：客户端、任务循环与数据契约已实现；项目工具接入及端到端闭环待续）
  - OpenAI 兼容客户端，支持 BaseURL/Key/模型名配置，默认可配本地中转与指定模型。
  - [x] Android 模型设置 UI、Keystore 加密 Key 存储与本地中转可达性说明，契约见 `features/openai-model-settings.md`，实施拆分见 `openai-model-settings-plan.md`；debug/release 网络策略和本地构建验证通过，Keystore/Compose 仪器测试待设备执行。
  - [x] 单页讲解预览和显式追加，按页 OCR 回退、相邻页上下文、20,000 字符截断、旧提示词预览禁用及 SAF 写入恢复/revision 冲突保护；契约见 `features/page-tutor-preview-and-append.md`，单测、APK 构建及 16 KB 检查通过，仪器测试仅编译未在设备执行。
  - 任务循环、本地工具收敛集、多步上限、revision 冲突处理、实际完成范围报告。
  - 提示词索引与按 revision 分批读，逐页独立请求加相邻源页。
  - 单页串行汇总只报告真实完成/修改的请求页；限流停止，普通失败继续。
  - 密钥不落项目目录，未配置明确提示。
  - _Requirement: Agent 与模型_

- [ ] 11. 设置、语言、主题（部分：DataStore 与设置面板、运行时主题/语言/比例/动画已实现；真机验收待续）
  - 主题、语言、双栏比例、动画开关持久化。
  - 语言键语义复用桌面 `locales/catalog.json`。
  - [x] 原生设置面板与运行时生效，契约见 `features/reader-settings-ui.md`。
  - _Requirement: 布局与适配_

- [ ] 12. 自动化与真机验收
  - 契约回归、单元、集成、模型真接口（本地中转）测试。
  - 手机与平板走通阅读、笔记、标注、Agent、笔迹闭环。
  - 飞行模式 OCR、超大页、限流、冲突、缺模型配置异常用例。
  - [ ] 安卓启动与 16 KB 页面兼容性修复，契约见 `features/startup-and-16kb-compatibility.md`，实施拆分见 `startup-and-16kb-compatibility-plan.md`。
    - [x] 保留本地化 Compose 下的 Activity Result owner；应用单测、instrumentation 编译与 APK 编译通过，设备启动复测待完成。
    - [x] 切换到已核验的官方 OpenCV 4.14.0 AAR，并通过全部 ABI APK 的 ELF/ZIP 16 KB 检查。
    - [ ] 连接设备拒绝 APK 与测试 runner 安装（`INSTALL_FAILED_USER_RESTRICTED`）；重新安装、启动和 SAF 选择器仍待真机验收。
  - _Requirement: 构建与测试，全部 EARS_

- [ ] 13. 分支、变更日志、PR 准备
  - `codex/android-port` 基于 `beta`，PR 目标 `beta`。
  - 每次推送附 `CHANGELOG.md` 未发布区条目。
  - 安卓产物独立，不混入桌面 `release/`。
  - _Requirement: 构建与测试_

# pagenote Android 端需求说明

> 位置约定：安卓 SPEC 文档归 `android/docs/spec/` 管理，不放入总仓库 `docs/`，总仓库 `docs/` 仍服务 Win/Mac 桌面项目。
> 流程约定：先文档，再代码，最后测试。分支基于 `beta`，功能 PR 目标为 `beta`。

## 1. 背景与目标

桌面端 pagenote 已实现原文与按页笔记对照、Markdown 笔记渲染、手写笔迹、AI 标注、本地页面准备与离线 OCR、MCP 供外部 AI 调用等能力。安卓端目标是在原生 Kotlin 应用中完整复刻上述用户可见功能，并通过测试可正常使用。

终态一次规划、分阶段交付。本需求文档锁定终态验收标准，不因分阶段而缩小成功定义。

### 当前设备支持范围

- 当前适配目标仅为 Android 14/API 34 的小米平板 5 Pro（骁龙 870）。其他厂商、机型、手机形态和 Android 版本暂不承诺兼容，也不纳入常规回归矩阵。
- Android 当前只导入 PDF 与 Markdown 项目源；Word/PowerPoint 编辑仍由桌面端负责，复杂 Office 文件在 Android 上明确提示不支持。
- Android 14/API 34 AVD 仅用于快速检查平台级行为；MIUI、骁龙 870 性能、屏幕布局和手写笔最终以目标小米实机验收。未实际使用目标硬件前不得记录为实机通过。
- API 35、16 KB 页大小及其他设备的既有测试记录属于历史验证，不构成当前迭代的持续测试门槛。若后续公开发布目标触发商店 16 KB 合规要求，应作为独立发布门禁重新启用。

### 常规验证范围

- 每个功能切片只运行受影响模块的相关单测、Debug APK 构建，以及确实涉及 Android UI/系统行为时的 API 34 平板 AVD 定向仪器测试；不重复运行全设备/全 API 矩阵。
- 目标机真机验证聚焦项目导入、PDF 与笔记阅读/保存、OCR、Agent 预览与追加、手写笔这几条核心路径；普通逻辑改动不要求每次重跑整套真机流程。
- 手机窗口、其他平板/OEM、Android 15+、16 KB 页大小和发布 ABI 静态矩阵均不属于当前日常适配验收项。历史结果只作背景记录，不扩大当前支持承诺。

## 2. 范围

### 2.1 在范围内

- 项目管理：新建、打开、解析 `project.pagenote` 系项目，兼容桌面项目格式。
- 阅读：PDF、Markdown 源文档阅读，页与逐页笔记对应。
- 笔记：按页 Markdown 笔记读写，分页规则与桌面一致，笔记历史与恢复。
- 渲染：公式、代码高亮、Mermaid 图表、表格、图片粘贴与显示行为与桌面一致，支持离线渲染和明确失败态。
- 标注：文字选区标注、区域框选标注、AI 标注工作台、标注状态流转。
- 笔迹：手写笔书写、荧光笔、橡皮擦、撤销、按页清除，支持主动笔压感与按键。
- 页面准备：原生文字提取、文字块坐标、整页与区域 PNG 缓存、Markdown 表格抽取、缓存失效与刷新语义。
- 离线 OCR：中英文识别、坐标、置信度、警告、原图保留，失败明确标记。
- 端侧轻量 Agent：任务循环、本地工具调用、多步上限、版本冲突处理，直连 OpenAI 兼容接口。
- Android Agent 映射桌面 MCP 学习工作流和提示词语义；Android 不提供桌面本地 MCP server。
- 模型接入：可配置 BaseURL/Key/模型名，默认支持本地中转 `http://127.0.0.1:3457/v1`。
- 布局与手势：当前目标小米平板 5 Pro 上原文/笔记左右对照、双栏比例、缩放与平移；手机形态暂不作为支持目标。
- 界面：保留桌面菜单分组、命令名称、信息层级和浅/深色视觉语言；Android 采用触控原生自适应布局。
- 构建与测试：本地可编译、可安装、可运行自动化与真机测试。

### 2.2 不在范围内

- 不移植 Electron、Windows COM、macOS AppleScript、桌面本机 MCP transport 或桌面 Office 编辑器；复杂 Office 文档工作流回到桌面端。
- 不做账号体系、自建模型网关、云同步。
- 不统一 monorepo 工具链，不改动现有 Electron 发布链路。
- 不要求 Android 像素级复刻桌面窗口尺寸；必须保持功能、菜单分组、命令语义、视觉 token 和数据语义一致，触控与手写笔交互按 Android 习惯适配。

## 3. 用户故事

1. 作为学习者，我在小米平板 5 Pro 上打开桌面建好的项目，按页阅读原文和笔记，页码对应不错乱。
2. 作为学习者，我在平板横屏下左看 PDF、右看笔记，翻页时笔记自动切换到对应页。
3. 作为学习者，我选中一段没懂的文字，写一句“补个例子”，Agent 能只改当前页对应笔记，不动其他页。
4. 作为学习者，我框选公式图片并要求重讲，Agent 能结合原图与 OCR 结果重写该页局部内容。
5. 作为学习者，我用手写笔在 PDF 上直接书写，笔迹按页保存，重开项目仍在。
6. 作为离线用户，我在飞行模式下打开扫描版 PDF，仍能看到 OCR 文字、置信度警告与原图。
7. 作为用户，我填入 OpenAI 兼容接口地址、Key、模型名后，能跑通逐页讲解与批注改写。
8. 作为开发者，我在本地能编译安装安卓包，跑通自动化测试，并在真机复现主要流程。
9. 作为学习者，我能在 Android 中编辑轻量 Markdown 笔记；需要 Word/PowerPoint 编辑时，继续在桌面端处理原文件。
10. 作为桌面端用户，我能在小米平板 5 Pro 的可收起侧栏中找到相同菜单分组，并保持原文与笔记并列。

## 4. 验收标准（EARS）

### 项目与数据兼容

- When 用户打开桌面项目时，the Android 端 shall 正确解析项目入口、原文链接、分页笔记、笔迹与 AI 标注，不丢失页。
- When 笔记包含 `<!-- pagebreak -->` 分页时，the Android 端 shall 与桌面 `splitPageNotes` 语义一致地切分与合并。
- When 笔记内容为空页时，the Android 端 shall 保留空页占位，不吞页、不错位。
- While 项目资源引用 `assets/` 图片时，when 打开或保存项目，the Android 端 shall 正确解析与拷贝资源，不产生 broken 图片。

### 阅读与笔记

- When 用户翻到第 N 页时，the Android 端 shall 显示第 N 页原文与第 N 页笔记，并保持各自滚动独立。
- When 用户修改第 N 页笔记并保存时，the Android 端 shall 计算该页完整笔记 SHA-256 revision，支持 `expectedRevision` 冲突拒绝。
- When 提交的 `expectedRevision` 与当前 revision 不一致时，the Android 端 shall 拒绝覆盖并提示重新读取后再改。
- When 用户查看笔记历史时，the Android 端 shall 按页列出历史快照并支持恢复到指定版本。

### 支持的源格式

- When 用户尝试打开 `.doc`、`.docx`、`.ppt` 或 `.pptx` 项目源时，the Android 端 shall 明确提示当前只支持 PDF/Markdown，不得创建半打开项目或修改原文件。
- When 用户需要编辑 Word/PowerPoint 文档时，the Android 端 shall 将该工作流留给桌面版；Android 不启动外部 Office 应用或远程转换服务。

### 标注

- When 用户选中文字或框选区域并填写需求时，the Android 端 shall 生成一条定位到文档/页/选区或区域的标注。
- When Agent 成功改写对应笔记后，the Android 端 shall 才能标记该标注完成，不提前标记。
- When 用户删除标注时，the Android 端 shall 仅删除该标注，不改动笔记正文。

### 笔迹

- When 用户用手写笔书写时，the Android 端 shall 记录压感、颜色、粗细与页号，并按页存储。
- When 用户使用橡皮或撤销时，the Android 端 shall 只影响当前页指定笔迹，不清除其他页。
- When 重新打开项目时，the Android 端 shall 完整恢复各页笔迹。

### 页面准备与 OCR

- When 原生文字不足、含位图或疑似乱码时，the Android 端 shall 补充 OCR，并在结果中区分原生与 OCR 通道。
- When OCR 置信度低或失败时，the Android 端 shall 保留原图与警告，不伪装成完整识别。
- When 原文发生变化后，the Android 端 shall 生成新缓存，不复用旧页缓存。
- When 用户指定区域并要求重识别时，the Android 端 shall 保留整页图与完整原生文字，另存区域识别结果。
- When 区域 OCR 成功或部分成功且包含文本时，the Android 端 shall 提供“讲解此区域”入口，并把该次区域识别文本、置信度和警告交给当前页 Page Tutor，不重复运行整页 OCR。
- When 用户通过区域 OCR 发起 Page Tutor 时，the Android 端 shall 只用该区域文本与目标页笔记作为当前页材料，不额外 OCR 相邻页；输出仍须先预览，再由用户显式追加。
- When 区域 OCR 失败、无可用文本或对应页/源已变化时，the Android 端 shall 隐藏或拒绝该讲解入口，并保留明确错误/置信度提示。
- While 离线状态下，when 用户打开已准备页面或触发本地 OCR，the Android 端 shall 不请求网络仍可完成本地流程。

### Agent 与模型

- When 用户发起逐页讲解时，the Android 端 shall 按页独立请求，携带当前页与相邻源页材料，不全章一次性生成再拆页。
- When 用户要求按标注修改时，the Android 端 shall 先读待处理标注与目标页 revision，冲突时合并或拒绝，不静默覆盖。
- When 工具调用超过多步上限或模型限流时，the Android 端 shall 停止并报告实际完成范围，不伪称完成。
- When 用户配置 OpenAI 兼容接口后，the Android 端 shall 支持自定义 BaseURL/Key/模型名，并能连通本地中转 `http://127.0.0.1:3457/v1` 与模型 `cline-free/mimo-v2.6-flash`。
- When 软件未配置模型时，the Android 端 shall 明确提示缺模型配置，不内置模型、不硬编码密钥。

### 布局与适配

- When 用户在小米平板 5 Pro 上阅读时，the Android 端 shall 支持原文与逐页笔记并列显示，并保持双栏比例、缩放与平移状态。
- When 用户缩放或平移放大页面时，the Android 端 shall 保持文字、图片与标注同步变换。
- When 用户触发适应宽度或重置缩放时，the Android 端 shall 恢复对应视图比例。

### 桌面功能区与自适应导航

- When 平板处于扩展窗口尺寸时，the Android 端 shall 显示可收起的菜单侧栏；展开时保留桌面菜单组名和相同命令分组。
- 紧凑手机窗口/抽屉布局保留为未来适配，不属于当前支持与验收范围。
- When 用户切换“文件、AI 批注、编辑、段落、格式、插入、阅读、手写批注、Word 编辑、设置、帮助”任一分组时，the Android 端 shall 保留当前文档上下文，并只显示当前模式适用的操作。
- When 窗口尺寸、方向或外接键盘状态变化时，the Android 端 shall 自适应侧栏和阅读区域，不丢失正在编辑的文本或笔迹。
- When 显示桌面风格控件时，the Android 端 shall 使用约定的品牌蓝、明暗表面色、字号层级和图标语义，同时保证触控命中区域符合 Android 使用习惯。

### 构建与测试

- When 在本地编译环境执行构建时，the Android 端 shall 可生成可安装产物，不依赖桌面 Electron 产物。
- When 执行常规安卓验证时，the Android 端 shall 只运行受影响模块的相关单测、Debug 构建，以及当前改动所需的 Android 14/API 34 平板 AVD 聚焦仪器测试；不运行其他设备或 API 版本矩阵。
- When 目标设备真机验收时，the Android 端 shall 在小米平板 5 Pro（骁龙 870，Android 14）走通阅读、笔记保存、标注/Agent 和手写笔的目标工作流；其他机型和手机形态不纳入当前验收。

## 5. 约束与非功能

- 原生 Kotlin，Jetpack Compose，PDF 与笔迹走原生链路，保证低延迟。
- 最低运行系统为 Android 14（API 34）；更早版本不属于支持范围。`minSdk`、仪器测试与发布构建须保持一致。
- Android 14/API 34 上原生 PDF 文字提取不可用时须明确走离线 OCR 回退；API 35+ 才使用平台文字内容提取。
- 复用桌面提示词协议语义（按需加载、相邻页上下文、revision 语义），不复用 Electron 壳。
- 安卓工程与桌面构建互不干扰；桌面构建忽略 `android/`，安卓构建只认 `android/`。
- 隐私：项目数据默认本地保存；模型调用走用户自带 Key，密钥不明文落盘到项目目录。
- 性能：超大页限制像素并记录实际比例，原文件可回查；OCR 超时保留原图并返回可操作错误。

## 6. 术语

- 项目：`project.pagenote` 入口加 `src/` 目录，含原文、分页笔记、资源、笔迹、标注。
- 分页：右侧笔记以 `<!-- pagebreak -->` 分页，与桌面切分语义一致。
- revision：整页笔记 SHA-256，用于并发写冲突检测。
- 页面准备：原生文字、文字块、PNG、表格、OCR 的本地缓存过程。
- 端侧 Agent：端内任务循环加少量本地工具，直连 OpenAI 兼容接口，不搬 Codex。

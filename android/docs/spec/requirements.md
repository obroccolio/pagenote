# pagenote Android 端需求说明

> 位置约定：安卓 SPEC 文档归 `android/docs/spec/` 管理，不放入总仓库 `docs/`，总仓库 `docs/` 仍服务 Win/Mac 桌面项目。
> 流程约定：先文档，再代码，最后测试。分支基于 `beta`，功能 PR 目标为 `beta`。

## 1. 背景与目标

桌面端 pagenote 已实现原文与按页笔记对照、Markdown 笔记渲染、手写笔迹、AI 标注、本地页面准备与离线 OCR、MCP 供外部 AI 调用等能力。安卓端目标是在原生 Kotlin 应用中完整复刻上述用户可见功能，并通过测试可正常使用。

终态一次规划、分阶段交付。本需求文档锁定终态验收标准，不因分阶段而缩小成功定义。

## 2. 范围

### 2.1 在范围内

- 项目管理：新建、打开、解析 `project.pagenote` 系项目，兼容桌面项目格式。
- 阅读：PDF 按页阅读，Markdown 源文档阅读。
- 笔记：按页 Markdown 笔记读写，分页规则与桌面一致，笔记历史与恢复。
- 渲染：公式、代码高亮、Mermaid 图表、表格、图片粘贴与显示行为与桌面一致。
- 标注：文字选区标注、区域框选标注、AI 标注工作台、标注状态流转。
- 笔迹：手写笔书写、荧光笔、橡皮擦、撤销、按页清除，支持主动笔压感与按键。
- 页面准备：原生文字提取、文字块坐标、整页与区域 PNG 缓存、Markdown 表格抽取、缓存失效与刷新语义。
- 离线 OCR：中英文识别、坐标、置信度、警告、原图保留，失败明确标记。
- 端侧轻量 Agent：任务循环、本地工具调用、多步上限、版本冲突处理，直连 OpenAI 兼容接口。
- 模型接入：可配置 BaseURL/Key/模型名，默认支持本地中转 `http://127.0.0.1:3457/v1`。
- 布局与手势：手机竖屏上下页模式，平板横屏左右对照，双栏比例、缩放、平移。
- 构建与测试：本地可编译、可安装、可运行自动化与真机测试。

### 2.2 不在范围内

- 安卓端不做 Word/PPT 本机编辑与 Office 转换；超出轻量 Markdown 的复杂文档回桌面处理。
- 不做账号体系、自建模型网关、云同步。
- 不统一 monorepo 工具链，不改动现有 Electron 发布链路。
- 首版不承诺与桌面像素级一致，只承诺功能与数据语义一致。

## 3. 用户故事

1. 作为学习者，我打开桌面建好的项目，在手机上继续按页阅读原文和笔记，页码对应不错乱。
2. 作为学习者，我在平板横屏下左看 PDF、右看笔记，翻页时笔记自动切换到对应页。
3. 作为学习者，我选中一段没懂的文字，写一句“补个例子”，Agent 能只改当前页对应笔记，不动其他页。
4. 作为学习者，我框选公式图片并要求重讲，Agent 能结合原图与 OCR 结果重写该页局部内容。
5. 作为学习者，我用手写笔在 PDF 上直接书写，笔迹按页保存，重开项目仍在。
6. 作为离线用户，我在飞行模式下打开扫描版 PDF，仍能看到 OCR 文字、置信度警告与原图。
7. 作为用户，我填入 OpenAI 兼容接口地址、Key、模型名后，能跑通逐页讲解与批注改写。
8. 作为开发者，我在本地能编译安装安卓包，跑通自动化测试，并在真机复现主要流程。

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
- While 离线状态下，when 用户打开已准备页面或触发本地 OCR，the Android 端 shall 不请求网络仍可完成本地流程。

### Agent 与模型

- When 用户发起逐页讲解时，the Android 端 shall 按页独立请求，携带当前页与相邻源页材料，不全章一次性生成再拆页。
- When 用户要求按标注修改时，the Android 端 shall 先读待处理标注与目标页 revision，冲突时合并或拒绝，不静默覆盖。
- When 工具调用超过多步上限或模型限流时，the Android 端 shall 停止并报告实际完成范围，不伪称完成。
- When 用户配置 OpenAI 兼容接口后，the Android 端 shall 支持自定义 BaseURL/Key/模型名，并能连通本地中转 `http://127.0.0.1:3457/v1` 与模型 `cline-free/mimo-v2.6-flash`。
- When 软件未配置模型时，the Android 端 shall 明确提示缺模型配置，不内置模型、不硬编码密钥。

### 布局与适配

- When 设备为手机竖屏时，the Android 端 shall 使用上下页对照模式；when 为平板横屏时，the Android 端 shall 使用左右对照模式。
- When 用户缩放或平移放大页面时，the Android 端 shall 保持文字、图片与标注同步变换。
- When 用户触发适应宽度或重置缩放时，the Android 端 shall 恢复对应视图比例。

### 构建与测试

- When 在本地编译环境执行构建时，the Android 端 shall 可生成可安装产物，不依赖桌面 Electron 产物。
- When 执行安卓自动化测试时，the Android 端 shall 覆盖项目解析、翻页对照、笔记读写冲突、标注流转、OCR 缓存、Agent 主流程。
- When 真机验收时，the Android 端 shall 在手机与平板各走通阅读、笔记、标注、Agent 改写闭环。

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

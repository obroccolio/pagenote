# AI Review 与交接 UI

## Scope

Task 6 最后两片：AI review 创建/列表/状态流转 UI + handoff 交接读写 UI（含 stale 处理）。数据层 File 语义已有（`ReviewStore`/`HandoffStore`），本片补 SAF 薄适配 + UI。

## Review Interaction

- 工具栏 `Review` 按钮打开本页 review 列表弹窗：列出 document/note 两侧本页 open 记录（instruction + 状态），可新建（instruction 输入 + side 选择，kind=overall）。
- 每条可标 applied/dismissed（二次确认），可删除；状态流转经 `ReviewStore.transition` 语义（open→applied/dismissed，不可逆转由 store 拒绝）。
- 新建时 quote/context 置空（移动端无框选），revision 带当前页 note revision，便于 Agent 侧 `expectedRevision` 校验。

## Review Data Contract

- 新增 `ProjectReviewStore(resolver, sourceDirectoryUri, reviewsUri)`：`list(page?)`、`create(input)`、`transition(id, to)`、`delete(id)`，语义与 File 版一致，SAF 读写 `src/ai-reviews.json`。
- 缺文件读空列表；写时建文件；transition 非法流转抛 IllegalStateException。

## Handoff Interaction

- 笔记头 `History` 旁加 `Handoff` 按钮：弹窗显示本页交接（summary + stale 标记），可新建/覆盖（summary 输入≤4000，二次确认），stale 时黄色警告并要求先 reload。
- stale 判定：`HandoffStore.view` 对当前 noteRevision（编辑器草稿 revision）与 sourceRevision（`PdfDocumentSession.sourceRevision` 或空）。

## Handoff Data Contract

- 新增 `ProjectHandoffStore(resolver, sourceDirectoryUri, handoffsUri)`：`view(page, noteRev, srcRev)`、`write(page, summary, expectedRevision, sourceRevision, currentNoteRevision)`，语义与 File 版一致，SAF 读写 `src/ai-handoffs.json`。
- `expectedRevision` 冲突抛 IllegalStateException，不覆盖。

## Acceptance (EARS)

- When 新建 review 时，shall 落盘 open 记录并列出。
- When 流转 review 状态时，非法流转 shall 拒绝。
- When 读 handoff 且 revision 漂移时，shall 标 stale 并要求 reload 后再写。
- When 写 handoff 用过期 revision 时，shall 拒绝覆盖。

## Verification

- JVM：SAF store 的 File 等价路径测试 + 仪器编译。
- `cd android && ./gradlew :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin --offline` 通过；真机待 task 12。

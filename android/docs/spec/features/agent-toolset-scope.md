# Agent 工具集范围（已实现 7 个）

## Scope

锁定端侧 Agent 首版实际接线的 7 个本地工具，避免 `LOCAL_TOOLS` 声明与实现脱节。`read_state/prepare_page/attach_image/navigate` 四个名额保留但本版不分发，调用即报 unknown tool。

## Tool Contract

- 已接线：`read_page/read_review/read_handoff/write_note/patch_note/write_review_status/write_handoff`（见 `agent-project-tools.md`、`agent-write-tools.md`）。
- 未接线：`read_state/prepare_page/attach_image/navigate` 在 `LocalToolDispatcher` 无 handler，分发即 `ok=false + unknown tool`，不中断循环。
- `ToolSchemas.SCHEMAS` 与 `AgentLoop.LOCAL_TOOLS` 保持同 key；参数名以 `ProjectAgentTools` 实现为准（`write_review_status: id/status`，`write_handoff: page/summary/expectedRevision/sourceRevision`）。

## Acceptance (EARS)

- When 模型调用 7 个已接线工具时，shall 按各自契约执行并返回 ok/touched/updated。
- When 模型调用 4 个未接线工具时，shall 返回 unknown tool 并继续循环，不伪称执行。

## Verification

- JVM：`ToolSchemasTest` 覆盖已接线参数与未知工具拒绝；`AgentRunnerTest` 覆盖 unknown tool 中断语义。
- `cd android && ./gradlew :agent:testDebugUnitTest --offline` 通过。

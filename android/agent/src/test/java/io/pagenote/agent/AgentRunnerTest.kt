package io.pagenote.agent

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** 假 ChatClient：只按预设回复，不调真网络。 */
private class FakeChatClient(
  private val replies: List<String> = emptyList(),
  private val error: Throwable? = null,
) : ChatClient {
  var calls = 0
    private set

  override suspend fun complete(endpoint: ModelEndpoint, messages: List<Map<String, String>>): String {
    calls++
    error?.let { throw it }
    if (replies.isEmpty()) return "DONE"
    return replies[minOf(calls - 1, replies.lastIndex)]
  }
}

private fun endpoint(apiKey: String = "k", model: String = OpenAiCompatibleDefaults.LOCAL_RELAY_MODEL) =
  ModelEndpoint(
    baseUrl = OpenAiCompatibleDefaults.LOCAL_RELAY_BASE_URL,
    apiKey = apiKey,
    model = model,
  )

private fun task(maxSteps: Int = 8) =
  AgentTask(documentId = "doc", page = 3, instruction = "explain", expectedRevision = null, maxSteps = maxSteps)

private fun messages() = listOf(mapOf("role" to "user", "content" to "explain page 3"))

class AgentRunnerTest {
  @Test
  fun missingApiKeyThrowsWithoutNetwork() = runBlocking {
    val runner = AgentRunner(FakeChatClient())
    try {
      runner.run(task(), endpoint(apiKey = ""), messages())
      fail("expected MissingModelConfigException")
    } catch (e: MissingModelConfigException) {
      // expected
    }
  }

  @Test
  fun missingModelThrowsWithoutNetwork() = runBlocking {
    val runner = AgentRunner(FakeChatClient())
    try {
      runner.run(task(), endpoint(model = ""), messages())
      fail("expected MissingModelConfigException")
    } catch (e: MissingModelConfigException) {
      // expected
    }
  }

  @Test
  fun missingBaseUrlThrowsWithoutNetwork() = runBlocking {
    val runner = AgentRunner(FakeChatClient())
    val bad = endpoint().copy(baseUrl = "")
    try {
      runner.run(task(), bad, messages())
      fail("expected MissingModelConfigException")
    } catch (e: MissingModelConfigException) {
      // expected
    }
  }

  @Test
  fun doneReportsCompletionScope() = runBlocking {
    suspend fun saveNote(@Suppress("UNUSED_PARAMETER") request: ToolRequest): ToolResult =
      ToolResult(ok = true, detail = "saved", updatedPage = 3)
    val tools = LocalToolDispatcher(mapOf("write_note" to ::saveNote))
    val runner = AgentRunner(FakeChatClient(listOf("TOOL write_note page=3", "DONE")), tools)
    val event = runner.run(task(), endpoint(), messages())
    assertTrue(event is AgentEvent.Done)
    event as AgentEvent.Done
    assertEquals(listOf(3), event.pages)
    assertEquals(listOf(3), event.updated)
  }

  @Test
  fun stepLimitReportsFailedNotDone() = runBlocking {
    // 模型永远不说 DONE：跑满 maxSteps 后必须报 Failed，不能伪称完成。
    val runner = AgentRunner(FakeChatClient(listOf("thinking...")), LocalToolDispatcher())
    val event = runner.run(task(maxSteps = 3), endpoint(), messages())
    assertTrue(event is AgentEvent.Failed)
    event as AgentEvent.Failed
    assertTrue(event.reason.contains("step limit"))
  }

  @Test
  fun unknownToolIsRejected() = runBlocking {
    val runner = AgentRunner(FakeChatClient(listOf("TOOL rm_rf_all page=3")), LocalToolDispatcher())
    val event = runner.run(task(), endpoint(), messages())
    assertTrue(event is AgentEvent.Failed)
    event as AgentEvent.Failed
    assertTrue(event.reason.contains("unknown tool"))
  }

  @Test
  fun rateLimitReportsActualScope() = runBlocking {
    val runner = AgentRunner(
      FakeChatClient(error = IllegalStateException("model http 429")),
      LocalToolDispatcher(),
    )
    val event = runner.run(task(), endpoint(), messages())
    assertTrue(event is AgentEvent.Failed)
    event as AgentEvent.Failed
    assertTrue(event.reason.contains("rate limited"))
  }

  @Test
  fun revisionConflictDoesNotSilentlyOverwrite() = runBlocking {
    suspend fun conflict(@Suppress("UNUSED_PARAMETER") request: ToolRequest): ToolResult =
      ToolResult(ok = false, detail = "note changed, read again")
    val tools = LocalToolDispatcher(mapOf("write_note" to ::conflict))
    val runner = AgentRunner(
      FakeChatClient(listOf("TOOL write_note page=3", "DONE")),
      tools,
    )
    val event = runner.run(task(), endpoint(), messages())
    assertTrue(event is AgentEvent.Done)
    event as AgentEvent.Done
    // 冲突写入失败：工具触达过该页但未更新，更新页必须为空。
    assertEquals(listOf(3), event.pages)
    assertTrue(event.updated.isEmpty())
  }

  @Test
  fun doneWithoutToolTouchReportsNoPages() = runBlocking {
    val runner = AgentRunner(FakeChatClient(listOf("DONE")), LocalToolDispatcher())
    val event = runner.run(task(), endpoint(), messages())
    assertTrue(event is AgentEvent.Done)
    event as AgentEvent.Done
    // 没有工具实际触达任何页：完成页必须为空，不能预填任务页。
    assertTrue(event.pages.isEmpty())
    assertTrue(event.updated.isEmpty())
  }
}

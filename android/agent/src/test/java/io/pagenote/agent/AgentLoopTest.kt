package io.pagenote.agent

import org.junit.Test

class AgentLoopTest {
  @Test
  fun toolsStayConverged() {
    val tools = AgentLoop.LOCAL_TOOLS
    assert(tools.contains("prepare_page"))
    assert(tools.contains("write_note"))
    assert(tools.size in 8..12)
  }

  @Test(expected = IllegalArgumentException::class)
  fun badMaxStepsRejected() {
    AgentLoop.validate(AgentTask("doc", 1, "teach", null, maxSteps = 99))
  }
}

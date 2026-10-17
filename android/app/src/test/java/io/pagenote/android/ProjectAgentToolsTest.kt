package io.pagenote.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectAgentToolsTest {
  @Test
  fun readPageArgsValidation() {
    val valid = ProjectAgentTools.parsePage(mapOf("page" to "3"), currentPage = 1)
    assertEquals(3, valid)

    val fallback = ProjectAgentTools.parsePage(emptyMap(), currentPage = 2)
    assertEquals(2, fallback)

    try {
      ProjectAgentTools.parsePage(mapOf("page" to "0"), currentPage = 1)
      throw AssertionError("expected bad page")
    } catch (_: IllegalArgumentException) {
    }
    try {
      ProjectAgentTools.parsePage(mapOf("page" to "abc"), currentPage = 1)
      throw AssertionError("expected bad page")
    } catch (_: IllegalArgumentException) {
    }
  }

  @Test
  fun reviewStatusFilter() {
    assertTrue(ProjectAgentTools.validReviewStatus(null))
    assertTrue(ProjectAgentTools.validReviewStatus("open"))
    assertFalse(ProjectAgentTools.validReviewStatus("bogus"))
  }

  @Test
  fun toolNamesAreRegistered() {
    assertTrue(ProjectAgentTools.READ_TOOLS.contains("read_page"))
    assertTrue(ProjectAgentTools.READ_TOOLS.contains("read_review"))
    assertTrue(ProjectAgentTools.READ_TOOLS.contains("read_handoff"))
  }
}

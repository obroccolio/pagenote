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

  @Test
  fun writeToolsAreRegistered() {
    assertTrue(ProjectAgentTools.WRITE_TOOLS.contains("write_note"))
    assertTrue(ProjectAgentTools.WRITE_TOOLS.contains("patch_note"))
    assertTrue(ProjectAgentTools.WRITE_TOOLS.contains("write_review_status"))
    assertTrue(ProjectAgentTools.WRITE_TOOLS.contains("write_handoff"))
  }

  @Test
  fun patchArgsValidation() {
    val parsed = ProjectAgentTools.parsePatch(
      mapOf("start" to "5", "end" to "10", "replacement" to "hi"),
      pageLength = 20,
    )
    assertEquals(5, parsed.start)
    assertEquals(10, parsed.end)
    assertEquals("hi", parsed.replacement)

    try {
      ProjectAgentTools.parsePatch(mapOf("start" to "-1", "replacement" to "x"), pageLength = 20)
      throw AssertionError("expected bad patch")
    } catch (_: IllegalArgumentException) {
    }
    try {
      ProjectAgentTools.parsePatch(mapOf("start" to "25", "replacement" to "x"), pageLength = 20)
      throw AssertionError("expected bad patch")
    } catch (_: IllegalArgumentException) {
    }
    try {
      ProjectAgentTools.parsePatch(mapOf("start" to "5"), pageLength = 20)
      throw AssertionError("expected missing replacement")
    } catch (_: IllegalArgumentException) {
    }
  }
}

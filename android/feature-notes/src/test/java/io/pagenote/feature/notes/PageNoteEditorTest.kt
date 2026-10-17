package io.pagenote.feature.notes

import io.pagenote.core.project.PageNotes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PageNoteEditorTest {
  @Test
  fun replacesOnlySelectedPageAndPreservesEmptyPages() {
    val original = PageNotes.join(listOf("first", "", "third"))

    val result = PageNoteEditor.replacePage(
      latestMarkdown = original,
      page = 2,
      replacement = "second",
      expectedRevision = PageNotes.revision(""),
    )

    assertEquals(listOf("first", "second", "third"), PageNotes.split(result.markdown))
    assertEquals(PageNotes.revision("second"), result.pageRevision)
  }

  @Test
  fun rejectsStaleRevisionForSelectedPage() {
    val latest = PageNotes.join(listOf("first", "changed elsewhere"))

    assertThrows(PageNoteConflictException::class.java) {
      PageNoteEditor.replacePage(
        latestMarkdown = latest,
        page = 2,
        replacement = "my edit",
        expectedRevision = PageNotes.revision("old text"),
      )
    }
  }

  @Test
  fun preservesConcurrentChangesOnOtherPages() {
    val latest = PageNotes.join(listOf("first updated", "second"))

    val result = PageNoteEditor.replacePage(
      latestMarkdown = latest,
      page = 2,
      replacement = "second updated",
      expectedRevision = PageNotes.revision("second"),
    )

    assertEquals(
      listOf("first updated", "second updated"),
      PageNotes.split(result.markdown),
    )
  }

  @Test
  fun extendsDocumentWithEmptyPagesWhenEditingFuturePage() {
    val result = PageNoteEditor.replacePage(
      latestMarkdown = "first",
      page = 3,
      replacement = "third",
      expectedRevision = PageNotes.revision(""),
    )

    assertEquals(listOf("first", "", "third"), PageNotes.split(result.markdown))
  }
}

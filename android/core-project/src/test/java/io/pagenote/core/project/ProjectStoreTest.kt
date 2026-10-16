package io.pagenote.core.project

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class ProjectStoreTest {
  @Test
  fun saveNoteConflictsOnStaleRevision() {
    val dir = Files.createTempDirectory("pagenote-android").toFile()
    val right = java.io.File(dir, "right.md")
    right.writeText("# p1\n\n<!-- pagebreak -->\n\n# p2\n")
    val rev = PageNotes.revision("# p1")
    ProjectStore.saveNote(right, 1, "# p1 edited", rev)
    assertEquals("# p1 edited", ProjectStore.readNotes(right)[1])
    try {
      ProjectStore.saveNote(right, 1, "# stale", rev)
      fail("expected conflict")
    } catch (e: IllegalStateException) {
      // expected
    }
  }
}

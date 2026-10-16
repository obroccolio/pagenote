package io.pagenote.core.project

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteHistoryTest {
  @Test
  fun snapshotRestoreRoundTrip() {
    val history = Files.createTempDirectory("pagenote-history").toFile()
    val file = NoteHistory.snapshot(history, 2, "# v1")
    val entries = NoteHistory.entries(history, 2)
    assertEquals(1, entries.size)
    assertEquals(0, NoteHistory.entries(history, 3).size)
    val restored = NoteHistory.restore(history, file.name, 2, PageNotes.revision("# now"), "# now")
    assertEquals("# v1", restored)
  }

  @Test(expected = IllegalStateException::class)
  fun staleRestoreRejected() {
    val history = Files.createTempDirectory("pagenote-history-stale").toFile()
    val file = NoteHistory.snapshot(history, 1, "# v1")
    NoteHistory.restore(history, file.name, 1, PageNotes.revision("# other"), "# now")
  }
}

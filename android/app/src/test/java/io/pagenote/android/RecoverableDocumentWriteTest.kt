package io.pagenote.android

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class RecoverableDocumentWriteTest {
  @Test
  fun restoresOriginalContentAfterPartialWriteFailure() {
    var document = "original"
    var attempts = 0
    val deletedBackups = mutableListOf<String>()
    val writer = RecoverableDocumentWrite(
      readTarget = { document },
      overwriteTarget = { content ->
        attempts += 1
        if (attempts == 1) {
          document = ""
          throw IOException("provider failed after truncating")
        }
        document = content
      },
      createBackup = { "recovery.md" },
      deleteBackup = deletedBackups::add,
      backupName = { it },
    )

    assertThrows(IOException::class.java) {
      writer.write(updated = "replacement", expectedOriginal = "original")
    }

    assertEquals("original", document)
    assertEquals(listOf("recovery.md"), deletedBackups)
  }

  @Test
  fun retainsRecoveryCopyWhenRollbackAlsoFails() {
    var attempts = 0
    var deletedBackups = 0
    val writer = RecoverableDocumentWrite(
      readTarget = { "original" },
      overwriteTarget = {
        attempts += 1
        throw IOException(if (attempts == 1) "update failed" else "rollback failed")
      },
      createBackup = { "pagenote-recovery-123.md" },
      deleteBackup = { deletedBackups += 1 },
      backupName = { it },
    )

    val error = assertThrows(NoteWriteRecoveryException::class.java) {
      writer.write(updated = "replacement", expectedOriginal = "original")
    }

    assertEquals("pagenote-recovery-123.md", error.recoveryCopyName)
    assertEquals(0, deletedBackups)
    assertTrue(error.cause is IOException)
  }

  @Test
  fun writesNewContentAndRemovesBackupOnSuccess() {
    var document = "original"
    val deletedBackups = mutableListOf<String>()
    val writer = RecoverableDocumentWrite(
      readTarget = { "original" },
      overwriteTarget = { document = it },
      createBackup = { "recovery.md" },
      deleteBackup = deletedBackups::add,
      backupName = { it },
    )

    writer.write(updated = "replacement", expectedOriginal = "original")

    assertEquals("replacement", document)
    assertEquals(listOf("recovery.md"), deletedBackups)
  }

  @Test
  fun abortsIfDocumentChangesWhileRecoveryCopyIsCreated() {
    var reads = 0
    var writes = 0
    val deletedBackups = mutableListOf<String>()
    val writer = RecoverableDocumentWrite(
      readTarget = {
        reads += 1
        if (reads == 1) "original" else "external edit"
      },
      overwriteTarget = { writes += 1 },
      createBackup = { "recovery.md" },
      deleteBackup = deletedBackups::add,
      backupName = { it },
    )

    assertThrows(NoteDocumentConflictException::class.java) {
      writer.write(updated = "replacement", expectedOriginal = "original")
    }

    assertEquals(0, writes)
    assertEquals(listOf("recovery.md"), deletedBackups)
  }
}

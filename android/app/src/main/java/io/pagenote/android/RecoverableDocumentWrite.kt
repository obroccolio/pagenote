package io.pagenote.android

import java.io.IOException

internal class RecoverableDocumentWrite<T>(
  private val readTarget: () -> String,
  private val overwriteTarget: (String) -> Unit,
  private val createBackup: (String) -> T?,
  private val deleteBackup: (T) -> Unit,
  private val backupName: (T) -> String,
) {
  fun write(updated: String, expectedOriginal: String) {
    val original = readTarget()
    if (original != expectedOriginal) throw NoteDocumentConflictException()

    val backup = createBackup(original)
    val latest = try {
      readTarget()
    } catch (error: Exception) {
      backup?.let { runCatching { deleteBackup(it) } }
      throw error
    }
    if (latest != expectedOriginal) {
      backup?.let { runCatching { deleteBackup(it) } }
      throw NoteDocumentConflictException()
    }

    try {
      overwriteTarget(updated)
    } catch (writeFailure: Exception) {
      val rollbackFailure = runCatching { overwriteTarget(original) }.exceptionOrNull()
      if (rollbackFailure == null) {
        backup?.let { runCatching { deleteBackup(it) } }
        throw writeFailure
      }

      writeFailure.addSuppressed(rollbackFailure)
      throw NoteWriteRecoveryException(backup?.let(backupName), writeFailure)
    }
    backup?.let { runCatching { deleteBackup(it) } }
  }
}

class NoteDocumentConflictException : IOException("project note changed during write preparation")

class NoteWriteRecoveryException(
  val recoveryCopyName: String?,
  cause: Throwable,
) : IOException(
  if (recoveryCopyName == null) {
    "note write failed and could not be restored; no recovery copy is available"
  } else {
    "note write failed and could not be restored; recovery copy: $recoveryCopyName"
  },
  cause,
)

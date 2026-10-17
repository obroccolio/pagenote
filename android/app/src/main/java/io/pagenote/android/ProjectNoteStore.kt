package io.pagenote.android

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import java.io.IOException
import java.util.UUID

class ProjectNoteStore(
  private val resolver: ContentResolver,
  private val uri: Uri,
) {
  fun read(): String = resolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
    it.readText()
  } ?: throw IOException("无法读取项目笔记")

  fun supportsWrite(): Boolean {
    val cursor = resolver.query(
      uri,
      arrayOf(DocumentsContract.Document.COLUMN_FLAGS),
      null,
      null,
      null,
    ) ?: return false
    return cursor.use {
      if (!it.moveToFirst()) return@use false
      val index = it.getColumnIndex(DocumentsContract.Document.COLUMN_FLAGS)
      index >= 0 &&
        (it.getInt(index) and DocumentsContract.Document.FLAG_SUPPORTS_WRITE) != 0
    }
  }

  fun write(markdown: String, expectedOriginal: String) {
    RecoverableDocumentWrite(
      readTarget = ::read,
      overwriteTarget = { content -> overwrite(uri, content) },
      createBackup = ::createRecoveryCopy,
      deleteBackup = { copy ->
        if (!DocumentsContract.deleteDocument(resolver, copy.uri)) {
          throw IOException("无法清理笔记恢复副本")
        }
      },
      backupName = RecoveryCopy::displayName,
    ).write(updated = markdown, expectedOriginal = expectedOriginal)
  }

  private fun overwrite(target: Uri, markdown: String) {
    val output = resolver.openOutputStream(target, "wt")
      ?: throw IOException("无法写入项目笔记")
    output.use { it.write(markdown.toByteArray(Charsets.UTF_8)) }
  }

  private fun createRecoveryCopy(markdown: String): RecoveryCopy? {
    val parent = findParentDocument() ?: return null
    if (!runCatching {
        supportsFlag(parent, DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE)
      }.getOrDefault(false)
    ) {
      return null
    }

    val displayName = "pagenote-recovery-${UUID.randomUUID()}.md"
    val copyUri = runCatching {
      DocumentsContract.createDocument(resolver, parent, "text/markdown", displayName)
    }.getOrNull() ?: return null

    if (!runCatching {
        supportsFlag(copyUri, DocumentsContract.Document.FLAG_SUPPORTS_DELETE)
      }.getOrDefault(false)
    ) {
      removeRecoveryCopyOrThrow(copyUri, displayName)
      return null
    }

    return try {
      overwrite(copyUri, markdown)
      RecoveryCopy(copyUri, displayName)
    } catch (error: Exception) {
      removeRecoveryCopyOrThrow(copyUri, displayName, error)
      null
    }
  }

  private fun findParentDocument(): Uri? {
    return try {
      val path = DocumentsContract.findDocumentPath(resolver, uri)?.path ?: return null
      if (path.size < 2) return null
      DocumentsContract.buildDocumentUriUsingTree(uri, path[path.lastIndex - 1])
    } catch (_: Exception) {
      null
    }
  }

  private fun supportsFlag(document: Uri, flag: Int): Boolean {
    val cursor = resolver.query(
      document,
      arrayOf(DocumentsContract.Document.COLUMN_FLAGS),
      null,
      null,
      null,
    ) ?: return false
    return cursor.use {
      if (!it.moveToFirst()) return@use false
      val index = it.getColumnIndex(DocumentsContract.Document.COLUMN_FLAGS)
      index >= 0 && (it.getInt(index) and flag) != 0
    }
  }

  private fun removeRecoveryCopyOrThrow(
    copyUri: Uri,
    displayName: String,
    cause: Exception? = null,
  ) {
    val removed = runCatching { DocumentsContract.deleteDocument(resolver, copyUri) }
      .getOrDefault(false)
    if (!removed) throw IOException("无法清理未完成的恢复副本：$displayName", cause)
  }

  private data class RecoveryCopy(
    val uri: Uri,
    val displayName: String,
  )
}

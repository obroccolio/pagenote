package io.pagenote.android

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import java.io.IOException

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

  fun write(markdown: String) {
    val output = resolver.openOutputStream(uri, "wt")
      ?: throw IOException("无法写入项目笔记")
    output.use { it.write(markdown.toByteArray(Charsets.UTF_8)) }
  }
}

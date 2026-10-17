package io.pagenote.android

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import io.pagenote.core.project.ProjectManifest
import io.pagenote.core.project.ProjectStore
import java.io.IOException

class ProjectCreator(private val resolver: ContentResolver) {
  fun create(parentTreeUri: Uri, name: String): Uri {
    val manifest = ProjectStore.newManifest(name)
    val parentId = DocumentsContract.getTreeDocumentId(parentTreeUri)
    if (childExists(parentTreeUri, parentId, name)) {
      throw IOException("同名项目已存在：$name")
    }
    var projectUri: Uri? = null
    var srcUri: Uri? = null
    try {
      projectUri = DocumentsContract.createDocument(
        resolver, buildChildUri(parentTreeUri, parentId),
        DocumentsContract.Document.MIME_TYPE_DIR, name,
      ) ?: throw IOException("无法创建项目目录：$name")
      srcUri = DocumentsContract.createDocument(
        resolver, projectUri, DocumentsContract.Document.MIME_TYPE_DIR, "src",
      ) ?: throw IOException("无法创建项目 src 目录")
      DocumentsContract.createDocument(
        resolver, srcUri, DocumentsContract.Document.MIME_TYPE_DIR, "assets",
      ) ?: throw IOException("无法创建项目 assets 目录")
      writeText(projectUri, "src", "left.md", "# ${manifest.name}\n", "text/markdown")
      writeText(projectUri, "src", "right.md", "", "text/markdown")
      val manifestUri = DocumentsContract.createDocument(
        resolver, projectUri, "application/json", "project.pagenote",
      ) ?: throw IOException("无法创建 project.pagenote")
      resolver.openOutputStream(manifestUri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use {
        it.write(manifestJson(manifest))
      } ?: throw IOException("无法写入 project.pagenote")
      return projectUri
    } catch (error: Exception) {
      runCatching {
        if (srcUri != null) DocumentsContract.deleteDocument(resolver, srcUri)
        else if (projectUri != null) DocumentsContract.deleteDocument(resolver, projectUri)
      }
      throw error
    }
  }

  private fun childExists(treeUri: Uri, parentId: String, name: String): Boolean {
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
    val cursor = resolver.query(
      childrenUri,
      arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null,
    ) ?: throw IOException("无法列出所选目录")
    cursor.use {
      val nameColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
      while (it.moveToNext()) {
        if (it.getString(nameColumn) == name) return true
      }
    }
    return false
  }

  private fun writeText(projectUri: Uri, dir: String, file: String, text: String, mime: String) {
    val dirUri = findChild(projectUri, dir) ?: throw IOException("项目目录结构异常：$dir")
    val fileUri = DocumentsContract.createDocument(resolver, dirUri, mime, file)
      ?: throw IOException("无法创建 $dir/$file")
    resolver.openOutputStream(fileUri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use {
      it.write(text)
    } ?: throw IOException("无法写入 $dir/$file")
  }

  private fun findChild(parent: Uri, name: String): Uri? {
    val parentId = DocumentsContract.getDocumentId(parent)
    val treeUri = parent
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
    val cursor = resolver.query(
      childrenUri,
      arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
      null, null, null,
    ) ?: return null
    cursor.use {
      val idColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
      val nameColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
      while (it.moveToNext()) {
        if (it.getString(nameColumn) == name) {
          return DocumentsContract.buildDocumentUriUsingTree(treeUri, it.getString(idColumn))
        }
      }
    }
    return null
  }

  private fun buildChildUri(treeUri: Uri, parentId: String): Uri =
    DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)

  private fun manifestJson(m: ProjectManifest): String {
    val created = m.created ?: ""
    return "{\"format\":\"" + m.format + "\",\"version\":" + m.version +
      ",\"id\":\"" + m.id + "\",\"name\":\"" + m.name.replace("\"", "\\\"") +
      "\",\"created\":\"" + created + "\",\"left\":\"" + m.left +
      "\",\"right\":\"" + m.right + "\"}"
  }
}

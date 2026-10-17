package io.pagenote.android

import android.content.ContentResolver
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.provider.DocumentsContract
import io.noties.markwon.image.ImageItem
import io.noties.markwon.image.SchemeHandler

class NoteAssetSchemeHandler(
  private val resolver: ContentResolver,
  private val sourceDirectoryUri: Uri?,
) : SchemeHandler() {
  override fun handle(raw: String, uri: Uri): ImageItem {
    val name = uri.schemeSpecificPart?.substringAfterLast('/')
      ?.takeIf { it.matches(Regex("[a-f0-9-]+\\.[a-z0-9]+", RegexOption.IGNORE_CASE)) }
      ?: return ImageItem.withResult(ColorDrawable(Color.TRANSPARENT))
    val base = sourceDirectoryUri ?: return ImageItem.withResult(ColorDrawable(Color.TRANSPARENT))
    return try {
      val assetsUri = findChild(base, "assets") ?: return ImageItem.withResult(ColorDrawable(Color.TRANSPARENT))
      val fileUri = findChild(assetsUri, name) ?: return ImageItem.withResult(ColorDrawable(Color.TRANSPARENT))
      val stream = resolver.openInputStream(fileUri)
        ?: return ImageItem.withResult(ColorDrawable(Color.TRANSPARENT))
      ImageItem.withDecodingNeeded(uri.toString(), stream)
    } catch (_: Exception) {
      ImageItem.withResult(ColorDrawable(Color.TRANSPARENT))
    }
  }

  override fun supportedSchemes(): Collection<String> =
    listOf(NotePreviewRewrite.DEFAULT_ASSET_SCHEME)

  private fun findChild(parent: Uri, name: String): Uri? {
    val parentId = DocumentsContract.getDocumentId(parent)
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(parent, parentId)
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
          return DocumentsContract.buildDocumentUriUsingTree(parent, it.getString(idColumn))
        }
      }
    }
    return null
  }
}

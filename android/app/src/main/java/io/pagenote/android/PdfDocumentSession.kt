package io.pagenote.android

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import io.pagenote.core.reader.PdfPagePolicy
import java.io.Closeable

class PdfDocumentSession private constructor(
  private val descriptor: ParcelFileDescriptor,
  private val renderer: PdfRenderer,
) : Closeable {
  val pageCount: Int get() = renderer.pageCount

  @Synchronized
  fun render(pageIndex: Int): Bitmap {
    check(pageIndex in 0 until renderer.pageCount) { "PDF page out of range" }
    val page = renderer.openPage(pageIndex)
    try {
      val (width, height) = PdfPagePolicy.renderedSize(page.width.toDouble(), page.height.toDouble())
      val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
      try {
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        return bitmap
      } catch (error: Exception) {
        bitmap.recycle()
        throw error
      }
    } finally {
      page.close()
    }
  }

  @Synchronized
  override fun close() {
    renderer.close()
    descriptor.close()
  }

  companion object {
    fun open(resolver: ContentResolver, uri: Uri): PdfDocumentSession {
      val descriptor = resolver.openFileDescriptor(uri, "r")
        ?: throw IllegalStateException("无法打开 PDF 文件")
      try {
        val renderer = PdfRenderer(descriptor)
        if (renderer.pageCount < 1) {
          renderer.close()
          throw IllegalArgumentException("PDF 中没有页面")
        }
        return PdfDocumentSession(descriptor, renderer)
      } catch (error: Exception) {
        descriptor.close()
        throw error
      }
    }
  }
}

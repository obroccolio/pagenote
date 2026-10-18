package io.pagenote.android

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(AndroidJUnit4::class)
class PdfDocumentSessionTest {
  @Test
  fun closeIsIdempotentForAnOpenedPdfRenderer() = runBlocking {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val source = File(context.cacheDir, "pdf-close-once-test.pdf")
    source.writeBytes(minimalPdf())
    try {
      val session = PdfDocumentSession.open(context, Uri.fromFile(source), context.cacheDir)
      assertEquals(1, session.pageCount)

      session.close()
      session.close()
    } finally {
      source.delete()
    }
  }

  private fun minimalPdf(): ByteArray {
    val output = ByteArrayOutputStream()
    val offsets = mutableListOf(0)
    output.write("%PDF-1.4\n".toByteArray(Charsets.US_ASCII))
    val objects = listOf(
      "<< /Type /Catalog /Pages 2 0 R >>",
      "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
      "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << >> /Contents 4 0 R >>",
      "<< /Length 0 >>\nstream\n\nendstream",
    )
    objects.forEachIndexed { index, body ->
      offsets += output.size()
      output.write("${index + 1} 0 obj\n$body\nendobj\n".toByteArray(Charsets.US_ASCII))
    }
    val xrefOffset = output.size()
    output.write("xref\n0 ${offsets.size}\n0000000000 65535 f \n".toByteArray(Charsets.US_ASCII))
    offsets.drop(1).forEach { offset ->
      output.write("%010d 00000 n \n".format(offset).toByteArray(Charsets.US_ASCII))
    }
    output.write(
      "trailer\n<< /Size ${offsets.size} /Root 1 0 R >>\nstartxref\n$xrefOffset\n%%EOF\n"
        .toByteArray(Charsets.US_ASCII),
    )
    return output.toByteArray()
  }
}

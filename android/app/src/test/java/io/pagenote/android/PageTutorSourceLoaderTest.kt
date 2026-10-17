package io.pagenote.android

import io.pagenote.core.reader.PdfTextExtractionResult
import io.pagenote.core.reader.PdfTextExtractionStatus
import io.pagenote.feature.ocr.OcrBlock
import io.pagenote.feature.ocr.OcrResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageTutorSourceLoaderTest {
  @Test
  fun prefersNativeTextWithoutRunningOcr() = runBlocking {
    val loader = PageTutorSourceLoader(
      nativeText = { availableText("native page text") },
      ocrText = { error("OCR must not run when native text is available") },
    )

    val source = loader.loadCurrent(2)

    assertEquals("native page text", source.text)
    assertFalse(source.usedOcr)
    assertTrue(source.warnings.isEmpty())
  }

  @Test
  fun fallsBackToOcrForUnavailablePdfText() = runBlocking {
    var ocrCalls = 0
    val loader = PageTutorSourceLoader(
      nativeText = { PdfTextExtractionResult.unavailableForOcr() },
      ocrText = {
        ocrCalls += 1
        recognizedText("recognized page")
      },
    )

    val source = loader.loadCurrent(0)

    assertEquals(1, ocrCalls)
    assertEquals("recognized page", source.text)
    assertTrue(source.usedOcr)
  }

  @Test
  fun fallsBackToOcrWhenNativeTextIsBlank() = runBlocking {
    val loader = PageTutorSourceLoader(
      nativeText = { availableText(" \n ") },
      ocrText = { recognizedText("recognized page") },
    )

    assertEquals("recognized page", loader.loadCurrent(0).text)
  }

  @Test
  fun rejectsFailedOcrWithoutReturningSource() = runBlocking {
    val loader = PageTutorSourceLoader(
      nativeText = { PdfTextExtractionResult.unavailableForOcr() },
      ocrText = { OcrResult(engine = "test", status = "failed", warnings = listOf("model failed")) },
    )

    val error = runCatching { loader.loadCurrent(0) }.exceptionOrNull()
    assertTrue(error is PageTutorSourceException)
    assertTrue(error?.message.orEmpty().contains("model failed"))
  }

  @Test
  fun rejectsEmptyOcrText() = runBlocking {
    val loader = PageTutorSourceLoader(
      nativeText = { PdfTextExtractionResult.unavailableForOcr() },
      ocrText = { OcrResult(engine = "test", status = "ok") },
    )

    val error = runCatching { loader.loadCurrent(0) }.exceptionOrNull()
    assertTrue(error is PageTutorSourceException)
  }

  @Test
  fun retainsOcrWarningsAndAddsLowConfidenceReminder() = runBlocking {
    val loader = PageTutorSourceLoader(
      nativeText = { PdfTextExtractionResult.unavailableForOcr() },
      ocrText = {
        OcrResult(
          engine = "test",
          status = "partial",
          blocks = listOf(OcrBlock("recognized", 0.3, 10.0, 10.0, 200.0, 50.0)),
          warnings = listOf("one OCR region was omitted"),
        )
      },
    )

    val source = loader.loadCurrent(0)

    assertTrue(source.warnings.contains("one OCR region was omitted"))
    assertTrue(source.warnings.any { it.contains("verify", ignoreCase = true) })
  }

  @Test
  fun neighborLoadingNeverRunsOcr() = runBlocking {
    val loader = PageTutorSourceLoader(
      nativeText = { availableText("neighbor text") },
      ocrText = { error("neighbor text must not trigger OCR") },
    )

    assertEquals("neighbor text", loader.loadNeighbor(1)?.text)
  }

  private fun availableText(text: String) = PdfTextExtractionResult(
    status = PdfTextExtractionStatus.AVAILABLE,
    fullText = text,
    blocks = emptyList(),
  )

  private fun recognizedText(text: String) = OcrResult(
    engine = "test",
    status = "ok",
    blocks = listOf(OcrBlock(text, 0.95, 10.0, 10.0, 200.0, 50.0)),
  )
}

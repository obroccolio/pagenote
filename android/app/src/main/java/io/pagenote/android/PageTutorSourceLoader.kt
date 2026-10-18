package io.pagenote.android

import io.pagenote.core.reader.PdfTextExtractionResult
import io.pagenote.core.reader.PdfTextExtractionStatus
import io.pagenote.feature.ocr.OcrResult
import io.pagenote.feature.ocr.OcrResultPolicy
import io.pagenote.feature.ocr.OcrTrigger

data class PageTutorSourceText(
  val text: String,
  val usedOcr: Boolean,
  val warnings: List<String> = emptyList(),
)

class PageTutorSourceException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

class PageTutorSourceLoader(
  private val nativeText: suspend (Int) -> PdfTextExtractionResult,
  private val ocrText: suspend (Int) -> OcrResult,
  private val hasBitmap: (Int) -> Boolean = { false },
) {
  suspend fun loadCurrent(pageIndex: Int): PageTutorSourceText {
    require(pageIndex >= 0) { "page index must not be negative" }
    val native = nativeText(pageIndex)
    return loadFromNative(pageIndex, native)
  }

  suspend fun loadNeighbor(pageIndex: Int): PageTutorSourceText? {
    require(pageIndex >= 0) { "page index must not be negative" }
    val native = nativeText(pageIndex)
    if (native.status != PdfTextExtractionStatus.AVAILABLE || native.fullText.isBlank()) return null
    return PageTutorSourceText(text = native.fullText, usedOcr = false)
  }

  suspend fun loadNeighborForAgent(pageIndex: Int): PageTutorSourceText {
    require(pageIndex >= 0) { "page index must not be negative" }
    val native = nativeText(pageIndex)
    return loadFromNative(pageIndex, native)
  }

  private suspend fun loadFromNative(
    pageIndex: Int,
    native: PdfTextExtractionResult,
  ): PageTutorSourceText {
    val shouldOcr = OcrTrigger.shouldRun(
      nativeAvailable = native.status == PdfTextExtractionStatus.AVAILABLE,
      nativeText = native.fullText,
      hasBitmap = hasBitmap(pageIndex),
    )
    if (!shouldOcr) return PageTutorSourceText(text = native.fullText, usedOcr = false)

    val result = ocrText(pageIndex)
    try {
      OcrResultPolicy.validate(result)
    } catch (error: IllegalArgumentException) {
      throw PageTutorSourceException("Offline OCR returned an invalid result.", error)
    }
    val text = OcrResultPolicy.textOf(result).trim()
    if (result.status !in setOf("ok", "partial") || text.isEmpty()) {
      val detail = result.warnings.filter(String::isNotBlank).joinToString(" ")
        .ifBlank { "No usable text was recognized from this page." }
      throw PageTutorSourceException("Offline OCR could not prepare page text. $detail")
    }

    val warnings = result.warnings.filter(String::isNotBlank).toMutableList()
    if (OcrResultPolicy.needsVisualReview(result)) {
      warnings += "OCR text may be inaccurate. Verify it against the original PDF page."
    }
    return PageTutorSourceText(text = text, usedOcr = true, warnings = warnings.distinct())
  }
}

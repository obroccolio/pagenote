package io.pagenote.feature.ocr

object OcrPolicy {
  const val SPARSE_TEXT_CHARS = 200
  const val TIMEOUT_MS = 90_000L

  fun shouldSupplementOcr(nativeNonBlankChars: Int, rasterImageCount: Int, suspiciousGlyphs: Boolean): Boolean {
    if (suspiciousGlyphs) return true
    if (rasterImageCount > 0) return true
    return nativeNonBlankChars < SPARSE_TEXT_CHARS
  }

  fun mergedText(nativeText: String, ocrText: String): String {
    if (nativeText.isNotEmpty() && ocrText.isNotEmpty()) {
      return "[PDF/Markdown 原文]\n$nativeText\n\n[OCR 补充，需核对原图]\n$ocrText"
    }
    return nativeText.ifEmpty { ocrText }
  }
}

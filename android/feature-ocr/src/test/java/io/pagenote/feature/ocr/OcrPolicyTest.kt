package io.pagenote.feature.ocr

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrPolicyTest {
  @Test
  fun sparseTextNeedsOcr() {
    assertTrue(OcrPolicy.shouldSupplementOcr(10, 0, false))
  }

  @Test
  fun richTextSkipsOcr() {
    assertFalse(OcrPolicy.shouldSupplementOcr(500, 0, false))
  }

  @Test
  fun rasterImageNeedsOcr() {
    assertTrue(OcrPolicy.shouldSupplementOcr(5000, 2, false))
  }

  @Test
  fun mergedTextKeepsBothChannels() {
    val merged = OcrPolicy.mergedText("native", "ocr")
    assertTrue(merged.contains("native"))
    assertTrue(merged.contains("ocr"))
  }
}

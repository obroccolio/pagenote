package io.pagenote.core.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfPagePolicyTest {
  @Test
  fun normalPageKeeps144DpiScale() {
    assertEquals(2.0, PdfPagePolicy.renderScale(612.0, 792.0), 0.0001)
  }

  @Test
  fun hugePageDownsamplesUnderPixelCap() {
    val scale = PdfPagePolicy.renderScale(10000.0, 10000.0)
    assertTrue(scale < 2.0)
    val (width, height) = PdfPagePolicy.renderedSize(10000.0, 10000.0)
    assertTrue(width * height <= PdfPagePolicy.MAX_PIXELS)
  }

  @Test
  fun fullPageRegionMapsToFullPixels() {
    val rect = PdfPagePolicy.regionPixels(1200, 1600, PageRegion(0.0, 0.0, 1000.0, 1000.0))
    assertEquals(0, rect[0])
    assertEquals(0, rect[1])
    assertEquals(1200, rect[2])
    assertEquals(1600, rect[3])
  }
}

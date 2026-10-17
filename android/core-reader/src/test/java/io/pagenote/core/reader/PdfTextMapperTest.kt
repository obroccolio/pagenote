package io.pagenote.core.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfTextMapperTest {
  @Test
  fun mapsMultiLineBoundsToOneBlockUsingIndependentPageDimensions() {
    val result = PdfTextMapper.extract(
      pageWidthPt = 200.0,
      pageHeightPt = 400.0,
      contents = listOf(
        PdfTextContent(
          text = "Two lines",
          bounds = listOf(
            PdfTextRect(left = 20.0, top = 40.0, right = 100.0, bottom = 80.0),
            PdfTextRect(left = 20.0, top = 100.0, right = 180.0, bottom = 140.0),
          ),
        ),
      ),
    )

    assertEquals(PdfTextExtractionStatus.AVAILABLE, result.status)
    assertEquals("Two lines", result.fullText)
    assertEquals(1, result.blocks.size)
    with(result.blocks.single()) {
      assertEquals("Two lines", text)
      assertEquals(100.0, x, 0.0001)
      assertEquals(100.0, y, 0.0001)
      assertEquals(800.0, width, 0.0001)
      assertEquals(250.0, height, 0.0001)
    }
  }

  @Test
  fun clipsPartiallyVisibleBoundsAndOmitsInvalidOrFullyOutsideBounds() {
    val result = PdfTextMapper.extract(
      pageWidthPt = 200.0,
      pageHeightPt = 100.0,
      contents = listOf(
        PdfTextContent("edge", listOf(PdfTextRect(-20.0, -40.0, 50.0, 40.0))),
        PdfTextContent("outside", listOf(PdfTextRect(210.0, 10.0, 220.0, 20.0))),
        PdfTextContent("non-finite", listOf(PdfTextRect(Double.NaN, 0.0, 10.0, 10.0))),
        PdfTextContent("inverted", listOf(PdfTextRect(60.0, 10.0, 40.0, 20.0))),
        PdfTextContent("zero area", listOf(PdfTextRect(1.0, 1.0, 1.0, 2.0))),
      ),
    )

    assertEquals("edge\noutside\nnon-finite\ninverted\nzero area", result.fullText)
    assertEquals(1, result.blocks.size)
    with(result.blocks.single()) {
      assertEquals("edge", text)
      assertEquals(0.0, x, 0.0001)
      assertEquals(0.0, y, 0.0001)
      assertEquals(250.0, width, 0.0001)
      assertEquals(400.0, height, 0.0001)
    }
  }

  @Test
  fun keepsUnboundedTextInFullTextButEmitsNoBlockForItOrEmptyText() {
    val result = PdfTextMapper.extract(
      pageWidthPt = 612.0,
      pageHeightPt = 792.0,
      contents = listOf(
        PdfTextContent("", listOf(PdfTextRect(10.0, 10.0, 30.0, 20.0))),
        PdfTextContent("Unbounded text", emptyList()),
      ),
    )

    assertEquals(PdfTextExtractionStatus.AVAILABLE, result.status)
    assertEquals("Unbounded text", result.fullText)
    assertTrue(result.blocks.isEmpty())
  }

  @Test
  fun unsupportedAndroidVersionsRequestOfflineOcrFallback() {
    assertEquals(
      PdfTextExtractionStatus.UNAVAILABLE_FALLBACK_TO_OCR,
      PdfTextExtractionPolicy.statusForApi(29),
    )
    assertEquals(
      PdfTextExtractionStatus.UNAVAILABLE_FALLBACK_TO_OCR,
      PdfTextExtractionPolicy.statusForApi(34),
    )
    assertEquals(PdfTextExtractionStatus.AVAILABLE, PdfTextExtractionPolicy.statusForApi(35))
    assertEquals(PdfTextExtractionStatus.AVAILABLE, PdfTextExtractionPolicy.statusForApi(36))
  }
}

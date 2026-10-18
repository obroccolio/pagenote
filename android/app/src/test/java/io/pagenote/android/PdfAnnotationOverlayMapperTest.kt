package io.pagenote.android

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class PdfAnnotationOverlayMapperTest {
  private val json = Json

  @Test
  fun parsesRectangleAndTextInSourceOrder() {
    val items = PdfAnnotationOverlayItem.fromPage(json.parseToJsonElement("""
      [
        {"type":"rect","color":"#12abef","width":3,"points":[[300,1200],[100,1400]],"text":"region"},
        {"type":"text","color":"#010203","width":4,"points":[[50,1300]],"text":"first line\nsecond line"}
      ]
    """))

    assertEquals(2, items.size)
    assertEquals("rect", items[0].type)
    assertEquals(listOf(100.0 to 1200.0, 300.0 to 1400.0), items[0].points)
    assertEquals("text", items[1].type)
    assertEquals("first line\nsecond line", items[1].text)
  }

  @Test
  fun skipsMalformedAndUnsupportedItems() {
    val items = PdfAnnotationOverlayItem.fromPage(json.parseToJsonElement("""
      [
        {"type":"rect","color":"red","width":3,"points":[[0,0],[100,100]]},
        {"type":"text","color":"#ffffff","width":3,"points":[[0,0]],"text":""},
        {"type":"ellipse","color":"#ffffff","width":3,"points":[[0,0],[100,100]]},
        {"type":"rect","color":"#ffffff","width":3,"points":[[0,0],[100,100]]}
      ]
    """))

    assertEquals(1, items.size)
    assertEquals("rect", items.single().type)
  }
}

package io.pagenote.android

import io.pagenote.feature.pen.PenPoint
import io.pagenote.feature.pen.PenStroke
import org.junit.Assert.assertEquals
import org.junit.Test

class AnnotationPageMergerTest {
  @Test
  fun replacingFreehandItemsRetainsOtherAnnotationTypesAndMetadata() {
    val rectangle = mapOf("type" to "rect", "color" to "#112233", "custom" to "kept")
    val existing = listOf(
      rectangle,
      mapOf("type" to "pen", "color" to "#ff0000"),
      mapOf("type" to "highlight", "color" to "#ffff00"),
    )
    val replacement = PenStroke(
      type = "pen",
      color = "#00aa88",
      width = 3.0,
      points = listOf(PenPoint(10f, 20f, 0.7f)),
    )

    val merged = AnnotationPageMerger.replaceFreehandItems(existing, listOf(replacement))

    assertEquals(rectangle, merged.first())
    assertEquals(2, merged.size)
    assertEquals("pen", merged.last()["type"])
    val pressures = merged.last()["pressures"] as List<*>
    assertEquals(0.7, (pressures.single() as Number).toDouble(), 0.0001)
  }

  @Test
  fun emptyReplacementRemovesOnlyPenAndHighlighterItems() {
    val existing = listOf(
      mapOf("type" to "image", "url" to "/attachments/asset.png"),
      mapOf("type" to "rect", "points" to listOf(listOf(1, 2))),
      mapOf("type" to "pen"),
    )

    val merged = AnnotationPageMerger.replaceFreehandItems(existing, emptyList())

    assertEquals(existing.take(2), merged)
  }

  @Test
  fun replacingPagePreservesUnknownFieldsAndOtherPagesAndSides() {
    val pageTwo = listOf(mapOf("type" to "pen", "custom" to "preserved"))
    val notes = mapOf("1" to listOf(mapOf("type" to "highlight")))
    val root = mapOf(
      "version" to 3,
      "document" to mapOf("2" to pageTwo),
      "note" to notes,
      "customRoot" to mapOf("enabled" to true),
    )
    val replacement = PenStroke(
      type = "pen",
      color = "#112233",
      width = 2.0,
      points = listOf(PenPoint(1f, 2f, 1f)),
    )

    val updated = AnnotationPageMerger.replacePage(root, "document", 1, listOf(replacement))

    assertEquals(3, updated["version"])
    assertEquals(root["customRoot"], updated["customRoot"])
    assertEquals(pageTwo, (updated["document"] as Map<*, *>)["2"])
    assertEquals(notes, updated["note"])
    assertEquals(listOf(replacement.toAnnotationsItem()),
      (updated["document"] as Map<*, *>)["1"])
  }
}

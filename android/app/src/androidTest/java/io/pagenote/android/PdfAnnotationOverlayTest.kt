package io.pagenote.android

import android.os.Bundle
import android.provider.DocumentsContract
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PdfAnnotationOverlayTest {
  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun rectangleUsesPageWidthScaleForVerticalCoordinates() {
    composeRule.setContent {
      Box(Modifier.size(width = 100.dp, height = 200.dp)) {
        PdfAnnotationOverlay(
          items = listOf(
            PdfAnnotationOverlayItem(
              type = "rect",
              points = listOf(100.0 to 100.0, 200.0 to 300.0),
              color = "#00ff00",
              width = 20.0,
              text = null,
            ),
          ),
        )
      }
    }

    val pixels = composeRule.onNodeWithTag("pdf_annotation_overlay").captureToImage().toPixelMap()
    val x = (pixels.width * 0.15f).toInt()
    val expectedY = (pixels.height * 0.10f).toInt()
    val actualY = (pixels.height * 0.05f).toInt()
    val green = Color(0xFF00FF00)
    assertTrue(
      "expected the rectangle's top edge at y scaled from page width",
      (actualY - 3..actualY + 3).any { y -> pixels[x, y] == green },
    )
    assertTrue(
      "the height-scaled alternative must not contain that edge",
      (expectedY - 3..expectedY + 3).none { y -> pixels[x, y] == green },
    )
  }

  @Test
  fun reloadFindsAnnotationsFileCreatedAfterProjectWasOpened() {
    val context = InstrumentationRegistry.getInstrumentation().context
    val resolver = context.contentResolver
    val treeUri = DocumentsContract.buildTreeDocumentUri(
      ExistingProjectSafTestProvider.AUTHORITY,
      ExistingProjectSafTestProvider.ROOT_ID,
    )
    resolver.call(
      ExistingProjectSafTestProvider.AUTHORITY,
      ExistingProjectSafTestProvider.METHOD_INSTALL,
      null,
      Bundle().apply {
        putByteArray(ExistingProjectSafTestProvider.EXTRA_MANIFEST, "{}".toByteArray())
        putByteArray(ExistingProjectSafTestProvider.EXTRA_PDF, byteArrayOf())
        putByteArray(ExistingProjectSafTestProvider.EXTRA_NOTES, byteArrayOf())
        putBoolean(ExistingProjectSafTestProvider.EXTRA_OMIT_REVIEWS, true)
      },
    )
    val sourceDirectory = DocumentsContract.buildDocumentUriUsingTree(
      treeUri,
      ExistingProjectSafTestProvider.SRC_ID,
    )
    val expected = JsonArray(listOf(Json.parseToJsonElement(
      """{"type":"rect","color":"#112233","width":3,"points":[[0,0],[100,100]]}""",
    )))
    ProjectAnnotationStore(resolver, sourceDirectory, null)
      .writePage("document", 1, expected)

    val reloaded = ProjectAnnotationStore(resolver, sourceDirectory, null)
      .readPage("document", 1)

    assertEquals(expected, reloaded)
  }
}

package io.pagenote.android

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

data class PdfAnnotationOverlayItem(
  val type: String,
  val points: List<Pair<Double, Double>>,
  val color: String,
  val width: Double,
  val text: String?,
) {
  companion object {
    private val colorPattern = Regex("^#[a-fA-F0-9]{6}$")

    fun fromPage(page: JsonElement): List<PdfAnnotationOverlayItem> {
      val items = page as? JsonArray ?: return emptyList()
      return items.mapNotNull(::parseItem)
    }

    private fun parseItem(element: JsonElement): PdfAnnotationOverlayItem? {
      val item = element as? JsonObject ?: return null
      val type = item["type"]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
        ?: return null
      if (type != "rect" && type != "text") return null
      val color = item["color"]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
        ?.takeIf(colorPattern::matches) ?: return null
      val width = item["width"]?.let { runCatching { it.jsonPrimitive.doubleOrNull }.getOrNull() }
        ?.takeIf { it.isFinite() && it >= 0.1 && it <= 100.0 } ?: return null
      val rawPoints = item["points"]?.let { runCatching { it.jsonArray }.getOrNull() }
        ?: return null
      val pointCount = if (type == "rect") 2 else 1
      if (rawPoints.size < pointCount) return null
      val points = rawPoints.take(pointCount).map { point ->
        val coordinates = runCatching { point.jsonArray }.getOrNull() ?: return null
        if (coordinates.size < 2) return null
        val x = runCatching { coordinates[0].jsonPrimitive.doubleOrNull }.getOrNull()
          ?.takeIf(Double::isFinite) ?: return null
        val y = runCatching { coordinates[1].jsonPrimitive.doubleOrNull }.getOrNull()
          ?.takeIf(Double::isFinite) ?: return null
        x to y
      }
      val normalizedPoints = if (type == "rect") {
        val first = points[0]
        val second = points[1]
        val left = minOf(first.first, second.first)
        val top = minOf(first.second, second.second)
        val right = maxOf(first.first, second.first)
        val bottom = maxOf(first.second, second.second)
        if (left == right || top == bottom) return null
        listOf(left to top, right to bottom)
      } else {
        points
      }
      val text = item["text"]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
      if (type == "text" && text.isNullOrBlank()) return null
      return PdfAnnotationOverlayItem(type, normalizedPoints, color, width, text)
    }
  }
}

@Composable
internal fun PdfAnnotationOverlay(
  items: List<PdfAnnotationOverlayItem>,
  modifier: Modifier = Modifier,
) {
  Canvas(modifier.fillMaxSize().testTag("pdf_annotation_overlay")) {
    val pageWidthScale = size.width / 1000f
    items.forEach { item ->
      val color = Color(android.graphics.Color.parseColor(item.color))
      val strokeWidth = (item.width.toFloat() * pageWidthScale).coerceAtLeast(1f)
      when (item.type) {
        "rect" -> {
          val first = item.points[0]
          val second = item.points[1]
          drawRect(
            color = color,
            topLeft = Offset(
              (first.first * pageWidthScale).toFloat(),
              (first.second * pageWidthScale).toFloat(),
            ),
            size = Size(
              ((second.first - first.first) * pageWidthScale).toFloat(),
              ((second.second - first.second) * pageWidthScale).toFloat(),
            ),
            style = Stroke(width = strokeWidth),
          )
        }
        "text" -> {
          val point = item.points.first()
          val textSize = maxOf(20.0, item.width * 5.0).toFloat() * pageWidthScale
          val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = android.graphics.Color.parseColor(item.color)
            this.textSize = textSize
            style = Paint.Style.FILL
          }
          item.text.orEmpty().split('\n').forEachIndexed { index, line ->
            drawContext.canvas.nativeCanvas.drawText(
              line,
              (point.first * pageWidthScale).toFloat(),
              (point.second * pageWidthScale + index * textSize * 1.4f).toFloat(),
              paint,
            )
          }
        }
      }
    }
  }
}

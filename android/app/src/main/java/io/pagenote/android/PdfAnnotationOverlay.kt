package io.pagenote.android

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

data class PdfAnnotationOverlayItem(
  val type: String,
  val points: List<Pair<Double, Double>>,
  val color: String,
  val width: Double,
  val text: String?,
) {
  companion object {
    private val colorPattern = Regex("^#[a-fA-F0-9]{6}$")
    private val vectorTypes = setOf("line", "arrow", "ellipse")

    fun fromPage(page: JsonElement): List<PdfAnnotationOverlayItem> {
      val items = page as? JsonArray ?: return emptyList()
      return items.mapNotNull(::parseItem)
    }

    private fun parseItem(element: JsonElement): PdfAnnotationOverlayItem? {
      val item = element as? JsonObject ?: return null
      val type = item["type"]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
        ?: return null
      if (type != "rect" && type != "text" && type !in vectorTypes) return null
      val color = item["color"]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
        ?.takeIf(colorPattern::matches) ?: return null
      val width = item["width"]?.let { runCatching { it.jsonPrimitive.doubleOrNull }.getOrNull() }
        ?.takeIf { it.isFinite() && it >= 0.1 && it <= 100.0 } ?: return null
      val rawPoints = item["points"]?.let { runCatching { it.jsonArray }.getOrNull() }
        ?: return null
      val minimumPointCount = if (type == "text" || type in vectorTypes) 1 else 2
      val pointCount = if (type == "text") 1 else 2
      if (rawPoints.size < minimumPointCount) return null
      val points = rawPoints.take(pointCount).map { point ->
        val coordinates = runCatching { point.jsonArray }.getOrNull() ?: return null
        if (coordinates.size < 2) return null
        val x = runCatching { coordinates[0].jsonPrimitive.doubleOrNull }.getOrNull()
          ?.takeIf(Double::isFinite) ?: return null
        val y = runCatching { coordinates[1].jsonPrimitive.doubleOrNull }.getOrNull()
          ?.takeIf(Double::isFinite) ?: return null
        if (abs(x) > MAX_COORDINATE || abs(y) > MAX_COORDINATE) return null
        x to y
      }
      val completePoints = if (type in vectorTypes && points.size == 1) {
        listOf(points[0], points[0])
      } else {
        points
      }
      val normalizedPoints = if (type == "rect" || type == "ellipse") {
        val first = completePoints[0]
        val second = completePoints[1]
        val left = minOf(first.first, second.first)
        val top = minOf(first.second, second.second)
        val right = maxOf(first.first, second.first)
        val bottom = maxOf(first.second, second.second)
        if (type == "rect" && (left == right || top == bottom)) return null
        listOf(left to top, right to bottom)
      } else {
        completePoints
      }
      val text = item["text"]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
      if (type == "text" && text.isNullOrBlank()) return null
      return PdfAnnotationOverlayItem(type, normalizedPoints, color, width, text)
    }

    private const val MAX_COORDINATE = 1_000_000.0
  }
}

@Composable
internal fun PdfAnnotationOverlay(
  items: List<PdfAnnotationOverlayItem>,
  modifier: Modifier = Modifier,
) {
  Canvas(modifier.fillMaxSize().testTag("pdf_annotation_overlay")) {
    val pageWidthScale = size.width / 1000f
    fun toPixel(point: Pair<Double, Double>) = Offset(
      (point.first * pageWidthScale).toFloat(),
      (point.second * pageWidthScale).toFloat(),
    )

    items.forEach { item ->
      val color = Color(android.graphics.Color.parseColor(item.color))
      val strokeWidth = (item.width.toFloat() * pageWidthScale).coerceAtLeast(1f)
      val vectorStroke = Stroke(
        width = strokeWidth,
        cap = StrokeCap.Round,
        join = StrokeJoin.Round,
      )
      when (item.type) {
        "rect" -> {
          val first = item.points[0]
          val second = item.points[1]
          drawRect(
            color = color,
            topLeft = toPixel(first),
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
        "line", "arrow" -> {
          val first = item.points[0]
          val second = item.points[1]
          drawLine(
            color = color,
            start = toPixel(first),
            end = toPixel(second),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
          )
          if (item.type == "arrow") {
            val direction = atan2(second.second - first.second, second.first - first.first)
            val headLength = 14.0 + item.width * 2.0
            listOf(direction - 0.45, direction + 0.45).forEach { wingAngle ->
              val wing = (second.first - headLength * cos(wingAngle)) to
                (second.second - headLength * sin(wingAngle))
              drawLine(
                color = color,
                start = toPixel(second),
                end = toPixel(wing),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
              )
            }
          }
        }
        "ellipse" -> {
          val first = item.points[0]
          val second = item.points[1]
          drawOval(
            color = color,
            topLeft = toPixel(first),
            size = Size(
              ((second.first - first.first) * pageWidthScale).toFloat(),
              ((second.second - first.second) * pageWidthScale).toFloat(),
            ),
            style = vectorStroke,
          )
        }
      }
    }
  }
}

package io.pagenote.android

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import com.caverock.androidsvg.SVG
import io.noties.markwon.image.MediaDecoder
import java.io.InputStream
import kotlin.math.roundToInt

internal class MermaidSvgMediaDecoder(
  context: Context,
  private val maxWidthPx: () -> Int,
) : MediaDecoder() {
  private val fallbackWidthPx = context.resources.displayMetrics.widthPixels.coerceAtLeast(1)

  override fun supportedTypes(): Collection<String> = listOf(CONTENT_TYPE)

  override fun decode(contentType: String?, inputStream: InputStream): Drawable {
    require(contentType == CONTENT_TYPE)
    val svg = SVG.getFromInputStream(inputStream)
    val viewBox = svg.documentViewBox
    val svgWidth = svg.documentWidth.takeIf { it.isFinite() && it > 0f }
      ?: viewBox?.width()?.takeIf { it.isFinite() && it > 0f }
      ?: fallbackWidthPx.toFloat()
    val svgHeight = svg.documentHeight.takeIf { it.isFinite() && it > 0f }
      ?: viewBox?.height()?.takeIf { it.isFinite() && it > 0f }
      ?: svgWidth * 0.6f
    return MermaidSvgDrawable(svg, svgWidth, svgHeight, maxWidthPx().takeIf { it > 0 } ?: fallbackWidthPx)
  }

  companion object {
    const val CONTENT_TYPE = "image/svg+xml"
  }
}

private class MermaidSvgDrawable(
  private val svg: SVG,
  private val svgWidth: Float,
  private val svgHeight: Float,
  maxWidthPx: Int,
) : Drawable() {
  private val intrinsicWidth = minOf(svgWidth, maxWidthPx.toFloat()).roundToInt().coerceAtLeast(1)
  private val intrinsicHeight = (svgHeight * intrinsicWidth / svgWidth).roundToInt().coerceAtLeast(1)

  override fun getIntrinsicWidth(): Int = intrinsicWidth

  override fun getIntrinsicHeight(): Int = intrinsicHeight

  override fun draw(canvas: Canvas) {
    val bounds = bounds
    if (bounds.width() <= 0 || bounds.height() <= 0) return
    canvas.save()
    canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
    canvas.scale(bounds.width() / svgWidth, bounds.height() / svgHeight)
    svg.renderToCanvas(canvas)
    canvas.restore()
  }

  override fun setAlpha(alpha: Int) = Unit

  override fun setColorFilter(colorFilter: ColorFilter?) = Unit

  @Deprecated("Deprecated in Android")
  override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

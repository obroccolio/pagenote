package io.pagenote.android

import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import io.noties.markwon.Markwon

internal class MermaidPreviewHost(
  context: Context,
  coordinator: MermaidRenderCoordinator,
  textColor: Int,
) : FrameLayout(context) {
  private var boundRenderer: Markwon? = null
  private var boundMarkdown: String? = null

  val markdownView = TextView(context).apply {
    textSize = 16f
    gravity = Gravity.TOP or Gravity.START
    setTextColor(textColor)
    setPadding(12, 10, 12, 10)
  }

  init {
    addView(
      markdownView,
      LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
    )
    coordinator.attachTo(this)
  }

  fun setMarkdown(renderer: Markwon, markdown: String, textColor: Int) {
    markdownView.setTextColor(textColor)
    if (boundRenderer !== renderer || boundMarkdown != markdown) {
      boundRenderer = renderer
      boundMarkdown = markdown
      renderer.setMarkdown(markdownView, markdown)
    }
  }
}

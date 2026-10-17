package io.pagenote.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotePreviewRewriteTest {
  @Test
  fun mermaidFenceRewritesToQuoteWithoutDuplication() {
    val markdown = "# t\n\n```mermaid\ngraph A-->B\n```\n\nAfter\n"

    val rewritten = NotePreviewRewrite.forPreview(markdown)

    assertFalse(rewritten.contains("```mermaid"))
    assertTrue(rewritten.contains("graph A-->B"))
    assertTrue(rewritten.contains("Diagram source (READY)"))
    // The source appears once: in the quote block, not as a second code fence.
    assertEquals(1, rewritten.split("graph A-->B").size - 1)
  }

  @Test
  fun mathFenceRewritesToFormulaQuote() {
    val rewritten = NotePreviewRewrite.forPreview("```math\nx^2\n```\n")

    assertFalse(rewritten.contains("```math"))
    assertTrue(rewritten.contains("x^2"))
  }

  @Test
  fun attachmentImageRewritesToLocalScheme() {
    val rewritten = NotePreviewRewrite.forPreview(
      "![p](/attachments/123e4567-e89b-12d3-a456-426614174000.png)",
      assetBase = "pagenote-asset",
    )

    assertTrue(rewritten.contains("pagenote-asset://123e4567-e89b-12d3-a456-426614174000.png"))
    assertFalse(rewritten.contains("/attachments/"))
  }

  private fun assertEquals(expected: Int, actual: Int) {
    org.junit.Assert.assertEquals(expected, actual)
  }
}

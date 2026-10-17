package io.pagenote.android

import org.junit.Assert.assertEquals
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
  fun mathFenceRewritesToNativeLatexBlock() {
    val rewritten = NotePreviewRewrite.forPreview("```math\nx^2\n```\n")

    assertFalse(rewritten.contains("```math"))
    assertTrue(rewritten.contains("$$\nx^2\n$$"))
  }

  @Test
  fun supportedCodeLanguageAliasesAreNormalizedForOfflineHighlighting() {
    val rewritten = NotePreviewRewrite.forPreview("```python3\nprint(1)\n```\n")

    assertTrue(rewritten.startsWith("```python\n"))
  }

  @Test
  fun inlineSingleDollarMathUsesMarkwonLatexDelimiters() {
    val rewritten = NotePreviewRewrite.forPreview("Value \$x^2\$ stays inline.")

    assertTrue(rewritten.contains("Value \$\$x^2\$\$ stays inline."))
  }

  @Test
  fun richFencesNestedInsideCodeAreNotRewritten() {
    val markdown = "```text\n```mermaid\ngraph A-->B\n```\n```\n"

    val rewritten = NotePreviewRewrite.forPreview(markdown)

    assertEquals(markdown, rewritten)
  }

  @Test
  fun dollarSignsInCodeAndEscapedTextAreNotRewritten() {
    val markdown = "`\$x\$` and \\$5 stay literal; \$y\$ is math."

    val rewritten = NotePreviewRewrite.forPreview(markdown)

    assertTrue(rewritten.contains("`\$x\$`"))
    assertTrue(rewritten.contains("\\$5"))
    assertTrue(rewritten.contains("\$\$y\$\$"))
  }

  @Test
  fun dollarSignsInsideMultilineCodeSpanRemainLiteral() {
    val markdown = "before `code\n\$not math\$\nstill code` and \$y\$"

    val rewritten = NotePreviewRewrite.forPreview(markdown)

    assertTrue(rewritten.contains("`code\n\$not math\$\nstill code`"))
    assertTrue(rewritten.endsWith("and \$\$y\$\$"))
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

}

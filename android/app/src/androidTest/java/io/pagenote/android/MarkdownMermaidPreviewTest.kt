package io.pagenote.android

import android.text.Spanned
import android.graphics.Color
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material3.Text
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.noties.markwon.image.AsyncDrawableSpan
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class MarkdownMermaidPreviewTest {
  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun sourcePreviewKeepsSurroundingMarkdownAndRendersDiagram() {
    val markdown = "Before diagram\n\n```mermaid\ngraph TD\nA[Start] --> B[Finish]\n```\n\nAfter diagram"
    val hostRef = AtomicReference<MermaidPreviewHost?>()
    composeRule.setContent { MermaidTestPreview(markdown, hostRef) }
    val image = awaitImage(hostRef) { it !is NoteImagePlaceholderDrawable }
    val text = requireNotNull(hostRef.get()).markdownView.text as Spanned
    assertTrue("diagram image should finish with a native drawable", image.hasResult())
    assertFalse("valid source should not become an error placeholder", image.result is NoteImagePlaceholderDrawable)
    assertTrue("diagram should stay within its preview column", image.result.intrinsicWidth <= requireNotNull(hostRef.get()).width)
    assertTrue("diagram should have a visible height", image.result.intrinsicHeight > 10)
    assertTrue(text.toString().contains("Before diagram"))
    assertTrue(text.toString().contains("After diagram"))
  }

  @Test
  fun invalidDiagramShowsNativeWarningAndRetainsSource() {
    val source = "this is not a Mermaid diagram"
    val hostRef = AtomicReference<MermaidPreviewHost?>()
    composeRule.setContent { MermaidTestPreview("```mermaid\n$source\n```", hostRef) }

    val image = awaitImage(hostRef) { it is NoteImagePlaceholderDrawable && it.message.contains(source) }
    val warning = image.result as? NoteImagePlaceholderDrawable
    assertTrue("invalid syntax should be explicit", warning != null)
    assertTrue(requireNotNull(warning).message.contains(source))
  }

  @Test
  fun emptyDiagramShowsExplicitEmptyState() {
    val hostRef = AtomicReference<MermaidPreviewHost?>()
    composeRule.setContent { MermaidTestPreview("```mermaid\n```", hostRef) }

    val image = awaitImage(hostRef) {
      it is NoteImagePlaceholderDrawable && it.message.contains("Empty Mermaid diagram")
    }
    val warning = image.result as? NoteImagePlaceholderDrawable
    assertTrue("empty diagrams should remain visible", warning != null)
    val emptyMessage = InstrumentationRegistry.getInstrumentation().targetContext
      .getString(R.string.note_rich_mermaid_empty)
    assertTrue(requireNotNull(warning).message.contains(emptyMessage))
  }

  private fun awaitImage(
    hostRef: AtomicReference<MermaidPreviewHost?>,
    isFinal: (android.graphics.drawable.Drawable) -> Boolean,
  ): io.noties.markwon.image.AsyncDrawable {
    composeRule.waitUntil(timeoutMillis = 30_000) {
      hostRef.get()?.markdownView?.text?.let { text ->
        val spanned = text as? Spanned ?: return@let false
        spanned.getSpans(0, spanned.length, AsyncDrawableSpan::class.java)
          .singleOrNull()?.drawable?.result?.let(isFinal) == true
      } == true
    }
    val spanned = requireNotNull(hostRef.get()).markdownView.text as Spanned
    return spanned.getSpans(0, spanned.length, AsyncDrawableSpan::class.java).single().drawable
  }

  @Composable
  private fun MermaidTestPreview(markdown: String, hostRef: AtomicReference<MermaidPreviewHost?>) {
    val context = LocalContext.current
    val coordinator = remember(context) { MermaidRenderCoordinator(context) }
    DisposableEffect(coordinator) { onDispose { coordinator.close() } }
    val markwon = remember(coordinator) {
      NoteMarkdownRenderer.createPreview(context, null, Color.BLACK, coordinator)
    }
    Column(Modifier.fillMaxSize()) {
      Text("Rendered Markdown")
      AndroidView(
        modifier = Modifier.weight(1f).fillMaxWidth().testTag("markdown_mermaid_host"),
        factory = { viewContext ->
          MermaidPreviewHost(viewContext, coordinator, Color.BLACK).also(hostRef::set)
        },
        update = { host -> host.setMarkdown(markwon, NotePreviewRewrite.forPreview(markdown), Color.BLACK) },
      )
    }
  }

}

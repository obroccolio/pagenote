package io.pagenote.android

import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.text.Spanned
import android.text.style.ClickableSpan
import android.text.style.StrikethroughSpan
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.noties.markwon.ext.latex.JLatexAsyncDrawableSpan
import io.noties.markwon.image.AsyncDrawableSpan
import io.noties.markwon.core.spans.EmphasisSpan
import io.noties.markwon.core.spans.StrongEmphasisSpan
import io.noties.markwon.ext.tables.TableSpan
import io.noties.markwon.ext.tables.TableRowSpan
import io.pagenote.core.project.PageNotes
import io.pagenote.feature.notes.NotePage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class NoteMarkdownFormatActivityTest {
  @get:Rule
  val composeRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun menuFormattingCommandsWrapSelectionAndSaveOnlyCurrentPage() {
    val savedPages = listOf("Lead Selected tail", "Second page remains unchanged")
    val savedNotes = PageNotes.join(savedPages)
    val project = installProject(savedNotes)

    formatCommand("bold").assertIsNotEnabled()

    val session = attachProjectToReader(project, savedNotes)
    composeRule.waitUntil(timeoutMillis = 10_000) { findNoteEditor() != null }
    composeRule.onNodeWithTag("note_tab_preview").performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) { findNoteEditor() == null }
    formatCommand("bold").assertIsNotEnabled()
    composeRule.onNodeWithTag("note_tab_edit").performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) { findNoteEditor() != null }

    listOf("bold", "italic", "strikethrough").forEach { command ->
      selectCurrentWord()
      formatCommand(command).assertIsEnabled().performClick()
      val expected = when (command) {
        "bold" -> "Lead **Selected** tail"
        "italic" -> "Lead ***Selected*** tail"
        else -> "Lead ***~~Selected~~*** tail"
      }
      try {
        composeRule.waitUntil(timeoutMillis = 10_000) { session.noteDraft.value == expected }
      } catch (error: Throwable) {
        throw AssertionError(
          "Expected draft <$expected>, got draft <${session.noteDraft.value}> and editor <${findNoteEditor()?.text}>",
          error,
        )
      }
    }

    val expectedCurrentPage = "Lead ***~~Selected~~*** tail"
    composeRule.onNodeWithText(composeRule.activity.getString(R.string.save)).performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) {
      NotePage.pageAt(ProjectNoteStore(testResolver, project.notesUri).read(), 1) == expectedCurrentPage
    }
    val saved = ProjectNoteStore(testResolver, project.notesUri).read()
    assertEquals(expectedCurrentPage, NotePage.pageAt(saved, 1))
    assertEquals(savedPages[1], NotePage.pageAt(saved, 2))
    assertEquals(expectedCurrentPage, session.noteDraft.value)

    composeRule.onNodeWithTag("note_tab_preview").performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) { findRenderedPreview() != null }
    val rendered = findRenderedPreview()?.text as? Spanned
      ?: error("formatted Markdown preview must expose native spans")
    val spans = rendered.getSpans(0, rendered.length, Any::class.java).toList()
    assertTrue("preview must render bold", spans.any { it is StrongEmphasisSpan })
    assertTrue("preview must render italic", spans.any { it is EmphasisSpan })
    assertTrue("preview must render strikethrough", spans.any { it is StrikethroughSpan })
  }

  @Test
  fun paragraphCommandsPrefixSelectedLinesAndSaveOnlyCurrentPage() {
    val initial = "intro\nalpha\nbeta\ngamma\ndelta"
    val savedPages = listOf(initial, "Second page remains unchanged")
    val savedNotes = PageNotes.join(savedPages)
    val project = installProject(savedNotes)

    paragraphCommand("heading").assertIsNotEnabled()

    val session = attachProjectToReader(project, savedNotes)
    composeRule.waitUntil(timeoutMillis = 10_000) { findNoteEditor() != null }
    composeRule.onNodeWithTag("note_tab_preview").performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) { findNoteEditor() == null }
    paragraphCommand("unordered_list").assertIsNotEnabled()
    composeRule.onNodeWithTag("note_tab_edit").performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) { findNoteEditor() != null }

    paragraphCommand("font_size").assertIsNotEnabled()
    paragraphCommand("task_list").assertIsNotEnabled()
    selectCurrentLine("intro")
    paragraphCommand("heading").assertIsEnabled().performClick()
    composeRule.onNodeWithTag("reader_heading_cancel").assertIsDisplayed().performClick()
    assertEquals(initial, session.noteDraft.value)

    paragraphCommand("heading").performClick()
    (1..3).forEach { level ->
      composeRule.onNodeWithTag("reader_heading_level_$level").assertIsDisplayed()
    }
    composeRule.onNodeWithTag("reader_heading_level_2").performClick()
    val afterHeading = "## intro\nalpha\nbeta\ngamma\ndelta"
    composeRule.waitUntil(timeoutMillis = 10_000) { session.noteDraft.value == afterHeading }

    selectTextRange("alpha", "beta")
    paragraphCommand("unordered_list").assertIsEnabled().performClick()
    val afterBullets = "## intro\n- alpha\n- beta\ngamma\ndelta"
    composeRule.waitUntil(timeoutMillis = 10_000) { session.noteDraft.value == afterBullets }

    selectCurrentLine("gamma")
    paragraphCommand("ordered_list").assertIsEnabled().performClick()
    val afterNumbered = "## intro\n- alpha\n- beta\n1. gamma\ndelta"
    composeRule.waitUntil(timeoutMillis = 10_000) { session.noteDraft.value == afterNumbered }

    selectCurrentLine("delta")
    paragraphCommand("quote").assertIsEnabled().performClick()
    val expected = "## intro\n- alpha\n- beta\n1. gamma\n> delta"
    composeRule.waitUntil(timeoutMillis = 10_000) { session.noteDraft.value == expected }

    composeRule.onNodeWithText(composeRule.activity.getString(R.string.save)).performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) {
      NotePage.pageAt(ProjectNoteStore(testResolver, project.notesUri).read(), 1) == expected
    }
    val persisted = ProjectNoteStore(testResolver, project.notesUri).read()
    assertEquals(expected, NotePage.pageAt(persisted, 1))
    assertEquals(savedPages[1], NotePage.pageAt(persisted, 2))

    composeRule.onNodeWithTag("note_tab_preview").performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) { findRenderedPreview("intro") != null }
    val rendered = findRenderedPreview("intro")?.text?.toString()
      ?: error("formatted Markdown preview must render the current page")
    listOf("intro", "alpha", "beta", "gamma", "delta").forEach { text ->
      assertTrue("preview must include <$text>", rendered.contains(text))
    }
    assertEquals(expected, session.noteDraft.value)
  }

  @Test
  fun codeBlockCommandWrapsSelectionAndSavesOnlyCurrentPage() {
    val savedPages = listOf("Lead Selected tail", "Second page remains unchanged")
    val savedNotes = PageNotes.join(savedPages)
    val project = installProject(savedNotes)

    insertCommand("code").assertIsNotEnabled()

    val session = attachProjectToReader(project, savedNotes)
    composeRule.waitUntil(timeoutMillis = 10_000) { findNoteEditor() != null }
    composeRule.onNodeWithTag("note_tab_preview").performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) { findNoteEditor() == null }
    insertCommand("code").assertIsNotEnabled()
    composeRule.onNodeWithTag("note_tab_edit").performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) { findNoteEditor() != null }

    selectCurrentWord()
    insertCommand("code").assertIsEnabled().performClick()
    val expected = "Lead \n```python\nSelected\n```\n tail"
    composeRule.waitUntil(timeoutMillis = 10_000) { session.noteDraft.value == expected }
    val editor = requireNotNull(findNoteEditor())
    val selectedStart = expected.indexOf("Selected")
    assertEquals(selectedStart, editor.selectionStart)
    assertEquals(selectedStart + "Selected".length, editor.selectionEnd)

    composeRule.onNodeWithText(composeRule.activity.getString(R.string.save)).performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) {
      NotePage.pageAt(ProjectNoteStore(testResolver, project.notesUri).read(), 1) == expected
    }
    val persisted = ProjectNoteStore(testResolver, project.notesUri).read()
    assertEquals(expected, NotePage.pageAt(persisted, 1))
    assertEquals(savedPages[1], NotePage.pageAt(persisted, 2))

    composeRule.onNodeWithTag("note_tab_preview").performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) { findRenderedPreview("Selected") != null }
    val rendered = findRenderedPreview("Selected")?.text?.toString()
      ?: error("code block preview must render the selected source")
    assertTrue("preview must retain code", rendered.contains("Selected"))
    assertTrue("preview must not expose Markdown fences", !rendered.contains("```"))
    assertEquals(expected, session.noteDraft.value)
  }

  @Test
  fun formulaCommandWrapsSelectionAndSavesOnlyCurrentPage() {
    val savedPages = listOf("Lead x^2 tail", "Second page remains unchanged")
    val savedNotes = PageNotes.join(savedPages)
    val project = installProject(savedNotes)

    insertCommand("formula").assertIsNotEnabled()

    val session = attachProjectToReader(project, savedNotes)
    composeRule.waitUntil(timeoutMillis = 10_000) { findNoteEditor() != null }
    composeRule.onNodeWithTag("note_tab_preview").performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) { findNoteEditor() == null }
    insertCommand("formula").assertIsNotEnabled()
    composeRule.onNodeWithTag("note_tab_edit").performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) { findNoteEditor() != null }

    val editor = requireNotNull(findNoteEditor())
    val formulaStart = editor.text.indexOf("x^2")
    check(formulaStart >= 0) { "formula source must remain in the Markdown editor" }
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      editor.requestFocus()
      editor.setSelection(formulaStart, formulaStart + "x^2".length)
    }
    composeRule.waitForIdle()

    insertCommand("formula").assertIsEnabled().performClick()
    val expected = "Lead \n\n$$\nx^2\n$$\n\n tail"
    composeRule.waitUntil(timeoutMillis = 10_000) { session.noteDraft.value == expected }
    val updatedEditor = requireNotNull(findNoteEditor())
    val selectedStart = expected.indexOf("x^2")
    assertEquals(selectedStart, updatedEditor.selectionStart)
    assertEquals(selectedStart + "x^2".length, updatedEditor.selectionEnd)

    composeRule.onNodeWithText(composeRule.activity.getString(R.string.save)).performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) {
      NotePage.pageAt(ProjectNoteStore(testResolver, project.notesUri).read(), 1) == expected
    }
    val persisted = ProjectNoteStore(testResolver, project.notesUri).read()
    assertEquals(expected, NotePage.pageAt(persisted, 1))
    assertEquals(savedPages[1], NotePage.pageAt(persisted, 2))

    composeRule.onNodeWithTag("note_tab_preview").performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) { findRenderedPreview("Lead") != null }
    val rendered = findRenderedPreview("Lead")?.text as? Spanned
      ?: error("formula preview must expose native spans")
    assertFalse("preview must not expose Markdown block delimiters", rendered.toString().contains("$$"))
    val formulaSpan = rendered.getSpans(0, rendered.length, JLatexAsyncDrawableSpan::class.java).single()
    val formulaDrawable = (formulaSpan as AsyncDrawableSpan).drawable
    composeRule.waitUntil(timeoutMillis = 10_000) { formulaDrawable.hasResult() }
    assertTrue(
      "valid formula must render instead of using the error fallback",
      formulaDrawable.result?.javaClass?.name?.startsWith("ru.noties.jlatexmath.") == true,
    )
    assertEquals(expected, session.noteDraft.value)
  }

  @Test
  fun tableCommandReplacesSelectionAndSavesOnlyCurrentPage() {
    val savedPages = listOf("Lead replace me tail", "Second page remains unchanged")
    val savedNotes = PageNotes.join(savedPages)
    val project = installProject(savedNotes)

    insertCommand("table").assertIsNotEnabled()

    val session = attachProjectToReader(project, savedNotes)
    composeRule.waitUntil(timeoutMillis = 10_000) { findNoteEditor() != null }
    composeRule.onNodeWithTag("note_tab_preview").performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) { findNoteEditor() == null }
    insertCommand("table").assertIsNotEnabled()
    composeRule.onNodeWithTag("note_tab_edit").performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) { findNoteEditor() != null }

    val editor = requireNotNull(findNoteEditor())
    val selectedText = "replace me"
    val selectionStart = editor.text.indexOf(selectedText)
    check(selectionStart >= 0) { "selected note text must remain in the Markdown editor" }
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      editor.requestFocus()
      editor.setSelection(selectionStart, selectionStart + selectedText.length)
    }
    composeRule.waitForIdle()

    insertCommand("table").assertIsEnabled().performClick()
    val table = "\n\n| 列一 | 列二 |\n| --- | --- |\n| 内容 | 内容 |\n\n"
    val expected = "Lead $table tail"
    val cursor = "Lead ".length + table.length
    composeRule.waitUntil(timeoutMillis = 10_000) { session.noteDraft.value == expected }
    val updatedEditor = requireNotNull(findNoteEditor())
    assertEquals(cursor, updatedEditor.selectionStart)
    assertEquals(cursor, updatedEditor.selectionEnd)

    composeRule.onNodeWithText(composeRule.activity.getString(R.string.save)).performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) {
      NotePage.pageAt(ProjectNoteStore(testResolver, project.notesUri).read(), 1) == expected
    }
    val persisted = ProjectNoteStore(testResolver, project.notesUri).read()
    assertEquals(expected, NotePage.pageAt(persisted, 1))
    assertEquals(savedPages[1], NotePage.pageAt(persisted, 2))

    composeRule.onNodeWithTag("note_tab_preview").performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) { findRenderedPreview("Lead") != null }
    val rendered = findRenderedPreview("Lead")?.text as? Spanned
      ?: error("table preview must expose native spans")
    assertTrue("GFM table must render as a native table span", rendered.getSpans(0, rendered.length, TableSpan::class.java).isNotEmpty())
    assertEquals("header and content must both render as table rows", 2, rendered.getSpans(0, rendered.length, TableRowSpan::class.java).size)
    assertEquals(expected, session.noteDraft.value)
  }

  @Test
  fun linkCommandWrapsSelectionInsertsAndSelectsPlaceholderSavesOnlyCurrentPage() {
    val savedPages = listOf("Lead chosen tail", "Second page remains unchanged")
    val savedNotes = PageNotes.join(savedPages)
    val project = installProject(savedNotes)

    insertCommand("link").assertIsNotEnabled()

    val session = attachProjectToReader(project, savedNotes)
    composeRule.waitUntil(timeoutMillis = 10_000) { findNoteEditor() != null }
    composeRule.onNodeWithTag("note_tab_preview").performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) { findNoteEditor() == null }
    insertCommand("link").assertIsNotEnabled()
    composeRule.onNodeWithTag("note_tab_edit").performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) { findNoteEditor() != null }

    val selectedText = "chosen"
    val selectionStart = savedPages.first().indexOf(selectedText)
    val selectionEditor = requireNotNull(findNoteEditor())
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      selectionEditor.requestFocus()
      selectionEditor.setSelection(selectionStart + selectedText.length, selectionStart)
    }
    composeRule.waitForIdle()

    insertCommand("link").assertIsEnabled().performClick()
    val selectedLink = "[$selectedText](https://example.com)"
    val expectedAfterSelection = "Lead $selectedLink tail"
    composeRule.waitUntil(timeoutMillis = 10_000) { session.noteDraft.value == expectedAfterSelection }
    val selectedEditor = requireNotNull(findNoteEditor())
    assertEquals("Lead [".length, selectedEditor.selectionStart)
    assertEquals("Lead [".length + selectedText.length, selectedEditor.selectionEnd)

    val placeholder = composeRule.activity.getString(R.string.reader_menu_link_placeholder)
    val insertionEditor = requireNotNull(findNoteEditor())
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      insertionEditor.setSelection(insertionEditor.text.length)
    }
    insertCommand("link").performClick()
    val expected = "$expectedAfterSelection[$placeholder](https://example.com)"
    composeRule.waitUntil(timeoutMillis = 10_000) { session.noteDraft.value == expected }
    val placeholderEditor = requireNotNull(findNoteEditor())
    assertEquals(expectedAfterSelection.length + 1, placeholderEditor.selectionStart)
    assertEquals(expectedAfterSelection.length + 1 + placeholder.length, placeholderEditor.selectionEnd)

    composeRule.onNodeWithText(composeRule.activity.getString(R.string.save)).performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) {
      NotePage.pageAt(ProjectNoteStore(testResolver, project.notesUri).read(), 1) == expected
    }
    val persisted = ProjectNoteStore(testResolver, project.notesUri).read()
    assertEquals(expected, NotePage.pageAt(persisted, 1))
    assertEquals(savedPages[1], NotePage.pageAt(persisted, 2))

    composeRule.onNodeWithTag("note_tab_preview").performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) { findRenderedPreview("chosen") != null }
    val rendered = findRenderedPreview("chosen")?.text as? Spanned
      ?: error("link preview must expose native spans")
    assertTrue(
      "Markdown link preview must expose a clickable span",
      rendered.getSpans(0, rendered.length, ClickableSpan::class.java).isNotEmpty(),
    )
  }

  private fun formatCommand(id: String) = run {
    val commandTag = "reader_menu_command_$id"
    if (composeRule.onAllNodesWithTag(commandTag).fetchSemanticsNodes().isEmpty()) {
      val collapsed = composeRule.onAllNodesWithTag("reader_menu_sidebar_collapsed").fetchSemanticsNodes()
      if (collapsed.isNotEmpty()) {
        composeRule.onNodeWithTag("reader_menu_group_format").performClick()
      } else {
        composeRule.onNodeWithTag("reader_menu_trigger").performClick()
      }
    }
    composeRule.waitUntil(timeoutMillis = 10_000) {
      composeRule.onAllNodesWithTag(commandTag).fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag(commandTag).performScrollTo()
  }

  private fun paragraphCommand(id: String) = run {
    val commandTag = "reader_menu_command_$id"
    if (composeRule.onAllNodesWithTag(commandTag).fetchSemanticsNodes().isEmpty()) {
      val collapsed = composeRule.onAllNodesWithTag("reader_menu_sidebar_collapsed").fetchSemanticsNodes()
      if (collapsed.isNotEmpty()) {
        composeRule.onNodeWithTag("reader_menu_group_paragraph").performClick()
      } else {
        composeRule.onNodeWithTag("reader_menu_trigger").performClick()
      }
    }
    composeRule.waitUntil(timeoutMillis = 10_000) {
      composeRule.onAllNodesWithTag(commandTag).fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag(commandTag).performScrollTo()
  }

  private fun insertCommand(id: String) = run {
    val commandTag = "reader_menu_command_$id"
    if (composeRule.onAllNodesWithTag(commandTag).fetchSemanticsNodes().isEmpty()) {
      val collapsed = composeRule.onAllNodesWithTag("reader_menu_sidebar_collapsed").fetchSemanticsNodes()
      if (collapsed.isNotEmpty()) {
        composeRule.onNodeWithTag("reader_menu_group_insert").performClick()
      } else {
        composeRule.onNodeWithTag("reader_menu_trigger").performClick()
      }
    }
    composeRule.waitUntil(timeoutMillis = 10_000) {
      composeRule.onAllNodesWithTag(commandTag).fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag(commandTag).performScrollTo()
  }

  private fun attachProjectToReader(project: OpenedProject, notes: String): ReaderSessionViewModel {
    composeRule.waitUntil(timeoutMillis = 10_000) {
      composeRule.onAllNodesWithTag("reader_menu_command_open_project").fetchSemanticsNodes().isNotEmpty()
    }
    val session = ViewModelProvider(composeRule.activity)[ReaderSessionViewModel::class.java]
    composeRule.runOnUiThread {
      session.project.value = project
      session.markdownSource.value = "Source document text"
      session.notesMarkdown.value = notes
      session.noteDraft.value = NotePage.pageAt(notes, 1)
      session.noteBaseRevision.value = PageNotes.revision(NotePage.pageAt(notes, 1))
      session.noteWritable.value = true
      session.noteStatus.value = null
    }
    return session
  }

  private fun selectCurrentWord() {
    val editor = requireNotNull(findNoteEditor())
    val text = editor.text.toString()
    val start = text.indexOf("Selected")
    check(start >= 0) { "formatted word must remain in the Markdown source" }
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      editor.requestFocus()
      editor.setSelection(start, start + "Selected".length)
    }
    composeRule.waitForIdle()
  }

  private fun selectCurrentLine(text: String) {
    val editor = requireNotNull(findNoteEditor())
    val markdown = editor.text.toString()
    val start = markdown.indexOf(text)
    check(start >= 0) { "expected <$text> to remain in the Markdown source" }
    val end = start + text.length
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      editor.requestFocus()
      editor.setSelection(start, end)
    }
    composeRule.waitForIdle()
  }

  private fun selectTextRange(firstText: String, lastText: String) {
    val editor = requireNotNull(findNoteEditor())
    val markdown = editor.text.toString()
    val start = markdown.indexOf(firstText)
    val lastStart = markdown.indexOf(lastText, start + firstText.length)
    check(start >= 0 && lastStart >= 0) { "expected selected Markdown text to remain in the editor" }
    val end = lastStart + lastText.length
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      editor.requestFocus()
      editor.setSelection(start, end)
    }
    composeRule.waitForIdle()
  }

  private fun installProject(notes: String): OpenedProject {
    val manifest = """
      {"format":"pagenote-project","version":1,"id":"${"c".repeat(64)}","name":"Markdown format fixture","left":"src/pattern-recognition.pdf","right":"src/notes.md","leftName":"pattern-recognition.pdf"}
    """.trimIndent().toByteArray(Charsets.UTF_8)
    testResolver.call(
      ExistingProjectSafTestProvider.AUTHORITY,
      ExistingProjectSafTestProvider.METHOD_INSTALL,
      null,
      Bundle().apply {
        putByteArray(ExistingProjectSafTestProvider.EXTRA_MANIFEST, manifest)
        putByteArray(ExistingProjectSafTestProvider.EXTRA_PDF, "%PDF-fixture".toByteArray())
        putByteArray(ExistingProjectSafTestProvider.EXTRA_NOTES, notes.toByteArray(Charsets.UTF_8))
        putByteArray(ExistingProjectSafTestProvider.EXTRA_REVIEWS, "[]".toByteArray())
      },
    )
    return ProjectDocumentOpener(testResolver).open(treeUri)
  }

  private fun findNoteEditor(): EditText? {
    val result = AtomicReference<EditText?>()
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      val matches = mutableListOf<EditText>()
      composeRule.activity.window.decorView.collectEditTexts(matches)
      result.set(matches.firstOrNull { it.tag == "note_markdown_editor" })
    }
    return result.get()
  }

  private fun findRenderedPreview(containsText: String = "Selected"): TextView? {
    val result = AtomicReference<TextView?>()
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      val hosts = mutableListOf<MermaidPreviewHost>()
      composeRule.activity.window.decorView.collectPreviewHosts(hosts)
      result.set(hosts.firstOrNull { it.markdownView.text.toString().contains(containsText) }?.markdownView)
    }
    return result.get()
  }

  private fun View.collectEditTexts(destination: MutableList<EditText>) {
    if (this is EditText) destination += this
    if (this is ViewGroup) for (index in 0 until childCount) getChildAt(index).collectEditTexts(destination)
  }

  private fun View.collectPreviewHosts(destination: MutableList<MermaidPreviewHost>) {
    if (this is MermaidPreviewHost) destination += this
    if (this is ViewGroup) for (index in 0 until childCount) getChildAt(index).collectPreviewHosts(destination)
  }

  private val testResolver get() = InstrumentationRegistry.getInstrumentation().context.contentResolver
  private val treeUri: Uri
    get() = DocumentsContract.buildTreeDocumentUri(
      ExistingProjectSafTestProvider.AUTHORITY,
      ExistingProjectSafTestProvider.ROOT_ID,
    )
}

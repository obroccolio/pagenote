package io.pagenote.android

import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.text.InputType
import android.widget.EditText
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.compose.foundation.Image
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import io.noties.markwon.editor.MarkwonEditor
import io.noties.markwon.editor.MarkwonEditorTextWatcher
import io.noties.markwon.ext.tables.TablePlugin
import io.pagenote.agent.MissingModelConfigException
import io.pagenote.agent.OkHttpChatClient
import io.pagenote.agent.PageRequest
import io.pagenote.agent.PageTutor
import io.pagenote.agent.PageTutorRequest
import io.pagenote.agent.PageTutorResult
import io.pagenote.core.project.PageNotes
import io.pagenote.feature.notes.NotePage
import io.pagenote.feature.notes.PageNoteConflictException
import io.pagenote.feature.notes.PageNoteEditor
import io.pagenote.feature.ocr.OfflineOcrEngine
import io.pagenote.feature.ocr.OcrPolicy
import io.pagenote.feature.ocr.OcrResult
import io.pagenote.feature.ocr.OcrResultMapper
import io.pagenote.feature.ocr.OcrResultPolicy
import io.pagenote.feature.pen.PenCanvasTool
import io.pagenote.feature.pen.PenCanvasView
import io.pagenote.feature.pen.PenPageStore
import io.pagenote.feature.pen.PenSide
import io.pagenote.feature.pen.PenStroke
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.Executors
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: android.os.Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      ReaderSettingsHost()
    }
  }

}

@Composable
private fun ReaderSettingsHost() {
  val context = LocalContext.current
  val activityResultRegistryOwner = context as ActivityResultRegistryOwner
  val systemConfiguration = LocalConfiguration.current
  val settingsStore = remember(context.applicationContext) {
    readerSettingsStore(context.applicationContext)
  }
  val modelSettingsStore = remember(context.applicationContext) {
    agentModelSettingsStore(context.applicationContext)
  }
  var retryAttempt by remember { mutableIntStateOf(0) }
  val loadState by produceState<ReaderSettingsLoadState>(
    initialValue = ReaderSettingsLoadState.Loading,
    settingsStore,
    retryAttempt,
  ) {
    value = ReaderSettingsLoadState.Loading
    settingsStore.settings.asReaderSettingsLoadStates().collect { value = it }
  }
  val systemThemeMode = resolveReaderTheme("system", isSystemInDarkTheme())
  val settings = (loadState as? ReaderSettingsLoadState.Loaded)?.settings

  if (settings == null) {
    MaterialTheme(colorScheme = readerColorScheme(systemThemeMode)) {
      Surface(modifier = Modifier.fillMaxSize()) {
        ReaderSettingsLoadScreen(loadState) { retryAttempt += 1 }
      }
    }
    return
  }

  val systemLocale = systemConfiguration.locales.get(0) ?: Locale.getDefault()
  val locale = resolveReaderLocale(settings.language, systemLocale)
  val localizedConfiguration = remember(systemConfiguration, settings.language, locale) {
    Configuration(systemConfiguration).apply {
      if (settings.language != "system") {
        setLocale(locale)
        setLayoutDirection(locale)
      }
    }
  }
  val localizedContext = remember(context, localizedConfiguration) {
    context.createConfigurationContext(localizedConfiguration)
  }
  val themeMode = resolveReaderTheme(settings.theme, isSystemInDarkTheme())

  CompositionLocalProvider(
    LocalConfiguration provides localizedConfiguration,
    LocalContext provides localizedContext,
    LocalActivityResultRegistryOwner provides activityResultRegistryOwner,
  ) {
    MaterialTheme(colorScheme = readerColorScheme(themeMode)) {
      Surface(modifier = Modifier.fillMaxSize()) {
        ReaderHome(settings = settings, settingsStore = settingsStore, modelSettingsStore = modelSettingsStore)
      }
    }
  }
}

private data class ReaderProjectData(
  val project: OpenedProject,
  val noteText: String,
  val renderer: PdfDocumentSession,
  val noteWritable: Boolean,
  val penStore: ProjectPenStore,
  val penPageStore: PenPageStore,
  val penWritable: Boolean,
)

@Composable
private fun ReaderHome(
  settings: ReaderSettings,
  settingsStore: ReaderSettingsStore,
  modelSettingsStore: AgentModelSettingsStore,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val pageTutor = remember { PageTutor(OkHttpChatClient()) }
  var project by remember { mutableStateOf<OpenedProject?>(null) }
  var renderer by remember { mutableStateOf<PdfDocumentSession?>(null) }
  var pageIndex by remember { mutableIntStateOf(0) }
  var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
  var viewportTransform by remember(project?.sourceUri, pageIndex) {
    mutableStateOf(PdfViewportTransform.DEFAULT)
  }
  var viewportBounds by remember(project?.sourceUri, pageIndex) {
    mutableStateOf(PdfViewportBounds.ZERO)
  }
  var notesMarkdown by remember { mutableStateOf("") }
  var noteDraft by remember { mutableStateOf("") }
  var noteBaseRevision by remember { mutableStateOf(PageNotes.revision("")) }
  var noteWritable by remember { mutableStateOf(false) }
  var noteStatus by remember { mutableStateOf<String?>(null) }
  var penStore by remember { mutableStateOf<ProjectPenStore?>(null) }
  var penPageStore by remember { mutableStateOf(PenPageStore()) }
  var penStrokes by remember { mutableStateOf<List<PenStroke>>(emptyList()) }
  var penWritable by remember { mutableStateOf(false) }
  var penTool by remember { mutableStateOf(PenCanvasTool.PEN) }
  var penStatus by remember { mutableStateOf<String?>(null) }
  var failedPenSave by remember { mutableStateOf<Pair<Int, List<PenStroke>>?>(null) }
  var pendingPenSaves by remember { mutableIntStateOf(0) }
  val penSaveMutex = remember { Mutex() }
  var ocrEngine by remember { mutableStateOf<OfflineOcrEngine?>(null) }
  var ocrResult by remember { mutableStateOf<OcrResult?>(null) }
  var ocrResultPage by remember { mutableIntStateOf(0) }
  var showOcrResult by remember { mutableStateOf(false) }
  var isOcrRunning by remember { mutableStateOf(false) }
  var isOpening by remember { mutableStateOf(false) }
  var showSettings by remember { mutableStateOf(false) }
  var showAgentModelSettings by remember { mutableStateOf(false) }
  var showPageTutor by remember { mutableStateOf(false) }
  var pageTutorInstruction by remember {
    mutableStateOf(context.getString(R.string.page_tutor_default_instruction))
  }
  var pageTutorResult by remember { mutableStateOf<PageTutorResult?>(null) }
  var pageTutorError by remember { mutableStateOf<String?>(null) }
  var isPageTutorRunning by remember { mutableStateOf(false) }
  var isRendering by remember { mutableStateOf(false) }
  var isSaving by remember { mutableStateOf(false) }
  val isPenSaving = pendingPenSaves > 0
  var errorMessage by remember { mutableStateOf<String?>(null) }
  val savedPageText = NotePage.pageAt(notesMarkdown, pageIndex + 1)
  val hasUnsavedChanges = noteDraft != savedPageText

  fun applyViewportZoom(zoomChange: Float) {
    val bounds = viewportBounds
    viewportTransform = PdfViewportTransform.applyGesture(
      current = viewportTransform,
      viewportWidthPx = bounds.viewportWidthPx,
      viewportHeightPx = bounds.viewportHeightPx,
      pageWidthPx = bounds.pageWidthPx,
      pageHeightPx = bounds.pageHeightPx,
      centroidX = bounds.viewportWidthPx / 2f,
      centroidY = bounds.viewportHeightPx / 2f,
      zoomChange = zoomChange,
      panX = 0f,
      panY = 0f,
    )
  }

  fun fitViewportWidth() {
    viewportTransform = PdfViewportTransform.fitWidth(
      viewportWidthPx = viewportBounds.viewportWidthPx,
      pageWidthPx = viewportBounds.pageWidthPx,
    )
  }

  suspend fun savePenPage(targetPage: Int, strokes: List<PenStroke>) {
    val store = penStore ?: return
    if (!penWritable) {
      penStatus = context.getString(R.string.readonly_pen_cannot_save)
      return
    }
    val snapshot = strokes.toList()
    pendingPenSaves += 1
    try {
      penSaveMutex.withLock {
        withContext(Dispatchers.IO) {
          store.writePage(PenSide.DOCUMENT, targetPage, snapshot)
        }
      }
      if (failedPenSave?.first == targetPage) failedPenSave = null
      penStatus = context.getString(R.string.pen_saved)
    } catch (error: Exception) {
      if (error is CancellationException) throw error
      failedPenSave = targetPage to snapshot
      penStatus = context.getString(R.string.pen_not_saved, error.message ?: "")
    } finally {
      pendingPenSaves -= 1
    }
  }

  fun applyPenStroke(stroke: PenStroke) {
    val targetPage = pageIndex + 1
    penPageStore.addStroke(PenSide.DOCUMENT, targetPage, stroke)
    val updated = penPageStore.getItems(PenSide.DOCUMENT, targetPage)
    penStrokes = updated
    scope.launch { savePenPage(targetPage, updated) }
  }

  fun erasePenStroke(index: Int) {
    val targetPage = pageIndex + 1
    if (penPageStore.eraseAt(PenSide.DOCUMENT, targetPage, index)) {
      val updated = penPageStore.getItems(PenSide.DOCUMENT, targetPage)
      penStrokes = updated
      scope.launch { savePenPage(targetPage, updated) }
    }
  }

  fun clearPenPage() {
    val targetPage = pageIndex + 1
    if (penPageStore.clearPage(PenSide.DOCUMENT, targetPage)) {
      penStrokes = emptyList()
      scope.launch { savePenPage(targetPage, emptyList()) }
    }
  }

  fun undoPenPage() {
    val targetPage = pageIndex + 1
    if (penPageStore.undo(PenSide.DOCUMENT, targetPage)) {
      val updated = penPageStore.getItems(PenSide.DOCUMENT, targetPage)
      penStrokes = updated
      scope.launch { savePenPage(targetPage, updated) }
    }
  }

  fun redoPenPage() {
    val targetPage = pageIndex + 1
    if (penPageStore.redo(PenSide.DOCUMENT, targetPage)) {
      val updated = penPageStore.getItems(PenSide.DOCUMENT, targetPage)
      penStrokes = updated
      scope.launch { savePenPage(targetPage, updated) }
    }
  }

  suspend fun saveCurrentNote(): Boolean {
    val activeProject = project ?: return false
    if (!noteWritable) {
      noteStatus = context.getString(R.string.note_read_only)
      return false
    }
    if (!hasUnsavedChanges) return true

    isSaving = true
    noteStatus = null
    val targetPage = pageIndex + 1
    val draftToSave = noteDraft
    val expectedRevision = noteBaseRevision
    return try {
      val result = withContext(Dispatchers.IO) {
        val store = ProjectNoteStore(context.contentResolver, activeProject.notesUri)
        val latest = store.read()
        val edited = PageNoteEditor.replacePage(
          latestMarkdown = latest,
          page = targetPage,
          replacement = draftToSave,
          expectedRevision = expectedRevision,
        )
        store.write(edited.markdown)
        edited
      }
      notesMarkdown = result.markdown
      noteBaseRevision = result.pageRevision
      noteStatus = context.getString(R.string.note_saved)
      true
    } catch (conflict: PageNoteConflictException) {
      noteStatus = conflict.message
      false
    } catch (error: Exception) {
      noteStatus = context.getString(R.string.note_save_failed, error.message ?: "")
      false
    } finally {
      isSaving = false
    }
  }

  fun reloadCurrentNote() {
    val activeProject = project ?: return
    scope.launch {
      try {
        val latest = withContext(Dispatchers.IO) {
          ProjectNoteStore(context.contentResolver, activeProject.notesUri).read()
        }
        notesMarkdown = latest
        val freshPage = NotePage.pageAt(latest, pageIndex + 1)
        noteDraft = freshPage
        noteBaseRevision = PageNotes.revision(freshPage)
        noteStatus = context.getString(R.string.note_reloaded)
      } catch (error: Exception) {
        noteStatus = context.getString(R.string.note_reload_failed, error.message ?: "")
      }
    }
  }

  suspend fun changePage(nextPage: Int) {
    if (hasUnsavedChanges && !saveCurrentNote()) return
    noteStatus = null
    showOcrResult = false
    pageIndex = nextPage
  }

  fun recognizeCurrentPage() {
    val activeRenderer = renderer ?: return
    val targetPage = pageIndex
    showOcrResult = false
    scope.launch {
      isOcrRunning = true
      try {
        val result = withTimeout(OcrPolicy.TIMEOUT_MS) {
          val engine = ocrEngine ?: OfflineOcrEngine.create(context).also { ocrEngine = it }
          val input = withContext(Dispatchers.IO) { activeRenderer.render(targetPage) }
          try {
            engine.recognize(input)
          } finally {
            input.recycle()
          }
        }
        ocrResult = result
        ocrResultPage = targetPage
        showOcrResult = true
      } catch (timeout: TimeoutCancellationException) {
        ocrResult = OcrResult(
          engine = OcrResultMapper.ENGINE,
          status = "failed",
          warnings = listOf(context.getString(R.string.ocr_timeout)),
        )
        ocrResultPage = targetPage
        showOcrResult = true
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (error: Exception) {
        ocrResult = OcrResult(
          engine = OcrResultMapper.ENGINE,
          status = "failed",
          warnings = listOf(error.message ?: context.getString(R.string.offline_ocr_failed)),
        )
        ocrResultPage = targetPage
        showOcrResult = true
      } finally {
        isOcrRunning = false
      }
    }
  }

  fun explainCurrentPage(instruction: String) {
    val activeProject = project ?: return
    val activeRenderer = renderer ?: return
    val targetPageIndex = pageIndex
    scope.launch {
      pageTutorInstruction = instruction
      isPageTutorRunning = true
      pageTutorError = null
      pageTutorResult = null
      try {
        val endpoint = withContext(Dispatchers.IO) { modelSettingsStore.resolveEndpoint() }
        if (hasUnsavedChanges && !saveCurrentNote()) {
          pageTutorError = noteStatus ?: context.getString(R.string.note_save_failed, "")
          return@launch
        }

        val currentNote = noteDraft
        val expectedRevision = noteBaseRevision
        val pageSourceLoader = PageTutorSourceLoader(
          nativeText = { page ->
            withContext(Dispatchers.IO) { activeRenderer.extractText(page) }
          },
          ocrText = { page ->
            val engine = ocrEngine ?: OfflineOcrEngine.create(context).also { ocrEngine = it }
            val bitmap = withContext(Dispatchers.IO) { activeRenderer.render(page) }
            try {
              withContext(Dispatchers.Default) { engine.recognize(bitmap) }
            } finally {
              bitmap.recycle()
            }
          },
        )
        val (currentSource, neighbors) = withTimeout(OcrPolicy.TIMEOUT_MS) {
          val current = pageSourceLoader.loadCurrent(targetPageIndex)
          val adjacent = mutableListOf<PageRequest.PageMaterial>()
          listOf(targetPageIndex - 1, targetPageIndex + 1)
            .filter { it in 0 until activeRenderer.pageCount }
            .forEach { neighborIndex ->
              pageSourceLoader.loadNeighbor(neighborIndex)?.let { source ->
                adjacent += PageRequest.PageMaterial(
                  page = neighborIndex + 1,
                  text = source.text,
                )
              }
            }
          current to adjacent
        }
        val result = pageTutor.explain(
          endpoint = endpoint,
          request = PageTutorRequest(
            documentId = activeProject.manifest.id,
            page = targetPageIndex + 1,
            totalPages = activeRenderer.pageCount,
            instruction = instruction,
            sourceText = currentSource.text,
            noteMarkdown = currentNote,
            noteRevision = expectedRevision,
            sourceWarnings = currentSource.warnings,
            neighbors = neighbors,
          ),
        )
        if (project?.manifest?.id != activeProject.manifest.id || pageIndex != targetPageIndex) {
          pageTutorError = context.getString(R.string.page_tutor_conflict)
        } else {
          pageTutorResult = result
        }
      } catch (missing: MissingModelConfigException) {
        pageTutorError = context.getString(R.string.page_tutor_model_missing)
        showAgentModelSettings = true
      } catch (timeout: TimeoutCancellationException) {
        pageTutorError = context.getString(R.string.page_tutor_timeout)
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (error: PageTutorSourceException) {
        pageTutorError = "${context.getString(R.string.page_tutor_source_failed)} ${error.message.orEmpty()}"
      } catch (error: Exception) {
        pageTutorError = "${context.getString(R.string.page_tutor_request_failed)} ${error.message.orEmpty()}"
      } finally {
        isPageTutorRunning = false
      }
    }
  }

  fun appendPageTutorResult() {
    val activeProject = project ?: return
    val preview = pageTutorResult ?: return
    if (!noteWritable) return
    if (preview.page != pageIndex + 1 || preview.noteRevision != noteBaseRevision) {
      pageTutorError = context.getString(R.string.page_tutor_conflict)
      return
    }

    scope.launch {
      isSaving = true
      pageTutorError = null
      try {
        val edited = withContext(Dispatchers.IO) {
          val store = ProjectNoteStore(context.contentResolver, activeProject.notesUri)
          val latest = store.read()
          val heading = context.getString(R.string.page_tutor_note_heading)
          val result = PageNoteEditor.appendPage(
            latestMarkdown = latest,
            page = preview.page,
            addition = "$heading\n\n${preview.markdown}",
            expectedRevision = preview.noteRevision,
          )
          store.write(result.markdown)
          result
        }
        notesMarkdown = edited.markdown
        noteDraft = NotePage.pageAt(edited.markdown, preview.page)
        noteBaseRevision = edited.pageRevision
        noteStatus = context.getString(R.string.page_tutor_saved)
        pageTutorResult = null
        showPageTutor = false
      } catch (conflict: PageNoteConflictException) {
        pageTutorError = context.getString(R.string.page_tutor_conflict)
      } catch (error: Exception) {
        pageTutorError = "${context.getString(R.string.page_tutor_append_failed)} ${error.message.orEmpty()}"
      } finally {
        isSaving = false
      }
    }
  }

  val projectPicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocumentTree(),
  ) { treeUri: Uri? ->
    if (treeUri != null) {
      scope.launch {
        isOpening = true
        errorMessage = null
        try {
          var writable = true
          try {
            context.contentResolver.takePersistableUriPermission(
              treeUri,
              Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
          } catch (_: SecurityException) {
            writable = false
            context.contentResolver.takePersistableUriPermission(
              treeUri,
              Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
          }
          val loaded = withContext(Dispatchers.IO) {
            val opened = ProjectDocumentOpener(context.contentResolver).open(treeUri)
            require(opened.sourceExtension == "pdf") {
              context.getString(
                R.string.unsupported_format,
                opened.sourceExtension.ifBlank { "" },
              )
            }
            val noteStore = ProjectNoteStore(context.contentResolver, opened.notesUri)
            val noteText = noteStore.read()
            val supportsNoteWrite = noteStore.supportsWrite()
            val projectPenStore = ProjectPenStore(
              context.contentResolver,
              opened.sourceDirectoryUri,
              opened.annotationsUri,
            )
            val loadedPenPages = PenPageStore().apply {
              loadAnnotationsMap(
                mapOf(PenSide.DOCUMENT.key to projectPenStore.readPages(PenSide.DOCUMENT)),
              )
            }
            val supportsPenWrite = projectPenStore.supportsWrite()
            val pdf = PdfDocumentSession.open(context.contentResolver, opened.sourceUri)
            ReaderProjectData(
              project = opened,
              noteText = noteText,
              renderer = pdf,
              noteWritable = writable && supportsNoteWrite,
              penStore = projectPenStore,
              penPageStore = loadedPenPages,
              penWritable = writable && supportsPenWrite,
            )
          }
          renderer = loaded.renderer
          project = loaded.project
          notesMarkdown = loaded.noteText
          penStore = loaded.penStore
          penPageStore = loaded.penPageStore
          penStrokes = loaded.penPageStore.getItems(PenSide.DOCUMENT, 1)
          penWritable = loaded.penWritable
          failedPenSave = null
          penStatus = if (loaded.penWritable) null else context.getString(R.string.pen_read_only)
          val firstPage = NotePage.pageAt(loaded.noteText, 1)
          noteDraft = firstPage
          noteBaseRevision = PageNotes.revision(firstPage)
          noteWritable = loaded.noteWritable
          noteStatus = if (noteWritable) null else context.getString(R.string.read_only_project)
          ocrResult = null
          showOcrResult = false
          pageIndex = 0
        } catch (error: Exception) {
          errorMessage = context.getString(R.string.select_project_error, error.message ?: "")
        } finally {
          isOpening = false
        }
      }
    }
  }

  val rendererToClose = renderer
  DisposableEffect(rendererToClose) {
    onDispose { rendererToClose?.close() }
  }
  DisposableEffect(Unit) {
    onDispose { pageBitmap?.recycle() }
  }
  val ocrEngineToClose = ocrEngine
  DisposableEffect(ocrEngineToClose) {
    onDispose {
      ocrEngineToClose?.let { engine ->
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { engine.close() }
      }
    }
  }

  LaunchedEffect(notesMarkdown, pageIndex) {
    val markdown = NotePage.pageAt(notesMarkdown, pageIndex + 1)
    noteDraft = markdown
    noteBaseRevision = PageNotes.revision(markdown)
  }

  LaunchedEffect(pageIndex) {
    noteStatus = null
  }

  LaunchedEffect(pageIndex, penPageStore) {
    penStrokes = penPageStore.getItems(PenSide.DOCUMENT, pageIndex + 1)
  }

  LaunchedEffect(renderer, pageIndex) {
    val activeRenderer = renderer ?: return@LaunchedEffect
    isRendering = true
    try {
      val rendered = withContext(Dispatchers.IO) { activeRenderer.render(pageIndex) }
      if (renderer === activeRenderer) {
        pageBitmap?.recycle()
        pageBitmap = rendered
        errorMessage = null
      } else {
        rendered.recycle()
      }
    } catch (error: Exception) {
      errorMessage = error.message ?: context.getString(R.string.page_render_failed)
    } finally {
      isRendering = false
    }
  }

  Column(modifier = Modifier.fillMaxSize()) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = project?.name ?: "pagenote",
        modifier = Modifier.weight(1f),
        style = MaterialTheme.typography.titleMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Button(
        modifier = Modifier.testTag("open_project"),
        onClick = {
          scope.launch {
            if (hasUnsavedChanges && !saveCurrentNote()) return@launch
            projectPicker.launch(null)
          }
        },
        enabled = !isOpening && !isSaving && !isPenSaving && failedPenSave == null && !isOcrRunning,
      ) {
        Text(stringResource(if (isOpening) R.string.opening_project else R.string.open_project))
      }
      TextButton(
        modifier = Modifier.testTag("settings_action"),
        onClick = { showSettings = true },
      ) {
        Text(stringResource(R.string.settings_action))
      }
    }

    errorMessage?.let { message ->
      Text(
        text = message,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
      )
    }

    val activeRenderer = renderer
    val activeProject = project
    if (activeRenderer != null && activeProject != null) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Button(
          onClick = { scope.launch { changePage((pageIndex - 1).coerceAtLeast(0)) } },
          enabled = pageIndex > 0 && !isRendering && !isSaving && !isPenSaving &&
            failedPenSave == null && !isOcrRunning,
        ) {
          Text(stringResource(R.string.previous_page))
        }
        Text(stringResource(R.string.page_count, pageIndex + 1, activeRenderer.pageCount))
        Button(
          onClick = ::recognizeCurrentPage,
          enabled = !isRendering && !isOcrRunning,
        ) {
          Text(stringResource(if (isOcrRunning) R.string.recognizing else R.string.ocr))
        }
        Button(
          onClick = {
            scope.launch {
              changePage((pageIndex + 1).coerceAtMost(activeRenderer.pageCount - 1))
            }
          },
          enabled = pageIndex < activeRenderer.pageCount - 1 &&
            !isRendering && !isSaving && !isPenSaving && failedPenSave == null && !isOcrRunning,
        ) {
          Text(stringResource(R.string.next_page))
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
          .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        TextButton(
          modifier = Modifier.testTag("page_tutor_action"),
          onClick = {
            pageTutorResult = null
            pageTutorError = null
            showPageTutor = true
          },
          enabled = !isRendering && !isSaving && !isPageTutorRunning && !isOcrRunning,
        ) { Text(stringResource(R.string.page_tutor_action)) }
        TextButton(
          onClick = { applyViewportZoom(0.8f) },
          enabled = viewportTransform.scale > PdfViewportTransform.MIN_SCALE,
        ) { Text(stringResource(R.string.zoom_out)) }
        Text("${(viewportTransform.scale * 100f).roundToInt()}%")
        TextButton(
          onClick = { applyViewportZoom(1.25f) },
          enabled = viewportTransform.scale < PdfViewportTransform.MAX_SCALE,
        ) { Text(stringResource(R.string.zoom_in)) }
        TextButton(onClick = ::fitViewportWidth) { Text(stringResource(R.string.fit_width)) }
        TextButton(onClick = { viewportTransform = PdfViewportTransform.DEFAULT }) {
          Text(stringResource(R.string.fit_page))
        }
        listOf(
          PenCanvasTool.PEN to R.string.pen,
          PenCanvasTool.HIGHLIGHTER to R.string.highlighter,
          PenCanvasTool.ERASER to R.string.eraser,
        ).forEach { (tool, label) ->
          if (penTool == tool) {
            Button(onClick = { penTool = tool }, enabled = penWritable) {
              Text(stringResource(label))
            }
          } else {
            TextButton(onClick = { penTool = tool }, enabled = penWritable) {
              Text(stringResource(label))
            }
          }
        }
        TextButton(
          onClick = ::undoPenPage,
          enabled = penWritable && penPageStore.canUndo(PenSide.DOCUMENT, pageIndex + 1),
        ) { Text(stringResource(R.string.undo)) }
        TextButton(
          onClick = ::redoPenPage,
          enabled = penWritable && penPageStore.canRedo(PenSide.DOCUMENT, pageIndex + 1),
        ) { Text(stringResource(R.string.redo)) }
        TextButton(
          onClick = ::clearPenPage,
          enabled = penWritable && penStrokes.isNotEmpty(),
        ) { Text(stringResource(R.string.clear_page)) }
      }
      penStatus?.let { status ->
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = status,
            modifier = Modifier.weight(1f),
            color = if (isReaderErrorStatus(status)) {
              MaterialTheme.colorScheme.error
            } else {
              MaterialTheme.colorScheme.onSurfaceVariant
            },
            style = MaterialTheme.typography.labelMedium,
          )
          failedPenSave?.let { (targetPage, strokes) ->
            TextButton(
              onClick = { scope.launch { savePenPage(targetPage, strokes) } },
              enabled = !isPenSaving,
            ) { Text(stringResource(R.string.retry)) }
          }
        }
      }
    }

    if (activeRenderer != null && activeProject != null) {
      BoxWithConstraints(
        modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
      ) {
        if (maxWidth >= 840.dp) {
          val (pdfPaneWeight, notePaneWeight) = readerPaneWeights(settings.leftPercent)
          Row(modifier = Modifier.fillMaxSize()) {
            PdfPageViewport(
              bitmap = pageBitmap,
              sourceName = activeProject.sourceName,
              page = pageIndex,
              loading = isRendering,
              strokes = penStrokes,
              tool = penTool,
              inputEnabled = penWritable,
              onStrokeAdded = ::applyPenStroke,
              onStrokeErased = ::erasePenStroke,
              transform = viewportTransform,
              onTransformChanged = { viewportTransform = it },
              onBoundsChanged = { viewportBounds = it },
              modifier = Modifier.weight(pdfPaneWeight).fillMaxHeight(),
            )
            Box(
              modifier = Modifier.fillMaxHeight().padding(horizontal = 5.dp)
                .width(1.dp).background(MaterialTheme.colorScheme.outlineVariant),
            )
            NotePanel(
              page = pageIndex + 1,
              markdown = noteDraft,
              dirty = hasUnsavedChanges,
              writable = noteWritable,
              saving = isSaving,
              status = noteStatus,
              onMarkdownChange = { noteDraft = it },
              onSave = { scope.launch { saveCurrentNote() } },
              onReload = ::reloadCurrentNote,
              animationEnabled = settings.noteAnimation,
              modifier = Modifier.weight(notePaneWeight).fillMaxHeight(),
            )
          }
        } else {
          Column(modifier = Modifier.fillMaxSize()) {
            PdfPageViewport(
              bitmap = pageBitmap,
              sourceName = activeProject.sourceName,
              page = pageIndex,
              loading = isRendering,
              strokes = penStrokes,
              tool = penTool,
              inputEnabled = penWritable,
              onStrokeAdded = ::applyPenStroke,
              onStrokeErased = ::erasePenStroke,
              transform = viewportTransform,
              onTransformChanged = { viewportTransform = it },
              onBoundsChanged = { viewportBounds = it },
              modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            Box(
              modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
                .height(1.dp).background(MaterialTheme.colorScheme.outlineVariant),
            )
            NotePanel(
              page = pageIndex + 1,
              markdown = noteDraft,
              dirty = hasUnsavedChanges,
              writable = noteWritable,
              saving = isSaving,
              status = noteStatus,
              onMarkdownChange = { noteDraft = it },
              onSave = { scope.launch { saveCurrentNote() } },
              onReload = ::reloadCurrentNote,
              animationEnabled = settings.noteAnimation,
              modifier = Modifier.weight(1f).fillMaxWidth(),
            )
          }
        }
      }
    } else {
      Box(
        modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
      ) {
        if (isOpening) {
          Text(stringResource(R.string.opening_project_message), style = MaterialTheme.typography.bodyLarge)
        } else {
          Text(stringResource(R.string.choose_project_message), style = MaterialTheme.typography.bodyLarge)
        }
      }
    }
  }

  if (showOcrResult) {
    ocrResult?.let { result ->
      OcrResultDialog(
        page = ocrResultPage + 1,
        result = result,
        onDismiss = { showOcrResult = false },
      )
    }
  }
  if (showSettings) {
    ReaderSettingsDialog(
      settings = settings,
      onDismiss = { showSettings = false },
      onConfigureAgent = {
        showSettings = false
        showAgentModelSettings = true
      },
      onSave = settingsStore::save,
    )
  }
  if (showPageTutor && renderer != null && project != null) {
    PageTutorDialog(
      page = pageIndex + 1,
      instruction = pageTutorInstruction,
      isRunning = isPageTutorRunning,
      isSaving = isSaving,
      errorMessage = pageTutorError,
      preview = pageTutorResult,
      noteWritable = noteWritable,
      onInstructionChange = { pageTutorInstruction = it },
      onExplain = ::explainCurrentPage,
      onAppend = ::appendPageTutorResult,
      onDismiss = {
        showPageTutor = false
        pageTutorResult = null
        pageTutorError = null
      },
    )
  }
  if (showAgentModelSettings) {
    AgentModelSettingsDialog(
      store = modelSettingsStore,
      onDismiss = { showAgentModelSettings = false },
    )
  }
}

private fun isReaderErrorStatus(status: String): Boolean =
  status.contains("冲突") || status.contains("失败") || status.contains("只读") ||
    status.contains("conflict", ignoreCase = true) || status.contains("fail", ignoreCase = true) ||
    status.contains("read-only", ignoreCase = true) || status.contains("not saved", ignoreCase = true)

@Composable
private fun PdfPageViewport(
  bitmap: Bitmap?,
  sourceName: String,
  page: Int,
  loading: Boolean,
  strokes: List<PenStroke>,
  tool: PenCanvasTool,
  inputEnabled: Boolean,
  onStrokeAdded: (PenStroke) -> Unit,
  onStrokeErased: (Int) -> Unit,
  transform: PdfViewportTransform,
  onTransformChanged: (PdfViewportTransform) -> Unit,
  onBoundsChanged: (PdfViewportBounds) -> Unit,
  modifier: Modifier = Modifier,
) {
  val latestTransform by rememberUpdatedState(transform)
  val latestOnTransformChanged by rememberUpdatedState(onTransformChanged)

  BoxWithConstraints(
    modifier = modifier.clipToBounds(),
    contentAlignment = Alignment.Center,
  ) {
    if (bitmap != null) {
      val pageAspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
      val viewportAspectRatio = maxWidth.value / maxHeight.value.coerceAtLeast(1f)
      val contentWidth = if (viewportAspectRatio > pageAspectRatio) {
        maxHeight * pageAspectRatio
      } else {
        maxWidth
      }
      val contentHeight = contentWidth / pageAspectRatio
      val density = LocalDensity.current
      val bounds = with(density) {
        PdfViewportBounds(
          viewportWidthPx = maxWidth.toPx(),
          viewportHeightPx = maxHeight.toPx(),
          pageWidthPx = contentWidth.toPx(),
          pageHeightPx = contentHeight.toPx(),
        )
      }
      LaunchedEffect(page, bounds) { onBoundsChanged(bounds) }

      Box(
        modifier = Modifier.fillMaxSize().pointerInput(page, bounds) {
          awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            do {
              val event = awaitPointerEvent()
              val touchChanges = event.changes.filter {
                it.type == PointerType.Touch && it.pressed && it.previousPressed
              }
              val pointers = touchChanges.map { change ->
                PdfViewportPointer(change.id.value, change.position.x, change.position.y)
              }
              val previousPointers = touchChanges.map { change ->
                PdfViewportPointer(
                  change.id.value,
                  change.previousPosition.x,
                  change.previousPosition.y,
                )
              }
              val gesture = PdfViewportTransform.calculateTwoPointerGesture(
                previous = previousPointers,
                current = pointers,
              )
              if (gesture != null) {
                latestOnTransformChanged(
                  PdfViewportTransform.applyGesture(
                    current = latestTransform,
                    viewportWidthPx = bounds.viewportWidthPx,
                    viewportHeightPx = bounds.viewportHeightPx,
                    pageWidthPx = bounds.pageWidthPx,
                    pageHeightPx = bounds.pageHeightPx,
                    centroidX = gesture.centroidX,
                    centroidY = gesture.centroidY,
                    zoomChange = gesture.zoomChange,
                    panX = gesture.panX,
                    panY = gesture.panY,
                  ),
                )
                touchChanges.forEach { it.consume() }
              }
            } while (event.changes.any { it.pressed })
          }
        },
        contentAlignment = Alignment.Center,
      ) {
        Box(
          modifier = Modifier.width(contentWidth).height(contentHeight).graphicsLayer {
            scaleX = transform.scale
            scaleY = transform.scale
            translationX = transform.translationX
            translationY = transform.translationY
          },
          contentAlignment = Alignment.Center,
        ) {
          Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = stringResource(R.string.page_image_description, sourceName, page + 1),
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
          )
          AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext -> PenCanvasView(viewContext) },
            update = { view ->
              view.strokes = strokes
              view.selectedTool = tool
              view.inputEnabled = inputEnabled
              view.onStrokeAdded = onStrokeAdded
              view.onStrokeErased = onStrokeErased
            },
          )
        }
      }
    } else if (loading) {
      Text(stringResource(R.string.loading_page), style = MaterialTheme.typography.bodyLarge)
    }
  }
}

@Composable
private fun NotePanel(
  page: Int,
  markdown: String,
  dirty: Boolean,
  writable: Boolean,
  saving: Boolean,
  status: String?,
  onMarkdownChange: (String) -> Unit,
  onSave: () -> Unit,
  onReload: () -> Unit,
  animationEnabled: Boolean,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val markwon = remember(context) {
    Markwon.builder(context).usePlugin(TablePlugin.create(context)).build()
  }
  val markwonEditor = remember(markwon) { MarkwonEditor.create(markwon) }
  val executor = remember { Executors.newSingleThreadExecutor() }
  var preview by remember { mutableStateOf(false) }
  val contentColor = MaterialTheme.colorScheme.onSurface
  val outlineColor = MaterialTheme.colorScheme.outline.toArgb()
  val contentColorInt = contentColor.toArgb()

  DisposableEffect(executor) {
    onDispose { executor.shutdownNow() }
  }

  Column(modifier = modifier.padding(8.dp)) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
      NotePanelHeader(
        page = page,
        layout = notePanelHeaderLayout(maxWidth.value),
        saving = saving,
        writable = writable,
        dirty = dirty,
        onReload = onReload,
        onSave = onSave,
      )
    }
    TabRow(selectedTabIndex = if (preview) 1 else 0) {
      Tab(selected = !preview, onClick = { preview = false }, text = { Text(stringResource(R.string.note_edit)) })
      Tab(selected = preview, onClick = { preview = true }, text = { Text(stringResource(R.string.note_preview)) })
    }
    if (!writable) {
      Text(
        stringResource(R.string.read_only_project),
        modifier = Modifier.padding(top = 5.dp),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.labelMedium,
      )
    }
    status?.let {
      Text(
        it,
        modifier = Modifier.padding(top = 5.dp),
        color = if (isReaderErrorStatus(it)) {
          MaterialTheme.colorScheme.error
        } else {
          MaterialTheme.colorScheme.onSurfaceVariant
        },
        style = MaterialTheme.typography.labelMedium,
      )
    }
    AnimatedContent(
      targetState = ReaderNoteContent(page, markdown, preview),
      contentKey = { it.page },
      transitionSpec = {
        val duration = notePageTransitionDurationMillis(animationEnabled)
        fadeIn(animationSpec = tween(duration)) togetherWith fadeOut(animationSpec = tween(duration))
      },
      modifier = Modifier.fillMaxSize().padding(top = 8.dp),
      label = "page-note-transition",
    ) { content ->
      key(content.page, content.preview) {
        if (content.preview) {
          AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
              TextView(viewContext).apply {
                textSize = 16f
                gravity = Gravity.TOP or Gravity.START
                setTextColor(contentColorInt)
                setPadding(12, 10, 12, 10)
              }
            },
            update = { view ->
              view.setTextColor(contentColorInt)
              markwon.setMarkdown(view, content.markdown)
            },
          )
        } else {
          AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
              EditText(viewContext).apply {
                gravity = Gravity.TOP or Gravity.START
                inputType = InputType.TYPE_CLASS_TEXT or
                  InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                  InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
                textSize = 16f
                setTextColor(contentColorInt)
                backgroundTintList = ColorStateList.valueOf(outlineColor)
                isEnabled = writable && !saving && content.page == page
                setPadding(12, 10, 12, 10)
                setText(content.markdown)
                setSelection(text.length)
                addTextChangedListener(
                  MarkwonEditorTextWatcher.withPreRender(markwonEditor, executor, this),
                )
                addTextChangedListener(object : TextWatcher {
                  override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                  override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                  override fun afterTextChanged(editable: Editable?) {
                    if (content.page == page) onMarkdownChange(editable?.toString().orEmpty())
                  }
                })
              }
            },
            update = { editText ->
              editText.setTextColor(contentColorInt)
              editText.backgroundTintList = ColorStateList.valueOf(outlineColor)
              editText.isEnabled = writable && !saving && content.page == page
              if (editText.text.toString() != content.markdown) {
                editText.setText(content.markdown)
                editText.setSelection(editText.text.length)
              }
            },
          )
        }
      }
    }
  }
}

private data class ReaderNoteContent(val page: Int, val markdown: String, val preview: Boolean)

@Composable
private fun NotePanelHeader(
  page: Int,
  layout: NotePanelHeaderLayout,
  saving: Boolean,
  writable: Boolean,
  dirty: Boolean,
  onReload: () -> Unit,
  onSave: () -> Unit,
) {
  val actions: @Composable (Boolean, Modifier) -> Unit = { vertical, modifier ->
    NotePanelHeaderActions(
      saving = saving,
      writable = writable,
      dirty = dirty,
      vertical = vertical,
      onReload = onReload,
      onSave = onSave,
      modifier = modifier,
    )
  }

  when (layout) {
    NotePanelHeaderLayout.INLINE -> Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        stringResource(R.string.note_page_title, page),
        modifier = Modifier.weight(1f),
        style = MaterialTheme.typography.titleSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      actions(false, Modifier)
    }
    NotePanelHeaderLayout.STACKED_ACTIONS -> Column(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Text(stringResource(R.string.note_page_title, page), style = MaterialTheme.typography.titleSmall)
      Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        actions(false, Modifier)
      }
    }
    NotePanelHeaderLayout.STACKED_CONTROLS -> Column(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Text(stringResource(R.string.note_page_title, page), style = MaterialTheme.typography.titleSmall)
      actions(true, Modifier.fillMaxWidth())
    }
  }
}

@Composable
private fun NotePanelHeaderActions(
  saving: Boolean,
  writable: Boolean,
  dirty: Boolean,
  vertical: Boolean,
  onReload: () -> Unit,
  onSave: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val reloadButton: @Composable (Modifier) -> Unit = { buttonModifier ->
    Button(onClick = onReload, enabled = !saving, modifier = buttonModifier) {
      Text(stringResource(R.string.reload))
    }
  }
  val saveButton: @Composable (Modifier) -> Unit = { buttonModifier ->
    Button(
      onClick = onSave,
      enabled = writable && dirty && !saving,
      modifier = buttonModifier,
    ) {
      Text(stringResource(if (saving) R.string.saving else R.string.save))
    }
  }

  if (vertical) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
      reloadButton(Modifier.fillMaxWidth())
      saveButton(Modifier.fillMaxWidth())
    }
  } else {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      reloadButton(Modifier)
      saveButton(Modifier)
    }
  }
}

@Composable
private fun OcrResultDialog(
  page: Int,
  result: OcrResult,
  onDismiss: () -> Unit,
) {
  val needsReview = OcrResultPolicy.needsVisualReview(result)
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.ocr_page_title, page)) },
    text = {
      Column(
        modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(stringResource(R.string.ocr_status, result.status, result.engine))
        if (needsReview) {
          Text(stringResource(R.string.ocr_review), color = MaterialTheme.colorScheme.error)
        }
        result.warnings.forEach { warning ->
          Text(warning, color = MaterialTheme.colorScheme.error)
        }
        if (result.blocks.isEmpty()) {
          Text(stringResource(R.string.ocr_empty))
        }
        result.blocks.forEachIndexed { index, block ->
          Text("${index + 1}. ${block.text}")
          Text(
            stringResource(
              R.string.ocr_block_confidence,
              (block.confidence * 100).toInt(),
              block.x.toInt(),
              block.y.toInt(),
              block.width.toInt(),
              block.height.toInt(),
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
  )
}

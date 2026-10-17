package io.pagenote.android

import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import io.noties.markwon.image.ImagesPlugin
import io.pagenote.agent.MissingModelConfigException
import io.pagenote.agent.OkHttpChatClient
import io.pagenote.agent.PageRequest
import io.pagenote.agent.PageTutor
import io.pagenote.agent.PageTutorRequest
import io.pagenote.agent.PageTutorResult
import io.pagenote.agent.AgentEvent
import io.pagenote.agent.AgentRunner
import io.pagenote.agent.LocalToolDispatcher
import io.pagenote.agent.ModelEndpoint
import io.pagenote.agent.SerialPages
import io.pagenote.core.project.PageNotes
import io.pagenote.core.project.NoteVersion
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
  val renderer: PdfDocumentSession?,
  val markdownSource: String?,
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
  var markdownSource by remember { mutableStateOf<String?>(null) }
  val isMarkdownReading = project != null && renderer == null && markdownSource != null
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
  var readerOcrState by remember { mutableStateOf(ReaderOcrState()) }
  var regionOcrResult by remember { mutableStateOf<OcrResult?>(null) }
  var isOpening by remember { mutableStateOf(false) }
  var showSettings by remember { mutableStateOf(false) }
  var showAgentModelSettings by remember { mutableStateOf(false) }
  var showPageTutor by remember { mutableStateOf(false) }
  var pageTutorInstruction by remember {
    mutableStateOf(context.getString(R.string.page_tutor_default_instruction))
  }
  var pageTutorResult by remember { mutableStateOf<PageTutorResult?>(null) }
  var pageTutorResultInstruction by remember { mutableStateOf<String?>(null) }
  var pageTutorError by remember { mutableStateOf<String?>(null) }
  var isPageTutorRunning by remember { mutableStateOf(false) }
  val agentRunner = remember { AgentRunner(OkHttpChatClient()) }
  var showAgentTask by remember { mutableStateOf(false) }
  var agentInstruction by remember { mutableStateOf("") }
  var agentPageRange by remember { mutableStateOf("") }
  var agentReport by remember { mutableStateOf<SerialPages.Report?>(null) }
  var agentEvent by remember { mutableStateOf<AgentEvent?>(null) }
  var agentError by remember { mutableStateOf<String?>(null) }
  var isAgentRunning by remember { mutableStateOf(false) }
  var isRendering by remember { mutableStateOf(false) }
  var isSaving by remember { mutableStateOf(false) }
  val isPenSaving = pendingPenSaves > 0
  var errorMessage by remember { mutableStateOf<String?>(null) }
  var showNoteHistory by remember { mutableStateOf(false) }
  var annotateMode by remember { mutableStateOf(false) }
  var annotateDraft by remember { mutableStateOf<Pair<Double, Double>?>(null) }
  var annotateText by remember { mutableStateOf("") }
  var annotateIsRegion by remember { mutableStateOf(false) }
  var showAnnotations by remember { mutableStateOf(false) }
  var showReviews by remember { mutableStateOf(false) }
  var showHandoff by remember { mutableStateOf(false) }
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
      val (result, recoveryCopyName) = withContext(Dispatchers.IO) {
        val store = ProjectNoteStore(context.contentResolver, activeProject.notesUri)
        val latest = store.read()
        val edited = PageNoteEditor.replacePage(
          latestMarkdown = latest,
          page = targetPage,
          replacement = draftToSave,
          expectedRevision = expectedRevision,
        )
        val retainedCopy = store.write(edited.markdown, expectedOriginal = latest)
        edited to retainedCopy
      }
      notesMarkdown = result.markdown
      noteBaseRevision = result.pageRevision
      noteStatus = recoveryCopyName?.let { name ->
        context.getString(R.string.note_saved_recovery_copy, name)
      } ?: context.getString(R.string.note_saved)
      withContext(Dispatchers.IO) {
        runCatching {
          NoteHistoryMirror.snapshotPage(
            context.filesDir, activeProject.manifest.id, targetPage,
            NotePage.pageAt(result.markdown, targetPage),
          )
        }
      }
      true
    } catch (conflict: PageNoteConflictException) {
      noteStatus = conflict.message
      false
    } catch (conflict: NoteDocumentConflictException) {
      noteStatus = conflict.recoveryCopyName?.let { name ->
        context.getString(R.string.note_conflict_recovery_copy, name)
      } ?: context.getString(R.string.note_conflict)
      false
    } catch (recovery: NoteRecoveryCopyRetainedException) {
      noteStatus = context.getString(R.string.note_recovery_copy_retained, recovery.recoveryCopyName)
      false
    } catch (recovery: NoteWriteRecoveryException) {
      noteStatus = recovery.recoveryCopyName?.let { name ->
        context.getString(
          if (recovery.rollbackSucceeded) R.string.note_write_recovery_rollback
          else R.string.note_write_recovery_failed,
          name,
        )
      } ?: context.getString(R.string.note_save_failed, recovery.message ?: "")
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
    if (readerOcrState.inputLocked) return
    if (hasUnsavedChanges && !saveCurrentNote()) return
    noteStatus = null
    showOcrResult = false
    pageIndex = nextPage
  }

  fun recognizeCurrentPage() {
    if (readerOcrState.inputLocked) return
    if (isMarkdownReading) return
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

  fun recognizeSelectedRegion(request: ReaderOcrState) {
    if (isMarkdownReading) return
    val activeRenderer = renderer ?: return
    val activeProject = project ?: return
    val targetPage = request.pageIndex ?: return
    val selection = request.selection ?: return
    val documentId = activeProject.manifest.id
    scope.launch {
      var input: Bitmap? = null
      suspend fun finish(result: OcrResult) {
        if (renderer === activeRenderer && project?.manifest?.id == documentId &&
          pageIndex == targetPage && readerOcrState.mode == ReaderOcrMode.RECOGNIZING
        ) {
          regionOcrResult = result
          readerOcrState = readerOcrState.showResult()
        }
      }
      try {
        val engine = ocrEngine ?: OfflineOcrEngine.create(context).also { ocrEngine = it }
        val renderedPage = withContext(Dispatchers.IO) { activeRenderer.renderWithMetadata(targetPage) }
        input = renderedPage.bitmap
        val pageImage = input ?: error("OCR page image is unavailable")
        val regionEngine = selection.toEngineRegion()
        val processor = RegionOcrProcessor(
          ProjectPreparedCache(SafPreparedDocumentAccess(context.contentResolver)),
        )
        val outcome = processor.recognize(
          srcDirectoryUri = activeProject.sourceDirectoryUri.toString(),
          documentId = documentId,
          page = targetPage + 1,
          sourceRevision = activeRenderer.sourceRevision,
          selection = selection,
          imageWidth = pageImage.width,
          imageHeight = pageImage.height,
          renderScale = renderedPage.scale,
          engine = OcrResultMapper.ENGINE,
          notCachedWarning = context.getString(R.string.ocr_not_cached),
          recognize = {
            // Scope the recognition timeout to inference only so a slow SAF
            // write cannot discard a completed OCR result.
            withTimeout(OcrPolicy.TIMEOUT_MS) {
              withContext(Dispatchers.Default) { engine.recognize(pageImage, regionEngine) }
            }
          },
          encodeImages = {
            val pixels = regionEngine.toPixelRect(pageImage.width, pageImage.height)
            val regionImage = Bitmap.createBitmap(
              pageImage, pixels.left, pixels.top, pixels.width, pixels.height,
            )
            try {
              PreparedOcrImages(pageImage.toPngBytes(), regionImage.toPngBytes())
            } finally {
              regionImage.recycle()
            }
          },
        )
        finish(outcome.result)
      } catch (timeout: TimeoutCancellationException) {
        finish(OcrResult(
          engine = OcrResultMapper.ENGINE,
          status = "failed",
          warnings = listOf(context.getString(R.string.ocr_timeout)),
        ))
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (error: Exception) {
        finish(OcrResult(
          engine = OcrResultMapper.ENGINE,
          status = "failed",
          warnings = listOf(error.message ?: context.getString(R.string.offline_ocr_failed)),
        ))
      } finally {
        input?.let { if (!it.isRecycled) it.recycle() }
      }
    }
  }

  fun explainCurrentPage(instruction: String) {
    val activeProject = project ?: return
    val targetPageIndex = pageIndex
    val activeMarkdown = markdownSource
    val activeRenderer = renderer
    if (activeRenderer == null && activeMarkdown == null) return
    scope.launch {
      pageTutorInstruction = instruction
      isPageTutorRunning = true
      pageTutorError = null
      pageTutorResult = null
      pageTutorResultInstruction = null
      try {
        val endpoint = withContext(Dispatchers.IO) { modelSettingsStore.resolveEndpoint() }
        if (hasUnsavedChanges && !saveCurrentNote()) {
          pageTutorError = noteStatus ?: context.getString(R.string.note_save_failed, "")
          return@launch
        }

        val currentNote = noteDraft
        val expectedRevision = noteBaseRevision
        val totalPages = if (activeMarkdown != null) {
          MarkdownSourcePages.pageCount(activeMarkdown, notesMarkdown)
        } else {
          activeRenderer!!.pageCount
        }
        val currentSource: PageTutorSourceText
        val neighbors: List<PageRequest.PageMaterial>
        if (activeMarkdown != null) {
          val text = MarkdownSourcePages.pageAt(activeMarkdown, targetPageIndex + 1)
          if (text.isBlank()) {
            throw PageTutorSourceException("Markdown 源本页为空。")
          }
          currentSource = PageTutorSourceText(text = text, usedOcr = false)
          neighbors = listOf(targetPageIndex - 1, targetPageIndex + 1)
            .filter { it in 0 until totalPages }
            .mapNotNull { neighborIndex ->
              val neighborText = MarkdownSourcePages.pageAt(activeMarkdown, neighborIndex + 1)
              if (neighborText.isBlank()) null else PageRequest.PageMaterial(
                page = neighborIndex + 1,
                text = neighborText,
              )
            }
        } else {
          val pageSourceLoader = PageTutorSourceLoader(
            nativeText = { page ->
              withContext(Dispatchers.IO) { activeRenderer!!.extractText(page) }
            },
            ocrText = { page ->
              val engine = ocrEngine ?: OfflineOcrEngine.create(context).also { ocrEngine = it }
              val bitmap = withContext(Dispatchers.IO) { activeRenderer!!.render(page) }
              try {
                withContext(Dispatchers.Default) { engine.recognize(bitmap) }
              } finally {
                bitmap.recycle()
              }
            },
          )
          val loaded = withTimeout(OcrPolicy.TIMEOUT_MS) {
            val current = pageSourceLoader.loadCurrent(targetPageIndex)
            val adjacent = mutableListOf<PageRequest.PageMaterial>()
            listOf(targetPageIndex - 1, targetPageIndex + 1)
              .filter { it in 0 until activeRenderer!!.pageCount }
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
          currentSource = loaded.first
          neighbors = loaded.second
        }
        val result = pageTutor.explain(
          endpoint = endpoint,
          request = PageTutorRequest(
            documentId = activeProject.manifest.id,
            page = targetPageIndex + 1,
            totalPages = totalPages,
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
          pageTutorResultInstruction = instruction
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
        val (edited, recoveryCopyName) = withContext(Dispatchers.IO) {
          val store = ProjectNoteStore(context.contentResolver, activeProject.notesUri)
          val latest = store.read()
          val heading = context.getString(R.string.page_tutor_note_heading)
          val result = PageNoteEditor.appendPage(
            latestMarkdown = latest,
            page = preview.page,
            addition = "$heading\n\n${preview.markdown}",
            expectedRevision = preview.noteRevision,
          )
          val retainedCopy = store.write(result.markdown, expectedOriginal = latest)
          result to retainedCopy
        }
        notesMarkdown = edited.markdown
        noteDraft = NotePage.pageAt(edited.markdown, preview.page)
        noteBaseRevision = edited.pageRevision
        noteStatus = recoveryCopyName?.let { name ->
          context.getString(R.string.page_tutor_saved_recovery_copy, name)
        } ?: context.getString(R.string.page_tutor_saved)
        pageTutorResult = null
        pageTutorResultInstruction = null
        showPageTutor = false
      } catch (conflict: PageNoteConflictException) {
        pageTutorError = context.getString(R.string.page_tutor_conflict)
      } catch (conflict: NoteDocumentConflictException) {
        pageTutorError = conflict.recoveryCopyName?.let { name ->
          context.getString(R.string.page_tutor_conflict_recovery_copy, name)
        } ?: context.getString(R.string.page_tutor_conflict)
      } catch (recovery: NoteRecoveryCopyRetainedException) {
        pageTutorError = context.getString(R.string.page_tutor_recovery_copy_retained, recovery.recoveryCopyName)
      } catch (recovery: NoteWriteRecoveryException) {
        pageTutorError = if (recovery.rollbackSucceeded) {
          recovery.recoveryCopyName?.let { name ->
            context.getString(R.string.page_tutor_recovery_copy_rollback, name)
          } ?: context.getString(R.string.page_tutor_append_failed)
        } else {
          recovery.recoveryCopyName?.let { name ->
            context.getString(R.string.page_tutor_recovery_copy_saved, name)
          } ?: context.getString(R.string.page_tutor_recovery_copy_unavailable)
        }
      } catch (error: Exception) {
        pageTutorError = "${context.getString(R.string.page_tutor_append_failed)} ${error.message.orEmpty()}"
      } finally {
        isSaving = false
      }
    }
  }

  suspend fun executeAgentPage(
    activeProject: OpenedProject,
    activeMarkdown: String?,
    activeRenderer: PdfDocumentSession?,
    targetPage: Int,
    instruction: String,
    endpoint: ModelEndpoint,
  ): AgentEvent {
    val targetPageIndex = targetPage - 1
    val notesSnapshot = withContext(Dispatchers.IO) {
      ProjectNoteStore(context.contentResolver, activeProject.notesUri).read()
    }
    val currentNote = NotePage.pageAt(notesSnapshot, targetPage)
    val expectedRevision = PageNotes.revision(currentNote)
    val totalPages = if (activeMarkdown != null) {
      MarkdownSourcePages.pageCount(activeMarkdown, notesSnapshot)
    } else {
      activeRenderer!!.pageCount
    }
    require(targetPage in 1..totalPages) { "page out of range" }
    val sourceText = if (activeMarkdown != null) {
      MarkdownSourcePages.pageAt(activeMarkdown, targetPage)
    } else {
      val native = withContext(Dispatchers.IO) { activeRenderer!!.extractText(targetPageIndex) }
      native.fullText
    }
    if (sourceText.isBlank()) {
      throw PageTutorSourceException(context.getString(R.string.page_tutor_source_failed))
    }
    val neighbors = if (activeMarkdown != null) {
      listOf(targetPageIndex - 1, targetPageIndex + 1)
        .filter { it in 0 until totalPages }
        .mapNotNull { neighborIndex ->
          val text = MarkdownSourcePages.pageAt(activeMarkdown, neighborIndex + 1)
          if (text.isBlank()) null else text to (neighborIndex + 1)
        }
    } else {
      listOf(targetPageIndex - 1, targetPageIndex + 1)
        .filter { it in 0 until activeRenderer!!.pageCount }
        .mapNotNull { neighborIndex ->
          val native = withContext(Dispatchers.IO) { activeRenderer!!.extractText(neighborIndex) }
          if (native.fullText.isBlank()) null else native.fullText to (neighborIndex + 1)
        }
    }
    val input = AgentTaskInput.build(
      documentId = activeProject.manifest.id,
      page = targetPage,
      totalPages = totalPages,
      instruction = instruction,
      noteMarkdown = currentNote,
      noteRevision = expectedRevision,
      sourceText = sourceText,
      neighbors = neighbors,
    )
    val handlers = ProjectAgentTools.handlers(
      context.contentResolver,
      activeProject,
      activeRenderer,
      targetPage,
    )
    return agentRunner.run(input.task, endpoint, input.messages, LocalToolDispatcher(handlers))
  }

  fun runAgentTask(instruction: String) {
    val activeProject = project ?: return
    val targetPageIndex = pageIndex
    val activeMarkdown = markdownSource
    val activeRenderer = renderer
    if (activeRenderer == null && activeMarkdown == null) return
    scope.launch {
      agentInstruction = instruction
      isAgentRunning = true
      agentError = null
      agentEvent = null
      try {
        val endpoint = withContext(Dispatchers.IO) { modelSettingsStore.resolveEndpoint() }
        if (hasUnsavedChanges && !saveCurrentNote()) {
          agentError = noteStatus ?: context.getString(R.string.note_save_failed, "")
          return@launch
        }
        val event = executeAgentPage(
          activeProject = activeProject,
          activeMarkdown = activeMarkdown,
          activeRenderer = activeRenderer,
          targetPage = targetPageIndex + 1,
          instruction = instruction,
          endpoint = endpoint,
        )
        if (project?.manifest?.id != activeProject.manifest.id || pageIndex != targetPageIndex) {
          agentError = context.getString(R.string.page_tutor_conflict)
        } else {
          agentEvent = event
          val updated = (event as? AgentEvent.Done)?.updated.orEmpty()
          if (targetPageIndex + 1 in updated) {
            val latest = withContext(Dispatchers.IO) {
              ProjectNoteStore(context.contentResolver, activeProject.notesUri).read()
            }
            notesMarkdown = latest
            val freshPage = NotePage.pageAt(latest, targetPageIndex + 1)
            noteDraft = freshPage
            noteBaseRevision = PageNotes.revision(freshPage)
            noteStatus = context.getString(R.string.note_reloaded)
          }
        }
      } catch (missing: MissingModelConfigException) {
        agentError = context.getString(R.string.page_tutor_model_missing)
        showAgentModelSettings = true
      } catch (timeout: TimeoutCancellationException) {
        agentError = context.getString(R.string.page_tutor_timeout)
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (error: PageTutorSourceException) {
        agentError = "${context.getString(R.string.page_tutor_source_failed)} ${error.message.orEmpty()}"
      } catch (error: Exception) {
        agentError = "${context.getString(R.string.page_tutor_request_failed)} ${error.message.orEmpty()}"
      } finally {
        isAgentRunning = false
      }
    }
  }

  fun runAgentBatch(instruction: String, rangeText: String) {
    val activeProject = project ?: return
    val activeMarkdown = markdownSource
    val activeRenderer = renderer
    if (activeRenderer == null && activeMarkdown == null) return
    scope.launch {
      agentInstruction = instruction
      agentPageRange = rangeText
      isAgentRunning = true
      agentError = null
      agentEvent = null
      agentReport = null
      try {
        val endpoint = withContext(Dispatchers.IO) { modelSettingsStore.resolveEndpoint() }
        if (hasUnsavedChanges && !saveCurrentNote()) {
          agentError = noteStatus ?: context.getString(R.string.note_save_failed, "")
          return@launch
        }
        val notesSnapshot = withContext(Dispatchers.IO) {
          ProjectNoteStore(context.contentResolver, activeProject.notesUri).read()
        }
        val totalPages = if (activeMarkdown != null) {
          MarkdownSourcePages.pageCount(activeMarkdown, notesSnapshot)
        } else {
          activeRenderer!!.pageCount
        }
        val pages = try {
          val raw = rangeText.trim().ifBlank { (pageIndex + 1).toString() }
          AgentPageRange.parse(raw, totalPages)
        } catch (error: IllegalArgumentException) {
          agentError = context.getString(R.string.agent_task_bad_range, rangeText.trim())
          return@launch
        }
        val documentId = activeProject.manifest.id
        val report = SerialPages.run(pages) { targetPage ->
          if (project?.manifest?.id != documentId) return@run AgentEvent.Failed("project changed")
          executeAgentPage(
            activeProject = activeProject,
            activeMarkdown = activeMarkdown,
            activeRenderer = activeRenderer,
            targetPage = targetPage,
            instruction = instruction,
            endpoint = endpoint,
          )
        }
        agentReport = report
        agentEvent = if (report.incomplete.isEmpty()) {
          AgentEvent.Done(pages = report.completed, updated = report.updated)
        } else {
          val reason = if (report.stoppedByRateLimit) {
            context.getString(R.string.agent_task_rate_limited, report.incomplete.joinToString(", "))
          } else {
            context.getString(R.string.agent_task_incomplete, report.incomplete.joinToString(", "))
          }
          AgentEvent.Failed(reason)
        }
        if (pageIndex + 1 in report.updated) {
          val latest = withContext(Dispatchers.IO) {
            ProjectNoteStore(context.contentResolver, activeProject.notesUri).read()
          }
          notesMarkdown = latest
          val freshPage = NotePage.pageAt(latest, pageIndex + 1)
          noteDraft = freshPage
          noteBaseRevision = PageNotes.revision(freshPage)
          noteStatus = context.getString(R.string.note_reloaded)
        }
      } catch (missing: MissingModelConfigException) {
        agentError = context.getString(R.string.page_tutor_model_missing)
        showAgentModelSettings = true
      } catch (timeout: TimeoutCancellationException) {
        agentError = context.getString(R.string.page_tutor_timeout)
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (error: Exception) {
        agentError = "${context.getString(R.string.page_tutor_request_failed)} ${error.message.orEmpty()}"
      } finally {
        isAgentRunning = false
      }
    }
  }

  var showNewProject by rememberSaveable { mutableStateOf(false) }
  var newProjectName by rememberSaveable { mutableStateOf("") }
  var newProjectDir by rememberSaveable { mutableStateOf<Uri?>(null) }
  var isCreatingProject by remember { mutableStateOf(false) }
  var isChoosingNewProjectDir by rememberSaveable { mutableStateOf(false) }
  var newProjectError by remember { mutableStateOf<String?>(null) }

  val newProjectDirPicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocumentTree(),
  ) { treeUri: Uri? ->
    isChoosingNewProjectDir = false
    showNewProject = true
    if (treeUri != null) {
      try {
        context.contentResolver.takePersistableUriPermission(
          treeUri,
          Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
      } catch (_: SecurityException) {
      }
      newProjectDir = treeUri
    }
  }

  fun chooseNewProjectDirectory() {
    isChoosingNewProjectDir = true
    newProjectDirPicker.launch(null)
  }

  fun assetChildExists(assetsUri: Uri, treeUri: Uri, name: String): Boolean {
    val assetsId = DocumentsContract.getDocumentId(assetsUri)
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, assetsId)
    val cursor = context.contentResolver.query(
      childrenUri,
      arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null,
    ) ?: return false
    cursor.use {
      val nameColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
      while (it.moveToNext()) {
        if (it.getString(nameColumn) == name) return true
      }
    }
    return false
  }

  suspend fun openProjectTree(target: ProjectOpenTarget, manageOpening: Boolean = true) {
    if (manageOpening) {
      isOpening = true
      errorMessage = null
    }
    try {
      var writable = true
      if (target.needsPersistablePermission) {
        try {
          context.contentResolver.takePersistableUriPermission(
            target.accessTreeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
          )
        } catch (_: SecurityException) {
          writable = false
          context.contentResolver.takePersistableUriPermission(
            target.accessTreeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
          )
        }
      }
      val loaded = withContext(Dispatchers.IO) {
        val opened = ProjectDocumentOpener(context.contentResolver).open(
          target.accessTreeUri,
          target.rootDocumentId,
        )
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
        val isMarkdown = MarkdownSourcePages.isMarkdownExtension(opened.sourceExtension)
        if (!isMarkdown) {
          require(opened.sourceExtension == "pdf") {
            // UI 侧再做一次本地化；这里只抛英文技术信息避免 IO 线程触碰资源。
            "Unsupported source format: ${opened.sourceExtension.ifBlank { "(empty)" }}"
          }
        }
        val markdownText = if (isMarkdown) {
          context.contentResolver.openInputStream(opened.sourceUri)?.bufferedReader(Charsets.UTF_8)?.use {
            it.readText()
          } ?: throw IllegalStateException("无法读取 Markdown 原文")
        } else {
          null
        }
        val pdf = if (isMarkdown) {
          null
        } else {
          PdfDocumentSession.open(context.contentResolver, opened.sourceUri, context.cacheDir)
        }
        ReaderProjectData(
          project = opened,
          noteText = noteText,
          renderer = pdf,
          markdownSource = markdownText,
          noteWritable = writable && supportsNoteWrite,
          penStore = projectPenStore,
          penPageStore = loadedPenPages,
          penWritable = writable && supportsPenWrite,
        )
      }
      renderer?.close()
      renderer = loaded.renderer
      markdownSource = loaded.markdownSource
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
      val missingAssets = withContext(Dispatchers.IO) {
        runCatching {
          val refs = ProjectAssetRefs.scan(loaded.noteText)
          if (refs.isEmpty()) return@runCatching emptyList<String>()
          val srcDir = loaded.project.sourceDirectoryUri
          val srcId = DocumentsContract.getDocumentId(srcDir)
          val assetsUri = DocumentsContract.buildDocumentUriUsingTree(srcDir, srcId + "/assets")
          ProjectAssetRefs.missing(refs) { name ->
            runCatching { assetChildExists(assetsUri, srcDir, name) }.getOrDefault(false)
          }
        }.getOrDefault(emptyList())
      }
      noteStatus = when {
        !noteWritable -> context.getString(R.string.read_only_project)
        missingAssets.isNotEmpty() -> context.getString(
          R.string.note_missing_assets, missingAssets.take(3).joinToString(", "),
        )
        else -> null
      }
      ocrResult = null
      showOcrResult = false
      regionOcrResult = null
      readerOcrState = ReaderOcrState()
      pageIndex = 0
    } catch (error: Exception) {
      val detail = error.message.orEmpty()
      errorMessage = if (detail.startsWith("Unsupported source format:")) {
        val extension = detail.substringAfter("Unsupported source format:").trim().ifBlank { "" }
        context.getString(R.string.unsupported_format, extension.ifBlank { "" })
      } else {
        context.getString(R.string.select_project_error, detail)
      }
    } finally {
      if (manageOpening) isOpening = false
    }
  }

  fun createNewProject(scope: CoroutineScope) {
    val name = newProjectName.trim()
    val dir = newProjectDir
    if (name.isEmpty() || name.contains(Regex("[<>:\"/\\|?*]"))) {
      newProjectError = context.getString(R.string.new_project_bad_name)
      return
    }
    if (dir == null) {
      chooseNewProjectDirectory()
      return
    }
    scope.launch {
      isCreatingProject = true
      newProjectError = null
      try {
        val projectTarget = withContext(Dispatchers.IO) {
          ProjectCreator(context.contentResolver).create(dir, name)
        }
        showNewProject = false
        newProjectName = ""
        newProjectDir = null
        if (hasUnsavedChanges && !saveCurrentNote()) return@launch
        openProjectTree(projectTarget, manageOpening = true)
      } catch (error: Exception) {
        newProjectError = context.getString(R.string.new_project_failed, error.message ?: "")
      } finally {
        isCreatingProject = false
      }
    }
  }


  val projectPicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocumentTree(),
  ) { treeUri: Uri? ->
    if (treeUri != null) {
      scope.launch { openProjectTree(ProjectOpenTarget.fromSelectedTree(treeUri)) }
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
        enabled = !isOpening && !isCreatingProject && !isSaving && !isPenSaving && failedPenSave == null &&
          !isOcrRunning && !readerOcrState.inputLocked,
      ) {
        Text(stringResource(if (isOpening) R.string.opening_project else R.string.open_project))
      }
      TextButton(
        modifier = Modifier.testTag("new_project"),
        onClick = { showNewProject = true },
        enabled = !isOpening && !isCreatingProject && !isSaving && !isPenSaving &&
          !isOcrRunning && !readerOcrState.inputLocked,
      ) {
        Text(stringResource(R.string.new_project))
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
    val activeMarkdown = markdownSource
    val totalPages = when {
      activeProject == null -> 0
      activeRenderer != null -> activeRenderer.pageCount
      activeMarkdown != null -> MarkdownSourcePages.pageCount(activeMarkdown, notesMarkdown)
      else -> 0
    }
    if (activeProject != null && (activeRenderer != null || activeMarkdown != null)) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Button(
          onClick = { scope.launch { changePage((pageIndex - 1).coerceAtLeast(0)) } },
          enabled = pageIndex > 0 && !isRendering && !isSaving && !isPenSaving &&
            failedPenSave == null && !isOcrRunning && !readerOcrState.inputLocked,
        ) {
          Text(stringResource(R.string.previous_page))
        }
        Text(stringResource(R.string.page_count, pageIndex + 1, totalPages))
        Button(
          onClick = ::recognizeCurrentPage,
          enabled = !isMarkdownReading && !isRendering && !isOcrRunning && !readerOcrState.inputLocked,
        ) {
          Text(stringResource(if (isOcrRunning) R.string.recognizing else R.string.ocr))
        }
        Button(
          onClick = {
            scope.launch {
              changePage((pageIndex + 1).coerceAtMost(totalPages - 1))
            }
          },
          enabled = pageIndex < totalPages - 1 &&
            !isRendering && !isSaving && !isPenSaving && failedPenSave == null && !isOcrRunning &&
            !readerOcrState.inputLocked,
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
            pageTutorResultInstruction = null
            pageTutorError = null
            showPageTutor = true
          },
          enabled = !isRendering && !isSaving && !isPageTutorRunning && !isOcrRunning &&
            !readerOcrState.inputLocked,
        ) { Text(stringResource(R.string.page_tutor_action)) }
      TextButton(
        modifier = Modifier.testTag("agent_task_action"),
        onClick = {
          agentEvent = null
          agentError = null
          showAgentTask = true
        },
        enabled = !isRendering && !isSaving && !isAgentRunning && !isOcrRunning &&
          !readerOcrState.inputLocked,
      ) { Text(stringResource(R.string.agent_task_action)) }
        ReaderOcrActions(
          state = readerOcrState,
          currentPage = pageIndex,
          enabled = !isMarkdownReading && !isOcrRunning && !isRendering,
          onStateChange = { readerOcrState = it },
          onRecognize = ::recognizeSelectedRegion,
        )
        TextButton(
          onClick = { applyViewportZoom(0.8f) },
          enabled = !isMarkdownReading && !readerOcrState.inputLocked && viewportTransform.scale > PdfViewportTransform.MIN_SCALE,
        ) { Text(stringResource(R.string.zoom_out)) }
        Text("${(viewportTransform.scale * 100f).roundToInt()}%")
        TextButton(
          onClick = { applyViewportZoom(1.25f) },
          enabled = !isMarkdownReading && !readerOcrState.inputLocked && viewportTransform.scale < PdfViewportTransform.MAX_SCALE,
        ) { Text(stringResource(R.string.zoom_in)) }
        TextButton(onClick = ::fitViewportWidth, enabled = !isMarkdownReading && !readerOcrState.inputLocked) { Text(stringResource(R.string.fit_width)) }
        TextButton(onClick = { viewportTransform = PdfViewportTransform.DEFAULT }, enabled = !isMarkdownReading && !readerOcrState.inputLocked) {
          Text(stringResource(R.string.fit_page))
        }
        listOf(
          PenCanvasTool.PEN to R.string.pen,
          PenCanvasTool.HIGHLIGHTER to R.string.highlighter,
          PenCanvasTool.ERASER to R.string.eraser,
        ).forEach { (tool, label) ->
          if (penTool == tool) {
            Button(onClick = { penTool = tool }, enabled = !isMarkdownReading && penWritable && !readerOcrState.inputLocked) {
              Text(stringResource(label))
            }
          } else {
            TextButton(onClick = { penTool = tool }, enabled = !isMarkdownReading && penWritable && !readerOcrState.inputLocked) {
              Text(stringResource(label))
            }
          }
        }
        TextButton(
          modifier = Modifier.testTag("annotate_mode"),
          onClick = { annotateMode = !annotateMode },
          enabled = !isMarkdownReading && penWritable && !readerOcrState.inputLocked,
        ) { Text(stringResource(if (annotateMode) R.string.annotate_on else R.string.annotate)) }
        TextButton(
          modifier = Modifier.testTag("annotations_list"),
          onClick = { showAnnotations = true },
          enabled = !readerOcrState.inputLocked,
        ) { Text(stringResource(R.string.annotations)) }
        TextButton(
          modifier = Modifier.testTag("reviews_list"),
          onClick = { showReviews = true },
          enabled = !readerOcrState.inputLocked,
        ) { Text(stringResource(R.string.review_action)) }
        TextButton(
          modifier = Modifier.testTag("handoff_open"),
          onClick = { showHandoff = true },
          enabled = !readerOcrState.inputLocked,
        ) { Text(stringResource(R.string.handoff_action)) }
        TextButton(
          onClick = ::undoPenPage,
          enabled = !isMarkdownReading && penWritable && !readerOcrState.inputLocked && penPageStore.canUndo(PenSide.DOCUMENT, pageIndex + 1),
        ) { Text(stringResource(R.string.undo)) }
        TextButton(
          onClick = ::redoPenPage,
          enabled = !isMarkdownReading && penWritable && !readerOcrState.inputLocked && penPageStore.canRedo(PenSide.DOCUMENT, pageIndex + 1),
        ) { Text(stringResource(R.string.redo)) }
        TextButton(
          onClick = ::clearPenPage,
          enabled = !isMarkdownReading && penWritable && !readerOcrState.inputLocked && penStrokes.isNotEmpty(),
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

    if (activeProject != null && (activeRenderer != null || activeMarkdown != null)) {
      BoxWithConstraints(
        modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
      ) {
        if (maxWidth >= 840.dp) {
          val (pdfPaneWeight, notePaneWeight) = readerPaneWeights(settings.leftPercent)
          Row(modifier = Modifier.fillMaxSize()) {
            if (activeMarkdown != null) {
              MarkdownSourcePane(
                markdown = activeMarkdown,
                sourceName = activeProject.sourceName,
                page = pageIndex + 1,
                sourceDirectoryUri = activeProject.sourceDirectoryUri,
                modifier = Modifier.weight(pdfPaneWeight).fillMaxHeight(),
              )
            } else {
              PdfPageViewport(
                bitmap = pageBitmap,
                sourceName = activeProject.sourceName,
                page = pageIndex,
                loading = isRendering,
                strokes = penStrokes,
                tool = penTool,
                inputEnabled = penWritable && !readerOcrState.inputLocked && !annotateMode,
                onStrokeAdded = ::applyPenStroke,
                onStrokeErased = ::erasePenStroke,
                transform = viewportTransform,
                onTransformChanged = { viewportTransform = it },
                onBoundsChanged = { viewportBounds = it },
                selectionEnabled = readerOcrState.mode == ReaderOcrMode.SELECTING,
                gesturesEnabled = !readerOcrState.inputLocked && !annotateMode,
                selection = if (readerOcrState.pageIndex == pageIndex) readerOcrState.selection else null,
                onSelectionChanged = { readerOcrState = readerOcrState.withSelection(it) },
                annotateTapEnabled = annotateMode,
                onAnnotateTap = { tap ->
                  val point = mapPdfTapToPagePoint(tap, viewportBounds, viewportTransform)
                  if (point != null) {
                    annotateDraft = point
                    annotateText = ""
                  }
                },
                modifier = Modifier.weight(pdfPaneWeight).fillMaxHeight(),
              )
            }
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
              historyEnabled = noteWritable &&
                NoteHistoryMirror.directory(context.filesDir, project?.manifest?.id) != null,
              onHistory = { showNoteHistory = true },
              sourceDirectoryUri = activeProject.sourceDirectoryUri,
              animationEnabled = settings.noteAnimation,
              modifier = Modifier.weight(notePaneWeight).fillMaxHeight(),
            )
          }
        } else {
          Column(modifier = Modifier.fillMaxSize()) {
            if (activeMarkdown != null) {
              MarkdownSourcePane(
                markdown = activeMarkdown,
                sourceName = activeProject.sourceName,
                page = pageIndex + 1,
                sourceDirectoryUri = activeProject.sourceDirectoryUri,
                modifier = Modifier.weight(1f).fillMaxWidth(),
              )
            } else {
              PdfPageViewport(
                bitmap = pageBitmap,
                sourceName = activeProject.sourceName,
                page = pageIndex,
                loading = isRendering,
                strokes = penStrokes,
                tool = penTool,
                inputEnabled = penWritable && !readerOcrState.inputLocked && !annotateMode,
                onStrokeAdded = ::applyPenStroke,
                onStrokeErased = ::erasePenStroke,
                transform = viewportTransform,
                onTransformChanged = { viewportTransform = it },
                onBoundsChanged = { viewportBounds = it },
                selectionEnabled = readerOcrState.mode == ReaderOcrMode.SELECTING,
                gesturesEnabled = !readerOcrState.inputLocked && !annotateMode,
                selection = if (readerOcrState.pageIndex == pageIndex) readerOcrState.selection else null,
                onSelectionChanged = { readerOcrState = readerOcrState.withSelection(it) },
                annotateTapEnabled = annotateMode,
                onAnnotateTap = { tap ->
                  val point = mapPdfTapToPagePoint(tap, viewportBounds, viewportTransform)
                  if (point != null) {
                    annotateDraft = point
                    annotateText = ""
                  }
                },
                modifier = Modifier.weight(1f).fillMaxWidth(),
              )
            }
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
              historyEnabled = noteWritable &&
                NoteHistoryMirror.directory(context.filesDir, project?.manifest?.id) != null,
              onHistory = { showNoteHistory = true },
              sourceDirectoryUri = activeProject.sourceDirectoryUri,
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
  if (readerOcrState.mode == ReaderOcrMode.RESULT) {
    regionOcrResult?.let { result ->
      OcrResultDialog(
        page = (readerOcrState.pageIndex ?: pageIndex) + 1,
        result = result,
        onDismiss = { readerOcrState = readerOcrState.dismissResult() },
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
  if (showPageTutor && project != null && (renderer != null || markdownSource != null)) {
    PageTutorDialog(
      page = pageIndex + 1,
      instruction = pageTutorInstruction,
      previewInstruction = pageTutorResultInstruction,
      isRunning = isPageTutorRunning,
      isSaving = isSaving && pageTutorResult != null,
      errorMessage = pageTutorError,
      preview = pageTutorResult,
      noteWritable = noteWritable,
      onInstructionChange = { pageTutorInstruction = it },
      onExplain = ::explainCurrentPage,
      onAppend = ::appendPageTutorResult,
      onDismiss = {
        showPageTutor = false
        pageTutorResult = null
        pageTutorResultInstruction = null
        pageTutorError = null
      },
    )
  }
  if (showAgentTask && project != null && (renderer != null || markdownSource != null)) {
    AgentTaskDialog(
      page = pageIndex + 1,
      instruction = agentInstruction,
      pageRange = agentPageRange,
      report = agentReport,
      isRunning = isAgentRunning,
      errorMessage = agentError,
      event = agentEvent,
      onInstructionChange = { agentInstruction = it },
      onPageRangeChange = { agentPageRange = it },
      onRun = { instruction, range -> runAgentBatch(instruction, range) },
      onDismiss = {
        showAgentTask = false
        agentEvent = null
        agentError = null
        agentReport = null
      },
    )
  }
  if (showAgentModelSettings) {
    AgentModelSettingsDialog(
      store = modelSettingsStore,
      onDismiss = { showAgentModelSettings = false },
    )
  }
  if (showNewProject) {
    NewProjectDialog(
      name = newProjectName,
      onNameChange = { newProjectName = it },
      hasDir = newProjectDir != null,
      error = newProjectError,
      creating = isCreatingProject,
      onChooseDir = { chooseNewProjectDirectory() },
      onConfirm = { createNewProject(scope) },
      onDismiss = {
        if (NewProjectDialogPolicy.canDismiss(isCreatingProject, isChoosingNewProjectDir)) {
          showNewProject = false
          newProjectError = null
        }
      },
    )
  }
  if (annotateDraft != null && project != null) {
    AnnotationTextInputDialog(
      text = annotateText,
      onTextChange = { annotateText = it },
      isRegion = annotateIsRegion,
      onKindChange = { annotateIsRegion = it },
      onConfirm = {
        scope.launch {
          val point = annotateDraft ?: return@launch
          val store = ProjectAnnotationStore(
            context.contentResolver,
            project!!.sourceDirectoryUri,
            project!!.annotationsUri,
          )
          try {
            val useRegion = annotateIsRegion
            val item = if (useRegion) {
              AnnotationRegionDraft.defaultSize(
                point.first, point.second, "#dc3545", 3.0, annotateText,
              )
            } else {
              AnnotationTextDraft.toItem(
                point.first, point.second, "#dc3545", 3.0, annotateText,
              )
            }
            penSaveMutex.withLock {
              val existing = withContext(Dispatchers.IO) {
                store.readPage("document", pageIndex + 1)
              }
              val merged = existing.toMutableList()
              merged.add(item.toJsonElement())
              withContext(Dispatchers.IO) {
                store.writePage("document", pageIndex + 1, kotlinx.serialization.json.JsonArray(merged))
              }
            }
            noteStatus = context.getString(R.string.annotation_saved)
          } catch (error: Exception) {
            noteStatus = context.getString(R.string.annotation_save_failed, error.message ?: "")
          } finally {
            annotateDraft = null
            annotateText = ""
            annotateIsRegion = false
            annotateMode = false
          }
        }
      },
      onDismiss = {
        annotateDraft = null
        annotateText = ""
        annotateIsRegion = false
      },
    )
  }
  if (showAnnotations && project != null) {
    AnnotationListDialog(
      page = pageIndex + 1,
      project = project!!,
      onDismiss = { showAnnotations = false },
      onChanged = { noteStatus = it },
      onDelete = { targetPage, targetIndex ->
        penSaveMutex.withLock {
          withContext(Dispatchers.IO) {
            val store = ProjectAnnotationStore(
              context.contentResolver,
              project!!.sourceDirectoryUri,
              project!!.annotationsUri,
            )
            val current = store.readPage("document", targetPage).toMutableList()
            current.removeAt(targetIndex)
            store.writePage("document", targetPage, kotlinx.serialization.json.JsonArray(current))
          }
        }
      },
    )
  }
  if (showReviews && project != null) {
    ReviewListDialog(
      page = pageIndex + 1,
      project = project!!,
      noteRevision = noteBaseRevision,
      onDismiss = { showReviews = false },
      onChanged = { noteStatus = it },
    )
  }
  if (showHandoff && project != null && (renderer != null || markdownSource != null)) {
    HandoffDialog(
      page = pageIndex + 1,
      project = project!!,
      noteRevision = PageNotes.revision(noteDraft),
      sourceRevision = renderer?.sourceRevision.orEmpty(),
      onDismiss = { showHandoff = false },
      onChanged = { noteStatus = it },
    )
  }
  if (showNoteHistory && project != null) {
    NoteHistoryDialog(
      page = pageIndex + 1,
      projectId = project!!.manifest.id,
      currentDraft = noteDraft,
      baseRevision = noteBaseRevision,
      onDismiss = { showNoteHistory = false },
      onRestore = { restored ->
        noteDraft = restored
        noteBaseRevision = PageNotes.revision(restored)
        noteStatus = context.getString(R.string.note_history_restored)
        showNoteHistory = false
      },
    )
  }
}

@Composable
internal fun NewProjectDialog(
  name: String,
  onNameChange: (String) -> Unit,
  hasDir: Boolean,
  error: String?,
  creating: Boolean,
  onChooseDir: () -> Unit,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.new_project_title)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.new_project_name))
        AndroidView(
          factory = { viewContext ->
            EditText(viewContext).apply {
              inputType = InputType.TYPE_CLASS_TEXT
              setText(name)
              setSelection(text.length)
              addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(editable: Editable?) {
                  onNameChange(editable?.toString().orEmpty())
                }
              })
            }
          },
          update = { editText ->
            if (editText.text.toString() != name) {
              editText.setText(name)
              editText.setSelection(editText.text.length)
            }
          },
        )
        TextButton(
          modifier = Modifier.testTag("new_project_choose_dir"),
          onClick = onChooseDir,
          enabled = !creating,
        ) {
          Text(
            if (hasDir) stringResource(R.string.new_project_choose_dir) + " ✓"
            else stringResource(R.string.new_project_choose_dir),
          )
        }
        error?.let {
          Text(it, color = MaterialTheme.colorScheme.error)
        }
      }
    },
    confirmButton = {
      Button(
        modifier = Modifier.testTag("new_project_confirm"),
        onClick = onConfirm,
        enabled = !creating,
      ) {
        Text(stringResource(if (creating) R.string.new_project_creating else R.string.new_project_create))
      }
    },
    dismissButton = {
      TextButton(modifier = Modifier.testTag("new_project_cancel"), onClick = onDismiss) {
        Text(stringResource(R.string.cancel))
      }
    },
  )
}

@Composable
internal fun AnnotationTextInputDialog(
  text: String,
  onTextChange: (String) -> Unit,
  isRegion: Boolean = false,
  onKindChange: (Boolean) -> Unit = {},
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.annotation_text_label)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.annotation_hint))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          if (isRegion) {
            Button(
              modifier = Modifier.testTag("annotation_kind_region"),
              onClick = {},
            ) { Text(stringResource(R.string.annotation_kind_region)) }
          } else {
            TextButton(
              modifier = Modifier.testTag("annotation_kind_region"),
              onClick = { onKindChange(true) },
            ) { Text(stringResource(R.string.annotation_kind_region)) }
          }
          if (!isRegion) {
            Button(
              modifier = Modifier.testTag("annotation_kind_text"),
              onClick = {},
            ) { Text(stringResource(R.string.annotation_kind_text)) }
          } else {
            TextButton(
              modifier = Modifier.testTag("annotation_kind_text"),
              onClick = { onKindChange(false) },
            ) { Text(stringResource(R.string.annotation_kind_text)) }
          }
        }
        AndroidView(
          factory = { viewContext ->
            EditText(viewContext).apply {
              inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
              setText(text)
              setSelection(getText().length)
              addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(editable: Editable?) {
                  onTextChange(editable?.toString().orEmpty())
                }
              })
            }
          },
          update = { editText ->
            if (editText.text.toString() != text) {
              editText.setText(text)
              editText.setSelection(editText.text.length)
            }
          },
        )
      }
    },
    confirmButton = {
      Button(
        modifier = Modifier.testTag("annotation_confirm"),
        onClick = onConfirm,
        enabled = isRegion || text.isNotBlank(),
      ) { Text(stringResource(R.string.annotation_add)) }
    },
    dismissButton = {
      TextButton(modifier = Modifier.testTag("annotation_cancel"), onClick = onDismiss) {
        Text(stringResource(R.string.cancel))
      }
    },
  )
}

@Composable
internal fun AnnotationListDialog(
  page: Int,
  project: OpenedProject,
  onDismiss: () -> Unit,
  onChanged: (String) -> Unit,
  onDelete: (suspend (Int, Int) -> Unit)? = null,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var items by remember(project, page) { mutableStateOf<List<kotlinx.serialization.json.JsonElement>>(emptyList()) }
  var loadError by remember(project, page) { mutableStateOf<String?>(null) }
  var pendingDelete by remember { mutableStateOf<Int?>(null) }

  LaunchedEffect(project, page) {
    loadError = null
    items = withContext(Dispatchers.IO) {
      runCatching {
        ProjectAnnotationStore(
          context.contentResolver, project.sourceDirectoryUri, project.annotationsUri,
        ).readPage("document", page).toList()
      }.getOrElse {
        loadError = context.getString(R.string.annotation_save_failed, it.message ?: "")
        emptyList()
      }
    }
  }

  fun nonPenEntries(): List<Pair<Int, kotlinx.serialization.json.JsonObject>> =
    items.mapIndexedNotNull { index, element ->
      val obj = element as? kotlinx.serialization.json.JsonObject ?: return@mapIndexedNotNull null
      val type = obj["type"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNullFallback() } ?: ""
      if (type == "pen" || type == "highlight") null else index to obj
    }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.annotation_title, page)) },
    text = {
      Column(
        modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        loadError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        val entries = nonPenEntries()
        if (loadError == null && entries.isEmpty()) {
          Text(stringResource(R.string.annotation_empty))
        }
        entries.forEach { (index, obj) ->
          val type = obj["type"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNullFallback() } ?: ""
          val label = if (type == "text" || type == "rect") {
            val text = obj["text"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNullFallback() }.orEmpty()
            if (text.isBlank()) context.getString(R.string.annotation_other_type, type) else text
          } else {
            context.getString(R.string.annotation_other_type, type)
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(label, modifier = Modifier.weight(1f))
            if (type == "text" || type == "rect") {
              TextButton(
                modifier = Modifier.testTag("annotation_delete_$index"),
                onClick = { pendingDelete = index },
              ) { Text(stringResource(R.string.delete)) }
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(modifier = Modifier.testTag("annotations_close"), onClick = onDismiss) {
        Text(stringResource(R.string.close))
      }
    },
  )
  if (pendingDelete != null) {
    AlertDialog(
      onDismissRequest = { pendingDelete = null },
      title = { Text(stringResource(R.string.delete)) },
      text = { Text(stringResource(R.string.annotation_hint)) },
      confirmButton = {
        TextButton(
          modifier = Modifier.testTag("annotation_delete_confirm"),
          onClick = {
            val target = pendingDelete ?: return@TextButton
            scope.launch {
              try {
                if (onDelete != null) {
                  onDelete(page, target)
                } else {
                  withContext(Dispatchers.IO) {
                    val store = ProjectAnnotationStore(
                      context.contentResolver, project.sourceDirectoryUri, project.annotationsUri,
                    )
                    val current = store.readPage("document", page).toMutableList()
                    current.removeAt(target)
                    store.writePage("document", page, kotlinx.serialization.json.JsonArray(current))
                  }
                }
                items = items.toMutableList().also { it.removeAt(target) }
                onChanged(context.getString(R.string.annotation_deleted))
              } catch (error: Exception) {
                onChanged(context.getString(R.string.annotation_save_failed, error.message ?: ""))
              } finally {
                pendingDelete = null
              }
            }
          },
        ) { Text(stringResource(R.string.delete)) }
      },
      dismissButton = {
        TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel)) }
      },
    )
  }
}

private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullFallback(): String? =
  try {
    if (isString) content else null
  } catch (_: Exception) {
    null
  }

private fun Map<String, Any?>.toJsonElement(): kotlinx.serialization.json.JsonElement = when {
  this == null -> kotlinx.serialization.json.JsonNull
  else -> kotlinx.serialization.json.JsonObject(
    entries.associate { (k, v) -> k to v.toJsonElementValue() },
  )
}

private fun Any?.toJsonElementValue(): kotlinx.serialization.json.JsonElement = when (this) {
  null -> kotlinx.serialization.json.JsonNull
  is String -> kotlinx.serialization.json.JsonPrimitive(this as String)
  is Number -> try {
    kotlinx.serialization.json.JsonPrimitive(this as Number)
  } catch (_: Exception) {
    kotlinx.serialization.json.JsonPrimitive(toString())
  }
  is Boolean -> kotlinx.serialization.json.JsonPrimitive(this as Boolean)
  is Map<*, *> -> (this as Map<String, Any?>).toJsonElement()
  is Iterable<*> -> kotlinx.serialization.json.JsonArray(map { it.toJsonElementValue() })
  else -> kotlinx.serialization.json.JsonPrimitive(toString())
}

@Composable
internal fun ReviewListDialog(
  page: Int,
  project: OpenedProject,
  noteRevision: String,
  onDismiss: () -> Unit,
  onChanged: (String) -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var records by remember(project, page) { mutableStateOf<List<JsonObject>>(emptyList()) }
  var loadError by remember(project, page) { mutableStateOf<String?>(null) }
  var instruction by remember { mutableStateOf("") }
  var pendingApply by remember { mutableStateOf<String?>(null) }
  var pendingDismiss by remember { mutableStateOf<String?>(null) }
  var pendingDelete by remember { mutableStateOf<String?>(null) }

  fun store() = ProjectReviewStore(context.contentResolver, project.sourceDirectoryUri, project.reviewsUri)

  LaunchedEffect(project, page) {
    loadError = null
    records = withContext(Dispatchers.IO) {
      runCatching {
        store().list(page).mapNotNull { it as? JsonObject }
      }.getOrElse {
        loadError = context.getString(R.string.review_save_failed, it.message ?: "")
        emptyList()
      }
    }
  }

  suspend fun refresh() {
    records = withContext(Dispatchers.IO) {
      runCatching { store().list(page).mapNotNull { it as? JsonObject } }.getOrDefault(records)
    }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.review_title, page)) },
    text = {
      Column(
        modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        loadError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (loadError == null && records.isEmpty()) {
          Text(stringResource(R.string.review_empty))
        }
        records.forEach { record ->
          val id = record["id"]?.let { (it as? JsonPrimitive)?.contentOrNullFallback() } ?: ""
          val text = record["instruction"]?.let { (it as? JsonPrimitive)?.contentOrNullFallback() } ?: ""
          val status = record["status"]?.let { (it as? JsonPrimitive)?.contentOrNullFallback() } ?: ""
          Column(modifier = Modifier.fillMaxWidth()) {
            Text(text)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(status, style = MaterialTheme.typography.labelSmall)
              if (status == "open") {
                TextButton(onClick = { pendingApply = id }) { Text(stringResource(R.string.review_applied)) }
                TextButton(onClick = { pendingDismiss = id }) { Text(stringResource(R.string.review_dismissed)) }
              }
              TextButton(onClick = { pendingDelete = id }) { Text(stringResource(R.string.delete)) }
            }
          }
        }
        Text(stringResource(R.string.review_instruction_label))
        AndroidView(
          factory = { viewContext ->
            EditText(viewContext).apply {
              inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
              setText(instruction)
              addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(editable: Editable?) {
                  instruction = editable?.toString().orEmpty()
                }
              })
            }
          },
          update = { editText ->
            if (editText.text.toString() != instruction) editText.setText(instruction)
          },
        )
        Button(
          modifier = Modifier.testTag("review_create"),
          onClick = {
            scope.launch {
              try {
                withContext(Dispatchers.IO) {
                  store().create("note", "overall", page, instruction, noteRevision)
                }
                instruction = ""
                refresh()
                onChanged(context.getString(R.string.review_saved))
              } catch (error: Exception) {
                onChanged(context.getString(R.string.review_save_failed, error.message ?: ""))
              }
            }
          },
          enabled = instruction.isNotBlank(),
        ) { Text(stringResource(R.string.review_save)) }
      }
    },
    confirmButton = {
      TextButton(modifier = Modifier.testTag("reviews_close"), onClick = onDismiss) {
        Text(stringResource(R.string.close))
      }
    },
  )
  pendingApply?.let { id ->
    AlertDialog(
      onDismissRequest = { pendingApply = null },
      title = { Text(stringResource(R.string.review_applied)) },
      text = { Text(stringResource(R.string.review_instruction_label)) },
      confirmButton = {
        TextButton(onClick = {
          scope.launch {
            try {
              withContext(Dispatchers.IO) { store().transition(id, "applied", noteRewritten = true) }
              refresh()
              onChanged(context.getString(R.string.review_applied))
            } catch (error: Exception) {
              onChanged(context.getString(R.string.review_save_failed, error.message ?: ""))
            } finally {
              pendingApply = null
            }
          }
        }) { Text(stringResource(R.string.review_applied)) }
      },
      dismissButton = {
        TextButton(onClick = { pendingApply = null }) { Text(stringResource(R.string.cancel)) }
      },
    )
  }
  pendingDismiss?.let { id ->
    scope.launch {
      try {
        withContext(Dispatchers.IO) { store().transition(id, "dismissed") }
        refresh()
        onChanged(context.getString(R.string.review_dismissed))
      } catch (error: Exception) {
        onChanged(context.getString(R.string.review_save_failed, error.message ?: ""))
      } finally {
        pendingDismiss = null
      }
    }
  }
  pendingDelete?.let { id ->
    scope.launch {
      try {
        withContext(Dispatchers.IO) { store().delete(id) }
        refresh()
        onChanged(context.getString(R.string.review_deleted))
      } catch (error: Exception) {
        onChanged(context.getString(R.string.review_save_failed, error.message ?: ""))
      } finally {
        pendingDelete = null
      }
    }
  }
}

@Composable
internal fun HandoffDialog(
  page: Int,
  project: OpenedProject,
  noteRevision: String,
  sourceRevision: String,
  onDismiss: () -> Unit,
  onChanged: (String) -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var view by remember(project, page) { mutableStateOf<HandoffSafView?>(null) }
  var summary by remember { mutableStateOf("") }
  var loadError by remember(project, page) { mutableStateOf<String?>(null) }

  fun store() = ProjectHandoffStore(context.contentResolver, project.sourceDirectoryUri, project.handoffsUri)

  LaunchedEffect(project, page, noteRevision, sourceRevision) {
    loadError = null
    view = withContext(Dispatchers.IO) {
      runCatching { store().view(page, noteRevision, sourceRevision) }.getOrElse {
        loadError = context.getString(R.string.handoff_save_failed, it.message ?: "")
        null
      }
    }
    view?.record?.let { summary = it.summary }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.handoff_title, page)) },
    text = {
      Column(
        modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        loadError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        val current = view
        if (current?.record == null && loadError == null) {
          Text(stringResource(R.string.handoff_empty))
        }
        current?.record?.let { Text(it.summary) }
        if (current?.stale == true) {
          Text(stringResource(R.string.handoff_stale), color = MaterialTheme.colorScheme.error)
        }
        Text(stringResource(R.string.handoff_summary_label))
        AndroidView(
          factory = { viewContext ->
            EditText(viewContext).apply {
              inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
              setText(summary)
              addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(editable: Editable?) {
                  summary = editable?.toString().orEmpty()
                }
              })
            }
          },
          update = { editText ->
            if (editText.text.toString() != summary) editText.setText(summary)
          },
        )
        Button(
          modifier = Modifier.testTag("handoff_save"),
          onClick = {
            scope.launch {
              try {
                val saved = withContext(Dispatchers.IO) {
                  store().write(page, summary, noteRevision, sourceRevision, noteRevision)
                }
                view = HandoffSafView(saved, false)
                onChanged(context.getString(R.string.handoff_saved))
              } catch (error: Exception) {
                onChanged(context.getString(R.string.handoff_save_failed, error.message ?: ""))
              }
            }
          },
          enabled = summary.isNotBlank() && current?.stale != true,
        ) { Text(stringResource(R.string.handoff_save)) }
      }
    },
    confirmButton = {
      TextButton(modifier = Modifier.testTag("handoff_close"), onClick = onDismiss) {
        Text(stringResource(R.string.close))
      }
    },
  )
}

private fun isReaderErrorStatus(status: String): Boolean =
  status.contains("冲突") || status.contains("失败") || status.contains("只读") ||
    status.contains("conflict", ignoreCase = true) || status.contains("fail", ignoreCase = true) ||
    status.contains("read-only", ignoreCase = true) || status.contains("not saved", ignoreCase = true)

@Composable
internal fun MarkdownSourcePane(
  markdown: String,
  sourceName: String,
  page: Int,
  sourceDirectoryUri: Uri?,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val markwon = remember(context, sourceDirectoryUri) {
    Markwon.builder(context)
      .usePlugin(TablePlugin.create(context))
      .usePlugin(
        ImagesPlugin.create().addSchemeHandler(
          NoteAssetSchemeHandler(context.contentResolver, sourceDirectoryUri),
        ),
      )
      .build()
  }
  val (previewText, truncated) = remember(markdown, page) {
    MarkdownSourcePages.previewPage(markdown, page)
  }
  val rewritten = remember(previewText) { NotePreviewRewrite.forPreview(previewText) }
  val contentColorInt = MaterialTheme.colorScheme.onSurface.toArgb()
  val fullPageText = remember(markdown, page) { MarkdownSourcePages.pageAt(markdown, page) }
  Column(modifier = modifier.padding(8.dp).testTag("markdown_source_pane")) {
    Text(
      text = "$sourceName · ${page}",
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    if (previewText.isBlank()) {
      Text(
        text = stringResource(R.string.markdown_source_empty),
        modifier = Modifier.padding(top = 12.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    } else {
      AndroidView(
        modifier = Modifier.fillMaxSize().padding(top = 8.dp),
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
          markwon.setMarkdown(view, rewritten)
        },
      )
    }
    if (truncated) {
      Text(
        text = stringResource(
          R.string.note_preview_truncated,
          fullPageText.length,
          previewText.length,
        ),
        modifier = Modifier.padding(top = 4.dp).testTag("note_preview_truncated"),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
internal fun PdfPageViewport(
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
  selectionEnabled: Boolean = false,
  gesturesEnabled: Boolean = true,
  selection: NormalizedOcrRegion? = null,
  onSelectionChanged: (NormalizedOcrRegion?) -> Unit = {},
  annotateTapEnabled: Boolean = false,
  onAnnotateTap: (PdfSelectionPoint) -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val latestTransform by rememberUpdatedState(transform)
  val latestOnTransformChanged by rememberUpdatedState(onTransformChanged)
  val latestOnSelectionChanged by rememberUpdatedState(onSelectionChanged)
  val latestOnAnnotateTap by rememberUpdatedState(onAnnotateTap)

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
        modifier = Modifier.fillMaxSize().pointerInput(page, bounds, selectionEnabled, gesturesEnabled, annotateTapEnabled) {
          awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (annotateTapEnabled && down.type != PointerType.Mouse) {
              val tap = PdfSelectionPoint(down.position.x, down.position.y)
              down.consume()
              latestOnAnnotateTap(tap)
              do {
                val event = awaitPointerEvent()
                event.changes.forEach { if (it.pressed) it.consume() }
              } while (event.changes.any { it.id == down.id && it.pressed })
            } else if (selectionEnabled && down.type != PointerType.Mouse) {
              val start = PdfSelectionPoint(down.position.x, down.position.y)
              down.consume()
              var deliveredValid = false
              var lastEnd: PdfSelectionPoint? = null
              do {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change != null) {
                  val endPoint = PdfSelectionPoint(change.position.x, change.position.y)
                  lastEnd = endPoint
                  // Deliver valid regions live; defer invalid to drag end so a
                  // tap does not flash a message before it is confirmed and a
                  // failed re-drag keeps the previous valid selection.
                  if (endPoint.x != start.x || endPoint.y != start.y) {
                    val region = mapPdfSelectionToOcrRegion(
                      start = start,
                      end = endPoint,
                      bounds = bounds,
                      transform = latestTransform,
                    )
                    if (region != null) {
                      deliveredValid = true
                      latestOnSelectionChanged(region)
                    }
                  }
                  if (change.pressed) change.consume()
                }
              } while (event.changes.any { it.id == down.id && it.pressed })
              // Report a tap or fully invalid drag once at drag end so
              // ReaderOcrActions can show the recoverable message.
              val end = lastEnd
              if (!deliveredValid && (end == null ||
                ((end.x == start.x && end.y == start.y) ||
                mapPdfSelectionToOcrRegion(
                  start = start,
                  end = end,
                  bounds = bounds,
                  transform = latestTransform,
                ) == null))
              ) {
                latestOnSelectionChanged(null)
              }
            } else if (gesturesEnabled) {
              do {
                val event = awaitPointerEvent()
                val touchChanges = event.changes.filter {
                  it.type == PointerType.Touch && it.pressed && it.previousPressed
                }
                val pointers = touchChanges.map { change ->
                  PdfViewportPointer(change.id.value, change.position.x, change.position.y)
                }
                val previousPointers = touchChanges.map { change ->
                  PdfViewportPointer(change.id.value, change.previousPosition.x, change.previousPosition.y)
                }
                val gesture = PdfViewportTransform.calculateTwoPointerGesture(previousPointers, pointers)
                if (gesture != null) {
                  latestOnTransformChanged(PdfViewportTransform.applyGesture(
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
                  ))
                  touchChanges.forEach { it.consume() }
                }
              } while (event.changes.any { it.pressed })
            } else {
              do {
                val event = awaitPointerEvent()
                event.changes.forEach { it.consume() }
              } while (event.changes.any { it.pressed })
            }
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
          OcrSelectionOverlay(selection)
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
  historyEnabled: Boolean = false,
  onHistory: () -> Unit = {},
  sourceDirectoryUri: Uri? = null,
  animationEnabled: Boolean,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val markwon = remember(context, sourceDirectoryUri) {
    Markwon.builder(context)
      .usePlugin(TablePlugin.create(context))
      .usePlugin(
        ImagesPlugin.create().addSchemeHandler(
          NoteAssetSchemeHandler(context.contentResolver, sourceDirectoryUri),
        ),
      )
      .build()
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
        historyEnabled = historyEnabled,
        onHistory = onHistory,
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
          val (previewMarkdown, isTruncated) = remember(content.markdown) {
            NotePage.preview(content.markdown)
          }
          Column(modifier = Modifier.fillMaxSize()) {
            AndroidView(
              modifier = Modifier.weight(1f).fillMaxWidth(),
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
                markwon.setMarkdown(view, NotePreviewRewrite.forPreview(previewMarkdown))
              },
            )
            if (isTruncated) {
              Text(
                text = stringResource(
                  R.string.note_preview_truncated,
                  content.markdown.length,
                  previewMarkdown.length,
                ),
                modifier = Modifier.padding(top = 4.dp).testTag("note_preview_truncated"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
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
  historyEnabled: Boolean = false,
  onHistory: () -> Unit = {},
) {
  val actions: @Composable (Boolean, Modifier) -> Unit = { vertical, modifier ->
    NotePanelHeaderActions(
      saving = saving,
      writable = writable,
      dirty = dirty,
      vertical = vertical,
      onReload = onReload,
      onSave = onSave,
      historyEnabled = historyEnabled,
      onHistory = onHistory,
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
  historyEnabled: Boolean = false,
  onHistory: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val reloadButton: @Composable (Modifier) -> Unit = { buttonModifier ->
    Button(onClick = onReload, enabled = !saving, modifier = buttonModifier) {
      Text(stringResource(R.string.reload))
    }
  }
  val historyButton: @Composable (Modifier) -> Unit = { buttonModifier ->
    TextButton(onClick = onHistory, enabled = !saving && historyEnabled, modifier = buttonModifier) {
      Text(stringResource(R.string.note_history))
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
      historyButton(Modifier.fillMaxWidth())
      saveButton(Modifier.fillMaxWidth())
    }
  } else {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      reloadButton(Modifier)
      historyButton(Modifier)
      saveButton(Modifier)
    }
  }
}

@Composable
internal fun NoteHistoryDialog(
  page: Int,
  projectId: String,
  currentDraft: String,
  baseRevision: String,
  onDismiss: () -> Unit,
  onRestore: (String) -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var entries by remember(projectId, page) { mutableStateOf<List<NoteVersion>>(emptyList()) }
  var loadError by remember(projectId, page) { mutableStateOf<String?>(null) }
  var selected by remember(projectId, page) { mutableStateOf<NoteVersion?>(null) }
  var preview by remember(projectId, page) { mutableStateOf("") }
  var confirming by remember { mutableStateOf(false) }
  var restoreError by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(projectId, page) {
    loadError = null
    entries = withContext(Dispatchers.IO) {
      runCatching { NoteHistoryMirror.entries(context.filesDir, projectId, page) }.getOrElse {
        loadError = context.getString(R.string.note_history_failed, it.message ?: "")
        emptyList()
      }
    }
    selected = entries.firstOrNull()
  }
  LaunchedEffect(selected) {
    preview = selected?.let {
      withContext(Dispatchers.IO) {
        runCatching { NoteHistoryMirror.readVersion(context.filesDir, projectId, it.id) }
          .getOrElse { "" }
      }
    }.orEmpty()
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.note_history_title, page)) },
    text = {
      Column(
        modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        loadError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (loadError == null && entries.isEmpty()) {
          Text(stringResource(R.string.note_history_empty))
        }
        entries.forEach { entry ->
          val label = java.text.DateFormat.getDateTimeInstance().format(java.util.Date(entry.created)) +
            " · ${entry.bytes}B"
          TextButton(
            modifier = Modifier.testTag("note_history_entry_${entry.id}"),
            onClick = { selected = entry },
          ) {
            Text(
              label,
              color = if (entry.id == selected?.id) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.onSurface,
            )
          }
        }
        if (selected != null) {
          Text(preview.ifBlank { stringResource(R.string.note_history_empty) })
        }
        restoreError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
      }
    },
    confirmButton = {
      TextButton(
        modifier = Modifier.testTag("note_history_restore"),
        enabled = selected != null,
        onClick = { confirming = true },
      ) { Text(stringResource(R.string.note_history_restore)) }
    },
    dismissButton = {
      TextButton(modifier = Modifier.testTag("note_history_close"), onClick = onDismiss) {
        Text(stringResource(R.string.close))
      }
    },
  )
  if (confirming && selected != null) {
    AlertDialog(
      onDismissRequest = { confirming = false },
      title = { Text(stringResource(R.string.note_history_confirm_title)) },
      text = { Text(stringResource(R.string.note_history_confirm_detail)) },
      confirmButton = {
        TextButton(
          modifier = Modifier.testTag("note_history_confirm"),
          onClick = {
            val version = selected ?: return@TextButton
            scope.launch {
              try {
                val restored = withContext(Dispatchers.IO) {
                  NoteHistoryMirror.snapshotPage(
                    context.filesDir, projectId, page, currentDraft,
                  )
                  NoteHistoryMirror.restore(
                    context.filesDir, projectId, version.id, page, baseRevision, currentDraft,
                  )
                }
                onRestore(restored)
              } catch (_: IllegalStateException) {
                restoreError = context.getString(R.string.note_history_conflict)
                confirming = false
              } catch (error: Exception) {
                restoreError = context.getString(
                  R.string.note_history_failed, error.message ?: "",
                )
                confirming = false
              }
            }
          },
        ) { Text(stringResource(R.string.note_history_restore)) }
      },
      dismissButton = {
        TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.cancel)) }
      },
    )
  }
}

@Composable
internal fun OcrResultDialog(
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
    confirmButton = {
      TextButton(modifier = Modifier.testTag("ocr_result_close"), onClick = onDismiss) {
        Text(stringResource(R.string.close))
      }
    },
  )
}

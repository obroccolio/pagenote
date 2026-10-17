# Android Page Tutor Preview and Append

## Scope

Add the first user-facing Android Agent workflow: explain one open PDF page with the configured OpenAI-compatible model, preview the Markdown response, and append it to that page's note only after explicit user confirmation. Keep the feature Android-only and keep all Android SPEC artifacts under `android/docs/spec/`.

## User Flow

1. With a PDF project open, the user opens the page tutor and may edit a default explanation instruction.
2. Before sending a request, the app saves a dirty current-page note. A failed save aborts the request.
3. The app reads selectable PDF text for the current page and its immediate neighbors. If the current page has no usable native text, the app runs the bundled offline OCR on that page before using the network. It never sends an empty source page.
4. The app sends one page-scoped request through the configured OpenAI-compatible chat client. The current page is the primary material; available previous/next pages are context only. Each page is capped as specified below. Project material is untrusted study content, not tool instructions. No local Agent tools are exposed to this request.
5. The model response is shown as a preview and does not modify project files automatically.
6. On explicit confirmation, the app appends the explanation to the current page using the page revision captured before the request. Other pages are preserved. A stale revision rejects the append and leaves the latest note untouched.

## Contracts

- The page tutor uses `ChatClient.complete` directly; it must not parse ordinary Markdown as `DONE` or as an Agent tool call.
- Requests carry document ID, one-based target page, total page count, user instruction, current source text, current note, note revision, and at most the immediately adjacent previous and next source pages.
- Each PDF page's text sent to the model is capped at 20,000 Kotlin string characters, matching desktop `prepare_task`'s default `maxChars`. Truncation is explicit in the preview warnings; it is never silent.
- A missing API key opens the existing model settings flow without sending a request. Model, PDF, OCR, timeout, HTTP, and empty-response failures are surfaced without changing notes.
- OCR-derived input retains its warnings. The preview tells the user to verify OCR-based explanations against the original PDF when the OCR result is incomplete, warned, or low-confidence.
- Apply is disabled for read-only notes. The append operation checks `expectedRevision` against the target page immediately before writing.
- Repeated explanations append as separate localized `AI explanation` sections; they never replace existing page content.

## Acceptance (EARS)

- When the user requests an explanation without a valid model configuration, the Android app shall open model settings and shall not call the model.
- When the current page has native text, the Android app shall use it without invoking OCR.
- When the current page has no usable native text, the Android app shall use bundled offline OCR before calling the model and shall retain any OCR warnings in the preview.
- When the user requests an explanation, the Android app shall send only the target page and available immediate neighbors, with the current page's note and revision, and shall not use the Agent tool loop.
- When the model returns Markdown, the Android app shall show it in a preview while leaving project notes unchanged.
- When the user confirms an append and the page revision is unchanged, the Android app shall append only to that page and preserve every other page.
- When the page revision changed after the request began, the Android app shall reject the append and preserve the latest note contents.
- When input text exceeds 20,000 characters for a page, the Android app shall mark that page material as truncated and display the warning before apply.
- When the model call, OCR, source extraction, or write fails, the Android app shall show a clear error and shall not claim that the note was updated.

## Non-Goals

- Editing AI review records or marking reviews applied; that is a separate revision workflow.
- Automatic background generation across pages.
- Modifying PDF source content, annotations, or pen strokes.
- Replacing the existing AgentRunner protocol or adding model tools to the page tutor.

## Verification

- Agent unit tests prove request scope, page/revision material, raw Markdown response handling, explicit text truncation, and failure behavior with a fake `ChatClient`.
- Android JVM tests prove native-text preference, OCR fallback only when native text is unavailable/empty, OCR failure stopping before the model call, and low-confidence warning retention.
- Note editor unit tests prove append semantics, preservation of other pages, empty-page behavior, and stale-revision rejection.
- Compose instrumentation tests prove the preview, retry/error state, and explicit append action are reachable and correctly gated.
- Run Android unit tests, compile Android instrumentation tests, and build debug APKs. A live model endpoint and a device/emulator are additional acceptance checks, not substitutes for the deterministic tests.

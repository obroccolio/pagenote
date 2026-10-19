# Project Attachments Implementation Plan

**Goal:** Enable project-local attachment browsing, importing, safe reference-aware deletion, and offline image insertion on Android.

**Architecture:** Add a SAF-backed attachment store scoped to the current project's `src/assets/`, with pure validation/reference helpers. Integrate it through the existing File menu and Compose dialog; reuse the existing Markdown image renderer and note draft/write contracts.

**Spec:** `features/project-attachments.md`

## Tasks

- [ ] Add JVM tests for attachment name/type validation, note/annotation reference detection, and delete eligibility.
- [ ] Implement SAF list/import/delete operations with collision-resistant UUID names and partial-write cleanup reporting.
- [ ] Add the project attachment dialog, import picker, image-to-Markdown insertion, and explicit deletion confirmation.
- [ ] Add one API 34 SAF Activity regression for list/import/cancel/reference protection/delete/provider failure.
- [ ] Run focused JVM and AVD tests, Android test compilation, app build, and `git diff --check`.

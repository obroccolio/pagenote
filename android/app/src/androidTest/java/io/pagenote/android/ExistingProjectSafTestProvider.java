package io.pagenote.android;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ExistingProjectSafTestProvider extends ContentProvider {
  public static final String AUTHORITY = "io.pagenote.android.test.existing-project";
  public static final String ROOT_ID = "fixture";
  public static final String SRC_ID = ROOT_ID + "/src";
  public static final String MANIFEST_ID = ROOT_ID + "/project.pagenote";
  public static final String PDF_ID = SRC_ID + "/pattern-recognition.pdf";
  public static final String NOTES_ID = SRC_ID + "/notes.md";
  public static final String REVIEWS_ID = SRC_ID + "/ai-reviews.json";
  public static final String METHOD_INSTALL = "installFixture";
  public static final String METHOD_REPLACE_NOTES = "replaceNotes";
  public static final String METHOD_GET_REVIEW_WRITE_COUNT = "getReviewWriteCount";
  public static final String METHOD_PAUSE_NEXT_REVIEW_READ = "pauseNextReviewRead";
  public static final String METHOD_WAIT_REVIEW_SNAPSHOT = "waitReviewSnapshot";
  public static final String METHOD_RELEASE_REVIEW_READ = "releaseReviewRead";
  private static final String METHOD_FIND_DOCUMENT_PATH = "android:findDocumentPath";
  private static final String EXTRA_URI = "uri";
  private static final String EXTRA_RESULT = "result";
  public static final String EXTRA_MANIFEST = "manifest";
  public static final String EXTRA_PDF = "pdf";
  public static final String EXTRA_NOTES = "notes";
  public static final String EXTRA_REVIEWS = "reviews";
  public static final String EXTRA_COUNT = "count";

  private static final String FIXTURE_DIR = "pagenote-existing-project-fixture";
  private static final String MANIFEST_FILE = "project.pagenote";
  private static final String PDF_FILE = "pattern-recognition.pdf";
  private static final String NOTES_FILE = "notes.md";
  private static final String REVIEWS_FILE = "ai-reviews.json";
  private int reviewWriteCount;
  private static volatile ReviewReadPause reviewReadPause;

  private static final class ReviewReadPause {
    final AtomicBoolean claimed = new AtomicBoolean();
    final CountDownLatch snapshotReady = new CountDownLatch(1);
    final CountDownLatch release = new CountDownLatch(1);
  }

  @Override
  public boolean onCreate() {
    return true;
  }

  @Override
  public Bundle call(String method, String arg, Bundle extras) {
    try {
      if (METHOD_INSTALL.equals(method)) {
        writeFixtureFile(MANIFEST_FILE, requiredBytes(extras, EXTRA_MANIFEST));
        writeFixtureFile(PDF_FILE, requiredBytes(extras, EXTRA_PDF));
        writeFixtureFile(NOTES_FILE, requiredBytes(extras, EXTRA_NOTES));
        writeFixtureFile(REVIEWS_FILE, requiredBytes(extras, EXTRA_REVIEWS));
        reviewWriteCount = 0;
        releaseReviewReadPause();
        return Bundle.EMPTY;
      }
      if (METHOD_REPLACE_NOTES.equals(method)) {
        writeFixtureFile(NOTES_FILE, requiredBytes(extras, EXTRA_NOTES));
        return Bundle.EMPTY;
      }
      if (METHOD_GET_REVIEW_WRITE_COUNT.equals(method)) {
        Bundle result = new Bundle();
        result.putInt(EXTRA_COUNT, reviewWriteCount);
        return result;
      }
      if (METHOD_PAUSE_NEXT_REVIEW_READ.equals(method)) {
        reviewReadPause = new ReviewReadPause();
        return Bundle.EMPTY;
      }
      if (METHOD_WAIT_REVIEW_SNAPSHOT.equals(method)) {
        ReviewReadPause pause = reviewReadPause;
        Bundle result = new Bundle();
        boolean ready = false;
        if (pause != null) {
          try {
            ready = pause.snapshotReady.await(5, TimeUnit.SECONDS);
          } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
          }
        }
        result.putBoolean("ready", ready);
        return result;
      }
      if (METHOD_RELEASE_REVIEW_READ.equals(method)) {
        releaseReviewReadPause();
        return Bundle.EMPTY;
      }
      if (METHOD_FIND_DOCUMENT_PATH.equals(method)) {
        Uri uri = extras == null ? null : extras.getParcelable(EXTRA_URI);
        if (uri == null) return null;
        Bundle result = new Bundle();
        result.putParcelable(EXTRA_RESULT, findPath(DocumentsContract.getDocumentId(uri)));
        return result;
      }
      return super.call(method, arg, extras);
    } catch (IOException error) {
      throw new IllegalStateException("Unable to prepare SAF fixture", error);
    }
  }

  @Override
  public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
    try {
      if ("children".equals(uri.getLastPathSegment())) {
        return queryChildDocuments(DocumentsContract.getDocumentId(uri), projection);
      }
      return queryDocument(DocumentsContract.getDocumentId(uri), projection);
    } catch (FileNotFoundException error) {
      return null;
    }
  }

  private Cursor queryDocument(String documentId, String[] projection) throws FileNotFoundException {
    Map<String, Object> values = documentValues(documentId);
    if (values == null) throw new FileNotFoundException(documentId);
    String[] columns = projection == null ? values.keySet().toArray(new String[0]) : projection;
    MatrixCursor cursor = new MatrixCursor(columns);
    addRow(cursor, columns, values);
    return cursor;
  }

  private Cursor queryChildDocuments(String parentDocumentId, String[] projection) throws FileNotFoundException {
    List<String> children;
    if (ROOT_ID.equals(parentDocumentId)) {
      children = Arrays.asList(MANIFEST_ID, SRC_ID);
    } else if (SRC_ID.equals(parentDocumentId)) {
      children = Arrays.asList(PDF_ID, NOTES_ID, REVIEWS_ID);
    } else {
      throw new FileNotFoundException(parentDocumentId);
    }
    String[] columns = projection == null ? new String[] {
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_FLAGS,
      } : projection;
    MatrixCursor cursor = new MatrixCursor(columns);
    for (String child : children) addRow(cursor, columns, documentValues(child));
    return cursor;
  }

  @Override
  public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
    String documentId = DocumentsContract.getDocumentId(uri);
    File file = fileFor(documentId);
    if (file == null || !file.exists()) throw new FileNotFoundException(documentId);
    if (REVIEWS_ID.equals(documentId) && mode.contains("w")) reviewWriteCount++;
    if (REVIEWS_ID.equals(documentId) && !mode.contains("w")) {
      ReviewReadPause pause = reviewReadPause;
      if (pause != null && pause.claimed.compareAndSet(false, true)) {
        return pausedReviewRead(file, pause);
      }
    }
    return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode));
  }

  private ParcelFileDescriptor pausedReviewRead(File file, ReviewReadPause pause) throws FileNotFoundException {
    try {
      byte[] snapshot = readFile(file);
      ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
      Thread writer = new Thread(() -> {
        try (ParcelFileDescriptor.AutoCloseOutputStream output =
                 new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
          output.write(snapshot);
          output.flush();
          pause.snapshotReady.countDown();
          pause.release.await(10, TimeUnit.SECONDS);
        } catch (IOException | InterruptedException error) {
          if (error instanceof InterruptedException) Thread.currentThread().interrupt();
          pause.snapshotReady.countDown();
        }
      }, "saf-test-review-snapshot");
      writer.start();
      return pipe[0];
    } catch (IOException error) {
      throw new FileNotFoundException(error.getMessage());
    }
  }

  private byte[] readFile(File file) throws IOException {
    try (FileInputStream input = new FileInputStream(file)) {
      byte[] buffer = new byte[(int) file.length()];
      int offset = 0;
      while (offset < buffer.length) {
        int count = input.read(buffer, offset, buffer.length - offset);
        if (count < 0) break;
        offset += count;
      }
      return offset == buffer.length ? buffer : Arrays.copyOf(buffer, offset);
    }
  }

  private static void releaseReviewReadPause() {
    ReviewReadPause pause = reviewReadPause;
    if (pause != null) pause.release.countDown();
    reviewReadPause = null;
  }

  @Override
  public String getType(Uri uri) {
    Map<String, Object> values = documentValues(DocumentsContract.getDocumentId(uri));
    return values == null ? null : (String) values.get(DocumentsContract.Document.COLUMN_MIME_TYPE);
  }

  @Override
  public Uri insert(Uri uri, ContentValues values) {
    return null;
  }

  @Override
  public int delete(Uri uri, String selection, String[] selectionArgs) {
    return 0;
  }

  @Override
  public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
    return 0;
  }

  private DocumentsContract.Path findPath(String documentId) throws FileNotFoundException {
    List<String> path;
    if (ROOT_ID.equals(documentId)) {
      path = Collections.singletonList(ROOT_ID);
    } else if (MANIFEST_ID.equals(documentId) || SRC_ID.equals(documentId)) {
      path = Arrays.asList(ROOT_ID, documentId);
    } else if (PDF_ID.equals(documentId) || NOTES_ID.equals(documentId) || REVIEWS_ID.equals(documentId)) {
      path = Arrays.asList(ROOT_ID, SRC_ID, documentId);
    } else {
      throw new FileNotFoundException(documentId);
    }
    return new DocumentsContract.Path(ROOT_ID, path);
  }

  private Map<String, Object> documentValues(String documentId) {
    if (ROOT_ID.equals(documentId)) return values(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID, ROOT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME, "Existing desktop project fixture",
        DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.MIME_TYPE_DIR,
        DocumentsContract.Document.COLUMN_FLAGS, 0);
    if (SRC_ID.equals(documentId)) return values(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID, SRC_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME, "src",
        DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.MIME_TYPE_DIR,
        DocumentsContract.Document.COLUMN_FLAGS, 0);
    if (MANIFEST_ID.equals(documentId)) return fileValues(
        MANIFEST_ID, "project.pagenote", "application/json", 0, MANIFEST_FILE);
    if (PDF_ID.equals(documentId)) return fileValues(
        PDF_ID, "pattern-recognition.pdf", "application/pdf", 0, PDF_FILE);
    if (NOTES_ID.equals(documentId)) return fileValues(
        NOTES_ID, "notes.md", "text/markdown", DocumentsContract.Document.FLAG_SUPPORTS_WRITE, NOTES_FILE);
    if (REVIEWS_ID.equals(documentId)) return fileValues(
        REVIEWS_ID, "ai-reviews.json", "application/json", DocumentsContract.Document.FLAG_SUPPORTS_WRITE, REVIEWS_FILE);
    return null;
  }

  private Map<String, Object> fileValues(
      String id, String name, String mime, int flags, String fileName
  ) {
    File file = new File(fixtureDir(), fileName);
    return values(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID, id,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME, name,
        DocumentsContract.Document.COLUMN_MIME_TYPE, mime,
        DocumentsContract.Document.COLUMN_FLAGS, flags,
        DocumentsContract.Document.COLUMN_SIZE, file.length(),
        DocumentsContract.Document.COLUMN_LAST_MODIFIED, file.lastModified());
  }

  private Map<String, Object> values(Object... pairs) {
    Map<String, Object> values = new HashMap<>();
    for (int i = 0; i < pairs.length; i += 2) values.put((String) pairs[i], pairs[i + 1]);
    return values;
  }

  private void addRow(MatrixCursor cursor, String[] columns, Map<String, Object> values) {
    Object[] row = new Object[columns.length];
    for (int index = 0; index < columns.length; index++) row[index] = values.get(columns[index]);
    cursor.addRow(row);
  }

  private byte[] requiredBytes(Bundle extras, String key) {
    byte[] value = extras == null ? null : extras.getByteArray(key);
    if (value == null) throw new IllegalArgumentException("Missing fixture bytes: " + key);
    return value;
  }

  private void writeFixtureFile(String name, byte[] contents) throws IOException {
    File directory = fixtureDir();
    if (!directory.exists() && !directory.mkdirs()) throw new IOException("Could not create fixture directory");
    try (FileOutputStream output = new FileOutputStream(new File(directory, name), false)) {
      output.write(contents);
    }
  }

  private File fixtureDir() {
    return new File(requireContext().getFilesDir(), FIXTURE_DIR);
  }

  private File fileFor(String documentId) {
    if (MANIFEST_ID.equals(documentId)) return new File(fixtureDir(), MANIFEST_FILE);
    if (PDF_ID.equals(documentId)) return new File(fixtureDir(), PDF_FILE);
    if (NOTES_ID.equals(documentId)) return new File(fixtureDir(), NOTES_FILE);
    if (REVIEWS_ID.equals(documentId)) return new File(fixtureDir(), REVIEWS_FILE);
    return null;
  }
}

package si.srtprevajalnik.gpt;

import android.app.Activity;
import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowContentResolver;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class MainActivityTest {
    @Test public void importedSelectionRetainsItsTargetEvenIfSpinnerChanges() throws Exception {
        Field target = MainActivity.class.getDeclaredField("targetLanguage"); target.setAccessible(true);
        android.widget.Spinner spinner = (android.widget.Spinner) target.get(activity);
        spinner.setSelection(TargetLanguage.CROATIAN.ordinal());
        activity.onActivityResult(100, Activity.RESULT_OK, files(1, SrtDocumentTest.SOURCE)); awaitIo();
        spinner.setSelection(TargetLanguage.GERMAN.ordinal());
        JobStore.Job job = new JobStore(activity).load().get(0);
        assertEquals("hr", job.targetLanguage);
        assertEquals("English-0.hr.srt", job.outputName());
    }
    @Test public void duplicateKeyedApiReplyIsRejectedOnAndroidInsteadOfOverwritingDialogue() {
        List<SrtDocument.Line> lines = List.of(new SrtDocument.Line("c270_l0", "Hello!", "Hello!", "", List.of()));
        String reply = "{\"translations\":{\"c270_l0\":\"Živjo!\",\"c270_l0\":\"Drug odgovor\"}}";
        assertThrows(IllegalArgumentException.class, () -> OpenAiTranslator.parseResponse(reply, lines));
    }
    public static class FilesProvider extends ContentProvider {
        File directory;
        @Override public boolean onCreate() { directory = new File(getContext().getCacheDir(), "fixtures"); directory.mkdirs(); return true; }
        @Override public Cursor query(Uri uri, String[] p, String s, String[] args, String sort) {
            MatrixCursor cursor = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME}); cursor.addRow(new Object[]{uri.getLastPathSegment()}); return cursor;
        }
        @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
            return ParcelFileDescriptor.open(new File(directory, uri.getLastPathSegment()), ParcelFileDescriptor.parseMode(mode));
        }
        @Override public String getType(Uri uri) { return "application/x-subrip"; }
        @Override public Uri insert(Uri u, ContentValues v) { return null; }
        @Override public int delete(Uri u, String s, String[] a) { return 0; }
        @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
    }
    public static class FolderProvider extends android.provider.DocumentsProvider {
        File directory;
        @Override public boolean onCreate() { directory = new File(getContext().getCacheDir(), "exported"); directory.mkdirs(); return true; }
        @Override public Cursor queryRoots(String[] projection) { return new MatrixCursor(new String[]{"root_id"}); }
        @Override public Cursor queryDocument(String id, String[] projection) {
            MatrixCursor cursor = new MatrixCursor(new String[]{"document_id", "_display_name", "mime_type", "flags", "_size"});
            cursor.addRow(new Object[]{id, id, "root".equals(id) ? "vnd.android.document/directory" : "application/x-subrip", 6, 0}); return cursor;
        }
        @Override public Cursor queryChildDocuments(String id, String[] projection, String sort) { return new MatrixCursor(new String[]{"document_id"}); }
        @Override public boolean isChildDocument(String parent, String child) { return "root".equals(parent) && new File(directory, child).exists(); }
        @Override public ParcelFileDescriptor openDocument(String id, String mode, CancellationSignal signal) throws FileNotFoundException {
            return ParcelFileDescriptor.open(new File(directory, id), ParcelFileDescriptor.parseMode(mode));
        }
        @Override public String createDocument(String parent, String mime, String name) throws FileNotFoundException {
            File file = new File(directory, name);
            try { if (!file.createNewFile()) throw new IOException("Already exists"); return name; }
            catch (IOException e) { throw new FileNotFoundException(e.getMessage()); }
        }
        @Override public void deleteDocument(String id) { new File(directory, id).delete(); }
    }
    private FilesProvider provider;
    private ActivityController<MainActivity> controller;
    private MainActivity activity;
    @Before public void setUp() throws Exception {
        android.content.pm.ProviderInfo info = new android.content.pm.ProviderInfo(); info.authority = "srt.test";
        provider = Robolectric.buildContentProvider(FilesProvider.class).create(info).get();
        ShadowContentResolver.registerProviderInternal("srt.test", provider);
        controller = Robolectric.buildActivity(MainActivity.class).setup(); activity = controller.get();
        new JobStore(activity).save(List.of());
    }
    @After public void tearDown() { controller.pause().stop().destroy(); }
    private void awaitIo() throws Exception {
        Field field = MainActivity.class.getDeclaredField("io"); field.setAccessible(true);
        ((ExecutorService)field.get(activity)).submit(() -> {}).get(15, TimeUnit.SECONDS);
        shadowOf(Looper.getMainLooper()).idle();
    }
    private Intent files(int count, String text) throws Exception {
        ClipData clip = null;
        for (int i = 0; i < count; i++) {
            String name = "English-" + i + ".srt";
            try (FileOutputStream out = new FileOutputStream(new File(provider.directory, name))) { out.write(text.getBytes(StandardCharsets.UTF_8)); }
            Uri uri = Uri.parse("content://srt.test/" + name);
            if (clip == null) clip = ClipData.newRawUri("SRT", uri); else clip.addItem(new ClipData.Item(uri));
        }
        Intent intent = new Intent(); intent.setClipData(clip); return intent;
    }
    @Test public void imports25TranslatesWithFakeApiAndExportsReadableZip() throws Exception {
        activity.onActivityResult(100, Activity.RESULT_OK, files(25, SrtDocumentTest.SOURCE)); awaitIo();
        JobStore store = new JobStore(activity); List<JobStore.Job> jobs = store.load(); assertEquals(25, jobs.size());
        AtomicInteger calls = new AtomicInteger();
        for (JobStore.Job job : jobs) {
            String output = TranslationEngine.translate(SrtDocument.parse(JobStore.read(new FileInputStream(store.source(job)))), Map.of(), SrtDocumentTest.fake(calls), () -> {}, p -> {});
            JobStore.write(store.output(job), output); job.status = "Končano"; job.done = job.total;
        }
        store.save(jobs);
        activity.onActivityResult(102, Activity.RESULT_OK, new Intent().setData(Uri.parse("content://srt.test/results.zip"))); awaitIo();
        int count = 0;
        try (ZipInputStream zip = new ZipInputStream(new FileInputStream(new File(provider.directory, "results.zip")), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                assertTrue(entry.getName().endsWith(".sl.srt"));
                String output = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(output.contains("Živjo!")); assertTrue(output.contains("Kako si?")); assertTrue(output.contains("Dobro jutro."));
                assertEquals(3, SrtDocument.parse(output).lines.size()); count++;
            }
        }
        assertEquals(25, calls.get()); assertEquals(25, count);
    }
    @Test public void rejects26FilesAndPreservesPreviousQueue() throws Exception {
        activity.onActivityResult(100, Activity.RESULT_OK, files(1, SrtDocumentTest.SOURCE)); awaitIo();
        String id = new JobStore(activity).load().get(0).id;
        activity.onActivityResult(100, Activity.RESULT_OK, files(26, SrtDocumentTest.SOURCE)); awaitIo();
        assertEquals(id, new JobStore(activity).load().get(0).id);
    }
    @Test public void malformedSelectionPreservesPreviousQueue() throws Exception {
        activity.onActivityResult(100, Activity.RESULT_OK, files(1, SrtDocumentTest.SOURCE)); awaitIo();
        String id = new JobStore(activity).load().get(0).id;
        activity.onActivityResult(100, Activity.RESULT_OK, files(2, "invalid subtitle")); awaitIo();
        assertEquals(id, new JobStore(activity).load().get(0).id);
    }
    @Test public void cancelledPickerKeepsQueue() throws Exception {
        activity.onActivityResult(100, Activity.RESULT_OK, files(1, SrtDocumentTest.SOURCE)); awaitIo();
        activity.onActivityResult(100, Activity.RESULT_CANCELED, null); awaitIo();
        assertEquals(1, new JobStore(activity).load().size());
    }
    @Test public void activityRecreationKeepsImportedSource() throws Exception {
        activity.onActivityResult(100, Activity.RESULT_OK, files(1, SrtDocumentTest.SOURCE)); awaitIo();
        controller.recreate(); activity = controller.get();
        JobStore store = new JobStore(activity); assertEquals(1, store.load().size());
        assertEquals(SrtDocumentTest.SOURCE, JobStore.read(new FileInputStream(store.source(store.load().get(0)))));
    }
    @Test public void savesAllCompletedSubtitlesIntoSelectedAndroidFolder() throws Exception {
        android.content.pm.ProviderInfo info = new android.content.pm.ProviderInfo();
        info.authority = "srt.folder"; info.exported = true; info.grantUriPermissions = true;
        info.readPermission = "android.permission.MANAGE_DOCUMENTS"; info.writePermission = info.readPermission;
        FolderProvider folder = Robolectric.buildContentProvider(FolderProvider.class).create(info).get();
        ShadowContentResolver.registerProviderInternal("srt.folder", folder);
        activity.onActivityResult(100, Activity.RESULT_OK, files(2, SrtDocumentTest.SOURCE)); awaitIo();
        JobStore store = new JobStore(activity); List<JobStore.Job> jobs = store.load(); assertEquals(2, jobs.size());
        for (JobStore.Job job : jobs) {
            JobStore.write(store.output(job), TranslationEngine.translate(SrtDocument.parse(SrtDocumentTest.SOURCE), Map.of(), SrtDocumentTest.fake(new AtomicInteger()), () -> {}, p -> {}));
            job.status = "Končano";
        }
        store.save(jobs);
        activity.onActivityResult(101, Activity.RESULT_OK, new Intent().setData(Uri.parse("content://srt.folder/tree/root"))); awaitIo();
        Field status = MainActivity.class.getDeclaredField("notice"); status.setAccessible(true);
        assertEquals(((android.widget.TextView)status.get(activity)).getText().toString(), 2, Objects.requireNonNull(folder.directory.listFiles()).length);
        for (JobStore.Job job : jobs) {
            String output = JobStore.read(new FileInputStream(new File(folder.directory, job.outputName())));
            assertTrue(output.contains("Živjo!")); assertEquals(3, SrtDocument.parse(output).lines.size());
        }
    }
}

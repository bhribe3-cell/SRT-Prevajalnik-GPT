package si.srtprevajalnik.gpt;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.concurrent.*;

/** Video tabs share the API key but use independent, persisted movie jobs. */
public final class VideoActivity extends Activity {
    private static final int PICK_MOVIE = 310, SAVE_SRT = 311;
    private static final java.util.concurrent.atomic.AtomicBoolean FILE_IO = MainActivity.FILE_IO;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean speech;
    private String initialTarget;
    private Spinner target;
    private Button choose, start, stop, save;
    private TextView status, notice, detail;
    private ProgressBar progress;
    private final Runnable poll = new Runnable() { public void run() { refresh(); handler.postDelayed(this, 700); } };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        speech = state != null ? state.getBoolean("speech") : getIntent().getBooleanExtra("speech", false);
        initialTarget = getIntent().getStringExtra("target");
        buildScreen();
    }
    private void buildScreen() {
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(Color.rgb(244,247,245));
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(20), dp(12), dp(20), dp(28));
        scroll.addView(root); setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); return insets;
        });
        text(root, "SRT Prevajalnik GPT", 26, true);
        LinearLayout tabs = new LinearLayout(this); tabs.setOrientation(LinearLayout.HORIZONTAL); root.addView(tabs);
        tab(tabs, "SRT", v -> { startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)); finish(); });
        tab(tabs, "Slika", v -> switchMode(false)); tab(tabs, "Govor", v -> switchMode(true));
        text(root, speech ? "Govor iz filma" : "Podnapisi v sliki filma", 22, true);
        text(root, speech
            ? "Film ostane na telefonu. Njegov zvok se po krajših odsekih pošlje OpenAI za prepis in prevod v slovenščino. Časi so približni po štirisekundnih odsekih; rezultat preglej."
            : "Film ostane na telefonu. Izbrane sličice se pošljejo OpenAI za prepoznavanje in prevod. Vzorčenje na 0,5 sekunde lahko spregleda kratke podnapise; rezultat preglej.", 15, false);
        if (!speech) {
            text(root, "Ciljni jezik za nov film", 14, true);
            target = new Spinner(this); target.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, TargetLanguage.values()));
            if (initialTarget != null) try { target.setSelection(TargetLanguage.fromCode(initialTarget).ordinal()); } catch (IllegalArgumentException ignored) {}
            root.addView(target);
        } else target = null;
        choose = button(root, "Izberi film (do 20 GB)", v -> chooseMovie());
        start = button(root, "Začni / nadaljuj", v -> begin());
        stop = button(root, "Ustavi", v -> {
            startService(new Intent(this, VideoProcessingService.class).setAction("cancel")); notice.setText("Ustavljam obdelavo …");
        });
        status = text(root, "Film še ni izbran.", 16, true);
        detail = text(root, "", 14, false);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); root.addView(progress);
        save = button(root, "Shrani dokončane podnapise SRT", v -> saveAs());
        text(root, "Za obdelavo je potrebna povezava z internetom in API-ključ, shranjen v zavihku SRT. OpenAI API se obračuna posebej. Po prekinitvi lahko nadaljuješ; zadnja zahteva se lahko ponovi.", 13, false);
        notice = text(root, "Pripravljeno.", 14, false); notice.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        refresh();
    }
    private void switchMode(boolean newSpeech) {
        if (speech == newSpeech) return;
        speech = newSpeech; buildScreen();
    }
    private void chooseMovie() {
        if (FILE_IO.get() || VideoProcessingService.RUNNING.get() || TranslationService.RUNNING.get()) return;
        try {
            if (new VideoJobStore(this, speech).load() != null)
                new AlertDialog.Builder(this).setTitle("Izbereš drug film?").setMessage("Trenutni napredek tega zavihka in še neizvoženi rezultat bosta zamenjana.")
                    .setNegativeButton("Prekliči", null).setPositiveButton("Izberi film", (dialog, which) -> openPicker()).show();
            else openPicker();
        } catch (Exception e) { notice.setText(TranslationService.friendly(e)); }
    }
    private void openPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE)
            .putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"video/*", "application/octet-stream"})
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        launch(intent, PICK_MOVIE);
    }
    private void launch(Intent intent, int code) {
        try { startActivityForResult(intent, code); }
        catch (ActivityNotFoundException e) { notice.setText("Na telefonu ni izbirnika dokumentov."); }
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        if (requestCode == PICK_MOVIE) selectMovie(data.getData());
        else if (requestCode == SAVE_SRT) exportSrt(data.getData());
    }
    private String name(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) return SubtitleFiles.safeName(cursor.getString(0));
        }
        return "film";
    }
    private void selectMovie(Uri uri) {
        if (VideoProcessingService.RUNNING.get() || TranslationService.RUNNING.get() || !FILE_IO.compareAndSet(false, true)) return;
        final boolean mode = speech; final String code = mode ? "sl" : ((TargetLanguage)target.getSelectedItem()).code;
        notice.setText("Preverjam film …"); refresh();
        io.execute(() -> {
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                String movieName = name(uri); VideoJobStore.Job job;
                try (VideoFrameReader reader = new VideoFrameReader(this, uri)) {
                    job = new VideoJobStore.Job(uri.toString(), movieName, code, reader.durationMs, reader.sizeBytes);
                    job.total = mode ? SpeechTimeline.windowCount(reader.durationMs) : VideoTimeline.sampleCount(reader.durationMs, VideoTimeline.FRAME_INTERVAL_MS);
                }
                job.fingerprint = VideoFingerprint.read(this, uri);
                VideoJobStore store = new VideoJobStore(this, mode); store.clearResults(); store.save(job);
                handler.post(() -> notice.setText("Film je pripravljen. Izbran jezik: " + TargetLanguage.fromCode(code).display + "."));
            } catch (SecurityException e) { handler.post(() -> notice.setText("Telefon ni dovolil trajnega dostopa do filma. Izberi datoteko iz lokalne shrambe.")); }
            catch (Exception e) { handler.post(() -> notice.setText(TranslationService.friendly(e))); }
            finally { FILE_IO.set(false); handler.post(this::refresh); }
        });
    }
    private void begin() {
        if (FILE_IO.get() || VideoProcessingService.RUNNING.get() || TranslationService.RUNNING.get()) return;
        try {
            VideoJobStore.Job job = new VideoJobStore(this, speech).load();
            if (job == null) throw new IllegalArgumentException("Najprej izberi film.");
            if (KeyVault.load(this).isEmpty()) throw new IllegalArgumentException("Najprej v zavihku SRT shrani OpenAI API-ključ.");
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 312);
            startForegroundService(new Intent(this, VideoProcessingService.class).putExtra("speech", speech));
            notice.setText("Obdelava se začenja …");
        } catch (Exception e) { notice.setText(TranslationService.friendly(e)); }
    }
    private void saveAs() {
        try {
            VideoJobStore store = new VideoJobStore(this, speech); VideoJobStore.Job job = store.load();
            if (job == null || !"Končano".equals(job.status) || !store.outputFile().exists()) throw new IOException("Prevod še ni končan.");
            String name = job.name.replaceFirst("\\.[^.]+$", "") + "." + job.target + ".srt";
            launch(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/x-subrip").addCategory(Intent.CATEGORY_OPENABLE)
                .putExtra(Intent.EXTRA_TITLE, name), SAVE_SRT);
        } catch (Exception e) { notice.setText(TranslationService.friendly(e)); }
    }
    private void exportSrt(Uri uri) {
        if (VideoProcessingService.RUNNING.get() || TranslationService.RUNNING.get() || !FILE_IO.compareAndSet(false, true)) return;
        final boolean mode = speech; notice.setText("Shranjujem podnapise …"); refresh();
        io.execute(() -> {
            try {
                VideoJobStore store = new VideoJobStore(this, mode); VideoJobStore.Job job = store.load();
                if (job == null || !"Končano".equals(job.status)) throw new IOException("Prevod še ni končan.");
                try (InputStream input = new FileInputStream(store.outputFile()); OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
                    if (out == null) throw new IOException("Datoteke ni mogoče zapisati."); input.transferTo(out);
                }
                handler.post(() -> notice.setText("Podnapisi so shranjeni."));
            } catch (Exception e) { handler.post(() -> notice.setText("Shranjevanje ni uspelo: " + TranslationService.friendly(e))); }
            finally { FILE_IO.set(false); handler.post(this::refresh); }
        });
    }
    private void refresh() {
        if (status == null) return;
        try {
            VideoJobStore.Job job = new VideoJobStore(this, speech).load();
            boolean running = VideoProcessingService.RUNNING.get(), activeHere = running && Boolean.valueOf(speech).equals(VideoProcessingService.ACTIVE_SPEECH);
            boolean busy = running || TranslationService.RUNNING.get() || FILE_IO.get();
            choose.setEnabled(!busy); start.setEnabled(!busy && job != null && !"Končano".equals(job.status));
            stop.setVisibility(activeHere ? View.VISIBLE : View.GONE);
            save.setEnabled(!busy && job != null && "Končano".equals(job.status));
            if (target != null) target.setEnabled(!busy);
            if (job == null) { status.setText("Film še ni izbran."); detail.setText(""); progress.setProgress(0); return; }
            String current = !activeHere && (job.status.startsWith("Prepoznavam") || job.status.startsWith("Prepisujem") || "Prevajam".equals(job.status)) ? "Prekinjeno – nadaljuj" : job.status;
            status.setText(job.name + "\n" + current + " · " + job.done + "/" + job.total);
            long units = speech ? SpeechTimeline.windowCount(job.durationMs) : VideoTimeline.sampleCount(job.durationMs, VideoTimeline.FRAME_INTERVAL_MS);
            long requests = speech ? units : (units + VideoTimeline.MAX_FRAME_BATCH - 1) / VideoTimeline.MAX_FRAME_BATCH;
            detail.setText("Ciljni jezik: " + TargetLanguage.fromCode(job.target).display + " · " + (speech ? "zvočnih odsekov: " : "sličic: ") + units
                + "\nNačrtovanih zahtev za " + (speech ? "prepis: " : "prepoznavanje: ") + requests + " + prevajanje."
                + (job.error.isEmpty() ? "" : "\n" + job.error));
            progress.setMax(1000); progress.setProgress(job.total <= 0 ? 0 : (int)Math.min(1000, 1000L * job.done / job.total));
        } catch (Exception e) { status.setText("Napake pri branju napredka: " + TranslationService.friendly(e)); }
    }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private TextView text(LinearLayout parent, String value, int size, boolean bold) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(Color.rgb(26,45,37));
        view.setPadding(0, dp(6), 0, dp(6)); if (bold) view.setTypeface(null, Typeface.BOLD); parent.addView(view); return view;
    }
    private Button button(LinearLayout parent, String title, View.OnClickListener action) {
        Button view = new Button(this); view.setText(title); view.setAllCaps(false); view.setMinHeight(dp(48)); view.setOnClickListener(action);
        parent.addView(view, new LinearLayout.LayoutParams(-1, -2)); return view;
    }
    private void tab(LinearLayout parent, String title, View.OnClickListener action) {
        Button view = new Button(this); view.setText(title); view.setAllCaps(false); view.setOnClickListener(action);
        parent.addView(view, new LinearLayout.LayoutParams(0, -2, 1));
    }
    @Override protected void onSaveInstanceState(Bundle state) { state.putBoolean("speech", speech); super.onSaveInstanceState(state); }
    @Override protected void onResume() { super.onResume(); handler.post(poll); }
    @Override protected void onPause() { handler.removeCallbacks(poll); super.onPause(); }
    @Override protected void onDestroy() { handler.removeCallbacks(poll); io.shutdown(); super.onDestroy(); }
}

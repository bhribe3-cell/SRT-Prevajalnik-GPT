package si.srtprevajalnik.gpt;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.*;
import android.provider.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private static final int PICK = 100, FOLDER = 101, ZIP = 102;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private JobStore store;
    private EditText apiKey;
    private Spinner model, language;
    private Button select, start, stop, export, zip;
    private LinearLayout fileList;
    private TextView summary, notice, costSummary;
    private EditText eurRate;
    private static final java.util.concurrent.atomic.AtomicBoolean FILE_IO = new java.util.concurrent.atomic.AtomicBoolean(false);
    private boolean keySaved;
    private String signature = "";
    private final Runnable poll = new Runnable() { public void run() { refresh(); handler.postDelayed(this, 700); } };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); store = new JobStore(this);
        getWindow().setStatusBarColor(Color.rgb(244,247,245));
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(Color.rgb(244,247,245));
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(20), dp(12), dp(20), dp(28));
        scroll.addView(root); setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); return insets;
        });
        text(root, "VEČ JEZIKOV  →  SL", 14, true).setTextColor(Color.rgb(22,107,85));
        text(root, "SRT Prevajalnik GPT", 28, true);
        text(root, "Podnapisi iz 10 jezikov v slovenščino. Do 25 datotek naenkrat.", 16, false);

        LinearLayout settings = card(root);
        text(settings, "1. Poveži OpenAI", 20, true);
        text(settings, "Uporabi svoj OpenAI API-ključ. API se obračuna posebej; naročnina ChatGPT ga ne vključuje. Besedilo podnapisov se pošlje neposredno OpenAI.", 14, false);
        apiKey = new EditText(this); apiKey.setHint("OpenAI API-ključ"); apiKey.setSingleLine(true);
        apiKey.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        apiKey.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        apiKey.setSaveEnabled(false); settings.addView(apiKey);
        try { keySaved = !KeyVault.load(this).isEmpty(); } catch (Exception e) { KeyVault.clear(this); }
        if (keySaved) apiKey.setHint("Ključ je varno shranjen na telefonu");
        text(settings, "Model", 14, true);
        model = new Spinner(this);
        model.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"gpt-4.1-mini", "gpt-4.1"}));
        model.setSelection("gpt-4.1".equals(getSharedPreferences("settings", 0).getString("model", "")) ? 1 : 0); settings.addView(model);
        text(settings, "Izvorni jezik podnapisov", 14, true);
        language = new Spinner(this);
        language.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, SubtitleLanguages.LABELS));
        language.setSelection(SubtitleLanguages.indexOf(getSharedPreferences("settings", 0).getString("queue_language", "auto")));
        settings.addView(language);
        text(settings, "Izberi jezik ali uporabi samodejno zaznavanje. Ciljni jezik je vedno slovenščina. Izbira se zaklene po začetku prevajanja, da se ne pomešajo prevodi.", 12, false);
        Button saveKey = button(settings, "Shrani ključ", v -> saveKey());
        Button forget = button(settings, "Pozabi ključ", v -> {
            if (TranslationService.RUNNING.get()) { message("Najprej ustavi prevajanje."); return; }
            KeyVault.clear(this); keySaved = false; apiKey.setText(""); apiKey.setHint("OpenAI API-ključ"); message("API-ključ je odstranjen s telefona.");
        });
        text(settings, "Ključ je šifriran s sistemom Android Keystore. Ni v APK-ju in ni vključen v varnostno kopiranje.", 12, false);

        LinearLayout work = card(root);
        text(work, "2. Izberi in prevedi", 20, true);
        select = button(work, "Izberi SRT datoteke (do 25)", v -> chooseFiles());
        text(work, "V izbirniku pridrži prvo datoteko in označi še druge. Nov izbor zamenja prejšnjo čakalno vrsto.", 13, false);
        start = button(work, "Prevedi v slovenščino", v -> beginTranslation());
        stop = button(work, "Ustavi prevajanje", v -> {
            startService(new Intent(this, TranslationService.class).setAction("cancel")); message("Ustavljam prevajanje …");
        });
        summary = text(work, "Izbranih ni še nobenih datotek.", 15, true);
        text(work, "Potrjeni paketi se shranjujejo sproti. Po prekinitvi tapni »Nadaljuj / ponovi«; lahko se ponovi zadnja nedokončana zahteva API-ja.", 12, false);
        fileList = new LinearLayout(this); fileList.setOrientation(LinearLayout.VERTICAL); work.addView(fileList);

        LinearLayout costs = card(root);
        text(costs, "3. Poraba OpenAI API", 20, true);
        costSummary = text(costs, "Podatkov o porabi še ni.", 16, true);
        text(costs, "Prikazane so lokalne OCENE za zahteve te aplikacije, tudi za uspešne API-odgovore, ki jih je treba ponoviti. To ni dejanski račun OpenAI. Zneski ne vključujejo davkov, drugih aplikacij ali zahtev brez podatkov o porabi.", 12, false);
        text(costs, "Približen tečaj: 1 USD = koliko EUR?", 14, true);
        eurRate = new EditText(this);
        eurRate.setSingleLine(true); eurRate.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        eurRate.setText(getSharedPreferences("settings", 0).getString("eur_per_usd", "0.90"));
        costs.addView(eurRate);
        button(costs, "Shrani tečaj EUR", v -> saveExchangeRate());
        text(costs, "Tečaj je ročno nastavljiv in se ne posodablja samodejno. Preveri dejanski obračun na platform.openai.com/usage.", 12, false);

        LinearLayout results = card(root);
        text(results, "4. Shrani podnapise", 20, true);
        export = button(results, "Shrani slovenske SRT v mapo", v -> launch(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION), FOLDER));
        zip = button(results, "Shrani vse prevedene kot ZIP", v -> launch(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/zip").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, "Slovenski-podnapisi.zip"), ZIP));
        text(results, "Shranjene datoteke imajo končnico .sl.srt in kodiranje UTF-8. Izvoz vključuje samo v celoti preverjene prevode.", 13, false);
        notice = text(root, "Pripravljeno.", 14, false); notice.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        refresh();
    }
    private boolean saveKey() {
        if (TranslationService.RUNNING.get()) { message("Najprej ustavi prevajanje."); return false; }
        String value = apiKey.getText().toString().trim();
        if (value.isEmpty() && keySaved) return true;
        if (!value.startsWith("sk-") || value.length() < 20 || value.matches(".*\\s.*")) { message("Vnesi veljaven OpenAI API-ključ (začne se s sk-)."); return false; }
        try {
            KeyVault.save(this, value); keySaved = true; apiKey.setText(""); apiKey.setHint("Ključ je varno shranjen na telefonu");
            message("Ključ je shranjen. OpenAI ga preveri ob prvi zahtevi."); return true;
        } catch (Exception e) { message("Ključa ni mogoče varno shraniti. Vnesi ga znova."); return false; }
    }
    private void beginTranslation() {
        if (FILE_IO.get() || TranslationService.RUNNING.get()) return;
        if (!saveKey()) return;
        try {
            List<JobStore.Job> jobs = store.load(); SubtitleFiles.validateCount(jobs.size());
            boolean hasProgress = jobs.stream().anyMatch(j -> j.done > 0 || "Končano".equals(j.status));
            String selectedLanguage = SubtitleLanguages.CODES[language.getSelectedItemPosition()];
            android.content.SharedPreferences.Editor prefs = getSharedPreferences("settings", 0).edit()
                .putString("model", model.getSelectedItem().toString());
            if (!hasProgress) prefs.putString("queue_language", selectedLanguage);
            prefs.apply();
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 200);
            startForegroundService(new Intent(this, TranslationService.class)); message("Prevajanje se začenja. Telefon naj ostane povezan z internetom.");
        } catch (Exception e) { message(TranslationService.friendly(e)); }
    }
    private void saveExchangeRate() {
        try {
            String rate = eurRate.getText().toString().trim().replace(',', '.');
            double parsed = Double.parseDouble(rate);
            if (!Double.isFinite(parsed) || parsed <= 0 || parsed > 5) throw new NumberFormatException();
            getSharedPreferences("settings", 0).edit().putString("eur_per_usd", rate).apply();
            message("Tečaj za okvirni preračun v EUR je shranjen.");
            refresh();
        } catch (NumberFormatException e) { eurRate.setError("Vnesi pozitivno število, na primer 0,90."); }
    }
    private double eurPerUsd() {
        try { return Double.parseDouble(getSharedPreferences("settings", 0).getString("eur_per_usd", "0.90")); }
        catch (NumberFormatException e) { return 0.90; }
    }
    private String estimatedEur(double usd) {
        return String.format(Locale.forLanguageTag("sl-SI"), "%.5f €", usd * eurPerUsd());
    }
    private void chooseFiles() {
        if (FILE_IO.get() || TranslationService.RUNNING.get()) return;
        try {
            if (!store.load().isEmpty()) {
                new AlertDialog.Builder(this).setTitle("Nov izbor datotek?")
                    .setMessage("Trenutna čakalna vrsta in lokalni prevodi bodo zamenjani. Že izvožene datoteke ostanejo shranjene.")
                    .setNegativeButton("Prekliči", null).setPositiveButton("Izberi nove", (d, w) -> openPicker()).show();
            } else openPicker();
        } catch (Exception e) { message(TranslationService.friendly(e)); }
    }
    private void openPicker() {
        // Some Android providers label SRT as application/octet-stream or text/plain.
        launch(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE)
            .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), PICK);
    }
    private void launch(Intent intent, int code) {
        try { startActivityForResult(intent, code); }
        catch (ActivityNotFoundException e) { message("Na telefonu ni izbirnika dokumentov."); }
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        if (requestCode == PICK) {
            LinkedHashSet<Uri> uris = new LinkedHashSet<>();
            if (data.getClipData() != null) for (int i = 0; i < data.getClipData().getItemCount(); i++) uris.add(data.getClipData().getItemAt(i).getUri());
            else if (data.getData() != null) uris.add(data.getData());
            try { SubtitleFiles.validateCount(uris.size()); } catch (Exception e) { message(e.getMessage()); return; }
            importFiles(new ArrayList<>(uris));
        } else if (data.getData() != null && (requestCode == ZIP || requestCode == FOLDER)) exportFiles(data.getData(), requestCode == ZIP);
    }
    private String displayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) return SubtitleFiles.safeName(cursor.getString(0));
        }
        return "podnapisi.srt";
    }
    private void importFiles(List<Uri> uris) {
        if (TranslationService.RUNNING.get() || !FILE_IO.compareAndSet(false, true)) return; refresh(); message("Preverjam datoteke …");
        io.execute(() -> {
            List<JobStore.Job> imported = new ArrayList<>();
            try {
                for (Uri uri : uris) {
                    String name = displayName(uri);
                    if (!name.toLowerCase(Locale.ROOT).endsWith(".srt")) throw new IOException("Izbrana datoteka ni SRT: " + name);
                    String source;
                    try (InputStream input = getContentResolver().openInputStream(uri)) {
                        if (input == null) throw new IOException("Datoteke ni mogoče odpreti: " + name);
                        source = SrtDocument.decode(JobStore.readSource(input));
                    }
                    SrtDocument document;
                    try { document = SrtDocument.parse(source); }
                    catch (Exception e) { throw new IOException(name + ": " + e.getMessage()); }
                    JobStore.Job job = new JobStore.Job(UUID.randomUUID().toString(), name); job.total = document.lines.size(); imported.add(job);
                    JobStore.write(store.source(job), source);
                }
                store.save(imported); store.clearOldFiles(imported);
                showMessage("Izbranih datotek: " + imported.size() + ". Pripravljene so za prevajanje.");
            } catch (Exception e) {
                for (JobStore.Job job : imported) store.source(job).delete();
                showMessage(TranslationService.friendly(e) + " Prejšnji izbor je ohranjen.");
            } finally { FILE_IO.set(false); handler.post(() -> { signature = ""; refresh(); }); }
        });
    }
    private void exportFiles(Uri destination, boolean asZip) {
        if (TranslationService.RUNNING.get() || !FILE_IO.compareAndSet(false, true)) return; refresh(); message("Shranjujem prevode …");
        io.execute(() -> {
            List<Uri> created = new ArrayList<>();
            try {
                List<JobStore.Job> completed = new ArrayList<>();
                for (JobStore.Job job : store.load()) if ("Končano".equals(job.status) && store.output(job).exists()) completed.add(job);
                if (completed.isEmpty()) throw new IOException("Za shranjevanje še ni dokončanih prevodov.");
                List<String> names = new ArrayList<>(); List<File> files = new ArrayList<>();
                for (JobStore.Job job : completed) { names.add(job.outputName()); files.add(store.output(job)); }
                names = SubtitleFiles.uniqueNames(names);
                if (asZip) {
                    created.add(destination);
                    OutputStream out = getContentResolver().openOutputStream(destination, "wt");
                    if (out == null) throw new IOException("Datoteke ZIP ni mogoče zapisati.");
                    SubtitleFiles.zip(out, names, files);
                } else {
                    Uri parent = DocumentsContract.buildDocumentUriUsingTree(destination, DocumentsContract.getTreeDocumentId(destination));
                    for (int i = 0; i < files.size(); i++) {
                        Uri child = DocumentsContract.createDocument(getContentResolver(), parent, "application/x-subrip", names.get(i));
                        if (child == null) throw new IOException("Datoteke ni mogoče ustvariti: " + names.get(i));
                        created.add(child);
                        try (OutputStream out = getContentResolver().openOutputStream(child, "wt"); InputStream input = new FileInputStream(files.get(i))) {
                            if (out == null) throw new IOException("Datoteke ni mogoče zapisati: " + names.get(i));
                            input.transferTo(out);
                        }
                    }
                }
                showMessage("Shranjeno: " + completed.size() + " slovenskih podnapisov" + (asZip ? " v izbrano datoteko ZIP." : " v izbrano mapo."));
            } catch (Exception e) {
                for (Uri uri : created) try { DocumentsContract.deleteDocument(getContentResolver(), uri); } catch (Exception ignored) { }
                showMessage("Shranjevanje ni uspelo: " + TranslationService.friendly(e) + " Lokalni prevodi so ohranjeni.");
            } finally { FILE_IO.set(false); handler.post(this::refresh); }
        });
    }
    private void refresh() {
        if (summary == null) return;
        try {
            List<JobStore.Job> jobs = store.load(); boolean running = TranslationService.RUNNING.get();
            int complete = 0; StringBuilder sig = new StringBuilder();
            for (JobStore.Job job : jobs) { if ("Končano".equals(job.status) && store.output(job).exists()) complete++; sig.append(job.id).append(job.status).append(job.done).append(job.error); }
            select.setEnabled(!FILE_IO.get() && !running); start.setEnabled(!FILE_IO.get() && !running && !jobs.isEmpty() && complete < jobs.size());
            stop.setVisibility(running ? View.VISIBLE : View.GONE);
            export.setEnabled(!FILE_IO.get() && !running && complete > 0); zip.setEnabled(!FILE_IO.get() && !running && complete > 0);
            model.setEnabled(!FILE_IO.get() && !running); apiKey.setEnabled(!running);
            language.setEnabled(!FILE_IO.get() && !running && jobs.stream().noneMatch(j -> j.done > 0 || "Končano".equals(j.status)));
            eurRate.setEnabled(!FILE_IO.get());
            start.setText(jobs.stream().anyMatch(j -> j.done > 0 || "Napaka".equals(j.status)) ? "Nadaljuj / ponovi" : "Prevedi v slovenščino");
            summary.setText(jobs.isEmpty() ? "Izbranih ni še nobenih datotek." : "Datoteke: " + jobs.size() + "/25 · Dokončano: " + complete + "/" + jobs.size() + (running ? " · Prevajam …" : ""));
            sig.append(running);
            UsageLedger.Stats usage;
            try { usage = new UsageLedger(this).stats(); }
            catch (Exception e) { usage = new UsageLedger.Stats(); }
            costSummary.setText("Zadnja zahteva: " + estimatedEur(usage.lastUsd)
                + "\nDanes: " + estimatedEur(usage.todayUsd)
                + "\nTa mesec: " + estimatedEur(usage.monthUsd)
                + "\nSkupaj: " + estimatedEur(usage.totalUsd));
            sig.append(usage.totalUsd);
            if (!signature.equals(sig.toString())) {
                signature = sig.toString(); fileList.removeAllViews();
                for (JobStore.Job job : jobs) {
                    String status = "Prevajam".equals(job.status) && !running ? "Prekinjeno – nadaljuj" : job.status;
                    text(fileList, job.name, 15, true);
                    text(fileList, status + " · " + job.done + "/" + job.total + " vrstic"
                        + " · Ocena: " + estimatedEur(usage.byJob.getOrDefault(job.id, 0.0))
                        + (job.error.isEmpty() ? "" : "\n" + job.error), 13, false);
                    ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
                    progress.setMax(Math.max(1, job.total)); progress.setProgress(job.done); fileList.addView(progress);
                }
            }
        } catch (Exception e) { message("Čakalne vrste ni mogoče prebrati: " + TranslationService.friendly(e)); }
    }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private TextView text(LinearLayout parent, String value, int size, boolean bold) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(Color.rgb(26,45,37));
        view.setPadding(0, dp(6), 0, dp(6)); if (bold) view.setTypeface(null, Typeface.BOLD); parent.addView(view); return view;
    }
    private LinearLayout card(LinearLayout parent) {
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(16), dp(12), dp(16), dp(16));
        android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
        background.setColor(Color.WHITE); background.setCornerRadius(dp(16)); card.setBackground(background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = dp(16); parent.addView(card, params); return card;
    }
    private Button button(LinearLayout parent, String label, View.OnClickListener action) {
        Button button = new Button(this); button.setText(label); button.setAllCaps(false); button.setMinHeight(dp(48)); button.setOnClickListener(action);
        parent.addView(button, new LinearLayout.LayoutParams(-1, -2)); return button;
    }
    private void message(String value) { if (notice != null) notice.setText(value); }
    private void showMessage(String value) { handler.post(() -> message(value)); }
    @Override protected void onResume() { super.onResume(); handler.post(poll); }
    @Override protected void onPause() { handler.removeCallbacks(poll); super.onPause(); }
    @Override protected void onDestroy() { handler.removeCallbacks(poll); io.shutdown(); super.onDestroy(); }
}

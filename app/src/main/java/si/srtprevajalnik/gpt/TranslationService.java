package si.srtprevajalnik.gpt;

import android.app.*;
import android.content.*;
import android.os.*;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class TranslationService extends Service {
    static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private volatile boolean cancelled;
    private Thread worker;
    private volatile OpenAiTranslator translator;
    private static final String CHANNEL = "translation";
    @Override public void onCreate() {
        super.onCreate();
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(CHANNEL, "Prevajanje podnapisov", NotificationManager.IMPORTANCE_LOW));
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "cancel".equals(intent.getAction())) { cancelWork(); return START_NOT_STICKY; }
        startForeground(1, notification("Pripravljam prevod …"));
        if (VideoProcessingService.RUNNING.get()) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(startId); return START_NOT_STICKY; }
        if (!RUNNING.compareAndSet(false, true)) return START_NOT_STICKY;
        cancelled = false; worker = new Thread(this::runTranslation, "srt-translation"); worker.start();
        return START_NOT_STICKY;
    }
    private Notification notification(String message) {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent cancel = PendingIntent.getService(this, 1, new Intent(this, TranslationService.class).setAction("cancel"), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, CHANNEL).setSmallIcon(si.srtprevajalnik.gpt.R.drawable.ic_app)
            .setContentTitle("SRT Prevajalnik GPT").setContentText(message).setContentIntent(open).setOngoing(true)
            .addAction(new Notification.Action.Builder(null, "Ustavi", cancel).build()).build();
    }
    private void checkCancel() throws InterruptedException { if (cancelled || Thread.currentThread().isInterrupted()) throw new InterruptedException(); }
    private void cancelWork() {
        cancelled = true; if (worker != null) worker.interrupt();
        OpenAiTranslator current = translator;
        if (current != null) new Thread(current::cancelActive, "srt-cancel").start();
    }
    private void runTranslation() {
        JobStore store = new JobStore(this); List<JobStore.Job> jobs = new ArrayList<>();
        PowerManager.WakeLock wakeLock = getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "srt:translation");
        try {
            wakeLock.acquire(6 * 60 * 60 * 1000L);
            jobs = store.load(); String key = KeyVault.load(this);
            if (key.isEmpty()) throw new OpenAiTranslator.FatalException("Najprej vnesi in shrani OpenAI API-ključ.");
            String model = getSharedPreferences("settings", 0).getString("model", "gpt-4.1-mini");
            for (JobStore.Job job : jobs) {
                checkCancel();
                if ("Končano".equals(job.status) && store.output(job).exists()) continue;
                try {
                    translator = new OpenAiTranslator(key, model, TargetLanguage.fromCode(job.targetLanguage), null);
                    job.status = "Prevajam"; job.error = ""; store.save(jobs);
                    SrtDocument document = SrtDocument.parse(JobStore.read(new FileInputStream(store.source(job))));
                    Map<String, String> translations = new LinkedHashMap<>();
                    if (store.cache(job).exists()) {
                        JSONObject cached = new JSONObject(JobStore.read(new android.util.AtomicFile(store.cache(job)).openRead()));
                        for (SrtDocument.Line line : document.lines) if (cached.has(line.id())) {
                            String text = cached.getString(line.id()); line.restore(text); translations.put(line.id(), text);
                        }
                    }
                    job.total = document.lines.size(); job.done = translations.size(); store.save(jobs);
                    final List<JobStore.Job> currentJobs = jobs;
                    String rendered = TranslationEngine.translate(document, translations, translator, this::checkCancel, progress -> {
                        JobStore.write(store.cache(job), new JSONObject(progress).toString());
                        job.done = progress.size(); store.save(currentJobs);
                        getSystemService(NotificationManager.class).notify(1, notification(job.name + " · " + job.done + "/" + job.total + " vrstic"));
                    });
                    job.done = document.lines.size();
                    JobStore.write(store.output(job), rendered); job.status = "Končano"; store.save(jobs);
                } catch (InterruptedException e) { throw e; }
                catch (OpenAiTranslator.FatalException e) { job.status = "Napaka"; job.error = e.getMessage(); store.save(jobs); break; }
                catch (Exception e) { checkCancel(); job.status = "Napaka"; job.error = friendly(e); store.save(jobs); }
            }
        } catch (InterruptedException e) {
            for (JobStore.Job job : jobs) if ("Prevajam".equals(job.status)) job.status = "Ustavljeno";
            try { store.save(jobs); } catch (Exception ignored) { }
        } catch (Exception e) {
            for (JobStore.Job job : jobs) if (!"Končano".equals(job.status)) { job.status = "Napaka"; job.error = friendly(e); }
            try { store.save(jobs); } catch (Exception ignored) { }
        } finally {
            translator = null;
            if (wakeLock.isHeld()) wakeLock.release();
            RUNNING.set(false); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf();
        }
    }
    static String friendly(Exception e) {
        if (e instanceof java.net.UnknownHostException) return "Ni internetne povezave. Preveri omrežje in poskusi znova.";
        if (e instanceof java.net.SocketTimeoutException) return "Povezava je potekla. Poskusi znova; potrjeni paketi so shranjeni.";
        return e.getMessage() == null ? "Obdelava ni uspela. Poskusi znova." : e.getMessage();
    }
    @Override public void onTimeout(int startId, int fgsType) { cancelWork(); stopSelf(); }
    @Override public void onDestroy() { cancelWork(); super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}

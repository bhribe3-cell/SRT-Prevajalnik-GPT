package si.srtprevajalnik.gpt;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ExecutorService;

/** Keeps the current movie job separate from the SRT queue and checkpoints each paid unit. */
public final class VideoProcessingService extends Service {
    static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    static volatile Boolean ACTIVE_SPEECH;
    private volatile boolean cancelled;
    private volatile VideoFrameRecognizer vision;
    private volatile OpenAiSpeechTranscriber speechApi;
    private volatile TimedSpeechTranscriber timedSpeechApi;
    private volatile OpenAiTranslator translator;
    private Thread worker;
    private static final String CHANNEL = "video_translation";

    @Override public void onCreate() {
        super.onCreate();
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(CHANNEL, "Obdelava filma", NotificationManager.IMPORTANCE_LOW));
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "cancel".equals(intent.getAction())) { cancelWork(); return START_NOT_STICKY; }
        boolean speech = intent != null && intent.getBooleanExtra("speech", false);
        if (RUNNING.get()) return START_NOT_STICKY;
        startForeground(2, notification("Pripravljam film …", speech));
        if (TranslationService.RUNNING.get() || !RUNNING.compareAndSet(false, true)) {
            stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(startId); return START_NOT_STICKY;
        }
        ACTIVE_SPEECH = speech; cancelled = false; worker = new Thread(() -> process(speech), "movie-subtitles"); worker.start();
        return START_NOT_STICKY;
    }
    private Notification notification(String message, boolean speech) {
        Intent activity = new Intent(this, VideoActivity.class).putExtra("speech", speech);
        PendingIntent open = PendingIntent.getActivity(this, speech ? 42 : 41, activity, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent cancel = PendingIntent.getService(this, 4, new Intent(this, VideoProcessingService.class).setAction("cancel"), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_app).setContentTitle("SRT Prevajalnik GPT")
            .setContentText(message).setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(null, "Ustavi", cancel).build()).build();
    }
    private void check() throws InterruptedException { if (cancelled || Thread.currentThread().isInterrupted()) throw new InterruptedException(); }
    private void cancelWork() {
        cancelled = true; if (worker != null) worker.interrupt();
        VideoFrameRecognizer v = vision; OpenAiSpeechTranscriber a = speechApi; TimedSpeechTranscriber timed = timedSpeechApi; OpenAiTranslator t = translator;
        new Thread(() -> { if (v != null) v.cancel(); if (a != null) a.cancel(); if (timed != null) timed.cancel(); if (t != null) t.cancelActive(); }, "movie-cancel").start();
    }
    private void process(boolean isSpeech) {
        VideoJobStore store = new VideoJobStore(this, isSpeech); VideoJobStore.Job job = null;
        PowerManager.WakeLock wake = getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "srt:movie");
        try {
            wake.acquire(6 * 60 * 60 * 1000L);
            job = store.load(); if (job == null) throw new IllegalArgumentException("Najprej izberi film.");
            String key = KeyVault.load(this); if (key.isEmpty()) throw new IllegalArgumentException("Najprej shrani OpenAI API-ključ v zavihku SRT.");
            String model = getSharedPreferences("settings", 0).getString("model", "gpt-4.1-mini");
            Uri uri = Uri.parse(job.uri); final VideoJobStore.Job current = job;
            if (job.fingerprint.isEmpty() || !job.fingerprint.equals(VideoFingerprint.read(this, uri)))
                throw new IllegalArgumentException("Film se je spremenil ali ga ni mogoče preveriti. Ponovno ga izberi.");
            List<VideoTimeline.Interval> intervals;
            try (VideoFrameReader reader = new VideoFrameReader(this, uri)) {
                if (reader.durationMs != job.durationMs || (job.sizeBytes >= 0 && reader.sizeBytes >= 0 && job.sizeBytes != reader.sizeBytes))
                    throw new IllegalArgumentException("Film se je spremenil. Ponovno ga izberi.");
                job.status = isSpeech ? "Prepisujem govor" : "Prepoznavam podnapise"; job.error = "";
                job.total = isSpeech ? (job.speechVersion == 2 ? TimedSpeechEngine.count(reader.durationMs) : SpeechTimeline.windowCount(reader.durationMs)) : VideoTimeline.sampleCount(reader.durationMs, VideoTimeline.FRAME_INTERVAL_MS);
                store.save(job);
                if (isSpeech) {
                    VideoAudioReader audio = new VideoAudioReader(this, uri);
                    if (job.speechVersion == 2) {
                        timedSpeechApi = new TimedSpeechTranscriber(key);
                        intervals = TimedSpeechEngine.scan(reader.durationMs, store.loadTimedSpeech(), audio::readTimed,
                            part -> timedSpeechApi.transcribe(part, this::check), windows -> {
                                store.saveTimedSpeech(windows); current.done = windows.size(); store.save(current);
                                getSystemService(NotificationManager.class).notify(2, notification("Prepisujem govor · " + current.done + "/" + current.total, true));
                            }, this::check);
                    } else {
                        speechApi = new OpenAiSpeechTranscriber(key);
                        intervals = SpeechTranscriptionEngine.scan(reader.durationMs, store.loadSpeech(), audio::read,
                            part -> speechApi.transcribe(part, this::check), windows -> {
                                store.saveSpeech(windows); current.done = windows.size(); store.save(current);
                                getSystemService(NotificationManager.class).notify(2, notification("Prepisujem govor · " + current.done + "/" + current.total, true));
                            }, this::check);
                    }
                } else {
                    List<VideoTimeline.Sample> checkpoint = store.loadFrames(); vision = new VideoFrameRecognizer(key, model);
                    ExecutorService apiWorker = DeadlineCall.worker("movie-vision-api");
                    try {
                        intervals = VideoOcrEngine.scan(reader.durationMs, checkpoint, time -> {
                            current.status = "Prepoznavam podnapise · berem sličico " + (time / VideoTimeline.FRAME_INTERVAL_MS + 1); store.save(current);
                            return reader.frame(time);
                        }, frames -> {
                            current.status = "Prepoznavam podnapise · čakam na OpenAI (" + frames.size() + " sličic)"; store.save(current);
                            try {
                                return DeadlineCall.run(apiWorker, () -> vision.recognize(frames, this::check), 180_000, this::check,
                                    "OpenAI se ni odzval v treh minutah. Tapni Nadaljuj; potrjene sličice ostanejo shranjene.");
                            } catch (Exception e) {
                                VideoFrameRecognizer request = vision;
                                Thread cancel = new Thread(request::cancel, "vision-timeout-cancel"); cancel.setDaemon(true); cancel.start();
                                throw e;
                            }
                        }, samples -> {
                            store.saveFrames(samples); current.done = samples.size(); store.save(current);
                            getSystemService(NotificationManager.class).notify(2, notification("Prepoznavam sličice · " + current.done + "/" + current.total, false));
                        }, this::check);
                    } finally { apiWorker.shutdownNow(); }
                }
            }
            check();
            SrtDocument document = SrtDocument.parse(VideoTimeline.toSrt(intervals));
            Map<String, String> checkpoint = store.loadTranslations(document);
            job.status = "Prevajam"; job.done = checkpoint.size(); job.total = document.lines.size(); store.save(job);
            translator = new OpenAiTranslator(key, model, TargetLanguage.fromCode(job.target), null);
            String output = TranslationEngine.translate(document, checkpoint, translator, this::check, translated -> {
                store.saveTranslations(translated); current.done = translated.size(); store.save(current);
                getSystemService(NotificationManager.class).notify(2, notification("Prevajam · " + current.done + "/" + current.total, isSpeech));
            });
            check(); JobStore.write(store.outputFile(), output);
            job.status = "Končano"; job.done = job.total; store.save(job);
        } catch (InterruptedException e) {
            if (job != null) { job.status = "Ustavljeno"; try { store.save(job); } catch (Exception ignored) {} }
        } catch (Exception e) {
            if (job != null) {
                job.status = cancelled ? "Ustavljeno" : "Napaka"; job.error = cancelled ? "" : TranslationService.friendly(e);
                try { store.save(job); } catch (Exception ignored) {}
            }
        } finally {
            vision = null; speechApi = null; timedSpeechApi = null; translator = null;
            if (wake.isHeld()) wake.release(); ACTIVE_SPEECH = null; RUNNING.set(false); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf();
        }
    }
    @Override public void onTimeout(int startId, int fgsType) { cancelWork(); stopSelf(); }
    @Override public void onDestroy() { cancelWork(); super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}

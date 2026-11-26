package si.srtprevajalnik.gpt;

import android.content.Context;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.util.*;

/** Separate storage prevents a movie job from replacing or deleting the SRT queue. */
final class VideoJobStore {
    static final class Job {
        String uri, name, target, status = "Čaka", error = "";
        String fingerprint = "";
        long durationMs, sizeBytes, done, total;
        int speechVersion = 1;
        Job(String uri, String name, String target, long durationMs, long sizeBytes) {
            this.uri = uri; this.name = name; this.target = TargetLanguage.fromCode(target).code;
            this.durationMs = durationMs; this.sizeBytes = sizeBytes;
        }
    }
    private final File directory;
    VideoJobStore(Context context) { this(context, false); }
    VideoJobStore(Context context, boolean speech) { directory = new File(context.getFilesDir(), speech ? "video_speech" : "video_ocr"); directory.mkdirs(); }
    File framesFile() { return new File(directory, "frames.json"); }
    File translationsFile() { return new File(directory, "translations.json"); }
    File speechFile() { return new File(directory, "speech.json"); }
    File timedSpeechFile() { return new File(directory, "speech-timed.json"); }
    File outputFile() { return new File(directory, "output.srt"); }
    private File index() { return new File(directory, "job.json"); }
    Job load() throws Exception {
        if (!index().exists()) return null;
        JSONObject json = new JSONObject(JobStore.read(new AtomicFile(index()).openRead()));
        Job job = new Job(json.getString("uri"), json.getString("name"), json.getString("target"), json.getLong("duration"), json.getLong("size"));
        job.status = json.getString("status"); job.error = json.optString("error", "");
        job.fingerprint = json.optString("fingerprint", "");
        job.done = json.optLong("done"); job.total = json.optLong("total"); job.speechVersion = json.optInt("speechVersion", 1);
        if (job.speechVersion != 1 && job.speechVersion != 2) throw new IllegalArgumentException("Nepodprta različica prepisa.");
        return job;
    }
    void save(Job job) throws Exception {
        JobStore.write(index(), new JSONObject().put("uri", job.uri).put("name", job.name).put("target", job.target)
            .put("duration", job.durationMs).put("size", job.sizeBytes).put("fingerprint", job.fingerprint).put("status", job.status)
            .put("error", job.error).put("speechVersion", job.speechVersion).put("done", job.done).put("total", job.total).toString());
    }
    List<VideoTimeline.Sample> loadFrames() throws Exception {
        if (!framesFile().exists()) return new ArrayList<>();
        JSONArray array = new JSONArray(JobStore.read(new AtomicFile(framesFile()).openRead())); List<VideoTimeline.Sample> result = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.getJSONObject(i);
            result.add(new VideoTimeline.Sample(item.getLong("time"), item.getString("text")));
        }
        return result;
    }
    void saveFrames(List<VideoTimeline.Sample> frames) throws Exception {
        JSONArray array = new JSONArray();
        for (VideoTimeline.Sample frame : frames) array.put(new JSONObject().put("time", frame.timeMs()).put("text", frame.text()));
        JobStore.write(framesFile(), array.toString());
    }
    List<SpeechTimeline.Window> loadSpeech() throws Exception {
        if (!speechFile().exists()) return new ArrayList<>();
        JSONArray array = new JSONArray(JobStore.read(new AtomicFile(speechFile()).openRead())); List<SpeechTimeline.Window> result = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.getJSONObject(i);
            result.add(new SpeechTimeline.Window(item.getLong("start"), item.getLong("end"), item.getString("text")));
        }
        return result;
    }
    void saveSpeech(List<SpeechTimeline.Window> windows) throws Exception {
        JSONArray array = new JSONArray();
        for (SpeechTimeline.Window window : windows) array.put(new JSONObject().put("start", window.startMs()).put("end", window.endMs()).put("text", window.transcript()));
        JobStore.write(speechFile(), array.toString());
    }
    List<TimedSpeechEngine.Window> loadTimedSpeech() throws Exception {
        if (!timedSpeechFile().exists()) return new ArrayList<>();
        JSONArray array = new JSONArray(JobStore.read(new AtomicFile(timedSpeechFile()).openRead())); List<TimedSpeechEngine.Window> result = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.getJSONObject(i); JSONArray parts = item.getJSONArray("segments"); List<VideoTimeline.Interval> segments = new ArrayList<>();
            for (int j = 0; j < parts.length(); j++) { JSONObject part = parts.getJSONObject(j); segments.add(new VideoTimeline.Interval(part.getLong("start"), part.getLong("end"), part.getString("text"))); }
            result.add(new TimedSpeechEngine.Window(item.getLong("start"), item.getLong("end"), List.copyOf(segments)));
        }
        return result;
    }
    void saveTimedSpeech(List<TimedSpeechEngine.Window> windows) throws Exception {
        JSONArray array = new JSONArray();
        for (TimedSpeechEngine.Window window : windows) {
            JSONArray segments = new JSONArray();
            for (VideoTimeline.Interval part : window.segments()) segments.put(new JSONObject().put("start", part.startMs()).put("end", part.endMs()).put("text", part.text()));
            array.put(new JSONObject().put("start", window.startMs()).put("end", window.endMs()).put("segments", segments));
        }
        JobStore.write(timedSpeechFile(), array.toString());
    }
    Map<String, String> loadTranslations(SrtDocument document) throws Exception {
        Map<String, String> result = new LinkedHashMap<>();
        if (!translationsFile().exists()) return result;
        JSONObject json = new JSONObject(JobStore.read(new AtomicFile(translationsFile()).openRead()));
        for (SrtDocument.Line line : document.lines) if (json.has(line.id())) {
            String translated = json.getString(line.id()); line.restore(translated); result.put(line.id(), translated);
        }
        return result;
    }
    void saveTranslations(Map<String, String> translations) throws IOException { JobStore.write(translationsFile(), new JSONObject(translations).toString()); }
    void clearResults() {
        // Call only after explicitly starting a new movie job, never after a failure.
        for (File file : List.of(framesFile(), speechFile(), timedSpeechFile(), translationsFile(), outputFile())) {
            new AtomicFile(file).delete();
        }
    }
}

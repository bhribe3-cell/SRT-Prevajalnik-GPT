package si.srtprevajalnik.gpt;

import java.util.*;

/** Checkpoint complete audio windows in order; never silently skip failed audio. */
final class SpeechTranscriptionEngine {
    record Audio(long startMs, long endMs, byte[] wave) {}
    interface Source { Audio read(long startMs, long endMs) throws Exception; }
    interface Transcriber { String transcribe(Audio audio) throws Exception; }
    interface Checkpoint { void save(List<SpeechTimeline.Window> confirmed) throws Exception; }
    interface Cancel { void check() throws InterruptedException; }

    static List<VideoTimeline.Interval> scan(long durationMs, List<SpeechTimeline.Window> checkpoint,
                                              Source source, Transcriber transcriber, Checkpoint save, Cancel cancel) throws Exception {
        long count = SpeechTimeline.windowCount(durationMs);
        if (count > Integer.MAX_VALUE || checkpoint.size() > count) throw new IllegalArgumentException("Neveljaven shranjeni prepis.");
        List<SpeechTimeline.Window> confirmed = new ArrayList<>(checkpoint);
        for (int i = 0; i < confirmed.size(); i++) validate(confirmed.get(i), i, durationMs);
        for (long index = confirmed.size(); index < count; index++) {
            cancel.check(); long begin = SpeechTimeline.start(index, durationMs), end = SpeechTimeline.end(index, durationMs);
            Audio audio = source.read(begin, end);
            if (audio == null || audio.startMs() != begin || audio.endMs() != end || audio.wave() == null
                || audio.wave().length <= 44 || audio.wave().length > VideoTimeline.MAX_AUDIO_UPLOAD_BYTES)
                throw new IllegalArgumentException("Zvočnega odseka ni mogoče pripraviti za prepis.");
            cancel.check(); String text = transcriber.transcribe(audio); cancel.check();
            SpeechTimeline.Window window = new SpeechTimeline.Window(begin, end, text); validate(window, index, durationMs);
            confirmed.add(window); save.save(Collections.unmodifiableList(new ArrayList<>(confirmed)));
        }
        return SpeechTimeline.toIntervals(confirmed, durationMs);
    }
    private static void validate(SpeechTimeline.Window window, long index, long durationMs) {
        if (window == null || window.startMs() != SpeechTimeline.start(index, durationMs)
            || window.endMs() != SpeechTimeline.end(index, durationMs) || window.transcript() == null)
            throw new IllegalArgumentException("Shranjeni zvočni odsek je neveljaven.");
        VideoTimeline.normalizeText(window.transcript());
    }
}

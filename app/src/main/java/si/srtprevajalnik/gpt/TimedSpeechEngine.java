package si.srtprevajalnik.gpt;

import java.util.*;

/** Time-stamped speech windows. Overlap provides context around each ten-second boundary. */
final class TimedSpeechEngine {
    static final long WINDOW_MS = 10_000, CONTEXT_MS = 1_000;
    record Window(long startMs, long endMs, List<VideoTimeline.Interval> segments) {}
    interface Transcriber { List<VideoTimeline.Interval> transcribe(SpeechTranscriptionEngine.Audio audio) throws Exception; }
    interface Checkpoint { void save(List<Window> windows) throws Exception; }
    static long count(long duration) { if (duration <= 0) throw new IllegalArgumentException("Neveljavno trajanje zvoka."); return 1 + (duration - 1) / WINDOW_MS; }
    static long start(long index, long duration) { if (index < 0 || index >= count(duration)) throw new IllegalArgumentException("Neveljaven položaj zvoka."); return Math.multiplyExact(index, WINDOW_MS); }
    static long end(long index, long duration) { return Math.min(start(index, duration) + WINDOW_MS, duration); }

    static List<VideoTimeline.Interval> scan(long duration, List<Window> checkpoint, SpeechTranscriptionEngine.Source source,
                                              Transcriber transcriber, Checkpoint save, SpeechTranscriptionEngine.Cancel cancel) throws Exception {
        long count = count(duration);
        if (count > Integer.MAX_VALUE || checkpoint.size() > count) throw new IllegalArgumentException("Neveljaven shranjeni prepis.");
        List<Window> done = new ArrayList<>(checkpoint);
        for (int i = 0; i < done.size(); i++) validate(done.get(i), i, duration);
        for (long i = done.size(); i < count; i++) {
            cancel.check(); long begin = start(i, duration), end = end(i, duration);
            long from = Math.max(0, begin - CONTEXT_MS), to = Math.min(duration, end + CONTEXT_MS);
            SpeechTranscriptionEngine.Audio audio = source.read(from, to);
            if (audio == null || audio.startMs() != from || audio.endMs() != to || audio.wave() == null
                || audio.wave().length <= 44 || audio.wave().length > VideoTimeline.MAX_AUDIO_UPLOAD_BYTES)
                throw new IllegalArgumentException("Zvočnega odseka ni mogoče pripraviti za prepis.");
            cancel.check(); List<VideoTimeline.Interval> received = transcriber.transcribe(audio); cancel.check();
            if (received == null) throw new IllegalArgumentException("Manjka prepis zvočnega odseka.");
            List<VideoTimeline.Interval> owned = new ArrayList<>();
            for (VideoTimeline.Interval segment : received) {
                if (segment == null || segment.startMs() < from || segment.endMs() > to || segment.endMs() <= segment.startMs())
                    throw new IllegalArgumentException("Neveljavna časovna oznaka govora.");
                String text = VideoTimeline.normalizeText(segment.text());
                if (text.isEmpty()) throw new IllegalArgumentException("Prazen govorni odsek.");
                long middle = segment.startMs() + (segment.endMs() - segment.startMs()) / 2;
                if (middle >= begin && middle < end) owned.add(new VideoTimeline.Interval(Math.max(begin, segment.startMs()), Math.min(end, segment.endMs()), text));
            }
            owned.sort(Comparator.comparingLong(VideoTimeline.Interval::startMs).thenComparingLong(VideoTimeline.Interval::endMs));
            Window window = new Window(begin, end, List.copyOf(owned)); validate(window, i, duration);
            done.add(window); save.save(Collections.unmodifiableList(new ArrayList<>(done)));
        }
        List<VideoTimeline.Interval> result = new ArrayList<>();
        for (Window window : done) for (VideoTimeline.Interval part : window.segments()) {
            if (!result.isEmpty() && part.startMs() < result.get(result.size() - 1).endMs()) {
                VideoTimeline.Interval previous = result.remove(result.size() - 1);
                result.add(new VideoTimeline.Interval(previous.startMs(), Math.max(previous.endMs(), part.endMs()), previous.text() + "\n" + part.text()));
            } else result.add(part);
        }
        return List.copyOf(result);
    }
    private static void validate(Window window, long i, long duration) {
        if (window == null || window.startMs() != start(i, duration) || window.endMs() != end(i, duration) || window.segments() == null)
            throw new IllegalArgumentException("Shranjeni zvočni odsek je neveljaven.");
        long previous = -1;
        for (VideoTimeline.Interval part : window.segments()) {
            if (part == null || part.startMs() < window.startMs() || part.endMs() > window.endMs() || part.endMs() <= part.startMs() || part.startMs() < previous || VideoTimeline.normalizeText(part.text()).isEmpty())
                throw new IllegalArgumentException("Shranjeni govor je neveljaven.");
            previous = part.startMs();
        }
    }
}

package si.srtprevajalnik.gpt;

import org.junit.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class SpeechTranscriptionEngineTest {
    @Test public void resumesAfterCheckpointAndUsesOriginalMovieTimes() throws Exception {
        List<SpeechTimeline.Window> saved = new ArrayList<>(); List<Long> read = new ArrayList<>(); AtomicInteger sent = new AtomicInteger();
        SpeechTranscriptionEngine.Source source = (start, end) -> { read.add(start); return new SpeechTranscriptionEngine.Audio(start, end, new byte[48]); };
        SpeechTranscriptionEngine.Transcriber api = audio -> { sent.incrementAndGet(); return audio.startMs() == 4000 ? "" : "Hello!"; };
        assertThrows(InterruptedException.class, () -> SpeechTranscriptionEngine.scan(10_000, saved, source, api, windows -> {
            saved.clear(); saved.addAll(windows); throw new InterruptedException();
        }, () -> {}));
        assertEquals(1, sent.get()); read.clear();
        List<VideoTimeline.Interval> result = SpeechTranscriptionEngine.scan(10_000, saved, source, api, windows -> {
            saved.clear(); saved.addAll(windows);
        }, () -> {});
        assertEquals(List.of(4000L, 8000L), read); assertEquals(3, sent.get());
        assertEquals(List.of(new VideoTimeline.Interval(0, 4000, "Hello!"), new VideoTimeline.Interval(8000, 10000, "Hello!")), result);
        assertTrue(VideoTimeline.toSrt(result).contains("00:00:08,000 --> 00:00:10,000"));
    }
    @Test public void rejectsFailedResponsesAndOversizedChunksWithoutSaving() {
        AtomicInteger saves = new AtomicInteger();
        assertThrows(IllegalArgumentException.class, () -> SpeechTranscriptionEngine.scan(5000, List.of(),
            (start, end) -> new SpeechTranscriptionEngine.Audio(start, end, new byte[48]), audio -> null,
            windows -> saves.incrementAndGet(), () -> {}));
        assertThrows(IllegalArgumentException.class, () -> SpeechTranscriptionEngine.scan(5000, List.of(),
            (start, end) -> new SpeechTranscriptionEngine.Audio(start, end, new byte[24_000_001]), audio -> "Hello",
            windows -> saves.incrementAndGet(), () -> {}));
        assertEquals(0, saves.get());
    }
    @Test public void rejectsGapsAndCorruptedCheckpointsBeforeApi() {
        AtomicInteger calls = new AtomicInteger();
        assertThrows(IllegalArgumentException.class, () -> SpeechTranscriptionEngine.scan(10_000,
            List.of(new SpeechTimeline.Window(4000, 8000, "Hello")), (start, end) -> { calls.incrementAndGet(); return null; }, audio -> "", windows -> {}, () -> {}));
        assertEquals(0, calls.get());
        assertThrows(IllegalArgumentException.class, () -> SpeechTimeline.toIntervals(List.of(new SpeechTimeline.Window(0, 4000, "Hello")), 10_000));
    }
}

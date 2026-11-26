package si.srtprevajalnik.gpt;

import org.junit.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class TimedSpeechEngineTest {
    @Test public void sendsContextAndResumesWithoutRepeatingConfirmedWindow() throws Exception {
        List<TimedSpeechEngine.Window> saved = new ArrayList<>(); List<String> reads = new ArrayList<>(); AtomicInteger calls = new AtomicInteger();
        SpeechTranscriptionEngine.Source source = (start, end) -> { reads.add(start + "-" + end); return new SpeechTranscriptionEngine.Audio(start, end, new byte[48]); };
        TimedSpeechEngine.Transcriber transcribe = audio -> { calls.incrementAndGet(); return audio.startMs() == 0
            ? List.of(new VideoTimeline.Interval(1200, 2300, "Hello"), new VideoTimeline.Interval(9700, 10200, "Across"))
            : List.of(new VideoTimeline.Interval(9700, 10200, "Across"), new VideoTimeline.Interval(12000, 13000, "Bye")); };
        assertThrows(InterruptedException.class, () -> TimedSpeechEngine.scan(16000, saved, source, transcribe, windows -> {
            saved.clear(); saved.addAll(windows); throw new InterruptedException();
        }, () -> {}));
        assertEquals(List.of("0-11000"), reads); reads.clear();
        List<VideoTimeline.Interval> result = TimedSpeechEngine.scan(16000, saved, source, transcribe, windows -> { saved.clear(); saved.addAll(windows); }, () -> {});
        assertEquals(List.of("9000-16000"), reads); assertEquals(2, calls.get());
        assertEquals(List.of(new VideoTimeline.Interval(1200, 2300, "Hello"), new VideoTimeline.Interval(9700, 10000, "Across"), new VideoTimeline.Interval(12000, 13000, "Bye")), result);
    }
    @Test public void preservesOverlappingSpeakersAndRejectsBadCheckpoint() throws Exception {
        List<VideoTimeline.Interval> result = TimedSpeechEngine.scan(2000, List.of(), (a, b) -> new SpeechTranscriptionEngine.Audio(a, b, new byte[48]),
            audio -> List.of(new VideoTimeline.Interval(100, 1000, "First"), new VideoTimeline.Interval(500, 1200, "Second")), windows -> {}, () -> {});
        assertEquals(List.of(new VideoTimeline.Interval(100, 1200, "First\nSecond")), result);
        assertThrows(IllegalArgumentException.class, () -> TimedSpeechEngine.scan(20_000, List.of(new TimedSpeechEngine.Window(10_000, 20_000, List.of())),
            (a,b) -> null, audio -> List.of(), windows -> {}, () -> {}));
    }
    @Test public void invalidOrMissingResultNeverAdvancesCheckpoint() {
        AtomicInteger saves = new AtomicInteger();
        assertThrows(IllegalArgumentException.class, () -> TimedSpeechEngine.scan(2000, List.of(), (a,b) -> new SpeechTranscriptionEngine.Audio(a,b,new byte[48]),
            audio -> List.of(new VideoTimeline.Interval(0, 3000, "bad")), windows -> saves.incrementAndGet(), () -> {}));
        assertEquals(0, saves.get());
    }
}

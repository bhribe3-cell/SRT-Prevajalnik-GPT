package si.srtprevajalnik.gpt;

import org.junit.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class VideoOcrEngineTest {
    @Test public void resumesOnlyAfterSavedBatchesAndPreservesGapsAndFinalCaption() throws Exception {
        List<VideoTimeline.Sample> cache = new ArrayList<>(); List<Long> reads = new ArrayList<>(); AtomicInteger calls = new AtomicInteger();
        VideoOcrEngine.Source source = t -> { reads.add(t); return new VideoFrameRecognizer.Frame(t, "YWJj"); };
        VideoOcrEngine.Recognizer recognizer = frames -> {
            calls.incrementAndGet(); List<VideoTimeline.Sample> out = new ArrayList<>();
            for (VideoFrameRecognizer.Frame frame : frames)
                out.add(new VideoTimeline.Sample(frame.timeMs(), frame.timeMs() >= 4500 ? "Second" : frame.timeMs() >= 500 ? "First" : ""));
            return out;
        };
        assertThrows(InterruptedException.class, () -> VideoOcrEngine.scan(5750, cache, source, recognizer, confirmed -> {
            cache.clear(); cache.addAll(confirmed); throw new InterruptedException();
        }, () -> {}));
        assertEquals(8, cache.size()); assertEquals(1, calls.get()); reads.clear();
        List<VideoTimeline.Interval> result = VideoOcrEngine.scan(5750, cache, source, recognizer, confirmed -> {
            cache.clear(); cache.addAll(confirmed);
        }, () -> {});
        assertEquals(List.of(4000L, 4500L, 5000L, 5500L), reads);
        assertEquals(List.of(new VideoTimeline.Interval(500, 4500, "First"), new VideoTimeline.Interval(4500, 5750, "Second")), result);
        assertEquals(12, cache.size()); assertEquals(2, calls.get());
    }
    @Test public void incompleteBatchNeverAdvancesCheckpoint() {
        AtomicInteger writes = new AtomicInteger();
        assertThrows(IllegalArgumentException.class, () -> VideoOcrEngine.scan(1000, List.of(), t -> new VideoFrameRecognizer.Frame(t, "YWJj"),
            frames -> List.of(new VideoTimeline.Sample(0, "Hello")), frames -> writes.incrementAndGet(), () -> {}));
        assertEquals(0, writes.get());
    }
    @Test public void corruptedCheckpointFailsBeforeReadingOrCallingApi() {
        AtomicInteger calls = new AtomicInteger();
        assertThrows(IllegalArgumentException.class, () -> VideoOcrEngine.scan(1000, List.of(new VideoTimeline.Sample(500, "Hello")),
            t -> { calls.incrementAndGet(); return new VideoFrameRecognizer.Frame(t, "YWJj"); }, frames -> List.of(), frames -> {}, () -> {}));
        assertEquals(0, calls.get());
    }
    @Test public void refusesMismatchedOrMissingFrameResults() {
        for (List<VideoTimeline.Sample> bad : List.of(List.of(new VideoTimeline.Sample(500, "A"), new VideoTimeline.Sample(0, "B")),
            Arrays.asList(new VideoTimeline.Sample(0, "A"), null), Arrays.asList(new VideoTimeline.Sample(0, "A"), new VideoTimeline.Sample(500, null)))) {
            assertThrows(IllegalArgumentException.class, () -> VideoOcrEngine.scan(1000, List.of(), t -> new VideoFrameRecognizer.Frame(t, "YWJj"),
                frames -> bad, frames -> {}, () -> {}));
        }
    }
}

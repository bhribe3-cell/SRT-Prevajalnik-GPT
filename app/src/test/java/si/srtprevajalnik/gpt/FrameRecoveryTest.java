package si.srtprevajalnik.gpt;

import org.junit.Test;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class FrameRecoveryTest {
    @Test public void reopensDecoderAndRetriesSameTimeBeforeTryingNearby() throws Exception {
        List<Long> attempts = new ArrayList<>(); AtomicInteger resets = new AtomicInteger();
        String result = FrameRecovery.read(93_500, 100_000, time -> {
            attempts.add(time); if (attempts.size() == 1) throw new FrameRecovery.Unavailable("decoder"); return "exact";
        }, resets::incrementAndGet);
        assertEquals("exact", result); assertEquals(List.of(93_500L, 93_500L), attempts); assertEquals(1, resets.get());
    }
    @Test public void nearbyRecoveryKeepsRequestedSampleIdAndContinuesCheckpointAt184() throws Exception {
        List<VideoTimeline.Sample> checkpoint = new ArrayList<>();
        for (int i = 0; i < 184; i++) checkpoint.add(new VideoTimeline.Sample(i * 500L, ""));
        List<Long> attempts = new ArrayList<>(); AtomicInteger reset = new AtomicInteger();
        VideoOcrEngine.Source source = time -> {
            if (time != 93_500) return new VideoFrameRecognizer.Frame(time, "YWJj");
            String jpeg = FrameRecovery.read(time, 96_000, sample -> {
                attempts.add(sample); if (sample == 93_500) throw new FrameRecovery.Unavailable("decoder"); return "YWJj";
            }, reset::incrementAndGet);
            return new VideoFrameRecognizer.Frame(time, jpeg);
        };
        List<VideoTimeline.Interval> intervals = VideoOcrEngine.scan(96_000, checkpoint, source, frames -> {
            List<VideoTimeline.Sample> output = new ArrayList<>();
            for (VideoFrameRecognizer.Frame frame : frames) output.add(new VideoTimeline.Sample(frame.timeMs(), frame.timeMs() == 93_500 ? "Hi" : ""));
            return output;
        }, saved -> { checkpoint.clear(); checkpoint.addAll(saved); }, () -> {});
        assertEquals(List.of(93_500L, 93_500L, 93_400L), attempts); assertEquals(2, reset.get());
        assertEquals(192, checkpoint.size()); assertEquals(new VideoTimeline.Interval(93_500, 94_000, "Hi"), intervals.get(0));
    }
    @Test public void failureNeverSkipsTheSampleOrSendsAnIncompleteBatch() {
        List<VideoTimeline.Sample> checkpoint = new ArrayList<>();
        for (int i = 0; i < 184; i++) checkpoint.add(new VideoTimeline.Sample(i * 500L, ""));
        AtomicInteger requests = new AtomicInteger(), writes = new AtomicInteger();
        IOException error = assertThrows(IOException.class, () -> VideoOcrEngine.scan(96_000, checkpoint, time -> {
            if (time == 93_500) FrameRecovery.read(time, 96_000, t -> { throw new FrameRecovery.Unavailable("failure"); }, () -> {});
            return new VideoFrameRecognizer.Frame(time, "YWJj");
        }, frames -> { requests.incrementAndGet(); return List.of(); }, frames -> writes.incrementAndGet(), () -> {}));
        assertTrue(error.getMessage().contains("93500")); assertEquals(0, requests.get()); assertEquals(0, writes.get()); assertEquals(184, checkpoint.size());
    }
    @Test public void handlesEdgesAndDoesNotRetryUnrelatedIoErrors() throws Exception {
        List<Long> attempted = new ArrayList<>();
        String result = FrameRecovery.read(0, 100, time -> { attempted.add(time); if (attempted.size() == 1) throw new FrameRecovery.Unavailable("failure"); return "ok"; }, () -> {});
        assertEquals("ok", result); assertEquals(List.of(0L, 0L), attempted);
        AtomicInteger reset = new AtomicInteger();
        assertThrows(IOException.class, () -> FrameRecovery.read(0, 100, time -> { throw new IOException("permission denied"); }, reset::incrementAndGet));
        assertEquals(0, reset.get());
    }
}

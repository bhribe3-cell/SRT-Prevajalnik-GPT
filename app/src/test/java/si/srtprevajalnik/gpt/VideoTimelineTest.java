package si.srtprevajalnik.gpt;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class VideoTimelineTest {
    @Test public void rejectsLargeFilesAndSupportsUnknownProviderSizes() {
        VideoTimeline.checkVideoSize(20_000_000_000L); VideoTimeline.checkVideoSize(-1);
        assertThrows(IllegalArgumentException.class, () -> VideoTimeline.checkVideoSize(20_000_000_001L));
        assertThrows(IllegalArgumentException.class, () -> VideoTimeline.checkVideoSize(0));
    }
    @Test public void tracksRepeatedCaptionsGapsAndTheLastPartialFrame() {
        List<VideoTimeline.Sample> frames = List.of(
            new VideoTimeline.Sample(0, ""), new VideoTimeline.Sample(500, "Hello"),
            new VideoTimeline.Sample(1000, "Hello"), new VideoTimeline.Sample(1500, ""),
            new VideoTimeline.Sample(2000, "Hello"), new VideoTimeline.Sample(2500, "Bye"));
        List<VideoTimeline.Interval> cues = VideoTimeline.fromFrames(frames, 2750, 500);
        assertEquals(List.of(new VideoTimeline.Interval(500, 1500, "Hello"),
            new VideoTimeline.Interval(2000, 2500, "Hello"), new VideoTimeline.Interval(2500, 2750, "Bye")), cues);
        assertEquals("1\n00:00:00,500 --> 00:00:01,500\nHello\n\n2\n00:00:02,000 --> 00:00:02,500\nHello\n\n3\n00:00:02,500 --> 00:00:02,750\nBye\n\n", VideoTimeline.toSrt(cues));
    }
    @Test public void rejectsMissingAndOutOfOrderFramesInsteadOfDroppingSubtitles() {
        assertThrows(IllegalArgumentException.class, () -> VideoTimeline.fromFrames(List.of(new VideoTimeline.Sample(0, "hello")), 1000, 500));
        assertThrows(IllegalArgumentException.class, () -> VideoTimeline.fromFrames(List.of(new VideoTimeline.Sample(0, "hello"), new VideoTimeline.Sample(501, "hello")), 1000, 500));
        assertThrows(IllegalArgumentException.class, () -> VideoTimeline.fromFrames(List.of(new VideoTimeline.Sample(0, "hello"), new VideoTimeline.Sample(500, null)), 1000, 500));
    }
    @Test public void refusesOverlappingOrEmptySrtAndBlankBlocks() {
        assertThrows(IllegalArgumentException.class, () -> VideoTimeline.toSrt(List.of()));
        assertThrows(IllegalArgumentException.class, () -> VideoTimeline.toSrt(List.of(new VideoTimeline.Interval(0, 2000, "one"), new VideoTimeline.Interval(1000, 3000, "two"))));
        assertThrows(IllegalArgumentException.class, () -> VideoTimeline.toSrt(List.of(new VideoTimeline.Interval(0, 500, "one\n\ntwo"))));
    }
    @Test public void longFilmNeedsOnlyCounterArithmeticForSamplePlanning() {
        assertEquals(21_600, VideoTimeline.sampleCount(10_800_000, 500));
        assertEquals(10_799_500, VideoTimeline.sampleTime(21_599, 10_800_000, 500));
        assertThrows(IllegalArgumentException.class, () -> VideoTimeline.sampleTime(21_600, 10_800_000, 500));
        assertEquals("1\n27:00:00,005 --> 27:00:00,010\nHello\n\n", VideoTimeline.toSrt(List.of(new VideoTimeline.Interval(97_200_005, 97_200_010, "Hello"))));
    }
}

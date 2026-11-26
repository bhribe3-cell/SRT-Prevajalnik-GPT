package si.srtprevajalnik.gpt;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class VideoJobStoreTest {
    @Test public void framesJobAndTranslationsPersistAcrossNewStoreInstancesWithoutTouchingSrtQueue() throws Exception {
        android.content.Context context = RuntimeEnvironment.getApplication();
        JobStore srt = new JobStore(context); JobStore.Job existing = new JobStore.Job("existing", "file.srt");
        existing.done = 440; existing.total = 1032; existing.status = "Napaka"; srt.save(List.of(existing));
        VideoJobStore store = new VideoJobStore(context);
        VideoJobStore.Job job = new VideoJobStore.Job("content://example/movie", "movie.mp4", "hr", 1250, 20_000_000_000L);
        job.done = 2; job.total = 3; job.status = "Ustavljeno"; store.save(job);
        List<VideoTimeline.Sample> frames = List.of(new VideoTimeline.Sample(0, ""), new VideoTimeline.Sample(500, "Hello"));
        store.saveFrames(frames); store.saveTranslations(Map.of("c0_l0", "Živjo"));
        VideoJobStore reopened = new VideoJobStore(context); VideoJobStore.Job loaded = reopened.load();
        assertEquals("content://example/movie", loaded.uri); assertEquals("hr", loaded.target); assertEquals(2, loaded.done);
        assertEquals(20_000_000_000L, loaded.sizeBytes); assertEquals(frames, reopened.loadFrames());
        SrtDocument document = SrtDocument.parse("1\n00:00:00,500 --> 00:00:01,250\nHello\n\n");
        assertEquals(Map.of("c0_l0", "Živjo"), reopened.loadTranslations(document));
        assertEquals(440, srt.load().get(0).done); assertEquals("existing", srt.load().get(0).id);
    }
    @Test public void invalidCachedTranslationFailsInsteadOfExportingPartialResults() throws Exception {
        VideoJobStore store = new VideoJobStore(RuntimeEnvironment.getApplication());
        store.saveTranslations(Map.of("c0_l0", ""));
        SrtDocument document = SrtDocument.parse("1\n00:00:00,000 --> 00:00:01,000\nHello\n\n");
        assertThrows(IllegalArgumentException.class, () -> store.loadTranslations(document));
    }
}

package si.srtprevajalnik.gpt;

import org.junit.Test;
import java.io.IOException;
import java.util.concurrent.*;
import static org.junit.Assert.*;

public class DeadlineCallTest {
    @Test public void stuckTaskTimesOutAndDoesNotBlockSubtitlesForever() throws Exception {
        ExecutorService worker = DeadlineCall.worker("test-timeout"); long start = System.nanoTime();
        try {
            IOException error = assertThrows(IOException.class, () -> DeadlineCall.run(worker, () -> { Thread.sleep(10000); return "bad"; }, 30, () -> {}, "poskusi znova"));
            assertEquals("poskusi znova", error.getMessage()); assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 3000);
        } finally { worker.shutdownNow(); }
    }
    @Test public void cancellationStopsWaitingForTask() {
        ExecutorService worker = DeadlineCall.worker("test-cancel");
        try { assertThrows(InterruptedException.class, () -> DeadlineCall.run(worker, () -> { Thread.sleep(10000); return "bad"; }, 5000, () -> { throw new InterruptedException(); }, "timeout")); }
        finally { worker.shutdownNow(); }
    }
}

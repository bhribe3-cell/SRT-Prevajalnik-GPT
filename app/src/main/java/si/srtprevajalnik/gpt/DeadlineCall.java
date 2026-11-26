package si.srtprevajalnik.gpt;

import java.io.IOException;
import java.util.concurrent.*;

/** Bounds blocking provider, codec, and network calls while keeping cancellation responsive. */
final class DeadlineCall {
    interface Cancel { void check() throws InterruptedException; }
    static ExecutorService worker(String name) {
        return Executors.newSingleThreadExecutor(r -> { Thread thread = new Thread(r, name); thread.setDaemon(true); return thread; });
    }
    static <T> T run(ExecutorService executor, Callable<T> call, long timeoutMs, Cancel cancel, String message) throws Exception {
        Future<T> future = executor.submit(call); long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        try {
            while (true) {
                cancel.check(); long remaining = deadline - System.nanoTime();
                if (remaining <= 0) throw new IOException(message);
                try { return future.get(Math.min(TimeUnit.NANOSECONDS.toMillis(remaining) + 1, 1000), TimeUnit.MILLISECONDS); }
                catch (TimeoutException ignored) {}
            }
        } catch (ExecutionException e) {
            Throwable cause = e.getCause(); if (cause instanceof Exception exception) throw exception;
            throw new IOException("Naprava ni mogla dokončati obdelave.", cause);
        } finally { if (!future.isDone()) future.cancel(true); }
    }
}

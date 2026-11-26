package si.srtprevajalnik.gpt;

import java.io.IOException;

/** Retries an unavailable frame with a fresh decoder and a tightly bounded nearby sample. */
final class FrameRecovery {
    static final long MAX_OFFSET_MS = 100;
    static final class Unavailable extends IOException {
        Unavailable(String message, Throwable cause) { super(message, cause); }
        Unavailable(String message) { super(message); }
    }
    interface Source<T> { T read(long timeMs) throws IOException; }
    interface Reset { void reopen() throws IOException; }
    static <T> T read(long requestedMs, long durationMs, Source<T> source, Reset reset) throws IOException {
        if (requestedMs < 0 || requestedMs >= durationMs) throw new IllegalArgumentException("Neveljaven čas sličice.");
        Unavailable last;
        try { return source.read(requestedMs); }
        catch (Unavailable e) { last = e; }
        checkCancelled(); reset.reopen();
        try { return source.read(requestedMs); }
        catch (Unavailable e) { last = e; }
        long[] nearby = { requestedMs - MAX_OFFSET_MS, requestedMs + MAX_OFFSET_MS };
        for (long time : nearby) {
            if (time < 0 || time >= durationMs) continue;
            checkCancelled(); reset.reopen();
            try { return source.read(time); }
            catch (Unavailable e) { last = e; }
        }
        throw new IOException("Sličice pri " + requestedMs + " ms ni mogoče prebrati niti po ponovnih poskusih. Napredek je shranjen; poskusi znova ali izberi drug zapis filma.", last);
    }
    private static void checkCancelled() throws IOException {
        if (Thread.currentThread().isInterrupted()) throw new IOException("Branje sličice je prekinjeno.");
    }
}

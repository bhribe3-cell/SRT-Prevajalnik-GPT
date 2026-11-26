package si.srtprevajalnik.gpt;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Base64;
import java.io.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;

/** Reads bounded frames without copying the source movie. */
final class VideoFrameReader implements AutoCloseable {
    private static final long FRAME_TIMEOUT_MS = 60_000;
    private static final Semaphore FRAME_SLOT = new Semaphore(1);
    private MediaMetadataRetriever retriever = new MediaMetadataRetriever();
    private final Context context;
    private final Uri uri;
    private int framesSinceReset;
    private final ExecutorService frames = DeadlineCall.worker("movie-frame");
    private int width, height;
    private boolean closed;
    final long durationMs, sizeBytes;
    VideoFrameReader(Context context, Uri uri) throws IOException {
        this.context = context.getApplicationContext(); this.uri = uri;
        long size = -1;
        try (Cursor cursor = context.getContentResolver().query(uri, new String[]{OpenableColumns.SIZE}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) size = cursor.getLong(0);
        }
        VideoTimeline.checkVideoSize(size); sizeBytes = size;
        try {
            retriever.setDataSource(context, uri);
            String duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            durationMs = Long.parseLong(duration == null ? "" : duration);
            if (durationMs <= 0) throw new IOException("Ni mogoče določiti trajanja filma.");
            if (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == null)
                throw new IOException("Izbrana datoteka nima video posnetka.");
            try { width = Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
                  height = Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)); }
            catch (Exception ignored) { width = 0; height = 0; }
        } catch (Exception e) {
            frames.shutdown(); retriever.release();
            if (e instanceof IOException io) throw io;
            throw new IOException("Filma ni mogoče odpreti ali njegova oblika ni podprta.", e);
        }
    }
    VideoFrameRecognizer.Frame frame(long timeMs) throws IOException {
        if (closed || timeMs < 0 || timeMs >= durationMs) throw new IOException("Neveljaven čas sličice.");
        try {
            return DeadlineCall.run(frames, () -> readFrame(timeMs), FRAME_TIMEOUT_MS,
                () -> { if (Thread.currentThread().isInterrupted()) throw new InterruptedException(); },
                "Branje sličice pri " + timeMs + " ms je trajalo predolgo. Tapni Nadaljuj; če se ponavlja, poskusi drug zapis filma.");
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("Branje sličice je prekinjeno.", e); }
        catch (IOException e) { throw e; }
        catch (Exception e) { throw new IOException("Sličice pri " + timeMs + " ms ni mogoče prebrati.", e); }
    }
    private VideoFrameRecognizer.Frame readFrame(long timeMs) throws IOException {
        if (!FRAME_SLOT.tryAcquire()) throw new IOException("Prejšnje branje sličice se še ni končalo. Zapri in znova odpri aplikacijo, nato tapni Nadaljuj.");
        try {
            if (framesSinceReset >= 64) reopen();
            String jpeg = FrameRecovery.read(timeMs, durationMs, this::decodeFrame, this::reopen);
            framesSinceReset++;
            return new VideoFrameRecognizer.Frame(timeMs, jpeg);
        } finally { FRAME_SLOT.release(); }
    }
    void reopen() throws IOException {
        MediaMetadataRetriever old = retriever; retriever = null;
        try { if (old != null) old.release(); }
        catch (Exception e) { throw new IOException("Video dekoderja ni mogoče ponastaviti.", e); }
        MediaMetadataRetriever replacement = new MediaMetadataRetriever();
        try { replacement.setDataSource(context, uri); retriever = replacement; framesSinceReset = 0; }
        catch (Exception e) {
            try { replacement.release(); } catch (Exception ignored) {}
            throw new IOException("Filma po ponastavitvi ni mogoče znova odpreti.", e);
        }
    }
    private String decodeFrame(long timeMs) throws IOException {
        long timeUs = Math.multiplyExact(timeMs, 1000); Bitmap bitmap = null;
        int max = Math.max(width, height);
        if (max > 1920 && width > 0 && height > 0) {
            double scale = 1920.0 / max;
            try { bitmap = retriever.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST,
                Math.max(1, (int)Math.round(width * scale)), Math.max(1, (int)Math.round(height * scale))); }
            catch (RuntimeException ignored) { /* Some decoders only support the full-size API. */ }
        }
        if (bitmap == null) try { bitmap = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST); }
        catch (RuntimeException e) { throw new FrameRecovery.Unavailable("Video dekoder ni vrnil sličice pri " + timeMs + " ms.", e); }
        if (bitmap == null) throw new FrameRecovery.Unavailable("Video dekoder ni vrnil sličice pri " + timeMs + " ms.");
        Bitmap scaled = bitmap;
        try {
            int longest = Math.max(bitmap.getWidth(), bitmap.getHeight());
            if (longest > 1920) {
                double scale = 1920.0 / longest;
                scaled = Bitmap.createScaledBitmap(bitmap, Math.max(1, (int)Math.round(bitmap.getWidth() * scale)), Math.max(1, (int)Math.round(bitmap.getHeight() * scale)), true);
            }
            try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                if (!scaled.compress(Bitmap.CompressFormat.JPEG, 88, bytes)) throw new IOException("Sličice ni mogoče pripraviti.");
                if (bytes.size() > 5_000_000) throw new IOException("Sličica je prevelika.");
                return Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP);
            }
        } finally { if (scaled != bitmap) scaled.recycle(); bitmap.recycle(); }
    }
    @Override public void close() {
        if (closed) return; closed = true;
        // Queue release after the native call: never block the service on a stuck decoder.
        frames.execute(() -> { try { if (retriever != null) retriever.release(); } catch (Exception ignored) {} }); frames.shutdown();
    }
}

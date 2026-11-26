package si.srtprevajalnik.gpt;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Base64;
import java.io.*;

/** Reads individual frames from the provider without copying the source movie. */
final class VideoFrameReader implements AutoCloseable {
    private final MediaMetadataRetriever retriever = new MediaMetadataRetriever();
    final long durationMs, sizeBytes;
    VideoFrameReader(Context context, Uri uri) throws IOException {
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
        } catch (Exception e) {
            retriever.release();
            if (e instanceof IOException io) throw io;
            throw new IOException("Filma ni mogoče odpreti ali njegova oblika ni podprta.", e);
        }
    }
    VideoFrameRecognizer.Frame frame(long timeMs) throws IOException {
        if (timeMs < 0 || timeMs >= durationMs) throw new IOException("Neveljaven čas sličice.");
        Bitmap bitmap = retriever.getFrameAtTime(Math.multiplyExact(timeMs, 1000), MediaMetadataRetriever.OPTION_CLOSEST);
        if (bitmap == null) throw new IOException("Sličice pri " + timeMs + " ms ni mogoče prebrati.");
        Bitmap scaled = bitmap;
        try {
            int max = Math.max(bitmap.getWidth(), bitmap.getHeight());
            if (max > 1920) {
                double scale = 1920.0 / max;
                scaled = Bitmap.createScaledBitmap(bitmap, Math.max(1, (int)Math.round(bitmap.getWidth() * scale)),
                    Math.max(1, (int)Math.round(bitmap.getHeight() * scale)), true);
            }
            try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                if (!scaled.compress(Bitmap.CompressFormat.JPEG, 88, bytes)) throw new IOException("Sličice ni mogoče pripraviti.");
                if (bytes.size() > 5_000_000) throw new IOException("Sličica je prevelika.");
                return new VideoFrameRecognizer.Frame(timeMs, Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP));
            }
        } finally {
            if (scaled != bitmap) scaled.recycle(); bitmap.recycle();
        }
    }
    @Override public void close() throws IOException { retriever.release(); }
}

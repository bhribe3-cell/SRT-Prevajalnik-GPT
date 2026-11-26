package si.srtprevajalnik.gpt;

import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.security.*;

/** Detect common file replacements before resuming a saved job without copying the movie. */
final class VideoFingerprint {
    private static final int SAMPLE_BYTES = 65_536;
    static String read(Context context, Uri uri) throws IOException {
        ParcelFileDescriptor descriptor = context.getContentResolver().openFileDescriptor(uri, "r");
        if (descriptor == null) throw new IOException("Filma ni mogoče odpreti.");
        try (ParcelFileDescriptor.AutoCloseInputStream input = new ParcelFileDescriptor.AutoCloseInputStream(descriptor)) {
            return read(input.getChannel());
        }
    }
    static String read(FileChannel channel) throws IOException {
        long size = channel.size();
        if (size <= 0) throw new IOException("Velikosti filma ni mogoče določiti; izberi datoteko iz lokalne shrambe.");
        VideoTimeline.checkVideoSize(size);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(ByteBuffer.allocate(8).putLong(size).array());
            sample(channel, digest, 0, (int)Math.min(size, SAMPLE_BYTES));
            if (size > SAMPLE_BYTES) sample(channel, digest, Math.max(SAMPLE_BYTES, size - SAMPLE_BYTES), (int)Math.min(size - SAMPLE_BYTES, SAMPLE_BYTES));
            StringBuilder out = new StringBuilder(); for (byte b : digest.digest()) out.append(String.format(java.util.Locale.ROOT, "%02x", b & 0xff));
            return out.toString();
        } catch (NoSuchAlgorithmException e) { throw new IOException("Preverjanje filma ni na voljo.", e); }
    }
    private static void sample(FileChannel channel, MessageDigest digest, long offset, int length) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(length); channel.position(offset);
        while (buffer.hasRemaining()) {
            int count = channel.read(buffer); if (count < 0) throw new EOFException("Film se je med branjem spremenil.");
            if (count == 0) throw new IOException("Filma ni mogoče zanesljivo prebrati iz tega vira.");
        }
        digest.update(buffer.array());
    }
}

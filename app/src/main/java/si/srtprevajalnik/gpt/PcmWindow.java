package si.srtprevajalnik.gpt;

import java.io.*;
import java.nio.*;

/** Bounded PCM window with a WAV header; timestamps remain in the movie's time base. */
final class PcmWindow {
    private final long startUs;
    private final int sampleRate, channels, frameBytes, frameCount;
    private final byte[] pcm;
    PcmWindow(long startMs, long endMs, int sampleRate, int channels) {
        if (startMs < 0 || endMs <= startMs || endMs - startMs > SpeechTimeline.WINDOW_MS
            || sampleRate < 8000 || sampleRate > 192000 || channels < 1 || channels > 8)
            throw new IllegalArgumentException("Nepodprta oblika zvoka.");
        this.startUs = Math.multiplyExact(startMs, 1000); this.sampleRate = sampleRate; this.channels = channels;
        this.frameBytes = Math.multiplyExact(channels, 2);
        this.frameCount = Math.toIntExact(Math.floorDiv(Math.multiplyExact(endMs - startMs, sampleRate) + 999, 1000));
        int length = Math.multiplyExact(frameCount, frameBytes);
        if (length + 44L > VideoTimeline.MAX_AUDIO_UPLOAD_BYTES) throw new IllegalArgumentException("Zvočni odsek je prevelik.");
        this.pcm = new byte[length];
    }
    void write(long ptsUs, ByteBuffer source) {
        if (source.remaining() % frameBytes != 0) throw new IllegalArgumentException("Nepopoln vzorec zvoka.");
        int inputFrames = source.remaining() / frameBytes;
        long first = Math.floorDiv(Math.multiplyExact(Math.subtractExact(ptsUs, startUs), sampleRate), 1_000_000L);
        long inputOffset = Math.max(0, -first), outputOffset = Math.max(0, first);
        long frames = Math.min(inputFrames - inputOffset, frameCount - outputOffset);
        if (frames <= 0) return;
        ByteBuffer clipped = source.duplicate();
        clipped.position(Math.addExact(clipped.position(), Math.toIntExact(inputOffset * frameBytes)));
        clipped.get(pcm, Math.toIntExact(outputOffset * frameBytes), Math.toIntExact(frames * frameBytes));
    }
    byte[] wav() {
        ByteBuffer wav = ByteBuffer.allocate(44 + pcm.length).order(ByteOrder.LITTLE_ENDIAN);
        wav.put(new byte[]{'R','I','F','F'}).putInt(36 + pcm.length).put(new byte[]{'W','A','V','E'});
        wav.put(new byte[]{'f','m','t',' '}).putInt(16).putShort((short)1).putShort((short)channels);
        wav.putInt(sampleRate).putInt(sampleRate * frameBytes).putShort((short)frameBytes).putShort((short)16);
        wav.put(new byte[]{'d','a','t','a'}).putInt(pcm.length).put(pcm); return wav.array();
    }
}

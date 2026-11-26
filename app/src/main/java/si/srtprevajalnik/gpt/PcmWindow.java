package si.srtprevajalnik.gpt;

import android.media.AudioFormat;
import java.nio.*;

/** Converts the decoder's actual PCM format to a bounded PCM16 WAV in the movie time base. */
final class PcmWindow {
    private final long startUs;
    private final int sampleRate, channels, inputEncoding, inputSampleBytes, inputFrameBytes, frameCount;
    private final boolean mono;
    private final byte[] pcm;
    PcmWindow(long startMs, long endMs, int sampleRate, int channels) { this(startMs, endMs, sampleRate, channels, false); }
    PcmWindow(long startMs, long endMs, int sampleRate, int channels, boolean mono) { this(startMs, endMs, sampleRate, channels, mono, AudioFormat.ENCODING_PCM_16BIT); }
    PcmWindow(long startMs, long endMs, int sampleRate, int channels, boolean mono, int encoding) {
        if (startMs < 0 || endMs <= startMs || endMs - startMs > (mono ? TimedSpeechEngine.WINDOW_MS + 2 * TimedSpeechEngine.CONTEXT_MS : SpeechTimeline.WINDOW_MS)
            || sampleRate < 8000 || sampleRate > 192000 || channels < 1 || channels > 8)
            throw new IllegalArgumentException("Nepodprta oblika zvoka.");
        this.startUs = Math.multiplyExact(startMs, 1000); this.sampleRate = sampleRate; this.channels = channels; this.mono = mono;
        inputEncoding = encoding; inputSampleBytes = sampleBytes(encoding); inputFrameBytes = Math.multiplyExact(channels, inputSampleBytes);
        frameCount = Math.toIntExact(Math.floorDiv(Math.multiplyExact(endMs - startMs, sampleRate) + 999, 1000));
        int length = Math.multiplyExact(frameCount, (mono ? 1 : channels) * 2);
        if (length + 44L > VideoTimeline.MAX_AUDIO_UPLOAD_BYTES) throw new IllegalArgumentException("Zvočni odsek je prevelik.");
        pcm = new byte[length];
    }
    static int sampleBytes(int encoding) {
        return switch (encoding) {
            case AudioFormat.ENCODING_PCM_8BIT -> 1;
            case AudioFormat.ENCODING_PCM_16BIT -> 2;
            case AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3;
            case AudioFormat.ENCODING_PCM_FLOAT, AudioFormat.ENCODING_PCM_32BIT -> 4;
            default -> throw new IllegalArgumentException("Nepodprta PCM oblika dekodiranega zvoka (" + encoding + ").");
        };
    }
    void write(long ptsUs, ByteBuffer source) {
        if (source.remaining() % inputFrameBytes != 0) throw new IllegalArgumentException("Nepopoln vzorec zvoka.");
        int inputFrames = source.remaining() / inputFrameBytes;
        long first = Math.floorDiv(Math.multiplyExact(Math.subtractExact(ptsUs, startUs), sampleRate), 1_000_000L);
        long inputOffset = Math.max(0, -first), outputOffset = Math.max(0, first);
        long frames = Math.min(inputFrames - inputOffset, frameCount - outputOffset);
        if (frames <= 0) return;
        ByteBuffer clipped = source.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        clipped.position(Math.addExact(clipped.position(), Math.toIntExact(inputOffset * inputFrameBytes)));
        int destination = Math.toIntExact(outputOffset * (mono ? 2 : channels * 2));
        if (!mono && inputEncoding == AudioFormat.ENCODING_PCM_16BIT) {
            clipped.get(pcm, destination, Math.toIntExact(frames * inputFrameBytes)); return;
        }
        for (long i = 0; i < frames; i++) {
            int sum = 0;
            for (int channel = 0; channel < channels; channel++) {
                int sample = readSample(clipped);
                if (mono) sum += sample;
                else { pcm[destination++] = (byte)sample; pcm[destination++] = (byte)(sample >> 8); }
            }
            if (mono) { int sample = sum / channels; pcm[destination++] = (byte)sample; pcm[destination++] = (byte)(sample >> 8); }
        }
    }
    private int readSample(ByteBuffer in) {
        return switch (inputEncoding) {
            case AudioFormat.ENCODING_PCM_8BIT -> ((in.get() & 255) - 128) << 8;
            case AudioFormat.ENCODING_PCM_16BIT -> in.getShort();
            case AudioFormat.ENCODING_PCM_24BIT_PACKED -> { int lo = in.get() & 255, mid = in.get() & 255, hi = in.get(); yield (lo | (mid << 8) | (hi << 16)) >> 8; }
            case AudioFormat.ENCODING_PCM_32BIT -> in.getInt() >> 16;
            case AudioFormat.ENCODING_PCM_FLOAT -> {
                float value = in.getFloat(); if (!Float.isFinite(value)) throw new IllegalArgumentException("Dekodirani zvok vsebuje neveljaven vzorec.");
                yield Math.max(-32768, Math.min(32767, Math.round(value * 32768f)));
            }
            default -> throw new IllegalArgumentException("Nepodprta oblika zvoka.");
        };
    }
    byte[] wav() {
        ByteBuffer wav = ByteBuffer.allocate(44 + pcm.length).order(ByteOrder.LITTLE_ENDIAN);
        wav.put(new byte[]{'R','I','F','F'}).putInt(36 + pcm.length).put(new byte[]{'W','A','V','E'});
        int outputChannels = mono ? 1 : channels, outputFrameBytes = outputChannels * 2;
        wav.put(new byte[]{'f','m','t',' '}).putInt(16).putShort((short)1).putShort((short)outputChannels);
        wav.putInt(sampleRate).putInt(sampleRate * outputFrameBytes).putShort((short)outputFrameBytes).putShort((short)16);
        wav.put(new byte[]{'d','a','t','a'}).putInt(pcm.length).put(pcm); return wav.array();
    }
}

package si.srtprevajalnik.gpt;

import android.media.AudioFormat;
import android.media.MediaFormat;
import java.io.IOException;

/** Output format comes from the decoder, not from the compressed input track. */
record DecodedPcmFormat(int sampleRate, int channels, int encoding) {
    static DecodedPcmFormat from(MediaFormat output) throws IOException {
        try {
            int rate = output.getInteger(MediaFormat.KEY_SAMPLE_RATE), channels = output.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            int encoding = output.containsKey(MediaFormat.KEY_PCM_ENCODING) ? output.getInteger(MediaFormat.KEY_PCM_ENCODING) : AudioFormat.ENCODING_PCM_16BIT;
            if (rate < 8000 || rate > 192000 || channels < 1 || channels > 8) throw new IllegalArgumentException("Nepodprto število kanalov ali frekvenca.");
            PcmWindow.sampleBytes(encoding);
            return new DecodedPcmFormat(rate, channels, encoding);
        } catch (RuntimeException e) { throw new IOException("Nepodprta oblika izhoda zvočnega dekoderja: " + e.getMessage(), e); }
    }
}

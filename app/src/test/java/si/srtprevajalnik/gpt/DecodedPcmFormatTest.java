package si.srtprevajalnik.gpt;

import android.media.AudioFormat;
import android.media.MediaFormat;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.nio.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class DecodedPcmFormatTest {
    @Test public void acceptsDecoderDownmixAndFloatRegardlessOfInputTrack() throws Exception {
        MediaFormat output = MediaFormat.createAudioFormat("audio/raw", 44100, 2);
        output.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_FLOAT);
        DecodedPcmFormat actual = DecodedPcmFormat.from(output);
        PcmWindow window = new PcmWindow(0, 1, actual.sampleRate(), actual.channels(), true, actual.encoding());
        ByteBuffer source = ByteBuffer.allocate(8 * 44).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < 44; i++) source.putFloat(0.75f).putFloat(-0.25f);
        window.write(0, ByteBuffer.wrap(source.array())); ByteBuffer wav = ByteBuffer.wrap(window.wav()).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(44100, wav.getInt(24)); assertEquals(1, wav.getShort(22)); assertEquals(8192, wav.getShort(44));
    }
    @Test public void sixChannelPcmIsMixedToMonoAndClippedAtWindowBoundary() {
        PcmWindow window = new PcmWindow(1000, 1001, 8000, 6, true, AudioFormat.ENCODING_PCM_16BIT);
        ByteBuffer input = ByteBuffer.allocate(8 * 6 * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < 8; i++) for (int ch = 1; ch <= 6; ch++) input.putShort((short)(ch * 1000));
        window.write(1_000_000, ByteBuffer.wrap(input.array())); ByteBuffer wav = ByteBuffer.wrap(window.wav()).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(3500, wav.getShort(44)); assertEquals(16, wav.getInt(40));
    }
    @Test public void integerPcmFormatsAndFloatClippingProduceValidSignedPcm16() {
        int[] encodings = {AudioFormat.ENCODING_PCM_8BIT, AudioFormat.ENCODING_PCM_24BIT_PACKED, AudioFormat.ENCODING_PCM_32BIT, AudioFormat.ENCODING_PCM_FLOAT};
        byte[][] samples = {{(byte)255}, {0, 0, 0x40}, {0, 0, 0, 0x40}, ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(1.5f).array()};
        int[] values = {32512, 16384, 16384, 32767};
        for (int i = 0; i < encodings.length; i++) {
            PcmWindow window = new PcmWindow(0, 1, 8000, 1, true, encodings[i]);
            window.write(0, ByteBuffer.wrap(samples[i]));
            assertEquals(values[i], ByteBuffer.wrap(window.wav()).order(ByteOrder.LITTLE_ENDIAN).getShort(44));
        }
    }
}

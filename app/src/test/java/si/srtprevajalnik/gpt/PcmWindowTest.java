package si.srtprevajalnik.gpt;

import org.junit.Test;
import java.nio.*;
import java.util.*;
import static org.junit.Assert.*;

public class PcmWindowTest {
    @Test public void clipsSamplesAtBothEdgesAndLeavesTimestampGapsSilent() {
        PcmWindow window = new PcmWindow(4000, 4002, 8000, 1);
        byte[] source = new byte[32]; for (int i = 0; i < source.length; i++) source[i] = (byte)(i + 1);
        window.write(3_999_000, ByteBuffer.wrap(source)); // 8 frames before the window, 8 within it.
        byte[] last = new byte[16]; Arrays.fill(last, (byte)99);
        window.write(4_001_500, ByteBuffer.wrap(last)); // last 4 frames fit.
        byte[] wav = window.wav(); ByteBuffer header = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals("RIFF", new String(wav, 0, 4)); assertEquals(8000, header.getInt(24));
        assertEquals(32, header.getInt(40)); assertEquals(76, wav.length);
        assertArrayEquals(Arrays.copyOfRange(source, 16, 32), Arrays.copyOfRange(wav, 44, 60));
        assertArrayEquals(new byte[8], Arrays.copyOfRange(wav, 60, 68));
        for (int i = 68; i < 76; i++) assertEquals(99, wav[i]);
    }
    @Test public void detectsUnsupportedOrTooLongPcmAndPartialSamples() {
        assertThrows(IllegalArgumentException.class, () -> new PcmWindow(0, 4001, 48000, 2));
        assertThrows(IllegalArgumentException.class, () -> new PcmWindow(0, 1000, 48000, 9));
        PcmWindow window = new PcmWindow(0, 1000, 48000, 2);
        assertThrows(IllegalArgumentException.class, () -> window.write(0, ByteBuffer.wrap(new byte[3])));
    }
}

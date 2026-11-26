package si.srtprevajalnik.gpt;

import org.junit.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class OpenAiSpeechTranscriberTest {
    @Test public void streamsWavWithCurrentTranscriptionModelAndRetainsFullSpeech() throws Exception {
        byte[] wav = new PcmWindow(0, 100, 8000, 1).wav();
        OpenAiSpeechTranscriber api = new OpenAiSpeechTranscriber("fake", body -> {
            ByteArrayOutputStream out = new ByteArrayOutputStream(); body.writeTo(out);
            assertEquals(body.length(), out.size());
            String header = new String(body.header(), StandardCharsets.US_ASCII);
            assertTrue(header.contains("gpt-transcribe")); assertTrue(header.contains("filename=\"audio.wav\""));
            assertArrayEquals(wav, body.audio()); assertFalse(header.contains("fake"));
            return "{\"text\":\"Hello there. How are you?\"}";
        });
        assertEquals("Hello there. How are you?", api.transcribe(new SpeechTranscriptionEngine.Audio(0, 100, wav), () -> {}));
    }
    @Test public void missingTextOrMalformedWavDoesNotBecomeSilence() {
        byte[] wav = new PcmWindow(0, 100, 8000, 1).wav(); AtomicInteger calls = new AtomicInteger();
        OpenAiSpeechTranscriber api = new OpenAiSpeechTranscriber("fake", body -> { calls.incrementAndGet(); return "{}"; });
        assertThrows(IOException.class, () -> api.transcribe(new SpeechTranscriptionEngine.Audio(0, 100, wav), () -> {}));
        assertThrows(IOException.class, () -> api.transcribe(new SpeechTranscriptionEngine.Audio(0, 100, new byte[60]), () -> {}));
        assertEquals(1, calls.get());
    }
    @Test public void cancellationPreventsNetworkRequest() {
        AtomicInteger calls = new AtomicInteger();
        OpenAiSpeechTranscriber api = new OpenAiSpeechTranscriber("fake", body -> { calls.incrementAndGet(); return "{\"text\":\"\"}"; });
        byte[] wav = new PcmWindow(0, 100, 8000, 1).wav();
        assertThrows(InterruptedException.class, () -> api.transcribe(new SpeechTranscriptionEngine.Audio(0, 100, wav), () -> { throw new InterruptedException(); }));
        assertEquals(0, calls.get());
    }
}

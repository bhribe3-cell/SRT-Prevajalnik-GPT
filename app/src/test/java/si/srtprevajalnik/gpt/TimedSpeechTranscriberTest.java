package si.srtprevajalnik.gpt;

import org.junit.Test;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;

public class TimedSpeechTranscriberTest {
    @Test public void requestsDiarizedOutputAndConvertsRelativeTimes() throws Exception {
        byte[] wav = new PcmWindow(0, 1000, 8000, 1).wav();
        TimedSpeechTranscriber api = new TimedSpeechTranscriber("secret", body -> {
            String header = new String(body.header(), StandardCharsets.US_ASCII);
            assertTrue(header.contains("gpt-4o-transcribe-diarize")); assertTrue(header.contains("diarized_json")); assertFalse(header.contains("secret"));
            return "{\"text\":\"Hello there\",\"segments\":[{\"start\":0.12,\"end\":0.87,\"text\":\"Hello there\",\"speaker\":\"A\"}]}";
        });
        assertEquals(List.of(new VideoTimeline.Interval(9120, 9870, "Hello there")), api.transcribe(new SpeechTranscriptionEngine.Audio(9000,10000,wav), () -> {}));
    }
    @Test public void rejectsMissingSegmentsAndInvalidTimesButAllowsExplicitSilence() throws Exception {
        byte[] wav = new PcmWindow(0, 1000, 8000, 1).wav(); SpeechTranscriptionEngine.Audio audio = new SpeechTranscriptionEngine.Audio(0,1000,wav);
        assertThrows(IOException.class, () -> new TimedSpeechTranscriber("fake", body -> "{\"text\":\"hello\",\"segments\":[]}").transcribe(audio, () -> {}));
        assertThrows(IOException.class, () -> new TimedSpeechTranscriber("fake", body -> "{\"text\":\"hello world\",\"segments\":[{\"start\":0.1,\"end\":0.5,\"text\":\"hello\"}]}").transcribe(audio, () -> {}));
        assertThrows(IOException.class, () -> new TimedSpeechTranscriber("fake", body -> "{\"text\":\"hello\",\"segments\":[{\"start\":0.1,\"end\":2,\"text\":\"hello\"}]}").transcribe(audio, () -> {}));
        assertEquals(List.of(), new TimedSpeechTranscriber("fake", body -> "{\"text\":\"\",\"segments\":[]}").transcribe(audio, () -> {}));
    }
}

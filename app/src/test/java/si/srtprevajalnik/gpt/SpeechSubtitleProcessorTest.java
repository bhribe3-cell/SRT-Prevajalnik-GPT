package si.srtprevajalnik.gpt;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class SpeechSubtitleProcessorTest {
    @Test public void producesSlovenianSrtWithOriginalWindowOffsets() throws Exception {
        List<SpeechTimeline.Window> speech = new ArrayList<>(); Map<String, String> translations = new LinkedHashMap<>();
        OpenAiTranslator translator = new OpenAiTranslator("fake", "gpt-4.1-mini", body -> {
            assertTrue(body.getJSONArray("messages").getJSONObject(0).getString("content").contains("Slovenian"));
            return "{\"translations\":{\"c0_l0\":\"Živjo!\",\"c1_l0\":\"Dobro jutro.\"}}";
        });
        String output = SpeechSubtitleProcessor.run(10_000, speech, (start, end) -> new SpeechTranscriptionEngine.Audio(start, end, new byte[48]),
            audio -> audio.startMs() == 0 ? "Hello!" : audio.startMs() == 4000 ? "" : "Good morning.",
            saved -> { speech.clear(); speech.addAll(saved); }, translations, translator, translations::putAll, () -> {});
        assertEquals("1\n00:00:00,000 --> 00:00:04,000\nŽivjo!\n\n2\n00:00:08,000 --> 00:00:10,000\nDobro jutro.\n\n", output);
        assertEquals(3, speech.size()); assertEquals(2, translations.size());
    }
}

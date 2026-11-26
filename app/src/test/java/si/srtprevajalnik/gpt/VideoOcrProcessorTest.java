package si.srtprevajalnik.gpt;

import org.json.*;
import org.junit.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class VideoOcrProcessorTest {
    @Test public void scansFramesTranslatesAndProducesTimedSrt() throws Exception {
        List<VideoTimeline.Sample> frames = new ArrayList<>(); Map<String, String> translations = new LinkedHashMap<>();
        OpenAiTranslator translator = new OpenAiTranslator("fake", "gpt-4.1-mini", TargetLanguage.CROATIAN, body -> {
            assertTrue(body.getJSONArray("messages").getJSONObject(0).getString("content").contains("Croatian"));
            return "{\"translations\":{\"c0_l0\":\"Bok!\",\"c1_l0\":\"Dobro jutro.\"}}";
        });
        String result = VideoOcrProcessor.run(2250, frames, t -> new VideoFrameRecognizer.Frame(t, "YWJj"), batch -> {
            List<VideoTimeline.Sample> samples = new ArrayList<>();
            for (VideoFrameRecognizer.Frame frame : batch) samples.add(new VideoTimeline.Sample(frame.timeMs(),
                frame.timeMs() < 500 ? "" : frame.timeMs() < 1500 ? "Hello!" : "Good morning."));
            return samples;
        }, saved -> { frames.clear(); frames.addAll(saved); }, translations, translator, translations::putAll, () -> {});
        assertEquals("1\n00:00:00,500 --> 00:00:01,500\nBok!\n\n2\n00:00:01,500 --> 00:00:02,250\nDobro jutro.\n\n", result);
        assertEquals(5, frames.size()); assertEquals(2, translations.size());
    }
    @Test public void rejectsMissingFramesBeforeAttemptingTranslation() {
        AtomicInteger calls = new AtomicInteger();
        OpenAiTranslator translator = new OpenAiTranslator("fake", "gpt-4.1-mini", body -> { calls.incrementAndGet(); return "{}"; });
        assertThrows(IllegalArgumentException.class, () -> VideoOcrProcessor.run(1000, List.of(),
            t -> new VideoFrameRecognizer.Frame(t, "YWJj"), batch -> List.of(new VideoTimeline.Sample(0, "Hello")),
            frames -> {}, Map.of(), translator, translations -> {}, () -> {}));
        assertEquals(0, calls.get());
    }
    @Test public void allBlankFramesDoNotProduceAnApparentlyCompleteSubtitle() {
        AtomicInteger calls = new AtomicInteger();
        OpenAiTranslator translator = new OpenAiTranslator("fake", "gpt-4.1-mini", body -> { calls.incrementAndGet(); return "{}"; });
        assertThrows(IllegalArgumentException.class, () -> VideoOcrProcessor.run(500, List.of(),
            t -> new VideoFrameRecognizer.Frame(t, "YWJj"), batch -> List.of(new VideoTimeline.Sample(0, "")),
            frames -> {}, Map.of(), translator, translations -> {}, () -> {}));
        assertEquals(0, calls.get());
    }
}

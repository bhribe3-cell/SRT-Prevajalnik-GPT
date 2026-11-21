package si.srtprevajalnik.gpt;

import org.json.*;
import org.junit.Test;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class OpenAiTranslatorTest {
    private static JSONArray inputs(JSONObject request) throws JSONException {
        return new JSONArray(request.getJSONArray("messages").getJSONObject(1).getString("content"));
    }
    private static String reply(JSONArray input, String blank, String omitted) throws JSONException {
        JSONArray output = new JSONArray();
        for (int i = 0; i < input.length(); i++) {
            JSONObject line = input.getJSONObject(i); String id = line.getString("id");
            if (id.equals(omitted)) continue;
            String translated = line.getString("text").replace("Hello!", "Živjo!")
                .replace("How are you?", "Kako si?").replace("Good morning.", "Dobro jutro.");
            output.put(new JSONObject().put("id", id).put("text", id.equals(blank) ? "" : translated));
        }
        return new JSONObject().put("translations", output).toString();
    }
    @Test public void canSelectCroatianOrAutomaticallyDetectInputLanguage() throws Exception {
        SrtDocument document = SrtDocument.parse("1\n00:00:01,000 --> 00:00:03,000\nDobar dan!\n\n");
        for (String source : List.of("hr", "auto", "sr", "bs")) {
            OpenAiTranslator translator = new OpenAiTranslator("fake", "gpt-4.1-mini", source, body -> {
                String system = body.getJSONArray("messages").getJSONObject(0).getString("content");
                assertTrue(system.contains(SubtitleLanguages.nameForPrompt(source)));
                assertTrue(system.contains("natural Slovenian"));
                return new JSONObject().put("translations", new JSONObject().put("c0_l0", "Dober dan!")).toString();
            }, null);
            assertEquals("Dober dan!", translator.translate(document.lines, () -> {}).get("c0_l0"));
        }
        assertEquals(11, SubtitleLanguages.CODES.length);
    }

    @Test public void normalizesModelWrappingWithinOneIdWithoutDroppingWordsOrLines() throws Exception {
        SrtDocument document = SrtDocument.parse("41\n00:01:00,000 --> 00:01:03,000\n- <i>How are you today?</i>\n- Hello!\n\n");
        String reply = new JSONObject().put("translations", new JSONArray()
            .put(new JSONObject().put("id", "c0_l0").put("text", "\r\n⟦T0⟧Kako si\r\ndanes?⟦T1⟧\n"))
            .put(new JSONObject().put("id", "c0_l1").put("text", "Živjo!"))).toString();
        Map<String, String> translated = OpenAiTranslator.parseResponse(reply, document.lines);
        assertEquals("41\n00:01:00,000 --> 00:01:03,000\n- <i>Kako si danes?</i>\n- Živjo!\n\n", document.render(translated));
        assertEquals(2, SrtDocument.parse(document.render(translated)).lines.size());
    }
    @Test public void retriesOnlyBlankLineWithNeighborContextAndKeepsValidTranslations() throws Exception {
        SrtDocument document = SrtDocument.parse(SrtDocumentTest.SOURCE);
        List<List<String>> requests = new ArrayList<>(); List<Integer> confirmed = new ArrayList<>();
        OpenAiTranslator translator = new OpenAiTranslator("fake", "gpt-4.1-mini", body -> {
            JSONArray input = inputs(body); List<String> ids = new ArrayList<>();
            for (int i = 0; i < input.length(); i++) ids.add(input.getJSONObject(i).getString("id")); requests.add(ids);
            if (input.length() == 1) {
                assertEquals("c0_l1", input.getJSONObject(0).getString("id"));
                assertEquals("⟦T0⟧Hello!⟦T1⟧", input.getJSONObject(0).getString("previous_line"));
                assertEquals("⟦T0⟧Good morning.", input.getJSONObject(0).getString("next_line"));
            }
            return reply(input, input.length() > 1 ? "c0_l1" : null, null);
        });
        Map<String, String> result = translator.translate(document.lines, () -> {}, part -> confirmed.add(part.size()));
        assertEquals(3, result.size()); assertEquals("Kako si?", result.get("c0_l1"));
        assertEquals(List.of(List.of("c0_l0", "c0_l1", "c1_l0"), List.of("c0_l1")), requests);
        assertEquals(List.of(2, 1), confirmed);
    }
    @Test public void retriesMissingIdWithoutResendingConfirmedIds() throws Exception {
        SrtDocument document = SrtDocument.parse(SrtDocumentTest.SOURCE); List<Integer> sizes = new ArrayList<>();
        OpenAiTranslator translator = new OpenAiTranslator("fake", "gpt-4.1-mini", body -> {
            JSONArray input = inputs(body); sizes.add(input.length());
            return reply(input, null, input.length() > 1 ? "c0_l1" : null);
        });
        assertEquals(3, translator.translate(document.lines, () -> {}).size()); assertEquals(List.of(3, 1), sizes);
    }
    @Test public void resumedRepairStillGetsSourceContextAcrossPreviouslyConfirmedLines() throws Exception {
        SrtDocument document = SrtDocument.parse(SrtDocumentTest.SOURCE);
        Map<String, String> cache = new LinkedHashMap<>(); cache.put("c0_l0", "⟦T0⟧Živjo!⟦T1⟧"); cache.put("c1_l0", "⟦T0⟧Dobro jutro.");
        AtomicInteger calls = new AtomicInteger();
        OpenAiTranslator translator = new OpenAiTranslator("fake", "gpt-4.1-mini", body -> {
            JSONArray input = inputs(body); assertEquals(1, input.length());
            JSONObject item = input.getJSONObject(0); assertEquals("c0_l1", item.getString("id"));
            if (calls.incrementAndGet() == 1) return reply(input, "c0_l1", null);
            assertEquals("⟦T0⟧Hello!⟦T1⟧", item.getString("previous_line"));
            assertEquals("⟦T0⟧Good morning.", item.getString("next_line"));
            return reply(input, null, null);
        });
        String result = TranslationEngine.translate(document, cache, translator, () -> {}, cache::putAll);
        assertEquals(2, calls.get()); assertEquals(3, cache.size()); assertTrue(result.contains("Kako si?"));
    }
    @Test public void permanentlyMissingLineNeverProducesOutputAndValidProgressSurvivesResume() throws Exception {
        SrtDocument document = SrtDocument.parse(SrtDocumentTest.SOURCE); Map<String, String> cache = new LinkedHashMap<>();
        List<Integer> sizes = new ArrayList<>();
        OpenAiTranslator broken = new OpenAiTranslator("fake", "gpt-4.1-mini", body -> {
            JSONArray input = inputs(body); sizes.add(input.length()); return reply(input, "c0_l1", null);
        });
        IOException error = assertThrows(IOException.class, () -> TranslationEngine.translate(document, cache, broken, () -> {}, cache::putAll));
        assertTrue(error.getMessage().contains("c0_l1")); assertEquals(List.of(3, 1, 1), sizes);
        assertEquals(Set.of("c0_l0", "c1_l0"), cache.keySet());
        OpenAiTranslator repaired = new OpenAiTranslator("fake", "gpt-4.1-mini", body -> {
            JSONArray input = inputs(body); assertEquals(1, input.length()); assertEquals("c0_l1", input.getJSONObject(0).getString("id"));
            return reply(input, null, null);
        });
        String result = TranslationEngine.translate(document, cache, repaired, () -> {}, cache::putAll);
        assertEquals(3, cache.size()); assertTrue(result.contains("Kako si?")); assertEquals(3, SrtDocument.parse(result).lines.size());
    }
    @Test public void cancellationAfterPartialResponseCheckpointsSuccessAndPreventsRepairRequests() throws Exception {
        SrtDocument document = SrtDocument.parse(SrtDocumentTest.SOURCE); AtomicInteger calls = new AtomicInteger(); Map<String, String> cache = new LinkedHashMap<>();
        OpenAiTranslator translator = new OpenAiTranslator("fake", "gpt-4.1-mini", body -> {
            calls.incrementAndGet(); return reply(inputs(body), "c0_l1", null);
        });
        assertThrows(InterruptedException.class, () -> TranslationEngine.translate(document, cache, translator, () -> {}, part -> {
            cache.putAll(part); throw new InterruptedException();
        }));
        assertEquals(2, cache.size()); assertEquals(1, calls.get());
    }
    @Test public void fatalAuthenticationErrorIsNotTreatedAsBadDialogue() {
        AtomicInteger calls = new AtomicInteger();
        OpenAiTranslator translator = new OpenAiTranslator("fake", "gpt-4.1-mini", body -> {
            calls.incrementAndGet(); throw new OpenAiTranslator.FatalException("API-ključ ni veljaven.");
        });
        assertThrows(OpenAiTranslator.FatalException.class, () -> translator.translate(SrtDocument.parse(SrtDocumentTest.SOURCE).lines, () -> {}));
        assertEquals(1, calls.get());
    }
    @Test public void unknownOrDuplicateIdsCannotBeCheckpointedEvenWithOtherValidText() {
        SrtDocument document = SrtDocument.parse("41\n00:01:00,000 --> 00:01:03,000\nHello!\n\n");
        for (boolean unknown : List.of(false, true)) {
            Map<String, String> confirmed = new LinkedHashMap<>();
            OpenAiTranslator translator = new OpenAiTranslator("fake", "gpt-4.1-mini", body -> new JSONObject().put("translations", new JSONArray()
                .put(new JSONObject().put("id", "c0_l0").put("text", "Živjo!"))
                .put(new JSONObject().put("id", unknown ? "other" : "c0_l0").put("text", "Živjo!"))).toString());
            assertThrows(IOException.class, () -> translator.translate(document.lines, () -> {}, confirmed::putAll));
            assertTrue(confirmed.isEmpty());
        }
    }
    @Test public void whitespaceOnlyResponseStillCannotStandInForDialogue() throws Exception {
        SrtDocument document = SrtDocument.parse("41\n00:01:00,000 --> 00:01:03,000\nHello!\n\n");
        String empty = new JSONObject().put("translations", new JSONArray().put(new JSONObject().put("id", "c0_l0").put("text", "\r\n \t\n"))).toString();
        assertThrows(IllegalArgumentException.class, () -> OpenAiTranslator.parseResponse(empty, document.lines));
    }
    @Test public void fileWith2126LinesRecoversAtC40L1AndPreservesCompleteSrt() throws Exception {
        StringBuilder source = new StringBuilder();
        for (int i = 0; i < 2125; i++) {
            source.append(i + 1).append("\n00:00:01,000 --> 00:00:02,000\n").append(i == 10 ? "..." : "Hello!").append('\n');
            if (i == 40) source.append("How are you?\n");
            source.append('\n');
        }
        SrtDocument document = SrtDocument.parse(source.toString()); assertEquals(2126, document.lines.size());
        AtomicInteger repaired = new AtomicInteger(); List<Integer> progress = new ArrayList<>();
        OpenAiTranslator translator = new OpenAiTranslator("fake", "gpt-4.1-mini", body -> {
            JSONArray input = inputs(body);
            if (input.length() == 1 && "c40_l1".equals(input.getJSONObject(0).getString("id"))) repaired.incrementAndGet();
            return reply(input, input.length() > 1 ? "c40_l1" : null, null);
        });
        String output = TranslationEngine.translate(document, Map.of(), translator, () -> {}, part -> progress.add(part.size()));
        assertTrue(progress.contains(41)); assertEquals(1, repaired.get());
        assertEquals(source.toString().replace("Hello!", "Živjo!").replace("How are you?", "Kako si?"), output);
        assertEquals(2126, SrtDocument.parse(output).lines.size());
    }
}

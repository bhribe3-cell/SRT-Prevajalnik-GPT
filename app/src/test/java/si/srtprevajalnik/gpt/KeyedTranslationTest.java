package si.srtprevajalnik.gpt;

import org.json.*;
import org.junit.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class KeyedTranslationTest {
    private static Set<String> keys(JSONObject object) {
        Set<String> keys = new HashSet<>(); Iterator<String> names = object.keys();
        while (names.hasNext()) keys.add(names.next()); return keys;
    }
    private static JSONObject translationSchema(JSONObject request) throws JSONException {
        return request.getJSONObject("response_format").getJSONObject("json_schema").getJSONObject("schema")
            .getJSONObject("properties").getJSONObject("translations");
    }
    private static String keyedReply(JSONObject request) throws JSONException {
        JSONArray input = new JSONArray(request.getJSONArray("messages").getJSONObject(1).getString("content"));
        JSONObject output = new JSONObject();
        for (int i = 0; i < input.length(); i++) {
            JSONObject line = input.getJSONObject(i);
            output.put(line.getString("id"), line.getString("text").replace("Hello!", "Živjo!").replace("How are you?", "Kako si?"));
        }
        assertEquals(keys(translationSchema(request).getJSONObject("properties")), keys(output));
        return new JSONObject().put("translations", output).toString();
    }
    @Test public void replySchemaRequiresExactlyTheRequestedIdsInsteadOfFreeTextIds() throws Exception {
        List<SrtDocument.Line> lines = List.of(
            new SrtDocument.Line("c270_l0", "Hello!", "Hello!", "", List.of()),
            new SrtDocument.Line("c270_l1", "How are you?", "How are you?", "", List.of()));
        OpenAiTranslator translator = new OpenAiTranslator("fake", "gpt-4.1-mini", request -> {
            JSONObject schema = request.getJSONObject("response_format").getJSONObject("json_schema").getJSONObject("schema")
                .getJSONObject("properties").getJSONObject("translations");
            assertEquals("object", schema.getString("type"));
            assertFalse(schema.getBoolean("additionalProperties"));
            Set<String> required = new HashSet<>();
            for (int i = 0; i < schema.getJSONArray("required").length(); i++) required.add(schema.getJSONArray("required").getString(i));
            assertEquals(Set.of("c270_l0", "c270_l1"), required);
            assertEquals(required, keys(schema.getJSONObject("properties")));
            assertFalse(schema.getJSONObject("properties").has("c270_10"));
            return "{\"translations\":{\"c270_l0\":\"Živjo!\",\"c270_l1\":\"Kako si?\"}}";
        });
        Map<String, String> result = translator.translate(lines, () -> {});
        assertEquals(Map.of("c270_l0", "Živjo!", "c270_l1", "Kako si?"), result);
    }
    @Test public void keyedReplyOrderCannotReorderDialogsOrTimestamps() throws Exception {
        SrtDocument document = SrtDocument.parse("271\r\n00:01:00,000 --> 00:01:03,000\r\n- Hello!\r\n- How are you?\r\n\r\n");
        Map<String, String> translated = OpenAiTranslator.parseResponse("{\"translations\":{\"c0_l1\":\"Kako si?\",\"c0_l0\":\"Živjo!\"}}", document.lines);
        assertEquals("271\r\n00:01:00,000 --> 00:01:03,000\r\n- Živjo!\r\n- Kako si?\r\n\r\n", document.render(translated));
    }
    @Test public void misspelledOrDuplicateRawKeysCannotBeSilentlyAccepted() {
        List<SrtDocument.Line> lines = List.of(new SrtDocument.Line("c270_l0", "Hello!", "Hello!", "", List.of()));
        for (String reply : List.of(
            "{\"translations\":{\"c270_10\":\"Živjo!\"}}",
            "{\"translations\":{\"c270_I0\":\"Živjo!\"}}",
            "{\"translations\":{\"c270_l0\":\"Živjo!\",\"c270_l0\":\"Drug odgovor\"}}"))
            assertThrows(IllegalArgumentException.class, () -> OpenAiTranslator.parseResponse(reply, lines));
    }
    @Test public void incompleteKeyedReplyRepairsOnlyItsMissingProperty() throws Exception {
        SrtDocument document = SrtDocument.parse("271\n00:00:01,000 --> 00:00:03,000\nHello!\nHow are you?\n\n");
        AtomicInteger calls = new AtomicInteger(); Map<String, String> saved = new LinkedHashMap<>();
        OpenAiTranslator translator = new OpenAiTranslator("fake", "gpt-4.1-mini", request -> {
            if (calls.incrementAndGet() == 1) return "{\"translations\":{\"c0_l0\":\"Živjo!\"}}";
            assertEquals(Set.of("c0_l1"), keys(translationSchema(request).getJSONObject("properties")));
            assertEquals("Živjo!", saved.get("c0_l0"));
            return keyedReply(request);
        });
        assertEquals(2, translator.translate(document.lines, () -> {}, saved::putAll).size());
        assertEquals(2, calls.get()); assertEquals("Kako si?", saved.get("c0_l1"));
    }
    @Test public void resume1032LinesFrom440ConfirmedRowsUsesFixedC270L0KeyAndPreservesAllText() throws Exception {
        StringBuilder source = new StringBuilder();
        for (int i = 0; i < 516; i++) source.append(i + 1).append("\n00:00:01,000 --> 00:00:03,000\nHello!\nHow are you?\n\n");
        SrtDocument document = SrtDocument.parse(source.toString()); Map<String, String> cache = new LinkedHashMap<>();
        for (int i = 0; i < 440; i++) { SrtDocument.Line line = document.lines.get(i); cache.put(line.id(), line.prompt().replace("Hello!", "Živjo!").replace("How are you?", "Kako si?")); }
        Set<String> existing = Set.copyOf(cache.keySet()); AtomicInteger targetRequests = new AtomicInteger();
        OpenAiTranslator translator = new OpenAiTranslator("fake", "gpt-4.1-mini", request -> {
            JSONObject schema = translationSchema(request); Set<String> requested = keys(schema.getJSONObject("properties"));
            assertEquals("object", schema.getString("type")); assertFalse(schema.getBoolean("additionalProperties"));
            assertTrue(Collections.disjoint(existing, requested));
            if (requested.contains("c270_l0")) {
                assertEquals("string", schema.getJSONObject("properties").getJSONObject("c270_l0").getString("type"));
                assertFalse(requested.contains("c270_10")); targetRequests.incrementAndGet();
            }
            return keyedReply(request);
        });
        String result = TranslationEngine.translate(document, cache, translator, () -> {}, cache::putAll);
        assertEquals(1, targetRequests.get()); assertEquals(1032, cache.size());
        assertEquals(source.toString().replace("Hello!", "Živjo!").replace("How are you?", "Kako si?"), result);
        assertEquals(1032, SrtDocument.parse(result).lines.size());
    }
    @Test public void keyedValuesMustContainActualText() {
        List<SrtDocument.Line> lines = List.of(new SrtDocument.Line("c270_l0", "Hello!", "Hello!", "", List.of()));
        for (String value : List.of("null", "42", "{}", "[]", "\"\"", "\"   \""))
            assertThrows(IllegalArgumentException.class, () -> OpenAiTranslator.parseResponse("{\"translations\":{\"c270_l0\":" + value + "}}", lines));
    }
    @Test public void rejectsMalformedOrAdditionalReplyContent() {
        List<SrtDocument.Line> lines = List.of(new SrtDocument.Line("c270_l0", "Hello!", "Hello!", "", List.of()));
        for (String reply : List.of(
            "{\"translations\":{\"c270_l0\":\"Živjo!\"},\"translations\":{\"c270_l0\":\"Drugo\"}}",
            "{\"translations\":{\"c270_l0\":\"Živjo!\"}}more",
            "{\"translations\":{\"c270_l0\" \"Živjo!\"}}",
            "{\"translations\":{\"c270_l0\":\"Živjo!\",}}"))
            assertThrows(JSONException.class, () -> OpenAiTranslator.parseResponse(reply, lines));
    }
}

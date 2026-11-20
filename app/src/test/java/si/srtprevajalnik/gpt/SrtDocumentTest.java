package si.srtprevajalnik.gpt;

import org.junit.Test;
import org.json.*;
import java.nio.charset.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class SrtDocumentTest {
    static final String SOURCE = "001\r\n00:00:01,200 --> 00:00:03,400 X1:10\r\n- <i>Hello!</i>\r\n- How are you?\r\n\r\n7\r\n00:00:05,000 --> 00:00:07,000\r\n{\\an8}Good morning.\r\n\r\n";
    static OpenAiTranslator fake(AtomicInteger calls) {
        return new OpenAiTranslator("fake-key", "gpt-4.1-mini", body -> {
            calls.incrementAndGet();
            assertEquals("json_schema", body.getJSONObject("response_format").getString("type"));
            JSONArray input = new JSONArray(body.getJSONArray("messages").getJSONObject(1).getString("content"));
            JSONArray output = new JSONArray();
            for (int i = 0; i < input.length(); i++) {
                JSONObject item = input.getJSONObject(i); String source = item.getString("text");
                assertFalse(source.contains("-->"));
                output.put(new JSONObject().put("id", item.getString("id")).put("text", source
                    .replace("Hello!", "Živjo!").replace("How are you?", "Kako si?").replace("Good morning.", "Dobro jutro.")));
            }
            return new JSONObject().put("translations", output).toString();
        });
    }
    @Test public void preservesEveryCueLineTimestampAndNumber() throws Exception {
        SrtDocument source = SrtDocument.parse(SOURCE); AtomicInteger calls = new AtomicInteger();
        String output = TranslationEngine.translate(source, Map.of(), fake(calls), () -> {}, progress -> assertEquals(3, progress.size()));
        assertEquals(SOURCE.replace("Hello!", "Živjo!").replace("How are you?", "Kako si?").replace("Good morning.", "Dobro jutro."), output);
        assertEquals(1, calls.get()); assertEquals(3, SrtDocument.parse(output).lines.size());
    }
    @Test public void rejectsMissingDialogue() {
        SrtDocument doc = SrtDocument.parse(SOURCE);
        assertThrows(IllegalArgumentException.class, () -> doc.render(Map.of("c0_l0", "⟦T0⟧Živjo!⟦T1⟧")));
    }
    @Test public void rejectsDuplicatedUnknownMissingAndBlankResponseLines() {
        var lines = SrtDocument.parse("1\n00:00:01,000 --> 00:00:02,000\nHello!\nHow are you?\n").lines;
        for (String json : List.of(
            "{\"translations\":[{\"id\":\"c0_l0\",\"text\":\"Živjo\"}]}",
            "{\"translations\":[{\"id\":\"c0_l0\",\"text\":\"Živjo\"},{\"id\":\"c0_l0\",\"text\":\"Kako si\"}]}",
            "{\"translations\":[{\"id\":\"c0_l0\",\"text\":\"Živjo\"},{\"id\":\"unknown\",\"text\":\"Kako si\"}]}",
            "{\"translations\":[{\"id\":\"c0_l0\",\"text\":\"\"},{\"id\":\"c0_l1\",\"text\":\"Kako si\"}]}"))
            assertThrows(IllegalArgumentException.class, () -> OpenAiTranslator.parseResponse(json, lines));
    }
    @Test public void rejectsLostOrAddedFormattingAndExtraNewline() {
        SrtDocument.Line line = SrtDocument.parse(SOURCE).lines.get(0);
        for (String text : List.of("Živjo!", "⟦T0⟧Živjo!", "⟦T1⟧Živjo!⟦T0⟧", "⟦T0⟧⟦T1⟧", "⟦T0⟧Živjo!⟦T1⟧\nDruga vrstica", "⟦T0⟧<b>Živjo!</b>⟦T1⟧"))
            assertThrows(IllegalArgumentException.class, () -> line.restore(text));
    }
    @Test public void rejectsBrokenSrtInsteadOfDroppingBlocks() {
        for (String source : List.of("", "not srt", "1\n00:00:01,000 --> 00:00:00,000\nHello", "1\n00:99:01,000 --> 00:00:02,000\nHello", SOURCE + "bad block", "1\n00:00:01,000 --> 00:00:02,000\n"))
            assertThrows(IllegalArgumentException.class, () -> SrtDocument.parse(source));
    }
    @Test public void readsUtf8BomUtf16AndWindows1252() {
        assertEquals(3, SrtDocument.parse(SrtDocument.decode(("\uFEFF" + SOURCE).getBytes(StandardCharsets.UTF_8))).lines.size());
        assertEquals(3, SrtDocument.parse(SrtDocument.decode(SOURCE.getBytes(StandardCharsets.UTF_16))).lines.size());
        assertEquals("It’s fine.", SrtDocument.decode("It’s fine.".getBytes(Charset.forName("windows-1252"))));
    }
    @Test public void checkpointsAndResumesOnlyUnfinishedBatches() throws Exception {
        StringBuilder text = new StringBuilder();
        for (int i = 1; i <= 125; i++) text.append(i).append("\n00:00:01,000 --> 00:00:02,000\nHello!\n\n");
        SrtDocument doc = SrtDocument.parse(text.toString()); Map<String, String> cache = new LinkedHashMap<>(); AtomicInteger calls = new AtomicInteger();
        assertThrows(InterruptedException.class, () -> TranslationEngine.translate(doc, cache, fake(calls), () -> {}, checkpoint -> {
            cache.putAll(checkpoint); throw new InterruptedException();
        }));
        assertEquals(40, cache.size());
        String output = TranslationEngine.translate(doc, cache, fake(calls), () -> {}, cache::putAll);
        assertEquals(125, SrtDocument.parse(output).cues.size()); assertEquals(125, cache.size());
        assertEquals(4, calls.get());
    }
    @Test public void cancellationPreventsNetworkRequest() {
        AtomicInteger calls = new AtomicInteger();
        assertThrows(InterruptedException.class, () -> TranslationEngine.translate(SrtDocument.parse(SOURCE), Map.of(), fake(calls), () -> { throw new InterruptedException(); }, p -> {}));
        assertEquals(0, calls.get());
    }
    @Test public void cachedInvalidLineCannotBeExported() {
        assertThrows(IllegalArgumentException.class, () -> TranslationEngine.translate(SrtDocument.parse(SOURCE), Map.of("c0_l0", ""), fake(new AtomicInteger()), () -> {}, p -> {}));
    }
    @Test public void preservesDuplicateSourceNumbersAndLiteralLineBreakTags() throws Exception {
        String source = "5\n00:00:01,000 --> 00:00:02,000\nHello!\\NGood morning.\n\n5\n00:00:02,000 --> 00:00:03,000\nHello!\n\n";
        String output = TranslationEngine.translate(SrtDocument.parse(source), Map.of(), fake(new AtomicInteger()), () -> {}, p -> {});
        assertEquals(source.replace("Hello!", "Živjo!").replace("Good morning.", "Dobro jutro."), output);
    }
    @Test public void acceptsExactlyOneThrough25Files() {
        SubtitleFiles.validateCount(1); SubtitleFiles.validateCount(25);
        assertThrows(IllegalArgumentException.class, () -> SubtitleFiles.validateCount(26));
        assertThrows(IllegalArgumentException.class, () -> SubtitleFiles.validateCount(0));
    }
    @Test public void exportNamesAreUniqueAndCannotEscapeArchive() {
        assertEquals(List.of("a.sl.srt", "a.sl (2).srt", "A.SL (3).SRT"), SubtitleFiles.uniqueNames(List.of("a.sl.srt", "a.sl.srt", "A.SL.SRT")));
        assertFalse(SubtitleFiles.safeName("../../subtitle.srt").contains("/"));
    }
    @Test public void punctuationAndFormattingOnlyLinesStayLocalAndIntact() throws Exception {
        String source = "1\n00:00:01,000 --> 00:00:02,000\n-\n<i>♪</i>\n...\n\n";
        AtomicInteger calls = new AtomicInteger();
        assertEquals(source, TranslationEngine.translate(SrtDocument.parse(source), Map.of(), fake(calls), () -> {}, p -> {}));
        assertEquals(0, calls.get());
    }
}

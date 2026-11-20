package si.srtprevajalnik.gpt;

import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;
import java.util.regex.*;

/** Timing and numbering never leave this local document; only identified text lines are translated. */
public final class SrtDocument {
    private static final Pattern TIMING = Pattern.compile("(\\d{2,}):([0-5]\\d):([0-5]\\d)[,.](\\d{3})[ \\t]+-->[ \\t]+(\\d{2,}):([0-5]\\d):([0-5]\\d)[,.](\\d{3})(?:[ \\t]+[^\\r\\n]*)?");
    private static final Pattern MARKUP = Pattern.compile("<[^>\\r\\n]+>|\\{[^}\\r\\n]*\\}|\\\\[Nnh]");
    private static final Pattern TOKEN = Pattern.compile("⟦T\\d+⟧");
    public record Cue(String number, String timing, List<Line> lines) {}
    public record Line(String id, String source, String prompt, String prefix, List<String> tags) {
        public String restore(String translated) {
            if (translated == null || (translated.isBlank() && !prompt.isBlank()) || translated.contains("\n") || translated.contains("\r"))
                throw new IllegalArgumentException("Manjka dialog ali pa je prevod spremenil število vrstic: " + id);
            List<String> found = new ArrayList<>();
            Matcher m = TOKEN.matcher(translated);
            while (m.find()) found.add(m.group());
            List<String> expected = new ArrayList<>();
            for (int i = 0; i < tags.size(); i++) expected.add("⟦T" + i + "⟧");
            if (!found.equals(expected)) throw new IllegalArgumentException("Prevod je spremenil oznake oblikovanja: " + id);
            String restored = translated;
            // Reverse replacement also protects literal token-like text restored from the source.
            for (int i = tags.size() - 1; i >= 0; i--) restored = restored.replace("⟦T" + i + "⟧", tags.get(i));
            if (MARKUP.matcher(translated).find()) throw new IllegalArgumentException("Prevod je dodal oznake oblikovanja: " + id);
            String sourceWords = TOKEN.matcher(prompt).replaceAll("").replaceAll("[^\\p{L}\\p{N}]", "");
            String translatedWords = TOKEN.matcher(translated).replaceAll("").replaceAll("[^\\p{L}\\p{N}]", "");
            if (!sourceWords.isEmpty() && translatedWords.isEmpty()) throw new IllegalArgumentException("Prevod je izpustil besedilo: " + id);
            return prefix + restored.strip();
        }
    }
    public final List<Cue> cues;
    public final List<Line> lines;
    private final String newline;
    private SrtDocument(List<Cue> cues, List<Line> lines, String newline) {
        this.cues = List.copyOf(cues); this.lines = List.copyOf(lines); this.newline = newline;
    }
    public static String decode(byte[] bytes) {
        if (bytes.length >= 2 && bytes[0] == (byte)0xff && bytes[1] == (byte)0xfe)
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16LE);
        if (bytes.length >= 2 && bytes[0] == (byte)0xfe && bytes[1] == (byte)0xff)
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16BE);
        try { return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); }
        catch (CharacterCodingException e) { return Charset.forName("windows-1252").decode(ByteBuffer.wrap(bytes)).toString(); }
    }
    public static SrtDocument parse(String input) {
        if (input.length() > 8 * 1024 * 1024) throw new IllegalArgumentException("Datoteka je prevelika (največ 8 MB).");
        String newline = input.contains("\r\n") ? "\r\n" : "\n";
        String normalized = input.replaceFirst("^\\uFEFF", "").replace("\r\n", "\n").replace('\r', '\n').strip();
        if (normalized.isEmpty() || normalized.indexOf('\0') >= 0) throw new IllegalArgumentException("Datoteka je prazna ali ni veljaven SRT.");
        List<Cue> cues = new ArrayList<>(); List<Line> lines = new ArrayList<>();
        for (String block : normalized.split("\n[ \\t]*\n+")) {
            String[] parts = block.split("\n", -1);
            if (parts.length < 3 || !parts[0].matches("[ \\t]*\\d+[ \\t]*"))
                throw new IllegalArgumentException("Neveljaven blok SRT pri bloku " + (cues.size() + 1));
            Matcher timing = TIMING.matcher(parts[1]);
            if (!timing.matches()) throw new IllegalArgumentException("Neveljavna časovna oznaka pri podnapisu " + parts[0]);
            if (millis(timing, 1) > millis(timing, 5)) throw new IllegalArgumentException("Konec je pred začetkom pri podnapisu " + parts[0]);
            List<Line> cueLines = new ArrayList<>();
            for (int j = 2; j < parts.length; j++) {
                if (parts[j].isBlank()) throw new IllegalArgumentException("Prazen dialog pri podnapisu " + parts[0]);
                String source = parts[j]; String prefix = ""; String body = source;
                Matcher speaker = Pattern.compile("^([ \\t]*[-–—][ \\t]*)(.*)$").matcher(source);
                if (speaker.matches()) { prefix = speaker.group(1); body = speaker.group(2); }
                List<String> tags = new ArrayList<>(); Matcher m = MARKUP.matcher(body); StringBuffer prompt = new StringBuffer();
                while (m.find()) { m.appendReplacement(prompt, Matcher.quoteReplacement("⟦T" + tags.size() + "⟧")); tags.add(m.group()); }
                m.appendTail(prompt);
                if (TOKEN.matcher(body).find()) throw new IllegalArgumentException("Besedilo vsebuje rezervirano oznako ⟦T…⟧.");
                Line line = new Line("c" + cues.size() + "_l" + (j - 2), source, prompt.toString(), prefix, List.copyOf(tags));
                cueLines.add(line); lines.add(line);
            }
            cues.add(new Cue(parts[0], parts[1], List.copyOf(cueLines)));
        }
        return new SrtDocument(cues, lines, newline);
    }
    private static long millis(Matcher m, int group) {
        try { return Math.addExact(Math.multiplyExact(Long.parseLong(m.group(group)), 3600000L), Long.parseLong(m.group(group + 1)) * 60000 + Long.parseLong(m.group(group + 2)) * 1000 + Long.parseLong(m.group(group + 3))); }
        catch (NumberFormatException | ArithmeticException e) { throw new IllegalArgumentException("Prevelika časovna oznaka."); }
    }
    public String render(Map<String, String> translations) {
        if (translations.size() != lines.size()) throw new IllegalArgumentException("Prevod nima vseh dialogov.");
        StringBuilder out = new StringBuilder();
        for (Cue cue : cues) {
            out.append(cue.number()).append(newline).append(cue.timing()).append(newline);
            for (Line line : cue.lines()) out.append(line.restore(translations.get(line.id()))).append(newline);
            out.append(newline);
        }
        return out.toString();
    }
    public static List<List<Line>> batches(List<Line> lines) {
        List<List<Line>> batches = new ArrayList<>(); List<Line> current = new ArrayList<>(); int chars = 0;
        for (Line line : lines) {
            if (!current.isEmpty() && (current.size() >= 40 || chars + line.prompt().length() > 4500)) {
                batches.add(List.copyOf(current)); current.clear(); chars = 0;
            }
            if (line.prompt().length() > 12000) throw new IllegalArgumentException("Posamezna vrstica je predolga za zanesljiv prevod.");
            current.add(line); chars += line.prompt().length();
        }
        if (!current.isEmpty()) batches.add(List.copyOf(current));
        return batches;
    }
}

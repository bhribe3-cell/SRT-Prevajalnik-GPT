package si.srtprevajalnik.gpt;

import java.io.IOException;
import java.util.*;

/** A file becomes exportable only after every line and all structural invariants pass. */
final class TranslationEngine {
    interface Checkpoint { void save(Map<String, String> translations) throws Exception; }
    static String translate(SrtDocument document, Map<String, String> cached, OpenAiTranslator translator,
                            OpenAiTranslator.Cancel cancel, Checkpoint checkpoint) throws Exception {
        Map<String, String> translated = new LinkedHashMap<>();
        for (SrtDocument.Line line : document.lines)
            if (!line.prompt().replaceAll("⟦T\\d+⟧", "").matches("(?s).*\\p{L}.*")) translated.put(line.id(), line.prompt());
        for (SrtDocument.Line line : document.lines) if (cached.containsKey(line.id())) {
            String text = cached.get(line.id()); line.restore(text); translated.put(line.id(), text);
        }
        List<SrtDocument.Line> pending = new ArrayList<>();
        for (SrtDocument.Line line : document.lines) if (!translated.containsKey(line.id())) pending.add(line);
        for (List<SrtDocument.Line> batch : SrtDocument.batches(pending)) {
            cancel.check();
            translated.putAll(translator.translate(batch, document.lines, cancel, confirmed -> {
                cancel.check(); translated.putAll(confirmed);
                checkpoint.save(Collections.unmodifiableMap(new LinkedHashMap<>(translated)));
            }));
            cancel.check();
        }
        cancel.check();
        String rendered = document.render(translated); SrtDocument validation = SrtDocument.parse(rendered);
        if (validation.cues.size() != document.cues.size() || validation.lines.size() != document.lines.size())
            throw new IOException("Prevod ni prestal preverjanja celovitosti.");
        for (int i = 0; i < document.cues.size(); i++)
            if (!document.cues.get(i).number().equals(validation.cues.get(i).number()) || !document.cues.get(i).timing().equals(validation.cues.get(i).timing()))
                throw new IOException("Prevod je spremenil časovne oznake ali številčenje.");
        return rendered;
    }
}

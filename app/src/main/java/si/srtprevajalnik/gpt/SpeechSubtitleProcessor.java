package si.srtprevajalnik.gpt;

import java.util.*;

final class SpeechSubtitleProcessor {
    static String run(long durationMs, List<SpeechTimeline.Window> previousWindows,
                      SpeechTranscriptionEngine.Source source, SpeechTranscriptionEngine.Transcriber transcriber,
                      SpeechTranscriptionEngine.Checkpoint speechCheckpoint, Map<String, String> previousTranslations,
                      OpenAiTranslator translator, TranslationEngine.Checkpoint translationCheckpoint,
                      OpenAiTranslator.Cancel cancel) throws Exception {
        List<VideoTimeline.Interval> intervals = SpeechTranscriptionEngine.scan(durationMs, previousWindows, source, transcriber,
            speechCheckpoint, cancel::check);
        cancel.check();
        SrtDocument document = SrtDocument.parse(VideoTimeline.toSrt(intervals));
        return TranslationEngine.translate(document, previousTranslations, translator, cancel, translationCheckpoint);
    }
}

package si.srtprevajalnik.gpt;

import java.util.*;

/** Produces complete SRT only after every frame and translated dialogue is validated. */
final class VideoOcrProcessor {
    static String run(long durationMs, List<VideoTimeline.Sample> previousFrames,
                      VideoOcrEngine.Source source, VideoOcrEngine.Recognizer recognizer,
                      VideoOcrEngine.Checkpoint frameCheckpoint, Map<String, String> previousTranslations,
                      OpenAiTranslator translator, TranslationEngine.Checkpoint translationCheckpoint,
                      OpenAiTranslator.Cancel cancel) throws Exception {
        List<VideoTimeline.Interval> intervals = VideoOcrEngine.scan(durationMs, previousFrames, source, recognizer,
            frameCheckpoint, cancel::check);
        cancel.check();
        SrtDocument document = SrtDocument.parse(VideoTimeline.toSrt(intervals));
        return TranslationEngine.translate(document, previousTranslations, translator, cancel, translationCheckpoint);
    }
}

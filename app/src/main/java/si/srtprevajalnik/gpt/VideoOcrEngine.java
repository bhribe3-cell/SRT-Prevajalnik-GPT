package si.srtprevajalnik.gpt;

import java.util.*;

/** Scans fixed-size frame batches and checkpoints only completely validated batches. */
final class VideoOcrEngine {
    interface Source { VideoFrameRecognizer.Frame read(long timeMs) throws Exception; }
    interface Recognizer { List<VideoTimeline.Sample> recognize(List<VideoFrameRecognizer.Frame> frames) throws Exception; }
    interface Checkpoint { void save(List<VideoTimeline.Sample> confirmed) throws Exception; }
    interface Cancel { void check() throws InterruptedException; }

    static List<VideoTimeline.Interval> scan(long durationMs, List<VideoTimeline.Sample> checkpoint, Source source,
                                              Recognizer recognizer, Checkpoint save, Cancel cancel) throws Exception {
        long count = VideoTimeline.sampleCount(durationMs, VideoTimeline.FRAME_INTERVAL_MS);
        if (count > Integer.MAX_VALUE || checkpoint.size() > count) throw new IllegalArgumentException("Neveljaven shranjeni napredek filma.");
        List<VideoTimeline.Sample> confirmed = new ArrayList<>(checkpoint);
        for (int i = 0; i < confirmed.size(); i++) {
            VideoTimeline.Sample sample = confirmed.get(i);
            if (sample == null || sample.text() == null || sample.timeMs() != VideoTimeline.sampleTime(i, durationMs, VideoTimeline.FRAME_INTERVAL_MS))
                throw new IllegalArgumentException("Shranjeni napredek ni skladen s filmom.");
            VideoTimeline.normalizeText(sample.text());
        }
        for (long start = confirmed.size(); start < count; start += VideoTimeline.MAX_FRAME_BATCH) {
            cancel.check();
            int n = (int)Math.min(VideoTimeline.MAX_FRAME_BATCH, count - start);
            List<VideoFrameRecognizer.Frame> frames = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                cancel.check(); long time = VideoTimeline.sampleTime(start + i, durationMs, VideoTimeline.FRAME_INTERVAL_MS);
                VideoFrameRecognizer.Frame frame = source.read(time);
                if (frame == null || frame.timeMs() != time) throw new IllegalArgumentException("Prebrana je napačna sličica.");
                frames.add(frame);
            }
            List<VideoTimeline.Sample> received = recognizer.recognize(List.copyOf(frames)); cancel.check();
            if (received == null || received.size() != frames.size()) throw new IllegalArgumentException("Manjka rezultat sličice.");
            for (int i = 0; i < frames.size(); i++) {
                VideoTimeline.Sample sample = received.get(i);
                if (sample == null || sample.timeMs() != frames.get(i).timeMs() || sample.text() == null)
                    throw new IllegalArgumentException("Rezultati sličic niso pravilno razvrščeni.");
                VideoTimeline.normalizeText(sample.text());
            }
            confirmed.addAll(received); save.save(Collections.unmodifiableList(new ArrayList<>(confirmed)));
        }
        return VideoTimeline.fromFrames(confirmed, durationMs, VideoTimeline.FRAME_INTERVAL_MS);
    }
}

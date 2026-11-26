package si.srtprevajalnik.gpt;

import java.util.*;

/** Keeps coarse audio-window bounds with each complete transcript. */
final class SpeechTimeline {
    static final long WINDOW_MS = 4000;
    record Window(long startMs, long endMs, String transcript) {}

    static long windowCount(long durationMs) {
        if (durationMs <= 0) throw new IllegalArgumentException("Neveljavno trajanje zvoka.");
        return 1 + (durationMs - 1) / WINDOW_MS;
    }
    static long start(long index, long durationMs) {
        if (index < 0 || index >= windowCount(durationMs)) throw new IllegalArgumentException("Neveljaven položaj zvoka.");
        return Math.multiplyExact(index, WINDOW_MS);
    }
    static long end(long index, long durationMs) { return Math.min(Math.addExact(start(index, durationMs), WINDOW_MS), durationMs); }
    static List<VideoTimeline.Interval> toIntervals(List<Window> windows, long durationMs) {
        if (windowCount(durationMs) > Integer.MAX_VALUE || windows.size() != windowCount(durationMs))
            throw new IllegalArgumentException("Manjka zvočni odsek.");
        List<VideoTimeline.Interval> result = new ArrayList<>();
        for (int i = 0; i < windows.size(); i++) {
            Window window = windows.get(i);
            if (window == null || window.startMs() != start(i, durationMs) || window.endMs() != end(i, durationMs) || window.transcript() == null)
                throw new IllegalArgumentException("Zvočni odseki niso popolni ali pravilno razvrščeni.");
            String text = VideoTimeline.normalizeText(window.transcript());
            if (!text.isEmpty()) result.add(new VideoTimeline.Interval(window.startMs(), window.endMs(), text));
        }
        return List.copyOf(result);
    }
}

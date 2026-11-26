package si.srtprevajalnik.gpt;

import java.util.*;

/** Timeline calculations use milliseconds and never load the video into memory. */
final class VideoTimeline {
    static final long MAX_VIDEO_BYTES = 20_000_000_000L;
    static final long MAX_AUDIO_UPLOAD_BYTES = 24_000_000L;
    static final int MAX_FRAME_BATCH = 8;
    static final long FRAME_INTERVAL_MS = 500;

    record Interval(long startMs, long endMs, String text) {}
    record Sample(long timeMs, String text) {}

    static void checkVideoSize(long size) {
        if (size > MAX_VIDEO_BYTES) throw new IllegalArgumentException("Film je večji od 20 GB.");
        if (size == 0) throw new IllegalArgumentException("Film je prazen.");
        // A provider is allowed to report -1 when the size is unknown.
        if (size < -1) throw new IllegalArgumentException("Neveljavna velikost filma.");
    }
    static long sampleCount(long durationMs, long intervalMs) {
        if (durationMs <= 0 || intervalMs <= 0) throw new IllegalArgumentException("Neveljavno trajanje filma.");
        return 1 + (durationMs - 1) / intervalMs;
    }
    static long sampleTime(long index, long durationMs, long intervalMs) {
        long count = sampleCount(durationMs, intervalMs);
        if (index < 0 || index >= count) throw new IllegalArgumentException("Neveljaven položaj sličice.");
        return Math.multiplyExact(index, intervalMs);
    }
    static List<Interval> fromFrames(List<Sample> frames, long durationMs, long intervalMs) {
        long expectedCount = sampleCount(durationMs, intervalMs);
        if (expectedCount > Integer.MAX_VALUE || frames.size() != expectedCount)
            throw new IllegalArgumentException("Manjka vsaj ena pregledana sličica.");
        List<Interval> result = new ArrayList<>(); String current = null; long begin = 0;
        for (int i = 0; i < frames.size(); i++) {
            Sample sample = frames.get(i); long expected = sampleTime(i, durationMs, intervalMs);
            if (sample == null || sample.timeMs() != expected || sample.text() == null)
                throw new IllegalArgumentException("Manjka rezultat sličice pri " + expected + " ms.");
            String text = normalizeText(sample.text());
            if (!Objects.equals(current, text)) {
                if (current != null && !current.isEmpty()) result.add(new Interval(begin, expected, current));
                begin = expected; current = text;
            }
        }
        if (current != null && !current.isEmpty()) result.add(new Interval(begin, durationMs, current));
        return List.copyOf(result);
    }
    static String toSrt(List<Interval> intervals) {
        if (intervals.isEmpty()) throw new IllegalArgumentException("Podnapisov ni bilo mogoče zaznati.");
        StringBuilder output = new StringBuilder(); long previous = -1; int number = 1;
        for (Interval interval : intervals) {
            if (interval == null || interval.startMs() < 0 || interval.endMs() <= interval.startMs() || interval.startMs() < previous)
                throw new IllegalArgumentException("Neveljavna ali prekrivajoča se časovna oznaka.");
            String text = normalizeText(interval.text());
            if (text.isBlank()) throw new IllegalArgumentException("Prazen podnapis.");
            output.append(number++).append('\n').append(timestamp(interval.startMs())).append(" --> ")
                .append(timestamp(interval.endMs())).append('\n').append(text).append("\n\n");
            previous = interval.endMs();
        }
        String rendered = output.toString();
        SrtDocument parsed = SrtDocument.parse(rendered);
        if (parsed.cues.size() != intervals.size()) throw new IllegalArgumentException("Podnapisi niso prestali preverjanja.");
        return rendered;
    }
    static String normalizeText(String text) {
        if (text == null) throw new IllegalArgumentException("Manjka besedilo podnapisa.");
        String value = text.replace("\r\n", "\n").replace('\r', '\n').strip();
        if (value.indexOf('\0') >= 0 || value.matches("(?s).*\\n[ \\t]*\\n.*"))
            throw new IllegalArgumentException("Besedilo vsebuje neveljaven prelom.");
        return value;
    }
    private static String timestamp(long milliseconds) {
        long hours = milliseconds / 3_600_000;
        long minutes = milliseconds / 60_000 % 60, seconds = milliseconds / 1_000 % 60, millis = milliseconds % 1_000;
        return String.format(Locale.ROOT, "%02d:%02d:%02d,%03d", hours, minutes, seconds, millis);
    }
}

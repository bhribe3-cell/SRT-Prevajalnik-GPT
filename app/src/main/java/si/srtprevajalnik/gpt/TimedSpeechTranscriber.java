package si.srtprevajalnik.gpt;

import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Parses the documented diarized_json format with times relative to the uploaded audio. */
final class TimedSpeechTranscriber {
    interface Exchange { String complete(OpenAiSpeechTranscriber.Multipart body) throws Exception; }
    private final OpenAiSpeechTranscriber transport;
    private final Exchange exchange;
    TimedSpeechTranscriber(String key) { this(key, null); }
    TimedSpeechTranscriber(String key, Exchange exchange) { transport = new OpenAiSpeechTranscriber(key); this.exchange = exchange; }
    void cancel() { transport.cancel(); }
    List<VideoTimeline.Interval> transcribe(SpeechTranscriptionEngine.Audio audio, OpenAiSpeechTranscriber.Cancel cancel) throws Exception {
        if (audio == null || audio.wave() == null || audio.wave().length <= 44 || audio.wave().length > VideoTimeline.MAX_AUDIO_UPLOAD_BYTES || audio.endMs() <= audio.startMs())
            throw new IOException("Neveljaven zvočni odsek.");
        byte[] wave = audio.wave();
        if (wave[0] != 'R' || wave[1] != 'I' || wave[2] != 'F' || wave[3] != 'F' || wave[8] != 'W' || wave[9] != 'A' || wave[10] != 'V' || wave[11] != 'E') throw new IOException("Zvočni odsek ni WAV.");
        String boundary = "srt-" + UUID.randomUUID();
        String header = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"model\"\r\n\r\ngpt-4o-transcribe-diarize\r\n"
            + "--" + boundary + "\r\nContent-Disposition: form-data; name=\"response_format\"\r\n\r\ndiarized_json\r\n"
            + "--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"audio.wav\"\r\nContent-Type: audio/wav\r\n\r\n";
        OpenAiSpeechTranscriber.Multipart body = new OpenAiSpeechTranscriber.Multipart("multipart/form-data; boundary=" + boundary,
            header.getBytes(StandardCharsets.US_ASCII), wave, ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII));
        cancel.check(); String raw = exchange != null ? exchange.complete(body) : transport.request(body); cancel.check();
        JSONObject response = new JSONObject(raw); Object full = response.opt("text"); Object rawSegments = response.opt("segments");
        if (!(full instanceof String) || !(rawSegments instanceof JSONArray)) throw new IOException("OpenAI ni vrnil časovno označenega prepisa.");
        JSONArray segments = (JSONArray)rawSegments; List<VideoTimeline.Interval> result = new ArrayList<>(); StringBuilder combined = new StringBuilder(); long length = audio.endMs() - audio.startMs();
        for (int i = 0; i < segments.length(); i++) {
            JSONObject part = segments.getJSONObject(i); Object text = part.opt("text"), start = part.opt("start"), end = part.opt("end");
            if (!(text instanceof String) || !(start instanceof Number) || !(end instanceof Number)) throw new IOException("OpenAI je vrnil neveljaven govorni odsek.");
            double a = ((Number)start).doubleValue(), b = ((Number)end).doubleValue();
            if (!Double.isFinite(a) || !Double.isFinite(b) || a < 0 || b <= a || b * 1000 > length + 100) throw new IOException("OpenAI je vrnil neveljaven čas govora.");
            long begin = Math.min(length, Math.round(a * 1000)), finish = Math.min(length, Math.round(b * 1000));
            String normalized = VideoTimeline.normalizeText((String)text);
            if (finish <= begin || normalized.isEmpty()) throw new IOException("OpenAI je vrnil prazen govorni odsek.");
            combined.append(normalized);
            result.add(new VideoTimeline.Interval(audio.startMs() + begin, audio.startMs() + finish, normalized));
        }
        if ((!((String)full).isBlank() && result.isEmpty()) || !canonical((String)full).equals(canonical(combined.toString())))
            throw new IOException("OpenAI ni vrnil vseh časovno označenih odsekov.");
        return List.copyOf(result);
    }
    private static String canonical(String text) {
        StringBuilder result = new StringBuilder();
        text.codePoints().filter(Character::isLetterOrDigit).map(Character::toLowerCase).forEach(result::appendCodePoint);
        return result.toString();
    }
}

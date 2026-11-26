package si.srtprevajalnik.gpt;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import javax.net.ssl.HttpsURLConnection;

/** Submits one bounded WAV window; gpt-transcribe returns text, not word-level timestamps. */
final class OpenAiSpeechTranscriber {
    record Multipart(String contentType, byte[] header, byte[] audio, byte[] footer) {
        long length() { return (long)header.length + audio.length + footer.length; }
        void writeTo(OutputStream out) throws IOException { out.write(header); out.write(audio); out.write(footer); }
    }
    interface Exchange { String complete(Multipart body) throws Exception; }
    interface Cancel { void check() throws InterruptedException; }
    private final String apiKey;
    private final Exchange exchange;
    private volatile HttpsURLConnection active;
    OpenAiSpeechTranscriber(String apiKey) { this(apiKey, null); }
    OpenAiSpeechTranscriber(String apiKey, Exchange exchange) { this.apiKey = apiKey; this.exchange = exchange; }
    void cancel() { HttpsURLConnection connection = active; if (connection != null) connection.disconnect(); }
    String transcribe(SpeechTranscriptionEngine.Audio audio, Cancel cancel) throws Exception {
        if (audio == null || audio.wave() == null || audio.wave().length <= 44 || audio.wave().length > VideoTimeline.MAX_AUDIO_UPLOAD_BYTES)
            throw new IOException("Zvočni odsek je prazen ali prevelik.");
        byte[] wave = audio.wave();
        if (wave[0] != 'R' || wave[1] != 'I' || wave[2] != 'F' || wave[3] != 'F'
            || wave[8] != 'W' || wave[9] != 'A' || wave[10] != 'V' || wave[11] != 'E')
            throw new IOException("Zvočni odsek ni veljavna datoteka WAV.");
        String boundary = "srt-" + UUID.randomUUID();
        String prefix = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"model\"\r\n\r\ngpt-transcribe\r\n"
            + "--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"audio.wav\"\r\n"
            + "Content-Type: audio/wav\r\n\r\n";
        Multipart multipart = new Multipart("multipart/form-data; boundary=" + boundary,
            prefix.getBytes(StandardCharsets.US_ASCII), wave, ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII));
        cancel.check(); String response = exchange == null ? request(multipart) : exchange.complete(multipart); cancel.check();
        JSONObject json = new JSONObject(response); Object text = json.opt("text");
        if (!(text instanceof String)) throw new IOException("OpenAI ni vrnil prepisa zvočnega odseka.");
        String result = VideoTimeline.normalizeText((String)text);
        if (result.length() > 20_000) throw new IOException("Prepis zvočnega odseka je predolg.");
        return result;
    }
    private String request(Multipart multipart) throws Exception {
        HttpsURLConnection connection = (HttpsURLConnection)new URL("https://api.openai.com/v1/audio/transcriptions").openConnection(); active = connection;
        try {
            connection.setRequestMethod("POST"); connection.setConnectTimeout(20000); connection.setReadTimeout(120000);
            connection.setInstanceFollowRedirects(false); connection.setDoOutput(true);
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("Content-Type", multipart.contentType());
            connection.setFixedLengthStreamingMode(multipart.length());
            try (OutputStream output = connection.getOutputStream()) { multipart.writeTo(output); }
            int status = connection.getResponseCode();
            if (status == 401 || status == 403) throw new IOException("OpenAI API-ključ ni veljaven ali nima dovoljenja.");
            if (status == 429) throw new IOException("OpenAI omejuje zahteve ali je porabljeno dobroimetje. Poskusi znova pozneje.");
            if (status != 200) throw new IOException("OpenAI je zavrnil prepis zvoka (HTTP " + status + ").");
            return OpenAiTranslator.read(connection.getInputStream());
        } finally { active = null; connection.disconnect(); }
    }
}

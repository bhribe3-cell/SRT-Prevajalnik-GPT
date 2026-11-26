package si.srtprevajalnik.gpt;

import android.content.Context;
import android.media.*;
import android.net.Uri;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.Collections;

/** Decodes one bounded audio window at a time directly from the selected provider URI. */
final class VideoAudioReader {
    private final Context context;
    private final Uri uri;
    VideoAudioReader(Context context, Uri uri) { this.context = context.getApplicationContext(); this.uri = uri; }

    SpeechTranscriptionEngine.Audio read(long startMs, long endMs) throws IOException, InterruptedException {
        MediaExtractor extractor = new MediaExtractor(); MediaCodec codec = null; boolean started = false;
        try {
            extractor.setDataSource(context, uri, Collections.emptyMap());
            MediaFormat format = null; int audioTrack = -1;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat candidate = extractor.getTrackFormat(i); String mime = candidate.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) { audioTrack = i; format = candidate; break; }
            }
            if (audioTrack < 0 || format == null) throw new IOException("Film nima zvočne sledi.");
            String mime = format.getString(MediaFormat.KEY_MIME);
            int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE), channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            PcmWindow window = new PcmWindow(startMs, endMs, sampleRate, channels);
            extractor.selectTrack(audioTrack); extractor.seekTo(startMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            if (extractor.getSampleTime() < 0) throw new IOException("Zvočne sledi ni mogoče prebrati.");
            format.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT);
            codec = MediaCodec.createDecoderByType(mime); codec.configure(format, null, null, 0); codec.start(); started = true;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo(); boolean inputEnd = false, outputEnd = false; long decodedBytes = 0;
            long deadline = android.os.SystemClock.elapsedRealtime() + 60_000;
            while (!outputEnd) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                if (android.os.SystemClock.elapsedRealtime() > deadline) throw new IOException("Branje zvočnega odseka je trajalo predolgo.");
                if (!inputEnd) {
                    int index = codec.dequeueInputBuffer(10_000);
                    if (index >= 0) {
                        ByteBuffer input = codec.getInputBuffer(index); if (input == null) throw new IOException("Zvočnega medpomnilnika ni mogoče odpreti.");
                        input.clear(); long pts = extractor.getSampleTime();
                        if (pts < 0 || pts >= endMs * 1000 + 500_000) {
                            codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputEnd = true;
                        } else {
                            if ((extractor.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_ENCRYPTED) != 0)
                                throw new IOException("Zaščitene zvočne sledi ni mogoče obdelati.");
                            int size = extractor.readSampleData(input, 0);
                            if (size < 0) { codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputEnd = true; }
                            else {
                                if (size > input.capacity()) throw new IOException("Vzorec zvoka je prevelik za dekoder.");
                                codec.queueInputBuffer(index, 0, size, pts, 0); extractor.advance();
                            }
                        }
                    }
                }
                int index = codec.dequeueOutputBuffer(info, 10_000);
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat output = codec.getOutputFormat();
                    if (output.getInteger(MediaFormat.KEY_SAMPLE_RATE) != sampleRate || output.getInteger(MediaFormat.KEY_CHANNEL_COUNT) != channels
                        || (output.containsKey(MediaFormat.KEY_PCM_ENCODING) && output.getInteger(MediaFormat.KEY_PCM_ENCODING) != AudioFormat.ENCODING_PCM_16BIT))
                        throw new IOException("Oblika dekodiranega zvoka ni podprta.");
                } else if (index >= 0) {
                    ByteBuffer output = codec.getOutputBuffer(index);
                    if (info.size > 0) {
                        if (output == null) throw new IOException("Dekodiranega zvoka ni mogoče prebrati.");
                        output.position(info.offset); output.limit(info.offset + info.size);
                        window.write(info.presentationTimeUs, output);
                        decodedBytes += info.size;
                    }
                    outputEnd = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    codec.releaseOutputBuffer(index, false);
                }
            }
            if (decodedBytes == 0) throw new IOException("Zvočni dekoder ni vrnil vzorcev; poskusi drug zapis filma.");
            return new SpeechTranscriptionEngine.Audio(startMs, endMs, window.wav());
        } catch (InterruptedException | IOException e) { throw e; }
        catch (Exception e) { throw new IOException("Zvočne sledi ni mogoče dekodirati v tej obliki.", e); }
        finally {
            if (codec != null) { if (started) try { codec.stop(); } catch (Exception ignored) {} codec.release(); }
            extractor.release();
        }
    }
}

package si.srtprevajalnik.gpt;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Base64;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class VideoMediaDeviceTest {
    @Test public void readsFramesAndDecodesAudioFromRealVideoOnAndroid() throws Exception {
        Context test = InstrumentationRegistry.getInstrumentation().getContext();
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File fixture = new File(app.getCacheDir(), "sample.mp4");
        try (InputStream in = test.getAssets().open("sample.mp4"); OutputStream out = new FileOutputStream(fixture)) { in.transferTo(out); }
        Uri uri = Uri.fromFile(fixture);
        try (VideoFrameReader reader = new VideoFrameReader(app, uri)) {
            assertTrue(reader.durationMs >= 2900 && reader.durationMs <= 3100);
            VideoFrameRecognizer.Frame frame = reader.frame(1000);
            assertEquals(1000, frame.timeMs());
            byte[] jpeg = Base64.decode(frame.jpegBase64(), Base64.NO_WRAP);
            android.graphics.Bitmap image = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length);
            assertNotNull(image); assertEquals(640, image.getWidth()); assertEquals(360, image.getHeight()); image.recycle();
            VideoFrameRecognizer fakeVision = new VideoFrameRecognizer("fake", "gpt-4.1-mini", body -> "{\"frames\":{\"f0\":\"\",\"f1\":\"Hello!\",\"f2\":\"Hello!\"}}");
            OpenAiTranslator fakeTranslator = new OpenAiTranslator("fake", "gpt-4.1-mini", body -> "{\"translations\":{\"c0_l0\":\"Živjo!\"}}");
            List<VideoTimeline.Sample> savedFrames = new ArrayList<>(); Map<String, String> savedTranslations = new LinkedHashMap<>();
            String output = VideoOcrProcessor.run(1500, savedFrames, reader::frame, batch -> fakeVision.recognize(batch, () -> {}),
                saved -> { savedFrames.clear(); savedFrames.addAll(saved); }, savedTranslations, fakeTranslator, savedTranslations::putAll, () -> {});
            assertEquals("1\n00:00:00,500 --> 00:00:01,500\nŽivjo!\n\n", output);
            assertEquals(3, savedFrames.size());
        }
        VideoAudioReader audio = new VideoAudioReader(app, uri);
        SpeechTranscriptionEngine.Audio part = audio.read(0, 2000);
        assertEquals(0, part.startMs()); assertEquals(2000, part.endMs());
        assertTrue(part.wave().length > 44 && part.wave().length < VideoTimeline.MAX_AUDIO_UPLOAD_BYTES);
        assertEquals('R', part.wave()[0]); assertEquals('W', part.wave()[8]);
        int nonzero = 0; for (int i = 44; i < part.wave().length; i++) if (part.wave()[i] != 0) nonzero++;
        assertTrue("Decoded sine wave must contain audible samples", nonzero > 1000);
        SpeechTranscriptionEngine.Audio last = audio.read(2000, 3000);
        int lastNonzero = 0; for (int i = 44; i < last.wave().length; i++) if (last.wave()[i] != 0) lastNonzero++;
        assertTrue("Seeking and decoding a later window must retain audio", lastNonzero > 100);
        List<SpeechTimeline.Window> savedSpeech = new ArrayList<>(); Map<String, String> translated = new LinkedHashMap<>();
        OpenAiTranslator fakeTranslator = new OpenAiTranslator("fake", "gpt-4.1-mini", body -> "{\"translations\":{\"c0_l0\":\"Živjo!\"}}");
        String speechOutput = SpeechSubtitleProcessor.run(3000, savedSpeech, audio::read,
            chunk -> { assertTrue(chunk.wave().length > 44); return "Hello!"; },
            saved -> { savedSpeech.clear(); savedSpeech.addAll(saved); }, translated, fakeTranslator, translated::putAll, () -> {});
        assertEquals("1\n00:00:00,000 --> 00:00:03,000\nŽivjo!\n\n", speechOutput);
        assertEquals(1, savedSpeech.size());
        List<TimedSpeechEngine.Window> timed = new ArrayList<>();
        TimedSpeechTranscriber fakeTimed = new TimedSpeechTranscriber("fake", body -> "{\"text\":\"Hello!\",\"segments\":[{\"start\":0.5,\"end\":1.4,\"text\":\"Hello!\",\"speaker\":\"A\"}]}");
        List<VideoTimeline.Interval> recognized = TimedSpeechEngine.scan(3000, timed, audio::readTimed,
            chunk -> fakeTimed.transcribe(chunk, () -> {}), windows -> { timed.clear(); timed.addAll(windows); }, () -> {});
        assertEquals(List.of(new VideoTimeline.Interval(500, 1400, "Hello!")), recognized);
        assertTrue(VideoTimeline.toSrt(recognized).contains("00:00:00,500 --> 00:00:01,400"));
    }
    @Test public void decodesSurroundEac3FromMkv() throws Exception {
        Context test = InstrumentationRegistry.getInstrumentation().getContext();
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File fixture = new File(app.getCacheDir(), "surround.mkv");
        try (InputStream in = test.getAssets().open("sample-51-eac3.mkv"); OutputStream out = new FileOutputStream(fixture)) { in.transferTo(out); }
        Uri uri = Uri.fromFile(fixture);
        android.media.MediaExtractor extractor = new android.media.MediaExtractor();
        boolean supported = false;
        try {
            extractor.setDataSource(app, uri, Collections.emptyMap());
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                android.media.MediaFormat format = extractor.getTrackFormat(i); String mime = format.getString(android.media.MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/") && new android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS).findDecoderForFormat(format) != null) supported = true;
            }
        } finally { extractor.release(); }
        org.junit.Assume.assumeTrue("Ta Android emulator ne podpira E-AC-3 v MKV", supported);
        SpeechTranscriptionEngine.Audio audio = new VideoAudioReader(app, uri).readTimed(0, 2000);
        byte[] wav = audio.wave(); java.nio.ByteBuffer header = java.nio.ByteBuffer.wrap(wav).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        assertEquals(1, header.getShort(22)); assertEquals(16, header.getShort(34));
        int nonzero = 0; for (int i = 44; i < wav.length; i++) if (wav[i] != 0) nonzero++;
        assertTrue("Decoded surround audio must contain sound", nonzero > 1000);
    }
    @Test public void readsScaledFrameFrom4kHevc() throws Exception {
        Context test = InstrumentationRegistry.getInstrumentation().getContext();
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File fixture = new File(app.getCacheDir(), "sample-4k.mp4");
        try (InputStream in = test.getAssets().open("sample-4k-hevc.mp4"); OutputStream out = new FileOutputStream(fixture)) { in.transferTo(out); }
        try (VideoFrameReader reader = new VideoFrameReader(app, Uri.fromFile(fixture))) {
            VideoFrameRecognizer.Frame frame = reader.frame(0);
            byte[] jpeg = Base64.decode(frame.jpegBase64(), Base64.NO_WRAP);
            android.graphics.Bitmap image = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length);
            assertNotNull(image); assertEquals(1920, image.getWidth()); assertEquals(1080, image.getHeight()); image.recycle();
        }
    }
    @Test public void decodesSurroundAacFromMkv() throws Exception {
        Context test = InstrumentationRegistry.getInstrumentation().getContext();
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File fixture = new File(app.getCacheDir(), "surround-aac.mkv");
        try (InputStream in = test.getAssets().open("sample-51-aac.mkv"); OutputStream out = new FileOutputStream(fixture)) { in.transferTo(out); }
        SpeechTranscriptionEngine.Audio audio = new VideoAudioReader(app, Uri.fromFile(fixture)).readTimed(0, 2000);
        byte[] wav = audio.wave(); java.nio.ByteBuffer header = java.nio.ByteBuffer.wrap(wav).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        assertEquals(1, header.getShort(22)); assertEquals(16, header.getShort(34));
        int nonzero = 0; for (int i = 44; i < wav.length; i++) if (wav[i] != 0) nonzero++;
        assertTrue("Decoded 5.1 audio must contain sound", nonzero > 1000);
    }
}

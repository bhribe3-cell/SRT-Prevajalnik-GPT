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
    }
}

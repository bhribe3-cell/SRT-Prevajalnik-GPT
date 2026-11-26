package si.srtprevajalnik.gpt;

import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class TargetLanguageTest {
    @Test public void oldQueuesDefaultToSlovenianAndRemainResumable() throws Exception {
        JobStore store = new JobStore(RuntimeEnvironment.getApplication());
        java.io.File index = new java.io.File(RuntimeEnvironment.getApplication().getFilesDir(), "jobs/index.json");
        JobStore.write(index, "[{\"id\":\"old\",\"name\":\"film.srt\",\"status\":\"Napaka\",\"done\":440,\"total\":1032}]");
        JobStore.Job job = store.load().get(0);
        assertEquals("sl", job.targetLanguage); assertEquals(440, job.done); assertEquals("film.sl.srt", job.outputName());
        store.save(List.of(job)); assertEquals("sl", store.load().get(0).targetLanguage);
    }
    @Test public void selectionPersistsAndNamesAndPromptsUseSameTarget() throws Exception {
        for (TargetLanguage lang : TargetLanguage.values()) {
            JobStore.Job job = new JobStore.Job("one", "film.srt"); job.targetLanguage = lang.code;
            JobStore store = new JobStore(RuntimeEnvironment.getApplication()); store.save(List.of(job));
            JobStore.Job loaded = store.load().get(0);
            assertEquals("film." + lang.code + ".srt", loaded.outputName());
            OpenAiTranslator translator = new OpenAiTranslator("fake", "gpt-4.1-mini", TargetLanguage.fromCode(loaded.targetLanguage), request -> {
                String instruction = request.getJSONArray("messages").getJSONObject(0).getString("content");
                assertTrue(instruction.contains("into natural " + lang.prompt));
                return "{\"translations\":{\"c0_l0\":\"Dober dan\"}}";
            });
            assertEquals("Dober dan", translator.translate(SrtDocument.parse("1\n00:00:01,000 --> 00:00:02,000\nHello\n").lines, () -> {}).get("c0_l0"));
        }
    }
    @Test public void invalidLanguageDoesNotSilentlyUseWrongCache() {
        assertThrows(IllegalArgumentException.class, () -> TargetLanguage.fromCode("xx"));
    }
}

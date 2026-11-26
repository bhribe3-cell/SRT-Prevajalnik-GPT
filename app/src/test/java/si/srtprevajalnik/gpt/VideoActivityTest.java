package si.srtprevajalnik.gpt;

import android.content.Intent;
import android.view.*;
import android.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class VideoActivityTest {
    private ActivityController<VideoActivity> controller;
    private VideoActivity activity;
    @Before public void setup() throws Exception {
        Intent intent = new Intent(RuntimeEnvironment.getApplication(), VideoActivity.class).putExtra("target", "hr");
        controller = Robolectric.buildActivity(VideoActivity.class, intent).setup(); activity = controller.get();
    }
    @After public void tearDown() { controller.pause().stop().destroy(); }
    private View findText(View view, String label) {
        if (view instanceof TextView text && label.contentEquals(text.getText())) return view;
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            View found = findText(group.getChildAt(i), label); if (found != null) return found;
        }
        return null;
    }
    private Spinner findSpinner(View view) {
        if (view instanceof Spinner spinner) return spinner;
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            Spinner found = findSpinner(group.getChildAt(i)); if (found != null) return found;
        }
        return null;
    }
    @Test public void videoTabsShowExpectedLanguageAndPersistModeOnRecreation() {
        View root = activity.getWindow().getDecorView();
        assertNotNull(findText(root, "Podnapisi v sliki filma"));
        assertEquals(TargetLanguage.CROATIAN, findSpinner(root).getSelectedItem());
        findText(root, "Govor").performClick(); root = activity.getWindow().getDecorView();
        assertNotNull(findText(root, "Govor iz filma")); assertNull(findSpinner(root));
        controller.recreate(); activity = controller.get(); root = activity.getWindow().getDecorView();
        assertNotNull(findText(root, "Govor iz filma"));
        findText(root, "Slika").performClick(); root = activity.getWindow().getDecorView();
        assertNotNull(findText(root, "Podnapisi v sliki filma"));
        assertEquals(TargetLanguage.CROATIAN, findSpinner(root).getSelectedItem());
    }
    @Test public void selectingVideoUsesPersistableAndroidDocumentPicker() {
        View root = activity.getWindow().getDecorView();
        findText(root, "Izberi film (do 20 GB)").performClick();
        Intent intent = shadowOf(activity).getNextStartedActivityForResult().intent;
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, intent.getAction());
        assertTrue((intent.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0);
        assertTrue((intent.getFlags() & Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) != 0);
    }
    private boolean containsText(View view, String fragment) {
        if (view instanceof TextView text && text.getText().toString().contains(fragment)) return true;
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) if (containsText(group.getChildAt(i), fragment)) return true;
        return false;
    }
    @Test public void explainsWhySpeechButtonsAreDisabledWhileImageTabIsRunning() throws Exception {
        VideoJobStore store = new VideoJobStore(RuntimeEnvironment.getApplication(), true);
        store.save(new VideoJobStore.Job("content://movie", "film.mkv", "sl", 10000, 100));
        VideoProcessingService.ACTIVE_SPEECH = false; VideoProcessingService.RUNNING.set(true);
        try {
            findText(activity.getWindow().getDecorView(), "Govor").performClick();
            View root = activity.getWindow().getDecorView();
            assertTrue(containsText(root, "Drug zavihek trenutno obdeluje film"));
            assertFalse(findText(root, "Začni / nadaljuj").isEnabled());
        } finally { VideoProcessingService.RUNNING.set(false); VideoProcessingService.ACTIVE_SPEECH = null; }
    }
}

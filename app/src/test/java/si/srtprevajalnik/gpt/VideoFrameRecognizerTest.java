package si.srtprevajalnik.gpt;

import org.json.*;
import org.junit.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class VideoFrameRecognizerTest {
    private final List<VideoFrameRecognizer.Frame> frames = List.of(new VideoFrameRecognizer.Frame(0, "YWJj"), new VideoFrameRecognizer.Frame(500, "ZGVm"));
    @Test public void requiresEveryFrameAndPreservesTimestampWhenRepliesAreReordered() throws Exception {
        VideoFrameRecognizer recognizer = new VideoFrameRecognizer("fake", "gpt-4.1-mini", request -> {
            JSONObject schema = request.getJSONObject("response_format").getJSONObject("json_schema").getJSONObject("schema")
                .getJSONObject("properties").getJSONObject("frames");
            assertFalse(schema.getBoolean("additionalProperties")); assertEquals(2, schema.getJSONArray("required").length());
            assertEquals(4, request.getJSONArray("messages").getJSONObject(1).getJSONArray("content").length());
            return "{\"frames\":{\"f1\":\"Hello!\",\"f0\":\"\"}}";
        });
        assertEquals(List.of(new VideoTimeline.Sample(0, ""), new VideoTimeline.Sample(500, "Hello!")), recognizer.recognize(frames, () -> {}));
    }
    @Test public void rejectsMissingExtraDuplicateAndNonStringFrameResults() {
        for (String reply : List.of("{\"frames\":{\"f0\":\"\"}}", "{\"frames\":{\"f0\":\"\",\"f2\":\"Hello\"}}",
            "{\"frames\":{\"f0\":\"\",\"f0\":\"oops\",\"f1\":\"Hello\"}}", "{\"frames\":{\"f0\":null,\"f1\":\"Hello\"}}"))
            assertThrows(JSONException.class, () -> VideoFrameRecognizer.parse(reply, frames));
    }
    @Test public void cancellationPreventsPaidRequest() {
        AtomicInteger count = new AtomicInteger();
        VideoFrameRecognizer recognizer = new VideoFrameRecognizer("fake", "gpt-4.1-mini", request -> { count.incrementAndGet(); return "{}"; });
        assertThrows(InterruptedException.class, () -> recognizer.recognize(frames, () -> { throw new InterruptedException(); }));
        assertEquals(0, count.get());
    }
}

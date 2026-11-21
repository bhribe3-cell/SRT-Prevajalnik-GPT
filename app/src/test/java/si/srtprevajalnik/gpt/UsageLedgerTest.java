package si.srtprevajalnik.gpt;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.util.Calendar;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class UsageLedgerTest {
    @Test public void usesStandardAndDiscountedCachedInputPrices() {
        // 750k regular input, 250k cached input, 500k output.
        assertEquals(1.125, UsageLedger.estimateUsd("gpt-4.1-mini", 1_000_000, 250_000, 500_000), 0.0000001);
        assertEquals(5.625, UsageLedger.estimateUsd("gpt-4.1", 1_000_000, 250_000, 500_000), 0.0000001);
        assertThrows(IllegalArgumentException.class, () -> UsageLedger.estimateUsd("gpt-4.1", 10, 11, 5));
    }

    @Test public void storesMultipleRequestsPerFileAcrossAppRestarts() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        UsageLedger ledger = new UsageLedger(context);
        double before = ledger.stats().totalUsd;
        ledger.record("file-a", "gpt-4.1-mini", 1000, 0, 1000);
        ledger.record("file-a", "gpt-4.1-mini", 1000, 0, 1000);
        ledger.record("file-b", "gpt-4.1", 1000, 500, 1000);
        UsageLedger.Stats stats = new UsageLedger(context).stats();
        assertEquals(before + .004 + .00925, stats.totalUsd, 0.000001);
        assertEquals(.004, stats.byJob.get("file-a"), 0.000001);
        assertEquals(.00925, stats.byJob.get("file-b"), 0.000001);
        assertEquals(.00925, stats.lastUsd, 0.000001);
    }

    @Test public void groupsDailyAndMonthlyTotalsUsingDeviceLocalTime() throws Exception {
        Calendar today = Calendar.getInstance(); today.set(2026, Calendar.NOVEMBER, 21, 15, 0);
        long now = today.getTimeInMillis();
        Calendar yesterday = (Calendar)today.clone(); yesterday.add(Calendar.DAY_OF_YEAR, -1);
        Calendar lastMonth = (Calendar)today.clone(); lastMonth.add(Calendar.MONTH, -1);
        JSONArray entries = new JSONArray()
            .put(new JSONObject().put("time", lastMonth.getTimeInMillis()).put("usd", 1.0).put("job", "file-a"))
            .put(new JSONObject().put("time", yesterday.getTimeInMillis()).put("usd", 2.0).put("job", "file-a"))
            .put(new JSONObject().put("time", now).put("usd", 3.0).put("job", "file-b"));
        UsageLedger.Stats summary = UsageLedger.calculate(entries, now);
        assertEquals(6.0, summary.totalUsd, .000001);
        assertEquals(5.0, summary.monthUsd, .000001);
        assertEquals(3.0, summary.todayUsd, .000001);
        assertEquals(3.0, summary.lastUsd, .000001);
        assertEquals(3.0, summary.byJob.get("file-a"), .000001);
    }
}

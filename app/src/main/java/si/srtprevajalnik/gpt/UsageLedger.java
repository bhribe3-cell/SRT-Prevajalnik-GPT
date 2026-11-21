package si.srtprevajalnik.gpt;

import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.util.Calendar;

/**
 * Local, best-effort cost estimates based on usage tokens returned by successful API responses.
 * Rates are in USD per million tokens. This is not the OpenAI billing ledger.
 */
final class UsageLedger {
    private final File file;
    UsageLedger(Context context) { file = new File(context.getFilesDir(), "api-usage.json"); }

    static double estimateUsd(String model, long input, long cached, long output) {
        if (input < 0 || output < 0 || cached < 0 || cached > input)
            throw new IllegalArgumentException("Neveljavni podatki o porabi žetonov.");
        // OpenAI published standard rates as of November 2026. Review if prices change.
        double standard = "gpt-4.1".equals(model) ? 2.00 : 0.40;
        double cache = "gpt-4.1".equals(model) ? 0.50 : 0.10;
        double generated = "gpt-4.1".equals(model) ? 8.00 : 1.60;
        return ((input - cached) * standard + cached * cache + output * generated) / 1_000_000.0;
    }

    synchronized void record(String jobId, String model, long input, long cached, long output) throws Exception {
        double usd = estimateUsd(model, input, cached, output);
        JSONArray entries = entries();
        entries.put(new JSONObject().put("time", System.currentTimeMillis()).put("job", jobId)
            .put("model", model).put("input", input).put("cached", cached)
            .put("output", output).put("usd", usd));
        JobStore.write(file, entries.toString());
    }

    synchronized Stats stats() throws Exception {
        return calculate(entries(), System.currentTimeMillis());
    }

    private JSONArray entries() throws Exception {
        if (!file.exists()) return new JSONArray();
        return new JSONArray(JobStore.read(new AtomicFile(file).openRead()));
    }

    static Stats calculate(JSONArray entries, long now) throws Exception {
        Calendar localNow = Calendar.getInstance(); localNow.setTimeInMillis(now);
        int year = localNow.get(Calendar.YEAR), month = localNow.get(Calendar.MONTH), day = localNow.get(Calendar.DAY_OF_YEAR);
        Stats totals = new Stats();
        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.getJSONObject(i);
            long time = entry.optLong("time", 0);
            // Ignore future-dated data if the device clock was set incorrectly.
            if (time > now || time <= 0) continue;
            double usd = entry.getDouble("usd");
            if (!Double.isFinite(usd) || usd < 0) continue;
            totals.totalUsd += usd;
            totals.byJob.merge(entry.optString("job", ""), usd, Double::sum);
            totals.lastUsd = usd; // entries are appended in order of receipt
            Calendar item = Calendar.getInstance(); item.setTimeInMillis(time);
            if (item.get(Calendar.YEAR) == year) {
                if (item.get(Calendar.MONTH) == month) totals.monthUsd += usd;
                if (item.get(Calendar.DAY_OF_YEAR) == day) totals.todayUsd += usd;
            }
        }
        return totals;
    }

    static final class Stats {
        double lastUsd, todayUsd, monthUsd, totalUsd;
        final java.util.Map<String, Double> byJob = new java.util.HashMap<>();
    }
}

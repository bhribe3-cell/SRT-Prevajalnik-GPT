package si.srtprevajalnik.gpt;

import android.content.Context;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class JobStore {
    static final int MAX_FILES = 25;
    static final class Job {
        String id, name, status = "Čaka", error = "";
        String targetLanguage = "sl";
        int done, total;
        Job(String id, String name) { this.id = id; this.name = name; }
        String outputName() { return name.replaceFirst("(?i)\\.srt$", "") + "." + TargetLanguage.fromCode(targetLanguage).code + ".srt"; }
    }
    private final File directory;
    JobStore(Context context) { directory = new File(context.getFilesDir(), "jobs"); directory.mkdirs(); }
    File source(Job job) { return new File(directory, job.id + ".source"); }
    File output(Job job) { return new File(directory, job.id + ".srt"); }
    File cache(Job job) { return new File(directory, job.id + ".json"); }
    synchronized List<Job> load() throws Exception {
        File index = new File(directory, "index.json"); if (!index.exists()) return new ArrayList<>();
        JSONArray array = new JSONArray(read(new AtomicFile(index).openRead())); List<Job> jobs = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.getJSONObject(i); Job job = new Job(object.getString("id"), object.getString("name"));
            job.status = object.getString("status"); job.error = object.optString("error");
            job.targetLanguage = object.optString("targetLanguage", "sl");
            TargetLanguage.fromCode(job.targetLanguage);
            job.done = object.optInt("done"); job.total = object.optInt("total"); jobs.add(job);
        }
        return jobs;
    }
    synchronized void save(List<Job> jobs) throws Exception {
        JSONArray array = new JSONArray();
        for (Job job : jobs) array.put(new JSONObject().put("id", job.id).put("name", job.name).put("status", job.status)
            .put("error", job.error).put("done", job.done).put("total", job.total).put("targetLanguage", job.targetLanguage));
        write(new File(directory, "index.json"), array.toString());
    }
    static void write(File file, String value) throws IOException {
        AtomicFile atomic = new AtomicFile(file); FileOutputStream out = null;
        try { out = atomic.startWrite(); out.write(value.getBytes(StandardCharsets.UTF_8)); atomic.finishWrite(out); }
        catch (IOException e) { if (out != null) atomic.failWrite(out); throw e; }
    }
    static String read(InputStream input) throws IOException {
        try (input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] bytes = new byte[8192]; int count;
            while ((count = input.read(bytes)) >= 0) {
                if (out.size() + count > 16 * 1024 * 1024) throw new IOException("Datoteka je prevelika.");
                out.write(bytes, 0, count);
            }
            return out.toString(StandardCharsets.UTF_8);
        }
    }
    static byte[] readSource(InputStream input) throws IOException {
        try (input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] bytes = new byte[8192]; int count;
            while ((count = input.read(bytes)) >= 0) {
                if (out.size() + count > 8 * 1024 * 1024) throw new IOException("SRT je prevelik (največ 8 MB).");
                out.write(bytes, 0, count);
            }
            return out.toByteArray();
        }
    }
    void clearOldFiles(List<Job> keep) {
        Set<String> ids = new HashSet<>(); for (Job job : keep) ids.add(job.id);
        File[] files = directory.listFiles(); if (files == null) return;
        for (File file : files) if (!file.getName().startsWith("index.") && !ids.contains(file.getName().split("\\.")[0])) file.delete();
    }
}

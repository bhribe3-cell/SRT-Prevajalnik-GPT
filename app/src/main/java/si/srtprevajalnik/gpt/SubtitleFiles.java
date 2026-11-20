package si.srtprevajalnik.gpt;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

final class SubtitleFiles {
    static void validateCount(int count) {
        if (count < 1 || count > 25) throw new IllegalArgumentException("Izberi od 1 do 25 datotek SRT. Izbor ni bil spremenjen.");
    }
    static String safeName(String name) {
        String safe = name == null ? "podnapisi.srt" : name.replaceAll("[\\\\/\\p{Cntrl}]", "_").strip();
        return safe.isEmpty() ? "podnapisi.srt" : safe;
    }
    static List<String> uniqueNames(List<String> names) {
        Set<String> used = new HashSet<>(); List<String> result = new ArrayList<>();
        for (String original : names) {
            String name = safeName(original), candidate = name; int n = 2;
            while (!used.add(candidate.toLowerCase(Locale.ROOT))) {
                int dot = name.lastIndexOf('.');
                candidate = (dot > 0 ? name.substring(0, dot) : name) + " (" + n++ + ")" + (dot > 0 ? name.substring(dot) : "");
            }
            result.add(candidate);
        }
        return result;
    }
    static void zip(OutputStream stream, List<String> names, List<File> files) throws IOException {
        List<String> unique = uniqueNames(names);
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(stream), StandardCharsets.UTF_8)) {
            for (int i = 0; i < files.size(); i++) {
                zip.putNextEntry(new ZipEntry(unique.get(i)));
                try (InputStream input = new FileInputStream(files.get(i))) { input.transferTo(zip); }
                zip.closeEntry();
            }
        }
    }
}

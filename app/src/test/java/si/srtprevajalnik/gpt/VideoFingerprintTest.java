package si.srtprevajalnik.gpt;

import org.junit.Test;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.Assert.*;

public class VideoFingerprintTest {
    @Test public void detectsReplacementOfSameSizeAtStartAndEndWithoutReadingWholeFile() throws Exception {
        File file = File.createTempFile("movie", ".bin");
        try {
            byte[] bytes = new byte[200_000]; Arrays.fill(bytes, (byte)7); Files.write(file.toPath(), bytes);
            String original; try (FileInputStream in = new FileInputStream(file)) { original = VideoFingerprint.read(in.getChannel()); }
            try (RandomAccessFile rw = new RandomAccessFile(file, "rw")) { rw.seek(199_999); rw.write(4); }
            String changed; try (FileInputStream in = new FileInputStream(file)) { changed = VideoFingerprint.read(in.getChannel()); }
            assertNotEquals(original, changed);
            try (RandomAccessFile rw = new RandomAccessFile(file, "rw")) { rw.seek(199_999); rw.write(7); rw.seek(0); rw.write(4); }
            try (FileInputStream in = new FileInputStream(file)) { assertNotEquals(original, VideoFingerprint.read(in.getChannel())); }
        } finally { file.delete(); }
    }
    @Test public void twentyGigabyteSparseFileIsFingerprintableWithoutAllocatingItInMemory() throws Exception {
        File file = File.createTempFile("movie-20gb", ".bin");
        try {
            try (RandomAccessFile rw = new RandomAccessFile(file, "rw")) { rw.setLength(20_000_000_000L); rw.seek(19_999_999_999L); rw.write(7); }
            try (FileInputStream input = new FileInputStream(file)) { assertEquals(64, VideoFingerprint.read(input.getChannel()).length()); }
            try (RandomAccessFile rw = new RandomAccessFile(file, "rw")) { rw.setLength(20_000_000_001L); }
            try (FileInputStream input = new FileInputStream(file)) { assertThrows(IllegalArgumentException.class, () -> VideoFingerprint.read(input.getChannel())); }
        } finally { file.delete(); }
    }
}

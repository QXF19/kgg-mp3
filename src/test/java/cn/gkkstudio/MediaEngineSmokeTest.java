package cn.gkkstudio;

import java.nio.file.*;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class MediaEngineSmokeTest {
    public static void main(String[] args) throws Exception {
        SystemCapabilities.Toolchain tools = SystemCapabilities.detect();
        if (!tools.ready() || !tools.hasProbe()) throw new AssertionError("FFmpeg toolchain not found");

        Path testDirectory = Files.createTempDirectory("gkk-studio-smoke-");
        try {
            Path flac = testDirectory.resolve("test-tone.flac");
            Process generate = new ProcessBuilder(tools.ffmpeg().toString(), "-hide_banner", "-loglevel", "error", "-y",
                "-f", "lavfi", "-i", "sine=frequency=1000:duration=1", "-c:a", "flac", flac.toString()).start();
            if (!generate.waitFor(20, TimeUnit.SECONDS) || generate.exitValue() != 0) throw new AssertionError("Could not generate test audio");

            Path encrypted = testDirectory.resolve("sample.kgg");
            KgmDecoder.createEncryptedFixture(flac, encrypted);
            if (!KgmDecoder.supports(encrypted)) throw new AssertionError("Generated KGM fixture was not detected");
            AudioItem item = new AudioItem(encrypted);
            CountDownLatch finished = new CountDownLatch(1);
            try (MediaEngine engine = new MediaEngine(tools)) {
                MediaEngine.Settings settings = new MediaEngine.Settings(testDirectory, false, MediaEngine.Quality.V0, 1);
                engine.convertAll(List.of(item), settings, ignored -> {}, finished::countDown);
                if (!finished.await(30, TimeUnit.SECONDS)) throw new AssertionError("Conversion timed out");
            }
            if (item.state != AudioItem.State.DONE) throw new AssertionError("Conversion failed: " + item.detail);
            if (item.output == null || !Files.isRegularFile(item.output) || Files.size(item.output) == 0) throw new AssertionError("MP3 output missing");

            Process verify = new ProcessBuilder(tools.ffprobe().toString(), "-v", "error", "-select_streams", "a:0",
                "-show_entries", "stream=codec_name", "-of", "default=nw=1:nk=1", item.output.toString()).redirectErrorStream(true).start();
            String codec = new String(verify.getInputStream().readAllBytes()).trim();
            if (!verify.waitFor(10, TimeUnit.SECONDS) || verify.exitValue() != 0 || !"mp3".equals(codec)) {
                throw new AssertionError("Output is not a valid MP3: " + codec);
            }
            System.out.println("PASS: encrypted KGM/KGG -> valid MP3 (" + Files.size(item.output) + " bytes)");
        } finally {
            try (var files = Files.walk(testDirectory)) {
                files.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(path -> {
                    try { Files.deleteIfExists(path); } catch (Exception ignored) {}
                });
            }
        }
    }
}

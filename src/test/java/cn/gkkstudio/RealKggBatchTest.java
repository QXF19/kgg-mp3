package cn.gkkstudio;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Operator-selected local acceptance test. Never bundled with private audio. */
public final class RealKggBatchTest {
    public static void main(String[] args) throws Exception {
        Path source = Path.of(args[0]), output = Path.of(args[1]);
        List<AudioItem> items;
        try (var files = Files.walk(source)) {
            items = files.filter(Files::isRegularFile)
                .filter(p -> Set.of("kgg", "gkk").contains(MediaEngine.extension(p)))
                .sorted().map(AudioItem::new).toList();
        }
        System.out.println("FOUND=" + items.size());
        if (items.isEmpty()) throw new AssertionError("No KGG samples");
        SystemCapabilities.Toolchain tools = SystemCapabilities.detect();
        CountDownLatch finished = new CountDownLatch(1);
        try (MediaEngine engine = new MediaEngine(tools)) {
            engine.convertAll(items, new MediaEngine.Settings(output, false, MediaEngine.Quality.V0, 2),
                item -> { if (item.state == AudioItem.State.DONE || item.state == AudioItem.State.FAILED)
                    System.out.println(item.state + " " + item.source.getFileName() + " " + item.detail); },
                finished::countDown);
            if (!finished.await(30, TimeUnit.MINUTES)) { engine.cancel(items); throw new AssertionError("Batch timed out"); }
        }
        int passed = 0;
        for (AudioItem item : items) {
            if (item.state != AudioItem.State.DONE) continue;
            Process process = new ProcessBuilder(tools.ffmpeg().toString(), "-v", "error", "-xerror",
                "-i", item.output.toString(), "-map", "0:a:0", "-f", "null", "-").redirectErrorStream(true).start();
            String error = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            if (!process.waitFor(60, TimeUnit.SECONDS) || process.exitValue() != 0) {
                process.destroyForcibly(); System.out.println("DECODE_FAIL " + item.source.getFileName() + " " + error);
            } else { passed++; System.out.println("VERIFIED " + item.output); }
        }
        System.out.println("RESULT=" + passed + "/" + items.size());
        if (passed != items.size()) System.exit(1);
    }
}

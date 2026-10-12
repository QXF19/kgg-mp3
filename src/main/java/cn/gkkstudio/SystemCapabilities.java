package cn.gkkstudio;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

final class SystemCapabilities {
    record Toolchain(Path ffmpeg, Path ffprobe, Path sqlite3, String gpuName) {
        boolean ready() { return ffmpeg != null; }
        boolean hasProbe() { return ffprobe != null; }
        boolean hasSqlite() { return sqlite3 != null; }
        boolean hasGpu() { return gpuName != null && !gpuName.isBlank(); }
    }

    private SystemCapabilities() {}

    static Toolchain detect() {
        Path ffmpeg = findExecutable("ffmpeg.exe", "ffmpeg");
        Path ffprobe = findExecutable("ffprobe.exe", "ffprobe");
        Path sqlite3 = findExecutable("sqlite3.exe", "sqlite3");
        return new Toolchain(ffmpeg, ffprobe, sqlite3, detectGpu());
    }

    private static Path findExecutable(String windowsName, String commandName) {
        List<Path> candidates = new ArrayList<>();
        Path cwd = Path.of("").toAbsolutePath();
        candidates.add(cwd.resolve("tools").resolve(windowsName));
        candidates.add(cwd.resolve(windowsName));
        Path app = applicationDirectory();
        candidates.add(app.resolve("tools").resolve(windowsName));
        candidates.add(app.resolve(windowsName));
        String configured = System.getenv("FFMPEG_HOME");
        if (configured != null && !configured.isBlank()) {
            candidates.add(Path.of(configured).resolve("bin").resolve(windowsName));
            candidates.add(Path.of(configured).resolve(windowsName));
        }
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) return candidate.toAbsolutePath().normalize();
        }
        try {
            Process process = new ProcessBuilder(commandName, "-version").redirectErrorStream(true).start();
            if (process.waitFor(2, TimeUnit.SECONDS) && process.exitValue() == 0) {
                return Path.of(commandName);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static Path applicationDirectory() {
        try {
            return Path.of(SystemCapabilities.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                .toAbsolutePath().normalize().getParent();
        } catch (Exception ignored) {
            return Path.of("").toAbsolutePath();
        }
    }

    private static String detectGpu() {
        String nvidia = firstLine(List.of("nvidia-smi", "--query-gpu=name", "--format=csv,noheader"));
        if (nvidia != null) return nvidia;
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            String value = firstLine(List.of("powershell", "-NoProfile", "-Command",
                "Get-CimInstance Win32_VideoController | Where-Object {$_.Name -notmatch 'Microsoft Basic'} | Select-Object -First 1 -ExpandProperty Name"));
            if (value != null) return value;
        }
        return null;
    }

    private static String firstLine(List<String> command) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String line;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                line = reader.readLine();
            }
            if (process.waitFor(3, TimeUnit.SECONDS) && process.exitValue() == 0 && line != null && !line.isBlank()) {
                return line.trim();
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}

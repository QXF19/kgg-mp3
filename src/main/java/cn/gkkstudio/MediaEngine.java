package cn.gkkstudio;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

final class MediaEngine implements AutoCloseable {
    enum Quality {
        V0("极致 VBR · 约 245 kbps", List.of("-q:a", "0")),
        CBR_320("最高兼容 · 320 kbps", List.of("-b:a", "320k")),
        V2("高品质 VBR · 约 190 kbps", List.of("-q:a", "2")),
        CBR_256("高品质 · 256 kbps", List.of("-b:a", "256k")),
        CBR_192("标准 · 192 kbps", List.of("-b:a", "192k"));

        final String label;
        final List<String> arguments;
        Quality(String label, List<String> arguments) { this.label = label; this.arguments = arguments; }
        @Override public String toString() { return label; }
    }

    record Settings(Path outputDirectory, boolean sourceDirectory, Quality quality, int concurrency) {}

    private final SystemCapabilities.Toolchain tools;
    private ExecutorService executor;
    private volatile boolean cancelling;

    MediaEngine(SystemCapabilities.Toolchain tools) {
        this.tools = tools;
    }

    void convertAll(List<AudioItem> items, Settings settings, Consumer<AudioItem> changed, Runnable completed) {
        cancelling = false;
        executor = Executors.newFixedThreadPool(Math.max(1, settings.concurrency()), r -> {
            Thread thread = new Thread(r, "gkk-converter-worker");
            thread.setDaemon(true);
            return thread;
        });
        CompletableFuture<?>[] futures = items.stream()
            .filter(item -> item.state == AudioItem.State.WAITING || item.state == AudioItem.State.FAILED || item.state == AudioItem.State.CANCELLED)
            .map(item -> CompletableFuture.runAsync(() -> convertOne(item, settings, changed), executor))
            .toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(futures).whenComplete((unused, error) -> {
            if (executor != null) executor.shutdown();
            completed.run();
        });
    }

    void cancel(List<AudioItem> items) {
        cancelling = true;
        for (AudioItem item : items) {
            Process process = item.process;
            if (process != null && process.isAlive()) process.destroyForcibly();
            if (item.state == AudioItem.State.WAITING) {
                item.state = AudioItem.State.CANCELLED;
                item.detail = "已取消";
            }
        }
        if (executor != null) executor.shutdownNow();
    }

    private void convertOne(AudioItem item, Settings settings, Consumer<AudioItem> changed) {
        Path prepared = null;
        Path part = null;
        try {
            if (cancelling) throw new CancellationException();
            item.state = AudioItem.State.ANALYZING;
            item.progress = 2;
            item.detail = "检查音频流";
            changed.accept(item);

            Path outputDir = settings.sourceDirectory() ? item.source.getParent() : settings.outputDirectory();
            if (outputDir == null) throw new IOException("请选择输出文件夹");
            Files.createDirectories(outputDir);
            Path output = uniqueOutput(outputDir, baseName(item.source.getFileName().toString()) + ".mp3");
            item.output = output;

            PreparedInput input = prepareInput(item.source);
            prepared = input.temporary() ? input.path() : null;
            ProbeInfo probe = probe(input.path());
            if (cancelling) throw new CancellationException();

            item.state = AudioItem.State.CONVERTING;
            item.progress = 5;
            item.detail = input.description();
            changed.accept(item);

            part = output.resolveSibling(baseName(output.getFileName().toString()) + ".part.mp3");
            Files.deleteIfExists(part);
            List<String> command = new ArrayList<>();
            command.add(tools.ffmpeg().toString());
            Collections.addAll(command, "-hide_banner", "-loglevel", "error", "-nostdin", "-i", input.path().toString(),
                "-map", "0:a:0", "-vn", "-map_metadata", "0");
            boolean preserveOriginalMp3 = "mp3".equalsIgnoreCase(probe.codec());
            if (preserveOriginalMp3) {
                Collections.addAll(command, "-c:a", "copy");
                item.detail = input.description() + " · 无损提取原始 MP3";
            } else {
                Collections.addAll(command, "-c:a", "libmp3lame");
                command.addAll(settings.quality().arguments);
            }
            Collections.addAll(command, "-id3v2_version", "3", "-write_id3v1", "1", "-progress", "pipe:1", "-nostats", part.toString());

            Process process = new ProcessBuilder(command).start();
            item.process = process;
            StringBuilder errors = new StringBuilder();
            Thread errorReader = new Thread(() -> readErrors(process.getErrorStream(), errors), "ffmpeg-errors");
            errorReader.setDaemon(true);
            errorReader.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("out_time_us=") && probe.duration() > 0) {
                        long micros = parseLong(line.substring(12));
                        item.progress = Math.max(5, Math.min(99, (int) (micros / (probe.duration() * 1_000_000d) * 100)));
                        item.detail = (preserveOriginalMp3 ? "无损提取中 · " : "高品质编码中 · ") + item.progress + "%";
                        changed.accept(item);
                    }
                }
            }
            int exit = process.waitFor();
            errorReader.join(1000);
            item.process = null;
            if (cancelling) throw new CancellationException();
            if (exit != 0 || !Files.isRegularFile(part) || Files.size(part) == 0) {
                String reason = simplifyError(errors.toString());
                throw new IOException(reason.isBlank() ? "FFmpeg 无法读取该音频流" : reason);
            }
            Files.move(part, output, StandardCopyOption.ATOMIC_MOVE);
            item.state = AudioItem.State.DONE;
            item.progress = 100;
            item.detail = "已保存 · " + humanSize(Files.size(output));
        } catch (CancellationException | InterruptedException e) {
            Thread.currentThread().interrupt();
            item.state = AudioItem.State.CANCELLED;
            item.detail = "已取消";
        } catch (Exception e) {
            item.state = AudioItem.State.FAILED;
            item.detail = friendlyMessage(item.source, e);
        } finally {
            item.process = null;
            deleteQuietly(part);
            deleteQuietly(prepared);
            changed.accept(item);
        }
    }

    private PreparedInput prepareInput(Path source) throws IOException {
        String ext = extension(source);
        if (!Set.of("gkk", "kgg", "kgm", "kgma", "kge", "kgtemp").contains(ext)) {
            return new PreparedInput(source, false, "读取源音频");
        }
        if (KggV5Decoder.supports(source)) {
            Path decoded = KggV5Decoder.decodeToTemporaryFile(source, tools);
            return new PreparedInput(decoded, true, "已解密新版 KGG v5 音频");
        }
        if (KgmDecoder.supports(source)) {
            Path decoded = KgmDecoder.decodeToTemporaryFile(source);
            return new PreparedInput(decoded, true, "已解密旧版 KGM/KGG 音频");
        }
        HeaderMatch match = findAudioHeader(source);
        if (match != null && match.offset == 0) return new PreparedInput(source, false, "已识别 " + match.format + " 音频流");
        if (match != null) {
            Path temp = Files.createTempFile("gkk-audio-", "." + match.extension);
            try (InputStream input = Files.newInputStream(source); OutputStream output = Files.newOutputStream(temp)) {
                input.skipNBytes(match.offset);
                input.transferTo(output);
            }
            return new PreparedInput(temp, true, "已提取 " + match.format + " 音频流");
        }
        return new PreparedInput(source, false, "尝试读取音频流");
    }

    private HeaderMatch findAudioHeader(Path source) throws IOException {
        int limit = (int) Math.min(Files.size(source), 8L * 1024 * 1024);
        byte[] data = new byte[limit];
        try (InputStream input = Files.newInputStream(source)) {
            int position = 0;
            while (position < limit) {
                int read = input.read(data, position, limit - position);
                if (read < 0) break;
                position += read;
            }
        }
        for (int i = 0; i + 12 < data.length; i++) {
            if (matches(data, i, "ID3")) return new HeaderMatch(i, "MP3", "mp3");
            if (matches(data, i, "fLaC")) return new HeaderMatch(i, "FLAC", "flac");
            if (matches(data, i, "OggS")) return new HeaderMatch(i, "OGG", "ogg");
            if (matches(data, i, "RIFF") && matches(data, i + 8, "WAVE")) return new HeaderMatch(i, "WAV", "wav");
            if (i < 4096 && (data[i] & 0xFF) == 0xFF && ((data[i + 1] & 0xE6) == 0xE2)) {
                return new HeaderMatch(i, "MP3", "mp3");
            }
        }
        return null;
    }

    private ProbeInfo probe(Path input) {
        if (!tools.hasProbe()) return new ProbeInfo(-1, "");
        try {
            Process process = new ProcessBuilder(tools.ffprobe().toString(), "-v", "error", "-select_streams", "a:0",
                "-show_entries", "stream=codec_name:format=duration", "-of", "default=noprint_wrappers=1",
                input.toString()).redirectErrorStream(true).start();
            double duration = -1;
            String codec = "";
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("codec_name=")) codec = line.substring("codec_name=".length()).trim();
                    if (line.startsWith("duration=")) {
                        try { duration = Double.parseDouble(line.substring("duration=".length()).trim()); }
                        catch (NumberFormatException ignored) {}
                    }
                }
            }
            if (process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0) return new ProbeInfo(duration, codec);
        } catch (Exception ignored) {
        }
        return new ProbeInfo(-1, "");
    }

    private static Path uniqueOutput(Path directory, String fileName) {
        Path candidate = directory.resolve(fileName);
        String base = baseName(fileName);
        int index = 2;
        while (Files.exists(candidate)) candidate = directory.resolve(base + " (" + index++ + ").mp3");
        return candidate;
    }

    private static void readErrors(InputStream stream, StringBuilder target) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null && target.length() < 6000) target.append(line).append('\n');
        } catch (IOException ignored) {
        }
    }

    private static String simplifyError(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("invalid data found") || lower.contains("could not find codec parameters"))
            return "未找到可解码的音频流，文件可能受保护或格式尚不支持";
        if (lower.contains("permission denied")) return "没有文件写入权限";
        if (lower.contains("no space left")) return "磁盘空间不足";
        if (lower.contains("unknown encoder 'libmp3lame'")) return "当前 FFmpeg 未包含 LAME MP3 编码器";
        String[] lines = text.strip().split("\\R");
        return lines.length == 0 ? "" : lines[lines.length - 1].strip();
    }

    private static String friendlyMessage(Path source, Exception error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) message = error.getClass().getSimpleName();
        if (Set.of("gkk", "kgg", "kgm", "kgma", "kge", "kgtemp").contains(extension(source)) &&
            (message.contains("受保护") || message.contains("Invalid data"))) {
            return "未检测到标准音频流；请确认文件可由官方客户端正常播放并拥有使用权";
        }
        return message.length() > 150 ? message.substring(0, 147) + "…" : message;
    }

    private static boolean matches(byte[] data, int offset, String value) {
        if (offset < 0 || offset + value.length() > data.length) return false;
        for (int i = 0; i < value.length(); i++) if (data[offset + i] != (byte) value.charAt(i)) return false;
        return true;
    }

    static String extension(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    static String baseName(String name) {
        int dot = name.lastIndexOf('.');
        return dot <= 0 ? name : name.substring(0, dot);
    }

    private static long parseLong(String value) {
        try { return Long.parseLong(value.trim()); } catch (NumberFormatException ignored) { return 0; }
    }

    static String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.ROOT, "%.1f KB", bytes / 1024d);
        return String.format(Locale.ROOT, "%.1f MB", bytes / 1024d / 1024d);
    }

    private static void deleteQuietly(Path file) {
        if (file == null) return;
        try { Files.deleteIfExists(file); } catch (IOException ignored) {}
    }

    @Override public void close() {
        if (executor != null) executor.shutdownNow();
    }

    private record PreparedInput(Path path, boolean temporary, String description) {}
    private record HeaderMatch(long offset, String format, String extension) {}
    private record ProbeInfo(double duration, String codec) {}
}

package cn.gkkstudio;

import javax.swing.*;
import java.awt.*;
import java.nio.file.*;
import java.util.List;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class GkkMp3App {
    private GkkMp3App() {}

    public static void main(String[] args) {
        System.setProperty("file.encoding", "UTF-8");
        if (args.length > 0 && "--diagnose".equals(args[0])) {
            SystemCapabilities.Toolchain tools = SystemCapabilities.detect();
            System.out.println("ffmpeg=" + (tools.ffmpeg() == null ? "NOT_FOUND" : tools.ffmpeg()));
            System.out.println("ffprobe=" + (tools.ffprobe() == null ? "NOT_FOUND" : tools.ffprobe()));
            System.out.println("sqlite3=" + (tools.sqlite3() == null ? "NOT_FOUND" : tools.sqlite3()));
            System.out.println("gpu=" + (tools.gpuName() == null ? "NOT_FOUND" : tools.gpuName()));
            System.exit(tools.ready() ? 0 : 2);
        }
        if (Boolean.getBoolean("gkk.cli") || Arrays.asList(args).contains("--cli")) {
            String[] cliArguments = Arrays.stream(args).filter(value -> !"--cli".equals(value)).toArray(String[]::new);
            System.exit(runCommandLine(cliArguments));
        }
        SwingUtilities.invokeLater(() -> {
            installLookAndFeel();
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
        });
    }

    private static int runCommandLine(String[] args) {
        if (args.length < 1) {
            System.err.println("用法: kgg-mp3-cli.exe <输入音频> [输出文件夹]");
            return 64;
        }
        Path source;
        Path outputDirectory;
        try {
            source = Path.of(args[0]).toAbsolutePath().normalize();
            outputDirectory = args.length >= 2
                ? Path.of(args[1]).toAbsolutePath().normalize()
                : source.getParent();
        } catch (InvalidPathException error) {
            System.err.println("路径无效: " + error.getInput());
            return 64;
        }
        if (!Files.isRegularFile(source)) {
            System.err.println("输入文件不存在: " + source);
            return 66;
        }

        SystemCapabilities.Toolchain tools = SystemCapabilities.detect();
        if (!tools.ready()) {
            System.err.println("未找到 FFmpeg，请将 ffmpeg.exe 和 ffprobe.exe 放入 tools 文件夹。");
            return 69;
        }

        AudioItem item = new AudioItem(source);
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<String> lastDetail = new AtomicReference<>("");
        try (MediaEngine engine = new MediaEngine(tools)) {
            MediaEngine.Settings settings = new MediaEngine.Settings(
                outputDirectory, false, MediaEngine.Quality.V0, 1);
            engine.convertAll(List.of(item), settings, changed -> {
                String message = changed.stateText() + " · " + changed.detail;
                if (!message.equals(lastDetail.getAndSet(message))) System.out.println(message);
            }, completed::countDown);
            if (!completed.await(30, TimeUnit.MINUTES)) {
                engine.cancel(List.of(item));
                System.err.println("转换超时");
                return 70;
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            System.err.println("转换被中断");
            return 130;
        }
        if (item.state != AudioItem.State.DONE) {
            System.err.println("转换失败: " + item.detail);
            return 1;
        }
        System.out.println("输出文件: " + item.output);
        return 0;
    }

    private static void installLookAndFeel() {
        UIManager.put("Component.arc", 12);
        UIManager.put("Button.arc", 14);
        UIManager.put("TextComponent.arc", 12);
        UIManager.put("Component.focusWidth", 2);
        UIManager.put("Component.focusColor", new Color(0x007AFF));
        UIManager.put("Button.default.background", new Color(0x007AFF));
        UIManager.put("Button.default.foreground", Color.WHITE);
        Font font = new Font("Microsoft YaHei UI", Font.PLAIN, 14);
        UIManager.put("defaultFont", font);
        com.formdev.flatlaf.themes.FlatMacLightLaf.setup();
        UIManager.put("defaultFont", font);
        UIManager.put("Button.font", font);
        UIManager.put("Label.font", font);
        UIManager.put("ComboBox.font", font);
        UIManager.put("Table.font", font);
        UIManager.put("TableHeader.font", font.deriveFont(Font.BOLD));
        UIManager.put("TextField.font", font);
        UIManager.put("Spinner.font", font);
        UIManager.put("OptionPane.font", font);
        UIManager.put("OptionPane.messageFont", font);
        UIManager.put("OptionPane.buttonFont", font);
    }
}

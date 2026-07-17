package cn.gkkstudio;

import javax.swing.*;
import java.awt.*;

public final class GkkMp3App {
    private GkkMp3App() {}

    public static void main(String[] args) {
        System.setProperty("file.encoding", "UTF-8");
        if (args.length > 0 && "--diagnose".equals(args[0])) {
            SystemCapabilities.Toolchain tools = SystemCapabilities.detect();
            System.out.println("ffmpeg=" + (tools.ffmpeg() == null ? "NOT_FOUND" : tools.ffmpeg()));
            System.out.println("ffprobe=" + (tools.ffprobe() == null ? "NOT_FOUND" : tools.ffprobe()));
            System.out.println("gpu=" + (tools.gpuName() == null ? "NOT_FOUND" : tools.gpuName()));
            System.exit(tools.ready() ? 0 : 2);
        }
        SwingUtilities.invokeLater(() -> {
            installLookAndFeel();
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
        });
    }

    private static void installLookAndFeel() {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception ignored) {
        }
        Font font = new Font("Microsoft YaHei UI", Font.PLAIN, 13);
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

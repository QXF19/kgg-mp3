package cn.gkkstudio;

import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.List;

public final class UiPreview {
    public static void main(String[] args) throws Exception {
        Method look = GkkMp3App.class.getDeclaredMethod("installLookAndFeel");
        look.setAccessible(true); look.invoke(null);
        SwingUtilities.invokeAndWait(() -> {
            MainFrame frame = new MainFrame();
            try {
                Path directory = Path.of(args[0]); Files.createDirectories(directory);
                for (int width : new int[]{1200, 1080}) {
                    frame.setSize(width, 820); frame.addNotify(); frame.validate();
                    capture(frame, directory.resolve("desktop-" + width + ".png"));
                }
                frame.setSize(1080, 740); frame.validate();
                capture(frame, directory.resolve("minimum-window.png"));
                if (args.length > 1) {
                    Method add = MainFrame.class.getDeclaredMethod("addPaths", List.class);
                    add.setAccessible(true); add.invoke(frame, List.of(Path.of(args[1])));
                    frame.setSize(1200, 820); frame.validate();
                    capture(frame, directory.resolve("queue.png"));
                }
            } catch (Exception error) { throw new RuntimeException(error); }
            finally { frame.dispose(); }
        });
        System.exit(0);
    }

    private static void capture(MainFrame frame, Path output) throws Exception {
        Component content = frame.getContentPane();
        BufferedImage image = new BufferedImage(content.getWidth(), content.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics(); content.printAll(g); g.dispose();
        ImageIO.write(image, "png", output.toFile());
    }
}

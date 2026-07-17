package cn.gkkstudio;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

final class MainFrame extends JFrame {
    private static final Color BG = new Color(0xF4F6FA);
    private static final Color CARD = Color.WHITE;
    private static final Color TEXT = new Color(0x172033);
    private static final Color MUTED = new Color(0x778198);
    private static final Color PRIMARY = new Color(0x6C55F5);
    private static final Color PRIMARY_DARK = new Color(0x5842DE);
    private static final Color SUCCESS = new Color(0x22A06B);
    private static final Set<String> SUPPORTED = Set.of(
        "gkk", "kgg", "kgm", "kgma", "kge", "kgtemp", "mp3", "flac", "wav", "ogg", "m4a", "aac", "wma", "ape", "opus"
    );

    private final List<AudioItem> items = new CopyOnWriteArrayList<>();
    private final QueueTableModel tableModel = new QueueTableModel();
    private final JTable table = new JTable(tableModel);
    private final JLabel countLabel = new JLabel("0 个文件");
    private final JLabel summaryLabel = new JLabel("拖入文件即可开始");
    private final JLabel engineLabel = new JLabel("正在检测转换引擎…");
    private final JButton startButton = primaryButton("开始转换");
    private final JButton cancelButton = secondaryButton("取消");
    private final JButton clearButton = secondaryButton("清空列表");
    private final JTextField outputField = new JTextField();
    private final JCheckBox sourceDirectory = new JCheckBox("保存到源文件夹");
    private final JComboBox<MediaEngine.Quality> qualityBox = new JComboBox<>(MediaEngine.Quality.values());
    private final JSpinner concurrencySpinner = new JSpinner(new SpinnerNumberModel(Math.min(4, Runtime.getRuntime().availableProcessors()), 1, 8, 1));
    private final SystemCapabilities.Toolchain tools;
    private final MediaEngine engine;
    private boolean running;

    MainFrame() {
        super("KGG MP3 · 高品质音频转换");
        setIconImages(createAppIcons());
        tools = SystemCapabilities.detect();
        engine = new MediaEngine(tools);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(980, 700));
        setSize(1120, 780);
        setLocationRelativeTo(null);
        setContentPane(buildRoot());
        configureActions();
        updateCapabilities();
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                if (running) {
                    int result = JOptionPane.showConfirmDialog(MainFrame.this, "转换仍在进行，确定要退出吗？", "退出确认", JOptionPane.YES_NO_OPTION);
                    if (result != JOptionPane.YES_OPTION) return;
                    engine.cancel(items);
                }
                engine.close();
                dispose();
            }
        });
    }

    private JComponent buildRoot() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG);
        root.add(buildSidebar(), BorderLayout.WEST);
        root.add(buildContent(), BorderLayout.CENTER);
        return root;
    }

    private JComponent buildSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setBackground(new Color(0x191827));
        sidebar.setPreferredSize(new Dimension(220, 0));
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBorder(new EmptyBorder(28, 20, 24, 20));

        JLabel brand = new JLabel("◉  KGG MP3");
        brand.setForeground(Color.WHITE);
        brand.setFont(brand.getFont().deriveFont(Font.BOLD, 20f));
        brand.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(brand);
        JLabel version = new JLabel("Audio Converter 1.0");
        version.setForeground(new Color(0x85839A));
        version.setBorder(new EmptyBorder(4, 29, 30, 0));
        version.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(version);
        sidebar.add(navItem("⌁", "音频转换", true));
        sidebar.add(Box.createVerticalStrut(8));
        sidebar.add(navItem("⚡", "性能状态", false));
        sidebar.add(Box.createVerticalGlue());

        JPanel info = new RoundedPanel(new Color(0x242238), 18);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.setBorder(new EmptyBorder(15, 15, 15, 15));
        info.setMaximumSize(new Dimension(180, 125));
        info.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel safe = new JLabel("本地安全转换");
        safe.setForeground(Color.WHITE);
        safe.setFont(safe.getFont().deriveFont(Font.BOLD));
        JLabel safe2 = new JLabel("文件不会上传网络");
        safe2.setForeground(new Color(0xA9A6BC));
        safe2.setFont(safe2.getFont().deriveFont(12f));
        JLabel safe3 = new JLabel("源文件永不覆盖");
        safe3.setForeground(new Color(0xA9A6BC));
        safe3.setFont(safe3.getFont().deriveFont(12f));
        info.add(safe); info.add(Box.createVerticalStrut(8)); info.add(safe2); info.add(Box.createVerticalStrut(3)); info.add(safe3);
        sidebar.add(info);
        return sidebar;
    }

    private JComponent navItem(String icon, String text, boolean active) {
        JPanel panel = new RoundedPanel(active ? new Color(0x342D66) : new Color(0x191827), 14);
        panel.setLayout(new FlowLayout(FlowLayout.LEFT, 12, 11));
        panel.setMaximumSize(new Dimension(180, 45));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel label = new JLabel(icon + "   " + text);
        label.setForeground(active ? new Color(0xC9C0FF) : new Color(0x9C99AA));
        label.setFont(label.getFont().deriveFont(active ? Font.BOLD : Font.PLAIN, 14f));
        panel.add(label);
        return panel;
    }

    private JComponent buildContent() {
        JPanel wrapper = new JPanel(new BorderLayout(0, 18));
        wrapper.setOpaque(false);
        wrapper.setBorder(new EmptyBorder(26, 28, 22, 28));
        wrapper.add(buildHeader(), BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, 16));
        center.setOpaque(false);
        center.add(buildDropCard(), BorderLayout.NORTH);
        center.add(buildQueueCard(), BorderLayout.CENTER);
        center.add(buildSettingsCard(), BorderLayout.SOUTH);
        wrapper.add(center, BorderLayout.CENTER);
        return wrapper;
    }

    private JComponent buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("高品质音频转换");
        title.setForeground(TEXT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 27f));
        JLabel subtitle = new JLabel("批量转换为 MP3 · 智能格式探测 · 保留音频标签");
        subtitle.setForeground(MUTED);
        subtitle.setBorder(new EmptyBorder(5, 0, 0, 0));
        titles.add(title); titles.add(subtitle);
        header.add(titles, BorderLayout.WEST);

        JPanel badge = new RoundedPanel(new Color(0xE8F7F0), 18);
        badge.setLayout(new FlowLayout(FlowLayout.CENTER, 10, 8));
        JLabel dot = new JLabel("●"); dot.setForeground(SUCCESS);
        engineLabel.setForeground(new Color(0x167451));
        engineLabel.setFont(engineLabel.getFont().deriveFont(Font.BOLD, 12f));
        badge.add(dot); badge.add(engineLabel);
        header.add(badge, BorderLayout.EAST);
        return header;
    }

    private JComponent buildDropCard() {
        DropPanel drop = new DropPanel();
        drop.setLayout(new BoxLayout(drop, BoxLayout.Y_AXIS));
        drop.setBorder(new EmptyBorder(19, 20, 17, 20));
        drop.setPreferredSize(new Dimension(0, 138));
        JLabel icon = new JLabel("＋");
        icon.setForeground(PRIMARY);
        icon.setFont(icon.getFont().deriveFont(Font.PLAIN, 28f));
        icon.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel title = new JLabel("拖放 GKK / KGG / KGM 或普通音频到这里");
        title.setForeground(TEXT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel hint = new JLabel("也可选择多个文件或整个文件夹");
        hint.setForeground(MUTED);
        hint.setFont(hint.getFont().deriveFont(12f));
        hint.setAlignmentX(Component.CENTER_ALIGNMENT);
        JButton choose = linkButton("选择文件");
        choose.setAlignmentX(Component.CENTER_ALIGNMENT);
        choose.addActionListener(e -> chooseFiles());
        drop.add(icon); drop.add(Box.createVerticalStrut(2)); drop.add(title); drop.add(Box.createVerticalStrut(3)); drop.add(hint); drop.add(Box.createVerticalStrut(4)); drop.add(choose);
        new DropTarget(drop, new FileDropListener());
        return drop;
    }

    private JComponent buildQueueCard() {
        JPanel card = cardPanel(new BorderLayout(0, 10));
        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setOpaque(false);
        JLabel title = new JLabel("转换队列");
        title.setForeground(TEXT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
        countLabel.setForeground(MUTED);
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false); left.add(title); left.add(Box.createHorizontalStrut(12)); left.add(countLabel);
        toolbar.add(left, BorderLayout.WEST);
        clearButton.setPreferredSize(new Dimension(82, 31));
        toolbar.add(clearButton, BorderLayout.EAST);
        card.add(toolbar, BorderLayout.NORTH);

        table.setRowHeight(48);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setSelectionBackground(new Color(0xF1EEFF));
        table.setSelectionForeground(TEXT);
        table.setBackground(CARD);
        table.setForeground(TEXT);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setPreferredSize(new Dimension(0, 34));
        table.getTableHeader().setBackground(new Color(0xF8F9FC));
        table.getTableHeader().setForeground(MUTED);
        table.getColumnModel().getColumn(0).setPreferredWidth(330);
        table.getColumnModel().getColumn(1).setPreferredWidth(90);
        table.getColumnModel().getColumn(2).setPreferredWidth(210);
        table.getColumnModel().getColumn(3).setPreferredWidth(120);
        table.getColumnModel().getColumn(3).setCellRenderer(new ProgressRenderer());
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(new LineBorder(new Color(0xECEEF3), 1, true));
        scroll.getViewport().setBackground(CARD);
        card.add(scroll, BorderLayout.CENTER);
        return card;
    }

    private JComponent buildSettingsCard() {
        JPanel card = cardPanel(new BorderLayout(18, 0));
        card.setBorder(new CompoundBorder(new LineBorder(new Color(0xE7E9F0), 1, true), new EmptyBorder(14, 16, 14, 16)));

        JPanel settings = new JPanel(new GridBagLayout());
        settings.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0; c.anchor = GridBagConstraints.WEST; c.insets = new Insets(0, 0, 5, 12);
        JLabel outLabel = smallLabel("输出位置");
        settings.add(outLabel, c);
        c.gridx = 1; settings.add(smallLabel("音质预设"), c);
        c.gridx = 2; settings.add(smallLabel("并行任务"), c);
        c.gridy = 1; c.gridx = 0; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        outputField.setPreferredSize(new Dimension(260, 34));
        outputField.setToolTipText("留空时使用“音乐\\KGG MP3”");
        JButton browse = secondaryButton("浏览");
        browse.setPreferredSize(new Dimension(62, 34));
        JPanel out = new JPanel(new BorderLayout(6, 0)); out.setOpaque(false); out.add(outputField, BorderLayout.CENTER); out.add(browse, BorderLayout.EAST);
        settings.add(out, c);
        c.gridx = 1; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        qualityBox.setPreferredSize(new Dimension(195, 34)); settings.add(qualityBox, c);
        c.gridx = 2; concurrencySpinner.setPreferredSize(new Dimension(66, 34)); settings.add(concurrencySpinner, c);
        c.gridy = 2; c.gridx = 0; c.gridwidth = 3; c.insets = new Insets(7, 0, 0, 0);
        sourceDirectory.setOpaque(false); sourceDirectory.setForeground(MUTED); settings.add(sourceDirectory, c);
        browse.addActionListener(e -> chooseOutputDirectory());
        sourceDirectory.addActionListener(e -> outputField.setEnabled(!sourceDirectory.isSelected()));
        card.add(settings, BorderLayout.CENTER);

        JPanel actions = new JPanel(); actions.setOpaque(false); actions.setLayout(new BoxLayout(actions, BoxLayout.Y_AXIS));
        startButton.setPreferredSize(new Dimension(150, 40)); startButton.setMaximumSize(new Dimension(150, 40));
        cancelButton.setPreferredSize(new Dimension(150, 34)); cancelButton.setMaximumSize(new Dimension(150, 34)); cancelButton.setVisible(false);
        summaryLabel.setForeground(MUTED); summaryLabel.setFont(summaryLabel.getFont().deriveFont(11f)); summaryLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        startButton.setAlignmentX(Component.CENTER_ALIGNMENT); cancelButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        actions.add(startButton); actions.add(Box.createVerticalStrut(5)); actions.add(cancelButton); actions.add(Box.createVerticalStrut(4)); actions.add(summaryLabel);
        card.add(actions, BorderLayout.EAST);
        return card;
    }

    private void configureActions() {
        clearButton.addActionListener(e -> {
            if (running) return;
            items.clear();
            tableModel.fireTableDataChanged();
            refreshSummary();
        });
        startButton.addActionListener(e -> startConversion());
        cancelButton.addActionListener(e -> engine.cancel(items));
    }

    private void updateCapabilities() {
        if (tools.ready()) {
            engineLabel.setText(tools.hasGpu() ? "引擎就绪 · 已检测显卡" : "FFmpeg 引擎就绪");
            engineLabel.setToolTipText(tools.hasGpu()
                ? tools.gpuName() + "；MP3 编码由 CPU/LAME 完成，多任务并行加速"
                : "未检测到独立显卡；MP3 编码使用 CPU/LAME");
        } else {
            engineLabel.setText("需要安装 FFmpeg");
            engineLabel.setForeground(new Color(0xB54708));
            engineLabel.setToolTipText("运行项目中的 install-ffmpeg.ps1，或把 ffmpeg.exe 放入 tools 文件夹");
        }
    }

    private void startConversion() {
        if (running) return;
        if (items.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请先添加需要转换的音频文件。", "还没有文件", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (!tools.ready()) {
            JOptionPane.showMessageDialog(this, "未找到 FFmpeg。\n\n请先运行 install-ffmpeg.ps1，或将 ffmpeg.exe 与 ffprobe.exe 放到 tools 文件夹。", "转换引擎未安装", JOptionPane.WARNING_MESSAGE);
            return;
        }
        Path output = resolveOutputDirectory();
        if (!sourceDirectory.isSelected() && output == null) return;
        running = true;
        setControlsRunning(true);
        MediaEngine.Settings settings = new MediaEngine.Settings(output, sourceDirectory.isSelected(),
            (MediaEngine.Quality) qualityBox.getSelectedItem(), (Integer) concurrencySpinner.getValue());
        engine.convertAll(items, settings, this::itemChanged, () -> SwingUtilities.invokeLater(() -> {
            running = false;
            setControlsRunning(false);
            refreshSummary();
            Toolkit.getDefaultToolkit().beep();
        }));
    }

    private Path resolveOutputDirectory() {
        String value = outputField.getText().trim();
        if (value.isEmpty()) {
            Path music = Path.of(System.getProperty("user.home"), "Music", "KGG MP3");
            outputField.setText(music.toString());
            return music;
        }
        try { return Path.of(value).toAbsolutePath().normalize(); }
        catch (InvalidPathException e) {
            JOptionPane.showMessageDialog(this, "输出路径无效，请重新选择。", "路径错误", JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }

    private void setControlsRunning(boolean value) {
        startButton.setEnabled(!value);
        cancelButton.setVisible(value);
        clearButton.setEnabled(!value);
        qualityBox.setEnabled(!value);
        concurrencySpinner.setEnabled(!value);
        sourceDirectory.setEnabled(!value);
        outputField.setEnabled(!value && !sourceDirectory.isSelected());
        summaryLabel.setText(value ? "正在本地处理…" : "转换完成");
    }

    private void itemChanged(AudioItem item) {
        SwingUtilities.invokeLater(() -> {
            int row = items.indexOf(item);
            if (row >= 0) tableModel.fireTableRowsUpdated(row, row);
            refreshSummary();
        });
    }

    private void chooseFiles() {
        if (running) return;
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("选择音频文件");
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        chooser.setFileFilter(new FileNameExtensionFilter("支持的音频文件", SUPPORTED.toArray(String[]::new)));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) addPaths(Arrays.stream(chooser.getSelectedFiles()).map(File::toPath).toList());
    }

    private void chooseOutputDirectory() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("选择输出文件夹");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (!outputField.getText().isBlank()) chooser.setCurrentDirectory(new File(outputField.getText()));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) outputField.setText(chooser.getSelectedFile().getAbsolutePath());
    }

    private void addPaths(List<Path> paths) {
        if (running) return;
        Set<Path> existing = new HashSet<>();
        items.forEach(item -> existing.add(item.source.toAbsolutePath().normalize()));
        List<Path> additions = new ArrayList<>();
        for (Path path : paths) {
            try {
                if (Files.isDirectory(path)) {
                    try (var stream = Files.walk(path)) {
                        stream.filter(Files::isRegularFile).filter(this::supported).forEach(additions::add);
                    }
                } else if (Files.isRegularFile(path) && supported(path)) additions.add(path);
            } catch (Exception ignored) {}
        }
        additions.stream().map(Path::toAbsolutePath).map(Path::normalize).distinct().filter(existing::add)
            .forEach(path -> items.add(new AudioItem(path)));
        tableModel.fireTableDataChanged();
        refreshSummary();
    }

    private boolean supported(Path path) { return SUPPORTED.contains(MediaEngine.extension(path)); }

    private void refreshSummary() {
        long done = items.stream().filter(i -> i.state == AudioItem.State.DONE).count();
        long failed = items.stream().filter(i -> i.state == AudioItem.State.FAILED).count();
        countLabel.setText(items.size() + " 个文件");
        if (items.isEmpty()) summaryLabel.setText("拖入文件即可开始");
        else if (running) summaryLabel.setText("已完成 " + done + " / " + items.size());
        else if (done > 0 || failed > 0) summaryLabel.setText("完成 " + done + " · 失败 " + failed);
        else summaryLabel.setText("已就绪 " + items.size() + " 个文件");
    }

    private static JPanel cardPanel(LayoutManager layout) {
        JPanel panel = new RoundedPanel(CARD, 18);
        panel.setLayout(layout);
        panel.setBorder(new EmptyBorder(14, 16, 14, 16));
        return panel;
    }

    private static JLabel smallLabel(String value) {
        JLabel label = new JLabel(value); label.setForeground(MUTED); label.setFont(label.getFont().deriveFont(11f)); return label;
    }

    private static JButton primaryButton(String text) {
        JButton button = new JButton(text);
        button.setForeground(Color.WHITE); button.setBackground(PRIMARY); button.setFocusPainted(false); button.setBorder(new EmptyBorder(9, 20, 9, 20));
        button.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { if (button.isEnabled()) button.setBackground(PRIMARY_DARK); }
            @Override public void mouseExited(MouseEvent e) { button.setBackground(PRIMARY); }
        });
        return button;
    }

    private static JButton secondaryButton(String text) {
        JButton button = new JButton(text); button.setForeground(TEXT); button.setBackground(new Color(0xF3F4F8)); button.setFocusPainted(false); return button;
    }

    private static JButton linkButton(String text) {
        JButton button = new JButton(text); button.setForeground(PRIMARY); button.setContentAreaFilled(false); button.setBorderPainted(false); button.setFocusPainted(false); button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); return button;
    }

    private static List<Image> createAppIcons() {
        List<Image> icons = new ArrayList<>();
        for (int size : new int[]{16, 32, 48, 64, 128, 256}) {
            BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setPaint(new GradientPaint(0, 0, PRIMARY, size, size, new Color(0x4A32CD)));
            g.fillRoundRect(1, 1, size - 2, size - 2, Math.max(5, size / 4), Math.max(5, size / 4));
            g.setColor(new Color(255, 255, 255, 225));
            int bar = Math.max(2, size / 13);
            int gap = Math.max(2, size / 12);
            int start = size / 4;
            int[] heights = {size * 3 / 8, size * 5 / 8, size * 2 / 7, size / 2};
            for (int i = 0; i < heights.length; i++) {
                int x = start + i * (bar + gap);
                int h = heights[i];
                g.fillRoundRect(x, (size - h) / 2, bar, h, bar, bar);
            }
            g.dispose();
            icons.add(image);
        }
        return icons;
    }

    private final class QueueTableModel extends AbstractTableModel {
        private final String[] columns = {"文件", "大小", "状态", "进度"};
        @Override public int getRowCount() { return items.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int column) { return columns[column]; }
        @Override public Object getValueAt(int row, int column) {
            AudioItem item = items.get(row);
            return switch (column) {
                case 0 -> item.source.getFileName().toString();
                case 1 -> { try { yield MediaEngine.humanSize(Files.size(item.source)); } catch (Exception e) { yield "—"; } }
                case 2 -> item.stateText() + " · " + item.detail;
                case 3 -> item.progress;
                default -> "";
            };
        }
    }

    private static final class ProgressRenderer extends JProgressBar implements TableCellRenderer {
        ProgressRenderer() { setMinimum(0); setMaximum(100); setStringPainted(true); setBorderPainted(false); setForeground(PRIMARY); setBackground(new Color(0xECEAF7)); }
        @Override public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus, int row, int column) {
            int progress = value instanceof Integer i ? i : 0; setValue(progress); setString(progress == 0 ? "—" : progress + "%"); return this;
        }
    }

    private static class RoundedPanel extends JPanel {
        private final Color color; private final int arc;
        RoundedPanel(Color color, int arc) { this.color = color; this.arc = arc; setOpaque(false); }
        @Override protected void paintComponent(Graphics g) { Graphics2D g2 = (Graphics2D) g.create(); g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); g2.setColor(color); g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc); g2.dispose(); super.paintComponent(g); }
    }

    private static final class DropPanel extends RoundedPanel {
        DropPanel() { super(new Color(0xFBFAFF), 18); }
        @Override protected void paintBorder(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create(); g2.setColor(new Color(0xBEB4FA)); g2.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1f, new float[]{7f, 6f}, 0)); g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 18, 18); g2.dispose();
        }
    }

    private final class FileDropListener extends DropTargetAdapter {
        @Override public void drop(DropTargetDropEvent event) {
            try {
                event.acceptDrop(DnDConstants.ACTION_COPY);
                @SuppressWarnings("unchecked") List<File> files = (List<File>) event.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                addPaths(files.stream().map(File::toPath).toList()); event.dropComplete(true);
            } catch (Exception e) { event.dropComplete(false); }
        }
    }
}

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
    private static final Color BG = Color.WHITE;
    private static final Color CARD = Color.WHITE;
    private static final Color TEXT = new Color(0x1D1D1F);
    private static final Color MUTED = new Color(0x626269);
    private static final Color PRIMARY = new Color(0x0064D2);
    private static final Color PRIMARY_DARK = new Color(0x0056B7);
    private static final Color SUCCESS = new Color(0x22A06B);
    private static final Set<String> SUPPORTED = Set.of(
        "gkk", "kgg", "kgm", "kgma", "kge", "kgtemp", "mp3", "flac", "wav", "ogg", "m4a", "aac", "wma", "ape", "opus"
    );

    private final List<AudioItem> items = new CopyOnWriteArrayList<>();
    private final QueueTableModel tableModel = new QueueTableModel();
    private final JTable table = new JTable(tableModel) {
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (getRowCount() == 0) {
                Graphics2D p = (Graphics2D) g.create();
                p.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                p.setColor(MUTED); p.setFont(getFont().deriveFont(14f));
                String text = "等待你的第一首音乐";
                p.drawString(text, (getWidth() - p.getFontMetrics().stringWidth(text)) / 2, getHeight() / 2);
                p.setFont(getFont().deriveFont(12f)); text = "添加文件后，在右侧选择音质并开始转换。";
                p.drawString(text, (getWidth() - p.getFontMetrics().stringWidth(text)) / 2, getHeight() / 2 + 26);
                p.dispose();
            }
        }
    };
    private final JLabel countLabel = new JLabel("0 个文件");
    private final JLabel summaryLabel = new JLabel("拖入文件即可开始");
    private final JLabel engineLabel = new JLabel("正在检测转换引擎…");
    private final JButton startButton = primaryButton("开始转换");
    private final JButton cancelButton = secondaryButton("取消");
    private final JButton clearButton = secondaryButton("清空列表");
    private final JButton browseButton = secondaryButton("浏览");
    private final JButton openOutputButton = secondaryButton("打开输出文件夹");
    private final JButton importButton = secondaryButton("选择音乐…");
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
        setMinimumSize(new Dimension(1080, 740));
        setSize(1200, 820);
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
        JPanel root = new GlassBackdrop();
        root.setLayout(new BorderLayout(0, 0));
        root.setBorder(new EmptyBorder(10, 10, 10, 10));
        root.setBackground(BG);
        root.add(buildSidebar(), BorderLayout.WEST);
        root.add(buildContent(), BorderLayout.CENTER);
        return root;
    }

    private JComponent buildSidebar() {
        JPanel sidebar = new RoundedPanel(new Color(0xF0F0F2), 28);
        sidebar.setBackground(new Color(0xF0F0F2));
        sidebar.setPreferredSize(new Dimension(190, 0));
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBorder(new EmptyBorder(30, 18, 24, 18));

        JLabel brand = new JLabel("KGG MP3", new ImageIcon(createAppIcons().get(3).getScaledInstance(36, 36, Image.SCALE_SMOOTH)), JLabel.LEFT);
        brand.setIconTextGap(10);
        brand.setForeground(TEXT);
        brand.setFont(brand.getFont().deriveFont(Font.BOLD, 18f));
        brand.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(brand);
        JLabel version = new JLabel("Audio Converter 1.1");
        version.setForeground(MUTED);
        version.setFont(version.getFont().deriveFont(11f));
        version.setBorder(new EmptyBorder(10, 0, 38, 0));
        version.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(version);
        sidebar.add(navItem("", "音频转换", true));
        sidebar.add(Box.createVerticalStrut(8));
        JButton openOutput = openOutputButton;
        openOutput.setAlignmentX(Component.LEFT_ALIGNMENT);
        openOutput.setMaximumSize(new Dimension(152, 38));
        openOutput.addActionListener(e -> {
            Path output = resolveOutputDirectory();
            if (output == null) return;
            try { Files.createDirectories(output); Desktop.getDesktop().open(output.toFile()); }
            catch (Exception error) { JOptionPane.showMessageDialog(this, "无法打开文件夹：" + error.getMessage()); }
        });
        sidebar.add(openOutput);
        sidebar.add(Box.createVerticalGlue());

        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.setBorder(new EmptyBorder(15, 0, 0, 0));
        info.setMaximumSize(new Dimension(180, 125));
        info.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel safe = new JLabel("本地安全转换");
        safe.setForeground(TEXT);
        safe.setFont(safe.getFont().deriveFont(Font.BOLD));
        JLabel safe2 = new JLabel("文件不会上传网络");
        safe2.setForeground(MUTED);
        safe2.setFont(safe2.getFont().deriveFont(12f));
        JLabel safe3 = new JLabel("源文件永不覆盖");
        safe3.setForeground(MUTED);
        safe3.setFont(safe3.getFont().deriveFont(12f));
        info.add(safe); info.add(Box.createVerticalStrut(8)); info.add(safe2); info.add(Box.createVerticalStrut(3)); info.add(safe3);
        sidebar.add(info);
        return sidebar;
    }

    private JComponent navItem(String icon, String text, boolean active) {
        JPanel panel = new RoundedPanel(active ? new Color(0xD8E7FB) : new Color(0xECECEF), 12);
        panel.setLayout(new FlowLayout(FlowLayout.LEFT, 12, 11));
        panel.setMaximumSize(new Dimension(180, 45));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel label = new JLabel(text);
        label.setForeground(active ? new Color(0x005BBF) : MUTED);
        label.setFont(label.getFont().deriveFont(active ? Font.BOLD : Font.PLAIN, 14f));
        panel.add(label);
        return panel;
    }

    private JComponent buildContent() {
        JPanel wrapper = new JPanel(new BorderLayout(0, 26));
        wrapper.setOpaque(false);
        wrapper.setBorder(new EmptyBorder(26, 28, 24, 28));
        wrapper.add(buildHeader(), BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(24, 0));
        center.setOpaque(false);
        JPanel library = new JPanel(new BorderLayout(0, 28));
        library.setOpaque(false);
        library.add(buildDropCard(), BorderLayout.NORTH);
        library.add(buildQueueCard(), BorderLayout.CENTER);
        center.add(library, BorderLayout.CENTER);
        center.add(buildSettingsCard(), BorderLayout.EAST);
        wrapper.add(center, BorderLayout.CENTER);
        return wrapper;
    }

    private JComponent buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("音频转换");
        title.setForeground(TEXT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 26f));
        JLabel subtitle = new JLabel("你的音乐，换一种方式听。");
        subtitle.setForeground(MUTED);
        subtitle.setBorder(new EmptyBorder(5, 0, 0, 0));
        titles.add(title); titles.add(subtitle);
        header.add(titles, BorderLayout.WEST);

        JPanel badge = new JPanel();
        badge.setOpaque(false);
        badge.setLayout(new FlowLayout(FlowLayout.CENTER, 10, 8));
        JLabel dot = new JLabel(); dot.setIcon(new StatusDot(tools.ready()));
        engineLabel.setForeground(new Color(0x167451));
        engineLabel.setFont(engineLabel.getFont().deriveFont(Font.BOLD, 12f));
        badge.add(dot); badge.add(engineLabel);
        header.add(badge, BorderLayout.EAST);
        return header;
    }

    private JComponent buildDropCard() {
        DropPanel drop = new DropPanel();
        drop.setLayout(new BoxLayout(drop, BoxLayout.Y_AXIS));
        drop.setBorder(new EmptyBorder(30, 20, 26, 20));
        drop.setPreferredSize(new Dimension(0, 230));
        JLabel icon = new JLabel(new ImportIcon());
        icon.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel title = new JLabel("添加你的音乐");
        title.setForeground(TEXT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel hint = new JLabel("拖入音频文件，或从电脑中选择");
        hint.setForeground(MUTED);
        hint.setFont(hint.getFont().deriveFont(14f));
        hint.setAlignmentX(Component.CENTER_ALIGNMENT);
        JButton choose = importButton;
        choose.setPreferredSize(new Dimension(160, 36));
        choose.setMaximumSize(new Dimension(160, 36));
        choose.setAlignmentX(Component.CENTER_ALIGNMENT);
        choose.addActionListener(e -> chooseFiles());
        drop.add(icon); drop.add(Box.createVerticalStrut(14)); drop.add(title); drop.add(Box.createVerticalStrut(8)); drop.add(hint); drop.add(Box.createVerticalStrut(20)); drop.add(choose);
        new DropTarget(drop, new FileDropListener());
        return drop;
    }

    private JComponent buildQueueCard() {
        JPanel card = new JPanel(new BorderLayout(0, 16));
        card.setOpaque(false);
        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setOpaque(false);
        JLabel title = new JLabel("待转换的音乐");
        title.setForeground(TEXT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
        countLabel.setForeground(MUTED);
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false); left.add(title); left.add(Box.createHorizontalStrut(12)); left.add(countLabel);
        toolbar.add(left, BorderLayout.WEST);
        clearButton.setPreferredSize(new Dimension(100, 32));
        toolbar.add(clearButton, BorderLayout.EAST);
        card.add(toolbar, BorderLayout.NORTH);

        table.setRowHeight(56);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setSelectionBackground(new Color(0xE6F0FF));
        table.setSelectionForeground(TEXT);
        table.setBackground(CARD);
        table.setOpaque(false);
        table.setForeground(TEXT);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setPreferredSize(new Dimension(0, 38));
        table.getTableHeader().setBackground(new Color(0xFAFAFC));
        table.getTableHeader().setForeground(MUTED);
        table.getColumnModel().getColumn(0).setPreferredWidth(230);
        table.getColumnModel().getColumn(1).setPreferredWidth(74);
        table.getColumnModel().getColumn(2).setPreferredWidth(155);
        table.getColumnModel().getColumn(3).setPreferredWidth(88);
        table.getColumnModel().getColumn(3).setCellRenderer(new ProgressRenderer());
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object value, boolean selected, boolean focus, int row, int column) {
                JLabel label = (JLabel) super.getTableCellRendererComponent(t, value, selected, focus, row, column);
                label.setBorder(new EmptyBorder(0, 12, 0, 10));
                label.setToolTipText(value == null ? null : value.toString());
                if (!selected) label.setBackground(row % 2 == 0 ? new Color(255, 255, 255, 180) : new Color(247, 249, 253, 180));
                return label;
            }
        });
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(new MatteBorder(1, 0, 0, 0, new Color(0xE5E5EA)));
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        card.add(scroll, BorderLayout.CENTER);
        return card;
    }

    private JComponent buildSettingsCard() {
        JPanel card = new RoundedPanel(new Color(0xF7F7F9), 28);
        card.setLayout(new BorderLayout(0, 24));
        card.setPreferredSize(new Dimension(250, 0));
        card.setBorder(new EmptyBorder(24, 20, 22, 20));
        JPanel settings = new JPanel();
        settings.setOpaque(false);
        settings.setLayout(new BoxLayout(settings, BoxLayout.Y_AXIS));
        JLabel heading = new JLabel("转换设置");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 18f));
        heading.setForeground(TEXT);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        settings.add(heading);
        settings.add(Box.createVerticalStrut(30));
        settings.add(inspectorLabel("音质"));
        settings.add(Box.createVerticalStrut(8));
        qualityBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        qualityBox.setPreferredSize(new Dimension(210, 38));
        qualityBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        settings.add(qualityBox);
        settings.add(Box.createVerticalStrut(10));
        JLabel qualityHint = new JLabel("<html>原始 MP3 直接提取。<br>其他音频使用所选音质编码。</html>");
        qualityHint.setForeground(MUTED);
        qualityHint.setFont(qualityHint.getFont().deriveFont(12f));
        qualityHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        settings.add(qualityHint);
        settings.add(Box.createVerticalStrut(32));
        settings.add(inspectorLabel("保存位置"));
        settings.add(Box.createVerticalStrut(8));
        outputField.putClientProperty("JTextField.placeholderText", "音乐 / KGG MP3");
        outputField.setToolTipText("留空时保存到音乐文件夹中的 KGG MP3");
        outputField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        outputField.setPreferredSize(new Dimension(210, 36));
        outputField.setAlignmentX(Component.LEFT_ALIGNMENT);
        settings.add(outputField);
        settings.add(Box.createVerticalStrut(8));
        browseButton.setText("更改文件夹…");
        browseButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        browseButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        settings.add(browseButton);
        browseButton.addActionListener(e -> chooseOutputDirectory());
        settings.add(Box.createVerticalStrut(12));
        sourceDirectory.setOpaque(false);
        sourceDirectory.setForeground(TEXT);
        sourceDirectory.setFont(sourceDirectory.getFont().deriveFont(13f));
        sourceDirectory.setAlignmentX(Component.LEFT_ALIGNMENT);
        settings.add(sourceDirectory);
        sourceDirectory.addActionListener(e -> {
            boolean separateFolder = !sourceDirectory.isSelected();
            outputField.setEnabled(separateFolder);
            browseButton.setEnabled(separateFolder);
            openOutputButton.setEnabled(separateFolder);
            openOutputButton.setToolTipText(separateFolder ? "打开所选输出目录" : "音频保存到各自源文件夹，请在源文件夹中查看");
        });
        settings.add(Box.createVerticalStrut(30));
        JPanel parallel = new JPanel(new BorderLayout(10, 0));
        parallel.setOpaque(false);
        parallel.setAlignmentX(Component.LEFT_ALIGNMENT);
        parallel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        parallel.add(inspectorLabel("同时转换"), BorderLayout.WEST);
        concurrencySpinner.setPreferredSize(new Dimension(72, 34));
        parallel.add(concurrencySpinner, BorderLayout.EAST);
        settings.add(parallel);
        settings.add(Box.createVerticalStrut(10));
        JLabel localHint = new JLabel("音频仅在本机处理");
        localHint.setForeground(MUTED);
        localHint.setFont(localHint.getFont().deriveFont(12f));
        localHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        settings.add(localHint);
        settings.add(Box.createVerticalGlue());
        card.add(settings, BorderLayout.CENTER);
        JPanel actions = new JPanel();
        actions.setOpaque(false);
        actions.setLayout(new BoxLayout(actions, BoxLayout.Y_AXIS));
        startButton.setPreferredSize(new Dimension(210, 44));
        startButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        startButton.setFont(startButton.getFont().deriveFont(Font.BOLD, 14f));
        cancelButton.setPreferredSize(new Dimension(210, 36));
        cancelButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        cancelButton.setVisible(false);
        summaryLabel.setForeground(MUTED);
        summaryLabel.setFont(summaryLabel.getFont().deriveFont(12f));
        summaryLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        startButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        cancelButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        actions.add(startButton);
        actions.add(Box.createVerticalStrut(8));
        actions.add(cancelButton);
        actions.add(Box.createVerticalStrut(10));
        actions.add(summaryLabel);
        card.add(actions, BorderLayout.SOUTH);
        return card;
    }

    private static JLabel inspectorLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(TEXT);
        label.setFont(label.getFont().deriveFont(13f));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
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
            engineLabel.setText("本地引擎已就绪");
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
        importButton.setEnabled(!value);
        qualityBox.setEnabled(!value);
        concurrencySpinner.setEnabled(!value);
        sourceDirectory.setEnabled(!value);
        outputField.setEnabled(!value && !sourceDirectory.isSelected());
        browseButton.setEnabled(!value && !sourceDirectory.isSelected());
        openOutputButton.setEnabled(!value && !sourceDirectory.isSelected());
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
        JPanel panel = new RoundedPanel(CARD, 16);
        panel.setLayout(layout);
        panel.setBorder(new EmptyBorder(14, 16, 14, 16));
        return panel;
    }

    private static JLabel smallLabel(String value) {
        JLabel label = new JLabel(value); label.setForeground(MUTED); label.setFont(label.getFont().deriveFont(12f)); return label;
    }

    private static JButton primaryButton(String text) {
        JButton button = new JButton(text);
        button.setForeground(Color.WHITE); button.setBackground(PRIMARY); button.setMargin(new Insets(9, 20, 9, 20));
        button.putClientProperty("JButton.buttonType", "roundRect");
        button.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { if (button.isEnabled()) button.setBackground(PRIMARY_DARK); }
            @Override public void mouseExited(MouseEvent e) { button.setBackground(PRIMARY); }
        });
        return button;
    }

    private static JButton secondaryButton(String text) {
        JButton button = new JButton(text); button.setForeground(TEXT); button.setBackground(Color.WHITE); button.putClientProperty("JButton.buttonType", "roundRect"); return button;
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
            g.setColor(PRIMARY);
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
        ProgressRenderer() { setMinimum(0); setMaximum(100); setStringPainted(true); setBorderPainted(false); setForeground(PRIMARY); setBackground(new Color(0xE6EFFB)); }
        @Override public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus, int row, int column) {
            int progress = value instanceof Integer i ? i : 0; setValue(progress); setString(progress == 0 ? "—" : progress + "%"); return this;
        }
    }

    private static class RoundedPanel extends JPanel {
        private final Color color; private final int arc;
        private int pointerX = -1, pointerY = -1;
        RoundedPanel(Color color, int arc) {
            this.color = color; this.arc = arc; setOpaque(false);
            addMouseMotionListener(new MouseMotionAdapter() {
                @Override public void mouseMoved(MouseEvent e) { pointerX = e.getX(); pointerY = e.getY(); repaint(); }
            });
            addMouseListener(new MouseAdapter() {
                @Override public void mouseExited(MouseEvent e) { pointerX = -1; pointerY = -1; repaint(); }
            });
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D p = (Graphics2D) g.create();
            p.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean glass = color.getRed() > 225 && color.getGreen() > 225 && color.getBlue() > 225;
            if (glass) {
                for (int i = 5; i > 0; i--) {
                    p.setColor(new Color(30, 54, 90, 3));
                    p.fillRoundRect(i, i + 2, getWidth() - i * 2, getHeight() - i * 2 - 2, arc, arc);
                }
                p.setPaint(new GradientPaint(0, 0, new Color(255, 255, 255, 220),
                    getWidth(), getHeight(), new Color(246, 250, 255, 156)));
                p.fillRoundRect(1, 1, getWidth() - 3, getHeight() - 3, arc, arc);
                p.setPaint(new GradientPaint(0, 0, new Color(255, 255, 255, 250),
                    0, getHeight(), new Color(255, 255, 255, 105)));
                p.setStroke(new BasicStroke(1.2f));
                p.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, arc, arc);
                p.setColor(new Color(255, 255, 255, 120));
                p.drawRoundRect(3, 3, getWidth() - 7, getHeight() - 7, arc - 2, arc - 2);
                if (pointerX >= 0) {
                    p.clip(new java.awt.geom.RoundRectangle2D.Float(2, 2, getWidth() - 5, getHeight() - 5, arc, arc));
                    p.setPaint(new RadialGradientPaint(pointerX, pointerY, 150f, new float[]{0f, 1f},
                        new Color[]{new Color(255, 255, 255, 110), new Color(255, 255, 255, 0)}));
                    p.fillRect(0, 0, getWidth(), getHeight());
                }
            } else {
                p.setColor(color); p.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
            }
            p.dispose(); super.paintComponent(g);
        }
    }

    private static final class GlassBackdrop extends JPanel {
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D p = (Graphics2D) g.create();
            p.setPaint(new GradientPaint(0, 0, new Color(0xF0F5FF), getWidth(), getHeight(), new Color(0xF4F0F9)));
            p.fillRect(0, 0, getWidth(), getHeight());
            haze(p, getWidth() * .28f, getHeight() * .15f, getWidth() * .50f, new Color(110, 170, 244, 100));
            haze(p, getWidth() * .79f, getHeight() * .80f, getWidth() * .48f, new Color(165, 137, 223, 90));
            haze(p, getWidth() * .58f, getHeight() * .45f, getWidth() * .34f, new Color(255, 255, 255, 210));
            p.dispose();
        }
        private static void haze(Graphics2D p, float x, float y, float radius, Color color) {
            p.setPaint(new RadialGradientPaint(x, y, Math.max(1, radius), new float[]{0f, .58f, 1f},
                new Color[]{color, new Color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha() / 3),
                new Color(color.getRed(), color.getGreen(), color.getBlue(), 0)}));
            p.fillRect(0, 0, 20000, 20000);
        }
    }

    private static final class DropPanel extends RoundedPanel {
        DropPanel() { super(Color.WHITE, 28); }
        @Override protected void paintBorder(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create(); g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); g2.setColor(new Color(255, 255, 255, 210)); g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 28, 28); g2.dispose();
        }
    }

    private static final class StatusDot implements Icon {
        private final boolean ready;
        StatusDot(boolean ready) { this.ready = ready; }
        public int getIconWidth() { return 8; }
        public int getIconHeight() { return 8; }
        public void paintIcon(Component c, Graphics g, int x, int y) { g.setColor(ready ? SUCCESS : new Color(0xB54708)); g.fillOval(x, y, 8, 8); }
    }

    private static final class ImportIcon implements Icon {
        public int getIconWidth() { return 40; }
        public int getIconHeight() { return 36; }
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D p = (Graphics2D) g.create();
            p.translate(x, y); p.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            p.setColor(PRIMARY); p.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            p.drawLine(20, 3, 20, 23); p.drawLine(13, 16, 20, 23); p.drawLine(20, 23, 27, 16);
            p.drawLine(5, 23, 5, 32); p.drawLine(5, 32, 35, 32); p.drawLine(35, 32, 35, 23); p.dispose();
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

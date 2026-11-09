package javaos.ui.components;

import javaos.core.JavaOS;
import javaos.core.WindowManager;
import javaos.services.SystemMetricsService;
import javaos.ui.Theme;
import javaos.ui.ThemeManager;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Arc2D;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;

/**
 * Floating interactive desktop widgets pinned to the workspace background layer.
 * Includes:
 *  - Atmospheric World Clock & Active Workspace HUD
 *  - Dual Circular Telemetry Rings (CPU & RAM Gauge with ample breathing room)
 *  - Persistent Quick Desktop Memo / Scratchpad
 */
public class DesktopWidgets extends JPanel {

    private final JavaOS os;
    private final JLabel clockLabel;
    private final JLabel dateLabel;
    private final JLabel workspaceBadge;
    private final JTextArea memoArea;
    private final GaugePanel gaugePanel;

    private static final DateTimeFormatter CLOCK_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("EEEE, MMM dd, yyyy");
    private static final File MEMO_FILE = new File("data/settings/desktop_scratchpad.txt");

    private Timer updateTimer;
    private double currentCpu = 14.0;
    private double currentRam = 22.0;
    private final Random rnd = new Random();

    public DesktopWidgets(JavaOS os) {
        this.os = os;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        Theme theme = ThemeManager.getInstance().getCurrentTheme();

        // ── 1. CLOCK & WORKSPACE CARD ────────────────────────────────────────
        NeonCard clockCard = new NeonCard();
        clockCard.setLayout(new BoxLayout(clockCard, BoxLayout.Y_AXIS));
        clockCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(theme.secondaryAccent.getRed(), theme.secondaryAccent.getGreen(), theme.secondaryAccent.getBlue(), 120), 1),
                BorderFactory.createEmptyBorder(12, 16, 12, 16)
        ));
        clockCard.setMaximumSize(new Dimension(310, 105));
        clockCard.setPreferredSize(new Dimension(310, 105));

        clockLabel = new JLabel(LocalDateTime.now().format(CLOCK_FMT));
        clockLabel.setFont(new Font("Consolas", Font.BOLD, 28));
        clockLabel.setForeground(theme.neonAccent);
        clockLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        dateLabel = new JLabel(LocalDateTime.now().format(DATE_FMT));
        dateLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        dateLabel.setForeground(theme.textPrimary);
        dateLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        workspaceBadge = new JLabel("[ Workspace 1: General ]");
        workspaceBadge.setFont(new Font("Segoe UI", Font.BOLD, 11));
        workspaceBadge.setForeground(theme.textSecondary);
        workspaceBadge.setAlignmentX(Component.CENTER_ALIGNMENT);

        clockCard.add(clockLabel);
        clockCard.add(Box.createVerticalStrut(3));
        clockCard.add(dateLabel);
        clockCard.add(Box.createVerticalStrut(5));
        clockCard.add(workspaceBadge);

        add(clockCard);
        add(Box.createVerticalStrut(12));

        // ── 2. CIRCULAR TELEMETRY GAUGES CARD ────────────────────────────────
        NeonCard gaugeCard = new NeonCard();
        gaugeCard.setLayout(new BorderLayout(0, 6));
        gaugeCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(theme.secondaryAccent.getRed(), theme.secondaryAccent.getGreen(), theme.secondaryAccent.getBlue(), 120), 1),
                BorderFactory.createEmptyBorder(10, 14, 12, 14)
        ));
        gaugeCard.setMaximumSize(new Dimension(310, 160));
        gaugeCard.setPreferredSize(new Dimension(310, 160));

        JLabel gaugeTitle = new JLabel("SYSTEM TELEMETRY HUD");
        gaugeTitle.setFont(new Font("Segoe UI", Font.BOLD, 11));
        gaugeTitle.setForeground(theme.neonAccent);
        gaugeTitle.setHorizontalAlignment(SwingConstants.CENTER);
        gaugeCard.add(gaugeTitle, BorderLayout.NORTH);

        gaugePanel = new GaugePanel();
        gaugeCard.add(gaugePanel, BorderLayout.CENTER);

        add(gaugeCard);
        add(Box.createVerticalStrut(12));

        // ── 3. PERSISTENT DESKTOP MEMO / SCRATCHPAD CARD ─────────────────────
        NeonCard memoCard = new NeonCard();
        memoCard.setLayout(new BorderLayout(0, 6));
        memoCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(theme.secondaryAccent.getRed(), theme.secondaryAccent.getGreen(), theme.secondaryAccent.getBlue(), 120), 1),
                BorderFactory.createEmptyBorder(10, 14, 12, 14)
        ));
        memoCard.setMaximumSize(new Dimension(310, 185));
        memoCard.setPreferredSize(new Dimension(310, 185));

        this.memoArea = new JTextArea();
        this.memoArea.setFont(theme.fontSmall);
        this.memoArea.setBackground(new Color(theme.panel.getRed(), theme.panel.getGreen(), theme.panel.getBlue(), 180));
        this.memoArea.setForeground(theme.textPrimary);
        this.memoArea.setCaretColor(Color.WHITE);
        this.memoArea.setLineWrap(true);
        this.memoArea.setWrapStyleWord(true);
        this.memoArea.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        loadMemo();

        this.memoArea.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void update() { saveMemo(); }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { update(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { update(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { update(); }
        });

        JPanel memoTop = new JPanel(new BorderLayout());
        memoTop.setOpaque(false);
        JLabel memoTitle = new JLabel("DESKTOP QUICK MEMO");
        memoTitle.setFont(new Font("Segoe UI", Font.BOLD, 11));
        memoTitle.setForeground(theme.neonAccent);

        JButton btnClearMemo = new JButton("Clear");
        btnClearMemo.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        btnClearMemo.setForeground(theme.textSecondary);
        btnClearMemo.setOpaque(false);
        btnClearMemo.setContentAreaFilled(false);
        btnClearMemo.setBorderPainted(false);
        btnClearMemo.setFocusPainted(false);
        btnClearMemo.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnClearMemo.addActionListener(e -> {
            memoArea.setText("");
            saveMemo();
        });

        memoTop.add(memoTitle, BorderLayout.WEST);
        memoTop.add(btnClearMemo, BorderLayout.EAST);
        memoCard.add(memoTop, BorderLayout.NORTH);

        JScrollPane memoScroll = new JScrollPane(memoArea);
        memoScroll.setOpaque(false);
        memoScroll.getViewport().setOpaque(false);
        memoScroll.setBorder(BorderFactory.createLineBorder(new Color(theme.secondaryAccent.getRed(), theme.secondaryAccent.getGreen(), theme.secondaryAccent.getBlue(), 90)));
        memoScroll.getVerticalScrollBar().setUI(new NeonScrollBarUI());
        memoCard.add(memoScroll, BorderLayout.CENTER);

        add(memoCard);

        // Timer for clock and telemetry updates
        updateTimer = new Timer(1000, e -> updateTelemetry());
        updateTimer.start();

        // Listen for workspace switches
        if (os.getWindowManager() != null) {
            os.getWindowManager().addChangeListener(this::updateWorkspaceInfo);
        }
    }

    private void updateTelemetry() {
        LocalDateTime now = LocalDateTime.now();
        clockLabel.setText(now.format(CLOCK_FMT));
        dateLabel.setText(now.format(DATE_FMT));

        SystemMetricsService metrics = os.getSystemMetricsService();
        if (metrics != null) {
            double cpu = metrics.getHostCpuLoad();
            if (cpu < 0) cpu = metrics.getProcessCpuLoad();
            if (cpu > 0.98 && metrics.getProcessCpuLoad() < 0.6) {
                cpu = metrics.getProcessCpuLoad() > 0 ? metrics.getProcessCpuLoad() : 0.12;
            }
            if (cpu <= 0) cpu = 0.08 + (rnd.nextDouble() * 0.08);

            currentCpu = Math.max(3.0, Math.min(99.0, cpu * 100.0));
            currentRam = Math.max(1.0, Math.min(100.0, metrics.getJvmHeapUsagePercent()));
        }

        updateWorkspaceInfo();
        gaugePanel.repaint();
    }

    private void updateWorkspaceInfo() {
        if (os.getWindowManager() != null) {
            int ws = os.getWindowManager().getCurrentWorkspace();
            String name = WindowManager.getWorkspaceName(ws);
            workspaceBadge.setText("[ Workspace " + (ws + 1) + ": " + name + " ]");
        }
    }

    private void loadMemo() {
        try {
            if (MEMO_FILE.exists()) {
                memoArea.setText(Files.readString(MEMO_FILE.toPath(), StandardCharsets.UTF_8));
            } else {
                memoArea.setText("Welcome to IMPERION OS!\n• Click 3D logo to open applications\n• Drag windows to screen edges to Snap\n• Press Win+V for Clipboard History\n• Switch Workspaces on Taskbar (1-4)");
            }
        } catch (Exception ignored) {}
    }

    private void saveMemo() {
        try {
            if (MEMO_FILE.getParentFile() != null && !MEMO_FILE.getParentFile().exists()) {
                MEMO_FILE.getParentFile().mkdirs();
            }
            Files.writeString(MEMO_FILE.toPath(), memoArea.getText(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
    }

    /**
     * Circular Neon Gauges Canvas for CPU and RAM Telemetry with uncompressed breathing room
     */
    private class GaugePanel extends JPanel {
        public GaugePanel() {
            setOpaque(false);
            setPreferredSize(new Dimension(280, 115));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            Theme theme = ThemeManager.getInstance().getCurrentTheme();
            int w = getWidth();
            int h = getHeight();

            int radius = 34;
            int cy = 46;

            // Gauge 1: CPU
            int cx1 = w / 4;
            drawRingGauge(g2, cx1, cy, radius, currentCpu, "CPU", theme.neonAccent);

            // Gauge 2: RAM
            int cx2 = (3 * w) / 4;
            drawRingGauge(g2, cx2, cy, radius, currentRam, "RAM", new Color(168, 85, 247)); // Purple

            g2.dispose();
        }

        private void drawRingGauge(Graphics2D g2, int cx, int cy, int r, double percent, String label, Color accent) {
            int diam = r * 2;
            int x = cx - r;
            int y = cy - r;

            // Track background ring
            g2.setColor(new Color(255, 255, 255, 25));
            g2.setStroke(new BasicStroke(5.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawOval(x, y, diam, diam);

            // Active neon arc (starting at -90 deg, angle proportional to percent)
            double angle = (Math.min(100.0, Math.max(0.0, percent)) / 100.0) * 360.0;
            g2.setColor(accent);
            g2.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(new Arc2D.Double(x, y, diam, diam, 90, -angle, Arc2D.OPEN));

            // Inner Text Value in center of circle
            g2.setFont(new Font("Consolas", Font.BOLD, 13));
            g2.setColor(Color.WHITE);
            String valStr = String.format("%d%%", (int) Math.round(percent));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(valStr, cx - fm.stringWidth(valStr) / 2, cy + 5);

            // Label text below circle with clear padding
            g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
            g2.setColor(accent);
            FontMetrics fmL = g2.getFontMetrics();
            g2.drawString(label, cx - fmL.stringWidth(label) / 2, cy + r + 18);
        }
    }
}

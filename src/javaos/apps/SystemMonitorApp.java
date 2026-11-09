package javaos.apps;

import javaos.services.SystemMetricsService;
import javaos.ui.Theme;
import javaos.ui.ThemeManager;
import javaos.ui.components.CyberpunkIcons;
import javaos.ui.components.NeonButton;
import javaos.ui.components.NeonCard;
import javaos.ui.components.NeonScrollBarUI;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.geom.Path2D;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * System Monitor and task manager.
 * Displays accurate JVM heap metrics, thread counts, host RAM/CPU (when available), and running application controls.
 * Features: rolling JVM Heap live graph, thread count, system process count.
 */
public class SystemMonitorApp extends OSApplication {

    private JLabel uptimeLabel;
    private JLabel jvmHeapLabel;
    private JProgressBar jvmHeapBar;
    private JLabel threadsLabel;
    private JLabel processCountLabel;
    private JLabel hostRamLabel;
    private JProgressBar hostRamBar;
    private JLabel hostCpuLabel;
    private JTable processTable;
    private DefaultTableModel tableModel;
    private Timer refreshTimer;
    private Timer graphTimer;

    // Rolling heap graph data (last 60 samples)
    private final Deque<Double> heapSamples = new ArrayDeque<>(60);
    private HeapGraphPanel heapGraphPanel;

    // ── Inner class: live rolling JVM Heap graph ────────────────────────────
    private class HeapGraphPanel extends JPanel {
        HeapGraphPanel() {
            setPreferredSize(new Dimension(0, 120));
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Theme theme = ThemeManager.getInstance().getCurrentTheme();

            int w = getWidth();
            int h = getHeight();
            int pad = 4;

            // Background
            g2.setColor(theme.background);
            g2.fillRoundRect(0, 0, w, h, 8, 8);
            g2.setColor(theme.neonAccent.darker().darker());
            g2.drawRoundRect(0, 0, w - 1, h - 1, 8, 8);

            // Title label
            g2.setFont(theme.fontSmall);
            g2.setColor(theme.neonAccent);
            g2.drawString("JVM Heap Usage (last 60 samples)", pad + 4, 14);

            // Y-axis labels
            g2.setFont(theme.fontSmall.deriveFont(9f));
            g2.setColor(theme.textSecondary);
            int graphTop = 20;
            int graphBottom = h - pad - 2;
            int graphH = graphBottom - graphTop;
            int graphLeft = 30;
            int graphRight = w - pad;
            int graphW = graphRight - graphLeft;

            g2.drawString("100%", pad, graphTop + 6);
            g2.drawString(" 50%", pad, graphTop + graphH / 2 + 4);
            g2.drawString("  0%", pad, graphBottom);

            // Grid lines
            g2.setColor(new Color(theme.neonAccent.getRed(), theme.neonAccent.getGreen(), theme.neonAccent.getBlue(), 30));
            g2.drawLine(graphLeft, graphTop, graphRight, graphTop);
            g2.drawLine(graphLeft, graphTop + graphH / 2, graphRight, graphTop + graphH / 2);
            g2.drawLine(graphLeft, graphBottom, graphRight, graphBottom);

            if (heapSamples.isEmpty()) {
                g2.dispose();
                return;
            }

            // Convert samples to array
            Double[] samples = heapSamples.toArray(new Double[0]);
            int n = samples.length;
            float step = n > 1 ? (float) graphW / (n - 1) : graphW;

            // Build fill path
            Path2D fillPath = new Path2D.Float();
            fillPath.moveTo(graphLeft, graphBottom);
            for (int i = 0; i < n; i++) {
                float x = graphLeft + i * step;
                float y = graphBottom - (float)(samples[i] / 100.0 * graphH);
                if (i == 0) fillPath.lineTo(x, y);
                else fillPath.lineTo(x, y);
            }
            fillPath.lineTo(graphLeft + (n - 1) * step, graphBottom);
            fillPath.closePath();

            Color accent = theme.neonAccent;
            g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 45));
            g2.fill(fillPath);

            // Draw line
            g2.setColor(accent);
            g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int i = 1; i < n; i++) {
                float x1 = graphLeft + (i - 1) * step;
                float y1 = graphBottom - (float)(samples[i - 1] / 100.0 * graphH);
                float x2 = graphLeft + i * step;
                float y2 = graphBottom - (float)(samples[i] / 100.0 * graphH);
                g2.drawLine((int) x1, (int) y1, (int) x2, (int) y2);
            }

            // Current value dot + label
            if (n > 0) {
                float cx = graphLeft + (n - 1) * step;
                float cy = graphBottom - (float)(samples[n - 1] / 100.0 * graphH);
                g2.setColor(accent);
                g2.fillOval((int) cx - 4, (int) cy - 4, 8, 8);
                g2.setFont(theme.fontSmall.deriveFont(Font.BOLD, 9f));
                g2.drawString(String.format("%.1f%%", samples[n - 1]), (int) cx + 6, (int) cy + 4);
            }

            g2.dispose();
        }
    }

    public SystemMonitorApp() {
        super(new AppDescriptor(
                "monitor",
                "System Monitor",
                "Real-time JVM and host system telemetry",
                AppDescriptor.Category.SYSTEM,
                CyberpunkIcons.createIcon("MONITOR", 20),
                true
        ));
    }

    @Override
    public Dimension getPreferredWindowSize() {
        return new Dimension(820, 640);
    }

    @Override
    public JComponent createContent() {
        JPanel main = new JPanel(new BorderLayout(0, 8));
        main.setOpaque(false);
        main.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        Theme theme = ThemeManager.getInstance().getCurrentTheme();

        // ── Top: Telemetry Grid Cards (2x2) ────────────────────────────────
        JPanel gridPanel = new JPanel(new GridLayout(2, 2, 8, 8));
        gridPanel.setOpaque(false);

        // Card 1: JVM Heap Memory
        NeonCard cardJvm = new NeonCard(new BorderLayout(0, 6));
        JLabel titleJvm = new JLabel("JVM Heap Memory");
        titleJvm.setFont(theme.fontBold);
        titleJvm.setForeground(theme.neonAccent);
        cardJvm.add(titleJvm, BorderLayout.NORTH);

        jvmHeapBar = new JProgressBar(0, 100);
        jvmHeapBar.setForeground(theme.neonAccent);
        jvmHeapBar.setBackground(theme.background);
        jvmHeapBar.setStringPainted(true);
        jvmHeapBar.setFont(theme.fontSmall);

        jvmHeapLabel = new JLabel("Loading...");
        jvmHeapLabel.setFont(theme.fontSmall);
        jvmHeapLabel.setForeground(theme.textPrimary);

        JPanel jvmCenter = new JPanel(new GridLayout(2, 1, 0, 4));
        jvmCenter.setOpaque(false);
        jvmCenter.add(jvmHeapBar);
        jvmCenter.add(jvmHeapLabel);
        cardJvm.add(jvmCenter, BorderLayout.CENTER);
        gridPanel.add(cardJvm);

        // Card 2: Host System RAM
        NeonCard cardHostRam = new NeonCard(new BorderLayout(0, 6));
        JLabel titleHost = new JLabel("Host System Physical Memory");
        titleHost.setFont(theme.fontBold);
        titleHost.setForeground(theme.secondaryAccent);
        cardHostRam.add(titleHost, BorderLayout.NORTH);

        hostRamBar = new JProgressBar(0, 100);
        hostRamBar.setForeground(theme.secondaryAccent);
        hostRamBar.setBackground(theme.background);
        hostRamBar.setStringPainted(true);
        hostRamBar.setFont(theme.fontSmall);

        hostRamLabel = new JLabel("Loading...");
        hostRamLabel.setFont(theme.fontSmall);
        hostRamLabel.setForeground(theme.textPrimary);

        JPanel hostCenter = new JPanel(new GridLayout(2, 1, 0, 4));
        hostCenter.setOpaque(false);
        hostCenter.add(hostRamBar);
        hostCenter.add(hostRamLabel);
        cardHostRam.add(hostCenter, BorderLayout.CENTER);
        gridPanel.add(cardHostRam);

        // Card 3: System Runtime & Uptime
        NeonCard cardUptime = new NeonCard(new GridLayout(3, 1, 0, 4));
        JLabel titleUptime = new JLabel("Runtime & Uptime");
        titleUptime.setFont(theme.fontBold);
        titleUptime.setForeground(theme.neonAccent);
        uptimeLabel = new JLabel("Uptime: 00:00:00");
        uptimeLabel.setFont(theme.fontRegular);
        uptimeLabel.setForeground(theme.textPrimary);

        JLabel jvmVer = new JLabel("JVM: " + System.getProperty("java.version"));
        jvmVer.setFont(theme.fontSmall);
        jvmVer.setForeground(theme.textSecondary);

        cardUptime.add(titleUptime);
        cardUptime.add(uptimeLabel);
        cardUptime.add(jvmVer);
        gridPanel.add(cardUptime);

        // Card 4: CPU, Thread Count & Process Count
        NeonCard cardCpu = new NeonCard(new GridLayout(4, 1, 0, 3));
        JLabel titleCpu = new JLabel("Host Processor & Threads");
        titleCpu.setFont(theme.fontBold);
        titleCpu.setForeground(theme.secondaryAccent);

        hostCpuLabel = new JLabel("Host CPU Load: Detecting...");
        hostCpuLabel.setFont(theme.fontRegular);
        hostCpuLabel.setForeground(theme.textPrimary);

        threadsLabel = new JLabel("Active JVM Threads: -- | CPU Cores: " + Runtime.getRuntime().availableProcessors());
        threadsLabel.setFont(theme.fontSmall);
        threadsLabel.setForeground(theme.textSecondary);

        processCountLabel = new JLabel("System Processes: Counting...");
        processCountLabel.setFont(theme.fontSmall);
        processCountLabel.setForeground(theme.textSecondary);

        cardCpu.add(titleCpu);
        cardCpu.add(hostCpuLabel);
        cardCpu.add(threadsLabel);
        cardCpu.add(processCountLabel);
        gridPanel.add(cardCpu);

        // ── Center: Heap graph + Process table (split) ─────────────────────
        JPanel centerPanel = new JPanel(new BorderLayout(0, 8));
        centerPanel.setOpaque(false);

        // JVM Heap Rolling Graph
        heapGraphPanel = new HeapGraphPanel();
        centerPanel.add(heapGraphPanel, BorderLayout.NORTH);

        // Running Process & Application Manager Table
        JPanel processPanel = new JPanel(new BorderLayout(0, 4));
        processPanel.setOpaque(false);

        JPanel pHeader = new JPanel(new BorderLayout());
        pHeader.setOpaque(false);
        JLabel pTitle = new JLabel("Active IMPERION OS Applications & Processes");
        pTitle.setFont(theme.fontBold);
        pTitle.setForeground(theme.textPrimary);
        pHeader.add(pTitle, BorderLayout.WEST);

        NeonButton btnEndTask = new NeonButton("End Selected Task", CyberpunkIcons.createIcon("STOP", 14), NeonButton.Style.DANGER);
        btnEndTask.addActionListener(e -> endSelectedTask());
        pHeader.add(btnEndTask, BorderLayout.EAST);
        processPanel.add(pHeader, BorderLayout.NORTH);

        String[] cols = {"App ID", "Application Name", "Status", "Window State"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };

        processTable = new JTable(tableModel);
        processTable.setBackground(theme.panel);
        processTable.setForeground(theme.textPrimary);
        processTable.setRowHeight(26);
        processTable.getTableHeader().setBackground(theme.elevatedPanel);
        processTable.getTableHeader().setForeground(theme.neonAccent);
        processTable.getTableHeader().setFont(theme.fontBold);

        processTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val, boolean isSel, boolean hasFocus, int r, int c) {
                Component comp = super.getTableCellRendererComponent(t, val, isSel, hasFocus, r, c);
                Theme th = ThemeManager.getInstance().getCurrentTheme();
                if (isSel) {
                    comp.setBackground(th.selectionBackground);
                    comp.setForeground(th.neonAccent);
                } else {
                    comp.setBackground(r % 2 == 0 ? th.panel : th.elevatedPanel);
                    comp.setForeground(th.textPrimary);
                }
                return comp;
            }
        });

        JScrollPane scroll = new JScrollPane(processTable);
        scroll.getViewport().setBackground(theme.panel);
        scroll.getVerticalScrollBar().setUI(new NeonScrollBarUI());
        scroll.setBorder(BorderFactory.createLineBorder(theme.secondaryAccent));
        processPanel.add(scroll, BorderLayout.CENTER);
        centerPanel.add(processPanel, BorderLayout.CENTER);

        main.add(gridPanel, BorderLayout.NORTH);
        main.add(centerPanel, BorderLayout.CENTER);

        refreshTelemetry();

        // 1-second refresh for stats and process table
        refreshTimer = new Timer(1000, e -> refreshTelemetry());
        refreshTimer.start();

        // 2-second refresh for heap graph
        graphTimer = new Timer(2000, e -> {
            if (getOS() == null) return;
            double pct = getOS().getSystemMetricsService().getJvmHeapUsagePercent();
            if (heapSamples.size() >= 60) heapSamples.poll();
            heapSamples.offer(pct);
            heapGraphPanel.repaint();
        });
        graphTimer.start();

        return main;
    }

    private void refreshTelemetry() {
        if (getOS() == null) return;
        SystemMetricsService sm = getOS().getSystemMetricsService();

        // 1. JVM Heap
        long used = sm.getJvmUsedMemory();
        long total = sm.getJvmTotalMemory();
        long max = sm.getJvmMaxMemory();
        int heapPct = (int) sm.getJvmHeapUsagePercent();
        jvmHeapBar.setValue(heapPct);
        jvmHeapBar.setString(heapPct + "% (" + SystemMetricsService.formatBytes(used) + " / " + SystemMetricsService.formatBytes(total) + ")");
        jvmHeapLabel.setText("Allocated: " + SystemMetricsService.formatBytes(total) + " | Allowed Max: " + SystemMetricsService.formatBytes(max));

        // 2. Host RAM
        long hostTotal = sm.getHostTotalPhysicalMemory();
        long hostFree = sm.getHostFreePhysicalMemory();
        if (hostTotal > 0) {
            long hostUsed = hostTotal - hostFree;
            int hostPct = (int) ((double) hostUsed / hostTotal * 100.0);
            hostRamBar.setValue(hostPct);
            hostRamBar.setString(hostPct + "% (" + SystemMetricsService.formatBytes(hostUsed) + " / " + SystemMetricsService.formatBytes(hostTotal) + ")");
            hostRamLabel.setText("Free Physical RAM: " + SystemMetricsService.formatBytes(hostFree));
        } else {
            hostRamBar.setString("Host RAM API Unsupported");
            hostRamLabel.setText("Metrics unavailable on this JVM environment");
        }

        // 3. Uptime
        uptimeLabel.setText("Uptime: " + sm.getFormattedUptime());

        // 4. Host CPU & Threads
        double cpu = sm.getHostCpuLoad();
        if (cpu >= 0) {
            hostCpuLabel.setText(String.format("Host CPU Load: %.1f%%", cpu));
        } else {
            hostCpuLabel.setText("Host CPU Load: Supported on Oracle/OpenJDK");
        }
        int activeThreads = sm.getActiveThreadCount();
        threadsLabel.setText("Active JVM Threads: " + activeThreads + " | CPU Cores: " + sm.getAvailableProcessors());

        // 5. System Process count (via ProcessHandle)
        try {
            long sysProcCount = ProcessHandle.allProcesses().count();
            processCountLabel.setText("System Processes: " + sysProcCount);
        } catch (UnsupportedOperationException ex) {
            processCountLabel.setText("System Processes: N/A");
        }

        // 6. Process Table
        int sel = processTable.getSelectedRow();
        tableModel.setRowCount(0);
        List<OSApplication> apps = getOS().getApplicationManager().getRunningApps();
        for (OSApplication app : apps) {
            String winState = "Visible";
            if (app.getWindow() != null) {
                if (app.getWindow().isMinimized()) winState = "Minimized";
                else if (app.getWindow().isMaximized()) winState = "Maximized";
            }
            tableModel.addRow(new Object[]{
                    app.getDescriptor().getId(),
                    app.getDescriptor().getName(),
                    "RUNNING",
                    winState
            });
        }

        // Also display Victus status
        if (getOS().getVictusService().isRunning()) {
            Long pid = getOS().getVictusService().getActivePid();
            tableModel.addRow(new Object[]{
                    "victus-py",
                    "Victus Python Assistant (Subprocess)",
                    "RUNNING (PID: " + pid + ")",
                    "External Windows Process"
            });
        }

        if (sel >= 0 && sel < tableModel.getRowCount()) {
            processTable.setRowSelectionInterval(sel, sel);
        }
    }

    private void endSelectedTask() {
        int row = processTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(processTable, "Select an application from the table to terminate.");
            return;
        }

        String appId = (String) tableModel.getValueAt(row, 0);
        if ("victus-py".equals(appId)) {
            int conf = JOptionPane.showConfirmDialog(processTable,
                    "Terminate running Victus Python assistant process?",
                    "Confirm Terminate", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (conf == JOptionPane.YES_OPTION) {
                getOS().getVictusService().terminate(true);
                refreshTelemetry();
            }
            return;
        }

        List<OSApplication> apps = getOS().getApplicationManager().getRunningApps();
        for (OSApplication app : apps) {
            if (app.getDescriptor().getId().equals(appId)) {
                getOS().getApplicationManager().closeApp(app);
                refreshTelemetry();
                break;
            }
        }
    }

    @Override
    public void onClose() {
        if (refreshTimer != null && refreshTimer.isRunning()) refreshTimer.stop();
        if (graphTimer != null && graphTimer.isRunning()) graphTimer.stop();
    }
}

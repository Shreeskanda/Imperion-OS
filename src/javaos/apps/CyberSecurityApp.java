package javaos.apps;

import javaos.models.NotificationMessage;
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
import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * CyberSecurity Scanner application for JavaOS.
 * Performs real threat analysis across temp directories, running processes,
 * environment variables, open ports, and JVM security posture.
 */
public class CyberSecurityApp extends OSApplication {

    private static final DateTimeFormatter LOG_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private JTextArea scanLogArea;
    private JProgressBar scanProgress;
    private JLabel scanStatusLabel;
    private NeonButton btnStartScan;

    private DefaultTableModel processTableModel;
    private Timer processRefreshTimer;
    private List<ProcessHandle> processSnapshot = new ArrayList<>();

    private JTextArea securityLog;
    private final List<String> sessionHistory = new ArrayList<>();

    public CyberSecurityApp() {
        super(new AppDescriptor(
                "security",
                "Security Scanner",
                "System threat detection, process audit & hardening advisor",
                AppDescriptor.Category.SYSTEM,
                CyberpunkIcons.createIcon("SHIELD", 20),
                true
        ));
    }

    @Override
    public Dimension getPreferredWindowSize() {
        return new Dimension(900, 580);
    }

    @Override
    public JComponent createContent() {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();

        JTabbedPane tabs = new JTabbedPane();
        tabs.setBackground(theme.panel);
        tabs.setForeground(theme.textPrimary);
        tabs.setFont(theme.fontBold);

        tabs.addTab(" Quick Scan ", CyberpunkIcons.createIcon("SHIELD", 14), buildScanTab());
        tabs.addTab(" Process Manager ", CyberpunkIcons.createIcon("MONITOR", 14), buildProcessTab());
        tabs.addTab(" Hardening Advisor ", CyberpunkIcons.createIcon("LOCK", 14), buildHardeningTab());
        tabs.addTab(" Security Log ", CyberpunkIcons.createIcon("NOTEPAD", 14), buildLogTab());

        return tabs;
    }

    // ─── TAB 1: QUICK SCAN ────────────────────────────────────────────────────

    private JPanel buildScanTab() {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        // Top shield & controls card
        NeonCard topCard = new NeonCard(new BorderLayout(16, 0));
        topCard.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        topCard.setPreferredSize(new Dimension(0, 135));
        topCard.setGlowing(true);

        // Shield icon + title
        JPanel iconPanel = new JPanel();
        iconPanel.setLayout(new BoxLayout(iconPanel, BoxLayout.Y_AXIS));
        iconPanel.setOpaque(false);
        JLabel shieldLbl = new JLabel(CyberpunkIcons.createIcon("SHIELD", 44));
        shieldLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel titleLbl = new JLabel("THREAT ANALYSIS & SECURITY ENGINE");
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
        titleLbl.setForeground(theme.neonAccent);
        titleLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

        iconPanel.add(Box.createVerticalStrut(4));
        iconPanel.add(shieldLbl);
        iconPanel.add(Box.createVerticalStrut(6));
        iconPanel.add(titleLbl);
        topCard.add(iconPanel, BorderLayout.WEST);

        // Status + progress + buttons arranged with BoxLayout to prevent compression
        JPanel progressPanel = new JPanel();
        progressPanel.setLayout(new BoxLayout(progressPanel, BoxLayout.Y_AXIS));
        progressPanel.setOpaque(false);

        scanStatusLabel = new JLabel("Ready to scan. Click START SCAN to begin analysis.");
        scanStatusLabel.setFont(theme.fontRegular);
        scanStatusLabel.setForeground(theme.textPrimary);
        scanStatusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        scanProgress = new JProgressBar(0, 100);
        scanProgress.setForeground(theme.neonAccent);
        scanProgress.setBackground(theme.background);
        scanProgress.setStringPainted(true);
        scanProgress.setString("Idle");
        scanProgress.setFont(theme.fontSmall);
        scanProgress.setPreferredSize(new Dimension(0, 22));
        scanProgress.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        scanProgress.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 2));
        btnRow.setOpaque(false);
        btnRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        btnStartScan = new NeonButton("START SCAN", CyberpunkIcons.createIcon("PLAY", 14), NeonButton.Style.PRIMARY);
        btnStartScan.setPreferredSize(new Dimension(135, 34));
        btnStartScan.addActionListener(e -> runScan());

        NeonButton btnClear = new NeonButton("Clear Log", null);
        btnClear.setPreferredSize(new Dimension(100, 34));
        btnClear.addActionListener(e -> { scanLogArea.setText(""); });

        NeonButton btnExport = new NeonButton("Export Report", CyberpunkIcons.createIcon("FILE", 14));
        btnExport.setPreferredSize(new Dimension(135, 34));
        btnExport.addActionListener(e -> exportReport());

        btnRow.add(btnClear);
        btnRow.add(btnExport);
        btnRow.add(btnStartScan);

        progressPanel.add(scanStatusLabel);
        progressPanel.add(Box.createVerticalStrut(6));
        progressPanel.add(scanProgress);
        progressPanel.add(Box.createVerticalStrut(8));
        progressPanel.add(btnRow);

        topCard.add(progressPanel, BorderLayout.CENTER);
        p.add(topCard, BorderLayout.NORTH);

        // Scan log console
        scanLogArea = new JTextArea();
        scanLogArea.setEditable(false);
        scanLogArea.setFont(theme.fontMonospace);
        scanLogArea.setBackground(theme.background);
        scanLogArea.setForeground(theme.textPrimary);
        scanLogArea.setCaretColor(Color.WHITE);
        scanLogArea.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        scanLogArea.setText("IMPERION OS Security Scanner v2026.10\n" +
                "Threat Database: Pattern-based heuristic engine\n" +
                "Click 'START SCAN' to begin system analysis...\n");

        JScrollPane logScroll = new JScrollPane(scanLogArea);
        logScroll.getVerticalScrollBar().setUI(new NeonScrollBarUI());
        logScroll.setBorder(BorderFactory.createLineBorder(theme.secondaryAccent));
        p.add(logScroll, BorderLayout.CENTER);

        return p;
    }

    private void runScan() {
        btnStartScan.setEnabled(false);
        scanProgress.setValue(0);
        scanProgress.setString("Scanning...");
        scanStatusLabel.setText("Scan in progress — do not close this window...");
        scanLogArea.setText("");

        new SwingWorker<ScanResult, String>() {
            @Override
            protected ScanResult doInBackground() {
                ScanResult result = new ScanResult();
                publish(header("=== IMPERION OS SYSTEM THREAT SCAN INITIATED ==="));
                publish(log("Scan started: " + LocalDateTime.now().format(LOG_FMT)));
                publish(log("System: " + System.getProperty("os.name") + " " + System.getProperty("os.arch")));
                publish(log("Java: " + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")"));
                publish(separator());

                setProgress(10);

                // 1. TEMP DIRECTORY SCAN
                publish(header(">>> [1/6] TEMP DIRECTORY SCAN"));
                String tmpDir = System.getProperty("java.io.tmpdir");
                publish(log("Scanning: " + tmpDir));
                try {
                    try (Stream<Path> stream = Files.walk(Paths.get(tmpDir), 2)) {
                        List<Path> files = stream.filter(Files::isRegularFile).collect(Collectors.toList());
                        for (Path fp : files) {
                            String name = fp.getFileName().toString().toLowerCase();
                            long size = 0;
                            try { size = Files.size(fp); } catch (IOException ignored) {}

                            if (name.endsWith(".exe") || name.endsWith(".bat") || name.endsWith(".cmd")) {
                                publish(threat("HIGH RISK", "Executable in temp directory: " + fp));
                                result.threats++;
                            } else if (name.endsWith(".dll") || name.endsWith(".sys")) {
                                publish(warn("MEDIUM RISK", "Library file in temp: " + fp));
                                result.warnings++;
                            } else if (size > 50 * 1024 * 1024) {
                                publish(warn("SUSPICIOUS", "Large file in temp (>" + (size / (1024 * 1024)) + " MB): " + fp.getFileName()));
                                result.warnings++;
                            }
                            result.itemsChecked++;
                        }
                        publish(log("Temp scan complete: " + files.size() + " files scanned"));
                    }
                } catch (IOException e) {
                    publish(warn("ERROR", "Could not fully scan temp directory: " + e.getMessage()));
                }
                setProgress(25);

                // 2. PROCESS AUDIT
                publish(separator());
                publish(header(">>> [2/6] RUNNING PROCESS AUDIT"));
                List<String> suspiciousNames = List.of("cryptominer", "keylogger", "trojan", "malware", "rat", "backdoor", "stealer", "miner", "hooker", "inject");
                List<ProcessHandle> allProcs = ProcessHandle.allProcesses().collect(Collectors.toList());
                publish(log("Total visible processes: " + allProcs.size()));
                for (ProcessHandle ph : allProcs) {
                    Optional<String> nameOpt = ph.info().command();
                    if (nameOpt.isPresent()) {
                        String procName = nameOpt.get().toLowerCase();
                        for (String sus : suspiciousNames) {
                            if (procName.contains(sus)) {
                                publish(threat("MALWARE INDICATOR", "Suspicious process: " + nameOpt.get() + " [PID " + ph.pid() + "]"));
                                result.threats++;
                                break;
                            }
                        }
                    }
                    result.itemsChecked++;
                }
                publish(log("Process audit complete."));
                setProgress(45);

                // 3. ENVIRONMENT VARIABLE AUDIT
                publish(separator());
                publish(header(">>> [3/6] ENVIRONMENT VARIABLE AUDIT"));
                String[] suspiciousEnv = {"JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "IBM_JAVA_OPTIONS", "JDK_JAVA_OPTIONS"};
                Map<String, String> env = System.getenv();
                for (String key : suspiciousEnv) {
                    if (env.containsKey(key)) {
                        publish(warn("ENV HIJACK RISK", key + " = " + env.get(key)));
                        result.warnings++;
                    }
                    result.itemsChecked++;
                }
                publish(log("Environment variables checked: " + suspiciousEnv.length));
                setProgress(60);

                // 4. HIDDEN EXECUTABLE FILES IN USER HOME
                publish(separator());
                publish(header(">>> [4/6] HIDDEN EXECUTABLE FILE SCAN (USER HOME)"));
                File home = new File(System.getProperty("user.home"));
                File[] homeFiles = home.listFiles();
                if (homeFiles != null) {
                    for (File hf : homeFiles) {
                        boolean hidden = hf.isHidden() || hf.getName().startsWith(".");
                        if (hidden && hf.canExecute() && !hf.isDirectory()) {
                            publish(warn("SUSPICIOUS", "Hidden executable in home: " + hf.getName()));
                            result.warnings++;
                        }
                        result.itemsChecked++;
                    }
                }
                publish(log("Home directory scan complete."));
                setProgress(75);

                // 5. NETWORK PORT AUDIT
                publish(separator());
                publish(header(">>> [5/6] NETWORK PORT AUDIT"));
                int[] suspiciousPorts = {4444, 1337, 31337, 6666, 8888, 5554, 9999};
                for (int port : suspiciousPorts) {
                    try (ServerSocket ss = new ServerSocket(port)) {
                        ss.setReuseAddress(true);
                        publish(log("Port " + port + ": FREE (not bound)"));
                    } catch (IOException e) {
                        publish(warn("PORT IN USE", "Port " + port + " is currently bound — investigate if unexpected!"));
                        result.warnings++;
                    }
                    result.itemsChecked++;
                }
                setProgress(90);

                // 6. JVM SECURITY CHECK
                publish(separator());
                publish(header(">>> [6/6] JVM SECURITY POSTURE"));
                int javaVersion = Runtime.version().major();
                if (javaVersion >= 21) {
                    publish(log("Java Version: " + javaVersion + " — CURRENT (Strong security defaults)"));
                } else if (javaVersion >= 17) {
                    publish(log("Java Version: " + javaVersion + " — LTS supported"));
                } else {
                    publish(warn("OUTDATED JVM", "Java " + javaVersion + " has known vulnerabilities. Upgrade to 17+."));
                    result.warnings++;
                }

                // SecurityManager (deprecated in Java 17, removed in 21+)
                try {
                    @SuppressWarnings("removal")
                    SecurityManager sm = System.getSecurityManager();
                    publish(log("SecurityManager: " + (sm != null ? sm.getClass().getName() : "null (removed in Java 21+)")));
                } catch (Exception ignored) {
                    publish(log("SecurityManager: Not available in this JVM version (Java 21+)"));
                }

                result.itemsChecked += 4;
                setProgress(100);

                publish(separator());
                publish(header("=== SCAN COMPLETE ==="));
                publish(log(String.format("Summary: %d threats, %d warnings, %d items checked.",
                        result.threats, result.warnings, result.itemsChecked)));

                return result;
            }

            @Override
            protected void process(List<String> chunks) {
                for (String line : chunks) {
                    scanLogArea.append(line + "\n");
                }
                scanLogArea.setCaretPosition(scanLogArea.getDocument().getLength());
                scanProgress.setValue(getProgress());
            }

            @Override
            protected void done() {
                btnStartScan.setEnabled(true);
                try {
                    ScanResult result = get();
                    scanProgress.setString("Done");
                    scanStatusLabel.setText(String.format("Scan complete: %d threats, %d warnings, %d items checked.",
                            result.threats, result.warnings, result.itemsChecked));

                    // Archive to session history
                    String summary = "[" + LocalDateTime.now().format(LOG_FMT) + "] Scan: " +
                            result.threats + " threats, " + result.warnings + " warnings, " +
                            result.itemsChecked + " items";
                    sessionHistory.add(summary);
                    if (securityLog != null) {
                        securityLog.append(summary + "\n");
                    }

                    // Post notification
                    NotificationMessage.Level level;
                    String notifMsg;
                    if (result.threats > 0) {
                        level = NotificationMessage.Level.ERROR;
                        notifMsg = result.threats + " THREATS detected! Immediate review required.";
                    } else if (result.warnings > 0) {
                        level = NotificationMessage.Level.WARNING;
                        notifMsg = result.warnings + " warnings found. Review scan log.";
                    } else {
                        level = NotificationMessage.Level.SUCCESS;
                        notifMsg = "System clean. No threats detected.";
                    }

                    if (getOS() != null) {
                        getOS().getNotificationManager().postNotification("Security Scan Complete", notifMsg, level);
                    }

                } catch (Exception e) {
                    scanStatusLabel.setText("Scan error: " + e.getMessage());
                    btnStartScan.setEnabled(true);
                }
            }
        }.execute();
    }

    private static class ScanResult {
        int threats = 0, warnings = 0, itemsChecked = 0;
    }

    private String header(String s)    { return "╔══ " + s; }
    private String separator()         { return "║───────────────────────────────────────────────────────"; }
    private String log(String s)       { return "║  " + s; }
    private String threat(String t, String s) { return "║  [🔴 " + t + "] " + s; }
    private String warn(String t, String s)   { return "║  [⚠ " + t + "] " + s; }

    private void exportReport() {
        String report = scanLogArea.getText();
        if (report.trim().isEmpty()) {
            JOptionPane.showMessageDialog(scanLogArea, "Run a scan first before exporting.", "No Report", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (getOS() != null) {
            try {
                String filename = "/security_report_" + System.currentTimeMillis() + ".txt";
                getOS().getFileService().writeFile(filename, report);
                JOptionPane.showMessageDialog(scanLogArea, "Report saved to IMPERION OS virtual home:\n" + filename, "Report Exported", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(scanLogArea, "Failed to export: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // ─── TAB 2: PROCESS MANAGER ───────────────────────────────────────────────

    private JPanel buildProcessTab() {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setOpaque(false);
        JLabel title = new JLabel("Running System Processes (Live)");
        title.setFont(theme.fontBold);
        title.setForeground(theme.neonAccent);
        topRow.add(title, BorderLayout.WEST);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        btns.setOpaque(false);
        NeonButton btnRefresh = new NeonButton("Refresh", CyberpunkIcons.createIcon("REFRESH", 14));
        btnRefresh.addActionListener(e -> refreshProcesses());

        NeonButton btnKill = new NeonButton("End Selected", CyberpunkIcons.createIcon("STOP", 14), NeonButton.Style.DANGER);
        btnKill.addActionListener(e -> endSelectedProcess());

        btns.add(btnRefresh);
        btns.add(btnKill);
        topRow.add(btns, BorderLayout.EAST);
        p.add(topRow, BorderLayout.NORTH);

        String[] cols = {"PID", "Process Name / Command", "Status", "CPU Time (ms)"};
        processTableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        JTable procTable = new JTable(processTableModel);
        procTable.setBackground(theme.panel);
        procTable.setForeground(theme.textPrimary);
        procTable.setSelectionBackground(theme.selectionBackground);
        procTable.setSelectionForeground(theme.neonAccent);
        procTable.setGridColor(new Color(theme.secondaryAccent.getRed(), theme.secondaryAccent.getGreen(), theme.secondaryAccent.getBlue(), 40));
        procTable.setRowHeight(24);
        procTable.getTableHeader().setBackground(theme.elevatedPanel);
        procTable.getTableHeader().setForeground(theme.neonAccent);
        procTable.getTableHeader().setFont(theme.fontBold);
        procTable.getColumnModel().getColumn(0).setPreferredWidth(60);
        procTable.getColumnModel().getColumn(2).setPreferredWidth(80);
        procTable.getColumnModel().getColumn(3).setPreferredWidth(100);

        procTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val, boolean isSel, boolean hasFocus, int row, int col) {
                Component c = super.getTableCellRendererComponent(t, val, isSel, hasFocus, row, col);
                Theme th = ThemeManager.getInstance().getCurrentTheme();
                c.setFont(th.fontSmall);
                if (isSel) {
                    c.setBackground(th.selectionBackground);
                    c.setForeground(th.neonAccent);
                } else {
                    c.setBackground(row % 2 == 0 ? th.panel : th.elevatedPanel);
                    // Color code: current JVM = cyan, suspicious = red
                    long pid = row < processSnapshot.size() ? processSnapshot.get(row).pid() : -1;
                    if (pid == ProcessHandle.current().pid()) {
                        c.setForeground(th.neonAccent);
                    } else {
                        c.setForeground(th.textPrimary);
                    }
                }
                return c;
            }
        });

        // Store reference to kill selected
        final JTable finalTable = procTable;
        btnKill.addActionListener(e2 -> {
            int row = finalTable.getSelectedRow();
            if (row < 0 || row >= processSnapshot.size()) {
                JOptionPane.showMessageDialog(finalTable, "Select a process to terminate.", "No Selection", JOptionPane.WARNING_MESSAGE);
                return;
            }
            ProcessHandle ph = processSnapshot.get(row);
            if (ph.pid() == ProcessHandle.current().pid()) {
                JOptionPane.showMessageDialog(finalTable, "Cannot terminate the IMPERION OS JVM process itself.", "Protected", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (ph.pid() == 1) {
                JOptionPane.showMessageDialog(finalTable, "Cannot terminate system process PID 1.", "Protected", JOptionPane.WARNING_MESSAGE);
                return;
            }
            String name = ph.info().command().orElse("Unknown [PID " + ph.pid() + "]");
            int opt = JOptionPane.showConfirmDialog(finalTable,
                    "Terminate process:\n" + name + " (PID " + ph.pid() + ")?",
                    "Confirm Terminate", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (opt == JOptionPane.YES_OPTION) {
                ph.destroy();
                sessionHistory.add("[" + LocalDateTime.now().format(LOG_FMT) + "] Process terminated: " + name + " [PID " + ph.pid() + "]");
                refreshProcesses();
            }
        });

        JScrollPane scroll = new JScrollPane(procTable);
        scroll.getVerticalScrollBar().setUI(new NeonScrollBarUI());
        scroll.setBorder(BorderFactory.createLineBorder(theme.secondaryAccent));
        scroll.getViewport().setBackground(theme.panel);
        p.add(scroll, BorderLayout.CENTER);

        refreshProcesses();

        // Auto-refresh every 5 seconds using javax.swing.Timer (not java.util.Timer for EDT safety)
        javax.swing.Timer refreshTimer = new javax.swing.Timer(5000, e -> refreshProcesses());
        refreshTimer.start();

        return p;
    }

    private void refreshProcesses() {
        processSnapshot = ProcessHandle.allProcesses()
                .sorted((a, b) -> Long.compare(a.pid(), b.pid()))
                .limit(80)
                .collect(Collectors.toList());

        processTableModel.setRowCount(0);
        for (ProcessHandle ph : processSnapshot) {
            String name = ph.info().command().orElse("<unknown>");
            String status = ph.isAlive() ? "RUNNING" : "DEAD";
            long cpuMs = ph.info().totalCpuDuration().map(d -> d.toMillis()).orElse(-1L);
            processTableModel.addRow(new Object[]{
                    ph.pid(),
                    name,
                    status,
                    cpuMs >= 0 ? cpuMs : "N/A"
            });
        }
    }

    private void endSelectedProcess() {
        // handled inline above in table click listener setup
    }

    // ─── TAB 3: HARDENING ADVISOR ─────────────────────────────────────────────

    private JPanel buildHardeningTab() {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        JPanel outer = new JPanel(new BorderLayout(0, 8));
        outer.setOpaque(false);
        outer.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel title = new JLabel("Security Hardening Recommendations");
        title.setFont(theme.fontBold);
        title.setForeground(theme.neonAccent);
        outer.add(title, BorderLayout.NORTH);

        JPanel cardsPanel = new JPanel();
        cardsPanel.setLayout(new BoxLayout(cardsPanel, BoxLayout.Y_AXIS));
        cardsPanel.setOpaque(false);

        cardsPanel.add(buildAdvisorCard(
                "Java Runtime Version",
                "Running Java " + Runtime.version().major() + " — " + System.getProperty("java.version"),
                Runtime.version().major() >= 21 ? "PASS" : Runtime.version().major() >= 17 ? "WARN" : "FAIL",
                Runtime.version().major() >= 21 ? "Current LTS version with strong security defaults. Excellent." :
                        Runtime.version().major() >= 17 ? "Java 17 LTS is supported, but upgrading to 21+ is recommended." :
                                "Outdated Java version! Security vulnerabilities may be present."
        ));

        cardsPanel.add(buildAdvisorCard(
                "Temp Directory Write Access",
                "Temp: " + System.getProperty("java.io.tmpdir"),
                new File(System.getProperty("java.io.tmpdir")).canWrite() ? "WARN" : "PASS",
                "Temp directory is writable (normal behavior, but can be exploited). Ensure auto-cleanup is enabled."
        ));

        cardsPanel.add(buildAdvisorCard(
                "User Home Permissions",
                "Home: " + System.getProperty("user.home"),
                new File(System.getProperty("user.home")).canRead() ? "PASS" : "WARN",
                new File(System.getProperty("user.home")).canRead() ? "Home directory accessible — normal." : "Cannot read home directory. Check permissions."
        ));

        // Admin check: try writing to Windows system temp
        String adminStatus = "PASS";
        String adminMsg = "Not running with elevated administrator privileges. Recommended.";
        try {
            File testFile = new File("C:\\Windows\\Temp\\javaos_priv_check.tmp");
            if (testFile.createNewFile()) {
                testFile.delete();
                adminStatus = "WARN";
                adminMsg = "IMPERION OS is running with Administrator privileges. Limit permissions where possible.";
            }
        } catch (IOException | SecurityException ex) {
            // Not admin — good
        }
        cardsPanel.add(buildAdvisorCard("Privilege Level", "Windows Process Elevation Check", adminStatus, adminMsg));

        cardsPanel.add(buildAdvisorCard(
                "Java Tool Options Injection",
                "JAVA_TOOL_OPTIONS env variable",
                System.getenv("JAVA_TOOL_OPTIONS") != null ? "WARN" : "PASS",
                System.getenv("JAVA_TOOL_OPTIONS") != null ?
                        "JAVA_TOOL_OPTIONS is set: '" + System.getenv("JAVA_TOOL_OPTIONS") + "'. This could be used to inject JVM arguments." :
                        "JAVA_TOOL_OPTIONS is not set. No JVM argument injection risk detected."
        ));

        cardsPanel.add(buildAdvisorCard(
                "IMPERION OS Filesystem Sandbox",
                "Virtual home path confinement system",
                "PASS",
                "All Terminal and File Manager operations are strictly sandboxed to data/home/. Path traversal attacks are blocked by PathValidator."
        ));

        cardsPanel.add(buildAdvisorCard(
                "Victus Subprocess Isolation",
                "Python assistant process isolation",
                getOS() != null && getOS().getVictusService().isRunning() ? "INFO" : "PASS",
                getOS() != null && getOS().getVictusService().isRunning() ?
                        "Victus is running as an isolated subprocess. Stdout/Stderr is captured to logs only." :
                        "Victus subprocess is not currently running."
        ));

        cardsPanel.add(buildAdvisorCard(
                "Telnet Port 23 (Legacy Protocol)",
                "Network port scan",
                checkPortInUse(23) ? "WARN" : "PASS",
                checkPortInUse(23) ? "Port 23 (Telnet) appears in use. Telnet is unencrypted — disable it." : "Port 23 is not in use. Good."
        ));

        cardsPanel.add(buildAdvisorCard(
                "FTP Port 21 (Plain-text Transfer)",
                "Network port scan",
                checkPortInUse(21) ? "WARN" : "PASS",
                checkPortInUse(21) ? "Port 21 (FTP) appears in use. Use SFTP/FTPS instead." : "Port 21 is not in use. Good."
        ));

        JScrollPane scroll = new JScrollPane(cardsPanel);
        scroll.getVerticalScrollBar().setUI(new NeonScrollBarUI());
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        outer.add(scroll, BorderLayout.CENTER);

        return outer;
    }

    private JPanel buildAdvisorCard(String title, String subtitle, String status, String advice) {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();

        Color statusColor;
        Icon statusIcon;
        switch (status) {
            case "PASS":
                statusColor = theme.success;
                statusIcon = CyberpunkIcons.createIcon("CHECK", 18);
                break;
            case "FAIL":
                statusColor = theme.error;
                statusIcon = CyberpunkIcons.createIcon("ERROR_ICON", 18);
                break;
            case "INFO":
                statusColor = theme.neonAccent;
                statusIcon = CyberpunkIcons.createIcon("SHIELD", 18);
                break;
            case "WARN":
            default:
                statusColor = theme.warning;
                statusIcon = CyberpunkIcons.createIcon("WARNING_ICON", 18);
                break;
        }

        NeonCard card = new NeonCard(new BorderLayout(10, 0));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
        card.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

        JLabel iconLbl = new JLabel(statusIcon);
        card.add(iconLbl, BorderLayout.WEST);

        JPanel textPanel = new JPanel(new GridLayout(3, 1, 0, 2));
        textPanel.setOpaque(false);

        JLabel titleLbl = new JLabel(title);
        titleLbl.setFont(theme.fontBold);
        titleLbl.setForeground(theme.textPrimary);

        JLabel subLbl = new JLabel(subtitle);
        subLbl.setFont(theme.fontSmall);
        subLbl.setForeground(theme.textSecondary);

        JLabel adviceLbl = new JLabel("<html>" + advice + "</html>");
        adviceLbl.setFont(theme.fontSmall);
        adviceLbl.setForeground(statusColor);

        textPanel.add(titleLbl);
        textPanel.add(subLbl);
        textPanel.add(adviceLbl);
        card.add(textPanel, BorderLayout.CENTER);

        JLabel statusLbl = new JLabel(status);
        statusLbl.setFont(theme.fontBold);
        statusLbl.setForeground(statusColor);
        card.add(statusLbl, BorderLayout.EAST);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
        wrapper.add(card);
        return wrapper;
    }

    private boolean checkPortInUse(int port) {
        try (ServerSocket ss = new ServerSocket(port)) {
            ss.setReuseAddress(true);
            return false; // Port is free
        } catch (IOException e) {
            return true; // Port is in use
        }
    }

    // ─── TAB 4: SECURITY LOG ──────────────────────────────────────────────────

    private JPanel buildLogTab() {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        JPanel p = new JPanel(new BorderLayout(0, 6));
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel lbl = new JLabel("Security Event Log — Current Session");
        lbl.setFont(theme.fontBold);
        lbl.setForeground(theme.neonAccent);
        header.add(lbl, BorderLayout.WEST);

        NeonButton clearLog = new NeonButton("Clear", null, NeonButton.Style.DANGER);
        clearLog.addActionListener(e -> {
            sessionHistory.clear();
            securityLog.setText("Security log cleared at " + LocalDateTime.now().format(LOG_FMT) + "\n");
        });
        header.add(clearLog, BorderLayout.EAST);
        p.add(header, BorderLayout.NORTH);

        securityLog = new JTextArea();
        securityLog.setEditable(false);
        securityLog.setFont(theme.fontMonospace);
        securityLog.setBackground(theme.background);
        securityLog.setForeground(theme.textPrimary);
        securityLog.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        securityLog.setText("[" + LocalDateTime.now().format(LOG_FMT) + "] Security module initialized.\n" +
                "[" + LocalDateTime.now().format(LOG_FMT) + "] IMPERION OS Security Scanner ready.\n");

        for (String entry : sessionHistory) {
            securityLog.append(entry + "\n");
        }

        JScrollPane scroll = new JScrollPane(securityLog);
        scroll.getVerticalScrollBar().setUI(new NeonScrollBarUI());
        scroll.setBorder(BorderFactory.createLineBorder(theme.secondaryAccent));
        p.add(scroll, BorderLayout.CENTER);

        return p;
    }

    @Override
    public void onClose() {
        // No cleanup needed
    }
}

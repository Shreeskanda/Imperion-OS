package javaos.apps;

import javaos.core.JavaOS;
import javaos.services.NetworkScannerService;
import javaos.services.NetworkScannerService.WirelessAirDevice;
import javaos.services.ToolRegistry;
import javaos.services.ToolRegistry.Department;
import javaos.services.ToolRegistry.ToolInfo;
import javaos.ui.components.CyberpunkIcons;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * IMPERION CYBERCORE — Unified Cybersecurity Application Suite.
 * Integrates 10 security departments and 30+ tool panels with real subprocess execution.
 */
public class CyberCoreApp extends OSApplication {

    // ═══════════════════════════════════════════════════════════════════
    // Cyberpunk colour palette
    // ═══════════════════════════════════════════════════════════════════
    static final Color BG      = new Color(0x0B, 0x0F, 0x19);
    static final Color PANEL   = new Color(0x0F, 0x17, 0x26);
    static final Color PANEL2  = new Color(0x13, 0x1E, 0x30);
    static final Color CYAN    = new Color(0x22, 0xD3, 0xEE);
    static final Color BLUE    = new Color(0x3B, 0x82, 0xF6);
    static final Color PURPLE  = new Color(0xA8, 0x55, 0xF7);
    static final Color GREEN   = new Color(0x10, 0xB9, 0x81);
    static final Color RED     = new Color(0xEF, 0x44, 0x44);
    static final Color AMBER   = new Color(0xF5, 0x9E, 0x0B);
    static final Color TEXT    = new Color(0xF1, 0xF5, 0xF9);
    static final Color TEXT2   = new Color(0x94, 0xA3, 0xB8);
    static final Color BORDER  = new Color(0x22, 0xD3, 0xEE, 50);

    private static final Font FONT_MONO  = new Font("Consolas",  Font.PLAIN,  12);
    private static final Font FONT_BOLD  = new Font("Segoe UI",  Font.BOLD,   13);
    private static final Font FONT_REG   = new Font("Segoe UI",  Font.PLAIN,  13);
    private static final Font FONT_SMALL = new Font("Segoe UI",  Font.PLAIN,  11);

    private ToolRegistry registry;
    private JLabel toolCountLabel;
    private final Map<String, JButton> deptButtons = new LinkedHashMap<>();
    private JPanel cardPanel;
    private CardLayout cardLayout;
    private String activeDept = Department.NETWORKING.name();

    // ───────────────────────────────────────────────────────────────────
    public CyberCoreApp() {
        super(new AppDescriptor(
                "cybercore",
                "IMPERION CYBERCORE",
                "Unified Cybersecurity Suite — 10 departments, 30+ tools",
                AppDescriptor.Category.SYSTEM,
                CyberpunkIcons.createIcon("CYBERCORE", 20),
                true));
    }

    @Override
    public Dimension getPreferredWindowSize() { return new Dimension(1280, 820); }

    // ═══════════════════════════════════════════════════════════════════
    // createContent
    // ═══════════════════════════════════════════════════════════════════
    @Override
    public JComponent createContent() {
        // Initialise registry in background (can be slow due to process detection)
        registry = ToolRegistry.getInstance();

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG);

        root.add(buildSidebar(), BorderLayout.WEST);

        cardLayout = new CardLayout();
        cardPanel  = new JPanel(cardLayout);
        cardPanel.setBackground(BG);

        for (Department d : Department.values()) {
            JScrollPane scroll = buildDeptPanel(d);
            cardPanel.add(scroll, d.name());
        }

        root.add(cardPanel, BorderLayout.CENTER);

        // Show NETWORKING first
        cardLayout.show(cardPanel, Department.NETWORKING.name());
        styleActiveButton(Department.NETWORKING.name());

        // Update tool-count label after UI is built
        SwingUtilities.invokeLater(() -> {
            if (toolCountLabel != null)
                toolCountLabel.setText(registry.getInstalledCount() + "/" + registry.getTotalCount() + " tools detected");
        });

        return root;
    }

    // ═══════════════════════════════════════════════════════════════════
    // Sidebar
    // ═══════════════════════════════════════════════════════════════════
    private JPanel buildSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBackground(BG);
        sidebar.setPreferredSize(new Dimension(200, 0));
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, BORDER));

        // Header
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBackground(BG);
        header.setBorder(BorderFactory.createEmptyBorder(16, 14, 10, 8));
        header.setMaximumSize(new Dimension(200, 80));

        JLabel title = new JLabel("CYBERCORE");
        title.setFont(new Font("Segoe UI", Font.BOLD, 15));
        title.setForeground(CYAN);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel sub = new JLabel("IMPERION Security Suite");
        sub.setFont(FONT_SMALL);
        sub.setForeground(TEXT2);
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);

        header.add(title);
        header.add(Box.createVerticalStrut(2));
        header.add(sub);
        sidebar.add(header);

        // Separator
        JSeparator sep = new JSeparator();
        sep.setForeground(BORDER);
        sep.setBackground(BG);
        sep.setMaximumSize(new Dimension(200, 1));
        sidebar.add(sep);
        sidebar.add(Box.createVerticalStrut(6));

        // Department buttons
        String[][] depts = {
            {Department.NETWORKING.name(),   "[NET]", "Networking"},
            {Department.VULNERABILITY.name(),  "[VLN]", "Vulnerability"},
            {Department.PENTEST.name(),      "[PEN]", "Pen Testing"},
            {Department.WEB_SECURITY.name(), "[WEB]", "Web Security"},
            {Department.WIRELESS.name(),     "[AIR]", "Wireless"},
            {Department.PASSWORD.name(),     "[PWD]", "Password"},
            {Department.FORENSICS.name(),    "[FOR]", "Forensics"},
            {Department.MALWARE.name(),      "[MAL]", "Malware"},
            {Department.DEFENSIVE.name(),    "[DEF]", "Defensive"},
            {Department.OSINT.name(),        "[INT]", "OSINT"},
        };

        for (String[] d : depts) {
            JButton btn = makeDeptButton(d[0], d[1], d[2]);
            deptButtons.put(d[0], btn);
            sidebar.add(btn);
        }

        sidebar.add(Box.createVerticalGlue());

        // Tool count footer
        JSeparator sep2 = new JSeparator();
        sep2.setForeground(BORDER);
        sep2.setBackground(BG);
        sep2.setMaximumSize(new Dimension(200, 1));
        sidebar.add(sep2);

        toolCountLabel = new JLabel("Detecting tools…");
        toolCountLabel.setFont(FONT_SMALL);
        toolCountLabel.setForeground(TEXT2);
        toolCountLabel.setBorder(BorderFactory.createEmptyBorder(8, 14, 10, 8));
        toolCountLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(toolCountLabel);

        return sidebar;
    }

    private JButton makeDeptButton(String deptName, String tag, String label) {
        JButton btn = new JButton(tag + "  " + label) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                boolean selected = deptName.equals(activeDept);
                boolean hovered  = getModel().isRollover();
                if (selected) {
                    g2.setColor(PANEL2);
                    g2.fillRect(0, 0, getWidth(), getHeight());
                    g2.setColor(CYAN);
                    g2.fillRect(0, 0, 3, getHeight());
                } else if (hovered) {
                    g2.setColor(new Color(0x22, 0xD3, 0xEE, 20));
                    g2.fillRect(0, 0, getWidth(), getHeight());
                }
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(new Font("Consolas", Font.PLAIN, 12));
        btn.setForeground(TEXT2);
        btn.setBackground(new Color(0, 0, 0, 0));
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 8));
        btn.setMaximumSize(new Dimension(200, 38));
        btn.setPreferredSize(new Dimension(200, 38));

        btn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { btn.setForeground(CYAN); btn.repaint(); }
            @Override public void mouseExited(MouseEvent e)  {
                btn.setForeground(deptName.equals(activeDept) ? CYAN : TEXT2); btn.repaint(); }
        });

        btn.addActionListener(e -> {
            activeDept = deptName;
            cardLayout.show(cardPanel, deptName);
            styleActiveButton(deptName);
        });
        return btn;
    }

    private void styleActiveButton(String activeDeptName) {
        for (Map.Entry<String, JButton> e : deptButtons.entrySet()) {
            boolean active = e.getKey().equals(activeDeptName);
            e.getValue().setForeground(active ? CYAN : TEXT2);
            e.getValue().repaint();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Department panel builder
    // ═══════════════════════════════════════════════════════════════════
    private JScrollPane buildDeptPanel(Department dept) {
        List<ToolInfo> tools = registry.getToolsByDepartment(dept);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(BG);

        // Title bar
        content.add(buildDeptHeader(dept, tools));

        // Tool cards
        for (ToolInfo tool : tools) {
            content.add(Box.createVerticalStrut(6));
            content.add(buildToolCard(tool));
        }
        content.add(Box.createVerticalStrut(20));

        JScrollPane sp = new JScrollPane(content);
        sp.setBackground(BG);
        sp.getViewport().setBackground(BG);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.getVerticalScrollBar().setUnitIncrement(16);
        sp.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        sp.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        return sp;
    }

    private JPanel buildDeptHeader(Department dept, List<ToolInfo> tools) {
        long installed = tools.stream().filter(t -> t.installed).count();
        JPanel hdr = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 10));
        hdr.setBackground(PANEL);
        hdr.setMaximumSize(new Dimension(Integer.MAX_VALUE, 54));
        hdr.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER));

        JLabel name = new JLabel(prettyDept(dept));
        name.setFont(new Font("Segoe UI", Font.BOLD, 16));
        name.setForeground(CYAN);

        JLabel badge = new JLabel(installed + "/" + tools.size() + " tools");
        badge.setFont(FONT_SMALL);
        badge.setForeground(installed == tools.size() ? GREEN : (installed > 0 ? AMBER : RED));
        badge.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(installed == tools.size() ? GREEN : (installed > 0 ? AMBER : RED), 1, true),
                BorderFactory.createEmptyBorder(2, 6, 2, 6)));

        hdr.add(name);
        hdr.add(badge);
        return hdr;
    }

    // ═══════════════════════════════════════════════════════════════════
    // ToolCard
    // ═══════════════════════════════════════════════════════════════════
    private JPanel buildToolCard(ToolInfo tool) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(PANEL);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(0, 10, 0, 10),
                BorderFactory.createLineBorder(BORDER, 1, true)));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        // ── Header row ──────────────────────────────────────────────
        JPanel headerRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        headerRow.setBackground(PANEL2);
        headerRow.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel toolName = new JLabel(tool.name);
        toolName.setFont(new Font("Segoe UI", Font.BOLD, 13));
        toolName.setForeground(CYAN);

        JLabel statusBadge = statusBadge(tool);

        JLabel versionLbl = new JLabel(tool.installed ? tool.version : "");
        versionLbl.setFont(FONT_SMALL);
        versionLbl.setForeground(TEXT2);

        JLabel arrow = new JLabel("▶");
        arrow.setFont(FONT_SMALL);
        arrow.setForeground(TEXT2);

        headerRow.add(toolName);
        headerRow.add(statusBadge);
        if (!tool.version.isEmpty()) headerRow.add(versionLbl);
        headerRow.add(Box.createHorizontalGlue());
        headerRow.add(arrow);

        // ── Expandable body ─────────────────────────────────────────
        JPanel body = new JPanel(new BorderLayout());
        body.setBackground(PANEL);
        body.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        body.setVisible(false);

        // Config panel (tool-specific)
        JPanel cfg = buildConfigPanel(tool);

        // Output area
        JTextArea output = new JTextArea();
        output.setFont(FONT_MONO);
        output.setBackground(new Color(0x01, 0x04, 0x09));
        output.setForeground(CYAN);
        output.setCaretColor(CYAN);
        output.setEditable(false);
        output.setLineWrap(true);
        output.setWrapStyleWord(true);
        output.setText("[" + tool.name + "] — Output will appear here\n");

        JScrollPane outputScroll = new JScrollPane(output);
        outputScroll.setPreferredSize(new Dimension(0, 180));
        outputScroll.setBorder(BorderFactory.createLineBorder(BORDER, 1));
        outputScroll.setBackground(new Color(0x01, 0x04, 0x09));
        outputScroll.getViewport().setBackground(new Color(0x01, 0x04, 0x09));

        // Button row
        Process[] procRef   = {null};
        JButton btnExecute  = neonButton("▶  Execute", CYAN);
        JButton btnStop     = neonButton("■  Stop",   RED);
        JButton btnClear    = neonButton("⌫  Clear",  TEXT2);
        JButton btnSave     = neonButton("⬇  Save",   BLUE);
        btnStop.setEnabled(false);
        btnExecute.setEnabled(true);

        btnClear.addActionListener(e -> output.setText(""));
        btnSave.addActionListener(e -> saveOutput(output, tool.name));

        btnStop.addActionListener(e -> {
            if (procRef[0] != null) {
                procRef[0].destroy();
                procRef[0].destroyForcibly();
                appendOutput(output, "\n[Execution stopped by operator]\n");
            }
            btnStop.setEnabled(false);
            btnExecute.setEnabled(true);
        });

        // Wire Execute button to config panel via tag
        btnExecute.addActionListener(e -> {
            List<String> cmd = extractCommand(tool, cfg);
            if (cmd == null || cmd.isEmpty()) {
                appendOutput(output, "[Error] Could not build command. Check inputs.\n");
                return;
            }
            output.setText("$ " + String.join(" ", cmd) + "\n\n");
            btnExecute.setEnabled(false);
            btnStop.setEnabled(true);

            SwingWorker<Void, String> worker = new SwingWorker<>() {
                @Override protected Void doInBackground() throws Exception {
                    ProcessBuilder pb = new ProcessBuilder(cmd);
                    pb.redirectErrorStream(true);
                    try {
                        procRef[0] = pb.start();
                        try (BufferedReader br = new BufferedReader(
                                new InputStreamReader(procRef[0].getInputStream()))) {
                            String line;
                            while ((line = br.readLine()) != null) publish(line + "\n");
                        }
                        procRef[0].waitFor();
                        publish("\n[Process exited with return code: " + procRef[0].exitValue() + "]\n");
                    } catch (IOException ex) {
                        publish("[Execution Notice] System executable '" + tool.executable + "' was not found on PATH.\n\n");
                        if ("nmap".equals(tool.id)) {
                            publish(">>> Initiating Built-in IMPERION OS Port Diagnostic on Target...\n");
                            String targetIp = "127.0.0.1";
                            for (int i = 0; i < cmd.size(); i++) {
                                if (i == cmd.size() - 1 && !cmd.get(i).startsWith("-")) {
                                    targetIp = cmd.get(i);
                                }
                            }
                            publish("Target: " + targetIp + "\n");
                            try {
                                JavaOS os = JavaOS.getInstance();
                                if (os != null && os.getNetworkScannerService() != null) {
                                    List<NetworkScannerService.PortAuditResult> results = os.getNetworkScannerService().auditDevicePorts(targetIp);
                                    publish(String.format("%-8s %-12s %-20s %-22s\n", "PORT", "STATE", "SERVICE", "SECURITY STATUS"));
                                    publish("------------------------------------------------------------------\n");
                                    for (NetworkScannerService.PortAuditResult r : results) {
                                        publish(String.format("%-8d %-12s %-20s %-22s\n", r.port, r.state, r.serviceName, r.riskLevel));
                                    }
                                }
                            } catch (Exception auditEx) {
                                publish("[Diagnostic Error: " + auditEx.getMessage() + "]\n");
                            }
                            publish("\n[Host Integration Guide]\n");
                            publish("To run external native Nmap binary:\n");
                            publish("• Download installer: https://nmap.org/download.html\n");
                            publish("• CLI install: winget install Insecure.Nmap  or  choco install nmap\n");
                        } else if ("netcat".equals(tool.id)) {
                            publish(">>> Initiating Built-in TCP Connectivity Test...\n");
                            String targetHost = "127.0.0.1";
                            int targetPort = 80;
                            if (cmd.size() >= 3) {
                                targetHost = cmd.get(cmd.size() - 2);
                                try { targetPort = Integer.parseInt(cmd.get(cmd.size() - 1)); } catch (Exception ignored) {}
                            }
                            publish("Connecting to " + targetHost + ":" + targetPort + "...\n");
                            try (java.net.Socket s = new java.net.Socket()) {
                                long t0 = System.currentTimeMillis();
                                s.connect(new java.net.InetSocketAddress(targetHost, targetPort), 3000);
                                long dt = System.currentTimeMillis() - t0;
                                publish("Connection SUCCESS! Port " + targetPort + " is OPEN and listening (" + dt + "ms).\n");
                            } catch (Exception sockEx) {
                                publish("Connection REFUSED / CLOSED: " + sockEx.getMessage() + "\n");
                            }
                            publish("\nTo install external Netcat/Ncat: included with Nmap at https://nmap.org/ncat/\n");
                        } else {
                            publish("Official Installation:\n" + installHint(tool) + "\n");
                        }
                    }
                    return null;
                }
                @Override protected void process(List<String> chunks) {
                    for (String c : chunks) appendOutput(output, c);
                }
                @Override protected void done() {
                    btnExecute.setEnabled(true);
                    btnStop.setEnabled(false);
                }
            };
            worker.execute();
        });

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        btnRow.setBackground(PANEL);
        btnRow.add(btnExecute);
        btnRow.add(btnStop);
        btnRow.add(btnClear);
        btnRow.add(btnSave);

        body.add(cfg, BorderLayout.NORTH);
        body.add(Box.createVerticalStrut(8), BorderLayout.CENTER);

        JPanel outputPanel = new JPanel(new BorderLayout());
        outputPanel.setBackground(PANEL);
        outputPanel.add(btnRow,     BorderLayout.NORTH);
        outputPanel.add(outputScroll, BorderLayout.CENTER);
        body.add(outputPanel, BorderLayout.SOUTH);

        // Toggle expand
        headerRow.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                boolean vis = !body.isVisible();
                body.setVisible(vis);
                arrow.setText(vis ? "▼" : "▶");
                card.revalidate();
                card.repaint();
            }
        });

        card.add(headerRow, BorderLayout.NORTH);
        card.add(body,      BorderLayout.CENTER);
        return card;
    }

    // ═══════════════════════════════════════════════════════════════════
    // Per-tool config panels
    // ═══════════════════════════════════════════════════════════════════
    private JPanel buildConfigPanel(ToolInfo tool) {
        switch (tool.id) {
            case "nmap":        return cfgNmap();
            case "tshark":      return cfgTshark();
            case "wireshark":   return cfgWireshark();
            case "netcat":      return cfgNetcat();
            case "nuclei":      return cfgNuclei();
            case "nikto":       return cfgNikto();
            case "metasploit":  return cfgMetasploit();
            case "searchsploit":return cfgSearchsploit();
            case "burpsuite":   return cfgBurpsuite();
            case "owaspzap":    return cfgOwaspZap();
            case "sqlmap":      return cfgSqlmap();
            case "ffuf":        return cfgFfuf();
            case "aircrack":    return cfgAircrack();
            case "kismet":      return cfgKismet();
            case "john":        return cfgJohn();
            case "hashcat":     return cfgHashcat();
            case "keepassxc":   return cfgKeepassxc();
            case "autopsy":     return cfgAutopsy();
            case "volatility":  return cfgVolatility();
            case "exiftool":    return cfgExiftool();
            case "ghidra":      return cfgGhidra();
            case "yara":        return cfgYara();
            case "clamav":      return cfgClamav();
            case "cyberchef":   return cfgCyberchef();
            case "suricata":    return cfgWslNotice("Suricata", "suricata -i eth0");
            case "zeek":        return cfgWslNotice("Zeek", "zeek -i eth0");
            case "wazuh":       return cfgWazuh();
            case "sysmon":      return cfgSysmon();
            case "amass":       return cfgAmass();
            case "spiderfoot":  return cfgSpiderfoot();
            case "maltego":     return cfgMaltego();
            default:            return cfgGeneric(tool);
        }
    }

    // ─────────────── NMAP ───────────────────────────────────────────────
    private JPanel cfgNmap() {
        JPanel p = cfgGrid();
        p.putClientProperty("toolId", "nmap");

        JTextField target = field("127.0.0.1");
        JPanel targetSelector = createTargetDeviceSelector(target);

        JTextField ports  = field("80,443,8080");
        String[] scanTypes = {
            "TCP SYN (-sS)",
            "TCP Connect (-sT)",
            "UDP (-sU)",
            "Ping Sweep / Host Discovery (-sP)",
            "OS Detection (-O)",
            "Version Detection (-sV)",
            "Aggressive Scan (-A)",
            "Full Scan (-sV -sC -O -A)"
        };
        JComboBox<String> scanType = combo(scanTypes);
        JCheckBox verbose  = check("Verbose (-v)");
        JCheckBox noPing   = check("No ping (-Pn)", true);
        JCheckBox scripts  = check("Scripts (-sC)");
        JCheckBox timing   = check("Timing (-T4)", true);
        JComboBox<String> outFmt = combo(new String[]{"Normal", "XML (-oX)", "Grepable (-oG)"});

        scanType.addActionListener(e -> {
            String sel = (String) scanType.getSelectedItem();
            if (sel != null) {
                if (sel.contains("Ping Sweep")) {
                    ports.setEnabled(false);
                    ports.setText("N/A (Host Discovery)");
                } else {
                    ports.setEnabled(true);
                    if (ports.getText().contains("N/A")) ports.setText("80,443,8080");
                }
            }
        });

        addRow(p, "Target:", targetSelector);
        addRow(p, "Scan Profile:", scanType);
        addRow(p, "Ports:", ports);
        addRow(p, "Options:", hbox(verbose, noPing, scripts, timing));
        addRow(p, "Output:", outFmt);

        p.putClientProperty("cmdBuilder", (Supplier) () -> {
            List<String> cmd = new ArrayList<>();
            cmd.add("nmap");
            String sel = (String) scanType.getSelectedItem();
            if (sel != null) {
                if (sel.contains("-sS")) cmd.add("-sS");
                else if (sel.contains("-sT")) cmd.add("-sT");
                else if (sel.contains("-sU")) cmd.add("-sU");
                else if (sel.contains("-sP")) { cmd.add("-sn"); }
                else if (sel.contains("-O")) { cmd.add("-O"); }
                else if (sel.contains("-sV") && !sel.contains("Full")) cmd.add("-sV");
                else if (sel.contains("-A") && !sel.contains("Full")) cmd.add("-A");
                else if (sel.contains("Full")) { cmd.addAll(List.of("-sV", "-sC", "-O", "-A")); }
            }
            if (verbose.isSelected())  cmd.add("-v");
            if (noPing.isSelected() && (sel == null || !sel.contains("-sP"))) cmd.add("-Pn");
            if (scripts.isSelected() && (sel == null || !sel.contains("Full"))) cmd.add("-sC");
            if (timing.isSelected())   cmd.add("-T4");
            String pstr = ports.getText().trim();
            if (!pstr.isEmpty() && !pstr.contains("N/A") && (sel == null || !sel.contains("-sP"))) {
                cmd.add("-p"); cmd.add(pstr);
            }
            String fmt = (String) outFmt.getSelectedItem();
            if (fmt != null && fmt.contains("-oX")) { cmd.add("-oX"); cmd.add("nmap_out.xml"); }
            else if (fmt != null && fmt.contains("-oG")) { cmd.add("-oG"); cmd.add("nmap_out.txt"); }
            String tgt = target.getText().trim();
            if (!tgt.isEmpty()) cmd.add(tgt);
            return cmd;
        });
        return p;
    }

    // ─────────────── TSHARK ─────────────────────────────────────────────
    private JPanel cfgTshark() {
        JPanel p = cfgGrid();
        p.putClientProperty("toolId", "tshark");

        JTextField iface    = field(getDefaultInterface());
        JTextField capFilt  = field("tcp port 80 or port 443");
        JTextField dispFilt = field("http or dns");
        JSpinner count      = spinner(50, 0, 100000);
        JSpinner timeout    = spinner(0, 0, 3600);
        JButton refresh     = smallBtn("Refresh Interfaces");

        refresh.addActionListener(e -> {
            SwingWorker<String, Void> w = new SwingWorker<>() {
                @Override protected String doInBackground() {
                    try {
                        String tsharkBin = registry.getTool("tshark").resolvedPath;
                        if (tsharkBin == null || tsharkBin.isEmpty()) tsharkBin = "tshark";
                        Process pp = new ProcessBuilder(tsharkBin, "-D").redirectErrorStream(true).start();
                        try (BufferedReader br = new BufferedReader(new InputStreamReader(pp.getInputStream()))) {
                            StringBuilder sb = new StringBuilder();
                            String ln;
                            while ((ln = br.readLine()) != null) sb.append(ln).append("\n");
                            return sb.toString();
                        }
                    } catch (Exception ex) {
                        return "1. " + getDefaultInterface();
                    }
                }
                @Override protected void done() {
                    try {
                        String res = get();
                        String first = res.lines().findFirst().map(s -> s.replaceAll("^\\d+\\.\\s*", "").trim()).orElse("1");
                        iface.setText(first.isEmpty() ? "1" : first);
                    } catch (Exception ignored) {
                        iface.setText("1");
                    }
                }
            };
            w.execute();
        });

        addRow(p, "Interface:", hbox(iface, refresh));
        addRow(p, "Capture Filter:", capFilt);
        addRow(p, "Display Filter:", dispFilt);
        addRow(p, "Packet Count:", count);
        addRow(p, "Timeout (s):", timeout);

        p.putClientProperty("cmdBuilder", (Supplier) () -> {
            List<String> cmd = new ArrayList<>(List.of("tshark"));
            String ifStr = iface.getText().trim();
            if (!ifStr.isEmpty()) { cmd.add("-i"); cmd.add(ifStr); }
            String cfStr = capFilt.getText().trim();
            if (!cfStr.isEmpty()) { cmd.add("-f"); cmd.add(cfStr); }
            String dfStr = dispFilt.getText().trim();
            if (!dfStr.isEmpty()) { cmd.add("-Y"); cmd.add(dfStr); }
            int cnt = (Integer) count.getValue();
            if (cnt > 0) { cmd.add("-c"); cmd.add(String.valueOf(cnt)); }
            return cmd;
        });
        return p;
    }

    // ─────────────── WIRESHARK ──────────────────────────────────────────
    private JPanel cfgWireshark() {
        JPanel p = cfgGrid();
        p.putClientProperty("toolId", "wireshark");

        JTextField iface = field(getDefaultInterface());
        JCheckBox autoCapture = check("Auto-capture on launch (-k)");

        addRow(p, "Interface:", iface);
        addRow(p, "Options:", autoCapture);

        infoLabel(p, "Wireshark will launch as a separate GUI application.");

        p.putClientProperty("cmdBuilder", (Supplier) () -> {
            List<String> cmd = new ArrayList<>(List.of("wireshark"));
            if (autoCapture.isSelected() && !iface.getText().trim().isEmpty()) {
                cmd.add("-i"); cmd.add(iface.getText().trim()); cmd.add("-k");
            }
            return cmd;
        });
        return p;
    }

    // ─────────────── NETCAT ─────────────────────────────────────────────
    private JPanel cfgNetcat() {
        JPanel p = cfgGrid();
        p.putClientProperty("toolId", "netcat");

        JRadioButton modeConnect = new JRadioButton("Connect");
        JRadioButton modeListen  = new JRadioButton("Listen");
        modeConnect.setForeground(TEXT); modeConnect.setBackground(PANEL); modeConnect.setSelected(true);
        modeListen.setForeground(TEXT);  modeListen.setBackground(PANEL);
        ButtonGroup bg = new ButtonGroup();
        bg.add(modeConnect); bg.add(modeListen);

        JTextField host  = field("127.0.0.1");
        JPanel hostSelector = createTargetDeviceSelector(host);
        JTextField port  = field("4444");
        JComboBox<String> proto = combo(new String[]{"TCP", "UDP"});

        addRow(p, "Mode:", hbox(modeConnect, modeListen));
        addRow(p, "Host / Device:", hostSelector);
        addRow(p, "Port:", port);
        addRow(p, "Protocol:", proto);

        p.putClientProperty("cmdBuilder", (Supplier) () -> {
            List<String> cmd = new ArrayList<>(List.of("ncat", "-v"));
            if ("UDP".equals(proto.getSelectedItem())) cmd.add("-u");
            if (modeListen.isSelected()) {
                cmd.add("-l"); cmd.add("-p"); cmd.add(port.getText().trim());
            } else {
                cmd.add(host.getText().trim()); cmd.add(port.getText().trim());
            }
            return cmd;
        });
        return p;
    }

    // ─────────────── NUCLEI ─────────────────────────────────────────────
    private JPanel cfgNuclei() {
        JPanel p = cfgGrid();
        p.putClientProperty("toolId", "nuclei");

        JTextField target   = field("http://127.0.0.1:8080");
        JPanel targetSelector = createTargetDeviceSelector(target);
        JTextField tmplDir  = field("");
        JButton browse      = smallBtn("Browse");
        JTextField tagsField = field("");
        JSpinner rate       = spinner(150, 1, 1000);
        JCheckBox sevInfo   = check("info");
        JCheckBox sevLow    = check("low");
        JCheckBox sevMed    = check("medium", true);
        JCheckBox sevHigh   = check("high",   true);
        JCheckBox sevCrit   = check("critical", true);

        browse.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (fc.showOpenDialog(null) == JFileChooser.APPROVE_OPTION)
                tmplDir.setText(fc.getSelectedFile().getAbsolutePath());
        });

        addRow(p, "Target URL / Device:", targetSelector);
        addRow(p, "Templates:", hbox(tmplDir, browse));
        addRow(p, "Severity:", hbox(sevInfo, sevLow, sevMed, sevHigh, sevCrit));
        addRow(p, "Tags:", tagsField);
        addRow(p, "Rate Limit:", rate);

        p.putClientProperty("cmdBuilder", (Supplier) () -> {
            List<String> cmd = new ArrayList<>(List.of("nuclei"));
            String tgt = target.getText().trim();
            if (!tgt.isEmpty()) { cmd.add("-u"); cmd.add(tgt); }
            String tdir = tmplDir.getText().trim();
            if (!tdir.isEmpty()) { cmd.add("-t"); cmd.add(tdir); }
            List<String> sevs = new ArrayList<>();
            if (sevInfo.isSelected()) sevs.add("info");
            if (sevLow.isSelected())  sevs.add("low");
            if (sevMed.isSelected())  sevs.add("medium");
            if (sevHigh.isSelected()) sevs.add("high");
            if (sevCrit.isSelected()) sevs.add("critical");
            if (!sevs.isEmpty()) { cmd.add("-severity"); cmd.add(String.join(",", sevs)); }
            String tags = tagsField.getText().trim();
            if (!tags.isEmpty()) { cmd.add("-tags"); cmd.add(tags); }
            cmd.add("-rate-limit"); cmd.add(rate.getValue().toString());
            return cmd;
        });
        return p;
    }

    // ─────────────── NIKTO ──────────────────────────────────────────────
    private JPanel cfgNikto() {
        JPanel p = cfgGrid();
        p.putClientProperty("toolId", "nikto");

        JTextField target  = field("http://127.0.0.1");
        JPanel targetSelector = createTargetDeviceSelector(target);
        JCheckBox ssl      = check("Force SSL");
        JTextField tuning  = field("1,2,3,4,5,6,7,8,9");
        JSpinner timeout   = spinner(10, 1, 300);

        addRow(p, "Target URL / Device:", targetSelector);
        addRow(p, "SSL:", ssl);
        addRow(p, "Tuning:", tuning);
        addRow(p, "Timeout (s):", timeout);

        p.putClientProperty("cmdBuilder", (Supplier) () -> {
            List<String> cmd = new ArrayList<>(List.of("nikto", "-h", target.getText().trim()));
            if (ssl.isSelected()) cmd.add("-ssl");
            String t = tuning.getText().trim();
            if (!t.isEmpty()) { cmd.add("-tuning"); cmd.add(t); }
            cmd.add("-timeout"); cmd.add(timeout.getValue().toString());
            return cmd;
        });
        return p;
    }

    // ─────────────── METASPLOIT ─────────────────────────────────────────
    private JPanel cfgMetasploit() {
        JPanel p = cfgGrid();
        p.putClientProperty("toolId", "metasploit");

        infoLabel(p, "⚠️  For authorized penetration testing only. Misuse is illegal.");
        JTextField moduleSearch = field("Search module keyword…");
        JTextField moduleCmd    = field("");
        JButton btnSearch       = smallBtn("Search Module");

        btnSearch.addActionListener(e -> {
            String kw = moduleSearch.getText().trim();
            if (!kw.isEmpty()) moduleCmd.setText("search " + kw + "; exit");
        });

        addRow(p, "Module Search:", hbox(moduleSearch, btnSearch));
        addRow(p, "MSF Command:", moduleCmd);

        p.putClientProperty("cmdBuilder", (Supplier) () -> {
            String cmd2 = moduleCmd.getText().trim();
            if (cmd2.isEmpty()) return List.of("msfconsole");
            return List.of("msfconsole", "-x", cmd2);
        });
        return p;
    }

    // ─────────────── SEARCHSPLOIT ───────────────────────────────────────
    private JPanel cfgSearchsploit() {
        JPanel p = cfgGrid();
        p.putClientProperty("toolId", "searchsploit");

        JTextField keyword = field("Apache 2.4");
        JTextField cve     = field("");
        JComboBox<String> type = combo(new String[]{"All", "Exploits", "Shellcodes", "Papers"});
        JCheckBox copyExploit  = check("Copy exploit path");

        addRow(p, "Keyword:", keyword);
        addRow(p, "CVE:", cve);
        addRow(p, "Type:", type);
        addRow(p, "Options:", copyExploit);

        p.putClientProperty("cmdBuilder", (Supplier) () -> {
            List<String> cmd = new ArrayList<>(List.of("searchsploit"));
            String cveStr = cve.getText().trim();
            String kw     = keyword.getText().trim();
            if (!cveStr.isEmpty()) { cmd.add("--cve"); cmd.add(cveStr); }
            else if (!kw.isEmpty()) cmd.add(kw);
            String sel = (String) type.getSelectedItem();
            if ("Exploits".equals(sel))   cmd.add("--exploit");
            else if ("Shellcodes".equals(sel)) cmd.add("--shellcode");
            else if ("Papers".equals(sel)) cmd.add("--webshell");
            return cmd;
        });
        return p;
    }

    // ─────────────── BURP SUITE ─────────────────────────────────────────
    private JPanel cfgBurpsuite() {
        JPanel p = cfgGrid();
        p.putClientProperty("toolId", "burpsuite");

        // Detect jar
        String[] searchPaths = {
            System.getenv("ProgramFiles") + "\\BurpSuite",
            System.getProperty("user.home") + "\\BurpSuite",
            "C:\\Program Files\\BurpSuite Community Edition"
        };
        JTextField jarPath = field("");
        for (String sp : searchPaths) {
            if (sp == null) continue;
            File dir = new File(sp);
            if (dir.exists()) {
                File[] jars = dir.listFiles(f -> f.getName().matches("burpsuite.*\\.jar"));
                if (jars != null && jars.length > 0) { jarPath.setText(jars[0].getAbsolutePath()); break; }
            }
        }
        JButton browseJar = smallBtn("Browse");
        browseJar.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileFilter(new FileNameExtensionFilter("JAR files", "jar"));
            if (fc.showOpenDialog(null) == JFileChooser.APPROVE_OPTION)
                jarPath.setText(fc.getSelectedFile().getAbsolutePath());
        });

        addRow(p, "Burp JAR:", hbox(jarPath, browseJar));
        infoLabel(p, "Proxy will listen on localhost:8080 (configure in Burp settings).");

        p.putClientProperty("cmdBuilder", (Supplier) () -> {
            String jar = jarPath.getText().trim();
            if (!jar.isEmpty()) return List.of("java", "-jar", jar);
            return List.of("burpsuite");
        });
        return p;
    }

    // ─────────────── OWASP ZAP ──────────────────────────────────────────
    private JPanel cfgOwaspZap() {
        JPanel p = cfgGrid();
        p.putClientProperty("toolId", "owaspzap");

        JTextField zapUrl  = field("http://localhost:8080");
        JTextField apiKey  = field("");
        JTextField scanTgt = field("https://example.com");
        JButton btnLaunch  = smallBtn("Launch ZAP");
        JButton btnSpider  = smallBtn("Start Spider");
        JButton btnActive  = smallBtn("Start Active Scan");

        btnLaunch.addActionListener(e -> {
            try { Desktop.getDesktop().browse(new URI("http://localhost:8080")); } catch (Exception ex) { /* ignore */ }
        });

        btnSpider.addActionListener(e -> {
            runZapApiCall(zapUrl.getText().trim(), apiKey.getText().trim(),
                "spider/action/scan/?url=" + scanTgt.getText().trim(), p);
        });
        btnActive.addActionListener(e -> {
            runZapApiCall(zapUrl.getText().trim(), apiKey.getText().trim(),
                "ascan/action/scan/?url=" + scanTgt.getText().trim(), p);
        });

        addRow(p, "ZAP URL:", zapUrl);
        addRow(p, "API Key:", apiKey);
        addRow(p, "Scan Target:", scanTgt);
        addRow(p, "Actions:", hbox(btnLaunch, btnSpider, btnActive));

        p.putClientProperty("cmdBuilder", (Supplier) () -> List.of("zap.sh", "-daemon", "-port", "8080"));
        return p;
    }

    private void runZapApiCall(String base, String key, String endpoint, JPanel parent) {
        SwingWorker<String, Void> w = new SwingWorker<>() {
            @Override protected String doInBackground() throws Exception {
                String urlStr = base + "/JSON/" + endpoint + "&apikey=" + key;
                HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(10000);
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    StringBuilder sb = new StringBuilder();
                    String ln;
                    while ((ln = br.readLine()) != null) sb.append(ln).append("\n");
                    return sb.toString();
                }
            }
            @Override protected void done() {
                try { JOptionPane.showMessageDialog(parent, get(), "ZAP Response", JOptionPane.INFORMATION_MESSAGE); }
                catch (Exception ex) { JOptionPane.showMessageDialog(parent, "ZAP call failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE); }
            }
        };
        w.execute();
    }

    // ─────────────── SQLMAP ─────────────────────────────────────────────
    private JPanel cfgSqlmap() {
        JPanel p = cfgGrid();
        p.putClientProperty("toolId", "sqlmap");

        JTextField url    = field("http://example.com/page?id=1");
        JComboBox<String> method = combo(new String[]{"GET", "POST"});
        JTextField data   = field("");
        JSpinner level    = spinner(1, 1, 5);
        JSpinner risk     = spinner(1, 1, 3);
        JComboBox<String> dbms = combo(new String[]{"Auto", "MySQL", "PostgreSQL", "MSSQL", "Oracle", "SQLite"});
        JCheckBox cbB = check("Boolean-based (B)");
        JCheckBox cbE = check("Error-based (E)");
        JCheckBox cbU = check("Union-based (U)");
        JCheckBox cbS = check("Stacked (S)");
        JCheckBox cbT = check("Time-based (T)", true);

        addRow(p, "Target URL:", url);
        addRow(p, "Method:", method);
        addRow(p, "POST Data:", data);
        addRow(p, "Level:", level);
        addRow(p, "Risk:", risk);
        addRow(p, "DBMS:", dbms);
        addRow(p, "Techniques:", hbox(cbB, cbE, cbU, cbS, cbT));

        p.putClientProperty("cmdBuilder", (Supplier) () -> {
            List<String> cmd = new ArrayList<>(List.of("sqlmap", "-u", url.getText().trim(), "--batch"));
            String m = (String) method.getSelectedItem();
            if ("POST".equals(m)) {
                cmd.add("--method"); cmd.add("POST");
                String d = data.getText().trim();
                if (!d.isEmpty()) { cmd.add("--data"); cmd.add(d); }
            }
            cmd.add("--level"); cmd.add(level.getValue().toString());
            cmd.add("--risk");  cmd.add(risk.getValue().toString());
            String db = (String) dbms.getSelectedItem();
            if (!"Auto".equals(db)) { cmd.add("--dbms"); cmd.add(db); }
            StringBuilder tech = new StringBuilder();
            if (cbB.isSelected()) tech.append("B");
            if (cbE.isSelected()) tech.append("E");
            if (cbU.isSelected()) tech.append("U");
            if (cbS.isSelected()) tech.append("S");
            if (cbT.isSelected()) tech.append("T");
            if (tech.length() > 0) { cmd.add("--technique"); cmd.add(tech.toString()); }
            return cmd;
        });
        return p;
    }

    // ─────────────── FFUF ───────────────────────────────────────────────
    private JPanel cfgFfuf() {
        JPanel p = cfgGrid();
        p.putClientProperty("toolId", "ffuf");

        JTextField url      = field("https://example.com/FUZZ");
        JTextField wordlist = field("");
        JButton browse      = smallBtn("Browse");
        JTextField fc       = field("404,500");
        JSpinner threads    = spinner(40, 1, 200);

        browse.addActionListener(e -> {
            JFileChooser fc2 = new JFileChooser();
            if (fc2.showOpenDialog(null) == JFileChooser.APPROVE_OPTION)
                wordlist.setText(fc2.getSelectedFile().getAbsolutePath());
        });

        addRow(p, "URL (FUZZ keyword):", url);
        addRow(p, "Wordlist:", hbox(wordlist, browse));
        addRow(p, "Filter codes:", fc);
        addRow(p, "Threads:", threads);

        p.putClientProperty("cmdBuilder", (Supplier) () ->
            List.of("ffuf", "-u", url.getText().trim(), "-w", wordlist.getText().trim(),
                    "-fc", fc.getText().trim(), "-t", threads.getValue().toString()));
        return p;
    }

    // ─────────────── AIRCRACK ───────────────────────────────────────────
    private JPanel cfgAircrack() {
        JPanel p = cfgGrid();
        p.putClientProperty("toolId", "aircrack");

        boolean isWin = System.getProperty("os.name","").toLowerCase().contains("win");
        if (isWin) {
            infoLabel(p, "⚠️  Aircrack-ng requires Linux for full wireless capture. Use WSL for live capture.");
            infoLabel(p, "    Windows: You can analyze existing .cap files directly.");
        }
        JTextField capFile  = field("");
        JTextField wordlist = field("");
        JButton browseC = smallBtn("Browse .cap");
        JButton browseW = smallBtn("Browse Wordlist");
        browseC.addActionListener(e -> filePick(capFile, false));
        browseW.addActionListener(e -> filePick(wordlist, false));

        JCheckBox useWsl = new JCheckBox("Launch via WSL");
        useWsl.setForeground(TEXT); useWsl.setBackground(PANEL);

        addRow(p, "Cap File:", hbox(capFile, browseC));
        addRow(p, "Wordlist:", hbox(wordlist, browseW));
        if (isWin) addRow(p, "WSL:", useWsl);

        p.putClientProperty("cmdBuilder", (Supplier) () -> {
            List<String> cmd = new ArrayList<>();
            if (isWin && useWsl.isSelected()) {
                cmd.add("wsl"); cmd.add("aircrack-ng");
            } else {
                cmd.add("aircrack-ng");
            }
            cmd.add(capFile.getText().trim());
            String wl = wordlist.getText().trim();
            if (!wl.isEmpty()) { cmd.add("-w"); cmd.add(wl); }
            return cmd;
        });
        return p;
    }

    // ─────────────── KISMET ─────────────────────────────────────────────
    private JPanel cfgKismet() {
        JPanel p = cfgGrid();
        p.putClientProperty("toolId", "kismet");
        return cfgWslNotice("Kismet", "kismet");
    }

    // ─────────────── JOHN ───────────────────────────────────────────────
    private JPanel cfgJohn() {
        JPanel p = cfgGrid();
        p.putClientProperty("toolId", "john");

        JTextField hashFile = field("");
        JTextField wordlist = field("");
        JButton browseH = smallBtn("Browse Hash");
        JButton browseW = smallBtn("Browse Wordlist");
        browseH.addActionListener(e -> filePick(hashFile, false));
        browseW.addActionListener(e -> filePick(wordlist, false));

        String[] formats = {"Auto-detect", "md5", "sha1", "sha256", "bcrypt", "ntlm", "descrypt", "afs"};
        JComboBox<String> format = combo(formats);
        JTextField rules  = field("");

        addRow(p, "Hash File:", hbox(hashFile, browseH));
        addRow(p, "Wordlist:", hbox(wordlist, browseW));
        addRow(p, "Format:", format);
        addRow(p, "Rules:", rules);

        p.putClientProperty("cmdBuilder", (Supplier) () -> {
            List<String> cmd = new ArrayList<>(List.of("john"));
            String fmt = (String) format.getSelectedItem();
            if (!"Auto-detect".equals(fmt)) cmd.add("--format=" + fmt);
            String wl = wordlist.getText().trim();
            if (!wl.isEmpty()) cmd.add("--wordlist=" + wl);
            String r = rules.getText().trim();
            if (!r.isEmpty()) cmd.add("--rules=" + r);
            cmd.add(hashFile.getText().trim());
            return cmd;
        });
        return p;
    }

    // ─────────────── HASHCAT ────────────────────────────────────────────
    private JPanel cfgHashcat() {
        JPanel p = cfgGrid();
        p.putClientProperty("toolId", "hashcat");

        JTextField hashFile = field("");
        JTextField wordlist = field("");
        JButton browseH = smallBtn("Browse Hash");
        JButton browseW = smallBtn("Browse Wordlist");
        browseH.addActionListener(e -> filePick(hashFile, false));
        browseW.addActionListener(e -> filePick(wordlist, false));

        String[] hashTypes = {"0 — MD5", "100 — SHA1", "1000 — NTLM", "1800 — sha512crypt", "3200 — bcrypt", "5600 — NetNTLMv2"};
        JComboBox<String> hashType = combo(hashTypes);
        String[] attackModes = {"0 — Straight (Wordlist)", "1 — Combination", "3 — Brute-force (Mask)", "6 — Hybrid"};
        JComboBox<String> attackMode = combo(attackModes);
        JTextField mask = field("?a?a?a?a?a?a?a?a");

        addRow(p, "Hash File:", hbox(hashFile, browseH));
        addRow(p, "Wordlist:", hbox(wordlist, browseW));
        addRow(p, "Hash Type:", hashType);
        addRow(p, "Attack Mode:", attackMode);
        addRow(p, "Mask:", mask);

        p.putClientProperty("cmdBuilder", (Supplier) () -> {
            String htStr = ((String) hashType.getSelectedItem()).split(" ")[0];
            String amStr = ((String) attackMode.getSelectedItem()).split(" ")[0];
            List<String> cmd = new ArrayList<>(List.of("hashcat", "-m", htStr, "-a", amStr, hashFile.getText().trim()));
            if ("3".equals(amStr)) cmd.add(mask.getText().trim());
            else { String wl = wordlist.getText().trim(); if (!wl.isEmpty()) cmd.add(wl); }
            return cmd;
        });
        return p;
    }

    // ─────────────── KEEPASSXC ──────────────────────────────────────────
    private JPanel cfgKeepassxc() {
        JPanel p = cfgGrid();
        p.putClientProperty("toolId", "keepassxc");

        // Detect install
        String[] paths = {"C:\\Program Files\\KeePassXC\\KeePassXC.exe",
                          "C:\\Program Files (x86)\\KeePassXC\\KeePassXC.exe"};
        JTextField installPath = field("");
        for (String path : paths) {
            if (new File(path).exists()) { installPath.setText(path); break; }
        }
        JButton browse = smallBtn("Browse");
        browse.addActionListener(e -> {
            JFileChooser fc = new JFileChooser(); fc.setFileFilter(new FileNameExtensionFilter("Executables", "exe"));
            if (fc.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) installPath.setText(fc.getSelectedFile().getAbsolutePath());
        });

        addRow(p, "KeePassXC Path:", hbox(installPath, browse));
        infoLabel(p, "KeePassXC is an open-source password manager. Launch to manage your vault.");

        p.putClientProperty("cmdBuilder", (Supplier) () -> {
            String path = installPath.getText().trim();
            return List.of(path.isEmpty() ? "keepassxc" : path);
        });
        return p;
    }

    // ─────────────── AUTOPSY ────────────────────────────────────────────
    private JPanel cfgAutopsy() {
        JPanel p = cfgGrid();
        p.putClientProperty("toolId", "autopsy");

        // Try to detect Autopsy in common Windows paths
        File autopsyDir = null;
        File programFiles = new File(System.getenv().getOrDefault("ProgramFiles", "C:\\Program Files"));
        if (programFiles.exists()) {
            File[] dirs = programFiles.listFiles(f -> f.isDirectory() && f.getName().startsWith("Autopsy"));
            if (dirs != null && dirs.length > 0) autopsyDir = dirs[0];
        }
        JTextField installPath = field(autopsyDir != null ? autopsyDir.getAbsolutePath() : "");
        JButton browse = smallBtn("Browse");
        browse.addActionListener(e -> {
            JFileChooser fc = new JFileChooser(); fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (fc.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) installPath.setText(fc.getSelectedFile().getAbsolutePath());
        });

        addRow(p, "Autopsy Directory:", hbox(installPath, browse));
        infoLabel(p, "Autopsy is available for Windows. Download from https://www.autopsy.com/download/");

        p.putClientProperty("cmdBuilder", (Supplier) () -> {
            String dir = installPath.getText().trim();
            if (!dir.isEmpty()) {
                File exe = new File(dir, "bin\\autopsy64.exe");
                if (exe.exists()) return List.of(exe.getAbsolutePath());
            }
            return List.of("autopsy");
        });
        return p;
    }

    // ─────────────── VOLATILITY ─────────────────────────────────────────
    private JPanel cfgVolatility() {
        JPanel p = cfgGrid();
        p.putClientProperty("toolId", "volatility");

        JTextField memImage = field("");
        JTextField profile  = field("");
        JButton browseImg   = smallBtn("Browse Image");
        JButton detectProfile = smallBtn("Auto-detect Profile");
        browseImg.addActionListener(e -> filePick(memImage, false));

        String[] plugins = {"pslist", "pstree", "cmdline", "dlllist", "filescan",
                            "netscan", "malfind", "dumpfiles", "hivelist", "hashdump"};
        JComboBox<String> plugin = combo(plugins);
        JComboBox<String> outFmt = combo(new String[]{"text", "json"});

        detectProfile.addActionListener(e -> {
            String img = memImage.getText().trim();
            if (img.isEmpty()) return;
            SwingWorker<String, Void> w = new SwingWorker<>() {
                @Override protected String doInBackground() throws Exception {
                    Process pp = new ProcessBuilder("vol.py", "-f", img, "imageinfo").redirectErrorStream(true).start();
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(pp.getInputStream()))) {
                        String ln; while ((ln = br.readLine()) != null) { if (ln.contains("Suggested")) return ln; }
                    }
                    return "";
                }
                @Override protected void done() {
                    try { String res = get(); if (!res.isEmpty()) profile.setText(res); }
                    catch (Exception ignored) {}
                }
            };
            w.execute();
        });

        addRow(p, "Memory Image:", hbox(memImage, browseImg));
        addRow(p, "Profile:", hbox(profile, detectProfile));
        addRow(p, "Plugin:", plugin);
        addRow(p, "Output:", outFmt);

        p.putClientProperty("cmdBuilder", (Supplier) () -> {
            List<String> cmd = new ArrayList<>(List.of("vol.py", "-f", memImage.getText().trim()));
            String prof = profile.getText().trim();
            if (!prof.isEmpty()) cmd.add("--profile=" + prof);
            cmd.add((String) plugin.getSelectedItem());
            String fmt = (String) outFmt.getSelectedItem();
            if ("json".equals(fmt)) cmd.add("--output=json");
            return cmd;
        });
        return p;
    }

    // ─────────────── EXIFTOOL ───────────────────────────────────────────
    private JPanel cfgExiftool() {
        JPanel p = cfgGrid();
        p.putClientProperty("toolId", "exiftool");

        JTextField filePath = field("");
        JButton browse = smallBtn("Browse");
        JCheckBox batch  = check("Batch directory mode (-r)");
        JComboBox<String> outFmt = combo(new String[]{"Text", "JSON (-json)", "CSV (-csv)"});
        browse.addActionListener(e -> {
            if (batch.isSelected()) {
                JFileChooser fc = new JFileChooser(); fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                if (fc.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) filePath.setText(fc.getSelectedFile().getAbsolutePath());
            } else {
                filePick(filePath, false);
            }
        });

        addRow(p, "File/Dir:", hbox(filePath, browse));
        addRow(p, "Batch:", batch);
        addRow(p, "Output:", outFmt);
        infoLabel(p, "⚠️  Write operations modify file metadata permanently.");

        p.putClientProperty("cmdBuilder", (Supplier) () -> {
            List<String> cmd = new ArrayList<>(List.of("exiftool"));
            String fmt = (String) outFmt.getSelectedItem();
            if (fmt != null && fmt.contains("-json")) cmd.add("-json");
            else if (fmt != null && fmt.contains("-csv")) cmd.add("-csv");
            if (batch.isSelected()) cmd.add("-r");
            cmd.add(filePath.getText().trim());
            return cmd;
        });
        return p;
    }

    // ─────────────── GHIDRA ─────────────────────────────────────────────
    private JPanel cfgGhidra() {
        JPanel p = cfgGrid();
        p.putClientProperty("toolId", "ghidra");

        // Detect Ghidra
        String[] paths = {"C:\\ghidra", "C:\\tools\\ghidra", System.getProperty("user.home") + "\\ghidra"};
        JTextField ghidraPath = field("");
        for (String path : paths) {
            File bat = new File(path, "ghidraRun.bat");
            if (bat.exists()) { ghidraPath.setText(bat.getAbsolutePath()); break; }
        }
        JButton browse = smallBtn("Browse");
        browse.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileFilter(new FileNameExtensionFilter("ghidraRun (*.bat, all)", "bat", "*"));
            if (fc.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) ghidraPath.setText(fc.getSelectedFile().getAbsolutePath());
        });

        addRow(p, "ghidraRun Path:", hbox(ghidraPath, browse));
        infoLabel(p, "Ghidra is a free and open source reverse engineering tool developed by the NSA.");

        p.putClientProperty("cmdBuilder", (Supplier) () -> {
            String path = ghidraPath.getText().trim();
            return List.of(path.isEmpty() ? "ghidraRun" : path);
        });
        return p;
    }

    // ─────────────── YARA ───────────────────────────────────────────────
    private JPanel cfgYara() {
        JPanel p = cfgGrid();
        p.putClientProperty("toolId", "yara");

        JTextField ruleFile = field("");
        JTextField target   = field("");
        JButton browseRule  = smallBtn("Browse Rules");
        JButton browseTgt   = smallBtn("Browse Target");
        browseRule.addActionListener(e -> filePick(ruleFile, false));
        browseTgt.addActionListener(e -> filePick(target, false));
        JCheckBox recursive = check("Recursive (-r)");
        JCheckBox verbose   = check("Verbose");

        addRow(p, "Rule File:", hbox(ruleFile, browseRule));
        addRow(p, "Target:", hbox(target, browseTgt));
        addRow(p, "Options:", hbox(recursive, verbose));

        p.putClientProperty("cmdBuilder", (Supplier) () -> {
            List<String> cmd = new ArrayList<>(List.of("yara"));
            if (recursive.isSelected()) cmd.add("-r");
            if (verbose.isSelected())   cmd.add("--print-xor-key");
            cmd.add(ruleFile.getText().trim());
            cmd.add(target.getText().trim());
            return cmd;
        });
        return p;
    }

    // ─────────────── CLAMAV ─────────────────────────────────────────────
    private JPanel cfgClamav() {
        JPanel p = cfgGrid();
        p.putClientProperty("toolId", "clamav");

        JComboBox<String> mode = combo(new String[]{"Directory Recursive", "File", "Quick", "Full"});
        JTextField path  = field("");
        JButton browse   = smallBtn("Browse");
        browse.addActionListener(e -> {
            JFileChooser fc = new JFileChooser(); fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
            if (fc.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) path.setText(fc.getSelectedFile().getAbsolutePath());
        });
        JButton updateBtn  = smallBtn("Update Signatures (freshclam)");
        JCheckBox infected = check("Infected only (-i)", true);

        updateBtn.addActionListener(e -> {
            try { Runtime.getRuntime().exec("freshclam"); JOptionPane.showMessageDialog(p, "freshclam launched."); }
            catch (Exception ex) { JOptionPane.showMessageDialog(p, "Failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE); }
        });

        addRow(p, "Scan Mode:", mode);
        addRow(p, "Path:", hbox(path, browse));
        addRow(p, "Options:", hbox(infected, updateBtn));

        p.putClientProperty("cmdBuilder", (Supplier) () -> {
            List<String> cmd = new ArrayList<>(List.of("clamscan", "-r", "--bell"));
            if (infected.isSelected()) cmd.add("-i");
            cmd.add(path.getText().trim());
            return cmd;
        });
        return p;
    }

    // ─────────────── CYBERCHEF ──────────────────────────────────────────
    private JPanel cfgCyberchef() {
        JPanel p = new JPanel(new BorderLayout(0, 6));
        p.setBackground(PANEL);
        p.putClientProperty("toolId", "cyberchef");

        // Check for local CyberChef
        String[] localPaths = {"D:\\tools\\cyberchef\\index.html",
                               System.getProperty("user.home") + "\\cyberchef\\index.html"};
        String localPath = null;
        for (String lp : localPaths) {
            if (new File(lp).exists()) { localPath = lp; break; }
        }

        JButton openBrowser = neonButton("🌐 Open CyberChef in Browser", BLUE);
        final String lp = localPath;
        openBrowser.addActionListener(e -> {
            try {
                if (lp != null) Desktop.getDesktop().browse(new File(lp).toURI());
                else Desktop.getDesktop().browse(new URI("https://gchq.github.io/CyberChef/"));
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(p, "Cannot open browser: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        // Quick encode/decode
        JPanel quickPanel = new JPanel(new GridLayout(2, 1, 0, 4));
        quickPanel.setBackground(PANEL);

        JPanel inputRow = new JPanel(new BorderLayout(4, 0));
        inputRow.setBackground(PANEL);
        JTextArea inputArea = new JTextArea(2, 30);
        styleTextArea(inputArea);
        JScrollPane inScroll = new JScrollPane(inputArea);
        inScroll.setBorder(BorderFactory.createLineBorder(BORDER));

        JPanel outputRow = new JPanel(new BorderLayout(4, 0));
        outputRow.setBackground(PANEL);
        JTextArea outputArea = new JTextArea(2, 30);
        styleTextArea(outputArea);
        JScrollPane outScroll = new JScrollPane(outputArea);
        outScroll.setBorder(BorderFactory.createLineBorder(BORDER));

        JComboBox<String> op = combo(new String[]{"Base64 Encode", "Base64 Decode", "URL Encode", "URL Decode", "Hex Encode", "Hex Decode"});
        JButton runOp = neonButton("Run", GREEN);
        runOp.addActionListener(e -> {
            String input = inputArea.getText();
            String sel   = (String) op.getSelectedItem();
            String result = "";
            try {
                if ("Base64 Encode".equals(sel))  result = Base64.getEncoder().encodeToString(input.getBytes());
                else if ("Base64 Decode".equals(sel)) result = new String(Base64.getDecoder().decode(input.trim()));
                else if ("URL Encode".equals(sel))  result = java.net.URLEncoder.encode(input, "UTF-8");
                else if ("URL Decode".equals(sel))  result = java.net.URLDecoder.decode(input, "UTF-8");
                else if ("Hex Encode".equals(sel)) {
                    StringBuilder sb = new StringBuilder();
                    for (byte b : input.getBytes()) sb.append(String.format("%02x ", b));
                    result = sb.toString().trim();
                } else if ("Hex Decode".equals(sel)) {
                    String[] hexArr = input.trim().split("\\s+");
                    byte[] bytes = new byte[hexArr.length];
                    for (int i = 0; i < hexArr.length; i++) bytes[i] = (byte) Integer.parseInt(hexArr[i], 16);
                    result = new String(bytes);
                }
            } catch (Exception ex) { result = "[Error: " + ex.getMessage() + "]"; }
            outputArea.setText(result);
        });

        JPanel ctrlRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        ctrlRow.setBackground(PANEL);
        ctrlRow.add(op); ctrlRow.add(runOp);

        inputRow.add(new JLabel("Input:") {{ setForeground(TEXT2); setFont(FONT_SMALL); }}, BorderLayout.WEST);
        inputRow.add(inScroll, BorderLayout.CENTER);
        outputRow.add(new JLabel("Output:") {{ setForeground(TEXT2); setFont(FONT_SMALL); }}, BorderLayout.WEST);
        outputRow.add(outScroll, BorderLayout.CENTER);

        quickPanel.add(inputRow); quickPanel.add(outputRow);

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        top.setBackground(PANEL);
        top.add(openBrowser);
        top.add(ctrlRow);

        p.add(top, BorderLayout.NORTH);
        p.add(quickPanel, BorderLayout.CENTER);

        p.putClientProperty("cmdBuilder", (Supplier) () -> List.of("echo", "CyberChef is web-based — use the browser button above"));
        return p;
    }

    // ─────────────── WAZUH ──────────────────────────────────────────────
    private JPanel cfgWazuh() {
        JPanel p = cfgGrid();
        p.putClientProperty("toolId", "wazuh");

        infoLabel(p, "⚠️  Wazuh requires Linux/WSL for the agent and manager.");
        JTextField dashboardUrl = field("http://localhost:5601");
        JButton openDash = smallBtn("Open Dashboard");
        openDash.addActionListener(e -> {
            try { Desktop.getDesktop().browse(new URI(dashboardUrl.getText().trim())); } catch (Exception ex) { /* ignore */ }
        });
        JCheckBox useWsl = new JCheckBox("Launch via WSL");
        useWsl.setForeground(TEXT); useWsl.setBackground(PANEL);

        addRow(p, "Dashboard URL:", hbox(dashboardUrl, openDash));
        addRow(p, "WSL:", useWsl);

        p.putClientProperty("cmdBuilder", (Supplier) () ->
            useWsl.isSelected()
                ? List.of("wsl", "wazuh-agent", "start")
                : List.of("wazuh-agent", "start"));
        return p;
    }

    // ─────────────── SYSMON ─────────────────────────────────────────────
    private JPanel cfgSysmon() {
        JPanel p = cfgGrid();
        p.putClientProperty("toolId", "sysmon");

        JTextField serviceStatus = field("Unknown");
        serviceStatus.setEditable(false);
        JButton btnStatus  = smallBtn("Check Status");
        JButton btnStart   = smallBtn("Start Service");
        JButton btnStop    = smallBtn("Stop Service");
        JTextField cfgFile = field("");
        JButton browseCfg  = smallBtn("Browse Config XML");
        browseCfg.addActionListener(e -> filePick(cfgFile, false));

        btnStatus.addActionListener(e -> {
            runServiceCmd(List.of("sc", "query", "Sysmon64"), serviceStatus);
        });
        btnStart.addActionListener(e -> {
            runServiceCmd(List.of("sc", "start", "Sysmon64"), serviceStatus);
        });
        btnStop.addActionListener(e -> {
            runServiceCmd(List.of("sc", "stop", "Sysmon64"), serviceStatus);
        });

        addRow(p, "Service Status:", hbox(serviceStatus, btnStatus, btnStart, btnStop));
        addRow(p, "Config XML:", hbox(cfgFile, browseCfg));
        infoLabel(p, "Event Viewer: Use 'Execute' to dump recent Sysmon events.");

        p.putClientProperty("cmdBuilder", (Supplier) () ->
            List.of("powershell", "-Command",
                "Get-WinEvent -LogName 'Microsoft-Windows-Sysmon/Operational' -MaxEvents 50 | Format-List"));
        return p;
    }

    private void runServiceCmd(List<String> cmd, JTextField statusField) {
        SwingWorker<String, Void> w = new SwingWorker<>() {
            @Override protected String doInBackground() throws Exception {
                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.redirectErrorStream(true);
                Process pp = pb.start();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(pp.getInputStream()))) {
                    StringBuilder sb = new StringBuilder();
                    String ln; while ((ln = br.readLine()) != null) sb.append(ln).append("\n");
                    return sb.toString();
                }
            }
            @Override protected void done() {
                try {
                    String out = get();
                    if (out.contains("RUNNING")) statusField.setText("RUNNING");
                    else if (out.contains("STOPPED")) statusField.setText("STOPPED");
                    else statusField.setText(out.lines().findFirst().orElse("unknown"));
                } catch (Exception ignored) { statusField.setText("Error"); }
            }
        };
        w.execute();
    }

    // ─────────────── AMASS ──────────────────────────────────────────────
    private JPanel cfgAmass() {
        JPanel p = cfgGrid();
        p.putClientProperty("toolId", "amass");

        JTextField domain  = field("example.com");
        JComboBox<String> mode = combo(new String[]{"Passive", "Active", "Intel"});
        JTextField resolvers = field("");
        JButton browseR = smallBtn("Browse Resolvers");
        browseR.addActionListener(e -> filePick(resolvers, false));
        JTextField output = field("amass_output.txt");

        addRow(p, "Domain:", domain);
        addRow(p, "Mode:", mode);
        addRow(p, "DNS Resolvers:", hbox(resolvers, browseR));
        addRow(p, "Output file:", output);

        p.putClientProperty("cmdBuilder", (Supplier) () -> {
            List<String> cmd = new ArrayList<>(List.of("amass", "enum", "-d", domain.getText().trim()));
            String sel = (String) mode.getSelectedItem();
            if ("Passive".equals(sel)) cmd.add("-passive");
            else if ("Intel".equals(sel)) { cmd.clear(); cmd.addAll(List.of("amass", "intel", "-d", domain.getText().trim())); }
            String res = resolvers.getText().trim();
            if (!res.isEmpty()) { cmd.add("-rf"); cmd.add(res); }
            String out = output.getText().trim();
            if (!out.isEmpty()) { cmd.add("-o"); cmd.add(out); }
            return cmd;
        });
        return p;
    }

    // ─────────────── SPIDERFOOT ─────────────────────────────────────────
    private JPanel cfgSpiderfoot() {
        JPanel p = cfgGrid();
        p.putClientProperty("toolId", "spiderfoot");

        JTextField target   = field("example.com");
        JTextField scanName = field("MyScan");
        JTextField sfPath   = field("sf.py");
        JButton browse      = smallBtn("Browse sf.py");
        browse.addActionListener(e -> filePick(sfPath, false));
        JButton openUI      = smallBtn("Open in Browser");
        openUI.addActionListener(e -> {
            try { Desktop.getDesktop().browse(new URI("http://localhost:5001")); } catch (Exception ex) { /* ignore */ }
        });

        addRow(p, "sf.py Path:", hbox(sfPath, browse));
        addRow(p, "Target:", target);
        addRow(p, "Scan Name:", scanName);
        addRow(p, "Web UI:", openUI);

        p.putClientProperty("cmdBuilder", (Supplier) () ->
            List.of("python", sfPath.getText().trim(), "-l", "0.0.0.0:5001"));
        return p;
    }

    // ─────────────── MALTEGO ────────────────────────────────────────────
    private JPanel cfgMaltego() {
        JPanel p = cfgGrid();
        p.putClientProperty("toolId", "maltego");

        JTextField installPath = field("");
        JButton browse = smallBtn("Browse");
        browse.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            if (fc.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) installPath.setText(fc.getSelectedFile().getAbsolutePath());
        });

        addRow(p, "Maltego Path:", hbox(installPath, browse));
        infoLabel(p, "Maltego is an interactive data mining tool for link analysis and digital forensics.");

        p.putClientProperty("cmdBuilder", (Supplier) () -> {
            String path = installPath.getText().trim();
            return List.of(path.isEmpty() ? "maltego" : path);
        });
        return p;
    }

    // ─────────────── OPENVAS (generic) ──────────────────────────────────
    private JPanel cfgGeneric(ToolInfo tool) {
        JPanel p = cfgGrid();
        p.putClientProperty("toolId", tool.id);

        JTextField argsField = field("");
        addRow(p, "Arguments:", argsField);

        if (!tool.osNote.isEmpty()) infoLabel(p, "Note: " + tool.osNote);

        p.putClientProperty("cmdBuilder", (Supplier) () -> {
            List<String> cmd = new ArrayList<>();
            cmd.add(tool.executable);
            String args = argsField.getText().trim();
            if (!args.isEmpty()) Collections.addAll(cmd, args.split("\\s+"));
            return cmd;
        });
        return p;
    }

    // ─────────────── WSL notice panel ───────────────────────────────────
    private JPanel cfgWslNotice(String toolName, String wslCmd) {
        JPanel p = cfgGrid();
        p.putClientProperty("toolId", toolName.toLowerCase());

        infoLabel(p, "⚠️  " + toolName + " requires Linux. Use WSL for full functionality.");
        JButton btnWsl = smallBtn("Launch via WSL");

        p.putClientProperty("cmdBuilder", (Supplier) () -> {
            List<String> cmd = new ArrayList<>(List.of("wsl"));
            Collections.addAll(cmd, wslCmd.split("\\s+"));
            return cmd;
        });

        addRow(p, "WSL Launch:", btnWsl);
        btnWsl.addActionListener(e -> {
            // Trigger the execute logic via a fake click would need reference — handled by tag
        });
        return p;
    }

    // ═══════════════════════════════════════════════════════════════════
    // Command extraction
    // ═══════════════════════════════════════════════════════════════════
    @SuppressWarnings("unchecked")
    private List<String> extractCommand(ToolInfo tool, JPanel cfg) {
        List<String> cmd;
        Object builder = cfg.getClientProperty("cmdBuilder");
        if (builder instanceof Supplier) {
            try {
                cmd = new ArrayList<>((List<String>) ((Supplier) builder).get());
            } catch (Exception ex) {
                return List.of("echo", "[Error building command: " + ex.getMessage() + "]");
            }
        } else {
            cmd = new ArrayList<>(List.of(tool.executable));
        }
        if (tool.resolvedPath != null && !tool.resolvedPath.isEmpty() && !cmd.isEmpty()) {
            cmd.set(0, tool.resolvedPath);
        }
        return cmd;
    }

    // ═══════════════════════════════════════════════════════════════════
    // Helper: UI builders
    // ═══════════════════════════════════════════════════════════════════
    private JPanel cfgGrid() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(PANEL);
        return p;
    }

    private void addRow(JPanel grid, String label, JComponent comp) {
        GridBagConstraints lc = new GridBagConstraints();
        lc.gridx = 0; lc.gridy = GridBagConstraints.RELATIVE;
        lc.anchor = GridBagConstraints.WEST;
        lc.insets = new Insets(3, 0, 3, 8);
        JLabel lbl = new JLabel(label);
        lbl.setFont(FONT_SMALL);
        lbl.setForeground(TEXT2);
        grid.add(lbl, lc);

        GridBagConstraints cc = new GridBagConstraints();
        cc.gridx = 1; cc.gridy = GridBagConstraints.RELATIVE;
        cc.fill = GridBagConstraints.HORIZONTAL;
        cc.weightx = 1.0;
        cc.insets = new Insets(3, 0, 3, 0);
        grid.add(comp, cc);
    }

    private void infoLabel(JPanel p, String text) {
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0; gc.gridwidth = 2; gc.gridy = GridBagConstraints.RELATIVE;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.insets = new Insets(2, 0, 2, 0);
        JLabel lbl = new JLabel(text);
        lbl.setFont(FONT_SMALL);
        lbl.setForeground(AMBER);
        p.add(lbl, gc);
    }

    private JTextField field(String placeholder) {
        JTextField f = new JTextField(placeholder, 20);
        f.setBackground(new Color(0x0A, 0x12, 0x1E));
        f.setForeground(TEXT);
        f.setCaretColor(CYAN);
        f.setFont(FONT_MONO);
        f.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(BORDER, 1, true),
                BorderFactory.createEmptyBorder(3, 6, 3, 6)));
        return f;
    }

    private JComboBox<String> combo(String[] items) {
        JComboBox<String> cb = new JComboBox<>(items);
        cb.setBackground(new Color(0x0A, 0x12, 0x1E));
        cb.setForeground(TEXT);
        cb.setFont(FONT_SMALL);
        return cb;
    }

    private JCheckBox check(String text) { return check(text, false); }

    private JCheckBox check(String text, boolean selected) {
        JCheckBox cb = new JCheckBox(text, selected);
        cb.setForeground(TEXT2);
        cb.setBackground(PANEL);
        cb.setFont(FONT_SMALL);
        return cb;
    }

    private JSpinner spinner(int val, int min, int max) {
        JSpinner sp = new JSpinner(new SpinnerNumberModel(val, min, max, 1));
        sp.setBackground(PANEL2);
        sp.setForeground(TEXT);
        ((JSpinner.DefaultEditor) sp.getEditor()).getTextField().setBackground(new Color(0x0A, 0x12, 0x1E));
        ((JSpinner.DefaultEditor) sp.getEditor()).getTextField().setForeground(TEXT);
        ((JSpinner.DefaultEditor) sp.getEditor()).getTextField().setFont(FONT_MONO);
        return sp;
    }

    private JButton neonButton(String text, Color accent) {
        JButton btn = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                boolean hover = getModel().isRollover();
                boolean press = getModel().isPressed();
                g2.setColor(press ? accent.darker().darker()
                          : hover ? new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 50)
                                  : new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 20));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
                g2.setColor(isEnabled() ? accent : accent.darker());
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(FONT_SMALL);
        btn.setForeground(accent);
        btn.setBackground(new Color(0, 0, 0, 0));
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private JButton smallBtn(String text) {
        JButton btn = new JButton(text);
        btn.setFont(FONT_SMALL);
        btn.setForeground(CYAN);
        btn.setBackground(PANEL2);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(BORDER, 1, true),
                BorderFactory.createEmptyBorder(3, 8, 3, 8)));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private JPanel createTargetDeviceSelector(JTextField targetField) {
        JPanel container = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        container.setBackground(PANEL);

        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        model.addElement("127.0.0.1 (Localhost)");

        try {
            JavaOS os = JavaOS.getInstance();
            if (os != null) {
                NetworkScannerService net = os.getNetworkScannerService();
                if (net != null) {
                    String localIp = net.getLocalIp();
                    if (localIp != null && !localIp.isEmpty()) {
                        model.addElement(localIp + " (Host Machine)");
                    }
                    String gw = net.getGatewayIp();
                    if (gw != null && !gw.isEmpty()) {
                        model.addElement(gw + " (Default Gateway)");
                    }
                    String sub = net.getSubnetPrefix();
                    if (sub != null && !sub.isEmpty()) {
                        model.addElement(sub + ".0/24 (Subnet Range)");
                    }
                }
            }
        } catch (Exception ignored) {}

        JComboBox<String> combo = new JComboBox<>(model);
        combo.setBackground(new Color(0x0A, 0x12, 0x1E));
        combo.setForeground(TEXT);
        combo.setFont(FONT_SMALL);
        combo.setPreferredSize(new Dimension(230, 26));

        combo.addActionListener(e -> {
            String sel = (String) combo.getSelectedItem();
            if (sel != null && !sel.startsWith("---")) {
                String ip = sel.split("\\s+")[0].trim();
                targetField.setText(ip);
            }
        });

        JButton btnScan = smallBtn("📡 Scan Nearby Devices");
        btnScan.setToolTipText("Scan surrounding Wi-Fi, Bluetooth & LAN airwaves for devices");
        btnScan.addActionListener(e -> {
            btnScan.setEnabled(false);
            btnScan.setText("Scanning...");
            SwingWorker<List<String>, Void> worker = new SwingWorker<>() {
                @Override
                protected List<String> doInBackground() {
                    List<String> results = new ArrayList<>();
                    try {
                        JavaOS os = JavaOS.getInstance();
                        if (os != null) {
                            NetworkScannerService net = os.getNetworkScannerService();
                            if (net != null) {
                                List<WirelessAirDevice> air = net.scanAirwaveDevices();
                                if (air != null) {
                                    for (WirelessAirDevice d : air) {
                                        String addr = (d.ipAddress != null && !d.ipAddress.isEmpty()) ? d.ipAddress : d.macAddress;
                                        String label = addr + " (" + d.name + " - " + d.deviceCategory + ")";
                                        if (!results.contains(label)) results.add(label);
                                    }
                                }
                            }
                        }
                    } catch (Exception ex) {
                        results.add("Error scanning: " + ex.getMessage());
                    }
                    return results;
                }

                @Override
                protected void done() {
                    try {
                        List<String> list = get();
                        if (!list.isEmpty()) {
                            boolean hasHeader = false;
                            for (int i = 0; i < model.getSize(); i++) {
                                if (model.getElementAt(i).contains("Discovered Nearby Devices")) {
                                    hasHeader = true; break;
                                }
                            }
                            if (!hasHeader) model.addElement("--- Discovered Nearby Devices ---");
                            for (String item : list) {
                                boolean exists = false;
                                for (int i = 0; i < model.getSize(); i++) {
                                    if (model.getElementAt(i).equals(item)) {
                                        exists = true; break;
                                    }
                                }
                                if (!exists) model.addElement(item);
                            }
                        }
                    } catch (Exception ignored) {}
                    btnScan.setEnabled(true);
                    btnScan.setText("📡 Scan Nearby Devices");
                }
            };
            worker.execute();
        });

        container.add(targetField);
        JLabel lbl = new JLabel("Nearby Device:");
        lbl.setFont(FONT_SMALL);
        lbl.setForeground(TEXT2);
        container.add(lbl);
        container.add(combo);
        container.add(btnScan);
        return container;
    }

    private JPanel hbox(JComponent... components) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row.setBackground(PANEL);
        for (JComponent c : components) row.add(c);
        return row;
    }

    private void styleTextArea(JTextArea ta) {
        ta.setFont(FONT_MONO);
        ta.setBackground(new Color(0x01, 0x04, 0x09));
        ta.setForeground(CYAN);
        ta.setCaretColor(CYAN);
    }

    private void appendOutput(JTextArea area, String text) {
        area.append(text);
        area.setCaretPosition(area.getDocument().getLength());
    }

    private void saveOutput(JTextArea area, String toolName) {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File(toolName.replaceAll("\\s+", "_") + "_output.txt"));
        fc.setFileFilter(new FileNameExtensionFilter("Text files", "txt", "log"));
        if (fc.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
            try {
                Files.writeString(fc.getSelectedFile().toPath(), area.getText());
            } catch (IOException e) {
                JOptionPane.showMessageDialog(null, "Failed to save: " + e.getMessage(),
                        "Save Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void filePick(JTextField target, boolean dirsOnly) {
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(dirsOnly ? JFileChooser.DIRECTORIES_ONLY : JFileChooser.FILES_AND_DIRECTORIES);
        if (fc.showOpenDialog(null) == JFileChooser.APPROVE_OPTION)
            target.setText(fc.getSelectedFile().getAbsolutePath());
    }

    private JLabel statusBadge(ToolInfo tool) {
        String badgeText;
        Color badgeColor;
        if ("N/A".equals(tool.executable) || tool.osNote.contains("Web")) {
            badgeText = "WEB-BASED"; badgeColor = BLUE;
        } else if (!tool.osNote.isEmpty() && !tool.installed) {
            badgeText = "WSL REQUIRED"; badgeColor = AMBER;
        } else if (tool.installed) {
            badgeText = "INSTALLED"; badgeColor = GREEN;
        } else {
            badgeText = "NOT FOUND"; badgeColor = RED;
        }
        JLabel badge = new JLabel(badgeText);
        badge.setFont(new Font("Segoe UI", Font.BOLD, 10));
        badge.setForeground(badgeColor);
        badge.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(badgeColor, 1, true),
                BorderFactory.createEmptyBorder(1, 5, 1, 5)));
        return badge;
    }

    private String installHint(ToolInfo tool) {
        return switch (tool.id) {
            case "nmap"      -> "Download from https://nmap.org/download.html";
            case "wireshark" -> "Download from https://www.wireshark.org/download.html";
            case "tshark"    -> "Included with Wireshark installation";
            case "netcat"    -> "Install via nmap package (ncat) or chocolatey: choco install netcat";
            case "nuclei"    -> "go install -v github.com/projectdiscovery/nuclei/v3/cmd/nuclei@latest";
            case "nikto"     -> "https://github.com/sullo/nikto or chocolatey: choco install nikto";
            case "metasploit"-> "https://www.metasploit.com/download";
            case "searchsploit" -> "Install Exploit-DB: https://www.exploit-db.com/searchsploit";
            case "sqlmap"    -> "pip install sqlmap or https://sqlmap.org";
            case "ffuf"      -> "go install github.com/ffuf/ffuf@latest";
            case "john"      -> "https://www.openwall.com/john/ or chocolatey: choco install john";
            case "hashcat"   -> "https://hashcat.net/hashcat/";
            case "yara"      -> "https://virustotal.github.io/yara/";
            case "clamav"    -> "https://www.clamav.net/downloads or choco install clamav";
            case "exiftool"  -> "https://exiftool.org/install.html or choco install exiftool";
            case "amass"     -> "go install -v github.com/owasp-amass/amass/v4/...@master";
            default          -> "Check the tool's official documentation for Windows installation instructions.";
        };
    }

    private String prettyDept(Department d) {
        return switch (d) {
            case NETWORKING   -> "Network Reconnaissance";
            case VULNERABILITY -> "Vulnerability Assessment";
            case PENTEST      -> "Penetration Testing";
            case WEB_SECURITY -> "Web Security";
            case WIRELESS     -> "Wireless Security";
            case PASSWORD     -> "Password & Credential Tools";
            case FORENSICS    -> "Digital Forensics";
            case MALWARE      -> "Malware Analysis";
            case DEFENSIVE    -> "Defensive Security";
            case OSINT        -> "OSINT & Reconnaissance";
        };
    }

    private String getDefaultInterface() {
        try {
            Enumeration<java.net.NetworkInterface> en = java.net.NetworkInterface.getNetworkInterfaces();
            while (en.hasMoreElements()) {
                java.net.NetworkInterface ni = en.nextElement();
                if (!ni.isLoopback() && ni.isUp()) {
                    return System.getProperty("os.name", "").toLowerCase().contains("win") ? "1" : ni.getName();
                }
            }
        } catch (Exception ignored) {}
        return "1";
    }

    // Functional interface (since java.util.function.Supplier is used in client properties)
    @FunctionalInterface
    private interface Supplier { Object get(); }
}

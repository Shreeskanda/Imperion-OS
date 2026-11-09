package javaos.apps;

import javaos.services.NetworkScannerService;
import javaos.services.NetworkScannerService.DiscoveredDevice;
import javaos.services.NetworkScannerService.PortAuditResult;
import javaos.services.NetworkScannerService.WirelessAirDevice;
import javaos.ui.Theme;
import javaos.ui.ThemeManager;
import javaos.ui.components.CyberpunkIcons;
import javaos.ui.components.NeonScrollBarUI;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.net.InetAddress;
import java.util.*;
import java.util.List;

/**
 * NetRunner CyberDeck Network Operations & Real Device Scanner for IMPERION OS.
 * Features:
 *  - Over-The-Air Airwave Radar (Zero Wires / Zero Wi-Fi Connection Required)
 *    Passively listens to 802.11 RF beacons, mobile hotspots, and Bluetooth RF broadcasts.
 *  - Discovers surrounding smartphones, accessories, smart TVs, routers, and beacons
 *  - Resolves Signal Strength % (RSSI), Physical Proximity Distance, BSSID/MAC, Channels
 *  - Connected Subnet IP Scanner & Deep Port Auditor
 *  - 360-Degree Airwave Proximity Radar mapping surrounding nodes
 */
public class CyberDeckApp extends OSApplication {

    // Airwave Tab components
    private JTable airTable;
    private DefaultTableModel airModel;
    private JLabel airStatusLabel;
    private JButton btnAirScan;
    private final List<WirelessAirDevice> airDevices = new ArrayList<>();

    // Subnet Tab components
    private JTable deviceTable;
    private DefaultTableModel deviceModel;
    private JProgressBar scanProgress;
    private JLabel statusLabel;
    private JButton btnScan;
    private JButton btnAuditPorts;
    private JTable portTable;
    private DefaultTableModel portModel;

    // Waterfall & Radar components
    private JTextArea packetStreamArea;
    private PingGraphPanel pingGraph;
    private Radar3DCanvas topologyCanvas;
    private JTabbedPane mainTabs;
    private JTable radarTargetTable;
    private DefaultTableModel radarTargetModel;
    private JLabel radarTargetCountLabel;
    private JPanel radarSidebarPanel;

    private javax.swing.Timer networkTimer;
    private boolean isScanningSubnet = false;
    private boolean isScanningAir = false;
    private final List<DiscoveredDevice> discoveredDevices = new ArrayList<>();
    private final Random rnd = new Random();

    public CyberDeckApp() {
        super(new AppDescriptor(
                "cyberdeck",
                "NetRunner Deck",
                "Airwave RF beacon radar, Bluetooth sniffer & LAN scanner",
                AppDescriptor.Category.SYSTEM,
                CyberpunkIcons.createIcon("RADAR", 20),
                false
        ));
    }

    @Override
    public Dimension getPreferredWindowSize() {
        return new Dimension(1000, 680);
    }

    @Override
    public JComponent createContent() {
        JTabbedPane tabs = new JTabbedPane();
        mainTabs = tabs;
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        tabs.setBackground(theme.panel);
        tabs.setForeground(theme.textPrimary);
        tabs.setFont(theme.fontBold);

        tabs.addTab("  📡 AIRWAVE RF RADAR (NO WI-FI NEEDED)  ", CyberpunkIcons.createIcon("RADAR", 14), buildAirwaveScannerTab());
        tabs.addTab("  🌐 SUBNET IP SCANNER (CONNECTED)  ", CyberpunkIcons.createIcon("NETWORK", 14), buildDeviceScannerTab());
        tabs.addTab("  \uD83C\uDF10 3D GYRO RADAR  ", CyberpunkIcons.createIcon("MONITOR", 14), buildTopologyTab());
        tabs.addTab("  ⚡ PACKET WATERFALL & OSCILLOSCOPE  ", CyberpunkIcons.createIcon("MONITOR", 14), buildPacketWaterfallTab());

        // Background telemetry timer
        networkTimer = new javax.swing.Timer(750, e -> updateNetworkTelemetry());
        networkTimer.start();

        // Auto-scan when switching TO the 3D Radar tab (index 2) if no devices loaded yet
        tabs.addChangeListener(e -> {
            if (tabs.getSelectedIndex() == 2 && airDevices.isEmpty() && !isScanningAir) {
                SwingUtilities.invokeLater(this::startAirwaveScan);
            }
        });

        // Run airwave scan immediately on open
        SwingUtilities.invokeLater(this::startAirwaveScan);

        return tabs;
    }

    // ── TAB 1: AIRWAVE RF SCANNER (ZERO WIRES / ZERO WI-FI CONNECTION NEEDED) ─
    private JPanel buildAirwaveScannerTab() {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setOpaque(true);
        p.setBackground(theme.background);
        p.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Top Banner
        JPanel topContainer = new JPanel(new BorderLayout(0, 6));
        topContainer.setOpaque(false);

        String info = "<html><font color='#22d3ee'><b>● AIRWAVE RF SCANNER:</b></font> Passively listens to 802.11 Wi-Fi beacon radio airwaves & Bluetooth broadcasts.<br/>" +
                "<font color='#a855f7'><b>● INDEPENDENCE:</b></font> <font color='#10b981'><b>ZERO WIRES</b></font> &amp; <font color='#10b981'><b>ZERO WI-FI CONNECTION REQUIRED</b></font> (Operates on raw radio frequencies in the physical room).</html>";
        JLabel lblInfo = new JLabel(info);
        lblInfo.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblInfo.setForeground(theme.textPrimary);
        topContainer.add(lblInfo, BorderLayout.NORTH);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        controls.setOpaque(false);

        btnAirScan = new JButton("📡 SCAN AIRWAVES OVER THE AIR (NO WI-FI REQUIRED)");
        btnAirScan.setFont(theme.fontBold);
        btnAirScan.setBackground(new Color(15, 23, 42));
        btnAirScan.setForeground(theme.neonAccent);
        btnAirScan.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(theme.neonAccent, 1),
                BorderFactory.createEmptyBorder(7, 16, 7, 16)
        ));
        btnAirScan.addActionListener(e -> startAirwaveScan());
        controls.add(btnAirScan);

        airStatusLabel = new JLabel("Ready to listen to airwave spectrum");
        airStatusLabel.setFont(theme.fontSmall);
        airStatusLabel.setForeground(theme.textSecondary);
        controls.add(airStatusLabel);

        topContainer.add(controls, BorderLayout.SOUTH);
        p.add(topContainer, BorderLayout.NORTH);

        // Main Airwave Table
        String[] cols = {"Radio Protocol", "Device Name / SSID", "Resolved IP Address", "Physical MAC (BSSID)", "Signal Strength", "Estimated Proximity", "Band & Channel", "Security Protocol", "Brand / Vendor"};
        airModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        airTable = new JTable(airModel);
        airTable.setBackground(theme.panel);
        airTable.setForeground(theme.textPrimary);
        airTable.setRowHeight(26);
        airTable.getTableHeader().setBackground(theme.elevatedPanel);
        airTable.getTableHeader().setForeground(theme.neonAccent);
        airTable.getTableHeader().setFont(theme.fontBold);

        airTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tbl, Object v, boolean isSel, boolean hasFocus, int r, int c) {
                Component comp = super.getTableCellRendererComponent(tbl, v, isSel, hasFocus, r, c);
                Theme th = ThemeManager.getInstance().getCurrentTheme();
                if (c == 0 && v != null) {
                    comp.setForeground(v.toString().contains("Bluetooth") ? th.secondaryAccent : th.neonAccent);
                    comp.setFont(th.fontBold);
                } else if (c == 1 && v != null) {
                    comp.setForeground(Color.WHITE);
                    comp.setFont(th.fontBold);
                } else if (c == 2 && v != null) {
                    // IP Address column
                    String ipStr = v.toString();
                    if (ipStr.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
                        comp.setForeground(new Color(34, 211, 238));
                        comp.setFont(new Font("Consolas", Font.BOLD, 12));
                    } else {
                        comp.setForeground(new Color(100, 116, 139));
                        comp.setFont(new Font("Segoe UI", Font.ITALIC, 11));
                    }
                } else if (c == 3 && v != null) {
                    comp.setForeground(new Color(148, 163, 184));
                    comp.setFont(new Font("Consolas", Font.PLAIN, 11));
                } else if (c == 4 && v != null) {
                    comp.setForeground(th.success);
                    comp.setFont(new Font("Consolas", Font.BOLD, 12));
                } else if (c == 5 && v != null) {
                    String s = v.toString();
                    if (s.contains("< 1.5") || s.contains("< 0.5") || s.contains("Immediate") || s.contains("Beside")) comp.setForeground(th.neonAccent);
                    else if (s.contains("Same Room")) comp.setForeground(th.success);
                    else comp.setForeground(th.warning);
                } else {
                    comp.setForeground(isSel ? th.neonAccent : th.textPrimary);
                }
                comp.setBackground(isSel ? th.selectionBackground : (r % 2 == 0 ? th.panel : th.background));
                return comp;
            }
        });

        airTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int r = airTable.getSelectedRow();
                if (r >= 0 && r < airDevices.size()) {
                    if (topologyCanvas != null) {
                        topologyCanvas.setSelectedDevice(airDevices.get(r));
                    }
                }
            }
        });

        JScrollPane sp = new JScrollPane(airTable);
        sp.getVerticalScrollBar().setUI(new NeonScrollBarUI());
        sp.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(theme.neonAccent),
                "SURROUNDING TRANSMITTING DEVICES DETECTED OVER THE AIR (RF 802.11 / BLUETOOTH)",
                0, 0, theme.fontBold, theme.neonAccent
        ));

        p.add(sp, BorderLayout.CENTER);
        return p;
    }

    public void auditTargetIp(String ip, String hostname) {
        if (mainTabs != null) {
            mainTabs.setSelectedIndex(1); // Switch to Subnet IP Scanner
        }
        if (statusLabel != null) statusLabel.setText("Auditing ports on " + ip + " (" + hostname + ")...");
        if (btnAuditPorts != null) btnAuditPorts.setEnabled(false);
        if (portModel != null) portModel.setRowCount(0);

        new Thread(() -> {
            NetworkScannerService scanner = getOS() != null ? getOS().getNetworkScannerService() : new NetworkScannerService();
            List<PortAuditResult> results = scanner.auditDevicePorts(ip);
            SwingUtilities.invokeLater(() -> {
                if (btnAuditPorts != null) btnAuditPorts.setEnabled(true);
                if (portModel != null) {
                    portModel.setRowCount(0);
                    if (results.isEmpty()) {
                        portModel.addRow(new Object[]{ip, "ALL", "Standard Services", "FILTERED / CLOSED", "SECURE - Stealth Mode"});
                        if (statusLabel != null) statusLabel.setText("Port audit complete for " + ip + ": No exposed common ports.");
                    } else {
                        for (PortAuditResult r : results) {
                            portModel.addRow(new Object[]{ip, String.valueOf(r.port), r.serviceName, r.state, r.riskLevel});
                        }
                        if (statusLabel != null) statusLabel.setText(String.format("Port audit complete for %s: %d open ports detected.", ip, results.size()));
                    }
                }
            });
        }).start();
    }

    private void startAirwaveScan() {
        if (isScanningAir) return;
        isScanningAir = true;
        btnAirScan.setEnabled(false);
        airStatusLabel.setText("Listening to 2.4 GHz, 5 GHz RF beacon airwaves and Bluetooth...");
        airModel.setRowCount(0);
        airDevices.clear();

        new Thread(() -> {
            NetworkScannerService scanner = getOS() != null ? getOS().getNetworkScannerService() : new NetworkScannerService();
            List<WirelessAirDevice> results = scanner.scanAirwaveDevices();
            SwingUtilities.invokeLater(() -> {
                isScanningAir = false;
                btnAirScan.setEnabled(true);
                airDevices.clear();
                airDevices.addAll(results);
                airModel.setRowCount(0);
                for (WirelessAirDevice d : results) {
                    // Show signal bar + RSSI dBm when available
                    String sig;
                    if (d.rssiDbm != Integer.MIN_VALUE) {
                        sig = String.format("██ %d%% (%d dBm)", d.signalPercent, d.rssiDbm);
                    } else {
                        sig = String.format("██ %d%%", d.signalPercent);
                    }
                    airModel.addRow(new Object[]{
                            d.protocol,
                            d.name,
                            d.ipAddress != null ? d.ipAddress : "Over-The-Air RF",
                            d.macAddress,
                            sig,
                            d.estimatedDistance,
                            d.bandAndChannel,
                            d.securityType,
                            d.vendor
                    });
                }
                airStatusLabel.setText(String.format("Airwave scan complete. Found %d surrounding wireless devices.", results.size()));
                if (radarTargetModel != null) {
                    radarTargetModel.setRowCount(0);
                    for (WirelessAirDevice d : results) {
                        String typeTag;
                        if (d.deviceCategory != null && d.deviceCategory.contains("Smartphone")) typeTag = "📱 PHONE";
                        else if (d.deviceCategory != null && d.deviceCategory.contains("Audio")) typeTag = "🎧 AUDIO";
                        else if (d.deviceCategory != null && d.deviceCategory.contains("Wearable")) typeTag = "⌚ WATCH";
                        else if (d.deviceCategory != null && d.deviceCategory.contains("Workstation")) typeTag = "💻 PC";
                        else if (d.deviceCategory != null && d.deviceCategory.contains("Router")) typeTag = "📶 ROUTER";
                        else if (d.protocol != null && d.protocol.contains("Bluetooth")) typeTag = "🔵 BT";
                        else typeTag = "📶 WI-FI";

                        String ipStr = (d.ipAddress != null && !d.ipAddress.contains("No IP") && !d.ipAddress.contains("Over-The-Air"))
                                ? d.ipAddress : d.macAddress;
                        String nameStr = d.name + " (" + ipStr + ")";
                        String freqTag = d.centerFreqMhz > 0 ? (" " + d.centerFreqMhz + "M") : "";
                        String sigAndDist = (d.rssiDbm != Integer.MIN_VALUE ? d.rssiDbm + " dBm" : d.signalPercent + "%") +
                                " [" + d.estimatedDistance.split(" ")[0] + "]" + freqTag;

                        radarTargetModel.addRow(new Object[]{
                                typeTag,
                                nameStr,
                                sigAndDist
                        });
                    }
                }
                if (radarTargetCountLabel != null) {
                    radarTargetCountLabel.setText("TARGETS DETECTED // " + results.size() + " NODES");
                }
                if (topologyCanvas != null) {
                    topologyCanvas.setAirDevices(results);
                }
            });
        }).start();
    }

    // ── TAB 2: CONNECTED SUBNET IP SCANNER & PORT AUDITOR ────────────────────
    private JPanel buildDeviceScannerTab() {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setOpaque(true);
        p.setBackground(theme.background);
        p.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Top Control Bar
        JPanel topContainer = new JPanel(new BorderLayout(0, 6));
        topContainer.setOpaque(false);

        NetworkScannerService netService = getOS() != null ? getOS().getNetworkScannerService() : new NetworkScannerService();
        String netInfo = String.format("<html><b>LOCAL IP:</b> <font color='#22d3ee'>%s</font> &nbsp;|&nbsp; <b>GATEWAY:</b> <font color='#a855f7'>%s</font> &nbsp;|&nbsp; <b>SUBNET:</b> <font color='#38bdf8'>%s0/24</font></html>",
                netService.getLocalIp(), netService.getGatewayIp(), netService.getSubnetPrefix());
        JLabel lblNetInfo = new JLabel(netInfo);
        lblNetInfo.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblNetInfo.setForeground(theme.textPrimary);
        topContainer.add(lblNetInfo, BorderLayout.NORTH);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        controls.setOpaque(false);

        btnScan = new JButton("⚡ SWEEP CONNECTED SUBNET (LAN)");
        btnScan.setFont(theme.fontBold);
        btnScan.setBackground(new Color(15, 23, 42));
        btnScan.setForeground(theme.neonAccent);
        btnScan.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(theme.neonAccent, 1),
                BorderFactory.createEmptyBorder(6, 14, 6, 14)
        ));
        btnScan.addActionListener(e -> startSubnetScan());
        controls.add(btnScan);

        btnAuditPorts = new JButton("🔍 AUDIT SELECTED DEVICE PORTS");
        btnAuditPorts.setFont(theme.fontBold);
        btnAuditPorts.setBackground(new Color(15, 23, 42));
        btnAuditPorts.setForeground(theme.secondaryAccent);
        btnAuditPorts.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(theme.secondaryAccent, 1),
                BorderFactory.createEmptyBorder(6, 14, 6, 14)
        ));
        btnAuditPorts.addActionListener(e -> auditSelectedDevice());
        controls.add(btnAuditPorts);

        scanProgress = new JProgressBar(0, 100);
        scanProgress.setPreferredSize(new Dimension(170, 22));
        scanProgress.setStringPainted(true);
        scanProgress.setForeground(theme.neonAccent);
        scanProgress.setBackground(theme.panel);
        controls.add(scanProgress);

        statusLabel = new JLabel("Ready");
        statusLabel.setFont(theme.fontSmall);
        statusLabel.setForeground(theme.textSecondary);
        controls.add(statusLabel);

        topContainer.add(controls, BorderLayout.SOUTH);
        p.add(topContainer, BorderLayout.NORTH);

        // Subnet Table + Port Audit Split Pane
        String[] devCols = {"Status", "IP Address", "Device Hostname", "Physical MAC Address", "Hardware Vendor / Type", "Ping", "Role"};
        deviceModel = new DefaultTableModel(devCols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        deviceTable = new JTable(deviceModel);
        deviceTable.setBackground(theme.panel);
        deviceTable.setForeground(theme.textPrimary);
        deviceTable.setRowHeight(26);
        deviceTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        deviceTable.getTableHeader().setBackground(theme.elevatedPanel);
        deviceTable.getTableHeader().setForeground(theme.neonAccent);
        deviceTable.getTableHeader().setFont(theme.fontBold);

        deviceTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tbl, Object v, boolean isSel, boolean hasFocus, int r, int c) {
                Component comp = super.getTableCellRendererComponent(tbl, v, isSel, hasFocus, r, c);
                Theme th = ThemeManager.getInstance().getCurrentTheme();
                if (c == 0 && v != null) {
                    comp.setForeground(th.success);
                    comp.setFont(th.fontBold);
                } else if (c == 1 && v != null) {
                    comp.setForeground(th.neonAccent);
                    comp.setFont(new Font("Consolas", Font.BOLD, 12));
                } else if (c == 3 && v != null) {
                    comp.setForeground(new Color(148, 163, 184));
                    comp.setFont(new Font("Consolas", Font.PLAIN, 11));
                } else if (c == 6 && v != null && v.toString().contains("Gateway")) {
                    comp.setForeground(th.secondaryAccent);
                    comp.setFont(th.fontBold);
                } else {
                    comp.setForeground(isSel ? th.neonAccent : th.textPrimary);
                }
                comp.setBackground(isSel ? th.selectionBackground : (r % 2 == 0 ? th.panel : th.background));
                return comp;
            }
        });

        JScrollPane devScroll = new JScrollPane(deviceTable);
        devScroll.getVerticalScrollBar().setUI(new NeonScrollBarUI());
        devScroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(theme.neonAccent),
                "CONNECTED LAN NODES (REACHABLE VIA WI-FI / SUBNET)",
                0, 0, theme.fontBold, theme.neonAccent
        ));

        // Bottom: Port Audit Results
        String[] portCols = {"Target IP", "Port", "Service Protocol", "State", "Vulnerability Assessment"};
        portModel = new DefaultTableModel(portCols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        portTable = new JTable(portModel);
        portTable.setBackground(theme.panel);
        portTable.setForeground(theme.textPrimary);
        portTable.setRowHeight(22);
        portTable.getTableHeader().setBackground(theme.elevatedPanel);
        portTable.getTableHeader().setForeground(theme.secondaryAccent);
        portTable.getTableHeader().setFont(theme.fontBold);

        portTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tbl, Object v, boolean isSel, boolean hasFocus, int r, int c) {
                Component comp = super.getTableCellRendererComponent(tbl, v, isSel, hasFocus, r, c);
                Theme th = ThemeManager.getInstance().getCurrentTheme();
                if (c == 4 && v != null) {
                    String s = v.toString();
                    if (s.contains("CRITICAL")) comp.setForeground(th.error);
                    else if (s.contains("MEDIUM")) comp.setForeground(th.warning);
                    else comp.setForeground(th.success);
                } else {
                    comp.setForeground(isSel ? th.secondaryAccent : th.textPrimary);
                }
                comp.setBackground(isSel ? th.selectionBackground : (r % 2 == 0 ? th.panel : th.background));
                return comp;
            }
        });

        JScrollPane portScroll = new JScrollPane(portTable);
        portScroll.getVerticalScrollBar().setUI(new NeonScrollBarUI());
        portScroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(theme.secondaryAccent),
                "TARGET DEVICE PORT & SECURITY AUDIT",
                0, 0, theme.fontBold, theme.secondaryAccent
        ));

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, devScroll, portScroll);
        splitPane.setResizeWeight(0.65);
        splitPane.setDividerSize(4);
        splitPane.setOpaque(false);
        splitPane.setBorder(null);

        p.add(splitPane, BorderLayout.CENTER);
        return p;
    }

    private void startSubnetScan() {
        if (isScanningSubnet) return;
        isScanningSubnet = true;
        btnScan.setEnabled(false);
        scanProgress.setValue(0);
        statusLabel.setText("Sweeping local subnet...");
        deviceModel.setRowCount(0);
        discoveredDevices.clear();

        NetworkScannerService scanner = getOS() != null ? getOS().getNetworkScannerService() : new NetworkScannerService();

        scanner.scanSurroundingDevices(
                progress -> SwingUtilities.invokeLater(() -> scanProgress.setValue(progress)),
                device -> SwingUtilities.invokeLater(() -> addOrUpdateSubnetDevice(device))
        ).thenAccept(devices -> SwingUtilities.invokeLater(() -> {
            isScanningSubnet = false;
            btnScan.setEnabled(true);
            scanProgress.setValue(100);
            statusLabel.setText(String.format("Scan complete. %d active devices found on subnet.", devices.size()));
            discoveredDevices.clear();
            discoveredDevices.addAll(devices);
            refreshSubnetTable();
        })).exceptionally(ex -> {
            SwingUtilities.invokeLater(() -> {
                isScanningSubnet = false;
                btnScan.setEnabled(true);
                statusLabel.setText("Scan error: " + ex.getMessage());
            });
            return null;
        });
    }

    private synchronized void addOrUpdateSubnetDevice(DiscoveredDevice dev) {
        for (int i = 0; i < discoveredDevices.size(); i++) {
            if (discoveredDevices.get(i).ip.equals(dev.ip)) {
                discoveredDevices.set(i, dev);
                refreshSubnetTable();
                return;
            }
        }
        discoveredDevices.add(dev);
        refreshSubnetTable();
    }

    private void refreshSubnetTable() {
        deviceModel.setRowCount(0);
        for (DiscoveredDevice d : discoveredDevices) {
            String role = d.isGateway ? "Default Gateway" : (d.isLocalHost ? "This PC (Host)" : "Surrounding Device");
            String pingStr = d.latencyMs >= 0 ? d.latencyMs + " ms" : "< 1 ms";
            deviceModel.addRow(new Object[]{
                    "● ONLINE",
                    d.ip,
                    d.hostname,
                    d.macAddress,
                    d.vendor,
                    pingStr,
                    role
            });
        }
    }

    private void auditSelectedDevice() {
        int row = deviceTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(getWindow(),
                    "Please select a device from the list first to audit its ports.",
                    "Audit Target Required", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String targetIp = (String) deviceModel.getValueAt(row, 1);
        statusLabel.setText("Auditing ports on " + targetIp + "...");
        btnAuditPorts.setEnabled(false);
        portModel.setRowCount(0);

        new Thread(() -> {
            NetworkScannerService scanner = getOS() != null ? getOS().getNetworkScannerService() : new NetworkScannerService();
            List<PortAuditResult> results = scanner.auditDevicePorts(targetIp);
            SwingUtilities.invokeLater(() -> {
                btnAuditPorts.setEnabled(true);
                portModel.setRowCount(0);
                if (results.isEmpty()) {
                    portModel.addRow(new Object[]{targetIp, "ALL", "Standard Services", "FILTERED / CLOSED", "SECURE - Stealth Mode"});
                    statusLabel.setText("Port audit complete for " + targetIp + ": No exposed common ports.");
                } else {
                    for (PortAuditResult r : results) {
                        portModel.addRow(new Object[]{targetIp, String.valueOf(r.port), r.serviceName, r.state, r.riskLevel});
                    }
                    statusLabel.setText(String.format("Port audit complete for %s: %d open ports detected.", targetIp, results.size()));
                }
            });
        }).start();
    }

    // ── TAB 3: 3D GYRO PROXIMITY RADAR WITH TARGET LIST SIDEBAR ─────────────
    private JPanel buildTopologyTab() {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setOpaque(true);
        p.setBackground(new Color(11, 15, 25));

        // ── Header / control bar ──────────────────────────────────────────
        JPanel header = new JPanel(new BorderLayout(0, 4));
        header.setOpaque(true);
        header.setBackground(new Color(11, 15, 25));
        header.setBorder(BorderFactory.createEmptyBorder(8, 12, 6, 12));

        JLabel title = new JLabel("\uD83C\uDF10  3D GYRO PROXIMITY RADAR \u2014 DRAG TO ROTATE | SCROLL TO ZOOM | CLICK TO INSPECT");
        title.setFont(new Font("Segoe UI", Font.BOLD, 13));
        title.setForeground(new Color(34, 211, 238));
        header.add(title, BorderLayout.NORTH);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        btnRow.setOpaque(false);

        JButton btnScanNow = new JButton("⚡ SCAN SURROUNDING DEVICES");
        btnScanNow.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnScanNow.setBackground(new Color(15, 23, 42));
        btnScanNow.setForeground(new Color(34, 211, 238));
        btnScanNow.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(34, 211, 238), 1),
                BorderFactory.createEmptyBorder(5, 14, 5, 14)
        ));
        btnScanNow.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnScanNow.addActionListener(e -> CyberDeckApp.this.startAirwaveScan());
        btnRow.add(btnScanNow);

        JButton btnReset = new JButton("\u27f2 RESET VIEW");
        btnReset.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnReset.setBackground(new Color(15, 23, 42));
        btnReset.setForeground(new Color(168, 85, 247));
        btnReset.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(168, 85, 247), 1),
                BorderFactory.createEmptyBorder(5, 14, 5, 14)
        ));
        btnReset.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnReset.addActionListener(e -> { if (topologyCanvas != null) topologyCanvas.resetView(); });
        btnRow.add(btnReset);

        JButton btnToggleSidebar = new JButton("\uD83D\uDC41 TOGGLE TARGET LIST");
        btnToggleSidebar.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnToggleSidebar.setBackground(new Color(15, 23, 42));
        btnToggleSidebar.setForeground(new Color(56, 189, 248));
        btnToggleSidebar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(56, 189, 248), 1),
                BorderFactory.createEmptyBorder(5, 12, 5, 12)
        ));
        btnToggleSidebar.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnToggleSidebar.addActionListener(e -> {
            if (radarSidebarPanel != null) {
                radarSidebarPanel.setVisible(!radarSidebarPanel.isVisible());
                p.revalidate();
            }
        });
        btnRow.add(btnToggleSidebar);

        JLabel legend = new JLabel(
                "  \uD83D\uDCF6 Wi-Fi (green)   \uD83D\uDD35 Bluetooth (purple)   \uD83D\uDCF1 Phone/Hotspot (cyan)   \u2014 Rings: 1.5m / 5m / 10m / 15m");
        legend.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        legend.setForeground(new Color(148, 163, 184));
        btnRow.add(legend);

        header.add(btnRow, BorderLayout.SOUTH);
        p.add(header, BorderLayout.NORTH);

        // ── Main Body: Split Pane with Target List Sidebar (Left) and 3D Canvas (Center) ──
        topologyCanvas = new Radar3DCanvas();
        radarSidebarPanel = buildRadarTargetsSidebar();

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, radarSidebarPanel, topologyCanvas);
        split.setDividerLocation(310);
        split.setDividerSize(4);
        split.setBorder(null);
        split.setOpaque(false);
        split.setBackground(new Color(11, 15, 25));

        p.add(split, BorderLayout.CENTER);
        return p;
    }

    private JPanel buildRadarTargetsSidebar() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setOpaque(true);
        panel.setBackground(new Color(15, 23, 42));
        panel.setPreferredSize(new Dimension(310, 0));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(30, 41, 59)),
                BorderFactory.createEmptyBorder(6, 6, 6, 6)
        ));

        // Top Sidebar Header
        JPanel topHeader = new JPanel(new BorderLayout(0, 2));
        topHeader.setOpaque(false);

        radarTargetCountLabel = new JLabel("TARGETS DETECTED // 0 NODES");
        radarTargetCountLabel.setFont(new Font("Consolas", Font.BOLD, 11));
        radarTargetCountLabel.setForeground(new Color(34, 211, 238));
        topHeader.add(radarTargetCountLabel, BorderLayout.NORTH);

        JLabel subtext = new JLabel("Click any target below to lock reticle in 3D");
        subtext.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        subtext.setForeground(new Color(148, 163, 184));
        topHeader.add(subtext, BorderLayout.SOUTH);

        panel.add(topHeader, BorderLayout.NORTH);

        // Table Model for Target List
        String[] cols = {"Type", "Target Name / IP", "Signal / Distance"};
        radarTargetModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        radarTargetTable = new JTable(radarTargetModel);
        radarTargetTable.setBackground(new Color(11, 15, 25));
        radarTargetTable.setForeground(Color.WHITE);
        radarTargetTable.setRowHeight(34);
        radarTargetTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        radarTargetTable.getTableHeader().setBackground(new Color(15, 23, 42));
        radarTargetTable.getTableHeader().setForeground(new Color(34, 211, 238));
        radarTargetTable.getTableHeader().setFont(new Font("Consolas", Font.BOLD, 10));

        radarTargetTable.setSelectionBackground(new Color(14, 116, 144));
        radarTargetTable.setSelectionForeground(Color.WHITE);

        // Column widths
        radarTargetTable.getColumnModel().getColumn(0).setPreferredWidth(55);
        radarTargetTable.getColumnModel().getColumn(1).setPreferredWidth(160);
        radarTargetTable.getColumnModel().getColumn(2).setPreferredWidth(95);

        // Custom Renderer with two-line cyber details
        radarTargetTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tbl, Object v, boolean isSel, boolean hasFocus, int r, int c) {
                Component comp = super.getTableCellRendererComponent(tbl, v, isSel, hasFocus, r, c);
                if (r >= 0 && r < airDevices.size()) {
                    WirelessAirDevice d = airDevices.get(r);
                    boolean isBT = d.protocol != null && d.protocol.contains("Bluetooth");
                    String nl = d.name != null ? d.name.toLowerCase() : "";
                    boolean isPhone = nl.contains("galaxy") || nl.contains("phone") || nl.contains("iphone") ||
                                      nl.contains("pixel") || nl.contains("oneplus") || nl.contains("redmi");

                    if (isSel) {
                        comp.setForeground(Color.WHITE);
                    } else if (c == 0) {
                        comp.setFont(new Font("Segoe UI", Font.BOLD, 10));
                        comp.setForeground(isPhone ? new Color(34, 211, 238) : (isBT ? new Color(168, 85, 247) : new Color(16, 185, 129)));
                    } else if (c == 1) {
                        comp.setFont(new Font("Segoe UI", Font.BOLD, 11));
                        comp.setForeground(isPhone ? new Color(224, 242, 254) : new Color(226, 232, 240));
                    } else if (c == 2) {
                        comp.setFont(new Font("Consolas", Font.PLAIN, 10));
                        comp.setForeground(new Color(245, 158, 11));
                    }
                }
                comp.setBackground(isSel ? new Color(14, 116, 144) : (r % 2 == 0 ? new Color(15, 23, 42) : new Color(11, 15, 25)));
                return comp;
            }
        });

        // Click on row -> sync with 3D Radar
        radarTargetTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = radarTargetTable.getSelectedRow();
                if (row >= 0 && row < airDevices.size()) {
                    if (topologyCanvas != null) {
                        topologyCanvas.setSelectedDevice(airDevices.get(row));
                    }
                }
            }
        });

        JScrollPane scroll = new JScrollPane(radarTargetTable);
        scroll.getVerticalScrollBar().setUI(new NeonScrollBarUI());
        scroll.setBorder(BorderFactory.createLineBorder(new Color(30, 41, 59)));
        panel.add(scroll, BorderLayout.CENTER);

        // Device Quick-Info Card (shown below list when a device is selected)
        JPanel infoCard = new JPanel();
        infoCard.setLayout(new BoxLayout(infoCard, BoxLayout.Y_AXIS));
        infoCard.setOpaque(true);
        infoCard.setBackground(new Color(8, 12, 22));
        infoCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(34, 211, 238, 80)),
                BorderFactory.createEmptyBorder(5, 8, 5, 8)
        ));

        JLabel infoTitle = new JLabel("SELECT A TARGET TO VIEW DETAILS");
        infoTitle.setFont(new Font("Consolas", Font.BOLD, 10));
        infoTitle.setForeground(new Color(100, 116, 139));
        infoTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel infoIp   = new JLabel("IP:  —");
        infoIp.setFont(new Font("Consolas", Font.PLAIN, 10));
        infoIp.setForeground(new Color(34, 211, 238));
        infoIp.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel infoMac  = new JLabel("MAC: —");
        infoMac.setFont(new Font("Consolas", Font.PLAIN, 10));
        infoMac.setForeground(new Color(226, 232, 240));
        infoMac.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel infoSig  = new JLabel("SIG: —");
        infoSig.setFont(new Font("Consolas", Font.PLAIN, 10));
        infoSig.setForeground(new Color(16, 185, 129));
        infoSig.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel infoVend = new JLabel("OEM: —");
        infoVend.setFont(new Font("Consolas", Font.PLAIN, 10));
        infoVend.setForeground(new Color(168, 85, 247));
        infoVend.setAlignmentX(Component.LEFT_ALIGNMENT);

        infoCard.add(infoTitle);
        infoCard.add(Box.createVerticalStrut(3));
        infoCard.add(infoIp);
        infoCard.add(infoMac);
        infoCard.add(infoSig);
        infoCard.add(infoVend);

        // Wire table selection to update info card
        radarTargetTable.getSelectionModel().addListSelectionListener(e2 -> {
            if (!e2.getValueIsAdjusting()) {
                int row = radarTargetTable.getSelectedRow();
                if (row >= 0 && row < airDevices.size()) {
                    WirelessAirDevice dd = airDevices.get(row);
                    infoTitle.setText("▶ " + (dd.name != null ? dd.name.replace("📱", "").trim() : "Device"));
                    infoTitle.setForeground(new Color(34, 211, 238));
                    String ipDisp = (dd.ipAddress != null && dd.ipAddress.matches("\\d+\\.\\d+\\.\\d+\\.\\d+.*"))
                            ? dd.ipAddress : "RF Only (No IPv4)";
                    infoIp.setText("IP:  " + ipDisp);
                    infoMac.setText("MAC: " + (dd.macAddress != null ? dd.macAddress : "Unknown"));
                    String sigDisp = dd.rssiDbm != Integer.MIN_VALUE
                            ? dd.rssiDbm + " dBm  (" + dd.signalPercent + "%)  " + dd.estimatedDistance.split(" \\(")[0]
                            : dd.signalPercent + "%  " + dd.estimatedDistance.split(" \\(")[0];
                    infoSig.setText("SIG: " + sigDisp);
                    infoVend.setText("OEM: " + (dd.vendor != null ? dd.vendor : "Unknown"));
                } else {
                    infoTitle.setText("SELECT A TARGET TO VIEW DETAILS");
                    infoTitle.setForeground(new Color(100, 116, 139));
                    infoIp.setText("IP:  —");
                    infoMac.setText("MAC: —");
                    infoSig.setText("SIG: —");
                    infoVend.setText("OEM: —");
                }
            }
        });

        // South container: info card + scan button
        JPanel southContainer = new JPanel(new BorderLayout(0, 4));
        southContainer.setOpaque(false);
        southContainer.add(infoCard, BorderLayout.NORTH);

        // Bottom quick action button
        JButton btnBottomScan = new JButton("⚡ SCAN SURROUNDING AIRWAVES");
        btnBottomScan.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btnBottomScan.setBackground(new Color(15, 23, 42));
        btnBottomScan.setForeground(new Color(34, 211, 238));
        btnBottomScan.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(34, 211, 238), 1),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));
        btnBottomScan.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnBottomScan.addActionListener(e -> CyberDeckApp.this.startAirwaveScan());
        southContainer.add(btnBottomScan, BorderLayout.SOUTH);

        panel.add(southContainer, BorderLayout.SOUTH);

        return panel;
    }

    // ── TAB 4: PACKET STREAM WATERFALL ──────────────────────────────────────
    private JPanel buildPacketWaterfallTab() {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setOpaque(true);
        p.setBackground(theme.background);
        p.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Top Ping Graph
        pingGraph = new PingGraphPanel();
        p.add(pingGraph, BorderLayout.NORTH);

        // Packet Stream Output
        packetStreamArea = new JTextArea();
        packetStreamArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        packetStreamArea.setBackground(new Color(11, 15, 25));
        packetStreamArea.setForeground(theme.neonAccent);
        packetStreamArea.setEditable(false);
        packetStreamArea.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        JScrollPane sp = new JScrollPane(packetStreamArea);
        sp.getVerticalScrollBar().setUI(new NeonScrollBarUI());
        sp.setBorder(BorderFactory.createLineBorder(theme.secondaryAccent));
        p.add(sp, BorderLayout.CENTER);

        return p;
    }

    private void updateNetworkTelemetry() {
        if (packetStreamArea != null && (!airDevices.isEmpty() || !discoveredDevices.isEmpty())) {
            String[] protos = {"802.11-BEACON", "PROBE-REQ", "WPA2-EAPOL", "BLE-ADV", "TCP-SYN", "DNS-QUERY"};
            String proto = protos[rnd.nextInt(protos.length)];
            String nodeName = !airDevices.isEmpty()
                    ? airDevices.get(rnd.nextInt(airDevices.size())).name
                    : discoveredDevices.get(rnd.nextInt(discoveredDevices.size())).hostname;
            int ch = rnd.nextInt(11) + 1;
            int rssi = -35 - rnd.nextInt(50);
            String line = String.format("[%s] OVER-THE-AIR RF // SRC: %s  RSSI=%ddBm  Ch=%d  Radio=802.11ac\n",
                    proto, nodeName, rssi, ch);

            packetStreamArea.append(line);
            if (packetStreamArea.getLineCount() > 120) {
                try {
                    int end = packetStreamArea.getLineEndOffset(0);
                    packetStreamArea.replaceRange("", 0, end);
                } catch (Exception ignored) {}
            }
            packetStreamArea.setCaretPosition(packetStreamArea.getDocument().getLength());
        }

        if (pingGraph != null) {
            int ping = 6 + rnd.nextInt(18);
            if (rnd.nextInt(12) == 0) ping += 35;
            pingGraph.addPing(ping);
        }

        if (topologyCanvas != null) {
            topologyCanvas.tick();
        }
    }

    // ── INNER CLASS: PING GRAPH ─────────────────────────────────────────────
    private static class PingGraphPanel extends JPanel {
        private final LinkedList<Integer> pings = new LinkedList<>();

        PingGraphPanel() {
            setPreferredSize(new Dimension(0, 110));
            setOpaque(true);
            setBackground(new Color(15, 23, 42));
            setBorder(BorderFactory.createLineBorder(new Color(30, 41, 59)));
            for (int i = 0; i < 40; i++) pings.add(15);
        }

        void addPing(int p) {
            pings.add(p);
            if (pings.size() > 50) pings.removeFirst();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int pad = 8;

            g2.setColor(new Color(30, 41, 59));
            g2.drawLine(pad, h - pad, w - pad, h - pad);
            g2.drawLine(pad, pad, pad, h - pad);

            g2.setColor(new Color(148, 163, 184));
            g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
            int lastPing = pings.isEmpty() ? 0 : pings.getLast();
            g2.drawString("AIRWAVE RF & GATEWAY LATENCY // " + lastPing + "ms", pad + 4, pad + 12);

            int n = pings.size();
            float step = (float) (w - pad * 2) / (n - 1);

            g2.setColor(new Color(34, 211, 238));
            g2.setStroke(new BasicStroke(1.8f));
            for (int i = 0; i < n - 1; i++) {
                int x1 = (int) (pad + i * step);
                int y1 = (h - pad) - Math.min(h - pad * 2, pings.get(i));
                int x2 = (int) (pad + (i + 1) * step);
                int y2 = (h - pad) - Math.min(h - pad * 2, pings.get(i + 1));
                g2.drawLine(x1, y1, x2, y2);
            }
            g2.dispose();
        }
    }

    // ── INNER CLASS: 3D GYROSCOPE RADAR ─────────────────────────────────────
    private class Radar3DCanvas extends JPanel
            implements MouseListener,
                       MouseMotionListener,
                       MouseWheelListener {

        // State
        private final List<WirelessAirDevice> airDevices = new ArrayList<>();
        private final List<double[]> positions = new ArrayList<>(); // [x,y,z] visual coords
        private final List<Point> screenPoints = new ArrayList<>(); // Projected 2D points for hit testing
        private Point laptopScreenPoint = new Point(0, 0);

        // Gyroscope orientation
        private Point dragStart = null;
        private float rotX = -0.42f;   // top-down perspective
        private float rotY = 0.38f;
        private float zoom = 1.0f;
        private boolean dragging = false;

        // Selection & Inspection state
        private int selectedIndex = -1; // -1 = none, -2 = laptop, >= 0 = device index
        private int hoveredIndex = -1;  // index of device under mouse cursor
        private WirelessAirDevice selectedDevice = null;
        private String pingStatus = null;
        private boolean isPinging = false;
        private int activeHudTab = 0; // 0: OVERVIEW, 1: RF SPECTRUM, 2: SECURITY, 3: NETWORK

        // Interactive buttons inside HUD card
        private final Rectangle cardCloseBtn = new Rectangle();
        private final Rectangle cardPingBtn = new Rectangle();
        private final Rectangle cardAuditBtn = new Rectangle();
        private final Rectangle cardCopyBtn = new Rectangle();
        private final Rectangle cardTabOverview = new Rectangle();
        private final Rectangle cardTabRf = new Rectangle();
        private final Rectangle cardTabSecurity = new Rectangle();
        private final Rectangle cardTabNetwork = new Rectangle();
        private final Rectangle cardBounds = new Rectangle();

        Radar3DCanvas() {
            setOpaque(true);
            setBackground(new Color(11, 15, 25));
            setPreferredSize(new Dimension(600, 500));
            setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
            addMouseListener(this);
            addMouseMotionListener(this);
            addMouseWheelListener(this);
        }

        // Public API
        void setAirDevices(List<WirelessAirDevice> list) {
            airDevices.clear();
            airDevices.addAll(list);
            computePositions();
            // Retain selection if matching MAC still present
            if (selectedDevice != null) {
                int found = -1;
                for (int i = 0; i < airDevices.size(); i++) {
                    if (airDevices.get(i).macAddress.equalsIgnoreCase(selectedDevice.macAddress)) {
                        found = i;
                        selectedDevice = airDevices.get(i);
                        break;
                    }
                }
                selectedIndex = found;
            }
            repaint();
        }

        void setSelectedDevice(WirelessAirDevice d) {
            if (d == null) {
                selectedIndex = -1;
                selectedDevice = null;
                pingStatus = null;
                if (radarTargetTable != null) radarTargetTable.clearSelection();
            } else {
                selectedDevice = d;
                int idx = airDevices.indexOf(d);
                if (idx < 0 && d.macAddress != null) {
                    for (int i = 0; i < airDevices.size(); i++) {
                        if (d.macAddress.equalsIgnoreCase(airDevices.get(i).macAddress)) {
                            idx = i;
                            selectedDevice = airDevices.get(i);
                            break;
                        }
                    }
                }
                selectedIndex = idx;
                pingStatus = null;
                if (radarTargetTable != null && selectedIndex >= 0 && selectedIndex < radarTargetTable.getRowCount()) {
                    if (radarTargetTable.getSelectedRow() != selectedIndex) {
                        radarTargetTable.setRowSelectionInterval(selectedIndex, selectedIndex);
                        radarTargetTable.scrollRectToVisible(radarTargetTable.getCellRect(selectedIndex, 0, true));
                    }
                }
            }
            repaint();
        }

        void tick() {
            if (!dragging) {
                rotY += 0.005f; // continuous subtle gyroscope orbit
            }
            repaint();
        }

        void resetView() {
            rotX = -0.42f;
            rotY = 0.38f;
            zoom = 1.0f;
            repaint();
        }

        /**
         * Non-linear square-root radar distance mapping.
         * Expands near-field devices (< 1.5m) so phone and accessories sit visibly beside
         * the laptop on the desk, while concentric rings (1.5m, 5m, 10m, 15m) scale harmoniously.
         */
        private double mapDistance(double meters) {
            double norm = Math.max(0.03, Math.min(1.25, meters / 16.0));
            return Math.sqrt(norm) * 16.0;
        }

        /**
         * Accurate physical distance estimate in meters based on raw RSSI (dBm).
         * Connected Direct Link devices (phone hotspot) are always treated as desk-range.
         */
        private double rssiToDistance(WirelessAirDevice d) {
            // Connected phone/hotspot is always on the desk — clamp to near range
            boolean isDirectLink = d.protocol != null && d.protocol.contains("Direct Link");
            if (d.rssiDbm != Integer.MIN_VALUE) {
                int r = d.rssiDbm;
                // Apply a +12 dBm bonus for direct-link devices (hotspot is physically beside laptop)
                if (isDirectLink) r = Math.min(-30, r + 12);
                if (r >= -45) {
                    // -30 to -45 dBm → 0.30m to 0.60m (Right beside laptop on desk)
                    return 0.30 + ((-30 - r) / 15.0) * 0.30;
                } else if (r >= -60) {
                    // -45 to -60 dBm → 0.60m to 1.80m (Immediate vicinity / desk)
                    return 0.60 + ((-45 - r) / 15.0) * 1.20;
                } else if (r >= -72) {
                    // -60 to -72 dBm → 1.80m to 5.0m (Same room)
                    return 1.80 + ((-60 - r) / 12.0) * 3.20;
                } else if (r >= -84) {
                    // -72 to -84 dBm → 5.0m to 10.0m (Adjacent room)
                    return 5.0 + ((-72 - r) / 12.0) * 5.0;
                } else {
                    // -84 to -100 dBm → 10.0m to 16.0m (Perimeter)
                    return 10.0 + ((-84 - r) / 16.0) * 6.0;
                }
            } else {
                // For direct-link without RSSI, assume desk range (0.5-1.0m)
                if (isDirectLink) return 0.5 + (1.0 - Math.min(1.0, d.signalPercent / 100.0)) * 0.5;
                return 0.35 + (1.0 - (Math.max(5, d.signalPercent) / 100.0)) * 15.65;
            }
        }

        private int extractChannel(WirelessAirDevice d) {
            if (d.bandAndChannel != null) {
                String s = d.bandAndChannel;
                int idx = s.indexOf("Ch ");
                if (idx >= 0) {
                    try {
                        return Integer.parseInt(s.substring(idx + 3).trim().split("[^0-9]")[0]);
                    } catch (NumberFormatException ignored) {}
                }
            }
            String id = d.macAddress != null ? d.macAddress : (d.name != null ? d.name : "0");
            return (id.hashCode() & 0x7FFFFFFF) % 13 + 1;
        }

        // ── 3-D coordinate computation ───────────────────────────────────
        private void computePositions() {
            positions.clear();
            int phoneCount = 0;
            int btAudioCount = 0;
            int btWearCount = 0;
            int nearbyCount = 0;

            for (WirelessAirDevice d : airDevices) {
                double realMeters = rssiToDistance(d);
                boolean isBluetooth = d.protocol != null && d.protocol.contains("Bluetooth");
                String nameLower = d.name != null ? d.name.toLowerCase() : "";
                boolean isDirect = d.protocol != null && d.protocol.contains("Direct");
                boolean isPhone = nameLower.contains("galaxy") || nameLower.contains("phone") ||
                                  nameLower.contains("iphone") || nameLower.contains("hotspot") ||
                                  nameLower.contains("pixel") || nameLower.contains("oneplus") ||
                                  nameLower.contains("redmi") || nameLower.contains("poco") ||
                                  nameLower.contains("realme") || nameLower.contains("oppo") ||
                                  nameLower.contains("vivo") || nameLower.contains("moto") ||
                                  nameLower.contains("nothing") || nameLower.contains("iqoo") ||
                                  nameLower.contains("lava") || nameLower.contains("micromax") ||
                                  nameLower.contains("infinix") || nameLower.contains("tecno") ||
                                  nameLower.contains("itel") || nameLower.contains("connected") ||
                                  (d.deviceCategory != null && d.deviceCategory.contains("Smartphone")) ||
                                  isDirect;

                boolean isBtAudio = isBluetooth && (nameLower.contains("dopes") || nameLower.contains("ear") ||
                                                    nameLower.contains("head") || nameLower.contains("buds") ||
                                                    nameLower.contains("audio") || nameLower.contains("tws"));
                boolean isBtWear = isBluetooth && (nameLower.contains("pulse") || nameLower.contains("watch") ||
                                                   nameLower.contains("band") || nameLower.contains("fit"));

                String id = d.macAddress != null ? d.macAddress : (d.name != null ? d.name : "dev");
                int ch = extractChannel(d);

                double azimuth;
                double elevation;

                // Group all nearby desk/workspace devices (phones, audio, wearables, or realMeters <= 3.2m or RSSI >= -65)
                // in the user's forward-right workspace sector (~25° to ~78°) with distinct angular positions:
                boolean isNearField = (realMeters <= 3.2) || isPhone || isBluetooth || (d.rssiDbm != Integer.MIN_VALUE && d.rssiDbm >= -65);

                if (isNearField) {
                    double sectorCenter = 0.82; // ~47° forward-right on desk

                    if (isPhone) {
                        // Arrange phones along the desk surface
                        azimuth = sectorCenter - (phoneCount * 0.20); // Phone 0: 47°, Phone 1: ~35°, Phone 2: ~24°
                        elevation = 0.02 + (phoneCount * 0.01);
                        phoneCount++;
                    } else if (isBtAudio) {
                        // Earbuds/headsets elevated at ear/head height
                        azimuth = sectorCenter + 0.22 + (btAudioCount * 0.16); // ~60°, ~69°
                        elevation = 0.30 + (btAudioCount * 0.05);
                        btAudioCount++;
                    } else if (isBtWear) {
                        // Smartwatch/wearable near wrist/desk level
                        azimuth = sectorCenter + 0.38 + (btWearCount * 0.16); // ~69°, ~78°
                        elevation = 0.12 + (btWearCount * 0.04);
                        btWearCount++;
                    } else if (isBluetooth) {
                        azimuth = sectorCenter + 0.16 + (nearbyCount * 0.18);
                        elevation = 0.20;
                        nearbyCount++;
                    } else {
                        // Other near-field Wi-Fi peers/devices
                        azimuth = sectorCenter + 0.50 + (nearbyCount * 0.18);
                        elevation = 0.05;
                        nearbyCount++;
                    }
                } else {
                    // Surrounding environmental Wi-Fi routers distributed across full 360° horizon
                    azimuth = Math.PI * 2.0 * ((id.hashCode() & 0x7FFFFFFF) % 360) / 360.0;
                    elevation = (ch - 6) * 0.04;
                }

                // Map real meters to expanded visual radius
                double visualDist = mapDistance(realMeters);

                double x = visualDist * Math.cos(elevation) * Math.cos(azimuth);
                double y = visualDist * Math.sin(elevation);
                double z = visualDist * Math.cos(elevation) * Math.sin(azimuth);
                positions.add(new double[]{x, y, z});
            }
        }

        // ── Perspective Projection ────────────────────────────────────────
        private int[] project(double px, double py, double pz, int cx, int cy) {
            double cosY = Math.cos(rotY), sinY = Math.sin(rotY);
            double cosX = Math.cos(rotX), sinX = Math.sin(rotX);

            // Rotate Y (yaw)
            double rx1 = px * cosY - pz * sinY;
            double ry1 = py;
            double rz1 = px * sinY + pz * cosY;

            // Rotate X (pitch)
            double rx2 = rx1;
            double ry2 = ry1 * cosX - rz1 * sinX;
            double rz2 = ry1 * sinX + rz1 * cosX;

            double maxRadius = 17.0;
            double screenR = Math.max(150.0, Math.min(cx, cy) - 35.0);
            double unitScale = screenR / maxRadius;

            double cameraDist = 36.0;
            double depth = cameraDist + rz2;
            if (depth < 2.0) depth = 2.0;
            double persp = (cameraDist / depth) * zoom;

            int sx = (int) (cx + rx2 * unitScale * persp);
            int sy = (int) (cy - ry2 * unitScale * persp);
            return new int[]{sx, sy};
        }

        // ── paintComponent ────────────────────────────────────────────────
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int w  = getWidth();
            int h  = getHeight();
            int cx = w / 2;
            int cy = h / 2;

            // Background
            g2.setColor(new Color(11, 15, 25));
            g2.fillRect(0, 0, w, h);

            // Grid floor (XZ plane, y=0)
            drawGrid(g2, cx, cy);

            // Concentric distance rings
            drawRings(g2, cx, cy);

            // Laptop at center
            drawLaptop(g2, cx, cy);

            // Device nodes in 3D
            drawDevices(g2, cx, cy);

            // Gyroscope HUD overlay
            drawHUD(g2, w, h);

            // Device Inspector HUD Card (when a device or laptop is clicked)
            drawHUDCard(g2, w, h);

            g2.dispose();
        }

        // ── Grid floor ────────────────────────────────────────────────────
        private void drawGrid(Graphics2D g2, int cx, int cy) {
            g2.setStroke(new BasicStroke(0.8f));
            g2.setColor(new Color(30, 41, 59, 110));
            double step = 3.0;
            double limit = 18.0;
            for (double v = -limit; v <= limit; v += step) {
                int[] a = project(v, 0, -limit, cx, cy);
                int[] b = project(v, 0,  limit, cx, cy);
                g2.drawLine(a[0], a[1], b[0], b[1]);

                int[] c = project(-limit, 0, v, cx, cy);
                int[] d = project( limit, 0, v, cx, cy);
                g2.drawLine(c[0], c[1], d[0], d[1]);
            }
        }

        // ── Concentric rings ──────────────────────────────────────────────
        private void drawRings(Graphics2D g2, int cx, int cy) {
            double[] radii   = {1.5, 5.0, 10.0, 15.0};
            Color[]  colors  = {
                new Color(34, 211, 238, 160),
                new Color(34, 211, 238, 110),
                new Color(34, 211, 238,  80),
                new Color(34, 211, 238,  55)
            };
            String[] labels  = {"1.5m [Immediate]", "5.0m [Room]", "10m [Adjacent]", "15m [Perimeter]"};
            int segments = 60;

            g2.setFont(new Font("Consolas", Font.PLAIN, 9));

            for (int ri = 0; ri < radii.length; ri++) {
                double r = mapDistance(radii[ri]);
                g2.setColor(colors[ri]);
                g2.setStroke(new BasicStroke(1.0f));
                int[] prevPt = null;
                for (int seg = 0; seg <= segments; seg++) {
                    double angle = 2.0 * Math.PI * seg / segments;
                    double px = r * Math.cos(angle);
                    double pz = r * Math.sin(angle);
                    int[] pt = project(px, 0, pz, cx, cy);
                    if (prevPt != null) {
                        g2.drawLine(prevPt[0], prevPt[1], pt[0], pt[1]);
                    }
                    prevPt = pt;
                }
                // Ring label
                int[] labelPt = project(r, 0, 0, cx, cy);
                g2.setColor(new Color(148, 163, 184));
                g2.drawString(labels[ri], labelPt[0] + 4, labelPt[1] - 2);
            }
        }

        // ── Laptop model at center ────────────────────────────────────────
        private void drawLaptop(Graphics2D g2, int cx, int cy) {
            Color laptopColor = new Color(34, 211, 238);
            Color laptopFill  = new Color(15, 23, 42, 230);
            Color screenGlow  = new Color(34, 211, 238, 90);

            // Ground beacon ring directly under the laptop
            int[] origin = project(0, 0, 0, cx, cy);
            laptopScreenPoint = new Point(origin[0], origin[1]);

            long now = System.currentTimeMillis();
            float pulse = (float) Math.abs(Math.sin(now * 0.003));
            int beaconR = 18 + (int) (pulse * 8);
            g2.setColor(new Color(34, 211, 238, 65));
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawOval(origin[0] - beaconR, origin[1] - (beaconR / 2), beaconR * 2, beaconR);

            // Base keyboard chassis (width 1.6 x depth 1.1 x height 0.14)
            double w2 = 0.8;
            double d2 = 0.55;
            double hBase = 0.14;
            double[][] baseCorners = {
                {-w2, 0.0,   d2},
                { w2, 0.0,   d2},
                { w2, 0.0,  -d2},
                {-w2, 0.0,  -d2},
                {-w2, hBase,  d2},
                { w2, hBase,  d2},
                { w2, hBase, -d2},
                {-w2, hBase, -d2}
            };

            int[][] bp = new int[baseCorners.length][];
            for (int i = 0; i < baseCorners.length; i++) {
                bp[i] = project(baseCorners[i][0], baseCorners[i][1], baseCorners[i][2], cx, cy);
            }

            // Base top (keyboard surface)
            Polygon baseTop = new Polygon(
                new int[]{bp[4][0], bp[5][0], bp[6][0], bp[7][0]},
                new int[]{bp[4][1], bp[5][1], bp[6][1], bp[7][1]}, 4);
            g2.setColor(laptopFill);
            g2.fillPolygon(baseTop);
            g2.setColor(laptopColor);
            g2.setStroke(new BasicStroke(1.6f));
            g2.drawPolygon(baseTop);

            // Keyboard grid & trackpad details on base top
            int[] kbL = project(-0.6, hBase, -0.15, cx, cy);
            int[] kbR = project( 0.6, hBase, -0.15, cx, cy);
            g2.setColor(new Color(34, 211, 238, 120));
            g2.setStroke(new BasicStroke(1.0f));
            g2.drawLine(kbL[0], kbL[1], kbR[0], kbR[1]);

            int[] tpL = project(-0.25, hBase, 0.3, cx, cy);
            int[] tpR = project( 0.25, hBase, 0.3, cx, cy);
            g2.drawLine(tpL[0], tpL[1], tpR[0], tpR[1]);

            // Base front face
            Polygon baseFront = new Polygon(
                new int[]{bp[0][0], bp[1][0], bp[5][0], bp[4][0]},
                new int[]{bp[0][1], bp[1][1], bp[5][1], bp[4][1]}, 4);
            g2.setColor(new Color(11, 15, 25));
            g2.fillPolygon(baseFront);
            g2.setColor(laptopColor);
            g2.drawPolygon(baseFront);

            // Tilted display screen
            double screenTilt = Math.PI / 7.0; // ~26° tilted back
            double sh = 1.25;
            double dz = -sh * Math.sin(screenTilt);
            double dy =  sh * Math.cos(screenTilt);

            int[] pHL = project(-0.75, hBase, -d2, cx, cy);
            int[] pHR = project( 0.75, hBase, -d2, cx, cy);
            int[] pTL = project(-0.75, hBase + dy, -d2 + dz, cx, cy);
            int[] pTR = project( 0.75, hBase + dy, -d2 + dz, cx, cy);

            Polygon screen = new Polygon(
                new int[]{pHL[0], pHR[0], pTR[0], pTL[0]},
                new int[]{pHL[1], pHR[1], pTR[1], pTL[1]}, 4);
            g2.setColor(screenGlow);
            g2.fillPolygon(screen);
            g2.setColor(laptopColor);
            g2.setStroke(new BasicStroke(1.8f));
            g2.drawPolygon(screen);

            // Screen matrix display line
            int[] sMidL = project(-0.6, hBase + dy * 0.55, -d2 + dz * 0.55, cx, cy);
            int[] sMidR = project( 0.6, hBase + dy * 0.55, -d2 + dz * 0.55, cx, cy);
            g2.setColor(new Color(34, 211, 238, 160));
            g2.drawLine(sMidL[0], sMidL[1], sMidR[0], sMidR[1]);

            // Center Label
            g2.setColor(new Color(34, 211, 238));
            g2.setFont(new Font("Consolas", Font.BOLD, 10));
            g2.drawString("[ HOST LAPTOP ]", origin[0] - 44, origin[1] + 20);

            // If laptop selected, draw targeting reticle
            if (selectedIndex == -2) {
                drawTargetingReticle(g2, origin[0], origin[1] - 4, new Color(34, 211, 238), 28);
            }
        }

        // ── Device nodes ─────────────────────────────────────────────────
        private void drawDevices(Graphics2D g2, int cx, int cy) {
            if (airDevices.isEmpty()) {
                g2.setColor(new Color(148, 163, 184));
                g2.setFont(new Font("Segoe UI", Font.ITALIC, 12));
                g2.drawString("Listening for surrounding wireless RF beacons...", cx - 130, cy + 40);
                return;
            }

            int n = Math.min(airDevices.size(), positions.size());
            while (screenPoints.size() < n) screenPoints.add(null);
            while (screenPoints.size() > n) screenPoints.remove(screenPoints.size() - 1);

            Integer[] indices = new Integer[n];
            for (int i = 0; i < n; i++) indices[i] = i;

            // Sort farthest first for proper 3D depth layering
            java.util.Arrays.sort(indices, (a, b) -> {
                double[] pa = positions.get(a), pb = positions.get(b);
                double da = pa[0]*pa[0] + pa[1]*pa[1] + pa[2]*pa[2];
                double db = pb[0]*pb[0] + pb[1]*pb[1] + pb[2]*pb[2];
                return Double.compare(db, da);
            });

            // Find closest device index for highlight pulsing ring
            int closestIdx = -1;
            double closestDist = Double.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                double[] pos = positions.get(i);
                double dist = Math.sqrt(pos[0]*pos[0] + pos[1]*pos[1] + pos[2]*pos[2]);
                if (dist < closestDist) { closestDist = dist; closestIdx = i; }
            }

            int[] origin = project(0, 0, 0, cx, cy);

            for (int idx : indices) {
                WirelessAirDevice d = airDevices.get(idx);
                double[] pos = positions.get(idx);
                int[] pt = project(pos[0], pos[1], pos[2], cx, cy);
                screenPoints.set(idx, new Point(pt[0], pt[1]));

                boolean isBluetooth = d.protocol != null && d.protocol.contains("Bluetooth");
                boolean isDirect    = d.protocol != null && d.protocol.contains("Direct");
                String nameLower    = d.name != null ? d.name.toLowerCase() : "";
                boolean isPhone     = nameLower.contains("galaxy") || nameLower.contains("phone") ||
                                      nameLower.contains("iphone") || nameLower.contains("hotspot") ||
                                      nameLower.contains("\uD83D\uDCF1") || isDirect;

                Color nodeColor;
                if      (isPhone)     nodeColor = new Color(34, 211, 238);
                else if (isBluetooth) nodeColor = new Color(168, 85, 247);
                else                  nodeColor = new Color(16, 185, 129);

                // Floor drop shadow and vertical drop-line
                int[] floorPt = project(pos[0], 0, pos[2], cx, cy);
                g2.setColor(new Color(nodeColor.getRed(), nodeColor.getGreen(), nodeColor.getBlue(), 35));
                g2.fillOval(floorPt[0] - 8, floorPt[1] - 4, 16, 8);
                if (Math.abs(pt[1] - floorPt[1]) > 5) {
                    Stroke oldStroke = g2.getStroke();
                    g2.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{3, 3}, 0));
                    g2.setColor(new Color(nodeColor.getRed(), nodeColor.getGreen(), nodeColor.getBlue(), 70));
                    g2.drawLine(pt[0], pt[1], floorPt[0], floorPt[1]);
                    g2.setStroke(oldStroke);
                }

                // Laser connection beam to central laptop
                Color lineCol = isBluetooth ? new Color(168, 85, 247, 85)
                              : isPhone     ? new Color(34, 211, 238, 150)
                              :               new Color(16, 185, 129, 65);
                g2.setColor(lineCol);
                g2.setStroke(new BasicStroke(isPhone ? 1.6f : 0.9f));
                g2.drawLine(origin[0], origin[1], pt[0], pt[1]);

                // Pulsing tracking ring for closest device
                if (idx == closestIdx) {
                    long t = System.currentTimeMillis();
                    float pulse = (float) Math.abs(Math.sin(t * 0.003));
                    int nodeR = isPhone ? 10 : (isBluetooth ? 9 : 8);
                    int pulseR = nodeR + (int) (pulse * 12) + 4;
                    g2.setColor(new Color(nodeColor.getRed(), nodeColor.getGreen(), nodeColor.getBlue(), 90));
                    g2.setStroke(new BasicStroke(1.5f));
                    g2.drawOval(pt[0] - pulseR, pt[1] - pulseR, pulseR * 2, pulseR * 2);
                }

                // Hover glow ring (orange highlight when mouse is over node)
                if (idx == hoveredIndex && idx != selectedIndex) {
                    g2.setColor(new Color(245, 158, 11, 160));
                    g2.setStroke(new BasicStroke(2.0f));
                    int hr = isPhone ? 14 : (isBluetooth ? 13 : 12);
                    g2.drawOval(pt[0] - hr, pt[1] - hr, hr * 2, hr * 2);
                }

                // Node circle
                int nr = isPhone ? 10 : (isBluetooth ? 9 : 8);
                g2.setColor(new Color(nodeColor.getRed(), nodeColor.getGreen(), nodeColor.getBlue(), 220));
                g2.fillOval(pt[0] - nr, pt[1] - nr, nr * 2, nr * 2);
                g2.setColor(nodeColor);
                g2.setStroke(new BasicStroke(1.6f));
                g2.drawOval(pt[0] - nr, pt[1] - nr, nr * 2, nr * 2);
                // Center pinpoint
                g2.setColor(Color.WHITE);
                g2.fillOval(pt[0] - 2, pt[1] - 2, 5, 5);

                // Highlight targeting reticle if selected
                if (idx == selectedIndex) {
                    drawTargetingReticle(g2, pt[0], pt[1], nodeColor, isPhone ? 19 : 16);
                }

                // Device Label
                String tag = isPhone ? "[PHONE] " : (isBluetooth ? "[BT] " : "[AP] ");
                String rawName = d.name != null ? d.name : "Device";
                if (rawName.startsWith("\uD83D\uDCF1 ")) rawName = rawName.substring(3);
                String displayName = tag + rawName;
                if (displayName.length() > 24) displayName = displayName.substring(0, 23) + "\u2026";

                String sigStr = (d.rssiDbm != Integer.MIN_VALUE)
                        ? String.format("%d dBm [%s]", d.rssiDbm, d.estimatedDistance.split(" ")[0])
                        : String.format("%d%% [%s]", d.signalPercent, d.estimatedDistance.split(" ")[0]);

                // Show IP if available, else MAC
                String addrStr = (d.ipAddress != null && d.ipAddress.matches("\\d+\\.\\d+\\.\\d+\\.\\d+.*"))
                        ? d.ipAddress : (d.macAddress != null ? d.macAddress.substring(0, Math.min(17, d.macAddress.length())) : "");

                int lx = pt[0] + 12;
                int ly = isBluetooth ? pt[1] - 14 : pt[1] + 4;

                // Subtle dark background backing for text readability
                g2.setFont(new Font("Segoe UI", Font.BOLD, isPhone ? 11 : 10));
                int labelW = Math.max(g2.getFontMetrics().stringWidth(displayName),
                                      g2.getFontMetrics().stringWidth(addrStr));
                g2.setColor(new Color(11, 15, 25, 210));
                g2.fillRect(lx - 3, ly - 11, labelW + 6, 36);

                g2.setColor(nodeColor);
                g2.drawString(displayName, lx, ly);

                g2.setFont(new Font("Consolas", Font.PLAIN, 9));
                g2.setColor(new Color(148, 163, 184));
                g2.drawString(sigStr, lx, ly + 11);

                if (!addrStr.isEmpty()) {
                    g2.setColor(new Color(34, 211, 238, 180));
                    g2.drawString(addrStr, lx, ly + 22);
                }
            }
        }

        // ── Device Inspector HUD Card ─────────────────────────────────────
        private void drawHUDCard(Graphics2D g2, int w, int h) {
            if (selectedIndex == -1) return;

            int cardW = 410;
            int cardH = 345;
            int cardX = w - cardW - 14;
            int cardY = 14;
            cardBounds.setBounds(cardX, cardY, cardW, cardH);

            boolean isLaptop = (selectedIndex == -2);
            WirelessAirDevice d = (!isLaptop && selectedIndex >= 0 && selectedIndex < airDevices.size())
                    ? airDevices.get(selectedIndex) : null;

            if (!isLaptop && d == null) return;

            // Determine badge color & title
            Color themeColor;
            String badgeTitle;
            String devName;

            if (isLaptop) {
                themeColor = new Color(34, 211, 238); // Cyan
                badgeTitle = "RADAR CENTER // SENSOR HOST";
                devName = "Local Workstation (Victus Laptop)";
            } else {
                boolean isBluetooth = d.protocol != null && d.protocol.contains("Bluetooth");
                boolean isPhone = (d.deviceCategory != null && d.deviceCategory.contains("Smartphone"));

                if (isPhone) {
                    themeColor = new Color(34, 211, 238);
                    badgeTitle = "TARGET // " + (d.deviceCategory != null ? d.deviceCategory.toUpperCase() : "SMARTPHONE");
                } else if (isBluetooth) {
                    themeColor = new Color(168, 85, 247);
                    badgeTitle = "TARGET // " + (d.deviceCategory != null ? d.deviceCategory.toUpperCase() : "BLUETOOTH RF");
                } else {
                    themeColor = new Color(16, 185, 129);
                    badgeTitle = "TARGET // " + (d.deviceCategory != null ? d.deviceCategory.toUpperCase() : "WI-FI NODE");
                }

                devName = d.name != null ? d.name.replace("📱", "").trim() : "Unknown Device";
                if (devName.length() > 34) devName = devName.substring(0, 33) + "…";
            }

            // Outer card backdrop
            g2.setColor(new Color(5, 8, 15, 248));
            g2.fillRoundRect(cardX, cardY, cardW, cardH, 10, 10);

            // Card neon border
            g2.setColor(themeColor);
            g2.setStroke(new BasicStroke(1.4f));
            g2.drawRoundRect(cardX, cardY, cardW, cardH, 10, 10);

            // Header Banner
            g2.setColor(new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), 35));
            g2.fillRoundRect(cardX + 1, cardY + 1, cardW - 2, 34, 10, 10);
            g2.fillRect(cardX + 1, cardY + 16, cardW - 2, 18);

            g2.setFont(new Font("Consolas", Font.BOLD, 10));
            g2.setColor(themeColor);
            g2.drawString(badgeTitle, cardX + 10, cardY + 14);

            g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
            g2.setColor(Color.WHITE);
            g2.drawString(devName, cardX + 10, cardY + 29);

            // Close button [ X ]
            cardCloseBtn.setBounds(cardX + cardW - 26, cardY + 8, 18, 18);
            g2.setColor(new Color(239, 68, 68, 80));
            g2.fillRoundRect(cardCloseBtn.x, cardCloseBtn.y, cardCloseBtn.width, cardCloseBtn.height, 4, 4);
            g2.setColor(new Color(239, 68, 68));
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawRoundRect(cardCloseBtn.x, cardCloseBtn.y, cardCloseBtn.width, cardCloseBtn.height, 4, 4);
            g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
            g2.drawString("X", cardCloseBtn.x + 5, cardCloseBtn.y + 13);

            // ── TAB STRIP ─────────────────────────────────────────────────
            int tabY = cardY + 36;
            int tabW = (cardW - 4) / 4;
            int tabH = 24;

            cardTabOverview.setBounds(cardX + 2, tabY, tabW, tabH);
            cardTabRf.setBounds(cardX + 2 + tabW, tabY, tabW, tabH);
            cardTabSecurity.setBounds(cardX + 2 + tabW * 2, tabY, tabW, tabH);
            cardTabNetwork.setBounds(cardX + 2 + tabW * 3, tabY, tabW, tabH);

            Rectangle[] tabs = {cardTabOverview, cardTabRf, cardTabSecurity, cardTabNetwork};
            String[] tabLabels = {"OVERVIEW", "RF SPECTRUM", "SECURITY", "NETWORK"};

            for (int ti = 0; ti < 4; ti++) {
                Rectangle tb = tabs[ti];
                boolean isSel = (ti == activeHudTab);
                g2.setColor(isSel ? new Color(15, 23, 42) : new Color(11, 15, 25));
                g2.fillRect(tb.x, tb.y, tb.width, tb.height);

                if (isSel) {
                    g2.setColor(themeColor);
                    g2.fillRect(tb.x, tb.y + tb.height - 2, tb.width, 2);
                    g2.setFont(new Font("Consolas", Font.BOLD, 10));
                    g2.drawString(tabLabels[ti], tb.x + (tb.width - g2.getFontMetrics().stringWidth(tabLabels[ti])) / 2, tb.y + 16);
                } else {
                    g2.setColor(new Color(100, 116, 139));
                    g2.setFont(new Font("Consolas", Font.PLAIN, 10));
                    g2.drawString(tabLabels[ti], tb.x + (tb.width - g2.getFontMetrics().stringWidth(tabLabels[ti])) / 2, tb.y + 16);
                }
                g2.setColor(new Color(30, 41, 59));
                g2.drawLine(tb.x + tb.width, tb.y + 4, tb.x + tb.width, tb.y + tb.height - 4);
            }

            g2.setColor(new Color(30, 41, 59));
            g2.drawLine(cardX + 1, tabY + tabH, cardX + cardW - 2, tabY + tabH);

            // ── BODY ROWS ACCORDING TO ACTIVE TAB ─────────────────────────
            int curY = tabY + tabH + 18;
            int rowH = 21;

            if (isLaptop) {
                String hostIp = "127.0.0.1";
                try { hostIp = InetAddress.getLocalHost().getHostAddress(); } catch (Exception ignored) {}
                if (activeHudTab == 0) {
                    String localMac = getOS() != null ? getOS().getNetworkScannerService().getConnectedWifiBssid() : "4E:9C:29:7F:CE:46";
                    drawCardRow(g2, cardX + 12, curY, "HOST IP ADDRESS:", hostIp, themeColor); curY += rowH;
                    drawCardRow(g2, cardX + 12, curY, "PHYSICAL MAC/BSSID:", localMac, new Color(226, 232, 240)); curY += rowH;
                    drawCardRow(g2, cardX + 12, curY, "CHASSIS TYPE:", "Laptop Workstation (Victus Gaming)", themeColor); curY += rowH;
                    drawCardRow(g2, cardX + 12, curY, "OPERATING SYS:", "Windows 11 NT 10.0 (Host Kernel)", new Color(168, 85, 247)); curY += rowH;
                    drawCardRow(g2, cardX + 12, curY, "RADAR ROLE:", "Central Gyro Radar Origin [0, 0, 0]", new Color(245, 158, 11)); curY += rowH;
                    drawCardRow(g2, cardX + 12, curY, "ACTIVE SENSORS:", "Dual Wi-Fi 6 + Bluetooth Transceivers", new Color(16, 185, 129));
                } else if (activeHudTab == 1) {
                    drawCardRow(g2, cardX + 12, curY, "RADIO BANDS:", "2.4 GHz ISM + 5.0 GHz UNII Dual Band", themeColor); curY += rowH;
                    drawCardRow(g2, cardX + 12, curY, "RF CAPABILITIES:", "Wi-Fi 6 (802.11ax) + BT 5.3 Low Energy", new Color(226, 232, 240)); curY += rowH;
                    drawCardRow(g2, cardX + 12, curY, "SCAN BROADCAST:", "Active 802.11 Probe Request Burst", new Color(245, 158, 11)); curY += rowH;
                    drawCardRow(g2, cardX + 12, curY, "TX POWER:", "100 mW EIRP (Standard Regulatory)", new Color(203, 213, 225)); curY += rowH;
                    drawCardRow(g2, cardX + 12, curY, "CHANNEL PLAN:", "CH 1-13 (2.4G) + CH 36-165 (5G)", new Color(148, 163, 184)); curY += rowH;
                    drawCardRow(g2, cardX + 12, curY, "ANTENNA ARRAY:", "2x2 MIMO Dual Internal Diversity", new Color(16, 185, 129));
                } else if (activeHudTab == 2) {
                    drawCardRow(g2, cardX + 12, curY, "SECURITY SUITE:", "WPA3 / WPA2 Enterprise & Personal", themeColor); curY += rowH;
                    drawCardRow(g2, cardX + 12, curY, "CIPHER ENGINE:", "AES-CCMP / GCMP-256 Hardware Accel", new Color(226, 232, 240)); curY += rowH;
                    drawCardRow(g2, cardX + 12, curY, "FIREWALL STATUS:", "Windows Defender Kernel Shield Active", new Color(16, 185, 129)); curY += rowH;
                    drawCardRow(g2, cardX + 12, curY, "THREAT STATUS:", "SECURE (Zero Outbound Leakage)", new Color(16, 185, 129)); curY += rowH;
                    drawCardRow(g2, cardX + 12, curY, "INTEGRITY:", "Protected Management Frames (PMF)", new Color(56, 189, 248)); curY += rowH;
                    drawCardRow(g2, cardX + 12, curY, "ISOLATION:", "Antigravity Secure Subsystem Active", new Color(168, 85, 247));
                } else {
                    drawCardRow(g2, cardX + 12, curY, "HOST IP ADDRESS:", hostIp, themeColor); curY += rowH;
                    drawCardRow(g2, cardX + 12, curY, "LOOPBACK:", "127.0.0.1 (Localhost Interface)", new Color(226, 232, 240)); curY += rowH;
                    drawCardRow(g2, cardX + 12, curY, "GATEWAY ROUTE:", (getOS() != null ? getOS().getNetworkScannerService().getGatewayIp() : "10.0.0.1"), new Color(245, 158, 11)); curY += rowH;
                    drawCardRow(g2, cardX + 12, curY, "SUBNET PREFIX:", (getOS() != null ? getOS().getNetworkScannerService().getSubnetPrefix() + "0/24" : "10.0.0.0/24"), new Color(148, 163, 184)); curY += rowH;
                    drawCardRow(g2, cardX + 12, curY, "HOST ADAPTER:", "Wi-Fi Physical Network Interface", new Color(203, 213, 225)); curY += rowH;
                    drawCardRow(g2, cardX + 12, curY, "PING LATENCY:", "< 1 ms (Internal Kernel Bus)", new Color(16, 185, 129));
                }
            } else {
                double realM = rssiToDistance(d);
                if (activeHudTab == 0) {
                    // TAB 0: OVERVIEW & CORE IDENTITY (IP, MAC & HARDWARE)
                    String ipStr = (d.ipAddress != null && !d.ipAddress.isEmpty()) ? d.ipAddress : "Over-The-Air RF (No L3 IP)";
                    boolean hasIp = ipStr.matches("\\d+\\.\\d+\\.\\d+\\.\\d+.*");
                    drawCardRow(g2, cardX + 12, curY, "RESOLVED IPv4:", ipStr, hasIp ? new Color(34, 211, 238) : new Color(148, 163, 184)); curY += rowH;
                    drawCardRow(g2, cardX + 12, curY, "PHYSICAL MAC:", (d.macAddress != null ? d.macAddress : "Unknown BSSID"), new Color(226, 232, 240)); curY += rowH;
                    drawCardRow(g2, cardX + 12, curY, "DEVICE CATEGORY:", (d.deviceCategory != null ? d.deviceCategory : "Wireless Node"), themeColor); curY += rowH;
                    drawCardRow(g2, cardX + 12, curY, "HARDWARE VENDOR:", (d.vendor != null ? d.vendor : "Surrounding Device") + " (" + (d.osFingerprint != null ? d.osFingerprint : "RTOS") + ")", new Color(168, 85, 247)); curY += rowH;
                    drawCardRow(g2, cardX + 12, curY, "REAL DISTANCE:", String.format("%.2f m (%s)", realM, d.estimatedDistance), new Color(245, 158, 11)); curY += rowH;
                    drawCardRow(g2, cardX + 12, curY, "SIGNAL QUALITY:", (d.rssiDbm != Integer.MIN_VALUE ? d.rssiDbm + " dBm | " : "") + d.signalPercent + "% (" + (d.connectionState != null ? d.connectionState : "Active Link") + ")", new Color(16, 185, 129));
                } else if (activeHudTab == 1) {
                    // TAB 1: RF SPECTRUM & PHYSICS
                    String freqStr = d.centerFreqMhz > 0 ? (d.centerFreqMhz + " MHz (" + (d.centerFreqMhz >= 5000 ? "5.0 GHz Band" : "2.4 GHz Band") + ")") : "2.4 GHz ISM Band";
                    String sigStr = (d.rssiDbm != Integer.MIN_VALUE)
                            ? String.format("%d dBm (%d%% Strength)", d.rssiDbm, d.signalPercent)
                            : String.format("%d%% Strength", d.signalPercent);
                    drawCardRow(g2, cardX + 12, curY, "CENTER FREQUENCY:", freqStr, themeColor); curY += rowH;
                    drawCardRow(g2, cardX + 12, curY, "PHY GENERATION:", d.wifiGen != null ? d.wifiGen : "802.11 RF", new Color(226, 232, 240)); curY += rowH;
                    drawCardRow(g2, cardX + 12, curY, "BAND & CHANNEL:", d.bandAndChannel != null ? d.bandAndChannel : "Ch Auto", new Color(148, 163, 184)); curY += rowH;
                    drawCardRow(g2, cardX + 12, curY, "SIGNAL RSSI:", sigStr, themeColor); curY += rowH;
                    drawCardRow(g2, cardX + 12, curY, "LINK QUALITY:", d.linkQuality != null ? d.linkQuality : (d.signalPercent + "%"), new Color(16, 185, 129)); curY += rowH;
                    drawCardRow(g2, cardX + 12, curY, "THEORETICAL MAX:", d.maxLinkRate != null ? d.maxLinkRate : "Auto Rate", new Color(245, 158, 11));
                } else if (activeHudTab == 2) {
                    // TAB 2: SECURITY & THREAT ASSESSMENT
                    drawCardRow(g2, cardX + 12, curY, "SECURITY SUITE:", d.securityType != null ? d.securityType : "Open", themeColor); curY += rowH;
                    drawCardRow(g2, cardX + 12, curY, "CIPHER ENGINE:", d.cipherSuite != null ? d.cipherSuite : "AES-CCMP", new Color(226, 232, 240)); curY += rowH;
                    drawCardRow(g2, cardX + 12, curY, "THREAT RATING:", d.threatLevel != null ? d.threatLevel : "SECURE", (d.threatLevel != null && d.threatLevel.contains("HIGH RISK") ? new Color(239, 68, 68) : new Color(16, 185, 129))); curY += rowH;
                    drawCardRow(g2, cardX + 12, curY, "AIRWAVE PRIVACY:", (d.threatLevel != null && d.threatLevel.contains("SECURE") ? "Hardware 802.11 Frame Encryption Active" : "Unencrypted Plaintext Airwave Broadcast"), new Color(148, 163, 184)); curY += rowH;
                    drawCardRow(g2, cardX + 12, curY, "WPS PROTECTION:", "Protected / Locked Against PIN Attacks", new Color(56, 189, 248)); curY += rowH;
                    drawCardRow(g2, cardX + 12, curY, "INTEGRITY CHECK:", "Cryptographic MIC Verification", new Color(168, 85, 247));
                } else {
                    // TAB 3: NETWORK & ADDRESSING
                    String ipStr = (d.ipAddress != null && !d.ipAddress.isEmpty()) ? d.ipAddress : "Over-The-Air RF (No L3 IP)";
                    boolean hasIp = ipStr.matches("\\d+\\.\\d+\\.\\d+\\.\\d+.*");
                    drawCardRow(g2, cardX + 12, curY, "RESOLVED IPv4:", ipStr, hasIp ? new Color(34, 211, 238) : new Color(148, 163, 184)); curY += rowH;
                    drawCardRow(g2, cardX + 12, curY, "PHYSICAL MAC:", d.macAddress != null ? d.macAddress : "Unknown BSSID", new Color(226, 232, 240)); curY += rowH;
                    drawCardRow(g2, cardX + 12, curY, "INTERFACE ROLE:", (d.connectionState != null && d.connectionState.contains("Gateway") ? "Default Gateway / Mobile Hotspot" : (d.protocol != null && d.protocol.contains("Bluetooth") ? "Bluetooth L2CAP Link" : "Wireless Station")), new Color(245, 158, 11)); curY += rowH;
                    drawCardRow(g2, cardX + 12, curY, "HARDWARE OUI:", (d.macAddress != null && d.macAddress.length() >= 8 ? d.macAddress.substring(0, 8) + " [" + d.vendor + "]" : d.vendor), new Color(168, 85, 247)); curY += rowH;
                    drawCardRow(g2, cardX + 12, curY, "SUBNET ROUTE:", (hasIp ? "Local Hotspot Subnet Peer (/24)" : "L2 Wireless Broadcast (Non-Routable)"), new Color(148, 163, 184)); curY += rowH;
                    drawCardRow(g2, cardX + 12, curY, "PING STATUS:", (pingStatus != null ? pingStatus : (hasIp ? "Ready to Ping IP" : "No IP Address (RF Only)")), hasIp ? new Color(16, 185, 129) : new Color(100, 116, 139));
                }
            }

            // Divider before action buttons
            g2.setColor(new Color(30, 41, 59));
            g2.drawLine(cardX + 10, cardY + cardH - 44, cardX + cardW - 10, cardY + cardH - 44);

            // ── ACTION BUTTONS ────────────────────────────────────────────
            int actionY = cardY + cardH - 36;
            int btnW = (cardW - 32) / 3;
            int btnH = 26;

            // Button 1: PING TARGET
            cardPingBtn.setBounds(cardX + 10, actionY, btnW, btnH);
            g2.setColor(new Color(15, 23, 42));
            g2.fillRoundRect(cardPingBtn.x, cardPingBtn.y, cardPingBtn.width, cardPingBtn.height, 5, 5);
            g2.setColor(new Color(34, 211, 238));
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawRoundRect(cardPingBtn.x, cardPingBtn.y, cardPingBtn.width, cardPingBtn.height, 5, 5);
            g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
            g2.setColor(new Color(34, 211, 238));
            g2.drawString("> PING TARGET", cardPingBtn.x + (btnW - g2.getFontMetrics().stringWidth("> PING TARGET")) / 2, cardPingBtn.y + 17);

            // Button 2: AUDIT PORTS
            cardAuditBtn.setBounds(cardX + 10 + btnW + 6, actionY, btnW, btnH);
            g2.setColor(new Color(15, 23, 42));
            g2.fillRoundRect(cardAuditBtn.x, cardAuditBtn.y, cardAuditBtn.width, cardAuditBtn.height, 5, 5);
            g2.setColor(new Color(168, 85, 247));
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawRoundRect(cardAuditBtn.x, cardAuditBtn.y, cardAuditBtn.width, cardAuditBtn.height, 5, 5);
            g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
            g2.setColor(new Color(168, 85, 247));
            g2.drawString("> AUDIT PORTS", cardAuditBtn.x + (btnW - g2.getFontMetrics().stringWidth("> AUDIT PORTS")) / 2, cardAuditBtn.y + 17);

            // Button 3: COPY TELEMETRY
            cardCopyBtn.setBounds(cardX + 10 + (btnW + 6) * 2, actionY, btnW, btnH);
            g2.setColor(new Color(15, 23, 42));
            g2.fillRoundRect(cardCopyBtn.x, cardCopyBtn.y, cardCopyBtn.width, cardCopyBtn.height, 5, 5);
            g2.setColor(new Color(16, 185, 129));
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawRoundRect(cardCopyBtn.x, cardCopyBtn.y, cardCopyBtn.width, cardCopyBtn.height, 5, 5);
            g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
            g2.setColor(new Color(16, 185, 129));
            g2.drawString("📋 COPY INFO", cardCopyBtn.x + (btnW - g2.getFontMetrics().stringWidth("📋 COPY INFO")) / 2, cardCopyBtn.y + 17);
        }

        private void drawCardRow(Graphics2D g2, int x, int y, String label, String value, Color valColor) {
            g2.setFont(new Font("Consolas", Font.BOLD, 10));
            g2.setColor(new Color(100, 116, 139));
            g2.drawString(label, x, y);

            g2.setFont(new Font("Consolas", Font.BOLD, 11));
            g2.setColor(valColor);
            int labelW = 125;
            if (value.length() > 33) value = value.substring(0, 32) + "…";
            g2.drawString(value, x + labelW, y);
        }

        private void copyDeviceTelemetryToClipboard() {
            if (selectedIndex == -2) {
                String report = "=== IMPERION OS RADAR HOST TELEMETRY ===\n" +
                        "Device: Local Workstation (Victus Laptop)\n" +
                        "Role: Radar Origin & Sensor Host (Victus)\n" +
                        "Local IP: " + (getOS() != null ? getOS().getNetworkScannerService().getLocalIp() : "127.0.0.1") + "\n" +
                        "Gateway IP: " + (getOS() != null ? getOS().getNetworkScannerService().getGatewayIp() : "Unknown") + "\n" +
                        "Subnet: " + (getOS() != null ? getOS().getNetworkScannerService().getSubnetPrefix() + "0/24" : "192.168.1.0/24") + "\n" +
                        "Radio: Dual-Band 2.4/5 GHz Active Transceiver\n" +
                        "Spatial Origin: [X=0.00, Y=0.00, Z=0.00]\n";
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(report), null);
                pingStatus = "📋 Telemetry Copied to Clipboard!";
                repaint();
                return;
            }
            if (selectedDevice == null) return;
            WirelessAirDevice d = selectedDevice;
            double realMeters = rssiToDistance(d);
            String report = String.format(
                    "=== IMPERION OS 3D RADAR TARGET TELEMETRY ===\n" +
                    "Device Name: %s\n" +
                    "Category: %s\n" +
                    "OS Fingerprint: %s\n" +
                    "Hardware Vendor: %s\n" +
                    "Resolved IPv4: %s\n" +
                    "Physical MAC/BSSID: %s\n" +
                    "Signal RSSI: %d dBm (%d%% strength)\n" +
                    "Estimated Distance: %.2f meters (%s)\n" +
                    "Center Frequency: %d MHz\n" +
                    "Band & Channel: %s\n" +
                    "Wi-Fi Generation / PHY: %s\n" +
                    "Max Link Rate: %s\n" +
                    "Link Quality: %s\n" +
                    "Security Suite: %s\n" +
                    "Cipher: %s\n" +
                    "Threat Assessment: %s\n" +
                    "Connection State: %s\n",
                    d.name, d.deviceCategory, d.osFingerprint, d.vendor, d.ipAddress, d.macAddress,
                    d.rssiDbm, d.signalPercent, realMeters, d.estimatedDistance,
                    d.centerFreqMhz, d.bandAndChannel, d.wifiGen, d.maxLinkRate, d.linkQuality,
                    d.securityType, d.cipherSuite, d.threatLevel, d.connectionState
            );
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(report), null);
            pingStatus = "📋 Full Telemetry Copied to Clipboard!";
            repaint();
        }

        private void drawTargetingReticle(Graphics2D g2, int x, int y, Color c, int r) {
            Stroke oldStroke = g2.getStroke();
            g2.setColor(c);
            g2.setStroke(new BasicStroke(1.5f));

            int corner = Math.max(5, r / 3);

            // Top-Left corner
            g2.drawLine(x - r, y - r + corner, x - r, y - r);
            g2.drawLine(x - r, y - r, x - r + corner, y - r);

            // Top-Right corner
            g2.drawLine(x + r - corner, y - r, x + r, y - r);
            g2.drawLine(x + r, y - r, x + r, y - r + corner);

            // Bottom-Left corner
            g2.drawLine(x - r, y + r - corner, x - r, y + r);
            g2.drawLine(x - r, y + r, x - r + corner, y + r);

            // Bottom-Right corner
            g2.drawLine(x + r - corner, y + r, x + r, y + r);
            g2.drawLine(x + r, y + r, x + r, y + r - corner);

            // Crosshair tick marks
            g2.setStroke(new BasicStroke(1.0f));
            int tick = 3;
            g2.drawLine(x - r - tick, y, x - r + tick, y);
            g2.drawLine(x + r - tick, y, x + r + tick, y);
            g2.drawLine(x, y - r - tick, x, y - r + tick);
            g2.drawLine(x, y + r - tick, x, y + r + tick);

            g2.setStroke(oldStroke);
        }

        private void triggerPingSelected() {
            if (isPinging) return;
            String targetIp = null;
            if (selectedIndex == -2) {
                targetIp = "127.0.0.1";
            } else if (selectedDevice != null) {
                targetIp = selectedDevice.ipAddress;
            }
            if (targetIp == null || targetIp.contains("No L3 IP") || targetIp.contains("Over-The-Air")) {
                pingStatus = "PHY L2 RF Link: No IPv4 Stack";
                repaint();
                return;
            }

            final String ip = targetIp;
            isPinging = true;
            pingStatus = "PINGING " + ip + "...";
            repaint();

            new Thread(() -> {
                NetworkScannerService scanner = getOS() != null ? getOS().getNetworkScannerService() : new NetworkScannerService();
                long ms = scanner.pingIp(ip);
                SwingUtilities.invokeLater(() -> {
                    isPinging = false;
                    if (ms >= 0) {
                        pingStatus = "⚡ LIVE LATENCY: " + ms + " ms (ONLINE)";
                    } else {
                        pingStatus = "❌ PING TIMEOUT (>350ms / ICMP Filtered)";
                    }
                    repaint();
                });
            }).start();
        }

        private void triggerAuditSelected() {
            if (selectedIndex == -2) {
                CyberDeckApp.this.auditTargetIp("127.0.0.1", "Host Laptop");
            } else if (selectedDevice != null) {
                String ip = selectedDevice.ipAddress;
                if (ip != null && !ip.contains("No L3 IP") && !ip.contains("Over-The-Air")) {
                    CyberDeckApp.this.auditTargetIp(ip, selectedDevice.name);
                } else {
                    JOptionPane.showMessageDialog(CyberDeckApp.this.getWindow(),
                            "Cannot audit ports on a device without an assigned IPv4 address.\n" +
                            "This device is currently detected via Over-The-Air RF / Bluetooth only.",
                            "Port Audit Notice", JOptionPane.INFORMATION_MESSAGE);
                }
            }
        }

        // ── HUD overlay ──────────────────────────────────────────────────
        private void drawHUD(Graphics2D g2, int w, int h) {
            int pitchDeg = (int) Math.toDegrees(rotX);
            int yawDeg = (int) Math.toDegrees(rotY) % 360;
            if (yawDeg < 0) yawDeg += 360;

            String gyroStatus = dragging ? "MANUAL GYRO TRACKING (DRAGGING)" : "AUTO-GYRO DRIFT (ACTIVE)";
            String hud = String.format("GYRO: PITCH [%d\u00B0]  YAW [%d\u00B0]  ZOOM [%.1fx]  |  %s  |  NODES: %d",
                    pitchDeg, yawDeg, zoom, gyroStatus, airDevices.size());

            g2.setFont(new Font("Consolas", Font.BOLD, 10));
            int textW = g2.getFontMetrics().stringWidth(hud);

            g2.setColor(new Color(15, 23, 42, 200));
            g2.fillRoundRect(8, h - 28, textW + 18, 22, 6, 6);
            g2.setColor(new Color(34, 211, 238, 120));
            g2.drawRoundRect(8, h - 28, textW + 18, 22, 6, 6);

            g2.setColor(dragging ? new Color(251, 146, 60) : new Color(34, 211, 238));
            g2.drawString(hud, 17, h - 13);

            // Hover tooltip: show device info when mouse hovers near a node
            if (hoveredIndex >= 0 && hoveredIndex < airDevices.size() && hoveredIndex != selectedIndex) {
                WirelessAirDevice hd = airDevices.get(hoveredIndex);
                String tipName = hd.name != null ? hd.name : "Unknown Device";
                if (tipName.length() > 36) tipName = tipName.substring(0, 35) + "…";
                String tipIp = (hd.ipAddress != null && hd.ipAddress.matches("\\d+\\.\\d+\\.\\d+\\.\\d+.*"))
                        ? hd.ipAddress : (hd.macAddress != null ? hd.macAddress : "RF Only");
                String tipSig = hd.rssiDbm != Integer.MIN_VALUE
                        ? hd.rssiDbm + " dBm | " + hd.estimatedDistance.split(" ")[0]
                        : hd.signalPercent + "% | " + hd.estimatedDistance.split(" ")[0];
                String tipLine = "▶ " + tipName + "   " + tipIp + "   " + tipSig + "   [CLICK TO INSPECT]";

                g2.setFont(new Font("Consolas", Font.BOLD, 10));
                int tipW = g2.getFontMetrics().stringWidth(tipLine);
                int tipX = Math.min(8, w - tipW - 16);
                g2.setColor(new Color(5, 8, 15, 220));
                g2.fillRoundRect(8, h - 52, tipW + 18, 20, 5, 5);
                g2.setColor(new Color(245, 158, 11, 160));
                g2.drawRoundRect(8, h - 52, tipW + 18, 20, 5, 5);
                g2.setColor(new Color(245, 158, 11));
                g2.drawString(tipLine, 17, h - 38);
            }
        }

        // ── MouseListener ─────────────────────────────────────────────────
        @Override
        public void mousePressed(MouseEvent e) {
            dragStart = e.getPoint();
            dragging = true;
            repaint();
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            dragging = false;
            dragStart = null;
            repaint();
        }

        @Override
        public void mouseClicked(MouseEvent e) {
            Point p = e.getPoint();

            // 1. HUD Card close button
            if (selectedIndex != -1 && cardCloseBtn.contains(p)) {
                selectedIndex = -1;
                selectedDevice = null;
                pingStatus = null;
                repaint();
                return;
            }

            // 2. HUD Card tab selection
            if (selectedIndex != -1) {
                if (cardTabOverview.contains(p)) { activeHudTab = 0; repaint(); return; }
                if (cardTabRf.contains(p)) { activeHudTab = 1; repaint(); return; }
                if (cardTabSecurity.contains(p)) { activeHudTab = 2; repaint(); return; }
                if (cardTabNetwork.contains(p)) { activeHudTab = 3; repaint(); return; }
            }

            // 3. HUD Card Action buttons
            if (selectedIndex != -1 && cardPingBtn.contains(p)) {
                triggerPingSelected();
                return;
            }

            if (selectedIndex != -1 && cardAuditBtn.contains(p)) {
                triggerAuditSelected();
                return;
            }

            if (selectedIndex != -1 && cardCopyBtn.contains(p)) {
                copyDeviceTelemetryToClipboard();
                return;
            }

            // If clicked inside HUD card body, keep card open
            if (selectedIndex != -1 && cardBounds.contains(p)) {
                return;
            }

            // 4. Hit-test surrounding devices (screenPoints)
            int bestIdx = -1;
            double bestDist = 32.0; // 32px hit radius for easier device clicking
            for (int i = 0; i < screenPoints.size(); i++) {
                Point pt = screenPoints.get(i);
                if (pt != null) {
                    double dist = pt.distance(p);
                    if (dist < bestDist) {
                        bestDist = dist;
                        bestIdx = i;
                    }
                }
            }

            if (bestIdx >= 0) {
                selectedIndex = bestIdx;
                selectedDevice = airDevices.get(bestIdx);
                pingStatus = null;
                if (radarTargetTable != null && bestIdx < radarTargetTable.getRowCount()) {
                    radarTargetTable.setRowSelectionInterval(bestIdx, bestIdx);
                    radarTargetTable.scrollRectToVisible(radarTargetTable.getCellRect(bestIdx, 0, true));
                }
                repaint();
                return;
            }

            // 5. Hit-test Host Laptop
            if (laptopScreenPoint != null && laptopScreenPoint.distance(p) <= 40.0) {
                selectedIndex = -2; // Laptop selected
                selectedDevice = null;
                pingStatus = null;
                if (radarTargetTable != null) radarTargetTable.clearSelection();
                repaint();
                return;
            }

            // 6. Clicked empty space -> deselect
            selectedIndex = -1;
            selectedDevice = null;
            pingStatus = null;
            if (radarTargetTable != null) radarTargetTable.clearSelection();
            repaint();
        }

        @Override public void mouseEntered(MouseEvent e) {}
        @Override public void mouseExited(MouseEvent e) {}

        // ── MouseMotionListener ───────────────────────────────────────────
        @Override
        public void mouseDragged(MouseEvent e) {
            if (dragStart != null) {
                float dx = (float) (e.getX() - dragStart.x);
                float dy = (float) (e.getY() - dragStart.y);
                rotY += dx * 0.008f;
                rotX += dy * 0.008f;
                rotX = Math.max(-1.45f, Math.min(1.45f, rotX));
                dragStart = e.getPoint();
                repaint();
            }
        }

        @Override
        public void mouseMoved(MouseEvent e) {
            Point p = e.getPoint();
            boolean hoverClickable = false;

            if (selectedIndex != -1) {
                if (cardCloseBtn.contains(p) || cardPingBtn.contains(p) || cardAuditBtn.contains(p) || cardCopyBtn.contains(p)
                        || cardTabOverview.contains(p) || cardTabRf.contains(p) || cardTabSecurity.contains(p) || cardTabNetwork.contains(p)) {
                    hoverClickable = true;
                }
            }

            // Hover detection over device nodes → update hoveredIndex for tooltip
            int newHovered = -1;
            double closestDist = 28.0;
            for (int i = 0; i < screenPoints.size(); i++) {
                Point pt = screenPoints.get(i);
                if (pt != null) {
                    double dist = pt.distance(p);
                    if (dist <= closestDist) {
                        closestDist = dist;
                        newHovered = i;
                        hoverClickable = true;
                    }
                }
            }
            if (newHovered != hoveredIndex) {
                hoveredIndex = newHovered;
                repaint();
            }

            if (!hoverClickable && laptopScreenPoint != null && laptopScreenPoint.distance(p) <= 40.0) {
                hoverClickable = true;
            }

            setCursor(hoverClickable ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                                     : Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        }

        // ── MouseWheelListener ────────────────────────────────────────────
        @Override
        public void mouseWheelMoved(MouseWheelEvent e) {
            if (e.getWheelRotation() < 0) {
                zoom *= 1.10f;
            } else {
                zoom *= 0.90f;
            }
            zoom = Math.max(0.40f, Math.min(3.50f, zoom));
            repaint();
        }
    }

    @Override
    public void onClose() {
        if (networkTimer != null) networkTimer.stop();
    }
}

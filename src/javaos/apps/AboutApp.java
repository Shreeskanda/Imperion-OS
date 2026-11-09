package javaos.apps;

import javaos.services.SystemMetricsService;
import javaos.ui.Theme;
import javaos.ui.ThemeManager;
import javaos.ui.components.CyberpunkIcons;
import javaos.ui.components.LogoManager;
import javaos.ui.components.NeonButton;
import javaos.ui.components.NeonCard;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * About / System Information panel for JavaOS.
 */
public class AboutApp extends OSApplication {

    public AboutApp() {
        super(new AppDescriptor(
                "about",
                "About IMPERION OS",
                "System information and platform credits",
                AppDescriptor.Category.SYSTEM,
                CyberpunkIcons.createIcon("START", 20),
                true
        ));
    }

    @Override
    public Dimension getPreferredWindowSize() {
        return new Dimension(560, 440);
    }

    @Override
    public JComponent createContent() {
        JPanel main = new JPanel(new BorderLayout(0, 12));
        main.setOpaque(false);
        main.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        Theme theme = ThemeManager.getInstance().getCurrentTheme();

        // Logo area
        JPanel logoPanel = new JPanel(new BorderLayout(16, 0));
        logoPanel.setOpaque(false);

        ImageIcon wolfLogo = LogoManager.getLogoIcon(80, 68);
        JLabel logoIcon = new JLabel(wolfLogo != null ? wolfLogo : CyberpunkIcons.createIcon("START", 64));
        logoPanel.add(logoIcon, BorderLayout.WEST);

        JPanel titlePanel = new JPanel(new GridLayout(4, 1, 0, 4));
        titlePanel.setOpaque(false);

        JLabel title = new JLabel("IMPERION OS — Cyberpunk Workstation");
        title.setFont(new Font("Segoe UI", Font.BOLD, 20));
        title.setForeground(theme.neonAccent);

        JLabel version = new JLabel("Version 2026.10 — Build 1.0.0 [STABLE]");
        version.setFont(theme.fontRegular);
        version.setForeground(theme.textPrimary);

        JLabel java = new JLabel("Java Platform: " + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")");
        java.setFont(theme.fontSmall);
        java.setForeground(theme.textSecondary);

        JLabel build = new JLabel("Built: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm")));
        build.setFont(theme.fontSmall);
        build.setForeground(theme.textSecondary);

        titlePanel.add(title);
        titlePanel.add(version);
        titlePanel.add(java);
        titlePanel.add(build);
        logoPanel.add(titlePanel, BorderLayout.CENTER);
        main.add(logoPanel, BorderLayout.NORTH);

        // Info cards grid
        JPanel infoGrid = new JPanel(new GridLayout(2, 2, 10, 10));
        infoGrid.setOpaque(false);

        SystemMetricsService sm = getOS().getSystemMetricsService();

        infoGrid.add(makeInfoCard("Host System", theme.neonAccent,
                new String[]{sm.getHostOsName(), "CPU Cores: " + sm.getAvailableProcessors(), "Uptime: " + sm.getFormattedUptime()}));

        long hostRam = sm.getHostTotalPhysicalMemory();
        infoGrid.add(makeInfoCard("Memory", theme.secondaryAccent,
                new String[]{
                        "JVM Heap: " + SystemMetricsService.formatBytes(sm.getJvmUsedMemory()) + " / " + SystemMetricsService.formatBytes(sm.getJvmMaxMemory()),
                        hostRam > 0 ? "Host RAM: " + SystemMetricsService.formatBytes(hostRam) : "Host RAM: API Unavailable",
                        "JVM Threads: " + sm.getActiveThreadCount()
                }));

        infoGrid.add(makeInfoCard("Applications", theme.neonAccent,
                new String[]{
                        "Registered Apps: " + getOS().getApplicationManager().getRegisteredApps().size(),
                        "Running Apps: " + getOS().getApplicationManager().getRunningApps().size(),
                        "Theme: " + ThemeManager.getInstance().getCurrentTheme().getName()
                }));

        String victusStatus = getOS().getVictusService().isRunning() ? "RUNNING (PID " + getOS().getVictusService().getActivePid() + ")" : "Idle (Not Running)";
        infoGrid.add(makeInfoCard("Victus AI", theme.secondaryAccent,
                new String[]{
                        "Status: " + victusStatus,
                        "Script: D:\\skanda\\OfflineVoiceAssistant",
                        "Integration: ProcessBuilder (Isolated)"
                }));

        main.add(infoGrid, BorderLayout.CENTER);

        // Footer
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 4));
        footer.setOpaque(false);

        NeonButton closeBtn = new NeonButton("Close", null);
        closeBtn.addActionListener(e -> { if (getWindow() != null) getWindow().close(); });
        footer.add(closeBtn);

        JLabel credit = new JLabel("Built entirely in Java Swing + NIO for Shreeskanda");
        credit.setFont(theme.fontSmall);
        credit.setForeground(theme.textSecondary);
        footer.add(credit);

        main.add(footer, BorderLayout.SOUTH);

        return main;
    }

    private NeonCard makeInfoCard(String title, Color accentColor, String[] lines) {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        NeonCard card = new NeonCard(new GridLayout(lines.length + 1, 1, 0, 4));
        card.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(accentColor),
                " " + title + " ",
                0, 0, theme.fontBold, accentColor));

        for (String line : lines) {
            JLabel lbl = new JLabel(line);
            lbl.setFont(theme.fontSmall);
            lbl.setForeground(theme.textPrimary);
            card.add(lbl);
        }
        return card;
    }
}

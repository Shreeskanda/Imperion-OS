package javaos.apps;

import javaos.services.VictusProcessService;
import javaos.ui.Theme;
import javaos.ui.ThemeManager;
import javaos.ui.components.CyberpunkIcons;
import javaos.ui.components.NeonButton;
import javaos.ui.components.NeonCard;
import javaos.ui.components.NeonScrollBarUI;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

/**
 * Dedicated graphical management console for the Victus Python Assistant.
 * Provides process lifecycle controls, telemetry, and live stdout/stderr console streaming.
 */
public class VictusLauncherApp extends OSApplication {

    private JLabel statusBadge;
    private JLabel pidLabel;
    private NeonButton btnLaunch;
    private NeonButton btnStop;
    private JTextArea logArea;
    private Consumer<String> logListener;

    public VictusLauncherApp() {
        super(new AppDescriptor(
                "victus",
                "Victus AI Assistant",
                "Bridge launcher for the external Python Ollama assistant",
                AppDescriptor.Category.AI,
                CyberpunkIcons.createIcon("VICTUS", 20),
                true
        ));
    }

    @Override
    public Dimension getPreferredWindowSize() {
        return new Dimension(740, 520);
    }

    @Override
    public JComponent createContent() {
        JPanel main = new JPanel(new BorderLayout(0, 8));
        main.setOpaque(false);
        main.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        VictusProcessService victus = getOS().getVictusService();

        // Top Status & Control Card
        NeonCard topCard = new NeonCard(new BorderLayout(12, 0));
        topCard.setPreferredSize(new Dimension(0, 90));

        JPanel leftStatus = new JPanel(new GridLayout(2, 1, 0, 4));
        leftStatus.setOpaque(false);

        statusBadge = new JLabel("Status: STOPPED");
        statusBadge.setFont(new Font("Segoe UI", Font.BOLD, 16));
        statusBadge.setForeground(theme.textSecondary);

        pidLabel = new JLabel("PID: -- | Python Subprocess Bridge");
        pidLabel.setFont(theme.fontSmall);
        pidLabel.setForeground(theme.textSecondary);

        leftStatus.add(statusBadge);
        leftStatus.add(pidLabel);
        topCard.add(leftStatus, BorderLayout.CENTER);

        // Control buttons
        JPanel rightControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 12));
        rightControls.setOpaque(false);

        btnLaunch = new NeonButton("Launch Assistant", CyberpunkIcons.createIcon("PLAY", 16), NeonButton.Style.SUCCESS);
        btnLaunch.addActionListener(e -> launchVictus());

        btnStop = new NeonButton("Stop Assistant", CyberpunkIcons.createIcon("STOP", 16), NeonButton.Style.DANGER);
        btnStop.addActionListener(e -> stopVictus());

        NeonButton btnSettings = new NeonButton("Configure", CyberpunkIcons.createIcon("SETTINGS", 16));
        btnSettings.addActionListener(e -> getOS().getApplicationManager().launchApp("settings"));

        rightControls.add(btnLaunch);
        rightControls.add(btnStop);
        rightControls.add(btnSettings);
        topCard.add(rightControls, BorderLayout.EAST);

        main.add(topCard, BorderLayout.NORTH);

        // Center: Live Process Output Console
        JPanel consolePanel = new JPanel(new BorderLayout(0, 4));
        consolePanel.setOpaque(false);

        JPanel cHeader = new JPanel(new BorderLayout());
        cHeader.setOpaque(false);
        JLabel cTitle = new JLabel("Live Output Console (Stdout / Stderr & Logs)");
        cTitle.setFont(theme.fontBold);
        cTitle.setForeground(theme.neonAccent);
        cHeader.add(cTitle, BorderLayout.WEST);

        NeonButton btnClearLog = new NeonButton("Clear Console", null);
        btnClearLog.addActionListener(e -> logArea.setText(""));
        cHeader.add(btnClearLog, BorderLayout.EAST);
        consolePanel.add(cHeader, BorderLayout.NORTH);

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setBackground(theme.background);
        logArea.setForeground(theme.textPrimary);
        logArea.setFont(theme.fontMonospace);
        logArea.setCaretColor(Color.WHITE);
        logArea.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.getVerticalScrollBar().setUI(new NeonScrollBarUI());
        logScroll.setBorder(BorderFactory.createLineBorder(theme.secondaryAccent));
        consolePanel.add(logScroll, BorderLayout.CENTER);

        main.add(consolePanel, BorderLayout.CENTER);

        // Stream live output to console
        logListener = line -> SwingUtilities.invokeLater(() -> {
            logArea.append(line + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
        victus.addOutputListener(logListener);

        // Update UI when process starts or terminates
        victus.addStateListener(this::updateStatusUI);
        updateStatusUI();

        return main;
    }

    @Override
    public void onOpen() {
        updateStatusUI();
    }

    private void launchVictus() {
        btnLaunch.setEnabled(false);
        statusBadge.setText("Status: LAUNCHING SUBPROCESS...");
        statusBadge.setForeground(ThemeManager.getInstance().getCurrentTheme().warning);

        // Launch in background to prevent EDT freeze
        new Thread(() -> {
            VictusProcessService.LaunchResult res = getOS().getVictusService().launch();
            SwingUtilities.invokeLater(() -> {
                btnLaunch.setEnabled(true);
                if (!res.success) {
                    JOptionPane.showMessageDialog(logArea,
                            res.message,
                            "Victus Launch Error",
                            JOptionPane.ERROR_MESSAGE);
                } else {
                    getOS().getNotificationManager().postNotification(
                            "Victus Started", "Process running with PID: " + res.pid, javaos.models.NotificationMessage.Level.SUCCESS);
                }
                updateStatusUI();
            });
        }, "Victus-AsyncLauncher").start();
    }

    private void stopVictus() {
        if (!getOS().getVictusService().isRunning()) return;

        int opt = JOptionPane.showConfirmDialog(logArea,
                "Are you sure you want to terminate the Victus assistant process?",
                "Confirm Termination",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (opt == JOptionPane.YES_OPTION) {
            getOS().getVictusService().terminate(false);
            getOS().getNotificationManager().postNotification(
                    "Victus Stopped", "Victus process terminated by user.", javaos.models.NotificationMessage.Level.INFO);
            updateStatusUI();
        }
    }

    private void updateStatusUI() {
        SwingUtilities.invokeLater(() -> {
            if (getOS() == null) return;
            Theme theme = ThemeManager.getInstance().getCurrentTheme();
            boolean running = getOS().getVictusService().isRunning();

            if (running) {
                Long pid = getOS().getVictusService().getActivePid();
                statusBadge.setText("Status: RUNNING (PID " + pid + ")");
                statusBadge.setForeground(theme.success);
                pidLabel.setText("Victus is active. Interface, Ollama LLM, and Voice are running.");
                btnLaunch.setEnabled(false);
                btnStop.setEnabled(true);
            } else {
                statusBadge.setText("Status: STOPPED / IDLE");
                statusBadge.setForeground(theme.textSecondary);
                pidLabel.setText("Subprocess inactive. Click 'Launch Assistant' to run.");
                btnLaunch.setEnabled(true);
                btnStop.setEnabled(false);
            }
        });
    }

    @Override
    public void onClose() {
        if (logListener != null && getOS() != null) {
            getOS().getVictusService().removeOutputListener(logListener);
        }
    }
}

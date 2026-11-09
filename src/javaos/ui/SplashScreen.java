package javaos.ui;

import javaos.ui.components.CyberpunkIcons;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * Animated boot splash screen shown at JavaOS startup.
 */
public class SplashScreen extends JWindow {

    private JProgressBar progressBar;
    private JLabel statusLabel;
    private Timer progressTimer;
    private int progress = 0;
    private Runnable onComplete;

    public SplashScreen(Runnable onComplete) {
        this.onComplete = onComplete;
        initUI();
    }

    private void initUI() {
        setSize(520, 280);
        setLocationRelativeTo(null);

        // Make it shaped (rounded)
        setBackground(new Color(0, 0, 0, 0));

        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Background
                g2.setColor(new Color(0x08, 0x0B, 0x14));
                g2.fill(new RoundRectangle2D.Float(2, 2, getWidth() - 4, getHeight() - 4, 16, 16));

                // Neon border
                g2.setColor(new Color(0x22, 0xD3, 0xEE));
                g2.setStroke(new BasicStroke(2.0f));
                g2.draw(new RoundRectangle2D.Float(2, 2, getWidth() - 4, getHeight() - 4, 16, 16));

                // Glow
                g2.setColor(new Color(0x22, 0xD3, 0xEE, 30));
                g2.fill(new RoundRectangle2D.Float(4, 4, getWidth() - 8, getHeight() - 8, 12, 12));

                // Grid background
                g2.setColor(new Color(0x22, 0xD3, 0xEE, 18));
                for (int x = 0; x < getWidth(); x += 30) g2.drawLine(x, 0, x, getHeight());
                for (int y = 0; y < getHeight(); y += 30) g2.drawLine(0, y, getWidth(), y);

                g2.dispose();
            }
        };
        panel.setOpaque(false);
        panel.setLayout(new BorderLayout(0, 0));
        panel.setBorder(BorderFactory.createEmptyBorder(32, 40, 28, 40));

        Color neonCyan = new Color(0x22, 0xD3, 0xEE);
        Color textPrimary = new Color(0xF1, 0xF5, 0xF9);
        Color textSec = new Color(0x94, 0xA3, 0xB8);

        // Center body
        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        // Logo
        JLabel logo = new JLabel(CyberpunkIcons.createIcon("START", 52));
        logo.setAlignmentX(Component.CENTER_ALIGNMENT);
        body.add(logo);
        body.add(Box.createVerticalStrut(14));

        // Title
        JLabel title = new JLabel("IMPERION OS");
        title.setFont(new Font("Segoe UI", Font.BOLD, 28));
        title.setForeground(neonCyan);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        body.add(title);

        JLabel subtitle = new JLabel("Cyberpunk Workstation Environment v2026.10");
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subtitle.setForeground(textSec);
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        body.add(subtitle);

        body.add(Box.createVerticalStrut(22));

        // Progress bar
        progressBar = new JProgressBar(0, 100);
        progressBar.setForeground(neonCyan);
        progressBar.setBackground(new Color(0x10, 0x18, 0x27));
        progressBar.setStringPainted(false);
        progressBar.setBorder(BorderFactory.createLineBorder(new Color(0x22, 0xD3, 0xEE, 80)));
        progressBar.setPreferredSize(new Dimension(400, 6));
        progressBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 6));
        progressBar.setAlignmentX(Component.CENTER_ALIGNMENT);
        body.add(progressBar);

        body.add(Box.createVerticalStrut(10));

        // Status text
        statusLabel = new JLabel("Initializing kernel subsystems...");
        statusLabel.setFont(new Font("Consolas", Font.PLAIN, 11));
        statusLabel.setForeground(textSec);
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        body.add(statusLabel);

        panel.add(body, BorderLayout.CENTER);

        // Version bottom
        JLabel versionLabel = new JLabel("Java " + System.getProperty("java.version") + " • " + System.getProperty("os.name"));
        versionLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        versionLabel.setForeground(new Color(0x64, 0x74, 0x8B));
        versionLabel.setHorizontalAlignment(SwingConstants.CENTER);
        panel.add(versionLabel, BorderLayout.SOUTH);

        setContentPane(panel);
    }

    public void start() {
        setVisible(true);

        String[] statusMessages = {
                "Initializing kernel subsystems...",
                "Loading neon theme engine...",
                "Mounting virtual filesystem...",
                "Registering built-in applications...",
                "Scanning Victus AI integration...",
                "Configuring window manager...",
                "Starting notification services...",
                "Loading desktop environment...",
                "Calibrating cyberpunk aesthetic...",
                "Boot sequence complete."
        };

        progressTimer = new Timer(130, null);
        progressTimer.addActionListener(e -> {
            progress += 10;
            if (progress > 100) progress = 100;
            progressBar.setValue(progress);

            int msgIndex = (progress / 10) - 1;
            if (msgIndex >= 0 && msgIndex < statusMessages.length) {
                statusLabel.setText(statusMessages[msgIndex]);
            }

            if (progress >= 100) {
                progressTimer.stop();
                Timer closeTimer = new Timer(400, ev -> {
                    dispose();
                    if (onComplete != null) {
                        onComplete.run();
                    }
                });
                closeTimer.setRepeats(false);
                closeTimer.start();
            }
        });

        progressTimer.start();
    }
}

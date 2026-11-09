package javaos.ui.components;

import javaos.models.NotificationMessage;
import javaos.ui.Theme;
import javaos.ui.ThemeManager;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * Modern cyberpunk toast notification card rendered in the desktop overlay.
 */
public class ToastNotification extends JPanel {

    private final NotificationMessage message;
    private final Runnable onDismiss;
    private Timer autoDismissTimer;

    public ToastNotification(NotificationMessage message, Runnable onDismiss) {
        this.message = message;
        this.onDismiss = onDismiss;
        initUI();
    }

    private void initUI() {
        setOpaque(false);
        setLayout(new BorderLayout(10, 0));
        setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 10));
        setPreferredSize(new Dimension(340, 72));

        Theme theme = ThemeManager.getInstance().getCurrentTheme();

        // Icon indicator
        JLabel iconLabel = new JLabel(getIconForLevel(message.getLevel()));
        add(iconLabel, BorderLayout.WEST);

        // Text area
        JPanel textPanel = new JPanel();
        textPanel.setOpaque(false);
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));

        JLabel titleLabel = new JLabel(message.getTitle());
        titleLabel.setFont(theme.fontBold);
        titleLabel.setForeground(theme.textPrimary);

        JLabel msgLabel = new JLabel("<html><body style='width: 220px;'>" + message.getMessage() + "</body></html>");
        msgLabel.setFont(theme.fontSmall);
        msgLabel.setForeground(theme.textSecondary);

        textPanel.add(titleLabel);
        textPanel.add(Box.createVerticalStrut(3));
        textPanel.add(msgLabel);
        add(textPanel, BorderLayout.CENTER);

        // Close button
        JButton closeBtn = new JButton(CyberpunkIcons.createIcon("CLOSE", 14));
        closeBtn.setOpaque(false);
        closeBtn.setContentAreaFilled(false);
        closeBtn.setBorderPainted(false);
        closeBtn.setFocusPainted(false);
        closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeBtn.addActionListener(e -> dismiss());
        add(closeBtn, BorderLayout.EAST);

        // Auto dismiss timer (5.5 seconds)
        autoDismissTimer = new Timer(5500, e -> dismiss());
        autoDismissTimer.setRepeats(false);
        autoDismissTimer.start();
    }

    private void dismiss() {
        if (autoDismissTimer != null && autoDismissTimer.isRunning()) {
            autoDismissTimer.stop();
        }
        if (onDismiss != null) {
            onDismiss.run();
        }
    }

    private Icon getIconForLevel(NotificationMessage.Level level) {
        switch (level) {
            case SUCCESS:
                return CyberpunkIcons.createIcon("PLAY", 20);
            case WARNING:
                return CyberpunkIcons.createIcon("LOCK", 20);
            case ERROR:
                return CyberpunkIcons.createIcon("STOP", 20);
            case INFO:
            default:
                return CyberpunkIcons.createIcon("START", 20);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        int w = getWidth();
        int h = getHeight();

        // Background
        g2.setColor(theme.elevatedPanel);
        g2.fill(new RoundRectangle2D.Float(1, 1, w - 2, h - 2, 10, 10));

        // Border according to level
        Color borderColor = theme.neonAccent;
        switch (message.getLevel()) {
            case SUCCESS:
                borderColor = theme.success;
                break;
            case WARNING:
                borderColor = theme.warning;
                break;
            case ERROR:
                borderColor = theme.error;
                break;
            case INFO:
            default:
                borderColor = theme.neonAccent;
                break;
        }

        g2.setColor(borderColor);
        g2.setStroke(new BasicStroke(1.2f));
        g2.draw(new RoundRectangle2D.Float(1.5f, 1.5f, w - 3, h - 3, 10, 10));

        // Left accent stripe
        g2.fill(new RoundRectangle2D.Float(1.5f, 1.5f, 4.0f, h - 3, 4, 4));

        g2.dispose();
        super.paintComponent(g);
    }
}

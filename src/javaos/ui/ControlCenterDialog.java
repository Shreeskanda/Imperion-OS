package javaos.ui;

import javaos.core.JavaOS;
import javaos.ui.components.CyberpunkIcons;
import javaos.ui.components.NeonButton;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.geom.RoundRectangle2D;

/**
 * Slide-out Cyberpunk Control Center & Action Matrix for IMPERION OS.
 * Features:
 *  - Master audio fader with live volume readout and mute toggle
 *  - Live instant theme matrix switcher (Cyan, Matrix, Synthwave, Solar, Crimson, OLED)
 *  - Performance mode profiles (Overclock, Balanced, Stealth)
 *  - Cyberspace environment switches (Matrix Rain, Shockwaves, Scanlines)
 *  - Quick lock session & mission control shortcuts
 */
public class ControlCenterDialog extends JDialog {

    private final JavaOS os;
    private JSlider volumeSlider;
    private JLabel volumeLabel;

    public ControlCenterDialog(JFrame parent, JavaOS os) {
        super(parent, false);
        this.os = os;

        setUndecorated(true);
        setBackground(new Color(0, 0, 0, 0));
        setSize(340, 520);

        Theme theme = ThemeManager.getInstance().getCurrentTheme();

        JPanel root = new JPanel(new BorderLayout(0, 10)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Theme t = ThemeManager.getInstance().getCurrentTheme();

                // Drop shadow
                g2.setColor(new Color(0, 0, 0, 180));
                g2.fill(new RoundRectangle2D.Float(4, 4, getWidth() - 8, getHeight() - 8, 16, 16));

                // Glassmorphic panel background
                g2.setColor(t.elevatedPanel);
                g2.fill(new RoundRectangle2D.Float(1, 1, getWidth() - 8, getHeight() - 8, 16, 16));

                // Glowing neon border
                g2.setColor(t.neonAccent);
                g2.setStroke(new BasicStroke(1.4f));
                g2.draw(new RoundRectangle2D.Float(1.5f, 1.5f, getWidth() - 9, getHeight() - 9, 16, 16));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        root.setOpaque(false);
        root.setBorder(new EmptyBorder(14, 16, 14, 16));
        setContentPane(root);

        // Header Bar
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);

        JLabel headerTitle = new JLabel("[*] CONTROL MATRIX", CyberpunkIcons.createIcon("SETTINGS", 18), SwingConstants.LEFT);
        headerTitle.setFont(theme.fontBold);
        headerTitle.setForeground(theme.neonAccent);
        headerPanel.add(headerTitle, BorderLayout.WEST);

        JButton btnClose = new JButton("X");
        btnClose.setFont(theme.fontBold);
        btnClose.setForeground(theme.textSecondary);
        btnClose.setOpaque(false);
        btnClose.setContentAreaFilled(false);
        btnClose.setBorderPainted(false);
        btnClose.setFocusPainted(false);
        btnClose.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnClose.addActionListener(e -> setVisible(false));
        headerPanel.add(btnClose, BorderLayout.EAST);
        root.add(headerPanel, BorderLayout.NORTH);

        // Center Content Box
        JPanel contentBox = new JPanel();
        contentBox.setLayout(new BoxLayout(contentBox, BoxLayout.Y_AXIS));
        contentBox.setOpaque(false);

        // -- 1. Master Audio Fader --
        contentBox.add(createSectionHeader("AUDIO TELEMETRY"));
        JPanel audioPanel = new JPanel(new BorderLayout(8, 0));
        audioPanel.setOpaque(false);

        int currentVol = os.getSoundService() != null ? os.getSoundService().getVolume() : 80;
        volumeLabel = new JLabel(currentVol + "%");
        volumeLabel.setFont(theme.fontMonospace);
        volumeLabel.setForeground(theme.neonAccent);
        volumeLabel.setPreferredSize(new Dimension(42, 24));

        volumeSlider = new JSlider(0, 100, currentVol);
        volumeSlider.setOpaque(false);
        volumeSlider.addChangeListener(e -> {
            int val = volumeSlider.getValue();
            volumeLabel.setText(val + "%");
            if (os.getSoundService() != null) {
                os.getSoundService().setVolume(val);
            }
        });

        JButton btnMute = new JButton(CyberpunkIcons.createIcon("MUSIC", 16));
        btnMute.setToolTipText("Toggle Sound");
        btnMute.setOpaque(false);
        btnMute.setContentAreaFilled(false);
        btnMute.setBorderPainted(false);
        btnMute.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnMute.addActionListener(e -> {
            if (os.getSoundService() != null) {
                boolean cur = os.getSoundService().isSoundEnabled();
                os.getSoundService().setSoundEnabled(!cur);
                btnMute.setText(cur ? "MUTED" : "");
                if (!cur) os.getSoundService().playClick();
            }
        });

        audioPanel.add(btnMute, BorderLayout.WEST);
        audioPanel.add(volumeSlider, BorderLayout.CENTER);
        audioPanel.add(volumeLabel, BorderLayout.EAST);
        contentBox.add(audioPanel);
        contentBox.add(Box.createVerticalStrut(12));

        // -- 2. Performance Profiles --
        contentBox.add(createSectionHeader("PERFORMANCE PROFILES"));
        JPanel perfPanel = new JPanel(new GridLayout(1, 3, 6, 0));
        perfPanel.setOpaque(false);

        JButton btnOverclock = createPillButton("[+] Overclock");
        btnOverclock.addActionListener(e -> {
            if (os.getSettingsService() != null) os.getSettingsService().set("graphics.animations", "high");
            if (os.getDesktopFrame() != null && os.getDesktopFrame().getDesktopSurface() != null) {
                os.getDesktopFrame().getDesktopSurface().setAnimationPerformance("high");
            }
            if (os.getSoundService() != null) os.getSoundService().playSuccess();
        });

        JButton btnBalanced = createPillButton("[=] Balanced");
        btnBalanced.addActionListener(e -> {
            if (os.getSettingsService() != null) os.getSettingsService().set("graphics.animations", "normal");
            if (os.getDesktopFrame() != null && os.getDesktopFrame().getDesktopSurface() != null) {
                os.getDesktopFrame().getDesktopSurface().setAnimationPerformance("normal");
            }
            if (os.getSoundService() != null) os.getSoundService().playClick();
        });

        JButton btnStealth = createPillButton("[-] Stealth");
        btnStealth.addActionListener(e -> {
            if (os.getSettingsService() != null) os.getSettingsService().set("graphics.animations", "saver");
            if (os.getDesktopFrame() != null && os.getDesktopFrame().getDesktopSurface() != null) {
                os.getDesktopFrame().getDesktopSurface().setAnimationPerformance("saver");
            }
            if (os.getSoundService() != null) os.getSoundService().playClick();
        });

        perfPanel.add(btnOverclock);
        perfPanel.add(btnBalanced);
        perfPanel.add(btnStealth);
        contentBox.add(perfPanel);
        contentBox.add(Box.createVerticalStrut(12));

        // -- 3. Cyber Theme Matrix --
        contentBox.add(createSectionHeader("CYBER THEME MATRIX"));
        JPanel themeGrid = new JPanel(new GridLayout(3, 2, 6, 6));
        themeGrid.setOpaque(false);

        String[] themes = {
            "Cyberpunk Cyan",
            "Matrix Green",
            "Synthwave Violet",
            "Solar Amber",
            "Crimson Protocol",
            "Midnight OLED"
        };

        for (String tName : themes) {
            JButton tBtn = new JButton(tName);
            tBtn.setFont(theme.fontSmall);
            tBtn.setForeground(theme.textPrimary);
            tBtn.setBackground(theme.panel);
            tBtn.setOpaque(true);
            tBtn.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(theme.secondaryAccent, 1),
                    BorderFactory.createEmptyBorder(5, 8, 5, 8)
            ));
            tBtn.setFocusPainted(false);
            tBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            tBtn.addActionListener(e -> {
                ThemeManager.getInstance().applyTheme(tName);
                if (os.getSettingsService() != null) {
                    os.getSettingsService().set("theme.active", tName);
                }
                if (os.getSoundService() != null) os.getSoundService().playSlideSwitch();
                repaint();
            });
            themeGrid.add(tBtn);
        }
        contentBox.add(themeGrid);
        contentBox.add(Box.createVerticalStrut(12));

        // -- 4. Quick Actions --
        contentBox.add(createSectionHeader("QUICK ACTION PROTOCOLS"));
        JPanel actionsPanel = new JPanel(new GridLayout(1, 2, 8, 0));
        actionsPanel.setOpaque(false);

        NeonButton btnMissionCtrl = new NeonButton("Expose", null, NeonButton.Style.DEFAULT);
        btnMissionCtrl.addActionListener(e -> {
            setVisible(false);
            if (os.getDesktopFrame() != null) {
                os.getDesktopFrame().showMissionControl();
            }
        });

        NeonButton btnLock = new NeonButton("Lock", null, NeonButton.Style.DANGER);
        btnLock.addActionListener(e -> {
            setVisible(false);
            if (os.getDesktopFrame() != null) {
                os.getDesktopFrame().lock();
            }
        });

        actionsPanel.add(btnMissionCtrl);
        actionsPanel.add(btnLock);
        contentBox.add(actionsPanel);

        root.add(contentBox, BorderLayout.CENTER);

        // Escape Key to close
        root.registerKeyboardAction(e -> setVisible(false),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    private JLabel createSectionHeader(String title) {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        JLabel lbl = new JLabel("-- " + title + " --");
        lbl.setFont(theme.fontSmall);
        lbl.setForeground(theme.textSecondary);
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        lbl.setBorder(new EmptyBorder(4, 0, 4, 0));
        return lbl;
    }

    private JButton createPillButton(String text) {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        JButton btn = new JButton(text);
        btn.setFont(theme.fontSmall);
        btn.setForeground(theme.textSecondary);
        btn.setBackground(theme.panel);
        btn.setOpaque(true);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(theme.secondaryAccent, 1),
                BorderFactory.createEmptyBorder(5, 6, 5, 6)
        ));
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    public void showDrawer() {
 if (os.getDesktopFrame() != null) {
 int parentX = os.getDesktopFrame().getX();
 int parentY = os.getDesktopFrame().getY();
 int parentW = os.getDesktopFrame().getWidth();
 // Docked top-right
 setLocation(parentX + parentW - getWidth() - 16, parentY + 40);
 } else {
 setLocationRelativeTo(null);
 }

 if (os.getSoundService() != null) {
 os.getSoundService().playSlideSwitch();
 }

 setVisible(true);
 requestFocusInWindow();
 }
}

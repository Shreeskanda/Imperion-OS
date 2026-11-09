package javaos.ui;

import javaos.core.JavaOS;
import javaos.services.SecurityService;
import javaos.ui.components.CyberpunkIcons;
import javaos.ui.components.LogoManager;
import javaos.ui.components.NeonButton;
import javaos.ui.components.NeonCard;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Cyberpunk Authentication and Security Lock Screen for IMPERION OS.
 * Supports:
 *  - Automated Biometric Face Recognition with live HUD radar & auto-unlock
 *  - PIN Code On-Screen Keypad + Physical Numpad/Keyboard input
 *  - Cryptographic Salted SHA-256 Alphanumeric Password with Reveal Eye Toggle
 *  - Real OS Lockout Cooldown after failed attempts
 *  - Smooth Shake Card visual alert on invalid credentials
 */
public class LockScreen extends JPanel {

    private final JavaOS os;
    private final JLabel timeLabel;
    private final JLabel dateLabel;
    private final JLabel statusLabel;
    private final JPanel authPanel;
    private final JPanel cardWrapper;
    private final NeonCard card;

    private Timer clockTimer;
    private Timer shakeTimer;
    private Timer lockoutTimer;
    private int shakeOffset = 0;

    // Biometric scanner subpanel
    private BiometricHudPanel biometricHud;
    private SwingWorker<SecurityService.BiometricResult, Void> faceWorker;

    // PIN subpanel state
    private final StringBuilder pinBuffer = new StringBuilder();
    private JPanel pinDotsRow;

    // Password subpanel state
    private JPasswordField passField;
    private boolean passwordRevealed = false;

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("EEEE, MMMM dd, yyyy");

    public LockScreen(JavaOS os) {
        this.os = os;
        setLayout(new GridBagLayout());
        setOpaque(true);

        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        setBackground(theme.background);

        // Shake wrapper panel
        cardWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0)) {
            @Override
            protected void paintChildren(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.translate(shakeOffset, 0);
                super.paintChildren(g2);
                g2.dispose();
            }
        };
        cardWrapper.setOpaque(false);

        // Central neon authentication card
        card = new NeonCard();
        card.setPreferredSize(new Dimension(420, 560));
        card.setGlowing(true);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createEmptyBorder(20, 26, 20, 26));

        // Logo
        ImageIcon logoImg = LogoManager.getLogoIcon(88, 75);
        JLabel logo = new JLabel(logoImg != null ? logoImg : CyberpunkIcons.createIcon("SHIELD", 44));
        logo.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(logo);
        card.add(Box.createVerticalStrut(8));

        // System title
        JLabel sysTitle = new JLabel("IMPERION OS WORKSTATION");
        sysTitle.setFont(theme.fontBold);
        sysTitle.setForeground(theme.neonAccent);
        sysTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(sysTitle);

        card.add(Box.createVerticalStrut(4));

        // Big Clock
        timeLabel = new JLabel(LocalDateTime.now().format(TIME_FORMAT));
        timeLabel.setFont(new Font("Segoe UI", Font.BOLD, 34));
        timeLabel.setForeground(theme.textPrimary);
        timeLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(timeLabel);

        dateLabel = new JLabel(LocalDateTime.now().format(DATE_FORMAT));
        dateLabel.setFont(theme.fontSmall);
        dateLabel.setForeground(theme.textSecondary);
        dateLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(dateLabel);

        card.add(Box.createVerticalStrut(10));

        // Operator Badge
        JLabel userLabel = new JLabel("OPERATOR: " + System.getProperty("user.name", "SHREESKANDA").toUpperCase());
        userLabel.setFont(theme.fontSmall);
        userLabel.setForeground(theme.secondaryAccent);
        userLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(userLabel);

        card.add(Box.createVerticalStrut(12));

        // Dynamic Auth Content Container
        authPanel = new JPanel();
        authPanel.setLayout(new BoxLayout(authPanel, BoxLayout.Y_AXIS));
        authPanel.setOpaque(false);
        authPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(authPanel);

        card.add(Box.createVerticalGlue());

        // Status / feedback line
        statusLabel = new JLabel("Secure session locked.");
        statusLabel.setFont(theme.fontSmall);
        statusLabel.setForeground(theme.textSecondary);
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(statusLabel);

        cardWrapper.add(card);
        add(cardWrapper);

        // Clock timer
        clockTimer = new Timer(1000, e -> {
            timeLabel.setText(LocalDateTime.now().format(TIME_FORMAT));
            dateLabel.setText(LocalDateTime.now().format(DATE_FORMAT));
        });
        clockTimer.start();
    }

    /**
     * Re-evaluates security state and mounts the appropriate authentication controls
     * whenever the lock screen is presented.
     */
    public void onShow() {
        SecurityService sec = os.getSecurityService();
        Theme theme = ThemeManager.getInstance().getCurrentTheme();

        // Cancel previous face scanning worker if running
        if (faceWorker != null && !faceWorker.isDone()) {
            faceWorker.cancel(true);
        }

        pinBuffer.setLength(0);
        statusLabel.setForeground(theme.textSecondary);
        authPanel.removeAll();

        // 1. Check Lockout State
        if (sec.isLockedOut()) {
            mountLockoutView();
            authPanel.revalidate();
            authPanel.repaint();
            return;
        }

        // 2. Mount Biometric Face Reticle HUD if enabled & enrolled
        boolean faceActive = sec.isFaceRecognitionEnabled() && sec.isFaceEnrolled();
        if (faceActive) {
            biometricHud = new BiometricHudPanel(theme);
            biometricHud.setAlignmentX(Component.CENTER_ALIGNMENT);
            authPanel.add(biometricHud);
            authPanel.add(Box.createVerticalStrut(10));
        }

        // 3. Mount Primary Lock Input (PIN, Password, or None)
        SecurityService.LockType lockType = sec.getLockType();
        switch (lockType) {
            case PIN:
                mountPinView();
                break;
            case PASSWORD:
                mountPasswordView();
                break;
            case NONE:
                mountNoLockView(faceActive);
                break;
        }

        // Adjust card dimension dynamically
        if (faceActive && lockType == SecurityService.LockType.PIN) {
            card.setPreferredSize(new Dimension(420, 600));
        } else if (lockType == SecurityService.LockType.PIN) {
            card.setPreferredSize(new Dimension(380, 520));
        } else {
            card.setPreferredSize(new Dimension(380, 460));
        }

        authPanel.revalidate();
        authPanel.repaint();
        card.revalidate();
        card.repaint();

        // Start biometric scan automatically in background if active
        if (faceActive) {
            startFaceRecognitionScan();
        }
    }

    // ── BIOMETRIC HUD COMPONENT & LOGIC ─────────────────────────────────────

    private void startFaceRecognitionScan() {
        if (biometricHud == null) return;
        biometricHud.setScanState(BiometricHudPanel.State.SCANNING, "Scanning facial geometry...");
        statusLabel.setText("Optical sensor active. Please face the camera.");
        statusLabel.setForeground(ThemeManager.getInstance().getCurrentTheme().neonAccent);

        faceWorker = new SwingWorker<>() {
            @Override
            protected SecurityService.BiometricResult doInBackground() {
                return os.getSecurityService().verifyFaceBiometricsDetailed();
            }

            @Override
            protected void done() {
                if (isCancelled()) return;
                try {
                    SecurityService.BiometricResult res = get();
                    if ("cancelled".equalsIgnoreCase(res.getStatus())) return;
                    int conf = (int) (res.getConfidence() * 100);
                    if (res.isSuccess()) {
                        biometricHud.setScanState(BiometricHudPanel.State.SUCCESS, "Operator Verified (" + conf + "%)");
                        statusLabel.setText("Biometrics Accepted. Access granted.");
                        statusLabel.setForeground(ThemeManager.getInstance().getCurrentTheme().success);

                        // Unlock after short pause so user sees confirmation
                        Timer unlockDelay = new Timer(600, ev -> {
                            ((Timer) ev.getSource()).stop();
                            unlock();
                        });
                        unlockDelay.setRepeats(false);
                        unlockDelay.start();
                    } else {
                        biometricHud.setScanState(BiometricHudPanel.State.FAILURE, "Face Unrecognized");
                        statusLabel.setText("Biometric mismatch. Authenticate with credentials.");
                        statusLabel.setForeground(ThemeManager.getInstance().getCurrentTheme().warning);
                    }
                } catch (Exception ex) {
                    biometricHud.setScanState(BiometricHudPanel.State.FAILURE, "Camera Error");
                    statusLabel.setText("Camera optical scan failed.");
                }
            }
        };
        faceWorker.execute();
    }

    private static class BiometricHudPanel extends JPanel {
        enum State { IDLE, SCANNING, SUCCESS, FAILURE }
        private State state = State.SCANNING;
        private String message = "Optical Sensor Active";
        private final Theme theme;
        private double scanAngle = 0;
        private int scanLineY = 0;
        private boolean scanDirectionDown = true;
        private final Timer animTimer;

        public BiometricHudPanel(Theme theme) {
            this.theme = theme;
            setPreferredSize(new Dimension(220, 80));
            setMaximumSize(new Dimension(220, 80));
            setOpaque(false);

            animTimer = new Timer(40, e -> {
                scanAngle = (scanAngle + 0.1) % (2 * Math.PI);
                if (scanDirectionDown) {
                    scanLineY += 2;
                    if (scanLineY >= 50) scanDirectionDown = false;
                } else {
                    scanLineY -= 2;
                    if (scanLineY <= 4) scanDirectionDown = true;
                }
                repaint();
            });
            animTimer.start();
        }

        public void setScanState(State s, String msg) {
            this.state = s;
            this.message = msg;
            if (s == State.SUCCESS || s == State.FAILURE) {
                animTimer.stop();
            } else if (!animTimer.isRunning()) {
                animTimer.start();
            }
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int cx = getWidth() / 2;
            int cy = 30;
            int r = 26;

            Color hudColor = theme.neonAccent;
            if (state == State.SUCCESS) hudColor = theme.success;
            else if (state == State.FAILURE) hudColor = theme.error;

            // Reticle circle
            g2.setColor(new Color(hudColor.getRed(), hudColor.getGreen(), hudColor.getBlue(), 40));
            g2.fillOval(cx - r, cy - r, r * 2, r * 2);

            g2.setColor(hudColor);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawOval(cx - r, cy - r, r * 2, r * 2);

            // Corner targeting brackets
            int bLen = 8;
            g2.drawLine(cx - r - 4, cy - r - 4, cx - r - 4 + bLen, cy - r - 4);
            g2.drawLine(cx - r - 4, cy - r - 4, cx - r - 4, cy - r - 4 + bLen);

            g2.drawLine(cx + r + 4, cy - r - 4, cx + r + 4 - bLen, cy - r - 4);
            g2.drawLine(cx + r + 4, cy - r - 4, cx + r + 4, cy - r - 4 + bLen);

            g2.drawLine(cx - r - 4, cy + r + 4, cx - r - 4 + bLen, cy + r + 4);
            g2.drawLine(cx - r - 4, cy + r + 4, cx - r - 4, cy + r + 4 - bLen);

            g2.drawLine(cx + r + 4, cy + r + 4, cx + r + 4 - bLen, cy + r + 4);
            g2.drawLine(cx + r + 4, cy + r + 4, cx + r + 4, cy + r + 4 - bLen);

            // Face silhouette icon in center
            g2.drawOval(cx - 7, cy - 12, 14, 14); // Head
            g2.drawArc(cx - 12, cy + 3, 24, 18, 0, 180); // Shoulders

            // Animated scanning beam
            if (state == State.SCANNING) {
                int beamY = (cy - r) + scanLineY;
                g2.setColor(new Color(theme.neonAccent.getRed(), theme.neonAccent.getGreen(), theme.neonAccent.getBlue(), 180));
                g2.setStroke(new BasicStroke(2.0f));
                g2.drawLine(cx - r + 4, beamY, cx + r - 4, beamY);
            }

            // Status message label below reticle
            g2.setFont(theme.fontSmall);
            g2.setColor(hudColor);
            FontMetrics fm = g2.getFontMetrics();
            int strW = fm.stringWidth(message);
            g2.drawString(message, cx - (strW / 2), cy + r + 18);

            g2.dispose();
        }
    }

    // ── PIN CODE AUTHENTICATION VIEW ────────────────────────────────────────

    private void mountPinView() {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();

        // 1. PIN indicator dots row
        pinDotsRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 4));
        pinDotsRow.setOpaque(false);
        updatePinDots();
        authPanel.add(pinDotsRow);
        authPanel.add(Box.createVerticalStrut(10));

        // 2. Interactive Keypad (3x4 grid)
        JPanel keypad = new JPanel(new GridLayout(4, 3, 8, 8));
        keypad.setOpaque(false);
        keypad.setPreferredSize(new Dimension(230, 200));
        keypad.setMaximumSize(new Dimension(230, 200));

        String[] keys = {
                "1", "2", "3",
                "4", "5", "6",
                "7", "8", "9",
                "⌫", "0", "⏎"
        };

        for (String k : keys) {
            NeonButton btn;
            if ("⏎".equals(k)) {
                btn = new NeonButton(k, null, NeonButton.Style.PRIMARY);
                btn.setToolTipText("Authenticate PIN");
                btn.addActionListener(e -> submitPin());
            } else if ("⌫".equals(k)) {
                btn = new NeonButton(k, null, NeonButton.Style.DEFAULT);
                btn.setToolTipText("Backspace");
                btn.addActionListener(e -> {
                    if (pinBuffer.length() > 0) {
                        pinBuffer.deleteCharAt(pinBuffer.length() - 1);
                        updatePinDots();
                    }
                });
            } else {
                btn = new NeonButton(k, null, NeonButton.Style.DEFAULT);
                btn.addActionListener(e -> {
                    if (pinBuffer.length() < 8) {
                        pinBuffer.append(k);
                        updatePinDots();
                    }
                });
            }
            btn.setFont(new Font("Segoe UI", Font.BOLD, 16));
            keypad.add(btn);
        }

        keypad.setAlignmentX(Component.CENTER_ALIGNMENT);
        authPanel.add(keypad);

        // Keypad physical keyboard listener
        setFocusable(true);
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (os.getSecurityService().getLockType() != SecurityService.LockType.PIN) return;
                char c = e.getKeyChar();
                if (Character.isDigit(c)) {
                    if (pinBuffer.length() < 8) {
                        pinBuffer.append(c);
                        updatePinDots();
                    }
                } else if (e.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
                    if (pinBuffer.length() > 0) {
                        pinBuffer.deleteCharAt(pinBuffer.length() - 1);
                        updatePinDots();
                    }
                } else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    submitPin();
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    pinBuffer.setLength(0);
                    updatePinDots();
                }
            }
        });

        SwingUtilities.invokeLater(this::requestFocusInWindow);
    }

    private void updatePinDots() {
        if (pinDotsRow == null) return;
        pinDotsRow.removeAll();
        Theme theme = ThemeManager.getInstance().getCurrentTheme();

        int length = Math.max(4, pinBuffer.length());
        for (int i = 0; i < length; i++) {
            boolean filled = (i < pinBuffer.length());
            JLabel dot = new JLabel() {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    if (filled) {
                        g2.setColor(theme.neonAccent);
                        g2.fillOval(2, 2, 12, 12);
                    } else {
                        g2.setColor(new Color(255, 255, 255, 60));
                        g2.drawOval(2, 2, 12, 12);
                    }
                    g2.dispose();
                }
            };
            dot.setPreferredSize(new Dimension(16, 16));
            pinDotsRow.add(dot);
        }
        pinDotsRow.revalidate();
        pinDotsRow.repaint();
    }

    private void submitPin() {
        SecurityService sec = os.getSecurityService();
        String entered = pinBuffer.toString();

        if (sec.authenticate(entered)) {
            statusLabel.setText("PIN Verified. Access Granted.");
            statusLabel.setForeground(ThemeManager.getInstance().getCurrentTheme().success);
            unlock();
        } else {
            pinBuffer.setLength(0);
            updatePinDots();
            shakeCard();

            if (sec.isLockedOut()) {
                mountLockoutView();
            } else {
                statusLabel.setText("ACCESS DENIED — Invalid PIN (Failed attempts: " + sec.getFailedAttempts() + ")");
                statusLabel.setForeground(ThemeManager.getInstance().getCurrentTheme().error);
            }
        }
    }

    // ── PASSWORD AUTHENTICATION VIEW ────────────────────────────────────────

    private void mountPasswordView() {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();

        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(false);
        row.setPreferredSize(new Dimension(280, 36));
        row.setMaximumSize(new Dimension(280, 36));

        passField = new JPasswordField();
        passField.setBackground(theme.panel);
        passField.setForeground(theme.textPrimary);
        passField.setCaretColor(Color.WHITE);
        passField.setHorizontalAlignment(JTextField.CENTER);
        passField.setFont(new Font("Segoe UI", Font.BOLD, 16));
        passField.setBorder(BorderFactory.createLineBorder(theme.secondaryAccent));
        passField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    submitPassword();
                }
            }
        });

        // Eye toggle button
        JButton btnEye = new JButton("👁");
        btnEye.setOpaque(false);
        btnEye.setContentAreaFilled(false);
        btnEye.setFocusPainted(false);
        btnEye.setBorder(BorderFactory.createLineBorder(theme.secondaryAccent));
        btnEye.setForeground(theme.neonAccent);
        btnEye.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnEye.setPreferredSize(new Dimension(36, 36));
        btnEye.addActionListener(e -> {
            passwordRevealed = !passwordRevealed;
            if (passwordRevealed) {
                passField.setEchoChar((char) 0);
                btnEye.setForeground(theme.warning);
            } else {
                passField.setEchoChar('•');
                btnEye.setForeground(theme.neonAccent);
            }
        });

        row.add(passField, BorderLayout.CENTER);
        row.add(btnEye, BorderLayout.EAST);
        authPanel.add(row);

        authPanel.add(Box.createVerticalStrut(14));

        NeonButton unlockBtn = new NeonButton("AUTHENTICATE WORKSTATION", CyberpunkIcons.createIcon("PLAY", 16), NeonButton.Style.PRIMARY);
        unlockBtn.setPreferredSize(new Dimension(280, 38));
        unlockBtn.setMaximumSize(new Dimension(280, 38));
        unlockBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        unlockBtn.addActionListener(e -> submitPassword());
        authPanel.add(unlockBtn);

        SwingUtilities.invokeLater(passField::requestFocusInWindow);
    }

    private void submitPassword() {
        if (passField == null) return;
        SecurityService sec = os.getSecurityService();
        String entered = new String(passField.getPassword());

        if (sec.authenticate(entered)) {
            statusLabel.setText("Password Verified. Initializing Session...");
            statusLabel.setForeground(ThemeManager.getInstance().getCurrentTheme().success);
            unlock();
        } else {
            passField.setText("");
            shakeCard();

            if (sec.isLockedOut()) {
                mountLockoutView();
            } else {
                statusLabel.setText("ACCESS DENIED — Invalid Password (Failed: " + sec.getFailedAttempts() + ")");
                statusLabel.setForeground(ThemeManager.getInstance().getCurrentTheme().error);
                SwingUtilities.invokeLater(passField::requestFocusInWindow);
            }
        }
    }

    // ── NO LOCK (UNPROTECTED) VIEW ──────────────────────────────────────────

    private void mountNoLockView(boolean faceActive) {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();

        if (!faceActive) {
            JLabel lblPrompt = new JLabel("WORKSTATION UNLOCKED");
            lblPrompt.setFont(theme.fontBold);
            lblPrompt.setForeground(theme.success);
            lblPrompt.setAlignmentX(Component.CENTER_ALIGNMENT);
            authPanel.add(lblPrompt);
            authPanel.add(Box.createVerticalStrut(10));
        }

        NeonButton unlockBtn = new NeonButton("INITIALIZE SESSION", CyberpunkIcons.createIcon("PLAY", 16), NeonButton.Style.PRIMARY);
        unlockBtn.setPreferredSize(new Dimension(280, 38));
        unlockBtn.setMaximumSize(new Dimension(280, 38));
        unlockBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        unlockBtn.addActionListener(e -> unlock());
        authPanel.add(unlockBtn);

        setFocusable(true);
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    unlock();
                }
            }
        });
        SwingUtilities.invokeLater(unlockBtn::requestFocusInWindow);
    }

    // ── LOCKOUT VIEW ────────────────────────────────────────────────────────

    private void mountLockoutView() {
        authPanel.removeAll();
        Theme theme = ThemeManager.getInstance().getCurrentTheme();

        JLabel warn = new JLabel("SECURITY LOCKOUT ACTIVE");
        warn.setFont(new Font("Segoe UI", Font.BOLD, 16));
        warn.setForeground(theme.error);
        warn.setAlignmentX(Component.CENTER_ALIGNMENT);
        authPanel.add(warn);

        authPanel.add(Box.createVerticalStrut(8));

        JLabel cdLabel = new JLabel();
        cdLabel.setFont(theme.fontBold);
        cdLabel.setForeground(theme.warning);
        cdLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        authPanel.add(cdLabel);

        if (lockoutTimer != null && lockoutTimer.isRunning()) lockoutTimer.stop();
        lockoutTimer = new Timer(1000, ev -> {
            SecurityService sec = os.getSecurityService();
            if (sec.isLockedOut()) {
                cdLabel.setText("Cooldown: " + sec.getLockoutRemainingSeconds() + "s remaining");
            } else {
                ((Timer) ev.getSource()).stop();
                onShow(); // Reload normal auth inputs
            }
        });
        lockoutTimer.start();
        cdLabel.setText("Cooldown: " + os.getSecurityService().getLockoutRemainingSeconds() + "s remaining");

        authPanel.revalidate();
        authPanel.repaint();
    }

    // ── SHAKE CARD ANIMATION ────────────────────────────────────────────────

    private void shakeCard() {
        int[] offsets = {-14, 14, -10, 10, -6, 6, -2, 2, 0};
        final int[] step = {0};

        if (shakeTimer != null && shakeTimer.isRunning()) shakeTimer.stop();
        shakeTimer = new Timer(30, ev -> {
            if (step[0] < offsets.length) {
                shakeOffset = offsets[step[0]++];
                cardWrapper.repaint();
            } else {
                shakeOffset = 0;
                cardWrapper.repaint();
                ((Timer) ev.getSource()).stop();
            }
        });
        shakeTimer.start();
    }

    private void unlock() {
        if (faceWorker != null && !faceWorker.isDone()) {
            faceWorker.cancel(true);
        }
        if (lockoutTimer != null && lockoutTimer.isRunning()) {
            lockoutTimer.stop();
        }
        os.unlockScreen();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        g2.setPaint(new GradientPaint(0, 0, theme.background, 0, getHeight(), theme.panel));
        g2.fillRect(0, 0, getWidth(), getHeight());

        // Cyberpunk background grid lines
        g2.setColor(new Color(theme.neonAccent.getRed(), theme.neonAccent.getGreen(), theme.neonAccent.getBlue(), 15));
        for (int x = 0; x < getWidth(); x += 60) {
            g2.drawLine(x, 0, x, getHeight());
        }
        for (int y = 0; y < getHeight(); y += 60) {
            g2.drawLine(0, y, getWidth(), y);
        }
        g2.dispose();
    }
}

package javaos.ui;

import javaos.apps.OSApplication;
import javaos.core.JavaOS;
import javaos.core.WindowManager;
import javaos.models.NotificationMessage;
import javaos.ui.components.CyberpunkIcons;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Bottom system taskbar for JavaOS.
 * Contains the Start menu launcher, running process pills, live clock, and system tray widgets.
 */
public class Taskbar extends JPanel {

    private final JavaOS os;
    private final JButton btnStart;
    private final JPanel tasksPanel;
    private final JLabel clockTimeLabel;
    private final JLabel clockDateLabel;
    private final JLabel notificationBadge;
    private final JLabel memBadge;
    private Timer clockTimer;
    private JWindow notifPopup;
    private final JButton[] wsButtons = new JButton[WindowManager.TOTAL_WORKSPACES];

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM dd, yyyy");

    public Taskbar(JavaOS os) {
        this.os = os;
        setLayout(new BorderLayout(8, 0));
        setPreferredSize(new Dimension(0, 48));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        Theme theme = ThemeManager.getInstance().getCurrentTheme();

        // Left Section: Quick Launch icons (Start button removed from taskbar per user directive)
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        leftPanel.setOpaque(false);

        btnStart = createStartButton(); // Kept for reference but not added to taskbar

        // Quick Launch icons
        leftPanel.add(createSpotlightLaunch());
        leftPanel.add(createQuickLaunch("terminal", "TERMINAL", "Terminal"));
        leftPanel.add(createQuickLaunch("files", "FILES", "File Manager"));
        leftPanel.add(createQuickLaunch("wordeditor", "WORD", "Word Processor"));
        leftPanel.add(createQuickLaunch("sheet", "SHEET", "Spreadsheet Analytics"));
        leftPanel.add(createQuickLaunch("slides", "PRESENTATION", "Presentation Slides"));
        leftPanel.add(createQuickLaunch("browser", "BROWSER", "NetRunner Browser"));
        leftPanel.add(createQuickLaunch("player", "MUSIC", "NeonWave Media Player"));
        leftPanel.add(createQuickLaunch("arcade", "GAMEPAD", "Arcade Studio"));
        leftPanel.add(createQuickLaunch("victus", "VICTUS", "Victus AI Assistant"));

        add(leftPanel, BorderLayout.WEST);

        // Center Section: Running application pills
        tasksPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        tasksPanel.setOpaque(false);
        add(tasksPanel, BorderLayout.CENTER);

        // Right Section: System Tray & Clock
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 2));
        rightPanel.setOpaque(false);

        // Virtual Workspaces Switcher (1-4)
        JPanel wsPanel = createWorkspaceSwitcher();
        rightPanel.add(wsPanel);
        rightPanel.add(new JSeparator(SwingConstants.VERTICAL));

        // Mission Control / Exposé Tray Button
        JButton btnExpose = new JButton(CyberpunkIcons.createIcon("WINDOW", 15));
        btnExpose.setToolTipText("Mission Control / Window Exposé (Win+Tab)");
        btnExpose.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnExpose.setOpaque(false);
        btnExpose.setContentAreaFilled(false);
        btnExpose.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        btnExpose.addActionListener(e -> {
            if (os.getDesktopFrame() != null) {
                os.getDesktopFrame().showMissionControl();
            }
        });
        rightPanel.add(btnExpose);

        // Control Center Drawer Button (Ctrl+Alt+C)
        JButton btnControlCenter = new JButton(CyberpunkIcons.createIcon("SETTINGS", 15));
        btnControlCenter.setToolTipText("Cyberpunk Control Matrix (Ctrl+Alt+C)");
        btnControlCenter.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnControlCenter.setOpaque(false);
        btnControlCenter.setContentAreaFilled(false);
        btnControlCenter.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        btnControlCenter.addActionListener(e -> {
            if (os.getDesktopFrame() != null) {
                os.getDesktopFrame().showControlCenter();
            }
        });
        rightPanel.add(btnControlCenter);

        // Clipboard History Tray Button (Win+V)
        JButton btnClipboard = new JButton(CyberpunkIcons.createIcon("NOTEPAD", 15));
        btnClipboard.setToolTipText("Clipboard History (Win+V / Ctrl+Alt+V)");
        btnClipboard.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnClipboard.setOpaque(false);
        btnClipboard.setContentAreaFilled(false);
        btnClipboard.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        btnClipboard.addActionListener(e -> {
            if (os.getDesktopFrame() != null) {
                os.getDesktopFrame().showClipboardHistory();
            }
        });
        rightPanel.add(btnClipboard);

        // Desktop Widgets Toggle Button
        JButton btnWidgets = new JButton(CyberpunkIcons.createIcon("IMAGE", 15));
        btnWidgets.setToolTipText("Toggle Desktop Widgets (Clock, Telemetry, Memo)");
        btnWidgets.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnWidgets.setOpaque(false);
        btnWidgets.setContentAreaFilled(false);
        btnWidgets.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        btnWidgets.addActionListener(e -> {
            if (os.getDesktopFrame() != null && os.getDesktopFrame().getDesktopSurface() != null) {
                os.getDesktopFrame().getDesktopSurface().toggleWidgets();
            }
        });
        rightPanel.add(btnWidgets);

        memBadge = new JLabel("RAM: --%");
        memBadge.setFont(theme.fontSmall);
        memBadge.setForeground(theme.textSecondary);
        memBadge.setToolTipText("JVM Heap Memory Usage");
        memBadge.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        memBadge.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                os.getApplicationManager().launchApp("monitor");
            }
        });
        rightPanel.add(memBadge);

        notificationBadge = new JLabel(CyberpunkIcons.createIcon("SHIELD", 16));
        notificationBadge.setToolTipText("System Notifications");
        notificationBadge.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        notificationBadge.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                showNotificationCenter();
            }
        });
        rightPanel.add(notificationBadge);

        // Clock widget
        JPanel clockPanel = new JPanel();
        clockPanel.setLayout(new BoxLayout(clockPanel, BoxLayout.Y_AXIS));
        clockPanel.setOpaque(false);
        clockPanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        clockTimeLabel = new JLabel(LocalDateTime.now().format(TIME_FORMAT));
        clockTimeLabel.setFont(theme.fontBold);
        clockTimeLabel.setForeground(theme.neonAccent);
        clockTimeLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        clockDateLabel = new JLabel(LocalDateTime.now().format(DATE_FORMAT));
        clockDateLabel.setFont(theme.fontSmall);
        clockDateLabel.setForeground(theme.textSecondary);
        clockDateLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        clockPanel.add(clockTimeLabel);
        clockPanel.add(clockDateLabel);
        clockPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                os.getApplicationManager().launchApp("calendar");
            }
        });
        rightPanel.add(clockPanel);

        // Show Desktop toggle button on far right edge
        JButton btnShowDesktop = new JButton();
        btnShowDesktop.setPreferredSize(new Dimension(8, 36));
        btnShowDesktop.setOpaque(false);
        btnShowDesktop.setContentAreaFilled(false);
        btnShowDesktop.setBorderPainted(false);
        btnShowDesktop.setToolTipText("Show Desktop");
        btnShowDesktop.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnShowDesktop.addActionListener(e -> os.getWindowManager().minimizeAll());
        rightPanel.add(btnShowDesktop);

        add(rightPanel, BorderLayout.EAST);

        // Listen for application lifecycle changes to update taskbar items
        os.getApplicationManager().addRunningAppsListener(this::refreshRunningTasks);
        os.getWindowManager().addChangeListener(this::refreshRunningTasks);

        // 1-second system clock and telemetry update timer
        clockTimer = new Timer(1000, e -> updateClockAndMetrics());
        clockTimer.start();
    }

    private JButton createStartButton() {
        JButton btn = new JButton(" START", CyberpunkIcons.createIcon("START", 20));
        btn.setPreferredSize(new Dimension(100, 36));
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        btn.setFont(theme.fontBold);
        btn.setForeground(theme.textPrimary);

        btn.addActionListener(e -> os.toggleStartMenu());
        return btn;
    }

    private JButton createQuickLaunch(String appId, String iconType, String tooltip) {
        JButton btn = new JButton(CyberpunkIcons.createIcon(iconType, 20));
        btn.setPreferredSize(new Dimension(34, 34));
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setToolTipText(tooltip);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addActionListener(e -> os.getApplicationManager().launchApp(appId));
        return btn;
    }

    private JButton createSpotlightLaunch() {
        JButton btn = new JButton(CyberpunkIcons.createIcon("SEARCH", 18));
        btn.setPreferredSize(new Dimension(34, 34));
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setToolTipText("Global Spotlight Quick Search (Alt+Space)");
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addActionListener(e -> {
            if (os.getDesktopFrame() != null && os.getDesktopFrame().getSpotlightDialog() != null) {
                os.getDesktopFrame().getSpotlightDialog().showSpotlight();
            }
        });
        return btn;
    }

    private JPanel createWorkspaceSwitcher() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 3, 0));
        panel.setOpaque(false);
        Theme theme = ThemeManager.getInstance().getCurrentTheme();

        for (int i = 0; i < WindowManager.TOTAL_WORKSPACES; i++) {
            final int wsIdx = i;
            JButton btn = new JButton(String.valueOf(i + 1));
            btn.setPreferredSize(new Dimension(24, 26));
            btn.setFont(new Font("Segoe UI", Font.BOLD, 11));
            btn.setFocusPainted(false);
            btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            btn.setToolTipText("Switch to " + WindowManager.getWorkspaceName(i) + " (Ctrl+Alt+" + (i + 1) + ")");
            btn.addActionListener(e -> {
                os.getWindowManager().switchWorkspace(wsIdx);
                if (os.getSoundService() != null) os.getSoundService().playSlideSwitch();
                updateWorkspaceButtons();
                refreshRunningTasks();
            });
            wsButtons[i] = btn;
            panel.add(btn);
        }
        updateWorkspaceButtons();

        if (os.getWindowManager() != null) {
            os.getWindowManager().addChangeListener(() -> {
                updateWorkspaceButtons();
                refreshRunningTasks();
            });
        }
        return panel;
    }

    public void updateWorkspaceButtons() {
        if (os.getWindowManager() == null) return;
        int current = os.getWindowManager().getCurrentWorkspace();
        Theme theme = ThemeManager.getInstance().getCurrentTheme();

        for (int i = 0; i < WindowManager.TOTAL_WORKSPACES; i++) {
            boolean active = (i == current);
            JButton btn = wsButtons[i];
            if (btn == null) continue;

            btn.setForeground(active ? theme.neonAccent : theme.textSecondary);
            btn.setBackground(active ? theme.elevatedPanel : theme.panel);
            btn.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(active ? theme.neonAccent : theme.secondaryAccent, active ? 2 : 1),
                    BorderFactory.createEmptyBorder(2, 4, 2, 4)
            ));
        }
    }

    public synchronized void refreshRunningTasks() {
        tasksPanel.removeAll();
        List<OSApplication> apps = os.getApplicationManager().getRunningApps();
        InternalWindow activeWin = os.getWindowManager().getActiveWindow();

        for (OSApplication app : apps) {
            InternalWindow win = app.getWindow();
            if (win == null) continue;
            if (win.getWorkspaceIndex() != os.getWindowManager().getCurrentWorkspace()) continue;

            boolean isActive = (win == activeWin && !win.isMinimized());
            JButton taskBtn = new JButton(win.getTitle(), win.getIcon());
            taskBtn.setPreferredSize(new Dimension(150, 34));
            taskBtn.setOpaque(false);
            taskBtn.setContentAreaFilled(false);
            taskBtn.setFocusPainted(false);
            taskBtn.setBorderPainted(false);
            taskBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            Theme theme = ThemeManager.getInstance().getCurrentTheme();
            taskBtn.setFont(theme.fontSmall);
            taskBtn.setForeground(isActive ? theme.textPrimary : theme.textSecondary);

            taskBtn.addActionListener(e -> {
                if (win.isMinimized()) {
                    win.restore();
                } else if (win == os.getWindowManager().getActiveWindow()) {
                    win.toggleMinimize();
                } else {
                    os.getWindowManager().setActiveWindow(win);
                }
            });

            // Custom border painter for task pill
            JPanel pill = new JPanel(new BorderLayout()) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    Theme t = ThemeManager.getInstance().getCurrentTheme();
                    g2.setColor(isActive ? t.elevatedPanel : t.panel);
                    g2.fill(new RoundRectangle2D.Float(1, 1, getWidth() - 2, getHeight() - 2, 8, 8));

                    g2.setColor(isActive ? t.neonAccent : t.secondaryAccent);
                    g2.setStroke(new BasicStroke(isActive ? 1.5f : 0.8f));
                    g2.draw(new RoundRectangle2D.Float(1.5f, 1.5f, getWidth() - 3, getHeight() - 3, 8, 8));

                    if (isActive) {
                        g2.setColor(t.neonAccent);
                        g2.fillRect(6, getHeight() - 3, getWidth() - 12, 2);
                    }
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            pill.setOpaque(false);
            pill.add(taskBtn, BorderLayout.CENTER);
            tasksPanel.add(pill);
        }

        tasksPanel.revalidate();
        tasksPanel.repaint();
    }

    private DateTimeFormatter getTimeFormatter() {
        if (os == null || os.getSettingsService() == null) return TIME_FORMAT;
        boolean is12h = "12h".equalsIgnoreCase(os.getSettingsService().getTaskbarClockFormat());
        boolean hasSec = os.getSettingsService().isTaskbarClockSeconds();
        if (is12h) {
            return hasSec ? DateTimeFormatter.ofPattern("hh:mm:ss a") : DateTimeFormatter.ofPattern("hh:mm a");
        } else {
            return hasSec ? DateTimeFormatter.ofPattern("HH:mm:ss") : DateTimeFormatter.ofPattern("HH:mm");
        }
    }

    private void updateClockAndMetrics() {
        LocalDateTime now = LocalDateTime.now();
        clockTimeLabel.setText(now.format(getTimeFormatter()));
        clockDateLabel.setText(now.format(DATE_FORMAT));

        double heapPct = os.getSystemMetricsService().getJvmHeapUsagePercent();
        memBadge.setText(String.format("JVM Heap: %.0f%%", heapPct));

        // Update notification badge icon to show unread count
        int unread = os.getNotificationManager().getUnreadCount();
        if (unread > 0) {
            notificationBadge.setIcon(CyberpunkIcons.createIcon("SHIELD", 16));
            notificationBadge.setText("<html><b style='color:#ff4444;font-size:8px'>" + unread + "</b></html>");
        } else {
            notificationBadge.setIcon(CyberpunkIcons.createIcon("SHIELD", 16));
            notificationBadge.setText("");
        }
    }

    private void showNotificationCenter() {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        List<NotificationMessage> history = os.getNotificationManager().getHistory();

        // Build a styled scrollable list
        JPanel listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBackground(theme.panel);
        listPanel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        if (history.isEmpty()) {
            JLabel empty = new JLabel("  No notifications yet.");
            empty.setFont(theme.fontRegular);
            empty.setForeground(theme.textSecondary);
            listPanel.add(empty);
        } else {
            for (int i = history.size() - 1; i >= 0; i--) {
                NotificationMessage msg = history.get(i);
                Color levelColor;
                switch (msg.getLevel()) {
                    case ERROR:   levelColor = new Color(255, 80, 80);   break;
                    case WARNING: levelColor = new Color(255, 190, 50);  break;
                    case SUCCESS: levelColor = new Color(80, 230, 120);  break;
                    default:      levelColor = theme.neonAccent;          break;
                }

                JPanel row = new JPanel(new BorderLayout(8, 0));
                row.setOpaque(false);
                row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
                row.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 3, 0, 0, levelColor),
                    BorderFactory.createEmptyBorder(4, 8, 4, 8)
                ));

                JLabel badge = new JLabel("[" + msg.getLevel() + "]");
                badge.setFont(theme.fontSmall.deriveFont(Font.BOLD));
                badge.setForeground(levelColor);
                badge.setPreferredSize(new Dimension(74, 16));

                JLabel titleLbl = new JLabel(msg.getTitle());
                titleLbl.setFont(theme.fontSmall.deriveFont(Font.BOLD));
                titleLbl.setForeground(theme.textPrimary);

                JLabel msgLbl = new JLabel(msg.getMessage());
                msgLbl.setFont(theme.fontSmall);
                msgLbl.setForeground(theme.textSecondary);

                JLabel timeLbl = new JLabel(msg.getFormattedTime());
                timeLbl.setFont(theme.fontSmall.deriveFont(9f));
                timeLbl.setForeground(theme.textSecondary);
                timeLbl.setPreferredSize(new Dimension(55, 16));
                timeLbl.setHorizontalAlignment(SwingConstants.RIGHT);

                JPanel textCol = new JPanel(new GridLayout(2, 1, 0, 1));
                textCol.setOpaque(false);
                textCol.add(titleLbl);
                textCol.add(msgLbl);

                row.add(badge, BorderLayout.WEST);
                row.add(textCol, BorderLayout.CENTER);
                row.add(timeLbl, BorderLayout.EAST);
                listPanel.add(row);
                listPanel.add(Box.createVerticalStrut(3));
            }
        }

        JScrollPane sp = new JScrollPane(listPanel);
        sp.setPreferredSize(new Dimension(460, 280));
        sp.getVerticalScrollBar().setUI(new javaos.ui.components.NeonScrollBarUI());
        sp.setBorder(BorderFactory.createLineBorder(theme.secondaryAccent));
        sp.getViewport().setBackground(theme.panel);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        bottom.setBackground(theme.background);
        javaos.ui.components.NeonButton btnClear = new javaos.ui.components.NeonButton("Clear All",
                CyberpunkIcons.createIcon("STOP", 12),
                javaos.ui.components.NeonButton.Style.DANGER);
        javaos.ui.components.NeonButton btnClose = new javaos.ui.components.NeonButton("Close",
                CyberpunkIcons.createIcon("SHIELD", 12),
                javaos.ui.components.NeonButton.Style.PRIMARY);
        bottom.add(btnClear);
        bottom.add(btnClose);

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(theme.elevatedPanel);
        JLabel headerLbl = new JLabel("  System Notifications (" + history.size() + ")");
        headerLbl.setFont(theme.fontBold);
        headerLbl.setForeground(theme.neonAccent);
        headerLbl.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        header.add(headerLbl, BorderLayout.CENTER);

        JPanel container = new JPanel(new BorderLayout());
        container.setBackground(theme.panel);
        container.add(header, BorderLayout.NORTH);
        container.add(sp, BorderLayout.CENTER);
        container.add(bottom, BorderLayout.SOUTH);

        JDialog dialog = new JDialog((java.awt.Frame) SwingUtilities.getWindowAncestor(this),
                "IMPERION OS — Notification Center", false);
        dialog.setContentPane(container);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        btnClose.addActionListener(e -> {
            os.getNotificationManager().markAllAsRead();
            notificationBadge.setText("");
            dialog.dispose();
        });
        btnClear.addActionListener(e -> {
            os.getNotificationManager().clearHistory();
            notificationBadge.setText("");
            dialog.dispose();
        });

        dialog.setVisible(true);
        os.getNotificationManager().markAllAsRead();
    }

    public Component getStartButton() {
        if (btnStart != null && btnStart.isShowing()) {
            return btnStart;
        }
        return this;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        int w = getWidth();
        int h = getHeight();

        // Taskbar background
        g2.setColor(theme.panel);
        g2.fillRect(0, 0, w, h);

        // Top neon line
        g2.setColor(theme.neonAccent);
        g2.setStroke(new BasicStroke(1.2f));
        g2.drawLine(0, 0, w, 0);

        // Subtle glow line below top line
        g2.setColor(new Color(theme.neonAccent.getRed(), theme.neonAccent.getGreen(), theme.neonAccent.getBlue(), 40));
        g2.drawLine(0, 1, w, 1);

        g2.dispose();
        super.paintComponent(g);
    }
}

package javaos.ui;

import javaos.core.JavaOS;
import javaos.core.WindowManager;
import javaos.ui.components.CyberpunkIcons;
import javaos.ui.components.NeonButton;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.util.List;

/**
 * Cyberpunk Mission Control / Window Exposé for IMPERION OS.
 * Activated via Win+Tab, Ctrl+Alt+Tab, or taskbar icon.
 * Features:
 *  - Responsive tiled mosaic grid of all open windows across virtual workspaces
 *  - Interactive workspace tabs [1: General] [2: Office] [3: Dev] [4: Media] [ALL]
 *  - 1-click focus and workspace warp with sound chime
 *  - Esc / click outside dismissal
 */
public class MissionControlOverlay extends JDialog {

    private final JavaOS os;
    private int selectedWorkspaceTab = -1; // -1 = current workspace, -2 = all workspaces
    private JPanel cardsGridPanel;
    private JPanel workspaceBarPanel;

    public MissionControlOverlay(JFrame parent, JavaOS os) {
        super(parent, false);
        this.os = os;

        setUndecorated(true);
        setBackground(new Color(0, 0, 0, 0));

        JPanel root = new JPanel(new BorderLayout(0, 14)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Theme theme = ThemeManager.getInstance().getCurrentTheme();

                // 1. Cyberpunk backdrop
                g2.setColor(new Color(5, 8, 16, 230));
                g2.fillRect(0, 0, getWidth(), getHeight());

                // 2. Faint cyber grid
                g2.setColor(new Color(theme.neonAccent.getRed(), theme.neonAccent.getGreen(), theme.neonAccent.getBlue(), 20));
                g2.setStroke(new BasicStroke(1.0f));
                for (int x = 0; x < getWidth(); x += 40) {
                    g2.drawLine(x, 0, x, getHeight());
                }
                for (int y = 0; y < getHeight(); y += 40) {
                    g2.drawLine(0, y, getWidth(), y);
                }

                // 3. Scanline overlay
                g2.setColor(new Color(0, 0, 0, 35));
                for (int y = 0; y < getHeight(); y += 3) {
                    g2.drawLine(0, y, getWidth(), y);
                }
                g2.dispose();
                super.paintComponent(g);
            }
        };
        root.setOpaque(false);
        root.setBorder(new EmptyBorder(24, 32, 24, 32));
        setContentPane(root);

        // Header Panel: Title + Close Button
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);

        JLabel titleLabel = new JLabel("[*] MISSION CONTROL // WORKSPACE EXPOSE");
        titleLabel.setFont(ThemeManager.getInstance().getCurrentTheme().fontDisplay);
        titleLabel.setForeground(ThemeManager.getInstance().getCurrentTheme().neonAccent);
        headerPanel.add(titleLabel, BorderLayout.WEST);

        NeonButton btnClose = new NeonButton("Close (Esc)", null, NeonButton.Style.DEFAULT);
        btnClose.setPreferredSize(new Dimension(130, 32));
        btnClose.addActionListener(e -> closeOverlay());
        headerPanel.add(btnClose, BorderLayout.EAST);

        // Workspace Switcher Bar
        workspaceBarPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        workspaceBarPanel.setOpaque(false);

        JPanel topContainer = new JPanel(new BorderLayout(0, 12));
        topContainer.setOpaque(false);
        topContainer.add(headerPanel, BorderLayout.NORTH);
        topContainer.add(workspaceBarPanel, BorderLayout.SOUTH);
        root.add(topContainer, BorderLayout.NORTH);

        // Grid of Window Cards
        cardsGridPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 22, 22));
        cardsGridPanel.setOpaque(false);

        JScrollPane scrollPane = new JScrollPane(cardsGridPanel);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        root.add(scrollPane, BorderLayout.CENTER);

        // Escape Key Binding
        root.registerKeyboardAction(e -> closeOverlay(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        // Click on background closes overlay
        root.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.getSource() == root || e.getSource() == cardsGridPanel) {
                    closeOverlay();
                }
            }
        });
    }

    public void showOverlay() {
        if (os.getDesktopFrame() != null) {
            setBounds(os.getDesktopFrame().getBounds());
        } else {
            setSize(1200, 750);
            setLocationRelativeTo(null);
        }

        selectedWorkspaceTab = os.getWindowManager().getCurrentWorkspace();
        refreshWorkspaceTabs();
        refreshCards();

        if (os.getSoundService() != null) {
            os.getSoundService().playSlideSwitch();
        }

        setVisible(true);
        requestFocusInWindow();
    }

    public void closeOverlay() {
        setVisible(false);
    }

    private void refreshWorkspaceTabs() {
        workspaceBarPanel.removeAll();
        Theme theme = ThemeManager.getInstance().getCurrentTheme();

        for (int i = 0; i < WindowManager.TOTAL_WORKSPACES; i++) {
            final int wsIndex = i;
            int count = os.getWindowManager().getWindowsInWorkspace(wsIndex).size();
            String label = (wsIndex + 1) + ": " + WindowManager.getWorkspaceName(wsIndex) + " (" + count + ")";
            boolean isActive = (selectedWorkspaceTab == wsIndex);

            JButton btn = new JButton(label);
            btn.setFont(theme.fontBold);
            btn.setForeground(isActive ? Color.WHITE : theme.textSecondary);
            btn.setBackground(isActive ? theme.neonAccent : theme.panel);
            btn.setOpaque(true);
            btn.setBorderPainted(true);
            btn.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(isActive ? theme.neonAccent : theme.secondaryAccent, 1),
                    BorderFactory.createEmptyBorder(6, 14, 6, 14)
            ));
            btn.setFocusPainted(false);
            btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            btn.addActionListener(e -> {
                selectedWorkspaceTab = wsIndex;
                refreshWorkspaceTabs();
                refreshCards();
            });
            workspaceBarPanel.add(btn);
        }

        // ALL WORKSPACES Tab
        boolean isAll = (selectedWorkspaceTab == -2);
        int totalWindows = os.getWindowManager().getWindows().size();
        JButton btnAll = new JButton("[*] ALL WORKSPACES (" + totalWindows + ")");
        btnAll.setFont(theme.fontBold);
        btnAll.setForeground(isAll ? Color.WHITE : theme.textSecondary);
        btnAll.setBackground(isAll ? theme.secondaryAccent : theme.panel);
        btnAll.setOpaque(true);
        btnAll.setBorderPainted(true);
        btnAll.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(theme.secondaryAccent, 1),
                BorderFactory.createEmptyBorder(6, 14, 6, 14)
        ));
        btnAll.setFocusPainted(false);
        btnAll.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnAll.addActionListener(e -> {
            selectedWorkspaceTab = -2;
            refreshWorkspaceTabs();
            refreshCards();
        });
        workspaceBarPanel.add(btnAll);

        workspaceBarPanel.revalidate();
        workspaceBarPanel.repaint();
    }

    private void refreshCards() {
        cardsGridPanel.removeAll();
        Theme theme = ThemeManager.getInstance().getCurrentTheme();

        List<InternalWindow> targetWindows;
        if (selectedWorkspaceTab == -2) {
            targetWindows = os.getWindowManager().getWindows();
        } else {
            targetWindows = os.getWindowManager().getWindowsInWorkspace(selectedWorkspaceTab);
        }

        if (targetWindows.isEmpty()) {
            JLabel emptyLabel = new JLabel("NO ACTIVE WINDOWS IN THIS SECTOR");
            emptyLabel.setFont(theme.fontBold);
            emptyLabel.setForeground(theme.textSecondary);
            cardsGridPanel.add(emptyLabel);
        } else {
            for (InternalWindow w : targetWindows) {
                cardsGridPanel.add(createWindowPreviewCard(w, theme));
            }
        }

        cardsGridPanel.revalidate();
        cardsGridPanel.repaint();
    }

    private JComponent createWindowPreviewCard(InternalWindow w, Theme theme) {
        int cardW = 270;
        int cardH = 175;

        final boolean[] hovered = {false};

        JPanel card = new JPanel(new BorderLayout(0, 6)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Background
                g2.setColor(hovered[0] ? theme.elevatedPanel : theme.panel);
                g2.fill(new RoundRectangle2D.Float(2, 2, getWidth() - 4, getHeight() - 4, 12, 12));

                // Glowing border
                Color borderColor = hovered[0] ? theme.neonAccent : theme.secondaryAccent;
                g2.setColor(borderColor);
                g2.setStroke(new BasicStroke(hovered[0] ? 2.0f : 1.2f));
                g2.draw(new RoundRectangle2D.Float(2, 2, getWidth() - 4, getHeight() - 4, 12, 12));

                // Subtle inner grid lines in preview area
                g2.setColor(new Color(theme.neonAccent.getRed(), theme.neonAccent.getGreen(), theme.neonAccent.getBlue(), hovered[0] ? 28 : 12));
                for (int y = 40; y < getHeight() - 10; y += 14) {
                    g2.drawLine(14, y, getWidth() - 14, y);
                }

                g2.dispose();
                super.paintComponent(g);
            }
        };
        card.setPreferredSize(new Dimension(cardW, cardH));
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(10, 12, 10, 12));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // Card Top Bar (Icon + Title + Workspace Badge)
        JPanel topBar = new JPanel(new BorderLayout(6, 0));
        topBar.setOpaque(false);

        JLabel titleLbl = new JLabel(w.getTitle(), CyberpunkIcons.createIcon("WINDOW", 16), SwingConstants.LEFT);
        titleLbl.setFont(theme.fontBold);
        titleLbl.setForeground(theme.textPrimary);
        topBar.add(titleLbl, BorderLayout.CENTER);

        JLabel wsBadge = new JLabel("WS " + (w.getWorkspaceIndex() + 1));
        wsBadge.setFont(theme.fontSmall);
        wsBadge.setForeground(theme.neonAccent);
        wsBadge.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(theme.neonAccent, 1),
                BorderFactory.createEmptyBorder(2, 4, 2, 4)
        ));
        topBar.add(wsBadge, BorderLayout.EAST);
        card.add(topBar, BorderLayout.NORTH);

        // Preview Center
        JPanel previewCenter = new JPanel(new GridBagLayout());
        previewCenter.setOpaque(false);

        String status = w.isMinimized() ? "STATUS: MINIMIZED" : (w.isMaximized() ? "STATUS: MAXIMIZED" : "STATUS: ACTIVE");
        JLabel statusLbl = new JLabel(status);
        statusLbl.setFont(theme.fontMonospace);
        statusLbl.setForeground(theme.textSecondary);
        previewCenter.add(statusLbl);
        card.add(previewCenter, BorderLayout.CENTER);

        // Card Bottom Bar (App ID / Dimensions)
        JPanel bottomBar = new JPanel(new BorderLayout());
        bottomBar.setOpaque(false);
        JLabel dimLbl = new JLabel(w.getWidth() + "x" + w.getHeight() + "px");
        dimLbl.setFont(theme.fontSmall);
        dimLbl.setForeground(theme.textSecondary);
        bottomBar.add(dimLbl, BorderLayout.WEST);

        JLabel clickLbl = new JLabel("CLICK TO WARP ->");
        clickLbl.setFont(theme.fontSmall);
        clickLbl.setForeground(theme.neonAccent);
        bottomBar.add(clickLbl, BorderLayout.EAST);
        card.add(bottomBar, BorderLayout.SOUTH);

        // Click and Hover listeners
        card.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                hovered[0] = true;
                card.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hovered[0] = false;
                card.repaint();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                closeOverlay();
                // Switch to the window's workspace
                if (w.getWorkspaceIndex() != os.getWindowManager().getCurrentWorkspace()) {
                    os.getWindowManager().switchWorkspace(w.getWorkspaceIndex());
                }
                // Restore if minimized
                if (w.isMinimized()) {
                    w.restore();
                }
                w.focusWindow();
                if (os.getSoundService() != null) {
                    os.getSoundService().playLatch();
                }
            }
        });

        return card;
    }
}

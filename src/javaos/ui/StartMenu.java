package javaos.ui;

import javaos.apps.AppDescriptor;
import javaos.core.JavaOS;
import javaos.ui.components.CyberpunkIcons;
import javaos.ui.components.LogoManager;
import javaos.ui.components.NeonScrollBarUI;
import javaos.ui.components.NeonTextField;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.List;

/**
 * Cyberpunk Start Menu for JavaOS.
 * Provides real-time application searching, user telemetry, and power actions.
 */
public class StartMenu extends JPopupMenu {

    private final JavaOS os;
    private final NeonTextField searchField;
    private final JPanel appsListPanel;
    private static final int MENU_WIDTH = 340;
    private static final int MENU_HEIGHT = 480;

    public StartMenu(JavaOS os) {
        this.os = os;
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(MENU_WIDTH, MENU_HEIGHT));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        Theme theme = ThemeManager.getInstance().getCurrentTheme();

        // Main container panel
        JPanel mainPanel = new JPanel(new BorderLayout(0, 10)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Theme t = ThemeManager.getInstance().getCurrentTheme();
                g2.setColor(t.elevatedPanel);
                g2.fill(new RoundRectangle2D.Float(1, 1, getWidth() - 2, getHeight() - 2, 14, 14));

                // Border glow
                g2.setColor(t.neonAccent);
                g2.setStroke(new BasicStroke(1.2f));
                g2.draw(new RoundRectangle2D.Float(1.5f, 1.5f, getWidth() - 3, getHeight() - 3, 14, 14));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        mainPanel.setOpaque(false);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        // Header: User Profile
        JPanel headerPanel = new JPanel(new BorderLayout(10, 0));
        headerPanel.setOpaque(false);

        ImageIcon startLogo = LogoManager.getLogoIcon(36, 31);
        JLabel avatarLabel = new JLabel(startLogo != null ? startLogo : CyberpunkIcons.createIcon("START", 32));
        headerPanel.add(avatarLabel, BorderLayout.WEST);

        JPanel userTextPanel = new JPanel(new GridLayout(2, 1));
        userTextPanel.setOpaque(false);
        JLabel userNameLabel = new JLabel(System.getProperty("user.name", "Operator").toUpperCase());
        userNameLabel.setFont(theme.fontBold);
        userNameLabel.setForeground(theme.textPrimary);

        JLabel userRoleLabel = new JLabel("IMPERION OS Administrator");
        userRoleLabel.setFont(theme.fontSmall);
        userRoleLabel.setForeground(theme.neonAccent);

        userTextPanel.add(userNameLabel);
        userTextPanel.add(userRoleLabel);
        headerPanel.add(userTextPanel, BorderLayout.CENTER);

        // Search bar
        searchField = new NeonTextField();
        searchField.setPlaceholder("Search applications...");
        searchField.setPreferredSize(new Dimension(0, 32));
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { filterApps(); }
            @Override
            public void removeUpdate(DocumentEvent e) { filterApps(); }
            @Override
            public void changedUpdate(DocumentEvent e) { filterApps(); }
        });

        JPanel topSection = new JPanel(new BorderLayout(0, 8));
        topSection.setOpaque(false);
        topSection.add(headerPanel, BorderLayout.NORTH);
        topSection.add(searchField, BorderLayout.SOUTH);
        mainPanel.add(topSection, BorderLayout.NORTH);

        // Center: Applications list
        appsListPanel = new JPanel();
        appsListPanel.setLayout(new BoxLayout(appsListPanel, BoxLayout.Y_AXIS));
        appsListPanel.setOpaque(false);

        JScrollPane scrollPane = new JScrollPane(appsListPanel);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUI(new NeonScrollBarUI());
        scrollPane.getVerticalScrollBar().setPreferredSize(new Dimension(6, 0));
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // Bottom Section: Power controls
        JPanel footerPanel = new JPanel(new BorderLayout());
        footerPanel.setOpaque(false);
        footerPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(theme.secondaryAccent.getRed(), theme.secondaryAccent.getGreen(), theme.secondaryAccent.getBlue(), 80)));

        JPanel powerButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 6));
        powerButtons.setOpaque(false);

        JButton lockBtn = createFooterButton("LOCK", "Lock Screen", e -> {
            setVisible(false);
            os.lockScreen();
        });

        JButton refreshBtn = createFooterButton("REFRESH", "Refresh Shell", e -> {
            setVisible(false);
            os.refreshDesktop();
        });

        JButton shutdownBtn = createFooterButton("POWER", "Shutdown IMPERION OS", e -> {
            setVisible(false);
            os.requestShutdown();
        });

        powerButtons.add(lockBtn);
        powerButtons.add(refreshBtn);
        powerButtons.add(shutdownBtn);
        footerPanel.add(powerButtons, BorderLayout.EAST);

        mainPanel.add(footerPanel, BorderLayout.SOUTH);

        add(mainPanel, BorderLayout.CENTER);
        filterApps();
    }

    private JButton createFooterButton(String iconType, String tooltip, java.awt.event.ActionListener action) {
        JButton btn = new JButton(CyberpunkIcons.createIcon(iconType, 16));
        btn.setPreferredSize(new Dimension(30, 30));
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setToolTipText(tooltip);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addActionListener(action);
        return btn;
    }

    public void filterApps() {
        appsListPanel.removeAll();
        String query = searchField.getText().trim().toLowerCase();
        List<AppDescriptor> apps = os.getApplicationManager().getRegisteredApps();

        Theme theme = ThemeManager.getInstance().getCurrentTheme();

        for (AppDescriptor desc : apps) {
            if (!query.isEmpty() && !desc.getName().toLowerCase().contains(query) && !desc.getDescription().toLowerCase().contains(query)) {
                continue;
            }

            JPanel item = new JPanel(new BorderLayout(10, 0));
            item.setOpaque(false);
            item.setMaximumSize(new Dimension(300, 42));
            item.setPreferredSize(new Dimension(300, 42));
            item.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
            item.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            JLabel icon = new JLabel(desc.getIcon());
            item.add(icon, BorderLayout.WEST);

            JPanel text = new JPanel(new GridLayout(2, 1));
            text.setOpaque(false);
            JLabel name = new JLabel(desc.getName());
            name.setFont(theme.fontBold);
            name.setForeground(theme.textPrimary);

            JLabel sub = new JLabel(desc.getDescription());
            sub.setFont(theme.fontSmall);
            sub.setForeground(theme.textSecondary);

            text.add(name);
            text.add(sub);
            item.add(text, BorderLayout.CENTER);

            final boolean[] hovered = {false};
            item.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hovered[0] = true;
                    item.repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hovered[0] = false;
                    item.repaint();
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    setVisible(false);
                    os.getApplicationManager().launchApp(desc.getId());
                }
            });

            JPanel wrapper = new JPanel(new BorderLayout()) {
                @Override
                protected void paintComponent(Graphics g) {
                    if (hovered[0]) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        Theme t = ThemeManager.getInstance().getCurrentTheme();
                        g2.setColor(new Color(t.neonAccent.getRed(), t.neonAccent.getGreen(), t.neonAccent.getBlue(), 35));
                        g2.fillRoundRect(2, 2, getWidth() - 4, getHeight() - 4, 6, 6);
                        g2.setColor(t.neonAccent);
                        g2.drawRoundRect(2, 2, getWidth() - 4, getHeight() - 4, 6, 6);
                        g2.dispose();
                    }
                    super.paintComponent(g);
                }
            };
            wrapper.setOpaque(false);
            wrapper.add(item, BorderLayout.CENTER);

            appsListPanel.add(wrapper);
            appsListPanel.add(Box.createVerticalStrut(4));
        }

        appsListPanel.revalidate();
        appsListPanel.repaint();
    }

    public void showAbove(Component invoker) {
        searchField.setText("");
        filterApps();
        Component target = invoker;
        if (target == null || !target.isShowing()) {
            if (os != null && os.getDesktopFrame() != null) {
                target = os.getDesktopFrame().getTaskbar();
            }
        }
        if (target != null && target.isShowing()) {
            int x = 10;
            int y = -MENU_HEIGHT - 6;
            show(target, x, y);
            SwingUtilities.invokeLater(searchField::requestFocusInWindow);
        }
    }
}

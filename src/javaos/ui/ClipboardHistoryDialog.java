package javaos.ui;

import javaos.core.JavaOS;
import javaos.services.ClipboardHistoryService;
import javaos.services.ClipboardHistoryService.ClipboardItem;
import javaos.ui.components.CyberpunkIcons;
import javaos.ui.components.NeonScrollBarUI;
import javaos.ui.components.NeonTextField;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.util.List;

/**
 * Global Clipboard History Dialog (Win+V / Ctrl+Alt+V).
 * Searchable popover showing recent clipboard snippets with 1-click restore/paste.
 */
public class ClipboardHistoryDialog extends JDialog {

    private final JavaOS os;
    private final NeonTextField searchField;
    private final JPanel itemsContainer;
    private final JScrollPane scrollPane;

    public ClipboardHistoryDialog(Frame owner, JavaOS os) {
        super(owner, "Clipboard History", false);
        this.os = os;

        setUndecorated(true);
        setSize(420, 520);
        setLayout(new BorderLayout());
        setShape(new RoundRectangle2D.Float(0, 0, 420, 520, 16, 16));

        Theme theme = ThemeManager.getInstance().getCurrentTheme();

        JPanel root = new JPanel(new BorderLayout(0, 8)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(theme.panel);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.setColor(theme.neonAccent);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
                g2.dispose();
            }
        };
        root.setOpaque(false);
        root.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));

        // Top Header
        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setOpaque(false);

        JLabel titleLbl = new JLabel("Clipboard History (Win+V)");
        titleLbl.setFont(theme.fontBold);
        titleLbl.setForeground(theme.neonAccent);
        titleLbl.setIcon(CyberpunkIcons.createIcon("NOTEPAD", 18));

        JButton btnClose = new JButton("✕");
        btnClose.setFont(theme.fontSmall);
        btnClose.setForeground(theme.textSecondary);
        btnClose.setOpaque(false);
        btnClose.setContentAreaFilled(false);
        btnClose.setBorderPainted(false);
        btnClose.setFocusPainted(false);
        btnClose.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnClose.addActionListener(e -> setVisible(false));

        header.add(titleLbl, BorderLayout.WEST);
        header.add(btnClose, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        // Search + Clear Toolbar
        JPanel searchBar = new JPanel(new BorderLayout(8, 0));
        searchBar.setOpaque(false);

        searchField = new NeonTextField("Search copied snippets...", 20);
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void update() { refreshItems(searchField.getText()); }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { update(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { update(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { update(); }
        });

        JButton btnClear = new JButton("Clear All");
        btnClear.setFont(theme.fontSmall);
        btnClear.setForeground(theme.error);
        btnClear.setBackground(theme.panel);
        btnClear.setOpaque(true);
        btnClear.setFocusPainted(false);
        btnClear.setBorder(BorderFactory.createLineBorder(theme.secondaryAccent));
        btnClear.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnClear.addActionListener(e -> {
            os.getClipboardService().clearHistory();
            refreshItems("");
        });

        searchBar.add(searchField, BorderLayout.CENTER);
        searchBar.add(btnClear, BorderLayout.EAST);

        // Center Content Container
        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setOpaque(false);
        center.add(searchBar, BorderLayout.NORTH);

        itemsContainer = new JPanel();
        itemsContainer.setLayout(new BoxLayout(itemsContainer, BoxLayout.Y_AXIS));
        itemsContainer.setOpaque(false);

        scrollPane = new JScrollPane(itemsContainer);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUI(new NeonScrollBarUI());
        center.add(scrollPane, BorderLayout.CENTER);

        root.add(center, BorderLayout.CENTER);
        setContentPane(root);

        // Close on ESC
        root.registerKeyboardAction(e -> setVisible(false), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        // Auto-close on click outside
        addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowLostFocus(WindowEvent e) {
                setVisible(false);
            }
        });
    }

    public void showDialog() {
        refreshItems("");
        searchField.setText("");

        // Center on screen or anchor near mouse
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        Point mouse = MouseInfo.getPointerInfo().getLocation();
        int x = Math.min(mouse.x - 200, screen.width - getWidth() - 20);
        int y = Math.min(mouse.y - 300, screen.height - getHeight() - 60);
        x = Math.max(20, x);
        y = Math.max(20, y);

        setLocation(x, y);
        setVisible(true);
        searchField.requestFocusInWindow();
    }

    private void refreshItems(String query) {
        itemsContainer.removeAll();
        Theme theme = ThemeManager.getInstance().getCurrentTheme();

        List<ClipboardItem> items = os.getClipboardService().getHistory();
        String q = query.toLowerCase().trim();

        int count = 0;
        for (ClipboardItem item : items) {
            if (!q.isEmpty() && !item.getContent().toLowerCase().contains(q)) {
                continue;
            }

            count++;
            JPanel card = new JPanel(new BorderLayout(6, 4));
            card.setOpaque(true);
            card.setBackground(theme.elevatedPanel);
            card.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(theme.secondaryAccent, 1),
                    BorderFactory.createEmptyBorder(6, 10, 6, 10)
            ));
            card.setMaximumSize(new Dimension(390, 64));
            card.setPreferredSize(new Dimension(390, 64));
            card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            JLabel previewLbl = new JLabel(item.getPreview());
            previewLbl.setFont(theme.fontRegular);
            previewLbl.setForeground(theme.textPrimary);

            JLabel metaLbl = new JLabel(item.getFormattedTime() + "  |  " + item.getCharCount() + " chars  |  " + item.getLineCount() + " line(s)");
            metaLbl.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            metaLbl.setForeground(theme.textSecondary);

            card.add(previewLbl, BorderLayout.CENTER);
            card.add(metaLbl, BorderLayout.SOUTH);

            card.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    card.setBackground(new Color(theme.neonAccent.getRed(), theme.neonAccent.getGreen(), theme.neonAccent.getBlue(), 35));
                    card.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(theme.neonAccent, 1),
                            BorderFactory.createEmptyBorder(6, 10, 6, 10)
                    ));
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    card.setBackground(theme.elevatedPanel);
                    card.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(theme.secondaryAccent, 1),
                            BorderFactory.createEmptyBorder(6, 10, 6, 10)
                    ));
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    os.getClipboardService().copyToSystemClipboard(item.getContent());
                    if (os.getNotificationManager() != null) {
                        os.getNotificationManager().postNotification("Snippet Copied", "Pasted back to system clipboard: " + item.getPreview(), javaos.models.NotificationMessage.Level.INFO);
                    }
                    if (os.getSoundService() != null) {
                        os.getSoundService().playSuccess();
                    }
                    setVisible(false);
                }
            });

            itemsContainer.add(card);
            itemsContainer.add(Box.createVerticalStrut(4));
        }

        if (count == 0) {
            JLabel emptyLbl = new JLabel(q.isEmpty() ? "Clipboard history is empty. Copy text anywhere to start!" : "No snippets match \"" + q + "\"");
            emptyLbl.setFont(theme.fontSmall);
            emptyLbl.setForeground(theme.textSecondary);
            emptyLbl.setBorder(BorderFactory.createEmptyBorder(20, 10, 20, 10));
            emptyLbl.setAlignmentX(Component.CENTER_ALIGNMENT);
            itemsContainer.add(emptyLbl);
        }

        itemsContainer.revalidate();
        itemsContainer.repaint();
    }
}

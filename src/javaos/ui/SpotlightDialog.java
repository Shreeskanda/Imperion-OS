package javaos.ui;

import javaos.apps.AppDescriptor;
import javaos.apps.OSApplication;
import javaos.apps.RealFileManagerApp;
import javaos.core.JavaOS;
import javaos.ui.components.CyberpunkIcons;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

/**
 * Global Spotlight Quick Launcher for IMPERION OS.
 * Activated via Alt+Space or desktop search bar.
 * Features:
 *  - Instant application launching with fuzzy matching
 *  - Shell command runner (> uptime, > whoami, > date, > theme, > mc, > cc, > lock)
 *  - In-line live math calculation (e.g. "50 * 2.5", "1024 / 8", "sqrt(144)")
 *  - Real and virtual filesystem search
 *  - Arrow key navigation & Enter to execute
 */
public class SpotlightDialog extends JDialog {

    private final JavaOS os;
    private final JTextField searchField;
    private final DefaultListModel<SearchResultItem> resultModel;
    private final JList<SearchResultItem> resultList;

    public static class SearchResultItem {
        enum Type { APP, CALC, FILE, COMMAND }
        final Type type;
        final String title;
        final String subtitle;
        final String payload;
        final Icon icon;

        public SearchResultItem(Type type, String title, String subtitle, String payload, Icon icon) {
            this.type = type;
            this.title = title;
            this.subtitle = subtitle;
            this.payload = payload;
            this.icon = icon;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    public SpotlightDialog(JFrame parent, JavaOS os) {
        super(parent, false);
        this.os = os;

        setUndecorated(true);
        setBackground(new Color(0, 0, 0, 0));
        setSize(560, 340);
        setLocationRelativeTo(parent);

        Theme theme = ThemeManager.getInstance().getCurrentTheme();

        JPanel card = new JPanel(new BorderLayout(0, 6)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // Shadow
                g2.setColor(new Color(0, 0, 0, 180));
                g2.fill(new RoundRectangle2D.Float(4, 4, getWidth() - 8, getHeight() - 8, 14, 14));
                // Card background
                g2.setColor(theme.panel);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth() - 8, getHeight() - 8, 14, 14));
                // Neon glow border
                g2.setColor(theme.neonAccent);
                g2.setStroke(new BasicStroke(1.5f));
                g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 9, getHeight() - 9, 14, 14));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(12, 14, 12, 14));

        // Top Search Bar
        JPanel searchBar = new JPanel(new BorderLayout(8, 0));
        searchBar.setOpaque(false);

        JLabel searchIcon = new JLabel(CyberpunkIcons.createIcon("SEARCH", 22));
        searchField = new JTextField();
        searchField.setFont(new Font("Segoe UI", Font.BOLD, 17));
        searchField.setBackground(theme.elevatedPanel);
        searchField.setForeground(theme.textPrimary);
        searchField.setCaretColor(Color.WHITE);
        searchField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(theme.secondaryAccent, 1),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));

        searchBar.add(searchIcon, BorderLayout.WEST);
        searchBar.add(searchField, BorderLayout.CENTER);
        card.add(searchBar, BorderLayout.NORTH);

        // Results List
        resultModel = new DefaultListModel<>();
        resultList = new JList<>(resultModel);
        resultList.setOpaque(false);
        resultList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultList.setFixedCellHeight(44);

        resultList.setCellRenderer(new ListCellRenderer<>() {
            @Override
            public Component getListCellRendererComponent(JList<? extends SearchResultItem> list, SearchResultItem item, int index, boolean isSelected, boolean cellHasFocus) {
                JPanel row = new JPanel(new BorderLayout(10, 0));
                row.setOpaque(true);
                Theme th = ThemeManager.getInstance().getCurrentTheme();

                row.setBackground(isSelected ? th.selectionBackground : th.panel);
                row.setBorder(new EmptyBorder(4, 8, 4, 8));

                JLabel iconLbl = new JLabel(item.icon != null ? item.icon : CyberpunkIcons.createIcon("FILE", 20));
                row.add(iconLbl, BorderLayout.WEST);

                JPanel textCol = new JPanel(new GridLayout(2, 1, 0, 1));
                textCol.setOpaque(false);

                JLabel titleLbl = new JLabel(item.title);
                titleLbl.setFont(th.fontBold);
                titleLbl.setForeground(isSelected ? th.neonAccent : th.textPrimary);

                JLabel subLbl = new JLabel(item.subtitle);
                subLbl.setFont(th.fontSmall);
                subLbl.setForeground(isSelected ? th.textPrimary : th.textSecondary);

                textCol.add(titleLbl);
                textCol.add(subLbl);
                row.add(textCol, BorderLayout.CENTER);

                return row;
            }
        });

        JScrollPane scroll = new JScrollPane(resultList);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        card.add(scroll, BorderLayout.CENTER);

        // Bottom hint
        JLabel hint = new JLabel("↑/↓ to Navigate | Enter to Launch | Esc to Exit");
        hint.setFont(theme.fontSmall);
        hint.setForeground(theme.textSecondary);
        hint.setHorizontalAlignment(SwingConstants.CENTER);
        card.add(hint, BorderLayout.SOUTH);

        setContentPane(card);

        // Event Listeners
        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                    if (!resultModel.isEmpty()) {
                        resultList.setSelectedIndex(0);
                        resultList.requestFocus();
                    }
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    setVisible(false);
                } else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    executeSelected();
                } else {
                    performSearch(searchField.getText().trim());
                }
            }
        });

        resultList.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    executeSelected();
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    setVisible(false);
                } else if (e.getKeyCode() == KeyEvent.VK_UP && resultList.getSelectedIndex() == 0) {
                    searchField.requestFocus();
                }
            }
        });

        resultList.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) executeSelected();
            }
        });

        // Close on blur
        addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowLostFocus(WindowEvent e) {
                setVisible(false);
            }
        });
    }

    public void showSpotlight() {
        searchField.setText("");
        performSearch("");
        setLocationRelativeTo(getParent());
        setVisible(true);
        searchField.requestFocus();
    }

    private void performSearch(String query) {
        resultModel.clear();
        String q = query.toLowerCase();

        // 1. Shell Command Runner (> cmd or $ cmd)
        if (query.startsWith(">") || query.startsWith("$")) {
            String rawCmd = query.substring(1).trim();
            String lowCmd = rawCmd.toLowerCase();
            if (lowCmd.startsWith("theme")) {
                String themeName = rawCmd.length() > 5 ? rawCmd.substring(5).trim() : "";
                resultModel.addElement(new SearchResultItem(
                        SearchResultItem.Type.COMMAND,
                        "Switch Theme: " + (themeName.isEmpty() ? "Cyan, Matrix, Synthwave, Solar, Crimson, OLED" : themeName),
                        "Instant System Matrix Visual Protocol",
                        "theme " + themeName,
                        CyberpunkIcons.createIcon("SETTINGS", 20)
                ));
            } else if (lowCmd.equals("uptime")) {
                String upStr = os.getSystemMetricsService() != null ? os.getSystemMetricsService().getFormattedUptime() : "00:00:00";
                resultModel.addElement(new SearchResultItem(
                        SearchResultItem.Type.COMMAND,
                        "Uptime: " + upStr,
                        "Press Enter to copy to clipboard",
                        "clipboard:" + upStr,
                        CyberpunkIcons.createIcon("SYSTEM", 20)
                ));
            } else if (lowCmd.equals("whoami")) {
                String user = System.getProperty("user.name") + " [Imperion Host Operator]";
                resultModel.addElement(new SearchResultItem(
                        SearchResultItem.Type.COMMAND,
                        "Operator: " + user,
                        "Press Enter to copy identity",
                        "clipboard:" + user,
                        CyberpunkIcons.createIcon("TERMINAL", 20)
                ));
            } else if (lowCmd.equals("sysinfo")) {
                double cpu = os.getSystemMetricsService() != null ? os.getSystemMetricsService().getHostCpuLoad() : 0.0;
                long usedMem = os.getSystemMetricsService() != null ? (os.getSystemMetricsService().getJvmUsedMemory() / (1024 * 1024)) : 0;
                long maxMem = os.getSystemMetricsService() != null ? (os.getSystemMetricsService().getJvmMaxMemory() / (1024 * 1024)) : 0;
                String info = String.format("CPU: %.1f%% | RAM: %dMB / %dMB", (cpu >= 0 ? cpu : 0.0), usedMem, maxMem);
                resultModel.addElement(new SearchResultItem(
                        SearchResultItem.Type.COMMAND,
                        info,
                        "System Diagnostic Snapshot (Press Enter to copy)",
                        "clipboard:" + info,
                        CyberpunkIcons.createIcon("SYSTEM", 20)
                ));
            } else if (lowCmd.equals("date") || lowCmd.equals("time")) {
                String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy HH:mm:ss"));
                resultModel.addElement(new SearchResultItem(
                        SearchResultItem.Type.COMMAND,
                        now,
                        "Chrono Matrix Timestamp (Press Enter to copy)",
                        "clipboard:" + now,
                        CyberpunkIcons.createIcon("CALCULATOR", 20)
                ));
            } else if (lowCmd.equals("lock")) {
                resultModel.addElement(new SearchResultItem(
                        SearchResultItem.Type.COMMAND,
                        "Lock Workstation",
                        "Engage biometric security lockout screen",
                        "action:lock",
                        CyberpunkIcons.createIcon("SHIELD", 20)
                ));
            } else if (lowCmd.equals("mc") || lowCmd.equals("expose")) {
                resultModel.addElement(new SearchResultItem(
                        SearchResultItem.Type.COMMAND,
                        "Mission Control / Window Exposé",
                        "View all workspace windows and grid overlay",
                        "action:mc",
                        CyberpunkIcons.createIcon("MAXIMIZE", 20)
                ));
            } else if (lowCmd.equals("cc") || lowCmd.equals("control")) {
                resultModel.addElement(new SearchResultItem(
                        SearchResultItem.Type.COMMAND,
                        "Cyberpunk Control Center",
                        "Master audio slider, power profiles & theme picker",
                        "action:cc",
                        CyberpunkIcons.createIcon("SETTINGS", 20)
                ));
            } else {
                resultModel.addElement(new SearchResultItem(
                        SearchResultItem.Type.COMMAND,
                        "> " + rawCmd,
                        "Execute in sandboxed terminal console",
                        "term:" + rawCmd,
                        CyberpunkIcons.createIcon("TERMINAL", 20)
                ));
            }
        }

        // 2. Math Evaluation Check
        if (query.matches(".*[0-9]+.*[+\\-*/^].*") || query.toLowerCase().startsWith("sqrt(") || query.toLowerCase().startsWith("sin(") || query.toLowerCase().startsWith("cos(")) {
            try {
                double res = evaluateMath(query);
                resultModel.addElement(new SearchResultItem(
                        SearchResultItem.Type.CALC,
                        "= " + (res == (long) res ? String.format("%d", (long) res) : String.format("%.4f", res)),
                        "Live Math Evaluation (Enter to copy to clipboard)",
                        String.valueOf(res),
                        CyberpunkIcons.createIcon("CALCULATOR", 20)
                ));
            } catch (Exception ignored) {}
        }

        // 3. Application Matches
        if (!query.startsWith(">") && !query.startsWith("$")) {
            List<AppDescriptor> apps = os.getApplicationManager().getRegisteredApps();
            for (AppDescriptor app : apps) {
                if (q.isEmpty() || app.getName().toLowerCase().contains(q) || app.getId().toLowerCase().contains(q)) {
                    resultModel.addElement(new SearchResultItem(
                            SearchResultItem.Type.APP,
                            app.getName(),
                            app.getDescription() != null ? app.getDescription() : "Application",
                            app.getId(),
                            app.getIcon()
                    ));
                }
            }

            // 4. File Search across real and virtual filesystem
            if (q.length() >= 2) {
                List<File> searchDirs = new ArrayList<>();
                searchDirs.add(new File("data/home"));
                searchDirs.add(new File(System.getProperty("user.home"), "Desktop"));
                searchDirs.add(new File(System.getProperty("user.home"), "Documents"));
                searchDirs.add(new File(System.getProperty("user.home"), "Downloads"));

                int matches = 0;
                for (File dir : searchDirs) {
                    if (matches >= 6) break;
                    if (dir.exists() && dir.isDirectory()) {
                        File[] files = dir.listFiles();
                        if (files != null) {
                            for (File f : files) {
                                if (f.getName().toLowerCase().contains(q)) {
                                    resultModel.addElement(new SearchResultItem(
                                            SearchResultItem.Type.FILE,
                                            f.getName(),
                                            f.getAbsolutePath(),
                                            f.getAbsolutePath(),
                                            f.isDirectory() ? CyberpunkIcons.createIcon("FOLDER", 20) : CyberpunkIcons.createIcon("REALFILES", 20)
                                    ));
                                    matches++;
                                    if (matches >= 6) break;
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Select first item by default
        if (!resultModel.isEmpty()) {
            resultList.setSelectedIndex(0);
        }
    }

    private void executeSelected() {
        SearchResultItem item = resultList.getSelectedValue();
        if (item == null && !resultModel.isEmpty()) {
            item = resultModel.get(0);
        }
        if (item == null) return;

        setVisible(false);
        if (item.type == SearchResultItem.Type.APP) {
            os.getApplicationManager().launchApp(item.payload);
        } else if (item.type == SearchResultItem.Type.CALC) {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(item.payload), null);
            if (os.getSoundService() != null) os.getSoundService().playKeyClick();
            if (os.getNotificationManager() != null) {
                os.getNotificationManager().postNotification("Spotlight", "Copied " + item.payload + " to clipboard", javaos.models.NotificationMessage.Level.INFO);
            }
        } else if (item.type == SearchResultItem.Type.COMMAND) {
            executeCommandPayload(item.payload);
        } else if (item.type == SearchResultItem.Type.FILE) {
            File f = new File(item.payload);
            if (f.exists()) {
                OSApplication app = os.getApplicationManager().launchApp("realfiles");
                if (app instanceof RealFileManagerApp rfm) {
                    rfm.navigate(f.isDirectory() ? f : f.getParentFile());
                }
            }
        }
    }

    private void executeCommandPayload(String payload) {
        if (payload.startsWith("clipboard:")) {
            String val = payload.substring("clipboard:".length());
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(val), null);
            if (os.getSoundService() != null) os.getSoundService().playKeyClick();
            if (os.getNotificationManager() != null) {
                os.getNotificationManager().postNotification("Spotlight", "Copied: " + val, javaos.models.NotificationMessage.Level.INFO);
            }
        } else if (payload.equals("action:lock")) {
            if (os.getDesktopFrame() != null) os.getDesktopFrame().lock();
        } else if (payload.equals("action:mc")) {
            if (os.getDesktopFrame() != null) os.getDesktopFrame().showMissionControl();
        } else if (payload.equals("action:cc")) {
            if (os.getDesktopFrame() != null) os.getDesktopFrame().showControlCenter();
        } else if (payload.startsWith("theme ")) {
            String th = payload.substring(6).trim();
            if (!th.isEmpty()) {
                ThemeManager.getInstance().applyTheme(th);
            }
        } else if (payload.startsWith("term:")) {
            os.getApplicationManager().launchApp("terminal");
        }
    }

    private double evaluateMath(String expr) {
        expr = expr.replaceAll("\\s+", "").toLowerCase();
        if (expr.startsWith("sqrt(") && expr.endsWith(")")) {
            return Math.sqrt(evaluateMath(expr.substring(5, expr.length() - 1)));
        }
        if (expr.startsWith("sin(") && expr.endsWith(")")) {
            return Math.sin(Math.toRadians(evaluateMath(expr.substring(4, expr.length() - 1))));
        }
        if (expr.startsWith("cos(") && expr.endsWith(")")) {
            return Math.cos(Math.toRadians(evaluateMath(expr.substring(4, expr.length() - 1))));
        }
        if (expr.contains("+")) {
            int idx = expr.lastIndexOf('+');
            if (idx > 0) return evaluateMath(expr.substring(0, idx)) + evaluateMath(expr.substring(idx + 1));
        }
        if (expr.contains("-")) {
            int idx = expr.lastIndexOf('-');
            if (idx > 0) return evaluateMath(expr.substring(0, idx)) - evaluateMath(expr.substring(idx + 1));
        }
        if (expr.contains("*")) {
            int idx = expr.lastIndexOf('*');
            if (idx > 0) return evaluateMath(expr.substring(0, idx)) * evaluateMath(expr.substring(idx + 1));
        }
        if (expr.contains("/")) {
            int idx = expr.lastIndexOf('/');
            if (idx > 0) return evaluateMath(expr.substring(0, idx)) / evaluateMath(expr.substring(idx + 1));
        }
        if (expr.contains("^")) {
            int idx = expr.indexOf('^');
            if (idx > 0) return Math.pow(evaluateMath(expr.substring(0, idx)), evaluateMath(expr.substring(idx + 1)));
        }
        return Double.parseDouble(expr);
    }
}

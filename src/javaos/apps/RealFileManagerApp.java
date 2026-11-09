package javaos.apps;

import javaos.ui.Theme;
import javaos.ui.ThemeManager;
import javaos.ui.components.CyberpunkIcons;
import javaos.ui.components.NeonButton;
import javaos.ui.components.NeonScrollBarUI;
import javaos.ui.components.NeonTextField;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;

/**
 * Real filesystem explorer for JavaOS.
 * Browses the actual Windows/Host filesystem with rich file-type icons,
 * side navigation panel, fast async directory loading, and context menus.
 */
public class RealFileManagerApp extends OSApplication {

    public static class ExplorerTab {
        public String id;
        public String title;
        public File currentDirectory;
        public final Stack<File> backStack = new Stack<>();
        public final Stack<File> forwardStack = new Stack<>();
    }

    private final List<ExplorerTab> tabs = new ArrayList<>();
    private ExplorerTab activeTab = null;
    private JPanel tabStrip;
    private int tabCounter = 0;

    private File currentDirectory;
    private JTextField addressBar;
    private NeonTextField searchField;
    private JTable fileTable;
    private DefaultTableModel tableModel;
    private JLabel statusLabel;
    private JPanel sidebarPanel;
    private List<File> currentFiles = new ArrayList<>();

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DecimalFormat SIZE_FMT = new DecimalFormat("#,##0");

    public RealFileManagerApp() {
        super(new AppDescriptor(
                "realfiles",
                "Real File Explorer",
                "Browse actual system files and drives",
                AppDescriptor.Category.UTILITIES,
                CyberpunkIcons.createIcon("REALFILES", 20),
                false
        ));
        currentDirectory = new File(System.getProperty("user.home"));
    }

    @Override
    public Dimension getPreferredWindowSize() {
        return new Dimension(1000, 600);
    }

    @Override
    public JComponent createContent() {
        JPanel main = new JPanel(new BorderLayout(0, 0));
        main.setOpaque(false);

        Theme theme = ThemeManager.getInstance().getCurrentTheme();

        // ── TOP NAVIGATION BAR ────────────────────────────────────────────────
        JPanel navBar = new JPanel(new BorderLayout(6, 0));
        navBar.setOpaque(true);
        navBar.setBackground(theme.elevatedPanel);
        navBar.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        // Left nav buttons
        JPanel navBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        navBtns.setOpaque(false);

        JButton btnBack = makeNavBtn("MINIMIZE", "Back");
        btnBack.addActionListener(e -> goBack());

        JButton btnForward = makeNavBtn("MAXIMIZE", "Forward");
        btnForward.addActionListener(e -> goForward());

        JButton btnUp = makeNavBtn("UP", "Parent Directory");
        btnUp.addActionListener(e -> goUp());

        JButton btnRefresh = makeNavBtn("REFRESH", "Refresh");
        btnRefresh.addActionListener(e -> refresh());

        navBtns.add(btnBack);
        navBtns.add(btnForward);
        navBtns.add(btnUp);
        navBtns.add(btnRefresh);
        navBar.add(navBtns, BorderLayout.WEST);

        // Address bar
        addressBar = new JTextField();
        addressBar.setBackground(theme.background);
        addressBar.setForeground(theme.neonAccent);
        addressBar.setFont(theme.fontMonospace);
        addressBar.setCaretColor(Color.WHITE);
        addressBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(theme.secondaryAccent),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        addressBar.addActionListener(e -> navigateToPath(addressBar.getText().trim()));
        navBar.add(addressBar, BorderLayout.CENTER);

        // Search
        searchField = new NeonTextField(14);
        searchField.setPlaceholder("Search here...");
        searchField.addActionListener(e -> performSearch());
        navBar.add(searchField, BorderLayout.EAST);

        // ── TOP CYBER TAB STRIP ──────────────────────────────────────────────
        tabStrip = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 3));
        tabStrip.setOpaque(true);
        tabStrip.setBackground(theme.background);
        tabStrip.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, theme.secondaryAccent));

        JPanel topContainer = new JPanel(new BorderLayout(0, 0));
        topContainer.setOpaque(false);
        topContainer.add(tabStrip, BorderLayout.NORTH);
        topContainer.add(navBar, BorderLayout.SOUTH);
        main.add(topContainer, BorderLayout.NORTH);

        // Shortcuts: Ctrl+T for new tab, Ctrl+W to close active tab
        main.registerKeyboardAction(e -> createNewTab(currentDirectory),
                KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_T, java.awt.event.InputEvent.CTRL_DOWN_MASK),
                JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        main.registerKeyboardAction(e -> { if (activeTab != null) closeTab(activeTab); },
                KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_W, java.awt.event.InputEvent.CTRL_DOWN_MASK),
                JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

        if (tabs.isEmpty()) {
            createNewTab(currentDirectory != null ? currentDirectory : new File(System.getProperty("user.home")));
        } else {
            renderTabStrip();
        }

        // ── MAIN SPLIT: SIDEBAR + FILE LIST ───────────────────────────────────
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(180);
        splitPane.setDividerSize(4);
        splitPane.setBorder(BorderFactory.createEmptyBorder());
        splitPane.setOpaque(false);

        // SIDEBAR
        sidebarPanel = new JPanel();
        sidebarPanel.setLayout(new BoxLayout(sidebarPanel, BoxLayout.Y_AXIS));
        sidebarPanel.setBackground(theme.panel);
        sidebarPanel.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0));

        buildSidebar();

        JScrollPane sideScroll = new JScrollPane(sidebarPanel);
        sideScroll.getVerticalScrollBar().setUI(new NeonScrollBarUI());
        sideScroll.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, theme.secondaryAccent));
        sideScroll.setBackground(theme.panel);
        splitPane.setLeftComponent(sideScroll);

        // FILE TABLE — icon rendered inside Name column (col 0)
        String[] cols = {"Name", "Type", "Size", "Modified"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        fileTable = new JTable(tableModel);
        fileTable.setBackground(theme.panel);
        fileTable.setForeground(theme.textPrimary);
        fileTable.setSelectionBackground(theme.selectionBackground);
        fileTable.setSelectionForeground(theme.neonAccent);
        fileTable.setGridColor(new Color(theme.secondaryAccent.getRed(), theme.secondaryAccent.getGreen(), theme.secondaryAccent.getBlue(), 40));
        fileTable.setRowHeight(30);
        fileTable.setShowVerticalLines(false);
        fileTable.setIntercellSpacing(new Dimension(4, 0));
        fileTable.setFillsViewportHeight(true);

        // Column widths — Name col is wide, others fixed
        fileTable.getColumnModel().getColumn(0).setPreferredWidth(280);
        fileTable.getColumnModel().getColumn(1).setPreferredWidth(110);
        fileTable.getColumnModel().getColumn(2).setPreferredWidth(90);
        fileTable.getColumnModel().getColumn(3).setPreferredWidth(140);

        // Header styling
        fileTable.getTableHeader().setBackground(theme.elevatedPanel);
        fileTable.getTableHeader().setForeground(theme.neonAccent);
        fileTable.getTableHeader().setFont(theme.fontBold);

        // Unified cell renderer — icon + filename in col 0, plain text in other cols
        fileTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val, boolean isSel, boolean hasFocus, int row, int col) {
                super.getTableCellRendererComponent(t, val, isSel, hasFocus, row, col);
                Theme th = ThemeManager.getInstance().getCurrentTheme();
                setFont(th.fontRegular);
                setHorizontalAlignment(SwingConstants.LEFT);
                setIconTextGap(9);

                boolean hidden = false;
                if (row < currentFiles.size()) {
                    File f = currentFiles.get(row);
                    hidden = f.isHidden() || f.getName().startsWith(".");
                }

                if (isSel) {
                    setBackground(th.selectionBackground);
                    setForeground(th.neonAccent);
                } else {
                    setBackground(row % 2 == 0 ? th.panel : th.elevatedPanel);
                    setForeground(hidden
                            ? new Color(th.textSecondary.getRed(), th.textSecondary.getGreen(), th.textSecondary.getBlue(), 140)
                            : th.textPrimary);
                }

                if (col == 0 && row < currentFiles.size()) {
                    // Name column: icon + filename
                    setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 4));
                    setIcon(getIconForFile(currentFiles.get(row)));
                } else {
                    // Data columns: text only, small left pad
                    setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 4));
                    setIcon(null);
                }
                return this;
            }
        });

        // Double-click to open
        fileTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) openSelected();
            }
        });

        // Right-click context menu
        fileTable.setComponentPopupMenu(buildContextMenu());

        JScrollPane tableScroll = new JScrollPane(fileTable);
        tableScroll.getVerticalScrollBar().setUI(new NeonScrollBarUI());
        tableScroll.getHorizontalScrollBar().setUI(new NeonScrollBarUI());
        tableScroll.setBackground(theme.panel);
        tableScroll.getViewport().setBackground(theme.panel);
        tableScroll.setBorder(BorderFactory.createEmptyBorder());
        splitPane.setRightComponent(tableScroll);

        main.add(splitPane, BorderLayout.CENTER);

        // ── STATUS BAR ────────────────────────────────────────────────────────
        statusLabel = new JLabel("Ready");
        statusLabel.setFont(theme.fontSmall);
        statusLabel.setForeground(theme.textSecondary);
        statusLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, theme.secondaryAccent),
                BorderFactory.createEmptyBorder(3, 8, 3, 8)));
        statusLabel.setOpaque(true);
        statusLabel.setBackground(theme.panel);
        main.add(statusLabel, BorderLayout.SOUTH);

        return main;
    }

    private void buildSidebar() {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();

        // Section: Drives
        addSidebarSection("DRIVES");
        for (File drive : File.listRoots()) {
            long total = drive.getTotalSpace();
            long free = drive.getFreeSpace();
            String label = drive.getAbsolutePath().replace("\\", "");
            String sizeStr = total > 0 ? formatSize(total - free) + " / " + formatSize(total) : "Unknown";
            addSidebarItem(label + "  [" + sizeStr + "]", "DRIVE", drive);
        }

        // Section: Quick Links
        addSidebarSection("QUICK ACCESS");
        String home = System.getProperty("user.home");
        addSidebarItem("Home", "HOME", new File(home));
        addSidebarItem("Desktop", "REALFILES", new File(home, "Desktop"));
        addSidebarItem("Documents", "NOTEPAD", new File(home, "Documents"));
        addSidebarItem("Downloads", "ARCHIVE", new File(home, "Downloads"));
        addSidebarItem("Pictures", "IMAGE", new File(home, "Pictures"));
        addSidebarItem("Music", "AUDIO", new File(home, "Music"));
        addSidebarItem("Videos", "VIDEO", new File(home, "Videos"));

        // IMPERION OS Virtual Home
        addSidebarSection("IMPERION OS");
        addSidebarItem("Virtual Home", "FILES", new File("data/home").getAbsoluteFile());
        addSidebarItem("Virtual Trash", "TRASH", new File("data/trash").getAbsoluteFile());
    }

    private void addSidebarSection(String title) {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        JLabel lbl = new JLabel("  " + title);
        lbl.setFont(theme.fontSmall);
        lbl.setForeground(theme.neonAccent);
        lbl.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        lbl.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(theme.neonAccent.getRed(), theme.neonAccent.getGreen(), theme.neonAccent.getBlue(), 60)));
        sidebarPanel.add(lbl);
        sidebarPanel.add(Box.createVerticalStrut(4));
    }

    private void addSidebarItem(String name, String iconType, File dir) {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        JButton btn = new JButton(name, CyberpunkIcons.createIcon(iconType, 14));
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.setBackground(theme.panel);
        btn.setForeground(theme.textPrimary);
        btn.setFont(theme.fontSmall);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setOpaque(true);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

        btn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                btn.setBackground(ThemeManager.getInstance().getCurrentTheme().elevatedPanel);
            }
            @Override public void mouseExited(MouseEvent e) {
                btn.setBackground(ThemeManager.getInstance().getCurrentTheme().panel);
            }
        });

        btn.addActionListener(e -> {
            if (dir.exists() && dir.isDirectory()) {
                navigate(dir);
            } else {
                JOptionPane.showMessageDialog(fileTable, "Directory not found:\n" + dir.getAbsolutePath(), "Not Found", JOptionPane.WARNING_MESSAGE);
            }
        });

        sidebarPanel.add(btn);
        sidebarPanel.add(Box.createVerticalStrut(2));
    }

    private JButton makeNavBtn(String iconType, String tooltip) {
        NeonButton btn = new NeonButton("", CyberpunkIcons.createIcon(iconType, 16));
        btn.setPreferredSize(new Dimension(32, 30));
        btn.setToolTipText(tooltip);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private JPopupMenu buildContextMenu() {
        JPopupMenu menu = new JPopupMenu();
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        menu.setBackground(theme.elevatedPanel);

        JMenuItem open = new JMenuItem("Open in IMPERION OS");
        open.addActionListener(e -> openSelected());
        menu.add(open);

        File sel = getSelectedFile();
        if (sel != null && !sel.isDirectory()) {
            String ln = sel.getName().toLowerCase();
            if (ln.endsWith(".docx") || ln.endsWith(".doc") || ln.endsWith(".txt")) {
                JMenuItem openWord = new JMenuItem("Edit in Word Processor");
                openWord.addActionListener(e -> openInWordEditor(sel));
                menu.add(openWord);
            }
            if (ln.endsWith(".pptx") || ln.endsWith(".ppt")) {
                JMenuItem openDeck = new JMenuItem("Edit in Presentation Slides");
                openDeck.addActionListener(e -> openInPresentationEditor(sel));
                menu.add(openDeck);
            }
        }

        JMenuItem openNotepad = new JMenuItem("Open in Notepad");
        openNotepad.addActionListener(e -> openInNotepad());
        menu.add(openNotepad);

        JMenuItem openHost = new JMenuItem("Open with Windows Host...");
        openHost.addActionListener(e -> openWithWindowsHostPrompt());
        menu.add(openHost);

        menu.addSeparator();

        JMenuItem copyPath = new JMenuItem("Copy Full Path");
        copyPath.addActionListener(e -> copySelectedPath());
        menu.add(copyPath);

        JMenuItem properties = new JMenuItem("Properties");
        properties.addActionListener(e -> showProperties());
        menu.add(properties);

        menu.addSeparator();

        JMenuItem delete = new JMenuItem("Delete");
        delete.setForeground(theme.error);
        delete.addActionListener(e -> deleteSelected());
        menu.add(delete);

        return menu;
    }

    @Override
    public void onOpen() {
        if (tabs.isEmpty()) {
            createNewTab(currentDirectory != null ? currentDirectory : new File(System.getProperty("user.home")));
        } else {
            switchToTab(activeTab != null ? activeTab : tabs.get(0));
        }
    }

    public void createNewTab(File dir) {
        ExplorerTab tab = new ExplorerTab();
        tab.id = "tab_" + (++tabCounter);
        tab.currentDirectory = (dir != null && dir.exists()) ? dir : new File(System.getProperty("user.home"));
        String name = tab.currentDirectory.getName();
        tab.title = (name == null || name.isEmpty()) ? tab.currentDirectory.getAbsolutePath() : name;
        tabs.add(tab);
        switchToTab(tab);
    }

    public void switchToTab(ExplorerTab tab) {
        if (tab == null) return;
        activeTab = tab;
        currentDirectory = tab.currentDirectory;
        if (addressBar != null) {
            addressBar.setText(currentDirectory != null ? currentDirectory.getAbsolutePath() : "");
        }
        renderTabStrip();
        if (currentDirectory != null) {
            loadDirectory(currentDirectory);
        }
    }

    public void closeTab(ExplorerTab tab) {
        if (tabs.size() <= 1) return; // Keep at least one tab
        int idx = tabs.indexOf(tab);
        tabs.remove(tab);
        if (activeTab == tab) {
            int nextIdx = Math.max(0, idx - 1);
            switchToTab(tabs.get(nextIdx));
        } else {
            renderTabStrip();
        }
    }

    private void renderTabStrip() {
        if (tabStrip == null) return;
        tabStrip.removeAll();
        Theme theme = ThemeManager.getInstance().getCurrentTheme();

        for (ExplorerTab tab : tabs) {
            boolean isActive = (tab == activeTab);
            JPanel tabBtn = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
            tabBtn.setOpaque(true);
            tabBtn.setBackground(isActive ? theme.panel : theme.elevatedPanel);
            tabBtn.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(isActive ? theme.neonAccent : theme.secondaryAccent, 1),
                    BorderFactory.createEmptyBorder(2, 8, 2, 6)
            ));
            tabBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            JLabel titleLbl = new JLabel(tab.title, CyberpunkIcons.createIcon("FOLDER", 14), SwingConstants.LEFT);
            titleLbl.setFont(theme.fontSmall);
            titleLbl.setForeground(isActive ? theme.neonAccent : theme.textPrimary);
            tabBtn.add(titleLbl);

            if (tabs.size() > 1) {
                JLabel closeLbl = new JLabel("✕");
                closeLbl.setFont(new Font("Segoe UI", Font.BOLD, 10));
                closeLbl.setForeground(theme.textSecondary);
                closeLbl.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                closeLbl.addMouseListener(new MouseAdapter() {
                    @Override public void mouseClicked(MouseEvent e) {
                        e.consume();
                        closeTab(tab);
                    }
                    @Override public void mouseEntered(MouseEvent e) {
                        closeLbl.setForeground(theme.error);
                    }
                    @Override public void mouseExited(MouseEvent e) {
                        closeLbl.setForeground(theme.textSecondary);
                    }
                });
                tabBtn.add(closeLbl);
            }

            tabBtn.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    switchToTab(tab);
                }
            });

            tabStrip.add(tabBtn);
        }

        // New Tab [+] Button
        JButton btnAdd = new JButton("+");
        btnAdd.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnAdd.setPreferredSize(new Dimension(28, 22));
        btnAdd.setBackground(theme.elevatedPanel);
        btnAdd.setForeground(theme.neonAccent);
        btnAdd.setBorder(BorderFactory.createLineBorder(theme.secondaryAccent));
        btnAdd.setFocusPainted(false);
        btnAdd.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnAdd.setToolTipText("New Tab (Ctrl+T)");
        btnAdd.addActionListener(e -> createNewTab(currentDirectory));
        tabStrip.add(btnAdd);

        tabStrip.revalidate();
        tabStrip.repaint();
    }

    public void navigate(File dir) {
        if (dir == null || !dir.exists()) return;

        if (activeTab != null) {
            if (activeTab.currentDirectory != null && !activeTab.currentDirectory.equals(dir)) {
                activeTab.backStack.push(activeTab.currentDirectory);
                activeTab.forwardStack.clear();
            }
            activeTab.currentDirectory = dir;
            String name = dir.getName();
            activeTab.title = (name == null || name.isEmpty()) ? dir.getAbsolutePath() : name;
        }

        currentDirectory = dir;
        if (addressBar != null) {
            addressBar.setText(dir.getAbsolutePath());
        }
        renderTabStrip();
        loadDirectory(dir);
    }

    private void loadDirectory(File dir) {
        tableModel.setRowCount(0);
        currentFiles.clear();
        statusLabel.setText("Loading...");

        // Use SwingWorker to avoid EDT freeze on large directories
        new SwingWorker<List<File>, Void>() {
            @Override
            protected List<File> doInBackground() {
                List<File> files = new ArrayList<>();
                try {
                    File[] list = dir.listFiles();
                    if (list != null) {
                        Arrays.sort(list, (a, b) -> {
                            if (a.isDirectory() && !b.isDirectory()) return -1;
                            if (!a.isDirectory() && b.isDirectory()) return 1;
                            return a.getName().compareToIgnoreCase(b.getName());
                        });
                        files.addAll(Arrays.asList(list));
                    }
                } catch (SecurityException se) {
                    SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(fileTable, "Access denied: " + dir.getAbsolutePath(), "Permission Denied", JOptionPane.ERROR_MESSAGE));
                }
                return files;
            }

            @Override
            protected void done() {
                try {
                    List<File> files = get();
                    currentFiles.addAll(files);
                    int dirs = 0, fileCount = 0;

                    for (File f : files) {
                        String type = f.isDirectory() ? "Folder" : getFileType(f.getName());
                        String size = f.isDirectory() ? "<DIR>" : formatSize(f.length());
                        String modified = LocalDateTime.ofInstant(
                                Instant.ofEpochMilli(f.lastModified()), ZoneId.systemDefault())
                                .format(DATE_FMT);

                        tableModel.addRow(new Object[]{f.getName(), type, size, modified});
                        if (f.isDirectory()) dirs++; else fileCount++;
                    }

                    // Drive free space
                    File root = dir;
                    while (root.getParentFile() != null) root = root.getParentFile();
                    long free = root.getFreeSpace();
                    String freeStr = free > 0 ? "  Free: " + formatSize(free) : "";

                    statusLabel.setText(files.size() + " items (" + dirs + " folders, " + fileCount + " files)" + freeStr);
                } catch (Exception e) {
                    statusLabel.setText("Error loading: " + e.getMessage());
                }
            }
        }.execute();
    }

    private void navigateToPath(String path) {
        if (path.isEmpty()) return;
        File target = new File(path);
        if (target.exists() && target.isDirectory()) {
            navigate(target);
        } else if (target.exists()) {
            openFile(target);
        } else {
            JOptionPane.showMessageDialog(fileTable, "Path does not exist:\n" + path, "Not Found", JOptionPane.ERROR_MESSAGE);
            addressBar.setText(currentDirectory != null ? currentDirectory.getAbsolutePath() : "");
        }
    }

    private void goBack() {
        if (activeTab != null && !activeTab.backStack.isEmpty()) {
            activeTab.forwardStack.push(activeTab.currentDirectory);
            File dir = activeTab.backStack.pop();
            activeTab.currentDirectory = null; // prevent double-push
            navigate(dir);
        }
    }

    private void goForward() {
        if (activeTab != null && !activeTab.forwardStack.isEmpty()) {
            navigate(activeTab.forwardStack.pop());
        }
    }

    private void goUp() {
        if (currentDirectory != null && currentDirectory.getParentFile() != null) {
            navigate(currentDirectory.getParentFile());
        }
    }

    private void refresh() {
        if (currentDirectory != null) {
            loadDirectory(currentDirectory);
        }
    }

    private File getSelectedFile() {
        int row = fileTable.getSelectedRow();
        if (row < 0 || row >= currentFiles.size()) return null;
        return currentFiles.get(row);
    }

    private void openSelected() {
        File f = getSelectedFile();
        if (f == null) return;
        if (f.isDirectory()) {
            navigate(f);
        } else {
            openFile(f);
        }
    }

    private void openFile(File f) {
        if (f == null || !f.exists()) return;
        String name = f.getName().toLowerCase();

        // 1. Image Files -> Internal Cyber Image Viewer (PNG, JPG, BMP, GIF, etc.)
        if (isImageFile(name)) {
            openInImageViewer(f);
            return;
        }

        // 2. Word Documents (.docx, .doc) -> Internal CyberWord Processor
        if (name.endsWith(".docx") || name.endsWith(".doc")) {
            openInWordEditor(f);
            return;
        }

        // 2b. Spreadsheets (.xlsx, .xls, .csv) -> Internal CyberSheet Analytics
        if (name.endsWith(".xlsx") || name.endsWith(".xls") || name.endsWith(".csv")) {
            openInSpreadsheet(f);
            return;
        }

        // 2c. Audio Media Files (.wav, .mp3, .mid) -> Internal NeonWave Player
        if (name.endsWith(".wav") || name.endsWith(".mp3") || name.endsWith(".mid") || name.endsWith(".midi")) {
            openInMediaPlayer(f);
            return;
        }

        // 3. PowerPoint Presentations (.pptx, .ppt) -> Internal CyberDeck Slide Editor & Slideshow
        if (name.endsWith(".pptx") || name.endsWith(".ppt")) {
            openInPresentationEditor(f);
            return;
        }

        // 4. PDF Documents -> Internal Cyber Document & Slide Viewer
        if (name.endsWith(".pdf")) {
            openInDocumentViewer(f);
            return;
        }

        // 5. Text, Source Code, Logs, Scripts, Data -> Internal Notepad
        if (isTextFile(name)) {
            openInNotepadFile(f);
            return;
        }

        // 6. Web & HTML -> NetRunner Web Browser
        if (name.endsWith(".html") || name.endsWith(".htm") || name.endsWith(".url")) {
            openInWebBrowser(f);
            return;
        }

        // 7. Zip / Jar Archives -> Cyber Archive Inspector
        if (name.endsWith(".zip") || name.endsWith(".jar")) {
            showArchiveInspector(f);
            return;
        }

        // 8. Unknown file types -> Interactive Cyber Dispatcher Dialog (staying inside IMPERION OS!)
        showUnknownFileDialog(f);
    }

    private static boolean isImageFile(String name) {
        String n = name.toLowerCase();
        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg")
                || n.endsWith(".gif") || n.endsWith(".bmp") || n.endsWith(".webp")
                || n.endsWith(".ico") || n.endsWith(".wbmp");
    }

    private static boolean isDocumentFile(String name) {
        String n = name.toLowerCase();
        return n.endsWith(".pdf") || n.endsWith(".pptx") || n.endsWith(".ppt")
                || n.endsWith(".docx") || n.endsWith(".doc");
    }

    private void openInWordEditor(File f) {
        if (getOS() == null) return;
        OSApplication app = getOS().getApplicationManager().launchApp("wordeditor");
        if (app instanceof WordEditorApp) {
            ((WordEditorApp) app).openFile(f);
        }
    }

    private void openInSpreadsheet(File f) {
        if (getOS() == null) return;
        OSApplication app = getOS().getApplicationManager().launchApp("sheet");
        if (app instanceof SpreadsheetApp) {
            ((SpreadsheetApp) app).openFile(f);
        }
    }

    private void openInMediaPlayer(File f) {
        if (getOS() == null) return;
        getOS().getApplicationManager().launchApp("player");
    }

    private void openInPresentationEditor(File f) {
        if (getOS() == null) return;
        OSApplication app = getOS().getApplicationManager().launchApp("slides");
        if (app instanceof PresentationEditorApp) {
            ((PresentationEditorApp) app).openFile(f);
        }
    }

    private void openInImageViewer(File f) {
        if (getOS() == null) return;
        OSApplication app = getOS().getApplicationManager().launchApp("imageviewer");
        if (app instanceof ImageViewerApp) {
            ((ImageViewerApp) app).openFile(f);
        }
    }

    private void openInDocumentViewer(File f) {
        if (getOS() == null) return;
        OSApplication app = getOS().getApplicationManager().launchApp("docviewer");
        if (app instanceof DocumentViewerApp) {
            ((DocumentViewerApp) app).openFile(f);
        }
    }

    private void openInWebBrowser(File f) {
        if (getOS() == null) return;
        OSApplication app = getOS().getApplicationManager().launchApp("browser");
        if (app instanceof WebBrowserApp) {
            ((WebBrowserApp) app).openFile(f);
        }
    }

    private void openInNotepad() {
        File f = getSelectedFile();
        if (f == null || f.isDirectory()) return;
        openInNotepadFile(f);
    }

    private void openInNotepadFile(File f) {
        if (getOS() == null) return;
        OSApplication app = getOS().getApplicationManager().launchApp("notepad");
        if (app instanceof NotepadApp) {
            ((NotepadApp) app).openRealFile(f);
        }
    }

    private void showArchiveInspector(File f) {
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(f)) {
            StringBuilder sb = new StringBuilder();
            sb.append("=== ARCHIVE CONTENTS: ").append(f.getName()).append(" ===\n\n");
            java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zip.entries();
            int count = 0;
            while (entries.hasMoreElements() && count < 100) {
                java.util.zip.ZipEntry ze = entries.nextElement();
                sb.append(ze.isDirectory() ? "[DIR]  " : "[FILE] ")
                        .append(ze.getName())
                        .append(" (").append(ze.getSize()).append(" bytes)\n");
                count++;
            }
            if (entries.hasMoreElements()) {
                sb.append("... and more entries.\n");
            }

            JTextArea ta = new JTextArea(sb.toString(), 20, 50);
            ta.setEditable(false);
            ta.setFont(ThemeManager.getInstance().getCurrentTheme().fontMonospace);
            ta.setBackground(ThemeManager.getInstance().getCurrentTheme().background);
            ta.setForeground(ThemeManager.getInstance().getCurrentTheme().textPrimary);
            JOptionPane.showMessageDialog(fileTable, new JScrollPane(ta), "Archive Explorer — " + f.getName(), JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(fileTable, "Could not read archive:\n" + ex.getMessage(), "Archive Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showUnknownFileDialog(File f) {
        String[] options = {"Open in Notepad", "Inspect Hex/Text", "Launch with Windows Host...", "Cancel"};
        int choice = JOptionPane.showOptionDialog(fileTable,
                "No dedicated IMPERION OS viewer for extension: " + getFileType(f.getName()) +
                        "\nFile: " + f.getName() +
                        "\n\nHow would you like to inspect this file inside IMPERION OS?",
                "Open File — " + f.getName(),
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]);

        if (choice == 0) {
            openInNotepadFile(f);
        } else if (choice == 1) {
            inspectFileHex(f);
        } else if (choice == 2) {
            openWithWindowsHost(f);
        }
    }

    private void inspectFileHex(File f) {
        try (InputStream is = Files.newInputStream(f.toPath())) {
            byte[] buf = new byte[1024];
            int read = is.read(buf);
            StringBuilder sb = new StringBuilder();
            sb.append("=== HEX / TEXT PREVIEW: ").append(f.getName()).append(" (First 1KB) ===\n\n");
            for (int i = 0; i < read; i += 16) {
                sb.append(String.format("%04X  ", i));
                for (int j = 0; j < 16; j++) {
                    if (i + j < read) {
                        sb.append(String.format("%02X ", buf[i + j]));
                    } else {
                        sb.append("   ");
                    }
                }
                sb.append(" |");
                for (int j = 0; j < 16; j++) {
                    if (i + j < read) {
                        byte b = buf[i + j];
                        sb.append((b >= 32 && b <= 126) ? (char) b : '.');
                    }
                }
                sb.append("|\n");
            }
            JTextArea ta = new JTextArea(sb.toString(), 22, 60);
            ta.setEditable(false);
            ta.setFont(ThemeManager.getInstance().getCurrentTheme().fontMonospace);
            ta.setBackground(ThemeManager.getInstance().getCurrentTheme().background);
            ta.setForeground(ThemeManager.getInstance().getCurrentTheme().neonAccent);
            JOptionPane.showMessageDialog(fileTable, new JScrollPane(ta), "Hex Inspector — " + f.getName(), JOptionPane.PLAIN_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(fileTable, "Error reading file:\n" + ex.getMessage(), "Read Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openWithWindowsHostPrompt() {
        File f = getSelectedFile();
        if (f == null || f.isDirectory()) return;
        int opt = JOptionPane.showConfirmDialog(fileTable,
                "Launch '" + f.getName() + "' in Windows host application?\nNote: Windows will switch focus away from IMPERION OS.",
                "Confirm External Launch", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (opt == JOptionPane.YES_OPTION) {
            openWithWindowsHost(f);
        }
    }

    private void openWithWindowsHost(File f) {
        new Thread(() -> {
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                    Desktop.getDesktop().open(f);
                } else {
                    new ProcessBuilder("cmd.exe", "/c", "start", "", f.getAbsolutePath()).start();
                }
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(fileTable, "Failed to launch host application:\n" + ex.getMessage(), "Open Error", JOptionPane.ERROR_MESSAGE));
            }
        }, "HostFileOpen").start();
    }

    private void copySelectedPath() {
        File f = getSelectedFile();
        if (f == null) return;
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(f.getAbsolutePath()), null);
        statusLabel.setText("Copied to clipboard: " + f.getAbsolutePath());
    }

    private void showProperties() {
        File f = getSelectedFile();
        if (f == null) return;

        String nl = "\n";
        StringBuilder sb = new StringBuilder();
        sb.append("Name:       ").append(f.getName()).append(nl);
        sb.append("Type:       ").append(f.isDirectory() ? "Folder" : getFileType(f.getName())).append(nl);
        sb.append("Location:   ").append(f.getParent()).append(nl);
        sb.append("Size:       ").append(f.isDirectory() ? "<Directory>" : formatSize(f.length())).append(nl);
        sb.append("Modified:   ").append(LocalDateTime.ofInstant(Instant.ofEpochMilli(f.lastModified()), ZoneId.systemDefault()).format(DATE_FMT)).append(nl);
        sb.append("Hidden:     ").append(f.isHidden() ? "Yes" : "No").append(nl);
        sb.append("Readable:   ").append(f.canRead() ? "Yes" : "No").append(nl);
        sb.append("Writable:   ").append(f.canWrite() ? "Yes" : "No").append(nl);
        sb.append("Executable: ").append(f.canExecute() ? "Yes" : "No").append(nl);
        sb.append("Absolute:   ").append(f.getAbsolutePath()).append(nl);

        JOptionPane.showMessageDialog(fileTable, sb.toString(), "File Properties — " + f.getName(), JOptionPane.PLAIN_MESSAGE);
    }

    private void deleteSelected() {
        File f = getSelectedFile();
        if (f == null) return;

        int opt = JOptionPane.showConfirmDialog(fileTable,
                "Delete \"" + f.getName() + "\"?\n\nThis will move it to the system Recycle Bin if supported,\nor permanently delete it otherwise.",
                "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (opt != JOptionPane.YES_OPTION) return;

        // Try system trash first
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH)) {
            boolean moved = Desktop.getDesktop().moveToTrash(f);
            if (moved) {
                statusLabel.setText("Moved to Recycle Bin: " + f.getName());
                refresh();
                return;
            }
        }

        // Fallback: permanent delete
        if (f.isFile()) {
            if (f.delete()) {
                statusLabel.setText("Deleted: " + f.getName());
                refresh();
            } else {
                JOptionPane.showMessageDialog(fileTable, "Could not delete:\n" + f.getName(), "Delete Failed", JOptionPane.ERROR_MESSAGE);
            }
        } else {
            JOptionPane.showMessageDialog(fileTable, "Directory deletion is not supported in Real File Explorer.\nUse the Terminal or Windows Explorer.", "Not Supported", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void performSearch() {
        String query = searchField.getText().trim().toLowerCase();
        if (query.isEmpty() || currentDirectory == null) {
            refresh();
            return;
        }

        statusLabel.setText("Searching for '" + query + "' in " + currentDirectory.getAbsolutePath() + "...");

        new SwingWorker<List<File>, Void>() {
            @Override
            protected List<File> doInBackground() {
                List<File> results = new ArrayList<>();
                try {
                    Files.walk(currentDirectory.toPath(), 4)
                            .filter(p -> p.getFileName().toString().toLowerCase().contains(query))
                            .limit(500)
                            .forEach(p -> results.add(p.toFile()));
                } catch (IOException ex) {
                    // Partial results ok
                }
                return results;
            }

            @Override
            protected void done() {
                try {
                    List<File> results = get();
                    tableModel.setRowCount(0);
                    currentFiles.clear();
                    currentFiles.addAll(results);

                    for (File f : results) {
                        tableModel.addRow(new Object[]{
                                f.getName(),
                                f.isDirectory() ? "Folder" : getFileType(f.getName()),
                                f.isDirectory() ? "<DIR>" : formatSize(f.length()),
                                f.getParent()
                        });
                    }
                    statusLabel.setText("Found " + results.size() + " matches for '" + query + "' (showing up to 500 results)");
                } catch (Exception ex) {
                    statusLabel.setText("Search error: " + ex.getMessage());
                }
            }
        }.execute();
    }

    // ── Icon selection by file type ───────────────────────────────────────────

    public static Icon getIconForFile(File f) {
        if (f.isDirectory()) return CyberpunkIcons.createIcon("FOLDER", 16);
        String name = f.getName().toLowerCase();
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.')) : "";
        switch (ext) {
            case ".txt": case ".md": case ".log": case ".json":
            case ".xml": case ".properties": case ".ini":
            case ".yaml": case ".yml": case ".toml":
                return CyberpunkIcons.createIcon("NOTEPAD", 16);
            case ".py": case ".java": case ".js": case ".ts":
            case ".cpp": case ".c": case ".h": case ".rb": case ".go":
            case ".rs": case ".kt": case ".sh": case ".bat": case ".ps1":
            case ".sql": case ".lua": case ".php": case ".swift":
                return CyberpunkIcons.createIcon("CODE", 16);
            case ".jpg": case ".jpeg": case ".png": case ".gif":
            case ".bmp": case ".svg": case ".webp": case ".ico": case ".tiff":
                return CyberpunkIcons.createIcon("IMAGE", 16);
            case ".mp3": case ".wav": case ".ogg": case ".flac":
            case ".aac": case ".m4a": case ".wma":
                return CyberpunkIcons.createIcon("AUDIO", 16);
            case ".mp4": case ".avi": case ".mkv": case ".mov":
            case ".wmv": case ".webm": case ".flv": case ".m4v":
                return CyberpunkIcons.createIcon("VIDEO", 16);
            case ".zip": case ".rar": case ".7z": case ".tar":
            case ".gz": case ".bz2": case ".xz": case ".cab":
                return CyberpunkIcons.createIcon("ARCHIVE", 16);
            case ".exe": case ".msi": case ".dll": case ".sys":
            case ".com": case ".scr":
                return CyberpunkIcons.createIcon("EXE", 16);
            case ".pdf":
                return CyberpunkIcons.createIcon("PDF", 16);
            case ".docx": case ".doc": case ".odt": case ".rtf":
                return CyberpunkIcons.createIcon("NOTEPAD", 16);
            case ".xlsx": case ".xls": case ".ods": case ".csv":
                return CyberpunkIcons.createIcon("CALCULATOR", 16);
            case ".pptx": case ".ppt": case ".odp":
                return CyberpunkIcons.createIcon("MONITOR", 16);
            default:
                return CyberpunkIcons.createIcon("FILE", 16);
        }
    }

    private static String getFileType(String name) {
        if (name == null) return "File";
        String lower = name.toLowerCase();
        if (!lower.contains(".")) return "File";
        String ext = lower.substring(lower.lastIndexOf('.') + 1);
        switch (ext) {
            case "txt": return "Text Document";
            case "md": return "Markdown";
            case "log": return "Log File";
            case "json": return "JSON Data";
            case "xml": return "XML File";
            case "py": return "Python Script";
            case "java": return "Java Source";
            case "js": return "JavaScript";
            case "ts": return "TypeScript";
            case "cpp": case "c": return "C/C++ Source";
            case "sh": case "bat": case "ps1": return "Script";
            case "jpg": case "jpeg": case "png": case "gif": case "bmp": case "webp": return "Image";
            case "mp3": case "wav": case "flac": case "ogg": return "Audio";
            case "mp4": case "avi": case "mkv": case "mov": return "Video";
            case "zip": case "rar": case "7z": case "tar": case "gz": return "Archive";
            case "exe": return "Executable";
            case "msi": return "Installer";
            case "dll": return "Library";
            case "pdf": return "PDF Document";
            case "docx": case "doc": return "Word Document";
            case "xlsx": case "xls": return "Spreadsheet";
            case "pptx": case "ppt": return "Presentation";
            default: return ext.toUpperCase() + " File";
        }
    }

    private static boolean isTextFile(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".log")
                || lower.endsWith(".json") || lower.endsWith(".xml") || lower.endsWith(".py")
                || lower.endsWith(".java") || lower.endsWith(".js") || lower.endsWith(".ts")
                || lower.endsWith(".sh") || lower.endsWith(".bat") || lower.endsWith(".properties")
                || lower.endsWith(".ini") || lower.endsWith(".yaml") || lower.endsWith(".yml")
                || lower.endsWith(".csv") || !lower.contains(".");
    }

    private static String formatSize(long bytes) {
        if (bytes < 0) return "N/A";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return SIZE_FMT.format(bytes / 1024) + " KB";
        if (bytes < 1024 * 1024 * 1024) return SIZE_FMT.format(bytes / (1024 * 1024)) + " MB";
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}

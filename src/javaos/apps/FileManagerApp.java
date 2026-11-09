package javaos.apps;

import javaos.models.FileItem;
import javaos.services.VirtualFileService;
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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * Sandboxed cyberpunk File Manager application.
 * Manages virtual file browsing, creation, deletion, search, and Recycle Bin restoration.
 */
public class FileManagerApp extends OSApplication {

    private String currentVirtualPath = "/";
    private final Stack<String> backStack = new Stack<>();

    private JLabel pathLabel;
    private JTable fileTable;
    private DefaultTableModel tableModel;
    private NeonTextField searchField;
    private JLabel statusLabel;
    private List<FileItem> currentItems = new ArrayList<>();
    private String clipboardPath = null;
    private boolean isCut = false;
    private boolean showingTrash = false;

    public FileManagerApp() {
        super(new AppDescriptor(
                "files",
                "File Manager",
                "Browse, manage, and inspect virtual home files",
                AppDescriptor.Category.UTILITIES,
                CyberpunkIcons.createIcon("FILES", 20),
                false
        ));
    }

    @Override
    public Dimension getPreferredWindowSize() {
        return new Dimension(780, 520);
    }

    @Override
    public JComponent createContent() {
        JPanel main = new JPanel(new BorderLayout(0, 6));
        main.setOpaque(false);
        main.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        Theme theme = ThemeManager.getInstance().getCurrentTheme();

        // Top Navigation Bar
        JPanel navBar = new JPanel(new BorderLayout(6, 0));
        navBar.setOpaque(false);

        JPanel leftNav = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        leftNav.setOpaque(false);

        NeonButton btnBack = new NeonButton("Back", CyberpunkIcons.createIcon("MINIMIZE", 14));
        btnBack.setToolTipText("Navigate to previous directory");
        btnBack.addActionListener(e -> navigateBack());

        NeonButton btnUp = new NeonButton("Up", CyberpunkIcons.createIcon("MAXIMIZE", 14));
        btnUp.setToolTipText("Navigate to parent directory");
        btnUp.addActionListener(e -> navigateUp());

        NeonButton btnRefresh = new NeonButton("", CyberpunkIcons.createIcon("REFRESH", 14));
        btnRefresh.setToolTipText("Refresh directory listing");
        btnRefresh.addActionListener(e -> refreshListing());

        leftNav.add(btnBack);
        leftNav.add(btnUp);
        leftNav.add(btnRefresh);
        navBar.add(leftNav, BorderLayout.WEST);

        pathLabel = new JLabel(" /");
        pathLabel.setFont(theme.fontBold);
        pathLabel.setForeground(theme.neonAccent);
        pathLabel.setOpaque(true);
        pathLabel.setBackground(theme.background);
        pathLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(theme.secondaryAccent),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));
        navBar.add(pathLabel, BorderLayout.CENTER);

        // Search input
        searchField = new NeonTextField(14);
        searchField.setPlaceholder("Search files...");
        searchField.addActionListener(e -> performSearch());
        navBar.add(searchField, BorderLayout.EAST);

        main.add(navBar, BorderLayout.NORTH);

        // Action Toolbar
        JPanel actionToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        actionToolbar.setOpaque(false);

        NeonButton btnNewFolder = new NeonButton("New Folder", CyberpunkIcons.createIcon("FOLDER", 14));
        btnNewFolder.addActionListener(e -> promptNewFolder());

        NeonButton btnNewFile = new NeonButton("New Text File", CyberpunkIcons.createIcon("FILE", 14));
        btnNewFile.addActionListener(e -> promptNewFile());

        NeonButton btnDelete = new NeonButton("Delete", CyberpunkIcons.createIcon("TRASH", 14), NeonButton.Style.DANGER);
        btnDelete.addActionListener(e -> deleteSelected());

        NeonButton btnTrash = new NeonButton("Recycle Bin", CyberpunkIcons.createIcon("TRASH", 14));
        btnTrash.addActionListener(e -> toggleTrashView());

        actionToolbar.add(btnNewFolder);
        actionToolbar.add(btnNewFile);
        actionToolbar.add(btnDelete);
        actionToolbar.add(btnTrash);

        // Table View
        String[] columns = {"Name", "Type", "Size", "Modified"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        fileTable = new JTable(tableModel);
        fileTable.setOpaque(true);
        fileTable.setBackground(theme.panel);
        fileTable.setForeground(theme.textPrimary);
        fileTable.setGridColor(new Color(theme.secondaryAccent.getRed(), theme.secondaryAccent.getGreen(), theme.secondaryAccent.getBlue(), 60));
        fileTable.setRowHeight(30);
        fileTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        fileTable.getTableHeader().setBackground(theme.elevatedPanel);
        fileTable.getTableHeader().setForeground(theme.neonAccent);
        fileTable.getTableHeader().setFont(theme.fontBold);

        // Custom Cell Renderer — icon + name in col 0, text in remaining cols
        fileTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object val, boolean isSel, boolean hasFocus, int row, int col) {
                super.getTableCellRendererComponent(table, val, isSel, hasFocus, row, col);
                Theme t = ThemeManager.getInstance().getCurrentTheme();
                setFont(t.fontRegular);
                setHorizontalAlignment(SwingConstants.LEFT);
                setIconTextGap(8);
                setBorder(BorderFactory.createEmptyBorder(0, col == 0 ? 8 : 10, 0, 4));
                if (isSel) {
                    setBackground(t.selectionBackground);
                    setForeground(t.neonAccent);
                } else {
                    setBackground(row % 2 == 0 ? t.panel : t.elevatedPanel);
                    setForeground(t.textPrimary);
                }
                if (col == 0 && row < currentItems.size()) {
                    FileItem item = currentItems.get(row);
                    setIcon(CyberpunkIcons.createIcon(item.isDirectory() ? "FOLDER" : "FILE", 16));
                } else {
                    setIcon(null);
                }
                return this;
            }
        });

        // Double click to open
        fileTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openSelected();
                }
            }
        });

        // Context Menu
        JPopupMenu popup = createContextMenu();
        fileTable.setComponentPopupMenu(popup);

        JScrollPane scrollPane = new JScrollPane(fileTable);
        scrollPane.getViewport().setBackground(theme.panel);
        scrollPane.getVerticalScrollBar().setUI(new NeonScrollBarUI());
        scrollPane.getHorizontalScrollBar().setUI(new NeonScrollBarUI());
        scrollPane.setBorder(BorderFactory.createLineBorder(theme.secondaryAccent));

        JPanel centerPanel = new JPanel(new BorderLayout(0, 4));
        centerPanel.setOpaque(false);
        centerPanel.add(actionToolbar, BorderLayout.NORTH);
        centerPanel.add(scrollPane, BorderLayout.CENTER);
        main.add(centerPanel, BorderLayout.CENTER);

        // Bottom status bar
        statusLabel = new JLabel("Ready");
        statusLabel.setFont(theme.fontSmall);
        statusLabel.setForeground(theme.textSecondary);
        main.add(statusLabel, BorderLayout.SOUTH);

        return main;
    }

    @Override
    public void onOpen() {
        refreshListing();
    }

    public void navigateTo(String path) {
        if (!showingTrash) {
            backStack.push(currentVirtualPath);
        }
        showingTrash = false;
        currentVirtualPath = path.startsWith("/") ? path : "/" + path;
        refreshListing();
    }

    private void navigateBack() {
        if (!backStack.isEmpty()) {
            showingTrash = false;
            currentVirtualPath = backStack.pop();
            refreshListing();
        }
    }

    private void navigateUp() {
        if ("/".equals(currentVirtualPath) || currentVirtualPath.isEmpty()) {
            return;
        }
        showingTrash = false;
        int lastSlash = currentVirtualPath.lastIndexOf('/');
        if (lastSlash <= 0) {
            navigateTo("/");
        } else {
            navigateTo(currentVirtualPath.substring(0, lastSlash));
        }
    }

    private void refreshListing() {
        if (getOS() == null) return;
        pathLabel.setText(" " + currentVirtualPath);
        tableModel.setRowCount(0);
        currentItems.clear();

        try {
            List<FileItem> items = getOS().getFileService().listFiles(currentVirtualPath);
            currentItems.addAll(items);

            for (FileItem item : items) {
                tableModel.addRow(new Object[]{
                        item.getName(),
                        item.isDirectory() ? "Directory" : "File",
                        item.getFormattedSize(),
                        item.getFormattedDate()
                });
            }
            statusLabel.setText(items.size() + " items in " + currentVirtualPath);
        } catch (IOException e) {
            statusLabel.setText("Error reading path: " + e.getMessage());
            JOptionPane.showMessageDialog(fileTable, "Failed to load directory: " + e.getMessage(), "I/O Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openSelected() {
        int row = fileTable.getSelectedRow();
        if (row < 0 || row >= currentItems.size()) return;

        FileItem item = currentItems.get(row);
        if (item.isDirectory()) {
            String next = "/".equals(currentVirtualPath) ? "/" + item.getName() : currentVirtualPath + "/" + item.getName();
            navigateTo(next);
        } else {
            String vPath = item.getVirtualPath();
            String rel = vPath.startsWith("/") ? vPath.substring(1) : vPath;
            java.io.File realBacking = getOS().getFileService().getHomeRoot().resolve(rel).toFile();
            String ln = item.getName().toLowerCase();

            if (ln.endsWith(".docx") || ln.endsWith(".doc")) {
                String defApp = getOS().getSettingsService().getDefaultWordApp();
                OSApplication app = getOS().getApplicationManager().launchApp(defApp);
                if (app instanceof WordEditorApp) {
                    ((WordEditorApp) app).openFile(realBacking);
                } else if (app instanceof DocumentViewerApp) {
                    ((DocumentViewerApp) app).openFile(realBacking);
                }
            } else if (ln.endsWith(".pptx") || ln.endsWith(".ppt")) {
                String defApp = getOS().getSettingsService().getDefaultSlidesApp();
                OSApplication app = getOS().getApplicationManager().launchApp(defApp);
                if (app instanceof PresentationEditorApp) {
                    ((PresentationEditorApp) app).openFile(realBacking);
                } else if (app instanceof DocumentViewerApp) {
                    ((DocumentViewerApp) app).openFile(realBacking);
                }
            } else if (ln.endsWith(".pdf")) {
                OSApplication app = getOS().getApplicationManager().launchApp("docviewer");
                if (app instanceof DocumentViewerApp) {
                    ((DocumentViewerApp) app).openFile(realBacking);
                }
            } else if (ln.endsWith(".xlsx") || ln.endsWith(".xls") || ln.endsWith(".csv")) {
                OSApplication app = getOS().getApplicationManager().launchApp("sheet");
                if (app instanceof SpreadsheetApp && realBacking != null) {
                    ((SpreadsheetApp) app).openFile(realBacking);
                }
            } else {
                // Open text file in Notepad
                OSApplication notepad = getOS().getApplicationManager().launchApp("notepad");
                if (notepad instanceof NotepadApp) {
                    ((NotepadApp) notepad).openVirtualFile(item.getVirtualPath());
                }
            }
        }
    }

    private void promptNewFolder() {
        String name = JOptionPane.showInputDialog(fileTable, "Enter directory name:", "New Folder", JOptionPane.PLAIN_MESSAGE);
        if (name != null && !name.trim().isEmpty()) {
            String vPath = "/".equals(currentVirtualPath) ? "/" + name.trim() : currentVirtualPath + "/" + name.trim();
            try {
                boolean created = getOS().getFileService().createDirectory(vPath);
                if (!created) {
                    JOptionPane.showMessageDialog(fileTable, "Directory already exists: " + name);
                } else {
                    refreshListing();
                }
            } catch (IOException e) {
                JOptionPane.showMessageDialog(fileTable, "Failed to create directory: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void promptNewFile() {
        String name = JOptionPane.showInputDialog(fileTable, "Enter file name (e.g. notes.txt):", "New File", JOptionPane.PLAIN_MESSAGE);
        if (name != null && !name.trim().isEmpty()) {
            String vPath = "/".equals(currentVirtualPath) ? "/" + name.trim() : currentVirtualPath + "/" + name.trim();
            try {
                boolean created = getOS().getFileService().createFile(vPath, "");
                if (!created) {
                    JOptionPane.showMessageDialog(fileTable, "File already exists: " + name);
                } else {
                    refreshListing();
                }
            } catch (IOException e) {
                JOptionPane.showMessageDialog(fileTable, "Failed to create file: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void deleteSelected() {
        int row = fileTable.getSelectedRow();
        if (row < 0 || row >= currentItems.size()) {
            JOptionPane.showMessageDialog(fileTable, "Please select an item to delete.");
            return;
        }

        FileItem item = currentItems.get(row);
        int confirm = JOptionPane.showConfirmDialog(fileTable,
                "Move '" + item.getName() + "' to Recycle Bin?",
                "Confirm Deletion", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            try {
                getOS().getFileService().moveToTrash(item.getVirtualPath());
                refreshListing();
                getOS().getNotificationManager().postNotification(
                        "File Deleted", "Moved to Recycle Bin: " + item.getName(), javaos.models.NotificationMessage.Level.INFO);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(fileTable, "Failed to delete: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void promptRename() {
        int row = fileTable.getSelectedRow();
        if (row < 0 || row >= currentItems.size()) return;

        FileItem item = currentItems.get(row);
        String newName = JOptionPane.showInputDialog(fileTable, "Rename '" + item.getName() + "' to:", item.getName());
        if (newName != null && !newName.trim().isEmpty() && !newName.trim().equals(item.getName())) {
            try {
                getOS().getFileService().rename(item.getVirtualPath(), newName.trim());
                refreshListing();
            } catch (IOException e) {
                JOptionPane.showMessageDialog(fileTable, "Failed to rename: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void performSearch() {
        String q = searchField.getText().trim();
        if (q.isEmpty()) {
            refreshListing();
            return;
        }

        try {
            List<FileItem> results = getOS().getFileService().search(q);
            tableModel.setRowCount(0);
            currentItems.clear();
            currentItems.addAll(results);

            for (FileItem item : results) {
                tableModel.addRow(new Object[]{
                        item.getVirtualPath(),
                        item.isDirectory() ? "Directory" : "File",
                        item.getFormattedSize(),
                        item.getFormattedDate()
                });
            }
            statusLabel.setText("Found " + results.size() + " matching items for '" + q + "'");
            pathLabel.setText(" Search: '" + q + "'");
        } catch (IOException e) {
            JOptionPane.showMessageDialog(fileTable, "Search error: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void toggleTrashView() {
        showingTrash = true;
        pathLabel.setText(" Recycle Bin (Trash)");
        tableModel.setRowCount(0);
        currentItems.clear();

        try {
            List<VirtualFileService.TrashItem> trash = getOS().getFileService().listTrash();
            if (trash.isEmpty()) {
                statusLabel.setText("Recycle Bin is empty.");
                return;
            }

            for (VirtualFileService.TrashItem item : trash) {
                tableModel.addRow(new Object[]{
                        item.originalName + " (from " + item.originalVirtualPath + ")",
                        item.isDirectory ? "Deleted Dir" : "Deleted File",
                        item.entryId,
                        "In Trash"
                });
            }
            statusLabel.setText(trash.size() + " items in Recycle Bin. Right-click to Restore or Empty.");

            // Offer restore dialog
            int opt = JOptionPane.showOptionDialog(fileTable,
                    "Recycle Bin contains " + trash.size() + " items.\nWhat action would you like to perform?",
                    "Recycle Bin Actions",
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.PLAIN_MESSAGE,
                    null,
                    new String[]{"Restore Selected", "Empty Bin", "Back to Files"},
                    "Restore Selected");

            if (opt == 0 && fileTable.getSelectedRow() >= 0 && fileTable.getSelectedRow() < trash.size()) {
                VirtualFileService.TrashItem selected = trash.get(fileTable.getSelectedRow());
                getOS().getFileService().restoreFromTrash(selected.entryId);
                getOS().getNotificationManager().postNotification(
                        "File Restored", "Restored " + selected.originalName + " to " + selected.originalVirtualPath, javaos.models.NotificationMessage.Level.SUCCESS);
                refreshListing();
            } else if (opt == 1) {
                getOS().getFileService().emptyTrash();
                getOS().getNotificationManager().postNotification(
                        "Recycle Bin Emptied", "All trash items permanently removed.", javaos.models.NotificationMessage.Level.INFO);
                refreshListing();
            } else if (opt == 2) {
                refreshListing();
            }

        } catch (IOException e) {
            JOptionPane.showMessageDialog(fileTable, "Trash error: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private JPopupMenu createContextMenu() {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem itemOpen = new JMenuItem("Open");
        itemOpen.addActionListener(e -> openSelected());

        JMenuItem itemRename = new JMenuItem("Rename");
        itemRename.addActionListener(e -> promptRename());

        JMenuItem itemDelete = new JMenuItem("Move to Trash");
        itemDelete.addActionListener(e -> deleteSelected());

        menu.add(itemOpen);
        menu.add(itemRename);
        menu.addSeparator();
        menu.add(itemDelete);
        return menu;
    }
}

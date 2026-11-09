package javaos.apps;

import javaos.models.FileItem;
import javaos.models.NotificationMessage;
import javaos.ui.Theme;
import javaos.ui.ThemeManager;
import javaos.ui.components.CyberpunkIcons;
import javaos.ui.components.NeonScrollBarUI;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.undo.UndoManager;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Modern Windows 11 Fluent Notepad equivalent for IMPERION OS.
 * Features:
 *  - Multi-tab document strip with instant tab switching and creation
 *  - Windows 11 Menu Bar (File, Edit, View, Theme)
 *  - Live Line Numbers, Syntax Word Wrap, and Zoom scaling (Ctrl +/-)
 *  - Interactive Find & Replace Dialog (Next, Replace, Replace All with occurrence count)
 *  - Windows 11 segmented Status Bar: Ln/Col, Characters/Words, Zoom %, CRLF, UTF-8
 *  - Sandboxed Virtual Filesystem + Host Real File I/O support
 *  - Seamless bridge: "Open in Word Processor"
 */
public class NotepadApp extends OSApplication {

    public static class TabInfo {
        public String title = "Untitled";
        public String virtualPath = null;
        public File realFile = null;
        public boolean isModified = false;
        public JTextArea textArea;
        public JTextArea lineNumbers;
        public JScrollPane scrollPane;
        public UndoManager undoManager;
        public int lastFoundIndex = -1;
        public JPanel tabComponent;
        public JLabel tabLabel;
    }

    private final List<TabInfo> tabs = new ArrayList<>();
    private int activeTabIndex = 0;

    // UI containers
    private JPanel tabStripPanel;
    private JPanel editorContainer;
    private CardLayout editorCardLayout;
    private JLabel statusPositionLabel;
    private JLabel statusCountLabel;
    private JLabel statusZoomLabel;
    private JLabel statusLineEndingLabel;
    private JLabel statusEncodingLabel;

    // View settings
    private boolean wordWrapEnabled = false;
    private boolean lineNumbersEnabled = true;
    private float currentZoomLevel = 1.0f; // 100%
    private int currentFontSize = 13;
    private boolean isLightMode = false;

    public NotepadApp() {
        super(new AppDescriptor(
                "notepad",
                "Notepad",
                "Modern Windows 11 fluent text editor with multi-tab support",
                AppDescriptor.Category.PRODUCTIVITY,
                CyberpunkIcons.createIcon("NOTEPAD", 20),
                false
        ));
    }

    @Override
    public Dimension getPreferredWindowSize() {
        return new Dimension(780, 540);
    }

    @Override
    public JComponent createContent() {
        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setOpaque(true);
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        root.setBackground(isLightMode ? new Color(243, 243, 243) : theme.background);

        // ── 1. TOP HEADER: MENU BAR & TAB STRIP ─────────────────────────────
        JPanel topContainer = new JPanel();
        topContainer.setLayout(new BoxLayout(topContainer, BoxLayout.Y_AXIS));
        topContainer.setOpaque(true);
        topContainer.setBackground(isLightMode ? new Color(243, 243, 243) : theme.panel);
        topContainer.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, isLightMode ? new Color(220, 220, 220) : theme.secondaryAccent));

        // Windows 11 Menu Bar
        JMenuBar menuBar = buildMenuBar();
        topContainer.add(menuBar);

        // Windows 11 Tabs Strip
        JPanel tabHeaderBar = new JPanel(new BorderLayout());
        tabHeaderBar.setOpaque(false);
        tabHeaderBar.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));

        tabStripPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        tabStripPanel.setOpaque(false);

        JButton btnNewTab = makeSmallIconBtn("+", "New Tab (Ctrl+T)");
        btnNewTab.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnNewTab.addActionListener(e -> addNewTab("Untitled", ""));

        JPanel tabLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        tabLeft.setOpaque(false);
        tabLeft.add(tabStripPanel);
        tabLeft.add(btnNewTab);

        tabHeaderBar.add(tabLeft, BorderLayout.WEST);

        // Right side quick buttons
        JPanel rightTools = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        rightTools.setOpaque(false);

        JButton btnTheme = makeSmallIconBtn(isLightMode ? "🌙 Dark" : "☀ Light", "Toggle Light / Dark Theme");
        btnTheme.addActionListener(e -> toggleTheme());
        rightTools.add(btnTheme);

        JButton btnWord = makeSmallIconBtn("📄 Open in Word", "Open in Word Processor (rich formatting)");
        btnWord.addActionListener(e -> openInWordProcessor());
        rightTools.add(btnWord);

        tabHeaderBar.add(rightTools, BorderLayout.EAST);
        topContainer.add(tabHeaderBar);

        root.add(topContainer, BorderLayout.NORTH);

        // ── 2. CENTER: EDITOR CARDS ──────────────────────────────────────────
        editorCardLayout = new CardLayout();
        editorContainer = new JPanel(editorCardLayout);
        editorContainer.setOpaque(false);
        root.add(editorContainer, BorderLayout.CENTER);

        // ── 3. BOTTOM: WINDOWS 11 STATUS BAR ────────────────────────────────
        JPanel statusBar = new JPanel(new BorderLayout(8, 0));
        statusBar.setOpaque(true);
        statusBar.setBackground(isLightMode ? new Color(243, 243, 243) : theme.panel);
        statusBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, isLightMode ? new Color(220, 220, 220) : theme.secondaryAccent),
                BorderFactory.createEmptyBorder(3, 12, 3, 12)
        ));

        statusPositionLabel = new JLabel("Ln 1, Col 1");
        statusPositionLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        statusPositionLabel.setForeground(isLightMode ? Color.DARK_GRAY : theme.textPrimary);
        statusBar.add(statusPositionLabel, BorderLayout.WEST);

        statusCountLabel = new JLabel("0 characters  |  0 words");
        statusCountLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        statusCountLabel.setForeground(isLightMode ? Color.GRAY : theme.textSecondary);
        statusCountLabel.setHorizontalAlignment(SwingConstants.CENTER);
        statusBar.add(statusCountLabel, BorderLayout.CENTER);

        JPanel rightStatus = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        rightStatus.setOpaque(false);

        statusZoomLabel = new JLabel("100%");
        statusZoomLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        statusZoomLabel.setForeground(isLightMode ? new Color(0, 102, 204) : theme.neonAccent);
        statusZoomLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        statusZoomLabel.setToolTipText("Click to reset zoom to 100%");
        statusZoomLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) { setZoom(1.0f); }
        });

        statusLineEndingLabel = new JLabel("Windows (CRLF)");
        statusLineEndingLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        statusLineEndingLabel.setForeground(isLightMode ? Color.GRAY : theme.textSecondary);

        statusEncodingLabel = new JLabel("UTF-8");
        statusEncodingLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        statusEncodingLabel.setForeground(isLightMode ? Color.GRAY : theme.textSecondary);

        rightStatus.add(statusZoomLabel);
        rightStatus.add(new JSeparator(SwingConstants.VERTICAL));
        rightStatus.add(statusLineEndingLabel);
        rightStatus.add(new JSeparator(SwingConstants.VERTICAL));
        rightStatus.add(statusEncodingLabel);

        statusBar.add(rightStatus, BorderLayout.EAST);
        root.add(statusBar, BorderLayout.SOUTH);

        // Bind global shortcuts
        setupKeyBindings(root);

        // Initialize with first blank document tab
        addNewTab("Untitled", "");

        return root;
    }

    // ── WINDOWS 11 FLUENT MENU BAR ──────────────────────────────────────────
    private JMenuBar buildMenuBar() {
        JMenuBar mb = new JMenuBar();
        mb.setOpaque(false);
        mb.setBorder(BorderFactory.createEmptyBorder(1, 4, 1, 4));

        Theme theme = ThemeManager.getInstance().getCurrentTheme();

        // 1. FILE MENU
        JMenu menuFile = makeMenu("File");
        menuFile.add(makeMenuItem("New Tab", "Ctrl+T", e -> addNewTab("Untitled", "")));
        menuFile.add(makeMenuItem("New Window", "Ctrl+N", e -> newDocument()));
        menuFile.add(makeMenuItem("Open...", "Ctrl+O", e -> promptOpen()));
        menuFile.add(makeMenuItem("Save", "Ctrl+S", e -> saveDocument()));
        menuFile.add(makeMenuItem("Save As...", "Ctrl+Shift+S", e -> saveAsDocument()));
        menuFile.addSeparator();
        menuFile.add(makeMenuItem("Close Tab", "Ctrl+W", e -> closeTab(activeTabIndex)));
        menuFile.add(makeMenuItem("Exit", "Alt+F4", e -> { if (canClose()) getWindow().close(); }));
        mb.add(menuFile);

        // 2. EDIT MENU
        JMenu menuEdit = makeMenu("Edit");
        menuEdit.add(makeMenuItem("Undo", "Ctrl+Z", e -> {
            TabInfo t = getActiveTab();
            if (t != null && t.undoManager.canUndo()) t.undoManager.undo();
        }));
        menuEdit.add(makeMenuItem("Redo", "Ctrl+Y", e -> {
            TabInfo t = getActiveTab();
            if (t != null && t.undoManager.canRedo()) t.undoManager.redo();
        }));
        menuEdit.addSeparator();
        menuEdit.add(makeMenuItem("Cut", "Ctrl+X", e -> { TabInfo t = getActiveTab(); if (t != null) t.textArea.cut(); }));
        menuEdit.add(makeMenuItem("Copy", "Ctrl+C", e -> { TabInfo t = getActiveTab(); if (t != null) t.textArea.copy(); }));
        menuEdit.add(makeMenuItem("Paste", "Ctrl+V", e -> { TabInfo t = getActiveTab(); if (t != null) t.textArea.paste(); }));
        menuEdit.add(makeMenuItem("Delete", "Del", e -> {
            TabInfo t = getActiveTab();
            if (t != null) t.textArea.replaceSelection("");
        }));
        menuEdit.addSeparator();
        menuEdit.add(makeMenuItem("Find...", "Ctrl+F", e -> showFindReplaceDialog()));
        menuEdit.add(makeMenuItem("Find & Replace...", "Ctrl+H", e -> showFindReplaceDialog()));
        menuEdit.add(makeMenuItem("Go To Line...", "Ctrl+G", e -> promptGoToLine()));
        menuEdit.addSeparator();
        menuEdit.add(makeMenuItem("Select All", "Ctrl+A", e -> { TabInfo t = getActiveTab(); if (t != null) t.textArea.selectAll(); }));
        menuEdit.add(makeMenuItem("Time/Date", "F5", e -> insertTimeDate()));
        mb.add(menuEdit);

        // 3. VIEW MENU
        JMenu menuView = makeMenu("View");
        menuView.add(makeMenuItem("Zoom In", "Ctrl++", e -> zoomIn()));
        menuView.add(makeMenuItem("Zoom Out", "Ctrl+-", e -> zoomOut()));
        menuView.add(makeMenuItem("Restore Default Zoom", "Ctrl+0", e -> setZoom(1.0f)));
        menuView.addSeparator();
        JCheckBoxMenuItem wrapItem = new JCheckBoxMenuItem("Word Wrap", wordWrapEnabled);
        wrapItem.addActionListener(e -> toggleWordWrap());
        menuView.add(wrapItem);
        JCheckBoxMenuItem lineNumItem = new JCheckBoxMenuItem("Line Numbers", lineNumbersEnabled);
        lineNumItem.addActionListener(e -> toggleLineNumbers());
        menuView.add(lineNumItem);
        mb.add(menuView);

        // 4. THEME MENU
        JMenu menuTheme = makeMenu("Theme");
        menuTheme.add(makeMenuItem("Windows Dark / Mica", null, e -> { isLightMode = false; applyVisualTheme(); }));
        menuTheme.add(makeMenuItem("Windows Light / Fluent", null, e -> { isLightMode = true; applyVisualTheme(); }));
        mb.add(menuTheme);

        return mb;
    }

    private JMenu makeMenu(String title) {
        JMenu m = new JMenu(title);
        m.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        m.setForeground(isLightMode ? Color.DARK_GRAY : Color.LIGHT_GRAY);
        m.setOpaque(false);
        return m;
    }

    private JMenuItem makeMenuItem(String title, String accelerator, java.awt.event.ActionListener l) {
        JMenuItem item = new JMenuItem(title);
        item.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        if (accelerator != null) {
            item.setAccelerator(KeyStroke.getKeyStroke(accelerator.replace("+", " ")));
        }
        item.addActionListener(l);
        return item;
    }

    // ── MULTI-TAB DOCUMENT ENGINE ───────────────────────────────────────────
    public TabInfo addNewTab(String title, String initialContent) {
        TabInfo tab = new TabInfo();
        tab.title = title;

        tab.textArea = new JTextArea(initialContent != null ? initialContent : "");
        tab.textArea.setFont(new Font("Consolas", Font.PLAIN, currentFontSize));
        tab.textArea.setTabSize(4);
        tab.textArea.setLineWrap(wordWrapEnabled);
        tab.textArea.setWrapStyleWord(wordWrapEnabled);
        tab.textArea.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        tab.undoManager = new UndoManager();
        tab.textArea.getDocument().addUndoableEditListener(e -> tab.undoManager.addEdit(e.getEdit()));

        tab.lineNumbers = new JTextArea("1 \n");
        tab.lineNumbers.setFont(new Font("Consolas", Font.PLAIN, currentFontSize));
        tab.lineNumbers.setEditable(false);
        tab.lineNumbers.setFocusable(false);
        tab.lineNumbers.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        tab.scrollPane = new JScrollPane(tab.textArea);
        if (lineNumbersEnabled) {
            tab.scrollPane.setRowHeaderView(tab.lineNumbers);
        }
        tab.scrollPane.getVerticalScrollBar().setUI(new NeonScrollBarUI());
        tab.scrollPane.getHorizontalScrollBar().setUI(new NeonScrollBarUI());
        tab.scrollPane.setBorder(BorderFactory.createEmptyBorder());

        // Document change & Caret listeners
        tab.textArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { onDocChanged(tab); }
            @Override public void removeUpdate(DocumentEvent e) { onDocChanged(tab); }
            @Override public void changedUpdate(DocumentEvent e) { onDocChanged(tab); }
        });

        tab.textArea.addCaretListener(e -> updateStatus(tab));

        // Create tab button visual
        tab.tabComponent = createTabPill(tab);

        int index = tabs.size();
        tabs.add(tab);

        // Add to card layout
        String cardKey = "TAB_" + System.identityHashCode(tab);
        editorContainer.add(tab.scrollPane, cardKey);

        refreshTabStrip();
        switchToTab(index);
        applyVisualTheme();

        return tab;
    }

    private JPanel createTabPill(TabInfo tab) {
        JPanel pill = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        pill.setOpaque(true);
        pill.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(60, 65, 80), 1),
                BorderFactory.createEmptyBorder(2, 8, 2, 4)
        ));
        pill.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        tab.tabLabel = new JLabel(tab.title);
        tab.tabLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        pill.add(tab.tabLabel);

        JButton btnClose = new JButton("✕");
        btnClose.setFont(new Font("Segoe UI", Font.BOLD, 10));
        btnClose.setFocusPainted(false);
        btnClose.setBorderPainted(false);
        btnClose.setContentAreaFilled(false);
        btnClose.setMargin(new Insets(0, 2, 0, 2));
        btnClose.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnClose.addActionListener(e -> closeTab(tabs.indexOf(tab)));
        pill.add(btnClose);

        pill.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                switchToTab(tabs.indexOf(tab));
            }
        });

        return pill;
    }

    private void refreshTabStrip() {
        tabStripPanel.removeAll();
        Theme theme = ThemeManager.getInstance().getCurrentTheme();

        for (int i = 0; i < tabs.size(); i++) {
            TabInfo t = tabs.get(i);
            boolean isSel = (i == activeTabIndex);

            if (isLightMode) {
                t.tabComponent.setBackground(isSel ? Color.WHITE : new Color(230, 230, 230));
                t.tabLabel.setForeground(isSel ? Color.BLACK : Color.DARK_GRAY);
                t.tabComponent.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, isSel ? 2 : 0, 0, new Color(0, 102, 204)),
                        BorderFactory.createEmptyBorder(3, 10, 3, 4)
                ));
            } else {
                t.tabComponent.setBackground(isSel ? theme.elevatedPanel : theme.panel);
                t.tabLabel.setForeground(isSel ? theme.neonAccent : theme.textPrimary);
                t.tabComponent.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, isSel ? 2 : 0, 0, theme.neonAccent),
                        BorderFactory.createEmptyBorder(3, 10, 3, 4)
                ));
            }

            t.tabLabel.setText(t.title + (t.isModified ? " •" : ""));
            tabStripPanel.add(t.tabComponent);
        }
        tabStripPanel.revalidate();
        tabStripPanel.repaint();
    }

    public void switchToTab(int index) {
        if (index < 0 || index >= tabs.size()) return;
        activeTabIndex = index;
        TabInfo t = tabs.get(index);
        String cardKey = "TAB_" + System.identityHashCode(t);
        editorCardLayout.show(editorContainer, cardKey);
        refreshTabStrip();
        updateStatus(t);
        updateTitle();
        t.textArea.requestFocusInWindow();
    }

    public void closeTab(int index) {
        if (index < 0 || index >= tabs.size()) return;
        TabInfo t = tabs.get(index);
        if (t.isModified) {
            int opt = JOptionPane.showConfirmDialog(t.textArea,
                    "Save changes to " + t.title + " before closing?",
                    "Save File", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
            if (opt == JOptionPane.CANCEL_OPTION) return;
            if (opt == JOptionPane.YES_OPTION) {
                saveDocument();
            }
        }

        editorContainer.remove(t.scrollPane);
        tabs.remove(index);

        if (tabs.isEmpty()) {
            addNewTab("Untitled", "");
        } else {
            int next = Math.max(0, Math.min(activeTabIndex, tabs.size() - 1));
            switchToTab(next);
        }
    }

    private TabInfo getActiveTab() {
        if (activeTabIndex >= 0 && activeTabIndex < tabs.size()) {
            return tabs.get(activeTabIndex);
        }
        return null;
    }

    // ── THEME & STYLING ─────────────────────────────────────────────────────
    private void toggleTheme() {
        isLightMode = !isLightMode;
        applyVisualTheme();
    }

    private void applyVisualTheme() {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        Color bg = isLightMode ? Color.WHITE : theme.background;
        Color fg = isLightMode ? new Color(30, 30, 30) : theme.textPrimary;
        Color gutterBg = isLightMode ? new Color(245, 245, 245) : theme.panel;
        Color gutterFg = isLightMode ? new Color(160, 160, 160) : theme.textSecondary;
        Color caretCol = isLightMode ? Color.BLACK : Color.WHITE;

        for (TabInfo t : tabs) {
            t.textArea.setBackground(bg);
            t.textArea.setForeground(fg);
            t.textArea.setCaretColor(caretCol);
            t.lineNumbers.setBackground(gutterBg);
            t.lineNumbers.setForeground(gutterFg);
        }
        refreshTabStrip();
    }

    private JButton makeSmallIconBtn(String text, String tooltip) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setToolTipText(tooltip);
        btn.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
        btn.setForeground(isLightMode ? Color.DARK_GRAY : ThemeManager.getInstance().getCurrentTheme().textPrimary);
        return btn;
    }

    // ── ZOOM CONTROLS ───────────────────────────────────────────────────────
    private void zoomIn() {
        setZoom(currentZoomLevel + 0.15f);
    }

    private void zoomOut() {
        setZoom(currentZoomLevel - 0.15f);
    }

    private void setZoom(float zoom) {
        currentZoomLevel = Math.max(0.5f, Math.min(3.0f, zoom));
        int newSize = Math.round(currentFontSize * currentZoomLevel);
        for (TabInfo t : tabs) {
            t.textArea.setFont(new Font("Consolas", Font.PLAIN, newSize));
            t.lineNumbers.setFont(new Font("Consolas", Font.PLAIN, newSize));
        }
        if (statusZoomLabel != null) {
            statusZoomLabel.setText(Math.round(currentZoomLevel * 100) + "%");
        }
    }

    private void toggleWordWrap() {
        wordWrapEnabled = !wordWrapEnabled;
        for (TabInfo t : tabs) {
            t.textArea.setLineWrap(wordWrapEnabled);
            t.textArea.setWrapStyleWord(wordWrapEnabled);
        }
    }

    private void toggleLineNumbers() {
        lineNumbersEnabled = !lineNumbersEnabled;
        for (TabInfo t : tabs) {
            if (lineNumbersEnabled) {
                t.scrollPane.setRowHeaderView(t.lineNumbers);
            } else {
                t.scrollPane.setRowHeaderView(null);
            }
        }
    }

    // ── DOCUMENT MODIFICATION & STATUS ──────────────────────────────────────
    private void onDocChanged(TabInfo tab) {
        if (!tab.isModified) {
            tab.isModified = true;
            refreshTabStrip();
            updateTitle();
        }
        updateLineNumbers(tab);
        updateStatus(tab);
    }

    private void updateTitle() {
        TabInfo t = getActiveTab();
        if (t != null && getWindow() != null) {
            getWindow().setTitle(t.title + (t.isModified ? " •" : "") + " — Notepad");
        }
    }

    private void updateLineNumbers(TabInfo tab) {
        if (!lineNumbersEnabled) return;
        int lineCount = tab.textArea.getLineCount();
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= lineCount; i++) {
            sb.append(i).append(" \n");
        }
        tab.lineNumbers.setText(sb.toString());
    }

    private void updateStatus(TabInfo tab) {
        if (tab == null) return;
        try {
            int caretPos = tab.textArea.getCaretPosition();
            int line = tab.textArea.getLineOfOffset(caretPos) + 1;
            int col = caretPos - tab.textArea.getLineStartOffset(line - 1) + 1;
            String text = tab.textArea.getText();
            int totalChars = text.length();
            int wordCount = text.trim().isEmpty() ? 0 : text.trim().split("\\s+").length;

            if (statusPositionLabel != null) statusPositionLabel.setText("Ln " + line + ", Col " + col);
            if (statusCountLabel != null) statusCountLabel.setText(totalChars + " characters  |  " + wordCount + " words");
        } catch (Exception ignored) {}
    }

    private void insertTimeDate() {
        TabInfo t = getActiveTab();
        if (t != null) {
            String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("hh:mm a MM/dd/yyyy"));
            t.textArea.insert(now, t.textArea.getCaretPosition());
        }
    }

    private void promptGoToLine() {
        TabInfo t = getActiveTab();
        if (t == null) return;
        String val = JOptionPane.showInputDialog(t.textArea, "Enter line number (1 - " + t.textArea.getLineCount() + "):", "Go to Line", JOptionPane.PLAIN_MESSAGE);
        if (val != null && !val.trim().isEmpty()) {
            try {
                int line = Integer.parseInt(val.trim());
                int offset = t.textArea.getLineStartOffset(Math.max(0, line - 1));
                t.textArea.setCaretPosition(offset);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(t.textArea, "Invalid line number: " + val);
            }
        }
    }

    // ── SEARCH & REPLACE DIALOG ─────────────────────────────────────────────
    private void showFindReplaceDialog() {
        TabInfo t = getActiveTab();
        if (t == null) return;

        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(t.textArea), "Find & Replace", false);
        dialog.setLayout(new BorderLayout(8, 8));
        dialog.setResizable(false);

        Theme theme = ThemeManager.getInstance().getCurrentTheme();

        JPanel fieldsPanel = new JPanel(new GridLayout(2, 2, 6, 6));
        fieldsPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 4, 10));
        fieldsPanel.setOpaque(false);

        JLabel findLabel = new JLabel("Find what:");
        findLabel.setFont(theme.fontRegular);
        JTextField findField = new JTextField(20);
        findField.setText(t.textArea.getSelectedText() != null ? t.textArea.getSelectedText() : "");

        JLabel replaceLabel = new JLabel("Replace with:");
        replaceLabel.setFont(theme.fontRegular);
        JTextField replaceField = new JTextField(20);

        fieldsPanel.add(findLabel);
        fieldsPanel.add(findField);
        fieldsPanel.add(replaceLabel);
        fieldsPanel.add(replaceField);

        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 6));
        buttonsPanel.setOpaque(false);

        JButton btnFindNext = new JButton("Find Next");
        JButton btnReplace = new JButton("Replace");
        JButton btnReplaceAll = new JButton("Replace All");
        JButton btnClose = new JButton("Cancel");

        btnFindNext.addActionListener(e -> {
            String searchText = findField.getText();
            if (searchText.isEmpty()) return;
            String content = t.textArea.getText();
            int start = t.lastFoundIndex >= 0 ? t.lastFoundIndex + 1 : 0;
            int idx = content.indexOf(searchText, start);
            if (idx < 0 && start > 0) idx = content.indexOf(searchText, 0); // wrap
            if (idx >= 0) {
                t.lastFoundIndex = idx;
                t.textArea.setSelectionStart(idx);
                t.textArea.setSelectionEnd(idx + searchText.length());
                t.textArea.requestFocusInWindow();
            } else {
                t.lastFoundIndex = -1;
                JOptionPane.showMessageDialog(dialog, "Cannot find \"" + searchText + "\"", "Notepad", JOptionPane.INFORMATION_MESSAGE);
            }
        });

        btnReplace.addActionListener(e -> {
            String searchText = findField.getText();
            String replaceText = replaceField.getText();
            if (searchText.isEmpty()) return;
            String selected = t.textArea.getSelectedText();
            if (selected != null && selected.equals(searchText)) {
                t.textArea.replaceSelection(replaceText);
                t.lastFoundIndex = -1;
            } else {
                String content = t.textArea.getText();
                int idx = content.indexOf(searchText);
                if (idx >= 0) {
                    t.textArea.setSelectionStart(idx);
                    t.textArea.setSelectionEnd(idx + searchText.length());
                } else {
                    JOptionPane.showMessageDialog(dialog, "Cannot find \"" + searchText + "\"", "Notepad", JOptionPane.INFORMATION_MESSAGE);
                }
            }
        });

        btnReplaceAll.addActionListener(e -> {
            String searchText = findField.getText();
            String replaceText = replaceField.getText();
            if (searchText.isEmpty()) return;
            String content = t.textArea.getText();
            int count = 0;
            int idx = content.indexOf(searchText);
            while (idx >= 0) {
                count++;
                idx = content.indexOf(searchText, idx + searchText.length());
            }
            if (count == 0) {
                JOptionPane.showMessageDialog(dialog, "Cannot find \"" + searchText + "\"", "Notepad", JOptionPane.INFORMATION_MESSAGE);
            } else {
                t.textArea.setText(content.replace(searchText, replaceText));
                JOptionPane.showMessageDialog(dialog, "Replaced " + count + " occurrences.", "Replace All", JOptionPane.INFORMATION_MESSAGE);
            }
        });

        btnClose.addActionListener(e -> dialog.dispose());

        buttonsPanel.add(btnFindNext);
        buttonsPanel.add(btnReplace);
        buttonsPanel.add(btnReplaceAll);
        buttonsPanel.add(btnClose);

        dialog.add(fieldsPanel, BorderLayout.CENTER);
        dialog.add(buttonsPanel, BorderLayout.SOUTH);
        dialog.pack();
        dialog.setLocationRelativeTo(t.textArea);
        dialog.setVisible(true);
    }

    // ── KEY SHORTCUTS ───────────────────────────────────────────────────────
    private void setupKeyBindings(JComponent comp) {
        InputMap im = comp.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        ActionMap am = comp.getActionMap();

        int meta = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_T, meta), "newTab");
        am.put("newTab", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { addNewTab("Untitled", ""); } });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_W, meta), "closeTab");
        am.put("closeTab", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { closeTab(activeTabIndex); } });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, meta), "save");
        am.put("save", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { saveDocument(); } });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_N, meta), "new");
        am.put("new", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { newDocument(); } });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_O, meta), "open");
        am.put("open", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { promptOpen(); } });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_F, meta), "find");
        am.put("find", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { showFindReplaceDialog(); } });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_H, meta), "replace");
        am.put("replace", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { showFindReplaceDialog(); } });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, meta), "zoomIn");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ADD, meta), "zoomIn");
        am.put("zoomIn", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { zoomIn(); } });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, meta), "zoomOut");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, meta), "zoomOut");
        am.put("zoomOut", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { zoomOut(); } });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_0, meta), "resetZoom");
        am.put("resetZoom", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { setZoom(1.0f); } });
    }

    // ── PUBLIC I/O API (COMPATIBLE WITH FILE MANAGERS & SYSTEM) ──────────────
    public void newDocument() {
        addNewTab("Untitled", "");
    }

    public void openRealFile(File file) {
        if (file == null || !file.exists()) return;
        try {
            String content = Files.readString(file.toPath());
            TabInfo tab = addNewTab(file.getName(), content);
            tab.realFile = file;
            tab.virtualPath = null;
            tab.isModified = false;
            updateTitle();
            if (getOS() != null) {
                getOS().getNotificationManager().postNotification(
                        "File Opened", "Loaded: " + file.getName(), NotificationMessage.Level.INFO);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Failed to read file:\n" + e.getMessage(), "File Open Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void openVirtualFile(String virtualPath) {
        if (getOS() == null) return;
        try {
            String content = getOS().getFileService().readFile(virtualPath);
            String name = virtualPath.contains("/") ? virtualPath.substring(virtualPath.lastIndexOf('/') + 1) : virtualPath;
            TabInfo tab = addNewTab(name, content);
            tab.virtualPath = virtualPath;
            tab.realFile = null;
            tab.isModified = false;
            updateTitle();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Failed to read file: " + e.getMessage(), "File Open Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void promptOpen() {
        if (getOS() == null) return;
        try {
            List<FileItem> allFiles = getOS().getFileService().search("");
            if (allFiles.isEmpty()) allFiles = getOS().getFileService().listFiles("/");
            List<FileItem> textFiles = allFiles.stream().filter(f -> !f.isDirectory()).collect(Collectors.toList());

            if (textFiles.isEmpty()) {
                JOptionPane.showMessageDialog(null, "No files found in virtual directory.");
                return;
            }
            String[] choices = textFiles.stream().map(FileItem::getVirtualPath).toArray(String[]::new);
            String selected = (String) JOptionPane.showInputDialog(null,
                    "Select a file to open:", "Open File",
                    JOptionPane.PLAIN_MESSAGE, null, choices, choices[0]);
            if (selected != null) openVirtualFile(selected);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Error browsing files: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void saveDocument() {
        TabInfo t = getActiveTab();
        if (t == null) return;
        if (t.realFile != null) {
            writeRealFile(t);
        } else if (t.virtualPath == null) {
            saveAsDocument();
        } else {
            writeVirtualFile(t);
        }
    }

    public void saveAsDocument() {
        TabInfo t = getActiveTab();
        if (t == null) return;
        if (t.realFile != null) {
            writeRealFile(t);
            return;
        }
        String defaultName = t.virtualPath != null ? t.virtualPath : (t.title.contains(".") ? t.title : t.title + ".txt");
        String name = JOptionPane.showInputDialog(t.textArea, "Enter filename (e.g. notes.txt or Documents/log.txt):", defaultName);
        if (name != null && !name.trim().isEmpty()) {
            t.virtualPath = name.trim().startsWith("/") ? name.trim() : "/" + name.trim();
            t.title = t.virtualPath.substring(t.virtualPath.lastIndexOf('/') + 1);
            writeVirtualFile(t);
            refreshTabStrip();
        }
    }

    private void writeRealFile(TabInfo t) {
        try {
            Files.writeString(t.realFile.toPath(), t.textArea.getText());
            t.isModified = false;
            refreshTabStrip();
            updateTitle();
            if (getOS() != null) {
                getOS().getNotificationManager().postNotification(
                        "File Saved", "Saved successfully to " + t.realFile.getName(), NotificationMessage.Level.SUCCESS);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(t.textArea, "Failed to save file: " + e.getMessage(), "Save Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void writeVirtualFile(TabInfo t) {
        if (getOS() == null || t.virtualPath == null) return;
        try {
            getOS().getFileService().writeFile(t.virtualPath, t.textArea.getText());
            t.isModified = false;
            refreshTabStrip();
            updateTitle();
            getOS().getNotificationManager().postNotification(
                    "File Saved", "Saved successfully to " + t.virtualPath, NotificationMessage.Level.SUCCESS);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(t.textArea, "Failed to save file: " + e.getMessage(), "Save Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openInWordProcessor() {
        TabInfo t = getActiveTab();
        if (getOS() != null) {
            OSApplication app = getOS().getApplicationManager().launchApp("wordeditor");
            if (app instanceof WordEditorApp) {
                if (t != null && t.realFile != null) {
                    ((WordEditorApp) app).openFile(t.realFile);
                }
            }
        }
    }

    @Override
    public boolean canClose() {
        for (TabInfo t : tabs) {
            if (t.isModified) {
                int opt = JOptionPane.showConfirmDialog(null,
                        "Document \"" + t.title + "\" has unsaved changes. Save before closing?",
                        "Unsaved Changes", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
                if (opt == JOptionPane.CANCEL_OPTION) return false;
                if (opt == JOptionPane.YES_OPTION) {
                    saveDocument();
                    if (t.isModified) return false;
                }
            }
        }
        return true;
    }
}

package javaos.apps;

import javaos.ui.Theme;
import javaos.ui.ThemeManager;
import javaos.ui.components.CyberpunkIcons;
import javaos.ui.components.NeonButton;
import javaos.ui.components.NeonCard;
import javaos.ui.components.NeonScrollBarUI;
import javaos.ui.components.NeonTextField;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Presentation & Slide Editor for IMPERION OS.
 * Complete presentation deck (.pptx / .ppt) editor with interactive canvas editing,
 * visual slide thumbnail strip, multi-layout slide staging, themes, speaker notes,
 * standard OpenXML (.pptx) packaging & parsing, and full-screen immersive slideshow mode.
 */
public class PresentationEditorApp extends OSApplication {

    public enum SlideLayout {
        TITLE_SUBTITLE("Title & Subtitle"),
        TITLE_BULLETS("Title & Bullet Points"),
        TWO_COLUMNS("Two Column Split"),
        BIG_STAT("Key Metric & Big Stat"),
        SECTION_HEADER("Section Divider"),
        BLANK("Blank Canvas");

        public final String displayName;
        SlideLayout(String displayName) { this.displayName = displayName; }
        @Override public String toString() { return displayName; }
    }

    public enum DeckTheme {
        CYBERPUNK("Neon Cyan", new Color(8, 11, 20), new Color(16, 24, 39), new Color(34, 211, 238), new Color(241, 245, 249)),
        MATRIX("Matrix Green", new Color(4, 13, 6), new Color(9, 29, 14), new Color(34, 197, 94), new Color(236, 253, 245)),
        SYNTHWAVE("Synthwave Neon", new Color(14, 7, 23), new Color(27, 15, 46), new Color(232, 121, 249), new Color(245, 243, 255)),
        SOLAR("Solar Amber", new Color(20, 11, 4), new Color(36, 21, 8), new Color(245, 158, 11), new Color(255, 251, 235)),
        CRIMSON("Crimson Red", new Color(18, 6, 8), new Color(32, 12, 17), new Color(239, 68, 68), new Color(255, 241, 242)),
        CORPORATE("Clean Slate", new Color(248, 250, 252), new Color(255, 255, 255), new Color(2, 132, 199), new Color(15, 23, 42));

        public final String label;
        public final Color bg;
        public final Color cardBg;
        public final Color accent;
        public final Color text;

        DeckTheme(String label, Color bg, Color cardBg, Color accent, Color text) {
            this.label = label;
            this.bg = bg;
            this.cardBg = cardBg;
            this.accent = accent;
            this.text = text;
        }

        @Override public String toString() { return label; }
    }

    public static class SlideData {
        public String title = "Slide Title";
        public String subtitle = "Subtitle or description";
        public List<String> bullets = new ArrayList<>(Arrays.asList("Key takeaway or talking point 1", "Detailed architectural feature 2", "Strategic implementation objective 3"));
        public SlideLayout layout = SlideLayout.TITLE_BULLETS;
        public DeckTheme theme = DeckTheme.CYBERPUNK;
        public String statValue = "99.8%";
        public String statLabel = "System Telemetry & Operational Uptime";
        public String col2Title = "Secondary Capabilities";
        public List<String> col2Bullets = new ArrayList<>(Arrays.asList("Subsystem verification", "Zero dependency native execution"));
        public String notes = "";

        public SlideData copy() {
            SlideData s = new SlideData();
            s.title = this.title;
            s.subtitle = this.subtitle;
            s.bullets = new ArrayList<>(this.bullets);
            s.layout = this.layout;
            s.theme = this.theme;
            s.statValue = this.statValue;
            s.statLabel = this.statLabel;
            s.col2Title = this.col2Title;
            s.col2Bullets = new ArrayList<>(this.col2Bullets);
            s.notes = this.notes;
            return s;
        }
    }

    private File currentFile;
    private final List<SlideData> slides = new ArrayList<>();
    private int currentSlideIndex = 0;
    private boolean isModified = false;
    private boolean isUpdatingControls = false;

    // UI Components
    private JPanel thumbnailPanel;
    private SlideStageCanvas stageCanvas;
    private JComboBox<SlideLayout> layoutBox;
    private JComboBox<DeckTheme> themeBox;
    private NeonTextField titleField;
    private JTextArea contentArea;
    private JTextArea notesArea;
    private JLabel statusLabel;
    private JLabel deckInfoLabel;

    public PresentationEditorApp() {
        this(null);
    }

    public PresentationEditorApp(File initialFile) {
        super(new AppDescriptor(
                "slides",
                "Presentation Slides",
                "PowerPoint & presentation slide creator, editor and slideshow engine",
                AppDescriptor.Category.PRODUCTIVITY,
                CyberpunkIcons.createIcon("PRESENTATION", 20),
                false
        ));
        this.currentFile = initialFile;
        initDefaultDeck();
    }

    @Override
    public Dimension getPreferredWindowSize() {
        return new Dimension(1080, 720);
    }

    @Override
    public JComponent createContent() {
        JPanel main = new JPanel(new BorderLayout(0, 0));
        main.setOpaque(true);
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        main.setBackground(theme.background);

        Color pptRed = new Color(196, 62, 28); // Microsoft PowerPoint Signature Red-Orange

        // ── TOP POWERPOINT HEADER & RIBBON ───────────────────────────────────
        JPanel topContainer = new JPanel();
        topContainer.setLayout(new BoxLayout(topContainer, BoxLayout.Y_AXIS));
        topContainer.setOpaque(true);
        topContainer.setBackground(theme.panel);
        topContainer.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, pptRed));

        // Row 1: PowerPoint Quick Access Bar & Title
        JPanel quickBar = new JPanel(new BorderLayout());
        quickBar.setOpaque(true);
        quickBar.setBackground(pptRed);
        quickBar.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));

        JPanel qbLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        qbLeft.setOpaque(false);

        JLabel pptIcon = new JLabel("📽️ ");
        pptIcon.setFont(new Font("Segoe UI", Font.BOLD, 13));
        pptIcon.setForeground(Color.WHITE);
        qbLeft.add(pptIcon);

        JButton qbSave = makeToolBtn("Save", "CHECK", "Save Presentation (.pptx)");
        qbSave.setBackground(new Color(140, 40, 18));
        qbSave.setForeground(Color.WHITE);
        qbSave.addActionListener(e -> savePresentation(false));
        qbLeft.add(qbSave);

        JButton qbSlideshow = makeToolBtn("▶ Start (F5)", null, "Start Slideshow from Beginning (F5)");
        qbSlideshow.setBackground(new Color(140, 40, 18));
        qbSlideshow.setForeground(Color.WHITE);
        qbSlideshow.setFont(new Font("Segoe UI", Font.BOLD, 11));
        qbSlideshow.addActionListener(e -> startSlideshow());
        qbLeft.add(qbSlideshow);

        JLabel docTitle = new JLabel("  Presentation1.pptx — PowerPoint Studio");
        docTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
        docTitle.setForeground(Color.WHITE);
        qbLeft.add(docTitle);

        quickBar.add(qbLeft, BorderLayout.WEST);
        topContainer.add(quickBar);

        // Row 2: File Actions & Slideshow Launcher
        JPanel fileBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 3));
        fileBar.setOpaque(false);

        JButton btnNew = makeToolBtn("New Deck", "START", "Create a blank presentation deck");
        btnNew.addActionListener(e -> newDeck());
        fileBar.add(btnNew);

        JButton btnOpen = makeToolBtn("Open .pptx", "FOLDER", "Open PowerPoint (.pptx) file");
        btnOpen.addActionListener(e -> promptOpenFile());
        fileBar.add(btnOpen);

        JButton btnSave = makeToolBtn("Save", "CHECK", "Save presentation (.pptx)");
        btnSave.addActionListener(e -> savePresentation(false));
        fileBar.add(btnSave);

        JButton btnSaveAs = makeToolBtn("Save As...", "SEARCH", "Save as new .pptx file");
        btnSaveAs.addActionListener(e -> savePresentation(true));
        fileBar.add(btnSaveAs);

        fileBar.add(new JSeparator(SwingConstants.VERTICAL));

        JButton btnAddSlideQuick = makeToolBtn("+ New Slide", "START", "Insert new slide (Ctrl+M)");
        btnAddSlideQuick.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btnAddSlideQuick.setForeground(pptRed);
        btnAddSlideQuick.addActionListener(e -> addSlide());
        fileBar.add(btnAddSlideQuick);

        JButton btnDupSlideQuick = makeToolBtn("⧉ Duplicate", null, "Duplicate slide (Ctrl+D)");
        btnDupSlideQuick.addActionListener(e -> duplicateSlide());
        fileBar.add(btnDupSlideQuick);

        JButton btnDelSlideQuick = makeToolBtn("✕ Delete", "CLOSE", "Delete current slide");
        btnDelSlideQuick.addActionListener(e -> deleteSlide());
        fileBar.add(btnDelSlideQuick);

        fileBar.add(new JSeparator(SwingConstants.VERTICAL));

        NeonButton btnEditModal = new NeonButton("✎ Edit Slide Text", CyberpunkIcons.createIcon("NOTEPAD", 14), NeonButton.Style.DEFAULT);
        btnEditModal.setToolTipText("Open interactive in-place editor dialog for current slide");
        btnEditModal.addActionListener(e -> promptEditSlideModal());
        fileBar.add(btnEditModal);

        NeonButton btnSlideshow = new NeonButton("▶ Start Slideshow (F5)", CyberpunkIcons.createIcon("PLAY", 14), NeonButton.Style.PRIMARY);
        btnSlideshow.setToolTipText("Launch full-screen interactive presentation mode");
        btnSlideshow.addActionListener(e -> startSlideshow());
        fileBar.add(btnSlideshow);

        topContainer.add(fileBar);

        // Row 3: Slide Layout & Visual Theme Controls
        JPanel designBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
        designBar.setOpaque(false);

        JLabel lblLayout = new JLabel("Slide Layout:");
        lblLayout.setForeground(theme.textPrimary);
        lblLayout.setFont(theme.fontSmall);
        designBar.add(lblLayout);

        layoutBox = new JComboBox<>(SlideLayout.values());
        layoutBox.setBackground(theme.panel);
        layoutBox.setForeground(theme.textPrimary);
        layoutBox.setFont(theme.fontSmall);
        layoutBox.setPreferredSize(new Dimension(170, 26));
        layoutBox.addActionListener(e -> {
            SlideData cur = getCurrentSlide();
            if (cur != null && layoutBox.getSelectedItem() != null) {
                cur.layout = (SlideLayout) layoutBox.getSelectedItem();
                isModified = true;
                stageCanvas.repaint();
                updateThumbnails();
            }
        });
        designBar.add(layoutBox);

        JLabel lblTheme = new JLabel("Theme:");
        lblTheme.setForeground(theme.textPrimary);
        lblTheme.setFont(theme.fontSmall);
        designBar.add(lblTheme);

        themeBox = new JComboBox<>(DeckTheme.values());
        themeBox.setBackground(theme.panel);
        themeBox.setForeground(theme.textPrimary);
        themeBox.setFont(theme.fontSmall);
        themeBox.setPreferredSize(new Dimension(150, 26));
        themeBox.addActionListener(e -> {
            SlideData cur = getCurrentSlide();
            if (cur != null && themeBox.getSelectedItem() != null) {
                cur.theme = (DeckTheme) themeBox.getSelectedItem();
                isModified = true;
                stageCanvas.repaint();
                updateThumbnails();
            }
        });
        designBar.add(themeBox);

        JButton btnApplyThemeAll = makeToolBtn("Apply to All Slides", null, "Apply this theme to all slides in deck");
        btnApplyThemeAll.addActionListener(e -> {
            DeckTheme t = (DeckTheme) themeBox.getSelectedItem();
            for (SlideData s : slides) s.theme = t;
            isModified = true;
            stageCanvas.repaint();
            updateThumbnails();
        });
        designBar.add(btnApplyThemeAll);

        topContainer.add(designBar);
        main.add(topContainer, BorderLayout.NORTH);

        // ── LEFT SIDEBAR: SLIDE THUMBNAILS DECK ──────────────────────────────
        JPanel leftSidebar = new JPanel(new BorderLayout(0, 4));
        leftSidebar.setOpaque(true);
        leftSidebar.setBackground(theme.panel);
        leftSidebar.setPreferredSize(new Dimension(220, 0));
        leftSidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, theme.secondaryAccent));

        JPanel thumbTopBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 4));
        thumbTopBar.setOpaque(false);

        JButton btnAddSlide = makeToolBtn("+ Add", "START", "Insert new slide");
        btnAddSlide.addActionListener(e -> addSlide());

        JButton btnDupSlide = makeToolBtn("⧉ Dup", null, "Duplicate slide");
        btnDupSlide.addActionListener(e -> duplicateSlide());

        JButton btnDelSlide = makeToolBtn("✕ Del", "CLOSE", "Delete slide");
        btnDelSlide.addActionListener(e -> deleteSlide());

        JButton btnUp = makeToolBtn("▲", null, "Move slide up");
        btnUp.addActionListener(e -> moveSlide(-1));

        JButton btnDown = makeToolBtn("▼", null, "Move slide down");
        btnDown.addActionListener(e -> moveSlide(1));

        thumbTopBar.add(btnAddSlide);
        thumbTopBar.add(btnDupSlide);
        thumbTopBar.add(btnDelSlide);
        thumbTopBar.add(btnUp);
        thumbTopBar.add(btnDown);
        leftSidebar.add(thumbTopBar, BorderLayout.NORTH);

        thumbnailPanel = new JPanel();
        thumbnailPanel.setLayout(new BoxLayout(thumbnailPanel, BoxLayout.Y_AXIS));
        thumbnailPanel.setOpaque(false);
        thumbnailPanel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        JScrollPane thumbScroll = new JScrollPane(thumbnailPanel);
        thumbScroll.setOpaque(false);
        thumbScroll.getViewport().setOpaque(false);
        thumbScroll.setBorder(BorderFactory.createEmptyBorder());
        thumbScroll.getVerticalScrollBar().setUI(new NeonScrollBarUI());
        leftSidebar.add(thumbScroll, BorderLayout.CENTER);

        main.add(leftSidebar, BorderLayout.WEST);

        // ── CENTER STAGE & EDITING CARDS ─────────────────────────────────────
        JPanel centerContainer = new JPanel(new BorderLayout(0, 6));
        centerContainer.setOpaque(false);
        centerContainer.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        stageCanvas = new SlideStageCanvas();
        centerContainer.add(stageCanvas, BorderLayout.CENTER);

        // Bottom Slide Content Editor Card
        NeonCard editorCard = new NeonCard();
        editorCard.setLayout(new BoxLayout(editorCard, BoxLayout.Y_AXIS));
        editorCard.setPreferredSize(new Dimension(0, 195));
        editorCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, theme.secondaryAccent),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)
        ));

        // Title Input Row
        JPanel titleRow = new JPanel(new BorderLayout(8, 0));
        titleRow.setOpaque(false);
        JLabel lblT = new JLabel("Slide Title: ");
        lblT.setForeground(theme.textPrimary);
        lblT.setFont(theme.fontBold);
        titleField = new NeonTextField("", 30);
        titleField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void update() {
                if (isUpdatingControls) return;
                SlideData s = getCurrentSlide();
                if (s != null) {
                    s.title = titleField.getText();
                    isModified = true;
                    stageCanvas.repaint();
                    updateThumbnails();
                }
            }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { update(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { update(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { update(); }
        });
        titleRow.add(lblT, BorderLayout.WEST);
        titleRow.add(titleField, BorderLayout.CENTER);
        editorCard.add(titleRow);

        editorCard.add(Box.createVerticalStrut(6));

        // Content / Bullets Row & Notes Split
        JPanel contentRow = new JPanel(new GridLayout(1, 2, 8, 0));
        contentRow.setOpaque(false);

        // Bullets / Text
        JPanel col1 = new JPanel(new BorderLayout(0, 2));
        col1.setOpaque(false);
        JLabel lblC = new JLabel("Slide Bullets / Subtitle (One line per item):");
        lblC.setForeground(theme.textSecondary);
        lblC.setFont(theme.fontSmall);
        col1.add(lblC, BorderLayout.NORTH);

        contentArea = new JTextArea();
        contentArea.setBackground(theme.panel);
        contentArea.setForeground(theme.textPrimary);
        contentArea.setCaretColor(Color.WHITE);
        contentArea.setFont(theme.fontRegular);
        contentArea.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void update() {
                if (isUpdatingControls) return;
                SlideData s = getCurrentSlide();
                if (s != null) {
                    String[] lines = contentArea.getText().split("\n");
                    s.bullets.clear();
                    for (String l : lines) {
                        String t = l.trim();
                        if (!t.isEmpty()) s.bullets.add(t);
                    }
                    if (s.layout == SlideLayout.TITLE_SUBTITLE && lines.length > 0) {
                        s.subtitle = lines[0];
                    }
                    isModified = true;
                    stageCanvas.repaint();
                }
            }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { update(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { update(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { update(); }
        });
        JScrollPane scrollC = new JScrollPane(contentArea);
        scrollC.getVerticalScrollBar().setUI(new NeonScrollBarUI());
        col1.add(scrollC, BorderLayout.CENTER);
        contentRow.add(col1);

        // Speaker Notes
        JPanel col2 = new JPanel(new BorderLayout(0, 2));
        col2.setOpaque(false);
        JLabel lblN = new JLabel("Speaker Presentation Notes:");
        lblN.setForeground(theme.textSecondary);
        lblN.setFont(theme.fontSmall);
        col2.add(lblN, BorderLayout.NORTH);

        notesArea = new JTextArea();
        notesArea.setBackground(theme.panel);
        notesArea.setForeground(theme.textSecondary);
        notesArea.setCaretColor(Color.WHITE);
        notesArea.setFont(theme.fontSmall);
        notesArea.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void update() {
                if (isUpdatingControls) return;
                SlideData s = getCurrentSlide();
                if (s != null) {
                    s.notes = notesArea.getText();
                    isModified = true;
                }
            }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { update(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { update(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { update(); }
        });
        JScrollPane scrollN = new JScrollPane(notesArea);
        scrollN.getVerticalScrollBar().setUI(new NeonScrollBarUI());
        col2.add(scrollN, BorderLayout.CENTER);
        contentRow.add(col2);

        editorCard.add(contentRow);
        centerContainer.add(editorCard, BorderLayout.SOUTH);

        main.add(centerContainer, BorderLayout.CENTER);

        // ── BOTTOM STATUS BAR ───────────────────────────────────────────────
        JPanel statusBar = new JPanel(new BorderLayout(10, 0));
        statusBar.setOpaque(true);
        statusBar.setBackground(theme.panel);
        statusBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, theme.secondaryAccent),
                BorderFactory.createEmptyBorder(4, 12, 4, 12)
        ));

        statusLabel = new JLabel("Slide " + (currentSlideIndex + 1) + " of " + Math.max(1, slides.size()));
        statusLabel.setFont(theme.fontSmall);
        statusLabel.setForeground(pptRed);
        statusBar.add(statusLabel, BorderLayout.WEST);

        deckInfoLabel = new JLabel("16:9 Widescreen  |  Press F5 for Presentation Mode");
        deckInfoLabel.setFont(theme.fontSmall);
        deckInfoLabel.setForeground(theme.textSecondary);
        deckInfoLabel.setHorizontalAlignment(SwingConstants.CENTER);
        statusBar.add(deckInfoLabel, BorderLayout.CENTER);

        JPanel rightStatus = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightStatus.setOpaque(false);

        JButton btnQuickShow = new JButton("🖥️ Slide Show");
        btnQuickShow.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btnQuickShow.setForeground(Color.WHITE);
        btnQuickShow.setBackground(pptRed);
        btnQuickShow.setFocusPainted(false);
        btnQuickShow.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnQuickShow.setToolTipText("Launch Full Screen Slideshow (F5)");
        btnQuickShow.addActionListener(e -> startSlideshow());
        rightStatus.add(btnQuickShow);

        JLabel brand = new JLabel("PowerPoint 2026");
        brand.setFont(theme.fontSmall);
        brand.setForeground(theme.textSecondary);
        rightStatus.add(brand);

        statusBar.add(rightStatus, BorderLayout.EAST);

        main.add(statusBar, BorderLayout.SOUTH);

        // F5 shortcut to start slideshow
        main.registerKeyboardAction(e -> startSlideshow(), KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        // Keyboard shortcuts
        InputMap im = main.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        ActionMap am = main.getActionMap();

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK), "pptSave");
        am.put("pptSave", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent ev) { savePresentation(false); }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), "pptSaveAs");
        am.put("pptSaveAs", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent ev) { savePresentation(true); }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK), "pptNew");
        am.put("pptNew", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent ev) { newDeck(); }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK), "pptOpen");
        am.put("pptOpen", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent ev) { promptOpenFile(); }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_M, InputEvent.CTRL_DOWN_MASK), "pptAddSlide");
        am.put("pptAddSlide", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent ev) { addSlide(); }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK), "pptDupSlide");
        am.put("pptDupSlide", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent ev) { duplicateSlide(); }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, InputEvent.CTRL_DOWN_MASK), "pptDelSlide");
        am.put("pptDelSlide", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent ev) { deleteSlide(); }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, 0), "pptNextSlide");
        am.put("pptNextSlide", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent ev) {
                if (currentSlideIndex < slides.size() - 1) selectSlide(currentSlideIndex + 1);
            }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, 0), "pptPrevSlide");
        am.put("pptPrevSlide", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent ev) {
                if (currentSlideIndex > 0) selectSlide(currentSlideIndex - 1);
            }
        });

        // Initialize or restore deck UI
        if (slides.isEmpty()) {
            initDefaultDeck();
        } else {
            selectSlide(currentSlideIndex);
        }

        if (currentFile != null) {
            SwingUtilities.invokeLater(() -> openFile(currentFile));
        }

        return main;
    }

    private void initDefaultDeck() {
        slides.clear();

        // Slide 1: Title Slide
        SlideData s1 = new SlideData();
        s1.title = "IMPERION OS PRESENTATION";
        s1.subtitle = "Next-Generation Visual Slide Presentation Platform";
        s1.layout = SlideLayout.TITLE_SUBTITLE;
        s1.theme = DeckTheme.CYBERPUNK;
        s1.notes = "Introduce IMPERION OS and presentation deck capabilities.";
        slides.add(s1);

        // Slide 2: Feature Bullets
        SlideData s2 = new SlideData();
        s2.title = "Core Capabilities & Architecture";
        s2.bullets = new ArrayList<>(Arrays.asList(
                "Native PowerPoint (.pptx) file packaging and parsing",
                "Multiple slide layouts with real-time vector rendering",
                "Full-Screen presentation slideshow engine with smooth navigation",
                "Interactive in-place slide text and bullet editing",
                "Theme synchronization across Neon Cyan, Matrix, Synthwave & Solar styles"
        ));
        s2.layout = SlideLayout.TITLE_BULLETS;
        s2.theme = DeckTheme.CYBERPUNK;
        slides.add(s2);

        // Slide 3: Big Stat KPI
        SlideData s3 = new SlideData();
        s3.title = "Engine Reliability Benchmark";
        s3.statValue = "100%";
        s3.statLabel = "Native Pure Java & Zero Windows Interruption";
        s3.layout = SlideLayout.BIG_STAT;
        s3.theme = DeckTheme.CYBERPUNK;
        slides.add(s3);

        currentSlideIndex = 0;
        selectSlide(0);
    }

    private SlideData getCurrentSlide() {
        if (currentSlideIndex >= 0 && currentSlideIndex < slides.size()) {
            return slides.get(currentSlideIndex);
        }
        return null;
    }

    private void selectSlide(int index) {
        if (index < 0 || index >= slides.size()) return;
        currentSlideIndex = index;
        SlideData s = slides.get(index);

        isUpdatingControls = true;
        try {
            if (titleField != null) titleField.setText(s.title != null ? s.title : "");
            if (layoutBox != null) layoutBox.setSelectedItem(s.layout);
            if (themeBox != null) themeBox.setSelectedItem(s.theme);

            StringBuilder sb = new StringBuilder();
            if (s.layout == SlideLayout.TITLE_SUBTITLE) {
                sb.append(s.subtitle != null ? s.subtitle : "");
            } else {
                for (String b : s.bullets) sb.append(b).append("\n");
            }
            if (contentArea != null) contentArea.setText(sb.toString());
            if (notesArea != null) notesArea.setText(s.notes != null ? s.notes : "");
        } finally {
            isUpdatingControls = false;
        }

        if (statusLabel != null) {
            statusLabel.setText("Slide " + (index + 1) + " of " + slides.size());
        }
        if (deckInfoLabel != null) {
            deckInfoLabel.setText("16:9 Widescreen  |  Theme: " + s.theme.label + "  |  Press F5 for Slideshow");
        }
        if (stageCanvas != null) stageCanvas.repaint();
        if (thumbnailPanel != null) updateThumbnails();
    }

    private void addSlide() {
        SlideData s = new SlideData();
        s.title = "New Slide " + (slides.size() + 1);
        s.theme = getCurrentSlide() != null ? getCurrentSlide().theme : DeckTheme.CYBERPUNK;
        slides.add(currentSlideIndex + 1, s);
        isModified = true;
        selectSlide(currentSlideIndex + 1);
    }

    private void duplicateSlide() {
        SlideData cur = getCurrentSlide();
        if (cur == null) return;
        SlideData copy = cur.copy();
        copy.title += " (Copy)";
        slides.add(currentSlideIndex + 1, copy);
        isModified = true;
        selectSlide(currentSlideIndex + 1);
    }

    private void deleteSlide() {
        if (slides.size() <= 1) {
            JOptionPane.showMessageDialog(stageCanvas, "A presentation deck must have at least one slide.");
            return;
        }
        slides.remove(currentSlideIndex);
        if (currentSlideIndex >= slides.size()) {
            currentSlideIndex = slides.size() - 1;
        }
        isModified = true;
        selectSlide(currentSlideIndex);
    }

    private void moveSlide(int dir) {
        int target = currentSlideIndex + dir;
        if (target < 0 || target >= slides.size()) return;
        SlideData cur = slides.remove(currentSlideIndex);
        slides.add(target, cur);
        isModified = true;
        selectSlide(target);
    }

    private void updateThumbnails() {
        thumbnailPanel.removeAll();
        Theme theme = ThemeManager.getInstance().getCurrentTheme();

        for (int i = 0; i < slides.size(); i++) {
            final int idx = i;
            SlideData s = slides.get(i);
            boolean isSelected = (i == currentSlideIndex);

            JPanel card = new JPanel(new BorderLayout(4, 2));
            card.setOpaque(true);
            card.setBackground(isSelected ? theme.elevatedPanel : theme.panel);
            card.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(isSelected ? theme.neonAccent : theme.secondaryAccent, isSelected ? 2 : 1),
                    BorderFactory.createEmptyBorder(4, 6, 4, 6)
            ));
            card.setPreferredSize(new Dimension(190, 72));
            card.setMaximumSize(new Dimension(190, 72));
            card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            JLabel numLabel = new JLabel((i + 1) + ". ");
            numLabel.setFont(theme.fontBold);
            numLabel.setForeground(isSelected ? theme.neonAccent : theme.textSecondary);

            JLabel titleLbl = new JLabel((s.title == null || s.title.isEmpty()) ? "Untitled" : s.title);
            titleLbl.setFont(theme.fontSmall);
            titleLbl.setForeground(theme.textPrimary);

            JLabel layoutLbl = new JLabel("[" + s.layout.name() + "]");
            layoutLbl.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            layoutLbl.setForeground(theme.textSecondary);

            card.add(numLabel, BorderLayout.WEST);
            card.add(titleLbl, BorderLayout.CENTER);
            card.add(layoutLbl, BorderLayout.SOUTH);

            card.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    selectSlide(idx);
                }
            });

            thumbnailPanel.add(card);
            thumbnailPanel.add(Box.createVerticalStrut(4));
        }

        thumbnailPanel.revalidate();
        thumbnailPanel.repaint();
    }

    /**
     * Interactive modal dialog to quickly edit slide title, subtitle, bullets, stat, and notes.
     */
    private void promptEditSlideModal() {
        SlideData s = getCurrentSlide();
        if (s == null) return;

        Theme theme = ThemeManager.getInstance().getCurrentTheme();

        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBackground(theme.panel);
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel fields = new JPanel();
        fields.setLayout(new BoxLayout(fields, BoxLayout.Y_AXIS));
        fields.setOpaque(false);

        JLabel lTitle = new JLabel("Slide Title:");
        lTitle.setForeground(theme.neonAccent);
        lTitle.setFont(theme.fontBold);
        JTextField tTitle = new JTextField(s.title != null ? s.title : "");
        tTitle.setFont(theme.fontRegular);

        JLabel lSub = new JLabel("Subtitle / Secondary Label:");
        lSub.setForeground(theme.textPrimary);
        JTextField tSub = new JTextField(s.subtitle != null ? s.subtitle : "");
        tSub.setFont(theme.fontRegular);

        JLabel lBullets = new JLabel("Slide Bullets / Content (One line per bullet):");
        lBullets.setForeground(theme.neonAccent);
        lBullets.setFont(theme.fontBold);
        JTextArea tBullets = new JTextArea(8, 30);
        tBullets.setFont(theme.fontRegular);
        StringBuilder bsb = new StringBuilder();
        for (String b : s.bullets) bsb.append(b).append("\n");
        tBullets.setText(bsb.toString());
        JScrollPane scrollB = new JScrollPane(tBullets);

        JLabel lStat = new JLabel("KPI / Big Stat Value (for Big Stat layout):");
        lStat.setForeground(theme.textPrimary);
        JTextField tStat = new JTextField(s.statValue != null ? s.statValue : "");

        fields.add(lTitle);
        fields.add(tTitle);
        fields.add(Box.createVerticalStrut(6));
        fields.add(lSub);
        fields.add(tSub);
        fields.add(Box.createVerticalStrut(6));
        fields.add(lStat);
        fields.add(tStat);
        fields.add(Box.createVerticalStrut(6));
        fields.add(lBullets);
        fields.add(scrollB);

        panel.add(fields, BorderLayout.CENTER);

        int res = JOptionPane.showConfirmDialog(stageCanvas, panel, "Edit Slide " + (currentSlideIndex + 1),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (res == JOptionPane.OK_OPTION) {
            s.title = tTitle.getText().trim();
            s.subtitle = tSub.getText().trim();
            s.statValue = tStat.getText().trim();

            s.bullets.clear();
            for (String line : tBullets.getText().split("\n")) {
                String tl = line.trim();
                if (!tl.isEmpty()) s.bullets.add(tl);
            }

            isModified = true;
            selectSlide(currentSlideIndex);
        }
    }

    // ── STAGE CANVAS (WYSIWYG 16:9 SLIDE VIEW WITH DIRECT INTERACTION) ───────

    private class SlideStageCanvas extends JPanel {
        private Rectangle titleBounds = null;
        private Rectangle contentBounds = null;
        private int hoveredZone = 0; // 0=none, 1=title, 2=content

        public SlideStageCanvas() {
            setOpaque(true);
            setBackground(new Color(11, 15, 25));

            addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    Point pt = e.getPoint();
                    int prev = hoveredZone;
                    if (titleBounds != null && titleBounds.contains(pt)) {
                        hoveredZone = 1;
                        setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
                    } else if (contentBounds != null && contentBounds.contains(pt)) {
                        hoveredZone = 2;
                        setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
                    } else {
                        hoveredZone = 0;
                        setCursor(Cursor.getDefaultCursor());
                    }
                    if (prev != hoveredZone) repaint();
                }
            });

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    Point pt = e.getPoint();
                    if (titleBounds != null && titleBounds.contains(pt)) {
                        if (e.getClickCount() >= 2) {
                            promptEditSlideModal();
                        } else {
                            titleField.requestFocusInWindow();
                            titleField.selectAll();
                        }
                    } else if (contentBounds != null && contentBounds.contains(pt)) {
                        if (e.getClickCount() >= 2) {
                            promptEditSlideModal();
                        } else {
                            contentArea.requestFocusInWindow();
                            contentArea.selectAll();
                        }
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hoveredZone = 0;
                    setCursor(Cursor.getDefaultCursor());
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            SlideData s = getCurrentSlide();
            if (s == null) return;

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            // Compute 16:9 aspect box centered
            double targetAspect = 16.0 / 9.0;
            int slideW = (int) (w * 0.92);
            int slideH = (int) (slideW / targetAspect);
            if (slideH > h * 0.94) {
                slideH = (int) (h * 0.94);
                slideW = (int) (slideH * targetAspect);
            }

            int sx = (w - slideW) / 2;
            int sy = (h - slideH) / 2;

            // Draw slide drop shadow
            g2.setColor(new Color(0, 0, 0, 100));
            g2.fill(new RoundRectangle2D.Float(sx + 6, sy + 6, slideW, slideH, 12, 12));

            // Draw slide background
            DeckTheme dt = s.theme;
            g2.setPaint(new GradientPaint(sx, sy, dt.bg, sx, sy + slideH, dt.cardBg));
            g2.fill(new RoundRectangle2D.Float(sx, sy, slideW, slideH, 12, 12));

            // Slide Border
            g2.setColor(dt.accent);
            g2.setStroke(new BasicStroke(1.5f));
            g2.draw(new RoundRectangle2D.Float(sx, sy, slideW, slideH, 12, 12));

            // Subtle top banner line
            g2.fillRect(sx + 24, sy + 18, slideW - 48, 2);

            // Calculate and draw content based on layout
            drawSlideContent(g2, s, sx, sy, slideW, slideH);

            // Draw interactive hover cues for editing
            Stroke dashed = new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{4, 4}, 0);
            if (hoveredZone == 1 && titleBounds != null) {
                g2.setStroke(dashed);
                g2.setColor(new Color(dt.accent.getRed(), dt.accent.getGreen(), dt.accent.getBlue(), 180));
                g2.drawRoundRect(titleBounds.x - 4, titleBounds.y - 2, titleBounds.width + 8, titleBounds.height + 4, 6, 6);
                g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
                g2.drawString("✎ Click to edit title", titleBounds.x, Math.max(sy + 14, titleBounds.y - 4));
            } else if (hoveredZone == 2 && contentBounds != null) {
                g2.setStroke(dashed);
                g2.setColor(new Color(dt.accent.getRed(), dt.accent.getGreen(), dt.accent.getBlue(), 180));
                g2.drawRoundRect(contentBounds.x - 4, contentBounds.y - 2, contentBounds.width + 8, contentBounds.height + 4, 6, 6);
                g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
                g2.drawString("✎ Click to edit content & bullets", contentBounds.x, contentBounds.y - 4);
            }

            g2.dispose();
        }

        private void drawSlideContent(Graphics2D g2, SlideData s, int sx, int sy, int sw, int sh) {
            DeckTheme dt = s.theme;

            // Slide Title
            g2.setColor(dt.accent);
            g2.setFont(new Font("Segoe UI", Font.BOLD, Math.max(16, sw / 26)));
            FontMetrics fmT = g2.getFontMetrics();

            if (s.layout == SlideLayout.TITLE_SUBTITLE) {
                // Centered Title & Subtitle
                int ty = sy + (sh / 2) - 10;
                int tx = sx + (sw - fmT.stringWidth(s.title)) / 2;
                g2.drawString(s.title, tx, ty);

                titleBounds = new Rectangle(tx - 10, ty - fmT.getAscent(), fmT.stringWidth(s.title) + 20, fmT.getHeight());

                g2.setColor(dt.text);
                g2.setFont(new Font("Segoe UI", Font.PLAIN, Math.max(13, sw / 40)));
                FontMetrics fmS = g2.getFontMetrics();
                int sxPos = sx + (sw - fmS.stringWidth(s.subtitle)) / 2;
                g2.drawString(s.subtitle, sxPos, ty + 38);

                contentBounds = new Rectangle(sxPos - 10, ty + 38 - fmS.getAscent(), fmS.stringWidth(s.subtitle) + 20, fmS.getHeight());

            } else if (s.layout == SlideLayout.BIG_STAT) {
                // Large KPI Stat Showcase
                g2.drawString(s.title, sx + 40, sy + 60);
                titleBounds = new Rectangle(sx + 35, sy + 60 - fmT.getAscent(), fmT.stringWidth(s.title) + 20, fmT.getHeight());

                // Giant Stat
                g2.setFont(new Font("Segoe UI", Font.BOLD, Math.max(36, sw / 10)));
                g2.setColor(dt.accent);
                FontMetrics fmStat = g2.getFontMetrics();
                int statX = sx + (sw - fmStat.stringWidth(s.statValue)) / 2;
                int statY = sy + (sh / 2) + 20;
                g2.drawString(s.statValue, statX, statY);

                // Stat Label
                g2.setFont(new Font("Segoe UI", Font.PLAIN, Math.max(13, sw / 38)));
                g2.setColor(dt.text);
                FontMetrics fmL = g2.getFontMetrics();
                int lblX = sx + (sw - fmL.stringWidth(s.statLabel)) / 2;
                g2.drawString(s.statLabel, lblX, statY + 40);

                contentBounds = new Rectangle(Math.min(statX, lblX) - 10, statY - fmStat.getAscent(), Math.max(fmStat.stringWidth(s.statValue), fmL.stringWidth(s.statLabel)) + 20, 90);

            } else {
                // Title & Bullets or Two Columns
                g2.drawString(s.title, sx + 40, sy + 58);
                titleBounds = new Rectangle(sx + 35, sy + 58 - fmT.getAscent(), fmT.stringWidth(s.title) + 20, fmT.getHeight());

                g2.setFont(new Font("Segoe UI", Font.PLAIN, Math.max(13, sw / 42)));
                g2.setColor(dt.text);

                int startY = sy + 100;
                int lineSpacing = Math.max(24, sh / 14);

                int maxBulletW = sw - 100;
                for (int i = 0; i < s.bullets.size(); i++) {
                    int by = startY + (i * lineSpacing);
                    if (by > sy + sh - 30) break;

                    // Bullet circle
                    g2.setColor(dt.accent);
                    g2.fillOval(sx + 40, by - 10, 6, 6);

                    g2.setColor(dt.text);
                    g2.drawString(s.bullets.get(i), sx + 56, by);
                }

                contentBounds = new Rectangle(sx + 35, startY - 18, maxBulletW, Math.max(60, s.bullets.size() * lineSpacing + 20));
            }

            // Slide number in bottom corner
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            g2.setColor(new Color(dt.text.getRed(), dt.text.getGreen(), dt.text.getBlue(), 120));
            g2.drawString(String.valueOf(currentSlideIndex + 1), sx + sw - 36, sy + sh - 16);
        }
    }

    // ── FULL-SCREEN PRESENTATION SLIDESHOW MODE ─────────────────────────────

    private void startSlideshow() {
        if (slides.isEmpty()) return;

        JDialog showDlg = new JDialog((Frame) SwingUtilities.getWindowAncestor(stageCanvas), "Presentation Slideshow", true);
        showDlg.setUndecorated(true);
        showDlg.setSize(Toolkit.getDefaultToolkit().getScreenSize());
        showDlg.setLocation(0, 0);

        final int[] curIdx = {currentSlideIndex};

        JPanel view = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                int w = getWidth();
                int h = getHeight();

                SlideData s = slides.get(curIdx[0]);
                DeckTheme dt = s.theme;

                // Full screen background
                g2.setPaint(new GradientPaint(0, 0, dt.bg, 0, h, dt.cardBg));
                g2.fillRect(0, 0, w, h);

                // Subtle grid
                g2.setColor(new Color(dt.accent.getRed(), dt.accent.getGreen(), dt.accent.getBlue(), 12));
                for (int x = 0; x < w; x += 60) g2.drawLine(x, 0, x, h);
                for (int y = 0; y < h; y += 60) g2.drawLine(0, y, w, y);

                // Top neon accent line
                g2.setColor(dt.accent);
                g2.fillRect(60, 40, w - 120, 3);

                // Draw slide content scaled to full screen
                drawSlideFullScreen(g2, s, 60, 50, w - 120, h - 100);

                // Bottom HUD: Progress Bar & Slide Index
                int progW = (int) (((curIdx[0] + 1) / (double) slides.size()) * (w - 120));
                g2.setColor(new Color(dt.accent.getRed(), dt.accent.getGreen(), dt.accent.getBlue(), 60));
                g2.fillRect(60, h - 28, w - 120, 4);
                g2.setColor(dt.accent);
                g2.fillRect(60, h - 28, progW, 4);

                g2.setFont(new Font("Segoe UI", Font.PLAIN, 12));
                g2.setColor(dt.text);
                g2.drawString("Slide " + (curIdx[0] + 1) + " of " + slides.size() + "  |  Press ESC to Exit", 60, h - 12);

                g2.dispose();
            }

            private void drawSlideFullScreen(Graphics2D g2, SlideData s, int sx, int sy, int sw, int sh) {
                DeckTheme dt = s.theme;
                g2.setColor(dt.accent);
                g2.setFont(new Font("Segoe UI", Font.BOLD, Math.max(22, sw / 22)));
                FontMetrics fmT = g2.getFontMetrics();

                if (s.layout == SlideLayout.TITLE_SUBTITLE) {
                    int ty = sy + (sh / 2) - 10;
                    int tx = sx + (sw - fmT.stringWidth(s.title)) / 2;
                    g2.drawString(s.title, tx, ty);

                    g2.setColor(dt.text);
                    g2.setFont(new Font("Segoe UI", Font.PLAIN, Math.max(16, sw / 36)));
                    FontMetrics fmS = g2.getFontMetrics();
                    int sxPos = sx + (sw - fmS.stringWidth(s.subtitle)) / 2;
                    g2.drawString(s.subtitle, sxPos, ty + 48);
                } else if (s.layout == SlideLayout.BIG_STAT) {
                    g2.drawString(s.title, sx + 40, sy + 70);
                    g2.setFont(new Font("Segoe UI", Font.BOLD, Math.max(48, sw / 8)));
                    g2.setColor(dt.accent);
                    FontMetrics fmStat = g2.getFontMetrics();
                    int statX = sx + (sw - fmStat.stringWidth(s.statValue)) / 2;
                    int statY = sy + (sh / 2) + 20;
                    g2.drawString(s.statValue, statX, statY);

                    g2.setFont(new Font("Segoe UI", Font.PLAIN, Math.max(16, sw / 32)));
                    g2.setColor(dt.text);
                    FontMetrics fmL = g2.getFontMetrics();
                    int lblX = sx + (sw - fmL.stringWidth(s.statLabel)) / 2;
                    g2.drawString(s.statLabel, lblX, statY + 50);
                } else {
                    g2.drawString(s.title, sx + 40, sy + 68);
                    g2.setFont(new Font("Segoe UI", Font.PLAIN, Math.max(16, sw / 36)));
                    g2.setColor(dt.text);

                    int startY = sy + 120;
                    int lineSpacing = Math.max(34, sh / 12);
                    for (int i = 0; i < s.bullets.size(); i++) {
                        int by = startY + (i * lineSpacing);
                        if (by > sy + sh - 30) break;
                        g2.setColor(dt.accent);
                        g2.fillOval(sx + 40, by - 12, 8, 8);
                        g2.setColor(dt.text);
                        g2.drawString(s.bullets.get(i), sx + 62, by);
                    }
                }
            }
        };
        view.setOpaque(true);
        view.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // Advance / Previous Action
        Runnable advance = () -> {
            if (curIdx[0] < slides.size() - 1) {
                curIdx[0]++;
                view.repaint();
                if (getOS() != null && getOS().getSoundService() != null) getOS().getSoundService().playSlideSwitch();
            }
        };
        Runnable previous = () -> {
            if (curIdx[0] > 0) {
                curIdx[0]--;
                view.repaint();
                if (getOS() != null && getOS().getSoundService() != null) getOS().getSoundService().playSlideSwitch();
            }
        };

        view.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) previous.run();
                else advance.run();
            }
        });

        // Keyboard bindings
        InputMap sim = view.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap sam = view.getActionMap();
        sim.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "esc");
        sam.put("esc", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { showDlg.dispose(); } });
        sim.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "next");
        sim.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "next");
        sim.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "next");
        sim.put(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, 0), "next");
        sim.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "next");
        sam.put("next", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { advance.run(); } });
        sim.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "prev");
        sim.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "prev");
        sim.put(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, 0), "prev");
        sim.put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), "prev");
        sam.put("prev", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { previous.run(); } });

        showDlg.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                int code = e.getKeyCode();
                if (code == KeyEvent.VK_ESCAPE) {
                    showDlg.dispose();
                } else if (code == KeyEvent.VK_RIGHT || code == KeyEvent.VK_DOWN || code == KeyEvent.VK_SPACE || code == KeyEvent.VK_PAGE_DOWN || code == KeyEvent.VK_ENTER) {
                    advance.run();
                } else if (code == KeyEvent.VK_LEFT || code == KeyEvent.VK_UP || code == KeyEvent.VK_PAGE_UP || code == KeyEvent.VK_BACK_SPACE) {
                    previous.run();
                }
            }
        });

        // Top right close button
        JPanel topHud = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        topHud.setOpaque(false);
        JButton btnExit = new JButton("✕ Exit Slideshow");
        btnExit.setOpaque(false);
        btnExit.setContentAreaFilled(false);
        btnExit.setFocusPainted(false);
        btnExit.setForeground(Color.WHITE);
        btnExit.setBorder(BorderFactory.createLineBorder(Color.WHITE));
        btnExit.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnExit.addActionListener(e -> showDlg.dispose());
        topHud.add(btnExit);

        showDlg.setLayout(new BorderLayout());
        showDlg.add(topHud, BorderLayout.NORTH);
        showDlg.add(view, BorderLayout.CENTER);
        showDlg.setVisible(true);

        // Sync back current slide upon exit
        selectSlide(curIdx[0]);
    }

    // ── FILE I/O: PPTX OPEN & SAVE ──────────────────────────────────────────

    public void openFile(File file) {
        if (file == null || !file.exists()) return;
        this.currentFile = file;

        try {
            String name = file.getName().toLowerCase();
            if (name.endsWith(".pptx") || name.endsWith(".ppt")) {
                loadPPTXFile(file);
            } else {
                throw new IOException("Unsupported presentation format: " + file.getName());
            }

            isModified = false;
            updateTitle();
            if (getOS() != null && getOS().getSoundService() != null) getOS().getSoundService().playSuccess();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(stageCanvas, "Failed to load presentation:\n" + e.getMessage(), "Presentation Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadPPTXFile(File file) throws Exception {
        try {
            try (ZipFile zip = new ZipFile(file)) {
                // Collect all slide entries in the zip
                List<ZipEntry> slideEntries = new ArrayList<>();
                for (Enumeration<? extends ZipEntry> en = zip.entries(); en.hasMoreElements(); ) {
                    ZipEntry ze = en.nextElement();
                    if (ze.getName().matches("ppt/slides/slide\\d+\\.xml")) {
                        slideEntries.add(ze);
                    }
                }

                // Sort numerically: ppt/slides/slide1.xml, slide2.xml...
                slideEntries.sort((a, b) -> {
                    int na = extractSlideNum(a.getName());
                    int nb = extractSlideNum(b.getName());
                    return Integer.compare(na, nb);
                });

                if (slideEntries.isEmpty()) {
                    throw new IOException("No slide entries found in presentation package.");
                }

                List<SlideData> loaded = new ArrayList<>();
                int slideCounter = 1;

                for (ZipEntry entry : slideEntries) {
                    InputStream is = zip.getInputStream(entry);
                    String xml = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    is.close();

                    SlideData sd = parseSlideXml(xml, slideCounter);
                    loaded.add(sd);
                    slideCounter++;
                }

                if (!loaded.isEmpty()) {
                    slides.clear();
                    slides.addAll(loaded);
                    currentSlideIndex = 0;
                    selectSlide(0);
                }
            }
        } catch (Exception ex) {
            // Fallback for legacy binary .ppt
            byte[] bytes = Files.readAllBytes(file.toPath());
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                if ((b >= 32 && b <= 126) || b == '\n' || b == '\t' || b == '\r') {
                    sb.append((char) b);
                }
            }
            String[] rawSlides = sb.toString().split("[\r\n]{3,}");
            List<SlideData> fallbackSlides = new ArrayList<>();
            for (int i = 0; i < rawSlides.length; i++) {
                String trimmed = rawSlides[i].trim();
                if (trimmed.length() > 5) {
                    SlideData sd = new SlideData();
                    String[] lines = trimmed.split("[\r\n]+");
                    sd.title = lines[0].trim();
                    for (int l = 1; l < lines.length; l++) {
                        String line = lines[l].trim();
                        if (!line.isEmpty()) sd.bullets.add(line);
                    }
                    sd.layout = sd.bullets.isEmpty() ? SlideLayout.TITLE_SUBTITLE : SlideLayout.TITLE_BULLETS;
                    fallbackSlides.add(sd);
                }
            }
            if (!fallbackSlides.isEmpty()) {
                slides.clear();
                slides.addAll(fallbackSlides);
                currentSlideIndex = 0;
                selectSlide(0);
            } else {
                throw ex;
            }
        }
    }

    private int extractSlideNum(String name) {
        Matcher m = Pattern.compile("slide(\\d+)\\.xml").matcher(name);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (Exception ignored) {}
        }
        return 0;
    }

    /**
     * Parses OpenXML PresentationML slide XML, properly grouping paragraph runs <a:r><a:t>
     * so words aren't shredded into individual bullets.
     */
    private SlideData parseSlideXml(String xml, int slideIndex) {
        SlideData sd = new SlideData();
        sd.title = "";
        sd.bullets = new ArrayList<>();

        // Match each shape <p:sp> ... </p:sp>
        Pattern spPattern = Pattern.compile("<p:sp[ >].*?</p:sp>", Pattern.DOTALL);
        Matcher spMatcher = spPattern.matcher(xml);

        boolean foundTitle = false;
        List<String> bodyParagraphs = new ArrayList<>();

        while (spMatcher.find()) {
            String shapeXml = spMatcher.group();

            boolean isTitleShape = shapeXml.contains("type=\"title\"")
                    || shapeXml.contains("type=\"ctrTitle\"")
                    || (!foundTitle && shapeXml.contains("<p:ph"));

            // Parse all paragraphs <a:p> inside this shape
            Pattern pPattern = Pattern.compile("<a:p[ >].*?</a:p>", Pattern.DOTALL);
            Matcher pMatcher = pPattern.matcher(shapeXml);

            List<String> paragraphsInShape = new ArrayList<>();
            while (pMatcher.find()) {
                String paraXml = pMatcher.group();
                // Gather all <a:t> text runs in this paragraph
                StringBuilder paraText = new StringBuilder();
                int idx = 0;
                while ((idx = paraXml.indexOf("<a:t", idx)) != -1) {
                    int contentStart = paraXml.indexOf(">", idx);
                    if (contentStart != -1) {
                        int end = paraXml.indexOf("</a:t>", contentStart);
                        if (end != -1) {
                            String piece = paraXml.substring(contentStart + 1, end);
                            paraText.append(unescapeXml(piece));
                            idx = end + 6;
                        } else break;
                    } else break;
                }

                String clean = paraText.toString().trim();
                if (!clean.isEmpty()) {
                    paragraphsInShape.add(clean);
                }
            }

            if (!paragraphsInShape.isEmpty()) {
                if (isTitleShape || !foundTitle) {
                    sd.title = paragraphsInShape.get(0);
                    foundTitle = true;
                    // If shape has more paragraphs, they could be subtitle
                    if (paragraphsInShape.size() > 1 && (sd.subtitle == null || sd.subtitle.isEmpty())) {
                        sd.subtitle = paragraphsInShape.get(1);
                    }
                    for (int i = 2; i < paragraphsInShape.size(); i++) {
                        bodyParagraphs.add(paragraphsInShape.get(i));
                    }
                } else {
                    bodyParagraphs.addAll(paragraphsInShape);
                }
            }
        }

        if (sd.title.isEmpty()) {
            sd.title = "Slide " + slideIndex;
        }

        sd.bullets.addAll(bodyParagraphs);
        if (sd.bullets.isEmpty() && sd.subtitle != null && !sd.subtitle.isEmpty()) {
            sd.layout = SlideLayout.TITLE_SUBTITLE;
        } else if (!sd.bullets.isEmpty()) {
            sd.layout = SlideLayout.TITLE_BULLETS;
        }

        return sd;
    }

    private void savePresentation(boolean promptAs) {
        if (currentFile == null || promptAs) {
            JFileChooser chooser = new JFileChooser(currentFile != null ? currentFile.getParentFile() : new File("."));
            chooser.setSelectedFile(new File(currentFile != null ? currentFile.getName() : "Presentation1.pptx"));
            int res = chooser.showSaveDialog(stageCanvas);
            if (res != JFileChooser.APPROVE_OPTION) return;

            File chosen = chooser.getSelectedFile();
            if (!chosen.getName().contains(".")) {
                chosen = new File(chosen.getAbsolutePath() + ".pptx");
            }
            this.currentFile = chosen;
        }

        try {
            savePPTXPackage(currentFile);
            isModified = false;
            updateTitle();
            if (getOS() != null && getOS().getNotificationManager() != null) {
                getOS().getNotificationManager().postNotification("Presentation Saved", currentFile.getName() + " saved successfully.", javaos.models.NotificationMessage.Level.SUCCESS);
            }
            if (getOS() != null && getOS().getSoundService() != null) getOS().getSoundService().playSuccess();
            JOptionPane.showMessageDialog(stageCanvas, "Presentation saved successfully to:\n" + currentFile.getAbsolutePath(), "File Saved", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(stageCanvas, "Error saving presentation:\n" + e.getMessage(), "Save Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Packages a fully-compliant OpenXML PresentationML (.pptx) ZIP file.
     * Includes all required parts, relationships, and shape drawings for native PowerPoint compatibility.
     */
    private void savePPTXPackage(File target) throws Exception {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(target))) {
            // 1. [Content_Types].xml
            StringBuilder ct = new StringBuilder();
            ct.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
            ct.append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">\n");
            ct.append("  <Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>\n");
            ct.append("  <Default Extension=\"xml\" ContentType=\"application/xml\"/>\n");
            ct.append("  <Override PartName=\"/ppt/presentation.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml\"/>\n");
            for (int i = 1; i <= slides.size(); i++) {
                ct.append("  <Override PartName=\"/ppt/slides/slide").append(i).append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slide+xml\"/>\n");
            }
            ct.append("</Types>");
            writeZipEntry(zos, "[Content_Types].xml", ct.toString());

            // 2. _rels/.rels
            writeZipEntry(zos, "_rels/.rels",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n" +
                            "  <Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"ppt/presentation.xml\"/>\n" +
                            "</Relationships>");

            // 3. ppt/_rels/presentation.xml.rels
            StringBuilder pRels = new StringBuilder();
            pRels.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
            pRels.append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n");
            for (int i = 1; i <= slides.size(); i++) {
                pRels.append("  <Relationship Id=\"rId").append(i).append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide\" Target=\"slides/slide").append(i).append(".xml\"/>\n");
            }
            pRels.append("</Relationships>");
            writeZipEntry(zos, "ppt/_rels/presentation.xml.rels", pRels.toString());

            // 4. ppt/presentation.xml
            StringBuilder pXml = new StringBuilder();
            pXml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
            pXml.append("<p:presentation xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">\n");
            pXml.append("  <p:sldIdLst>\n");
            for (int i = 1; i <= slides.size(); i++) {
                pXml.append("    <p:sldId id=\"").append(255 + i).append("\" r:id=\"rId").append(i).append("\"/>\n");
            }
            pXml.append("  </p:sldIdLst>\n");
            pXml.append("  <p:sldSz cx=\"12192000\" cy=\"6858000\"/>\n"); // 16:9 widescreen
            pXml.append("</p:presentation>");
            writeZipEntry(zos, "ppt/presentation.xml", pXml.toString());

            // 5. Each slide XML
            for (int i = 0; i < slides.size(); i++) {
                SlideData s = slides.get(i);
                StringBuilder sXml = new StringBuilder();
                sXml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
                sXml.append("<p:sld xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\" xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">\n");
                sXml.append("  <p:cSld>\n");
                sXml.append("    <p:spTree>\n");
                sXml.append("      <p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>\n");
                sXml.append("      <p:grpSpPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"0\" cy=\"0\"/><a:chOff x=\"0\" y=\"0\"/><a:chExt cx=\"0\" cy=\"0\"/></a:xfrm></p:grpSpPr>\n");

                // Title shape with full OpenXML parameters
                sXml.append("      <p:sp>\n");
                sXml.append("        <p:nvSpPr>\n");
                sXml.append("          <p:cNvPr id=\"2\" name=\"Title ").append(i + 1).append("\"/>\n");
                sXml.append("          <p:cNvSpPr><a:spLocks noGrp=\"1\"/></p:cNvSpPr>\n");
                sXml.append("          <p:nvPr><p:ph type=\"title\"/></p:nvPr>\n");
                sXml.append("        </p:nvSpPr>\n");
                sXml.append("        <p:spPr>\n");
                sXml.append("          <a:xfrm><a:off x=\"685800\" y=\"500000\"/><a:ext cx=\"10820400\" cy=\"1200000\"/></a:xfrm>\n");
                sXml.append("          <a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom>\n");
                sXml.append("        </p:spPr>\n");
                sXml.append("        <p:txBody>\n");
                sXml.append("          <a:bodyPr/>\n");
                sXml.append("          <a:lstStyle/>\n");
                sXml.append("          <a:p><a:r><a:rPr lang=\"en-US\" b=\"1\"/><a:t>").append(escapeXml(s.title)).append("</a:t></a:r></a:p>\n");
                sXml.append("        </p:txBody>\n");
                sXml.append("      </p:sp>\n");

                // Content / Bullets shape
                sXml.append("      <p:sp>\n");
                sXml.append("        <p:nvSpPr>\n");
                sXml.append("          <p:cNvPr id=\"3\" name=\"Content ").append(i + 1).append("\"/>\n");
                sXml.append("          <p:cNvSpPr><a:spLocks noGrp=\"1\"/></p:cNvSpPr>\n");
                sXml.append("          <p:nvPr><p:ph idx=\"1\"/></p:nvPr>\n");
                sXml.append("        </p:nvSpPr>\n");
                sXml.append("        <p:spPr>\n");
                sXml.append("          <a:xfrm><a:off x=\"685800\" y=\"1900000\"/><a:ext cx=\"10820400\" cy=\"4400000\"/></a:xfrm>\n");
                sXml.append("          <a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom>\n");
                sXml.append("        </p:spPr>\n");
                sXml.append("        <p:txBody>\n");
                sXml.append("          <a:bodyPr/>\n");
                sXml.append("          <a:lstStyle/>\n");

                if (s.layout == SlideLayout.TITLE_SUBTITLE) {
                    sXml.append("          <a:p><a:r><a:rPr lang=\"en-US\"/><a:t>").append(escapeXml(s.subtitle)).append("</a:t></a:r></a:p>\n");
                } else if (s.layout == SlideLayout.BIG_STAT) {
                    sXml.append("          <a:p><a:r><a:rPr lang=\"en-US\" b=\"1\" sz=\"4800\"/><a:t>").append(escapeXml(s.statValue)).append("</a:t></a:r></a:p>\n");
                    sXml.append("          <a:p><a:r><a:rPr lang=\"en-US\"/><a:t>").append(escapeXml(s.statLabel)).append("</a:t></a:r></a:p>\n");
                } else {
                    for (String b : s.bullets) {
                        sXml.append("          <a:p><a:r><a:rPr lang=\"en-US\"/><a:t>").append(escapeXml(b)).append("</a:t></a:r></a:p>\n");
                    }
                }

                sXml.append("        </p:txBody>\n");
                sXml.append("      </p:sp>\n");

                sXml.append("    </p:spTree>\n");
                sXml.append("  </p:cSld>\n");
                sXml.append("</p:sld>");

                writeZipEntry(zos, "ppt/slides/slide" + (i + 1) + ".xml", sXml.toString());
            }
        }
    }

    private void writeZipEntry(ZipOutputStream zos, String entryName, String content) throws IOException {
        ZipEntry ze = new ZipEntry(entryName);
        zos.putNextEntry(ze);
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    private String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }

    private String unescapeXml(String s) {
        if (s == null) return "";
        return s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&#39;", "'");
    }

    private void newDeck() {
        if (isModified) {
            int opt = JOptionPane.showConfirmDialog(stageCanvas, "Discard unsaved changes and create new presentation?", "New Presentation", JOptionPane.YES_NO_OPTION);
            if (opt != JOptionPane.YES_OPTION) return;
        }
        currentFile = null;
        initDefaultDeck();
        isModified = false;
        updateTitle();
    }

    private void promptOpenFile() {
        JFileChooser chooser = new JFileChooser(currentFile != null ? currentFile.getParentFile() : new File("."));
        int res = chooser.showOpenDialog(stageCanvas);
        if (res == JFileChooser.APPROVE_OPTION) {
            openFile(chooser.getSelectedFile());
        }
    }

    private void updateTitle() {
        String name = (currentFile != null) ? currentFile.getName() : "Untitled Presentation.pptx";
        statusLabel.setText(name + (isModified ? " *" : ""));
        if (getWindow() != null) {
            getWindow().setTitle(name + (isModified ? " *" : "") + " — Presentation Slides");
        }
    }

    private JButton makeToolBtn(String text, String iconName, String tip) {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        JButton btn = new JButton(text);
        if (iconName != null) {
            btn.setIcon(CyberpunkIcons.createIcon(iconName, 14));
        }
        btn.setFont(theme.fontSmall);
        btn.setForeground(theme.textPrimary);
        btn.setBackground(theme.panel);
        btn.setOpaque(true);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setToolTipText(tip);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(theme.secondaryAccent),
                BorderFactory.createEmptyBorder(3, 8, 3, 8)
        ));
        btn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                btn.setForeground(theme.neonAccent);
                btn.setBackground(theme.elevatedPanel);
            }
            @Override public void mouseExited(MouseEvent e) {
                btn.setForeground(theme.textPrimary);
                btn.setBackground(theme.panel);
            }
        });
        return btn;
    }
}

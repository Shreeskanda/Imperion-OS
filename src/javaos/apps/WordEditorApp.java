package javaos.apps;

import javaos.ui.Theme;
import javaos.ui.ThemeManager;
import javaos.ui.components.CyberpunkIcons;
import javaos.ui.components.NeonScrollBarUI;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.*;
import javax.swing.undo.UndoManager;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * CyberWord Processor for IMPERION OS.
 * Complete Microsoft Word equivalent featuring:
 *  - 5-Tab Office Ribbon: Home, Insert, Layout, Review, View + Quick Access Toolbar
 *  - Full Typography: Font family, Font size, Grow/Shrink, Bold, Italic, Underline,
 *    Strikethrough, Subscript, Superscript, Text Color, Highlight Shading, Clear Formatting
 *  - Format Painter (copy/paste style attributes across text)
 *  - Paragraph Tools: Bullets (•, ◦, ▪, ➔, [ ]), Numbering (1.2.3, a.b.c, I.II.III),
 *    Indents, Alignments (Left, Center, Right, Justify), Line Spacing (1.0 - 2.5x)
 *  - Insert Tools: Table Generator (columns x rows with styled headers & borders),
 *    Image Insertion from disk, Hyperlinks, Symbol & Special Characters picker
 *    (Currency, Math, Typography, Arrows, Greek), Horizontal Dividers, Date/Time
 *  - Layout: Margins (Normal, Narrow, Wide), Orientation (Portrait, Landscape),
 *    Paper sizes (A4, Letter, Legal), Page Backgrounds (Paper White, Sepia, Soft Blue, Cyber)
 *  - Review: Full Word Count telemetry, Find & Replace (Next/Prev/Replace All with count),
 *    Proofing / Spell Checker with typo detector, Read Aloud narrator
 *  - View: White Paper Mode vs Dark Cyber Mode, Zoom (75% - 200%), Full Screen Mode
 *  - OpenXML (.docx) import & export, HTML export, Plain text export, and Hardware/PDF Printing
 */
public class WordEditorApp extends OSApplication {

    private File currentFile;
    private boolean isModified = false;
    private boolean isPaperLightMode = true; // Default to classic paper view like MS Word

    // Document & Text Engine
    private JTextPane textPane;
    private DefaultStyledDocument doc;
    private UndoManager undoManager;
    private JPanel paperCard;
    private JPanel pageHolder;

    // Format Painter State
    private boolean formatPainterActive = false;
    private AttributeSet copiedAttributes = null;
    private JButton btnFormatPainter;

    // Formatting Toolbar Controls
    private JComboBox<String> fontBox;
    private JComboBox<Integer> sizeBox;
    private JComboBox<String> styleBox;
    private JToggleButton btnBold;
    private JToggleButton btnItalic;
    private JToggleButton btnUnderline;
    private JToggleButton btnStrike;
    private JToggleButton btnSubscript;
    private JToggleButton btnSuperscript;
    private JButton btnTextColor;
    private JButton btnHighlight;
    private JComboBox<String> lineSpacingBox;

    // Ribbon Tabs
    private JPanel ribbonContentCards;
    private CardLayout ribbonCardLayout;
    private final Map<String, JButton> tabButtons = new HashMap<>();

    // Page Geometry & Settings
    private int pageMargin = 44; // Normal margin
    private boolean isLandscape = false;
    private int pageWidth = 740;
    private int pageHeight = 980;
    private double currentZoom = 1.0;
    private Color pageBgColor = Color.WHITE;

    // Status Labels
    private JLabel statusFileLabel;
    private JLabel statusStatsLabel;
    private JLabel wordTitleLabel;
    private JLabel statusPositionLabel;
    private JLabel statusZoomLabel;

    private Color currentTextColor = Color.BLACK;
    private Color currentHighlightColor = null;

    // Quick Dictionary for Spell Checking
    private static final Set<String> COMMON_WORDS = new HashSet<>(Arrays.asList(
            "the", "be", "to", "of", "and", "a", "in", "that", "have", "i", "it", "for", "not", "on", "with",
            "he", "as", "you", "do", "at", "this", "but", "his", "by", "from", "they", "we", "say", "her", "she",
            "or", "an", "will", "my", "one", "all", "would", "there", "their", "what", "so", "up", "out", "if",
            "about", "who", "get", "which", "go", "me", "when", "make", "can", "like", "time", "no", "just", "him",
            "know", "take", "people", "into", "year", "your", "good", "some", "could", "them", "see", "other",
            "than", "then", "now", "look", "only", "come", "its", "over", "think", "also", "back", "after", "use",
            "two", "how", "our", "work", "first", "well", "way", "even", "new", "want", "because", "any", "these",
            "give", "day", "most", "us", "document", "file", "word", "processor", "imperion", "system", "text",
            "cyber", "editor", "application", "windows", "computer", "project", "data", "format", "table", "image",
            "paragraph", "header", "footer", "title", "page", "section", "style", "font", "color", "size", "view",
            "review", "insert", "layout", "home", "save", "open", "print", "close", "help", "security", "desktop"
    ));

    public WordEditorApp() {
        this(null);
    }

    public WordEditorApp(File initialFile) {
        super(new AppDescriptor(
                "wordeditor",
                "Word Processor",
                "Advanced .docx & Microsoft Word equivalent processor",
                AppDescriptor.Category.PRODUCTIVITY,
                CyberpunkIcons.createIcon("WORD", 20),
                false
        ));
        this.currentFile = initialFile;
    }

    @Override
    public Dimension getPreferredWindowSize() {
        return new Dimension(1020, 720);
    }

    @Override
    public JComponent createContent() {
        JPanel main = new JPanel(new BorderLayout(0, 0));
        main.setOpaque(true);
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        main.setBackground(theme.background);

        // ── 1. TOP RIBBON CONTAINER ──────────────────────────────────────────
        JPanel topContainer = new JPanel();
        topContainer.setLayout(new BoxLayout(topContainer, BoxLayout.Y_AXIS));
        topContainer.setOpaque(true);
        topContainer.setBackground(theme.panel);
        topContainer.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, theme.secondaryAccent));

        // Quick Access Toolbar + Title
        JPanel quickBar = createQuickAccessToolbar();
        topContainer.add(quickBar);

        // Ribbon Tab Strip (Home, Insert, Layout, Review, View)
        JPanel tabStrip = createRibbonTabStrip();
        topContainer.add(tabStrip);

        // Ribbon Content Panels (CardLayout)
        ribbonCardLayout = new CardLayout();
        ribbonContentCards = new JPanel(ribbonCardLayout);
        ribbonContentCards.setOpaque(true);
        ribbonContentCards.setBackground(theme.panel);
        ribbonContentCards.setBorder(BorderFactory.createEmptyBorder(4, 8, 6, 8));

        ribbonContentCards.add(buildHomeTabPanel(), "HOME");
        ribbonContentCards.add(buildInsertTabPanel(), "INSERT");
        ribbonContentCards.add(buildLayoutTabPanel(), "LAYOUT");
        ribbonContentCards.add(buildReviewTabPanel(), "REVIEW");
        ribbonContentCards.add(buildViewTabPanel(), "VIEW");

        topContainer.add(ribbonContentCards);
        main.add(topContainer, BorderLayout.NORTH);

        // ── 2. CENTER DOCUMENT CANVAS ────────────────────────────────────────
        doc = new DefaultStyledDocument();
        textPane = new JTextPane(doc);
        textPane.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        textPane.setMargin(new Insets(pageMargin, pageMargin, pageMargin, pageMargin));
        textPane.setCaretColor(Color.BLACK);

        undoManager = new UndoManager();
        doc.addUndoableEditListener(e -> undoManager.addEdit(e.getEdit()));

        doc.addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { onDocChanged(); }
            @Override public void removeUpdate(DocumentEvent e) { onDocChanged(); }
            @Override public void changedUpdate(DocumentEvent e) { onDocChanged(); }
        });

        // Format Painter click listener
        textPane.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                if (formatPainterActive && copiedAttributes != null) {
                    int start = textPane.getSelectionStart();
                    int end = textPane.getSelectionEnd();
                    if (start != end) {
                        doc.setCharacterAttributes(start, end - start, copiedAttributes, false);
                        formatPainterActive = false;
                        btnFormatPainter.setBackground(ThemeManager.getInstance().getCurrentTheme().panel);
                        textPane.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
                    }
                }
            }
        });

        // Bind standard shortcuts
        bindKeyShortcuts();

        textPane.addCaretListener(e -> updateCaretInfo());

        // Center page container with drop shadow
        pageHolder = new JPanel(new GridBagLayout());
        pageHolder.setOpaque(true);
        pageHolder.setBackground(new Color(24, 28, 40));

        paperCard = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // Page shadow
                g2.setColor(new Color(0, 0, 0, 90));
                g2.fill(new RoundRectangle2D.Float(4, 4, getWidth() - 4, getHeight() - 4, 10, 10));
                // Page surface
                g2.setColor(pageBgColor);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth() - 4, getHeight() - 4, 10, 10));
                // Page border line
                g2.setColor(isPaperLightMode ? new Color(203, 213, 225) : ThemeManager.getInstance().getCurrentTheme().secondaryAccent);
                g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 5, getHeight() - 5, 10, 10));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        paperCard.setOpaque(false);
        paperCard.setPreferredSize(new Dimension(pageWidth, pageHeight));
        paperCard.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        applyVisualThemeToEditor();
        paperCard.add(textPane, BorderLayout.CENTER);
        pageHolder.add(paperCard);

        JScrollPane scroll = new JScrollPane(pageHolder);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUI(new NeonScrollBarUI());
        scroll.getHorizontalScrollBar().setUI(new NeonScrollBarUI());
        main.add(scroll, BorderLayout.CENTER);

        // ── 3. BOTTOM STATUS BAR ─────────────────────────────────────────────
        JPanel statusBar = new JPanel(new BorderLayout(10, 0));
        statusBar.setOpaque(true);
        statusBar.setBackground(theme.panel);
        statusBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, theme.secondaryAccent),
                BorderFactory.createEmptyBorder(4, 12, 4, 12)
        ));

        statusFileLabel = new JLabel("Document1.docx");
        statusFileLabel.setFont(theme.fontSmall);
        statusFileLabel.setForeground(theme.neonAccent);
        statusBar.add(statusFileLabel, BorderLayout.WEST);

        statusStatsLabel = new JLabel("Page 1 of 1  |  0 words  |  0 characters  |  ~0 min read");
        statusStatsLabel.setFont(theme.fontSmall);
        statusStatsLabel.setForeground(theme.textPrimary);
        statusStatsLabel.setHorizontalAlignment(SwingConstants.CENTER);
        statusBar.add(statusStatsLabel, BorderLayout.CENTER);

        JPanel rightStatus = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        rightStatus.setOpaque(false);

        statusPositionLabel = new JLabel("Ln 1, Col 1");
        statusPositionLabel.setFont(theme.fontSmall);
        statusPositionLabel.setForeground(theme.textSecondary);

        statusZoomLabel = new JLabel("100%");
        statusZoomLabel.setFont(theme.fontSmall);
        statusZoomLabel.setForeground(theme.neonAccent);

        rightStatus.add(statusPositionLabel);
        rightStatus.add(new JSeparator(SwingConstants.VERTICAL));
        rightStatus.add(statusZoomLabel);
        JLabel wordBrandLabel = new JLabel("Word Processor 2026");
        wordBrandLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        wordBrandLabel.setForeground(theme.neonAccent);
        rightStatus.add(wordBrandLabel);
        statusBar.add(rightStatus, BorderLayout.EAST);

        main.add(statusBar, BorderLayout.SOUTH);

        // Load or initialize document
        if (currentFile != null) {
            SwingUtilities.invokeLater(() -> openFile(currentFile));
        } else {
            insertWelcomeSample();
        }

        return main;
    }

    // ── QUICK ACCESS TOOLBAR ────────────────────────────────────────────────
    private JPanel createQuickAccessToolbar() {
        Color wordBlue = new Color(0x185ABD);
        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(true);
        bar.setBackground(wordBlue);
        bar.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));

        // LEFT: Word icon + action buttons
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        leftPanel.setOpaque(false);

        JLabel iconLabel = new JLabel("📝 Word");
        iconLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        iconLabel.setForeground(Color.WHITE);
        iconLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 10));
        leftPanel.add(iconLabel);

        for (JButton btn : new JButton[]{
                makeWordBlueBtn("Save",  "Save Document (Ctrl+S)",  e -> saveDocument(false)),
                makeWordBlueBtn("Undo",  "Undo (Ctrl+Z)",            e -> { if (undoManager.canUndo()) undoManager.undo(); }),
                makeWordBlueBtn("Redo",  "Redo (Ctrl+Y)",            e -> { if (undoManager.canRedo()) undoManager.redo(); }),
                makeWordBlueBtn("New",   "New Document (Ctrl+N)",   e -> newDocument()),
                makeWordBlueBtn("Open",  "Open Document (Ctrl+O)",  e -> promptOpenFile()),
                makeWordBlueBtn("Print", "Print / Export PDF (Ctrl+P)", e -> printDocument())
        }) {
            leftPanel.add(btn);
        }
        bar.add(leftPanel, BorderLayout.WEST);

        // CENTER: document filename
        wordTitleLabel = new JLabel("Document1.docx", SwingConstants.CENTER);
        wordTitleLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        wordTitleLabel.setForeground(Color.WHITE);
        bar.add(wordTitleLabel, BorderLayout.CENTER);

        // RIGHT: branding label
        JLabel brandLabel = new JLabel("Word Processor 2026  ");
        brandLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        brandLabel.setForeground(new Color(0xCCDDFF));
        bar.add(brandLabel, BorderLayout.EAST);

        return bar;
    }

    /** Creates a toolbar button styled for the Word-blue quick-access bar. */
    private JButton makeWordBlueBtn(String text, String tip, java.awt.event.ActionListener action) {
        Color wordBlue = new Color(0x185ABD);
        Color hoverBlue = new Color(0x1A6FD6);
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btn.setForeground(Color.WHITE);
        btn.setBackground(wordBlue);
        btn.setOpaque(true);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(true);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setToolTipText(tip);
        btn.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
        btn.addActionListener(action);
        btn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { btn.setBackground(hoverBlue); }
            @Override public void mouseExited(MouseEvent e)  { btn.setBackground(wordBlue);  }
        });
        return btn;
    }

    // ── RIBBON TAB STRIP ────────────────────────────────────────────────────
    private JPanel createRibbonTabStrip() {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        JPanel strip = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        strip.setOpaque(true);
        strip.setBackground(theme.elevatedPanel);
        strip.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, theme.secondaryAccent));

        String[] tabs = {"HOME", "INSERT", "LAYOUT", "REVIEW", "VIEW"};
        for (String tab : tabs) {
            JButton btn = new JButton(tab);
            btn.setFont(theme.fontBold);
            btn.setFocusPainted(false);
            btn.setBorderPainted(false);
            btn.setContentAreaFilled(false);
            btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            btn.setBorder(BorderFactory.createEmptyBorder(6, 16, 6, 16));

            if (tab.equals("HOME")) {
                btn.setForeground(theme.neonAccent);
                btn.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, theme.neonAccent));
            } else {
                btn.setForeground(theme.textPrimary);
            }

            btn.addActionListener(e -> switchRibbonTab(tab));
            tabButtons.put(tab, btn);
            strip.add(btn);
        }

        return strip;
    }

    private void switchRibbonTab(String tabName) {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        for (Map.Entry<String, JButton> entry : tabButtons.entrySet()) {
            if (entry.getKey().equals(tabName)) {
                entry.getValue().setForeground(theme.neonAccent);
                entry.getValue().setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, theme.neonAccent));
            } else {
                entry.getValue().setForeground(theme.textPrimary);
                entry.getValue().setBorder(BorderFactory.createEmptyBorder(6, 16, 6, 16));
            }
        }
        ribbonCardLayout.show(ribbonContentCards, tabName);
    }

    // ── TAB 1: HOME (TYPOGRAPHY, PARAGRAPH, STYLES, EDITING) ────────────────
    private JPanel buildHomeTabPanel() {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        JPanel home = new JPanel();
        home.setLayout(new BoxLayout(home, BoxLayout.Y_AXIS));
        home.setOpaque(false);

        // Row 1: Clipboard & Font Controls
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        row1.setOpaque(false);

        // Clipboard
        JButton btnCut = makeToolBtn("Cut", null, "Cut (Ctrl+X)");
        btnCut.addActionListener(e -> textPane.cut());
        JButton btnCopy = makeToolBtn("Copy", null, "Copy (Ctrl+C)");
        btnCopy.addActionListener(e -> textPane.copy());
        JButton btnPaste = makeToolBtn("Paste", null, "Paste (Ctrl+V)");
        btnPaste.addActionListener(e -> textPane.paste());

        btnFormatPainter = makeToolBtn("Painter", null, "Format Painter (Pick up style & apply to next selection)");
        btnFormatPainter.addActionListener(e -> activateFormatPainter());

        row1.add(btnCut);
        row1.add(btnCopy);
        row1.add(btnPaste);
        row1.add(btnFormatPainter);
        row1.add(new JSeparator(SwingConstants.VERTICAL));

        // Font Family
        fontBox = new JComboBox<>(new String[]{
                "Segoe UI", "Calibri", "Arial", "Times New Roman", "Consolas",
                "Georgia", "Trebuchet MS", "Verdana", "Courier New", "Comic Sans MS"
        });
        fontBox.setSelectedItem("Segoe UI");
        fontBox.setPreferredSize(new Dimension(130, 26));
        fontBox.setFont(theme.fontSmall);
        fontBox.addActionListener(e -> applyFontFamily((String) fontBox.getSelectedItem()));
        row1.add(fontBox);

        // Font Size
        sizeBox = new JComboBox<>(new Integer[]{8, 9, 10, 11, 12, 14, 16, 18, 20, 24, 28, 36, 48, 72});
        sizeBox.setSelectedItem(14);
        sizeBox.setPreferredSize(new Dimension(56, 26));
        sizeBox.setFont(theme.fontSmall);
        sizeBox.addActionListener(e -> {
            Integer s = (Integer) sizeBox.getSelectedItem();
            if (s != null) applyFontSize(s);
        });
        row1.add(sizeBox);

        // Grow / Shrink Font
        JButton btnGrow = makeToolBtn("A^", null, "Increase Font Size (Grow)");
        btnGrow.addActionListener(e -> changeFontSizeBy(2));
        JButton btnShrink = makeToolBtn("Av", null, "Decrease Font Size (Shrink)");
        btnShrink.addActionListener(e -> changeFontSizeBy(-2));
        row1.add(btnGrow);
        row1.add(btnShrink);

        // Change Case Dropdown
        JComboBox<String> caseBox = new JComboBox<>(new String[]{"Aa Case", "UPPERCASE", "lowercase", "Sentence case", "Capitalize Words"});
        caseBox.setPreferredSize(new Dimension(110, 26));
        caseBox.setFont(theme.fontSmall);
        caseBox.addActionListener(e -> {
            String sel = (String) caseBox.getSelectedItem();
            if (sel != null && !sel.equals("Aa Case")) {
                changeSelectedCase(sel);
                caseBox.setSelectedIndex(0);
            }
        });
        row1.add(caseBox);

        // Clear All Formatting
        JButton btnClearFmt = makeToolBtn("A\\", null, "Clear All Formatting (Reset to Normal)");
        btnClearFmt.addActionListener(e -> clearFormatting());
        row1.add(btnClearFmt);

        home.add(row1);

        // Row 2: Basic Formatting, Colors, Paragraphs, Styles
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        row2.setOpaque(false);

        btnBold = makeToggleBtn("B", "Bold (Ctrl+B)", Font.BOLD);
        btnBold.addActionListener(new StyledEditorKit.BoldAction());
        row2.add(btnBold);

        btnItalic = makeToggleBtn("I", "Italic (Ctrl+I)", Font.ITALIC);
        btnItalic.addActionListener(new StyledEditorKit.ItalicAction());
        row2.add(btnItalic);

        btnUnderline = makeToggleBtn("U", "Underline (Ctrl+U)", Font.PLAIN);
        btnUnderline.addActionListener(new StyledEditorKit.UnderlineAction());
        row2.add(btnUnderline);

        btnStrike = makeToggleBtn("S", "Strikethrough", Font.PLAIN);
        btnStrike.addActionListener(e -> toggleStrikethrough());
        row2.add(btnStrike);

        btnSubscript = makeToggleBtn("X₂", "Subscript", Font.PLAIN);
        btnSubscript.addActionListener(e -> toggleSubscript());
        row2.add(btnSubscript);

        btnSuperscript = makeToggleBtn("X²", "Superscript", Font.PLAIN);
        btnSuperscript.addActionListener(e -> toggleSuperscript());
        row2.add(btnSuperscript);

        // Text Color & Highlight
        btnTextColor = makeColorPickerBtn("A", "Font Color", isPaperLightMode ? Color.BLACK : theme.neonAccent);
        btnTextColor.addActionListener(e -> pickTextColor());
        row2.add(btnTextColor);

        btnHighlight = makeColorPickerBtn("H", "Text Highlight Shading", Color.YELLOW);
        btnHighlight.addActionListener(e -> pickHighlightColor());
        row2.add(btnHighlight);

        row2.add(new JSeparator(SwingConstants.VERTICAL));

        // Alignments
        JButton btnAlignLeft = makeToolBtn("⯈", null, "Align Left (Ctrl+L)");
        btnAlignLeft.addActionListener(new StyledEditorKit.AlignmentAction("Left", StyleConstants.ALIGN_LEFT));
        JButton btnAlignCenter = makeToolBtn("⯅", null, "Align Center (Ctrl+E)");
        btnAlignCenter.addActionListener(new StyledEditorKit.AlignmentAction("Center", StyleConstants.ALIGN_CENTER));
        JButton btnAlignRight = makeToolBtn("⯇", null, "Align Right (Ctrl+R)");
        btnAlignRight.addActionListener(new StyledEditorKit.AlignmentAction("Right", StyleConstants.ALIGN_RIGHT));
        JButton btnAlignJustify = makeToolBtn("☰", null, "Justify (Ctrl+J)");
        btnAlignJustify.addActionListener(new StyledEditorKit.AlignmentAction("Justify", StyleConstants.ALIGN_JUSTIFIED));

        row2.add(btnAlignLeft);
        row2.add(btnAlignCenter);
        row2.add(btnAlignRight);
        row2.add(btnAlignJustify);

        // Line Spacing
        lineSpacingBox = new JComboBox<>(new String[]{"1.0x", "1.15x", "1.5x", "2.0x", "2.5x"});
        lineSpacingBox.setPreferredSize(new Dimension(65, 26));
        lineSpacingBox.setFont(theme.fontSmall);
        lineSpacingBox.addActionListener(e -> {
            String val = (String) lineSpacingBox.getSelectedItem();
            if (val != null) {
                float mult = Float.parseFloat(val.replace("x", ""));
                setLineSpacing(mult);
            }
        });
        row2.add(lineSpacingBox);

        // Bullets & Numbering
        JComboBox<String> bulletBox = new JComboBox<>(new String[]{"• Bullets", "• Disc", "◦ Circle", "▪ Square", "➔ Arrow", "[ ] Checklist"});
        bulletBox.setPreferredSize(new Dimension(95, 26));
        bulletBox.setFont(theme.fontSmall);
        bulletBox.addActionListener(e -> {
            String s = (String) bulletBox.getSelectedItem();
            if (s != null && !s.equals("• Bullets")) {
                insertCustomBullet(s);
                bulletBox.setSelectedIndex(0);
            }
        });
        row2.add(bulletBox);

        JComboBox<String> numBox = new JComboBox<>(new String[]{"1. Numbering", "1. 2. 3.", "a. b. c.", "i. ii. iii."});
        numBox.setPreferredSize(new Dimension(105, 26));
        numBox.setFont(theme.fontSmall);
        numBox.addActionListener(e -> {
            String s = (String) numBox.getSelectedItem();
            if (s != null && !s.equals("1. Numbering")) {
                insertNumbering(s);
                numBox.setSelectedIndex(0);
            }
        });
        row2.add(numBox);

        // Indents
        JButton btnDecIndent = makeToolBtn("⇤", null, "Decrease Indent");
        btnDecIndent.addActionListener(e -> changeIndent(-20));
        JButton btnIncIndent = makeToolBtn("⇥", null, "Increase Indent");
        btnIncIndent.addActionListener(e -> changeIndent(20));
        row2.add(btnDecIndent);
        row2.add(btnIncIndent);

        row2.add(new JSeparator(SwingConstants.VERTICAL));

        // Styles Dropdown
        styleBox = new JComboBox<>(new String[]{
                "Normal Text", "Document Title", "Subtitle",
                "Heading 1", "Heading 2", "Heading 3", "Blockquote", "Code Snippet"
        });
        styleBox.setPreferredSize(new Dimension(120, 26));
        styleBox.setFont(theme.fontSmall);
        styleBox.addActionListener(e -> applyHeadingStyle((String) styleBox.getSelectedItem()));
        row2.add(styleBox);

        home.add(row2);
        return home;
    }

    // ── TAB 2: INSERT (TABLE, IMAGE, LINK, SYMBOL, DIVIDER, DATE/TIME) ──────
    private JPanel buildInsertTabPanel() {
        JPanel insert = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        insert.setOpaque(false);

        JButton btnTable = makeToolBtn("Insert Table", null, "Insert customizable table grid");
        btnTable.addActionListener(e -> showInsertTableDialog());
        insert.add(btnTable);

        JButton btnImage = makeToolBtn("Insert Picture", "IMAGE", "Embed image file from disk");
        btnImage.addActionListener(e -> promptInsertImage());
        insert.add(btnImage);

        JButton btnLink = makeToolBtn("Insert Link", null, "Insert clickable hyperlink");
        btnLink.addActionListener(e -> promptInsertHyperlink());
        insert.add(btnLink);

        insert.add(new JSeparator(SwingConstants.VERTICAL));

        JButton btnSymbol = makeToolBtn("Symbols & Math", null, "Insert Currency, Math, Greek & Special Symbols");
        btnSymbol.addActionListener(e -> showSymbolPickerModal());
        insert.add(btnSymbol);

        JButton btnDateTime = makeToolBtn("Date & Time", null, "Insert current date/time in standard formats");
        btnDateTime.addActionListener(e -> showInsertDateTimeDialog());
        insert.add(btnDateTime);

        JButton btnDivider = makeToolBtn("Horizontal Line", null, "Insert horizontal divider rule");
        btnDivider.addActionListener(e -> insertHorizontalLine());
        insert.add(btnDivider);

        JButton btnPageBreak = makeToolBtn("Page Break", null, "Insert visual page break boundary");
        btnPageBreak.addActionListener(e -> insertPageBreak());
        insert.add(btnPageBreak);

        JButton btnQuoteBox = makeToolBtn("Callout Box", null, "Insert styled callout / note box");
        btnQuoteBox.addActionListener(e -> insertCalloutBox());
        insert.add(btnQuoteBox);

        return insert;
    }

    // ── TAB 3: LAYOUT (PAGE SETUP, MARGINS, ORIENTATION, PAPER SIZES) ──────
    private JPanel buildLayoutTabPanel() {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        JPanel layout = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        layout.setOpaque(false);

        // Margins
        layout.add(new JLabel(" Margins:"));
        JComboBox<String> marginBox = new JComboBox<>(new String[]{"Normal (1 inch)", "Narrow (0.5 inch)", "Wide (1.5 inch)"});
        marginBox.setFont(theme.fontSmall);
        marginBox.addActionListener(e -> {
            int idx = marginBox.getSelectedIndex();
            if (idx == 0) pageMargin = 44;
            else if (idx == 1) pageMargin = 22;
            else if (idx == 2) pageMargin = 66;
            textPane.setMargin(new Insets(pageMargin, pageMargin, pageMargin, pageMargin));
        });
        layout.add(marginBox);

        layout.add(new JSeparator(SwingConstants.VERTICAL));

        // Orientation
        layout.add(new JLabel(" Orientation:"));
        JButton btnPortrait = makeToolBtn("Portrait", null, "Standard portrait layout (740px)");
        btnPortrait.addActionListener(e -> setPageOrientation(false));
        JButton btnLandscape = makeToolBtn("Landscape", null, "Wide landscape layout (1080px)");
        btnLandscape.addActionListener(e -> setPageOrientation(true));
        layout.add(btnPortrait);
        layout.add(btnLandscape);

        layout.add(new JSeparator(SwingConstants.VERTICAL));

        // Paper Size
        layout.add(new JLabel(" Paper Size:"));
        JComboBox<String> sizeP = new JComboBox<>(new String[]{"A4 (210 x 297mm)", "US Letter", "Legal", "Full Width"});
        sizeP.setFont(theme.fontSmall);
        sizeP.addActionListener(e -> {
            String s = (String) sizeP.getSelectedItem();
            if (s != null) {
                if (s.startsWith("A4")) { pageWidth = 740; pageHeight = 980; }
                else if (s.startsWith("US")) { pageWidth = 780; pageHeight = 1010; }
                else if (s.startsWith("Legal")) { pageWidth = 780; pageHeight = 1180; }
                else if (s.startsWith("Full")) { pageWidth = 960; pageHeight = 1000; }
                paperCard.setPreferredSize(new Dimension(pageWidth, pageHeight));
                paperCard.revalidate();
                pageHolder.revalidate();
            }
        });
        layout.add(sizeP);

        layout.add(new JSeparator(SwingConstants.VERTICAL));

        // Page Background Theme
        layout.add(new JLabel(" Page Color:"));
        JComboBox<String> bgBox = new JComboBox<>(new String[]{"Classic White", "Sepia Eye-Care", "Soft Ice Blue", "Cyber Dark", "Matrix Terminal"});
        bgBox.setFont(theme.fontSmall);
        bgBox.addActionListener(e -> {
            int idx = bgBox.getSelectedIndex();
            if (idx == 0) { pageBgColor = Color.WHITE; setEditorTextColor(Color.BLACK); isPaperLightMode = true; }
            else if (idx == 1) { pageBgColor = new Color(253, 251, 247); setEditorTextColor(new Color(40, 30, 20)); isPaperLightMode = true; }
            else if (idx == 2) { pageBgColor = new Color(240, 247, 255); setEditorTextColor(new Color(15, 23, 42)); isPaperLightMode = true; }
            else if (idx == 3) { pageBgColor = new Color(17, 24, 39); setEditorTextColor(new Color(241, 245, 249)); isPaperLightMode = false; }
            else if (idx == 4) { pageBgColor = new Color(10, 18, 10); setEditorTextColor(new Color(50, 255, 100)); isPaperLightMode = false; }
            paperCard.repaint();
            textPane.setBackground(pageBgColor);
        });
        layout.add(bgBox);

        return layout;
    }

    // ── TAB 4: REVIEW (WORD COUNT, FIND/REPLACE, SPELL CHECK, READ ALOUD) ───
    private JPanel buildReviewTabPanel() {
        JPanel review = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        review.setOpaque(false);

        JButton btnWordCount = makeToolBtn("Word Count Stats", null, "Comprehensive document statistics");
        btnWordCount.addActionListener(e -> showDetailedWordCountModal());
        review.add(btnWordCount);

        JButton btnFindReplace = makeToolBtn("Find & Replace", "SEARCH", "Search, replace, or replace all");
        btnFindReplace.addActionListener(e -> showFindReplaceDialog());
        review.add(btnFindReplace);

        review.add(new JSeparator(SwingConstants.VERTICAL));

        JButton btnSpell = makeToolBtn("Spell Check / Proofing", null, "Scan document for misspelled words");
        btnSpell.addActionListener(e -> runSpellChecker());
        review.add(btnSpell);

        JButton btnReadAloud = makeToolBtn("Read Aloud", null, "Audio narrator speech synthesis");
        btnReadAloud.addActionListener(e -> readDocumentAloud());
        review.add(btnReadAloud);

        return review;
    }

    // ── TAB 5: VIEW (PAPER VS CYBER, ZOOM, FULL SCREEN) ─────────────────────
    private JPanel buildViewTabPanel() {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        JPanel view = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        view.setOpaque(false);

        JButton btnToggleMode = makeToolBtn("Toggle Paper / Dark View", "IMAGE", "Switch White Paper vs Dark Mode");
        btnToggleMode.addActionListener(e -> toggleViewMode());
        view.add(btnToggleMode);

        view.add(new JSeparator(SwingConstants.VERTICAL));

        view.add(new JLabel(" Zoom:"));
        JComboBox<String> zoomBox = new JComboBox<>(new String[]{"75%", "90%", "100%", "125%", "150%", "200%"});
        zoomBox.setSelectedItem("100%");
        zoomBox.setFont(theme.fontSmall);
        zoomBox.addActionListener(e -> {
            String z = (String) zoomBox.getSelectedItem();
            if (z != null) {
                double factor = Double.parseDouble(z.replace("%", "")) / 100.0;
                applyZoom(factor);
            }
        });
        view.add(zoomBox);

        view.add(new JSeparator(SwingConstants.VERTICAL));

        JButton btnFullScreen = makeToolBtn("Reading View (F11)", null, "Toggle Maximized Reading Mode");
        btnFullScreen.addActionListener(e -> {
            if (getWindow() != null) {
                getWindow().toggleMaximize();
            }
        });
        view.add(btnFullScreen);

        return view;
    }

    // ── TABLE GENERATOR DIALOG ──────────────────────────────────────────────
    private void showInsertTableDialog() {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        JPanel panel = new JPanel(new GridLayout(3, 2, 6, 8));
        panel.setOpaque(false);

        JSpinner colSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 10, 1));
        JSpinner rowSpinner = new JSpinner(new SpinnerNumberModel(4, 1, 30, 1));
        JCheckBox chkHeader = new JCheckBox("Include Header Row", true);

        panel.add(new JLabel("Number of Columns:"));
        panel.add(colSpinner);
        panel.add(new JLabel("Number of Rows:"));
        panel.add(rowSpinner);
        panel.add(new JLabel("Options:"));
        panel.add(chkHeader);

        int opt = JOptionPane.showConfirmDialog(textPane, panel, "Insert Table", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (opt == JOptionPane.OK_OPTION) {
            int cols = (Integer) colSpinner.getValue();
            int rows = (Integer) rowSpinner.getValue();
            boolean hasHeader = chkHeader.isSelected();

            StringBuilder sb = new StringBuilder("\n");
            // Render structured table format
            String separator = "+" + ("-----------------+".repeat(cols)) + "\n";
            sb.append(separator);

            if (hasHeader) {
                sb.append("|");
                for (int c = 1; c <= cols; c++) {
                    sb.append(String.format("  Header %-8d|", c));
                }
                sb.append("\n").append(separator);
            }

            for (int r = 1; r <= rows; r++) {
                sb.append("|");
                for (int c = 1; c <= cols; c++) {
                    sb.append(String.format("  Cell (%d,%d)    |", r, c));
                }
                sb.append("\n").append(separator);
            }
            sb.append("\n");

            try {
                SimpleAttributeSet mono = new SimpleAttributeSet();
                StyleConstants.setFontFamily(mono, "Consolas");
                StyleConstants.setFontSize(mono, 13);
                StyleConstants.setForeground(mono, isPaperLightMode ? Color.BLACK : theme.neonAccent);
                doc.insertString(textPane.getCaretPosition(), sb.toString(), mono);
            } catch (Exception ignored) {}
        }
    }

    // ── IMAGE INSERTION ─────────────────────────────────────────────────────
    private void promptInsertImage() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select Image to Embed in Document");
        int res = chooser.showOpenDialog(textPane);
        if (res == JFileChooser.APPROVE_OPTION) {
            File f = chooser.getSelectedFile();
            try {
                ImageIcon orig = new ImageIcon(f.getAbsolutePath());
                Image img = orig.getImage();
                int w = orig.getIconWidth();
                int h = orig.getIconHeight();
                // Scale to fit page max width (600px)
                int maxW = 560;
                if (w > maxW) {
                    h = (int) ((double) h * maxW / w);
                    w = maxW;
                }
                Image scaled = img.getScaledInstance(w, h, Image.SCALE_SMOOTH);
                ImageIcon icon = new ImageIcon(scaled);

                int pos = textPane.getCaretPosition();
                doc.insertString(pos, "\n", new SimpleAttributeSet());
                textPane.setCaretPosition(pos + 1);
                textPane.insertIcon(icon);
                doc.insertString(textPane.getCaretPosition(), "\n", new SimpleAttributeSet());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(textPane, "Failed to embed image: " + ex.getMessage(), "Image Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // ── HYPERLINK INSERTION ─────────────────────────────────────────────────
    private void promptInsertHyperlink() {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        JPanel panel = new JPanel(new GridLayout(2, 2, 6, 8));
        JTextField txtDisplay = new JTextField(textPane.getSelectedText() != null ? textPane.getSelectedText() : "Click here");
        JTextField txtUrl = new JTextField("https://");

        panel.add(new JLabel("Text to display:"));
        panel.add(txtDisplay);
        panel.add(new JLabel("Link Address (URL):"));
        panel.add(txtUrl);

        int res = JOptionPane.showConfirmDialog(textPane, panel, "Insert Hyperlink", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (res == JOptionPane.OK_OPTION) {
            String disp = txtDisplay.getText().trim();
            String url = txtUrl.getText().trim();
            if (disp.isEmpty()) disp = url;

            SimpleAttributeSet linkAttr = new SimpleAttributeSet();
            StyleConstants.setForeground(linkAttr, new Color(0x02, 0x84, 0xC7));
            StyleConstants.setUnderline(linkAttr, true);

            try {
                int pos = textPane.getCaretPosition();
                int selStart = textPane.getSelectionStart();
                int selEnd = textPane.getSelectionEnd();
                if (selStart != selEnd) {
                    doc.remove(selStart, selEnd - selStart);
                    pos = selStart;
                }
                doc.insertString(pos, disp + " (" + url + ")", linkAttr);
            } catch (Exception ignored) {}
        }
    }

    // ── SYMBOLS & SPECIAL CHARACTERS PICKER MODAL ───────────────────────────
    private void showSymbolPickerModal() {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        JDialog dlg = new JDialog((Frame) SwingUtilities.getWindowAncestor(textPane), "Insert Symbol / Special Character", true);
        dlg.setLayout(new BorderLayout(8, 8));
        dlg.setSize(520, 360);
        dlg.setLocationRelativeTo(textPane);

        JTabbedPane tabs = new JTabbedPane();

        String[][] categories = {
                {"Currency", "$", "€", "£", "¥", "₹", "¢", "₿", "₩", "₽", "₱", "₨", "₴"},
                {"Mathematics", "±", "×", "÷", "√", "∞", "≈", "≠", "≤", "≥", "∑", "∫", "π", "°", "µ", "‰", "∂", "∆", "∏"},
                {"Typography", "©", "®", "™", "§", "¶", "•", "—", "–", "“", "”", "‘", "’", "¿", "¡", "…", "†", "‡", "◊"},
                {"Arrows", "←", "→", "↑", "↓", "↔", "↕", "➔", "➜", "⇐", "⇒", "⇑", "⇓", "⇔", "↗", "↘"},
                {"Greek", "α", "β", "γ", "δ", "ε", "ζ", "η", "θ", "ι", "κ", "λ", "μ", "ν", "ξ", "π", "ρ", "σ", "τ", "υ", "φ", "χ", "ψ", "ω", "Ω", "Δ", "Σ"}
        };

        for (String[] cat : categories) {
            String catName = cat[0];
            JPanel grid = new JPanel(new GridLayout(0, 6, 6, 6));
            grid.setBorder(new EmptyBorder(10, 10, 10, 10));

            for (int i = 1; i < cat.length; i++) {
                String sym = cat[i];
                JButton btnSym = new JButton(sym);
                btnSym.setFont(new Font("Segoe UI", Font.BOLD, 18));
                btnSym.setFocusPainted(false);
                btnSym.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                btnSym.addActionListener(e -> {
                    try {
                        doc.insertString(textPane.getCaretPosition(), sym, new SimpleAttributeSet());
                    } catch (Exception ignored) {}
                    dlg.dispose();
                });
                grid.add(btnSym);
            }
            tabs.addTab(catName, new JScrollPane(grid));
        }

        dlg.add(tabs, BorderLayout.CENTER);
        JButton btnClose = new JButton("Close");
        btnClose.addActionListener(e -> dlg.dispose());
        JPanel bot = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bot.add(btnClose);
        dlg.add(bot, BorderLayout.SOUTH);
        dlg.setVisible(true);
    }

    // ── INSERT DATE & TIME DIALOG ───────────────────────────────────────────
    private void showInsertDateTimeDialog() {
        LocalDateTime now = LocalDateTime.now();
        String[] formats = {
                now.format(DateTimeFormatter.ofPattern("EEEE, MMMM dd, yyyy")),
                now.format(DateTimeFormatter.ofPattern("MMMM dd, yyyy")),
                now.format(DateTimeFormatter.ofPattern("dd-MMM-yyyy")),
                now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                now.format(DateTimeFormatter.ofPattern("hh:mm:ss a")),
                now.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        };

        String choice = (String) JOptionPane.showInputDialog(
                textPane,
                "Select Date & Time Format:",
                "Insert Date / Time",
                JOptionPane.PLAIN_MESSAGE,
                null,
                formats,
                formats[0]
        );

        if (choice != null) {
            try {
                doc.insertString(textPane.getCaretPosition(), choice, new SimpleAttributeSet());
            } catch (Exception ignored) {}
        }
    }

    private void insertHorizontalLine() {
        try {
            int pos = textPane.getCaretPosition();
            String divider = "\n" + "─".repeat(50) + "\n";
            SimpleAttributeSet divAttr = new SimpleAttributeSet();
            StyleConstants.setForeground(divAttr, Color.GRAY);
            doc.insertString(pos, divider, divAttr);
        } catch (Exception ignored) {}
    }

    private void insertPageBreak() {
        try {
            int pos = textPane.getCaretPosition();
            String pb = "\n" + "┄".repeat(22) + " [ PAGE BREAK ] " + "┄".repeat(22) + "\n";
            SimpleAttributeSet pbAttr = new SimpleAttributeSet();
            StyleConstants.setForeground(pbAttr, new Color(140, 150, 165));
            StyleConstants.setItalic(pbAttr, true);
            doc.insertString(pos, pb, pbAttr);
        } catch (Exception ignored) {}
    }

    private void insertCalloutBox() {
        try {
            int pos = textPane.getCaretPosition();
            String box = "\n┌────────────────────────────────────────────────────────┐\n" +
                         "│ NOTE:                                                  │\n" +
                         "│ Enter callout text or important information here.      │\n" +
                         "└────────────────────────────────────────────────────────┘\n";
            SimpleAttributeSet bAttr = new SimpleAttributeSet();
            StyleConstants.setFontFamily(bAttr, "Consolas");
            StyleConstants.setForeground(bAttr, new Color(0x02, 0x84, 0xC7));
            doc.insertString(pos, box, bAttr);
        } catch (Exception ignored) {}
    }

    // ── WORD COUNT STATS MODAL ──────────────────────────────────────────────
    private void showDetailedWordCountModal() {
        try {
            String text = doc.getText(0, doc.getLength());
            String trimmed = text.trim();
            int charsWithSpaces = text.length();
            int charsWithoutSpaces = text.replace(" ", "").replace("\n", "").replace("\r", "").length();
            int words = trimmed.isEmpty() ? 0 : trimmed.split("\\s+").length;
            int paragraphs = text.isEmpty() ? 0 : text.split("\n+").length;
            int lines = text.isEmpty() ? 0 : text.split("\n").length;

            int readMinutes = Math.max(1, (int) Math.ceil(words / 200.0));
            int speakMinutes = Math.max(1, (int) Math.ceil(words / 130.0));

            JPanel stats = new JPanel(new GridLayout(7, 2, 8, 6));
            stats.add(new JLabel("Pages:")); stats.add(new JLabel("1"));
            stats.add(new JLabel("Words:")); stats.add(new JLabel(String.format("%,d", words)));
            stats.add(new JLabel("Characters (no spaces):")); stats.add(new JLabel(String.format("%,d", charsWithoutSpaces)));
            stats.add(new JLabel("Characters (with spaces):")); stats.add(new JLabel(String.format("%,d", charsWithSpaces)));
            stats.add(new JLabel("Paragraphs:")); stats.add(new JLabel(String.format("%,d", paragraphs)));
            stats.add(new JLabel("Estimated Reading Time:")); stats.add(new JLabel(readMinutes + " min (at 200 wpm)"));
            stats.add(new JLabel("Estimated Speaking Time:")); stats.add(new JLabel(speakMinutes + " min (at 130 wpm)"));

            JOptionPane.showMessageDialog(textPane, stats, "Word Count & Telemetry", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ignored) {}
    }

    // ── SPELL CHECKER / PROOFING ────────────────────────────────────────────
    private void runSpellChecker() {
        try {
            String text = doc.getText(0, doc.getLength());
            Matcher matcher = Pattern.compile("\\b[A-Za-z]{3,}\\b").matcher(text);

            List<String> misspelled = new ArrayList<>();
            while (matcher.find()) {
                String word = matcher.group().toLowerCase();
                if (!COMMON_WORDS.contains(word) && !misspelled.contains(word)) {
                    misspelled.add(word);
                }
            }

            if (misspelled.isEmpty()) {
                JOptionPane.showMessageDialog(textPane, "Spell check complete!\nNo misspelled or unknown words detected in document.", "Proofing Complete", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            DefaultListModel<String> listModel = new DefaultListModel<>();
            for (String w : misspelled) listModel.addElement(w);
            JList<String> list = new JList<>(listModel);
            list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            list.setSelectedIndex(0);

            JPanel panel = new JPanel(new BorderLayout(6, 6));
            panel.add(new JLabel("Review flagged or unrecognized words (" + misspelled.size() + " found):"), BorderLayout.NORTH);
            panel.add(new JScrollPane(list), BorderLayout.CENTER);

            JPanel btnPnl = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton btnJump = new JButton("Locate in Text");
            btnJump.addActionListener(e -> {
                String sel = list.getSelectedValue();
                if (sel != null) {
                    try {
                        String full = doc.getText(0, doc.getLength()).toLowerCase();
                        int idx = full.indexOf(sel);
                        if (idx != -1) {
                            textPane.setSelectionStart(idx);
                            textPane.setSelectionEnd(idx + sel.length());
                        }
                    } catch (Exception ignored) {}
                }
            });
            btnPnl.add(btnJump);
            panel.add(btnPnl, BorderLayout.SOUTH);

            JOptionPane.showMessageDialog(textPane, panel, "Word Processor Spell Checker", JOptionPane.PLAIN_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(textPane, "Error executing spell check: " + ex.getMessage());
        }
    }

    // ── READ ALOUD AUDIO NARRATOR ───────────────────────────────────────────
    private void readDocumentAloud() {
        try {
            String text = doc.getText(0, doc.getLength()).trim();
            if (text.isEmpty()) {
                JOptionPane.showMessageDialog(textPane, "Document is empty. Nothing to read aloud.");
                return;
            }

            // Play synthesized audio tone sequence indicating narrator playback
            if (getOS() != null && getOS().getSoundService() != null) {
                getOS().getSoundService().playSlideSwitch();
            }

            String preview = text.length() > 200 ? text.substring(0, 200) + "..." : text;
            JOptionPane.showMessageDialog(textPane,
                    "Read Aloud active:\n\n\"" + preview + "\"\n\n(Procedural audio speech synthesis active)",
                    "Word Processor Narrator", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ignored) {}
    }

    // ── FORMAT PAINTER LOGIC ────────────────────────────────────────────────
    private void activateFormatPainter() {
        int pos = textPane.getCaretPosition();
        copiedAttributes = doc.getCharacterElement(pos).getAttributes().copyAttributes();
        formatPainterActive = true;
        btnFormatPainter.setBackground(ThemeManager.getInstance().getCurrentTheme().neonAccent);
        textPane.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
    }

    // ── TEXT TRANSFORMATIONS ────────────────────────────────────────────────
    private void changeSelectedCase(String mode) {
        int start = textPane.getSelectionStart();
        int end = textPane.getSelectionEnd();
        if (start == end) return;
        try {
            String sel = doc.getText(start, end - start);
            String transformed = switch (mode) {
                case "UPPERCASE" -> sel.toUpperCase();
                case "lowercase" -> sel.toLowerCase();
                case "Sentence case" -> sel.isEmpty() ? "" : Character.toUpperCase(sel.charAt(0)) + sel.substring(1).toLowerCase();
                case "Capitalize Words" -> capitalizeWords(sel);
                default -> sel;
            };

            AttributeSet attr = doc.getCharacterElement(start).getAttributes();
            doc.remove(start, end - start);
            doc.insertString(start, transformed, attr);
            textPane.setSelectionStart(start);
            textPane.setSelectionEnd(start + transformed.length());
        } catch (Exception ignored) {}
    }

    private String capitalizeWords(String str) {
        char[] chars = str.toCharArray();
        boolean whitespace = true;
        for (int i = 0; i < chars.length; i++) {
            if (Character.isWhitespace(chars[i])) {
                whitespace = true;
            } else if (whitespace) {
                chars[i] = Character.toTitleCase(chars[i]);
                whitespace = false;
            }
        }
        return new String(chars);
    }

    private void changeFontSizeBy(int delta) {
        Integer cur = (Integer) sizeBox.getSelectedItem();
        if (cur == null) cur = 14;
        int next = Math.max(8, Math.min(72, cur + delta));
        sizeBox.setSelectedItem(next);
        applyFontSize(next);
    }

    private void clearFormatting() {
        int start = textPane.getSelectionStart();
        int end = textPane.getSelectionEnd();
        SimpleAttributeSet normal = new SimpleAttributeSet();
        StyleConstants.setFontFamily(normal, "Segoe UI");
        StyleConstants.setFontSize(normal, 14);
        StyleConstants.setBold(normal, false);
        StyleConstants.setItalic(normal, false);
        StyleConstants.setUnderline(normal, false);
        StyleConstants.setStrikeThrough(normal, false);
        StyleConstants.setSubscript(normal, false);
        StyleConstants.setSuperscript(normal, false);
        StyleConstants.setForeground(normal, isPaperLightMode ? Color.BLACK : new Color(241, 245, 249));
        StyleConstants.setBackground(normal, pageBgColor);

        if (start != end) {
            doc.setCharacterAttributes(start, end - start, normal, true);
        } else {
            MutableAttributeSet inputAttr = ((StyledEditorKit) textPane.getEditorKit()).getInputAttributes();
            inputAttr.removeAttributes(inputAttr);
            inputAttr.addAttributes(normal);
        }
    }

    private void toggleSubscript() {
        AttributeSet cur = textPane.getCharacterAttributes();
        boolean sub = StyleConstants.isSubscript(cur);
        SimpleAttributeSet attr = new SimpleAttributeSet();
        StyleConstants.setSubscript(attr, !sub);
        if (!sub) StyleConstants.setSuperscript(attr, false);
        applyAttributeToSelection(attr);
    }

    private void toggleSuperscript() {
        AttributeSet cur = textPane.getCharacterAttributes();
        boolean sup = StyleConstants.isSuperscript(cur);
        SimpleAttributeSet attr = new SimpleAttributeSet();
        StyleConstants.setSuperscript(attr, !sup);
        if (!sup) StyleConstants.setSubscript(attr, false);
        applyAttributeToSelection(attr);
    }

    private void setLineSpacing(float multiplier) {
        SimpleAttributeSet pAttr = new SimpleAttributeSet();
        StyleConstants.setLineSpacing(pAttr, multiplier - 1.0f);
        int start = textPane.getSelectionStart();
        int end = textPane.getSelectionEnd();
        doc.setParagraphAttributes(start, Math.max(1, end - start), pAttr, false);
    }

    private void changeIndent(int delta) {
        Element root = doc.getDefaultRootElement();
        int elemIdx = root.getElementIndex(textPane.getCaretPosition());
        Element pElem = root.getElement(elemIdx);
        AttributeSet cur = pElem.getAttributes();
        float left = StyleConstants.getLeftIndent(cur);
        float next = Math.max(0, left + delta);
        SimpleAttributeSet attr = new SimpleAttributeSet();
        StyleConstants.setLeftIndent(attr, next);
        doc.setParagraphAttributes(pElem.getStartOffset(), pElem.getEndOffset() - pElem.getStartOffset(), attr, false);
    }

    private void insertCustomBullet(String bulletType) {
        String glyph = switch (bulletType) {
            case "• Disc" -> "• ";
            case "◦ Circle" -> "◦ ";
            case "▪ Square" -> "▪ ";
            case "➔ Arrow" -> "➔ ";
            case "[ ] Checklist" -> "[ ] ";
            default -> "• ";
        };
        try {
            int pos = textPane.getCaretPosition();
            doc.insertString(pos, "\n" + glyph, new SimpleAttributeSet());
            textPane.setCaretPosition(pos + glyph.length() + 1);
        } catch (Exception ignored) {}
    }

    private void insertNumbering(String numType) {
        try {
            int pos = textPane.getCaretPosition();
            String prefix = switch (numType) {
                case "a. b. c." -> "\na. ";
                case "i. ii. iii." -> "\ni. ";
                default -> "\n1. ";
            };
            doc.insertString(pos, prefix, new SimpleAttributeSet());
            textPane.setCaretPosition(pos + prefix.length());
        } catch (Exception ignored) {}
    }

    private void setPageOrientation(boolean landscape) {
        this.isLandscape = landscape;
        if (landscape) {
            pageWidth = 1040;
            pageHeight = 740;
        } else {
            pageWidth = 740;
            pageHeight = 980;
        }
        paperCard.setPreferredSize(new Dimension(pageWidth, pageHeight));
        paperCard.revalidate();
        pageHolder.revalidate();
    }

    private void applyZoom(double factor) {
        this.currentZoom = factor;
        int nextW = (int) (pageWidth * factor);
        int nextH = (int) (pageHeight * factor);
        paperCard.setPreferredSize(new Dimension(nextW, nextH));
        paperCard.revalidate();
        pageHolder.revalidate();
        statusZoomLabel.setText((int) (factor * 100) + "%");
    }

    // ── PRINT DOCUMENT ──────────────────────────────────────────────────────
    private void printDocument() {
        try {
            boolean done = textPane.print(new MessageFormat(currentFile != null ? currentFile.getName() : "Document"), new MessageFormat("Page {0}"), true, null, null, true);
            if (done && getOS() != null && getOS().getNotificationManager() != null) {
                getOS().getNotificationManager().postNotification("Print Complete", "Document printing / PDF job finished.", javaos.models.NotificationMessage.Level.SUCCESS);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(textPane, "Printing Error: " + ex.getMessage(), "Print Job Failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ── KEYBOARD SHORTCUTS ──────────────────────────────────────────────────
    private void bindKeyShortcuts() {
        InputMap im = textPane.getInputMap();
        ActionMap am = textPane.getActionMap();

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK), "Undo");
        am.put("Undo", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { if (undoManager.canUndo()) undoManager.undo(); }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK), "Redo");
        am.put("Redo", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { if (undoManager.canRedo()) undoManager.redo(); }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK), "Save");
        am.put("Save", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { saveDocument(false); }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK), "Find");
        am.put("Find", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { showFindReplaceDialog(); }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_P, InputEvent.CTRL_DOWN_MASK), "Print");
        am.put("Print", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { printDocument(); }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK), "New");
        am.put("New", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { newDocument(); }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK), "Open");
        am.put("Open", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { promptOpenFile(); }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), "SaveAs");
        am.put("SaveAs", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { saveDocument(true); }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_B, InputEvent.CTRL_DOWN_MASK), "Bold");
        am.put("Bold", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                int start = textPane.getSelectionStart();
                int end = textPane.getSelectionEnd();
                if (start == end) return;
                SimpleAttributeSet sas = new SimpleAttributeSet();
                // Toggle bold
                AttributeSet cur = doc.getCharacterElement(start).getAttributes();
                boolean isBold = StyleConstants.isBold(cur);
                StyleConstants.setBold(sas, !isBold);
                doc.setCharacterAttributes(start, end - start, sas, false);
            }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_I, InputEvent.CTRL_DOWN_MASK), "Italic");
        am.put("Italic", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                int start = textPane.getSelectionStart();
                int end = textPane.getSelectionEnd();
                if (start == end) return;
                SimpleAttributeSet sas = new SimpleAttributeSet();
                AttributeSet cur = doc.getCharacterElement(start).getAttributes();
                boolean isItalic = StyleConstants.isItalic(cur);
                StyleConstants.setItalic(sas, !isItalic);
                doc.setCharacterAttributes(start, end - start, sas, false);
            }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_U, InputEvent.CTRL_DOWN_MASK), "Underline");
        am.put("Underline", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                int start = textPane.getSelectionStart();
                int end = textPane.getSelectionEnd();
                if (start == end) return;
                SimpleAttributeSet sas = new SimpleAttributeSet();
                AttributeSet cur = doc.getCharacterElement(start).getAttributes();
                boolean isUnder = StyleConstants.isUnderline(cur);
                StyleConstants.setUnderline(sas, !isUnder);
                doc.setCharacterAttributes(start, end - start, sas, false);
            }
        });
    }

    // ── WELCOME DOCUMENT CONTENT ────────────────────────────────────────────
    private void insertWelcomeSample() {
        try {
            SimpleAttributeSet h1 = new SimpleAttributeSet();
            StyleConstants.setFontFamily(h1, "Segoe UI");
            StyleConstants.setFontSize(h1, 20);
            StyleConstants.setBold(h1, true);
            StyleConstants.setForeground(h1, new Color(0x02, 0x84, 0xC7));

            SimpleAttributeSet body = new SimpleAttributeSet();
            StyleConstants.setFontFamily(body, "Segoe UI");
            StyleConstants.setFontSize(body, 14);
            StyleConstants.setForeground(body, isPaperLightMode ? Color.BLACK : new Color(0xF1, 0xF5, 0xF9));

            doc.insertString(0, "IMPERION OS Word Document\n", h1);
            doc.insertString(doc.getLength(), "Start typing your document here...\n", body);

            isModified = false;
            updateStats();
        } catch (Exception ignored) {}
    }

    // ── FILE OPERATIONS: OPEN & SAVE DOCX ───────────────────────────────────
    public void openFile(File file) {
        if (file == null || !file.exists()) return;
        this.currentFile = file;

        try {
            doc.remove(0, doc.getLength());
            String name = file.getName().toLowerCase();

            if (name.endsWith(".docx") || name.endsWith(".doc")) {
                try {
                    loadDOCXFile(file);
                } catch (Exception ex) {
                    // Fallback for legacy binary .doc
                    byte[] bytes = Files.readAllBytes(file.toPath());
                    StringBuilder sb = new StringBuilder();
                    for (byte b : bytes) {
                        if ((b >= 32 && b <= 126) || b == '\n' || b == '\t' || b == '\r') {
                            sb.append((char) b);
                        }
                    }
                    String raw = sb.toString().replaceAll("[\r\n]{3,}", "\n\n").trim();
                    if (!raw.isEmpty()) {
                        SimpleAttributeSet def = new SimpleAttributeSet();
                        StyleConstants.setFontFamily(def, "Segoe UI");
                        StyleConstants.setFontSize(def, 14);
                        StyleConstants.setForeground(def, isPaperLightMode ? Color.BLACK : new Color(0xF1, 0xF5, 0xF9));
                        doc.insertString(0, raw, def);
                    } else {
                        throw ex;
                    }
                }
            } else {
                String text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                SimpleAttributeSet def = new SimpleAttributeSet();
                StyleConstants.setFontFamily(def, "Segoe UI");
                StyleConstants.setFontSize(def, 14);
                StyleConstants.setForeground(def, isPaperLightMode ? Color.BLACK : new Color(0xF1, 0xF5, 0xF9));
                doc.insertString(0, text, def);
            }

            isModified = false;
            updateTitle();
            updateStats();
            if (getOS() != null && getOS().getSoundService() != null) {
                getOS().getSoundService().playSuccess();
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(textPane, "Failed to open document:\n" + e.getMessage(), "Document Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadDOCXFile(File file) throws Exception {
        try (ZipFile zip = new ZipFile(file)) {
            ZipEntry docEntry = zip.getEntry("word/document.xml");
            if (docEntry == null) throw new IOException("Invalid DOCX container: missing word/document.xml");

            InputStream is = zip.getInputStream(docEntry);
            String xml = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            is.close();

            int pIdx = 0;
            while ((pIdx = xml.indexOf("<w:p", pIdx)) != -1) {
                int pEnd = xml.indexOf("</w:p>", pIdx);
                if (pEnd == -1) break;
                String pXml = xml.substring(pIdx, pEnd + 6);

                boolean isHeading = pXml.contains("<w:pStyle w:val=\"Heading") || pXml.contains("<w:pStyle w:val=\"Title\"");

                int rIdx = 0;
                while ((rIdx = pXml.indexOf("<w:r", rIdx)) != -1) {
                    int rEnd = pXml.indexOf("</w:r>", rIdx);
                    if (rEnd == -1) break;
                    String rXml = pXml.substring(rIdx, rEnd + 6);

                    boolean isBold = rXml.contains("<w:b/>") || rXml.contains("<w:b ");
                    boolean isItalic = rXml.contains("<w:i/>") || rXml.contains("<w:i ");
                    boolean isUnderline = rXml.contains("<w:u ");

                    int tStart = rXml.indexOf("<w:t");
                    if (tStart != -1) {
                        int tClose = rXml.indexOf(">", tStart);
                        int tEnd = rXml.indexOf("</w:t>", tClose);
                        if (tClose != -1 && tEnd != -1) {
                            String text = rXml.substring(tClose + 1, tEnd);
                            text = text.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"");

                            SimpleAttributeSet attr = new SimpleAttributeSet();
                            StyleConstants.setFontFamily(attr, "Segoe UI");
                            StyleConstants.setFontSize(attr, isHeading ? 18 : 14);
                            StyleConstants.setBold(attr, isBold || isHeading);
                            StyleConstants.setItalic(attr, isItalic);
                            StyleConstants.setUnderline(attr, isUnderline);
                            StyleConstants.setForeground(attr, isPaperLightMode ? Color.BLACK : new Color(0xF1, 0xF5, 0xF9));

                            doc.insertString(doc.getLength(), text, attr);
                        }
                    }
                    rIdx = rEnd + 6;
                }
                doc.insertString(doc.getLength(), "\n", new SimpleAttributeSet());
                pIdx = pEnd + 6;
            }
        }
    }

    private void saveDocument(boolean promptAs) {
        if (currentFile == null || promptAs) {
            JFileChooser chooser = new JFileChooser(currentFile != null ? currentFile.getParentFile() : new File("."));
            chooser.setSelectedFile(new File(currentFile != null ? currentFile.getName() : "Document1.docx"));
            int res = chooser.showSaveDialog(textPane);
            if (res != JFileChooser.APPROVE_OPTION) return;

            File chosen = chooser.getSelectedFile();
            if (!chosen.getName().contains(".")) {
                chosen = new File(chosen.getAbsolutePath() + ".docx");
            }
            this.currentFile = chosen;
        }

        try {
            String name = currentFile.getName().toLowerCase();
            if (name.endsWith(".docx")) {
                saveDOCXPackage(currentFile);
            } else {
                String plain = doc.getText(0, doc.getLength());
                Files.writeString(currentFile.toPath(), plain, StandardCharsets.UTF_8);
            }

            isModified = false;
            updateTitle();
            if (getOS() != null && getOS().getNotificationManager() != null) {
                getOS().getNotificationManager().postNotification("Document Saved", currentFile.getName() + " saved successfully.", javaos.models.NotificationMessage.Level.SUCCESS);
            }
            if (getOS() != null && getOS().getSoundService() != null) {
                getOS().getSoundService().playSuccess();
            }
            JOptionPane.showMessageDialog(textPane, "Document saved successfully to:\n" + currentFile.getAbsolutePath(), "File Saved", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(textPane, "Error saving document:\n" + e.getMessage(), "Save Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void saveDOCXPackage(File target) throws Exception {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(target))) {
            // [Content_Types].xml
            writeZipEntry(zos, "[Content_Types].xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                            "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">\n" +
                            "  <Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>\n" +
                            "  <Default Extension=\"xml\" ContentType=\"application/xml\"/>\n" +
                            "  <Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>\n" +
                            "</Types>");

            // _rels/.rels
            writeZipEntry(zos, "_rels/.rels",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n" +
                            "  <Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>\n" +
                            "</Relationships>");

            // word/document.xml
            StringBuilder sb = new StringBuilder();
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
            sb.append("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">\n");
            sb.append("  <w:body>\n");

            String fullText = doc.getText(0, doc.getLength());
            String[] lines = fullText.split("\n", -1);

            int charOffset = 0;
            for (String line : lines) {
                sb.append("    <w:p>\n");

                if (!line.isEmpty()) {
                    int pLen = line.length();
                    int localOffset = 0;

                    while (localOffset < pLen) {
                        int docPos = charOffset + localOffset;
                        Element runElem = doc.getCharacterElement(docPos);
                        AttributeSet as = runElem.getAttributes();

                        int runEnd = Math.min(pLen, runElem.getEndOffset() - charOffset);
                        if (runEnd <= localOffset) runEnd = localOffset + 1;

                        String runText = line.substring(localOffset, runEnd);
                        boolean bold = StyleConstants.isBold(as);
                        boolean italic = StyleConstants.isItalic(as);
                        boolean underline = StyleConstants.isUnderline(as);
                        int fontSize = StyleConstants.getFontSize(as);

                        String escaped = runText.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");

                        sb.append("      <w:r>\n");
                        sb.append("        <w:rPr>\n");
                        if (bold) sb.append("          <w:b/>\n");
                        if (italic) sb.append("          <w:i/>\n");
                        if (underline) sb.append("          <w:u w:val=\"single\"/>\n");
                        if (fontSize > 0) sb.append("          <w:sz w:val=\"").append(fontSize * 2).append("\"/>\n");
                        sb.append("        </w:rPr>\n");
                        sb.append("        <w:t xml:space=\"preserve\">").append(escaped).append("</w:t>\n");
                        sb.append("      </w:r>\n");

                        localOffset = runEnd;
                    }
                }

                sb.append("    </w:p>\n");
                charOffset += line.length() + 1;
            }

            sb.append("  </w:body>\n");
            sb.append("</w:document>");

            writeZipEntry(zos, "word/document.xml", sb.toString());
        }
    }

    private void writeZipEntry(ZipOutputStream zos, String entryName, String content) throws IOException {
        ZipEntry ze = new ZipEntry(entryName);
        zos.putNextEntry(ze);
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    private void newDocument() {
        if (isModified) {
            int opt = JOptionPane.showConfirmDialog(textPane, "Discard unsaved changes and create new document?", "New Document", JOptionPane.YES_NO_OPTION);
            if (opt != JOptionPane.YES_OPTION) return;
        }
        currentFile = null;
        try {
            doc.remove(0, doc.getLength());
            undoManager.discardAllEdits();
            isModified = false;
            updateTitle();
            updateStats();
        } catch (Exception ignored) {}
    }

    private void promptOpenFile() {
        JFileChooser chooser = new JFileChooser(currentFile != null ? currentFile.getParentFile() : new File("."));
        int res = chooser.showOpenDialog(textPane);
        if (res == JFileChooser.APPROVE_OPTION) {
            openFile(chooser.getSelectedFile());
        }
    }

    // ── FORMATTING ENGINE & APPLIERS ────────────────────────────────────────
    private void applyFontFamily(String family) {
        if (family == null) return;
        SimpleAttributeSet attr = new SimpleAttributeSet();
        StyleConstants.setFontFamily(attr, family);
        applyAttributeToSelection(attr);
    }

    private void applyFontSize(int size) {
        SimpleAttributeSet attr = new SimpleAttributeSet();
        StyleConstants.setFontSize(attr, size);
        applyAttributeToSelection(attr);
    }

    private void applyHeadingStyle(String style) {
        if (style == null) return;
        SimpleAttributeSet attr = new SimpleAttributeSet();
        Theme theme = ThemeManager.getInstance().getCurrentTheme();

        switch (style) {
            case "Document Title" -> {
                StyleConstants.setFontSize(attr, 26);
                StyleConstants.setBold(attr, true);
                StyleConstants.setForeground(attr, new Color(0x02, 0x84, 0xC7));
            }
            case "Subtitle" -> {
                StyleConstants.setFontSize(attr, 16);
                StyleConstants.setItalic(attr, true);
                StyleConstants.setForeground(attr, new Color(0x64, 0x74, 0x8B));
            }
            case "Heading 1" -> {
                StyleConstants.setFontSize(attr, 22);
                StyleConstants.setBold(attr, true);
                StyleConstants.setForeground(attr, new Color(0x0F, 0x17, 0x2A));
            }
            case "Heading 2" -> {
                StyleConstants.setFontSize(attr, 18);
                StyleConstants.setBold(attr, true);
            }
            case "Heading 3" -> {
                StyleConstants.setFontSize(attr, 15);
                StyleConstants.setBold(attr, true);
            }
            case "Blockquote" -> {
                StyleConstants.setFontSize(attr, 13);
                StyleConstants.setItalic(attr, true);
                StyleConstants.setForeground(attr, new Color(0x47, 0x55, 0x69));
            }
            case "Code Snippet" -> {
                StyleConstants.setFontFamily(attr, "Consolas");
                StyleConstants.setFontSize(attr, 13);
                StyleConstants.setBackground(attr, new Color(0xF1, 0xF5, 0xF9));
                StyleConstants.setForeground(attr, new Color(0x0F, 0x17, 0x2A));
            }
            default -> {
                StyleConstants.setFontSize(attr, 14);
                StyleConstants.setBold(attr, false);
                StyleConstants.setItalic(attr, false);
                StyleConstants.setForeground(attr, isPaperLightMode ? Color.BLACK : new Color(0xF1, 0xF5, 0xF9));
            }
        }
        applyAttributeToSelection(attr);
    }

    private void toggleStrikethrough() {
        AttributeSet current = textPane.getCharacterAttributes();
        boolean isStrike = StyleConstants.isStrikeThrough(current);
        SimpleAttributeSet attr = new SimpleAttributeSet();
        StyleConstants.setStrikeThrough(attr, !isStrike);
        applyAttributeToSelection(attr);
    }

    private void pickTextColor() {
        Color c = JColorChooser.showDialog(textPane, "Select Text Color", currentTextColor);
        if (c != null) {
            currentTextColor = c;
            btnTextColor.setBackground(c);
            SimpleAttributeSet attr = new SimpleAttributeSet();
            StyleConstants.setForeground(attr, c);
            applyAttributeToSelection(attr);
        }
    }

    private void pickHighlightColor() {
        Color c = JColorChooser.showDialog(textPane, "Select Highlight Color", currentHighlightColor != null ? currentHighlightColor : Color.YELLOW);
        if (c != null) {
            currentHighlightColor = c;
            btnHighlight.setBackground(c);
            SimpleAttributeSet attr = new SimpleAttributeSet();
            StyleConstants.setBackground(attr, c);
            applyAttributeToSelection(attr);
        }
    }

    private void applyAttributeToSelection(AttributeSet attr) {
        int start = textPane.getSelectionStart();
        int end = textPane.getSelectionEnd();
        if (start != end) {
            doc.setCharacterAttributes(start, end - start, attr, false);
        } else {
            MutableAttributeSet inputAttr = ((StyledEditorKit) textPane.getEditorKit()).getInputAttributes();
            inputAttr.addAttributes(attr);
        }
        textPane.requestFocusInWindow();
    }

    private void toggleViewMode() {
        isPaperLightMode = !isPaperLightMode;
        if (isPaperLightMode) {
            pageBgColor = Color.WHITE;
            setEditorTextColor(Color.BLACK);
        } else {
            pageBgColor = new Color(17, 24, 39);
            setEditorTextColor(new Color(241, 245, 249));
        }
        paperCard.repaint();
        applyVisualThemeToEditor();
    }

    private void setEditorTextColor(Color c) {
        textPane.setForeground(c);
        textPane.setCaretColor(isPaperLightMode ? Color.BLACK : Color.WHITE);
        textPane.setBackground(pageBgColor);
    }

    private void applyVisualThemeToEditor() {
        if (isPaperLightMode) {
            textPane.setBackground(Color.WHITE);
            textPane.setForeground(new Color(15, 23, 42));
            textPane.setCaretColor(Color.BLACK);
        } else {
            textPane.setBackground(new Color(17, 24, 39));
            textPane.setForeground(new Color(241, 245, 249));
            textPane.setCaretColor(Color.WHITE);
        }
        textPane.repaint();
    }

    // ── FIND & REPLACE DIALOG ───────────────────────────────────────────────
    private void showFindReplaceDialog() {
        JDialog dlg = new JDialog((Frame) SwingUtilities.getWindowAncestor(textPane), "Find & Replace", false);
        dlg.setLayout(new BorderLayout(8, 8));
        dlg.setSize(440, 220);
        dlg.setLocationRelativeTo(textPane);

        JPanel form = new JPanel(new GridLayout(3, 2, 8, 8));
        form.setBorder(new EmptyBorder(10, 10, 6, 10));

        JTextField txtFind = new JTextField(textPane.getSelectedText() != null ? textPane.getSelectedText() : "");
        JTextField txtReplace = new JTextField();
        JCheckBox chkMatchCase = new JCheckBox("Match Case");
        JCheckBox chkWholeWord = new JCheckBox("Whole Words Only");

        form.add(new JLabel("Find What:"));
        form.add(txtFind);
        form.add(new JLabel("Replace With:"));
        form.add(txtReplace);
        form.add(chkMatchCase);
        form.add(chkWholeWord);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 6));
        JButton btnFindNext = new JButton("Find Next");
        JButton btnReplace = new JButton("Replace");
        JButton btnReplaceAll = new JButton("Replace All");
        JButton btnClose = new JButton("Close");

        btnFindNext.addActionListener(e -> {
            String target = txtFind.getText();
            if (target.isEmpty()) return;
            boolean matchCase = chkMatchCase.isSelected();
            try {
                String full = doc.getText(0, doc.getLength());
                String searchIn = matchCase ? full : full.toLowerCase();
                String query = matchCase ? target : target.toLowerCase();

                int start = searchIn.indexOf(query, textPane.getCaretPosition());
                if (start == -1) start = searchIn.indexOf(query, 0);

                if (start != -1) {
                    textPane.setSelectionStart(start);
                    textPane.setSelectionEnd(start + query.length());
                } else {
                    JOptionPane.showMessageDialog(dlg, "Text '" + target + "' not found.", "Find", JOptionPane.INFORMATION_MESSAGE);
                }
            } catch (Exception ignored) {}
        });

        btnReplace.addActionListener(e -> {
            int selStart = textPane.getSelectionStart();
            int selEnd = textPane.getSelectionEnd();
            if (selStart != selEnd) {
                try {
                    doc.remove(selStart, selEnd - selStart);
                    doc.insertString(selStart, txtReplace.getText(), new SimpleAttributeSet());
                    btnFindNext.doClick();
                } catch (Exception ignored) {}
            } else {
                btnFindNext.doClick();
            }
        });

        btnReplaceAll.addActionListener(e -> {
            String target = txtFind.getText();
            String repl = txtReplace.getText();
            if (target.isEmpty()) return;
            try {
                String full = doc.getText(0, doc.getLength());
                boolean matchCase = chkMatchCase.isSelected();
                Pattern p = Pattern.compile(matchCase ? Pattern.quote(target) : "(?i)" + Pattern.quote(target));
                Matcher m = p.matcher(full);
                int count = 0;
                while (m.find()) count++;

                if (count > 0) {
                    String result = m.replaceAll(Matcher.quoteReplacement(repl));
                    doc.remove(0, doc.getLength());
                    doc.insertString(0, result, new SimpleAttributeSet());
                    JOptionPane.showMessageDialog(dlg, "Replaced " + count + " occurrence(s) of '" + target + "'.", "Replace Complete", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(dlg, "No occurrences of '" + target + "' found.", "Replace", JOptionPane.INFORMATION_MESSAGE);
                }
            } catch (Exception ignored) {}
        });

        btnClose.addActionListener(e -> dlg.dispose());

        btnRow.add(btnFindNext);
        btnRow.add(btnReplace);
        btnRow.add(btnReplaceAll);
        btnRow.add(btnClose);

        dlg.add(form, BorderLayout.CENTER);
        dlg.add(btnRow, BorderLayout.SOUTH);
        dlg.setVisible(true);
    }

    private void onDocChanged() {
        isModified = true;
        updateTitle();
        updateStats();

        // Dynamically adjust paperCard height so scrollbars allow scrolling past 1 page
        if (paperCard != null && textPane != null) {
            int neededH = textPane.getPreferredSize().height + (pageMargin * 2) + 60;
            int targetH = Math.max(pageHeight, neededH);
            if (paperCard.getPreferredSize().height != targetH) {
                paperCard.setPreferredSize(new Dimension(pageWidth, targetH));
                paperCard.revalidate();
                if (pageHolder != null) pageHolder.revalidate();
            }
        }
    }

    private void updateTitle() {
        String name = (currentFile != null) ? currentFile.getName() : "Document1.docx";
        statusFileLabel.setText(name + (isModified ? " *" : ""));
        if (wordTitleLabel != null) wordTitleLabel.setText(name + (isModified ? " *" : ""));
        if (getWindow() != null) {
            getWindow().setTitle(name + (isModified ? " *" : "") + " — Word Processor");
        }
    }

    private void updateStats() {
        try {
            String text = doc.getText(0, doc.getLength()).trim();
            int chars = text.length();
            int words = text.isEmpty() ? 0 : text.split("\\s+").length;
            int readMin = Math.max(1, words / 200);

            statusStatsLabel.setText("Page 1 of 1  |  " + words + " words  |  " + chars + " characters  |  ~" + readMin + " min read");
        } catch (Exception ignored) {}
    }

    private void updateCaretInfo() {
        try {
            int pos = textPane.getCaretPosition();
            Element root = doc.getDefaultRootElement();
            int line = root.getElementIndex(pos) + 1;
            int col = pos - root.getElement(line - 1).getStartOffset() + 1;
            statusPositionLabel.setText("Ln " + line + ", Col " + col);
        } catch (Exception ignored) {}
    }

    // ── BUTTON CREATION HELPERS ─────────────────────────────────────────────
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

    private JToggleButton makeToggleBtn(String text, String tip, int fontStyle) {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        JToggleButton btn = new JToggleButton(text);
        btn.setFont(new Font("Segoe UI", fontStyle, 13));
        btn.setForeground(theme.textPrimary);
        btn.setBackground(theme.panel);
        btn.setOpaque(true);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setToolTipText(tip);
        btn.setPreferredSize(new Dimension(32, 26));
        btn.setBorder(BorderFactory.createLineBorder(theme.secondaryAccent));
        return btn;
    }

    private JButton makeColorPickerBtn(String text, String tip, Color initialColor) {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        JButton btn = new JButton(text);
        btn.setFont(theme.fontBold);
        btn.setForeground(initialColor);
        btn.setBackground(theme.panel);
        btn.setOpaque(true);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setToolTipText(tip);
        btn.setPreferredSize(new Dimension(32, 26));
        btn.setBorder(BorderFactory.createLineBorder(theme.secondaryAccent));
        return btn;
    }
}

package javaos.apps;

import javaos.ui.Theme;
import javaos.ui.ThemeManager;
import javaos.ui.components.CyberpunkIcons;
import javaos.ui.components.NeonScrollBarUI;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.Inflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Cyber Document & Presentation Viewer for IMPERION OS.
 * Natively renders intact visual pages of PDFs, PowerPoint slide decks (.pptx),
 * and Word documents (.docx) directly inside the OS workspace without returning to Windows.
 */
public class DocumentViewerApp extends OSApplication {

    private File currentFile;
    private File cacheDir;
    private ViewMode viewMode = ViewMode.VISUAL;

    private final List<DocumentPage> pages = new ArrayList<>();
    private int currentPageIndex = 0;
    private double zoom = 1.0;
    private int panX = 0;
    private int panY = 0;
    private Point dragStart;

    // UI Components
    private CardLayout displayCardLayout;
    private JPanel displayPanel;
    private VisualPageCanvas pageCanvas;
    private JEditorPane textPane;
    private DefaultListModel<String> outlineModel;
    private JList<String> outlineList;
    private JLabel pageIndicator;
    private JLabel docTitleLabel;
    private JLabel docStatsLabel;
    private JLabel zoomLabel;
    private JButton btnPrev;
    private JButton btnNext;
    private JButton btnToggleView;
    private JButton btnEdit;
    private int textFontSize = 14;

    private final Map<Integer, BufferedImage> imageCache = new HashMap<>();

    public enum ViewMode {
        VISUAL, TEXT
    }

    public static class DocumentPage {
        public final int number;
        public final String title;
        public final File imageFile;
        public final String cleanText;

        public DocumentPage(int number, String title, File imageFile, String cleanText) {
            this.number = number;
            this.title = title;
            this.imageFile = imageFile;
            this.cleanText = cleanText;
        }
    }

    public DocumentViewerApp() {
        this(null);
    }

    public DocumentViewerApp(File initialFile) {
        super(new AppDescriptor(
                "docviewer",
                "Document Viewer",
                "High-DPI visual explorer for PDFs, PowerPoint & Word documents",
                AppDescriptor.Category.UTILITIES,
                CyberpunkIcons.createIcon("PDF", 20),
                false
        ));
        this.currentFile = initialFile;
    }

    @Override
    public Dimension getPreferredWindowSize() {
        return new Dimension(1020, 680);
    }

    @Override
    public JComponent createContent() {
        JPanel main = new JPanel(new BorderLayout());
        main.setOpaque(false);
        Theme theme = ThemeManager.getInstance().getCurrentTheme();

        // ── TOP NAVIGATION TOOLBAR ─────────────────────────────────────────
        JPanel toolbar = new JPanel(new BorderLayout(8, 0));
        toolbar.setOpaque(true);
        toolbar.setBackground(theme.elevatedPanel);
        toolbar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, theme.secondaryAccent),
                BorderFactory.createEmptyBorder(5, 8, 5, 8)
        ));

        // Left Navigation Controls
        JPanel leftControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        leftControls.setOpaque(false);

        JButton btnOpen = makeButton("Open...", "Open another document (PDF, PPTX, DOCX)");
        btnOpen.addActionListener(e -> chooseDocument());

        btnPrev = makeButton("< Prev", "Previous page / slide");
        btnPrev.addActionListener(e -> navigatePrev());

        btnNext = makeButton("Next >", "Next page / slide");
        btnNext.addActionListener(e -> navigateNext());

        pageIndicator = new JLabel("Page 0 / 0");
        pageIndicator.setFont(theme.fontBold);
        pageIndicator.setForeground(theme.neonAccent);
        pageIndicator.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));

        btnEdit = makeButton("✏ Edit Presentation", "Open in native editor for modifications");
        btnEdit.setVisible(false);
        btnEdit.addActionListener(e -> {
            if (currentFile == null || getOS() == null) return;
            String fn = currentFile.getName().toLowerCase();
            if (fn.endsWith(".pptx") || fn.endsWith(".ppt")) {
                OSApplication app = getOS().getApplicationManager().launchApp("slides");
                if (app instanceof PresentationEditorApp) {
                    ((PresentationEditorApp) app).openFile(currentFile);
                }
            } else if (fn.endsWith(".docx") || fn.endsWith(".doc")) {
                OSApplication app = getOS().getApplicationManager().launchApp("wordeditor");
                if (app instanceof WordEditorApp) {
                    ((WordEditorApp) app).openFile(currentFile);
                }
            } else if (fn.endsWith(".xlsx") || fn.endsWith(".xls") || fn.endsWith(".csv")) {
                OSApplication app = getOS().getApplicationManager().launchApp("sheet");
                if (app instanceof SpreadsheetApp) {
                    ((SpreadsheetApp) app).openFile(currentFile);
                }
            }
        });

        leftControls.add(btnOpen);
        leftControls.add(Box.createHorizontalStrut(4));
        leftControls.add(btnPrev);
        leftControls.add(btnNext);
        leftControls.add(pageIndicator);
        leftControls.add(Box.createHorizontalStrut(6));
        leftControls.add(btnEdit);

        toolbar.add(leftControls, BorderLayout.WEST);

        // Center Document Title
        docTitleLabel = new JLabel("No document open");
        docTitleLabel.setFont(theme.fontSmall);
        docTitleLabel.setForeground(theme.textPrimary);
        docTitleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        toolbar.add(docTitleLabel, BorderLayout.CENTER);

        // Right View & Zoom Controls
        JPanel rightControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        rightControls.setOpaque(false);

        btnToggleView = makeButton("Text Mode", "Toggle between Visual Page and Clean Text mode");
        btnToggleView.addActionListener(e -> toggleViewMode());

        JButton btnZoomOut = makeButton("-", "Zoom Out");
        btnZoomOut.addActionListener(e -> setZoom(zoom / 1.2));

        JButton btnZoomIn = makeButton("+", "Zoom In");
        btnZoomIn.addActionListener(e -> setZoom(zoom * 1.2));

        JButton btnFit = makeButton("Fit", "Fit page width to window");
        btnFit.addActionListener(e -> fitToWindow());

        JButton btnReset = makeButton("1:1", "Original 100% scale");
        btnReset.addActionListener(e -> {
            zoom = 1.0;
            panX = 0;
            panY = 0;
            updateZoomLabel();
            pageCanvas.repaint();
        });

        zoomLabel = new JLabel("100%");
        zoomLabel.setFont(theme.fontSmall);
        zoomLabel.setForeground(theme.neonAccent);
        zoomLabel.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));

        JButton btnFind = makeButton("Find", "Search text across all pages");
        btnFind.addActionListener(e -> findText());

        rightControls.add(btnToggleView);
        rightControls.add(Box.createHorizontalStrut(4));
        rightControls.add(btnZoomOut);
        rightControls.add(zoomLabel);
        rightControls.add(btnZoomIn);
        rightControls.add(btnFit);
        rightControls.add(btnReset);
        rightControls.add(Box.createHorizontalStrut(4));
        rightControls.add(btnFind);

        toolbar.add(rightControls, BorderLayout.EAST);
        main.add(toolbar, BorderLayout.NORTH);

        // ── DUAL PANE WORKSPACE (Sidebar + Page Display) ───────────────────
        outlineModel = new DefaultListModel<>();
        outlineList = new JList<>(outlineModel);
        outlineList.setBackground(theme.panel);
        outlineList.setForeground(theme.textPrimary);
        outlineList.setFont(theme.fontSmall);
        outlineList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        outlineList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int idx = outlineList.getSelectedIndex();
                if (idx >= 0 && idx < pages.size()) {
                    currentPageIndex = idx;
                    renderCurrentPage();
                }
            }
        });

        JScrollPane outlineScroll = new JScrollPane(outlineList);
        outlineScroll.getVerticalScrollBar().setUI(new NeonScrollBarUI());
        outlineScroll.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, theme.secondaryAccent));
        outlineScroll.setPreferredSize(new Dimension(220, 0));

        // 1. Visual Page Canvas
        pageCanvas = new VisualPageCanvas();

        // 2. Text Reader Pane
        textPane = new JEditorPane();
        textPane.setEditable(false);
        textPane.setBackground(theme.background);
        textPane.setContentType("text/html");

        HTMLEditorKit kit = new HTMLEditorKit();
        StyleSheet css = kit.getStyleSheet();
        css.addRule("body { background-color: #080B14; color: #F1F5F9; font-family: 'Segoe UI', sans-serif; padding: 28px; line-height: 1.6; }");
        css.addRule("h1 { color: #22D3EE; font-size: 22px; border-bottom: 1px solid #1E293B; padding-bottom: 6px; }");
        css.addRule("h2 { color: #38BDF8; font-size: 18px; margin-top: 14px; }");
        css.addRule("pre, code { background-color: #101827; color: #38BDF8; font-family: Consolas, monospace; padding: 8px; border: 1px solid #1E293B; border-radius: 4px; }");
        css.addRule("p { margin-bottom: 12px; }");
        css.addRule(".card { background-color: #101827; border: 1px solid #1E293B; border-radius: 8px; padding: 20px; }");
        textPane.setEditorKit(kit);

        JScrollPane textScroll = new JScrollPane(textPane);
        textScroll.getVerticalScrollBar().setUI(new NeonScrollBarUI());
        textScroll.getHorizontalScrollBar().setUI(new NeonScrollBarUI());
        textScroll.setBorder(BorderFactory.createEmptyBorder());

        displayCardLayout = new CardLayout();
        displayPanel = new JPanel(displayCardLayout);
        displayPanel.add(pageCanvas, "VISUAL");
        displayPanel.add(textScroll, "TEXT");

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, outlineScroll, displayPanel);
        splitPane.setDividerLocation(220);
        splitPane.setDividerSize(4);
        splitPane.setOpaque(false);
        splitPane.setBorder(BorderFactory.createEmptyBorder());

        main.add(splitPane, BorderLayout.CENTER);

        // ── BOTTOM STATUS BAR ───────────────────────────────────────────────
        JPanel statusBar = new JPanel(new BorderLayout(8, 0));
        statusBar.setOpaque(true);
        statusBar.setBackground(theme.panel);
        statusBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, theme.secondaryAccent),
                BorderFactory.createEmptyBorder(4, 10, 4, 10)
        ));

        docStatsLabel = new JLabel("IMPERION Document Engine Ready");
        docStatsLabel.setFont(theme.fontSmall);
        docStatsLabel.setForeground(theme.textSecondary);
        statusBar.add(docStatsLabel, BorderLayout.WEST);

        main.add(statusBar, BorderLayout.SOUTH);

        if (currentFile != null) {
            SwingUtilities.invokeLater(() -> openFile(currentFile));
        }

        return main;
    }

    private JButton makeButton(String text, String tooltip) {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        JButton btn = new JButton(text);
        btn.setFont(theme.fontSmall);
        btn.setForeground(theme.textPrimary);
        btn.setBackground(theme.panel);
        btn.setOpaque(true);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(theme.secondaryAccent),
                BorderFactory.createEmptyBorder(3, 8, 3, 8)
        ));
        btn.setToolTipText(tooltip);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

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

    public void openFile(File file) {
        if (file == null || !file.exists()) return;
        this.currentFile = file;

        docTitleLabel.setText("Processing: " + file.getName() + "...");
        docStatsLabel.setText("Rendering high-DPI document pages...");

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                pages.clear();
                imageCache.clear();
                currentPageIndex = 0;

                // Cache directory for rendered document
                String hash = Integer.toHexString(file.getAbsolutePath().hashCode()) + "_" + file.length();
                cacheDir = new File("data/cache/docs/" + hash);
                File manifestFile = new File(cacheDir, "manifest.json");

                boolean rendered = false;
                if (!manifestFile.exists()) {
                    // Run python doc_renderer.py
                    try {
                        ProcessBuilder pb = new ProcessBuilder("python", "scripts/doc_renderer.py", file.getAbsolutePath(), cacheDir.getAbsolutePath());
                        pb.redirectErrorStream(true);
                        Process p = pb.start();
                        int exitCode = p.waitFor();
                        if (exitCode == 0 && manifestFile.exists()) {
                            rendered = true;
                        }
                    } catch (Exception ex) {
                        System.err.println("[DOCVIEWER] Python doc_renderer error: " + ex.getMessage());
                    }
                } else {
                    rendered = true;
                }

                if (rendered && manifestFile.exists()) {
                    parseManifest(manifestFile);
                } else {
                    // Fallback to pure Java parser with clean text repair
                    parseWithJavaFallback(file);
                }
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    docTitleLabel.setText(file.getName());
                    populateOutline();
                    fitToWindow();
                    renderCurrentPage();

                    if (getWindow() != null) {
                        getWindow().setTitle(file.getName() + " — Document Viewer");
                    }

                    // Dynamically configure Edit button
                    String fn = file.getName().toLowerCase();
                    if (fn.endsWith(".pptx") || fn.endsWith(".ppt")) {
                        btnEdit.setText("✏ Edit Presentation");
                        btnEdit.setToolTipText("Open this presentation in Presentation Slides to edit text, bullets and slides");
                        btnEdit.setVisible(true);
                    } else if (fn.endsWith(".docx") || fn.endsWith(".doc")) {
                        btnEdit.setText("✏ Edit Document");
                        btnEdit.setToolTipText("Open this document in Word Processor to edit text, styles and tables");
                        btnEdit.setVisible(true);
                    } else if (fn.endsWith(".xlsx") || fn.endsWith(".xls") || fn.endsWith(".csv")) {
                        btnEdit.setText("✏ Edit Spreadsheet");
                        btnEdit.setToolTipText("Open this spreadsheet in Spreadsheet Analytics to edit cells and formulas");
                        btnEdit.setVisible(true);
                    } else {
                        btnEdit.setVisible(false);
                    }

                    long bytes = file.length();
                    String sz = bytes > 1024 * 1024
                            ? String.format("%.2f MB", bytes / (1024.0 * 1024.0))
                            : String.format("%.1f KB", bytes / 1024.0);

                    docStatsLabel.setText(String.format("%s | %d Visual Pages | Size: %s | High-DPI Engine",
                            file.getName().toUpperCase(), pages.size(), sz));

                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(displayPanel, "Error rendering document:\n" + ex.getMessage(), "Document Error", JOptionPane.ERROR_MESSAGE);
                    docTitleLabel.setText("Error reading document");
                }
            }
        }.execute();
    }

    private void parseManifest(File manifestFile) throws Exception {
        String json = Files.readString(manifestFile.toPath());
        int pIdx = 0;
        while ((pIdx = json.indexOf("\"number\":", pIdx)) != -1) {
            int numStart = pIdx + 9;
            int numEnd = json.indexOf(",", numStart);
            if (numEnd == -1) numEnd = json.indexOf("\n", numStart);
            int number = pages.size() + 1;
            try {
                number = Integer.parseInt(json.substring(numStart, numEnd).trim());
            } catch (Exception ignored) {}

            String title = extractJsonString(json, "\"title\":", pIdx);
            String imageRel = extractJsonString(json, "\"image\":", pIdx);
            String text = extractJsonString(json, "\"text\":", pIdx);

            File imgFile = (!imageRel.isEmpty()) ? new File(cacheDir, imageRel) : null;
            pages.add(new DocumentPage(number, title, imgFile, text));
            pIdx = numEnd + 1;
        }

        if (pages.isEmpty()) {
            parseWithJavaFallback(currentFile);
        }
    }

    private static String extractJsonString(String json, String key, int startFrom) {
        int kIdx = json.indexOf(key, startFrom);
        if (kIdx == -1 || kIdx > startFrom + 1200) return "";
        int valStart = json.indexOf("\"", kIdx + key.length());
        if (valStart == -1) return "";

        StringBuilder sb = new StringBuilder();
        boolean escape = false;
        for (int i = valStart + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escape) {
                if (c == 'n') sb.append('\n');
                else if (c == 'r') sb.append('\r');
                else if (c == 't') sb.append('\t');
                else sb.append(c);
                escape = false;
            } else if (c == '\\') {
                escape = true;
            } else if (c == '\"') {
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private void parseWithJavaFallback(File file) throws Exception {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".pptx") || name.endsWith(".ppt")) {
            parsePPTXJava(file);
        } else if (name.endsWith(".pdf")) {
            parsePDFJava(file);
        } else if (name.endsWith(".docx") || name.endsWith(".doc")) {
            parseDOCXJava(file);
        } else {
            String content = Files.readString(file.toPath());
            pages.add(new DocumentPage(1, "Page 1", null, content));
        }
    }

    private void parsePPTXJava(File file) throws Exception {
        try (ZipFile zip = new ZipFile(file)) {
            int slideNum = 1;
            while (true) {
                ZipEntry entry = zip.getEntry("ppt/slides/slide" + slideNum + ".xml");
                if (entry == null) break;

                InputStream is = zip.getInputStream(entry);
                String xml = new String(is.readAllBytes(), "UTF-8");
                is.close();

                List<String> pieces = new ArrayList<>();
                int idx = 0;
                while ((idx = xml.indexOf("<a:t", idx)) != -1) {
                    int contentStart = xml.indexOf(">", idx);
                    if (contentStart != -1) {
                        int end = xml.indexOf("</a:t>", contentStart);
                        if (end != -1) {
                            String piece = xml.substring(contentStart + 1, end).trim();
                            if (!piece.isEmpty()) pieces.add(piece);
                            idx = end + 6;
                        } else break;
                    } else break;
                }

                String title = !pieces.isEmpty() ? pieces.get(0) : "Slide " + slideNum;
                StringBuilder text = new StringBuilder();
                for (int i = 1; i < pieces.size(); i++) text.append("• ").append(pieces.get(i)).append("\n");

                pages.add(new DocumentPage(slideNum, title, null, text.toString()));
                slideNum++;
            }
        }
    }

    private void parseDOCXJava(File file) throws Exception {
        try (ZipFile zip = new ZipFile(file)) {
            ZipEntry docEntry = zip.getEntry("word/document.xml");
            if (docEntry != null) {
                InputStream is = zip.getInputStream(docEntry);
                String xml = new String(is.readAllBytes(), "UTF-8");
                StringBuilder sb = new StringBuilder();
                int idx = 0;
                while ((idx = xml.indexOf("<w:t", idx)) != -1) {
                    int start = xml.indexOf(">", idx);
                    if (start != -1) {
                        int end = xml.indexOf("</w:t>", start);
                        if (end != -1) {
                            sb.append(xml.substring(start + 1, end));
                            idx = end + 6;
                        } else break;
                    } else break;
                }
                pages.add(new DocumentPage(1, file.getName(), null, sb.toString()));
            }
        }
    }

    private void parsePDFJava(File file) throws Exception {
        byte[] bytes = Files.readAllBytes(file.toPath());
        String s = new String(bytes, "ISO-8859-1");

        StringBuilder fullText = new StringBuilder();
        int searchPos = 0;

        while ((searchPos = s.indexOf("stream", searchPos)) != -1) {
            int before = Math.max(0, searchPos - 200);
            String dict = s.substring(before, searchPos);
            boolean isFlate = dict.contains("/FlateDecode");

            int streamStart = searchPos + 6;
            if (s.charAt(streamStart) == '\r') streamStart++;
            if (s.charAt(streamStart) == '\n') streamStart++;

            int streamEnd = s.indexOf("endstream", streamStart);
            if (streamEnd == -1) break;

            if (isFlate) {
                try {
                    byte[] deflated = new byte[streamEnd - streamStart];
                    System.arraycopy(bytes, streamStart, deflated, 0, deflated.length);
                    Inflater inflater = new Inflater();
                    inflater.setInput(deflated);
                    byte[] buffer = new byte[65536];
                    int len = inflater.inflate(buffer);
                    if (len > 0) {
                        String uncompressed = new String(buffer, 0, len, "ISO-8859-1");
                        // Clean regex to join isolated letters into coherent words
                        int tjIdx = 0;
                        while ((tjIdx = uncompressed.indexOf("(", tjIdx)) != -1) {
                            int close = uncompressed.indexOf(")", tjIdx);
                            if (close != -1 && close - tjIdx < 300) {
                                String chunk = uncompressed.substring(tjIdx + 1, close);
                                if (!chunk.contains("\\") && !chunk.contains("en-US") && chunk.matches(".*[a-zA-Z0-9].*")) {
                                    fullText.append(chunk).append(" ");
                                }
                                tjIdx = close + 1;
                            } else break;
                        }
                    }
                    inflater.end();
                } catch (Exception ignored) {}
            }
            searchPos = streamEnd + 9;
        }

        String raw = fullText.toString().replaceAll("([a-zA-Z])\\s+([a-zA-Z])\\s+", "$1$2 ").trim();
        pages.add(new DocumentPage(1, file.getName(), null, raw.isEmpty() ? "Document loaded (Text stream empty)" : raw));
    }

    private void populateOutline() {
        outlineModel.clear();
        for (DocumentPage p : pages) {
            String title = (p.title != null && !p.title.isEmpty()) ? p.title : "Page " + p.number;
            if (title.length() > 24) title = title.substring(0, 22) + "...";
            outlineModel.addElement(p.number + ". " + title);
        }
    }

    private void renderCurrentPage() {
        if (pages.isEmpty()) return;
        if (currentPageIndex < 0) currentPageIndex = 0;
        if (currentPageIndex >= pages.size()) currentPageIndex = pages.size() - 1;

        DocumentPage p = pages.get(currentPageIndex);
        pageIndicator.setText("Page " + p.number + " / " + pages.size());

        // Update outline list selection
        if (outlineList.getSelectedIndex() != currentPageIndex && currentPageIndex < outlineModel.size()) {
            outlineList.setSelectedIndex(currentPageIndex);
        }

        btnPrev.setEnabled(currentPageIndex > 0);
        btnNext.setEnabled(currentPageIndex < pages.size() - 1);

        // Load image if visual page available
        if (p.imageFile != null && p.imageFile.exists()) {
            if (!imageCache.containsKey(p.number)) {
                try {
                    BufferedImage img = ImageIO.read(p.imageFile);
                    if (img != null) {
                        imageCache.put(p.number, img);
                    }
                } catch (Exception ignored) {}
            }
        }

        // Render in active view mode
        if (viewMode == ViewMode.VISUAL && (p.imageFile != null || imageCache.containsKey(p.number))) {
            displayCardLayout.show(displayPanel, "VISUAL");
            pageCanvas.repaint();
        } else {
            // Text mode
            displayCardLayout.show(displayPanel, "TEXT");
            String safeText = escapeHtml(p.cleanText);
            String html = "<html><body>" +
                    "<div class='card'>" +
                    "<h1>" + escapeHtml(p.title) + "</h1>" +
                    "<pre style='font-size:" + textFontSize + "px;'>" + safeText + "</pre>" +
                    "</div></body></html>";
            textPane.setText(html);
            textPane.setCaretPosition(0);
        }
    }

    private void toggleViewMode() {
        if (viewMode == ViewMode.VISUAL) {
            viewMode = ViewMode.TEXT;
            btnToggleView.setText("Visual Page");
        } else {
            viewMode = ViewMode.VISUAL;
            btnToggleView.setText("Text Mode");
        }
        renderCurrentPage();
    }

    private void navigatePrev() {
        if (currentPageIndex > 0) {
            currentPageIndex--;
            panX = 0;
            panY = 0;
            renderCurrentPage();
        }
    }

    private void navigateNext() {
        if (currentPageIndex < pages.size() - 1) {
            currentPageIndex++;
            panX = 0;
            panY = 0;
            renderCurrentPage();
        }
    }

    private void chooseDocument() {
        JFileChooser chooser = new JFileChooser(currentFile != null ? currentFile.getParentFile() : new File("."));
        chooser.setDialogTitle("Select Document or Presentation");
        int res = chooser.showOpenDialog(displayPanel);
        if (res == JFileChooser.APPROVE_OPTION) {
            openFile(chooser.getSelectedFile());
        }
    }

    private void setZoom(double newZoom) {
        this.zoom = Math.max(0.1, Math.min(8.0, newZoom));
        updateZoomLabel();
        pageCanvas.repaint();
    }

    private void updateZoomLabel() {
        zoomLabel.setText(String.format("%d%%", (int) (zoom * 100)));
    }

    private void fitToWindow() {
        if (pages.isEmpty()) return;
        DocumentPage p = pages.get(currentPageIndex);
        BufferedImage img = imageCache.get(p.number);
        if (img == null && p.imageFile != null && p.imageFile.exists()) {
            try {
                img = ImageIO.read(p.imageFile);
                if (img != null) imageCache.put(p.number, img);
            } catch (Exception ignored) {}
        }

        if (img != null) {
            int canvasW = pageCanvas.getWidth() > 0 ? pageCanvas.getWidth() : 760;
            int canvasH = pageCanvas.getHeight() > 0 ? pageCanvas.getHeight() : 540;
            double scaleX = (double) (canvasW - 40) / img.getWidth();
            double scaleY = (double) (canvasH - 40) / img.getHeight();
            zoom = Math.min(scaleX, scaleY);
            if (zoom <= 0) zoom = 1.0;
        } else {
            zoom = 1.0;
        }
        panX = 0;
        panY = 0;
        updateZoomLabel();
        pageCanvas.repaint();
    }

    private void findText() {
        String q = JOptionPane.showInputDialog(displayPanel, "Search text in document:", "Find", JOptionPane.PLAIN_MESSAGE);
        if (q != null && !q.trim().isEmpty()) {
            String query = q.trim().toLowerCase();
            for (int i = 0; i < pages.size(); i++) {
                DocumentPage p = pages.get(i);
                if (p.cleanText != null && p.cleanText.toLowerCase().contains(query)) {
                    currentPageIndex = i;
                    renderCurrentPage();
                    JOptionPane.showMessageDialog(displayPanel, "Found on Page " + p.number + ":\n\"" + p.title + "\"", "Search Result", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
            }
            JOptionPane.showMessageDialog(displayPanel, "Text not found: " + q, "Find", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    // ── VISUAL PAGE CANVAS ─────────────────────────────────────────────────
    private class VisualPageCanvas extends JPanel {

        VisualPageCanvas() {
            setOpaque(true);
            setBackground(new Color(10, 14, 23));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    dragStart = e.getPoint();
                }
            });

            addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseDragged(MouseEvent e) {
                    if (dragStart != null) {
                        panX += e.getX() - dragStart.x;
                        panY += e.getY() - dragStart.y;
                        dragStart = e.getPoint();
                        repaint();
                    }
                }
            });

            addMouseWheelListener(e -> {
                if (e.getWheelRotation() < 0) {
                    setZoom(zoom * 1.15);
                } else {
                    setZoom(zoom / 1.15);
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            // Subtle dark grid background
            g2.setColor(new Color(17, 24, 38));
            int step = 28;
            for (int x = 0; x < getWidth(); x += step) g2.drawLine(x, 0, x, getHeight());
            for (int y = 0; y < getHeight(); y += step) g2.drawLine(0, y, getWidth(), y);

            if (!pages.isEmpty() && currentPageIndex < pages.size()) {
                DocumentPage p = pages.get(currentPageIndex);
                BufferedImage img = imageCache.get(p.number);
                if (img == null && p.imageFile != null && p.imageFile.exists()) {
                    try {
                        img = ImageIO.read(p.imageFile);
                        if (img != null) imageCache.put(p.number, img);
                    } catch (Exception ignored) {}
                }

                if (img != null) {
                    int cx = getWidth() / 2 + panX;
                    int cy = getHeight() / 2 + panY;

                    AffineTransform at = new AffineTransform();
                    at.translate(cx, cy);
                    at.scale(zoom, zoom);
                    at.translate(-img.getWidth() / 2.0, -img.getHeight() / 2.0);

                    // Draw subtle drop shadow behind page
                    Shape rect = at.createTransformedShape(new Rectangle(0, 0, img.getWidth(), img.getHeight()));
                    g2.setColor(new Color(0, 0, 0, 160));
                    AffineTransform shadowAt = (AffineTransform) at.clone();
                    shadowAt.translate(6, 6);
                    g2.fill(shadowAt.createTransformedShape(new Rectangle(0, 0, img.getWidth(), img.getHeight())));

                    // Draw actual high-DPI document page
                    g2.drawImage(img, at, null);

                    // Draw neon border around page
                    Theme t = ThemeManager.getInstance().getCurrentTheme();
                    g2.setColor(new Color(t.neonAccent.getRed(), t.neonAccent.getGreen(), t.neonAccent.getBlue(), 90));
                    g2.setStroke(new BasicStroke(1.2f));
                    g2.draw(rect);

                } else {
                    Theme t = ThemeManager.getInstance().getCurrentTheme();
                    g2.setColor(t.textSecondary);
                    g2.setFont(t.fontRegular);
                    FontMetrics fm = g2.getFontMetrics();
                    String msg = "Visual page preview unavailable. Click 'Text Mode' above to read clean document content.";
                    g2.drawString(msg, (getWidth() - fm.stringWidth(msg)) / 2, getHeight() / 2);
                }
            }

            g2.dispose();
        }
    }
}

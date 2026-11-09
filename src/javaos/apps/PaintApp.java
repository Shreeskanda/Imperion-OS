package javaos.apps;

import javaos.ui.Theme;
import javaos.ui.ThemeManager;
import javaos.ui.components.CyberpunkIcons;
import javaos.ui.components.NeonButton;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.LinkedList;
import java.util.Queue;

/**
 * CyberPaint Creative Graphics Studio for IMPERION OS.
 * Microsoft Paint equivalent featuring:
 *  - Tools: Pencil, Neon Glow Brush, Eraser, Line, Rectangle, Filled Box, Circle, Filled Circle,
 *    Fill Bucket (Flood Fill), Color Eyedropper, Text Tool
 *  - Stroke Size Slider (1px to 40px) & Color Palette
 *  - Cyberpunk Canvas Filters: Invert Colors, Matrix Green, CRT Scanlines, Grayscale
 *  - Image Open, Clear, Save as PNG / JPEG
 */
public class PaintApp extends OSApplication {

    private enum Tool {
        PENCIL, NEON_BRUSH, ERASER, LINE, RECT, RECT_FILLED, CIRCLE, CIRCLE_FILLED, BUCKET, DROPPER, TEXT
    }

    private Tool currentTool = Tool.NEON_BRUSH;
    private Color currentColor = new Color(34, 211, 238); // Cyan
    private int strokeSize = 6;

    private BufferedImage canvasImage;
    private Graphics2D canvasG2;
    private CanvasPanel canvasPanel;

    private int startX, startY;
    private int curX, curY;
    private boolean isDrawing = false;
    private JLabel statusLabel;

    public PaintApp() {
        super(new AppDescriptor(
                "paint",
                "Paint Studio",
                "Digital graphics canvas, neon brushes & image filters",
                AppDescriptor.Category.MEDIA,
                CyberpunkIcons.createIcon("PAINT", 20),
                false
        ));
    }

    @Override
    public Dimension getPreferredWindowSize() {
        return new Dimension(920, 640);
    }

    @Override
    public JComponent createContent() {
        JPanel main = new JPanel(new BorderLayout(0, 0));
        main.setOpaque(true);
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        main.setBackground(theme.background);

        // Initialize 1200x800 canvas backing buffer
        initCanvasBuffer(1200, 800);

        // ── TOP TOOLBAR ──────────────────────────────────────────────────────
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(true);
        top.setBackground(theme.panel);
        top.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, theme.secondaryAccent));

        JPanel toolRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        toolRow.setOpaque(false);

        JButton btnNew = makeToolBtn("New", "FILE", "Clear / New Canvas");
        btnNew.addActionListener(e -> clearCanvas());
        toolRow.add(btnNew);

        JButton btnOpen = makeToolBtn("Open", "FOLDER", "Open Image File");
        btnOpen.addActionListener(e -> openImageFile());
        toolRow.add(btnOpen);

        JButton btnSave = makeToolBtn("Save", "CHECK", "Save Image (.png)");
        btnSave.addActionListener(e -> saveImageFile());
        toolRow.add(btnSave);

        toolRow.add(new JSeparator(SwingConstants.VERTICAL));

        // Tools
        ButtonGroup toolGrp = new ButtonGroup();
        addToolToggle(toolRow, toolGrp, "Neon", Tool.NEON_BRUSH, true, "Neon Glow Brush");
        addToolToggle(toolRow, toolGrp, "Pencil", Tool.PENCIL, false, "Fine Pencil");
        addToolToggle(toolRow, toolGrp, "Eraser", Tool.ERASER, false, "Eraser");
        addToolToggle(toolRow, toolGrp, "Line", Tool.LINE, false, "Straight Line");
        addToolToggle(toolRow, toolGrp, "Box", Tool.RECT, false, "Rectangle Outline");
        addToolToggle(toolRow, toolGrp, "FillBox", Tool.RECT_FILLED, false, "Filled Box");
        addToolToggle(toolRow, toolGrp, "Circle", Tool.CIRCLE, false, "Circle Outline");
        addToolToggle(toolRow, toolGrp, "FillCircle", Tool.CIRCLE_FILLED, false, "Filled Circle");
        addToolToggle(toolRow, toolGrp, "Bucket", Tool.BUCKET, false, "Fill Bucket (Flood Fill)");
        addToolToggle(toolRow, toolGrp, "Text", Tool.TEXT, false, "Text Label");

        toolRow.add(new JSeparator(SwingConstants.VERTICAL));

        // Brush Size Slider
        toolRow.add(new JLabel("Size:"));
        JSlider sizeSlider = new JSlider(1, 40, strokeSize);
        sizeSlider.setPreferredSize(new Dimension(80, 24));
        sizeSlider.setOpaque(false);
        sizeSlider.addChangeListener(e -> strokeSize = sizeSlider.getValue());
        toolRow.add(sizeSlider);

        // Cyber Filters Dropdown
        JComboBox<String> filterBox = new JComboBox<>(new String[]{"⚡ Filters", "Invert Colors", "Matrix Green", "CRT Scanlines", "Grayscale"});
        filterBox.setFont(theme.fontSmall);
        filterBox.addActionListener(e -> {
            String f = (String) filterBox.getSelectedItem();
            if (f != null && !f.startsWith("⚡")) {
                applyFilter(f);
                filterBox.setSelectedIndex(0);
            }
        });
        toolRow.add(filterBox);

        top.add(toolRow, BorderLayout.NORTH);

        // Color Palette Row
        JPanel colorRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        colorRow.setOpaque(false);
        colorRow.setBorder(BorderFactory.createEmptyBorder(0, 8, 4, 8));

        Color[] quickColors = {
                new Color(34, 211, 238),   // Cyan
                new Color(244, 63, 94),    // Neon Rose
                new Color(52, 211, 153),   // Emerald Green
                new Color(251, 191, 36),   // Amber Yellow
                new Color(168, 85, 247),   // Purple
                new Color(255, 255, 255),  // White
                new Color(15, 23, 42),     // Dark Background
                new Color(239, 68, 68),    // Red
                new Color(59, 130, 246)    // Blue
        };

        for (Color c : quickColors) {
            JButton cBtn = new JButton();
            cBtn.setPreferredSize(new Dimension(20, 20));
            cBtn.setBackground(c);
            cBtn.setBorder(BorderFactory.createLineBorder(Color.GRAY));
            cBtn.setFocusPainted(false);
            cBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            cBtn.addActionListener(e -> currentColor = c);
            colorRow.add(cBtn);
        }

        JButton btnMoreColor = new JButton("Color Picker...");
        btnMoreColor.setFont(theme.fontSmall);
        btnMoreColor.setBackground(theme.elevatedPanel);
        btnMoreColor.setForeground(theme.textPrimary);
        btnMoreColor.addActionListener(e -> {
            Color chosen = JColorChooser.showDialog(main, "Choose Paint Color", currentColor);
            if (chosen != null) currentColor = chosen;
        });
        colorRow.add(btnMoreColor);

        top.add(colorRow, BorderLayout.SOUTH);
        main.add(top, BorderLayout.NORTH);

        // ── CENTER DRAWING CANVAS ────────────────────────────────────────────
        canvasPanel = new CanvasPanel();
        JScrollPane scroll = new JScrollPane(canvasPanel);
        scroll.getVerticalScrollBar().setUI(new javaos.ui.components.NeonScrollBarUI());
        scroll.getHorizontalScrollBar().setUI(new javaos.ui.components.NeonScrollBarUI());
        scroll.setBorder(BorderFactory.createEmptyBorder());
        main.add(scroll, BorderLayout.CENTER);

        // ── BOTTOM STATUS BAR ────────────────────────────────────────────────
        JPanel statusBar = new JPanel(new BorderLayout(8, 0));
        statusBar.setOpaque(true);
        statusBar.setBackground(theme.panel);
        statusBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, theme.secondaryAccent),
                BorderFactory.createEmptyBorder(4, 12, 4, 12)
        ));

        statusLabel = new JLabel("Tool: Neon Glow Brush | Size: 6px | Canvas: 1200x800");
        statusLabel.setFont(theme.fontSmall);
        statusLabel.setForeground(theme.neonAccent);
        statusBar.add(statusLabel, BorderLayout.WEST);

        JLabel hint = new JLabel("Click & Drag to Paint");
        hint.setFont(theme.fontSmall);
        hint.setForeground(theme.textSecondary);
        statusBar.add(hint, BorderLayout.EAST);

        main.add(statusBar, BorderLayout.SOUTH);

        return main;
    }

    private void initCanvasBuffer(int w, int h) {
        canvasImage = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        canvasG2 = canvasImage.createGraphics();
        canvasG2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        canvasG2.setColor(new Color(15, 23, 42)); // Deep cyberpunk canvas
        canvasG2.fillRect(0, 0, w, h);
    }

    private void clearCanvas() {
        int opt = JOptionPane.showConfirmDialog(canvasPanel, "Clear entire canvas?", "New Canvas", JOptionPane.YES_NO_OPTION);
        if (opt == JOptionPane.YES_OPTION) {
            canvasG2.setColor(new Color(15, 23, 42));
            canvasG2.fillRect(0, 0, canvasImage.getWidth(), canvasImage.getHeight());
            canvasPanel.repaint();
        }
    }

    private void addToolToggle(JPanel p, ButtonGroup grp, String name, Tool tool, boolean def, String tip) {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        JToggleButton btn = new JToggleButton(name, def);
        btn.setFont(theme.fontSmall);
        btn.setForeground(theme.textPrimary);
        btn.setBackground(theme.panel);
        btn.setFocusPainted(false);
        btn.setToolTipText(tip);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addActionListener(e -> {
            currentTool = tool;
            statusLabel.setText("Tool: " + tip + " | Size: " + strokeSize + "px");
        });
        grp.add(btn);
        p.add(btn);
    }

    // ── INNER CANVAS PANEL ──────────────────────────────────────────────────
    private class CanvasPanel extends JPanel {
        CanvasPanel() {
            setPreferredSize(new Dimension(1200, 800));
            setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));

            MouseAdapter ma = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    startX = e.getX();
                    startY = e.getY();
                    curX = startX;
                    curY = startY;
                    isDrawing = true;

                    if (currentTool == Tool.BUCKET) {
                        floodFill(startX, startY, currentColor.getRGB());
                        repaint();
                    } else if (currentTool == Tool.DROPPER) {
                        if (startX >= 0 && startX < canvasImage.getWidth() && startY >= 0 && startY < canvasImage.getHeight()) {
                            currentColor = new Color(canvasImage.getRGB(startX, startY));
                        }
                    } else if (currentTool == Tool.TEXT) {
                        String txt = JOptionPane.showInputDialog(canvasPanel, "Enter text to stamp on canvas:");
                        if (txt != null && !txt.isEmpty()) {
                            canvasG2.setColor(currentColor);
                            canvasG2.setFont(new Font("Segoe UI", Font.BOLD, strokeSize * 3));
                            canvasG2.drawString(txt, startX, startY);
                            repaint();
                        }
                    } else if (currentTool == Tool.PENCIL || currentTool == Tool.NEON_BRUSH || currentTool == Tool.ERASER) {
                        paintStroke(startX, startY, startX, startY);
                        repaint();
                    }
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (!isDrawing) return;
                    int nx = e.getX();
                    int ny = e.getY();

                    if (currentTool == Tool.PENCIL || currentTool == Tool.NEON_BRUSH || currentTool == Tool.ERASER) {
                        paintStroke(curX, curY, nx, ny);
                        curX = nx;
                        curY = ny;
                        repaint();
                    } else {
                        curX = nx;
                        curY = ny;
                        repaint();
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (!isDrawing) return;
                    isDrawing = false;
                    curX = e.getX();
                    curY = e.getY();

                    // Finalize shape onto canvas buffer
                    int x1 = Math.min(startX, curX);
                    int y1 = Math.min(startY, curY);
                    int bw = Math.abs(curX - startX);
                    int bh = Math.abs(curY - startY);

                    canvasG2.setColor(currentColor);
                    canvasG2.setStroke(new BasicStroke(strokeSize, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

                    switch (currentTool) {
                        case LINE -> canvasG2.drawLine(startX, startY, curX, curY);
                        case RECT -> canvasG2.drawRect(x1, y1, bw, bh);
                        case RECT_FILLED -> canvasG2.fillRect(x1, y1, bw, bh);
                        case CIRCLE -> canvasG2.drawOval(x1, y1, bw, bh);
                        case CIRCLE_FILLED -> canvasG2.fillOval(x1, y1, bw, bh);
                    }
                    repaint();
                }
            };

            addMouseListener(ma);
            addMouseMotionListener(ma);
        }

        private void paintStroke(int x1, int y1, int x2, int y2) {
            if (currentTool == Tool.ERASER) {
                canvasG2.setColor(new Color(15, 23, 42));
                canvasG2.setStroke(new BasicStroke(strokeSize * 2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                canvasG2.drawLine(x1, y1, x2, y2);
            } else if (currentTool == Tool.NEON_BRUSH) {
                // Outer glow stroke
                canvasG2.setColor(new Color(currentColor.getRed(), currentColor.getGreen(), currentColor.getBlue(), 50));
                canvasG2.setStroke(new BasicStroke(strokeSize * 2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                canvasG2.drawLine(x1, y1, x2, y2);

                // Core stroke
                canvasG2.setColor(currentColor);
                canvasG2.setStroke(new BasicStroke(strokeSize, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                canvasG2.drawLine(x1, y1, x2, y2);

                // White core highlight
                canvasG2.setColor(new Color(255, 255, 255, 180));
                canvasG2.setStroke(new BasicStroke(Math.max(1, strokeSize * 0.35f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                canvasG2.drawLine(x1, y1, x2, y2);
            } else {
                canvasG2.setColor(currentColor);
                canvasG2.setStroke(new BasicStroke(strokeSize, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                canvasG2.drawLine(x1, y1, x2, y2);
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            g.drawImage(canvasImage, 0, 0, null);

            // Shape preview guide while dragging
            if (isDrawing && (currentTool == Tool.LINE || currentTool == Tool.RECT || currentTool == Tool.RECT_FILLED
                    || currentTool == Tool.CIRCLE || currentTool == Tool.CIRCLE_FILLED)) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(new Color(currentColor.getRed(), currentColor.getGreen(), currentColor.getBlue(), 160));
                g2.setStroke(new BasicStroke(strokeSize, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

                int x1 = Math.min(startX, curX);
                int y1 = Math.min(startY, curY);
                int bw = Math.abs(curX - startX);
                int bh = Math.abs(curY - startY);

                switch (currentTool) {
                    case LINE -> g2.drawLine(startX, startY, curX, curY);
                    case RECT, RECT_FILLED -> g2.drawRect(x1, y1, bw, bh);
                    case CIRCLE, CIRCLE_FILLED -> g2.drawOval(x1, y1, bw, bh);
                }
                g2.dispose();
            }
        }
    }

    // ── FLOOD FILL ALGORITHM ────────────────────────────────────────────────
    private void floodFill(int x, int y, int newColor) {
        int w = canvasImage.getWidth();
        int h = canvasImage.getHeight();
        if (x < 0 || x >= w || y < 0 || y >= h) return;

        int targetColor = canvasImage.getRGB(x, y);
        if (targetColor == newColor) return;

        Queue<Point> q = new LinkedList<>();
        q.add(new Point(x, y));

        while (!q.isEmpty()) {
            Point p = q.poll();
            int px = p.x;
            int py = p.y;

            if (px < 0 || px >= w || py < 0 || py >= h) continue;
            if (canvasImage.getRGB(px, py) != targetColor) continue;

            canvasImage.setRGB(px, py, newColor);
            q.add(new Point(px + 1, py));
            q.add(new Point(px - 1, py));
            q.add(new Point(px, py + 1));
            q.add(new Point(px, py - 1));
        }
    }

    // ── CYBER IMAGE FILTERS ─────────────────────────────────────────────────
    private void applyFilter(String filterName) {
        int w = canvasImage.getWidth();
        int h = canvasImage.getHeight();

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = canvasImage.getRGB(x, y);
                int a = (rgb >> 24) & 0xFF;
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                switch (filterName) {
                    case "Invert Colors" -> {
                        r = 255 - r; g = 255 - g; b = 255 - b;
                    }
                    case "Matrix Green" -> {
                        int avg = (r + g + b) / 3;
                        r = 0;
                        g = Math.min(255, avg + 40);
                        b = (int) (avg * 0.2);
                    }
                    case "CRT Scanlines" -> {
                        if (y % 4 == 0) {
                            r = (int) (r * 0.4);
                            g = (int) (g * 0.4);
                            b = (int) (b * 0.4);
                        }
                    }
                    case "Grayscale" -> {
                        int lum = (int) (0.299 * r + 0.587 * g + 0.114 * b);
                        r = lum; g = lum; b = lum;
                    }
                }
                int newRgb = (a << 24) | (r << 16) | (g << 8) | b;
                canvasImage.setRGB(x, y, newRgb);
            }
        }
        canvasPanel.repaint();
    }

    private void openImageFile() {
        JFileChooser chooser = new JFileChooser();
        int res = chooser.showOpenDialog(canvasPanel);
        if (res == JFileChooser.APPROVE_OPTION) {
            try {
                BufferedImage loaded = ImageIO.read(chooser.getSelectedFile());
                if (loaded != null) {
                    initCanvasBuffer(Math.max(1200, loaded.getWidth()), Math.max(800, loaded.getHeight()));
                    canvasG2.drawImage(loaded, 0, 0, null);
                    canvasPanel.repaint();
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(canvasPanel, "Failed to load image: " + ex.getMessage());
            }
        }
    }

    private void saveImageFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("artwork.png"));
        int res = chooser.showSaveDialog(canvasPanel);
        if (res == JFileChooser.APPROVE_OPTION) {
            try {
                File out = chooser.getSelectedFile();
                if (!out.getName().contains(".")) out = new File(out.getAbsolutePath() + ".png");
                ImageIO.write(canvasImage, "png", out);
                JOptionPane.showMessageDialog(canvasPanel, "Artwork saved to: " + out.getName());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(canvasPanel, "Failed to save: " + ex.getMessage());
            }
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
                BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));
        return btn;
    }
}

package javaos.apps;

import javaos.ui.Theme;
import javaos.ui.ThemeManager;
import javaos.ui.components.CyberpunkIcons;
import javaos.ui.components.NeonButton;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Cyber Image Viewer for IMPERION OS.
 * Natively views images (PNG, JPEG, GIF, BMP, WBMP) inside the OS workspace
 * with hardware-accelerated pan, zoom, rotation, and cyberpunk diagnostic telemetry.
 */
public class ImageViewerApp extends OSApplication {

    private BufferedImage currentImage;
    private File currentFile;
    private double zoom = 1.0;
    private int rotation = 0; // 0, 90, 180, 270
    private int panX = 0;
    private int panY = 0;
    private Point dragStart;
    private boolean neonFilter = false;

    private ImageCanvas canvas;
    private JLabel infoLabel;
    private JLabel zoomLabel;
    private JLabel statusLabel;

    public ImageViewerApp() {
        this(null);
    }

    public ImageViewerApp(File initialFile) {
        super(new AppDescriptor(
                "imageviewer",
                "Image Viewer",
                "Internal image visualizer, pan/zoom & diagnostics",
                AppDescriptor.Category.UTILITIES,
                CyberpunkIcons.createIcon("IMAGE", 20),
                false
        ));
        this.currentFile = initialFile;
    }

    @Override
    public Dimension getPreferredWindowSize() {
        return new Dimension(860, 620);
    }

    @Override
    public JComponent createContent() {
        JPanel main = new JPanel(new BorderLayout());
        main.setOpaque(false);
        Theme theme = ThemeManager.getInstance().getCurrentTheme();

        // ── TOP TOOLBAR ────────────────────────────────────────────────────────
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        toolbar.setOpaque(true);
        toolbar.setBackground(theme.elevatedPanel);
        toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, theme.secondaryAccent));

        JButton btnOpen = makeButton("Open...", "Open another image file from disk");
        btnOpen.addActionListener(e -> chooseFile());

        JButton btnZoomIn = makeButton("+", "Zoom In (Mouse Wheel Up)");
        btnZoomIn.addActionListener(e -> setZoom(zoom * 1.25));

        JButton btnZoomOut = makeButton("-", "Zoom Out (Mouse Wheel Down)");
        btnZoomOut.addActionListener(e -> setZoom(zoom / 1.25));

        JButton btnFit = makeButton("Fit Window", "Scale image to fit window");
        btnFit.addActionListener(e -> fitToWindow());

        JButton btnOriginal = makeButton("100%", "Display at original pixel scale");
        btnOriginal.addActionListener(e -> resetZoom());

        JButton btnRotLeft = makeButton("↺ 90°", "Rotate counter-clockwise");
        btnRotLeft.addActionListener(e -> rotate(-90));

        JButton btnRotRight = makeButton("↻ 90°", "Rotate clockwise");
        btnRotRight.addActionListener(e -> rotate(90));

        JButton btnFilter = makeButton("Neon Glow", "Toggle cyberpunk neon matrix filter");
        btnFilter.addActionListener(e -> {
            neonFilter = !neonFilter;
            canvas.repaint();
        });

        toolbar.add(btnOpen);
        toolbar.add(Box.createHorizontalStrut(6));
        toolbar.add(btnZoomIn);
        toolbar.add(btnZoomOut);
        toolbar.add(btnFit);
        toolbar.add(btnOriginal);
        toolbar.add(Box.createHorizontalStrut(6));
        toolbar.add(btnRotLeft);
        toolbar.add(btnRotRight);
        toolbar.add(Box.createHorizontalStrut(6));
        toolbar.add(btnFilter);

        main.add(toolbar, BorderLayout.NORTH);

        // ── IMAGE CANVAS ───────────────────────────────────────────────────────
        canvas = new ImageCanvas();
        main.add(canvas, BorderLayout.CENTER);

        // ── BOTTOM STATUS BAR ──────────────────────────────────────────────────
        JPanel statusBar = new JPanel(new BorderLayout(8, 0));
        statusBar.setOpaque(true);
        statusBar.setBackground(theme.panel);
        statusBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, theme.secondaryAccent),
                BorderFactory.createEmptyBorder(4, 10, 4, 10)
        ));

        infoLabel = new JLabel("No image loaded");
        infoLabel.setFont(theme.fontSmall);
        infoLabel.setForeground(theme.textPrimary);

        zoomLabel = new JLabel("Zoom: 100%");
        zoomLabel.setFont(theme.fontSmall);
        zoomLabel.setForeground(theme.neonAccent);

        statusLabel = new JLabel("IMPERION Image Engine Ready");
        statusLabel.setFont(theme.fontSmall);
        statusLabel.setForeground(theme.textSecondary);

        JPanel rightStatus = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        rightStatus.setOpaque(false);
        rightStatus.add(zoomLabel);
        rightStatus.add(statusLabel);

        statusBar.add(infoLabel, BorderLayout.WEST);
        statusBar.add(rightStatus, BorderLayout.EAST);

        main.add(statusBar, BorderLayout.SOUTH);

        // Load initial file if provided
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

        statusLabel.setText("Loading: " + file.getName() + "...");

        new SwingWorker<BufferedImage, Void>() {
            @Override
            protected BufferedImage doInBackground() throws Exception {
                return ImageIO.read(file);
            }

            @Override
            protected void done() {
                try {
                    currentImage = get();
                    if (currentImage != null) {
                        rotation = 0;
                        panX = 0;
                        panY = 0;
                        fitToWindow();
                        updateTelemetry();
                        statusLabel.setText("Loaded: " + file.getName());
                    } else {
                        JOptionPane.showMessageDialog(canvas,
                                "Could not decode image format for:\n" + file.getName(),
                                "Unsupported Image", JOptionPane.WARNING_MESSAGE);
                        statusLabel.setText("Decode failed");
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(canvas,
                            "Failed to load image:\n" + ex.getMessage(),
                            "Load Error", JOptionPane.ERROR_MESSAGE);
                    statusLabel.setText("Error loading image");
                }
                canvas.repaint();
            }
        }.execute();
    }

    private void chooseFile() {
        JFileChooser chooser = new JFileChooser(currentFile != null ? currentFile.getParentFile() : new File("."));
        chooser.setDialogTitle("Select Image File");
        int res = chooser.showOpenDialog(canvas);
        if (res == JFileChooser.APPROVE_OPTION) {
            openFile(chooser.getSelectedFile());
        }
    }

    private void updateTelemetry() {
        if (currentImage == null || currentFile == null) {
            infoLabel.setText("No image loaded");
            zoomLabel.setText("Zoom: 100%");
            return;
        }

        long bytes = currentFile.length();
        String sizeStr = bytes > 1024 * 1024
                ? String.format("%.2f MB", bytes / (1024.0 * 1024.0))
                : String.format("%.1f KB", bytes / 1024.0);

        String info = String.format("File: %s | Resolution: %d × %d px | Size: %s",
                currentFile.getName(), currentImage.getWidth(), currentImage.getHeight(), sizeStr);
        infoLabel.setText(info);
        zoomLabel.setText(String.format("Zoom: %d%%", (int) (zoom * 100)));
    }

    private void setZoom(double newZoom) {
        this.zoom = Math.max(0.05, Math.min(20.0, newZoom));
        zoomLabel.setText(String.format("Zoom: %d%%", (int) (zoom * 100)));
        canvas.repaint();
    }

    private void resetZoom() {
        zoom = 1.0;
        panX = 0;
        panY = 0;
        zoomLabel.setText("Zoom: 100%");
        canvas.repaint();
    }

    private void fitToWindow() {
        if (currentImage == null) return;
        int w = canvas.getWidth();
        int h = canvas.getHeight();
        if (w <= 0) w = 800;
        if (h <= 0) h = 500;

        int imgW = (rotation % 180 == 0) ? currentImage.getWidth() : currentImage.getHeight();
        int imgH = (rotation % 180 == 0) ? currentImage.getHeight() : currentImage.getWidth();

        double scaleX = (double) (w - 32) / imgW;
        double scaleY = (double) (h - 32) / imgH;
        zoom = Math.min(scaleX, scaleY);
        if (zoom <= 0) zoom = 1.0;
        panX = 0;
        panY = 0;
        zoomLabel.setText(String.format("Zoom: %d%%", (int) (zoom * 100)));
        canvas.repaint();
    }

    private void rotate(int deg) {
        rotation = (rotation + deg + 360) % 360;
        canvas.repaint();
    }

    private class ImageCanvas extends JPanel {

        ImageCanvas() {
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

            // Paint subtle background grid
            g2.setColor(new Color(18, 25, 38));
            int step = 24;
            for (int x = 0; x < getWidth(); x += step) g2.drawLine(x, 0, x, getHeight());
            for (int y = 0; y < getHeight(); y += step) g2.drawLine(0, y, getWidth(), y);

            if (currentImage != null) {
                int cx = getWidth() / 2 + panX;
                int cy = getHeight() / 2 + panY;

                AffineTransform at = new AffineTransform();
                at.translate(cx, cy);
                at.scale(zoom, zoom);
                at.rotate(Math.toRadians(rotation));
                at.translate(-currentImage.getWidth() / 2.0, -currentImage.getHeight() / 2.0);

                // Draw image with transform
                g2.drawImage(currentImage, at, null);

                // Draw neon border around image
                Shape rect = at.createTransformedShape(new Rectangle(0, 0, currentImage.getWidth(), currentImage.getHeight()));
                Theme t = ThemeManager.getInstance().getCurrentTheme();
                g2.setColor(new Color(t.neonAccent.getRed(), t.neonAccent.getGreen(), t.neonAccent.getBlue(), 80));
                g2.setStroke(new BasicStroke(1.5f));
                g2.draw(rect);

                if (neonFilter) {
                    // Cyberpunk filter overlay
                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.15f));
                    g2.setColor(t.neonAccent);
                    g2.fill(rect);
                }
            } else {
                Theme t = ThemeManager.getInstance().getCurrentTheme();
                g2.setColor(t.textSecondary);
                g2.setFont(t.fontRegular);
                FontMetrics fm = g2.getFontMetrics();
                String msg = "No image loaded. Use 'Open...' or double-click an image in Real File Explorer.";
                g2.drawString(msg, (getWidth() - fm.stringWidth(msg)) / 2, getHeight() / 2);
            }

            g2.dispose();
        }
    }
}

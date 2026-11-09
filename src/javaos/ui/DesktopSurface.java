package javaos.ui;

import javaos.apps.AppDescriptor;
import javaos.core.JavaOS;
import javaos.core.WindowManager;
import javaos.models.NotificationMessage;
import javaos.ui.components.CyberpunkIcons;
import javaos.ui.components.DesktopWidgets;
import javaos.ui.components.LogoManager;
import javaos.ui.components.ToastNotification;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Main desktop workspace surface.
 * Features an interactive 3D animated cyberpunk hacker engine with parallax depth,
 * streaming matrix glyphs, floating holographic wireframe core, reactive shockwaves,
 * and draggable desktop shortcut icons.
 */
public class DesktopSurface extends JLayeredPane {

    private final JavaOS os;
    private final JPanel toastContainer;
    private final JPanel shortcutPanel;       // null-layout, icons placed absolutely
    private String wallpaperStyle = "grid";

    // Window Snap Assist Ghost & Desktop Widgets
    private Rectangle snapGhostBounds = null;
    private DesktopWidgets desktopWidgets;
    private boolean showWidgets = true;
    private boolean isLogoHovered = false;

    // Tracks the position of each icon by app ID so positions survive refresh
    private final Map<String, Point> iconPositions = new HashMap<>();
    private static final int ICON_W = 88;
    private static final int ICON_H = 96;
    private static final int ICON_COL_X = 14;
    private static final int ICON_ROW_START_Y = 14;
    private static final int ICON_ROW_GAP = 6;

    // ── 3D Cyberpunk Hacker Engine Fields ──────────────────────────────────
    private Timer animTimer;
    private float animTime = 0f;
    private float gridOffset = 0f;
    private int mouseX = 640;
    private int mouseY = 360;
    private float currentVpX = 640f;
    private float currentVpY = 400f;


    private final Random rnd = new Random();
    private final List<MatrixStream> matrixStreams = new ArrayList<>();
    private final List<CyberParticle> particles = new ArrayList<>();
    private final List<Shockwave> shockwaves = new ArrayList<>();

    // ── Imperion Logo Core Animations ──────────────────────────────────────
    // 1. Singularity Collapse & Supernova Burst on Click
    private boolean isCollapsing = false;
    private float collapseProgress = 0f;
    private float collapseAngle = 0f;
    private boolean collapseTriggered = false;
    private final List<BurstSpark> burstSparks = new ArrayList<>();

    // 2. Orbital Gyro Rings on Hover
    private float gyroHoverAlpha = 0f;
    private float gyroAngle1 = 0f;
    private float gyroAngle2 = 0f;

    // 3. Audio-Reactive Resonant Pulse
    private float audioPulseEnergy = 0f;

    // 4. Neon Laser Cursor Trail
    private final List<CursorTrailParticle> cursorTrail = new ArrayList<>();

    // ── 3D Interactive Imperion Hologram Engine ──
    private float currentPitch = 0f;
    private float currentYaw = 0f;
    private float currentRoll = 0f;
    private float targetPitch = 0f;
    private float targetYaw = 0f;
    private float manualPitch = 0f;
    private float manualYaw = 0f;
    private boolean isDragging3D = false;
    private int dragStartX = 0;
    private int dragStartY = 0;
    private float cageRotationAngle = 0f;
    private final List<QuantumOrbitalParticle> quantumParticles = new ArrayList<>();

    // ── 3D Matrix Geometry Modes: 0=Octahedron, 1=Tesseract 4D, 2=Icosahedron, 3=Hex Shield ──
    public static final int GEO_OCTAHEDRON = 0;
    public static final int GEO_TESSERACT = 1;
    public static final int GEO_ICOSAHEDRON = 2;
    public static final int GEO_HEX_SHIELD = 3;
    private int geometryMode = GEO_OCTAHEDRON;
    private float tesseractRot4D = 0f;

    public DesktopSurface(JavaOS os) {
        this.os = os;
        setLayout(null);
        setOpaque(true);

        // Key binding for 'M' or 'G' to cycle 3D Hologram Geometry
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke('m'), "cycleGeometry");
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke('M'), "cycleGeometry");
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke('g'), "cycleGeometry");
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke('G'), "cycleGeometry");
        getActionMap().put("cycleGeometry", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                cycle3DGeometry();
            }
        });

        // Toast notification container anchored at bottom-right
        toastContainer = new JPanel();
        toastContainer.setLayout(new BoxLayout(toastContainer, BoxLayout.Y_AXIS));
        toastContainer.setOpaque(false);
        add(toastContainer, JLayeredPane.POPUP_LAYER);

        // Shortcut icons panel — null layout covering full desktop so icons can be placed and dragged freely anywhere
        shortcutPanel = new JPanel(null);
        shortcutPanel.setOpaque(false);
        shortcutPanel.setBounds(0, 0, 1920, 1080);
        add(shortcutPanel, JLayeredPane.DEFAULT_LAYER);

        // Floating interactive desktop widgets (Clock, Telemetry Rings, Memo)
        desktopWidgets = new DesktopWidgets(os);
        desktopWidgets.setBounds(1000, 20, 310, 480);
        add(desktopWidgets, JLayeredPane.DEFAULT_LAYER);

        // Click on desktop or empty shortcut area deselects windows + triggers 3D Logo Start Launcher
        MouseAdapter deselectAdapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                isDragging3D = true;
                dragStartX = e.getX();
                dragStartY = e.getY();

                if (SwingUtilities.isRightMouseButton(e)) {
                    showDesktopMenu(e.getPoint());
                    return;
                }
                // Check if Imperion Logo was clicked (primary start launcher)
                if (isPointOverLogo(e.getX(), e.getY())) {
                    triggerLogoClick();
                    return;
                }
                if (e.getSource() == DesktopSurface.this || e.getSource() == shortcutPanel) {
                    if (os.getWindowManager().getActiveWindow() != null) {
                        os.getWindowManager().setActiveWindow(null);
                    }
                    addShockwave(e.getX(), e.getY());
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                isDragging3D = false;
            }
        };
        addMouseListener(deselectAdapter);
        shortcutPanel.addMouseListener(deselectAdapter);

        // Mouse motion listener for real-time 3D camera parallax & 3D logo hover reactivity & neon cursor trail
        MouseMotionAdapter motionAdapter = new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                mouseX = e.getX();
                mouseY = e.getY();
                addCursorTrail(e.getX(), e.getY());
                boolean overLogo = isPointOverLogo(e.getX(), e.getY());
                if (overLogo != isLogoHovered) {
                    isLogoHovered = overLogo;
                    Cursor c = overLogo ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor();
                    setCursor(c);
                    shortcutPanel.setCursor(c);
                    repaint();
                }
            }
            @Override
            public void mouseDragged(MouseEvent e) {
                mouseX = e.getX();
                mouseY = e.getY();
                addCursorTrail(e.getX(), e.getY());
                if (isDragging3D) {
                    float dx = e.getX() - dragStartX;
                    float dy = e.getY() - dragStartY;
                    manualYaw += dx * 0.007f;
                    manualPitch -= dy * 0.007f;
                    dragStartX = e.getX();
                    dragStartY = e.getY();
                }
            }
        };
        addMouseMotionListener(motionAdapter);
        shortcutPanel.addMouseMotionListener(motionAdapter);

        initWallpaperEngine();
    }

    private void addCursorTrail(int x, int y) {
        if (cursorTrail.size() < 35) {
            cursorTrail.add(new CursorTrailParticle(x, y));
        }
    }

    public void initShortcuts() {
        shortcutPanel.removeAll();

        List<AppDescriptor> apps = os.getApplicationManager().getRegisteredApps();
        int surfaceH = getHeight() > 0 ? getHeight() : 700;
        int maxH = Math.max(380, surfaceH - 80); // leave room above taskbar

        int curX = ICON_COL_X;
        int curY = ICON_ROW_START_Y;

        for (AppDescriptor desc : apps) {
            Point pos = iconPositions.get(desc.getId());
            if (pos == null) {
                // Wrap to next column if overflowing vertical space
                if (curY + ICON_H > maxH) {
                    curX += ICON_W + 16;
                    curY = ICON_ROW_START_Y;
                }
                pos = new Point(curX, curY);
                iconPositions.put(desc.getId(), pos);
                curY += ICON_H + ICON_ROW_GAP;
            }

            JComponent icon = createDesktopIcon(desc, pos);
            shortcutPanel.add(icon);
        }

        shortcutPanel.revalidate();
        shortcutPanel.repaint();
    }

    public void setAnimationPerformance(String mode) {
        if ("saver".equalsIgnoreCase(mode)) {
            if (animTimer != null) animTimer.setDelay(60);
        } else if ("high".equalsIgnoreCase(mode)) {
            if (animTimer != null) animTimer.setDelay(20);
        } else {
            if (animTimer != null) animTimer.setDelay(33);
        }
    }

    private JComponent createDesktopIcon(AppDescriptor desc, Point initialPos) {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();

        // Inner item: icon + label stacked vertically, centered
        JPanel iconItem = new JPanel();
        iconItem.setLayout(new BoxLayout(iconItem, BoxLayout.Y_AXIS));
        iconItem.setOpaque(false);

        JLabel iconLabel = new JLabel(CyberpunkIcons.createIcon(desc.getId(), 40));
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
        iconLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        iconLabel.setMaximumSize(new Dimension(ICON_W - 8, 44));
        iconLabel.setMinimumSize(new Dimension(ICON_W - 8, 44));
        iconLabel.setPreferredSize(new Dimension(ICON_W - 8, 44));

        JLabel textLabel = new JLabel("<html><div style='text-align:center;width:" + (ICON_W - 8) + "px;'>"
                + desc.getName() + "</div></html>");
        textLabel.setFont(theme.fontSmall);
        textLabel.setForeground(theme.textPrimary);
        textLabel.setHorizontalAlignment(SwingConstants.CENTER);
        textLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        textLabel.setMaximumSize(new Dimension(ICON_W - 8, 34));

        iconItem.add(Box.createVerticalStrut(6));
        iconItem.add(iconLabel);
        iconItem.add(Box.createVerticalStrut(4));
        iconItem.add(textLabel);
        iconItem.add(Box.createVerticalStrut(4));

        final boolean[] hovered  = {false};
        final boolean[] selected = {false};
        final boolean[] dragging = {false};

        // Wrapper panel: draws neon hover/select glow + is the draggable unit
        JPanel wrapper = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                if (hovered[0] || selected[0] || dragging[0]) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    Theme t = ThemeManager.getInstance().getCurrentTheme();
                    int alpha = dragging[0] ? 70 : (hovered[0] ? 35 : 55);
                    g2.setColor(new Color(t.neonAccent.getRed(), t.neonAccent.getGreen(), t.neonAccent.getBlue(), alpha));
                    g2.fill(new RoundRectangle2D.Float(2, 2, getWidth() - 4, getHeight() - 4, 10, 10));
                    g2.setColor(t.neonAccent);
                    g2.setStroke(new BasicStroke(dragging[0] ? 2f : 1.2f));
                    g2.draw(new RoundRectangle2D.Float(2.5f, 2.5f, getWidth() - 5, getHeight() - 5, 10, 10));
                    g2.dispose();
                }
                super.paintComponent(g);
            }
        };
        wrapper.setOpaque(false);
        wrapper.setBounds(initialPos.x, initialPos.y, ICON_W, ICON_H);
        wrapper.add(iconItem, BorderLayout.CENTER);
        wrapper.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));

        // ── Drag logic ────────────────────────────────────────────────────────
        final Point[] dragOffsetInWrapper = {null};
        final Point[] startScreenPt = {null};

        MouseAdapter dragAdapter = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                hovered[0] = true;
                wrapper.repaint();
            }
            @Override
            public void mouseExited(MouseEvent e) {
                if (!dragging[0]) { hovered[0] = false; wrapper.repaint(); }
            }
            @Override
            public void mousePressed(MouseEvent e) {
                selected[0] = true;
                Point screenPt = e.getLocationOnScreen();
                startScreenPt[0] = screenPt;
                Point wrapperScreen = wrapper.getLocationOnScreen();
                dragOffsetInWrapper[0] = new Point(screenPt.x - wrapperScreen.x, screenPt.y - wrapperScreen.y);
                shortcutPanel.setComponentZOrder(wrapper, 0);
            }
            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragOffsetInWrapper[0] == null) return;
                dragging[0] = true;
                hovered[0]  = false;

                Point screenPt = e.getLocationOnScreen();
                Point panelScreen = shortcutPanel.getLocationOnScreen();

                int nx = (screenPt.x - panelScreen.x) - dragOffsetInWrapper[0].x;
                int ny = (screenPt.y - panelScreen.y) - dragOffsetInWrapper[0].y;

                // Clamp to keep entire icon inside desktop visible area
                int maxX = Math.max(0, shortcutPanel.getWidth()  - ICON_W);
                int maxY = Math.max(0, shortcutPanel.getHeight() - ICON_H);
                nx = Math.max(4, Math.min(nx, maxX - 4));
                ny = Math.max(4, Math.min(ny, maxY - 4));

                wrapper.setLocation(nx, ny);
                iconPositions.put(desc.getId(), new Point(nx, ny));
                shortcutPanel.repaint();
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                dragging[0] = false;
                hovered[0]  = false;
                dragOffsetInWrapper[0] = null;
                wrapper.repaint();
            }
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && !dragging[0]) {
                    os.getApplicationManager().launchApp(desc.getId());
                    selected[0] = false;
                } else if (e.getClickCount() == 1) {
                    selected[0] = !selected[0];
                }
                wrapper.repaint();
            }
        };

        // Attach listeners to every child so dragging works anywhere user clicks
        wrapper.addMouseListener(dragAdapter);
        wrapper.addMouseMotionListener(dragAdapter);
        iconItem.addMouseListener(dragAdapter);
        iconItem.addMouseMotionListener(dragAdapter);
        iconLabel.addMouseListener(dragAdapter);
        iconLabel.addMouseMotionListener(dragAdapter);
        textLabel.addMouseListener(dragAdapter);
        textLabel.addMouseMotionListener(dragAdapter);

        return wrapper;
    }

    public void showToast(NotificationMessage msg) {
        final ToastNotification[] toastHolder = new ToastNotification[1];
        toastHolder[0] = new ToastNotification(msg, () -> {
            if (toastHolder[0] != null) {
                toastContainer.remove(toastHolder[0]);
                toastContainer.revalidate();
                toastContainer.repaint();
            }
        });
        toastContainer.add(toastHolder[0]);
        toastContainer.add(Box.createVerticalStrut(8));
        layoutToastContainer();
        toastContainer.revalidate();
        toastContainer.repaint();
    }

    private void layoutToastContainer() {
        int w = 360;
        int h = Math.min(getHeight() - 60, toastContainer.getPreferredSize().height);
        int x = getWidth() - w - 20;
        int y = getHeight() - h - 20;
        toastContainer.setBounds(Math.max(0, x), Math.max(0, y), w, h);
    }

    @Override
    public void doLayout() {
        super.doLayout();
        layoutToastContainer();
        if (shortcutPanel != null) {
            shortcutPanel.setBounds(0, 0, Math.max(1024, getWidth()), Math.max(600, getHeight()));
        }
        if (desktopWidgets != null) {
            int widgetW = 310;
            int widgetH = 490;
            desktopWidgets.setBounds(Math.max(20, getWidth() - widgetW - 20), 20, widgetW, widgetH);
        }
    }

    public boolean isPointOverLogo(int x, int y) {
        int h = getHeight();
        int cx = (int) currentVpX;
        int cy = (int) (currentVpY - Math.min(130, h * 0.20));
        int logoSize = Math.min(620, Math.max(380, (int) (h * 0.54)));
        int radius = (int) (logoSize * 0.50);
        int dx = x - cx;
        int dy = y - cy;
        return (dx * dx + dy * dy) <= (radius * radius);
    }

    public boolean isPointOver3DLogo(int x, int y) {
        return isPointOverLogo(x, y);
    }

    public void triggerLogoClick() {
        if (isCollapsing) return;
        isCollapsing = true;
        collapseProgress = 0f;
        collapseAngle = 0f;
        collapseTriggered = false;
        if (os.getSoundService() != null) {
            os.getSoundService().playClick();
        }
        repaint();
    }

    public void showSnapGhost(Rectangle bounds) {
        this.snapGhostBounds = bounds;
        repaint();
    }

    public void hideSnapGhost() {
        if (this.snapGhostBounds != null) {
            this.snapGhostBounds = null;
            repaint();
        }
    }

    public void toggleWidgets() {
        setWidgetsVisible(!showWidgets);
    }

    public void setWidgetsVisible(boolean visible) {
        this.showWidgets = visible;
        if (desktopWidgets != null) {
            desktopWidgets.setVisible(visible);
            repaint();
        }
    }

    public boolean isWidgetsVisible() {
        return showWidgets;
    }

    public void cycle3DGeometry() {
        geometryMode = (geometryMode + 1) % 4;
        String name = getGeometryName();
        if (os != null && os.getNotificationManager() != null) {
            os.getNotificationManager().postNotification(
                    "3D MATRIX GEOMETRY",
                    "Switched to " + name,
                    NotificationMessage.Level.INFO
            );
        }
        if (os != null && os.getSoundService() != null) {
            os.getSoundService().playClick();
        }
        repaint();
    }

    public void setGeometryMode(int mode) {
        this.geometryMode = Math.max(0, Math.min(3, mode));
        repaint();
    }

    public String getGeometryName() {
        switch (geometryMode) {
            case GEO_TESSERACT: return "4D TESSERACT HYPERCUBE";
            case GEO_ICOSAHEDRON: return "ICOSAHEDRON CYBER SPHERE";
            case GEO_HEX_SHIELD: return "HEXAGONAL CYBER SHIELD";
            default: return "OCTAHEDRON CRYSTAL CAGE";
        }
    }

    public void showDesktopMenu(Point pt) {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(theme.panel);
        menu.setBorder(BorderFactory.createLineBorder(theme.neonAccent, 1));

        JCheckBoxMenuItem miWidgets = new JCheckBoxMenuItem("Desktop Telemetry & Clock Widgets", showWidgets);
        miWidgets.addActionListener(ev -> setWidgetsVisible(miWidgets.isSelected()));
        menu.add(miWidgets);

        menu.addSeparator();

        // 3D Matrix Hologram Geometry Submenu
        JMenu geoMenu = new JMenu("❖ 3D Matrix Hologram Geometry (Key: M)");
        String[] geoNames = {"Octahedron Crystal Cage", "4D Tesseract Hypercube", "Icosahedron Cyber Sphere", "Hexagonal Cyber Shield"};
        for (int i = 0; i < geoNames.length; i++) {
            final int m = i;
            JMenuItem gi = new JMenuItem((geometryMode == i ? "● " : "  ") + geoNames[i]);
            gi.addActionListener(ev -> setGeometryMode(m));
            geoMenu.add(gi);
        }
        menu.add(geoMenu);

        JMenuItem miNetScanner = new JMenuItem("⚡ Scan Surrounding Devices (CyberDeck LAN)");
        miNetScanner.addActionListener(ev -> os.getApplicationManager().launchApp("cyberdeck"));
        menu.add(miNetScanner);

        menu.addSeparator();

        JMenuItem miTile2 = new JMenuItem("Tile Windows Side-by-Side (50/50)");
        miTile2.addActionListener(ev -> os.getWindowManager().tileSideBySide());
        menu.add(miTile2);

        JMenuItem miTile4 = new JMenuItem("Tile 4-Window Grid (2x2)");
        miTile4.addActionListener(ev -> os.getWindowManager().tile4Grid());
        menu.add(miTile4);

        JMenuItem miCascade = new JMenuItem("Cascade Windows");
        miCascade.addActionListener(ev -> os.getWindowManager().cascadeWindows());
        menu.add(miCascade);

        JMenuItem miMinAll = new JMenuItem("Minimize All Windows");
        miMinAll.addActionListener(ev -> os.getWindowManager().minimizeAll());
        menu.add(miMinAll);

        menu.addSeparator();

        JMenuItem miClip = new JMenuItem("Clipboard History (Win+V)");
        miClip.addActionListener(ev -> {
            if (os.getDesktopFrame() != null) {
                os.getDesktopFrame().showClipboardHistory();
            }
        });
        menu.add(miClip);

        JMenu wsMenu = new JMenu("Switch Workspace");
        for (int i = 0; i < WindowManager.TOTAL_WORKSPACES; i++) {
            final int target = i;
            String name = WindowManager.getWorkspaceName(i);
            JMenuItem item = new JMenuItem((i == os.getWindowManager().getCurrentWorkspace() ? "❖ " : "   ") + name + " [Ctrl+Alt+" + (i + 1) + "]");
            item.addActionListener(ev -> os.getWindowManager().switchWorkspace(target));
            wsMenu.add(item);
        }
        menu.add(wsMenu);

        menu.addSeparator();

        JMenuItem miSettings = new JMenuItem("Desktop Settings");
        miSettings.addActionListener(ev -> os.getApplicationManager().launchApp("settings"));
        menu.add(miSettings);

        menu.show(this, pt.x, pt.y);
    }

    @Override
    protected void paintChildren(Graphics g) {
        super.paintChildren(g);
        if (snapGhostBounds != null) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Theme theme = ThemeManager.getInstance().getCurrentTheme();

            Color fill = new Color(theme.neonAccent.getRed(), theme.neonAccent.getGreen(), theme.neonAccent.getBlue(), 45);
            Color border = theme.neonAccent;

            g2.setColor(fill);
            g2.fill(new RoundRectangle2D.Float(snapGhostBounds.x + 4, snapGhostBounds.y + 4, snapGhostBounds.width - 8, snapGhostBounds.height - 8, 14, 14));

            g2.setColor(border);
            g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, new float[]{8, 6}, 0));
            g2.draw(new RoundRectangle2D.Float(snapGhostBounds.x + 4, snapGhostBounds.y + 4, snapGhostBounds.width - 8, snapGhostBounds.height - 8, 14, 14));

            // Snap indicator badge in center
            String snapText = "❖ SNAP TO ZONE";
            g2.setFont(new Font("Segoe UI", Font.BOLD, 13));
            FontMetrics fm = g2.getFontMetrics();
            int tx = snapGhostBounds.x + (snapGhostBounds.width - fm.stringWidth(snapText)) / 2;
            int ty = snapGhostBounds.y + (snapGhostBounds.height / 2);

            g2.setColor(new Color(0, 0, 0, 160));
            g2.fillRoundRect(tx - 12, ty - 18, fm.stringWidth(snapText) + 24, 28, 8, 8);
            g2.setColor(theme.neonAccent);
            g2.drawString(snapText, tx, ty + 2);

            g2.dispose();
        }
    }

    public void setWallpaperStyle(String style) {
        this.wallpaperStyle = style;
        applyDesktopSettings();
    }

    public void applyDesktopSettings() {
        if (os != null && os.getSettingsService() != null) {
            String anim = os.getSettingsService().getDesktopAnimation();
            int delay = 50; // balanced (20 FPS)
            boolean run = true;
            if ("high".equalsIgnoreCase(anim)) {
                delay = 33; // 30 FPS
            } else if ("saver".equalsIgnoreCase(anim)) {
                delay = 100; // 10 FPS
            } else if ("static".equalsIgnoreCase(anim)) {
                run = false;
            }

            if (animTimer != null) {
                animTimer.stop();
            }
            if (run) {
                animTimer = new Timer(delay, e -> updateWallpaperEngine());
                animTimer.start();
            }
        }
        repaint();
    }

    private void initWallpaperEngine() {
        int w = 1920;
        int h = 1080;

        // Initialize digital matrix rain columns across the screen width
        matrixStreams.clear();
        int cols = 45;
        int spacing = w / cols;
        for (int i = 0; i < cols; i++) {
            matrixStreams.add(new MatrixStream(i * spacing + rnd.nextInt(Math.max(1, spacing / 2)), h, rnd));
        }

        // Initialize interactive cyber dust particles
        particles.clear();
        for (int i = 0; i < 55; i++) {
            particles.add(new CyberParticle(w, h, rnd));
        }

        // Initialize 3D quantum particles
        quantumParticles.clear();
        for (int i = 0; i < 40; i++) {
            quantumParticles.add(new QuantumOrbitalParticle(rnd));
        }

        // 30 FPS animation timer
        if (animTimer != null && animTimer.isRunning()) {
            animTimer.stop();
        }
        animTimer = new Timer(33, e -> updateWallpaperEngine());
        animTimer.start();
    }

    private void updateWallpaperEngine() {
        int w = Math.max(800, getWidth());
        int h = Math.max(600, getHeight());

        animTime += 0.035f;
        gridOffset = (gridOffset + 0.038f) % 1.0f;

        // Interactive 3D parallax tracking the mouse cursor
        float targetVpX = w * 0.5f + (mouseX - w * 0.5f) * 0.14f;
        float targetVpY = h * 0.53f + (mouseY - h * 0.5f) * 0.09f;
        currentVpX += (targetVpX - currentVpX) * 0.08f;
        currentVpY += (targetVpY - currentVpY) * 0.08f;

        // ── Animation 1: Singularity Collapse & Supernova Burst ──
        if (isCollapsing) {
            collapseProgress += 0.055f;
            if (collapseProgress < 0.40f) {
                float t = collapseProgress / 0.40f;
                collapseAngle += 0.35f + t * 0.45f;
            } else if (!collapseTriggered) {
                collapseTriggered = true;
                int cx = (int) currentVpX;
                int cy = (int) (currentVpY - Math.min(130, h * 0.20));
                addShockwave(cx, cy);
                try {
                    if (os.getSoundService() != null) {
                        os.getSoundService().playSlideSwitch();
                    }
                } catch (Exception ignored) {}

                // Spawn radial supernova burst sparks
                for (int i = 0; i < 24; i++) {
                    double rad = (Math.PI * 2 * i) / 24.0 + (rnd.nextFloat() - 0.5f) * 0.3f;
                    float spd = 5.0f + rnd.nextFloat() * 7.5f;
                    burstSparks.add(new BurstSpark(cx, cy, (float)Math.cos(rad) * spd, (float)Math.sin(rad) * spd));
                }

                try {
                    os.toggleStartMenu();
                } catch (Exception ignored) {}
            } else {
                collapseAngle *= 0.82f;
            }

            if (collapseProgress >= 1.0f) {
                isCollapsing = false;
                collapseProgress = 0f;
                collapseAngle = 0f;
                collapseTriggered = false;
            }
        }

        // Update burst sparks
        Iterator<BurstSpark> sparkIt = burstSparks.iterator();
        while (sparkIt.hasNext()) {
            if (!sparkIt.next().update()) {
                sparkIt.remove();
            }
        }

        // Update cursor trail particles
        Iterator<CursorTrailParticle> ctIt = cursorTrail.iterator();
        while (ctIt.hasNext()) {
            if (!ctIt.next().update()) {
                ctIt.remove();
            }
        }

        // ── 3D Camera & Orientation Tracking ──
        int cx = (int) currentVpX;
        int cy = (int) (currentVpY - Math.min(130, h * 0.20));
        float mouseRelX = (mouseX - cx) / (float) Math.max(200, w * 0.5f);
        float mouseRelY = (mouseY - cy) / (float) Math.max(200, h * 0.5f);

        targetYaw = Math.max(-0.68f, Math.min(0.68f, mouseRelX * 0.65f)) + manualYaw;
        targetPitch = Math.max(-0.55f, Math.min(0.55f, -mouseRelY * 0.50f)) + manualPitch;

        if (!isDragging3D) {
            manualYaw *= 0.93f;
            manualPitch *= 0.93f;
        }

        currentYaw += (targetYaw - currentYaw) * 0.10f;
        currentPitch += (targetPitch - currentPitch) * 0.10f;
        cageRotationAngle += 0.018f;
        tesseractRot4D += 0.022f;

        if (isCollapsing) {
            currentRoll += 0.22f;
            currentYaw += 0.15f;
            currentPitch += 0.09f;
        } else {
            currentRoll *= 0.88f;
        }

        for (QuantumOrbitalParticle qp : quantumParticles) {
            qp.update();
        }

        // ── Animation 2: Orbital Gyro Rings Interpolation ──
        float targetGyro = isLogoHovered ? 1.0f : 0.0f;
        gyroHoverAlpha += (targetGyro - gyroHoverAlpha) * 0.14f;
        gyroAngle1 += 0.038f;
        gyroAngle2 -= 0.026f;

        // ── Animation 3: Audio-Reactive Resonance ──
        boolean audioActive = javaos.apps.MediaPlayerApp.isAudioPlaying();
        float targetPulse = audioActive ? 1.0f : 0.0f;
        audioPulseEnergy += (targetPulse - audioPulseEnergy) * 0.12f;

        // Update matrix streams
        for (MatrixStream ms : matrixStreams) {
            ms.update(h, rnd);
        }

        // Update cyber particles with mouse interaction
        for (CyberParticle p : particles) {
            p.update(w, h, mouseX, mouseY);
        }

        // Update expanding shockwaves
        Iterator<Shockwave> it = shockwaves.iterator();
        while (it.hasNext()) {
            if (!it.next().update()) {
                it.remove();
            }
        }

        repaint();
    }

    public void addShockwave(int x, int y) {
        shockwaves.add(new Shockwave(x, y));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        int w = getWidth();
        int h = getHeight();

        // 1. Deep Cyberpunk Void Backdrop
        g2.setPaint(new GradientPaint(0, 0, theme.background, 0, h, new Color(5, 7, 14)));
        g2.fillRect(0, 0, w, h);

        // 2. Hacker Digital Matrix Rain in distant cyberspace
        paintMatrixRain(g2, w, h, theme);

        // 3. 3D Perspective Moving Cyber-Grid Floor
        paint3DFloorGrid(g2, w, h, theme);

        // 3.5. Cyberspace Spectrum Horizon Wave Bar
        paintCyberspaceSpectrumBar(g2, w, h, theme);

        // 4. Centerpiece: IMPERION OS Borderless Hologram Insignia with 3 Animations
        paintDesktopImperionLogo(g2, w, h, theme);

        // 5. Interactive Ambient Cyber-Dust Particles
        paintCyberParticles(g2, w, h, theme);

        // 5.5. Neon Laser Cursor Trail
        paintCursorTrail(g2, theme);

        // 6. Interactive Click Shockwaves / Radar Pulses
        if (os == null || os.getSettingsService() == null || os.getSettingsService().isShockwavesEnabled()) {
            paintShockwaves(g2, theme);
        }

        // 7. Subtle Cyberpunk Scanlines
        if (os == null || os.getSettingsService() == null || os.getSettingsService().isScanlinesEnabled()) {
            paintScanlines(g2, w, h);
        }

        g2.dispose();
    }

    private void paintCursorTrail(Graphics2D g2, Theme theme) {
        if (cursorTrail.isEmpty()) return;
        for (int i = 0; i < cursorTrail.size(); i++) {
            CursorTrailParticle p = cursorTrail.get(i);
            int a = Math.max(0, Math.min(255, (int)(p.alpha * 170)));
            g2.setColor(new Color(theme.neonAccent.getRed(), theme.neonAccent.getGreen(), theme.neonAccent.getBlue(), a));
            g2.fillOval((int)(p.x - p.size / 2), (int)(p.y - p.size / 2), (int)p.size, (int)p.size);
            // Core white glint
            if (p.alpha > 0.4f) {
                int a2 = Math.max(0, Math.min(255, (int)(p.alpha * 190)));
                g2.setColor(new Color(255, 255, 255, a2));
                g2.fillOval((int)(p.x - p.size * 0.25f), (int)(p.y - p.size * 0.25f), (int)(p.size * 0.5f), (int)(p.size * 0.5f));
            }
        }
    }

    private void paintCyberspaceSpectrumBar(Graphics2D g2, int w, int h, Theme theme) {
        int horizonY = (int) currentVpY;
        boolean audioActive = javaos.apps.MediaPlayerApp.isAudioPlaying();
        float beat = javaos.apps.MediaPlayerApp.getAudioBeat();

        int bars = 48;
        int barW = Math.max(4, w / bars);
        for (int i = 0; i < bars; i++) {
            float norm = (float) i / bars;
            double wave = Math.abs(Math.sin(norm * Math.PI * 4.0 + animTime * 5.0));
            if (audioActive) {
                wave = (wave * 0.35 + (Math.sin(i * 1.4 + animTime * 10.0) * 0.5 + 0.5) * 0.65) * (0.4 + beat * 0.6);
            } else {
                wave *= 0.22;
            }
            int barH = (int) (wave * (audioActive ? 26 : 8));
            if (barH < 2) barH = 2;

            int bx = i * barW;
            int by = horizonY - barH / 2;
            int alpha = (int) Math.min(220, (audioActive ? 180 : 55) * (1.0f - Math.abs(norm - 0.5f) * 0.7f));
            g2.setColor(new Color(theme.neonAccent.getRed(), theme.neonAccent.getGreen(), theme.neonAccent.getBlue(), Math.max(0, alpha)));
            g2.fillRect(bx, by, barW - 1, barH);
        }
    }

    /**
     * Paints an authentic 3D perspective cyber grid rushing towards the viewer with horizon glow.
     */
    private void paint3DFloorGrid(Graphics2D g2, int w, int h, Theme theme) {
        int horizonY = (int) currentVpY;

        // Horizon neon glow aura
        int glowW = Math.max(w, 800);
        int glowH = Math.max(120, (int) (h * 0.22));
        RadialGradientPaint horizonGlow = new RadialGradientPaint(
                currentVpX, horizonY, glowW * 0.5f,
                new float[]{0.0f, 0.4f, 1.0f},
                new Color[]{
                        new Color(theme.neonAccent.getRed(), theme.neonAccent.getGreen(), theme.neonAccent.getBlue(), 90),
                        new Color(theme.secondaryAccent.getRed(), theme.secondaryAccent.getGreen(), theme.secondaryAccent.getBlue(), 35),
                        new Color(0, 0, 0, 0)
                }
        );
        g2.setPaint(horizonGlow);
        g2.fillRect(0, Math.max(0, horizonY - glowH / 2), w, glowH);

        // 3D Transverse Depth Lines (moving forward towards viewer)
        for (int i = 1; i <= 24; i++) {
            double z = i - gridOffset;
            if (z <= 0.08) continue;

            double py = horizonY + (h - horizonY) / Math.pow(z, 0.72);
            if (py > h || py < horizonY) continue;

            // Lines fade with distance creating atmospheric cyber fog
            int alpha = (int) Math.min(220, Math.max(8, (1.0 - (z / 25.0)) * 190));
            Color lineColor = new Color(theme.neonAccent.getRed(), theme.neonAccent.getGreen(), theme.neonAccent.getBlue(), alpha);
            g2.setColor(lineColor);
            float strokeW = (float) Math.max(0.8f, Math.min(2.5f, 2.8f - (z * 0.1f)));
            g2.setStroke(new BasicStroke(strokeW));
            g2.drawLine(0, (int) py, w, (int) py);
        }

        // 3D Longitudinal Perspective Fan Lines radiating from vanishing point
        int fanStep = Math.max(50, w / 22);
        for (int x = -w / 2; x <= w + w / 2; x += fanStep) {
            Color fanCol = new Color(theme.secondaryAccent.getRed(), theme.secondaryAccent.getGreen(), theme.secondaryAccent.getBlue(), 45);
            g2.setColor(fanCol);
            g2.setStroke(new BasicStroke(1.0f));
            g2.drawLine((int) currentVpX, horizonY, x, h);
        }

        // Dynamic Hacker Horizon Waveform
        Path2D.Double wave = new Path2D.Double();
        wave.moveTo(0, horizonY);
        for (int x = 0; x <= w; x += 10) {
            double wy = horizonY
                    + Math.sin(x * 0.018 + animTime * 3.5) * 6
                    + Math.cos(x * 0.045 - animTime * 2.0) * 4;
            wave.lineTo(x, wy);
        }
        g2.setColor(theme.neonAccent);
        g2.setStroke(new BasicStroke(1.8f));
        g2.draw(wave);
    }

    /**
     * Paints the official borderless IMPERION OS wolf insignia centerpiece floating in cyberspace,
     * powered by a full 3D interactive perspective holographic engine:
     * 1. 3D Euler gimbal tilt following mouse cursor & manual drag rotation
     * 2. Volumetric 3D holographic depth slices with stereoscopic parallax
     * 3. 3D rotating dual-pass gyroscopic telemetry rings with depth-sorted satellites
     * 4. 3D floating quantum cyber-cage (polyhedron) with illuminated vertices
     * 5. 3D quantum swarm particles orbiting the core with Z-depth sorting
     * 6. 3D singularity implosion on click with supernova burst
     * 7. Real-time floating 3D telemetry HUD with live pitch/yaw readouts
     */
    private void paintDesktopImperionLogo(Graphics2D g2, int w, int h, Theme theme) {
        try {
            BufferedImage img = LogoManager.getLogo();
            if (img == null) return;

            int cx = (int) currentVpX;
            int cy = (int) (currentVpY - Math.min(130, h * 0.20));
            int baseLogoSize = Math.min(620, Math.max(380, (int) (h * 0.54)));

            // ── Animation 3: Audio-Reactive Resonant Pulse ──
            float breath = (float) Math.sin(animTime * 3.0) * 0.025f;
            if (audioPulseEnergy > 0.01f) {
                breath += audioPulseEnergy * (float) (Math.sin(animTime * 10.0) * 0.045f + Math.abs(Math.sin(animTime * 5.0)) * 0.035f);
            }

            // ── Animation 1: Singularity Collapse & Supernova Rebound Scale ──
            float collapseScale = 1.0f;
            float logoAlpha = 1.0f;
            if (isCollapsing) {
                if (collapseProgress < 0.40f) {
                    float t = collapseProgress / 0.40f;
                    // Collapse rapidly into singularity
                    collapseScale = Math.max(0.04f, (1.0f - t) * (1.0f - t));
                    logoAlpha = Math.max(0.2f, 1.0f - t * 0.7f);
                } else {
                    // Supernova elastic rebound
                    float t = (collapseProgress - 0.40f) / 0.60f;
                    collapseScale = 1.0f + (float) Math.sin(t * Math.PI) * 0.32f;
                    logoAlpha = 1.0f;
                }
            }

            float totalScale = (1.0f + breath) * collapseScale;
            int currentSize = (int) (baseLogoSize * totalScale);
            if (currentSize <= 4) currentSize = 4;
            int aspectH = (int) (currentSize * (img.getHeight() / (double) img.getWidth()));
            int hoverY = (int) (Math.sin(animTime * 2.2) * 5.0);

            // ── Ambient Cyan & Violet Radial Backlight Glow ──
            int glowR = (int) (currentSize * (0.50f + audioPulseEnergy * 0.15f + gyroHoverAlpha * 0.15f));
            if (glowR > 10) {
                int glowAlpha1 = Math.max(0, Math.min(255, (int) (65 + gyroHoverAlpha * 75 + audioPulseEnergy * 65)));
                int glowAlpha2 = Math.max(0, Math.min(255, (int) (25 + gyroHoverAlpha * 40)));
                RadialGradientPaint rgp = new RadialGradientPaint(
                        cx, cy + hoverY, glowR,
                        new float[]{0.0f, 0.45f, 1.0f},
                        new Color[]{
                                new Color(theme.neonAccent.getRed(), theme.neonAccent.getGreen(), theme.neonAccent.getBlue(), glowAlpha1),
                                new Color(theme.secondaryAccent.getRed(), theme.secondaryAccent.getGreen(), theme.secondaryAccent.getBlue(), glowAlpha2),
                                new Color(0, 0, 0, 0)
                        }
                );
                g2.setPaint(rgp);
                g2.fillOval(cx - glowR, cy + hoverY - glowR, glowR * 2, glowR * 2);
            }

            // ── PASS 1: BACKGROUND 3D ELEMENTS (z > 0: Behind the central logo) ──
            paint3DScenePass(g2, cx, cy + hoverY, currentSize, theme, true);

            // ── PASS 2: 3D VOLUMETRIC HOLOGRAM OF IMPERION WOLF INSIGNIA ──
            paint3DVolumetricLogo(g2, img, cx, cy + hoverY, currentSize, aspectH, logoAlpha, theme);

            // ── PASS 3: FOREGROUND 3D ELEMENTS (z <= 0: In front of the central logo) ──
            paint3DScenePass(g2, cx, cy + hoverY, currentSize, theme, false);

            // ── PASS 4: 3D INTERACTIVE TELEMETRY HUD ──
            paint3DTelemetryHUD(g2, cx, cy + hoverY, currentSize, theme);

            // ── Singularity Core Flare & Supernova Sparks ──
            if (isCollapsing && collapseProgress < 0.45f) {
                // Intense white pinpoint singularity
                float flareIntensity = Math.max(0f, Math.min(1f, 1.0f - (float) Math.abs((collapseProgress - 0.40f) / 0.40f)));
                int flareR = (int) (24 * flareIntensity);
                if (flareR > 2) {
                    int fAlpha = Math.max(0, Math.min(255, (int)(250 * flareIntensity)));
                    g2.setColor(new Color(255, 255, 255, fAlpha));
                    g2.fillOval(cx - flareR, cy + hoverY - flareR, flareR * 2, flareR * 2);
                    // Cross lens flare beams
                    int beamLen = (int) (80 * flareIntensity);
                    int bAlpha = Math.max(0, Math.min(255, (int)(220 * flareIntensity)));
                    g2.setColor(new Color(theme.neonAccent.getRed(), theme.neonAccent.getGreen(), theme.neonAccent.getBlue(), bAlpha));
                    g2.setStroke(new BasicStroke(2.0f));
                    g2.drawLine(cx - beamLen, cy + hoverY, cx + beamLen, cy + hoverY);
                    g2.drawLine(cx, cy + hoverY - beamLen, cx, cy + hoverY + beamLen);
                }
            }

            // Paint burst sparks
            paintBurstSparks(g2, theme);

            // ── Audio-Reactive Baseline Soundwave Ripple ──
            if (audioPulseEnergy > 0.05f) {
                paintAudioBaselinePulse(g2, cx, cy + hoverY + aspectH / 2 + 10, currentSize, theme);
            }
        } catch (Exception e) {
            // Defensive catch to guarantee EDT thread never hangs or crashes
        }
    }

    /**
     * Renders the 3D multi-layered volumetric hologram of the Imperion Wolf insignia.
     * Stacks stereoscopic parallax depth slices that physically shift in 3D perspective
     * as the user moves their cursor or drags the hologram.
     * Uses relative Graphics2D transforms to preserve HiDPI device scaling perfectly.
     */
    private void paint3DVolumetricLogo(Graphics2D g2, BufferedImage img, int cx, int cy, int size, int aspectH, float logoAlpha, Theme theme) {
        Graphics2D gLogo = (Graphics2D) g2.create();
        gLogo.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        gLogo.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        gLogo.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        float cosY = (float) Math.cos(currentYaw);
        float cosP = (float) Math.cos(currentPitch);
        float shearX = -(float) (Math.sin(currentPitch) * Math.sin(currentYaw) * 0.45);
        float shearY = (float) (Math.sin(currentYaw) * Math.sin(currentPitch) * 0.45);

        int halfW = size / 2;
        int halfH = aspectH / 2;

        // ── Depth Layer 1: Deep Hologram Violet Parallax Aura (z = +20) ──
        {
            Graphics2D gBack = (Graphics2D) gLogo.create();
            gBack.translate(cx - currentYaw * 20f, cy + currentPitch * 20f);
            if (isCollapsing && collapseAngle != 0f) gBack.rotate(collapseAngle);
            gBack.scale(cosY * 0.96f, cosP * 0.96f);
            gBack.shear(shearX, shearY);
            gBack.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.32f * logoAlpha));
            gBack.drawImage(img, -halfW, -halfH, size, aspectH, null);
            gBack.dispose();
        }

        // ── Depth Layer 2: Mid-Back Cyan Radiance Parallax Slice (z = +10) ──
        {
            Graphics2D gMid = (Graphics2D) gLogo.create();
            gMid.translate(cx - currentYaw * 10f, cy + currentPitch * 10f);
            if (isCollapsing && collapseAngle != 0f) gMid.rotate(collapseAngle);
            gMid.scale(cosY * 0.98f, cosP * 0.98f);
            gMid.shear(shearX, shearY);
            gMid.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.50f * logoAlpha));
            gMid.drawImage(img, -halfW, -halfH, size, aspectH, null);
            gMid.dispose();
        }

        // ── Depth Layer 3: Central Crisp Imperion Wolf Emblem (z = 0: DEAD CENTER) ──
        if (size > 4 && aspectH > 4) {
            // Render logo + masked laser scanlines & specular sheen into an offscreen buffer
            BufferedImage logoCanvas = new BufferedImage(size, aspectH, BufferedImage.TYPE_INT_ARGB);
            Graphics2D gCanvas = logoCanvas.createGraphics();
            gCanvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            gCanvas.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            gCanvas.drawImage(img, 0, 0, size, aspectH, null);

            // Masked Scanlines & Sheen: AlphaComposite.SRC_ATOP restricts effects strictly to the wolf insignia pixels!
            gCanvas.setComposite(AlphaComposite.SrcAtop);

            // Holographic Laser Scanlines
            int scanAlpha = (int) (38 + Math.sin(animTime * 6.0) * 20);
            gCanvas.setColor(new Color(theme.neonAccent.getRed(), theme.neonAccent.getGreen(), theme.neonAccent.getBlue(), scanAlpha));
            gCanvas.setStroke(new BasicStroke(1.2f));
            for (int y = (int) ((animTime * 30) % 12); y < aspectH; y += 12) {
                gCanvas.drawLine(0, y, size, y);
            }

            // Dynamic 3D Specular Reflection Sheen
            float sheenOffset = ((animTime * 0.75f + currentYaw * 1.5f + currentPitch * 1.5f) % 2.0f) - 0.5f;
            int sheenX = (int) (sheenOffset * size);
            if (sheenX > -60 && sheenX < size + 60) {
                GradientPaint gp = new GradientPaint(
                        sheenX - 35, 0, new Color(255, 255, 255, 0),
                        sheenX, 0, new Color(255, 255, 255, 140),
                        true
                );
                gCanvas.setPaint(gp);
                gCanvas.fillRect(sheenX - 35, 0, 70, aspectH);
            }
            gCanvas.dispose();

            // Project 3D transformed Core onto Desktop
            Graphics2D gCore = (Graphics2D) gLogo.create();
            gCore.translate(cx, cy);
            if (isCollapsing && collapseAngle != 0f) gCore.rotate(collapseAngle);
            gCore.scale(cosY, cosP);
            gCore.shear(shearX, shearY);
            if (logoAlpha < 1.0f) {
                gCore.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, logoAlpha));
            }
            gCore.drawImage(logoCanvas, -halfW, -halfH, null);
            gCore.dispose();
        }

        gLogo.dispose();
    }

    /**
     * Renders depth-sorted 3D elements in either background pass (z > 0) or foreground pass (z <= 0):
     * - 3D Gyroscopic Telemetry Gimbal Rings with real CPU / RAM load arcs
     * - 3D Orbiting Satellites with live telemetry HUD tags
     * - 3D Morphing Quantum Cyber Polyhedron (Octahedron, 4D Tesseract, Icosahedron, Hex-Shield)
     * - 3D Quantum Swarm Particles
     */
    private void paint3DScenePass(Graphics2D g2, int cx, int cy, int size, Theme theme, boolean backgroundPass) {
        Graphics2D g3d = (Graphics2D) g2.create();
        g3d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        float fov = 580f;

        // Query real host hardware metrics
        double cpuLoad = 25.0;
        double ramLoad = 50.0;
        String uptime = "00:00:00";
        int activeThreads = 24;
        if (os != null && os.getSystemMetricsService() != null) {
            double cl = os.getSystemMetricsService().getHostCpuLoad();
            if (cl >= 0) cpuLoad = cl;
            ramLoad = os.getSystemMetricsService().getHostRamUsagePercent();
            uptime = os.getSystemMetricsService().getFormattedUptime();
            activeThreads = os.getSystemMetricsService().getActiveThreadCount();
        }
        float cpuFill = Math.max(0.08f, Math.min(1.0f, (float)(cpuLoad / 100.0)));
        float ramFill = Math.max(0.10f, Math.min(1.0f, (float)(ramLoad / 100.0)));

        // ── 1. 3D Gyroscopic Telemetry Rings ──
        if (gyroHoverAlpha > 0.01f || isLogoHovered) {
            float effAlpha = Math.max(gyroHoverAlpha, isLogoHovered ? 1.0f : 0.45f);
            int baseRingAlpha = Math.max(0, Math.min(255, (int) (effAlpha * 185)));

            // Outer Ring 1 (Neon Cyan, real CPU load arc, tilted 24 deg on X, 16 deg on Z)
            float r1 = size * 0.74f;
            paint3DProjectedRing(g3d, cx, cy, r1, 0.42f, 0.28f, gyroAngle1,
                    new float[]{10f, 6f, 3f, 6f}, 25f, theme.neonAccent, baseRingAlpha, fov, backgroundPass, cpuFill);

            // 4 Orbiting Telemetry Satellites along Ring 1
            String[] satLabels = {
                    String.format("CPU %d%%", (int) cpuLoad),
                    String.format("RAM %d%%", (int) ramLoad),
                    "UP " + uptime,
                    "THR " + activeThreads
            };

            for (int i = 0; i < 4; i++) {
                double satAngle = gyroAngle1 + i * (Math.PI / 2.0);
                Point3D localSat = getRingLocalPoint(r1, (float) satAngle, 0.42f, 0.28f);
                Point3D sat = project3D(localSat.x, localSat.y, localSat.z, currentPitch, currentYaw, currentRoll, fov, cx, cy);

                boolean inPass = backgroundPass ? (sat.z > 0) : (sat.z <= 0);
                if (inPass) {
                    int satAlpha = backgroundPass ? Math.max(0, Math.min(255, (int)(baseRingAlpha * 0.6f))) : 240;
                    g3d.setColor(Color.WHITE);
                    g3d.fillOval((int) sat.x - 3, (int) sat.y - 3, 6, 6);
                    g3d.setColor(new Color(theme.neonAccent.getRed(), theme.neonAccent.getGreen(), theme.neonAccent.getBlue(), satAlpha));
                    g3d.drawOval((int) sat.x - 5, (int) sat.y - 5, 10, 10);

                    if (!backgroundPass) {
                        // Foreground satellite target brackets
                        g3d.setStroke(new BasicStroke(1.0f));
                        g3d.drawLine((int) sat.x - 8, (int) sat.y - 4, (int) sat.x - 8, (int) sat.y + 4);
                        g3d.drawLine((int) sat.x + 8, (int) sat.y - 4, (int) sat.x + 8, (int) sat.y + 4);

                        // Live Telemetry Readout Beside Satellite
                        g3d.setFont(new Font("Consolas", Font.BOLD, 10));
                        g3d.setColor(theme.neonAccent);
                        g3d.drawString(satLabels[i], (int) sat.x + 11, (int) sat.y + 4);
                    }
                }
            }

            // Middle Ring 2 (Secondary Accent / Violet, real RAM load arc, tilted -36 deg on X, -22 deg on Z)
            float r2 = size * 0.58f;
            paint3DProjectedRing(g3d, cx, cy, r2, -0.62f, -0.38f, gyroAngle2,
                    new float[]{6f, 8f}, 14f, theme.secondaryAccent, (int) (baseRingAlpha * 0.85f), fov, backgroundPass, ramFill);

            // Inner Ring 3 (Equator Flux Ring, fast spin)
            float r3 = size * 0.44f;
            paint3DProjectedRing(g3d, cx, cy, r3, 0.12f, 0.05f, gyroAngle1 * 1.8f,
                    new float[]{4f, 4f}, 8f, theme.neonAccent, (int) (baseRingAlpha * 0.65f), fov, backgroundPass, 1.0f);
        }

        // ── 2. 3D Floating Quantum Cyber Polyhedron (Crystal Cage) ──
        paint3DQuantumCage(g3d, cx, cy, size * 0.68f, fov, theme, backgroundPass);

        // ── 3. 3D Quantum Swarm Particles ──
        for (QuantumOrbitalParticle qp : quantumParticles) {
            Point3D local = getRingLocalPoint(qp.radiusRatio * size, qp.theta, qp.tiltX, qp.tiltZ);
            Point3D pt = project3D(local.x, local.y, local.z, currentPitch, currentYaw, currentRoll, fov, cx, cy);

            boolean inPass = backgroundPass ? (pt.z > 0) : (pt.z <= 0);
            if (inPass) {
                float pScale = fov / Math.max(15f, fov + pt.z);
                float drawSize = Math.max(1.5f, qp.size * pScale);
                int pAlpha = backgroundPass ? Math.max(20, Math.min(180, (int) (130 * pScale))) : Math.max(50, Math.min(255, (int) (220 * pScale)));

                Color pCol = backgroundPass ? theme.secondaryAccent : theme.neonAccent;
                g3d.setColor(new Color(pCol.getRed(), pCol.getGreen(), pCol.getBlue(), pAlpha));
                g3d.fillOval((int) (pt.x - drawSize * 0.5f), (int) (pt.y - drawSize * 0.5f), (int) drawSize, (int) drawSize);

                if (!backgroundPass && pScale > 0.95f) {
                    g3d.setColor(Color.WHITE);
                    g3d.fillOval((int) (pt.x - 1), (int) (pt.y - 1), 2, 2);
                }
            }
        }

        g3d.dispose();
    }

    /**
     * Projects a 3D ring into screen perspective and draws segments with fillRatio representing load percentage.
     */
    private void paint3DProjectedRing(Graphics2D g, int cx, int cy, float radius, float tiltX, float tiltZ, float rotAngle,
                                      float[] dash, float dashLen, Color color, int alpha, float fov, boolean backgroundPass, float fillRatio) {
        int steps = 36;
        Point3D[] pts = new Point3D[steps];
        for (int i = 0; i < steps; i++) {
            float theta = (float) (i * Math.PI * 2 / steps) + rotAngle;
            Point3D local = getRingLocalPoint(radius, theta, tiltX, tiltZ);
            pts[i] = project3D(local.x, local.y, local.z, currentPitch, currentYaw, currentRoll, fov, cx, cy);
        }

        for (int i = 0; i < steps; i++) {
            Point3D p1 = pts[i];
            Point3D p2 = pts[(i + 1) % steps];
            float avgZ = (p1.z + p2.z) * 0.5f;

            boolean inPass = backgroundPass ? (avgZ > 0) : (avgZ <= 0);
            if (inPass) {
                float zScale = fov / Math.max(15f, fov + avgZ);
                boolean isFilled = ((i + 1) / (float) steps) <= fillRatio;
                int segAlpha = isFilled
                        ? Math.max(0, Math.min(255, (int) (alpha * Math.min(1.4f, zScale))))
                        : Math.max(12, (int) (alpha * 0.28f * zScale));
                float strokeW = (isFilled ? 1.8f : 0.9f) * (backgroundPass ? 1.0f : 1.5f) * zScale;

                g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), segAlpha));
                g.setStroke(new BasicStroke(Math.max(0.6f, strokeW)));
                g.drawLine((int) p1.x, (int) p1.y, (int) p2.x, (int) p2.y);
            }
        }
    }

    /**
     * Renders the active 3D Quantum Cyber Geometry (Octahedron, 4D Tesseract, Icosahedron, or Hex-Shield).
     */
    private void paint3DQuantumCage(Graphics2D g, int cx, int cy, float radius, float fov, Theme theme, boolean backgroundPass) {
        switch (geometryMode) {
            case GEO_TESSERACT:
                paint3DTesseract(g, cx, cy, radius * 0.95f, fov, theme, backgroundPass);
                break;
            case GEO_ICOSAHEDRON:
                paint3DIcosahedron(g, cx, cy, radius * 0.95f, fov, theme, backgroundPass);
                break;
            case GEO_HEX_SHIELD:
                paint3DHexShield(g, cx, cy, radius * 1.05f, fov, theme, backgroundPass);
                break;
            default:
                paint3DOctahedron(g, cx, cy, radius, fov, theme, backgroundPass);
                break;
        }
    }

    /**
     * Mode 0: Octahedron Crystal Cage (6 Vertices, 12 Edges)
     */
    private void paint3DOctahedron(Graphics2D g, int cx, int cy, float radius, float fov, Theme theme, boolean backgroundPass) {
        Point3D[] localVerts = new Point3D[]{
                new Point3D(0, -radius * 1.15f, 0),        // 0: Top Apex
                new Point3D(0, radius * 1.15f, 0),         // 1: Bottom Apex
                new Point3D(radius, 0, 0),                 // 2: Right
                new Point3D(-radius, 0, 0),                // 3: Left
                new Point3D(0, 0, radius),                 // 4: Front
                new Point3D(0, 0, -radius)                 // 5: Back
        };

        Point3D[] proj = new Point3D[6];
        float cosC = (float) Math.cos(cageRotationAngle), sinC = (float) Math.sin(cageRotationAngle);
        float cosC2 = (float) Math.cos(cageRotationAngle * 0.6f), sinC2 = (float) Math.sin(cageRotationAngle * 0.6f);

        for (int i = 0; i < 6; i++) {
            Point3D v = localVerts[i];
            float x1 = v.x * cosC + v.z * sinC;
            float z1 = -v.x * sinC + v.z * cosC;
            float y1 = v.y;
            float y2 = y1 * cosC2 - z1 * sinC2;
            float z2 = y1 * sinC2 + z1 * cosC2;
            float x2 = x1;
            proj[i] = project3D(x2, y2, z2, currentPitch, currentYaw, currentRoll, fov, cx, cy);
        }

        int[][] edges = {
                {0, 2}, {0, 3}, {0, 4}, {0, 5},
                {1, 2}, {1, 3}, {1, 4}, {1, 5},
                {2, 4}, {4, 3}, {3, 5}, {5, 2}
        };

        renderWireframeEdgesAndNodes(g, proj, edges, fov, theme, backgroundPass);
    }

    /**
     * Mode 1: 4D Tesseract (Hypercube) with 16 vertices & 32 edges rotating through 4D hyperspace
     */
    private void paint3DTesseract(Graphics2D g, int cx, int cy, float radius, float fov, Theme theme, boolean backgroundPass) {
        float angle4D = tesseractRot4D;
        float cos4 = (float) Math.cos(angle4D), sin4 = (float) Math.sin(angle4D);
        float cos4B = (float) Math.cos(angle4D * 0.65f), sin4B = (float) Math.sin(angle4D * 0.65f);

        Point3D[] proj = new Point3D[16];
        float r4 = radius * 0.62f;
        float dist4D = r4 * 2.6f;

        for (int i = 0; i < 16; i++) {
            float x = ((i & 1) != 0 ? 1 : -1) * r4;
            float y = ((i & 2) != 0 ? 1 : -1) * r4;
            float z = ((i & 4) != 0 ? 1 : -1) * r4;
            float w = ((i & 8) != 0 ? 1 : -1) * r4;

            // Rotate in 4D X-W plane
            float x1 = x * cos4 - w * sin4;
            float w1 = x * sin4 + w * cos4;

            // Rotate in 4D Y-Z plane
            float y1 = y * cos4B - z * sin4B;
            float z1 = y * sin4B + z * cos4B;

            // 4D -> 3D perspective projection
            float scale4 = dist4D / Math.max(10f, dist4D - w1);
            float x3 = x1 * scale4;
            float y3 = y1 * scale4;
            float z3 = z1 * scale4;

            proj[i] = project3D(x3, y3, z3, currentPitch, currentYaw, currentRoll, fov, cx, cy);
        }

        // 32 Edges connecting vertices differing by 1 bit
        List<int[]> edgesList = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            for (int bit = 0; bit < 4; bit++) {
                int j = i ^ (1 << bit);
                if (i < j) edgesList.add(new int[]{i, j});
            }
        }
        int[][] edges = edgesList.toArray(new int[0][]);
        renderWireframeEdgesAndNodes(g, proj, edges, fov, theme, backgroundPass);
    }

    /**
     * Mode 2: Icosahedron Cyber Sphere (12 Vertices, 30 Edges)
     */
    private void paint3DIcosahedron(Graphics2D g, int cx, int cy, float radius, float fov, Theme theme, boolean backgroundPass) {
        float phi = (1.0f + (float) Math.sqrt(5.0)) * 0.5f;
        float scale = (radius * 0.72f) / (float) Math.sqrt(1.0 + phi * phi);

        Point3D[] local = {
                new Point3D(0, scale, scale * phi),
                new Point3D(0, scale, -scale * phi),
                new Point3D(0, -scale, scale * phi),
                new Point3D(0, -scale, -scale * phi),
                new Point3D(scale, scale * phi, 0),
                new Point3D(scale, -scale * phi, 0),
                new Point3D(-scale, scale * phi, 0),
                new Point3D(-scale, -scale * phi, 0),
                new Point3D(scale * phi, 0, scale),
                new Point3D(scale * phi, 0, -scale),
                new Point3D(-scale * phi, 0, scale),
                new Point3D(-scale * phi, 0, -scale)
        };

        float cosC = (float) Math.cos(cageRotationAngle * 0.8f), sinC = (float) Math.sin(cageRotationAngle * 0.8f);
        float cosC2 = (float) Math.cos(cageRotationAngle * 0.5f), sinC2 = (float) Math.sin(cageRotationAngle * 0.5f);
        Point3D[] proj = new Point3D[12];

        for (int i = 0; i < 12; i++) {
            Point3D v = local[i];
            float x1 = v.x * cosC + v.z * sinC;
            float z1 = -v.x * sinC + v.z * cosC;
            float y1 = v.y;
            float y2 = y1 * cosC2 - z1 * sinC2;
            float z2 = y1 * sinC2 + z1 * cosC2;
            float x2 = x1;
            proj[i] = project3D(x2, y2, z2, currentPitch, currentYaw, currentRoll, fov, cx, cy);
        }

        List<int[]> edgesList = new ArrayList<>();
        float thresh = (scale * 2.05f) * (scale * 2.05f);
        for (int i = 0; i < 12; i++) {
            for (int j = i + 1; j < 12; j++) {
                float dx = local[i].x - local[j].x;
                float dy = local[i].y - local[j].y;
                float dz = local[i].z - local[j].z;
                if ((dx * dx + dy * dy + dz * dz) <= thresh) {
                    edgesList.add(new int[]{i, j});
                }
            }
        }
        int[][] edges = edgesList.toArray(new int[0][]);
        renderWireframeEdgesAndNodes(g, proj, edges, fov, theme, backgroundPass);
    }

    /**
     * Mode 3: Hexagonal Cyber Shield Ring (12 Vertices, 18 Edges)
     */
    private void paint3DHexShield(Graphics2D g, int cx, int cy, float radius, float fov, Theme theme, boolean backgroundPass) {
        Point3D[] proj = new Point3D[12];
        float rot = cageRotationAngle * 0.7f;
        float hHalf = radius * 0.45f;
        float rHex = radius * 0.75f;

        for (int i = 0; i < 6; i++) {
            double a = rot + (i * Math.PI / 3.0);
            float x = (float) Math.cos(a) * rHex;
            float z = (float) Math.sin(a) * rHex;
            proj[i] = project3D(x, -hHalf, z, currentPitch, currentYaw, currentRoll, fov, cx, cy);
            proj[i + 6] = project3D(x, hHalf, z, currentPitch, currentYaw, currentRoll, fov, cx, cy);
        }

        List<int[]> edgesList = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            edgesList.add(new int[]{i, (i + 1) % 6});             // Top ring
            edgesList.add(new int[]{i + 6, ((i + 1) % 6) + 6});     // Bottom ring
            edgesList.add(new int[]{i, i + 6});                     // Vertical struts
        }
        int[][] edges = edgesList.toArray(new int[0][]);
        renderWireframeEdgesAndNodes(g, proj, edges, fov, theme, backgroundPass);
    }

    private void renderWireframeEdgesAndNodes(Graphics2D g, Point3D[] proj, int[][] edges, float fov, Theme theme, boolean backgroundPass) {
        for (int[] edge : edges) {
            Point3D p1 = proj[edge[0]];
            Point3D p2 = proj[edge[1]];
            float avgZ = (p1.z + p2.z) * 0.5f;

            boolean inPass = backgroundPass ? (avgZ > 0) : (avgZ <= 0);
            if (inPass) {
                float zScale = fov / Math.max(15f, fov + avgZ);
                int alpha = Math.max(0, Math.min(255, (int) ((backgroundPass ? 65 : 165) * zScale)));
                float strokeW = Math.max(0.7f, (backgroundPass ? 1.0f : 1.6f) * zScale);

                Color edgeCol = backgroundPass ? theme.secondaryAccent : theme.neonAccent;
                g.setColor(new Color(edgeCol.getRed(), edgeCol.getGreen(), edgeCol.getBlue(), alpha));
                g.setStroke(new BasicStroke(strokeW));
                g.drawLine((int) p1.x, (int) p1.y, (int) p2.x, (int) p2.y);
            }
        }

        // Glowing Vertex Nodes
        for (Point3D p : proj) {
            boolean inPass = backgroundPass ? (p.z > 0) : (p.z <= 0);
            if (inPass) {
                float zScale = fov / Math.max(15f, fov + p.z);
                int nodeSize = Math.max(3, (int) (6 * zScale));
                int nAlpha = backgroundPass ? 80 : 220;

                g.setColor(new Color(theme.neonAccent.getRed(), theme.neonAccent.getGreen(), theme.neonAccent.getBlue(), nAlpha));
                g.fillOval((int) p.x - nodeSize / 2, (int) p.y - nodeSize / 2, nodeSize, nodeSize);
                if (!backgroundPass) {
                    g.setColor(Color.WHITE);
                    g.fillOval((int) p.x - 1, (int) p.y - 1, 3, 3);
                }
            }
        }
    }

    /**
     * Renders floating 3D telemetry brackets and pitch/yaw readouts around the hologram.
     */
    private void paint3DTelemetryHUD(Graphics2D g, int cx, int cy, int size, Theme theme) {
        if (!isLogoHovered && gyroHoverAlpha < 0.15f) return;

        Graphics2D gh = (Graphics2D) g.create();
        gh.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int hudAlpha = Math.max(0, Math.min(255, (int) (Math.max(gyroHoverAlpha, isLogoHovered ? 1f : 0f) * 190)));
        gh.setColor(new Color(theme.neonAccent.getRed(), theme.neonAccent.getGreen(), theme.neonAccent.getBlue(), hudAlpha));
        gh.setFont(theme.fontMonospace);

        int boxHalfW = (int) (size * 0.62f);
        int boxHalfH = (int) (size * 0.48f);
        int bLen = 14;

        // 4 Corner Brackets
        // Top-Left
        gh.drawLine(cx - boxHalfW, cy - boxHalfH, cx - boxHalfW + bLen, cy - boxHalfH);
        gh.drawLine(cx - boxHalfW, cy - boxHalfH, cx - boxHalfW, cy - boxHalfH + bLen);
        // Top-Right
        gh.drawLine(cx + boxHalfW, cy - boxHalfH, cx + boxHalfW - bLen, cy - boxHalfH);
        gh.drawLine(cx + boxHalfW, cy - boxHalfH, cx + boxHalfW, cy - boxHalfH + bLen);
        // Bottom-Left
        gh.drawLine(cx - boxHalfW, cy + boxHalfH, cx - boxHalfW + bLen, cy + boxHalfH);
        gh.drawLine(cx - boxHalfW, cy + boxHalfH, cx - boxHalfW, cy + boxHalfH - bLen);
        // Bottom-Right
        gh.drawLine(cx + boxHalfW, cy + boxHalfH, cx + boxHalfW - bLen, cy + boxHalfH);
        gh.drawLine(cx + boxHalfW, cy + boxHalfH, cx + boxHalfW, cy + boxHalfH - bLen);

        // Real-Time 3D Telemetry Readouts
        int pitchDeg = (int) Math.toDegrees(currentPitch);
        int yawDeg = (int) Math.toDegrees(currentYaw);
        String hudTop = String.format("3D CORE // PITCH: %+03d DEG | YAW: %+03d DEG", pitchDeg, yawDeg);
        gh.drawString(hudTop, cx - boxHalfW + 4, cy - boxHalfH - 6);

        String hudBottom = "IMPERION OS // " + getGeometryName() + " [KEY: M]";
        gh.drawString(hudBottom, cx - boxHalfW + 4, cy + boxHalfH + 16);

        gh.dispose();
    }

    private void paintBurstSparks(Graphics2D g2, Theme theme) {
        if (burstSparks.isEmpty()) return;
        for (BurstSpark s : burstSparks) {
            int a = Math.max(0, Math.min(255, (int) (s.alpha * 230)));
            if (a <= 0) continue;
            g2.setColor(new Color(theme.neonAccent.getRed(), theme.neonAccent.getGreen(), theme.neonAccent.getBlue(), a));
            g2.fillOval((int) s.x - 2, (int) s.y - 2, 4, 4);
            int a2 = Math.max(0, Math.min(255, (int)(s.alpha * 180)));
            g2.setColor(new Color(255, 255, 255, a2));
            g2.fillOval((int) s.x - 1, (int) s.y - 1, 2, 2);
        }
    }

    private void paintAudioBaselinePulse(Graphics2D g2, int cx, int cy, int size, Theme theme) {
        int halfW = (int) (size * 0.45);
        int alpha = Math.max(0, Math.min(255, (int) (audioPulseEnergy * 160)));
        g2.setColor(new Color(theme.neonAccent.getRed(), theme.neonAccent.getGreen(), theme.neonAccent.getBlue(), alpha));
        g2.setStroke(new BasicStroke(1.4f));

        Path2D.Double wave = new Path2D.Double();
        wave.moveTo(cx - halfW, cy);
        for (int x = cx - halfW; x <= cx + halfW; x += 6) {
            double normX = (x - cx) / (double) halfW;
            double env = 1.0 - normX * normX;
            double wy = cy + Math.sin(x * 0.12 + animTime * 12.0) * (6.0 * audioPulseEnergy * env);
            wave.lineTo(x, wy);
        }
        g2.draw(wave);
    }

    /**
     * Paints falling hacker digital matrix rain streams.
     */
    private void paintMatrixRain(Graphics2D g2, int w, int h, Theme theme) {
        g2.setFont(theme.fontMonospace.deriveFont(Font.BOLD, 12f));

        for (MatrixStream ms : matrixStreams) {
            for (int i = 0; i < ms.length; i++) {
                int charY = (int) (ms.y - i * 15);
                if (charY < 0 || charY > h) continue;

                if (i == 0) {
                    // Bright glowing head character
                    g2.setColor(new Color(240, 255, 255, 220));
                } else {
                    // Trailing characters fade down
                    int alpha = Math.max(10, 130 - (i * 130 / ms.length));
                    g2.setColor(new Color(theme.neonAccent.getRed(), theme.neonAccent.getGreen(), theme.neonAccent.getBlue(), alpha));
                }
                g2.drawString(String.valueOf(ms.chars[i]), ms.x, charY);
            }
        }
    }

    /**
     * Paints ambient glowing cyber dust particles that react to mouse proximity.
     */
    private void paintCyberParticles(Graphics2D g2, int w, int h, Theme theme) {
        for (CyberParticle p : particles) {
            float alphaPulse = (float) (p.baseAlpha + Math.sin(p.pulse) * 0.15f);
            alphaPulse = Math.max(0.1f, Math.min(0.9f, alphaPulse));
            int alpha = (int) (alphaPulse * 255);

            g2.setColor(new Color(theme.neonAccent.getRed(), theme.neonAccent.getGreen(), theme.neonAccent.getBlue(), alpha));
            g2.fillOval((int) p.x, (int) p.y, (int) p.size, (int) p.size);
        }
    }

    /**
     * Paints interactive concentric holographic shockwaves spawned by clicking.
     */
    private void paintShockwaves(Graphics2D g2, Theme theme) {
        for (Shockwave sw : shockwaves) {
            int alpha = (int) (sw.alpha * 190);
            if (alpha <= 0) continue;

            Color ringCol = new Color(theme.neonAccent.getRed(), theme.neonAccent.getGreen(), theme.neonAccent.getBlue(), alpha);
            g2.setColor(ringCol);
            g2.setStroke(new BasicStroke(1.8f));

            int r = (int) sw.radius;
            g2.drawOval(sw.x - r, sw.y - r, r * 2, r * 2);

            // Secondary faint inner ring
            if (r > 15) {
                int r2 = (int) (r * 0.65f);
                int alpha2 = (int) (alpha * 0.5f);
                g2.setColor(new Color(theme.secondaryAccent.getRed(), theme.secondaryAccent.getGreen(), theme.secondaryAccent.getBlue(), alpha2));
                g2.drawOval(sw.x - r2, sw.y - r2, r2 * 2, r2 * 2);
            }
        }
    }

    /**
     * Subtle horizontal scanlines overlay for high-tech CRT cyberpunk aesthetic.
     */
    private void paintScanlines(Graphics2D g2, int w, int h) {
        Color scanColor = new Color(0, 0, 0, 18);
        g2.setColor(scanColor);
        g2.setStroke(new BasicStroke(1.0f));
        for (int y = 0; y < h; y += 4) {
            g2.drawLine(0, y, w, y);
        }
    }

    // ── Supernova Burst Sparks Entity ────────────────────────────────────
    private static class BurstSpark {
        float x, y;
        float vx, vy;
        float alpha = 1.0f;

        BurstSpark(int x, int y, float vx, float vy) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
        }

        boolean update() {
            x += vx;
            y += vy;
            vx *= 0.93f;
            vy *= 0.93f;
            alpha -= 0.055f;
            return alpha > 0.02f;
        }
    }

    // ── Inner Cyber Entities ──────────────────────────────────────────────

    private static class MatrixStream {
        int x;
        float y;
        float speed;
        int length;
        char[] chars;
        long lastChange;

        MatrixStream(int x, int maxH, Random rnd) {
            this.x = x;
            reset(maxH, rnd, true);
        }

        void reset(int maxH, Random rnd, boolean randomY) {
            y = randomY ? rnd.nextInt(Math.max(100, maxH)) : -rnd.nextInt(150);
            speed = 2.0f + rnd.nextFloat() * 4.0f;
            length = 7 + rnd.nextInt(14);
            chars = new char[length];
            for (int i = 0; i < length; i++) {
                chars[i] = randomGlyph(rnd);
            }
            lastChange = System.currentTimeMillis();
        }

        static char randomGlyph(Random rnd) {
            String charset = "0123456789ABCDEF$#@%&*+-/<>[]{}=";
            return charset.charAt(rnd.nextInt(charset.length()));
        }

        void update(int maxH, Random rnd) {
            y += speed;
            if (System.currentTimeMillis() - lastChange > 140) {
                chars[rnd.nextInt(length)] = randomGlyph(rnd);
                lastChange = System.currentTimeMillis();
            }
            if (y - length * 15 > maxH) {
                reset(maxH, rnd, false);
            }
        }
    }

    private static class CyberParticle {
        float x, y;
        float vx, vy;
        float size;
        float baseAlpha;
        float pulse;

        CyberParticle(int w, int h, Random rnd) {
            x = rnd.nextInt(Math.max(w, 800));
            y = rnd.nextInt(Math.max(h, 600));
            vx = (rnd.nextFloat() - 0.5f) * 0.7f;
            vy = (rnd.nextFloat() - 0.5f) * 0.7f;
            size = 1.5f + rnd.nextFloat() * 2.8f;
            baseAlpha = 0.25f + rnd.nextFloat() * 0.45f;
            pulse = rnd.nextFloat() * (float) Math.PI * 2;
        }

        void update(int w, int h, int mouseX, int mouseY) {
            x += vx;
            y += vy;
            pulse += 0.04f;

            // Interactive repulsion if cursor approaches
            float dx = x - mouseX;
            float dy = y - mouseY;
            float distSq = dx * dx + dy * dy;
            if (distSq < 14000 && distSq > 1) {
                float dist = (float) Math.sqrt(distSq);
                float force = (110 - dist) * 0.04f;
                x += (dx / dist) * force;
                y += (dy / dist) * force;
            }

            if (x < 0) x = w;
            if (x > w) x = 0;
            if (y < 0) y = h;
            if (y > h) y = 0;
        }
    }

    private static class Shockwave {
        int x, y;
        float radius = 0;
        float maxRadius = 300;
        float speed = 6.5f;
        float alpha = 1.0f;

        Shockwave(int x, int y) {
            this.x = x;
            this.y = y;
        }

        boolean update() {
            radius += speed;
            alpha = Math.max(0, 1.0f - (radius / maxRadius));
            return radius < maxRadius;
        }
    }

    private static class CursorTrailParticle {
        float x, y;
        float alpha = 1.0f;
        float size = 5.0f;

        CursorTrailParticle(float x, float y) {
            this.x = x;
            this.y = y;
        }

        boolean update() {
            alpha -= 0.08f;
            size = Math.max(1.0f, size - 0.25f);
            return alpha > 0.04f;
        }
    }

    // ── 3D Projection Engine Helpers & Geometry ──
    public static class Point3D {
        public float x, y, z;
        public Point3D(float x, float y, float z) {
            this.x = x; this.y = y; this.z = z;
        }
    }

    private static class QuantumOrbitalParticle {
        float radiusRatio;
        float theta;
        float tiltX;
        float tiltZ;
        float speed;
        float size;

        QuantumOrbitalParticle(Random rnd) {
            this.radiusRatio = 0.52f + rnd.nextFloat() * 0.38f;
            this.theta = rnd.nextFloat() * (float) (Math.PI * 2);
            this.tiltX = (rnd.nextFloat() - 0.5f) * 1.5f;
            this.tiltZ = (rnd.nextFloat() - 0.5f) * 1.5f;
            this.speed = (0.016f + rnd.nextFloat() * 0.030f) * (rnd.nextBoolean() ? 1 : -1);
            this.size = 2.0f + rnd.nextFloat() * 3.5f;
        }

        void update() {
            theta += speed;
        }
    }

    private Point3D project3D(float x, float y, float z, float pitch, float yaw, float roll, float fov, int cx, int cy) {
        // Roll around Z
        float cosR = (float) Math.cos(roll), sinR = (float) Math.sin(roll);
        float x1 = x * cosR - y * sinR;
        float y1 = x * sinR + y * cosR;
        float z1 = z;

        // Pitch around X
        float cosP = (float) Math.cos(pitch), sinP = (float) Math.sin(pitch);
        float y2 = y1 * cosP - z1 * sinP;
        float z2 = y1 * sinP + z1 * cosP;
        float x2 = x1;

        // Yaw around Y
        float cosY = (float) Math.cos(yaw), sinY = (float) Math.sin(yaw);
        float x3 = x2 * cosY + z2 * sinY;
        float z3 = -x2 * sinY + z2 * cosY;
        float y3 = y2;

        // Perspective division
        float zEff = fov + z3;
        if (zEff < 15f) zEff = 15f;
        float scale = fov / zEff;

        return new Point3D(cx + x3 * scale, cy + y3 * scale, z3);
    }

    private Point3D getRingLocalPoint(float radius, float angle, float tiltX, float tiltZ) {
        float x0 = (float) Math.cos(angle) * radius;
        float y0 = (float) Math.sin(angle) * radius;
        float z0 = 0f;

        // Rotate by tiltX
        float cosX = (float) Math.cos(tiltX), sinX = (float) Math.sin(tiltX);
        float y1 = y0 * cosX - z0 * sinX;
        float z1 = y0 * sinX + z0 * cosX;
        float x1 = x0;

        // Rotate by tiltZ
        float cosZ = (float) Math.cos(tiltZ), sinZ = (float) Math.sin(tiltZ);
        float x2 = x1 * cosZ - y1 * sinZ;
        float y2 = x1 * sinZ + y1 * cosZ;
        float z2 = z1;

        return new Point3D(x2, y2, z2);
    }
}

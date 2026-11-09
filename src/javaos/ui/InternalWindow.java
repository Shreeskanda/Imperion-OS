package javaos.ui;

import javaos.core.JavaOS;
import javaos.core.WindowManager;
import javaos.core.WindowManager.SnapZone;
import javaos.ui.components.CyberpunkIcons;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;

/**
 * Custom draggable, resizable cyberpunk internal window frame.
 * Renders glowing neon borders, titlebars, control buttons, snap assist detection,
 * and multi-workspace assignment without relying on dated Swing L&F frames.
 */
public class InternalWindow extends JPanel {

    private final String id;
    private String title;
    private Icon icon;
    private final JPanel contentPane;
    private final JPanel titleBar;
    private final JLabel titleLabel;
    private final JLabel iconLabel;
    private final JButton btnMinimize;
    private final JButton btnMaximize;
    private final JButton btnClose;

    private boolean isMaximized = false;
    private boolean isMinimized = false;
    private boolean isActive = true;
    private Rectangle normalBounds;

    private int workspaceIndex = 0;
    private SnapZone candidateSnapZone = null;

    private Point dragOffset;
    private int resizeDirection = 0; // 0=none, 1=N, 2=S, 4=W, 8=E
    private static final int BORDER_MARGIN = 6;
    private static final int TITLE_HEIGHT = 32;

    public interface WindowStateListener {
        void onClosed(InternalWindow window);
        void onMinimized(InternalWindow window);
        void onRestored(InternalWindow window);
        void onFocused(InternalWindow window);
    }

    private WindowStateListener stateListener;

    public InternalWindow(String id, String title, Icon icon) {
        this.id = id;
        this.title = title;
        this.icon = icon;
        this.contentPane = new JPanel(new BorderLayout());
        this.titleBar = new JPanel(new BorderLayout(8, 0));
        this.titleLabel = new JLabel(title);
        this.iconLabel = new JLabel(icon);
        this.btnMinimize = new JButton(CyberpunkIcons.createIcon("MINIMIZE", 14));
        this.btnMaximize = new JButton(CyberpunkIcons.createIcon("MAXIMIZE", 14));
        this.btnClose = new JButton(CyberpunkIcons.createIcon("CLOSE", 14));

        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout());
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(BORDER_MARGIN, BORDER_MARGIN, BORDER_MARGIN, BORDER_MARGIN));

        Theme theme = ThemeManager.getInstance().getCurrentTheme();

        // Title bar layout
        titleBar.setOpaque(false);
        titleBar.setPreferredSize(new Dimension(0, TITLE_HEIGHT));
        titleBar.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));

        titleLabel.setFont(theme.fontBold);
        titleLabel.setForeground(theme.textPrimary);

        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        leftPanel.setOpaque(false);
        if (icon != null) {
            leftPanel.add(iconLabel);
        }
        leftPanel.add(titleLabel);
        titleBar.add(leftPanel, BorderLayout.CENTER);

        // Control buttons
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 4));
        controls.setOpaque(false);

        setupWindowButton(btnMinimize, e -> toggleMinimize());
        setupWindowButton(btnMaximize, e -> toggleMaximize());
        setupWindowButton(btnClose, e -> close());

        controls.add(btnMinimize);
        controls.add(btnMaximize);
        controls.add(btnClose);
        titleBar.add(controls, BorderLayout.EAST);

        add(titleBar, BorderLayout.NORTH);

        // Content panel
        contentPane.setOpaque(true);
        contentPane.setBackground(theme.panel);
        add(contentPane, BorderLayout.CENTER);

        // Window drag listeners on titleBar with Snap Assist
        MouseAdapter dragAdapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                focusWindow();
                if (SwingUtilities.isRightMouseButton(e)) {
                    showTitleBarMenu(e.getPoint());
                    return;
                }
                if (isMaximized) return;
                dragOffset = e.getPoint();
                candidateSnapZone = null;
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (isMaximized || dragOffset == null) return;
                Container parent = getParent();
                if (parent == null) return;

                Point p = SwingUtilities.convertPoint(titleBar, e.getPoint(), parent);
                int newX = p.x - dragOffset.x;
                int newY = Math.max(0, p.y - dragOffset.y);
                setLocation(newX, newY);

                // Snap Zone Detection based on cursor proximity to screen edges
                int pw = parent.getWidth();
                int ph = parent.getHeight();

                SnapZone zone = null;
                if (p.x <= 22) {
                    if (p.y <= 50) zone = SnapZone.TOP_LEFT;
                    else if (p.y >= ph - 50) zone = SnapZone.BOTTOM_LEFT;
                    else zone = SnapZone.LEFT;
                } else if (p.x >= pw - 22) {
                    if (p.y <= 50) zone = SnapZone.TOP_RIGHT;
                    else if (p.y >= ph - 50) zone = SnapZone.BOTTOM_RIGHT;
                    else zone = SnapZone.RIGHT;
                } else if (p.y <= 16) {
                    zone = SnapZone.TOP;
                }

                candidateSnapZone = zone;
                if (parent instanceof DesktopSurface) {
                    DesktopSurface ds = (DesktopSurface) parent;
                    if (zone != null) {
                        ds.showSnapGhost(zone.calculateBounds(pw, ph));
                    } else {
                        ds.hideSnapGhost();
                    }
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                Container parent = getParent();
                if (parent instanceof DesktopSurface) {
                    ((DesktopSurface) parent).hideSnapGhost();
                }

                if (candidateSnapZone != null) {
                    if (normalBounds == null) {
                        normalBounds = getBounds();
                    }
                    JavaOS.getInstance().getWindowManager().snapWindow(InternalWindow.this, candidateSnapZone);
                    if (JavaOS.getInstance().getSoundService() != null) {
                        JavaOS.getInstance().getSoundService().playLatch();
                    }
                    candidateSnapZone = null;
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    showTitleBarMenu(e.getPoint());
                } else if (e.getClickCount() == 2) {
                    toggleMaximize();
                }
            }
        };
        titleBar.addMouseListener(dragAdapter);
        titleBar.addMouseMotionListener(dragAdapter);

        // Border resizing listener on window edges
        MouseAdapter resizeAdapter = new MouseAdapter() {
            private Point startPt;
            private Rectangle startBounds;

            @Override
            public void mouseMoved(MouseEvent e) {
                if (isMaximized) {
                    setCursor(Cursor.getDefaultCursor());
                    resizeDirection = 0;
                    return;
                }
                int dir = computeResizeDirection(e.getPoint());
                resizeDirection = dir;
                setCursor(getCursorForDirection(dir));
            }

            @Override
            public void mousePressed(MouseEvent e) {
                focusWindow();
                if (isMaximized || resizeDirection == 0) return;
                startPt = e.getLocationOnScreen();
                startBounds = getBounds();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (isMaximized || resizeDirection == 0 || startPt == null) return;
                Point current = e.getLocationOnScreen();
                int dx = current.x - startPt.x;
                int dy = current.y - startPt.y;

                int x = startBounds.x;
                int y = startBounds.y;
                int w = startBounds.width;
                int h = startBounds.height;

                if ((resizeDirection & 4) != 0) { // W
                    x += dx;
                    w -= dx;
                }
                if ((resizeDirection & 8) != 0) { // E
                    w += dx;
                }
                if ((resizeDirection & 1) != 0) { // N
                    y += dy;
                    h -= dy;
                }
                if ((resizeDirection & 2) != 0) { // S
                    h += dy;
                }

                if (w >= 300 && h >= 200 && y >= 0) {
                    setBounds(x, y, w, h);
                    revalidate();
                }
            }
        };
        addMouseListener(resizeAdapter);
        addMouseMotionListener(resizeAdapter);

        // Click anywhere inside to focus
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                focusWindow();
            }
        });
    }

    private void showTitleBarMenu(Point pt) {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(theme.panel);
        menu.setBorder(BorderFactory.createLineBorder(theme.neonAccent, 1));

        JMenuItem miMax = new JMenuItem(isMaximized ? "Restore Window" : "Maximize Window");
        miMax.addActionListener(e -> toggleMaximize());
        menu.add(miMax);

        JMenuItem miMin = new JMenuItem("Minimize Window");
        miMin.addActionListener(e -> toggleMinimize());
        menu.add(miMin);

        menu.addSeparator();

        // Snap Assist Submenu
        JMenu snapMenu = new JMenu("Snap Window Layout");
        JMenuItem miLeft = new JMenuItem("Snap Left (50%)  [Ctrl+Alt+Left]");
        miLeft.addActionListener(e -> JavaOS.getInstance().getWindowManager().snapWindow(this, SnapZone.LEFT));
        JMenuItem miRight = new JMenuItem("Snap Right (50%) [Ctrl+Alt+Right]");
        miRight.addActionListener(e -> JavaOS.getInstance().getWindowManager().snapWindow(this, SnapZone.RIGHT));
        JMenuItem miTopLeft = new JMenuItem("Top-Left Quadrant (25%)");
        miTopLeft.addActionListener(e -> JavaOS.getInstance().getWindowManager().snapWindow(this, SnapZone.TOP_LEFT));
        JMenuItem miTopRight = new JMenuItem("Top-Right Quadrant (25%)");
        miTopRight.addActionListener(e -> JavaOS.getInstance().getWindowManager().snapWindow(this, SnapZone.TOP_RIGHT));
        JMenuItem miBottomLeft = new JMenuItem("Bottom-Left Quadrant (25%)");
        miBottomLeft.addActionListener(e -> JavaOS.getInstance().getWindowManager().snapWindow(this, SnapZone.BOTTOM_LEFT));
        JMenuItem miBottomRight = new JMenuItem("Bottom-Right Quadrant (25%)");
        miBottomRight.addActionListener(e -> JavaOS.getInstance().getWindowManager().snapWindow(this, SnapZone.BOTTOM_RIGHT));

        snapMenu.add(miLeft);
        snapMenu.add(miRight);
        snapMenu.addSeparator();
        snapMenu.add(miTopLeft);
        snapMenu.add(miTopRight);
        snapMenu.add(miBottomLeft);
        snapMenu.add(miBottomRight);
        menu.add(snapMenu);

        // Workspace Assignment Submenu
        JMenu wsMenu = new JMenu("Move to Workspace");
        for (int i = 0; i < WindowManager.TOTAL_WORKSPACES; i++) {
            final int targetWs = i;
            String name = WindowManager.getWorkspaceName(i);
            JMenuItem wsItem = new JMenuItem((i == workspaceIndex ? "✓ " : "   ") + name);
            wsItem.addActionListener(e -> JavaOS.getInstance().getWindowManager().moveWindowToWorkspace(this, targetWs));
            wsMenu.add(wsItem);
        }
        menu.add(wsMenu);

        menu.addSeparator();

        JMenuItem miClose = new JMenuItem("Close Window");
        miClose.setForeground(theme.error);
        miClose.addActionListener(e -> close());
        menu.add(miClose);

        menu.show(titleBar, pt.x, pt.y);
    }

    private void setupWindowButton(JButton btn, ActionListener action) {
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(24, 22));
        btn.addActionListener(action);
    }

    private int computeResizeDirection(Point p) {
        int dir = 0;
        int w = getWidth();
        int h = getHeight();

        if (p.x < BORDER_MARGIN) dir |= 4; // W
        else if (p.x > w - BORDER_MARGIN) dir |= 8; // E

        if (p.y < BORDER_MARGIN) dir |= 1; // N
        else if (p.y > h - BORDER_MARGIN) dir |= 2; // S

        return dir;
    }

    private Cursor getCursorForDirection(int dir) {
        switch (dir) {
            case 1:
            case 2:
                return Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR);
            case 4:
            case 8:
                return Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR);
            case 1 | 4:
            case 2 | 8:
                return Cursor.getPredefinedCursor(Cursor.NW_RESIZE_CURSOR);
            case 1 | 8:
            case 2 | 4:
                return Cursor.getPredefinedCursor(Cursor.NE_RESIZE_CURSOR);
            default:
                return Cursor.getDefaultCursor();
        }
    }

    public void focusWindow() {
        if (getParent() != null) {
            getParent().setComponentZOrder(this, 0);
            getParent().repaint();
        }
        setActive(true);
        if (stateListener != null) {
            stateListener.onFocused(this);
        }
        if (contentPane != null) {
            contentPane.requestFocusInWindow();
        }
    }

    public void toggleMinimize() {
        if (!isMinimized) {
            isMinimized = true;
            setVisible(false);
            if (JavaOS.getInstance().getSoundService() != null) {
                JavaOS.getInstance().getSoundService().playWhoosh();
            }
            if (stateListener != null) stateListener.onMinimized(this);
        } else {
            restore();
        }
    }

    public void restore() {
        isMinimized = false;
        setVisible(true);
        focusWindow();
        if (JavaOS.getInstance().getSoundService() != null) {
            JavaOS.getInstance().getSoundService().playWhoosh();
        }
        if (stateListener != null) stateListener.onRestored(this);
    }

    public void toggleMaximize() {
        if (!isMaximized) {
            normalBounds = getBounds();
            if (getParent() != null) {
                setBounds(0, 0, getParent().getWidth(), getParent().getHeight());
            }
            isMaximized = true;
            btnMaximize.setIcon(CyberpunkIcons.createIcon("RESTORE", 14));
        } else {
            if (normalBounds != null) {
                setBounds(normalBounds);
            }
            isMaximized = false;
            btnMaximize.setIcon(CyberpunkIcons.createIcon("MAXIMIZE", 14));
        }
        revalidate();
    }

    public void close() {
        if (stateListener != null) {
            stateListener.onClosed(this);
        }
        if (getParent() != null) {
            Container parent = getParent();
            parent.remove(this);
            parent.repaint();
        }
    }

    public int getWorkspaceIndex() {
        return workspaceIndex;
    }

    public void setWorkspaceIndex(int workspaceIndex) {
        this.workspaceIndex = workspaceIndex;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
        this.titleLabel.setText(title);
    }

    public Icon getIcon() {
        return icon;
    }

    public void setIcon(Icon icon) {
        this.icon = icon;
        this.iconLabel.setIcon(icon);
    }

    public JPanel getContentPane() {
        return contentPane;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        this.isActive = active;
        repaint();
    }

    public boolean isMinimized() {
        return isMinimized;
    }

    public boolean isMaximized() {
        return isMaximized;
    }

    public void setStateListener(WindowStateListener stateListener) {
        this.stateListener = stateListener;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        int w = getWidth();
        int h = getHeight();

        // Window background
        g2.setColor(theme.panel);
        g2.fill(new RoundRectangle2D.Float(1, 1, w - 2, h - 2, 10, 10));

        // Window titlebar background
        g2.setColor(theme.windowTitlebar);
        g2.fill(new RoundRectangle2D.Float(1, 1, w - 2, TITLE_HEIGHT + BORDER_MARGIN, 10, 10));

        // Outer glow & border
        Color borderCol = isActive ? theme.neonAccent : theme.secondaryAccent;
        if (isActive) {
            g2.setColor(theme.borderGlow);
            g2.setStroke(new BasicStroke(2.0f));
            g2.draw(new RoundRectangle2D.Float(1, 1, w - 2, h - 2, 10, 10));
        }

        g2.setColor(borderCol);
        g2.setStroke(new BasicStroke(1.2f));
        g2.draw(new RoundRectangle2D.Float(1.5f, 1.5f, w - 3, h - 3, 10, 10));

        // Line separator under titlebar
        g2.setColor(new Color(borderCol.getRed(), borderCol.getGreen(), borderCol.getBlue(), 80));
        g2.drawLine(1, TITLE_HEIGHT + BORDER_MARGIN, w - 2, TITLE_HEIGHT + BORDER_MARGIN);

        g2.dispose();
        super.paintComponent(g);
    }
}

package javaos.core;

import javaos.ui.InternalWindow;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Coordinates internal desktop windows, z-ordering, multi-monitor tiling,
 * edge snap assist zones, and 4 virtual workspaces.
 */
public class WindowManager {

    public enum SnapZone {
        LEFT,
        RIGHT,
        TOP,
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT;

        public Rectangle calculateBounds(int containerW, int containerH) {
            int halfW = containerW / 2;
            int halfH = containerH / 2;
            switch (this) {
                case LEFT:
                    return new Rectangle(0, 0, halfW, containerH);
                case RIGHT:
                    return new Rectangle(halfW, 0, halfW, containerH);
                case TOP:
                    return new Rectangle(0, 0, containerW, containerH);
                case TOP_LEFT:
                    return new Rectangle(0, 0, halfW, halfH);
                case TOP_RIGHT:
                    return new Rectangle(halfW, 0, halfW, halfH);
                case BOTTOM_LEFT:
                    return new Rectangle(0, halfH, halfW, halfH);
                case BOTTOM_RIGHT:
                    return new Rectangle(halfW, halfH, halfW, halfH);
                default:
                    return new Rectangle(0, 0, containerW, containerH);
            }
        }
    }

    private JComponent desktopContainer;
    private final List<InternalWindow> windows = new CopyOnWriteArrayList<>();
    private InternalWindow activeWindow;
    private int cascadeOffset = 30;
    private final List<Runnable> changeListeners = new CopyOnWriteArrayList<>();

    // Multi-Workspace state (0=General, 1=Office, 2=Dev & Terminal, 3=Media & Games)
    private int currentWorkspace = 0;
    public static final int TOTAL_WORKSPACES = 4;
    private static final String[] WORKSPACE_NAMES = {
            "General",
            "Office",
            "Dev & Shell",
            "Media & Arcade"
    };

    public void setDesktopContainer(JComponent container) {
        this.desktopContainer = container;
    }

    public JComponent getDesktopContainer() {
        return desktopContainer;
    }

    public synchronized void addWindow(InternalWindow window, int preferredWidth, int preferredHeight) {
        if (desktopContainer == null) return;

        window.setWorkspaceIndex(currentWorkspace);
        windows.add(window);

        int containerW = desktopContainer.getWidth() > 0 ? desktopContainer.getWidth() : 1024;
        int containerH = desktopContainer.getHeight() > 0 ? desktopContainer.getHeight() : 700;

        int w = Math.min(preferredWidth, containerW - 40);
        int h = Math.min(preferredHeight, containerH - 40);

        int count = getWindowsInCurrentWorkspace().size();
        int x = 40 + ((count - 1) * cascadeOffset) % Math.max(100, containerW - w - 60);
        int y = 40 + ((count - 1) * cascadeOffset) % Math.max(100, containerH - h - 60);

        window.setBounds(x, y, w, h);
        desktopContainer.add(window);
        desktopContainer.setComponentZOrder(window, 0);

        setActiveWindow(window);
        desktopContainer.revalidate();
        desktopContainer.repaint();

        notifyChange();
    }

    public synchronized void removeWindow(InternalWindow window) {
        windows.remove(window);
        if (desktopContainer != null) {
            desktopContainer.remove(window);
            desktopContainer.repaint();
        }

        if (activeWindow == window) {
            activeWindow = null;
            List<InternalWindow> curWs = getWindowsInCurrentWorkspace();
            if (!curWs.isEmpty()) {
                setActiveWindow(curWs.get(curWs.size() - 1));
            }
        }

        notifyChange();
    }

    public synchronized void setActiveWindow(InternalWindow window) {
        if (activeWindow != null && activeWindow != window) {
            activeWindow.setActive(false);
        }
        this.activeWindow = window;
        if (window != null) {
            window.setActive(true);
            if (desktopContainer != null && window.getParent() == desktopContainer) {
                desktopContainer.setComponentZOrder(window, 0);
                desktopContainer.repaint();
            }
        }
        notifyChange();
    }

    public synchronized InternalWindow getActiveWindow() {
        return activeWindow;
    }

    public List<InternalWindow> getWindows() {
        return Collections.unmodifiableList(new ArrayList<>(windows));
    }

    public List<InternalWindow> getWindowsInCurrentWorkspace() {
        return getWindowsInWorkspace(currentWorkspace);
    }

    public List<InternalWindow> getWindowsInWorkspace(int ws) {
        List<InternalWindow> res = new ArrayList<>();
        for (InternalWindow w : windows) {
            if (w.getWorkspaceIndex() == ws) {
                res.add(w);
            }
        }
        return res;
    }

    public int getCurrentWorkspace() {
        return currentWorkspace;
    }

    public static String getWorkspaceName(int index) {
        if (index >= 0 && index < WORKSPACE_NAMES.length) return WORKSPACE_NAMES[index];
        return "Workspace " + (index + 1);
    }

    public synchronized void switchWorkspace(int targetWorkspace) {
        if (targetWorkspace < 0 || targetWorkspace >= TOTAL_WORKSPACES || targetWorkspace == currentWorkspace) return;
        this.currentWorkspace = targetWorkspace;

        for (InternalWindow w : windows) {
            boolean belongs = (w.getWorkspaceIndex() == currentWorkspace);
            w.setVisible(belongs && !w.isMinimized());
        }

        List<InternalWindow> curWs = getWindowsInCurrentWorkspace();
        if (!curWs.isEmpty()) {
            setActiveWindow(curWs.get(curWs.size() - 1));
        } else {
            setActiveWindow(null);
        }

        if (desktopContainer != null) {
            desktopContainer.repaint();
        }
        notifyChange();
    }

    public synchronized void moveWindowToWorkspace(InternalWindow window, int targetWorkspace) {
        if (window == null || targetWorkspace < 0 || targetWorkspace >= TOTAL_WORKSPACES) return;
        window.setWorkspaceIndex(targetWorkspace);
        boolean belongs = (targetWorkspace == currentWorkspace);
        window.setVisible(belongs && !window.isMinimized());

        if (!belongs && activeWindow == window) {
            activeWindow = null;
            List<InternalWindow> curWs = getWindowsInCurrentWorkspace();
            if (!curWs.isEmpty()) {
                setActiveWindow(curWs.get(curWs.size() - 1));
            }
        }
        if (desktopContainer != null) {
            desktopContainer.repaint();
        }
        notifyChange();
    }

    public synchronized void minimizeAll() {
        for (InternalWindow w : getWindowsInCurrentWorkspace()) {
            if (!w.isMinimized()) {
                w.toggleMinimize();
            }
        }
    }

    public synchronized void cascadeWindows() {
        if (desktopContainer == null) return;
        int x = 30;
        int y = 30;
        for (InternalWindow w : getWindowsInCurrentWorkspace()) {
            if (w.isMinimized()) w.restore();
            w.setBounds(x, y, Math.min(750, desktopContainer.getWidth() - 60), Math.min(480, desktopContainer.getHeight() - 60));
            x += 35;
            y += 35;
        }
        desktopContainer.repaint();
    }

    // ── SNAP ASSIST & TILING ENGINE ──────────────────────────────────────────

    public synchronized void snapWindow(InternalWindow w, SnapZone zone) {
        if (w == null || desktopContainer == null) return;
        if (w.isMinimized()) w.restore();

        int containerW = desktopContainer.getWidth();
        int containerH = desktopContainer.getHeight();
        Rectangle bounds = zone.calculateBounds(containerW, containerH);

        w.setBounds(bounds);
        desktopContainer.repaint();
    }

    public synchronized void snapActiveLeft() {
        if (activeWindow != null) snapWindow(activeWindow, SnapZone.LEFT);
    }

    public synchronized void snapActiveRight() {
        if (activeWindow != null) snapWindow(activeWindow, SnapZone.RIGHT);
    }

    public synchronized void snapActiveMaximize() {
        if (activeWindow != null) {
            if (activeWindow.isMaximized()) {
                activeWindow.toggleMaximize();
            } else {
                activeWindow.toggleMaximize();
            }
        }
    }

    public synchronized void tileSideBySide() {
        List<InternalWindow> cur = getWindowsInCurrentWorkspace();
        if (desktopContainer == null || cur.size() < 2) return;

        InternalWindow w1 = cur.get(cur.size() - 1);
        InternalWindow w2 = cur.get(cur.size() - 2);

        snapWindow(w1, SnapZone.LEFT);
        snapWindow(w2, SnapZone.RIGHT);
    }

    public synchronized void tile4Grid() {
        List<InternalWindow> cur = getWindowsInCurrentWorkspace();
        if (desktopContainer == null || cur.isEmpty()) return;

        SnapZone[] zones = {SnapZone.TOP_LEFT, SnapZone.TOP_RIGHT, SnapZone.BOTTOM_LEFT, SnapZone.BOTTOM_RIGHT};
        int limit = Math.min(cur.size(), 4);
        for (int i = 0; i < limit; i++) {
            snapWindow(cur.get(i), zones[i]);
        }
    }

    public void addChangeListener(Runnable r) {
        changeListeners.add(r);
    }

    public void removeChangeListener(Runnable r) {
        changeListeners.remove(r);
    }

    private void notifyChange() {
        for (Runnable r : changeListeners) {
            try {
                r.run();
            } catch (Exception ignored) {}
        }
    }
}

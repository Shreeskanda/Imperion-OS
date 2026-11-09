package javaos.ui;

import javaos.core.JavaOS;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Rectangle2D;

/**
 * Top-level window hosting the JavaOS desktop shell.
 */
public class DesktopFrame extends JFrame {

    private final JavaOS os;
    private final CardLayout cardLayout;
    private final JPanel rootContainer;
    private final DesktopSurface desktopSurface;
    private final Taskbar taskbar;
    private final LockScreen lockScreen;
    private final StartMenu startMenu;
    private final SpotlightDialog spotlightDialog;
    private ClipboardHistoryDialog clipboardDialog;
    private MissionControlOverlay missionControlOverlay;
    private ControlCenterDialog controlCenterDialog;

    public static final String CARD_DESKTOP = "desktop";
    public static final String CARD_LOCK = "lock";

    public DesktopFrame(JavaOS os) {
        super("IMPERION OS");
        this.os = os;

        setUndecorated(true);                            // Remove OS title bar entirely
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

        // Borderless maximized full-screen workspace that covers the entire display
        // and does NOT minimize back to Windows when focus changes
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        setBounds(0, 0, screen.width, screen.height);
        setExtendedState(JFrame.MAXIMIZED_BOTH);

        // Keep IMPERION OS locked on screen — prevent accidental minimization to Windows
        addWindowStateListener(e -> {
            if ((e.getNewState() & Frame.ICONIFIED) != 0) {
                SwingUtilities.invokeLater(() -> {
                    setExtendedState(JFrame.MAXIMIZED_BOTH);
                    toFront();
                    requestFocus();
                });
            }
        });

        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        setIconImage(null); // Swing default or painted icon

        cardLayout = new CardLayout();
        rootContainer = new JPanel(cardLayout);

        // Desktop Card
        JPanel desktopCard = new JPanel(new BorderLayout());
        desktopSurface = new DesktopSurface(os);
        taskbar = new Taskbar(os);
        desktopCard.add(desktopSurface, BorderLayout.CENTER);
        desktopCard.add(taskbar, BorderLayout.SOUTH);

        // Lock Card
        lockScreen = new LockScreen(os);

        rootContainer.add(desktopCard, CARD_DESKTOP);
        rootContainer.add(lockScreen, CARD_LOCK);

        setContentPane(rootContainer);

        startMenu = new StartMenu(os);
        spotlightDialog = new SpotlightDialog(this, os);

        // Global hotkey dispatch:
        // Alt+Space -> Spotlight
        // Win+V / Ctrl+Shift+V / Ctrl+Alt+V -> Clipboard History
        // Ctrl+Alt+Left/Right/Up/Down -> Window Snap Assist
        // Ctrl+Alt+1..4 -> Workspace Switching
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
            if (e.getID() == KeyEvent.KEY_PRESSED) {
                int mod = e.getModifiersEx();
                boolean isCtrlAlt = (mod & (KeyEvent.CTRL_DOWN_MASK | KeyEvent.ALT_DOWN_MASK)) == (KeyEvent.CTRL_DOWN_MASK | KeyEvent.ALT_DOWN_MASK);
                boolean isCtrlShift = (mod & (KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK)) == (KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK);

                // Spotlight (Alt+Space or Ctrl+Space)
                if (e.getKeyCode() == KeyEvent.VK_SPACE && ((mod & KeyEvent.ALT_DOWN_MASK) != 0 || (mod & KeyEvent.CTRL_DOWN_MASK) != 0)) {
                    SwingUtilities.invokeLater(spotlightDialog::showSpotlight);
                    return true;
                }

                // Mission Control / Exposé (Win+Tab or Ctrl+Alt+Tab)
                if (e.getKeyCode() == KeyEvent.VK_TAB && (isCtrlAlt || (mod & KeyEvent.META_DOWN_MASK) != 0)) {
                    SwingUtilities.invokeLater(this::showMissionControl);
                    return true;
                }

                // Control Center Drawer (Ctrl+Alt+C)
                if (e.getKeyCode() == KeyEvent.VK_C && isCtrlAlt) {
                    SwingUtilities.invokeLater(this::showControlCenter);
                    return true;
                }

                // Clipboard History (Ctrl+Shift+V or Ctrl+Alt+V)
                if (e.getKeyCode() == KeyEvent.VK_V && (isCtrlAlt || isCtrlShift)) {
                    SwingUtilities.invokeLater(this::showClipboardHistory);
                    return true;
                }

                // Window Snap Assist Shortcuts
                if (isCtrlAlt) {
                    if (e.getKeyCode() == KeyEvent.VK_LEFT) {
                        SwingUtilities.invokeLater(() -> os.getWindowManager().snapActiveLeft());
                        return true;
                    }
                    if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
                        SwingUtilities.invokeLater(() -> os.getWindowManager().snapActiveRight());
                        return true;
                    }
                    if (e.getKeyCode() == KeyEvent.VK_UP) {
                        SwingUtilities.invokeLater(() -> os.getWindowManager().snapActiveMaximize());
                        return true;
                    }
                    if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                        SwingUtilities.invokeLater(() -> {
                            InternalWindow w = os.getWindowManager().getActiveWindow();
                            if (w != null && w.isMaximized()) w.toggleMaximize();
                        });
                        return true;
                    }

                    // Workspace Switching (Ctrl+Alt+1..4)
                    if (e.getKeyCode() >= KeyEvent.VK_1 && e.getKeyCode() <= KeyEvent.VK_4) {
                        final int ws = e.getKeyCode() - KeyEvent.VK_1;
                        SwingUtilities.invokeLater(() -> {
                            os.getWindowManager().switchWorkspace(ws);
                            if (os.getSoundService() != null) os.getSoundService().playSlideSwitch();
                        });
                        return true;
                    }
                }
            }
            return false;
        });

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                os.requestShutdown();
            }
        });
    }

    public DesktopSurface getDesktopSurface() {
        return desktopSurface;
    }

    public Taskbar getTaskbar() {
        return taskbar;
    }

    public StartMenu getStartMenu() {
        return startMenu;
    }

    public SpotlightDialog getSpotlightDialog() {
        return spotlightDialog;
    }

    public void showClipboardHistory() {
        if (clipboardDialog == null) {
            clipboardDialog = new ClipboardHistoryDialog(this, os);
        }
        clipboardDialog.showDialog();
    }

    public void showMissionControl() {
        if (missionControlOverlay == null) {
            missionControlOverlay = new MissionControlOverlay(this, os);
        }
        missionControlOverlay.showOverlay();
    }

    public void showControlCenter() {
        if (controlCenterDialog == null) {
            controlCenterDialog = new ControlCenterDialog(this, os);
        }
        controlCenterDialog.showDrawer();
    }

    public void showDesktopView() {
        cardLayout.show(rootContainer, CARD_DESKTOP);
        desktopSurface.revalidate();
        desktopSurface.repaint();
    }

    public void showLockView() {
        cardLayout.show(rootContainer, CARD_LOCK);
        lockScreen.onShow();
        lockScreen.revalidate();
        lockScreen.repaint();
    }

    public void lock() {
        showLockView();
    }
}

package javaos.core;

import javaos.apps.*;
import javaos.services.SettingsService;
import javaos.services.SystemMetricsService;
import javaos.services.VictusProcessService;
import javaos.services.VirtualFileService;
import javaos.ui.DesktopFrame;
import javaos.ui.ThemeManager;
import javaos.util.OSLogger;

import javax.swing.*;

/**
 * Core operating system orchestrator and singleton for JavaOS.
 * Connects subsystem services, UI managers, and application lifecycles.
 */
public class JavaOS {

    private static JavaOS instance;

    private final SettingsService settingsService;
    private final VirtualFileService fileService;
    private final SystemMetricsService systemMetricsService;
    private final VictusProcessService victusService;
    private final NotificationManager notificationManager;
    private final WindowManager windowManager;
    private final ApplicationManager applicationManager;
    private final javaos.services.SecurityService securityService;
    private final javaos.services.SoundService soundService;
    private final javaos.services.ClipboardHistoryService clipboardService;
    private final javaos.services.NetworkScannerService networkScannerService;

    private DesktopFrame desktopFrame;

    public JavaOS() {
        OSLogger.info("Booting JavaOS Neon Desktop Subsystems...");

        this.settingsService = new SettingsService();
        this.fileService = new VirtualFileService();
        this.systemMetricsService = new SystemMetricsService();
        this.victusService = new VictusProcessService(settingsService);
        this.notificationManager = new NotificationManager();
        this.windowManager = new WindowManager();
        this.applicationManager = new ApplicationManager(this);
        this.securityService = new javaos.services.SecurityService(this);
        this.soundService = new javaos.services.SoundService(this);
        this.clipboardService = new javaos.services.ClipboardHistoryService();
        this.networkScannerService = new javaos.services.NetworkScannerService();

        // Apply saved theme
        ThemeManager.getInstance().applyTheme(settingsService.getTheme());

        // Register built-in applications
        registerBuiltInApplications();
    }

    public static synchronized JavaOS getInstance() {
        if (instance == null) {
            instance = new JavaOS();
        }
        return instance;
    }

    private void registerBuiltInApplications() {
        applicationManager.registerApp(new AppDescriptor(
                "files", "File Manager", "Sandboxed virtual home explorer", AppDescriptor.Category.UTILITIES,
                javaos.ui.components.CyberpunkIcons.createIcon("FILES", 20), false), FileManagerApp::new);

        applicationManager.registerApp(new AppDescriptor(
                "notepad", "Notepad", "Multi-document text editor", AppDescriptor.Category.PRODUCTIVITY,
                javaos.ui.components.CyberpunkIcons.createIcon("NOTEPAD", 20), false), NotepadApp::new);

        applicationManager.registerApp(new AppDescriptor(
                "calculator", "Calculator", "Futuristic arithmetic calculator", AppDescriptor.Category.UTILITIES,
                javaos.ui.components.CyberpunkIcons.createIcon("CALCULATOR", 20), false), CalculatorApp::new);

        applicationManager.registerApp(new AppDescriptor(
                "terminal", "Terminal", "Sandboxed command-line shell", AppDescriptor.Category.UTILITIES,
                javaos.ui.components.CyberpunkIcons.createIcon("TERMINAL", 20), false), TerminalApp::new);

        applicationManager.registerApp(new AppDescriptor(
                "monitor", "System Monitor", "Live JVM and host telemetry dashboard", AppDescriptor.Category.SYSTEM,
                javaos.ui.components.CyberpunkIcons.createIcon("MONITOR", 20), true), SystemMonitorApp::new);

        applicationManager.registerApp(new AppDescriptor(
                "calendar", "Calendar & Clock", "Interactive monthly calendar and clock", AppDescriptor.Category.PRODUCTIVITY,
                javaos.ui.components.CyberpunkIcons.createIcon("CALENDAR", 20), false), CalendarApp::new);

        applicationManager.registerApp(new AppDescriptor(
                "settings", "Settings", "Themes, wallpapers, and Victus paths", AppDescriptor.Category.SYSTEM,
                javaos.ui.components.CyberpunkIcons.createIcon("SETTINGS", 20), true), SettingsApp::new);

        applicationManager.registerApp(new AppDescriptor(
                "victus", "Victus AI Assistant", "Subprocess bridge for external Python assistant", AppDescriptor.Category.AI,
                javaos.ui.components.CyberpunkIcons.createIcon("VICTUS", 20), true), VictusLauncherApp::new);

        applicationManager.registerApp(new AppDescriptor(
                "about", "About IMPERION OS", "System information and platform credits", AppDescriptor.Category.SYSTEM,
                javaos.ui.components.CyberpunkIcons.createIcon("ABOUT", 20), true), AboutApp::new);

        applicationManager.registerApp(new AppDescriptor(
                "realfiles", "Real File Explorer", "Browse actual system drives and files", AppDescriptor.Category.UTILITIES,
                javaos.ui.components.CyberpunkIcons.createIcon("REALFILES", 20), false), RealFileManagerApp::new);

        applicationManager.registerApp(new AppDescriptor(
                "security", "Security Scanner", "Threat detection, process audit & hardening advisor", AppDescriptor.Category.SYSTEM,
                javaos.ui.components.CyberpunkIcons.createIcon("SHIELD", 20), true), CyberSecurityApp::new);

        applicationManager.registerApp(new AppDescriptor(
                "browser", "NetRunner Browser", "Futuristic web navigator & dev inspector", AppDescriptor.Category.UTILITIES,
                javaos.ui.components.CyberpunkIcons.createIcon("BROWSER", 20), false), WebBrowserApp::new);

        applicationManager.registerApp(new AppDescriptor(
                "imageviewer", "Image Viewer", "Hardware-accelerated image & graphics viewer", AppDescriptor.Category.UTILITIES,
                javaos.ui.components.CyberpunkIcons.createIcon("IMAGE", 20), false), ImageViewerApp::new);

        applicationManager.registerApp(new AppDescriptor(
                "docviewer", "Document Viewer", "Native PDF, PowerPoint & Word document explorer", AppDescriptor.Category.UTILITIES,
                javaos.ui.components.CyberpunkIcons.createIcon("PDF", 20), false), DocumentViewerApp::new);

        applicationManager.registerApp(new AppDescriptor(
                "wordeditor", "Word Processor", "Visual Word & .docx rich text document editor", AppDescriptor.Category.PRODUCTIVITY,
                javaos.ui.components.CyberpunkIcons.createIcon("WORD", 20), false), WordEditorApp::new);

        applicationManager.registerApp(new AppDescriptor(
                "slides", "Presentation Slides", "Interactive PowerPoint & .pptx presentation editor and slideshow", AppDescriptor.Category.PRODUCTIVITY,
                javaos.ui.components.CyberpunkIcons.createIcon("PRESENTATION", 20), false), PresentationEditorApp::new);

        applicationManager.registerApp(new AppDescriptor(
                "sheet", "Spreadsheet Analytics", "Spreadsheet, formula engine & neon chart generator (.xlsx / .csv)", AppDescriptor.Category.PRODUCTIVITY,
                javaos.ui.components.CyberpunkIcons.createIcon("SHEET", 20), false), javaos.apps.SpreadsheetApp::new);

        applicationManager.registerApp(new AppDescriptor(
                "player", "NeonWave Media Player", "Procedural synthwave radio, audio player & live dancing visualizer", AppDescriptor.Category.MEDIA,
                javaos.ui.components.CyberpunkIcons.createIcon("MUSIC", 20), false), javaos.apps.MediaPlayerApp::new);

        applicationManager.registerApp(new AppDescriptor(
                "arcade", "Arcade Studio", "Retro Breach Protocol & Tron Light Cycle arcade", AppDescriptor.Category.GAMES,
                javaos.ui.components.CyberpunkIcons.createIcon("GAMEPAD", 20), false), javaos.apps.ArcadeApp::new);

        applicationManager.registerApp(new AppDescriptor(
                "paint", "Paint Studio", "Digital graphics canvas, neon brushes & image filters", AppDescriptor.Category.MEDIA,
                javaos.ui.components.CyberpunkIcons.createIcon("PAINT", 20), false), javaos.apps.PaintApp::new);

        applicationManager.registerApp(new AppDescriptor(
                "cyberdeck", "NetRunner Deck", "Subnet port scanner, packet waterfall & network radar", AppDescriptor.Category.SYSTEM,
                javaos.ui.components.CyberpunkIcons.createIcon("RADAR", 20), false), javaos.apps.CyberDeckApp::new);

        applicationManager.registerApp(new AppDescriptor(
                "stickynotes", "Neon Sticky Notes", "Quick desktop notes, task reminders & memory scratchpad", AppDescriptor.Category.PRODUCTIVITY,
                javaos.ui.components.CyberpunkIcons.createIcon("STICKY", 20), false), javaos.apps.StickyNotesApp::new);
    }

    public void launchUI() {
        SwingUtilities.invokeLater(() -> {
            try {
                desktopFrame = new DesktopFrame(this);
                windowManager.setDesktopContainer(desktopFrame.getDesktopSurface());

                // Wire toast alerts to desktop surface overlay
                notificationManager.addToastListener(msg -> {
                    SwingUtilities.invokeLater(() -> desktopFrame.getDesktopSurface().showToast(msg));
                });

                // Set configured wallpaper
                desktopFrame.getDesktopSurface().setWallpaperStyle(settingsService.getWallpaper());

                // Populate desktop shortcuts
                desktopFrame.getDesktopSurface().initShortcuts();

                desktopFrame.setVisible(true);

                // Show lock screen if lock or face biometrics active; otherwise go to desktop
                if (securityService.isLockActive() || securityService.isFaceRecognitionEnabled()) {
                    desktopFrame.showLockView();
                } else {
                    desktopFrame.showDesktopView();
                }

                notificationManager.postNotification(
                        "IMPERION OS Ready", "Neon Cyberpunk Desktop Shell Initialized.", javaos.models.NotificationMessage.Level.INFO);

                // Post helpful Victus detection notice
                String script = victusService.resolveScriptPath();
                if (script != null && java.nio.file.Files.exists(java.nio.file.Paths.get(script))) {
                    notificationManager.postNotification(
                            "Victus Assistant Detected", "Found: " + script, javaos.models.NotificationMessage.Level.SUCCESS);
                } else {
                    notificationManager.postNotification(
                            "Victus Path Unset", "Configure script path in JavaOS Settings.", javaos.models.NotificationMessage.Level.WARNING);
                }

            } catch (Exception e) {
                OSLogger.error("Fatal error in JavaOS launchUI: " + e.getMessage(), e);
                JOptionPane.showMessageDialog(null, "JavaOS startup failed:\n" + e.getMessage(), "Startup Error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    public void toggleStartMenu() {
        if (desktopFrame != null && desktopFrame.getStartMenu() != null) {
            if (desktopFrame.getStartMenu().isVisible()) {
                desktopFrame.getStartMenu().setVisible(false);
            } else {
                desktopFrame.getStartMenu().showAbove(desktopFrame.getTaskbar());
            }
        }
    }

    public void lockScreen() {
        if (desktopFrame != null) {
            desktopFrame.showLockView();
            OSLogger.info("JavaOS session locked by operator.");
        }
    }

    public void unlockScreen() {
        if (desktopFrame != null) {
            desktopFrame.showDesktopView();
            OSLogger.info("JavaOS session unlocked.");
        }
    }

    public void refreshDesktop() {
        if (desktopFrame != null) {
            desktopFrame.getDesktopSurface().initShortcuts();
            desktopFrame.getDesktopSurface().repaint();
            desktopFrame.getTaskbar().refreshRunningTasks();
            notificationManager.postNotification("Desktop Refreshed", "Icons and surfaces reloaded.", javaos.models.NotificationMessage.Level.INFO);
        }
    }

    public void requestShutdown() {
        int opt = JOptionPane.showConfirmDialog(
                desktopFrame,
                "Are you sure you want to shut down IMPERION OS?",
                "Shutdown IMPERION OS",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );

        if (opt != JOptionPane.YES_OPTION) {
            return;
        }

        // Check if Victus was launched by IMPERION OS
        if (victusService.isRunning()) {
            int killVictus = JOptionPane.showConfirmDialog(
                    desktopFrame,
                    "Victus Python Assistant is currently running.\nDo you want to terminate the Victus process as well?",
                    "Running External Subprocess",
                    JOptionPane.YES_NO_CANCEL_OPTION,
                    JOptionPane.QUESTION_MESSAGE
            );

            if (killVictus == JOptionPane.CANCEL_OPTION) {
                return;
            }
            if (killVictus == JOptionPane.YES_OPTION) {
                victusService.terminate(false);
            }
        }

        // Close all applications gracefully
        for (OSApplication app : applicationManager.getRunningApps()) {
            applicationManager.closeApp(app);
        }

        settingsService.save();
        OSLogger.info("IMPERION OS clean shutdown completed.");

        if (desktopFrame != null) {
            desktopFrame.dispose();
        }
        System.exit(0);
    }

    public SettingsService getSettingsService() {
        return settingsService;
    }

    public VirtualFileService getFileService() {
        return fileService;
    }

    public SystemMetricsService getSystemMetricsService() {
        return systemMetricsService;
    }

    public VictusProcessService getVictusService() {
        return victusService;
    }

    public NotificationManager getNotificationManager() {
        return notificationManager;
    }

    public WindowManager getWindowManager() {
        return windowManager;
    }

    public ApplicationManager getApplicationManager() {
        return applicationManager;
    }

    public javaos.services.SecurityService getSecurityService() {
        return securityService;
    }

    public javaos.services.SoundService getSoundService() {
        return soundService;
    }

    public javaos.services.ClipboardHistoryService getClipboardService() {
        return clipboardService;
    }

    public javaos.services.NetworkScannerService getNetworkScannerService() {
        return networkScannerService;
    }

    public DesktopFrame getDesktopFrame() {
        return desktopFrame;
    }
}

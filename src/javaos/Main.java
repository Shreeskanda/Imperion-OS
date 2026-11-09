package javaos;

import javaos.core.JavaOS;
import javaos.ui.SplashScreen;
import javaos.util.OSLogger;

import javax.swing.*;

/**
 * Main application entry point for JavaOS — Neon Desktop Operating System Simulator.
 */
public class Main {

    public static void main(String[] args) {
        // High-DPI and Anti-Aliasing configuration for crisp rendering
        System.setProperty("sun.java2d.uiScale.enabled", "true");
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");

        // Global uncaught exception handler
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            OSLogger.error("Uncaught exception on thread " + thread.getName() + ": " + throwable.getMessage(), throwable);
        });

        // Initialize Look and Feel
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {}

        OSLogger.info("Starting IMPERION OS — Cyberpunk Desktop Environment");

        // Show splash screen, then launch IMPERION OS on completion
        SwingUtilities.invokeLater(() -> {
            try {
                SplashScreen splash = new SplashScreen(() -> {
                    SwingUtilities.invokeLater(() -> {
                        try {
                            JavaOS os = JavaOS.getInstance();
                            os.launchUI();
                        } catch (Exception e) {
                            OSLogger.error("Fatal startup error in IMPERION OS: " + e.getMessage(), e);
                            JOptionPane.showMessageDialog(null,
                                    "Failed to initialize IMPERION OS:\n" + e.getMessage(),
                                    "Startup Failure", JOptionPane.ERROR_MESSAGE);
                        }
                    });
                });
                splash.start();
            } catch (Exception e) {
                OSLogger.error("Failed to show splash screen: " + e.getMessage(), e);
                // Fallback: launch directly
                try {
                    JavaOS os = JavaOS.getInstance();
                    os.launchUI();
                } catch (Exception ex) {
                    OSLogger.error("Fatal startup error: " + ex.getMessage(), ex);
                }
            }
        });
    }
}

package javaos.ui;

import javaos.services.SettingsService;
import javaos.util.OSLogger;

import javax.swing.*;
import java.awt.Color;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Centralized theme manager that coordinates the visual palette across all JavaOS UI components.
 */
public class ThemeManager {

    private static ThemeManager instance;
    private final Map<String, Theme> registeredThemes = new LinkedHashMap<>();
    private Theme currentTheme;
    private final List<ThemeChangeListener> listeners = new CopyOnWriteArrayList<>();

    public interface ThemeChangeListener {
        void onThemeChanged(Theme newTheme);
    }

    private ThemeManager() {
        registerTheme(Theme.createCyberpunkCyan());
        registerTheme(Theme.createSynthwaveViolet());
        registerTheme(Theme.createMatrixGreen());
        registerTheme(Theme.createSolarAmber());
        registerTheme(Theme.createCrimsonProtocol());
        registerTheme(Theme.createMidnightOLED());
        registerTheme(Theme.createTokyoSakura());
        currentTheme = registeredThemes.get("Cyberpunk Cyan");
    }

    public static synchronized ThemeManager getInstance() {
        if (instance == null) {
            instance = new ThemeManager();
        }
        return instance;
    }

    public void registerTheme(Theme theme) {
        registeredThemes.put(theme.getName(), theme);
    }

    public List<Theme> getAvailableThemes() {
        return new ArrayList<>(registeredThemes.values());
    }

    public Theme getCurrentTheme() {
        return currentTheme;
    }

    public synchronized void applyTheme(String themeName) {
        Theme theme = registeredThemes.get(themeName);
        if (theme == null) {
            theme = registeredThemes.get("Cyberpunk Cyan");
        }
        this.currentTheme = theme;
        configureSwingDefaults(theme);
        for (ThemeChangeListener l : listeners) {
            try {
                l.onThemeChanged(theme);
            } catch (Exception e) {
                OSLogger.error("Error in theme change listener: " + e.getMessage());
            }
        }
        OSLogger.info("Applied theme: " + theme.getName());
    }

    public void addListener(ThemeChangeListener listener) {
        listeners.add(listener);
    }

    public void removeListener(ThemeChangeListener listener) {
        listeners.remove(listener);
    }

    public static void configureSwingDefaults(Theme theme) {
        try {
            UIManager.put("Panel.background", theme.panel);
            UIManager.put("Panel.foreground", theme.textPrimary);
            UIManager.put("OptionPane.background", theme.panel);
            UIManager.put("OptionPane.messageForeground", theme.textPrimary);
            UIManager.put("ToolTip.background", theme.elevatedPanel);
            UIManager.put("ToolTip.foreground", theme.textPrimary);
            UIManager.put("ToolTip.border", BorderFactory.createLineBorder(theme.neonAccent));
            UIManager.put("ScrollBar.thumb", theme.neonAccent);
            UIManager.put("ScrollBar.track", theme.panel);
        } catch (Exception ignored) {}
    }
}

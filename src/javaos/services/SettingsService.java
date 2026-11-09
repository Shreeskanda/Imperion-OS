package javaos.services;

import javaos.util.OSLogger;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;

/**
 * Manages persistent configuration for JavaOS using Java Properties.
 */
public class SettingsService {

    private final Path settingsFile;
    private final Properties properties = new Properties();
    private final List<Consumer<String>> listeners = new ArrayList<>();

    public SettingsService() {
        this(Paths.get("data", "settings", "settings.properties"));
    }

    public SettingsService(Path settingsFile) {
        this.settingsFile = settingsFile;
        loadDefaults();
        load();
    }

    private void loadDefaults() {
        properties.setProperty("theme", "Cyberpunk Cyan");
        properties.setProperty("wallpaper", "grid");
        properties.setProperty("desktop.grid_size", "normal");
        properties.setProperty("desktop.animation", "balanced");
        properties.setProperty("desktop.scanlines", "true");
        properties.setProperty("desktop.shockwaves", "true");
        properties.setProperty("desktop.icon_size", "standard");
        properties.setProperty("taskbar.clock_format", "24h");
        properties.setProperty("taskbar.clock_seconds", "true");
        properties.setProperty("sound.enabled", "true");
        properties.setProperty("sound.volume", "80");
        properties.setProperty("office.default_word", "wordeditor");
        properties.setProperty("office.default_slides", "slides");

        // Sensible Victus Python detection defaults
        String defaultPython = "python";
        Path py312 = Paths.get(System.getProperty("user.home"), "AppData", "Local", "Programs", "Python", "Python312", "python.exe");
        if (Files.exists(py312)) {
            defaultPython = py312.toString();
        }

        String defaultScript = "";
        String defaultWorkDir = "";
        Path knownVictus = Paths.get("D:", "skanda", "OfflineVoiceAssistant", "Victus_Ollama_Integrated.py");
        if (Files.exists(knownVictus)) {
            defaultScript = knownVictus.toString();
            defaultWorkDir = knownVictus.getParent().toString();
        }

        properties.setProperty("victus.python.path", defaultPython);
        properties.setProperty("victus.script.path", defaultScript);
        properties.setProperty("victus.working.dir", defaultWorkDir);
        properties.setProperty("victus.auto_restart", "false");
    }

    public synchronized void load() {
        if (!Files.exists(settingsFile)) {
            OSLogger.info("Settings file not found, saving initial defaults: " + settingsFile);
            save();
            return;
        }

        try (FileInputStream in = new FileInputStream(settingsFile.toFile())) {
            properties.load(in);
            OSLogger.info("Loaded configuration from " + settingsFile);
        } catch (IOException e) {
            OSLogger.error("Failed to load settings file, using in-memory settings: " + e.getMessage());
        }
    }

    public synchronized void save() {
        try {
            if (settingsFile.getParent() != null && !Files.exists(settingsFile.getParent())) {
                Files.createDirectories(settingsFile.getParent());
            }
            try (FileOutputStream out = new FileOutputStream(settingsFile.toFile())) {
                properties.store(out, "JavaOS Neon Simulator Settings");
            }
            OSLogger.info("Settings successfully saved to " + settingsFile);
        } catch (IOException e) {
            OSLogger.error("Failed to save settings to " + settingsFile + ": " + e.getMessage());
        }
    }

    public synchronized String get(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    public synchronized void set(String key, String value) {
        properties.setProperty(key, value);
        save();
        notifyListeners(key);
    }

    public synchronized String getTheme() {
        return properties.getProperty("theme", "Cyberpunk Cyan");
    }

    public synchronized void setTheme(String theme) {
        set("theme", theme);
    }

    public synchronized String getWallpaper() {
        return properties.getProperty("wallpaper", "grid");
    }

    public synchronized void setWallpaper(String wallpaper) {
        set("wallpaper", wallpaper);
    }

    public synchronized String getVictusPythonPath() {
        return properties.getProperty("victus.python.path", "python");
    }

    public synchronized void setVictusPythonPath(String path) {
        set("victus.python.path", path);
    }

    public synchronized String getVictusScriptPath() {
        return properties.getProperty("victus.script.path", "");
    }

    public synchronized void setVictusScriptPath(String path) {
        set("victus.script.path", path);
    }

    public synchronized String getVictusWorkingDir() {
        return properties.getProperty("victus.working.dir", "");
    }

    public synchronized void setVictusWorkingDir(String path) {
        set("victus.working.dir", path);
    }

    public synchronized String getDesktopAnimation() {
        return properties.getProperty("desktop.animation", "balanced");
    }

    public synchronized void setDesktopAnimation(String val) {
        set("desktop.animation", val);
    }

    public synchronized boolean isScanlinesEnabled() {
        return Boolean.parseBoolean(properties.getProperty("desktop.scanlines", "true"));
    }

    public synchronized void setScanlinesEnabled(boolean val) {
        set("desktop.scanlines", String.valueOf(val));
    }

    public synchronized boolean isShockwavesEnabled() {
        return Boolean.parseBoolean(properties.getProperty("desktop.shockwaves", "true"));
    }

    public synchronized void setShockwavesEnabled(boolean val) {
        set("desktop.shockwaves", String.valueOf(val));
    }

    public synchronized String getDesktopIconSize() {
        return properties.getProperty("desktop.icon_size", "standard");
    }

    public synchronized void setDesktopIconSize(String val) {
        set("desktop.icon_size", val);
    }

    public synchronized String getTaskbarClockFormat() {
        return properties.getProperty("taskbar.clock_format", "24h");
    }

    public synchronized void setTaskbarClockFormat(String val) {
        set("taskbar.clock_format", val);
    }

    public synchronized boolean isTaskbarClockSeconds() {
        return Boolean.parseBoolean(properties.getProperty("taskbar.clock_seconds", "true"));
    }

    public synchronized void setTaskbarClockSeconds(boolean val) {
        set("taskbar.clock_seconds", String.valueOf(val));
    }

    public synchronized boolean isSoundEnabled() {
        return Boolean.parseBoolean(properties.getProperty("sound.enabled", "true"));
    }

    public synchronized void setSoundEnabled(boolean val) {
        set("sound.enabled", String.valueOf(val));
    }

    public synchronized int getSoundVolume() {
        try {
            return Integer.parseInt(properties.getProperty("sound.volume", "80"));
        } catch (Exception e) {
            return 80;
        }
    }

    public synchronized void setSoundVolume(int val) {
        set("sound.volume", String.valueOf(val));
    }

    public synchronized String getDefaultWordApp() {
        return properties.getProperty("office.default_word", "wordeditor");
    }

    public synchronized void setDefaultWordApp(String val) {
        set("office.default_word", val);
    }

    public synchronized String getDefaultSlidesApp() {
        return properties.getProperty("office.default_slides", "slides");
    }

    public synchronized void setDefaultSlidesApp(String val) {
        set("office.default_slides", val);
    }

    public void addListener(Consumer<String> listener) {
        listeners.add(listener);
    }

    private void notifyListeners(String key) {
        for (Consumer<String> listener : listeners) {
            try {
                listener.accept(key);
            } catch (Exception e) {
                OSLogger.error("Error in settings listener for " + key + ": " + e.getMessage());
            }
        }
    }
}

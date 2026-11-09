package javaos.services;

import javaos.util.OSLogger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Dedicated process manager for the Victus Python assistant.
 * Launches the original Python script in a separate OS process via ProcessBuilder,
 * captures stdout/stderr in background threads to data/logs/victus.log,
 * enforces single-instance safety, and prevents UI blocking.
 */
public class VictusProcessService {

    private final SettingsService settingsService;
    private Process activeProcess;
    private Long activePid;
    private final Path victusLogFile = Paths.get("data", "logs", "victus.log");
    private final List<Consumer<String>> outputListeners = new CopyOnWriteArrayList<>();
    private final List<Runnable> stateListeners = new CopyOnWriteArrayList<>();

    public VictusProcessService(SettingsService settingsService) {
        this.settingsService = settingsService;
        try {
            if (victusLogFile.getParent() != null && !Files.exists(victusLogFile.getParent())) {
                Files.createDirectories(victusLogFile.getParent());
            }
        } catch (IOException e) {
            OSLogger.error("Failed to initialize Victus log path: " + e.getMessage());
        }
    }

    public synchronized boolean isRunning() {
        return activeProcess != null && activeProcess.isAlive();
    }

    public synchronized Long getActivePid() {
        if (isRunning()) {
            return activePid;
        }
        return null;
    }

    public void addOutputListener(Consumer<String> listener) {
        outputListeners.add(listener);
    }

    public void removeOutputListener(Consumer<String> listener) {
        outputListeners.remove(listener);
    }

    public void addStateListener(Runnable listener) {
        stateListeners.add(listener);
    }

    public static class LaunchResult {
        public final boolean success;
        public final String message;
        public final Long pid;

        public LaunchResult(boolean success, String message, Long pid) {
            this.success = success;
            this.message = message;
            this.pid = pid;
        }
    }

    /**
     * Attempts to launch Victus Python Assistant asynchronously.
     */
    public synchronized LaunchResult launch() {
        if (isRunning()) {
            return new LaunchResult(false, "Victus is already running (PID: " + activePid + "). Only one instance is permitted.", activePid);
        }

        String pythonPath = resolvePythonExecutable();
        String scriptPath = resolveScriptPath();

        if (scriptPath == null || !Files.exists(Paths.get(scriptPath))) {
            String errorMsg = "Victus script not found!\nConfigured path: " +
                    (scriptPath == null ? "None" : scriptPath) +
                    "\nPlease configure the correct script path in JavaOS Settings.";
            OSLogger.error(errorMsg);
            return new LaunchResult(false, errorMsg, null);
        }

        if (pythonPath == null || (!"python".equalsIgnoreCase(pythonPath) && !Files.exists(Paths.get(pythonPath)))) {
            String errorMsg = "Python executable not found!\nConfigured path: " +
                    (pythonPath == null ? "None" : pythonPath) +
                    "\nPlease install Python 3.10+ or update the path in Settings.";
            OSLogger.error(errorMsg);
            return new LaunchResult(false, errorMsg, null);
        }

        Path scriptFile = Paths.get(scriptPath);
        File workingDir = scriptFile.getParent() != null ? scriptFile.getParent().toFile() : new File(".");

        // Check for custom working dir in settings
        String configuredWorkDir = settingsService.getVictusWorkingDir();
        if (configuredWorkDir != null && !configuredWorkDir.trim().isEmpty() && Files.isDirectory(Paths.get(configuredWorkDir))) {
            workingDir = new File(configuredWorkDir);
        }

        List<String> command = new ArrayList<>();
        command.add(pythonPath);
        command.add(scriptFile.toAbsolutePath().toString());

        OSLogger.info("Starting Victus assistant: " + command + " in working directory: " + workingDir);

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(workingDir);

            // Inherit system environment variables (Path, etc.)
            pb.environment().put("PYTHONUNBUFFERED", "1");

            Process process = pb.start();
            this.activeProcess = process;
            this.activePid = process.pid();

            notifyLog("=== JavaOS launched Victus (PID: " + activePid + ") at " + java.time.LocalDateTime.now() + " ===");

            // Spawn background thread to consume and log standard output & error
            Thread outputReader = new Thread(() -> streamOutput(process.getInputStream(), "[STDOUT]"), "Victus-StdoutReader");
            outputReader.setDaemon(true);
            outputReader.start();

            Thread errorReader = new Thread(() -> streamOutput(process.getErrorStream(), "[STDERR]"), "Victus-StderrReader");
            errorReader.setDaemon(true);
            errorReader.start();

            // Spawn monitor thread to watch process termination
            Thread monitorThread = new Thread(() -> {
                try {
                    int exitCode = process.waitFor();
                    notifyLog("=== Victus process (PID: " + activePid + ") exited with code " + exitCode + " ===");
                    synchronized (VictusProcessService.this) {
                        if (activeProcess == process) {
                            activeProcess = null;
                            activePid = null;
                        }
                    }
                    notifyStateChange();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "Victus-ProcessMonitor");
            monitorThread.setDaemon(true);
            monitorThread.start();

            notifyStateChange();
            return new LaunchResult(true, "Victus launched successfully (PID: " + activePid + ")", activePid);

        } catch (IOException e) {
            String err = "Failed to launch Victus process: " + e.getMessage();
            OSLogger.error(err, e);
            notifyLog("[ERROR] " + err);
            return new LaunchResult(false, err, null);
        }
    }

    private void streamOutput(InputStream in, String prefix) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String formatted = prefix + " " + line;
                notifyLog(formatted);
            }
        } catch (IOException ignored) {}
    }

    private void notifyLog(String line) {
        for (Consumer<String> l : outputListeners) {
            try {
                l.accept(line);
            } catch (Exception ignored) {}
        }

        // Append to log file safely
        try (FileWriter fw = new FileWriter(victusLogFile.toFile(), true);
             PrintWriter pw = new PrintWriter(fw)) {
            pw.println(line);
        } catch (IOException ignored) {}
    }

    private void notifyStateChange() {
        for (Runnable r : stateListeners) {
            try {
                r.run();
            } catch (Exception ignored) {}
        }
    }

    public synchronized boolean terminate(boolean force) {
        if (!isRunning()) {
            return false;
        }

        OSLogger.info("Terminating Victus process (PID: " + activePid + ", force=" + force + ")");
        if (force) {
            activeProcess.destroyForcibly();
        } else {
            activeProcess.destroy();
        }

        // Wait up to 3 seconds for graceful exit
        try {
            boolean exited = activeProcess.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
            if (!exited && force) {
                activeProcess.destroyForcibly();
            }
        } catch (InterruptedException ignored) {}

        return true;
    }

    public String resolvePythonExecutable() {
        String configured = settingsService.getVictusPythonPath();
        if (configured != null && !configured.trim().isEmpty()) {
            if ("python".equalsIgnoreCase(configured) || Files.exists(Paths.get(configured))) {
                return configured;
            }
        }

        // Auto-detection locations on Windows
        Path py312 = Paths.get(System.getProperty("user.home"), "AppData", "Local", "Programs", "Python", "Python312", "python.exe");
        if (Files.exists(py312)) return py312.toString();

        Path py311 = Paths.get(System.getProperty("user.home"), "AppData", "Local", "Programs", "Python", "Python311", "python.exe");
        if (Files.exists(py311)) return py311.toString();

        return "python";
    }

    public String resolveScriptPath() {
        String configured = settingsService.getVictusScriptPath();
        if (configured != null && !configured.trim().isEmpty() && Files.exists(Paths.get(configured))) {
            return configured;
        }

        // Known location from user environment
        Path knownPath = Paths.get("D:", "skanda", "OfflineVoiceAssistant", "Victus_Ollama_Integrated.py");
        if (Files.exists(knownPath)) {
            settingsService.setVictusScriptPath(knownPath.toString());
            settingsService.setVictusWorkingDir(knownPath.getParent().toString());
            return knownPath.toString();
        }

        return configured;
    }
}

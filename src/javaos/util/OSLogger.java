package javaos.util;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Thread-safe logging utility for JavaOS.
 * Directs diagnostic and audit logs to disk and standard streams.
 */
public final class OSLogger {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static Path logFile = Paths.get("data", "logs", "javaos.log");
    private static final Object LOCK = new Object();

    private OSLogger() {
        // Utility class
    }

    public static void setLogFile(Path path) {
        synchronized (LOCK) {
            logFile = path;
            try {
                if (path.getParent() != null) {
                    Files.createDirectories(path.getParent());
                }
            } catch (IOException e) {
                System.err.println("[OSLogger] Failed to create log directories: " + e.getMessage());
            }
        }
    }

    public static void info(String message) {
        log("INFO", message, null);
    }

    public static void warn(String message) {
        log("WARN", message, null);
    }

    public static void error(String message) {
        log("ERROR", message, null);
    }

    public static void error(String message, Throwable throwable) {
        log("ERROR", message, throwable);
    }

    public static void debug(String message) {
        log("DEBUG", message, null);
    }

    private static void log(String level, String message, Throwable throwable) {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        String thread = Thread.currentThread().getName();
        String formatted = String.format("[%s] [%s] [%s] %s", timestamp, thread, level, message);

        if ("ERROR".equals(level)) {
            System.err.println(formatted);
            if (throwable != null) {
                throwable.printStackTrace(System.err);
            }
        } else {
            System.out.println(formatted);
        }

        synchronized (LOCK) {
            try {
                if (logFile != null) {
                    if (logFile.getParent() != null && !Files.exists(logFile.getParent())) {
                        Files.createDirectories(logFile.getParent());
                    }
                    try (PrintWriter out = new PrintWriter(new FileWriter(logFile.toFile(), true))) {
                        out.println(formatted);
                        if (throwable != null) {
                            throwable.printStackTrace(out);
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("[OSLogger] Write to log failed: " + e.getMessage());
            }
        }
    }
}

package javaos.models;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Model representing a file or directory inside the JavaOS virtual filesystem.
 */
public class FileItem {

    private final String name;
    private final String virtualPath;
    private final boolean isDirectory;
    private final long sizeBytes;
    private final long lastModifiedMillis;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public FileItem(String name, String virtualPath, boolean isDirectory, long sizeBytes, long lastModifiedMillis) {
        this.name = name;
        this.virtualPath = virtualPath;
        this.isDirectory = isDirectory;
        this.sizeBytes = sizeBytes;
        this.lastModifiedMillis = lastModifiedMillis;
    }

    public String getName() {
        return name;
    }

    public String getVirtualPath() {
        return virtualPath;
    }

    public boolean isDirectory() {
        return isDirectory;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public long getLastModifiedMillis() {
        return lastModifiedMillis;
    }

    public String getFormattedSize() {
        if (isDirectory) {
            return "<DIR>";
        }
        if (sizeBytes < 1024) {
            return sizeBytes + " B";
        } else if (sizeBytes < 1024 * 1024) {
            return String.format("%.1f KB", sizeBytes / 1024.0);
        } else {
            return String.format("%.1f MB", sizeBytes / (1024.0 * 1024.0));
        }
    }

    public String getFormattedDate() {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(lastModifiedMillis), ZoneId.systemDefault())
                .format(DATE_FORMAT);
    }

    public boolean isTextFile() {
        if (isDirectory) return false;
        String lower = name.toLowerCase();
        return lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".log") ||
               lower.endsWith(".json") || lower.endsWith(".properties") || lower.endsWith(".xml") ||
               lower.endsWith(".csv") || lower.endsWith(".java") || lower.endsWith(".py") ||
               lower.endsWith(".sh") || lower.endsWith(".bat") || lower.endsWith(".ini") ||
               !lower.contains(".");
    }

    @Override
    public String toString() {
        return name + (isDirectory ? "/" : "");
    }
}

package javaos.models;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Model representing a system notification within JavaOS.
 */
public class NotificationMessage {

    public enum Level {
        INFO, SUCCESS, WARNING, ERROR
    }

    private final String id;
    private final String title;
    private final String message;
    private final Level level;
    private final LocalDateTime timestamp;
    private boolean read;

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public NotificationMessage(String title, String message, Level level) {
        this.id = System.currentTimeMillis() + "-" + (int) (Math.random() * 1000);
        this.title = title;
        this.message = message;
        this.level = level == null ? Level.INFO : level;
        this.timestamp = LocalDateTime.now();
        this.read = false;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public Level getLevel() {
        return level;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getFormattedTime() {
        return timestamp.format(TIME_FORMAT);
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }
}

package javaos.core;

import javaos.models.NotificationMessage;
import javaos.util.OSLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Manages desktop toast alerts and system notification history.
 */
public class NotificationManager {

    private final List<NotificationMessage> history = new CopyOnWriteArrayList<>();
    private final List<Consumer<NotificationMessage>> toastListeners = new CopyOnWriteArrayList<>();

    public void postNotification(String title, String message, NotificationMessage.Level level) {
        NotificationMessage msg = new NotificationMessage(title, message, level);
        history.add(msg);
        OSLogger.info("Notification [" + level + "] " + title + ": " + message);

        for (Consumer<NotificationMessage> listener : toastListeners) {
            try {
                listener.accept(msg);
            } catch (Exception e) {
                OSLogger.error("Failed to deliver notification to listener: " + e.getMessage());
            }
        }
    }

    public void addToastListener(Consumer<NotificationMessage> listener) {
        toastListeners.add(listener);
    }

    public void removeToastListener(Consumer<NotificationMessage> listener) {
        toastListeners.remove(listener);
    }

    public List<NotificationMessage> getHistory() {
        return Collections.unmodifiableList(new ArrayList<>(history));
    }

    public int getUnreadCount() {
        int count = 0;
        for (NotificationMessage m : history) {
            if (!m.isRead()) count++;
        }
        return count;
    }

    public void markAllAsRead() {
        for (NotificationMessage m : history) {
            m.setRead(true);
        }
    }

    public void clearHistory() {
        history.clear();
    }
}

package javaos.services;

import javaos.util.OSLogger;

import java.awt.*;
import java.awt.datatransfer.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Background daemon service that tracks global system clipboard changes
 * and maintains a searchable history of copied text snippets.
 */
public class ClipboardHistoryService {

    public static class ClipboardItem {
        private final String content;
        private final LocalDateTime timestamp;
        private final int charCount;
        private final int lineCount;

        public ClipboardItem(String content) {
            this.content = content;
            this.timestamp = LocalDateTime.now();
            this.charCount = content.length();
            this.lineCount = content.split("\n").length;
        }

        public String getContent() { return content; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public int getCharCount() { return charCount; }
        public int getLineCount() { return lineCount; }

        public String getFormattedTime() {
            return timestamp.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        }

        public String getPreview() {
            String singleLine = content.replace("\n", " ⏎ ").replace("\t", " ").trim();
            if (singleLine.length() > 65) {
                return singleLine.substring(0, 62) + "...";
            }
            return singleLine;
        }
    }

    private final List<ClipboardItem> history = new CopyOnWriteArrayList<>();
    private static final int MAX_HISTORY = 30;
    private String lastContent = "";
    private javax.swing.Timer pollTimer;

    public ClipboardHistoryService() {
        startClipboardWatcher();
    }

    private void startClipboardWatcher() {
        // Poll clipboard every 400ms on background timer
        pollTimer = new javax.swing.Timer(400, e -> checkClipboard());
        pollTimer.start();
        OSLogger.info("ClipboardHistoryService started (Global Win+V history daemon active).");
    }

    private synchronized void checkClipboard() {
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                String text = (String) clipboard.getData(DataFlavor.stringFlavor);
                if (text != null && !text.trim().isEmpty() && !text.equals(lastContent)) {
                    lastContent = text;
                    addItem(text);
                }
            }
        } catch (Exception ignored) {
            // Clipboard may be temporarily locked by another host process
        }
    }

    public synchronized void addItem(String text) {
        // Remove prior duplicate if exists
        history.removeIf(item -> item.getContent().equals(text));

        // Insert at beginning
        history.add(0, new ClipboardItem(text));

        while (history.size() > MAX_HISTORY) {
            history.remove(history.size() - 1);
        }
    }

    public List<ClipboardItem> getHistory() {
        return Collections.unmodifiableList(new ArrayList<>(history));
    }

    public void copyToSystemClipboard(String text) {
        try {
            lastContent = text;
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            StringSelection selection = new StringSelection(text);
            clipboard.setContents(selection, selection);
            addItem(text);
        } catch (Exception ex) {
            OSLogger.error("Failed to copy text to clipboard: " + ex.getMessage());
        }
    }

    public synchronized void clearHistory() {
        history.clear();
    }
}

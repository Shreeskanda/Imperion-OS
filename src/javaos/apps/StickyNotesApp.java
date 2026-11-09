package javaos.apps;

import javaos.ui.Theme;
import javaos.ui.ThemeManager;
import javaos.ui.components.CyberpunkIcons;
import javaos.ui.components.NeonButton;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Neon Sticky Notes Desktop Widget for IMPERION OS.
 * Features:
 *  - Multi-colored cyber sticky notes (Gold Amber, Cyan Ice, Hot Pink, Mint Green)
 *  - Auto-saving note contents to disk
 *  - Minimalist floating notepad cards with quick addition and deletion
 */
public class StickyNotesApp extends OSApplication {

    private JTextArea noteArea;
    private JPanel colorBar;
    private final File notesFile = new File("data/notes/quick_note.txt");
    private Color currentNoteBg = new Color(30, 41, 59);

    public StickyNotesApp() {
        super(new AppDescriptor(
                "stickynotes",
                "Neon Sticky Notes",
                "Quick desktop notes, task reminders & memory scratchpad",
                AppDescriptor.Category.PRODUCTIVITY,
                CyberpunkIcons.createIcon("STICKY", 20),
                false
        ));
    }

    @Override
    public Dimension getPreferredWindowSize() {
        return new Dimension(320, 360);
    }

    @Override
    public JComponent createContent() {
        JPanel main = new JPanel(new BorderLayout());
        main.setOpaque(true);
        main.setBackground(currentNoteBg);

        // Top Toolbar
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.setBorder(BorderFactory.createEmptyBorder(6, 8, 4, 8));

        JLabel title = new JLabel("STICKY MEMORY NOTE");
        title.setFont(new Font("Segoe UI", Font.BOLD, 11));
        title.setForeground(new Color(203, 213, 225));
        top.add(title, BorderLayout.WEST);

        // Color selector dots
        colorBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        colorBar.setOpaque(false);

        Color[] colors = {
                new Color(30, 41, 59),   // Cyber Slate
                new Color(40, 25, 45),   // Neon Purple
                new Color(15, 35, 30),   // Mint Dark
                new Color(45, 35, 15)    // Amber Gold
        };

        for (Color c : colors) {
            JButton b = new JButton();
            b.setPreferredSize(new Dimension(14, 14));
            b.setBackground(c);
            b.setBorder(BorderFactory.createLineBorder(Color.GRAY));
            b.setFocusPainted(false);
            b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            b.addActionListener(e -> {
                currentNoteBg = c;
                main.setBackground(c);
                noteArea.setBackground(c);
            });
            colorBar.add(b);
        }

        JButton btnClear = new JButton("×");
        btnClear.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnClear.setForeground(Color.LIGHT_GRAY);
        btnClear.setContentAreaFilled(false);
        btnClear.setBorderPainted(false);
        btnClear.setFocusPainted(false);
        btnClear.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnClear.addActionListener(e -> noteArea.setText(""));
        colorBar.add(btnClear);

        top.add(colorBar, BorderLayout.EAST);
        main.add(top, BorderLayout.NORTH);

        // Note text area
        noteArea = new JTextArea();
        noteArea.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        noteArea.setForeground(new Color(241, 245, 249));
        noteArea.setBackground(currentNoteBg);
        noteArea.setCaretColor(Color.WHITE);
        noteArea.setLineWrap(true);
        noteArea.setWrapStyleWord(true);
        noteArea.setBorder(BorderFactory.createEmptyBorder(8, 12, 12, 12));

        loadNote();

        // Autosave on changes
        noteArea.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { saveNote(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { saveNote(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { saveNote(); }
        });

        main.add(noteArea, BorderLayout.CENTER);
        return main;
    }

    private void loadNote() {
        if (notesFile.exists()) {
            try {
                String text = Files.readString(notesFile.toPath(), StandardCharsets.UTF_8);
                noteArea.setText(text);
            } catch (Exception ignored) {}
        } else {
            noteArea.setText("⚡ QUICK MEMORY NOTE\n• Press Alt+Space for Spotlight search\n• Use CyberSheet for spreadsheet formulas\n• Listen to NeonWave radio in background");
        }
    }

    private void saveNote() {
        try {
            if (notesFile.getParentFile() != null && !notesFile.getParentFile().exists()) {
                notesFile.getParentFile().mkdirs();
            }
            Files.writeString(notesFile.toPath(), noteArea.getText(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
    }

    @Override
    public void onClose() {
        saveNote();
    }
}

package javaos.apps;

import javaos.models.FileItem;
import javaos.services.NetworkScannerService;
import javaos.ui.Theme;
import javaos.ui.ThemeManager;
import javaos.ui.components.CyberpunkIcons;
import javaos.ui.components.NeonScrollBarUI;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Sandboxed cyberpunk command interpreter.
 * Strictly operates on JavaOS services; never forwards commands to the host OS shell.
 * Features: command history, tab completion, color-coded output, extended commands.
 */
public class TerminalApp extends OSApplication {

    private static class SessionState {
        String id;
        String title;
        JTextPane textPane;
        StyledDocument doc;
        JTextField inputField;
        JLabel promptLabel;
        String currentVirtualDir = "/";
        final List<String> history = new ArrayList<>();
        int historyIndex = -1;
        JPanel panel;
    }

    private final List<SessionState> sessions = new ArrayList<>();
    private SessionState activeSession;
    private JPanel tabStrip;
    private JPanel sessionsContainer;
    private CardLayout sessionsCardLayout;
    private int sessionCounter = 1;

    // Status bar (bottom)
    private JLabel termStatusLabel;
    private int lastExitCode = 0;

    // Active session delegate references for existing command methods
    private JTextPane textPane;
    private StyledDocument doc;
    private JTextField inputField;
    private JLabel promptLabel;
    private String currentVirtualDir = "/";
    private List<String> history = new ArrayList<>();
    private int historyIndex = -1;

    /** Known commands for tab completion */
    private static final String[] KNOWN_COMMANDS = {
        "help", "clear", "cls", "pwd", "time", "date", "uptime", "whoami",
        "ls", "dir", "cd", "mkdir", "touch", "cat", "write", "search", "find",
        "rm", "del", "cp", "mv", "apps", "open", "sysinfo", "theme", "echo",
        "history", "victus", "lock", "netscan", "scan", "airscan", "exit"
    };

    public TerminalApp() {
        super(new AppDescriptor(
                "terminal",
                "Terminal",
                "Cyberpunk sandboxed command-line console",
                AppDescriptor.Category.UTILITIES,
                CyberpunkIcons.createIcon("TERMINAL", 20),
                false
        ));
    }

    @Override
    public Dimension getPreferredWindowSize() {
        return new Dimension(740, 480);
    }

    @Override
    public JComponent createContent() {
        JPanel main = new JPanel(new BorderLayout());
        main.setOpaque(true);

        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        main.setBackground(theme.background);

        // ── NORTH: Tab strip + Quick shortcuts bar ───────────────────────────
        JPanel northContainer = new JPanel();
        northContainer.setLayout(new BoxLayout(northContainer, BoxLayout.Y_AXIS));
        northContainer.setOpaque(true);
        northContainer.setBackground(theme.elevatedPanel);

        // Top Cyber Tab Strip
        tabStrip = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 3));
        tabStrip.setOpaque(true);
        tabStrip.setBackground(theme.elevatedPanel);
        tabStrip.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, theme.secondaryAccent));
        northContainer.add(tabStrip);

        // Quick Shortcuts Bar
        JPanel shortcutsBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        shortcutsBar.setOpaque(true);
        shortcutsBar.setBackground(theme.panel);
        shortcutsBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, theme.secondaryAccent));

        JLabel shortcutLbl = new JLabel("⚡");
        shortcutLbl.setFont(theme.fontSmall);
        shortcutLbl.setForeground(theme.neonAccent);
        shortcutLbl.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        shortcutsBar.add(shortcutLbl);

        String[] quickCmds = {"pwd", "ls", "clear", "history", "whoami"};
        for (String qcmd : quickCmds) {
            JButton qBtn = new JButton(qcmd);
            qBtn.setFont(new Font("Consolas", Font.PLAIN, 11));
            qBtn.setForeground(theme.neonAccent);
            qBtn.setBackground(theme.elevatedPanel);
            qBtn.setOpaque(true);
            qBtn.setFocusPainted(false);
            qBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            qBtn.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(theme.secondaryAccent, 1),
                    BorderFactory.createEmptyBorder(1, 7, 1, 7)
            ));
            qBtn.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mouseEntered(java.awt.event.MouseEvent e) {
                    qBtn.setBackground(theme.panel);
                    qBtn.setForeground(Color.WHITE);
                }
                @Override public void mouseExited(java.awt.event.MouseEvent e) {
                    qBtn.setBackground(theme.elevatedPanel);
                    qBtn.setForeground(theme.neonAccent);
                }
            });
            qBtn.addActionListener(e -> {
                if (activeSession != null) {
                    syncActiveSessionFromFields();
                    executeCommand(qcmd);
                    syncActiveSessionToFields();
                    activeSession.inputField.requestFocusInWindow();
                }
            });
            shortcutsBar.add(qBtn);
        }
        northContainer.add(shortcutsBar);

        main.add(northContainer, BorderLayout.NORTH);

        // ── CENTER: CardLayout sessions container ────────────────────────────
        sessionsCardLayout = new CardLayout();
        sessionsContainer = new JPanel(sessionsCardLayout);
        sessionsContainer.setOpaque(false);
        main.add(sessionsContainer, BorderLayout.CENTER);

        // ── SOUTH: Status bar ────────────────────────────────────────────────
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setOpaque(true);
        statusBar.setBackground(theme.panel);
        statusBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, theme.secondaryAccent),
                BorderFactory.createEmptyBorder(2, 10, 2, 10)
        ));
        termStatusLabel = new JLabel("Shell 1  |  /  |  exit: 0");
        termStatusLabel.setFont(new Font("Consolas", Font.PLAIN, 11));
        termStatusLabel.setForeground(theme.textSecondary);
        statusBar.add(termStatusLabel, BorderLayout.WEST);
        main.add(statusBar, BorderLayout.SOUTH);

        // Create initial session
        createNewSession("Shell 1");

        return main;
    }

    private void createNewSession(String title) {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        SessionState s = new SessionState();
        s.id = "session_" + System.currentTimeMillis() + "_" + sessionCounter;
        s.title = title;

        s.panel = new JPanel(new BorderLayout());
        s.panel.setOpaque(true);
        s.panel.setBackground(theme.background);

        s.textPane = new JTextPane();
        s.textPane.setEditable(false);
        s.textPane.setBackground(theme.background);
        s.textPane.setFont(theme.fontMonospace);
        s.textPane.setCaretColor(Color.WHITE);
        s.doc = s.textPane.getStyledDocument();

        JScrollPane scrollPane = new JScrollPane(s.textPane);
        scrollPane.getVerticalScrollBar().setUI(new NeonScrollBarUI());
        scrollPane.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        s.panel.add(scrollPane, BorderLayout.CENTER);

        // Bottom Input Line
        JPanel inputPanel = new JPanel(new BorderLayout(6, 0));
        inputPanel.setOpaque(true);
        inputPanel.setBackground(theme.panel);
        inputPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, theme.secondaryAccent),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));

        s.promptLabel = new JLabel("user@imperion:~" + s.currentVirtualDir + "$ ");
        s.promptLabel.setFont(theme.fontMonospace);
        s.promptLabel.setForeground(theme.neonAccent);
        inputPanel.add(s.promptLabel, BorderLayout.WEST);

        s.inputField = new JTextField();
        s.inputField.setOpaque(false);
        s.inputField.setBorder(BorderFactory.createEmptyBorder());
        s.inputField.setFont(theme.fontMonospace);
        s.inputField.setForeground(theme.textPrimary);
        s.inputField.setCaretColor(Color.WHITE);

        s.inputField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                // Key click sound
                if (javaos.core.JavaOS.getInstance() != null && javaos.core.JavaOS.getInstance().getSoundService() != null) {
                    javaos.core.JavaOS.getInstance().getSoundService().playKeyClick();
                }

                if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_T) {
                    sessionCounter++;
                    createNewSession("Shell " + sessionCounter);
                    return;
                }

                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    syncActiveSessionFromFields();
                    executeCommand(inputField.getText());
                    inputField.setText("");
                    syncActiveSessionToFields();
                } else if (e.getKeyCode() == KeyEvent.VK_UP) {
                    navigateHistory(-1);
                    e.consume();
                } else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                    navigateHistory(1);
                    e.consume();
                } else if (e.getKeyCode() == KeyEvent.VK_TAB) {
                    e.consume();
                    doTabCompletion();
                }
            }
        });

        inputPanel.add(s.inputField, BorderLayout.CENTER);
        s.panel.add(inputPanel, BorderLayout.SOUTH);

        sessions.add(s);
        sessionsContainer.add(s.panel, s.id);

        switchToSession(s);
        printWelcomeBanner();
        refreshTabStrip();
    }

    private void switchToSession(SessionState s) {
        if (activeSession != null) {
            syncActiveSessionFromFields();
        }
        activeSession = s;
        syncActiveSessionToFields();
        sessionsCardLayout.show(sessionsContainer, s.id);
        refreshTabStrip();
        SwingUtilities.invokeLater(s.inputField::requestFocusInWindow);
    }

    private void closeSession(SessionState s) {
        if (sessions.size() <= 1) return;
        int idx = sessions.indexOf(s);
        sessions.remove(s);
        sessionsContainer.remove(s.panel);
        int nextIdx = Math.max(0, idx - 1);
        switchToSession(sessions.get(nextIdx));
    }

    private void syncActiveSessionToFields() {
        if (activeSession == null) return;
        this.textPane = activeSession.textPane;
        this.doc = activeSession.doc;
        this.inputField = activeSession.inputField;
        this.promptLabel = activeSession.promptLabel;
        this.currentVirtualDir = activeSession.currentVirtualDir;
        this.history = activeSession.history;
        this.historyIndex = activeSession.historyIndex;
    }

    private void syncActiveSessionFromFields() {
        if (activeSession == null) return;
        activeSession.currentVirtualDir = this.currentVirtualDir;
        activeSession.historyIndex = this.historyIndex;
    }

    private void refreshTabStrip() {
        tabStrip.removeAll();
        Theme theme = ThemeManager.getInstance().getCurrentTheme();

        for (SessionState s : sessions) {
            boolean isActive = (s == activeSession);
            JPanel tab = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
            tab.setOpaque(true);
            tab.setBackground(isActive ? theme.neonAccent : theme.panel);
            tab.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(isActive ? theme.neonAccent : theme.secondaryAccent, 1),
                    BorderFactory.createEmptyBorder(2, 8, 2, 6)
            ));
            tab.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            JLabel titleLbl = new JLabel(s.title);
            titleLbl.setFont(theme.fontSmall);
            titleLbl.setForeground(isActive ? Color.BLACK : theme.textPrimary);
            tab.add(titleLbl);

            if (sessions.size() > 1) {
                JLabel closeLbl = new JLabel("✕");
                closeLbl.setFont(theme.fontSmall);
                closeLbl.setForeground(isActive ? Color.DARK_GRAY : theme.textSecondary);
                closeLbl.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                closeLbl.addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override
                    public void mousePressed(java.awt.event.MouseEvent e) {
                        closeSession(s);
                    }
                });
                tab.add(closeLbl);
            }

            tab.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mousePressed(java.awt.event.MouseEvent e) {
                    switchToSession(s);
                }
            });

            tabStrip.add(tab);
        }

        // New Tab [+] Button
        JButton btnAdd = new JButton("+");
        btnAdd.setFont(theme.fontBold);
        btnAdd.setForeground(theme.neonAccent);
        btnAdd.setOpaque(false);
        btnAdd.setContentAreaFilled(false);
        btnAdd.setBorderPainted(false);
        btnAdd.setFocusPainted(false);
        btnAdd.setToolTipText("New Shell Tab (Ctrl+T)");
        btnAdd.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnAdd.addActionListener(e -> {
            sessionCounter++;
            createNewSession("Shell " + sessionCounter);
        });
        tabStrip.add(btnAdd);

        tabStrip.revalidate();
        tabStrip.repaint();
    }

    private void printWelcomeBanner() {
        appendColored("==================================================================\n", ThemeManager.getInstance().getCurrentTheme().neonAccent);
        appendColored(" IMPERION OS COMMAND INTERPRETER [Version 2026.10-SANDBOXED]\n", ThemeManager.getInstance().getCurrentTheme().neonAccent);
        appendColored(" Type 'help' for a full list of available commands.\n", ThemeManager.getInstance().getCurrentTheme().textPrimary);
        appendColored(" All filesystem commands are strictly confined to virtual home.\n", ThemeManager.getInstance().getCurrentTheme().textPrimary);
        appendColored("==================================================================\n", ThemeManager.getInstance().getCurrentTheme().neonAccent);
        appendColored("\n", ThemeManager.getInstance().getCurrentTheme().textPrimary);
    }

    private void updatePrompt() {
        promptLabel.setText("user@imperion:~" + currentVirtualDir + "$ ");
    }

    private void updateTermStatus() {
        if (termStatusLabel == null || activeSession == null) return;
        termStatusLabel.setText(
            activeSession.title + "  |  " + currentVirtualDir + "  |  exit: " + lastExitCode
        );
    }

    private void navigateHistory(int dir) {
        if (history.isEmpty()) return;
        historyIndex += dir;
        if (historyIndex < 0) historyIndex = 0;
        if (historyIndex >= history.size()) {
            historyIndex = history.size();
            inputField.setText("");
            return;
        }
        inputField.setText(history.get(historyIndex));
    }

    private void doTabCompletion() {
        String current = inputField.getText();
        if (current.isEmpty()) return;

        List<String> tokens = parseArguments(current);
        if (tokens.isEmpty()) return;

        if (tokens.size() == 1) {
            // Complete command name
            String prefix = tokens.get(0).toLowerCase();
            List<String> matches = new ArrayList<>();
            for (String cmd : KNOWN_COMMANDS) {
                if (cmd.startsWith(prefix)) {
                    matches.add(cmd);
                }
            }
            if (matches.size() == 1) {
                inputField.setText(matches.get(0) + " ");
            } else if (matches.size() > 1) {
                appendColored("\n", ThemeManager.getInstance().getCurrentTheme().textPrimary);
                appendColored(String.join("  ", matches) + "\n", ThemeManager.getInstance().getCurrentTheme().neonAccent);
            }
        } else {
            // Complete file/directory path argument
            String partialArg = tokens.get(tokens.size() - 1);
            String dirToSearch = currentVirtualDir;
            String namePrefix = partialArg;

            int slashIdx = partialArg.lastIndexOf('/');
            if (slashIdx >= 0) {
                dirToSearch = resolvePath(partialArg.substring(0, slashIdx));
                namePrefix = partialArg.substring(slashIdx + 1);
            }

            try {
                List<FileItem> items = getOS().getFileService().listFiles(dirToSearch);
                List<String> matches = new ArrayList<>();
                final String finalNamePrefix = namePrefix;
                for (FileItem item : items) {
                    if (item.getName().startsWith(finalNamePrefix)) {
                        matches.add(item.getName() + (item.isDirectory() ? "/" : ""));
                    }
                }
                if (matches.size() == 1) {
                    // Build new input
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < tokens.size() - 1; i++) {
                        sb.append(tokens.get(i)).append(" ");
                    }
                    if (slashIdx >= 0) {
                        sb.append(partialArg, 0, slashIdx + 1);
                    }
                    sb.append(matches.get(0));
                    inputField.setText(sb.toString());
                } else if (matches.size() > 1) {
                    appendColored("\n", ThemeManager.getInstance().getCurrentTheme().textPrimary);
                    appendColored(String.join("  ", matches) + "\n", ThemeManager.getInstance().getCurrentTheme().neonAccent);
                }
            } catch (IOException ignored) {}
        }
    }

    private void executeCommand(String rawLine) {
        if (rawLine == null) return;
        String line = rawLine.trim();
        if (line.isEmpty()) return;

        history.add(line);
        historyIndex = history.size();

        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        appendColored("user@imperion:~" + currentVirtualDir + "$ " + line + "\n", theme.secondaryAccent);

        List<String> tokens = parseArguments(line);
        if (tokens.isEmpty()) return;

        String cmd = tokens.get(0).toLowerCase();
        List<String> args = tokens.subList(1, tokens.size());

        lastExitCode = 0;
        try {
            switch (cmd) {
                case "help":
                    cmdHelp();
                    break;
                case "clear":
                case "cls":
                    textPane.setText("");
                    break;
                case "pwd":
                    appendColored(currentVirtualDir + "\n", theme.textPrimary);
                    break;
                case "time":
                    appendColored("Current Time: " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "\n", theme.neonAccent);
                    break;
                case "date":
                    appendColored("Current Date: " + LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM dd, yyyy")) + "\n", theme.neonAccent);
                    break;
                case "uptime":
                    appendColored("IMPERION OS Uptime: " + getOS().getSystemMetricsService().getFormattedUptime() + "\n", theme.neonAccent);
                    break;
                case "whoami":
                    appendColored("user: " + System.getProperty("user.name") + " (IMPERION OS Administrator)\n", theme.textPrimary);
                    break;
                case "echo":
                    appendColored(String.join(" ", args) + "\n", theme.textPrimary);
                    break;
                case "history":
                    cmdHistory();
                    break;
                case "ls":
                case "dir":
                    cmdLs(args);
                    break;
                case "cd":
                    cmdCd(args);
                    break;
                case "mkdir":
                    cmdMkdir(args);
                    break;
                case "touch":
                    cmdTouch(args);
                    break;
                case "cat":
                    cmdCat(args);
                    break;
                case "write":
                    cmdWrite(args);
                    break;
                case "search":
                case "find":
                    cmdSearch(args);
                    break;
                case "rm":
                case "del":
                    cmdRm(args);
                    break;
                case "cp":
                    cmdCp(args);
                    break;
                case "mv":
                    cmdMv(args);
                    break;
                case "apps":
                    cmdApps();
                    break;
                case "open":
                    cmdOpen(args);
                    break;
                case "sysinfo":
                    cmdSysinfo();
                    break;
                case "theme":
                    cmdTheme(args);
                    break;
                case "victus":
                    appendColored("Launching Victus Python Assistant subprocess...\n", theme.neonAccent);
                    getOS().getVictusService().launch();
                    break;
                case "lock":
                    appendColored("Locking IMPERION OS workstation session...\n", theme.neonAccent);
                    getOS().lockScreen();
                    break;
                case "netscan":
                case "scan":
                    cmdNetScan();
                    break;
                case "airscan":
                    cmdAirScan();
                    break;
                case "exit":
                case "shutdown":
                case "quit":
                    appendColored("Initiating IMPERION OS shutdown sequence...\n", theme.neonAccent);
                    appendColored("Goodbye, " + System.getProperty("user.name") + ". System powering down.\n", theme.success);
                    new javax.swing.Timer(800, ev -> {
                        ((javax.swing.Timer) ev.getSource()).stop();
                        getOS().requestShutdown();
                    }).start();
                    break;
                default:
                    appendColored("Unknown command: '" + cmd + "'. Type 'help' to view valid commands.\n", theme.error);
                    break;
            }
        } catch (SecurityException se) {
            appendColored("SECURITY VIOLATION: " + se.getMessage() + "\n", ThemeManager.getInstance().getCurrentTheme().error);
            lastExitCode = 1;
        } catch (Exception e) {
            appendColored("Error executing command: " + e.getMessage() + "\n", ThemeManager.getInstance().getCurrentTheme().error);
            lastExitCode = 1;
        }

        updatePrompt();
        updateTermStatus();
        textPane.setCaretPosition(doc.getLength());
    }

    private void cmdHelp() {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        appendColored("IMPERION OS SHELL COMMAND REFERENCE:\n", theme.neonAccent);
        appendColored("  help                   - Display this command manual\n", theme.textPrimary);
        appendColored("  clear                  - Clear terminal output buffer\n", theme.textPrimary);
        appendColored("  pwd                    - Print working virtual directory\n", theme.textPrimary);
        appendColored("  time / date / uptime   - System clock and uptime statistics\n", theme.textPrimary);
        appendColored("  whoami                 - Print current user name\n", theme.textPrimary);
        appendColored("  echo <text>            - Print text to terminal\n", theme.textPrimary);
        appendColored("  history                - List previously entered commands\n", theme.textPrimary);
        appendColored("  ls [dir]               - List files and directories\n", theme.textPrimary);
        appendColored("  cd <path>              - Change working virtual directory\n", theme.textPrimary);
        appendColored("  mkdir <name>           - Create a new virtual folder\n", theme.textPrimary);
        appendColored("  touch <file>           - Create an empty virtual file\n", theme.textPrimary);
        appendColored("  cat <file>             - Print text file contents\n", theme.textPrimary);
        appendColored("  write <file> <text>    - Write text into a virtual file\n", theme.textPrimary);
        appendColored("  rm <file>              - Move file or folder to Recycle Bin\n", theme.textPrimary);
        appendColored("  cp <src> <dest>        - Copy file or folder\n", theme.textPrimary);
        appendColored("  mv <src> <dest>        - Move or rename file or folder\n", theme.textPrimary);
        appendColored("  search <term>          - Search filenames recursively\n", theme.textPrimary);
        appendColored("  apps                   - List registered IMPERION OS applications\n", theme.textPrimary);
        appendColored("  open <appId|file>      - Launch application or open document\n", theme.textPrimary);
        appendColored("  sysinfo                - Display JVM and Host hardware metrics\n", theme.textPrimary);
        appendColored("  theme <name>           - Switch active visual theme\n", theme.textPrimary);
        appendColored("  victus                 - Launch Victus Python AI Assistant\n", theme.textPrimary);
        appendColored("  lock                   - Lock IMPERION OS workstation session\n", theme.textPrimary);
        appendColored("  exit / shutdown / quit - Shut down IMPERION OS completely\n", theme.textPrimary);
        appendColored("  [Tab]                  - Auto-complete commands and file paths\n", theme.textPrimary);
        appendColored("  [Up/Down]              - Navigate command history\n", theme.textPrimary);
    }

    private void cmdHistory() {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        appendColored("Command History:\n", theme.neonAccent);
        // Show all history except the current "history" command (last item)
        int displayCount = history.size() - 1;
        for (int i = 0; i < displayCount; i++) {
            appendColored(String.format("  %3d  %s\n", i + 1, history.get(i)), theme.textPrimary);
        }
        if (displayCount <= 0) {
            appendColored("  (no previous commands)\n", theme.textPrimary);
        }
    }

    private void cmdLs(List<String> args) throws IOException {
        String target = currentVirtualDir;
        if (!args.isEmpty()) {
            target = resolvePath(args.get(0));
        }

        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        List<FileItem> items = getOS().getFileService().listFiles(target);
        if (items.isEmpty()) {
            appendColored("  (Directory is empty)\n", theme.textPrimary);
            return;
        }

        appendColored(String.format("  %-18s %-10s %-12s %s\n", "NAME", "TYPE", "SIZE", "MODIFIED"), theme.neonAccent);
        appendColored("  -------------------------------------------------------------\n", theme.textPrimary);
        for (FileItem item : items) {
            String typeStr = item.isDirectory() ? "<DIR>" : "<FILE>";
            Color c = item.isDirectory() ? theme.neonAccent : theme.textPrimary;
            appendColored(String.format("  %-18s %-10s %-12s %s\n", item.getName(), typeStr, item.getFormattedSize(), item.getFormattedDate()), c);
        }
    }

    private void cmdCd(List<String> args) throws IOException {
        if (args.isEmpty()) {
            currentVirtualDir = "/";
            return;
        }
        String p = args.get(0);
        if ("..".equals(p)) {
            int last = currentVirtualDir.lastIndexOf('/');
            currentVirtualDir = last <= 0 ? "/" : currentVirtualDir.substring(0, last);
            return;
        }
        if ("/".equals(p) || "~".equals(p)) {
            currentVirtualDir = "/";
            return;
        }

        String target = resolvePath(p);
        // Verify target exists and is directory
        List<FileItem> check = getOS().getFileService().listFiles(target);
        currentVirtualDir = target;
    }

    private void cmdMkdir(List<String> args) throws IOException {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        if (args.isEmpty()) {
            appendColored("Usage: mkdir <folder_name>\n", theme.error);
            return;
        }
        String p = resolvePath(args.get(0));
        boolean ok = getOS().getFileService().createDirectory(p);
        if (ok) {
            appendColored("Directory created: " + p + "\n", theme.success);
        } else {
            appendColored("Directory already exists or could not be created: " + p + "\n", theme.error);
        }
    }

    private void cmdTouch(List<String> args) throws IOException {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        if (args.isEmpty()) {
            appendColored("Usage: touch <filename>\n", theme.error);
            return;
        }
        String p = resolvePath(args.get(0));
        boolean ok = getOS().getFileService().createFile(p, "");
        if (ok) {
            appendColored("File created: " + p + "\n", theme.success);
        } else {
            appendColored("File already exists: " + p + "\n", theme.error);
        }
    }

    private void cmdCat(List<String> args) throws IOException {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        if (args.isEmpty()) {
            appendColored("Usage: cat <filename>\n", theme.error);
            return;
        }
        String p = resolvePath(args.get(0));
        String content = getOS().getFileService().readFile(p);
        appendColored(content + "\n", theme.textPrimary);
    }

    private void cmdWrite(List<String> args) throws IOException {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        if (args.size() < 2) {
            appendColored("Usage: write <filename> <content text...>\n", theme.error);
            return;
        }
        String p = resolvePath(args.get(0));
        String text = String.join(" ", args.subList(1, args.size()));
        getOS().getFileService().writeFile(p, text);
        appendColored("Wrote " + text.length() + " bytes to " + p + "\n", theme.success);
    }

    private void cmdSearch(List<String> args) throws IOException {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        if (args.isEmpty()) {
            appendColored("Usage: search <term>\n", theme.error);
            return;
        }
        String q = args.get(0);
        List<FileItem> res = getOS().getFileService().search(q);
        if (res.isEmpty()) {
            appendColored("No matching files found for '" + q + "'.\n", theme.textPrimary);
        } else {
            appendColored("Found " + res.size() + " matches:\n", theme.neonAccent);
            for (FileItem item : res) {
                appendColored("  " + item.getVirtualPath() + " [" + item.getFormattedSize() + "]\n", theme.textPrimary);
            }
        }
    }

    private void cmdRm(List<String> args) throws IOException {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        if (args.isEmpty()) {
            appendColored("Usage: rm <filename>\n", theme.error);
            return;
        }
        String p = resolvePath(args.get(0));
        getOS().getFileService().moveToTrash(p);
        appendColored("Moved to Recycle Bin: " + p + "\n", theme.success);
    }

    private void cmdCp(List<String> args) throws IOException {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        if (args.size() < 2) {
            appendColored("Usage: cp <source> <destination>\n", theme.error);
            return;
        }
        String src = resolvePath(args.get(0));
        String dest = resolvePath(args.get(1));
        getOS().getFileService().copy(src, dest);
        appendColored("Copied " + src + " -> " + dest + "\n", theme.success);
    }

    private void cmdMv(List<String> args) throws IOException {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        if (args.size() < 2) {
            appendColored("Usage: mv <source> <destination>\n", theme.error);
            return;
        }
        String src = resolvePath(args.get(0));
        String dest = resolvePath(args.get(1));
        getOS().getFileService().move(src, dest);
        appendColored("Moved " + src + " -> " + dest + "\n", theme.success);
    }

    private void cmdApps() {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        appendColored("REGISTERED APPLICATIONS:\n", theme.neonAccent);
        for (AppDescriptor desc : getOS().getApplicationManager().getRegisteredApps()) {
            appendColored(String.format("  %-12s : %s\n", desc.getId(), desc.getName()), theme.textPrimary);
        }
    }

    private void cmdOpen(List<String> args) {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        if (args.isEmpty()) {
            appendColored("Usage: open <appId | filename>\n", theme.error);
            return;
        }
        String target = args.get(0);
        AppDescriptor desc = getOS().getApplicationManager().getDescriptor(target.toLowerCase());
        if (desc != null) {
            getOS().getApplicationManager().launchApp(desc.getId());
            appendColored("Launched application: " + desc.getName() + "\n", theme.success);
        } else {
            // Check if it's a file
            try {
                String p = resolvePath(target);
                OSApplication np = getOS().getApplicationManager().launchApp("notepad");
                if (np instanceof NotepadApp) {
                    ((NotepadApp) np).openVirtualFile(p);
                    appendColored("Opened " + p + " in Notepad\n", theme.success);
                }
            } catch (Exception e) {
                appendColored("Cannot open: " + target + " (" + e.getMessage() + ")\n", theme.error);
            }
        }
    }

    private void cmdSysinfo() {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        javaos.services.SystemMetricsService sm = getOS().getSystemMetricsService();
        appendColored("=================== SYSTEM TELEMETRY ===================\n", theme.neonAccent);
        appendColored("  Platform: " + sm.getHostOsName() + "\n", theme.textPrimary);
        appendColored("  Java Runtime: " + sm.getJavaVersion() + "\n", theme.textPrimary);
        appendColored("  Uptime: " + sm.getFormattedUptime() + "\n", theme.textPrimary);
        appendColored("  JVM Processors: " + sm.getAvailableProcessors() + "\n", theme.textPrimary);
        appendColored("  Active JVM Threads: " + sm.getActiveThreadCount() + "\n", theme.textPrimary);
        appendColored("  JVM Heap Used: " + javaos.services.SystemMetricsService.formatBytes(sm.getJvmUsedMemory()) +
                " / " + javaos.services.SystemMetricsService.formatBytes(sm.getJvmTotalMemory()) +
                " (Max: " + javaos.services.SystemMetricsService.formatBytes(sm.getJvmMaxMemory()) + ")\n", theme.neonAccent);

        long hostRam = sm.getHostTotalPhysicalMemory();
        long freeRam = sm.getHostFreePhysicalMemory();
        if (hostRam > 0) {
            appendColored("  Host RAM: " + javaos.services.SystemMetricsService.formatBytes(hostRam - freeRam) +
                    " / " + javaos.services.SystemMetricsService.formatBytes(hostRam) + "\n", theme.textPrimary);
        }
        double hostCpu = sm.getHostCpuLoad();
        if (hostCpu >= 0) {
            appendColored(String.format("  Host CPU Load: %.1f%%\n", hostCpu), theme.textPrimary);
        }
        appendColored("========================================================\n", theme.neonAccent);
    }

    private void cmdTheme(List<String> args) {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        if (args.isEmpty()) {
            appendColored("Available themes: 'Cyberpunk Cyan', 'Synthwave Violet', 'Matrix Green', 'Solar Amber'\n", theme.textPrimary);
            return;
        }
        String name = String.join(" ", args);
        ThemeManager.getInstance().applyTheme(name);
        getOS().getSettingsService().setTheme(name);
        appendColored("Applied theme: " + name + "\n", theme.success);
    }

    private void cmdNetScan() {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        appendColored("⚡ INITIATING LOCAL NETWORK & SURROUNDING DEVICE RADAR SWEEP...\n", theme.neonAccent);
        NetworkScannerService scanner = getOS().getNetworkScannerService();
        appendColored(String.format("Interface Local IP: %s | Default Gateway: %s | Subnet: %s0/24\n",
                scanner.getLocalIp(), scanner.getGatewayIp(), scanner.getSubnetPrefix()), theme.textSecondary);
        appendColored("Scanning 254 subnet addresses & querying ARP cache table...\n", theme.textSecondary);

        new Thread(() -> {
            try {
                List<NetworkScannerService.DiscoveredDevice> devices = scanner.scanSurroundingDevices(null, null).get();
                SwingUtilities.invokeLater(() -> {
                    appendColored("\n--- DISCOVERED SURROUNDING DEVICES (" + devices.size() + " NODES) ---\n", theme.neonAccent);
                    appendColored(String.format("%-17s %-20s %-20s %-20s %-6s\n", "IP ADDRESS", "HOSTNAME", "MAC ADDRESS", "VENDOR", "PING"), theme.neonAccent);
                    appendColored("----------------------------------------------------------------------------------------\n", theme.textSecondary);
                    for (NetworkScannerService.DiscoveredDevice d : devices) {
                        String ping = d.latencyMs >= 0 ? d.latencyMs + "ms" : "<1ms";
                        appendColored(String.format("%-17s %-20s %-20s %-20s %-6s\n",
                                d.ip, truncate(d.hostname, 19), d.macAddress, truncate(d.vendor, 19), ping),
                                d.isGateway ? theme.secondaryAccent : (d.isLocalHost ? theme.neonAccent : theme.textPrimary));
                    }
                    appendColored("----------------------------------------------------------------------------------------\n", theme.textSecondary);
                    appendColored("Scan complete. Use 'open cyberdeck' for GUI radar and deep port auditing.\n\n", theme.success);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> appendColored("Network scan failed: " + ex.getMessage() + "\n", theme.error));
            }
        }).start();
    }

    private void cmdAirScan() {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        appendColored("📡 LISTENING TO RF AIRWAVES (ZERO WIRES / ZERO WI-FI REQUIRED)...\n", theme.neonAccent);
        appendColored("Scanning 802.11 beacon frames, hotspots, and Bluetooth RF broadcasts...\n", theme.textSecondary);

        new Thread(() -> {
            try {
                NetworkScannerService scanner = getOS().getNetworkScannerService();
                List<NetworkScannerService.WirelessAirDevice> devices = scanner.scanAirwaveDevices();
                SwingUtilities.invokeLater(() -> {
                    appendColored("\n--- SURROUNDING WIRELESS DEVICES DETECTED OVER THE AIR (" + devices.size() + " NODES) ---\n", theme.neonAccent);
                    appendColored(String.format("%-24s %-16s %-19s %-14s %-22s %-16s\n",
                            "DEVICE NAME", "RESOLVED IP", "MAC (BSSID)", "SIGNAL / RSSI", "ESTIMATED PROXIMITY", "PROTOCOL"), theme.neonAccent);
                    appendColored("─────────────────────────────────────────────────────────────────────────────────────────────────────────────\n", theme.textSecondary);
                    for (NetworkScannerService.WirelessAirDevice d : devices) {
                        // Build signal string: show RSSI dBm if available
                        String sigStr;
                        if (d.rssiDbm != Integer.MIN_VALUE) {
                            sigStr = d.signalPercent + "% (" + d.rssiDbm + " dBm)";
                        } else {
                            sigStr = d.signalPercent + "%";
                        }
                        String ipDisplay = (d.ipAddress != null && !d.ipAddress.isEmpty()) ? d.ipAddress : "No IP / RF Only";
                        if (ipDisplay.length() > 15) ipDisplay = ipDisplay.substring(0, 14) + "…";

                        // Highlight phone/hotspot in cyan, BT in violet, others in normal
                        boolean isDirect = d.protocol != null && d.protocol.contains("Direct");
                        boolean isBluetooth = d.protocol != null && d.protocol.contains("Bluetooth");
                        java.awt.Color rowColor = isDirect ? theme.neonAccent
                                : isBluetooth ? theme.secondaryAccent
                                : theme.textPrimary;
                        appendColored(String.format("%-24s %-16s %-19s %-14s %-22s %-16s\n",
                                truncate(d.name, 23), ipDisplay, d.macAddress, sigStr,
                                truncate(d.estimatedDistance, 21), d.protocol), rowColor);
                    }
                    appendColored("─────────────────────────────────────────────────────────────────────────────────────────────────────────────\n", theme.textSecondary);
                    appendColored("Airwave sweep complete. Type 'open cyberdeck' for full 360° proximity radar.\n\n", theme.success);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> appendColored("Airwave scan failed: " + ex.getMessage() + "\n", theme.error));
            }
        }).start();
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "Unknown";
        return s.length() <= maxLen ? s : s.substring(0, maxLen - 2) + "..";
    }

    private String resolvePath(String p) {
        if (p == null || p.trim().isEmpty()) return currentVirtualDir;
        String trimmed = p.trim();
        if (trimmed.startsWith("/")) {
            return trimmed;
        }
        if ("/".equals(currentVirtualDir)) {
            return "/" + trimmed;
        }
        return currentVirtualDir + "/" + trimmed;
    }

    private List<String> parseArguments(String input) {
        List<String> list = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (Character.isWhitespace(c) && !inQuotes) {
                if (current.length() > 0) {
                    list.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            list.add(current.toString());
        }
        return list;
    }

    /**
     * Appends styled (colored) text to the JTextPane output area.
     */
    private void appendColored(String text, Color color) {
        SimpleAttributeSet attrs = new SimpleAttributeSet();
        StyleConstants.setForeground(attrs, color);
        try {
            doc.insertString(doc.getLength(), text, attrs);
        } catch (BadLocationException ignored) {}
    }

    // Legacy helper kept for any internal callers
    private void appendLine(String text, String styleType) {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        Color color;
        switch (styleType) {
            case "cyan":    color = theme.neonAccent; break;
            case "prompt":  color = theme.secondaryAccent; break;
            case "success": color = theme.success; break;
            case "error":   color = theme.error; break;
            default:        color = theme.textPrimary; break;
        }
        appendColored(text + "\n", color);
    }
}

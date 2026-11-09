package javaos.apps;

import javaos.ui.Theme;
import javaos.ui.ThemeManager;
import javaos.ui.components.CyberpunkIcons;
import javaos.ui.components.NeonButton;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.util.*;
import java.util.List;

/**
 * CyberArcade Retro Mini-Game Center for IMPERION OS.
 * Features:
 *  - Game 1: Authentic Cyberpunk 2077 Breach Protocol Hex Matrix Hacking Puzzle
 *    with alternating Row/Col mechanics, buffer slots, sequence daemons & countdown timer
 *  - Game 2: Tron Neon Grid Runner (Light Cycle Snake) with glowing particle trails,
 *    energy nodes, and high score telemetry
 */
public class ArcadeApp extends OSApplication {

    public ArcadeApp() {
        super(new AppDescriptor(
                "arcade",
                "Arcade Studio",
                "Breach Protocol & Tron Neon Runner arcade",
                AppDescriptor.Category.GAMES,
                CyberpunkIcons.createIcon("GAMEPAD", 20),
                false
        ));
    }

    @Override
    public Dimension getPreferredWindowSize() {
        return new Dimension(860, 600);
    }

    @Override
    public JComponent createContent() {
        JTabbedPane tabs = new JTabbedPane();
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        tabs.setBackground(theme.panel);
        tabs.setForeground(theme.textPrimary);
        tabs.setFont(theme.fontBold);

        tabs.addTab("  BREACH PROTOCOL (Hacking Puzzle)  ", CyberpunkIcons.createIcon("SHIELD", 14), new BreachProtocolPanel());
        tabs.addTab("  NEON GRID RUNNER (Tron Light Cycle)  ", CyberpunkIcons.createIcon("GAMEPAD", 14), new NeonRunnerPanel());

        return tabs;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // GAME 1: CYBERPUNK 2077 BREACH PROTOCOL HACKING PUZZLE
    // ══════════════════════════════════════════════════════════════════════════
    private class BreachProtocolPanel extends JPanel {
        private static final int GRID_SIZE = 5;
        private static final int BUFFER_CAPACITY = 6;
        private static final String[] HEX_POOL = {"1C", "55", "BD", "E9", "7A", "FF"};

        private String[][] matrix = new String[GRID_SIZE][GRID_SIZE];
        private boolean[][] used = new boolean[GRID_SIZE][GRID_SIZE];
        private List<String> buffer = new ArrayList<>();

        // Target sequences
        private List<String> target1 = List.of("1C", "BD");
        private List<String> target2 = List.of("E9", "55", "7A");
        private List<String> target3 = List.of("BD", "1C", "FF");

        private boolean rowSelection = true; // true=row, false=col
        private int activeIndex = 0; // index of active row or col
        private int timeLeft = 30;
        private javax.swing.Timer countdownTimer;
        private boolean gameActive = false;

        private JButton[][] cellButtons = new JButton[GRID_SIZE][GRID_SIZE];
        private JPanel bufferPanel;
        private JLabel timerLabel;
        private JLabel statusLabel;
        private JLabel daemon1Lbl;
        private JLabel daemon2Lbl;
        private JLabel daemon3Lbl;

        BreachProtocolPanel() {
            setLayout(new BorderLayout(10, 10));
            setOpaque(true);
            Theme theme = ThemeManager.getInstance().getCurrentTheme();
            setBackground(theme.background);
            setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));

            // Top Header & Countdown
            JPanel top = new JPanel(new BorderLayout());
            top.setOpaque(false);

            JPanel titleCol = new JPanel(new GridLayout(2, 1));
            titleCol.setOpaque(false);
            JLabel title = new JLabel("ICE BREACH PROTOCOL // DAEMON UPLOAD");
            title.setFont(theme.fontBold);
            title.setForeground(theme.neonAccent);
            statusLabel = new JLabel("Upload sequence into buffer to bypass subnet security.");
            statusLabel.setFont(theme.fontSmall);
            statusLabel.setForeground(theme.textSecondary);
            titleCol.add(title);
            titleCol.add(statusLabel);
            top.add(titleCol, BorderLayout.WEST);

            JPanel rightTop = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
            rightTop.setOpaque(false);
            timerLabel = new JLabel("TIME: 30s");
            timerLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
            timerLabel.setForeground(theme.warning);
            JButton btnReset = new JButton("Reset Breach");
            btnReset.setFont(theme.fontSmall);
            btnReset.setBackground(theme.panel);
            btnReset.setForeground(theme.neonAccent);
            btnReset.addActionListener(e -> startNewBreach());
            rightTop.add(timerLabel);
            rightTop.add(btnReset);
            top.add(rightTop, BorderLayout.EAST);

            add(top, BorderLayout.NORTH);

            // Center Matrix Grid & Daemons Split
            JPanel center = new JPanel(new GridLayout(1, 2, 14, 0));
            center.setOpaque(false);

            // 5x5 Code Matrix
            JPanel matrixPanel = new JPanel(new GridLayout(GRID_SIZE, GRID_SIZE, 6, 6));
            matrixPanel.setOpaque(true);
            matrixPanel.setBackground(theme.panel);
            matrixPanel.setBorder(BorderFactory.createTitledBorder(
                    BorderFactory.createLineBorder(theme.secondaryAccent),
                    "CODE MATRIX // SELECT NEXT HEX", 0, 0, theme.fontSmall, theme.neonAccent
            ));

            for (int r = 0; r < GRID_SIZE; r++) {
                for (int c = 0; c < GRID_SIZE; c++) {
                    int fr = r, fc = c;
                    JButton btn = new JButton();
                    btn.setFont(new Font("Consolas", Font.BOLD, 16));
                    btn.setFocusPainted(false);
                    btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    btn.addActionListener(e -> selectCell(fr, fc));
                    cellButtons[r][c] = btn;
                    matrixPanel.add(btn);
                }
            }
            center.add(matrixPanel);

            // Right: Daemons & Buffer
            JPanel rightPanel = new JPanel();
            rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
            rightPanel.setOpaque(false);

            // Daemons Targets Card
            JPanel daemonCard = new JPanel(new GridLayout(3, 1, 4, 8));
            daemonCard.setOpaque(true);
            daemonCard.setBackground(theme.panel);
            daemonCard.setBorder(BorderFactory.createTitledBorder(
                    BorderFactory.createLineBorder(theme.secondaryAccent),
                    "TARGET DAEMONS TO UPLOAD", 0, 0, theme.fontSmall, theme.neonAccent
            ));

            daemon1Lbl = new JLabel("  [DATAMINE_V1] ➔ 1C  BD  (+100 Credits)");
            daemon1Lbl.setFont(new Font("Consolas", Font.BOLD, 13));
            daemon1Lbl.setForeground(theme.textPrimary);

            daemon2Lbl = new JLabel("  [DATAMINE_V2] ➔ E9  55  7A  (+250 Credits)");
            daemon2Lbl.setFont(new Font("Consolas", Font.BOLD, 13));
            daemon2Lbl.setForeground(theme.textPrimary);

            daemon3Lbl = new JLabel("  [DATAMINE_V3] ➔ BD  1C  FF  (+500 Credits)");
            daemon3Lbl.setFont(new Font("Consolas", Font.BOLD, 13));
            daemon3Lbl.setForeground(theme.textPrimary);

            daemonCard.add(daemon1Lbl);
            daemonCard.add(daemon2Lbl);
            daemonCard.add(daemon3Lbl);
            rightPanel.add(daemonCard);

            rightPanel.add(Box.createVerticalStrut(10));

            // Buffer display
            JPanel bufCard = new JPanel(new BorderLayout(0, 6));
            bufCard.setOpaque(true);
            bufCard.setBackground(theme.panel);
            bufCard.setBorder(BorderFactory.createTitledBorder(
                    BorderFactory.createLineBorder(theme.secondaryAccent),
                    "MEMORY BUFFER (MAX " + BUFFER_CAPACITY + ")", 0, 0, theme.fontSmall, theme.neonAccent
            ));

            bufferPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
            bufferPanel.setOpaque(false);
            bufCard.add(bufferPanel, BorderLayout.CENTER);
            rightPanel.add(bufCard);

            center.add(rightPanel);
            add(center, BorderLayout.CENTER);

            startNewBreach();
        }

        private void startNewBreach() {
            Random rnd = new Random();
            for (int r = 0; r < GRID_SIZE; r++) {
                for (int c = 0; c < GRID_SIZE; c++) {
                    matrix[r][c] = HEX_POOL[rnd.nextInt(HEX_POOL.length)];
                    used[r][c] = false;
                }
            }

            buffer.clear();
            rowSelection = true;
            activeIndex = 0;
            timeLeft = 35;
            gameActive = true;

            if (countdownTimer != null) countdownTimer.stop();
            countdownTimer = new javax.swing.Timer(1000, e -> {
                timeLeft--;
                timerLabel.setText("TIME: " + timeLeft + "s");
                if (timeLeft <= 0) {
                    countdownTimer.stop();
                    gameActive = false;
                    statusLabel.setText("BREACH FAILED // ICE LOCKED DOWN SUBNET");
                    statusLabel.setForeground(ThemeManager.getInstance().getCurrentTheme().error);
                }
            });
            countdownTimer.start();

            statusLabel.setText("Breach active. Pick an unlocked node from the highlighted row.");
            statusLabel.setForeground(ThemeManager.getInstance().getCurrentTheme().neonAccent);

            updateMatrixView();
            updateBufferView();
        }

        private void selectCell(int r, int c) {
            if (!gameActive || used[r][c]) return;

            // Check if cell is in the currently active row or col
            if (rowSelection && r != activeIndex) return;
            if (!rowSelection && c != activeIndex) return;

            used[r][c] = true;
            buffer.add(matrix[r][c]);

            // Alternate row/col
            if (rowSelection) {
                rowSelection = false;
                activeIndex = c;
            } else {
                rowSelection = true;
                activeIndex = r;
            }

            updateMatrixView();
            updateBufferView();
            checkBreachProgress();
        }

        private void updateMatrixView() {
            Theme theme = ThemeManager.getInstance().getCurrentTheme();
            for (int r = 0; r < GRID_SIZE; r++) {
                for (int c = 0; c < GRID_SIZE; c++) {
                    JButton btn = cellButtons[r][c];
                    btn.setText(matrix[r][c]);

                    if (used[r][c]) {
                        btn.setBackground(new Color(20, 24, 34));
                        btn.setForeground(new Color(71, 85, 105));
                        btn.setBorder(BorderFactory.createLineBorder(new Color(30, 41, 59)));
                        btn.setEnabled(false);
                    } else {
                        boolean isActiveGuide = (rowSelection && r == activeIndex) || (!rowSelection && c == activeIndex);
                        if (isActiveGuide) {
                            btn.setBackground(theme.elevatedPanel);
                            btn.setForeground(theme.neonAccent);
                            btn.setBorder(BorderFactory.createLineBorder(theme.neonAccent, 1));
                            btn.setEnabled(true);
                        } else {
                            btn.setBackground(theme.panel);
                            btn.setForeground(new Color(148, 163, 184));
                            btn.setBorder(BorderFactory.createLineBorder(theme.secondaryAccent));
                            btn.setEnabled(false);
                        }
                    }
                }
            }
        }

        private void updateBufferView() {
            bufferPanel.removeAll();
            Theme theme = ThemeManager.getInstance().getCurrentTheme();

            for (int i = 0; i < BUFFER_CAPACITY; i++) {
                JLabel slot = new JLabel();
                slot.setPreferredSize(new Dimension(38, 38));
                slot.setHorizontalAlignment(SwingConstants.CENTER);
                slot.setFont(new Font("Consolas", Font.BOLD, 15));

                if (i < buffer.size()) {
                    slot.setText(buffer.get(i));
                    slot.setForeground(theme.neonAccent);
                    slot.setBackground(theme.elevatedPanel);
                    slot.setOpaque(true);
                    slot.setBorder(BorderFactory.createLineBorder(theme.neonAccent, 1));
                } else {
                    slot.setText("[]");
                    slot.setForeground(new Color(71, 85, 105));
                    slot.setBackground(theme.panel);
                    slot.setOpaque(true);
                    slot.setBorder(BorderFactory.createLineBorder(theme.secondaryAccent));
                }
                bufferPanel.add(slot);
            }
            bufferPanel.revalidate();
            bufferPanel.repaint();
        }

        private void checkBreachProgress() {
            String bufStr = String.join("", buffer);
            String seq1 = String.join("", target1);
            String seq2 = String.join("", target2);
            String seq3 = String.join("", target3);

            boolean d1 = bufStr.contains(seq1);
            boolean d2 = bufStr.contains(seq2);
            boolean d3 = bufStr.contains(seq3);

            Theme theme = ThemeManager.getInstance().getCurrentTheme();
            if (d1) daemon1Lbl.setForeground(theme.success);
            if (d2) daemon2Lbl.setForeground(theme.success);
            if (d3) daemon3Lbl.setForeground(theme.success);

            if ((d1 && d2) || (d1 && d3) || (d2 && d3) || buffer.size() >= BUFFER_CAPACITY) {
                if (countdownTimer != null) countdownTimer.stop();
                gameActive = false;

                int completed = (d1 ? 1 : 0) + (d2 ? 1 : 0) + (d3 ? 1 : 0);
                if (completed > 0) {
                    statusLabel.setText("BREACH SUCCESSFUL // " + completed + " DAEMON(S) UPLOADED // ICE COMPROMISED!");
                    statusLabel.setForeground(theme.success);
                    if (getOS() != null && getOS().getSoundService() != null) {
                        getOS().getSoundService().playSuccess();
                    }
                } else {
                    statusLabel.setText("BUFFER FULL // TARGET DAEMONS MISSED");
                    statusLabel.setForeground(theme.error);
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // GAME 2: TRON NEON RUNNER (LIGHT CYCLE / SNAKE)
    // ══════════════════════════════════════════════════════════════════════════
    private class NeonRunnerPanel extends JPanel {
        private static final int CELL_SIZE = 16;
        private final LinkedList<Point> snake = new LinkedList<>();
        private Point food = new Point(12, 12);
        private int dirX = 1, dirY = 0;
        private int score = 0;
        private int highScore = 0;
        private boolean isRunning = false;
        private javax.swing.Timer gameTimer;

        NeonRunnerPanel() {
            setLayout(new BorderLayout());
            setOpaque(true);
            setBackground(new Color(10, 15, 26));

            JPanel top = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 8));
            top.setBackground(new Color(15, 23, 42));

            JLabel scoreLbl = new JLabel("SCORE: 0  |  HIGH: 0");
            scoreLbl.setFont(new Font("Segoe UI", Font.BOLD, 14));
            scoreLbl.setForeground(Color.CYAN);
            top.add(scoreLbl);

            JButton btnStart = new JButton("Start Light Cycle");
            btnStart.setBackground(new Color(30, 41, 59));
            btnStart.setForeground(Color.WHITE);
            btnStart.setFocusable(false);
            btnStart.addActionListener(e -> startGame(scoreLbl));
            top.add(btnStart);

            add(top, BorderLayout.NORTH);

            GameCanvas canvas = new GameCanvas();
            add(canvas, BorderLayout.CENTER);

            // Keyboard navigation
            setFocusable(true);
            addKeyListener(new KeyAdapter() {
                @Override public void keyPressed(KeyEvent e) {
                    int k = e.getKeyCode();
                    if ((k == KeyEvent.VK_UP || k == KeyEvent.VK_W) && dirY != 1) { dirX = 0; dirY = -1; }
                    else if ((k == KeyEvent.VK_DOWN || k == KeyEvent.VK_S) && dirY != -1) { dirX = 0; dirY = 1; }
                    else if ((k == KeyEvent.VK_LEFT || k == KeyEvent.VK_A) && dirX != 1) { dirX = -1; dirY = 0; }
                    else if ((k == KeyEvent.VK_RIGHT || k == KeyEvent.VK_D) && dirX != -1) { dirX = 1; dirY = 0; }
                }
            });

            gameTimer = new javax.swing.Timer(75, e -> {
                if (!isRunning) return;
                Point head = snake.getFirst();
                Point next = new Point(head.x + dirX, head.y + dirY);

                int maxW = canvas.getWidth() / CELL_SIZE;
                int maxH = canvas.getHeight() / CELL_SIZE;
                if (maxW <= 0) maxW = 30;
                if (maxH <= 0) maxH = 20;

                // Screen wrap
                if (next.x < 0) next.x = maxW - 1;
                else if (next.x >= maxW) next.x = 0;
                if (next.y < 0) next.y = maxH - 1;
                else if (next.y >= maxH) next.y = 0;

                // Collision with self
                if (snake.contains(next)) {
                    isRunning = false;
                    gameTimer.stop();
                    JOptionPane.showMessageDialog(canvas, "Light Cycle De-rezzed!\nFinal Score: " + score, "Game Over", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }

                snake.addFirst(next);
                if (next.equals(food)) {
                    score += 100;
                    if (score > highScore) highScore = score;
                    scoreLbl.setText("SCORE: " + score + "  |  HIGH: " + highScore);
                    spawnFood(maxW, maxH);
                } else {
                    snake.removeLast();
                }

                canvas.repaint();
            });
        }

        private void startGame(JLabel scoreLbl) {
            snake.clear();
            snake.add(new Point(10, 10));
            snake.add(new Point(9, 10));
            snake.add(new Point(8, 10));
            dirX = 1; dirY = 0;
            score = 0;
            scoreLbl.setText("SCORE: 0  |  HIGH: " + highScore);
            isRunning = true;
            gameTimer.start();
            requestFocusInWindow();
        }

        private void spawnFood(int maxW, int maxH) {
            Random r = new Random();
            food = new Point(r.nextInt(Math.max(1, maxW)), r.nextInt(Math.max(1, maxH)));
        }

        private class GameCanvas extends JPanel {
            GameCanvas() {
                setOpaque(true);
                setBackground(new Color(11, 15, 25));
            }

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int w = getWidth();
                int h = getHeight();

                // Cyber Grid
                g2.setColor(new Color(30, 41, 59, 60));
                for (int x = 0; x < w; x += CELL_SIZE) g2.drawLine(x, 0, x, h);
                for (int y = 0; y < h; y += CELL_SIZE) g2.drawLine(0, y, w, y);

                // Food (Data Core)
                g2.setColor(new Color(244, 63, 94)); // Neon Pink
                g2.fillOval(food.x * CELL_SIZE + 2, food.y * CELL_SIZE + 2, CELL_SIZE - 4, CELL_SIZE - 4);
                g2.setColor(Color.WHITE);
                g2.fillOval(food.x * CELL_SIZE + 5, food.y * CELL_SIZE + 5, CELL_SIZE - 10, CELL_SIZE - 10);

                // Light Cycle Trail
                int idx = 0;
                for (Point p : snake) {
                    if (idx == 0) {
                        g2.setColor(Color.WHITE); // Head
                    } else {
                        g2.setColor(new Color(34, 211, 238, Math.max(70, 255 - idx * 4))); // Cyan trail
                    }
                    g2.fillRoundRect(p.x * CELL_SIZE + 1, p.y * CELL_SIZE + 1, CELL_SIZE - 2, CELL_SIZE - 2, 4, 4);
                    idx++;
                }

                g2.dispose();
            }
        }
    }
}

package javaos.apps;

import javaos.ui.Theme;
import javaos.ui.ThemeManager;
import javaos.ui.components.CyberpunkIcons;
import javaos.ui.components.NeonButton;
import javaos.ui.components.NeonScrollBarUI;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.text.DecimalFormat;

/**
 * Cyberpunk Neon Calculator application.
 * Supports basic arithmetic, scientific functions, history panel, and full keyboard input.
 */
public class CalculatorApp extends OSApplication {

    private JLabel display;
    private JLabel historyLabel;
    private JTextArea historyArea;
    private double firstOperand = 0;
    private String pendingOperator = "";
    private boolean startNewNumber = true;
    private final DecimalFormat format = new DecimalFormat("#,##0.########");

    public CalculatorApp() {
        super(new AppDescriptor(
                "calculator",
                "Calculator",
                "Futuristic arithmetic and calculation console",
                AppDescriptor.Category.UTILITIES,
                CyberpunkIcons.createIcon("CALCULATOR", 20),
                false
        ));
    }

    @Override
    public Dimension getPreferredWindowSize() {
        return new Dimension(520, 480);
    }

    @Override
    public JComponent createContent() {
        JPanel outerPanel = new JPanel(new BorderLayout(8, 0));
        outerPanel.setOpaque(false);
        outerPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        Theme theme = ThemeManager.getInstance().getCurrentTheme();

        // Main calculator panel
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setOpaque(false);

        // Top display panel
        JPanel displayPanel = new JPanel(new GridLayout(2, 1));
        displayPanel.setOpaque(true);
        displayPanel.setBackground(theme.background);
        displayPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(theme.neonAccent, 1),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));

        historyLabel = new JLabel(" ");
        historyLabel.setFont(theme.fontSmall);
        historyLabel.setForeground(theme.textSecondary);
        historyLabel.setHorizontalAlignment(SwingConstants.RIGHT);

        display = new JLabel("0");
        display.setFont(new Font("Consolas", Font.BOLD, 28));
        display.setForeground(theme.neonAccent);
        display.setHorizontalAlignment(SwingConstants.RIGHT);

        displayPanel.add(historyLabel);
        displayPanel.add(display);
        panel.add(displayPanel, BorderLayout.NORTH);

        // Keypad grid: 6 rows (1 scientific + 5 standard)
        JPanel keypad = new JPanel(new GridLayout(6, 4, 6, 6));
        keypad.setOpaque(false);

        // Row 0: Scientific functions
        String[] sciButtons = {"√", "x²", "%", "1/x"};
        for (String text : sciButtons) {
            NeonButton btn = new NeonButton(text, null, NeonButton.Style.DEFAULT);
            btn.setFont(new Font("Segoe UI", Font.BOLD, 14));
            btn.setForeground(theme.secondaryAccent);
            btn.addActionListener(e -> handleButtonPress(text));
            keypad.add(btn);
        }

        // Row 1: C, CE, DEL, /
        // Rows 2-5: Standard buttons
        String[] buttons = {
                "C", "CE", "DEL", "/",
                "7", "8", "9", "*",
                "4", "5", "6", "-",
                "1", "2", "3", "+",
                "±", "0", ".", "="
        };

        for (String text : buttons) {
            NeonButton btn;
            if ("=".equals(text)) {
                btn = new NeonButton(text, null, NeonButton.Style.PRIMARY);
            } else if ("C".equals(text) || "CE".equals(text) || "DEL".equals(text)) {
                btn = new NeonButton(text, null, NeonButton.Style.DANGER);
            } else if ("+".equals(text) || "-".equals(text) || "*".equals(text) || "/".equals(text)) {
                btn = new NeonButton(text, null, NeonButton.Style.DEFAULT);
                btn.setForeground(theme.neonAccent);
            } else {
                btn = new NeonButton(text, null, NeonButton.Style.DEFAULT);
            }

            btn.setFont(new Font("Segoe UI", Font.BOLD, 16));
            btn.addActionListener(e -> handleButtonPress(text));
            keypad.add(btn);
        }

        panel.add(keypad, BorderLayout.CENTER);
        outerPanel.add(panel, BorderLayout.CENTER);

        // Right-side history panel
        JPanel historyPanel = new JPanel(new BorderLayout(0, 4));
        historyPanel.setOpaque(false);
        historyPanel.setPreferredSize(new Dimension(160, 0));

        JLabel histTitle = new JLabel("History");
        histTitle.setFont(theme.fontBold);
        histTitle.setForeground(theme.neonAccent);
        histTitle.setBorder(BorderFactory.createEmptyBorder(0, 4, 4, 0));
        historyPanel.add(histTitle, BorderLayout.NORTH);

        historyArea = new JTextArea();
        historyArea.setEditable(false);
        historyArea.setFont(theme.fontSmall);
        historyArea.setBackground(theme.panel);
        historyArea.setForeground(theme.textPrimary);
        historyArea.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        historyArea.setLineWrap(true);
        historyArea.setWrapStyleWord(true);

        JScrollPane histScroll = new JScrollPane(historyArea);
        histScroll.getVerticalScrollBar().setUI(new NeonScrollBarUI());
        histScroll.setBorder(BorderFactory.createLineBorder(theme.secondaryAccent));
        historyPanel.add(histScroll, BorderLayout.CENTER);

        NeonButton btnClearHistory = new NeonButton("Clear", null, NeonButton.Style.DANGER);
        btnClearHistory.setFont(theme.fontSmall);
        btnClearHistory.addActionListener(e -> historyArea.setText(""));
        historyPanel.add(btnClearHistory, BorderLayout.SOUTH);

        outerPanel.add(historyPanel, BorderLayout.EAST);

        // Keyboard listener on outer panel
        outerPanel.setFocusable(true);
        outerPanel.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                char ch = e.getKeyChar();
                if (Character.isDigit(ch) || ch == '.') {
                    handleButtonPress(String.valueOf(ch));
                } else if (ch == '+' || ch == '-' || ch == '*' || ch == '/') {
                    handleButtonPress(String.valueOf(ch));
                } else if (ch == '=' || e.getKeyCode() == KeyEvent.VK_ENTER) {
                    handleButtonPress("=");
                }
            }

            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
                    handleButtonPress("DEL");
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    handleButtonPress("C");
                } else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    handleButtonPress("=");
                }
            }
        });

        SwingUtilities.invokeLater(outerPanel::requestFocusInWindow);
        return outerPanel;
    }

    private void handleButtonPress(String cmd) {
        switch (cmd) {
            case "0": case "1": case "2": case "3": case "4":
            case "5": case "6": case "7": case "8": case "9":
                if (startNewNumber || "0".equals(display.getText())) {
                    display.setText(cmd);
                    startNewNumber = false;
                } else {
                    display.setText(display.getText() + cmd);
                }
                break;

            case ".":
                if (startNewNumber) {
                    display.setText("0.");
                    startNewNumber = false;
                } else if (!display.getText().contains(".")) {
                    display.setText(display.getText() + ".");
                }
                break;

            case "±":
                try {
                    double val = Double.parseDouble(display.getText().replace(",", ""));
                    display.setText(format.format(-val));
                } catch (NumberFormatException ignored) {}
                break;

            case "DEL":
                if (!startNewNumber) {
                    String cur = display.getText();
                    if (cur.length() > 1) {
                        display.setText(cur.substring(0, cur.length() - 1));
                    } else {
                        display.setText("0");
                        startNewNumber = true;
                    }
                }
                break;

            case "CE":
                display.setText("0");
                startNewNumber = true;
                break;

            case "C":
                display.setText("0");
                firstOperand = 0;
                pendingOperator = "";
                historyLabel.setText(" ");
                startNewNumber = true;
                break;

            case "+": case "-": case "*": case "/":
                applyOperator(cmd);
                break;

            case "=":
                evaluateEquals();
                break;

            // Scientific functions
            case "√":
                try {
                    double val = Double.parseDouble(display.getText().replace(",", ""));
                    if (val < 0) {
                        display.setText("Error: √neg");
                    } else {
                        double result = Math.sqrt(val);
                        String expr = "√(" + format.format(val) + ")";
                        appendHistory(expr + " = " + format.format(result));
                        display.setText(format.format(result));
                        startNewNumber = true;
                    }
                } catch (NumberFormatException ignored) {}
                break;

            case "x²":
                try {
                    double val = Double.parseDouble(display.getText().replace(",", ""));
                    double result = val * val;
                    String expr = "(" + format.format(val) + ")²";
                    appendHistory(expr + " = " + format.format(result));
                    display.setText(format.format(result));
                    startNewNumber = true;
                } catch (NumberFormatException ignored) {}
                break;

            case "%":
                try {
                    double val = Double.parseDouble(display.getText().replace(",", ""));
                    double result = val / 100.0;
                    String expr = format.format(val) + "%";
                    appendHistory(expr + " = " + format.format(result));
                    display.setText(format.format(result));
                    startNewNumber = true;
                } catch (NumberFormatException ignored) {}
                break;

            case "1/x":
                try {
                    double val = Double.parseDouble(display.getText().replace(",", ""));
                    if (val == 0.0) {
                        display.setText("Error: Div by 0");
                        startNewNumber = true;
                    } else {
                        double result = 1.0 / val;
                        String expr = "1/(" + format.format(val) + ")";
                        appendHistory(expr + " = " + format.format(result));
                        display.setText(format.format(result));
                        startNewNumber = true;
                    }
                } catch (NumberFormatException ignored) {}
                break;
        }
    }

    private void applyOperator(String op) {
        try {
            double currentVal = Double.parseDouble(display.getText().replace(",", ""));
            if (!pendingOperator.isEmpty() && !startNewNumber) {
                firstOperand = calculate(firstOperand, currentVal, pendingOperator);
                display.setText(format.format(firstOperand));
            } else {
                firstOperand = currentVal;
            }
            pendingOperator = op;
            historyLabel.setText(format.format(firstOperand) + " " + op);
            startNewNumber = true;
        } catch (ArithmeticException ae) {
            display.setText("Error: Div by 0");
            startNewNumber = true;
            pendingOperator = "";
        } catch (NumberFormatException ignored) {}
    }

    private void evaluateEquals() {
        if (pendingOperator.isEmpty()) return;
        try {
            double secondVal = Double.parseDouble(display.getText().replace(",", ""));
            String expr = format.format(firstOperand) + " " + pendingOperator + " " + format.format(secondVal);
            historyLabel.setText(expr + " =");
            double result = calculate(firstOperand, secondVal, pendingOperator);
            display.setText(format.format(result));
            appendHistory(expr + " = " + format.format(result));
            firstOperand = result;
            pendingOperator = "";
            startNewNumber = true;
        } catch (ArithmeticException ae) {
            display.setText("Error: Div by 0");
            startNewNumber = true;
            pendingOperator = "";
        } catch (NumberFormatException ignored) {}
    }

    private void appendHistory(String entry) {
        if (historyArea != null) {
            historyArea.append(entry + "\n");
            // Scroll to bottom
            historyArea.setCaretPosition(historyArea.getDocument().getLength());
        }
    }

    private double calculate(double a, double b, String op) {
        switch (op) {
            case "+": return a + b;
            case "-": return a - b;
            case "*": return a * b;
            case "/":
                if (b == 0.0) {
                    throw new ArithmeticException("Division by zero");
                }
                return a / b;
            default: return b;
        }
    }
}

package javaos.ui.components;

import javaos.ui.Theme;
import javaos.ui.ThemeManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.geom.RoundRectangle2D;

/**
 * Reusable cyberpunk neon text input field with placeholder and glowing focus border.
 */
public class NeonTextField extends JTextField {

    private String placeholder = "";
    private boolean isFocused = false;
    private int cornerRadius = 8;

    public NeonTextField() {
        this("", 15);
    }

    public NeonTextField(int columns) {
        this("", columns);
    }

    public NeonTextField(String text, int columns) {
        super(text, columns);
        initUI();
    }

    private void initUI() {
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        setCaretColor(Color.WHITE);

        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        setFont(theme.fontRegular);
        setForeground(theme.textPrimary);
        setBackground(theme.panel);

        addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                isFocused = true;
                repaint();
            }

            @Override
            public void focusLost(FocusEvent e) {
                isFocused = false;
                repaint();
            }
        });
    }

    public void setPlaceholder(String placeholder) {
        this.placeholder = placeholder == null ? "" : placeholder;
        repaint();
    }

    public String getPlaceholder() {
        return placeholder;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        int w = getWidth();
        int h = getHeight();

        // Background
        g2.setColor(theme.panel);
        g2.fill(new RoundRectangle2D.Float(1, 1, w - 2, h - 2, cornerRadius, cornerRadius));

        // Border glow
        Color borderColor = isFocused ? theme.neonAccent : theme.secondaryAccent;
        if (isFocused) {
            g2.setColor(new Color(theme.neonAccent.getRed(), theme.neonAccent.getGreen(), theme.neonAccent.getBlue(), 35));
            g2.fill(new RoundRectangle2D.Float(2, 2, w - 4, h - 4, cornerRadius, cornerRadius));
        }

        g2.setColor(borderColor);
        g2.setStroke(new BasicStroke(isFocused ? 1.5f : 1.0f));
        g2.draw(new RoundRectangle2D.Float(1.5f, 1.5f, w - 3, h - 3, cornerRadius, cornerRadius));

        // Placeholder
        if (getText().isEmpty() && !placeholder.isEmpty() && !isFocused) {
            g2.setFont(getFont());
            g2.setColor(theme.textSecondary);
            FontMetrics fm = g2.getFontMetrics();
            int py = (h - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(placeholder, 10, py);
        }

        g2.dispose();
        super.paintComponent(g);
    }
}

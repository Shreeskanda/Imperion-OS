package javaos.ui.components;

import javaos.ui.Theme;
import javaos.ui.ThemeManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;

/**
 * Reusable cyberpunk neon button with hover glow and press feedback.
 */
public class NeonButton extends JButton {

    public enum Style {
        DEFAULT, PRIMARY, DANGER, SUCCESS
    }

    private final Style style;
    private boolean isHovered = false;
    private boolean isPressed = false;
    private int cornerRadius = 8;

    public NeonButton(String text) {
        this(text, null, Style.DEFAULT);
    }

    public NeonButton(String text, Icon icon) {
        this(text, icon, Style.DEFAULT);
    }

    public NeonButton(String text, Icon icon, Style style) {
        super(text, icon);
        this.style = style == null ? Style.DEFAULT : style;
        initUI();
    }

    private void initUI() {
        setOpaque(false);
        setContentAreaFilled(false);
        setFocusPainted(false);
        setBorderPainted(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setMargin(new Insets(6, 14, 6, 14));

        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        setFont(theme.fontBold);
        setForeground(theme.textPrimary);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (isEnabled()) {
                    isHovered = true;
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                isHovered = false;
                isPressed = false;
                repaint();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (isEnabled()) {
                    isPressed = true;
                    repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (isEnabled()) {
                    isPressed = false;
                    repaint();
                }
            }
        });
    }

    public void setCornerRadius(int radius) {
        this.cornerRadius = radius;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        int w = getWidth();
        int h = getHeight();

        Color baseColor;
        Color accentBorder;

        switch (style) {
            case PRIMARY:
                baseColor = isHovered ? theme.elevatedPanel : theme.panel;
                accentBorder = theme.neonAccent;
                break;
            case DANGER:
                baseColor = isHovered ? new Color(0x38, 0x14, 0x18) : new Color(0x24, 0x0C, 0x10);
                accentBorder = theme.error;
                break;
            case SUCCESS:
                baseColor = isHovered ? new Color(0x0C, 0x30, 0x1E) : new Color(0x08, 0x20, 0x14);
                accentBorder = theme.success;
                break;
            case DEFAULT:
            default:
                baseColor = isHovered ? theme.elevatedPanel : theme.panel;
                accentBorder = isHovered ? theme.neonAccent : theme.secondaryAccent;
                break;
        }

        if (!isEnabled()) {
            baseColor = new Color(theme.panel.getRed(), theme.panel.getGreen(), theme.panel.getBlue(), 120);
            accentBorder = new Color(theme.textSecondary.getRed(), theme.textSecondary.getGreen(), theme.textSecondary.getBlue(), 60);
        }

        // Fill background
        RoundRectangle2D.Float shape = new RoundRectangle2D.Float(1, 1, w - 2, h - 2, cornerRadius, cornerRadius);
        g2.setColor(baseColor);
        g2.fill(shape);

        // Hover subtle neon glow
        if (isHovered && isEnabled()) {
            g2.setColor(new Color(accentBorder.getRed(), accentBorder.getGreen(), accentBorder.getBlue(), 40));
            g2.fill(new RoundRectangle2D.Float(2, 2, w - 4, h - 4, cornerRadius, cornerRadius));
        }

        // Border
        g2.setColor(accentBorder);
        g2.setStroke(new BasicStroke(isHovered ? 1.5f : 1.0f));
        g2.draw(new RoundRectangle2D.Float(1.5f, 1.5f, w - 3, h - 3, cornerRadius, cornerRadius));

        g2.dispose();
        super.paintComponent(g);
    }
}

package javaos.ui.components;

import javaos.ui.Theme;
import javaos.ui.ThemeManager;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * Reusable elevated cyberpunk card panel with rounded corners and glowing borders.
 */
public class NeonCard extends JPanel {

    private int cornerRadius = 12;
    private boolean glowing = false;

    public NeonCard() {
        this(new BorderLayout());
    }

    public NeonCard(LayoutManager layout) {
        super(layout);
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
    }

    public void setCornerRadius(int radius) {
        this.cornerRadius = radius;
        repaint();
    }

    public void setGlowing(boolean glowing) {
        this.glowing = glowing;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        int w = getWidth();
        int h = getHeight();

        // Card body
        g2.setColor(theme.elevatedPanel);
        g2.fill(new RoundRectangle2D.Float(1, 1, w - 2, h - 2, cornerRadius, cornerRadius));

        // Glow effect
        if (glowing) {
            g2.setColor(new Color(theme.neonAccent.getRed(), theme.neonAccent.getGreen(), theme.neonAccent.getBlue(), 30));
            g2.fill(new RoundRectangle2D.Float(3, 3, w - 6, h - 6, cornerRadius, cornerRadius));
            g2.setColor(theme.neonAccent);
            g2.setStroke(new BasicStroke(1.5f));
        } else {
            g2.setColor(theme.secondaryAccent);
            g2.setStroke(new BasicStroke(0.8f));
        }

        g2.draw(new RoundRectangle2D.Float(1.5f, 1.5f, w - 3, h - 3, cornerRadius, cornerRadius));

        g2.dispose();
        super.paintComponent(g);
    }
}

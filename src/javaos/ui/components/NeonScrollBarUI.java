package javaos.ui.components;

import javaos.ui.Theme;
import javaos.ui.ThemeManager;

import javax.swing.*;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;

/**
 * Slim cyberpunk neon scrollbar UI matching the dark aesthetic.
 */
public class NeonScrollBarUI extends BasicScrollBarUI {

    @Override
    protected void configureScrollBarColors() {
        // Dynamic colors handled in paint methods
    }

    @Override
    protected JButton createDecreaseButton(int orientation) {
        return createZeroButton();
    }

    @Override
    protected JButton createIncreaseButton(int orientation) {
        return createZeroButton();
    }

    private JButton createZeroButton() {
        JButton jb = new JButton();
        jb.setPreferredSize(new Dimension(0, 0));
        jb.setMinimumSize(new Dimension(0, 0));
        jb.setMaximumSize(new Dimension(0, 0));
        return jb;
    }

    @Override
    protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        g.setColor(theme.background);
        g.fillRect(trackBounds.x, trackBounds.y, trackBounds.width, trackBounds.height);
    }

    @Override
    protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
        if (thumbBounds.isEmpty() || !scrollbar.isEnabled()) {
            return;
        }

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        Color thumbColor = isDragging ? theme.neonAccent : (isThumbRollover() ? theme.secondaryAccent : new Color(theme.secondaryAccent.getRed(), theme.secondaryAccent.getGreen(), theme.secondaryAccent.getBlue(), 120));

        g2.setColor(thumbColor);
        int arc = Math.min(thumbBounds.width, thumbBounds.height);
        g2.fillRoundRect(thumbBounds.x + 2, thumbBounds.y + 2, thumbBounds.width - 4, thumbBounds.height - 4, arc, arc);

        g2.dispose();
    }
}

package javaos.ui.components;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages loading, scaling, and caching of the official borderless IMPERION OS logo.
 */
public final class LogoManager {

    private static final String BORDERLESS_PATH = "data/assets/imperion_logo_borderless.png";
    private static final String ORIGINAL_PATH = "data/assets/imperion_logo.jpg";

    private static BufferedImage baseLogo = null;
    private static final Map<String, ImageIcon> iconCache = new HashMap<>();

    private LogoManager() {}

    public static synchronized BufferedImage getLogo() {
        if (baseLogo != null) return baseLogo;

        try {
            File f = new File(BORDERLESS_PATH);
            if (!f.exists()) {
                f = new File(ORIGINAL_PATH);
            }
            if (f.exists()) {
                baseLogo = ImageIO.read(f);
            }
        } catch (Exception e) {
            System.err.println("[LogoManager] Error loading logo: " + e.getMessage());
        }
        return baseLogo;
    }

    /**
     * Retrieves a scaled, borderless ImageIcon.
     */
    public static synchronized ImageIcon getLogoIcon(int w, int h) {
        String key = w + "x" + h;
        if (iconCache.containsKey(key)) {
            return iconCache.get(key);
        }

        BufferedImage src = getLogo();
        if (src == null) return null;

        Image scaled = src.getScaledInstance(w, h, Image.SCALE_SMOOTH);
        ImageIcon icon = new ImageIcon(scaled);
        iconCache.put(key, icon);
        return icon;
    }

    /**
     * Renders the borderless logo smoothly with optional alpha transparency.
     */
    public static void paintLogo(Graphics2D g2, int x, int y, int w, int h, float alpha) {
        BufferedImage img = getLogo();
        if (img == null) return;

        Graphics2D g = (Graphics2D) g2.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        if (alpha < 1.0f) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, Math.min(1f, alpha))));
        }

        g.drawImage(img, x, y, w, h, null);
        g.dispose();
    }

    /**
     * Renders a glowing, borderless desktop hologram insignia centered at (cx, cy).
     */
    public static void paintDesktopHologram(Graphics2D g2, int cx, int cy, int size, double hoverPhase, Color glowColor) {
        BufferedImage img = getLogo();
        if (img == null) return;

        int aspectH = (int) (size * (img.getHeight() / (double) img.getWidth()));
        int hoverY = (int) (Math.sin(hoverPhase) * 6.0);

        int drawX = cx - (size / 2);
        int drawY = cy - (aspectH / 2) + hoverY;

        Graphics2D g = (Graphics2D) g2.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        // Soft ambient radial cyan glow behind the logo
        if (glowColor != null) {
            int glowRadius = (int) (size * 0.40);
            RadialGradientPaint rgp = new RadialGradientPaint(
                    cx, cy + hoverY, glowRadius,
                    new float[]{0.0f, 0.5f, 1.0f},
                    new Color[]{
                            new Color(glowColor.getRed(), glowColor.getGreen(), glowColor.getBlue(), 65),
                            new Color(glowColor.getRed(), glowColor.getGreen(), glowColor.getBlue(), 25),
                            new Color(0, 0, 0, 0)
                    }
            );
            g.setPaint(rgp);
            g.fillOval(cx - glowRadius, cy + hoverY - glowRadius, glowRadius * 2, glowRadius * 2);
        }

        // Render borderless logo image
        g.drawImage(img, drawX, drawY, size, aspectH, null);
        g.dispose();
    }
}

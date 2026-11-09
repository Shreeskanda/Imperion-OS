package javaos.ui;

import java.awt.Color;
import java.awt.Font;

/**
 * Encapsulates a complete neon cyberpunk color palette and typography configuration.
 */
public class Theme {

    private final String name;
    public final Color background;
    public final Color panel;
    public final Color elevatedPanel;
    public final Color windowTitlebar;
    public final Color neonAccent;
    public final Color secondaryAccent;
    public final Color textPrimary;
    public final Color textSecondary;
    public final Color warning;
    public final Color error;
    public final Color success;
    public final Color borderGlow;
    public final Color selectionBackground;

    public final Font fontDisplay;
    public final Font fontRegular;
    public final Font fontBold;
    public final Font fontSmall;
    public final Font fontMonospace;

    public Theme(String name,
                 Color background,
                 Color panel,
                 Color elevatedPanel,
                 Color windowTitlebar,
                 Color neonAccent,
                 Color secondaryAccent,
                 Color textPrimary,
                 Color textSecondary,
                 Color warning,
                 Color error,
                 Color success) {
        this.name = name;
        this.background = background;
        this.panel = panel;
        this.elevatedPanel = elevatedPanel;
        this.windowTitlebar = windowTitlebar;
        this.neonAccent = neonAccent;
        this.secondaryAccent = secondaryAccent;
        this.textPrimary = textPrimary;
        this.textSecondary = textSecondary;
        this.warning = warning;
        this.error = error;
        this.success = success;
        this.borderGlow = new Color(neonAccent.getRed(), neonAccent.getGreen(), neonAccent.getBlue(), 90);
        this.selectionBackground = new Color(neonAccent.getRed(), neonAccent.getGreen(), neonAccent.getBlue(), 40);

        this.fontDisplay = new Font("Segoe UI", Font.BOLD, 18);
        this.fontRegular = new Font("Segoe UI", Font.PLAIN, 13);
        this.fontBold = new Font("Segoe UI", Font.BOLD, 13);
        this.fontSmall = new Font("Segoe UI", Font.PLAIN, 11);
        this.fontMonospace = new Font("Consolas", Font.PLAIN, 13);
    }

    public String getName() {
        return name;
    }

    public static Theme createCyberpunkCyan() {
        return new Theme(
                "Cyberpunk Cyan",
                new Color(0x08, 0x0B, 0x14), // #080B14
                new Color(0x10, 0x18, 0x27), // #101827
                new Color(0x17, 0x22, 0x35), // #172235
                new Color(0x1E, 0x2C, 0x44), // #1E2C44
                new Color(0x22, 0xD3, 0xEE), // #22D3EE (Neon Cyan)
                new Color(0x3B, 0x82, 0xF6), // #3B82F6 (Electric Blue)
                new Color(0xF1, 0xF5, 0xF9), // #F1F5F9 (Main text)
                new Color(0x94, 0xA3, 0xB8), // #94A3B8 (Secondary text)
                new Color(0xF5, 0x9E, 0x0B), // #F59E0B (Warning)
                new Color(0xEF, 0x44, 0x44), // #EF4444 (Error)
                new Color(0x10, 0xB9, 0x81)  // #10B981 (Success)
        );
    }

    public static Theme createSynthwaveViolet() {
        return new Theme(
                "Synthwave Violet",
                new Color(0x0E, 0x07, 0x17),
                new Color(0x1B, 0x0F, 0x2E),
                new Color(0x28, 0x16, 0x44),
                new Color(0x34, 0x1C, 0x58),
                new Color(0xE8, 0x79, 0xF9), // Fuchsia / Neon Pink
                new Color(0x8B, 0x5C, 0xF6), // Violet
                new Color(0xF5, 0xF3, 0xFF),
                new Color(0xA7, 0x8B, 0xFA),
                new Color(0xFB, 0xBF, 0x24),
                new Color(0xF8, 0x71, 0x71),
                new Color(0x34, 0xD3, 0x99)
        );
    }

    public static Theme createMatrixGreen() {
        return new Theme(
                "Matrix Green",
                new Color(0x04, 0x0D, 0x06),
                new Color(0x09, 0x1D, 0x0E),
                new Color(0x10, 0x2E, 0x17),
                new Color(0x16, 0x3E, 0x20),
                new Color(0x22, 0xC5, 0x5E), // Matrix neon green
                new Color(0x10, 0xB9, 0x81), // Emerald
                new Color(0xEC, 0xFD, 0xF5),
                new Color(0x86, 0xEF, 0xAC),
                new Color(0xFA, 0xCC, 0x15),
                new Color(0xF8, 0x71, 0x71),
                new Color(0x4A, 0xDE, 0x80)
        );
    }

    public static Theme createSolarAmber() {
        return new Theme(
                "Solar Amber",
                new Color(0x14, 0x0B, 0x04),
                new Color(0x24, 0x15, 0x08),
                new Color(0x38, 0x22, 0x0E),
                new Color(0x4A, 0x2E, 0x14),
                new Color(0xF5, 0x9E, 0x0B), // Neon Amber
                new Color(0xFB, 0x92, 0x3C), // Bright Orange
                new Color(0xFF, 0xFB, 0xEB),
                new Color(0xFC, 0xD3, 0x4D),
                new Color(0xFE, 0xF0, 0x8A),
                new Color(0xF8, 0x71, 0x71),
                new Color(0x34, 0xD3, 0x99)
        );
    }

    public static Theme createCrimsonProtocol() {
        return new Theme(
                "Crimson Protocol",
                new Color(0x12, 0x06, 0x08),
                new Color(0x20, 0x0C, 0x11),
                new Color(0x30, 0x13, 0x1B),
                new Color(0x40, 0x1A, 0x24),
                new Color(0xEF, 0x44, 0x44), // Crimson Red
                new Color(0xF4, 0x3F, 0x5E), // Rose Accent
                new Color(0xFF, 0xF1, 0xF2),
                new Color(0xFB, 0x71, 0x85),
                new Color(0xF5, 0x9E, 0x0B),
                new Color(0xDC, 0x26, 0x26),
                new Color(0x10, 0xB9, 0x81)
        );
    }

    public static Theme createMidnightOLED() {
        return new Theme(
                "Midnight OLED",
                new Color(0x02, 0x04, 0x0A),
                new Color(0x09, 0x0E, 0x17),
                new Color(0x11, 0x18, 0x27),
                new Color(0x1E, 0x29, 0x3B),
                new Color(0x38, 0xBD, 0xF8), // Ice Sky Blue
                new Color(0x60, 0xA5, 0xFA), // Blue accent
                new Color(0xF8, 0xFA, 0xFC),
                new Color(0x94, 0xA3, 0xB8),
                new Color(0xFB, 0xBF, 0x24),
                new Color(0xEF, 0x44, 0x44),
                new Color(0x34, 0xD3, 0x99)
        );
    }

    public static Theme createTokyoSakura() {
        return new Theme(
                "Tokyo Sakura Neon",
                new Color(0x11, 0x05, 0x19),
                new Color(0x1D, 0x0B, 0x2D),
                new Color(0x2A, 0x10, 0x41),
                new Color(0x38, 0x16, 0x56),
                new Color(0xFF, 0x2A, 0x85), // Sakura Hot Pink
                new Color(0x00, 0xF0, 0xFF), // Electric Mint Cyan
                new Color(0xFA, 0xF5, 0xFF),
                new Color(0xC0, 0x84, 0xFC),
                new Color(0xFB, 0xBF, 0x24),
                new Color(0xF4, 0x3F, 0x5E),
                new Color(0x2D, 0xD4, 0xBF)
        );
    }
}

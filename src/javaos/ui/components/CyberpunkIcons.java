package javaos.ui.components;

import javaos.ui.Theme;
import javaos.ui.ThemeManager;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;

/**
 * Procedural vector icon generator for JavaOS.
 * Renders razor-sharp cyberpunk and neon icons without requiring external image assets.
 */
public final class CyberpunkIcons {

    private CyberpunkIcons() {}

    public static Icon createIcon(String type, int size) {
        return new VectorIcon(type, size);
    }

    private static class VectorIcon implements Icon {
        private final String type;
        private final int size;

        public VectorIcon(String type, int size) {
            this.type = (type != null) ? type.toUpperCase() : "GENERIC";
            this.size = size;
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.translate(x, y);

            Theme theme = ThemeManager.getInstance().getCurrentTheme();
            Color accent = theme.neonAccent;
            Color secAccent = theme.secondaryAccent;
            Color text = theme.textPrimary;

            switch (type) {
                case "START":
                    paintStartLogo(g2, size, accent, secAccent);
                    break;
                case "VICTUS":
                    paintVictusLogo(g2, size, accent, secAccent);
                    break;
                case "TERMINAL":
                    paintTerminal(g2, size, accent, text);
                    break;
                case "FILES":
                case "FOLDER":
                    paintFolder(g2, size, accent, secAccent);
                    break;
                case "NOTEPAD":
                    paintNotepad(g2, size, accent, text);
                    break;
                case "CALCULATOR":
                    paintCalculator(g2, size, accent, secAccent);
                    break;
                case "MONITOR":
                    paintMonitor(g2, size, accent, secAccent);
                    break;
                case "CALENDAR":
                    paintCalendar(g2, size, accent, text);
                    break;
                case "SETTINGS":
                    paintSettings(g2, size, accent, secAccent);
                    break;
                case "TRASH":
                    paintTrash(g2, size, theme.error, accent);
                    break;
                case "FILE":
                    paintFile(g2, size, text, accent);
                    break;
                case "SEARCH":
                    paintSearch(g2, size, accent);
                    break;
                case "CLOSE":
                    paintClose(g2, size, theme.error);
                    break;
                case "MINIMIZE":
                    paintMinimize(g2, size, accent);
                    break;
                case "MAXIMIZE":
                    paintMaximize(g2, size, accent);
                    break;
                case "RESTORE":
                    paintRestore(g2, size, accent);
                    break;
                case "POWER":
                    paintPower(g2, size, theme.error);
                    break;
                case "LOCK":
                    paintLock(g2, size, theme.warning);
                    break;
                case "REFRESH":
                    paintRefresh(g2, size, accent);
                    break;
                case "PLAY":
                    paintPlay(g2, size, theme.success);
                    break;
                case "STOP":
                    paintStop(g2, size, theme.error);
                    break;
                case "SHIELD":
                case "SECURITY":
                    paintShield(g2, size, accent, secAccent);
                    break;
                case "CYBERCORE":
                case "CYBER":
                case "CORE":
                    paintCyberCore(g2, size, accent, secAccent);
                    break;
                case "VIRUS":
                case "BUG":
                    paintVirus(g2, size, theme.error, theme.warning);
                    break;
                case "NETWORK":
                case "WIFI":
                    paintNetwork(g2, size, accent, secAccent);
                    break;
                case "IMAGE":
                case "IMAGEVIEWER":
                case "PHOTO":
                    paintImage(g2, size, accent, secAccent);
                    break;
                case "AUDIO":
                case "MUSIC":
                case "PLAYER":
                    paintAudio(g2, size, theme.success, accent);
                    break;
                case "VIDEO":
                    paintVideo(g2, size, secAccent, accent);
                    break;
                case "CODE":
                    paintCode(g2, size, accent, text);
                    break;
                case "ARCHIVE":
                case "ZIP":
                    paintArchive(g2, size, theme.warning, accent);
                    break;
                case "EXE":
                case "BINARY":
                    paintExe(g2, size, theme.error, accent);
                    break;
                case "PDF":
                case "DOCVIEWER":
                    paintPdf(g2, size, theme.error, text);
                    break;
                case "WORD":
                case "WORDEDITOR":
                case "DOCX":
                case "DOC":
                case "DOCUMENT":
                    paintWordProcessor(g2, size, new Color(37, 99, 235), accent);
                    break;
                case "PRESENTATION":
                case "SLIDES":
                case "PPT":
                case "PPTX":
                case "POWERPOINT":
                    paintPresentation(g2, size, new Color(249, 115, 22), accent);
                    break;
                case "ABOUT":
                case "INFO":
                case "SYSTEM_INFO":
                    paintAbout(g2, size, accent, secAccent);
                    break;
                case "SHEET":
                case "EXCEL":
                case "SPREADSHEET":
                case "XLS":
                case "XLSX":
                case "CSV":
                    paintSpreadsheet(g2, size, theme.success, text);
                    break;
                case "GAME":
                case "GAMEPAD":
                case "ARCADE":
                    paintGamepad(g2, size, accent, theme.warning);
                    break;
                case "PAINT":
                case "DRAW":
                case "BRUSH":
                    paintBrush(g2, size, secAccent, accent);
                    break;
                case "STICKY":
                case "STICKYNOTES":
                case "NOTE":
                case "NOTES":
                    paintSticky(g2, size, theme.warning, text);
                    break;
                case "RADAR":
                case "CYBERDECK":
                case "DECK":
                    paintRadar(g2, size, accent, secAccent);
                    break;
                case "CHECK":
                case "SUCCESS_ICON":
                    paintCheck(g2, size, theme.success);
                    break;
                case "WARNING_ICON":
                case "ALERT":
                    paintAlertTriangle(g2, size, theme.warning);
                    break;
                case "ERROR_ICON":
                    paintErrorIcon(g2, size, theme.error);
                    break;
                case "DRIVE":
                case "HDD":
                    paintDrive(g2, size, accent, secAccent);
                    break;
                case "HOME":
                    paintHome(g2, size, accent, secAccent);
                    break;
                case "UP":
                case "ARROW_UP":
                    paintArrowUp(g2, size, accent);
                    break;
                case "REALFILES":
                    paintRealFiles(g2, size, accent, secAccent);
                    break;
                case "BROWSER":
                case "GLOBE":
                case "WEB":
                    paintGlobe(g2, size, accent, secAccent);
                    break;
                case "EXTERNAL":
                case "POPOUT":
                    paintExternal(g2, size, accent);
                    break;
                default:
                    paintGeneric(g2, size, accent);
                    break;
            }

            g2.dispose();
        }
    }

    private static void paintStartLogo(Graphics2D g, int s, Color c1, Color c2) {
        double r = s * 0.45;
        double cx = s / 2.0;
        double cy = s / 2.0;

        Path2D.Double hex = new Path2D.Double();
        for (int i = 0; i < 6; i++) {
            double angle = Math.toRadians(60 * i - 30);
            double x = cx + r * Math.cos(angle);
            double y = cy + r * Math.sin(angle);
            if (i == 0) hex.moveTo(x, y);
            else hex.lineTo(x, y);
        }
        hex.closePath();

        g.setPaint(new GradientPaint(0, 0, c1, s, s, c2));
        g.setStroke(new BasicStroke(Math.max(1.5f, s * 0.08f)));
        g.draw(hex);

        // Core dot and lines
        g.fill(new Ellipse2D.Double(cx - s * 0.12, cy - s * 0.12, s * 0.24, s * 0.24));
    }

    private static void paintVictusLogo(Graphics2D g, int s, Color c1, Color c2) {
        double cx = s / 2.0;
        double cy = s / 2.0;
        // Outer glowing orbital ellipse
        g.setColor(new Color(c2.getRed(), c2.getGreen(), c2.getBlue(), 120));
        g.setStroke(new BasicStroke(Math.max(1.2f, s * 0.06f)));
        g.draw(new Arc2D.Double(s * 0.1, s * 0.2, s * 0.8, s * 0.6, 25, 310, Arc2D.OPEN));

        // Diagonal orbital ring
        g.setColor(c1);
        g.setStroke(new BasicStroke(Math.max(1.5f, s * 0.07f)));
        g.draw(new Arc2D.Double(s * 0.2, s * 0.1, s * 0.6, s * 0.8, -45, 270, Arc2D.OPEN));

        // Core AI sphere
        g.setPaint(new RadialGradientPaint((float) cx, (float) cy, (float) (s * 0.25),
                new float[]{0.0f, 1.0f}, new Color[]{Color.WHITE, c1}));
        g.fill(new Ellipse2D.Double(cx - s * 0.18, cy - s * 0.18, s * 0.36, s * 0.36));
    }

    private static void paintTerminal(Graphics2D g, int s, Color accent, Color text) {
        g.setColor(new Color(0x0C, 0x12, 0x1E));
        g.fillRoundRect((int) (s * 0.1), (int) (s * 0.1), (int) (s * 0.8), (int) (s * 0.8), (int) (s * 0.2), (int) (s * 0.2));
        g.setColor(accent);
        g.setStroke(new BasicStroke(Math.max(1.2f, s * 0.06f)));
        g.drawRoundRect((int) (s * 0.1), (int) (s * 0.1), (int) (s * 0.8), (int) (s * 0.8), (int) (s * 0.2), (int) (s * 0.2));

        // Draw '>' prompt
        Path2D.Double p = new Path2D.Double();
        p.moveTo(s * 0.25, s * 0.35);
        p.lineTo(s * 0.45, s * 0.5);
        p.lineTo(s * 0.25, s * 0.65);
        g.draw(p);

        // Draw cursor '_'
        g.setColor(text);
        g.drawLine((int) (s * 0.52), (int) (s * 0.65), (int) (s * 0.72), (int) (s * 0.65));
    }

    private static void paintFolder(Graphics2D g, int s, Color c1, Color c2) {
        Path2D.Double folder = new Path2D.Double();
        folder.moveTo(s * 0.15, s * 0.25);
        folder.lineTo(s * 0.45, s * 0.25);
        folder.lineTo(s * 0.55, s * 0.35);
        folder.lineTo(s * 0.85, s * 0.35);
        folder.lineTo(s * 0.85, s * 0.75);
        folder.lineTo(s * 0.15, s * 0.75);
        folder.closePath();

        g.setPaint(new GradientPaint(0, 0, new Color(c2.getRed(), c2.getGreen(), c2.getBlue(), 60),
                0, s, new Color(c1.getRed(), c1.getGreen(), c1.getBlue(), 120)));
        g.fill(folder);

        g.setColor(c1);
        g.setStroke(new BasicStroke(Math.max(1.2f, s * 0.06f)));
        g.draw(folder);
    }

    private static void paintNotepad(Graphics2D g, int s, Color c1, Color text) {
        int x = (int) (s * 0.2);
        int y = (int) (s * 0.15);
        int w = (int) (s * 0.6);
        int h = (int) (s * 0.7);

        g.setColor(new Color(0x13, 0x1E, 0x30));
        g.fillRoundRect(x, y, w, h, (int) (s * 0.1), (int) (s * 0.1));

        g.setColor(c1);
        g.setStroke(new BasicStroke(Math.max(1.2f, s * 0.06f)));
        g.drawRoundRect(x, y, w, h, (int) (s * 0.1), (int) (s * 0.1));

        // Lines of text
        g.setColor(text);
        int lineY = (int) (s * 0.35);
        g.drawLine((int) (s * 0.3), lineY, (int) (s * 0.7), lineY);
        lineY += (int) (s * 0.12);
        g.drawLine((int) (s * 0.3), lineY, (int) (s * 0.6), lineY);
        lineY += (int) (s * 0.12);
        g.drawLine((int) (s * 0.3), lineY, (int) (s * 0.65), lineY);
    }

    private static void paintCalculator(Graphics2D g, int s, Color c1, Color c2) {
        int x = (int) (s * 0.2);
        int y = (int) (s * 0.15);
        int w = (int) (s * 0.6);
        int h = (int) (s * 0.7);

        g.setColor(new Color(0x13, 0x1E, 0x30));
        g.fillRoundRect(x, y, w, h, (int) (s * 0.1), (int) (s * 0.1));
        g.setColor(c1);
        g.setStroke(new BasicStroke(Math.max(1.2f, s * 0.06f)));
        g.drawRoundRect(x, y, w, h, (int) (s * 0.1), (int) (s * 0.1));

        // Display screen
        g.setColor(new Color(0x08, 0x0E, 0x1A));
        g.fillRect((int) (s * 0.28), (int) (s * 0.24), (int) (s * 0.44), (int) (s * 0.14));

        // Buttons grid
        g.setColor(c2);
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 3; col++) {
                int bx = (int) (s * 0.28 + col * s * 0.16);
                int by = (int) (s * 0.45 + row * s * 0.16);
                g.fillRect(bx, by, (int) (s * 0.1), (int) (s * 0.1));
            }
        }
    }

    private static void paintMonitor(Graphics2D g, int s, Color c1, Color c2) {
        int x = (int) (s * 0.15);
        int y = (int) (s * 0.2);
        int w = (int) (s * 0.7);
        int h = (int) (s * 0.5);

        g.setColor(new Color(0x0C, 0x14, 0x24));
        g.fillRoundRect(x, y, w, h, (int) (s * 0.08), (int) (s * 0.08));
        g.setColor(c1);
        g.setStroke(new BasicStroke(Math.max(1.2f, s * 0.06f)));
        g.drawRoundRect(x, y, w, h, (int) (s * 0.08), (int) (s * 0.08));

        // Stand
        g.drawLine((int) (s * 0.5), (int) (s * 0.7), (int) (s * 0.5), (int) (s * 0.82));
        g.drawLine((int) (s * 0.35), (int) (s * 0.82), (int) (s * 0.65), (int) (s * 0.82));

        // Pulse wave
        Path2D.Double wave = new Path2D.Double();
        wave.moveTo(s * 0.22, s * 0.45);
        wave.lineTo(s * 0.35, s * 0.45);
        wave.lineTo(s * 0.42, s * 0.32);
        wave.lineTo(s * 0.52, s * 0.58);
        wave.lineTo(s * 0.6, s * 0.45);
        wave.lineTo(s * 0.78, s * 0.45);
        g.setColor(c2);
        g.draw(wave);
    }

    private static void paintCalendar(Graphics2D g, int s, Color c1, Color text) {
        int x = (int) (s * 0.18);
        int y = (int) (s * 0.2);
        int w = (int) (s * 0.64);
        int h = (int) (s * 0.62);

        g.setColor(new Color(0x13, 0x1E, 0x30));
        g.fillRoundRect(x, y, w, h, (int) (s * 0.1), (int) (s * 0.1));
        g.setColor(c1);
        g.setStroke(new BasicStroke(Math.max(1.2f, s * 0.06f)));
        g.drawRoundRect(x, y, w, h, (int) (s * 0.1), (int) (s * 0.1));

        // Calendar top header band
        g.fillRect(x, y, w, (int) (h * 0.28));

        // Grid dots
        g.setColor(text);
        for (int r = 0; r < 2; r++) {
            for (int col = 0; col < 3; col++) {
                g.fillOval((int) (s * 0.3 + col * s * 0.16), (int) (s * 0.52 + r * s * 0.14), (int) (s * 0.06), (int) (s * 0.06));
            }
        }
    }

    private static void paintSettings(Graphics2D g, int s, Color c1, Color c2) {
        double cx = s / 2.0;
        double cy = s / 2.0;
        double rOuter = s * 0.35;
        double rInner = s * 0.25;

        g.setColor(c1);
        g.setStroke(new BasicStroke(Math.max(1.5f, s * 0.08f)));

        int teeth = 6;
        for (int i = 0; i < teeth; i++) {
            double angle = Math.toRadians(360.0 / teeth * i);
            double x1 = cx + (rOuter - s * 0.05) * Math.cos(angle);
            double y1 = cy + (rOuter - s * 0.05) * Math.sin(angle);
            double x2 = cx + (rOuter + s * 0.05) * Math.cos(angle);
            double y2 = cy + (rOuter + s * 0.05) * Math.sin(angle);
            g.draw(new Line2D.Double(x1, y1, x2, y2));
        }

        g.draw(new Ellipse2D.Double(cx - rInner, cy - rInner, rInner * 2, rInner * 2));
        g.setColor(c2);
        g.fill(new Ellipse2D.Double(cx - s * 0.1, cy - s * 0.1, s * 0.2, s * 0.2));
    }

    private static void paintTrash(Graphics2D g, int s, Color err, Color acc) {
        g.setColor(err);
        g.setStroke(new BasicStroke(Math.max(1.2f, s * 0.06f)));

        // Lid
        g.drawLine((int) (s * 0.25), (int) (s * 0.3), (int) (s * 0.75), (int) (s * 0.3));
        g.drawArc((int) (s * 0.42), (int) (s * 0.22), (int) (s * 0.16), (int) (s * 0.12), 0, 180);

        // Can
        Path2D.Double can = new Path2D.Double();
        can.moveTo(s * 0.3, s * 0.32);
        can.lineTo(s * 0.35, s * 0.78);
        can.lineTo(s * 0.65, s * 0.78);
        can.lineTo(s * 0.7, s * 0.32);
        g.draw(can);

        // Vertical ribs
        g.drawLine((int) (s * 0.44), (int) (s * 0.4), (int) (s * 0.44), (int) (s * 0.7));
        g.drawLine((int) (s * 0.56), (int) (s * 0.4), (int) (s * 0.56), (int) (s * 0.7));
    }

    private static void paintFile(Graphics2D g, int s, Color text, Color acc) {
        Path2D.Double doc = new Path2D.Double();
        doc.moveTo(s * 0.25, s * 0.15);
        doc.lineTo(s * 0.55, s * 0.15);
        doc.lineTo(s * 0.75, s * 0.35);
        doc.lineTo(s * 0.75, s * 0.85);
        doc.lineTo(s * 0.25, s * 0.85);
        doc.closePath();

        g.setColor(new Color(0x10, 0x19, 0x2B));
        g.fill(doc);
        g.setColor(acc);
        g.setStroke(new BasicStroke(Math.max(1.2f, s * 0.06f)));
        g.draw(doc);

        // Dog ear line
        g.drawLine((int) (s * 0.55), (int) (s * 0.15), (int) (s * 0.55), (int) (s * 0.35));
        g.drawLine((int) (s * 0.55), (int) (s * 0.35), (int) (s * 0.75), (int) (s * 0.35));
    }

    private static void paintSearch(Graphics2D g, int s, Color acc) {
        g.setColor(acc);
        g.setStroke(new BasicStroke(Math.max(1.5f, s * 0.08f)));
        double r = s * 0.24;
        g.draw(new Ellipse2D.Double(s * 0.2, s * 0.2, r * 2, r * 2));
        g.draw(new Line2D.Double(s * 0.58, s * 0.58, s * 0.8, s * 0.8));
    }

    private static void paintClose(Graphics2D g, int s, Color err) {
        g.setColor(err);
        g.setStroke(new BasicStroke(Math.max(1.5f, s * 0.1f)));
        g.drawLine((int) (s * 0.3), (int) (s * 0.3), (int) (s * 0.7), (int) (s * 0.7));
        g.drawLine((int) (s * 0.7), (int) (s * 0.3), (int) (s * 0.3), (int) (s * 0.7));
    }

    private static void paintMinimize(Graphics2D g, int s, Color acc) {
        g.setColor(acc);
        g.setStroke(new BasicStroke(Math.max(1.5f, s * 0.1f)));
        g.drawLine((int) (s * 0.25), (int) (s * 0.65), (int) (s * 0.75), (int) (s * 0.65));
    }

    private static void paintMaximize(Graphics2D g, int s, Color acc) {
        g.setColor(acc);
        g.setStroke(new BasicStroke(Math.max(1.2f, s * 0.08f)));
        g.drawRect((int) (s * 0.25), (int) (s * 0.25), (int) (s * 0.5), (int) (s * 0.5));
    }

    private static void paintRestore(Graphics2D g, int s, Color acc) {
        g.setColor(acc);
        g.setStroke(new BasicStroke(Math.max(1.0f, s * 0.06f)));
        g.drawRect((int) (s * 0.35), (int) (s * 0.2), (int) (s * 0.4), (int) (s * 0.4));
        g.drawRect((int) (s * 0.2), (int) (s * 0.35), (int) (s * 0.4), (int) (s * 0.4));
    }

    private static void paintPower(Graphics2D g, int s, Color err) {
        g.setColor(err);
        g.setStroke(new BasicStroke(Math.max(1.5f, s * 0.09f)));
        g.draw(new Arc2D.Double(s * 0.2, s * 0.2, s * 0.6, s * 0.6, 50, 260, Arc2D.OPEN));
        g.drawLine((int) (s * 0.5), (int) (s * 0.15), (int) (s * 0.5), (int) (s * 0.45));
    }

    private static void paintLock(Graphics2D g, int s, Color warn) {
        g.setColor(warn);
        g.setStroke(new BasicStroke(Math.max(1.2f, s * 0.08f)));
        g.drawRect((int) (s * 0.25), (int) (s * 0.45), (int) (s * 0.5), (int) (s * 0.4));
        g.drawArc((int) (s * 0.35), (int) (s * 0.2), (int) (s * 0.3), (int) (s * 0.35), 0, 180);
    }

    private static void paintRefresh(Graphics2D g, int s, Color acc) {
        g.setColor(acc);
        g.setStroke(new BasicStroke(Math.max(1.2f, s * 0.08f)));
        g.drawArc((int) (s * 0.25), (int) (s * 0.25), (int) (s * 0.5), (int) (s * 0.5), 45, 270);
        g.drawLine((int) (s * 0.75), (int) (s * 0.35), (int) (s * 0.75), (int) (s * 0.55));
        g.drawLine((int) (s * 0.75), (int) (s * 0.55), (int) (s * 0.55), (int) (s * 0.55));
    }

    private static void paintPlay(Graphics2D g, int s, Color col) {
        Path2D.Double p = new Path2D.Double();
        p.moveTo(s * 0.3, s * 0.2);
        p.lineTo(s * 0.75, s * 0.5);
        p.lineTo(s * 0.3, s * 0.8);
        p.closePath();
        g.setColor(col);
        g.fill(p);
    }

    private static void paintStop(Graphics2D g, int s, Color col) {
        g.setColor(col);
        g.fillRect((int) (s * 0.28), (int) (s * 0.28), (int) (s * 0.44), (int) (s * 0.44));
    }

    private static void paintGeneric(Graphics2D g, int s, Color acc) {
        g.setColor(acc);
        g.fillOval((int) (s * 0.3), (int) (s * 0.3), (int) (s * 0.4), (int) (s * 0.4));
    }

    // ── NEW ICON TYPES ────────────────────────────────────────────────────────

    /** Neon shield for security applications */
    private static void paintShield(Graphics2D g, int s, Color c1, Color c2) {
        Path2D.Double shield = new Path2D.Double();
        shield.moveTo(s * 0.5, s * 0.1);
        shield.lineTo(s * 0.85, s * 0.25);
        shield.lineTo(s * 0.85, s * 0.55);
        shield.quadTo(s * 0.85, s * 0.82, s * 0.5, s * 0.92);
        shield.quadTo(s * 0.15, s * 0.82, s * 0.15, s * 0.55);
        shield.lineTo(s * 0.15, s * 0.25);
        shield.closePath();

        g.setPaint(new GradientPaint(0, 0, new Color(c2.getRed(), c2.getGreen(), c2.getBlue(), 50), 0, s, new Color(c1.getRed(), c1.getGreen(), c1.getBlue(), 100)));
        g.fill(shield);
        g.setColor(c1);
        g.setStroke(new BasicStroke(Math.max(1.5f, s * 0.07f)));
        g.draw(shield);

        // Checkmark inside
        g.setColor(c1);
        g.setStroke(new BasicStroke(Math.max(1.5f, s * 0.08f)));
        g.drawLine((int)(s * 0.35), (int)(s * 0.54), (int)(s * 0.46), (int)(s * 0.66));
        g.drawLine((int)(s * 0.46), (int)(s * 0.66), (int)(s * 0.65), (int)(s * 0.42));
    }

    /** Futuristic glowing cyber-matrix reactor core icon for IMPERION CYBERCORE */
    private static void paintCyberCore(Graphics2D g, int s, Color c1, Color c2) {
        double cx = s / 2.0;
        double cy = s / 2.0;

        // 1. Outer cyber hexagon matrix shield
        Path2D.Double hex = new Path2D.Double();
        int nPoints = 6;
        double rOuter = s * 0.44;
        for (int i = 0; i < nPoints; i++) {
            double angle = Math.toRadians(60 * i - 30);
            double x = cx + rOuter * Math.cos(angle);
            double y = cy + rOuter * Math.sin(angle);
            if (i == 0) hex.moveTo(x, y);
            else hex.lineTo(x, y);
        }
        hex.closePath();

        // Glowing semi-transparent background fill
        g.setPaint(new RadialGradientPaint(
                (float) cx, (float) cy, (float) (s * 0.5),
                new float[]{0.0f, 0.7f, 1.0f},
                new Color[]{new Color(c1.getRed(), c1.getGreen(), c1.getBlue(), 120),
                            new Color(c2.getRed(), c2.getGreen(), c2.getBlue(), 40),
                            new Color(0, 0, 0, 0)}
        ));
        g.fill(hex);

        // Outer neon border
        g.setColor(c1);
        g.setStroke(new BasicStroke(Math.max(1.5f, s * 0.07f)));
        g.draw(hex);

        // 2. Corner circuit nodes & trace spokes
        g.setColor(new Color(c2.getRed(), c2.getGreen(), c2.getBlue(), 180));
        g.setStroke(new BasicStroke(Math.max(1.0f, s * 0.04f)));
        for (int i = 0; i < 6; i++) {
            double angle = Math.toRadians(60 * i - 30);
            double x1 = cx + (s * 0.22) * Math.cos(angle);
            double y1 = cy + (s * 0.22) * Math.sin(angle);
            double x2 = cx + rOuter * Math.cos(angle);
            double y2 = cy + rOuter * Math.sin(angle);
            g.drawLine((int) x1, (int) y1, (int) x2, (int) y2);
        }

        // 3. Inner cyber reticle ring
        double rInner = s * 0.24;
        g.setColor(c2);
        g.setStroke(new BasicStroke(Math.max(1.2f, s * 0.05f)));
        g.draw(new Ellipse2D.Double(cx - rInner, cy - rInner, rInner * 2, rInner * 2));

        // 4. Glowing Reactor Core
        double rCore = s * 0.13;
        g.setPaint(new RadialGradientPaint(
                (float) cx, (float) cy, (float) rCore,
                new float[]{0.0f, 0.6f, 1.0f},
                new Color[]{Color.WHITE, c1, new Color(c2.getRed(), c2.getGreen(), c2.getBlue(), 200)}
        ));
        g.fill(new Ellipse2D.Double(cx - rCore, cy - rCore, rCore * 2, rCore * 2));
    }

    /** Stylized bug/virus icon for threat display */
    private static void paintVirus(Graphics2D g, int s, Color err, Color warn) {
        g.setStroke(new BasicStroke(Math.max(1.2f, s * 0.07f)));
        // Central hexagonal body
        g.setColor(err);
        g.fillOval((int)(s * 0.35), (int)(s * 0.35), (int)(s * 0.3), (int)(s * 0.3));
        // Spikes radiating outward
        g.setColor(warn);
        double cx = s * 0.5, cy = s * 0.5;
        for (int i = 0; i < 8; i++) {
            double angle = Math.toRadians(i * 45);
            double x1 = cx + s * 0.2 * Math.cos(angle);
            double y1 = cy + s * 0.2 * Math.sin(angle);
            double x2 = cx + s * 0.42 * Math.cos(angle);
            double y2 = cy + s * 0.42 * Math.sin(angle);
            g.draw(new Line2D.Double(x1, y1, x2, y2));
            // Small dot at spike tip
            g.fillOval((int)(x2 - s * 0.03), (int)(y2 - s * 0.03), (int)(s * 0.07), (int)(s * 0.07));
        }
    }

    /** Network icon (signal waves) */
    private static void paintNetwork(Graphics2D g, int s, Color c1, Color c2) {
        double cx = s * 0.5, by = s * 0.78;
        g.setStroke(new BasicStroke(Math.max(1.5f, s * 0.08f)));

        // Dot at base
        g.setColor(c1);
        g.fill(new Ellipse2D.Double(cx - s * 0.05, by - s * 0.05, s * 0.1, s * 0.1));

        // Concentric arcs
        g.setColor(c1);
        g.draw(new Arc2D.Double(cx - s * 0.18, by - s * 0.2, s * 0.36, s * 0.24, 30, 120, Arc2D.OPEN));
        g.setColor(c2);
        g.draw(new Arc2D.Double(cx - s * 0.33, by - s * 0.38, s * 0.66, s * 0.42, 30, 120, Arc2D.OPEN));
        g.setColor(new Color(c1.getRed(), c1.getGreen(), c1.getBlue(), 140));
        g.draw(new Arc2D.Double(cx - s * 0.48, by - s * 0.58, s * 0.96, s * 0.62, 30, 120, Arc2D.OPEN));
    }

    /** Image / photo icon (landscape scene) */
    private static void paintImage(Graphics2D g, int s, Color c1, Color c2) {
        g.setColor(new Color(0x0C, 0x14, 0x22));
        g.fillRoundRect((int)(s * 0.12), (int)(s * 0.18), (int)(s * 0.76), (int)(s * 0.64), (int)(s * 0.1), (int)(s * 0.1));
        g.setColor(c1);
        g.setStroke(new BasicStroke(Math.max(1.2f, s * 0.06f)));
        g.drawRoundRect((int)(s * 0.12), (int)(s * 0.18), (int)(s * 0.76), (int)(s * 0.64), (int)(s * 0.1), (int)(s * 0.1));

        // Sun circle
        g.setColor(c2);
        g.fill(new Ellipse2D.Double(s * 0.22, s * 0.27, s * 0.16, s * 0.16));

        // Mountain shape
        Path2D.Double mnt = new Path2D.Double();
        mnt.moveTo(s * 0.15, s * 0.72);
        mnt.lineTo(s * 0.38, s * 0.44);
        mnt.lineTo(s * 0.55, s * 0.60);
        mnt.lineTo(s * 0.68, s * 0.48);
        mnt.lineTo(s * 0.85, s * 0.72);
        mnt.closePath();
        g.setColor(new Color(c1.getRed(), c1.getGreen(), c1.getBlue(), 80));
        g.fill(mnt);
        g.setColor(c1);
        g.draw(mnt);
    }

    /** Music note for audio files */
    private static void paintAudio(Graphics2D g, int s, Color c1, Color c2) {
        g.setColor(c1);
        g.setStroke(new BasicStroke(Math.max(1.5f, s * 0.08f)));

        // Note stem
        g.drawLine((int)(s * 0.55), (int)(s * 0.2), (int)(s * 0.55), (int)(s * 0.65));
        // Note head
        g.fill(new Ellipse2D.Double(s * 0.34, s * 0.60, s * 0.24, s * 0.18));

        // Flag on stem
        Path2D.Double flag = new Path2D.Double();
        flag.moveTo(s * 0.55, s * 0.2);
        flag.curveTo(s * 0.8, s * 0.28, s * 0.8, s * 0.4, s * 0.55, s * 0.44);
        g.setColor(c2);
        g.draw(flag);
    }

    /** Play button with film frame for video files */
    private static void paintVideo(Graphics2D g, int s, Color c1, Color c2) {
        // Film frame
        g.setColor(new Color(0x0C, 0x14, 0x22));
        g.fillRoundRect((int)(s * 0.1), (int)(s * 0.2), (int)(s * 0.8), (int)(s * 0.6), (int)(s * 0.08), (int)(s * 0.08));
        g.setColor(c1);
        g.setStroke(new BasicStroke(Math.max(1.2f, s * 0.06f)));
        g.drawRoundRect((int)(s * 0.1), (int)(s * 0.2), (int)(s * 0.8), (int)(s * 0.6), (int)(s * 0.08), (int)(s * 0.08));

        // Play triangle inside
        Path2D.Double tri = new Path2D.Double();
        tri.moveTo(s * 0.35, s * 0.32);
        tri.lineTo(s * 0.72, s * 0.5);
        tri.lineTo(s * 0.35, s * 0.68);
        tri.closePath();
        g.setColor(c2);
        g.fill(tri);
    }

    /** Code brackets icon */
    private static void paintCode(Graphics2D g, int s, Color c1, Color text) {
        g.setColor(c1);
        g.setStroke(new BasicStroke(Math.max(1.5f, s * 0.09f)));

        // < bracket
        Path2D.Double lbr = new Path2D.Double();
        lbr.moveTo(s * 0.38, s * 0.3);
        lbr.lineTo(s * 0.22, s * 0.5);
        lbr.lineTo(s * 0.38, s * 0.7);
        g.draw(lbr);

        // > bracket
        Path2D.Double rbr = new Path2D.Double();
        rbr.moveTo(s * 0.62, s * 0.3);
        rbr.lineTo(s * 0.78, s * 0.5);
        rbr.lineTo(s * 0.62, s * 0.7);
        g.draw(rbr);

        // / slash
        g.setColor(text);
        g.draw(new Line2D.Double(s * 0.56, s * 0.28, s * 0.44, s * 0.72));
    }

    /** Zipper/archive box icon */
    private static void paintArchive(Graphics2D g, int s, Color c1, Color c2) {
        // Box
        g.setColor(new Color(0x0C, 0x12, 0x1E));
        g.fillRect((int)(s * 0.18), (int)(s * 0.25), (int)(s * 0.64), (int)(s * 0.6));
        g.setColor(c1);
        g.setStroke(new BasicStroke(Math.max(1.2f, s * 0.06f)));
        g.drawRect((int)(s * 0.18), (int)(s * 0.25), (int)(s * 0.64), (int)(s * 0.6));

        // Lid
        g.setColor(c2);
        g.fillRect((int)(s * 0.14), (int)(s * 0.17), (int)(s * 0.72), (int)(s * 0.12));
        g.drawRect((int)(s * 0.14), (int)(s * 0.17), (int)(s * 0.72), (int)(s * 0.12));

        // Zipper teeth
        g.setColor(c1);
        g.setStroke(new BasicStroke(Math.max(1.0f, s * 0.05f)));
        for (int i = 0; i < 4; i++) {
            int ty = (int)(s * 0.38 + i * s * 0.1);
            g.drawLine((int)(s * 0.45), ty, (int)(s * 0.55), ty);
        }
    }

    /** Executable binary icon (gear + chip) */
    private static void paintExe(Graphics2D g, int s, Color c1, Color c2) {
        double cx = s * 0.5, cy = s * 0.5;

        // Central circuit chip
        g.setColor(new Color(0x0C, 0x14, 0x22));
        g.fillRect((int)(cx - s * 0.2), (int)(cy - s * 0.2), (int)(s * 0.4), (int)(s * 0.4));
        g.setColor(c1);
        g.setStroke(new BasicStroke(Math.max(1.2f, s * 0.06f)));
        g.drawRect((int)(cx - s * 0.2), (int)(cy - s * 0.2), (int)(s * 0.4), (int)(s * 0.4));

        // Circuit pins
        g.setColor(c2);
        g.setStroke(new BasicStroke(Math.max(1.0f, s * 0.05f)));
        int[] pinX = {(int)(cx - s * 0.1), (int)(cx + s * 0.0), (int)(cx + s * 0.1)};
        for (int px : pinX) {
            g.drawLine(px, (int)(cy - s * 0.2), px, (int)(cy - s * 0.34));
            g.drawLine(px, (int)(cy + s * 0.2), px, (int)(cy + s * 0.34));
        }

        // Warning triangle overlay
        Path2D.Double w = new Path2D.Double();
        w.moveTo(cx, cy - s * 0.12);
        w.lineTo(cx + s * 0.13, cy + s * 0.1);
        w.lineTo(cx - s * 0.13, cy + s * 0.1);
        w.closePath();
        g.setColor(new Color(c1.getRed(), c1.getGreen(), c1.getBlue(), 200));
        g.fill(w);
    }

    /** PDF document icon */
    private static void paintPdf(Graphics2D g, int s, Color c1, Color text) {
        // Document body
        Path2D.Double doc = new Path2D.Double();
        doc.moveTo(s * 0.22, s * 0.12);
        doc.lineTo(s * 0.62, s * 0.12);
        doc.lineTo(s * 0.78, s * 0.28);
        doc.lineTo(s * 0.78, s * 0.88);
        doc.lineTo(s * 0.22, s * 0.88);
        doc.closePath();

        g.setColor(new Color(c1.getRed(), c1.getGreen(), c1.getBlue(), 50));
        g.fill(doc);
        g.setColor(c1);
        g.setStroke(new BasicStroke(Math.max(1.2f, s * 0.06f)));
        g.draw(doc);
        g.drawLine((int)(s * 0.62), (int)(s * 0.12), (int)(s * 0.62), (int)(s * 0.28));
        g.drawLine((int)(s * 0.62), (int)(s * 0.28), (int)(s * 0.78), (int)(s * 0.28));

        // "PDF" text area
        g.setColor(text);
        g.setFont(new Font("Segoe UI", Font.BOLD, Math.max(6, s / 4)));
        g.drawString("PDF", (int)(s * 0.3), (int)(s * 0.6));
    }

    /** Green checkmark */
    private static void paintCheck(Graphics2D g, int s, Color col) {
        g.setColor(col);
        g.setStroke(new BasicStroke(Math.max(2.0f, s * 0.12f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine((int)(s * 0.2), (int)(s * 0.52), (int)(s * 0.44), (int)(s * 0.74));
        g.drawLine((int)(s * 0.44), (int)(s * 0.74), (int)(s * 0.8), (int)(s * 0.28));
    }

    /** Warning triangle ! icon */
    private static void paintAlertTriangle(Graphics2D g, int s, Color warn) {
        Path2D.Double tri = new Path2D.Double();
        tri.moveTo(s * 0.5, s * 0.1);
        tri.lineTo(s * 0.9, s * 0.85);
        tri.lineTo(s * 0.1, s * 0.85);
        tri.closePath();

        g.setColor(new Color(warn.getRed(), warn.getGreen(), warn.getBlue(), 50));
        g.fill(tri);
        g.setColor(warn);
        g.setStroke(new BasicStroke(Math.max(1.5f, s * 0.07f)));
        g.draw(tri);

        // Exclamation mark
        g.setStroke(new BasicStroke(Math.max(1.5f, s * 0.09f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine((int)(s * 0.5), (int)(s * 0.38), (int)(s * 0.5), (int)(s * 0.65));
        g.fillOval((int)(s * 0.44), (int)(s * 0.7), (int)(s * 0.12), (int)(s * 0.1));
    }

    /** Red circle X for error state */
    private static void paintErrorIcon(Graphics2D g, int s, Color err) {
        g.setColor(new Color(err.getRed(), err.getGreen(), err.getBlue(), 50));
        g.fill(new Ellipse2D.Double(s * 0.1, s * 0.1, s * 0.8, s * 0.8));
        g.setColor(err);
        g.setStroke(new BasicStroke(Math.max(1.5f, s * 0.07f)));
        g.draw(new Ellipse2D.Double(s * 0.1, s * 0.1, s * 0.8, s * 0.8));
        g.setStroke(new BasicStroke(Math.max(2.0f, s * 0.1f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine((int)(s * 0.3), (int)(s * 0.3), (int)(s * 0.7), (int)(s * 0.7));
        g.drawLine((int)(s * 0.7), (int)(s * 0.3), (int)(s * 0.3), (int)(s * 0.7));
    }

    /** Hard drive / disk icon */
    private static void paintDrive(Graphics2D g, int s, Color c1, Color c2) {
        g.setColor(new Color(0x0C, 0x12, 0x1E));
        g.fillRoundRect((int)(s * 0.12), (int)(s * 0.22), (int)(s * 0.76), (int)(s * 0.56), (int)(s * 0.12), (int)(s * 0.12));
        g.setColor(c1);
        g.setStroke(new BasicStroke(Math.max(1.2f, s * 0.06f)));
        g.drawRoundRect((int)(s * 0.12), (int)(s * 0.22), (int)(s * 0.76), (int)(s * 0.56), (int)(s * 0.12), (int)(s * 0.12));

        // Platter circles
        g.setColor(c2);
        g.draw(new Ellipse2D.Double(s * 0.28, s * 0.38, s * 0.44, s * 0.24));
        g.fill(new Ellipse2D.Double(s * 0.44, s * 0.44, s * 0.12, s * 0.12));

        // LED dot
        g.setColor(c1);
        g.fill(new Ellipse2D.Double(s * 0.72, s * 0.64, s * 0.08, s * 0.08));
    }

    /** House icon for home directory */
    private static void paintHome(Graphics2D g, int s, Color c1, Color c2) {
        // Roof
        Path2D.Double roof = new Path2D.Double();
        roof.moveTo(s * 0.5, s * 0.12);
        roof.lineTo(s * 0.88, s * 0.46);
        roof.lineTo(s * 0.12, s * 0.46);
        roof.closePath();
        g.setColor(new Color(c2.getRed(), c2.getGreen(), c2.getBlue(), 80));
        g.fill(roof);
        g.setColor(c1);
        g.setStroke(new BasicStroke(Math.max(1.5f, s * 0.07f)));
        g.draw(roof);

        // Body
        g.setColor(new Color(0x0C, 0x14, 0x22));
        g.fillRect((int)(s * 0.22), (int)(s * 0.44), (int)(s * 0.56), (int)(s * 0.44));
        g.setColor(c1);
        g.drawRect((int)(s * 0.22), (int)(s * 0.44), (int)(s * 0.56), (int)(s * 0.44));

        // Door
        g.setColor(c2);
        g.fillRect((int)(s * 0.42), (int)(s * 0.6), (int)(s * 0.16), (int)(s * 0.28));
    }

    /** Upward arrow for navigation */
    private static void paintArrowUp(Graphics2D g, int s, Color c1) {
        g.setColor(c1);
        g.setStroke(new BasicStroke(Math.max(1.5f, s * 0.09f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine((int)(s * 0.5), (int)(s * 0.2), (int)(s * 0.5), (int)(s * 0.8));
        g.drawLine((int)(s * 0.5), (int)(s * 0.2), (int)(s * 0.25), (int)(s * 0.45));
        g.drawLine((int)(s * 0.5), (int)(s * 0.2), (int)(s * 0.75), (int)(s * 0.45));
    }

    /** Real file explorer icon (window with explorer pane) */
    private static void paintRealFiles(Graphics2D g, int s, Color c1, Color c2) {
        // Outer window frame
        g.setColor(new Color(0x0C, 0x12, 0x1E));
        g.fillRoundRect((int)(s * 0.08), (int)(s * 0.12), (int)(s * 0.84), (int)(s * 0.76), (int)(s * 0.1), (int)(s * 0.1));
        g.setColor(c1);
        g.setStroke(new BasicStroke(Math.max(1.2f, s * 0.06f)));
        g.drawRoundRect((int)(s * 0.08), (int)(s * 0.12), (int)(s * 0.84), (int)(s * 0.76), (int)(s * 0.1), (int)(s * 0.1));

        // Title bar
        g.setColor(c2);
        g.fillRect((int)(s * 0.08), (int)(s * 0.12), (int)(s * 0.84), (int)(s * 0.15));

        // Sidebar divider
        g.setColor(c2);
        g.drawLine((int)(s * 0.32), (int)(s * 0.28), (int)(s * 0.32), (int)(s * 0.86));

        // File list lines
        g.setColor(new Color(c1.getRed(), c1.getGreen(), c1.getBlue(), 150));
        for (int row = 0; row < 3; row++) {
            int ly = (int)(s * 0.38 + row * s * 0.14);
            g.drawLine((int)(s * 0.38), ly, (int)(s * 0.84), ly);
        }

        // Folder icon in sidebar
        g.setColor(c1);
        g.drawRect((int)(s * 0.13), (int)(s * 0.35), (int)(s * 0.14), (int)(s * 0.1));
    }

    /** Cyberpunk Globe / Web Browser icon */
    private static void paintGlobe(Graphics2D g, int s, Color c1, Color c2) {
        float strokeW = Math.max(1.2f, s * 0.065f);
        g.setStroke(new BasicStroke(strokeW));

        int cx = s / 2;
        int cy = s / 2;
        int r = (int) (s * 0.38);

        // Outer sphere glow
        g.setColor(new Color(c2.getRed(), c2.getGreen(), c2.getBlue(), 35));
        g.fillOval(cx - r, cy - r, r * 2, r * 2);

        // Outer sphere ring
        g.setColor(c1);
        g.drawOval(cx - r, cy - r, r * 2, r * 2);

        // Equator horizontal line
        g.setColor(c2);
        g.drawLine(cx - r, cy, cx + r, cy);

        // Prime meridian vertical line
        g.drawLine(cx, cy - r, cx, cy + r);

        // Longitude ellipses
        int ew = (int) (r * 0.55);
        g.setColor(c1);
        g.drawOval(cx - ew, cy - r, ew * 2, r * 2);

        // Tilted orbital cyber-ring
        g.setColor(new Color(c1.getRed(), c1.getGreen(), c1.getBlue(), 180));
        g.drawOval((int) (s * 0.08), (int) (s * 0.38), (int) (s * 0.84), (int) (s * 0.24));
    }

    /** External link / Popout icon */
    private static void paintExternal(Graphics2D g, int s, Color c1) {
        float strokeW = Math.max(1.2f, s * 0.07f);
        g.setStroke(new BasicStroke(strokeW));
        g.setColor(c1);

        // Box with gap at top-right
        Path2D.Double box = new Path2D.Double();
        box.moveTo(s * 0.5, s * 0.18);
        box.lineTo(s * 0.18, s * 0.18);
        box.lineTo(s * 0.18, s * 0.82);
        box.lineTo(s * 0.82, s * 0.82);
        box.lineTo(s * 0.82, s * 0.5);
        g.draw(box);

        // Diagonal arrow pointing out
        g.drawLine((int)(s * 0.44), (int)(s * 0.56), (int)(s * 0.82), (int)(s * 0.18));
        g.drawLine((int)(s * 0.58), (int)(s * 0.18), (int)(s * 0.82), (int)(s * 0.18));
        g.drawLine((int)(s * 0.82), (int)(s * 0.18), (int)(s * 0.82), (int)(s * 0.42));
    }

    /** Spreadsheet / Grid icon */
    private static void paintSpreadsheet(Graphics2D g, int s, Color c1, Color c2) {
        float strokeW = Math.max(1.2f, s * 0.065f);
        g.setStroke(new BasicStroke(strokeW));
        int pad = (int) (s * 0.14);
        int w = s - (pad * 2);
        int h = s - (pad * 2);

        // Green background tint
        g.setColor(new Color(c1.getRed(), c1.getGreen(), c1.getBlue(), 35));
        g.fillRect(pad, pad, w, h);

        // Outer border
        g.setColor(c1);
        g.drawRect(pad, pad, w, h);

        // Header band
        int headerH = h / 3;
        g.setColor(new Color(c1.getRed(), c1.getGreen(), c1.getBlue(), 120));
        g.fillRect(pad, pad, w, headerH);

        // Grid lines
        g.setColor(c1);
        int colW = w / 3;
        g.drawLine(pad + colW, pad, pad + colW, pad + h);
        g.drawLine(pad + colW * 2, pad, pad + colW * 2, pad + h);
        g.drawLine(pad, pad + headerH, pad + w, pad + headerH);
        g.drawLine(pad, pad + (headerH + (h - headerH) / 2), pad + w, pad + (headerH + (h - headerH) / 2));
    }

    /** Gamepad / Arcade icon */
    private static void paintGamepad(Graphics2D g, int s, Color c1, Color c2) {
        float strokeW = Math.max(1.2f, s * 0.065f);
        g.setStroke(new BasicStroke(strokeW));
        int pad = (int) (s * 0.15);
        int w = s - (pad * 2);
        int h = (int) (s * 0.55);
        int y = (int) (s * 0.25);

        // Body with rounded grips
        g.setColor(new Color(c1.getRed(), c1.getGreen(), c1.getBlue(), 40));
        g.fillRoundRect(pad, y, w, h, (int)(s * 0.28), (int)(s * 0.28));
        g.setColor(c1);
        g.drawRoundRect(pad, y, w, h, (int)(s * 0.28), (int)(s * 0.28));

        // D-Pad on left
        int dX = pad + (int)(w * 0.22);
        int dY = y + (h / 2);
        int arm = (int)(s * 0.10);
        g.setColor(c1);
        g.drawLine(dX - arm, dY, dX + arm, dY);
        g.drawLine(dX, dY - arm, dX, dY + arm);

        // Action buttons on right
        int btnX = pad + (int)(w * 0.76);
        int btnY = y + (h / 2);
        int bR = Math.max(2, (int)(s * 0.05));
        g.setColor(c2);
        g.fillOval(btnX - bR, btnY - (int)(s * 0.08) - bR, bR * 2, bR * 2);
        g.fillOval(btnX - bR, btnY + (int)(s * 0.08) - bR, bR * 2, bR * 2);
        g.fillOval(btnX - (int)(s * 0.08) - bR, btnY - bR, bR * 2, bR * 2);
        g.fillOval(btnX + (int)(s * 0.08) - bR, btnY - bR, bR * 2, bR * 2);
    }

    /** Paintbrush / Studio icon */
    private static void paintBrush(Graphics2D g, int s, Color c1, Color c2) {
        float strokeW = Math.max(1.2f, s * 0.07f);
        g.setStroke(new BasicStroke(strokeW, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        // Diagonal brush handle
        g.setColor(c1);
        g.drawLine((int)(s * 0.78), (int)(s * 0.22), (int)(s * 0.44), (int)(s * 0.56));

        // Ferrule
        g.setColor(c2);
        g.drawLine((int)(s * 0.44), (int)(s * 0.56), (int)(s * 0.35), (int)(s * 0.65));

        // Bristles / tip
        Path2D.Double tip = new Path2D.Double();
        tip.moveTo(s * 0.35, s * 0.65);
        tip.curveTo(s * 0.22, s * 0.72, s * 0.16, s * 0.84, s * 0.22, s * 0.84);
        tip.curveTo(s * 0.34, s * 0.84, s * 0.42, s * 0.72, s * 0.35, s * 0.65);
        g.setColor(c2);
        g.fill(tip);
    }

    /** Sticky Note icon */
    private static void paintSticky(Graphics2D g, int s, Color c1, Color c2) {
        float strokeW = Math.max(1.2f, s * 0.065f);
        g.setStroke(new BasicStroke(strokeW));
        int pad = (int) (s * 0.18);
        int w = s - (pad * 2);
        int h = s - (pad * 2);

        // Folded corner note
        Path2D.Double note = new Path2D.Double();
        note.moveTo(pad, pad);
        note.lineTo(pad + w - (int)(s * 0.18), pad);
        note.lineTo(pad + w, pad + (int)(s * 0.18));
        note.lineTo(pad + w, pad + h);
        note.lineTo(pad, pad + h);
        note.closePath();

        g.setColor(new Color(c1.getRed(), c1.getGreen(), c1.getBlue(), 45));
        g.fill(note);
        g.setColor(c1);
        g.draw(note);

        // Fold line
        g.drawLine(pad + w - (int)(s * 0.18), pad, pad + w - (int)(s * 0.18), pad + (int)(s * 0.18));
        g.drawLine(pad + w - (int)(s * 0.18), pad + (int)(s * 0.18), pad + w, pad + (int)(s * 0.18));

        // Text lines on note
        g.setColor(new Color(c2.getRed(), c2.getGreen(), c2.getBlue(), 180));
        g.drawLine(pad + (int)(s * 0.08), pad + (int)(s * 0.32), pad + w - (int)(s * 0.08), pad + (int)(s * 0.32));
        g.drawLine(pad + (int)(s * 0.08), pad + (int)(s * 0.48), pad + w - (int)(s * 0.08), pad + (int)(s * 0.48));
    }

    /** Radar / Cyberdeck icon */
    private static void paintRadar(Graphics2D g, int s, Color c1, Color c2) {
        float strokeW = Math.max(1.2f, s * 0.065f);
        g.setStroke(new BasicStroke(strokeW));
        int cx = s / 2;
        int cy = s / 2;
        int r = (int) (s * 0.36);

        // Concentric rings
        g.setColor(new Color(c1.getRed(), c1.getGreen(), c1.getBlue(), 35));
        g.fillOval(cx - r, cy - r, r * 2, r * 2);

        g.setColor(c1);
        g.drawOval(cx - r, cy - r, r * 2, r * 2);
        g.drawOval(cx - r / 2, cy - r / 2, r, r);

        // Crosshairs
        g.setColor(c2);
        g.drawLine(cx - r, cy, cx + r, cy);
        g.drawLine(cx, cy - r, cx, cy + r);

        // Sweep line
        g.setColor(c1);
        int sweepX = cx + (int)(r * 0.707);
        int sweepY = cy - (int)(r * 0.707);
        g.drawLine(cx, cy, sweepX, sweepY);

        // Blip dot
        g.setColor(Color.WHITE);
        g.fillOval(cx + (int)(r * 0.4), cy - (int)(r * 0.3), 3, 3);
    }

    /** Word Processor / .docx document icon with cyber blue body and bold 'W' */
    private static void paintWordProcessor(Graphics2D g, int s, Color blueAccent, Color cyanAccent) {
        float strokeW = Math.max(1.2f, s * 0.065f);
        g.setStroke(new BasicStroke(strokeW));

        int x = (int) (s * 0.18);
        int y = (int) (s * 0.12);
        int w = (int) (s * 0.64);
        int h = (int) (s * 0.76);
        int fold = (int) (s * 0.20);

        // Document body outline with folded top-right corner
        Path2D.Double doc = new Path2D.Double();
        doc.moveTo(x, y);
        doc.lineTo(x + w - fold, y);
        doc.lineTo(x + w, y + fold);
        doc.lineTo(x + w, y + h);
        doc.lineTo(x, y + h);
        doc.closePath();

        // Rich royal blue gradient fill
        g.setPaint(new GradientPaint(x, y, new Color(30, 64, 175, 180), x, y + h, new Color(15, 23, 42, 220)));
        g.fill(doc);

        // Neon outline
        g.setColor(blueAccent);
        g.draw(doc);

        // Folded ear
        Path2D.Double ear = new Path2D.Double();
        ear.moveTo(x + w - fold, y);
        ear.lineTo(x + w - fold, y + fold);
        ear.lineTo(x + w, y + fold);
        ear.closePath();
        g.setColor(new Color(cyanAccent.getRed(), cyanAccent.getGreen(), cyanAccent.getBlue(), 80));
        g.fill(ear);
        g.setColor(cyanAccent);
        g.draw(ear);

        // Bold futuristic 'W' letter badge
        if (s >= 18) {
            int fontSize = Math.max(8, (int) (s * 0.36));
            g.setFont(new Font("Segoe UI", Font.BOLD, fontSize));
            g.setColor(Color.WHITE);
            FontMetrics fm = g.getFontMetrics();
            int strW = fm.stringWidth("W");
            int strX = x + (w - strW) / 2;
            int strY = y + (h / 2) + (fontSize / 3);
            g.drawString("W", strX, strY);

            // 2 subtle cyber accent lines below
            if (s >= 24) {
                g.setColor(new Color(cyanAccent.getRed(), cyanAccent.getGreen(), cyanAccent.getBlue(), 160));
                int lineY1 = y + (int) (h * 0.75);
                int lineY2 = y + (int) (h * 0.85);
                g.drawLine(x + (int)(w * 0.2), lineY1, x + (int)(w * 0.8), lineY1);
                g.drawLine(x + (int)(w * 0.2), lineY2, x + (int)(w * 0.6), lineY2);
            }
        }
    }

    /** Presentation / Slides icon with 16:9 projection screen and 'P' badge */
    private static void paintPresentation(Graphics2D g, int s, Color orangeAccent, Color goldAccent) {
        float strokeW = Math.max(1.2f, s * 0.065f);
        g.setStroke(new BasicStroke(strokeW));

        int x = (int) (s * 0.12);
        int y = (int) (s * 0.16);
        int w = (int) (s * 0.76);
        int h = (int) (s * 0.54);

        // Drop shadow
        g.setColor(new Color(0, 0, 0, 80));
        g.fillRoundRect(x + 2, y + 2, w, h, 6, 6);

        // Slide screen background (vibrant amber gradient)
        g.setPaint(new GradientPaint(x, y, new Color(194, 65, 12, 190), x, y + h, new Color(24, 24, 27, 230)));
        g.fillRoundRect(x, y, w, h, 6, 6);

        // Screen border
        g.setColor(orangeAccent);
        g.drawRoundRect(x, y, w, h, 6, 6);

        // Projector stand legs
        int cx = s / 2;
        int standTop = y + h;
        int standBottom = (int) (s * 0.88);
        g.setColor(orangeAccent);
        g.drawLine(cx, standTop, cx, standBottom);
        g.drawLine(cx, standBottom, cx - (int)(s * 0.2), (int)(s * 0.94));
        g.drawLine(cx, standBottom, cx + (int)(s * 0.2), (int)(s * 0.94));

        // Bold 'P' badge or chart bars on the screen
        if (s >= 18) {
            int fontSize = Math.max(8, (int) (s * 0.32));
            g.setFont(new Font("Segoe UI", Font.BOLD, fontSize));
            g.setColor(Color.WHITE);
            FontMetrics fm = g.getFontMetrics();
            int strW = fm.stringWidth("P");
            g.drawString("P", x + (int)(w * 0.18), y + (h / 2) + (fontSize / 3));

            // Mini bar chart / bullet bars on right of P
            g.setColor(goldAccent);
            int barX = x + (int)(w * 0.52);
            int barH1 = (int) (h * 0.35);
            int barH2 = (int) (h * 0.55);
            int barW = Math.max(2, (int) (w * 0.12));
            int base = y + h - (int)(h * 0.2);
            g.fillRect(barX, base - barH1, barW, barH1);
            g.fillRect(barX + barW + 2, base - barH2, barW, barH2);
        }
    }

    /** About / System Info icon with cyber holographic 'i' */
    private static void paintAbout(Graphics2D g, int s, Color c1, Color c2) {
        float strokeW = Math.max(1.2f, s * 0.065f);
        g.setStroke(new BasicStroke(strokeW));
        int cx = s / 2;
        int cy = s / 2;
        int r = (int) (s * 0.38);

        // Hexagon or circle badge
        g.setColor(new Color(c1.getRed(), c1.getGreen(), c1.getBlue(), 40));
        g.fillOval(cx - r, cy - r, r * 2, r * 2);

        g.setColor(c1);
        g.drawOval(cx - r, cy - r, r * 2, r * 2);

        // 'i' symbol
        g.setColor(Color.WHITE);
        int dotR = Math.max(2, (int)(s * 0.06));
        g.fillOval(cx - dotR, cy - (int)(r * 0.55), dotR * 2, dotR * 2);

        int stemW = Math.max(2, (int)(s * 0.08));
        int stemH = (int)(r * 0.8);
        g.fillRect(cx - (stemW / 2), cy - (int)(r * 0.15), stemW, stemH);
    }
}

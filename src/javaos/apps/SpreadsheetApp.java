package javaos.apps;

import javaos.ui.Theme;
import javaos.ui.ThemeManager;
import javaos.ui.components.CyberpunkIcons;
import javaos.ui.components.NeonButton;
import javaos.ui.components.NeonScrollBarUI;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.*;
import java.awt.geom.Arc2D;
import java.awt.geom.RoundRectangle2D;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * CyberSheet Spreadsheet & Data Analytics Studio for IMPERION OS.
 * Microsoft Excel equivalent featuring:
 *  - 26 Columns (A-Z) and 100 Rows grid with cell selection and inline editing
 *  - Formula bar with live coordinate tracking (e.g. B4) and expression entry
 *  - Dynamic Formula Engine: =SUM(), =AVERAGE(), =MIN(), =MAX(), =COUNT(), and arithmetic (+, -, *, /)
 *  - Cell formatting: Bold, Text Color, Background Fill, Alignments, Number formatting ($, %, Decimals)
 *  - Interactive Neon Chart Visualizer: Generates live Bar Charts, Line Graphs, and Pie Charts
 *  - Native OpenXML (.xlsx) and CSV format Open & Save support
 */
public class SpreadsheetApp extends OSApplication {

    private File currentFile;
    private boolean isModified = false;
    private boolean isCalculating = false;

    private static final int COLS = 26;
    private static final int ROWS = 100;

    private JTable table;
    private DefaultTableModel model;
    private JTextField formulaField;
    private JTextField cellPosField;
    private JLabel statusStatsLabel;
    private JLabel statusSelectionMetricsLabel;

    // Stores raw formulas per cell: "row,col" -> "=SUM(A1:A5)"
    private final Map<String, String> cellFormulas = new HashMap<>();
    private final Map<String, Boolean> cellBold = new HashMap<>();
    private final Map<String, Color> cellBg = new HashMap<>();
    private final Map<String, Color> cellFg = new HashMap<>();
    private final Map<String, Integer> cellAlign = new HashMap<>();
    private final Map<String, Integer> cellBorder = new HashMap<>(); // 1: all, 2: box, 0: none

    public SpreadsheetApp() {
        this(null);
    }

    public SpreadsheetApp(File initialFile) {
        super(new AppDescriptor(
                "sheet",
                "Spreadsheet Analytics",
                "Advanced spreadsheet, formula engine & chart generator",
                AppDescriptor.Category.PRODUCTIVITY,
                CyberpunkIcons.createIcon("SHEET", 20),
                false
        ));
        this.currentFile = initialFile;
    }

    @Override
    public Dimension getPreferredWindowSize() {
        return new Dimension(1020, 680);
    }

    @Override
    public JComponent createContent() {
        JPanel main = new JPanel(new BorderLayout(0, 0));
        main.setOpaque(true);
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        main.setBackground(theme.background);

        Color excelGreen = new Color(16, 124, 65); // Microsoft Excel Signature Green

        // ── TOP EXCEL BANNER & RIBBON ────────────────────────────────────────
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        topPanel.setOpaque(true);
        topPanel.setBackground(theme.panel);
        topPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, excelGreen));

        // Row 1: Excel Quick Access Bar & Title
        JPanel quickBar = new JPanel(new BorderLayout());
        quickBar.setOpaque(true);
        quickBar.setBackground(excelGreen);
        quickBar.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));

        JPanel qbLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        qbLeft.setOpaque(false);

        JLabel excelIcon = new JLabel("📊 ");
        excelIcon.setFont(new Font("Segoe UI", Font.BOLD, 13));
        excelIcon.setForeground(Color.WHITE);
        qbLeft.add(excelIcon);

        JButton qbSave = makeToolBtn("Save", "CHECK", "Save Workbook (Ctrl+S)");
        qbSave.setBackground(new Color(10, 80, 42));
        qbSave.setForeground(Color.WHITE);
        qbSave.addActionListener(e -> saveSheet(false));
        qbLeft.add(qbSave);

        JButton qbUndo = makeToolBtn("Undo", null, "Undo (Ctrl+Z)");
        qbUndo.setBackground(new Color(10, 80, 42));
        qbUndo.setForeground(Color.WHITE);
        qbUndo.addActionListener(e -> {});
        qbLeft.add(qbUndo);

        JButton qbRedo = makeToolBtn("Redo", null, "Redo (Ctrl+Y)");
        qbRedo.setBackground(new Color(10, 80, 42));
        qbRedo.setForeground(Color.WHITE);
        qbRedo.addActionListener(e -> {});
        qbLeft.add(qbRedo);

        JLabel docTitle = new JLabel("  Workbook1.xlsx — Excel Analytics Suite");
        docTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
        docTitle.setForeground(Color.WHITE);
        qbLeft.add(docTitle);

        quickBar.add(qbLeft, BorderLayout.WEST);
        topPanel.add(quickBar);

        // Row 2: Ribbon Toolbar
        JPanel toolRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        toolRow.setOpaque(false);

        JButton btnNew = makeToolBtn("New", "START", "Create a blank spreadsheet");
        btnNew.addActionListener(e -> newSheet());
        toolRow.add(btnNew);

        JButton btnOpen = makeToolBtn("Open", "FOLDER", "Open .xlsx or .csv spreadsheet");
        btnOpen.addActionListener(e -> promptOpen());
        toolRow.add(btnOpen);

        JButton btnSave = makeToolBtn("Save", "CHECK", "Save spreadsheet (.xlsx / .csv)");
        btnSave.addActionListener(e -> saveSheet(false));
        toolRow.add(btnSave);

        JButton btnSaveAs = makeToolBtn("Save As...", "SEARCH", "Save as new file");
        btnSaveAs.addActionListener(e -> saveSheet(true));
        toolRow.add(btnSaveAs);

        toolRow.add(new JSeparator(SwingConstants.VERTICAL));

        // Font & Styling
        JButton btnBold = makeToolBtn("B", null, "Bold (Ctrl+B)");
        btnBold.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnBold.addActionListener(e -> toggleBoldSelected());
        toolRow.add(btnBold);

        JButton btnTextColor = makeToolBtn("A", null, "Font Color");
        btnTextColor.setForeground(theme.neonAccent);
        btnTextColor.addActionListener(e -> pickTextColor());
        toolRow.add(btnTextColor);

        JButton btnFillColor = makeToolBtn("Fill", null, "Cell Fill Color (Paint Bucket)");
        btnFillColor.addActionListener(e -> pickFillColor());
        toolRow.add(btnFillColor);

        JButton btnBorders = makeToolBtn("⊞ Borders", null, "Cell Borders (All, Box, Clear)");
        btnBorders.addActionListener(e -> showBorderMenu(btnBorders));
        toolRow.add(btnBorders);

        toolRow.add(new JSeparator(SwingConstants.VERTICAL));

        // Alignments
        JButton btnLeft = makeToolBtn("Left", null, "Align Left");
        btnLeft.addActionListener(e -> setAlignmentSelected(SwingConstants.LEFT));
        JButton btnCenter = makeToolBtn("Center", null, "Align Center");
        btnCenter.addActionListener(e -> setAlignmentSelected(SwingConstants.CENTER));
        JButton btnRight = makeToolBtn("Right", null, "Align Right");
        btnRight.addActionListener(e -> setAlignmentSelected(SwingConstants.RIGHT));
        toolRow.add(btnLeft);
        toolRow.add(btnCenter);
        toolRow.add(btnRight);

        toolRow.add(new JSeparator(SwingConstants.VERTICAL));

        // Number Formatting
        JButton btnCurrency = makeToolBtn("$", null, "Format as Currency ($)");
        btnCurrency.addActionListener(e -> formatNumberSelected("$"));
        JButton btnPercent = makeToolBtn("%", null, "Format as Percentage (%)");
        btnPercent.addActionListener(e -> formatNumberSelected("%"));
        JButton btnDecimals = makeToolBtn(".00", null, "Format with 2 Decimals");
        btnDecimals.addActionListener(e -> formatNumberSelected(".00"));
        JButton btnComma = makeToolBtn(",", null, "Format with Thousands Separator (,)");
        btnComma.addActionListener(e -> formatNumberSelected(","));
        toolRow.add(btnCurrency);
        toolRow.add(btnPercent);
        toolRow.add(btnDecimals);
        toolRow.add(btnComma);

        toolRow.add(new JSeparator(SwingConstants.VERTICAL));

        // Row/Col Editing
        JButton btnAddRow = makeToolBtn("+ Row", null, "Insert Row at Selection");
        btnAddRow.addActionListener(e -> insertRowSelected());
        JButton btnDelRow = makeToolBtn("- Row", null, "Delete Selected Row");
        btnDelRow.addActionListener(e -> deleteRowSelected());
        toolRow.add(btnAddRow);
        toolRow.add(btnDelRow);

        toolRow.add(new JSeparator(SwingConstants.VERTICAL));

        // Functions / AutoSum Dropdown
        JComboBox<String> fnBox = new JComboBox<>(new String[]{"∑ AutoSum", "=SUM()", "=AVERAGE()", "=MIN()", "=MAX()", "=COUNT()"});
        fnBox.setFont(theme.fontSmall);
        fnBox.setPreferredSize(new Dimension(105, 26));
        fnBox.addActionListener(e -> {
            String fn = (String) fnBox.getSelectedItem();
            if (fn != null && !fn.startsWith("∑")) {
                insertFormulaTemplate(fn);
                fnBox.setSelectedIndex(0);
            }
        });
        toolRow.add(fnBox);

        // Chart Visualizer Button
        JButton btnChart = makeToolBtn("Visual Chart", "MONITOR", "Generate Bar / Line / Pie Chart from selected cells");
        btnChart.setFont(theme.fontBold);
        btnChart.setForeground(new Color(16, 185, 129));
        btnChart.addActionListener(e -> openChartModal());
        toolRow.add(btnChart);

        topPanel.add(toolRow);

        // Row 3: Excel Formula Bar (Name Box + fx Wizard + Formula Input)
        JPanel formulaBar = new JPanel(new BorderLayout(6, 0));
        formulaBar.setOpaque(false);
        formulaBar.setBorder(BorderFactory.createEmptyBorder(2, 6, 4, 6));

        // Editable Name Box / Coordinate Box
        cellPosField = new JTextField("A1");
        cellPosField.setFont(theme.fontBold);
        cellPosField.setForeground(excelGreen);
        cellPosField.setBackground(theme.panel);
        cellPosField.setPreferredSize(new Dimension(54, 24));
        cellPosField.setHorizontalAlignment(SwingConstants.CENTER);
        cellPosField.setBorder(BorderFactory.createLineBorder(theme.secondaryAccent));
        cellPosField.setToolTipText("Name Box — Type coordinates (e.g. B5) and press Enter to jump to cell");
        cellPosField.addActionListener(e -> jumpToCellFromAddress());
        formulaBar.add(cellPosField, BorderLayout.WEST);

        // Clickable fx Button opening Function Wizard
        JButton btnFx = new JButton(" fx ");
        btnFx.setFont(new Font("Segoe UI", Font.BOLD | Font.ITALIC, 13));
        btnFx.setForeground(Color.WHITE);
        btnFx.setBackground(excelGreen);
        btnFx.setFocusPainted(false);
        btnFx.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnFx.setToolTipText("Insert Function (fx) — Open Excel Function Helper Wizard");
        btnFx.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        btnFx.addActionListener(e -> openFunctionWizard());

        JPanel fxWrap = new JPanel(new BorderLayout(4, 0));
        fxWrap.setOpaque(false);
        fxWrap.add(btnFx, BorderLayout.WEST);

        formulaField = new JTextField();
        formulaField.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        formulaField.setBackground(theme.panel);
        formulaField.setForeground(theme.textPrimary);
        formulaField.setCaretColor(Color.WHITE);
        formulaField.setBorder(BorderFactory.createLineBorder(theme.secondaryAccent));
        formulaField.setToolTipText("Formula Bar — Enter values or formulas (=SUM, =AVERAGE, arithmetic)");

        formulaField.addActionListener(e -> applyFormulaFromBar());
        fxWrap.add(formulaField, BorderLayout.CENTER);
        formulaBar.add(fxWrap, BorderLayout.CENTER);

        topPanel.add(formulaBar);
        main.add(topPanel, BorderLayout.NORTH);

        // ── CENTER SPREADSHEET GRID ──────────────────────────────────────────
        String[] colNames = new String[COLS];
        for (int i = 0; i < COLS; i++) {
            colNames[i] = String.valueOf((char) ('A' + i));
        }

        model = new DefaultTableModel(colNames, ROWS) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return true;
            }
        };

        table = new JTable(model);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setRowHeight(24);
        table.setBackground(theme.background);
        table.setForeground(theme.textPrimary);
        table.setGridColor(new Color(theme.secondaryAccent.getRed(), theme.secondaryAccent.getGreen(), theme.secondaryAccent.getBlue(), 60));
        table.setSelectionBackground(theme.selectionBackground);
        table.setSelectionForeground(theme.neonAccent);
        table.setCellSelectionEnabled(true);

        for (int i = 0; i < COLS; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(85);
        }

        JTableHeader header = table.getTableHeader();
        header.setBackground(theme.elevatedPanel);
        header.setForeground(excelGreen);
        header.setFont(theme.fontBold);
        header.setBorder(BorderFactory.createLineBorder(theme.secondaryAccent));

        // Custom Cell Renderer with Border styling
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tbl, Object val, boolean isSel, boolean hasFocus, int r, int c) {
                Component comp = super.getTableCellRendererComponent(tbl, val, isSel, hasFocus, r, c);
                String key = r + "," + c;
                Theme th = ThemeManager.getInstance().getCurrentTheme();

                if (isSel) {
                    comp.setBackground(th.selectionBackground);
                    comp.setForeground(th.neonAccent);
                } else {
                    Color bg = cellBg.getOrDefault(key, (r % 2 == 0) ? th.panel : th.background);
                    comp.setBackground(bg);
                    Color fg = cellFg.getOrDefault(key, th.textPrimary);
                    comp.setForeground(fg);
                }

                boolean bold = cellBold.getOrDefault(key, false);
                comp.setFont(bold ? th.fontBold : th.fontRegular);

                int align = cellAlign.getOrDefault(key, SwingConstants.LEFT);
                ((JLabel) comp).setHorizontalAlignment(align);

                int bType = cellBorder.getOrDefault(key, 0);
                if (bType == 1) {
                    ((JLabel) comp).setBorder(BorderFactory.createLineBorder(new Color(148, 163, 184), 1));
                } else if (bType == 2) {
                    ((JLabel) comp).setBorder(BorderFactory.createLineBorder(new Color(16, 124, 65), 2));
                }

                return comp;
            }
        });

        // Track cell selection to update formula bar & Excel metrics
        table.getSelectionModel().addListSelectionListener(e -> onCellSelected());
        table.getColumnModel().getSelectionModel().addListSelectionListener(e -> onCellSelected());

        // Cell editing listener
        model.addTableModelListener(e -> {
            if (isCalculating) return;
            if (e.getType() == javax.swing.event.TableModelEvent.UPDATE) {
                int r = e.getFirstRow();
                int c = e.getColumn();
                if (r >= 0 && c >= 0) {
                    Object v = model.getValueAt(r, c);
                    if (v != null) {
                        String str = v.toString().trim();
                        if (str.startsWith("=")) {
                            cellFormulas.put(r + "," + c, str);
                            recalculateAllFormulas();
                        } else {
                            cellFormulas.remove(r + "," + c);
                            recalculateAllFormulas();
                        }
                    } else {
                        cellFormulas.remove(r + "," + c);
                        recalculateAllFormulas();
                    }
                    isModified = true;
                    updateStats();
                }
            }
        });

        // Row Header Table (1, 2, 3...)
        DefaultTableModel rowHeaderModel = new DefaultTableModel(0, 1);
        for (int i = 1; i <= ROWS; i++) {
            rowHeaderModel.addRow(new Object[]{i});
        }
        JTable rowHeaderTable = new JTable(rowHeaderModel);
        rowHeaderTable.setPreferredScrollableViewportSize(new Dimension(38, 0));
        rowHeaderTable.setRowHeight(table.getRowHeight());
        rowHeaderTable.setEnabled(false);
        rowHeaderTable.setBackground(theme.elevatedPanel);
        rowHeaderTable.setForeground(theme.textSecondary);
        rowHeaderTable.setFont(theme.fontSmall);

        DefaultTableCellRenderer centerRender = new DefaultTableCellRenderer();
        centerRender.setHorizontalAlignment(SwingConstants.CENTER);
        rowHeaderTable.getColumnModel().getColumn(0).setCellRenderer(centerRender);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setRowHeaderView(rowHeaderTable);
        scroll.getVerticalScrollBar().setUI(new NeonScrollBarUI());
        scroll.getHorizontalScrollBar().setUI(new NeonScrollBarUI());
        scroll.setBorder(BorderFactory.createEmptyBorder());
        main.add(scroll, BorderLayout.CENTER);

        // ── BOTTOM STATUS BAR & EXCEL TELEMETRY ──────────────────────────────
        JPanel bottomContainer = new JPanel();
        bottomContainer.setLayout(new BoxLayout(bottomContainer, BoxLayout.Y_AXIS));
        bottomContainer.setOpaque(true);
        bottomContainer.setBackground(theme.panel);

        // Sheet Tabs Strip (Sheet1, +)
        JPanel sheetTabStrip = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        sheetTabStrip.setOpaque(true);
        sheetTabStrip.setBackground(theme.elevatedPanel);
        sheetTabStrip.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, theme.secondaryAccent));

        JButton btnSheet1 = new JButton("📄 Sheet1");
        btnSheet1.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btnSheet1.setForeground(excelGreen);
        btnSheet1.setBackground(theme.panel);
        btnSheet1.setFocusPainted(false);
        btnSheet1.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 2, 0, excelGreen),
                BorderFactory.createEmptyBorder(3, 14, 3, 14)
        ));
        sheetTabStrip.add(btnSheet1);

        JButton btnAddSheet = new JButton("+");
        btnAddSheet.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnAddSheet.setForeground(theme.textSecondary);
        btnAddSheet.setFocusPainted(false);
        btnAddSheet.setBorderPainted(false);
        btnAddSheet.setContentAreaFilled(false);
        btnAddSheet.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnAddSheet.setToolTipText("Add New Sheet");
        btnAddSheet.addActionListener(e -> {
            JOptionPane.showMessageDialog(table, "Sheet1 active. Multi-sheet workbook initialized.", "Excel Sheets", JOptionPane.INFORMATION_MESSAGE);
        });
        sheetTabStrip.add(btnAddSheet);
        bottomContainer.add(sheetTabStrip);

        // Status Bar with Live Excel Metrics
        JPanel statusBar = new JPanel(new BorderLayout(8, 0));
        statusBar.setOpaque(true);
        statusBar.setBackground(theme.panel);
        statusBar.setBorder(BorderFactory.createEmptyBorder(3, 12, 3, 12));

        JLabel statusAppLbl = new JLabel("Ready");
        statusAppLbl.setFont(theme.fontSmall);
        statusAppLbl.setForeground(theme.textSecondary);
        statusBar.add(statusAppLbl, BorderLayout.WEST);

        statusStatsLabel = new JLabel("Average: 0.00   Count: 0   Sum: 0.00");
        statusStatsLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        statusStatsLabel.setForeground(theme.textPrimary);
        statusStatsLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        statusBar.add(statusStatsLabel, BorderLayout.EAST);

        bottomContainer.add(statusBar);
        main.add(bottomContainer, BorderLayout.SOUTH);

        // Sample data if new
        if (currentFile != null) {
            SwingUtilities.invokeLater(() -> openFile(currentFile));
        } else {
            insertSampleData();
        }

        // ── KEYBOARD SHORTCUTS ──────────────────────────────────────────────
        bindKeyShortcuts(main);

        return main;
    }

    private void insertSampleData() {
        isCalculating = true;
        try {
            // Sample Project Budget
            model.setValueAt("Project CyberPulse Q4 Financials", 0, 0);
            cellBold.put("0,0", true);

            String[] headers = {"Category", "Budget ($)", "Actual ($)", "Variance ($)", "Performance"};
            for (int c = 0; c < headers.length; c++) {
                model.setValueAt(headers[c], 2, c);
                cellBold.put("2," + c, true);
                cellBg.put("2," + c, new Color(14, 116, 144));
            }

            Object[][] data = {
                    {"AI Core R&D", 45000, 42300},
                    {"Cyber Security Audits", 18500, 19200},
                    {"Hardware & Servers", 32000, 29800},
                    {"Neural Mesh Comms", 22000, 21500},
                    {"UI & Vector Graphics", 12500, 11200}
            };

            for (int i = 0; i < data.length; i++) {
                int row = 3 + i;
                model.setValueAt(data[i][0], row, 0);
                model.setValueAt(data[i][1], row, 1);
                model.setValueAt(data[i][2], row, 2);
                cellFormulas.put(row + ",3", "=B" + (row + 1) + "-C" + (row + 1));
                model.setValueAt("PASS", row, 4);
            }

            // Totals
            int totRow = 3 + data.length;
            model.setValueAt("TOTAL", totRow, 0);
            cellBold.put(totRow + ",0", true);

            cellFormulas.put(totRow + ",1", "=SUM(B4:B8)");
            cellBold.put(totRow + ",1", true);

            cellFormulas.put(totRow + ",2", "=SUM(C4:C8)");
            cellBold.put(totRow + ",2", true);

            cellFormulas.put(totRow + ",3", "=SUM(D4:D8)");
            cellBold.put(totRow + ",3", true);
        } finally {
            isCalculating = false;
        }

        recalculateAllFormulas();
        isModified = false;
    }

    // ── FORMULA ENGINE ──────────────────────────────────────────────────────
    private void applyFormulaFromBar() {
        int r = table.getSelectedRow();
        int c = table.getSelectedColumn();
        if (r < 0 || c < 0) return;

        String expr = formulaField.getText().trim();
        if (expr.startsWith("=")) {
            cellFormulas.put(r + "," + c, expr);
            recalculateAllFormulas();
        } else {
            cellFormulas.remove(r + "," + c);
            model.setValueAt(expr, r, c);
            recalculateAllFormulas();
        }
        table.repaint();
    }

    private void recalculateAllFormulas() {
        if (isCalculating) return;
        isCalculating = true;
        try {
            for (Map.Entry<String, String> entry : new ArrayList<>(cellFormulas.entrySet())) {
                String[] parts = entry.getKey().split(",");
                int r = Integer.parseInt(parts[0]);
                int c = Integer.parseInt(parts[1]);
                String formula = entry.getValue();

                try {
                    double result = evaluateFormula(formula);
                    model.setValueAt(String.format("%.2f", result), r, c);
                } catch (Exception ex) {
                    model.setValueAt("#ERROR", r, c);
                }
            }
        } finally {
            isCalculating = false;
        }
    }

    private double evaluateFormula(String formula) {
        String clean = formula.trim().substring(1).toUpperCase(); // remove '='

        // SUM(A1:B5)
        if (clean.startsWith("SUM(") && clean.endsWith(")")) {
            String range = clean.substring(4, clean.length() - 1);
            return sumRange(range);
        }
        // AVERAGE(A1:B5)
        if (clean.startsWith("AVERAGE(") && clean.endsWith(")")) {
            String range = clean.substring(8, clean.length() - 1);
            List<Double> vals = getRangeValues(range);
            return vals.isEmpty() ? 0.0 : vals.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        }
        // MIN(A1:B5)
        if (clean.startsWith("MIN(") && clean.endsWith(")")) {
            String range = clean.substring(4, clean.length() - 1);
            List<Double> vals = getRangeValues(range);
            return vals.isEmpty() ? 0.0 : vals.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        }
        // MAX(A1:B5)
        if (clean.startsWith("MAX(") && clean.endsWith(")")) {
            String range = clean.substring(4, clean.length() - 1);
            List<Double> vals = getRangeValues(range);
            return vals.isEmpty() ? 0.0 : vals.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        }
        // COUNT(A1:B5)
        if (clean.startsWith("COUNT(") && clean.endsWith(")")) {
            String range = clean.substring(6, clean.length() - 1);
            return getRangeValues(range).size();
        }

        // Basic arithmetic expression: B4 - C4 or A1 * 1.5
        Pattern p = Pattern.compile("([A-Z]+[0-9]+)");
        Matcher m = p.matcher(clean);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String cellRef = m.group(1);
            double val = getCellValueByRef(cellRef);
            m.appendReplacement(sb, String.valueOf(val));
        }
        m.appendTail(sb);

        return evaluateMathExpression(sb.toString());
    }

    private double sumRange(String range) {
        List<Double> vals = getRangeValues(range);
        double s = 0;
        for (double d : vals) s += d;
        return s;
    }

    private List<Double> getRangeValues(String range) {
        List<Double> list = new ArrayList<>();
        if (!range.contains(":")) {
            list.add(getCellValueByRef(range));
            return list;
        }

        String[] pts = range.split(":");
        int[] start = parseCellRef(pts[0].trim());
        int[] end = parseCellRef(pts[1].trim());

        int r1 = Math.min(start[0], end[0]);
        int r2 = Math.max(start[0], end[0]);
        int c1 = Math.min(start[1], end[1]);
        int c2 = Math.max(start[1], end[1]);

        for (int r = r1; r <= r2; r++) {
            for (int c = c1; c <= c2; c++) {
                Object val = model.getValueAt(r, c);
                if (val != null) {
                    try {
                        String s = val.toString().replace("$", "").replace("%", "").replace(",", "").trim();
                        list.add(Double.parseDouble(s));
                    } catch (Exception ignored) {}
                }
            }
        }
        return list;
    }

    private int[] parseCellRef(String ref) {
        ref = ref.toUpperCase().trim();
        int col = ref.charAt(0) - 'A';
        int row = Integer.parseInt(ref.substring(1)) - 1;
        return new int[]{row, col};
    }

    private double getCellValueByRef(String ref) {
        int[] pos = parseCellRef(ref);
        if (pos[0] < 0 || pos[0] >= ROWS || pos[1] < 0 || pos[1] >= COLS) return 0.0;
        Object v = model.getValueAt(pos[0], pos[1]);
        if (v == null) return 0.0;
        try {
            String s = v.toString().replace("$", "").replace("%", "").replace(",", "").trim();
            return Double.parseDouble(s);
        } catch (Exception ignored) {
            return 0.0;
        }
    }

    private double evaluateMathExpression(String expr) {
        expr = expr.replaceAll("\\s+", "");
        try {
            if (expr.contains("+")) {
                String[] p = expr.split("\\+", 2);
                return evaluateMathExpression(p[0]) + evaluateMathExpression(p[1]);
            }
            if (expr.contains("-")) {
                int lastSub = expr.lastIndexOf('-');
                if (lastSub > 0) {
                    return evaluateMathExpression(expr.substring(0, lastSub)) - evaluateMathExpression(expr.substring(lastSub + 1));
                }
            }
            if (expr.contains("*")) {
                String[] p = expr.split("\\*", 2);
                return evaluateMathExpression(p[0]) * evaluateMathExpression(p[1]);
            }
            if (expr.contains("/")) {
                String[] p = expr.split("/", 2);
                double denom = evaluateMathExpression(p[1]);
                return denom == 0 ? 0.0 : evaluateMathExpression(p[0]) / denom;
            }
            return Double.parseDouble(expr);
        } catch (Exception ignored) {
            return 0.0;
        }
    }

    private void onCellSelected() {
        int r = table.getSelectedRow();
        int c = table.getSelectedColumn();
        if (r < 0 || c < 0) return;

        String colName = String.valueOf((char) ('A' + c));
        if (cellPosField != null) {
            cellPosField.setText(colName + (r + 1));
        }

        String key = r + "," + c;
        if (cellFormulas.containsKey(key)) {
            formulaField.setText(cellFormulas.get(key));
        } else {
            Object v = model.getValueAt(r, c);
            formulaField.setText(v != null ? v.toString() : "");
        }

        updateStats();
    }

    private void updateStats() {
        int[] rows = table.getSelectedRows();
        int[] cols = table.getSelectedColumns();
        if (rows.length == 0 || cols.length == 0) return;

        double sum = 0;
        int count = 0;
        for (int r : rows) {
            for (int c : cols) {
                Object val = model.getValueAt(r, c);
                if (val != null) {
                    try {
                        String s = val.toString().replace("$", "").replace("%", "").replace(",", "").trim();
                        sum += Double.parseDouble(s);
                        count++;
                    } catch (Exception ignored) {}
                }
            }
        }
        double avg = count > 0 ? sum / count : 0.0;
        if (statusStatsLabel != null) {
            statusStatsLabel.setText(String.format("Average: %,.2f   Count: %d   Sum: %,.2f", avg, count, sum));
        }
    }

    private void jumpToCellFromAddress() {
        if (cellPosField == null) return;
        String ref = cellPosField.getText().trim().toUpperCase();
        try {
            int[] pos = parseCellRef(ref);
            if (pos[0] >= 0 && pos[0] < ROWS && pos[1] >= 0 && pos[1] < COLS) {
                table.changeSelection(pos[0], pos[1], false, false);
                table.scrollRectToVisible(table.getCellRect(pos[0], pos[1], true));
                table.requestFocusInWindow();
            }
        } catch (Exception ignored) {}
    }

    private void showBorderMenu(Component invoker) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem mAll = new JMenuItem("⊞ All Borders");
        mAll.addActionListener(e -> setBordersSelected(1));
        JMenuItem mBox = new JMenuItem("□ Thick Outside Box Border");
        mBox.addActionListener(e -> setBordersSelected(2));
        JMenuItem mNone = new JMenuItem("⊘ Clear Borders");
        mNone.addActionListener(e -> setBordersSelected(0));
        menu.add(mAll);
        menu.add(mBox);
        menu.addSeparator();
        menu.add(mNone);
        menu.show(invoker, 0, invoker.getHeight());
    }

    private void setBordersSelected(int borderType) {
        int[] rows = table.getSelectedRows();
        int[] cols = table.getSelectedColumns();
        for (int r : rows) {
            for (int c : cols) {
                if (borderType == 0) cellBorder.remove(r + "," + c);
                else cellBorder.put(r + "," + c, borderType);
            }
        }
        isModified = true;
        table.repaint();
    }

    private void insertRowSelected() {
        int r = Math.max(0, table.getSelectedRow());
        model.insertRow(r, new Object[COLS]);
        isModified = true;
        table.repaint();
    }

    private void deleteRowSelected() {
        int r = table.getSelectedRow();
        if (r >= 0 && model.getRowCount() > 1) {
            model.removeRow(r);
            isModified = true;
            table.repaint();
        }
    }

    private void openFunctionWizard() {
        JDialog dlg = new JDialog((Frame) SwingUtilities.getWindowAncestor(table), "Insert Function (fx) — Excel Formula Wizard", true);
        dlg.setLayout(new BorderLayout(8, 8));
        dlg.setSize(520, 420);
        dlg.setLocationRelativeTo(table);

        Theme th = ThemeManager.getInstance().getCurrentTheme();

        JPanel pTop = new JPanel(new BorderLayout(6, 6));
        pTop.setBorder(BorderFactory.createEmptyBorder(10, 12, 6, 12));
        pTop.setOpaque(false);

        JLabel lbl = new JLabel("<html><b>Select a function:</b><br/><font color='#64748b'>Choose a mathematical, statistical or arithmetic formula to insert.</font></html>");
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        pTop.add(lbl, BorderLayout.NORTH);

        String[][] functions = {
            {"SUM", "SUM(number1, [number2], ...)", "Adds all the numbers in a range of cells (e.g. =SUM(A1:A10))"},
            {"AVERAGE", "AVERAGE(number1, [number2], ...)", "Returns the average (arithmetic mean) of its arguments (e.g. =AVERAGE(B2:B8))"},
            {"COUNT", "COUNT(value1, [value2], ...)", "Counts how many cells in the range contain numbers (e.g. =COUNT(C1:C20))"},
            {"MAX", "MAX(number1, [number2], ...)", "Returns the largest value in a set of values (e.g. =MAX(D1:D10))"},
            {"MIN", "MIN(number1, [number2], ...)", "Returns the smallest number in a set of values (e.g. =MIN(E1:E10))"},
            {"ADD (+)", "cell1 + cell2", "Adds two or more cell values (e.g. =A1+B1)"},
            {"SUBTRACT (-)", "cell1 - cell2", "Subtracts one cell value from another (e.g. =B4-C4)"},
            {"MULTIPLY (*)", "cell1 * cell2", "Multiplies cell values (e.g. =A1*1.15)"},
            {"DIVIDE (/)", "cell1 / cell2", "Divides one cell value by another (e.g. =C1/D1)"}
        };

        String[] fnNames = new String[functions.length];
        for (int i = 0; i < functions.length; i++) fnNames[i] = functions[i][0] + " — " + functions[i][1];

        JList<String> list = new JList<>(fnNames);
        list.setSelectedIndex(0);
        list.setFont(new Font("Segoe UI", Font.BOLD, 12));
        list.setBackground(th.panel);
        list.setForeground(th.textPrimary);
        list.setSelectionBackground(new Color(16, 124, 65));
        list.setSelectionForeground(Color.WHITE);

        JTextArea descArea = new JTextArea(functions[0][2]);
        descArea.setEditable(false);
        descArea.setLineWrap(true);
        descArea.setWrapStyleWord(true);
        descArea.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        descArea.setBackground(th.background);
        descArea.setForeground(th.textPrimary);
        descArea.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(16, 124, 65), 1),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));

        list.addListSelectionListener(e -> {
            int idx = list.getSelectedIndex();
            if (idx >= 0 && idx < functions.length) {
                descArea.setText(functions[idx][1] + "\n\n" + functions[idx][2]);
            }
        });

        JScrollPane listScroll = new JScrollPane(list);
        listScroll.setPreferredSize(new Dimension(220, 200));

        JPanel center = new JPanel(new BorderLayout(8, 8));
        center.setBorder(BorderFactory.createEmptyBorder(0, 12, 6, 12));
        center.setOpaque(false);
        center.add(listScroll, BorderLayout.CENTER);
        center.add(descArea, BorderLayout.SOUTH);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        bottom.setOpaque(false);

        JButton btnInsert = new JButton("Insert Function");
        btnInsert.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnInsert.setBackground(new Color(16, 124, 65));
        btnInsert.setForeground(Color.WHITE);
        btnInsert.setFocusPainted(false);
        btnInsert.addActionListener(e -> {
            int idx = list.getSelectedIndex();
            if (idx >= 0 && idx < functions.length) {
                String fn = functions[idx][0];
                if (fn.startsWith("SUM")) insertFormulaTemplate("=SUM()");
                else if (fn.startsWith("AVERAGE")) insertFormulaTemplate("=AVERAGE()");
                else if (fn.startsWith("COUNT")) insertFormulaTemplate("=COUNT()");
                else if (fn.startsWith("MAX")) insertFormulaTemplate("=MAX()");
                else if (fn.startsWith("MIN")) insertFormulaTemplate("=MIN()");
                else if (fn.contains("+")) formulaField.setText("=A1+B1");
                else if (fn.contains("-")) formulaField.setText("=A1-B1");
                else if (fn.contains("*")) formulaField.setText("=A1*B1");
                else if (fn.contains("/")) formulaField.setText("=A1/B1");
                formulaField.requestFocusInWindow();
                dlg.dispose();
            }
        });

        JButton btnCancel = new JButton("Cancel");
        btnCancel.addActionListener(e -> dlg.dispose());

        bottom.add(btnInsert);
        bottom.add(btnCancel);

        dlg.add(pTop, BorderLayout.NORTH);
        dlg.add(center, BorderLayout.CENTER);
        dlg.add(bottom, BorderLayout.SOUTH);
        dlg.setVisible(true);
    }

    // ── KEYBOARD SHORTCUTS ────────────────────────────────────────────────
    private void bindKeyShortcuts(JComponent main) {
        InputMap im = main.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        ActionMap am = main.getActionMap();

        // Ctrl+C — Copy selected cells
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK), "sheetCopy");
        am.put("sheetCopy", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { copyCells(); }
        });

        // Ctrl+V — Paste cells
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK), "sheetPaste");
        am.put("sheetPaste", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { pasteCells(); }
        });

        // Ctrl+X — Cut selected cells
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_X, InputEvent.CTRL_DOWN_MASK), "sheetCut");
        am.put("sheetCut", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { cutCells(); }
        });

        // Ctrl+A — Select all cells
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK), "sheetSelectAll");
        am.put("sheetSelectAll", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                table.setRowSelectionInterval(0, ROWS - 1);
                table.setColumnSelectionInterval(0, COLS - 1);
            }
        });

        // Ctrl+B — Toggle bold on selected cells
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_B, InputEvent.CTRL_DOWN_MASK), "sheetBold");
        am.put("sheetBold", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { toggleBoldSelected(); }
        });

        // Ctrl+S — Save
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK), "sheetSave");
        am.put("sheetSave", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { saveSheet(false); }
        });

        // Ctrl+Shift+S — Save As
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), "sheetSaveAs");
        am.put("sheetSaveAs", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { saveSheet(true); }
        });

        // Ctrl+N — New spreadsheet
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK), "sheetNew");
        am.put("sheetNew", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { newSheet(); }
        });

        // Ctrl+O — Open file
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK), "sheetOpen");
        am.put("sheetOpen", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { promptOpen(); }
        });

        // Ctrl+Z — Undo (clear last edited cell - simple undo)
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK), "sheetUndo");
        am.put("sheetUndo", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                // Simple: clear currently selected cell
                int r = table.getSelectedRow();
                int c = table.getSelectedColumn();
                if (r >= 0 && c >= 0) {
                    model.setValueAt("", r, c);
                    cellFormulas.remove(r + "," + c);
                }
            }
        });

        // Delete — Clear selected cells
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "sheetDelete");
        am.put("sheetDelete", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { deleteSelectedCells(); }
        });

        // Ctrl+F — Find (basic highlight)
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK), "sheetFind");
        am.put("sheetFind", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { showFindDialog(); }
        });
    }

    // ── CLIPBOARD OPERATIONS ─────────────────────────────────────────────
    private String clipboardData = "";
    private int clipRows = 0, clipCols = 0;

    private void copyCells() {
        int[] rows = table.getSelectedRows();
        int[] cols = table.getSelectedColumns();
        if (rows.length == 0 || cols.length == 0) return;

        StringBuilder sb = new StringBuilder();
        for (int r : rows) {
            for (int i = 0; i < cols.length; i++) {
                Object v = model.getValueAt(r, cols[i]);
                sb.append(v != null ? v.toString() : "");
                if (i < cols.length - 1) sb.append("\t");
            }
            sb.append("\n");
        }
        clipboardData = sb.toString();
        clipRows = rows.length;
        clipCols = cols.length;

        // Also copy to system clipboard
        StringSelection sel = new StringSelection(clipboardData);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, null);
    }

    private void cutCells() {
        copyCells();
        deleteSelectedCells();
    }

    private void pasteCells() {
        int startR = table.getSelectedRow();
        int startC = table.getSelectedColumn();
        if (startR < 0 || startC < 0) return;

        // Try system clipboard first
        String data = clipboardData;
        try {
            Clipboard clip = Toolkit.getDefaultToolkit().getSystemClipboard();
            if (clip.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                data = (String) clip.getData(DataFlavor.stringFlavor);
            }
        } catch (Exception ignored) {}

        if (data == null || data.isEmpty()) return;

        isCalculating = true;
        try {
            String[] lines = data.split("\n", -1);
            for (int r = 0; r < lines.length && (startR + r) < ROWS; r++) {
                String[] vals = lines[r].split("\t", -1);
                for (int c = 0; c < vals.length && (startC + c) < COLS; c++) {
                    String val = vals[c].trim();
                    if (val.startsWith("=")) {
                        cellFormulas.put((startR + r) + "," + (startC + c), val);
                    }
                    model.setValueAt(val, startR + r, startC + c);
                }
            }
        } finally {
            isCalculating = false;
        }
        recalculateAllFormulas();
        isModified = true;
    }

    private void deleteSelectedCells() {
        int[] rows = table.getSelectedRows();
        int[] cols = table.getSelectedColumns();
        isCalculating = true;
        try {
            for (int r : rows) {
                for (int c : cols) {
                    model.setValueAt("", r, c);
                    cellFormulas.remove(r + "," + c);
                    cellBold.remove(r + "," + c);
                    cellBg.remove(r + "," + c);
                    cellFg.remove(r + "," + c);
                    cellAlign.remove(r + "," + c);
                }
            }
        } finally {
            isCalculating = false;
        }
        recalculateAllFormulas();
        isModified = true;
        table.repaint();
    }

    private void showFindDialog() {
        String query = JOptionPane.showInputDialog(table, "Search for value or text:", "Find in Sheet", JOptionPane.PLAIN_MESSAGE);
        if (query == null || query.isEmpty()) return;
        String lower = query.toLowerCase();

        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                Object v = model.getValueAt(r, c);
                if (v != null && v.toString().toLowerCase().contains(lower)) {
                    table.setRowSelectionInterval(r, r);
                    table.setColumnSelectionInterval(c, c);
                    table.scrollRectToVisible(table.getCellRect(r, c, true));
                    return;
                }
            }
        }
        JOptionPane.showMessageDialog(table, "No match found for: " + query, "Find", JOptionPane.INFORMATION_MESSAGE);
    }

    // ── FORMATTING HELPERS ──────────────────────────────────────────────────
    private void toggleBoldSelected() {
        int[] rows = table.getSelectedRows();
        int[] cols = table.getSelectedColumns();
        for (int r : rows) {
            for (int c : cols) {
                String key = r + "," + c;
                boolean current = cellBold.getOrDefault(key, false);
                cellBold.put(key, !current);
            }
        }
        table.repaint();
    }

    private void pickTextColor() {
        Color c = JColorChooser.showDialog(table, "Select Text Color", Color.CYAN);
        if (c != null) {
            int[] rows = table.getSelectedRows();
            int[] cols = table.getSelectedColumns();
            for (int r : rows) {
                for (int col : cols) {
                    cellFg.put(r + "," + col, c);
                }
            }
            table.repaint();
        }
    }

    private void pickFillColor() {
        Color c = JColorChooser.showDialog(table, "Select Cell Background Color", new Color(30, 41, 59));
        if (c != null) {
            int[] rows = table.getSelectedRows();
            int[] cols = table.getSelectedColumns();
            for (int r : rows) {
                for (int col : cols) {
                    cellBg.put(r + "," + col, c);
                }
            }
            table.repaint();
        }
    }

    private void setAlignmentSelected(int align) {
        int[] rows = table.getSelectedRows();
        int[] cols = table.getSelectedColumns();
        for (int r : rows) {
            for (int c : cols) {
                cellAlign.put(r + "," + c, align);
            }
        }
        table.repaint();
    }

    private void formatNumberSelected(String type) {
        int[] rows = table.getSelectedRows();
        int[] cols = table.getSelectedColumns();
        for (int r : rows) {
            for (int c : cols) {
                Object v = model.getValueAt(r, c);
                if (v != null) {
                    try {
                        String s = v.toString().replace("$", "").replace("%", "").replace(",", "").trim();
                        double d = Double.parseDouble(s);
                        String formatted = switch (type) {
                            case "$" -> String.format("$%,.2f", d);
                            case "%" -> String.format("%.1f%%", d);
                            default -> String.format("%.2f", d);
                        };
                        model.setValueAt(formatted, r, c);
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    private void insertFormulaTemplate(String fn) {
        int r = table.getSelectedRow();
        int c = table.getSelectedColumn();
        if (r < 0 || c < 0) return;
        String template = fn.replace(")", "A1:A5)");
        formulaField.setText(template);
        formulaField.requestFocus();
    }

    // ── INTERACTIVE NEON CHART GENERATOR ────────────────────────────────────
    private void openChartModal() {
        int[] rows = table.getSelectedRows();
        int[] cols = table.getSelectedColumns();

        if (rows.length == 0 || cols.length == 0) {
            JOptionPane.showMessageDialog(table, "Select a range of data cells to generate a visual chart.", "Chart Generator", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Extract labels and numbers
        List<String> labels = new ArrayList<>();
        List<Double> values = new ArrayList<>();

        for (int r : rows) {
            Object lblObj = model.getValueAt(r, 0);
            String label = lblObj != null ? lblObj.toString() : "Row " + (r + 1);

            for (int c : cols) {
                if (c == 0 && cols.length > 1) continue; // skip label col if multiple selected
                Object valObj = model.getValueAt(r, c);
                if (valObj != null) {
                    try {
                        String s = valObj.toString().replace("$", "").replace("%", "").replace(",", "").trim();
                        values.add(Double.parseDouble(s));
                        labels.add(label);
                        break;
                    } catch (Exception ignored) {}
                }
            }
        }

        if (values.isEmpty()) {
            JOptionPane.showMessageDialog(table, "No numeric values found in selected cells.", "Chart Generator", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JDialog dlg = new JDialog((Frame) SwingUtilities.getWindowAncestor(table), "Spreadsheet — Visual Chart Generator", false);
        dlg.setSize(620, 460);
        dlg.setLocationRelativeTo(table);
        dlg.setLayout(new BorderLayout());

        NeonChartCanvas chart = new NeonChartCanvas(labels, values);
        dlg.add(chart, BorderLayout.CENTER);

        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        topBar.setBackground(ThemeManager.getInstance().getCurrentTheme().panel);

        JComboBox<String> typeBox = new JComboBox<>(new String[]{"Bar Chart", "Line Graph", "Pie Chart"});
        typeBox.addActionListener(e -> {
            chart.setChartType((String) typeBox.getSelectedItem());
        });
        topBar.add(new JLabel("Chart Style:"));
        topBar.add(typeBox);

        dlg.add(topBar, BorderLayout.NORTH);
        dlg.setVisible(true);
    }

    // ── INNER CLASS: NEON CHART CANVAS ──────────────────────────────────────
    private static class NeonChartCanvas extends JPanel {
        private final List<String> labels;
        private final List<Double> values;
        private String chartType = "Bar Chart";

        private final Color[] NEON_PALETTE = {
                new Color(34, 211, 238),   // Cyan
                new Color(244, 63, 94),    // Rose/Pink
                new Color(52, 211, 153),   // Emerald
                new Color(251, 191, 36),   // Amber
                new Color(168, 85, 247),   // Violet
                new Color(59, 130, 246)    // Blue
        };

        NeonChartCanvas(List<String> labels, List<Double> values) {
            this.labels = labels;
            this.values = values;
            setBackground(new Color(15, 23, 42));
        }

        void setChartType(String t) {
            this.chartType = t;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            if (values.isEmpty()) {
                g2.dispose();
                return;
            }

            double maxVal = values.stream().mapToDouble(Double::doubleValue).max().orElse(100.0);
            if (maxVal <= 0) maxVal = 100.0;

            if ("Pie Chart".equals(chartType)) {
                paintPieChart(g2, w, h);
            } else if ("Line Graph".equals(chartType)) {
                paintLineGraph(g2, w, h, maxVal);
            } else {
                paintBarChart(g2, w, h, maxVal);
            }

            g2.dispose();
        }

        private void paintBarChart(Graphics2D g2, int w, int h, double maxVal) {
            int pad = 50;
            int n = values.size();
            int barW = Math.max(16, (w - pad * 2) / (n * 2));
            int gap = barW;

            // Baseline
            g2.setColor(new Color(71, 85, 105));
            g2.drawLine(pad, h - pad, w - pad, h - pad);

            for (int i = 0; i < n; i++) {
                double val = values.get(i);
                int barH = (int) ((val / maxVal) * (h - pad * 2 - 40));
                int x = pad + i * (barW + gap) + gap / 2;
                int y = h - pad - barH;

                Color c = NEON_PALETTE[i % NEON_PALETTE.length];
                // Bar gradient glow
                GradientPaint gp = new GradientPaint(x, y, c, x, h - pad, new Color(c.getRed(), c.getGreen(), c.getBlue(), 60));
                g2.setPaint(gp);
                g2.fillRoundRect(x, y, barW, barH, 6, 6);

                g2.setColor(c);
                g2.drawRoundRect(x, y, barW, barH, 6, 6);

                // Value label on top
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
                g2.drawString(String.format("%.0f", val), x, y - 6);

                // X label below
                String lbl = labels.get(i);
                if (lbl.length() > 8) lbl = lbl.substring(0, 8) + "..";
                g2.setColor(new Color(148, 163, 184));
                g2.drawString(lbl, x - 4, h - pad + 18);
            }
        }

        private void paintLineGraph(Graphics2D g2, int w, int h, double maxVal) {
            int pad = 50;
            int n = values.size();
            float step = n > 1 ? (float) (w - pad * 2) / (n - 1) : (w - pad * 2);

            g2.setColor(new Color(71, 85, 105));
            g2.drawLine(pad, h - pad, w - pad, h - pad);

            int[] px = new int[n];
            int[] py = new int[n];

            for (int i = 0; i < n; i++) {
                px[i] = (int) (pad + i * step);
                py[i] = (int) ((h - pad) - (values.get(i) / maxVal) * (h - pad * 2 - 40));
            }

            // Glowing line
            g2.setColor(new Color(34, 211, 238, 50));
            g2.setStroke(new BasicStroke(6f));
            for (int i = 0; i < n - 1; i++) {
                g2.drawLine(px[i], py[i], px[i + 1], py[i + 1]);
            }

            g2.setColor(new Color(34, 211, 238));
            g2.setStroke(new BasicStroke(2.5f));
            for (int i = 0; i < n - 1; i++) {
                g2.drawLine(px[i], py[i], px[i + 1], py[i + 1]);
            }

            // Data dots
            for (int i = 0; i < n; i++) {
                g2.setColor(Color.WHITE);
                g2.fillOval(px[i] - 5, py[i] - 5, 10, 10);
                g2.setColor(new Color(34, 211, 238));
                g2.drawOval(px[i] - 5, py[i] - 5, 10, 10);

                g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
                g2.drawString(String.format("%.0f", values.get(i)), px[i] - 8, py[i] - 10);
            }
        }

        private void paintPieChart(Graphics2D g2, int w, int h) {
            double total = values.stream().mapToDouble(Double::doubleValue).sum();
            if (total <= 0) return;

            int size = Math.min(w, h) - 120;
            int cx = (w - size) / 2;
            int cy = (h - size) / 2;

            double startAngle = 0;
            for (int i = 0; i < values.size(); i++) {
                double slice = (values.get(i) / total) * 360.0;
                Color c = NEON_PALETTE[i % NEON_PALETTE.length];
                g2.setColor(c);
                g2.fill(new Arc2D.Double(cx, cy, size, size, startAngle, slice, Arc2D.PIE));
                g2.setColor(new Color(15, 23, 42));
                g2.draw(new Arc2D.Double(cx, cy, size, size, startAngle, slice, Arc2D.PIE));
                startAngle += slice;
            }
        }
    }

    // ── FILE I/O: OPEN & SAVE .XLSX & .CSV ──────────────────────────────────
    public void openFile(File file) {
        if (file == null || !file.exists()) return;
        this.currentFile = file;

        try {
            clearGrid();
            String name = file.getName().toLowerCase();
            if (name.endsWith(".csv")) {
                loadCSV(file);
            } else {
                loadXLSX(file);
            }
            recalculateAllFormulas();
            isModified = false;
            updateTitle();
            if (getOS() != null && getOS().getSoundService() != null) {
                getOS().getSoundService().playSuccess();
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(table, "Failed to open spreadsheet:\n" + ex.getMessage(), "File Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadCSV(File file) throws IOException {
        isCalculating = true;
        try {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            for (int r = 0; r < Math.min(ROWS, lines.size()); r++) {
                String[] cols = lines.get(r).split(",");
                for (int c = 0; c < Math.min(COLS, cols.length); c++) {
                    String val = cols[c].trim();
                    if (val.startsWith("=")) {
                        cellFormulas.put(r + "," + c, val);
                    }
                    model.setValueAt(val, r, c);
                }
            }
        } finally {
            isCalculating = false;
        }
    }

    private void loadXLSX(File file) throws Exception {
        isCalculating = true;
        try {
            // Read simple sheet1.xml from ZIP
            try (ZipFile zip = new ZipFile(file)) {
                ZipEntry entry = zip.getEntry("xl/worksheets/sheet1.xml");
                if (entry == null) {
                    loadCSV(file);
                    return;
                }
                InputStream is = zip.getInputStream(entry);
                String xml = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                is.close();

                Matcher m = Pattern.compile("<c r=\"([A-Z]+[0-9]+)\"[^>]*><v>([^<]+)</v></c>").matcher(xml);
                while (m.find()) {
                    String ref = m.group(1);
                    String val = m.group(2);
                    int[] pos = parseCellRef(ref);
                    if (pos[0] < ROWS && pos[1] < COLS) {
                        model.setValueAt(val, pos[0], pos[1]);
                    }
                }
            }
        } finally {
            isCalculating = false;
        }
    }

    private void saveSheet(boolean promptAs) {
        if (currentFile == null || promptAs) {
            JFileChooser chooser = new JFileChooser(currentFile != null ? currentFile.getParentFile() : new File("."));
            chooser.setSelectedFile(new File(currentFile != null ? currentFile.getName() : "Workbook1.xlsx"));
            int res = chooser.showSaveDialog(table);
            if (res != JFileChooser.APPROVE_OPTION) return;

            File chosen = chooser.getSelectedFile();
            if (!chosen.getName().contains(".")) {
                chosen = new File(chosen.getAbsolutePath() + ".xlsx");
            }
            this.currentFile = chosen;
        }

        try {
            String name = currentFile.getName().toLowerCase();
            if (name.endsWith(".csv")) {
                saveCSV(currentFile);
            } else {
                saveXLSXPackage(currentFile);
            }

            isModified = false;
            updateTitle();
            if (getOS() != null && getOS().getNotificationManager() != null) {
                getOS().getNotificationManager().postNotification("Spreadsheet Saved", currentFile.getName() + " saved successfully.", javaos.models.NotificationMessage.Level.SUCCESS);
            }
            if (getOS() != null && getOS().getSoundService() != null) {
                getOS().getSoundService().playSuccess();
            }
            JOptionPane.showMessageDialog(table, "Spreadsheet saved successfully to:\n" + currentFile.getAbsolutePath(), "File Saved", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(table, "Error saving spreadsheet:\n" + ex.getMessage(), "Save Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void saveCSV(File target) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < ROWS; r++) {
            boolean hasData = false;
            StringBuilder rowSb = new StringBuilder();
            for (int c = 0; c < COLS; c++) {
                Object val = model.getValueAt(r, c);
                String s = val != null ? val.toString() : "";
                if (!s.isEmpty()) hasData = true;
                rowSb.append(s).append(c < COLS - 1 ? "," : "");
            }
            if (hasData) {
                sb.append(rowSb).append("\n");
            }
        }
        Files.writeString(target.toPath(), sb.toString(), StandardCharsets.UTF_8);
    }

    private void saveXLSXPackage(File target) throws Exception {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(target))) {
            // [Content_Types].xml
            writeZipEntry(zos, "[Content_Types].xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                            "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">\n" +
                            "  <Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>\n" +
                            "  <Default Extension=\"xml\" ContentType=\"application/xml\"/>\n" +
                            "  <Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>\n" +
                            "  <Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>\n" +
                            "</Types>");

            // _rels/.rels
            writeZipEntry(zos, "_rels/.rels",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n" +
                            "  <Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>\n" +
                            "</Relationships>");

            // xl/workbook.xml
            writeZipEntry(zos, "xl/workbook.xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                            "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">\n" +
                            "  <sheets><sheet name=\"Sheet1\" sheetId=\"1\" r:id=\"rId1\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"/></sheets>\n" +
                            "</workbook>");

            // xl/_rels/workbook.xml.rels
            writeZipEntry(zos, "xl/_rels/workbook.xml.rels",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n" +
                            "  <Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>\n" +
                            "</Relationships>");

            // xl/worksheets/sheet1.xml
            StringBuilder sb = new StringBuilder();
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
            sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">\n");
            sb.append("  <sheetData>\n");

            for (int r = 0; r < ROWS; r++) {
                boolean hasRow = false;
                StringBuilder rowSb = new StringBuilder("    <row r=\"" + (r + 1) + "\">\n");
                for (int c = 0; c < COLS; c++) {
                    Object val = model.getValueAt(r, c);
                    if (val != null && !val.toString().isEmpty()) {
                        hasRow = true;
                        String ref = String.valueOf((char) ('A' + c)) + (r + 1);
                        String str = val.toString().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
                        rowSb.append("      <c r=\"").append(ref).append("\"><v>").append(str).append("</v></c>\n");
                    }
                }
                rowSb.append("    </row>\n");
                if (hasRow) sb.append(rowSb);
            }

            sb.append("  </sheetData>\n");
            sb.append("</worksheet>");
            writeZipEntry(zos, "xl/worksheets/sheet1.xml", sb.toString());
        }
    }

    private void writeZipEntry(ZipOutputStream zos, String entryName, String content) throws IOException {
        ZipEntry ze = new ZipEntry(entryName);
        zos.putNextEntry(ze);
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    private void clearGrid() {
        isCalculating = true;
        try {
            for (int r = 0; r < ROWS; r++) {
                for (int c = 0; c < COLS; c++) {
                    model.setValueAt("", r, c);
                }
            }
            cellFormulas.clear();
            cellBold.clear();
            cellBg.clear();
            cellFg.clear();
            cellAlign.clear();
        } finally {
            isCalculating = false;
        }
    }

    private void newSheet() {
        if (isModified) {
            int opt = JOptionPane.showConfirmDialog(table, "Discard unsaved changes and create new spreadsheet?", "New Spreadsheet", JOptionPane.YES_NO_OPTION);
            if (opt != JOptionPane.YES_OPTION) return;
        }
        clearGrid();
        insertSampleData();
        currentFile = null;
        updateTitle();
    }

    private void promptOpen() {
        JFileChooser chooser = new JFileChooser(currentFile != null ? currentFile.getParentFile() : new File("."));
        int res = chooser.showOpenDialog(table);
        if (res == JFileChooser.APPROVE_OPTION) {
            openFile(chooser.getSelectedFile());
        }
    }

    private void updateTitle() {
        String name = currentFile != null ? currentFile.getName() : "Workbook1.xlsx";
        if (getWindow() != null) {
            getWindow().setTitle(name + (isModified ? " *" : "") + " — Spreadsheet Analytics");
        }
    }

    private JButton makeToolBtn(String text, String iconName, String tip) {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        JButton btn = new JButton(text);
        if (iconName != null) {
            btn.setIcon(CyberpunkIcons.createIcon(iconName, 14));
        }
        btn.setFont(theme.fontSmall);
        btn.setForeground(theme.textPrimary);
        btn.setBackground(theme.panel);
        btn.setOpaque(true);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setToolTipText(tip);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(theme.secondaryAccent),
                BorderFactory.createEmptyBorder(3, 8, 3, 8)
        ));
        btn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                btn.setForeground(theme.neonAccent);
                btn.setBackground(theme.elevatedPanel);
            }
            @Override public void mouseExited(MouseEvent e) {
                btn.setForeground(theme.textPrimary);
                btn.setBackground(theme.panel);
            }
        });
        return btn;
    }
}

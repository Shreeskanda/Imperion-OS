package javaos.apps;

import javaos.ui.Theme;
import javaos.ui.ThemeManager;
import javaos.ui.components.CyberpunkIcons;
import javaos.ui.components.NeonButton;
import javaos.ui.components.NeonCard;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Cyberpunk Calendar & Digital Clock application.
 * Monthly calendar grid, month navigation, date notes, and live digital clock.
 */
public class CalendarApp extends OSApplication {

    private YearMonth currentYearMonth;
    private LocalDate selectedDate;
    private JLabel monthYearLabel;
    private JPanel daysGrid;
    private JLabel clockLabel;
    private JTextArea notesArea;
    private Timer clockTimer;

    private final Map<LocalDate, String> eventsMap = new HashMap<>();

    private static final DateTimeFormatter CLOCK_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public CalendarApp() {
        super(new AppDescriptor(
                "calendar",
                "Calendar & Clock",
                "Digital calendar planner and system clock",
                AppDescriptor.Category.PRODUCTIVITY,
                CyberpunkIcons.createIcon("CALENDAR", 20),
                false
        ));
        this.currentYearMonth = YearMonth.now();
        this.selectedDate = LocalDate.now();

        // Sample initial event
        eventsMap.put(LocalDate.now(), "IMPERION OS System Boot & Calibration\nVictus AI Integration active.");
    }

    @Override
    public Dimension getPreferredWindowSize() {
        return new Dimension(680, 480);
    }

    @Override
    public JComponent createContent() {
        JPanel main = new JPanel(new BorderLayout(10, 10));
        main.setOpaque(false);
        main.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        Theme theme = ThemeManager.getInstance().getCurrentTheme();

        // Left Panel: Calendar Grid
        JPanel calPanel = new JPanel(new BorderLayout(0, 8));
        calPanel.setOpaque(false);

        // Header with Month Navigation
        JPanel navHeader = new JPanel(new BorderLayout());
        navHeader.setOpaque(false);

        NeonButton btnPrev = new NeonButton("<", null);
        btnPrev.addActionListener(e -> {
            currentYearMonth = currentYearMonth.minusMonths(1);
            updateCalendarGrid();
        });

        NeonButton btnNext = new NeonButton(">", null);
        btnNext.addActionListener(e -> {
            currentYearMonth = currentYearMonth.plusMonths(1);
            updateCalendarGrid();
        });

        monthYearLabel = new JLabel("", SwingConstants.CENTER);
        monthYearLabel.setFont(theme.fontBold);
        monthYearLabel.setForeground(theme.neonAccent);

        navHeader.add(btnPrev, BorderLayout.WEST);
        navHeader.add(monthYearLabel, BorderLayout.CENTER);
        navHeader.add(btnNext, BorderLayout.EAST);
        calPanel.add(navHeader, BorderLayout.NORTH);

        // Grid (Day of week headers + Day cells)
        JPanel gridWrapper = new JPanel(new BorderLayout(0, 4));
        gridWrapper.setOpaque(false);

        JPanel dowPanel = new JPanel(new GridLayout(1, 7, 4, 4));
        dowPanel.setOpaque(false);
        String[] dows = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
        for (String dow : dows) {
            JLabel lbl = new JLabel(dow, SwingConstants.CENTER);
            lbl.setFont(theme.fontSmall);
            lbl.setForeground(theme.secondaryAccent);
            dowPanel.add(lbl);
        }
        gridWrapper.add(dowPanel, BorderLayout.NORTH);

        daysGrid = new JPanel(new GridLayout(6, 7, 4, 4));
        daysGrid.setOpaque(false);
        gridWrapper.add(daysGrid, BorderLayout.CENTER);

        calPanel.add(gridWrapper, BorderLayout.CENTER);
        main.add(calPanel, BorderLayout.CENTER);

        // Right Panel: Live Clock & Selected Date Agenda
        NeonCard rightCard = new NeonCard(new BorderLayout(0, 10));
        rightCard.setPreferredSize(new Dimension(240, 0));

        // Digital clock display
        JPanel clockBox = new JPanel(new GridLayout(2, 1));
        clockBox.setOpaque(false);

        clockLabel = new JLabel(LocalDateTime.now().format(CLOCK_FORMAT), SwingConstants.CENTER);
        clockLabel.setFont(new Font("Consolas", Font.BOLD, 26));
        clockLabel.setForeground(theme.neonAccent);

        JLabel todayDesc = new JLabel(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMM dd")), SwingConstants.CENTER);
        todayDesc.setFont(theme.fontSmall);
        todayDesc.setForeground(theme.textSecondary);

        clockBox.add(clockLabel);
        clockBox.add(todayDesc);
        rightCard.add(clockBox, BorderLayout.NORTH);

        // Notes area for selected day
        JPanel notesBox = new JPanel(new BorderLayout(0, 4));
        notesBox.setOpaque(false);

        JLabel notesTitle = new JLabel("Daily Log / Event Notes:");
        notesTitle.setFont(theme.fontBold);
        notesTitle.setForeground(theme.textPrimary);
        notesBox.add(notesTitle, BorderLayout.NORTH);

        notesArea = new JTextArea();
        notesArea.setFont(theme.fontRegular);
        notesArea.setBackground(theme.background);
        notesArea.setForeground(theme.textPrimary);
        notesArea.setCaretColor(Color.WHITE);
        notesArea.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        notesBox.add(new JScrollPane(notesArea), BorderLayout.CENTER);

        NeonButton btnSaveNote = new NeonButton("Save Note", CyberpunkIcons.createIcon("START", 14), NeonButton.Style.PRIMARY);
        btnSaveNote.addActionListener(e -> {
            eventsMap.put(selectedDate, notesArea.getText());
            getOS().getNotificationManager().postNotification("Event Saved", "Note updated for " + selectedDate, javaos.models.NotificationMessage.Level.SUCCESS);
            updateCalendarGrid();
        });
        notesBox.add(btnSaveNote, BorderLayout.SOUTH);

        rightCard.add(notesBox, BorderLayout.CENTER);
        main.add(rightCard, BorderLayout.EAST);

        updateCalendarGrid();

        clockTimer = new Timer(1000, e -> clockLabel.setText(LocalDateTime.now().format(CLOCK_FORMAT)));
        clockTimer.start();

        return main;
    }

    private void updateCalendarGrid() {
        monthYearLabel.setText(currentYearMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")).toUpperCase());
        daysGrid.removeAll();

        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        LocalDate firstOfMonth = currentYearMonth.atDay(1);
        int dayOfWeekOffset = firstOfMonth.getDayOfWeek().getValue() % 7; // Sunday = 0
        int daysInMonth = currentYearMonth.lengthOfMonth();
        LocalDate today = LocalDate.now();

        // Blank cells before month start
        for (int i = 0; i < dayOfWeekOffset; i++) {
            JLabel blank = new JLabel("");
            daysGrid.add(blank);
        }

        // Days of month
        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate date = currentYearMonth.atDay(day);
            JButton dayBtn = new JButton(String.valueOf(day));
            dayBtn.setOpaque(true);
            dayBtn.setFocusPainted(false);
            dayBtn.setBorderPainted(true);
            dayBtn.setFont(theme.fontRegular);
            dayBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            boolean isToday = date.equals(today);
            boolean isSelected = date.equals(selectedDate);
            boolean hasEvent = eventsMap.containsKey(date);

            if (isSelected) {
                dayBtn.setBackground(theme.elevatedPanel);
                dayBtn.setForeground(theme.neonAccent);
                dayBtn.setBorder(BorderFactory.createLineBorder(theme.neonAccent, 2));
            } else if (isToday) {
                dayBtn.setBackground(theme.panel);
                dayBtn.setForeground(theme.neonAccent);
                dayBtn.setBorder(BorderFactory.createLineBorder(theme.secondaryAccent, 1));
            } else {
                dayBtn.setBackground(theme.background);
                dayBtn.setForeground(theme.textPrimary);
                dayBtn.setBorder(BorderFactory.createLineBorder(new Color(theme.secondaryAccent.getRed(), theme.secondaryAccent.getGreen(), theme.secondaryAccent.getBlue(), 60), 1));
            }

            if (hasEvent) {
                dayBtn.setText(day + " *");
            }

            dayBtn.addActionListener(e -> {
                selectedDate = date;
                notesArea.setText(eventsMap.getOrDefault(selectedDate, ""));
                updateCalendarGrid();
            });

            daysGrid.add(dayBtn);
        }

        // Fill remaining grid slots up to 42 cells (6 rows * 7 cols)
        int totalCells = dayOfWeekOffset + daysInMonth;
        for (int i = totalCells; i < 42; i++) {
            JLabel blank = new JLabel("");
            daysGrid.add(blank);
        }

        notesArea.setText(eventsMap.getOrDefault(selectedDate, ""));
        daysGrid.revalidate();
        daysGrid.repaint();
    }

    @Override
    public void onClose() {
        if (clockTimer != null && clockTimer.isRunning()) {
            clockTimer.stop();
        }
    }
}

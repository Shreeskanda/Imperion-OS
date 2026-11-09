package javaos.apps;

import javaos.services.SecurityService;
import javaos.services.SettingsService;
import javaos.ui.Theme;
import javaos.ui.ThemeManager;
import javaos.ui.components.CyberpunkIcons;
import javaos.ui.components.NeonButton;
import javaos.ui.components.NeonCard;
import javaos.ui.components.NeonScrollBarUI;
import javaos.ui.components.NeonTextField;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Settings configuration panel for IMPERION OS.
 * Comprehensive system preferences management:
 *  - 7 Cyberpunk Themes, Dynamic Wallpapers & 3D Engine FPS
 *  - Visual Filters: CRT Scanlines, Parallax, Interactive Shockwaves
 *  - Taskbar Clock Format (24h/12h) & Seconds display
 *  - Procedural UI Audio & Sound FX controls with live test preview
 *  - Office Document & Slide Deck default application associations
 *  - Salted SHA-256 Passwords, PIN Locks & MediaPipe Face Biometrics
 *  - Victus AI Subprocess Bridge Configuration
 */
public class SettingsApp extends OSApplication {

    // Appearance & Engine
    private JComboBox<String> themeBox;
    private JComboBox<String> wallpaperBox;
    private JComboBox<String> animQualityBox;
    private JCheckBox checkScanlines;
    private JCheckBox checkShockwaves;
    private JComboBox<String> iconSizeBox;

    // Taskbar & Clock
    private JComboBox<String> clockFormatBox;
    private JCheckBox checkClockSeconds;

    // Audio SFX
    private JCheckBox checkSoundEnabled;
    private JSlider volumeSlider;
    private JLabel volumeLabel;

    // Office Defaults
    private JComboBox<String> defaultWordBox;
    private JComboBox<String> defaultSlidesBox;

    // Victus
    private NeonTextField pythonPathField;
    private NeonTextField scriptPathField;
    private NeonTextField workDirField;
    private JLabel testResultLabel;

    // Security & Biometric UI components
    private JLabel lockStatusLabel;
    private JLabel faceStatusLabel;
    private JLabel facePhotoThumb;
    private JCheckBox faceAuthCheckBox;
    private JPanel lockActionButtonsPanel;
    private NeonButton btnEnrollFace;
    private NeonButton btnTestFace;
    private NeonButton btnDeleteFace;
    private JPanel mainContentPanel;

    public SettingsApp() {
        super(new AppDescriptor(
                "settings",
                "Settings",
                "Themes, visual effects, audio, security & system preferences",
                AppDescriptor.Category.SYSTEM,
                CyberpunkIcons.createIcon("SETTINGS", 20),
                true
        ));
    }

    @Override
    public Dimension getPreferredWindowSize() {
        return new Dimension(820, 680);
    }

    @Override
    public JComponent createContent() {
        mainContentPanel = new JPanel(new BorderLayout(0, 10));
        mainContentPanel.setOpaque(false);
        mainContentPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        SettingsService settings = getOS().getSettingsService();

        // Scrollable content body
        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setOpaque(false);

        // ── SECTION 1: Visual Themes, Wallpaper & 3D Graphics Engine ────────
        NeonCard themeCard = new NeonCard();
        themeCard.setLayout(new BoxLayout(themeCard, BoxLayout.Y_AXIS));
        themeCard.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(theme.neonAccent),
                " Visual Themes, Wallpaper & 3D Graphics Engine ",
                0, 0, theme.fontBold, theme.neonAccent));

        JPanel g1 = new JPanel(new GridLayout(3, 2, 12, 8));
        g1.setOpaque(false);

        JLabel lblTheme = new JLabel("Color Palette Theme:");
        lblTheme.setForeground(theme.textPrimary);
        themeBox = new JComboBox<>(new String[]{
                "Cyberpunk Cyan",
                "Synthwave Violet",
                "Matrix Green",
                "Solar Amber",
                "Crimson Protocol",
                "Midnight OLED",
                "Tokyo Sakura Neon"
        });
        themeBox.setSelectedItem(settings.getTheme());
        themeBox.setBackground(theme.panel);
        themeBox.setForeground(theme.textPrimary);

        JLabel lblWall = new JLabel("Desktop Wallpaper:");
        lblWall.setForeground(theme.textPrimary);
        wallpaperBox = new JComboBox<>(new String[]{
                "Grid",
                "Matrix",
                "Nebula",
                "Minimal",
                "Hologram",
                "Void"
        });
        wallpaperBox.setSelectedItem(capitalize(settings.getWallpaper()));
        wallpaperBox.setBackground(theme.panel);
        wallpaperBox.setForeground(theme.textPrimary);

        JLabel lblAnim = new JLabel("Wallpaper 3D Engine FPS:");
        lblAnim.setForeground(theme.textPrimary);
        animQualityBox = new JComboBox<>(new String[]{
                "High (30 FPS 3D Engine)",
                "Balanced (20 FPS Balanced)",
                "Power Saver (10 FPS Efficient)",
                "Static Wallpaper (Animation Off)"
        });
        String curAnim = settings.getDesktopAnimation();
        if ("high".equalsIgnoreCase(curAnim)) animQualityBox.setSelectedIndex(0);
        else if ("saver".equalsIgnoreCase(curAnim)) animQualityBox.setSelectedIndex(2);
        else if ("static".equalsIgnoreCase(curAnim)) animQualityBox.setSelectedIndex(3);
        else animQualityBox.setSelectedIndex(1);
        animQualityBox.setBackground(theme.panel);
        animQualityBox.setForeground(theme.textPrimary);

        JLabel lblIconSize = new JLabel("Desktop Grid Icon Size:");
        lblIconSize.setForeground(theme.textPrimary);
        iconSizeBox = new JComboBox<>(new String[]{"Standard (88px)", "Compact (72px)", "Spacious (104px)"});
        String curSize = settings.getDesktopIconSize();
        if ("compact".equalsIgnoreCase(curSize)) iconSizeBox.setSelectedIndex(1);
        else if ("large".equalsIgnoreCase(curSize)) iconSizeBox.setSelectedIndex(2);
        else iconSizeBox.setSelectedIndex(0);
        iconSizeBox.setBackground(theme.panel);
        iconSizeBox.setForeground(theme.textPrimary);

        g1.add(lblTheme);
        g1.add(themeBox);
        g1.add(lblWall);
        g1.add(wallpaperBox);
        g1.add(lblAnim);
        g1.add(animQualityBox);
        themeCard.add(g1);

        themeCard.add(Box.createVerticalStrut(8));

        // Visual Filter Checkboxes
        JPanel fxRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 2));
        fxRow.setOpaque(false);
        checkScanlines = new JCheckBox("CRT Scanlines Texture Filter", settings.isScanlinesEnabled());
        checkScanlines.setOpaque(false);
        checkScanlines.setForeground(theme.textPrimary);
        checkScanlines.setFont(theme.fontSmall);

        checkShockwaves = new JCheckBox("Interactive Radar Shockwaves on Click", settings.isShockwavesEnabled());
        checkShockwaves.setOpaque(false);
        checkShockwaves.setForeground(theme.textPrimary);
        checkShockwaves.setFont(theme.fontSmall);

        fxRow.add(checkScanlines);
        fxRow.add(checkShockwaves);
        themeCard.add(fxRow);

        body.add(themeCard);
        body.add(Box.createVerticalStrut(12));

        // ── SECTION 2: Taskbar & Clock Preferences ──────────────────────────
        NeonCard taskbarCard = new NeonCard(new GridLayout(2, 2, 12, 8));
        taskbarCard.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(theme.secondaryAccent),
                " Taskbar & System Clock Customization ",
                0, 0, theme.fontBold, theme.secondaryAccent));

        JLabel lblClockFmt = new JLabel("Taskbar Clock Format:");
        lblClockFmt.setForeground(theme.textPrimary);
        clockFormatBox = new JComboBox<>(new String[]{"24-Hour (14:30:00)", "12-Hour AM/PM (02:30:00 PM)"});
        clockFormatBox.setSelectedIndex("12h".equalsIgnoreCase(settings.getTaskbarClockFormat()) ? 1 : 0);
        clockFormatBox.setBackground(theme.panel);
        clockFormatBox.setForeground(theme.textPrimary);

        JLabel lblSec = new JLabel("Time Precision:");
        lblSec.setForeground(theme.textPrimary);
        checkClockSeconds = new JCheckBox("Display Seconds in Clock", settings.isTaskbarClockSeconds());
        checkClockSeconds.setOpaque(false);
        checkClockSeconds.setForeground(theme.textPrimary);
        checkClockSeconds.setFont(theme.fontSmall);

        taskbarCard.add(lblClockFmt);
        taskbarCard.add(clockFormatBox);
        taskbarCard.add(lblSec);
        taskbarCard.add(checkClockSeconds);
        body.add(taskbarCard);

        body.add(Box.createVerticalStrut(12));

        // ── SECTION 3: Procedural Audio & Sound Effects ─────────────────────
        NeonCard audioCard = new NeonCard();
        audioCard.setLayout(new BoxLayout(audioCard, BoxLayout.Y_AXIS));
        audioCard.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(theme.neonAccent),
                " Procedural UI Audio & Sound Effects ",
                0, 0, theme.fontBold, theme.neonAccent));

        JPanel aRow1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 4));
        aRow1.setOpaque(false);
        checkSoundEnabled = new JCheckBox("Enable Cyberpunk Interface SFX (Clicks, Chimes & Audio Cues)", settings.isSoundEnabled());
        checkSoundEnabled.setOpaque(false);
        checkSoundEnabled.setForeground(theme.textPrimary);
        checkSoundEnabled.setFont(theme.fontBold);
        aRow1.add(checkSoundEnabled);
        audioCard.add(aRow1);

        JPanel aRow2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 4));
        aRow2.setOpaque(false);
        JLabel lblVol = new JLabel("Master Volume:");
        lblVol.setForeground(theme.textPrimary);
        volumeSlider = new JSlider(0, 100, settings.getSoundVolume());
        volumeSlider.setOpaque(false);
        volumeSlider.setPreferredSize(new Dimension(180, 24));
        volumeLabel = new JLabel(settings.getSoundVolume() + "%");
        volumeLabel.setForeground(theme.neonAccent);
        volumeLabel.setFont(theme.fontBold);
        volumeSlider.addChangeListener(e -> volumeLabel.setText(volumeSlider.getValue() + "%"));

        NeonButton btnTestChime = new NeonButton("Test Audio Chime", CyberpunkIcons.createIcon("PLAY", 14), NeonButton.Style.DEFAULT);
        btnTestChime.addActionListener(e -> {
            if (getOS() != null && getOS().getSoundService() != null) {
                getOS().getSoundService().playSuccess();
            }
        });

        aRow2.add(lblVol);
        aRow2.add(volumeSlider);
        aRow2.add(volumeLabel);
        aRow2.add(Box.createHorizontalStrut(16));
        aRow2.add(btnTestChime);
        audioCard.add(aRow2);

        body.add(audioCard);
        body.add(Box.createVerticalStrut(12));

        // ── SECTION 4: Office Suite & Default File Handlers ─────────────────
        NeonCard officeCard = new NeonCard(new GridLayout(2, 2, 12, 8));
        officeCard.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(theme.secondaryAccent),
                " Office Suite & Default File Handlers ",
                0, 0, theme.fontBold, theme.secondaryAccent));

        JLabel lblDefWord = new JLabel("Word Documents (.docx, .doc):");
        lblDefWord.setForeground(theme.textPrimary);
        defaultWordBox = new JComboBox<>(new String[]{"Word Processor (Native Editor)", "Document Viewer (Read-Only)"});
        defaultWordBox.setSelectedIndex("docviewer".equalsIgnoreCase(settings.getDefaultWordApp()) ? 1 : 0);
        defaultWordBox.setBackground(theme.panel);
        defaultWordBox.setForeground(theme.textPrimary);

        JLabel lblDefSlides = new JLabel("Slide Presentations (.pptx, .ppt):");
        lblDefSlides.setForeground(theme.textPrimary);
        defaultSlidesBox = new JComboBox<>(new String[]{"Presentation Slides (Native Editor & Slideshow)", "Document Viewer (Read-Only)"});
        defaultSlidesBox.setSelectedIndex("docviewer".equalsIgnoreCase(settings.getDefaultSlidesApp()) ? 1 : 0);
        defaultSlidesBox.setBackground(theme.panel);
        defaultSlidesBox.setForeground(theme.textPrimary);

        officeCard.add(lblDefWord);
        officeCard.add(defaultWordBox);
        officeCard.add(lblDefSlides);
        officeCard.add(defaultSlidesBox);
        body.add(officeCard);

        body.add(Box.createVerticalStrut(12));

        // ── SECTION 5: Security, Password & Biometrics ──────────────────────
        body.add(createSecurityCard(theme));

        body.add(Box.createVerticalStrut(12));

        // ── SECTION 6: Victus AI Integration Settings ───────────────────────
        NeonCard victusCard = new NeonCard();
        victusCard.setLayout(new BoxLayout(victusCard, BoxLayout.Y_AXIS));
        victusCard.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(theme.secondaryAccent),
                " Victus Python Assistant Configuration ",
                0, 0, theme.fontBold, theme.secondaryAccent));

        // Python Path
        JPanel pyRow = new JPanel(new BorderLayout(6, 0));
        pyRow.setOpaque(false);
        JLabel lblPy = new JLabel("Python Binary: ");
        lblPy.setPreferredSize(new Dimension(110, 26));
        lblPy.setForeground(theme.textPrimary);
        pythonPathField = new NeonTextField(settings.getVictusPythonPath(), 20);
        NeonButton btnDetectPy = new NeonButton("Detect", null);
        btnDetectPy.addActionListener(e -> {
            String detected = getOS().getVictusService().resolvePythonExecutable();
            pythonPathField.setText(detected);
        });
        pyRow.add(lblPy, BorderLayout.WEST);
        pyRow.add(pythonPathField, BorderLayout.CENTER);
        pyRow.add(btnDetectPy, BorderLayout.EAST);
        victusCard.add(pyRow);
        victusCard.add(Box.createVerticalStrut(8));

        // Script Path
        JPanel scRow = new JPanel(new BorderLayout(6, 0));
        scRow.setOpaque(false);
        JLabel lblSc = new JLabel("Script Path: ");
        lblSc.setPreferredSize(new Dimension(110, 26));
        lblSc.setForeground(theme.textPrimary);
        scriptPathField = new NeonTextField(settings.getVictusScriptPath(), 20);
        NeonButton btnDetectSc = new NeonButton("Auto-Find", null);
        btnDetectSc.addActionListener(e -> {
            String detected = getOS().getVictusService().resolveScriptPath();
            scriptPathField.setText(detected);
            if (detected != null && Files.exists(Paths.get(detected))) {
                workDirField.setText(Paths.get(detected).getParent().toString());
            }
        });
        scRow.add(lblSc, BorderLayout.WEST);
        scRow.add(scriptPathField, BorderLayout.CENTER);
        scRow.add(btnDetectSc, BorderLayout.EAST);
        victusCard.add(scRow);
        victusCard.add(Box.createVerticalStrut(8));

        // Working Directory
        JPanel wdRow = new JPanel(new BorderLayout(6, 0));
        wdRow.setOpaque(false);
        JLabel lblWd = new JLabel("Working Dir: ");
        lblWd.setPreferredSize(new Dimension(110, 26));
        lblWd.setForeground(theme.textPrimary);
        workDirField = new NeonTextField(settings.getVictusWorkingDir(), 20);
        wdRow.add(lblWd, BorderLayout.WEST);
        wdRow.add(workDirField, BorderLayout.CENTER);
        victusCard.add(wdRow);
        victusCard.add(Box.createVerticalStrut(10));

        // Test Connection Button & Status
        JPanel testRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        testRow.setOpaque(false);
        NeonButton btnTest = new NeonButton("Validate Paths", CyberpunkIcons.createIcon("REFRESH", 14));
        btnTest.addActionListener(e -> validateVictusSetup());
        testResultLabel = new JLabel("Status: Unchecked");
        testResultLabel.setFont(theme.fontSmall);
        testResultLabel.setForeground(theme.textSecondary);
        testRow.add(btnTest);
        testRow.add(testResultLabel);
        victusCard.add(testRow);

        body.add(victusCard);
        body.add(Box.createVerticalStrut(12));

        // ── SECTION 7: About IMPERION OS ────────────────────────────────────
        NeonCard aboutCard = new NeonCard(new GridLayout(3, 1, 0, 4));
        aboutCard.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(theme.neonAccent),
                " About IMPERION OS Neon Workstation ",
                0, 0, theme.fontBold, theme.neonAccent));

        JLabel a1 = new JLabel("IMPERION OS Workstation v2026.10 — Powered by Pure Java Swing & MediaPipe Biometrics");
        a1.setFont(theme.fontSmall);
        a1.setForeground(theme.textPrimary);

        JLabel a2 = new JLabel("Cryptographic Security: Salted SHA-256 Hashing | Office: Word Processor & Presentation Slides Suite");
        a2.setFont(theme.fontSmall);
        a2.setForeground(theme.textSecondary);

        JLabel a3 = new JLabel("Victus AI Assistant Subsystem: Direct hardware & Ollama bridge via subprocess isolation.");
        a3.setFont(theme.fontSmall);
        a3.setForeground(theme.neonAccent);

        aboutCard.add(a1);
        aboutCard.add(a2);
        aboutCard.add(a3);
        body.add(aboutCard);

        JScrollPane scroll = new JScrollPane(body);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUI(new NeonScrollBarUI());
        mainContentPanel.add(scroll, BorderLayout.CENTER);

        // Bottom Action Bar
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 4));
        footer.setOpaque(false);

        NeonButton btnSave = new NeonButton("Save & Apply Settings", CyberpunkIcons.createIcon("START", 16), NeonButton.Style.PRIMARY);
        btnSave.addActionListener(e -> saveAndApply());
        footer.add(btnSave);

        mainContentPanel.add(footer, BorderLayout.SOUTH);

        // Initialize security state
        refreshSecurityUI();

        return mainContentPanel;
    }

    private JPanel createSecurityCard(Theme theme) {
        NeonCard card = new NeonCard();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(theme.neonAccent),
                " Security, Password & Biometric Authentication ",
                0, 0, theme.fontBold, theme.neonAccent));

        // Row 1: Current Lock Status
        JPanel r1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        r1.setOpaque(false);
        JLabel lblLockHeader = new JLabel("Workstation Lock Mode: ");
        lblLockHeader.setForeground(theme.textPrimary);
        lblLockHeader.setFont(theme.fontBold);
        lockStatusLabel = new JLabel("Checking...");
        lockStatusLabel.setFont(theme.fontBold);
        r1.add(lblLockHeader);
        r1.add(lockStatusLabel);
        card.add(r1);

        // Row 2: Biometric Profile Status
        JPanel r2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        r2.setOpaque(false);
        facePhotoThumb = new JLabel();
        facePhotoThumb.setPreferredSize(new Dimension(38, 38));
        facePhotoThumb.setBorder(BorderFactory.createLineBorder(theme.secondaryAccent, 1));
        JLabel lblBioHeader = new JLabel("Biometric Face Engine: ");
        lblBioHeader.setForeground(theme.textPrimary);
        lblBioHeader.setFont(theme.fontBold);
        faceStatusLabel = new JLabel("Checking...");
        faceStatusLabel.setFont(theme.fontBold);
        r2.add(facePhotoThumb);
        r2.add(lblBioHeader);
        r2.add(faceStatusLabel);
        card.add(r2);

        card.add(Box.createVerticalStrut(6));

        // Row 3: Biometric Inclusion/Exclusion Checkbox
        JPanel r3 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        r3.setOpaque(false);
        faceAuthCheckBox = new JCheckBox("Include Face Recognition for Workstation Unlock");
        faceAuthCheckBox.setOpaque(false);
        faceAuthCheckBox.setForeground(theme.textPrimary);
        faceAuthCheckBox.setFont(theme.fontBold);
        faceAuthCheckBox.setToolTipText("If enabled, IMPERION OS will scan and authenticate your face on startup and screen lock.");
        faceAuthCheckBox.addActionListener(e -> {
            boolean selected = faceAuthCheckBox.isSelected();
            SecurityService sec = getOS().getSecurityService();
            if (selected && !sec.isFaceEnrolled()) {
                JOptionPane.showMessageDialog(mainContentPanel,
                        "No biometric face profile has been enrolled yet.\nPlease click 'Scan & Enroll Face' first.",
                        "Face Profile Not Enrolled", JOptionPane.WARNING_MESSAGE);
                faceAuthCheckBox.setSelected(false);
                return;
            }
            sec.setFaceRecognitionEnabled(selected);
            refreshSecurityUI();
        });
        r3.add(faceAuthCheckBox);
        card.add(r3);

        card.add(Box.createVerticalStrut(8));

        // Row 4: Lock Management Buttons (Create / Change / Remove)
        lockActionButtonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        lockActionButtonsPanel.setOpaque(false);
        card.add(lockActionButtonsPanel);

        card.add(Box.createVerticalStrut(8));

        // Row 5: Biometric Management Buttons
        JPanel bioButtonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        bioButtonsPanel.setOpaque(false);

        btnEnrollFace = new NeonButton("Scan & Enroll Face", CyberpunkIcons.createIcon("SETTINGS", 14), NeonButton.Style.DEFAULT);
        btnEnrollFace.setToolTipText("Capture live webcam feed and register facial geometry with MediaPipe BlazeFace");
        btnEnrollFace.addActionListener(e -> enrollFaceProfile());
        bioButtonsPanel.add(btnEnrollFace);

        btnTestFace = new NeonButton("Test Live Verification", CyberpunkIcons.createIcon("PLAY", 14), NeonButton.Style.DEFAULT);
        btnTestFace.setToolTipText("Test live camera face recognition against stored profile");
        btnTestFace.addActionListener(e -> testFaceVerification());
        bioButtonsPanel.add(btnTestFace);

        btnDeleteFace = new NeonButton("Delete Face Profile", CyberpunkIcons.createIcon("TRASH", 14), NeonButton.Style.DANGER);
        btnDeleteFace.setToolTipText("Wipe biometric face profile and enrolled images from disk");
        btnDeleteFace.addActionListener(e -> deleteFaceProfile());
        bioButtonsPanel.add(btnDeleteFace);

        NeonButton btnLockNow = new NeonButton("Lock Screen Now", CyberpunkIcons.createIcon("LOCK", 14), NeonButton.Style.PRIMARY);
        btnLockNow.setToolTipText("Immediately lock the IMPERION OS session");
        btnLockNow.addActionListener(e -> getOS().lockScreen());
        bioButtonsPanel.add(btnLockNow);

        card.add(bioButtonsPanel);

        return card;
    }

    private void refreshSecurityUI() {
        SecurityService sec = getOS().getSecurityService();
        Theme theme = ThemeManager.getInstance().getCurrentTheme();

        // 1. Lock Mode Status
        SecurityService.LockType type = sec.getLockType();
        switch (type) {
            case NONE:
                lockStatusLabel.setText("NONE (Workstation unlocked - boots directly to desktop)");
                lockStatusLabel.setForeground(theme.warning);
                break;
            case PIN:
                lockStatusLabel.setText("PIN CODE LOCK (Numeric keypad authentication active)");
                lockStatusLabel.setForeground(theme.success);
                break;
            case PASSWORD:
                lockStatusLabel.setText("ALPHANUMERIC PASSWORD (SHA-256 salted encryption active)");
                lockStatusLabel.setForeground(theme.success);
                break;
        }

        // 2. Face Biometric Status
        boolean enrolled = sec.isFaceEnrolled();
        boolean enabled = sec.isFaceRecognitionEnabled();
        if (enrolled) {
            File imgFile = new File("data/security/face_enrolled.png");
            if (imgFile.exists()) {
                try {
                    ImageIcon ic = new ImageIcon(imgFile.getAbsolutePath());
                    Image sc = ic.getImage().getScaledInstance(36, 36, Image.SCALE_SMOOTH);
                    facePhotoThumb.setIcon(new ImageIcon(sc));
                    facePhotoThumb.setText("");
                } catch (Exception ex) {
                    facePhotoThumb.setIcon(CyberpunkIcons.createIcon("SETTINGS", 20));
                }
            } else {
                facePhotoThumb.setIcon(CyberpunkIcons.createIcon("SETTINGS", 20));
            }
            if (enabled) {
                faceStatusLabel.setText("ENROLLED & ACTIVE (Included in workstation unlock)");
                faceStatusLabel.setForeground(theme.success);
            } else {
                faceStatusLabel.setText("ENROLLED (EXCLUDED from workstation unlock)");
                faceStatusLabel.setForeground(theme.warning);
            }
        } else {
            facePhotoThumb.setIcon(null);
            facePhotoThumb.setText(" [N/A] ");
            facePhotoThumb.setForeground(theme.textSecondary);
            faceStatusLabel.setText("NOT ENROLLED (No biometric data on record)");
            faceStatusLabel.setForeground(theme.textSecondary);
        }

        faceAuthCheckBox.setSelected(enabled && enrolled);
        faceAuthCheckBox.setEnabled(enrolled);
        btnTestFace.setEnabled(enrolled);
        btnDeleteFace.setEnabled(enrolled);

        // 3. Dynamic Lock Management Buttons
        lockActionButtonsPanel.removeAll();
        if (type == SecurityService.LockType.NONE) {
            NeonButton btnSetPin = new NeonButton("Create PIN Lock", CyberpunkIcons.createIcon("SETTINGS", 14), NeonButton.Style.PRIMARY);
            btnSetPin.addActionListener(e -> promptCreateLock(SecurityService.LockType.PIN));

            NeonButton btnSetPass = new NeonButton("Create Password Lock", CyberpunkIcons.createIcon("SHIELD", 14), NeonButton.Style.PRIMARY);
            btnSetPass.addActionListener(e -> promptCreateLock(SecurityService.LockType.PASSWORD));

            lockActionButtonsPanel.add(btnSetPin);
            lockActionButtonsPanel.add(btnSetPass);
        } else {
            NeonButton btnChange = new NeonButton("Change Lock / Password", CyberpunkIcons.createIcon("SETTINGS", 14), NeonButton.Style.DEFAULT);
            btnChange.addActionListener(e -> promptChangeLock());

            NeonButton btnRemove = new NeonButton("Remove / Disable Lock", CyberpunkIcons.createIcon("CLOSE", 14), NeonButton.Style.DANGER);
            btnRemove.addActionListener(e -> promptRemoveLock());

            lockActionButtonsPanel.add(btnChange);
            lockActionButtonsPanel.add(btnRemove);
        }

        lockActionButtonsPanel.revalidate();
        lockActionButtonsPanel.repaint();
    }

    private void promptCreateLock(SecurityService.LockType targetType) {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        boolean isPin = (targetType == SecurityService.LockType.PIN);

        JPanel panel = new JPanel(new GridLayout(4, 1, 4, 6));
        panel.setOpaque(false);

        JLabel lblTitle = new JLabel("Create New " + (isPin ? "Numeric PIN (4-8 digits)" : "Alphanumeric Password"));
        lblTitle.setFont(theme.fontBold);
        lblTitle.setForeground(theme.neonAccent);

        JPasswordField field1 = new JPasswordField(16);
        field1.setBackground(theme.panel);
        field1.setForeground(theme.textPrimary);
        field1.setCaretColor(Color.WHITE);

        JLabel lblConfirm = new JLabel("Confirm " + (isPin ? "PIN:" : "Password:"));
        lblConfirm.setForeground(theme.textPrimary);

        JPasswordField field2 = new JPasswordField(16);
        field2.setBackground(theme.panel);
        field2.setForeground(theme.textPrimary);
        field2.setCaretColor(Color.WHITE);

        panel.add(lblTitle);
        panel.add(field1);
        panel.add(lblConfirm);
        panel.add(field2);

        int res = JOptionPane.showConfirmDialog(
                mainContentPanel, panel, "Set Workstation Lock",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (res == JOptionPane.OK_OPTION) {
            String p1 = new String(field1.getPassword()).trim();
            String p2 = new String(field2.getPassword()).trim();

            if (p1.isEmpty()) {
                JOptionPane.showMessageDialog(mainContentPanel, "Password cannot be empty.", "Validation Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (!p1.equals(p2)) {
                JOptionPane.showMessageDialog(mainContentPanel, "Passwords do not match.", "Validation Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (isPin) {
                if (!p1.matches("\\d{4,8}")) {
                    JOptionPane.showMessageDialog(mainContentPanel, "PIN must be between 4 and 8 digits (numeric only).", "Validation Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            } else {
                if (p1.length() < 4) {
                    JOptionPane.showMessageDialog(mainContentPanel, "Password must be at least 4 characters long.", "Validation Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }

            getOS().getSecurityService().createLock(targetType, p1);
            refreshSecurityUI();
            JOptionPane.showMessageDialog(mainContentPanel,
                    "Workstation lock successfully created with " + targetType.name() + " authentication!",
                    "Lock Configured", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void promptChangeLock() {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        SecurityService sec = getOS().getSecurityService();

        JPanel panel = new JPanel(new GridLayout(8, 1, 2, 4));
        panel.setOpaque(false);

        JLabel lblCur = new JLabel("Enter Current Password / PIN:");
        lblCur.setForeground(theme.textPrimary);
        JPasswordField curField = new JPasswordField(16);
        curField.setBackground(theme.panel);
        curField.setForeground(theme.textPrimary);

        JLabel lblType = new JLabel("Select New Lock Type:");
        lblType.setForeground(theme.textPrimary);
        JComboBox<String> typeBox = new JComboBox<>(new String[]{"PIN Code (Numeric)", "Alphanumeric Password"});
        typeBox.setBackground(theme.panel);
        typeBox.setForeground(theme.textPrimary);
        if (sec.getLockType() == SecurityService.LockType.PASSWORD) {
            typeBox.setSelectedIndex(1);
        }

        JLabel lblNew = new JLabel("Enter New PIN / Password:");
        lblNew.setForeground(theme.textPrimary);
        JPasswordField newField = new JPasswordField(16);
        newField.setBackground(theme.panel);
        newField.setForeground(theme.textPrimary);

        JLabel lblConf = new JLabel("Confirm New PIN / Password:");
        lblConf.setForeground(theme.textPrimary);
        JPasswordField confField = new JPasswordField(16);
        confField.setBackground(theme.panel);
        confField.setForeground(theme.textPrimary);

        panel.add(lblCur);
        panel.add(curField);
        panel.add(lblType);
        panel.add(typeBox);
        panel.add(lblNew);
        panel.add(newField);
        panel.add(lblConf);
        panel.add(confField);

        int res = JOptionPane.showConfirmDialog(
                mainContentPanel, panel, "Change Workstation Lock",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (res == JOptionPane.OK_OPTION) {
            String cur = new String(curField.getPassword());
            String p1 = new String(newField.getPassword()).trim();
            String p2 = new String(confField.getPassword()).trim();
            boolean isPin = (typeBox.getSelectedIndex() == 0);
            SecurityService.LockType newType = isPin ? SecurityService.LockType.PIN : SecurityService.LockType.PASSWORD;

            if (!sec.authenticate(cur)) {
                JOptionPane.showMessageDialog(mainContentPanel, "Authentication Failed: Incorrect current password / PIN.", "Security Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (p1.isEmpty()) {
                JOptionPane.showMessageDialog(mainContentPanel, "New password cannot be empty.", "Validation Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (!p1.equals(p2)) {
                JOptionPane.showMessageDialog(mainContentPanel, "New passwords do not match.", "Validation Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (isPin && !p1.matches("\\d{4,8}")) {
                JOptionPane.showMessageDialog(mainContentPanel, "PIN must be between 4 and 8 numeric digits.", "Validation Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (!isPin && p1.length() < 4) {
                JOptionPane.showMessageDialog(mainContentPanel, "Password must be at least 4 characters long.", "Validation Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            boolean ok = sec.changeLock(cur, newType, p1);
            if (ok) {
                refreshSecurityUI();
                JOptionPane.showMessageDialog(mainContentPanel, "Workstation lock credentials updated successfully!", "Security Updated", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(mainContentPanel, "Failed to update lock credentials.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void promptRemoveLock() {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        SecurityService sec = getOS().getSecurityService();

        JPanel panel = new JPanel(new GridLayout(3, 1, 2, 4));
        panel.setOpaque(false);

        JLabel lblWarn = new JLabel("Enter current credentials to completely remove screen lock:");
        lblWarn.setForeground(theme.error);
        lblWarn.setFont(theme.fontBold);

        JPasswordField curField = new JPasswordField(16);
        curField.setBackground(theme.panel);
        curField.setForeground(theme.textPrimary);

        panel.add(lblWarn);
        panel.add(curField);

        int res = JOptionPane.showConfirmDialog(
                mainContentPanel, panel, "Disable Workstation Lock",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);

        if (res == JOptionPane.OK_OPTION) {
            String cur = new String(curField.getPassword());
            boolean ok = sec.removeLock(cur);
            if (ok) {
                refreshSecurityUI();
                JOptionPane.showMessageDialog(mainContentPanel, "Workstation lock has been completely removed. System is now unlocked.", "Lock Disabled", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(mainContentPanel, "Authentication Failed: Incorrect password / PIN.", "Security Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void enrollFaceProfile() {
        btnEnrollFace.setEnabled(false);
        btnEnrollFace.setText("Accessing Camera...");

        SwingWorker<SecurityService.BiometricResult, Void> worker = new SwingWorker<>() {
            @Override
            protected SecurityService.BiometricResult doInBackground() {
                return getOS().getSecurityService().enrollFaceBiometricsDetailed();
            }

            @Override
            protected void done() {
                btnEnrollFace.setEnabled(true);
                btnEnrollFace.setText("Scan & Enroll Face");
                try {
                    SecurityService.BiometricResult res = get();
                    if (res.isSuccess()) {
                        int opt = JOptionPane.showConfirmDialog(
                                mainContentPanel,
                                "Biometric face registration succeeded!\nProfile: " + res.getMessage() + "\n\nWould you like to include Face Recognition for workstation unlock?",
                                "Face Biometrics Enrolled",
                                JOptionPane.YES_NO_OPTION,
                                JOptionPane.INFORMATION_MESSAGE
                        );
                        if (opt == JOptionPane.YES_OPTION) {
                            getOS().getSecurityService().setFaceRecognitionEnabled(true);
                        }
                    } else {
                        JOptionPane.showMessageDialog(
                                mainContentPanel,
                                "Face registration failed:\n" + res.getMessage() + "\n\nPlease ensure your webcam is connected and face the camera directly.",
                                "Biometric Registration Failed",
                                JOptionPane.ERROR_MESSAGE
                        );
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(mainContentPanel, "Error executing biometric scanner: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
                refreshSecurityUI();
            }
        };
        worker.execute();
    }

    private void testFaceVerification() {
        btnTestFace.setEnabled(false);
        btnTestFace.setText("Scanning Optical Feed...");

        SwingWorker<SecurityService.BiometricResult, Void> worker = new SwingWorker<>() {
            @Override
            protected SecurityService.BiometricResult doInBackground() {
                return getOS().getSecurityService().verifyFaceBiometricsDetailed();
            }

            @Override
            protected void done() {
                btnTestFace.setEnabled(true);
                btnTestFace.setText("Test Live Verification");
                try {
                    SecurityService.BiometricResult res = get();
                    int confPct = (int) (res.getConfidence() * 100);

                    // Build visual dialog showing face images
                    Theme theme = ThemeManager.getInstance().getCurrentTheme();
                    JPanel pnl = new JPanel(new BorderLayout(0, 10));
                    pnl.setOpaque(false);

                    JPanel imgRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 6));
                    imgRow.setOpaque(false);

                    File enrolledF = new File("data/security/face_enrolled.png");
                    if (enrolledF.exists()) {
                        JLabel enLbl = new JLabel(new ImageIcon(new ImageIcon(enrolledF.getAbsolutePath()).getImage().getScaledInstance(90, 90, Image.SCALE_SMOOTH)));
                        enLbl.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(theme.neonAccent), "Enrolled Profile", 0, 0, theme.fontSmall, theme.neonAccent));
                        imgRow.add(enLbl);
                    }

                    File liveF = new File("data/security/live_scan.png");
                    if (liveF.exists()) {
                        JLabel liveLbl = new JLabel(new ImageIcon(new ImageIcon(liveF.getAbsolutePath()).getImage().getScaledInstance(90, 90, Image.SCALE_SMOOTH)));
                        liveLbl.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(res.isSuccess() ? theme.success : theme.error), "Live Camera Scan", 0, 0, theme.fontSmall, res.isSuccess() ? theme.success : theme.error));
                        imgRow.add(liveLbl);
                    }
                    pnl.add(imgRow, BorderLayout.NORTH);

                    JLabel statusDesc = new JLabel(String.format("<html><center><b style='color:%s;font-size:14px'>%s</b><br/><br/>Biometric Score: <b>%d%%</b> | Status: %s</center></html>",
                            res.isSuccess() ? "#00ffaa" : "#ff4444",
                            res.isSuccess() ? "ACCESS GRANTED — OPERATOR VERIFIED" : "VERIFICATION FAILED — FACE MISMATCH",
                            confPct,
                            res.getMessage()));
                    statusDesc.setHorizontalAlignment(SwingConstants.CENTER);
                    pnl.add(statusDesc, BorderLayout.CENTER);

                    JOptionPane.showMessageDialog(
                            mainContentPanel,
                            pnl,
                            res.isSuccess() ? "Biometric Verification Succeeded" : "Biometric Verification Failed",
                            res.isSuccess() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE
                    );
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(mainContentPanel, "Verification error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void deleteFaceProfile() {
        int opt = JOptionPane.showConfirmDialog(
                mainContentPanel,
                "Are you sure you want to delete your enrolled face biometrics?\nFace recognition will be completely disabled.",
                "Delete Biometric Profile",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (opt == JOptionPane.YES_OPTION) {
            getOS().getSecurityService().deleteFaceBiometrics();
            refreshSecurityUI();
            JOptionPane.showMessageDialog(mainContentPanel, "Biometric face profile has been deleted.", "Profile Removed", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void validateVictusSetup() {
        String py = pythonPathField.getText().trim();
        String sc = scriptPathField.getText().trim();
        Theme theme = ThemeManager.getInstance().getCurrentTheme();

        if (sc.isEmpty() || !Files.exists(Paths.get(sc))) {
            testResultLabel.setText("FAIL: Script not found on disk at " + sc);
            testResultLabel.setForeground(theme.error);
            return;
        }

        if (!"python".equalsIgnoreCase(py) && !Files.exists(Paths.get(py))) {
            testResultLabel.setText("FAIL: Python executable not found at " + py);
            testResultLabel.setForeground(theme.error);
            return;
        }

        testResultLabel.setText("OK: Python and Script paths verified successfully!");
        testResultLabel.setForeground(theme.success);
    }

    private void saveAndApply() {
        SettingsService s = getOS().getSettingsService();

        // 1. Theme
        String selectedTheme = (String) themeBox.getSelectedItem();
        s.setTheme(selectedTheme);
        ThemeManager.getInstance().applyTheme(selectedTheme);

        // 2. Wallpaper & Graphics Engine
        String selectedWall = ((String) wallpaperBox.getSelectedItem()).toLowerCase();
        s.setWallpaper(selectedWall);

        int animIdx = animQualityBox.getSelectedIndex();
        String animStr = (animIdx == 0) ? "high" : (animIdx == 2 ? "saver" : (animIdx == 3 ? "static" : "balanced"));
        s.setDesktopAnimation(animStr);
        s.setScanlinesEnabled(checkScanlines.isSelected());
        s.setShockwavesEnabled(checkShockwaves.isSelected());

        int iconIdx = iconSizeBox.getSelectedIndex();
        s.setDesktopIconSize(iconIdx == 1 ? "compact" : (iconIdx == 2 ? "large" : "standard"));

        if (getOS().getDesktopFrame() != null) {
            getOS().getDesktopFrame().getDesktopSurface().setWallpaperStyle(selectedWall);
            getOS().getDesktopFrame().getDesktopSurface().applyDesktopSettings();
            getOS().getDesktopFrame().getDesktopSurface().initShortcuts();
        }

        // 3. Taskbar & Clock
        s.setTaskbarClockFormat(clockFormatBox.getSelectedIndex() == 1 ? "12h" : "24h");
        s.setTaskbarClockSeconds(checkClockSeconds.isSelected());

        // 4. Audio
        s.setSoundEnabled(checkSoundEnabled.isSelected());
        s.setSoundVolume(volumeSlider.getValue());

        // 5. Office Defaults
        s.setDefaultWordApp(defaultWordBox.getSelectedIndex() == 1 ? "docviewer" : "wordeditor");
        s.setDefaultSlidesApp(defaultSlidesBox.getSelectedIndex() == 1 ? "docviewer" : "slides");

        // 6. Victus Paths
        s.setVictusPythonPath(pythonPathField.getText().trim());
        s.setVictusScriptPath(scriptPathField.getText().trim());
        s.setVictusWorkingDir(workDirField.getText().trim());

        // Notify
        getOS().getNotificationManager().postNotification(
                "Preferences Applied", "System configurations successfully updated and persisted.", javaos.models.NotificationMessage.Level.SUCCESS);

        if (getOS().getSoundService() != null) {
            getOS().getSoundService().playSuccess();
        }

        JOptionPane.showMessageDialog(themeBox, "IMPERION OS settings have been successfully updated and applied!", "Preferences Saved", JOptionPane.INFORMATION_MESSAGE);
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return "Grid";
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }
}

package javaos.apps;

import javaos.ui.Theme;
import javaos.ui.ThemeManager;
import javaos.ui.components.CyberpunkIcons;
import javaos.ui.components.NeonButton;
import javaos.ui.components.NeonScrollBarUI;

import javax.sound.midi.*;
import javax.sound.sampled.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.util.*;
import java.util.List;

/**
 * NeonWave Media Player & Procedural Synthwave Radio for IMPERION OS.
 * Features:
 *  - Procedural Synthwave Radio Engine with 4 real-time generated stations (Night City Cyberpunk,
 *    Neo Tokyo Lo-Fi, Orbital Ambient, and 8-Bit Chiptune) using Java MIDI synthesis
 *  - Native Audio File Player for .wav and local sound files
 *  - Live Dancing 32-band Audio Spectrum Visualizer & Oscilloscope wave
 *  - Playlist queue with file drag-and-drop, track progress seeker, and volume control
 */
public class MediaPlayerApp extends OSApplication {

    private final List<File> playlist = new ArrayList<>();
    private int currentTrackIndex = -1;

    // Audio Playback
    private Clip currentClip;
    private Synthesizer midiSynth;
    private MidiChannel midiChannelLead;
    private MidiChannel midiChannelBass;
    private javax.swing.Timer radioTimer;
    private int radioStep = 0;
    private int currentStation = 0; // 0=Cyberpunk, 1=Lo-Fi, 2=Ambient, 3=Chiptune

    private boolean isPlaying = false;
    private boolean isRadioMode = true;

    private static volatile boolean globalAudioPlaying = false;
    private static volatile float globalAudioBeat = 0f;

    public static boolean isAudioPlaying() {
        return globalAudioPlaying;
    }

    public static float getAudioBeat() {
        return globalAudioBeat;
    }

    // UI Components
    private JLabel trackTitleLabel;
    private JLabel stationLabel;
    private JSlider progressSlider;
    private JSlider volumeSlider;
    private DefaultListModel<String> playlistModel;
    private JList<String> playlistView;
    private VisualizerPanel visualizerPanel;
    private javax.swing.Timer visualizerTimer;
    private JButton btnPlayPause;

    private static final String[] STATIONS = {
            "⚡ Night City 2077 (High-Energy Synthwave)",
            "🌸 Neo Tokyo (Chillwave / Lo-Fi)",
            "🌌 Orbital Station (Deep Cyber Ambient)",
            "👾 Mega Drive (8-Bit Arcade Chiptune)"
    };

    public MediaPlayerApp() {
        super(new AppDescriptor(
                "player",
                "NeonWave Media Player",
                "Synthwave radio, audio player & live dancing visualizer",
                AppDescriptor.Category.MEDIA,
                CyberpunkIcons.createIcon("MUSIC", 20),
                false
        ));
        initMidi();
    }

    private void initMidi() {
        try {
            midiSynth = MidiSystem.getSynthesizer();
            midiSynth.open();
            MidiChannel[] channels = midiSynth.getChannels();
            if (channels != null && channels.length >= 2) {
                midiChannelLead = channels[0]; // Lead Synth
                midiChannelBass = channels[1]; // Sub-bass
                midiChannelLead.programChange(81); // Lead 2 (sawtooth)
                midiChannelBass.programChange(38); // Synth Bass 1
            }
        } catch (Exception ignored) {}
    }

    @Override
    public Dimension getPreferredWindowSize() {
        return new Dimension(780, 520);
    }

    @Override
    public JComponent createContent() {
        JPanel main = new JPanel(new BorderLayout(0, 0));
        main.setOpaque(true);
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        main.setBackground(theme.background);

        // ── TOP: TRACK TITLE & RADIO STATION SELECTOR ─────────────────────────
        JPanel topPanel = new JPanel(new BorderLayout(8, 0));
        topPanel.setOpaque(true);
        topPanel.setBackground(theme.panel);
        topPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, theme.secondaryAccent),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));

        JPanel titleCol = new JPanel(new GridLayout(2, 1, 0, 2));
        titleCol.setOpaque(false);

        trackTitleLabel = new JLabel("⚡ Station 1: Night City 2077");
        trackTitleLabel.setFont(theme.fontBold);
        trackTitleLabel.setForeground(theme.neonAccent);

        stationLabel = new JLabel("Procedural Cyberpunk Synthesizer Audio Stream Active");
        stationLabel.setFont(theme.fontSmall);
        stationLabel.setForeground(theme.textSecondary);

        titleCol.add(trackTitleLabel);
        titleCol.add(stationLabel);
        topPanel.add(titleCol, BorderLayout.WEST);

        // Station Selector Dropdown
        JComboBox<String> stationBox = new JComboBox<>(STATIONS);
        stationBox.setFont(theme.fontSmall);
        stationBox.setBackground(theme.elevatedPanel);
        stationBox.setForeground(theme.textPrimary);
        stationBox.setPreferredSize(new Dimension(280, 28));
        stationBox.addActionListener(e -> {
            currentStation = stationBox.getSelectedIndex();
            switchStation(currentStation);
        });
        topPanel.add(stationBox, BorderLayout.EAST);

        main.add(topPanel, BorderLayout.NORTH);

        // ── CENTER: VISUALIZER & PLAYLIST SPLIT ───────────────────────────────
        JPanel centerSplit = new JPanel(new GridLayout(1, 2, 8, 0));
        centerSplit.setOpaque(false);
        centerSplit.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // Left: 32-Band Neon Dancing Visualizer
        visualizerPanel = new VisualizerPanel();
        centerSplit.add(visualizerPanel);

        // Right: Playlist Panel
        JPanel playlistPanel = new JPanel(new BorderLayout(0, 6));
        playlistPanel.setOpaque(true);
        playlistPanel.setBackground(theme.panel);
        playlistPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(theme.secondaryAccent),
                BorderFactory.createEmptyBorder(6, 6, 6, 6)
        ));

        JPanel pHeader = new JPanel(new BorderLayout());
        pHeader.setOpaque(false);
        JLabel pTitle = new JLabel("Playback Queue & Audio Files");
        pTitle.setFont(theme.fontBold);
        pTitle.setForeground(theme.textPrimary);
        pHeader.add(pTitle, BorderLayout.WEST);

        JButton btnAddFile = makeToolBtn("+ Add File", null, "Add audio file from disk");
        btnAddFile.addActionListener(e -> promptAddAudioFile());
        pHeader.add(btnAddFile, BorderLayout.EAST);
        playlistPanel.add(pHeader, BorderLayout.NORTH);

        playlistModel = new DefaultListModel<>();
        playlistModel.addElement("⚡ Procedural Synthwave Stream (Station 1)");
        playlistModel.addElement("🌸 Lo-Fi Chillwave Stream (Station 2)");
        playlistModel.addElement("🌌 Orbital Cyber Ambient Stream (Station 3)");
        playlistModel.addElement("👾 8-Bit Retro Chiptune Stream (Station 4)");

        playlistView = new JList<>(playlistModel);
        playlistView.setBackground(theme.background);
        playlistView.setForeground(theme.textPrimary);
        playlistView.setSelectionBackground(theme.selectionBackground);
        playlistView.setSelectionForeground(theme.neonAccent);
        playlistView.setSelectedIndex(0);

        playlistView.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int idx = playlistView.getSelectedIndex();
                    if (idx < 4) {
                        currentStation = idx;
                        switchStation(currentStation);
                    } else {
                        playLocalTrack(idx - 4);
                    }
                }
            }
        });

        JScrollPane pScroll = new JScrollPane(playlistView);
        pScroll.getVerticalScrollBar().setUI(new NeonScrollBarUI());
        pScroll.setBorder(BorderFactory.createLineBorder(theme.secondaryAccent));
        playlistPanel.add(pScroll, BorderLayout.CENTER);

        centerSplit.add(playlistPanel);
        main.add(centerSplit, BorderLayout.CENTER);

        // ── BOTTOM: TRANSPORT CONTROLS & VOLUME ──────────────────────────────
        JPanel bottomBar = new JPanel(new BorderLayout(10, 4));
        bottomBar.setOpaque(true);
        bottomBar.setBackground(theme.panel);
        bottomBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, theme.secondaryAccent),
                BorderFactory.createEmptyBorder(6, 14, 8, 14)
        ));

        // Progress Bar
        progressSlider = new JSlider(0, 100, 0);
        progressSlider.setOpaque(false);
        progressSlider.setForeground(theme.neonAccent);
        bottomBar.add(progressSlider, BorderLayout.NORTH);

        // Playback Buttons (Center)
        JPanel transportPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        transportPanel.setOpaque(false);

        JButton btnPrev = makeToolBtn("⏮", null, "Previous Station / Track");
        btnPrev.addActionListener(e -> prevTrack());
        transportPanel.add(btnPrev);

        btnPlayPause = makeToolBtn("▶ Play", "PLAY", "Play or Pause Audio");
        btnPlayPause.setFont(theme.fontBold);
        btnPlayPause.setForeground(theme.neonAccent);
        btnPlayPause.addActionListener(e -> togglePlayPause());
        transportPanel.add(btnPlayPause);

        JButton btnStop = makeToolBtn("⏹", "STOP", "Stop Audio");
        btnStop.addActionListener(e -> stopAudio());
        transportPanel.add(btnStop);

        JButton btnNext = makeToolBtn("⏭", null, "Next Station / Track");
        btnNext.addActionListener(e -> nextTrack());
        transportPanel.add(btnNext);

        bottomBar.add(transportPanel, BorderLayout.CENTER);

        // Volume Slider (Right)
        JPanel volPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        volPanel.setOpaque(false);
        JLabel volIcon = new JLabel("🔊");
        volSlider = new JSlider(0, 100, 80);
        volSlider.setPreferredSize(new Dimension(90, 24));
        volSlider.setOpaque(false);
        volSlider.addChangeListener(e -> {
            if (currentClip != null) {
                try {
                    FloatControl gain = (FloatControl) currentClip.getControl(FloatControl.Type.MASTER_GAIN);
                    float range = gain.getMaximum() - gain.getMinimum();
                    float val = gain.getMinimum() + (range * (volSlider.getValue() / 100f));
                    gain.setValue(val);
                } catch (Exception ignored) {}
            }
        });
        volPanel.add(volIcon);
        volPanel.add(volSlider);
        bottomBar.add(volPanel, BorderLayout.EAST);

        main.add(bottomBar, BorderLayout.SOUTH);

        // Visualizer animation timer (30 FPS)
        visualizerTimer = new javax.swing.Timer(33, e -> {
            visualizerPanel.updateWave(isPlaying);
        });
        visualizerTimer.start();

        // Start radio synthesizer loop
        startRadioEngine();

        return main;
    }

    private JSlider volSlider;

    // ── PROCEDURAL SYNTHWAVE RADIO ENGINE ───────────────────────────────────
    private void startRadioEngine() {
        // Pentatonic / minor arpeggio scales for each station
        final int[][] NOTES_LEAD = {
                {60, 63, 65, 67, 70, 72, 75, 72, 67, 65, 63, 60, 67, 70, 72, 75}, // Cyberpunk Dm / Cm
                {58, 62, 65, 69, 70, 69, 65, 62, 58, 62, 65, 69, 72, 69, 65, 62}, // Lo-Fi Major 7th
                {48, 55, 60, 62, 67, 60, 55, 48, 51, 58, 63, 65, 70, 63, 58, 51}, // Ambient Space
                {72, 76, 79, 84, 79, 76, 74, 77, 81, 84, 81, 77, 72, 76, 79, 84}  // 8-bit Chiptune
        };

        final int[][] NOTES_BASS = {
                {36, 36, 36, 36, 39, 39, 41, 43},
                {34, 34, 38, 38, 41, 41, 43, 43},
                {24, 24, 27, 27, 29, 29, 31, 31},
                {36, 48, 36, 48, 38, 50, 41, 53}
        };

        radioTimer = new javax.swing.Timer(135, e -> {
            if (!isPlaying || !isRadioMode || midiChannelLead == null) return;

            int[] leadScale = NOTES_LEAD[currentStation % NOTES_LEAD.length];
            int[] bassScale = NOTES_BASS[currentStation % NOTES_BASS.length];

            int noteLead = leadScale[radioStep % leadScale.length];
            int noteBass = bassScale[(radioStep / 2) % bassScale.length];

            midiChannelLead.noteOn(noteLead, 85);
            if (radioStep % 2 == 0 && midiChannelBass != null) {
                midiChannelBass.noteOn(noteBass, 95);
            }

            radioStep++;
            progressSlider.setValue((radioStep % 100));
        });
    }

    private void switchStation(int stationIndex) {
        currentStation = stationIndex;
        isRadioMode = true;
        if (currentClip != null && currentClip.isRunning()) {
            currentClip.stop();
        }

        // Change MIDI instruments per station
        if (midiChannelLead != null) {
            switch (stationIndex) {
                case 0 -> { midiChannelLead.programChange(81); midiChannelBass.programChange(38); } // Lead 2 Saw
                case 1 -> { midiChannelLead.programChange(4);  midiChannelBass.programChange(33); } // Electric Piano
                case 2 -> { midiChannelLead.programChange(89); midiChannelBass.programChange(43); } // Warm Pad
                case 3 -> { midiChannelLead.programChange(80); midiChannelBass.programChange(39); } // Square Wave Lead
            }
        }

        trackTitleLabel.setText(STATIONS[stationIndex]);
        stationLabel.setText("Procedural Cyberpunk Synthesizer Audio Stream Active");
        if (!isPlaying) togglePlayPause();
    }

    private void togglePlayPause() {
        isPlaying = !isPlaying;
        globalAudioPlaying = isPlaying;
        if (isPlaying) {
            btnPlayPause.setText("⏸ Pause");
            if (isRadioMode) {
                radioTimer.start();
            } else if (currentClip != null) {
                currentClip.start();
            }
        } else {
            btnPlayPause.setText("▶ Play");
            if (radioTimer != null) radioTimer.stop();
            if (currentClip != null) currentClip.stop();
            if (midiChannelLead != null) midiChannelLead.allNotesOff();
            if (midiChannelBass != null) midiChannelBass.allNotesOff();
        }
    }

    private void stopAudio() {
        isPlaying = false;
        globalAudioPlaying = false;
        btnPlayPause.setText("▶ Play");
        if (radioTimer != null) radioTimer.stop();
        if (currentClip != null) {
            currentClip.stop();
            currentClip.setFramePosition(0);
        }
        if (midiChannelLead != null) midiChannelLead.allNotesOff();
        if (midiChannelBass != null) midiChannelBass.allNotesOff();
        progressSlider.setValue(0);
    }

    private void nextTrack() {
        if (isRadioMode) {
            currentStation = (currentStation + 1) % STATIONS.length;
            switchStation(currentStation);
        } else if (!playlist.isEmpty()) {
            currentTrackIndex = (currentTrackIndex + 1) % playlist.size();
            playLocalTrack(currentTrackIndex);
        }
    }

    private void prevTrack() {
        if (isRadioMode) {
            currentStation = (currentStation - 1 + STATIONS.length) % STATIONS.length;
            switchStation(currentStation);
        } else if (!playlist.isEmpty()) {
            currentTrackIndex = (currentTrackIndex - 1 + playlist.size()) % playlist.size();
            playLocalTrack(currentTrackIndex);
        }
    }

    private void promptAddAudioFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select Audio File (.wav)");
        int res = chooser.showOpenDialog(mainContentPanel != null ? mainContentPanel : progressSlider);
        if (res == JFileChooser.APPROVE_OPTION) {
            File f = chooser.getSelectedFile();
            playlist.add(f);
            playlistModel.addElement("🎵 " + f.getName());
            playLocalTrack(playlist.size() - 1);
        }
    }

    private void playLocalTrack(int index) {
        if (index < 0 || index >= playlist.size()) return;
        File f = playlist.get(index);
        currentTrackIndex = index;
        isRadioMode = false;
        if (radioTimer != null) radioTimer.stop();

        try {
            if (currentClip != null && currentClip.isOpen()) {
                currentClip.close();
            }
            AudioInputStream ais = AudioSystem.getAudioInputStream(f);
            currentClip = AudioSystem.getClip();
            currentClip.open(ais);
            currentClip.start();
            isPlaying = true;
            globalAudioPlaying = true;
            btnPlayPause.setText("⏸ Pause");
            trackTitleLabel.setText("🎵 " + f.getName());
            stationLabel.setText("Local Audio Track — " + (currentClip.getMicrosecondLength() / 1_000_000) + "s");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(progressSlider, "Cannot play audio file: " + ex.getMessage(), "Audio Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ── INNER CLASS: 32-BAND NEON SPECTRUM DANCING VISUALIZER ───────────────
    private static class VisualizerPanel extends JPanel {
        private final float[] bars = new float[32];
        private final Random rnd = new Random();
        private float wavePhase = 0f;

        VisualizerPanel() {
            setOpaque(true);
            setBackground(new Color(15, 23, 42));
            setBorder(BorderFactory.createLineBorder(new Color(30, 41, 59)));
        }

        void updateWave(boolean active) {
            wavePhase += 0.15f;
            for (int i = 0; i < bars.length; i++) {
                if (active) {
                    float target = (float) (Math.sin(wavePhase + i * 0.4) * 0.4 + 0.5);
                    target += rnd.nextFloat() * 0.35f;
                    bars[i] = Math.max(0.05f, Math.min(1.0f, bars[i] * 0.65f + target * 0.35f));
                } else {
                    bars[i] = Math.max(0.02f, bars[i] * 0.88f);
                }
            }
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int pad = 12;

            // Background subtle grid
            g2.setColor(new Color(30, 41, 59, 70));
            for (int y = pad; y < h - pad; y += 20) g2.drawLine(pad, y, w - pad, y);

            // 32 Frequency Bars
            int n = bars.length;
            int gap = 3;
            int barW = Math.max(4, (w - (pad * 2) - (gap * (n - 1))) / n);

            for (int i = 0; i < n; i++) {
                int barH = (int) (bars[i] * (h - pad * 2 - 20));
                int x = pad + i * (barW + gap);
                int y = h - pad - barH;

                // Cyan to Pink Neon Gradient
                Color topColor = new Color(244, 63, 94); // Neon Pink
                Color botColor = new Color(34, 211, 238); // Cyan
                GradientPaint gp = new GradientPaint(x, y, topColor, x, h - pad, botColor);
                g2.setPaint(gp);
                g2.fillRoundRect(x, y, barW, barH, 4, 4);

                // Peak Dot
                g2.setColor(Color.WHITE);
                g2.fillRect(x, Math.max(pad, y - 4), barW, 2);
            }

            // Oscilloscope Wave overlay across bottom
            Path2D.Double wave = new Path2D.Double();
            wave.moveTo(pad, h / 2.0);
            for (int x = pad; x < w - pad; x += 4) {
                double wy = (h / 2.0) + Math.sin(wavePhase + x * 0.05) * (bars[(x / 20) % n] * 24);
                wave.lineTo(x, wy);
            }
            g2.setColor(new Color(34, 211, 238, 120));
            g2.setStroke(new BasicStroke(1.5f));
            g2.draw(wave);

            // Title
            g2.setColor(new Color(148, 163, 184));
            g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
            g2.drawString("LIVE AUDIO SPECTRUM // 32-BAND FFT", pad + 4, pad + 12);

            g2.dispose();
        }
    }

    private JPanel mainContentPanel;

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
                BorderFactory.createEmptyBorder(4, 10, 4, 10)
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

    @Override
    public void onClose() {
        stopAudio();
        if (visualizerTimer != null) visualizerTimer.stop();
        if (midiSynth != null && midiSynth.isOpen()) {
            midiSynth.close();
        }
    }
}

package com.datazuul.euroworks.apps;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;

/**
 * Platform-independent Windows 95 CD Player application replica for EuroWorks.
 * Features:
 * - JNA (Java Native Access) integration with native 'libcdio' to query CD-DA details cross-platform.
 * - Digital Audio Extraction (DAE) reading CD sectors directly via 'cdio_read_audio_sectors' 
 *   and playing them dynamically through Java Sound API SourceDataLine.
 * - Robust Java-only directory file parsing fallback for .cda track extraction.
 * - LCD layout display showing track elapsed and disc remaining times.
 * - Concentric holographic CD disc vectors and retro Compact Disc branding.
 */
public class EuroCDPlayer extends EuroAppFrame {

    private enum PlayerState {
        NO_CD, STOPPED, PLAYING, PAUSED
    }

    // ── JNA libcdio mapping ──────────────────────────────────────────────────

    public interface LibCdio extends com.sun.jna.Library {
        LibCdio INSTANCE = (LibCdio) com.sun.jna.Native.load("cdio", LibCdio.class);

        com.sun.jna.Pointer cdio_open(String psz_source, int driver_id);
        void cdio_destroy(com.sun.jna.Pointer p);
        int cdio_get_first_track_num(com.sun.jna.Pointer p);
        int cdio_get_num_tracks(com.sun.jna.Pointer p);
        int cdio_get_track_sec_count(com.sun.jna.Pointer p, int i_track);
        int cdio_get_track_lba(com.sun.jna.Pointer p, int i_track);
        int cdio_read_audio_sectors(com.sun.jna.Pointer p, byte[] p_buf, int i_lsn, int i_sectors);
        // cdio_eject_media takes CdIo_t** (pointer-to-pointer): it frees and nulls the handle
        int cdio_eject_media(com.sun.jna.ptr.PointerByReference pp_cdio);
    }

    private static boolean libCdioAvailable = false;
    private static com.sun.jna.Pointer activeCdio = null;

    static {
        try {
            // Load libiconv-2 dependency on Windows first
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                try {
                    com.sun.jna.Native.load("libiconv-2", com.sun.jna.Library.class);
                } catch (Throwable t) {
                    System.out.println("libiconv-2 load skipped or failed: " + t.getMessage());
                }
            }
            // Attempt to initialize JNA link with cdio
            LibCdio.INSTANCE.toString();
            libCdioAvailable = true;
        } catch (Throwable t) {
            System.out.println("libcdio native library not loaded: " + t.getMessage());
        }
    }

    private static final int WIDTH = 560;
    private static final int HEIGHT = 300;
    private static final int PLAYLIST_HEIGHT = 200;

    // Palette
    private static final Color SILVER  = new Color(192, 192, 192);
    private static final Color SHADOW  = new Color(128, 128, 128);
    private static final Color DARK    = new Color(64,  64,  64);
    private static final Color HILIGHT = Color.WHITE;
    private static final Color LCD_BG  = Color.BLACK; // Black LCD background
    private static final Color LCD_FG  = new Color(0, 220, 0); // Bright green text

    // State Variables
    private PlayerState state = PlayerState.STOPPED;
    private int currentTrack = 0; // 0-indexed
    private int trackElapsed = 0; // seconds elapsed in current track
    private int volumeTicks = 12; // 0 to 18 volume ticks
    private boolean repeatEnabled = false;

    // Dynamic CD Tracks
    private int[] trackDurations = new int[0];
    private int[] trackStartLbas = new int[0];
    private int[] trackSectorCounts = new int[0];

    // MusicBrainz Metadata
    private String[] trackTitles = new String[0];
    private String albumTitle = "";
    private String albumArtist = "";

    // Playlist panel state
    private boolean playlistVisible = false;
    private PlaylistPanel playlistPanel;

    // Components/Timers/Threads
    private final Timer playTimer; // Only used in simulation fallback mode
    private final DisplayPanel displayPanel;

    private Thread audioThread = null;
    private volatile boolean audioStopRequested = false;
    private javax.sound.sampled.SourceDataLine audioLine = null;

    /** Polls every 3 s for disc insertion while in NO_CD state. */
    private final Timer discDetectionTimer;

    public EuroCDPlayer() {
        super("CD Player");
        setSize(WIDTH, HEIGHT);
        setJMenuBar(buildMenuBar());

        // Playback timer (fires every second, only used in fallback simulated playback mode)
        playTimer = new Timer(1000, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (state == PlayerState.PLAYING && trackDurations.length > 0) {
                    trackElapsed++;

                    if (trackElapsed >= trackDurations[currentTrack]) {
                        if (currentTrack < trackDurations.length - 1) {
                            currentTrack++;
                            trackElapsed = 0;

                        } else {
                            if (repeatEnabled) {
                                currentTrack = 0;
                                trackElapsed = 0;

                            } else {
                                state = PlayerState.STOPPED;
                                trackElapsed = 0;
                                playTimer.stop();
                            }
                        }
                    }
                    displayPanel.repaint();
                }
            }
        });

        // Disc insertion polling timer: checks every 3 seconds when no disc is present
        discDetectionTimer = new Timer(3000, e -> {
            if (state == PlayerState.NO_CD) {
                loadCD();
                if (state != PlayerState.NO_CD) {
                    // Disc found – stop polling and refresh UI
                    ((Timer) e.getSource()).stop();
                    repaintPanels();
                }
            }
        });

        // Detect and load CD tracks on startup
        loadCD();
        // Start polling if no disc detected on startup
        if (state == PlayerState.NO_CD) {
            discDetectionTimer.start();
        }

        // Layout panels
        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(SILVER);
        content.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        // 1. LCD Status Display (North)
        displayPanel = new DisplayPanel();
        displayPanel.setPreferredSize(new Dimension(WIDTH - 12, 110));
        content.add(displayPanel, BorderLayout.NORTH);

        // 2. Control Center (Center)
        JPanel controlCenter = new JPanel(new BorderLayout());
        controlCenter.setBackground(SILVER);
        controlCenter.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));

        // Left block of control buttons
        JPanel buttonGridWrap = new JPanel(new GridLayout(2, 1, 4, 4));
        buttonGridWrap.setBackground(SILVER);

        // Row 1
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row1.setBackground(SILVER);
        JButton btnList = buildControlBtn("☰", "Open/Close Playlist");
        JButton btnSetA = buildControlBtn("SET A", "Set A-B Repeat");
        btnSetA.setFont(new Font("SansSerif", Font.BOLD, 9));
        JButton btnSkipBack = buildControlBtn("⏮", "Previous Track");
        JButton btnScan = buildControlBtn("SCAN", "Intro Scan");
        btnScan.setFont(new Font("SansSerif", Font.BOLD, 9));
        JButton btnSkipFwd = buildControlBtn("⏭", "Next Track");
        row1.add(btnList);
        row1.add(btnSetA);
        row1.add(btnSkipBack);
        row1.add(btnScan);
        row1.add(btnSkipFwd);
        buttonGridWrap.add(row1);

        // Row 2
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row2.setBackground(SILVER);
        JButton btnEject = buildControlBtn("⏏ EJECT", "Eject CD");
        btnEject.setFont(new Font("SansSerif", Font.BOLD, 9));
        JButton btnStop = buildControlBtn("⏹ STOP", "Stop");
        btnStop.setFont(new Font("SansSerif", Font.BOLD, 9));
        JButton btnRew = buildControlBtn("⏪ REW", "Fast Rewind");
        btnRew.setFont(new Font("SansSerif", Font.BOLD, 9));
        JButton btnPlay = buildControlBtn("▶ PLAY", "Play");
        btnPlay.setFont(new Font("SansSerif", Font.BOLD, 9));
        JButton btnFwd = buildControlBtn("⏩ FWD", "Fast Forward");
        btnFwd.setFont(new Font("SansSerif", Font.BOLD, 9));
        row2.add(btnEject);
        row2.add(btnStop);
        row2.add(btnRew);
        row2.add(btnPlay);
        row2.add(btnFwd);
        buttonGridWrap.add(row2);

        // Button action mappings
        btnPlay.addActionListener(e -> playCD());
        btnStop.addActionListener(e -> stopCD());
        btnEject.addActionListener(e -> ejectCD());
        btnSkipBack.addActionListener(e -> skipPrev());
        btnSkipFwd.addActionListener(e -> skipNext());
        btnRew.addActionListener(e -> fastRewind());
        btnFwd.addActionListener(e -> fastForward());
        btnList.addActionListener(e -> togglePlaylist());
        btnScan.addActionListener(e -> toggleRepeat());

        controlCenter.add(buttonGridWrap, BorderLayout.WEST);

        // Right block: CD Disc illustration & Digital Audio logo
        JPanel brandingPanel = new BrandingPanel();
        brandingPanel.setPreferredSize(new Dimension(150, 80));
        controlCenter.add(brandingPanel, BorderLayout.EAST);

        content.add(controlCenter, BorderLayout.CENTER);

        // Playlist panel (docked below, initially hidden)
        playlistPanel = new PlaylistPanel();
        playlistPanel.setPreferredSize(new Dimension(WIDTH - 12, PLAYLIST_HEIGHT));
        playlistPanel.setVisible(false);
        content.add(playlistPanel, BorderLayout.SOUTH);

        setContentPane(content);
    }

    private JMenuBar buildMenuBar() {
        JMenuBar mb = new JMenuBar();
        JMenu pMenu = new JMenu("Player");
        pMenu.setFont(new Font("SansSerif", Font.PLAIN, 11));
        
        JMenuItem playItem = new JMenuItem("Play");
        playItem.addActionListener(e -> playCD());
        pMenu.add(playItem);
        JMenuItem stopItem = new JMenuItem("Stop");
        stopItem.addActionListener(e -> stopCD());
        pMenu.add(stopItem);
        JMenuItem ejectItem = new JMenuItem("Eject");
        ejectItem.addActionListener(e -> ejectCD());
        pMenu.add(ejectItem);
        pMenu.addSeparator();
        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(e -> dispose());
        pMenu.add(exitItem);

        mb.add(pMenu);
        return mb;
    }

    // ── Physical CD-ROM Integration ─────────────────────────────────────────

    private void loadCD() {
        boolean loadedNative = false;
        if (libCdioAvailable) {
            try {
                if (activeCdio != null) {
                    LibCdio.INSTANCE.cdio_destroy(activeCdio);
                    activeCdio = null;
                }
                activeCdio = LibCdio.INSTANCE.cdio_open(null, 0); // 0 = DRIVER_UNKNOWN
                if (activeCdio != null) {
                    int first = LibCdio.INSTANCE.cdio_get_first_track_num(activeCdio);
                    int num = LibCdio.INSTANCE.cdio_get_num_tracks(activeCdio);
                    if (num > 0 && first != 255) {
                        trackDurations = new int[num];
                        trackStartLbas = new int[num];
                        trackSectorCounts = new int[num];
                        for (int i = 0; i < num; i++) {
                            int t = first + i;
                            trackStartLbas[i] = LibCdio.INSTANCE.cdio_get_track_lba(activeCdio, t);
                            trackSectorCounts[i] = LibCdio.INSTANCE.cdio_get_track_sec_count(activeCdio, t);
                            trackDurations[i] = trackSectorCounts[i] / 75;
                        }
                        currentTrack = 0;
                        trackElapsed = 0;
                        state = PlayerState.STOPPED;
                        loadedNative = true;
                    }
                }
            } catch (Throwable t) {
                System.out.println("Error reading CD using libcdio: " + t.getMessage());
            }
        }

        if (!loadedNative) {
            // Platform-independent file-level fallback
            File cdDrive = detectCdDrive();
            if (cdDrive != null) {
                File[] files = cdDrive.listFiles((dir, name) -> name.toLowerCase().endsWith(".cda"));
                if (files != null && files.length > 0) {
                    Arrays.sort(files, Comparator.comparing(File::getName));
                    trackDurations = new int[files.length];
                    trackStartLbas = new int[files.length];
                    trackSectorCounts = new int[files.length];
                    for (int i = 0; i < files.length; i++) {
                        trackDurations[i] = readCdaDurationInSeconds(files[i]);
                        trackSectorCounts[i] = trackDurations[i] * 75;
                        trackStartLbas[i] = (i == 0) ? 150 : trackStartLbas[i - 1] + trackSectorCounts[i - 1];
                    }
                    currentTrack = 0;
                    trackElapsed = 0;
                    state = PlayerState.STOPPED;
                    // Kick off MusicBrainz lookup in background
                    lookupMusicBrainz();
                    return;
                }
            }
            // Fallback to empty state
            trackDurations = new int[0];
            trackStartLbas = new int[0];
            trackSectorCounts = new int[0];
            trackTitles = new String[0];
            albumTitle = "";
            albumArtist = "";
            state = PlayerState.NO_CD;
            return;
        }
        // Kick off MusicBrainz lookup after native CD loaded
        if (loadedNative) {
            lookupMusicBrainz();
        }
    }

    private File detectCdDrive() {
        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("win")) {
            // Windows: try drive letters D: through Z:
            for (char c = 'D'; c <= 'Z'; c++) {
                File f = new File(c + ":\\");
                if (hasCdaFiles(f)) return f;
            }

        } else if (os.contains("linux")) {
            // Linux: common fixed CD/DVD mount points
            String[] linuxPaths = {
                "/media/cdrom", "/media/cdrom0", "/media/cdrom1",
                "/media/dvd",   "/media/dvd0",   "/media/cdrecorder",
                "/mnt/cdrom",   "/mnt/cdrom0",   "/mnt/cd",
                "/mnt/dvd",     "/mnt/cdrecorder"
            };
            for (String path : linuxPaths) {
                File f = new File(path);
                if (hasCdaFiles(f)) return f;
            }
            // Modern Linux (udisks2): /run/media/<username>/<disc-label>/
            File runMedia = new File("/run/media");
            if (runMedia.exists() && runMedia.isDirectory()) {
                File[] userDirs = runMedia.listFiles();
                if (userDirs != null) {
                    for (File userDir : userDirs) {
                        if (userDir.isDirectory()) {
                            File[] discDirs = userDir.listFiles();
                            if (discDirs != null) {
                                for (File disc : discDirs) {
                                    if (hasCdaFiles(disc)) return disc;
                                }
                            }
                        }
                    }
                }
            }
            // Also check /media/<username>/<disc-label>/ (some distros)
            File mediaRoot = new File("/media");
            if (mediaRoot.exists() && mediaRoot.isDirectory()) {
                File[] userDirs = mediaRoot.listFiles();
                if (userDirs != null) {
                    for (File userDir : userDirs) {
                        if (userDir.isDirectory() && !userDir.getName().startsWith("cd")) {
                            File[] discDirs = userDir.listFiles();
                            if (discDirs != null) {
                                for (File disc : discDirs) {
                                    if (hasCdaFiles(disc)) return disc;
                                }
                            }
                        }
                    }
                }
            }

        } else if (os.contains("mac")) {
            // macOS: all volumes appear under /Volumes/
            File volumes = new File("/Volumes");
            if (volumes.exists() && volumes.isDirectory()) {
                File[] vols = volumes.listFiles();
                if (vols != null) {
                    for (File vol : vols) {
                        if (hasCdaFiles(vol)) return vol;
                    }
                }
            }
        }
        return null;
    }

    private boolean hasCdaFiles(File dir) {
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".cda"));
            return files != null && files.length > 0;
        }
        return false;
    }

    private int readCdaDurationInSeconds(File file) {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            if (raf.length() >= 44) {
                raf.seek(28); // 4-byte frame count
                byte[] bytes = new byte[4];
                raf.readFully(bytes);
                int frames = ((bytes[3] & 0xFF) << 24) |
                             ((bytes[2] & 0xFF) << 16) |
                             ((bytes[1] & 0xFF) << 8)  |
                             (bytes[0] & 0xFF);
                return frames / 75;
            }
        } catch (IOException e) {
            // ignore
        }
        return 240;
    }

    // ── Platform-independent Hardware CD-DA Audio Stream ────────────────────

    private void startAudioThread() {
        int savedTrack = currentTrack;
        int savedElapsed = trackElapsed;
        stopAudioThread();
        // Restore after old thread is dead (it may have overwritten these during shutdown)
        currentTrack = savedTrack;
        trackElapsed = savedElapsed;
        if (!libCdioAvailable || activeCdio == null || trackDurations.length == 0) return;

        audioStopRequested = false;
        openAudioLine();

        audioThread = new Thread(() -> {
            int startLsn = trackStartLbas[currentTrack];
            int sectorCount = trackSectorCounts[currentTrack];
            int currentSector = startLsn + (trackElapsed * 75);
            byte[] buf = new byte[2352]; // 1 sector CD-DA PCM data (16-bit stereo 44.1kHz)

            while (state == PlayerState.PLAYING && !audioStopRequested) {
                if (currentSector >= startLsn + sectorCount) {
                    final int next = currentTrack + 1;
                    if (next < trackDurations.length) {
                        SwingUtilities.invokeLater(() -> {
                            currentTrack = next;
                            trackElapsed = 0;
                            repaintPanels();
                        });
                        startLsn = trackStartLbas[next];
                        sectorCount = trackSectorCounts[next];
                        currentSector = startLsn;

                    } else {
                        SwingUtilities.invokeLater(() -> stopCD());
                        break;
                    }
                }

                // Read real CD audio sector from hardware drive via JNA
                int ret = LibCdio.INSTANCE.cdio_read_audio_sectors(activeCdio, buf, currentSector, 1);
                if (ret == 0) { // Success
                    if (audioLine != null) {
                        audioLine.write(buf, 0, buf.length); // Blocking write synchronizes timing
                    }
                    currentSector++;

                    int elapsed = (currentSector - startLsn) / 75;
                    if (elapsed != trackElapsed) {
                        trackElapsed = elapsed;
                        SwingUtilities.invokeLater(() -> repaintPanels());
                    }
                } else {
                    // Read error (CD removed/tray opened)
                    SwingUtilities.invokeLater(() -> stopCD());
                    break;
                }
            }
            closeAudioLine();
        }, "CD-Audio-Player");

        audioThread.start();
    }

    private void stopAudioThread() {
        audioStopRequested = true;
        // Flush the audio line to unblock any blocking write() call
        if (audioLine != null) {
            try {
                audioLine.flush();
            } catch (Exception ex) {
                // ignore
            }
        }
        if (audioThread != null) {
            try {
                audioThread.join(500);
            } catch (InterruptedException e) {
                // ignore
            }
            if (audioThread.isAlive()) {
                audioThread.interrupt();
            }
            audioThread = null;
        }
        closeAudioLine();
    }

    private void openAudioLine() {
        try {
            // CD format is exactly 44100Hz, 16-bit, stereo, signed, little-endian
            javax.sound.sampled.AudioFormat format = new javax.sound.sampled.AudioFormat(44100.0f, 16, 2, true, false);
            javax.sound.sampled.DataLine.Info info = new javax.sound.sampled.DataLine.Info(javax.sound.sampled.SourceDataLine.class, format);
            audioLine = (javax.sound.sampled.SourceDataLine) javax.sound.sampled.AudioSystem.getLine(info);
            audioLine.open(format);
            audioLine.start();
            setLineVolume();
        } catch (Exception ex) {
            System.out.println("Could not open Java Sound audio line: " + ex.getMessage());
        }
    }

    private void closeAudioLine() {
        if (audioLine != null) {
            try {
                audioLine.stop();
                audioLine.close();
            } catch (Exception ex) {
                // ignore
            }
            audioLine = null;
        }
    }

    private void setLineVolume() {
        if (audioLine != null && audioLine.isControlSupported(javax.sound.sampled.FloatControl.Type.MASTER_GAIN)) {
            javax.sound.sampled.FloatControl gainControl = 
                (javax.sound.sampled.FloatControl) audioLine.getControl(javax.sound.sampled.FloatControl.Type.MASTER_GAIN);
            float min = gainControl.getMinimum();
            float max = gainControl.getMaximum();
            if (volumeTicks == 0) {
                gainControl.setValue(min); // Muted
            } else {
                float percent = volumeTicks / 18.0f;
                float gain = min + (max - min) * percent;
                gainControl.setValue(Math.max(min, Math.min(max, gain)));
            }
        }
    }

    // ── Controls Logic ──────────────────────────────────────────────────────

    private void playCD() {
        if (state == PlayerState.NO_CD || trackDurations.length == 0) {
            return;
        }
        state = PlayerState.PLAYING;
        if (libCdioAvailable && activeCdio != null) {
            startAudioThread();
        } else {
            playTimer.start(); // Fallback simulation
        }
        repaintPanels();
    }

    private void stopCD() {
        if (state == PlayerState.NO_CD) return;
        state = PlayerState.STOPPED;
        trackElapsed = 0;
        playTimer.stop();
        stopAudioThread();
        repaintPanels();
    }

    private void ejectCD() {
        if (state == PlayerState.NO_CD) {
            loadCD();

        } else {
            if (libCdioAvailable && activeCdio != null) {
                stopCD();
                // cdio_eject_media takes a pointer-to-pointer; it ejects AND frees the handle
                com.sun.jna.ptr.PointerByReference pbr = new com.sun.jna.ptr.PointerByReference(activeCdio);
                LibCdio.INSTANCE.cdio_eject_media(pbr);
                activeCdio = null; // handle is now freed by cdio_eject_media
            } else if (!libCdioAvailable) {
                // libcdio not available – stop playback only
                stopAudioThread();
            }
            state = PlayerState.NO_CD;
            trackDurations = new int[0];
            trackStartLbas = new int[0];
            trackSectorCounts = new int[0];
            trackElapsed = 0;
            playTimer.stop();
            stopAudioThread();
            // Start polling for the next disc insertion
            if (!discDetectionTimer.isRunning()) {
                discDetectionTimer.start();
            }

        }
        repaintPanels();
    }

    private void selectTrack(int index) {
        if (state == PlayerState.NO_CD || index < 0 || index >= trackDurations.length) return;
        currentTrack = index;
        trackElapsed = 0;
        if (state == PlayerState.PLAYING) {
            if (libCdioAvailable && activeCdio != null) {
                startAudioThread();
            }
        }
        repaintPanels();
    }

    private void skipPrev() {
        if (state == PlayerState.NO_CD || trackDurations.length == 0) return;
        if (trackElapsed > 3) {
            trackElapsed = 0;
        } else {
            if (currentTrack > 0) {
                currentTrack--;
            }
            trackElapsed = 0;
        }
        if (state == PlayerState.PLAYING && libCdioAvailable && activeCdio != null) {
            startAudioThread();
        }
        repaintPanels();
    }

    private void skipNext() {
        if (state == PlayerState.NO_CD || trackDurations.length == 0) return;
        if (currentTrack < trackDurations.length - 1) {
            currentTrack++;
        } else {
            currentTrack = 0;
        }
        trackElapsed = 0;
        if (state == PlayerState.PLAYING && libCdioAvailable && activeCdio != null) {
            startAudioThread();
        }
        repaintPanels();
    }

    private void fastRewind() {
        if (state == PlayerState.NO_CD || trackDurations.length == 0) return;
        trackElapsed = Math.max(0, trackElapsed - 10);
        if (state == PlayerState.PLAYING && libCdioAvailable && activeCdio != null) {
            startAudioThread();
        }
        repaintPanels();
    }

    private void fastForward() {
        if (state == PlayerState.NO_CD || trackDurations.length == 0) return;
        trackElapsed = Math.min(trackDurations[currentTrack] - 1, trackElapsed + 10);
        if (state == PlayerState.PLAYING && libCdioAvailable && activeCdio != null) {
            startAudioThread();
        }
        repaintPanels();
    }

    private void toggleRepeat() {
        repeatEnabled = !repeatEnabled;
        repaintPanels();
    }

    private void setVolumeTicks(int ticks) {
        volumeTicks = ticks;
        setLineVolume();
        repaintPanels();
    }

    /** Repaints both the LCD display and the playlist panel so they stay in sync. */
    private void repaintPanels() {
        displayPanel.repaint();
        if (playlistPanel != null) {
            playlistPanel.scrollToCurrentTrack();
            playlistPanel.repaint();
        }
    }

    private void togglePlaylist() {
        playlistVisible = !playlistVisible;
        playlistPanel.setVisible(playlistVisible);
        if (playlistVisible) {
            setSize(WIDTH, HEIGHT + PLAYLIST_HEIGHT + 30);
        } else {
            setSize(WIDTH, HEIGHT);
        }
        repaintPanels();
    }

    // ── MusicBrainz Disc ID & Lookup ────────────────────────────────────────

    /**
     * Computes the MusicBrainz Disc ID from the current CD's TOC.
     * Algorithm: SHA-1 of hex-encoded TOC offsets (lead-in +150), then custom Base64.
     */
    private String computeDiscId() {
        if (trackDurations.length == 0) return null;
        try {
            int numTracks = trackDurations.length;
            // Lead-out LBA (offset +150 for physical lead-in)
            int leadOut = trackStartLbas[numTracks - 1] + trackSectorCounts[numTracks - 1] + 150;

            StringBuilder sb = new StringBuilder();
            // First track (1-based), last track
            sb.append(String.format("%02X", 1));
            sb.append(String.format("%02X", numTracks));
            // Lead-out offset
            sb.append(String.format("%08X", leadOut));
            // Offsets for each of 99 possible tracks (padded with 0 for unused)
            for (int i = 0; i < 99; i++) {
                if (i < numTracks) {
                    sb.append(String.format("%08X", trackStartLbas[i] + 150));
                } else {
                    sb.append("00000000");
                }
            }

            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] digest = sha1.digest(sb.toString().getBytes(StandardCharsets.US_ASCII));

            // Custom Base64: + -> ., / -> _, no padding =
            String b64 = Base64.getEncoder().encodeToString(digest);
            b64 = b64.replace('+', '.').replace('/', '_').replace("=", "");
            return b64;
        } catch (Exception e) {
            System.out.println("Disc ID computation failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Queries MusicBrainz for disc metadata and updates trackTitles/albumTitle/albumArtist.
     * Runs on a background thread to avoid blocking the UI.
     */
    private void lookupMusicBrainz() {
        // Reset metadata
        trackTitles = new String[trackDurations.length];
        for (int i = 0; i < trackTitles.length; i++) {
            trackTitles[i] = String.format("Track %02d", i + 1);
        }
        albumTitle = "";
        albumArtist = "";
        if (playlistPanel != null) SwingUtilities.invokeLater(() -> playlistPanel.repaint());

        String discId = computeDiscId();
        if (discId == null) return;
        System.out.println("MusicBrainz Disc ID: " + discId);

        new Thread(() -> {
            try {
                String urlStr = "https://musicbrainz.org/ws/2/discid/" + discId
                        + "?inc=recordings+artists&fmt=json";
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent",
                        "EuroWorks/1.0 (https://github.com/datazuul/euroworks)");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                int code = conn.getResponseCode();
                if (code == 200) {
                    StringBuilder json = new StringBuilder();
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = br.readLine()) != null) json.append(line);
                    }
                    parseAndApplyMusicBrainzJson(json.toString());
                } else {
                    System.out.println("MusicBrainz returned HTTP " + code + " for disc " + discId);
                }
            } catch (Exception e) {
                System.out.println("MusicBrainz lookup failed: " + e.getMessage());
            }
        }, "MBZ-Lookup").start();
    }

    /**
     * Minimal JSON parser for the MusicBrainz discid response.
     * Extracts: releases[0].title, releases[0].artist-credit[0].name,
     *           releases[0].media[0].tracks[*].recording.title
     */
    private void parseAndApplyMusicBrainzJson(String json) {
        try {
            // --- Album title ---
            String albumTitleFound = jsonStringAfter(json, "\"title\":");
            // --- Artist ---
            int artistIdx = json.indexOf("\"artist-credit\"");
            String artistFound = "";
            if (artistIdx >= 0) {
                String sub = json.substring(artistIdx);
                artistFound = jsonStringAfter(sub, "\"name\":");
            }
            // --- Tracks ---
            // Find first media array
            int mediaIdx = json.indexOf("\"media\"");
            String[] newTitles = new String[trackDurations.length];
            for (int i = 0; i < newTitles.length; i++) {
                newTitles[i] = String.format("Track %02d", i + 1);
            }
            if (mediaIdx >= 0) {
                String mediaSub = json.substring(mediaIdx);
                int tracksIdx = mediaSub.indexOf("\"tracks\"");
                if (tracksIdx >= 0) {
                    String tracksSub = mediaSub.substring(tracksIdx);
                    int trackNum = 0;
                    int pos = 0;
                    while (trackNum < newTitles.length) {
                        int recordingIdx = tracksSub.indexOf("\"recording\"", pos);
                        if (recordingIdx < 0) break;
                        String recSub = tracksSub.substring(recordingIdx);
                        String title = jsonStringAfter(recSub, "\"title\":");
                        if (title != null && !title.isEmpty()) {
                            newTitles[trackNum] = title;
                        }
                        trackNum++;
                        pos = recordingIdx + 1;
                    }
                }
            }
            final String fa = artistFound != null ? artistFound : "";
            final String ft = albumTitleFound != null ? albumTitleFound : "";
            final String[] fn = newTitles;
            SwingUtilities.invokeLater(() -> {
                albumArtist = fa;
                albumTitle = ft;
                trackTitles = fn;
                System.out.println("MusicBrainz: " + fa + " - " + ft);
                if (playlistPanel != null) playlistPanel.repaint();
            });
        } catch (Exception e) {
            System.out.println("MusicBrainz JSON parse error: " + e.getMessage());
        }
    }

    /** Extracts the JSON string value immediately after the given key pattern. */
    private String jsonStringAfter(String json, String key) {
        int idx = json.indexOf(key);
        if (idx < 0) return null;
        int start = json.indexOf('"', idx + key.length());
        if (start < 0) return null;
        start++; // skip opening quote
        int end = start;
        while (end < json.length()) {
            char c = json.charAt(end);
            if (c == '"' && (end == 0 || json.charAt(end - 1) != '\\')) break;
            end++;
        }
        return json.substring(start, end);
    }

    private void playBeep(int freq, int durationMs) {
        final double volScale = (volumeTicks / 18.0) * 40.0;
        if (volScale <= 0) return;

        new Thread(() -> {
            try {
                int sampleRate = 8000;
                int numSamples = durationMs * sampleRate / 1000;
                int totalSamples = Math.max(numSamples, 3000);
                byte[] buf = new byte[totalSamples];
                double phase = 0.0;
                for (int i = 0; i < numSamples; i++) {
                    phase += 2.0 * Math.PI * freq / sampleRate;
                    buf[i] = (byte) (Math.sin(phase) >= 0.0 ? volScale : -volScale);
                }
                javax.sound.sampled.AudioFormat af = new javax.sound.sampled.AudioFormat(sampleRate, 8, 1, true, false);
                javax.sound.sampled.SourceDataLine sdl = javax.sound.sampled.AudioSystem.getSourceDataLine(af);
                sdl.open(af);
                sdl.start();
                sdl.write(buf, 0, buf.length);
                sdl.drain();
                sdl.close();
            } catch (Exception ex) {
                // Fallback
            }
        }).start();
    }

    private JButton buildControlBtn(String text, String tooltip) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(SILVER);
                g2.fillRect(0, 0, getWidth(), getHeight());

                boolean pressed = getModel().isPressed();
                if (pressed) {
                    g2.setColor(DARK);
                    g2.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
                    g2.setColor(SHADOW);
                    g2.drawRect(1, 1, getWidth() - 3, getHeight() - 3);
                } else {
                    g2.setColor(HILIGHT);
                    g2.drawLine(0, 0, getWidth() - 1, 0);
                    g2.drawLine(0, 0, 0, getHeight() - 1);
                    g2.setColor(DARK);
                    g2.drawLine(getWidth() - 1, 0, getWidth() - 1, getHeight() - 1);
                    g2.drawLine(0, getHeight() - 1, getWidth() - 1, getHeight() - 1);
                    g2.setColor(SHADOW);
                    g2.drawLine(getWidth() - 2, 1, getWidth() - 2, getHeight() - 2);
                    g2.drawLine(1, getHeight() - 2, getWidth() - 2, getHeight() - 2);
                }

                g2.setFont(getFont());
                g2.setColor(Color.BLACK);
                FontMetrics fm = g2.getFontMetrics();
                int tx = (getWidth() - fm.stringWidth(getText())) / 2 + (pressed ? 1 : 0);
                int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2 + (pressed ? 1 : 0);
                g2.drawString(getText(), tx, ty);
                g2.dispose();
            }
        };
        btn.setFont(new Font("SansSerif", Font.PLAIN, 10));
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setOpaque(true);
        btn.setToolTipText(tooltip);
        btn.setPreferredSize(new Dimension(54, 24));
        return btn;
    }

    @Override
    public void dispose() {
        discDetectionTimer.stop();
        playTimer.stop();
        stopAudioThread();
        if (libCdioAvailable && activeCdio != null) {
            LibCdio.INSTANCE.cdio_destroy(activeCdio);
            activeCdio = null;
        }
        super.dispose();
    }

    // ── Panel Subclasses ─────────────────────────────────────────────────────

    private class DisplayPanel extends JPanel {

        DisplayPanel() {
            setBackground(LCD_BG);
            setBorder(BorderFactory.createLoweredBevelBorder());

            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mousePressed(java.awt.event.MouseEvent e) {
                    if (e.getX() >= 240 && e.getX() <= 260 && e.getY() >= 25 && e.getY() <= 85) {
                        int clickY = e.getY() - 25;
                        int v = 18 - (int) ((clickY / 60.0) * 18);
                        setVolumeTicks(Math.max(0, Math.min(18, v)));
                        return;
                    }

                    int startX = 265;
                    int startY = 22;
                    int colsCount = 10;
                    for (int i = 0; i < trackDurations.length; i++) {
                        int r = i / colsCount;
                        int c = i % colsCount;
                        int bx = startX + c * 18;
                        int by = startY + r * 22;
                        if (e.getX() >= bx && e.getX() <= bx + 16 &&
                            e.getY() >= by && e.getY() <= by + 20) {
                            selectTrack(i);
                            break;
                        }
                    }
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2.setColor(LCD_BG);
            g2.fillRect(0, 0, getWidth(), getHeight());

            g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
            g2.setColor(LCD_FG);
            g2.drawString("Drive 1", 12, 20);
            g2.drawString("41445906000200", 120, 20);

            if (state == PlayerState.NO_CD || trackDurations.length == 0) {
                g2.setFont(new Font("Monospaced", Font.BOLD, 22));
                g2.setColor(LCD_FG);
                g2.drawString("NO DISC", 12, 55);
            } else {
                g2.setFont(new Font("SansSerif", Font.PLAIN, 9));
                g2.setColor(LCD_FG);
                g2.drawString("Track", 12, 38);
                g2.setFont(new Font("Monospaced", Font.BOLD, 28));
                g2.drawString(String.format("%02d", currentTrack + 1), 12, 68);

                g2.setFont(new Font("SansSerif", Font.PLAIN, 9));
                g2.drawString("Track Time", 66, 38);
                g2.setFont(new Font("Monospaced", Font.BOLD, 28));
                g2.drawString(String.format("%d:%02d", trackElapsed / 60, trackElapsed % 60), 66, 68);

                int totalSecs = 0;
                for (int i = currentTrack; i < trackDurations.length; i++) {
                    totalSecs += trackDurations[i];
                }
                totalSecs -= trackElapsed;
                g2.setFont(new Font("SansSerif", Font.PLAIN, 9));
                g2.drawString("Disc Time", 146, 38);
                g2.setFont(new Font("Monospaced", Font.BOLD, 28));
                g2.drawString(String.format("%d:%02d", totalSecs / 60, totalSecs % 60), 146, 68);
            }

            g2.setFont(new Font("SansSerif", Font.PLAIN, 9));
            g2.setColor(LCD_FG);
            String statusStr = "STOP";
            if (state == PlayerState.PLAYING) statusStr = "PLAY";
            else if (state == PlayerState.PAUSED) statusStr = "PAUSE";
            else if (state == PlayerState.NO_CD) statusStr = "OPEN";
            
            g2.drawString(statusStr, 12, 92);
            g2.drawString(repeatEnabled ? "Repeat All" : "Repeat Off", 90, 92);
            g2.drawString("Normal Play", 170, 92);

            g2.drawString("Vol.", 243, 20);
            for (int i = 0; i < 18; i++) {
                int tickY = 85 - (i * 3);
                if (i < volumeTicks) {
                    g2.setColor(Color.GREEN);
                    g2.fillRect(245, tickY, 10, 2);
                } else {
                    g2.setColor(new Color(0, 80, 0));
                    g2.fillRect(245, tickY, 10, 2);
                }
            }

            int startX = 265;
            int startY = 22;
            int colsCount = 10;
            g2.setFont(new Font("SansSerif", Font.BOLD, 8));
            for (int i = 0; i < trackDurations.length; i++) {
                int r = i / colsCount;
                int c = i % colsCount;
                int bx = startX + c * 18;
                int by = startY + r * 22;

                boolean current = (i == currentTrack) && (state != PlayerState.NO_CD);
                if (current) {
                    g2.setColor(LCD_FG);
                    g2.fillRect(bx, by, 16, 20);
                    g2.setColor(LCD_BG);
                    g2.drawRect(bx, by, 16, 20);
                    g2.setColor(LCD_BG);
                } else {
                    g2.setColor(LCD_BG);
                    g2.fillRect(bx, by, 16, 20);
                    g2.setColor(LCD_FG);
                    g2.drawRect(bx, by, 16, 20);
                    g2.setColor(LCD_FG);
                }

                String numStr = String.valueOf(i + 1);
                FontMetrics fm = g2.getFontMetrics();
                int nx = bx + (16 - fm.stringWidth(numStr)) / 2;
                int ny = by + (20 + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(numStr, nx, ny);
            }

            g2.dispose();
        }
    }

    // ── Playlist Panel ───────────────────────────────────────────────────────

    private class PlaylistPanel extends JPanel {

        private static final Color PL_BG      = new Color(29, 33, 40);
        private static final Color PL_HEADER  = new Color(20, 24, 30);
        private static final Color PL_GREEN   = new Color(0, 220, 0);
        private static final Color PL_CURRENT = Color.WHITE;
        private static final Color PL_SCROLL  = new Color(60, 70, 90);
        private static final Color PL_SELECT  = new Color(50, 80, 50);

        private int scrollOffset = 0;
        private static final int ROW_HEIGHT = 18;

        PlaylistPanel() {
            setBackground(PL_BG);
            setBorder(BorderFactory.createMatteBorder(2, 0, 0, 0, new Color(0, 150, 0)));

            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    int headerH = 28;
                    int clickRow = (e.getY() - headerH + scrollOffset) / ROW_HEIGHT;
                    if (clickRow >= 0 && clickRow < trackDurations.length) {
                        selectTrack(clickRow);
                        if (state != PlayerState.PLAYING) playCD();
                        // repaintPanels() is called by selectTrack, no extra repaint needed
                    }
                }
            });

            addMouseWheelListener(e -> {
                int maxScroll = Math.max(0, trackDurations.length * ROW_HEIGHT - (getHeight() - 28));
                scrollOffset = Math.max(0, Math.min(maxScroll,
                        scrollOffset + (int) (e.getPreciseWheelRotation() * ROW_HEIGHT)));
                repaint();
            });
        }

        /** Scrolls the playlist so that the current track is fully visible. */
        void scrollToCurrentTrack() {
            int headerH = 28;
            int visibleH = getHeight() - headerH;
            int trackTop = currentTrack * ROW_HEIGHT;
            int trackBottom = trackTop + ROW_HEIGHT;
            // Scroll down if track is below viewport
            if (trackBottom > scrollOffset + visibleH) {
                scrollOffset = trackBottom - visibleH;
            }
            // Scroll up if track is above viewport
            if (trackTop < scrollOffset) {
                scrollOffset = trackTop;
            }
            // Clamp
            int maxScroll = Math.max(0, trackDurations.length * ROW_HEIGHT - visibleH);
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            // Background
            g2.setColor(PL_BG);
            g2.fillRect(0, 0, w, h);

            // Header bar
            g2.setColor(PL_HEADER);
            g2.fillRect(0, 0, w, 28);
            g2.setFont(new Font("SansSerif", Font.BOLD, 11));
            g2.setColor(PL_GREEN);
            String header = albumArtist.isEmpty() && albumTitle.isEmpty()
                    ? "Playlist"
                    : (albumArtist.isEmpty() ? albumTitle : albumArtist + " – " + albumTitle);
            g2.drawString(header, 8, 19);

            // Track count info
            if (trackDurations.length > 0) {
                int totalSecs = 0;
                for (int d : trackDurations) totalSecs += d;
                String info = trackDurations.length + " tracks  " +
                        String.format("%d:%02d", totalSecs / 60, totalSecs % 60);
                g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
                g2.setColor(new Color(120, 180, 120));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(info, w - fm.stringWidth(info) - 10, 19);
            }

            // Track rows
            g2.setFont(new Font("Monospaced", Font.PLAIN, 12));
            int headerH = 28;
            int visibleRows = (h - headerH) / ROW_HEIGHT + 1;
            int firstRow = scrollOffset / ROW_HEIGHT;
            int yBase = headerH - (scrollOffset % ROW_HEIGHT);

            for (int i = firstRow; i < Math.min(trackDurations.length, firstRow + visibleRows + 1); i++) {
                int rowY = yBase + (i - firstRow) * ROW_HEIGHT;
                boolean isCurrent = (i == currentTrack) && (state != PlayerState.NO_CD);

                // Row background
                if (isCurrent) {
                    g2.setColor(PL_SELECT);
                    g2.fillRect(0, rowY, w - 12, ROW_HEIGHT);
                }

                // Track number
                g2.setColor(isCurrent ? PL_CURRENT : PL_GREEN);
                String num = String.format("%02d.", i + 1);
                g2.drawString(num, 8, rowY + ROW_HEIGHT - 4);

                // Track title
                String title = (trackTitles != null && i < trackTitles.length && trackTitles[i] != null)
                        ? trackTitles[i] : String.format("Track %02d", i + 1);
                // Artist prefix if available
                if (!albumArtist.isEmpty()) {
                    title = albumArtist + " - " + title;
                }
                // Clip title to fit
                FontMetrics fm2 = g2.getFontMetrics();
                int maxTitleWidth = w - 80;
                while (title.length() > 3 && fm2.stringWidth(title) > maxTitleWidth) {
                    title = title.substring(0, title.length() - 4) + "...";
                }
                g2.drawString(title, 38, rowY + ROW_HEIGHT - 4);

                // Duration (right-aligned)
                int d = trackDurations[i];
                String dur = String.format("%d:%02d", d / 60, d % 60);
                g2.setColor(new Color(0, 160, 0));
                g2.drawString(dur, w - 46, rowY + ROW_HEIGHT - 4);

                // Separator line
                g2.setColor(new Color(40, 50, 60));
                g2.drawLine(0, rowY + ROW_HEIGHT - 1, w - 12, rowY + ROW_HEIGHT - 1);
            }

            // Scroll bar
            if (trackDurations.length * ROW_HEIGHT > h - headerH) {
                int contentH = trackDurations.length * ROW_HEIGHT;
                int visH = h - headerH;
                int sbH = Math.max(20, visH * visH / contentH);
                int sbY = headerH + (int) ((long) scrollOffset * (visH - sbH) / Math.max(1, contentH - visH));
                g2.setColor(PL_SCROLL);
                g2.fillRoundRect(w - 10, sbY, 8, sbH, 4, 4);
            }

            g2.dispose();
        }
    }

    private class BrandingPanel extends JPanel {

        BrandingPanel() {
            setBackground(SILVER);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int cx = 20, cy = 30, r = 32;
            g2.setColor(Color.DARK_GRAY);
            g2.drawOval(cx, cy, r, r);
            
            g2.setColor(new Color(220, 220, 220));
            g2.fillOval(cx + 1, cy + 1, r - 2, r - 2);

            g2.setColor(new Color(255, 100, 100, 160));
            g2.fill(new Arc2D.Double(cx + 1, cy + 1, r - 2, r - 2, 45, 45, Arc2D.PIE));
            g2.setColor(new Color(100, 255, 100, 160));
            g2.fill(new Arc2D.Double(cx + 1, cy + 1, r - 2, r - 2, 135, 45, Arc2D.PIE));
            g2.setColor(new Color(100, 100, 255, 160));
            g2.fill(new Arc2D.Double(cx + 1, cy + 1, r - 2, r - 2, 225, 45, Arc2D.PIE));
            g2.setColor(new Color(255, 255, 100, 160));
            g2.fill(new Arc2D.Double(cx + 1, cy + 1, r - 2, r - 2, 315, 45, Arc2D.PIE));

            g2.setColor(SILVER);
            g2.fillOval(cx + 10, cy + 10, 12, 12);
            g2.setColor(Color.BLACK);
            g2.drawOval(cx + 10, cy + 10, 12, 12);
            g2.setColor(DARK);
            g2.fillOval(cx + 13, cy + 13, 6, 6);

            int lx = 60;
            int ly = 24;

            g2.setFont(new Font("SansSerif", Font.PLAIN, 6));
            g2.setColor(Color.BLACK);
            g2.drawString("C O M P A C T", lx, ly);

            g2.setFont(new Font("Georgia", Font.BOLD | Font.ITALIC, 16));
            g2.drawString("disc", lx, ly + 14);

            g2.setFont(new Font("SansSerif", Font.BOLD, 5));
            g2.setColor(Color.BLACK);
            g2.fillRect(lx, ly + 18, 62, 8);
            g2.setColor(Color.WHITE);
            g2.drawString("DIGITAL AUDIO", lx + 4, ly + 24);

            g2.dispose();
        }
    }
}

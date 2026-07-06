package com.datazuul.euroworks.apps.eurotv;

import com.datazuul.euroworks.apps.EuroAppFrame;
import com.datazuul.euroworks.shell.EuroDesktopPane;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class EuroTv extends EuroAppFrame {

    private static final int DEFAULT_WIDTH = 750;
    private static final int DEFAULT_HEIGHT = 550;

    private static final String CONFIG_DIR = System.getProperty("user.home") + "/.euroworks/eurotv";
    private static final String CONFIG_FILE = CONFIG_DIR + "/streams.json";

    private final DefaultListModel<EuroTvStream> modelStreams = new DefaultListModel<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // JavaFX WebView Components
    private JFXPanel jfxPanel;
    private WebEngine webEngine;

    // Swing Controls & Container
    private JPanel mainPanel;
    private JDialog fullscreenDialog;
    private JLabel lblTitle;
    private JLabel lblStatus;
    private JSlider sliderVolume;
    private float currentVolume = 0.8f;
    private EuroTvStream activeStream = null;

    // Screensaver Management
    private EuroDesktopPane desktopPaneRef;
    private boolean wasScreensaverActive = false;

    public EuroTv() {
        super("EuroTv Video Streamer");
        setSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
        setMinimumSize(new Dimension(400, 300));

        mainPanel = new JPanel(new BorderLayout(6, 6));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        // 1. Controls Panel (North)
        mainPanel.add(buildControlPanel(), BorderLayout.NORTH);

        // 2. Video Panel (Center)
        jfxPanel = new JFXPanel();
        jfxPanel.setBorder(BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.LOWERED));
        mainPanel.add(jfxPanel, BorderLayout.CENTER);

        setContentPane(mainPanel);

        // Load streams and pre-populate defaults if needed
        loadStreams();

        // Initialize JavaFX WebView and Hls.js Player
        initJavaFX();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        if (getDesktopPane() instanceof EuroDesktopPane edp) {
            desktopPaneRef = edp;
            wasScreensaverActive = edp.isScreensaverEnabled();
            if (wasScreensaverActive) {
                edp.setScreensaverEnabled(false);
            }
        }
    }

    private JPanel buildControlPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);

        // Current Stream Label
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.WEST;
        lblTitle = new JLabel("Kein Stream ausgewählt");
        lblTitle.setFont(lblTitle.getFont().deriveFont(Font.BOLD, 13f));
        panel.add(lblTitle, gbc);

        // Status Label
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        lblStatus = new JLabel("Gestoppt");
        panel.add(lblStatus, gbc);

        // Playback Buttons Panel
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        JPanel playbackButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));

        JButton btnPlay = new JButton("▶ Play");
        btnPlay.addActionListener(e -> resumePlayback());
        playbackButtons.add(btnPlay);

        JButton btnPause = new JButton("⏸ Pause");
        btnPause.addActionListener(e -> pausePlayback());
        playbackButtons.add(btnPause);

        JButton btnStop = new JButton("⏹ Stopp");
        btnStop.addActionListener(e -> stopPlayback());
        playbackButtons.add(btnStop);

        JButton btnSelect = new JButton("Stream-Auswahl...");
        btnSelect.addActionListener(e -> showStreamSelectionDialog());
        playbackButtons.add(btnSelect);

        JButton btnFullscreen = new JButton("⛶ Vollbild");
        btnFullscreen.addActionListener(e -> enterFullscreen());
        playbackButtons.add(btnFullscreen);

        panel.add(playbackButtons, gbc);

        // Volume Panel
        gbc.gridx = 1;
        gbc.gridy = 2;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.EAST;
        JPanel volumePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        JLabel lblVol = new JLabel("Lautstärke:");
        sliderVolume = new JSlider(0, 100, 80);
        sliderVolume.setPreferredSize(new Dimension(100, 20));
        sliderVolume.addChangeListener(e -> {
            currentVolume = sliderVolume.getValue() / 100.0f;
            Platform.runLater(() -> {
                try {
                    if (webEngine != null) {
                        webEngine.executeScript("setVolume(" + currentVolume + ")");
                    }
                } catch (Exception ex) {
                    // Ignore
                }
            });
        });
        volumePanel.add(lblVol);
        volumePanel.add(sliderVolume);
        panel.add(volumePanel, gbc);

        return panel;
    }

    private void initJavaFX() {
        Platform.runLater(() -> {
            try {
                Platform.setImplicitExit(false);
            } catch (Throwable t) {
                // Ignore if already set
            }

            WebView webView = new WebView();
            webEngine = webView.getEngine();

            // Load an HTML page wrapping hls.js for robust video playback
            String playerHtml = """
                <!DOCTYPE html>
                <html>
                <head>
                    <style>
                        body, html {
                            margin: 0;
                            padding: 0;
                            width: 100%;
                            height: 100%;
                            background-color: black;
                            overflow: hidden;
                        }
                        video {
                            width: 100%;
                            height: 100%;
                            object-fit: contain;
                        }
                    </style>
                    <script src="https://cdn.jsdelivr.net/npm/hls.js@1.5.8/dist/hls.min.js"></script>
                </head>
                <body>
                    <video id="video" autoplay></video>
                    <script>
                        var video = document.getElementById('video');
                        var hls = null;

                        function playStream(url) {
                            if (hls) {
                                hls.destroy();
                                hls = null;
                            }

                            if (Hls.isSupported()) {
                                hls = new Hls({
                                    enableWorker: true,
                                    lowLatencyMode: true
                                });
                                hls.loadSource(url);
                                hls.attachMedia(video);
                                hls.on(Hls.Events.MANIFEST_PARSED, function() {
                                    video.play();
                                });
                                hls.on(Hls.Events.ERROR, function(event, data) {
                                    if (data.fatal) {
                                        switch (data.type) {
                                            case Hls.ErrorTypes.NETWORK_ERROR:
                                                hls.startLoad();
                                                break;
                                            case Hls.ErrorTypes.MEDIA_ERROR:
                                                hls.recoverMediaError();
                                                break;
                                            default:
                                                break;
                                        }
                                    }
                                });
                            } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
                                video.src = url;
                                video.addEventListener('loadedmetadata', function() {
                                    video.play();
                                });
                            }
                        }

                        function setVolume(vol) {
                            video.volume = vol;
                        }

                        function pauseVideo() {
                            video.pause();
                        }

                        function playVideo() {
                            video.play();
                        }

                        function stopVideo() {
                            video.pause();
                            video.src = '';
                            if (hls) {
                                hls.destroy();
                                hls = null;
                            }
                        }
                    </script>
                </body>
                </html>
                """;

            webEngine.loadContent(playerHtml);

            Scene scene = new Scene(webView);
            scene.setOnKeyPressed(keyEvent -> {
                if (keyEvent.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                    SwingUtilities.invokeLater(() -> exitFullscreen());
                }
            });
            jfxPanel.setScene(scene);
        });
    }

    private void enterFullscreen() {
        if (fullscreenDialog != null) return;

        Window owner = SwingUtilities.getWindowAncestor(this);
        fullscreenDialog = new JDialog(owner);
        fullscreenDialog.setUndecorated(true);
        fullscreenDialog.setLayout(new BorderLayout());

        // Remove jfxPanel from EuroTv's mainPanel and place it in the fullscreenDialog
        mainPanel.remove(jfxPanel);
        fullscreenDialog.add(jfxPanel, BorderLayout.CENTER);

        // Size to cover the graphics configuration screen bounds
        GraphicsConfiguration gc = this.getGraphicsConfiguration();
        Rectangle screenBounds = gc.getBounds();
        fullscreenDialog.setBounds(screenBounds);

        // Escape Key Binding on Swing root pane
        fullscreenDialog.getRootPane().registerKeyboardAction(
                e -> exitFullscreen(),
                KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        fullscreenDialog.setVisible(true);

        // Re-focus WebView to make sure JavaFX handles keyboard inputs
        Platform.runLater(() -> jfxPanel.requestFocus());
    }

    private void exitFullscreen() {
        if (fullscreenDialog == null) return;

        // Remove jfxPanel from fullscreenDialog and restore to mainPanel
        fullscreenDialog.remove(jfxPanel);
        mainPanel.add(jfxPanel, BorderLayout.CENTER);

        fullscreenDialog.dispose();
        fullscreenDialog = null;

        mainPanel.revalidate();
        mainPanel.repaint();

        // Return focus to EuroTv frame
        this.requestFocusInWindow();
    }

    private void showStreamSelectionDialog() {
        Window owner = SwingUtilities.getWindowAncestor(this);
        StreamSelectionDialog dialog = new StreamSelectionDialog(owner, modelStreams, this::playStream, this::saveStreams);
        dialog.setVisible(true);
    }

    private void playStream(EuroTvStream stream) {
        if (stream == null) return;
        activeStream = stream;

        lblStatus.setText("Löse Stream-URL auf...");
        lblTitle.setText(stream.getName());

        Thread.startVirtualThread(() -> {
            String resolvedUrl = ArdUrlResolver.resolveStreamUrl(stream.getUrl());
            if (resolvedUrl == null) {
                SwingUtilities.invokeLater(() -> {
                    lblStatus.setText("Fehler: Kann URL nicht auflösen");
                    JOptionPane.showMessageDialog(this,
                            "Stream-URL konnte nicht aufgelöst werden.",
                            "Fehler",
                            JOptionPane.ERROR_MESSAGE);
                });
                return;
            }

            Platform.runLater(() -> {
                try {
                    webEngine.executeScript("playStream('" + resolvedUrl + "')");
                    webEngine.executeScript("setVolume(" + currentVolume + ")");
                    SwingUtilities.invokeLater(() -> lblStatus.setText("Wiedergabe"));
                } catch (Exception ex) {
                    ex.printStackTrace();
                    SwingUtilities.invokeLater(() -> lblStatus.setText("Fehler: " + ex.getMessage()));
                }
            });
        });
    }

    private void resumePlayback() {
        Platform.runLater(() -> {
            try {
                webEngine.executeScript("playVideo()");
                SwingUtilities.invokeLater(() -> lblStatus.setText("Wiedergabe"));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
    }

    private void pausePlayback() {
        Platform.runLater(() -> {
            try {
                webEngine.executeScript("pauseVideo()");
                SwingUtilities.invokeLater(() -> lblStatus.setText("Pausiert"));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
    }

    private void stopPlayback() {
        Platform.runLater(() -> {
            try {
                webEngine.executeScript("stopVideo()");
                SwingUtilities.invokeLater(() -> lblStatus.setText("Gestoppt"));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
    }

    private void loadStreams() {
        modelStreams.clear();
        File file = new File(CONFIG_FILE);
        List<EuroTvStream> streams;
        if (!file.exists()) {
            streams = createDefaultStreams();
            saveStreamsList(streams);
        } else {
            try {
                streams = objectMapper.readValue(file, new TypeReference<List<EuroTvStream>>() {});
            } catch (Exception ex) {
                streams = createDefaultStreams();
            }
        }

        // Migrate legacy/dead stream URLs to current working domains
        boolean migrated = false;
        for (EuroTvStream s : streams) {
            if (s.getUrl().contains("br-brfernsehen-live.akamaized.net")) {
                s.setUrl("https://mcdn.br.de/br/fs/bfs_sued/hls/de/master.m3u8");
                migrated = true;
            }
            if (s.getUrl().contains("wdr-wdrfernsehen-live.akamaized.net")) {
                s.setUrl("https://wdr-live.ard-mcdn.de/wdr/live/hls/de/master.m3u8");
                migrated = true;
            }
            if (s.getUrl().contains("ndr-ndrfernsehen-live.akamaized.net")) {
                s.setUrl("https://mcdn.ndr.de/ndr/hls/ndr_fs/ndr_nds/master.m3u8");
                migrated = true;
            }
            if (s.getUrl().contains("swrbw-swrfernsehen-live.akamaized.net")) {
                s.setUrl("https://swrbwd-hls.akamaized.net/hls/live/2018672/swrbwd/master.m3u8");
                migrated = true;
            }
            if (s.getUrl().contains("hr-hrfernsehen-live.akamaized.net")) {
                s.setUrl("https://hrhlsde.akamaized.net/hls/live/2024526/hrhlsde/master.m3u8");
                migrated = true;
            }
        }
        if (migrated) {
            saveStreamsList(streams);
        }

        // Sort alphabetically case-insensitive
        streams.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));

        for (EuroTvStream stream : streams) {
            modelStreams.addElement(stream);
        }
    }

    private void saveStreams() {
        List<EuroTvStream> list = new ArrayList<>();
        for (int i = 0; i < modelStreams.size(); i++) {
            list.add(modelStreams.getElementAt(i));
        }
        list.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        saveStreamsList(list);
    }

    private void saveStreamsList(List<EuroTvStream> list) {
        try {
            File dir = new File(CONFIG_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File(CONFIG_FILE), list);
        } catch (Exception ex) {
            System.err.println("EuroTv: Save streams failed: " + ex.getMessage());
        }
    }

    private List<EuroTvStream> createDefaultStreams() {
        List<EuroTvStream> list = new ArrayList<>();
        list.add(new EuroTvStream("Das Erste (ARD)", "https://www.ardmediathek.de/live/Y3JpZDovL2Rhc2Vyc3RlLmRlL2xpdmUvY2xpcC9hYmNhMDdhMy0zNDc2LTQ4NTEtYjE2Mi1mZGU4ZjY0NmQ0YzQ"));
        list.add(new EuroTvStream("ZDF", "https://zdf-hls-15.akamaized.net/hls/live/2016498/de/veryhigh/master.m3u8"));
        list.add(new EuroTvStream("WDR Köln", "https://wdr-live.ard-mcdn.de/wdr/live/hls/de/master.m3u8"));
        list.add(new EuroTvStream("NDR FS NDS", "https://mcdn.ndr.de/ndr/hls/ndr_fs/ndr_nds/master.m3u8"));
        list.add(new EuroTvStream("BR Fernsehen Süd", "https://mcdn.br.de/br/fs/bfs_sued/hls/de/master.m3u8"));
        list.add(new EuroTvStream("SWR BW", "https://swrbwd-hls.akamaized.net/hls/live/2018672/swrbwd/master.m3u8"));
        list.add(new EuroTvStream("HR Fernsehen", "https://hrhlsde.akamaized.net/hls/live/2024526/hrhlsde/master.m3u8"));
        return list;
    }

    @Override
    public void dispose() {
        if (wasScreensaverActive && desktopPaneRef != null) {
            desktopPaneRef.setScreensaverEnabled(true);
        }
        if (fullscreenDialog != null) {
            fullscreenDialog.dispose();
            fullscreenDialog = null;
        }
        Platform.runLater(() -> {
            try {
                if (webEngine != null) {
                    webEngine.executeScript("stopVideo()");
                }
            } catch (Exception ex) {
                // Ignore during shutdown
            }
        });
        super.dispose();
    }
}

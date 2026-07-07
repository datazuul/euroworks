package com.datazuul.euroworks.apps.euroradio;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

import com.datazuul.euroworks.apps.EuroAppFrame;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * EuroRadio – An internet radio streaming application for EuroWorks.
 * Streams MP3 stations, parses M3U/PLS playlists, manages custom channels,
 * and allows searching the worldwide Radio-Browser.info database.
 */
public class EuroRadio extends EuroAppFrame {
    private static final int APP_WIDTH = 750;
    private static final int APP_HEIGHT = 550;

    private static final String CONFIG_DIR = System.getProperty("user.home") + "/.euroworks/euroradio";
    private static final String CONFIG_FILE = CONFIG_DIR + "/channels.json";

    // Colors & Fonts
    private static final Color LCD_BG = Color.BLACK;
    private static final Color LCD_FG = new Color(0, 220, 240); // Bright cyan digital look
    private static final Color LCD_MUTED = new Color(0, 90, 100);

    // Audio State
    private RadioPlaybackThread playbackThread = null;
    private float currentVolume = 0.8f;
    private RadioStation activeStation = null;

    // API Client
    private final RadioBrowserClient apiClient;
    private final ObjectMapper objectMapper;

    // UI Components
    private JLabel lblLCDTitle;
    private JLabel lblLCDStatus;
    private JLabel lblLCDStats;
    private JSlider sliderVolume;

    // Channels Tab
    private JList<RadioStation> listFavorites;
    private DefaultListModel<RadioStation> modelFavorites;
    private JButton btnPlayFav;
    private JButton btnStopFav;

    // Search Tab
    private JTextField txtSearch;
    private JTable tableSearch;
    private DefaultTableModel modelSearch;
    private List<RadioStation> searchResults = new ArrayList<>();
    private JButton btnPlaySearch;
    private JButton btnAddSearch;

    public EuroRadio() {
        super("EuroRadio");
        setSize(APP_WIDTH, APP_HEIGHT);
        setResizable(false);

        apiClient = new RadioBrowserClient();
        objectMapper = new ObjectMapper();

        // Initialize UI panels
        JPanel root = new JPanel(new BorderLayout());
        root.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        // 1. LCD & Player Controls (North)
        root.add(buildPlayerPanel(), BorderLayout.NORTH);

        // 2. Tabs for favorites and browsing (Center)
        root.add(buildTabsPanel(), BorderLayout.CENTER);

        setContentPane(root);

        // Load custom or default channels
        loadFavorites();

        // Pre-populate popular channels list in search tab in background
        loadPopularStations();
    }

    private JPanel buildPlayerPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 0));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 2, 0, new Color(128, 128, 128)),
                BorderFactory.createEmptyBorder(0, 0, 8, 0)));

        // A. Retro LCD Panel
        JPanel lcd = new JPanel();
        lcd.setLayout(new BoxLayout(lcd, BoxLayout.Y_AXIS));
        lcd.setBackground(LCD_BG);
        lcd.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(160, 160, 160)),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)));
        lcd.setPreferredSize(new Dimension(380, 80));

        lblLCDTitle = new JLabel("EuroRadio - Bereit");
        lblLCDTitle.setFont(new Font("Courier New", Font.BOLD, 14));
        lblLCDTitle.setForeground(LCD_FG);

        lblLCDStatus = new JLabel("Gestoppt");
        lblLCDStatus.setFont(new Font("Courier New", Font.PLAIN, 12));
        lblLCDStatus.setForeground(LCD_FG);

        lblLCDStats = new JLabel("Buffer: 0% | Bitrate: --- kbps | --- kHz");
        lblLCDStats.setFont(new Font("Courier New", Font.PLAIN, 10));
        lblLCDStats.setForeground(LCD_MUTED);

        lcd.add(lblLCDTitle);
        lcd.add(Box.createVerticalStrut(4));
        lcd.add(lblLCDStatus);
        lcd.add(Box.createVerticalStrut(4));
        lcd.add(lblLCDStats);

        panel.add(lcd, BorderLayout.CENTER);

        // B. Master Controls Panel (Right)
        JPanel controls = new JPanel(new GridLayout(2, 1, 0, 6));

        // Volume Panel
        JPanel volPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        JLabel lblVol = new JLabel("Lautstärke:");
        lblVol.setFont(new Font("SansSerif", Font.PLAIN, 11));

        sliderVolume = new JSlider(0, 100, 80);
        sliderVolume.setPreferredSize(new Dimension(150, 20));
        sliderVolume.addChangeListener(e -> {
            currentVolume = sliderVolume.getValue() / 100.0f;
            if (playbackThread != null) {
                playbackThread.setVolume(currentVolume);
            }
        });

        volPanel.add(lblVol);
        volPanel.add(sliderVolume);

        // Quick play state buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));

        JButton btnGlobalStop = buildRetroButton("⏹ Stopp");
        btnGlobalStop.setToolTipText("Wiedergabe stoppen");
        btnGlobalStop.addActionListener(e -> stopActivePlayback());
        buttonPanel.add(btnGlobalStop);

        controls.add(volPanel);
        controls.add(buttonPanel);

        panel.add(controls, BorderLayout.EAST);

        return panel;
    }

    private JTabbedPane buildTabsPanel() {
        JTabbedPane tabs = new JTabbedPane();

        // Tab 1: Favorites
        tabs.addTab("⭐ Favoriten (Kanäle)", buildFavoritesTab());

        // Tab 2: Browser
        tabs.addTab("🔍 Radio Stationen suchen", buildSearchTab());

        return tabs;
    }

    private JPanel buildFavoritesTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 0));
        panel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        modelFavorites = new DefaultListModel<>();
        listFavorites = new JList<>(modelFavorites);
        listFavorites.setFont(new Font("SansSerif", Font.BOLD, 12));
        listFavorites.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        listFavorites.setBorder(BorderFactory.createLoweredBevelBorder());

        listFavorites.addListSelectionListener(e -> {
            boolean hasSel = listFavorites.getSelectedIndex() != -1;
            btnPlayFav.setEnabled(hasSel);
            btnStopFav.setEnabled(hasSel);
        });

        // Double-click plays the favorite
        listFavorites.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    RadioStation selected = listFavorites.getSelectedValue();
                    if (selected != null) {
                        playStation(selected);
                    }
                }
            }
        });

        JScrollPane scroll = new JScrollPane(listFavorites);
        panel.add(scroll, BorderLayout.CENTER);

        // Buttons (Right side of List)
        JPanel buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.Y_AXIS));

        btnPlayFav = buildRetroButton("▶ Abspielen");
        btnPlayFav.setEnabled(false);
        btnPlayFav.addActionListener(e -> {
            RadioStation selected = listFavorites.getSelectedValue();
            if (selected != null)
                playStation(selected);
        });

        btnStopFav = buildRetroButton("⏹ Stopp");
        btnStopFav.setEnabled(false);
        btnStopFav.addActionListener(e -> stopActivePlayback());

        JButton btnAdd = buildRetroButton("➕ Neu...");
        btnAdd.addActionListener(e -> addNewFavoriteDialog());

        JButton btnDelete = buildRetroButton("➖ Löschen");
        btnDelete.addActionListener(e -> deleteSelectedFavorite());

        buttons.add(btnPlayFav);
        buttons.add(Box.createVerticalStrut(6));
        buttons.add(btnStopFav);
        buttons.add(Box.createVerticalStrut(12));
        buttons.add(btnAdd);
        buttons.add(Box.createVerticalStrut(6));
        buttons.add(btnDelete);

        panel.add(buttons, BorderLayout.EAST);

        return panel;
    }

    private JPanel buildSearchTab() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        // Top Search Bar
        JPanel searchBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));

        JLabel lblQuery = new JLabel("Name/Genre:");
        lblQuery.setFont(new Font("SansSerif", Font.PLAIN, 11));
        txtSearch = new JTextField(20);
        txtSearch.setFont(new Font("SansSerif", Font.PLAIN, 12));
        txtSearch.addActionListener(e -> triggerSearch());

        JButton btnSearch = buildRetroButton("Suchen");
        btnSearch.addActionListener(e -> triggerSearch());

        searchBar.add(lblQuery);
        searchBar.add(txtSearch);
        searchBar.add(btnSearch);

        panel.add(searchBar, BorderLayout.NORTH);

        // Results Table
        modelSearch = new DefaultTableModel(
                new Object[] { "Name", "Genre", "Codec", "Bitrate", "Land" }, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };

        tableSearch = new JTable(modelSearch);
        tableSearch.setFont(new Font("SansSerif", Font.PLAIN, 11));
        tableSearch.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tableSearch.getSelectionModel().addListSelectionListener(e -> {
            boolean hasSel = tableSearch.getSelectedRow() != -1;
            btnPlaySearch.setEnabled(hasSel);
            btnAddSearch.setEnabled(hasSel);
        });

        JScrollPane scroll = new JScrollPane(tableSearch);
        panel.add(scroll, BorderLayout.CENTER);

        // Table actions panel
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));

        btnPlaySearch = buildRetroButton("▶ Ausgewählte Station abspielen");
        btnPlaySearch.setEnabled(false);
        btnPlaySearch.addActionListener(e -> {
            int row = tableSearch.getSelectedRow();
            if (row != -1 && row < searchResults.size()) {
                playStation(searchResults.get(row));
            }
        });

        btnAddSearch = buildRetroButton("⭐ Zu Favoriten hinzufügen");
        btnAddSearch.setEnabled(false);
        btnAddSearch.addActionListener(e -> {
            int row = tableSearch.getSelectedRow();
            if (row != -1 && row < searchResults.size()) {
                addFavorite(searchResults.get(row));
            }
        });

        actionPanel.add(btnPlaySearch);
        actionPanel.add(btnAddSearch);
        panel.add(actionPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JButton buildRetroButton(String label) {
        return new JButton(label);
    }

    // ── Favorites IO ─────────────────────────────────────────────────────────

    private void loadFavorites() {
        modelFavorites.clear();
        File file = new File(CONFIG_FILE);
        List<RadioStation> favorites;
        if (!file.exists()) {
            favorites = createDefaultChannels();
            saveChannels(favorites);
        } else {
            try {
                favorites = objectMapper.readValue(file, new TypeReference<List<RadioStation>>() {
                });
            } catch (Exception ex) {
                favorites = createDefaultChannels();
            }
        }

        // Migrate legacy/dead cast.addradio.de stream URLs to current working domains
        boolean migrated = false;
        for (RadioStation rs : favorites) {
            if (rs.getUrl().contains("wdr-1live-live.cast.addradio.de")) {
                rs.setUrl("https://wdr-1live-live.icecastssl.wdr.de/wdr/1live/live/mp3/128/stream.mp3");
                migrated = true;
            }
            if (rs.getUrl().contains("swr-swr3-live.cast.addradio.de")) {
                rs.setUrl("https://liveradio.swr.de/sw282p3/swr3/play.mp3");
                migrated = true;
            }
            if (rs.getUrl().contains("dradio-dlf-live.cast.addradio.de")) {
                rs.setUrl("https://st01.sslstream.dlf.de/dlf/01/128/mp3/stream.mp3");
                migrated = true;
            }
        }

        // Sort alphabetically case-insensitive on load
        favorites.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));

        if (migrated) {
            saveChannels(favorites);
        }

        for (RadioStation station : favorites) {
            modelFavorites.addElement(station);
        }
    }

    private void saveFavorites() {
        RadioStation selected = listFavorites.getSelectedValue();
        List<RadioStation> list = new ArrayList<>();
        for (int i = 0; i < modelFavorites.size(); i++) {
            list.add(modelFavorites.getElementAt(i));
        }
        // Sort alphabetically case-insensitive
        list.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));

        modelFavorites.clear();
        for (RadioStation station : list) {
            modelFavorites.addElement(station);
        }
        if (selected != null) {
            listFavorites.setSelectedValue(selected, true);
        }
        saveChannels(list);
    }

    private void saveChannels(List<RadioStation> list) {
        try {
            File dir = new File(CONFIG_DIR);
            if (!dir.exists())
                dir.mkdirs();
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File(CONFIG_FILE), list);
        } catch (Exception ex) {
            System.err.println("EuroRadio: Save favorites failed: " + ex.getMessage());
        }
    }

    private List<RadioStation> createDefaultChannels() {
        List<RadioStation> list = new ArrayList<>();
        list.add(new RadioStation("WDR 1Live",
                "https://wdr-1live-live.icecastssl.wdr.de/wdr/1live/live/mp3/128/stream.mp3"));
        list.add(new RadioStation("SWR3", "https://liveradio.swr.de/sw282p3/swr3/play.mp3"));
        list.add(new RadioStation("Deutschlandfunk", "https://st01.sslstream.dlf.de/dlf/01/128/mp3/stream.mp3"));
        list.add(new RadioStation("Antenne Bayern", "http://stream.antenne.de/antenne"));
        return list;
    }

    // ── Search & API Integration ─────────────────────────────────────────────

    private void triggerSearch() {
        String q = txtSearch.getText().trim();
        if (q.isEmpty())
            return;

        modelSearch.setRowCount(0);
        searchResults.clear();

        Thread.startVirtualThread(() -> {
            List<RadioStation> stations = apiClient.searchStations(q, null, null, 40);
            SwingUtilities.invokeLater(() -> {
                searchResults.addAll(stations);
                for (RadioStation rs : stations) {
                    // Check if MP3 or AAC (our supported formats)
                    String codecDisplay = rs.getCodec();
                    if (codecDisplay != null) {
                        String codecLower = codecDisplay.toLowerCase();
                        if (!codecLower.contains("mp3") && !codecLower.contains("aac")) {
                            codecDisplay = codecDisplay + " (⚠️ unsupportiert)";
                        }
                    }
                    modelSearch.addRow(new Object[] {
                            rs.getName(),
                            rs.toString(), // JTable needs string columns
                            codecDisplay,
                            rs.getBitrate() > 0 ? rs.getBitrate() + " kbps" : "---",
                            rs.getUrl() // Dummy column or metadata
                    });
                }
                // Map the data correctly to search table columns
                refreshSearchTable();
            });
        });
    }

    private void loadPopularStations() {
        Thread.startVirtualThread(() -> {
            List<RadioStation> top = apiClient.getPopularStations(30);
            SwingUtilities.invokeLater(() -> {
                searchResults.clear();
                searchResults.addAll(top);
                refreshSearchTable();
            });
        });
    }

    private void refreshSearchTable() {
        modelSearch.setRowCount(0);
        for (RadioStation rs : searchResults) {
            String codecDisplay = rs.getCodec() != null ? rs.getCodec() : "MP3";
            String codecLower = codecDisplay.toLowerCase();
            if (!codecLower.contains("mp3") && !codecLower.contains("aac")) {
                codecDisplay = codecDisplay + " (⚠️ unsupportiert)";
            }
            modelSearch.addRow(new Object[] {
                    rs.getName(),
                    "", // We can populate tags/genres later if needed
                    codecDisplay,
                    rs.getBitrate() > 0 ? rs.getBitrate() + " kbps" : "---",
                    "" // We can populate country later if needed
            });
        }
    }

    // ── Playback control ─────────────────────────────────────────────────────

    private synchronized void playStation(RadioStation station) {
        stopActivePlayback();

        activeStation = station;
        lblLCDTitle.setText(station.getName());
        lblLCDStatus.setText("Verbinde...");
        lblLCDStats.setText("Puffere...");

        // Increment click stats in API
        if (station.getStationuuid() != null) {
            apiClient.reportClick(station.getStationuuid());
        }

        playbackThread = new RadioPlaybackThread(station, (status, errorMsg) -> {
            SwingUtilities.invokeLater(() -> {
                if (activeStation != station)
                    return; // Ignore updates from older threads

                switch (status) {
                    case CONNECTING -> {
                        lblLCDStatus.setText("Verbinde...");
                        lblLCDStats.setText("Lade URL...");
                    }
                    case BUFFERING -> {
                        lblLCDStatus.setText("Puffere...");
                        lblLCDStats.setText("Cushion wird gefüllt...");
                    }
                    case PLAYING -> {
                        lblLCDStatus.setText("Wiedergabe");
                        int br = playbackThread.getBitrate();
                        int sr = playbackThread.getSampleRate();
                        String brStr = br > 0 ? br + " kbps" : "--- kbps";
                        String srStr = sr > 0 ? (sr / 1000.0) + " kHz" : "--- kHz";
                        lblLCDStats.setText("Stream: " + brStr + " | " + srStr);
                    }
                    case STOPPED -> {
                        lblLCDStatus.setText("Gestoppt");
                        lblLCDStats.setText("");
                    }
                    case ERROR -> {
                        lblLCDStatus.setText("Fehler!");
                        lblLCDStats.setText(errorMsg != null ? errorMsg : "Unbekannter Stream-Fehler");
                    }
                }
            });
        });

        playbackThread.setVolume(currentVolume);
        playbackThread.start();
    }

    private synchronized void stopActivePlayback() {
        if (playbackThread != null) {
            playbackThread.stopPlayback();
            playbackThread = null;
        }
        activeStation = null;
        lblLCDTitle.setText("EuroRadio - Gestoppt");
        lblLCDStatus.setText("Gestoppt");
        lblLCDStats.setText("");
    }

    // ── Favorites Management ─────────────────────────────────────────────────

    private void addNewFavoriteDialog() {
        JTextField nameField = new JTextField();
        JTextField urlField = new JTextField();
        Object[] message = {
                "Stationsname:", nameField,
                "Stream URL:", urlField
        };

        int option = JOptionPane.showConfirmDialog(this, message, "Neue Radio-Station hinzufügen",
                JOptionPane.OK_CANCEL_OPTION);
        if (option == JOptionPane.OK_OPTION) {
            String name = nameField.getText().trim();
            String url = urlField.getText().trim();
            if (!name.isEmpty() && !url.isEmpty()) {
                RadioStation station = new RadioStation(name, url);
                modelFavorites.addElement(station);
                saveFavorites();
            } else {
                JOptionPane.showMessageDialog(this, "Bitte alle Felder ausfüllen.", "Eingabefehler",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void addFavorite(RadioStation station) {
        if (station == null)
            return;
        // Check if already in list
        for (int i = 0; i < modelFavorites.size(); i++) {
            if (modelFavorites.getElementAt(i).getUrl().equals(station.getUrl())) {
                JOptionPane.showMessageDialog(this, "Station ist bereits in den Favoriten.", "Info",
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }
        }
        modelFavorites.addElement(station);
        saveFavorites();
        JOptionPane.showMessageDialog(this, station.getName() + " zu Favoriten hinzugefügt.", "Erfolg",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void deleteSelectedFavorite() {
        int idx = listFavorites.getSelectedIndex();
        if (idx != -1) {
            RadioStation removed = modelFavorites.remove(idx);
            if (activeStation == removed) {
                stopActivePlayback();
            }
            saveFavorites();
        }
    }

    @Override
    public void dispose() {
        stopActivePlayback();
        super.dispose();
    }
}

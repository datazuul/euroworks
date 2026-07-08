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
import javax.swing.UIManager;
import javax.swing.table.DefaultTableModel;
import javax.swing.JTree;
import javax.swing.JPopupMenu;
import javax.swing.JMenuItem;
import javax.swing.Icon;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreePath;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeExpansionEvent;
import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import com.datazuul.euroworks.apps.EuroAppFrame;
import com.datazuul.euroworks.shell.EuroIconThemeManager;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

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

    // Colors & Fonts (using standard UIManager theme colors)

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

    // Länder (Countries) Tab
    private JTree treeCountries;
    private DefaultTreeModel modelCountries;
    private DefaultMutableTreeNode rootCountries;
    private JButton btnPlayCountry;
    private JButton btnAddCountry;

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

        // A. Retro LCD Panel (styled with theme colors)
        JPanel lcd = new JPanel();
        lcd.setLayout(new BoxLayout(lcd, BoxLayout.Y_AXIS));
        lcd.setBackground(UIManager.getColor("TextField.background"));
        Color borderColor = UIManager.getColor("Component.borderColor");
        if (borderColor == null) {
            borderColor = new Color(160, 160, 160);
        }
        lcd.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)));
        lcd.setPreferredSize(new Dimension(380, 80));

        lblLCDTitle = new JLabel("EuroRadio - Bereit");
        lblLCDTitle.setFont(new Font("Courier New", Font.BOLD, 14));
        lblLCDTitle.setForeground(UIManager.getColor("TextField.foreground"));

        lblLCDStatus = new JLabel("Gestoppt");
        lblLCDStatus.setFont(new Font("Courier New", Font.PLAIN, 12));
        lblLCDStatus.setForeground(UIManager.getColor("TextField.foreground"));

        lblLCDStats = new JLabel("Buffer: 0% | Bitrate: --- kbps | --- kHz");
        lblLCDStats.setFont(new Font("Courier New", Font.PLAIN, 10));
        lblLCDStats.setForeground(UIManager.getColor("TextField.inactiveForeground"));

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

        // Tab 3: Länder
        tabs.addTab("🌍 Länder", buildCountriesTab());

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
                        String friendlyMsg = errorMsg;
                        if (friendlyMsg != null) {
                            if (friendlyMsg.contains("unexpected profile") || friendlyMsg.contains("unsupported profile") || 
                                friendlyMsg.contains("too many bands") || friendlyMsg.contains("invalid huffman") ||
                                friendlyMsg.contains("Unsupported conversion")) {
                                friendlyMsg = "Format unsupportiert (Stream-Fehler)";
                            } else if (friendlyMsg.contains("Connection failed") || friendlyMsg.contains("HTTP Error") || friendlyMsg.contains("timeout")) {
                                friendlyMsg = "Verbindungsfehler (Stream offline?)";
                            }
                        } else {
                            friendlyMsg = "Unbekannter Stream-Fehler";
                        }
                        lblLCDStats.setText(friendlyMsg);
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

    // ── Countries Tab & Lazy Loading ──────────────────────────────────────────

    private JPanel buildCountriesTab() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        rootCountries = new DefaultMutableTreeNode("Länder");
        modelCountries = new DefaultTreeModel(rootCountries);
        treeCountries = new JTree(modelCountries);
        treeCountries.setFont(new Font("SansSerif", Font.PLAIN, 12));
        treeCountries.setRootVisible(false);
        treeCountries.setShowsRootHandles(true);
        treeCountries.setCellRenderer(new CountryTreeCellRenderer());

        JScrollPane scroll = new JScrollPane(treeCountries);
        panel.add(scroll, BorderLayout.CENTER);

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));

        btnPlayCountry = buildRetroButton("▶ Ausgewählte Station abspielen");
        btnPlayCountry.setEnabled(false);
        btnPlayCountry.addActionListener(e -> playSelectedTreeStation());

        btnAddCountry = buildRetroButton("⭐ Zu Favoriten hinzufügen");
        btnAddCountry.setEnabled(false);
        btnAddCountry.addActionListener(e -> addSelectedTreeStationToFavorites());

        actionPanel.add(btnPlayCountry);
        actionPanel.add(btnAddCountry);
        panel.add(actionPanel, BorderLayout.SOUTH);

        treeCountries.addTreeSelectionListener(e -> {
            DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) treeCountries.getLastSelectedPathComponent();
            boolean isStation = selectedNode != null && selectedNode.getUserObject() instanceof RadioStation;
            btnPlayCountry.setEnabled(isStation);
            btnAddCountry.setEnabled(isStation);
        });

        treeCountries.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) treeCountries.getLastSelectedPathComponent();
                    if (selectedNode != null && selectedNode.getUserObject() instanceof RadioStation station) {
                        playStation(station);
                    }
                }
                
                if (SwingUtilities.isRightMouseButton(e)) {
                    int row = treeCountries.getClosestRowForLocation(e.getX(), e.getY());
                    if (row != -1) {
                        treeCountries.setSelectionRow(row);
                        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) treeCountries.getLastSelectedPathComponent();
                        if (selectedNode != null && selectedNode.getUserObject() instanceof RadioStation station) {
                            showTreePopupMenu(e.getComponent(), e.getX(), e.getY(), station);
                        }
                    }
                }
            }
        });

        treeCountries.addTreeExpansionListener(new TreeExpansionListener() {
            @Override
            public void treeExpanded(TreeExpansionEvent event) {
                TreePath path = event.getPath();
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                if (node.getUserObject() instanceof CountryNodeUserObject userObj) {
                    if (node.getChildCount() == 1 && "Lade...".equals(node.getFirstChild().toString())) {
                        loadCountryStationsAsync(node, userObj.getCountryCode());
                    }
                }
            }

            @Override
            public void treeCollapsed(TreeExpansionEvent event) {}
        });

        loadCountriesAsync();

        return panel;
    }

    private void showTreePopupMenu(Component comp, int x, int y, RadioStation station) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem itemPlay = new JMenuItem("▶ Abspielen");
        itemPlay.addActionListener(e -> playStation(station));
        
        JMenuItem itemAdd = new JMenuItem("⭐ Zu Favoriten hinzufügen");
        itemAdd.addActionListener(e -> addFavorite(station));

        menu.add(itemPlay);
        menu.add(itemAdd);
        menu.show(comp, x, y);
    }

    private void playSelectedTreeStation() {
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) treeCountries.getLastSelectedPathComponent();
        if (selectedNode != null && selectedNode.getUserObject() instanceof RadioStation station) {
            playStation(station);
        }
    }

    private void addSelectedTreeStationToFavorites() {
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) treeCountries.getLastSelectedPathComponent();
        if (selectedNode != null && selectedNode.getUserObject() instanceof RadioStation station) {
            addFavorite(station);
        }
    }

    private void loadCountriesAsync() {
        Thread.startVirtualThread(() -> {
            Map<String, Integer> countryCodes = apiClient.getCountryCodes();
            
            List<CountryNodeUserObject> sortedList = new ArrayList<>();
            countryCodes.forEach((code, count) -> {
                if (code != null && !code.strip().isEmpty() && count != null) {
                    sortedList.add(new CountryNodeUserObject(code, count));
                }
            });

            // Sort alphabetically by country name
            sortedList.sort((a, b) -> a.getCountryName().compareToIgnoreCase(b.getCountryName()));

            SwingUtilities.invokeLater(() -> {
                rootCountries.removeAllChildren();
                for (CountryNodeUserObject nodeObj : sortedList) {
                    DefaultMutableTreeNode countryNode = new DefaultMutableTreeNode(nodeObj);
                    countryNode.add(new DefaultMutableTreeNode("Lade..."));
                    rootCountries.add(countryNode);
                }
                modelCountries.reload();
            });
        });
    }

    private void loadCountryStationsAsync(DefaultMutableTreeNode countryNode, String countryCodeValue) {
        Thread.startVirtualThread(() -> {
            List<RadioStation> stations = apiClient.getStationsByCountryCode(countryCodeValue, 20);
            SwingUtilities.invokeLater(() -> {
                countryNode.removeAllChildren();
                if (stations.isEmpty()) {
                    countryNode.add(new DefaultMutableTreeNode("Keine Stationen gefunden"));
                } else {
                    for (RadioStation rs : stations) {
                        countryNode.add(new DefaultMutableTreeNode(rs));
                    }
                }
                modelCountries.nodeStructureChanged(countryNode);
            });
        });
    }

    private static class CountryNodeUserObject {
        private final String countryCode;
        private final String countryName;
        private final int stationCount;

        public CountryNodeUserObject(String countryCode, int stationCount) {
            this.countryCode = countryCode;
            this.stationCount = stationCount;
            // Resolve localized German name using Java's standard Locale class
            String name = new java.util.Locale("", countryCode).getDisplayCountry(java.util.Locale.GERMAN);
            if (name == null || name.strip().isEmpty() || name.equals(countryCode)) {
                name = new java.util.Locale("", countryCode).getDisplayCountry();
            }
            this.countryName = (name != null && !name.strip().isEmpty()) ? name : countryCode;
        }

        public String getCountryCode() {
            return countryCode;
        }

        public String getCountryName() {
            return countryName;
        }

        public int getStationCount() {
            return stationCount;
        }

        @Override
        public String toString() {
            return countryName + " (" + stationCount + ")";
        }
    }

    private static class CountryTreeCellRenderer extends DefaultTreeCellRenderer {
        private final Icon radioIcon = EuroIconThemeManager.getIcon("radio");
        private final Icon folderIcon = EuroIconThemeManager.getIcon("folder");

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value,
                boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

            if (value instanceof DefaultMutableTreeNode node) {
                Object userObj = node.getUserObject();
                if (userObj instanceof CountryNodeUserObject countryNodeObj) {
                    Icon flag = FlagIconLoader.getFlagIcon(countryNodeObj.getCountryCode());
                    if (flag != null) {
                        setIcon(flag);
                    } else {
                        setIcon(folderIcon);
                    }
                } else if (userObj instanceof RadioStation) {
                    setIcon(radioIcon);
                }
            }
            return this;
        }
    }

    @Override
    public void dispose() {
        stopActivePlayback();
        super.dispose();
    }
}

package com.datazuul.euroworks.apps.euronews;

import com.datazuul.euroworks.apps.EuroAppFrame;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.DefaultTableModel;
import com.fasterxml.jackson.databind.ObjectMapper;

public class EuroNews extends EuroAppFrame {

    private static final int DEFAULT_WIDTH = 750;
    private static final int DEFAULT_HEIGHT = 550;

    private final NewsService newsService;
    
    // UI elements
    private JTextField txtSearch;
    private JList<EuropeanCountry> listCountries;
    private DefaultListModel<EuropeanCountry> modelList;
    
    private JLabel lblHeaderFlag;
    private JLabel lblHeaderCountryName;
    private JButton btnRefresh;
    private JLabel lblStatus;
    
    private JPanel cardContainer;
    private EuropeanCountry selectedCountry = null;
    private static final String CONFIG_DIR = System.getProperty("user.home") + "/.euroworks/euronews";
    private static final String CONFIG_FILE = CONFIG_DIR + "/settings.json";

    private int newsFontSize = 11;
    private List<NewsItem> currentNewsItems = new java.util.ArrayList<>();

    public EuroNews() {
        super("EuroNews Aggregator");
        loadSettings();
        setSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
        setMinimumSize(new Dimension(500, 380));

        newsService = new NewsService();

        // Main split panel
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(230);
        splitPane.setDividerSize(4);

        // 1. Left Panel (Countries)
        splitPane.setLeftComponent(buildLeftPanel());

        // 2. Right Panel (News Display)
        splitPane.setRightComponent(buildRightPanel());

        setContentPane(splitPane);

        // Select first country by default
        if (modelList.getSize() > 0) {
            listCountries.setSelectedIndex(0);
        }
    }

    private void loadSettings() {
        File file = new File(CONFIG_FILE);
        if (file.exists()) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                Map<?, ?> map = mapper.readValue(file, Map.class);
                if (map != null) {
                    if (map.containsKey("newsFontSize")) {
                        newsFontSize = ((Number) map.get("newsFontSize")).intValue();
                    }
                    if (map.containsKey("feeds")) {
                        Map<?, ?> feedsMap = (Map<?, ?>) map.get("feeds");
                        for (Map.Entry<?, ?> entry : feedsMap.entrySet()) {
                            try {
                                EuropeanCountry ec = EuropeanCountry.valueOf(entry.getKey().toString());
                                String url = entry.getValue() != null ? entry.getValue().toString() : "";
                                if (!url.trim().isEmpty()) {
                                    NewsService.SPECIFIC_FEEDS.put(ec, url);
                                } else {
                                    NewsService.SPECIFIC_FEEDS.remove(ec);
                                }
                            } catch (IllegalArgumentException e) {
                                // Skip unknown country keys
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("EuroNews: Failed to load settings: " + e.getMessage());
            }
        }
    }

    private void saveSettings() {
        try {
            File dir = new File(CONFIG_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> map = new HashMap<>();
            map.put("newsFontSize", newsFontSize);
            
            Map<String, String> feedsMap = new HashMap<>();
            for (Map.Entry<EuropeanCountry, String> entry : NewsService.SPECIFIC_FEEDS.entrySet()) {
                feedsMap.put(entry.getKey().name(), entry.getValue());
            }
            map.put("feeds", feedsMap);
            
            mapper.writerWithDefaultPrettyPrinter().writeValue(new File(CONFIG_FILE), map);
        } catch (Exception e) {
            System.err.println("EuroNews: Failed to save settings: " + e.getMessage());
        }
    }

    private void showSettingsDialog() {
        Window parentWindow = SwingUtilities.getWindowAncestor(this);
        JDialog dialog;
        if (parentWindow instanceof Frame) {
            dialog = new JDialog((Frame) parentWindow, "EuroNews Einstellungen", true);
        } else {
            dialog = new JDialog((Dialog) parentWindow, "EuroNews Einstellungen", true);
        }
        dialog.setLayout(new BorderLayout(8, 8));
        
        // 1. Top Panel: Font Size Selector
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 12));
        topPanel.add(new JLabel("Schriftgröße der News:"));
        Integer[] fontSizes = { 9, 10, 11, 12, 13, 14, 15, 16, 18, 20 };
        JComboBox<Integer> comboFontSize = new JComboBox<>(fontSizes);
        comboFontSize.setSelectedItem(newsFontSize);
        topPanel.add(comboFontSize);
        dialog.add(topPanel, BorderLayout.NORTH);
        
        // 2. Center Panel: RSS feeds list (JTable)
        String[] columns = { "Land", "RSS-Feed URL" };
        List<EuropeanCountry> countries = getSortedCountries();
        Object[][] data = new Object[countries.size()][2];
        for (int i = 0; i < countries.size(); i++) {
            EuropeanCountry c = countries.get(i);
            data[i][0] = c;
            data[i][1] = NewsService.SPECIFIC_FEEDS.getOrDefault(c, "");
        }
        
        DefaultTableModel tableModel = new DefaultTableModel(data, columns) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 1; // Only URL column is editable
            }
        };
        
        JTable table = new JTable(tableModel);
        table.getColumnModel().getColumn(0).setCellRenderer(new javax.swing.table.DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val, boolean isS, boolean hasF, int r, int c) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(t, val, isS, hasF, r, c);
                if (val instanceof EuropeanCountry ec) {
                    l.setText(ec.getName());
                }
                return l;
            }
        });
        
        table.setRowHeight(22);
        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setPreferredSize(new Dimension(550, 250));
        tableScroll.setBorder(BorderFactory.createTitledBorder("RSS-Feed URLs pro Land (bearbeitbar)"));
        
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
        centerPanel.add(tableScroll, BorderLayout.CENTER);
        dialog.add(centerPanel, BorderLayout.CENTER);
        
        // 3. Bottom Panel: Action Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 12));
        JButton btnSave = new JButton("Speichern");
        btnSave.addActionListener(e -> {
            if (table.isEditing()) {
                table.getCellEditor().stopCellEditing();
            }
            newsFontSize = (Integer) comboFontSize.getSelectedItem();
            
            // Save table rows back to SPECIFIC_FEEDS
            for (int i = 0; i < tableModel.getRowCount(); i++) {
                EuropeanCountry ec = (EuropeanCountry) tableModel.getValueAt(i, 0);
                String url = tableModel.getValueAt(i, 1) != null ? tableModel.getValueAt(i, 1).toString().trim() : "";
                if (!url.isEmpty()) {
                    NewsService.SPECIFIC_FEEDS.put(ec, url);
                } else {
                    NewsService.SPECIFIC_FEEDS.remove(ec);
                }
            }
            
            saveSettings();
            dialog.dispose();
            if (selectedCountry != null) {
                loadNewsAsync(selectedCountry);
            }
        });
        
        JButton btnCancel = new JButton("Abbrechen");
        btnCancel.addActionListener(e -> dialog.dispose());
        
        buttonPanel.add(btnSave);
        buttonPanel.add(btnCancel);
        dialog.add(buttonPanel, BorderLayout.SOUTH);
        
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private JPanel buildLeftPanel() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        // Filter / Search Input
        JPanel searchBar = new JPanel(new BorderLayout(4, 0));
        JLabel lblSearchIcon = new JLabel("🔍 ");
        searchBar.add(lblSearchIcon, BorderLayout.WEST);

        txtSearch = new JTextField();
        txtSearch.putClientProperty("JTextField.placeholderText", "Land filtern...");
        txtSearch.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                filterCountries();
            }
        });
        searchBar.add(txtSearch, BorderLayout.CENTER);
        panel.add(searchBar, BorderLayout.NORTH);

        // Countries list
        modelList = new DefaultListModel<>();
        for (EuropeanCountry country : getSortedCountries()) {
            modelList.addElement(country);
        }

        listCountries = new JList<>(modelList);
        listCountries.setCellRenderer(new CountryListCellRenderer());
        listCountries.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        listCountries.addListSelectionListener(this::countrySelectionChanged);

        JScrollPane scrollPane = new JScrollPane(listCountries);
        scrollPane.setBorder(BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.LOWERED));
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel buildRightPanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // Header Panel
        JPanel headerPanel = new JPanel(new GridBagLayout());
        headerPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UIManager.getColor("Component.borderColor")),
                BorderFactory.createEmptyBorder(0, 0, 8, 0)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 4, 2, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Flag
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.0;
        lblHeaderFlag = new JLabel();
        headerPanel.add(lblHeaderFlag, gbc);

        // Country name
        gbc.gridx = 1; gbc.weightx = 1.0;
        lblHeaderCountryName = new JLabel("Wählen Sie ein Land");
        lblHeaderCountryName.setFont(new Font("SansSerif", Font.BOLD, 16));
        headerPanel.add(lblHeaderCountryName, gbc);

        // Refresh button
        gbc.gridx = 2; gbc.weightx = 0.0;
        btnRefresh = new JButton("⟳ Aktualisieren");
        btnRefresh.setEnabled(false);
        btnRefresh.addActionListener(e -> {
            if (selectedCountry != null) {
                loadNewsAsync(selectedCountry);
            }
        });
        headerPanel.add(btnRefresh, gbc);

        // Settings button
        gbc.gridx = 3; gbc.weightx = 0.0;
        JButton btnSettings = new JButton("⚙ Einstellungen");
        btnSettings.addActionListener(e -> showSettingsDialog());
        headerPanel.add(btnSettings, gbc);

        panel.add(headerPanel, BorderLayout.NORTH);

        // News cards Container
        cardContainer = new JPanel();
        cardContainer.setLayout(new BoxLayout(cardContainer, BoxLayout.Y_AXIS));
        cardContainer.setBackground(UIManager.getColor("Panel.background"));

        JScrollPane newsScroll = new JScrollPane(cardContainer);
        newsScroll.setBorder(null);
        newsScroll.getVerticalScrollBar().setUnitIncrement(10);
        panel.add(newsScroll, BorderLayout.CENTER);

        // Status bar
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, UIManager.getColor("Component.borderColor")),
                BorderFactory.createEmptyBorder(4, 4, 0, 4)
        ));
        lblStatus = new JLabel("Bereit");
        lblStatus.setFont(new Font("SansSerif", Font.PLAIN, 10));
        lblStatus.setForeground(Color.GRAY);
        statusPanel.add(lblStatus, BorderLayout.WEST);

        panel.add(statusPanel, BorderLayout.SOUTH);

        return panel;
    }

    private List<EuropeanCountry> getSortedCountries() {
        List<EuropeanCountry> list = new java.util.ArrayList<>(List.of(EuropeanCountry.values()));
        list.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return list;
    }

    private void filterCountries() {
        String query = txtSearch.getText().trim().toLowerCase();
        EuropeanCountry currentlySelected = listCountries.getSelectedValue();
        
        modelList.clear();
        for (EuropeanCountry country : getSortedCountries()) {
            if (country.getName().toLowerCase().contains(query)) {
                modelList.addElement(country);
            }
        }
        
        // Re-select previous if it still matches
        if (currentlySelected != null && modelList.contains(currentlySelected)) {
            listCountries.setSelectedValue(currentlySelected, true);
        } else if (modelList.getSize() > 0) {
            listCountries.setSelectedIndex(0);
        } else {
            selectedCountry = null;
            lblHeaderFlag.setIcon(null);
            lblHeaderCountryName.setText("Keine Ergebnisse");
            btnRefresh.setEnabled(false);
            cardContainer.removeAll();
            cardContainer.revalidate();
            cardContainer.repaint();
            lblStatus.setText("Keine Länder gefunden.");
        }
    }

    private void countrySelectionChanged(ListSelectionEvent e) {
        if (!e.getValueIsAdjusting()) {
            EuropeanCountry country = listCountries.getSelectedValue();
            if (country != null && country != selectedCountry) {
                selectedCountry = country;
                btnRefresh.setEnabled(true);
                lblHeaderCountryName.setText(country.getName());
                
                // Set Header Large Flag
                try {
                    String svgPath = "images/flags/" + country.getCode() + ".svg";
                    lblHeaderFlag.setIcon(new FlatSVGIcon(svgPath, 48, 36));
                } catch (Exception ex) {
                    lblHeaderFlag.setIcon(null);
                }

                loadNewsAsync(country);
            }
        }
    }

    private void loadNewsAsync(EuropeanCountry country) {
        lblStatus.setText("Lade Nachrichten für " + country.getName() + "...");
        cardContainer.removeAll();
        cardContainer.revalidate();
        cardContainer.repaint();

        // Fetch asynchronously in virtual thread to prevent UI freezing
        Thread.startVirtualThread(() -> {
            try {
                List<NewsItem> items = newsService.getNewsForCountry(country);
                SwingUtilities.invokeLater(() -> {
                    currentNewsItems = items;
                    renderNewsItems();
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    lblStatus.setText("Fehler beim Laden.");
                    showNoNewsPanel(ex.getMessage());
                });
            }
        });
    }

    private void renderNewsItems() {
        cardContainer.removeAll();
        if (currentNewsItems == null || currentNewsItems.isEmpty()) {
            showNoNewsPanel("Keine Nachrichten für " + (selectedCountry != null ? selectedCountry.getName() : "dieses Land") + " gefunden.");
        } else {
            for (NewsItem item : currentNewsItems) {
                cardContainer.add(createNewsCard(item));
                cardContainer.add(Box.createVerticalStrut(10));
            }
        }
        lblStatus.setText("Letztes Update: " + java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")));
        cardContainer.revalidate();
        cardContainer.repaint();
    }

    private void showNoNewsPanel(String message) {
        cardContainer.removeAll();
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(30, 20, 30, 20));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0; gbc.gridy = 0;
        gbc.insets = new Insets(10, 10, 10, 10);

        // Warning/Error Icon
        Icon warnIcon = UIManager.getIcon("OptionPane.warningIcon");
        JLabel lblIcon = new JLabel(warnIcon);
        panel.add(lblIcon, gbc);

        gbc.gridy = 1;
        JLabel lblMsg = new JLabel("<html><center><body style='width: 300px;'>" + message + "</body></center></html>");
        lblMsg.setFont(new Font("SansSerif", Font.PLAIN, 12));
        lblMsg.setHorizontalAlignment(SwingConstants.CENTER);
        panel.add(lblMsg, gbc);

        cardContainer.add(panel);
        cardContainer.revalidate();
        cardContainer.repaint();
    }

    private JPanel createNewsCard(NewsItem item) {
        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(UIManager.getColor("TextField.background"));
        
        Border border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor"), 1, true),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)
        );
        card.setBorder(border);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 300));
        card.setMinimumSize(new Dimension(280, 120));

        boolean hasImage = item.getImageUrl() != null && !item.getImageUrl().trim().isEmpty();
        int textGridX = hasImage ? 1 : 0;
        int htmlWidth = hasImage ? 300 : 420;

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        if (hasImage) {
            gbc.gridx = 0; gbc.gridy = 0;
            gbc.gridheight = 4;
            gbc.weightx = 0.0;
            gbc.fill = GridBagConstraints.NONE;
            gbc.anchor = GridBagConstraints.NORTHWEST;
            gbc.insets = new Insets(2, 2, 2, 10);
            
            JLabel lblImage = new JLabel("Lade Bild...");
            lblImage.setPreferredSize(new Dimension(120, 90));
            lblImage.setMinimumSize(new Dimension(120, 90));
            lblImage.setMaximumSize(new Dimension(120, 90));
            lblImage.setBorder(BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor"), 1));
            lblImage.setHorizontalAlignment(SwingConstants.CENTER);
            lblImage.setFont(new Font("SansSerif", Font.PLAIN, 9));
            lblImage.setForeground(Color.LIGHT_GRAY);
            card.add(lblImage, gbc);
            
            final String imageUrl = item.getImageUrl();
            Thread.startVirtualThread(() -> {
                try {
                    java.net.URL imgUrl = new java.net.URL(imageUrl);
                    Image img = javax.imageio.ImageIO.read(imgUrl);
                    if (img != null) {
                        Image scaledImg = img.getScaledInstance(120, 90, Image.SCALE_SMOOTH);
                        ImageIcon icon = new ImageIcon(scaledImg);
                        SwingUtilities.invokeLater(() -> {
                            lblImage.setIcon(icon);
                            lblImage.setText("");
                        });
                    }
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> {
                        lblImage.setText("Kein Bild");
                    });
                }
            });
            
            // Restore constraints for text cells
            gbc.gridheight = 1;
            gbc.weightx = 1.0;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.anchor = GridBagConstraints.CENTER;
            gbc.insets = new Insets(2, 2, 2, 2);
        }

        // Title
        gbc.gridx = textGridX; gbc.gridy = 0;
        JLabel lblTitle = new JLabel("<html><body style='width: " + htmlWidth + "px;'><b>" + item.getTitle() + "</b></body></html>");
        lblTitle.setFont(new Font("SansSerif", Font.BOLD, newsFontSize + 1));
        lblTitle.setForeground(new Color(0, 90, 140)); // Nice retro/modern Steel Blue
        card.add(lblTitle, gbc);

        // Meta info (Date and source)
        gbc.gridx = textGridX; gbc.gridy = 1;
        JLabel lblMeta = new JLabel(item.getDate() + " | Quelle: " + item.getSource());
        lblMeta.setFont(new Font("SansSerif", Font.PLAIN, newsFontSize - 1));
        lblMeta.setForeground(Color.GRAY);
        card.add(lblMeta, gbc);

        // Description
        gbc.gridx = textGridX; gbc.gridy = 2;
        JLabel lblDesc = new JLabel("<html><body style='width: " + htmlWidth + "px;'>" + item.getDescription() + "</body></html>");
        lblDesc.setFont(new Font("SansSerif", Font.PLAIN, newsFontSize));
        card.add(lblDesc, gbc);

        // Actions panel
        gbc.gridx = textGridX; gbc.gridy = 3;
        JPanel actionsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actionsPanel.setOpaque(false);

        JButton btnCopy = new JButton("Kopieren");
        btnCopy.setFont(new Font("SansSerif", Font.PLAIN, Math.max(9, newsFontSize - 2)));
        btnCopy.setMargin(new Insets(2, 5, 2, 5));
        btnCopy.addActionListener(e -> {
            java.awt.datatransfer.StringSelection selection = new java.awt.datatransfer.StringSelection(item.getLink());
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
            lblStatus.setText("Link kopiert: " + item.getLink());
        });
        actionsPanel.add(btnCopy);

        // Translate button (only for non-German countries)
        if (selectedCountry != EuropeanCountry.GERMANY && 
            selectedCountry != EuropeanCountry.AUSTRIA && 
            selectedCountry != EuropeanCountry.SWITZERLAND) {
            
            final boolean[] isTranslated = {false};
            final String[] transTitle = {null};
            final String[] transDesc = {null};
            
            JButton btnTranslate = new JButton("Übersetzen");
            btnTranslate.setFont(new Font("SansSerif", Font.PLAIN, Math.max(9, newsFontSize - 2)));
            btnTranslate.setMargin(new Insets(2, 5, 2, 5));
            
            String origTitle = item.getTitle();
            String origDesc = item.getDescription();
            
            btnTranslate.addActionListener(e -> {
                if (isTranslated[0]) {
                    lblTitle.setText("<html><body style='width: " + htmlWidth + "px;'><b>" + origTitle + "</b></body></html>");
                    lblDesc.setText("<html><body style='width: " + htmlWidth + "px;'>" + origDesc + "</body></html>");
                    btnTranslate.setText("Übersetzen");
                    isTranslated[0] = false;
                    lblStatus.setText("Originaltext wird angezeigt.");
                } else {
                    if (transTitle[0] != null) {
                        lblTitle.setText("<html><body style='width: " + htmlWidth + "px;'><b>" + transTitle[0] + "</b></body></html>");
                        lblDesc.setText("<html><body style='width: " + htmlWidth + "px;'>" + transDesc[0] + "</body></html>");
                        btnTranslate.setText("Original");
                        isTranslated[0] = true;
                        lblStatus.setText("Übersetzung wird angezeigt.");
                    } else {
                        btnTranslate.setEnabled(false);
                        btnTranslate.setText("...");
                        lblStatus.setText("Übersetze ins Deutsche...");
                        Thread.startVirtualThread(() -> {
                            try {
                                String tTitle = TranslationService.translateToGerman(origTitle);
                                String tDesc = TranslationService.translateToGerman(origDesc);
                                SwingUtilities.invokeLater(() -> {
                                    transTitle[0] = tTitle;
                                    transDesc[0] = tDesc;
                                    lblTitle.setText("<html><body style='width: " + htmlWidth + "px;'><b>" + tTitle + "</b></body></html>");
                                    lblDesc.setText("<html><body style='width: " + htmlWidth + "px;'>" + tDesc + "</body></html>");
                                    btnTranslate.setText("Original");
                                    btnTranslate.setEnabled(true);
                                    isTranslated[0] = true;
                                    lblStatus.setText("Erfolgreich übersetzt.");
                                });
                            } catch (Exception ex) {
                                SwingUtilities.invokeLater(() -> {
                                    btnTranslate.setEnabled(true);
                                    btnTranslate.setText("Übersetzen");
                                    lblStatus.setText("Übersetzung fehlgeschlagen.");
                                    JOptionPane.showMessageDialog(card, "Übersetzung fehlgeschlagen: " + ex.getMessage(), "Fehler", JOptionPane.ERROR_MESSAGE);
                                });
                            }
                        });
                    }
                }
            });
            actionsPanel.add(btnTranslate);
        }

        if (item.getLink() != null && !item.getLink().isEmpty()) {
            JButton btnRead = new JButton("Mehr lesen...");
            btnRead.setFont(new Font("SansSerif", Font.BOLD, Math.max(9, newsFontSize - 2)));
            btnRead.setMargin(new Insets(2, 6, 2, 6));
            btnRead.addActionListener(e -> openUrl(item.getLink()));
            actionsPanel.add(btnRead);
        }
        
        card.add(actionsPanel, gbc);

        // Hover animation logic for dynamic UI feel
        card.addMouseListener(new MouseAdapter() {
            private final Color originalBg = card.getBackground();
            @Override
            public void mouseEntered(MouseEvent e) {
                card.setBackground(UIManager.getColor("List.hoverBackground"));
                card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            }
            @Override
            public void mouseExited(MouseEvent e) {
                card.setBackground(originalBg);
                card.setCursor(Cursor.getDefaultCursor());
            }
        });

        return card;
    }

    private void openUrl(String url) {
        if (url == null || url.strip().isEmpty()) return;

        JDesktopPane desktop = getDesktopPane();
        if (desktop != null) {
            // Find existing EuroWeb instance in JDesktopPane
            JInternalFrame existingBrowser = null;
            for (JInternalFrame frame : desktop.getAllFrames()) {
                if (frame.getClass().getName().contains("EuroWeb")) {
                    existingBrowser = frame;
                    break;
                }
            }

            if (existingBrowser != null) {
                try {
                    existingBrowser.setSelected(true);
                    if (existingBrowser.isIcon()) {
                        existingBrowser.setIcon(false);
                    }
                    existingBrowser.toFront();
                    
                    // Call navigateTo using reflection
                    java.lang.reflect.Method navigateMethod = existingBrowser.getClass().getMethod("navigateTo", String.class);
                    navigateMethod.invoke(existingBrowser, url);
                    lblStatus.setText("Lade Link in EuroWeb: " + url);
                    return;
                } catch (Exception ex) {
                    System.err.println("EuroNews: Failed to call navigateTo in existing EuroWeb instance: " + ex.getMessage());
                }
            }

            // Start a new EuroWeb instance using reflection
            try {
                Class<?> browserClass = Class.forName("com.datazuul.euroworks.apps.euroweb.EuroWeb");
                java.lang.reflect.Constructor<?> ctor = browserClass.getConstructor(String.class);
                JInternalFrame newBrowser = (JInternalFrame) ctor.newInstance(url);
                
                desktop.add(newBrowser);
                newBrowser.setBounds(30, 30, 800, 600);
                newBrowser.setSelected(true);
                newBrowser.toFront();
                lblStatus.setText("Starte EuroWeb und öffne Link: " + url);
                return;
            } catch (Exception ex) {
                System.err.println("EuroNews: Failed to instantiate EuroWeb with URL ctor: " + ex.getMessage());
            }
        }

        // Fallback: Open in external system browser
        try {
            if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
                lblStatus.setText("Link im System-Browser geöffnet: " + url);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Link konnte nicht geöffnet werden: " + ex.getMessage(), "Fehler", JOptionPane.ERROR_MESSAGE);
        }
    }

    @Override
    public String getIconThemeKey() {
        return "news";
    }

    private static class CountryListCellRenderer extends DefaultListCellRenderer {
        private final Map<String, Icon> flagCache = new ConcurrentHashMap<>();

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof EuropeanCountry country) {
                setText(country.getName());
                Icon icon = flagCache.computeIfAbsent(country.getCode(), c -> {
                    try {
                        String svgPath = "images/flags/" + c + ".svg";
                        java.net.URL url = CountryListCellRenderer.class.getClassLoader().getResource(svgPath);
                        if (url != null) {
                            return new FlatSVGIcon(svgPath, 24, 18);
                        }
                    } catch (Exception e) {
                        System.err.println("EuroNews: Failed to load flag SVG for " + c + ": " + e.getMessage());
                    }
                    return null;
                });
                setIcon(icon);
                setIconTextGap(10);
            }
            setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
            return this;
        }
    }
}

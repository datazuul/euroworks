package com.datazuul.euroworks.apps;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class EuroDex extends EuroAppFrame {

    private final List<Contact> contacts = new ArrayList<>();
    private final List<Contact> filteredContacts = new ArrayList<>();
    private int currentIndex = -1;
    private File contactsFile = new File(System.getProperty("user.home"), "contacts.eurodex");

    // UI form fields
    private final JTextField lastNameField;
    private final JTextField firstNameField;
    private final JTextField emailField;
    private final JTextArea addressArea;
    
    // Six Phone Fields
    private final JTextField phone1Field;
    private final JTextField phone2Field;
    private final JTextField phone3Field;
    private final JTextField phone4Field;
    private final JTextField phone5Field;
    private final JTextField phone6Field;

    // Six Phone Type Combos
    private final JComboBox<String> phone1TypeCombo;
    private final JComboBox<String> phone2TypeCombo;
    private final JComboBox<String> phone3TypeCombo;
    private final JComboBox<String> phone4TypeCombo;
    private final JComboBox<String> phone5TypeCombo;
    private final JComboBox<String> phone6TypeCombo;

    private boolean isDisplaying = false;

    // Sidebar Tab state
    private final char[] alphabet = {
            'A','B','C','D','E','F','G','H','I','J',
            'K','L','M','N','O','P','Q','R','S','T',
            'U','V','W','X','Y','Z','*'
    };
    private final JButton[] tabButtons = new JButton[27];
    private char activeLetter = '*';
    private String searchQuery = null;

    // List selection pane
    private final DefaultListModel<String> listModel;
    private final JList<String> namesList;
    private final JPanel listPanel;

    // Navigation buttons
    private final JButton btnBack;
    private final JButton btnNext;

    // Status
    private final JLabel statusLabel;

    public EuroDex() {
        super("EuroDex (Address Book)");
        setSize(700, 480); // Adjusted for split layouts

        // --- LEFT AREA: Stacked Tabs & Address Card ---
        JPanel leftContainer = new JPanel(new BorderLayout());
        leftContainer.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Create 3 stacked rows of index tabs (nested / folder divider style)
        JPanel tabsPanel = new JPanel(new GridLayout(3, 10, 2, 0));
        tabsPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        for (int i = 0; i < alphabet.length; i++) {
            char ch = alphabet[i];
            JButton btn = new JButton(String.valueOf(ch)) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    
                    boolean active = (ch == activeLetter && searchQuery == null);
                    
                    // Fill tab background (round at top, flat at bottom using extended height clipping)
                    if (active) {
                        g2.setColor(Color.BLACK);
                    } else {
                        g2.setColor(new Color(255, 255, 240)); // Vintage Cream (matching card background)
                    }
                    g2.fillRoundRect(0, 0, getWidth(), getHeight() + 4, 6, 6);
                    
                    // Draw outline
                    g2.setColor(Color.BLACK);
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() + 4, 6, 6);
                    
                    // Draw letter label
                    g2.setFont(new Font("Courier New", Font.BOLD, 12));
                    if (active) {
                        g2.setColor(Color.CYAN);
                    } else {
                        g2.setColor(Color.RED);
                    }
                    FontMetrics fm = g2.getFontMetrics();
                    String text = getText();
                    int tx = (getWidth() - fm.stringWidth(text)) / 2;
                    int ty = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
                    g2.drawString(text, tx, ty);
                    
                    g2.dispose();
                }

                @Override
                public Dimension getPreferredSize() {
                    Dimension d = super.getPreferredSize();
                    return new Dimension(d.width, 24);
                }
            };
            btn.setBorder(null);
            btn.setContentAreaFilled(false);
            btn.setFocusable(false);
            btn.setMargin(new Insets(1, 1, 1, 1));
            btn.addActionListener(e -> {
                autoSaveActiveCard();
                searchQuery = null; // Clear search on tab select
                activeLetter = ch;
                highlightActiveTab();
                updateContactsList();
            });
            tabButtons[i] = btn;
            tabsPanel.add(btn);
        }
        // Fill remaining slots in Row 3 (Row 3 has 7 letters, so add 3 blank fillers)
        for (int i = 0; i < 3; i++) {
            JPanel filler = new JPanel();
            filler.setOpaque(false);
            tabsPanel.add(filler);
        }
        leftContainer.add(tabsPanel, BorderLayout.NORTH);

        // Address Card Panel (Cream colored Rolodex Card)
        JPanel cardPanel = new JPanel(new GridBagLayout());
        cardPanel.setBackground(new Color(255, 255, 240)); // Vintage Cream
        cardPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.GRAY, 1),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 5, 3, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // 1. Name Input Block (divided into Nachname and Vorname fields side-by-side)
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.0; gbc.anchor = GridBagConstraints.WEST;
        JLabel nameLabel = new JLabel("Name (N,V):");
        nameLabel.setFont(new Font("Courier New", Font.BOLD, 11));
        cardPanel.add(nameLabel, gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        JPanel nameSplitPanel = new JPanel(new GridBagLayout());
        nameSplitPanel.setOpaque(false);
        GridBagConstraints sgbc = new GridBagConstraints();
        sgbc.fill = GridBagConstraints.HORIZONTAL;
        sgbc.insets = new Insets(0, 0, 0, 0);

        sgbc.gridx = 0; sgbc.weightx = 0.5;
        lastNameField = new JTextField();
        lastNameField.setFont(new Font("Courier New", Font.BOLD, 12));
        lastNameField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                autoSaveActiveCard();
            }
        });
        nameSplitPanel.add(lastNameField, sgbc);

        sgbc.gridx = 1; sgbc.weightx = 0.0;
        sgbc.insets = new Insets(0, 5, 0, 5);
        JLabel commaLabel = new JLabel(",");
        commaLabel.setFont(new Font("Courier New", Font.BOLD, 12));
        nameSplitPanel.add(commaLabel, sgbc);

        sgbc.gridx = 2; sgbc.weightx = 0.5;
        sgbc.insets = new Insets(0, 0, 0, 0);
        firstNameField = new JTextField();
        firstNameField.setFont(new Font("Courier New", Font.BOLD, 12));
        firstNameField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                autoSaveActiveCard();
            }
        });
        nameSplitPanel.add(firstNameField, sgbc);

        cardPanel.add(nameSplitPanel, gbc);

        // 2. Email Input Block
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.0;
        JLabel emailLabel = new JLabel("eMail:");
        emailLabel.setFont(new Font("Courier New", Font.BOLD, 11));
        cardPanel.add(emailLabel, gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        emailField = new JTextField();
        emailField.setFont(new Font("Courier New", Font.PLAIN, 12));
        emailField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                autoSaveActiveCard();
            }
        });
        cardPanel.add(emailField, gbc);

        // 3. Address Input Block
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0.0; gbc.anchor = GridBagConstraints.NORTHWEST;
        JLabel addrLabel = new JLabel("Adresse:");
        addrLabel.setFont(new Font("Courier New", Font.BOLD, 11));
        cardPanel.add(addrLabel, gbc);

        gbc.gridx = 1; gbc.weightx = 1.0; gbc.fill = GridBagConstraints.BOTH; gbc.weighty = 0.3;
        addressArea = new JTextArea(3, 20);
        addressArea.setFont(new Font("Courier New", Font.PLAIN, 12));
        addressArea.setLineWrap(true);
        addressArea.setWrapStyleWord(true);
        addressArea.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                autoSaveActiveCard();
            }
        });
        JScrollPane addrScroll = new JScrollPane(addressArea);
        cardPanel.add(addrScroll, gbc);

        // 4. Six Phone Fields (with dynamic JComboBox type dropdowns)
        gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weighty = 0.0;
        JTextField[] phoneInputs = new JTextField[6];
        JComboBox<String>[] phoneCombos = new JComboBox[6];
        String[] phoneChoices = {"Privat", "Beruflich", "Handy", "Fax", "Tel", "Privat 2", "Pager"};

        for (int i = 0; i < 6; i++) {
            gbc.gridy = 3 + i;
            gbc.gridx = 0; gbc.weightx = 0.0;
            
            phoneCombos[i] = new JComboBox<>(phoneChoices);
            phoneCombos[i].setFont(new Font("Courier New", Font.BOLD, 11));
            phoneCombos[i].setSelectedIndex(i < phoneChoices.length ? i : 0);
            phoneCombos[i].setFocusable(false);
            phoneCombos[i].setBackground(new Color(255, 255, 240)); // Vintage Cream
            phoneCombos[i].addActionListener(e -> autoSaveActiveCard());
            cardPanel.add(phoneCombos[i], gbc);

            gbc.gridx = 1; gbc.weightx = 1.0;
            phoneInputs[i] = new JTextField();
            phoneInputs[i].setFont(new Font("Courier New", Font.PLAIN, 12));
            phoneInputs[i].addFocusListener(new java.awt.event.FocusAdapter() {
                @Override
                public void focusLost(java.awt.event.FocusEvent e) {
                    autoSaveActiveCard();
                }
            });
            cardPanel.add(phoneInputs[i], gbc);
        }

        phone1Field = phoneInputs[0];
        phone2Field = phoneInputs[1];
        phone3Field = phoneInputs[2];
        phone4Field = phoneInputs[3];
        phone5Field = phoneInputs[4];
        phone6Field = phoneInputs[5];

        phone1TypeCombo = phoneCombos[0];
        phone2TypeCombo = phoneCombos[1];
        phone3TypeCombo = phoneCombos[2];
        phone4TypeCombo = phoneCombos[3];
        phone5TypeCombo = phoneCombos[4];
        phone6TypeCombo = phoneCombos[5];

        leftContainer.add(cardPanel, BorderLayout.CENTER);

        // --- MIDDLE AREA: Vertical Action Icons Toolbar ---
        JPanel toolbarPanel = new JPanel(new GridLayout(8, 1, 4, 4));
        toolbarPanel.setBorder(BorderFactory.createEmptyBorder(10, 5, 10, 5));

        btnBack = createIconButton("zurück", new ConeUpIcon());
        btnNext = createIconButton("vor", new ConeDownIcon());
        JButton btnNew = createIconButton("Neu", new FolderDocIcon());
        JButton btnNotes = createIconButton("Notizen", new NotepadPencilIcon());
        JButton btnMail = createIconButton("eMail", new EnvelopeMailIcon());
        JButton btnSearch = createIconButton("Suchen", new SearchHomeIcon());
        JButton btnCal = createIconButton("Kalender", new CalendarIcon());
        btnCal.setEnabled(false);
        JButton btnDial = createIconButton("Wählen", new PhoneCallIcon());

        btnBack.addActionListener(e -> navigateFiltered(-1));
        btnNext.addActionListener(e -> navigateFiltered(1));
        btnNew.addActionListener(e -> newCard());
        btnNotes.addActionListener(e -> editNotes());
        btnMail.addActionListener(e -> triggerMailAction());
        btnSearch.addActionListener(e -> searchContacts());
        btnDial.addActionListener(e -> dialPhoneNumber());

        toolbarPanel.add(btnBack);
        toolbarPanel.add(btnNext);
        toolbarPanel.add(btnNew);
        toolbarPanel.add(btnNotes);
        toolbarPanel.add(btnMail);
        toolbarPanel.add(btnSearch);
        toolbarPanel.add(btnCal);
        toolbarPanel.add(btnDial);

        // --- RIGHT AREA: Contacts List ---
        listPanel = new JPanel(new BorderLayout());
        listPanel.setBorder(BorderFactory.createEmptyBorder(10, 5, 10, 10));

        listModel = new DefaultListModel<>();
        namesList = new JList<>(listModel);
        namesList.setFont(new Font("Courier New", Font.BOLD, 12));
        namesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        namesList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int selIdx = namesList.getSelectedIndex();
                if (selIdx >= 0 && selIdx < filteredContacts.size()) {
                    Contact c = filteredContacts.get(selIdx);
                    int targetIdx = contacts.indexOf(c);
                    if (targetIdx != currentIndex) {
                        autoSaveActiveCard(c); // Target the newly selected contact!
                        currentIndex = targetIdx;
                        displayContact(currentIndex);
                        updateNavigationButtonsState();
                    }
                }
            }
        });

        JScrollPane listScroll = new JScrollPane(namesList);
        listPanel.add(listScroll, BorderLayout.CENTER);

        // --- BOTTOM STATUS ---
        statusLabel = new JLabel("Ready");
        statusLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(3, 8, 3, 8)
        ));
        statusLabel.setFont(new Font("Courier New", Font.PLAIN, 10));

        // --- Assemble Window layout ---
        JPanel mainContent = new JPanel(new BorderLayout());
        mainContent.add(leftContainer, BorderLayout.CENTER);
        mainContent.add(toolbarPanel, BorderLayout.EAST);
        
        // Wrap right list panel and toolbar in a right side composite panel
        JPanel rightSidePanel = new JPanel(new BorderLayout());
        rightSidePanel.add(toolbarPanel, BorderLayout.WEST);
        rightSidePanel.add(listPanel, BorderLayout.CENTER);
        
        mainContent.add(rightSidePanel, BorderLayout.EAST);
        mainContent.add(statusLabel, BorderLayout.SOUTH);

        setContentPane(mainContent);

        // Local Menu Bar
        JMenuBar menuBar = new JMenuBar();
        JMenu cardMenu = new JMenu("Datei");
        
        JMenuItem mNew = new JMenuItem("Neu");
        mNew.addActionListener(e -> newCard());
        
        JMenuItem mSave = new JMenuItem("Speichern");
        mSave.addActionListener(e -> saveCurrentCard());
        
        JMenuItem mSaveAs = new JMenuItem("Speichern unter...");
        mSaveAs.addActionListener(e -> saveAsFile());
        
        JMenuItem mDelete = new JMenuItem("Löschen");
        mDelete.addActionListener(e -> deleteCurrentCard());
        
        JMenuItem mImport = new JMenuItem("Importieren (CSV)...");
        mImport.addActionListener(e -> importCSV());
        
        JMenuItem mExportCSV = new JMenuItem("Exportieren (CSV)...");
        mExportCSV.addActionListener(e -> exportCSV());
        
        JMenuItem mExportMerge = new JMenuItem("Exportieren (Seriendruck)...");
        mExportMerge.addActionListener(e -> exportMailMerge());

        JMenuItem mPrint = new JMenuItem("Drucken...");
        mPrint.addActionListener(e -> printContacts());
        
        cardMenu.add(mNew);
        cardMenu.add(mSave);
        cardMenu.add(mSaveAs);
        cardMenu.add(mDelete);
        cardMenu.addSeparator();
        cardMenu.add(mImport);
        cardMenu.add(mExportCSV);
        cardMenu.add(mExportMerge);
        cardMenu.addSeparator();
        cardMenu.add(mPrint);

        JMenu viewMenu = new JMenu("Ansicht");
        JMenuItem mSearch = new JMenuItem("Suchen...");
        mSearch.addActionListener(e -> searchContacts());
        JMenuItem mClearSearch = new JMenuItem("Suche zurücksetzen");
        mClearSearch.addActionListener(e -> {
            searchQuery = null;
            highlightActiveTab();
            updateContactsList();
            statusLabel.setText("Suche zurückgesetzt. Zeige alle an.");
        });
        JMenuItem mSort = new JMenuItem("Alphabetisch sortieren");
        mSort.addActionListener(e -> sortCards());
        viewMenu.add(mSearch);
        viewMenu.add(mClearSearch);
        viewMenu.add(mSort);

        menuBar.add(cardMenu);
        menuBar.add(viewMenu);
        setJMenuBar(menuBar);

        // Auto-save when frame is closed
        addInternalFrameListener(new javax.swing.event.InternalFrameAdapter() {
            @Override
            public void internalFrameClosing(javax.swing.event.InternalFrameEvent e) {
                autoSaveActiveCard();
            }
        });

        // Initial tab highlight and loads
        highlightActiveTab();
        loadContacts();
    }

    private JButton createIconButton(String text, Icon icon) {
        JButton button = new JButton(text, icon);
        button.setFont(new Font("Courier New", Font.BOLD, 10));
        button.setHorizontalTextPosition(SwingConstants.CENTER);
        button.setVerticalTextPosition(SwingConstants.BOTTOM);
        button.setPreferredSize(new Dimension(80, 50));
        button.setFocusable(false);
        button.setBackground(new Color(212, 208, 200));
        button.setBorder(BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED));
        return button;
    }

    private void highlightActiveTab() {
        for (JButton btn : tabButtons) {
            if (btn != null) {
                btn.repaint();
            }
        }
    }

    private void loadContacts() {
        contacts.clear();
        if (contactsFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(contactsFile))) {
                String line;
                Contact contact = null;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.equals("[Card]")) {
                        contact = new Contact();
                        contacts.add(contact);
                    } else if (contact != null && line.startsWith("Name:")) {
                        contact.setName(line.substring(5).trim());
                    } else if (contact != null && line.startsWith("Address:")) {
                        contact.setAddress(line.substring(8).trim().replace("\\n", "\n"));
                    } else if (contact != null && line.startsWith("Phone1Type:")) {
                        contact.setPhone1Type(line.substring(11).trim());
                    } else if (contact != null && line.startsWith("Phone1:")) {
                        contact.setPhone1(line.substring(7).trim());
                    } else if (contact != null && line.startsWith("Phone2Type:")) {
                        contact.setPhone2Type(line.substring(11).trim());
                    } else if (contact != null && line.startsWith("Phone2:")) {
                        contact.setPhone2(line.substring(7).trim());
                    } else if (contact != null && line.startsWith("Phone3Type:")) {
                        contact.setPhone3Type(line.substring(11).trim());
                    } else if (contact != null && line.startsWith("Phone3:")) {
                        contact.setPhone3(line.substring(7).trim());
                    } else if (contact != null && line.startsWith("Phone4Type:")) {
                        contact.setPhone4Type(line.substring(11).trim());
                    } else if (contact != null && line.startsWith("Phone4:")) {
                        contact.setPhone4(line.substring(7).trim());
                    } else if (contact != null && line.startsWith("Phone5Type:")) {
                        contact.setPhone5Type(line.substring(11).trim());
                    } else if (contact != null && line.startsWith("Phone5:")) {
                        contact.setPhone5(line.substring(7).trim());
                    } else if (contact != null && line.startsWith("Phone6Type:")) {
                        contact.setPhone6Type(line.substring(11).trim());
                    } else if (contact != null && line.startsWith("Phone6:")) {
                        contact.setPhone6(line.substring(7).trim());
                    } else if (contact != null && line.startsWith("Phone:")) {
                        // Older singular phone compatibility
                        contact.setPhone1(line.substring(6).trim());
                    } else if (contact != null && line.startsWith("Email:")) {
                        contact.setEmail(line.substring(6).trim());
                    } else if (contact != null && line.startsWith("Notes:")) {
                        contact.setNotes(line.substring(6).trim().replace("\\n", "\n"));
                    }
                }
                statusLabel.setText("Geladen: " + contacts.size() + " Kontakte aus " + contactsFile.getName());
            } catch (IOException e) {
                statusLabel.setText("Fehler beim Laden: " + e.getMessage());
            }
        }

        // Write sample files if empty
        if (contacts.isEmpty()) {
            contacts.add(new Contact("Abbott, Dan", "123 Main St, Boston, MA", "555-1212", "", "", "", "", "", "dan@abbott.org", "Retro systems enthusiast."));
            contacts.add(new Contact("Geoworks, Blueway", "456 Silicon Rd, San Jose, CA", "555-2000", "", "", "", "", "", "info@bluewaysw.com", "PC/GEOS maintainers."));
            contacts.add(new Contact("Smith, John", "789 Vintage Way, Portland, OR", "555-9000", "", "", "", "", "", "john@smith.net", "EuroWorks programmer."));
            saveContactsToFile();
            statusLabel.setText("Beispieldaten erstellt in " + contactsFile.getName());
        }

        sortContacts();
        updateContactsList();
        
        if (!filteredContacts.isEmpty()) {
            currentIndex = contacts.indexOf(filteredContacts.get(0));
            displayContact(currentIndex);
        } else if (!contacts.isEmpty()) {
            currentIndex = 0;
            displayContact(currentIndex);
        } else {
            currentIndex = -1;
            displayContact(-1);
        }
        updateNavigationButtonsState();
    }

    private void saveContactsToFile() {
        try (PrintWriter writer = new PrintWriter(new FileWriter(contactsFile))) {
            for (Contact c : contacts) {
                writer.println("[Card]");
                writer.println("Name: " + c.getName());
                writer.println("Address: " + c.getAddress().replace("\n", "\\n"));
                
                writer.println("Phone1: " + c.getPhone1());
                writer.println("Phone1Type: " + c.getPhone1Type());
                
                writer.println("Phone2: " + c.getPhone2());
                writer.println("Phone2Type: " + c.getPhone2Type());
                
                writer.println("Phone3: " + c.getPhone3());
                writer.println("Phone3Type: " + c.getPhone3Type());
                
                writer.println("Phone4: " + c.getPhone4());
                writer.println("Phone4Type: " + c.getPhone4Type());
                
                writer.println("Phone5: " + c.getPhone5());
                writer.println("Phone5Type: " + c.getPhone5Type());
                
                writer.println("Phone6: " + c.getPhone6());
                writer.println("Phone6Type: " + c.getPhone6Type());
                
                writer.println("Email: " + c.getEmail());
                writer.println("Notes: " + c.getNotes().replace("\n", "\\n"));
                writer.println("---");
            }
            statusLabel.setText("Gespeichert in " + contactsFile.getName());
        } catch (IOException e) {
            statusLabel.setText("Fehler beim Speichern: " + e.getMessage());
        }
    }

    private void displayContact(int index) {
        isDisplaying = true;
        try {
            if (index >= 0 && index < contacts.size()) {
                Contact c = contacts.get(index);
                lastNameField.setText(c.getLastName());
                firstNameField.setText(c.getFirstName());
                emailField.setText(c.getEmail());
                addressArea.setText(c.getAddress());
                phone1Field.setText(c.getPhone1());
                phone2Field.setText(c.getPhone2());
                phone3Field.setText(c.getPhone3());
                phone4Field.setText(c.getPhone4());
                phone5Field.setText(c.getPhone5());
                phone6Field.setText(c.getPhone6());

                phone1TypeCombo.setSelectedItem(c.getPhone1Type());
                phone2TypeCombo.setSelectedItem(c.getPhone2Type());
                phone3TypeCombo.setSelectedItem(c.getPhone3Type());
                phone4TypeCombo.setSelectedItem(c.getPhone4Type());
                phone5TypeCombo.setSelectedItem(c.getPhone5Type());
                phone6TypeCombo.setSelectedItem(c.getPhone6Type());
            } else {
                lastNameField.setText("");
                firstNameField.setText("");
                emailField.setText("");
                addressArea.setText("");
                phone1Field.setText("");
                phone2Field.setText("");
                phone3Field.setText("");
                phone4Field.setText("");
                phone5Field.setText("");
                phone6Field.setText("");

                phone1TypeCombo.setSelectedIndex(0);
                phone2TypeCombo.setSelectedIndex(1);
                phone3TypeCombo.setSelectedIndex(2);
                phone4TypeCombo.setSelectedIndex(3);
                phone5TypeCombo.setSelectedIndex(4);
                phone6TypeCombo.setSelectedIndex(5);
            }
        } finally {
            isDisplaying = false;
        }
    }

    private void updateContactsList() {
        listModel.clear();
        filteredContacts.clear();

        for (Contact c : contacts) {
            if (searchQuery != null) {
                if (c.getName().toLowerCase().contains(searchQuery)) {
                    filteredContacts.add(c);
                    listModel.addElement(c.getName());
                }
            } else {
                if (activeLetter == '*') {
                    filteredContacts.add(c);
                    listModel.addElement(c.getName());
                } else {
                    String name = c.getName();
                    if (!name.isEmpty() && Character.toUpperCase(name.charAt(0)) == activeLetter) {
                        filteredContacts.add(c);
                        listModel.addElement(c.getName());
                    }
                }
            }
        }

        // Selection mapping
        if (!filteredContacts.isEmpty()) {
            Contact active = getCurrentContact();
            int filterIdx = filteredContacts.indexOf(active);
            if (filterIdx >= 0) {
                namesList.setSelectedIndex(filterIdx);
            } else {
                namesList.setSelectedIndex(0);
                currentIndex = contacts.indexOf(filteredContacts.get(0));
                displayContact(currentIndex);
            }
        } else {
            namesList.clearSelection();
            displayContact(-1);
        }
        resizeRightColumn();
        updateNavigationButtonsState();
    }

    private Contact getCurrentContact() {
        if (currentIndex >= 0 && currentIndex < contacts.size()) {
            return contacts.get(currentIndex);
        }
        return null;
    }

    private void navigateFiltered(int offset) {
        if (filteredContacts.isEmpty()) return;
        int filterIdx = filteredContacts.indexOf(getCurrentContact());
        if (filterIdx == -1) {
            filterIdx = 0;
        } else {
            filterIdx += offset;
        }

        if (filterIdx < 0) {
            filterIdx = 0;
        } else if (filterIdx >= filteredContacts.size()) {
            filterIdx = filteredContacts.size() - 1;
        }

        Contact target = filteredContacts.get(filterIdx);
        autoSaveActiveCard(target); // Target the new card
        currentIndex = contacts.indexOf(target);
        displayContact(currentIndex);
        namesList.setSelectedIndex(filterIdx);
        updateNavigationButtonsState();
    }

    private void updateNavigationButtonsState() {
        if (btnBack != null && btnNext != null) {
            if (filteredContacts.isEmpty()) {
                btnBack.setEnabled(false);
                btnNext.setEnabled(false);
            } else {
                int filterIdx = filteredContacts.indexOf(getCurrentContact());
                if (filterIdx == -1) {
                    btnBack.setEnabled(false);
                    btnNext.setEnabled(false);
                } else {
                    btnBack.setEnabled(filterIdx > 0);
                    btnNext.setEnabled(filterIdx < filteredContacts.size() - 1);
                }
            }
        }
    }

    private void resizeRightColumn() {
        if (namesList == null || listPanel == null) return;
        FontMetrics fm = namesList.getFontMetrics(namesList.getFont());
        int maxWidth = 100; // Minimum default width
        for (int i = 0; i < listModel.size(); i++) {
            String name = listModel.getElementAt(i);
            if (name != null) {
                int w = fm.stringWidth(name);
                if (w > maxWidth) {
                    maxWidth = w;
                }
            }
        }
        // Account for scrollbar and borders padding
        int paddedWidth = maxWidth + 45;
        // Limit max width so the list doesn't grow excessively large
        if (paddedWidth > 250) {
            paddedWidth = 250;
        }
        listPanel.setPreferredSize(new Dimension(paddedWidth, listPanel.getHeight()));
        listPanel.revalidate();
    }

    private void autoSaveActiveCard() {
        autoSaveActiveCard(getCurrentContact());
    }

    private void autoSaveActiveCard(Contact targetToSelect) {
        if (isDisplaying) return;
        String lastName = lastNameField.getText().trim();
        String firstName = firstNameField.getText().trim();
        if (lastName.isEmpty() && firstName.isEmpty()) return; // Skip if blank

        Contact c = getCurrentContact();
        boolean isNew = (c == null);
        if (isNew) {
            c = new Contact();
            contacts.add(c);
        }

        c.setLastName(lastName);
        c.setFirstName(firstName);
        c.setEmail(emailField.getText().trim());
        c.setAddress(addressArea.getText());
        c.setPhone1(phone1Field.getText().trim());
        c.setPhone2(phone2Field.getText().trim());
        c.setPhone3(phone3Field.getText().trim());
        c.setPhone4(phone4Field.getText().trim());
        c.setPhone5(phone5Field.getText().trim());
        c.setPhone6(phone6Field.getText().trim());

        if (phone1TypeCombo != null) c.setPhone1Type((String) phone1TypeCombo.getSelectedItem());
        if (phone2TypeCombo != null) c.setPhone2Type((String) phone2TypeCombo.getSelectedItem());
        if (phone3TypeCombo != null) c.setPhone3Type((String) phone3TypeCombo.getSelectedItem());
        if (phone4TypeCombo != null) c.setPhone4Type((String) phone4TypeCombo.getSelectedItem());
        if (phone5TypeCombo != null) c.setPhone5Type((String) phone5TypeCombo.getSelectedItem());
        if (phone6TypeCombo != null) c.setPhone6Type((String) phone6TypeCombo.getSelectedItem());

        sortContacts();
        if (isNew) {
            currentIndex = contacts.indexOf(c);
        }
        saveContactsToFile();

        // Rebuild list and keep selection
        isDisplaying = true;
        try {
            listModel.clear();
            filteredContacts.clear();
            for (Contact contact : contacts) {
                if (searchQuery != null) {
                    if (contact.getName().toLowerCase().contains(searchQuery)) {
                        filteredContacts.add(contact);
                        listModel.addElement(contact.getName());
                    }
                } else {
                    if (activeLetter == '*') {
                        filteredContacts.add(contact);
                        listModel.addElement(contact.getName());
                    } else {
                        String cName = contact.getName();
                        if (!cName.isEmpty() && Character.toUpperCase(cName.charAt(0)) == activeLetter) {
                            filteredContacts.add(contact);
                            listModel.addElement(contact.getName());
                        }
                    }
                }
            }
            if (targetToSelect != null && !filteredContacts.isEmpty()) {
                int newIdx = filteredContacts.indexOf(targetToSelect);
                if (newIdx >= 0) {
                    namesList.setSelectedIndex(newIdx);
                }
            }
        } finally {
            isDisplaying = false;
        }
        
        statusLabel.setText("Kontakt automatisch gespeichert: " + c.getName());
    }

    private void newCard() {
        autoSaveActiveCard();
        currentIndex = -1;
        displayContact(-1);
        namesList.clearSelection();
        lastNameField.requestFocus();
        statusLabel.setText("Erstelle neuen Kontakt...");
        updateNavigationButtonsState();
    }

    private void saveCurrentCard() {
        String lastName = lastNameField.getText().trim();
        String firstName = firstNameField.getText().trim();
        if (lastName.isEmpty() && firstName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Nachname oder Vorname darf nicht leer sein.", "Validierungsfehler", JOptionPane.ERROR_MESSAGE);
            return;
        }

        Contact c = getCurrentContact();
        boolean isNew = (c == null);
        if (isNew) {
            c = new Contact();
            contacts.add(c);
        }

        c.setLastName(lastName);
        c.setFirstName(firstName);
        c.setEmail(emailField.getText().trim());
        c.setAddress(addressArea.getText());
        c.setPhone1(phone1Field.getText().trim());
        c.setPhone2(phone2Field.getText().trim());
        c.setPhone3(phone3Field.getText().trim());
        c.setPhone4(phone4Field.getText().trim());
        c.setPhone5(phone5Field.getText().trim());
        c.setPhone6(phone6Field.getText().trim());

        if (phone1TypeCombo != null) c.setPhone1Type((String) phone1TypeCombo.getSelectedItem());
        if (phone2TypeCombo != null) c.setPhone2Type((String) phone2TypeCombo.getSelectedItem());
        if (phone3TypeCombo != null) c.setPhone3Type((String) phone3TypeCombo.getSelectedItem());
        if (phone4TypeCombo != null) c.setPhone4Type((String) phone4TypeCombo.getSelectedItem());
        if (phone5TypeCombo != null) c.setPhone5Type((String) phone5TypeCombo.getSelectedItem());
        if (phone6TypeCombo != null) c.setPhone6Type((String) phone6TypeCombo.getSelectedItem());

        sortContacts();
        currentIndex = contacts.indexOf(c);
        saveContactsToFile();

        // Refresh filter indices and GUI elements
        updateContactsList();
        statusLabel.setText("Kontakt gespeichert: " + c.getName());
    }

    private void deleteCurrentCard() {
        Contact c = getCurrentContact();
        if (c != null) {
            String name = c.getName();
            contacts.remove(c);
            saveContactsToFile();
            statusLabel.setText("Kontakt gelöscht: " + name);
            
            // Adjust indices
            updateContactsList();
            if (filteredContacts.isEmpty()) {
                if (contacts.isEmpty()) {
                    currentIndex = -1;
                } else {
                    currentIndex = 0;
                }
            } else {
                currentIndex = contacts.indexOf(filteredContacts.get(0));
            }
            displayContact(currentIndex);
        } else {
            statusLabel.setText("Kein Kontakt zum Löschen gewählt.");
        }
    }

    private void editNotes() {
        Contact c = getCurrentContact();
        if (c == null) {
            JOptionPane.showMessageDialog(this, "Kein Kontakt geladen.", "Information", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JTextArea textArea = new JTextArea(10, 30);
        textArea.setFont(new Font("Courier New", Font.PLAIN, 12));
        textArea.setText(c.getNotes());
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        JScrollPane scroll = new JScrollPane(textArea);

        int option = JOptionPane.showConfirmDialog(this, scroll, "Notizen bearbeiten: " + c.getName(),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (option == JOptionPane.OK_OPTION) {
            c.setNotes(textArea.getText());
            saveContactsToFile();
            statusLabel.setText("Notizen aktualisiert für " + c.getName());
        }
    }

    private void triggerMailAction() {
        Contact c = getCurrentContact();
        if (c == null || c.getEmail().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Keine E-Mail-Adresse hinterlegt.", "E-Mail", JOptionPane.WARNING_MESSAGE);
            return;
        }
        JOptionPane.showMessageDialog(this, "Sende E-Mail an: " + c.getEmail() + "\n(Simuliert)", "E-Mail senden", JOptionPane.INFORMATION_MESSAGE);
    }

    private void searchContacts() {
        String query = JOptionPane.showInputDialog(this, "Suche nach Name:", "Kontaktsuche", JOptionPane.QUESTION_MESSAGE);
        if (query != null) {
            String cleanQuery = query.trim().toLowerCase();
            if (cleanQuery.isEmpty()) {
                searchQuery = null;
                highlightActiveTab();
                updateContactsList();
                statusLabel.setText("Suche zurückgesetzt.");
            } else {
                searchQuery = cleanQuery;
                // Repaint all tabs to reflect the search-filter state
                highlightActiveTab();
                updateContactsList();
                if (filteredContacts.isEmpty()) {
                    statusLabel.setText("Keine Treffer für: \"" + query + "\"");
                    Toolkit.getDefaultToolkit().beep();
                } else {
                    statusLabel.setText("Suche: \"" + query + "\" (" + filteredContacts.size() + " Treffer). Tab klicken zum Zurücksetzen.");
                }
            }
        }
    }

    private void dialPhoneNumber() {
        Contact c = getCurrentContact();
        if (c == null) {
            statusLabel.setText("Kein Kontakt zum Wählen.");
            return;
        }

        List<String> numbers = new ArrayList<>();
        if (!c.getPhone1().isEmpty()) numbers.add("Privat: " + c.getPhone1());
        if (!c.getPhone2().isEmpty()) numbers.add("Beruflich: " + c.getPhone2());
        if (!c.getPhone3().isEmpty()) numbers.add("Handy: " + c.getPhone3());
        if (!c.getPhone5().isEmpty()) numbers.add("Tel: " + c.getPhone5());

        if (numbers.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Keine Telefonnummern hinterlegt.", "Wählen", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String selected = numbers.get(0);
        if (numbers.size() > 1) {
            selected = (String) JOptionPane.showInputDialog(this, "Wähle Nummer:", "Telefon",
                    JOptionPane.QUESTION_MESSAGE, null, numbers.toArray(), numbers.get(0));
        }

        if (selected != null) {
            String nr = selected.substring(selected.indexOf(":") + 1).trim();
            showDialerDialog(nr);
        }
    }

    private void showDialerDialog(String number) {
        JDialog dialDialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "Wählen...", true);
        dialDialog.setSize(300, 130);
        dialDialog.setLocationRelativeTo(this);

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JLabel dialLabel = new JLabel("Wähle: " + number, SwingConstants.CENTER);
        dialLabel.setFont(new Font("Courier New", Font.BOLD, 13));
        panel.add(dialLabel, BorderLayout.CENTER);

        JProgressBar progress = new JProgressBar(0, 100);
        progress.setIndeterminate(true);
        panel.add(progress, BorderLayout.SOUTH);

        dialDialog.setContentPane(panel);

        // Auto close timer simulation
        Timer timer = new Timer(3000, e -> dialDialog.dispose());
        timer.setRepeats(false);
        timer.start();

        dialDialog.setVisible(true);
        statusLabel.setText("Verbindung hergestellt mit: " + number);
    }

    private void saveAsFile() {
        JFileChooser fileChooser = new JFileChooser(System.getProperty("user.home"));
        fileChooser.setDialogTitle("Adressbuch speichern unter...");
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("EuroDex Files (*.eurodex)", "eurodex"));
        int userSelection = fileChooser.showSaveDialog(this);
        if (userSelection == JFileChooser.APPROVE_OPTION) {
            File fileToSave = fileChooser.getSelectedFile();
            if (!fileToSave.getName().toLowerCase().endsWith(".eurodex")) {
                fileToSave = new File(fileToSave.getParentFile(), fileToSave.getName() + ".eurodex");
            }
            contactsFile = fileToSave;
            saveContactsToFile();
        }
    }

    private void jumpToLetter(char letter) {
        char upperLetter = Character.toUpperCase(letter);
        for (int i = 0; i < contacts.size(); i++) {
            String name = contacts.get(i).getName();
            if (!name.isEmpty() && Character.toUpperCase(name.charAt(0)) == upperLetter) {
                currentIndex = i;
                activeLetter = upperLetter;
                highlightActiveTab();
                updateContactsList();
                return;
            }
        }
        statusLabel.setText("Keine Einträge für " + upperLetter);
        Toolkit.getDefaultToolkit().beep();
    }

    private void sortCards() {
        sortContacts();
        updateContactsList();
        if (!contacts.isEmpty()) {
            currentIndex = 0;
            displayContact(currentIndex);
            statusLabel.setText("Kontakte alphabetisch sortiert.");
        }
    }

    private void sortContacts() {
        Collections.sort(contacts, Comparator.comparing(Contact::getName, String.CASE_INSENSITIVE_ORDER));
    }

    // --- Vector Icon Painters ---

    private static class ConeUpIcon implements Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int[] xp = {x + 12, x + 4, x + 20};
            int[] yp = {y + 4, y + 20, y + 20};
            GradientPaint gp = new GradientPaint(x + 4, y + 20, new Color(150, 180, 255), x + 12, y + 4, new Color(0, 50, 200));
            g2.setPaint(gp);
            g2.fillPolygon(xp, yp, 3);
            g2.setColor(new Color(0, 0, 100));
            g2.drawPolygon(xp, yp, 3);
            g2.dispose();
        }
        @Override public int getIconWidth() { return 24; }
        @Override public int getIconHeight() { return 24; }
    }

    private static class ConeDownIcon implements Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int[] xp = {x + 12, x + 4, x + 20};
            int[] yp = {y + 20, y + 4, y + 4};
            GradientPaint gp = new GradientPaint(x + 4, y + 4, new Color(150, 180, 255), x + 12, y + 20, new Color(0, 50, 200));
            g2.setPaint(gp);
            g2.fillPolygon(xp, yp, 3);
            g2.setColor(new Color(0, 0, 100));
            g2.drawPolygon(xp, yp, 3);
            g2.dispose();
        }
        @Override public int getIconWidth() { return 24; }
        @Override public int getIconHeight() { return 24; }
    }

    private static class FolderDocIcon implements Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            g.setColor(new Color(230, 200, 120));
            g.fillRoundRect(x + 2, y + 6, 20, 14, 2, 2);
            g.setColor(new Color(150, 100, 50));
            g.drawRoundRect(x + 2, y + 6, 20, 14, 2, 2);
            
            g.setColor(Color.WHITE);
            g.fillRect(x + 6, y + 2, 12, 14);
            g.setColor(Color.BLACK);
            g.drawRect(x + 6, y + 2, 12, 14);
            g.drawLine(x + 9, y + 6, x + 15, y + 6);
            g.drawLine(x + 9, y + 10, x + 15, y + 10);
        }
        @Override public int getIconWidth() { return 24; }
        @Override public int getIconHeight() { return 24; }
    }

    private static class NotepadPencilIcon implements Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            g.setColor(new Color(240, 240, 240));
            g.fillRect(x + 4, y + 2, 14, 18);
            g.setColor(Color.DARK_GRAY);
            g.drawRect(x + 4, y + 2, 14, 18);
            g.setColor(Color.BLACK);
            for (int i = 4; i <= 16; i += 4) {
                g.drawArc(x + i, y, 3, 4, 0, 180);
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.rotate(Math.toRadians(-45), x + 16, y + 12);
            g2.setColor(Color.YELLOW);
            g2.fillRect(x + 14, y + 6, 4, 12);
            g2.setColor(new Color(255, 200, 150));
            g2.fillRect(x + 14, y + 18, 4, 2);
            g2.setColor(Color.BLACK);
            g2.fillRect(x + 15, y + 20, 2, 2);
            g2.dispose();
        }
        @Override public int getIconWidth() { return 24; }
        @Override public int getIconHeight() { return 24; }
    }

    private static class EnvelopeMailIcon implements Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            g.setColor(Color.WHITE);
            g.fillRect(x + 3, y + 5, 18, 12);
            g.setColor(Color.BLUE);
            g.drawRect(x + 3, y + 5, 18, 12);
            g.drawLine(x + 3, y + 5, x + 12, y + 11);
            g.drawLine(x + 21, y + 5, x + 12, y + 11);
        }
        @Override public int getIconWidth() { return 24; }
        @Override public int getIconHeight() { return 24; }
    }

    private static class SearchHomeIcon implements Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setStroke(new BasicStroke(3));
            g2.setColor(new Color(220, 180, 0));
            g2.drawOval(x + 4, y + 4, 11, 11);
            g2.setStroke(new BasicStroke(4));
            g2.setColor(Color.BLACK);
            g2.drawLine(x + 13, y + 13, x + 20, y + 20);
            g2.setStroke(new BasicStroke(1));
            g2.setColor(new Color(200, 230, 255));
            g2.fillOval(x + 5, y + 5, 9, 9);
            g2.dispose();
        }
        @Override public int getIconWidth() { return 24; }
        @Override public int getIconHeight() { return 24; }
    }

    private static class CalendarIcon implements Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            g.setColor(Color.LIGHT_GRAY);
            g.fillRect(x + 4, y + 4, 16, 16);
            g.setColor(Color.GRAY);
            g.drawRect(x + 4, y + 4, 16, 16);
            g.drawLine(x + 4, y + 8, x + 20, y + 8);
            g.drawLine(x + 8, y + 8, x + 8, y + 20);
            g.drawLine(x + 12, y + 8, x + 12, y + 20);
            g.drawLine(x + 16, y + 8, x + 16, y + 20);
        }
        @Override public int getIconWidth() { return 24; }
        @Override public int getIconHeight() { return 24; }
    }

    private static class PhoneCallIcon implements Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            g.setColor(Color.BLACK);
            g.fillRoundRect(x + 4, y + 12, 16, 6, 2, 2);
            g.fillOval(x + 2, y + 8, 6, 6);
            g.fillOval(x + 16, y + 8, 6, 6);
            g.fillRect(x + 8, y + 10, 8, 3);
        }
        @Override public int getIconWidth() { return 24; }
        @Override public int getIconHeight() { return 24; }
    }

    private void printContacts() {
        String[] options = {"Aktuelle Karte", "Alle Kontakte", "Abbrechen"};
        int selection = JOptionPane.showOptionDialog(this,
                "Was möchten Sie drucken?",
                "Druckoptionen (Print)",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]);

        if (selection == 0) {
            Contact c = getCurrentContact();
            if (c == null) {
                JOptionPane.showMessageDialog(this, "Kein Kontakt geladen.", "Fehler", JOptionPane.ERROR_MESSAGE);
                return;
            }
            printCurrentCard(c);
        } else if (selection == 1) {
            if (contacts.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Keine Kontakte zum Drucken vorhanden.", "Fehler", JOptionPane.ERROR_MESSAGE);
                return;
            }
            printAllContactsList();
        }
    }

    private void printCurrentCard(Contact c) {
        java.awt.print.PrinterJob job = java.awt.print.PrinterJob.getPrinterJob();
        job.setJobName("EuroDex - Karte: " + c.getName());

        job.setPrintable((graphics, pageFormat, pageIndex) -> {
            if (pageIndex > 0) {
                return java.awt.print.Printable.NO_SUCH_PAGE;
            }

            Graphics2D g2d = (Graphics2D) graphics;
            g2d.translate(pageFormat.getImageableX(), pageFormat.getImageableY());

            g2d.setFont(new Font("Courier New", Font.BOLD, 12));
            g2d.setColor(Color.BLACK);

            int cardW = 360; // ~5 inches at 72 dpi
            int cardH = 216; // ~3 inches at 72 dpi
            int startX = (int) (pageFormat.getImageableWidth() - cardW) / 2;
            int startY = 50;

            g2d.setColor(new Color(255, 255, 240));
            g2d.fillRect(startX, startY, cardW, cardH);
            
            g2d.setColor(Color.BLACK);
            g2d.drawRect(startX, startY, cardW, cardH);
            g2d.drawRect(startX + 2, startY + 2, cardW - 4, cardH - 4);

            g2d.setFont(new Font("Courier New", Font.BOLD, 14));
            g2d.drawString("EuroDex Adresskarte", startX + 15, startY + 25);
            g2d.drawLine(startX + 15, startY + 30, startX + cardW - 15, startY + 30);

            g2d.setFont(new Font("Courier New", Font.PLAIN, 10));
            int textY = startY + 45;
            g2d.drawString("Name:    " + c.getName(), startX + 20, textY); textY += 15;
            g2d.drawString("eMail:   " + c.getEmail(), startX + 20, textY); textY += 15;
            
            String[] addrLines = c.getAddress().split("\n");
            g2d.drawString("Adresse: " + (addrLines.length > 0 ? addrLines[0] : ""), startX + 20, textY); textY += 15;
            if (addrLines.length > 1) {
                g2d.drawString("         " + addrLines[1], startX + 20, textY); textY += 15;
            }

            if (!c.getPhone1().isEmpty()) {
                g2d.drawString(c.getPhone1Type() + ": " + c.getPhone1(), startX + 20, textY); textY += 15;
            }
            if (!c.getPhone2().isEmpty()) {
                g2d.drawString(c.getPhone2Type() + ": " + c.getPhone2(), startX + 20, textY); textY += 15;
            }
            if (!c.getPhone3().isEmpty()) {
                g2d.drawString(c.getPhone3Type() + ": " + c.getPhone3(), startX + 20, textY); textY += 15;
            }
            if (!c.getPhone4().isEmpty()) {
                g2d.drawString(c.getPhone4Type() + ": " + c.getPhone4(), startX + 20, textY); textY += 15;
            }

            return java.awt.print.Printable.PAGE_EXISTS;
        });

        if (job.printDialog()) {
            try {
                job.print();
                statusLabel.setText("Druckauftrag gesendet: " + c.getName());
            } catch (java.awt.print.PrinterException e) {
                JOptionPane.showMessageDialog(this, "Fehler beim Drucken: " + e.getMessage(), "Druckfehler", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void printAllContactsList() {
        java.awt.print.PrinterJob job = java.awt.print.PrinterJob.getPrinterJob();
        job.setJobName("EuroDex - Kontaktliste");

        int contactsPerPage = 25;

        job.setPrintable((graphics, pageFormat, pageIndex) -> {
            int startContact = pageIndex * contactsPerPage;
            if (startContact >= contacts.size()) {
                return java.awt.print.Printable.NO_SUCH_PAGE;
            }

            Graphics2D g2d = (Graphics2D) graphics;
            g2d.translate(pageFormat.getImageableX(), pageFormat.getImageableY());

            g2d.setFont(new Font("Courier New", Font.BOLD, 14));
            g2d.drawString("EuroDex Kontaktliste (Gesamtverzeichnis)", 30, 40);
            g2d.setFont(new Font("Courier New", Font.PLAIN, 8));
            g2d.drawString("Seite " + (pageIndex + 1), (int) pageFormat.getImageableWidth() - 70, 40);
            g2d.drawLine(30, 45, (int) pageFormat.getImageableWidth() - 30, 45);

            g2d.setFont(new Font("Courier New", Font.BOLD, 10));
            int y = 65;
            g2d.drawString("Name", 35, y);
            g2d.drawString("Primary Telefon", 220, y);
            g2d.drawString("eMail", 380, y);
            g2d.drawLine(30, y + 5, (int) pageFormat.getImageableWidth() - 30, y + 5);
            y += 20;

            g2d.setFont(new Font("Courier New", Font.PLAIN, 9));
            int endContact = Math.min(startContact + contactsPerPage, contacts.size());
            for (int i = startContact; i < endContact; i++) {
                Contact c = contacts.get(i);
                
                String name = c.getName();
                if (name.length() > 25) name = name.substring(0, 22) + "...";
                g2d.drawString(name, 35, y);
                
                String phone = c.getPhone1();
                String pType = c.getPhone1Type();
                if (phone.isEmpty() && !c.getPhone2().isEmpty()) { phone = c.getPhone2(); pType = c.getPhone2Type(); }
                if (phone.isEmpty() && !c.getPhone3().isEmpty()) { phone = c.getPhone3(); pType = c.getPhone3Type(); }
                
                String phoneLabelStr = phone.isEmpty() ? "" : (pType + ": " + phone);
                if (phoneLabelStr.length() > 22) phoneLabelStr = phoneLabelStr.substring(0, 19) + "...";
                g2d.drawString(phoneLabelStr, 220, y);
                
                String email = c.getEmail();
                if (email.length() > 30) email = email.substring(0, 27) + "...";
                g2d.drawString(email, 380, y);
                
                y += 18;
            }

            return java.awt.print.Printable.PAGE_EXISTS;
        });

        if (job.printDialog()) {
            try {
                job.print();
                statusLabel.setText("Gesamte Kontaktliste an Drucker gesendet.");
            } catch (java.awt.print.PrinterException e) {
                JOptionPane.showMessageDialog(this, "Fehler beim Drucken: " + e.getMessage(), "Druckfehler", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void importCSV() {
        JFileChooser chooser = new JFileChooser(System.getProperty("user.home"));
        chooser.setDialogTitle("CSV-Datei importieren");
        int res = chooser.showOpenDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            File selectedFile = chooser.getSelectedFile();
            try (BufferedReader reader = new BufferedReader(new FileReader(selectedFile))) {
                String headerLine = reader.readLine();
                if (headerLine == null) {
                    JOptionPane.showMessageDialog(this, "Die Datei ist leer.", "Fehler", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                
                String line;
                int importedCount = 0;
                while ((line = reader.readLine()) != null) {
                    String[] tokens = parseCSVLine(line);
                    if (tokens.length > 0) {
                        Contact c = new Contact();
                        c.setName(tokens[0]);
                        if (tokens.length > 1) c.setAddress(tokens[1].replace("\\n", "\n"));
                        
                        if (tokens.length > 2) c.setPhone1(tokens[2]);
                        if (tokens.length > 3) c.setPhone1Type(tokens[3]);
                        
                        if (tokens.length > 4) c.setPhone2(tokens[4]);
                        if (tokens.length > 5) c.setPhone2Type(tokens[5]);
                        
                        if (tokens.length > 6) c.setPhone3(tokens[6]);
                        if (tokens.length > 7) c.setPhone3Type(tokens[7]);
                        
                        if (tokens.length > 8) c.setPhone4(tokens[8]);
                        if (tokens.length > 9) c.setPhone4Type(tokens[9]);
                        
                        if (tokens.length > 10) c.setPhone5(tokens[10]);
                        if (tokens.length > 11) c.setPhone5Type(tokens[11]);
                        
                        if (tokens.length > 12) c.setPhone6(tokens[12]);
                        if (tokens.length > 13) c.setPhone6Type(tokens[13]);
                        
                        if (tokens.length > 14) c.setEmail(tokens[14]);
                        if (tokens.length > 15) c.setNotes(tokens[15].replace("\\n", "\n"));
                        
                        contacts.add(c);
                        importedCount++;
                    }
                }
                sortContacts();
                saveContactsToFile();
                updateContactsList();
                JOptionPane.showMessageDialog(this, importedCount + " Kontakte erfolgreich importiert.", "Erfolgreicher Import", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Fehler beim Importieren: " + e.getMessage(), "Importfehler", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private String[] parseCSVLine(String line) {
        List<String> list = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                list.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        list.add(sb.toString().trim());
        
        String[] res = new String[list.size()];
        for (int i = 0; i < list.size(); i++) {
            String s = list.get(i);
            if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
                s = s.substring(1, s.length() - 1);
            }
            res[i] = s;
        }
        return res;
    }

    private void exportCSV() {
        JFileChooser chooser = new JFileChooser(System.getProperty("user.home"));
        chooser.setDialogTitle("Exportieren als CSV");
        chooser.setSelectedFile(new File("contacts_export.csv"));
        int res = chooser.showSaveDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            File selectedFile = chooser.getSelectedFile();
            try (PrintWriter writer = new PrintWriter(new FileWriter(selectedFile))) {
                writer.println("Name,Address,Phone1,Phone1Type,Phone2,Phone2Type,Phone3,Phone3Type,Phone4,Phone4Type,Phone5,Phone5Type,Phone6,Phone6Type,Email,Notes");
                for (Contact c : contacts) {
                    writer.println(escapeCSV(c.getName()) + "," +
                            escapeCSV(c.getAddress().replace("\n", "\\n")) + "," +
                            escapeCSV(c.getPhone1()) + "," +
                            escapeCSV(c.getPhone1Type()) + "," +
                            escapeCSV(c.getPhone2()) + "," +
                            escapeCSV(c.getPhone2Type()) + "," +
                            escapeCSV(c.getPhone3()) + "," +
                            escapeCSV(c.getPhone3Type()) + "," +
                            escapeCSV(c.getPhone4()) + "," +
                            escapeCSV(c.getPhone4Type()) + "," +
                            escapeCSV(c.getPhone5()) + "," +
                            escapeCSV(c.getPhone5Type()) + "," +
                            escapeCSV(c.getPhone6()) + "," +
                            escapeCSV(c.getPhone6Type()) + "," +
                            escapeCSV(c.getEmail()) + "," +
                            escapeCSV(c.getNotes().replace("\n", "\\n")));
                }
                JOptionPane.showMessageDialog(this, "Kontakte erfolgreich exportiert.", "Erfolgreicher Export", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Fehler beim Exportieren: " + e.getMessage(), "Exportfehler", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private String escapeCSV(String value) {
        if (value == null) return "";
        String s = value.replace("\"", "\"\"");
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s + "\"";
        }
        return s;
    }

    private void exportMailMerge() {
        JFileChooser chooser = new JFileChooser(System.getProperty("user.home"));
        chooser.setDialogTitle("Exportieren als Seriendruck-Datei (Mail Merge)");
        chooser.setSelectedFile(new File("contacts_merge.txt"));
        int res = chooser.showSaveDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            File selectedFile = chooser.getSelectedFile();
            try (PrintWriter writer = new PrintWriter(new FileWriter(selectedFile))) {
                writer.println("Name\tAddress\tPhone1\tPhone1Type\tPhone2\tPhone2Type\tPhone3\tPhone3Type\tPhone4\tPhone4Type\tPhone5\tPhone5Type\tPhone6\tPhone6Type\tEmail\tNotes");
                for (Contact c : contacts) {
                    writer.println(c.getName() + "\t" +
                            c.getAddress().replace("\n", " ").replace("\t", " ") + "\t" +
                            c.getPhone1() + "\t" +
                            c.getPhone1Type() + "\t" +
                            c.getPhone2() + "\t" +
                            c.getPhone2Type() + "\t" +
                            c.getPhone3() + "\t" +
                            c.getPhone3Type() + "\t" +
                            c.getPhone4() + "\t" +
                            c.getPhone4Type() + "\t" +
                            c.getPhone5() + "\t" +
                            c.getPhone5Type() + "\t" +
                            c.getPhone6() + "\t" +
                            c.getPhone6Type() + "\t" +
                            c.getEmail() + "\t" +
                            c.getNotes().replace("\n", " ").replace("\t", " "));
                }
                JOptionPane.showMessageDialog(this, "Seriendruck-Datei erfolgreich exportiert.", "Erfolgreicher Export", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Fehler beim Exportieren: " + e.getMessage(), "Exportfehler", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}

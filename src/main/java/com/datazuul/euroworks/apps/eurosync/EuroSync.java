package com.datazuul.euroworks.apps.eurosync;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ItemListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import java.awt.event.KeyEvent;
import java.net.URL;
import javax.swing.JEditorPane;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;

import com.datazuul.euroworks.apps.EuroAppFrame;

/**
 * EuroSync: GUI for date synchronization between source and target,
 * matching the features and look of GRsync, modified to support multiple path
 * pairs.
 */
public class EuroSync extends EuroAppFrame {

    private static final Color RETRO_BG = new Color(212, 208, 200);

    // Data State
    private final List<SyncSession> sessionsList = new ArrayList<>();
    private SyncSession activeSession = null;
    private boolean isUpdatingUI = false;

    // UI Components - Top (Sessions selection)
    private JComboBox<SyncSession> comboSessions;
    private JButton btnAddSession;
    private JButton btnDeleteSession;

    // UI Components - Paths Table
    private JTable tablePathPairs;
    private DefaultTableModel tableModelPathPairs;
    private JButton btnAddPathPair;
    private JButton btnDeletePathPair;

    // UI Components - Tab 1 (Standard)
    private JCheckBox chkPreserveTimestamps;
    private JCheckBox chkPreservePermissions;
    private JCheckBox chkPreserveOwner;
    private JCheckBox chkPreserveGroup;
    private JCheckBox chkDeleteOnDestination;
    private JCheckBox chkDontLeaveFilesystem;
    private JCheckBox chkVerbose;
    private JCheckBox chkShowProgress;
    private JCheckBox chkIgnoreExisting;
    private JCheckBox chkSizeOnly;
    private JCheckBox chkSkipNewer;
    private JCheckBox chkWindowsMode;
    private JTextField txtNotes;

    // UI Components - Tab 2 (Advanced)
    private JCheckBox chkCompression;
    private JCheckBox chkKeepPartial;
    private JCheckBox chkCopySymlinks;
    private JCheckBox chkFollowSymlinks;
    private JCheckBox chkSparseFiles;
    private JTextField txtAdditionalOptions;

    // UI Components - Tab 3 (Extra)
    private JTextField txtPreCommand;
    private JCheckBox chkHaltOnPreFailure;
    private JTextField txtPostCommand;
    private JCheckBox chkSkipPostOnFailure;

    // Action Buttons
    private JButton btnCloseApp;
    private JButton btnStartSimulation;
    private JButton btnExecute;

    public EuroSync() {
        super("EuroSync (Dateisynchronisation)");
        setSize(680, 600);
        setMinimumSize(new Dimension(600, 520));

        // Load sessions from configuration
        sessionsList.addAll(SyncSessionStore.loadSessions());
        if (sessionsList.isEmpty()) {
            sessionsList.add(new SyncSession("default"));
        }
        activeSession = sessionsList.get(0);

        // Build GUI Layout
        JPanel mainPanel = new JPanel(new BorderLayout(5, 5));
        mainPanel.setBackground(RETRO_BG);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // 1. Top Panel (Sessions + Paths Table)
        mainPanel.add(buildTopPanel(), BorderLayout.NORTH);

        // 2. Tabbed Options Panel
        mainPanel.add(buildTabbedPane(), BorderLayout.CENTER);

        // 3. Bottom Actions Panel
        mainPanel.add(buildBottomPanel(), BorderLayout.SOUTH);

        setContentPane(mainPanel);

        // Load active session data into components
        populateUIFromActiveSession();

        // Bind control listeners to save updates to active session automatically
        bindControlListeners();

        // Set up blocking glass pane
        JPanel glass = new JPanel();
        glass.setOpaque(false);
        glass.addMouseListener(new java.awt.event.MouseAdapter() {});
        setGlassPane(glass);

        // Set up application menu bar
        setupMenuBar();
    }

    private void blockWindow() {
        getGlassPane().setVisible(true);
        setEnabled(false);
    }

    private void unblockWindow() {
        getGlassPane().setVisible(false);
        setEnabled(true);
        try {
            setSelected(true);
        } catch (Exception ex) {}
    }

    private JPanel buildTopPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(RETRO_BG);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Row 0: Sessions selection
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.0;
        JLabel lblSessions = new JLabel("Sitzungen:");
        lblSessions.setFont(lblSessions.getFont().deriveFont(Font.BOLD));
        panel.add(lblSessions, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        comboSessions = new JComboBox<>(sessionsList.toArray(new SyncSession[0]));
        comboSessions.addActionListener(e -> {
            if (!isUpdatingUI) {
                SyncSession selected = (SyncSession) comboSessions.getSelectedItem();
                if (selected != null) {
                    activeSession = selected;
                    populateUIFromActiveSession();
                }
            }
        });
        panel.add(comboSessions, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0.0;
        btnAddSession = new JButton("+ Hinzufügen");
        btnAddSession.addActionListener(e -> addNewSession());
        panel.add(btnAddSession, gbc);

        gbc.gridx = 3;
        btnDeleteSession = new JButton("Löschen");
        btnDeleteSession.addActionListener(e -> deleteCurrentSession());
        panel.add(btnDeleteSession, gbc);

        // Header Label: Quell- und Zielverzeichnisse
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 4;
        gbc.insets = new Insets(10, 4, 2, 4);
        JLabel lblPaths = new JLabel("Quell- und Zielverzeichnisse:");
        lblPaths.setFont(lblPaths.getFont().deriveFont(Font.BOLD));
        panel.add(lblPaths, gbc);

        // Row 2: Paths Table
        gbc.gridy = 2;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1.0;
        tableModelPathPairs = new DefaultTableModel(new Object[] { "Quelle (Source)", "Ziel (Target)" }, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // read-only in table, edit via dialog
            }
        };
        tablePathPairs = new JTable(tableModelPathPairs);
        tablePathPairs.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tablePathPairs.getTableHeader().setReorderingAllowed(false);
        JScrollPane scrollPane = new JScrollPane(tablePathPairs);
        scrollPane.setPreferredSize(new Dimension(0, 110));
        panel.add(scrollPane, gbc);

        // Row 3: Add / Delete Buttons for Path Pairs
        gbc.gridy = 3;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weighty = 0.0;
        gbc.insets = new Insets(4, 4, 4, 4);
        JPanel pathButtonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        pathButtonsPanel.setOpaque(false);

        btnAddPathPair = new JButton("+ Hinzufügen");
        btnAddPathPair.addActionListener(e -> addPathPair());
        pathButtonsPanel.add(btnAddPathPair);

        btnDeletePathPair = new JButton("🗑 Löschen");
        btnDeletePathPair.addActionListener(e -> deleteSelectedPathPair());
        pathButtonsPanel.add(btnDeletePathPair);

        panel.add(pathButtonsPanel, gbc);

        return panel;
    }

    private JTabbedPane buildTabbedPane() {
        JTabbedPane tabbedPane = new JTabbedPane();

        // --- Tab 1: Standardoptionen ---
        JPanel tabStandard = new JPanel(new BorderLayout(5, 5));
        tabStandard.setBackground(RETRO_BG);
        tabStandard.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel checksPanel = new JPanel(new GridLayout(0, 2, 8, 4));
        checksPanel.setBackground(RETRO_BG);

        chkPreserveTimestamps = new JCheckBox("Zeitstempel erhalten");
        chkPreservePermissions = new JCheckBox("Rechte erhalten");
        chkPreserveOwner = new JCheckBox("Besitzer erhalten");
        chkPreserveGroup = new JCheckBox("Gruppe erhalten");
        chkDeleteOnDestination = new JCheckBox("Im Zielverzeichnis löschen");
        chkDontLeaveFilesystem = new JCheckBox("Dateisystem nicht verlassen");
        chkVerbose = new JCheckBox("Ausführliche Meldungen");
        chkShowProgress = new JCheckBox("Fortschritt anzeigen");
        chkIgnoreExisting = new JCheckBox("Bestehende ignorieren");
        chkSizeOnly = new JCheckBox("Nur Dateigröße vergleichen");
        chkSkipNewer = new JCheckBox("Neuere überspringen");
        chkWindowsMode = new JCheckBox("Windows-Kompatibilitätsmodus");

        // Style checkboxes retro
        for (JCheckBox cb : new JCheckBox[] { chkPreserveTimestamps, chkPreservePermissions, chkPreserveOwner,
                chkPreserveGroup,
                chkDeleteOnDestination, chkDontLeaveFilesystem, chkVerbose, chkShowProgress, chkIgnoreExisting,
                chkSizeOnly,
                chkSkipNewer, chkWindowsMode }) {
            cb.setBackground(RETRO_BG);
        }

        // Set help tooltips
        chkPreserveTimestamps.setToolTipText("Kopiert die Änderungszeitpunkte der Quelldateien auf die Zieldateien. Dringend empfohlen für inkrementelle Backups.");
        chkPreservePermissions.setToolTipText("Übernimmt Lese-, Schreib- und Ausführungsrechte der Quelldateien auf das Ziel (erfordert kompatibles Dateisystem).");
        chkPreserveOwner.setToolTipText("Versucht, den Dateibesitzer auf dem Zielsystem beizubehalten (erfordert meist Admin-Rechte).");
        chkPreserveGroup.setToolTipText("Versucht, die Dateigruppe auf dem Zielsystem beizubehalten.");
        chkDeleteOnDestination.setToolTipText("Löscht Dateien im Zielverzeichnis, die in der Quelle nicht mehr vorhanden sind. Macht das Ziel zu einer exakten Kopie.");
        chkDontLeaveFilesystem.setToolTipText("Verhindert das Überschreiten von Dateisystemgrenzen (z. B. gemountete Partitionen/Netzlaufwerke).");
        chkVerbose.setToolTipText("Gibt detaillierte Informationen über jeden einzelnen Vergleich und Kopiervorgang im Logfenster aus.");
        chkShowProgress.setToolTipText("Zeigt den aktuellen Fortschritt und die geschätzte Restzeit während des Kopiervorgangs an.");
        chkIgnoreExisting.setToolTipText("Überspringt das Kopieren von Dateien, die im Ziel bereits existieren (unabhängig von Alter/Größe).");
        chkSizeOnly.setToolTipText("Vergleicht Dateien ausschließlich anhand ihrer Größe. Unterschiede im Änderungsdatum werden ignoriert.");
        chkSkipNewer.setToolTipText("Überspringt Dateien im Ziel, falls diese ein neuereres Änderungsdatum haben als die entsprechende Quelldatei.");
        chkWindowsMode.setToolTipText("Toleriert geringfügige Zeitunterschiede (bis zu 2s) zwischen FAT- und NTFS-Dateisystemen und überspringt Unix-Rechte-Fehler.");

        checksPanel.add(chkPreserveTimestamps);
        checksPanel.add(chkPreservePermissions);
        checksPanel.add(chkPreserveOwner);
        checksPanel.add(chkPreserveGroup);
        checksPanel.add(chkDeleteOnDestination);
        checksPanel.add(chkDontLeaveFilesystem);
        checksPanel.add(chkVerbose);
        checksPanel.add(chkShowProgress);
        checksPanel.add(chkIgnoreExisting);
        checksPanel.add(chkSizeOnly);
        checksPanel.add(chkSkipNewer);
        checksPanel.add(chkWindowsMode);

        tabStandard.add(checksPanel, BorderLayout.CENTER);

        JPanel notesPanel = new JPanel(new BorderLayout(5, 5));
        notesPanel.setBackground(RETRO_BG);
        notesPanel.add(new JLabel("Notes:"), BorderLayout.WEST);
        txtNotes = new JTextField();
        txtNotes.setToolTipText("Optionale Bemerkungen zu dieser Sitzung.");
        notesPanel.add(txtNotes, BorderLayout.CENTER);
        tabStandard.add(notesPanel, BorderLayout.SOUTH);

        tabbedPane.addTab("Standardoptionen", tabStandard);

        // --- Tab 2: Erweiterte Optionen ---
        JPanel tabAdvanced = new JPanel(new GridBagLayout());
        tabAdvanced.setBackground(RETRO_BG);
        tabAdvanced.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        chkCompression = new JCheckBox("Kompression");
        chkKeepPartial = new JCheckBox("Teilweise übertragenen Dateien behalten");
        chkCopySymlinks = new JCheckBox("Kopiere symbolische Links als Links");
        chkFollowSymlinks = new JCheckBox("Links folgen (in Dateien/Verzeichnisse auflösen)");
        chkSparseFiles = new JCheckBox("Sparsame Dateien");

        for (JCheckBox cb : new JCheckBox[] { chkCompression, chkKeepPartial, chkCopySymlinks, chkFollowSymlinks,
                chkSparseFiles }) {
            cb.setBackground(RETRO_BG);
        }

        chkCompression.setToolTipText("Komprimiert Daten während der Übertragung (nützlich bei langsamen Netzwerkverbindungen).");
        chkKeepPartial.setToolTipText("Behält teilweise übertragene Dateien bei einem Abbruch bei, um sie später fortzusetzen.");
        chkCopySymlinks.setToolTipText("Kopiert symbolische Links als Links/Verweise, statt die Zieldateien selbst zu kopieren.");
        chkFollowSymlinks.setToolTipText("Folgt symbolischen Links und kopiert die echten Dateien/Verzeichnisse, auf die sie verweisen.");
        chkSparseFiles.setToolTipText("Optimiert die Übertragung von Dateien mit großen leeren Datenblöcken (Sparse-Dateien), um Speicherplatz zu sparen.");

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        tabAdvanced.add(chkCompression, gbc);
        gbc.gridy = 1;
        tabAdvanced.add(chkKeepPartial, gbc);
        gbc.gridy = 2;
        tabAdvanced.add(chkCopySymlinks, gbc);
        gbc.gridy = 3;
        tabAdvanced.add(chkFollowSymlinks, gbc);
        gbc.gridy = 4;
        tabAdvanced.add(chkSparseFiles, gbc);

        // Additional Options
        gbc.gridy = 5;
        gbc.insets = new Insets(12, 4, 4, 4);
        tabAdvanced.add(new JLabel("Zusätzliche Optionen:"), gbc);
        gbc.gridy = 6;
        gbc.insets = new Insets(2, 4, 4, 4);
        txtAdditionalOptions = new JTextField();
        txtAdditionalOptions.setToolTipText("Zusätzliche Optionen und Parameter, die direkt an die Synchronisations-Engine übergeben werden.");
        tabAdvanced.add(txtAdditionalOptions, gbc);

        // spacer
        gbc.gridy = 7;
        gbc.weighty = 1.0;
        tabAdvanced.add(Box.createGlue(), gbc);

        tabbedPane.addTab("Erweiterte Optionen", tabAdvanced);

        // --- Tab 3: Zusatzoptionen ---
        JPanel tabExtra = new JPanel(new GridBagLayout());
        tabExtra.setBackground(RETRO_BG);
        tabExtra.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        GridBagConstraints gbcEx = new GridBagConstraints();
        gbcEx.insets = new Insets(4, 4, 4, 4);
        gbcEx.anchor = GridBagConstraints.WEST;
        gbcEx.fill = GridBagConstraints.HORIZONTAL;

        // Pre Command
        gbcEx.gridx = 0;
        gbcEx.gridy = 0;
        gbcEx.weightx = 1.0;
        tabExtra.add(new JLabel("Vorher auszuführender Befehl:"), gbcEx);
        gbcEx.gridy = 1;
        txtPreCommand = new JTextField();
        txtPreCommand.setToolTipText("Ein Systembefehl oder Skript, das vor dem Starten der Synchronisation ausgeführt wird.");
        tabExtra.add(txtPreCommand, gbcEx);
        gbcEx.gridy = 2;
        chkHaltOnPreFailure = new JCheckBox("Bei Fehler abbrechen");
        chkHaltOnPreFailure.setBackground(RETRO_BG);
        chkHaltOnPreFailure.setToolTipText("Bricht die Synchronisation sofort ab, wenn der Vorher-Befehl fehlschlägt (Rückgabewert ungleich 0).");
        tabExtra.add(chkHaltOnPreFailure, gbcEx);

        // Post Command
        gbcEx.gridy = 3;
        gbcEx.insets = new Insets(16, 4, 4, 4);
        tabExtra.add(new JLabel("Nachher auszuführender Befehl:"), gbcEx);
        gbcEx.gridy = 4;
        gbcEx.insets = new Insets(4, 4, 4, 4);
        txtPostCommand = new JTextField();
        txtPostCommand.setToolTipText("Ein Systembefehl oder Skript, das nach erfolgreichem Abschluss der Synchronisation ausgeführt wird.");
        tabExtra.add(txtPostCommand, gbcEx);
        gbcEx.gridy = 5;
        chkSkipPostOnFailure = new JCheckBox("Bei Fehler nicht ausführen");
        chkSkipPostOnFailure.setBackground(RETRO_BG);
        chkSkipPostOnFailure.setToolTipText("Führt den Nachher-Befehl nicht aus, wenn bei der Synchronisation Fehler aufgetreten sind.");
        tabExtra.add(chkSkipPostOnFailure, gbcEx);

        // spacer
        gbcEx.gridy = 6;
        gbcEx.weighty = 1.0;
        tabExtra.add(Box.createGlue(), gbcEx);

        tabbedPane.addTab("Zusatzoptionen", tabExtra);

        return tabbedPane;
    }

    private JPanel buildBottomPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        panel.setBackground(RETRO_BG);

        btnCloseApp = new JButton("Beenden");
        btnCloseApp.addActionListener(e -> dispose());
        panel.add(btnCloseApp);

        btnStartSimulation = new JButton("Simulation starten");
        btnStartSimulation.addActionListener(e -> runSync(true));
        panel.add(btnStartSimulation);

        btnExecute = new JButton("Ausführen");
        btnExecute.addActionListener(e -> runSync(false));
        panel.add(btnExecute);

        return panel;
    }

    private void addPathPair() {
        Frame parent = (Frame) SwingUtilities.getWindowAncestor(this);
        blockWindow();

        AddPathPairDialog dialog = new AddPathPairDialog(parent, pair -> {
            activeSession.getPathPairs().add(pair);
            tableModelPathPairs.addRow(new Object[] { pair.getSourceDir(), pair.getTargetDir() });
            
            // Save settings
            SyncSessionStore.saveSessions(sessionsList);
        });

        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                unblockWindow();
            }
        });

        dialog.setVisible(true);
    }

    private void deleteSelectedPathPair() {
        int selectedRow = tablePathPairs.getSelectedRow();
        if (selectedRow != -1) {
            activeSession.getPathPairs().remove(selectedRow);
            tableModelPathPairs.removeRow(selectedRow);

            // Save settings
            SyncSessionStore.saveSessions(sessionsList);
        } else {
            JOptionPane.showMessageDialog(this,
                    "Bitte wählen Sie ein Verzeichnispaar aus der Tabelle zum Löschen aus.",
                    "Hinweis", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void populateUIFromActiveSession() {
        if (activeSession == null)
            return;
        isUpdatingUI = true;

        // Clear and populate path pairs table
        tableModelPathPairs.setRowCount(0);
        for (SyncPathPair pair : activeSession.getPathPairs()) {
            tableModelPathPairs.addRow(new Object[] { pair.getSourceDir(), pair.getTargetDir() });
        }

        // Basic Tab
        chkPreserveTimestamps.setSelected(activeSession.isPreserveTimestamps());
        chkPreservePermissions.setSelected(activeSession.isPreservePermissions());
        chkPreserveOwner.setSelected(activeSession.isPreserveOwner());
        chkPreserveGroup.setSelected(activeSession.isPreserveGroup());
        chkDeleteOnDestination.setSelected(activeSession.isDeleteOnDestination());
        chkDontLeaveFilesystem.setSelected(activeSession.isDontLeaveFilesystem());
        chkVerbose.setSelected(activeSession.isVerbose());
        chkShowProgress.setSelected(activeSession.isShowProgress());
        chkIgnoreExisting.setSelected(activeSession.isIgnoreExisting());
        chkSizeOnly.setSelected(activeSession.isSizeOnly());
        chkSkipNewer.setSelected(activeSession.isSkipNewer());
        chkWindowsMode.setSelected(activeSession.isWindowsMode());
        txtNotes.setText(activeSession.getNotes());

        // Advanced Tab
        chkCompression.setSelected(activeSession.isCompression());
        chkKeepPartial.setSelected(activeSession.isKeepPartial());
        chkCopySymlinks.setSelected(activeSession.isCopySymlinks());
        chkFollowSymlinks.setSelected(activeSession.isFollowSymlinks());
        chkSparseFiles.setSelected(activeSession.isSparseFiles());
        txtAdditionalOptions.setText(activeSession.getAdditionalOptions());

        // Extra Tab
        txtPreCommand.setText(activeSession.getPreCommand());
        chkHaltOnPreFailure.setSelected(activeSession.isHaltOnPreFailure());
        txtPostCommand.setText(activeSession.getPostCommand());
        chkSkipPostOnFailure.setSelected(activeSession.isSkipPostOnFailure());

        isUpdatingUI = false;
    }

    private void updateActiveSessionFromUI() {
        if (activeSession == null || isUpdatingUI)
            return;

        // Basic Tab
        activeSession.setPreserveTimestamps(chkPreserveTimestamps.isSelected());
        activeSession.setPreservePermissions(chkPreservePermissions.isSelected());
        activeSession.setPreserveOwner(chkPreserveOwner.isSelected());
        activeSession.setPreserveGroup(chkPreserveGroup.isSelected());
        activeSession.setDeleteOnDestination(chkDeleteOnDestination.isSelected());
        activeSession.setDontLeaveFilesystem(chkDontLeaveFilesystem.isSelected());
        activeSession.setVerbose(chkVerbose.isSelected());
        activeSession.setShowProgress(chkShowProgress.isSelected());
        activeSession.setIgnoreExisting(chkIgnoreExisting.isSelected());
        activeSession.setSizeOnly(chkSizeOnly.isSelected());
        activeSession.setSkipNewer(chkSkipNewer.isSelected());
        activeSession.setWindowsMode(chkWindowsMode.isSelected());
        activeSession.setNotes(txtNotes.getText());

        // Advanced Tab
        activeSession.setCompression(chkCompression.isSelected());
        activeSession.setKeepPartial(chkKeepPartial.isSelected());
        activeSession.setCopySymlinks(chkCopySymlinks.isSelected());
        activeSession.setFollowSymlinks(chkFollowSymlinks.isSelected());
        activeSession.setSparseFiles(chkSparseFiles.isSelected());
        activeSession.setAdditionalOptions(txtAdditionalOptions.getText().trim());

        // Extra Tab
        activeSession.setPreCommand(txtPreCommand.getText().trim());
        activeSession.setHaltOnPreFailure(chkHaltOnPreFailure.isSelected());
        activeSession.setPostCommand(txtPostCommand.getText().trim());
        activeSession.setSkipPostOnFailure(chkSkipPostOnFailure.isSelected());

        // Persist to json file
        SyncSessionStore.saveSessions(sessionsList);
    }

    private void bindControlListeners() {
        // DocumentListeners for text fields
        DocumentListener docListener = new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                updateActiveSessionFromUI();
            }

            public void removeUpdate(DocumentEvent e) {
                updateActiveSessionFromUI();
            }

            public void changedUpdate(DocumentEvent e) {
                updateActiveSessionFromUI();
            }
        };

        txtNotes.getDocument().addDocumentListener(docListener);
        txtAdditionalOptions.getDocument().addDocumentListener(docListener);
        txtPreCommand.getDocument().addDocumentListener(docListener);
        txtPostCommand.getDocument().addDocumentListener(docListener);

        // ItemListeners for checkboxes
        ItemListener itemListener = e -> updateActiveSessionFromUI();

        for (JCheckBox cb : new JCheckBox[] { chkPreserveTimestamps, chkPreservePermissions, chkPreserveOwner,
                chkPreserveGroup,
                chkDeleteOnDestination, chkDontLeaveFilesystem, chkVerbose, chkShowProgress, chkIgnoreExisting,
                chkSizeOnly,
                chkSkipNewer, chkWindowsMode, chkCompression, chkKeepPartial, chkCopySymlinks, chkFollowSymlinks,
                chkSparseFiles,
                chkHaltOnPreFailure, chkSkipPostOnFailure }) {
            cb.addItemListener(itemListener);
        }
    }

    private void addNewSession() {
        String name = JOptionPane.showInputDialog(this, "Name der neuen Sitzung eingeben:", "Sitzung hinzufügen",
                JOptionPane.PLAIN_MESSAGE);
        if (name != null && !name.trim().isEmpty()) {
            name = name.trim();
            for (SyncSession s : sessionsList) {
                if (s.getName().equalsIgnoreCase(name)) {
                    JOptionPane.showMessageDialog(this, "Eine Sitzung mit diesem Namen existiert bereits.", "Fehler",
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }

            SyncSession newSession = new SyncSession(name);
            sessionsList.add(newSession);

            // Reload combo box
            isUpdatingUI = true;
            comboSessions.addItem(newSession);
            comboSessions.setSelectedItem(newSession);
            activeSession = newSession;
            populateUIFromActiveSession();
            isUpdatingUI = false;

            SyncSessionStore.saveSessions(sessionsList);
        }
    }

    private void deleteCurrentSession() {
        if (sessionsList.size() <= 1) {
            JOptionPane.showMessageDialog(this, "Die letzte Sitzung kann nicht gelöscht werden.", "Hinweis",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        int response = JOptionPane.showConfirmDialog(this,
                "Möchten Sie die Sitzung \"" + activeSession.getName() + "\" wirklich löschen?",
                "Sitzung löschen", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);

        if (response == JOptionPane.YES_OPTION) {
            int index = sessionsList.indexOf(activeSession);
            sessionsList.remove(activeSession);

            isUpdatingUI = true;
            comboSessions.removeItem(activeSession);
            int nextIndex = Math.max(0, index - 1);
            activeSession = sessionsList.get(nextIndex);
            comboSessions.setSelectedItem(activeSession);
            populateUIFromActiveSession();
            isUpdatingUI = false;

            SyncSessionStore.saveSessions(sessionsList);
        }
    }

    private void runSync(boolean dryRun) {
        // Double check save state
        updateActiveSessionFromUI();

        if (activeSession.getPathPairs().isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Bitte mindestens ein Quell- und Zielverzeichnispaar hinzufügen.",
                    "Fehler", JOptionPane.ERROR_MESSAGE);
            return;
        }

        blockWindow();

        // Show Progress Log Dialog
        SyncProgressDialog dialog = new SyncProgressDialog(
                (Frame) SwingUtilities.getWindowAncestor(this),
                activeSession,
                dryRun);

        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                unblockWindow();
            }
        });

        dialog.setLocationRelativeTo(this);
        dialog.startSync();
        dialog.setVisible(true);
    }

    private static class AddPathPairDialog extends JDialog {
        private final JTextField txtDialogSource;
        private final JTextField txtDialogTarget;
        private final JButton btnDialogBrowseSource;
        private final JButton btnDialogBrowseTarget;
        private final JButton btnDialogOk;
        private final JButton btnDialogCancel;
        private final java.util.function.Consumer<SyncPathPair> onApprove;

        public AddPathPairDialog(Frame owner, java.util.function.Consumer<SyncPathPair> onApprove) {
            super(owner, "Verzeichnispaar hinzufügen", false); // modeless!
            this.onApprove = onApprove;
            setSize(500, 180);
            setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
            setLayout(new GridBagLayout());
            setLocationRelativeTo(owner);

            // Style with retro-like details
            JPanel content = new JPanel(new GridBagLayout());
            content.setBackground(RETRO_BG);
            content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(6, 6, 6, 6);
            gbc.fill = GridBagConstraints.HORIZONTAL;

            // Source row
            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.weightx = 0.0;
            content.add(new JLabel("Quelle:"), gbc);

            gbc.gridx = 1;
            gbc.weightx = 1.0;
            txtDialogSource = new JTextField();
            content.add(txtDialogSource, gbc);

            gbc.gridx = 2;
            gbc.weightx = 0.0;
            btnDialogBrowseSource = new JButton("Auswählen");
            btnDialogBrowseSource.addActionListener(e -> browseDirectory(txtDialogSource, false));
            content.add(btnDialogBrowseSource, gbc);

            // Target row
            gbc.gridx = 0;
            gbc.gridy = 1;
            content.add(new JLabel("Ziel:"), gbc);

            gbc.gridx = 1;
            txtDialogTarget = new JTextField();
            content.add(txtDialogTarget, gbc);

            gbc.gridx = 2;
            btnDialogBrowseTarget = new JButton("Auswählen");
            btnDialogBrowseTarget.addActionListener(e -> browseDirectory(txtDialogTarget, true));
            content.add(btnDialogBrowseTarget, gbc);

            // Action row
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
            buttonPanel.setOpaque(false);
            btnDialogOk = new JButton("Hinzufügen");
            btnDialogOk.addActionListener(e -> {
                if (getSource().isEmpty() || getTarget().isEmpty()) {
                    JOptionPane.showMessageDialog(this, "Bitte beide Verzeichnisse angeben.", "Fehler",
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }
                onApprove.accept(new SyncPathPair(getSource(), getTarget()));
                dispose();
            });
            btnDialogCancel = new JButton("Abbrechen");
            btnDialogCancel.addActionListener(e -> dispose());
            buttonPanel.add(btnDialogCancel);
            buttonPanel.add(btnDialogOk);

            gbc.gridx = 0;
            gbc.gridy = 2;
            gbc.gridwidth = 3;
            gbc.insets = new Insets(12, 6, 6, 6);
            content.add(buttonPanel, gbc);

            setContentPane(content);
        }

        private void browseDirectory(JTextField field, boolean isTarget) {
            JFileChooser chooser = new JFileChooser();
            if (!field.getText().isEmpty()) {
                File current = new File(field.getText().trim());
                if (current.exists()) {
                    chooser.setCurrentDirectory(current);
                }
            }
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                String path = chooser.getSelectedFile().getAbsolutePath();
                if (isTarget) {
                    String srcPath = txtDialogSource.getText().trim();
                    if (!srcPath.isEmpty()) {
                        File srcFile = new File(srcPath);
                        if (srcFile.exists() && srcFile.isDirectory()) {
                            String lastDir = srcFile.getName();
                            if (lastDir != null && !lastDir.isEmpty()) {
                                File checkPath = new File(path);
                                if (!checkPath.getName().equalsIgnoreCase(lastDir)) {
                                    if (!path.endsWith(File.separator)) {
                                        path += File.separator;
                                    }
                                    path += lastDir;
                                }
                            }
                        }
                    }
                }
                if (!path.endsWith(File.separator)) {
                    path += File.separator;
                }
                field.setText(path);
            }
        }

        public String getSource() {
            return txtDialogSource.getText().trim();
        }

        public String getTarget() {
            return txtDialogTarget.getText().trim();
        }
    }

    // ── Sync Progress Dialog ────────────────────────────────────────────────
    private static class SyncProgressDialog extends JDialog {
        private final SyncSession session;
        private final boolean dryRun;
        private EuroSyncEngine engine;

        private JLabel lblStatus;
        private JProgressBar progressBar;
        private JTextArea txtLog;
        private JButton btnCancel;
        private JButton btnClose;

        public SyncProgressDialog(Frame owner, SyncSession session, boolean dryRun) {
            super(owner, "EuroSync: " + session.getName(), false); // modeless!
            this.session = session;
            this.dryRun = dryRun;

            setSize(600, 400);
            setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
            addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    handleCancel();
                }
            });

            JPanel root = new JPanel(new BorderLayout(5, 5));
            root.setBackground(RETRO_BG);
            root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            // Top Status Panel
            JPanel topPanel = new JPanel(new BorderLayout(5, 5));
            topPanel.setBackground(RETRO_BG);
            lblStatus = new JLabel(dryRun ? "Bereite Simulation vor..." : "Bereite Synchronisation vor...");
            lblStatus.setFont(lblStatus.getFont().deriveFont(Font.BOLD));
            topPanel.add(lblStatus, BorderLayout.NORTH);

            progressBar = new JProgressBar(0, 100);
            progressBar.setStringPainted(true);
            topPanel.add(progressBar, BorderLayout.CENTER);
            root.add(topPanel, BorderLayout.NORTH);

            // Center Log Panel (Terminal feel)
            txtLog = new JTextArea();
            txtLog.setEditable(false);
            txtLog.setBackground(Color.BLACK);
            txtLog.setForeground(new Color(220, 220, 220)); // Soft white/grey for readability
            txtLog.setFont(new Font("Courier New", Font.PLAIN, 12));
            JScrollPane scrollPane = new JScrollPane(txtLog);
            root.add(scrollPane, BorderLayout.CENTER);

            // Bottom Buttons
            JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
            bottomPanel.setBackground(RETRO_BG);

            btnCancel = new JButton("Abbrechen");
            btnCancel.addActionListener(e -> handleCancel());
            bottomPanel.add(btnCancel);

            btnClose = new JButton("Schließen");
            btnClose.setEnabled(false);
            btnClose.addActionListener(e -> dispose());
            bottomPanel.add(btnClose);

            root.add(bottomPanel, BorderLayout.SOUTH);
            setContentPane(root);
        }

        public void startSync() {
            engine = new EuroSyncEngine(session, dryRun, new EuroSyncEngine.EngineCallback() {
                @Override
                public void onLog(String message) {
                    txtLog.append(message + "\n");
                    txtLog.setCaretPosition(txtLog.getDocument().getLength());
                }

                @Override
                public void onProgress(int current, int max, String currentFile, long fileBytesTransferred, long fileTotalBytes) {
                    SwingUtilities.invokeLater(() -> {
                        String sizeInfo = "";
                        if (fileTotalBytes > 0) {
                            sizeInfo = String.format(" (%s / %s)", formatSize(fileBytesTransferred), formatSize(fileTotalBytes));
                        }
                        lblStatus.setText("Synchronisiere: " + currentFile + sizeInfo);
                        int pct = max > 0 ? (int) (((double) current / max) * 100) : 0;
                        progressBar.setValue(pct);
                        progressBar.setString(String.format("%d / %d (%d%%)", current, max, pct));
                    });
                }

                private static String formatSize(long bytes) {
                    if (bytes <= 0) return "0 B";
                    if (bytes < 1024) return bytes + " B";
                    if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
                    if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
                    return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
                }

                @Override
                public void onFinished(boolean success, boolean canceled, String summary) {
                    lblStatus.setText(canceled ? "Vom Benutzer abgebrochen." : "Synchronisation beendet.");
                    txtLog.append(summary);
                    txtLog.setCaretPosition(txtLog.getDocument().getLength());

                    btnCancel.setEnabled(false);
                    btnClose.setEnabled(true);
                }
            });
            engine.execute();
        }

        private void handleCancel() {
            if (engine != null && !engine.isDone()) {
                int resp = JOptionPane.showConfirmDialog(this,
                        "Möchten Sie die Synchronisation wirklich abbrechen?",
                        "Abbrechen", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (resp == JOptionPane.YES_OPTION) {
                    engine.cancelSync();
                    engine.cancel(true);
                    btnCancel.setEnabled(false);
                    btnClose.setEnabled(true);
                }
            } else {
                dispose();
            }
        }
    }

    // ── Menu Bar & Help Dialog ──────────────────────────────────────────────
    private void setupMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        JMenu helpMenu = new JMenu("Hilfe");
        helpMenu.setMnemonic(KeyEvent.VK_H);

        JMenuItem helpItem = new JMenuItem("EuroSync-Hilfe...", KeyEvent.VK_F);
        helpItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0));
        helpItem.addActionListener(e -> showHelpDialog());
        helpMenu.add(helpItem);

        menuBar.add(helpMenu);
        setJMenuBar(menuBar);
    }

    private void showHelpDialog() {
        Frame parent = (Frame) SwingUtilities.getWindowAncestor(this);
        blockWindow();
        HelpDialog dialog = new HelpDialog(parent);
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                unblockWindow();
            }
        });
        dialog.setVisible(true);
    }

    private static class HelpDialog extends JDialog {
        public HelpDialog(Frame owner) {
            super(owner, "EuroSync Hilfe / Dokumentation", false);
            setSize(600, 500);
            setMinimumSize(new Dimension(450, 400));
            setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
            setLocationRelativeTo(owner);

            JPanel contentPanel = new JPanel(new BorderLayout(5, 5));
            contentPanel.setBackground(RETRO_BG);
            contentPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            JEditorPane helpPane = new JEditorPane();
            helpPane.setEditable(false);
            helpPane.setContentType("text/html");

            try {
                URL helpUrl = EuroSync.class.getResource("/apps/eurosync/help.html");
                if (helpUrl != null) {
                    helpPane.setPage(helpUrl);
                } else {
                    helpPane.setText("<html><body style='font-family:sans-serif;'><h2>Dokumentation nicht gefunden.</h2></body></html>");
                }
            } catch (Exception ex) {
                helpPane.setText("<html><body style='font-family:sans-serif;'><h2>Fehler beim Laden der Dokumentation:</h2><p>" + ex.getMessage() + "</p></body></html>");
            }

            JScrollPane scrollPane = new JScrollPane(helpPane);
            contentPanel.add(scrollPane, BorderLayout.CENTER);

            // Add close button at the bottom
            JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            btnPanel.setOpaque(false);
            JButton btnClose = new JButton("Schließen");
            btnClose.addActionListener(e -> dispose());
            btnPanel.add(btnClose);
            contentPanel.add(btnPanel, BorderLayout.SOUTH);

            setContentPane(contentPanel);
        }
    }
}

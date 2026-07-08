package com.datazuul.euroworks.apps.eurocommander;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class EuroCommanderPane extends JPanel {

    private final EuroCommander parent;
    private final JComboBox<File> driveCombo;
    private final JTextField pathField;
    private final JTable table;
    private final FileTableModel tableModel;
    private File currentDir;
    private boolean isUpdatingDrive = false;

    private static final Icon FOLDER_ICON = new FolderIcon();
    private static final Icon FILE_ICON = new FileIcon();

    public EuroCommanderPane(EuroCommander parent, File initialDir) {
        this.parent = parent;
        setLayout(new BorderLayout());

        // Top Panel: Drive Selector & Path TextField
        JPanel topPanel = new JPanel(new BorderLayout(5, 0));
        topPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        driveCombo = new JComboBox<>();
        File[] roots = File.listRoots();
        if (roots != null) {
            for (File root : roots) {
                driveCombo.addItem(root);
            }
        }

        // Custom renderer for drive combo to display full path (e.g. C:\)
        driveCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof File file) {
                    setText(file.getAbsolutePath());
                }
                return this;
            }
        });

        driveCombo.addActionListener(e -> {
            if (isUpdatingDrive) return;
            File selectedRoot = (File) driveCombo.getSelectedItem();
            if (selectedRoot != null) {
                navigateTo(selectedRoot);
            }
        });

        pathField = new JTextField();
        pathField.addActionListener(e -> {
            String pathText = pathField.getText().trim();
            if (!pathText.isEmpty()) {
                navigateTo(new File(pathText));
            }
        });

        topPanel.add(driveCombo, BorderLayout.WEST);
        topPanel.add(pathField, BorderLayout.CENTER);
        add(topPanel, BorderLayout.NORTH);

        // Center Panel: File Table
        tableModel = new FileTableModel();
        table = new JTable(tableModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setShowGrid(false);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);

        // Styling the table to look neat and clean
        table.setRowHeight(20);

        // Set column widths and custom renderers
        table.getColumnModel().getColumn(0).setPreferredWidth(30);
        table.getColumnModel().getColumn(0).setMinWidth(30);
        table.getColumnModel().getColumn(0).setMaxWidth(40);

        table.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(table, "", isSelected, hasFocus, row, column);
                if (value instanceof FileEntry entry) {
                    setIcon(entry.isDirectory() ? FOLDER_ICON : FILE_ICON);
                    setHorizontalAlignment(SwingConstants.CENTER);
                } else {
                    setIcon(null);
                }
                return this;
            }
        });

        // Double click navigation
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int selectedRow = table.getSelectedRow();
                    if (selectedRow != -1) {
                        int modelRow = table.convertRowIndexToModel(selectedRow);
                        FileEntry entry = tableModel.getEntry(modelRow);
                        if (entry != null && entry.isDirectory()) {
                            navigateTo(entry.getFile());
                        }
                    }
                }
            }
        });

        // Keyboard navigation (Enter to open, Backspace to go up)
        table.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_ENTER:
                        int selectedRow = table.getSelectedRow();
                        if (selectedRow != -1) {
                            int modelRow = table.convertRowIndexToModel(selectedRow);
                            FileEntry entry = tableModel.getEntry(modelRow);
                            if (entry != null && entry.isDirectory()) {
                                navigateTo(entry.getFile());
                                e.consume();
                            }
                        }
                        break;
                    case KeyEvent.VK_BACK_SPACE:
                        File parentDir = currentDir.getParentFile();
                        if (parentDir != null) {
                            navigateTo(parentDir);
                            e.consume();
                        }
                        break;
                    case KeyEvent.VK_F2:
                        parent.performRename();
                        e.consume();
                        break;
                    case KeyEvent.VK_F3:
                        parent.performView();
                        e.consume();
                        break;
                    case KeyEvent.VK_F4:
                        parent.performEdit();
                        e.consume();
                        break;
                    case KeyEvent.VK_F5:
                        parent.performCopy();
                        e.consume();
                        break;
                    case KeyEvent.VK_F6:
                        parent.performMove();
                        e.consume();
                        break;
                    case KeyEvent.VK_F7:
                        parent.performMkDir();
                        e.consume();
                        break;
                    case KeyEvent.VK_F8:
                        parent.performDelete();
                        e.consume();
                        break;
                    case KeyEvent.VK_F9:
                        parent.performRefresh();
                        e.consume();
                        break;
                }
            }
        });

        // Disable default table actions for F2-F9 to let frame bindings intercept them
        int[] keysToDisable = {KeyEvent.VK_F2, KeyEvent.VK_F3, KeyEvent.VK_F4, KeyEvent.VK_F5, KeyEvent.VK_F6, KeyEvent.VK_F7, KeyEvent.VK_F8, KeyEvent.VK_F9};
        for (int key : keysToDisable) {
            table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(key, 0), "none");
            table.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key, 0), "none");
        }

        // --- Active Pane Tracking Listeners ---
        FocusAdapter activeFocusListener = new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                parent.setActivePane(EuroCommanderPane.this);
            }
        };
        table.addFocusListener(activeFocusListener);
        driveCombo.addFocusListener(activeFocusListener);
        pathField.addFocusListener(activeFocusListener);

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                parent.setActivePane(EuroCommanderPane.this);
            }
        });

        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                parent.setActivePane(EuroCommanderPane.this);
            }
        });

        JScrollPane scrollPane = new JScrollPane(table);
        add(scrollPane, BorderLayout.CENTER);

        // Initial Navigation
        navigateTo(initialDir);
    }

    public File getCurrentDir() {
        return currentDir;
    }

    public void setActive(boolean active) {
        if (active) {
            pathField.setBackground(new Color(220, 240, 255)); // Light blue highlight
        } else {
            pathField.setBackground(UIManager.getColor("TextField.background"));
        }
    }

    public File getSelectedFile() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow != -1) {
            int modelRow = table.convertRowIndexToModel(selectedRow);
            FileEntry entry = tableModel.getEntry(modelRow);
            if (entry != null && !entry.isParentPointer()) {
                return entry.getFile();
            }
        }
        return null;
    }

    public void refresh() {
        loadDirectory(currentDir);
    }

    public void navigateTo(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            JOptionPane.showMessageDialog(this, "Directory does not exist or is not accessible:\n" 
                    + (dir != null ? dir.getAbsolutePath() : "null"), "Error", JOptionPane.ERROR_MESSAGE);
            if (currentDir != null) {
                pathField.setText(currentDir.getAbsolutePath());
            }
            return;
        }

        currentDir = dir;
        pathField.setText(currentDir.getAbsolutePath());

        // Update the drive selector index silently
        File root = getRoot(dir);
        if (root != null) {
            isUpdatingDrive = true;
            for (int i = 0; i < driveCombo.getItemCount(); i++) {
                File item = driveCombo.getItemAt(i);
                if (item.getAbsolutePath().equalsIgnoreCase(root.getAbsolutePath())) {
                    driveCombo.setSelectedIndex(i);
                    break;
                }
            }
            isUpdatingDrive = false;
        }

        loadDirectory(dir);
    }

    private File getRoot(File file) {
        File root = file;
        while (root.getParentFile() != null) {
            root = root.getParentFile();
        }
        return root;
    }

    private void loadDirectory(File dir) {
        new Thread(() -> {
            List<FileEntry> entries = new ArrayList<>();

            // 1. Add Parent navigation row ".."
            File parent = dir.getParentFile();
            if (parent != null) {
                entries.add(new FileEntry(parent, "..", true));
            }

            try {
                File[] files = dir.listFiles();
                if (files != null) {
                    List<FileEntry> dirsList = new ArrayList<>();
                    List<FileEntry> filesList = new ArrayList<>();
                    for (File f : files) {
                        if (f.getName().startsWith(".") || f.isHidden()) {
                            continue;
                        }
                        if (f.isDirectory()) {
                            dirsList.add(new FileEntry(f, f.getName(), false));
                        } else {
                            filesList.add(new FileEntry(f, f.getName(), false));
                        }
                    }

                    // Sort alphabetically case-insensitive
                    dirsList.sort((e1, e2) -> e1.getDisplayName().compareToIgnoreCase(e2.getDisplayName()));
                    filesList.sort((e1, e2) -> e1.getDisplayName().compareToIgnoreCase(e2.getDisplayName()));

                    entries.addAll(dirsList);
                    entries.addAll(filesList);
                }
            } catch (Exception e) {
                // List files might throw security exceptions, ignore and display whatever we loaded (e.g. parent)
            }

            SwingUtilities.invokeLater(() -> {
                if (currentDir.equals(dir)) {
                    tableModel.setEntries(entries);
                    if (table.getRowCount() > 0) {
                        table.setRowSelectionInterval(0, 0);
                    }
                }
            });
        }).start();
    }

    // --- Helper Models ---

    private static class FileEntry {
        private final File file;
        private final String displayName;
        private final boolean isParentPointer;

        public FileEntry(File file, String displayName, boolean isParentPointer) {
            this.file = file;
            this.displayName = displayName;
            this.isParentPointer = isParentPointer;
        }

        public File getFile() {
            return file;
        }

        public String getDisplayName() {
            return displayName;
        }

        public boolean isParentPointer() {
            return isParentPointer;
        }

        public boolean isDirectory() {
            return isParentPointer || file.isDirectory();
        }
    }

    private static class FileTableModel extends AbstractTableModel {
        private final String[] columnNames = {"", "Name", "Type", "Size", "Modified"};
        private final List<FileEntry> entries = new ArrayList<>();

        public void setEntries(List<FileEntry> newEntries) {
            entries.clear();
            entries.addAll(newEntries);
            fireTableDataChanged();
        }

        public FileEntry getEntry(int rowIndex) {
            if (rowIndex >= 0 && rowIndex < entries.size()) {
                return entries.get(rowIndex);
            }
            return null;
        }

        @Override
        public int getRowCount() {
            return entries.size();
        }

        @Override
        public int getColumnCount() {
            return columnNames.length;
        }

        @Override
        public String getColumnName(int column) {
            return columnNames[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            FileEntry entry = entries.get(rowIndex);
            if (entry == null) return null;

            switch (columnIndex) {
                case 0:
                    return entry;
                case 1:
                    return entry.getDisplayName();
                case 2:
                    if (entry.isParentPointer()) return "";
                    return entry.isDirectory() ? "Folder" : getFileExtension(entry.getFile());
                case 3:
                    if (entry.isParentPointer()) return "";
                    return entry.isDirectory() ? "<DIR>" : formatSize(entry.getFile().length());
                case 4:
                    if (entry.isParentPointer()) return "";
                    return formatTimestamp(entry.getFile().lastModified());
                default:
                    return null;
            }
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex == 0) {
                return FileEntry.class;
            }
            return String.class;
        }

        private String getFileExtension(File file) {
            String name = file.getName();
            int lastDot = name.lastIndexOf('.');
            if (lastDot > 0 && lastDot < name.length() - 1) {
                return name.substring(lastDot + 1).toUpperCase();
            }
            return "File";
        }

        private String formatSize(long size) {
            if (size < 1024) {
                return size + " B";
            } else if (size < 1024 * 1024) {
                return (size / 1024) + " KB";
            } else {
                return String.format("%.1f MB", (double) size / (1024 * 1024));
            }
        }

        private String formatTimestamp(long time) {
            if (time == 0) return "";
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            return sdf.format(new Date(time));
        }
    }

    // --- Retro Custom Icons ---

    private static class FolderIcon implements Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(new Color(230, 200, 80));
            g2.fillRect(x + 2, y + 4, 12, 9);
            g2.fillRect(x + 2, y + 2, 5, 2); // Folder Tab

            g2.setColor(Color.BLACK);
            g2.drawRect(x + 2, y + 4, 12, 9);
            g2.drawLine(x + 2, y + 2, x + 6, y + 2);
            g2.drawLine(x + 6, y + 2, x + 7, y + 4);
            g2.dispose();
        }

        @Override
        public int getIconWidth() { return 16; }
        @Override
        public int getIconHeight() { return 16; }
    }

    private static class FileIcon implements Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(Color.WHITE);
            g2.fillRect(x + 3, y + 1, 10, 14);

            g2.setColor(Color.BLACK);
            g2.drawRect(x + 3, y + 1, 10, 14);

            g2.drawLine(x + 10, y + 1, x + 10, y + 4);
            g2.drawLine(x + 10, y + 4, x + 13, y + 4);

            g2.setColor(Color.LIGHT_GRAY);
            g2.drawLine(x + 5, y + 5, x + 11, y + 5);
            g2.drawLine(x + 5, y + 8, x + 11, y + 8);
            g2.drawLine(x + 5, y + 11, x + 9, y + 11);
            g2.dispose();
        }

        @Override
        public int getIconWidth() { return 16; }
        @Override
        public int getIconHeight() { return 16; }
    }
}

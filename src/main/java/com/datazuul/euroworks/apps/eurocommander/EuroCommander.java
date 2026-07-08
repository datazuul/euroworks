package com.datazuul.euroworks.apps.eurocommander;

import com.datazuul.euroworks.apps.EuroAppFrame;
import com.datazuul.euroworks.apps.euronotepad.EuroNotepad;
import com.datazuul.euroworks.shell.EuroIconThemeManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class EuroCommander extends EuroAppFrame {

    private final EuroCommanderPane leftPane;
    private final EuroCommanderPane rightPane;
    private EuroCommanderPane activePane;

    public EuroCommander() {
        super("EuroCommander (File Commander)");
        setSize(1024, 800);

        // Find initial directory (C:\ as default, fallback to first system root)
        File initialDir = new File("C:\\");
        if (!initialDir.exists()) {
            File[] roots = File.listRoots();
            if (roots != null && roots.length > 0) {
                initialDir = roots[0];
            }
        }

        // Initialize Left and Right Panes
        leftPane = new EuroCommanderPane(this, initialDir);
        rightPane = new EuroCommanderPane(this, initialDir);
        activePane = leftPane;
        leftPane.setActive(true);
        rightPane.setActive(false);

        // Set up JSplitPane
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPane, rightPane);
        splitPane.setDividerLocation(500); // balance divided at approximately center
        splitPane.setResizeWeight(0.5);   // keeps proportional size on window resize

        // Bottom Button Bar Panel
        JPanel bottomPanel = new JPanel(new GridLayout(1, 8, 5, 0));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JButton btnRename = new JButton("Rename [F2]", EuroIconThemeManager.getIcon("preferences"));
        btnRename.addActionListener(e -> performRename());

        JButton btnView = new JButton("View [F3]", EuroIconThemeManager.getIcon("document"));
        btnView.addActionListener(e -> performView());

        JButton btnEdit = new JButton("Edit [F4]", EuroIconThemeManager.getIcon("document"));
        btnEdit.addActionListener(e -> performEdit());

        JButton btnCopy = new JButton("Copy [F5]", EuroIconThemeManager.getIcon("sync"));
        btnCopy.addActionListener(e -> performCopy());

        JButton btnMove = new JButton("Move [F6]", EuroIconThemeManager.getIcon("sync"));
        btnMove.addActionListener(e -> performMove());

        JButton btnMkDir = new JButton("Make directory [F7]", EuroIconThemeManager.getIcon("folder"));
        btnMkDir.addActionListener(e -> performMkDir());

        JButton btnDelete = new JButton("Delete [F8]", EuroIconThemeManager.getIcon("exit"));
        btnDelete.addActionListener(e -> performDelete());

        JButton btnRefresh = new JButton("Refresh [F9]", EuroIconThemeManager.getIcon("sync"));
        btnRefresh.addActionListener(e -> performRefresh());

        bottomPanel.add(btnRename);
        bottomPanel.add(btnView);
        bottomPanel.add(btnEdit);
        bottomPanel.add(btnCopy);
        bottomPanel.add(btnMove);
        bottomPanel.add(btnMkDir);
        bottomPanel.add(btnDelete);
        bottomPanel.add(btnRefresh);

        // Main Layout
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(splitPane, BorderLayout.CENTER);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);
        setContentPane(mainPanel);

        // Bind global Keyboard shortcuts F2 - F9
        setupGlobalKeyBindings();
    }

    public void setActivePane(EuroCommanderPane pane) {
        if (pane == null) return;
        if (activePane != pane) {
            activePane.setActive(false);
            activePane = pane;
            activePane.setActive(true);
        }
    }

    public EuroCommanderPane getInactivePane() {
        return (activePane == leftPane) ? rightPane : leftPane;
    }

    private void setupGlobalKeyBindings() {
        JPanel contentPane = (JPanel) getContentPane();
        InputMap inputMap = contentPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = contentPane.getActionMap();

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), "renameAction");
        actionMap.put("renameAction", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                performRename();
            }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_F3, 0), "viewAction");
        actionMap.put("viewAction", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                performView();
            }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_F4, 0), "editAction");
        actionMap.put("editAction", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                performEdit();
            }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), "copyAction");
        actionMap.put("copyAction", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                performCopy();
            }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_F6, 0), "moveAction");
        actionMap.put("moveAction", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                performMove();
            }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_F7, 0), "mkdirAction");
        actionMap.put("mkdirAction", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                performMkDir();
            }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_F8, 0), "deleteAction");
        actionMap.put("deleteAction", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                performDelete();
            }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_F9, 0), "refreshAction");
        actionMap.put("refreshAction", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                performRefresh();
            }
        });
    }

    // --- Action Methods ---

    void performRename() {
        File file = activePane.getSelectedFile();
        if (file == null) {
            JOptionPane.showMessageDialog(this, "No file or directory selected to rename.", "Rename", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String newName = (String) JOptionPane.showInputDialog(this, "Rename to:", "Rename", JOptionPane.PLAIN_MESSAGE, null, null, file.getName());
        if (newName == null || newName.trim().isEmpty() || newName.equals(file.getName())) {
            return;
        }

        File destFile = new File(file.getParentFile(), newName.trim());
        if (destFile.exists()) {
            JOptionPane.showMessageDialog(this, "A file or directory with that name already exists.", "Rename Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (file.renameTo(destFile)) {
            activePane.refresh();
            getInactivePane().refresh();
        } else {
            JOptionPane.showMessageDialog(this, "Failed to rename file/directory.", "Rename Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    void performView() {
        File file = activePane.getSelectedFile();
        if (file == null || !file.isFile()) {
            JOptionPane.showMessageDialog(this, "Please select a file to view.", "View", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JDesktopPane desktop = getDesktopPane();
        if (desktop != null) {
            EuroNotepad notepad = new EuroNotepad(file);
            notepad.setReadOnly(true);
            desktop.add(notepad);
            notepad.setLocation(getX() + 50, getY() + 50);
            try {
                notepad.setSelected(true);
            } catch (Exception ex) {
                // ignore
            }
        }
    }

    void performEdit() {
        File file = activePane.getSelectedFile();
        if (file == null || !file.isFile()) {
            JOptionPane.showMessageDialog(this, "Please select a file to edit.", "Edit", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JDesktopPane desktop = getDesktopPane();
        if (desktop != null) {
            EuroNotepad notepad = new EuroNotepad(file);
            notepad.setReadOnly(false);
            desktop.add(notepad);
            notepad.setLocation(getX() + 50, getY() + 50);
            try {
                notepad.setSelected(true);
            } catch (Exception ex) {
                // ignore
            }
        }
    }

    void performCopy() {
        File source = activePane.getSelectedFile();
        if (source == null) {
            JOptionPane.showMessageDialog(this, "No file or directory selected to copy.", "Copy", JOptionPane.WARNING_MESSAGE);
            return;
        }

        File targetDir = getInactivePane().getCurrentDir();
        File dest = new File(targetDir, source.getName());

        if (dest.exists()) {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Destination already exists. Overwrite?\n" + dest.getAbsolutePath(),
                    "Confirm Overwrite",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (confirm != JOptionPane.YES_OPTION) {
                return;
            }
        }

        new Thread(() -> {
            try {
                copyRecursive(source, dest);
                SwingUtilities.invokeLater(() -> {
                    activePane.refresh();
                    getInactivePane().refresh();
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, "Copy failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
    }

    private void copyRecursive(File src, File dest) throws IOException {
        if (src.isDirectory()) {
            if (!dest.exists()) {
                dest.mkdirs();
            }
            String[] files = src.list();
            if (files != null) {
                for (String f : files) {
                    copyRecursive(new File(src, f), new File(dest, f));
                }
            }
        } else {
            Files.copy(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    void performMove() {
        File source = activePane.getSelectedFile();
        if (source == null) {
            JOptionPane.showMessageDialog(this, "No file or directory selected to move.", "Move", JOptionPane.WARNING_MESSAGE);
            return;
        }

        File targetDir = getInactivePane().getCurrentDir();
        File dest = new File(targetDir, source.getName());

        if (dest.exists()) {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Destination already exists. Overwrite?\n" + dest.getAbsolutePath(),
                    "Confirm Overwrite",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (confirm != JOptionPane.YES_OPTION) {
                return;
            }
        }

        new Thread(() -> {
            try {
                moveRecursive(source, dest);
                SwingUtilities.invokeLater(() -> {
                    activePane.refresh();
                    getInactivePane().refresh();
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, "Move failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
    }

    private void moveRecursive(File src, File dest) throws IOException {
        try {
            Files.move(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            // Fallback for cross-drive moving: Copy recursively then delete recursively
            copyRecursive(src, dest);
            deleteRecursive(src);
        }
    }

    void performMkDir() {
        String name = JOptionPane.showInputDialog(this, "Enter name for new directory:", "Make Directory", JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.trim().isEmpty()) {
            return;
        }

        File newDir = new File(activePane.getCurrentDir(), name.trim());
        if (newDir.exists()) {
            JOptionPane.showMessageDialog(this, "A file or directory with that name already exists.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (newDir.mkdirs()) {
            activePane.refresh();
        } else {
            JOptionPane.showMessageDialog(this, "Failed to create directory.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    void performDelete() {
        File fileToDelete = activePane.getSelectedFile();
        if (fileToDelete == null) {
            JOptionPane.showMessageDialog(this, "No file selected to delete.", "Delete", JOptionPane.WARNING_MESSAGE);
            return;
        }

        boolean trashSupported = false;
        try {
            if (Desktop.isDesktopSupported()) {
                trashSupported = Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH);
            }
        } catch (Exception ex) {
            trashSupported = false;
        }

        Object[] options;
        if (trashSupported) {
            options = new Object[]{"Recycle Bin", "Delete Permanently", "Cancel"};
        } else {
            options = new Object[]{"Delete Permanently", "Cancel"};
        }

        int choice = JOptionPane.showOptionDialog(this,
                "Are you sure you want to delete this file/folder?\n" + fileToDelete.getAbsolutePath(),
                "Confirm Delete",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
                options,
                options[0]);

        if (choice == -1 || options[choice].equals("Cancel")) {
            return;
        }

        boolean moveToTrash = options[choice].equals("Recycle Bin");

        new Thread(() -> {
            try {
                if (moveToTrash) {
                    try {
                        Desktop.getDesktop().moveToTrash(fileToDelete);
                    } catch (Exception ex) {
                        SwingUtilities.invokeLater(() -> {
                            int confirm = JOptionPane.showConfirmDialog(this,
                                    "Failed to move to Recycle Bin. Delete permanently instead?",
                                    "Delete",
                                    JOptionPane.YES_NO_OPTION,
                                    JOptionPane.WARNING_MESSAGE);
                            if (confirm == JOptionPane.YES_OPTION) {
                                new Thread(() -> {
                                    try {
                                        deleteRecursive(fileToDelete);
                                        SwingUtilities.invokeLater(() -> {
                                            activePane.refresh();
                                            getInactivePane().refresh();
                                        });
                                    } catch (Exception e) {
                                        SwingUtilities.invokeLater(() -> {
                                            JOptionPane.showMessageDialog(this, "Delete failed: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                                        });
                                    }
                                }).start();
                            }
                        });
                        return;
                    }
                } else {
                    deleteRecursive(fileToDelete);
                }
                SwingUtilities.invokeLater(() -> {
                    activePane.refresh();
                    getInactivePane().refresh();
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, "Delete failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
    }

    private void deleteRecursive(File file) throws IOException {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        if (!file.delete()) {
            throw new IOException("Failed to delete: " + file.getAbsolutePath());
        }
    }

    void performRefresh() {
        activePane.refresh();
    }
}

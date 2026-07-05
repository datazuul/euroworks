package com.datazuul.euroworks.apps;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;

public class EuroManager extends EuroAppFrame {

    private final JTree tree;
    private final DefaultMutableTreeNode virtualRoot;
    private final JList<FileListEntry> fileList;
    private final DefaultListModel<FileListEntry> listModel;
    private final JLabel statusLabel;
    private File currentDir;

    // Custom retro vector icons
    private static final Icon FOLDER_ICON = new FolderIcon();
    private static final Icon FILE_ICON = new FileIcon();

    public EuroManager() {
        super("EuroManager (File Manager)");
        setSize(600, 450);

        // Initialize Tree Components (Left Panel) with a hidden virtual root for drives
        virtualRoot = new DefaultMutableTreeNode("Computer");
        DefaultTreeModel treeModel = new DefaultTreeModel(virtualRoot);
        tree = new JTree(treeModel);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new TreeRenderer());

        // Find system drives and populate asynchronously

        // Lazy-loading tree expansion
        tree.addTreeWillExpandListener(new TreeWillExpandListener() {
            @Override
            public void treeWillExpand(TreeExpansionEvent event) {
                Object lastComponent = event.getPath().getLastPathComponent();
                if (lastComponent instanceof FileTreeNode node) {
                    node.populateChildren(treeModel);
                }
            }

            @Override
            public void treeWillCollapse(TreeExpansionEvent event) {}
        });

        // Tree selection updates the file list
        tree.addTreeSelectionListener(e -> {
            Object selectedNode = tree.getLastSelectedPathComponent();
            if (selectedNode instanceof FileTreeNode node) {
                updateFileList(node.getFile());
            }
        });

        // Initialize List Components (Right Panel)
        listModel = new DefaultListModel<>();
        fileList = new JList<>(listModel);
        fileList.setCellRenderer(new FileListRenderer());

        // Double-click navigation on folders in the list
        fileList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    FileListEntry selectedEntry = fileList.getSelectedValue();
                    if (selectedEntry != null && selectedEntry.isDirectory()) {
                        navigateToDirectory(selectedEntry.getFile());
                    }
                }
            }
        });

        // Split Pane Layout
        JScrollPane treeScrollPane = new JScrollPane(tree);
        JScrollPane listScrollPane = new JScrollPane(fileList);
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treeScrollPane, listScrollPane);
        splitPane.setDividerLocation(200);

        // Status bar
        statusLabel = new JLabel("Ready");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(3, 5, 3, 5));

        // Assemble panels
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(splitPane, BorderLayout.CENTER);
        mainPanel.add(statusLabel, BorderLayout.SOUTH);
        setContentPane(mainPanel);

        // 1. Add C: drive (harddisk) immediately to the tree in the EDT
        File cDrive = new File("C:\\");
        FileTreeNode cDriveNode = new FileTreeNode(cDrive);
        virtualRoot.add(cDriveNode);
        treeModel.reload(virtualRoot);

        // Auto-select C: drive immediately
        TreePath cPath = new TreePath(new Object[]{virtualRoot, cDriveNode});
        tree.setSelectionPath(cPath);
        tree.scrollPathToVisible(cPath);
        updateFileList(cDrive);

        // Pre-populate C: drive level asynchronously
        cDriveNode.populateChildren(treeModel);

        // 2. Scan for other drives asynchronously in a background thread with a delay
        Timer rootsTimer = new Timer(1500, e -> {
            new Thread(() -> {
                File[] roots = null;
                try {
                    roots = File.listRoots();
                } catch (Exception ex) {
                    // ignore
                }

                final File[] finalRoots = roots;
                if (finalRoots != null) {
                    for (File root : finalRoots) {
                        String path = root.getAbsolutePath();
                        if (path.toLowerCase().startsWith("c:")) {
                            continue; // Already added C:
                        }

                        // Add the drive to the tree immediately on the EDT
                        SwingUtilities.invokeLater(() -> {
                            // Check duplicates
                            boolean exists = false;
                            FileTreeNode existingNode = null;
                            for (int i = 0; i < virtualRoot.getChildCount(); i++) {
                                if (virtualRoot.getChildAt(i) instanceof FileTreeNode node) {
                                    if (node.getFile().getAbsolutePath().equalsIgnoreCase(path)) {
                                        exists = true;
                                        existingNode = node;
                                        break;
                                    }
                                }
                            }

                            final FileTreeNode driveNode;
                            if (!exists) {
                                driveNode = new FileTreeNode(root);
                                virtualRoot.add(driveNode);
                                treeModel.reload(virtualRoot);
                            } else {
                                driveNode = existingNode;
                            }

                            // Start a parallel thread to test drive accessibility
                            new Thread(() -> {
                                boolean working = false;
                                try {
                                    File[] testList = root.listFiles();
                                    if (testList != null) {
                                        working = true;
                                    }
                                } catch (Exception ex) {
                                    working = false;
                                }

                                final boolean finalWorking = working;
                                SwingUtilities.invokeLater(() -> {
                                    if (!finalWorking) {
                                        driveNode.setBroken(true);
                                        treeModel.nodeChanged(driveNode);
                                    }
                                });
                            }).start();
                        });
                    }
                }
            }).start();
        });
        rootsTimer.setRepeats(false);
        rootsTimer.start();
    }

    private void updateFileList(File dir) {
        currentDir = dir;
        listModel.clear();
        statusLabel.setText("Loading directory: " + dir.getAbsolutePath() + "...");

        new Thread(() -> {
            File[] files = null;
            java.util.List<FileListEntry> entries = new java.util.ArrayList<>();
            boolean success = false;
            try {
                files = dir.listFiles();
                if (files != null) {
                    success = true;
                    for (File f : files) {
                        if (!f.getName().startsWith(".") && !f.isHidden()) {
                            entries.add(new FileListEntry(f));
                        }
                    }
                    // Sort: directories first, then files alphabetically
                    entries.sort((e1, e2) -> {
                        if (e1.isDirectory() && !e2.isDirectory()) return -1;
                        if (!e1.isDirectory() && e2.isDirectory()) return 1;
                        return e1.getFile().getName().compareToIgnoreCase(e2.getFile().getName());
                    });
                }
            } catch (Exception e) {
                success = false;
            }

            final boolean finalSuccess = success;
            SwingUtilities.invokeLater(() -> {
                if (currentDir != dir) return;
                listModel.clear();
                if (finalSuccess) {
                    for (FileListEntry entry : entries) {
                        listModel.addElement(entry);
                    }
                    statusLabel.setText("Directory: " + dir.getAbsolutePath() + " (" + listModel.size() + " items)");
                } else {
                    statusLabel.setText("Access Denied or Inaccessible: " + dir.getAbsolutePath());
                }
            });
        }).start();
    }

    private void navigateToDirectory(File dir) {
        FileTreeNode node = findTreeNodeForFile(dir);
        if (node != null) {
            TreePath path = new TreePath(node.getPath());
            tree.setSelectionPath(path);
            tree.scrollPathToVisible(path);
        } else {
            updateFileList(dir);
        }
    }

    private FileTreeNode findTreeNodeForFile(File file) {
        TreeNode root = (TreeNode) tree.getModel().getRoot();
        for (int i = 0; i < root.getChildCount(); i++) {
            TreeNode child = root.getChildAt(i);
            if (child instanceof FileTreeNode driveNode) {
                String filePath = file.getAbsolutePath().toLowerCase();
                String drivePath = driveNode.getFile().getAbsolutePath().toLowerCase();
                if (filePath.startsWith(drivePath)) {
                    FileTreeNode result = findTreeNodeForFile(driveNode, file);
                    if (result != null) return result;
                }
            }
        }
        return null;
    }

    private FileTreeNode findTreeNodeForFile(FileTreeNode current, File file) {
        if (current.getFile().getAbsolutePath().equalsIgnoreCase(file.getAbsolutePath())) {
            return current;
        }
        current.populateChildren((DefaultTreeModel) tree.getModel(), true);
        for (int i = 0; i < current.getChildCount(); i++) {
            TreeNode child = current.getChildAt(i);
            if (child instanceof FileTreeNode) {
                FileTreeNode result = findTreeNodeForFile((FileTreeNode) child, file);
                if (result != null) return result;
            }
        }
        return null;
    }

    // --- Inner Helper Classes ---

    private static class FileTreeNode extends DefaultMutableTreeNode {
        private final File file;
        private boolean populated = false;
        private boolean isBroken = false;

        public FileTreeNode(File file) {
            super(file);
            this.file = file;
            if (file.isDirectory()) {
                add(new DefaultMutableTreeNode("Loading..."));
            }
        }

        public File getFile() {
            return file;
        }

        public boolean isBroken() {
            return isBroken;
        }

        public void setBroken(boolean val) {
            this.isBroken = val;
        }

        @Override
        public String toString() {
            String name = file.getName();
            String path = name.isEmpty() ? file.getPath() : name;
            if (isBroken) {
                return "[Broken] " + path;
            }
            return path;
        }

        @Override
        public boolean isLeaf() {
            return false;
        }

        public void populateChildren(DefaultTreeModel model) {
            populateChildren(model, false);
        }

        public void populateChildren(DefaultTreeModel model, boolean sync) {
            if (populated) return;

            if (sync) {
                this.removeAllChildren();
                try {
                    File[] children = file.listFiles();
                    if (children != null) {
                        java.util.Arrays.sort(children, (f1, f2) -> f1.getName().compareToIgnoreCase(f2.getName()));
                        for (File child : children) {
                            if (child.isDirectory() && !child.getName().startsWith(".")) {
                                add(new FileTreeNode(child));
                            }
                        }
                        isBroken = false;
                    } else {
                        isBroken = true;
                    }
                } catch (Exception e) {
                    isBroken = true;
                }
                populated = true;
                if (model != null) {
                    model.nodeStructureChanged(this);
                }
            } else {
                new Thread(() -> {
                    File[] children = null;
                    boolean success = false;
                    try {
                        children = file.listFiles();
                        success = (children != null);
                    } catch (Exception e) {
                        success = false;
                    }

                    final File[] finalChildren = children;
                    final boolean finalSuccess = success;
                    SwingUtilities.invokeLater(() -> {
                        this.removeAllChildren();
                        if (finalSuccess) {
                            java.util.Arrays.sort(finalChildren, (f1, f2) -> f1.getName().compareToIgnoreCase(f2.getName()));
                            for (File child : finalChildren) {
                                if (child.isDirectory() && !child.getName().startsWith(".")) {
                                    add(new FileTreeNode(child));
                                }
                            }
                            isBroken = false;
                        } else {
                            isBroken = true;
                        }
                        populated = true;
                        if (model != null) {
                            model.nodeStructureChanged(this);
                        }
                    });
                }).start();
            }
        }
    }

    private static class TreeRenderer extends DefaultTreeCellRenderer {
        private static final Icon BROKEN_ICON = new BrokenDriveIcon();

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel,
                                                      boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            if (value instanceof FileTreeNode node) {
                if (node.isBroken()) {
                    setForeground(Color.RED);
                    setIcon(BROKEN_ICON);
                } else {
                    setIcon(FOLDER_ICON);
                }
            } else {
                setIcon(FOLDER_ICON);
            }
            return this;
        }
    }

    private static class FileListRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof FileListEntry entry) {
                setText(entry.getFile().getName());
                setIcon(entry.isDirectory() ? FOLDER_ICON : FILE_ICON);
            }
            return this;
        }
    }

    // --- Custom Retro Vector Icon Painters ---

    private static class FolderIcon implements Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            // Solid yellow folder back
            g2.setColor(new Color(230, 200, 80));
            g2.fillRect(x + 2, y + 4, 12, 9);
            g2.fillRect(x + 2, y + 2, 5, 2); // Tab

            // Folder borders
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
            // White document sheet
            g2.setColor(Color.WHITE);
            g2.fillRect(x + 3, y + 1, 10, 14);

            // Document outline
            g2.setColor(Color.BLACK);
            g2.drawRect(x + 3, y + 1, 10, 14);

            // Folded top-right corner
            g2.drawLine(x + 10, y + 1, x + 10, y + 4);
            g2.drawLine(x + 10, y + 4, x + 13, y + 4);

            // Mock text lines on the paper
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

    private static class BrokenDriveIcon implements Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            // Solid gray folder back
            g2.setColor(new Color(150, 150, 150));
            g2.fillRect(x + 2, y + 4, 12, 9);
            g2.fillRect(x + 2, y + 2, 5, 2); // Tab

            // Folder borders
            g2.setColor(Color.DARK_GRAY);
            g2.drawRect(x + 2, y + 4, 12, 9);
            g2.drawLine(x + 2, y + 2, x + 6, y + 2);
            g2.drawLine(x + 6, y + 2, x + 7, y + 4);

            // Red X overlay
            g2.setColor(Color.RED);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawLine(x + 4, y + 4, x + 12, y + 12);
            g2.drawLine(x + 12, y + 4, x + 4, y + 12);
            g2.dispose();
        }

        @Override
        public int getIconWidth() { return 16; }
        @Override
        public int getIconHeight() { return 16; }
    }

    private static class FileListEntry {
        private final File file;
        private final boolean isDirectory;

        public FileListEntry(File file) {
            this.file = file;
            this.isDirectory = file.isDirectory();
        }

        public File getFile() {
            return file;
        }

        public boolean isDirectory() {
            return isDirectory;
        }

        @Override
        public String toString() {
            return file.getName();
        }
    }
}

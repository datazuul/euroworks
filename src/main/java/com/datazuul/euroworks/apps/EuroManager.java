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
    private final JList<File> fileList;
    private final DefaultListModel<File> listModel;
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

        // Find system drives and populate
        FileTreeNode cDriveNode = null;
        File[] roots = File.listRoots();
        if (roots != null) {
            for (File root : roots) {
                FileTreeNode driveNode = new FileTreeNode(root);
                virtualRoot.add(driveNode);
                // Pre-populate drives level
                driveNode.populateChildren(treeModel);
                if (root.getPath().toLowerCase().startsWith("c:")) {
                    cDriveNode = driveNode;
                }
            }
        }

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
                    File selectedFile = fileList.getSelectedValue();
                    if (selectedFile != null && selectedFile.isDirectory()) {
                        navigateToDirectory(selectedFile);
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

        // Load initial state (Default to C: drive if detected, else the first available root)
        if (cDriveNode != null) {
            TreePath path = new TreePath(new Object[]{virtualRoot, cDriveNode});
            tree.setSelectionPath(path);
            tree.scrollPathToVisible(path);
            updateFileList(cDriveNode.getFile());
        } else if (virtualRoot.getChildCount() > 0) {
            FileTreeNode firstNode = (FileTreeNode) virtualRoot.getChildAt(0);
            TreePath path = new TreePath(new Object[]{virtualRoot, firstNode});
            tree.setSelectionPath(path);
            tree.scrollPathToVisible(path);
            updateFileList(firstNode.getFile());
        }
    }

    private void updateFileList(File dir) {
        currentDir = dir;
        listModel.clear();
        try {
            File[] files = dir.listFiles();
            if (files != null) {
                // Sort: directories first, then files alphabetically
                java.util.Arrays.sort(files, (f1, f2) -> {
                    if (f1.isDirectory() && !f2.isDirectory()) return -1;
                    if (!f1.isDirectory() && f2.isDirectory()) return 1;
                    return f1.getName().compareToIgnoreCase(f2.getName());
                });
                for (File f : files) {
                    if (!f.getName().startsWith(".") && !f.isHidden()) {
                        listModel.addElement(f);
                    }
                }
            }
        } catch (Exception e) {
            statusLabel.setText("Access Denied: " + dir.getAbsolutePath());
            return;
        }
        statusLabel.setText("Directory: " + dir.getAbsolutePath() + " (" + listModel.size() + " items)");
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
        current.populateChildren((DefaultTreeModel) tree.getModel());
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

        @Override
        public String toString() {
            String name = file.getName();
            return name.isEmpty() ? file.getPath() : name;
        }

        @Override
        public boolean isLeaf() {
            return !file.isDirectory();
        }

        public void populateChildren(DefaultTreeModel model) {
            if (populated) return;
            this.removeAllChildren();
            File[] children = file.listFiles();
            if (children != null) {
                java.util.Arrays.sort(children, (f1, f2) -> f1.getName().compareToIgnoreCase(f2.getName()));
                for (File child : children) {
                    if (child.isDirectory() && !child.getName().startsWith(".")) {
                        add(new FileTreeNode(child));
                    }
                }
            }
            populated = true;
            if (model != null) {
                model.nodeStructureChanged(this);
            }
        }
    }

    private static class TreeRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel,
                                                      boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            setIcon(FOLDER_ICON);
            return this;
        }
    }

    private static class FileListRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof File file) {
                setText(file.getName());
                setIcon(file.isDirectory() ? FOLDER_ICON : FILE_ICON);
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
}

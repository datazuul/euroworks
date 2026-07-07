package com.datazuul.euroworks.apps.euronotepad;

import com.datazuul.euroworks.apps.EuroAppFrame;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.DefaultEditorKit;
import javax.swing.undo.UndoManager;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.text.MessageFormat;
import java.util.Calendar;

public class EuroNotepad extends EuroAppFrame {

    private static final String UNNAMED = "[untitled]";

    private File file = null;
    private boolean modified = false;
    private boolean ignoreEdits = false;

    // Undo manager
    private final UndoManager undoManager;

    // GUI elements
    private JTextArea txtArea;
    private JPanel statusBar;
    private JLabel statusLabelLeft;
    private JLabel statusLabelRight;

    private JMenuItem mnuItemUndo;
    private JMenuItem mnuItemRedo;
    private JMenuItem mnuItemSave;

    private FindReplaceDialog findReplaceDialog = null;

    public EuroNotepad() {
        super("EuroNotepad");
        setSize(600, 450);

        undoManager = new UndoManager();

        buildGUI();
        setTextAreaText("");
        updateTitle();
    }

    private void buildGUI() {
        // Text area
        txtArea = new JTextArea();
        txtArea.setDragEnabled(true);
        txtArea.setMargin(new Insets(4, 4, 4, 4));
        txtArea.setFont(new Font("Monospaced", Font.PLAIN, 12));

        // Track changes
        txtArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                if (!ignoreEdits) {
                    modified = true;
                    updateTitle();
                }
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                if (!ignoreEdits) {
                    modified = true;
                    updateTitle();
                }
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                if (!ignoreEdits) {
                    modified = true;
                    updateTitle();
                }
            }
        });

        // Track undo edits
        txtArea.getDocument().addUndoableEditListener(e -> {
            if (!ignoreEdits) {
                undoManager.addEdit(e.getEdit());
                updateUndoRedoMenuItems();
            }
        });

        // Caret position status bar update
        txtArea.addCaretListener(e -> updateCaretStatus());

        JScrollPane scrollPane = new JScrollPane(txtArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

        // Status bar
        statusBar = new JPanel(new BorderLayout());
        EmptyBorder innerBorder = new EmptyBorder(3, 5, 3, 5);
        BevelBorder outerBorder = new BevelBorder(BevelBorder.LOWERED);

        statusLabelLeft = new JLabel("Ready");
        statusLabelLeft.setBorder(new CompoundBorder(outerBorder, innerBorder));
        statusLabelRight = new JLabel("Ln 1, Col 1");
        statusLabelRight.setBorder(new CompoundBorder(outerBorder, innerBorder));

        statusBar.add(statusLabelLeft, BorderLayout.CENTER);
        statusBar.add(statusLabelRight, BorderLayout.EAST);

        // Frame Layout
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(statusBar, BorderLayout.SOUTH);
        setContentPane(mainPanel);

        // Menu Bar
        setJMenuBar(buildMenuBar());
    }

    private JMenuBar buildMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        menuBar.add(buildFileMenu());
        menuBar.add(buildEditMenu());
        menuBar.add(buildFormatMenu());
        menuBar.add(buildViewMenu());
        menuBar.add(buildHelpMenu());

        return menuBar;
    }

    private JMenu buildFileMenu() {
        JMenu mnuFile = new JMenu("File");
        mnuFile.setMnemonic(KeyEvent.VK_F);

        JMenuItem mnuItemNew = new JMenuItem("New", KeyEvent.VK_N);
        mnuItemNew.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, ActionEvent.CTRL_MASK));
        mnuItemNew.addActionListener(e -> doNew());

        JMenuItem mnuItemOpen = new JMenuItem("Open...", KeyEvent.VK_O);
        mnuItemOpen.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, ActionEvent.CTRL_MASK));
        mnuItemOpen.addActionListener(e -> doOpen());

        mnuItemSave = new JMenuItem("Save", KeyEvent.VK_S);
        mnuItemSave.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, ActionEvent.CTRL_MASK));
        mnuItemSave.addActionListener(e -> doSave());

        JMenuItem mnuItemSaveAs = new JMenuItem("Save As...", KeyEvent.VK_A);
        mnuItemSaveAs.addActionListener(e -> doSaveAs());

        JMenuItem mnuItemPrint = new JMenuItem("Print...", KeyEvent.VK_P);
        mnuItemPrint.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_P, ActionEvent.CTRL_MASK));
        mnuItemPrint.addActionListener(e -> doPrint());

        JMenuItem mnuItemClose = new JMenuItem("Close", KeyEvent.VK_C);
        mnuItemClose.addActionListener(e -> doDefaultCloseAction());

        mnuFile.add(mnuItemNew);
        mnuFile.add(mnuItemOpen);
        mnuFile.add(mnuItemSave);
        mnuFile.add(mnuItemSaveAs);
        mnuFile.addSeparator();
        mnuFile.add(mnuItemPrint);
        mnuFile.addSeparator();
        mnuFile.add(mnuItemClose);

        return mnuFile;
    }

    private JMenu buildEditMenu() {
        JMenu mnuEdit = new JMenu("Edit");
        mnuEdit.setMnemonic(KeyEvent.VK_E);

        mnuItemUndo = new JMenuItem("Undo", KeyEvent.VK_U);
        mnuItemUndo.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, ActionEvent.CTRL_MASK));
        mnuItemUndo.addActionListener(e -> {
            if (undoManager.canUndo()) {
                undoManager.undo();
                updateUndoRedoMenuItems();
            }
        });

        mnuItemRedo = new JMenuItem("Redo", KeyEvent.VK_R);
        mnuItemRedo.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Y, ActionEvent.CTRL_MASK));
        mnuItemRedo.addActionListener(e -> {
            if (undoManager.canRedo()) {
                undoManager.redo();
                updateUndoRedoMenuItems();
            }
        });

        updateUndoRedoMenuItems();

        JMenuItem mnuItemCut = new JMenuItem(txtArea.getActionMap().get(DefaultEditorKit.cutAction));
        mnuItemCut.setText("Cut");
        mnuItemCut.setMnemonic(KeyEvent.VK_T);
        mnuItemCut.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_X, ActionEvent.CTRL_MASK));

        JMenuItem mnuItemCopy = new JMenuItem(txtArea.getActionMap().get(DefaultEditorKit.copyAction));
        mnuItemCopy.setText("Copy");
        mnuItemCopy.setMnemonic(KeyEvent.VK_C);
        mnuItemCopy.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, ActionEvent.CTRL_MASK));

        JMenuItem mnuItemPaste = new JMenuItem(txtArea.getActionMap().get(DefaultEditorKit.pasteAction));
        mnuItemPaste.setText("Paste");
        mnuItemPaste.setMnemonic(KeyEvent.VK_P);
        mnuItemPaste.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_V, ActionEvent.CTRL_MASK));

        JMenuItem mnuItemDelete = new JMenuItem("Delete");
        mnuItemDelete.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0));
        mnuItemDelete.addActionListener(e -> txtArea.replaceSelection(""));

        JMenuItem mnuItemFind = new JMenuItem("Find/Replace...", KeyEvent.VK_F);
        mnuItemFind.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F, ActionEvent.CTRL_MASK));
        mnuItemFind.addActionListener(e -> openFindReplaceDialog());

        JMenuItem mnuItemFindNext = new JMenuItem("Find Next");
        mnuItemFindNext.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F3, 0));
        mnuItemFindNext.addActionListener(e -> {
            if (findReplaceDialog != null) {
                findReplaceDialog.findNext(true);
            } else {
                openFindReplaceDialog();
            }
        });

        JMenuItem mnuItemGoTo = new JMenuItem("Go To...", KeyEvent.VK_G);
        mnuItemGoTo.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_G, ActionEvent.CTRL_MASK));
        mnuItemGoTo.addActionListener(e -> doGoTo());

        JMenuItem mnuItemSelectAll = new JMenuItem("Select All", KeyEvent.VK_A);
        mnuItemSelectAll.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_A, ActionEvent.CTRL_MASK));
        mnuItemSelectAll.addActionListener(e -> txtArea.selectAll());

        JMenuItem mnuItemTimeDate = new JMenuItem("Time/Date");
        mnuItemTimeDate.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0));
        mnuItemTimeDate.addActionListener(e -> doTimeDate());

        mnuEdit.add(mnuItemUndo);
        mnuEdit.add(mnuItemRedo);
        mnuEdit.addSeparator();
        mnuEdit.add(mnuItemCut);
        mnuEdit.add(mnuItemCopy);
        mnuEdit.add(mnuItemPaste);
        mnuEdit.add(mnuItemDelete);
        mnuEdit.addSeparator();
        mnuEdit.add(mnuItemFind);
        mnuEdit.add(mnuItemFindNext);
        mnuEdit.add(mnuItemGoTo);
        mnuEdit.addSeparator();
        mnuEdit.add(mnuItemSelectAll);
        mnuEdit.addSeparator();
        mnuEdit.add(mnuItemTimeDate);

        return mnuEdit;
    }

    private JMenu buildFormatMenu() {
        JMenu mnuFormat = new JMenu("Format");
        mnuFormat.setMnemonic(KeyEvent.VK_O);

        JCheckBoxMenuItem mnuItemWordWrap = new JCheckBoxMenuItem("Word Wrap", false);
        mnuItemWordWrap.setMnemonic(KeyEvent.VK_W);
        mnuItemWordWrap.addActionListener(e -> {
            boolean wrap = mnuItemWordWrap.isSelected();
            txtArea.setLineWrap(wrap);
            txtArea.setWrapStyleWord(wrap);
        });

        JMenuItem mnuItemFont = new JMenuItem("Font...", KeyEvent.VK_F);
        mnuItemFont.addActionListener(e -> doChooseFont());

        JMenuItem mnuItemTextColor = new JMenuItem("Text Color...", KeyEvent.VK_T);
        mnuItemTextColor.addActionListener(e -> {
            Color c = JColorChooser.showDialog(this, "Choose Text Color", txtArea.getForeground());
            if (c != null) {
                txtArea.setForeground(c);
            }
        });

        JMenuItem mnuItemBgColor = new JMenuItem("Background Color...", KeyEvent.VK_B);
        mnuItemBgColor.addActionListener(e -> {
            Color c = JColorChooser.showDialog(this, "Choose Background Color", txtArea.getBackground());
            if (c != null) {
                txtArea.setBackground(c);
            }
        });

        mnuFormat.add(mnuItemWordWrap);
        mnuFormat.add(mnuItemFont);
        mnuFormat.addSeparator();
        mnuFormat.add(mnuItemTextColor);
        mnuFormat.add(mnuItemBgColor);

        return mnuFormat;
    }

    private JMenu buildViewMenu() {
        JMenu mnuView = new JMenu("View");
        mnuView.setMnemonic(KeyEvent.VK_V);

        JCheckBoxMenuItem mnuItemStatusBar = new JCheckBoxMenuItem("Status Bar", true);
        mnuItemStatusBar.setMnemonic(KeyEvent.VK_S);
        mnuItemStatusBar.addActionListener(e -> statusBar.setVisible(mnuItemStatusBar.isSelected()));

        mnuView.add(mnuItemStatusBar);

        return mnuView;
    }

    private JMenu buildHelpMenu() {
        JMenu mnuHelp = new JMenu("Help");
        mnuHelp.setMnemonic(KeyEvent.VK_H);

        JMenuItem mnuItemAbout = new JMenuItem("About EuroNotepad...", KeyEvent.VK_A);
        mnuItemAbout.addActionListener(e -> {
            JOptionPane.showMessageDialog(this,
                    "EuroNotepad v1.0\nPart of the EuroWorks suite.\nBuilt in 2026.",
                    "About EuroNotepad",
                    JOptionPane.INFORMATION_MESSAGE,
                    com.datazuul.euroworks.shell.EuroIconThemeManager.getIcon("document", 32, 32));
        });

        mnuHelp.add(mnuItemAbout);

        return mnuHelp;
    }

    private void updateUndoRedoMenuItems() {
        if (mnuItemUndo != null) mnuItemUndo.setEnabled(undoManager.canUndo());
        if (mnuItemRedo != null) mnuItemRedo.setEnabled(undoManager.canRedo());
    }

    private void updateCaretStatus() {
        try {
            int caretPos = txtArea.getCaretPosition();
            int line = txtArea.getLineOfOffset(caretPos);
            int col = caretPos - txtArea.getLineStartOffset(line);
            statusLabelRight.setText("Ln " + (line + 1) + ", Col " + (col + 1));
        } catch (Exception ex) {
            statusLabelRight.setText("Ln 1, Col 1");
        }
    }

    private void setTextAreaText(String text) {
        ignoreEdits = true;
        txtArea.setText(text);
        ignoreEdits = false;
        modified = false;
    }

    private void updateTitle() {
        String fileName = (file == null) ? UNNAMED : file.getName();
        String titlePrefix = modified ? "*" : "";
        setTitle(titlePrefix + fileName + " - EuroNotepad");
        if (statusLabelLeft != null) {
            statusLabelLeft.setText(file == null ? "New Document" : file.getAbsolutePath());
        }
    }

    private boolean confirmSaveIfModified() {
        if (!modified) return true;

        String filename = (file == null) ? "Untitled" : file.getName();
        int response = JOptionPane.showConfirmDialog(this,
                "The text in the " + filename + " file has changed.\nDo you want to save the changes?",
                "EuroNotepad",
                JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (response == JOptionPane.YES_OPTION) {
            return doSave();
        } else return response == JOptionPane.NO_OPTION;
    }

    private void doNew() {
        if (confirmSaveIfModified()) {
            file = null;
            setTextAreaText("");
            undoManager.discardAllEdits();
            updateUndoRedoMenuItems();
            updateTitle();
        }
    }

    private void doOpen() {
        if (!confirmSaveIfModified()) return;

        JFileChooser fileDialog = new JFileChooser();
        int response = fileDialog.showOpenDialog(this);
        if (response == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileDialog.getSelectedFile();
            try {
                byte[] bytes = java.nio.file.Files.readAllBytes(selectedFile.toPath());
                String text;
                try {
                    text = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                } catch (Exception e) {
                    text = new String(bytes); // default charset fallback
                }
                file = selectedFile;
                setTextAreaText(text);
                undoManager.discardAllEdits();
                updateUndoRedoMenuItems();
                updateTitle();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Could not open file: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private boolean doSave() {
        if (file == null) {
            return doSaveAs();
        }
        try {
            java.nio.file.Files.writeString(file.toPath(), txtArea.getText(), java.nio.charset.StandardCharsets.UTF_8);
            modified = false;
            updateTitle();
            return true;
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not save file: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private boolean doSaveAs() {
        JFileChooser fileDialog = new JFileChooser();
        int response = fileDialog.showSaveDialog(this);
        if (response == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileDialog.getSelectedFile();
            if (selectedFile.exists()) {
                int overwrite = JOptionPane.showConfirmDialog(this,
                        "File already exists. Do you want to replace it?",
                        "Confirm Save As",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);
                if (overwrite != JOptionPane.YES_OPTION) {
                    return false;
                }
            }
            file = selectedFile;
            return doSave();
        }
        return false;
    }

    private void doPrint() {
        String fileName = file == null ? UNNAMED : file.getName();
        try {
            txtArea.print(
                    new MessageFormat(fileName),
                    new MessageFormat("Page {0}"),
                    true,
                    null,
                    null,
                    true
            );
        } catch (java.awt.print.PrinterException ex) {
            JOptionPane.showMessageDialog(this, "Printing failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void doGoTo() {
        String input = JOptionPane.showInputDialog(this, "Line number:", "Go To Line", JOptionPane.QUESTION_MESSAGE);
        if (input != null) {
            try {
                int line = Integer.parseInt(input);
                if (line > 0) {
                    int lineCount = txtArea.getLineCount();
                    if (line <= lineCount) {
                        int offset = txtArea.getLineStartOffset(line - 1);
                        txtArea.setCaretPosition(offset);
                    } else {
                        JOptionPane.showMessageDialog(this, "Line number out of range", "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Invalid line number", "Error", JOptionPane.ERROR_MESSAGE);
            } catch (javax.swing.text.BadLocationException ex) {
                // ignore
            }
        }
    }

    private void doTimeDate() {
        Calendar now = Calendar.getInstance();
        int hour = now.get(Calendar.HOUR);
        if (hour == 0) hour = 12;
        int minute = now.get(Calendar.MINUTE);
        String paddedMinute = (minute < 10) ? ("0" + minute) : String.valueOf(minute);
        String amPm = (now.get(Calendar.AM_PM) == Calendar.AM) ? "AM" : "PM";

        int month = now.get(Calendar.MONTH) + 1;
        int day = now.get(Calendar.DAY_OF_MONTH);
        int year = now.get(Calendar.YEAR);

        String dateStr = hour + ":" + paddedMinute + " " + amPm + " " + month + "/" + day + "/" + year;
        txtArea.insert(dateStr, txtArea.getCaretPosition());
    }

    private void doChooseFont() {
        Window parentWindow = SwingUtilities.getWindowAncestor(this);
        FontDialog fontDialog = new FontDialog(parentWindow, txtArea.getFont());
        fontDialog.setVisible(true);
        if (fontDialog.isApproved()) {
            txtArea.setFont(fontDialog.getSelectedFont());
        }
    }

    private void openFindReplaceDialog() {
        if (findReplaceDialog == null || !findReplaceDialog.isDisplayable()) {
            Window parentWindow = SwingUtilities.getWindowAncestor(this);
            findReplaceDialog = new FindReplaceDialog(parentWindow, txtArea);
        }
        findReplaceDialog.setVisible(true);
        findReplaceDialog.toFront();
    }

    @Override
    public void doDefaultCloseAction() {
        if (confirmSaveIfModified()) {
            if (findReplaceDialog != null) {
                findReplaceDialog.dispose();
            }
            super.doDefaultCloseAction();
        }
    }

    @Override
    public String getIconThemeKey() {
        return "document";
    }
}

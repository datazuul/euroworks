package com.datazuul.euroworks.apps.euronotepad;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FindReplaceDialog extends JDialog {

    private final JTextArea txtArea;
    private JComboBox<String> comboFind;
    private JComboBox<String> comboReplaceWith;

    private JRadioButton radioUp;
    private JRadioButton radioDown;

    private JCheckBox checkCaseSensitive;
    private JCheckBox checkRegEx;

    private JButton btnFind;
    private JButton btnReplace;
    private JButton btnReplaceAll;
    private JButton btnCancel;

    public FindReplaceDialog(Window owner, JTextArea txtArea) {
        super(owner, "Find/Replace", ModalityType.MODELESS);
        this.txtArea = txtArea;

        setContentPane(buildGUI());
        getRootPane().setDefaultButton(btnFind);

        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    private JPanel buildGUI() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        panel.add(buildSearchPanel(), BorderLayout.CENTER);
        panel.add(buildButtonPanel(), BorderLayout.EAST);

        return panel;
    }

    private JPanel buildSearchPanel() {
        JPanel searchPanel = new JPanel(new BorderLayout(5, 5));

        searchPanel.add(buildSearchInput(), BorderLayout.NORTH);
        searchPanel.add(buildSearchChoices(), BorderLayout.SOUTH);

        return searchPanel;
    }

    private JPanel buildSearchInput() {
        JPanel searchInput = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(4, 4, 4, 4);

        // Find Label & Combo
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.0;
        searchInput.add(new JLabel("Find:"), gbc);

        comboFind = new JComboBox<>();
        comboFind.setEditable(true);
        comboFind.setPreferredSize(new Dimension(200, 24));
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        searchInput.add(comboFind, gbc);

        // Replace Label & Combo
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0.0;
        searchInput.add(new JLabel("Replace with:"), gbc);

        comboReplaceWith = new JComboBox<>();
        comboReplaceWith.setEditable(true);
        comboReplaceWith.setPreferredSize(new Dimension(200, 24));
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        searchInput.add(comboReplaceWith, gbc);

        return searchInput;
    }

    private JPanel buildSearchChoices() {
        JPanel searchChoices = new JPanel(new GridLayout(1, 2, 5, 5));

        searchChoices.add(buildDirections());
        searchChoices.add(buildOptions());

        return searchChoices;
    }

    private JPanel buildDirections() {
        JPanel directionPanel = new JPanel(new GridLayout(2, 1));
        EmptyBorder innerBorder = new EmptyBorder(3, 3, 3, 3);
        TitledBorder outerBorder = BorderFactory.createTitledBorder("Direction");
        directionPanel.setBorder(new CompoundBorder(outerBorder, innerBorder));

        radioUp = new JRadioButton("Up");
        radioDown = new JRadioButton("Down");
        radioDown.setSelected(true);

        ButtonGroup directionGroup = new ButtonGroup();
        directionGroup.add(radioUp);
        directionGroup.add(radioDown);

        directionPanel.add(radioUp);
        directionPanel.add(radioDown);

        return directionPanel;
    }

    private JPanel buildOptions() {
        JPanel optionsPanel = new JPanel(new GridLayout(2, 1));
        EmptyBorder innerBorder = new EmptyBorder(3, 3, 3, 3);
        TitledBorder outerBorder = BorderFactory.createTitledBorder("Options");
        optionsPanel.setBorder(new CompoundBorder(outerBorder, innerBorder));

        checkCaseSensitive = new JCheckBox("Case Sensitive");
        checkRegEx = new JCheckBox("Regular Expressions");

        optionsPanel.add(checkCaseSensitive);
        optionsPanel.add(checkRegEx);

        return optionsPanel;
    }

    private JPanel buildButtonPanel() {
        JPanel buttonPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(2, 5, 2, 5);
        gbc.gridx = 0;

        btnFind = new JButton("Find Next");
        btnFind.addActionListener(e -> findNext(true));
        gbc.gridy = 0;
        buttonPanel.add(btnFind, gbc);

        btnReplace = new JButton("Replace");
        btnReplace.addActionListener(e -> replace());
        gbc.gridy = 1;
        buttonPanel.add(btnReplace, gbc);

        btnReplaceAll = new JButton("Replace All");
        btnReplaceAll.addActionListener(e -> replaceAll());
        gbc.gridy = 2;
        buttonPanel.add(btnReplaceAll, gbc);

        btnCancel = new JButton("Cancel");
        btnCancel.addActionListener(e -> dispose());
        gbc.gridy = 3;
        buttonPanel.add(btnCancel, gbc);

        return buttonPanel;
    }

    private void addSearchHistory(JComboBox<String> combo, String query) {
        if (query == null || query.isEmpty()) return;
        boolean exists = false;
        for (int i = 0; i < combo.getItemCount(); i++) {
            if (query.equals(combo.getItemAt(i))) {
                exists = true;
                break;
            }
        }
        if (!exists) {
            combo.addItem(query);
        }
    }

    public boolean findNext(boolean showMessageOnFail) {
        String query = (String) comboFind.getEditor().getItem();
        if (query == null || query.isEmpty()) {
            return false;
        }
        addSearchHistory(comboFind, query);

        String text = txtArea.getText();
        boolean down = radioDown.isSelected();
        boolean caseSensitive = checkCaseSensitive.isSelected();
        boolean useRegex = checkRegEx.isSelected();

        int startPos = -1;
        int endPos = -1;

        if (useRegex) {
            try {
                int flags = caseSensitive ? 0 : (Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
                Pattern pattern = Pattern.compile(query, flags);
                Matcher matcher = pattern.matcher(text);

                if (down) {
                    // Search Down starting from current selection end
                    int searchStart = txtArea.getSelectionEnd();
                    if (matcher.find(searchStart)) {
                        startPos = matcher.start();
                        endPos = matcher.end();
                    }
                } else {
                    // Search Up starting before current selection start
                    int searchEnd = txtArea.getSelectionStart();
                    int lastStart = -1;
                    int lastEnd = -1;
                    while (matcher.find()) {
                        if (matcher.start() < searchEnd) {
                            lastStart = matcher.start();
                            lastEnd = matcher.end();
                        } else {
                            break;
                        }
                    }
                    startPos = lastStart;
                    endPos = lastEnd;
                }
            } catch (Exception ex) {
                if (showMessageOnFail) {
                    JOptionPane.showMessageDialog(this, "Regex error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
                return false;
            }
        } else {
            // Literal search
            String docText = caseSensitive ? text : text.toLowerCase();
            String searchTerm = caseSensitive ? query : query.toLowerCase();

            if (down) {
                int searchStart = txtArea.getSelectionEnd();
                startPos = docText.indexOf(searchTerm, searchStart);
                if (startPos != -1) {
                    endPos = startPos + searchTerm.length();
                }
            } else {
                int searchStart = txtArea.getSelectionStart() - 1;
                if (searchStart >= 0) {
                    startPos = docText.lastIndexOf(searchTerm, searchStart);
                    if (startPos != -1) {
                        endPos = startPos + searchTerm.length();
                    }
                }
            }
        }

        if (startPos != -1) {
            txtArea.requestFocusInWindow();
            txtArea.select(startPos, endPos);
            return true;
        } else {
            if (showMessageOnFail) {
                JOptionPane.showMessageDialog(this, "Cannot find \"" + query + "\"", "Notepad", JOptionPane.INFORMATION_MESSAGE);
            }
            return false;
        }
    }

    private void replace() {
        String query = (String) comboFind.getEditor().getItem();
        if (query == null || query.isEmpty()) return;

        String replaceWith = (String) comboReplaceWith.getEditor().getItem();
        if (replaceWith == null) replaceWith = "";
        addSearchHistory(comboReplaceWith, replaceWith);

        // Check if the current selection matches the find query
        String selection = txtArea.getSelectedText();
        boolean isMatch = false;

        if (selection != null) {
            boolean caseSensitive = checkCaseSensitive.isSelected();
            boolean useRegex = checkRegEx.isSelected();
            if (useRegex) {
                try {
                    int flags = caseSensitive ? 0 : (Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
                    Pattern pattern = Pattern.compile(query, flags);
                    isMatch = pattern.matcher(selection).matches();
                } catch (Exception e) {
                    // Ignore regex error
                }
            } else {
                if (caseSensitive) {
                    isMatch = selection.equals(query);
                } else {
                    isMatch = selection.equalsIgnoreCase(query);
                }
            }
        }

        if (isMatch) {
            txtArea.replaceSelection(replaceWith);
        }
        findNext(true);
    }

    private void replaceAll() {
        String query = (String) comboFind.getEditor().getItem();
        if (query == null || query.isEmpty()) return;

        String replaceWith = (String) comboReplaceWith.getEditor().getItem();
        if (replaceWith == null) replaceWith = "";

        addSearchHistory(comboFind, query);
        addSearchHistory(comboReplaceWith, replaceWith);

        String text = txtArea.getText();
        boolean caseSensitive = checkCaseSensitive.isSelected();
        boolean useRegex = checkRegEx.isSelected();

        String newText;
        if (useRegex) {
            try {
                int flags = caseSensitive ? 0 : (Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
                Pattern pattern = Pattern.compile(query, flags);
                newText = pattern.matcher(text).replaceAll(replaceWith);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Regex error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
        } else {
            // Replace literal occurrences
            if (caseSensitive) {
                newText = text.replace(query, replaceWith);
            } else {
                // Regex-based replace to support case-insensitivity
                String patternStr = Pattern.quote(query);
                Pattern pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
                newText = pattern.matcher(text).replaceAll(Matcher.quoteReplacement(replaceWith));
            }
        }

        if (!text.equals(newText)) {
            txtArea.setText(newText);
        } else {
            JOptionPane.showMessageDialog(this, "Cannot find \"" + query + "\"", "Notepad", JOptionPane.INFORMATION_MESSAGE);
        }
    }
}

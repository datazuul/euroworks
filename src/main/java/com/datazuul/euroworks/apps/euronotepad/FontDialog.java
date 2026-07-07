package com.datazuul.euroworks.apps.euronotepad;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;

public class FontDialog extends JDialog {

    private final JList<String> listFamily;
    private final JList<String> listStyle;
    private final JList<String> listSize;

    private final JTextField tfFamily;
    private final JTextField tfStyle;
    private final JTextField tfSize;

    private final JLabel lblPreview;

    private Font selectedFont;
    private boolean approved = false;

    private static final String[] STYLE_NAMES = {"Regular", "Bold", "Italic", "Bold Italic"};
    private static final int[] STYLE_VALUES = {Font.PLAIN, Font.BOLD, Font.ITALIC, Font.BOLD | Font.ITALIC};
    private static final String[] SIZE_NAMES = {"8", "9", "10", "11", "12", "14", "16", "18", "20", "22", "24", "26", "28", "36", "48", "72"};

    public FontDialog(Window owner, Font initialFont) {
        super(owner, "Font", ModalityType.APPLICATION_MODAL);
        this.selectedFont = initialFont;

        // Retrieve system fonts
        String[] families = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();

        // Build UI elements
        tfFamily = new JTextField(12);
        tfFamily.setEditable(false);
        listFamily = new JList<>(families);
        JScrollPane scrollFamily = new JScrollPane(listFamily);
        scrollFamily.setPreferredSize(new Dimension(150, 150));

        tfStyle = new JTextField(8);
        tfStyle.setEditable(false);
        listStyle = new JList<>(STYLE_NAMES);
        JScrollPane scrollStyle = new JScrollPane(listStyle);
        scrollStyle.setPreferredSize(new Dimension(100, 150));

        tfSize = new JTextField(5);
        tfSize.setEditable(false);
        listSize = new JList<>(SIZE_NAMES);
        JScrollPane scrollSize = new JScrollPane(listSize);
        scrollSize.setPreferredSize(new Dimension(60, 150));

        lblPreview = new JLabel("AaBbYyZz", SwingConstants.CENTER);
        lblPreview.setPreferredSize(new Dimension(300, 70));
        TitledBorder previewBorder = BorderFactory.createTitledBorder("Preview");
        lblPreview.setBorder(BorderFactory.createCompoundBorder(
                previewBorder,
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));

        // Set initial values
        setSelection(initialFont);

        // Selection listeners
        ListSelectionListener listener = new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                updateSelectedFont();
            }
        };
        listFamily.addListSelectionListener(listener);
        listStyle.addListSelectionListener(listener);
        listSize.addListSelectionListener(listener);

        // Layout panels
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel listsPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        gbc.weighty = 0.0;
        gbc.insets = new Insets(2, 2, 2, 2);

        // Family Column
        gbc.gridx = 0; gbc.gridy = 0; listsPanel.add(new JLabel("Font:"), gbc);
        gbc.gridy = 1; listsPanel.add(tfFamily, gbc);
        gbc.gridy = 2; gbc.weighty = 1.0; listsPanel.add(scrollFamily, gbc);

        // Style Column
        gbc.gridx = 1; gbc.gridy = 0; gbc.weighty = 0.0; listsPanel.add(new JLabel("Font style:"), gbc);
        gbc.gridy = 1; listsPanel.add(tfStyle, gbc);
        gbc.gridy = 2; gbc.weighty = 1.0; listsPanel.add(scrollStyle, gbc);

        // Size Column
        gbc.gridx = 2; gbc.gridy = 0; gbc.weighty = 0.0; listsPanel.add(new JLabel("Size:"), gbc);
        gbc.gridy = 1; listsPanel.add(tfSize, gbc);
        gbc.gridy = 2; gbc.weighty = 1.0; listsPanel.add(scrollSize, gbc);

        mainPanel.add(listsPanel, BorderLayout.CENTER);

        // Bottom area (Preview & Buttons)
        JPanel bottomPanel = new JPanel(new BorderLayout(10, 10));
        bottomPanel.add(lblPreview, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnOK = new JButton("OK");
        btnOK.addActionListener(e -> {
            approved = true;
            dispose();
        });
        JButton btnCancel = new JButton("Cancel");
        btnCancel.addActionListener(e -> {
            approved = false;
            dispose();
        });
        buttonPanel.add(btnOK);
        buttonPanel.add(btnCancel);
        bottomPanel.add(buttonPanel, BorderLayout.SOUTH);

        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);
        pack();
        setLocationRelativeTo(owner);
    }

    private void setSelection(Font font) {
        // Set family
        String family = font.getFamily();
        listFamily.setSelectedValue(family, true);
        if (listFamily.getSelectedIndex() == -1) {
            listFamily.setSelectedIndex(0);
        }
        tfFamily.setText(listFamily.getSelectedValue());

        // Set style
        int style = font.getStyle();
        int styleIdx = 0;
        for (int i = 0; i < STYLE_VALUES.length; i++) {
            if (STYLE_VALUES[i] == style) {
                styleIdx = i;
                break;
            }
        }
        listStyle.setSelectedIndex(styleIdx);
        tfStyle.setText(STYLE_NAMES[styleIdx]);

        // Set size
        String sizeStr = String.valueOf(font.getSize());
        listSize.setSelectedValue(sizeStr, true);
        if (listSize.getSelectedIndex() == -1) {
            listSize.setSelectedValue("12", true);
        }
        tfSize.setText(listSize.getSelectedValue());

        updatePreview();
    }

    private void updateSelectedFont() {
        String family = listFamily.getSelectedValue();
        if (family == null) family = "Dialog";
        tfFamily.setText(family);

        int styleIdx = listStyle.getSelectedIndex();
        if (styleIdx == -1) styleIdx = 0;
        tfStyle.setText(STYLE_NAMES[styleIdx]);
        int style = STYLE_VALUES[styleIdx];

        String sizeStr = listSize.getSelectedValue();
        if (sizeStr == null) sizeStr = "12";
        tfSize.setText(sizeStr);
        int size;
        try {
            size = Integer.parseInt(sizeStr);
        } catch (NumberFormatException e) {
            size = 12;
        }

        selectedFont = new Font(family, style, size);
        updatePreview();
    }

    private void updatePreview() {
        if (selectedFont != null) {
            lblPreview.setFont(selectedFont.deriveFont(selectedFont.getStyle(), Math.min(36, selectedFont.getSize())));
        }
    }

    public boolean isApproved() {
        return approved;
    }

    public Font getSelectedFont() {
        return selectedFont;
    }
}

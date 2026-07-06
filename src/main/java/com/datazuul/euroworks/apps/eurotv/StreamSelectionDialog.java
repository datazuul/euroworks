package com.datazuul.euroworks.apps.eurotv;

import javax.swing.*;
import java.awt.*;

public class StreamSelectionDialog extends JDialog {
    private final JList<EuroTvStream> listStreams;
    private final DefaultListModel<EuroTvStream> modelStreams;
    private final java.util.function.Consumer<EuroTvStream> playCallback;
    private final Runnable saveCallback;

    public StreamSelectionDialog(Window owner, DefaultListModel<EuroTvStream> model, 
                                 java.util.function.Consumer<EuroTvStream> playCallback,
                                 Runnable saveCallback) {
        super(owner, "Stream-Auswahl", ModalityType.APPLICATION_MODAL);
        this.modelStreams = model;
        this.playCallback = playCallback;
        this.saveCallback = saveCallback;

        setSize(450, 300);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(8, 8));

        // Create UI components
        listStreams = new JList<>(modelStreams);
        listStreams.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        
        JScrollPane scrollPane = new JScrollPane(listStreams);
        scrollPane.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(6, 6, 6, 6),
                scrollPane.getBorder()
        ));
        add(scrollPane, BorderLayout.CENTER);

        // Control Panel
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.Y_AXIS));
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(6, 0, 6, 6));

        JButton btnPlay = new JButton("Wiedergabe");
        btnPlay.setMaximumSize(new Dimension(120, 26));
        btnPlay.addActionListener(e -> {
            EuroTvStream selected = listStreams.getSelectedValue();
            if (selected != null) {
                playCallback.accept(selected);
                dispose();
            } else {
                JOptionPane.showMessageDialog(this, "Bitte wählen Sie einen Stream aus.", "Information", JOptionPane.INFORMATION_MESSAGE);
            }
        });

        JButton btnAdd = new JButton("Hinzufügen...");
        btnAdd.setMaximumSize(new Dimension(120, 26));
        btnAdd.addActionListener(e -> addNewStream());

        JButton btnDelete = new JButton("Löschen");
        btnDelete.setMaximumSize(new Dimension(120, 26));
        btnDelete.addActionListener(e -> deleteSelectedStream());

        JButton btnClose = new JButton("Schließen");
        btnClose.setMaximumSize(new Dimension(120, 26));
        btnClose.addActionListener(e -> dispose());

        buttonPanel.add(btnPlay);
        buttonPanel.add(Box.createVerticalStrut(6));
        buttonPanel.add(btnAdd);
        buttonPanel.add(Box.createVerticalStrut(6));
        buttonPanel.add(btnDelete);
        buttonPanel.add(Box.createVerticalGlue());
        buttonPanel.add(btnClose);

        add(buttonPanel, BorderLayout.EAST);
    }

    private void addNewStream() {
        JTextField txtName = new JTextField();
        JTextField txtUrl = new JTextField();
        Object[] fields = {
            "Kanal-Name:", txtName,
            "Stream- oder Mediathek-URL:", txtUrl
        };

        int option = JOptionPane.showConfirmDialog(this, fields, "Kanal hinzufügen", JOptionPane.OK_CANCEL_OPTION);
        if (option == JOptionPane.OK_OPTION) {
            String name = txtName.getText().trim();
            String url = txtUrl.getText().trim();

            if (!name.isEmpty() && !url.isEmpty()) {
                EuroTvStream stream = new EuroTvStream(name, url);
                modelStreams.addElement(stream);
                saveCallback.run();
            } else {
                JOptionPane.showMessageDialog(this, "Bitte füllen Sie beide Felder aus.", "Eingabefehler", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void deleteSelectedStream() {
        int index = listStreams.getSelectedIndex();
        if (index != -1) {
            modelStreams.remove(index);
            saveCallback.run();
        } else {
            JOptionPane.showMessageDialog(this, "Bitte wählen Sie einen Stream zum Löschen aus.", "Information", JOptionPane.INFORMATION_MESSAGE);
        }
    }
}

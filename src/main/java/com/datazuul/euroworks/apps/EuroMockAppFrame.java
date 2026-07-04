package com.datazuul.euroworks.apps;

import javax.swing.*;
import java.awt.*;

public class EuroMockAppFrame extends EuroAppFrame {

    public EuroMockAppFrame(String appName) {
        super(appName);

        // Create a local menu bar for the internal frame
        JMenuBar appMenuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");
        fileMenu.add(new JMenuItem("New"));
        fileMenu.add(new JMenuItem("Open..."));
        fileMenu.addSeparator();
        fileMenu.add(new JMenuItem("Close"));

        JMenu editMenu = new JMenu("Edit");
        editMenu.add(new JMenuItem("Cut"));
        editMenu.add(new JMenuItem("Copy"));
        editMenu.add(new JMenuItem("Paste"));

        appMenuBar.add(fileMenu);
        appMenuBar.add(editMenu);
        setJMenuBar(appMenuBar);

        // Content layout
        JPanel contentPanel = new JPanel(new BorderLayout());
        contentPanel.setBackground(Color.WHITE);

        // Center welcome area with border
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBackground(Color.WHITE);
        centerPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel infoLabel = new JLabel("Welcome to " + appName + " (Mock Application)", SwingConstants.CENTER);
        infoLabel.setFont(new Font("Monospaced", Font.BOLD, 14));
        centerPanel.add(infoLabel, BorderLayout.CENTER);

        contentPanel.add(centerPanel, BorderLayout.CENTER);

        // Status bar (full width)
        JPanel statusBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        statusBar.setBorder(BorderFactory.createEtchedBorder());
        statusBar.add(new JLabel("Ready"));
        contentPanel.add(statusBar, BorderLayout.SOUTH);

        setContentPane(contentPanel);
    }
}

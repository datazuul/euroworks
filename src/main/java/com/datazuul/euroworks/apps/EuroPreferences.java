package com.datazuul.euroworks.apps;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Toolkit;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDesktopPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;

import com.datazuul.euroworks.shell.EuroDesktopPane;

public class EuroPreferences extends EuroAppFrame {

    private final CardLayout cardLayout;
    private final JPanel cardsPanel;

    // Display Card components
    private final JComboBox<String> colorComboBox;
    private final Color[] colors = {
            new Color(0, 128, 128), // Retro Teal
            new Color(0, 70, 130), // Navy Blue
            new Color(128, 128, 128), // VGA Gray
            new Color(34, 112, 63), // Forest Green
            new Color(130, 40, 40) // Dark Red
    };
    private final String[] colorNames = { "Retro Teal", "Navy Blue", "VGA Gray", "Forest Green", "Dark Red" };

    // Mouse Card components
    private final JCheckBox outlineDragCheckBox;

    // Sound Card components
    private final JCheckBox enableSoundCheckBox;

    // Screensaver Card components
    private final JCheckBox enableScreensaverCheckBox;
    private final JComboBox<String> screensaverComboBox;
    private final JSpinner timeoutSpinner;

    public EuroPreferences() {
        super("EuroPreferences (System Settings)");
        setSize(520, 380);

        // Main Layout split: Sidebar (left) and Cards (right)
        JPanel sidebarPanel = new JPanel(new GridLayout(0, 1, 5, 5));
        sidebarPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JButton btnDisplay = new JButton("Display");
        JButton btnMouse = new JButton("Mouse");
        JButton btnSound = new JButton("Sound");
        JButton btnScreensaver = new JButton("Screensaver");
        JButton btnSystem = new JButton("System");

        sidebarPanel.add(btnDisplay);
        sidebarPanel.add(btnMouse);
        sidebarPanel.add(btnSound);
        sidebarPanel.add(btnScreensaver);
        sidebarPanel.add(btnSystem);

        // Cards Panel Setup
        cardLayout = new CardLayout();
        cardsPanel = new JPanel(cardLayout);
        cardsPanel.setBorder(BorderFactory.createEtchedBorder());

        // 1. Display Card
        JPanel displayCard = new JPanel(new GridBagLayout());
        displayCard.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        displayCard.add(new JLabel("Desktop Color:"), gbc);

        colorComboBox = new JComboBox<>(colorNames);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        displayCard.add(colorComboBox, gbc);

        // 2. Mouse Card
        JPanel mouseCard = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        outlineDragCheckBox = new JCheckBox("Use Outline Window Dragging", true);
        mouseCard.add(outlineDragCheckBox);

        // 3. Sound Card
        JPanel soundCard = new JPanel(new GridBagLayout());
        soundCard.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        enableSoundCheckBox = new JCheckBox("Enable PC Speaker Emulation", true);
        soundCard.add(enableSoundCheckBox, gbc);

        JButton testBeepButton = new JButton("Test System Beep");
        testBeepButton.addActionListener(e -> Toolkit.getDefaultToolkit().beep());
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.NONE;
        soundCard.add(testBeepButton, gbc);

        // 4. Screensaver Card
        JPanel screensaverCard = new JPanel(new GridBagLayout());
        screensaverCard.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        enableScreensaverCheckBox = new JCheckBox("Bildschirmschoner aktivieren", true);
        String[] screensavers = { "EuroPipes", "EuroMaze", "EuroBezier", "EuroStarfield", "Kein" };
        screensaverComboBox = new JComboBox<>(screensavers);

        SpinnerModel spinnerModel = new SpinnerNumberModel(10, 5, 3600, 5);
        timeoutSpinner = new JSpinner(spinnerModel);

        gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        screensaverCard.add(enableScreensaverCheckBox, gbc);

        gbc.gridy = 1;
        gbc.gridwidth = 1;
        gbc.weightx = 0.0;
        screensaverCard.add(new JLabel("Auswahl:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        screensaverCard.add(screensaverComboBox, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0.0;
        screensaverCard.add(new JLabel("Wartezeit (Sekunden):"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        screensaverCard.add(timeoutSpinner, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        gbc.weighty = 1.0;
        screensaverCard.add(Box.createVerticalGlue(), gbc);

        // 5. System Card
        JPanel systemCard = new JPanel(new GridLayout(0, 1, 5, 5));
        systemCard.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        systemCard.add(new JLabel("OS: " + System.getProperty("os.name") + " (" + System.getProperty("os.arch") + ")"));
        systemCard.add(new JLabel("Java Runtime: " + System.getProperty("java.version")));

        long totalMemory = Runtime.getRuntime().totalMemory() / (1024 * 1024);
        long freeMemory = Runtime.getRuntime().freeMemory() / (1024 * 1024);
        systemCard.add(new JLabel("Memory: " + (totalMemory - freeMemory) + "MB used / " + totalMemory + "MB total"));

        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        systemCard.add(new JLabel("Resolution: " + screenSize.width + "x" + screenSize.height));

        // Add cards
        cardsPanel.add(displayCard, "DISPLAY");
        cardsPanel.add(mouseCard, "MOUSE");
        cardsPanel.add(soundCard, "SOUND");
        cardsPanel.add(screensaverCard, "SCREENSAVER");
        cardsPanel.add(systemCard, "SYSTEM");

        // Wire category buttons
        btnDisplay.addActionListener(e -> cardLayout.show(cardsPanel, "DISPLAY"));
        btnMouse.addActionListener(e -> cardLayout.show(cardsPanel, "MOUSE"));
        btnSound.addActionListener(e -> cardLayout.show(cardsPanel, "SOUND"));
        btnScreensaver.addActionListener(e -> cardLayout.show(cardsPanel, "SCREENSAVER"));
        btnSystem.addActionListener(e -> cardLayout.show(cardsPanel, "SYSTEM"));

        // Bottom Action buttons
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        JButton applyButton = new JButton("Apply");
        applyButton.addActionListener(e -> applySettings());
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dispose());

        actionPanel.add(applyButton);
        actionPanel.add(closeButton);

        // Assemble Main Panel
        JPanel contentPanel = new JPanel(new BorderLayout());
        contentPanel.add(sidebarPanel, BorderLayout.WEST);
        contentPanel.add(cardsPanel, BorderLayout.CENTER);
        contentPanel.add(actionPanel, BorderLayout.SOUTH);

        setContentPane(contentPanel);

        // Read current drag mode from desktop pane on startup (if attached)
        SwingUtilities.invokeLater(this::syncCurrentState);
    }

    private void syncCurrentState() {
        JDesktopPane desktop = getDesktopPane();
        if (desktop != null) {
            outlineDragCheckBox.setSelected(desktop.getDragMode() == JDesktopPane.OUTLINE_DRAG_MODE);
            if (desktop instanceof EuroDesktopPane euroDesktop) {
                Color c = euroDesktop.getDesktopColor();
                for (int i = 0; i < colors.length; i++) {
                    if (colors[i].equals(c)) {
                        colorComboBox.setSelectedIndex(i);
                        break;
                    }
                }
                enableScreensaverCheckBox.setSelected(euroDesktop.isScreensaverEnabled());
                screensaverComboBox.setSelectedItem(euroDesktop.getScreensaverName());
                timeoutSpinner.setValue(euroDesktop.getScreensaverTimeoutSeconds());
            }
        }
    }

    private void applySettings() {
        JDesktopPane desktop = getDesktopPane();
        if (desktop != null) {
            // Apply mouse dragging preference
            boolean useOutline = outlineDragCheckBox.isSelected();
            desktop.setDragMode(useOutline ? JDesktopPane.OUTLINE_DRAG_MODE : JDesktopPane.LIVE_DRAG_MODE);

            // Apply display settings
            int index = colorComboBox.getSelectedIndex();
            if (desktop instanceof EuroDesktopPane euroDesktop) {
                if (index >= 0 && index < colors.length) {
                    euroDesktop.setDesktopColor(colors[index]);
                }
                // Apply screensaver settings
                euroDesktop.setScreensaverEnabled(enableScreensaverCheckBox.isSelected());
                euroDesktop.setScreensaverName((String) screensaverComboBox.getSelectedItem());
                euroDesktop.setScreensaverTimeoutSeconds((Integer) timeoutSpinner.getValue());
            } else {
                if (index >= 0 && index < colors.length) {
                    desktop.setBackground(colors[index]);
                    desktop.repaint();
                }
            }
        }
    }
}

package com.datazuul.euroworks.screensavers;

import javax.swing.*;

public class EuroPipes extends AbstractEuroScreensaver {

    private final EuroPipesCanvas canvas;
    private int maxPipes = 3;
    private String jointType = "Kugeln";
    private static final int DEFAULT_SPEED_MS = 50;

    public EuroPipes() {
        this(new EuroPipesCanvas());
    }

    // Private chaining constructor so canvas is fully built before super() runs
    private EuroPipes(EuroPipesCanvas canvas) {
        super("EuroPipes (Screensaver App)", canvas);
        this.canvas = canvas;
    }

    @Override
    protected void buildExtraMenuItems(JMenu dateiMenu, JPopupMenu popupMenu) {
        JMenuItem mClear = new JMenuItem("Löschen");
        mClear.addActionListener(e -> canvas.clearCanvas());
        dateiMenu.add(mClear);

        JMenuItem popClear = new JMenuItem("Löschen");
        popClear.addActionListener(e -> canvas.clearCanvas());
        popupMenu.add(popClear);
    }

    @Override
    protected JMenu buildSettingsMenu() {
        JMenu settingsMenu = new JMenu("Einstellungen");

        // Max Rohre
        JMenu maxMenu = new JMenu("Max Rohre");
        ButtonGroup maxGroup = new ButtonGroup();
        for (int c : new int[]{1, 2, 3, 5, 8}) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(String.valueOf(c), c == maxPipes);
            item.addActionListener(e -> { maxPipes = c; canvas.setMaxPipes(c); });
            maxGroup.add(item);
            maxMenu.add(item);
        }
        settingsMenu.add(maxMenu);

        // Verbindungen
        JMenu jointMenu = new JMenu("Verbindungen");
        ButtonGroup jointGroup = new ButtonGroup();
        for (String j : new String[]{"Kugeln", "Ellbogen"}) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(j, j.equals(jointType));
            item.addActionListener(e -> { jointType = j; canvas.setJointType(j); });
            jointGroup.add(item);
            jointMenu.add(item);
        }
        settingsMenu.add(jointMenu);

        settingsMenu.add(buildSpeedMenu(
                new String[]{"Schnell", "Normal", "Langsam"},
                new int[]{20, 50, 90},
                DEFAULT_SPEED_MS, canvas));

        return settingsMenu;
    }
}

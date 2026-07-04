package com.datazuul.euroworks.screensavers;

import javax.swing.*;

public class EuroMaze extends AbstractEuroScreensaver {

    private final EuroMazeCanvas canvas;
    private static final int DEFAULT_SPEED_MS = 40;

    public EuroMaze() {
        this(new EuroMazeCanvas());
    }

    private EuroMaze(EuroMazeCanvas canvas) {
        super("EuroMaze (3D Maze Screensaver)", canvas);
        this.canvas = canvas;
    }

    @Override
    protected void buildExtraMenuItems(JMenu dateiMenu, JPopupMenu popupMenu) {
        JMenuItem mReset = new JMenuItem("Neues Labyrinth");
        mReset.addActionListener(e -> canvas.resetMaze());
        dateiMenu.add(mReset);

        JMenuItem popReset = new JMenuItem("Neues Labyrinth");
        popReset.addActionListener(e -> canvas.resetMaze());
        popupMenu.add(popReset);
    }

    @Override
    protected JMenu buildSettingsMenu() {
        JMenu settingsMenu = new JMenu("Einstellungen");
        settingsMenu.add(buildSpeedMenu(
                new String[]{"Schnell", "Normal", "Langsam"},
                new int[]{25, 40, 70},
                DEFAULT_SPEED_MS, canvas));
        return settingsMenu;
    }
}

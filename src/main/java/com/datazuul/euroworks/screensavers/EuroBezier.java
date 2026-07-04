package com.datazuul.euroworks.screensavers;

import javax.swing.*;

public class EuroBezier extends AbstractEuroScreensaver {

    private final EuroBezierCanvas canvas;
    private static final int DEFAULT_SPEED_MS = 30;

    public EuroBezier() {
        this(new EuroBezierCanvas());
    }

    private EuroBezier(EuroBezierCanvas canvas) {
        super("EuroBezier (Bezier Screensaver)", canvas);
        this.canvas = canvas;
    }

    @Override
    protected void buildExtraMenuItems(JMenu dateiMenu, JPopupMenu popupMenu) {
        // No extra Datei items for Bezier
    }

    @Override
    protected JMenu buildSettingsMenu() {
        JMenu settingsMenu = new JMenu("Einstellungen");

        settingsMenu.add(buildSpeedMenu(
                new String[]{"Schnell", "Normal", "Langsam"},
                new int[]{16, 30, 60},
                DEFAULT_SPEED_MS, canvas));

        JCheckBoxMenuItem trailItem = new JCheckBoxMenuItem("Schwanz-Effekt (Trail Fade)", true);
        trailItem.addActionListener(e -> canvas.setTrailFade(trailItem.isSelected()));
        settingsMenu.add(trailItem);

        return settingsMenu;
    }
}

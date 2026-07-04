package com.datazuul.euroworks.screensavers;

import javax.swing.*;

/**
 * Starfield Screensaver wrapper. Manages the EuroStarfieldCanvas lifecycle,
 * fullscreen integration, and settings menu.
 */
public class EuroStarfield extends AbstractEuroScreensaver {

    private final EuroStarfieldCanvas canvas;
    private static final int DEFAULT_SPEED_MS = 30;

    public EuroStarfield() {
        this(new EuroStarfieldCanvas());
    }

    private EuroStarfield(EuroStarfieldCanvas canvas) {
        super("EuroStarfield (Starfield Screensaver)", canvas);
        this.canvas = canvas;
    }

    @Override
    protected void buildExtraMenuItems(JMenu dateiMenu, JPopupMenu popupMenu) {
        // No extra generic items needed
    }

    @Override
    protected JMenu buildSettingsMenu() {
        JMenu settingsMenu = new JMenu("Einstellungen");
        EuroStarfieldCanvas sCanvas = (EuroStarfieldCanvas) getCanvas();

        // 1. Frame rate speed (Timer delay)
        settingsMenu.add(buildSpeedMenu(
                new String[]{"Schnell", "Normal", "Langsam"},
                new int[]{16, 30, 60},
                DEFAULT_SPEED_MS, sCanvas));

        settingsMenu.addSeparator();

        // 2. Warp Speed Menu
        JMenu warpMenu = new JMenu("Warp-Geschwindigkeit");
        ButtonGroup warpGroup = new ButtonGroup();
        int[] speeds = {5, 10, 20, 35};
        String[] speedLabels = {"Impuls (Langsam)", "Standard (Normal)", "Warp-Geschwindigkeit", "Hyperspace"};
        int currentWarp = sCanvas.getWarpSpeed();

        for (int i = 0; i < speeds.length; i++) {
            final int wSpeed = speeds[i];
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(speedLabels[i], wSpeed == currentWarp);
            item.addActionListener(e -> sCanvas.setWarpSpeed(wSpeed));
            warpGroup.add(item);
            warpMenu.add(item);
        }
        settingsMenu.add(warpMenu);

        // 3. Star Count Menu
        JMenu countMenu = new JMenu("Anzahl Sterne");
        ButtonGroup countGroup = new ButtonGroup();
        int[] counts = {100, 300, 600, 1000, 5000};
        String[] countLabels = {"Wenig (100)", "Normal (300)", "Viele (600)", "Galaktisch (1000)", "Cosmic (5000)"};
        int currentCount = sCanvas.getNumStars();

        for (int i = 0; i < counts.length; i++) {
            final int starCount = counts[i];
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(countLabels[i], starCount == currentCount);
            item.addActionListener(e -> sCanvas.setNumStars(starCount));
            countGroup.add(item);
            countMenu.add(item);
        }
        settingsMenu.add(countMenu);

        settingsMenu.addSeparator();

        // 4. Flight Rotation Mode Menu
        JMenu rotationMenu = new JMenu("Flug-Modus (Kamera-Rollen)");
        ButtonGroup rotationGroup = new ButtonGroup();
        double[] rotSpeeds = {0.0, 0.005, 0.015};
        String[] rotLabels = {"Geradeaus (Keine Drehung)", "Rollen / Trudeln", "Hyperraum-Drift (Schnell)"};
        double currentRot = sCanvas.getRotationSpeed();

        for (int i = 0; i < rotSpeeds.length; i++) {
            final double rSpeed = rotSpeeds[i];
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(rotLabels[i], Math.abs(rSpeed - currentRot) < 0.0001);
            item.addActionListener(e -> sCanvas.setRotationSpeed(rSpeed));
            rotationGroup.add(item);
            rotationMenu.add(item);
        }
        settingsMenu.add(rotationMenu);

        return settingsMenu;
    }
}

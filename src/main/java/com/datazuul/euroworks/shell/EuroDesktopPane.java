package com.datazuul.euroworks.shell;

import com.datazuul.euroworks.apps.EuroPreferencesStore;
import javax.swing.JDesktopPane;
import java.awt.Color;
import java.awt.Graphics;

public class EuroDesktopPane extends JDesktopPane {

    private static final Color RETRO_TEAL = new Color(0, 128, 128);
    private Color customColor = RETRO_TEAL;

    private boolean screensaverEnabled = true;
    private String screensaverName = "EuroPipes";
    private int screensaverTimeoutSeconds = 60;

    public EuroDesktopPane() {
        // Load settings from persistent preferences store
        EuroPreferencesStore.load();

        // Apply color
        int colorIdx = EuroPreferencesStore.getDesktopColorIndex();
        Color[] colors = {
            new Color(0, 128, 128), // Retro Teal
            new Color(0, 70, 130),  // Navy Blue
            new Color(128, 128, 128),// VGA Gray
            new Color(34, 112, 63), // Forest Green
            new Color(130, 40, 40)  // Dark Red
        };
        if (colorIdx >= 0 && colorIdx < colors.length) {
            customColor = colors[colorIdx];
        }
        setBackground(customColor);

        // Apply dragging preference
        boolean outlineDrag = EuroPreferencesStore.isOutlineDragging();
        setDragMode(outlineDrag ? JDesktopPane.OUTLINE_DRAG_MODE : JDesktopPane.LIVE_DRAG_MODE);

        // Apply screensaver preferences
        screensaverEnabled = EuroPreferencesStore.isScreensaverEnabled();
        screensaverName = EuroPreferencesStore.getScreensaverName();
        screensaverTimeoutSeconds = EuroPreferencesStore.getScreensaverTimeout();
    }

    public boolean isScreensaverEnabled() {
        return screensaverEnabled;
    }

    public void setScreensaverEnabled(boolean val) {
        this.screensaverEnabled = val;
    }

    public String getScreensaverName() {
        return screensaverName;
    }

    public void setScreensaverName(String val) {
        this.screensaverName = val;
    }

    public int getScreensaverTimeoutSeconds() {
        return screensaverTimeoutSeconds;
    }

    public void setScreensaverTimeoutSeconds(int val) {
        this.screensaverTimeoutSeconds = val;
    }

    public void setDesktopColor(Color color) {
        this.customColor = color;
        setBackground(color);
        repaint();
    }

    public Color getDesktopColor() {
        return customColor;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        // Paint the background solid custom color
        g.setColor(customColor);
        g.fillRect(0, 0, getWidth(), getHeight());
    }
}

package com.datazuul.euroworks.shell;

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
        setBackground(customColor);
        // Ensure standard window dragging mode for better performance on retro styling
        setDragMode(JDesktopPane.OUTLINE_DRAG_MODE);
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

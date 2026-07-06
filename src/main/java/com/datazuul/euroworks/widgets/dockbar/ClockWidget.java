package com.datazuul.euroworks.widgets.dockbar;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import javax.swing.JPanel;
import javax.swing.Timer;

/**
 * Taskbar clock widget — GEOS/BreadBox retro style.
 * Renders a sunken inset box (like Win95 clock) showing HH:mm in 24-hour
 * format.
 */
public class ClockWidget extends JPanel {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final Color SILVER = new Color(192, 192, 192);
    private static final Color SHADOW = new Color(128, 128, 128);
    private static final Color DARK = new Color(64, 64, 64);
    private static final Color HILIGHT = Color.WHITE;

    private String timeText = "";

    public ClockWidget() {
        setOpaque(false);
        setBorder(null);
        setPreferredSize(new Dimension(60, 25));

        new Timer(1000, e -> refresh()).start();
        refresh();
    }

    private void refresh() {
        timeText = LocalTime.now().format(FMT);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_OFF); // pixel-perfect retro

        int w = getWidth(), h = getHeight();

        // Sunken inset border (Win95 style: dark top-left, white bottom-right)
        g2.setColor(SHADOW);
        g2.drawLine(0, 0, w - 2, 0); // top
        g2.drawLine(0, 0, 0, h - 2); // left
        g2.setColor(DARK);
        g2.drawLine(1, 1, w - 3, 1); // inner top
        g2.drawLine(1, 1, 1, h - 3); // inner left
        g2.setColor(HILIGHT);
        g2.drawLine(0, h - 1, w - 1, h - 1); // bottom
        g2.drawLine(w - 1, 0, w - 1, h - 1); // right
        g2.setColor(SILVER);
        g2.drawLine(1, h - 2, w - 2, h - 2); // inner bottom
        g2.drawLine(w - 2, 1, w - 2, h - 2); // inner right

        // Fill background
        g2.setColor(SILVER);
        g2.fillRect(2, 2, w - 4, h - 4);

        // Time text
        Font font = new Font("SansSerif", Font.PLAIN, 13);
        g2.setFont(font);
        g2.setColor(Color.BLACK);
        FontMetrics fm = g2.getFontMetrics(font);
        int tx = 2 + (w - 4 - fm.stringWidth(timeText)) / 2;
        int ty = 2 + (h - 4 + fm.getAscent() - fm.getDescent()) / 2;
        g2.drawString(timeText, tx, ty);

        g2.dispose();
    }
}

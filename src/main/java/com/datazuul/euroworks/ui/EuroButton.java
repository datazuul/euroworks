package com.datazuul.euroworks.ui;

import javax.swing.*;
import java.awt.*;

/**
 * EuroButton - A unified 3D-styled JButton designed to look like a classic Windows 95 button.
 * Implements raised/sunken borders, text shift on press, and proper disabled states.
 */
public class EuroButton extends JButton {

    private static final Color SILVER = new Color(212, 208, 200);
    private static final Color WHITE = Color.WHITE;
    private static final Color DARK_GRAY = new Color(128, 128, 128);
    private static final Color BLACK = Color.BLACK;

    public EuroButton() {
        this("");
    }

    public EuroButton(String text) {
        super(text);
        init();
    }

    public EuroButton(String text, Color bg) {
        super(text);
        init();
        setBackground(bg);
    }

    public EuroButton(String text, Color bg, Color fg) {
        super(text);
        init();
        setBackground(bg);
        setForeground(fg);
    }

    private void init() {
        setFont(new Font("SansSerif", Font.BOLD, 11));
        setBackground(SILVER);
        setForeground(Color.BLACK);
        setOpaque(true);
        setFocusPainted(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setMargin(new Insets(4, 10, 4, 10));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        boolean sunken = (getModel().isPressed() && getModel().isArmed()) || getModel().isSelected();
        boolean enabled = isEnabled();

        // 1. Draw Background
        g2.setColor(getBackground());
        g2.fillRect(0, 0, w, h);

        // 2. Draw 3D Bevel Border
        if (enabled) {
            if (sunken) {
                // Sunken 3D Border (classic Windows 95 double shadow bevel)
                g2.setColor(DARK_GRAY);
                g2.drawLine(0, 0, w - 2, 0); // top outer shadow
                g2.drawLine(0, 0, 0, h - 2); // left outer shadow

                g2.setColor(BLACK);
                g2.drawLine(1, 1, w - 3, 1); // top inner shadow
                g2.drawLine(1, 1, 1, h - 3); // left inner shadow

                g2.setColor(WHITE);
                g2.drawLine(0, h - 1, w - 1, h - 1); // bottom outer highlight
                g2.drawLine(w - 1, 0, w - 1, h - 1); // right outer highlight
            } else {
                // Raised 3D Border
                g2.setColor(WHITE);
                g2.drawLine(0, 0, w - 2, 0); // top outer highlight
                g2.drawLine(0, 0, 0, h - 2); // left outer highlight

                g2.setColor(SILVER);
                g2.drawLine(1, 1, w - 3, 1); // top inner highlight
                g2.drawLine(1, 1, 1, h - 3); // left inner highlight

                g2.setColor(DARK_GRAY);
                g2.drawLine(1, h - 2, w - 2, h - 2); // bottom inner shadow
                g2.drawLine(w - 2, 1, w - 2, h - 2); // right inner shadow

                g2.setColor(BLACK);
                g2.drawLine(0, h - 1, w - 1, h - 1); // bottom outer shadow
                g2.drawLine(w - 1, 0, w - 1, h - 1); // right outer shadow
            }
        } else {
            // Disabled Flat Border
            g2.setColor(DARK_GRAY);
            g2.drawRect(0, 0, w - 1, h - 1);
        }

        // 3. Paint Text and Icon (with 1px shift if pressed)
        int shift = sunken ? 1 : 0;

        // Calculate text and icon locations
        FontMetrics fm = g2.getFontMetrics(getFont());
        String text = getText();
        Icon icon = getIcon();

        int totalWidth = 0;
        int iconWidth = (icon != null) ? icon.getIconWidth() : 0;
        int textWidth = (text != null && !text.isEmpty()) ? fm.stringWidth(text) : 0;
        int gap = (iconWidth > 0 && textWidth > 0) ? getIconTextGap() : 0;

        totalWidth = iconWidth + gap + textWidth;

        int startX = (w - totalWidth) / 2 + shift;
        int centerY = h / 2 + shift;

        if (icon != null) {
            int iy = centerY - icon.getIconHeight() / 2;
            icon.paintIcon(this, g2, startX, iy);
        }

        if (textWidth > 0) {
            int tx = startX + iconWidth + gap;
            int ty = centerY + (fm.getAscent() - fm.getDescent()) / 2;
            if (enabled) {
                g2.setColor(getForeground());
                g2.drawString(text, tx, ty);
            } else {
                // Disabled text shadow effect
                g2.setColor(WHITE);
                g2.drawString(text, tx + 1, ty + 1);
                g2.setColor(DARK_GRAY);
                g2.drawString(text, tx, ty);
            }
        }

        g2.dispose();
    }
}

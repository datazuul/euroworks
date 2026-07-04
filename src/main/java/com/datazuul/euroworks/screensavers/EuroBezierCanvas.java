package com.datazuul.euroworks.screensavers;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Bezier screensaver canvas. Draws animated cubic Bezier curves that bounce
 * around the screen with color-cycling, similar to the classic Windows
 * Bezier screensaver.
 */
public class EuroBezierCanvas extends JPanel implements ScreensaverCanvas {

    private final Random rand = new Random();
    private final Timer loopTimer;
    private BufferedImage bufferImage = null;

    // Each curve is defined by 4 control points + velocities + current color
    private static final int NUM_CURVES = 5;
    private final BezierCurve[] curves = new BezierCurve[NUM_CURVES];

    // Configurable
    private int speedMs = 30;
    private boolean trailFade = true; // Fade old curves or keep black background

    // Hue cycling
    private float baseHue = 0.0f;

    private static class BezierCurve {
        double x0, y0, x1, y1, x2, y2, x3, y3;
        double vx0, vy0, vx1, vy1, vx2, vy2, vx3, vy3;
        Color color;

        BezierCurve(int w, int h, Random rand) {
            x0 = rand.nextInt(w); y0 = rand.nextInt(h);
            x1 = rand.nextInt(w); y1 = rand.nextInt(h);
            x2 = rand.nextInt(w); y2 = rand.nextInt(h);
            x3 = rand.nextInt(w); y3 = rand.nextInt(h);

            // Random velocities between 1.5 and 5 pixels/frame
            vx0 = (rand.nextDouble() * 3.5 + 1.5) * (rand.nextBoolean() ? 1 : -1);
            vy0 = (rand.nextDouble() * 3.5 + 1.5) * (rand.nextBoolean() ? 1 : -1);
            vx1 = (rand.nextDouble() * 3.5 + 1.5) * (rand.nextBoolean() ? 1 : -1);
            vy1 = (rand.nextDouble() * 3.5 + 1.5) * (rand.nextBoolean() ? 1 : -1);
            vx2 = (rand.nextDouble() * 3.5 + 1.5) * (rand.nextBoolean() ? 1 : -1);
            vy2 = (rand.nextDouble() * 3.5 + 1.5) * (rand.nextBoolean() ? 1 : -1);
            vx3 = (rand.nextDouble() * 3.5 + 1.5) * (rand.nextBoolean() ? 1 : -1);
            vy3 = (rand.nextDouble() * 3.5 + 1.5) * (rand.nextBoolean() ? 1 : -1);

            color = Color.WHITE;
        }

        void update(int w, int h) {
            x0 += vx0; y0 += vy0;
            x1 += vx1; y1 += vy1;
            x2 += vx2; y2 += vy2;
            x3 += vx3; y3 += vy3;

            if (x0 < 0) { x0 = 0; vx0 = -vx0; }
            if (x0 > w) { x0 = w; vx0 = -vx0; }
            if (y0 < 0) { y0 = 0; vy0 = -vy0; }
            if (y0 > h) { y0 = h; vy0 = -vy0; }

            if (x1 < 0) { x1 = 0; vx1 = -vx1; }
            if (x1 > w) { x1 = w; vx1 = -vx1; }
            if (y1 < 0) { y1 = 0; vy1 = -vy1; }
            if (y1 > h) { y1 = h; vy1 = -vy1; }

            if (x2 < 0) { x2 = 0; vx2 = -vx2; }
            if (x2 > w) { x2 = w; vx2 = -vx2; }
            if (y2 < 0) { y2 = 0; vy2 = -vy2; }
            if (y2 > h) { y2 = h; vy2 = -vy2; }

            if (x3 < 0) { x3 = 0; vx3 = -vx3; }
            if (x3 > w) { x3 = w; vx3 = -vx3; }
            if (y3 < 0) { y3 = 0; vy3 = -vy3; }
            if (y3 > h) { y3 = h; vy3 = -vy3; }
        }
    }

    public EuroBezierCanvas() {
        setBackground(Color.BLACK);
        loopTimer = new Timer(speedMs, e -> updateFrame());
    }

    public void startAnimation() {
        int w = Math.max(800, getWidth());
        int h = Math.max(600, getHeight());
        initCurves(w, h);
        bufferImage = null;
        loopTimer.setDelay(speedMs);
        loopTimer.start();
    }

    public void stopAnimation() {
        loopTimer.stop();
    }

    public void setSpeedMs(int ms) {
        this.speedMs = ms;
        loopTimer.setDelay(ms);
    }

    public void setTrailFade(boolean fade) {
        this.trailFade = fade;
    }

    private void initCurves(int w, int h) {
        for (int i = 0; i < NUM_CURVES; i++) {
            curves[i] = new BezierCurve(w, h, rand);
        }
    }

    private void updateFrame() {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        // Create / resize buffer
        if (bufferImage == null || bufferImage.getWidth() != w || bufferImage.getHeight() != h) {
            bufferImage = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            // Clear to black
            Graphics2D bg = bufferImage.createGraphics();
            bg.setColor(Color.BLACK);
            bg.fillRect(0, 0, w, h);
            bg.dispose();
            initCurves(w, h);
        }

        Graphics2D g2d = bufferImage.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        // Optionally fade the previous frame with a semi-transparent black overlay
        if (trailFade) {
            g2d.setColor(new Color(0, 0, 0, 18));
            g2d.fillRect(0, 0, w, h);
        }

        // Advance hue for rainbow cycling
        baseHue = (baseHue + 0.003f) % 1.0f;

        // Draw and update each curve
        for (int i = 0; i < NUM_CURVES; i++) {
            BezierCurve c = curves[i];
            c.update(w, h);

            // Each curve gets a slightly shifted hue
            float hue = (baseHue + i * (1.0f / NUM_CURVES)) % 1.0f;
            c.color = Color.getHSBColor(hue, 0.85f, 1.0f);

            // Draw the cubic Bezier curve
            CubicCurve2D curve = new CubicCurve2D.Double(
                    c.x0, c.y0,
                    c.x1, c.y1,
                    c.x2, c.y2,
                    c.x3, c.y3
            );

            // Glow effect: draw thick semi-transparent curve below, then bright thin on top
            g2d.setStroke(new BasicStroke(4.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2d.setColor(new Color(c.color.getRed(), c.color.getGreen(), c.color.getBlue(), 60));
            g2d.draw(curve);

            g2d.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2d.setColor(c.color);
            g2d.draw(curve);
        }

        g2d.dispose();
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (bufferImage != null) {
            g.drawImage(bufferImage, 0, 0, null);
        }
    }
}

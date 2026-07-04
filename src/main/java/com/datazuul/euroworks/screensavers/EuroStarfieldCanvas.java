package com.datazuul.euroworks.screensavers;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * 3D Starfield screensaver canvas. Simulates flying through space with stars
 * emerging from the center, accelerating, and growing larger as they near the screen.
 * It implements speed-streak trails (motion blur) and optional camera roll rotation.
 */
public class EuroStarfieldCanvas extends JPanel implements ScreensaverCanvas {

    private final Random rand = new Random();
    private final Timer loopTimer;
    private BufferedImage bufferImage = null;

    private static final double MAX_Z = 1000.0;
    private static final double STAR_RADIUS = 0.6; // virtual star radius

    private Star[] stars;
    private int numStars = 1000;    // default number of stars
    private int warpSpeed = 20;     // Z decrement per frame
    private int speedMs = 30;       // frame rate timer delay
    private double rotationSpeed = 0.005; // angle delta per frame (camera roll)

    private static class Star {
        double x, y, z;
        double prevX, prevY, prevZ;
        Color color;

        Star(double x, double y, double z, Color color) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.prevX = x;
            this.prevY = y;
            this.prevZ = z;
            this.color = color;
        }
    }

    public EuroStarfieldCanvas() {
        setBackground(Color.BLACK);
        loopTimer = new Timer(speedMs, e -> tick());
    }

    // ── ScreensaverCanvas ───────────────────────────────────────────────────

    @Override
    public void startAnimation() {
        loopTimer.start();
    }

    @Override
    public void stopAnimation() {
        loopTimer.stop();
    }

    @Override
    public void setSpeedMs(int ms) {
        this.speedMs = ms;
        loopTimer.setDelay(ms);
    }

    // ── Public Setters/Getters for Screensaver Configuration ────────────────

    public int getNumStars() {
        return numStars;
    }

    public void setNumStars(int numStars) {
        this.numStars = numStars;
        stars = null;
        repaint();
    }

    public int getWarpSpeed() {
        return warpSpeed;
    }

    public void setWarpSpeed(int warpSpeed) {
        this.warpSpeed = warpSpeed;
    }

    public double getRotationSpeed() {
        return rotationSpeed;
    }

    public void setRotationSpeed(double rotationSpeed) {
        this.rotationSpeed = rotationSpeed;
    }

    // ── Animation Loop ──────────────────────────────────────────────────────

    private void tick() {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        // Lazy initialize stars
        if (stars == null || stars.length != numStars) {
            initStars(w, h);
        }

        // Create double-buffer if size changed
        if (bufferImage == null || bufferImage.getWidth() != w || bufferImage.getHeight() != h) {
            bufferImage = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        }

        updateStars(w, h);
        renderFrame(w, h);
        repaint();
    }

    private void initStars(int w, int h) {
        stars = new Star[numStars];
        for (int i = 0; i < numStars; i++) {
            stars[i] = createRandomStar(w, h, true);
        }
    }

    private Star createRandomStar(int w, int h, boolean randomizeZ) {
        double spreadX = w * 2.0;
        double spreadY = h * 2.0;
        double x = (rand.nextDouble() - 0.5) * spreadX;
        double y = (rand.nextDouble() - 0.5) * spreadY;
        double z = randomizeZ ? rand.nextDouble() * MAX_Z : MAX_Z;

        // Variety of retro space colors
        double p = rand.nextDouble();
        Color color;
        if (p < 0.82) {
            color = Color.WHITE;
        } else if (p < 0.88) {
            color = new Color(195, 215, 255); // Pale Blue
        } else if (p < 0.94) {
            color = new Color(255, 245, 195); // Pale Yellow
        } else {
            color = new Color(255, 205, 195); // Pale Orange
        }

        return new Star(x, y, z, color);
    }

    private void updateStars(int w, int h) {
        double cos = Math.cos(rotationSpeed);
        double sin = Math.sin(rotationSpeed);

        for (int i = 0; i < numStars; i++) {
            Star s = stars[i];
            
            // Stash current position as previous
            s.prevX = s.x;
            s.prevY = s.y;
            s.prevZ = s.z;

            // Move forward (closer to screen)
            s.z -= warpSpeed;

            // Apply camera roll rotation around Z axis in 3D space
            if (rotationSpeed != 0.0) {
                double rx = s.x * cos - s.y * sin;
                double ry = s.x * sin + s.y * cos;
                s.x = rx;
                s.y = ry;
            }

            // Project coordinates to verify if star is still visible
            double fov = w * 0.45;
            double px = (w / 2.0) + (s.x / s.z) * fov;
            double py = (h / 2.0) + (s.y / s.z) * fov;

            // Reset star if it passes the camera (z <= 0) or goes far off bounds
            if (s.z <= 0 || px < -w || px >= w * 2 || py < -h || py >= h * 2) {
                stars[i] = createRandomStar(w, h, false);
            }
        }
    }

    private void renderFrame(int w, int h) {
        Graphics2D g2 = bufferImage.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Clear with space black
        g2.setColor(Color.BLACK);
        g2.fillRect(0, 0, w, h);

        double fov = w * 0.45;
        double cx = w / 2.0;
        double cy = h / 2.0;

        for (Star s : stars) {
            // Project current coordinates
            double px = cx + (s.x / s.z) * fov;
            double py = cy + (s.y / s.z) * fov;

            // Project previous coordinates
            double prevPx = cx + (s.prevX / s.prevZ) * fov;
            double prevPy = cy + (s.prevY / s.prevZ) * fov;

            // Size expands as depth Z decreases
            double size = Math.max(1.0, (fov * STAR_RADIUS) / s.z);

            // Distance-based brightness (fog effect for depth)
            double brightnessScale = Math.max(0.15, 1.0 - (s.z / MAX_Z));
            int r = (int) (s.color.getRed() * brightnessScale);
            int gr = (int) (s.color.getGreen() * brightnessScale);
            int b = (int) (s.color.getBlue() * brightnessScale);
            g2.setColor(new Color(r, gr, b));

            // Draw a streak line from previous screen position to current position
            // This models authentic Windows starfield motion blur
            double speed = Math.sqrt((px - prevPx) * (px - prevPx) + (py - prevPy) * (py - prevPy));
            
            if (speed < 1.0) {
                // Star is almost stationary (far away), draw as single dot
                if (size <= 1.5) {
                    g2.fillRect((int) px, (int) py, 1, 1);
                } else {
                    g2.fillOval((int) (px - size / 2.0), (int) (py - size / 2.0), (int) size, (int) size);
                }
            } else {
                // Star is moving fast (close by), draw as a thick speed streak trail
                g2.setStroke(new BasicStroke((float) Math.max(1.0, size * 0.8), 
                                             BasicStroke.CAP_ROUND, 
                                             BasicStroke.JOIN_ROUND));
                g2.draw(new Line2D.Double(prevPx, prevPy, px, py));
            }
        }

        g2.dispose();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (bufferImage != null) {
            g.drawImage(bufferImage, 0, 0, null);
        }
    }
}

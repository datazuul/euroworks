package com.datazuul.euroworks.screensavers;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class EuroPipesCanvas extends JPanel implements ScreensaverCanvas {

    private final Random rand = new Random();
    private final Timer animationTimer;
    private BufferedImage bufferImage = null;
    
    // Configurable settings
    private int maxPipes = 3;
    private String jointType = "Kugeln"; // "Kugeln" or "Ellbogen"
    private int speedMs = 50; // Delay in milliseconds

    // Growing pipes state
    private final List<Pipe> activePipes = new ArrayList<>();
    private static final int GRID_X = 60;
    private static final int GRID_Y = 40;
    private static final int GRID_Z = 30;
    private boolean[][][] grid = new boolean[GRID_X][GRID_Y][GRID_Z]; // Collision grid
    
    // 3D Space constants
    private static final double CAMERA_Z = 200.0;
    private static final double FOV = 280.0;
    private static final int STEP_SIZE = 15; // Set step size equal to grid cell size
    private static final double BASE_RADIUS = 5.0;

    // Palette of retro metallic colors
    private static final Color[] PALETTE = {
        new Color(200, 50, 50),   // Red
        new Color(50, 180, 50),   // Green
        new Color(50, 100, 220),  // Blue
        new Color(220, 180, 40),  // Gold
        new Color(160, 160, 160), // Silver
        new Color(200, 100, 50),  // Copper
        new Color(150, 50, 180),  // Purple
        new Color(50, 180, 180)   // Cyan
    };

    public EuroPipesCanvas() {
        setBackground(Color.BLACK);

        // Setup Swing timer on EDT
        animationTimer = new Timer(speedMs, e -> updatePipes());
    }

    public void startAnimation() {
        clearCanvas();
        animationTimer.setDelay(speedMs);
        animationTimer.start();
    }

    public void stopAnimation() {
        animationTimer.stop();
        activePipes.clear();
    }

    public void clearCanvas() {
        // Clear grid
        grid = new boolean[GRID_X][GRID_Y][GRID_Z];
        activePipes.clear();
        
        int w = Math.max(1, getWidth());
        int h = Math.max(1, getHeight());
        bufferImage = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        
        Graphics g = bufferImage.getGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, w, h);
        g.dispose();
        
        repaint();
    }

    public void setMaxPipes(int max) {
        this.maxPipes = max;
    }

    public void setJointType(String type) {
        this.jointType = type;
    }

    public void setSpeedMs(int ms) {
        this.speedMs = ms;
        animationTimer.setDelay(ms);
    }

    /**
     * Core update loop triggered by the timer.
     */
    private void updatePipes() {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        // Ensure buffered image exists and matches screen dimensions
        if (bufferImage == null || bufferImage.getWidth() != w || bufferImage.getHeight() != h) {
            clearCanvas();
            return;
        }

        // Spawn new pipes if needed
        while (activePipes.size() < maxPipes) {
            Pipe p = spawnNewPipe();
            if (p != null) {
                activePipes.add(p);
            } else {
                // If grid is too full, clear screen and restart
                clearCanvas();
                return;
            }
        }

        Graphics2D g2d = bufferImage.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Update each active pipe
        for (int i = activePipes.size() - 1; i >= 0; i--) {
            Pipe pipe = activePipes.get(i);
            boolean extended = extendPipe(pipe, g2d);
            if (!extended) {
                activePipes.remove(i);
            }
        }

        g2d.dispose();
        repaint();
    }

    private Pipe spawnNewPipe() {
        // Search for a free spot on the grid
        int attempts = 50;
        while (attempts-- > 0) {
            int gx = rand.nextInt(GRID_X - 6) + 3;
            int gy = rand.nextInt(GRID_Y - 6) + 3;
            int gz = rand.nextInt(GRID_Z - 6) + 3;

            if (!grid[gx][gy][gz]) {
                grid[gx][gy][gz] = true;
                
                // Map grid coordinate to 3D space
                double x = (gx - GRID_X / 2.0) * (double) STEP_SIZE;
                double y = (gy - GRID_Y / 2.0) * (double) STEP_SIZE;
                double z = (gz - GRID_Z / 2.0) * (double) STEP_SIZE + 100.0;

                int dir = rand.nextInt(6); // 0=X+, 1=X-, 2=Y+, 3=Y-, 4=Z+, 5=Z- (full 3D perspective path)
                Color color = PALETTE[rand.nextInt(PALETTE.length)];
                // Draw significantly longer sections (12 to 26 steps)
                int steps = rand.nextInt(15) + 12;

                return new Pipe(x, y, z, dir, color, steps);
            }
        }
        return null;
    }

    private boolean extendPipe(Pipe p, Graphics2D g2d) {
        // Calculate new endpoint coordinates
        double x1 = p.x;
        double y1 = p.y;
        double z1 = p.z;

        double x2 = x1;
        double y2 = y1;
        double z2 = z1;

        switch (p.dir) {
            case 0 -> x2 += STEP_SIZE; // +X
            case 1 -> x2 -= STEP_SIZE; // -X
            case 2 -> y2 += STEP_SIZE; // +Y
            case 3 -> y2 -= STEP_SIZE; // -Y
            case 4 -> z2 += STEP_SIZE; // +Z
            case 5 -> z2 -= STEP_SIZE; // -Z
        }

        // Convert to grid indexes using Math.round to avoid floating point truncation errors
        int gx = (int) Math.round((x2 / (double) STEP_SIZE) + GRID_X / 2.0);
        int gy = (int) Math.round((y2 / (double) STEP_SIZE) + GRID_Y / 2.0);
        int gz = (int) Math.round(((z2 - 100.0) / (double) STEP_SIZE) + GRID_Z / 2.0);

        // Check bounds and collisions
        if (gx < 0 || gx >= GRID_X || gy < 0 || gy >= GRID_Y || gz < 0 || gz >= GRID_Z || grid[gx][gy][gz]) {
            // Collision or bounds hit! Try to turn perpendicular
            return turnPipe(p, g2d);
        }

        // Set occupied on grid
        grid[gx][gy][gz] = true;

        // Draw cylinder from (x1,y1,z1) to (x2,y2,z2)
        drawCylinder(g2d, x1, y1, z1, x2, y2, z2, p.color);

        // Update state
        p.x = x2;
        p.y = y2;
        p.z = z2;
        p.steps--;

        // Randomly turn if remaining steps reached 0
        if (p.steps <= 0) {
            p.steps = rand.nextInt(15) + 12;
            return turnPipe(p, g2d);
        }

        return true;
    }

    private boolean turnPipe(Pipe p, Graphics2D g2d) {
        // Find possible perpendicular directions
        List<Integer> validDirs = new ArrayList<>();
        int currentAxis = p.dir / 2; // 0=X, 1=Y, 2=Z

        for (int d = 0; d < 6; d++) { // Allow full 3D directions (X, Y, Z axes)
            if (d / 2 == currentAxis) continue; // Exclude current axis

            double tx = p.x;
            double ty = p.y;
            double tz = p.z;

            switch (d) {
                case 0 -> tx += STEP_SIZE;
                case 1 -> tx -= STEP_SIZE;
                case 2 -> ty += STEP_SIZE;
                case 3 -> ty -= STEP_SIZE;
                case 4 -> tz += STEP_SIZE;
                case 5 -> tz -= STEP_SIZE;
            }

            int tgx = (int) Math.round((tx / (double) STEP_SIZE) + GRID_X / 2.0);
            int tgy = (int) Math.round((ty / (double) STEP_SIZE) + GRID_Y / 2.0);
            int tgz = (int) Math.round(((tz - 100.0) / (double) STEP_SIZE) + GRID_Z / 2.0);

            if (tgx >= 0 && tgx < GRID_X && tgy >= 0 && tgy < GRID_Y && tgz >= 0 && tgz < GRID_Z && !grid[tgx][tgy][tgz]) {
                validDirs.add(d);
            }
        }

        if (validDirs.isEmpty()) {
            // Completely trapped. Cap the end of the pipe and stop
            drawJoint(g2d, p.x, p.y, p.z, p.color);
            return false;
        }

        // Draw joint at bend point
        drawJoint(g2d, p.x, p.y, p.z, p.color);

        // Select new direction
        p.dir = validDirs.get(rand.nextInt(validDirs.size()));
        return true;
    }

    private void drawCylinder(Graphics2D g2d, double x1, double y1, double z1, double x2, double y2, double z2, Color baseColor) {
        int w = getWidth();
        int h = getHeight();

        // 3D Perspective Projection to Screen Space
        double denom1 = z1 + CAMERA_Z;
        double denom2 = z2 + CAMERA_Z;
        if (denom1 <= 0 || denom2 <= 0) return;

        double sx1 = w / 2.0 + (x1 * FOV) / denom1;
        double sy1 = h / 2.0 - (y1 * FOV) / denom1;
        double r1 = (BASE_RADIUS * FOV) / denom1;

        double sx2 = w / 2.0 + (x2 * FOV) / denom2;
        double sy2 = h / 2.0 - (y2 * FOV) / denom2;
        double r2 = (BASE_RADIUS * FOV) / denom2;

        // Vector calculations for cylinder boundary polygon
        double dx = sx2 - sx1;
        double dy = sy2 - sy1;
        double len = Math.hypot(dx, dy);
        if (len <= 0) return;

        double nx = -dy / len;
        double ny = dx / len;

        Path2D.Double path = new Path2D.Double();
        path.moveTo(sx1 + nx * r1, sy1 + ny * r1);
        path.lineTo(sx2 + nx * r2, sy2 + ny * r2);
        path.lineTo(sx2 - nx * r2, sy2 - ny * r2);
        path.lineTo(sx1 - nx * r1, sy1 - ny * r1);
        path.closePath();

        // 3D Specular Shading Gradient (Metallic Cylinder Look)
        float[] fractions = {0.0f, 0.25f, 0.5f, 0.75f, 1.0f};
        Color darkColor = baseColor.darker().darker();
        Color lightColor = Color.WHITE;
        Color[] colors = {
            darkColor,
            baseColor,
            lightColor,
            baseColor,
            darkColor
        };

        // Linear gradient drawn perpendicular to the cylinder axis
        LinearGradientPaint lgp = new LinearGradientPaint(
                new Point2D.Double(sx1 + nx * r1, sy1 + ny * r1),
                new Point2D.Double(sx1 - nx * r1, sy1 - ny * r1),
                fractions, colors
        );

        g2d.setPaint(lgp);
        g2d.fill(path);
    }

    private void drawJoint(Graphics2D g2d, double x, double y, double z, Color baseColor) {
        int w = getWidth();
        int h = getHeight();

        double denom = z + CAMERA_Z;
        if (denom <= 0) return;

        double sx = w / 2.0 + (x * FOV) / denom;
        double sy = h / 2.0 - (y * FOV) / denom;
        double r = (BASE_RADIUS * FOV) / denom;

        if ("Kugeln".equals(jointType)) {
            // Radial Shading for Sphere look
            java.awt.geom.Point2D center = new java.awt.geom.Point2D.Double(sx, sy);
            java.awt.geom.Point2D focus = new java.awt.geom.Point2D.Double(sx - r * 0.3, sy - r * 0.3);
            float[] fractions = {0.0f, 0.7f, 1.0f};
            Color[] colors = {Color.WHITE, baseColor, baseColor.darker().darker()};

            RadialGradientPaint rgp = new RadialGradientPaint(
                    center, (float) r, focus, fractions, colors,
                    java.awt.MultipleGradientPaint.CycleMethod.NO_CYCLE
            );
            g2d.setPaint(rgp);
            g2d.fillOval((int) (sx - r), (int) (sy - r), (int) (r * 2), (int) (r * 2));
        } else {
            // Elbow: Simple flat oval highlight matching elbow joints
            g2d.setColor(baseColor.darker());
            g2d.fillOval((int) (sx - r), (int) (sy - r), (int) (r * 2), (int) (r * 2));
            g2d.setColor(Color.WHITE);
            g2d.fillOval((int) (sx - r * 0.4), (int) (sy - r * 0.4), (int) (r * 0.6), (int) (r * 0.6));
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (bufferImage != null) {
            g.drawImage(bufferImage, 0, 0, null);
        }
    }

    /**
     * Inner helper class defining individual pipe coordinates and state.
     */
    private static class Pipe {
        double x, y, z;
        int dir; // 0=X+, 1=X-, 2=Y+, 3=Y-, 4=Z+, 5=Z-
        Color color;
        int steps;

        Pipe(double x, double y, double z, int dir, Color color, int steps) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.dir = dir;
            this.color = color;
            this.steps = steps;
        }
    }
}

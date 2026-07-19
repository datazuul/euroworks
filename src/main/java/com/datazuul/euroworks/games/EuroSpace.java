package com.datazuul.euroworks.games;

import com.datazuul.euroworks.apps.EuroAppFrame;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.geom.Path2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * EuroSpace - 2D Solar System Flight Simulator and Lander Minigame for EuroWorks.
 * Features:
 * - Scaled orbital mechanics for Sun and 8 planets.
 * - Spaceship controls with momentum inertia, thrust vectoring, and gravity pull from the Sun.
 * - Planetary landing mode (Lunar Lander minigame) with planet-specific gravity.
 * - Educational facts dialogue pop-ups after successful landings.
 * - Starfield background and telemetry HUD.
 * - Retro sound synthesis.
 */
public class EuroSpace extends EuroAppFrame {

    private static final int GAME_WIDTH = 440;
    private static final int GAME_HEIGHT = 460;

    private enum Mode {
        SPACE_FLIGHT, LANDING, FACT_SHEET, GAME_OVER
    }

    // Solar system planets definition
    private static class Planet {
        String name;
        Color color;
        double radius;
        double orbitRadius;
        double orbitSpeed;
        double angle;
        double x, y; // computed real-time coordinates

        // Lander gravity multiplier
        double gravity;
        String facts;

        Planet(String name, Color color, double radius, double orbitRadius, double orbitSpeed, double gravity, String facts) {
            this.name = name;
            this.color = color;
            this.radius = radius;
            this.orbitRadius = orbitRadius;
            this.orbitSpeed = orbitSpeed;
            this.gravity = gravity;
            this.facts = facts;
            this.angle = new Random().nextDouble() * Math.PI * 2;
        }

        void updatePosition() {
            angle += orbitSpeed;
            x = Math.cos(angle) * orbitRadius;
            y = Math.sin(angle) * orbitRadius;
        }
    }

    private final List<Planet> planets = new ArrayList<>();
    private Planet targetPlanet = null;

    // Flight variables
    private double sx = 200.0; // Space coordinates
    private double sy = 200.0;
    private double svx = 0.0;
    private double svy = -1.5;
    private double sAngle = -Math.PI / 2; // facing up

    // Lander variables (Lander mode)
    private double lx = 220;
    private double ly = 50;
    private double lvx = 0;
    private double lvy = 0.5;
    private double lAngle = -Math.PI / 2; // facing up
    private int fuel = 500;
    private int landingPadX = 180;
    private int landingPadW = 60;
    private int[] terrainY = new int[GAME_WIDTH];

    // Starfield
    private static class Star {
        int x, y;
        double speed;
        Star(int x, int y, double speed) {
            this.x = x;
            this.y = y;
            this.speed = speed;
        }
    }
    private final List<Star> stars = new ArrayList<>();

    // State variables
    private Mode mode = Mode.SPACE_FLIGHT;
    private int score = 0;
    private final Timer loopTimer;
    private boolean soundRunning = false;

    // Autopilot and Music variables
    private boolean autopilotActive = false;
    private int autopilotTargetIndex = 0;
    private Thread musicThread = null;
    private volatile boolean musicPlaying = false;

    // Keyboard flags
    private boolean keyUp = false;
    private boolean keyLeft = false;
    private boolean keyRight = false;
    private boolean keyDown = false;

    private final SpacePanel playPanel;

    public EuroSpace() {
        super("EuroSpace (Solar System)");
        setSize(GAME_WIDTH + 16, GAME_HEIGHT + 74);
        setJMenuBar(buildMenuBar());

        // Generate stars
        Random rand = new Random();
        for (int i = 0; i < 60; i++) {
            stars.add(new Star(rand.nextInt(GAME_WIDTH), rand.nextInt(GAME_HEIGHT), 0.2 + rand.nextDouble() * 0.8));
        }

        // Initialize Planets
        planets.add(new Planet("Merkur", new Color(169, 169, 169), 6, 120, 0.015, 0.38,
            "Merkur hat fast keine Atmosphäre und extreme Temperaturunterschiede von -173°C bis 427°C. Ein Tag dauert dort 59 Erdentage."));
        planets.add(new Planet("Venus", new Color(222, 184, 135), 10, 180, 0.011, 0.9,
            "Die Venus hat eine dichte CO2-Atmosphäre mit Schwefelsäurewolken. Der Treibhauseffekt macht sie zum heißesten Planeten (460°C)."));
        planets.add(new Planet("Erde", new Color(30, 144, 255), 12, 260, 0.008, 1.0,
            "Die Erde ist der einzige bekannte Planet mit flüssigem Wasser an der Oberfläche und blühendem Leben."));
        planets.add(new Planet("Mars", new Color(205, 92, 92), 8, 350, 0.006, 0.38,
            "Mars ist eine kalte Wüste mit einer dünnen CO2-Atmosphäre. Er beheimatet den Olympus Mons, den höchsten Vulkan unseres Sonnensystems."));
        planets.add(new Planet("Jupiter", new Color(210, 180, 140), 24, 480, 0.003, 2.52,
            "Jupiter ist der größte Planet im Sonnensystem. Dieser riesige Gasball hat über 80 Monde und den berühmten Großen Roten Fleck."));
        planets.add(new Planet("Saturn", new Color(238, 221, 130), 20, 620, 0.002, 1.06,
            "Saturn ist berühmt für sein ausgedehntes Ringsystem aus Eis und Staub. Seine Dichte ist so gering, dass er im Wasser schwimmen würde."));
        planets.add(new Planet("Uranus", new Color(175, 238, 238), 16, 760, 0.001, 0.89,
            "Uranus ist ein Eisriese mit einer extrem geneigten Rotationsachse. Er rollt praktisch auf seiner Bahn um die Sonne."));
        planets.add(new Planet("Neptun", new Color(70, 130, 180), 15, 900, 0.0007, 1.14,
            "Neptun ist der sonnenfernste Planet. Dort toben die stärksten Winde des Sonnensystems mit Geschwindigkeiten von bis zu 2100 km/h."));

        playPanel = new SpacePanel();
        setContentPane(playPanel);

        playPanel.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                handleKeyEvent(e.getKeyCode(), true);
            }

            @Override
            public void keyReleased(KeyEvent e) {
                handleKeyEvent(e.getKeyCode(), false);
            }
        });
        playPanel.setFocusable(true);

        loopTimer = new Timer(20, e -> {
            updatePhysics();
            playPanel.repaint();
        });
        loopTimer.start();
    }

    private void handleKeyEvent(int keyCode, boolean pressed) {
        if (pressed) {
            if (keyCode == KeyEvent.VK_UP || keyCode == KeyEvent.VK_W) keyUp = true;
            if (keyCode == KeyEvent.VK_LEFT || keyCode == KeyEvent.VK_A) keyLeft = true;
            if (keyCode == KeyEvent.VK_RIGHT || keyCode == KeyEvent.VK_D) keyRight = true;
            if (keyCode == KeyEvent.VK_DOWN || keyCode == KeyEvent.VK_S) keyDown = true;

            if (keyCode == KeyEvent.VK_SPACE) {
                if (mode == Mode.SPACE_FLIGHT) {
                    tryLanding();
                }
            }

            if (keyCode == KeyEvent.VK_ENTER) {
                if (mode == Mode.FACT_SHEET) {
                    resumeSpaceFlight(true);
                } else if (mode == Mode.GAME_OVER) {
                    resumeSpaceFlight(false);
                }
            }
        } else {
            if (keyCode == KeyEvent.VK_UP || keyCode == KeyEvent.VK_W) keyUp = false;
            if (keyCode == KeyEvent.VK_LEFT || keyCode == KeyEvent.VK_A) keyLeft = false;
            if (keyCode == KeyEvent.VK_RIGHT || keyCode == KeyEvent.VK_D) keyRight = false;
            if (keyCode == KeyEvent.VK_DOWN || keyCode == KeyEvent.VK_S) keyDown = false;
        }
    }

    private void tryLanding() {
        // Find nearest planet
        Planet nearest = getNearestPlanet();
        if (nearest != null) {
            double dist = Math.hypot(sx - nearest.x, sy - nearest.y);
            if (dist < nearest.radius + 30) {
                // Initialize Lander Mode
                mode = Mode.LANDING;
                targetPlanet = nearest;
                lx = GAME_WIDTH / 2.0;
                ly = 40;
                lvx = (svx * 0.5);
                lvy = 0.5;
                lAngle = -Math.PI / 2;
                fuel = 400;

                // Build random terrain
                Random rand = new Random();
                int baseH = GAME_HEIGHT - 60;
                for (int i = 0; i < GAME_WIDTH; i++) {
                    terrainY[i] = baseH + (int)(Math.sin(i * 0.05) * 15 + Math.cos(i * 0.02) * 20);
                }
                // Flatten landing pad
                landingPadX = 120 + rand.nextInt(140);
                for (int i = landingPadX; i < landingPadX + landingPadW; i++) {
                    terrainY[i] = GAME_HEIGHT - 40;
                }

                playLevelStartSound();
            }
        }
    }

    private void resumeSpaceFlight(boolean success) {
        mode = Mode.SPACE_FLIGHT;
        if (success) {
            score += 100;
        }
        // Reposition ship safely outside target orbit
        if (targetPlanet != null) {
            sx = targetPlanet.x + (targetPlanet.radius + 40) * Math.cos(targetPlanet.angle);
            sy = targetPlanet.y + (targetPlanet.radius + 40) * Math.sin(targetPlanet.angle);
            svx = targetPlanet.x * -0.005; // orbit speed alignment
            svy = targetPlanet.y * 0.005;
        }
        targetPlanet = null;
    }

    private Planet getNearestPlanet() {
        Planet nearest = null;
        double minDist = Double.MAX_VALUE;
        for (Planet p : planets) {
            double d = Math.hypot(sx - p.x, sy - p.y);
            if (d < minDist) {
                minDist = d;
                nearest = p;
            }
        }
        return nearest;
    }

    private void updatePhysics() {
        // Orbit update
        for (Planet p : planets) {
            p.updatePosition();
        }

        if (mode == Mode.SPACE_FLIGHT) {
            if (autopilotActive) {
                Planet target = planets.get(autopilotTargetIndex);
                double dx = target.x - sx;
                double dy = target.y - sy;
                double dist = Math.hypot(dx, dy);
                if (dist < target.radius + 60) {
                    autopilotTargetIndex = (autopilotTargetIndex + 1) % planets.size();
                } else {
                    double targetAngle = Math.atan2(dy, dx);
                    double diff = targetAngle - sAngle;
                    while (diff < -Math.PI) diff += Math.PI * 2;
                    while (diff > Math.PI) diff -= Math.PI * 2;
                    if (diff > 0.05) sAngle += 0.05;
                    else if (diff < -0.05) sAngle -= 0.05;
                    else sAngle = targetAngle;

                    if (Math.abs(diff) < 0.3) {
                        double thrust = 0.08;
                        svx += Math.cos(sAngle) * thrust;
                        svy += Math.sin(sAngle) * thrust;
                    }
                }
            } else {
                // Apply ship movement
                if (keyLeft) sAngle -= 0.06;
                if (keyRight) sAngle += 0.06;

                if (keyUp) {
                    double speed = 0.15;
                    svx += Math.cos(sAngle) * speed;
                    svy += Math.sin(sAngle) * speed;
                    playThrustSound();
                }
                if (keyDown) {
                    svx *= 0.95;
                    svy *= 0.95;
                }
            }

            // Gravity towards the Sun (0, 0)
            double sunDist = Math.hypot(sx, sy);
            if (sunDist > 20) {
                double gForce = 8.0 / (sunDist * sunDist);
                svx -= (sx / sunDist) * gForce;
                svy -= (sy / sunDist) * gForce;
            }

            // Drag
            svx *= 0.99;
            svy *= 0.99;

            sx += svx;
            sy += svy;

        } else if (mode == Mode.LANDING) {
            // Lander controls
            if (keyLeft) lAngle -= 0.05;
            if (keyRight) lAngle += 0.05;

            if (keyUp && fuel > 0) {
                double thrust = 0.08 + (targetPlanet != null ? targetPlanet.gravity * 0.04 : 0.04);
                lvx += Math.cos(lAngle) * thrust;
                lvy += Math.sin(lAngle) * thrust;
                fuel -= 2;
                playThrustSound();
            }

            // Gravity pull downward
            lvy += (targetPlanet != null ? targetPlanet.gravity * 0.03 : 0.03);

            lx += lvx;
            ly += lvy;

            // Bounds checks
            if (lx < 0) { lx = 0; lvx = 0; }
            if (lx >= GAME_WIDTH) { lx = GAME_WIDTH - 1; lvx = 0; }

            // Terrain collision check
            int ix = (int) lx;
            if (ly >= terrainY[ix]) {
                checkLanderTouchdown();
            }
        }
    }

    private void checkLanderTouchdown() {
        // Did we land on the pad?
        boolean onPad = (lx >= landingPadX && lx <= landingPadX + landingPadW);

        // Angle check (close to upright: -Math.PI / 2)
        double angleDiff = Math.abs(lAngle - (-Math.PI / 2));
        boolean angleSafe = (angleDiff < 0.25 || angleDiff > Math.PI * 2 - 0.25);

        // Speed check
        boolean speedSafe = (Math.abs(lvx) < 1.2 && Math.abs(lvy) < 2.0);

        if (onPad && angleSafe && speedSafe) {
            // Success!
            mode = Mode.FACT_SHEET;
            playSound(600, 200);
        } else {
            // Crash!
            mode = Mode.GAME_OVER;
            playExplosionSound();
            checkAndPromptHighScore();
        }
    }

    private void checkAndPromptHighScore() {
        if (score <= 0) return;

        HighScore hs = new HighScore("EuroSpace");
        List<HighScore.ScoreEntry> scores = hs.getScores();
        boolean qualifies = false;
        if (scores.size() < 10) {
            qualifies = true;
        } else {
            if (score > scores.get(9).score) {
                qualifies = true;
            }
        }

        if (qualifies) {
            SwingUtilities.invokeLater(() -> {
                String username = JOptionPane.showInputDialog(this,
                    "Glückwunsch! Neuer High Score: " + score + " Punkte\nBitte Name eingeben:",
                    "Neuer High Score",
                    JOptionPane.PLAIN_MESSAGE
                );
                if (username != null && !username.trim().isEmpty()) {
                    try {
                        hs.setHighScore(score, username.trim(), 0);
                        showHighScoresDialog();
                    } catch (IOException ex) {
                        JOptionPane.showMessageDialog(this, "Fehler beim Speichern des Highscores.", "Fehler", JOptionPane.ERROR_MESSAGE);
                    }
                }
            });
        }
    }

    private void showHighScoresDialog() {
        HighScore hs = new HighScore("EuroSpace");
        List<HighScore.ScoreEntry> scores = hs.getScores();
        StringBuilder sb = new StringBuilder();
        sb.append("EuroSpace Bestenliste (Top 10):\n\n");
        if (scores.isEmpty()) {
            sb.append("Keine Einträge vorhanden.");
        } else {
            int limit = Math.min(10, scores.size());
            for (int i = 0; i < limit; i++) {
                HighScore.ScoreEntry entry = scores.get(i);
                sb.append(String.format("%d. %s - %d Punkte (%s)\n",
                    (i + 1),
                    entry.username,
                    entry.score,
                    new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm").format(entry.date)
                ));
            }
        }
        JOptionPane.showMessageDialog(this, sb.toString(), "Bestenliste", JOptionPane.INFORMATION_MESSAGE);
    }

    // ── Retro Sound Synthesizer ─────────────────────────────────────────────

    private void playSound(int freq, int durationMs) {
        new Thread(() -> {
            try {
                int sampleRate = 8000;
                int numSamples = durationMs * sampleRate / 1000;
                byte[] buf = new byte[numSamples];
                double phase = 0.0;
                for (int i = 0; i < numSamples; i++) {
                    phase += 2.0 * Math.PI * freq / sampleRate;
                    buf[i] = (byte) (Math.sin(phase) >= 0.0 ? 30 : -30);
                }
                javax.sound.sampled.AudioFormat af = new javax.sound.sampled.AudioFormat(sampleRate, 8, 1, true, false);
                javax.sound.sampled.SourceDataLine sdl = javax.sound.sampled.AudioSystem.getSourceDataLine(af);
                sdl.open(af);
                sdl.start();
                sdl.write(buf, 0, buf.length);
                sdl.drain();
                sdl.close();
            } catch (Exception ex) {
                // Fallback
            }
        }).start();
    }

    private void playThrustSound() {
        if (soundRunning) return;
        soundRunning = true;
        new Thread(() -> {
            try {
                int sampleRate = 8000;
                byte[] buf = new byte[1000];
                Random rand = new Random();
                for (int i = 0; i < buf.length; i++) {
                    buf[i] = (byte) (rand.nextInt(30) - 15);
                }
                javax.sound.sampled.AudioFormat af = new javax.sound.sampled.AudioFormat(sampleRate, 8, 1, true, false);
                javax.sound.sampled.SourceDataLine sdl = javax.sound.sampled.AudioSystem.getSourceDataLine(af);
                sdl.open(af);
                sdl.start();
                sdl.write(buf, 0, buf.length);
                sdl.drain();
                sdl.close();
            } catch (Exception ex) {
                // sound failed
            } finally {
                soundRunning = false;
            }
        }).start();
    }

    private void playExplosionSound() {
        new Thread(() -> {
            try {
                int sampleRate = 8000;
                byte[] buf = new byte[3000];
                Random rand = new Random();
                for (int i = 0; i < buf.length; i++) {
                    double volume = 1.0 - (double) i / buf.length;
                    buf[i] = (byte) ((rand.nextInt(80) - 40) * volume);
                }
                javax.sound.sampled.AudioFormat af = new javax.sound.sampled.AudioFormat(sampleRate, 8, 1, true, false);
                javax.sound.sampled.SourceDataLine sdl = javax.sound.sampled.AudioSystem.getSourceDataLine(af);
                sdl.open(af);
                sdl.start();
                sdl.write(buf, 0, buf.length);
                sdl.drain();
                sdl.close();
            } catch (Exception ex) {
                // sound failed
            }
        }).start();
    }

    private void playLevelStartSound() {
        playSound(880, 100);
    }

    // ── Panel Graphics Painting ─────────────────────────────────────────────

    private class SpacePanel extends JPanel {

        SpacePanel() {
            setBackground(Color.BLACK);
            setPreferredSize(new Dimension(GAME_WIDTH, GAME_HEIGHT));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (mode == Mode.SPACE_FLIGHT) {
                renderSpaceFlight(g2);
            } else if (mode == Mode.LANDING) {
                renderLanderMode(g2);
            } else if (mode == Mode.FACT_SHEET) {
                renderFactSheet(g2);
            } else if (mode == Mode.GAME_OVER) {
                renderGameOver(g2);
            }
            g2.dispose();
        }

        private void renderSpaceFlight(Graphics2D g2) {
            // Draw Stars relative to camera
            g2.setColor(Color.WHITE);
            for (Star s : stars) {
                int px = (int)((s.x - sx * s.speed) % GAME_WIDTH);
                int py = (int)((s.y - sy * s.speed) % GAME_HEIGHT);
                if (px < 0) px += GAME_WIDTH;
                if (py < 0) py += GAME_HEIGHT;
                g2.fillRect(px, py, 1, 1);
            }

            // Camera offset
            double cx = GAME_WIDTH / 2.0 - sx;
            double cy = GAME_HEIGHT / 2.0 - sy;

            // Draw Sun
            g2.setColor(Color.YELLOW);
            g2.fillOval((int)(cx - 20), (int)(cy - 20), 40, 40);

            // Draw orbits and planets
            for (Planet p : planets) {
                g2.setColor(new Color(255, 255, 255, 30));
                g2.drawOval((int)(cx - p.orbitRadius), (int)(cy - p.orbitRadius), (int)(p.orbitRadius * 2), (int)(p.orbitRadius * 2));

                g2.setColor(p.color);
                int px = (int) (cx + p.x);
                int py = (int) (cy + p.y);
                int rad = (int) p.radius;
                g2.fillOval(px - rad, py - rad, rad * 2, rad * 2);

                // Saturn ring
                if ("Saturn".equals(p.name)) {
                    g2.setColor(new Color(210, 180, 140));
                    g2.drawOval(px - rad - 5, py - 3, rad * 2 + 10, 6);
                }

                // Tag/Label
                g2.setFont(new Font("Monospaced", Font.PLAIN, 9));
                g2.setColor(Color.LIGHT_GRAY);
                g2.drawString(p.name, px + rad + 2, py + 3);
            }

            // Draw player ship
            g2.setColor(Color.WHITE);
            int sxCenter = GAME_WIDTH / 2;
            int syCenter = GAME_HEIGHT / 2;

            // Triangular ship path
            Path2D.Double shipPath = new Path2D.Double();
            shipPath.moveTo(sxCenter + 10 * Math.cos(sAngle), syCenter + 10 * Math.sin(sAngle));
            shipPath.lineTo(sxCenter + 7 * Math.cos(sAngle + 2.5), syCenter + 7 * Math.sin(sAngle + 2.5));
            shipPath.lineTo(sxCenter + 7 * Math.cos(sAngle - 2.5), syCenter + 7 * Math.sin(sAngle - 2.5));
            shipPath.closePath();
            g2.fill(shipPath);

            // Thruster fire
            if (keyUp || autopilotActive) {
                g2.setColor(Color.ORANGE);
                g2.fillOval((int)(sxCenter - 8 * Math.cos(sAngle) - 2), (int)(syCenter - 8 * Math.sin(sAngle) - 2), 4, 4);
            }

            // HUD details
            g2.setFont(new Font("Monospaced", Font.BOLD, 11));
            if (autopilotActive) {
                g2.setColor(Color.CYAN);
                g2.drawString("AUTOPILOT FLUG AKTIV", 15, 30);
            } else {
                g2.setColor(Color.GREEN);
                g2.drawString("SCORE: " + score, 15, 30);
            }
            g2.drawString("SPEED: " + String.format("%.1f", Math.hypot(svx, svy) * 10), 15, 50);

            Planet nearest = getNearestPlanet();
            if (nearest != null) {
                double dist = Math.hypot(sx - nearest.x, sy - nearest.y);
                g2.drawString(String.format("NÄCHSTER PLANET: %s", nearest.name), 15, 70);
                g2.drawString(String.format("DISTANZ: %.1f", dist - nearest.radius), 15, 90);

                if (dist < nearest.radius + 30) {
                    g2.setColor(Color.YELLOW);
                    g2.drawString("BEREIT ZUM LANDEN! DRÜCKE [LEERTASTE]", 15, 120);
                }
            }
        }

        private void renderLanderMode(Graphics2D g2) {
            // Draw Landing Sky background
            g2.setColor(new Color(0, 0, 30));
            g2.fillRect(0, 0, GAME_WIDTH, GAME_HEIGHT);

            // Draw terrain
            g2.setColor(Color.GRAY);
            for (int i = 0; i < GAME_WIDTH - 1; i++) {
                g2.drawLine(i, terrainY[i], i + 1, terrainY[i + 1]);
            }

            // Draw landing pad
            g2.setColor(Color.GREEN);
            g2.fillRect(landingPadX, GAME_HEIGHT - 40, landingPadW, 6);
            g2.setFont(new Font("Monospaced", Font.BOLD, 10));
            g2.drawString("LANDING PAD", landingPadX + 2, GAME_HEIGHT - 48);

            // Draw lander
            g2.setColor(Color.WHITE);
            // Draw simple lander body
            int lcx = (int) lx;
            int lcy = (int) ly;

            Path2D.Double landerPath = new Path2D.Double();
            landerPath.moveTo(lcx + 8 * Math.cos(lAngle), lcy + 8 * Math.sin(lAngle));
            landerPath.lineTo(lcx + 6 * Math.cos(lAngle + 2.3), lcy + 6 * Math.sin(lAngle + 2.3));
            landerPath.lineTo(lcx + 6 * Math.cos(lAngle - 2.3), lcy + 6 * Math.sin(lAngle - 2.3));
            landerPath.closePath();
            g2.fill(landerPath);

            // Legs
            g2.drawLine(lcx - 4, lcy + 4, lcx - 7, lcy + 9);
            g2.drawLine(lcx + 4, lcy + 4, lcx + 7, lcy + 9);

            // Thruster fire
            if (keyUp && fuel > 0) {
                g2.setColor(Color.RED);
                g2.fillOval(lcx - (int)(8 * Math.cos(lAngle)) - 3, lcy - (int)(8 * Math.sin(lAngle)) - 3, 6, 6);
            }

            // HUD
            g2.setFont(new Font("Monospaced", Font.BOLD, 11));
            g2.setColor(Color.GREEN);
            g2.drawString(String.format("PLANET: %s", targetPlanet != null ? targetPlanet.name : "Unbekannt"), 15, 30);
            g2.drawString(String.format("SPEED X: %.2f", lvx), 15, 50);
            g2.drawString(String.format("SPEED Y: %.2f", lvy), 15, 70);
            g2.drawString(String.format("FUEL: %d", fuel), 15, 90);
        }

        private void renderFactSheet(Graphics2D g2) {
            g2.setColor(new Color(0, 0, 0, 220));
            g2.fillRect(0, 0, GAME_WIDTH, GAME_HEIGHT);

            g2.setColor(Color.GREEN);
            g2.drawRect(20, 20, GAME_WIDTH - 40, GAME_HEIGHT - 40);

            g2.setFont(new Font("Monospaced", Font.BOLD, 18));
            g2.drawString("ERFOLGREICHE LANDUNG!", 40, 60);

            if (targetPlanet != null) {
                g2.setColor(targetPlanet.color);
                g2.fillOval(40, 100, 30, 30);

                g2.setFont(new Font("SansSerif", Font.BOLD, 16));
                g2.setColor(Color.WHITE);
                g2.drawString("Planet " + targetPlanet.name, 90, 120);

                g2.setFont(new Font("SansSerif", Font.PLAIN, 13));
                g2.setColor(Color.LIGHT_GRAY);

                // Multi-line rendering for facts
                drawStringMultiLine(g2, targetPlanet.facts, 40, 170, GAME_WIDTH - 80);
            }

            g2.setFont(new Font("Monospaced", Font.BOLD, 12));
            g2.setColor(Color.YELLOW);
            g2.drawString("Drücke [ENTER] um weiterzufliegen.", 40, GAME_HEIGHT - 60);
        }

        private void renderGameOver(Graphics2D g2) {
            g2.setColor(new Color(0, 0, 0, 220));
            g2.fillRect(0, 0, GAME_WIDTH, GAME_HEIGHT);

            g2.setFont(new Font("Monospaced", Font.BOLD, 26));
            g2.setColor(Color.RED);
            g2.drawString("LANDER ZERSTÖRT!", 90, GAME_HEIGHT / 2 - 30);

            g2.setFont(new Font("SansSerif", Font.PLAIN, 14));
            g2.setColor(Color.WHITE);
            g2.drawString("Aufprallgeschwindigkeit zu hoch oder Bruchlandung.", 60, GAME_HEIGHT / 2 + 10);

            g2.setFont(new Font("Monospaced", Font.BOLD, 12));
            g2.setColor(Color.YELLOW);
            g2.drawString("Drücke [ENTER] zum Beenden / Neustarten", 80, GAME_HEIGHT / 2 + 60);
        }

        private void drawStringMultiLine(Graphics2D g2, String text, int x, int y, int width) {
            FontMetrics fm = g2.getFontMetrics();
            String[] words = text.split(" ");
            StringBuilder line = new StringBuilder();
            int currentY = y;

            for (String word : words) {
                String testLine = line + word + " ";
                int testWidth = fm.stringWidth(testLine);
                if (testWidth > width) {
                    g2.drawString(line.toString(), x, currentY);
                    line = new StringBuilder(word + " ");
                    currentY += fm.getHeight();
                } else {
                    line.append(word).append(" ");
                }
            }
            g2.drawString(line.toString(), x, currentY);
        }
    }

    private JMenuBar buildMenuBar() {
        JMenuBar mb = new JMenuBar();
        JMenu menu = new JMenu("Optionen");
        menu.setFont(new Font("SansSerif", Font.PLAIN, 11));

        JCheckBoxMenuItem apItem = new JCheckBoxMenuItem("Autopilot & Musik");
        apItem.setFont(new Font("SansSerif", Font.PLAIN, 11));
        apItem.addActionListener(e -> setAutopilotActive(apItem.isSelected()));
        menu.add(apItem);

        menu.addSeparator();
        JMenuItem exitItem = new JMenuItem("Beenden");
        exitItem.setFont(new Font("SansSerif", Font.PLAIN, 11));
        exitItem.addActionListener(e -> {
            stopSpaceMusic();
            dispose();
        });
        menu.add(exitItem);

        mb.add(menu);
        return mb;
    }

    private void setAutopilotActive(boolean active) {
        this.autopilotActive = active;
        if (active) {
            startSpaceMusic();
        } else {
            stopSpaceMusic();
        }
    }

    private void startSpaceMusic() {
        if (musicPlaying) return;
        musicPlaying = true;
        musicThread = new Thread(() -> {
            // Quiet, spacey ambient arpeggio chord progression (Cmaj9 -> Fmaj7 -> G6 -> Am)
            int[][] progressions = {
                {261, 329, 392, 493, 523}, // Cmaj9
                {349, 440, 523, 587, 698}, // Fmaj9
                {392, 493, 587, 659, 784}, // G6/9
                {220, 330, 440, 523, 659}  // Am7
            };
            int sampleRate = 8000;
            byte[] buf = new byte[1600]; // 200ms per note

            try {
                javax.sound.sampled.AudioFormat af = new javax.sound.sampled.AudioFormat(sampleRate, 8, 1, true, false);
                javax.sound.sampled.SourceDataLine sdl = javax.sound.sampled.AudioSystem.getSourceDataLine(af);
                sdl.open(af);
                sdl.start();

                int chordIndex = 0;
                while (musicPlaying) {
                    int[] chord = progressions[chordIndex];
                    for (int noteFreq : chord) {
                        if (!musicPlaying) break;
                        // Generate very quiet sine wave (amplitude 8)
                        double phase = 0.0;
                        for (int i = 0; i < buf.length; i++) {
                            phase += 2.0 * Math.PI * noteFreq / sampleRate;
                            // Add envelope fade out
                            double volume = 8.0 * (1.0 - (double) i / buf.length);
                            buf[i] = (byte) (volume * Math.sin(phase));
                        }
                        sdl.write(buf, 0, buf.length);
                        Thread.sleep(250); // Pause between notes
                    }
                    chordIndex = (chordIndex + 1) % progressions.length;
                    Thread.sleep(600); // Pause between chord changes
                }
                sdl.drain();
                sdl.close();
            } catch (Exception ex) {
                // music failed
            }
        });
        musicThread.start();
    }

    private void stopSpaceMusic() {
        musicPlaying = false;
        if (musicThread != null) {
            musicThread.interrupt();
            musicThread = null;
        }
    }

    @Override
    public void dispose() {
        stopSpaceMusic();
        loopTimer.stop();
        super.dispose();
    }
}

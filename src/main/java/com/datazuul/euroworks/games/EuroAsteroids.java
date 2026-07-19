package com.datazuul.euroworks.games;

import com.datazuul.euroworks.apps.EuroAppFrame;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Path2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * EuroAsteroids - Classic Asteroids (Atari 1979) replica for EuroWorks.
 * Features:
 * - Ship with realistic vector thrust physics, rotation inertia, drag, and wrapping bounds.
 * - Keybindings for Left/Right (rotation), Up/W (thrust), Space (fire laser), and Shift/Enter (hyperspace).
 * - Vector wireframe aesthetics matching the 1979 vector display.
 * - Dynamic asteroid splitting (Large -> 2x Medium -> 2x Small) with randomized vector shapes.
 * - Bullet collision checks and ship invulnerability frames upon spawn.
 * - Level wave progression (increasing starting asteroids).
 * - HighScore integration saving score and duration.
 * - Retro sound effects for thrusting hum, fire sound, and explosion rumbles.
 */
public class EuroAsteroids extends EuroAppFrame {

    private enum GameState {
        LAUNCH, RUNNING, PAUSED, GAME_OVER, LEVEL_COMPLETE
    }

    private static final int GAME_WIDTH = 440;
    private static final int GAME_HEIGHT = 500;

    private static final int PLAY_WIDTH = 420;
    private static final int PLAY_HEIGHT = 440;

    // Game states
    private GameState state = GameState.LAUNCH;
    private int score = 0;
    private int lives = 3;
    private int wave = 1;
    private long gameStartTime = 0;

    // Ship variables
    private double shipX = 220;
    private double shipY = 270;
    private double shipVx = 0;
    private double shipVy = 0;
    private double shipAngle = -Math.PI / 2; // pointing up
    private static final double DRAG = 0.99;
    private static final double THRUST_ACCEL = 0.15;
    private static final double MAX_SPEED = 6.0;
    private static final double ROTATION_SPEED = 0.08;

    private boolean invulnerable = false;
    private long invulnerableUntil = 0;

    // Controls flags
    private boolean keyRotateLeft = false;
    private boolean keyRotateRight = false;
    private boolean keyThrust = false;

    // Bullets
    private static class Bullet {
        double x, y;
        double vx, vy;
        int life = 45; // ~750ms lifetime

        Bullet(double x, double y, double angle, double shipVx, double shipVy) {
            this.x = x;
            this.y = y;
            // Add relative speed of ship
            this.vx = shipVx + Math.cos(angle) * 7.0;
            this.vy = shipVy + Math.sin(angle) * 7.0;
        }

        void update() {
            x += vx;
            y += vy;
            life--;

            // Wrap bullets
            if (x < 0) x = PLAY_WIDTH;
            else if (x > PLAY_WIDTH) x = 0;

            if (y < 0) y = PLAY_HEIGHT;
            else if (y > PLAY_HEIGHT) y = 0;
        }
    }
    private final List<Bullet> bullets = new ArrayList<>();

    // Asteroids
    private static class Asteroid {
        double x, y;
        double vx, vy;
        int size; // 3 = Large, 2 = Medium, 1 = Small
        double radius;
        Polygon polygon;

        Asteroid(double x, double y, int size) {
            this.x = x;
            this.y = y;
            this.size = size;

            Random rand = new Random();
            if (size == 3) {
                this.radius = 28 + rand.nextInt(8);
            } else if (size == 2) {
                this.radius = 16 + rand.nextInt(6);
            } else {
                this.radius = 8 + rand.nextInt(4);
            }

            // Generate randomized vertices for vector look
            int numPoints = 8 + rand.nextInt(5);
            int[] px = new int[numPoints];
            int[] py = new int[numPoints];
            for (int i = 0; i < numPoints; i++) {
                double angle = (2 * Math.PI * i) / numPoints;
                double r = radius * (0.85 + rand.nextDouble() * 0.3);
                px[i] = (int) (Math.cos(angle) * r);
                py[i] = (int) (Math.sin(angle) * r);
            }
            this.polygon = new Polygon(px, py, numPoints);

            // Random direction
            double speed = 0.5 + (4 - size) * 0.4 + rand.nextDouble() * 0.5;
            double angle = rand.nextDouble() * 2 * Math.PI;
            this.vx = Math.cos(angle) * speed;
            this.vy = Math.sin(angle) * speed;
        }

        void update() {
            x += vx;
            y += vy;

            // Wrap asteroids
            if (x < -radius) x = PLAY_WIDTH + radius;
            else if (x > PLAY_WIDTH + radius) x = -radius;

            if (y < -radius) y = PLAY_HEIGHT + radius;
            else if (y > PLAY_HEIGHT + radius) y = -radius;
        }
    }
    private final List<Asteroid> asteroids = new ArrayList<>();

    // Explosion particles
    private static class Particle {
        double x, y;
        double vx, vy;
        int life;

        Particle(double x, double y) {
            this.x = x;
            this.y = y;
            Random rand = new Random();
            double angle = rand.nextDouble() * 2 * Math.PI;
            double speed = 1.0 + rand.nextDouble() * 2.0;
            this.vx = Math.cos(angle) * speed;
            this.vy = Math.sin(angle) * speed;
            this.life = 20 + rand.nextInt(15);
        }
    }
    private final List<Particle> particles = new ArrayList<>();

    private final AsteroidsPanel playPanel;
    private final Timer loopTimer;
    private final Random random = new Random();
    private boolean soundRunning = false;

    public EuroAsteroids() {
        super("EuroAsteroids (Asteroids)");
        setSize(GAME_WIDTH + 16, GAME_HEIGHT + 74);
        setJMenuBar(buildMenuBar());

        playPanel = new AsteroidsPanel();
        setContentPane(playPanel);

        // Game loop (~60 FPS)
        loopTimer = new Timer(16, e -> updateGame());

        setupKeyboardBindings();
        resetGame();
    }

    private JMenuBar buildMenuBar() {
        JMenuBar mb = new JMenuBar();
        JMenu menu = new JMenu("Spiel");
        menu.setFont(new Font("SansSerif", Font.PLAIN, 11));

        JMenuItem restartItem = new JMenuItem("Neues Spiel (F2)");
        restartItem.addActionListener(e -> resetGame());
        menu.add(restartItem);

        JMenuItem pauseItem = new JMenuItem("Pause (P)");
        pauseItem.addActionListener(e -> togglePause());
        menu.add(pauseItem);

        menu.addSeparator();
        JMenuItem highscoresItem = new JMenuItem("Bestenliste...");
        highscoresItem.addActionListener(e -> showHighScoresDialog());
        menu.add(highscoresItem);

        menu.addSeparator();
        JMenuItem exitItem = new JMenuItem("Beenden");
        exitItem.addActionListener(e -> dispose());
        menu.add(exitItem);

        mb.add(menu);
        return mb;
    }

    private void setupKeyboardBindings() {
        InputMap im = playPanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = playPanel.getActionMap();

        // Key Press mappings
        im.put(KeyStroke.getKeyStroke("LEFT"), "pressLeft");
        im.put(KeyStroke.getKeyStroke("A"), "pressLeft");
        am.put("pressLeft", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { keyRotateLeft = true; }
        });

        im.put(KeyStroke.getKeyStroke("RIGHT"), "pressRight");
        im.put(KeyStroke.getKeyStroke("D"), "pressRight");
        am.put("pressRight", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { keyRotateRight = true; }
        });

        im.put(KeyStroke.getKeyStroke("UP"), "pressUp");
        im.put(KeyStroke.getKeyStroke("W"), "pressUp");
        am.put("pressUp", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { keyThrust = true; }
        });

        // Key Release mappings
        im.put(KeyStroke.getKeyStroke("released LEFT"), "releaseLeft");
        im.put(KeyStroke.getKeyStroke("released A"), "releaseLeft");
        am.put("releaseLeft", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { keyRotateLeft = false; }
        });

        im.put(KeyStroke.getKeyStroke("released RIGHT"), "releaseRight");
        im.put(KeyStroke.getKeyStroke("released D"), "releaseRight");
        am.put("releaseRight", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { keyRotateRight = false; }
        });

        im.put(KeyStroke.getKeyStroke("released UP"), "releaseUp");
        im.put(KeyStroke.getKeyStroke("released W"), "releaseUp");
        am.put("releaseUp", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { keyThrust = false; }
        });

        // Laser Firing (Space)
        im.put(KeyStroke.getKeyStroke("SPACE"), "fire");
        am.put("fire", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (state == GameState.LAUNCH) {
                    startGame();
                } else if (state == GameState.RUNNING) {
                    fireLaser();
                }
            }
        });

        // Hyperspace Teleport (Shift or Enter)
        im.put(KeyStroke.getKeyStroke("SHIFT"), "hyperspace");
        im.put(KeyStroke.getKeyStroke("ENTER"), "hyperspace");
        am.put("hyperspace", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (state == GameState.RUNNING) {
                    triggerHyperspace();
                }
            }
        });

        // Settings Shortcuts
        im.put(KeyStroke.getKeyStroke("P"), "pause");
        am.put("pause", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { togglePause(); }
        });

        im.put(KeyStroke.getKeyStroke("F2"), "restart");
        am.put("restart", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { resetGame(); }
        });
    }

    private void resetGame() {
        score = 0;
        lives = 3;
        wave = 1;
        state = GameState.LAUNCH;
        gameStartTime = 0;

        resetShip();
        asteroids.clear();
        bullets.clear();
        particles.clear();

        loopTimer.start();
        playPanel.repaint();
    }

    private void resetShip() {
        shipX = PLAY_WIDTH / 2.0;
        shipY = PLAY_HEIGHT / 2.0;
        shipVx = 0;
        shipVy = 0;
        shipAngle = -Math.PI / 2;
        invulnerable = true;
        invulnerableUntil = System.currentTimeMillis() + 2000; // 2 seconds safety
    }

    private void startGame() {
        state = GameState.RUNNING;
        gameStartTime = System.currentTimeMillis();
        generateAsteroids();
        playLevelStartSound();
    }

    private void togglePause() {
        if (state == GameState.RUNNING) {
            state = GameState.PAUSED;
            loopTimer.stop();
        } else if (state == GameState.PAUSED) {
            state = GameState.RUNNING;
            loopTimer.start();
        }
        playPanel.repaint();
    }

    private void generateAsteroids() {
        asteroids.clear();
        int count = 3 + wave; // start with wave + 3 large asteroids

        for (int i = 0; i < count; i++) {
            // Keep safe spawn distance from center ship
            double ax, ay;
            do {
                ax = random.nextInt(PLAY_WIDTH);
                ay = random.nextInt(PLAY_HEIGHT);
            } while (Math.hypot(ax - shipX, ay - shipY) < 100);

            asteroids.add(new Asteroid(ax, ay, 3));
        }
    }

    private void fireLaser() {
        // Tip of ship
        double tx = shipX + Math.cos(shipAngle) * 12;
        double ty = shipY + Math.sin(shipAngle) * 12;

        bullets.add(new Bullet(tx, ty, shipAngle, shipVx, shipVy));
        playFireSound();
    }

    private void triggerHyperspace() {
        // Relocate randomly
        shipX = random.nextInt(PLAY_WIDTH - 30) + 15;
        shipY = random.nextInt(PLAY_HEIGHT - 30) + 15;
        shipVx = 0;
        shipVy = 0;

        // Visual teleport explosion
        for (int i = 0; i < 8; i++) {
            particles.add(new Particle(shipX, shipY));
        }

        // 10% chance of sudden death on reentry (authentic retro danger)
        if (random.nextInt(10) == 0) {
            handleShipDeath();
        } else {
            // Safe spawn window
            invulnerable = true;
            invulnerableUntil = System.currentTimeMillis() + 1000;
            playExplosionSound(120);
        }
    }

    private void updateGame() {
        if (state != GameState.RUNNING) return;

        // 1. Handle Ship Input Rotation
        if (keyRotateLeft) {
            shipAngle -= ROTATION_SPEED;
        }
        if (keyRotateRight) {
            shipAngle += ROTATION_SPEED;
        }

        // 2. Thrust Physics
        if (keyThrust) {
            shipVx += Math.cos(shipAngle) * THRUST_ACCEL;
            shipVy += Math.sin(shipAngle) * THRUST_ACCEL;

            // Cap max speed
            double speed = Math.hypot(shipVx, shipVy);
            if (speed > MAX_SPEED) {
                shipVx = (shipVx / speed) * MAX_SPEED;
                shipVy = (shipVy / speed) * MAX_SPEED;
            }

            if (random.nextInt(5) == 0) {
                playThrustHum();
            }
        }

        // 3. Move Ship
        shipX += shipVx;
        shipY += shipVy;
        // Drag
        shipVx *= DRAG;
        shipVy *= DRAG;

        // Wrap Ship bounds
        if (shipX < 0) shipX = PLAY_WIDTH;
        else if (shipX > PLAY_WIDTH) shipX = 0;

        if (shipY < 0) shipY = PLAY_HEIGHT;
        else if (shipY > PLAY_HEIGHT) shipY = 0;

        // Check invulnerability expiration
        if (invulnerable && System.currentTimeMillis() > invulnerableUntil) {
            invulnerable = false;
        }

        // 4. Update Bullets
        Iterator<Bullet> bIt = bullets.iterator();
        while (bIt.hasNext()) {
            Bullet b = bIt.next();
            b.update();
            if (b.life <= 0) {
                bIt.remove();
            }
        }

        // 5. Update Asteroids
        for (Asteroid a : asteroids) {
            a.update();
        }

        // 6. Update Particles
        Iterator<Particle> pIt = particles.iterator();
        while (pIt.hasNext()) {
            Particle p = pIt.next();
            p.x += p.vx;
            p.y += p.vy;
            p.life--;
            if (p.life <= 0) {
                pIt.remove();
            }
        }

        // 7. Check Bullet vs Asteroid Collisions
        Iterator<Bullet> bulletIterator = bullets.iterator();
        while (bulletIterator.hasNext()) {
            Bullet b = bulletIterator.next();
            boolean hit = false;

            Iterator<Asteroid> asteroidIterator = asteroids.iterator();
            List<Asteroid> toAdd = new ArrayList<>();
            while (asteroidIterator.hasNext()) {
                Asteroid a = asteroidIterator.next();
                double dist = Math.hypot(b.x - a.x, b.y - a.y);
                if (dist <= a.radius) {
                    hit = true;
                    asteroidIterator.remove();

                    // Split asteroid
                    splitAsteroid(a, toAdd);
                    break;
                }
            }

            if (hit) {
                bulletIterator.remove();
                asteroids.addAll(toAdd);
            }
        }

        // 8. Check Ship vs Asteroid Collision
        if (!invulnerable) {
            for (Asteroid a : asteroids) {
                double dist = Math.hypot(shipX - a.x, shipY - a.y);
                // Approx ship radius of 8 pixels
                if (dist <= a.radius + 8) {
                    handleShipDeath();
                    break;
                }
            }
        }

        // 9. Wave completion
        if (asteroids.isEmpty()) {
            nextLevel();
        }

        playPanel.repaint();
    }

    private void splitAsteroid(Asteroid a, List<Asteroid> toAdd) {
        // Emit particles
        for (int i = 0; i < 8; i++) {
            particles.add(new Particle(a.x, a.y));
        }

        if (a.size == 3) {
            score += 20 * wave;
            toAdd.add(new Asteroid(a.x, a.y, 2));
            toAdd.add(new Asteroid(a.x, a.y, 2));
            playExplosionSound(180);
        } else if (a.size == 2) {
            score += 50 * wave;
            toAdd.add(new Asteroid(a.x, a.y, 1));
            toAdd.add(new Asteroid(a.x, a.y, 1));
            playExplosionSound(250);
        } else {
            score += 100 * wave;
            playExplosionSound(350);
        }
    }

    private void handleShipDeath() {
        lives--;
        // Heavy particle count
        for (int i = 0; i < 20; i++) {
            particles.add(new Particle(shipX, shipY));
        }
        playExplosionSound(90);

        if (lives <= 0) {
            state = GameState.GAME_OVER;
            loopTimer.stop();
            checkAndPromptHighScore();
        } else {
            resetShip();
        }
    }

    private void nextLevel() {
        state = GameState.LEVEL_COMPLETE;
        loopTimer.stop();
        wave++;
        score += 500;
        playLevelUpSound();

        Timer delay = new Timer(2000, e -> {
            resetShip();
            generateAsteroids();
            state = GameState.RUNNING;
            loopTimer.start();
        });
        delay.setRepeats(false);
        delay.start();
    }

    private void checkAndPromptHighScore() {
        if (score <= 0) return;

        int durationSecs = 0;
        if (gameStartTime > 0) {
            durationSecs = (int) ((System.currentTimeMillis() - gameStartTime) / 1000);
        }
        final int finalDuration = durationSecs;

        HighScore hs = new HighScore("EuroAsteroids");
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
                    "Glückwunsch! Neuer High Score: " + score + " Punkte (in " + finalDuration + " Sek)\nBitte Name eingeben:",
                    "Neuer High Score",
                    JOptionPane.PLAIN_MESSAGE
                );
                if (username != null && !username.trim().isEmpty()) {
                    try {
                        hs.setHighScore(score, username.trim(), finalDuration);
                        showHighScoresDialog();
                    } catch (IOException ex) {
                        JOptionPane.showMessageDialog(this, "Fehler beim Speichern des Highscores.", "Fehler", JOptionPane.ERROR_MESSAGE);
                    }
                }
            });
        }
    }

    private void showHighScoresDialog() {
        HighScore hs = new HighScore("EuroAsteroids");
        List<HighScore.ScoreEntry> scores = hs.getScores();
        StringBuilder sb = new StringBuilder();
        sb.append("EuroAsteroids Bestenliste (Top 10):\n\n");
        if (scores.isEmpty()) {
            sb.append("Keine Einträge vorhanden.");
        } else {
            int limit = Math.min(10, scores.size());
            for (int i = 0; i < limit; i++) {
                HighScore.ScoreEntry entry = scores.get(i);
                sb.append(String.format("%d. %s - %d Punkte (%d Sek) (%s)\n",
                    (i + 1),
                    entry.username,
                    entry.score,
                    entry.timeNeeded,
                    new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm").format(entry.date)
                ));
            }
        }
        JOptionPane.showMessageDialog(this, sb.toString(), "Bestenliste", JOptionPane.INFORMATION_MESSAGE);
    }

    // ── Retro Sound Synthesizer ─────────────────────────────────────────────

    private void playSynthesizedSound(int[] freqs, int[] durationsMs, boolean square) {
        if (soundRunning) return;
        soundRunning = true;
        new Thread(() -> {
            try {
                int sampleRate = 8000;
                int totalDurationMs = 0;
                for (int d : durationsMs) totalDurationMs += d;

                byte[] buf = new byte[totalDurationMs * sampleRate / 1000];
                int offset = 0;

                for (int step = 0; step < freqs.length; step++) {
                    int freq = freqs[step];
                    int dur = durationsMs[step];
                    int samples = dur * sampleRate / 1000;
                    double phase = 0.0;

                    for (int i = 0; i < samples; i++) {
                        phase += 2.0 * Math.PI * freq / sampleRate;
                        if (square) {
                            buf[offset + i] = (byte) (Math.sin(phase) >= 0.0 ? 25 : -25);
                        } else {
                            buf[offset + i] = (byte) (35 * Math.sin(phase));
                        }
                    }
                    offset += samples;
                }

                javax.sound.sampled.AudioFormat af = new javax.sound.sampled.AudioFormat(sampleRate, 8, 1, true, false);
                javax.sound.sampled.SourceDataLine sdl = javax.sound.sampled.AudioSystem.getSourceDataLine(af);
                sdl.open(af);
                sdl.start();
                sdl.write(buf, 0, buf.length);
                sdl.drain();
                sdl.close();
            } catch (Exception ex) {
                // sound failed (mute fallback)
            } finally {
                soundRunning = false;
            }
        }).start();
    }

    private void playFireSound() {
        playSynthesizedSound(new int[]{880, 1100, 1300}, new int[]{25, 25, 30}, true);
    }

    private void playThrustHum() {
        playSynthesizedSound(new int[]{70, 80}, new int[]{40, 40}, false);
    }

    private void playExplosionSound(int freq) {
        playSynthesizedSound(new int[]{freq, freq - 40, freq - 80}, new int[]{100, 100, 150}, false);
    }

    private void playLevelStartSound() {
        playSynthesizedSound(new int[]{587, 659, 784}, new int[]{100, 100, 200}, false);
    }

    private void playLevelUpSound() {
        playSynthesizedSound(new int[]{523, 659, 784, 1047}, new int[]{100, 100, 100, 250}, false);
    }

    @Override
    public void dispose() {
        loopTimer.stop();
        state = GameState.GAME_OVER;
        super.dispose();
    }

    // ── Panel Graphics Painting ─────────────────────────────────────────────

    private class AsteroidsPanel extends JPanel {

        AsteroidsPanel() {
            setBackground(Color.BLACK);
            setPreferredSize(new Dimension(GAME_WIDTH, GAME_HEIGHT));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            double actualWidth = getWidth() - 20;
            double actualHeight = getHeight() - 60;
            double scale = Math.min(actualWidth / (double) PLAY_WIDTH, actualHeight / (double) PLAY_HEIGHT);
            if (scale < 0.1) scale = 0.1;

            int xOffset = (getWidth() - (int) (PLAY_WIDTH * scale)) / 2;
            int yOffset = 50 + (getHeight() - 50 - (int) (PLAY_HEIGHT * scale)) / 2;
            if (yOffset < 50) yOffset = 50;

            g2.translate(xOffset, yOffset);
            g2.scale(scale, scale);

            // Outer boundary border box (retro cyan color)
            g2.setColor(new Color(0, 128, 128));
            g2.drawRect(0, 0, PLAY_WIDTH, PLAY_HEIGHT);

            // Draw Asteroids (vector lines style)
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(1.2f));
            for (Asteroid a : asteroids) {
                g2.translate(a.x, a.y);
                g2.draw(a.polygon);
                g2.translate(-a.x, -a.y);
            }

            // Draw Bullets (tiny boxes)
            for (Bullet b : bullets) {
                g2.setColor(Color.GREEN);
                g2.fillRect((int) b.x - 1, (int) b.y - 1, 3, 3);
            }

            // Draw Particles
            g2.setColor(Color.ORANGE);
            for (Particle p : particles) {
                g2.fillRect((int) p.x, (int) p.y, 2, 2);
            }

            // Draw Ship (vector triangle)
            if (state == GameState.RUNNING || state == GameState.LEVEL_COMPLETE) {
                if (!invulnerable || (System.currentTimeMillis() / 150) % 2 == 0) {
                    // Coordinates of triangle relative to shipX, shipY
                    double cos = Math.cos(shipAngle);
                    double sin = Math.sin(shipAngle);
                    double cosR = Math.cos(shipAngle + (5 * Math.PI / 6));
                    double sinR = Math.sin(shipAngle + (5 * Math.PI / 6));
                    double cosL = Math.cos(shipAngle - (5 * Math.PI / 6));
                    double sinL = Math.sin(shipAngle - (5 * Math.PI / 6));

                    int tx = (int) (shipX + cos * 12);
                    int ty = (int) (shipY + sin * 12);
                    int rx = (int) (shipX + cosR * 9);
                    int ry = (int) (shipY + sinR * 9);
                    int lx = (int) (shipX + cosL * 9);
                    int ly = (int) (shipY + sinL * 9);

                    Path2D path = new Path2D.Double();
                    path.moveTo(tx, ty);
                    path.lineTo(rx, ry);
                    // Draw a small indentation at the bottom of ship
                    int bx = (int) (shipX - cos * 3);
                    int by = (int) (shipY - sin * 3);
                    path.lineTo(bx, by);
                    path.lineTo(lx, ly);
                    path.closePath();

                    g2.setColor(invulnerable ? Color.CYAN : Color.GREEN);
                    g2.setStroke(new BasicStroke(1.5f));
                    g2.draw(path);

                    // Shield indicator
                    if (invulnerable) {
                        g2.setColor(new Color(0, 255, 255, 120));
                        g2.drawOval((int) shipX - 16, (int) shipY - 16, 32, 32);
                    }

                    // Thrust flame
                    if (keyThrust && (System.currentTimeMillis() / 50) % 2 == 0) {
                        int fx = (int) (shipX - cos * 12);
                        int fy = (int) (shipY - sin * 12);
                        g2.setColor(Color.RED);
                        g2.drawLine(bx, by, fx, fy);
                    }
                }
            }

            // Draw Overlays
            if (state == GameState.LAUNCH) {
                g2.setColor(new Color(0, 0, 0, 180));
                g2.fillRect(0, 0, PLAY_WIDTH, PLAY_HEIGHT);
                g2.setFont(new Font("SansSerif", Font.BOLD, 18));
                g2.setColor(Color.GREEN);
                drawCenteredString(g2, "EUROASTEROIDS 1979", PLAY_HEIGHT / 2 - 30);
                g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
                g2.setColor(Color.WHITE);
                drawCenteredString(g2, "Drücke LEERTASTE zum Starten", PLAY_HEIGHT / 2 + 10);
                drawCenteredString(g2, "A / D oder Links / Rechts = Drehen", PLAY_HEIGHT / 2 + 35);
                drawCenteredString(g2, "W oder Pfeil Oben = Schubkraft", PLAY_HEIGHT / 2 + 55);
                drawCenteredString(g2, "LEERTASTE = Schießen", PLAY_HEIGHT / 2 + 75);
                drawCenteredString(g2, "SHIFT / ENTER = Hypersprung", PLAY_HEIGHT / 2 + 95);
            } else if (state == GameState.PAUSED) {
                g2.setColor(new Color(0, 0, 0, 160));
                g2.fillRect(0, 0, PLAY_WIDTH, PLAY_HEIGHT);
                g2.setFont(new Font("SansSerif", Font.BOLD, 22));
                g2.setColor(Color.YELLOW);
                drawCenteredString(g2, "PAUSE", PLAY_HEIGHT / 2);
            } else if (state == GameState.GAME_OVER) {
                g2.setColor(new Color(0, 0, 0, 200));
                g2.fillRect(0, 0, PLAY_WIDTH, PLAY_HEIGHT);
                g2.setFont(new Font("SansSerif", Font.BOLD, 24));
                g2.setColor(Color.RED);
                drawCenteredString(g2, "GAME OVER", PLAY_HEIGHT / 2 - 20);
                g2.setFont(new Font("SansSerif", Font.PLAIN, 14));
                g2.setColor(Color.WHITE);
                drawCenteredString(g2, "Drücke F2 für Neues Spiel", PLAY_HEIGHT / 2 + 20);
            } else if (state == GameState.LEVEL_COMPLETE) {
                g2.setColor(new Color(0, 0, 0, 150));
                g2.fillRect(0, 0, PLAY_WIDTH, PLAY_HEIGHT);
                g2.setFont(new Font("SansSerif", Font.BOLD, 24));
                g2.setColor(Color.GREEN);
                drawCenteredString(g2, "WELLE ÜBERSTANDEN!", PLAY_HEIGHT / 2 - 20);
            }

            g2.dispose();

            // Draw HUD on the outer non-scaled panel top
            Graphics2D hudG = (Graphics2D) g.create();
            hudG.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            hudG.setFont(new Font("Monospaced", Font.BOLD, 14));
            hudG.setColor(Color.WHITE);
            int width = getWidth();
            hudG.drawString(String.format("SCORE: %05d", score), 25, 34);
            hudG.drawString(String.format("LIVES: %d", lives), width / 2 - 40, 34);
            hudG.drawString(String.format("WAVE: %d", wave), width - 120, 34);
            hudG.dispose();
        }

        private void drawCenteredString(Graphics2D g2, String text, int y) {
            FontMetrics fm = g2.getFontMetrics();
            int x = (PLAY_WIDTH - fm.stringWidth(text)) / 2;
            g2.drawString(text, x, y);
        }
    }
}

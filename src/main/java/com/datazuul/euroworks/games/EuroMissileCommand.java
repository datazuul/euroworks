package com.datazuul.euroworks.games;

import com.datazuul.euroworks.apps.EuroAppFrame;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * EuroMissileCommand - Retro Missile Command (Atari 1980) replica for EuroWorks.
 * Features:
 * - 6 Cities and 3 Missile Silos (Left, Center, Right) defending the ground.
 * - Mouse targeting with a custom retro crosshair.
 * - Circular expanding/contracting explosions with radial collision detection.
 * - Incoming enemy ballistic trails descending from the sky.
 * - Auto-selects the nearest operational silo with remaining ammunition to fire.
 * - Wave progression with increasing speed, multiplier, and missile counts.
 * - HighScore integration saving scores and duration.
 * - Programmatic retro sound effects for firing, explosions, and base losses.
 */
public class EuroMissileCommand extends EuroAppFrame {

    private enum GameState {
        LAUNCH, RUNNING, PAUSED, GAME_OVER, WAVE_COMPLETE
    }

    private static final int GAME_WIDTH = 440;
    private static final int GAME_HEIGHT = 500;

    // Game states
    private GameState state = GameState.LAUNCH;
    private int score = 0;
    private int wave = 1;
    private long gameStartTime = 0;
    private int mouseX = 220;
    private int mouseY = 250;

    // Cities
    private static class City {
        int x, y;
        boolean active = true;

        City(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }
    private final List<City> cities = new ArrayList<>();

    // Silos
    private static class Silo {
        int x, y;
        int missiles = 10;
        boolean active = true;

        Silo(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }
    private final Silo[] silos = new Silo[3];

    // Incoming missiles
    private static class EnemyMissile {
        double startX, startY;
        double currentX, currentY;
        double targetX, targetY;
        double dx, dy;
        double speed;

        EnemyMissile(double sx, double sy, double tx, double ty, double speed) {
            this.startX = sx;
            this.startY = sy;
            this.currentX = sx;
            this.currentY = sy;
            this.targetX = tx;
            this.targetY = ty;
            this.speed = speed;

            double dist = Math.hypot(tx - sx, ty - sy);
            this.dx = (tx - sx) / dist;
            this.dy = (ty - sy) / dist;
        }
    }
    private final List<EnemyMissile> enemyMissiles = new ArrayList<>();

    // Counter missiles (fired by player)
    private static class PlayerMissile {
        double startX, startY;
        double currentX, currentY;
        double targetX, targetY;
        double dx, dy;
        double speed = 8.5;
        boolean reached = false;

        PlayerMissile(double sx, double sy, double tx, double ty) {
            this.startX = sx;
            this.startY = sy;
            this.currentX = sx;
            this.currentY = sy;
            this.targetX = tx;
            this.targetY = ty;

            double dist = Math.hypot(tx - sx, ty - sy);
            this.dx = (tx - sx) / dist;
            this.dy = (ty - sy) / dist;
        }
    }
    private final List<PlayerMissile> playerMissiles = new ArrayList<>();

    // Explosions
    private static class Explosion {
        double x, y;
        double radius = 1.0;
        double maxRadius = 32.0;
        boolean expanding = true;
        boolean finished = false;

        Explosion(double x, double y) {
            this.x = x;
            this.y = y;
        }

        void update() {
            if (expanding) {
                radius += 1.2;
                if (radius >= maxRadius) {
                    expanding = false;
                }
            } else {
                radius -= 0.8;
                if (radius <= 0) {
                    finished = true;
                }
            }
        }
    }
    private final List<Explosion> explosions = new ArrayList<>();

    // Wave parameters
    private int enemyMissilesRemainingToSpawn = 0;
    private long lastEnemySpawnTime = 0;
    private long enemySpawnIntervalMs = 2000;

    private final MissilePanel playPanel;
    private final Timer loopTimer;
    private final Random random = new Random();
    private boolean soundRunning = false;

    public EuroMissileCommand() {
        super("EuroMissileCommand (Missile Command)");
        setSize(GAME_WIDTH + 16, GAME_HEIGHT + 74);
        setJMenuBar(buildMenuBar());

        playPanel = new MissilePanel();
        setContentPane(playPanel);

        // Game loop (~60 FPS)
        loopTimer = new Timer(16, e -> updateGame());

        setupListeners();
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

    private void setupListeners() {
        MouseAdapter ma = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                mouseX = e.getX();
                mouseY = e.getY();
                playPanel.repaint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                mouseX = e.getX();
                mouseY = e.getY();
                playPanel.repaint();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (state == GameState.LAUNCH) {
                    startGame();
                } else if (state == GameState.RUNNING) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        firePlayerMissile(e.getX(), e.getY());
                    }
                }
            }
        };

        playPanel.addMouseListener(ma);
        playPanel.addMouseMotionListener(ma);

        // Bind keys F2, P
        InputMap im = playPanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = playPanel.getActionMap();

        im.put(KeyStroke.getKeyStroke("F2"), "restart");
        am.put("restart", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                resetGame();
            }
        });

        im.put(KeyStroke.getKeyStroke("P"), "pause");
        am.put("pause", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                togglePause();
            }
        });
    }

    private void resetGame() {
        score = 0;
        wave = 1;
        state = GameState.LAUNCH;
        gameStartTime = 0;

        cities.clear();
        // 6 Cities positioned at the bottom ground
        cities.add(new City(50, 460));
        cities.add(new City(100, 460));
        cities.add(new City(150, 460));
        cities.add(new City(260, 460));
        cities.add(new City(310, 460));
        cities.add(new City(360, 460));

        // 3 Silos (Left, Center, Right)
        silos[0] = new Silo(20, 460);
        silos[1] = new Silo(205, 460);
        silos[2] = new Silo(390, 460);

        enemyMissiles.clear();
        playerMissiles.clear();
        explosions.clear();

        loopTimer.start();
        playPanel.repaint();
    }

    private void startGame() {
        state = GameState.RUNNING;
        gameStartTime = System.currentTimeMillis();
        startWave();
    }

    private void startWave() {
        enemyMissiles.clear();
        playerMissiles.clear();
        explosions.clear();

        // Ammunition refilled
        for (Silo s : silos) {
            s.missiles = 10;
            s.active = true;
        }

        // Count of enemies based on wave
        enemyMissilesRemainingToSpawn = 12 + wave * 3;
        enemySpawnIntervalMs = Math.max(500, 2000 - wave * 150);
        lastEnemySpawnTime = System.currentTimeMillis();

        playChimeSound();
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

    private void firePlayerMissile(int targetX, int targetY) {
        // Find nearest active silo with missiles
        Silo bestSilo = null;
        double bestDist = Double.MAX_VALUE;

        for (Silo s : silos) {
            if (s.active && s.missiles > 0) {
                double dist = Math.hypot(s.x - targetX, s.y - targetY);
                if (dist < bestDist) {
                    bestDist = dist;
                    bestSilo = s;
                }
            }
        }

        if (bestSilo != null) {
            bestSilo.missiles--;
            playerMissiles.add(new PlayerMissile(bestSilo.x, bestSilo.y, targetX, targetY));
            playFireSound();
        }
    }

    private void spawnEnemyMissile() {
        double sx = random.nextInt(GAME_WIDTH - 20) + 10;
        double sy = 50; // top ceiling

        // Random target choice (either a city or a silo)
        double tx;
        double ty = 460;

        if (random.nextBoolean() && !cities.isEmpty()) {
            City c = cities.get(random.nextInt(cities.size()));
            tx = c.x;
        } else {
            Silo s = silos[random.nextInt(3)];
            tx = s.x;
        }

        double speed = 0.8 + wave * 0.15 + random.nextDouble() * 0.4;
        enemyMissiles.add(new EnemyMissile(sx, sy, tx, ty, speed));
    }

    private void updateGame() {
        if (state != GameState.RUNNING) return;

        long now = System.currentTimeMillis();

        // 1. Spawning enemy missiles
        if (enemyMissilesRemainingToSpawn > 0 && now - lastEnemySpawnTime > enemySpawnIntervalMs) {
            spawnEnemyMissile();
            enemyMissilesRemainingToSpawn--;
            lastEnemySpawnTime = now;
        }

        // 2. Update player missiles
        Iterator<PlayerMissile> pIt = playerMissiles.iterator();
        while (pIt.hasNext()) {
            PlayerMissile pm = pIt.next();
            pm.currentX += pm.dx * pm.speed;
            pm.currentY += pm.dy * pm.speed;

            // Reached target?
            double distToTarget = Math.hypot(pm.targetX - pm.currentX, pm.targetY - pm.currentY);
            if (distToTarget <= pm.speed || pm.currentY <= pm.targetY && pm.dy < 0 || pm.currentY >= pm.targetY && pm.dy > 0) {
                // Detonate
                explosions.add(new Explosion(pm.targetX, pm.targetY));
                playExplosionSound();
                pIt.remove();
            }
        }

        // 3. Update explosions
        Iterator<Explosion> exIt = explosions.iterator();
        while (exIt.hasNext()) {
            Explosion ex = exIt.next();
            ex.update();
            if (ex.finished) {
                exIt.remove();
            }
        }

        // 4. Update enemy missiles & check collisions with explosions
        Iterator<EnemyMissile> eIt = enemyMissiles.iterator();
        while (eIt.hasNext()) {
            EnemyMissile em = eIt.next();
            em.currentX += em.dx * em.speed;
            em.currentY += em.dy * em.speed;

            // Collision with active explosions
            boolean hitByExplosion = false;
            for (Explosion ex : explosions) {
                double dist = Math.hypot(em.currentX - ex.x, em.currentY - ex.y);
                if (dist <= ex.radius) {
                    hitByExplosion = true;
                    break;
                }
            }

            if (hitByExplosion) {
                score += 100 * wave;
                explosions.add(new Explosion(em.currentX, em.currentY));
                playExplosionSound();
                eIt.remove();
                continue;
            }

            // Hit ground target?
            if (em.currentY >= em.targetY) {
                // Determine target impact
                boolean hitCity = false;
                for (City c : cities) {
                    if (c.active && Math.abs(c.x - em.targetX) < 15) {
                        c.active = false;
                        hitCity = true;
                        break;
                    }
                }

                if (!hitCity) {
                    for (Silo s : silos) {
                        if (s.active && Math.abs(s.x - em.targetX) < 20) {
                            s.active = false;
                            s.missiles = 0; // destroyed for the wave
                            break;
                        }
                    }
                }

                explosions.add(new Explosion(em.currentX, em.currentY));
                playCrashSound();
                eIt.remove();
            }
        }

        // 5. Check Game Over
        boolean anyCityLeft = false;
        for (City c : cities) {
            if (c.active) {
                anyCityLeft = true;
                break;
            }
        }

        if (!anyCityLeft) {
            state = GameState.GAME_OVER;
            loopTimer.stop();
            checkAndPromptHighScore();
        }

        // 6. Check Wave Complete
        if (enemyMissilesRemainingToSpawn == 0 && enemyMissiles.isEmpty() && playerMissiles.isEmpty()) {
            waveComplete();
        }

        playPanel.repaint();
    }

    private void waveComplete() {
        state = GameState.WAVE_COMPLETE;
        loopTimer.stop();

        // Calculate bonus
        int unusedMissiles = 0;
        for (Silo s : silos) {
            if (s.active) unusedMissiles += s.missiles;
        }

        int activeCities = 0;
        for (City c : cities) {
            if (c.active) activeCities++;
        }

        int bonus = (unusedMissiles * 5 + activeCities * 100) * wave;
        score += bonus;

        playLevelUpSound();

        Timer delay = new Timer(3000, e -> {
            wave++;
            state = GameState.RUNNING;
            startWave();
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

        HighScore hs = new HighScore("EuroMissileCommand");
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
        HighScore hs = new HighScore("EuroMissileCommand");
        List<HighScore.ScoreEntry> scores = hs.getScores();
        StringBuilder sb = new StringBuilder();
        sb.append("EuroMissileCommand Bestenliste (Top 10):\n\n");
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
        playSynthesizedSound(new int[]{700, 600, 500}, new int[]{40, 40, 40}, true);
    }

    private void playExplosionSound() {
        playSynthesizedSound(new int[]{120, 80, 50}, new int[]{100, 100, 150}, false);
    }

    private void playCrashSound() {
        playSynthesizedSound(new int[]{200, 100, 60}, new int[]{150, 150, 200}, false);
    }

    private void playChimeSound() {
        playSynthesizedSound(new int[]{440, 554, 659}, new int[]{80, 80, 120}, false);
    }

    private void playLevelUpSound() {
        playSynthesizedSound(new int[]{523, 659, 784, 1047}, new int[]{100, 100, 100, 200}, false);
    }

    @Override
    public void dispose() {
        loopTimer.stop();
        state = GameState.GAME_OVER;
        super.dispose();
    }

    // ── Panel Graphics Painting ─────────────────────────────────────────────

    private class MissilePanel extends JPanel {

        MissilePanel() {
            setBackground(Color.BLACK);
            setPreferredSize(new Dimension(GAME_WIDTH, GAME_HEIGHT));
            // Hide standard cursor inside game area to draw retro crosshair
            setCursor(Toolkit.getDefaultToolkit().createCustomCursor(
                new java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB),
                new Point(0, 0), "blank"));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Draw HUD
            g2.setFont(new Font("Monospaced", Font.BOLD, 14));
            g2.setColor(Color.WHITE);
            g2.drawString(String.format("SCORE: %05d", score), 25, 34);
            g2.drawString(String.format("WAVE: %d", wave), 320, 34);

            // Draw Ground/Base strip
            g2.setColor(new Color(139, 69, 19)); // Brown terrain
            g2.fillRect(10, 470, GAME_WIDTH - 20, 20);

            // Draw Cities
            for (City c : cities) {
                if (c.active) {
                    g2.setColor(Color.CYAN);
                    // Draw retro building silhouettes
                    g2.fillRect(c.x - 12, c.y - 8, 24, 18);
                    g2.setColor(Color.BLUE);
                    g2.fillRect(c.x - 8, c.y - 12, 16, 4);
                    g2.setColor(Color.CYAN);
                    g2.fillOval(c.x - 4, c.y - 18, 8, 8);
                } else {
                    // Draw rubble
                    g2.setColor(Color.DARK_GRAY);
                    g2.fillRect(c.x - 10, c.y + 2, 20, 8);
                }
            }

            // Draw Silos & Ammo Dots
            for (Silo s : silos) {
                if (s.active) {
                    g2.setColor(Color.YELLOW);
                    // Draw battery shape
                    int[] xPoints = {s.x - 20, s.x, s.x + 20};
                    int[] yPoints = {s.y + 10, s.y - 15, s.y + 10};
                    g2.fillPolygon(xPoints, yPoints, 3);

                    // Draw remaining missiles as tiny dots in a stack
                    g2.setColor(Color.RED);
                    int drawn = 0;
                    for (int row = 0; row < 3; row++) {
                        for (int col = 0; col <= row; col++) {
                            if (drawn < s.missiles) {
                                int dx = s.x - (row * 5) + (col * 10);
                                int dy = s.y + (row * 6);
                                g2.fillRect(dx - 2, dy, 4, 4);
                                drawn++;
                            }
                        }
                    }
                } else {
                    // Ruined silo
                    g2.setColor(Color.DARK_GRAY);
                    g2.fillRect(s.x - 15, s.y - 2, 30, 12);
                }
            }

            // Draw enemy missiles (Red trails)
            for (EnemyMissile em : enemyMissiles) {
                g2.setColor(Color.RED);
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawLine((int) em.startX, (int) em.startY, (int) em.currentX, (int) em.currentY);
                // Draw target coordinates indicator
                g2.fillRect((int) em.currentX - 2, (int) em.currentY - 2, 4, 4);
            }

            // Draw player missiles (Blue trails)
            for (PlayerMissile pm : playerMissiles) {
                g2.setColor(Color.BLUE);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawLine((int) pm.startX, (int) pm.startY, (int) pm.currentX, (int) pm.currentY);
                // Draw target indicator
                g2.setColor(Color.WHITE);
                g2.drawRect((int) pm.targetX - 2, (int) pm.targetY - 2, 4, 4);
            }

            // Draw explosions
            for (Explosion ex : explosions) {
                // Color cycle explosion for retro flare effect
                Color explosionColor = (System.currentTimeMillis() / 50) % 2 == 0 ? Color.YELLOW : Color.ORANGE;
                g2.setColor(explosionColor);
                g2.fillOval((int) (ex.x - ex.radius), (int) (ex.y - ex.radius), (int) (ex.radius * 2), (int) (ex.radius * 2));
                g2.setColor(Color.WHITE);
                g2.drawOval((int) (ex.x - ex.radius), (int) (ex.y - ex.radius), (int) (ex.radius * 2), (int) (ex.radius * 2));
            }

            // Draw crosshair targeting cursor
            if (state == GameState.RUNNING) {
                g2.setColor(Color.WHITE);
                g2.drawLine(mouseX - 8, mouseY, mouseX + 8, mouseY);
                g2.drawLine(mouseX, mouseY - 8, mouseX, mouseY + 8);
            }

            // Draw Overlays
            if (state == GameState.LAUNCH) {
                g2.setColor(new Color(0, 0, 0, 180));
                g2.fillRect(10, 10, GAME_WIDTH - 20, GAME_HEIGHT - 20);
                g2.setFont(new Font("SansSerif", Font.BOLD, 18));
                g2.setColor(Color.RED);
                drawCenteredString(g2, "EURO-MISSILE COMMAND 1980", GAME_HEIGHT / 2 - 30);
                g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
                g2.setColor(Color.WHITE);
                drawCenteredString(g2, "Klicke mit der MAUS zum Starten", GAME_HEIGHT / 2 + 10);
                drawCenteredString(g2, "Bewege MAUS = Zielen", GAME_HEIGHT / 2 + 35);
                drawCenteredString(g2, "LINKSKLICK = Abwehrrakete abfeuern", GAME_HEIGHT / 2 + 55);
                drawCenteredString(g2, "Verteidige alle 6 Städte vor der Zerstörung", GAME_HEIGHT / 2 + 75);
            } else if (state == GameState.PAUSED) {
                g2.setColor(new Color(0, 0, 0, 160));
                g2.fillRect(10, 10, GAME_WIDTH - 20, GAME_HEIGHT - 20);
                g2.setFont(new Font("SansSerif", Font.BOLD, 22));
                g2.setColor(Color.YELLOW);
                drawCenteredString(g2, "PAUSE", GAME_HEIGHT / 2);
            } else if (state == GameState.GAME_OVER) {
                g2.setColor(new Color(0, 0, 0, 200));
                g2.fillRect(10, 10, GAME_WIDTH - 20, GAME_HEIGHT - 20);
                g2.setFont(new Font("SansSerif", Font.BOLD, 24));
                g2.setColor(Color.RED);
                drawCenteredString(g2, "GAME OVER", GAME_HEIGHT / 2 - 20);
                g2.setFont(new Font("SansSerif", Font.PLAIN, 14));
                g2.setColor(Color.WHITE);
                drawCenteredString(g2, "Drücke F2 für Neues Spiel", GAME_HEIGHT / 2 + 20);
            } else if (state == GameState.WAVE_COMPLETE) {
                g2.setColor(new Color(0, 0, 0, 150));
                g2.fillRect(10, 10, GAME_WIDTH - 20, GAME_HEIGHT - 20);
                g2.setFont(new Font("SansSerif", Font.BOLD, 24));
                g2.setColor(Color.GREEN);
                drawCenteredString(g2, "ANGRIFF ABGEWEHRT!", GAME_HEIGHT / 2 - 20);
            }

            g2.dispose();
        }

        private void drawCenteredString(Graphics2D g2, String text, int y) {
            FontMetrics fm = g2.getFontMetrics();
            int x = (getWidth() - fm.stringWidth(text)) / 2;
            g2.drawString(text, x, y);
        }
    }
}

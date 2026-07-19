package com.datazuul.euroworks.games;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import com.datazuul.euroworks.apps.EuroAppFrame;

/**
 * EuroPhoenix - Classic Phoenix (Amstar 1980) replica for EuroWorks.
 * Features:
 * - Player ship with horizontal movement and upward firing laser.
 * - Invulnerable Shield (Down arrow / S) with 1.6-second duration and 5-second
 * cooldown.
 * - Flapping wing bird formations that periodically break formation to swoop
 * and shoot fireballs.
 * - Boss level (Wave 3) featuring the giant Mothership with a destructible
 * shield belt protecting the alien pilot.
 * - Color cycling vector and sprite retro graphics.
 * - HighScore integration saving score and duration.
 * - Programmatic retro sound effects for laser fire, shield hums, bird dives,
 * and explosions.
 */
public class EuroPhoenix extends EuroAppFrame {

    private enum GameState {
        LAUNCH, RUNNING, PAUSED, GAME_OVER, WAVE_COMPLETE
    }

    private static final int GAME_WIDTH = 440;
    private static final int GAME_HEIGHT = 500;

    // Sprite definitions
    private static final String[] BIRD_WING_UP = {
            "00011000",
            "00111100",
            "11111111",
            "11011011",
            "10011001",
            "10000001",
            "00000000",
            "00000000"
    };

    private static final String[] BIRD_WING_DOWN = {
            "00011000",
            "00111100",
            "11111111",
            "11011011",
            "01111110",
            "00100100",
            "01000010",
            "10000001"
    };

    private static final String[] PLAYER_SHIP = {
            "000010000",
            "000111000",
            "001111100",
            "011111110",
            "111111111",
            "110000011",
            "100000001"
    };

    private static final String[] ALIEN_BOSS = {
            "00111100",
            "01100110",
            "11111111",
            "10111101",
            "01111110",
            "00100100"
    };

    // Game state variables
    private GameState state = GameState.LAUNCH;
    private int score = 0;
    private int lives = 3;
    private int wave = 1;
    private long gameStartTime = 0;

    // Player position and shield
    private double playerX = 200;
    private static final int PLAYER_Y = 440;
    private static final int PLAYER_SPEED = 4;
    private boolean shieldActive = false;
    private long shieldEndTime = 0;
    private long shieldNextAvailableTime = 0;

    // Bullets
    private static class Laser {
        double x, y;
        boolean fromPlayer;

        Laser(double x, double y, boolean fromPlayer) {
            this.x = x;
            this.y = y;
            this.fromPlayer = fromPlayer;
        }
    }

    private final List<Laser> lasers = new ArrayList<>();

    // Enemies
    private static class Bird {
        double spawnX, spawnY;
        double x, y;
        boolean active = true;
        boolean swooping = false;
        double swoopTime = 0;
        int wingFrame = 0;
        int type; // 0 = Small red, 1 = Large yellow

        Bird(double x, double y, int type) {
            this.spawnX = x;
            this.spawnY = y;
            this.x = x;
            this.y = y;
            this.type = type;
        }

        void update(double playerX, Random rand, List<Laser> listLasers) {
            wingFrame = (wingFrame + 1) % 30;

            if (swooping) {
                soopTimeUpdate(playerX);
                // Shoot fireballs downward
                if (rand.nextInt(120) == 0) {
                    listLasers.add(new Laser(x + 8, y + 12, false));
                }
            } else {
                // Stay in formation
                x = spawnX;
                y = spawnY;
            }
        }

        private void soopTimeUpdate(double playerX) {
            swoopTime += 0.02;
            y += 2.5; // descent speed
            // Sine wave horizontal wiggle towards player
            x = spawnX + Math.sin(swoopTime * 4) * 60;

            if (y > GAME_HEIGHT) {
                // Wrap back to top formation
                swooping = false;
                y = spawnY;
                x = spawnX;
            }
        }
    }

    private final List<Bird> birds = new ArrayList<>();

    // Boss Battle variables (Wave 3, 6, 9...)
    private boolean bossActive = false;
    private double bossX = 180;
    private double bossY = 80;
    private double bossDir = 1.2;
    private int bossPilotHealth = 5;
    private final int[] bossShieldBelt = new int[16]; // 16 columns of shield block health (starts at 3)

    // Key flags
    private boolean keyLeft = false;
    private boolean keyRight = false;

    private final PhoenixPanel playPanel;
    private final Timer loopTimer;
    private final Random random = new Random();
    private boolean soundRunning = false;

    public EuroPhoenix() {
        super("EuroPhoenix (Phoenix)");
        setSize(GAME_WIDTH + 16, GAME_HEIGHT + 74);
        setJMenuBar(buildMenuBar());

        playPanel = new PhoenixPanel();
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

        // Left Press/Release
        im.put(KeyStroke.getKeyStroke("LEFT"), "pressLeft");
        im.put(KeyStroke.getKeyStroke("A"), "pressLeft");
        am.put("pressLeft", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                keyLeft = true;
            }
        });

        im.put(KeyStroke.getKeyStroke("released LEFT"), "releaseLeft");
        im.put(KeyStroke.getKeyStroke("released A"), "releaseLeft");
        am.put("releaseLeft", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                keyLeft = false;
            }
        });

        // Right Press/Release
        im.put(KeyStroke.getKeyStroke("RIGHT"), "pressRight");
        im.put(KeyStroke.getKeyStroke("D"), "pressRight");
        am.put("pressRight", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                keyRight = true;
            }
        });

        im.put(KeyStroke.getKeyStroke("released RIGHT"), "releaseRight");
        im.put(KeyStroke.getKeyStroke("released D"), "releaseRight");
        am.put("releaseRight", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                keyRight = false;
            }
        });

        // Shield (Down Arrow or S)
        im.put(KeyStroke.getKeyStroke("DOWN"), "shield");
        im.put(KeyStroke.getKeyStroke("S"), "shield");
        am.put("shield", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (state == GameState.RUNNING) {
                    triggerShield();
                }
            }
        });

        // Shoot (Space)
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

        // Pause / Restart
        im.put(KeyStroke.getKeyStroke("P"), "pause");
        am.put("pause", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                togglePause();
            }
        });

        im.put(KeyStroke.getKeyStroke("F2"), "restart");
        am.put("restart", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                resetGame();
            }
        });
    }

    private void resetGame() {
        score = 0;
        lives = 3;
        wave = 1;
        state = GameState.LAUNCH;
        gameStartTime = 0;

        playerX = 200;
        shieldActive = false;
        lasers.clear();
        birds.clear();
        bossActive = false;

        loopTimer.start();
        playPanel.repaint();
    }

    private void startGame() {
        state = GameState.RUNNING;
        gameStartTime = System.currentTimeMillis();
        setupWave();
    }

    private void setupWave() {
        lasers.clear();
        birds.clear();

        // Wave 3, 6, 9 are boss motherships
        if (wave % 3 == 0) {
            bossActive = true;
            bossPilotHealth = 5 + (wave / 3);
            bossX = 180;
            bossY = 70;
            // Refill Boss shield belt segments
            for (int i = 0; i < 16; i++) {
                bossShieldBelt[i] = 3;
            }
            playLevelStartSound();
        } else {
            bossActive = false;
            // 2 rows of 8 birds
            for (int row = 0; row < 2; row++) {
                for (int col = 0; col < 8; col++) {
                    birds.add(new Bird(50 + col * 42, 80 + row * 32, row));
                }
            }
            playLevelStartSound();
        }
    }

    private void triggerShield() {
        long now = System.currentTimeMillis();
        if (!shieldActive && now >= shieldNextAvailableTime) {
            shieldActive = true;
            shieldEndTime = now + 1600; // 1.6s duration
            shieldNextAvailableTime = now + 5000; // 5s cooldown
            playShieldHum();
        }
    }

    private void fireLaser() {
        if (shieldActive)
            return;

        // Check if there is already a player laser on screen (limit to 1 like arcade)
        boolean playerLaserOnScreen = false;
        for (Laser l : lasers) {
            if (l.fromPlayer) {
                playerLaserOnScreen = true;
                break;
            }
        }

        if (!playerLaserOnScreen) {
            lasers.add(new Laser(playerX + 8, PLAYER_Y - 4, true));
            playFireSound();
        }
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

    private void updateGame() {
        if (state != GameState.RUNNING)
            return;

        long now = System.currentTimeMillis();

        // Check shield duration
        if (shieldActive && now > shieldEndTime) {
            shieldActive = false;
        }

        // Horizontal player movement
        if (!shieldActive) {
            if (keyLeft && playerX > 15) {
                playerX -= PLAYER_SPEED;
            }
            if (keyRight && playerX < GAME_WIDTH - 35) {
                playerX += PLAYER_SPEED;
            }
        }

        // 1. Update Lasers
        Iterator<Laser> lIt = lasers.iterator();
        while (lIt.hasNext()) {
            Laser l = lIt.next();
            if (l.fromPlayer) {
                l.y -= 7.0; // player laser speed
                if (l.y < 50) {
                    lIt.remove();
                }
            } else {
                l.y += 3.5; // enemy fireball speed
                if (l.y > GAME_HEIGHT - 20) {
                    lIt.remove();
                }
            }
        }

        // 2. Update Birds & handle diving swoop triggers
        if (!bossActive) {
            // Randomly trigger swooping
            if (random.nextInt(60) == 0) {
                List<Bird> inactiveBirds = new ArrayList<>();
                for (Bird b : birds) {
                    if (b.active && !b.swooping) {
                        inactiveBirds.add(b);
                    }
                }
                if (!inactiveBirds.isEmpty()) {
                    Bird toSwoop = inactiveBirds.get(random.nextInt(inactiveBirds.size()));
                    toSwoop.swooping = true;
                    toSwoop.swoopTime = 0;
                    playDiveSound();
                }
            }

            for (Bird b : birds) {
                if (b.active) {
                    b.update(playerX, random, lasers);
                }
            }
        } else {
            // Update Boss side movement
            bossX += bossDir;
            if (bossX < 40 || bossX > GAME_WIDTH - 120) {
                bossDir = -bossDir;
            }

            // Boss fires fireballs periodically
            if (random.nextInt(45) == 0) {
                lasers.add(new Laser(bossX + 20 + random.nextInt(40), bossY + 32, false));
            }
        }

        // 3. Collision Checks
        checkCollisions();

        // 4. Wave Complete condition
        checkWaveCompleteStatus();

        playPanel.repaint();
    }

    private void checkCollisions() {
        // Player Laser collisions
        Iterator<Laser> lIt = lasers.iterator();
        while (lIt.hasNext()) {
            Laser l = lIt.next();
            if (!l.fromPlayer)
                continue;

            boolean laserRemoved = false;

            if (!bossActive) {
                // Check birds hits
                for (Bird b : birds) {
                    if (b.active) {
                        // Bird bounds roughly 20x20
                        if (l.x >= b.x && l.x <= b.x + 20 && l.y >= b.y && l.y <= b.y + 16) {
                            b.active = false;
                            score += b.swooping ? 100 * wave : 50 * wave;
                            playExplosionSound(200);
                            lIt.remove();
                            laserRemoved = true;
                            break;
                        }
                    }
                }
            } else {
                // Check Boss Hit (Mothership)
                // Width 80, height 40
                if (l.x >= bossX && l.x <= bossX + 80 && l.y >= bossY && l.y <= bossY + 40) {
                    // Check shield belt hit (Y = bossY + 28)
                    if (l.y >= bossY + 24 && l.y <= bossY + 34) {
                        int colIndex = (int) ((l.x - bossX) / 5);
                        if (colIndex >= 0 && colIndex < 16) {
                            if (bossShieldBelt[colIndex] > 0) {
                                bossShieldBelt[colIndex]--;
                                score += 10;
                                playExplosionSound(300);
                                lIt.remove();
                                laserRemoved = true;
                            }
                        }
                    }

                    // Check center Core pilot hit (X center: bossX + 32 to bossX + 48, Y: bossY to
                    // bossY + 20)
                    if (!laserRemoved && l.x >= bossX + 32 && l.x <= bossX + 48 && l.y >= bossY && l.y <= bossY + 20) {
                        // Only hits if shield barrier in front is destroyed!
                        int centralCol1 = 7;
                        int centralCol2 = 8;
                        if (bossShieldBelt[centralCol1] == 0 && bossShieldBelt[centralCol2] == 0) {
                            bossPilotHealth--;
                            score += 250;
                            playExplosionSound(120);

                            if (bossPilotHealth <= 0) {
                                bossActive = false;
                                score += 1000 * wave;
                                nextLevel();
                            }
                            lIt.remove();
                            laserRemoved = true;
                        } else {
                            // Laser blocked by shield
                            lIt.remove();
                            laserRemoved = true;
                        }
                    }
                }
            }
        }

        // Enemy Fireballs / Swooping bird collisions with Player Ship
        Iterator<Laser> eIt = lasers.iterator();
        while (eIt.hasNext()) {
            Laser l = eIt.next();
            if (l.fromPlayer)
                continue;

            // Player ship bounds: X [playerX, playerX + 18], Y PLAYER_Y to PLAYER_Y + 14
            if (l.x >= playerX && l.x <= playerX + 18 && l.y >= PLAYER_Y && l.y <= PLAYER_Y + 14) {
                if (shieldActive) {
                    // Disintegrated / Deflected
                    eIt.remove();
                } else {
                    handlePlayerDeath();
                    return;
                }
            }
        }

        // Swooping bird body collision
        if (!bossActive) {
            for (Bird b : birds) {
                if (b.active && b.swooping) {
                    if (b.x + 16 >= playerX && b.x <= playerX + 18 && b.y + 16 >= PLAYER_Y && b.y <= PLAYER_Y + 14) {
                        if (shieldActive) {
                            // Disintegrate bird
                            b.active = false;
                            score += 150 * wave;
                            playExplosionSound(200);
                        } else {
                            handlePlayerDeath();
                            return;
                        }
                    }
                }
            }
        }
    }

    private void handlePlayerDeath() {
        lives--;
        playExplosionSound(80);
        shieldActive = false;

        if (lives <= 0) {
            state = GameState.GAME_OVER;
            loopTimer.stop();
            checkAndPromptHighScore();
        } else {
            playerX = 200;
            // Clear lasers
            lasers.clear();
        }
    }

    private void checkWaveCompleteStatus() {
        if (bossActive)
            return;

        boolean anyBirdLeft = false;
        for (Bird b : birds) {
            if (b.active) {
                anyBirdLeft = true;
                break;
            }
        }

        if (!anyBirdLeft) {
            nextLevel();
        }
    }

    private void nextLevel() {
        state = GameState.WAVE_COMPLETE;
        loopTimer.stop();
        wave++;
        score += 500;
        playLevelUpSound();

        Timer delay = new Timer(2000, e -> {
            playerX = 200;
            setupWave();
            state = GameState.RUNNING;
            loopTimer.start();
        });
        delay.setRepeats(false);
        delay.start();
    }

    private void checkAndPromptHighScore() {
        if (score <= 0)
            return;

        int durationSecs = 0;
        if (gameStartTime > 0) {
            durationSecs = (int) ((System.currentTimeMillis() - gameStartTime) / 1000);
        }
        final int finalDuration = durationSecs;

        HighScore hs = new HighScore("EuroPhoenix");
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
                        "Glückwunsch! Neuer High Score: " + score + " Punkte (in " + finalDuration
                                + " Sek)\nBitte Name eingeben:",
                        "Neuer High Score",
                        JOptionPane.PLAIN_MESSAGE);
                if (username != null && !username.trim().isEmpty()) {
                    try {
                        hs.setHighScore(score, username.trim(), finalDuration);
                        showHighScoresDialog();
                    } catch (IOException ex) {
                        JOptionPane.showMessageDialog(this, "Fehler beim Speichern des Highscores.", "Fehler",
                                JOptionPane.ERROR_MESSAGE);
                    }
                }
            });
        }
    }

    private void showHighScoresDialog() {
        HighScore hs = new HighScore("EuroPhoenix");
        List<HighScore.ScoreEntry> scores = hs.getScores();
        StringBuilder sb = new StringBuilder();
        sb.append("EuroPhoenix Bestenliste (Top 10):\n\n");
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
                        new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm").format(entry.date)));
            }
        }
        JOptionPane.showMessageDialog(this, sb.toString(), "Bestenliste", JOptionPane.INFORMATION_MESSAGE);
    }

    // ── Retro Sound Synthesizer ─────────────────────────────────────────────

    private void playSynthesizedSound(int[] freqs, int[] durationsMs, boolean square) {
        if (soundRunning)
            return;
        soundRunning = true;
        new Thread(() -> {
            try {
                int sampleRate = 8000;
                int totalDurationMs = 0;
                for (int d : durationsMs)
                    totalDurationMs += d;

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
        playSynthesizedSound(new int[] { 900, 1200 }, new int[] { 30, 40 }, true);
    }

    private void playShieldHum() {
        playSynthesizedSound(new int[] { 120, 100, 90 }, new int[] { 400, 400, 400 }, false);
    }

    private void playDiveSound() {
        playSynthesizedSound(new int[] { 600, 400, 200 }, new int[] { 50, 50, 50 }, false);
    }

    private void playExplosionSound(int freq) {
        playSynthesizedSound(new int[] { freq, freq - 40, freq - 80 }, new int[] { 80, 80, 100 }, false);
    }

    private void playLevelStartSound() {
        playSynthesizedSound(new int[] { 523, 659, 784 }, new int[] { 80, 80, 150 }, false);
    }

    private void playLevelUpSound() {
        playSynthesizedSound(new int[] { 523, 659, 784, 1047 }, new int[] { 90, 90, 90, 200 }, false);
    }

    @Override
    public void dispose() {
        loopTimer.stop();
        state = GameState.GAME_OVER;
        super.dispose();
    }

    // ── Panel Graphics Painting ─────────────────────────────────────────────

    private class PhoenixPanel extends JPanel {

        PhoenixPanel() {
            setBackground(Color.BLACK);
            setPreferredSize(new Dimension(GAME_WIDTH, GAME_HEIGHT));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Bounds box
            g2.setColor(Color.DARK_GRAY);
            g2.drawRect(10, 50, GAME_WIDTH - 20, GAME_HEIGHT - 70);

            // HUD
            g2.setFont(new Font("Monospaced", Font.BOLD, 14));
            g2.setColor(Color.WHITE);
            g2.drawString(String.format("SCORE: %05d", score), 25, 34);
            g2.drawString(String.format("LIVES: %d", lives), 180, 34);
            g2.drawString(String.format("WAVE: %d", wave), 320, 34);

            // Draw player ship
            if (state == GameState.RUNNING || state == GameState.WAVE_COMPLETE) {
                drawSprite(g2, PLAYER_SHIP, (int) playerX, PLAYER_Y, 2, Color.GREEN);

                // Shield visual effect
                if (shieldActive) {
                    g2.setColor(new Color(0, 255, 255, 120));
                    g2.setStroke(new BasicStroke(2.0f));
                    g2.drawOval((int) playerX - 10, PLAYER_Y - 12, 38, 38);
                }

                // Shield Cooldown HUD
                long now = System.currentTimeMillis();
                if (now < shieldNextAvailableTime) {
                    g2.setColor(Color.RED);
                    g2.fillRect(100, GAME_HEIGHT - 12, 240, 4);
                } else {
                    g2.setColor(Color.CYAN);
                    g2.fillRect(100, GAME_HEIGHT - 12, 240, 4);
                }
                g2.setFont(new Font("SansSerif", Font.PLAIN, 9));
                g2.setColor(Color.WHITE);
                g2.drawString("SHIELD READY", 30, GAME_HEIGHT - 8);
            }

            // Draw Lasers
            for (Laser l : lasers) {
                if (l.fromPlayer) {
                    g2.setColor(Color.YELLOW);
                    g2.fillRect((int) l.x - 1, (int) l.y, 2, 8);
                } else {
                    g2.setColor(Color.RED);
                    g2.fillOval((int) l.x - 2, (int) l.y, 5, 5);
                }
            }

            // Draw Birds
            if (!bossActive) {
                for (Bird b : birds) {
                    if (b.active) {
                        String[] sprite = (b.wingFrame < 15) ? BIRD_WING_UP : BIRD_WING_DOWN;
                        Color c = (b.type == 0) ? Color.RED : Color.YELLOW;
                        drawSprite(g2, sprite, (int) b.x, (int) b.y, 2, c);
                    }
                }
            } else {
                // Draw Boss Mothership
                // Width 80, height 40
                g2.setColor(new Color(128, 0, 128)); // Purple shell
                g2.fillRect((int) bossX, (int) bossY, 80, 20);
                g2.fillOval((int) bossX + 10, (int) bossY - 10, 60, 30);

                // Shield belt
                for (int i = 0; i < 16; i++) {
                    int hp = bossShieldBelt[i];
                    if (hp > 0) {
                        if (hp == 3)
                            g2.setColor(Color.GREEN);
                        else if (hp == 2)
                            g2.setColor(Color.YELLOW);
                        else
                            g2.setColor(Color.RED);
                        g2.fillRect((int) bossX + i * 5, (int) bossY + 24, 4, 6);
                    }
                }

                // Alien Pilot Core
                if (bossPilotHealth > 0) {
                    drawSprite(g2, ALIEN_BOSS, (int) bossX + 36, (int) bossY + 4, 1, Color.CYAN);
                }
            }

            // Draw Overlays
            if (state == GameState.LAUNCH) {
                g2.setColor(new Color(0, 0, 0, 180));
                g2.fillRect(10, 50, GAME_WIDTH - 20, GAME_HEIGHT - 70);
                g2.setFont(new Font("SansSerif", Font.BOLD, 18));
                g2.setColor(Color.GREEN);
                drawCenteredString(g2, "EUROPHOENIX 1980", GAME_HEIGHT / 2 - 30);
                g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
                g2.setColor(Color.WHITE);
                drawCenteredString(g2, "Drücke LEERTASTE zum Starten", GAME_HEIGHT / 2 + 10);
                drawCenteredString(g2, "A / D oder Links / Rechts = Bewegen", GAME_HEIGHT / 2 + 35);
                drawCenteredString(g2, "LEERTASTE = Schießen", GAME_HEIGHT / 2 + 55);
                drawCenteredString(g2, "S oder Pfeil Unten = SCHILD (Schutz)", GAME_HEIGHT / 2 + 75);
            } else if (state == GameState.PAUSED) {
                g2.setColor(new Color(0, 0, 0, 160));
                g2.fillRect(10, 50, GAME_WIDTH - 20, GAME_HEIGHT - 70);
                g2.setFont(new Font("SansSerif", Font.BOLD, 22));
                g2.setColor(Color.YELLOW);
                drawCenteredString(g2, "PAUSE", GAME_HEIGHT / 2);
            } else if (state == GameState.GAME_OVER) {
                g2.setColor(new Color(0, 0, 0, 200));
                g2.fillRect(10, 50, GAME_WIDTH - 20, GAME_HEIGHT - 70);
                g2.setFont(new Font("SansSerif", Font.BOLD, 24));
                g2.setColor(Color.RED);
                drawCenteredString(g2, "GAME OVER", GAME_HEIGHT / 2 - 20);
                g2.setFont(new Font("SansSerif", Font.PLAIN, 14));
                g2.setColor(Color.WHITE);
                drawCenteredString(g2, "Drücke F2 für Neues Spiel", GAME_HEIGHT / 2 + 20);
            } else if (state == GameState.WAVE_COMPLETE) {
                g2.setColor(new Color(0, 0, 0, 150));
                g2.fillRect(10, 50, GAME_WIDTH - 20, GAME_HEIGHT - 70);
                g2.setFont(new Font("SansSerif", Font.BOLD, 24));
                g2.setColor(Color.GREEN);
                drawCenteredString(g2, "WELLE BEENDET!", GAME_HEIGHT / 2 - 20);
            }

            g2.dispose();
        }

        private void drawSprite(Graphics2D g2, String[] sprite, int x, int y, int pixelSize, Color color) {
            g2.setColor(color);
            for (int r = 0; r < sprite.length; r++) {
                String row = sprite[r];
                for (int c = 0; c < row.length(); c++) {
                    if (row.charAt(c) == '1') {
                        g2.fillRect(x + c * pixelSize, y + r * pixelSize, pixelSize, pixelSize);
                    }
                }
            }
        }

        private void drawCenteredString(Graphics2D g2, String text, int y) {
            FontMetrics fm = g2.getFontMetrics();
            int x = (getWidth() - fm.stringWidth(text)) / 2;
            g2.drawString(text, x, y);
        }
    }
}

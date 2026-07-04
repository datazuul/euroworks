package com.datazuul.euroworks.games;

import com.datazuul.euroworks.apps.EuroAppFrame;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Classic Space Invaders (1978) replica for EuroWorks.
 * Recreates the iconic arcade cabinet experience featuring:
 * - Alternating 2-frame walking animations for Squids, Crabs, and Octopuses.
 * - Dynamic marching acceleration (speeds up as invaders are destroyed).
 * - Multi-layer crumbling shield bunkers built of destructible sub-blocks.
 * - Mystery flying saucer (UFO) spawning at the top with retro flyby tone.
 * - Rhythmic 8-bit marching bass beats and laser fire synth blips.
 * - Focus-independent keyboard control bindings (Left/A, Right/D, Space to fire).
 * - Full HighScore integration storing entries by username and elapsed time.
 */
public class EuroInvaders extends EuroAppFrame {

    private enum GameState {
        LAUNCH, RUNNING, PAUSED, GAME_OVER, GAME_WON
    }

    private static final int GAME_WIDTH = 440;
    private static final int GAME_HEIGHT = 500;

    // Sprite definitions (1 represents filled pixels, 0 represents empty)
    private static final String[] SQUID_SPRITE_1 = {
        "00011000", "00111100", "01111110", "11011011", "11111111", "00100100", "01011010", "10100101"
    };
    private static final String[] SQUID_SPRITE_2 = {
        "00011000", "00111100", "01111110", "11011011", "11111111", "01011010", "10011001", "01000010"
    };

    private static final String[] CRAB_SPRITE_1 = {
        "00100100", "01011010", "01111110", "11011011", "11111111", "01111110", "01000010", "00100100"
    };
    private static final String[] CRAB_SPRITE_2 = {
        "00100100", "11011011", "11111111", "01111110", "01000010", "10000001", "01000010", "10100101"
    };

    private static final String[] BEETLE_SPRITE_1 = {
        "00011100", "01111110", "11111111", "11011011", "11111111", "00111100", "01000010", "10000001"
    };
    private static final String[] BEETLE_SPRITE_2 = {
        "00011100", "01111110", "11111111", "11011011", "11111111", "01111110", "10100101", "01000010"
    };

    private static final String[] PLAYER_SPRITE = {
        "00000100000",
        "00001110000",
        "00001110000",
        "01111111110",
        "11111111111",
        "11111111111",
        "11111111111"
    };

    private static final String[] UFO_SPRITE = {
        "000011110000",
        "001111111100",
        "011011110110",
        "111111111111",
        "001100001100",
        "000110011000"
    };

    // State Variables
    private GameState state = GameState.LAUNCH;
    private int score = 0;
    private int lives = 3;
    private long gameStartTime = 0;
    private boolean keyLeftPressed = false;
    private boolean keyRightPressed = false;

    // Player
    private double playerX = (GAME_WIDTH - 22) / 2.0;
    private static final int PLAYER_Y = 440;
    private static final int PLAYER_SPEED = 4;

    // lasers
    private Laser playerLaser = null;
    private final List<Laser> invaderLasers = new ArrayList<>();

    // Invaders
    private static final int ROWS = 5;
    private static final int COLS = 11;
    private final Invader[][] grid = new Invader[ROWS][COLS];
    private double invaderXOffset = 20;
    private double invaderYOffset = 80;
    private int invaderDir = 1; // 1 = Right, -1 = Left
    private int animationFrame = 1;
    private long lastMarchTime = 0;
    private int marchIntervalMs = 800; // speeds up as count decreases

    // UFO
    private double ufoX = -50;
    private double ufoY = 60;
    private int ufoDir = 1;
    private boolean ufoActive = false;
    private long lastUfoSpawnTime = 0;
    private final Random random = new Random();

    // Bunkers / Shields
    private static final int BUNKER_COUNT = 3;
    private final Bunker[] bunkers = new Bunker[BUNKER_COUNT];

    // UI/Loop
    private final InvadersPanel playPanel;
    private final Timer loopTimer;

    // Classes
    private static class Laser {
        double x, y;
        boolean fromPlayer;

        Laser(double x, double y, boolean fromPlayer) {
            this.x = x;
            this.y = y;
            this.fromPlayer = fromPlayer;
        }
    }

    private static class Invader {
        int type; // 0 = Squid, 1 = Crab, 2 = Beetle
        int points;
        boolean active = true;

        Invader(int row) {
            if (row == 0) {
                type = 0;
                points = 30;
            } else if (row == 1 || row == 2) {
                type = 1;
                points = 20;
            } else {
                type = 2;
                points = 10;
            }
        }
    }

    private static class Bunker {
        // A bunker consists of 8 columns and 6 rows of sub-blocks (each 4x4 px)
        // Health ranges from 3 (perfect) down to 0 (destroyed)
        int[][] blocks = new int[6][8];
        int startX;

        Bunker(int startX) {
            this.startX = startX;
            for (int r = 0; r < 6; r++) {
                for (int c = 0; c < 8; c++) {
                    // Form classic arch shape: bottom middle is hollow
                    if (r >= 4 && (c == 2 || c == 3 || c == 4 || c == 5)) {
                        blocks[r][c] = 0; // Hollow arch tunnel
                    } else {
                        blocks[r][c] = 3; // Solid block
                    }
                }
            }
        }
    }

    public EuroInvaders() {
        super("EuroInvaders (Space Invaders)");
        setSize(GAME_WIDTH + 16, GAME_HEIGHT + 74);

        setJMenuBar(buildMenuBar());

        playPanel = new InvadersPanel();
        setContentPane(playPanel);

        // Standard Game loop (~60fps)
        loopTimer = new Timer(16, e -> updateGame());

        // Keyboard bindings mapping
        InputMap im = playPanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = playPanel.getActionMap();

        // Left Press/Release
        im.put(KeyStroke.getKeyStroke("LEFT"), "pressLeft");
        im.put(KeyStroke.getKeyStroke("A"), "pressLeft");
        am.put("pressLeft", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                keyLeftPressed = true;
            }
        });
        im.put(KeyStroke.getKeyStroke("released LEFT"), "releaseLeft");
        im.put(KeyStroke.getKeyStroke("released A"), "releaseLeft");
        am.put("releaseLeft", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                keyLeftPressed = false;
            }
        });

        // Right Press/Release
        im.put(KeyStroke.getKeyStroke("RIGHT"), "pressRight");
        im.put(KeyStroke.getKeyStroke("D"), "pressRight");
        am.put("pressRight", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                keyRightPressed = true;
            }
        });
        im.put(KeyStroke.getKeyStroke("released RIGHT"), "releaseRight");
        im.put(KeyStroke.getKeyStroke("released D"), "releaseRight");
        am.put("releaseRight", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                keyRightPressed = false;
            }
        });

        // Fire Laser / Serve
        im.put(KeyStroke.getKeyStroke("SPACE"), "fire");
        am.put("fire", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (state == GameState.LAUNCH) {
                    startGame();
                } else if (state == GameState.RUNNING) {
                    firePlayerLaser();
                }
            }
        });

        // System
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

    private void resetGame() {
        score = 0;
        lives = 3;
        state = GameState.LAUNCH;
        gameStartTime = 0;
        playerLaser = null;
        invaderLasers.clear();
        ufoActive = false;
        lastUfoSpawnTime = System.currentTimeMillis();

        // 1. Initialize Invaders
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                grid[r][c] = new Invader(r);
            }
        }
        invaderXOffset = 20;
        invaderYOffset = 80;
        invaderDir = 1;
        marchIntervalMs = 800;
        lastMarchTime = System.currentTimeMillis();

        // 2. Initialize Bunkers
        int step = GAME_WIDTH / (BUNKER_COUNT + 1);
        for (int i = 0; i < BUNKER_COUNT; i++) {
            bunkers[i] = new Bunker((i + 1) * step - 16);
        }

        playerX = (GAME_WIDTH - 22) / 2.0;

        loopTimer.start();
        playPanel.repaint();
    }

    private void startGame() {
        state = GameState.RUNNING;
        gameStartTime = System.currentTimeMillis();
        playSound(440, 100);
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

    private void firePlayerLaser() {
        if (playerLaser == null) {
            playerLaser = new Laser(playerX + 10, PLAYER_Y - 4, true);
            // Retro high sweep square wave sound
            playLaserSound();
        }
    }

    // ── Retro Sound Synthesizer ─────────────────────────────────────────────

    private void playSound(int freq, int durationMs) {
        new Thread(() -> {
            try {
                int sampleRate = 8000;
                int numSamples = durationMs * sampleRate / 1000;
                int totalSamples = Math.max(numSamples, 3000);
                byte[] buf = new byte[totalSamples];
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

    private void playLaserSound() {
        new Thread(() -> {
            try {
                int sampleRate = 8000;
                int durationMs = 150;
                int numSamples = durationMs * sampleRate / 1000;
                int totalSamples = Math.max(numSamples, 3000);
                byte[] buf = new byte[totalSamples];
                double phase = 0.0;
                for (int i = 0; i < numSamples; i++) {
                    double pct = (double) i / numSamples;
                    // Sweep frequency UP from 350 to 900 Hz
                    double freq = 350 + 550 * pct;
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

    private void playSlideSound(int startFreq, int endFreq, int durationMs) {
        new Thread(() -> {
            try {
                int sampleRate = 8000;
                int numSamples = durationMs * sampleRate / 1000;
                int totalSamples = Math.max(numSamples, 3000);
                byte[] buf = new byte[totalSamples];
                double phase = 0.0;
                for (int i = 0; i < numSamples; i++) {
                    double pct = (double) i / numSamples;
                    double currentFreq = startFreq + (endFreq - startFreq) * pct;
                    phase += 2.0 * Math.PI * currentFreq / sampleRate;
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

    // ── Main Update Logic ───────────────────────────────────────────────────

    private void updateGame() {
        if (state != GameState.RUNNING) {
            playPanel.repaint();
            return;
        }

        // 1. Move Player
        if (keyLeftPressed) {
            playerX = Math.max(10, playerX - PLAYER_SPEED);
        } else if (keyRightPressed) {
            playerX = Math.min(GAME_WIDTH - 32, playerX + PLAYER_SPEED);
        }

        // 2. Move Player Laser
        if (playerLaser != null) {
            playerLaser.y -= 7.0;
            if (playerLaser.y < 20) {
                playerLaser = null;
            }
        }

        // 3. Move Invader Lasers
        Iterator<Laser> itLaser = invaderLasers.iterator();
        while (itLaser.hasNext()) {
            Laser l = itLaser.next();
            l.y += 3.5;

            // Check collision with player
            if (l.y >= PLAYER_Y && l.y <= PLAYER_Y + 14 &&
                l.x >= playerX && l.x <= playerX + 22) {
                itLaser.remove();
                handlePlayerHit();
                continue;
            }

            // Check collision with shields
            if (checkBunkerHit(l.x, l.y)) {
                itLaser.remove();
                continue;
            }

            // Out of bounds
            if (l.y > GAME_HEIGHT - 20) {
                itLaser.remove();
            }
        }

        // 4. Move Player Laser collisions with Bunkers, Invaders, UFO
        if (playerLaser != null) {
            // Check shield hit
            if (checkBunkerHit(playerLaser.x, playerLaser.y)) {
                playerLaser = null;
            } else if (checkInvaderHit(playerLaser.x, playerLaser.y)) {
                playerLaser = null;
            } else if (ufoActive && playerLaser.y >= ufoY && playerLaser.y <= ufoY + 12 &&
                       playerLaser.x >= ufoX && playerLaser.x <= ufoX + 24) {
                // Destroy UFO
                playerLaser = null;
                ufoActive = false;
                int pts = (random.nextInt(4) + 1) * 50; // 50, 100, 150, 200 pts
                score += pts;
                playSound(900, 200);
            }
        }

        // 5. March Invaders (grid movement)
        long now = System.currentTimeMillis();
        int activeCount = getActiveInvaderCount();
        if (activeCount == 0) {
            victory();
            return;
        }

        // Interval speeds up as invaders decrease
        marchIntervalMs = Math.max(80, activeCount * 14);

        if (now - lastMarchTime >= marchIntervalMs) {
            lastMarchTime = now;
            marchInvaders();
        }

        // 6. Invader firing back
        if (random.nextInt(100) < 3 && invaderLasers.size() < 3) {
            fireInvaderLaser();
        }

        // 7. UFO Spawning
        updateUfo();

        playPanel.repaint();
    }

    private void marchInvaders() {
        // Check if any invader touches boundary to drop row
        boolean dropRow = false;
        double leftmost = GAME_WIDTH;
        double rightmost = 0;

        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (grid[r][c].active) {
                    double ix = invaderXOffset + c * 26;
                    if (ix < leftmost) leftmost = ix;
                    if (ix + 16 > rightmost) rightmost = ix + 16;
                }
            }
        }

        if (invaderDir == 1 && rightmost >= GAME_WIDTH - 20) {
            invaderDir = -1;
            dropRow = true;
        } else if (invaderDir == -1 && leftmost <= 20) {
            invaderDir = 1;
            dropRow = true;
        }

        if (dropRow) {
            invaderYOffset += 14;
            // Check Game Over by landing
            for (int r = 0; r < ROWS; r++) {
                for (int c = 0; c < COLS; c++) {
                    if (grid[r][c].active && (invaderYOffset + r * 20 + 16 >= PLAYER_Y)) {
                        gameOver();
                        return;
                    }
                }
            }
        } else {
            invaderXOffset += invaderDir * 8;
        }

        // Play low marching synth beat on step
        animationFrame = (animationFrame == 1) ? 2 : 1;
        playSound(100 - (invaderDir * 15), 60);
    }

    private int getActiveInvaderCount() {
        int count = 0;
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (grid[r][c].active) count++;
            }
        }
        return count;
    }

    private void fireInvaderLaser() {
        // Pick a random column that has active invaders
        List<Integer> activeCols = new ArrayList<>();
        for (int c = 0; c < COLS; c++) {
            for (int r = ROWS - 1; r >= 0; r--) {
                if (grid[r][c].active) {
                    activeCols.add(c);
                    break;
                }
            }
        }
        if (!activeCols.isEmpty()) {
            int randomCol = activeCols.get(random.nextInt(activeCols.size()));
            // Find lowest active invader in that column
            for (int r = ROWS - 1; r >= 0; r--) {
                if (grid[r][randomCol].active) {
                    double ix = invaderXOffset + randomCol * 26 + 8;
                    double iy = invaderYOffset + r * 20 + 16;
                    invaderLasers.add(new Laser(ix, iy, false));
                    break;
                }
            }
        }
    }

    private boolean checkBunkerHit(double lx, double ly) {
        for (Bunker b : bunkers) {
            if (lx >= b.startX && lx < b.startX + 32 &&
                ly >= 380 && ly < 380 + 24) {
                // Determine block indices
                int bc = (int) ((lx - b.startX) / 4);
                int br = (int) ((ly - 380) / 4);
                if (bc >= 0 && bc < 8 && br >= 0 && br < 6) {
                    if (b.blocks[br][bc] > 0) {
                        b.blocks[br][bc]--; // Damage block
                        playSound(180, 40);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean checkInvaderHit(double lx, double ly) {
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (grid[r][c].active) {
                    double ix = invaderXOffset + c * 26;
                    double iy = invaderYOffset + r * 20;
                    if (lx >= ix && lx <= ix + 16 &&
                        ly >= iy && ly <= iy + 16) {
                        grid[r][c].active = false;
                        score += grid[r][c].points;
                        playSound(350, 60);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void updateUfo() {
        long now = System.currentTimeMillis();
        if (!ufoActive) {
            if (now - lastUfoSpawnTime > 20000 + random.nextInt(15000)) {
                ufoActive = true;
                ufoY = 60;
                ufoDir = random.nextBoolean() ? 1 : -1;
                ufoX = (ufoDir == 1) ? -30 : GAME_WIDTH + 10;
                lastUfoSpawnTime = now;
            }
        } else {
            ufoX += ufoDir * 2.2;
            if (random.nextInt(15) == 0) {
                // Alternating high ticking flyby sound
                playSound(random.nextBoolean() ? 750 : 600, 50);
            }
            if ((ufoDir == 1 && ufoX > GAME_WIDTH + 10) || (ufoDir == -1 && ufoX < -40)) {
                ufoActive = false;
            }
        }
    }

    private void handlePlayerHit() {
        lives--;
        playSlideSound(300, 60, 500); // defeat sweep
        if (lives <= 0) {
            gameOver();
        } else {
            playerX = (GAME_WIDTH - 22) / 2.0;
            playerLaser = null;
            invaderLasers.clear();
        }
    }

    private void gameOver() {
        state = GameState.GAME_OVER;
        loopTimer.stop();
        checkAndPromptHighScore();
    }

    private void victory() {
        state = GameState.GAME_WON;
        loopTimer.stop();
        playSound(880, 500);
        checkAndPromptHighScore();
    }

    private void checkAndPromptHighScore() {
        if (score <= 0) return;
        
        int durationSecs = 0;
        if (gameStartTime > 0) {
            durationSecs = (int) ((System.currentTimeMillis() - gameStartTime) / 1000);
        }
        final int finalDuration = durationSecs;

        HighScore hs = new HighScore("EuroInvaders");
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
        HighScore hs = new HighScore("EuroInvaders");
        List<HighScore.ScoreEntry> scores = hs.getScores();
        StringBuilder sb = new StringBuilder();
        sb.append("EuroInvaders Bestenliste (Top 10):\n\n");
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

    // ── Panel Graphics Painting ─────────────────────────────────────────────

    private class InvadersPanel extends JPanel {

        InvadersPanel() {
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
            g2.drawRect(10, 10, GAME_WIDTH - 20, GAME_HEIGHT - 20);

            // 1. Draw HUD
            g2.setFont(new Font("Monospaced", Font.BOLD, 14));
            g2.setColor(Color.WHITE);
            g2.drawString(String.format("SCORE: %05d", score), 25, 34);
            g2.drawString(String.format("LIVES: %d", lives), GAME_WIDTH - 110, 34);

            // 2. Draw Bunkers
            for (Bunker b : bunkers) {
                for (int r = 0; r < 6; r++) {
                    for (int c = 0; c < 8; c++) {
                        int hp = b.blocks[r][c];
                        if (hp > 0) {
                            if (hp == 3) g2.setColor(Color.GREEN);
                            else if (hp == 2) g2.setColor(new Color(0, 180, 0));
                            else g2.setColor(new Color(0, 110, 0));

                            g2.fillRect(b.startX + c * 4, 380 + r * 4, 4, 4);
                        }
                    }
                }
            }

            // 3. Draw Invaders
            for (int r = 0; r < ROWS; r++) {
                for (int c = 0; c < COLS; c++) {
                    Invader inv = grid[r][c];
                    if (inv.active) {
                        int ix = (int) (invaderXOffset + c * 26);
                        int iy = (int) (invaderYOffset + r * 20);

                        String[] sprite;
                        if (inv.type == 0) {
                            sprite = (animationFrame == 1) ? SQUID_SPRITE_1 : SQUID_SPRITE_2;
                        } else if (inv.type == 1) {
                            sprite = (animationFrame == 1) ? CRAB_SPRITE_1 : CRAB_SPRITE_2;
                        } else {
                            sprite = (animationFrame == 1) ? BEETLE_SPRITE_1 : BEETLE_SPRITE_2;
                        }
                        drawSprite(g2, sprite, ix, iy, 2, Color.WHITE);
                    }
                }
            }

            // 4. Draw UFO
            if (ufoActive) {
                drawSprite(g2, UFO_SPRITE, (int) ufoX, (int) ufoY, 2, Color.RED);
            }

            // 5. Draw Player
            drawSprite(g2, PLAYER_SPRITE, (int) playerX, PLAYER_Y, 2, Color.GREEN);

            // 6. Draw Lasers
            if (playerLaser != null) {
                g2.setColor(Color.GREEN);
                g2.fillRect((int) playerLaser.x, (int) playerLaser.y, 2, 8);
            }
            g2.setColor(Color.WHITE);
            for (Laser l : invaderLasers) {
                g2.fillRect((int) l.x, (int) l.y, 2, 8);
            }

            // 7. Green floor line
            g2.setColor(Color.GREEN);
            g2.drawLine(10, GAME_HEIGHT - 35, GAME_WIDTH - 10, GAME_HEIGHT - 35);

            // 8. Draw Overlays
            if (state == GameState.LAUNCH) {
                g2.setColor(new Color(0, 0, 0, 160));
                g2.fillRect(10, 10, GAME_WIDTH - 20, GAME_HEIGHT - 20);
                g2.setFont(new Font("SansSerif", Font.BOLD, 18));
                g2.setColor(Color.GREEN);
                drawCenteredString(g2, "SPACE INVADERS 1978", GAME_HEIGHT / 2 - 30);
                g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
                g2.setColor(Color.WHITE);
                drawCenteredString(g2, "Drücke LEERTASTE zum Starten", GAME_HEIGHT / 2 + 10);
                drawCenteredString(g2, "A / D oder Links / Rechts = Bewegen", GAME_HEIGHT / 2 + 35);
                drawCenteredString(g2, "LEERTASTE = Schießen", GAME_HEIGHT / 2 + 55);
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
            } else if (state == GameState.GAME_WON) {
                g2.setColor(new Color(0, 0, 0, 200));
                g2.fillRect(10, 10, GAME_WIDTH - 20, GAME_HEIGHT - 20);
                g2.setFont(new Font("SansSerif", Font.BOLD, 24));
                g2.setColor(Color.GREEN);
                drawCenteredString(g2, "SIEG!", GAME_HEIGHT / 2 - 20);
                g2.setFont(new Font("SansSerif", Font.PLAIN, 14));
                g2.setColor(Color.WHITE);
                drawCenteredString(g2, "Drücke F2 zum nochmal Spielen", GAME_HEIGHT / 2 + 20);
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

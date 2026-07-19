package com.datazuul.euroworks.games;

import com.datazuul.euroworks.apps.EuroAppFrame;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;

/**
 * EuroFrogger - Classic Frogger (Konami 1981) replica for EuroWorks.
 * Recreates the retro arcade cabinet experience featuring:
 * - High-speed road lanes with different car/truck sprites and speeds.
 * - River lanes with floating logs and diving turtles (turning red then invisible/underwater).
 * - 5 target home docks at the top.
 * - Single-step grid-aligned hopping controls (arrows and WASD).
 * - Timer countdown bar which kills the frog upon expiration.
 * - HighScore integration saving players' scores and times.
 * - Retro sound synthesis for hops, deaths, success, and level completion.
 */
public class EuroFrogger extends EuroAppFrame {

    private enum GameState {
        LAUNCH, RUNNING, PAUSED, GAME_OVER, LEVEL_COMPLETE
    }

    private static final int GAME_WIDTH = 440;
    private static final int GAME_HEIGHT = 500;

    // Sprite definitions using pixel matrices
    private static final String[] FROG_UP = {
        "00111100",
        "01100110",
        "11111111",
        "10111101",
        "11111111",
        "01111110",
        "10100101",
        "11000011"
    };

    private static final String[] CAR_RED = {
        "00111100",
        "01111110",
        "11111111",
        "10011001",
        "11111111",
        "11111111",
        "01111110",
        "00111100"
    };

    private static final String[] CAR_YELLOW = {
        "00011000",
        "01111110",
        "11111111",
        "10111101",
        "11111111",
        "01111110",
        "10000001",
        "01111110"
    };

    private static final String[] TRUCK = {
        "1111111111111111",
        "1100000000000011",
        "1101111111110111",
        "1101111111110111",
        "1100000000000011",
        "1111111111111111",
        "0110000110000110",
        "0011001100110011"
    };

    private static final String[] TURTLE_NORMAL = {
        "00111100",
        "01111110",
        "11111111",
        "11111111",
        "11111111",
        "01111110",
        "00111100",
        "01000010"
    };

    private static final String[] TURTLE_DIVE = {
        "00011000",
        "00111100",
        "01100110",
        "11000011",
        "11000011",
        "01100110",
        "00111100",
        "00011000"
    };

    private static final String[] LILYPAD = {
        "00111100",
        "01111110",
        "11111101",
        "11111001",
        "11111101",
        "11111111",
        "01111110",
        "00111100"
    };

    // Game variables
    private GameState state = GameState.LAUNCH;
    private int score = 0;
    private int lives = 3;
    private int level = 1;
    private long gameStartTime = 0;
    private double frogX, frogY;

    // Timer variables (ticks down from 60 seconds)
    private double timeLeft = 60.0;
    private static final double MAX_TIME = 60.0;

    // Lanes definition
    private final List<Obstacle> obstacles = new ArrayList<>();
    private final boolean[] homeOccupied = new boolean[5];
    private final int[] homeX = { 35, 125, 215, 305, 395 }; // X coords of docks
    private static final int HOME_Y = 80;

    // Loop & rendering
    private final FroggerPanel playPanel;
    private final Timer loopTimer;
    private final Random random = new Random();

    // Sound lock/flag
    private boolean soundRunning = false;

    // Class helper for road/river entities
    private static class Obstacle {
        double x, y;
        double speed;
        int width;
        int height;
        boolean isLog;
        boolean isTurtle;
        boolean isDivingTurtle;
        Color color;
        String[] sprite;

        Obstacle(double x, double y, double speed, int width, int height, boolean isLog, boolean isTurtle, Color color, String[] sprite) {
            this.x = x;
            this.y = y;
            this.speed = speed;
            this.width = width;
            this.height = height;
            this.isLog = isLog;
            this.isTurtle = isTurtle;
            this.color = color;
            this.sprite = sprite;
        }
    }

    public EuroFrogger() {
        super("EuroFrogger (Frogger)");
        setSize(GAME_WIDTH + 16, GAME_HEIGHT + 74);
        setJMenuBar(buildMenuBar());

        playPanel = new FroggerPanel();
        setContentPane(playPanel);

        // Game loop (~60 FPS)
        loopTimer = new Timer(16, e -> updateGame());

        setupControls();
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

    private void setupControls() {
        InputMap im = playPanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = playPanel.getActionMap();

        String[] keys = { "UP", "DOWN", "LEFT", "RIGHT", "W", "S", "A", "D" };
        for (String key : keys) {
            im.put(KeyStroke.getKeyStroke(key), "move_" + key);
        }

        am.put("move_UP", new MoveAction(0, -32));
        am.put("move_W", new MoveAction(0, -32));
        am.put("move_DOWN", new MoveAction(0, 32));
        am.put("move_S", new MoveAction(0, 32));
        am.put("move_LEFT", new MoveAction(-24, 0));
        am.put("move_A", new MoveAction(-24, 0));
        am.put("move_RIGHT", new MoveAction(24, 0));
        am.put("move_D", new MoveAction(24, 0));

        im.put(KeyStroke.getKeyStroke("SPACE"), "start_game");
        am.put("start_game", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (state == GameState.LAUNCH) {
                    startGame();
                }
            }
        });

        im.put(KeyStroke.getKeyStroke("P"), "pause_game");
        am.put("pause_game", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                togglePause();
            }
        });

        im.put(KeyStroke.getKeyStroke("F2"), "restart_game");
        am.put("restart_game", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                resetGame();
            }
        });
    }

    private class MoveAction extends AbstractAction {
        private final int dx, dy;

        MoveAction(int dx, int dy) {
            this.dx = dx;
            this.dy = dy;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (state != GameState.RUNNING) return;

            frogX += dx;
            frogY += dy;

            // Constrain X bounds
            if (frogX < 10) frogX = 10;
            if (frogX > GAME_WIDTH - 26) frogX = GAME_WIDTH - 26;

            // Constrain Y bounds
            if (frogY > 464) frogY = 464;
            if (frogY < 80) frogY = 80;

            playHopSound();
            playPanel.repaint();
        }
    }

    private void resetGame() {
        score = 0;
        lives = 3;
        level = 1;
        state = GameState.LAUNCH;
        gameStartTime = 0;
        resetFrog();

        for (int i = 0; i < 5; i++) {
            homeOccupied[i] = false;
        }

        generateObstacles();

        loopTimer.start();
        playPanel.repaint();
    }

    private void resetFrog() {
        frogX = 208.0;
        frogY = 464.0;
        timeLeft = MAX_TIME;
    }

    private void startGame() {
        state = GameState.RUNNING;
        gameStartTime = System.currentTimeMillis();
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

    private void generateObstacles() {
        obstacles.clear();
        double speedMult = 1.0 + (level - 1) * 0.15;

        // Road Row 1 (Y = 432) - Speeders/Race cars (Fast right)
        obstacles.add(new Obstacle(50, 432, 2.5 * speedMult, 24, 24, false, false, Color.YELLOW, CAR_YELLOW));
        obstacles.add(new Obstacle(220, 432, 2.5 * speedMult, 24, 24, false, false, Color.YELLOW, CAR_YELLOW));

        // Road Row 2 (Y = 400) - Trucks (Slow left)
        obstacles.add(new Obstacle(10, 400, -1.2 * speedMult, 48, 24, false, false, Color.LIGHT_GRAY, TRUCK));
        obstacles.add(new Obstacle(200, 400, -1.2 * speedMult, 48, 24, false, false, Color.LIGHT_GRAY, TRUCK));
        obstacles.add(new Obstacle(380, 400, -1.2 * speedMult, 48, 24, false, false, Color.LIGHT_GRAY, TRUCK));

        // Road Row 3 (Y = 368) - Normal Cars (Medium right)
        obstacles.add(new Obstacle(40, 368, 1.8 * speedMult, 24, 24, false, false, Color.RED, CAR_RED));
        obstacles.add(new Obstacle(200, 368, 1.8 * speedMult, 24, 24, false, false, Color.RED, CAR_RED));

        // Road Row 4 (Y = 336) - Slow Cars (Slow right)
        obstacles.add(new Obstacle(80, 336, 1.0 * speedMult, 24, 24, false, false, Color.CYAN, CAR_RED));
        obstacles.add(new Obstacle(260, 336, 1.0 * speedMult, 24, 24, false, false, Color.CYAN, CAR_RED));

        // Road Row 5 (Y = 304) - Fast Cars (Fast left)
        obstacles.add(new Obstacle(100, 304, -2.2 * speedMult, 24, 24, false, false, Color.ORANGE, CAR_YELLOW));
        obstacles.add(new Obstacle(300, 304, -2.2 * speedMult, 24, 24, false, false, Color.ORANGE, CAR_YELLOW));

        // River Row 1 (Y = 240) - Medium Logs (Right)
        obstacles.add(new Obstacle(20, 240, 1.5 * speedMult, 96, 24, true, false, new Color(139, 69, 19), null));
        obstacles.add(new Obstacle(220, 240, 1.5 * speedMult, 96, 24, true, false, new Color(139, 69, 19), null));

        // River Row 2 (Y = 208) - Turtles (Left)
        Obstacle t1 = new Obstacle(80, 208, -1.3 * speedMult, 24, 24, false, true, Color.GREEN, TURTLE_NORMAL);
        t1.isDivingTurtle = true;
        Obstacle t2 = new Obstacle(120, 208, -1.3 * speedMult, 24, 24, false, true, Color.GREEN, TURTLE_NORMAL);
        t2.isDivingTurtle = true;
        Obstacle t3 = new Obstacle(280, 208, -1.3 * speedMult, 24, 24, false, true, Color.GREEN, TURTLE_NORMAL);
        Obstacle t4 = new Obstacle(320, 208, -1.3 * speedMult, 24, 24, false, true, Color.GREEN, TURTLE_NORMAL);
        obstacles.add(t1); obstacles.add(t2); obstacles.add(t3); obstacles.add(t4);

        // River Row 3 (Y = 176) - Long Logs (Right)
        obstacles.add(new Obstacle(10, 176, 2.0 * speedMult, 140, 24, true, false, new Color(139, 69, 19), null));
        obstacles.add(new Obstacle(250, 176, 2.0 * speedMult, 140, 24, true, false, new Color(139, 69, 19), null));

        // River Row 4 (Y = 144) - Short Logs (Right)
        obstacles.add(new Obstacle(50, 144, 1.2 * speedMult, 64, 24, true, false, new Color(139, 69, 19), null));
        obstacles.add(new Obstacle(200, 144, 1.2 * speedMult, 64, 24, true, false, new Color(139, 69, 19), null));
        obstacles.add(new Obstacle(350, 144, 1.2 * speedMult, 64, 24, true, false, new Color(139, 69, 19), null));

        // River Row 5 (Y = 112) - Diving/Normal Turtles (Left)
        Obstacle t5 = new Obstacle(30, 112, -1.6 * speedMult, 24, 24, false, true, Color.GREEN, TURTLE_NORMAL);
        t5.isDivingTurtle = true;
        Obstacle t6 = new Obstacle(70, 112, -1.6 * speedMult, 24, 24, false, true, Color.GREEN, TURTLE_NORMAL);
        t6.isDivingTurtle = true;
        Obstacle t7 = new Obstacle(210, 112, -1.6 * speedMult, 24, 24, false, true, Color.GREEN, TURTLE_NORMAL);
        Obstacle t8 = new Obstacle(250, 112, -1.6 * speedMult, 24, 24, false, true, Color.GREEN, TURTLE_NORMAL);
        obstacles.add(t5); obstacles.add(t6); obstacles.add(t7); obstacles.add(t8);
    }

    private void updateGame() {
        if (state != GameState.RUNNING) return;

        // Ticks down time
        timeLeft -= 0.016; // timer interval roughly 16ms
        if (timeLeft <= 0) {
            handleFrogDeath();
            return;
        }

        // Update obstacle positions
        long timeNow = System.currentTimeMillis();
        for (Obstacle o : obstacles) {
            o.x += o.speed;

            // Wrap around lanes
            if (o.speed > 0 && o.x > GAME_WIDTH) {
                o.x = -o.width;
            } else if (o.speed < 0 && o.x < -o.width) {
                o.x = GAME_WIDTH;
            }

            // Turtle diving animation logic based on system time
            if (o.isTurtle && o.isDivingTurtle) {
                long cycle = (timeNow / 1500) % 3;
                if (cycle == 0) {
                    o.sprite = TURTLE_NORMAL;
                    o.color = Color.GREEN;
                } else if (cycle == 1) {
                    o.sprite = TURTLE_DIVE;
                    o.color = Color.ORANGE;
                } else {
                    o.sprite = null; // Under water!
                }
            }
        }

        // Perform collisions checks
        checkCollisions();

        playPanel.repaint();
    }

    private void checkCollisions() {
        // Y coords mapping:
        // Road: [304, 432]
        // River: [112, 240]
        // Home: 80

        if (frogY >= 304 && frogY <= 432) {
            // Collision with vehicles
            for (Obstacle o : obstacles) {
                if (!o.isLog && !o.isTurtle) {
                    if (intersects(frogX, frogY, 16, 16, o.x, o.y, o.width, o.height)) {
                        handleFrogDeath();
                        return;
                    }
                }
            }
        } else if (frogY >= 112 && frogY <= 240) {
            // River check: Must stay on top of log or turtle
            boolean safeOnWater = false;
            double driftSpeed = 0;

            for (Obstacle o : obstacles) {
                if (o.isLog || (o.isTurtle && o.sprite != null)) {
                    if (intersects(frogX, frogY, 16, 16, o.x, o.y, o.width, o.height)) {
                        safeOnWater = true;
                        driftSpeed = o.speed;
                        break;
                    }
                }
            }

            if (!safeOnWater) {
                handleFrogDeath();
            } else {
                // Drift with obstacle
                frogX += driftSpeed;
                if (frogX < 10 || frogX > GAME_WIDTH - 26) {
                    handleFrogDeath(); // Flowed off-screen
                }
            }
        } else if (frogY == 80) {
            // Check Home Dock arrival
            int landedSlot = -1;
            for (int i = 0; i < 5; i++) {
                if (Math.abs(frogX - homeX[i]) < 18) {
                    landedSlot = i;
                    break;
                }
            }

            if (landedSlot != -1 && !homeOccupied[landedSlot]) {
                // Success!
                homeOccupied[landedSlot] = true;
                score += 50 + (int)(timeLeft * 2);
                playChimeSound();
                resetFrog();

                // Check level completion
                boolean allFull = true;
                for (boolean occupied : homeOccupied) {
                    if (!occupied) {
                        allFull = false;
                        break;
                    }
                }

                if (allFull) {
                    levelComplete();
                }
            } else {
                // Landed in water/shrubbery or occupied slot
                handleFrogDeath();
            }
        }
    }

    private boolean intersects(double fx, double fy, int fw, int fh, double ox, double oy, int ow, int oh) {
        return fx < ox + ow && fx + fw > ox && fy < oy + oh && fy + fh > oy;
    }

    private void handleFrogDeath() {
        lives--;
        playDeathSound();
        if (lives <= 0) {
            gameOver();
        } else {
            resetFrog();
        }
    }

    private void levelComplete() {
        state = GameState.LEVEL_COMPLETE;
        loopTimer.stop();
        level++;
        score += 1000;
        playLevelUpSound();

        Timer delay = new Timer(2000, e -> {
            for (int i = 0; i < 5; i++) {
                homeOccupied[i] = false;
            }
            generateObstacles();
            resetFrog();
            state = GameState.RUNNING;
            loopTimer.start();
        });
        delay.setRepeats(false);
        delay.start();
    }

    private void gameOver() {
        state = GameState.GAME_OVER;
        loopTimer.stop();
        checkAndPromptHighScore();
    }

    private void checkAndPromptHighScore() {
        if (score <= 0) return;

        int durationSecs = 0;
        if (gameStartTime > 0) {
            durationSecs = (int) ((System.currentTimeMillis() - gameStartTime) / 1000);
        }
        final int finalDuration = durationSecs;

        HighScore hs = new HighScore("EuroFrogger");
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
        HighScore hs = new HighScore("EuroFrogger");
        List<HighScore.ScoreEntry> scores = hs.getScores();
        StringBuilder sb = new StringBuilder();
        sb.append("EuroFrogger Bestenliste (Top 10):\n\n");
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
                // Calculate total duration
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

    private void playHopSound() {
        playSynthesizedSound(new int[]{300, 450, 600}, new int[]{25, 25, 30}, true);
    }

    private void playDeathSound() {
        playSynthesizedSound(new int[]{400, 300, 200, 100}, new int[]{80, 80, 80, 100}, false);
    }

    private void playChimeSound() {
        playSynthesizedSound(new int[]{523, 659, 784, 1047}, new int[]{80, 80, 80, 120}, false);
    }

    private void playLevelUpSound() {
        playSynthesizedSound(new int[]{523, 587, 659, 698, 784, 880, 988, 1047}, new int[]{70, 70, 70, 70, 70, 70, 70, 140}, false);
    }

    @Override
    public void dispose() {
        loopTimer.stop();
        state = GameState.GAME_OVER;
        super.dispose();
    }

    // ── Panel Graphics Painting ─────────────────────────────────────────────

    private class FroggerPanel extends JPanel {

        FroggerPanel() {
            setBackground(Color.BLACK);
            setPreferredSize(new Dimension(GAME_WIDTH, GAME_HEIGHT));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Outer boundary border
            g2.setColor(Color.DARK_GRAY);
            g2.drawRect(10, 10, GAME_WIDTH - 20, GAME_HEIGHT - 20);

            // 1. Draw HUD
            g2.setFont(new Font("Monospaced", Font.BOLD, 14));
            g2.setColor(Color.WHITE);
            g2.drawString(String.format("SCORE: %05d", score), 25, 34);
            g2.drawString(String.format("LIVES: %d", lives), 180, 34);
            g2.drawString(String.format("LEVEL: %d", level), 320, 34);

            // 2. Draw Top river banks and Home Slots (Y = 80)
            g2.setColor(new Color(0, 0, 128)); // Blue river
            g2.fillRect(10, 80, GAME_WIDTH - 20, 192);

            g2.setColor(new Color(0, 100, 0)); // Dark Green grass bank
            g2.fillRect(10, 80, GAME_WIDTH - 20, 32);

            // Fill water bays in grass bank
            g2.setColor(new Color(0, 0, 128)); // Water docks
            for (int i = 0; i < 5; i++) {
                g2.fillRect(homeX[i], 80, 24, 32);
                if (homeOccupied[i]) {
                    // Draw lilypad or frog in dock
                    drawSprite(g2, LILYPAD, homeX[i], 84, 2, Color.GREEN);
                    drawSprite(g2, FROG_UP, homeX[i] + 4, 88, 1, Color.YELLOW);
                } else {
                    drawSprite(g2, LILYPAD, homeX[i], 84, 2, new Color(0, 160, 0));
                }
            }

            // 3. Safe Median Strip (Y = 272)
            g2.setColor(new Color(128, 0, 128)); // Purple safe strip
            g2.fillRect(10, 272, GAME_WIDTH - 20, 32);
            // Retro dash lines on median
            g2.setColor(Color.WHITE);
            for (int dx = 20; dx < GAME_WIDTH - 20; dx += 40) {
                g2.fillRect(dx, 286, 15, 4);
            }

            // 4. Starting Zone (Y = 464)
            g2.setColor(new Color(128, 0, 128)); // Purple bottom strip
            g2.fillRect(10, 464, GAME_WIDTH - 20, 32);

            // 5. Draw Obstacles
            for (Obstacle o : obstacles) {
                if (o.isLog) {
                    // Logs are simple wooden rectangles
                    g2.setColor(o.color);
                    g2.fillRect((int) o.x, (int) o.y + 4, o.width, o.height - 8);
                    // Tree rings detailing
                    g2.setColor(new Color(210, 105, 30));
                    g2.drawRect((int) o.x, (int) o.y + 4, o.width, o.height - 8);
                } else if (o.isTurtle) {
                    if (o.sprite != null) {
                        // Drawing group of turtles
                        drawSprite(g2, o.sprite, (int) o.x, (int) o.y, 2, o.color);
                    }
                } else {
                    // Vehicles
                    if (o.sprite != null) {
                        drawSprite(g2, o.sprite, (int) o.x, (int) o.y, 2, o.color);
                    } else {
                        g2.setColor(o.color);
                        g2.fillRect((int) o.x, (int) o.y, o.width, o.height);
                    }
                }
            }

            // 6. Draw Player Frog
            if (state == GameState.RUNNING || state == GameState.LEVEL_COMPLETE) {
                drawSprite(g2, FROG_UP, (int) frogX, (int) frogY, 2, Color.GREEN);
            }

            // 7. Draw Timer Bar at bottom
            g2.setColor(Color.DARK_GRAY);
            g2.fillRect(100, GAME_HEIGHT - 32, 240, 12);
            double pct = timeLeft / MAX_TIME;
            if (pct > 0) {
                g2.setColor(pct > 0.3 ? Color.GREEN : Color.RED);
                g2.fillRect(100, GAME_HEIGHT - 32, (int) (240 * pct), 12);
            }
            g2.setColor(Color.WHITE);
            g2.drawString("TIME", 50, GAME_HEIGHT - 21);

            // 8. Draw Overlays
            if (state == GameState.LAUNCH) {
                g2.setColor(new Color(0, 0, 0, 180));
                g2.fillRect(10, 10, GAME_WIDTH - 20, GAME_HEIGHT - 20);
                g2.setFont(new Font("SansSerif", Font.BOLD, 18));
                g2.setColor(Color.GREEN);
                drawCenteredString(g2, "EUROFROGGER 1981", GAME_HEIGHT / 2 - 30);
                g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
                g2.setColor(Color.WHITE);
                drawCenteredString(g2, "Drücke LEERTASTE zum Starten", GAME_HEIGHT / 2 + 10);
                drawCenteredString(g2, "WASD oder Pfeiltasten = Bewegen", GAME_HEIGHT / 2 + 35);
                drawCenteredString(g2, "Bringe 5 Frösche sicher in die Docks", GAME_HEIGHT / 2 + 55);
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
            } else if (state == GameState.LEVEL_COMPLETE) {
                g2.setColor(new Color(0, 0, 0, 150));
                g2.fillRect(10, 10, GAME_WIDTH - 20, GAME_HEIGHT - 20);
                g2.setFont(new Font("SansSerif", Font.BOLD, 24));
                g2.setColor(Color.GREEN);
                drawCenteredString(g2, "LEVEL GESCHAFFT!", GAME_HEIGHT / 2 - 20);
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

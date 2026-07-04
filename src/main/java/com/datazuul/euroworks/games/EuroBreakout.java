package com.datazuul.euroworks.games;

import com.datazuul.euroworks.apps.EuroAppFrame;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.ActionEvent;

/**
 * Classic Atari Breakout (1976) replica for EuroWorks.
 * Recreates the retro arcade screen including:
 * - 8 rows of colored bricks (Red, Orange, Green, Yellow).
 * - Multi-stage speed acceleration on hitting specific rows or bounce count.
 * - Score counter and remaining ball lives.
 * - Authentic retro sound synth blips using background thread source data lines.
 * - Interactive mouse paddle tracking and keyboard controls.
 */
public class EuroBreakout extends EuroAppFrame {

    private enum GameState {
        LAUNCH, RUNNING, PAUSED, GAME_OVER, GAME_WON
    }

    private static final int GAME_WIDTH = 400;
    private static final int GAME_HEIGHT = 500;

    // Brick rows colors (2 red, 2 orange, 2 green, 2 yellow)
    private static final Color[] ROW_COLORS = {
        new Color(255, 0, 0),       // Row 0: Red
        new Color(255, 0, 0),       // Row 1: Red
        new Color(255, 128, 0),     // Row 2: Orange
        new Color(255, 128, 0),     // Row 3: Orange
        new Color(0, 200, 0),       // Row 4: Green
        new Color(0, 200, 0),       // Row 5: Green
        new Color(225, 225, 0),     // Row 6: Yellow
        new Color(225, 225, 0)      // Row 7: Yellow
    };

    private static final int BRICK_ROWS = 8;
    private static final int BRICK_COLS = 14;
    private static final int BRICK_HEIGHT = 15;
    private static final int BRICK_TOP_OFFSET = 80;
    private static final int BRICK_SIDE_PADDING = 10;

    // State Variables
    private GameState state = GameState.LAUNCH;
    private int score = 0;
    private int lives = 5;
    private int hitCount = 0;
    private boolean keyLeftPressed = false;
    private boolean keyRightPressed = false;
    private int level = 1;
    private long gameStartTime = 0;

    // Paddle
    private double paddleX = (GAME_WIDTH - 60) / 2.0;
    private static final int PADDLE_Y = 440;
    private static final int PADDLE_WIDTH = 50;
    private static final int PADDLE_HEIGHT = 8;

    // Ball
    private double ballX, ballY;
    private double ballDx, ballDy;
    private double baseSpeed = 4.0;
    private static final int BALL_SIZE = 6;

    // Bricks
    private boolean[][] bricks = new boolean[BRICK_ROWS][BRICK_COLS];

    // Graphics/Loop
    private final BreakoutPanel playPanel;
    private final Timer loopTimer;

    public EuroBreakout() {
        super("EuroBreakout (Classic Breakout)");
        // Explicitly size the frame to fit the 400x500 canvas plus headers and borders
        setSize(GAME_WIDTH + 16, GAME_HEIGHT + 74);

        setJMenuBar(buildMenuBar());

        playPanel = new BreakoutPanel();
        setContentPane(playPanel);

        // Frame update loop at ~60fps (16ms)
        loopTimer = new Timer(16, e -> updateGame());

        // Setup Key Bindings (guarantees focus-independent keyboard input)
        InputMap im = playPanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = playPanel.getActionMap();

        // Left Press
        im.put(KeyStroke.getKeyStroke("LEFT"), "pressLeft");
        im.put(KeyStroke.getKeyStroke("A"), "pressLeft");
        am.put("pressLeft", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                keyLeftPressed = true;
            }
        });

        // Left Release
        im.put(KeyStroke.getKeyStroke("released LEFT"), "releaseLeft");
        im.put(KeyStroke.getKeyStroke("released A"), "releaseLeft");
        am.put("releaseLeft", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                keyLeftPressed = false;
            }
        });

        // Right Press
        im.put(KeyStroke.getKeyStroke("RIGHT"), "pressRight");
        im.put(KeyStroke.getKeyStroke("D"), "pressRight");
        am.put("pressRight", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                keyRightPressed = true;
            }
        });

        // Right Release
        im.put(KeyStroke.getKeyStroke("released RIGHT"), "releaseRight");
        im.put(KeyStroke.getKeyStroke("released D"), "releaseRight");
        am.put("releaseRight", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                keyRightPressed = false;
            }
        });

        im.put(KeyStroke.getKeyStroke("SPACE"), "serve");
        am.put("serve", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (state == GameState.LAUNCH) {
                    launchBall();
                }
            }
        });

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

    private void movePaddle(double delta) {
        if (state == GameState.RUNNING || state == GameState.LAUNCH) {
            paddleX += delta;
            paddleX = Math.max(BRICK_SIDE_PADDING, 
                      Math.min(GAME_WIDTH - BRICK_SIDE_PADDING - PADDLE_WIDTH, paddleX));
            if (state == GameState.LAUNCH) {
                resetBallPosition();
            }
            playPanel.repaint();
        }
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
        lives = 5;
        hitCount = 0;
        baseSpeed = 4.0;
        level = 1;
        gameStartTime = 0;
        state = GameState.LAUNCH;

        initBricks();

        resetBallPosition();
        loopTimer.start();
        playPanel.repaint();
    }

    private void initBricks() {
        for (int r = 0; r < BRICK_ROWS; r++) {
            for (int c = 0; c < BRICK_COLS; c++) {
                if (level == 1) {
                    bricks[r][c] = true; // Level 1 is a solid grid
                } else {
                    // Level 2 is a pyramid shape
                    bricks[r][c] = c >= r && c < (BRICK_COLS - r);
                }
            }
        }
    }

    private void resetBallPosition() {
        ballX = paddleX + PADDLE_WIDTH / 2.0 - BALL_SIZE / 2.0;
        ballY = PADDLE_Y - BALL_SIZE - 2;
        ballDx = 0;
        ballDy = 0;
    }

    private void launchBall() {
        state = GameState.RUNNING;
        ballDx = 2.0; // Angled launch
        ballDy = -baseSpeed;
        playSound(440, 100);

        // Record start time on first launch of the game
        if (level == 1 && gameStartTime == 0) {
            gameStartTime = System.currentTimeMillis();
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

    // ── Sound Generator (Authentic 1976 Arcade Chiptune Synth) ────────────────

    private void playSound(int freq, int durationMs) {
        new Thread(() -> {
            try {
                int sampleRate = 8000;
                int numSamples = durationMs * sampleRate / 1000;
                // Pad to at least 3000 samples (375ms) to prevent audio driver dropouts on short clips
                int totalSamples = Math.max(numSamples, 3000);
                byte[] buf = new byte[totalSamples];
                double phase = 0.0;
                for (int i = 0; i < numSamples; i++) {
                    phase += 2.0 * Math.PI * freq / sampleRate;
                    buf[i] = (byte) (Math.sin(phase) >= 0.0 ? 40 : -40);
                }
                // Trailing samples are default-initialized to 0 (silence)
                
                javax.sound.sampled.AudioFormat af = new javax.sound.sampled.AudioFormat(sampleRate, 8, 1, true, false);
                javax.sound.sampled.SourceDataLine sdl = javax.sound.sampled.AudioSystem.getSourceDataLine(af);
                sdl.open(af);
                sdl.start();
                sdl.write(buf, 0, buf.length);
                sdl.drain();
                sdl.close();
            } catch (Exception ex) {
                // Silent fallback
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
                    buf[i] = (byte) (Math.sin(phase) >= 0.0 ? 40 : -40);
                }
                // Trailing samples are 0
                
                javax.sound.sampled.AudioFormat af = new javax.sound.sampled.AudioFormat(sampleRate, 8, 1, true, false);
                javax.sound.sampled.SourceDataLine sdl = javax.sound.sampled.AudioSystem.getSourceDataLine(af);
                sdl.open(af);
                sdl.start();
                sdl.write(buf, 0, buf.length);
                sdl.drain();
                sdl.close();
            } catch (Exception ex) {
                // Silent fallback
            }
        }).start();
    }

    // ── Main Update Logic ───────────────────────────────────────────────────

    private void updateGame() {
        // Move paddle based on continuous keyboard state
        if (keyLeftPressed) {
            movePaddle(-6);
        } else if (keyRightPressed) {
            movePaddle(6);
        }

        if (state != GameState.RUNNING) {
            playPanel.repaint();
            return;
        }

        // 1. Move Ball
        ballX += ballDx;
        ballY += ballDy;

        // 2. Wall Collisions
        if (ballX <= BRICK_SIDE_PADDING) {
            ballX = BRICK_SIDE_PADDING;
            ballDx = -ballDx;
            playSound(500, 30);
        } else if (ballX >= GAME_WIDTH - BRICK_SIDE_PADDING - BALL_SIZE) {
            ballX = GAME_WIDTH - BRICK_SIDE_PADDING - BALL_SIZE;
            ballDx = -ballDx;
            playSound(500, 30);
        }

        if (ballY <= BRICK_SIDE_PADDING) {
            ballY = BRICK_SIDE_PADDING;
            ballDy = -ballDy;
            playSound(500, 30);
        }

        // 3. Fall out of bottom (lose life)
        if (ballY >= GAME_HEIGHT) {
            lives--;
            playSlideSound(220, 60, 400);
            if (lives <= 0) {
                state = GameState.GAME_OVER;
                loopTimer.stop();
                checkAndPromptHighScore();
            } else {
                state = GameState.LAUNCH;
                resetBallPosition();
            }
            playPanel.repaint();
            return;
        }

        // 4. Paddle Collision
        if (ballY + BALL_SIZE >= PADDLE_Y && ballY <= PADDLE_Y + PADDLE_HEIGHT) {
            if (ballX + BALL_SIZE >= paddleX && ballX <= paddleX + PADDLE_WIDTH) {
                // Bounce ball
                ballDy = -baseSpeed;
                
                // Adjust reflection angle based on where ball hits the paddle
                double paddleCenter = paddleX + PADDLE_WIDTH / 2.0;
                double ballCenter = ballX + BALL_SIZE / 2.0;
                double relativeHit = (ballCenter - paddleCenter) / (PADDLE_WIDTH / 2.0);
                ballDx = relativeHit * (baseSpeed * 0.8);

                hitCount++;
                // Classic Breakout speed acceleration milestones
                if (hitCount == 4 || hitCount == 12) {
                    baseSpeed += 1.0;
                    ballDy = -baseSpeed;
                }

                playSound(300, 80);
            }
        }

        // 5. Brick Collisions
        checkBrickCollisions();

        playPanel.repaint();
    }

    private void checkBrickCollisions() {
        int brickWidth = (GAME_WIDTH - 2 * BRICK_SIDE_PADDING) / BRICK_COLS;

        for (int r = 0; r < BRICK_ROWS; r++) {
            for (int c = 0; c < BRICK_COLS; c++) {
                if (!bricks[r][c]) continue;

                int bx = c * brickWidth + BRICK_SIDE_PADDING;
                int by = r * BRICK_HEIGHT + BRICK_TOP_OFFSET;

                // Simple check if ball box overlaps brick box
                if (ballX + BALL_SIZE >= bx && ballX <= bx + brickWidth &&
                    ballY + BALL_SIZE >= by && ballY <= by + BRICK_HEIGHT) {

                    bricks[r][c] = false; // Destroy brick

                    // Award points based on brick color/row
                    int rowPoints = 1;
                    if (r < 2) { // Red
                        rowPoints = 7;
                        if (hitCount < 50) { baseSpeed = Math.max(baseSpeed, 7.0); }
                    } else if (r < 4) { // Orange
                        rowPoints = 5;
                        if (hitCount < 50) { baseSpeed = Math.max(baseSpeed, 6.0); }
                    } else if (r < 6) { // Green
                        rowPoints = 3;
                    }
                    score += rowPoints;

                    // Play classic brick bounce synth tone
                    // Yellow: 100Hz, Green: 200Hz, Orange: 400Hz, Red: 800Hz
                    int freq = 100;
                    if (r < 2) freq = 800;       // Red
                    else if (r < 4) freq = 400;  // Orange
                    else if (r < 6) freq = 200;  // Green
                    playSound(freq, 80);

                    // Collision physics: reflect velocity along minimum overlap axis
                    double overlapLeft = (ballX + BALL_SIZE) - bx;
                    double overlapRight = (bx + brickWidth) - ballX;
                    double overlapTop = (ballY + BALL_SIZE) - by;
                    double overlapBottom = (by + BRICK_HEIGHT) - ballY;

                    double minOverlapX = Math.min(overlapLeft, overlapRight);
                    double minOverlapY = Math.min(overlapTop, overlapBottom);

                    if (minOverlapX < minOverlapY) {
                        ballDx = -ballDx;
                    } else {
                        ballDy = -ballDy;
                    }

                    // Check if all bricks are cleared
                    checkWinCondition();
                    return; // Max one brick hit processed per tick
                }
            }
        }
    }

    private void checkWinCondition() {
        boolean allCleared = true;
        for (int r = 0; r < BRICK_ROWS; r++) {
            for (int c = 0; c < BRICK_COLS; c++) {
                if (bricks[r][c]) {
                    allCleared = false;
                    break;
                }
            }
        }
        if (allCleared) {
            if (level == 1) {
                level = 2;
                playSound(600, 150);
                playSound(800, 300);
                JOptionPane.showMessageDialog(this, "Level 1 geschafft! Weiter zu Level 2 (Pyramide)!", "Level Up", JOptionPane.INFORMATION_MESSAGE);
                initBricks();
                resetBallPosition();
                baseSpeed = 5.0; // Ball starts faster in level 2
                state = GameState.LAUNCH;
                playPanel.repaint();
            } else {
                state = GameState.GAME_WON;
                loopTimer.stop();
                playSound(880, 500);
                checkAndPromptHighScore();
            }
        }
    }

    private void checkAndPromptHighScore() {
        if (score <= 0) return;

        // Calculate total time needed in seconds
        int durationSecs = 0;
        if (gameStartTime > 0) {
            durationSecs = (int) ((System.currentTimeMillis() - gameStartTime) / 1000);
        }
        final int finalDuration = durationSecs;

        HighScore hs = new HighScore("EuroBreakout");
        java.util.List<HighScore.ScoreEntry> scores = hs.getScores();
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
                    } catch (java.io.IOException ex) {
                        JOptionPane.showMessageDialog(this, "Fehler beim Speichern des Highscores.", "Fehler", JOptionPane.ERROR_MESSAGE);
                    }
                }
            });
        }
    }

    private void showHighScoresDialog() {
        HighScore hs = new HighScore("EuroBreakout");
        java.util.List<HighScore.ScoreEntry> scores = hs.getScores();
        StringBuilder sb = new StringBuilder();
        sb.append("EuroBreakout Bestenliste (Top 10):\n\n");
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

    private class BreakoutPanel extends JPanel {

        BreakoutPanel() {
            setBackground(Color.BLACK);
            setPreferredSize(new Dimension(GAME_WIDTH, GAME_HEIGHT));

            // Tracking paddle with Mouse Motion
            MouseAdapter mouseTracker = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    if (state == GameState.RUNNING || state == GameState.LAUNCH) {
                        paddleX = e.getX() - PADDLE_WIDTH / 2.0;
                        // Clamp bounds
                        paddleX = Math.max(BRICK_SIDE_PADDING, 
                                  Math.min(GAME_WIDTH - BRICK_SIDE_PADDING - PADDLE_WIDTH, paddleX));
                        
                        if (state == GameState.LAUNCH) {
                            resetBallPosition();
                        }
                        repaint();
                    }
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    if (state == GameState.LAUNCH) {
                        launchBall();
                    }
                }
            };
            addMouseMotionListener(mouseTracker);
            addMouseListener(mouseTracker);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Draw game screen boundaries/lines
            g2.setColor(Color.DARK_GRAY);
            g2.drawRect(BRICK_SIDE_PADDING, BRICK_SIDE_PADDING, GAME_WIDTH - 2 * BRICK_SIDE_PADDING, GAME_HEIGHT - 2 * BRICK_SIDE_PADDING);

            // 1. Draw HUD
            g2.setFont(new Font("Monospaced", Font.BOLD, 16));
            g2.setColor(Color.GREEN);
            g2.drawString(String.format("PUNKTE: %03d", score), 20, 40);
            
            g2.setColor(Color.WHITE);
            g2.drawString(String.format("LEVEL: %d", level), GAME_WIDTH / 2 - 40, 40);
            
            g2.setColor(Color.RED);
            g2.drawString(String.format("BALLS: %d", lives), GAME_WIDTH - 120, 40);

            // 2. Draw Bricks
            int brickWidth = (GAME_WIDTH - 2 * BRICK_SIDE_PADDING) / BRICK_COLS;
            for (int r = 0; r < BRICK_ROWS; r++) {
                g2.setColor(ROW_COLORS[r]);
                for (int c = 0; c < BRICK_COLS; c++) {
                    if (bricks[r][c]) {
                        int bx = c * brickWidth + BRICK_SIDE_PADDING;
                        int by = r * BRICK_HEIGHT + BRICK_TOP_OFFSET;
                        g2.fillRect(bx + 1, by + 1, brickWidth - 2, BRICK_HEIGHT - 2);
                    }
                }
            }

            // 3. Draw Paddle
            g2.setColor(new Color(255, 200, 0)); // classic yellow paddle
            g2.fillRect((int) paddleX, PADDLE_Y, PADDLE_WIDTH, PADDLE_HEIGHT);

            // 4. Draw Ball
            g2.setColor(Color.WHITE);
            g2.fillOval((int) ballX, (int) ballY, BALL_SIZE, BALL_SIZE);

            // 5. Draw Overlays based on state
            if (state == GameState.LAUNCH) {
                g2.setFont(new Font("SansSerif", Font.BOLD, 16));
                g2.setColor(Color.WHITE);
                drawCenteredString(g2, "CLICK / SPACE TO SERVE", GAME_HEIGHT / 2 + 50);
            } else if (state == GameState.PAUSED) {
                g2.setColor(new Color(0, 0, 0, 160));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setFont(new Font("SansSerif", Font.BOLD, 22));
                g2.setColor(Color.YELLOW);
                drawCenteredString(g2, "PAUSE", GAME_HEIGHT / 2);
            } else if (state == GameState.GAME_OVER) {
                g2.setColor(new Color(0, 0, 0, 200));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setFont(new Font("SansSerif", Font.BOLD, 24));
                g2.setColor(Color.RED);
                drawCenteredString(g2, "GAME OVER", GAME_HEIGHT / 2 - 20);
                g2.setFont(new Font("SansSerif", Font.PLAIN, 14));
                g2.setColor(Color.WHITE);
                drawCenteredString(g2, "Press F2 for New Game", GAME_HEIGHT / 2 + 20);
            } else if (state == GameState.GAME_WON) {
                g2.setColor(new Color(0, 0, 0, 200));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setFont(new Font("SansSerif", Font.BOLD, 24));
                g2.setColor(Color.GREEN);
                drawCenteredString(g2, "VICTORY!", GAME_HEIGHT / 2 - 20);
                g2.setFont(new Font("SansSerif", Font.PLAIN, 14));
                g2.setColor(Color.WHITE);
                drawCenteredString(g2, "Press F2 to Play Again", GAME_HEIGHT / 2 + 20);
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

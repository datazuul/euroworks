package com.datazuul.euroworks.games;

import com.datazuul.euroworks.apps.EuroAppFrame;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Random;

/**
 * EuroTetris - Classic Tetris (1984) replica for EuroWorks.
 * Features:
 * - 10x20 grid playfield with next piece preview frame.
 * - 3D beveled blocks with light highlights and dark shadow bevels.
 * - All 7 classic tetromino shapes (I, O, T, J, L, S, Z) with matching colors.
 * - Left/Right movement, clockwise rotation, soft drop, and hard drop (Spacebar).
 * - Full grid collision detection, line clears, levels, and scoring system.
 * - HighScore integration saving scores and times.
 * - Retro sound chimes for rotation, drops, line clears, and Game Over.
 */
public class EuroTetris extends EuroAppFrame {

    private enum GameState {
        LAUNCH, RUNNING, PAUSED, GAME_OVER
    }

    private static final int GAME_WIDTH = 440;
    private static final int GAME_HEIGHT = 500;

    // Grid details
    private static final int COLS = 10;
    private static final int ROWS = 20;
    private static final int BLOCK_SIZE = 20;
    private static final int GRID_OFFSET_X = 70;
    private static final int GRID_OFFSET_Y = 60;

    // Grid representation: 0 = Empty, 1-7 = Tetromino block types
    private final int[][] grid = new int[ROWS][COLS];
    private final Color[] blockColors = {
        Color.BLACK,          // Dummy
        Color.CYAN,           // I (1)
        Color.BLUE,           // J (2)
        new Color(255, 140, 0), // L (3) - Orange
        Color.YELLOW,         // O (4)
        Color.GREEN,          // S (5)
        new Color(148, 0, 211), // T (6) - Purple
        Color.RED             // Z (7)
    };

    // Tetromino shapes definitions
    private static final int[][][] SHAPES = {
        {}, // Dummy
        // I
        {{0, 0, 0, 0},
         {1, 1, 1, 1},
         {0, 0, 0, 0},
         {0, 0, 0, 0}},
        // J
        {{1, 0, 0},
         {1, 1, 1},
         {0, 0, 0}},
        // L
        {{0, 0, 1},
         {1, 1, 1},
         {0, 0, 0}},
        // O
        {{1, 1},
         {1, 1}},
        // S
        {{0, 1, 1},
         {1, 1, 0},
         {0, 0, 0}},
        // T
        {{0, 1, 0},
         {1, 1, 1},
         {0, 0, 0}},
        // Z
        {{1, 1, 0},
         {0, 1, 1},
         {0, 0, 0}}
    };

    // Game stats
    private GameState state = GameState.LAUNCH;
    private int score = 0;
    private int linesCleared = 0;
    private int level = 1;
    private long gameStartTime = 0;

    // Active block variables
    private int activeType = 0;
    private int[][] activeShape;
    private int activeX = 0;
    private int activeY = 0;

    // Next block preview
    private int nextType = 0;

    // Timers
    private final Timer loopTimer;
    private long lastGravityDrop = 0;
    private final Random random = new Random();
    private boolean soundRunning = false;

    private final TetrisPanel playPanel;

    public EuroTetris() {
        super("EuroTetris (Tetris)");
        setSize(GAME_WIDTH + 16, GAME_HEIGHT + 74);
        setJMenuBar(buildMenuBar());

        playPanel = new TetrisPanel();
        setContentPane(playPanel);

        // Game loop (~60 FPS)
        loopTimer = new Timer(16, e -> updateGame());

        setupKeyboardControls();
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

    private void setupKeyboardControls() {
        InputMap im = playPanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = playPanel.getActionMap();

        im.put(KeyStroke.getKeyStroke("LEFT"), "moveLeft");
        im.put(KeyStroke.getKeyStroke("A"), "moveLeft");
        am.put("moveLeft", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (state == GameState.RUNNING && tryMove(activeShape, activeX - 1, activeY)) {
                    activeX--;
                    playTickSound();
                }
            }
        });

        im.put(KeyStroke.getKeyStroke("RIGHT"), "moveRight");
        im.put(KeyStroke.getKeyStroke("D"), "moveRight");
        am.put("moveRight", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (state == GameState.RUNNING && tryMove(activeShape, activeX + 1, activeY)) {
                    activeX++;
                    playTickSound();
                }
            }
        });

        im.put(KeyStroke.getKeyStroke("UP"), "rotate");
        im.put(KeyStroke.getKeyStroke("W"), "rotate");
        am.put("rotate", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (state == GameState.RUNNING) {
                    rotateActivePiece();
                }
            }
        });

        im.put(KeyStroke.getKeyStroke("DOWN"), "softDrop");
        im.put(KeyStroke.getKeyStroke("S"), "softDrop");
        am.put("softDrop", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (state == GameState.RUNNING) {
                    dropPieceOneStep();
                }
            }
        });

        im.put(KeyStroke.getKeyStroke("SPACE"), "hardDrop");
        am.put("hardDrop", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (state == GameState.LAUNCH) {
                    startGame();
                } else if (state == GameState.RUNNING) {
                    hardDropPiece();
                }
            }
        });

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
        linesCleared = 0;
        level = 1;
        state = GameState.LAUNCH;
        gameStartTime = 0;

        // Clear grid
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                grid[r][c] = 0;
            }
        }

        nextType = random.nextInt(7) + 1;
        spawnPiece();

        loopTimer.start();
        playPanel.repaint();
    }

    private void startGame() {
        state = GameState.RUNNING;
        gameStartTime = System.currentTimeMillis();
        lastGravityDrop = System.currentTimeMillis();
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

    private void spawnPiece() {
        activeType = nextType;
        activeShape = copyMatrix(SHAPES[activeType]);
        activeX = (COLS - activeShape[0].length) / 2;
        activeY = 0;

        nextType = random.nextInt(7) + 1;

        // Check immediate game over
        if (!tryMove(activeShape, activeX, activeY)) {
            gameOver();
        }
    }

    private void gameOver() {
        state = GameState.GAME_OVER;
        loopTimer.stop();
        playGameOverSound();
        checkAndPromptHighScore();
    }

    private void rotateActivePiece() {
        int[][] rotated = rotateMatrixClockwise(activeShape);
        if (tryMove(rotated, activeX, activeY)) {
            activeShape = rotated;
            playTickSound();
        } else {
            // Kick wall tries
            if (tryMove(rotated, activeX - 1, activeY)) {
                activeX--;
                activeShape = rotated;
                playTickSound();
            } else if (tryMove(rotated, activeX + 1, activeY)) {
                activeX++;
                activeShape = rotated;
                playTickSound();
            }
        }
    }

    private void dropPieceOneStep() {
        if (tryMove(activeShape, activeX, activeY + 1)) {
            activeY++;
            score += 1; // points for soft drop
            lastGravityDrop = System.currentTimeMillis();
        } else {
            lockPiece();
        }
    }

    private void hardDropPiece() {
        int steps = 0;
        while (tryMove(activeShape, activeX, activeY + 1)) {
            activeY++;
            steps++;
        }
        score += steps * 2; // points for hard drop
        lockPiece();
        playDropSound();
    }

    private void lockPiece() {
        // Copy active piece blocks onto grid
        for (int r = 0; r < activeShape.length; r++) {
            for (int c = 0; c < activeShape[r].length; c++) {
                if (activeShape[r][c] > 0) {
                    int gy = activeY + r;
                    int gx = activeX + c;
                    if (gy >= 0 && gy < ROWS && gx >= 0 && gx < COLS) {
                        grid[gy][gx] = activeType;
                    }
                }
            }
        }

        // Check line clears
        checkLineClears();

        // Spawn next piece
        spawnPiece();
    }

    private void checkLineClears() {
        int clearedCount = 0;
        for (int r = ROWS - 1; r >= 0; r--) {
            boolean full = true;
            for (int c = 0; c < COLS; c++) {
                if (grid[r][c] == 0) {
                    full = false;
                    break;
                }
            }

            if (full) {
                clearedCount++;
                // Shift down rows
                for (int row = r; row > 0; row--) {
                    System.arraycopy(grid[row - 1], 0, grid[row], 0, COLS);
                }
                // Clear top row
                for (int c = 0; c < COLS; c++) {
                    grid[0][c] = 0;
                }
                r++; // scan same row index again
            }
        }

        if (clearedCount > 0) {
            linesCleared += clearedCount;
            // Classic scoring multiplier
            int baseScore = 0;
            if (clearedCount == 1) baseScore = 100;
            else if (clearedCount == 2) baseScore = 300;
            else if (clearedCount == 3) baseScore = 500;
            else if (clearedCount == 4) baseScore = 800; // Tetris!
            score += baseScore * level;

            // Level progression (every 10 lines)
            level = 1 + linesCleared / 10;

            playLineClearSound();
        }
    }

    private boolean tryMove(int[][] shape, int targetX, int targetY) {
        for (int r = 0; r < shape.length; r++) {
            for (int c = 0; c < shape[r].length; c++) {
                if (shape[r][c] > 0) {
                    int gx = targetX + c;
                    int gy = targetY + r;

                    if (gx < 0 || gx >= COLS || gy >= ROWS) {
                        return false;
                    }

                    if (gy >= 0 && grid[gy][gx] > 0) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private void updateGame() {
        if (state != GameState.RUNNING) return;

        // Auto gravity fall
        long now = System.currentTimeMillis();
        long speedMs = Math.max(100, 800 - (level - 1) * 80);
        if (now - lastGravityDrop > speedMs) {
            dropPieceOneStep();
            lastGravityDrop = now;
        }

        playPanel.repaint();
    }

    // ── Helper Matrix Rotations ─────────────────────────────────────────────

    private int[][] copyMatrix(int[][] original) {
        int[][] copy = new int[original.length][original[0].length];
        for (int i = 0; i < original.length; i++) {
            System.arraycopy(original[i], 0, copy[i], 0, original[i].length);
        }
        return copy;
    }

    private int[][] rotateMatrixClockwise(int[][] m) {
        int r = m.length;
        int c = m[0].length;
        int[][] rotated = new int[c][r];
        for (int i = 0; i < r; i++) {
            for (int j = 0; j < c; j++) {
                rotated[j][r - 1 - i] = m[i][j];
            }
        }
        return rotated;
    }

    private void checkAndPromptHighScore() {
        if (score <= 0) return;

        int durationSecs = 0;
        if (gameStartTime > 0) {
            durationSecs = (int) ((System.currentTimeMillis() - gameStartTime) / 1000);
        }
        final int finalDuration = durationSecs;

        HighScore hs = new HighScore("EuroTetris");
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
        HighScore hs = new HighScore("EuroTetris");
        List<HighScore.ScoreEntry> scores = hs.getScores();
        StringBuilder sb = new StringBuilder();
        sb.append("EuroTetris Bestenliste (Top 10):\n\n");
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

    private void playTickSound() {
        playSynthesizedSound(new int[]{700}, new int[]{20}, true);
    }

    private void playDropSound() {
        playSynthesizedSound(new int[]{200, 100}, new int[]{40, 60}, false);
    }

    private void playLineClearSound() {
        playSynthesizedSound(new int[]{523, 659, 784, 1047}, new int[]{80, 80, 80, 150}, false);
    }

    private void playLevelStartSound() {
        playSynthesizedSound(new int[]{523, 784}, new int[]{100, 150}, false);
    }

    private void playGameOverSound() {
        playSynthesizedSound(new int[]{300, 250, 200}, new int[]{150, 150, 250}, false);
    }

    @Override
    public void dispose() {
        loopTimer.stop();
        state = GameState.GAME_OVER;
        super.dispose();
    }

    // ── Panel Graphics Painting ─────────────────────────────────────────────

    private class TetrisPanel extends JPanel {

        TetrisPanel() {
            setBackground(Color.BLACK);
            setPreferredSize(new Dimension(GAME_WIDTH, GAME_HEIGHT));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Draw game border box
            g2.setColor(Color.DARK_GRAY);
            g2.drawRect(GRID_OFFSET_X - 1, GRID_OFFSET_Y - 1, COLS * BLOCK_SIZE + 2, ROWS * BLOCK_SIZE + 2);

            // Draw Grid cells (locked pieces)
            for (int r = 0; r < ROWS; r++) {
                for (int c = 0; c < COLS; c++) {
                    int val = grid[r][c];
                    if (val > 0) {
                        drawBeveledBlock(g2, GRID_OFFSET_X + c * BLOCK_SIZE, GRID_OFFSET_Y + r * BLOCK_SIZE, blockColors[val]);
                    } else {
                        // Background grid squares to the edges of the fields
                        g2.setColor(new Color(40, 40, 40));
                        g2.drawRect(GRID_OFFSET_X + c * BLOCK_SIZE, GRID_OFFSET_Y + r * BLOCK_SIZE, BLOCK_SIZE, BLOCK_SIZE);
                    }
                }
            }

            // Draw Active falling piece
            if (state == GameState.RUNNING) {
                for (int r = 0; r < activeShape.length; r++) {
                    for (int c = 0; c < activeShape[r].length; c++) {
                        if (activeShape[r][c] > 0) {
                            int gx = GRID_OFFSET_X + (activeX + c) * BLOCK_SIZE;
                            int gy = GRID_OFFSET_Y + (activeY + r) * BLOCK_SIZE;
                            if (gy >= GRID_OFFSET_Y) {
                                drawBeveledBlock(g2, gx, gy, blockColors[activeType]);
                            }
                        }
                    }
                }
            }

            // Draw Right Panel details (Score, Level, Next Piece)
            drawRightPanel(g2);

            // Draw Overlays
            if (state == GameState.LAUNCH) {
                g2.setColor(new Color(0, 0, 0, 180));
                g2.fillRect(GRID_OFFSET_X, GRID_OFFSET_Y, COLS * BLOCK_SIZE, ROWS * BLOCK_SIZE);
                g2.setFont(new Font("SansSerif", Font.BOLD, 18));
                g2.setColor(Color.GREEN);
                drawCenteredString(g2, "EUROTETRIS 1984", GAME_HEIGHT / 2 - 30);
                g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
                g2.setColor(Color.WHITE);
                drawCenteredString(g2, "Drücke LEERTASTE", GAME_HEIGHT / 2 + 10);
                drawCenteredString(g2, "A / D = Bewegen", GAME_HEIGHT / 2 + 35);
                drawCenteredString(g2, "W = Drehen", GAME_HEIGHT / 2 + 55);
                drawCenteredString(g2, "S = Soft Drop", GAME_HEIGHT / 2 + 75);
                drawCenteredString(g2, "LEERTASTE = Hard Drop", GAME_HEIGHT / 2 + 95);
            } else if (state == GameState.PAUSED) {
                g2.setColor(new Color(0, 0, 0, 160));
                g2.fillRect(GRID_OFFSET_X, GRID_OFFSET_Y, COLS * BLOCK_SIZE, ROWS * BLOCK_SIZE);
                g2.setFont(new Font("SansSerif", Font.BOLD, 22));
                g2.setColor(Color.YELLOW);
                drawCenteredString(g2, "PAUSE", GAME_HEIGHT / 2);
            } else if (state == GameState.GAME_OVER) {
                g2.setColor(new Color(0, 0, 0, 200));
                g2.fillRect(GRID_OFFSET_X, GRID_OFFSET_Y, COLS * BLOCK_SIZE, ROWS * BLOCK_SIZE);
                g2.setFont(new Font("SansSerif", Font.BOLD, 24));
                g2.setColor(Color.RED);
                drawCenteredString(g2, "GAME OVER", GAME_HEIGHT / 2 - 20);
                g2.setFont(new Font("SansSerif", Font.PLAIN, 14));
                g2.setColor(Color.WHITE);
                drawCenteredString(g2, "Drücke F2", GAME_HEIGHT / 2 + 20);
            }

            g2.dispose();
        }

        private void drawBeveledBlock(Graphics2D g2, int x, int y, Color c) {
            // Draw 3D beveled block: fill base, draw brighter highlights and darker shadows
            g2.setColor(c);
            g2.fillRect(x, y, BLOCK_SIZE, BLOCK_SIZE);

            // Light highlight edges (Top, Left)
            g2.setColor(c.brighter().brighter());
            g2.fillRect(x, y, BLOCK_SIZE, 3);
            g2.fillRect(x, y, 3, BLOCK_SIZE);

            // Dark shadow edges (Bottom, Right)
            g2.setColor(c.darker().darker());
            g2.fillRect(x, y + BLOCK_SIZE - 3, BLOCK_SIZE, 3);
            g2.fillRect(x + BLOCK_SIZE - 3, y, 3, BLOCK_SIZE);

            // Inner dark divider lines to separate 3D grids
            g2.setColor(Color.BLACK);
            g2.drawRect(x, y, BLOCK_SIZE - 1, BLOCK_SIZE - 1);
        }

        private void drawRightPanel(Graphics2D g2) {
            int px = GRID_OFFSET_X + COLS * BLOCK_SIZE + 25;

            // Score & Stats
            g2.setFont(new Font("Monospaced", Font.BOLD, 12));
            g2.setColor(Color.WHITE);
            g2.drawString("SCORE", px, 80);
            g2.setColor(Color.GREEN);
            g2.drawString(String.format("%05d", score), px, 100);

            g2.setColor(Color.WHITE);
            g2.drawString("LEVEL", px, 130);
            g2.setColor(Color.YELLOW);
            g2.drawString(String.format("%02d", level), px, 150);

            g2.setColor(Color.WHITE);
            g2.drawString("LINES", px, 180);
            g2.setColor(Color.CYAN);
            g2.drawString(String.format("%d", linesCleared), px, 200);

            // Next Piece preview box
            g2.setColor(Color.WHITE);
            g2.drawString("NEXT PIECE", px, 250);
            g2.setColor(Color.DARK_GRAY);
            g2.drawRect(px, 260, 80, 80);

            if (state == GameState.RUNNING && nextType > 0) {
                int[][] nextShape = SHAPES[nextType];
                Color c = blockColors[nextType];
                int startX = px + (80 - nextShape[0].length * BLOCK_SIZE) / 2;
                int startY = 260 + (80 - nextShape.length * BLOCK_SIZE) / 2;

                for (int r = 0; r < nextShape.length; r++) {
                    for (int col = 0; col < nextShape[r].length; col++) {
                        if (nextShape[r][col] > 0) {
                            drawBeveledBlock(g2, startX + col * BLOCK_SIZE, startY + r * BLOCK_SIZE, c);
                        }
                    }
                }
            }
        }

        private void drawCenteredString(Graphics2D g2, String text, int y) {
            FontMetrics fm = g2.getFontMetrics();
            int x = GRID_OFFSET_X + (COLS * BLOCK_SIZE - fm.stringWidth(text)) / 2;
            g2.drawString(text, x, y);
        }
    }
}

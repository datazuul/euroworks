package com.datazuul.euroworks.games;

import com.datazuul.euroworks.apps.EuroAppFrame;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;

/**
 * EuroQix - Classic Qix (Taito 1981) replica for EuroWorks.
 * Features:
 * - A grid-based playfield (100x100 grid mapped to screen area).
 * - Player Marker traveling safely along perimeter borders.
 * - Stix drawing (Hold Space to draw) into unclaimed territory.
 * - Flood-fill algorithm to capture enclosed territories and claim them.
 * - Swirling Qix vector monster bouncing within unclaimed territory.
 * - Sparx enemies crawling along borders to pursue the player.
 * - Claimed percentage HUD display.
 * - Wave progression upon reaching 70% territory claim.
 * - HighScore integration saving scores and times.
 * - Programmatic retro sound effects for drawing, enclosing, sparks, and deaths.
 */
public class EuroQix extends EuroAppFrame {

    private enum GameState {
        LAUNCH, RUNNING, PAUSED, GAME_OVER, WAVE_COMPLETE
    }

    private static final int GAME_WIDTH = 440;
    private static final int GAME_HEIGHT = 500;

    // Grid details
    private static final int GRID_SIZE = 100;
    private static final int CELL_SIZE = 4;
    private static final int OFFSET_X = 20;
    private static final int OFFSET_Y = 60;

    // Grid states
    private static final byte CELL_BORDER = 0;
    private static final byte CELL_UNCLAIMED = 1;
    private static final byte CELL_CLAIMED = 2;
    private static final byte CELL_STIX = 3;

    private final byte[][] grid = new byte[GRID_SIZE][GRID_SIZE];

    // Player marker
    private int px = 50;
    private int py = 99;
    private int lastDirectionX = 0;
    private int lastDirectionY = 0;
    private boolean isDrawing = false;
    private final List<Point> stixPath = new ArrayList<>();

    // Qix swirling stick monster
    private static class QixStick {
        double x, y;
        double vx, vy;
        final double[] historyX = new double[10];
        final double[] historyY = new double[10];

        QixStick(double x, double y) {
            this.x = x;
            this.y = y;
            Random rand = new Random();
            double angle = rand.nextDouble() * 2 * Math.PI;
            this.vx = Math.cos(angle) * 1.5;
            this.vy = Math.sin(angle) * 1.5;
            for (int i = 0; i < 10; i++) {
                historyX[i] = x;
                historyY[i] = y;
            }
        }

        void update(byte[][] playfield, Random rand) {
            // Shift history
            System.arraycopy(historyX, 0, historyX, 1, 9);
            System.arraycopy(historyY, 0, historyY, 1, 9);

            // Move head
            double nextX = x + vx;
            double nextY = y + vy;

            // Bounce checks
            int gx = (int) nextX;
            int gy = (int) nextY;
            if (gx < 0 || gx >= GRID_SIZE || gy < 0 || gy >= GRID_SIZE || playfield[gx][gy] != CELL_UNCLAIMED) {
                // Change direction
                double angle = rand.nextDouble() * 2 * Math.PI;
                vx = Math.cos(angle) * (1.2 + rand.nextDouble() * 0.8);
                vy = Math.sin(angle) * (1.2 + rand.nextDouble() * 0.8);
            } else {
                x = nextX;
                y = nextY;
            }

            historyX[0] = x;
            historyY[0] = y;
        }
    }
    private QixStick qix;

    // Sparx sparks crawling on border
    private static class Sparx {
        int x, y;
        int dir; // 1 = clockwise, -1 = counterclockwise
        long lastMoveTime = 0;

        Sparx(int x, int y, int dir) {
            this.x = x;
            this.y = y;
            this.dir = dir;
        }

        void update(byte[][] playfield, int playerX, int playerY) {
            long now = System.currentTimeMillis();
            if (now - lastMoveTime < 100) return; // movement rate limit
            lastMoveTime = now;

            // Find next valid border neighbor (prefer moving in dir direction)
            int[] dx = { 0, 1, 0, -1 };
            int[] dy = { -1, 0, 1, 0 };
            
            // Check adjacent cells
            int bestX = x;
            int bestY = y;
            double bestDist = Double.MAX_VALUE;

            for (int i = 0; i < 4; i++) {
                int nx = x + dx[i];
                int ny = y + dy[i];
                if (nx >= 0 && nx < GRID_SIZE && ny >= 0 && ny < GRID_SIZE) {
                    if (playfield[nx][ny] == CELL_BORDER || playfield[nx][ny] == CELL_CLAIMED) {
                        double dist = Math.hypot(nx - playerX, ny - playerY);
                        if (dist < bestDist) {
                            bestDist = dist;
                            bestX = nx;
                            bestY = ny;
                        }
                    }
                }
            }
            x = bestX;
            y = bestY;
        }
    }
    private Sparx sparx1;
    private Sparx sparx2;

    // Game stats
    private GameState state = GameState.LAUNCH;
    private int score = 0;
    private int lives = 3;
    private int wave = 1;
    private double percentClaimed = 0.0;
    private long gameStartTime = 0;

    // Controls
    private boolean keyUp = false;
    private boolean keyDown = false;
    private boolean keyLeft = false;
    private boolean keyRight = false;
    private boolean keySpace = false;

    private final QixPanel playPanel;
    private final Timer loopTimer;
    private final Random random = new Random();
    private boolean soundRunning = false;

    public EuroQix() {
        super("EuroQix (Qix)");
        setSize(GAME_WIDTH + 16, GAME_HEIGHT + 74);
        setJMenuBar(buildMenuBar());

        playPanel = new QixPanel();
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

        // Direction Keys Press
        im.put(KeyStroke.getKeyStroke("UP"), "pressUp");
        im.put(KeyStroke.getKeyStroke("W"), "pressUp");
        am.put("pressUp", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { keyUp = true; }
        });

        im.put(KeyStroke.getKeyStroke("DOWN"), "pressDown");
        im.put(KeyStroke.getKeyStroke("S"), "pressDown");
        am.put("pressDown", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { keyDown = true; }
        });

        im.put(KeyStroke.getKeyStroke("LEFT"), "pressLeft");
        im.put(KeyStroke.getKeyStroke("A"), "pressLeft");
        am.put("pressLeft", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { keyLeft = true; }
        });

        im.put(KeyStroke.getKeyStroke("RIGHT"), "pressRight");
        im.put(KeyStroke.getKeyStroke("D"), "pressRight");
        am.put("pressRight", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { keyRight = true; }
        });

        // Direction Keys Release
        im.put(KeyStroke.getKeyStroke("released UP"), "releaseUp");
        im.put(KeyStroke.getKeyStroke("released W"), "releaseUp");
        am.put("releaseUp", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { keyUp = false; }
        });

        im.put(KeyStroke.getKeyStroke("released DOWN"), "releaseDown");
        im.put(KeyStroke.getKeyStroke("released S"), "releaseDown");
        am.put("releaseDown", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { keyDown = false; }
        });

        im.put(KeyStroke.getKeyStroke("released LEFT"), "releaseLeft");
        im.put(KeyStroke.getKeyStroke("released A"), "releaseLeft");
        am.put("releaseLeft", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { keyLeft = false; }
        });

        im.put(KeyStroke.getKeyStroke("released RIGHT"), "releaseRight");
        im.put(KeyStroke.getKeyStroke("released D"), "releaseRight");
        am.put("releaseRight", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { keyRight = false; }
        });

        // Space (Draw Modifier)
        im.put(KeyStroke.getKeyStroke("SPACE"), "pressSpace");
        am.put("pressSpace", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (state == GameState.LAUNCH) {
                    startGame();
                } else {
                    keySpace = true;
                }
            }
        });

        im.put(KeyStroke.getKeyStroke("released SPACE"), "releaseSpace");
        am.put("releaseSpace", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { keySpace = false; }
        });

        // Menu Shortcuts
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

        resetPlayfield();
        loopTimer.start();
        playPanel.repaint();
    }

    private void resetPlayfield() {
        // Clear grid
        for (int x = 0; x < GRID_SIZE; x++) {
            for (int y = 0; y < GRID_SIZE; y++) {
                if (x == 0 || x == GRID_SIZE - 1 || y == 0 || y == GRID_SIZE - 1) {
                    grid[x][y] = CELL_BORDER;
                } else {
                    grid[x][y] = CELL_UNCLAIMED;
                }
            }
        }

        px = 50;
        py = 99;
        isDrawing = false;
        stixPath.clear();
        percentClaimed = 0.0;

        qix = new QixStick(50, 50);
        sparx1 = new Sparx(0, 0, 1);
        sparx2 = new Sparx(GRID_SIZE - 1, 0, -1);
    }

    private void startGame() {
        state = GameState.RUNNING;
        gameStartTime = System.currentTimeMillis();
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

    private void updateGame() {
        if (state != GameState.RUNNING) return;

        // 1. Move Player Marker
        movePlayer();

        // 2. Update Qix swirl
        qix.update(grid, random);

        // 3. Update Sparx sparks
        sparx1.update(grid, px, py);
        sparx2.update(grid, px, py);

        // 4. Collision check
        checkCollisions();

        playPanel.repaint();
    }

    private void movePlayer() {
        // Determine input direction
        int dx = 0;
        int dy = 0;
        if (keyUp) dy = -1;
        else if (keyDown) dy = 1;
        else if (keyLeft) dx = -1;
        else if (keyRight) dx = 1;

        if (dx == 0 && dy == 0) return;

        // Prevent immediate reverse direction while drawing
        if (isDrawing && dx == -lastDirectionX && dy == -lastDirectionY) {
            return;
        }

        int nx = px + dx;
        int ny = py + dy;

        // Bounds constraints
        if (nx < 0 || nx >= GRID_SIZE || ny < 0 || ny >= GRID_SIZE) return;

        byte targetCell = grid[nx][ny];

        if (keySpace) {
            // Initiate/Continue drawing Stix
            if (targetCell == CELL_UNCLAIMED) {
                isDrawing = true;
                px = nx;
                py = ny;
                grid[px][py] = CELL_STIX;
                stixPath.add(new Point(px, py));
                lastDirectionX = dx;
                lastDirectionY = dy;
                playDrawTickSound();
            } else if ((targetCell == CELL_BORDER || targetCell == CELL_CLAIMED) && isDrawing) {
                // Completed Stix!
                px = nx;
                py = ny;
                completeStix();
            }
        } else {
            // Move safely along borders without drawing
            if (targetCell == CELL_BORDER || targetCell == CELL_CLAIMED) {
                isDrawing = false;
                stixPath.clear();
                px = nx;
                py = ny;
                lastDirectionX = dx;
                lastDirectionY = dy;
            }
        }
    }

    private void completeStix() {
        isDrawing = false;
        
        // 1. Flood-fill from Qix position to find the unclaimed segment
        boolean[][] reached = new boolean[GRID_SIZE][GRID_SIZE];
        Queue<Point> queue = new LinkedList<>();

        int qx = (int) qix.x;
        int qy = (int) qix.y;
        if (qx >= 0 && qx < GRID_SIZE && qy >= 0 && qy < GRID_SIZE && grid[qx][qy] == CELL_UNCLAIMED) {
            queue.add(new Point(qx, qy));
            reached[qx][qy] = true;
        }

        while (!queue.isEmpty()) {
            Point p = queue.poll();
            int[] dx = { 0, 1, 0, -1 };
            int[] dy = { -1, 0, 1, 0 };
            for (int i = 0; i < 4; i++) {
                int nx = p.x + dx[i];
                int ny = p.y + dy[i];
                if (nx >= 0 && nx < GRID_SIZE && ny >= 0 && ny < GRID_SIZE) {
                    if (!reached[nx][ny] && grid[nx][ny] == CELL_UNCLAIMED) {
                        reached[nx][ny] = true;
                        queue.add(new Point(nx, ny));
                    }
                }
            }
        }

        // 2. Claim anything that Qix could not reach
        int claimedNowCount = 0;
        for (int x = 0; x < GRID_SIZE; x++) {
            for (int y = 0; y < GRID_SIZE; y++) {
                if (grid[x][y] == CELL_UNCLAIMED && !reached[x][y]) {
                    grid[x][y] = CELL_CLAIMED;
                    claimedNowCount++;
                } else if (grid[x][y] == CELL_STIX) {
                    grid[x][y] = CELL_BORDER; // convert path to border
                }
            }
        }

        stixPath.clear();

        // 3. Score & claimed calculation
        score += claimedNowCount * wave;
        calculatePercentageClaimed();
        playEncloseSound();

        // Check wave success (70%)
        if (percentClaimed >= 70.0) {
            nextLevel();
        }
    }

    private void calculatePercentageClaimed() {
        int totalCells = (GRID_SIZE - 2) * (GRID_SIZE - 2);
        int claimedCount = 0;
        for (int x = 1; x < GRID_SIZE - 1; x++) {
            for (int y = 1; y < GRID_SIZE - 1; y++) {
                if (grid[x][y] == CELL_CLAIMED) {
                    claimedCount++;
                }
            }
        }
        percentClaimed = (claimedCount * 100.0) / totalCells;
    }

    private void checkCollisions() {
        // 1. Qix hits drawing Stix
        for (int i = 0; i < 10; i++) {
            int qx = (int) qix.historyX[i];
            int qy = (int) qix.historyY[i];
            if (qx >= 0 && qx < GRID_SIZE && qy >= 0 && qy < GRID_SIZE) {
                if (grid[qx][qy] == CELL_STIX) {
                    handleDeath();
                    return;
                }
            }
        }

        // 2. Sparx hits Player Marker
        if (sparx1.x == px && sparx1.y == py || sparx2.x == px && sparx2.y == py) {
            handleDeath();
        }
    }

    private void handleDeath() {
        lives--;
        playExplosionSound();
        isDrawing = false;
        
        // Remove active stix path
        for (Point p : stixPath) {
            grid[p.x][p.y] = CELL_UNCLAIMED;
        }
        stixPath.clear();

        if (lives <= 0) {
            state = GameState.GAME_OVER;
            loopTimer.stop();
            checkAndPromptHighScore();
        } else {
            // Find a safe spot along the border
            px = 50;
            py = 99;
        }
    }

    private void nextLevel() {
        state = GameState.WAVE_COMPLETE;
        loopTimer.stop();
        wave++;
        score += 1000;
        playLevelUpSound();

        Timer delay = new Timer(2500, e -> {
            resetPlayfield();
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

        HighScore hs = new HighScore("EuroQix");
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
        HighScore hs = new HighScore("EuroQix");
        List<HighScore.ScoreEntry> scores = hs.getScores();
        StringBuilder sb = new StringBuilder();
        sb.append("EuroQix Bestenliste (Top 10):\n\n");
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

    private void playDrawTickSound() {
        playSynthesizedSound(new int[]{300, 350}, new int[]{20, 20}, true);
    }

    private void playEncloseSound() {
        playSynthesizedSound(new int[]{440, 554, 659}, new int[]{80, 80, 120}, false);
    }

    private void playExplosionSound() {
        playSynthesizedSound(new int[]{150, 100, 50}, new int[]{150, 150, 250}, false);
    }

    private void playLevelStartSound() {
        playSynthesizedSound(new int[]{523, 659}, new int[]{100, 150}, false);
    }

    private void playLevelUpSound() {
        playSynthesizedSound(new int[]{523, 659, 784, 1047}, new int[]{100, 100, 100, 300}, false);
    }

    @Override
    public void dispose() {
        loopTimer.stop();
        state = GameState.GAME_OVER;
        super.dispose();
    }

    // ── Panel Graphics Painting ─────────────────────────────────────────────

    private class QixPanel extends JPanel {

        QixPanel() {
            setBackground(Color.BLACK);
            setPreferredSize(new Dimension(GAME_WIDTH, GAME_HEIGHT));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();

            // HUD
            g2.setFont(new Font("Monospaced", Font.BOLD, 14));
            g2.setColor(Color.WHITE);
            g2.drawString(String.format("SCORE: %05d", score), 25, 34);
            g2.drawString(String.format("CLAIMED: %.1f%%", percentClaimed), 160, 34);
            g2.drawString(String.format("LIVES: %d", lives), 340, 34);

            // Draw Grid cells
            for (int x = 0; x < GRID_SIZE; x++) {
                for (int y = 0; y < GRID_SIZE; y++) {
                    byte cell = grid[x][y];
                    if (cell == CELL_BORDER) {
                        g2.setColor(Color.WHITE);
                        g2.fillRect(OFFSET_X + x * CELL_SIZE, OFFSET_Y + y * CELL_SIZE, CELL_SIZE, CELL_SIZE);
                    } else if (cell == CELL_CLAIMED) {
                        g2.setColor(new Color(0, 100, 100)); // claimed fills cyan-blue
                        g2.fillRect(OFFSET_X + x * CELL_SIZE, OFFSET_Y + y * CELL_SIZE, CELL_SIZE, CELL_SIZE);
                    } else if (cell == CELL_STIX) {
                        g2.setColor(Color.YELLOW);
                        g2.fillRect(OFFSET_X + x * CELL_SIZE, OFFSET_Y + y * CELL_SIZE, CELL_SIZE, CELL_SIZE);
                    }
                }
            }

            // Draw Qix swirling stick
            if (state == GameState.RUNNING || state == GameState.WAVE_COMPLETE) {
                for (int i = 0; i < 9; i++) {
                    // Trailing color cycle lines
                    g2.setColor(Color.getHSBColor((float) ((System.currentTimeMillis() / 200.0 + i * 0.1) % 1.0), 1.0f, 1.0f));
                    g2.setStroke(new BasicStroke(1.5f));
                    int x1 = OFFSET_X + (int) (qix.historyX[i] * CELL_SIZE);
                    int y1 = OFFSET_Y + (int) (qix.historyY[i] * CELL_SIZE);
                    int x2 = OFFSET_X + (int) (qix.historyX[i + 1] * CELL_SIZE);
                    int y2 = OFFSET_Y + (int) (qix.historyY[i + 1] * CELL_SIZE);
                    g2.drawLine(x1, y1, x2, y2);
                }
            }

            // Draw Sparx (Sparks)
            if (state == GameState.RUNNING) {
                g2.setColor(Color.RED);
                g2.fillOval(OFFSET_X + sparx1.x * CELL_SIZE - 2, OFFSET_Y + sparx1.y * CELL_SIZE - 2, 8, 8);
                g2.setColor(Color.ORANGE);
                g2.fillOval(OFFSET_X + sparx2.x * CELL_SIZE - 2, OFFSET_Y + sparx2.y * CELL_SIZE - 2, 8, 8);
            }

            // Draw Player Marker (Diamond shape)
            if (state == GameState.RUNNING || state == GameState.WAVE_COMPLETE) {
                int pxScreen = OFFSET_X + px * CELL_SIZE;
                int pyScreen = OFFSET_Y + py * CELL_SIZE;
                int[] xPoints = { pxScreen, pxScreen + 5, pxScreen, pxScreen - 5 };
                int[] yPoints = { pyScreen - 5, pyScreen, pyScreen + 5, pyScreen };
                g2.setColor(Color.GREEN);
                g2.fillPolygon(xPoints, yPoints, 4);
            }

            // Draw Overlays
            if (state == GameState.LAUNCH) {
                g2.setColor(new Color(0, 0, 0, 180));
                g2.fillRect(OFFSET_X, OFFSET_Y, GRID_SIZE * CELL_SIZE, GRID_SIZE * CELL_SIZE);
                g2.setFont(new Font("SansSerif", Font.BOLD, 18));
                g2.setColor(Color.GREEN);
                drawCenteredString(g2, "EUROQIX 1981", GAME_HEIGHT / 2 - 30);
                g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
                g2.setColor(Color.WHITE);
                drawCenteredString(g2, "Drücke LEERTASTE zum Starten", GAME_HEIGHT / 2 + 10);
                drawCenteredString(g2, "WASD oder Pfeiltasten = Bewegen", GAME_HEIGHT / 2 + 35);
                drawCenteredString(g2, "Halte LEERTASTE = Territorium zeichnen", GAME_HEIGHT / 2 + 55);
                drawCenteredString(g2, "Fülle mindestens 70% der Fläche aus", GAME_HEIGHT / 2 + 75);
            } else if (state == GameState.PAUSED) {
                g2.setColor(new Color(0, 0, 0, 160));
                g2.fillRect(OFFSET_X, OFFSET_Y, GRID_SIZE * CELL_SIZE, GRID_SIZE * CELL_SIZE);
                g2.setFont(new Font("SansSerif", Font.BOLD, 22));
                g2.setColor(Color.YELLOW);
                drawCenteredString(g2, "PAUSE", GAME_HEIGHT / 2);
            } else if (state == GameState.GAME_OVER) {
                g2.setColor(new Color(0, 0, 0, 200));
                g2.fillRect(OFFSET_X, OFFSET_Y, GRID_SIZE * CELL_SIZE, GRID_SIZE * CELL_SIZE);
                g2.setFont(new Font("SansSerif", Font.BOLD, 24));
                g2.setColor(Color.RED);
                drawCenteredString(g2, "GAME OVER", GAME_HEIGHT / 2 - 20);
                g2.setFont(new Font("SansSerif", Font.PLAIN, 14));
                g2.setColor(Color.WHITE);
                drawCenteredString(g2, "Drücke F2 für Neues Spiel", GAME_HEIGHT / 2 + 20);
            } else if (state == GameState.WAVE_COMPLETE) {
                g2.setColor(new Color(0, 0, 0, 150));
                g2.fillRect(OFFSET_X, OFFSET_Y, GRID_SIZE * CELL_SIZE, GRID_SIZE * CELL_SIZE);
                g2.setFont(new Font("SansSerif", Font.BOLD, 24));
                g2.setColor(Color.GREEN);
                drawCenteredString(g2, "ZIEL ERREICHT!", GAME_HEIGHT / 2 - 20);
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

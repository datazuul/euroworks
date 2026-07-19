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
 * EuroArtilleryDuel - Classic 1983 Artillery Duel simulator for EuroWorks.
 * Features:
 * - Jagged random mountain terrain generation with real-time cratering explosions.
 * - Turn-based gameplay: Player vs Player or Player vs AI (Computer).
 * - Wind changes dynamically every turn, affecting trajectory.
 * - Ballistics simulator with wind resistance and gravity.
 * - Sound effects: firing boom, whistle shell, explosion, and victory arpeggios.
 * - Match system: First to 3 round wins. High score savings.
 */
public class EuroArtilleryDuel extends EuroAppFrame {

    private static final int GAME_WIDTH = 440;
    private static final int GAME_HEIGHT = 460;

    private enum GameState {
        LAUNCH, PLAYING, SHELL_FLYING, ROUND_OVER, GAME_OVER
    }

    private GameState state = GameState.LAUNCH;
    private boolean vsComputer = true;

    // Terrain height array
    private final int[] terrainY = new int[GAME_WIDTH];

    // Cannons positions
    private int p1X = 40;
    private int p1Y;
    private int p2X = GAME_WIDTH - 40;
    private int p2Y;

    // Cannon states (angles in degrees: 0 to 90, power: 10 to 100)
    private double p1Angle = 45;
    private double p1Power = 50;
    private double p2Angle = 45;
    private double p2Power = 50;

    // Target variables during adjustment
    private int activePlayer = 1; // 1 = Left, 2 = Right
    private double wind = 0; // -5 to +5 (negative is left, positive is right)

    // Shell projectile variables
    private double shellX, shellY;
    private double shellVx, shellVy;
    private final List<Point> trail = new ArrayList<>();

    // Score states
    private int p1Wins = 0;
    private int p2Wins = 0;
    private String winText = "";

    // AI Variables
    private int aiTurnCount = 0;
    private double aiLastDiff = 0; // tracking if shot went too short or too long
    private double aiEstPower = 55;

    // Timers & utilities
    private final Timer loopTimer;
    private final Random random = new Random();
    private boolean soundRunning = false;

    // Keyboard flags
    private boolean keyUp = false;
    private boolean keyDown = false;
    private boolean keyLeft = false;
    private boolean keyRight = false;

    private final ArtilleryPanel playPanel;

    public EuroArtilleryDuel() {
        super("EuroArtilleryDuel (Artillery)");
        setSize(GAME_WIDTH + 16, GAME_HEIGHT + 74);

        playPanel = new ArtilleryPanel();
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

        loopTimer = new Timer(20, e -> updatePhysics());
        loopTimer.start();
    }

    private void generateNewTerrain() {
        // Build smooth mountain slopes using sin/cos layers
        double base = GAME_HEIGHT - 130;
        double f1 = 0.005 + random.nextDouble() * 0.01;
        double f2 = 0.02 + random.nextDouble() * 0.02;
        int amplitude1 = 40 + random.nextInt(40);
        int amplitude2 = 10 + random.nextInt(15);

        for (int x = 0; x < GAME_WIDTH; x++) {
            terrainY[x] = (int) (base + Math.sin(x * f1) * amplitude1 + Math.cos(x * f2) * amplitude2);
        }

        // Align Player 1 and Player 2 locations on flatter slopes
        p1X = 35 + random.nextInt(25);
        p2X = GAME_WIDTH - 60 + random.nextInt(25);

        p1Y = terrainY[p1X];
        p2Y = terrainY[p2X];

        // Ensure flat pad for both cannons
        for (int i = p1X - 5; i <= p1X + 5; i++) terrainY[i] = p1Y;
        for (int i = p2X - 5; i <= p2X + 5; i++) terrainY[i] = p2Y;
    }

    private void changeTurn() {
        activePlayer = (activePlayer == 1) ? 2 : 1;
        // Generate new wind
        wind = -5.0 + random.nextDouble() * 10.0;
        trail.clear();
        state = GameState.PLAYING;

        if (vsComputer && activePlayer == 2) {
            triggerAIThinking();
        }
    }

    private void triggerAIThinking() {
        state = GameState.PLAYING;
        aiTurnCount++;

        // AI Ballistic adjustments
        Timer aiTimer = new Timer(1000, e -> {
            // Target is Player 1 at p1X
            double dist = p2X - p1X;

            // Simple targeting calculation
            // Base angle around 45 degrees
            p2Angle = 35 + random.nextInt(20);

            // Estimate power based on distance and wind resistance
            double targetPower = (dist * 0.12) - (wind * 2.0) + (p1Y - p2Y) * 0.05;
            
            // Apply error margins based on turn count (AI gets more accurate)
            double errorRange = Math.max(1, 20 - aiTurnCount * 4);
            p2Power = targetPower + (random.nextDouble() * errorRange - errorRange / 2.0);
            p2Power = Math.max(10, Math.min(100, p2Power));

            // Fire
            fireShell();
        });
        aiTimer.setRepeats(false);
        aiTimer.start();
    }

    private void handleKeyEvent(int keyCode, boolean pressed) {
        if (pressed) {
            if (state == GameState.LAUNCH) {
                if (keyCode == KeyEvent.VK_1) {
                    vsComputer = true;
                    startNewMatch();
                } else if (keyCode == KeyEvent.VK_2) {
                    vsComputer = false;
                    startNewMatch();
                }
            } else if (state == GameState.PLAYING) {
                if (vsComputer && activePlayer == 2) return; // Ignore input on AI turn

                if (keyCode == KeyEvent.VK_UP || keyCode == KeyEvent.VK_W) keyUp = true;
                if (keyCode == KeyEvent.VK_DOWN || keyCode == KeyEvent.VK_S) keyDown = true;
                if (keyCode == KeyEvent.VK_LEFT || keyCode == KeyEvent.VK_A) keyLeft = true;
                if (keyCode == KeyEvent.VK_RIGHT || keyCode == KeyEvent.VK_D) keyRight = true;

                if (keyCode == KeyEvent.VK_SPACE || keyCode == KeyEvent.VK_ENTER) {
                    fireShell();
                }
            } else if (state == GameState.ROUND_OVER) {
                if (keyCode == KeyEvent.VK_ENTER || keyCode == KeyEvent.VK_SPACE) {
                    startNewRound();
                }
            } else if (state == GameState.GAME_OVER) {
                if (keyCode == KeyEvent.VK_ENTER || keyCode == KeyEvent.VK_SPACE) {
                    state = GameState.LAUNCH;
                }
            }
        } else {
            if (keyCode == KeyEvent.VK_UP || keyCode == KeyEvent.VK_W) keyUp = false;
            if (keyCode == KeyEvent.VK_DOWN || keyCode == KeyEvent.VK_S) keyDown = false;
            if (keyCode == KeyEvent.VK_LEFT || keyCode == KeyEvent.VK_A) keyLeft = false;
            if (keyCode == KeyEvent.VK_RIGHT || keyCode == KeyEvent.VK_D) keyRight = false;
        }
    }

    private void startNewMatch() {
        p1Wins = 0;
        p2Wins = 0;
        startNewRound();
    }

    private void startNewRound() {
        generateNewTerrain();
        wind = -4.0 + random.nextDouble() * 8.0;
        activePlayer = 1;
        aiTurnCount = 0;
        trail.clear();
        state = GameState.PLAYING;
        playLevelStartSound();
    }

    private void fireShell() {
        state = GameState.SHELL_FLYING;
        trail.clear();

        double radAngle;
        double p;
        if (activePlayer == 1) {
            radAngle = Math.toRadians(p1Angle);
            p = p1Power * 0.15;
            shellX = p1X;
            shellY = p1Y - 8;
            shellVx = p * Math.cos(radAngle);
            shellVy = -p * Math.sin(radAngle);
        } else {
            radAngle = Math.toRadians(p2Angle);
            p = p2Power * 0.15;
            shellX = p2X;
            shellY = p2Y - 8;
            shellVx = -p * Math.cos(radAngle);
            shellVy = -p * Math.sin(radAngle);
        }

        playFireSound();
    }

    private void updatePhysics() {
        if (state == GameState.PLAYING) {
            // Apply adjustments
            if (activePlayer == 1) {
                if (keyUp) p1Angle = Math.min(90, p1Angle + 0.5);
                if (keyDown) p1Angle = Math.max(0, p1Angle - 0.5);
                if (keyLeft) p1Power = Math.max(10, p1Power - 0.5);
                if (keyRight) p1Power = Math.min(100, p1Power + 0.5);
            } else if (!vsComputer) {
                if (keyUp) p2Angle = Math.min(90, p2Angle + 0.5);
                if (keyDown) p2Angle = Math.max(0, p2Angle - 0.5);
                if (keyLeft) p2Power = Math.min(100, p2Power + 0.5); // Inverse left/right for P2
                if (keyRight) p2Power = Math.max(10, p2Power - 0.5);
            }
            playPanel.repaint();
        } else if (state == GameState.SHELL_FLYING) {
            // Ballistics steps
            for (int step = 0; step < 4; step++) {
                shellVx += wind * 0.003;
                shellVy += 0.04; // gravity

                shellX += shellVx;
                shellY += shellVy;

                trail.add(new Point((int) shellX, (int) shellY));

                // Bounding collision checks
                int ix = (int) shellX;
                if (ix < 0 || ix >= GAME_WIDTH || shellY >= GAME_HEIGHT) {
                    // Out of bounds
                    changeTurn();
                    return;
                }

                // Opponent hits check
                if (activePlayer == 1) {
                    if (Math.abs(shellX - p2X) < 10 && Math.abs(shellY - p2Y) < 10) {
                        triggerHit(2); // P2 hit
                        return;
                    }
                } else {
                    if (Math.abs(shellX - p1X) < 10 && Math.abs(shellY - p1Y) < 10) {
                        triggerHit(1); // P1 hit
                        return;
                    }
                }

                // Terrain collision
                if (shellY >= terrainY[ix]) {
                    triggerExplosion(ix, (int) shellY);
                    return;
                }
            }
            playPanel.repaint();
        }
    }

    private void triggerExplosion(int hx, int hy) {
        state = GameState.PLAYING;
        playExplosionSound();

        // Crater terrain
        int radius = 18;
        for (int x = Math.max(0, hx - radius); x < Math.min(GAME_WIDTH, hx + radius); x++) {
            double distance = Math.abs(x - hx);
            double depth = Math.sqrt(radius * radius - distance * distance);
            terrainY[x] = Math.min(GAME_HEIGHT - 20, (int) (terrainY[x] + depth * 0.8));
        }

        // Adjust player positions if terrain sinks under them
        p1Y = terrainY[p1X];
        p2Y = terrainY[p2X];

        // Did we commit suicide?
        if (activePlayer == 1 && Math.abs(hx - p1X) < 12) {
            triggerHit(1);
            return;
        }
        if (activePlayer == 2 && Math.abs(hx - p2X) < 12) {
            triggerHit(2);
            return;
        }

        changeTurn();
    }

    private void triggerHit(int victimPlayer) {
        playExplosionSound();
        if (victimPlayer == 1) {
            p2Wins++;
            winText = vsComputer ? "COMPUTER GEWINNT DIE RUNDE!" : "SPIELER 2 GEWINNT DIE RUNDE!";
        } else {
            p1Wins++;
            winText = "SPIELER 1 GEWINNT DIE RUNDE!";
        }

        if (p1Wins >= 3 || p2Wins >= 3) {
            state = GameState.GAME_OVER;
            winText = (p1Wins >= 3) ? "SPIELER 1 GEWINNT DAS MATCH!" : (vsComputer ? "COMPUTER GEWINNT DAS MATCH!" : "SPIELER 2 GEWINNT DAS MATCH!");
            checkAndPromptHighScore();
        } else {
            state = GameState.ROUND_OVER;
        }
        playSound(523, 300);
    }

    private void checkAndPromptHighScore() {
        int finalScore = p1Wins * 100 - p2Wins * 50;
        if (finalScore <= 0) return;

        HighScore hs = new HighScore("EuroArtilleryDuel");
        List<HighScore.ScoreEntry> scores = hs.getScores();
        boolean qualifies = false;
        if (scores.size() < 10) {
            qualifies = true;
        } else {
            if (finalScore > scores.get(9).score) {
                qualifies = true;
            }
        }

        if (qualifies) {
            SwingUtilities.invokeLater(() -> {
                String username = JOptionPane.showInputDialog(this,
                    "Glückwunsch! Neuer High Score: " + finalScore + " Punkte\nBitte Name eingeben:",
                    "Neuer High Score",
                    JOptionPane.PLAIN_MESSAGE
                );
                if (username != null && !username.trim().isEmpty()) {
                    try {
                        hs.setHighScore(finalScore, username.trim(), 0);
                        showHighScoresDialog();
                    } catch (IOException ex) {
                        JOptionPane.showMessageDialog(this, "Fehler beim Speichern des Highscores.", "Fehler", JOptionPane.ERROR_MESSAGE);
                    }
                }
            });
        }
    }

    private void showHighScoresDialog() {
        HighScore hs = new HighScore("EuroArtilleryDuel");
        List<HighScore.ScoreEntry> scores = hs.getScores();
        StringBuilder sb = new StringBuilder();
        sb.append("EuroArtilleryDuel Bestenliste (Top 10):\n\n");
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

    private void playFireSound() {
        if (soundRunning) return;
        soundRunning = true;
        new Thread(() -> {
            try {
                int sampleRate = 8000;
                byte[] buf = new byte[2500];
                Random rand = new Random();
                for (int i = 0; i < buf.length; i++) {
                    double volume = 1.0 - (double) i / buf.length;
                    buf[i] = (byte) ((rand.nextInt(60) - 30) * volume);
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
                byte[] buf = new byte[4500];
                Random rand = new Random();
                for (int i = 0; i < buf.length; i++) {
                    double volume = 1.0 - (double) i / buf.length;
                    buf[i] = (byte) ((rand.nextInt(90) - 45) * volume);
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
        playSound(659, 120);
    }

    // ── Panel Graphics Painting ─────────────────────────────────────────────

    private class ArtilleryPanel extends JPanel {

        ArtilleryPanel() {
            setBackground(new Color(135, 206, 250)); // Sky blue background
            setPreferredSize(new Dimension(GAME_WIDTH, GAME_HEIGHT));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (state == GameState.LAUNCH) {
                renderLaunchScreen(g2);
                g2.dispose();
                return;
            }

            // Draw Terrain
            g2.setColor(new Color(100, 70, 40)); // Brown mountains
            for (int x = 0; x < GAME_WIDTH; x++) {
                g2.drawLine(x, terrainY[x], x, GAME_HEIGHT);
            }
            g2.setColor(new Color(34, 139, 34)); // Green grass peak
            for (int x = 0; x < GAME_WIDTH; x++) {
                g2.drawLine(x, terrainY[x], x, terrainY[x] + 4);
            }

            // Draw Cannons
            // Player 1 (Red)
            g2.setColor(Color.RED);
            g2.fillRect(p1X - 6, p1Y - 6, 12, 6);
            double r1 = Math.toRadians(p1Angle);
            g2.setStroke(new BasicStroke(3));
            g2.drawLine(p1X, p1Y - 6, (int) (p1X + 12 * Math.cos(r1)), (int) (p1Y - 6 - 12 * Math.sin(r1)));

            // Player 2 (Blue)
            g2.setColor(Color.BLUE);
            g2.fillRect(p2X - 6, p2Y - 6, 12, 6);
            double r2 = Math.toRadians(p2Angle);
            g2.drawLine(p2X, p2Y - 6, (int) (p2X - 12 * Math.cos(r2)), (int) (p2Y - 6 - 12 * Math.sin(r2)));

            // Draw shell trail
            g2.setStroke(new BasicStroke(2));
            g2.setColor(Color.YELLOW);
            for (int i = 0; i < trail.size(); i++) {
                Point pt = trail.get(i);
                g2.fillRect(pt.x - 1, pt.y - 1, 2, 2);
            }

            // Draw Shell projectile
            if (state == GameState.SHELL_FLYING) {
                g2.setColor(Color.BLACK);
                g2.fillOval((int) shellX - 2, (int) shellY - 2, 4, 4);
            }

            // Draw HUD
            drawHUD(g2);

            // Overlay Screens
            if (state == GameState.ROUND_OVER) {
                g2.setColor(new Color(0, 0, 0, 160));
                g2.fillRect(40, GAME_HEIGHT / 2 - 50, GAME_WIDTH - 80, 100);
                g2.setColor(Color.GREEN);
                g2.drawRect(40, GAME_HEIGHT / 2 - 50, GAME_WIDTH - 80, 100);

                g2.setFont(new Font("Monospaced", Font.BOLD, 14));
                g2.setColor(Color.WHITE);
                drawCenteredString(g2, winText, GAME_HEIGHT / 2 - 10);
                g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
                g2.setColor(Color.YELLOW);
                drawCenteredString(g2, "Drücke [ENTER] für die nächste Runde", GAME_HEIGHT / 2 + 20);
            } else if (state == GameState.GAME_OVER) {
                g2.setColor(new Color(0, 0, 0, 200));
                g2.fillRect(40, GAME_HEIGHT / 2 - 60, GAME_WIDTH - 80, 120);
                g2.setColor(Color.RED);
                g2.drawRect(40, GAME_HEIGHT / 2 - 60, GAME_WIDTH - 80, 120);

                g2.setFont(new Font("Monospaced", Font.BOLD, 14));
                g2.setColor(Color.WHITE);
                drawCenteredString(g2, winText, GAME_HEIGHT / 2 - 20);
                g2.drawString("Match beendet!", GAME_WIDTH / 2 - 50, GAME_HEIGHT / 2 + 10);
                g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
                g2.setColor(Color.YELLOW);
                drawCenteredString(g2, "Drücke [ENTER] für das Hauptmenü", GAME_HEIGHT / 2 + 35);
            }

            g2.dispose();
        }

        private void renderLaunchScreen(Graphics2D g2) {
            g2.setColor(Color.BLACK);
            g2.fillRect(0, 0, GAME_WIDTH, GAME_HEIGHT);

            g2.setFont(new Font("Monospaced", Font.BOLD, 22));
            g2.setColor(Color.GREEN);
            drawCenteredString(g2, "EURO ARTILLERY DUEL", 80);

            g2.setFont(new Font("SansSerif", Font.BOLD, 14));
            g2.setColor(Color.WHITE);
            drawCenteredString(g2, "Wähle den Spielmodus:", 160);

            g2.setFont(new Font("Monospaced", Font.BOLD, 13));
            g2.setColor(Color.YELLOW);
            drawCenteredString(g2, "[1] SPIELER vs COMPUTER (KI)", 220);
            drawCenteredString(g2, "[2] SPIELER vs SPIELER", 250);

            g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g2.setColor(Color.GRAY);
            drawCenteredString(g2, "Steuerung: W/S = Winkel | A/D = Kraft | LEERTASTE = Feuern", 340);
        }

        private void drawHUD(Graphics2D g2) {
            // Background box for HUD
            g2.setColor(new Color(0, 0, 0, 120));
            g2.fillRect(0, 0, GAME_WIDTH, 45);

            g2.setFont(new Font("Monospaced", Font.BOLD, 11));
            // Player 1 stats
            g2.setColor(Color.RED);
            g2.drawString(String.format("P1: W:%02d K:%02d S:%d", (int) p1Angle, (int) p1Power, p1Wins), 10, 20);

            // Player 2 stats
            g2.setColor(Color.BLUE);
            String p2Label = vsComputer ? "COM" : "P2";
            g2.drawString(String.format("%s: W:%02d K:%02d S:%d", p2Label, (int) p2Angle, (int) p2Power, p2Wins), GAME_WIDTH - 150, 20);

            // Active player turn indicator
            g2.setFont(new Font("SansSerif", Font.BOLD, 12));
            g2.setColor(Color.YELLOW);
            String turnMsg = "";
            if (state == GameState.PLAYING) {
                if (activePlayer == 1) turnMsg = "<- SPIELER 1 IST AM ZUG";
                else turnMsg = vsComputer ? "COMPUTER RECHNET..." : "SPIELER 2 IST AM ZUG ->";
            }
            drawCenteredString(g2, turnMsg, 20);

            // Wind indicator
            g2.setFont(new Font("Monospaced", Font.BOLD, 11));
            g2.setColor(Color.WHITE);
            String windDir = wind < 0 ? "<< " : " >>";
            g2.drawString(String.format("WIND: %.1f %s", Math.abs(wind), windDir), GAME_WIDTH / 2 - 50, 38);
        }

        private void drawCenteredString(Graphics2D g2, String text, int y) {
            FontMetrics fm = g2.getFontMetrics();
            int x = (GAME_WIDTH - fm.stringWidth(text)) / 2;
            g2.drawString(text, x, y);
        }
    }
}

package com.datazuul.euroworks.games;

import com.datazuul.euroworks.apps.EuroAppFrame;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Random;

/**
 * Classic Windows-style Minesweeper game for EuroWorks.
 * Recreates the retro Win95 Minesweeper look, including:
 * - 3-digit LED displays for Mine Counter and Game Timer.
 * - Yellow smiley face indicator representing game state.
 * - Custom painted grid of cells with 3D bevels and authentic digit colors.
 * - First-click safety to prevent immediate detonation.
 */
public class EuroMines extends EuroAppFrame {

    // ── Difficulty Configurations ───────────────────────────────────────────
    private enum Difficulty {
        BEGINNER(9, 9, 10, "Beginner"),
        INTERMEDIATE(16, 16, 40, "Intermediate"),
        EXPERT(30, 16, 99, "Expert");

        final int cols, rows, mines;
        final String name;

        Difficulty(int cols, int rows, int mines, String name) {
            this.cols = cols;
            this.rows = rows;
            this.mines = mines;
            this.name = name;
        }
    }

    private Difficulty currentDifficulty = Difficulty.BEGINNER;

    // ── Palette ─────────────────────────────────────────────────────────────
    private static final Color SILVER   = new Color(192, 192, 192);
    private static final Color SHADOW   = new Color(128, 128, 128);
    private static final Color DARK     = new Color(64,  64,  64);
    private static final Color HILIGHT  = Color.WHITE;
    private static final Color LED_RED  = new Color(255, 0, 0);
    private static final Color LED_DARK = new Color(80, 0, 0);

    // Number colors from classic Minesweeper
    private static final Color[] NUM_COLORS = {
        Color.LIGHT_GRAY, // Dummy for 0
        new Color(0, 0, 255),     // 1: Blue
        new Color(0, 128, 0),     // 2: Green
        new Color(255, 0, 0),     // 3: Red
        new Color(0, 0, 128),     // 4: Dark Blue
        new Color(128, 0, 0),     // 5: Dark Red/Maroon
        new Color(0, 128, 128),   // 6: Turquoise
        Color.BLACK,              // 7: Black
        Color.GRAY                // 8: Gray
    };

    // ── Game State ──────────────────────────────────────────────────────────
    private Cell[][] grid;
    private int rows, cols, totalMines;
    private int flaggedCount;
    private boolean firstClick;
    private boolean gameOver;
    private boolean gameWon;

    // Timer and LED displays
    private int timeElapsed;
    private Timer gameTimer;

    // Components
    private JLabel lblMinesLED;
    private JLabel lblTimeLED;
    private JButton btnSmiley;
    private MinesGridPanel gridPanel;

    private static class Cell {
        boolean isMine = false;
        boolean isRevealed = false;
        boolean isFlagged = false;
        int neighbors = 0;
    }

    public EuroMines() {
        super("EuroMines (Minesweeper)");
        
        // Build difficulty selection menu
        setJMenuBar(buildMenuBar());

        // Setup Main Container Layout
        JPanel mainPanel = new JPanel(new BorderLayout(0, 6));
        mainPanel.setBackground(SILVER);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        // 1. Top Control Bar (Smiley, Timer, Mine Counter)
        JPanel controlBar = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                // Draw Win95-style sunken border around control bar
                drawSunken((Graphics2D) g, 0, 0, getWidth(), getHeight());
            }
        };
        controlBar.setBackground(SILVER);
        controlBar.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        // Left LED: Mine counter
        lblMinesLED = createLEDDisplay("000");
        controlBar.add(lblMinesLED, BorderLayout.WEST);

        // Center: Smiley button
        btnSmiley = new JButton("☺") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setColor(SILVER);
                g2.fillRect(0, 0, getWidth(), getHeight());
                
                boolean pressed = getModel().isPressed();
                if (pressed) {
                    drawSunken(g2, 0, 0, getWidth(), getHeight());
                } else {
                    drawRaised(g2, 0, 0, getWidth(), getHeight());
                }

                // Center coordinates
                int cx = getWidth() / 2;
                int cy = getHeight() / 2;
                if (pressed) {
                    cx += 1;
                    cy += 1;
                }

                // Draw yellow circle for smiley face background
                g2.setColor(new Color(255, 225, 0)); // retro yellow
                g2.fillOval(cx - 7, cy - 7, 14, 14);

                // Draw black UTF-8 smiley character on top
                g2.setFont(new Font("SansSerif", Font.PLAIN, 15));
                g2.setColor(Color.BLACK);
                FontMetrics fm = g2.getFontMetrics();
                int tx = cx - fm.stringWidth(getText()) / 2;
                int ty = cy + (fm.getAscent() - fm.getDescent()) / 2 - 1; // minor vertical offset correction
                g2.drawString(getText(), tx, ty);
                g2.dispose();
            }
        };
        btnSmiley.setPreferredSize(new Dimension(28, 28));
        btnSmiley.setFocusPainted(false);
        btnSmiley.setBorderPainted(false);
        btnSmiley.setContentAreaFilled(false);
        btnSmiley.addActionListener(e -> newGame());

        JPanel smileyWrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        smileyWrap.setOpaque(false);
        smileyWrap.add(btnSmiley);
        controlBar.add(smileyWrap, BorderLayout.CENTER);

        // Right LED: Timer
        lblTimeLED = createLEDDisplay("000");
        controlBar.add(lblTimeLED, BorderLayout.EAST);

        mainPanel.add(controlBar, BorderLayout.NORTH);

        // 2. Grid Panel
        gridPanel = new MinesGridPanel();
        mainPanel.add(gridPanel, BorderLayout.CENTER);

        setContentPane(mainPanel);

        // Initialize timer
        gameTimer = new Timer(1000, e -> {
            if (timeElapsed < 999) {
                timeElapsed++;
                lblTimeLED.setText(formatThreeDigits(timeElapsed));
            }
        });

        // Start beginner game by default
        setDifficulty(Difficulty.BEGINNER);
    }

    private JMenuBar buildMenuBar() {
        JMenuBar mb = new JMenuBar();
        JMenu gameMenu = new JMenu("Spiel");
        gameMenu.setFont(new Font("SansSerif", Font.PLAIN, 11));

        JMenuItem newGameItem = new JMenuItem("Neues Spiel (F2)");
        newGameItem.addActionListener(e -> newGame());
        gameMenu.add(newGameItem);
        gameMenu.addSeparator();

        for (Difficulty diff : Difficulty.values()) {
            JMenuItem diffItem = new JMenuItem(diff.name);
            diffItem.addActionListener(e -> setDifficulty(diff));
            gameMenu.add(diffItem);
        }

        gameMenu.addSeparator();
        JMenuItem exitItem = new JMenuItem("Beenden");
        exitItem.addActionListener(e -> dispose());
        gameMenu.add(exitItem);

        mb.add(gameMenu);
        return mb;
    }

    private void setDifficulty(Difficulty diff) {
        this.currentDifficulty = diff;
        this.cols = diff.cols;
        this.rows = diff.rows;
        this.totalMines = diff.mines;

        // Force layout manager to recalculate sizes and pack the window precisely
        newGame();
        pack();
    }

    private void newGame() {
        gameTimer.stop();
        timeElapsed = 0;
        flaggedCount = 0;
        firstClick = true;
        gameOver = false;
        gameWon = false;

        btnSmiley.setText("☺");
        lblTimeLED.setText("000");
        lblMinesLED.setText(formatThreeDigits(totalMines));

        // Create empty grid
        grid = new Cell[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                grid[r][c] = new Cell();
            }
        }

        gridPanel.revalidate();
        gridPanel.repaint();
    }

    // ── LED Widget Helper ───────────────────────────────────────────────────

    private JLabel createLEDDisplay(String startText) {
        JLabel led = new JLabel(startText, SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                // LED Digital look: black screen with 3D sunken border
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(Color.BLACK);
                g2.fillRect(0, 0, getWidth(), getHeight());
                drawSunken(g2, 0, 0, getWidth(), getHeight());

                // Paint dithered dark red background for segments
                g2.setFont(new Font("Monospaced", Font.BOLD, 18));
                FontMetrics fm = g2.getFontMetrics();
                int tx = (getWidth() - fm.stringWidth("888")) / 2;
                int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;

                g2.setColor(LED_DARK);
                g2.drawString("888", tx, ty);

                // Paint active red digits
                g2.setColor(LED_RED);
                g2.drawString(getText(), tx, ty);
                g2.dispose();
            }
        };
        led.setPreferredSize(new Dimension(46, 24));
        led.setOpaque(false);
        return led;
    }

    // ── Minesweeper Game Logic ──────────────────────────────────────────────

    private void placeMines(int firstCol, int firstRow) {
        Random rand = new Random();
        int minesPlaced = 0;

        while (minesPlaced < totalMines) {
            int r = rand.nextInt(rows);
            int c = rand.nextInt(cols);

            // Avoid putting a mine at the first clicked cell or its immediate neighbors (safe start)
            if (Math.abs(r - firstRow) <= 1 && Math.abs(c - firstCol) <= 1) {
                continue;
            }

            if (!grid[r][c].isMine) {
                grid[r][c].isMine = true;
                minesPlaced++;
            }
        }

        calculateNeighbors();
    }

    private void calculateNeighbors() {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (grid[r][c].isMine) continue;

                int mineCount = 0;
                for (int dr = -1; dr <= 1; dr++) {
                    for (int dc = -1; dc <= 1; dc++) {
                        int nr = r + dr;
                        int nc = c + dc;
                        if (nr >= 0 && nr < rows && nc >= 0 && nc < cols) {
                            if (grid[nr][nc].isMine) {
                                mineCount++;
                            }
                        }
                    }
                }
                grid[r][c].neighbors = mineCount;
            }
        }
    }

    private void revealCell(int c, int r) {
        if (c < 0 || c >= cols || r < 0 || r >= rows) return;
        Cell cell = grid[r][c];

        if (cell.isRevealed || cell.isFlagged) return;

        // First click safety
        if (firstClick) {
            firstClick = false;
            placeMines(c, r);
            gameTimer.start();
        }

        cell.isRevealed = true;

        if (cell.isMine) {
            detonate();
            return;
        }

        // Flood fill empty neighbors
        if (cell.neighbors == 0) {
            for (int dr = -1; dr <= 1; dr++) {
                for (int dc = -1; dc <= 1; dc++) {
                    revealCell(c + dc, r + dr);
                }
            }
        }

        checkVictoryConditions();
    }

    private void toggleFlag(int c, int r) {
        if (gameOver || gameWon || firstClick) return;
        Cell cell = grid[r][c];
        if (cell.isRevealed) return;

        cell.isFlagged = !cell.isFlagged;
        if (cell.isFlagged) {
            flaggedCount++;
        } else {
            flaggedCount--;
        }

        lblMinesLED.setText(formatThreeDigits(Math.max(0, totalMines - flaggedCount)));
        gridPanel.repaint();
    }

    private void detonate() {
        gameOver = true;
        gameTimer.stop();
        btnSmiley.setText("😵");

        // Reveal all mines
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                Cell cell = grid[r][c];
                if (cell.isMine && !cell.isFlagged) {
                    cell.isRevealed = true;
                } else if (!cell.isMine && cell.isFlagged) {
                    // Flagged cell that was NOT a mine (misflagged mark)
                    cell.isRevealed = true;
                }
            }
        }
        gridPanel.repaint();
    }

    private void checkVictoryConditions() {
        boolean won = true;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                Cell cell = grid[r][c];
                if (!cell.isMine && !cell.isRevealed) {
                    won = false;
                    break;
                }
            }
        }

        if (won) {
            gameWon = true;
            gameTimer.stop();
            btnSmiley.setText("😎");
            // Auto flag remaining mines
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    Cell cell = grid[r][c];
                    if (cell.isMine) {
                        cell.isFlagged = true;
                    }
                }
            }
            lblMinesLED.setText("000");
            gridPanel.repaint();
        }
    }

    // ── Grid Panel Custom Painting ──────────────────────────────────────────

    private class MinesGridPanel extends JPanel {

        MinesGridPanel() {
            setBackground(SILVER);

            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (gameOver || gameWon) return;
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        btnSmiley.setText("😮");
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (gameOver || gameWon) return;
                    btnSmiley.setText("☺");

                    int cellPixelSize = 18;
                    int col = e.getX() / cellPixelSize;
                    int row = e.getY() / cellPixelSize;

                    if (col >= 0 && col < cols && row >= 0 && row < rows) {
                        if (SwingUtilities.isLeftMouseButton(e)) {
                            revealCell(col, row);
                        } else if (SwingUtilities.isRightMouseButton(e)) {
                            toggleFlag(col, row);
                        }
                    }
                    repaint();
                }
            });
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(cols * 18, rows * 18);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);

            // Draw outer border around grid
            drawSunken(g2, 0, 0, getWidth(), getHeight());

            int cellSize = 18;

            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    Cell cell = grid[r][c];
                    int cx = c * cellSize;
                    int cy = r * cellSize;

                    if (cell.isRevealed) {
                        g2.setColor(SILVER);
                        g2.fillRect(cx, cy, cellSize, cellSize);
                        
                        // Sunken border for revealed cells
                        drawSunkenCellBorder(g2, cx, cy, cellSize);

                        if (cell.isMine) {
                            // Mine detonated cell is red, normal revealed mine cell is gray
                            g2.setColor(new Color(255, 80, 80));
                            g2.fillRect(cx + 1, cy + 1, cellSize - 2, cellSize - 2);
                            drawRetroMine(g2, cx, cy, cellSize);
                        } else if (cell.neighbors > 0) {
                            // Draw adjacent mines count number
                            g2.setFont(new Font("SansSerif", Font.BOLD, 12));
                            g2.setColor(NUM_COLORS[cell.neighbors]);
                            FontMetrics fm = g2.getFontMetrics();
                            String txt = String.valueOf(cell.neighbors);
                            int tx = cx + (cellSize - fm.stringWidth(txt)) / 2;
                            int ty = cy + (cellSize + fm.getAscent() - fm.getDescent()) / 2;
                            g2.drawString(txt, tx, ty);
                        }
                    } else {
                        // Unrevealed cells are 3D raised panels
                        g2.setColor(SILVER);
                        g2.fillRect(cx, cy, cellSize, cellSize);
                        drawRaisedCellBorder(g2, cx, cy, cellSize);

                        if (cell.isFlagged) {
                            drawRetroFlag(g2, cx, cy, cellSize);
                        }
                    }
                }
            }

            g2.dispose();
        }
    }

    // ── Vector Graphics Retro Painters ──────────────────────────────────────

    private void drawRetroMine(Graphics2D g2, int x, int y, int size) {
        g2.setColor(Color.BLACK);
        int cx = x + size / 2;
        int cy = y + size / 2;

        // Draw spiked circle mine
        g2.fillOval(cx - 4, cy - 4, 8, 8);
        
        // Horizontal/vertical spikes
        g2.drawLine(cx - 6, cy, cx + 6, cy);
        g2.drawLine(cx, cy - 6, cx, cy + 6);
        
        // Diagonal spikes
        g2.drawLine(cx - 5, cy - 5, cx + 5, cy + 5);
        g2.drawLine(cx - 5, cy + 5, cx + 5, cy - 5);
        
        // Small white glare pixel
        g2.setColor(Color.WHITE);
        g2.fillRect(cx - 2, cy - 2, 2, 2);
    }

    private void drawRetroFlag(Graphics2D g2, int x, int y, int size) {
        int cx = x + size / 2;
        int cy = y + size / 2;

        // Flag pole
        g2.setColor(Color.BLACK);
        g2.drawLine(cx - 1, cy - 4, cx - 1, cy + 4);
        g2.drawLine(cx - 3, cy + 4, cx + 1, cy + 4); // flag stand

        // Red flag cloth
        g2.setColor(Color.RED);
        int[] xPoints = {cx - 2, cx - 2, cx + 3};
        int[] yPoints = {cy - 4, cy, cy - 2};
        g2.fillPolygon(xPoints, yPoints, 3);
    }

    private static void drawRaisedCellBorder(Graphics2D g, int x, int y, int size) {
        g.setColor(HILIGHT);
        g.drawLine(x, y, x + size - 1, y);
        g.drawLine(x, y, x, y + size - 1);
        g.setColor(SHADOW);
        g.drawLine(x + 1, y + size - 1, x + size - 1, y + size - 1);
        g.drawLine(x + size - 1, y + 1, x + size - 1, y + size - 1);
    }

    private static void drawSunkenCellBorder(Graphics2D g, int x, int y, int size) {
        g.setColor(SHADOW);
        g.drawLine(x, y, x + size - 1, y);
        g.drawLine(x, y, x, y + size - 1);
    }

    // ── 3D paint helpers ──────────────────────────────────────────────────────

    private static void drawRaised(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(HILIGHT);
        g.drawLine(x, y, x + w - 2, y);
        g.drawLine(x, y, x, y + h - 2);
        g.setColor(DARK);
        g.drawLine(x, y + h - 1, x + w - 1, y + h - 1);
        g.drawLine(x + w - 1, y, x + w - 1, y + h - 1);
        g.setColor(SILVER);
        g.drawLine(x + 1, y + 1, x + w - 3, y + 1);
        g.drawLine(x + 1, y + 1, x + 1, y + h - 3);
        g.setColor(SHADOW);
        g.drawLine(x + 1, y + h - 2, x + w - 2, y + h - 2);
        g.drawLine(x + w - 2, y + 1, x + w - 2, y + h - 2);
    }

    private static void drawSunken(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(SHADOW);
        g.drawLine(x, y, x + w - 2, y);
        g.drawLine(x, y, x, y + h - 2);
        g.setColor(DARK);
        g.drawLine(x + 1, y + 1, x + w - 3, y + 1);
        g.drawLine(x + 1, y + 1, x + 1, y + h - 3);
        g.setColor(HILIGHT);
        g.drawLine(x, y + h - 1, x + w - 1, y + h - 1);
        g.drawLine(x + w - 1, y, x + w - 1, y + h - 1);
        g.setColor(SILVER);
        g.drawLine(x + 1, y + h - 2, x + w - 2, y + h - 2);
        g.drawLine(x + w - 2, y + 1, x + w - 2, y + h - 2);
    }

    // ── Utilities ───────────────────────────────────────────────────────────

    private static String formatThreeDigits(int val) {
        if (val < 0) return "000";
        if (val > 999) return "999";
        return String.format("%03d", val);
    }
}

package com.datazuul.euroworks.screensavers;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * First-person 3D raycaster maze screensaver, similar to the classic Windows 95 3D Maze.
 *
 * Design goals:
 * - Wall thickness = 1 cell, corridor width = 1 cell (no thick walls)
 * - Multiple corridor branches visible in the distance via proper perspective
 * - Distance fog darkens far walls for depth
 * - Checkerboard floor gives strong depth cues (like the original)
 * - Camera moves continuously; random turn chosen at each cell boundary
 */
public class EuroMazeCanvas extends JPanel implements ScreensaverCanvas {

    // ── map constants ───────────────────────────────────────────────────────
    private static final int MAP_W = 31;   // must be odd
    private static final int MAP_H = 31;
    private final int[][] map = new int[MAP_W][MAP_H];

    // ── rendering constants ─────────────────────────────────────────────────
    /** Horizontal FOV factor: 0.66 ≈ 66° at 90° camera */
    private static final double PLANE = 0.66;
    private static final int    TEX_SIZE = 64;

    // ── camera state ────────────────────────────────────────────────────────
    private double posX, posY;         // world position (cell units, fractional)
    private double dirX, dirY;         // unit direction vector
    private double planeX, planeY;     // camera plane (perpendicular to dir)

    /** Current heading as integer: 0=E 1=S 2=W 3=N */
    private int heading;

    // ── navigation FSM ──────────────────────────────────────────────────────
    private enum NavState { MOVING, TURNING }
    private NavState state = NavState.MOVING;

    /**
     * Explicit start/target cell centres for wall-safe interpolation.
     * posX/posY always lerps between (startCellX+0.5, startCellY+0.5)
     * and (targetCellX+0.5, targetCellY+0.5), so it can NEVER enter a wall.
     */
    private int startCellX, startCellY;
    private int targetCellX, targetCellY;

    /** Fractional progress [0,1] from startCell to targetCell */
    private double moveT;
    private static final double MOVE_STEP = 0.05; // fraction per frame

    /** Set to true once we have chosen the NEXT direction after targetCell */
    private boolean nextChosen = false;
    private int nextHeading;

    /** Turning: angle interpolation (happens while camera keeps gliding forward) */
    private double startAngle, targetAngle;
    private int    turnFrame;
    private static final int TURN_FRAMES = 16;

    // ── precomputed texture ─────────────────────────────────────────────────
    /** Brick texture cached as flat ARGB int[TEX_SIZE * TEX_SIZE]. */
    private int[] texPixels;

    // ── off-screen buffer ───────────────────────────────────────────────────
    private BufferedImage buffer;
    /** Direct pixel array of buffer for zero-overhead writes. */
    private int[] pixels;

    private final Random rand = new Random();
    private final Timer  loopTimer;
    private int speedMs = 40;

    // ── constructor ─────────────────────────────────────────────────────────

    public EuroMazeCanvas() {
        setBackground(Color.BLACK);
        buildTexture();
        resetMaze();
        loopTimer = new Timer(speedMs, e -> tick());
    }

    // ── ScreensaverCanvas ───────────────────────────────────────────────────

    @Override public void startAnimation() { loopTimer.start(); }
    @Override public void stopAnimation()  { loopTimer.stop();  }
    @Override public void setSpeedMs(int ms) {
        speedMs = ms;
        loopTimer.setDelay(ms);
    }

    // ── public API ──────────────────────────────────────────────────────────

    public void resetMaze() {
        generateMaze();
        placeCamera();
        buffer = null;
        repaint();
    }

    // ── maze generation (recursive backtracker) ─────────────────────────────

    private void generateMaze() {
        // Fill with walls
        for (int x = 0; x < MAP_W; x++)
            for (int y = 0; y < MAP_H; y++)
                map[x][y] = 1;
        // Carve corridors — step by 2 so wall thickness stays = 1
        carve(1, 1);
    }

    private void carve(int cx, int cy) {
        map[cx][cy] = 0;
        List<Integer> dirs = new ArrayList<>(List.of(0, 1, 2, 3));
        Collections.shuffle(dirs, rand);
        // delta for 4 cardinal directions (E, S, W, N) — step 2 to hop over the wall cell
        int[] dx = {2, 0, -2, 0};
        int[] dy = {0, 2, 0, -2};
        for (int d : dirs) {
            int nx = cx + dx[d];
            int ny = cy + dy[d];
            if (nx > 0 && nx < MAP_W - 1 && ny > 0 && ny < MAP_H - 1 && map[nx][ny] == 1) {
                map[cx + dx[d] / 2][cy + dy[d] / 2] = 0; // knock out wall in between
                carve(nx, ny);
            }
        }
    }

    // ── camera placement ────────────────────────────────────────────────────

    private void placeCamera() {
        startCellX  = 1; startCellY  = 1;
        targetCellX = 1; targetCellY = 1;
        posX = 1.5; posY = 1.5;
        heading = 0; // East
        applyHeading(heading);
        state     = NavState.MOVING;
        moveT     = 0.0;
        nextChosen = false;
        // Immediately pick a first move so the camera doesn't idle
        advanceTarget();
    }

    private void applyHeading(int h) {
        //  0=E  1=S  2=W  3=N
        double[] dirXs  = { 1,  0, -1,  0};
        double[] dirYs  = { 0,  1,  0, -1};
        double[] plXs   = { 0, -PLANE, 0,  PLANE};
        double[] plYs   = { PLANE,  0, -PLANE, 0};
        dirX   = dirXs [h];
        dirY   = dirYs [h];
        planeX = plXs  [h];
        planeY = plYs  [h];
    }

    // ── texture ─────────────────────────────────────────────────────────────

    private void buildTexture() {
        final int MORTAR = 2;
        final int BRICK_H = TEX_SIZE / 4;
        final int BRICK_W = TEX_SIZE / 2;

        final int cMortar = rgb(60, 50, 40);
        final int cBrick1 = rgb(160, 60, 30);
        final int cBrick2 = rgb(130, 40, 20);

        texPixels = new int[TEX_SIZE * TEX_SIZE];
        for (int ty = 0; ty < TEX_SIZE; ty++) {
            int row = ty / BRICK_H;
            int yInRow = ty % BRICK_H;
            int offset = (row % 2 == 0) ? 0 : BRICK_W / 2;
            for (int tx = 0; tx < TEX_SIZE; tx++) {
                int xInRow = (tx + offset) % TEX_SIZE;
                int xInBrick = xInRow % BRICK_W;

                if (yInRow < MORTAR || xInBrick < MORTAR) {
                    texPixels[ty * TEX_SIZE + tx] = cMortar;
                } else {
                    texPixels[ty * TEX_SIZE + tx] = (row % 2 == 0) ? cBrick1 : cBrick2;
                }
            }
        }
    }

    /** Pack r,g,b into 0xRRGGBB. */
    private static int rgb(int r, int g, int b) { return (r << 16) | (g << 8) | b; }

    // ── game loop ───────────────────────────────────────────────────────────

    private void tick() {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        if (buffer == null || buffer.getWidth() != w || buffer.getHeight() != h) {
            buffer = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            pixels = ((DataBufferInt) buffer.getRaster().getDataBuffer()).getData();
        }

        navigate();
        renderFrame(w, h);
        repaint();
    }

    // ── navigation FSM ──────────────────────────────────────────────────────

    private void navigate() {
        // ── TURNING: rotate in place while gliding ───────────────────────────
        if (state == NavState.TURNING) {
            turnFrame++;
            double t = Math.min(1.0, (double) turnFrame / TURN_FRAMES);
            t = t * t * (3 - 2 * t); // smooth-step
            double angle = startAngle + (targetAngle - startAngle) * t;
            dirX   = Math.cos(angle); dirY   = Math.sin(angle);
            planeX = -dirY * PLANE;   planeY =  dirX * PLANE;

            if (turnFrame >= TURN_FRAMES) {
                heading = nextHeading;
                applyHeading(heading);
                state = NavState.MOVING;
                // heading has changed; pick next target in the new direction
                nextChosen = false;
            }
        }

        // ── MOVING: advance along startCell → targetCell ─────────────────────
        moveT = Math.min(1.0, moveT + MOVE_STEP);

        // Safe interpolation – always between two known-open cell centres
        posX = (startCellX + 0.5) + (targetCellX - startCellX) * moveT;
        posY = (startCellY + 0.5) + (targetCellY - startCellY) * moveT;

        // At midpoint: look ahead and decide the direction AFTER targetCell
        if (!nextChosen && moveT >= 0.5) {
            chooseNextAfterTarget();
            nextChosen = true;
        }

        // Arrived at targetCell centre
        if (moveT >= 1.0) {
            startCellX = targetCellX;
            startCellY = targetCellY;
            posX = startCellX + 0.5;
            posY = startCellY + 0.5;
            moveT = 0.0;
            nextChosen = false;
            advanceTarget(); // apply the pre-chosen next direction
        }
    }

    /**
     * Look one step ahead from targetCell (in direction nextHeading) and
     * commit the choice.  If a turn is required, start its animation now so
     * it finishes before we reach the wall.
     */
    private void advanceTarget() {
        if (nextHeading == heading) {
            // Straight: just extend targetCell by one step
        } else {
            // Turn needed: start rotation animation
            state = NavState.TURNING;
            turnFrame = 0;
            startAngle  = heading * (Math.PI / 2.0);
            targetAngle = nextHeading * (Math.PI / 2.0);
            double diff = targetAngle - startAngle;
            if (diff >  Math.PI) diff -= 2 * Math.PI;
            if (diff < -Math.PI) diff += 2 * Math.PI;
            targetAngle = startAngle + diff;
        }
        // Commit heading choice and step to new target
        heading = nextHeading;
        int[] dx = {1, 0, -1, 0};
        int[] dy = {0, 1, 0, -1};
        targetCellX = startCellX + dx[heading];
        targetCellY = startCellY + dy[heading];
    }

    /** Scan available exits from targetCell and pick a random one. */
    private void chooseNextAfterTarget() {
        int cx = targetCellX;
        int cy = targetCellY;
        int[] dx = {1, 0, -1, 0};
        int[] dy = {0, 1, 0, -1};

        int backward = (heading + 2) % 4;
        List<Integer> available = new ArrayList<>();
        for (int d = 0; d < 4; d++) {
            if (d == backward) continue;
            int tx = cx + dx[d];
            int ty = cy + dy[d];
            if (tx >= 0 && tx < MAP_W && ty >= 0 && ty < MAP_H && map[tx][ty] == 0) {
                available.add(d);
            }
        }

        nextHeading = available.isEmpty()
                ? backward
                : available.get(rand.nextInt(available.size()));
    }

    // ── raycaster ───────────────────────────────────────────────────────────

    private void renderFrame(int W, int H) {
        int halfH = H / 2;
        // Direct pixel array – no per-pixel JNI overhead
        int[] px = pixels;

        // ── 1. Ceiling (gradient, pre-computed per scanline) ───────────────────
        for (int y = 0; y < halfH; y++) {
            int br   = 40 + 80 * y / halfH;
            int ceil = (br << 16) | (br << 8) | (br + 30); // slight blue tint
            int base = y * W;
            for (int x = 0; x < W; x++) px[base + x] = ceil;
        }

        // ── 2. Floor (checkerboard, direct array) ─────────────────────────
        for (int y = halfH; y < H; y++) {
            int dy = y - halfH;
            // Avoid division by zero at the horizon row
            double rowDist = (dy == 0) ? 1e10 : (double) halfH / dy;

            double floorX = posX + (dirX - planeX) * rowDist;
            double floorY = posY + (dirY - planeY) * rowDist;
            double stepX  = 2.0 * planeX * rowDist / W;
            double stepY  = 2.0 * planeY * rowDist / W;

            // Precompute fade for this row
            double fade  = rowDist / 9.0;  // 0 = close, 1+ = far
            int darkCol  = (int) Math.max(10,  30  - fade * 20);
            int lightCol = (int) Math.max(30,  100 - fade * 70);

            int base = y * W;
            for (int x = 0; x < W; x++) {
                int cX = (int) floorX;
                int cY = (int) floorY;
                int col = ((cX ^ cY) & 1) == 0 ? lightCol : darkCol;
                px[base + x] = (col << 16) | (col << 8) | col;
                floorX += stepX;
                floorY += stepY;
            }
        }

        // ── 3. Walls (DDA raycaster, texture from int[]) ──────────────────
        for (int x = 0; x < W; x++) {
            double cameraX = 2.0 * x / W - 1.0;
            double rDirX   = dirX + planeX * cameraX;
            double rDirY   = dirY + planeY * cameraX;

            int mapX = (int) posX;
            int mapY = (int) posY;

            double dDX = (rDirX == 0) ? 1e30 : Math.abs(1.0 / rDirX);
            double dDY = (rDirY == 0) ? 1e30 : Math.abs(1.0 / rDirY);

            double sideX, sideY;
            int stepX, stepY;

            if (rDirX < 0) { stepX = -1; sideX = (posX - mapX) * dDX; }
            else           { stepX =  1; sideX = (mapX + 1.0 - posX) * dDX; }
            if (rDirY < 0) { stepY = -1; sideY = (posY - mapY) * dDY; }
            else           { stepY =  1; sideY = (mapY + 1.0 - posY) * dDY; }

            int side = 0;
            while (true) {
                if (sideX < sideY) { sideX += dDX; mapX += stepX; side = 0; }
                else               { sideY += dDY; mapY += stepY; side = 1; }
                if (mapX < 0 || mapX >= MAP_W || mapY < 0 || mapY >= MAP_H) break;
                if (map[mapX][mapY] == 1) break;
            }

            double perpDist = (side == 0)
                    ? (mapX - posX + (1 - stepX) * 0.5) / rDirX
                    : (mapY - posY + (1 - stepY) * 0.5) / rDirY;
            if (perpDist < 0.001) perpDist = 0.001;

            int lineH   = (int) (H / perpDist);
            int drawTop = Math.max(0,     H / 2 - lineH / 2);
            int drawBot = Math.min(H - 1, H / 2 + lineH / 2);

            // Wall texture X
            double wallX = (side == 0)
                    ? posY + perpDist * rDirY
                    : posX + perpDist * rDirX;
            wallX -= Math.floor(wallX);
            int texX = (int) (wallX * TEX_SIZE);
            if ((side == 0 && rDirX > 0) || (side == 1 && rDirY < 0))
                texX = TEX_SIZE - 1 - texX;

            texX = Math.min(texX, TEX_SIZE - 1);

            // Combined brightness: fog + N/S shade (integer arithmetic, no floats)
            int fogScale = Math.max(0, (int) (256 - perpDist * 256 / 12));
            if (side == 1) fogScale = fogScale * 180 >> 8; // N/S ~70%

            for (int y = drawTop; y <= drawBot; y++) {
                int d    = y * 2 - H + lineH;
                int texY = (d < 0) ? 0 : (d >= 2 * lineH) ? TEX_SIZE - 1
                         : d * TEX_SIZE / (2 * lineH);

                int texCol = texPixels[texY * TEX_SIZE + texX];
                int r = ((texCol >> 16) & 0xFF) * fogScale >> 8;
                int g = ((texCol >>  8) & 0xFF) * fogScale >> 8;
                int b = ( texCol        & 0xFF) * fogScale >> 8;
                pixels[y * W + x] = (r << 16) | (g << 8) | b;
            }
        }
    }

    // ── Swing ───────────────────────────────────────────────────────────────

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (buffer != null) g.drawImage(buffer, 0, 0, null);
    }
}

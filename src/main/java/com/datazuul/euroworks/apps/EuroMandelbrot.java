package com.datazuul.euroworks.apps;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;

public class EuroMandelbrot extends EuroAppFrame {

    private final MandelbrotCanvas canvas;
    private final JLabel statusLabel;
    private int maxIterations = 256;
    private String colorScheme = "Original";

    private boolean isFullscreen = false;
    private JFrame fullscreenFrame = null;
    private Container originalContainer = null;

    public EuroMandelbrot() {
        super("EuroMandelbrot (Fractal Generator)");
        setSize(500, 400);

        // Build main content layout
        JPanel contentPane = new JPanel(new BorderLayout());

        // Setup canvas
        canvas = new MandelbrotCanvas();
        contentPane.add(canvas, BorderLayout.CENTER);

        // Setup status bar
        statusLabel = new JLabel("Berechnung bereit.");
        statusLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(4, 6, 4, 6)
        ));
        statusLabel.setFont(new Font("Courier New", Font.PLAIN, 10));
        contentPane.add(statusLabel, BorderLayout.SOUTH);

        setContentPane(contentPane);

        // Setup menu bar
        setJMenuBar(createAppMenuBar());
    }

    private JMenuBar createAppMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        // 1. Datei Menu
        JMenu dateiMenu = new JMenu("Datei");
        JMenuItem mReset = new JMenuItem("Zurücksetzen");
        mReset.addActionListener(e -> canvas.resetZoom());
        JMenuItem mRecalc = new JMenuItem("Neu berechnen");
        mRecalc.addActionListener(e -> canvas.startCalculation());
        JMenuItem mClose = new JMenuItem("Schließen");
        mClose.addActionListener(e -> dispose());

        dateiMenu.add(mReset);
        dateiMenu.add(mRecalc);
        JMenuItem mFullscreen = new JMenuItem("Vollbildmodus");
        mFullscreen.addActionListener(e -> {
            if (isFullscreen) {
                leaveFullscreen();
            } else {
                enterFullscreen();
            }
        });
        dateiMenu.add(mFullscreen);
        dateiMenu.addSeparator();
        dateiMenu.add(mClose);

        // 2. Einstellungen Menu
        JMenu settingsMenu = new JMenu("Einstellungen");
        
        // Iterations Submenu
        JMenu iterMenu = new JMenu("Iterationen (Max)");
        ButtonGroup iterGroup = new ButtonGroup();
        int[] iters = {64, 128, 256, 512, 1024};
        for (int it : iters) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(String.valueOf(it), it == maxIterations);
            item.addActionListener(e -> {
                maxIterations = it;
                canvas.startCalculation();
            });
            iterGroup.add(item);
            iterMenu.add(item);
        }
        settingsMenu.add(iterMenu);

        // Color Scheme Submenu
        JMenu colorMenu = new JMenu("Farbschema");
        ButtonGroup colorGroup = new ButtonGroup();
        String[] schemes = {"Original", "Regenbogen", "Eis-Blau", "Feuer", "Schwarz/Weiß"};
        for (String sc : schemes) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(sc, sc.equals(colorScheme));
            item.addActionListener(e -> {
                colorScheme = sc;
                canvas.startCalculation();
            });
            colorGroup.add(item);
            colorMenu.add(item);
        }
        settingsMenu.add(colorMenu);

        menuBar.add(dateiMenu);
        menuBar.add(settingsMenu);
        return menuBar;
    }

    private void enterFullscreen() {
        if (isFullscreen) return;
        isFullscreen = true;

        originalContainer = canvas.getParent();
        if (originalContainer != null) {
            originalContainer.remove(canvas);
        }

        fullscreenFrame = new JFrame();
        fullscreenFrame.setUndecorated(true);
        fullscreenFrame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        fullscreenFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        fullscreenFrame.getContentPane().setLayout(new BorderLayout());
        fullscreenFrame.getContentPane().add(canvas, BorderLayout.CENTER);

        // Setup ESC key close binding
        fullscreenFrame.getRootPane().registerKeyboardAction(e -> leaveFullscreen(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        fullscreenFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                if (isFullscreen) {
                    leaveFullscreen();
                }
            }
        });

        fullscreenFrame.setVisible(true);
        canvas.revalidate();
        canvas.repaint();
    }

    private void leaveFullscreen() {
        if (!isFullscreen) return;
        isFullscreen = false;

        if (fullscreenFrame != null) {
            fullscreenFrame.getContentPane().remove(canvas);
            fullscreenFrame.dispose();
            fullscreenFrame = null;
        }

        if (originalContainer != null) {
            originalContainer.add(canvas, BorderLayout.CENTER);
            originalContainer.revalidate();
            originalContainer.repaint();
        }
    }

    /**
     * Custom JPanel that calculates and renders the Mandelbrot set.
     */
    private class MandelbrotCanvas extends JPanel {
        // Complex coordinate boundaries
        private double xMin = -2.0;
        private double xMax = 0.5;
        private double yMin = -1.25;
        private double yMax = 1.25;

        // Mouse drag variables
        private Point pressPoint = null;
        private Point dragPoint = null;

        // Image holding calculated pixels
        private BufferedImage image = null;
        private Thread calcThread = null;

        public MandelbrotCanvas() {
            setBackground(Color.BLACK);
            setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));

            // Right-click popup menu
            JPopupMenu popupMenu = new JPopupMenu();
            JMenuItem mItemFullscreen = new JMenuItem("Vollbild verlassen");
            mItemFullscreen.addActionListener(e -> {
                if (isFullscreen) {
                    leaveFullscreen();
                } else {
                    enterFullscreen();
                }
            });
            JMenuItem mItemReset = new JMenuItem("Zurücksetzen");
            mItemReset.addActionListener(e -> resetZoom());
            JMenuItem mItemRecalc = new JMenuItem("Neu berechnen");
            mItemRecalc.addActionListener(e -> startCalculation());

            popupMenu.add(mItemFullscreen);
            popupMenu.add(mItemReset);
            popupMenu.add(mItemRecalc);

            // Mouse zoom and popup bindings
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        pressPoint = e.getPoint();
                        dragPoint = e.getPoint();
                    } else if (e.isPopupTrigger()) {
                        showPopup(e);
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e) && pressPoint != null && dragPoint != null) {
                        int w = Math.abs(pressPoint.x - dragPoint.x);
                        int h = Math.abs(pressPoint.y - dragPoint.y);

                        // If drag is large enough, perform zoom
                        if (w > 4 && h > 4) {
                            double oldWidth = xMax - xMin;
                            double oldHeight = yMax - yMin;

                            double newXMin = xMin + (double) Math.min(pressPoint.x, dragPoint.x) / getWidth() * oldWidth;
                            double newXMax = xMin + (double) Math.max(pressPoint.x, dragPoint.x) / getWidth() * oldWidth;
                            double newYMax = yMax - (double) Math.min(pressPoint.y, dragPoint.y) / getHeight() * oldHeight;
                            double newYMin = yMax - (double) Math.max(pressPoint.y, dragPoint.y) / getHeight() * oldHeight;

                            xMin = newXMin;
                            xMax = newXMax;
                            yMin = newYMin;
                            yMax = newYMax;

                            adjustAspectRatio();
                            startCalculation();
                        }
                        pressPoint = null;
                        dragPoint = null;
                        repaint();
                    } else if (e.isPopupTrigger()) {
                        showPopup(e);
                    }
                }

                private void showPopup(MouseEvent e) {
                    mItemFullscreen.setText(isFullscreen ? "Vollbild verlassen" : "Vollbildmodus");
                    popupMenu.show(e.getComponent(), e.getX(), e.getY());
                }
            });

            addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseDragged(MouseEvent e) {
                    if (pressPoint != null) {
                        dragPoint = e.getPoint();
                        repaint();
                    }
                }
            });

            addMouseWheelListener(new MouseWheelListener() {
                @Override
                public void mouseWheelMoved(MouseWheelEvent e) {
                    double factor = (e.getWheelRotation() < 0) ? 0.8 : 1.25;
                    Point p = e.getPoint();
                    
                    double cx = xMin + (double) p.x / getWidth() * (xMax - xMin);
                    double cy = yMax - (double) p.y / getHeight() * (yMax - yMin);
                    
                    xMin = cx - (cx - xMin) * factor;
                    xMax = cx + (xMax - cx) * factor;
                    yMin = cy - (cy - yMin) * factor;
                    yMax = cy + (yMax - cy) * factor;
                    
                    adjustAspectRatio();
                    startCalculation();
                }
            });

            // Resizing listener at the Canvas component level
            addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    handleResize();
                }
            });
        }

        public void resetZoom() {
            xMin = -2.0;
            xMax = 0.5;
            yMin = -1.25;
            yMax = 1.25;
            adjustAspectRatio();
            startCalculation();
        }

        /**
         * Adjusts the complex bounds to match the canvas aspect ratio, preventing squishing of the fractal shape.
         */
        private void adjustAspectRatio() {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;

            double complexWidth = xMax - xMin;
            double complexHeight = yMax - yMin;
            double screenAspect = (double) w / h;
            double complexAspect = complexWidth / complexHeight;

            if (screenAspect > complexAspect) {
                // Screen is wider than complex box: expand complex box width symmetrically
                double targetWidth = complexHeight * screenAspect;
                double diff = targetWidth - complexWidth;
                xMin -= diff / 2.0;
                xMax += diff / 2.0;
            } else {
                // Screen is taller than complex box: expand complex box height symmetrically
                double targetHeight = complexWidth / screenAspect;
                double diff = targetHeight - complexHeight;
                yMin -= diff / 2.0;
                yMax += diff / 2.0;
            }
        }

        public void handleResize() {
            adjustAspectRatio();
            startCalculation();
        }

        /**
         * Triggers the computation on a background worker thread.
         */
        public synchronized void startCalculation() {
            // Terminate existing background threads
            if (calcThread != null && calcThread.isAlive()) {
                calcThread.interrupt();
            }

            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;

            calcThread = new Thread(() -> calculateFractal(w, h));
            calcThread.start();
        }

        /**
         * Implements multi-pass progressive refinement drawing.
         * Render sequence: 16x16 -> 8x8 -> 4x4 -> 2x2 -> 1x1 blocks.
         */
        private void calculateFractal(int width, int height) {
            BufferedImage localImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            int[] blockSizes = {16, 8, 4, 2, 1};

            for (int pass = 0; pass < blockSizes.length; pass++) {
                int blockSize = blockSizes[pass];
                updateStatus("Rechne Pass " + (pass + 1) + "/5 (Blockgröße: " + blockSize + ")...");

                for (int y = 0; y < height; y += blockSize) {
                    if (Thread.currentThread().isInterrupted()) {
                        return;
                    }

                    for (int x = 0; x < width; x += blockSize) {
                        // Map screen coords to complex plane
                        double c_re = xMin + (double) x / width * (xMax - xMin);
                        double c_im = yMax - (double) y / height * (yMax - yMin);

                        int iter = computeMandelbrot(c_re, c_im);
                        int rgb = getPixelColor(iter);

                        // Fill block size box on local image
                        for (int dy = 0; dy < blockSize && y + dy < height; dy++) {
                            for (int dx = 0; dx < blockSize && x + dx < width; dx++) {
                                localImage.setRGB(x + dx, y + dy, rgb);
                            }
                        }
                    }
                    // Trigger progressive row refresh
                    if (y % 4 == 0) {
                        synchronized (this) {
                            this.image = localImage;
                        }
                        repaint();
                    }
                }
                synchronized (this) {
                    this.image = localImage;
                }
                repaint();
            }

            updateStatus("Berechnung abgeschlossen.");
        }

        private int computeMandelbrot(double cr, double ci) {
            double zr = 0.0;
            double zi = 0.0;
            int iter = 0;
            while (zr * zr + zi * zi <= 4.0 && iter < maxIterations) {
                double temp = zr * zr - zi * zi + cr;
                zi = 2.0 * zr * zi + ci;
                zr = temp;
                iter++;
            }
            return iter;
        }

        private int getPixelColor(int iter) {
            if (iter == maxIterations) {
                return Color.WHITE.getRGB(); // Set body is white
            }

            switch (colorScheme) {
                case "Regenbogen":
                    float hue = (float) iter / maxIterations;
                    return Color.getHSBColor(hue, 0.85f, 0.9f).getRGB();

                case "Eis-Blau":
                    float b = (float) iter / maxIterations;
                    return new Color(0, (int) (100 + b * 155), (int) (160 + b * 95)).getRGB();

                case "Feuer":
                    float r = (float) iter / maxIterations;
                    return new Color((int) (r * 255), (int) (r * r * 255), 0).getRGB();

                case "Schwarz/Weiß":
                    return (iter % 2 == 0) ? Color.BLACK.getRGB() : Color.WHITE.getRGB();

                case "Original":
                default:
                    // Authentically replicate original retro GIF rings
                    Color[] palette = {
                        new Color(0, 153, 0),       // Green
                        new Color(0, 153, 153),     // Teal
                        new Color(153, 0, 0),       // Red
                        new Color(153, 0, 153),     // Magenta
                        new Color(102, 51, 0),      // Brown
                        new Color(100, 100, 100),   // Dark Gray
                        new Color(150, 150, 150),   // Mid Gray
                        new Color(200, 200, 200)    // Light Gray
                    };
                    return palette[iter % palette.length].getRGB();
            }
        }

        private void updateStatus(String statusMsg) {
            SwingUtilities.invokeLater(() -> {
                statusLabel.setText(String.format("%s [Iter: %d | Scheme: %s | Re: %.3f, %.3f]",
                        statusMsg, maxIterations, colorScheme, xMin, xMax));
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            if (image == null) {
                int w = getWidth();
                int h = getHeight();
                if (w > 0 && h > 0) {
                    adjustAspectRatio();
                    startCalculation();
                }
            }

            if (image != null) {
                g.drawImage(image, 0, 0, null);
            }

            // Draw selection box outline
            if (pressPoint != null && dragPoint != null) {
                Graphics2D g2d = (Graphics2D) g;
                g2d.setColor(Color.WHITE);
                // Dotted line style
                float[] dash = {4.0f};
                g2d.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, dash, 0.0f));

                int x = Math.min(pressPoint.x, dragPoint.x);
                int y = Math.min(pressPoint.y, dragPoint.y);
                int w = Math.abs(pressPoint.x - dragPoint.x);
                int h = Math.abs(pressPoint.y - dragPoint.y);

                g2d.drawRect(x, y, w, h);
            }
        }
    }
}

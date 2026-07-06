package com.datazuul.euroworks.shell;

import com.datazuul.euroworks.apps.EuroAppFrame;
import com.datazuul.euroworks.apps.EuroMockAppFrame;
import com.datazuul.euroworks.apps.EuroManager;
import com.datazuul.euroworks.apps.EuroPreferences;
import com.datazuul.euroworks.apps.EuroDex;
import com.datazuul.euroworks.apps.EuroMandelbrot;
import com.datazuul.euroworks.apps.EuroCDPlayer;
import com.datazuul.euroworks.apps.EuroScan;
import com.datazuul.euroworks.apps.euroradio.EuroRadio;
import com.datazuul.euroworks.apps.euroweb.EuroWeb;
import com.datazuul.euroworks.screensavers.EuroPipes;
import com.datazuul.euroworks.screensavers.EuroMaze;
import com.datazuul.euroworks.screensavers.EuroBezier;
import com.datazuul.euroworks.screensavers.EuroStarfield;
import com.datazuul.euroworks.games.EuroMines;
import com.datazuul.euroworks.games.EuroBreakout;
import com.datazuul.euroworks.games.EuroInvaders;
import com.datazuul.euroworks.apps.eurosync.EuroSync;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyVetoException;
import java.util.ArrayList;
import java.util.List;

public class EuroShellFrame extends JFrame {

    private final EuroDesktopPane desktopPane;
    private final EuroDock dock;
    private EuroGroupPanel activeGroupPanel = null;

    private Timer idleTimer = null;
    private long lastActivityTime = System.currentTimeMillis();
    private boolean screensaverRunning = false;
    private JFrame activeScreensaverFrame = null;
    private Point initialMousePos = null;

    private JCheckBoxMenuItem fullscreenMenuItem = null;
    private Rectangle normalBounds = null;
    private boolean isFullscreen = false;

    public EuroShellFrame() {
        super("EuroWorks Desktop Shell");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1024, 768);
        setLocationRelativeTo(null);

        // Desktop pane fills the centre
        desktopPane = new EuroDesktopPane();

        // Dock sits on the left side
        dock = new EuroDock(desktopPane, this::toggleGroupPanel);

        // Wrap desktop + dock in a BorderLayout panel
        JPanel root = new JPanel(new BorderLayout());
        root.add(desktopPane, BorderLayout.CENTER);
        root.add(dock, BorderLayout.WEST);
        setContentPane(root);

        // Build global menu bar
        setJMenuBar(createMenuBar());

        // Initialize global inactivity screensaver timer
        initIdleTimer();
    }

    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        // 1. Express Menu (Retro Start Menu)
        JMenu expressMenu = new JMenu("Express");
        expressMenu.setFont(expressMenu.getFont().deriveFont(Font.BOLD));
        expressMenu.setForeground(new Color(0, 100, 100)); // Special styling for retro branding

        String[] apps = { "EuroManager", "EuroWrite", "EuroDraw", "EuroCalc", "EuroFile", "EuroDex", "EuroMandelbrot",
                "EuroPipes", "EuroMaze", "EuroBezier", "EuroStarfield", "EuroMines", "EuroBreakout", "EuroInvaders",
                "EuroCDPlayer", "EuroScan", "EuroRadio", "EuroWeb", "EuroSync", "EuroPreferences" };
        for (String appName : apps) {
            JMenuItem appItem = new JMenuItem(appName);
            appItem.addActionListener(e -> launchApp(appName));
            expressMenu.add(appItem);
        }
        expressMenu.addSeparator();
        JMenuItem exitItem = new JMenuItem("Exit EuroWorks");
        exitItem.addActionListener(e -> System.exit(0));
        expressMenu.add(exitItem);

        // 2. File Menu
        JMenu fileMenu = new JMenu("File");
        JMenuItem closeAllItem = new JMenuItem("Close All Windows");
        closeAllItem.addActionListener(e -> closeAllWindows());
        fileMenu.add(closeAllItem);
        fileMenu.addSeparator();
        JMenuItem fileExitItem = new JMenuItem("Exit");
        fileExitItem.addActionListener(e -> System.exit(0));
        fileMenu.add(fileExitItem);

        // 3. Window Menu
        JMenu windowMenu = new JMenu("Window");
        JMenuItem cascadeItem = new JMenuItem("Cascade");
        cascadeItem.addActionListener(e -> cascadeWindows());
        windowMenu.add(cascadeItem);

        JMenuItem tileItem = new JMenuItem("Tile");
        tileItem.addActionListener(e -> tileWindows());
        windowMenu.add(tileItem);

        JMenuItem arrangeItem = new JMenuItem("Arrange Icons");
        arrangeItem.addActionListener(e -> arrangeIcons());
        windowMenu.add(arrangeItem);

        windowMenu.addSeparator();
        fullscreenMenuItem = new JCheckBoxMenuItem("Fullscreen");
        fullscreenMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F11, 0));
        fullscreenMenuItem.addActionListener(e -> setFullscreen(fullscreenMenuItem.isSelected()));
        windowMenu.add(fullscreenMenuItem);

        // 4. Help Menu
        JMenu helpMenu = new JMenu("Help");
        JMenuItem aboutItem = new JMenuItem("About EuroWorks...");
        aboutItem.addActionListener(e -> JOptionPane.showMessageDialog(this,
                "EuroWorks v0.0.1\nA Java Swing replica of PC/GEOS.\nBuilt for modern systems with classic pixel aesthetics.",
                "About EuroWorks",
                JOptionPane.INFORMATION_MESSAGE));
        helpMenu.add(aboutItem);

        // Add to bar
        menuBar.add(expressMenu);
        menuBar.add(fileMenu);
        menuBar.add(windowMenu);
        menuBar.add(helpMenu);

        return menuBar;
    }

    private void launchApp(String appName) {
        EuroAppFrame frame;
        if ("EuroManager".equals(appName)) {
            frame = new EuroManager();
        } else if ("EuroPreferences".equals(appName)) {
            frame = new EuroPreferences();
        } else if ("EuroDex".equals(appName)) {
            frame = new EuroDex();
        } else if ("EuroMandelbrot".equals(appName)) {
            frame = new EuroMandelbrot();
        } else if ("EuroPipes".equals(appName)) {
            frame = new EuroPipes();
        } else if ("EuroMaze".equals(appName)) {
            frame = new EuroMaze();
        } else if ("EuroBezier".equals(appName)) {
            frame = new EuroBezier();
        } else if ("EuroStarfield".equals(appName)) {
            frame = new EuroStarfield();
        } else if ("EuroMines".equals(appName)) {
            frame = new EuroMines();
        } else if ("EuroBreakout".equals(appName)) {
            frame = new EuroBreakout();
        } else if ("EuroInvaders".equals(appName)) {
            frame = new EuroInvaders();
        } else if ("EuroCDPlayer".equals(appName)) {
            frame = new EuroCDPlayer();
        } else if ("EuroScan".equals(appName)) {
            frame = new EuroScan();
        } else if ("EuroRadio".equals(appName)) {
            frame = new EuroRadio();
        } else if ("EuroWeb".equals(appName)) {
            frame = new EuroWeb();
        } else if ("EuroSync".equals(appName)) {
            frame = new EuroSync();
        } else {
            frame = new EuroMockAppFrame(appName);
        }
        desktopPane.add(frame);
        // Trigger dock active LED update on launch
        dock.updateActiveIndicators();
        try {
            frame.setSelected(true);
        } catch (PropertyVetoException e) {
            e.printStackTrace();
        }
    }

    private void closeAllWindows() {
        for (JInternalFrame frame : desktopPane.getAllFrames()) {
            frame.dispose();
        }
    }

    private void cascadeWindows() {
        JInternalFrame[] frames = desktopPane.getAllFrames();
        int x = 0;
        int y = 0;
        int distance = 30;
        for (JInternalFrame frame : frames) {
            if (frame.isIcon() || !frame.isVisible())
                continue;
            try {
                frame.setMaximum(false);
            } catch (PropertyVetoException e) {
                e.printStackTrace();
            }
            frame.setSize(400, 300); // Reset to default size for cascade clarity
            frame.setLocation(x, y);
            x += distance;
            y += distance;

            // Wrap cascade if it exceeds pane bounds
            if (x + frame.getWidth() > desktopPane.getWidth() || y + frame.getHeight() > desktopPane.getHeight()) {
                x = 0;
                y = 0;
            }
            frame.toFront();
        }
    }

    private void tileWindows() {
        JInternalFrame[] frames = desktopPane.getAllFrames();
        List<JInternalFrame> visibleFrames = new ArrayList<>();
        for (JInternalFrame frame : frames) {
            if (!frame.isIcon() && frame.isVisible()) {
                visibleFrames.add(frame);
            }
        }

        int count = visibleFrames.size();
        if (count == 0)
            return;

        int rows = (int) Math.sqrt(count);
        int cols = count / rows;
        if (rows * cols < count) {
            cols++;
        }

        int w = desktopPane.getWidth() / cols;
        int h = desktopPane.getHeight() / rows;
        int index = 0;

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (index >= count)
                    break;
                JInternalFrame frame = visibleFrames.get(index++);
                try {
                    frame.setMaximum(false);
                } catch (PropertyVetoException e) {
                    e.printStackTrace();
                }
                frame.setBounds(c * w, r * h, w, h);
            }
        }
    }

    private void arrangeIcons() {
        DesktopManager manager = desktopPane.getDesktopManager();
        if (manager != null) {
            for (JInternalFrame frame : desktopPane.getAllFrames()) {
                if (frame.isIcon()) {
                    manager.iconifyFrame(frame);
                }
            }
        }
    }

    private void initIdleTimer() {
        // Intercept all key presses, mouse clicks, and mouse moves globally across the
        // JVM
        Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            if (event instanceof MouseEvent me) {
                if (me.getID() == MouseEvent.MOUSE_MOVED) {
                    Point currentPos = me.getLocationOnScreen();
                    if (screensaverRunning) {
                        if (initialMousePos == null) {
                            initialMousePos = currentPos;
                        } else if (currentPos.distance(initialMousePos) > 10) {
                            stopScreensaver();
                        }
                    } else {
                        lastActivityTime = System.currentTimeMillis();
                    }
                } else if (me.getID() == MouseEvent.MOUSE_PRESSED || me.getID() == MouseEvent.MOUSE_RELEASED
                        || me.getID() == MouseEvent.MOUSE_CLICKED) {
                    resetIdleTimer();
                }
            } else if (event instanceof KeyEvent ke) {
                resetIdleTimer();
            }
        }, AWTEvent.MOUSE_MOTION_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK | AWTEvent.KEY_EVENT_MASK);

        idleTimer = new Timer(1000, e -> checkIdleTime());
        idleTimer.start();
    }

    private synchronized void resetIdleTimer() {
        lastActivityTime = System.currentTimeMillis();
        if (screensaverRunning) {
            stopScreensaver();
        }
    }

    private void checkIdleTime() {
        if (screensaverRunning)
            return;

        if (desktopPane.isScreensaverEnabled()) {
            long idleMs = System.currentTimeMillis() - lastActivityTime;
            long timeoutMs = desktopPane.getScreensaverTimeoutSeconds() * 1000L;
            if (idleMs >= timeoutMs) {
                startScreensaver();
            }
        }
    }

    private synchronized void startScreensaver() {
        if (screensaverRunning)
            return;
        screensaverRunning = true;
        initialMousePos = MouseInfo.getPointerInfo().getLocation();

        String appName = desktopPane.getScreensaverName();
        if ("Kein".equalsIgnoreCase(appName) || "None".equalsIgnoreCase(appName) || appName == null
                || appName.isEmpty()) {
            screensaverRunning = false;
            return;
        }

        // Create borderless maximized fullscreen Window for screensaver rendering
        activeScreensaverFrame = new JFrame();
        activeScreensaverFrame.setUndecorated(true);
        activeScreensaverFrame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        activeScreensaverFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        activeScreensaverFrame.getContentPane().setBackground(Color.BLACK);

        if ("EuroPipes".equals(appName)) {
            com.datazuul.euroworks.screensavers.EuroPipesCanvas canvas = new com.datazuul.euroworks.screensavers.EuroPipesCanvas();
            canvas.setSpeedMs(50);
            activeScreensaverFrame.getContentPane().setLayout(new BorderLayout());
            activeScreensaverFrame.getContentPane().add(canvas, BorderLayout.CENTER);

            canvas.startAnimation();
        } else if ("EuroMaze".equals(appName)) {
            com.datazuul.euroworks.screensavers.EuroMazeCanvas canvas = new com.datazuul.euroworks.screensavers.EuroMazeCanvas();
            canvas.setSpeedMs(40);
            activeScreensaverFrame.getContentPane().setLayout(new BorderLayout());
            activeScreensaverFrame.getContentPane().add(canvas, BorderLayout.CENTER);
            canvas.startAnimation();
        } else if ("EuroBezier".equals(appName)) {
            com.datazuul.euroworks.screensavers.EuroBezierCanvas canvas = new com.datazuul.euroworks.screensavers.EuroBezierCanvas();
            canvas.setSpeedMs(30);
            activeScreensaverFrame.getContentPane().setLayout(new BorderLayout());
            activeScreensaverFrame.getContentPane().add(canvas, BorderLayout.CENTER);
            canvas.startAnimation();
        } else if ("EuroStarfield".equals(appName)) {
            com.datazuul.euroworks.screensavers.EuroStarfieldCanvas canvas = new com.datazuul.euroworks.screensavers.EuroStarfieldCanvas();
            canvas.setSpeedMs(30);
            activeScreensaverFrame.getContentPane().setLayout(new BorderLayout());
            activeScreensaverFrame.getContentPane().add(canvas, BorderLayout.CENTER);
            canvas.startAnimation();
        }

        activeScreensaverFrame.setVisible(true);
    }

    private synchronized void stopScreensaver() {
        if (!screensaverRunning)
            return;
        screensaverRunning = false;
        initialMousePos = null;

        if (activeScreensaverFrame != null) {
            Component[] comps = activeScreensaverFrame.getContentPane().getComponents();
            for (Component c : comps) {
                if (c instanceof com.datazuul.euroworks.screensavers.EuroPipesCanvas canvas) {
                    canvas.stopAnimation();
                } else if (c instanceof com.datazuul.euroworks.screensavers.EuroMazeCanvas canvas) {
                    canvas.stopAnimation();
                } else if (c instanceof com.datazuul.euroworks.screensavers.EuroBezierCanvas canvas) {
                    canvas.stopAnimation();
                } else if (c instanceof com.datazuul.euroworks.screensavers.EuroStarfieldCanvas canvas) {
                    canvas.stopAnimation();
                }
            }
            activeScreensaverFrame.dispose();
            activeScreensaverFrame = null;
        }

        lastActivityTime = System.currentTimeMillis();
    }

    public void setFullscreen(boolean enable) {
        if (this.isFullscreen == enable) {
            return;
        }

        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice gd = ge.getDefaultScreenDevice();
        if (!gd.isFullScreenSupported()) {
            JOptionPane.showMessageDialog(this,
                    "Fullscreen mode is not supported on this screen device.",
                    "Not Supported",
                    JOptionPane.WARNING_MESSAGE);
            if (fullscreenMenuItem != null) {
                fullscreenMenuItem.setSelected(false);
            }
            return;
        }

        this.isFullscreen = enable;

        if (fullscreenMenuItem != null && fullscreenMenuItem.isSelected() != enable) {
            fullscreenMenuItem.setSelected(enable);
        }

        if (enable) {
            normalBounds = getBounds();
            dispose();
            setUndecorated(true);
            gd.setFullScreenWindow(this);
            setVisible(true);
        } else {
            dispose();
            setUndecorated(false);
            gd.setFullScreenWindow(null);
            if (normalBounds != null) {
                setBounds(normalBounds);
            } else {
                setSize(1024, 768);
                setLocationRelativeTo(null);
            }
            setVisible(true);
        }
    }

    private void toggleGroupPanel(String groupName, List<org.w3c.dom.Element> apps) {
        if (activeGroupPanel != null) {
            desktopPane.remove(activeGroupPanel);
            activeGroupPanel = null;
        }

        if (groupName != null && apps != null && !apps.isEmpty()) {
            activeGroupPanel = new EuroGroupPanel(groupName, apps, this::launchApp);
            
            // Calculate height/width based on items
            int items = apps.size();
            int width = Math.min(600, 30 + items * 100);
            int height = 110;
            activeGroupPanel.setBounds(90, 20, width, height);

            // Add behind active windows
            desktopPane.add(activeGroupPanel, Integer.valueOf(JLayeredPane.DEFAULT_LAYER - 5));
        }
        desktopPane.repaint();
    }
}

package com.datazuul.euroworks.screensavers;

import com.datazuul.euroworks.apps.EuroAppFrame;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * Common base class for all EuroWorks screensaver application windows.
 *
 * <p>Provides:
 * <ul>
 *   <li>Canvas lifecycle wiring (start/stop on open/close, immediate start via invokeLater)</li>
 *   <li>Fullscreen enter/leave mechanics (borderless JFrame, ESC key binding, window listener)</li>
 *   <li>Right-click popup menu on the canvas with a Vollbildmodus entry</li>
 *   <li>A standard "Datei" menu with Vollbildmodus + Schließen</li>
 * </ul>
 *
 * <p>Subclasses call {@code super(title, canvas)} and then implement:
 * <ul>
 *   <li>{@link #buildExtraMenuItems(JMenu, JPopupMenu)} – screensaver-specific Datei / popup items</li>
 *   <li>{@link #buildSettingsMenu()} – the "Einstellungen" JMenu (or {@code null} to omit)</li>
 * </ul>
 */
public abstract class AbstractEuroScreensaver extends EuroAppFrame {

    private final ScreensaverCanvas canvas;

    // ── fullscreen state ────────────────────────────────────────────────────
    private boolean   isFullscreen      = false;
    private JFrame    fullscreenFrame   = null;
    private Container originalContainer = null;

    // updated text before each popup show
    private JMenuItem popFullscreen;

    /**
     * @param title  window title shown in the internal-frame title bar
     * @param canvas the screensaver canvas (must be created in the subclass constructor
     *               <em>before</em> calling {@code super()})
     */
    protected AbstractEuroScreensaver(String title, ScreensaverCanvas canvas) {
        super(title);
        this.canvas = canvas;
        setSize(500, 400);

        // Place canvas in content pane
        JPanel contentPane = new JPanel(new BorderLayout());
        contentPane.add((Component) canvas, BorderLayout.CENTER);
        setContentPane(contentPane);

        // Wire open/close → start/stop
        addInternalFrameListener(new javax.swing.event.InternalFrameAdapter() {
            @Override
            public void internalFrameOpened(javax.swing.event.InternalFrameEvent e) {
                canvas.startAnimation();
            }
            @Override
            public void internalFrameClosed(javax.swing.event.InternalFrameEvent e) {
                canvas.stopAnimation();
            }
        });

        // Start immediately when added to desktop
        SwingUtilities.invokeLater(canvas::startAnimation);

        // Build and set menu bar
        setJMenuBar(buildMenuBar());

        // Attach right-click popup to canvas
        attachPopupMenu();
    }

    protected ScreensaverCanvas getCanvas() {
        return canvas;
    }

    // ── abstract hooks ──────────────────────────────────────────────────────

    /**
     * Add screensaver-specific items to the Datei menu and/or the canvas popup.
     * Called during construction, after the common "Vollbildmodus" item is added.
     *
     * @param dateiMenu the "Datei" JMenu
     * @param popupMenu the canvas right-click popup
     */
    protected abstract void buildExtraMenuItems(JMenu dateiMenu, JPopupMenu popupMenu);

    /**
     * Build and return the "Einstellungen" JMenu, or {@code null} to omit it.
     */
    protected abstract JMenu buildSettingsMenu();

    // ── fullscreen ──────────────────────────────────────────────────────────

    protected final void toggleFullscreen() {
        if (isFullscreen) leaveFullscreen(); else enterFullscreen();
    }

    private void enterFullscreen() {
        if (isFullscreen) return;
        isFullscreen = true;

        originalContainer = canvas.getParent();
        if (originalContainer != null) originalContainer.remove((Component) canvas);

        fullscreenFrame = new JFrame();
        fullscreenFrame.setUndecorated(true);
        fullscreenFrame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        fullscreenFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        fullscreenFrame.getContentPane().setLayout(new BorderLayout());
        fullscreenFrame.getContentPane().add((Component) canvas, BorderLayout.CENTER);

        fullscreenFrame.getRootPane().registerKeyboardAction(
                e -> leaveFullscreen(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        fullscreenFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                if (isFullscreen) leaveFullscreen();
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
            fullscreenFrame.getContentPane().remove((Component) canvas);
            fullscreenFrame.dispose();
            fullscreenFrame = null;
        }
        if (originalContainer != null) {
            originalContainer.add((Component) canvas, BorderLayout.CENTER);
            originalContainer.revalidate();
            originalContainer.repaint();
        }
    }

    // ── menu building ───────────────────────────────────────────────────────

    private JMenuBar buildMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        JPopupMenu popupMenu = new JPopupMenu();

        // Datei menu
        JMenu dateiMenu = new JMenu("Datei");

        JMenuItem mFullscreen = new JMenuItem("Vollbildmodus");
        mFullscreen.addActionListener(e -> toggleFullscreen());
        dateiMenu.add(mFullscreen);

        // Hook for subclass-specific Datei / popup items
        buildExtraMenuItems(dateiMenu, popupMenu);

        dateiMenu.addSeparator();
        JMenuItem mClose = new JMenuItem("Schließen");
        mClose.addActionListener(e -> dispose());
        dateiMenu.add(mClose);

        menuBar.add(dateiMenu);

        // Optional Einstellungen menu
        JMenu settingsMenu = buildSettingsMenu();
        if (settingsMenu != null) {
            menuBar.add(settingsMenu);
        }

        // Common popup fullscreen entry (always first)
        popFullscreen = new JMenuItem("Vollbildmodus");
        popFullscreen.addActionListener(e -> toggleFullscreen());
        popupMenu.insert(popFullscreen, 0);

        // Stash popup so the mouse listener can retrieve it
        ((JPanel) canvas).putClientProperty("popupMenu", popupMenu);

        return menuBar;
    }

    private void attachPopupMenu() {
        canvas.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed (MouseEvent e) { if (e.isPopupTrigger()) showPopup(e); }
            @Override public void mouseReleased(MouseEvent e) { if (e.isPopupTrigger()) showPopup(e); }
            private void showPopup(MouseEvent e) {
                if (popFullscreen != null) {
                    popFullscreen.setText(isFullscreen ? "Vollbild verlassen" : "Vollbildmodus");
                }
                JPopupMenu popup = (JPopupMenu) ((JPanel) canvas).getClientProperty("popupMenu");
                if (popup != null) popup.show(e.getComponent(), e.getX(), e.getY());
            }
        });
    }

    // ── shared helpers for subclasses ───────────────────────────────────────

    /**
     * Builds a standard "Geschwindigkeit" radio-button sub-menu.
     *
     * @param labels    display labels (e.g. "Schnell", "Normal", "Langsam")
     * @param delays    corresponding Timer delays in ms
     * @param defaultMs the currently active delay
     * @param canvas    canvas whose {@code setSpeedMs} will be called
     */
    protected static JMenu buildSpeedMenu(String[] labels, int[] delays,
                                          int defaultMs, ScreensaverCanvas canvas) {
        JMenu speedMenu = new JMenu("Geschwindigkeit");
        ButtonGroup group = new ButtonGroup();
        for (int i = 0; i < labels.length; i++) {
            final int delay = delays[i];
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(labels[i], delay == defaultMs);
            item.addActionListener(e -> canvas.setSpeedMs(delay));
            group.add(item);
            speedMenu.add(item);
        }
        return speedMenu;
    }
}

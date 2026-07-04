package com.datazuul.euroworks.screensavers;

/**
 * Common interface for all EuroWorks screensaver canvas panels.
 * Allows {@link AbstractEuroScreensaver} to control the animation lifecycle
 * without knowing the concrete canvas type.
 */
public interface ScreensaverCanvas {

    /** Start (or restart) the animation timer. */
    void startAnimation();

    /** Stop the animation timer. */
    void stopAnimation();

    /**
     * Set the animation frame delay in milliseconds.
     *
     * @param ms delay between frames
     */
    void setSpeedMs(int ms);

    // ── Swing paint/layout methods used by the base class ──────────────────
    // (all implemented by JPanel / JComponent already)

    void revalidate();
    void repaint();

    java.awt.Container getParent();
    void addMouseListener(java.awt.event.MouseListener l);
    void putClientProperty(Object key, Object value);
    Object getClientProperty(Object key);
}

package com.datazuul.euroworks.shell;

import com.datazuul.euroworks.widgets.taskbar.TaskbarClock;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bottom taskbar — styled after the BreadBox/GEOS/PC-GEOS retro desktop
 * (Win95 silver 3D look).
 *
 * Layout (left → right):
 *   [≡ Express launcher]  |  [task toggle buttons ...]  |  [HH:mm clock]
 */
public class EuroTaskbar extends JPanel {

    // ── palette ─────────────────────────────────────────────────────────────
    private static final Color SILVER  = new Color(192, 192, 192);
    private static final Color SHADOW  = new Color(128, 128, 128);
    private static final Color DARK    = new Color(64,  64,  64);
    private static final Color HILIGHT = Color.WHITE;
    private static final Color SEL_BG  = new Color(0,   0,   128); // Win95 blue selection
    private static final Color SEL_FG  = Color.WHITE;

    private static final int HEIGHT = 28;

    // ── state ────────────────────────────────────────────────────────────────
    private final JDesktopPane desktop;
    private final JPanel       taskArea;
    private final Map<JInternalFrame, AbstractButton> taskButtons = new LinkedHashMap<>();

    private JPopupMenu launcherPopup;
    private ActionListener launchCallback;

    public EuroTaskbar(JDesktopPane desktop) {
        this.desktop = desktop;

        setLayout(new BorderLayout());
        setBackground(SILVER);
        setPreferredSize(new Dimension(0, HEIGHT));
        // Paint 3D raised panel in paintComponent — no border needed
        setOpaque(true);

        // ── LEFT: launcher button ────────────────────────────────────────────
        JButton launcherBtn = buildLauncherButton();
        JPanel leftWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 3));
        leftWrap.setBackground(SILVER);
        leftWrap.setOpaque(false);
        leftWrap.add(launcherBtn);
        add(leftWrap, BorderLayout.WEST);

        // ── CENTRE: task buttons ─────────────────────────────────────────────
        taskArea = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 3));
        taskArea.setBackground(SILVER);
        taskArea.setOpaque(false);
        add(taskArea, BorderLayout.CENTER);

        // ── RIGHT: clock ─────────────────────────────────────────────────────
        JPanel rightWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 4));
        rightWrap.setBackground(SILVER);
        rightWrap.setOpaque(false);
        rightWrap.add(new TaskbarClock());
        add(rightWrap, BorderLayout.EAST);
    }

    // ── public API ───────────────────────────────────────────────────────────

    /** Paint the raised 3D taskbar panel background. */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        int w = getWidth(), h = getHeight();
        // Top highlight line
        g.setColor(HILIGHT);
        g.drawLine(0, 0, w - 1, 0);
        // Silver body (already filled by super)
        // Bottom shadow
        g.setColor(SHADOW);
        g.drawLine(0, h - 1, w - 1, h - 1);
        g.setColor(DARK);
        g.drawLine(0, h - 2, w - 1, h - 2);
    }

    public void setLaunchCallback(ActionListener cb) {
        this.launchCallback = cb;
        rebuildLauncherPopup();
    }

    public void registerFrame(JInternalFrame frame) {
        if (taskButtons.containsKey(frame)) return;

        JToggleButton btn = buildTaskButton(frame);
        taskButtons.put(frame, btn);
        taskArea.add(btn);
        taskArea.revalidate();
        taskArea.repaint();

        frame.addInternalFrameListener(new InternalFrameAdapter() {
            @Override public void internalFrameClosed     (InternalFrameEvent e) { unregister(frame); }
            @Override public void internalFrameActivated  (InternalFrameEvent e) { syncState(frame); }
            @Override public void internalFrameDeactivated(InternalFrameEvent e) { syncState(frame); }
            @Override public void internalFrameIconified  (InternalFrameEvent e) { syncState(frame); }
            @Override public void internalFrameDeiconified(InternalFrameEvent e) { syncState(frame); }
        });
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private void unregister(JInternalFrame frame) {
        AbstractButton btn = taskButtons.remove(frame);
        if (btn != null) { taskArea.remove(btn); taskArea.revalidate(); taskArea.repaint(); }
    }

    private void syncState(JInternalFrame frame) {
        AbstractButton btn = taskButtons.get(frame);
        if (btn instanceof JToggleButton tb) {
            tb.setSelected(frame.isSelected() && !frame.isIcon());
            styleTaskButton(tb, tb.isSelected());
            tb.repaint();
        }
    }

    // ── Launcher ──────────────────────────────────────────────────────────────

    private JButton buildLauncherButton() {
        JButton btn = new JButton("≡  Express");
        btn.setFont(new Font("SansSerif", Font.BOLD, 11));
        btn.setForeground(Color.BLACK);
        btn.setBackground(SILVER);
        btn.setOpaque(true);
        btn.setFocusPainted(false);
        btn.setBorder(buildRaisedBorder());
        btn.setPreferredSize(new Dimension(96, HEIGHT - 6));

        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mousePressed(java.awt.event.MouseEvent e) {
                btn.setBorder(buildSunkenBorder());
            }
            @Override public void mouseReleased(java.awt.event.MouseEvent e) {
                btn.setBorder(buildRaisedBorder());
            }
        });

        btn.addActionListener(e -> {
            if (launcherPopup == null) rebuildLauncherPopup();
            launcherPopup.show(btn, 0, -launcherPopup.getPreferredSize().height);
        });
        return btn;
    }

    private void rebuildLauncherPopup() {
        launcherPopup = new JPopupMenu() {
            @Override protected void paintBorder(Graphics g) {
                // Win95 popup: 1px black border
                g.setColor(Color.BLACK);
                g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
            }
        };
        launcherPopup.setBackground(SILVER);
        launcherPopup.setBorderPainted(true);
        launcherPopup.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

        // ── 1. Programs (Submenu) ───────────────────────────────────────────
        JMenu programsMenu = buildSubMenu("Programs");

        // ── 1a. Games (Submenu)
        JMenu gamesMenu = buildSubMenu("Games");
        gamesMenu.add(buildMenuItem("EuroMines", "EuroMines"));
        gamesMenu.add(buildMenuItem("EuroBreakout", "EuroBreakout"));
        gamesMenu.add(buildMenuItem("EuroInvaders", "EuroInvaders"));
        programsMenu.add(gamesMenu);

        // ── 1b. Office (Submenu)
        JMenu officeMenu = buildSubMenu("Office");
        officeMenu.add(buildMenuItem("EuroWrite", "EuroWrite"));
        officeMenu.add(buildMenuItem("EuroDraw", "EuroDraw"));
        officeMenu.add(buildMenuItem("EuroCalc", "EuroCalc"));
        officeMenu.add(buildMenuItem("EuroFile", "EuroFile"));
        programsMenu.add(officeMenu);

        // ── 1c. Organize (Submenu)
        JMenu organizeMenu = buildSubMenu("Organize");
        organizeMenu.add(buildMenuItem("EuroManager", "EuroManager"));
        organizeMenu.add(buildMenuItem("EuroDex", "EuroDex"));
        organizeMenu.add(buildMenuItem("EuroMandelbrot", "EuroMandelbrot"));
        organizeMenu.add(buildMenuItem("CD Player", "EuroCDPlayer"));
        organizeMenu.addSeparator();
        organizeMenu.add(buildMenuItem("EuroPipes", "EuroPipes"));
        organizeMenu.add(buildMenuItem("EuroMaze", "EuroMaze"));
        organizeMenu.add(buildMenuItem("EuroBezier", "EuroBezier"));
        organizeMenu.add(buildMenuItem("EuroStarfield", "EuroStarfield"));
        programsMenu.add(organizeMenu);

        launcherPopup.add(programsMenu);

        launcherPopup.addSeparator();

        // ── 2. Settings (Submenu) ───────────────────────────────────────────
        JMenu settingsMenu = buildSubMenu("Settings");
        settingsMenu.add(buildMenuItem("EuroPreferences", "EuroPreferences"));
        launcherPopup.add(settingsMenu);

        launcherPopup.addSeparator();

        // ── 3. Shut Down... ─────────────────────────────────────────────────
        launcherPopup.add(buildMenuItem("Shut Down...", "__EXIT__"));
    }

    private JMenuItem buildMenuItem(String label, String command) {
        JMenuItem item = new JMenuItem(label) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                if (isArmed()) {
                    g2.setColor(SEL_BG);
                    g2.fillRect(0, 0, getWidth(), getHeight());
                    g2.setColor(SEL_FG);
                } else {
                    g2.setColor(SILVER);
                    g2.fillRect(0, 0, getWidth(), getHeight());
                    g2.setColor(Color.BLACK);
                }
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText(), 24, (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
            }
        };
        item.setFont(new Font("SansSerif", Font.PLAIN, 11));
        item.setBackground(SILVER);
        item.setBorderPainted(false);
        item.setOpaque(false);
        item.setPreferredSize(new Dimension(170, 20));
        item.setActionCommand(command);
        item.addActionListener(e -> {
            if ("__EXIT__".equals(e.getActionCommand())) {
                System.exit(0);
            } else if (launchCallback != null) {
                launchCallback.actionPerformed(e);
            }
        });
        return item;
    }

    private JMenu buildSubMenu(String label) {
        JMenu menu = new JMenu(label) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                boolean active = isSelected() || isPopupMenuVisible();
                if (active) {
                    g2.setColor(SEL_BG);
                    g2.fillRect(0, 0, getWidth(), getHeight());
                    g2.setColor(SEL_FG);
                } else {
                    g2.setColor(SILVER);
                    g2.fillRect(0, 0, getWidth(), getHeight());
                    g2.setColor(Color.BLACK);
                }

                // Draw 12x10 pixelated retro folder icon
                int ix = 6;
                int iy = (getHeight() - 10) / 2;
                
                // Draw back tab and main border outline in dark brown/yellow
                g2.setColor(new Color(150, 110, 40));
                g2.drawRect(ix, iy, 4, 2);
                g2.drawRect(ix, iy + 2, 11, 7);
                
                // Fill main body with retro yellow/cream
                g2.setColor(new Color(252, 210, 95));
                g2.fillRect(ix + 1, iy + 1, 3, 1);
                g2.fillRect(ix + 1, iy + 3, 10, 6);
                
                // Front flap outline crease for depth overlap look
                g2.setColor(new Color(200, 150, 50));
                g2.drawLine(ix + 1, iy + 4, ix + 10, iy + 4);

                // Draw menu label text (re-establish active/inactive text color)
                g2.setColor(active ? SEL_FG : Color.BLACK);
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText(), 24, (getHeight() + fm.getAscent() - fm.getDescent()) / 2);

                // Submenu right-arrow marker
                int h = getHeight();
                int[] xPoints = { getWidth() - 12, getWidth() - 12, getWidth() - 6 };
                int[] yPoints = { h / 2 - 4, h / 2 + 4, h / 2 };
                g2.fillPolygon(xPoints, yPoints, 3);
                g2.dispose();
            }
        };
        menu.setFont(new Font("SansSerif", Font.PLAIN, 11));
        menu.setBackground(SILVER);
        menu.setBorderPainted(false);
        menu.setOpaque(false);
        menu.setPreferredSize(new Dimension(170, 20));
        menu.getPopupMenu().setBackground(SILVER);
        menu.getPopupMenu().setBorder(BorderFactory.createLineBorder(Color.BLACK));
        return menu;
    }

    // ── Task buttons ──────────────────────────────────────────────────────────

    private JToggleButton buildTaskButton(JInternalFrame frame) {
        String title = truncate(frame.getTitle(), 20);
        JToggleButton btn = new JToggleButton(title) {
            @Override protected void paintComponent(Graphics g) {
                boolean sel = isSelected() || getModel().isPressed();
                Graphics2D g2 = (Graphics2D) g.create();
                
                // Draw background
                if (sel) {
                    // Checkered dither pattern: alternating SILVER and WHITE pixels
                    g2.setColor(SILVER);
                    g2.fillRect(0, 0, getWidth(), getHeight());
                    g2.setColor(HILIGHT);
                    for (int y = 2; y < getHeight() - 2; y++) {
                        for (int x = 2 + (y % 2); x < getWidth() - 2; x += 2) {
                            g2.fillRect(x, y, 1, 1);
                        }
                    }
                } else {
                    g2.setColor(SILVER);
                    g2.fillRect(0, 0, getWidth(), getHeight());
                }

                // 3D border
                if (sel) drawSunken(g2, 0, 0, getWidth(), getHeight());
                else      drawRaised(g2, 0, 0, getWidth(), getHeight());

                // Text (shift 1px when pressed for depth illusion)
                g2.setFont(getFont());
                g2.setColor(Color.BLACK);
                FontMetrics fm = g2.getFontMetrics();
                int tx = (getWidth()  - fm.stringWidth(getText())) / 2 + (sel ? 1 : 0);
                int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2 + (sel ? 1 : 0);
                g2.drawString(getText(), tx, ty);
                g2.dispose();
            }
        };
        btn.setFont(new Font("SansSerif", Font.PLAIN, 11));
        btn.setBackground(SILVER);
        btn.setOpaque(true);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false); // we paint manually
        btn.setContentAreaFilled(false);
        btn.setPreferredSize(new Dimension(120, HEIGHT - 6));
        btn.setSelected(frame.isSelected() && !frame.isIcon());

        btn.addActionListener(e -> {
            try {
                if (frame.isIcon()) {
                    frame.setIcon(false);
                    frame.setSelected(true);
                } else if (frame.isSelected()) {
                    frame.setIcon(true);
                } else {
                    frame.setSelected(true);
                }
            } catch (Exception ex) { ex.printStackTrace(); }
        });
        return btn;
    }

    private void styleTaskButton(JToggleButton btn, boolean selected) {
        // Visual state is handled in paintComponent; just repaint
        btn.repaint();
    }

    // ── 3D paint helpers ──────────────────────────────────────────────────────

    static void drawRaised(Graphics2D g, int x, int y, int w, int h) {
        // outer hilight (top+left white, bottom+right dark)
        g.setColor(HILIGHT);
        g.drawLine(x, y, x + w - 2, y);         // top
        g.drawLine(x, y, x, y + h - 2);          // left
        g.setColor(DARK);
        g.drawLine(x, y + h - 1, x + w - 1, y + h - 1); // bottom
        g.drawLine(x + w - 1, y, x + w - 1, y + h - 1); // right
        // inner shadow
        g.setColor(SILVER);
        g.drawLine(x + 1, y + 1, x + w - 3, y + 1); // inner top
        g.drawLine(x + 1, y + 1, x + 1, y + h - 3); // inner left
        g.setColor(SHADOW);
        g.drawLine(x + 1, y + h - 2, x + w - 2, y + h - 2); // inner bottom
        g.drawLine(x + w - 2, y + 1, x + w - 2, y + h - 2); // inner right
    }

    static void drawSunken(Graphics2D g, int x, int y, int w, int h) {
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

    // ── Border factories ──────────────────────────────────────────────────────

    private static Border buildRaisedBorder() {
        return new javax.swing.border.AbstractBorder() {
            @Override public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
                drawRaised((Graphics2D) g, x, y, w, h);
            }
            @Override public Insets getBorderInsets(Component c) { return new Insets(2, 4, 2, 4); }
        };
    }

    private static Border buildSunkenBorder() {
        return new javax.swing.border.AbstractBorder() {
            @Override public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
                drawSunken((Graphics2D) g, x, y, w, h);
            }
            @Override public Insets getBorderInsets(Component c) { return new Insets(2, 4, 2, 4); }
        };
    }

    /** 1-pixel raised top + sunken-panel bottom for the taskbar strip itself. */
    private static Border buildRaisedTopBorder() {
        return new javax.swing.border.AbstractBorder() {
            @Override public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
                g.setColor(HILIGHT);
                g.drawLine(x, y, x + w - 1, y);       // top white
                g.setColor(SHADOW);
                g.drawLine(x, y + 1, x + w - 1, y + 1); // second line shadow
            }
            @Override public Insets getBorderInsets(Component c) { return new Insets(2, 0, 0, 0); }
        };
    }

    // ── utils ─────────────────────────────────────────────────────────────────

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}

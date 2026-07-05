package com.datazuul.euroworks.apps;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.basic.BasicInternalFrameUI;
import java.awt.*;
import java.beans.PropertyVetoException;

public class EuroAppFrame extends JInternalFrame {

    private JPanel mainWrapperPanel;
    private JPanel headerPanel;
    private JPanel customTitleBar;
    private JLabel titleLabel;
    private Container userContentPane;
    private JMenuBar userMenuBar;
    private JButton sysButton;

    // Custom retro vector icons for title buttons
    private static final Icon SYS_MENU_ICON = new SysMenuIcon();
    private static final Icon MIN_ICON = new MinIcon();
    private static final Icon MAX_ICON = new MaxIcon();
    private static final Icon CLOSE_ICON = new CloseIcon();

    public EuroAppFrame(String title) {
        // title, resizable, closable, maximizable, iconifiable
        super(title, true, true, true, true);

        // Remove standard Look & Feel window decorations (north pane)
        if (getUI() instanceof BasicInternalFrameUI ui) {
            ui.setNorthPane(null);
        }

        // Create main wrapper panel to hold our custom header and user contents
        mainWrapperPanel = new JPanel(new BorderLayout());

        // Create vertical header panel to contain custom title bar and JMenuBar
        headerPanel = new JPanel();
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));

        // Create custom retro title bar and add to header
        customTitleBar = createCustomTitleBar(title);
        customTitleBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        headerPanel.add(customTitleBar);

        mainWrapperPanel.add(headerPanel, BorderLayout.NORTH);

        // Set initial border and title bar colors (active state)
        updateBorder(true);
        updateTitleBarColors(true);

        // Apply our custom wrapper panel as the actual internal frame content pane
        super.setContentPane(mainWrapperPanel);

        // Listen for internal frame selection (focus) events to toggle active/inactive states
        addPropertyChangeListener(evt -> {
            if (JInternalFrame.IS_SELECTED_PROPERTY.equals(evt.getPropertyName())) {
                boolean selected = (Boolean) evt.getNewValue();
                updateTitleBarColors(selected);
                updateBorder(selected);
            }
        });

        // Default size and constraints
        setSize(400, 300);
        setMinimumSize(new Dimension(200, 150));
        setVisible(true);
    }
    @Override
    public void setJMenuBar(JMenuBar menuBar) {
        if (mainWrapperPanel == null) {
            super.setJMenuBar(menuBar);
        } else {
            if (userMenuBar != null) {
                headerPanel.remove(userMenuBar);
            }
            userMenuBar = menuBar;
            if (userMenuBar != null) {
                userMenuBar.setAlignmentX(Component.LEFT_ALIGNMENT);
                userMenuBar.setMaximumSize(new Dimension(Short.MAX_VALUE, userMenuBar.getPreferredSize().height));
                headerPanel.add(userMenuBar);
            }
            headerPanel.revalidate();
            headerPanel.repaint();
        }
    }

    @Override
    public JMenuBar getJMenuBar() {
        return mainWrapperPanel == null ? super.getJMenuBar() : userMenuBar;
    }

    @Override
    public void setContentPane(Container c) {
        if (mainWrapperPanel == null) {
            super.setContentPane(c);
        } else {
            if (userContentPane != null) {
                mainWrapperPanel.remove(userContentPane);
            }
            userContentPane = c;
            mainWrapperPanel.add(userContentPane, BorderLayout.CENTER);
            mainWrapperPanel.revalidate();
            mainWrapperPanel.repaint();
        }
    }

    private JPanel createCustomTitleBar(String title) {
        JPanel titlePanel = new JPanel(new BorderLayout());
        titlePanel.setPreferredSize(new Dimension(0, 24));

        // Title text label
        titleLabel = new JLabel(" " + title);
        titleLabel.setFont(new Font("Courier New", Font.BOLD, 12));
        titlePanel.add(titleLabel, BorderLayout.CENTER);

        // Window Dragging functionality
        final Point[] initialClick = {null};
        titlePanel.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                initialClick[0] = e.getPoint();
                try {
                    setSelected(true);
                } catch (PropertyVetoException ex) {
                    ex.printStackTrace();
                }
            }
        });

        titlePanel.addMouseMotionListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseDragged(java.awt.event.MouseEvent e) {
                if (initialClick[0] == null) return;
                int thisX = getX();
                int thisY = getY();
                int xMoved = e.getX() - initialClick[0].x;
                int yMoved = e.getY() - initialClick[0].y;
                setLocation(thisX + xMoved, thisY + yMoved);
            }
        });

        // Left controls: Retro System Close box [=]
        JPanel leftButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 3));
        leftButtonPanel.setOpaque(false);
        sysButton = createRetroButton(com.datazuul.euroworks.shell.EuroIconThemeManager.getIcon(getIconThemeKey()));
        sysButton.setToolTipText("Close");
        sysButton.addActionListener(e -> dispose());
        leftButtonPanel.add(sysButton);
        titlePanel.add(leftButtonPanel, BorderLayout.WEST);

        // Right controls: Minimize, Maximize, Close [.][^][X]
        JPanel rightButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 3, 3));
        rightButtonPanel.setOpaque(false);

        JButton minButton = createRetroButton(MIN_ICON);
        minButton.setToolTipText("Minimize");
        minButton.addActionListener(e -> {
            try {
                setIcon(true);
            } catch (PropertyVetoException ex) {
                ex.printStackTrace();
            }
        });

        JButton maxButton = createRetroButton(MAX_ICON);
        maxButton.setToolTipText("Maximize");
        maxButton.addActionListener(e -> {
            try {
                setMaximum(!isMaximum());
            } catch (PropertyVetoException ex) {
                ex.printStackTrace();
            }
        });

        JButton closeButton = createRetroButton(CLOSE_ICON);
        closeButton.setToolTipText("Close");
        closeButton.addActionListener(e -> dispose());

        rightButtonPanel.add(minButton);
        rightButtonPanel.add(maxButton);
        rightButtonPanel.add(closeButton);
        titlePanel.add(rightButtonPanel, BorderLayout.EAST);

        return titlePanel;
    }

    private JButton createRetroButton(Icon icon) {
        JButton button = new JButton(icon);
        button.setPreferredSize(new Dimension(18, 18));
        button.setMinimumSize(new Dimension(18, 18));
        button.setMaximumSize(new Dimension(18, 18));
        button.setFocusable(false);
        button.setMargin(new Insets(0, 0, 0, 0));
        button.setBackground(new Color(212, 208, 200));
        button.setBorder(BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED));
        return button;
    }

    private void updateTitleBarColors(boolean active) {
        if (customTitleBar != null) {
            if (active) {
                customTitleBar.setBackground(new Color(0, 90, 110)); // Steel Blue
                titleLabel.setForeground(Color.WHITE);
            } else {
                customTitleBar.setBackground(new Color(150, 150, 150)); // Flat Gray
                titleLabel.setForeground(new Color(212, 208, 200)); // Light Gray
            }
            customTitleBar.repaint();
        }
    }

    private void updateBorder(boolean active) {
        Color borderColor = active ? new Color(0, 90, 110) : new Color(150, 150, 150);
        Border lineBorder = BorderFactory.createLineBorder(borderColor, 3);
        Border shadowBorder = BorderFactory.createLineBorder(Color.BLACK, 1);
        setBorder(BorderFactory.createCompoundBorder(shadowBorder, lineBorder));
    }

    // --- Inner Retro Icon Painters ---

    private static class SysMenuIcon implements Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            g.setColor(Color.BLACK);
            // Thick horizontal line matching Geoworks close menu drawer
            g.fillRect(x + 2, y + 6, 12, 3);
        }

        @Override public int getIconWidth() { return 16; }
        @Override public int getIconHeight() { return 16; }
    }

    private static class MinIcon implements Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            g.setColor(Color.BLACK);
            // Retro downward triangle
            int[] xPoints = {x + 3, x + 13, x + 8};
            int[] yPoints = {y + 6, y + 6, y + 11};
            g.fillPolygon(xPoints, yPoints, 3);
        }

        @Override public int getIconWidth() { return 16; }
        @Override public int getIconHeight() { return 16; }
    }

    private static class MaxIcon implements Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            g.setColor(Color.BLACK);
            // Retro upward triangle
            int[] xPoints = {x + 3, x + 13, x + 8};
            int[] yPoints = {y + 11, y + 11, y + 6};
            g.fillPolygon(xPoints, yPoints, 3);
        }

        @Override public int getIconWidth() { return 16; }
        @Override public int getIconHeight() { return 16; }
    }

    private static class CloseIcon implements Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(Color.BLACK);
            g2.setStroke(new BasicStroke(2)); // Clean 2-pixel stroke
            // Paint 'X' cross
            g2.drawLine(x + 4, y + 4, x + 12, y + 12);
            g2.drawLine(x + 12, y + 4, x + 4, y + 12);
            g2.dispose();
        }

        @Override public int getIconWidth() { return 16; }
        @Override public int getIconHeight() { return 16; }
    }

    public String getIconThemeKey() {
        String title = getTitle().toLowerCase();
        if (title.contains("cd player")) return "cdplayer";
        if (title.contains("preferences")) return "preferences";
        if (title.contains("manager")) return "folder";
        if (title.contains("write")) return "document";
        if (title.contains("calc")) return "calc";
        if (title.contains("draw") || title.contains("paint")) return "paint";
        if (title.contains("mines")) return "mines";
        if (title.contains("radio")) return "radio";
        if (title.contains("web")) return "web";
        if (title.contains("breakout")) return "breakout";
        if (title.contains("invaders")) return "invaders";
        if (title.contains("file")) return "file";
        if (title.contains("dex")) return "dex";
        if (title.contains("mandelbrot")) return "mandelbrot";
        if (title.contains("scan")) return "scan";
        if (title.contains("pipes")) return "pipes";
        if (title.contains("maze")) return "maze";
        if (title.contains("bezier")) return "bezier";
        if (title.contains("starfield")) return "starfield";
        return "generic_app";
    }

    public void updateTitleBarIcon() {
        if (sysButton != null) {
            sysButton.setIcon(com.datazuul.euroworks.shell.EuroIconThemeManager.getIcon(getIconThemeKey()));
            sysButton.repaint();
        }
    }
}

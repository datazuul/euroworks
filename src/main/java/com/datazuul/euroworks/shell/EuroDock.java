package com.datazuul.euroworks.shell;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JDesktopPane;
import javax.swing.JInternalFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class EuroDock extends JPanel {

    private static final Color CHARCOAL = new Color(0x666769);
    private static final Color LIGHT_GREY = new Color(192, 192, 192);
    private static final Color DARK_SHADOW = new Color(100, 100, 100);

    private final JDesktopPane desktop;
    private final java.util.function.BiConsumer<String, List<Element>> groupToggleCallback;
    private final List<GroupButton> groupButtons = new ArrayList<>();
    private final List<Element> groupElements = new ArrayList<>();

    private static class ThickBevelBorder extends javax.swing.border.AbstractBorder {
        private final boolean sunken;

        public ThickBevelBorder(boolean sunken) {
            this.sunken = sunken;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            Color light = Color.WHITE;
            Color dark = DARK_SHADOW;
            if (sunken) {
                light = new Color(70, 70, 70); // deep recess shadow
                dark = Color.WHITE;
            }
            // Outermost outline: Black border around the button
            g2.setColor(Color.BLACK);
            g2.drawRect(x, y, width - 1, height - 1);

            // Bevel line 1 (x+1, y+1)
            g2.setColor(light);
            g2.drawLine(x + 1, y + 1, x + width - 2, y + 1); // Top
            g2.drawLine(x + 1, y + 1, x + 1, y + height - 2); // Left
            g2.setColor(dark);
            g2.drawLine(x + 1, y + height - 2, x + width - 2, y + height - 2); // Bottom
            g2.drawLine(x + width - 2, y + 1, x + width - 2, y + height - 2); // Right

            // Bevel line 2 (x+2, y+2)
            g2.setColor(light);
            g2.drawLine(x + 2, y + 2, x + width - 3, y + 2); // Top
            g2.drawLine(x + 2, y + 2, x + 2, y + height - 3); // Left
            g2.setColor(dark);
            g2.drawLine(x + 2, y + height - 3, x + width - 3, y + height - 3); // Bottom
            g2.drawLine(x + width - 3, y + 2, x + width - 3, y + height - 3); // Right

            g2.dispose();
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(3, 3, 3, 3);
        }

        @Override
        public Insets getBorderInsets(Component c, Insets insets) {
            insets.left = insets.top = insets.right = insets.bottom = 3;
            return insets;
        }
    }

    public static javax.swing.border.Border getDockTileBorder(boolean pressedOrSelected) {
        javax.swing.border.Border outer = new ThickBevelBorder(pressedOrSelected);
        javax.swing.border.Border inner = BorderFactory.createEtchedBorder(
                javax.swing.border.EtchedBorder.LOWERED,
                Color.WHITE,
                DARK_SHADOW);
        javax.swing.border.Border spacer = BorderFactory.createEmptyBorder(1, 1, 1, 1);
        javax.swing.border.Border contentPadding = BorderFactory.createEmptyBorder(1, 1, 1, 1);
        return BorderFactory.createCompoundBorder(
                outer,
                BorderFactory.createCompoundBorder(
                        spacer,
                        BorderFactory.createCompoundBorder(inner, contentPadding)));
    }

    public EuroDock(JDesktopPane desktop, java.util.function.BiConsumer<String, List<Element>> groupToggleCallback) {
        this.desktop = desktop;
        this.groupToggleCallback = groupToggleCallback;

        setPreferredSize(new Dimension(80, 0));
        setOpaque(false);
        setLayout(new GridBagLayout());

        // Load configuration from dock.xml
        loadDockConfig();

        // Build UI Components
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.NONE;

        // 1. Clock Tile
        gbc.gridy = 0;
        gbc.weighty = 0;
        gbc.insets = new Insets(6, 6, 4, 6);
        add(new ClockTile(), gbc);

        // 2. Group Buttons
        int gridy = 1;
        for (int i = 0; i < groupElements.size(); i++) {
            Element group = groupElements.get(i);
            String name = group.getAttribute("name");
            String iconName = group.getAttribute("icon");

            GroupButton btn = new GroupButton(name, iconName, group);
            btn.addActionListener(e -> handleGroupClick(btn));
            groupButtons.add(btn);

            gbc.gridy = gridy++;
            gbc.insets = new Insets(0, 6, 4, 6);
            add(btn, gbc);
        }

        // 3. Vertical Spacer (Glue)
        gbc.gridy = gridy++;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.VERTICAL;
        gbc.insets = new Insets(0, 6, 0, 6);
        JPanel spacer = new JPanel();
        spacer.setOpaque(false);
        add(spacer, gbc);

        // 4. Exit Button
        gbc.gridy = gridy++;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.NONE;
        JButton exitBtn = new JButton(EuroIconThemeManager.getIcon("exit", 28, 28));
        exitBtn.setPreferredSize(new Dimension(64, 64));
        exitBtn.setBackground(LIGHT_GREY);
        exitBtn.setBorder(getDockTileBorder(false));
        exitBtn.setFocusPainted(false);
        exitBtn.setMargin(new Insets(0, 0, 0, 0));
        exitBtn.addActionListener(e -> handleExit());
        // Handle physical press feel
        exitBtn.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                exitBtn.setBorder(getDockTileBorder(true));
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                exitBtn.setBorder(getDockTileBorder(false));
            }
        });
        gbc.insets = new Insets(0, 6, 6, 6);
        add(exitBtn, gbc);

        // Setup dynamic check timer for LED active app dots
        Timer activeCheckTimer = new Timer(1000, e -> updateActiveDots());
        activeCheckTimer.start();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        /*
         * Graphics2D g2 = (Graphics2D) g.create();
         * int w = getWidth();
         * int h = getHeight();
         * 
         * // 1. Fill solid charcoal background for the dock body (0 to w-5)
         * g2.setColor(CHARCOAL);
         * g2.fillRect(0, 0, w - 4, h);
         * 
         * // 2. Draw 3D-raised bevel on the top edge of the dock body
         * g2.setColor(Color.WHITE); // Highlight line
         * g2.drawLine(0, 0, w - 5, 0);
         * g2.setColor(new Color(0x3C3C3C)); // Shadow line
         * g2.drawLine(0, 1, w - 5, 1);
         * 
         * // 3. Draw 3D-raised bevel on the right edge of the dock body
         * g2.setColor(Color.WHITE); // Highlight line
         * g2.drawLine(w - 5, 0, w - 5, h);
         * g2.setColor(new Color(0x3C3C3C)); // Outer shadow line
         * g2.drawLine(w - 4, 0, w - 4, h);
         * 
         * // 4. Draw soft drop shadow gradient on the right (w-3 to w-1)
         * g2.setColor(new Color(0, 0, 0, 100)); // 40% black shadow
         * g2.drawLine(w - 3, 0, w - 3, h);
         * g2.setColor(new Color(0, 0, 0, 50)); // 20% black shadow
         * g2.drawLine(w - 2, 0, w - 2, h);
         * g2.setColor(new Color(0, 0, 0, 20)); // 8% black shadow
         * g2.drawLine(w - 1, 0, w - 1, h);
         * 
         * g2.dispose();
         */
    }

    private void loadDockConfig() {
        try {
            File dockFile = new File(System.getProperty("user.home"), ".euroworks/.config/menus/dock.xml");
            if (!dockFile.exists()) {
                // Try to load from classpath fallback
                java.net.URL url = getClass().getClassLoader().getResource("themes/euro/config/menus/dock.xml");
                if (url != null) {
                    dockFile = new File(url.toURI());
                }
            }

            if (dockFile.exists()) {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setFeature("http://xml.org/sax/features/validation", false);
                factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
                DocumentBuilder builder = factory.newDocumentBuilder();
                builder.setEntityResolver(
                        (publicId, systemId) -> new org.xml.sax.InputSource(new java.io.StringReader("")));
                Document doc = builder.parse(dockFile);

                NodeList groups = doc.getElementsByTagName("Group");
                for (int i = 0; i < groups.getLength(); i++) {
                    groupElements.add((Element) groups.item(i));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleGroupClick(GroupButton clickedBtn) {
        boolean wasSelected = clickedBtn.isDockSelected();
        // Deselect all buttons first
        for (GroupButton btn : groupButtons) {
            btn.setDockSelected(false);
        }

        if (!wasSelected) {
            clickedBtn.setDockSelected(true);
            // Collect apps of this group to toggle overlay
            List<Element> apps = new ArrayList<>();
            NodeList appList = clickedBtn.groupEl.getElementsByTagName("App");
            for (int i = 0; i < appList.getLength(); i++) {
                apps.add((Element) appList.item(i));
            }
            groupToggleCallback.accept(clickedBtn.groupName, apps);
        } else {
            // Close group panel
            groupToggleCallback.accept(null, null);
        }
    }

    private void handleExit() {
        int result = JOptionPane.showConfirmDialog(
                this,
                "Möchten Sie EuroWorks wirklich beenden?",
                "Beenden",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        if (result == JOptionPane.YES_OPTION) {
            System.exit(0);
        }
    }

    public void updateActiveIndicators() {
        updateActiveDots();
        repaint();
    }

    private void updateActiveDots() {
        for (GroupButton btn : groupButtons) {
            boolean hasActive = false;
            NodeList apps = btn.groupEl.getElementsByTagName("App");
            JInternalFrame[] frames = desktop.getAllFrames();
            for (int i = 0; i < apps.getLength(); i++) {
                Element app = (Element) apps.item(i);
                String exec = app.getAttribute("exec");
                for (JInternalFrame frame : frames) {
                    if (frame.getClass().getSimpleName().equalsIgnoreCase(exec)) {
                        hasActive = true;
                        break;
                    }
                }
                if (hasActive)
                    break;
            }
            btn.setAppRunning(hasActive);
        }
    }

    // ── Clock Tile Component ─────────────────────────────────────────────────
    private static class ClockTile extends JPanel {
        private String timeStr = "";
        private String dateStr = "";

        public ClockTile() {
            setPreferredSize(new Dimension(64, 64));
            setBackground(LIGHT_GREY);
            setBorder(getDockTileBorder(false));

            Timer timer = new Timer(1000, e -> updateClock());
            timer.start();
            updateClock();
        }

        private void updateClock() {
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            java.time.format.DateTimeFormatter timeFormatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm");
            java.time.format.DateTimeFormatter dateFormatter = java.time.format.DateTimeFormatter.ofPattern("E, d. MMM",
                    java.util.Locale.GERMAN);
            timeStr = now.format(timeFormatter);
            dateStr = now.format(dateFormatter);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            g2.setColor(Color.BLACK);
            // Paint time centered in the top half
            g2.setFont(new Font("Monospaced", Font.BOLD, 13));
            FontMetrics fmTime = g2.getFontMetrics();
            int tx = (getWidth() - fmTime.stringWidth(timeStr)) / 2;
            g2.drawString(timeStr, tx, 24);

            // Paint date centered in the bottom half
            g2.setFont(new Font("Tahoma", Font.PLAIN, 9));
            FontMetrics fmDate = g2.getFontMetrics();
            int dx = (getWidth() - fmDate.stringWidth(dateStr)) / 2;
            g2.drawString(dateStr, dx, 48);

            g2.dispose();
        }
    }

    // ── Group Button Component ───────────────────────────────────────────────
    private static class GroupButton extends JButton {
        private final String groupName;
        private final Element groupEl;
        private boolean isDockSelected = false;
        private boolean isAppRunning = false;

        public GroupButton(String groupName, String iconName, Element groupEl) {
            this.groupName = groupName;
            this.groupEl = groupEl;

            setPreferredSize(new Dimension(64, 64));
            setBackground(LIGHT_GREY);
            setBorder(getDockTileBorder(false));
            setFocusPainted(false);
            setMargin(new Insets(0, 0, 0, 0));

            Icon icon = EuroIconThemeManager.getIcon(iconName, 28, 28);
            setIcon(icon);
            setText(groupName.toUpperCase());
            setHorizontalTextPosition(SwingConstants.CENTER);
            setVerticalTextPosition(SwingConstants.BOTTOM);
            setFont(new Font("Tahoma", Font.BOLD, 9));
            setForeground(Color.BLACK);
            setIconTextGap(1);

            // Hover state feedback
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    setBorder(getDockTileBorder(true));
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (!isDockSelected) {
                        setBorder(getDockTileBorder(false));
                    }
                }
            });
        }

        public String getGroupName() {
            return groupName;
        }

        public boolean isDockSelected() {
            return isDockSelected;
        }

        public void setDockSelected(boolean val) {
            this.isDockSelected = val;
            setBorder(getDockTileBorder(val));
            setBackground(val ? new Color(175, 175, 175) : LIGHT_GREY);
            repaint();
        }

        public void setAppRunning(boolean val) {
            if (this.isAppRunning != val) {
                this.isAppRunning = val;
                repaint();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (isAppRunning) {
                // Paint active indicator (blue LED-like dot in top-right corner)
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0, 150, 255)); // Glowing bright neon blue
                g2.fillOval(getWidth() - 12, 4, 7, 7);
                // Subtle shine/glow inner core
                g2.setColor(Color.WHITE);
                g2.fillOval(getWidth() - 10, 6, 2, 2);
                g2.dispose();
            }
        }
    }
}

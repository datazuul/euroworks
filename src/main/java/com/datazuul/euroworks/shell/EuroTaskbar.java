package com.datazuul.euroworks.shell;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JDesktopPane;
import javax.swing.JInternalFrame;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JToggleButton;
import javax.swing.border.Border;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.datazuul.euroworks.widgets.taskbar.TaskbarClock;

/**
 * Bottom taskbar — styled after the BreadBox/GEOS/PC-GEOS retro desktop
 * (Win95 silver 3D look).
 *
 * Layout (left → right):
 * [≡ Express launcher] | [task toggle buttons ...] | [HH:mm clock]
 */
public class EuroTaskbar extends JPanel {

    // ── palette ─────────────────────────────────────────────────────────────
    private static final Color SILVER = new Color(192, 192, 192);
    private static final Color SHADOW = new Color(128, 128, 128);
    private static final Color DARK = new Color(64, 64, 64);
    private static final Color HILIGHT = Color.WHITE;
    private static final Color SEL_BG = new Color(0, 0, 128); // Win95 blue selection
    private static final Color SEL_FG = Color.WHITE;

    private static final int HEIGHT = 35;

    // ── state ────────────────────────────────────────────────────────────────
    private final JDesktopPane desktop;
    private final JPanel taskArea;
    private final Map<JInternalFrame, AbstractButton> taskButtons = new LinkedHashMap<>();

    private JPopupMenu launcherPopup;
    private ActionListener launchCallback;
    private JButton launcherBtn;
    private long lastPopupDismissedTime = 0;

    public EuroTaskbar(JDesktopPane desktop) {
        this.desktop = desktop;

        setLayout(new BorderLayout());
        setBackground(SILVER);
        setPreferredSize(new Dimension(0, HEIGHT));
        // Paint 3D raised panel in paintComponent — no border needed
        setOpaque(true);

        // ── LEFT: launcher button ────────────────────────────────────────────
        launcherBtn = buildLauncherButton();
        JPanel leftWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 5));
        leftWrap.setBackground(SILVER);
        leftWrap.setOpaque(false);
        leftWrap.add(launcherBtn);
        add(leftWrap, BorderLayout.WEST);

        // ── CENTRE: task buttons ─────────────────────────────────────────────
        taskArea = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 5));
        taskArea.setBackground(SILVER);
        taskArea.setOpaque(false);
        add(taskArea, BorderLayout.CENTER);

        // ── RIGHT: clock ─────────────────────────────────────────────────────
        JPanel rightWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 5));
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
        if (taskButtons.containsKey(frame))
            return;

        JToggleButton btn = buildTaskButton(frame);
        taskButtons.put(frame, btn);
        taskArea.add(btn);
        taskArea.revalidate();
        taskArea.repaint();

        frame.addInternalFrameListener(new InternalFrameAdapter() {
            @Override
            public void internalFrameClosed(InternalFrameEvent e) {
                unregister(frame);
            }

            @Override
            public void internalFrameActivated(InternalFrameEvent e) {
                syncState(frame);
            }

            @Override
            public void internalFrameDeactivated(InternalFrameEvent e) {
                syncState(frame);
            }

            @Override
            public void internalFrameIconified(InternalFrameEvent e) {
                syncState(frame);
            }

            @Override
            public void internalFrameDeiconified(InternalFrameEvent e) {
                syncState(frame);
            }
        });
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private void unregister(JInternalFrame frame) {
        AbstractButton btn = taskButtons.remove(frame);
        if (btn != null) {
            taskArea.remove(btn);
            taskArea.revalidate();
            taskArea.repaint();
        }
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
        JButton btn = new com.datazuul.euroworks.ui.EuroButton("Start");
        btn.setIcon(EuroIconThemeManager.getIcon("start"));
        btn.setFont(new Font("SansSerif", Font.BOLD, 15));
        btn.setForeground(Color.BLACK);
        btn.setBackground(SILVER);
        btn.setPreferredSize(new Dimension(85, 25));
        btn.setMargin(new Insets(2, 4, 2, 6));

        btn.addActionListener(e -> {
            if (System.currentTimeMillis() - lastPopupDismissedTime < 200) {
                btn.setSelected(false);
                return;
            }
            if (launcherPopup == null)
                rebuildLauncherPopup();
            btn.setSelected(true);
            launcherPopup.show(btn, 0, -launcherPopup.getPreferredSize().height);
        });
        return btn;
    }

    private static final Logger logger = LoggerFactory.getLogger(EuroTaskbar.class);

    private void rebuildLauncherPopup() {
        launcherPopup = new JPopupMenu() {
            @Override
            protected void paintBorder(Graphics g) {
                drawRaised((Graphics2D) g, 0, 0, getWidth(), getHeight());
            }
        };
        launcherPopup.setBackground(SILVER);
        launcherPopup.setBorderPainted(true);
        launcherPopup.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));

        launcherPopup.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {
                if (launcherBtn != null) {
                    launcherBtn.setSelected(true);
                }
            }

            @Override
            public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) {
                if (launcherBtn != null) {
                    launcherBtn.setSelected(false);
                }
                lastPopupDismissedTime = System.currentTimeMillis();
            }

            @Override
            public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) {
                if (launcherBtn != null) {
                    launcherBtn.setSelected(false);
                }
                lastPopupDismissedTime = System.currentTimeMillis();
            }
        });

        // Load and parse XML menus from standard user home location
        File menuFile = new File(System.getProperty("user.home"), ".euroworks/.config/menus/menus.xml");
        if (menuFile.exists()) {
            try {
                parseAndBuildMenu(menuFile, launcherPopup);
            } catch (Exception e) {
                logger.error("Failed to parse menu XML: " + menuFile, e);
                buildDefaultMenu();
            }
        } else {
            buildDefaultMenu();
        }
    }

    private void parseAndBuildMenu(File xmlFile, JPopupMenu popup) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setValidating(false);
        try {
            factory.setFeature("http://xml.org/sax/features/validation", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        } catch (Exception e) {
            // ignore
        }
        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setEntityResolver((publicId, systemId) -> new org.xml.sax.InputSource(new java.io.StringReader("")));
        Document doc = builder.parse(xmlFile);
        doc.getDocumentElement().normalize();

        Element root = doc.getDocumentElement();
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                String nodeName = node.getNodeName();
                if ("Menu".equals(nodeName)) {
                    buildSubMenuFromElement((Element) node, popup);
                } else if ("Separator".equals(nodeName)) {
                    popup.addSeparator();
                } else if ("Include".equals(nodeName)) {
                    NodeList includeChildren = node.getChildNodes();
                    for (int j = 0; j < includeChildren.getLength(); j++) {
                        Node incNode = includeChildren.item(j);
                        if (incNode.getNodeType() == Node.ELEMENT_NODE) {
                            if ("Filename".equals(incNode.getNodeName())) {
                                String filename = incNode.getTextContent().trim();
                                DesktopEntry entry = readDesktopEntry(filename);
                                popup.add(buildMenuItem(entry.name, entry.exec, entry.icon));
                            } else if ("Separator".equals(incNode.getNodeName())) {
                                popup.addSeparator();
                            }
                        }
                    }
                }
            }
        }
    }

    private void buildSubMenuFromElement(Element menuElement, Object parentMenu) {
        String name = "";
        NodeList names = menuElement.getElementsByTagName("Name");
        if (names.getLength() > 0) {
            name = names.item(0).getTextContent().trim();
        }

        JMenu subMenu = buildSubMenu(name);

        NodeList children = menuElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                String nodeName = node.getNodeName();
                if ("Menu".equals(nodeName)) {
                    buildSubMenuFromElement((Element) node, subMenu);
                } else if ("Separator".equals(nodeName)) {
                    subMenu.addSeparator();
                } else if ("Include".equals(nodeName)) {
                    NodeList includeChildren = node.getChildNodes();
                    for (int j = 0; j < includeChildren.getLength(); j++) {
                        Node incNode = includeChildren.item(j);
                        if (incNode.getNodeType() == Node.ELEMENT_NODE) {
                            if ("Filename".equals(incNode.getNodeName())) {
                                String filename = incNode.getTextContent().trim();
                                DesktopEntry entry = readDesktopEntry(filename);
                                subMenu.add(buildMenuItem(entry.name, entry.exec, entry.icon));
                            } else if ("Separator".equals(incNode.getNodeName())) {
                                subMenu.addSeparator();
                            }
                        }
                    }
                }
            }
        }

        if (parentMenu instanceof JPopupMenu popup) {
            popup.add(subMenu);
        } else if (parentMenu instanceof JMenu menu) {
            menu.add(subMenu);
        }
    }

    private void buildDefaultMenu() {
        JMenu programsMenu = buildSubMenu("Programs");

        JMenu gamesMenu = buildSubMenu("Games");
        gamesMenu.add(buildMenuItem("EuroMines", "EuroMines", "mines"));
        gamesMenu.add(buildMenuItem("EuroBreakout", "EuroBreakout", "breakout"));
        gamesMenu.add(buildMenuItem("EuroInvaders", "EuroInvaders", "invaders"));
        programsMenu.add(gamesMenu);

        JMenu officeMenu = buildSubMenu("Office");
        officeMenu.add(buildMenuItem("EuroWrite", "EuroWrite", "document"));
        officeMenu.add(buildMenuItem("EuroDraw", "EuroDraw", "paint"));
        officeMenu.add(buildMenuItem("EuroCalc", "EuroCalc", "calc"));
        officeMenu.add(buildMenuItem("EuroFile", "EuroFile", "file"));
        programsMenu.add(officeMenu);

        JMenu organizeMenu = buildSubMenu("Organize");
        organizeMenu.add(buildMenuItem("EuroManager", "EuroManager", "folder"));
        organizeMenu.add(buildMenuItem("EuroDex", "EuroDex", "dex"));
        organizeMenu.add(buildMenuItem("EuroMandelbrot", "EuroMandelbrot", "mandelbrot"));
        organizeMenu.add(buildMenuItem("CD Player", "EuroCDPlayer", "cdplayer"));
        organizeMenu.add(buildMenuItem("EuroScan", "EuroScan", "scan"));
        organizeMenu.add(buildMenuItem("EuroRadio", "EuroRadio", "radio"));
        organizeMenu.add(buildMenuItem("EuroWeb", "EuroWeb", "web"));
        organizeMenu.add(buildMenuItem("EuroSync", "EuroSync", "sync"));
        organizeMenu.addSeparator();
        organizeMenu.add(buildMenuItem("EuroPipes", "EuroPipes", "pipes"));
        organizeMenu.add(buildMenuItem("EuroMaze", "EuroMaze", "maze"));
        organizeMenu.add(buildMenuItem("EuroBezier", "EuroBezier", "bezier"));
        organizeMenu.add(buildMenuItem("EuroStarfield", "EuroStarfield", "starfield"));
        programsMenu.add(organizeMenu);

        launcherPopup.add(programsMenu);
        launcherPopup.addSeparator();

        JMenu settingsMenu = buildSubMenu("Settings");
        settingsMenu.add(buildMenuItem("EuroPreferences", "EuroPreferences", "preferences"));
        launcherPopup.add(settingsMenu);

        launcherPopup.addSeparator();
        launcherPopup.add(buildMenuItem("Shut Down...", "__EXIT__", "exit"));
    }

    private static class DesktopEntry {
        String name;
        String exec;
        String icon;
    }

    private DesktopEntry readDesktopEntry(String filename) {
        File file = new File(System.getProperty("user.home"), ".euroworks/share/applications/" + filename);
        DesktopEntry entry = new DesktopEntry();
        entry.name = filename.replace(".desktop", "");
        entry.exec = entry.name;
        entry.icon = "generic_app";

        if (file.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("Name=")) {
                        entry.name = line.substring(5).trim();
                    } else if (line.startsWith("Exec=")) {
                        entry.exec = line.substring(5).trim();
                    } else if (line.startsWith("Icon=")) {
                        entry.icon = line.substring(5).trim();
                    }
                }
            } catch (Exception e) {
                // ignore
            }
        }
        return entry;
    }

    private String getMenuItemIconKey(String command) {
        if (command == null)
            return "generic_app";
        String cmd = command.toLowerCase();
        if (cmd.contains("cdplayer"))
            return "cdplayer";
        if (cmd.contains("preferences"))
            return "preferences";
        if (cmd.contains("manager"))
            return "folder";
        if (cmd.contains("write"))
            return "document";
        if (cmd.contains("calc"))
            return "calc";
        if (cmd.contains("draw"))
            return "paint";
        if (cmd.contains("mines"))
            return "mines";
        if (cmd.contains("radio"))
            return "radio";
        if (cmd.contains("web"))
            return "web";
        if (cmd.contains("breakout"))
            return "breakout";
        if (cmd.contains("invaders"))
            return "invaders";
        if (cmd.contains("file"))
            return "file";
        if (cmd.contains("dex"))
            return "dex";
        if (cmd.contains("mandelbrot"))
            return "mandelbrot";
        if (cmd.contains("scan"))
            return "scan";
        if (cmd.contains("pipes"))
            return "pipes";
        if (cmd.contains("maze"))
            return "maze";
        if (cmd.contains("bezier"))
            return "bezier";
        if (cmd.contains("starfield"))
            return "starfield";
        if (cmd.contains("__exit__"))
            return "exit";
        return "generic_app";
    }

    private JMenuItem buildMenuItem(String label, String command) {
        return buildMenuItem(label, command, getMenuItemIconKey(command));
    }

    private JMenuItem buildMenuItem(String label, String command, String iconKey) {
        JMenuItem item = new JMenuItem(label) {
            @Override
            protected void paintComponent(Graphics g) {
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

                // Draw theme icon
                Icon itemIcon = EuroIconThemeManager.getIcon(iconKey);
                int ix = 6;
                int iy = (getHeight() - itemIcon.getIconHeight()) / 2;
                itemIcon.paintIcon(this, g2, ix, iy);

                g2.setColor(isArmed() ? SEL_FG : Color.BLACK);
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText(), 32, (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
            }
        };
        item.setFont(new Font("SansSerif", Font.PLAIN, 13));
        item.setBackground(SILVER);
        item.setBorderPainted(false);
        item.setOpaque(false);
        item.setPreferredSize(new Dimension(200, 28));
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
            @Override
            protected void paintComponent(Graphics g) {
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

                // Draw theme folder icon
                Icon folderIcon = EuroIconThemeManager.getIcon("folder");
                int ix = 6;
                int iy = (getHeight() - folderIcon.getIconHeight()) / 2;
                folderIcon.paintIcon(this, g2, ix, iy);

                // Draw menu label text
                g2.setColor(active ? SEL_FG : Color.BLACK);
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText(), 32, (getHeight() + fm.getAscent() - fm.getDescent()) / 2);

                // Submenu right-arrow marker
                int h = getHeight();
                int[] xPoints = { getWidth() - 12, getWidth() - 12, getWidth() - 6 };
                int[] yPoints = { h / 2 - 4, h / 2 + 4, h / 2 };
                g2.fillPolygon(xPoints, yPoints, 3);
                g2.dispose();
            }
        };
        menu.setFont(new Font("SansSerif", Font.PLAIN, 13));
        menu.setBackground(SILVER);
        menu.setBorderPainted(false);
        menu.setOpaque(false);
        menu.setPreferredSize(new Dimension(200, 28));
        menu.getPopupMenu().setBackground(SILVER);
        menu.getPopupMenu().setBorder(new javax.swing.border.AbstractBorder() {
            @Override
            public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
                drawRaised((Graphics2D) g, x, y, w, h);
            }

            @Override
            public Insets getBorderInsets(Component c) {
                return new Insets(3, 3, 3, 3);
            }
        });
        return menu;
    }

    // ── Task buttons ──────────────────────────────────────────────────────────

    private JToggleButton buildTaskButton(JInternalFrame frame) {
        String title = truncate(frame.getTitle(), 20);
        JToggleButton btn = new JToggleButton(title) {
            @Override
            protected void paintComponent(Graphics g) {
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
                if (sel)
                    drawSunken(g2, 0, 0, getWidth(), getHeight());
                else
                    drawRaised(g2, 0, 0, getWidth(), getHeight());

                // Icon and text drawing
                Icon icon = frame.getFrameIcon();
                g2.setFont(getFont());
                g2.setColor(Color.BLACK);
                FontMetrics fm = g2.getFontMetrics();

                int textW = fm.stringWidth(getText());
                int iconW = (icon != null) ? icon.getIconWidth() : 0;
                int gap = (icon != null) ? 4 : 0;
                int totalW = iconW + gap + textW;

                int startX = (getWidth() - totalW) / 2 + (sel ? 1 : 0);
                int centerY = getHeight() / 2 + (sel ? 1 : 0);

                if (icon != null) {
                    int iy = centerY - icon.getIconHeight() / 2;
                    icon.paintIcon(this, g2, startX, iy);
                }

                int tx = startX + iconW + gap;
                int ty = centerY + (fm.getAscent() - fm.getDescent()) / 2;
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
        btn.setPreferredSize(new Dimension(120, 25));
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
            } catch (Exception ex) {
                ex.printStackTrace();
            }
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
        g.drawLine(x, y, x + w - 2, y); // top
        g.drawLine(x, y, x, y + h - 2); // left
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
            @Override
            public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
                drawRaised((Graphics2D) g, x, y, w, h);
            }

            @Override
            public Insets getBorderInsets(Component c) {
                return new Insets(2, 4, 2, 4);
            }
        };
    }

    private static Border buildSunkenBorder() {
        return new javax.swing.border.AbstractBorder() {
            @Override
            public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
                drawSunken((Graphics2D) g, x, y, w, h);
            }

            @Override
            public Insets getBorderInsets(Component c) {
                return new Insets(2, 4, 2, 4);
            }
        };
    }

    /** 1-pixel raised top + sunken-panel bottom for the taskbar strip itself. */
    private static Border buildRaisedTopBorder() {
        return new javax.swing.border.AbstractBorder() {
            @Override
            public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
                g.setColor(HILIGHT);
                g.drawLine(x, y, x + w - 1, y); // top white
                g.setColor(SHADOW);
                g.drawLine(x, y + 1, x + w - 1, y + 1); // second line shadow
            }

            @Override
            public Insets getBorderInsets(Component c) {
                return new Insets(2, 0, 0, 0);
            }
        };
    }

    // ── utils ─────────────────────────────────────────────────────────────────

    private static String truncate(String s, int max) {
        if (s == null)
            return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}

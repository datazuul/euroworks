package com.datazuul.euroworks.shell;

import javax.swing.*;
import org.w3c.dom.Element;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

public class EuroGroupPanel extends JPanel {

    private final java.util.function.Consumer<String> launchCallback;

    public EuroGroupPanel(String title, List<Element> appNodes, java.util.function.Consumer<String> launchCallback) {
        this.launchCallback = launchCallback;

        // Semi-transparent charcoal panel
        setBackground(new Color(30, 30, 30, 200));
        setOpaque(false); // custom painted rounded background
        setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));
        setLayout(new FlowLayout(FlowLayout.LEFT, 16, 12));

        for (Element appNode : appNodes) {
            String name = appNode.getAttribute("name");
            String exec = appNode.getAttribute("exec");
            String iconName = appNode.getAttribute("icon");

            // App button styled as desktop shortcut
            JButton btn = new JButton();
            btn.setPreferredSize(new Dimension(84, 84));
            btn.setContentAreaFilled(false);
            btn.setBorderPainted(false);
            btn.setFocusPainted(false);

            Icon icon = EuroIconThemeManager.getIcon(iconName, 36, 36);
            btn.setIcon(icon);
            btn.setText(name);
            btn.setHorizontalTextPosition(SwingConstants.CENTER);
            btn.setVerticalTextPosition(SwingConstants.BOTTOM);
            btn.setForeground(Color.WHITE);
            btn.setFont(new Font("Tahoma", Font.PLAIN, 11));

            // Launch on click
            btn.addActionListener(e -> launchCallback.accept(exec));

            // Custom hover outline feedback
            btn.addMouseListener(new MouseAdapter() {
                private boolean isHover = false;
                @Override
                public void mouseEntered(MouseEvent e) {
                    isHover = true;
                    btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    btn.repaint();
                }
                @Override
                public void mouseExited(MouseEvent e) {
                    isHover = false;
                    btn.repaint();
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    btn.repaint();
                }
            });

            add(btn);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // Fill charcoal background panel
        g2.setColor(getBackground());
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
        
        // Draw thin border
        g2.setColor(new Color(80, 80, 80));
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
        
        g2.dispose();
        super.paintComponent(g);
    }
}

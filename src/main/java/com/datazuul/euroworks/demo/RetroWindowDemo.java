package com.datazuul.euroworks.demo;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

public class RetroWindowDemo {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(RetroWindowDemo::createUI);
    }

    private static void createUI() {
        JFrame frame = new JFrame();
        frame.setUndecorated(true);
        frame.setSize(500, 350);
        frame.setLocationRelativeTo(null);

        JPanel root = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);

                // Hintergrund
                g.setColor(new Color(55, 55, 55));
                g.fillRect(0, 0, getWidth(), getHeight());

                // Breiter 3D-Rahmen
                draw3DBorder(g, 0, 0, getWidth(), getHeight());

                // Schwarze Innenkante
                g.setColor(Color.BLACK);
                g.drawRect(4, 4, getWidth() - 8, getHeight() - 8);

                // Draw black border (1px)
                g.setColor(Color.BLACK);
                g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
            }
        };
        root.setLayout(new BorderLayout());

        // Titelbar
        JPanel titleBar = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);

                // Titelbar Hintergrund heller
                g.setColor(new Color(95, 95, 95));
                g.fillRect(0, 0, getWidth(), getHeight());

                // 3D-Rahmen innen
                draw3DBorder(g, 0, 0, getWidth(), getHeight());

                // Titeltext
                g.setColor(Color.WHITE);
                g.setFont(new Font("SansSerif", Font.BOLD, 14));
                g.drawString("APPLICATIONS", 10, 18);
            }
        };
        titleBar.setPreferredSize(new Dimension(0, 32));

        // Dragging
        final Point dragOffset = new Point();
        titleBar.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                dragOffset.x = e.getX();
                dragOffset.y = e.getY();
            }
        });
        titleBar.addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseDragged(MouseEvent e) {
                Point p = e.getLocationOnScreen();
                frame.setLocation(p.x - dragOffset.x, p.y - dragOffset.y);
            }
        });

        // Close Button
        /*
         * JButton close = new JButton("X");
         * close.setFocusable(false);
         * close.setMargin(new Insets(0, 6, 0, 6));
         * close.setBackground(new Color(140, 40, 40));
         * close.setForeground(Color.WHITE);
         * close.addActionListener(e -> System.exit(0));
         * titleBar.setLayout(new BorderLayout());
         * titleBar.add(close, BorderLayout.EAST);
         */
        root.add(titleBar, BorderLayout.NORTH);
        frame.setContentPane(root);
        frame.setVisible(true);
    }

    private static void draw3DBorder(Graphics g, int x, int y, int w, int h) {
        // Breiter heller Rand oben/links (4px)
        g.setColor(new Color(139, 140, 144)); // #8b8c90
        g.fillRect(x, y, w, 2); // oben
        g.fillRect(x, y, 2, h); // links

        // Breiter dunkler Rand unten/rechts (4px)
        g.setColor(new Color(30, 30, 30));
        g.fillRect(x, y + h - 4, w, 4); // unten
        g.fillRect(x + w - 4, y, 4, h); // rechts
    }
}

package com.datazuul.euroworks.apps;

import javax.swing.*;
import java.awt.*;

public class EuroAppFrame extends JInternalFrame {

    public EuroAppFrame(String title) {
        // title, resizable, closable, maximizable, iconifiable
        super(title, true, true, true, true);

        // Default size and constraints
        setSize(400, 300);
        setMinimumSize(new Dimension(200, 150));
        setVisible(true);

        // Set look & feel title bar icon
        setFrameIcon(com.datazuul.euroworks.shell.EuroIconThemeManager.getIcon(getIconThemeKey()));
    }

    public String getIconThemeKey() {
        String title = getTitle().toLowerCase();
        if (title.contains("cd player")) return "cdplayer";
        if (title.contains("preferences")) return "preferences";
        if (title.contains("manager") || title.contains("commander")) return "folder";
        if (title.contains("write")) return "document";
        if (title.contains("calc")) return "calc";
        if (title.contains("draw") || title.contains("paint")) return "paint";
        if (title.contains("mines")) return "mines";
        if (title.contains("radio")) return "radio";
        if (title.contains("web")) return "web";
        if (title.contains("tv")) return "tv";
        if (title.contains("news")) return "news";
        if (title.contains("breakout")) return "breakout";
        if (title.contains("invaders")) return "invaders";
        if (title.contains("frogger")) return "frogger";
        if (title.contains("missile") || title.contains("command")) return "missilecommand";
        if (title.contains("asteroids")) return "asteroids";
        if (title.contains("phoenix")) return "phoenix";
        if (title.contains("qix")) return "qix";
        if (title.contains("world")) return "world";
        if (title.contains("tetris")) return "tetris";
        if (title.contains("space")) return "space";
        if (title.contains("artillery")) return "artillery";
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
        setFrameIcon(com.datazuul.euroworks.shell.EuroIconThemeManager.getIcon(getIconThemeKey()));
    }
}

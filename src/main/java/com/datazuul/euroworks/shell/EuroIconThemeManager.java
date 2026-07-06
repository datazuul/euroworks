package com.datazuul.euroworks.shell;

import com.datazuul.euroworks.apps.EuroPreferencesStore;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages the active icon theme for EuroWorks.
 * Dynamically resolves icons in SVG or PNG formats, caching them for performance.
 * Falls back to programmatic vector drawings if resource files are missing.
 */
public class EuroIconThemeManager {

    private static final Map<String, Icon> iconCache = new HashMap<>();
    private static final Map<String, String[]> CANDIDATES = new HashMap<>();

    static {
        CANDIDATES.put("folder", new String[] { "folder", "places/folder" });
        CANDIDATES.put("computer", new String[] { "computer", "devices/computer", "places/user-desktop", "devices/video-display" });
        CANDIDATES.put("document", new String[] { "document", "apps/accessories-text-editor", "mimetypes/text-x-generic" });
        CANDIDATES.put("preferences", new String[] { "preferences", "apps/preferences-desktop-theme", "apps/preferences-system", "apps/preferences-system-session" });
        CANDIDATES.put("cdplayer", new String[] { "cdplayer", "devices/media-optical", "devices/drive-optical", "apps/multimedia-player" });
        CANDIDATES.put("radio", new String[] { "radio", "devices/audio-card", "apps/multimedia-player" });
        CANDIDATES.put("web", new String[] { "web", "apps/internet-web-browser" });
        CANDIDATES.put("mines", new String[] { "mines", "apps/gnome-mines", "apps/mines", "apps/input-gaming" });
        CANDIDATES.put("calc", new String[] { "calc", "apps/accessories-calculator", "apps/calc" });
        CANDIDATES.put("paint", new String[] { "paint", "apps/gimp", "apps/paint", "apps/accessories-paint" });
        CANDIDATES.put("generic_app", new String[] { "generic_app", "apps/utilities-terminal", "apps/application-default-icon" });
        CANDIDATES.put("breakout", new String[] { "breakout", "apps/input-gaming", "apps/gnome-breakout" });
        CANDIDATES.put("invaders", new String[] { "invaders", "apps/input-gaming", "apps/space-invaders" });
        CANDIDATES.put("file", new String[] { "file", "apps/system-file-manager", "places/folder" });
        CANDIDATES.put("dex", new String[] { "dex", "apps/office-address-book", "apps/accessories-dictionary" });
        CANDIDATES.put("mandelbrot", new String[] { "mandelbrot", "apps/preferences-desktop-wallpaper", "apps/accessories-calculator" });
        CANDIDATES.put("scan", new String[] { "scan", "devices/scanner", "apps/accessories-scanner" });
        CANDIDATES.put("pipes", new String[] { "pipes", "apps/preferences-desktop-screensaver" });
        CANDIDATES.put("maze", new String[] { "maze", "apps/input-gaming" });
        CANDIDATES.put("bezier", new String[] { "bezier", "apps/accessories-calculator" });
        CANDIDATES.put("starfield", new String[] { "starfield", "apps/preferences-desktop-screensaver" });
        CANDIDATES.put("exit", new String[] { "exit", "actions/system-shutdown", "actions/exit" });
        CANDIDATES.put("sync", new String[] { "sync", "apps/system-software-update", "actions/sync" });
        CANDIDATES.put("eurosync", new String[] { "sync", "apps/system-software-update", "actions/sync" });
        CANDIDATES.put("start", new String[] { "start", "apps/application-default-icon" });
        CANDIDATES.put("work", new String[] { "work", "places/folder" });
        CANDIDATES.put("games", new String[] { "games", "apps/input-gaming" });
        CANDIDATES.put("net", new String[] { "web", "apps/internet-web-browser" });
        CANDIDATES.put("apps", new String[] { "apps", "apps/utilities-terminal" });
        CANDIDATES.put("tools", new String[] { "tools", "apps/preferences-system" });
        CANDIDATES.put("system", new String[] { "computer", "devices/computer" });
    }

    /** Clear the cached icons (e.g., when the active theme is changed). */
    public static synchronized void clearCache() {
        iconCache.clear();
    }

    /**
     * Retrieves an icon by name.
     */
    public static synchronized Icon getIcon(String name) {
        return getIcon(name, 16, 16);
    }

    /**
     * Retrieves an icon by name and custom size. Matches the active theme name.
     * Looks for name.svg first, then name.png, and falls back to vector icons.
     */
    public static synchronized Icon getIcon(String name, int width, int height) {
        String activeTheme = EuroPreferencesStore.getIconThemeName();
        if ("vector".equalsIgnoreCase(activeTheme)) {
            return getFallbackIcon(name);
        }

        String cacheKey = activeTheme + ":" + name + ":" + width + "x" + height;
        if (iconCache.containsKey(cacheKey)) {
            return iconCache.get(cacheKey);
        }

        Icon resolved = loadThemeIcon(activeTheme, name, width, height);
        iconCache.put(cacheKey, resolved);
        return resolved;
    }

    private static Icon loadThemeIcon(String theme, String name, int width, int height) {
        String[] paths = CANDIDATES.get(name.toLowerCase());
        if (paths == null) {
            paths = new String[] { name };
        }

        String userHome = System.getProperty("user.home");
        File externalIconBase = new File(userHome, ".euroworks/share/icons/" + theme);

        for (String path : paths) {
            // 1. Try external SVG first
            File extSvg = new File(externalIconBase, "icons/" + path + ".svg");
            if (extSvg.exists()) {
                try {
                    com.formdev.flatlaf.extras.FlatSVGIcon svgIcon = new com.formdev.flatlaf.extras.FlatSVGIcon(extSvg);
                    return svgIcon.derive(width, height);
                } catch (Throwable t) {
                    // ignore and fallback to classpath
                }
            }

            // 2. Try classpath SVG
            try {
                String svgPath = "themes/" + theme + "/icons/" + path + ".svg";
                URL url = EuroIconThemeManager.class.getClassLoader().getResource(svgPath);
                if (url != null) {
                    return new com.formdev.flatlaf.extras.FlatSVGIcon(svgPath, width, height);
                }
            } catch (Throwable t) {
                // ignore
            }

            // 3. Try external PNG
            File extPng = new File(externalIconBase, "icons/" + path + ".png");
            if (extPng.exists()) {
                try {
                    ImageIcon img = new ImageIcon(extPng.getAbsolutePath());
                    return new ImageIcon(img.getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH));
                } catch (Throwable t) {
                    // ignore
                }
            }

            // 4. Try classpath PNG
            try {
                String pngPath = "/themes/" + theme + "/icons/" + path + ".png";
                URL url = EuroIconThemeManager.class.getResource(pngPath);
                if (url != null) {
                    ImageIcon img = new ImageIcon(url);
                    return new ImageIcon(img.getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH));
                }
            } catch (Exception e) {
                // ignore
            }
        }

        // 5. System built-in vector fallback
        return getFallbackIcon(name);
    }

    private static Icon getFallbackIcon(String name) {
        if ("folder".equalsIgnoreCase(name)) {
            return new FolderFallbackIcon();
        }
        if ("sync".equalsIgnoreCase(name) || "eurosync".equalsIgnoreCase(name)) {
            return new SyncFallbackIcon();
        }
        return new GenericAppFallbackIcon();
    }

    // ── Fallback Icon Implementations ────────────────────────────────────────

    private static class FolderFallbackIcon implements Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            int ix = x + (getIconWidth() - 12) / 2;
            int iy = y + (getIconHeight() - 10) / 2;

            // Draw back tab and main border outline in dark brown/yellow
            g2.setColor(new Color(150, 110, 40));
            g2.drawRect(ix, iy, 4, 2);
            g2.drawRect(ix, iy + 2, 11, 7);

            // Fill main body with retro yellow/cream
            g2.setColor(new Color(252, 210, 95));
            g2.fillRect(ix + 1, iy + 1, 3, 1);
            g2.fillRect(ix + 1, iy + 3, 10, 6);

            // Front flap outline crease
            g2.setColor(new Color(200, 150, 50));
            g2.drawLine(ix + 1, iy + 4, ix + 10, iy + 4);
            g2.dispose();
        }

        @Override public int getIconWidth() { return 16; }
        @Override public int getIconHeight() { return 16; }
    }

    private static class GenericAppFallbackIcon implements Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            int ix = x + (getIconWidth() - 12) / 2;
            int iy = y + (getIconHeight() - 12) / 2;

            g2.setColor(Color.DARK_GRAY);
            g2.drawRect(ix, iy, 11, 11);
            g2.setColor(Color.WHITE);
            g2.fillRect(ix + 1, iy + 1, 10, 10);
            g2.setColor(new Color(0, 90, 110)); // Steel blue title bar of mini window
            g2.fillRect(ix + 2, iy + 2, 8, 3);
            g2.dispose();
        }

        @Override public int getIconWidth() { return 16; }
        @Override public int getIconHeight() { return 16; }
    }

    private static class SyncFallbackIcon implements Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            int ix = x + (getIconWidth() - 12) / 2;
            int iy = y + (getIconHeight() - 12) / 2;

            g2.setColor(new Color(0, 100, 100)); // Retro Teal
            g2.drawArc(ix, iy, 11, 11, 45, 270);
            
            // Arrow heads
            g2.drawLine(ix + 8, iy + 2, ix + 10, iy);
            g2.drawLine(ix + 8, iy + 2, ix + 8, iy + 5);
            
            g2.drawLine(ix + 2, iy + 8, ix, iy + 10);
            g2.drawLine(ix + 2, iy + 8, ix + 2, iy + 5);
            
            g2.dispose();
        }

        @Override public int getIconWidth() { return 16; }
        @Override public int getIconHeight() { return 16; }
    }
}

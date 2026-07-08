package com.datazuul.euroworks.apps.euroradio;

import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.Icon;

/**
 * Loads SVG flag icons from the classpath assets (images/flags/).
 * Caches loaded icons in memory to optimize JTree rendering performance.
 */
public class FlagIconLoader {
    private static final Map<String, Icon> cache = new ConcurrentHashMap<>();

    /**
     * Gets the SVG flag icon for a country code.
     * @param countryCode The 2-letter ISO country code (case-insensitive).
     * @return The 16x12 SVG Icon, or null if the country code is empty or no flag file is found.
     */
    public static Icon getFlagIcon(String countryCode) {
        if (countryCode == null || countryCode.strip().isEmpty()) {
            return null;
        }
        String code = countryCode.trim().toLowerCase();
        return cache.computeIfAbsent(code, c -> {
            try {
                String svgPath = "images/flags/" + c + ".svg";
                URL url = FlagIconLoader.class.getClassLoader().getResource(svgPath);
                if (url != null) {
                    // Standard JTree row height is usually 16-20px, so 16x12 is a perfect flag aspect ratio.
                    return new com.formdev.flatlaf.extras.FlatSVGIcon(svgPath, 16, 12);
                }
            } catch (Exception e) {
                System.err.println("EuroRadio: Failed to load flag SVG for " + c + ": " + e.getMessage());
            }
            return null;
        });
    }
}

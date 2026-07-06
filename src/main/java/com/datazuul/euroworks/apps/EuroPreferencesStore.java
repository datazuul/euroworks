package com.datazuul.euroworks.apps;

import java.io.*;
import java.util.prefs.Preferences;

/**
 * Persists system settings using the Java Preferences API,
 * backing them up/restoring them to and from ~/.euroworks/preferences.xml.
 */
public class EuroPreferencesStore {
    private static final String PREF_DIR_NAME = ".euroworks";
    private static final String PREF_FILE_NAME = "preferences.xml";

    public static Preferences getPrefs() {
        return Preferences.userNodeForPackage(EuroPreferencesStore.class);
    }

    public static void load() {
        File dir = new File(System.getProperty("user.home"), PREF_DIR_NAME);
        File file = new File(dir, PREF_FILE_NAME);
        if (file.exists()) {
            try (InputStream is = new FileInputStream(file)) {
                Preferences.importPreferences(is);
            } catch (Exception e) {
                System.err.println("Could not import preferences: " + e.getMessage());
            }
        }
    }

    public static void save() {
        File dir = new File(System.getProperty("user.home"), PREF_DIR_NAME);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File file = new File(dir, PREF_FILE_NAME);
        try (OutputStream os = new FileOutputStream(file)) {
            getPrefs().exportNode(os);
        } catch (Exception e) {
            System.err.println("Could not export preferences: " + e.getMessage());
        }
    }

    // Convenience getters and setters
    public static int getDesktopColorIndex() {
        return getPrefs().getInt("desktopColorIndex", 1);
    }

    public static void setDesktopColorIndex(int index) {
        getPrefs().putInt("desktopColorIndex", index);
    }

    public static boolean isOutlineDragging() {
        return getPrefs().getBoolean("outlineDragging", true);
    }

    public static void setOutlineDragging(boolean val) {
        getPrefs().putBoolean("outlineDragging", val);
    }

    public static boolean isSoundEnabled() {
        return getPrefs().getBoolean("enableSound", true);
    }

    public static void setSoundEnabled(boolean val) {
        getPrefs().putBoolean("enableSound", val);
    }

    public static boolean isScreensaverEnabled() {
        return getPrefs().getBoolean("enableScreensaver", true);
    }

    public static void setScreensaverEnabled(boolean val) {
        getPrefs().putBoolean("enableScreensaver", val);
    }

    public static String getScreensaverName() {
        return getPrefs().get("screensaverName", "EuroPipes");
    }

    public static void setScreensaverName(String name) {
        getPrefs().put("screensaverName", name);
    }

    public static int getScreensaverTimeout() {
        return getPrefs().getInt("screensaverTimeout", 60);
    }

    public static void setScreensaverTimeout(int seconds) {
        getPrefs().putInt("screensaverTimeout", seconds);
    }

    public static String getIconThemeName() {
        return getPrefs().get("iconThemeName", "euro");
    }

    public static void setIconThemeName(String name) {
        getPrefs().put("iconThemeName", name);
    }
}

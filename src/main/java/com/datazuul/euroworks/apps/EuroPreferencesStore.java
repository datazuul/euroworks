package com.datazuul.euroworks.apps;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.prefs.Preferences;
import java.util.List;

/**
 * Persists system settings using the Java Preferences API,
 * backing them up/restoring them to and from ~/.euroworks/preferences.xml.
 */
public class EuroPreferencesStore {
    private static final String PREF_DIR_NAME = ".euroworks";
    private static final String PREF_FILE_NAME = "preferences.xml";
    private static final String KEY_NETWORK_PROXY_ENABLED = "networkProxyEnabled";
    private static final String KEY_NETWORK_PROXY_HOST = "networkProxyHost";
    private static final String KEY_NETWORK_PROXY_PORT = "networkProxyPort";

    private static final NetworkProxySelector NETWORK_PROXY_SELECTOR = new NetworkProxySelector();

    static {
        ProxySelector.setDefault(NETWORK_PROXY_SELECTOR);
    }

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
        applyNetworkProxySettings();
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

    public static void applyNetworkProxySettings() {
        boolean enabled = isNetworkProxyEnabled();
        String host = getNetworkProxyHost().trim();
        int port = getNetworkProxyPort();
        boolean proxyConfigured = enabled && !host.isEmpty() && port > 0;

        NETWORK_PROXY_SELECTOR.update(proxyConfigured, host, port);

        if (proxyConfigured) {
            System.setProperty("http.proxyHost", host);
            System.setProperty("http.proxyPort", Integer.toString(port));
            System.setProperty("https.proxyHost", host);
            System.setProperty("https.proxyPort", Integer.toString(port));
        } else {
            clearProxyProperty("http.proxyHost");
            clearProxyProperty("http.proxyPort");
            clearProxyProperty("https.proxyHost");
            clearProxyProperty("https.proxyPort");
        }
    }

    private static void clearProxyProperty(String key) {
        System.clearProperty(key);
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

    public static String getLookAndFeelClass() {
        return getPrefs().get("lookAndFeelClass", "org.pushingpixels.radiance.theming.api.skin.RadianceGraphiteChalkLookAndFeel");
    }

    public static void setLookAndFeelClass(String className) {
        getPrefs().put("lookAndFeelClass", className);
    }

    public static boolean isNetworkProxyEnabled() {
        return getPrefs().getBoolean(KEY_NETWORK_PROXY_ENABLED, false);
    }

    public static void setNetworkProxyEnabled(boolean enabled) {
        getPrefs().putBoolean(KEY_NETWORK_PROXY_ENABLED, enabled);
    }

    public static String getNetworkProxyHost() {
        return getPrefs().get(KEY_NETWORK_PROXY_HOST, "");
    }

    public static void setNetworkProxyHost(String host) {
        getPrefs().put(KEY_NETWORK_PROXY_HOST, host == null ? "" : host);
    }

    public static int getNetworkProxyPort() {
        return getPrefs().getInt(KEY_NETWORK_PROXY_PORT, 8080);
    }

    public static void setNetworkProxyPort(int port) {
        getPrefs().putInt(KEY_NETWORK_PROXY_PORT, port);
    }

    private static final class NetworkProxySelector extends ProxySelector {
        private volatile boolean enabled;
        private volatile String host = "";
        private volatile int port;

        void update(boolean enabled, String host, int port) {
            this.enabled = enabled;
            this.host = host == null ? "" : host.trim();
            this.port = port;
        }

        @Override
        public List<Proxy> select(URI uri) {
            if (!enabled || host.isEmpty() || port <= 0 || uri == null) {
                return List.of(Proxy.NO_PROXY);
            }

            String scheme = uri.getScheme();
            if (scheme == null) {
                return List.of(Proxy.NO_PROXY);
            }

            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                return List.of(Proxy.NO_PROXY);
            }

            return List.of(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port)));
        }

        @Override
        public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
            // No-op.
        }
    }
}

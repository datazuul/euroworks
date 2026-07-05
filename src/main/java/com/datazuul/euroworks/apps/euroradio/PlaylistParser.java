package com.datazuul.euroworks.apps.euroradio;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses playlist formats (.pls and .m3u) to extract direct stream URLs.
 */
public class PlaylistParser {
    public static final int MAX_LINES = 100;

    private PlaylistParser() {
    }

    /**
     * Parses the playlist at playlistURL and returns all URLs contained within it.
     */
    public static List<URL> parse(URL playlistURL) {
        List<URL> urls = new ArrayList<>();
        InputStream inputStream = null;

        try {
            URLConnection connection = playlistURL.openConnection();
            connection.setAllowUserInteraction(false);
            connection.setUseCaches(false);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("User-Agent", "EuroWorks-EuroRadio/1.0");
            connection.connect();

            inputStream = connection.getInputStream();
            String contentType = connection.getContentType();

            boolean couldBePLS = true;
            boolean couldBeM3U = true;

            if (contentType != null) {
                String type = contentType.toLowerCase();
                couldBePLS = type.contains("scpls") || type.contains("playlist")
                        || playlistURL.getPath().toLowerCase().endsWith(".pls");
                couldBeM3U = type.contains("mpegurl") || type.contains("mpegurl")
                        || playlistURL.getPath().toLowerCase().endsWith(".m3u");
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            int lineCount = 0;

            while ((line = reader.readLine()) != null && lineCount++ < MAX_LINES) {
                line = line.trim();
                if (line.isEmpty())
                    continue;

                if (couldBePLS) {
                    if (line.toLowerCase().startsWith("file")) {
                        int eq = line.indexOf('=');
                        if (eq != -1) {
                            String urlStr = line.substring(eq + 1).trim();
                            if (urlStr.toLowerCase().startsWith("http://")
                                    || urlStr.toLowerCase().startsWith("https://")) {
                                try {
                                    urls.add(new URL(urlStr));
                                } catch (MalformedURLException ignored) {
                                }
                            }
                        }
                    }
                }

                if (couldBeM3U) {
                    if (line.toLowerCase().startsWith("http://") || line.toLowerCase().startsWith("https://")) {
                        try {
                            urls.add(new URL(line));
                        } catch (MalformedURLException ignored) {
                        }
                    }
                }
            }
        } catch (IOException ex) {
            System.err.println("EuroRadio: Failed to parse playlist: " + ex.getMessage());
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException ignored) {
                }
            }
        }
        return urls;
    }
}

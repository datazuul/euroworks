package com.datazuul.euroworks.apps.euroradio;

import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Client for the free Radio-Browser API (radio-browser.info).
 * Discovers servers dynamically using DNS SRV/A-records lookup and executes
 * queries.
 */
public class RadioBrowserClient {
    private static final String DYNAMIC_DNS_HOST = "all.api.radio-browser.info";
    private static final String DEFAULT_FALLBACK_HOST = "de1.api.radio-browser.info";
    private static final String USER_AGENT = "EuroWorks-EuroRadio/1.0";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private String resolvedBaseUrl = null;

    public RadioBrowserClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Resolves an available radio-browser.info API server dynamically using DNS
     * lookup.
     * Caches the resolved URL. Falls back to a reliable static host on failure.
     */
    private synchronized String getBaseUrl() {
        if (resolvedBaseUrl != null) {
            return resolvedBaseUrl;
        }

        try {
            InetAddress[] addresses = InetAddress.getAllByName(DYNAMIC_DNS_HOST);
            if (addresses != null && addresses.length > 0) {
                // Randomize server selection to balance load
                List<InetAddress> list = new ArrayList<>(List.of(addresses));
                Collections.shuffle(list);
                for (InetAddress addr : list) {
                    try {
                        String canonical = addr.getCanonicalHostName();
                        // If it successfully returns a radio-browser domain, use it
                        if (canonical.contains("radio-browser.info")) {
                            resolvedBaseUrl = "https://" + canonical;
                            System.out.println("EuroRadio: Dynamically resolved API server to " + resolvedBaseUrl);
                            return resolvedBaseUrl;
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception ex) {
            System.err.println("EuroRadio: DNS resolution of dynamic API hosts failed: " + ex.getMessage());
        }

        resolvedBaseUrl = "https://" + DEFAULT_FALLBACK_HOST;
        System.out.println("EuroRadio: Using fallback static API server: " + resolvedBaseUrl);
        return resolvedBaseUrl;
    }

    /**
     * Searches for stations by name, tag, or country.
     */
    public List<RadioStation> searchStations(String query, String tag, String country, int limit) {
        try {
            StringBuilder sb = new StringBuilder(getBaseUrl() + "/json/stations/search?limit=" + limit
                    + "&hidebroken=true&order=clickcount&reverse=true");
            if (query != null && !query.strip().isEmpty()) {
                sb.append("&name=").append(URLEncoder.encode(query.trim(), StandardCharsets.UTF_8));
            }
            if (tag != null && !tag.strip().isEmpty()) {
                sb.append("&tag=").append(URLEncoder.encode(tag.trim(), StandardCharsets.UTF_8));
            }
            if (country != null && !country.strip().isEmpty()) {
                sb.append("&country=").append(URLEncoder.encode(country.trim(), StandardCharsets.UTF_8));
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(sb.toString()))
                    .header("User-Agent", USER_AGENT)
                    .timeout(Duration.ofSeconds(6))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return objectMapper.readValue(response.body(), new TypeReference<List<RadioStation>>() {
                });
            }
        } catch (Exception ex) {
            System.err.println("EuroRadio: API search failed: " + ex.getMessage());
        }
        return Collections.emptyList();
    }

    /**
     * Gets the top clicked popular stations.
     */
    public List<RadioStation> getPopularStations(int limit) {
        try {
            String url = getBaseUrl() + "/json/stations/topclick/" + limit + "?hidebroken=true";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", USER_AGENT)
                    .timeout(Duration.ofSeconds(6))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return objectMapper.readValue(response.body(), new TypeReference<List<RadioStation>>() {
                });
            }
        } catch (Exception ex) {
            System.err.println("EuroRadio: API popular list fetch failed: " + ex.getMessage());
        }
        return Collections.emptyList();
    }

    /**
     * Reports a stream click (plays) back to the server.
     */
    public void reportClick(String stationuuid) {
        if (stationuuid == null || stationuuid.strip().isEmpty()) {
            return;
        }
        // Run in background to not block UI
        Thread.startVirtualThread(() -> {
            try {
                String url = getBaseUrl() + "/json/url/" + stationuuid;
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("User-Agent", USER_AGENT)
                        .timeout(Duration.ofSeconds(4))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build();

                httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            } catch (Exception ignored) {
            }
        });
    }
}

package com.datazuul.euroworks.apps.euroradio;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import de.sfuhrm.radiobrowser4j.RadioBrowser;
import de.sfuhrm.radiobrowser4j.ConnectionParams;
import de.sfuhrm.radiobrowser4j.EndpointDiscovery;
import de.sfuhrm.radiobrowser4j.FieldName;
import de.sfuhrm.radiobrowser4j.ListParameter;
import de.sfuhrm.radiobrowser4j.AdvancedSearch;
import de.sfuhrm.radiobrowser4j.Station;
import de.sfuhrm.radiobrowser4j.Limit;
import de.sfuhrm.radiobrowser4j.SearchMode;

/**
 * Client for the free Radio-Browser API (radio-browser.info).
 * Uses the radiobrowser4j library under the hood.
 */
public class RadioBrowserClient {
    private static final String USER_AGENT = "EuroWorks-EuroRadio/1.0";

    private final RadioBrowser radioBrowser;
    private final String resolvedBaseUrl;
    private final HttpClient httpClient;

    public RadioBrowserClient() {
        String discoveredUrl = null;
        try {
            java.util.Optional<String> endpoint = new EndpointDiscovery(USER_AGENT).discover();
            if (endpoint.isPresent()) {
                discoveredUrl = endpoint.get();
            }
        } catch (Exception ex) {
            System.err.println("EuroRadio: Endpoint discovery failed: " + ex.getMessage());
        }

        if (discoveredUrl == null) {
            discoveredUrl = "https://de1.api.radio-browser.info";
            System.out.println("EuroRadio: Using fallback static API server: " + discoveredUrl);
        } else {
            System.out.println("EuroRadio: Dynamically resolved API server to " + discoveredUrl);
        }

        this.resolvedBaseUrl = discoveredUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        this.radioBrowser = new RadioBrowser(
                ConnectionParams.builder()
                        .apiUrl(resolvedBaseUrl)
                        .userAgent(USER_AGENT)
                        .timeout(5000)
                        .build()
        );
    }

    private RadioStation mapToRadioStation(Station station) {
        String uuidStr = "";
        if (station.getStationUUID() != null) {
            uuidStr = station.getStationUUID().toString();
        }
        int bitrateVal = 0;
        if (station.getBitrate() != null) {
            bitrateVal = station.getBitrate();
        }
        return new RadioStation(
                station.getName(),
                station.getUrl(),
                station.getCodec(),
                bitrateVal,
                uuidStr
        );
    }

    /**
     * Searches for stations by name, tag, or country.
     */
    public List<RadioStation> searchStations(String query, String tag, String country, int limit) {
        try {
            AdvancedSearch.AdvancedSearchBuilder builder = AdvancedSearch.builder()
                    .order(FieldName.CLICKCOUNT)
                    .reverse(true);

            if (query != null && !query.strip().isEmpty()) {
                builder = builder.name(query.trim());
            }
            if (tag != null && !tag.strip().isEmpty()) {
                builder = builder.tag(tag.trim());
            }
            if (country != null && !country.strip().isEmpty()) {
                builder = builder.countryCode(country.trim().toUpperCase());
            }

            return radioBrowser.listStationsWithAdvancedSearch(builder.build())
                    .limit(limit)
                    .map(this::mapToRadioStation)
                    .collect(Collectors.toList());
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
            return radioBrowser.listTopClickStations(Limit.of(limit))
                    .stream()
                    .map(this::mapToRadioStation)
                    .collect(Collectors.toList());
        } catch (Exception ex) {
            System.err.println("EuroRadio: API popular list fetch failed: " + ex.getMessage());
        }
        return Collections.emptyList();
    }

    /**
     * Gets list of available country codes with their names and station counts.
     * Returns a map of ISO country code -> station count.
     */
    public Map<String, Integer> getCountryCodes() {
        try {
            return radioBrowser.listCountryCodes();
        } catch (Exception ex) {
            System.err.println("EuroRadio: Failed to list country codes: " + ex.getMessage());
        }
        return Collections.emptyMap();
    }

    /**
     * Gets top clicked popular stations for a specific country code.
     */
    public List<RadioStation> getStationsByCountryCode(String countryCode, int limit) {
        try {
            return radioBrowser.listStationsBy(
                    SearchMode.BYCOUNTRYCODEEXACT,
                    countryCode,
                    ListParameter.create().order(FieldName.CLICKCOUNT).reverseOrder(true))
                    .limit(limit)
                    .map(this::mapToRadioStation)
                    .collect(Collectors.toList());
        } catch (Exception ex) {
            System.err.println("EuroRadio: Stations fetch for country " + countryCode + " failed: " + ex.getMessage());
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
                String url = resolvedBaseUrl + "/json/url/" + stationuuid;
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

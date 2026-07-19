package com.datazuul.euroworks.apps.eurotv;

import java.net.URI;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ArdUrlResolver {
    
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(10))
            .proxy(ProxySelector.getDefault())
            .build();

    public static String resolveStreamUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return null;
        }
        
        String trimmed = url.trim();
        // If it's not an ARD Mediathek page, assume it is a direct stream URL
        if (!trimmed.contains("ardmediathek.de")) {
            return trimmed;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimmed))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return null;
            }

            String html = response.body();

            // Try first regex order: itemprop="contentUrl" content="..."
            Pattern pattern1 = Pattern.compile("<meta[^>]*item[pP]rop=\"contentUrl\"[^>]*content=\"([^\"]+)\"");
            Matcher matcher1 = pattern1.matcher(html);
            if (matcher1.find()) {
                return matcher1.group(1);
            }

            // Try second regex order: content="..." itemprop="contentUrl"
            Pattern pattern2 = Pattern.compile("<meta[^>]*content=\"([^\"]+)\"[^>]*item[pP]rop=\"contentUrl\"");
            Matcher matcher2 = pattern2.matcher(html);
            if (matcher2.find()) {
                return matcher2.group(1);
            }

            // Fallback: If page-gateway format is in the URL, try fetching the API directly
            // e.g. /live/Y3JpZDov...
            if (trimmed.contains("/live/")) {
                int lastSlash = trimmed.lastIndexOf("/live/");
                String id = trimmed.substring(lastSlash + 6);
                if (id.contains("?")) {
                    id = id.substring(0, id.indexOf("?"));
                }
                if (!id.isEmpty()) {
                    String apiUrl = "https://api.ardmediathek.de/page-gateway/pages/ard/item/" + id;
                    HttpRequest apiRequest = HttpRequest.newBuilder()
                            .uri(URI.create(apiUrl))
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                            .timeout(Duration.ofSeconds(10))
                            .GET()
                            .build();
                    HttpResponse<String> apiResponse = CLIENT.send(apiRequest, HttpResponse.BodyHandlers.ofString());
                    if (apiResponse.statusCode() == 200) {
                        String json = apiResponse.body();
                        // Find the first "url" after "application/vnd.apple.mpegurl"
                        int mimeIdx = json.indexOf("application/vnd.apple.mpegurl");
                        if (mimeIdx != -1) {
                            int urlIdx = json.indexOf("\"url\":\"", mimeIdx);
                            if (urlIdx != -1) {
                                int start = urlIdx + 7;
                                int end = json.indexOf("\"", start);
                                if (end != -1) {
                                    return json.substring(start, end).replace("\\/", "/");
                                }
                            }
                        }
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("EuroTv: Error resolving ARD Mediathek stream URL: " + e.getMessage());
        }
        return null;
    }
}

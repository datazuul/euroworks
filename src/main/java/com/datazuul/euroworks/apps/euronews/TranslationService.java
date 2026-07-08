package com.datazuul.euroworks.apps.euronews;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

public class TranslationService {

    private static final String TRANSLATE_URL = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=de&dt=t&q=";

    public static String translateToGerman(String text) throws Exception {
        if (text == null || text.strip().isEmpty()) {
            return text;
        }

        String encodedText = URLEncoder.encode(text, StandardCharsets.UTF_8);
        String url = TRANSLATE_URL + encodedText;

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new Exception("HTTP status " + response.statusCode());
        }

        return parseTranslationJson(response.body());
    }

    private static String parseTranslationJson(String json) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<?> root = mapper.readValue(json, List.class);
        if (root != null && !root.isEmpty()) {
            List<?> partsList = (List<?>) root.get(0);
            StringBuilder sb = new StringBuilder();
            for (Object partObj : partsList) {
                if (partObj instanceof List<?> part) {
                    if (!part.isEmpty()) {
                        sb.append(part.get(0).toString());
                    }
                }
            }
            return sb.toString().trim();
        }
        throw new Exception("Ungültiges JSON-Format");
    }
}

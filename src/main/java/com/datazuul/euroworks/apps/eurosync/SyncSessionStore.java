package com.datazuul.euroworks.apps.eurosync;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles loading and saving EuroSync sessions from/to ~/.euroworks/eurosync/sessions.json.
 */
public class SyncSessionStore {
    private static final String CONFIG_DIR = System.getProperty("user.home") + "/.euroworks/eurosync";
    private static final String CONFIG_FILE = CONFIG_DIR + "/sessions.json";
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    public static List<SyncSession> loadSessions() {
        File file = new File(CONFIG_FILE);
        if (!file.exists()) {
            List<SyncSession> defaultList = new ArrayList<>();
            defaultList.add(new SyncSession("default"));
            return defaultList;
        }
        try {
            return objectMapper.readValue(file, new TypeReference<List<SyncSession>>() {});
        } catch (Exception e) {
            System.err.println("Could not load EuroSync sessions: " + e.getMessage());
            List<SyncSession> defaultList = new ArrayList<>();
            defaultList.add(new SyncSession("default"));
            return defaultList;
        }
    }

    public static void saveSessions(List<SyncSession> sessions) {
        File dir = new File(CONFIG_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File file = new File(CONFIG_FILE);
        try {
            objectMapper.writeValue(file, sessions);
        } catch (Exception e) {
            System.err.println("Could not save EuroSync sessions: " + e.getMessage());
        }
    }
}

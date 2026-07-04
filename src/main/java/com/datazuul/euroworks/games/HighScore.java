package com.datazuul.euroworks.games;

import java.io.*;
import java.util.*;

/**
 * Modernized HighScore system using NIO/standard file streams.
 * Eliminates deprecated AccessController/doPrivileged blocks.
 * Stores highscores in ~/.euroworks/.highscores
 */
public class HighScore implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String gameName;
    private final boolean isAscending;
    private final File highScoreFile;

    public static class ScoreEntry implements Serializable, Comparable<ScoreEntry> {
        private static final long serialVersionUID = 1L;
        public final String username;
        public final int score;
        public final Date date;
        private final boolean isAscending;
        public final int timeNeeded; // added field for duration/time needed (in seconds)

        public ScoreEntry(String username, int score, boolean isAscending, int timeNeeded) {
            this.username = username;
            this.score = score;
            this.date = new Date();
            this.isAscending = isAscending;
            this.timeNeeded = timeNeeded;
        }

        public ScoreEntry(String username, int score, boolean isAscending) {
            this(username, score, isAscending, 0);
        }

        @Override
        public int compareTo(ScoreEntry o) {
            if (isAscending) {
                return Integer.compare(this.score, o.score); // Ascending (lower time is better)
            } else {
                return Integer.compare(o.score, this.score); // Descending (higher score is better)
            }
        }
    }

    public HighScore(String gameName) {
        this.gameName = gameName;
        this.isAscending = gameName.startsWith("EuroMines");
        
        File dir = new File(System.getProperty("user.home"), ".euroworks");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        this.highScoreFile = new File(dir, ".highscores");
    }

    @SuppressWarnings("unchecked")
    public List<ScoreEntry> getScores() {
        if (!highScoreFile.exists()) {
            return new ArrayList<>();
        }
        try (FileInputStream fis = new FileInputStream(highScoreFile);
             ObjectInputStream ois = new ObjectInputStream(fis)) {
            Hashtable<String, List<ScoreEntry>> allScores = (Hashtable<String, List<ScoreEntry>>) ois.readObject();
            List<ScoreEntry> list = allScores.get(gameName);
            if (list == null) {
                return new ArrayList<>();
            }
            Collections.sort(list);
            return list;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public void setHighScore(int score, String username) throws IOException {
        setHighScore(score, username, 0);
    }

    @SuppressWarnings("unchecked")
    public void setHighScore(int score, String username, int timeNeeded) throws IOException {
        Hashtable<String, List<ScoreEntry>> allScores = null;
        if (highScoreFile.exists()) {
            try (FileInputStream fis = new FileInputStream(highScoreFile);
                 ObjectInputStream ois = new ObjectInputStream(fis)) {
                allScores = (Hashtable<String, List<ScoreEntry>>) ois.readObject();
            } catch (Exception e) {
                // ignore and rebuild
            }
        }

        if (allScores == null) {
            allScores = new Hashtable<>(13);
        }

        List<ScoreEntry> list = allScores.get(gameName);
        if (list == null) {
            list = new ArrayList<>();
        }

        list.add(new ScoreEntry(username, score, isAscending, timeNeeded));
        Collections.sort(list);

        // Keep only top 100 entries for performance
        if (list.size() > 100) {
            list = new ArrayList<>(list.subList(0, 100));
        }

        allScores.put(gameName, list);

        try (FileOutputStream fos = new FileOutputStream(highScoreFile);
             ObjectOutputStream oos = new ObjectOutputStream(fos)) {
            oos.writeObject(allScores);
        }
    }
}

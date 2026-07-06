package com.datazuul.euroworks.apps.eurosync;

import javax.swing.SwingWorker;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Background worker that implements the rsync-like synchronization logic.
 */
public class EuroSyncEngine extends SwingWorker<Void, String> {

    public interface EngineCallback {
        void onLog(String message);
        void onProgress(int current, int max, String currentFile);
        void onFinished(boolean success, boolean canceled, String summary);
    }

    private final SyncSession session;
    private final boolean dryRun;
    private final EngineCallback callback;
    
    private volatile boolean isCanceled = false;
    private int filesProcessed = 0;
    private int filesCopied = 0;
    private int filesDeleted = 0;
    private long bytesTransferred = 0;
    private long startTime;
    private final Map<File, Long> directoryTimestamps = new LinkedHashMap<>();

    public EuroSyncEngine(SyncSession session, boolean dryRun, EngineCallback callback) {
        this.session = session;
        this.dryRun = dryRun;
        this.callback = callback;
    }

    public void cancelSync() {
        this.isCanceled = true;
    }

    @Override
    protected Void doInBackground() throws Exception {
        startTime = System.currentTimeMillis();
        publish("=== EuroSync Start ===");
        publish("Sitzung: " + session.getName());
        publish("Modus: " + (dryRun ? "SIMULATION (Dry-Run)" : "Echtlauf"));
        publish("Pfade:");
        for (SyncPathPair pair : session.getPathPairs()) {
            publish(String.format("  Quelle: %s -> Ziel: %s", pair.getSourceDir(), pair.getTargetDir()));
        }
        publish("");

        List<SyncPathPair> pairs = session.getPathPairs();
        if (pairs == null || pairs.isEmpty()) {
            throw new IllegalArgumentException("Keine Quell- und Zielverzeichnisse konfiguriert.");
        }

        // Validate all pairs before starting
        for (SyncPathPair pair : pairs) {
            String src = pair.getSourceDir();
            String dst = pair.getTargetDir();
            if (src == null || src.trim().isEmpty()) {
                throw new IllegalArgumentException("Quellverzeichnis ist leer.");
            }
            if (dst == null || dst.trim().isEmpty()) {
                throw new IllegalArgumentException("Zielverzeichnis ist leer.");
            }

            File srcDir = new File(src);
            if (!srcDir.exists()) {
                throw new FileNotFoundException("Quellverzeichnis existiert nicht: " + srcDir.getAbsolutePath());
            }
            if (!srcDir.isDirectory()) {
                throw new IllegalArgumentException("Quelle ist kein Verzeichnis: " + srcDir.getAbsolutePath());
            }
        }

        // 1. Pre-Sync Command
        if (session.getPreCommand() != null && !session.getPreCommand().trim().isEmpty()) {
            boolean preSuccess = runExternalCommand(session.getPreCommand().trim(), "Pre-Sync");
            if (!preSuccess && session.isHaltOnPreFailure()) {
                publish("⚠️ Pre-Sync Befehl fehlgeschlagen. Synchronisation wird abgebrochen.");
                return null;
            }
        }

        if (isCanceled) {
            publish("Synchronisation abgebrochen.");
            return null;
        }

        // 2. Scan Phase
        publish("Scanne Quellverzeichnisse...");
        Map<SyncPathPair, List<File>> filesMap = new LinkedHashMap<>();
        long totalSourceSize = 0;
        int totalItems = 0;

        for (SyncPathPair pair : pairs) {
            if (isCanceled) break;
            File srcDir = new File(pair.getSourceDir());
            List<File> sourceFiles = new ArrayList<>();
            long size = scanDirectory(srcDir, sourceFiles);
            filesMap.put(pair, sourceFiles);
            totalSourceSize += size;
            totalItems += sourceFiles.size();
        }

        publish(String.format("Scan beendet: %d Dateien/Ordner in %d Paaren gefunden (Gesamtgröße: %,d Bytes)", 
                totalItems, pairs.size(), totalSourceSize));
        publish("");

        if (isCanceled) {
            publish("Synchronisation abgebrochen.");
            return null;
        }

        // 3. Process Sync
        int globalIndex = 0;
        for (SyncPathPair pair : pairs) {
            if (isCanceled) break;

            File srcDir = new File(pair.getSourceDir());
            File dstDir = new File(pair.getTargetDir());
            List<File> sourceFiles = filesMap.get(pair);

            publish("--- Synchronisiere Paar: " + srcDir.getName() + " -> " + dstDir.getName() + " ---");

            for (File srcFile : sourceFiles) {
                if (isCanceled) break;

                String relativePath = srcDir.toURI().relativize(srcFile.toURI()).getPath();
                if (relativePath.isEmpty()) continue;

                File dstFile = new File(dstDir, relativePath);

                globalIndex++;
                if (callback != null) {
                    callback.onProgress(globalIndex, totalItems, relativePath);
                }

                filesProcessed++;

                if (srcFile.isDirectory()) {
                    syncDirectory(srcFile, dstFile, relativePath);
                } else {
                    syncFile(srcFile, dstFile, relativePath);
                }
            }
        }

        // 4. Delete on Destination Phase
        if (!isCanceled && session.isDeleteOnDestination()) {
            publish("");
            publish("Überprüfe verwaiste Dateien im Zielverzeichnis zum Löschen...");
            for (SyncPathPair pair : pairs) {
                if (isCanceled) break;

                File srcDir = new File(pair.getSourceDir());
                File dstDir = new File(pair.getTargetDir());

                List<File> destFiles = new ArrayList<>();
                scanDirectory(dstDir, destFiles);

                // Traverse backwards (bottom-up) so child files are deleted before parent folders
                for (int i = destFiles.size() - 1; i >= 0; i--) {
                    if (isCanceled) break;
                    File dstFile = destFiles.get(i);
                    String relativePath = dstDir.toURI().relativize(dstFile.toURI()).getPath();
                    if (relativePath.isEmpty()) continue;

                    File matchingSrc = new File(srcDir, relativePath);
                    if (!matchingSrc.exists()) {
                        deleteTargetItem(dstFile, relativePath);
                    }
                }
            }
        }

        // Apply preserved directory timestamps in bottom-up order
        if (!isCanceled && session.isPreserveTimestamps() && !dryRun) {
            publish("");
            publish("Setze Zeitstempel für Verzeichnisse...");
            List<File> dirs = new ArrayList<>(directoryTimestamps.keySet());
            // Sort by path length descending (subfolders before parents)
            dirs.sort(Comparator.comparing(File::getAbsolutePath).reversed());
            for (File dir : dirs) {
                if (dir.exists() && dir.isDirectory()) {
                    long srcTime = directoryTimestamps.get(dir);
                    dir.setLastModified(srcTime);
                }
            }
        }

        // 5. Post-Sync Command
        if (!isCanceled && !(session.isSkipPostOnFailure() && filesCopied == 0 && filesDeleted == 0)) {
            if (session.getPostCommand() != null && !session.getPostCommand().trim().isEmpty()) {
                publish("");
                runExternalCommand(session.getPostCommand().trim(), "Post-Sync");
            }
        }

        return null;
    }

    private long scanDirectory(File root, List<File> list) {
        long totalSize = 0;
        File[] children = root.listFiles();
        if (children != null) {
            // Sort to process files in a clean, predictable order
            Arrays.sort(children, Comparator.comparing(File::getName));
            for (File child : children) {
                list.add(child);
                if (child.isDirectory()) {
                    totalSize += scanDirectory(child, list);
                } else {
                    totalSize += child.length();
                }
            }
        }
        return totalSize;
    }

    private void syncDirectory(File src, File dest, String relativePath) {
        if (!dest.exists()) {
            publish("[CREATE DIR] " + relativePath);
            if (!dryRun) {
                dest.mkdirs();
            }
        } else if (dest.isFile()) {
            publish("[REPLACE FILE WITH DIR] " + relativePath);
            if (!dryRun) {
                dest.delete();
                dest.mkdirs();
            }
        }

        if (session.isPreserveTimestamps()) {
            directoryTimestamps.put(dest, src.lastModified());
        }
        if (session.isPreservePermissions() && !session.isWindowsMode() && !dryRun) {
            dest.setReadable(src.canRead());
            dest.setWritable(src.canWrite());
            dest.setExecutable(src.canExecute());
        }
    }

    private void syncFile(File src, File dest, String relativePath) {
        boolean shouldCopy = false;
        String reason = "";

        if (!dest.exists()) {
            shouldCopy = true;
            reason = "Datei existiert nicht im Ziel";
        } else {
            if (session.isIgnoreExisting()) {
                // Ignore existing files
                return;
            }

            long srcLen = src.length();
            long dstLen = dest.length();

            if (srcLen != dstLen) {
                shouldCopy = true;
                reason = String.format("Größe unterscheidet sich (Quelle: %,d Bytes, Ziel: %,d Bytes)", srcLen, dstLen);
            } else if (!session.isSizeOnly()) {
                long srcTime = src.lastModified();
                long dstTime = dest.lastModified();
                long diff = Math.abs(srcTime - dstTime);

                // Windows compatibility / modify-window allows 2 second tolerance (FAT/NTFS differences)
                long tolerance = session.isWindowsMode() ? 2000 : 0;

                if (diff > tolerance) {
                    if (session.isSkipNewer() && dstTime > srcTime) {
                        // Skip if target file is newer
                        return;
                    }
                    shouldCopy = true;
                    reason = String.format("Zeitstempel unterscheidet sich (Quelle: %tF %<tT, Ziel: %tF %<tT)", srcTime, dstTime);
                }
            }
        }

        if (shouldCopy) {
            publish(String.format("[COPY] %s (%s)", relativePath, reason));
            if (!dryRun) {
                try {
                    copyFileContent(src, dest);
                    filesCopied++;
                    bytesTransferred += src.length();

                    // Preserve timestamps
                    if (session.isPreserveTimestamps()) {
                        dest.setLastModified(src.lastModified());
                    }
                    // Preserve permissions
                    if (session.isPreservePermissions() && !session.isWindowsMode()) {
                        dest.setReadable(src.canRead());
                        dest.setWritable(src.canWrite());
                        dest.setExecutable(src.canExecute());
                    }
                } catch (Exception e) {
                    publish("❌ Fehler beim Kopieren von " + relativePath + ": " + e.getMessage());
                }
            } else {
                filesCopied++;
                bytesTransferred += src.length();
            }
        }
    }

    private void copyFileContent(File src, File dest) throws Exception {
        dest.getParentFile().mkdirs();
        try (InputStream in = new FileInputStream(src);
             OutputStream out = new FileOutputStream(dest)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                if (isCanceled) {
                    throw new InterruptedException("Kopiervorgang abgebrochen");
                }
                out.write(buffer, 0, bytesRead);
            }
        }
    }

    private void deleteTargetItem(File target, String relativePath) {
        publish("[DELETE] " + relativePath);
        if (!dryRun) {
            try {
                if (target.isDirectory()) {
                    deleteDirectoryRecursively(target);
                } else {
                    target.delete();
                }
                filesDeleted++;
            } catch (Exception e) {
                publish("❌ Fehler beim Löschen von " + relativePath + ": " + e.getMessage());
            }
        } else {
            filesDeleted++;
        }
    }

    private void deleteDirectoryRecursively(File dir) {
        File[] children = dir.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory()) {
                    deleteDirectoryRecursively(child);
                } else {
                    child.delete();
                }
            }
        }
        dir.delete();
    }

    private boolean runExternalCommand(String commandLine, String label) {
        publish(String.format("Führe %s-Befehl aus: %s", label, commandLine));
        try {
            ProcessBuilder pb;
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                pb = new ProcessBuilder("cmd.exe", "/c", commandLine);
            } else {
                pb = new ProcessBuilder("sh", "-c", commandLine);
            }
            pb.redirectErrorStream(true);
            Process p = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (isCanceled) {
                        p.destroy();
                        publish("  Command aborted by user.");
                        return false;
                    }
                    publish("  " + line);
                }
            }

            int exitCode = p.waitFor();
            publish(String.format("%s-Befehl beendet mit Exit-Code %d", label, exitCode));
            return exitCode == 0;
        } catch (Exception e) {
            publish(String.format("❌ Fehler bei Ausführung von %s-Befehl: %s", label, e.getMessage()));
            return false;
        }
    }

    @Override
    protected void process(List<String> chunks) {
        for (String message : chunks) {
            if (callback != null) {
                callback.onLog(message);
            }
            if (session.isVerbose()) {
                System.out.println(message);
            }
        }
    }

    @Override
    protected void done() {
        long elapsed = System.currentTimeMillis() - startTime;
        long secs = elapsed / 1000;
        String timeStr = secs > 0 ? String.format("%d:%02d Min.", secs / 60, secs % 60) : (elapsed + " ms");

        StringBuilder summary = new StringBuilder();
        summary.append("\n=== EuroSync Beendet ===\n");
        summary.append(String.format("Dauer: %s\n", timeStr));
        summary.append(String.format("Dateien analysiert: %d\n", filesProcessed));
        summary.append(String.format("Dateien kopiert: %d\n", filesCopied));
        summary.append(String.format("Dateien gelöscht: %d\n", filesDeleted));
        summary.append(String.format("Übertragene Bytes: %,d Bytes\n", bytesTransferred));

        if (isCanceled) {
            summary.append("Status: VOM BENUTZER ABGEBROCHEN\n");
        } else {
            summary.append("Status: ERFOLGREICH BEENDET\n");
        }

        if (callback != null) {
            callback.onFinished(!isCanceled, isCanceled, summary.toString());
        }
    }
}

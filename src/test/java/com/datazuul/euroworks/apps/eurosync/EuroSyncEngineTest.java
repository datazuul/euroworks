package com.datazuul.euroworks.apps.eurosync;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.*;

class EuroSyncEngineTest {

    @Test
    void testBasicSynchronization(@TempDir Path tempDir) throws Exception {
        File srcDir = tempDir.resolve("src").toFile();
        File dstDir = tempDir.resolve("dst").toFile();
        assertTrue(srcDir.mkdir());
        assertTrue(dstDir.mkdir());

        // Create some source files
        File file1 = new File(srcDir, "file1.txt");
        try (FileWriter writer = new FileWriter(file1)) {
            writer.write("Hello World");
        }

        File subDir = new File(srcDir, "subdir");
        assertTrue(subDir.mkdir());
        File file2 = new File(subDir, "file2.txt");
        try (FileWriter writer = new FileWriter(file2)) {
            writer.write("Nested file contents");
        }

        // Configure sync session
        SyncSession session = new SyncSession("test");
        session.getPathPairs().add(new SyncPathPair(srcDir.getAbsolutePath() + File.separator, dstDir.getAbsolutePath() + File.separator));
        session.setVerbose(true);
        session.setDeleteOnDestination(true);

        final boolean[] finishedCalled = new boolean[1];
        
        EuroSyncEngine.EngineCallback callback = new EuroSyncEngine.EngineCallback() {
            @Override
            public void onLog(String message) {
                System.out.println("TEST LOG: " + message);
            }

            @Override
            public void onProgress(int current, int max, String currentFile, long fileBytesTransferred, long fileTotalBytes) {
                System.out.println("TEST PROGRESS: " + current + "/" + max + " -> " + currentFile + " (" + fileBytesTransferred + "/" + fileTotalBytes + ")");
            }

            @Override
            public void onFinished(boolean success, boolean canceled, String summary) {
                finishedCalled[0] = true;
                assertTrue(success);
                assertFalse(canceled);
                assertTrue(summary.contains("Dateien kopiert: 2"));
            }
        };

        // Run engine logic synchronously in test thread
        TestEuroSyncEngine engine = new TestEuroSyncEngine(session, false, callback);
        engine.runSyncSynchronously();

        // Verify target files exist and content is correct
        File targetFile1 = new File(dstDir, "file1.txt");
        File targetSubdir = new File(dstDir, "subdir");
        File targetFile2 = new File(targetSubdir, "file2.txt");

        assertTrue(targetFile1.exists());
        assertTrue(targetSubdir.exists());
        assertTrue(targetFile2.exists());

        assertEquals(file1.length(), targetFile1.length());
        assertEquals(file2.length(), targetFile2.length());
        assertEquals(subDir.lastModified(), targetSubdir.lastModified());

        // Run deletion test: add an orphan file in target
        File orphanFile = new File(dstDir, "orphan.txt");
        try (FileWriter writer = new FileWriter(orphanFile)) {
            writer.write("I should be deleted");
        }
        assertTrue(orphanFile.exists());

        // Sync again with delete on destination
        TestEuroSyncEngine engine2 = new TestEuroSyncEngine(session, false, new EuroSyncEngine.EngineCallback() {
            @Override public void onLog(String message) {}
            @Override public void onProgress(int current, int max, String currentFile, long fileBytesTransferred, long fileTotalBytes) {}
            @Override
            public void onFinished(boolean success, boolean canceled, String summary) {
                assertTrue(summary.contains("Dateien gelöscht: 1"));
            }
        });
        engine2.runSyncSynchronously();

        // Verify orphan was deleted and other files remain
        assertFalse(orphanFile.exists());
        assertTrue(targetFile1.exists());
        assertTrue(targetFile2.exists());
    }

    @Test
    void testDryRunSimulation(@TempDir Path tempDir) throws Exception {
        File srcDir = tempDir.resolve("src").toFile();
        File dstDir = tempDir.resolve("dst").toFile();
        assertTrue(srcDir.mkdir());
        assertTrue(dstDir.mkdir());

        File file1 = new File(srcDir, "file1.txt");
        try (FileWriter writer = new FileWriter(file1)) {
            writer.write("Hello World");
        }

        SyncSession session = new SyncSession("test-dry");
        session.getPathPairs().add(new SyncPathPair(srcDir.getAbsolutePath() + File.separator, dstDir.getAbsolutePath() + File.separator));
        session.setVerbose(true);

        TestEuroSyncEngine engine = new TestEuroSyncEngine(session, true, new EuroSyncEngine.EngineCallback() {
            @Override public void onLog(String message) {}
            @Override public void onProgress(int current, int max, String currentFile, long fileBytesTransferred, long fileTotalBytes) {}
            @Override
            public void onFinished(boolean success, boolean canceled, String summary) {
                assertTrue(summary.contains("Dateien kopiert: 1"));
            }
        });
        engine.runSyncSynchronously();

        // Target file should NOT exist because it was dry-run/simulation!
        File targetFile1 = new File(dstDir, "file1.txt");
        assertFalse(targetFile1.exists());
    }

    // Custom subclass to expose doInBackground and done for synchronous testing
    private static class TestEuroSyncEngine extends EuroSyncEngine {
        public TestEuroSyncEngine(SyncSession session, boolean dryRun, EngineCallback callback) {
            super(session, dryRun, callback);
        }

        public void runSyncSynchronously() throws Exception {
            this.doInBackground();
            this.done();
        }
    }
}

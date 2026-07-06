package com.datazuul.euroworks.apps.eurosync;

/**
 * Represents a source and target directory pair to be synchronized.
 */
public class SyncPathPair {
    private String sourceDir = "";
    private String targetDir = "";

    public SyncPathPair() {
    }

    public SyncPathPair(String sourceDir, String targetDir) {
        this.sourceDir = sourceDir;
        this.targetDir = targetDir;
    }

    public String getSourceDir() {
        return sourceDir;
    }

    public void setSourceDir(String sourceDir) {
        this.sourceDir = sourceDir;
    }

    public String getTargetDir() {
        return targetDir;
    }

    public void setTargetDir(String targetDir) {
        this.targetDir = targetDir;
    }
}

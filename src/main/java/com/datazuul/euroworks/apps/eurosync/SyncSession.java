package com.datazuul.euroworks.apps.eurosync;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SyncSession {
    private String name = "default";
    private List<SyncPathPair> pathPairs = new ArrayList<>();
    
    // Basic Options
    private boolean preserveTimestamps = true;
    private boolean preservePermissions = false;
    private boolean preserveOwner = false;
    private boolean preserveGroup = false;
    private boolean deleteOnDestination = false;
    private boolean dontLeaveFilesystem = false;
    private boolean verbose = true;
    private boolean showProgress = true;
    private boolean ignoreExisting = false;
    private boolean sizeOnly = false;
    private boolean skipNewer = false;
    private boolean windowsMode = false;
    private String notes = "";
    
    // Advanced Options
    private boolean compression = false;
    private boolean keepPartial = false;
    private boolean copySymlinks = false;
    private boolean followSymlinks = false;
    private boolean sparseFiles = false;
    private String additionalOptions = "";
    
    // Extra Options
    private String preCommand = "";
    private boolean haltOnPreFailure = false;
    private String postCommand = "";
    private boolean skipPostOnFailure = false;

    public SyncSession() {
    }

    public SyncSession(String name) {
        this.name = name;
    }

    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<SyncPathPair> getPathPairs() { return pathPairs; }
    public void setPathPairs(List<SyncPathPair> pathPairs) { this.pathPairs = pathPairs; }

    public boolean isPreserveTimestamps() { return preserveTimestamps; }
    public void setPreserveTimestamps(boolean preserveTimestamps) { this.preserveTimestamps = preserveTimestamps; }

    public boolean isPreservePermissions() { return preservePermissions; }
    public void setPreservePermissions(boolean preservePermissions) { this.preservePermissions = preservePermissions; }

    public boolean isPreserveOwner() { return preserveOwner; }
    public void setPreserveOwner(boolean preserveOwner) { this.preserveOwner = preserveOwner; }

    public boolean isPreserveGroup() { return preserveGroup; }
    public void setPreserveGroup(boolean preserveGroup) { this.preserveGroup = preserveGroup; }

    public boolean isDeleteOnDestination() { return deleteOnDestination; }
    public void setDeleteOnDestination(boolean deleteOnDestination) { this.deleteOnDestination = deleteOnDestination; }

    public boolean isDontLeaveFilesystem() { return dontLeaveFilesystem; }
    public void setDontLeaveFilesystem(boolean dontLeaveFilesystem) { this.dontLeaveFilesystem = dontLeaveFilesystem; }

    public boolean isVerbose() { return verbose; }
    public void setVerbose(boolean verbose) { this.verbose = verbose; }

    public boolean isShowProgress() { return showProgress; }
    public void setShowProgress(boolean showProgress) { this.showProgress = showProgress; }

    public boolean isIgnoreExisting() { return ignoreExisting; }
    public void setIgnoreExisting(boolean ignoreExisting) { this.ignoreExisting = ignoreExisting; }

    public boolean isSizeOnly() { return sizeOnly; }
    public void setSizeOnly(boolean sizeOnly) { this.sizeOnly = sizeOnly; }

    public boolean isSkipNewer() { return skipNewer; }
    public void setSkipNewer(boolean skipNewer) { this.skipNewer = skipNewer; }

    public boolean isWindowsMode() { return windowsMode; }
    public void setWindowsMode(boolean windowsMode) { this.windowsMode = windowsMode; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public boolean isCompression() { return compression; }
    public void setCompression(boolean compression) { this.compression = compression; }

    public boolean isKeepPartial() { return keepPartial; }
    public void setKeepPartial(boolean keepPartial) { this.keepPartial = keepPartial; }

    public boolean isCopySymlinks() { return copySymlinks; }
    public void setCopySymlinks(boolean copySymlinks) { this.copySymlinks = copySymlinks; }

    public boolean isFollowSymlinks() { return followSymlinks; }
    public void setFollowSymlinks(boolean followSymlinks) { this.followSymlinks = followSymlinks; }

    public boolean isSparseFiles() { return sparseFiles; }
    public void setSparseFiles(boolean sparseFiles) { this.sparseFiles = sparseFiles; }

    public String getAdditionalOptions() { return additionalOptions; }
    public void setAdditionalOptions(String additionalOptions) { this.additionalOptions = additionalOptions; }

    public String getPreCommand() { return preCommand; }
    public void setPreCommand(String preCommand) { this.preCommand = preCommand; }

    public boolean isHaltOnPreFailure() { return haltOnPreFailure; }
    public void setHaltOnPreFailure(boolean haltOnPreFailure) { this.haltOnPreFailure = haltOnPreFailure; }

    public String getPostCommand() { return postCommand; }
    public void setPostCommand(String postCommand) { this.postCommand = postCommand; }

    public boolean isSkipPostOnFailure() { return skipPostOnFailure; }
    public void setSkipPostOnFailure(boolean skipPostOnFailure) { this.skipPostOnFailure = skipPostOnFailure; }

    @Override
    public String toString() {
        return name;
    }
}

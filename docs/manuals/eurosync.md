# EuroSync – User Manual

EuroSync is a graphical file synchronization utility for the EuroWorks desktop suite. It is designed to match the layout and features of the classic **GRsync** tool, utilizing a high-fidelity, cross-platform synchronization engine modeled after the standard `rsync` utility.

---

## Getting Started

EuroSync synchronizes files from a **Source directory (Quelle)** to a **Target directory (Ziel)**. It scans both directories, analyzes differences, and updates the target directory so that it mirrors the source according to your selected rules.

### Basic Steps
1. Select or create a **Session (Sitzung)**.
2. Configure **Source and Target directory pairs (Quell- und Zielverzeichnisse)** in the paths table.
3. Configure your desired rules under the **Options Tabs**.
4. Click **Simulation starten** (Dry-Run) to preview changes without modifying files.
5. Click **Ausführen** to perform the actual synchronization.

---

## Sessions Management (Sitzungen)

At the top of the interface, you can manage different profiles/sessions (e.g., one for backing up documents, another for synchronizing media):
- **Dropdown List**: Switch between saved sessions. Selecting a session loads its path pairs and options automatically.
- **+ Hinzufügen (Add)**: Create a new session with a custom name.
- **Löschen (Delete)**: Delete the currently active session.

*All settings, path pairs list, checkbox choices, and notes are automatically saved to `~/.euroworks/eurosync/sessions.json` whenever they are changed.*

---

## Path Configurations (Quell- und Zielverzeichnisse)

Instead of a single sync pair, EuroSync lets you manage multiple directory pairs under a single session. They are displayed in a table and synced sequentially one after the other.

- **+ Hinzufügen**: Opens a selection dialog to add a new pair.
  - Choose **Quelle (Source)** and **Ziel (Target)** using folder browse dialogs.
  - When choosing the target folder, EuroSync will automatically recommend appending the last directory of the source (e.g., source `C:\FolderA` and selected target `D:\Backup` will suggest `D:\Backup\FolderA`). You can edit or delete this suffix before clicking Add.
- **🗑 Löschen**: Deletes the selected directory pair row from the table.

---

## Options Tabs

### 1. Standardoptionen (Basic Options)
These control the core file comparison and copy behavior:
*   **Zeitstempel erhalten** (Preserve timestamps): Copies the modification time of source files to target files. Highly recommended for efficient future incremental backups.
*   **Rechte erhalten** (Preserve permissions): Attempts to match read/write/executable permissions.
*   **Besitzer/Gruppe erhalten**: Visual options showing owner/group options (handled where supported by OS filesystems).
*   **Im Zielverzeichnis löschen** (Delete on destination): Deletes files in the target directory that no longer exist in the source. Use this option to make the target an exact mirror of the source.
*   **Dateisystem nicht verlassen**: Visual options showing standard filesystem boundaries.
*   **Ausführliche Meldungen** (Verbose): Detailed logging of individual file comparisons and copy operations.
*   **Fortschritt anzeigen** (Show progress): Displays current file count, completion percentages, and progress bars.
*   **Bestehende ignorieren** (Ignore existing): Skips copying if a file already exists at the target, regardless of timestamp or size differences.
*   **Nur Dateigröße vergleichen** (Size only): Skips timestamp checking and only copies files if their sizes differ.
*   **Neuere überspringen** (Skip newer): Avoids overwriting target files if they have a newer modification timestamp than their source counterparts.
*   **Windows-Kompatibilitätsmodus** (Windows mode): Tolerates tiny timestamp variations (up to 2 seconds) to avoid redundant copies between FAT and NTFS filesystems, and skips unsupported local permission operations.
*   **Notes**: A custom comment text line saved with each session.

### 2. Erweiterte Optionen (Advanced Options)
Controls for specialized synchronizations:
*   **Kompression / Sparsame Dateien / Symbolische Links**: Options for compatibility with standard `rsync` flags.
*   **Zusätzliche Optionen**: Arbitrary parameters or comment switches for synchronization overrides.

### 3. Zusatzoptionen (Extra Options)
Hooks to run custom external commands during execution:
*   **Vorher auszuführender Befehl** (Pre-sync command): An external shell command or script to run *before* the synchronization starts.
*   *   **Bei Fehler abbrechen** (Halt on failure): If the pre-sync command returns a non-zero exit code, the synchronization is aborted immediately.
*   **Nachher auszuführender Befehl** (Post-sync command): An external shell command or script to run *after* a successful synchronization.
*   *   **Bei Fehler nicht ausführen** (Skip on failure): Skips the post-sync command if the sync was aborted, canceled, or encountered errors.

---

## Execution Modes

When you initiate synchronization, a modal log window opens:

### Simulation (Simulation starten)
Runs a **dry-run** comparison. It scans the source and target, builds the synchronization plan, and logs exactly what *would* have been copied, deleted, or updated, without writing anything to disk. This is a safe way to verify your configurations (especially destructive actions like *Delete on destination*) before performing them.

### Real Run (Ausführen)
Runs the actual synchronization, modifying the target directory. It executes any pre-sync scripts, creates directories, copies changed files, deletes orphaned target items, sets metadata attributes, and runs any post-sync scripts.

### Progress & Logging Dialog
- **Progress Bar**: Displays `Completed files / Total files` and percentage.
- **Log text area**: A retro-style scrolling text console printing transfer logs.
- **Abbrechen (Cancel)**: Gracefully aborts the sync thread mid-process (closing files safely).
- **Schließen (Close)**: Closes the log view when the process has finished or been canceled.

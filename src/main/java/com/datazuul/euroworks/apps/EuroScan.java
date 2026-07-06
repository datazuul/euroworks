package com.datazuul.euroworks.apps;

import java.awt.geom.Area;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * EuroScan – Flatbed scanner integration for EuroWorks.
 *
 * <p>Acquires images from a connected flatbed scanner using the platform-native
 * scanning subsystem:
 * <ul>
 *   <li><b>Windows</b>: Windows Image Acquisition (WIA) via PowerShell.
 *       WIA is built into all Windows XP+ systems and handles TWAIN driver bridging
 *       internally, including the 32-bit / 64-bit compatibility problem.</li>
 *   <li><b>Linux/macOS</b>: {@code scanimage} CLI tool from the SANE project
 *       ({@code sane-utils} package).</li>
 * </ul>
 *
 * <p>Post-scan editing:
 * <ul>
 *   <li>Crop: drag to draw a rectangle selection with 8 resize handles; click
 *       "Zuschneiden" to crop the image to that selection.</li>
 *   <li>Rotate: "Drehen +90°" rotates the image 90 degrees clockwise.</li>
 * </ul>
 */
public class EuroScan extends EuroAppFrame {

    // ── UI layout constants ───────────────────────────────────────────────────

    private static final int APP_WIDTH  = 800;
    private static final int APP_HEIGHT = 600;

    // ── State ─────────────────────────────────────────────────────────────────

    private BufferedImage    scannedImage     = null;
    private File             tempScanFile     = null;
    private volatile boolean scanInProgress   = false;

    /** Currently selected scanner. {@code null} = auto-select first available. */
    private ScannerInfo selectedScanner = null;

    private File lastSaveDirectory = null;

    // ── UI components ─────────────────────────────────────────────────────────

    private final ImageViewPanel imageViewPanel;
    private final JButton        btnScan;
    private final JButton        btnSave;
    private final JButton        btnSelect;
    private final JButton        btnCrop;
    private final JButton        btnRotate;
    private final JLabel         lblStatus;
    private final JLabel         lblScanner;
    private final JLabel         lblZoom;

    // ── Constructor ───────────────────────────────────────────────────────────

    public EuroScan() {
        super("EuroScan");
        setSize(APP_WIDTH + 16, APP_HEIGHT + 74);

        // Initialize early so that toolbar lambdas can refer to it
        imageViewPanel = new ImageViewPanel();

        setJMenuBar(buildMenuBar());

        // ── Top toolbar ──────────────────────────────────────────────────────
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 5));
        toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(128, 128, 128)));

        btnSelect = buildRetroButton("📋  Scanner...");
        btnSelect.setToolTipText("Verfügbare Scanner auflisten und auswählen");
        btnSelect.addActionListener(e -> selectScanner());
        toolbar.add(btnSelect);

        btnScan = buildRetroButton("⬛  Scannen");
        btnScan.setToolTipText("Scan starten");
        btnScan.addActionListener(e -> startScan());
        toolbar.add(btnScan);

        btnCrop = buildRetroButton("✂  Zuschneiden");
        btnCrop.setToolTipText("Ausgewählten Bereich ausschneiden");
        btnCrop.setEnabled(false);
        btnCrop.addActionListener(e -> cropImage());
        toolbar.add(btnCrop);

        btnRotate = buildRetroButton("↻  Drehen +90°");
        btnRotate.setToolTipText("Bild 90° im Uhrzeigersinn drehen");
        btnRotate.setEnabled(false);
        btnRotate.addActionListener(e -> rotateImage());
        toolbar.add(btnRotate);

        btnSave = buildRetroButton("💾  Speichern");
        btnSave.setToolTipText("Scanbild als PNG speichern");
        btnSave.setEnabled(false);
        btnSave.addActionListener(e -> saveImage());
        toolbar.add(btnSave);

        // ── Zoom controls ────────────────────────────────────────────────────
        JButton btnZoomIn = buildSmallButton("+", "Vergrößern (auch Mausrad)");
        btnZoomIn.addActionListener(e -> imageViewPanel.zoomIn());
        JButton btnZoomOut = buildSmallButton("−", "Verkleinern (auch Mausrad)");
        btnZoomOut.addActionListener(e -> imageViewPanel.zoomOut());
        JButton btnZoomReset = buildSmallButton("⊡", "Einpassen (Zoom zurücksetzen)");
        btnZoomReset.addActionListener(e -> imageViewPanel.resetZoom());
        lblZoom = new JLabel("Einpassen");
        lblZoom.setFont(new Font("Courier New", Font.PLAIN, 10));
        lblZoom.setForeground(new Color(60, 60, 60));
        lblZoom.setToolTipText("Aktueller Zoom");
        toolbar.add(new javax.swing.JSeparator(javax.swing.SwingConstants.VERTICAL) {{ setPreferredSize(new Dimension(6, 22)); }});
        toolbar.add(btnZoomOut);
        toolbar.add(lblZoom);
        toolbar.add(btnZoomIn);
        toolbar.add(btnZoomReset);

        // ── Info rows ────────────────────────────────────────────────────────
        JPanel infoRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        infoRow.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(160, 160, 160)));
        lblScanner = new JLabel("Scanner: (automatisch – erster verfügbarer)");
        lblScanner.setFont(new Font("Courier New", Font.PLAIN, 10));
        lblScanner.setForeground(new Color(80, 80, 80));
        infoRow.add(lblScanner);

        JPanel statusRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        lblStatus = new JLabel("Bereit. Bild auswählen mit Drag, dann Zuschneiden.");
        lblStatus.setFont(new Font("Courier New", Font.PLAIN, 11));
        lblStatus.setForeground(new Color(64, 64, 64));
        statusRow.add(lblStatus);

        JPanel north = new JPanel(new BorderLayout());
        north.add(toolbar,    BorderLayout.NORTH);
        north.add(infoRow,    BorderLayout.CENTER);
        north.add(statusRow,  BorderLayout.SOUTH);

        // ── Image viewer ─────────────────────────────────────────────────────

        JPanel root = new JPanel(new BorderLayout());
        root.add(north,          BorderLayout.NORTH);
        root.add(imageViewPanel, BorderLayout.CENTER);
        setContentPane(root);
    }

    // ── Menu ──────────────────────────────────────────────────────────────────

    private JMenuBar buildMenuBar() {
        JMenuBar mb = new JMenuBar();
        JMenu menu = new JMenu("Scan");

        JMenuItem selectItem = new JMenuItem("Scanner auswählen...");
        selectItem.addActionListener(e -> selectScanner());
        menu.add(selectItem);

        menu.addSeparator();

        JMenuItem scanItem = new JMenuItem("Scannen");
        scanItem.addActionListener(e -> startScan());
        menu.add(scanItem);

        JMenu editMenu = new JMenu("Bearbeiten");
        JMenuItem cropItem = new JMenuItem("Zuschneiden");
        cropItem.addActionListener(e -> cropImage());
        editMenu.add(cropItem);
        JMenuItem rotateItem = new JMenuItem("Drehen +90°");
        rotateItem.addActionListener(e -> rotateImage());
        editMenu.add(rotateItem);

        JMenuItem saveItem = new JMenuItem("Speichern als...");
        saveItem.addActionListener(e -> saveImage());
        menu.add(saveItem);

        menu.addSeparator();
        JMenuItem closeItem = new JMenuItem("Schließen");
        closeItem.addActionListener(e -> dispose());
        menu.add(closeItem);

        mb.add(menu);
        mb.add(editMenu);
        return mb;
    }

    // ── Scanner selection ─────────────────────────────────────────────────────

    private void selectScanner() {
        if (scanInProgress) return;
        btnSelect.setEnabled(false);
        btnScan.setEnabled(false);
        setStatus("Scanner werden gesucht...", new Color(0, 90, 110));

        Thread t = new Thread(() -> {
            try {
                List<ScannerInfo> scanners = listScanners();
                SwingUtilities.invokeLater(() -> {
                    btnSelect.setEnabled(true);
                    btnScan.setEnabled(true);
                    showScannerChooserDialog(scanners);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    btnSelect.setEnabled(true);
                    btnScan.setEnabled(true);
                    setStatus("Fehler beim Suchen: " + ex.getMessage(), Color.RED);
                    JOptionPane.showMessageDialog(EuroScan.this,
                        "Scanner konnten nicht aufgelistet werden:\n" + ex.getMessage(),
                        "Fehler", JOptionPane.ERROR_MESSAGE);
                });
            }
        }, "EuroScan-List-Thread");
        t.setDaemon(true);
        t.start();
    }

    private List<ScannerInfo> listScanners() throws IOException, InterruptedException {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            return WindowsWiaScanBackend.listScanners();
        } else {
            return LinuxSaneScanBackend.listScanners();
        }
    }

    private void showScannerChooserDialog(List<ScannerInfo> scanners) {
        List<String> displayList = new ArrayList<>();
        displayList.add("⟵ Automatisch (erster verfügbarer)");
        for (ScannerInfo s : scanners) displayList.add(s.displayName());

        if (scanners.isEmpty()) {
            int choice = JOptionPane.showConfirmDialog(this,
                "Es wurden keine Scanner gefunden.\n\nStellen Sie sicher, dass der Scanner\n" +
                "angeschlossen, eingeschaltet und ein Treiber installiert ist.\n\n" +
                "Trotzdem mit automatischer Auswahl fortfahren?",
                "Kein Scanner gefunden", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice == JOptionPane.YES_OPTION) { selectedScanner = null; updateScannerLabel(); }
            return;
        }

        int currentIndex = 0;
        if (selectedScanner != null) {
            for (int i = 0; i < scanners.size(); i++) {
                if (scanners.get(i).deviceId().equals(selectedScanner.deviceId())) {
                    currentIndex = i + 1; break;
                }
            }
        }

        Object chosen = JOptionPane.showInputDialog(this,
            "Verfügbare Scanner (" + scanners.size() + " gefunden).\nScanner auswählen:",
            "Scanner auswählen", JOptionPane.PLAIN_MESSAGE,
            null, displayList.toArray(), displayList.get(currentIndex));

        if (chosen == null) return;
        int idx = displayList.indexOf(chosen);
        selectedScanner = (idx == 0) ? null : scanners.get(idx - 1);
        updateScannerLabel();
        setStatus("Scanner ausgewählt.", new Color(0, 120, 0));
    }

    private void updateScannerLabel() {
        if (selectedScanner == null) {
            lblScanner.setText("Scanner: (automatisch – erster verfügbarer)");
        } else {
            lblScanner.setText("Scanner: " + selectedScanner.displayName());
        }
    }

    // ── Scan logic ────────────────────────────────────────────────────────────

    private void startScan() {
        if (scanInProgress) return;
        scanInProgress = true;
        btnScan.setEnabled(false);
        btnSelect.setEnabled(false);
        btnSave.setEnabled(false);
        btnCrop.setEnabled(false);
        btnRotate.setEnabled(false);
        setStatus("Scannen läuft...", new Color(0, 90, 110));

        final ScannerInfo scannerToUse = selectedScanner;

        Thread scanThread = new Thread(() -> {
            try {
                File result = detectBackendAndScan(scannerToUse);
                BufferedImage img = ImageIO.read(result);
                if (img == null) throw new IOException("Bilddatei konnte nicht gelesen werden.");
                SwingUtilities.invokeLater(() -> {
                    scannedImage = img;
                    tempScanFile = result;
                    imageViewPanel.setImage(img);
                    btnSave.setEnabled(true);
                    btnCrop.setEnabled(true);
                    btnRotate.setEnabled(true);
                    setStatus("Scan fertig. (" + img.getWidth() + " × " + img.getHeight() + " px) — Auswahl ziehen zum Zuschneiden.",
                              new Color(0, 120, 0));
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    setStatus("Fehler: " + ex.getMessage(), Color.RED);
                    JOptionPane.showMessageDialog(EuroScan.this,
                        "Scan fehlgeschlagen:\n" + ex.getMessage(), "Scan-Fehler", JOptionPane.ERROR_MESSAGE);
                });
            } finally {
                SwingUtilities.invokeLater(() -> {
                    btnScan.setEnabled(true);
                    btnSelect.setEnabled(true);
                    scanInProgress = false;
                });
            }
        }, "EuroScan-Thread");
        scanThread.setDaemon(true);
        scanThread.start();
    }

    private File detectBackendAndScan(ScannerInfo scanner) throws IOException, InterruptedException {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            return new WindowsWiaScanBackend(scanner).scan();
        } else {
            return new LinuxSaneScanBackend(scanner).scan();
        }
    }

    // ── Crop & Rotate ─────────────────────────────────────────────────────────

    /**
     * Crops the current image to the user's selection rectangle.
     * The selection is converted from panel coordinates to image coordinates.
     */
    private void cropImage() {
        if (scannedImage == null) return;
        Rectangle cropRect = imageViewPanel.getCropRectInImageCoords();
        if (cropRect == null || cropRect.width < 2 || cropRect.height < 2) {
            JOptionPane.showMessageDialog(this,
                "Bitte zuerst einen Bereich durch Ziehen auswählen.",
                "Kein Bereich ausgewählt", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Clamp to image bounds
        int x = Math.max(0, cropRect.x);
        int y = Math.max(0, cropRect.y);
        int w = Math.min(scannedImage.getWidth()  - x, cropRect.width);
        int h = Math.min(scannedImage.getHeight() - y, cropRect.height);
        if (w <= 0 || h <= 0) return;

        // getSubimage shares the raster — make an independent copy
        int type = scannedImage.getType();
        if (type == BufferedImage.TYPE_CUSTOM || type == 0) type = BufferedImage.TYPE_INT_RGB;
        BufferedImage cropped = new BufferedImage(w, h, type);
        Graphics2D g2 = cropped.createGraphics();
        g2.drawImage(scannedImage, 0, 0, w, h, x, y, x + w, y + h, null);
        g2.dispose();

        scannedImage = cropped;
        imageViewPanel.setImage(cropped);
        setStatus("Zugeschnitten: " + w + " × " + h + " px", new Color(0, 120, 0));
    }

    /**
     * Rotates the current image 90 degrees clockwise.
     */
    private void rotateImage() {
        if (scannedImage == null) return;
        int w = scannedImage.getWidth();
        int h = scannedImage.getHeight();
        int type = scannedImage.getType();
        if (type == BufferedImage.TYPE_CUSTOM || type == 0) type = BufferedImage.TYPE_INT_RGB;

        BufferedImage rotated = new BufferedImage(h, w, type);
        Graphics2D g2 = rotated.createGraphics();
        g2.transform(AffineTransform.getRotateInstance(Math.PI / 2, 0, 0));
        g2.transform(AffineTransform.getTranslateInstance(0, -h));
        g2.drawImage(scannedImage, 0, 0, null);
        g2.dispose();

        scannedImage = rotated;
        imageViewPanel.setImage(rotated);
        setStatus("Gedreht: " + rotated.getWidth() + " × " + rotated.getHeight() + " px", new Color(0, 120, 0));
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    private void saveImage() {
        if (scannedImage == null) return;

        FileNameExtensionFilter jpgFilter = new FileNameExtensionFilter("JPEG-Bilder (*.jpg)", "jpg", "jpeg");
        FileNameExtensionFilter pngFilter = new FileNameExtensionFilter("PNG-Bilder (*.png)", "png");

        JFileChooser chooser = new JFileChooser(lastSaveDirectory);
        chooser.setDialogTitle("Scan speichern");
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.addChoosableFileFilter(jpgFilter); // JPEG first = default
        chooser.addChoosableFileFilter(pngFilter);
        chooser.setFileFilter(jpgFilter);
        chooser.setSelectedFile(new File("scan.jpg"));

        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File dest = chooser.getSelectedFile();
        boolean isJpeg = chooser.getFileFilter() == jpgFilter;
        String ext = isJpeg ? ".jpg" : ".png";
        String fmt = isJpeg ? "jpg"  : "png";

        // Auto-append extension if not already present
        String name = dest.getName().toLowerCase();
        if (!name.endsWith(".jpg") && !name.endsWith(".jpeg") && !name.endsWith(".png")) {
            dest = new File(dest.getAbsolutePath() + ext);
        }

        try {
            BufferedImage toWrite = scannedImage;
            if (isJpeg && scannedImage.getColorModel().hasAlpha()) {
                // JPEG does not support alpha — flatten onto white background
                BufferedImage flat = new BufferedImage(
                    scannedImage.getWidth(), scannedImage.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D g2 = flat.createGraphics();
                g2.setColor(Color.WHITE);
                g2.fillRect(0, 0, flat.getWidth(), flat.getHeight());
                g2.drawImage(scannedImage, 0, 0, null);
                g2.dispose();
                toWrite = flat;
            }
            ImageIO.write(toWrite, fmt, dest);
            lastSaveDirectory = dest.getParentFile();
            setStatus("Gespeichert: " + dest.getName(), new Color(0, 120, 0));
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Fehler beim Speichern:\n" + ex.getMessage(),
                "Speicherfehler", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void setStatus(String text, Color color) {
        lblStatus.setText(text);
        lblStatus.setForeground(color);
    }

    private JButton buildRetroButton(String label) {
        return new JButton(label);
    }

    private JButton buildSmallButton(String label, String tooltip) {
        JButton btn = new JButton(label);
        btn.setToolTipText(tooltip);
        return btn;
    }

    @Override
    public void dispose() {
        if (tempScanFile != null && tempScanFile.exists()) tempScanFile.delete();
        super.dispose();
    }

    // ── ScannerInfo record ────────────────────────────────────────────────────

    /**
     * Represents a scanner device returned by the enumeration backends.
     *
     * @param displayName Human-readable name shown in the selection dialog.
     * @param deviceId    Platform-specific device identifier:
     *                    <ul>
     *                      <li>Windows: WIA {@code DeviceID} string</li>
     *                      <li>Linux: SANE device name (e.g. {@code epson2:usb:001:003})</li>
     *                    </ul>
     */
    private record ScannerInfo(String displayName, String deviceId) {}

    // ── Windows WIA backend ───────────────────────────────────────────────────

    /**
     * Windows backend using WIA via PowerShell.
     *
     * <p><b>Prerequisites:</b>
     * <ul>
     *   <li>WIA-compatible scanner driver (see README.md)</li>
     *   <li>PowerShell 5+ (included with Windows 10/11)</li>
     * </ul>
     */
    private static class WindowsWiaScanBackend {

        private static final String WIA_FORMAT_PNG = "{B96B3CAF-0728-11D3-9D7B-0000F81EF32E}";

        private final ScannerInfo scanner;

        WindowsWiaScanBackend(ScannerInfo scanner) { this.scanner = scanner; }

        /** Lists all WIA scanner devices (DeviceType == 1). */
        static List<ScannerInfo> listScanners() throws IOException, InterruptedException {
            String script = String.join("\n",
                "$wia = New-Object -ComObject WIA.DeviceManager",
                "foreach ($d in $wia.DeviceInfos) {",
                "  if ($d.Type -eq 1) {",
                "    Write-Output \"$($d.DeviceID)|$($d.Properties['Name'].Value)\"",
                "  }",
                "}"
            );
            ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script);
            pb.redirectErrorStream(false);
            Process proc = pb.start();
            String stdout = new String(proc.getInputStream().readAllBytes());
            proc.waitFor();

            List<ScannerInfo> result = new ArrayList<>();
            for (String line : stdout.split("\\r?\\n")) {
                line = line.trim();
                if (line.isEmpty()) continue;
                int sep = line.indexOf('|');
                if (sep > 0) {
                    result.add(new ScannerInfo(line.substring(sep + 1).trim(), line.substring(0, sep).trim()));
                }
            }
            return result;
        }

        File scan() throws IOException, InterruptedException {
            File outFile = File.createTempFile("euroscan_", ".png");
            outFile.deleteOnExit();
            // WIA SaveFile refuses to overwrite — delete the pre-created placeholder first
            outFile.delete();

            String deviceConnect;
            if (scanner != null) {
                String escapedId = scanner.deviceId().replace("'", "''");
                deviceConnect = String.join("\n",
                    "$wia = New-Object -ComObject WIA.DeviceManager",
                    "$devInfo = $null",
                    "foreach ($d in $wia.DeviceInfos) {",
                    "  if ($d.DeviceID -eq '" + escapedId + "') { $devInfo = $d; break }",
                    "}",
                    "if ($devInfo -eq $null) { throw 'Scanner nicht gefunden: " + escapedId + "' }",
                    "$device = $devInfo.Connect()");
            } else {
                deviceConnect = String.join("\n",
                    "$wia = New-Object -ComObject WIA.DeviceManager",
                    "$devices = $wia.DeviceInfos",
                    "if ($devices.Count -eq 0) { throw 'Kein WIA-Scanner gefunden.' }",
                    "$devInfo = $devices.Item(1)",
                    "$device = $devInfo.Connect()");
            }

            String script = deviceConnect + "\n" + String.join("\n",
                "$item = $device.Items.Item(1)",
                // WIA_IPA_DATATYPE=4103: 0=bitonal, 2=grey, 4=color
                "try { $item.Properties(4103).Value = 4   } catch {}",
                // WIA_IPS_XRES=6147, WIA_IPS_YRES=6148
                "try { $item.Properties(6147).Value = 200 } catch {}",
                "try { $item.Properties(6148).Value = 200 } catch {}",
                "$image = $item.Transfer('" + WIA_FORMAT_PNG + "')",
                "$image.SaveFile('" + outFile.getAbsolutePath().replace("\\", "\\\\") + "')");

            ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script);
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            String output = new String(proc.getInputStream().readAllBytes());
            int exitCode = proc.waitFor();

            if (exitCode != 0) throw new IOException("WIA-Scan fehlgeschlagen (Exit " + exitCode + "):\n" + output.trim());
            if (!outFile.exists() || outFile.length() == 0)
                throw new IOException("WIA lieferte kein Bild. Ist der Scanner angeschlossen und eingeschaltet?\n" + output.trim());
            return outFile;
        }
    }

    // ── Linux/macOS SANE backend ──────────────────────────────────────────────

    /**
     * Linux/macOS backend using the SANE {@code scanimage} CLI.
     *
     * <p><b>Prerequisites:</b>
     * <ul>
     *   <li>{@code sudo apt install sane-utils} (Debian/Ubuntu)</li>
     *   <li>Verify with {@code scanimage -L}</li>
     * </ul>
     */
    private static class LinuxSaneScanBackend {

        private final ScannerInfo scanner;

        LinuxSaneScanBackend(ScannerInfo scanner) { this.scanner = scanner; }

        /** Runs {@code scanimage -L} and parses device entries. */
        static List<ScannerInfo> listScanners() throws IOException, InterruptedException {
            ProcessBuilder pb = new ProcessBuilder("scanimage", "-L");
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            String output = new String(proc.getInputStream().readAllBytes());
            proc.waitFor();

            List<ScannerInfo> result = new ArrayList<>();
            for (String line : output.split("\\r?\\n")) {
                line = line.trim();
                if (!line.startsWith("device")) continue;
                int backtick = line.indexOf('`');
                int quote    = line.indexOf('\'', backtick + 1);
                int isA      = line.indexOf(" is a ");
                if (backtick < 0 || quote < 0) continue;
                String deviceId   = line.substring(backtick + 1, quote);
                String desc = (isA >= 0) ? line.substring(isA + 6).trim() : deviceId;
                result.add(new ScannerInfo(desc + "  [" + deviceId + "]", deviceId));
            }
            return result;
        }

        File scan() throws IOException, InterruptedException {
            Process check = new ProcessBuilder("which", "scanimage").start();
            check.waitFor();
            if (check.exitValue() != 0) throw new IOException(
                "'scanimage' nicht gefunden.\n" +
                "  sudo apt install sane-utils    (Debian/Ubuntu)\n" +
                "  sudo dnf install sane-backends (Fedora)");

            File outFile = File.createTempFile("euroscan_", ".pnm");
            outFile.deleteOnExit();

            List<String> cmd = new ArrayList<>(List.of("scanimage", "--format=pnm", "--resolution=200",
                "--mode=Color", "--output-file=" + outFile.getAbsolutePath()));
            if (scanner != null) cmd.add("--device-name=" + scanner.deviceId());

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            String output = new String(proc.getInputStream().readAllBytes());
            int exitCode = proc.waitFor();

            if (exitCode != 0) throw new IOException("scanimage fehlgeschlagen (Exit " + exitCode + "):\n" + output.trim());
            if (!outFile.exists() || outFile.length() == 0)
                throw new IOException("scanimage lieferte kein Bild.\n" + output.trim());

            BufferedImage img = ImageIO.read(outFile);
            outFile.delete();
            if (img == null) throw new IOException("PNM-Datei konnte nicht gelesen werden.");

            File pngFile = File.createTempFile("euroscan_", ".png");
            pngFile.deleteOnExit();
            ImageIO.write(img, "png", pngFile);
            return pngFile;
        }
    }

    // ── Image viewer panel ────────────────────────────────────────────────────

    /**
     * Displays a {@link BufferedImage} scaled to fit within its bounds, with an interactive
     * crop-selection overlay. The user drags to create a rectangle; 8 resize handles
     * (corners and midpoints) allow adjusting the selection. A dark overlay dims the
     * area outside the selection, and a dimension label shows the crop size in image pixels.
     */
    private class ImageViewPanel extends JPanel {

        // ── Display transform (updated every paintComponent) ─────────────────
        private int    imgX, imgY, imgW, imgH; // zoomed+panned display rect
        private double displayScale = 1.0;      // actual pixels-per-image-pixel

        // ── Zoom & pan state ──────────────────────────────────────────────────
        private double  zoomFactor  = 1.0;  // multiplied on top of fit-to-panel scale
        private double  panX        = 0.0;
        private double  panY        = 0.0;
        private Point   panDragStart = null;
        private double  panStartX, panStartY;

        private static final double ZOOM_STEP = 1.2;
        private static final double ZOOM_MIN  = 0.2;
        private static final double ZOOM_MAX  = 16.0;

        // ── Crop selection ───────────────────────────────────────────────────
        private Rectangle selection    = null; // in panel coordinates (for drawing/dragging)
        private Rectangle selectionImg = null; // in image coordinates (survives zoom/pan)

        // ── Crop drag state ───────────────────────────────────────────────────
        private enum DragMode { NONE, DRAWING, MOVING, RESIZING }
        private DragMode  dragMode     = DragMode.NONE;
        private Point     dragStart    = null;
        private Rectangle dragOrigRect = null;
        private int       resizeHandle = -1;

        private static final int HANDLE_SIZE = 8;
        private static final int[] RESIZE_CURSORS = {
            Cursor.NW_RESIZE_CURSOR, Cursor.N_RESIZE_CURSOR,
            Cursor.NE_RESIZE_CURSOR, Cursor.E_RESIZE_CURSOR,
            Cursor.SE_RESIZE_CURSOR, Cursor.S_RESIZE_CURSOR,
            Cursor.SW_RESIZE_CURSOR, Cursor.W_RESIZE_CURSOR
        };

        ImageViewPanel() {
            setBackground(new Color(40, 40, 40));
            setPreferredSize(new Dimension(APP_WIDTH, 520));

            MouseAdapter ma = new MouseAdapter() {
                @Override public void mousePressed (MouseEvent e) { onMousePressed(e);  }
                @Override public void mouseDragged (MouseEvent e) { onMouseDragged(e);  }
                @Override public void mouseReleased(MouseEvent e) { onMouseReleased(e); }
                @Override public void mouseMoved   (MouseEvent e) { onMouseMoved(e);    }
                @Override public void mouseWheelMoved(java.awt.event.MouseWheelEvent e) { onMouseWheel(e); }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
            addMouseWheelListener(ma);
        }

        // ── Public API ────────────────────────────────────────────────────────

        void setImage(BufferedImage img) {
            selection    = null;
            selectionImg = null;
            dragMode     = DragMode.NONE;
            zoomFactor   = 1.0;
            panX         = 0.0;
            panY         = 0.0;
            setCursor(img != null ? Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)
                                  : Cursor.getDefaultCursor());
            updateZoomLabel();
            repaint();
        }

        void zoomIn()    { applyZoom(ZOOM_STEP,      getWidth() / 2.0, getHeight() / 2.0); }
        void zoomOut()   { applyZoom(1.0 / ZOOM_STEP, getWidth() / 2.0, getHeight() / 2.0); }
        void resetZoom() { zoomFactor = 1.0; panX = 0; panY = 0; updateZoomLabel(); repaint(); }

        /** Applies a zoom multiplier centred on the given panel point. */
        private void applyZoom(double factor, double cx, double cy) {
            if (scannedImage == null) return;
            double newZoom = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, zoomFactor * factor));
            if (newZoom == zoomFactor) return;

            // Keep the point under the cursor fixed in image space
            double oldScale = displayScale > 0 ? displayScale : 1.0;
            double imgPx = (cx - imgX) / oldScale;
            double imgPy = (cy - imgY) / oldScale;

            zoomFactor = newZoom;
            // Recalculate new display scale temporarily to recompute pan
            int pw = getWidth(), ph = getHeight();
            double fitScale = Math.min((double) pw / scannedImage.getWidth(),
                                       (double) ph / scannedImage.getHeight());
            double newScale = fitScale * zoomFactor;
            double newDispW = scannedImage.getWidth()  * newScale;
            double newDispH = scannedImage.getHeight() * newScale;
            panX = cx - (pw - newDispW) / 2.0 - imgPx * newScale;
            panY = cy - (ph - newDispH) / 2.0 - imgPy * newScale;

            updateZoomLabel();
            repaint();
        }

        private void updateZoomLabel() {
            if (zoomFactor == 1.0) {
                lblZoom.setText("Einpassen");
            } else {
                lblZoom.setText(String.format("%d%%", Math.round(zoomFactor * 100)));
            }
        }

        /**
         * Returns the current crop selection converted to image-pixel coordinates,
         * or {@code null} if no selection exists or no image is loaded.
         */
        Rectangle getCropRectInImageCoords() {
            return selectionImg;
        }

        /** Converts a panel-coordinate rectangle to image coordinates. */
        private Rectangle getCropRectInImageCoordsFromSelection(Rectangle sel) {
            if (sel == null || scannedImage == null || displayScale <= 0) return null;

            Rectangle imgArea = new Rectangle(imgX, imgY, imgW, imgH);
            Rectangle clamped = sel.intersection(imgArea);
            if (clamped.isEmpty()) return null;

            double s = displayScale;

            int ix = (int) Math.round((clamped.x - imgX) / s);
            int iy = (int) Math.round((clamped.y - imgY) / s);
            int iw = (int) Math.round(clamped.width  / s);
            int ih = (int) Math.round(clamped.height / s);

            ix = Math.max(0, Math.min(scannedImage.getWidth()  - 1, ix));
            iy = Math.max(0, Math.min(scannedImage.getHeight() - 1, iy));
            iw = Math.max(1, Math.min(scannedImage.getWidth()  - ix, iw));
            ih = Math.max(1, Math.min(scannedImage.getHeight() - iy, ih));

            return new Rectangle(ix, iy, iw, ih);
        }

        /** Converts an image-coordinate rectangle to panel coordinates. */
        private Rectangle imageToPanel(Rectangle r) {
            if (r == null || scannedImage == null || displayScale <= 0) return null;
            int px = (int) Math.round(imgX + r.x * displayScale);
            int py = (int) Math.round(imgY + r.y * displayScale);
            int pw = (int) Math.round(r.width * displayScale);
            int ph = (int) Math.round(r.height * displayScale);
            return new Rectangle(px, py, pw, ph);
        }

        // ── Mouse interaction ─────────────────────────────────────────────────

        private void onMousePressed(MouseEvent e) {
            if (scannedImage == null) return;
            // Right-click = pan
            if (e.getButton() == MouseEvent.BUTTON3) {
                panDragStart = e.getPoint();
                panStartX    = panX;
                panStartY    = panY;
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                return;
            }
            Point p = e.getPoint();
            if (selection != null) {
                int h = getHandleAt(p);
                if (h >= 0) {
                    dragMode = DragMode.RESIZING;
                    resizeHandle = h;
                    dragStart = p;
                    dragOrigRect = new Rectangle(selection);
                    return;
                }
                if (selection.contains(p)) {
                    dragMode = DragMode.MOVING;
                    dragStart = p;
                    dragOrigRect = new Rectangle(selection);
                    return;
                }
            }
            dragMode  = DragMode.DRAWING;
            dragStart = p;
            selection = new Rectangle(p.x, p.y, 0, 0);
            selectionImg = getCropRectInImageCoordsFromSelection(selection);
            repaint();
        }

        private void onMouseDragged(MouseEvent e) {
            // Right-drag = pan
            if (panDragStart != null) {
                panX = panStartX + (e.getX() - panDragStart.x);
                panY = panStartY + (e.getY() - panDragStart.y);
                repaint();
                return;
            }
            if (dragMode == DragMode.NONE || dragStart == null) return;

            Point p = e.getPoint();
            int pw = getWidth(), ph = getHeight();
            int margin = 20;
            int scrollSpeed = 8;
            int dx_pan = 0;
            int dy_pan = 0;

            // Pan left/right if mouse is near or outside viewport borders
            if (p.x < margin) {
                if (imgX < 0) {
                    dx_pan = Math.min(scrollSpeed, -imgX);
                }
            } else if (p.x > pw - margin) {
                if (imgX + imgW > pw) {
                    dx_pan = -Math.min(scrollSpeed, imgX + imgW - pw);
                }
            }

            // Pan up/down
            if (p.y < margin) {
                if (imgY < 0) {
                    dy_pan = Math.min(scrollSpeed, -imgY);
                }
            } else if (p.y > ph - margin) {
                if (imgY + imgH > ph) {
                    dy_pan = -Math.min(scrollSpeed, imgY + imgH - ph);
                }
            }

            if (dx_pan != 0 || dy_pan != 0) {
                panX += dx_pan;
                panY += dy_pan;
                dragStart.x += dx_pan;
                dragStart.y += dy_pan;
                if (dragOrigRect != null) {
                    dragOrigRect.x += dx_pan;
                    dragOrigRect.y += dy_pan;
                }
                // Keep panel-coordinate display position in sync
                imgX += dx_pan;
                imgY += dy_pan;
            }

            int dx = p.x - dragStart.x;
            int dy = p.y - dragStart.y;

            switch (dragMode) {
                case DRAWING  -> selection = normalizeRect(dragStart.x, dragStart.y, p.x, p.y);
                case MOVING   -> { selection = new Rectangle(dragOrigRect); selection.translate(dx, dy); }
                case RESIZING -> applyResize(dx, dy);
                default -> {}
            }
            selectionImg = getCropRectInImageCoordsFromSelection(selection);
            repaint();
        }

        private void onMouseReleased(MouseEvent e) {
            if (e.getButton() == MouseEvent.BUTTON3) {
                panDragStart = null;
                onMouseMoved(e);
                return;
            }
            if (selection != null && (selection.width < 4 || selection.height < 4)) selection = null;
            selectionImg = getCropRectInImageCoordsFromSelection(selection);
            dragMode     = DragMode.NONE;
            dragStart    = null;
            dragOrigRect = null;
            resizeHandle = -1;
            repaint();
        }

        private void onMouseWheel(java.awt.event.MouseWheelEvent e) {
            double factor = (e.getWheelRotation() < 0) ? ZOOM_STEP : 1.0 / ZOOM_STEP;
            applyZoom(factor, e.getX(), e.getY());
        }

        private void onMouseMoved(MouseEvent e) {
            if (scannedImage == null) { setCursor(Cursor.getDefaultCursor()); return; }
            Point p = e.getPoint();
            if (selection != null) {
                int h = getHandleAt(p);
                if (h >= 0) { setCursor(Cursor.getPredefinedCursor(RESIZE_CURSORS[h])); return; }
                if (selection.contains(p)) { setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)); return; }
            }
            setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        }

        // ── Resize ────────────────────────────────────────────────────────────

        private void applyResize(int dx, int dy) {
            if (dragOrigRect == null) return;
            int x = dragOrigRect.x, y = dragOrigRect.y, w = dragOrigRect.width, h = dragOrigRect.height;
            switch (resizeHandle) {
                case 0 -> { x += dx; y += dy; w -= dx; h -= dy; } // NW
                case 1 -> {           y += dy;           h -= dy; } // N
                case 2 -> {           y += dy; w += dx;  h -= dy; } // NE
                case 3 -> {                    w += dx;           } // E
                case 4 -> {                    w += dx;  h += dy; } // SE
                case 5 -> {                              h += dy; } // S
                case 6 -> { x += dx;           w -= dx;  h += dy; } // SW
                case 7 -> { x += dx;           w -= dx;           } // W
            }
            if (w < 0) { x += w; w = -w; }
            if (h < 0) { y += h; h = -h; }
            selection = new Rectangle(x, y, w, h);
        }

        // ── Handle geometry ───────────────────────────────────────────────────

        private Point[] getHandlePoints() {
            if (selection == null) return new Point[0];
            int r  = selection.x + selection.width;
            int b  = selection.y + selection.height;
            int cx = selection.x + selection.width  / 2;
            int cy = selection.y + selection.height / 2;
            return new Point[]{
                new Point(selection.x, selection.y), // 0 NW
                new Point(cx,          selection.y), // 1 N
                new Point(r,           selection.y), // 2 NE
                new Point(r,           cy),           // 3 E
                new Point(r,           b),            // 4 SE
                new Point(cx,          b),            // 5 S
                new Point(selection.x, b),            // 6 SW
                new Point(selection.x, cy),           // 7 W
            };
        }

        private int getHandleAt(Point p) {
            Point[] handles = getHandlePoints();
            int hs = HANDLE_SIZE / 2 + 2; // slightly larger hit area
            for (int i = 0; i < handles.length; i++) {
                if (Math.abs(p.x - handles[i].x) <= hs && Math.abs(p.y - handles[i].y) <= hs) return i;
            }
            return -1;
        }

        private Rectangle normalizeRect(int x1, int y1, int x2, int y2) {
            return new Rectangle(Math.min(x1, x2), Math.min(y1, y2),
                                 Math.abs(x2 - x1), Math.abs(y2 - y1));
        }



        // ── Paint ─────────────────────────────────────────────────────────────

@Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,  RenderingHints.VALUE_ANTIALIAS_ON);

            int pw = getWidth(), ph = getHeight();

            if (scannedImage == null) {
                imgX = imgY = imgW = imgH = 0;
                displayScale = 1.0;
                drawEmptyPlaceholder(g2, pw, ph);
            } else {
                // Base scale = fit-to-panel, then multiply by zoomFactor
                double fitScale = Math.min((double) pw / scannedImage.getWidth(),
                                           (double) ph / scannedImage.getHeight());
                displayScale = fitScale * zoomFactor;

                imgW = (int) (scannedImage.getWidth()  * displayScale);
                imgH = (int) (scannedImage.getHeight() * displayScale);
                imgX = (int) ((pw - imgW) / 2.0 + panX);
                imgY = (int) ((ph - imgH) / 2.0 + panY);

                if (dragMode == DragMode.NONE) {
                    selection = imageToPanel(selectionImg);
                }

                // Drop shadow (only when image fits comfortably)
                g2.setColor(new Color(0, 0, 0, 80));
                g2.fillRect(imgX + 4, imgY + 4, imgW, imgH);

                g2.drawImage(scannedImage, imgX, imgY, imgW, imgH, null);

                g2.setColor(new Color(200, 200, 200, 100));
                g2.setStroke(new BasicStroke(1f));
                g2.drawRect(imgX, imgY, imgW - 1, imgH - 1);

                if (selection != null) drawCropOverlay(g2, pw, ph);
            }
            g2.dispose();
        }

        private void drawCropOverlay(Graphics2D g2, int pw, int ph) {
            // Dim area outside selection
            Area outside = new Area(new Rectangle(0, 0, pw, ph));
            outside.subtract(new Area(selection));
            g2.setColor(new Color(0, 0, 0, 110));
            g2.fill(outside);

            // Marching-ants dashed border (two offset passes)
            float[] dash = {6f, 4f};
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, dash, 0f));
            g2.setColor(Color.WHITE);
            g2.drawRect(selection.x, selection.y, selection.width, selection.height);
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, dash, 5f));
            g2.setColor(new Color(0, 40, 60));
            g2.drawRect(selection.x, selection.y, selection.width, selection.height);

            // Rule-of-thirds grid inside selection (subtle guides)
            g2.setColor(new Color(255, 255, 255, 40));
            g2.setStroke(new BasicStroke(0.5f));
            int tw = selection.width / 3, th = selection.height / 3;
            for (int i = 1; i < 3; i++) {
                g2.drawLine(selection.x + tw * i, selection.y, selection.x + tw * i, selection.y + selection.height);
                g2.drawLine(selection.x, selection.y + th * i, selection.x + selection.width, selection.y + th * i);
            }

            // Resize handles
            Point[] handles = getHandlePoints();
            int hs = HANDLE_SIZE / 2;
            for (Point hp : handles) {
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(1f));
                g2.fillRect(hp.x - hs, hp.y - hs, HANDLE_SIZE, HANDLE_SIZE);
                g2.setColor(new Color(0, 90, 110));
                g2.drawRect(hp.x - hs, hp.y - hs, HANDLE_SIZE, HANDLE_SIZE);
            }

            // Dimension label
            Rectangle imgRect = getCropRectInImageCoords();
            if (imgRect != null) {
                String label = imgRect.width + " × " + imgRect.height + " px";
                g2.setFont(new Font("Courier New", Font.BOLD, 10));
                FontMetrics fm = g2.getFontMetrics();
                int lw = fm.stringWidth(label) + 8;
                int lh = fm.getHeight() + 4;
                int lx = selection.x + (selection.width - lw) / 2;
                int ly = selection.y + selection.height + 4;
                if (ly + lh > ph - 4) ly = selection.y - lh - 4;
                g2.setColor(new Color(0, 0, 0, 180));
                g2.fillRoundRect(lx, ly, lw, lh, 6, 6);
                g2.setColor(Color.WHITE);
                g2.drawString(label, lx + 4, ly + fm.getAscent() + 2);
            }
        }

        private void drawEmptyPlaceholder(Graphics2D g2, int pw, int ph) {
            int tileSize = 20;
            for (int y = 0; y < ph; y += tileSize) {
                for (int x = 0; x < pw; x += tileSize) {
                    boolean even = ((x / tileSize) + (y / tileSize)) % 2 == 0;
                    g2.setColor(even ? new Color(50, 50, 50) : new Color(42, 42, 42));
                    g2.fillRect(x, y, tileSize, tileSize);
                }
            }
            int margin = 40;
            g2.setColor(new Color(80, 80, 80));
            g2.setStroke(new BasicStroke(2f));
            g2.drawRoundRect(margin, margin, pw - margin * 2, ph - margin * 2, 8, 8);

            int cx = pw / 2, cy = ph / 2;
            g2.setColor(new Color(100, 140, 160));
            g2.setStroke(new BasicStroke(2f));
            g2.drawRect(cx - 28, cy - 36, 56, 70);
            for (int i = 0; i < 6; i++) {
                g2.setColor(new Color(80, 120, 140, 160));
                g2.drawLine(cx - 20, cy - 24 + i * 10, cx + 20, cy - 24 + i * 10);
            }
            g2.setColor(new Color(0, 180, 220, 200));
            g2.setStroke(new BasicStroke(3f));
            g2.drawLine(cx - 28, cy - 10, cx + 28, cy - 10);

            g2.setFont(new Font("Courier New", Font.PLAIN, 12));
            g2.setColor(new Color(140, 140, 140));
            FontMetrics fm = g2.getFontMetrics();
            String msg = "Kein Scan vorhanden";
            g2.drawString(msg, (pw - fm.stringWidth(msg)) / 2, cy + 58);
            g2.setFont(new Font("Courier New", Font.PLAIN, 10));
            fm = g2.getFontMetrics();
            g2.setColor(new Color(100, 100, 100));
            String hint = "Drücke \"Scanner...\" zum Auswählen, dann \"Scannen\"";
            g2.drawString(hint, (pw - fm.stringWidth(hint)) / 2, cy + 74);
        }
    }
}

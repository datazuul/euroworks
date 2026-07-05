package com.datazuul.euroworks.apps.euroweb;

import com.datazuul.euroworks.apps.EuroAppFrame;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebHistory;
import javafx.scene.web.WebView;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * EuroWeb - A feature-rich Swing-based web browser running JavaFX WebView.
 * Visually integrated into the EuroWorks retro desktop shell environment.
 */
public class EuroWeb extends EuroAppFrame {

    private static final int DEFAULT_WIDTH = 850;
    private static final int DEFAULT_HEIGHT = 650;
    private static final Color RETRO_BG = new Color(212, 208, 200); // Standard Win95 Silver

    private JFXPanel jfxPanel;
    private WebEngine webEngine;

    // Swing controls
    private JButton btnBack;
    private JButton btnForward;
    private JButton btnReload;
    private JButton btnStop;
    private JButton btnHome;
    private JButton btnGo;
    private JTextField txtAddress;
    private JLabel lblStatus;
    private JProgressBar progressBar;
    private ThrobberPanel throbber;

    public EuroWeb() {
        super("EuroWeb Web Browser");
        setSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
        setMinimumSize(new Dimension(400, 300));

        // Create main Swing container
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(RETRO_BG);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        // 1. Top Panel: Navigation & URL bar
        mainPanel.add(buildNavigationPanel(), BorderLayout.NORTH);

        // 2. Center Panel: JavaFX WebView container
        jfxPanel = new JFXPanel();
        jfxPanel.setBorder(BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.LOWERED));
        mainPanel.add(jfxPanel, BorderLayout.CENTER);

        // 3. Bottom Panel: Status bar & Progress indicator
        mainPanel.add(buildStatusPanel(), BorderLayout.SOUTH);

        setContentPane(mainPanel);

        // Initialize JavaFX Platform & components
        initJavaFX();
    }

    private JPanel buildNavigationPanel() {
        JPanel navPanel = new JPanel(new BorderLayout(6, 0));
        navPanel.setBackground(RETRO_BG);
        navPanel.setBorder(BorderFactory.createEmptyBorder(2, 2, 6, 2));

        // Left control buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        buttonPanel.setBackground(RETRO_BG);

        btnBack = buildRetroNavButton("◀ Zurück");
        btnBack.setToolTipText("Go Back");
        btnBack.setEnabled(false);
        btnBack.addActionListener(e -> navigateBack());
        buttonPanel.add(btnBack);

        btnForward = buildRetroNavButton("Vorwärts ▶");
        btnForward.setToolTipText("Go Forward");
        btnForward.setEnabled(false);
        btnForward.addActionListener(e -> navigateForward());
        buttonPanel.add(btnForward);

        btnReload = buildRetroNavButton("⟳ Aktualisieren");
        btnReload.setToolTipText("Reload Current Page");
        btnReload.addActionListener(e -> reloadPage());
        buttonPanel.add(btnReload);

        btnStop = buildRetroNavButton("⏹ Stopp");
        btnStop.setToolTipText("Stop Loading");
        btnStop.setEnabled(false);
        btnStop.addActionListener(e -> stopLoading());
        buttonPanel.add(btnStop);

        btnHome = buildRetroNavButton("🏠 Startseite");
        btnHome.setToolTipText("Go to Homepage");
        btnHome.addActionListener(e -> loadHomePage());
        buttonPanel.add(btnHome);

        navPanel.add(buttonPanel, BorderLayout.WEST);

        // Address panel (Center)
        JPanel addressPanel = new JPanel(new BorderLayout(4, 0));
        addressPanel.setBackground(RETRO_BG);

        JLabel lblAddress = new JLabel("Adresse: ");
        lblAddress.setFont(new Font("Courier New", Font.BOLD, 12));
        lblAddress.setForeground(Color.BLACK);
        addressPanel.add(lblAddress, BorderLayout.WEST);

        txtAddress = new JTextField();
        txtAddress.setFont(new Font("Courier New", Font.PLAIN, 12));
        txtAddress.setBackground(Color.WHITE);
        txtAddress.setForeground(Color.BLACK);
        txtAddress.setCaretColor(Color.BLACK);
        txtAddress.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.LOWERED),
                BorderFactory.createEmptyBorder(3, 5, 3, 5)
        ));
        txtAddress.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    handleNavigationInput();
                }
            }
        });
        addressPanel.add(txtAddress, BorderLayout.CENTER);

        btnGo = buildRetroNavButton("Los");
        btnGo.setToolTipText("Go to URL / Search");
        btnGo.addActionListener(e -> handleNavigationInput());
        addressPanel.add(btnGo, BorderLayout.EAST);

        navPanel.add(addressPanel, BorderLayout.CENTER);

        // Throbber animation widget (Right)
        throbber = new ThrobberPanel();
        JPanel throbberWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        throbberWrap.setBackground(RETRO_BG);
        throbberWrap.add(throbber);
        navPanel.add(throbberWrap, BorderLayout.EAST);

        return navPanel;
    }

    private JPanel buildStatusPanel() {
        JPanel statusPanel = new JPanel(new BorderLayout(8, 0));
        statusPanel.setBackground(RETRO_BG);
        statusPanel.setBorder(BorderFactory.createEmptyBorder(4, 2, 2, 2));

        lblStatus = new JLabel("Bereit");
        lblStatus.setFont(new Font("Courier New", Font.PLAIN, 11));
        lblStatus.setForeground(Color.BLACK);
        statusPanel.add(lblStatus, BorderLayout.CENTER);

        progressBar = new JProgressBar(0, 100);
        progressBar.setPreferredSize(new Dimension(140, 16));
        progressBar.setStringPainted(true);
        progressBar.setFont(new Font("Courier New", Font.PLAIN, 10));
        progressBar.setForeground(new Color(0, 90, 110)); // Steel Blue
        progressBar.setBackground(RETRO_BG);
        progressBar.setBorder(BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.LOWERED));
        progressBar.setVisible(false);
        statusPanel.add(progressBar, BorderLayout.EAST);

        return statusPanel;
    }

    private JButton buildRetroNavButton(String label) {
        JButton btn = new JButton(label);
        btn.setFont(new Font("Courier New", Font.BOLD, 11));
        btn.setBackground(RETRO_BG);
        btn.setForeground(Color.BLACK);
        btn.setOpaque(true);
        btn.setFocusPainted(false);
        
        Border raised = BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED);
        Border empty = BorderFactory.createEmptyBorder(3, 8, 3, 8);
        btn.setBorder(BorderFactory.createCompoundBorder(raised, empty));

        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                if (btn.isEnabled()) {
                    btn.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.LOWERED), empty));
                }
            }
            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                if (btn.isEnabled()) {
                    btn.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED), empty));
                }
            }
        });

        return btn;
    }

    // ── JavaFX Engine Integration ───────────────────────────────────────────

    private void initJavaFX() {
        Platform.runLater(() -> {
            WebView webView = new WebView();
            webEngine = webView.getEngine();

            // Set browser User-Agent for modern website compatibility
            webEngine.setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) EuroWorks-EuroWeb/1.0 Chrome/120.0.0.0 Safari/537.36");

            Scene scene = new Scene(webView);
            jfxPanel.setScene(scene);

            setupJavaFXListeners();
            loadHomePage();
        });
    }

    private void setupJavaFXListeners() {
        // 1. URL/Location change listener
        webEngine.locationProperty().addListener((obs, oldLoc, newLoc) -> {
            SwingUtilities.invokeLater(() -> {
                if (newLoc != null) {
                    // Don't show confusing raw jar:file URLs to users if loading our homepage
                    if (newLoc.contains("homepage.html")) {
                        txtAddress.setText("euroweb://home");
                    } else {
                        txtAddress.setText(newLoc);
                    }
                }
                updateNavButtons();
            });
        });

        // 2. Document title listener
        webEngine.titleProperty().addListener((obs, oldTitle, newTitle) -> {
            SwingUtilities.invokeLater(() -> {
                if (newTitle != null && !newTitle.isEmpty()) {
                    setTitle("EuroWeb - " + newTitle);
                } else {
                    setTitle("EuroWeb");
                }
            });
        });

        // 3. Worker state listener (for loading animations, progress indicators, and status messages)
        webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            SwingUtilities.invokeLater(() -> {
                boolean active = (newState == Worker.State.RUNNING || newState == Worker.State.SCHEDULED);
                throbber.setLoading(active);
                btnStop.setEnabled(active);

                if (newState == Worker.State.SUCCEEDED) {
                    lblStatus.setText("Bereit");
                    progressBar.setVisible(false);
                } else if (newState == Worker.State.FAILED) {
                    lblStatus.setText("Fehler beim Laden der Seite.");
                    progressBar.setVisible(false);
                } else if (newState == Worker.State.CANCELLED) {
                    lblStatus.setText("Ladevorgang abgebrochen.");
                    progressBar.setVisible(false);
                } else if (newState == Worker.State.RUNNING) {
                    lblStatus.setText("Lade " + webEngine.getLocation() + "...");
                    progressBar.setVisible(true);
                }
                updateNavButtons();
            });
        });

        // 4. Progress listener
        webEngine.getLoadWorker().progressProperty().addListener((obs, oldVal, newVal) -> {
            SwingUtilities.invokeLater(() -> {
                if (newVal != null) {
                    int val = (int) (newVal.doubleValue() * 100);
                    progressBar.setValue(val);
                }
            });
        });
    }

    private void updateNavButtons() {
        // Must fetch history state on JavaFX Application Thread, then apply states in Swing EDT
        Platform.runLater(() -> {
            if (webEngine == null) return;
            WebHistory history = webEngine.getHistory();
            int idx = history.getCurrentIndex();
            int size = history.getEntries().size();
            boolean canGoBack = idx > 0;
            boolean canGoForward = idx < size - 1;

            SwingUtilities.invokeLater(() -> {
                btnBack.setEnabled(canGoBack);
                btnForward.setEnabled(canGoForward);
            });
        });
    }

    // ── Navigation actions ──────────────────────────────────────────────────

    private void loadURL(String url) {
        Platform.runLater(() -> {
            if (webEngine != null) {
                webEngine.load(url);
            }
        });
    }

    private void loadHomePage() {
        Platform.runLater(() -> {
            if (webEngine != null) {
                URL homeUrl = getClass().getResource("/apps/euroweb/homepage.html");
                if (homeUrl != null) {
                    webEngine.load(homeUrl.toExternalForm());
                } else {
                    webEngine.loadContent("<!DOCTYPE html><html><body style='background:#0b0f17;color:white;font-family:sans-serif;'><h2>EuroWeb Homepage nicht gefunden.</h2></body></html>");
                }
            }
        });
    }

    private void navigateBack() {
        Platform.runLater(() -> {
            if (webEngine != null) {
                WebHistory history = webEngine.getHistory();
                if (history.getCurrentIndex() > 0) {
                    history.go(-1);
                }
            }
        });
    }

    private void navigateForward() {
        Platform.runLater(() -> {
            if (webEngine != null) {
                WebHistory history = webEngine.getHistory();
                if (history.getCurrentIndex() < history.getEntries().size() - 1) {
                    history.go(1);
                }
            }
        });
    }

    private void reloadPage() {
        Platform.runLater(() -> {
            if (webEngine != null) {
                webEngine.reload();
            }
        });
    }

    private void stopLoading() {
        Platform.runLater(() -> {
            if (webEngine != null) {
                webEngine.getLoadWorker().cancel();
            }
        });
    }

    private void handleNavigationInput() {
        String input = txtAddress.getText().trim();
        if (input.isEmpty()) return;

        // Custom protocol check to return home
        if ("euroweb://home".equalsIgnoreCase(input)) {
            loadHomePage();
            return;
        }

        String targetUrl;
        // Check if query is likely a search or a URL
        boolean isSearch = input.contains(" ") || !input.contains(".");

        if (isSearch) {
            targetUrl = "https://duckduckgo.com/?q=" + URLEncoder.encode(input, StandardCharsets.UTF_8);
        } else {
            if (!input.toLowerCase().startsWith("http://") &&
                !input.toLowerCase().startsWith("https://") &&
                !input.toLowerCase().startsWith("file://")) {
                targetUrl = "https://" + input;
            } else {
                targetUrl = input;
            }
        }
        loadURL(targetUrl);
    }

    // ── Animated Throbber component ──────────────────────────────────────────

    private static class ThrobberPanel extends JPanel {
        private int angle = 0;
        private boolean loading = false;
        private final Timer timer;

        public ThrobberPanel() {
            setPreferredSize(new Dimension(22, 22));
            setBackground(RETRO_BG);
            
            // Increment angle every 80ms for rotating animation
            timer = new Timer(80, e -> {
                angle = (angle + 30) % 360;
                repaint();
            });
        }

        public void setLoading(boolean loading) {
            this.loading = loading;
            if (loading) {
                timer.start();
            } else {
                timer.stop();
                repaint();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            // Draw Win95 inset frame border
            g2.setColor(new Color(128, 128, 128)); // Dark shadow
            g2.drawRect(0, 0, w - 1, h - 1);
            g2.setColor(Color.WHITE);
            g2.drawLine(1, h - 1, w - 1, h - 1);
            g2.drawLine(w - 1, 1, w - 1, h - 1);

            int cx = w / 2;
            int cy = h / 2;
            int radius = 6;

            if (loading) {
                // Paint animated rotating neon cyan dot wheel
                g2.translate(cx, cy);
                g2.rotate(Math.toRadians(angle));
                for (int i = 0; i < 8; i++) {
                    float alpha = (float) i / 8f;
                    g2.setColor(new Color(0f, 0.9f, 1.0f, alpha)); // Glowy Cyan
                    g2.setStroke(new BasicStroke(2.0f));
                    g2.drawLine(0, -radius + 2, 0, -radius);
                    g2.rotate(Math.toRadians(45));
                }
            } else {
                // Static display: draw clean steel blue retro web symbol 'W'
                g2.setColor(new Color(0, 90, 110)); // Steel Blue
                g2.setFont(new Font("Courier New", Font.BOLD, 12));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString("W", cx - fm.stringWidth("W") / 2, cy + fm.getAscent() / 2 - 1);
            }

            g2.dispose();
        }
    }
}

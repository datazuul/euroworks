package com.datazuul.euroworks.games;

import com.datazuul.euroworks.apps.EuroAppFrame;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jme3.app.SimpleApplication;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Cylinder;
import com.jme3.scene.shape.Dome;
import com.jme3.system.AppSettings;
import com.jme3.util.BufferUtils;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * EuroWorld - Hardware-accelerated 3D World exploration using jMonkeyEngine 3.
 * Features:
 * - Runs in a dedicated native GLFW 3D window to ensure full platform compatibility.
 * - 3D Terrain mesh loading heightmap.png, colormap.png, and treemap.png.
 * - Dynamic camera collision following the terrain height.
 * - 3D Library building and organically spawned 3D tree geometries.
 * - Interactive library interior GUI inside the EuroWorks frame:
 *   - Dialogue with an old librarian behind the desk.
 *   - Search field with real-time book filtering.
 *   - Scrollable list of books read from classpath books.json.
 *   - System web browser redirection for selected book URL.
 */
public class EuroWorld extends EuroAppFrame {

    private static final int GAME_WIDTH = 440;
    private static final int GAME_HEIGHT = 460;
    private static final int MAP_SIZE = 256;

    // Swing UI Components for Library Overlay
    private JPanel mainContainer;
    private CardLayout cardLayout;
    private JPanel startPanel;
    private JPanel libraryPanel;

    // Search and List components
    private JTextField searchField;
    private JList<String> bookList;
    private DefaultListModel<String> listModel;
    private List<Book> allBooks = new ArrayList<>();
    private List<Book> filteredBooks = new ArrayList<>();

    // jME3 Application variables
    private JmeApp jmeApp;
    private volatile boolean jmeAppStarted = false;

    // Player inputs shared with jME3 thread
    private volatile boolean keyW = false;
    private volatile boolean keyS = false;
    private volatile boolean keyA = false;
    private volatile boolean keyD = false;
    private volatile boolean insideLibrary = false;

    // Map data
    private final double[][] heightMap = new double[MAP_SIZE][MAP_SIZE];
    private final Color[][] colorMap = new Color[MAP_SIZE][MAP_SIZE];
    private final List<Point> treeCoords = new ArrayList<>();

    // Library Coordinates (scaled to 3D space)
    private static final float LIB_X = 128f;
    private static final float LIB_Z = 100f;

    // Book data class mapping JSON
    public static class Book {
        private String title;
        private String author;
        private String url;

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getAuthor() { return author; }
        public void setAuthor(String author) { this.author = author; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }

        @Override
        public String toString() {
            return title + " - " + author;
        }
    }

    public EuroWorld() {
        super("EuroWorld (3D World)");
        setSize(GAME_WIDTH + 16, GAME_HEIGHT + 74);

        // Load terrain images and JSON book list
        loadTerrainData();
        loadBooksData();

        // Setup Swing layouts
        cardLayout = new CardLayout();
        mainContainer = new JPanel(cardLayout);

        // Setup Start screen
        buildStartPanel();

        // Setup Library Dialogue Panel
        buildLibraryPanel();

        mainContainer.add(startPanel, "Start");
        mainContainer.add(libraryPanel, "Library");
        setContentPane(mainContainer);

        cardLayout.show(mainContainer, "Start");
    }

    private void loadTerrainData() {
        try {
            InputStream hStream = getClass().getResourceAsStream("/apps/euroworld/heightmap.png");
            InputStream cStream = getClass().getResourceAsStream("/apps/euroworld/colormap.png");
            InputStream tStream = getClass().getResourceAsStream("/apps/euroworld/treemap.png");

            if (hStream != null && cStream != null && tStream != null) {
                BufferedImage hImg = javax.imageio.ImageIO.read(hStream);
                BufferedImage cImg = javax.imageio.ImageIO.read(cStream);
                BufferedImage tImg = javax.imageio.ImageIO.read(tStream);

                for (int x = 0; x < MAP_SIZE; x++) {
                    for (int y = 0; y < MAP_SIZE; y++) {
                        int gray = hImg.getRaster().getSample(x, y, 0);
                        heightMap[x][y] = (gray / 255.0) * 35.0; // scale elevation

                        colorMap[x][y] = new Color(cImg.getRGB(x, y));

                        int treeVal = tImg.getRaster().getSample(x, y, 0);
                        if (treeVal > 0) {
                            treeCoords.add(new Point(x, y));
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Default flat terrain if missing
            for (int x = 0; x < MAP_SIZE; x++) {
                for (int y = 0; y < MAP_SIZE; y++) {
                    heightMap[x][y] = 2.0;
                    colorMap[x][y] = Color.GREEN;
                }
            }
        }
    }

    private void loadBooksData() {
        try {
            InputStream is = getClass().getResourceAsStream("/apps/euroworld/books.json");
            if (is != null) {
                ObjectMapper mapper = new ObjectMapper();
                allBooks = mapper.readValue(is, mapper.getTypeFactory().constructCollectionType(List.class, Book.class));
            }
        } catch (IOException e) {
            // Fallback
            Book b = new Book();
            b.setTitle("EuroWorks Handbuch");
            b.setAuthor("System");
            b.setUrl("https://github.com/datazuul/euroworks");
            allBooks.add(b);
        }
        filteredBooks.addAll(allBooks);
    }

    private void buildStartPanel() {
        startPanel = new JPanel(new GridBagLayout());
        startPanel.setBackground(new Color(40, 44, 52));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = GridBagConstraints.RELATIVE;
        gbc.insets = new Insets(10, 10, 10, 10);

        JLabel titleLabel = new JLabel("EuroWorld 3D");
        titleLabel.setFont(new Font("Georgia", Font.BOLD, 24));
        titleLabel.setForeground(Color.GREEN);
        startPanel.add(titleLabel, gbc);

        JLabel descLabel = new JLabel("<html><center>Erkunde eine hardwarebeschleunigte 3D-Welt.<br>Nutze <b>WASD</b> oder die <b>Pfeiltasten</b> im separaten Fenster zum Steuern.<br>Finde das Bibliotheksgebäude, um Bücher zu durchsuchen.</center></html>");
        descLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        descLabel.setForeground(Color.WHITE);
        startPanel.add(descLabel, gbc);

        JButton start3DBtn = new JButton("3D-Simulation starten");
        start3DBtn.setFont(new Font("SansSerif", Font.BOLD, 14));
        start3DBtn.addActionListener(e -> startJmeApp());
        startPanel.add(start3DBtn, gbc);
    }

    private synchronized void startJmeApp() {
        if (jmeAppStarted) {
            return;
        }
        jmeAppStarted = true;

        AppSettings settings = new AppSettings(true);
        settings.setTitle("EuroWorld 3D Simulation");
        settings.setWidth(640);
        settings.setHeight(480);
        settings.setFrameRate(60);
        settings.setAudioRenderer(null);

        jmeApp = new JmeApp();
        jmeApp.setSettings(settings);
        jmeApp.setShowSettings(false); // Hide settings dialog

        // Map Keyboard Events from JInternalFrame keys if focused
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                handleKeyEvent(e.getKeyCode(), true);
            }
            @Override
            public void keyReleased(KeyEvent e) {
                handleKeyEvent(e.getKeyCode(), false);
            }
        });
        setFocusable(true);

        new Thread(() -> {
            jmeApp.start();
        }).start();
    }

    private void handleKeyEvent(int keyCode, boolean pressed) {
        if (keyCode == KeyEvent.VK_UP || keyCode == KeyEvent.VK_W) keyW = pressed;
        if (keyCode == KeyEvent.VK_DOWN || keyCode == KeyEvent.VK_S) keyS = pressed;
        if (keyCode == KeyEvent.VK_LEFT || keyCode == KeyEvent.VK_A) keyA = pressed;
        if (keyCode == KeyEvent.VK_RIGHT || keyCode == KeyEvent.VK_D) keyD = pressed;
    }

    private void buildLibraryPanel() {
        libraryPanel = new JPanel(new BorderLayout());
        libraryPanel.setBackground(new Color(245, 240, 230)); // Warm library theme

        // Top banner: Librarian Greeting
        JPanel headerPanel = new JPanel(new GridLayout(1, 2, 10, 10));
        headerPanel.setBackground(new Color(101, 67, 33)); // Dark wood
        headerPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JLabel greetingLabel = new JLabel("<html><b>Bibliothekar:</b><br>Guten Tag, junger Reisender.<br>Welches Buch suchst du?</html>");
        greetingLabel.setFont(new Font("Georgia", Font.ITALIC, 14));
        greetingLabel.setForeground(Color.WHITE);
        headerPanel.add(greetingLabel);

        libraryPanel.add(headerPanel, BorderLayout.NORTH);

        // Center: Search & Lists
        JPanel searchPanel = new JPanel(new BorderLayout(5, 5));
        searchPanel.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));
        searchPanel.setBackground(new Color(245, 240, 230));

        JPanel filterRow = new JPanel(new BorderLayout(5, 5));
        filterRow.setBackground(new Color(245, 240, 230));
        filterRow.add(new JLabel("Suchen: "), BorderLayout.WEST);
        searchField = new JTextField();
        filterRow.add(searchField, BorderLayout.CENTER);
        searchPanel.add(filterRow, BorderLayout.NORTH);

        listModel = new DefaultListModel<>();
        updateBookListUI();

        bookList = new JList<>(listModel);
        bookList.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(bookList);
        searchPanel.add(scrollPane, BorderLayout.CENTER);

        libraryPanel.add(searchPanel, BorderLayout.CENTER);

        // Bottom Controls
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        btnPanel.setBackground(new Color(230, 220, 200));

        JButton checkoutBtn = new JButton("Ausleihen & Link öffnen");
        checkoutBtn.addActionListener(e -> {
            int selectedIdx = bookList.getSelectedIndex();
            if (selectedIdx >= 0 && selectedIdx < filteredBooks.size()) {
                Book b = filteredBooks.get(selectedIdx);
                openWebUrl(b.getUrl());
            } else {
                JOptionPane.showMessageDialog(this, "Bitte wähle ein Buch aus.", "Hinweis", JOptionPane.WARNING_MESSAGE);
            }
        });
        btnPanel.add(checkoutBtn);

        JButton leaveBtn = new JButton("Bibliothek verlassen");
        leaveBtn.addActionListener(e -> exitLibraryView());
        btnPanel.add(leaveBtn);

        libraryPanel.add(btnPanel, BorderLayout.SOUTH);

        // Live filtering
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { filterBooks(); }
            @Override public void removeUpdate(DocumentEvent e) { filterBooks(); }
            @Override public void changedUpdate(DocumentEvent e) { filterBooks(); }
        });
    }

    private void filterBooks() {
        String query = searchField.getText().toLowerCase().trim();
        filteredBooks.clear();
        for (Book b : allBooks) {
            if (b.getTitle().toLowerCase().contains(query) || b.getAuthor().toLowerCase().contains(query)) {
                filteredBooks.add(b);
            }
        }
        updateBookListUI();
    }

    private void updateBookListUI() {
        listModel.clear();
        for (Book b : filteredBooks) {
            listModel.addElement(b.toString());
        }
    }

    private void openWebUrl(String urlString) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(new URI(urlString));
            } else {
                JOptionPane.showMessageDialog(this, "Browser kann nicht geöffnet werden. Link: " + urlString, "URL", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Fehler beim Öffnen der URL.", "Fehler", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void enterLibraryView() {
        insideLibrary = true;
        SwingUtilities.invokeLater(() -> {
            cardLayout.show(mainContainer, "Library");
            searchField.setText("");
            searchField.requestFocusInWindow();
            // Reset keys
            keyW = keyS = keyA = keyD = false;
        });
    }

    private void exitLibraryView() {
        insideLibrary = false;
        SwingUtilities.invokeLater(() -> {
            cardLayout.show(mainContainer, "Start");
            // Move player back outside building in 3D
            jmeApp.pushPlayerBack();
        });
    }

    @Override
    public void dispose() {
        if (jmeApp != null) {
            jmeApp.stop();
        }
        super.dispose();
    }

    // ── jMonkeyEngine 3 Application Core ─────────────────────────────────────

    private class JmeApp extends SimpleApplication {

        private Vector3f playerPos = new Vector3f(128, 0, 120);
        private float angle = (float) (-Math.PI / 2);

        private Node worldNode;
        private Geometry terrainGeom;

        private final ActionListener actionListener = new ActionListener() {
            @Override
            public void onAction(String name, boolean isPressed, float tpf) {
                if (name.equals("Forward")) {
                    keyW = isPressed;
                } else if (name.equals("Backward")) {
                    keyS = isPressed;
                } else if (name.equals("Left")) {
                    keyA = isPressed;
                } else if (name.equals("Right")) {
                    keyD = isPressed;
                }
            }
        };

        @Override
        public void simpleInitApp() {
            // Re-enable FlyCam but bound movement to custom logic
            flyCam.setEnabled(false);

            // Register key mappings directly in GLFW context via jME3 inputManager
            inputManager.addMapping("Forward", new KeyTrigger(KeyInput.KEY_W), new KeyTrigger(KeyInput.KEY_UP));
            inputManager.addMapping("Backward", new KeyTrigger(KeyInput.KEY_S), new KeyTrigger(KeyInput.KEY_DOWN));
            inputManager.addMapping("Left", new KeyTrigger(KeyInput.KEY_A), new KeyTrigger(KeyInput.KEY_LEFT));
            inputManager.addMapping("Right", new KeyTrigger(KeyInput.KEY_D), new KeyTrigger(KeyInput.KEY_RIGHT));
            inputManager.addListener(actionListener, "Forward", "Backward", "Left", "Right");

            worldNode = new Node("world");
            rootNode.attachChild(worldNode);

            // Lighting
            DirectionalLight sun = new DirectionalLight();
            sun.setDirection(new Vector3f(-0.5f, -0.8f, -0.5f).normalizeLocal());
            sun.setColor(ColorRGBA.White);
            rootNode.addLight(sun);

            AmbientLight ambient = new AmbientLight();
            ambient.setColor(ColorRGBA.DarkGray);
            rootNode.addLight(ambient);

            // Generate Custom 3D Terrain Mesh
            buildTerrainMesh();

            // Build Library structure
            build3DLibrary();

            // Build Woods (tree cylinders/cones)
            buildWoods();

            cam.setLocation(new Vector3f(playerPos.x, 10f, playerPos.z));
            cam.lookAt(cam.getLocation().add(new Vector3f(0, 0, -1)), Vector3f.UNIT_Y);
        }

        private void buildTerrainMesh() {
            Mesh m = new Mesh();
            int numVertices = MAP_SIZE * MAP_SIZE;
            Vector3f[] vertices = new Vector3f[numVertices];
            ColorRGBA[] colors = new ColorRGBA[numVertices];
            int[] indices = new int[(MAP_SIZE - 1) * (MAP_SIZE - 1) * 6];

            int vIdx = 0;
            for (int z = 0; z < MAP_SIZE; z++) {
                for (int x = 0; x < MAP_SIZE; x++) {
                    vertices[vIdx] = new Vector3f(x, (float) heightMap[x][z], z);
                    Color c = colorMap[x][z];
                    colors[vIdx] = new ColorRGBA(c.getRed()/255f, c.getGreen()/255f, c.getBlue()/255f, 1f);
                    vIdx++;
                }
            }

            int iIdx = 0;
            for (int z = 0; z < MAP_SIZE - 1; z++) {
                for (int x = 0; x < MAP_SIZE - 1; x++) {
                    int tl = z * MAP_SIZE + x;
                    int tr = tl + 1;
                    int bl = (z + 1) * MAP_SIZE + x;
                    int br = bl + 1;

                    // Triangle 1
                    indices[iIdx++] = tl;
                    indices[iIdx++] = bl;
                    indices[iIdx++] = tr;

                    // Triangle 2
                    indices[iIdx++] = tr;
                    indices[iIdx++] = bl;
                    indices[iIdx++] = br;
                }
            }

            m.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(vertices));
            m.setBuffer(VertexBuffer.Type.Color, 4, BufferUtils.createFloatBuffer(colors));
            m.setBuffer(VertexBuffer.Type.Index, 3, BufferUtils.createIntBuffer(indices));
            m.updateBound();

            Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
            mat.setBoolean("VertexColor", true);

            terrainGeom = new Geometry("terrain", m);
            terrainGeom.setMaterial(mat);
            worldNode.attachChild(terrainGeom);
        }

        private void build3DLibrary() {
            Node libNode = new Node("LibraryBuilding");
            libNode.setLocalTranslation(new Vector3f(LIB_X, (float) heightMap[(int) LIB_X][(int) LIB_Z], LIB_Z));

            // Main box
            Box box = new Box(6f, 4f, 6f);
            Geometry body = new Geometry("LibBody", box);
            Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
            mat.setColor("Color", new ColorRGBA(0.6f, 0.4f, 0.25f, 1f));
            body.setMaterial(mat);
            body.setLocalTranslation(0, 4f, 0);
            libNode.attachChild(body);

            // Roof (Dome)
            Dome roof = new Dome(Vector3f.ZERO, 2, 4, 6f, false);
            Geometry roofGeom = new Geometry("LibRoof", roof);
            Material rMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
            rMat.setColor("Color", new ColorRGBA(0.4f, 0.1f, 0.1f, 1f));
            roofGeom.setMaterial(rMat);
            roofGeom.setLocalTranslation(0, 8f, 0);
            libNode.attachChild(roofGeom);

            // Doorway (black block)
            Box door = new Box(1.5f, 2f, 0.1f);
            Geometry doorGeom = new Geometry("LibDoor", door);
            Material dMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
            dMat.setColor("Color", ColorRGBA.Black);
            doorGeom.setMaterial(dMat);
            doorGeom.setLocalTranslation(0, 2f, 6.01f);
            libNode.attachChild(doorGeom);

            worldNode.attachChild(libNode);
        }

        private void buildWoods() {
            Material tMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
            tMat.setColor("Color", new ColorRGBA(0.4f, 0.25f, 0.1f, 1f)); // Trunk

            Material fMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
            fMat.setColor("Color", new ColorRGBA(0.15f, 0.5f, 0.15f, 1f)); // Foliage

            Node forestNode = new Node("forest");

            int limit = Math.min(200, treeCoords.size());
            for (int i = 0; i < limit; i++) {
                Point p = treeCoords.get(i);
                float tx = p.x;
                float tz = p.y;
                float ty = (float) heightMap[p.x][p.y];

                Node tree = new Node("tree_" + i);
                tree.setLocalTranslation(new Vector3f(tx, ty, tz));

                // Trunk
                Cylinder trunk = new Cylinder(2, 6, 0.25f, 2f, true);
                Geometry trunkGeom = new Geometry("trunk", trunk);
                trunkGeom.setMaterial(tMat);
                trunkGeom.setLocalTranslation(0, 1f, 0);
                tree.attachChild(trunkGeom);

                // Leaves (Dome)
                Dome leaves = new Dome(Vector3f.ZERO, 2, 4, 1.2f, false);
                Geometry leavesGeom = new Geometry("leaves", leaves);
                leavesGeom.setMaterial(fMat);
                leavesGeom.setLocalTranslation(0, 2.5f, 0);
                tree.attachChild(leavesGeom);

                forestNode.attachChild(tree);
            }
            worldNode.attachChild(forestNode);
        }

        @Override
        public void simpleUpdate(float tpf) {
            boolean w = keyW;
            boolean s = keyS;
            boolean a = keyA;
            boolean d = keyD;

            if (insideLibrary) return;

            float speed = 12f * tpf;
            float rotSpeed = 2f * tpf;

            if (a) angle += rotSpeed;
            if (d) angle -= rotSpeed;

            Vector3f forward = new Vector3f((float) Math.cos(angle), 0, (float) -Math.sin(angle)).normalizeLocal();

            if (w) {
                playerPos.addLocal(forward.mult(speed));
            }
            if (s) {
                playerPos.addLocal(forward.mult(-speed));
            }

            // Boundary constraints
            playerPos.x = Math.max(5f, Math.min(MAP_SIZE - 5f, playerPos.x));
            playerPos.z = Math.max(5f, Math.min(MAP_SIZE - 5f, playerPos.z));

            // Snap Y to heightmap terrain
            int pxInt = (int) playerPos.x;
            int pzInt = (int) playerPos.z;
            if (pxInt >= 0 && pxInt < MAP_SIZE && pzInt >= 0 && pzInt < MAP_SIZE) {
                playerPos.y = (float) heightMap[pxInt][pzInt] + 2.5f; // eye height level
            }

            cam.setLocation(playerPos);
            cam.lookAt(playerPos.add(forward), Vector3f.UNIT_Y);

            // Collide with library door trigger
            double distToLib = Math.hypot(playerPos.x - LIB_X, playerPos.z - LIB_Z);
            if (distToLib < 7.5) {
                enterLibraryView();
            }
        }

        public void pushPlayerBack() {
            playerPos.x = LIB_X;
            playerPos.z = LIB_Z + 12f;
            angle = (float) (-Math.PI / 2); // face north
        }
    }
}

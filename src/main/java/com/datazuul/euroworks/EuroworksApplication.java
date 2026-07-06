package com.datazuul.euroworks;

import com.datazuul.euroworks.shell.EuroShellFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.JFrame;
import javax.swing.JDialog;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

@SpringBootApplication
public class EuroworksApplication implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(EuroworksApplication.class);

    public static void main(String[] args) {
        // Disable headless mode for AWT/Swing GUI
        System.setProperty("java.awt.headless", "false");
        SpringApplication.run(EuroworksApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        logger.info("EuroWorks Desktop application launching...");

        // Setup external theme directory and copy resources if not present
        setupExternalTheme();

        // Load preferences on startup
        com.datazuul.euroworks.apps.EuroPreferencesStore.load();

        if (GraphicsEnvironment.isHeadless()) {
            logger.warn("Running in headless environment. Skipping GUI startup.");
            return;
        }
        SwingUtilities.invokeLater(() -> {
            try {
                JFrame.setDefaultLookAndFeelDecorated(true);
                JDialog.setDefaultLookAndFeelDecorated(true);
                String lafClass = com.datazuul.euroworks.apps.EuroPreferencesStore.getLookAndFeelClass();
                UIManager.setLookAndFeel(lafClass);
            } catch (Exception e) {
                logger.warn("Could not set look and feel from preferences, using fallback.", e);
                try {
                    UIManager.setLookAndFeel(new org.pushingpixels.radiance.theming.api.skin.RadianceGraphiteChalkLookAndFeel());
                } catch (Exception ex) {
                    // ignore
                }
            }
            EuroShellFrame shellFrame = new EuroShellFrame();
            shellFrame.setVisible(true);
            logger.info("EuroWorks Desktop shell launched successfully!");
        });
    }

    private void setupExternalTheme() {
        String userHome = System.getProperty("user.home");
        File euroworksDir = new File(userHome, ".euroworks");
        
        // Always sync files to keep external directory up-to-date with classpath updates

        logger.info("Setting up external Euro theme under ~/.euroworks...");
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath*:themes/euro/**/*");
            
            for (Resource resource : resources) {
                if (resource.isReadable()) {
                    String uriStr = resource.getURI().toString();
                    int index = uriStr.indexOf("themes/euro/");
                    if (index != -1) {
                        String relativePath = uriStr.substring(index + "themes/euro/".length());
                        if (relativePath.isEmpty()) continue;
                        
                        File targetFile = null;
                        if (relativePath.startsWith("icons/")) {
                            String sub = relativePath.substring(6);
                            targetFile = new File(euroworksDir, "share/icons/euro/icons/" + sub);
                        } else if (relativePath.startsWith("share/applications/")) {
                            String sub = relativePath.substring(19);
                            targetFile = new File(euroworksDir, "share/applications/" + sub);
                        } else if (relativePath.startsWith("config/menus/")) {
                            String sub = relativePath.substring(13);
                            targetFile = new File(euroworksDir, ".config/menus/" + sub);
                        } else if (relativePath.equals("index.theme")) {
                            copyResourceToFile(resource, new File(euroworksDir, "share/themes/euro/index.theme"));
                            targetFile = new File(euroworksDir, "share/icons/euro/index.theme");
                        }
                        
                        if (targetFile != null) {
                            copyResourceToFile(resource, targetFile);
                        }
                    }
                }
            }
            logger.info("Finished setting up external Euro theme.");
        } catch (Exception e) {
            logger.error("Failed to copy Euro theme resources to external directory", e);
        }
    }

    private void copyResourceToFile(Resource resource, File targetFile) throws Exception {
        targetFile.getParentFile().mkdirs();
        try (InputStream is = resource.getInputStream();
             FileOutputStream os = new FileOutputStream(targetFile)) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = is.read(buffer)) != -1) {
                os.write(buffer, 0, read);
            }
        }
        logger.info("Copied: {} -> {}", resource.getFilename(), targetFile.getAbsolutePath());
    }
}

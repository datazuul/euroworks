package com.datazuul.euroworks;

import com.datazuul.euroworks.shell.EuroShellFrame;
import com.datazuul.euroworks.shell.EuroRetroTheme;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.metal.MetalLookAndFeel;
import java.awt.GraphicsEnvironment;

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
        if (GraphicsEnvironment.isHeadless()) {
            logger.warn("Running in headless environment. Skipping GUI startup.");
            return;
        }
        SwingUtilities.invokeLater(() -> {
            try {
                // Apply custom retro theme
                MetalLookAndFeel.setCurrentTheme(new EuroRetroTheme());
                // Turn off default bold fonts for a cleaner monospaced pixel look
                UIManager.put("swing.boldMetal", Boolean.FALSE);
                UIManager.setLookAndFeel(new MetalLookAndFeel());
            } catch (Exception e) {
                logger.warn("Could not set custom retro look and feel, using default.", e);
            }
            EuroShellFrame shellFrame = new EuroShellFrame();
            shellFrame.setVisible(true);
            logger.info("EuroWorks Desktop shell launched successfully!");
        });
    }
}

package com.vku.pulseoj.frontend;

import com.formdev.flatlaf.FlatDarkLaf;
import com.vku.pulseoj.backend.master.MasterBackendController;
import com.vku.pulseoj.frontend.ui.master.MasterMainFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * [TV1 & TV4] Master Server & Teacher Console Application Entry Point.
 * Launches the Master Node with FlatDarkLaf theme.
 */
public class MasterAppLauncher {

    private static final Logger logger = LoggerFactory.getLogger(MasterAppLauncher.class);

    public static void main(String[] args) {
        // 1. Initialize Modern FlatDarkLaf Theme
        try {
            FlatDarkLaf.setup();
            UIManager.put("Button.arc", 8);
            UIManager.put("Component.arc", 8);
            UIManager.put("ProgressBar.arc", 8);
            UIManager.put("TextComponent.arc", 8);
        } catch (Exception e) {
            logger.warn("Failed to initialize FlatDarkLaf: {}", e.getMessage());
        }

        // 2. Launch UI on Swing Event Dispatch Thread (EDT)
        SwingUtilities.invokeLater(() -> {
            try {
                MasterBackendController controller = new MasterBackendController();
                MasterMainFrame frame = new MasterMainFrame(controller);
                frame.setVisible(true);
                logger.info("PulseOJ Master Application UI started.");
            } catch (Exception e) {
                logger.error("Error launching Master Application: {}", e.getMessage(), e);
            }
        });
    }
}

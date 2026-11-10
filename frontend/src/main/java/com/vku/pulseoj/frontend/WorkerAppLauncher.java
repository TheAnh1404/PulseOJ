package com.vku.pulseoj.frontend;

import com.formdev.flatlaf.FlatLightLaf;
import com.vku.pulseoj.backend.worker.WorkerBackendController;
import com.vku.pulseoj.frontend.ui.worker.WorkerMainFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * [TV2] Judge Worker Node Application Entry Point.
 * Launches the Worker Node with FlatLightLaf theme.
 */
public class WorkerAppLauncher {

    private static final Logger logger = LoggerFactory.getLogger(WorkerAppLauncher.class);

    public static void main(String[] args) {
        // 1. Initialize Modern FlatLightLaf Theme
        try {
            FlatLightLaf.setup();
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
                WorkerBackendController controller = new WorkerBackendController();
                WorkerMainFrame frame = new WorkerMainFrame(controller);
                frame.setVisible(true);
                logger.info("PulseOJ Judge Worker UI started.");
            } catch (Exception e) {
                logger.error("Error launching Worker Application: {}", e.getMessage(), e);
            }
        });
    }
}

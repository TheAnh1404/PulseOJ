package com.vku.pulseoj.frontend;

import com.formdev.flatlaf.FlatLightLaf;
import com.vku.pulseoj.backend.client.ClientBackendController;
import com.vku.pulseoj.frontend.ui.client.ClientMainFrame;
import com.vku.pulseoj.frontend.ui.client.LanDiscoveryLoginDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * [TV3] Contestant Client Application Entry Point.
 * Launches the Contestant Workspace with FlatLightLaf theme.
 */
public class ClientAppLauncher {

    private static final Logger logger = LoggerFactory.getLogger(ClientAppLauncher.class);

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

        // 2. Launch Login Dialog on Swing Event Dispatch Thread (EDT)
        SwingUtilities.invokeLater(() -> {
            try {
                ClientBackendController controller = new ClientBackendController();
                LanDiscoveryLoginDialog loginDialog = new LanDiscoveryLoginDialog(null, controller);
                loginDialog.setVisible(true);

                if (loginDialog.isLoggedIn()) {
                    ClientMainFrame mainFrame = new ClientMainFrame(controller);
                    mainFrame.setVisible(true);
                    logger.info("PulseOJ Contestant Workspace UI started.");
                } else {
                    logger.info("Contestant login cancelled. Exiting.");
                    System.exit(0);
                }
            } catch (Exception e) {
                logger.error("Error launching Client Application: {}", e.getMessage(), e);
            }
        });
    }
}

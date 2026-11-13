package com.vku.pulseoj.frontend;

import com.formdev.flatlaf.FlatLightLaf;
import com.vku.pulseoj.backend.client.ClientBackendController;
import com.vku.pulseoj.common.util.NetworkUtils;
import com.vku.pulseoj.frontend.ui.client.ClientMainFrame;
import com.vku.pulseoj.frontend.ui.client.LanDiscoveryLoginDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;

/**
 * [TV3] Contestant Client Application Entry Point.
 * Launches the Contestant Workspace with a modern FlatLightLaf theme synchronized with Master.
 */
public class ClientAppLauncher {

    private static final Logger logger = LoggerFactory.getLogger(ClientAppLauncher.class);

    public static void main(String[] args) {
        // Rule 3: Automatically configure actual LAN IPv4 to avoid 127.0.0.1 loopback error
        NetworkUtils.configureRmiHostname();

        // 1. Initialize Modern FlatLightLaf Theme (Synchronized with MasterAppLauncher)
        try {
            FlatLightLaf.setup();

            UIManager.put("Button.arc", 8);
            UIManager.put("Component.arc", 8);
            UIManager.put("ProgressBar.arc", 8);
            UIManager.put("TextComponent.arc", 8);
            UIManager.put("ScrollBar.thumbArc", 8);
            UIManager.put("ScrollBar.thumbInsets", new Insets(2, 2, 2, 2));

            UIManager.put("Table.rowHeight", 30);
            UIManager.put("Table.intercellSpacing", new Dimension(0, 1));
            UIManager.put("Table.gridColor", new Color(238, 242, 246));
            UIManager.put("TableHeader.height", 34);
            UIManager.put("TableHeader.font", new Font("Segoe UI", Font.BOLD, 12));

            UIManager.put("TabbedPane.tabInsets", new Insets(8, 18, 8, 18));
            UIManager.put("TabbedPane.selectedBackground", Color.WHITE);
            UIManager.put("TabbedPane.showTabSeparators", true);
            UIManager.put("TabbedPane.underlineColor", new Color(37, 99, 235));
            UIManager.put("TabbedPane.selectedForeground", new Color(37, 99, 235));
        } catch (Exception e) {
            logger.warn("Failed to initialize FlatLightLaf: {}", e.getMessage());
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

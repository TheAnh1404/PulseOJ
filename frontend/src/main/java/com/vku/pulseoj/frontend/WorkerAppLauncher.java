package com.vku.pulseoj.frontend;

import com.formdev.flatlaf.FlatLightLaf;
import com.vku.pulseoj.backend.worker.WorkerBackendController;
import com.vku.pulseoj.common.util.NetworkUtils;
import com.vku.pulseoj.frontend.ui.worker.WorkerMainFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;

/**
 * [TV2] Judge Worker Node Application Entry Point.
 * Launches the Worker Node with a clean, modern FlatLightLaf theme synchronized with Master.
 */
public class WorkerAppLauncher {

    private static final Logger logger = LoggerFactory.getLogger(WorkerAppLauncher.class);

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
        } catch (Exception e) {
            logger.warn("Failed to initialize FlatLightLaf: {}", e.getMessage());
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

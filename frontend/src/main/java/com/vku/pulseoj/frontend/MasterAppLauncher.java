package com.vku.pulseoj.frontend;

import com.formdev.flatlaf.FlatLightLaf;
import com.vku.pulseoj.backend.master.MasterBackendController;
import com.vku.pulseoj.frontend.ui.master.MasterMainFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;

/**
 * [TV1 & TV4] Master Server & Teacher Console Application Entry Point.
 * Launches the Master Node with a clean, modern, bright FlatLightLaf theme.
 */
public class MasterAppLauncher {

    private static final Logger logger = LoggerFactory.getLogger(MasterAppLauncher.class);

    public static void main(String[] args) {
        // 1. Initialize Modern FlatLightLaf Theme
        try {
            FlatLightLaf.setup();
            
            // Set modern rounded corners & spacing
            UIManager.put("Button.arc", 8);
            UIManager.put("Component.arc", 8);
            UIManager.put("ProgressBar.arc", 8);
            UIManager.put("TextComponent.arc", 8);
            UIManager.put("ScrollBar.thumbArc", 8);
            UIManager.put("ScrollBar.thumbInsets", new Insets(2, 2, 2, 2));

            // Clean Table styling
            UIManager.put("Table.rowHeight", 30);
            UIManager.put("Table.intercellSpacing", new Dimension(0, 1));
            UIManager.put("Table.gridColor", new Color(238, 242, 246));
            UIManager.put("TableHeader.height", 34);
            UIManager.put("TableHeader.font", new Font("Segoe UI", Font.BOLD, 12));

            // Clean TabbedPane styling
            UIManager.put("TabbedPane.tabInsets", new Insets(8, 18, 8, 18));
            UIManager.put("TabbedPane.selectedBackground", Color.WHITE);
            UIManager.put("TabbedPane.showTabSeparators", true);
            UIManager.put("TabbedPane.underlineColor", new Color(37, 99, 235));
            UIManager.put("TabbedPane.selectedForeground", new Color(37, 99, 235));
        } catch (Exception e) {
            logger.warn("Failed to initialize FlatLightLaf: {}", e.getMessage());
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

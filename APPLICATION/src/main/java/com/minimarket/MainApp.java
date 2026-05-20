package com.minimarket;

import com.minimarket.utils.AppLogger;
import com.minimarket.utils.Config;
import com.minimarket.views.MainWindow;

import javax.swing.*;

/**
 * Punto de entrada del Sistema POS MiniMarket.
 * Tecnologia: Java SE 17 + Swing
 */
public class MainApp {

    public static void main(String[] args) {
        AppLogger.init();
        Config.initDirectories();

        // Usar Nimbus si esta disponible; fallback a Look&Feel del sistema
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception e) {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (Exception ignored) { }
        }

        SwingUtilities.invokeLater(() -> {
            MainWindow window = new MainWindow();
            window.setExtendedState(JFrame.MAXIMIZED_BOTH);
            window.setVisible(true);
            AppLogger.getLogger("MainApp").info(
                "Aplicacion iniciada. BASE_DIR=" + Config.BASE_DIR);
        });
    }
}

package com.minimarket.client;

import com.minimarket.client.socket.SocketClient;
import com.minimarket.client.utils.AppLogger;
import com.minimarket.client.utils.Config;
import com.minimarket.client.views.MainWindow;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Punto de entrada del cliente POS delgado. */
public class MainApp {

    public static void main(String[] args) {
        AppLogger.init();
        Config.initDirectories();
        configureLookAndFeel();

        if (!verificarServidor()) {
            JOptionPane.showMessageDialog(null,
                "No se pudo conectar con el AppServer en "
                    + Config.APP_SERVER_HOST + ":" + Config.APP_SERVER_PORT
                    + ".\nInicie SERVIDOR_APLICACIONES\\dist\\Server.jar y vuelva a intentar.",
                "Servidor de aplicaciones no disponible",
                JOptionPane.ERROR_MESSAGE);
            return;
        }

        SwingUtilities.invokeLater(() -> {
            MainWindow window = new MainWindow();
            window.setExtendedState(JFrame.MAXIMIZED_BOTH);
            window.setVisible(true);
            AppLogger.getLogger("MainApp").info(
                "Cliente POS iniciado. AppServer=" + Config.APP_SERVER_HOST + ":" + Config.APP_SERVER_PORT);
        });
    }

    private static boolean verificarServidor() {
        try {
            return "OK|pong".equals(SocketClient.enviar("PING"));
        } catch (Exception ex) {
            AppLogger.getLogger("MainApp").severe("PING fallido: " + ex.getMessage());
            return false;
        }
    }

    private static void configureLookAndFeel() {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    return;
                }
            }
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
    }
}

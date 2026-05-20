package com.minimarket;

import com.minimarket.utils.AppLogger;
import com.minimarket.utils.Config;
import com.minimarket.views.MainWindow;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Punto de entrada del Sistema POS MiniMarket — version Java.
 *
 * Equivalente Java de APPLICATION/main.py.
 *
 * Ejecutar:
 *   mvn javafx:run             (desde APPLICATION-JAVA/)
 *   o con variable de entorno:
 *   MINIMARKET_HOME=C:\ruta\del\proyecto mvn javafx:run
 */
public class MainApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        // Inicializar sistema de logging
        AppLogger.init();

        // Crear directorios necesarios
        Config.initDirectories();

        // Construir la escena principal
        MainWindow mainWindow = new MainWindow();
        Scene scene = new Scene(mainWindow, 1200, 740);

        primaryStage.setTitle(Config.APP_NAME + " — " + Config.EMPRESA);
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(600);

        // Maximizar al iniciar (comportamiento similar a Tkinter state='zoomed')
        primaryStage.setMaximized(true);

        primaryStage.show();

        AppLogger.getLogger("MainApp")
            .info("Aplicacion iniciada. BASE_DIR=" + Config.BASE_DIR);
    }

    public static void main(String[] args) {
        launch(args);
    }
}

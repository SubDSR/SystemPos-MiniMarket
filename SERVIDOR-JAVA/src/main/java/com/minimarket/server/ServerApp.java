package com.minimarket.server;

import com.minimarket.server.database.DatabaseManager;
import com.minimarket.server.sync.SyncMonitor;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.nio.file.Path;

/**
 * Servidor Central MiniMarket POS — version Java.
 *
 * Interfaz grafica JavaFX que:
 *   - Muestra el estado del monitor de sincronizacion
 *   - Permite iniciar/detener el monitoreo de DATOS/
 *   - Muestra el log de operaciones en tiempo real
 *
 * Equivalente Java de SERVIDOR/update.py (funcion main() + server.py).
 *
 * Ejecutar:
 *   mvn javafx:run             (desde SERVIDOR-JAVA/)
 */
public class ServerApp extends Application {

    // Resolcion de rutas (igual que Config.java del cliente)
    private static final Path BASE_DIR;
    private static final Path SERVIDOR_DIR;
    private static final Path DATOS_DIR;
    private static final Path DB_DIR;

    static {
        String home = System.getenv("MINIMARKET_HOME");
        Path base;
        if (home != null && !home.isBlank()) {
            base = Path.of(home).toAbsolutePath();
        } else {
            base = Path.of(System.getProperty("user.dir")).toAbsolutePath().getParent();
        }
        BASE_DIR     = base;
        SERVIDOR_DIR = base.resolve("SERVIDOR");
        DATOS_DIR    = base.resolve("DATOS");
        DB_DIR       = base.resolve("SERVIDOR").resolve("database");
    }

    private DatabaseManager dbManager;
    private SyncMonitor     monitor;
    private TextArea        logArea;
    private Label           statusLabel;
    private Button          toggleBtn;

    @Override
    public void start(Stage stage) {
        dbManager = new DatabaseManager(DB_DIR.resolve("minimarket.db"));
        monitor   = new SyncMonitor(SERVIDOR_DIR, DATOS_DIR, dbManager, 10);
        monitor.setLogCallback(msg -> Platform.runLater(() -> appendLog(msg)));
        monitor.setStatusCallback(msg -> Platform.runLater(() ->
            statusLabel.setText(msg)));

        Scene scene = new Scene(buildLayout(), 680, 560);
        stage.setTitle("MiniMarket POS — Servidor Central");
        stage.setScene(scene);
        stage.setResizable(true);
        stage.setOnCloseRequest(e -> monitor.stop());

        // Inicializar BD al arrancar
        try {
            dbManager.initDatabase();
            appendLog("[OK] Base de datos lista: " + dbManager.getDbPath());
            appendLog("[INFO] Carpeta monitoreada: " + DATOS_DIR);
        } catch (Exception e) {
            appendLog("[ERROR] BD: " + e.getMessage());
        }

        stage.show();
    }

    // ════════════════════════════════════════════════════════════════════════
    // UI
    // ════════════════════════════════════════════════════════════════════════

    private BorderPane buildLayout() {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #0f172a;");

        // Header
        VBox header = buildHeader();
        root.setTop(header);

        // Log area
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setFont(Font.font("Consolas", 11));
        logArea.setStyle("-fx-control-inner-background: #1e293b;"
            + "-fx-text-fill: #94fa75;"
            + "-fx-border-width: 0;");
        logArea.setWrapText(true);
        BorderPane.setMargin(logArea, new Insets(0, 16, 0, 16));
        root.setCenter(logArea);

        // Footer con botones
        HBox footer = buildFooter();
        root.setBottom(footer);

        return root;
    }

    private VBox buildHeader() {
        VBox header = new VBox(4);
        header.setPadding(new Insets(20, 20, 16, 20));
        header.setStyle("-fx-background-color: #0f172a;");

        Label title = new Label("Servidor de Actualizacion — MiniMarket POS");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 15));
        title.setStyle("-fx-text-fill: white;");

        Label dbPath = new Label("BD: " + DB_DIR.resolve("minimarket.db"));
        dbPath.setFont(Font.font("Consolas", 10));
        dbPath.setStyle("-fx-text-fill: #94a3b8;");

        statusLabel = new Label("Detenido");
        statusLabel.setFont(Font.font("Segoe UI", 11));
        statusLabel.setStyle("-fx-text-fill: #22c55e;");

        header.getChildren().addAll(title, dbPath, statusLabel);
        return header;
    }

    private HBox buildFooter() {
        HBox footer = new HBox(10);
        footer.setPadding(new Insets(12, 16, 16, 16));
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setStyle("-fx-background-color: #0f172a;");

        toggleBtn = new Button("Iniciar Monitor");
        styleBtn(toggleBtn, "#22c55e");
        toggleBtn.setOnAction(e -> toggleMonitor());

        Button scanBtn = new Button("Escanear Ahora");
        styleBtn(scanBtn, "#3b82f6");
        scanBtn.setOnAction(e -> {
            Thread t = new Thread(() -> {
                int n = monitor.scanOnce();
                Platform.runLater(() ->
                    appendLog("[Manual] " + n + " archivo(s) procesado(s)"));
            });
            t.setDaemon(true);
            t.start();
        });

        Button clearBtn = new Button("Limpiar Log");
        styleBtn(clearBtn, "#64748b");
        clearBtn.setOnAction(e -> logArea.clear());

        footer.getChildren().addAll(toggleBtn, scanBtn, clearBtn);
        return footer;
    }

    // ════════════════════════════════════════════════════════════════════════
    // LOGICA
    // ════════════════════════════════════════════════════════════════════════

    private void toggleMonitor() {
        if (!monitor.isRunning()) {
            monitor.start();
            styleBtn(toggleBtn, "#ef4444");
            toggleBtn.setText("Detener Monitor");
            statusLabel.setText("Monitoreando...");
        } else {
            monitor.stop();
            styleBtn(toggleBtn, "#22c55e");
            toggleBtn.setText("Iniciar Monitor");
            statusLabel.setText("Detenido");
        }
    }

    private void appendLog(String msg) {
        logArea.appendText(msg + "\n");
        logArea.setScrollTop(Double.MAX_VALUE);
    }

    private void styleBtn(Button btn, String bg) {
        btn.setStyle("-fx-background-color: " + bg + ";"
            + "-fx-text-fill: white;"
            + "-fx-background-radius: 6;"
            + "-fx-padding: 8 18;"
            + "-fx-font-weight: bold;"
            + "-fx-cursor: hand;");
        btn.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
    }

    public static void main(String[] args) {
        launch(args);
    }
}

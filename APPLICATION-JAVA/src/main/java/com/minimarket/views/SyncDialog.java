package com.minimarket.views;

import com.minimarket.sync.SyncAgent;
import com.minimarket.utils.Config;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Dialogo de sincronizacion con el servidor central.
 * Exporta datos a CSV y los copia a la carpeta compartida de red.
 * Equivalente Java de views/sync_dialog.py.
 */
public class SyncDialog extends Dialog<Void> {

    private TextArea logArea;
    private Label    statusLabel;
    private Button   syncBtn;

    public SyncDialog() {
        setTitle("Sincronizar con Servidor Central");
        setHeaderText(null);
        buildContent();
        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        getDialogPane().setPrefWidth(600);
        getDialogPane().setPrefHeight(460);
    }

    private void buildContent() {
        VBox content = new VBox(14);
        content.setPadding(new Insets(16));
        content.setStyle("-fx-background-color: " + Config.C_MAIN_BG + ";");

        // Titulo
        Label titulo = new Label("Sincronizacion de Datos");
        titulo.setFont(Font.font("Segoe UI", FontWeight.BOLD, 15));
        titulo.setStyle("-fx-text-fill: " + Config.C_TEXT + ";");

        // Info
        Label info = new Label(
            "Exporta los datos locales a CSV y los copia a:\n" +
            "  Red (UNC): " + Config.UNC_DATOS + "\n" +
            "  Fallback: " + Config.DATOS_DIR);
        info.setFont(Font.font("Segoe UI", 11));
        info.setStyle("-fx-text-fill: " + Config.C_TEXT_MUTED + ";");
        info.setWrapText(true);

        // Area de log
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefHeight(220);
        logArea.setFont(Font.font("Consolas", 11));
        logArea.setStyle("-fx-background-color: #1e293b; -fx-text-fill: #94fa75;");
        logArea.setWrapText(true);

        // Status
        statusLabel = new Label("Listo para sincronizar.");
        statusLabel.setFont(Font.font("Segoe UI", 12));
        statusLabel.setStyle("-fx-text-fill: " + Config.C_TEXT_MUTED + ";");

        // Boton
        syncBtn = new Button("Iniciar Sincronizacion");
        syncBtn.setStyle("-fx-background-color: " + Config.C_ACCENT + ";"
            + "-fx-text-fill: white;"
            + "-fx-background-radius: 6;"
            + "-fx-padding: 10 24;"
            + "-fx-font-weight: bold;"
            + "-fx-cursor: hand;");
        syncBtn.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        syncBtn.setMaxWidth(Double.MAX_VALUE);
        syncBtn.setOnAction(e -> ejecutarSync());

        content.getChildren().addAll(titulo, info, logArea, statusLabel, syncBtn);
        getDialogPane().setContent(content);
    }

    private void ejecutarSync() {
        syncBtn.setDisable(true);
        logArea.clear();
        setStatus("Sincronizando...", Config.C_ACCENT);
        appendLog("=".repeat(50));

        SyncAgent agent = new SyncAgent(msg -> Platform.runLater(() -> appendLog(msg)));

        // Ejecutar en hilo de fondo para no bloquear la UI
        Thread thread = new Thread(() -> {
            SyncAgent.SyncResult result = agent.sincronizar();
            Platform.runLater(() -> {
                appendLog("=".repeat(50));
                if (result.success) {
                    setStatus("Sincronizacion exitosa. " + result.filesProcessed + " archivo(s) enviado(s).",
                        Config.C_SUCCESS);
                } else {
                    setStatus("Error en sincronizacion: " + result.errorMessage, Config.C_ERROR);
                }
                syncBtn.setDisable(false);
            });
        });
        thread.setDaemon(true);
        thread.start();
    }

    private void appendLog(String msg) {
        logArea.appendText(msg + "\n");
        logArea.setScrollTop(Double.MAX_VALUE);
    }

    private void setStatus(String msg, String color) {
        statusLabel.setText(msg);
        statusLabel.setStyle("-fx-text-fill: " + color + ";");
    }
}

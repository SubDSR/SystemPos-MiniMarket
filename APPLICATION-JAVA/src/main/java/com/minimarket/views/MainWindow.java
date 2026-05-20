package com.minimarket.views;

import com.minimarket.utils.Config;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ventana principal del Sistema POS.
 *
 * Layout: BorderPane
 *   - Izquierda: Sidebar de navegacion
 *   - Centro:    Area de contenido intercambiable (vistas)
 *
 * Equivalente Java de views/main_window.py (Tkinter).
 */
public class MainWindow extends BorderPane {

    private final StackPane      contentArea  = new StackPane();
    private       Button         activeButton = null;

    private final DashboardView  dashboardView;
    private final ProductosView  productosView;
    private final ClientesView   clientesView;
    private final VentasView     ventasView;
    private final HistorialView  historialView;

    public MainWindow() {
        // Inicializar vistas (creacion unica)
        dashboardView = new DashboardView();
        productosView = new ProductosView();
        clientesView  = new ClientesView();
        ventasView    = new VentasView();
        historialView = new HistorialView();

        buildLayout();
        navigateTo("Dashboard", dashboardView);
    }

    // ════════════════════════════════════════════════════════════════════════
    // LAYOUT
    // ════════════════════════════════════════════════════════════════════════

    private void buildLayout() {
        setLeft(buildSidebar());
        setCenter(buildMainArea());
        setStyle("-fx-background-color: " + Config.C_MAIN_BG + ";");
    }

    private VBox buildSidebar() {
        VBox sidebar = new VBox(4);
        sidebar.setPrefWidth(220);
        sidebar.setStyle("-fx-background-color: " + Config.C_SIDEBAR_BG + ";");
        sidebar.setPadding(new Insets(0, 0, 16, 0));

        // Logo / Header
        VBox header = new VBox(4);
        header.setPadding(new Insets(24, 16, 20, 16));
        header.setStyle("-fx-border-color: " + Config.C_SIDEBAR_HOVER + ";"
            + "-fx-border-width: 0 0 1 0;");

        Label logo = new Label("MiniMarket");
        logo.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        logo.setStyle("-fx-text-fill: white;");

        Label sub = new Label(Config.EMPRESA);
        sub.setFont(Font.font("Segoe UI", 10));
        sub.setStyle("-fx-text-fill: " + Config.C_SIDEBAR_FG + ";");
        sub.setWrapText(true);

        header.getChildren().addAll(logo, sub);

        // Spacer superior
        Region topSpacer = new Region();
        topSpacer.setPrefHeight(8);

        // Botones de navegacion
        Map<String, String[]> navItems = new LinkedHashMap<>();
        navItems.put("Dashboard",    new String[]{"Dashboard",    "Resumen del dia"});
        navItems.put("Productos",    new String[]{"Productos",    "Gestion de inventario"});
        navItems.put("Clientes",     new String[]{"Clientes",     "Base de clientes"});
        navItems.put("Nueva Venta",  new String[]{"Nueva Venta",  "Registrar venta POS"});
        navItems.put("Historial",    new String[]{"Historial",    "Ventas realizadas"});

        sidebar.getChildren().addAll(header, topSpacer);

        Map<String, Node> viewMap = Map.of(
            "Dashboard",   dashboardView,
            "Productos",   productosView,
            "Clientes",    clientesView,
            "Nueva Venta", ventasView,
            "Historial",   historialView
        );

        for (Map.Entry<String, String[]> entry : navItems.entrySet()) {
            String key   = entry.getKey();
            Button btn   = buildNavButton(entry.getValue()[0]);
            Node   view  = viewMap.get(key);

            btn.setOnAction(e -> navigateTo(key, view));
            sidebar.getChildren().add(btn);

            // Guardar referencia al primer boton
            if ("Dashboard".equals(key)) activeButton = btn;
        }

        // Spacer flexible
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        // Boton sincronizar al fondo
        Button syncBtn = buildNavButton("Sincronizar");
        syncBtn.setOnAction(e -> {
            SyncDialog dialog = new SyncDialog();
            dialog.showAndWait();
        });

        // Version
        Label version = new Label(Config.APP_NAME + " v" + Config.APP_VERSION);
        version.setFont(Font.font("Segoe UI", 9));
        version.setStyle("-fx-text-fill: " + Config.C_TEXT_MUTED + ";");
        version.setPadding(new Insets(8, 16, 0, 16));

        sidebar.getChildren().addAll(spacer, syncBtn, version);
        return sidebar;
    }

    private Button buildNavButton(String text) {
        Button btn = new Button(text);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setAlignment(Pos.CENTER_LEFT);
        btn.setFont(Font.font("Segoe UI", 12));
        btn.setPadding(new Insets(11, 20, 11, 20));
        btn.setCursor(javafx.scene.Cursor.HAND);
        setNavStyle(btn, false);

        btn.setOnMouseEntered(e -> {
            if (btn != activeButton) setNavStyle(btn, true);
        });
        btn.setOnMouseExited(e -> {
            if (btn != activeButton) setNavStyle(btn, false);
        });

        return btn;
    }

    private void setNavStyle(Button btn, boolean hover) {
        String bg = hover ? Config.C_SIDEBAR_HOVER : Config.C_SIDEBAR_BG;
        btn.setStyle("-fx-background-color: " + bg + ";"
            + "-fx-text-fill: " + Config.C_SIDEBAR_FG + ";"
            + "-fx-border-width: 0;"
            + "-fx-background-radius: 6;"
            + "-fx-padding: 11 20 11 20;");
    }

    private void setActiveStyle(Button btn) {
        btn.setStyle("-fx-background-color: " + Config.C_ACTIVE_BTN + ";"
            + "-fx-text-fill: white;"
            + "-fx-border-width: 0;"
            + "-fx-background-radius: 6;"
            + "-fx-padding: 11 20 11 20;");
    }

    private BorderPane buildMainArea() {
        BorderPane main = new BorderPane();

        // Header del contenido
        HBox header = new HBox();
        header.setPadding(new Insets(16, 24, 16, 24));
        header.setStyle("-fx-background-color: " + Config.C_CARD_BG + ";"
            + "-fx-border-color: " + Config.C_BORDER + ";"
            + "-fx-border-width: 0 0 1 0;");

        Label title = new Label(Config.APP_NAME);
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        title.setStyle("-fx-text-fill: " + Config.C_TEXT + ";");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label ruc = new Label("RUC: " + Config.RUC);
        ruc.setFont(Font.font("Segoe UI", 11));
        ruc.setStyle("-fx-text-fill: " + Config.C_TEXT_MUTED + ";");

        header.getChildren().addAll(title, spacer, ruc);

        contentArea.setStyle("-fx-background-color: " + Config.C_MAIN_BG + ";");
        contentArea.setPadding(new Insets(24));

        main.setTop(header);
        main.setCenter(contentArea);
        return main;
    }

    // ════════════════════════════════════════════════════════════════════════
    // NAVEGACION
    // ════════════════════════════════════════════════════════════════════════

    private void navigateTo(String name, Node view) {
        contentArea.getChildren().setAll(view);

        // Actualizar estado visual de botones en sidebar (VBox izquierda)
        VBox sidebar = (VBox) getLeft();
        for (Node node : sidebar.getChildren()) {
            if (node instanceof Button btn) {
                if (btn.getText().equals(name)) {
                    setActiveStyle(btn);
                    activeButton = btn;
                } else if (btn != activeButton) {
                    setNavStyle(btn, false);
                }
            }
        }

        // Refrescar la vista al navegar
        if (view instanceof Refreshable r) r.refresh();
    }

    /** Interfaz para vistas que necesitan refrescar datos al activarse. */
    public interface Refreshable {
        void refresh();
    }
}

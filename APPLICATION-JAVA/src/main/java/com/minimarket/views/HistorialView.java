package com.minimarket.views;

import com.minimarket.models.DetalleVenta;
import com.minimarket.models.Producto;
import com.minimarket.models.Venta;
import com.minimarket.services.ProductoService;
import com.minimarket.services.VentaService;
import com.minimarket.utils.Config;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Vista de Historial de Ventas.
 * Permite filtrar por fecha, ver detalles y anular ventas.
 * Equivalente Java de views/historial_view.py.
 */
public class HistorialView extends BorderPane implements MainWindow.Refreshable {

    private final VentaService    ventaSvc = new VentaService();
    private final ProductoService prodSvc  = new ProductoService();

    private TableView<Venta>        ventasTable;
    private TableView<DetalleVenta> detallesTable;
    private TextField               filtroFechaField;
    private Label                   statusLabel;
    private Label                   resumenLabel;

    public HistorialView() { build(); }

    @SuppressWarnings("unchecked")
    private void build() {
        // ── Titulo y filtro ───────────────────────────────────────────────────
        HBox toolbar = new HBox(12);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(0, 0, 12, 0));

        Label titulo = new Label("Historial de Ventas");
        titulo.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        titulo.setStyle("-fx-text-fill: " + Config.C_TEXT + ";");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label filtroLbl = new Label("Filtrar fecha:");
        filtroLbl.setFont(Font.font("Segoe UI", 11));
        filtroFechaField = new TextField();
        filtroFechaField.setPromptText("ej. 2026-05-19");
        filtroFechaField.setPrefWidth(140);
        filtroFechaField.setStyle(fieldStyle());
        filtroFechaField.textProperty().addListener((obs, old, val) -> filtrarPorFecha(val));

        Button todosBtn = buildBtn("Mostrar Todas", Config.C_ACCENT);
        todosBtn.setOnAction(e -> { filtroFechaField.clear(); refresh(); });

        toolbar.getChildren().addAll(titulo, spacer, filtroLbl, filtroFechaField, todosBtn);

        // ── Tabla de ventas ───────────────────────────────────────────────────
        ventasTable = new TableView<>();
        ventasTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        ventasTable.setPlaceholder(new Label("No hay ventas"));

        ventasTable.getColumns().addAll(
            ventaCol("ID",       55,  cd -> String.valueOf(cd.getValue().getId())),
            ventaCol("Fecha",    150, cd -> cd.getValue().getFecha()),
            ventaCol("Cliente",  90,  cd -> cd.getValue().getClienteId() == 0
                ? "Anonimo" : "ID:" + cd.getValue().getClienteId()),
            ventaCol("Subtotal", 90,  cd -> String.format("S/%.2f", cd.getValue().getSubtotal())),
            ventaCol("IGV",      80,  cd -> String.format("S/%.2f", cd.getValue().getIgv())),
            ventaCol("Total",    90,  cd -> String.format("S/%.2f", cd.getValue().getTotal()))
        );

        ventasTable.getSelectionModel().selectedItemProperty()
            .addListener((obs, old, sel) -> mostrarDetalles(sel));

        // ── Tabla de detalles ─────────────────────────────────────────────────
        Label detalleLbl = new Label("Detalle de la venta seleccionada:");
        detalleLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        detalleLbl.setStyle("-fx-text-fill: " + Config.C_TEXT + ";");

        detallesTable = new TableView<>();
        detallesTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        detallesTable.setPrefHeight(180);
        detallesTable.setPlaceholder(new Label("Seleccione una venta"));

        detallesTable.getColumns().addAll(
            detalleCol("Producto",  180, cd -> {
                Producto p = prodSvc.obtener(cd.getValue().getProductoId());
                return p != null ? p.getNombre() : "ID:" + cd.getValue().getProductoId();
            }),
            detalleCol("Cantidad",  70,  cd -> String.valueOf(cd.getValue().getCantidad())),
            detalleCol("P.Unit",    90,  cd -> String.format("S/%.2f", cd.getValue().getPrecioUnitario())),
            detalleCol("Subtotal",  90,  cd -> String.format("S/%.2f", cd.getValue().getSubtotal()))
        );

        // ── Panel de acciones ─────────────────────────────────────────────────
        resumenLabel = new Label("");
        resumenLabel.setFont(Font.font("Segoe UI", 12));
        resumenLabel.setStyle("-fx-text-fill: " + Config.C_TEXT_MUTED + ";");

        statusLabel = new Label("");
        statusLabel.setFont(Font.font("Segoe UI", 12));

        Button anularBtn = buildBtn("Anular Venta Seleccionada", Config.C_ERROR);
        anularBtn.setOnAction(e -> anularVenta());

        Button refrescarBtn = buildBtn("Actualizar", Config.C_ACCENT);
        refrescarBtn.setOnAction(e -> refresh());

        HBox btnRow = new HBox(10, anularBtn, refrescarBtn);

        VBox tableSection = new VBox(6, ventasTable);
        VBox.setVgrow(ventasTable, Priority.ALWAYS);
        VBox detalleSection = new VBox(8, detalleLbl, detallesTable);

        VBox main = new VBox(12, toolbar, tableSection, detalleSection,
            resumenLabel, btnRow, statusLabel);
        VBox.setVgrow(tableSection, Priority.ALWAYS);

        setCenter(main);
        refresh();
    }

    // ── Logica ────────────────────────────────────────────────────────────────

    private void filtrarPorFecha(String fecha) {
        if (fecha == null || fecha.isBlank()) { refresh(); return; }
        List<Venta> filtradas = ventaSvc.listarVentas().stream()
            .filter(v -> v.getFecha().startsWith(fecha.strip()))
            .sorted(Comparator.comparingInt(Venta::getId).reversed())
            .collect(Collectors.toList());
        ventasTable.setItems(FXCollections.observableArrayList(filtradas));
        actualizarResumen(filtradas);
    }

    private void mostrarDetalles(Venta venta) {
        if (venta == null) { detallesTable.setItems(FXCollections.emptyObservableList()); return; }
        detallesTable.setItems(FXCollections.observableArrayList(
            ventaSvc.obtenerDetalles(venta.getId())));
    }

    private void anularVenta() {
        Venta sel = ventasTable.getSelectionModel().getSelectedItem();
        if (sel == null) { setStatus("Seleccione una venta.", Config.C_WARNING); return; }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            String.format("¿Anular la venta #%d del %s (Total: S/%.2f)?",
                sel.getId(), sel.getFecha(), sel.getTotal()),
            ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText("Confirmar anulacion");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                boolean ok = ventaSvc.anularVenta(sel.getId());
                if (ok) {
                    setStatus("Venta #" + sel.getId() + " anulada. Stock restaurado.", Config.C_SUCCESS);
                    refresh();
                } else {
                    setStatus("No se pudo anular la venta.", Config.C_ERROR);
                }
            }
        });
    }

    private void actualizarResumen(List<Venta> ventas) {
        double total = ventas.stream().mapToDouble(Venta::getTotal).sum();
        resumenLabel.setText(String.format("%d venta(s)  |  Total acumulado: S/%.2f",
            ventas.size(), total));
    }

    private void setStatus(String msg, String color) {
        statusLabel.setText(msg);
        statusLabel.setStyle("-fx-text-fill: " + color + ";");
    }

    @Override
    public void refresh() {
        List<Venta> ventas = ventaSvc.listarVentas().stream()
            .sorted(Comparator.comparingInt(Venta::getId).reversed())
            .collect(Collectors.toList());
        ventasTable.setItems(FXCollections.observableArrayList(ventas));
        detallesTable.setItems(FXCollections.emptyObservableList());
        actualizarResumen(ventas);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    @FunctionalInterface
    interface VentaCellValue { String get(TableColumn.CellDataFeatures<Venta, String> cd); }

    @SuppressWarnings("unchecked")
    private TableColumn<Venta, String> ventaCol(String title, int width, VentaCellValue fn) {
        TableColumn<Venta, String> c = new TableColumn<>(title);
        c.setCellValueFactory(cd -> new SimpleStringProperty(fn.get(cd)));
        c.setPrefWidth(width);
        return c;
    }

    @FunctionalInterface
    interface DetalleCellValue { String get(TableColumn.CellDataFeatures<DetalleVenta, String> cd); }

    @SuppressWarnings("unchecked")
    private TableColumn<DetalleVenta, String> detalleCol(String title, int width, DetalleCellValue fn) {
        TableColumn<DetalleVenta, String> c = new TableColumn<>(title);
        c.setCellValueFactory(cd -> new SimpleStringProperty(fn.get(cd)));
        c.setPrefWidth(width);
        return c;
    }

    private String fieldStyle() {
        return "-fx-background-color: white;"
            + "-fx-border-color: " + Config.C_BORDER + ";"
            + "-fx-border-radius: 6;"
            + "-fx-background-radius: 6;"
            + "-fx-padding: 7;";
    }

    private Button buildBtn(String text, String bg) {
        Button btn = new Button(text);
        btn.setStyle("-fx-background-color: " + bg + ";"
            + "-fx-text-fill: white;"
            + "-fx-background-radius: 6;"
            + "-fx-padding: 8 16;"
            + "-fx-font-weight: bold;"
            + "-fx-cursor: hand;");
        return btn;
    }
}

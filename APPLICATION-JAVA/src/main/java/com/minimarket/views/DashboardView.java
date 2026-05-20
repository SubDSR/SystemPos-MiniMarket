package com.minimarket.views;

import com.minimarket.models.Venta;
import com.minimarket.services.ClienteService;
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
 * Vista Dashboard — KPIs y resumen del dia.
 * Equivalente Java de views/dashboard_view.py.
 */
public class DashboardView extends VBox implements MainWindow.Refreshable {

    private final ProductoService prodSvc  = new ProductoService();
    private final ClienteService  cliSvc   = new ClienteService();
    private final VentaService    ventaSvc = new VentaService();

    private Label ventasHoyLabel;
    private Label totalHoyLabel;
    private Label productosLabel;
    private Label clientesLabel;

    private TableView<Venta> ventasTable;

    public DashboardView() {
        setSpacing(20);
        setPadding(new Insets(0));
        build();
        refresh();
    }

    private void build() {
        // Titulo
        Label titulo = new Label("Dashboard — Resumen del Dia");
        titulo.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        titulo.setStyle("-fx-text-fill: " + Config.C_TEXT + ";");

        // Fila de KPI cards
        HBox kpiRow = buildKpiRow();

        // Tabla de ultimas ventas
        VBox tableSection = buildRecentSalesTable();
        VBox.setVgrow(tableSection, Priority.ALWAYS);

        // Boton refrescar
        Button refreshBtn = new Button("Actualizar");
        refreshBtn.setStyle(accentStyle());
        refreshBtn.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        refreshBtn.setOnAction(e -> refresh());

        HBox footer = new HBox(refreshBtn);
        footer.setAlignment(Pos.CENTER_RIGHT);

        getChildren().addAll(titulo, kpiRow, tableSection, footer);
    }

    private HBox buildKpiRow() {
        ventasHoyLabel  = new Label("0");
        totalHoyLabel   = new Label("S/0.00");
        productosLabel  = new Label("0");
        clientesLabel   = new Label("0");

        HBox row = new HBox(16);
        row.getChildren().addAll(
            buildKpiCard("Ventas Hoy",      ventasHoyLabel, Config.C_ACCENT),
            buildKpiCard("Ingresos Hoy",    totalHoyLabel,  Config.C_SUCCESS),
            buildKpiCard("Total Productos", productosLabel, Config.C_WARNING),
            buildKpiCard("Total Clientes",  clientesLabel,  Config.C_SIDEBAR_BG)
        );
        return row;
    }

    private VBox buildKpiCard(String titulo, Label valueLabel, String accentColor) {
        VBox card = new VBox(6);
        card.setPadding(new Insets(18, 22, 18, 22));
        card.setMinWidth(160);
        card.setStyle("-fx-background-color: " + Config.C_CARD_BG + ";"
            + "-fx-background-radius: 10;"
            + "-fx-border-color: " + Config.C_BORDER + ";"
            + "-fx-border-radius: 10;"
            + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 8, 0, 0, 2);");

        Label tituloLbl = new Label(titulo);
        tituloLbl.setFont(Font.font("Segoe UI", 11));
        tituloLbl.setStyle("-fx-text-fill: " + Config.C_TEXT_MUTED + ";");

        valueLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 26));
        valueLabel.setStyle("-fx-text-fill: " + accentColor + ";");

        card.getChildren().addAll(tituloLbl, valueLabel);
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    @SuppressWarnings("unchecked")
    private VBox buildRecentSalesTable() {
        Label titulo = new Label("Ultimas Ventas");
        titulo.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        titulo.setStyle("-fx-text-fill: " + Config.C_TEXT + ";");

        ventasTable = new TableView<>();
        ventasTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        ventasTable.setStyle("-fx-background-color: " + Config.C_CARD_BG + ";"
            + "-fx-border-color: " + Config.C_BORDER + ";"
            + "-fx-border-radius: 8;");
        ventasTable.setPlaceholder(new Label("No hay ventas registradas"));

        TableColumn<Venta, String> idCol = new TableColumn<>("#");
        idCol.setCellValueFactory(cd ->
            new SimpleStringProperty(String.valueOf(cd.getValue().getId())));
        idCol.setPrefWidth(50);

        TableColumn<Venta, String> fechaCol = new TableColumn<>("Fecha");
        fechaCol.setCellValueFactory(cd ->
            new SimpleStringProperty(cd.getValue().getFecha()));

        TableColumn<Venta, String> clienteCol = new TableColumn<>("Cliente");
        clienteCol.setCellValueFactory(cd ->
            new SimpleStringProperty(cd.getValue().getClienteId() == 0
                ? "Anonimo" : "ID: " + cd.getValue().getClienteId()));

        TableColumn<Venta, String> subtotalCol = new TableColumn<>("Subtotal");
        subtotalCol.setCellValueFactory(cd ->
            new SimpleStringProperty(String.format("S/%.2f", cd.getValue().getSubtotal())));

        TableColumn<Venta, String> igvCol = new TableColumn<>("IGV");
        igvCol.setCellValueFactory(cd ->
            new SimpleStringProperty(String.format("S/%.2f", cd.getValue().getIgv())));

        TableColumn<Venta, String> totalCol = new TableColumn<>("Total");
        totalCol.setCellValueFactory(cd ->
            new SimpleStringProperty(String.format("S/%.2f", cd.getValue().getTotal())));
        totalCol.setStyle("-fx-font-weight: bold;");

        ventasTable.getColumns().addAll(idCol, fechaCol, clienteCol, subtotalCol, igvCol, totalCol);

        VBox section = new VBox(10, titulo, ventasTable);
        section.setStyle("-fx-background-color: " + Config.C_CARD_BG + ";"
            + "-fx-background-radius: 10;"
            + "-fx-padding: 16;"
            + "-fx-border-color: " + Config.C_BORDER + ";"
            + "-fx-border-radius: 10;");
        VBox.setVgrow(ventasTable, Priority.ALWAYS);
        return section;
    }

    // ════════════════════════════════════════════════════════════════════════
    // REFRESH
    // ════════════════════════════════════════════════════════════════════════

    @Override
    public void refresh() {
        List<Venta> hoy = ventaSvc.ventasHoy();
        ventasHoyLabel.setText(String.valueOf(hoy.size()));
        totalHoyLabel.setText(String.format("S/%.2f", ventaSvc.totalHoy()));
        productosLabel.setText(String.valueOf(prodSvc.contar()));
        clientesLabel.setText(String.valueOf(cliSvc.contar()));

        // Mostrar las 10 ventas mas recientes
        List<Venta> recientes = ventaSvc.listarVentas().stream()
            .sorted(Comparator.comparingInt(Venta::getId).reversed())
            .limit(10)
            .collect(Collectors.toList());
        ventasTable.setItems(FXCollections.observableArrayList(recientes));
    }

    private String accentStyle() {
        return "-fx-background-color: " + Config.C_ACCENT + ";"
            + "-fx-text-fill: white;"
            + "-fx-background-radius: 6;"
            + "-fx-padding: 8 18;"
            + "-fx-cursor: hand;";
    }
}

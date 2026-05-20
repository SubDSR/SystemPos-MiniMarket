package com.minimarket.views;

import com.minimarket.models.Cliente;
import com.minimarket.models.Producto;
import com.minimarket.models.Venta;
import com.minimarket.services.ClienteService;
import com.minimarket.services.ProductoService;
import com.minimarket.services.VentaService;
import com.minimarket.utils.Config;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.*;

/**
 * Vista POS de Nueva Venta — carrito + busqueda de productos.
 * Equivalente Java de views/ventas_view.py.
 */
public class VentasView extends BorderPane implements MainWindow.Refreshable {

    private final ProductoService prodSvc  = new ProductoService();
    private final ClienteService  cliSvc   = new ClienteService();
    private final VentaService    ventaSvc = new VentaService();

    // Busqueda de productos
    private TextField buscarField;
    private ListView<Producto> productosListView;

    // Carrito: (Producto, cantidad)
    private final ObservableList<int[]> carritoItems = FXCollections.observableArrayList();
    private TableView<int[]> carritoTable;

    // Totales
    private Label subtotalLabel;
    private Label igvLabel;
    private Label totalLabel;

    // Cliente
    private ComboBox<String> clienteCombo;
    private Map<String, Integer> clienteIdMap = new LinkedHashMap<>();

    private Label statusLabel;

    public VentasView() { build(); }

    @SuppressWarnings("unchecked")
    private void build() {
        Label titulo = new Label("Nueva Venta — POS");
        titulo.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        titulo.setStyle("-fx-text-fill: " + Config.C_TEXT + ";");

        // ── Panel izquierdo: busqueda de productos ────────────────────────────
        VBox leftPanel = buildProductSearchPanel();
        leftPanel.setPrefWidth(380);

        // ── Panel derecho: carrito ────────────────────────────────────────────
        VBox rightPanel = buildCarritoPanel();
        HBox.setHgrow(rightPanel, Priority.ALWAYS);

        HBox content = new HBox(16, leftPanel, rightPanel);
        VBox.setVgrow(content, Priority.ALWAYS);

        VBox main = new VBox(12, titulo, content);
        VBox.setVgrow(content, Priority.ALWAYS);
        setCenter(main);
        refresh();
    }

    private VBox buildProductSearchPanel() {
        VBox panel = new VBox(10);
        panel.setStyle("-fx-background-color: " + Config.C_CARD_BG + ";"
            + "-fx-background-radius: 10;"
            + "-fx-padding: 16;"
            + "-fx-border-color: " + Config.C_BORDER + ";"
            + "-fx-border-radius: 10;");

        Label lbl = new Label("Buscar Producto");
        lbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        lbl.setStyle("-fx-text-fill: " + Config.C_TEXT + ";");

        buscarField = new TextField();
        buscarField.setPromptText("Nombre o categoria...");
        buscarField.setStyle(fieldStyle());
        buscarField.textProperty().addListener((obs, old, val) -> filtrarProductos(val));

        productosListView = new ListView<>();
        productosListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Producto p, boolean empty) {
                super.updateItem(p, empty);
                if (empty || p == null) { setText(null); }
                else {
                    setText(String.format("[%d] %s  —  S/%.2f  (stock: %d)",
                        p.getId(), p.getNombre(), p.getPrecio(), p.getStock()));
                }
            }
        });
        VBox.setVgrow(productosListView, Priority.ALWAYS);

        // Spinner de cantidad
        HBox cantRow = new HBox(10);
        cantRow.setAlignment(Pos.CENTER_LEFT);
        Label cantLbl = new Label("Cantidad:");
        cantLbl.setFont(Font.font("Segoe UI", 11));
        Spinner<Integer> cantSpinner = new Spinner<>(1, 9999, 1);
        cantSpinner.setPrefWidth(90);
        cantSpinner.setEditable(true);

        Button agregarBtn = buildBtn("Agregar al Carrito", Config.C_ACCENT);
        agregarBtn.setMaxWidth(Double.MAX_VALUE);
        agregarBtn.setOnAction(e -> {
            Producto sel = productosListView.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            int cant = cantSpinner.getValue();
            agregarAlCarrito(sel, cant);
        });

        cantRow.getChildren().addAll(cantLbl, cantSpinner);
        panel.getChildren().addAll(lbl, buscarField, productosListView, cantRow, agregarBtn);
        return panel;
    }

    @SuppressWarnings("unchecked")
    private VBox buildCarritoPanel() {
        VBox panel = new VBox(12);
        panel.setStyle("-fx-background-color: " + Config.C_CARD_BG + ";"
            + "-fx-background-radius: 10;"
            + "-fx-padding: 16;"
            + "-fx-border-color: " + Config.C_BORDER + ";"
            + "-fx-border-radius: 10;");

        Label carritoLbl = new Label("Carrito de Venta");
        carritoLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        carritoLbl.setStyle("-fx-text-fill: " + Config.C_TEXT + ";");

        // Tabla del carrito
        carritoTable = new TableView<>();
        carritoTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        carritoTable.setPlaceholder(new Label("Carrito vacio"));
        VBox.setVgrow(carritoTable, Priority.ALWAYS);

        TableColumn<int[], String> prodCol = new TableColumn<>("Producto");
        prodCol.setCellValueFactory(cd -> {
            Producto p = prodSvc.obtener(cd.getValue()[0]);
            return new SimpleStringProperty(p != null ? p.getNombre() : "?");
        });
        TableColumn<int[], String> cantCol = new TableColumn<>("Cant.");
        cantCol.setCellValueFactory(cd -> new SimpleStringProperty(String.valueOf(cd.getValue()[1])));
        cantCol.setPrefWidth(60);
        TableColumn<int[], String> precCol = new TableColumn<>("P.Unit");
        precCol.setCellValueFactory(cd -> {
            Producto p = prodSvc.obtener(cd.getValue()[0]);
            return new SimpleStringProperty(p != null ? String.format("S/%.2f", p.getPrecio()) : "");
        });
        TableColumn<int[], String> subCol = new TableColumn<>("Subtotal");
        subCol.setCellValueFactory(cd -> {
            Producto p = prodSvc.obtener(cd.getValue()[0]);
            if (p == null) return new SimpleStringProperty("");
            double sub = p.getPrecio() * cd.getValue()[1];
            return new SimpleStringProperty(String.format("S/%.2f", sub));
        });

        carritoTable.getColumns().addAll(prodCol, cantCol, precCol, subCol);
        carritoTable.setItems(carritoItems);

        Button quitarBtn = buildBtn("Quitar Seleccionado", Config.C_ERROR);
        quitarBtn.setOnAction(e -> {
            int[] sel = carritoTable.getSelectionModel().getSelectedItem();
            if (sel != null) { carritoItems.remove(sel); recalcularTotales(); }
        });

        Button limpiarBtn = buildBtn("Limpiar Carrito", Config.C_WARNING);
        limpiarBtn.setOnAction(e -> { carritoItems.clear(); recalcularTotales(); });

        HBox cartBtns = new HBox(8, quitarBtn, limpiarBtn);

        // Totales
        VBox totalesBox = buildTotalesBox();

        // Selector de cliente
        VBox clienteBox = buildClienteBox();

        statusLabel = new Label("");
        statusLabel.setWrapText(true);
        statusLabel.setFont(Font.font("Segoe UI", 12));

        Button registrarBtn = buildBtn("REGISTRAR VENTA", Config.C_SUCCESS);
        registrarBtn.setMaxWidth(Double.MAX_VALUE);
        registrarBtn.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        registrarBtn.setStyle(registrarBtn.getStyle() + "-fx-padding: 12;");
        registrarBtn.setOnAction(e -> registrarVenta());

        panel.getChildren().addAll(carritoLbl, carritoTable, cartBtns,
            totalesBox, clienteBox, statusLabel, registrarBtn);
        return panel;
    }

    private VBox buildTotalesBox() {
        VBox box = new VBox(6);
        box.setStyle("-fx-background-color: " + Config.C_MAIN_BG + ";"
            + "-fx-padding: 12;"
            + "-fx-background-radius: 8;");

        subtotalLabel = new Label("Subtotal:  S/0.00");
        igvLabel      = new Label("IGV (18%): S/0.00");
        totalLabel    = new Label("TOTAL:     S/0.00");

        subtotalLabel.setFont(Font.font("Segoe UI", 12));
        igvLabel.setFont(Font.font("Segoe UI", 12));
        totalLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 15));
        totalLabel.setStyle("-fx-text-fill: " + Config.C_ACCENT + ";");

        box.getChildren().addAll(subtotalLabel, igvLabel, new Separator(), totalLabel);
        return box;
    }

    private VBox buildClienteBox() {
        VBox box = new VBox(6);
        Label lbl = new Label("Cliente (opcional):");
        lbl.setFont(Font.font("Segoe UI", 11));
        lbl.setStyle("-fx-text-fill: " + Config.C_TEXT + ";");

        clienteCombo = new ComboBox<>();
        clienteCombo.setMaxWidth(Double.MAX_VALUE);
        clienteCombo.setStyle(fieldStyle());

        box.getChildren().addAll(lbl, clienteCombo);
        return box;
    }

    // ── Logica de negocio ─────────────────────────────────────────────────────

    private void filtrarProductos(String termino) {
        List<Producto> lista = termino.isBlank()
            ? prodSvc.listar() : prodSvc.buscar(termino);
        productosListView.setItems(FXCollections.observableArrayList(lista));
    }

    private void agregarAlCarrito(Producto prod, int cantidad) {
        // Verificar si ya esta en el carrito
        for (int[] item : carritoItems) {
            if (item[0] == prod.getId()) {
                item[1] += cantidad;
                carritoTable.refresh();
                recalcularTotales();
                return;
            }
        }
        carritoItems.add(new int[]{prod.getId(), cantidad});
        recalcularTotales();
    }

    private void recalcularTotales() {
        double subtotal = 0;
        for (int[] item : carritoItems) {
            Producto p = prodSvc.obtener(item[0]);
            if (p != null) subtotal += p.getPrecio() * item[1];
        }
        subtotal = Math.round(subtotal * 100.0) / 100.0;
        double igv   = Math.round(subtotal * Config.IGV_RATE * 100.0) / 100.0;
        double total = Math.round((subtotal + igv) * 100.0) / 100.0;

        subtotalLabel.setText(String.format("Subtotal:  S/%.2f", subtotal));
        igvLabel.setText(String.format("IGV (18%): S/%.2f", igv));
        totalLabel.setText(String.format("TOTAL:     S/%.2f", total));
    }

    private void registrarVenta() {
        if (carritoItems.isEmpty()) {
            setStatus("El carrito esta vacio.", Config.C_WARNING);
            return;
        }

        List<int[]> items = new ArrayList<>(carritoItems);
        int clienteId = 0;
        String clienteSel = clienteCombo.getValue();
        if (clienteSel != null && clienteIdMap.containsKey(clienteSel)) {
            clienteId = clienteIdMap.get(clienteSel);
        }

        Venta venta = ventaSvc.crearVenta(items, clienteId);
        if (venta != null) {
            carritoItems.clear();
            recalcularTotales();
            setStatus(String.format("Venta #%d registrada. Total: S/%.2f",
                venta.getId(), venta.getTotal()), Config.C_SUCCESS);
        } else {
            setStatus("No se pudo registrar la venta. Verifique el stock disponible.", Config.C_ERROR);
        }
    }

    private void setStatus(String msg, String color) {
        statusLabel.setText(msg);
        statusLabel.setStyle("-fx-text-fill: " + color + ";");
    }

    // ── Refresh ───────────────────────────────────────────────────────────────

    @Override
    public void refresh() {
        filtrarProductos(buscarField != null ? buscarField.getText() : "");

        // Actualizar combo de clientes
        clienteIdMap.clear();
        List<String> clienteOptions = new ArrayList<>();
        clienteOptions.add("-- Sin cliente (anonimo) --");
        for (Cliente c : cliSvc.listar()) {
            String label = c.getNombre() + " (" + c.getDni() + ")";
            clienteOptions.add(label);
            clienteIdMap.put(label, c.getId());
        }
        if (clienteCombo != null) {
            clienteCombo.setItems(FXCollections.observableArrayList(clienteOptions));
            clienteCombo.setValue(clienteOptions.get(0));
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

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

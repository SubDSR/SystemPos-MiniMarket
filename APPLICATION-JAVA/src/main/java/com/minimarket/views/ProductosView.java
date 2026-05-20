package com.minimarket.views;

import com.minimarket.models.Producto;
import com.minimarket.services.ProductoService;
import com.minimarket.utils.Config;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.List;

/**
 * Vista CRUD de Productos.
 * Equivalente Java de views/productos_view.py.
 */
public class ProductosView extends BorderPane implements MainWindow.Refreshable {

    private final ProductoService prodSvc = new ProductoService();

    private TableView<Producto> tabla;
    private TextField buscarField;

    // Formulario
    private TextField  nombreField;
    private TextField  precioField;
    private TextField  stockField;
    private TextField  categoriaField;
    private Label      statusLabel;

    private Producto   selectedProduct = null;

    public ProductosView() { build(); }

    @SuppressWarnings("unchecked")
    private void build() {
        // ── Toolbar ──────────────────────────────────────────────────────────
        HBox toolbar = new HBox(10);
        toolbar.setPadding(new Insets(0, 0, 12, 0));
        toolbar.setAlignment(Pos.CENTER_LEFT);

        Label titulo = new Label("Gestion de Productos");
        titulo.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        titulo.setStyle("-fx-text-fill: " + Config.C_TEXT + ";");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        buscarField = new TextField();
        buscarField.setPromptText("Buscar por nombre o categoria...");
        buscarField.setPrefWidth(260);
        buscarField.setStyle(fieldStyle());
        buscarField.textProperty().addListener((obs, old, val) -> buscar(val));

        Button nuevoBtn = buildBtn("Nuevo", Config.C_ACCENT, "white");
        nuevoBtn.setOnAction(e -> limpiarFormulario());

        toolbar.getChildren().addAll(titulo, spacer, buscarField, nuevoBtn);

        // ── Tabla ─────────────────────────────────────────────────────────────
        tabla = new TableView<>();
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tabla.setPlaceholder(new Label("No hay productos registrados"));
        tabla.getSelectionModel().selectedItemProperty()
            .addListener((obs, old, sel) -> cargarEnFormulario(sel));

        TableColumn<Producto, String> idCol = col("ID", 50,
            cd -> String.valueOf(cd.getValue().getId()));
        TableColumn<Producto, String> nomCol = col("Nombre", 200,
            cd -> cd.getValue().getNombre());
        TableColumn<Producto, String> precCol = col("Precio", 90,
            cd -> String.format("S/%.2f", cd.getValue().getPrecio()));
        TableColumn<Producto, String> stkCol = col("Stock", 70,
            cd -> String.valueOf(cd.getValue().getStock()));
        TableColumn<Producto, String> catCol = col("Categoria", 130,
            cd -> cd.getValue().getCategoria());

        tabla.getColumns().addAll(idCol, nomCol, precCol, stkCol, catCol);
        VBox.setVgrow(tabla, Priority.ALWAYS);

        // ── Formulario ────────────────────────────────────────────────────────
        VBox form = buildForm();
        form.setPrefWidth(280);

        // ── Layout principal ──────────────────────────────────────────────────
        HBox content = new HBox(16);
        VBox leftSide = new VBox(toolbar, tabla);
        VBox.setVgrow(tabla, Priority.ALWAYS);
        HBox.setHgrow(leftSide, Priority.ALWAYS);
        content.getChildren().addAll(leftSide, form);

        setCenter(content);
        refresh();
    }

    private VBox buildForm() {
        VBox form = new VBox(12);
        form.setPadding(new Insets(0, 0, 0, 16));
        form.setStyle("-fx-background-color: " + Config.C_CARD_BG + ";"
            + "-fx-background-radius: 10;"
            + "-fx-padding: 20;"
            + "-fx-border-color: " + Config.C_BORDER + ";"
            + "-fx-border-radius: 10;");

        Label formTitle = new Label("Datos del Producto");
        formTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        formTitle.setStyle("-fx-text-fill: " + Config.C_TEXT + ";");

        nombreField   = fieldWithLabel(form, "Nombre *");
        precioField   = fieldWithLabel(form, "Precio (S/) *");
        stockField    = fieldWithLabel(form, "Stock *");
        categoriaField = fieldWithLabel(form, "Categoria");

        statusLabel = new Label("");
        statusLabel.setWrapText(true);
        statusLabel.setFont(Font.font("Segoe UI", 11));

        Button guardarBtn = buildBtn("Guardar", Config.C_SUCCESS, "white");
        Button eliminarBtn = buildBtn("Eliminar", Config.C_ERROR, "white");
        Button compactarBtn = buildBtn("Compactar", Config.C_WARNING, "white");

        guardarBtn.setMaxWidth(Double.MAX_VALUE);
        eliminarBtn.setMaxWidth(Double.MAX_VALUE);

        guardarBtn.setOnAction(e -> guardar());
        eliminarBtn.setOnAction(e -> eliminar());
        compactarBtn.setOnAction(e -> compactar());

        form.getChildren().addAll(formTitle, guardarBtn, eliminarBtn, compactarBtn, statusLabel);
        return form;
    }

    // ── Acciones ──────────────────────────────────────────────────────────────

    private void guardar() {
        try {
            String nombre    = nombreField.getText().strip();
            double precio    = Double.parseDouble(precioField.getText().strip().replace(",", "."));
            int    stock     = Integer.parseInt(stockField.getText().strip());
            String categoria = categoriaField.getText().strip();

            if (selectedProduct == null) {
                prodSvc.crear(nombre, precio, stock, categoria);
                setStatus("Producto creado exitosamente.", Config.C_SUCCESS);
            } else {
                prodSvc.actualizar(selectedProduct.getId(), nombre, precio, stock, categoria);
                setStatus("Producto actualizado.", Config.C_SUCCESS);
            }
            limpiarFormulario();
            refresh();
        } catch (NumberFormatException ex) {
            setStatus("Error: precio y stock deben ser numeros validos.", Config.C_ERROR);
        } catch (Exception ex) {
            setStatus("Error: " + ex.getMessage(), Config.C_ERROR);
        }
    }

    private void eliminar() {
        if (selectedProduct == null) { setStatus("Seleccione un producto.", Config.C_WARNING); return; }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "¿Eliminar el producto '" + selectedProduct.getNombre() + "'?",
            ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText("Confirmar eliminacion");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                prodSvc.eliminar(selectedProduct.getId());
                limpiarFormulario();
                refresh();
                setStatus("Producto eliminado.", Config.C_SUCCESS);
            }
        });
    }

    private void compactar() {
        int bytes = prodSvc.compactar();
        setStatus("Compactacion completada. " + bytes + " bytes recuperados.", Config.C_SUCCESS);
        refresh();
    }

    private void buscar(String termino) {
        List<Producto> resultados = termino.isBlank()
            ? prodSvc.listar() : prodSvc.buscar(termino);
        tabla.setItems(FXCollections.observableArrayList(resultados));
    }

    private void cargarEnFormulario(Producto p) {
        selectedProduct = p;
        if (p == null) return;
        nombreField.setText(p.getNombre());
        precioField.setText(String.format("%.2f", p.getPrecio()));
        stockField.setText(String.valueOf(p.getStock()));
        categoriaField.setText(p.getCategoria());
        statusLabel.setText("");
    }

    private void limpiarFormulario() {
        selectedProduct = null;
        nombreField.clear(); precioField.clear();
        stockField.clear(); categoriaField.clear();
        statusLabel.setText("");
        tabla.getSelectionModel().clearSelection();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    @Override
    public void refresh() {
        tabla.setItems(FXCollections.observableArrayList(prodSvc.listar()));
    }

    private void setStatus(String msg, String color) {
        statusLabel.setText(msg);
        statusLabel.setStyle("-fx-text-fill: " + color + ";");
    }

    @FunctionalInterface
    interface CellValue { String get(TableColumn.CellDataFeatures<Producto, String> cd); }

    private TableColumn<Producto, String> col(String title, int width, CellValue fn) {
        TableColumn<Producto, String> c = new TableColumn<>(title);
        c.setCellValueFactory(cd -> new SimpleStringProperty(fn.get(cd)));
        c.setPrefWidth(width);
        return c;
    }

    private TextField fieldWithLabel(VBox parent, String label) {
        Label lbl = new Label(label);
        lbl.setFont(Font.font("Segoe UI", 11));
        lbl.setStyle("-fx-text-fill: " + Config.C_TEXT + ";");
        TextField field = new TextField();
        field.setStyle(fieldStyle());
        parent.getChildren().addAll(lbl, field);
        return field;
    }

    private String fieldStyle() {
        return "-fx-background-color: white;"
            + "-fx-border-color: " + Config.C_BORDER + ";"
            + "-fx-border-radius: 6;"
            + "-fx-background-radius: 6;"
            + "-fx-padding: 8;";
    }

    private Button buildBtn(String text, String bg, String fg) {
        Button btn = new Button(text);
        btn.setStyle("-fx-background-color: " + bg + ";"
            + "-fx-text-fill: " + fg + ";"
            + "-fx-background-radius: 6;"
            + "-fx-padding: 8 16;"
            + "-fx-font-weight: bold;"
            + "-fx-cursor: hand;");
        btn.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        return btn;
    }
}

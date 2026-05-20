package com.minimarket.views;

import com.minimarket.models.Cliente;
import com.minimarket.services.ClienteService;
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
 * Vista CRUD de Clientes.
 * Equivalente Java de views/clientes_view.py.
 */
public class ClientesView extends BorderPane implements MainWindow.Refreshable {

    private final ClienteService cliSvc = new ClienteService();

    private TableView<Cliente> tabla;
    private TextField buscarField;

    private TextField nombreField;
    private TextField dniField;
    private TextField telefonoField;
    private TextField emailField;
    private Label     statusLabel;

    private Cliente selectedCliente = null;

    public ClientesView() { build(); }

    @SuppressWarnings("unchecked")
    private void build() {
        HBox toolbar = new HBox(10);
        toolbar.setPadding(new Insets(0, 0, 12, 0));
        toolbar.setAlignment(Pos.CENTER_LEFT);

        Label titulo = new Label("Gestion de Clientes");
        titulo.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        titulo.setStyle("-fx-text-fill: " + Config.C_TEXT + ";");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        buscarField = new TextField();
        buscarField.setPromptText("Buscar por nombre o DNI...");
        buscarField.setPrefWidth(260);
        buscarField.setStyle(fieldStyle());
        buscarField.textProperty().addListener((obs, old, val) -> buscar(val));

        Button nuevoBtn = buildBtn("Nuevo", Config.C_ACCENT);
        nuevoBtn.setOnAction(e -> limpiarFormulario());
        toolbar.getChildren().addAll(titulo, spacer, buscarField, nuevoBtn);

        // ── Tabla ─────────────────────────────────────────────────────────────
        tabla = new TableView<>();
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tabla.setPlaceholder(new Label("No hay clientes registrados"));
        tabla.getSelectionModel().selectedItemProperty()
            .addListener((obs, old, sel) -> cargarEnFormulario(sel));

        tabla.getColumns().addAll(
            col("ID",       50,  cd -> String.valueOf(cd.getValue().getId())),
            col("Nombre",   180, cd -> cd.getValue().getNombre()),
            col("DNI",      100, cd -> cd.getValue().getDni()),
            col("Telefono", 110, cd -> cd.getValue().getTelefono()),
            col("Email",    180, cd -> cd.getValue().getEmail())
        );
        VBox.setVgrow(tabla, Priority.ALWAYS);

        VBox form = buildForm();
        form.setPrefWidth(280);

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
        form.setStyle("-fx-background-color: " + Config.C_CARD_BG + ";"
            + "-fx-background-radius: 10;"
            + "-fx-padding: 20;"
            + "-fx-border-color: " + Config.C_BORDER + ";"
            + "-fx-border-radius: 10;");

        Label formTitle = new Label("Datos del Cliente");
        formTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        formTitle.setStyle("-fx-text-fill: " + Config.C_TEXT + ";");

        nombreField   = fieldWithLabel(form, "Nombre *");
        dniField      = fieldWithLabel(form, "DNI *");
        telefonoField = fieldWithLabel(form, "Telefono");
        emailField    = fieldWithLabel(form, "Email");

        statusLabel = new Label("");
        statusLabel.setWrapText(true);
        statusLabel.setFont(Font.font("Segoe UI", 11));

        Button guardarBtn  = buildBtn("Guardar",  Config.C_SUCCESS);
        Button eliminarBtn = buildBtn("Eliminar", Config.C_ERROR);
        guardarBtn.setMaxWidth(Double.MAX_VALUE);
        eliminarBtn.setMaxWidth(Double.MAX_VALUE);

        guardarBtn.setOnAction(e  -> guardar());
        eliminarBtn.setOnAction(e -> eliminar());

        form.getChildren().addAll(formTitle, guardarBtn, eliminarBtn, statusLabel);
        return form;
    }

    // ── Acciones ──────────────────────────────────────────────────────────────

    private void guardar() {
        try {
            String nombre   = nombreField.getText().strip();
            String dni      = dniField.getText().strip();
            String telefono = telefonoField.getText().strip();
            String email    = emailField.getText().strip();

            if (selectedCliente == null) {
                cliSvc.crear(nombre, dni, telefono, email);
                setStatus("Cliente creado exitosamente.", Config.C_SUCCESS);
            } else {
                cliSvc.actualizar(selectedCliente.getId(), nombre, dni, telefono, email);
                setStatus("Cliente actualizado.", Config.C_SUCCESS);
            }
            limpiarFormulario();
            refresh();
        } catch (Exception ex) {
            setStatus("Error: " + ex.getMessage(), Config.C_ERROR);
        }
    }

    private void eliminar() {
        if (selectedCliente == null) { setStatus("Seleccione un cliente.", Config.C_WARNING); return; }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "¿Eliminar al cliente '" + selectedCliente.getNombre() + "'?",
            ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText("Confirmar eliminacion");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                cliSvc.eliminar(selectedCliente.getId());
                limpiarFormulario();
                refresh();
                setStatus("Cliente eliminado.", Config.C_SUCCESS);
            }
        });
    }

    private void buscar(String termino) {
        List<Cliente> resultados = termino.isBlank()
            ? cliSvc.listar() : cliSvc.buscar(termino);
        tabla.setItems(FXCollections.observableArrayList(resultados));
    }

    private void cargarEnFormulario(Cliente c) {
        selectedCliente = c;
        if (c == null) return;
        nombreField.setText(c.getNombre());
        dniField.setText(c.getDni());
        telefonoField.setText(c.getTelefono());
        emailField.setText(c.getEmail());
        statusLabel.setText("");
    }

    private void limpiarFormulario() {
        selectedCliente = null;
        nombreField.clear(); dniField.clear();
        telefonoField.clear(); emailField.clear();
        statusLabel.setText("");
        tabla.getSelectionModel().clearSelection();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    @Override
    public void refresh() {
        tabla.setItems(FXCollections.observableArrayList(cliSvc.listar()));
    }

    private void setStatus(String msg, String color) {
        statusLabel.setText(msg);
        statusLabel.setStyle("-fx-text-fill: " + color + ";");
    }

    @FunctionalInterface
    interface CellValue { String get(TableColumn.CellDataFeatures<Cliente, String> cd); }

    @SuppressWarnings("unchecked")
    private TableColumn<Cliente, String> col(String title, int width, CellValue fn) {
        TableColumn<Cliente, String> c = new TableColumn<>(title);
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

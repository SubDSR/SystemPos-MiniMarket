package com.minimarket.views;

import com.minimarket.models.Cliente;
import com.minimarket.models.Producto;
import com.minimarket.models.Venta;
import com.minimarket.services.ClienteService;
import com.minimarket.services.ProductoService;
import com.minimarket.services.VentaService;
import com.minimarket.utils.Config;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Vista de punto de venta (POS).
 * Panel izquierdo: busqueda de productos.
 * Panel derecho: carrito + totales + registro.
 */
public class VentasView extends JPanel implements MainWindow.Refreshable {

    private final VentaService    ventaSvc;
    private final ProductoService productoSvc;
    private final ClienteService  clienteSvc;

    // Panel izquierdo
    private final JTextField       buscarField   = new JTextField();
    private final DefaultListModel<String> prodListModel = new DefaultListModel<>();
    private final JList<String>    prodList      = new JList<>(prodListModel);
    private final JSpinner         cantSpinner   = new JSpinner(new SpinnerNumberModel(1, 1, 9999, 1));
    private final List<Producto>   searchResults = new ArrayList<>();

    // Panel derecho — carrito
    private final DefaultTableModel cartModel;
    private final JTable            cartTable;
    private final List<int[]>       cartItems    = new ArrayList<>(); // {productoId, cantidad}

    // Totales
    private final JLabel lblSubtotal = new JLabel("S/ 0.00");
    private final JLabel lblIgv      = new JLabel("S/ 0.00");
    private final JLabel lblTotal    = new JLabel("S/ 0.00");
    private final JLabel statusLbl   = new JLabel(" ");

    // Cliente
    private final JComboBox<String> clienteCombo = new JComboBox<>();
    private final List<Integer>     clienteIds   = new ArrayList<>();

    public VentasView(VentaService ventaSvc, ProductoService productoSvc,
                      ClienteService clienteSvc) {
        this.ventaSvc    = ventaSvc;
        this.productoSvc = productoSvc;
        this.clienteSvc  = clienteSvc;

        setBackground(Config.C_MAIN_BG);
        setLayout(new BorderLayout(0, 18));
        setBorder(new EmptyBorder(28, 28, 28, 28));

        JLabel title = MainWindow.pageTitle("Nueva Venta");
        add(title, BorderLayout.NORTH);

        // Carrito
        String[] cols = {"ID", "Producto", "Cant.", "Precio", "Subtotal"};
        cartModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        cartTable = DashboardView.buildTable(cartModel);
        cartTable.getColumnModel().getColumn(0).setMaxWidth(0);
        cartTable.getColumnModel().getColumn(0).setMinWidth(0);
        cartTable.getColumnModel().getColumn(2).setMaxWidth(60);
        cartTable.getColumnModel().getColumn(3).setMaxWidth(90);
        cartTable.getColumnModel().getColumn(4).setMaxWidth(90);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
            buildLeftPanel(), buildRightPanel());
        split.setDividerLocation(420);
        split.setDividerSize(6);
        split.setBorder(null);
        split.setOpaque(false);
        add(split, BorderLayout.CENTER);
    }

    // ════════════════════════════════════════════════════════════════════════
    // CONSTRUCCION DE UI
    // ════════════════════════════════════════════════════════════════════════

    private JPanel buildLeftPanel() {
        JPanel p = MainWindow.card(new BorderLayout(0, 12));

        // Busqueda
        JPanel searchBar = new JPanel(new BorderLayout(6, 0));
        searchBar.setOpaque(false);
        buscarField.setFont(new Font("SansSerif", Font.PLAIN, 13));
        buscarField.setPreferredSize(new Dimension(0, 32));
        JButton btnBuscar = MainWindow.actionBtn("Buscar", Config.C_ACCENT);
        btnBuscar.addActionListener(e -> buscarProducto());
        buscarField.addActionListener(e -> buscarProducto());
        searchBar.add(new JLabel("Buscar producto:"), BorderLayout.NORTH);
        searchBar.add(buscarField, BorderLayout.CENTER);
        searchBar.add(btnBuscar,   BorderLayout.EAST);

        // Lista de resultados
        prodList.setFont(new Font("SansSerif", Font.PLAIN, 13));
        prodList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        prodList.setFixedCellHeight(30);
        JScrollPane scroll = new JScrollPane(prodList);
        scroll.setBorder(titledBorder("Resultados"));

        // Cantidad y agregar
        JPanel addBar = new JPanel(new BorderLayout(8, 0));
        addBar.setOpaque(false);
        JPanel cantPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        cantPanel.setOpaque(false);
        cantPanel.add(new JLabel("Cantidad:"));
        cantSpinner.setPreferredSize(new Dimension(70, 30));
        cantPanel.add(cantSpinner);

        JButton btnAgregar = MainWindow.actionBtn("Agregar al carrito", Config.C_SUCCESS);
        btnAgregar.addActionListener(e -> agregarAlCarrito());
        addBar.add(cantPanel,   BorderLayout.CENTER);
        addBar.add(btnAgregar,  BorderLayout.EAST);

        p.add(searchBar, BorderLayout.NORTH);
        p.add(scroll,    BorderLayout.CENTER);
        p.add(addBar,    BorderLayout.SOUTH);
        return p;
    }

    private JPanel buildRightPanel() {
        JPanel p = MainWindow.card(new BorderLayout(0, 12));

        // Carrito
        JScrollPane scroll = new JScrollPane(cartTable);
        scroll.setBorder(titledBorder("Carrito de Compras"));

        JButton btnEliminar = MainWindow.actionBtn("Quitar seleccionado", Config.C_ERROR);
        btnEliminar.addActionListener(e -> quitarDelCarrito());

        JPanel cartPanel = new JPanel(new BorderLayout(0, 6));
        cartPanel.setOpaque(false);
        cartPanel.add(scroll,       BorderLayout.CENTER);
        cartPanel.add(btnEliminar,  BorderLayout.SOUTH);

        // Totales
        JPanel totalesPanel = buildTotalesPanel();

        // Cliente + acciones
        JPanel bottomPanel = new JPanel(new BorderLayout(0, 10));
        bottomPanel.setOpaque(false);
        bottomPanel.add(totalesPanel, BorderLayout.NORTH);
        bottomPanel.add(buildAcciones(), BorderLayout.SOUTH);

        p.add(cartPanel,    BorderLayout.CENTER);
        p.add(bottomPanel,  BorderLayout.SOUTH);
        return p;
    }

    private JPanel buildTotalesPanel() {
        JPanel p = new JPanel(new GridLayout(3, 2, 0, 4));
        p.setBackground(new Color(248, 250, 252));
        p.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Config.C_BORDER, 1),
            new EmptyBorder(10, 14, 10, 14)
        ));

        Font labelFont = new Font("SansSerif", Font.PLAIN, 13);
        Font valueFont = new Font("SansSerif", Font.BOLD, 13);

        JLabel lSub = new JLabel("Subtotal:");      lSub.setFont(labelFont);
        JLabel lIgv = new JLabel("IGV (18%):");     lIgv.setFont(labelFont);
        JLabel lTot = new JLabel("TOTAL:");         lTot.setFont(new Font("SansSerif", Font.BOLD, 15));

        lblSubtotal.setFont(valueFont); lblSubtotal.setHorizontalAlignment(SwingConstants.RIGHT);
        lblIgv.setFont(valueFont);      lblIgv.setHorizontalAlignment(SwingConstants.RIGHT);
        lblTotal.setFont(new Font("SansSerif", Font.BOLD, 16));
        lblTotal.setForeground(Config.C_SUCCESS);
        lblTotal.setHorizontalAlignment(SwingConstants.RIGHT);

        p.add(lSub); p.add(lblSubtotal);
        p.add(lIgv); p.add(lblIgv);
        p.add(lTot); p.add(lblTotal);
        return p;
    }

    private JPanel buildAcciones() {
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setOpaque(false);

        // Cliente
        JPanel cliPanel = new JPanel(new BorderLayout(6, 0));
        cliPanel.setOpaque(false);
        JLabel cliLabel = new JLabel("Cliente (opcional):");
        cliLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        clienteCombo.setFont(new Font("SansSerif", Font.PLAIN, 13));
        cliPanel.add(cliLabel,    BorderLayout.NORTH);
        cliPanel.add(clienteCombo, BorderLayout.CENTER);

        // Botones
        JPanel btns = new JPanel(new GridLayout(1, 2, 10, 0));
        btns.setOpaque(false);
        JButton btnRegistrar = MainWindow.actionBtn("Registrar Venta", Config.C_SUCCESS);
        JButton btnLimpiar   = MainWindow.actionBtn("Limpiar",         Config.C_TEXT_MUTED);
        btnRegistrar.addActionListener(e -> registrarVenta());
        btnLimpiar.addActionListener(e   -> limpiarCarrito());
        btns.add(btnRegistrar);
        btns.add(btnLimpiar);

        statusLbl.setFont(new Font("SansSerif", Font.PLAIN, 11));
        statusLbl.setHorizontalAlignment(SwingConstants.CENTER);

        p.add(cliPanel, BorderLayout.NORTH);
        p.add(btns,     BorderLayout.CENTER);
        p.add(statusLbl, BorderLayout.SOUTH);
        return p;
    }

    // ════════════════════════════════════════════════════════════════════════
    // LOGICA
    // ════════════════════════════════════════════════════════════════════════

    @Override
    public void refresh() {
        cargarClientes();
    }

    private void cargarClientes() {
        clienteCombo.removeAllItems();
        clienteIds.clear();
        clienteCombo.addItem("— Sin cliente —");
        clienteIds.add(0);
        for (Cliente c : clienteSvc.listar()) {
            clienteCombo.addItem(c.getNombre() + " (" + c.getDni() + ")");
            clienteIds.add(c.getId());
        }
    }

    private void buscarProducto() {
        String txt = buscarField.getText().strip();
        searchResults.clear();
        prodListModel.clear();
        List<Producto> res = txt.isEmpty() ? productoSvc.listar() : productoSvc.buscar(txt);
        for (Producto p : res) {
            searchResults.add(p);
            prodListModel.addElement(
                String.format("[%d] %s — S/%.2f (stock: %d)", p.getId(), p.getNombre(), p.getPrecio(), p.getStock()));
        }
    }

    private void agregarAlCarrito() {
        int idx = prodList.getSelectedIndex();
        if (idx < 0) { status("Seleccione un producto de la lista.", Config.C_WARNING); return; }
        Producto prod = searchResults.get(idx);
        int cant = (int) cantSpinner.getValue();

        // Verificar si ya esta en el carrito
        for (int[] item : cartItems) {
            if (item[0] == prod.getId()) {
                int nuevaCant = item[1] + cant;
                if (prod.getStock() < nuevaCant) {
                    status("Stock insuficiente. Disponible: " + prod.getStock(), Config.C_ERROR);
                    return;
                }
                item[1] = nuevaCant;
                refreshCart();
                return;
            }
        }
        if (prod.getStock() < cant) {
            status("Stock insuficiente. Disponible: " + prod.getStock(), Config.C_ERROR);
            return;
        }
        cartItems.add(new int[]{prod.getId(), cant});
        refreshCart();
        status(" ", Config.C_TEXT_MUTED);
    }

    private void quitarDelCarrito() {
        int row = cartTable.getSelectedRow();
        if (row < 0) return;
        cartItems.remove(row);
        refreshCart();
    }

    private void refreshCart() {
        cartModel.setRowCount(0);
        double subtotalSum = 0;
        for (int[] item : cartItems) {
            Producto p = productoSvc.obtener(item[0]);
            if (p == null) continue;
            double sub = Math.round(p.getPrecio() * item[1] * 100.0) / 100.0;
            subtotalSum += sub;
            cartModel.addRow(new Object[]{
                p.getId(), p.getNombre(), item[1],
                String.format("S/ %.2f", p.getPrecio()),
                String.format("S/ %.2f", sub)
            });
        }
        subtotalSum = Math.round(subtotalSum * 100.0) / 100.0;
        double igv   = Math.round(subtotalSum * Config.IGV_RATE * 100.0) / 100.0;
        double total = Math.round((subtotalSum + igv) * 100.0) / 100.0;
        lblSubtotal.setText(String.format("S/ %.2f", subtotalSum));
        lblIgv.setText(String.format("S/ %.2f", igv));
        lblTotal.setText(String.format("S/ %.2f", total));
    }

    private void registrarVenta() {
        if (cartItems.isEmpty()) { status("El carrito esta vacio.", Config.C_WARNING); return; }
        int clienteIdx = clienteCombo.getSelectedIndex();
        int clienteId  = clienteIds.isEmpty() ? 0 : clienteIds.get(clienteIdx);

        Venta v = ventaSvc.crearVenta(cartItems, clienteId);
        if (v == null) {
            status("Error al registrar la venta. Verifique el stock.", Config.C_ERROR);
        } else {
            status("Venta #" + v.getId() + " registrada. Total: " +
                String.format("S/ %.2f", v.getTotal()), Config.C_SUCCESS);
            limpiarCarrito();
            buscarProducto();
        }
    }

    private void limpiarCarrito() {
        cartItems.clear();
        refreshCart();
        status(" ", Config.C_TEXT_MUTED);
    }

    private void status(String msg, Color color) {
        statusLbl.setText(msg);
        statusLbl.setForeground(color);
    }

    private static TitledBorder titledBorder(String title) {
        TitledBorder b = BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(Config.C_BORDER), title);
        b.setTitleFont(new Font("SansSerif", Font.BOLD, 12));
        b.setTitleColor(Config.C_TEXT_MUTED);
        return b;
    }
}

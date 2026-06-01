package com.minimarket.client.views;

import com.minimarket.client.models.DetalleVenta;
import com.minimarket.client.models.Producto;
import com.minimarket.client.models.Venta;
import com.minimarket.client.services.ProductoService;
import com.minimarket.client.services.VentaService;
import com.minimarket.client.utils.Config;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

/**
 * Vista del historial de ventas.
 * Tabla superior: ventas (con filtro por fecha).
 * Tabla inferior: detalles de la venta seleccionada.
 */
public class HistorialView extends JPanel implements MainWindow.Refreshable {

    private final VentaService    ventaSvc;
    private final ProductoService productoSvc;

    private final DefaultTableModel ventasModel;
    private final JTable            ventasTable;
    private final DefaultTableModel detModel;
    private final JTable            detTable;

    private final JTextField fFecha  = new JTextField(12);
    private final JLabel     statusLbl = new JLabel(" ");

    private List<Venta> ventasActuales = List.of();

    public HistorialView(VentaService ventaSvc, ProductoService productoSvc) {
        this.ventaSvc    = ventaSvc;
        this.productoSvc = productoSvc;

        setBackground(Config.C_MAIN_BG);
        setLayout(new BorderLayout(0, 18));
        setBorder(new EmptyBorder(28, 28, 28, 28));

        add(buildHeader(), BorderLayout.NORTH);

        // Tabla de ventas
        String[] vCols = {"ID", "Fecha", "Cliente", "Sucursal", "Subtotal", "IGV", "Total", "Estado"};
        ventasModel = new DefaultTableModel(vCols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        ventasTable = DashboardView.buildTable(ventasModel);
        ventasTable.getColumnModel().getColumn(0).setMaxWidth(55);
        ventasTable.getColumnModel().getColumn(2).setMaxWidth(90);
        ventasTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) cargarDetalles();
        });

        JScrollPane vScroll = new JScrollPane(ventasTable);
        vScroll.setBorder(BorderFactory.createEmptyBorder());
        JPanel ventasCard = MainWindow.card(new BorderLayout(0, 6));
        ventasCard.add(MainWindow.sectionLabel("Ventas"), BorderLayout.NORTH);
        ventasCard.add(vScroll, BorderLayout.CENTER);

        // Tabla de detalles
        String[] dCols = {"ID Det.", "Producto", "Cantidad", "Precio Unit.", "Subtotal"};
        detModel = new DefaultTableModel(dCols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        detTable = DashboardView.buildTable(detModel);
        detTable.getColumnModel().getColumn(0).setMaxWidth(60);
        detTable.getColumnModel().getColumn(2).setMaxWidth(80);

        JScrollPane dScroll = new JScrollPane(detTable);
        dScroll.setBorder(BorderFactory.createEmptyBorder());
        JPanel detCard = MainWindow.card(new BorderLayout(0, 6));
        detCard.add(MainWindow.sectionLabel("Detalle de Venta Seleccionada"), BorderLayout.NORTH);
        detCard.add(dScroll, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, ventasCard, detCard);
        split.setDividerLocation(340);
        split.setDividerSize(6);
        split.setBorder(null);
        split.setOpaque(false);
        add(split, BorderLayout.CENTER);
    }

    private JPanel buildHeader() {
        JPanel p = new JPanel(new BorderLayout(12, 0));
        p.setOpaque(false);
        p.add(MainWindow.pageTitle("Historial de Ventas"), BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        fFecha.setFont(new Font("SansSerif", Font.PLAIN, 13));

        JButton btnFiltrar  = MainWindow.actionBtn("Filtrar",  Config.C_ACCENT);
        JButton btnTodas    = MainWindow.actionBtn("Todas",    Config.C_TEXT_MUTED);
        JButton btnAnular   = MainWindow.actionBtn("Anular Venta", Config.C_ERROR);

        btnFiltrar.addActionListener(e  -> filtrar());
        btnTodas.addActionListener(e    -> { fFecha.setText(""); refresh(); });
        btnAnular.addActionListener(e   -> anular());

        right.add(new JLabel("Fecha (yyyy-MM-dd):"));
        right.add(fFecha);
        right.add(btnFiltrar);
        right.add(btnTodas);
        right.add(btnAnular);

        statusLbl.setFont(new Font("SansSerif", Font.PLAIN, 11));
        right.add(statusLbl);

        p.add(right, BorderLayout.EAST);
        return p;
    }

    @Override
    public void refresh() {
        ventasActuales = ventaSvc.listarVentas();
        loadVentasTable(ventasActuales);
        detModel.setRowCount(0);
    }

    private void filtrar() {
        String fecha = fFecha.getText().strip();
        if (fecha.isEmpty()) { refresh(); return; }
        ventasActuales = ventaSvc.listarVentas().stream()
            .filter(v -> v.getFecha().startsWith(fecha))
            .toList();
        loadVentasTable(ventasActuales);
        detModel.setRowCount(0);
    }

    private void loadVentasTable(List<Venta> lista) {
        ventasModel.setRowCount(0);
        for (Venta v : lista) {
            ventasModel.addRow(new Object[]{
                v.getId(), v.getFecha(),
                v.getClienteId() == 0 ? "-" : "C#" + v.getClienteId(),
                "S#" + v.getSucursalId(),
                String.format("S/ %.2f", v.getSubtotal()),
                String.format("S/ %.2f", v.getIgv()),
                String.format("S/ %.2f", v.getTotal()),
                v.getEstado() == 1 ? "Activa" : "Anulada"
            });
        }
    }

    private void cargarDetalles() {
        int row = ventasTable.getSelectedRow();
        if (row < 0) { detModel.setRowCount(0); return; }
        int ventaId = (int) ventasModel.getValueAt(row, 0);

        List<DetalleVenta> detalles = ventaSvc.obtenerDetalles(ventaId);
        detModel.setRowCount(0);
        for (DetalleVenta d : detalles) {
            Producto p = productoSvc.obtener(d.getProductoId());
            String nombre = p != null ? p.getNombre() : "Producto #" + d.getProductoId();
            detModel.addRow(new Object[]{
                d.getId(), nombre, d.getCantidad(),
                String.format("S/ %.2f", d.getPrecioUnitario()),
                String.format("S/ %.2f", d.getSubtotal())
            });
        }
    }

    private void anular() {
        int row = ventasTable.getSelectedRow();
        if (row < 0) { status("Seleccione una venta.", Config.C_WARNING); return; }
        int ventaId = (int) ventasModel.getValueAt(row, 0);
        String estado = (String) ventasModel.getValueAt(row, 7);
        if ("Anulada".equals(estado)) { status("La venta ya esta anulada.", Config.C_WARNING); return; }

        int ok = JOptionPane.showConfirmDialog(this,
            "¿Anular venta #" + ventaId + "? Se restaurara el stock.",
            "Confirmar anulacion", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (ok != JOptionPane.YES_OPTION) return;

        if (ventaSvc.anularVenta(ventaId)) {
            status("Venta #" + ventaId + " anulada.", Config.C_SUCCESS);
            refresh();
        } else {
            status("No se pudo anular.", Config.C_ERROR);
        }
    }

    private void status(String msg, Color color) {
        statusLbl.setText(msg);
        statusLbl.setForeground(color);
    }
}

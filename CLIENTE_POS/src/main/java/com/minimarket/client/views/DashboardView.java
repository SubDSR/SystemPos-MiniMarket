package com.minimarket.client.views;

import com.minimarket.client.models.Venta;
import com.minimarket.client.services.ClienteService;
import com.minimarket.client.services.ProductoService;
import com.minimarket.client.services.VentaService;
import com.minimarket.client.utils.Config;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.List;

/**
 * Vista del dashboard: KPIs del dia + tabla de ultimas ventas.
 */
public class DashboardView extends JPanel implements MainWindow.Refreshable {

    private final VentaService    ventaSvc;
    private final ProductoService productoSvc;
    private final ClienteService  clienteSvc;

    private final JLabel kpiVentas    = kpiValue("0");
    private final JLabel kpiIngresos  = kpiValue("S/ 0.00");
    private final JLabel kpiProductos = kpiValue("0");
    private final JLabel kpiClientes  = kpiValue("0");

    private final DefaultTableModel tableModel;

    public DashboardView(VentaService ventaSvc, ProductoService productoSvc,
                         ClienteService clienteSvc) {
        this.ventaSvc    = ventaSvc;
        this.productoSvc = productoSvc;
        this.clienteSvc  = clienteSvc;

        setBackground(Config.C_MAIN_BG);
        setLayout(new BorderLayout(0, 20));
        setBorder(new EmptyBorder(28, 28, 28, 28));

        // Cabecera
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(MainWindow.pageTitle("Dashboard"), BorderLayout.WEST);

        JLabel fecha = new JLabel(
            "Hoy: " + java.time.LocalDate.now().format(
                java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        fecha.setFont(new Font("SansSerif", Font.PLAIN, 13));
        fecha.setForeground(Config.C_TEXT_MUTED);
        header.add(fecha, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        // KPIs
        JPanel kpiRow = new JPanel(new GridLayout(1, 4, 16, 0));
        kpiRow.setOpaque(false);
        kpiRow.add(kpiCard("Ventas Hoy",    kpiVentas,    Config.C_ACCENT));
        kpiRow.add(kpiCard("Ingresos Hoy",  kpiIngresos,  Config.C_SUCCESS));
        kpiRow.add(kpiCard("Productos",      kpiProductos, new Color(124, 58, 237)));
        kpiRow.add(kpiCard("Clientes",       kpiClientes,  new Color(234, 88, 12)));

        // Tabla de ultimas ventas
        String[] cols = {"ID", "Fecha", "Cliente ID", "Sucursal", "Subtotal", "IGV", "Total", "Estado"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = buildTable(tableModel);
        table.getColumnModel().getColumn(0).setMaxWidth(60);
        table.getColumnModel().getColumn(2).setMaxWidth(90);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createEmptyBorder());

        JPanel tableCard = MainWindow.card(new BorderLayout(0, 10));
        JLabel tLabel = new JLabel("Ultimas Ventas");
        tLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        tLabel.setForeground(Config.C_TEXT);
        tableCard.add(tLabel, BorderLayout.NORTH);
        tableCard.add(scroll, BorderLayout.CENTER);

        JPanel center = new JPanel(new BorderLayout(0, 18));
        center.setOpaque(false);
        center.add(kpiRow, BorderLayout.NORTH);
        center.add(tableCard, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);
    }

    @Override
    public void refresh() {
        List<Venta> hoy = ventaSvc.ventasHoy();
        kpiVentas.setText(String.valueOf(hoy.size()));
        kpiIngresos.setText(String.format("S/ %.2f", ventaSvc.totalHoy()));
        kpiProductos.setText(String.valueOf(productoSvc.contar()));
        kpiClientes.setText(String.valueOf(clienteSvc.contar()));

        tableModel.setRowCount(0);
        List<Venta> todas = ventaSvc.listarVentas();
        int from = Math.max(0, todas.size() - 10);
        for (int i = todas.size() - 1; i >= from; i--) {
            Venta v = todas.get(i);
            tableModel.addRow(new Object[]{
                v.getId(), v.getFecha(), v.getClienteId() == 0 ? "-" : v.getClienteId(),
                "S#" + v.getSucursalId(),
                String.format("S/ %.2f", v.getSubtotal()),
                String.format("S/ %.2f", v.getIgv()),
                String.format("S/ %.2f", v.getTotal()),
                v.getEstado() == 1 ? "Activa" : "Anulada"
            });
        }
    }

    // ── Helpers de construccion ────────────────────────────────────────────

    private static JPanel kpiCard(String titulo, JLabel value, Color color) {
        JPanel card = new JPanel(new BorderLayout(0, 10));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 4, 0, 0, color),
            BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Config.C_BORDER, 1),
                new EmptyBorder(16, 18, 16, 18)
            )
        ));

        JLabel lbl = new JLabel(titulo.toUpperCase());
        lbl.setFont(new Font("SansSerif", Font.BOLD, 10));
        lbl.setForeground(Config.C_TEXT_MUTED);

        value.setFont(new Font("SansSerif", Font.BOLD, 26));
        value.setForeground(color);

        card.add(lbl,   BorderLayout.NORTH);
        card.add(value, BorderLayout.CENTER);
        return card;
    }

    private static JLabel kpiValue(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("SansSerif", Font.BOLD, 26));
        return l;
    }

    static JTable buildTable(DefaultTableModel model) {
        JTable table = new JTable(model);
        table.setFont(new Font("SansSerif", Font.PLAIN, 13));
        table.setRowHeight(30);
        table.setGridColor(Config.C_BORDER);
        table.setShowVerticalLines(false);
        table.setSelectionBackground(new Color(219, 234, 254));
        table.setSelectionForeground(Config.C_TEXT);
        table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));
        table.getTableHeader().setBackground(new Color(248, 250, 252));
        table.getTableHeader().setForeground(Config.C_TEXT_MUTED);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);

        // Filas alternadas
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val,
                    boolean sel, boolean focus, int row, int col) {
                Component c = super.getTableCellRendererComponent(t, val, sel, focus, row, col);
                if (!sel) c.setBackground(row % 2 == 0 ? Color.WHITE : new Color(248, 250, 252));
                return c;
            }
        });
        return table;
    }
}

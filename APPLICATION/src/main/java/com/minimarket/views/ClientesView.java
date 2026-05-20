package com.minimarket.views;

import com.minimarket.models.Cliente;
import com.minimarket.services.ClienteService;
import com.minimarket.utils.Config;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

/**
 * Vista CRUD de clientes: tabla + formulario lateral.
 */
public class ClientesView extends JPanel implements MainWindow.Refreshable {

    private final ClienteService svc;

    private final DefaultTableModel tableModel;
    private final JTable table;

    private final JTextField fNombre    = new JTextField();
    private final JTextField fDni       = new JTextField();
    private final JTextField fTelefono  = new JTextField();
    private final JTextField fEmail     = new JTextField();
    private final JTextField fBuscar    = new JTextField();
    private final JLabel     statusLbl  = new JLabel(" ");

    private int editingId = 0;

    public ClientesView(ClienteService svc) {
        this.svc = svc;
        setBackground(Config.C_MAIN_BG);
        setLayout(new BorderLayout(0, 18));
        setBorder(new EmptyBorder(28, 28, 28, 28));

        add(buildHeader(), BorderLayout.NORTH);

        String[] cols = {"ID", "Nombre", "DNI", "Telefono", "Email"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        table = DashboardView.buildTable(tableModel);
        table.getColumnModel().getColumn(0).setMaxWidth(55);
        table.getColumnModel().getColumn(2).setMaxWidth(100);
        table.getColumnModel().getColumn(3).setMaxWidth(110);

        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) populateForm();
        });

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        JPanel tableCard = MainWindow.card(new BorderLayout());
        tableCard.add(scroll, BorderLayout.CENTER);

        add(tableCard,  BorderLayout.CENTER);
        add(buildForm(), BorderLayout.EAST);
    }

    private JPanel buildHeader() {
        JPanel p = new JPanel(new BorderLayout(12, 0));
        p.setOpaque(false);
        p.add(MainWindow.pageTitle("Clientes"), BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        fBuscar.setPreferredSize(new Dimension(200, 32));
        JButton btnBuscar = MainWindow.actionBtn("Buscar",  Config.C_ACCENT);
        JButton btnTodos  = MainWindow.actionBtn("Todos",   Config.C_TEXT_MUTED);
        btnBuscar.addActionListener(e -> loadTable(svc.buscar(fBuscar.getText())));
        btnTodos.addActionListener(e  -> { fBuscar.setText(""); refresh(); });

        right.add(new JLabel("Buscar:"));
        right.add(fBuscar);
        right.add(btnBuscar);
        right.add(btnTodos);
        p.add(right, BorderLayout.EAST);
        return p;
    }

    private JPanel buildForm() {
        JPanel card = MainWindow.card(new BorderLayout(0, 14));
        card.setPreferredSize(new Dimension(280, 0));

        JLabel title = new JLabel("Detalle del Cliente");
        title.setFont(new Font("SansSerif", Font.BOLD, 14));
        title.setForeground(Config.C_TEXT);
        card.add(title, BorderLayout.NORTH);

        JPanel fields = new JPanel(new GridBagLayout());
        fields.setOpaque(false);
        GridBagConstraints gc = new GridBagConstraints();
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.insets = new Insets(4, 0, 4, 0);
        gc.gridx = 0; gc.weightx = 1;

        addField(fields, gc, "Nombre *",  fNombre,   0);
        addField(fields, gc, "DNI *",     fDni,      1);
        addField(fields, gc, "Telefono",  fTelefono, 2);
        addField(fields, gc, "Email",     fEmail,    3);
        card.add(fields, BorderLayout.CENTER);

        JPanel btns = new JPanel(new GridLayout(1, 3, 8, 0));
        btns.setOpaque(false);
        JButton btnNuevo    = MainWindow.actionBtn("Nuevo",    Config.C_TEXT_MUTED);
        JButton btnGuardar  = MainWindow.actionBtn("Guardar",  Config.C_SUCCESS);
        JButton btnEliminar = MainWindow.actionBtn("Eliminar", Config.C_ERROR);

        btnNuevo.addActionListener(e    -> clearForm());
        btnGuardar.addActionListener(e  -> guardar());
        btnEliminar.addActionListener(e -> eliminar());

        btns.add(btnNuevo);
        btns.add(btnGuardar);
        btns.add(btnEliminar);

        JPanel south = new JPanel(new BorderLayout(0, 8));
        south.setOpaque(false);
        statusLbl.setFont(new Font("SansSerif", Font.PLAIN, 11));
        statusLbl.setForeground(Config.C_TEXT_MUTED);
        south.add(btns,      BorderLayout.NORTH);
        south.add(statusLbl, BorderLayout.SOUTH);
        card.add(south, BorderLayout.SOUTH);
        return card;
    }

    private static void addField(JPanel p, GridBagConstraints gc,
                                  String label, JTextField field, int row) {
        gc.gridy = row * 2;
        JLabel lbl = new JLabel(label);
        lbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
        lbl.setForeground(Config.C_TEXT_MUTED);
        p.add(lbl, gc);
        gc.gridy = row * 2 + 1;
        field.setFont(new Font("SansSerif", Font.PLAIN, 13));
        field.setPreferredSize(new Dimension(0, 32));
        p.add(field, gc);
    }

    @Override
    public void refresh() {
        loadTable(svc.listar());
    }

    private void loadTable(List<Cliente> lista) {
        tableModel.setRowCount(0);
        for (Cliente c : lista) {
            tableModel.addRow(new Object[]{
                c.getId(), c.getNombre(), c.getDni(), c.getTelefono(), c.getEmail()
            });
        }
        clearForm();
    }

    private void populateForm() {
        int row = table.getSelectedRow();
        if (row < 0) return;
        int id = (int) tableModel.getValueAt(row, 0);
        Cliente c = svc.obtener(id);
        if (c == null) return;
        editingId = c.getId();
        fNombre.setText(c.getNombre());
        fDni.setText(c.getDni());
        fTelefono.setText(c.getTelefono());
        fEmail.setText(c.getEmail());
        status(" ", Config.C_TEXT_MUTED);
    }

    private void guardar() {
        String nombre = fNombre.getText().strip();
        String dni    = fDni.getText().strip();
        if (nombre.isEmpty()) { status("El nombre es obligatorio.", Config.C_ERROR); return; }
        if (dni.isEmpty())    { status("El DNI es obligatorio.", Config.C_ERROR); return; }
        try {
            if (editingId == 0) {
                svc.crear(nombre, dni, fTelefono.getText().strip(), fEmail.getText().strip());
                status("Cliente creado.", Config.C_SUCCESS);
            } else {
                svc.actualizar(editingId, nombre, dni,
                    fTelefono.getText().strip(), fEmail.getText().strip());
                status("Cliente actualizado.", Config.C_SUCCESS);
            }
            refresh();
        } catch (IllegalArgumentException ex) {
            status(ex.getMessage(), Config.C_ERROR);
        }
    }

    private void eliminar() {
        if (editingId == 0) { status("Seleccione un cliente.", Config.C_WARNING); return; }
        int ok = JOptionPane.showConfirmDialog(this,
            "¿Eliminar cliente ID=" + editingId + "?", "Confirmar",
            JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (ok != JOptionPane.YES_OPTION) return;
        if (svc.eliminar(editingId)) {
            status("Cliente eliminado.", Config.C_SUCCESS);
            refresh();
        } else {
            status("No se pudo eliminar.", Config.C_ERROR);
        }
    }

    private void clearForm() {
        editingId = 0;
        fNombre.setText(""); fDni.setText("");
        fTelefono.setText(""); fEmail.setText("");
        table.clearSelection();
        status(" ", Config.C_TEXT_MUTED);
    }

    private void status(String msg, Color color) {
        statusLbl.setText(msg);
        statusLbl.setForeground(color);
    }
}

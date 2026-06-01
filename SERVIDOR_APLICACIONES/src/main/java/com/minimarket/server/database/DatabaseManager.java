package com.minimarket.server.database;

import com.minimarket.server.models.Cliente;
import com.minimarket.server.models.DetalleVenta;
import com.minimarket.server.models.Producto;
import com.minimarket.server.models.Sucursal;
import com.minimarket.server.models.Venta;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/** Acceso centralizado a SQLite operativo del servidor de datos. */
public class DatabaseManager {

    private static final Logger LOG = Logger.getLogger(DatabaseManager.class.getName());
    private static final double IGV_RATE = 0.18;
    private static final DateTimeFormatter FECHA_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String SCHEMA = """
        PRAGMA foreign_keys = ON;
        PRAGMA journal_mode = WAL;

        CREATE TABLE IF NOT EXISTS sucursales (
            id_sucursal INTEGER PRIMARY KEY AUTOINCREMENT,
            nombre      TEXT NOT NULL,
            direccion   TEXT,
            activo      INTEGER NOT NULL DEFAULT 1
        );

        CREATE TABLE IF NOT EXISTS productos (
            id        INTEGER PRIMARY KEY AUTOINCREMENT,
            nombre    TEXT NOT NULL,
            precio    REAL NOT NULL CHECK(precio >= 0),
            stock     INTEGER NOT NULL CHECK(stock >= 0),
            categoria TEXT DEFAULT '',
            estado    INTEGER NOT NULL DEFAULT 1
        );

        CREATE TABLE IF NOT EXISTS clientes (
            id       INTEGER PRIMARY KEY AUTOINCREMENT,
            nombre   TEXT NOT NULL,
            dni      TEXT NOT NULL UNIQUE,
            telefono TEXT DEFAULT '',
            email    TEXT DEFAULT '',
            estado   INTEGER NOT NULL DEFAULT 1
        );

        CREATE TABLE IF NOT EXISTS ventas (
            id          INTEGER PRIMARY KEY AUTOINCREMENT,
            cliente_id  INTEGER NOT NULL DEFAULT 0,
            id_sucursal INTEGER NOT NULL DEFAULT 1,
            fecha       TEXT NOT NULL,
            subtotal    REAL NOT NULL,
            igv         REAL NOT NULL,
            total       REAL NOT NULL,
            estado      INTEGER NOT NULL DEFAULT 1,
            FOREIGN KEY (id_sucursal) REFERENCES sucursales(id_sucursal)
        );

        CREATE TABLE IF NOT EXISTS detalle_ventas (
            id              INTEGER PRIMARY KEY AUTOINCREMENT,
            venta_id        INTEGER NOT NULL,
            producto_id     INTEGER NOT NULL,
            cantidad        INTEGER NOT NULL CHECK(cantidad > 0),
            precio_unitario REAL NOT NULL,
            subtotal        REAL NOT NULL,
            estado          INTEGER NOT NULL DEFAULT 1,
            FOREIGN KEY (venta_id) REFERENCES ventas(id),
            FOREIGN KEY (producto_id) REFERENCES productos(id)
        );
        """;

    private final Path dbPath;

    public DatabaseManager(Path dbPath) {
        this.dbPath = dbPath;
        try {
            Files.createDirectories(dbPath.getParent());
            Class.forName("org.sqlite.JDBC");
        } catch (Exception e) {
            LOG.warning("Driver SQLite no inicializado todavia: " + e.getMessage());
        }
    }

    public Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
            st.execute("PRAGMA journal_mode = WAL");
        }
        return conn;
    }

    public void initDatabase() {
        try (Connection conn = getConnection(); Statement st = conn.createStatement()) {
            for (String sql : SCHEMA.split(";")) {
                String trimmed = sql.strip();
                if (!trimmed.isEmpty()) st.execute(trimmed);
            }
            migrateVentasSucursal(st);
            seedBaseData(conn);
            LOG.info("Base de datos operativa lista: " + dbPath);
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo inicializar SQLite: " + e.getMessage(), e);
        }
    }

    private void migrateVentasSucursal(Statement st) {
        try {
            st.execute("ALTER TABLE ventas ADD COLUMN id_sucursal INTEGER NOT NULL DEFAULT 1");
        } catch (SQLException ignored) {
            // SQLite no soporta ADD COLUMN IF NOT EXISTS.
        }
    }

    private void seedBaseData(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR IGNORE INTO sucursales (id_sucursal, nombre, direccion) VALUES (?, ?, ?)")) {
            Object[][] rows = {
                {1, "Lima", "Av. Principal 123"},
                {2, "Arequipa", "Calle Real 456"},
                {3, "Trujillo", "Jr. Libertad 789"}
            };
            for (Object[] row : rows) {
                ps.setInt(1, (Integer) row[0]);
                ps.setString(2, (String) row[1]);
                ps.setString(3, (String) row[2]);
                ps.executeUpdate();
            }
        }

        if (countRows(conn, "productos") == 0) {
            Object[][] productos = {
                {"Arroz Costeno 1kg", 4.80, 80, "Abarrotes"},
                {"Azucar Rubia 1kg", 4.20, 65, "Abarrotes"},
                {"Aceite Vegetal 1L", 9.90, 40, "Abarrotes"},
                {"Leche Evaporada", 4.10, 90, "Lacteos"},
                {"Pan Molde", 7.50, 35, "Panaderia"},
                {"Detergente 800g", 12.40, 25, "Limpieza"},
                {"Gaseosa 1.5L", 6.80, 55, "Bebidas"},
                {"Cafe Instantaneo", 11.20, 30, "Bebidas"},
                {"Fideos Spaghetti", 3.60, 70, "Abarrotes"},
                {"Atun en Lata", 5.90, 50, "Conservas"}
            };
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO productos (nombre, precio, stock, categoria, estado) VALUES (?, ?, ?, ?, 1)")) {
                for (Object[] p : productos) {
                    ps.setString(1, (String) p[0]);
                    ps.setDouble(2, (Double) p[1]);
                    ps.setInt(3, (Integer) p[2]);
                    ps.setString(4, (String) p[3]);
                    ps.executeUpdate();
                }
            }
        }

        if (countRows(conn, "clientes") == 0) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO clientes (nombre, dni, telefono, email, estado) VALUES (?, ?, ?, ?, 1)")) {
                Object[][] clientes = {
                    {"Cliente General", "00000000", "", ""},
                    {"Maria Lopez", "71234567", "999111222", "maria@example.com"}
                };
                for (Object[] c : clientes) {
                    ps.setString(1, (String) c[0]);
                    ps.setString(2, (String) c[1]);
                    ps.setString(3, (String) c[2]);
                    ps.setString(4, (String) c[3]);
                    ps.executeUpdate();
                }
            }
        }
    }

    private int countRows(Connection conn, String table) throws SQLException {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    public List<Sucursal> listarSucursales() throws SQLException {
        List<Sucursal> list = new ArrayList<>();
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(
                "SELECT id_sucursal, nombre, direccion, activo FROM sucursales WHERE activo = 1 ORDER BY id_sucursal")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapSucursal(rs));
            }
        }
        return list;
    }

    public List<Producto> listarProductos() throws SQLException {
        List<Producto> list = new ArrayList<>();
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(
                "SELECT id, nombre, precio, stock, categoria, estado FROM productos WHERE estado = 1 ORDER BY id")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapProducto(rs));
            }
        }
        return list;
    }

    public Producto buscarProducto(int id) throws SQLException {
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(
                "SELECT id, nombre, precio, stock, categoria, estado FROM productos WHERE id = ? AND estado = 1")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapProducto(rs) : null;
            }
        }
    }

    public int insertarProducto(String nombre, double precio, int stock, String categoria) throws SQLException {
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO productos (nombre, precio, stock, categoria, estado) VALUES (?, ?, ?, ?, 1)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, nombre);
            ps.setDouble(2, precio);
            ps.setInt(3, stock);
            ps.setString(4, categoria);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public void actualizarProducto(int id, String nombre, double precio, int stock, String categoria) throws SQLException {
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(
                "UPDATE productos SET nombre = ?, precio = ?, stock = ?, categoria = ? WHERE id = ? AND estado = 1")) {
            ps.setString(1, nombre);
            ps.setDouble(2, precio);
            ps.setInt(3, stock);
            ps.setString(4, categoria);
            ps.setInt(5, id);
            if (ps.executeUpdate() == 0) throw new SQLException("Producto no encontrado");
        }
    }

    public void eliminarProducto(int id) throws SQLException {
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(
                "UPDATE productos SET estado = 0 WHERE id = ? AND estado = 1")) {
            ps.setInt(1, id);
            if (ps.executeUpdate() == 0) throw new SQLException("Producto no encontrado");
        }
    }

    public List<Cliente> listarClientes() throws SQLException {
        List<Cliente> list = new ArrayList<>();
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(
                "SELECT id, nombre, dni, telefono, email, estado FROM clientes WHERE estado = 1 ORDER BY id")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapCliente(rs));
            }
        }
        return list;
    }

    public Cliente buscarCliente(int id) throws SQLException {
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(
                "SELECT id, nombre, dni, telefono, email, estado FROM clientes WHERE id = ? AND estado = 1")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapCliente(rs) : null;
            }
        }
    }

    public int insertarCliente(String nombre, String dni, String telefono, String email) throws SQLException {
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO clientes (nombre, dni, telefono, email, estado) VALUES (?, ?, ?, ?, 1)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, nombre);
            ps.setString(2, dni);
            ps.setString(3, telefono);
            ps.setString(4, email);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public void actualizarCliente(int id, String nombre, String dni, String telefono, String email) throws SQLException {
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(
                "UPDATE clientes SET nombre = ?, dni = ?, telefono = ?, email = ? WHERE id = ? AND estado = 1")) {
            ps.setString(1, nombre);
            ps.setString(2, dni);
            ps.setString(3, telefono);
            ps.setString(4, email);
            ps.setInt(5, id);
            if (ps.executeUpdate() == 0) throw new SQLException("Cliente no encontrado");
        }
    }

    public void eliminarCliente(int id) throws SQLException {
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(
                "UPDATE clientes SET estado = 0 WHERE id = ? AND estado = 1")) {
            ps.setInt(1, id);
            if (ps.executeUpdate() == 0) throw new SQLException("Cliente no encontrado");
        }
    }

    public synchronized String registrarVenta(int clienteId, int sucursalId, Map<Integer, Integer> items) {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                validarSucursal(conn, sucursalId);
                if (clienteId > 0) validarClienteActivo(conn, clienteId);
                if (items == null || items.isEmpty()) throw new SQLException("La venta no tiene items");

                Map<Integer, Producto> productos = new LinkedHashMap<>();
                double subtotal = 0.0;
                for (Map.Entry<Integer, Integer> entry : items.entrySet()) {
                    int productoId = entry.getKey();
                    int cantidad = entry.getValue();
                    if (cantidad <= 0) throw new SQLException("Cantidad invalida para producto " + productoId);
                    Producto producto = buscarProductoActivo(conn, productoId);
                    if (producto == null) throw new SQLException("Producto no existe o esta inactivo: " + productoId);
                    if (producto.getStock() < cantidad) {
                        throw new SQLException("Stock insuficiente para " + producto.getNombre()
                            + ": disponible=" + producto.getStock() + ", solicitado=" + cantidad);
                    }
                    productos.put(productoId, producto);
                    subtotal += round2(producto.getPrecio() * cantidad);
                }

                subtotal = round2(subtotal);
                double igv = round2(subtotal * IGV_RATE);
                double total = round2(subtotal + igv);
                int ventaId = insertarCabeceraVenta(conn, clienteId, sucursalId, subtotal, igv, total);

                for (Map.Entry<Integer, Integer> entry : items.entrySet()) {
                    Producto producto = productos.get(entry.getKey());
                    int cantidad = entry.getValue();
                    double lineaSubtotal = round2(producto.getPrecio() * cantidad);
                    insertarDetalleVenta(conn, ventaId, producto.getId(), cantidad, producto.getPrecio(), lineaSubtotal);
                    descontarStock(conn, producto.getId(), cantidad);
                }

                conn.commit();
                return "OK|" + ventaId;
            } catch (Exception e) {
                conn.rollback();
                return "ERROR|TX|" + e.getMessage();
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            return "ERROR|TX|" + e.getMessage();
        }
    }

    public List<Venta> listarVentas() throws SQLException {
        List<Venta> list = new ArrayList<>();
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(
                "SELECT id, cliente_id, id_sucursal, fecha, subtotal, igv, total, estado FROM ventas ORDER BY id")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapVenta(rs));
            }
        }
        return list;
    }

    public Venta buscarVenta(int id) throws SQLException {
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(
                "SELECT id, cliente_id, id_sucursal, fecha, subtotal, igv, total, estado FROM ventas WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapVenta(rs) : null;
            }
        }
    }

    public List<DetalleVenta> listarDetalles(Integer ventaId) throws SQLException {
        List<DetalleVenta> list = new ArrayList<>();
        String sql = "SELECT id, venta_id, producto_id, cantidad, precio_unitario, subtotal, estado "
            + "FROM detalle_ventas" + (ventaId == null ? "" : " WHERE venta_id = ?") + " ORDER BY id";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            if (ventaId != null) ps.setInt(1, ventaId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapDetalle(rs));
            }
        }
        return list;
    }

    public synchronized void anularVenta(int ventaId) throws SQLException {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                Venta venta = buscarVenta(conn, ventaId);
                if (venta == null || venta.getEstado() == 0) throw new SQLException("Venta no encontrada o ya anulada");
                try (PreparedStatement det = conn.prepareStatement(
                        "SELECT producto_id, cantidad FROM detalle_ventas WHERE venta_id = ? AND estado = 1")) {
                    det.setInt(1, ventaId);
                    try (ResultSet rs = det.executeQuery()) {
                        while (rs.next()) restaurarStock(conn, rs.getInt("producto_id"), rs.getInt("cantidad"));
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement("UPDATE detalle_ventas SET estado = 0 WHERE venta_id = ?")) {
                    ps.setInt(1, ventaId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement("UPDATE ventas SET estado = 0 WHERE id = ?")) {
                    ps.setInt(1, ventaId);
                    ps.executeUpdate();
                }
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    private void validarSucursal(Connection conn, int sucursalId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM sucursales WHERE id_sucursal = ? AND activo = 1")) {
            ps.setInt(1, sucursalId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("Sucursal no existe o esta inactiva");
            }
        }
    }

    private void validarClienteActivo(Connection conn, int clienteId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM clientes WHERE id = ? AND estado = 1")) {
            ps.setInt(1, clienteId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("Cliente no existe o esta inactivo");
            }
        }
    }

    private Producto buscarProductoActivo(Connection conn, int productoId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, nombre, precio, stock, categoria, estado FROM productos WHERE id = ? AND estado = 1")) {
            ps.setInt(1, productoId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapProducto(rs) : null;
            }
        }
    }

    private int insertarCabeceraVenta(Connection conn, int clienteId, int sucursalId,
                                      double subtotal, double igv, double total) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO ventas (cliente_id, id_sucursal, fecha, subtotal, igv, total, estado) "
                    + "VALUES (?, ?, ?, ?, ?, ?, 1)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, clienteId);
            ps.setInt(2, sucursalId);
            ps.setString(3, LocalDateTime.now().format(FECHA_FMT));
            ps.setDouble(4, subtotal);
            ps.setDouble(5, igv);
            ps.setDouble(6, total);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        throw new SQLException("No se genero ID de venta");
    }

    private void insertarDetalleVenta(Connection conn, int ventaId, int productoId, int cantidad,
                                      double precio, double subtotal) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO detalle_ventas (venta_id, producto_id, cantidad, precio_unitario, subtotal, estado) "
                    + "VALUES (?, ?, ?, ?, ?, 1)")) {
            ps.setInt(1, ventaId);
            ps.setInt(2, productoId);
            ps.setInt(3, cantidad);
            ps.setDouble(4, precio);
            ps.setDouble(5, subtotal);
            ps.executeUpdate();
        }
    }

    private void descontarStock(Connection conn, int productoId, int cantidad) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE productos SET stock = stock - ? WHERE id = ? AND stock >= ? AND estado = 1")) {
            ps.setInt(1, cantidad);
            ps.setInt(2, productoId);
            ps.setInt(3, cantidad);
            if (ps.executeUpdate() == 0) throw new SQLException("No se pudo descontar stock de producto " + productoId);
        }
    }

    private void restaurarStock(Connection conn, int productoId, int cantidad) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE productos SET stock = stock + ? WHERE id = ?")) {
            ps.setInt(1, cantidad);
            ps.setInt(2, productoId);
            ps.executeUpdate();
        }
    }

    private Venta buscarVenta(Connection conn, int id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, cliente_id, id_sucursal, fecha, subtotal, igv, total, estado FROM ventas WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapVenta(rs) : null;
            }
        }
    }

    private static Producto mapProducto(ResultSet rs) throws SQLException {
        return new Producto(rs.getInt("id"), rs.getString("nombre"), rs.getDouble("precio"),
            rs.getInt("stock"), rs.getString("categoria"), rs.getInt("estado"));
    }

    private static Cliente mapCliente(ResultSet rs) throws SQLException {
        return new Cliente(rs.getInt("id"), rs.getString("nombre"), rs.getString("dni"),
            rs.getString("telefono"), rs.getString("email"), rs.getInt("estado"));
    }

    private static Sucursal mapSucursal(ResultSet rs) throws SQLException {
        return new Sucursal(rs.getInt("id_sucursal"), rs.getString("nombre"),
            rs.getString("direccion"), rs.getInt("activo"));
    }

    private static Venta mapVenta(ResultSet rs) throws SQLException {
        return new Venta(rs.getInt("id"), rs.getInt("cliente_id"), rs.getInt("id_sucursal"),
            rs.getString("fecha"), rs.getDouble("subtotal"), rs.getDouble("igv"),
            rs.getDouble("total"), rs.getInt("estado"));
    }

    private static DetalleVenta mapDetalle(ResultSet rs) throws SQLException {
        return new DetalleVenta(rs.getInt("id"), rs.getInt("venta_id"), rs.getInt("producto_id"),
            rs.getInt("cantidad"), rs.getDouble("precio_unitario"), rs.getDouble("subtotal"),
            rs.getInt("estado"));
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public Path getDbPath() { return dbPath; }
}

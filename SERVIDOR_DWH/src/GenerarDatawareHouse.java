import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;

public class GenerarDatawareHouse {
    private static final String[] MESES = {
        "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
        "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
    };

    public static void main(String[] args) throws Exception {
        Path source = args.length > 0 ? Path.of(args[0]) : Path.of("SERVIDOR_DATOS", "minimarket.db");
        Path target = args.length > 1 ? Path.of(args[1]) : Path.of("SERVIDOR_DWH", "data", "dwh.db");
        Files.createDirectories(target.toAbsolutePath().getParent());
        Class.forName("org.sqlite.JDBC");

        try (Connection op = DriverManager.getConnection("jdbc:sqlite:" + source);
             Connection dwh = DriverManager.getConnection("jdbc:sqlite:" + target)) {
            crearEsquema(dwh);
            limpiarDwh(dwh);
            cargarDimProducto(op, dwh);
            cargarDimCliente(op, dwh);
            cargarDimTiempo(op, dwh);
            cargarDimSucursal(op, dwh);
            cargarFactVentas(op, dwh);
        }

        System.out.println("[DWH] ETL finalizado: " + target.toAbsolutePath());
    }

    private static void crearEsquema(Connection dwh) throws SQLException {
        String schema = """
            CREATE TABLE IF NOT EXISTS DIM_PRODUCTO (id_producto INTEGER PRIMARY KEY, nombre TEXT, categoria TEXT, precio_unitario REAL);
            CREATE TABLE IF NOT EXISTS DIM_CLIENTE  (id_cliente INTEGER PRIMARY KEY, nombre TEXT, tipo TEXT);
            CREATE TABLE IF NOT EXISTS DIM_TIEMPO   (id_tiempo INTEGER PRIMARY KEY, anio INTEGER, mes INTEGER, trimestre INTEGER, nombre_mes TEXT);
            CREATE TABLE IF NOT EXISTS DIM_SUCURSAL (id_sucursal INTEGER PRIMARY KEY, nombre TEXT, region TEXT);
            CREATE TABLE IF NOT EXISTS FACT_VENTAS  (
                id_fact INTEGER PRIMARY KEY AUTOINCREMENT,
                id_producto INTEGER, id_cliente INTEGER,
                id_tiempo INTEGER,   id_sucursal INTEGER,
                cantidad INTEGER, total_venta REAL, igv REAL
            );
            """;
        try (Statement st = dwh.createStatement()) {
            for (String sql : schema.split(";")) {
                String trimmed = sql.strip();
                if (!trimmed.isEmpty()) st.execute(trimmed);
            }
        }
    }

    private static void limpiarDwh(Connection dwh) throws SQLException {
        try (Statement st = dwh.createStatement()) {
            st.executeUpdate("DELETE FROM FACT_VENTAS");
            st.executeUpdate("DELETE FROM DIM_PRODUCTO");
            st.executeUpdate("DELETE FROM DIM_CLIENTE");
            st.executeUpdate("DELETE FROM DIM_TIEMPO");
            st.executeUpdate("DELETE FROM DIM_SUCURSAL");
        }
    }

    private static void cargarDimProducto(Connection op, Connection dwh) throws SQLException {
        try (Statement st = op.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, nombre, categoria, precio FROM productos WHERE estado = 1");
             PreparedStatement ins = dwh.prepareStatement(
                 "INSERT INTO DIM_PRODUCTO (id_producto, nombre, categoria, precio_unitario) VALUES (?, ?, ?, ?)")) {
            while (rs.next()) {
                ins.setInt(1, rs.getInt("id"));
                ins.setString(2, rs.getString("nombre"));
                ins.setString(3, rs.getString("categoria"));
                ins.setDouble(4, rs.getDouble("precio"));
                ins.executeUpdate();
            }
        }
    }

    private static void cargarDimCliente(Connection op, Connection dwh) throws SQLException {
        try (PreparedStatement ins = dwh.prepareStatement(
                "INSERT INTO DIM_CLIENTE (id_cliente, nombre, tipo) VALUES (?, ?, ?)")) {
            ins.setInt(1, 0);
            ins.setString(2, "Cliente anonimo");
            ins.setString(3, "Anonimo");
            ins.executeUpdate();

            try (Statement st = op.createStatement();
                 ResultSet rs = st.executeQuery("SELECT id, nombre FROM clientes WHERE estado = 1")) {
                while (rs.next()) {
                    ins.setInt(1, rs.getInt("id"));
                    ins.setString(2, rs.getString("nombre"));
                    ins.setString(3, "Registrado");
                    ins.executeUpdate();
                }
            }
        }
    }

    private static void cargarDimTiempo(Connection op, Connection dwh) throws SQLException {
        int currentYear = LocalDate.now().getYear();
        try (PreparedStatement ins = dwh.prepareStatement(
                "INSERT OR IGNORE INTO DIM_TIEMPO (id_tiempo, anio, mes, trimestre, nombre_mes) VALUES (?, ?, ?, ?, ?)")) {
            for (int mes = 1; mes <= 12; mes++) insertarMes(ins, currentYear, mes);

            try (Statement st = op.createStatement();
                 ResultSet rs = st.executeQuery("SELECT DISTINCT CAST(strftime('%Y', fecha) AS INTEGER) anio, CAST(strftime('%m', fecha) AS INTEGER) mes FROM ventas")) {
                while (rs.next()) insertarMes(ins, rs.getInt("anio"), rs.getInt("mes"));
            }
        }
    }

    private static void insertarMes(PreparedStatement ins, int anio, int mes) throws SQLException {
        if (anio <= 0 || mes < 1 || mes > 12) return;
        ins.setInt(1, anio * 100 + mes);
        ins.setInt(2, anio);
        ins.setInt(3, mes);
        ins.setInt(4, ((mes - 1) / 3) + 1);
        ins.setString(5, MESES[mes - 1]);
        ins.executeUpdate();
    }

    private static void cargarDimSucursal(Connection op, Connection dwh) throws SQLException {
        try (Statement st = op.createStatement();
             ResultSet rs = st.executeQuery("SELECT id_sucursal, nombre FROM sucursales WHERE activo = 1");
             PreparedStatement ins = dwh.prepareStatement(
                 "INSERT INTO DIM_SUCURSAL (id_sucursal, nombre, region) VALUES (?, ?, ?)")) {
            while (rs.next()) {
                ins.setInt(1, rs.getInt("id_sucursal"));
                ins.setString(2, rs.getString("nombre"));
                ins.setString(3, rs.getString("nombre"));
                ins.executeUpdate();
            }
        }
    }

    private static void cargarFactVentas(Connection op, Connection dwh) throws SQLException {
        String sql = """
            SELECT d.producto_id,
                   v.cliente_id,
                   v.id_sucursal,
                   CAST(strftime('%Y', v.fecha) AS INTEGER) * 100 + CAST(strftime('%m', v.fecha) AS INTEGER) AS id_tiempo,
                   d.cantidad,
                   ROUND(d.subtotal * 1.18, 2) AS total_venta,
                   ROUND(d.subtotal * 0.18, 2) AS igv
            FROM detalle_ventas d
            JOIN ventas v ON v.id = d.venta_id
            WHERE v.estado = 1 AND d.estado = 1
            """;
        try (Statement st = op.createStatement();
             ResultSet rs = st.executeQuery(sql);
             PreparedStatement ins = dwh.prepareStatement(
                 "INSERT INTO FACT_VENTAS (id_producto, id_cliente, id_tiempo, id_sucursal, cantidad, total_venta, igv) "
                     + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            while (rs.next()) {
                ins.setInt(1, rs.getInt("producto_id"));
                ins.setInt(2, rs.getInt("cliente_id"));
                ins.setInt(3, rs.getInt("id_tiempo"));
                ins.setInt(4, rs.getInt("id_sucursal"));
                ins.setInt(5, rs.getInt("cantidad"));
                ins.setDouble(6, rs.getDouble("total_venta"));
                ins.setDouble(7, rs.getDouble("igv"));
                ins.executeUpdate();
            }
        }
    }
}

package com.minimarket.server.database;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.*;
import java.sql.*;
import java.util.logging.Logger;

/**
 * Gestor de la base de datos SQLite del servidor central.
 *
 * Mantiene el esquema de tablas consolidado para todas las sucursales:
 *   sucursales, productos, clientes, ventas, detalle_ventas, sync_log
 *
 * Equivalente Java de SERVIDOR/update.py (funciones de BD) y init_db.sql.
 */
public class DatabaseManager {

    private static final Logger LOG = Logger.getLogger(DatabaseManager.class.getName());

    // Esquema de la base de datos (equivalente a init_db.sql)
    private static final String SCHEMA = """
        PRAGMA foreign_keys = ON;
        PRAGMA journal_mode = WAL;

        CREATE TABLE IF NOT EXISTS sucursales (
            id           INTEGER PRIMARY KEY AUTOINCREMENT,
            hostname     TEXT    NOT NULL UNIQUE,
            nombre       TEXT    NOT NULL,
            ultima_sync  TEXT
        );

        CREATE TABLE IF NOT EXISTS productos (
            id           INTEGER NOT NULL,
            sucursal_id  INTEGER NOT NULL,
            nombre       TEXT    NOT NULL,
            precio       REAL    NOT NULL CHECK(precio >= 0),
            stock        INTEGER NOT NULL CHECK(stock >= 0),
            categoria    TEXT    DEFAULT '',
            estado       INTEGER DEFAULT 1,
            PRIMARY KEY (id, sucursal_id),
            FOREIGN KEY (sucursal_id) REFERENCES sucursales(id)
        );

        CREATE TABLE IF NOT EXISTS clientes (
            id           INTEGER NOT NULL,
            sucursal_id  INTEGER NOT NULL,
            nombre       TEXT    NOT NULL,
            dni          TEXT    NOT NULL,
            telefono     TEXT    DEFAULT '',
            email        TEXT    DEFAULT '',
            estado       INTEGER DEFAULT 1,
            PRIMARY KEY (id, sucursal_id),
            FOREIGN KEY (sucursal_id) REFERENCES sucursales(id)
        );

        CREATE TABLE IF NOT EXISTS ventas (
            id           INTEGER NOT NULL,
            sucursal_id  INTEGER NOT NULL,
            cliente_id   INTEGER DEFAULT 0,
            fecha        TEXT    NOT NULL,
            subtotal     REAL    NOT NULL CHECK(subtotal >= 0),
            igv          REAL    NOT NULL CHECK(igv >= 0),
            total        REAL    NOT NULL CHECK(total >= 0),
            estado       INTEGER DEFAULT 1,
            PRIMARY KEY (id, sucursal_id),
            FOREIGN KEY (sucursal_id) REFERENCES sucursales(id)
        );

        CREATE TABLE IF NOT EXISTS detalle_ventas (
            id               INTEGER NOT NULL,
            sucursal_id      INTEGER NOT NULL,
            venta_id         INTEGER NOT NULL,
            producto_id      INTEGER NOT NULL,
            cantidad         INTEGER NOT NULL CHECK(cantidad > 0),
            precio_unitario  REAL    NOT NULL CHECK(precio_unitario >= 0),
            subtotal         REAL    NOT NULL CHECK(subtotal >= 0),
            estado           INTEGER DEFAULT 1,
            PRIMARY KEY (id, sucursal_id),
            FOREIGN KEY (sucursal_id) REFERENCES sucursales(id)
        );

        CREATE TABLE IF NOT EXISTS sync_log (
            id              INTEGER PRIMARY KEY AUTOINCREMENT,
            sucursal_id     INTEGER NOT NULL,
            archivo         TEXT    NOT NULL,
            entidad         TEXT    NOT NULL,
            registros_proc  INTEGER DEFAULT 0,
            registros_dup   INTEGER DEFAULT 0,
            registros_err   INTEGER DEFAULT 0,
            timestamp       TEXT    DEFAULT (datetime('now', 'localtime')),
            FOREIGN KEY (sucursal_id) REFERENCES sucursales(id)
        );

        CREATE VIEW IF NOT EXISTS v_ventas_resumen AS
            SELECT s.nombre AS sucursal,
                   date(v.fecha) AS fecha,
                   COUNT(*) AS total_ventas,
                   ROUND(SUM(v.total), 2) AS ingresos_totales
            FROM ventas v
            JOIN sucursales s ON v.sucursal_id = s.id
            WHERE v.estado = 1
            GROUP BY s.nombre, date(v.fecha)
            ORDER BY fecha DESC;

        CREATE VIEW IF NOT EXISTS v_productos_mas_vendidos AS
            SELECT p.nombre AS producto,
                   SUM(dv.cantidad) AS total_vendido,
                   ROUND(SUM(dv.subtotal), 2) AS ingresos
            FROM detalle_ventas dv
            JOIN productos p ON dv.producto_id = p.id AND dv.sucursal_id = p.sucursal_id
            WHERE dv.estado = 1
            GROUP BY p.nombre
            ORDER BY total_vendido DESC
            LIMIT 10;
        """;

    private final Path dbPath;

    public DatabaseManager(Path dbPath) {
        this.dbPath = dbPath;
        try {
            Files.createDirectories(dbPath.getParent());
            Class.forName("org.sqlite.JDBC");
        } catch (Exception e) {
            LOG.severe("No se pudo inicializar el driver SQLite: " + e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // CONEXION
    // ════════════════════════════════════════════════════════════════════════

    public Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toString());
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
            st.execute("PRAGMA journal_mode = WAL");
        }
        return conn;
    }

    /** Inicializa el esquema de la BD ejecutando el SQL de creacion de tablas. */
    public void initDatabase() {
        try (Connection conn = getConnection();
             Statement st = conn.createStatement()) {
            // executeBatch de sentencias separadas por ;
            for (String sql : SCHEMA.split(";")) {
                String trimmed = sql.strip();
                if (!trimmed.isEmpty()) {
                    try { st.execute(trimmed); }
                    catch (SQLException e) {
                        // Las CREATE IF NOT EXISTS pueden lanzar errores en vistas repetidas
                        if (!e.getMessage().contains("already exists")) {
                            LOG.warning("SQL warning: " + e.getMessage());
                        }
                    }
                }
            }
            LOG.info("Base de datos inicializada: " + dbPath);
        } catch (SQLException e) {
            LOG.severe("Error inicializando BD: " + e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // SUCURSALES
    // ════════════════════════════════════════════════════════════════════════

    /** Retorna el ID de la sucursal, creandola si no existe. */
    public int getOrCreateSucursal(Connection conn, String hostname) throws SQLException {
        PreparedStatement sel = conn.prepareStatement(
            "SELECT id FROM sucursales WHERE hostname = ?");
        sel.setString(1, hostname);
        ResultSet rs = sel.executeQuery();
        if (rs.next()) return rs.getInt("id");

        PreparedStatement ins = conn.prepareStatement(
            "INSERT INTO sucursales (hostname, nombre) VALUES (?, ?)");
        ins.setString(1, hostname);
        ins.setString(2, "Sucursal " + hostname);
        ins.executeUpdate();
        ResultSet gen = ins.getGeneratedKeys();
        return gen.next() ? gen.getInt(1) : 1;
    }

    // ════════════════════════════════════════════════════════════════════════
    // UPSERT GENERICO
    // ════════════════════════════════════════════════════════════════════════

    /**
     * INSERT OR REPLACE generico para cualquier tabla.
     * @return "inserted" o "updated"
     */
    public String upsert(Connection conn, String table, String[] pkCols,
                         String[] cols, Object[] values) throws SQLException {
        // Verificar si existe
        StringBuilder whereQ = new StringBuilder();
        for (int i = 0; i < pkCols.length; i++) {
            if (i > 0) whereQ.append(" AND ");
            whereQ.append(pkCols[i]).append(" = ?");
        }
        PreparedStatement check = conn.prepareStatement(
            "SELECT 1 FROM " + table + " WHERE " + whereQ);
        for (int i = 0; i < pkCols.length; i++) {
            for (int j = 0; j < cols.length; j++) {
                if (cols[j].equals(pkCols[i])) {
                    check.setObject(i + 1, values[j]);
                    break;
                }
            }
        }
        boolean exists = check.executeQuery().next();

        // INSERT OR REPLACE
        StringBuilder colsQ  = new StringBuilder();
        StringBuilder marksQ = new StringBuilder();
        for (int i = 0; i < cols.length; i++) {
            if (i > 0) { colsQ.append(", "); marksQ.append(", "); }
            colsQ.append(cols[i]);
            marksQ.append("?");
        }
        PreparedStatement ins = conn.prepareStatement(
            "INSERT OR REPLACE INTO " + table + " (" + colsQ + ") VALUES (" + marksQ + ")");
        for (int i = 0; i < values.length; i++) {
            ins.setObject(i + 1, values[i]);
        }
        ins.executeUpdate();
        return exists ? "updated" : "inserted";
    }

    /** Registra un ciclo de sincronizacion en sync_log. */
    public void logSync(Connection conn, int sucursalId, String archivo,
                        String entidad, int insertados, int actualizados, int errores)
            throws SQLException {
        PreparedStatement st = conn.prepareStatement(
            "INSERT INTO sync_log (sucursal_id, archivo, entidad, registros_proc, registros_dup, registros_err)"
            + " VALUES (?, ?, ?, ?, ?, ?)");
        st.setInt(1, sucursalId); st.setString(2, archivo); st.setString(3, entidad);
        st.setInt(4, insertados); st.setInt(5, actualizados); st.setInt(6, errores);
        st.executeUpdate();
    }

    /** Actualiza timestamp de ultima sincronizacion de la sucursal. */
    public void updateLastSync(Connection conn, int sucursalId) throws SQLException {
        PreparedStatement st = conn.prepareStatement(
            "UPDATE sucursales SET ultima_sync = datetime('now','localtime') WHERE id = ?");
        st.setInt(1, sucursalId);
        st.executeUpdate();
    }

    public Path getDbPath() { return dbPath; }
}

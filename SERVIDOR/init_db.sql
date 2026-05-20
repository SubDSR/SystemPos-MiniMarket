-- ============================================================
-- MiniMarket POS — Inicialización de Base de Datos SQLite
-- Servidor Central
-- ============================================================
-- Uso:
--   sqlite3 database/minimarket.db < init_db.sql
--   (solo en el servidor, NUNCA en el cliente POS)
--
-- Estructura:
--   sucursales    → registro de clientes POS conectados
--   productos     → catalogo consolidado de todas las sucursales
--   clientes      → cartera de clientes por sucursal
--   ventas        → cabeceras de ventas (con IGV 18%)
--   detalle_ventas → lineas de detalle por venta
--   sync_log      → auditoría de sincronizaciones recibidas
--
-- Clave primaria compuesta (id, sucursal_id) para soportar
-- múltiples sucursales con IDs independientes.
-- ============================================================

PRAGMA foreign_keys = ON;
PRAGMA journal_mode = WAL;
PRAGMA synchronous  = NORMAL;

-- ── Sucursales ───────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS sucursales (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    hostname    TEXT    NOT NULL UNIQUE,
    nombre      TEXT    NOT NULL,
    ultima_sync TEXT
);

-- ── Productos ────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS productos (
    id          INTEGER NOT NULL,
    sucursal_id INTEGER NOT NULL,
    nombre      TEXT    NOT NULL,
    precio      REAL    NOT NULL CHECK (precio >= 0),
    stock       INTEGER NOT NULL CHECK (stock >= 0),
    categoria   TEXT    DEFAULT '',
    estado      INTEGER DEFAULT 1,
    PRIMARY KEY (id, sucursal_id),
    FOREIGN KEY (sucursal_id) REFERENCES sucursales(id)
);

-- ── Clientes ─────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS clientes (
    id          INTEGER NOT NULL,
    sucursal_id INTEGER NOT NULL,
    nombre      TEXT    NOT NULL,
    dni         TEXT    NOT NULL,
    telefono    TEXT    DEFAULT '',
    email       TEXT    DEFAULT '',
    estado      INTEGER DEFAULT 1,
    PRIMARY KEY (id, sucursal_id),
    FOREIGN KEY (sucursal_id) REFERENCES sucursales(id)
);

-- ── Ventas ───────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ventas (
    id          INTEGER NOT NULL,
    sucursal_id INTEGER NOT NULL,
    cliente_id  INTEGER DEFAULT 0,
    fecha       TEXT    NOT NULL,
    subtotal    REAL    NOT NULL CHECK (subtotal >= 0),
    igv         REAL    NOT NULL CHECK (igv >= 0),
    total       REAL    NOT NULL CHECK (total >= 0),
    estado      INTEGER DEFAULT 1,
    PRIMARY KEY (id, sucursal_id),
    FOREIGN KEY (sucursal_id) REFERENCES sucursales(id)
);

-- ── Detalle de Ventas ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS detalle_ventas (
    id              INTEGER NOT NULL,
    sucursal_id     INTEGER NOT NULL,
    venta_id        INTEGER NOT NULL,
    producto_id     INTEGER NOT NULL,
    cantidad        INTEGER NOT NULL CHECK (cantidad > 0),
    precio_unitario REAL    NOT NULL CHECK (precio_unitario >= 0),
    subtotal        REAL    NOT NULL CHECK (subtotal >= 0),
    estado          INTEGER DEFAULT 1,
    PRIMARY KEY (id, sucursal_id),
    FOREIGN KEY (sucursal_id) REFERENCES sucursales(id)
);

-- ── Log de Sincronización ────────────────────────────────────
CREATE TABLE IF NOT EXISTS sync_log (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    sucursal_id    INTEGER NOT NULL,
    archivo        TEXT    NOT NULL,
    entidad        TEXT    NOT NULL,
    registros_proc INTEGER DEFAULT 0,
    registros_dup  INTEGER DEFAULT 0,
    registros_err  INTEGER DEFAULT 0,
    timestamp      TEXT    DEFAULT (datetime('now', 'localtime')),
    FOREIGN KEY (sucursal_id) REFERENCES sucursales(id)
);

-- ── Vistas analíticas ────────────────────────────────────────

-- Resumen de ventas por sucursal y día
CREATE VIEW IF NOT EXISTS v_ventas_resumen AS
    SELECT
        s.nombre                   AS sucursal,
        date(v.fecha)              AS fecha,
        COUNT(*)                   AS total_ventas,
        ROUND(SUM(v.subtotal), 2)  AS subtotal_total,
        ROUND(SUM(v.igv),      2)  AS igv_total,
        ROUND(SUM(v.total),    2)  AS ingresos_totales
    FROM ventas v
    JOIN sucursales s ON v.sucursal_id = s.id
    WHERE v.estado = 1
    GROUP BY s.nombre, date(v.fecha)
    ORDER BY fecha DESC, ingresos_totales DESC;

-- Productos más vendidos (top 10 global)
CREATE VIEW IF NOT EXISTS v_productos_mas_vendidos AS
    SELECT
        p.nombre                      AS producto,
        p.categoria                   AS categoria,
        SUM(dv.cantidad)              AS total_vendido,
        ROUND(SUM(dv.subtotal), 2)    AS ingresos,
        ROUND(AVG(dv.precio_unitario), 2) AS precio_promedio
    FROM detalle_ventas dv
    JOIN productos p ON dv.producto_id = p.id AND dv.sucursal_id = p.sucursal_id
    WHERE dv.estado = 1
    GROUP BY p.nombre, p.categoria
    ORDER BY total_vendido DESC
    LIMIT 10;

-- Estado de sincronización por sucursal
CREATE VIEW IF NOT EXISTS v_sync_status AS
    SELECT
        s.nombre                    AS sucursal,
        s.hostname                  AS hostname,
        s.ultima_sync               AS ultima_sincronizacion,
        COUNT(sl.id)                AS total_syncs,
        SUM(sl.registros_proc)      AS registros_procesados,
        SUM(sl.registros_err)       AS errores_totales
    FROM sucursales s
    LEFT JOIN sync_log sl ON s.id = sl.sucursal_id
    GROUP BY s.id, s.nombre, s.hostname, s.ultima_sync
    ORDER BY s.ultima_sync DESC;

-- ── Datos iniciales de demostración ─────────────────────────
-- Sucursal por defecto (localhost para desarrollo)
INSERT OR IGNORE INTO sucursales (hostname, nombre, ultima_sync)
VALUES ('localhost', 'Sucursal Principal', NULL);

-- Índices para optimización de consultas
CREATE INDEX IF NOT EXISTS idx_ventas_fecha
    ON ventas (fecha, sucursal_id);

CREATE INDEX IF NOT EXISTS idx_detalle_venta_id
    ON detalle_ventas (venta_id, sucursal_id);

CREATE INDEX IF NOT EXISTS idx_productos_nombre
    ON productos (nombre, sucursal_id);

CREATE INDEX IF NOT EXISTS idx_clientes_dni
    ON clientes (dni, sucursal_id);

CREATE INDEX IF NOT EXISTS idx_sync_log_ts
    ON sync_log (timestamp DESC);

-- ============================================================
-- Verificacion de estructura creada:
--
--   SELECT name, type FROM sqlite_master
--   WHERE type IN ('table','view','index')
--   ORDER BY type, name;
-- ============================================================

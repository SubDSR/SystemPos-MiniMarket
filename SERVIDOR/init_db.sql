-- ═══════════════════════════════════════════════════════════════════════════
-- init_db.sql — Esquema de la Base de Datos Consolidada del Servidor
-- Sistema POS MiniMarket — Arquitectura Monolítica
--
-- Este script crea las tablas del servidor central (SQLite).
-- Solo el servidor tiene acceso a esta base de datos.
-- El cliente POS trabaja exclusivamente con archivos binarios (.dat/.idx).
-- ═══════════════════════════════════════════════════════════════════════════

PRAGMA journal_mode = WAL;
PRAGMA foreign_keys = ON;

-- ─────────────────────────────────────────────────────────────────────────────
-- Tabla de sucursales/clientes POS que sincronizan datos
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS sucursales (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    hostname    TEXT    NOT NULL UNIQUE,
    nombre      TEXT    NOT NULL,
    ultima_sync DATETIME,
    activo      INTEGER NOT NULL DEFAULT 1,
    creado_en   DATETIME NOT NULL DEFAULT (datetime('now','localtime'))
);

-- ─────────────────────────────────────────────────────────────────────────────
-- Tabla de productos consolidados
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS productos (
    id            INTEGER NOT NULL,
    sucursal_id   INTEGER NOT NULL,
    nombre        TEXT    NOT NULL,
    precio        REAL    NOT NULL CHECK (precio >= 0),
    stock         INTEGER NOT NULL CHECK (stock >= 0),
    categoria     TEXT    NOT NULL DEFAULT '',
    estado        INTEGER NOT NULL DEFAULT 1 CHECK (estado IN (0, 1)),
    sincronizado  DATETIME NOT NULL DEFAULT (datetime('now','localtime')),
    PRIMARY KEY (id, sucursal_id),
    FOREIGN KEY (sucursal_id) REFERENCES sucursales(id)
);

CREATE INDEX IF NOT EXISTS idx_productos_nombre
    ON productos (nombre);
CREATE INDEX IF NOT EXISTS idx_productos_categoria
    ON productos (categoria);

-- ─────────────────────────────────────────────────────────────────────────────
-- Tabla de clientes consolidados
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS clientes (
    id            INTEGER NOT NULL,
    sucursal_id   INTEGER NOT NULL,
    nombre        TEXT    NOT NULL,
    dni           TEXT    NOT NULL,
    telefono      TEXT    NOT NULL DEFAULT '',
    email         TEXT    NOT NULL DEFAULT '',
    estado        INTEGER NOT NULL DEFAULT 1 CHECK (estado IN (0, 1)),
    sincronizado  DATETIME NOT NULL DEFAULT (datetime('now','localtime')),
    PRIMARY KEY (id, sucursal_id),
    FOREIGN KEY (sucursal_id) REFERENCES sucursales(id)
);

CREATE INDEX IF NOT EXISTS idx_clientes_dni
    ON clientes (dni);

-- ─────────────────────────────────────────────────────────────────────────────
-- Tabla de ventas consolidadas
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ventas (
    id            INTEGER NOT NULL,
    sucursal_id   INTEGER NOT NULL,
    cliente_id    INTEGER NOT NULL DEFAULT 0,
    fecha         TEXT    NOT NULL,
    subtotal      REAL    NOT NULL CHECK (subtotal >= 0),
    igv           REAL    NOT NULL CHECK (igv >= 0),
    total         REAL    NOT NULL CHECK (total >= 0),
    estado        INTEGER NOT NULL DEFAULT 1 CHECK (estado IN (0, 1)),
    sincronizado  DATETIME NOT NULL DEFAULT (datetime('now','localtime')),
    PRIMARY KEY (id, sucursal_id),
    FOREIGN KEY (sucursal_id) REFERENCES sucursales(id)
);

CREATE INDEX IF NOT EXISTS idx_ventas_fecha
    ON ventas (fecha);
CREATE INDEX IF NOT EXISTS idx_ventas_sucursal
    ON ventas (sucursal_id);

-- ─────────────────────────────────────────────────────────────────────────────
-- Tabla de detalle de ventas consolidados
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS detalle_ventas (
    id              INTEGER NOT NULL,
    sucursal_id     INTEGER NOT NULL,
    venta_id        INTEGER NOT NULL,
    producto_id     INTEGER NOT NULL,
    cantidad        INTEGER NOT NULL CHECK (cantidad > 0),
    precio_unitario REAL    NOT NULL CHECK (precio_unitario >= 0),
    subtotal        REAL    NOT NULL CHECK (subtotal >= 0),
    estado          INTEGER NOT NULL DEFAULT 1,
    sincronizado    DATETIME NOT NULL DEFAULT (datetime('now','localtime')),
    PRIMARY KEY (id, sucursal_id),
    FOREIGN KEY (sucursal_id) REFERENCES sucursales(id)
);

CREATE INDEX IF NOT EXISTS idx_detalle_venta
    ON detalle_ventas (venta_id, sucursal_id);

-- ─────────────────────────────────────────────────────────────────────────────
-- Tabla de log de sincronizaciones recibidas
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS sync_log (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    sucursal_id     INTEGER,
    archivo         TEXT    NOT NULL,
    entidad         TEXT    NOT NULL,
    registros_proc  INTEGER NOT NULL DEFAULT 0,
    registros_dup   INTEGER NOT NULL DEFAULT 0,
    registros_err   INTEGER NOT NULL DEFAULT 0,
    resultado       TEXT    NOT NULL DEFAULT 'OK',
    procesado_en    DATETIME NOT NULL DEFAULT (datetime('now','localtime'))
);

-- ─────────────────────────────────────────────────────────────────────────────
-- Vistas útiles para reportes
-- ─────────────────────────────────────────────────────────────────────────────
CREATE VIEW IF NOT EXISTS v_ventas_resumen AS
    SELECT
        v.sucursal_id,
        s.nombre                  AS sucursal,
        date(v.fecha)             AS fecha,
        COUNT(*)                  AS num_ventas,
        SUM(v.subtotal)           AS total_subtotal,
        SUM(v.igv)                AS total_igv,
        SUM(v.total)              AS total_general
    FROM ventas v
    JOIN sucursales s ON s.id = v.sucursal_id
    WHERE v.estado = 1
    GROUP BY v.sucursal_id, date(v.fecha)
    ORDER BY fecha DESC;

CREATE VIEW IF NOT EXISTS v_productos_mas_vendidos AS
    SELECT
        dv.producto_id,
        p.nombre,
        SUM(dv.cantidad)     AS total_vendido,
        SUM(dv.subtotal)     AS total_facturado
    FROM detalle_ventas dv
    JOIN productos p
        ON p.id = dv.producto_id AND p.sucursal_id = dv.sucursal_id
    WHERE dv.estado = 1
    GROUP BY dv.producto_id
    ORDER BY total_vendido DESC;

-- ─────────────────────────────────────────────────────────────────────────────
-- Sucursal por defecto (localhost)
-- ─────────────────────────────────────────────────────────────────────────────
INSERT OR IGNORE INTO sucursales (hostname, nombre)
    VALUES ('localhost', 'Sucursal Principal');

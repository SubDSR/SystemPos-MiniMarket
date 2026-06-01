PRAGMA foreign_keys = ON;
PRAGMA journal_mode = WAL;

CREATE TABLE IF NOT EXISTS sucursales (
    id_sucursal INTEGER PRIMARY KEY AUTOINCREMENT,
    nombre      TEXT NOT NULL,
    direccion   TEXT,
    activo      INTEGER NOT NULL DEFAULT 1
);

INSERT OR IGNORE INTO sucursales (id_sucursal, nombre, direccion) VALUES (1, 'Lima', 'Av. Principal 123');
INSERT OR IGNORE INTO sucursales (id_sucursal, nombre, direccion) VALUES (2, 'Arequipa', 'Calle Real 456');
INSERT OR IGNORE INTO sucursales (id_sucursal, nombre, direccion) VALUES (3, 'Trujillo', 'Jr. Libertad 789');

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
    subtotal    REAL,
    igv         REAL,
    total       REAL,
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

-- Migracion para bases antiguas. Si la columna ya existe, SQLite reportara error y debe ignorarse.
ALTER TABLE ventas ADD COLUMN id_sucursal INTEGER NOT NULL DEFAULT 1;

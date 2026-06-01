CREATE TABLE IF NOT EXISTS DIM_PRODUCTO (
    id_producto INTEGER PRIMARY KEY,
    nombre TEXT,
    categoria TEXT,
    precio_unitario REAL
);

CREATE TABLE IF NOT EXISTS DIM_CLIENTE (
    id_cliente INTEGER PRIMARY KEY,
    nombre TEXT,
    tipo TEXT
);

CREATE TABLE IF NOT EXISTS DIM_TIEMPO (
    id_tiempo INTEGER PRIMARY KEY,
    anio INTEGER,
    mes INTEGER,
    trimestre INTEGER,
    nombre_mes TEXT
);

CREATE TABLE IF NOT EXISTS DIM_SUCURSAL (
    id_sucursal INTEGER PRIMARY KEY,
    nombre TEXT,
    region TEXT
);

CREATE TABLE IF NOT EXISTS FACT_VENTAS (
    id_fact INTEGER PRIMARY KEY AUTOINCREMENT,
    id_producto INTEGER,
    id_cliente INTEGER,
    id_tiempo INTEGER,
    id_sucursal INTEGER,
    cantidad INTEGER,
    total_venta REAL,
    igv REAL
);

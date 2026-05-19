# Sistema POS MiniMarket — Arquitectura Monolítica con Acceso Directo

> **Curso:** Arquitectura de Software  
> **Entregable:** 01 — Arquitectura Unitaria / Monolítica  
> **Empresa simulada:** MiniMarket El Ahorro S.A.C.

---

## 1. Descripción General

Sistema de Punto de Venta (POS) para un minimarket que demuestra los principios
de la **arquitectura monolítica** aplicada a un contexto de sucursales distribuidas:

- Cada sucursal trabaja **localmente y de forma autónoma**.
- Los datos se persisten en **archivos binarios de acceso directo** (`.dat` + `.idx`).
- La sincronización con el servidor central se realiza mediante **copia de archivos
  a una carpeta compartida en red** (ruta UNC: `\\HOSTNAME\DATOS`).
- El servidor consolida los datos en una **base de datos SQLite** centralizada.

---

## 2. Arquitectura del Sistema

```
┌──────────────────────────────────────────────────────────────────────┐
│              ARQUITECTURA MONOLÍTICA — VISTA GENERAL                 │
├─────────────────────────┬────────────────────────────────────────────┤
│   CLIENTE POS           │   SERVIDOR CENTRAL                         │
│   (APPLICATION/)        │   (SERVIDOR/)                              │
│                         │                                            │
│  ┌─────────────────┐    │    ┌──────────────────────┐               │
│  │  Interfaz GUI   │    │    │  server.py (GUI)      │               │
│  │  (Tkinter)      │    │    │  - Dashboard          │               │
│  └────────┬────────┘    │    │  - Consultas SQL      │               │
│           │             │    │  - Log de syncs        │               │
│  ┌────────▼────────┐    │    └──────────┬───────────┘               │
│  │  Servicios      │    │               │                            │
│  │  (CRUD)         │    │    ┌──────────▼───────────┐               │
│  └────────┬────────┘    │    │  update.py            │               │
│           │             │    │  - Monitor DATOS/     │               │
│  ┌────────▼────────┐    │    │  - Lee CSV            │               │
│  │  FileManager    │◄───┼────│  - Inserta en SQLite  │               │
│  │  (struct+seek)  │    │    └──────────────────────┘               │
│  └────────┬────────┘    │                                            │
│           │             │         ┌─────────────────┐               │
│  ┌────────▼────────┐    │         │  minimarket.db   │               │
│  │  DATA/          │    │         │  (SQLite)        │               │
│  │  *.dat  *.idx   │    │         │  productos       │               │
│  │  *.holes        │    │         │  clientes        │               │
│  └────────┬────────┘    │         │  ventas          │               │
│           │             │         │  detalle_ventas  │               │
│  ┌────────▼────────┐    │         └─────────────────┘               │
│  │  send.py        │    │                   ▲                        │
│  │  Export→CSV     │    │                   │                        │
│  │  Copiar→UNC     │────┼─────────────────┘                         │
│  └─────────────────┘    │                                            │
│                         │   DATOS/ (\\HOSTNAME\DATOS)                │
│         ▼               │   carpeta compartida en red                │
│   exports/*.csv ────────┼──────────────────────────────────►         │
└─────────────────────────┴────────────────────────────────────────────┘
```

---

## 3. Estructura de Archivos

```
SystemPos-MiniMarket/
│
├── APPLICATION/                  ← Aplicación Cliente POS
│   ├── models/
│   │   ├── producto.py           ← Registro binario 97 bytes
│   │   ├── cliente.py            ← Registro binario 131 bytes
│   │   ├── venta.py              ← Registro binario 52 bytes
│   │   └── detalle_venta.py      ← Registro binario 33 bytes
│   ├── services/
│   │   ├── index_manager.py      ← Gestión de archivos .idx y .holes
│   │   ├── file_manager.py       ← Acceso directo con struct + seek()
│   │   ├── producto_service.py   ← CRUD productos
│   │   ├── cliente_service.py    ← CRUD clientes
│   │   └── venta_service.py      ← Gestión de ventas
│   ├── views/
│   │   ├── main_window.py        ← Ventana principal + sidebar
│   │   ├── dashboard_view.py     ← KPIs en tiempo real
│   │   ├── productos_view.py     ← CRUD productos (Treeview + formulario)
│   │   ├── clientes_view.py      ← CRUD clientes
│   │   ├── ventas_view.py        ← Pantalla de nueva venta (POS)
│   │   ├── historial_view.py     ← Historial y anulación de ventas
│   │   └── sync_dialog.py        ← Diálogo de sincronización
│   ├── utils/
│   │   ├── config.py             ← Rutas, colores, constantes
│   │   └── logger.py             ← Logging diario a archivo
│   ├── indexes/                  ← (vacío, reservado)
│   ├── logs/                     ← Logs diarios pos_YYYY-MM-DD.log
│   ├── exports/                  ← CSV exportados para sincronización
│   ├── main.py                   ← Punto de entrada del POS
│   ├── send.py                   ← Agente de sincronización → Send.exe
│   └── requirements.txt
│
├── DATA/                         ← Archivos binarios del cliente
│   ├── productos.dat             ← Registros de productos (97 B/reg)
│   ├── productos.idx             ← Índice id→offset (12 B/entrada)
│   ├── productos.holes           ← Slots libres para reutilizar
│   ├── clientes.dat              ← Registros de clientes (131 B/reg)
│   ├── clientes.idx
│   ├── clientes.holes
│   ├── ventas.dat                ← Registros de ventas (52 B/reg)
│   ├── ventas.idx
│   ├── ventas.holes
│   ├── detalles.dat              ← Detalles de ventas (33 B/reg)
│   ├── detalles.idx
│   └── detalles.holes
│
├── DATOS/                        ← Carpeta compartida en red (UNC)
│   └── (archivos CSV enviados por send.py)
│
├── SERVIDOR/                     ← Aplicación Servidor
│   ├── database/
│   │   └── minimarket.db         ← SQLite consolidado
│   ├── logs/
│   ├── init_db.sql               ← Esquema SQL del servidor
│   ├── update.py                 ← Monitor + procesador → Update.exe
│   ├── server.py                 ← GUI del servidor
│   └── requirements.txt
│
├── build.py                      ← Generador de .EXE con PyInstaller
└── README.md
```

---

## 4. Formato de Archivos Binarios (.dat)

### 4.1 Concepto de Registro de Longitud Fija

Todos los archivos `.dat` almacenan **registros de tamaño exactamente fijo**.
Esto permite calcular la posición de cualquier registro sin escaneo secuencial:

```
Archivo productos.dat:
┌────────────────────────────────────────── offset=0
│  Registro ID=1  (97 bytes)
│  [id:4][nombre:50][precio:8][stock:4][cat:30][estado:1]
├────────────────────────────────────────── offset=97
│  Registro ID=2  (97 bytes)
│  [id:4][nombre:50][precio:8][stock:4][cat:30][estado:1]
├────────────────────────────────────────── offset=194
│  Registro ID=3  (ELIMINADO lógicamente, estado=0)
│  [id:4][nombre:50][precio:8][stock:4][cat:30][estado:1]
└────────────────────────────────────────── offset=291
```

### 4.2 Tamaños de Registro

| Entidad       | Formato struct        | Tamaño (bytes) |
|---------------|-----------------------|----------------|
| Producto      | `!I50sdI30sB`         | **97 bytes**   |
| Cliente       | `!I50s11s15s50sB`     | **131 bytes**  |
| Venta         | `!II19sdddB`          | **52 bytes**   |
| DetalleVenta  | `!IIIIddB`            | **33 bytes**   |

### 4.3 Desglose del Registro de Producto (97 bytes)

```
Byte 0-3:    id           (unsigned int, 4 bytes, big-endian)
Byte 4-53:   nombre       (char[50], rellenado con \x00)
Byte 54-61:  precio       (double IEEE 754, 8 bytes)
Byte 62-65:  stock        (unsigned int, 4 bytes)
Byte 66-95:  categoria    (char[30], rellenado con \x00)
Byte 96:     estado       (unsigned char: 1=activo, 0=eliminado)
```

**Empaquetado Python:**
```python
struct.pack("!I50sdI30sB",
    1,                                    # id
    b"Agua Mineral\x00\x00...",           # nombre (50 bytes)
    1.50,                                 # precio
    100,                                  # stock
    b"Bebidas\x00\x00...",                # categoria (30 bytes)
    1                                     # estado=activo
)
```

---

## 5. Formato de Archivos de Índice (.idx)

### 5.1 Estructura del Archivo .idx

```
productos.idx:
┌──────────────────────────────────────────────────────────┐
│  n_entradas = 3  (4 bytes, unsigned int, big-endian)     │
├─────────────────────────────────┬────────────────────────┤
│  key = 1  (4 bytes)             │  offset = 0   (8 bytes)│
│  key = 2  (4 bytes)             │  offset = 97  (8 bytes)│
│  key = 4  (4 bytes)             │  offset = 291 (8 bytes)│
└─────────────────────────────────┴────────────────────────┘
Tamaño por entrada: 12 bytes (I=4 + Q=8)
Nota: ID=3 fue eliminado; su offset 194 está en productos.holes
```

### 5.2 Archivo .holes (Espacios Libres)

```
productos.holes:
┌────────────────────────────────┐
│  n_holes = 1  (4 bytes)        │
├────────────────────────────────┤
│  offset = 194  (8 bytes)       │  ← espacio libre del ID=3 eliminado
└────────────────────────────────┘
```

### 5.3 Algoritmo de Búsqueda Directa (O(1))

```python
# Buscar producto con id=2
offset = index[2]          # 97   → O(1) lookup en dict
file.seek(97)              # posicionarse en byte 97
data = file.read(97)       # leer exactamente 97 bytes
producto = Producto.unpack(data)   # deserializar
```

### 5.4 Algoritmo de Inserción con Reutilización

```python
# Insertar nuevo producto
free_offset = holes.pop(0)   # reutilizar offset=194 (ID=3 eliminado)
file.seek(194)
file.write(nuevo_producto.pack())
index[nuevo_id] = 194        # actualizar índice
```

---

## 6. Eliminación Lógica y Reutilización

La eliminación **no borra físicamente** el registro del `.dat`. Solo:
1. Escribe `estado = 0` en el campo del registro
2. Remueve la clave del índice `.idx`
3. Agrega el byte offset al archivo `.holes`

En la próxima inserción, se usa primero un slot del `.holes` antes de hacer
append al final del archivo, evitando fragmentación indefinida.

Para recuperar espacio en disco físicamente, se llama a `compact()`:

```
ANTES compact():               DESPUÉS compact():
offset=0   → ID=1 (activo)    offset=0   → ID=1
offset=97  → ID=2 (activo)    offset=97  → ID=2
offset=194 → ID=3 (DELETED)   offset=194 → ID=4   ← reorganizado
offset=291 → ID=4 (activo)    (archivo más pequeño)
```

---

## 7. Sincronización Cliente→Servidor (Rutas UNC)

### 7.1 Flujo de Sincronización

```
CLIENTE                          RED / UNC                    SERVIDOR
──────                           ─────────                    ────────
send.py                          \\HOSTNAME\DATOS             update.py
  │                                    │                          │
  ├─ Exporta productos.dat → CSV       │                          │
  ├─ Exporta clientes.dat  → CSV       │                          │
  ├─ Exporta ventas.dat    → CSV       │                          │
  ├─ shutil.copy2(csv, UNC)──────────►│◄─── monitor ────────────┤
  └─ Escribe MANIFEST.txt  ──────────►│     scan cada 10s        │
                                       │     lee CSV              │
                                       │     INSERT OR REPLACE────►│
                                       │                       minimarket.db
```

### 7.2 Configuración de la Ruta UNC

**En `APPLICATION/utils/config.py`:**
```python
HOSTNAME  = socket.gethostname()    # ej. "LAPTOP-XYZ"
UNC_DATOS = f"\\\\{HOSTNAME}\\DATOS"  # \\LAPTOP-XYZ\DATOS
```

**Para habilitarla en Windows:**
1. Click derecho en la carpeta `DATOS/` → Propiedades → Compartir
2. Compartir con el nombre `DATOS`
3. Dar permisos de escritura al usuario local

Si la ruta UNC no está disponible, `send.py` usa automáticamente
el fallback local `BASE_DIR/DATOS/`.

---

## 8. Instalación y Ejecución

### 8.1 Requisitos

- Python 3.10 o superior
- Windows 10/11 (para rutas UNC nativas)
- No se requieren paquetes externos para ejecutar el sistema

### 8.2 Ejecución del Cliente POS

```bash
cd C:\ProyectosUniversidad\SystemPos-MiniMarket\APPLICATION
python main.py
```

### 8.3 Ejecución del Servidor

```bash
cd C:\ProyectosUniversidad\SystemPos-MiniMarket\SERVIDOR
python server.py
```

### 8.4 Ejecutar Sincronización Manual

```bash
cd C:\ProyectosUniversidad\SystemPos-MiniMarket\APPLICATION
python send.py
```

### 8.5 Ejecutar Monitor del Servidor

```bash
cd C:\ProyectosUniversidad\SystemPos-MiniMarket\SERVIDOR
python update.py
```

---

## 9. Compilación a Ejecutables .EXE

### 9.1 Instalar PyInstaller

```bash
pip install pyinstaller
```

### 9.2 Compilar Todos los Ejecutables

```bash
cd C:\ProyectosUniversidad\SystemPos-MiniMarket
python build.py
```

Esto genera en `dist/`:
| Ejecutable          | Descripción                                    |
|---------------------|------------------------------------------------|
| `MiniMarketPOS.exe` | Aplicación cliente POS completa                |
| `Send.exe`          | Agente de sincronización (cliente → servidor)  |
| `Update.exe`        | Monitor y procesador de archivos (servidor)    |
| `Server.exe`        | Panel de gestión del servidor central          |

### 9.3 Compilar Solo un Ejecutable

```bash
python build.py --only send     # Solo Send.exe
python build.py --only update   # Solo Update.exe
python build.py --only pos      # Solo MiniMarketPOS.exe
python build.py --only server   # Solo Server.exe
```

### 9.4 Uso de los Ejecutables en Producción

**Sucursal (cliente):**
```
MiniMarketPOS.exe  → Abrir el POS
Send.exe           → Sincronizar datos hacia el servidor
```

**Servidor central:**
```
Server.exe   → Panel de administración y consultas
Update.exe   → Monitor continuo de la carpeta DATOS/
```

**Automatización (tarea programada Windows):**
```
Ejecutar Send.exe cada hora para sincronizar automáticamente.
Ejecutar Update.exe al inicio del servidor para monitoreo continuo.
```

---

## 10. Funcionalidades del Sistema

### 10.1 Módulo de Productos
- Alta, baja y modificación de productos
- Búsqueda por nombre o categoría (filtrado sobre índice)
- Control de stock (actualización automática al vender)
- Eliminación lógica con reutilización de espacio
- Compactación física del archivo `.dat`

### 10.2 Módulo de Clientes
- Registro de clientes con DNI único
- Búsqueda por nombre o DNI
- CRUD completo con validaciones

### 10.3 Módulo de Ventas (POS)
- Búsqueda de productos con sugerencias en tiempo real
- Carrito con múltiples productos y cantidades
- Cálculo automático de subtotal, IGV (18%) y total
- Descuento automático de stock al registrar venta
- Anulación de ventas con restauración de stock

### 10.4 Dashboard
- Ventas del día con actualización en tiempo real
- KPIs: número de ventas, ingresos, productos, clientes
- Tabla de últimas transacciones

### 10.5 Historial
- Filtro de ventas por fecha
- Vista de detalle por venta
- Anulación de ventas históricas

### 10.6 Sincronización
- Exportación de datos a CSV
- Copia automática a carpeta de red (UNC)
- Log de sincronización con timestamps
- Manifiesto de archivos enviados

---

## 11. Base de Datos del Servidor (SQLite)

Solo el servidor accede a SQLite. El cliente nunca se conecta a una BD.

### 11.1 Tablas Principales

| Tabla           | Descripción                               |
|-----------------|-------------------------------------------|
| `sucursales`    | Registro de sucursales que sincronizan    |
| `productos`     | Inventario consolidado de todas las suc.  |
| `clientes`      | Clientes registrados en todas las suc.    |
| `ventas`        | Transacciones de todas las sucursales     |
| `detalle_ventas`| Ítems de cada venta                       |
| `sync_log`      | Log de cada sincronización procesada      |

### 11.2 Vistas de Análisis

```sql
-- Resumen de ventas por sucursal y fecha
SELECT * FROM v_ventas_resumen;

-- Productos más vendidos
SELECT * FROM v_productos_mas_vendidos LIMIT 10;
```

### 11.3 Inicializar la BD manualmente

```bash
cd SERVIDOR
sqlite3 database/minimarket.db < init_db.sql
```

---

## 12. Justificación Académica

### 12.1 Arquitectura Monolítica

Toda la lógica del cliente reside en un único módulo ejecutable
(`MiniMarketPOS.exe`). No hay comunicación en tiempo real con el servidor;
el cliente opera **completamente autónomo**.

### 12.2 Acceso Directo (Random Access)

`FileManager.find_by_id()` implementa acceso en O(1):
```python
offset = self._index.get_offset(record_id)  # O(1) dict lookup
file.seek(offset)                           # posicionamiento directo
data = file.read(RECORD_SIZE)              # lectura de tamaño fijo
```

### 12.3 Organización Indexada

Los archivos `.idx` implementan un **índice primario** que mapea
clave → offset físico, permitiendo acceso por clave sin escaneo secuencial.

### 12.4 Rutas de Red (UNC)

`send.py` usa rutas UNC estándar de Windows (`\\HOSTNAME\DATOS`)
para copiar archivos a través de la red local, simulando una
arquitectura cliente-servidor de sincronización por archivos.

### 12.5 Sincronización Diferida

El sistema no usa comunicación en tiempo real. La sincronización
es **batch/diferida**: el cliente exporta cuando el usuario lo decide
y el servidor procesa los archivos de forma asíncrona. Esto es
propio de arquitecturas monolíticas distribuidas con eventual consistency.

---

## 13. Diagrama de Flujo — Nueva Venta

```
Usuario en VentasView
        │
        ▼
Buscar producto (búsqueda sobre índice .idx)
        │
        ▼
Ingresar cantidad
        │
        ▼
Agregar al carrito (validar stock disponible)
        │  (repetir por cada producto)
        ▼
Registrar Venta
        │
        ├─► venta_service.crear_venta()
        │       │
        │       ├─ Validar stock de cada producto (FileManager.find_by_id)
        │       ├─ Calcular subtotal, IGV(18%), total
        │       ├─ FileManager.insert(venta)     → ventas.dat
        │       ├─ FileManager.insert(detalle)×n → detalles.dat
        │       └─ ProductoService.descontar_stock() × n → productos.dat
        │
        └─► Mostrar ticket de venta
```

---

## 14. Ejemplo de Uso de Offsets e Índices

```python
# Verificar el índice en memoria:
from services.index_manager import IndexManager
from pathlib import Path

idx = IndexManager(
    Path("DATA/productos.idx"),
    Path("DATA/productos.holes")
)
print(idx.debug_dump())
# Salida:
# IndexManager(productos.idx)
#   Entradas activas: 3
#   Holes disponibles: 1
#     key=     1 → offset=         0 bytes
#     key=     2 → offset=        97 bytes
#     key=     4 → offset=       291 bytes

# Leer un registro directamente:
import struct
with open("DATA/productos.dat", "rb") as f:
    f.seek(97)              # ir al registro con id=2
    data = f.read(97)       # leer 97 bytes
    print(struct.unpack("!I50sdI30sB", data))
```

---

## 15. Logs del Sistema

Los logs se generan diariamente en:
- **Cliente:** `APPLICATION/logs/pos_YYYY-MM-DD.log`
- **Servidor:** `SERVIDOR/logs/` (según configuración)

Formato:
```
2025-01-15 10:23:45 | INFO     | file_manager          | [Producto] INSERT id=1 offset=0 size=97B
2025-01-15 10:23:46 | INFO     | producto_service      | Producto creado: Producto(id=1, nombre='Agua Mineral', ...)
2025-01-15 10:24:01 | INFO     | venta_service         | Venta registrada: id=1 total=S/4.13 ítems=2 cliente=0
2025-01-15 10:30:00 | INFO     | send                  | ✓ productos_20250115_103000.csv → \\LAPTOP-XYZ\DATOS\
```

---

*Proyecto desarrollado para el Curso de Arquitectura de Software — UNMSM*

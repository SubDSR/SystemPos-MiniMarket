# MiniMarket POS — Módulo Cliente (APPLICATION)

Módulo cliente del sistema MiniMarket POS. Aplicación de escritorio Java SE 17 + Swing con
acceso directo a archivos binarios de longitud fija y sincronización hacia el servidor central.

---

## Compilar y ejecutar

```bat
# Desde la raíz del proyecto:
build_client.bat           # genera APPLICATION/dist/POSClient.jar y Send.jar

# Ejecutar POS (GUI):
APPLICATION\run.bat
# o directamente:
java -jar APPLICATION\dist\POSClient.jar

# Ejecutar sincronización (GUI):
APPLICATION\run_send.bat
# o directamente:
java -jar APPLICATION\dist\Send.jar
# o en modo consola (sin ventana):
java -jar APPLICATION\dist\Send.jar --headless
```

> **Requisito:** JDK 17+. Sin Maven ni dependencias externas.

---

## JARs generados

| JAR | Main-Class | Tamaño |
|-----|-----------|--------|
| `dist/POSClient.jar` | `com.minimarket.MainApp` | ~88 KB |
| `dist/Send.jar` | `com.minimarket.send.Send` | ~88 KB |

---

## Estructura de paquetes

```
com.minimarket/
├── MainApp.java                 ← Entry point; inicia Swing (Look&Feel Nimbus)
├── models/
│   ├── BinaryRecord.java        ← Interfaz: SIZE, toBytes(), fromBytes()
│   ├── Producto.java            ← 97 bytes: id(4)+nombre(50)+precio(8)+stock(4)+categoria(30)+estado(1)
│   ├── Cliente.java             ← 131 bytes: id(4)+nombre(50)+dni(11)+telefono(15)+email(50)+estado(1)
│   ├── Venta.java               ← 52 bytes: id(4)+cliente_id(4)+fecha(19)+subtotal(8)+igv(8)+total(8)+estado(1)
│   └── DetalleVenta.java        ← 33 bytes: id(4)+venta_id(4)+producto_id(4)+cantidad(4)+precio_unitario(8)+subtotal(8)+1
├── services/
│   ├── FileManager.java         ← RandomAccessFile, seek(offset), insert/update/delete/findById/compact
│   ├── IndexManager.java        ← TreeMap<Integer,Long> persistido en .idx; .holes para offsets libres
│   ├── ProductoService.java     ← CRUD de productos vía FileManager
│   ├── ClienteService.java      ← CRUD de clientes vía FileManager
│   └── VentaService.java        ← CRUD de ventas y detalles vía FileManager
├── sync/
│   └── SyncAgent.java           ← Exporta .dat → CSV → copia a DATOS/ (UNC o local)
├── send/
│   └── Send.java                ← Main-Class de Send.jar; llama a SyncAgent
├── utils/
│   ├── Config.java              ← Rutas del sistema; resuelve BASE_DIR via MINIMARKET_HOME
│   └── AppLogger.java           ← Logger en APPLICATION/logs/pos_YYYY-MM-DD.log
└── views/
    ├── MainWindow.java          ← Ventana principal con sidebar de navegación
    ├── DashboardView.java       ← Panel de estadísticas y KPIs
    ├── ProductosView.java       ← CRUD de productos con tabla Swing
    ├── ClientesView.java        ← CRUD de clientes con tabla Swing
    ├── VentasView.java          ← Nueva venta con búsqueda de productos y clientes
    ├── HistorialView.java       ← Historial de ventas con filtros
    └── SyncDialog.java          ← Diálogo de sincronización manual
```

---

## Archivos de datos (DATA/)

Los datos se almacenan en `DATA/` en la raíz del proyecto:

```
DATA/
├── productos.dat   ← Registros de 97 bytes (acceso O(1) por offset)
├── productos.idx   ← Índice binario: [4B count][4B key][8B offset] × N
├── productos.holes ← Lista de offsets liberados para reutilización
├── clientes.dat / clientes.idx / clientes.holes
├── ventas.dat / ventas.idx / ventas.holes
└── detalles.dat / detalles.idx / detalles.holes
```

### Estructura de registros binarios

| Entidad | Tamaño | Campos |
|---------|--------|--------|
| Producto | 97 B | id(4B) nombre(50B) precio(8B) stock(4B) categoria(30B) estado(1B) |
| Cliente | 131 B | id(4B) nombre(50B) dni(11B) telefono(15B) email(50B) estado(1B) |
| Venta | 52 B | id(4B) cliente_id(4B) fecha(19B) subtotal(8B) igv(8B) total(8B) estado(1B) |
| DetalleVenta | 33 B | id(4B) venta_id(4B) prod_id(4B) cantidad(4B) precio_unit(8B) subtotal(8B) estado(1B) |

- **Byte order:** big-endian (compatible con `struct.pack("!", ...)` de Python)
- **Strings:** UTF-8 con padding de ceros hasta longitud fija
- **Eliminación lógica:** campo `estado = 0` (el offset va a `.holes` para reutilizar)
- **Compactación:** `FileManager.compact()` reescribe el `.dat` eliminando registros borrados

---

## Sincronización (Send.jar)

El módulo `Send` exporta los archivos locales hacia el servidor:

1. Lee todos los registros activos de cada `.dat` usando `FileManager.findAll()`
2. Genera 4 CSVs en `APPLICATION/exports/`: `productos.csv`, `clientes.csv`, `ventas.csv`, `detalles.csv`
3. Copia los CSVs + un `MANIFEST` a `DATOS/` (intenta UNC `\\HOSTNAME\DATOS`, cae en local)
4. El servidor (`Update.jar`) monitorea `DATOS/` y hace UPSERT en SQLite

---

## Resolución de rutas

`Config.java` determina `BASE_DIR` en este orden:

1. Variable de entorno `MINIMARKET_HOME` (setada por los `.bat` de ejecución)
2. Si CWD es `APPLICATION/` → sube un nivel
3. Si CWD es `APPLICATION/dist/` → sube dos niveles
4. Caso contrario → usa CWD como raíz del proyecto

```bat
# En caso de problemas de rutas:
set MINIMARKET_HOME=C:\ProyectosUniversidad\SystemPos-MiniMarket
java -jar APPLICATION\dist\POSClient.jar
```

---

## Directorios en runtime

```
APPLICATION/
├── logs/      ← pos_YYYY-MM-DD.log  (creado automáticamente)
└── exports/   ← CSVs exportados por Send.jar (creado automáticamente)
```

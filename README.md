# Sistema POS MiniMarket — Arquitectura Monolítica con Acceso Directo

> **Curso:** Arquitectura de Software
> **Entregable:** 01 — Arquitectura Unitaria / Monolítica
> **Empresa simulada:** MiniMarket El Ahorro S.A.C. — RUC 20123456789
> **Tecnología:** Java 17 SE + Swing (sin frameworks, sin Maven en ejecución)

---

## 1. Descripción General

Sistema de Punto de Venta (POS) para minimarket que demuestra los principios de
la **arquitectura monolítica** en un entorno de sucursales distribuidas:

- Cada sucursal trabaja **localmente y de forma completamente autónoma**.
- Los datos se persisten en **archivos binarios de acceso directo** (`.dat` + `.idx`).
- La sincronización al servidor se realiza copiando archivos CSV a una
  **carpeta compartida de red** (ruta UNC: `\\HOSTNAME\DATOS`).
- El servidor consolida los datos en una **base de datos SQLite centralizada**.
- Todo el sistema es ejecutable con `java -jar` — sin dependencia de Maven en tiempo de ejecución.

---

## 2. Arquitectura del Sistema

```
┌────────────────────────────────────────────────────────────────────┐
│              ARQUITECTURA MONOLÍTICA — VISTA GENERAL               │
├──────────────────────────┬─────────────────────────────────────────┤
│   CLIENTE POS            │   SERVIDOR CENTRAL                       │
│   APPLICATION/           │   SERVIDOR/                              │
│                          │                                          │
│  ┌──────────────────┐    │   ┌──────────────────────┐             │
│  │  Interfaz Swing  │    │   │  ServerApp.java (GUI) │             │
│  │  - Dashboard     │    │   │  - Monitor DATOS/     │             │
│  │  - Productos     │    │   │  - Log de syncs       │             │
│  │  - Clientes      │    │   └──────────┬───────────┘             │
│  │  - Ventas        │    │              │                           │
│  │  - Historial     │    │   ┌──────────▼───────────┐             │
│  └──────────┬───────┘    │   │  SyncMonitor.java     │             │
│             │            │   │  - Escanea DATOS/     │             │
│  ┌──────────▼───────┐    │   │  - UPSERT SQLite      │             │
│  │  Servicios CRUD  │    │   └──────────────────────┘             │
│  │  ProductoService │    │                                          │
│  │  ClienteService  │    │   ┌──────────────────────┐             │
│  │  VentaService    │    │   │  minimarket.db         │             │
│  └──────────┬───────┘    │   │  (SQLite consolidado) │             │
│             │            │   └──────────────────────┘             │
│  ┌──────────▼───────┐    │                                          │
│  │  FileManager<T>  │    │              ▲                           │
│  │  RandomAccessFile│    │              │                           │
│  │  seek(offset)    │    │   ┌──────────┴───────────┐             │
│  │  .dat + .idx     │    │   │  Update.jar           │             │
│  └──────────┬───────┘    │   │  (autónomo)           │             │
│             │            │   └──────────────────────┘             │
│  ┌──────────▼───────┐    │              ▲                           │
│  │  SyncAgent       │    │              │ CSV                       │
│  │  Exporta CSV     │────┼──────────────┘                          │
│  │  → DATOS/ (UNC)  │    │   ╔══════════════════════╗             │
│  └──────────────────┘    │   ║  DATOS/               ║             │
│             │            │   ║  (Carpeta compartida) ║             │
│  ┌──────────▼───────┐    │   ╚══════════════════════╝             │
│  │  Send.jar        │────┼──────────────►                          │
│  │  (autónomo)      │    │                                          │
│  └──────────────────┘    │                                          │
└──────────────────────────┴─────────────────────────────────────────┘
```

### Flujo de Datos — Registros Binarios

```
ESCRITURA (INSERT):
  Producto.toBytes() → byte[97]
       │
       ▼
  FileManager.insert()
       │
       ├─ IndexManager.getFreeSlot() → offset reutilizado (.holes)
       │  └─ O bien: offset = tamaño_actual(.dat) → append
       │
       ├─ RandomAccessFile.seek(offset)
       ├─ RandomAccessFile.write(byte[97])
       └─ IndexManager.add(id → offset) → persiste en .idx

LECTURA (findById):
  id → IndexManager.getOffset(id) → offset    O(1)
     → RandomAccessFile.seek(offset)
     → RandomAccessFile.readFully(byte[97])
     → Producto.fromBytes(byte[97])            O(1) total

ELIMINACIÓN LÓGICA:
  record.setEstado(0)
  RandomAccessFile.seek(offset)
  RandomAccessFile.write(bytes_actualizados)   ← solo el flag cambia
  IndexManager.remove(id) → offset va a .holes ← reutilizable
```

---

## 3. Estructura del Proyecto

```
SystemPos-MiniMarket/
│
├── APPLICATION/                     ← Cliente POS (Java SE + Swing)
│   ├── src/main/java/com/minimarket/
│   │   ├── MainApp.java             ← Punto de entrada POSClient.jar
│   │   ├── models/
│   │   │   ├── BinaryRecord.java    ← Interfaz de serialización binaria
│   │   │   ├── Producto.java        ← 97  bytes fijos
│   │   │   ├── Cliente.java         ← 131 bytes fijos
│   │   │   ├── Venta.java           ← 52  bytes fijos
│   │   │   └── DetalleVenta.java    ← 33  bytes fijos
│   │   ├── services/
│   │   │   ├── FileManager.java     ← CRUD con RandomAccessFile + seek()
│   │   │   ├── IndexManager.java    ← TreeMap<id,offset> ↔ .idx binario
│   │   │   ├── ProductoService.java
│   │   │   ├── ClienteService.java
│   │   │   └── VentaService.java
│   │   ├── send/
│   │   │   └── Send.java            ← Punto de entrada Send.jar
│   │   ├── sync/
│   │   │   └── SyncAgent.java       ← Exporta CSV → DATOS/ (UNC/fallback)
│   │   ├── utils/
│   │   │   ├── Config.java          ← Rutas centralizadas + UNC
│   │   │   └── AppLogger.java       ← Logging diario a APPLICATION/logs/
│   │   └── views/
│   │       ├── MainWindow.java      ← Ventana principal (CardLayout)
│   │       ├── DashboardView.java   ← KPIs + últimas ventas
│   │       ├── ProductosView.java   ← CRUD productos
│   │       ├── ClientesView.java    ← CRUD clientes
│   │       ├── VentasView.java      ← POS interactivo con carrito
│   │       ├── HistorialView.java   ← Historial con anulación
│   │       └── SyncDialog.java      ← Diálogo de sincronización
│   ├── build/                       ← Generado por build_client.bat
│   │   ├── classes/
│   │   └── tmp/
│   ├── dist/                        ← Generado por build_client.bat
│   │   ├── POSClient.jar
│   │   └── Send.jar
│   ├── logs/                        ← Logs diarios (pos_YYYY-MM-DD.log)
│   ├── exports/                     ← CSVs de exportación temporal
│   ├── run.bat                      ← Lanzador (java -jar POSClient.jar)
│   └── run_send.bat                 ← Lanzador (java -jar Send.jar)
│
├── SERVIDOR/                        ← Servidor Central (Java SE + SQLite)
│   ├── src/main/java/com/minimarket/server/
│   │   ├── ServerApp.java           ← Punto de entrada Server.jar
│   │   ├── database/
│   │   │   └── DatabaseManager.java ← Gestor SQLite JDBC
│   │   ├── sync/
│   │   │   └── SyncMonitor.java     ← Monitor DATOS/ → UPSERT SQLite
│   │   └── update/
│   │       └── Update.java          ← Punto de entrada Update.jar
│   ├── build/                       ← Generado por build_server.bat
│   ├── dist/                        ← Generado por build_server.bat
│   │   ├── Server.jar               ← Fat JAR (con sqlite-jdbc)
│   │   └── Update.jar               ← Fat JAR (con sqlite-jdbc)
│   ├── lib/                         ← sqlite-jdbc.jar (auto-descargado)
│   ├── database/                    ← minimarket.db (SQLite)
│   ├── logs/                        ← Logs del servidor
│   ├── init_db.sql                  ← Esquema SQL (referencia/init manual)
│   ├── run-servidor.bat             ← Lanzador (java -jar Server.jar)
│   └── run_update.bat               ← Lanzador (java -jar Update.jar)
│
├── DATA/                            ← Archivos binarios locales del cliente
│   ├── productos.dat / .idx / .holes
│   ├── clientes.dat  / .idx / .holes
│   ├── ventas.dat    / .idx / .holes
│   └── detalles.dat  / .idx / .holes
│
├── DATOS/                           ← Carpeta compartida de sincronización
│   │                                   (UNC: \\HOSTNAME\DATOS o fallback local)
│   ├── productos_YYYYMMDD_HHMMSS.csv
│   ├── clientes_YYYYMMDD_HHMMSS.csv
│   ├── ventas_YYYYMMDD_HHMMSS.csv
│   ├── detalles_YYYYMMDD_HHMMSS.csv
│   └── MANIFEST_YYYYMMDD_HHMMSS.txt
│
├── build.bat            ← Alias de rebuild_all.bat
├── build_client.bat     ← Compila CLIENT: POSClient.jar + Send.jar
├── build_server.bat     ← Compila SERVER: Server.jar + Update.jar
└── rebuild_all.bat      ← Rebuild completo del sistema
```

---

## 4. Compilación — Puro javac + jar

### Requisitos
- **JDK 17+** (OpenJDK / Temurin recomendado)
- **Conexión a internet** (solo primera vez, para descargar sqlite-jdbc)

### Compilar todo (recomendado)
```bat
rebuild_all.bat
```

### Compilar solo cliente
```bat
build_client.bat
```
Genera: `APPLICATION\dist\POSClient.jar` + `APPLICATION\dist\Send.jar`

### Compilar solo servidor
```bat
build_server.bat
```
Genera: `SERVIDOR\dist\Server.jar` + `SERVIDOR\dist\Update.jar`
> La primera vez descarga automáticamente `sqlite-jdbc-3.45.3.0.jar` desde Maven Central.

---

## 5. Ejecución

### Cliente POS
```bat
java -jar APPLICATION\dist\POSClient.jar
REM  o directamente:
APPLICATION\run.bat
```

### Módulo Send (sincronización)
```bat
java -jar APPLICATION\dist\Send.jar
REM  Modo consola (sin GUI):
java -jar APPLICATION\dist\Send.jar --headless
```

### Servidor Central
```bat
java -jar SERVIDOR\dist\Server.jar
REM  o directamente:
SERVIDOR\run-servidor.bat
```

### Módulo Update (procesamiento autónomo)
```bat
java -jar SERVIDOR\dist\Update.jar
REM  Monitoreo continuo (sin GUI):
java -jar SERVIDOR\dist\Update.jar --headless
REM  Un scan y termina:
java -jar SERVIDOR\dist\Update.jar --once
```

### Variable de entorno (si hay problemas de rutas)
```bat
set MINIMARKET_HOME=C:\ProyectosUniversidad\SystemPos-MiniMarket
```

---

## 6. Flujo de Sincronización

```
  ┌─────────┐         ┌──────────┐        ┌─────────────┐
  │ Cliente │  Click  │ Send.jar │  Copia  │   DATOS/    │
  │   POS   │────────►│(SyncAgent│────────►│  CSV files  │
  └─────────┘ Sync    │ .java)   │   UNC   └──────┬──────┘
      │               └──────────┘                │
      │ .dat+.idx                                  │ 10s poll
      │ (RandomAccess)                    ┌────────▼──────┐
      │                                   │ SyncMonitor   │
  ┌───▼──────┐                            │  .java        │
  │  DATA/   │                            └────────┬──────┘
  │ *.dat    │                                      │ UPSERT
  │ *.idx    │                            ┌────────▼──────┐
  │ *.holes  │                            │ minimarket.db │
  └──────────┘                            │   (SQLite)    │
                                          └───────────────┘
```

**Pasos detallados:**

1. El cliente POS registra productos, clientes y ventas en archivos `.dat` locales.
2. Al presionar **Sincronizar** (o ejecutar `Send.jar`):
   - Se exportan 4 CSVs a `APPLICATION/exports/`
   - Se copian al destino: `\\HOSTNAME\DATOS` (UNC) o `DATOS/` (fallback)
   - Se crea un `MANIFEST_timestamp.txt` con metadatos
3. El `SyncMonitor` del servidor escanea `DATOS/` cada 10 segundos.
4. Por cada CSV nuevo: parseo → UPSERT transaccional en SQLite.
5. El proceso se registra en `sync_log` y el archivo se marca en `processed_files.txt`.

---

## 7. Estructura de Registros Binarios

| Entidad | Tamaño | Campos |
|---------|--------|--------|
| Producto | **97 bytes** | id(4) nombre(50) precio(8) stock(4) categoria(30) estado(1) |
| Cliente | **131 bytes** | id(4) nombre(50) dni(11) telefono(15) email(50) estado(1) |
| Venta | **52 bytes** | id(4) cliente_id(4) fecha(19) subtotal(8) igv(8) total(8) estado(1) |
| DetalleVenta | **33 bytes** | id(4) venta_id(4) producto_id(4) cantidad(4) precio_unitario(8) subtotal(8) estado(1) |

**Formato de índice `.idx`:**
```
[4 bytes: count] [4 bytes: id][8 bytes: offset] × count
```
Ejemplo: id=101 → offset=0, id=102 → offset=97, id=103 → offset=194

---

## 8. Conceptos Académicos Implementados

| Concepto | Implementación | Clase/Archivo |
|----------|----------------|---------------|
| **Arquitectura Monolítica** | Todo el cliente en un JAR, todo el servidor en otro JAR | `POSClient.jar`, `Server.jar` |
| **Procesamiento Autónomo** | Cliente funciona sin conexión permanente | `FileManager`, `Config` |
| **Acceso Directo** | `seek(offset)` para O(1) sin escaneo | `FileManager.java` |
| **Registros de Longitud Fija** | 97B, 131B, 52B, 33B exactos | Todos los modelos |
| **Índices Físicos** | TreeMap<id,offset> ↔ archivo `.idx` binario | `IndexManager.java` |
| **Eliminación Lógica** | Campo `estado` (1=activo, 0=eliminado) | `FileManager.delete()` |
| **Reutilización de Slots** | Offsets liberados en archivo `.holes` | `IndexManager.java` |
| **Compactación** | Reescribe `.dat` eliminando registros borrados | `FileManager.compact()` |
| **Rutas UNC de Red** | `\\HOSTNAME\DATOS` con fallback local | `SyncAgent.java` |
| **Sincronización por Archivos** | CSV → carpeta compartida → UPSERT SQLite | `Send.jar` → `Update.jar` |
| **Base de Datos Solo en Servidor** | SQLite únicamente en `Server.jar`/`Update.jar` | `DatabaseManager.java` |
| **Módulos Ejecutables** | 4 JARs independientes con `java -jar` | `build_*.bat` |

---

## 9. Notas Técnicas

- **SQLite JDBC**: Solo existe como dependencia del servidor. El cliente NO usa SQLite.
- **Transacciones**: Los UPSERT del servidor son transaccionales con rollback en errores.
- **Concurrencia**: SQLite usa WAL mode para permitir lecturas concurrentes.
- **IGV**: La tasa de 18% se calcula en `VentaService` y se almacena en el registro.
- **Fat JARs**: `Server.jar` y `Update.jar` incluyen internamente `sqlite-jdbc` para ser 100% portables.
- **Logs**: El cliente genera `APPLICATION/logs/pos_YYYY-MM-DD.log` diario con rotación.

---

*Generado para el curso de Arquitectura de Software — Entregable 01*

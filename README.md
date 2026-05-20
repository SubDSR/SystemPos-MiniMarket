# Sistema POS MiniMarket — Arquitectura Monolítica con Acceso Directo

> **Curso:** Arquitectura de Software  
> **Entregable:** 01 — Arquitectura Unitaria / Monolítica  
> **Empresa simulada:** MiniMarket El Ahorro S.A.C.  
> **Tecnología:** Java 17 + JavaFX 21

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
│   (APPLICATION-JAVA/)   │   (SERVIDOR-JAVA/)                         │
│                         │                                            │
│  ┌─────────────────┐    │    ┌──────────────────────┐               │
│  │  Interfaz GUI   │    │    │  ServerApp.java (GUI) │               │
│  │  (JavaFX)       │    │    │  - Dashboard          │               │
│  └────────┬────────┘    │    │  - Consultas SQL      │               │
│           │             │    │  - Log de syncs        │               │
│  ┌────────▼────────┐    │    └──────────┬───────────┘               │
│  │  Servicios      │    │               │                            │
│  │  (CRUD)         │    │    ┌──────────▼───────────┐               │
│  └────────┬────────┘    │    │  SyncMonitor.java     │               │
│           │             │    │  - Monitor DATOS/     │               │
│  ┌────────▼────────┐    │    │  - UPSERT SQLite       │               │
│  │  FileManager    │    │    └──────────────────────┘               │
│  │  (.dat + .idx)  │    │                                            │
│  └────────┬────────┘    │    ┌──────────────────────┐               │
│           │             │    │  minimarket.db         │               │
│  ┌────────▼────────┐    │    │  (SQLite consolidado) │               │
│  │  SyncAgent      │────┼───►│                        │               │
│  │  (CSV → DATOS/) │    │    └──────────────────────┘               │
│  └─────────────────┘    │                                            │
└─────────────────────────┴────────────────────────────────────────────┘
```

---

## 3. Estructura del Proyecto

```
SystemPos-MiniMarket/
├── APPLICATION-JAVA/          ← Cliente POS (JavaFX)
│   ├── pom.xml
│   ├── run.bat
│   └── src/main/java/com/minimarket/
│       ├── MainApp.java
│       ├── models/            ← Modelos serializables (binario big-endian)
│       │   ├── BinaryRecord.java
│       │   ├── Producto.java    (97 bytes)
│       │   ├── Cliente.java     (131 bytes)
│       │   ├── Venta.java       (52 bytes)
│       │   └── DetalleVenta.java (33 bytes)
│       ├── services/
│       │   ├── IndexManager.java
│       │   ├── FileManager.java
│       │   ├── ProductoService.java
│       │   ├── ClienteService.java
│       │   └── VentaService.java
│       ├── sync/
│       │   └── SyncAgent.java   ← Exporta CSV y copia a DATOS/
│       ├── utils/
│       │   ├── Config.java
│       │   └── AppLogger.java
│       └── views/               ← UI programática (sin FXML)
│           ├── MainWindow.java
│           ├── DashboardView.java
│           ├── ProductosView.java
│           ├── ClientesView.java
│           ├── VentasView.java
│           ├── HistorialView.java
│           └── SyncDialog.java
│
├── SERVIDOR-JAVA/             ← Servidor Central (JavaFX + SQLite)
│   ├── pom.xml
│   ├── run-servidor.bat
│   └── src/main/java/com/minimarket/server/
│       ├── ServerApp.java
│       ├── database/
│       │   └── DatabaseManager.java
│       └── sync/
│           └── SyncMonitor.java
│
├── DATA/                      ← Archivos binarios (.dat, .idx, .holes)
├── DATOS/                     ← Carpeta de sincronización (CSVs)
└── SERVIDOR/
    ├── database/              ← Base de datos SQLite (minimarket.db)
    ├── init_db.sql            ← Esquema SQL de referencia
    └── logs/
```

---

## 4. Cómo Ejecutar

### Requisitos
- Java 17+ (OpenJDK / Temurin recomendado)
- Maven 3.8+

### Cliente POS
```bat
cd APPLICATION-JAVA
mvn javafx:run
```

### Servidor Central
```bat
cd SERVIDOR-JAVA
mvn javafx:run
```

### Variable de entorno (si hay problemas de rutas)
```bat
set MINIMARKET_HOME=C:\ProyectosUniversidad\SystemPos-MiniMarket
```

---

## 5. Flujo de Sincronización

1. El cliente POS registra ventas y gestiona productos/clientes en archivos `.dat`.
2. Al hacer clic en **Sincronizar**, `SyncAgent` exporta 4 archivos CSV a `DATOS/`.
3. El `SyncMonitor` del servidor escanea `DATOS/` cada 10 segundos.
4. Cada CSV nuevo se parsea y se inserta/actualiza en la BD SQLite (`minimarket.db`).
5. El servidor registra el proceso en `sync_log` y marca el archivo como procesado.

---

## 6. Conceptos Académicos Implementados

| Concepto | Implementación |
|----------|----------------|
| Acceso Directo | `FileManager<T>` + `RandomAccessFile.seek()` |
| Registros de longitud fija | Tamaños fijos: Producto=97B, Cliente=131B, etc. |
| Índice en memoria | `IndexManager` con `TreeMap<Integer, Long>` |
| Eliminación lógica | Campo `estado` (1=activo, 0=eliminado) |
| Reutilización de slots | Lista de holes en archivo `.holes` |
| Compactación | `FileManager.compact()` reconstruye el `.dat` |
| Sincronización por archivos | CSV → carpeta compartida UNC → UPSERT SQLite |

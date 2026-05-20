# MiniMarket POS — Módulo Servidor (SERVIDOR)

Módulo servidor del sistema MiniMarket POS. Servidor central con base de datos SQLite,
GUI de monitoreo Swing, y procesador de CSVs de sincronización desde los clientes.

---

## Compilar y ejecutar

```bat
# Desde la raíz del proyecto:
build_server.bat           # descarga dependencias, compila → Server.jar y Update.jar

# Ejecutar servidor (GUI):
SERVIDOR\run-servidor.bat
# o directamente:
java -jar SERVIDOR\dist\Server.jar

# Ejecutar procesador de actualizaciones:
SERVIDOR\run_update.bat                    # GUI Swing
SERVIDOR\run_update.bat headless           # consola, monitoreo continuo
SERVIDOR\run_update.bat once               # un scan y termina (útil en CI/scripts)
# o directamente:
java -jar SERVIDOR\dist\Update.jar
java -jar SERVIDOR\dist\Update.jar --headless
java -jar SERVIDOR\dist\Update.jar --once
```

> **Requisito:** JDK 17+. `build_server.bat` descarga sqlite-jdbc y slf4j automáticamente.

---

## JARs generados (fat JARs)

| JAR | Main-Class | Tamaño | Contenido |
|-----|-----------|--------|-----------|
| `dist/Server.jar` | `com.minimarket.server.ServerApp` | ~13 MB | clases + sqlite-jdbc + slf4j |
| `dist/Update.jar` | `com.minimarket.server.update.Update` | ~13 MB | clases + sqlite-jdbc + slf4j |

Los fat JARs incluyen `sqlite-jdbc-3.45.3.0`, `slf4j-api-2.0.6` y `slf4j-nop-2.0.6`.
No requieren configuración de classpath adicional.

---

## Dependencias (`SERVIDOR/lib/`)

| Archivo | Versión | Propósito |
|---------|---------|-----------|
| `sqlite-jdbc.jar` | 3.45.3.0 | Driver SQLite nativo para Java |
| `slf4j-api.jar` | 2.0.6 | API de logging (requerida por sqlite-jdbc 3.45+) |
| `slf4j-nop.jar` | 2.0.6 | Implementación NOP (silencia mensajes de log de sqlite) |

Descargadas automáticamente por `build_server.bat` desde Maven Central.

---

## Estructura de paquetes

```
com.minimarket.server/
├── ServerApp.java              ← Main-Class de Server.jar; GUI de monitoreo del servidor
├── update/
│   └── Update.java             ← Main-Class de Update.jar; procesador de CSVs (GUI o headless)
├── database/
│   └── DatabaseManager.java    ← Gestión SQLite: esquema, UPSERT, getOrCreateSucursal, logSync
└── sync/
    └── SyncMonitor.java         ← Monitorea DATOS/ cada 10 s; parsea CSV; llama a DatabaseManager
```

---

## Base de datos (`database/minimarket.db`)

Esquema SQLite con soporte multi-sucursal:

```sql
sucursales      (id, hostname, nombre, ultima_sync)
productos       (id, sucursal_id, nombre, precio, stock, categoria, estado)
clientes        (id, sucursal_id, nombre, dni, telefono, email, estado)
ventas          (id, sucursal_id, cliente_id, fecha, subtotal, igv, total, estado)
detalle_ventas  (id, sucursal_id, venta_id, producto_id, cantidad, precio_unitario, subtotal)
sync_log        (id, sucursal_id, archivo, timestamp, registros, errores, mensaje)
```

Ver `init_db.sql` para el esquema completo con índices y vistas.

### Clave primaria compuesta

Todos los datos usan `(id, sucursal_id)` como PK para soportar múltiples clientes
con IDs independientes. La sucursal se identifica por el `hostname` de la máquina.

### UPSERT

`DatabaseManager.upsert()` usa `INSERT OR REPLACE` para todas las entidades:
si el registro `(id, sucursal_id)` ya existe se actualiza, si no se inserta.

---

## Procesamiento de sincronización (Update.jar)

Ciclo de `SyncMonitor`:

1. Escanea `DATOS/` buscando archivos `productos_*.csv`, `clientes_*.csv`, etc.
2. Compara contra `SERVIDOR/processed_files.txt` para evitar reprocesar
3. Por cada CSV nuevo: parsea línea a línea → llama `DatabaseManager.upsert()`
4. Registra resultado en `sync_log` y agrega nombre de archivo a `processed_files.txt`
5. Repite cada 10 segundos (modo `--headless`) o termina (modo `--once`)

### Formato CSV esperado

```
# productos_HOSTNAME_TIMESTAMP.csv
id,nombre,precio,stock,categoria,estado
1,Arroz Extra,3.50,100,Abarrotes,1

# clientes_HOSTNAME_TIMESTAMP.csv
id,nombre,dni,telefono,email,estado
1,Juan Perez,12345678,987654321,juan@email.com,1

# ventas_HOSTNAME_TIMESTAMP.csv
id,cliente_id,fecha,subtotal,igv,total,estado
1,1,2024-01-15 10:30:00,100.00,18.00,118.00,1

# detalles_HOSTNAME_TIMESTAMP.csv
id,venta_id,producto_id,cantidad,precio_unitario,subtotal
1,1,1,2,3.50,7.00
```

---

## Resolución de rutas

`Update.java` (y `ServerApp.java`) determinan la raíz del proyecto:

1. Variable de entorno `MINIMARKET_HOME` (setada por los `.bat` de ejecución)
2. Si CWD es `dist/` (dentro de `SERVIDOR/`) → sube dos niveles
3. Si CWD es `SERVIDOR/` → sube un nivel
4. Caso contrario → usa CWD como raíz del proyecto

```bat
# En caso de problemas de rutas:
set MINIMARKET_HOME=C:\ProyectosUniversidad\SystemPos-MiniMarket
java -jar SERVIDOR\dist\Update.jar --once
```

---

## Directorios en runtime

```
SERVIDOR/
├── database/minimarket.db      ← Base de datos SQLite (creada automáticamente)
├── logs/                       ← Logs del servidor (creado automáticamente)
├── processed_files.txt         ← Registro de CSVs ya procesados
└── lib/                        ← JARs de dependencias (descargados por build_server.bat)
```

---

## Inicializar base de datos manualmente

Para inspeccionar o reinicializar la base de datos con sqlite3:

```bash
sqlite3 SERVIDOR/database/minimarket.db < SERVIDOR/init_db.sql
```

> Nota: `Update.jar` inicializa el esquema automáticamente en el primer arranque.
> `init_db.sql` existe como referencia documentativa y para administración manual.

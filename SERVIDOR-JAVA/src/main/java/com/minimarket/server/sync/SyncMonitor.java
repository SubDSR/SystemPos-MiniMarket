package com.minimarket.server.sync;

import com.minimarket.server.database.DatabaseManager;

import java.io.*;
import java.nio.file.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Monitor de sincronizacion del servidor central.
 *
 * Monitorea la carpeta DATOS/ cada N segundos, detecta nuevos archivos CSV
 * enviados por los clientes POS y los procesa mediante UPSERT en SQLite.
 *
 * Proceso:
 *   1. Escanenar DATOS/ buscando archivos .csv nuevos
 *   2. Detectar entidad segun prefijo del nombre (productos_, clientes_, etc.)
 *   3. Parsear CSV y hacer UPSERT en la tabla correspondiente
 *   4. Registrar en sync_log
 *   5. Marcar el archivo como procesado
 *
 * Equivalente Java de SERVIDOR/update.py (clase UpdateAgent).
 */
public class SyncMonitor {

    private static final Logger LOG = Logger.getLogger(SyncMonitor.class.getName());
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final DatabaseManager dbManager;
    private final Path            datosDir;
    private final Path            processedFile;
    private final int             intervalSeconds;

    private Consumer<String> logCallback;
    private Consumer<String> statusCallback;

    private volatile boolean running = false;
    private Thread           monitorThread;

    public SyncMonitor(Path serverDir, Path datosDir, DatabaseManager dbManager,
                       int intervalSeconds) {
        this.dbManager      = dbManager;
        this.datosDir       = datosDir;
        this.intervalSeconds = intervalSeconds;
        this.processedFile  = serverDir.resolve("processed_files.txt");
        this.logCallback    = LOG::info;
        this.statusCallback = s -> { };
    }

    // ════════════════════════════════════════════════════════════════════════
    // CONTROL
    // ════════════════════════════════════════════════════════════════════════

    public void start() {
        if (running) return;
        running = true;
        monitorThread = new Thread(this::loop, "SyncMonitor");
        monitorThread.setDaemon(true);
        monitorThread.start();
        log("Monitor iniciado — carpeta: " + datosDir);
        log("Intervalo de escaneo: " + intervalSeconds + "s");
    }

    public void stop() {
        running = false;
        if (monitorThread != null) monitorThread.interrupt();
        log("Monitor detenido.");
    }

    public boolean isRunning() { return running; }

    // ════════════════════════════════════════════════════════════════════════
    // LOOP PRINCIPAL
    // ════════════════════════════════════════════════════════════════════════

    private void loop() {
        try {
            dbManager.initDatabase();
            log("Base de datos inicializada: " + dbManager.getDbPath());
            log("Monitoreando carpeta DATOS/...");

            while (running) {
                try {
                    int n = scanOnce();
                    if (n > 0) log("Ciclo: " + n + " archivo(s) procesado(s)");
                } catch (Exception e) {
                    log("[ERROR] en ciclo: " + e.getMessage());
                }
                Thread.sleep(intervalSeconds * 1000L);
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // ESCANEO
    // ════════════════════════════════════════════════════════════════════════

    /** Escanea una vez y procesa todos los archivos CSV nuevos. */
    public int scanOnce() {
        if (!Files.isDirectory(datosDir)) {
            log("[WARN] Carpeta DATOS no encontrada: " + datosDir);
            return 0;
        }

        Set<String> processed = getProcessedSet();
        List<Path>  csvFiles;
        try {
            csvFiles = Files.list(datosDir)
                .filter(p -> p.toString().toLowerCase().endsWith(".csv"))
                .sorted()
                .toList();
        } catch (IOException e) {
            log("[ERROR] Leyendo carpeta: " + e.getMessage());
            return 0;
        }

        List<Path> newFiles = csvFiles.stream()
            .filter(f -> !processed.contains(f.getFileName().toString()))
            .toList();

        if (newFiles.isEmpty()) return 0;

        log("Encontrados " + newFiles.size() + " archivo(s) nuevo(s)");
        int okCount = 0;
        for (Path fp : newFiles) {
            if (processCsvFile(fp)) {
                okCount++;
                statusCallback.accept("Procesado: " + fp.getFileName());
            }
        }
        return okCount;
    }

    // ════════════════════════════════════════════════════════════════════════
    // PROCESADOR DE ARCHIVO CSV
    // ════════════════════════════════════════════════════════════════════════

    private boolean processCsvFile(Path filepath) {
        String filename = filepath.getFileName().toString();
        String entity   = detectEntity(filename);
        if (entity == null) {
            log("[SKIP] Entidad desconocida: " + filename);
            return false;
        }
        log("[PROC] " + filename + " → entidad=" + entity);

        List<Map<String, String>> rows;
        try { rows = readCsv(filepath); }
        catch (IOException e) {
            log("[ERROR] Leyendo " + filename + ": " + e.getMessage());
            return false;
        }

        if (rows.isEmpty()) {
            log("[SKIP] " + filename + " sin registros");
            markProcessed(filename);
            return true;
        }

        try (Connection conn = dbManager.getConnection()) {
            conn.setAutoCommit(false);
            int sucursalId = dbManager.getOrCreateSucursal(conn, "localhost");

            int[] stats = processEntity(conn, entity, rows, sucursalId);
            dbManager.updateLastSync(conn, sucursalId);
            dbManager.logSync(conn, sucursalId, filename, entity, stats[0], stats[1], stats[2]);
            conn.commit();

            log(String.format("[OK]   %s — insertados=%d actualizados=%d errores=%d",
                filename, stats[0], stats[1], stats[2]));
            markProcessed(filename);
            return true;
        } catch (SQLException e) {
            log("[ERROR] Procesando " + filename + ": " + e.getMessage());
            return false;
        }
    }

    private int[] processEntity(Connection conn, String entity,
                                List<Map<String, String>> rows, int sucursalId)
            throws SQLException {
        int inserted = 0, updated = 0, errors = 0;
        for (Map<String, String> row : rows) {
            try {
                String result = switch (entity) {
                    case "productos" -> upsertProducto(conn, row, sucursalId);
                    case "clientes"  -> upsertCliente(conn, row, sucursalId);
                    case "ventas"    -> upsertVenta(conn, row, sucursalId);
                    case "detalles"  -> upsertDetalle(conn, row, sucursalId);
                    default -> "error";
                };
                if ("inserted".equals(result)) inserted++;
                else if ("updated".equals(result)) updated++;
                else errors++;
            } catch (Exception e) {
                errors++;
            }
        }
        return new int[]{inserted, updated, errors};
    }

    private String upsertProducto(Connection conn, Map<String, String> row, int sid)
            throws SQLException {
        return dbManager.upsert(conn, "productos",
            new String[]{"id", "sucursal_id"},
            new String[]{"id", "sucursal_id", "nombre", "precio", "stock", "categoria", "estado"},
            new Object[]{
                parseInt(row, "id"), sid, row.get("nombre"),
                parseDouble(row, "precio"), parseInt(row, "stock"),
                row.getOrDefault("categoria", ""), parseInt(row, "estado")
            });
    }

    private String upsertCliente(Connection conn, Map<String, String> row, int sid)
            throws SQLException {
        return dbManager.upsert(conn, "clientes",
            new String[]{"id", "sucursal_id"},
            new String[]{"id", "sucursal_id", "nombre", "dni", "telefono", "email", "estado"},
            new Object[]{
                parseInt(row, "id"), sid, row.get("nombre"), row.get("dni"),
                row.getOrDefault("telefono", ""), row.getOrDefault("email", ""),
                parseInt(row, "estado")
            });
    }

    private String upsertVenta(Connection conn, Map<String, String> row, int sid)
            throws SQLException {
        return dbManager.upsert(conn, "ventas",
            new String[]{"id", "sucursal_id"},
            new String[]{"id", "sucursal_id", "cliente_id", "fecha",
                         "subtotal", "igv", "total", "estado"},
            new Object[]{
                parseInt(row, "id"), sid, parseInt(row, "cliente_id"),
                row.get("fecha"), parseDouble(row, "subtotal"),
                parseDouble(row, "igv"), parseDouble(row, "total"),
                parseInt(row, "estado")
            });
    }

    private String upsertDetalle(Connection conn, Map<String, String> row, int sid)
            throws SQLException {
        return dbManager.upsert(conn, "detalle_ventas",
            new String[]{"id", "sucursal_id"},
            new String[]{"id", "sucursal_id", "venta_id", "producto_id",
                         "cantidad", "precio_unitario", "subtotal", "estado"},
            new Object[]{
                parseInt(row, "id"), sid, parseInt(row, "venta_id"),
                parseInt(row, "producto_id"), parseInt(row, "cantidad"),
                parseDouble(row, "precio_unitario"), parseDouble(row, "subtotal"),
                parseInt(row, "estado")
            });
    }

    // ════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ════════════════════════════════════════════════════════════════════════

    private String detectEntity(String filename) {
        String lower = filename.toLowerCase();
        for (String e : List.of("productos", "clientes", "ventas", "detalles")) {
            if (lower.startsWith(e)) return e;
        }
        return null;
    }

    private Set<String> getProcessedSet() {
        if (!Files.exists(processedFile)) return new HashSet<>();
        try {
            return new HashSet<>(Files.readAllLines(processedFile));
        } catch (IOException e) { return new HashSet<>(); }
    }

    private void markProcessed(String filename) {
        try (var pw = new FileWriter(processedFile.toFile(), true)) {
            pw.write(filename + System.lineSeparator());
        } catch (IOException e) {
            LOG.warning("No se pudo marcar como procesado: " + filename);
        }
    }

    private List<Map<String, String>> readCsv(Path path) throws IOException {
        List<Map<String, String>> rows = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(path)) {
            String headerLine = br.readLine();
            if (headerLine == null) return rows;
            String[] headers = headerLine.split(",");
            String line;
            while ((line = br.readLine()) != null) {
                String[] values = line.split(",", -1);
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < headers.length && i < values.length; i++) {
                    row.put(headers[i].strip(), values[i].strip());
                }
                rows.add(row);
            }
        }
        return rows;
    }

    private int    parseInt(Map<String, String> row, String key) {
        try { return Integer.parseInt(row.getOrDefault(key, "0").strip()); }
        catch (NumberFormatException e) { return 0; }
    }

    private double parseDouble(Map<String, String> row, String key) {
        try { return Double.parseDouble(row.getOrDefault(key, "0").strip()); }
        catch (NumberFormatException e) { return 0.0; }
    }

    private void log(String msg) {
        String ts = LocalDateTime.now().format(TS_FMT);
        String full = "[" + ts + "] " + msg;
        logCallback.accept(full);
    }

    // ── Setters de callbacks ──────────────────────────────────────────────────

    public void setLogCallback(Consumer<String> cb)    { this.logCallback = cb; }
    public void setStatusCallback(Consumer<String> cb) { this.statusCallback = cb; }
}

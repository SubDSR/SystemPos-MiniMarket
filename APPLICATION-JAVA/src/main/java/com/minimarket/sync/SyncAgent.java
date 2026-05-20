package com.minimarket.sync;

import com.minimarket.services.ClienteService;
import com.minimarket.services.ProductoService;
import com.minimarket.services.VentaService;
import com.minimarket.utils.Config;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Agente de sincronizacion del cliente POS.
 *
 * Proceso:
 *   1. Exporta los archivos .dat → .csv (productos, clientes, ventas, detalles)
 *   2. Copia los CSV a la carpeta UNC de red o al fallback local DATOS/
 *   3. Crea un MANIFEST_YYYYMMDD_HHMMSS.txt con metadatos
 *   4. Registra el resultado en el log
 *
 * Equivalente Java del script send.py de Python.
 */
public class SyncAgent {

    private static final Logger LOG = Logger.getLogger(SyncAgent.class.getName());
    private static final DateTimeFormatter TS_FMT =
        DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final DateTimeFormatter DISP_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ProductoService prodSvc  = new ProductoService();
    private final ClienteService  cliSvc   = new ClienteService();
    private final VentaService    ventaSvc = new VentaService();

    private Consumer<String> logCallback;

    public SyncAgent() {
        this.logCallback = LOG::info;
    }

    public SyncAgent(Consumer<String> logCallback) {
        this.logCallback = logCallback;
    }

    // ════════════════════════════════════════════════════════════════════════
    // SINCRONIZACION PRINCIPAL
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Ejecuta el ciclo completo de sincronizacion.
     *
     * @return Resultado de la operacion con estadisticas
     */
    public SyncResult sincronizar() {
        SyncResult result = new SyncResult();
        String timestamp = LocalDateTime.now().format(TS_FMT);
        log("Iniciando sincronizacion... [" + LocalDateTime.now().format(DISP_FMT) + "]");

        try {
            // Asegurar directorio de exportaciones
            Files.createDirectories(Config.EXPORTS_DIR);

            // ── 1. Exportar a CSV ────────────────────────────────────────────
            Path pCsv = Config.EXPORTS_DIR.resolve("productos_" + timestamp + ".csv");
            Path cCsv = Config.EXPORTS_DIR.resolve("clientes_"  + timestamp + ".csv");
            Path vCsv = Config.EXPORTS_DIR.resolve("ventas_"    + timestamp + ".csv");
            Path dCsv = Config.EXPORTS_DIR.resolve("detalles_"  + timestamp + ".csv");

            int nProd    = prodSvc.exportarCsv(pCsv);
            int nCli     = cliSvc.exportarCsv(cCsv);
            int nVentas  = ventaSvc.exportarVentasCsv(vCsv);
            int nDet     = ventaSvc.exportarDetallesCsv(dCsv);

            log(String.format("  Exportados: %d productos, %d clientes, %d ventas, %d detalles",
                nProd, nCli, nVentas, nDet));

            // ── 2. Determinar destino ────────────────────────────────────────
            Path targetDir = resolveTargetDir();
            log("  Destino: " + targetDir);
            Files.createDirectories(targetDir);

            // ── 3. Copiar CSV al destino ─────────────────────────────────────
            List<Path[]> filesToCopy = List.of(
                new Path[]{pCsv, targetDir.resolve(pCsv.getFileName())},
                new Path[]{cCsv, targetDir.resolve(cCsv.getFileName())},
                new Path[]{vCsv, targetDir.resolve(vCsv.getFileName())},
                new Path[]{dCsv, targetDir.resolve(dCsv.getFileName())}
            );

            List<String> copiedFiles = new ArrayList<>();
            for (Path[] pair : filesToCopy) {
                Files.copy(pair[0], pair[1], StandardCopyOption.REPLACE_EXISTING);
                long size = Files.size(pair[1]);
                copiedFiles.add(pair[1].getFileName() + "  (" + size + " bytes)");
                log("  Copiado: " + pair[0].getFileName() + " → " + targetDir);
                result.filesProcessed++;
            }

            // ── 4. Crear MANIFEST ────────────────────────────────────────────
            String manifestName = "MANIFEST_" + timestamp + ".txt";
            Path manifestPath   = targetDir.resolve(manifestName);
            writeManifest(manifestPath, timestamp, copiedFiles);
            log("  Manifest: " + manifestName);

            result.success = true;
            log("Sincronizacion completada exitosamente.");

        } catch (Exception e) {
            result.success = false;
            result.errorMessage = e.getMessage();
            log("ERROR en sincronizacion: " + e.getMessage());
            LOG.severe("Error en sincronizacion: " + e.getMessage());
        }

        return result;
    }

    // ════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ════════════════════════════════════════════════════════════════════════

    private Path resolveTargetDir() {
        // Intentar ruta UNC de red
        if (Files.isDirectory(Config.UNC_DATOS_PATH)) {
            return Config.UNC_DATOS_PATH;
        }
        // Fallback: carpeta local DATOS/
        return Config.DATOS_DIR;
    }

    private void writeManifest(Path manifestPath, String timestamp,
                               List<String> files) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(manifestPath))) {
            pw.println("MANIFEST DE SINCRONIZACION");
            pw.println("Fecha:    " + LocalDateTime.now().format(DISP_FMT));
            pw.println("Origen:   " + Config.HOSTNAME);
            pw.println("Archivos: " + files.size());
            for (String f : files) {
                pw.println("  " + f);
            }
        }
    }

    private void log(String msg) {
        if (logCallback != null) logCallback.accept(msg);
    }

    public void setLogCallback(Consumer<String> cb) { this.logCallback = cb; }

    // ════════════════════════════════════════════════════════════════════════
    // RESULTADO
    // ════════════════════════════════════════════════════════════════════════

    public static class SyncResult {
        public boolean success         = false;
        public int     filesProcessed  = 0;
        public String  errorMessage    = "";
    }
}

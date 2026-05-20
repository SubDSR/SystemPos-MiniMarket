package com.minimarket.utils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.*;

/**
 * Configuracion central del Sistema POS MiniMarket.
 *
 * Resolucion de BASE_DIR:
 *   - Si se define la variable de entorno MINIMARKET_HOME → se usa ese directorio.
 *   - En caso contrario → directorio padre de donde se ejecuta la aplicacion.
 */
public final class Config {

    // ════════════════════════════════════════════════════════════════════════
    // RUTAS BASE
    // ════════════════════════════════════════════════════════════════════════

    public static final Path BASE_DIR;

    static {
        String home = System.getenv("MINIMARKET_HOME");
        if (home != null && !home.isBlank()) {
            BASE_DIR = Path.of(home).toAbsolutePath();
        } else {
            // Ejecutar desde APPLICATION-JAVA/ → sube un nivel al raiz del proyecto
            BASE_DIR = Path.of(System.getProperty("user.dir"))
                           .toAbsolutePath()
                           .getParent();
        }
    }

    public static final Path APP_DIR      = BASE_DIR.resolve("APPLICATION");
    public static final Path DATA_DIR     = BASE_DIR.resolve("DATA");
    public static final Path DATOS_DIR    = BASE_DIR.resolve("DATOS");
    public static final Path SERVIDOR_DIR = BASE_DIR.resolve("SERVIDOR");

    // Subdirectorios de APPLICATION
    public static final Path LOGS_DIR    = BASE_DIR.resolve("APPLICATION-JAVA").resolve("logs");
    public static final Path EXPORTS_DIR = BASE_DIR.resolve("APPLICATION-JAVA").resolve("exports");

    // Subdirectorios del servidor
    public static final Path DB_DIR          = SERVIDOR_DIR.resolve("database");
    public static final Path SERVER_LOGS_DIR = SERVIDOR_DIR.resolve("logs");

    // ════════════════════════════════════════════════════════════════════════
    // ARCHIVOS DE DATOS BINARIOS (.dat)
    // ════════════════════════════════════════════════════════════════════════

    public static final Path PRODUCTOS_DAT  = DATA_DIR.resolve("productos.dat");
    public static final Path CLIENTES_DAT   = DATA_DIR.resolve("clientes.dat");
    public static final Path VENTAS_DAT     = DATA_DIR.resolve("ventas.dat");
    public static final Path DETALLES_DAT   = DATA_DIR.resolve("detalles.dat");

    // ════════════════════════════════════════════════════════════════════════
    // ARCHIVOS DE INDICE (.idx)
    // ════════════════════════════════════════════════════════════════════════

    public static final Path PRODUCTOS_IDX  = DATA_DIR.resolve("productos.idx");
    public static final Path CLIENTES_IDX   = DATA_DIR.resolve("clientes.idx");
    public static final Path VENTAS_IDX     = DATA_DIR.resolve("ventas.idx");
    public static final Path DETALLES_IDX   = DATA_DIR.resolve("detalles.idx");

    // ════════════════════════════════════════════════════════════════════════
    // ARCHIVOS DE ESPACIOS LIBRES (.holes)
    // ════════════════════════════════════════════════════════════════════════

    public static final Path PRODUCTOS_HOLES = DATA_DIR.resolve("productos.holes");
    public static final Path CLIENTES_HOLES  = DATA_DIR.resolve("clientes.holes");
    public static final Path VENTAS_HOLES    = DATA_DIR.resolve("ventas.holes");
    public static final Path DETALLES_HOLES  = DATA_DIR.resolve("detalles.holes");

    // ════════════════════════════════════════════════════════════════════════
    // BASE DE DATOS DEL SERVIDOR
    // ════════════════════════════════════════════════════════════════════════

    public static final Path SERVER_DB = DB_DIR.resolve("minimarket.db");

    // ════════════════════════════════════════════════════════════════════════
    // RED — Carpeta compartida UNC
    // ════════════════════════════════════════════════════════════════════════

    public static final String HOSTNAME;
    public static final String UNC_DATOS;
    public static final Path   UNC_DATOS_PATH;

    static {
        String host = "localhost";
        try { host = InetAddress.getLocalHost().getHostName(); }
        catch (UnknownHostException ignored) { }
        HOSTNAME      = host;
        UNC_DATOS     = "\\\\" + HOSTNAME + "\\DATOS";
        UNC_DATOS_PATH = Path.of(UNC_DATOS);
    }

    // ════════════════════════════════════════════════════════════════════════
    // CONSTANTES DE NEGOCIO
    // ════════════════════════════════════════════════════════════════════════

    public static final double IGV_RATE    = 0.18;
    public static final String APP_NAME    = "MiniMarket POS";
    public static final String APP_VERSION = "1.0.0";
    public static final String EMPRESA     = "MiniMarket El Ahorro S.A.C.";
    public static final String RUC         = "20123456789";

    // ════════════════════════════════════════════════════════════════════════
    // COLORES (equivalentes CSS para JavaFX)
    // ════════════════════════════════════════════════════════════════════════

    public static final String C_SIDEBAR_BG    = "#1e293b";
    public static final String C_SIDEBAR_FG    = "#cbd5e1";
    public static final String C_SIDEBAR_HOVER = "#334155";
    public static final String C_ACTIVE_BTN    = "#3b82f6";
    public static final String C_MAIN_BG       = "#f1f5f9";
    public static final String C_CARD_BG       = "#ffffff";
    public static final String C_ACCENT        = "#3b82f6";
    public static final String C_SUCCESS       = "#22c55e";
    public static final String C_ERROR         = "#ef4444";
    public static final String C_WARNING       = "#f59e0b";
    public static final String C_TEXT          = "#0f172a";
    public static final String C_TEXT_MUTED    = "#64748b";
    public static final String C_BORDER        = "#e2e8f0";

    private Config() { }

    /** Crea todos los directorios necesarios si no existen. */
    public static void initDirectories() {
        Path[] dirs = {
            DATA_DIR, DATOS_DIR, LOGS_DIR, EXPORTS_DIR,
            DB_DIR, SERVER_LOGS_DIR
        };
        for (Path dir : dirs) {
            try { Files.createDirectories(dir); }
            catch (Exception ignored) { }
        }
    }
}

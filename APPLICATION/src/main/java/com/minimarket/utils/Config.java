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
            // Ejecutar desde APPLICATION/ → sube un nivel al raiz del proyecto
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
    public static final Path LOGS_DIR    = APP_DIR.resolve("logs");
    public static final Path EXPORTS_DIR = APP_DIR.resolve("exports");

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
    // COLORES (java.awt.Color para Swing)
    // ════════════════════════════════════════════════════════════════════════

    public static final java.awt.Color C_SIDEBAR_BG    = new java.awt.Color(15,  23,  42);
    public static final java.awt.Color C_SIDEBAR_NAV   = new java.awt.Color(30,  41,  59);
    public static final java.awt.Color C_SIDEBAR_FG    = new java.awt.Color(203, 213, 225);
    public static final java.awt.Color C_SIDEBAR_HOVER = new java.awt.Color(51,  65,  85);
    public static final java.awt.Color C_ACTIVE_BTN    = new java.awt.Color(59,  130, 246);
    public static final java.awt.Color C_MAIN_BG       = new java.awt.Color(241, 245, 249);
    public static final java.awt.Color C_CARD_BG       = java.awt.Color.WHITE;
    public static final java.awt.Color C_ACCENT        = new java.awt.Color(59,  130, 246);
    public static final java.awt.Color C_SUCCESS       = new java.awt.Color(34,  197, 94);
    public static final java.awt.Color C_ERROR         = new java.awt.Color(239, 68,  68);
    public static final java.awt.Color C_WARNING       = new java.awt.Color(245, 158, 11);
    public static final java.awt.Color C_TEXT          = new java.awt.Color(15,  23,  42);
    public static final java.awt.Color C_TEXT_MUTED    = new java.awt.Color(100, 116, 139);
    public static final java.awt.Color C_BORDER        = new java.awt.Color(226, 232, 240);

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

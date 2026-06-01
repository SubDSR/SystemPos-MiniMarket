package com.minimarket.client.utils;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Configuracion central del cliente POS delgado. */
public final class Config {

    public static final Path BASE_DIR;

    static {
        String home = System.getenv("MINIMARKET_HOME");
        BASE_DIR = home != null && !home.isBlank()
            ? Path.of(home).toAbsolutePath()
            : resolveBaseDir();
    }

    private static Path resolveBaseDir() {
        try {
            Path codeSource = Path.of(Config.class.getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI())
                .toAbsolutePath();

            if (Files.isRegularFile(codeSource)) {
                Path parent = codeSource.getParent();
                if (parent != null && parent.getFileName() != null
                        && parent.getFileName().toString().equalsIgnoreCase("dist")
                        && parent.getParent() != null
                        && parent.getParent().getFileName() != null
                        && parent.getParent().getFileName().toString().equalsIgnoreCase("SERVIDOR_APLICACIONES")) {
                    return parent.getParent().getParent();
                }
            }
        } catch (URISyntaxException | NullPointerException | SecurityException ignored) {
        }

        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        String name = cwd.getFileName() != null ? cwd.getFileName().toString() : "";
        if (name.equalsIgnoreCase("CLIENTE_POS") || name.equalsIgnoreCase("SERVIDOR_APLICACIONES")) {
            return cwd.getParent();
        }
        if (name.equalsIgnoreCase("dist") && cwd.getParent() != null
                && cwd.getParent().getFileName() != null
                && cwd.getParent().getFileName().toString().equalsIgnoreCase("SERVIDOR_APLICACIONES")) {
            return cwd.getParent().getParent();
        }
        return cwd;
    }

    public static final String APP_SERVER_HOST = envOrDefault("MINIMARKET_APP_HOST", "localhost");
    public static final int    APP_SERVER_PORT = parsePort(envOrDefault("MINIMARKET_APP_PORT", "9090"));
    public static final Path   LOGS_DIR = BASE_DIR.resolve("CLIENTE_POS").resolve("logs");
    public static final int    DEFAULT_SUCURSAL_ID = 1;

    public static final double IGV_RATE    = 0.18;
    public static final String APP_NAME    = "MiniMarket POS";
    public static final String APP_VERSION = "2.0.0";
    public static final String EMPRESA     = "MiniMarket El Ahorro S.A.C.";
    public static final String RUC         = "20123456789";

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

    public static void initDirectories() {
        try {
            Files.createDirectories(LOGS_DIR);
        } catch (Exception ignored) {
        }
    }

    private static String envOrDefault(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value.strip();
    }

    private static int parsePort(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            return 9090;
        }
    }
}

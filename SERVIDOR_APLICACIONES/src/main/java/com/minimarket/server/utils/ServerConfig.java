package com.minimarket.server.utils;

import java.nio.file.Path;
import java.nio.file.Files;
import java.net.URISyntaxException;

public final class ServerConfig {
    public static final Path BASE_DIR = resolveBaseDir();
    public static final int  PORT = parsePort(System.getenv("MINIMARKET_APP_PORT"), 9090);
    public static final Path DB_PATH = BASE_DIR.resolve("SERVIDOR_DATOS").resolve("minimarket.db");
    public static final Path LOG_DIR = BASE_DIR.resolve("SERVIDOR_APLICACIONES").resolve("logs");

    private ServerConfig() { }

    private static Path resolveBaseDir() {
        String home = System.getenv("MINIMARKET_HOME");
        if (home != null && !home.isBlank()) return Path.of(home).toAbsolutePath();

        try {
            Path codeSource = Path.of(ServerConfig.class.getProtectionDomain()
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
        if (name.equalsIgnoreCase("SERVIDOR_APLICACIONES")) return cwd.getParent();
        if (name.equalsIgnoreCase("dist") && cwd.getParent() != null
                && cwd.getParent().getFileName() != null
                && cwd.getParent().getFileName().toString().equalsIgnoreCase("SERVIDOR_APLICACIONES")) {
            return cwd.getParent().getParent();
        }
        return cwd;
    }

    private static int parsePort(String value, int fallback) {
        try {
            return value == null || value.isBlank() ? fallback : Integer.parseInt(value.strip());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}

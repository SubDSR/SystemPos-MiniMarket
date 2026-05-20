package com.minimarket.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.logging.*;

/**
 * Sistema de logging para el cliente POS.
 * Escribe a consola y a un archivo diario: logs/pos_YYYY-MM-DD.log
 */
public final class AppLogger {

    private static boolean initialized = false;

    private AppLogger() { }

    /** Inicializa el sistema de logging. Llamar una vez al arrancar la app. */
    public static void init() {
        if (initialized) return;
        initialized = true;

        try {
            Files.createDirectories(Config.LOGS_DIR);

            String fechaStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            Path logFile = Config.LOGS_DIR.resolve("pos_" + fechaStr + ".log");

            Logger rootLogger = Logger.getLogger("");
            rootLogger.setLevel(Level.INFO);

            // Eliminar handlers existentes
            for (Handler h : rootLogger.getHandlers()) {
                rootLogger.removeHandler(h);
            }

            // Handler de archivo
            FileHandler fileHandler = new FileHandler(logFile.toString(), true);
            fileHandler.setLevel(Level.INFO);
            fileHandler.setFormatter(new PosFormatter());
            rootLogger.addHandler(fileHandler);

            // Handler de consola
            ConsoleHandler consoleHandler = new ConsoleHandler();
            consoleHandler.setLevel(Level.INFO);
            consoleHandler.setFormatter(new PosFormatter());
            rootLogger.addHandler(consoleHandler);

        } catch (IOException e) {
            System.err.println("No se pudo inicializar el logger: " + e.getMessage());
        }
    }

    /** Retorna un Logger con el nombre del modulo dado. */
    public static Logger getLogger(String module) {
        return Logger.getLogger(module);
    }

    // ── Formateador personalizado ─────────────────────────────────────────────

    private static class PosFormatter extends Formatter {
        private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        @Override
        public String format(LogRecord record) {
            String level  = String.format("%-8s", record.getLevel().getName());
            String module = record.getLoggerName();
            if (module.length() > 22) module = module.substring(module.length() - 22);
            module = String.format("%-22s", module);
            return String.format("%s | %s | %s | %s%n",
                java.time.LocalDateTime.now().format(FMT),
                level, module, record.getMessage());
        }
    }
}

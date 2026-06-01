package com.minimarket.server.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public final class ServerLogger {
    private static boolean initialized = false;

    private ServerLogger() { }

    public static void init() {
        if (initialized) return;
        initialized = true;

        try {
            Files.createDirectories(ServerConfig.LOG_DIR);
            String fecha = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            Path logFile = ServerConfig.LOG_DIR.resolve("server_" + fecha + ".log");

            Logger root = Logger.getLogger("");
            root.setLevel(Level.INFO);
            for (Handler handler : root.getHandlers()) {
                root.removeHandler(handler);
            }

            FileHandler file = new FileHandler(logFile.toString(), true);
            file.setLevel(Level.INFO);
            file.setFormatter(new ServerFormatter());
            root.addHandler(file);

            ConsoleHandler console = new ConsoleHandler();
            console.setLevel(Level.INFO);
            console.setFormatter(new ServerFormatter());
            root.addHandler(console);
        } catch (IOException e) {
            System.err.println("No se pudo inicializar el logger del servidor: " + e.getMessage());
        }
    }

    public static Logger getLogger(String module) {
        return Logger.getLogger(module);
    }

    private static class ServerFormatter extends Formatter {
        private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        @Override
        public String format(LogRecord record) {
            String level = String.format("%-7s", record.getLevel().getName());
            String module = String.format("%-14s", record.getLoggerName());
            return String.format("%s | %s | %s | %s%n",
                LocalDateTime.now().format(FMT), level, module, record.getMessage());
        }
    }
}

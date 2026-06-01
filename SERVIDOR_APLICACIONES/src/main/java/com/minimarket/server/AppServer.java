package com.minimarket.server;

import com.minimarket.server.database.DatabaseManager;
import com.minimarket.server.utils.ServerConfig;
import com.minimarket.server.utils.ServerLogger;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Logger;

/** Main-Class del servidor de aplicaciones multihilo. */
public class AppServer {
    private static final Logger LOG = ServerLogger.getLogger("AppServer");

    public static void main(String[] args) throws Exception {
        ServerLogger.init();
        DatabaseManager db = new DatabaseManager(ServerConfig.DB_PATH);
        db.initDatabase();

        InetAddress ia = InetAddress.getLocalHost();
        LOG.info("Iniciando servidor TCP en " + ia.getHostName()
            + " (" + ia.getHostAddress() + ") puerto " + ServerConfig.PORT);
        LOG.info("Base de datos operativa: " + ServerConfig.DB_PATH);
        LOG.info("Logs del servidor: " + ServerConfig.LOG_DIR);

        try (ServerSocket ss = new ServerSocket(ServerConfig.PORT)) {
            LOG.info("AppServer listo. Esperando peticiones de clientes POS...");
            while (true) {
                Socket c = ss.accept();
                new Thread(new ClientHandler(c, db), "client-" + c.getPort()).start();
            }
        }
    }
}

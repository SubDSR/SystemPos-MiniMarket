package com.minimarket.client.socket;

import com.minimarket.client.utils.Config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.StringJoiner;

/** Canal TCP/IP del cliente POS hacia el servidor de aplicaciones. */
public final class SocketClient {

    private SocketClient() { }

    public static String enviar(String peticion) throws IOException {
        try (Socket s = new Socket(Config.APP_SERVER_HOST, Config.APP_SERVER_PORT);
             PrintWriter out = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8))) {
            out.println(peticion);
            return in.readLine();
        }
    }

    public static String enviarPartes(String... partes) throws IOException {
        StringJoiner joiner = new StringJoiner("|");
        for (String parte : partes) {
            validarCampo(parte);
            joiner.add(parte == null ? "" : parte);
        }
        return enviar(joiner.toString());
    }

    public static void validarCampo(String campo) {
        if (campo != null && campo.contains("|")) {
            throw new IllegalArgumentException("Caracter no permitido: |");
        }
    }
}

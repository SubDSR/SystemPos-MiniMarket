package com.minimarket.client.services;

import com.minimarket.client.models.Sucursal;
import com.minimarket.client.socket.SocketClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Stub del cliente para consultar sucursales activas desde el AppServer. */
public class SucursalService {

    public List<Sucursal> listar() {
        return parseSucursales(request("LISTAR", "SUCURSAL"));
    }

    public String nombrePorId(int sucursalId) {
        return listar().stream()
            .filter(s -> s.getId() == sucursalId)
            .map(Sucursal::getNombre)
            .findFirst()
            .orElse("Sucursal #" + sucursalId);
    }

    private static String request(String... partes) {
        try {
            return requireOk(SocketClient.enviarPartes(partes));
        } catch (IOException ex) {
            throw new IllegalArgumentException("Servidor no disponible: " + ex.getMessage(), ex);
        }
    }

    private static String requireOk(String response) {
        if (response == null) throw new IllegalArgumentException("Sin respuesta del servidor.");
        String[] parts = response.split("\\|", 3);
        if (parts.length > 0 && "OK".equals(parts[0])) return parts.length > 1 ? parts[1] : "";
        if (parts.length >= 3) throw new IllegalArgumentException(parts[2]);
        throw new IllegalArgumentException(response);
    }

    private static List<Sucursal> parseSucursales(String data) {
        List<Sucursal> sucursales = new ArrayList<>();
        if (data == null || data.isBlank()) return sucursales;
        for (String row : data.split(";")) {
            if (!row.isBlank()) sucursales.add(parseSucursal(row));
        }
        return sucursales;
    }

    private static Sucursal parseSucursal(String row) {
        String[] s = row.split("~", -1);
        if (s.length < 4) throw new IllegalArgumentException("Respuesta de sucursal invalida.");
        return new Sucursal(Integer.parseInt(s[0]), s[1], s[2], Integer.parseInt(s[3]));
    }
}

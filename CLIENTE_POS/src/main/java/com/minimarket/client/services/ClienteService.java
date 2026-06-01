package com.minimarket.client.services;

import com.minimarket.client.models.Cliente;
import com.minimarket.client.socket.SocketClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Stub del cliente: todo CRUD viaja por socket al AppServer. */
public class ClienteService {

    public Cliente crear(String nombre, String dni, String telefono, String email) {
        validarCliente(nombre, dni);
        String data = request("CREAR", "CLIENTE", nombre.strip(), dni.strip(),
            telefono != null ? telefono.strip() : "", email != null ? email.strip() : "");
        return obtener(Integer.parseInt(data));
    }

    public Cliente obtener(int clienteId) {
        try {
            return parseCliente(request("BUSCAR", "CLIENTE", String.valueOf(clienteId)));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public List<Cliente> listar() {
        return parseClientes(request("LISTAR", "CLIENTE"));
    }

    public List<Cliente> buscar(String termino) {
        String t = termino == null ? "" : termino.toLowerCase().strip();
        if (t.isEmpty()) return listar();
        return listar().stream()
            .filter(c -> c.getNombre().toLowerCase().contains(t)
                      || c.getDni().toLowerCase().contains(t))
            .toList();
    }

    public Cliente buscarPorDni(String dni) {
        if (dni == null) return null;
        String clean = dni.strip();
        return listar().stream()
            .filter(c -> c.getDni().equals(clean))
            .findFirst()
            .orElse(null);
    }

    public Cliente actualizar(int clienteId, String nombre, String dni,
                              String telefono, String email) {
        validarCliente(nombre, dni);
        request("ACTUALIZAR", "CLIENTE", String.valueOf(clienteId), nombre.strip(), dni.strip(),
            telefono != null ? telefono.strip() : "", email != null ? email.strip() : "");
        return obtener(clienteId);
    }

    public boolean eliminar(int clienteId) {
        request("ELIMINAR", "CLIENTE", String.valueOf(clienteId));
        return true;
    }

    public int contar() { return listar().size(); }

    private static void validarCliente(String nombre, String dni) {
        if (nombre == null || nombre.isBlank())
            throw new IllegalArgumentException("El nombre del cliente no puede estar vacio.");
        if (dni == null || dni.isBlank())
            throw new IllegalArgumentException("El DNI no puede estar vacio.");
        SocketClient.validarCampo(nombre);
        SocketClient.validarCampo(dni);
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

    private static List<Cliente> parseClientes(String data) {
        List<Cliente> clientes = new ArrayList<>();
        if (data == null || data.isBlank()) return clientes;
        for (String row : data.split(";")) {
            if (!row.isBlank()) clientes.add(parseCliente(row));
        }
        return clientes;
    }

    private static Cliente parseCliente(String row) {
        String[] c = row.split("~", -1);
        if (c.length < 6) throw new IllegalArgumentException("Respuesta de cliente invalida.");
        return new Cliente(Integer.parseInt(c[0]), c[1], c[2], c[3], c[4], Integer.parseInt(c[5]));
    }
}

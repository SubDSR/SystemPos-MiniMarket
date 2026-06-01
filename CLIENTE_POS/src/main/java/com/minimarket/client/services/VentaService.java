package com.minimarket.client.services;

import com.minimarket.client.models.DetalleVenta;
import com.minimarket.client.models.Venta;
import com.minimarket.client.socket.SocketClient;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/** Stub del cliente: las ventas se registran solo en el AppServer. */
public class VentaService {

    public Venta crearVenta(List<int[]> items, int clienteId) {
        return crearVenta(items, clienteId, 1);
    }

    public Venta crearVenta(List<int[]> items, int clienteId, int sucursalId) {
        if (items == null || items.isEmpty()) return null;
        StringJoiner joiner = new StringJoiner(";");
        for (int[] item : items) {
            if (item.length < 2 || item[1] <= 0) return null;
            joiner.add(item[0] + ":" + item[1]);
        }
        String data = request("REGISTRAR", "VENTA", String.valueOf(clienteId),
            String.valueOf(sucursalId), joiner.toString());
        return obtenerVenta(Integer.parseInt(data));
    }

    public Venta obtenerVenta(int ventaId) {
        try {
            return parseVenta(request("BUSCAR", "VENTA", String.valueOf(ventaId)));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public List<Venta> listarVentas() {
        return parseVentas(request("HISTORIAL", "VENTA"));
    }

    public List<DetalleVenta> listarDetalles() {
        return parseDetalles(request("DETALLES", "VENTA"));
    }

    public int contarVentas() { return listarVentas().size(); }

    public List<DetalleVenta> obtenerDetalles(int ventaId) {
        return parseDetalles(request("DETALLES", "VENTA", String.valueOf(ventaId)));
    }

    public List<Venta> ventasHoy() {
        String hoy = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        return listarVentas().stream()
            .filter(v -> v.getFecha().startsWith(hoy))
            .toList();
    }

    public double totalHoy() {
        double sum = ventasHoy().stream().mapToDouble(Venta::getTotal).sum();
        return Math.round(sum * 100.0) / 100.0;
    }

    public boolean anularVenta(int ventaId) {
        request("ANULAR", "VENTA", String.valueOf(ventaId));
        return true;
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

    private static List<Venta> parseVentas(String data) {
        List<Venta> ventas = new ArrayList<>();
        if (data == null || data.isBlank()) return ventas;
        for (String row : data.split(";")) {
            if (!row.isBlank()) ventas.add(parseVenta(row));
        }
        return ventas;
    }

    private static Venta parseVenta(String row) {
        String[] v = row.split("~", -1);
        if (v.length < 8) throw new IllegalArgumentException("Respuesta de venta invalida.");
        return new Venta(Integer.parseInt(v[0]), Integer.parseInt(v[1]), Integer.parseInt(v[2]), v[3],
            Double.parseDouble(v[4]), Double.parseDouble(v[5]), Double.parseDouble(v[6]), Integer.parseInt(v[7]));
    }

    private static List<DetalleVenta> parseDetalles(String data) {
        List<DetalleVenta> detalles = new ArrayList<>();
        if (data == null || data.isBlank()) return detalles;
        for (String row : data.split(";")) {
            if (!row.isBlank()) detalles.add(parseDetalle(row));
        }
        return detalles;
    }

    private static DetalleVenta parseDetalle(String row) {
        String[] d = row.split("~", -1);
        if (d.length < 7) throw new IllegalArgumentException("Respuesta de detalle invalida.");
        return new DetalleVenta(Integer.parseInt(d[0]), Integer.parseInt(d[1]), Integer.parseInt(d[2]),
            Integer.parseInt(d[3]), Double.parseDouble(d[4]), Double.parseDouble(d[5]), Integer.parseInt(d[6]));
    }
}

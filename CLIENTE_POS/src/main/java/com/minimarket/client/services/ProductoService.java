package com.minimarket.client.services;

import com.minimarket.client.models.Producto;
import com.minimarket.client.socket.SocketClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Stub del cliente: todo CRUD viaja por socket al AppServer. */
public class ProductoService {

    public Producto crear(String nombre, double precio, int stock, String categoria) {
        validarProducto(nombre, precio, stock);
        String data = request("CREAR", "PRODUCTO", nombre.strip(), String.valueOf(precio),
            String.valueOf(stock), categoria != null ? categoria.strip() : "");
        return obtener(Integer.parseInt(data));
    }

    public Producto obtener(int productoId) {
        try {
            return parseProducto(request("BUSCAR", "PRODUCTO", String.valueOf(productoId)));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public List<Producto> listar() {
        return parseProductos(request("LISTAR", "PRODUCTO"));
    }

    public List<Producto> buscar(String termino) {
        String t = termino == null ? "" : termino.toLowerCase().strip();
        if (t.isEmpty()) return listar();
        return listar().stream()
            .filter(p -> p.getNombre().toLowerCase().contains(t)
                      || p.getCategoria().toLowerCase().contains(t))
            .toList();
    }

    public Producto actualizar(int productoId, String nombre, Double precio,
                               Integer stock, String categoria) {
        validarProducto(nombre, precio != null ? precio : 0.0, stock != null ? stock : 0);
        request("ACTUALIZAR", "PRODUCTO", String.valueOf(productoId), nombre.strip(),
            String.valueOf(precio), String.valueOf(stock), categoria != null ? categoria.strip() : "");
        return obtener(productoId);
    }

    public boolean descontarStock(int productoId, int cantidad) {
        throw new UnsupportedOperationException("El stock solo se modifica en el AppServer.");
    }

    public boolean restaurarStock(int productoId, int cantidad) {
        throw new UnsupportedOperationException("El stock solo se modifica en el AppServer.");
    }

    public boolean eliminar(int productoId) {
        request("ELIMINAR", "PRODUCTO", String.valueOf(productoId));
        return true;
    }

    public int contar() { return listar().size(); }

    public int compactar() { return 0; }

    private static void validarProducto(String nombre, double precio, int stock) {
        if (nombre == null || nombre.isBlank())
            throw new IllegalArgumentException("El nombre del producto no puede estar vacio.");
        if (precio < 0)
            throw new IllegalArgumentException("El precio no puede ser negativo.");
        if (stock < 0)
            throw new IllegalArgumentException("El stock no puede ser negativo.");
        SocketClient.validarCampo(nombre);
    }

    private static String request(String... partes) {
        try {
            String response = SocketClient.enviarPartes(partes);
            return requireOk(response);
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

    private static List<Producto> parseProductos(String data) {
        List<Producto> productos = new ArrayList<>();
        if (data == null || data.isBlank()) return productos;
        for (String row : data.split(";")) {
            if (!row.isBlank()) productos.add(parseProducto(row));
        }
        return productos;
    }

    private static Producto parseProducto(String row) {
        String[] p = row.split("~", -1);
        if (p.length < 6) throw new IllegalArgumentException("Respuesta de producto invalida.");
        return new Producto(Integer.parseInt(p[0]), p[1], Double.parseDouble(p[2]),
            Integer.parseInt(p[3]), p[4], Integer.parseInt(p[5]));
    }
}

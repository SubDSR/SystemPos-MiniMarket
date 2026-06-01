package com.minimarket.server;

import com.minimarket.server.database.DatabaseManager;
import com.minimarket.server.models.Cliente;
import com.minimarket.server.models.DetalleVenta;
import com.minimarket.server.models.Producto;
import com.minimarket.server.models.Sucursal;
import com.minimarket.server.models.Venta;
import com.minimarket.server.services.ClienteService;
import com.minimarket.server.services.ProductoService;
import com.minimarket.server.services.SucursalService;
import com.minimarket.server.services.VentaService;
import com.minimarket.server.utils.ServerLogger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.logging.Logger;

/** Atiende una conexion TCP del cliente POS. */
public class ClientHandler implements Runnable {
    private static final Logger LOG = ServerLogger.getLogger("ClientHandler");

    private final Socket socket;
    private final ProductoService productos;
    private final ClienteService clientes;
    private final SucursalService sucursales;
    private final VentaService ventas;

    public ClientHandler(Socket socket, DatabaseManager db) {
        this.socket = socket;
        this.productos = new ProductoService(db);
        this.clientes = new ClienteService(db);
        this.sucursales = new SucursalService(db);
        this.ventas = new VentaService(db);
    }

    @Override
    public void run() {
        String remote = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
        long start = System.currentTimeMillis();
        try (socket;
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {
            String peticion = reader.readLine();
            String respuesta = handle(peticion);
            writer.println(respuesta);
            logRequest(remote, peticion, respuesta, System.currentTimeMillis() - start);
        } catch (Exception e) {
            LOG.warning("ip=" + remote + " hilo=" + Thread.currentThread().getName()
                + " estado=ERROR excepcion=\"" + e.getMessage() + "\"");
        }
    }

    private void logRequest(String remote, String peticion, String respuesta, long elapsedMs) {
        String[] requestParts = peticion == null ? new String[0] : peticion.split("\\|", -1);
        String op = requestParts.length > 0 ? requestParts[0] : "VACIA";
        String entidad = requestParts.length > 1 ? requestParts[1] : "-";
        String estado = respuesta != null && respuesta.startsWith("OK|") ? "OK" : "ERROR";
        String codigo = extraerCodigo(respuesta);
        LOG.info("ip=" + remote
            + " hilo=" + Thread.currentThread().getName()
            + " op=" + op
            + " entidad=" + entidad
            + " estado=" + estado
            + " codigo=" + codigo
            + " ms=" + elapsedMs);
    }

    private static String extraerCodigo(String respuesta) {
        if (respuesta == null || respuesta.isBlank()) return "SIN_RESPUESTA";
        String[] parts = respuesta.split("\\|", 3);
        if (parts.length == 0) return "SIN_CODIGO";
        if ("OK".equals(parts[0])) return "OK";
        return parts.length > 1 ? parts[1] : parts[0];
    }

    private String handle(String peticion) {
        if (peticion == null || peticion.isBlank()) return "ERROR|VALIDATION|Peticion vacia";
        String[] parts = peticion.split("\\|", -1);
        try {
            return switch (parts[0].toUpperCase()) {
                case "PING" -> require(parts, 1) ? "OK|pong" : validation("PING no recibe campos");
                case "LISTAR" -> listar(parts);
                case "BUSCAR" -> buscar(parts);
                case "CREAR" -> crear(parts);
                case "ACTUALIZAR" -> actualizar(parts);
                case "ELIMINAR" -> eliminar(parts);
                case "REGISTRAR" -> registrar(parts);
                case "HISTORIAL" -> historial(parts);
                case "DETALLES" -> detalles(parts);
                case "ANULAR" -> anular(parts);
                default -> "ERROR|UNKNOWN|Operacion desconocida";
            };
        } catch (NumberFormatException e) {
            return "ERROR|VALIDATION|Numero invalido: " + e.getMessage();
        } catch (IllegalArgumentException e) {
            return "ERROR|VALIDATION|" + e.getMessage();
        } catch (SQLException e) {
            return "ERROR|DB|" + e.getMessage();
        } catch (Exception e) {
            return "ERROR|SERVER|" + e.getMessage();
        }
    }

    private String listar(String[] parts) throws SQLException {
        if (!require(parts, 2)) return validation("Formato: LISTAR|ENTIDAD");
        return switch (parts[1].toUpperCase()) {
            case "PRODUCTO" -> "OK|" + serializarProductos(productos.listar());
            case "CLIENTE" -> "OK|" + serializarClientes(clientes.listar());
            case "SUCURSAL" -> "OK|" + serializarSucursales(sucursales.listar());
            default -> "ERROR|UNKNOWN|Entidad no soportada";
        };
    }

    private String buscar(String[] parts) throws SQLException {
        if (!require(parts, 3)) return validation("Formato: BUSCAR|ENTIDAD|id");
        int id = Integer.parseInt(parts[2]);
        return switch (parts[1].toUpperCase()) {
            case "PRODUCTO" -> {
                Producto p = productos.buscar(id);
                yield p == null ? "ERROR|NOT_FOUND|Producto no encontrado" : "OK|" + serializarProducto(p);
            }
            case "CLIENTE" -> {
                Cliente c = clientes.buscar(id);
                yield c == null ? "ERROR|NOT_FOUND|Cliente no encontrado" : "OK|" + serializarCliente(c);
            }
            case "VENTA" -> {
                Venta v = ventas.buscar(id);
                yield v == null ? "ERROR|NOT_FOUND|Venta no encontrada" : "OK|" + serializarVenta(v);
            }
            default -> "ERROR|UNKNOWN|Entidad no soportada";
        };
    }

    private String crear(String[] parts) throws SQLException {
        return switch (parts.length > 1 ? parts[1].toUpperCase() : "") {
            case "PRODUCTO" -> {
                if (!require(parts, 6)) yield validation("Formato: CREAR|PRODUCTO|nombre|precio|stock|categoria");
                validarTexto(parts[2], parts[5]);
                int id = productos.crear(parts[2], Double.parseDouble(parts[3]), Integer.parseInt(parts[4]), parts[5]);
                yield "OK|" + id;
            }
            case "CLIENTE" -> {
                if (!(parts.length == 4 || parts.length == 6)) yield validation("Formato: CREAR|CLIENTE|nombres|documento");
                validarTexto(parts[2], parts[3]);
                String telefono = parts.length == 6 ? parts[4] : "";
                String email = parts.length == 6 ? parts[5] : "";
                validarTexto(telefono, email);
                int id = clientes.crear(parts[2], parts[3], telefono, email);
                yield "OK|" + id;
            }
            default -> "ERROR|UNKNOWN|Entidad no soportada";
        };
    }

    private String actualizar(String[] parts) throws SQLException {
        return switch (parts.length > 1 ? parts[1].toUpperCase() : "") {
            case "PRODUCTO" -> {
                if (!require(parts, 7)) yield validation("Formato: ACTUALIZAR|PRODUCTO|id|nombre|precio|stock|categoria");
                validarTexto(parts[3], parts[6]);
                productos.actualizar(Integer.parseInt(parts[2]), parts[3], Double.parseDouble(parts[4]),
                    Integer.parseInt(parts[5]), parts[6]);
                yield "OK|actualizado";
            }
            case "CLIENTE" -> {
                if (!require(parts, 7)) yield validation("Formato: ACTUALIZAR|CLIENTE|id|nombre|dni|telefono|email");
                validarTexto(parts[3], parts[4], parts[5], parts[6]);
                clientes.actualizar(Integer.parseInt(parts[2]), parts[3], parts[4], parts[5], parts[6]);
                yield "OK|actualizado";
            }
            default -> "ERROR|UNKNOWN|Entidad no soportada";
        };
    }

    private String eliminar(String[] parts) throws SQLException {
        if (!require(parts, 3)) return validation("Formato: ELIMINAR|ENTIDAD|id");
        int id = Integer.parseInt(parts[2]);
        return switch (parts[1].toUpperCase()) {
            case "PRODUCTO" -> { productos.eliminar(id); yield "OK|eliminado"; }
            case "CLIENTE" -> { clientes.eliminar(id); yield "OK|eliminado"; }
            default -> "ERROR|UNKNOWN|Entidad no soportada";
        };
    }

    private String registrar(String[] parts) {
        if (!require(parts, 5) || !"VENTA".equalsIgnoreCase(parts[1])) {
            return validation("Formato: REGISTRAR|VENTA|clienteId|sucursalId|prodId:cantidad;prodId:cantidad");
        }
        int clienteId = Integer.parseInt(parts[2]);
        int sucursalId = Integer.parseInt(parts[3]);
        return ventas.registrar(clienteId, sucursalId, parseItems(parts[4]));
    }

    private String historial(String[] parts) throws SQLException {
        if (!require(parts, 2) || !"VENTA".equalsIgnoreCase(parts[1])) return validation("Formato: HISTORIAL|VENTA");
        return "OK|" + serializarVentas(ventas.listar());
    }

    private String detalles(String[] parts) throws SQLException {
        if (!(parts.length == 2 || parts.length == 3) || !"VENTA".equalsIgnoreCase(parts[1])) {
            return validation("Formato: DETALLES|VENTA|ventaId");
        }
        Integer ventaId = parts.length == 3 ? Integer.parseInt(parts[2]) : null;
        return "OK|" + serializarDetalles(ventas.detalles(ventaId));
    }

    private String anular(String[] parts) throws SQLException {
        if (!require(parts, 3) || !"VENTA".equalsIgnoreCase(parts[1])) return validation("Formato: ANULAR|VENTA|id");
        ventas.anular(Integer.parseInt(parts[2]));
        return "OK|anulada";
    }

    private Map<Integer, Integer> parseItems(String raw) {
        Map<Integer, Integer> items = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("Items vacios");
        for (String token : raw.split(";")) {
            if (token.isBlank()) continue;
            String[] kv = token.split(":", -1);
            if (kv.length != 2) throw new IllegalArgumentException("Item invalido: " + token);
            int productoId = Integer.parseInt(kv[0]);
            int cantidad = Integer.parseInt(kv[1]);
            items.merge(productoId, cantidad, Integer::sum);
        }
        return items;
    }

    private static boolean require(String[] parts, int expected) {
        return parts.length == expected;
    }

    private static String validation(String msg) {
        return "ERROR|VALIDATION|" + msg;
    }

    private static void validarTexto(String... campos) {
        for (String campo : campos) {
            if (campo != null && (campo.contains("|") || campo.contains(";") || campo.contains("~"))) {
                throw new IllegalArgumentException("Caracter de protocolo no permitido");
            }
        }
    }

    private static String serializarProductos(List<Producto> lista) {
        StringJoiner joiner = new StringJoiner(";");
        for (Producto p : lista) joiner.add(serializarProducto(p));
        return joiner.toString();
    }

    private static String serializarProducto(Producto p) {
        return p.getId() + "~" + p.getNombre() + "~" + p.getPrecio() + "~" + p.getStock()
            + "~" + p.getCategoria() + "~" + p.getEstado();
    }

    private static String serializarClientes(List<Cliente> lista) {
        StringJoiner joiner = new StringJoiner(";");
        for (Cliente c : lista) joiner.add(serializarCliente(c));
        return joiner.toString();
    }

    private static String serializarCliente(Cliente c) {
        return c.getId() + "~" + c.getNombre() + "~" + c.getDni() + "~" + c.getTelefono()
            + "~" + c.getEmail() + "~" + c.getEstado();
    }

    private static String serializarSucursales(List<Sucursal> lista) {
        StringJoiner joiner = new StringJoiner(";");
        for (Sucursal s : lista) {
            joiner.add(s.getId() + "~" + s.getNombre() + "~" + s.getDireccion() + "~" + s.getActivo());
        }
        return joiner.toString();
    }

    private static String serializarVentas(List<Venta> lista) {
        StringJoiner joiner = new StringJoiner(";");
        for (Venta v : lista) joiner.add(serializarVenta(v));
        return joiner.toString();
    }

    private static String serializarVenta(Venta v) {
        return v.getId() + "~" + v.getClienteId() + "~" + v.getSucursalId() + "~" + v.getFecha()
            + "~" + v.getSubtotal() + "~" + v.getIgv() + "~" + v.getTotal() + "~" + v.getEstado();
    }

    private static String serializarDetalles(List<DetalleVenta> lista) {
        StringJoiner joiner = new StringJoiner(";");
        for (DetalleVenta d : lista) {
            joiner.add(d.getId() + "~" + d.getVentaId() + "~" + d.getProductoId() + "~" + d.getCantidad()
                + "~" + d.getPrecioUnitario() + "~" + d.getSubtotal() + "~" + d.getEstado());
        }
        return joiner.toString();
    }
}

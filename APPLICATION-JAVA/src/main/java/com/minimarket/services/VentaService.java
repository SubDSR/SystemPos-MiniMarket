package com.minimarket.services;

import com.minimarket.models.DetalleVenta;
import com.minimarket.models.Venta;
import com.minimarket.utils.Config;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Servicio para gestion completa de ventas.
 *
 * Coordina tres archivos binarios:
 *   - ventas.dat / .idx      → cabecera de la venta
 *   - detalles.dat / .idx    → lineas de producto por venta
 *   - productos.dat / .idx   → actualizacion de stock
 */
public class VentaService {

    private static final Logger LOG = Logger.getLogger(VentaService.class.getName());
    private static final DateTimeFormatter FECHA_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final FileManager<Venta>        ventaFm;
    private final FileManager<DetalleVenta> detalleFm;
    private final ProductoService           prodSvc;

    public VentaService() {
        ventaFm = new FileManager<>(
            Config.VENTAS_DAT, Config.VENTAS_IDX, Config.VENTAS_HOLES,
            Venta.SIZE, Venta::fromBytes, "Venta"
        );
        detalleFm = new FileManager<>(
            Config.DETALLES_DAT, Config.DETALLES_IDX, Config.DETALLES_HOLES,
            DetalleVenta.SIZE, DetalleVenta::fromBytes, "DetalleVenta"
        );
        prodSvc = new ProductoService();
    }

    // ════════════════════════════════════════════════════════════════════════
    // CREAR VENTA
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Registra una venta completa.
     *
     * Proceso:
     *   1. Validar stock disponible para cada producto
     *   2. Calcular subtotales, IGV (18%) y total
     *   3. Guardar cabecera en ventas.dat
     *   4. Guardar cada linea en detalles.dat
     *   5. Descontar stock en productos.dat
     *
     * @param items     Lista de pares (productoId, cantidad)
     * @param clienteId ID del cliente (0 = venta anonima)
     * @return Objeto Venta creado, o null si fallo la validacion
     */
    public Venta crearVenta(List<int[]> items, int clienteId) {
        if (items == null || items.isEmpty()) {
            LOG.warning("Intento de crear venta sin items");
            return null;
        }

        // ── 1. Validar stock ─────────────────────────────────────────────────
        List<Map<String, Object>> detallesData = new ArrayList<>();
        for (int[] item : items) {
            int prodId   = item[0];
            int cantidad = item[1];

            if (cantidad <= 0) {
                LOG.warning("Cantidad invalida: prod=" + prodId + " cant=" + cantidad);
                return null;
            }
            var prod = prodSvc.obtener(prodId);
            if (prod == null) {
                LOG.severe("Producto id=" + prodId + " no encontrado");
                return null;
            }
            if (prod.getStock() < cantidad) {
                LOG.warning("Stock insuficiente: prod=" + prodId
                    + " disponible=" + prod.getStock() + " solicitado=" + cantidad);
                return null;
            }
            Map<String, Object> d = new HashMap<>();
            d.put("prodId",   prodId);
            d.put("cantidad", cantidad);
            d.put("precio",   prod.getPrecio());
            d.put("subtotal", Math.round(prod.getPrecio() * cantidad * 100.0) / 100.0);
            detallesData.add(d);
        }

        // ── 2. Calcular montos ────────────────────────────────────────────────
        double subtotalSum = detallesData.stream()
            .mapToDouble(d -> (Double) d.get("subtotal")).sum();
        subtotalSum = Math.round(subtotalSum * 100.0) / 100.0;
        double igv   = Math.round(subtotalSum * Config.IGV_RATE * 100.0) / 100.0;
        double total = Math.round((subtotalSum + igv) * 100.0) / 100.0;
        String fecha = LocalDateTime.now().format(FECHA_FMT);

        // ── 3. Guardar cabecera ───────────────────────────────────────────────
        Venta venta = new Venta(0, clienteId, fecha, subtotalSum, igv, total, 1);
        ventaFm.insert(venta);

        // ── 4. Guardar detalles ───────────────────────────────────────────────
        for (Map<String, Object> d : detallesData) {
            DetalleVenta detalle = new DetalleVenta(
                0, venta.getId(), (Integer) d.get("prodId"),
                (Integer) d.get("cantidad"), (Double) d.get("precio"),
                (Double) d.get("subtotal"), 1
            );
            detalleFm.insert(detalle);
        }

        // ── 5. Descontar stock ────────────────────────────────────────────────
        for (Map<String, Object> d : detallesData) {
            prodSvc.descontarStock((Integer) d.get("prodId"), (Integer) d.get("cantidad"));
        }

        LOG.info("Venta registrada: id=" + venta.getId() + " total=S/" + total
            + " items=" + items.size() + " cliente=" + clienteId);
        return venta;
    }

    // ════════════════════════════════════════════════════════════════════════
    // CONSULTAS
    // ════════════════════════════════════════════════════════════════════════

    public Venta obtenerVenta(int ventaId)   { return ventaFm.findById(ventaId); }
    public List<Venta> listarVentas()        { return ventaFm.findAll(); }
    public List<DetalleVenta> listarDetalles() { return detalleFm.findAll(); }
    public int contarVentas()                { return ventaFm.countActive(); }

    /** Retorna las lineas de detalle de una venta especifica. */
    public List<DetalleVenta> obtenerDetalles(int ventaId) {
        return detalleFm.findAll().stream()
            .filter(d -> d.getVentaId() == ventaId)
            .collect(Collectors.toList());
    }

    /** Ventas realizadas en el dia de hoy. */
    public List<Venta> ventasHoy() {
        String hoy = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        return ventaFm.findAll().stream()
            .filter(v -> v.getFecha().startsWith(hoy))
            .collect(Collectors.toList());
    }

    /** Suma de totales de las ventas del dia. */
    public double totalHoy() {
        double sum = ventasHoy().stream().mapToDouble(Venta::getTotal).sum();
        return Math.round(sum * 100.0) / 100.0;
    }

    // ════════════════════════════════════════════════════════════════════════
    // ANULAR VENTA
    // ════════════════════════════════════════════════════════════════════════

    /** Anula una venta: estado=0 y restaura el stock de cada producto. */
    public boolean anularVenta(int ventaId) {
        Venta venta = ventaFm.findById(ventaId);
        if (venta == null) return false;

        List<DetalleVenta> detalles = obtenerDetalles(ventaId);
        for (DetalleVenta det : detalles) {
            prodSvc.restaurarStock(det.getProductoId(), det.getCantidad());
            detalleFm.delete(det.getId());
        }

        boolean ok = ventaFm.delete(ventaId);
        if (ok) LOG.info("Venta id=" + ventaId + " anulada correctamente");
        return ok;
    }

    // ════════════════════════════════════════════════════════════════════════
    // EXPORTACION
    // ════════════════════════════════════════════════════════════════════════

    public int exportarVentasCsv(Path exportPath) throws IOException {
        List<Venta> ventas = listarVentas();
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(exportPath, StandardCharsets.UTF_8))) {
            pw.println("id,cliente_id,fecha,subtotal,igv,total,estado");
            for (Venta v : ventas) pw.println(v.toCsvRow());
        }
        LOG.info("Exportadas " + ventas.size() + " ventas a " + exportPath);
        return ventas.size();
    }

    public int exportarDetallesCsv(Path exportPath) throws IOException {
        List<DetalleVenta> detalles = listarDetalles();
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(exportPath, StandardCharsets.UTF_8))) {
            pw.println("id,venta_id,producto_id,cantidad,precio_unitario,subtotal,estado");
            for (DetalleVenta d : detalles) pw.println(d.toCsvRow());
        }
        LOG.info("Exportados " + detalles.size() + " detalles a " + exportPath);
        return detalles.size();
    }
}

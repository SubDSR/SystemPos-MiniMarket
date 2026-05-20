package com.minimarket.services;

import com.minimarket.models.Producto;
import com.minimarket.utils.Config;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Servicio CRUD para productos.
 * Toda la persistencia se delega al FileManager (RandomAccessFile + seek()).
 */
public class ProductoService {

    private static final Logger LOG = Logger.getLogger(ProductoService.class.getName());

    private final FileManager<Producto> fm;

    public ProductoService() {
        fm = new FileManager<>(
            Config.PRODUCTOS_DAT,
            Config.PRODUCTOS_IDX,
            Config.PRODUCTOS_HOLES,
            Producto.SIZE,
            Producto::fromBytes,
            "Producto"
        );
    }

    // ── Crear ────────────────────────────────────────────────────────────────

    public Producto crear(String nombre, double precio, int stock, String categoria) {
        if (nombre == null || nombre.isBlank())
            throw new IllegalArgumentException("El nombre del producto no puede estar vacío.");
        if (precio < 0)
            throw new IllegalArgumentException("El precio no puede ser negativo.");
        if (stock < 0)
            throw new IllegalArgumentException("El stock no puede ser negativo.");

        Producto producto = new Producto(0, nombre.strip(), precio, stock, categoria.strip(), 1);
        fm.insert(producto);
        LOG.info("Producto creado: " + producto);
        return producto;
    }

    // ── Leer ─────────────────────────────────────────────────────────────────

    /** Acceso directo O(1) por ID. */
    public Producto obtener(int productoId) {
        return fm.findById(productoId);
    }

    /** Todos los productos activos. */
    public List<Producto> listar() {
        return fm.findAll();
    }

    /** Busqueda por nombre o categoria (filtro sobre resultados del indice). */
    public List<Producto> buscar(String termino) {
        String t = termino.toLowerCase().strip();
        return fm.findAll().stream()
            .filter(p -> p.getNombre().toLowerCase().contains(t)
                      || p.getCategoria().toLowerCase().contains(t))
            .collect(Collectors.toList());
    }

    // ── Actualizar ───────────────────────────────────────────────────────────

    public Producto actualizar(int productoId, String nombre, Double precio,
                               Integer stock, String categoria) {
        Producto prod = fm.findById(productoId);
        if (prod == null) {
            LOG.warning("Producto id=" + productoId + " no encontrado para actualizar");
            return null;
        }
        if (nombre    != null) prod.setNombre(nombre.strip());
        if (precio    != null) prod.setPrecio(precio);
        if (stock     != null) prod.setStock(stock);
        if (categoria != null) prod.setCategoria(categoria.strip());

        fm.update(prod);
        LOG.info("Producto actualizado: " + prod);
        return prod;
    }

    /**
     * Descuenta 'cantidad' unidades del stock al procesar una venta.
     * @return false si el stock es insuficiente.
     */
    public boolean descontarStock(int productoId, int cantidad) {
        Producto prod = fm.findById(productoId);
        if (prod == null) {
            LOG.severe("Producto id=" + productoId + " no encontrado para descontar stock");
            return false;
        }
        if (prod.getStock() < cantidad) {
            LOG.warning("Stock insuficiente: id=" + productoId
                + " disponible=" + prod.getStock() + " solicitado=" + cantidad);
            return false;
        }
        prod.setStock(prod.getStock() - cantidad);
        return fm.update(prod);
    }

    /** Restaura stock al anular una venta. */
    public boolean restaurarStock(int productoId, int cantidad) {
        Producto prod = fm.findById(productoId);
        if (prod == null) return false;
        prod.setStock(prod.getStock() + cantidad);
        return fm.update(prod);
    }

    // ── Eliminar ─────────────────────────────────────────────────────────────

    /** Eliminacion logica (estado=0). El slot queda disponible para reutilizar. */
    public boolean eliminar(int productoId) {
        boolean ok = fm.delete(productoId);
        if (ok) LOG.info("Producto id=" + productoId + " eliminado logicamente");
        return ok;
    }

    // ── Utilidades ───────────────────────────────────────────────────────────

    public int contar() { return fm.countActive(); }

    public int compactar() { return fm.compact(); }

    /** Exporta todos los productos activos a CSV para sincronizacion. */
    public int exportarCsv(Path exportPath) throws IOException {
        List<Producto> productos = listar();
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(exportPath, StandardCharsets.UTF_8))) {
            pw.println("id,nombre,precio,stock,categoria,estado");
            for (Producto p : productos) {
                pw.println(p.toCsvRow());
            }
        }
        LOG.info("Exportados " + productos.size() + " productos a " + exportPath);
        return productos.size();
    }
}

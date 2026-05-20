package com.minimarket.services;

import com.minimarket.models.Cliente;
import com.minimarket.utils.Config;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Servicio CRUD para clientes.
 * Toda la persistencia se delega al FileManager (RandomAccessFile + seek()).
 */
public class ClienteService {

    private static final Logger LOG = Logger.getLogger(ClienteService.class.getName());

    private final FileManager<Cliente> fm;

    public ClienteService() {
        fm = new FileManager<>(
            Config.CLIENTES_DAT,
            Config.CLIENTES_IDX,
            Config.CLIENTES_HOLES,
            Cliente.SIZE,
            Cliente::fromBytes,
            "Cliente"
        );
    }

    // ── Crear ────────────────────────────────────────────────────────────────

    public Cliente crear(String nombre, String dni, String telefono, String email) {
        if (nombre == null || nombre.isBlank())
            throw new IllegalArgumentException("El nombre del cliente no puede estar vacío.");
        if (dni == null || dni.isBlank())
            throw new IllegalArgumentException("El DNI no puede estar vacío.");
        if (buscarPorDni(dni) != null)
            throw new IllegalArgumentException("Ya existe un cliente con DNI " + dni + ".");

        Cliente cliente = new Cliente(0, nombre.strip(), dni.strip(),
            telefono != null ? telefono.strip() : "",
            email    != null ? email.strip()    : "", 1);
        fm.insert(cliente);
        LOG.info("Cliente creado: " + cliente);
        return cliente;
    }

    // ── Leer ─────────────────────────────────────────────────────────────────

    /** Acceso directo O(1) por ID. */
    public Cliente obtener(int clienteId) {
        return fm.findById(clienteId);
    }

    /** Todos los clientes activos. */
    public List<Cliente> listar() {
        return fm.findAll();
    }

    /** Busqueda por nombre o DNI. */
    public List<Cliente> buscar(String termino) {
        String t = termino.toLowerCase().strip();
        return fm.findAll().stream()
            .filter(c -> c.getNombre().toLowerCase().contains(t)
                      || c.getDni().toLowerCase().contains(t))
            .collect(Collectors.toList());
    }

    /** Busca un cliente especifico por su DNI. */
    public Cliente buscarPorDni(String dni) {
        String dniClean = dni.strip();
        return fm.findAll().stream()
            .filter(c -> c.getDni().equals(dniClean))
            .findFirst()
            .orElse(null);
    }

    // ── Actualizar ───────────────────────────────────────────────────────────

    public Cliente actualizar(int clienteId, String nombre, String dni,
                              String telefono, String email) {
        Cliente cli = fm.findById(clienteId);
        if (cli == null) {
            LOG.warning("Cliente id=" + clienteId + " no encontrado para actualizar");
            return null;
        }
        if (nombre   != null) cli.setNombre(nombre.strip());
        if (dni      != null) cli.setDni(dni.strip());
        if (telefono != null) cli.setTelefono(telefono.strip());
        if (email    != null) cli.setEmail(email.strip());

        fm.update(cli);
        LOG.info("Cliente actualizado: " + cli);
        return cli;
    }

    // ── Eliminar ─────────────────────────────────────────────────────────────

    /** Eliminacion logica (estado=0). */
    public boolean eliminar(int clienteId) {
        boolean ok = fm.delete(clienteId);
        if (ok) LOG.info("Cliente id=" + clienteId + " eliminado logicamente");
        return ok;
    }

    // ── Utilidades ───────────────────────────────────────────────────────────

    public int contar() { return fm.countActive(); }

    /** Exporta todos los clientes activos a CSV para sincronizacion. */
    public int exportarCsv(Path exportPath) throws IOException {
        List<Cliente> clientes = listar();
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(exportPath, StandardCharsets.UTF_8))) {
            pw.println("id,nombre,dni,telefono,email,estado");
            for (Cliente c : clientes) {
                pw.println(c.toCsvRow());
            }
        }
        LOG.info("Exportados " + clientes.size() + " clientes a " + exportPath);
        return clientes.size();
    }
}

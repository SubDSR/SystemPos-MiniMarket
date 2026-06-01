package com.minimarket.server.services;

import com.minimarket.server.database.DatabaseManager;
import com.minimarket.server.models.DetalleVenta;
import com.minimarket.server.models.Venta;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class VentaService {
    private final DatabaseManager db;

    public VentaService(DatabaseManager db) { this.db = db; }

    public String registrar(int clienteId, int sucursalId, Map<Integer, Integer> items) {
        return db.registrarVenta(clienteId, sucursalId, items);
    }
    public List<Venta> listar() throws SQLException { return db.listarVentas(); }
    public Venta buscar(int id) throws SQLException { return db.buscarVenta(id); }
    public List<DetalleVenta> detalles(Integer ventaId) throws SQLException { return db.listarDetalles(ventaId); }
    public void anular(int ventaId) throws SQLException { db.anularVenta(ventaId); }
}

package com.minimarket.server.services;

import com.minimarket.server.database.DatabaseManager;
import com.minimarket.server.models.Producto;

import java.sql.SQLException;
import java.util.List;

public class ProductoService {
    private final DatabaseManager db;

    public ProductoService(DatabaseManager db) { this.db = db; }

    public List<Producto> listar() throws SQLException { return db.listarProductos(); }
    public Producto buscar(int id) throws SQLException { return db.buscarProducto(id); }
    public int crear(String nombre, double precio, int stock, String categoria) throws SQLException {
        return db.insertarProducto(nombre, precio, stock, categoria);
    }
    public void actualizar(int id, String nombre, double precio, int stock, String categoria) throws SQLException {
        db.actualizarProducto(id, nombre, precio, stock, categoria);
    }
    public void eliminar(int id) throws SQLException { db.eliminarProducto(id); }
}

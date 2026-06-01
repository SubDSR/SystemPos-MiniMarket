package com.minimarket.server.services;

import com.minimarket.server.database.DatabaseManager;
import com.minimarket.server.models.Cliente;

import java.sql.SQLException;
import java.util.List;

public class ClienteService {
    private final DatabaseManager db;

    public ClienteService(DatabaseManager db) { this.db = db; }

    public List<Cliente> listar() throws SQLException { return db.listarClientes(); }
    public Cliente buscar(int id) throws SQLException { return db.buscarCliente(id); }
    public int crear(String nombre, String dni, String telefono, String email) throws SQLException {
        return db.insertarCliente(nombre, dni, telefono, email);
    }
    public void actualizar(int id, String nombre, String dni, String telefono, String email) throws SQLException {
        db.actualizarCliente(id, nombre, dni, telefono, email);
    }
    public void eliminar(int id) throws SQLException { db.eliminarCliente(id); }
}

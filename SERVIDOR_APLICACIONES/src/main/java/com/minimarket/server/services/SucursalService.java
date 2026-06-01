package com.minimarket.server.services;

import com.minimarket.server.database.DatabaseManager;
import com.minimarket.server.models.Sucursal;

import java.sql.SQLException;
import java.util.List;

public class SucursalService {
    private final DatabaseManager db;

    public SucursalService(DatabaseManager db) { this.db = db; }

    public List<Sucursal> listar() throws SQLException { return db.listarSucursales(); }
}

package com.minimarket.server.models;

public class Sucursal {
    private final int id;
    private final String nombre;
    private final String direccion;
    private final int activo;

    public Sucursal(int id, String nombre, String direccion, int activo) {
        this.id = id;
        this.nombre = nombre != null ? nombre : "";
        this.direccion = direccion != null ? direccion : "";
        this.activo = activo;
    }

    public int getId() { return id; }
    public String getNombre() { return nombre; }
    public String getDireccion() { return direccion; }
    public int getActivo() { return activo; }
}

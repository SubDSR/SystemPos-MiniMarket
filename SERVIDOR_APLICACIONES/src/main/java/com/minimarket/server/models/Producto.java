package com.minimarket.server.models;

public class Producto {
    private int id;
    private String nombre;
    private double precio;
    private int stock;
    private String categoria;
    private int estado;

    public Producto(int id, String nombre, double precio, int stock, String categoria, int estado) {
        this.id = id;
        this.nombre = nombre != null ? nombre : "";
        this.precio = precio;
        this.stock = stock;
        this.categoria = categoria != null ? categoria : "";
        this.estado = estado;
    }

    public int getId() { return id; }
    public String getNombre() { return nombre; }
    public double getPrecio() { return precio; }
    public int getStock() { return stock; }
    public String getCategoria() { return categoria; }
    public int getEstado() { return estado; }
}

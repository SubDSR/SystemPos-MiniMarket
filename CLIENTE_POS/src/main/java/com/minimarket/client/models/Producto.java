package com.minimarket.client.models;

public class Producto {
    private int id;
    private String nombre;
    private double precio;
    private int stock;
    private String categoria;
    private int estado;

    public Producto() {
        this.nombre = "";
        this.categoria = "";
        this.estado = 1;
    }

    public Producto(int id, String nombre, double precio, int stock, String categoria, int estado) {
        this.id = id;
        this.nombre = nombre != null ? nombre : "";
        this.precio = precio;
        this.stock = stock;
        this.categoria = categoria != null ? categoria : "";
        this.estado = estado;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre != null ? nombre : ""; }
    public double getPrecio() { return precio; }
    public void setPrecio(double precio) { this.precio = precio; }
    public int getStock() { return stock; }
    public void setStock(int stock) { this.stock = stock; }
    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria != null ? categoria : ""; }
    public int getEstado() { return estado; }
    public void setEstado(int estado) { this.estado = estado; }
}

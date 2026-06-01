package com.minimarket.server.models;

public class DetalleVenta {
    private int id;
    private int ventaId;
    private int productoId;
    private int cantidad;
    private double precioUnitario;
    private double subtotal;
    private int estado;

    public DetalleVenta(int id, int ventaId, int productoId, int cantidad,
                        double precioUnitario, double subtotal, int estado) {
        this.id = id;
        this.ventaId = ventaId;
        this.productoId = productoId;
        this.cantidad = cantidad;
        this.precioUnitario = precioUnitario;
        this.subtotal = subtotal;
        this.estado = estado;
    }

    public int getId() { return id; }
    public int getVentaId() { return ventaId; }
    public int getProductoId() { return productoId; }
    public int getCantidad() { return cantidad; }
    public double getPrecioUnitario() { return precioUnitario; }
    public double getSubtotal() { return subtotal; }
    public int getEstado() { return estado; }
}

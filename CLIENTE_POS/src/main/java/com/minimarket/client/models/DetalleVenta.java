package com.minimarket.client.models;

public class DetalleVenta {
    private int id;
    private int ventaId;
    private int productoId;
    private int cantidad;
    private double precioUnitario;
    private double subtotal;
    private int estado;

    public DetalleVenta() { this.estado = 1; }

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
    public void setId(int id) { this.id = id; }
    public int getVentaId() { return ventaId; }
    public void setVentaId(int ventaId) { this.ventaId = ventaId; }
    public int getProductoId() { return productoId; }
    public void setProductoId(int productoId) { this.productoId = productoId; }
    public int getCantidad() { return cantidad; }
    public void setCantidad(int cantidad) { this.cantidad = cantidad; }
    public double getPrecioUnitario() { return precioUnitario; }
    public void setPrecioUnitario(double precioUnitario) { this.precioUnitario = precioUnitario; }
    public double getSubtotal() { return subtotal; }
    public void setSubtotal(double subtotal) { this.subtotal = subtotal; }
    public int getEstado() { return estado; }
    public void setEstado(int estado) { this.estado = estado; }
}
